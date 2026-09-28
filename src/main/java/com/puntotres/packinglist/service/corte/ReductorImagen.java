package com.puntotres.packinglist.service.corte;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

/**
 * Deja una foto lista para meterla en un Word: en RGB, derecha y con el lado
 * mayor a {@value #LADO_MAXIMO} px como mucho.
 *
 * Reducir no es cosmético: una foto de móvil pesa 3-5 MB y un Word con ocho
 * pasa de 30 MB. A 1.600 px, una foto de la cuadrícula de 2×3 (unos 9 cm de
 * ancho) sale a más de 400 ppp, más de lo que imprime una impresora de
 * oficina.
 */
public final class ReductorImagen {

    public static final int LADO_MAXIMO = 1600;
    private static final float CALIDAD_JPEG = 0.88f;

    private ReductorImagen() {
    }

    /**
     * Reduce sin deformar y aplana la transparencia sobre blanco (dibujada
     * sobre RGB sin fondo saldría negra). Una foto que ya cabe no se amplía.
     */
    public static BufferedImage reducir(BufferedImage original, int ladoMaximo) {
        double escala = Math.min(1.0,
                (double) ladoMaximo / Math.max(original.getWidth(), original.getHeight()));
        int ancho = Math.max(1, (int) Math.round(original.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(original.getHeight() * escala));
        BufferedImage actual = original;
        // A saltos de la mitad: de golpe, la interpolación bilineal solo mira
        // los cuatro píxeles vecinos y una reducción grande sale con dientes.
        while (actual.getWidth() / 2 >= ancho && actual.getHeight() / 2 >= alto) {
            actual = escalar(actual, actual.getWidth() / 2, actual.getHeight() / 2);
        }
        return escalar(actual, ancho, alto);
    }

    /**
     * Endereza según la etiqueta de orientación EXIF (1-8). Los móviles
     * guardan la foto como sale del sensor y apuntan el giro en el EXIF; Java
     * no lo aplica al leer, así que sin esto las fotos verticales salen
     * tumbadas.
     */
    public static BufferedImage orientar(BufferedImage imagen, int orientacion) {
        if (orientacion < 2 || orientacion > 8) {
            return imagen;
        }
        int w = imagen.getWidth();
        int h = imagen.getHeight();
        // AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00·x + m01·y + m02, y' = m10·x + m11·y + m12
        AffineTransform giro = switch (orientacion) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        boolean cambiaDeLado = orientacion >= 5;
        BufferedImage destino = new BufferedImage(cambiaDeLado ? h : w, cambiaDeLado ? w : h,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.drawImage(imagen, giro, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    /** La orientación EXIF de un fichero, o 1 (tal cual) si no la trae o no se deja leer. */
    public static int orientacionExif(Path fichero) {
        try {
            Metadata metadatos = ImageMetadataReader.readMetadata(fichero.toFile());
            ExifIFD0Directory exif = metadatos.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            // Sin EXIF legible la foto se queda como está: no es motivo para perderla.
        }
        return 1;
    }

    public static void escribirJpeg(BufferedImage imagen, Path destino) throws IOException {
        // Sin borrar antes, un fichero más grande que el nuevo conserva su cola.
        Files.deleteIfExists(destino);
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(CALIDAD_JPEG);
        try (ImageOutputStream salida = ImageIO.createImageOutputStream(destino.toFile())) {
            escritor.setOutput(salida);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
    }

    private static BufferedImage escalar(BufferedImage origen, int ancho, int alto) {
        BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ancho, alto);
            g.drawImage(origen, 0, 0, ancho, alto, null);
        } finally {
            g.dispose();
        }
        return destino;
    }
}
