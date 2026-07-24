package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void tamanoSinTaraQuedaPendienteYSeAvisa() {
        CajaData conocida = caja("60x40x40", 20, 21.6);
        CajaData sinTara = caja("50x30x20", 10, null);

        ResultadoInferencia resultado = service.inferirPesos(List.of(conocida, sinTara));

        assertNull(sinTara.getPesoNetoKg());
        assertNull(sinTara.getPesoBrutoKg());
        // Ya no falla en silencio: el tamaño sin tara se reporta.
        assertEquals(1, resultado.getAvisos().size());
        assertTrue(resultado.getAvisos().get(0).contains("50x30x20"));
    }

    @Test
    void netoManualCompletaSuBrutoYDesbloqueaSuReferencia() {
        // Un neto tecleado a mano vale igual que un bruto: unitario = neto/cantidad.
        CajaData conNeto = caja("60x40x40", 10, null);
        conNeto.setPesoNetoKg(10.0);                    // unitario 1.0
        CajaData sinPeso = caja("60x40x30", 5, null);

        ResultadoInferencia resultado = service.inferirPesos(List.of(conNeto, sinPeso));

        assertEquals(11.6, conNeto.getPesoBrutoKg());   // 10.0 + tara 1.6
        assertEquals(5.0, sinPeso.getPesoNetoKg());     // 5 * 1.0
        assertEquals(6.2, sinPeso.getPesoBrutoKg());    // 5.0 + tara 1.2
        assertTrue(resultado.getAvisos().isEmpty());
    }

    @Test
    void elMismoTamanoSinTaraEnVariasReferenciasSeAvisaUnaSolaVez() {
        CajaData bolso = caja("50x30x20", 10, 9.0);
        bolso.setReferencia("BOLSO");
        CajaData cinturon = caja("50x30x20", 5, 4.0);
        cinturon.setReferencia("CINTURON");

        ResultadoInferencia resultado =
                service.inferirPesosPorReferencia(List.of(bolso, cinturon));

        assertEquals(1, resultado.getAvisos().size());
    }

    @Test
    void enCajaMixtaElPesoEsDeLaCajaEnteraYSoloLoLlevaLaLinea() {
        // Una caja conocida fija el peso unitario (1.0 kg/ud). La caja física
        // 5 es mixta: 3 tallas (3 + 31 + 3 = 37 uds) con el mismo nº de caja.
        // Su peso se infiere sobre las 37 uds y con UNA tara, y recae en la
        // línea líder; las demás tallas de la caja quedan a null.
        CajaData conocida = caja("60x40x40", 10, 11.6);   // (11.6-1.6)/10 = 1.0
        CajaData lider = lineaMixta(5, "001", "85", 3);
        CajaData talla95 = lineaMixta(5, "001", "95", 31);
        CajaData talla105 = lineaMixta(5, "001", "105", 3);

        service.inferirPesos(List.of(conocida, lider, talla95, talla105));

        assertEquals(37.0, lider.getPesoNetoKg());    // 37 uds * 1.0
        assertEquals(38.6, lider.getPesoBrutoKg());   // 37.0 + tara 1.6 (una sola)
        assertNull(talla95.getPesoNetoKg());
        assertNull(talla95.getPesoBrutoKg());
        assertNull(talla105.getPesoNetoKg());
        assertNull(talla105.getPesoBrutoKg());
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

    @Test
    void enElEnvioLasCajasConMismoNumeroEnDistintasDestinacionesNoSeMezclan() {
        // El mismo modelo y el mismo nº de caja (5, color 001) en DOS
        // destinaciones son cajas físicas DISTINTAS (los nº de caja se
        // reinician por destinación). Cada una con su propio bruto debe
        // recibir su neto; no debe fusionarse una dentro de la otra.
        CajaData chinaCaja5 = caja("60x40x30", 50, 11.2);   // (11.2-1.2)/50
        chinaCaja5.setReferencia("REF");
        chinaCaja5.setCodigoColor("001");
        chinaCaja5.setNumeroCaja(5);
        CajaData japanCaja5 = caja("60x40x30", 20, 5.2);
        japanCaja5.setReferencia("REF");
        japanCaja5.setCodigoColor("001");
        japanCaja5.setNumeroCaja(5);

        service.inferirPesosDelEnvio(List.of(List.of(chinaCaja5), List.of(japanCaja5)));

        assertEquals(10.0, chinaCaja5.getPesoNetoKg());  // 11.2 - tara 1.2
        assertEquals(4.0, japanCaja5.getPesoNetoKg());   // 5.2 - tara 1.2 (no se pierde)
    }

    @Test
    void enElEnvioElPesoUnitarioSeComparteEntreDestinaciones() {
        // REF pesada solo en la destinación A; una caja de REF sin peso en la
        // destinación B (con el mismo nº de caja) debe inferirse con el
        // unitario de A: el peso por unidad es del producto, no del palet.
        CajaData a = caja("60x40x40", 10, 11.6);   // (11.6-1.6)/10 = 1.0
        a.setReferencia("REF");
        a.setNumeroCaja(1);
        CajaData b = caja("60x40x40", 5, null);
        b.setReferencia("REF");
        b.setNumeroCaja(1);

        service.inferirPesosDelEnvio(List.of(List.of(a), List.of(b)));

        assertEquals(5.0, b.getPesoNetoKg());   // 5 * 1.0
        assertEquals(6.6, b.getPesoBrutoKg());  // 5.0 + tara 1.6
    }

    /**
     * Cada caja del helper es una caja física distinta: le damos un nº de caja
     * único para que la inferencia (que agrupa por color+nº) no las mezcle.
     * El color queda a null, que basta al ser el número siempre distinto.
     */
    private static int siguienteNumeroCaja = 1;

    private static CajaData caja(String tamano, int cantidad, Double pesoBruto) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(siguienteNumeroCaja++);
        caja.setTamanoCaja(tamano);
        caja.setCantidad(cantidad);
        caja.setPesoBrutoKg(pesoBruto);
        return caja;
    }

    /** Una línea (talla) de una caja física mixta, identificada por su color+nº. */
    private static CajaData lineaMixta(int numeroCaja, String color, String talla, int cantidad) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(numeroCaja);
        caja.setCodigoColor(color);
        caja.setTalla(talla);
        caja.setTamanoCaja("60x40x40");
        caja.setCantidad(cantidad);
        return caja;
    }
}
