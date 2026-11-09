package com.puntotres.packinglist.modelos.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.modelos.ModelosProperties;
import com.puntotres.packinglist.modelos.persistence.AvisoEnviado;
import com.puntotres.packinglist.modelos.persistence.AvisoEnviadoRepository;
import com.puntotres.packinglist.modelos.persistence.EstadoModificacion;
import com.puntotres.packinglist.modelos.persistence.Mensaje;
import com.puntotres.packinglist.modelos.persistence.MensajeRepository;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionDestino;
import com.puntotres.packinglist.modelos.persistence.ModificacionDestinoRepository;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.Prioridad;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;

/**
 * Los recordatorios anti-olvido: cada hora mira lo que sigue abierto y manda
 * un push a quien tiene que moverlo. Las reglas y sus plazos están en
 * {@code modelos.recordatorios} de application.yml:
 *
 * <ul>
 * <li>Una orden del despacho al taller sin hacer: al TALLER, a los N días de
 * crearla según su prioridad ({@code dias-por-prioridad}; sin prioridad, la
 * de NORMAL) y otra vez cada N días.</li>
 * <li>Un aviso del taller sin prioridad: al DESPACHO, a las
 * {@code sin-prioridad-horas} de crearlo y otra vez cada tanto.</li>
 * <li>Un aviso del taller con prioridad y sin aplicar del todo: al DESPACHO,
 * cada {@code sin-aplicar-dias}, diciendo qué destinos faltan.</li>
 * <li>Una pregunta sin contestar ({@code necesita_respuesta}): al lado que
 * tiene que contestar (el contrario del autor del último mensaje; un ADMIN
 * habla por el despacho), a las {@code respuesta-pendiente-horas} de ese
 * mensaje y otra vez cada tanto.</li>
 * </ul>
 *
 * Lo cerrado (HECHA o DESCARTADA) no insiste nunca, tampoco con una pregunta
 * pendiente. Cada envío se apunta en {@code aviso_enviado} y no se repite
 * mientras el último del mismo tipo de esa modificación sea más reciente que
 * el plazo. Un plazo de 0 o sin configurar apaga su regla: avisar cada hora
 * sería ruido, no un recordatorio.
 *
 * Usa los repositorios y {@link Push}, nunca {@link Modificaciones}: esa
 * clase pregunta aquí si una modificación está vencida, y depender de ella
 * sería un ciclo.
 */
@Service
public class Recordatorios {

    private static final Logger LOG = LoggerFactory.getLogger(Recordatorios.class);

    private static final Set<EstadoModificacion> ABIERTAS = EnumSet.of(EstadoModificacion.NUEVA,
            EstadoModificacion.VISTA, EstadoModificacion.EN_CURSO);

    private final ModificacionRepository modificaciones;
    private final MensajeRepository mensajes;
    private final ModificacionDestinoRepository destinos;
    private final UsuarioRepository usuarios;
    private final ModeloRepository modelos;
    private final AvisoEnviadoRepository enviados;
    private final Push push;
    private final ModelosProperties props;
    private final Clock reloj;
    private final boolean activo;

    public Recordatorios(ModificacionRepository modificaciones, MensajeRepository mensajes,
                         ModificacionDestinoRepository destinos, UsuarioRepository usuarios, ModeloRepository modelos,
                         AvisoEnviadoRepository enviados, Push push, ModelosProperties props, Clock reloj,
                         @Value("${modelos.recordatorios.activo:true}") boolean activo) {
        this.modificaciones = modificaciones;
        this.mensajes = mensajes;
        this.destinos = destinos;
        this.usuarios = usuarios;
        this.modelos = modelos;
        this.enviados = enviados;
        this.push = push;
        this.props = props;
        this.reloj = reloj;
        this.activo = activo;
    }

    /**
     * La revisión de cada hora. Con {@code modelos.recordatorios.activo: false}
     * (el perfil de los tests) no hace nada. No lanza nunca: una excepción
     * aquí pararía las revisiones siguientes.
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void revisar() {
        if (!activo) {
            return;
        }
        try {
            int mandados = revisar(LocalDateTime.now(reloj));
            if (mandados > 0) {
                LOG.info("Recordatorios: {} mandados", mandados);
            }
        } catch (Throwable fallo) {
            LOG.warn("No se han podido revisar los recordatorios", fallo);
        }
    }

    /**
     * Manda los recordatorios que tocan a esa hora y devuelve cuántos ha
     * mandado (uno por modificación y regla, vaya a cuantas personas vaya).
     */
    public int revisar(LocalDateTime ahora) {
        List<Modificacion> abiertas = modificaciones.findByEstadoIn(ABIERTAS);
        if (abiertas.isEmpty()) {
            return 0;
        }
        Contexto contexto = contexto(abiertas);
        int mandados = 0;
        for (Modificacion m : abiertas) {
            for (Pendiente pendiente : pendientes(m, contexto.ultimoMensaje.get(m.getId()), contexto)) {
                if (!pendiente.toca(ahora) || contexto.repetido(m.getId(), pendiente, ahora)) {
                    continue;
                }
                try {
                    if (mandar(m, pendiente, ahora, contexto)) {
                        mandados++;
                    }
                } catch (RuntimeException fallo) {
                    LOG.warn("No se ha podido mandar el recordatorio {} de la modificación {}", pendiente.tipo(),
                            m.getId(), fallo);
                }
            }
        }
        return mandados;
    }

    /**
     * Si la modificación va tarde: una orden sin hacer, un aviso sin
     * prioridad o un aviso sin aplicar, pasado su plazo desde que se creó.
     * Mira solo la regla, no lo que ya se haya mandado: la marca «vencida»
     * sigue mientras siga tarde. Las preguntas sin contestar no cuentan.
     */
    public boolean vencida(Modificacion m, LocalDateTime ahora) {
        if (m == null || ahora == null) {
            return false;
        }
        Pendiente regla = reglaPrincipal(m);
        return regla != null && regla.toca(ahora);
    }

    /** Lo que se le podría recordar a esa modificación, toque ya o no. */
    private List<Pendiente> pendientes(Modificacion m, Mensaje ultimo, Contexto contexto) {
        List<Pendiente> lista = new ArrayList<>();
        Pendiente principal = reglaPrincipal(m);
        if (principal != null) {
            lista.add(principal);
        }
        Pendiente respuesta = respuestaPendiente(m, ultimo, contexto);
        if (respuesta != null) {
            lista.add(respuesta);
        }
        return lista;
    }

    /** La orden sin hacer, el aviso sin prioridad o el aviso sin aplicar; null si no hay o está cerrada. */
    private Pendiente reglaPrincipal(Modificacion m) {
        if (m.getEstado() == null || m.getEstado().cerrada() || m.getCreadaEn() == null) {
            return null;
        }
        ModelosProperties.Recordatorios reglas = props.recordatorios();
        if (reglas == null) {
            return null;
        }
        if (!m.esAviso()) {
            Prioridad prioridad = m.getPrioridad() == null ? Prioridad.NORMAL : m.getPrioridad();
            Integer dias = reglas.taller() == null || reglas.taller().diasPorPrioridad() == null ? null
                    : reglas.taller().diasPorPrioridad().get(prioridad.name());
            return Pendiente.de(AvisoEnviado.Tipo.RECORDATORIO, Rol.TALLER, Duration.ofDays(entero(dias)),
                    m.getCreadaEn());
        }
        if (reglas.despacho() == null) {
            return null;
        }
        if (m.getPrioridad() == null) {
            return Pendiente.de(AvisoEnviado.Tipo.SIN_PRIORIDAD, Rol.DESPACHO,
                    Duration.ofHours(reglas.despacho().sinPrioridadHoras()), m.getCreadaEn());
        }
        return Pendiente.de(AvisoEnviado.Tipo.RECORDATORIO, Rol.DESPACHO,
                Duration.ofDays(reglas.despacho().sinAplicarDias()), m.getCreadaEn());
    }

    /** La pregunta sin contestar de una abierta; null si no la hay. */
    private Pendiente respuestaPendiente(Modificacion m, Mensaje ultimo, Contexto contexto) {
        if (!m.isNecesitaRespuesta() || m.getEstado().cerrada() || ultimo == null || ultimo.getCreadoEn() == null
                || props.recordatorios() == null || props.recordatorios().despacho() == null) {
            return null;
        }
        Usuario autor = contexto.usuarios.get(ultimo.getAutorId());
        if (autor == null || autor.getRol() == null) {
            return null;
        }
        Rol para = autor.getRol().esDespacho() ? Rol.TALLER : Rol.DESPACHO;
        return Pendiente.de(AvisoEnviado.Tipo.RESPUESTA, para,
                Duration.ofHours(props.recordatorios().despacho().respuestaPendienteHoras()), ultimo.getCreadoEn());
    }

    /** Lo manda al rol y lo apunta, una fila por destinatario; false si ese rol no tiene a nadie. */
    private boolean mandar(Modificacion m, Pendiente pendiente, LocalDateTime ahora, Contexto contexto) {
        Set<Long> quienes = push.destinatarios(pendiente.para());
        if (quienes.isEmpty()) {
            return false;
        }
        push.avisarA(pendiente.para(), aviso(m, pendiente, ahora, contexto));
        List<AvisoEnviado> filas = quienes.stream()
                .map(id -> new AvisoEnviado(m.getId(), id, pendiente.tipo(), ahora))
                .toList();
        enviados.saveAll(filas);
        return true;
    }

    /** Lo que dice el push de cada regla. */
    private Push.Aviso aviso(Modificacion m, Pendiente pendiente, LocalDateTime ahora, Contexto contexto) {
        Modelo modelo = contexto.modelos.get(m.getModeloId());
        String referencia = modelo == null ? "" : Objects.toString(modelo.nombreVisible(), "");
        String queEs = queEs(m);
        String tag = "recordatorio-" + m.getId();
        String bandeja = "/app/bandeja/" + m.getId();
        String ordenes = "/app/ordenes/" + m.getId();
        return switch (pendiente.tipo()) {
            case RECORDATORIO -> {
                if (!m.esAviso()) {
                    long dias = Duration.between(m.getCreadaEn(), ahora).toDays();
                    String hace = "Desde hace " + dias + (dias == 1 ? " día" : " días");
                    yield new Push.Aviso("Pendiente: " + queEs, juntar(hace, referencia), ordenes, m.getId(), tag);
                }
                yield new Push.Aviso("Por aplicar: " + queEs,
                        juntar(destinosPendientes(contexto.destinos.getOrDefault(m.getId(), List.of())), referencia),
                        bandeja, m.getId(), tag);
            }
            case SIN_PRIORIDAD -> new Push.Aviso("Aviso sin prioridad: " + queEs,
                    juntar(referencia, "de " + nombre(contexto.usuarios.get(m.getCreadaPor()))), bandeja, m.getId(),
                    tag);
            case RESPUESTA -> {
                Mensaje ultimo = contexto.ultimoMensaje.get(m.getId());
                String autor = nombre(ultimo == null ? null : contexto.usuarios.get(ultimo.getAutorId()));
                // El taller no tiene pantalla de un aviso suelto: sus avisos se leen en la lista.
                String url = pendiente.para() == Rol.DESPACHO ? bandeja : (m.esAviso() ? "/app/avisos" : ordenes);
                yield new Push.Aviso("Respuesta pendiente: " + queEs,
                        juntar(autor + " espera respuesta", referencia), url, m.getId(), tag);
            }
        };
    }

    /** «ICSUITE / Excel de escandallos pendientes»: los destinos que aplican y no están hechos. */
    private static String destinosPendientes(List<ModificacionDestino> suyos) {
        String faltan = suyos.stream()
                .filter(d -> d.isAplica() && !d.hecho())
                .map(d -> TextosDestino.texto(d.getDestino()))
                .collect(Collectors.joining(" / "));
        return faltan.isEmpty() ? "Pendiente de aplicar" : faltan + " pendientes";
    }

    /** El título; sin él (un aviso al que nadie ha puesto título), el tipo de cambio. */
    private String queEs(Modificacion m) {
        if (m.getTitulo() != null && !m.getTitulo().isBlank()) {
            return m.getTitulo().strip();
        }
        if (m.getTipoCambio() == null) {
            return m.esAviso() ? "aviso del taller" : "orden";
        }
        return props.tipoCambio(m.getTipoCambio()).map(ModelosProperties.TipoCambio::texto).orElse(m.getTipoCambio());
    }

    private static String nombre(Usuario usuario) {
        return usuario == null ? "—" : usuario.getNombre();
    }

    private static String juntar(String primero, String segundo) {
        if (segundo == null || segundo.isBlank()) {
            return primero;
        }
        return primero == null || primero.isBlank() ? segundo : primero + " · " + segundo;
    }

    private static int entero(Integer valor) {
        return valor == null ? 0 : valor;
    }

    /**
     * Una regla para una modificación: qué tipo, a quién, cada cuánto y desde
     * cuándo se cuenta. Toca a partir de {@code desde + plazo}.
     */
    private record Pendiente(AvisoEnviado.Tipo tipo, Rol para, Duration plazo, LocalDateTime desde) {

        /** null si el plazo no es positivo: la regla está apagada. */
        static Pendiente de(AvisoEnviado.Tipo tipo, Rol para, Duration plazo, LocalDateTime desde) {
            return plazo.isZero() || plazo.isNegative() ? null : new Pendiente(tipo, para, plazo, desde);
        }

        boolean toca(LocalDateTime ahora) {
            return !desde.plus(plazo).isAfter(ahora);
        }
    }

    /** Lo que hace falta para las abiertas, con una consulta por tabla. */
    private Contexto contexto(List<Modificacion> abiertas) {
        List<Long> ids = abiertas.stream().map(Modificacion::getId).toList();
        Map<Long, Modelo> porModelo = new HashMap<>();
        modelos.findAllById(abiertas.stream().map(Modificacion::getModeloId).collect(Collectors.toSet()))
                .forEach(modelo -> porModelo.put(modelo.getId(), modelo));

        List<Long> conPregunta = abiertas.stream().filter(Modificacion::isNecesitaRespuesta)
                .map(Modificacion::getId).toList();
        Map<Long, Mensaje> ultimos = new HashMap<>();
        if (!conPregunta.isEmpty()) {
            mensajes.findByModificacionIdInOrderByCreadoEnAscIdAsc(conPregunta)
                    .forEach(mensaje -> ultimos.put(mensaje.getModificacionId(), mensaje));
        }

        List<Long> avisos = abiertas.stream().filter(Modificacion::esAviso).map(Modificacion::getId).toList();
        Map<Long, List<ModificacionDestino>> porDestino = new HashMap<>();
        if (!avisos.isEmpty()) {
            destinos.findByModificacionIdInOrderByIdAsc(avisos).forEach(d -> porDestino
                    .computeIfAbsent(d.getModificacionId(), clave -> new ArrayList<>()).add(d));
        }

        Set<Long> usuarioIds = new HashSet<>();
        abiertas.forEach(m -> usuarioIds.add(m.getCreadaPor()));
        ultimos.values().forEach(mensaje -> usuarioIds.add(mensaje.getAutorId()));
        usuarioIds.remove(null);
        Map<Long, Usuario> porUsuario = new HashMap<>();
        if (!usuarioIds.isEmpty()) {
            usuarios.findAllById(usuarioIds).forEach(usuario -> porUsuario.put(usuario.getId(), usuario));
        }

        Map<String, LocalDateTime> ultimosEnvios = new HashMap<>();
        for (AvisoEnviadoRepository.Ultimo ultimo : enviados.ultimosDe(ids)) {
            ultimosEnvios.put(clave(ultimo.getModificacionId(), ultimo.getTipo()), ultimo.getEnviadoEn());
        }
        return new Contexto(porModelo, ultimos, porDestino, porUsuario, ultimosEnvios);
    }

    private static String clave(Long modificacionId, AvisoEnviado.Tipo tipo) {
        return modificacionId + "/" + tipo;
    }

    private record Contexto(Map<Long, Modelo> modelos, Map<Long, Mensaje> ultimoMensaje,
                            Map<Long, List<ModificacionDestino>> destinos, Map<Long, Usuario> usuarios,
                            Map<String, LocalDateTime> ultimosEnvios) {

        /** Ya se mandó uno de ese tipo dentro del plazo. */
        boolean repetido(Long modificacionId, Pendiente pendiente, LocalDateTime ahora) {
            LocalDateTime ultimo = ultimosEnvios.get(clave(modificacionId, pendiente.tipo()));
            return ultimo != null && ultimo.isAfter(ahora.minus(pendiente.plazo()));
        }
    }
}
