package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class CodigoBarrasCode128Test {

    @Test
    void generaUnPngLegibleConProporcionApaisada() throws Exception {
        byte[] png = CodigoBarrasCode128.png("07672");

        // Firma PNG
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                Arrays.copyOfRange(png, 0, 4));

        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(imagen);
        // Apaisado (más ancho que alto): las barras + el número debajo.
        assertTrue(imagen.getWidth() > imagen.getHeight());
    }

    @Test
    void elTextoDeUnMensajeLargoNoSaleCortado() throws Exception {
        // El EAN128 de AMI son 44 caracteres, más anchos a cuerpo normal que
        // las barras que los codifican: barcode4j dimensiona el lienzo solo
        // con las barras, así que el texto centrado se sale y se recorta.
        String ean128 = "366659835477100001000076650000000000000000ES";

        BufferedImage imagen = ImageIO.read(
                new ByteArrayInputStream(CodigoBarrasCode128.png(ean128)));

        assertTrue(margenBlancoEnLaBandaDelTexto(imagen),
                "el texto legible toca el borde de la imagen: sale cortado");
    }

    /**
     * ¿Queda blanco a los dos lados en la franja inferior, la del texto? El
     * texto va centrado, así que si no cabe pinta hasta el píxel del borde.
     */
    private static boolean margenBlancoEnLaBandaDelTexto(BufferedImage imagen) {
        int desde = (int) (imagen.getHeight() * 0.85);
        for (int y = desde; y < imagen.getHeight(); y++) {
            if (esOscuro(imagen, 0, y) || esOscuro(imagen, imagen.getWidth() - 1, y)) {
                return false;
            }
        }
        return true;
    }

    private static boolean esOscuro(BufferedImage imagen, int x, int y) {
        return (imagen.getRGB(x, y) & 0xFF) < 128;
    }
}
