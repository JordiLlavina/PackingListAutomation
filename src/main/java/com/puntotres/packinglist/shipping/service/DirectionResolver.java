package com.puntotres.packinglist.shipping.service;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.shipping.config.EnviosProperties;
import com.puntotres.packinglist.shipping.model.Direction;

/**
 * Sentido del envío respecto al país de la sede (España). No es respecto a
 * la UE: eso lo decide CustomsChecker, y son cosas distintas (ES→FR es
 * exportación sin aduana).
 */
@Component
public class DirectionResolver {

    private final String paisSede;

    public DirectionResolver(EnviosProperties propiedades) { this(propiedades.getPaisSede()); }

    DirectionResolver(String paisSede) { this.paisSede = paisSede.toUpperCase(); }

    public Direction resolver(String origen, String destino) {
        if (origen == null || destino == null) return null;
        boolean saleDeSede = paisSede.equalsIgnoreCase(origen);
        boolean llegaASede = paisSede.equalsIgnoreCase(destino);
        if (saleDeSede && llegaASede) return Direction.DOMESTIC;
        if (saleDeSede) return Direction.EXPORT;
        if (llegaASede) return Direction.IMPORT;
        return Direction.CROSS_TRADE;
    }
}
