package com.puntotres.packinglist.service.embalaje;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

class CodigoQrTest {

    @Test
    void elPngSeDecodificaAlMismoTexto() throws Exception {
        byte[] png = CodigoQr.png("https://youtu.be/abc123", 300);
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(300, imagen.getWidth());
        assertEquals(300, imagen.getHeight());
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen)));
        assertEquals("https://youtu.be/abc123", new MultiFormatReader().decode(bitmap).getText());
    }
}
