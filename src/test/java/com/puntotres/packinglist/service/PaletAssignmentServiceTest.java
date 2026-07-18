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

    @Test
    void destinacionSinPaletsGeneraAviso() {
        DestinoData japan = destino("Japan", 1);
        List<PaletData> palets = List.of(palet("Paris", 1, 1, 12));

        ResultadoAsignacion resultado = service.asignar(japan, palets);

        assertEquals(1, resultado.getCajasSinPalet().size());
        assertFalse(resultado.getAvisos().isEmpty());
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

    private static PaletData palet(String destino, int numero, int inicio, int fin) {
        PaletData palet = new PaletData();
        palet.setDestino(destino);
        palet.setNumeroPalet(numero);
        palet.setCajaInicio(inicio);
        palet.setCajaFin(fin);
        return palet;
    }
}
