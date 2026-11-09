package com.puntotres.packinglist.modelos.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.puntotres.packinglist.modelos.api.dto.ResumenBandeja;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.Modificaciones;

/**
 * Los números de la bandeja de quien la mira, según su lado: lo que pinta el
 * contador de la app. Qué cuenta cada número es de
 * {@link Modificaciones#resumen}.
 */
@RestController
@RequestMapping("/api/bandeja")
public class BandejaController {

    private final Modificaciones modificaciones;

    public BandejaController(Modificaciones modificaciones) {
        this.modificaciones = modificaciones;
    }

    @GetMapping("/resumen")
    public ResumenBandeja resumen(@AuthenticationPrincipal UsuarioActual quien) {
        return modificaciones.resumen(quien);
    }
}
