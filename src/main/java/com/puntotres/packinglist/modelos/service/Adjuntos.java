package com.puntotres.packinglist.modelos.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.modelos.persistence.Adjunto;
import com.puntotres.packinglist.modelos.persistence.AdjuntoRepository;
import com.puntotres.packinglist.modelos.persistence.EstadoTranscripcion;
import com.puntotres.packinglist.modelos.persistence.MensajeRepository;
import com.puntotres.packinglist.modelos.persistence.ResumenAdjunto;
import com.puntotres.packinglist.modelos.persistence.TipoAdjunto;
import com.puntotres.packinglist.service.corte.ReductorImagen;

/**
 * Las fotos, notas de voz y documentos de los mensajes de una modificación:
 * qué se admite, cómo se guarda y cómo va su transcripción.
 *
 * Una foto se guarda reducida a {@value ReductorImagen#LADO_MAXIMO} px y en
 * JPEG, como las del catálogo, aunque el navegador ya la mande reducida: así
 * la base de datos no depende de lo que haga cada navegador. Una nota de voz y
 * un documento se guardan tal cual llegan, pero una nota de voz tiene que
 * decir un tipo de audio. Guardar una nota de voz publica
 * {@link AdjuntoAudioGuardado} para que alguien la transcriba; aquí no se
 * transcribe nada.
 */
@Service
public class Adjuntos {

    /** Lo más que puede durar una nota de voz: tres minutos. */
    public static final int MAX_AUDIO_SEG = 180;

    /** Lo más que puede pesar un adjunto: 15 MB. */
    public static final long MAX_BYTES = 15L * 1024 * 1024;

    /** Lo que cabe en {@code adjunto.mime}. */
    private static final int LARGO_MIME = 100;

    /** Hasta dónde un punto final se lee como extensión. */
    private static final int LARGO_EXTENSION = 10;

    private static final String MIME_DESCONOCIDO = "application/octet-stream";

    private final AdjuntoRepository repositorio;
    private final MensajeRepository mensajes;
    private final ApplicationEventPublisher eventos;

    public Adjuntos(AdjuntoRepository repositorio, MensajeRepository mensajes, ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.mensajes = mensajes;
        this.eventos = eventos;
    }

    /**
     * Valida (400 con mensaje) y normaliza: una FOTO se reduce a 1.600 px con
     * {@link ReductorImagen} y se guarda como JPEG.
     */
    @Transactional
    public Adjunto guardar(Long mensajeId, AdjuntoNuevo nuevo) {
        if (nuevo == null || nuevo.tipo() == null) {
            throw new IllegalArgumentException("Falta el tipo del adjunto");
        }
        if (nuevo.nombre() == null || nuevo.nombre().isBlank()) {
            throw new IllegalArgumentException("El adjunto necesita un nombre");
        }
        String nombre = nuevo.nombre().trim();
        byte[] bytes = nuevo.bytes();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("El fichero " + nombre + " está vacío");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("El fichero " + nombre + " pesa más de 15 MB");
        }
        if (nuevo.tipo() == TipoAdjunto.AUDIO && nuevo.duracionSeg() != null
                && nuevo.duracionSeg() > MAX_AUDIO_SEG) {
            throw new IllegalArgumentException("La nota de voz no puede pasar de 3 minutos");
        }
        String mime = mime(nuevo.mime());
        if (nuevo.tipo() == TipoAdjunto.AUDIO && !esAudio(mime)) {
            throw new IllegalArgumentException("La nota de voz no es un fichero de audio");
        }
        if (mensajeId == null || !mensajes.existsById(mensajeId)) {
            throw new NoSuchElementException("No hay ningún mensaje con id " + mensajeId);
        }
        if (nuevo.tipo() == TipoAdjunto.FOTO) {
            bytes = jpegReducido(bytes, nombre);
            nombre = conExtensionJpg(nombre);
            mime = "image/jpeg";
        }
        boolean esAudio = nuevo.tipo() == TipoAdjunto.AUDIO;
        Adjunto adjunto = repositorio.save(new Adjunto(mensajeId, nuevo.tipo(),
                Modelos.nombreLibre(nombre, Set.of()), mime, bytes, esAudio ? nuevo.duracionSeg() : null,
                esAudio ? EstadoTranscripcion.PENDIENTE : EstadoTranscripcion.NO_APLICA, LocalDateTime.now()));
        if (esAudio) {
            eventos.publishEvent(new AdjuntoAudioGuardado(adjunto.getId()));
        }
        return adjunto;
    }

    /** El adjunto con sus bytes, para servirlo. */
    @Transactional(readOnly = true)
    public Optional<Adjunto> conBytes(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id);
    }

    /** Los adjuntos de esos mensajes, sin bytes, por mensaje y en el orden en que se guardaron. */
    @Transactional(readOnly = true)
    public List<ResumenAdjunto> deMensajes(Collection<Long> mensajeIds) {
        if (mensajeIds == null || mensajeIds.isEmpty()) {
            return List.of();
        }
        return repositorio.resumenes(mensajeIds);
    }

    @Transactional
    public void marcarTranscripcion(Long id, EstadoTranscripcion estado, String transcripcion, String error) {
        adjuntoQueExiste(id).marcarTranscripcion(estado, transcripcion, error);
    }

    @Transactional
    public void marcarResumen(Long id, String resumenIa) {
        adjuntoQueExiste(id).marcarResumen(resumenIa);
    }

    /**
     * Como {@link #marcarTranscripcion}, pero solo si sigue {@code PENDIENTE}:
     * lo usa quien transcribe en segundo plano, para no pisar lo que se haya
     * marcado mientras tanto. Dice si lo ha apuntado.
     */
    @Transactional
    public boolean marcarTranscripcionSiPendiente(Long id, EstadoTranscripcion estado, String transcripcion,
                                                  String error) {
        return repositorio.marcarTranscripcionSiSigue(id, EstadoTranscripcion.PENDIENTE, estado, transcripcion,
                error) > 0;
    }

    /**
     * Devuelve una nota de voz a {@code PENDIENTE} para transcribirla otra
     * vez: sin texto, sin resumen y sin error. Un 400 si no es una nota de
     * voz, un 404 si no existe. Encolarla es cosa de quien transcribe.
     */
    @Transactional
    public void volverAPendiente(Long id) {
        Adjunto adjunto = adjuntoQueExiste(id);
        if (adjunto.getTipo() != TipoAdjunto.AUDIO) {
            throw new IllegalArgumentException("Solo se transcribe una nota de voz");
        }
        adjunto.marcarTranscripcion(EstadoTranscripcion.PENDIENTE, null, null);
        adjunto.marcarResumen(null);
    }

    /** Las notas de voz que siguen pendientes de transcribir, de la más vieja a la más nueva. */
    @Transactional(readOnly = true)
    public List<Long> notasPendientes() {
        return repositorio.idsPorTipoYEstado(TipoAdjunto.AUDIO, EstadoTranscripcion.PENDIENTE);
    }

    private Adjunto adjuntoQueExiste(Long id) {
        return conBytes(id).orElseThrow(() -> new NoSuchElementException("No hay ningún adjunto con id " + id));
    }

    /** La foto reducida y en JPEG; un 400 que nombra el fichero si no es una imagen que Java sepa leer. */
    private static byte[] jpegReducido(byte[] bytes, String nombre) {
        BufferedImage imagen = null;
        try {
            imagen = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException ilegible) {
            // Igual que si no fuera una imagen: el mensaje de abajo.
        }
        if (imagen == null) {
            throw new IllegalArgumentException("El fichero " + nombre + " no es una imagen");
        }
        try {
            return ReductorImagen.jpeg(ReductorImagen.reducir(imagen, ReductorImagen.LADO_MAXIMO));
        } catch (IOException enMemoria) {
            throw new UncheckedIOException("No se ha podido pasar a JPEG la foto " + nombre, enMemoria);
        }
    }

    /**
     * Un tipo de audio, o {@code video/webm}, que es lo que dice el
     * MediaRecorder de Chrome de algunas grabaciones solo de voz. Lo demás no
     * se admite como nota de voz: se sirve en línea con el tipo que trajo, y
     * un {@code text/html} se pintaría como una página de la aplicación.
     */
    private static boolean esAudio(String mime) {
        String tipo = mime.toLowerCase(Locale.ROOT);
        int puntoYComa = tipo.indexOf(';');
        String sinParametros = (puntoYComa >= 0 ? tipo.substring(0, puntoYComa) : tipo).trim();
        return sinParametros.startsWith("audio/") || sinParametros.equals("video/webm");
    }

    /** El mismo nombre con extensión {@code .jpg}, que es lo que de verdad se guarda. */
    private static String conExtensionJpg(String nombre) {
        int punto = nombre.lastIndexOf('.');
        boolean conExtension = punto > 0 && nombre.length() - punto <= LARGO_EXTENSION;
        return (conExtension ? nombre.substring(0, punto) : nombre) + ".jpg";
    }

    /**
     * El tipo tal como lo dice el navegador ({@code audio/webm;codecs=opus}
     * se conserva entero, es lo que necesita el reproductor); sin él, uno
     * genérico, y si no cabe en su columna, sin los parámetros.
     */
    private static String mime(String mime) {
        if (mime == null || mime.isBlank()) {
            return MIME_DESCONOCIDO;
        }
        String limpio = mime.trim();
        if (limpio.length() <= LARGO_MIME) {
            return limpio;
        }
        int puntoYComa = limpio.indexOf(';');
        String sinParametros = puntoYComa > 0 ? limpio.substring(0, puntoYComa).trim() : limpio;
        return sinParametros.length() <= LARGO_MIME ? sinParametros : MIME_DESCONOCIDO;
    }
}
