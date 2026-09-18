package com.puntotres.packinglist.shipping.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.shipping.config.EnviosProperties;

/** Hay declaración de aduana salvo cuando los dos países están en la unión aduanera del yml. */
@Component
public class CustomsChecker {

    private final Set<String> unionAduanera;

    public CustomsChecker(EnviosProperties propiedades) { this(propiedades.getPaisesUnionAduanera()); }

    CustomsChecker(List<String> paises) {
        this.unionAduanera = paises.stream().map(String::toUpperCase).collect(Collectors.toSet());
    }

    public boolean esAduanero(String origen, String destino) {
        if (origen == null || destino == null) return true;
        return !(unionAduanera.contains(origen.toUpperCase()) && unionAduanera.contains(destino.toUpperCase()));
    }
}
