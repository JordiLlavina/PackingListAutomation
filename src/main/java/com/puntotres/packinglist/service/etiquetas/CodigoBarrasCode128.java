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
 */
public final class CodigoBarrasCode128 {

    private CodigoBarrasCode128() {
    }

    public static byte[] png(String texto) {
        Code128Bean codigo = new Code128Bean();
        codigo.doQuietZone(true);
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
}
