package com.puntotres.packinglist.modelos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.modelos.PdfDePrueba;
import com.puntotres.packinglist.modelos.api.dto.CandidataImagen;

/**
 * De un PDF salen las imágenes incrustadas que pueden ser fotos (las de al
 * menos {@value ImagenesDePdf#LADO_MINIMO} px de lado) y cada página pintada,
 * porque un dibujo técnico vectorial no tiene ninguna imagen que extraer. Sin
 * Spring: el directorio de los lotes es uno temporal del test.
 */
class ImagenesDePdfTest {

    @TempDir
    Path base;

    @Test
    void salenLasDosImagenesGrandesYLasPaginasPeroNoElLogo() throws Exception {
        ImagenesDePdf imagenes = new ImagenesDePdf(base);

        List<CandidataImagen> candidatas = imagenes.extraer("lote-1", PdfDePrueba.fichaTecnica());

        assertEquals(PdfDePrueba.CANDIDATAS, candidatas.size(), candidatas.toString());
        List<CandidataImagen> fotos = candidatas.stream().filter(c -> c.origen().equals("imagen")).toList();
        assertEquals(2, fotos.size(), "la foto pegada dos veces cuenta una y el logo no sale");
        assertTrue(fotos.stream().allMatch(c -> c.ancho() == 400 && c.alto() == 400), fotos.toString());
        assertEquals(List.of("página 1", "página 2"), candidatas.stream().map(CandidataImagen::origen)
                .filter(origen -> origen.startsWith("página")).toList());
        assertTrue(candidatas.stream().noneMatch(c -> c.ancho() == 100 && c.alto() == 100));

        for (CandidataImagen candidata : candidatas) {
            byte[] png = imagenes.candidata("lote-1", candidata.clave()).orElseThrow();
            BufferedImage leida = ImageIO.read(new ByteArrayInputStream(png));
            assertEquals(candidata.ancho(), leida.getWidth(), candidata.clave());
            assertEquals(candidata.alto(), leida.getHeight(), candidata.clave());
        }
        CandidataImagen pagina = candidatas.stream().filter(c -> c.origen().equals("página 1")).findFirst()
                .orElseThrow();
        // Un A4 son 595 × 842 puntos; a 100 ppp, 826 × 1169 px (PDFBox redondea hacia abajo).
        assertEquals(826, pagina.ancho());
        assertEquals(1169, pagina.alto());
    }

    @Test
    void unFicheroQueNoEsPdfSeRechazaConSuMensaje() {
        ImagenesDePdf imagenes = new ImagenesDePdf(base);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> imagenes.extraer("lote-2", "esto no es un PDF".getBytes(StandardCharsets.UTF_8)));

        assertEquals("El fichero no es un PDF que se pueda leer", error.getMessage());
    }

    @Test
    void unPdfDeMasDe50MbSeRechazaSinLeerlo() {
        ImagenesDePdf imagenes = new ImagenesDePdf(base);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> imagenes.extraer("lote-3", new byte[(int) ImagenesDePdf.MAX_BYTES + 1]));

        assertEquals("El documento pesa más de 50 MB", error.getMessage());
    }

    @Test
    void unaClaveOUnLoteConRutaNoSalenDelDirectorio() throws Exception {
        ImagenesDePdf imagenes = new ImagenesDePdf(base);
        String clave = imagenes.extraer("lote-4", PdfDePrueba.fichaTecnica()).get(0).clave();
        Files.writeString(base.resolve("x"), "fuera del lote");

        assertTrue(imagenes.candidata("lote-4", clave).isPresent());
        assertEquals(Optional.empty(), imagenes.candidata("lote-4", "../x"));
        assertEquals(Optional.empty(), imagenes.candidata("lote-4", "..\\x"));
        assertEquals(Optional.empty(), imagenes.candidata("lote-4", "a/../../x"));
        assertEquals(Optional.empty(), imagenes.candidata("../lote-4", clave));
        assertEquals(Optional.empty(), imagenes.candidata("lote-4", "no-existe"));
        assertEquals(Optional.empty(), imagenes.candidata("no-existe", clave));
        assertThrows(IllegalArgumentException.class, () -> imagenes.extraer("../fuera", PdfDePrueba.fichaTecnica()));
    }

    @Test
    void unLotePasadoDeVigenciaYaNoDaNadaYLaLimpiezaLoBorra() throws Exception {
        ImagenesDePdf imagenes = new ImagenesDePdf(base);
        String clave = imagenes.extraer("viejo", PdfDePrueba.fichaTecnica()).get(0).clave();
        imagenes.extraer("nuevo", PdfDePrueba.fichaTecnica());
        Path viejo = base.resolve(ImagenesDePdf.PREFIJO + "viejo");
        Files.setLastModifiedTime(viejo, FileTime.from(Instant.now().minus(ImagenesDePdf.VIGENCIA).minusSeconds(60)));

        assertEquals(Optional.empty(), imagenes.candidata("viejo", clave), "caducado aunque aún no se haya limpiado");

        imagenes.limpiarCaducados();

        assertFalse(Files.exists(viejo));
        assertTrue(Files.exists(base.resolve(ImagenesDePdf.PREFIJO + "nuevo")));
        assertTrue(imagenes.candidata("nuevo", clave).isPresent());
    }
}
