package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

import com.puntotres.packinglist.service.corte.ConversionFotos;
import com.puntotres.packinglist.service.corte.ConversorFotos;
import com.puntotres.packinglist.service.corte.FotoModelo;

/**
 * Una foto subida en un formulario (Hoja de Producción, Hoja de corte),
 * reducida a JPEG con el conversor del corte, HEIC incluido. Lanza
 * IOException con el motivo si no es una imagen que se pueda leer.
 */
final class FotoSubida {

    private FotoSubida() {
    }

    static byte[] reducir(MultipartFile subida, ConversorFotos conversor) throws IOException {
        Path directorio = Files.createTempDirectory("hoja-foto-");
        try {
            String original = subida.getOriginalFilename() == null ? "foto.jpg" : subida.getOriginalFilename();
            String nombre = Paths.get(original).getFileName().toString();
            String extension = nombre.contains(".") ? nombre.substring(nombre.lastIndexOf('.')) : ".jpg";
            Path copia = directorio.resolve("subida" + extension);
            subida.transferTo(copia);
            FotoModelo foto = new FotoModelo("hoja", nombre, copia);
            ConversionFotos conversion = conversor.convertir(List.of(foto), directorio);
            conversion.esperar();
            Optional<Path> reducida = conversion.reducida(foto);
            if (reducida.isEmpty()) {
                throw new IOException(conversion.avisos().isEmpty() ? "no es una imagen que se pueda leer"
                        : String.join(" ", conversion.avisos()));
            }
            return Files.readAllBytes(reducida.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("se ha interrumpido la conversión", e);
        } finally {
            CorteEnCurso.borrar(directorio);
        }
    }
}
