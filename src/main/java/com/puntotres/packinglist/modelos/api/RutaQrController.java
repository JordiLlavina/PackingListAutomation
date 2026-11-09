package com.puntotres.packinglist.modelos.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.puntotres.packinglist.modelos.service.Modelos;

/**
 * La dirección que lleva el QR de la etiqueta de patrones: {@code /m/{id}}.
 * Es corta a propósito (cuanto menos texto, menos módulos y más fácil de
 * leer) y no cambia aunque la app reorganice sus pantallas: solo reenvía a la
 * ficha del modelo en la app.
 *
 * Es pública: quien la abre sin haber entrado llega a la app, que le pide
 * entrar. Si el modelo se fusionó con otro, lleva al superviviente; si no
 * existe, lleva igualmente a su id y la app dice que no está.
 */
@Controller
public class RutaQrController {

    private final Modelos modelos;

    public RutaQrController(Modelos modelos) {
        this.modelos = modelos;
    }

    @GetMapping("/m/{id}")
    public String abrir(@PathVariable Long id) {
        return "redirect:/app/modelos/" + modelos.resolverId(id).orElse(id);
    }
}
