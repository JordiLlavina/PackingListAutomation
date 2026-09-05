package com.puntotres.packinglist.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.model.MedidaCaja;
import com.puntotres.packinglist.persistence.CatalogoTarasJpa;

/**
 * Pantalla de la tabla de taras: el peso del cartón vacío de cada tamaño de
 * caja.
 *
 * Existe porque la tara es un dato del almacén y no una constante del
 * programa: entran tamaños nuevos y los pesos se corrigen a medida que se
 * pesa cada cartón. Sin esta pantalla, haber movido la tabla del yml a la
 * base de datos habría empeorado las cosas.
 */
@Controller
public class TarasController {

    private final CatalogoTarasJpa catalogo;

    public TarasController(CatalogoTarasJpa catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping("/taras")
    public String taras(Model model) {
        model.addAttribute("taras", catalogo.todas());
        return "taras";
    }

    @PostMapping("/taras")
    public String guardar(@RequestParam String medida,
                          @RequestParam double taraKg,
                          RedirectAttributes redirect) {
        // Una medida que no sea largo x ancho x alto no sirve: de ella salen
        // el orden del desplegable y la altura con la que se apila el palet.
        if (MedidaCaja.parse(medida).isEmpty()) {
            redirect.addFlashAttribute("mensajeError",
                    "La medida '" + medida + "' no se entiende. Se escribe largo x ancho x alto, "
                            + "en centímetros y con la altura al final: 60x40x45.");
            return "redirect:/taras";
        }
        if (taraKg < 0) {
            redirect.addFlashAttribute("mensajeError", "El peso del cartón no puede ser negativo.");
            return "redirect:/taras";
        }
        catalogo.guardar(MedidaCaja.parse(medida).orElseThrow().normalizada(), taraKg);
        redirect.addFlashAttribute("mensaje", "Guardado el cartón " + medida + ".");
        return "redirect:/taras";
    }
}
