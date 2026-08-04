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

    /**
     * Proporción barras:dígitos del mock del cliente ({@code image2.png} en
     * ETIQUETA CAJA AMI.xlsx), medida fila a fila. Solo se aplica dentro de
     * {@link #png(String, double)}: con proporciones de hueco muy alargadas
     * (como la del hueco de AMI) dejar el cuerpo de los dígitos en su valor
     * por defecto hacía que todo el achatamiento se lo llevaran las barras,
     * saliendo más aplastadas que en el mock.
     */
    private static final double RATIO_BARRAS_DIGITOS = 5.5;

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
     * repartiéndolo entre barras y dígitos con la proporción del mock del
     * cliente ({@link #RATIO_BARRAS_DIGITOS}) en vez de dejar los dígitos al
     * cuerpo por defecto. Si la proporción pedida no deja sitio ni para las
     * barras se ignora: mejor una imagen algo achatada que una sin barras.
     *
     * Orden importante: primero se fija el cuerpo de la fuente, luego se
     * vuelve a preguntar a calcDimensions cuánto alto de dígitos ha salido
     * de verdad (en vez de asumir que el alto de dígitos es exactamente el
     * cuerpo de fuente pedido) y solo entonces se ajusta el alto de barras
     * con ese dato real.
     */
    private static void ajustarElAltoALaProporcion(EAN13Bean codigo, String texto,
                                                   double proporcion) {
        if (proporcion <= 0) {
            return;
        }
        double anchoConZonaMuda = codigo.calcDimensions(texto).getWidthPlusQuiet();
        double altoTotal = anchoConZonaMuda / proporcion;
        double altoDeLosDigitosObjetivo = altoTotal / (RATIO_BARRAS_DIGITOS + 1);
        codigo.setFontSize(altoDeLosDigitosObjetivo);
        double altoDeLosDigitosReal =
                codigo.calcDimensions(texto).getHeight() - codigo.getBarHeight();
        double altoDeLasBarras = altoTotal - altoDeLosDigitosReal;
        if (altoDeLasBarras > 0) {
            codigo.setBarHeight(altoDeLasBarras);
        }
    }
}
