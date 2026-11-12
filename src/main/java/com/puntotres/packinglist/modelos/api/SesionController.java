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

import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.AutenticacionModelos;
import com.puntotres.packinglist.modelos.seguridad.FiltroDispositivo;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.seguridad.FrenoEntrada;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.Dispositivos;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Entrar y salir de la app de modelos. Todos entran igual, con usuario y
 * contraseña en {@code POST /api/sesion} (decisión del usuario, noviembre de
 * 2026: antes el taller tecleaba un código de alta), y lo que pasa después
 * depende del rol:
 *
 * <ul>
 * <li>El despacho y el admin: una sesión de 8 horas, una jornada, puesta en
 * esa sesión y no en la configuración global, que es la de la app vieja.</li>
 * <li>El taller: el aparato se da de alta ({@link Dispositivos#darDeAlta}) y
 * se queda con la cookie {@value FiltroDispositivo#COOKIE}, que vale un año y
 * con la que entra cada petición sin sesión ({@link FiltroDispositivo}). Así
 * la tablet y el móvil no piden la contraseña cada mañana, y cada aparato es
 * un alta aparte que el admin puede revocar.</li>
 * </ul>
 *
 * Las entradas fallidas se frenan por IP ({@link FrenoEntrada}).
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
    private final FrenoEntrada freno;
    private final SecurityContextHolderStrategy estrategia = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository repositorioContexto = new HttpSessionSecurityContextRepository();

    public SesionController(Usuarios usuarios, Dispositivos dispositivos, FrenoEntrada freno) {
        this.usuarios = usuarios;
        this.dispositivos = dispositivos;
        this.freno = freno;
    }

    /**
     * Lo que se teclea en el login, y cómo llamar al aparato si es del taller
     * (lo manda la app: el navegador y el sistema, para reconocerlo en la
     * lista de dispositivos).
     */
    public record Credenciales(String login, String password, String nombre) {
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> entrar(@RequestBody Credenciales credenciales,
                                                      HttpServletRequest request, HttpServletResponse response) {
        String ip = request.getRemoteAddr();
        if (freno.frenada(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("mensaje", DEMASIADOS_INTENTOS));
        }
        Usuario usuario = usuarios.autenticar(credenciales.login(), credenciales.password()).orElse(null);
        if (usuario == null) {
            freno.fallo(ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensaje", INCORRECTOS));
        }
        freno.acierto(ip);
        if (usuario.getRol() == Rol.TALLER) {
            return entrarConDispositivo(usuario, credenciales.nombre(), request);
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
        Map<String, Object> datos = datos(autenticacion.getPrincipal());
        datos.put("dispositivo", false);
        return ResponseEntity.ok(datos);
    }

    /**
     * El taller: se da de alta el aparato y se le deja la cookie. Si en ese
     * navegador había una sesión de contraseña (alguien del despacho entró
     * antes), se quita de la sesión: la sesión manda sobre la cookie
     * ({@link FiltroDispositivo} solo actúa sin autenticación), y la tablet
     * seguiría atendida como esa otra persona.
     */
    private ResponseEntity<Map<String, Object>> entrarConDispositivo(Usuario usuario, String nombre,
                                                                      HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            request.changeSessionId();
        }
        estrategia.clearContext();
        Dispositivos.TokenDispositivo token = dispositivos.darDeAlta(usuario.getId(), nombre);
        UsuarioActual actual = AutenticacionModelos.de(usuario, token.dispositivo().getId()).getPrincipal();
        String cookie = cookie(request, token.tokenEnClaro(), Dispositivos.VIGENCIA_COOKIE.toSeconds()).toString();
        Map<String, Object> datos = datos(actual);
        datos.put("dispositivo", true);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie).body(datos);
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
     * Salir: quita de la sesión quién ha entrado y, si se entró con un
     * dispositivo, le borra la cookie. El dispositivo no se revoca (eso lo hace
     * el admin, y la cookie ya no está en ningún sitio); al volver a entrar con
     * la contraseña, el aparato se da de alta otra vez.
     *
     * La sesión NO se invalida, a propósito: la app de modelos y las pantallas
     * viejas se sirven desde el mismo origen y comparten la misma sesión HTTP,
     * y en ella viven el envío en curso del packing list, el corte en curso o
     * las filas desplegadas de la revisión. Invalidarla al salir de /app haría
     * perder lo tecleado en otra pestaña. Solo se quita el contexto de
     * seguridad y se cambia el id de la sesión, para que el id que llevaba la
     * sesión entrada no sirva después.
     */
    @DeleteMapping
    public ResponseEntity<Void> salir(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            request.changeSessionId();
        }
        estrategia.clearContext();
        ResponseEntity.HeadersBuilder<?> respuesta = ResponseEntity.noContent();
        if (traeCookieDeDispositivo(request)) {
            respuesta.header(HttpHeaders.SET_COOKIE, cookie(request, "", 0).toString());
        }
        return respuesta.build();
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
