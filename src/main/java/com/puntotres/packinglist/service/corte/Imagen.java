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
        return de(Files.readAllBytes(fichero), fichero.getFileName().toString());
    }

    /** Un JPEG ya reducido que está en memoria (las fotos del catálogo de modelos). */
    public static Imagen de(byte[] jpeg, String nombre) throws IOException {
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(jpeg));
        if (imagen == null) {
            throw new IOException("no es una imagen legible: " + nombre);
        }
        return new Imagen(jpeg, imagen.getWidth(), imagen.getHeight());
    }
}
