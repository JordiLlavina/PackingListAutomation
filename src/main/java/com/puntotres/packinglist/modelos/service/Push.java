package com.puntotres.packinglist.modelos.service;

import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.modelos.ModelosProperties;
import com.puntotres.packinglist.modelos.persistence.DispositivoRepository;
import com.puntotres.packinglist.modelos.persistence.Mensaje;
import com.puntotres.packinglist.modelos.persistence.MensajeRepository;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.ResumenAdjunto;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.SuscripcionPush;
import com.puntotres.packinglist.modelos.persistence.SuscripcionPushRepository;
import com.puntotres.packinglist.modelos.persistence.TipoAdjunto;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;

/**
 * Los avisos al móvil (Web Push): las suscripciones de cada navegador y a
 * quién se le manda qué.
 *
 * <ul>
 * <li>Una orden nueva del despacho avisa al taller; un aviso nuevo del taller,
 * al despacho (el ADMIN cuenta como despacho); una respuesta, al otro lado del
 * que la escribe, <b>esté como esté la modificación</b>: también en una cerrada,
 * que es donde el despacho explica por qué la ha descartado.</li>
 * <li>Solo a usuarios activos, y nunca a un aparato revocado.</li>
 * <li>El push sale cuando se confirma la transacción que creó la
 * modificación o el mensaje (antes no existiría para el hilo de los push), y
 * en un hilo aparte ({@code push}): el servidor de push del navegador puede
 * tardar, y la petición no lo espera.</li>
 * <li>Cada envío va por su cuenta: una suscripción que el navegador ya ha
 * tirado (404 o 410) se borra; cualquier otro fallo se apunta en el log y se
 * sigue con las demás. Nada de esto llega nunca a quien ha creado o
 * contestado: la modificación ya está guardada.</li>
 * </ul>
 */
@Service
public class Push {

    private static final Logger LOG = LoggerFactory.getLogger(Push.class);

    /** Lo que se enseña, como mucho, del texto de una respuesta. */
    static final int LARGO_CUERPO = 80;

    /**
     * Lo que dice un aviso al móvil. El {@code tag} de la notificación es
     * {@code modificacion-<id>}: un segundo aviso de la misma modificación
     * sustituye al primero en vez de apilarse.
     */
    public record Aviso(String titulo, String cuerpo, String url, Long modificacionId) {

        /** La etiqueta de la notificación. */
        public String tag() {
            return "modificacion-" + modificacionId;
        }
    }

    private final SuscripcionPushRepository suscripciones;
    private final UsuarioRepository usuarios;
    private final DispositivoRepository dispositivos;
    private final ModificacionRepository modificaciones;
    private final MensajeRepository mensajes;
    private final Adjuntos adjuntos;
    private final ModeloRepository modelos;
    private final ModelosProperties props;
    private final EnviadorPush enviador;
    private final TransactionTemplate lectura;
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(tarea -> {
        Thread nuevo = new Thread(tarea, "push");
        nuevo.setDaemon(true);
        return nuevo;
    });

    /** Los push encolados que no han acabado todavía, el que está en marcha incluido. */
    private final AtomicInteger pendientes = new AtomicInteger();

    public Push(SuscripcionPushRepository suscripciones, UsuarioRepository usuarios,
                DispositivoRepository dispositivos, ModificacionRepository modificaciones,
                MensajeRepository mensajes, Adjuntos adjuntos, ModeloRepository modelos, ModelosProperties props,
                EnviadorPush enviador, PlatformTransactionManager transacciones) {
        this.suscripciones = suscripciones;
        this.usuarios = usuarios;
        this.dispositivos = dispositivos;
        this.modificaciones = modificaciones;
        this.mensajes = mensajes;
        this.adjuntos = adjuntos;
        this.modelos = modelos;
        this.props = props;
        this.enviador = enviador;
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    /** La clave pública VAPID, la que pide el navegador para suscribirse; vacía si no hay push. */
    public String clavePublica() {
        return configurado() ? props.push().vapidPublic().strip() : "";
    }

    /** Hay claves VAPID y se dejan leer: se mandan push de verdad. */
    public boolean configurado() {
        return props.pushConfigurado() && !(enviador instanceof EnviadorPushDesactivado);
    }

    /**
     * Da de alta la suscripción de este navegador. Un endpoint que ya estaba
     * (el mismo navegador que se vuelve a suscribir) se actualiza: pasa a ser
     * de quien está ahora dentro, con su aparato y sus claves.
     */
    @Transactional
    public void suscribir(UsuarioActual quien, String endpoint, String p256dh, String auth) {
        if (quien == null || quien.id() == null) {
            throw new IllegalArgumentException("Hay que entrar para recibir avisos");
        }
        String elEndpoint = obligatorio(endpoint, "Falta el endpoint");
        String laClave = obligatorio(p256dh, "Falta la clave p256dh");
        String elSecreto = obligatorio(auth, "Falta la clave auth");
        suscripciones.findFirstByEndpoint(elEndpoint).ifPresentOrElse(
                existente -> existente.renovar(quien.id(), quien.dispositivoId(), laClave, elSecreto),
                () -> suscripciones.save(new SuscripcionPush(quien.id(), quien.dispositivoId(), elEndpoint, laClave,
                        elSecreto)));
    }

    /** Da de baja la suscripción de este navegador, si es de quien la pide; si no, no hace nada. */
    @Transactional
    public void borrar(UsuarioActual quien, String endpoint) {
        if (quien == null || endpoint == null || endpoint.isBlank()) {
            return;
        }
        suscripciones.findFirstByEndpoint(endpoint.strip())
                .filter(s -> Objects.equals(s.getUsuarioId(), quien.id()))
                .ifPresent(suscripciones::delete);
    }

    /**
     * Manda el aviso a todas las suscripciones de los usuarios activos de ese
     * rol (con DESPACHO, también a los ADMIN), en el hilo de quien llama. No
     * lanza nunca: cada envío va por su cuenta.
     */
    public void avisarA(Rol rol, Aviso aviso) {
        if (rol == null || aviso == null) {
            return;
        }
        try {
            String carga = carga(aviso);
            for (SuscripcionPush s : suscripcionesDe(rol)) {
                enviarUna(s, carga);
            }
        } catch (RuntimeException e) {
            LOG.warn("No se han podido mandar los avisos al móvil de la modificación {}", aviso.modificacionId(), e);
        }
    }

    /** Un aviso o una orden recién creados: al otro lado, cuando la transacción se confirma. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCrear(ModificacionCreada evento) {
        encolar(() -> avisoDeCreada(evento));
    }

    /** Una respuesta: al otro lado del que la escribe, cuando la transacción se confirma. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alResponder(MensajeRespondido evento) {
        encolar(() -> avisoDeRespuesta(evento));
    }

    /** Cuántos push quedan por acabar (para los tests: se espera a que sea 0). */
    public int pendientes() {
        return pendientes.get();
    }

    @PreDestroy
    void parar() {
        hilo.shutdownNow();
    }

    /** A quién y qué: lo calcula el hilo de los push, que lo lee de la base de datos ya confirmada. */
    private record Destino(Rol rol, Aviso aviso) {
    }

    private void encolar(Supplier<Destino> queMandar) {
        pendientes.incrementAndGet();
        try {
            hilo.execute(() -> {
                try {
                    Destino destino = lectura.execute(estado -> queMandar.get());
                    if (destino != null) {
                        avisarA(destino.rol(), destino.aviso());
                    }
                } catch (Throwable fallo) {
                    LOG.warn("No se ha podido preparar un aviso al móvil", fallo);
                } finally {
                    pendientes.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException parado) {
            pendientes.decrementAndGet();
            LOG.warn("No se manda un aviso al móvil: la aplicación se está parando");
        }
    }

    private Destino avisoDeCreada(ModificacionCreada evento) {
        Modificacion m = modificaciones.findById(evento.id()).orElse(null);
        if (m == null) {
            return null;
        }
        Modelo modelo = modelos.findById(m.getModeloId()).orElse(null);
        String referencia = modelo == null ? "" : modelo.nombreVisible();
        if (m.esAviso()) {
            String tipo = props.tipoCambio(m.getTipoCambio()).map(ModelosProperties.TipoCambio::texto)
                    .orElse(Objects.toString(m.getTipoCambio(), "cambio"));
            String quien = usuarios.findById(evento.creadaPorId()).map(Usuario::getNombre).orElse("el taller");
            return new Destino(Rol.DESPACHO, new Aviso("Aviso del taller: " + tipo,
                    juntar(referencia, "de " + quien), "/app/bandeja/" + m.getId(), m.getId()));
        }
        String nombre = modelo == null ? "" : modelo.getNombre();
        return new Destino(Rol.TALLER, new Aviso("Orden de despacho: " + Objects.toString(m.getTitulo(), ""),
                juntar(referencia, nombre), "/app/ordenes/" + m.getId(), m.getId()));
    }

    private Destino avisoDeRespuesta(MensajeRespondido evento) {
        Modificacion m = modificaciones.findById(evento.modificacionId()).orElse(null);
        Mensaje ultimo = mensajes.findFirstByModificacionIdOrderByCreadoEnDescIdDesc(evento.modificacionId())
                .orElse(null);
        if (m == null || ultimo == null || evento.autor() == null) {
            return null;
        }
        boolean delDespacho = evento.autor().esDespacho();
        Rol para = delDespacho ? Rol.TALLER : Rol.DESPACHO;
        String autor = usuarios.findById(ultimo.getAutorId()).map(Usuario::getNombre)
                .orElse(delDespacho ? "despacho" : "el taller");
        String url;
        if (para == Rol.TALLER) {
            // El taller no tiene pantalla de un aviso suelto: sus avisos se leen en la lista.
            url = m.esAviso() ? "/app/avisos" : "/app/ordenes/" + m.getId();
        } else {
            url = "/app/bandeja/" + m.getId();
        }
        return new Destino(para, new Aviso("Respuesta de " + autor, cuerpoDe(ultimo), url, m.getId()));
    }

    /** Las 80 primeras letras del texto; sin texto, qué lleva. */
    private String cuerpoDe(Mensaje mensaje) {
        String texto = mensaje.getTexto() == null ? "" : mensaje.getTexto().strip();
        if (!texto.isEmpty()) {
            return texto.length() <= LARGO_CUERPO ? texto : texto.substring(0, LARGO_CUERPO) + "…";
        }
        List<ResumenAdjunto> suyos = adjuntos.deMensajes(List.of(mensaje.getId()));
        return suyos.stream().anyMatch(a -> a.tipo() == TipoAdjunto.AUDIO) ? "Nota de voz" : "Adjunto";
    }

    /** Las suscripciones de los usuarios activos de ese rol, sin las de un aparato revocado. */
    private List<SuscripcionPush> suscripcionesDe(Rol rol) {
        Set<Rol> roles = rol.esDespacho() ? EnumSet.of(Rol.DESPACHO, Rol.ADMIN) : EnumSet.of(rol);
        Set<Long> ids = usuarios.findByRolInAndActivoTrue(roles).stream().map(Usuario::getId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        List<SuscripcionPush> todas = suscripciones.findByUsuarioIdIn(ids);
        Set<Long> aparatos = todas.stream().map(SuscripcionPush::getDispositivoId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Boolean> revocados = new LinkedHashMap<>();
        dispositivos.findAllById(aparatos).forEach(d -> revocados.put(d.getId(), d.revocado()));
        return todas.stream()
                .filter(s -> s.getDispositivoId() == null || !revocados.getOrDefault(s.getDispositivoId(), false))
                .toList();
    }

    private void enviarUna(SuscripcionPush s, String carga) {
        try {
            enviador.enviar(s, carga);
        } catch (IOException e) {
            if (caducada(e)) {
                LOG.info("La suscripción {} ya no existe en el navegador; se borra", s.getId());
                borrarSinFallar(s);
            } else {
                LOG.warn("No se ha podido mandar el aviso a la suscripción {}: {}", s.getId(), e.getMessage());
            }
        } catch (RuntimeException e) {
            LOG.warn("No se ha podido mandar el aviso a la suscripción {}", s.getId(), e);
        }
    }

    private static boolean caducada(IOException e) {
        String mensaje = e.getMessage();
        return e instanceof SuscripcionCaducada
                || (mensaje != null && (mensaje.contains("410") || mensaje.contains("404")));
    }

    private void borrarSinFallar(SuscripcionPush s) {
        try {
            suscripciones.deleteById(s.getId());
        } catch (RuntimeException e) {
            LOG.warn("No se ha podido borrar la suscripción caducada {}", s.getId(), e);
        }
    }

    private String carga(Aviso aviso) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("titulo", aviso.titulo());
        campos.put("cuerpo", aviso.cuerpo());
        campos.put("url", aviso.url());
        campos.put("modificacionId", aviso.modificacionId());
        campos.put("tag", aviso.tag());
        try {
            return json.writeValueAsString(campos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se ha podido escribir el aviso en JSON", e);
        }
    }

    private static String juntar(String referencia, String resto) {
        if (resto == null || resto.isBlank()) {
            return referencia;
        }
        return referencia.isBlank() ? resto : referencia + " · " + resto;
    }

    private static String obligatorio(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.strip();
    }
}

