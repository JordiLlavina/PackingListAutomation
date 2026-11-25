package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.model.DatosHojaProduccion;
import com.puntotres.packinglist.model.DatosHojaProduccion.LineaProduccion;

/**
 * Las Hojas de Producción guardadas, a través de la base real (H2 en memoria
 * con Flyway). Talleres de prueba con prefijo propio ("HPT ") porque la base
 * es la misma para toda la suite: nada aquí asume que la tabla está vacía.
 */
@SpringBootTest
class ArchivoHojasProduccionTest {

    @Autowired
    private ArchivoHojasProduccion archivo;

    @Test
    void unTallerSinHojasEmpiezaEnUnoYUnTallerVacioTambien() {
        assertEquals(1, archivo.siguienteNumero("HPT Taller sin hojas"));
        assertEquals(1, archivo.siguienteNumero("  "));
        assertEquals(1, archivo.siguienteNumero(null));
    }

    @Test
    void cadaTallerLlevaSuPropioContadorYSeComparaSinMayusculas() {
        archivo.guardar(datos("HPT Sweet Bag", 325));
        archivo.guardar(datos("HPT Adrian", 12));
        archivo.guardar(datos("HPT Sweet Bag", 7));

        assertEquals(326, archivo.siguienteNumero("HPT Sweet Bag"));
        assertEquals(326, archivo.siguienteNumero(" hpt sweet bag "));
        assertEquals(13, archivo.siguienteNumero("HPT Adrian"));
        assertEquals(1, archivo.siguienteNumero("HPT C&C Leather"));

        Map<String, Integer> siguientes = archivo.siguientesPorTaller();
        assertEquals(326, siguientes.get("HPT SWEET BAG"));
        assertEquals(13, siguientes.get("HPT ADRIAN"));
        assertFalse(siguientes.containsKey("HPT C&C LEATHER"));
        assertTrue(archivo.talleres().contains("HPT Adrian"));
    }

    @Test
    void elSnapshotVuelveEnteroYElListadoTraeElModelo() {
        HojaProduccion guardada = archivo.guardar(datos("HPT Snapshot", 41));

        DatosHojaProduccion leidos = archivo.datosDe(guardada.getId()).orElseThrow();
        assertEquals("HPT Snapshot", leidos.taller());
        assertEquals(41, leidos.numero());
        assertEquals("F67104", leidos.modelo());
        assertEquals("F67104", leidos.codigoModelo());
        assertEquals(LocalDate.of(2026, 11, 24), leidos.fechaSalida());
        assertEquals(1, leidos.lineas().size());
        LineaProduccion linea = leidos.lineas().get(0);
        assertEquals(3, linea.unidades());
        assertEquals("Gütermann", linea.marcaHilo());
        assertEquals("181", linea.numeroHilo());
        assertEquals("3", linea.cmHilo());
        assertEquals("APC ORO", linea.marcadoExterior());
        assertEquals("", linea.tinta(), "un texto nulo se guarda vacío");

        ResumenHojaProduccion resumen = archivo.todas().stream()
                .filter(r -> r.id().equals(guardada.getId())).findFirst().orElseThrow();
        assertEquals("APC", resumen.cliente());
        assertEquals("SMS FALL 27", resumen.temporada());
        assertEquals("F67104", resumen.modelo());
        assertEquals("LE NEIGE", resumen.nombreBolso());
        assertEquals(41, resumen.numero());
    }

    @Test
    void laFotoSubidaSeGuardaConLaHojaYSinElla() {
        byte[] foto = {1, 2, 3};
        HojaProduccion conFoto = archivo.guardar(datos("HPT Foto", 1), foto);
        HojaProduccion sinFoto = archivo.guardar(datos("HPT Foto", 2), null);

        assertArrayEquals(foto, archivo.fotoDe(conFoto.getId()).orElseThrow());
        assertTrue(archivo.fotoDe(sinFoto.getId()).isEmpty(), "sin foto propia usa la del catálogo");
        assertTrue(archivo.fotoDe(null).isEmpty());
    }

    private static DatosHojaProduccion datos(String taller, int numero) {
        LineaProduccion linea = new LineaProduccion(3, "NOMBRE PIEL 1", "COLOR PIEL 1", "NOMBRE PIEL COMB.",
                "COLOR PIEL COMB", "Gütermann", "181", "3", "MADE IN MOROCCO", "APC", "ORO", "-", "-",
                "SARJA 005", null);
        return new DatosHojaProduccion("APC", "A.P.C.", "SMS FALL 27", "F67104", "F67104", "LE NEIGE", taller,
                numero, LocalDate.of(2026, 11, 24), List.of(linea));
    }
}
