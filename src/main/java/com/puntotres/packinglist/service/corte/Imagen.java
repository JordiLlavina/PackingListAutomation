package com.puntotres.packinglist.service.corte;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/** Una foto ya reducida, lista para el Word: el JPEG y sus medidas en píxeles. */
public record Imagen(byte[] jpeg, int ancho, int alto) {

    public static Imagen de(Path fichero) throws IOException {
        byte[] bytes = Files.readAllBytes(fichero);
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(bytes));
        if (imagen == null) {
            throw new IOException("no es una imagen legible: " + fichero.getFileName());
        }
        return new Imagen(bytes, imagen.getWidth(), imagen.getHeight());
    }
}
