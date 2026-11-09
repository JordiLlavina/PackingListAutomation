package com.puntotres.packinglist.modelos.api;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Cómo se sirve un fichero que alguien ha subido (un adjunto de un mensaje,
 * un documento de un modelo): una sola copia de la regla para todos los
 * controladores que lo hacen.
 *
 * Se sirve como {@link Resource} para que Spring conteste a las peticiones
 * con {@code Range} (206 y {@code Content-Range}): el {@code <audio>} de
 * Chrome pide la nota de voz a trozos y sin eso no deja saltar ni, a veces,
 * reproducirla. Va con su nombre y con {@code nosniff}, y el tipo es el que
 * dijo el navegador al subirlo, así que solo se abren {@code inline} los
 * tipos que el navegador enseña sin ejecutar nada (ver
 * {@link #seAbreEnLinea}); lo demás se descarga.
 */
final class DescargaFichero {

    /** Un fichero subido no cambia nunca con el mismo id: se puede guardar un día en el navegador, sin compartir. */
    static final String CACHE = "private, max-age=86400";

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private DescargaFichero() {
    }

    /** La respuesta con los bytes, el tipo si se puede abrir en línea (si no, binario y {@code attachment}) y su nombre. */
    static ResponseEntity<Resource> servir(String nombre, String mime, byte[] bytes) {
        MediaType tipo = tipo(mime);
        boolean enLinea = seAbreEnLinea(tipo);
        return ResponseEntity.ok()
                .contentType(enLinea ? tipo : MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(enLinea ? "inline" : "attachment", nombre))
                .header(HttpHeaders.CACHE_CONTROL, CACHE)
                .body(new ByteArrayResource(bytes));
    }

    /**
     * Lo que el navegador enseña sin ejecutar nada: imágenes (menos SVG, que
     * lleva scripts), audio, vídeo, PDF y texto plano. Lo demás (un
     * {@code text/html}, un SVG...) se pintaría como una página de la
     * aplicación, con acceso a su cookie XSRF, y {@code nosniff} no lo evita:
     * se descarga como binario.
     */
    static boolean seAbreEnLinea(MediaType tipo) {
        if (tipo.isWildcardType() || tipo.isWildcardSubtype()) {
            return false;
        }
        return switch (tipo.getType()) {
            case "image" -> !tipo.getSubtype().equals("svg+xml");
            case "audio", "video" -> true;
            case "application" -> tipo.getSubtype().equals("pdf");
            case "text" -> tipo.getSubtype().equals("plain");
            default -> false;
        };
    }

    /** El tipo guardado; uno que no se deja leer se sirve como binario genérico. */
    private static MediaType tipo(String mime) {
        try {
            return MediaType.parseMediaType(mime);
        } catch (InvalidMediaTypeException noSeEntiende) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * {@code inline; filename="…"} (o {@code attachment}) y, si el nombre no
     * es ASCII, además {@code filename*=UTF-8''…} (RFC 5987/6266): el
     * {@code filename} lleva entonces una versión ASCII con {@code _} en lugar
     * de lo demás, para los clientes que no entienden el otro.
     */
    private static String contentDisposition(String como, String nombre) {
        StringBuilder ascii = new StringBuilder();
        boolean soloAscii = true;
        for (int i = 0; i < nombre.length(); i++) {
            char letra = nombre.charAt(i);
            if (letra < 0x20 || letra > 0x7E) {
                soloAscii = false;
                ascii.append('_');
            } else {
                if (letra == '"' || letra == '\\') {
                    ascii.append('\\');
                }
                ascii.append(letra);
            }
        }
        String cabecera = como + "; filename=\"" + ascii + "\"";
        return soloAscii ? cabecera : cabecera + "; filename*=UTF-8''" + rfc5987(nombre);
    }

    /** Codificado por ciento salvo los {@code attr-char} de RFC 5987. */
    private static String rfc5987(String nombre) {
        StringBuilder codificado = new StringBuilder();
        for (byte b : nombre.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean libre = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "!#$&+-.^_`|~".indexOf(c) >= 0;
            if (libre) {
                codificado.append((char) c);
            } else {
                codificado.append('%').append(HEX[c >> 4]).append(HEX[c & 0x0F]);
            }
        }
        return codificado.toString();
    }
}
