package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class ConversorFotosTest {

    @TempDir
    Path dir;

    @Test
    void convierteJpgYHeicYAvisaDeLoQueNoSeLee() throws Exception {
        FotoModelo grande = foto("ULL027", "grande.jpg", "00001.jpg",
                FotosDePrueba.conOrientacionExif(FotosDePrueba.jpeg(3000, 2000, Color.GRAY), 6));
        FotoModelo heic = foto("ULL027", "b.HEIC", "00002.heic", FotosDePrueba.heicDePrueba());
        FotoModelo rota = foto("ULL712", "rota.jpg", "00003.jpg",
                "no soy un jpeg".getBytes(StandardCharsets.UTF_8));
        FotoModelo heicRoto = foto("ULL712", "rota.heic", "00004.heic",
                "tampoco soy un heic".getBytes(StandardCharsets.UTF_8));

        ConversionFotos conversion = new ConversorFotos(new DecodificadorHeicJava(), null)
                .convertir(List.of(grande, heic, rota, heicRoto), dir);
        conversion.esperar();

        assertTrue(conversion.terminada());
        assertEquals(4, conversion.total());
        assertEquals(4, conversion.hechas());
        BufferedImage enderezada = ImageIO.read(conversion.reducida(grande).orElseThrow().toFile());
        assertEquals(1067, enderezada.getWidth(), "3000x2000 girada por el EXIF y reducida");
        assertEquals(1600, enderezada.getHeight());
        assertEquals(430, ImageIO.read(conversion.reducida(heic).orElseThrow().toFile()).getWidth());
        assertTrue(conversion.reducida(rota).isEmpty());
        assertTrue(conversion.reducida(heicRoto).isEmpty());
        String avisos = String.join("\n", conversion.avisos());
        assertTrue(avisos.contains("rota.jpg") && avisos.contains("rota.heic"), avisos);
    }

    @Test
    void unPngConTransparenciaSaleEnJpegSobreFondoBlanco() throws Exception {
        // Pequeño a propósito: una foto que ya cabe no pasa por el escalado.
        BufferedImage transparente = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        ImageIO.write(transparente, "png", png);
        FotoModelo foto = foto("ULL027", "logo.png", "00001.png", png.toByteArray());

        ConversionFotos conversion = new ConversorFotos(new DecodificadorHeicJava(), null)
                .convertir(List.of(foto), dir);
        conversion.esperar();

        assertTrue(conversion.avisos().isEmpty(), conversion.avisos().toString());
        BufferedImage jpeg = ImageIO.read(conversion.reducida(foto).orElseThrow().toFile());
        assertEquals(200, jpeg.getWidth());
        Color centro = new Color(jpeg.getRGB(100, 50));
        assertTrue(centro.getRed() > 240 && centro.getGreen() > 240 && centro.getBlue() > 240,
                "el fondo transparente sale blanco y no negro: " + centro);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void enWindowsElHeicSaleIgualPorUnCaminoOPorElOtro() throws Exception {
        FotoModelo heic = foto("ULL027", "b.HEIC", "00001.heic", FotosDePrueba.heicDePrueba());
        FotoModelo roto = foto("ULL027", "c.heic", "00002.heic",
                "no soy un heic".getBytes(StandardCharsets.UTF_8));

        ConversionFotos conversion = new ConversorFotos(new DecodificadorHeicJava(),
                new ConversorHeicWindows()).convertir(List.of(heic, roto), dir);
        conversion.esperar();

        assertEquals(430, ImageIO.read(conversion.reducida(heic).orElseThrow().toFile()).getWidth());
        assertEquals(2, conversion.hechas());
        assertEquals(1, conversion.avisos().size(), conversion.avisos().toString());
    }

    @Test
    void losOriginalesSeBorranAlAcabarConEllosYSeQuedanLosReducidos() throws Exception {
        // Una temporada de fotos de 24 MP son cientos de megas: sin esto el
        // directorio de la sesión guardaba originales y reducidas a la vez
        // mientras se rellenaba la tabla, que puede ser media hora.
        FotoModelo buena = foto("ULL027", "a.jpg", "00001.jpg", FotosDePrueba.jpeg(300, 200, Color.GRAY));
        FotoModelo rota = foto("ULL027", "b.jpg", "00002.jpg",
                "no soy un jpeg".getBytes(StandardCharsets.UTF_8));

        ConversionFotos conversion = new ConversorFotos(new DecodificadorHeicJava(), null)
                .convertir(List.of(buena, rota), dir);
        conversion.esperar();

        assertTrue(Files.notExists(buena.original()));
        assertTrue(Files.notExists(rota.original()));
        assertTrue(Files.exists(conversion.reducida(buena).orElseThrow()));
    }

    @Test
    void sinFotosTerminaEnseguida() throws Exception {
        ConversionFotos conversion = new ConversorFotos(new DecodificadorHeicJava(), null)
                .convertir(List.of(), dir);

        conversion.esperar();
        assertTrue(conversion.terminada());
        assertEquals(0, conversion.total());
    }

    @Test
    void cancelarNoDejaAnadidasLasQueFaltan() throws Exception {
        FotoModelo grande = foto("ULL027", "a.jpg", "00001.jpg",
                FotosDePrueba.jpeg(3000, 2000, Color.GRAY));

        ConversionFotos conversion = new ConversorFotos(new DecodificadorHeicJava(), null)
                .convertir(List.of(grande), dir);
        conversion.cancelar();
        conversion.esperar();

        assertTrue(conversion.terminada());
        assertTrue(conversion.avisos().isEmpty(), "una cancelación no es un fallo de la foto");
    }

    private FotoModelo foto(String modelo, String nombre, String copia, byte[] contenido)
            throws IOException {
        Path fichero = Files.createDirectories(dir.resolve("originales")).resolve(copia);
        Files.write(fichero, contenido);
        return new FotoModelo(modelo, nombre, fichero);
    }
}
