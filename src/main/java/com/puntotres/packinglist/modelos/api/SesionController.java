package com.puntotres.packinglist.modelos.api;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.AutenticacionModelos;
import com.puntotres.packinglist.modelos.seguridad.FiltroDispositivo;
import com.puntotres.packinglist.modelos.seguridad.FrenoCanje;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.Dispositivos;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Entrar y salir de la app de modelos.
 *
 * <ul>
 * <li>Con contraseña (el despacho): {@code POST /api/sesion} abre una sesión
 * de 8 horas, una jornada, puesta en esa sesión y no en la configuración
 * global, que es la de la app vieja.</li>
 * <li>Con un dispositivo (el taller): {@code POST /api/sesion/dispositivo}
 * canjea el código de alta y deja en el aparato la cookie
 * {@value FiltroDispositivo#COOKIE}, que vale un año y con la que entra cada
 * petición sin sesión ({@link FiltroDispositivo}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/sesion")
public class SesionController {

    /** Lo que dura una sesión abierta con contraseña sin tocar nada. */
    static final int SEGUNDOS_SESION = 8 * 3600;

    private static final String INCORRECTOS = "Usuario o contraseña incorrectos";
    private static final String HACE_FALTA_ENTRAR = "Hace falta entrar";
    private static final String DEMASIADOS_INTENTOS = "Demasiados intentos, espera unos minutos";

    private final Usuarios usuarios;
    private final Dispositivos dispositivos;
    private final FrenoCanje freno;
    private final SecurityContextHolderStrategy estrategia = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository repositorioContexto = new HttpSessionSecurityContextRepository();

    public SesionController(Usuarios usuarios, Dispositivos dispositivos, FrenoCanje freno) {
        this.usuarios = usuarios;
        this.dispositivos = dispositivos;
        this.freno = freno;
    }

    /** Lo que se teclea en el login. */
    public record Credenciales(String login, String password) {
    }

    /** El código de alta y cómo se quiere llamar al aparato ("Tablet del taller"). */
    public record Canje(String codigo, String nombre) {
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> entrar(@RequestBody Credenciales credenciales,
                                                      HttpServletRequest request, HttpServletResponse response) {
        Usuario usuario = usuarios.autenticar(credenciales.login(), credenciales.password()).orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensaje", INCORRECTOS));
        }
        // Id de sesión nuevo al entrar: uno que alguien hubiera fijado antes no sirve después.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        request.getSession(true).setMaxInactiveInterval(SEGUNDOS_SESION);

        AutenticacionModelos autenticacion = AutenticacionModelos.de(usuario, null);
        SecurityContext contexto = estrategia.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        estrategia.setContext(contexto);
        repositorioContexto.saveContext(contexto, request, response);
        return ResponseEntity.ok(datos(autenticacion.getPrincipal()));
    }

    /** Quién ha entrado; 401 si nadie, que es como la SPA sabe que tiene que enseñar el login. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> quienSoy(@AuthenticationPrincipal UsuarioActual actual) {
        if (actual == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensaje", HACE_FALTA_ENTRAR));
        }
        Map<String, Object> datos = datos(actual);
        datos.put("dispositivo", actual.dispositivoId() != null);
        return ResponseEntity.ok(datos);
    }

    /**
     * Salir: cierra la sesión y, si se entró con un dispositivo, le borra la
     * cookie. El dispositivo no se revoca (eso lo hace el admin); el aparato
     * tendrá que darse de alta con un código nuevo.
     */
    @DeleteMapping
    public ResponseEntity<Void> salir(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        estrategia.clearContext();
        ResponseEntity.HeadersBuilder<?> respuesta = ResponseEntity.noContent();
        if (traeCookieDeDispositivo(request)) {
            respuesta.header(HttpHeaders.SET_COOKIE, cookie(request, "", 0).toString());
        }
        return respuesta.build();
    }

    @PostMapping("/dispositivo")
    public ResponseEntity<Map<String, Object>> canjear(@RequestBody Canje canje, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (freno.frenada(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("mensaje", DEMASIADOS_INTENTOS));
        }
        Dispositivos.TokenDispositivo token;
        try {
            token = dispositivos.canjear(canje.codigo(), canje.nombre());
        } catch (IllegalArgumentException codigoQueNoVale) {
            freno.fallo(ip);
            throw codigoQueNoVale;
        }
        freno.acierto(ip);
        Usuario usuario = usuarios.porId(token.dispositivo().getUsuarioId())
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Este usuario está desactivado"));

        UsuarioActual actual = AutenticacionModelos.de(usuario, token.dispositivo().getId()).getPrincipal();
        String cookie = cookie(request, token.tokenEnClaro(), Dispositivos.VIGENCIA_COOKIE.toSeconds()).toString();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie).body(datos(actual));
    }

    /** HttpOnly (la SPA no la necesita leer), Lax y Secure solo en https: el PC de desarrollo va en http. */
    private static ResponseCookie cookie(HttpServletRequest request, String valor, long segundos) {
        return ResponseCookie.from(FiltroDispositivo.COOKIE, valor)
                .httpOnly(true)
                .sameSite("Lax")
                .secure(request.isSecure())
                .maxAge(segundos)
                .path("/")
                .build();
    }

    private static boolean traeCookieDeDispositivo(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies != null && Arrays.stream(cookies).anyMatch(c -> FiltroDispositivo.COOKIE.equals(c.getName()));
    }

    private static Map<String, Object> datos(UsuarioActual actual) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", actual.id());
        datos.put("nombre", actual.nombre());
        datos.put("rol", actual.rol().name());
        return datos;
    }
}
