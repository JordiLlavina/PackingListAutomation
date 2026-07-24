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
}
