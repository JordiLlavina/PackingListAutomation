package com.puntotres.packinglist.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void unCartonConLaTaraDeRellenoNoEstaPesado() {
        // En /taras un cartón que aún no se ha pesado se deja en 0,01: tiene
        // tara para que el programa funcione, pero un neto calculado con ella
        // es casi un bruto y no se puede guardar como peso del artículo.
        TaraProperties props = new TaraProperties();
        Map<String, Double> taras = new LinkedHashMap<>();
        taras.put("60x40x40", 1.6);
        taras.put("60x40x51", 0.01);
        props.setTaras(taras);

        assertTrue(props.cartonPesado("60X40X40"));
        assertFalse(props.cartonPesado("60x40x51"));
        assertFalse(props.cartonPesado("77x77x77"), "sin tara tampoco");
        assertFalse(props.cartonPesado(null));
    }
}
