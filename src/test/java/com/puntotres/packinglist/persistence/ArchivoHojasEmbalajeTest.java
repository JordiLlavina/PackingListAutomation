package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.model.DatosHojaEmbalaje;
import com.puntotres.packinglist.model.DatosHojaEmbalaje.DestinacionHoja;

/**
 * Las hojas de «Info de embalaje» guardadas, a través de la base real (H2 en
 * memoria con Flyway). Cliente de prueba propio ("EMB-TEST") porque la base es
 * la misma para toda la suite y otras clases de test de la feature también
 * guardan hojas: nada aquí asume que la tabla está vacía.
 */
@SpringBootTest
class ArchivoHojasEmbalajeTest {

    private static final String CLIENTE = "EMB-TEST";

    @Autowired
    private ArchivoHojasEmbalaje archivo;

    @Test
    void unTallerSinHojasEmpiezaEnUnoYUnTallerVacioTambien() {
        assertEquals(1, archivo.siguienteNumeroEnvio("EMBT Taller sin hojas"));
        assertEquals(1, archivo.siguienteNumeroEnvio("  "));
        assertEquals(1, archivo.siguienteNumeroEnvio(null));
    }

    @Test
    void elSiguienteNumeroEsElMaximoMasUnoAunqueSeGuardeUnoMenor() {
        archivo.guardar(conTaller(datos(500, "ULL027"), "EMBT Máximo"), null);
        archivo.guardar(conTaller(datos(7, "ULL027"), "EMBT Máximo"), null);

        assertEquals(501, archivo.siguienteNumeroEnvio("EMBT Máximo"));
    }

    @Test
    void cadaTallerLlevaSuPropioContador() {
        archivo.guardar(conTaller(datos(270, "ULL027"), "EMBT Tánger"), null);
        archivo.guardar(conTaller(datos(145, "ULL027"), "EMBT Fez"), null);

        assertEquals(271, archivo.siguienteNumeroEnvio("EMBT Tánger"));
        assertEquals(146, archivo.siguienteNumeroEnvio("EMBT Fez"));
        assertEquals(271, archivo.siguienteNumeroEnvio("embt tánger "), "sin mayúsculas ni espacios de más");
        assertEquals(1, archivo.siguienteNumeroEnvio("EMBT Ubrique"));

        Map<String, Integer> siguientes = archivo.siguientesPorTaller();
        assertEquals(271, siguientes.get("EMBT TÁNGER"));
        assertEquals(146, siguientes.get("EMBT FEZ"));
        assertFalse(siguientes.containsKey("EMBT UBRIQUE"));
    }

    @Test
    void elSnapshotVuelveEnteroYLaFotoNoViajaEnEl() {
        DatosHojaEmbalaje datos = datos(11, "ULL027");

        HojaEmbalaje guardada = archivo.guardar(datos, 99L);

        assertEquals(datos, archivo.datosDe(guardada.getId()).orElseThrow());
        HojaEmbalaje leida = archivo.hoja(guardada.getId()).orElseThrow();
        assertEquals("ULL027", leida.getCodigoModelo());
        assertEquals(99L, leida.getTemporadaId());
        assertFalse(leida.getDatos().toLowerCase().contains("foto"), "la foto no forma parte del snapshot");
    }

    @Test
    void laUltimaDelModeloEsLaMasRecienteDelMismoClienteYModelo() {
        archivo.guardar(conBolsa(datos(21, "ULL027"), "A"), null);
        archivo.guardar(conBolsa(datos(22, "ULL027"), "B"), null);
        archivo.guardar(conBolsa(datos(23, "ULL999"), "C"), null);

        assertEquals("B", archivo.ultimaDe(CLIENTE, "ULL027").orElseThrow().bolsaTela());
        assertEquals("C", archivo.ultimaDe(CLIENTE, "ULL999").orElseThrow().bolsaTela());
        assertTrue(archivo.ultimaDe(CLIENTE, "NO-EXISTE").isEmpty());
    }

    @Test
    void losTalleresSonLosDistintosSinVacios() {
        archivo.guardar(conTaller(datos(31, "ULL027"), "Taller Z-prueba"), null);
        archivo.guardar(conTaller(datos(32, "ULL027"), "Taller Z-prueba"), null);
        archivo.guardar(conTaller(datos(33, "ULL027"), "Taller A-prueba"), null);
        archivo.guardar(conTaller(datos(34, "ULL027"), ""), null);

        List<String> talleres = archivo.talleres();

        assertEquals(1, talleres.stream().filter("Taller Z-prueba"::equals).count());
        assertEquals(1, talleres.stream().filter("Taller A-prueba"::equals).count());
        assertFalse(talleres.contains(""));
        assertTrue(talleres.indexOf("Taller A-prueba") < talleres.indexOf("Taller Z-prueba"), "van ordenados");
    }

    private static DatosHojaEmbalaje datos(int numeroEnvio, String codigoModelo) {
        return new DatosHojaEmbalaje(CLIENTE, "Cliente de prueba", "SS27", "Taller Fez", numeroEnvio, "NOTA-1",
                LocalDate.of(2026, 11, 2), codigoModelo + ".AL0103", "001", "NEGRO", "CANDY", codigoModelo,
                "60x40x40", 12, "Bolsa blanca", "PARIS", "GRABADO LOGO NIKEL",
                List.of(new DestinacionHoja("CHINA", "07704 CH", 60), new DestinacionHoja("JAPAN", "07705 JP", 24)),
                "Caja 1-5: 12 uds", "Cajas pesadas", "https://ejemplo.com/video");
    }

    private static DatosHojaEmbalaje conBolsa(DatosHojaEmbalaje d, String bolsa) {
        return new DatosHojaEmbalaje(d.cliente(), d.nombreCliente(), d.temporada(), d.taller(), d.numeroEnvio(),
                d.nota(), d.fecha(), d.referencia(), d.codigoColor(), d.descripcionColor(), d.nombreBolso(),
                d.codigoModelo(), d.medidaCaja(), d.unidadesPorCaja(), bolsa, d.etiquetaInterior(),
                d.instruccionEspecial(), d.destinaciones(), d.repartoCajas(), d.comentarios(), d.enlaceVideo());
    }

    private static DatosHojaEmbalaje conTaller(DatosHojaEmbalaje d, String taller) {
        return new DatosHojaEmbalaje(d.cliente(), d.nombreCliente(), d.temporada(), taller, d.numeroEnvio(),
                d.nota(), d.fecha(), d.referencia(), d.codigoColor(), d.descripcionColor(), d.nombreBolso(),
                d.codigoModelo(), d.medidaCaja(), d.unidadesPorCaja(), d.bolsaTela(), d.etiquetaInterior(),
                d.instruccionEspecial(), d.destinaciones(), d.repartoCajas(), d.comentarios(), d.enlaceVideo());
    }
}
