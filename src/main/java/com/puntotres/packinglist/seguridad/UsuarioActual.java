package com.puntotres.packinglist.seguridad;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Quién está haciendo esta petición.
 *
 * De ámbito petición y con el resultado guardado dentro: el interceptor lo
 * pregunta una vez y los controladores y el registro de actividad lo vuelven a
 * preguntar, y eso sería una consulta por cada uno.
 */
@Component
@RequestScope
public class UsuarioActual {

    private final UsuarioRepository repositorio;
    private Optional<Usuario> resuelto;

    public UsuarioActual(UsuarioRepository repositorio) {
        this.repositorio = repositorio;
    }

    public Optional<Usuario> get() {
        if (resuelto == null) {
            resuelto = buscar();
        }
        return resuelto;
    }

    private Optional<Usuario> buscar() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof OidcUser oidcUser)) {
            return Optional.empty();
        }
        String oid = oidcUser.getClaimAsString("oid");
        return oid == null ? Optional.empty() : repositorio.findByOidEntra(oid);
    }
}
