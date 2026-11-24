package com.puntotres.packinglist.service.cargamodelos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.service.cargamodelos.CarpetaModelos.FicheroModelo;
import com.puntotres.packinglist.service.cargamodelos.CarpetaModelos.Lectura;
import com.puntotres.packinglist.service.corte.LectorFotos;

class CarpetaModelosTest {

    @TempDir
    Path destino;

    private static LectorFotos.Fichero fichero(String ruta) {
        return new LectorFotos.Fichero(ruta, () -> new ByteArrayInputStream(ruta.getBytes()));
    }

    private Path zip(String... rutas) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (String ruta : rutas) {
                zip.putNextEntry(new ZipEntry(ruta));
                zip.write(ruta.getBytes());
                zip.closeEntry();
            }
        }
        Path fichero = destino.resolve("subido.zip");
        Files.write(fichero, salida.toByteArray());
        return fichero;
    }

    @Test
    void laRaizEsLaTemporadaYCadaSubcarpetaUnModeloConSusFotosYSusPdf() throws IOException {
        Lectura lectura = CarpetaModelos.leer(List.of(
                fichero("FALL27/CM100 bolso grande/b.jpg"),
                fichero("FALL27/CM100 bolso grande/a.HEIC"),
                fichero("FALL27/CM100 bolso grande/ficha tecnica.pdf"),
                fichero("FALL27/CM200/detalle/c.png")), destino);

        assertEquals("FALL27", lectura.raiz());
        assertEquals(List.of("CM100 bolso grande", "CM200"), new ArrayList<>(lectura.porCarpeta().keySet()));
        List<FicheroModelo> delPrimero = lectura.porCarpeta().get("CM100 bolso grande");
        assertEquals(List.of("a.HEIC", "b.jpg", "ficha tecnica.pdf"),
                delPrimero.stream().map(FicheroModelo::nombre).toList(), "por nombre");
        assertEquals(List.of(false, false, true), delPrimero.stream().map(FicheroModelo::pdf).toList());
        assertEquals(1, lectura.porCarpeta().get("CM200").size(), "una subcarpeta de dentro es del mismo modelo");
        assertTrue(lectura.avisos().isEmpty());
        for (FicheroModelo copiado : delPrimero) {
            assertTrue(Files.exists(copiado.copia()));
            assertTrue(copiado.copia().startsWith(destino), "nunca se escribe con el nombre que llega");
        }
    }

    @Test
    void unZipHechoDeLaCarpetaDaLoMismoQueLaCarpeta() throws IOException {
        Lectura lectura = CarpetaModelos.leerZip(zip("FALL27/", "FALL27/CM100/", "FALL27/CM100/a.jpg",
                "FALL27/CM200/f.pdf", "__MACOSX/FALL27/CM100/._a.jpg"), destino);

        assertEquals("FALL27", lectura.raiz());
        assertEquals(List.of("CM100", "CM200"), new ArrayList<>(lectura.porCarpeta().keySet()));
    }

    @Test
    void unZipHechoDelContenidoNoTieneRaiz() throws IOException {
        Lectura lectura = CarpetaModelos.leerZip(zip("CM100/a.jpg", "CM200/b.jpg"), destino);

        assertEquals("", lectura.raiz());
        assertEquals(List.of("CM100", "CM200"), new ArrayList<>(lectura.porCarpeta().keySet()));
    }

    @Test
    void loQueNoEsFotoNiPdfYLoSueltoSeAvisaYNoSeUsa() throws IOException {
        Lectura lectura = CarpetaModelos.leer(List.of(
                fichero("FALL27/CM100/a.jpg"),
                fichero("FALL27/CM100/notas.docx"),
                fichero("FALL27/suelta.jpg"),
                fichero("FALL27/CM100/Thumbs.db")), destino);

        assertEquals(1, lectura.porCarpeta().get("CM100").size());
        assertEquals(2, lectura.avisos().size(), "la basura del sistema no avisa");
        assertTrue(lectura.avisos().stream().anyMatch(aviso -> aviso.contains("suelta.jpg")));
        assertTrue(lectura.avisos().stream().anyMatch(aviso -> aviso.contains("notas.docx")));
    }

    @Test
    void unaRutaConPuntosNoSaleDelDirectorioDelLote() throws IOException {
        Lectura lectura = CarpetaModelos.leerZip(zip("FALL27/CM100/../../../fuera.jpg"), destino);

        for (List<FicheroModelo> ficheros : lectura.porCarpeta().values()) {
            for (FicheroModelo copiado : ficheros) {
                assertTrue(copiado.copia().normalize().startsWith(destino));
            }
        }
    }

    @Test
    void sinNadaQueLeerEstaVacia() throws IOException {
        assertTrue(CarpetaModelos.leer(List.of(), destino).estaVacia());
    }

    @Test
    void loQueNoEsUnZipLanza() throws IOException {
        Path falso = destino.resolve("falso.zip");
        Files.writeString(falso, "no soy un zip");
        assertThrows(IOException.class, () -> CarpetaModelos.leerZip(falso, destino));
    }

    @Test
    void elEsqueletoLlevaUnaCarpetaVaciaPorModeloDentroDeLaTemporada() throws IOException {
        byte[] zip = EsqueletoCarpetas.zip("FALL 27", List.of(
                EsqueletoCarpetas.carpetaDe("F67008", "LE NEIGE"),
                EsqueletoCarpetas.carpetaDe("ULL027", ""),
                EsqueletoCarpetas.carpetaDe("F63033", "porte/monnaie: rond"),
                EsqueletoCarpetas.carpetaDe("ULL027", "")));

        List<String> entradas = new ArrayList<>();
        try (ZipInputStream lector = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entrada = lector.getNextEntry(); entrada != null; entrada = lector.getNextEntry()) {
                assertTrue(entrada.isDirectory(), "el esqueleto está vacío: solo carpetas");
                entradas.add(entrada.getName());
            }
        }
        assertEquals(List.of("FALL 27/", "FALL 27/F67008 LE NEIGE/", "FALL 27/ULL027/",
                "FALL 27/F63033 porte-monnaie- rond/"), entradas);
        assertFalse(EsqueletoCarpetas.zip("", List.of()).length == 0, "sin temporada, la raíz tiene un nombre por defecto");
    }
}
