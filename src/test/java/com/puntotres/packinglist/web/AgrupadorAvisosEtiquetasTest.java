package com.puntotres.packinglist.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.etiquetas.AvisoEtiqueta;

/**
 * Cómo se presentan los avisos de etiquetas en la pantalla de resultados.
 *
 * El generador emite un aviso por caja, que es la verdad del envío: si la
 * caja 2 va sin EAN, eso es un hecho de la caja 2. Pero leído en pantalla,
 * el mismo aviso repetido nueve veces es ruido que empuja hacia abajo los
 * que sí hay que atender. Juntarlos es decisión de pantalla y por eso vive
 * aquí y no en el generador, igual que AgrupadorFilasRevision compacta filas
 * sin tocar el modelo.
 */
class AgrupadorAvisosEtiquetasTest {

    private static AvisoEtiqueta caja(String destino, int numero, String hecho, String cons) {
        return AvisoEtiqueta.deCaja(destino, numero, hecho, cons);
    }

    @Test
    void lasCajasSeguidasConElMismoAvisoSeCompactanEnUnRango() {
        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados = AgrupadorAvisosEtiquetas.agrupar(
                List.of(caja("CHINA", 1, "Sin número de pedido en la entrada", "Etiqueta sin order number"),
                        caja("CHINA", 2, "Sin número de pedido en la entrada", "Etiqueta sin order number"),
                        caja("CHINA", 3, "Sin número de pedido en la entrada", "Etiqueta sin order number")));

        List<AgrupadorAvisosEtiquetas.LineaAviso> detalle = agrupados.bloques().get(0).detalle();
        assertEquals(1, detalle.size(), "tres avisos idénticos son un solo hecho");
        assertEquals("Cajas 1-3", detalle.get(0).rotulo());
    }

    @Test
    void unasCajasSueltasNoSeFundenEnUnRangoQueNoEsCierto() {
        // Un rango "1-5" diciendo que la 3 y la 4 también fallan sería mentira.
        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados = AgrupadorAvisosEtiquetas.agrupar(
                List.of(caja("CHINA", 1, "hecho", "cons"),
                        caja("CHINA", 2, "hecho", "cons"),
                        caja("CHINA", 5, "hecho", "cons")));

        assertEquals("Cajas 1-2, 5", agrupados.bloques().get(0).detalle().get(0).rotulo());
    }

    @Test
    void unaCajaSolaSeRotulaEnSingular() {
        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados = AgrupadorAvisosEtiquetas.agrupar(
                List.of(caja("CHINA", 7, "hecho", "cons")));

        assertEquals("Caja 7", agrupados.bloques().get(0).detalle().get(0).rotulo());
    }

    @Test
    void elResumenCuentaCajasPorConsecuenciaAunqueElHechoSeaDistinto() {
        // Lo que el usuario busca de un vistazo es QUÉ le pasa a la etiqueta;
        // de qué referencia venía es el detalle. Dos referencias que no están
        // en el pedido son, en resumen, dos cajas sin EAN.
        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados = AgrupadorAvisosEtiquetas.agrupar(
                List.of(caja("CHINA", 1, "no hay fila de ULL163", "etiqueta sin EAN13 ni EAN128"),
                        caja("CHINA", 2, "no hay fila de ULL999", "etiqueta sin EAN13 ni EAN128")));

        List<AgrupadorAvisosEtiquetas.LineaResumen> resumen = agrupados.bloques().get(0).resumen();
        assertEquals(1, resumen.size());
        assertEquals("2 cajas", resumen.get(0).recuento());
        assertEquals("etiqueta sin EAN13 ni EAN128", resumen.get(0).consecuencia());
        assertEquals(2, agrupados.bloques().get(0).detalle().size(), "el detalle sí los separa");
    }

    @Test
    void unaCajaConDosProblemasCuentaUnaVezEnCadaConsecuencia() {
        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados = AgrupadorAvisosEtiquetas.agrupar(
                List.of(caja("CHINA", 1, "sin pedido", "sin order number"),
                        caja("CHINA", 1, "no hay fila", "sin EAN13 ni EAN128")));

        List<AgrupadorAvisosEtiquetas.LineaResumen> resumen = agrupados.bloques().get(0).resumen();
        assertEquals(List.of("1 caja", "1 caja"),
                resumen.stream().map(AgrupadorAvisosEtiquetas.LineaResumen::recuento).toList());
    }

    @Test
    void losAvisosDePaletSeMarcanParaTenirlosAparte() {
        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados = AgrupadorAvisosEtiquetas.agrupar(
                List.of(caja("CHINA", 1, "sin pedido", "sin order number"),
                        AvisoEtiqueta.dePalet("CHINA", 1, "con cajas sin peso",
                                "Etiqueta de palet sin peso")));

        List<AgrupadorAvisosEtiquetas.LineaAviso> detalle = agrupados.bloques().get(0).detalle();
        assertFalse(detalle.get(0).palet(), "el de caja no");
        assertTrue(detalle.get(1).palet(), "el de palet sí");
        assertEquals("Palet 1", detalle.get(1).rotulo());
    }

    @Test
    void cadaDestinacionEsUnBloqueEnElOrdenEnQueAparece() {
        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados = AgrupadorAvisosEtiquetas.agrupar(
                List.of(caja("CHINA", 1, "h", "c"),
                        caja("JAPAN", 4, "h", "c"),
                        caja("CHINA", 2, "h", "c")));

        assertEquals(List.of("CHINA", "JAPAN"), agrupados.bloques().stream()
                .map(AgrupadorAvisosEtiquetas.BloqueDestino::destino).toList());
        assertEquals("Cajas 1-2", agrupados.bloques().get(0).detalle().get(0).rotulo(),
                "las dos de CHINA se reencuentran aunque llegaran separadas");
    }

    @Test
    void unAvisoDeLaDestinacionEnteraNoLlevaRotuloDeCaja() {
        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados = AgrupadorAvisosEtiquetas.agrupar(
                List.of(AvisoEtiqueta.deDestino("IVRY", "sin palets",
                        "La hoja de etiquetas de palet sale en blanco")));

        AgrupadorAvisosEtiquetas.LineaAviso linea = agrupados.bloques().get(0).detalle().get(0);
        assertEquals("", linea.rotulo());
        assertEquals("", agrupados.bloques().get(0).resumen().get(0).recuento(),
                "no hay nada que contar: es la destinación entera");
    }

    @Test
    void unAvisoDelFicheroNoEntraEnNingunBloqueDeDestinacion() {
        // No es de ninguna destinación: sale suelto y arriba, como hasta ahora.
        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados = AgrupadorAvisosEtiquetas.agrupar(
                List.of(AvisoEtiqueta.deFichero("El pedido no trae columna EAN128", null),
                        caja("CHINA", 1, "h", "c")));

        assertEquals(1, agrupados.generales().size());
        assertEquals("El pedido no trae columna EAN128", agrupados.generales().get(0).hecho());
        assertEquals(List.of("CHINA"), agrupados.bloques().stream()
                .map(AgrupadorAvisosEtiquetas.BloqueDestino::destino).toList());
    }

    @Test
    void unAvisoSinConsecuenciaSeResumePorSuHecho() {
        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados = AgrupadorAvisosEtiquetas.agrupar(
                List.of(caja("CHINA", 1, "Lleva varias tallas", null)));

        assertEquals("Lleva varias tallas",
                agrupados.bloques().get(0).resumen().get(0).consecuencia());
    }

    @Test
    void sinAvisosNoHayBloques() {
        AgrupadorAvisosEtiquetas.AvisosAgrupados agrupados =
                AgrupadorAvisosEtiquetas.agrupar(List.of());

        assertTrue(agrupados.bloques().isEmpty());
        assertTrue(agrupados.generales().isEmpty());
        assertTrue(agrupados.vacio());
    }
}
