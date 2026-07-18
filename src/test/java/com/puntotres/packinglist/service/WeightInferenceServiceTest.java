package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.TaraProperties;
import com.puntotres.packinglist.model.CajaData;

class WeightInferenceServiceTest {

    private final WeightInferenceService service = new WeightInferenceService(taras());

    private static TaraProperties taras() {
        TaraProperties props = new TaraProperties();
        props.setTaras(Map.of(
                "60x40x40", 1.6,
                "60x40x30", 1.2));
        return props;
    }

    @Test
    void infierePesosPromediandoLasCajasConocidas() {
        // Conocidas: (21.6-1.6)/20 = 1.0 y (13.6-1.6)/10 = 1.2 -> unitario medio 1.1
        CajaData conocida1 = caja("60x40x40", 20, 21.6);
        CajaData conocida2 = caja("60x40x40", 10, 13.6);
        CajaData sinPeso = caja("60x40x30", 10, null);

        service.inferirPesos(List.of(conocida1, conocida2, sinPeso));

        assertEquals(11.0, sinPeso.getPesoNetoKg());   // 10 * 1.1
        assertEquals(12.2, sinPeso.getPesoBrutoKg());  // 11.0 + tara 1.2
    }

    @Test
    void completaElNetoDeCajasConBrutoConocido() {
        CajaData conocida = caja("60x40x40", 20, 21.6);

        service.inferirPesos(List.of(conocida));

        assertEquals(20.0, conocida.getPesoNetoKg()); // 21.6 - tara 1.6
        assertEquals(21.6, conocida.getPesoBrutoKg());
    }

    @Test
    void tamanoSinTaraQuedaPendienteAunqueHayaConocidas() {
        CajaData conocida = caja("60x40x40", 20, 21.6);
        CajaData sinTara = caja("50x30x20", 10, null);

        service.inferirPesos(List.of(conocida, sinTara));

        assertNull(sinTara.getPesoNetoKg());
        assertNull(sinTara.getPesoBrutoKg());
    }

    @Test
    void sinNingunaCajaConocidaTodoQuedaPendiente() {
        CajaData caja1 = caja("60x40x40", 20, null);
        CajaData caja2 = caja("60x40x30", 10, null);

        service.inferirPesos(List.of(caja1, caja2));

        assertNull(caja1.getPesoNetoKg());
        assertNull(caja1.getPesoBrutoKg());
        assertNull(caja2.getPesoNetoKg());
        assertNull(caja2.getPesoBrutoKg());
    }

    @Test
    void normalizaElTamanoDeCajaAlBuscarLaTara() {
        // Mayúsculas y espacios: debe casar con "60x40x40" del yml
        CajaData conocida = caja(" 60X40X40 ", 20, 21.6);
        CajaData sinPeso = caja("60 x 40 x 30", 10, null);

        service.inferirPesos(List.of(conocida, sinPeso));

        assertEquals(10.0, sinPeso.getPesoNetoKg());  // 10 * 1.0
        assertEquals(11.2, sinPeso.getPesoBrutoKg()); // 10.0 + 1.2
    }

    @Test
    void agrupaPorReferenciaSinMezclarPesosUnitarios() {
        CajaData bolso = caja("60x40x40", 10, 11.6);   // unitario 1.0
        bolso.setReferencia("BOLSO");
        CajaData bolsoSinPeso = caja("60x40x40", 5, null);
        bolsoSinPeso.setReferencia("BOLSO");
        CajaData cinturonSinPeso = caja("60x40x30", 5, null); // su referencia no tiene conocidas
        cinturonSinPeso.setReferencia("CINTURON");

        service.inferirPesosPorReferencia(List.of(bolso, bolsoSinPeso, cinturonSinPeso));

        assertEquals(5.0, bolsoSinPeso.getPesoNetoKg());
        assertEquals(6.6, bolsoSinPeso.getPesoBrutoKg());
        assertNull(cinturonSinPeso.getPesoNetoKg());
        assertNull(cinturonSinPeso.getPesoBrutoKg());
    }

    private static CajaData caja(String tamano, int cantidad, Double pesoBruto) {
        CajaData caja = new CajaData();
        caja.setTamanoCaja(tamano);
        caja.setCantidad(cantidad);
        caja.setPesoBrutoKg(pesoBruto);
        return caja;
    }
}
