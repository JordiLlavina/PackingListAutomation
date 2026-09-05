package com.puntotres.packinglist.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * La tabla de taras se consume a través de una interfaz porque tiene dos
 * orígenes: el bloque packing-list.taras del yml (semilla, y lo que usan
 * Main.java y los tests unitarios) y la tabla de la base de datos.
 */
class CatalogoTarasTest {

    @Test
    void taraPropertiesEsUnCatalogoDeTaras() {
        TaraProperties props = new TaraProperties();
        Map<String, Double> taras = new LinkedHashMap<>();
        taras.put("60x40x40", 1.6);
        props.setTaras(taras);

        CatalogoTaras catalogo = props;

        assertEquals(1.6, catalogo.taraPara(" 60X40X40 ").orElseThrow());
        assertEquals(List.of("60x40x40"), catalogo.tamanosDeMayorAMenor());
    }
}
