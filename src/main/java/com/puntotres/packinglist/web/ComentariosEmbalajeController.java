package com.puntotres.packinglist.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.persistence.ArchivoComentariosEmbalaje;

/**
 * Pantalla de los comentarios tipo de la hoja de «Info de embalaje»: frases que
 * se repiten de un envío a otro («Cajas pesadas», «Frágil»...) y que se
 * guardan una vez, con un nombre corto y su texto largo.
 *
 * Existe porque el formulario de la hoja ofrece un desplegable con estos
 * comentarios y, al elegir uno, <strong>añade</strong> su texto al cuadro de
 * comentarios de la hoja (no lo sustituye: se pueden sumar varios); el lápiz
 * junto a ese desplegable lleva aquí. Es la gemela de {@code /temporadas}: una
 * fila editable por comentario y un alta debajo.
 *
 * Un nombre repetido o vacío no se pierde en silencio: el aviso de
 * {@link ArchivoComentariosEmbalaje#guardar} vuelve a la pantalla tal cual.
 */
@Controller
public class ComentariosEmbalajeController {

    private final ArchivoComentariosEmbalaje archivo;

    public ComentariosEmbalajeController(ArchivoComentariosEmbalaje archivo) {
        this.archivo = archivo;
    }

    @GetMapping("/comentarios-embalaje")
    public String comentarios(Model model) {
        model.addAttribute("comentarios", archivo.todos());
        return "comentarios-embalaje";
    }

    /** Alta (sin {@code id}) o corrección (con él). */
    @PostMapping("/comentarios-embalaje")
    public String guardar(@RequestParam(required = false) Long id,
                          @RequestParam String nombre,
                          @RequestParam(required = false, defaultValue = "") String texto,
                          RedirectAttributes redirect) {
        try {
            var guardado = archivo.guardar(id, nombre, texto);
            redirect.addFlashAttribute("mensaje", "Guardado el comentario «" + guardado.getNombre() + "».");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage() + ".");
        }
        return "redirect:/comentarios-embalaje";
    }

    @PostMapping("/comentarios-embalaje/borrar")
    public String borrar(@RequestParam Long id, RedirectAttributes redirect) {
        archivo.uno(id).ifPresent(comentario -> redirect.addFlashAttribute("mensaje",
                "Borrado el comentario «" + comentario.getNombre() + "»."));
        archivo.borrar(id);
        return "redirect:/comentarios-embalaje";
    }
}
