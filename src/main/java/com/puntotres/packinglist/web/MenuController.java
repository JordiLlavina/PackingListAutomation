package com.puntotres.packinglist.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Portada de la aplicación: elige entre las dos familias de salidas
 * (packing list + etiquetas de caja + volcado ERP, o etiquetas de artículo).
 *
 * La raíz redirige al menú en vez de servirlo para que la portada tenga una
 * URL propia y no se rompa ningún enlace o marcador a "/".
 */
@Controller
public class MenuController {

    @GetMapping("/")
    public String raiz() {
        return "redirect:/menu";
    }

    @GetMapping("/menu")
    public String menu() {
        return "menu";
    }
}
