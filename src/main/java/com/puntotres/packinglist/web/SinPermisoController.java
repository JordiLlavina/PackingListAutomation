package com.puntotres.packinglist.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.puntotres.packinglist.seguridad.Seccion;

/**
 * Lo que ve quien abre una sección que no tiene concedida. Es una pantalla y
 * no un 403 porque un 403 en blanco no dice qué hacer a continuación.
 */
@Controller
public class SinPermisoController {

    @GetMapping("/sin-permiso")
    public String sinPermiso(@RequestParam(required = false) String seccion, Model model) {
        String rotulo = null;
        if (seccion != null) {
            try {
                rotulo = Seccion.valueOf(seccion).getRotulo();
            } catch (IllegalArgumentException noEsUnaSeccion) {
                rotulo = null;
            }
        }
        model.addAttribute("rotuloSeccion", rotulo);
        return "sin-permiso";
    }
}
