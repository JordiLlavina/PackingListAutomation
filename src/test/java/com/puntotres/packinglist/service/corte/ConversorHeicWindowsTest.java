package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.service.corte.ConversorHeicWindows.Trabajo;
import com.puntotres.packinglist.testutil.FotosDePrueba;

/**
 * El camino rápido de Windows. Solo corre en Windows, y el primer test se
 * salta si el sistema no trae el códec HEIF/HEVC: sin él, todas las fotos
 * pasan al camino Java, que ya tiene sus tests.
 */
@EnabledOnOs(OS.WINDOWS)
class ConversorHeicWindowsTest {

    @Test
    void convierteElHeicAJpeg(@TempDir Path dir) throws Exception {
        Path heic = dir.resolve("a.heic");
        Files.write(heic, FotosDePrueba.heicDePrueba());
        Path salida = dir.resolve("a.jpg");
        List<Trabajo> convertidos = new ArrayList<>();

        List<Trabajo> pendientes = new ConversorHeicWindows().convertir(
                List.of(new Trabajo(heic, salida)), dir, proceso -> { }, convertidos::add);

        assumeTrue(pendientes.isEmpty(), "este Windows no trae el códec HEIF/HEVC");
        assertEquals(1, convertidos.size());
        assertEquals(430, ImageIO.read(salida.toFile()).getWidth());
    }

    @Test
    void unPowerShellColgadoSeMataYSusFotosVuelvenComoPendientes(@TempDir Path dir) {
        // Un códec de HEVC que se cuelga con un fichero raro dejaba el lote
        // esperando para siempre: la salida solo acaba cuando PowerShell sale.
        Path heic = dir.resolve("a.heic");
        ConversorHeicWindows colgado = new ConversorHeicWindows("/corte/cuelga.ps1",
                Duration.ofSeconds(3), Duration.ZERO);

        List<Trabajo> pendientes = assertTimeoutPreemptively(Duration.ofSeconds(60),
                () -> colgado.convertir(List.of(new Trabajo(heic, dir.resolve("a.jpg"))), dir,
                        proceso -> { }, trabajo -> { }));

        assertEquals(1, pendientes.size());
    }

    @Test
    void loQueWindowsNoLeeVuelveComoPendienteSinDejarFichero(@TempDir Path dir) throws Exception {
        Path roto = dir.resolve("roto.heic");
        Files.writeString(roto, "no soy una foto");
        Path salida = dir.resolve("roto.jpg");

        List<Trabajo> pendientes = new ConversorHeicWindows().convertir(
                List.of(new Trabajo(roto, salida)), dir, proceso -> { }, trabajo -> { });

        assertEquals(1, pendientes.size());
        assertFalse(Files.exists(salida));
    }
}
