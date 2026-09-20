package com.puntotres.packinglist.web;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.puntotres.packinglist.seguridad.Seccion;
import com.puntotres.packinglist.seguridad.UsuarioActual;

/**
 * Portada de la aplicación: elige entre las dos familias de salidas
 * (packing list + etiquetas de caja + volcado ERP, o etiquetas de artículo).
 *
 * La raíz redirige al menú en vez de servirlo para que la portada tenga una
 * URL propia y no se rompa ningún enlace o marcador a "/".
 *
 * El menú solo pinta las tarjetas de las secciones concedidas. Esconder una
 * tarjeta no es seguridad —la seguridad es el interceptor—: es no enseñar
 * puertas que no se pueden abrir.
 */
@Controller
public class MenuController {

    private final UsuarioActual usuarioActual;

    public MenuController(UsuarioActual usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    @GetMapping("/")
    public String raiz() {
        return "redirect:/menu";
    }

    @GetMapping("/menu")
    public String menu(Model model) {
        model.addAttribute("puede", usuarioActual.get()
                .map(usuario -> Arrays.stream(Seccion.values())
                        .filter(usuario::puedeAbrir)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Seccion.class))))
                .orElseGet(() -> EnumSet.noneOf(Seccion.class)));
        return "menu";
    }

    /**
     * Menú de la sección de etiquetas de artículo: producción (el flujo de
     * EtiquetasArticuloController) y, todavía sin hacer, SMS y prototipos.
     * Vive aquí y no en EtiquetasArticuloController porque es una portada, no
     * un paso de ese flujo. Su ruta ya la protege el interceptor.
     */
    @GetMapping("/etiquetas-articulo")
    public String etiquetasArticulo() {
        return "etiquetas-articulo";
    }
}
