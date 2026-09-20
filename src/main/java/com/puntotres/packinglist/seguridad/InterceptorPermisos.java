package com.puntotres.packinglist.seguridad;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import com.puntotres.packinglist.actividad.RegistroActividad;
import com.puntotres.packinglist.actividad.TipoEvento;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Deja pasar solo lo que la persona tenga concedido.
 *
 * Lee de la base de datos en cada petición y no de las autoridades de la
 * sesión: esas se calculan en el login, así que quitarle un permiso a alguien
 * que está dentro no surtiría efecto hasta que volviera a entrar.
 */
@Component
public class InterceptorPermisos implements HandlerInterceptor {

    private final ObjectProvider<UsuarioActual> usuarioActual;
    // Como ObjectProvider y no directo: el registro depende de un bean de
    // ámbito petición y este interceptor se construye antes de que exista.
    private final ObjectProvider<RegistroActividad> registro;

    public InterceptorPermisos(ObjectProvider<UsuarioActual> usuarioActual,
                               ObjectProvider<RegistroActividad> registro) {
        this.usuarioActual = usuarioActual;
        this.registro = registro;
    }

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta,
                             Object manejador) throws Exception {
        // Un fichero estático no es una sección, y una URL que no existe cae
        // en el mismo manejador de recursos: dejarla pasar es lo que hace que
        // responda 404 en vez de mandar a "sin permiso" por algo que no es
        // un permiso. La cadena de seguridad ya exige identidad para llegar
        // hasta aquí.
        if (manejador instanceof ResourceHttpRequestHandler) {
            return true;
        }

        String uri = peticion.getRequestURI();
        if (Seccion.esPublica(uri)) {
            return true;
        }

        Optional<Seccion> seccion = Seccion.deRuta(uri);
        if (seccion.isEmpty()) {
            // Una ruta sin dueño no debería existir: CoberturaDeRutasTest lo
            // impide. Si aparece una, se cierra en vez de abrirse.
            registro.getObject().registrar(TipoEvento.ACCESO_DENEGADO, null,
                    null, null, "Ha intentado abrir " + uri);
            respuesta.sendRedirect(peticion.getContextPath() + "/sin-permiso");
            return false;
        }

        Optional<Usuario> usuario = usuarioActual.getObject().get();
        if (usuario.isPresent() && usuario.get().puedeAbrir(seccion.get())) {
            return true;
        }

        registro.getObject().registrar(TipoEvento.ACCESO_DENEGADO, seccion.get(),
                null, null, "Ha intentado abrir " + uri);
        respuesta.sendRedirect(
                peticion.getContextPath() + "/sin-permiso?seccion=" + seccion.get().name());
        return false;
    }
}
