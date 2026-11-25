package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.TaraProperties;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.OrigenPeso;

/**
 * Qué peso por unidad se aprende de un envío: solo de las cajas pesadas de
 * verdad, de una sola referencia y con el cartón pesado. La parte que guarda
 * en el catálogo la prueba PesosAprendidosRevisionTest, por la web.
 */
class AprendizajePesosTest {

    private final AprendizajePesos aprendizaje = new AprendizajePesos(null, taras());

    private static TaraProperties taras() {
        TaraProperties props = new TaraProperties();
        props.setTaras(Map.of(
                "60x40x40", 1.6,
                "60x40x51", 0.01));
        return props;
    }

    private static int siguienteNumero = 1;

    private static CajaData caja(String referencia, String medida, int unidades, Double bruto) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(siguienteNumero++);
        caja.setReferencia(referencia);
        caja.setCodigoColor("NOIR");
        caja.setTamanoCaja(medida);
        caja.setCantidad(unidades);
        caja.setPesoBrutoKg(bruto);
        return caja;
    }

    @Test
    void deUnaCajaPesadaSaleElNetoPorUnidad() {
        CajaData pesada = caja("REF", "60x40x40", 10, 11.6);   // (11.6 - 1.6) / 10

        AprendizajePesos.Medidos medidos = aprendizaje.medir(List.of(List.of(pesada)));

        assertEquals(Map.of("REF", 1.0), medidos.pesoPorUnidad());
    }

    @Test
    void elNetoTecleadoManda() {
        CajaData pesada = caja("REF", "60x40x40", 10, null);
        pesada.setPesoNetoKg(8.0);

        assertEquals(0.8, aprendizaje.medir(List.of(List.of(pesada))).pesoPorUnidad().get("REF"));
    }

    @Test
    void variasCajasPesadasSePromedianEntreDestinaciones() {
        CajaData paris = caja("REF", "60x40x40", 10, 11.6);    // 1.0
        CajaData china = caja("REF", "60x40x40", 10, 13.6);    // 1.2

        AprendizajePesos.Medidos medidos = aprendizaje.medir(List.of(List.of(paris), List.of(china)));

        assertEquals(1.1, medidos.pesoPorUnidad().get("REF"), 1e-9);
    }

    @Test
    void loCalculadoNoSeAprende() {
        // Aprender de lo inferido o de lo que vino del catálogo sería
        // aprenderse a sí mismo: un error se perpetuaría solo.
        CajaData inferida = caja("INF", "60x40x40", 10, 11.6);
        inferida.setOrigenPeso(OrigenPeso.INFERIDO);
        CajaData delCatalogo = caja("CAT", "60x40x40", 10, 11.6);
        delCatalogo.setOrigenPeso(OrigenPeso.CATALOGO);

        assertTrue(aprendizaje.medir(List.of(List.of(inferida, delCatalogo))).pesoPorUnidad().isEmpty());
    }

    @Test
    void unBultoConDosReferenciasNoSeAprende() {
        // No se sabe qué parte del peso es de cada artículo.
        CajaData lider = caja("REF-A", "60x40x40", 4, 11.6);
        CajaData segunda = caja("REF-B", "60x40x40", 6, null);
        segunda.setNumeroCaja(lider.getNumeroCaja());

        assertTrue(aprendizaje.medir(List.of(List.of(lider, segunda))).pesoPorUnidad().isEmpty());
    }

    @Test
    void conElCartonSinPesarNoSeAprendeYSeDiceCualEs() {
        CajaData pesada = caja("REF", "60x40x51", 10, 11.6);

        AprendizajePesos.Medidos medidos = aprendizaje.medir(List.of(List.of(pesada)));

        assertTrue(medidos.pesoPorUnidad().isEmpty());
        assertEquals(Map.of("REF", "60x40x51"), medidos.cartonSinPesar());
    }

    @Test
    void siOtraCajaDeLaReferenciaSiValeNoSeAvisaDelCartonSinPesar() {
        CajaData buena = caja("REF", "60x40x40", 10, 11.6);
        CajaData sinPesar = caja("REF", "60x40x51", 10, 11.6);

        AprendizajePesos.Medidos medidos = aprendizaje.medir(List.of(List.of(buena, sinPesar)));

        assertEquals(1.0, medidos.pesoPorUnidad().get("REF"));
        assertFalse(medidos.cartonSinPesar().containsKey("REF"));
    }
}
