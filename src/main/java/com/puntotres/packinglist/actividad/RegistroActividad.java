package com.puntotres.packinglist.actividad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.seguridad.Seccion;
import com.puntotres.packinglist.seguridad.Usuario;
import com.puntotres.packinglist.seguridad.UsuarioActual;

/**
 * Deja constancia de quién hizo qué.
 *
 * Un fallo escribiendo el evento NO puede tumbar un envío: la regla del
 * proyecto es nunca fallar en silencio y nunca bloquear por algo que un humano
 * puede resolver, y aquí eso significa capturar la excepción, dejar un WARN en
 * el log —que no es silencio— y seguir. Que no se pueda apuntar quién generó
 * un documento no es razón para no generarlo.
 */
@Service
public class RegistroActividad {

    private static final Logger log = LoggerFactory.getLogger(RegistroActividad.class);

    private final EventoActividadRepository repositorio;
    private final UsuarioActual usuarioActual;

    public RegistroActividad(EventoActividadRepository repositorio, UsuarioActual usuarioActual) {
        this.repositorio = repositorio;
        this.usuarioActual = usuarioActual;
    }

    public void registrar(TipoEvento tipo, Seccion seccion) {
        registrar(tipo, seccion, null, null, null);
    }

    public void registrar(TipoEvento tipo, Seccion seccion,
                          String cliente, Integer cantidad, String detalle) {
        Usuario usuario;
        try {
            // Resolver quién es también puede fallar (fuera de una petición
            // no hay usuario actual), y eso tampoco puede tumbar nada.
            usuario = usuarioActual.get().orElse(null);
        } catch (RuntimeException fallo) {
            log.warn("No se ha podido resolver quién registra el evento {}: {}", tipo, fallo.toString());
            usuario = null;
        }
        registrarPara(usuario, tipo, seccion, cliente, cantidad, detalle);
    }

    /** Con el usuario dado en vez del de la sesión: lo usa el login. */
    public void registrarPara(Usuario usuario, TipoEvento tipo, Seccion seccion,
                              String cliente, Integer cantidad, String detalle) {
        try {
            repositorio.save(new EventoActividad(
                    usuario == null ? null : usuario.getId(),
                    usuario == null ? "desconocido" : usuario.getCorreo(),
                    tipo,
                    seccion,
                    recortar(cliente, 64),
                    cantidad,
                    recortar(detalle, EventoActividad.LARGO_DETALLE)));
        } catch (RuntimeException fallo) {
            log.warn("No se ha podido registrar el evento {}: {}", tipo, fallo.toString());
        }
    }

    private static String recortar(String texto, int largo) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= largo ? texto : texto.substring(0, largo);
    }
}
