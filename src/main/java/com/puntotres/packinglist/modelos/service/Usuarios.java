package com.puntotres.packinglist.modelos.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.modelos.ModelosProperties;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;

/**
 * Los usuarios de la app de modelos: alta, comprobación de contraseña y
 * cambios. La contraseña solo existe aquí de paso: se guarda su hash BCrypt.
 *
 * Es también lo que corre en el primer arranque ({@link ApplicationRunner}):
 * sin ningún usuario, alguien tiene que poder entrar a dar de alta a los
 * demás, así que con {@code MODELOS_ADMIN_PASSWORD} definida se crea el
 * usuario {@code admin}. Sin ella no se inventa ninguna contraseña: se avisa
 * en el log y la app de modelos se queda sin nadie que pueda entrar hasta que
 * se configure.
 */
@Service
public class Usuarios implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(Usuarios.class);

    /** Login del administrador que se crea en el primer arranque. */
    static final String LOGIN_ADMIN = "admin";

    private final UsuarioRepository repositorio;
    private final ModelosProperties props;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public Usuarios(UsuarioRepository repositorio, ModelosProperties props) {
        this.repositorio = repositorio;
        this.props = props;
    }

    /**
     * Lo que hace falta para dar de alta a un usuario. Una contraseña vacía es
     * un usuario que no entra con contraseña (el taller entra con un
     * dispositivo dado de alta por código).
     */
    public record Alta(String nombre, String login, String password, Rol rol) {
    }

    /**
     * Da de alta un usuario activo. El login es único sin mirar mayúsculas:
     * {@code Pepa} y {@code pepa} serían la misma persona al teclearlo.
     *
     * @throws IllegalArgumentException si falta el nombre, el login o el rol,
     *                                  o si el login ya existe
     */
    @Transactional
    public Usuario crear(Alta alta) {
        String nombre = recortado(alta.nombre());
        String login = recortado(alta.login());
        if (nombre == null || login == null || alta.rol() == null) {
            throw new IllegalArgumentException("Un usuario necesita nombre, login y rol");
        }
        if (repositorio.existsByLoginIgnoreCase(login)) {
            throw new IllegalArgumentException("Ya hay un usuario con el login " + login);
        }
        return repositorio.save(new Usuario(nombre, login, hashDe(alta.password()), alta.rol()));
    }

    /**
     * El usuario con ese login y esa contraseña, si está activo, y apunta su
     * acceso. Vacío si el login no existe, si está inactivo, si no entra con
     * contraseña o si la contraseña no es: nunca se dice cuál de las cosas
     * falla, para no enseñar qué logins existen.
     */
    @Transactional
    public Optional<Usuario> autenticar(String login, String password) {
        if (login == null || password == null) {
            return Optional.empty();
        }
        Optional<Usuario> usuario = repositorio.findByLoginIgnoreCase(login.trim())
                .filter(Usuario::isActivo)
                .filter(encontrado -> encontrado.getPasswordHash() != null)
                .filter(encontrado -> encoder.matches(password, encontrado.getPasswordHash()));
        usuario.ifPresent(Usuario::tocarAcceso);
        return usuario;
    }

    /** Todos, activos e inactivos, por nombre. */
    public List<Usuario> todos() {
        return repositorio.findAll(Sort.by("nombre", "id"));
    }

    public Optional<Usuario> porId(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id);
    }

    /**
     * Cambia lo que llegue: un nulo (o un texto en blanco) es "no tocar", como
     * en el resto de pantallas del programa.
     *
     * @throws IllegalArgumentException si no hay ningún usuario con ese id
     */
    @Transactional
    public void cambiar(Long id, String nombre, Rol rol, Boolean activo, String passwordNueva) {
        cambiar(id, nombre, rol, activo, passwordNueva, null);
    }

    /**
     * Como {@link #cambiar(Long, String, Rol, Boolean, String)}, sabiendo
     * quién lo pide (la pantalla de administración). Dos reglas, porque de
     * otro modo la app se queda sin nadie que la administre: nadie puede
     * desactivarse ni cambiarse el rol a sí mismo, y tiene que quedar al menos
     * un administrador activo (esta vale también sin {@code quien}).
     *
     * @param quien quien hace el cambio; null si no lo hace una persona
     * @throws IllegalArgumentException si no hay ningún usuario con ese id o
     *                                  el cambio rompe una de las dos reglas
     */
    @Transactional
    public void cambiar(Long id, String nombre, Rol rol, Boolean activo, String passwordNueva, UsuarioActual quien) {
        Usuario usuario = porId(id)
                .orElseThrow(() -> new IllegalArgumentException("No hay ningún usuario con id " + id));
        boolean seDesactiva = Boolean.FALSE.equals(activo) && usuario.isActivo();
        boolean cambiaDeRol = rol != null && rol != usuario.getRol();
        if (quien != null && id.equals(quien.id()) && (seDesactiva || cambiaDeRol)) {
            throw new IllegalArgumentException("No puedes desactivarte ni cambiarte el rol a ti mismo");
        }
        if (usuario.getRol() == Rol.ADMIN && usuario.isActivo() && (seDesactiva || cambiaDeRol)
                && repositorio.countByRolAndActivoTrue(Rol.ADMIN) <= 1) {
            throw new IllegalArgumentException("Tiene que quedar al menos un administrador activo");
        }
        String nombreNuevo = recortado(nombre);
        if (nombreNuevo != null) {
            usuario.setNombre(nombreNuevo);
        }
        if (rol != null) {
            usuario.cambiarRol(rol);
        }
        if (Boolean.TRUE.equals(activo)) {
            usuario.activar();
        } else if (Boolean.FALSE.equals(activo)) {
            usuario.desactivar();
        }
        if (passwordNueva != null && !passwordNueva.isBlank()) {
            usuario.cambiarPassword(encoder.encode(passwordNueva));
        }
    }

    /** El primer usuario TALLER activo (por orden de alta), al que saluda la cara del taller. */
    public Optional<Usuario> usuarioDeTaller() {
        return repositorio.findFirstByRolAndActivoTrueOrderByIdAsc(Rol.TALLER);
    }

    /**
     * Si la dirección pública es la del propio PC: los QR de las etiquetas de
     * patrones la llevan dentro, y un móvil que lea {@code localhost} se
     * busca a sí mismo.
     */
    static boolean apuntaALocalhost(String urlPublica) {
        if (urlPublica == null) {
            return false;
        }
        String url = urlPublica.toLowerCase(java.util.Locale.ROOT);
        return url.contains("localhost") || url.contains("127.0.0.1");
    }

    /** Primer arranque: sin usuarios, crea el {@code admin} si hay contraseña configurada; avisa de una URL local. */
    @Override
    public void run(ApplicationArguments args) {
        if (apuntaALocalhost(props.urlPublica())) {
            // No impide arrancar (en desarrollo es lo normal), pero una etiqueta impresa así no la lee nadie más.
            LOG.warn("modelos.url-publica apunta a localhost: los QR de las etiquetas impresas llevarán esa URL");
        }
        if (repositorio.count() > 0) {
            return;
        }
        String password = props.adminPassword();
        if (password == null || password.isBlank()) {
            LOG.warn("No hay usuarios y falta MODELOS_ADMIN_PASSWORD: nadie podrá entrar en /app hasta que se configure");
            return;
        }
        crear(new Alta("Administrador", LOGIN_ADMIN, password, Rol.ADMIN));
        LOG.info("Primer arranque de la app de modelos: creado el usuario {} con la contraseña de MODELOS_ADMIN_PASSWORD",
                LOGIN_ADMIN);
    }

    private String hashDe(String password) {
        return password == null || password.isBlank() ? null : encoder.encode(password);
    }

    private static String recortado(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
