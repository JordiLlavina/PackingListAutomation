package com.puntotres.packinglist.shipping.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.shipping.model.ExportReasonType;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;
import com.puntotres.packinglist.shipping.service.ShipmentReasonService;

/** Mantenimiento de los motivos de envío y su equivalencia en la declaración de exportación de DHL. */
@Controller
@RequestMapping("/envios/motivos")
public class MotivosController {

    private final ShipmentReasonService motivos;

    public MotivosController(ShipmentReasonService motivos) { this.motivos = motivos; }

    @GetMapping
    public String listado(Model model) {
        model.addAttribute("motivos", motivos.todos());
        model.addAttribute("motivosDhl", ExportReasonType.values());
        return "motivos";
    }

    @PostMapping
    public String guardar(@RequestParam(required = false) Long id, @RequestParam String nombre,
                          @RequestParam(required = false) ExportReasonType motivoDhl, RedirectAttributes redirect) {
        try {
            motivos.guardar(id, nombre, motivoDhl);
            redirect.addFlashAttribute("mensaje", "Guardado el motivo " + nombre.trim() + ".");
        } catch (DatoInvalidoException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/envios/motivos";
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id, @RequestParam boolean activo) {
        motivos.activar(id, activo);
        return "redirect:/envios/motivos";
    }
}
