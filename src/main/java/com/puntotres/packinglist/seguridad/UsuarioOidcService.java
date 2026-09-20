package com.puntotres.packinglist.seguridad;

import java.time.OffsetDateTime;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.actividad.RegistroActividad;
import com.puntotres.packinglist.actividad.TipoEvento;

/**
 * Da de alta sola a la persona la primera vez que entra, y sin ningún
 * permiso: quien entra por primera vez ve el menú vacío y un texto que dice a
 * quién pedirlos.
 *
 * Es lo contrario de una lista negra, y es lo correcto: el día que entre en
 * la empresa alguien de administración, no verá los escandallos por defecto.
 */
@Service
public class UsuarioOidcService extends OidcUserService {

    private final UsuarioRepository repositorio;
    private final SeguridadProperties propiedades;
    private final RegistroActividad registro;

    public UsuarioOidcService(UsuarioRepository repositorio, SeguridadProperties propiedades,
                              RegistroActividad registro) {
        this.repositorio = repositorio;
        this.propiedades = propiedades;
        this.registro = registro;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest peticion) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(peticion);

        String oid = oidcUser.getClaimAsString("oid");
        String correo = primerNoVacio(
                oidcUser.getEmail(),
                oidcUser.getPreferredUsername(),
                oidcUser.getClaimAsString("upn"));
        String nombre = primerNoVacio(oidcUser.getFullName(), correo, oid);

        entrar(oid, correo, nombre);
        return oidcUser;
    }

    /**
     * Busca la persona por su oid, la crea si no está y le estampa el último
     * acceso. Separado de loadUser para poder probarlo sin montar un token.
     */
    @Transactional
    public Usuario entrar(String oidEntra, String correo, String nombre) {
        Usuario usuario = repositorio.findByOidEntra(oidEntra)
                .orElseGet(() -> altaNueva(oidEntra, correo, nombre));

        usuario.setCorreo(correo);
        usuario.setNombre(nombre);
        usuario.setUltimoAcceso(OffsetDateTime.now());
        Usuario guardado = repositorio.save(usuario);
        registro.registrarPara(guardado, TipoEvento.ENTRADA, null, null, null, null);
        return guardado;
    }

    private Usuario altaNueva(String oidEntra, String correo, String nombre) {
        Usuario nuevo = new Usuario(oidEntra, correo, nombre);
        // Solo aquí, en el alta: ver el javadoc de SeguridadProperties.
        nuevo.setAdministrador(propiedades.esAdministradorInicial(correo));
        return nuevo;
    }

    private static String primerNoVacio(String... candidatos) {
        for (String candidato : candidatos) {
            if (candidato != null && !candidato.isBlank()) {
                return candidato;
            }
        }
        return "desconocido";
    }
}
