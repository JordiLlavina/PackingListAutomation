package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class CodigoBarrasEan13Test {

    /** EAN13 real del pedido de H26; su dígito de control (2) es correcto. */
    private static final String VALIDO = "3666598543892";

    @Test
    void generaUnPngApaisadoParaUnEan13Valido() throws Exception {
        byte[] png = CodigoBarrasEan13.png(VALIDO).orElseThrow();

        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                Arrays.copyOfRange(png, 0, 4));
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(imagen);
        // Las barras más los dígitos debajo: siempre más ancho que alto.
        assertTrue(imagen.getWidth() > imagen.getHeight());
    }

    @Test
    void aceptaEspaciosAlrededor() {
        assertTrue(CodigoBarrasEan13.esValido("  " + VALIDO + " "));
        assertTrue(CodigoBarrasEan13.png("  " + VALIDO + " ").isPresent());
    }

    @Test
    void rechazaUnDigitoDeControlIncorrecto() {
        // Mismo código con el último dígito cambiado: 3 en vez de 2.
        assertFalse(CodigoBarrasEan13.esValido("3666598543893"));
        assertTrue(CodigoBarrasEan13.png("3666598543893").isEmpty());
    }

    @Test
    void rechazaLoQueNoEsUnEan13Completo() {
        // 12 dígitos no se completan con un control inventado: se rechaza.
        for (String malo : new String[] {null, "", "   ", "366659854389",
                "36665985438921", "366659854389X"}) {
            assertFalse(CodigoBarrasEan13.esValido(malo), "debería rechazar: " + malo);
            assertTrue(CodigoBarrasEan13.png(malo).isEmpty(), "debería rechazar: " + malo);
        }
    }
}
