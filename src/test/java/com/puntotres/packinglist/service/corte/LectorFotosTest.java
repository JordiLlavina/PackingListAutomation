package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LectorFotosTest {

    private static final byte[] FOTO = {1, 2, 3, 4};
    private static final Set<String> MODELOS = Set.of("ULL027", "F67008");

    @TempDir
    Path dir;

    @Test
    void cadaFotoVaASuModeloAunqueHayaCarpetaRaizOSubcarpetas() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("H26/ULL027/b.jpg", FOTO);
        entradas.put("H26/ULL027/A.HEIC", FOTO);
        entradas.put("H26/ULL027/detalles/c.jpeg", FOTO);
        entradas.put("H26/f67008/y.jpg", FOTO);

        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, entradas));

        assertEquals(List.of("A.HEIC", "b.jpg", "c.jpeg"), nombres(fotos.de("ULL027")));
        assertEquals(List.of("y.jpg"), nombres(fotos.de("F67008")));
        assertEquals(4, fotos.total());
        assertTrue(fotos.avisos().isEmpty(), fotos.avisos().toString());
    }

    @Test
    void loQueNoCaeEnNingunModeloSeAvisaYNoSeUsa() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("H26/ULL999/x.jpg", FOTO);
        entradas.put("H26/ULL999/z.jpg", FOTO);
        entradas.put("suelta.jpg", FOTO);
        entradas.put("H26/ULL027/notas.txt", FOTO);

        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, entradas));

        assertEquals(0, fotos.total());
        String avisos = String.join("\n", fotos.avisos());
        assertTrue(avisos.contains("ULL999") && avisos.contains("sus 2 fotos"), avisos);
        assertTrue(avisos.contains("suelta.jpg"), avisos);
        assertTrue(avisos.contains("notas.txt"), avisos);
    }

    @Test
    void laBasuraDelSistemaSeIgnoraSinAvisar() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("__MACOSX/H26/ULL027/._b.jpg", FOTO);
        entradas.put("H26/ULL027/._b.jpg", FOTO);
        entradas.put("H26/ULL027/Thumbs.db", FOTO);
        entradas.put("H26/ULL027/.DS_Store", FOTO);
        entradas.put("H26/ULL027/b.jpg", FOTO);

        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, entradas));

        assertEquals(List.of("b.jpg"), nombres(fotos.de("ULL027")));
        assertTrue(fotos.avisos().isEmpty(), fotos.avisos().toString());
    }

    @Test
    void lasFotosSeCopianConNombreGeneradoYSuContenido() throws IOException {
        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, Map.of("ULL027/Foto 1.JPG", FOTO)));

        FotoModelo foto = fotos.de("ULL027").get(0);
        assertTrue(foto.original().getFileName().toString().matches("\\d{5}\\.jpg"));
        assertArrayEquals(FOTO, Files.readAllBytes(foto.original()));
    }

    @Test
    void unaEntradaConPuntosPuntosNoEscribeFueraDelDirectorioDeTrabajo() throws IOException {
        Path trabajo = dir.resolve("trabajo");
        leer(zip(StandardCharsets.UTF_8, Map.of("ULL027/../../../fuera.jpg", FOTO)), trabajo);

        try (Stream<Path> todo = Files.walk(dir)) {
            assertTrue(todo.filter(Files::isRegularFile)
                    .filter(fichero -> !fichero.getFileName().toString().equals("fotos.zip"))
                    .allMatch(fichero -> fichero.startsWith(trabajo)));
        }
        assertFalse(Files.exists(dir.getParent().resolve("fuera.jpg")));
    }

    @Test
    void unZipDelExploradorDeWindowsSinUtf8SeLeeIgual() throws IOException {
        FotosTemporada fotos = leer(zip(Charset.forName("IBM437"), Map.of("ULL027/foto ñ.jpg", FOTO)));

        assertEquals(List.of("foto ñ.jpg"), nombres(fotos.de("ULL027")));
    }

    @Test
    void unaCarpetaConLaReferenciaEnteraVaASuModelo() throws IOException {
        // El pedido real dice ULL027.AL0103 y la carpeta ULL027.AL103: manda el modelo.
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("H26/ULL027.AL103/a.jpg", FOTO);
        entradas.put("H26/ull027.al0216/b.jpg", FOTO);

        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, entradas));

        assertEquals(List.of("a.jpg", "b.jpg"), nombres(fotos.de("ULL027")));
        assertTrue(fotos.avisos().isEmpty(), fotos.avisos().toString());
    }

    @Test
    void enApcElModeloDeLaCarpetaVaDetrasDelGuion() throws IOException {
        Path zip = zip(StandardCharsets.UTF_8, Map.of("PXCBC-F67008/y.jpg", FOTO));

        FotosTemporada fotos = new LectorFotos().leer(zip, MODELOS, ReferenciaCorte::deApc,
                dir.resolve("trabajo"));

        assertEquals(List.of("y.jpg"), nombres(fotos.de("F67008")));
    }

    @Test
    void unaCarpetaConReferenciaDeOtroModeloSigueSinUsarse() throws IOException {
        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, Map.of("ULL999.AL0103/a.jpg", FOTO)));

        assertEquals(0, fotos.total());
        assertTrue(String.join("\n", fotos.avisos()).contains("ULL999.AL0103"),
                fotos.avisos().toString());
    }

    @Test
    void losPngSonFotos() throws IOException {
        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, Map.of("ULL027/a.PNG", FOTO)));

        assertEquals(List.of("a.PNG"), nombres(fotos.de("ULL027")));
        assertTrue(fotos.de("ULL027").get(0).original().getFileName().toString().endsWith(".png"));
    }

    @Test
    void unaCarpetaSubidaSinZipSeLeeIgualQueElZip() throws IOException {
        List<LectorFotos.Fichero> ficheros = List.of(
                fichero("H26/ULL027/b.jpg"),
                fichero("H26/ULL027.AL0103/detalles/a.heic"),
                fichero("H26/Thumbs.db"),
                fichero("H26/notas.txt"),
                fichero("suelta.jpg"));

        FotosTemporada fotos = new LectorFotos().leer(ficheros, MODELOS, ReferenciaCorte::deAmi,
                dir.resolve("trabajo"));

        assertEquals(List.of("a.heic", "b.jpg"), nombres(fotos.de("ULL027")));
        assertArrayEquals(FOTO, Files.readAllBytes(fotos.de("ULL027").get(0).original()));
        String avisos = String.join("\n", fotos.avisos());
        assertTrue(avisos.contains("notas.txt") && avisos.contains("suelta.jpg"), avisos);
        assertFalse(avisos.contains("Thumbs"), avisos);
    }

    @Test
    void unaCarpetaSubidaDesproporcionadaSePara() {
        List<LectorFotos.Fichero> ficheros = List.of(fichero("ULL027/a.jpg"), fichero("ULL027/b.jpg"),
                fichero("ULL027/c.jpg"));

        assertThrows(IllegalArgumentException.class, () -> new LectorFotos(1024 * 1024, 2)
                .leer(ficheros, MODELOS, ReferenciaCorte::deAmi, dir.resolve("t")));
        assertThrows(IllegalArgumentException.class, () -> new LectorFotos(5, 100)
                .leer(ficheros, MODELOS, ReferenciaCorte::deAmi, dir.resolve("t2")));
    }

    @Test
    void losTopesParanUnZipDesproporcionado() throws IOException {
        // Cada zip se comprueba antes de crear el siguiente: los dos se escriben en fotos.zip.
        Path grande = zip(StandardCharsets.UTF_8, Map.of("ULL027/a.jpg", new byte[2048]));
        assertThrows(IllegalArgumentException.class,
                () -> new LectorFotos(1024, 100).leer(grande, MODELOS, ReferenciaCorte::deAmi, dir.resolve("t1")));

        Path muchos = zip(StandardCharsets.UTF_8, Map.of("ULL027/a.jpg", FOTO, "ULL027/b.jpg", FOTO,
                "ULL027/c.jpg", FOTO));
        assertThrows(IllegalArgumentException.class,
                () -> new LectorFotos(1024 * 1024, 2).leer(muchos, MODELOS, ReferenciaCorte::deAmi, dir.resolve("t2")));
    }

    @Test
    void loQueNoEsUnZipEsUnaIOException() throws IOException {
        Path falso = dir.resolve("falso.zip");
        Files.writeString(falso, "no soy un zip");

        assertThrows(IOException.class,
                () -> new LectorFotos().leer(falso, MODELOS, ReferenciaCorte::deAmi, dir.resolve("t")));
    }

    private static LectorFotos.Fichero fichero(String ruta) {
        return new LectorFotos.Fichero(ruta, () -> new ByteArrayInputStream(FOTO));
    }

    private FotosTemporada leer(Path zip) throws IOException {
        return leer(zip, dir.resolve("trabajo"));
    }

    private static FotosTemporada leer(Path zip, Path trabajo) throws IOException {
        return new LectorFotos().leer(zip, MODELOS, ReferenciaCorte::deAmi, trabajo);
    }

    private Path zip(Charset charset, Map<String, byte[]> entradas) throws IOException {
        Path zip = dir.resolve("fotos.zip");
        try (ZipOutputStream salida = new ZipOutputStream(Files.newOutputStream(zip), charset)) {
            for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                salida.putNextEntry(new ZipEntry(entrada.getKey()));
                salida.write(entrada.getValue());
                salida.closeEntry();
            }
        }
        return zip;
    }

    private static List<String> nombres(List<FotoModelo> fotos) {
        return fotos.stream().map(FotoModelo::nombreOriginal).toList();
    }
}
