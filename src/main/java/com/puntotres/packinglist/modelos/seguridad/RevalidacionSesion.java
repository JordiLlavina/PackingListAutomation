package com.puntotres.packinglist.modelos.seguridad;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Una sesión de contraseña (la del PC, que dura 8 horas) vuelve a mirar a su
 * usuario en cada petición: si el admin lo desactiva (o ya no existe), la
 * sesión se corta en la petición siguiente —se quita el contexto de la
 * petición Y de la sesión, así que lo que venga después es anónimo y da
 * 401—; si le cambia el rol o el nombre, la petición ya va con los nuevos y
 * la sesión se queda con ellos. Sin esto un despacho pasado a TALLER seguiría
 * creando órdenes hasta que caducara su sesión.
 *
 * Las autenticaciones con la cookie de un dispositivo no pasan por aquí:
 * {@link FiltroDispositivo} ya mira el dispositivo y el usuario en cada
 * petición y no las guarda en la sesión.
 *
 * No es un bean, por lo mismo que {@link FiltroDispositivo}: Spring Boot
 * registraría un filtro bean en TODAS las rutas del programa.
 */
public class RevalidacionSesion extends OncePerRequestFilter {

    private final Usuarios usuarios;
    private final SecurityContextHolderStrategy estrategia;
    private final SecurityContextRepository repositorioContexto = new HttpSessionSecurityContextRepository();

    public RevalidacionSesion(Usuarios usuarios, SecurityContextHolderStrategy estrategia) {
        this.usuarios = usuarios;
        this.estrategia = estrategia;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication autenticacion = estrategia.getContext().getAuthentication();
        if (autenticacion instanceof AutenticacionModelos modelos && modelos.getPrincipal().dispositivoId() == null) {
            UsuarioActual actual = modelos.getPrincipal();
            Optional<Usuario> usuario = usuarios.porId(actual.id()).filter(Usuario::isActivo);
            if (usuario.isEmpty()) {
                estrategia.clearContext();
                HttpSession sesion = request.getSession(false);
                if (sesion != null) {
                    sesion.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                }
            } else if (usuario.get().getRol() != actual.rol()
                    || !Objects.equals(usuario.get().getNombre(), actual.nombre())) {
                SecurityContext contexto = estrategia.createEmptyContext();
                contexto.setAuthentication(AutenticacionModelos.de(usuario.get(), null));
                estrategia.setContext(contexto);
                repositorioContexto.saveContext(contexto, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
