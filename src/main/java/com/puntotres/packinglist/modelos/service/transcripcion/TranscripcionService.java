package com.puntotres.packinglist.modelos.service.transcripcion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.puntotres.packinglist.modelos.ModelosProperties;
import com.puntotres.packinglist.modelos.persistence.Adjunto;
import com.puntotres.packinglist.modelos.persistence.AdjuntoRepository;
import com.puntotres.packinglist.modelos.persistence.Direccion;
import com.puntotres.packinglist.modelos.persistence.EstadoTranscripcion;
import com.puntotres.packinglist.modelos.persistence.Mensaje;
import com.puntotres.packinglist.modelos.persistence.MensajeRepository;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.TipoAdjunto;
import com.puntotres.packinglist.modelos.service.AdjuntoAudioGuardado;
import com.puntotres.packinglist.modelos.service.Adjuntos;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;

/**
 * Transcribe las notas de voz y las resume para el despacho, en segundo
 * plano: guardar una nota de voz nunca espera a Azure ni a Claude, y que
 * cualquiera de los dos falle nunca impide crear un aviso o responder.
 *
 * Cada nota de voz pasa por un solo hilo ({@code transcripcion}), una detrás
 * de otra, y cada paso va en su propia transacción: no se tiene una
 * transacción abierta mientras se espera a la red.
 * <ol>
 * <li>Llega por {@link AdjuntoAudioGuardado}, cuando se confirma la
 * transacción que la guardó (antes no existiría para este hilo), o por
 * {@link #retranscribir}. Al arrancar se vuelven a encolar las que se quedaron
 * pendientes con la aplicación parada.</li>
 * <li>Sin transcriptor configurado: {@code NO_APLICA}, sin error (no es un
 * fallo, es que no hay servicio). Si el transcriptor falla: {@code FALLIDA}
 * con su mensaje, y no se resume. Si contesta: {@code HECHA} con el texto,
 * vacío si era un silencio.</li>
 * <li>Con texto y resumidor configurado se resume. Si el resumen falla, la nota
 * <b>se queda {@code HECHA}</b> con el error apuntado: la transcripción sigue
 * valiendo y es lo que importa; el resumen es un accesorio.</li>
 * <li>Con resumen, si la nota es de un aviso del taller que todavía no tiene
 * título, el título de la IA pasa a ser el suyo, marcado como de la IA para
 * que el despacho lo vea y lo pueda corregir. Un título que ya hay (el de una
 * orden, uno corregido) no se pisa nunca.</li>
 * </ol>
 *
 * Solo se apunta el resultado si la nota sigue como se dejó: si mientras se
 * transcribía alguien la ha vuelto a encolar o la ha marcado a mano, gana eso.
 * Un fallo inesperado de una nota se registra y no para el hilo.
 */
@Service
public class TranscripcionService {

    private static final Logger LOG = LoggerFactory.getLogger(TranscripcionService.class);

    /** Lo que se guarda, como mucho, de un mensaje de error. */
    static final int LARGO_ERROR = 2000;

    /** Lo que cabe en {@code modificacion.titulo}. */
    private static final int LARGO_TITULO = 200;

    private final Adjuntos adjuntos;
    private final AdjuntoRepository repositorioAdjuntos;
    private final MensajeRepository mensajes;
    private final ModificacionRepository modificaciones;
    private final ModeloRepository modelos;
    private final ModelosProperties props;
    private final Transcriptor transcriptor;
    private final ResumidorModificacion resumidor;
    private final TransactionTemplate transaccion;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(tarea -> {
        Thread nuevo = new Thread(tarea, "transcripcion");
        nuevo.setDaemon(true);
        return nuevo;
    });

    /** Las encoladas que no han acabado todavía, la que está en marcha incluida. */
    private final AtomicInteger pendientes = new AtomicInteger();

    public TranscripcionService(Adjuntos adjuntos, AdjuntoRepository repositorioAdjuntos, MensajeRepository mensajes,
                                ModificacionRepository modificaciones, ModeloRepository modelos,
                                ModelosProperties props, Transcriptor transcriptor,
                                ResumidorModificacion resumidor, PlatformTransactionManager transacciones) {
        this.adjuntos = adjuntos;
        this.repositorioAdjuntos = repositorioAdjuntos;
        this.mensajes = mensajes;
        this.modificaciones = modificaciones;
        this.modelos = modelos;
        this.props = props;
        this.transcriptor = transcriptor;
        this.resumidor = resumidor;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Una nota de voz recién guardada; se encola cuando su transacción ya se ha confirmado. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alGuardarAudio(AdjuntoAudioGuardado evento) {
        encolar(evento.adjuntoId());
    }

    /** Las que se quedaron pendientes con la aplicación parada; si no, se verían «transcribiendo…» para siempre. */
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            adjuntos.notasPendientes().forEach(this::encolar);
        } catch (RuntimeException e) {
            LOG.warn("No se han podido encolar las notas de voz pendientes al arrancar", e);
        }
    }

    /** La pone en la cola del hilo de la transcripción; vuelve enseguida. */
    public void encolar(Long adjuntoId) {
        if (adjuntoId == null) {
            return;
        }
        pendientes.incrementAndGet();
        try {
            hilo.execute(() -> {
                try {
                    procesar(adjuntoId);
                } catch (Throwable fallo) {
                    LOG.warn("Ha fallado la transcripción de la nota de voz {}", adjuntoId, fallo);
                    dejarFallida(adjuntoId, fallo);
                } finally {
                    pendientes.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException parado) {
            pendientes.decrementAndGet();
            LOG.warn("No se transcribe la nota de voz {}: la aplicación se está parando", adjuntoId);
        }
    }

    /**
     * Vuelve a transcribir una nota de voz (el «Reintentar» de una que falló,
     * o una que se quiere repetir): la deja {@code PENDIENTE}, sin texto, sin
     * resumen y sin error, y la encola. 400 si no es una nota de voz.
     */
    public void retranscribir(Long adjuntoId) {
        adjuntos.volverAPendiente(adjuntoId);
        encolar(adjuntoId);
    }

    /** Cuántas notas de voz quedan por acabar (para los tests: se espera a que sea 0). */
    public int pendientes() {
        return pendientes.get();
    }

    @PreDestroy
    void parar() {
        hilo.shutdownNow();
    }

    /** Lo de una nota de voz que hace falta fuera de la transacción. */
    private record Nota(byte[] bytes, String mime, String tipoCambio, String modelo) {
    }

    private void procesar(Long id) {
        Nota nota = transaccion.execute(estado -> leer(id));
        if (nota == null) {
            return;
        }
        if (!transcriptor.disponible()) {
            adjuntos.marcarTranscripcionSiPendiente(id, EstadoTranscripcion.NO_APLICA, null, null);
            return;
        }
        String transcrito;
        try {
            transcrito = transcriptor.transcribir(nota.bytes(), nota.mime());
        } catch (Exception fallo) {
            LOG.warn("No se ha podido transcribir la nota de voz {}: {}", id, fallo.getMessage());
            adjuntos.marcarTranscripcionSiPendiente(id, EstadoTranscripcion.FALLIDA, null, mensajeDe(fallo, ""));
            return;
        }
        String texto = transcrito == null ? "" : transcrito.strip();
        if (!adjuntos.marcarTranscripcionSiPendiente(id, EstadoTranscripcion.HECHA, texto, null)) {
            return;
        }
        if (texto.isEmpty() || !resumidor.disponible()) {
            return;
        }
        ResumenIa resumen;
        try {
            resumen = resumidor.resumir(texto, nota.tipoCambio(), nota.modelo());
            if (resumen == null) {
                throw new IllegalStateException("La IA no ha devuelto ningún resumen");
            }
        } catch (Exception fallo) {
            LOG.warn("No se ha podido resumir la nota de voz {}: {}", id, fallo.getMessage());
            String error = mensajeDe(fallo, "Sin resumen de la IA: ");
            transaccion.executeWithoutResult(estado -> siSigueHecha(id, texto,
                    adjunto -> adjunto.marcarTranscripcion(EstadoTranscripcion.HECHA, texto, error)));
            return;
        }
        transaccion.executeWithoutResult(estado -> siSigueHecha(id, texto, adjunto -> {
            adjunto.marcarResumen(textoParaDespacho(resumen));
            ponerTituloSiFalta(adjunto, resumen.titulo());
        }));
    }

    /** La nota con su contexto, o null si ya no hay nada que hacer con ella (borrada, no es audio, ya no está pendiente). */
    private Nota leer(Long id) {
        Adjunto adjunto = repositorioAdjuntos.findById(id).orElse(null);
        if (adjunto == null || adjunto.getTipo() != TipoAdjunto.AUDIO
                || adjunto.getEstadoTranscripcion() != EstadoTranscripcion.PENDIENTE) {
            return null;
        }
        Modificacion modificacion = modificacionDe(adjunto);
        String tipoCambio = "";
        String modelo = "";
        if (modificacion != null) {
            String clave = modificacion.getTipoCambio();
            tipoCambio = props.tipoCambio(clave).map(ModelosProperties.TipoCambio::texto)
                    .orElse(clave == null ? "" : clave);
            modelo = modelos.findById(modificacion.getModeloId()).map(TranscripcionService::nombreDe).orElse("");
        }
        return new Nota(adjunto.getBytes(), adjunto.getMime(), tipoCambio, modelo);
    }

    private interface Cambio {
        void aplicar(Adjunto adjunto);
    }

    /** Aplica el cambio solo si la nota sigue HECHA con ese mismo texto (no se ha vuelto a encolar entretanto). */
    private void siSigueHecha(Long id, String texto, Cambio cambio) {
        repositorioAdjuntos.findById(id)
                .filter(adjunto -> adjunto.getEstadoTranscripcion() == EstadoTranscripcion.HECHA
                        && texto.equals(adjunto.getTranscripcion()))
                .ifPresent(cambio::aplicar);
    }

    /**
     * El título de la IA en un aviso del taller que aún no tiene ninguno. Va
     * en una sola sentencia condicional y no cambiando la entidad: si el
     * despacho pone el título mientras tanto, el suyo no se pisa.
     */
    private void ponerTituloSiFalta(Adjunto adjunto, String tituloIa) {
        if (tituloIa == null || tituloIa.isBlank()) {
            return;
        }
        mensajes.findById(adjunto.getMensajeId())
                .map(Mensaje::getModificacionId)
                .ifPresent(id -> modificaciones.ponerTituloDeIaSiNoTiene(id, Direccion.TALLER_A_DESPACHO,
                        recortar(tituloIa.strip(), LARGO_TITULO)));
    }

    private Modificacion modificacionDe(Adjunto adjunto) {
        return mensajes.findById(adjunto.getMensajeId())
                .flatMap(mensaje -> modificaciones.findById(mensaje.getModificacionId()))
                .orElse(null);
    }

    /** Si algo inesperado ha parado el proceso con la nota aún pendiente, que se vea como fallida y no «transcribiendo…». */
    private void dejarFallida(Long id, Throwable fallo) {
        try {
            adjuntos.marcarTranscripcionSiPendiente(id, EstadoTranscripcion.FALLIDA, null, mensajeDe(fallo, ""));
        } catch (RuntimeException otro) {
            LOG.warn("Tampoco se ha podido marcar como fallida la nota de voz {}", id, otro);
        }
    }

    /**
     * El resumen como lo lee el despacho debajo de la nota de voz: el
     * resumen, y en líneas aparte el tipo de cambio sugerido (con su texto
     * del yml), los materiales y las dudas. El título no va: se ve arriba.
     */
    String textoParaDespacho(ResumenIa resumen) {
        List<String> lineas = new ArrayList<>();
        agregar(lineas, "", resumen.resumen());
        String tipo = resumen.tipoCambioSugerido();
        if (tipo != null && !tipo.isBlank()) {
            agregar(lineas, "Tipo de cambio sugerido: ",
                    props.tipoCambio(tipo.strip()).map(ModelosProperties.TipoCambio::texto).orElse(tipo.strip()));
        }
        if (resumen.materiales() != null && !resumen.materiales().isEmpty()) {
            agregar(lineas, "Materiales: ", String.join(", ", resumen.materiales()));
        }
        if (resumen.dudas() != null && !resumen.dudas().isEmpty()) {
            agregar(lineas, "Dudas: ", String.join("; ", resumen.dudas()));
        }
        if (lineas.isEmpty()) {
            agregar(lineas, "", resumen.titulo());
        }
        return lineas.isEmpty() ? null : String.join("\n", lineas);
    }

    private static void agregar(List<String> lineas, String rotulo, String valor) {
        if (valor != null && !valor.isBlank()) {
            lineas.add(rotulo + valor.strip());
        }
    }

    /** {@code ULL027 · Bolso Candy}: como se nombra el modelo en pantalla. */
    private static String nombreDe(Modelo modelo) {
        String nombre = modelo.getNombre();
        return modelo.nombreVisible() + (nombre.isBlank() ? "" : " · " + nombre);
    }

    private static String mensajeDe(Throwable fallo, String prefijo) {
        String mensaje = fallo.getMessage();
        String texto = mensaje == null || mensaje.isBlank() ? fallo.getClass().getSimpleName() : mensaje.strip();
        return recortar(prefijo + texto, LARGO_ERROR);
    }

    private static String recortar(String texto, int largo) {
        return texto.length() <= largo ? texto : texto.substring(0, largo);
    }
}
