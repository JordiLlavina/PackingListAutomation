package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** El EAN13 del mock de la plantilla del cliente. */
    private static final String EAN_VALIDO = "3666598354771";

    /** Proporción barras:dígitos medida en el mock del cliente. */
    private static final double RATIO_BARRAS_DIGITOS_DEL_MOCK = 5.5;

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

    @Test
    void conProporcionLaImagenSaleConEsaProporcion() throws Exception {
        byte[] png = CodigoBarrasEan13.png(EAN_VALIDO, 3.3).orElseThrow();
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(3.3, (double) imagen.getWidth() / imagen.getHeight(), 0.15);
    }

    @Test
    void conProporcionCeroSaleExactamenteLoMismoQueSinProporcion() {
        assertArrayEquals(CodigoBarrasEan13.png(EAN_VALIDO).orElseThrow(),
                CodigoBarrasEan13.png(EAN_VALIDO, 0).orElseThrow());
    }

    @Test
    void laProporcionNoCambiaElAnchoDelCodigo() throws Exception {
        // El ancho lo fija el ancho de módulo, no la proporción: lo que se
        // ajusta es el alto de las barras. Si esto cambiara, la imagen
        // compuesta dejaría de poder dimensionarse a partir del código.
        BufferedImage suelto = ImageIO.read(new ByteArrayInputStream(
                CodigoBarrasEan13.png(EAN_VALIDO).orElseThrow()));
        BufferedImage ajustado = ImageIO.read(new ByteArrayInputStream(
                CodigoBarrasEan13.png(EAN_VALIDO, 3.3).orElseThrow()));
        assertEquals(suelto.getWidth(), ajustado.getWidth());
    }

    @Test
    void unEan13InvalidoConProporcionSigueDevolviendoVacio() {
        assertTrue(CodigoBarrasEan13.png("1234567890123", 3.3).isEmpty());
    }

    @Test
    void conProporcionAlargadaLasBarrasImitanElAltoDelMockDelCliente() throws Exception {
        // Con una proporción de hueco muy alargada como la de AMI, dejar el
        // cuerpo de los dígitos en su valor por defecto hacía que toda la
        // compresión se la llevaran las barras (regresión encontrada en
        // revisión: 4,36:1 en vez del 5,5:1 del mock del cliente). Se mide
        // fila a fila, como hizo el revisor: una fila de barra llena tiene
        // una densidad de negro alta y uniforme (barra+hueco alternando en
        // todo el ancho); una fila de dígitos o de cola de guarda, mucha
        // menos. La proporción de la imagen compuesta de AMI es la que se
        // usa aquí porque es la que desencadenó el hallazgo.
        double proporcionDelCodigo = 2.1969 * 0.96 / 0.64;
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(
                CodigoBarrasEan13.png(EAN_VALIDO, proporcionDelCodigo).orElseThrow()));

        int umbralFilaDeBarras = imagen.getWidth() * 3 / 10;
        int filasDeBarras = 0;
        int filasDeDigitos = 0;
        for (int y = 0; y < imagen.getHeight(); y++) {
            int negros = 0;
            for (int x = 0; x < imagen.getWidth(); x++) {
                if ((imagen.getRGB(x, y) & 0xFFFFFF) == 0x000000) {
                    negros++;
                }
            }
            if (negros == 0) {
                continue; // ni barra ni dígito: margen en blanco
            }
            if (negros >= umbralFilaDeBarras) {
                filasDeBarras++;
            } else {
                filasDeDigitos++;
            }
        }
        double ratio = (double) filasDeBarras / filasDeDigitos;
        assertEquals(RATIO_BARRAS_DIGITOS_DEL_MOCK, ratio, 0.75,
                "barras:dígitos debería imitar el mock del cliente (5,5:1); salió "
                        + filasDeBarras + ":" + filasDeDigitos + " = " + ratio);
    }
}
