package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class LectorZipFotosTest {

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
    void losModelosSinFotosSeListan() throws IOException {
        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, Map.of("ULL027/a.jpg", FOTO)));

        assertEquals(List.of("F67008", "ULL745"),
                fotos.modelosSinFotos(List.of("ULL027", "ull745", "F67008")));
    }

    @Test
    void losTopesParanUnZipDesproporcionado() throws IOException {
        // Cada zip se comprueba antes de crear el siguiente: los dos se escriben en fotos.zip.
        Path grande = zip(StandardCharsets.UTF_8, Map.of("ULL027/a.jpg", new byte[2048]));
        assertThrows(IllegalArgumentException.class,
                () -> new LectorZipFotos(1024, 100).leer(grande, MODELOS, dir.resolve("t1")));

        Path muchos = zip(StandardCharsets.UTF_8, Map.of("ULL027/a.jpg", FOTO, "ULL027/b.jpg", FOTO,
                "ULL027/c.jpg", FOTO));
        assertThrows(IllegalArgumentException.class,
                () -> new LectorZipFotos(1024 * 1024, 2).leer(muchos, MODELOS, dir.resolve("t2")));
    }

    @Test
    void loQueNoEsUnZipEsUnaIOException() throws IOException {
        Path falso = dir.resolve("falso.zip");
        Files.writeString(falso, "no soy un zip");

        assertThrows(IOException.class,
                () -> new LectorZipFotos().leer(falso, MODELOS, dir.resolve("t")));
    }

    private FotosTemporada leer(Path zip) throws IOException {
        return leer(zip, dir.resolve("trabajo"));
    }

    private static FotosTemporada leer(Path zip, Path trabajo) throws IOException {
        return new LectorZipFotos().leer(zip, MODELOS, trabajo);
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
