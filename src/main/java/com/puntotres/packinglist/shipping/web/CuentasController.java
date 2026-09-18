package com.puntotres.packinglist.shipping.web;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.shipping.model.BillingRole;
import com.puntotres.packinglist.shipping.service.BillingAccountService;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;

/** Mantenimiento de las cuentas DHL: la propia y las de los clientes que pagan ellos el transporte o los aranceles. */
@Controller
@RequestMapping("/envios/cuentas")
public class CuentasController {

    private final BillingAccountService cuentas;

    public CuentasController(BillingAccountService cuentas) { this.cuentas = cuentas; }

    @GetMapping
    public String listado(Model model) {
        model.addAttribute("cuentas", cuentas.todas());
        model.addAttribute("roles", BillingRole.values());
        return "cuentas";
    }

    @PostMapping
    public String guardar(@RequestParam(required = false) Long id, @RequestParam String numero, @RequestParam String titular,
                          @RequestParam(defaultValue = "false") boolean propia,
                          @RequestParam(required = false) Set<BillingRole> roles, RedirectAttributes redirect) {
        try {
            cuentas.guardar(id, numero, titular, propia, roles == null ? EnumSet.noneOf(BillingRole.class) : roles);
            redirect.addFlashAttribute("mensaje", "Guardada la cuenta " + numero.trim() + ".");
        } catch (DatoInvalidoException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/envios/cuentas";
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id, @RequestParam boolean activa) {
        cuentas.activar(id, activa);
        return "redirect:/envios/cuentas";
    }
}
