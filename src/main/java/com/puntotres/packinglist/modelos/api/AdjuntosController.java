package com.puntotres.packinglist.modelos.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.puntotres.packinglist.modelos.persistence.Adjunto;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.Adjuntos;
import com.puntotres.packinglist.modelos.service.Modificaciones;
import com.puntotres.packinglist.persistence.ResumenFoto;

/**
 * Los adjuntos de los mensajes: descargarlos (cualquiera que haya entrado) y
 * pasar una foto a la galería del modelo (el despacho).
 *
 * Se sirven como {@link Resource} para que Spring conteste a las peticiones
 * con {@code Range} (206 y {@code Content-Range}): el {@code <audio>} de
 * Chrome pide la nota de voz a trozos y sin eso no deja saltar ni, a veces,
 * reproducirla. Van con su nombre y con {@code nosniff}, y el tipo es el que
 * dijo el navegador al subirlo, así que solo se abren {@code inline} los
 * tipos que el navegador enseña sin ejecutar nada (ver
 * {@link #seAbreEnLinea}); lo demás se descarga.
 */
@RestController
@RequestMapping("/api/adjuntos")
public class AdjuntosController {

    private static final String DESPACHO = "hasAnyRole('DESPACHO','ADMIN')";

    /** Un adjunto no cambia nunca con el mismo id: se puede guardar un día en el navegador, sin compartir. */
    private static final String CACHE_ADJUNTO = "private, max-age=86400";

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final Adjuntos adjuntos;
    private final Modificaciones modificaciones;

    public AdjuntosController(Adjuntos adjuntos, Modificaciones modificaciones) {
        this.adjuntos = adjuntos;
        this.modificaciones = modificaciones;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> descargar(@PathVariable Long id) {
        Adjunto adjunto = adjuntos.conBytes(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay ningún adjunto con id " + id));
        MediaType tipo = tipo(adjunto.getMime());
        boolean enLinea = seAbreEnLinea(tipo);
        return ResponseEntity.ok()
                .contentType(enLinea ? tipo : MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition(enLinea ? "inline" : "attachment", adjunto.getNombre()))
                .header(HttpHeaders.CACHE_CONTROL, CACHE_ADJUNTO)
                .body(new ByteArrayResource(adjunto.getBytes()));
    }

    /**
     * Lo que el navegador enseña sin ejecutar nada: imágenes (menos SVG, que
     * lleva scripts), audio, vídeo, PDF y texto plano. Lo demás (un
     * {@code text/html}, un SVG...) se pintaría como una página de la
     * aplicación, con acceso a su cookie XSRF, y {@code nosniff} no lo evita:
     * se descarga como binario.
     */
    private static boolean seAbreEnLinea(MediaType tipo) {
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

    /** Copia la foto al modelo de su modificación y contesta con todas las fotos del modelo. */
    @PostMapping("/{id}/a-galeria")
    @PreAuthorize(DESPACHO)
    public List<ResumenFoto> aGaleria(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual quien) {
        return modificaciones.fotoAGaleria(id, quien);
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
