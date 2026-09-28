package com.puntotres.packinglist.testutil;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * Fotos fabricadas para los tests de los documentos del corte. Ninguna es una
 * foto de verdad: en el repo no entra ninguna foto personal ni de producto.
 */
public final class FotosDePrueba {

    private FotosDePrueba() {
    }

    /** Un JPEG de un solo color. */
    public static byte[] jpeg(int ancho, int alto, Color color) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, ancho, alto);
        g.dispose();
        return jpeg(imagen);
    }

    public static byte[] jpeg(BufferedImage imagen) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "jpg", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Una imagen de relleno con la silueta de un bolso y un rótulo, para los
     * Word de ejemplo que se revisan a ojo: se ve dónde cae cada foto y cómo
     * encaja según sea apaisada o vertical.
     */
    public static byte[] relleno(int ancho, int alto, String texto, Color color) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, color.brighter(), 0, alto, color.darker()));
        g.fillRect(0, 0, ancho, alto);
        int cuerpoAncho = ancho * 3 / 5;
        int cuerpoAlto = Math.min(alto * 2 / 5, cuerpoAncho);
        int x = (ancho - cuerpoAncho) / 2;
        int y = alto / 2 - cuerpoAlto / 4;
        g.setColor(new Color(255, 255, 255, 210));
        g.setStroke(new BasicStroke(Math.max(4, ancho / 60f)));
        g.drawArc(x + cuerpoAncho / 4, y - cuerpoAlto / 2, cuerpoAncho / 2, cuerpoAlto, 0, 180);
        g.fillRoundRect(x, y, cuerpoAncho, cuerpoAlto, ancho / 15, ancho / 15);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, ancho / 14)));
        FontMetrics medidas = g.getFontMetrics();
        g.drawString(texto, (ancho - medidas.stringWidth(texto)) / 2,
                y + cuerpoAlto / 2 + medidas.getAscent() / 3);
        g.dispose();
        return jpeg(imagen);
    }

    /**
     * El mismo JPEG con un bloque EXIF que solo trae la orientación, como el
     * que escribe un móvil que guarda la foto sin girarla. Va detrás del APP0
     * JFIF: delante, el lector JPEG de Java protesta.
     */
    public static byte[] conOrientacionExif(byte[] jpeg, int orientacion) {
        byte[] tiff = {
                'M', 'M', 0, 42, 0, 0, 0, 8,
                0, 1,
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientacion, 0, 0,
                0, 0, 0, 0};
        byte[] cabecera = {'E', 'x', 'i', 'f', 0, 0};
        int longitud = 2 + cabecera.length + tiff.length;
        int posicion = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            posicion = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(jpeg, 0, posicion);
        salida.write(0xFF);
        salida.write(0xE1);
        salida.write(longitud >> 8);
        salida.write(longitud & 0xFF);
        salida.writeBytes(cabecera);
        salida.writeBytes(tiff);
        salida.write(jpeg, posicion, jpeg.length - posicion);
        return salida.toByteArray();
    }

    /**
     * Un HEIC de verdad y pequeño (430×430, 8 KB): el de prueba del propio
     * Openize, "gimp_rgb_420_with_alpha.heic", con la licencia de su repo.
     */
    public static byte[] heicDePrueba() {
        try (InputStream entrada = FotosDePrueba.class.getResourceAsStream(
                "/ejemplos/corte/gimp_rgb_420_with_alpha.heic")) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
