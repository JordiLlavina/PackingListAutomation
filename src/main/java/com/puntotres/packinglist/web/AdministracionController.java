package com.puntotres.packinglist.web;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.actividad.RegistroActividad;
import com.puntotres.packinglist.actividad.TipoEvento;
import com.puntotres.packinglist.seguridad.Seccion;
import com.puntotres.packinglist.seguridad.Usuario;
import com.puntotres.packinglist.seguridad.UsuarioActual;
import com.puntotres.packinglist.seguridad.UsuarioRepository;

/**
 * Quién entra y qué ve cada uno.
 *
 * No hay botón de borrar: se desactiva. Borrar la fila deja el histórico de
 * actividad apuntando a un id que ya no existe, y el histórico es justamente
 * lo que se quiere conservar.
 */
@Controller
public class AdministracionController {

    private final UsuarioRepository usuarios;
    private final UsuarioActual usuarioActual;
    private final RegistroActividad registro;

    public AdministracionController(UsuarioRepository usuarios, UsuarioActual usuarioActual,
                                    RegistroActividad registro) {
        this.usuarios = usuarios;
        this.usuarioActual = usuarioActual;
        this.registro = registro;
    }

    @GetMapping("/administracion/usuarios")
    public String usuarios(Model model) {
        model.addAttribute("usuarios", usuarios.findAllByOrderByNombreAsc());
        model.addAttribute("secciones", Seccion.concedibles());
        model.addAttribute("yo", usuarioActual.get().map(Usuario::getId).orElse(null));
        return "administracion-usuarios";
    }

    @PostMapping("/administracion/usuarios")
    @Transactional
    public String guardar(@ModelAttribute AdministracionForm form, RedirectAttributes redirect) {
        Long yo = usuarioActual.get().map(Usuario::getId).orElse(null);
        int cambiados = 0;

        for (AdministracionForm.FilaUsuario fila : form.getFilas()) {
            if (fila.getUsuarioId() == null) {
                continue;
            }
            Usuario usuario = usuarios.findById(fila.getUsuarioId()).orElse(null);
            if (usuario == null) {
                continue;
            }

            // Quien administra no puede quitarse a sí mismo el administrador ni
            // desactivarse: quedarse sin ningún administrador deja la
            // aplicación sin forma de conceder permisos salvo tocando la base
            // de datos a mano.
            boolean estabaActivo = usuario.isActivo();
            Set<Seccion> antes = new HashSet<>(usuario.getPermisos());
            boolean soyYo = Objects.equals(usuario.getId(), yo);
            if (!soyYo) {
                usuario.setAdministrador(fila.isAdministrador());
                usuario.setActivo(fila.isActivo());
            }

            for (Seccion seccion : Seccion.concedibles()) {
                if (fila.getSecciones().contains(seccion)) {
                    usuario.conceder(seccion);
                } else {
                    usuario.retirar(seccion);
                }
            }
            if (estabaActivo != usuario.isActivo()) {
                registro.registrar(usuario.isActivo()
                                ? TipoEvento.USUARIO_REACTIVADO : TipoEvento.USUARIO_DESACTIVADO,
                        Seccion.ADMINISTRACION, null, null, usuario.getCorreo());
            }
            if (!antes.equals(usuario.getPermisos())) {
                registro.registrar(TipoEvento.PERMISOS_CAMBIADOS, Seccion.ADMINISTRACION,
                        null, usuario.getPermisos().size(),
                        usuario.getCorreo() + ": " + usuario.getPermisos());
            }
            usuarios.save(usuario);
            cambiados++;
        }

        redirect.addFlashAttribute("mensaje", cambiados == 1
                ? "Guardado 1 usuario."
                : "Guardados " + cambiados + " usuarios.");
        return "redirect:/administracion/usuarios";
    }
}
