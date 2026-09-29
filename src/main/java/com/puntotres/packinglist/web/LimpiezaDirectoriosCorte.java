package com.puntotres.packinglist.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, borra los directorios temporales de los documentos del corte
 * que dejó una ejecución anterior.
 *
 * CorteEnCurso los borra al acabar la sesión, pero matar la aplicación a la
 * fuerza (taskkill /F, que es como se libera el puerto en Windows) se salta
 * ese paso, y cada temporada son cientos de megas de fotos. Las sesiones no
 * sobreviven a un reinicio, así que lo que queda es basura; aun así solo se
 * borra lo que lleva horas sin tocarse, porque en la misma máquina puede
 * haber otra instancia en marcha (los tests, sin ir más lejos) con su
 * directorio en uso.
 */
@Component
public class LimpiezaDirectoriosCorte {

    /** Mucho más que una sesión (30 min sin actividad) y que cualquier conversión. */
    static final Duration ANTIGUEDAD_MINIMA = Duration.ofHours(6);

    private static final Logger LOG = LoggerFactory.getLogger(LimpiezaDirectoriosCorte.class);

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        int borrados = limpiar(Path.of(System.getProperty("java.io.tmpdir")), Instant.now());
        if (borrados > 0) {
            LOG.info("Borrados {} directorios temporales de documentos del corte de ejecuciones anteriores",
                    borrados);
        }
    }

    /** Devuelve cuántos directorios ha borrado. Nunca lanza: limpiar no debe impedir arrancar. */
    static int limpiar(Path temporales, Instant ahora) {
        int borrados = 0;
        try (DirectoryStream<Path> entradas = Files.newDirectoryStream(temporales,
                CorteEnCurso.PREFIJO_DIRECTORIO + "*")) {
            for (Path directorio : entradas) {
                if (Files.isDirectory(directorio) && esViejo(directorio, ahora)) {
                    CorteEnCurso.borrar(directorio);
                    if (Files.notExists(directorio)) {
                        borrados++;
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("No se han podido revisar los temporales de documentos del corte: {}", e.getMessage());
        }
        return borrados;
    }

    /**
     * Viejo es que NADA de dentro se ha tocado en horas. La fecha del
     * directorio de arriba no basta: escribir en sus subcarpetas no la cambia,
     * y un directorio en uso parecería abandonado.
     */
    private static boolean esViejo(Path directorio, Instant ahora) {
        try (Stream<Path> todo = Files.walk(directorio)) {
            Instant ultimo = todo.map(LimpiezaDirectoriosCorte::fecha)
                    .max(Instant::compareTo)
                    .orElse(Instant.MAX);
            return ultimo.plus(ANTIGUEDAD_MINIMA).isBefore(ahora);
        } catch (IOException | UncheckedIOException e) {
            return false;
        }
    }

    private static Instant fecha(Path ruta) {
        try {
            return Files.getLastModifiedTime(ruta).toInstant();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
