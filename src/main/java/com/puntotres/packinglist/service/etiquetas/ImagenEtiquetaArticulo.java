package com.puntotres.packinglist.service.etiquetas;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * La etiqueta de artículo entera en un solo PNG: los cuatro textos arriba
 * (referencia y color a la izquierda, talla y pedido a la derecha) y el
 * EAN-13 centrado debajo.
 *
 * Va en el hueco que la plantilla de etiquetas de caja de AMI reserva para
 * ella. Como imagen flotante única se puede colocar donde quiera el cliente
 * dentro de una maqueta de celdas fija.
 *
 * <b>El código de barras no se reescala nunca.</b> Se pide a
 * CodigoBarrasEan13 con la proporción que le toca —el ancho de un EAN-13 lo
 * fija el ancho de módulo, lo que se ajusta es el alto de las barras— y se
 * pega con drawImage a resolución nativa. Reescalar un código de barras con
 * interpolación lo deja bonito en pantalla e ilegible para un lector físico,
 * así que el lienzo se dimensiona A PARTIR del código y no al revés.
 *
 * Las proporciones (márgenes y alto de las líneas de texto) están medidas del
 * mock que el cliente dejó en su plantilla (290 × 132 px).
 */
public final class ImagenEtiquetaArticulo {

    /** Margen a cada lado, como fracción del lado correspondiente. */
    private static final double MARGEN = 0.02;
    /** Alto de cada una de las dos líneas de texto, como fracción del alto. */
    private static final double ALTO_LINEA = 0.16;
    /** Ancho del lienzo cuando no hay código que pegar, en px. */
    private static final int ANCHO_SIN_CODIGO_PX = 440;
    /** Cuerpo de la fuente como fracción del alto de su línea. */
    private static final double CUERPO = 0.75;
    private static final int CUERPO_MINIMO_PX = 8;
    private static final String FUENTE = "Arial";

    private ImagenEtiquetaArticulo() {
    }

    /**
     * proporcion = ancho/alto del hueco donde va la imagen. Nunca lanza por
     * datos: sin EAN-13 válido sale la imagen con los textos y sin código, y
     * un texto null se dibuja como vacío.
     */
    public static byte[] png(EtiquetaArticulo etiqueta, double proporcion) {
        BufferedImage codigo = codigoDeBarras(etiqueta.ean13(), proporcion);
        int ancho = codigo == null
                ? ANCHO_SIN_CODIGO_PX
                : (int) Math.round(codigo.getWidth() / fraccionAncho());
        int alto = (int) Math.round(ancho / proporcion);
        int margenX = (int) Math.round(ancho * MARGEN);
        int margenY = (int) Math.round(alto * MARGEN);
        int altoLinea = (int) Math.round(alto * ALTO_LINEA);

        BufferedImage lienzo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D pincel = lienzo.createGraphics();
        try {
            pincel.setColor(Color.WHITE);
            pincel.fillRect(0, 0, ancho, alto);
            pincel.setColor(Color.BLACK);
            pincel.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int anchoUtil = ancho - 2 * margenX;
            linea(pincel, etiqueta.referencia(), etiqueta.talla(),
                    margenX, margenY, anchoUtil, altoLinea);
            linea(pincel, etiqueta.color(), etiqueta.pedido(),
                    margenX, margenY + altoLinea, anchoUtil, altoLinea);
            if (codigo != null) {
                // 1:1, sin escalar y sin RenderingHints de interpolación.
                pincel.drawImage(codigo, (ancho - codigo.getWidth()) / 2,
                        alto - margenY - codigo.getHeight(), null);
            }
            pincel.setColor(Color.BLACK);
            pincel.drawRect(0, 0, ancho - 1, alto - 1);
        } finally {
            pincel.dispose();
        }
        return aPng(lienzo);
    }

    // --- pasos ---

    private static double fraccionAncho() {
        return 1 - 2 * MARGEN;
    }

    private static double fraccionAlto() {
        return 1 - 2 * ALTO_LINEA - 2 * MARGEN;
    }

    /** null si no hay EAN-13 válido: la imagen sale igual, sin código. */
    private static BufferedImage codigoDeBarras(String ean13, double proporcion) {
        return CodigoBarrasEan13.png(ean13, proporcion * fraccionAncho() / fraccionAlto())
                .map(ImagenEtiquetaArticulo::leer)
                .orElse(null);
    }

    /**
     * Una línea de la etiqueta: un texto pegado a la izquierda y otro a la
     * derecha. El cuerpo baja hasta que los dos caben sin solaparse; por
     * debajo del mínimo se deja de encoger y se acepta el solape antes que
     * dejar la etiqueta ilegible.
     */
    private static void linea(Graphics2D pincel, String izquierda, String derecha,
                              int x, int y, int ancho, int alto) {
        String izq = izquierda == null ? "" : izquierda;
        String der = derecha == null ? "" : derecha;
        int cuerpo = Math.max(CUERPO_MINIMO_PX, (int) Math.round(alto * CUERPO));
        Font fuente = new Font(FUENTE, Font.PLAIN, cuerpo);
        while (cuerpo > CUERPO_MINIMO_PX && anchoDe(pincel, fuente, izq)
                + anchoDe(pincel, fuente, der) > ancho) {
            cuerpo--;
            fuente = new Font(FUENTE, Font.PLAIN, cuerpo);
        }
        pincel.setFont(fuente);
        int base = y + alto - pincel.getFontMetrics().getDescent();
        pincel.drawString(izq, x, base);
        pincel.drawString(der, x + ancho - pincel.getFontMetrics().stringWidth(der), base);
    }

    private static int anchoDe(Graphics2D pincel, Font fuente, String texto) {
        return pincel.getFontMetrics(fuente).stringWidth(texto);
    }

    private static BufferedImage leer(byte[] png) {
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el código de barras generado", e);
        }
    }

    private static byte[] aPng(BufferedImage imagen) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir la imagen de la etiqueta", e);
        }
    }
}
