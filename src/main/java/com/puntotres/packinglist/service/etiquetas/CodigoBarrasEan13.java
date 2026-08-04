package com.puntotres.packinglist.service.etiquetas;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.krysalis.barcode4j.impl.upcean.EAN13Bean;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;

/**
 * Genera la imagen PNG de un código de barras EAN-13 con los dígitos
 * legibles debajo, igual que los .gif de los ficheros de etiquetas de
 * artículo del cliente. Hermano de CodigoBarrasCode128, que hace lo mismo
 * para el PO de las etiquetas de caja.
 *
 * Devuelve Optional.empty() en vez de lanzar cuando el código no es un
 * EAN-13 válido: regla del proyecto, la etiqueta se imprime igual sin su
 * código de barras y quien llama acumula un aviso.
 */
public final class CodigoBarrasEan13 {

    private CodigoBarrasEan13() {
    }

    /**
     * ¿Son 13 dígitos con el dígito de control correcto? Se comprueba aquí
     * y no generando la imagen para poder avisar sin pagar el render (el
     * generador valida todas las filas, el builder solo dibuja las buenas).
     */
    public static boolean esValido(String ean13) {
        if (ean13 == null) {
            return false;
        }
        String digitos = ean13.trim();
        if (!digitos.matches("\\d{13}")) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 12; i++) {
            int digito = digitos.charAt(i) - '0';
            suma += (i % 2 == 0) ? digito : digito * 3;
        }
        int control = (10 - suma % 10) % 10;
        return control == digitos.charAt(12) - '0';
    }

    public static Optional<byte[]> png(String ean13) {
        return png(ean13, 0);
    }

    /**
     * Igual que {@link #png(String)} pero ajustando el alto de las barras para
     * que la imagen salga con la proporción ancho/alto pedida (0 = la que
     * salga). Lo usa ImagenEtiquetaArticulo: el código se pega en la imagen
     * compuesta a resolución nativa, sin reescalar, así que tiene que salir ya
     * con la forma del hueco que le toca.
     *
     * El ancho NO cambia: lo fija el ancho de módulo. Lo que se ajusta es el
     * alto de las barras.
     */
    public static Optional<byte[]> png(String ean13, double proporcion) {
        if (!esValido(ean13)) {
            return Optional.empty();
        }
        String digitos = ean13.trim();
        EAN13Bean codigo = new EAN13Bean();
        codigo.doQuietZone(true);
        ajustarElAltoALaProporcion(codigo, digitos, proporcion);
        BitmapCanvasProvider lienzo =
                new BitmapCanvasProvider(300, BufferedImage.TYPE_BYTE_BINARY, false, 0);
        try {
            codigo.generateBarcode(lienzo, digitos);
            lienzo.finish();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(lienzo.getBufferedImage(), "png", salida);
            return Optional.of(salida.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "No se pudo generar el código de barras de '" + ean13 + "'", e);
        }
    }

    /**
     * Deja el alto total (barras + dígitos legibles) en ancho/proporción,
     * tocando solo el alto de las barras. Si la proporción pedida no deja
     * sitio ni para las barras se ignora: mejor una imagen algo achatada que
     * una sin barras.
     */
    private static void ajustarElAltoALaProporcion(EAN13Bean codigo, String texto,
                                                   double proporcion) {
        if (proporcion <= 0) {
            return;
        }
        var dimensiones = codigo.calcDimensions(texto);
        double altoDeLosDigitos = dimensiones.getHeight() - codigo.getBarHeight();
        double altoDeLasBarras = dimensiones.getWidthPlusQuiet() / proporcion - altoDeLosDigitos;
        if (altoDeLasBarras > 0) {
            codigo.setBarHeight(altoDeLasBarras);
        }
    }
}
