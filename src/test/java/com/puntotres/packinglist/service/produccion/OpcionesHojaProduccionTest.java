package com.puntotres.packinglist.service.produccion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class OpcionesHojaProduccionTest {

    @Test
    void losTamanosDelMarcadoDependenDelCliente() {
        assertEquals(List.of("4CM", "4.5CM", "5CM", "-"), OpcionesHojaProduccion.tamanosMarcado("AMI"));
        assertEquals(List.of("2.5CM", "3CM", "3.5CM", "4CM", "-"), OpcionesHojaProduccion.tamanosMarcado("apc "));
        List<String> genericos = OpcionesHojaProduccion.tamanosMarcado("PALOMA WOOL");
        assertTrue(genericos.containsAll(List.of("2.5CM", "5CM", "-")));
    }

    @Test
    void apcArrancaSinTamanoYAmiEnCuatro() {
        assertEquals("", OpcionesHojaProduccion.tamanoPorDefecto("APC"));
        assertEquals("", OpcionesHojaProduccion.tamanoPorDefecto("KAMI"));
        assertEquals("4CM", OpcionesHojaProduccion.tamanoPorDefecto("AMI"));
    }

    @Test
    void elMarcadoExteriorUneLasPartesYSaltaLosGuiones() {
        assertEquals("AMI ORO 4CM", OpcionesHojaProduccion.marcadoExterior("AMI", "ORO", "4CM"));
        assertEquals("APC ORO", OpcionesHojaProduccion.marcadoExterior("APC", "ORO", "-"));
        assertEquals("APC NIQUEL", OpcionesHojaProduccion.marcadoExterior(" APC ", "NIQUEL", ""));
        assertEquals("-", OpcionesHojaProduccion.marcadoExterior("-", "-", "-"));
        assertEquals("-", OpcionesHojaProduccion.marcadoExterior(null, "", null));
    }

    @Test
    void elTallerProponeElMarcadoInterior() {
        assertEquals("MADE IN SPAIN", OpcionesHojaProduccion.MARCADO_POR_TALLER.get("ADRIAN"));
        assertEquals("MADE IN SPAIN", OpcionesHojaProduccion.MARCADO_POR_TALLER.get("ANTONIO MUÑIZ"));
        assertEquals("MADE IN MOROCCO", OpcionesHojaProduccion.MARCADO_POR_TALLER.get("C&C LEATHER"));
        assertEquals("MADE IN MOROCCO", OpcionesHojaProduccion.MARCADO_POR_TALLER.get("SWEET BAG"));
    }
}
