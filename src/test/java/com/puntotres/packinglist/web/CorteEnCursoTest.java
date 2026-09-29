package com.puntotres.packinglist.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.service.corte.ArticuloCorte;
import com.puntotres.packinglist.service.corte.ColorCorte;
import com.puntotres.packinglist.service.corte.ConversionFotos;
import com.puntotres.packinglist.service.corte.ConversionesDePrueba;
import com.puntotres.packinglist.service.corte.FotosTemporada;
import com.puntotres.packinglist.service.corte.PedidoCorte;
import com.puntotres.packinglist.service.corte.ReferenciaCorte;

class CorteEnCursoTest {

    @TempDir
    Path dir;

    @Test
    void unaCargaNuevaCancelaLaConversionAnteriorYBorraSuDirectorio() throws IOException {
        // Dos cargas que se pisan en la misma sesión (dos pestañas): sin esto
        // la primera conversión seguía trabajando y su directorio, con cientos
        // de megas de fotos, se quedaba sin dueño hasta el próximo arranque.
        CorteEnCurso enCurso = new CorteEnCurso();
        Path primero = Files.createDirectories(dir.resolve("primero"));
        Files.writeString(primero.resolve("foto.jpg"), "x");
        ConversionFotos primera = ConversionesDePrueba.sinTerminar(1);
        enCurso.cargar("AMI", "AMI", "H26", primero, pedido(), sinFotos(), primera, List.of());

        Path segundo = Files.createDirectories(dir.resolve("segundo"));
        enCurso.cargar("AMI", "AMI", "H26", segundo, pedido(), sinFotos(),
                ConversionesDePrueba.sinTerminar(1), List.of());

        assertTrue(primera.terminada(), "la primera conversión se cancela");
        assertTrue(Files.notExists(primero));
        assertTrue(Files.exists(segundo));
    }

    private static PedidoCorte pedido() {
        return new PedidoCorte(List.of(new ArticuloCorte(ReferenciaCorte.deAmi("ULL027.AL0103"),
                List.of(new ColorCorte("001 BLACK", 1)))), List.of());
    }

    private static FotosTemporada sinFotos() {
        return new FotosTemporada(Map.of(), List.of());
    }
}
