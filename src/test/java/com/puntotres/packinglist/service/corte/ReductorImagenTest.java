package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class ReductorImagenTest {

    @Test
    void unaFotoGrandeSeQuedaEnElLadoMaximo() {
        BufferedImage reducida = ReductorImagen.reducir(
                new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB), 1600);

        assertEquals(1600, reducida.getWidth());
        assertEquals(1200, reducida.getHeight());
    }

    @Test
    void unaFotoPequenaNoSeAmpliaPeroSaleEnRgb() {
        BufferedImage reducida = ReductorImagen.reducir(
                new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB), 1600);

        assertEquals(800, reducida.getWidth());
        assertEquals(BufferedImage.TYPE_INT_RGB, reducida.getType());
    }

    @Test
    void loTransparenteSaleBlancoYNoNegro() {
        BufferedImage reducida = ReductorImagen.reducir(
                new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB), 1600);

        assertEquals(Color.WHITE.getRGB(), reducida.getRGB(5, 5));
    }

    @Test
    void laOrientacionSeisGiraNoventaGradosALaDerecha() {
        BufferedImage girada = ReductorImagen.orientar(marcaArribaIzquierda(), 6);

        assertEquals(20, girada.getWidth());
        assertEquals(40, girada.getHeight());
        assertTrue(esRojo(girada.getRGB(15, 5)), "la esquina de arriba a la izquierda pasa a arriba a la derecha");
        assertTrue(!esRojo(girada.getRGB(5, 5)));
    }

    @Test
    void laOrientacionOchoGiraALaIzquierdaYLaTresMediaVuelta() {
        BufferedImage ocho = ReductorImagen.orientar(marcaArribaIzquierda(), 8);
        BufferedImage tres = ReductorImagen.orientar(marcaArribaIzquierda(), 3);

        assertTrue(esRojo(ocho.getRGB(5, 35)), "abajo a la izquierda");
        assertTrue(esRojo(tres.getRGB(35, 15)), "abajo a la derecha");
    }

    @Test
    void sinOrientacionOConUnaDesconocidaSeQuedaIgual() {
        BufferedImage original = marcaArribaIzquierda();

        assertEquals(original, ReductorImagen.orientar(original, 1));
        assertEquals(original, ReductorImagen.orientar(original, 0));
    }

    @Test
    void laOrientacionSeLeeDelExifDelJpeg(@TempDir Path dir) throws IOException {
        Path conExif = dir.resolve("girada.jpg");
        Path sinExif = dir.resolve("sin.jpg");
        Files.write(conExif, FotosDePrueba.conOrientacionExif(FotosDePrueba.jpeg(30, 20, Color.GRAY), 6));
        Files.write(sinExif, FotosDePrueba.jpeg(30, 20, Color.GRAY));

        assertEquals(6, ReductorImagen.orientacionExif(conExif));
        assertEquals(1, ReductorImagen.orientacionExif(sinExif));
        assertEquals(30, ImageIO.read(conExif.toFile()).getWidth(), "el JPEG con EXIF se sigue leyendo");
    }

    @Test
    void escribirSobreUnFicheroMasGrandeNoDejaRestos(@TempDir Path dir) throws IOException {
        Path destino = dir.resolve("f.jpg");
        Files.write(destino, new byte[500_000]);

        ReductorImagen.escribirJpeg(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), destino);

        assertTrue(Files.size(destino) < 10_000);
        assertEquals(20, ImageIO.read(destino.toFile()).getWidth());
    }

    /** 40×20 en blanco con un cuadrado rojo de 10×10 en la esquina de arriba a la izquierda. */
    private static BufferedImage marcaArribaIzquierda() {
        BufferedImage imagen = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 40, 20);
        g.setColor(Color.RED);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        return imagen;
    }

    private static boolean esRojo(int rgb) {
        Color color = new Color(rgb);
        return color.getRed() > 200 && color.getGreen() < 60;
    }
}
