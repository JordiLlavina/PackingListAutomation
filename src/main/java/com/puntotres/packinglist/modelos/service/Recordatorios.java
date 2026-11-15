package com.puntotres.packinglist.modelos.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
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
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionDestino;
import com.puntotres.packinglist.modelos.persistence.ModificacionDestinoRepository;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;

/**
 * Los recordatorios anti-olvido: cada hora mira lo que sigue abierto y manda
 * un push a quien tiene que moverlo. Las reglas y sus plazos están en
 * {@code modelos.recordatorios} de application.yml:
 *
 * <ul>
 * <li>La cola del taller ({@link ColaTaller}), una vez al día: al TALLER, a
 * partir de la hora {@code resumen-cola-hora}, cuántas órdenes tiene y cuál es
 * la siguiente. Nada caduca: las órdenes no urgentes no tienen recordatorio
 * propio, las recuerda este resumen.</li>
 * <li>Una orden urgente sin hacer: al TALLER, a los {@code urgente-dias} de
 * crearla y otra vez cada tantos días.</li>
 * <li>Un aviso del taller sin aplicar del todo: al DESPACHO, cada
 * {@code sin-aplicar-dias} desde que se creó, diciendo qué destinos faltan.</li>
 * </ul>
 *
 * Lo cerrado (HECHA o DESCARTADA) no insiste nunca. Cada envío se apunta en {@code aviso_enviado} y no se repite
 * mientras el último del mismo tipo de esa modificación sea más reciente que
 * el plazo. Un plazo de 0 o sin configurar apaga su regla: avisar cada hora
 * sería ruido, no un recordatorio.
 *
 * Usa los repositorios, {@link ColaTaller} y {@link Push}, nunca
 * {@link Modificaciones}, que no necesita nada de aquí.
 */
@Service
public class Recordatorios {

    private static final Logger LOG = LoggerFactory.getLogger(Recordatorios.class);

    private static final Set<EstadoModificacion> ABIERTAS = EnumSet.of(EstadoModificacion.NUEVA,
            EstadoModificacion.VISTA, EstadoModificacion.EN_CURSO);

    private final ModificacionRepository modificaciones;
    private final ModificacionDestinoRepository destinos;
    private final ModeloRepository modelos;
    private final AvisoEnviadoRepository enviados;
    private final Push push;
    private final ColaTaller cola;
    private final ModelosProperties props;
    private final Clock reloj;
    private final boolean activo;

    public Recordatorios(ModificacionRepository modificaciones, ModificacionDestinoRepository destinos,
                         ModeloRepository modelos,
                         AvisoEnviadoRepository enviados, Push push, ColaTaller cola, ModelosProperties props,
                         Clock reloj,
                         @Value("${modelos.recordatorios.activo:true}") boolean activo) {
        this.modificaciones = modificaciones;
        this.destinos = destinos;
        this.modelos = modelos;
        this.enviados = enviados;
        this.push = push;
        this.cola = cola;
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
     * mandado (uno por modificación y regla, vaya a cuantas personas vaya, y
     * uno por el resumen de la cola).
     */
    public int revisar(LocalDateTime ahora) {
        int mandados = 0;
        ModelosProperties.Recordatorios reglas = props.recordatorios();
        Integer hora = reglas == null || reglas.taller() == null ? null : reglas.taller().resumenColaHora();
        if (hora != null && hora >= 0) {
            try {
                if (resumenDeLaCola(ahora, hora)) {
                    mandados++;
                }
            } catch (RuntimeException fallo) {
                LOG.warn("No se ha podido mandar el resumen de la cola", fallo);
            }
        }
        List<Modificacion> abiertas = modificaciones.findByEstadoIn(ABIERTAS);
        if (abiertas.isEmpty()) {
            return mandados;
        }
        Contexto contexto = contexto(abiertas);
        for (Modificacion m : abiertas) {
            for (Pendiente pendiente : pendientes(m)) {
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
     * El resumen del día de la cola al taller: si ya es la hora (o más) y hoy
     * no se ha mandado, «Tienes N órdenes por hacer» con la siguiente. Sin
     * órdenes en la cola no se manda nada. Se apunta en {@code aviso_enviado}
     * con la orden que iba primera; de un día a otro se mira por la fecha, no
     * por 24 horas, para que salga cada mañana a la misma hora. Los sábados y
     * los domingos no sale (decisión del usuario): el taller no trabaja.
     *
     * @return si se ha mandado
     */
    boolean resumenDeLaCola(LocalDateTime ahora, int hora) {
        if (ahora.getHour() < hora || ahora.getDayOfWeek() == DayOfWeek.SATURDAY
                || ahora.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalDateTime hoy = ahora.toLocalDate().atStartOfDay();
        if (enviados.existsByTipoAndEnviadoEnGreaterThanEqualAndEnviadoEnLessThan(AvisoEnviado.Tipo.RESUMEN_COLA, hoy,
                hoy.plusDays(1))) {
            return false;
        }
        List<Modificacion> laCola = cola.cola();
        if (laCola.isEmpty()) {
            return false;
        }
        Set<Long> quienes = push.destinatarios(Rol.TALLER);
        if (quienes.isEmpty()) {
            return false;
        }
        Modificacion primera = laCola.get(0);
        Modelo modelo = modelos.findById(primera.getModeloId()).orElse(null);
        String referencia = modelo == null ? "" : Objects.toString(modelo.nombreVisible(), "");
        int n = laCola.size();
        String titulo = "Tienes " + n + (n == 1 ? " orden por hacer" : " órdenes por hacer");
        String cuerpo = "La siguiente: " + juntar(referencia, queEs(primera));
        push.avisarA(Rol.TALLER, new Push.Aviso(titulo, cuerpo, "/app/ordenes", primera.getId(), "resumen-cola"));
        enviados.saveAll(quienes.stream()
                .map(id -> new AvisoEnviado(primera.getId(), id, AvisoEnviado.Tipo.RESUMEN_COLA, ahora))
                .toList());
        return true;
    }

    /** Lo que se le podría recordar a esa modificación, toque ya o no. */
    private List<Pendiente> pendientes(Modificacion m) {
        Pendiente principal = reglaPrincipal(m);
        return principal == null ? List.of() : List.of(principal);
    }

    /** La orden urgente sin hacer o el aviso sin aplicar; null si no hay o está cerrada. */
    private Pendiente reglaPrincipal(Modificacion m) {
        if (m.getEstado() == null || m.getEstado().cerrada() || m.getCreadaEn() == null) {
            return null;
        }
        ModelosProperties.Recordatorios reglas = props.recordatorios();
        if (reglas == null) {
            return null;
        }
        if (!m.esAviso()) {
            // Las no urgentes ya las recuerda el resumen diario de la cola.
            if (!m.isUrgente() || reglas.taller() == null) {
                return null;
            }
            return Pendiente.de(AvisoEnviado.Tipo.RECORDATORIO, Rol.TALLER,
                    Duration.ofDays(reglas.taller().urgenteDias()), m.getCreadaEn());
        }
        if (reglas.despacho() == null) {
            return null;
        }
        return Pendiente.de(AvisoEnviado.Tipo.RECORDATORIO, Rol.DESPACHO,
                Duration.ofDays(reglas.despacho().sinAplicarDias()), m.getCreadaEn());
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
                    Integer puesto = contexto.posiciones.get(m.getId());
                    String enLaCola = puesto == null ? null : "Nº " + puesto + " de la cola";
                    yield new Push.Aviso("Urgente: " + queEs, juntar(enLaCola, referencia), ordenes, m.getId(), tag);
                }
                yield new Push.Aviso("Por aplicar: " + queEs,
                        juntar(destinosPendientes(contexto.destinos.getOrDefault(m.getId(), List.of())), referencia),
                        bandeja, m.getId(), tag);
            }
            // Ya no hay regla que los mande (ver AvisoEnviado.Tipo); el switch tiene que cubrirlos.
            case SIN_PRIORIDAD, RESPUESTA, RESUMEN_COLA -> throw new IllegalStateException("No es un recordatorio de una "
                    + "modificación: " + pendiente.tipo());
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
        String texto = props.textoTiposCambio(m.getTipoCambio());
        if (texto == null) {
            return m.esAviso() ? "aviso del taller" : "orden";
        }
        return texto;
    }

    private static String juntar(String primero, String segundo) {
        if (segundo == null || segundo.isBlank()) {
            return primero;
        }
        return primero == null || primero.isBlank() ? segundo : primero + " · " + segundo;
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

        List<Long> avisos = abiertas.stream().filter(Modificacion::esAviso).map(Modificacion::getId).toList();
        Map<Long, List<ModificacionDestino>> porDestino = new HashMap<>();
        if (!avisos.isEmpty()) {
            destinos.findByModificacionIdInOrderByIdAsc(avisos).forEach(d -> porDestino
                    .computeIfAbsent(d.getModificacionId(), clave -> new ArrayList<>()).add(d));
        }

        Map<String, LocalDateTime> ultimosEnvios = new HashMap<>();
        for (AvisoEnviadoRepository.Ultimo ultimo : enviados.ultimosDe(ids)) {
            ultimosEnvios.put(clave(ultimo.getModificacionId(), ultimo.getTipo()), ultimo.getEnviadoEn());
        }
        // El puesto en la cola solo lo dice el recordatorio de una urgente.
        Map<Long, Integer> posiciones = abiertas.stream().anyMatch(m -> !m.esAviso() && m.isUrgente())
                ? cola.posiciones() : Map.of();
        return new Contexto(porModelo, porDestino, ultimosEnvios, posiciones);
    }

    private static String clave(Long modificacionId, AvisoEnviado.Tipo tipo) {
        return modificacionId + "/" + tipo;
    }

    private record Contexto(Map<Long, Modelo> modelos, Map<Long, List<ModificacionDestino>> destinos,
                            Map<String, LocalDateTime> ultimosEnvios, Map<Long, Integer> posiciones) {

        /** Ya se mandó uno de ese tipo dentro del plazo. */
        boolean repetido(Long modificacionId, Pendiente pendiente, LocalDateTime ahora) {
            LocalDateTime ultimo = ultimosEnvios.get(clave(modificacionId, pendiente.tipo()));
            return ultimo != null && ultimo.isAfter(ahora.minus(pendiente.plazo()));
        }
    }
}
