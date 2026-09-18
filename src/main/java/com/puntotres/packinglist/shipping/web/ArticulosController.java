package com.puntotres.packinglist.shipping.web;

import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.shipping.service.ArticleService;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;

/**
 * Mantenimiento del catálogo aduanero de artículos. La importación desde el
 * excel de pedido del cliente se añade en la Task 10; aquí solo el alta y
 * edición manual.
 */
@Controller
@RequestMapping("/envios/articulos")
public class ArticulosController {

    private final ArticleService articulos;

    public ArticulosController(ArticleService articulos) { this.articulos = articulos; }

    @GetMapping
    public String listado(Model model) {
        model.addAttribute("articulos", articulos.todos());
        return "articulos";
    }

    @PostMapping
    public String guardar(@RequestParam(required = false) Long id, @RequestParam String referencia, @RequestParam String descripcion,
                          @RequestParam String codigoHs, @RequestParam String paisOrigen,
                          @RequestParam(required = false) Double pesoNeto, @RequestParam(required = false) Double pesoBruto,
                          @RequestParam(required = false) BigDecimal valorUnitario, RedirectAttributes redirect) {
        try {
            articulos.guardar(id, referencia, descripcion, codigoHs, paisOrigen, pesoNeto, pesoBruto, valorUnitario);
            redirect.addFlashAttribute("mensaje", "Guardado el artículo " + referencia.trim() + ".");
        } catch (DatoInvalidoException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/envios/articulos";
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id, @RequestParam boolean activo) {
        articulos.activar(id, activo);
        return "redirect:/envios/articulos";
    }
}
