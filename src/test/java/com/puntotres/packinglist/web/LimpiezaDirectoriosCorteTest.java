package com.puntotres.packinglist.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LimpiezaDirectoriosCorteTest {

    @TempDir
    Path temporales;

    @Test
    void alArrancarSeBorranLosDirectoriosDelCorteQueDejoUnProcesoMatado() throws IOException {
        // Matar la aplicación a la fuerza (taskkill /F, que es como se libera
        // el puerto en Windows) se salta @PreDestroy: sin barrido, cada temporada
        // dejaba cientos de megas de fotos en temporales para siempre.
        Instant ahora = Instant.parse("2026-09-29T12:00:00Z");
        Path viejo = directorio(CorteEnCurso.PREFIJO_DIRECTORIO + "111", ahora.minus(Duration.ofHours(7)));
        Path reciente = directorio(CorteEnCurso.PREFIJO_DIRECTORIO + "222", ahora.minus(Duration.ofHours(7)));
        // En uso: el directorio de arriba es viejo, pero se sigue escribiendo
        // dentro, y eso no le cambia la fecha a él.
        Files.setLastModifiedTime(reciente.resolve("reducidas").resolve("00001.jpg"),
                FileTime.from(ahora.minus(Duration.ofMinutes(20))));
        Path ajeno = directorio("otra-cosa-333", ahora.minus(Duration.ofDays(3)));

        int borrados = LimpiezaDirectoriosCorte.limpiar(temporales, ahora);

        assertEquals(1, borrados);
        assertTrue(Files.notExists(viejo));
        assertTrue(Files.exists(reciente), "puede ser de otra instancia que sigue en marcha");
        assertTrue(Files.exists(ajeno), "lo que no es del corte no se toca");
    }

    /** Un directorio con una foto dentro, todo con la misma fecha. */
    private Path directorio(String nombre, Instant modificado) throws IOException {
        Path directorio = Files.createDirectories(temporales.resolve(nombre));
        Path reducidas = Files.createDirectories(directorio.resolve("reducidas"));
        Path foto = Files.writeString(reducidas.resolve("00001.jpg"), "x");
        for (Path ruta : new Path[] {foto, reducidas, directorio}) {
            Files.setLastModifiedTime(ruta, FileTime.from(modificado));
        }
        return directorio;
    }
}
