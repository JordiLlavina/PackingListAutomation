package com.puntotres.packinglist.service.corte;

import static com.puntotres.packinglist.testutil.WordDePrueba.fotosDe;
import static com.puntotres.packinglist.testutil.WordDePrueba.texto;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class DocumentosCorteGeneracionTest {

    @TempDir
    Path dir;

    private final DocumentosCorteService servicio = new DocumentosCorteService(List.of());
    private FotoModelo fotoA;
    private FotoModelo fotoB;
    private FotosTemporada fotos;
    private final Map<Path, Path> reducidas = new HashMap<>();

    @BeforeEach
    void fotosDeUll027() throws IOException {
        fotoA = new FotoModelo("ULL027", "a.jpg", dir.resolve("00001.jpg"));
        fotoB = new FotoModelo("ULL027", "b.jpg", dir.resolve("00002.jpg"));
        fotos = new FotosTemporada(Map.of("ULL027", List.of(fotoA, fotoB)), List.of());
        reducidas.put(fotoA.original(), escribir("a-reducida.jpg", Color.RED));
        reducidas.put(fotoB.original(), escribir("b-reducida.jpg", Color.BLUE));
    }

    @Test
    void unaOrdenPorColorConBolsosYUnWordDeFotosPorModeloConFotos() throws IOException {
        ResultadoCorte resultado = generar("H26", List.of(
                fila("ULL027.AL0103", 10, 0),
                fila("ULL712.AL0103", 5),
                fila("ULL999.AL0001", 0)));

        assertEquals(List.of("Ordenes de corte AMI H26.docx", "Fotos ULL027.docx"),
                resultado.documentos().stream().map(DocumentoCorte::nombreFichero).toList());
        XWPFDocument ordenes = abrir(resultado.documentos().get(0));
        assertEquals(4, ordenes.getTables().size(), "ULL027 en negro y ULL712: dos órdenes");
        String texto = texto(ordenes);
        assertTrue(texto.contains("ULL027") && texto.contains("ULL712"));
        assertTrue(!texto.contains("ULL999"), "sin bolsos no hay orden");
        XWPFDocument album = abrir(resultado.documentos().get(1));
        assertEquals(2, fotosDe(album.getTables().get(0)).size());
        assertTrue(resultado.avisos().isEmpty(), resultado.avisos().toString());
    }

    @Test
    void laFotoPrincipalEsLaElegida() throws IOException {
        FilaCorte fila = fila("ULL027.AL0103", 10);
        fila.setFotoPrincipal(1);

        ResultadoCorte resultado = generar("H26", List.of(fila));

        assertArrayEquals(Files.readAllBytes(reducidas.get(fotoB.original())),
                fotoPrincipal(resultado));
    }

    @Test
    void siLaElegidaNoSeHaPodidoConvertirSaleLaSiguienteLegible() throws IOException {
        reducidas.remove(fotoB.original());
        FilaCorte fila = fila("ULL027.AL0103", 10);
        fila.setFotoPrincipal(1);

        ResultadoCorte resultado = generar("H26", List.of(fila));

        assertArrayEquals(Files.readAllBytes(reducidas.get(fotoA.original())),
                fotoPrincipal(resultado));
    }

    @Test
    void laFotoEsDelModeloYSaleUnSoloWordDeFotosParaTodasSusPieles() throws IOException {
        ResultadoCorte resultado = generar("H26", List.of(
                fila("ULL027.AL0103", 1), fila("ULL027.AL0216", 1)));

        assertEquals(List.of("Ordenes de corte AMI H26.docx", "Fotos ULL027.docx"),
                resultado.documentos().stream().map(DocumentoCorte::nombreFichero).toList());
    }

    @Test
    void unaFilaDesmarcadaNoSacaOrdenesNiWordDeFotos() throws IOException {
        FilaCorte fuera = fila("ULL027.AL0103", 10);
        fuera.setIncluida(false);

        ResultadoCorte resultado = generar("H26", List.of(fuera, fila("ULL712.AL0103", 5)));

        assertEquals(List.of("Ordenes de corte AMI H26.docx"),
                resultado.documentos().stream().map(DocumentoCorte::nombreFichero).toList());
        String texto = texto(abrir(resultado.documentos().get(0)));
        assertTrue(texto.contains("ULL712") && !texto.contains("ULL027"), texto);
    }

    @Test
    void elWordDeFotosSeTitulaConElModeloYElNombreDelBolsoSiElPedidoLoTrae() throws IOException {
        FilaCorte conNombre = new FilaCorte(new ArticuloCorte(ReferenciaCorte.deAmi("ULL027.AL0103"),
                List.of(new ColorCorte("BLACK", 1)), "Sac Le Neige"));

        ResultadoCorte resultado = generar("H26", List.of(conNombre));

        XWPFDocument album = abrir(resultado.documentos().get(1));
        assertEquals("ULL027 - Sac Le Neige", album.getParagraphs().get(0).getText());
    }

    @Test
    void sinBolsosEnNingunaFilaNoSaleNadaYSeAvisa() throws IOException {
        ResultadoCorte resultado = generar("H26", List.of(fila("ULL027.AL0103", 0)));

        assertTrue(resultado.documentos().isEmpty());
        assertEquals(1, resultado.avisos().size());
    }

    @Test
    void elNombreDelFicheroSeSanea() throws IOException {
        ResultadoCorte resultado = generar("H26/27", List.of(fila("ULL712.AL0103", 1)));

        assertEquals("Ordenes de corte AMI H26_27.docx", resultado.documentos().get(0).nombreFichero());
        assertTrue(Files.exists(resultado.documentos().get(0).fichero()));
    }

    private ResultadoCorte generar(String temporada, List<FilaCorte> filas) throws IOException {
        return servicio.generar("AMI", temporada, filas, fotos,
                foto -> Optional.ofNullable(reducidas.get(foto.original())), dir.resolve("salida"));
    }

    private static FilaCorte fila(String referencia, int... bolsos) {
        String[] colores = {"001 BLACK", "718 VANILLA CREAM", "A237 MOCHA"};
        List<ColorCorte> lista = new java.util.ArrayList<>();
        for (int i = 0; i < bolsos.length; i++) {
            lista.add(new ColorCorte(colores[i], bolsos[i]));
        }
        FilaCorte fila = new FilaCorte(new ArticuloCorte(ReferenciaCorte.deAmi(referencia), lista));
        fila.setNombrePiel("Box calf");
        return fila;
    }

    private Path escribir(String nombre, Color color) throws IOException {
        Path fichero = dir.resolve(nombre);
        Files.write(fichero, FotosDePrueba.jpeg(160, 120, color));
        return fichero;
    }

    private static XWPFDocument abrir(DocumentoCorte documento) throws IOException {
        try (InputStream entrada = Files.newInputStream(documento.fichero())) {
            return new XWPFDocument(entrada);
        }
    }

    private static byte[] fotoPrincipal(ResultadoCorte resultado) throws IOException {
        return fotosDe(abrir(resultado.documentos().get(0)).getTables().get(0))
                .get(0).getPictureData().getData();
    }
}
