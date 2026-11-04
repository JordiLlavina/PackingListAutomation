package com.puntotres.packinglist.service.embalaje;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.embalaje.RepartoCajas.Destinacion;
import com.puntotres.packinglist.service.embalaje.RepartoCajas.LineaReparto;
import com.puntotres.packinglist.service.embalaje.RepartoCajas.Resultado;

/**
 * El formato del reparto en cajas y sus mensajes. Los mismos casos tienen que
 * dar lo mismo en {@code comprobarReparto} de reparto-cajas.js.
 */
class RepartoCajasTest {

    private static final List<Destinacion> TABLA = List.of(
            new Destinacion("JAPAN", 40),
            new Destinacion("RETAIL", 127),
            new Destinacion("DOUANES USA", 243));

    @Test
    void lasLineasBuenasSeAgrupanPorDestinacionEnSuOrden() {
        Resultado r = RepartoCajas.leer("""
                12 CAJAS X 10 BOLSOS: 120 - RETAIL
                4 CAJAS X 10 BOLSOS: 40 - JAPAN
                1 CAJA X 7 BOLSOS: 7 - RETAIL
                24 CAJAS X 10 BOLSOS: 240 - DOUANES USA
                1 CAJA X 3 BOLSOS: 3 - DOUANES USA""", TABLA);
        assertEquals(List.of(), r.errores());
        assertEquals(List.of(), r.avisos());
        assertEquals(List.of(new LineaReparto(4, 10, 40, "JAPAN")), r.lineasDe(0));
        assertEquals(List.of(new LineaReparto(12, 10, 120, "RETAIL"), new LineaReparto(1, 7, 7, "RETAIL")),
                r.lineasDe(1));
        assertEquals(2, r.lineasDe(2).size());
        assertEquals(42, r.totalCajas());
        assertTrue(r.hayLineas());
        assertTrue(r.sinColocar().isEmpty());
    }

    @Test
    void elTextoDeLaColumnaVaEnSingularOPlural() {
        assertEquals("12 CAJAS X 10 BOLSOS", new LineaReparto(12, 10, 120, "RETAIL").cajasTexto());
        assertEquals("1 CAJA X 7 BOLSOS", new LineaReparto(1, 7, 7, "RETAIL").cajasTexto());
        assertEquals("3 CAJAS X 1 BOLSO", new LineaReparto(3, 1, 3, "RETAIL").cajasTexto());
        assertEquals("25 CAJAS", RepartoCajas.cajas(25));
        assertEquals("1 CAJA", RepartoCajas.cajas(1));
    }

    @Test
    void lasLineasEnBlancoNoCuentanPeroSiNumeran() {
        Resultado r = RepartoCajas.leer("\n4 CAJAS X 10 BOLSOS: 40 - JAPAN\n   \nRevisar costuras\n", TABLA);
        assertEquals(1, r.lineasDe(0).size());
        assertEquals(List.of("Línea 4: «Revisar costuras» no tiene el formato N CAJAS X M BOLSOS: TOTAL - DESTINACIÓN"),
                r.errores());
    }

    @Test
    void unaLineaConOtroFormatoEsUnError() {
        Resultado r = RepartoCajas.leer("4 CAJAS X 10 BOLSOS: 40 - JAPAN\n12 cajas de 10\nRevisar costuras", TABLA);
        assertEquals(List.of(
                "Línea 2: «12 cajas de 10» no tiene el formato N CAJAS X M BOLSOS: TOTAL - DESTINACIÓN",
                "Línea 3: «Revisar costuras» no tiene el formato N CAJAS X M BOLSOS: TOTAL - DESTINACIÓN"),
                r.errores());
        assertEquals(List.of("12 cajas de 10", "Revisar costuras"), r.sinColocar());
    }

    @Test
    void sinDestinacionLaLineaNoTieneElFormato() {
        // Ni siquiera contra una fila de la tabla sin destinación (solo con pedido).
        Resultado r = RepartoCajas.leer("4 CAJAS X 10 BOLSOS: 40 -   ",
                List.of(new Destinacion("", 40)));
        assertEquals(List.of("Línea 1: «4 CAJAS X 10 BOLSOS: 40 -» no tiene el formato "
                + "N CAJAS X M BOLSOS: TOTAL - DESTINACIÓN"), r.errores());
    }

    @Test
    void unaDestinacionQueNoEstaEnLaTablaEsUnError() {
        Resultado r = RepartoCajas.leer("4 CAJAS X 10 BOLSOS: 40 - JAPAN\n1 CAJA X 5 BOLSOS: 5 - KOREA", TABLA);
        assertEquals(List.of("Línea 2: KOREA no está en la tabla de destinaciones"), r.errores());
        assertEquals(List.of("1 CAJA X 5 BOLSOS: 5 - KOREA"), r.sinColocar());
    }

    @Test
    void unasCuentasQueNoSalenSonUnError() {
        Resultado r = RepartoCajas.leer("4 CAJAS X 10 BOLSOS: 45 - JAPAN\n1 CAJA X 7 BOLSOS: 8 - RETAIL", TABLA);
        assertEquals(List.of("Línea 1: 4 cajas de 10 son 40, no 45", "Línea 2: 1 caja de 7 es 7, no 8"),
                r.errores());
        assertFalse(r.hayLineas(), "una línea con las cuentas mal no se coloca");
    }

    @Test
    void siLasCajasNoSumanLaCantidadSeAvisaSinBloquear() {
        Resultado r = RepartoCajas.leer("3 CAJAS X 10 BOLSOS: 30 - JAPAN\n12 CAJAS X 10 BOLSOS: 120 - RETAIL\n"
                + "1 CAJA X 7 BOLSOS: 7 - RETAIL", TABLA);
        assertFalse(r.tieneErrores());
        assertEquals(List.of("JAPAN: las cajas suman 30 y la cantidad es 40",
                "DOUANES USA: las cajas suman 0 y la cantidad es 243"), r.avisos());
    }

    @Test
    void laDestinacionSeReconoceSinMirarMayusculasNiEspacios() {
        Resultado r = RepartoCajas.leer("  24  cajas x 10 bolsos :240-  douanes   usa  \n1 caja X 3 bolso: 3 - Douanes Usa",
                List.of(new Destinacion(" Douanes  USA ", 243)));
        assertEquals(List.of(), r.errores());
        assertEquals(List.of(), r.avisos());
        assertEquals(2, r.lineasDe(0).size());
        assertEquals("1 CAJA X 3 BOLSOS", r.lineasDe(0).get(1).cajasTexto());
    }

    @Test
    void siDosFilasSeLlamanIgualLaLineaVaALaPrimera() {
        Resultado r = RepartoCajas.leer("4 CAJAS X 10 BOLSOS: 40 - JAPAN",
                List.of(new Destinacion("JAPAN", 40), new Destinacion("japan", 10)));
        assertEquals(1, r.lineasDe(0).size());
        assertTrue(r.lineasDe(1).isEmpty());
    }

    @Test
    void unTextoVacioNoTieneLineasNiErroresNiAvisos() {
        for (String vacio : new String[] {"", "  \n \n", null}) {
            Resultado r = RepartoCajas.leer(vacio, TABLA);
            assertFalse(r.hayLineas());
            assertTrue(r.errores().isEmpty());
            assertTrue(r.avisos().isEmpty(), "sin reparto no se cuadra nada");
            assertEquals(0, r.totalCajas());
        }
    }
}
