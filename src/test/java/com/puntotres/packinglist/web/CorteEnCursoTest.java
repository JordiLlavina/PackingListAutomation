package com.puntotres.packinglist.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.puntotres.packinglist.service.corte.FotoModelo;
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
        enCurso.cargar("AMI", "AMI", "H26", primero, pedido(), FotosTemporada.vacia(), primera,
                true, List.of());

        Path segundo = Files.createDirectories(dir.resolve("segundo"));
        enCurso.cargar("AMI", "AMI", "H26", segundo, pedido(), FotosTemporada.vacia(),
                ConversionesDePrueba.sinTerminar(1), true, List.of());

        assertTrue(primera.terminada(), "la primera conversión se cancela");
        assertTrue(Files.notExists(primero));
        assertTrue(Files.exists(segundo));
    }

    @Test
    void cadaFilaArrancaConLaFotoPrincipalDeSuModelo() {
        // Las fotos llegan ordenadas por nombre: sin la regla, la orden de
        // corte llevaría «0001.jpg» y no la que el usuario ha llamado main.
        CorteEnCurso enCurso = new CorteEnCurso();
        FotosTemporada fotos = new FotosTemporada(Map.of("ULL027", List.of(
                foto("0001.jpg"), foto("detalle.jpg"), foto("main.heic"))), List.of());
        enCurso.cargar("AMI", "AMI", "H26", dir, pedido(), fotos, ConversionesDePrueba.sinTerminar(0),
                false, List.of());

        assertEquals(2, enCurso.getFilas().get(0).getFotoPrincipal());
    }

    private static FotoModelo foto(String nombre) {
        return new FotoModelo("ULL027", nombre, Path.of(nombre));
    }

    private static PedidoCorte pedido() {
        return new PedidoCorte(List.of(new ArticuloCorte(ReferenciaCorte.deAmi("ULL027.AL0103"),
                List.of(new ColorCorte("001", "BLACK", 1)))), List.of());
    }
}
