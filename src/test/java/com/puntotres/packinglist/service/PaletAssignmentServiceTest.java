package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

import static com.puntotres.packinglist.testutil.TestDatos.palet;

class PaletAssignmentServiceTest {

    private final PaletAssignmentService service = new PaletAssignmentService();

    @Test
    void asignaPaletsPorRangoIgnorandoOtrasDestinaciones() {
        DestinoData paris = destino("Paris", 1, 12, 13, 24);
        List<PaletData> palets = List.of(
                palet("Paris", 1, 1, 12),
                palet("Paris", 2, 13, 24),
                palet("Japan", 1, 1, 8));

        ResultadoAsignacion resultado = service.asignar(paris, palets);

        assertTrue(resultado.todoAsignado());
        assertEquals(1, paris.getCajas().get(0).getNumeroPalet()); // caja 1
        assertEquals(1, paris.getCajas().get(1).getNumeroPalet()); // caja 12
        assertEquals(2, paris.getCajas().get(2).getNumeroPalet()); // caja 13
        assertEquals(2, paris.getCajas().get(3).getNumeroPalet()); // caja 24
    }

    @Test
    void cajaFueraDeTodoRangoQuedaSinPaletYReportada() {
        DestinoData paris = destino("Paris", 5, 30);
        List<PaletData> palets = List.of(palet("Paris", 1, 1, 12));

        ResultadoAsignacion resultado = service.asignar(paris, palets);

        assertFalse(resultado.todoAsignado());
        assertEquals(1, resultado.getCajasSinPalet().size());
        assertEquals(30, resultado.getCajasSinPalet().get(0).getNumeroCaja());
        assertNull(paris.getCajas().get(1).getNumeroPalet());
        assertEquals(1, paris.getCajas().get(0).getNumeroPalet());
    }

    /**
     * Por encima de {@link PaletAssignmentService#MAX_CAJAS_SIN_PALET} cajas,
     * una destinación sin palets sí es un dato que falta.
     */
    @Test
    void destinacionSinPaletsGeneraAviso() {
        DestinoData japan = destino("Japan", 1, 2, 3, 4);
        List<PaletData> palets = List.of(palet("Paris", 1, 1, 12));

        ResultadoAsignacion resultado = service.asignar(japan, palets);

        assertEquals(4, resultado.getCajasSinPalet().size());
        assertFalse(resultado.getAvisos().isEmpty());
    }

    /**
     * De 1 a 3 cajas el envío va suelto: no hay palets que declarar, así que
     * las cajas se marcan con SIN_PALET y no sale ni aviso ni lista de cajas
     * sin palet. Avisar de lo que pasa en todos los envíos pequeños solo
     * entierra los avisos que sí hay que leer.
     */
    @Test
    void hastaTresCajasSinPaletsSeMandanSueltasYNoAvisan() {
        DestinoData usa = destino("D. USA", 1, 2, 3);

        ResultadoAsignacion resultado = service.asignar(usa, List.of());

        assertTrue(resultado.todoAsignado());
        assertTrue(resultado.getAvisos().isEmpty());
        assertTrue(usa.getCajas().stream()
                .allMatch(caja -> Integer.valueOf(CajaData.SIN_PALET).equals(caja.getNumeroPalet())));
    }

    /**
     * Se cuentan cajas FÍSICAS, no líneas: una sola caja con cuatro artículos
     * dentro sigue yendo suelta.
     */
    @Test
    void unaSolaCajaConVariasLineasSigueYendoSuelta() {
        DestinoData usa = destino("D. USA", 1, 1, 1, 1);

        ResultadoAsignacion resultado = service.asignar(usa, List.of());

        assertTrue(resultado.getAvisos().isEmpty());
        assertEquals(CajaData.SIN_PALET, usa.getCajas().get(0).getNumeroPalet());
    }

    @Test
    void rangosSolapadosAsignanElPrimeroYAvisan() {
        DestinoData paris = destino("Paris", 11);
        List<PaletData> palets = List.of(
                palet("Paris", 1, 1, 12),
                palet("Paris", 2, 10, 20));

        ResultadoAsignacion resultado = service.asignar(paris, palets);

        assertEquals(1, paris.getCajas().get(0).getNumeroPalet());
        assertFalse(resultado.getAvisos().isEmpty());
    }

    private static DestinoData destino(String nombre, int... numerosCaja) {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino(nombre);
        destino.setCajas(new java.util.ArrayList<>());
        for (int numero : numerosCaja) {
            CajaData caja = new CajaData();
            caja.setNumeroCaja(numero);
            destino.getCajas().add(caja);
        }
        return destino;
    }
}
