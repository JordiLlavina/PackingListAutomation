package com.puntotres.packinglist.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres;
import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres.Aviso;
import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres.Columna;

/**
 * Los avisos de la conversión como los lee la pantalla: los del fichero a
 * la vista, los de fila plegados por consecuencia, y lo completado solo
 * contado.
 */
class AgrupadorAvisosConversionTest {

    private static PackingFormatoPuntotres con(Aviso... avisos) {
        return new PackingFormatoPuntotres("H", List.of(), null, List.of(), List.of(), null,
                List.of(avisos));
    }

    @Test
    void losDeFilaSePlieganPorConsecuenciaConSuRecuento() {
        AgrupadorAvisosConversion.Vista vista = AgrupadorAvisosConversion.agrupar(con(
                Aviso.deFila(7, Columna.CODIGO_CLIENTE, "varios pedidos posibles", "Para F63023 en LAW..."),
                Aviso.deFila(8, Columna.CODIGO_CLIENTE, "varios pedidos posibles", "Para F63023 en LAW..."),
                Aviso.deFila(20, Columna.CODIGO_CLIENTE, "sin ningún pedido que case", "En el pedido no hay F67043..."),
                Aviso.general("La hoja declara 135 cajas y las filas suman 141")));

        assertEquals(List.of("La hoja declara 135 cajas y las filas suman 141"), vista.generales());
        assertEquals(2, vista.bloques().size());
        AgrupadorAvisosConversion.Bloque varios = vista.bloques().get(0);
        assertEquals("varios pedidos posibles", varios.consecuencia());
        assertEquals("2 filas", varios.recuento());
        assertEquals("Fila 7", varios.filas().get(0).rotulo());
        assertEquals("Fila 8", varios.filas().get(1).rotulo());
        assertEquals("1 fila", vista.bloques().get(1).recuento());
        assertEquals(0, vista.completados());
    }

    /**
     * «Referencia escrita como ULL163AL052 por el taller: en el pedido es
     * ULL163.AL0052» no es una notificación: ya va como comentario en el
     * excel, y en pantalla solo se cuenta.
     */
    @Test
    void loCompletadoNoSeEnsenaSoloSeCuenta() {
        AgrupadorAvisosConversion.Vista vista = AgrupadorAvisosConversion.agrupar(con(
                Aviso.informativo(1, Columna.REFERENCIA, "Referencia escrita como ULL163AL052 por el taller: "
                        + "en el pedido es ULL163.AL0052"),
                Aviso.informativo(2, Columna.COLOR, "El color 'noir' es el código LZZ")));

        assertTrue(vista.generales().isEmpty());
        assertTrue(vista.bloques().isEmpty());
        assertEquals(2, vista.completados());
    }

    /** Los del modelo no traen consecuencia: todos son dudas suyas. */
    @Test
    void losDelModeloVanJuntosComoDudasDeLectura() {
        AgrupadorAvisosConversion.Vista vista = AgrupadorAvisosConversion.agrupar(con(
                new Aviso(4, "", "La destinación tiene fondo amarillo", null, null),
                new Aviso(15, "", "Pone G7008 y se ha leído 67008", null, null)));

        assertEquals(1, vista.bloques().size());
        assertEquals(Aviso.DUDA_DE_LECTURA, vista.bloques().get(0).consecuencia());
        assertEquals("2 filas", vista.bloques().get(0).recuento());
    }
}
