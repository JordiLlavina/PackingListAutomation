package com.puntotres.packinglist.service.embalaje;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

/**
 * El QR se pinta a mano (puntos y localizadores redondeados), así que lo que
 * importa es que un lector lo siga leyendo: se decodifica con ZXing.
 */
class CodigoQrTest {

    private static String decodificar(BufferedImage imagen) throws Exception {
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen)));
        return new MultiFormatReader().decode(bitmap).getText();
    }

    @Test
    void elPngSeDecodificaAlMismoTexto() throws Exception {
        byte[] png = CodigoQr.png("https://youtu.be/abc123", 300);
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertTrue(imagen.getWidth() >= 300, "los píxeles pedidos son el lado mínimo");
        assertEquals(imagen.getWidth(), imagen.getHeight(), "cuadrado");
        assertEquals("https://youtu.be/abc123", decodificar(imagen));
    }

    @Test
    void unEnlaceLargoDeDriveTambienSeLee() throws Exception {
        String enlace = "https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789/view?usp=sharing";
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(CodigoQr.png(enlace, 413)));
        assertTrue(imagen.getWidth() >= 413);
        assertEquals(enlace, decodificar(imagen));
    }
}
