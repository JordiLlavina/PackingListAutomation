package com.puntotres.packinglist.service.etiquetas;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import org.krysalis.barcode4j.impl.code128.Code128Bean;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;

/**
 * Genera la imagen PNG de un código de barras Code 128 con el texto
 * legible debajo, tal como aparecen en las etiquetas de caja de ejemplo.
 * (El spec original hablaba de EAN-13, pero los ejemplos reales codifican
 * el PO de 5 dígitos, imposible como EAN-13; decisión: Code 128.)
 *
 * Lo usan los dos Code 128 de las etiquetas de caja de AMI: el PO (5 dígitos)
 * y el EAN128 del excel de pedido (44 caracteres).
 */
public final class CodigoBarrasCode128 {

    /**
     * Ancho medio de un carácter como fracción del cuerpo de la fuente, para
     * estimar cuánto ocupa el texto legible. 0,6 es lo típico de una sans
     * serif y aquí solo hace falta para no pasarse de ancho.
     */
    private static final double ANCHO_MEDIO_CARACTER = 0.6;

    private CodigoBarrasCode128() {
    }

    public static byte[] png(String texto) {
        Code128Bean codigo = new Code128Bean();
        codigo.doQuietZone(true);
        ajustarLaFuenteAlAnchoDelCodigo(codigo, texto);
        BitmapCanvasProvider lienzo =
                new BitmapCanvasProvider(300, BufferedImage.TYPE_BYTE_BINARY, false, 0);
        codigo.generateBarcode(lienzo, texto);
        try {
            lienzo.finish();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(lienzo.getBufferedImage(), "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el código de barras de '" + texto + "'", e);
        }
    }

    /**
     * barcode4j dimensiona el lienzo con las BARRAS y pinta el texto legible
     * centrado debajo: si el texto es más ancho, se sale y se recorta. Pasa
     * con el EAN128 de AMI, que son 44 caracteres codificados en pocas barras
     * (los dígitos van de dos en dos en el modo C). Se reduce el cuerpo de la
     * fuente lo justo para que quepa; para mensajes cortos como el PO no toca
     * nada.
     */
    private static void ajustarLaFuenteAlAnchoDelCodigo(Code128Bean codigo, String texto) {
        double anchoBarras = codigo.calcDimensions(texto).getWidth();
        double anchoTexto = texto.length() * codigo.getFontSize() * ANCHO_MEDIO_CARACTER;
        if (anchoTexto > anchoBarras) {
            codigo.setFontSize(codigo.getFontSize() * anchoBarras / anchoTexto);
        }
    }
}
