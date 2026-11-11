package com.puntotres.packinglist.modelos;

import java.util.Locale;
import java.util.Map;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.AutenticacionModelos;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Para los tests de la API de modelos: entrar como un usuario sin pasar por
 * el login.
 *
 * <pre>
 * Usuario taller = ComoUsuario.usuarioDePrueba(usuarios, Rol.TALLER);
 * mvc.perform(get("/api/...").with(comoUsuario(taller)));
 * mvc.perform(post("/api/...").with(comoUsuario(taller)).with(csrf()));
 * </pre>
 *
 * Los POST, PATCH y DELETE de la API llevan CSRF, así que además necesitan
 * {@code .with(csrf())} (de {@code SecurityMockMvcRequestPostProcessors}); sin
 * él contestan 403 aunque el usuario tenga permiso.
 *
 * No hay un {@code comoUsuario(Rol)} a propósito: el usuario tiene que existir
 * de verdad, porque lo que se guarda en su nombre ({@code creada_por},
 * {@code usuario_id}...) lleva clave foránea a {@code usuario(id)} y un id
 * inventado rompería en la base de datos, no en el test.
 */
public final class ComoUsuario {

    /** La contraseña de los usuarios de prueba. */
    public static final String PASSWORD = "prueba";

    private static final Map<Rol, String> NOMBRES = Map.of(
            Rol.TALLER, "Prueba Taller",
            Rol.DESPACHO, "Prueba Despacho",
            Rol.ADMIN, "Prueba Admin");

    private ComoUsuario() {
    }

    /**
     * El usuario {@code prueba-taller}, {@code prueba-despacho} o
     * {@code prueba-admin} (contraseña {@value #PASSWORD}): lo crea la primera
     * vez y después lo encuentra, porque la base de datos en memoria es la
     * misma para toda la suite. Son compartidos: un test no debe
     * desactivarlos ni cambiarles el rol.
     */
    public static Usuario usuarioDePrueba(Usuarios usuarios, Rol rol) {
        String login = "prueba-" + rol.name().toLowerCase(Locale.ROOT);
        return usuarios.todos().stream()
                .filter(usuario -> login.equalsIgnoreCase(usuario.getLogin()))
                .findFirst()
                .orElseGet(() -> usuarios.crear(new Usuarios.Alta(NOMBRES.get(rol), login, PASSWORD, rol)));
    }

    /** La petición va como ese usuario, entrado con contraseña (sin dispositivo). */
    public static RequestPostProcessor comoUsuario(Usuario usuario) {
        return SecurityMockMvcRequestPostProcessors.authentication(AutenticacionModelos.de(usuario, null));
    }
}
