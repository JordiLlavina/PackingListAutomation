package com.puntotres.packinglist.modelos.seguridad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;

/**
 * Un usuario de la app de modelos ya comprobado, con contraseña o con la
 * cookie de su dispositivo. Sus papeles son {@code ROLE_<rol>} y, el ADMIN,
 * también {@code ROLE_DESPACHO}: el administrador es un despacho que además
 * gestiona usuarios, así que un {@code hasRole('DESPACHO')} lo deja pasar sin
 * tener que nombrarlo en cada método.
 */
public class AutenticacionModelos extends AbstractAuthenticationToken {

    private final UsuarioActual principal;

    public AutenticacionModelos(UsuarioActual principal) {
        super(papelesDe(principal.rol()));
        this.principal = principal;
        setAuthenticated(true);
    }

    /** La autenticación de ese usuario; {@code dispositivoId} null si entró con contraseña. */
    public static AutenticacionModelos de(Usuario usuario, Long dispositivoId) {
        return new AutenticacionModelos(
                new UsuarioActual(usuario.getId(), usuario.getNombre(), usuario.getRol(), dispositivoId));
    }

    @Override
    public UsuarioActual getPrincipal() {
        return principal;
    }

    /** Nunca guarda la contraseña ni el token. */
    @Override
    public Object getCredentials() {
        return null;
    }

    private static Collection<GrantedAuthority> papelesDe(Rol rol) {
        List<GrantedAuthority> papeles = new ArrayList<>();
        papeles.add(new SimpleGrantedAuthority("ROLE_" + rol.name()));
        if (rol == Rol.ADMIN) {
            papeles.add(new SimpleGrantedAuthority("ROLE_" + Rol.DESPACHO.name()));
        }
        return papeles;
    }
}
