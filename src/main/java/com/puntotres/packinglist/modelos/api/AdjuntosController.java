package com.puntotres.packinglist.modelos.api;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
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
import com.puntotres.packinglist.modelos.persistence.EstadoTranscripcion;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.Adjuntos;
import com.puntotres.packinglist.modelos.service.Modificaciones;
import com.puntotres.packinglist.modelos.service.transcripcion.TranscripcionService;
import com.puntotres.packinglist.persistence.ResumenFoto;

/**
 * Los adjuntos de los mensajes: descargarlos y volver a transcribir una nota
 * de voz (cualquiera que haya entrado), y pasar una foto a la galería del
 * modelo (el despacho).
 *
 * Se sirven con {@link DescargaFichero}, la misma regla que los documentos de
 * los modelos: con {@code Range} (el {@code <audio>} de Chrome pide la nota de
 * voz a trozos), con su nombre y {@code nosniff}, y en línea solo los tipos
 * que el navegador enseña sin ejecutar nada; lo demás se descarga.
 */
@RestController
@RequestMapping("/api/adjuntos")
public class AdjuntosController {

    private static final String DESPACHO = "hasAnyRole('DESPACHO','ADMIN')";

    private final Adjuntos adjuntos;
    private final Modificaciones modificaciones;
    private final TranscripcionService transcripcion;

    public AdjuntosController(Adjuntos adjuntos, Modificaciones modificaciones, TranscripcionService transcripcion) {
        this.adjuntos = adjuntos;
        this.modificaciones = modificaciones;
        this.transcripcion = transcripcion;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> descargar(@PathVariable Long id) {
        Adjunto adjunto = adjuntos.conBytes(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay ningún adjunto con id " + id));
        return DescargaFichero.servir(adjunto.getNombre(), adjunto.getMime(), adjunto.getBytes());
    }

    /** Cómo queda una nota de voz que se vuelve a transcribir. */
    public record NotaEnCola(Long id, EstadoTranscripcion estadoTranscripcion) {
    }

    /**
     * Vuelve a transcribir una nota de voz (el «Reintentar» de una que falló):
     * queda {@code PENDIENTE} y se transcribe en segundo plano, así que la
     * respuesta no espera a Azure. 400 si el adjunto no es una nota de voz.
     */
    @PostMapping("/{id}/retranscribir")
    public NotaEnCola retranscribir(@PathVariable Long id) {
        transcripcion.retranscribir(id);
        return new NotaEnCola(id, EstadoTranscripcion.PENDIENTE);
    }

    /** Copia la foto al modelo de su modificación y contesta con todas las fotos del modelo. */
    @PostMapping("/{id}/a-galeria")
    @PreAuthorize(DESPACHO)
    public List<ResumenFoto> aGaleria(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual quien) {
        return modificaciones.fotoAGaleria(id, quien);
    }
}
