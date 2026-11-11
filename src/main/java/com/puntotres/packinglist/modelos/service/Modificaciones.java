package com.puntotres.packinglist.modelos.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.modelos.ModelosProperties;
import com.puntotres.packinglist.modelos.ModelosProperties.TipoCambio;
import com.puntotres.packinglist.modelos.api.dto.AdjuntoVista;
import com.puntotres.packinglist.modelos.api.dto.DestinoEstado;
import com.puntotres.packinglist.modelos.api.dto.Evento;
import com.puntotres.packinglist.modelos.api.dto.MensajeVista;
import com.puntotres.packinglist.modelos.api.dto.ModificacionDetalle;
import com.puntotres.packinglist.modelos.api.dto.ModificacionResumen;
import com.puntotres.packinglist.modelos.api.dto.ResumenBandeja;
import com.puntotres.packinglist.modelos.persistence.Adjunto;
import com.puntotres.packinglist.modelos.persistence.DestinoAplicacion;
import com.puntotres.packinglist.modelos.persistence.Direccion;
import com.puntotres.packinglist.modelos.persistence.EstadoModificacion;
import com.puntotres.packinglist.modelos.persistence.Mensaje;
import com.puntotres.packinglist.modelos.persistence.MensajeRepository;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionDestino;
import com.puntotres.packinglist.modelos.persistence.ModificacionDestinoRepository;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.ResumenAdjunto;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.TipoAdjunto;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;

/**
 * Las modificaciones de los modelos en los dos sentidos: los avisos del
 * taller al despacho y las órdenes del despacho al taller, con su ciclo
 * (NUEVA → VISTA → EN_CURSO → HECHA, o DESCARTADA), sus destinos, sus
 * mensajes y la bandeja de cada lado.
 *
 * Reglas del ciclo que no se ven en las firmas:
 * <ul>
 * <li>Un aviso necesita al menos una foto o una nota de voz, y nace con un
 * destino por cada uno de {@code modelos.destinos-aplicacion}, todos
 * aplicando. Una orden necesita título, no tiene destinos y entra en la cola
 * del taller ({@link ColaTaller}): al final o delante de la que se diga.</li>
 * <li>Solo una orden abierta se marca urgente, y marcarla la sube la primera
 * de la cola; quitarle la marca no la mueve.</li>
 * <li>Un aviso pasa solo a EN_CURSO al marcar el primer destino hecho y a
 * HECHA cuando están hechos todos los que aplican (también si ya no aplica
 * ninguno). Desmarcar un destino de un aviso HECHO lo devuelve a EN_CURSO.</li>
 * <li>Una DESCARTADA no cambia de destinos, ni se marca
 * hecha, pero sigue admitiendo mensajes: se puede explicar por qué se
 * descartó.</li>
 * <li>Cada respuesta enciende {@code necesita_respuesta} y deja la pelota en
 * el otro lado ({@link #ladoQueDebeResponder}). Abrir la modificación no la
 * apaga: la pelota cambia de lado cuando ese lado contesta.</li>
 * </ul>
 *
 * El modelo se lee del catálogo ({@link CatalogoModelos}, {@link FotosCatalogo})
 * y no de {@link Modelos}: Modelos recibe esta clase como fuente de su línea de
 * tiempo, y depender de ella sería un ciclo.
 *
 * Las listas cargan sus piezas (modelos, fotos, mensajes, adjuntos, destinos,
 * usuarios) con una consulta cada una sobre todos los candidatos, nunca una
 * por modificación.
 */
@Service
public class Modificaciones implements Modelos.FuenteEventos {

    /** Lo que sale en la línea de tiempo cuando no hay nadie a quien atribuirlo. */
    private static final String NADIE = "—";

    /** Lo que cabe en {@code modificacion.titulo}. */
    private static final int LARGO_TITULO = 200;

    /** Las que todavía tienen algo por hacer. */
    private static final Set<EstadoModificacion> ABIERTAS = EnumSet.of(EstadoModificacion.NUEVA,
            EstadoModificacion.VISTA, EstadoModificacion.EN_CURSO);

    private final ModificacionRepository repositorio;
    private final ModificacionDestinoRepository destinos;
    private final MensajeRepository mensajes;
    private final Adjuntos adjuntos;
    private final UsuarioRepository usuarios;
    private final ModeloRepository modelos;
    private final CatalogoModelos catalogo;
    private final FotosCatalogo fotos;
    private final ModelosProperties props;
    private final ApplicationEventPublisher eventos;
    private final ColaTaller cola;

    public Modificaciones(ModificacionRepository repositorio, ModificacionDestinoRepository destinos,
                          MensajeRepository mensajes, Adjuntos adjuntos, UsuarioRepository usuarios,
                          ModeloRepository modelos, CatalogoModelos catalogo, FotosCatalogo fotos,
                          ModelosProperties props, ApplicationEventPublisher eventos, ColaTaller cola) {
        this.repositorio = repositorio;
        this.destinos = destinos;
        this.mensajes = mensajes;
        this.adjuntos = adjuntos;
        this.usuarios = usuarios;
        this.modelos = modelos;
        this.catalogo = catalogo;
        this.fotos = fotos;
        this.props = props;
        this.eventos = eventos;
        this.cola = cola;
    }

    /** Lo que manda el taller: el modelo, el tipo de cambio (clave del yml) y sus fotos y notas de voz. */
    public record AvisoTaller(Long modeloId, String tipoCambio, List<AdjuntoNuevo> adjuntos) {
    }

    /**
     * Lo que manda el despacho; el tipo de cambio es opcional, el título no.
     *
     * @param urgente marcada urgente desde que nace
     * @param antesDe la orden de la cola delante de la que va; null, al final
     */
    public record OrdenDespacho(Long modeloId, String tipoCambio, String titulo, String texto, boolean urgente,
                                Long antesDe, List<AdjuntoNuevo> adjuntos) {

        /** Ni urgente, y al final de la cola. */
        public OrdenDespacho(Long modeloId, String tipoCambio, String titulo, String texto,
                             List<AdjuntoNuevo> adjuntos) {
            this(modeloId, tipoCambio, titulo, texto, false, null, adjuntos);
        }
    }

    /**
     * Los filtros de la lista; un campo nulo (o un conjunto vacío) no filtra.
     *
     * @param cliente la clave del cliente del modelo, sin mirar mayúsculas
     * @param texto   busca, sin mirar mayúsculas, en el título, el texto de los
     *                mensajes, las transcripciones y los resúmenes de la IA
     */
    public record Filtro(Direccion direccion, Set<EstadoModificacion> estados, Long modeloId, String cliente,
                         String texto) {
    }

    @Transactional
    public Modificacion crearAvisoDeTaller(AvisoTaller aviso, UsuarioActual quien) {
        if (aviso == null) {
            throw new IllegalArgumentException("Falta el aviso");
        }
        modeloQueExiste(aviso.modeloId());
        String tipoCambio = tipoCambioDelYml(aviso.tipoCambio(), true);
        List<AdjuntoNuevo> nuevos = sinNulos(aviso.adjuntos());
        if (nuevos.stream().noneMatch(a -> a.tipo() == TipoAdjunto.FOTO || a.tipo() == TipoAdjunto.AUDIO)) {
            throw new IllegalArgumentException("Hace falta una foto o una nota de voz");
        }
        LocalDateTime ahora = LocalDateTime.now();
        Modificacion modificacion = repositorio.save(new Modificacion(aviso.modeloId(), Direccion.TALLER_A_DESPACHO,
                tipoCambio, null, idDe(quien), ahora));
        for (DestinoAplicacion destino : destinosDelYml()) {
            destinos.save(new ModificacionDestino(modificacion.getId(), destino));
        }
        guardarMensaje(modificacion.getId(), "", nuevos, quien, ahora);
        // El push al despacho sale cuando se confirma la transacción (Push).
        eventos.publishEvent(new ModificacionCreada(modificacion.getId(), Direccion.TALLER_A_DESPACHO, idDe(quien)));
        return modificacion;
    }

    @Transactional
    public Modificacion crearOrdenParaTaller(OrdenDespacho orden, UsuarioActual quien) {
        if (orden == null) {
            throw new IllegalArgumentException("Falta la orden");
        }
        modeloQueExiste(orden.modeloId());
        if (vacio(orden.titulo())) {
            throw new IllegalArgumentException("Falta el título");
        }
        String titulo = orden.titulo().trim();
        if (titulo.length() > LARGO_TITULO) {
            throw new IllegalArgumentException("El título no puede pasar de " + LARGO_TITULO + " caracteres");
        }
        String tipoCambio = tipoCambioDelYml(orden.tipoCambio(), false);
        LocalDateTime ahora = LocalDateTime.now();
        Modificacion nueva = new Modificacion(orden.modeloId(), Direccion.DESPACHO_A_TALLER, tipoCambio, titulo,
                idDe(quien), ahora);
        nueva.marcarUrgente(orden.urgente());
        Modificacion modificacion = repositorio.save(nueva);
        cola.colocar(modificacion, orden.antesDe());
        guardarMensaje(modificacion.getId(), vacio(orden.texto()) ? "" : orden.texto().trim(),
                sinNulos(orden.adjuntos()), quien, ahora);
        // El push al taller sale cuando se confirma la transacción (Push).
        eventos.publishEvent(new ModificacionCreada(modificacion.getId(), Direccion.DESPACHO_A_TALLER, idDe(quien)));
        return modificacion;
    }

    /**
     * Las que pasan el filtro, en el orden de la bandeja: las órdenes abiertas
     * en el orden de la cola del taller; los avisos, los NUEVOS primero y a
     * igualdad el más antiguo. Las cerradas (HECHA, DESCARTADA) van al final,
     * salvo que el filtro pida estados concretos: entonces se ordenan como las
     * demás (por antigüedad).
     */
    @Transactional(readOnly = true)
    public List<ModificacionResumen> listar(Filtro filtro, UsuarioActual quien) {
        Filtro f = filtro == null ? new Filtro(null, null, null, null, null) : filtro;
        boolean conEstados = f.estados() != null && !f.estados().isEmpty();
        List<Modificacion> candidatos;
        if (f.direccion() != null) {
            candidatos = conEstados ? repositorio.findByDireccionAndEstadoIn(f.direccion(), f.estados())
                    : repositorio.findByDireccion(f.direccion());
        } else {
            candidatos = conEstados ? repositorio.findByEstadoIn(f.estados()) : repositorio.findAll();
        }
        List<Modificacion> filtrados = candidatos.stream()
                .filter(m -> f.modeloId() == null || f.modeloId().equals(m.getModeloId()))
                .toList();
        Contexto contexto = contexto(filtrados);
        String cliente = vacio(f.cliente()) ? null : f.cliente().trim();
        String texto = vacio(f.texto()) ? null : f.texto().trim().toLowerCase(Locale.ROOT);
        return filtrados.stream()
                .filter(m -> cliente == null || cliente.equalsIgnoreCase(contexto.clienteDe(m)))
                .filter(m -> texto == null || contexto.contiene(m, texto))
                .sorted(ordenDeBandeja(!conEstados, contexto.posiciones))
                .map(contexto::resumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public ModificacionDetalle detalle(Long id) {
        Modificacion modificacion = modificacionQueExiste(id);
        Contexto contexto = contexto(List.of(modificacion));
        List<MensajeVista> vistas = contexto.mensajesDe(modificacion).stream()
                .map(mensaje -> {
                    Usuario autor = contexto.usuarios.get(mensaje.getAutorId());
                    List<AdjuntoVista> suyos = contexto.adjuntosDe(mensaje).stream().map(AdjuntoVista::de).toList();
                    return new MensajeVista(mensaje.getId(), autor == null ? null : autor.getNombre(),
                            autor == null ? null : autor.getRol(), Objects.toString(mensaje.getTexto(), ""),
                            mensaje.getCreadoEn(), suyos);
                })
                .toList();
        return new ModificacionDetalle(contexto.resumen(modificacion), modificacion.getMotivoDescarte(), vistas);
    }

    /**
     * La da por vista si es NUEVA y la abre quien la recibe: el taller en las
     * órdenes, el despacho en los avisos. Si la abre quien la mandó, o ya no
     * es NUEVA, no cambia nada.
     */
    @Transactional
    public void marcarVista(Long id, UsuarioActual quien) {
        Modificacion modificacion = modificacionQueExiste(id);
        if (modificacion.getEstado() == EstadoModificacion.NUEVA && esDestinatario(modificacion, quien)) {
            modificacion.marcarVista(LocalDateTime.now());
        }
    }

    /**
     * Marca o quita la marca de urgente de una orden abierta para el taller.
     * Marcarla la sube la primera de la cola (luego se puede mover como
     * cualquier otra); quitarla la deja donde está.
     */
    @Transactional
    public void marcarUrgente(Long id, boolean urgente, UsuarioActual quien) {
        exigirDespacho(quien);
        Modificacion modificacion = modificacionQueExiste(id);
        if (modificacion.esAviso()) {
            throw new IllegalArgumentException("Solo una orden para el taller puede ser urgente");
        }
        if (!ColaTaller.enLaCola(modificacion)) {
            throw new IllegalArgumentException("La orden ya está cerrada");
        }
        boolean antes = modificacion.isUrgente();
        modificacion.marcarUrgente(urgente);
        if (urgente && !antes) {
            cola.alPrincipio(modificacion);
        }
    }

    /** La cola del taller, de la primera a la última, como la ve la bandeja. */
    @Transactional(readOnly = true)
    public List<ModificacionResumen> cola() {
        List<Modificacion> lista = cola.cola();
        Contexto contexto = contexto(lista);
        return lista.stream().map(contexto::resumen).toList();
    }

    /** Deja la cola en ese orden (tiene que ser la cola entera: {@link ColaTaller#reordenar}). */
    @Transactional
    public void reordenarCola(List<Long> ids, UsuarioActual quien) {
        exigirDespacho(quien);
        cola.reordenar(ids);
    }

    /**
     * Si el aviso aplica en ese destino y si ya está hecho ahí (un destino
     * que no aplica no puede estar hecho). Mueve el estado solo: EN_CURSO al
     * primer destino hecho, HECHA cuando lo están todos los que aplican (o no
     * aplica ninguno), y de HECHA a EN_CURSO si se desmarca uno.
     */
    @Transactional
    public void marcarDestino(Long id, DestinoAplicacion destino, boolean aplica, boolean hecho,
                              UsuarioActual quien) {
        exigirDespacho(quien);
        Modificacion modificacion = modificacionQueExiste(id);
        if (!modificacion.esAviso()) {
            throw new IllegalArgumentException("Una orden para el taller no tiene destinos");
        }
        if (destino == null) {
            throw new IllegalArgumentException("Falta el destino");
        }
        exigirNoDescartada(modificacion);
        List<ModificacionDestino> suyos = destinos.findByModificacionIdOrderByIdAsc(id);
        ModificacionDestino elDestino = suyos.stream()
                .filter(d -> d.getDestino() == destino)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Esta modificación no tiene el destino "
                        + TextosDestino.texto(destino)));
        LocalDateTime ahora = LocalDateTime.now();
        elDestino.marcar(aplica, hecho, quien.id(), ahora);

        boolean todosHechos = suyos.stream().filter(ModificacionDestino::isAplica).allMatch(ModificacionDestino::hecho);
        boolean algunoHecho = suyos.stream().anyMatch(ModificacionDestino::hecho);
        if (todosHechos) {
            if (modificacion.getEstado() != EstadoModificacion.HECHA) {
                modificacion.marcarVista(ahora);
                modificacion.marcarHecha(quien.id(), ahora);
            }
        } else if (algunoHecho || modificacion.getEstado() == EstadoModificacion.HECHA) {
            modificacion.marcarVista(ahora);
            modificacion.pasarAEnCurso();
        }
    }

    /**
     * La da por hecha. Una orden la puede dar por hecha el taller (lo normal)
     * o el despacho; un aviso, solo el despacho, y es un atajo que marca
     * hechos todos los destinos que aplican. Una que ya estaba hecha se queda
     * como estaba (un segundo toque no cambia quién ni cuándo).
     */
    @Transactional
    public void marcarHecha(Long id, UsuarioActual quien) {
        Modificacion modificacion = modificacionQueExiste(id);
        if (modificacion.esAviso()) {
            exigirDespacho(quien);
        }
        exigirNoDescartada(modificacion);
        if (modificacion.getEstado() == EstadoModificacion.HECHA) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (modificacion.esAviso()) {
            for (ModificacionDestino destino : destinos.findByModificacionIdOrderByIdAsc(id)) {
                if (destino.isAplica()) {
                    destino.marcar(true, true, idDe(quien), ahora);
                }
            }
        }
        if (esDestinatario(modificacion, quien)) {
            modificacion.marcarVista(ahora);
        }
        modificacion.marcarHecha(idDe(quien), ahora);
    }

    /**
     * DESCARTADA con su motivo, que es obligatorio; no se borra nada. Una que
     * ya está cerrada (HECHA o DESCARTADA) no se descarta: volver a hacerlo
     * cambiaría en silencio el motivo, y una hecha ya no tiene nada que
     * descartar.
     */
    @Transactional
    public void descartar(Long id, String motivo, UsuarioActual quien) {
        exigirDespacho(quien);
        Modificacion modificacion = modificacionQueExiste(id);
        if (modificacion.getEstado() == EstadoModificacion.DESCARTADA) {
            throw new IllegalArgumentException("La modificación ya está descartada");
        }
        if (modificacion.getEstado() == EstadoModificacion.HECHA) {
            throw new IllegalArgumentException("La modificación ya está hecha");
        }
        if (vacio(motivo)) {
            throw new IllegalArgumentException("Hace falta el motivo");
        }
        modificacion.descartar(motivo.trim());
    }

    /**
     * El título que pone o corrige el despacho (el de un aviso lo pone la IA
     * al resumir la nota de voz, y se corrige aquí): deja de ser de la IA.
     */
    @Transactional
    public void cambiarTitulo(Long id, String titulo, UsuarioActual quien) {
        exigirDespacho(quien);
        Modificacion modificacion = modificacionQueExiste(id);
        if (vacio(titulo)) {
            throw new IllegalArgumentException("Falta el título");
        }
        String limpio = titulo.trim();
        if (limpio.length() > LARGO_TITULO) {
            throw new IllegalArgumentException("El título no puede pasar de " + LARGO_TITULO + " caracteres");
        }
        modificacion.cambiarTitulo(limpio);
    }

    /** Un mensaje más, también en una descartada; deja la pelota en el otro lado. */
    @Transactional
    public Mensaje responder(Long id, String texto, List<AdjuntoNuevo> adjuntosNuevos, UsuarioActual quien) {
        Modificacion modificacion = modificacionQueExiste(id);
        List<AdjuntoNuevo> nuevos = sinNulos(adjuntosNuevos);
        String limpio = vacio(texto) ? "" : texto.trim();
        if (limpio.isEmpty() && nuevos.isEmpty()) {
            throw new IllegalArgumentException("Escribe algo o adjunta una nota de voz o una foto");
        }
        Mensaje mensaje = guardarMensaje(id, limpio, nuevos, quien, LocalDateTime.now());
        modificacion.pedirRespuesta();
        // El push al otro lado sale cuando se confirma la transacción (Push), también en una cerrada.
        eventos.publishEvent(new MensajeRespondido(id, quien == null ? null : quien.rol()));
        return mensaje;
    }

    /**
     * Copia la foto de un mensaje a la galería del modelo de su modificación
     * y devuelve todas las fotos del modelo. La misma foto pasada dos veces
     * no se duplica; otra que se llame igual (las de la cámara del móvil se
     * llaman casi todas {@code image.jpg}) entra con {@code " (2)"}, porque
     * {@link FotosCatalogo#guardar(Long, String, byte[])} con un nombre que ya
     * está devuelve la que había y no guarda nada.
     */
    @Transactional
    public List<ResumenFoto> fotoAGaleria(Long adjuntoId, UsuarioActual quien) {
        exigirDespacho(quien);
        Adjunto adjunto = adjuntos.conBytes(adjuntoId)
                .orElseThrow(() -> new NoSuchElementException("No hay ningún adjunto con id " + adjuntoId));
        if (adjunto.getTipo() != TipoAdjunto.FOTO) {
            throw new IllegalArgumentException("Solo se puede pasar una foto a la galería");
        }
        Long modificacionId = mensajes.findById(adjunto.getMensajeId()).map(Mensaje::getModificacionId)
                .orElseThrow(() -> new NoSuchElementException("No hay ningún mensaje con id "
                        + adjunto.getMensajeId()));
        Long modeloId = modificacionQueExiste(modificacionId).getModeloId();
        List<ResumenFoto> delModelo = fotos.de(modeloId);
        Set<String> usados = new HashSet<>();
        for (ResumenFoto existente : delModelo) {
            usados.add(existente.nombreFichero().toLowerCase(Locale.ROOT));
            if (mismoNombreQuizaNumerado(existente.nombreFichero(), adjunto.getNombre())
                    && fotos.conBytes(existente.id()).map(foto -> Arrays.equals(foto.getJpeg(), adjunto.getBytes()))
                    .orElse(false)) {
                return delModelo;
            }
        }
        fotos.guardar(modeloId, Modelos.nombreLibre(adjunto.getNombre(), usados), adjunto.getBytes());
        return fotos.de(modeloId);
    }

    /**
     * Los números de la bandeja de quien la mira.
     * <ul>
     * <li>TALLER: nuevas = órdenes NUEVAS; pendientes = órdenes NUEVA, VISTA o
     * EN_CURSO; conRespuesta = avisos suyos abiertos con respuesta del
     * despacho pendiente de contestar.</li>
     * <li>DESPACHO: nuevas = avisos NUEVOS; pendientes = avisos NUEVA, VISTA o
     * EN_CURSO; conRespuesta = órdenes abiertas con una duda del taller
     * pendiente de contestar.</li>
     * </ul>
     * En los dos, urgentes = órdenes para el taller abiertas y urgentes.
     */
    @Transactional(readOnly = true)
    public ResumenBandeja resumen(UsuarioActual quien) {
        if (quien == null || quien.rol() == null) {
            return new ResumenBandeja(0, 0, 0, 0);
        }
        Direccion ordenes = Direccion.DESPACHO_A_TALLER;
        int urgentes = (int) repositorio.countByDireccionAndUrgenteTrueAndEstadoIn(ordenes, ABIERTAS);
        if (quien.esDespacho()) {
            Direccion avisos = Direccion.TALLER_A_DESPACHO;
            int nuevas = (int) repositorio.countByDireccionAndEstadoIn(avisos, EnumSet.of(EstadoModificacion.NUEVA));
            int pendientes = (int) repositorio.countByDireccionAndEstadoIn(avisos, ABIERTAS);
            int conRespuesta = esperandoA(repositorio.findByDireccionAndNecesitaRespuestaTrue(ordenes),
                    Rol.DESPACHO);
            return new ResumenBandeja(nuevas, pendientes, urgentes, conRespuesta);
        }
        int nuevas = (int) repositorio.countByDireccionAndEstadoIn(ordenes, EnumSet.of(EstadoModificacion.NUEVA));
        int pendientes = (int) repositorio.countByDireccionAndEstadoIn(ordenes, ABIERTAS);
        int conRespuesta = esperandoA(repositorio.findByDireccionAndNecesitaRespuestaTrueAndCreadaPor(
                Direccion.TALLER_A_DESPACHO, quien.id()), Rol.TALLER);
        return new ResumenBandeja(nuevas, pendientes, urgentes, conRespuesta);
    }

    /**
     * El lado que tiene que contestar: el contrario del autor del último
     * mensaje (TALLER o DESPACHO; un ADMIN habla por el despacho), si la
     * modificación espera respuesta; null si no la espera o el último mensaje
     * no tiene autor.
     */
    @Transactional(readOnly = true)
    public Rol ladoQueDebeResponder(Modificacion m) {
        if (m == null || !m.isNecesitaRespuesta()) {
            return null;
        }
        return mensajes.findFirstByModificacionIdOrderByCreadoEnDescIdDesc(m.getId())
                .map(Mensaje::getAutorId)
                .flatMap(usuarios::findById)
                .map(autor -> ladoContrario(autor.getRol()))
                .orElse(null);
    }

    /**
     * Una entrada por modificación del modelo, de la más nueva a la más vieja:
     * «Aviso: …» con el título (o, sin él, el tipo de cambio) o «Orden: …»
     * con el título, atribuida a quien la creó y a su lado, y con la primera
     * nota de voz de su primer mensaje si la tiene.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Evento> eventosDe(Long modeloId) {
        List<Modificacion> suyas = modeloId == null ? List.of()
                : repositorio.findByModeloIdOrderByCreadaEnDescIdDesc(modeloId);
        if (suyas.isEmpty()) {
            return List.of();
        }
        Map<Long, Mensaje> primeros = new HashMap<>();
        for (Mensaje mensaje : mensajes.findByModificacionIdInOrderByCreadoEnAscIdAsc(ids(suyas))) {
            primeros.putIfAbsent(mensaje.getModificacionId(), mensaje);
        }
        Map<Long, Long> primerAudio = new HashMap<>();
        for (ResumenAdjunto adjunto : adjuntos.deMensajes(primeros.values().stream().map(Mensaje::getId).toList())) {
            if (adjunto.tipo() == TipoAdjunto.AUDIO) {
                primerAudio.putIfAbsent(adjunto.mensajeId(), adjunto.id());
            }
        }
        Map<Long, Usuario> creadores = usuariosPorId(suyas.stream().map(Modificacion::getCreadaPor).toList());
        List<Evento> eventos = new ArrayList<>();
        for (Modificacion m : suyas) {
            Usuario creador = creadores.get(m.getCreadaPor());
            String lado = m.esAviso() ? " (taller)" : " (despacho)";
            String quien = creador == null ? NADIE : creador.getNombre() + lado;
            Mensaje primero = primeros.get(m.getId());
            eventos.add(new Evento(m.getCreadaEn(), "MODIFICACION", textoDeEvento(m), quien, m.getId(),
                    primero == null ? null : primerAudio.get(primero.getId()),
                    m.getEstado() == null ? null : m.getEstado().name()));
        }
        return eventos;
    }

    private String textoDeEvento(Modificacion m) {
        String queEs = vacio(m.getTitulo()) ? (m.esAviso() ? tipoCambioTexto(m.getTipoCambio()) : null)
                : m.getTitulo();
        String prefijo = m.esAviso() ? "Aviso" : "Orden";
        return vacio(queEs) ? prefijo : prefijo + ": " + queEs;
    }

    private Mensaje guardarMensaje(Long modificacionId, String texto, List<AdjuntoNuevo> nuevos, UsuarioActual quien,
                                   LocalDateTime cuando) {
        Mensaje mensaje = mensajes.save(new Mensaje(modificacionId, idDe(quien), texto, cuando));
        for (AdjuntoNuevo nuevo : nuevos) {
            adjuntos.guardar(mensaje.getId(), nuevo);
        }
        return mensaje;
    }

    /**
     * Cuántas de esas modificaciones, de las abiertas, esperan respuesta de
     * ese lado; una consulta para todas. Una cerrada no cuenta aunque tenga la
     * marca encendida, igual que en {@code respuestaPendienteDe}.
     */
    private int esperandoA(List<Modificacion> candidatas, Rol lado) {
        List<Modificacion> conRespuesta = candidatas.stream().filter(m -> !m.getEstado().cerrada()).toList();
        if (conRespuesta.isEmpty()) {
            return 0;
        }
        Map<Long, Mensaje> ultimos = new HashMap<>();
        for (Mensaje mensaje : mensajes.findByModificacionIdInOrderByCreadoEnAscIdAsc(ids(conRespuesta))) {
            ultimos.put(mensaje.getModificacionId(), mensaje);
        }
        Map<Long, Usuario> autores = usuariosPorId(ultimos.values().stream().map(Mensaje::getAutorId).toList());
        return (int) conRespuesta.stream()
                .map(m -> ultimos.get(m.getId()))
                .filter(Objects::nonNull)
                .map(ultimo -> autores.get(ultimo.getAutorId()))
                .filter(autor -> autor != null && ladoContrario(autor.getRol()) == lado)
                .count();
    }

    /**
     * Las cerradas al final si se pide; las órdenes de la cola por su puesto;
     * lo demás, NUEVA primero y la más antigua primero.
     */
    private static Comparator<Modificacion> ordenDeBandeja(boolean cerradasAlFinal, Map<Long, Integer> posiciones) {
        return Comparator.comparingInt((Modificacion m) -> cerradasAlFinal && m.getEstado().cerrada() ? 1 : 0)
                .thenComparingInt(m -> posiciones.getOrDefault(m.getId(), Integer.MAX_VALUE))
                .thenComparingInt(m -> m.getEstado() == EstadoModificacion.NUEVA ? 0 : 1)
                .thenComparing(Modificacion::getCreadaEn, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Modificacion::getId);
    }

    /** Todo lo que hace falta para pintar esas modificaciones, con una consulta por tabla. */
    private Contexto contexto(List<Modificacion> lista) {
        if (lista.isEmpty()) {
            return new Contexto(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Set<Long> modeloIds = lista.stream().map(Modificacion::getModeloId).collect(Collectors.toSet());
        Map<Long, Modelo> porId = new HashMap<>();
        modelos.findAllById(modeloIds).forEach(modelo -> porId.put(modelo.getId(), modelo));
        Map<Long, Long> principales = fotos.principalesDe(modeloIds);

        List<Long> ids = ids(lista);
        Map<Long, List<Mensaje>> mensajesPorModificacion = new LinkedHashMap<>();
        List<Mensaje> todos = mensajes.findByModificacionIdInOrderByCreadoEnAscIdAsc(ids);
        todos.forEach(mensaje -> mensajesPorModificacion
                .computeIfAbsent(mensaje.getModificacionId(), clave -> new ArrayList<>()).add(mensaje));
        Map<Long, List<ResumenAdjunto>> adjuntosPorMensaje = new HashMap<>();
        adjuntos.deMensajes(todos.stream().map(Mensaje::getId).toList()).forEach(adjunto -> adjuntosPorMensaje
                .computeIfAbsent(adjunto.mensajeId(), clave -> new ArrayList<>()).add(adjunto));
        Map<Long, List<ModificacionDestino>> destinosPorModificacion = new HashMap<>();
        List<ModificacionDestino> todosLosDestinos = destinos.findByModificacionIdInOrderByIdAsc(ids);
        todosLosDestinos.forEach(destino -> destinosPorModificacion
                .computeIfAbsent(destino.getModificacionId(), clave -> new ArrayList<>()).add(destino));

        List<Long> usuarioIds = new ArrayList<>();
        lista.forEach(m -> {
            usuarioIds.add(m.getCreadaPor());
            usuarioIds.add(m.getHechaPor());
        });
        todos.forEach(mensaje -> usuarioIds.add(mensaje.getAutorId()));
        todosLosDestinos.forEach(destino -> usuarioIds.add(destino.getHechoPor()));
        // El puesto en la cola solo hace falta si hay alguna orden de la cola entre ellas.
        Map<Long, Integer> posiciones = lista.stream().anyMatch(ColaTaller::enLaCola) ? cola.posiciones() : Map.of();
        return new Contexto(porId, principales, mensajesPorModificacion, adjuntosPorMensaje, destinosPorModificacion,
                usuariosPorId(usuarioIds), posiciones);
    }

    /** Las piezas de una lista ya cargadas, por id. */
    private final class Contexto {

        private final Map<Long, Modelo> modelosPorId;
        private final Map<Long, Long> fotosPrincipales;
        private final Map<Long, List<Mensaje>> mensajesPorModificacion;
        private final Map<Long, List<ResumenAdjunto>> adjuntosPorMensaje;
        private final Map<Long, List<ModificacionDestino>> destinosPorModificacion;
        private final Map<Long, Usuario> usuarios;
        private final Map<Long, Integer> posiciones;

        private Contexto(Map<Long, Modelo> modelosPorId, Map<Long, Long> fotosPrincipales,
                         Map<Long, List<Mensaje>> mensajesPorModificacion,
                         Map<Long, List<ResumenAdjunto>> adjuntosPorMensaje,
                         Map<Long, List<ModificacionDestino>> destinosPorModificacion, Map<Long, Usuario> usuarios,
                         Map<Long, Integer> posiciones) {
            this.modelosPorId = modelosPorId;
            this.fotosPrincipales = fotosPrincipales;
            this.mensajesPorModificacion = mensajesPorModificacion;
            this.adjuntosPorMensaje = adjuntosPorMensaje;
            this.destinosPorModificacion = destinosPorModificacion;
            this.usuarios = usuarios;
            this.posiciones = posiciones;
        }

        List<Mensaje> mensajesDe(Modificacion m) {
            return mensajesPorModificacion.getOrDefault(m.getId(), List.of());
        }

        List<ResumenAdjunto> adjuntosDe(Mensaje mensaje) {
            return adjuntosPorMensaje.getOrDefault(mensaje.getId(), List.of());
        }

        String clienteDe(Modificacion m) {
            Modelo modelo = modelosPorId.get(m.getModeloId());
            return modelo == null ? null : modelo.getCliente();
        }

        /** El texto (ya en minúsculas) sale en el título, en algún mensaje o en algún adjunto. */
        boolean contiene(Modificacion m, String texto) {
            if (contieneEn(m.getTitulo(), texto)) {
                return true;
            }
            for (Mensaje mensaje : mensajesDe(m)) {
                if (contieneEn(mensaje.getTexto(), texto)) {
                    return true;
                }
                for (ResumenAdjunto adjunto : adjuntosDe(mensaje)) {
                    if (contieneEn(adjunto.transcripcion(), texto) || contieneEn(adjunto.resumenIa(), texto)) {
                        return true;
                    }
                }
            }
            return false;
        }

        ModificacionResumen resumen(Modificacion m) {
            Modelo modelo = modelosPorId.get(m.getModeloId());
            int numeroFotos = 0;
            int audios = 0;
            int documentos = 0;
            List<Mensaje> suyos = mensajesDe(m);
            for (Mensaje mensaje : suyos) {
                for (ResumenAdjunto adjunto : adjuntosDe(mensaje)) {
                    switch (adjunto.tipo()) {
                        case FOTO -> numeroFotos++;
                        case AUDIO -> audios++;
                        case DOCUMENTO -> documentos++;
                    }
                }
            }
            // De qué lado es el último mensaje: el taller enseña la última palabra del despacho
            // también en un aviso cerrado (por qué se descartó).
            Usuario autorUltimo = suyos.isEmpty() ? null : usuarios.get(suyos.get(suyos.size() - 1).getAutorId());
            Rol ultimoMensajeDe = autorUltimo == null ? null : ladoDe(autorUltimo.getRol());
            Rol pendiente = null;
            // Una cerrada no espera a nadie, aunque la marca siga encendida
            // (se puede escribir en una descartada sin reabrirla).
            if (m.isNecesitaRespuesta() && !m.getEstado().cerrada() && !suyos.isEmpty()) {
                Usuario autor = usuarios.get(suyos.get(suyos.size() - 1).getAutorId());
                pendiente = autor == null ? null : ladoContrario(autor.getRol());
            }
            List<DestinoEstado> estados = destinosPorModificacion.getOrDefault(m.getId(), List.of()).stream()
                    .map(d -> new DestinoEstado(d.getDestino(), TextosDestino.texto(d.getDestino()), d.isAplica(),
                            d.getHechoEn(), nombre(d.getHechoPor())))
                    .toList();
            // La nota de voz del cuerpo (el primer mensaje), para decir en la lista cuánto dura.
            Integer duracionAudio = suyos.isEmpty() ? null : adjuntosDe(suyos.get(0)).stream()
                    .filter(adjunto -> adjunto.tipo() == TipoAdjunto.AUDIO)
                    .findFirst()
                    .map(ResumenAdjunto::duracionSeg)
                    .orElse(null);
            return new ModificacionResumen(m.getId(), m.getModeloId(),
                    modelo == null ? null : modelo.nombreVisible(), modelo == null ? null : modelo.getNombre(),
                    modelo == null ? null : modelo.getCliente(), fotosPrincipales.get(m.getModeloId()),
                    m.getDireccion(), m.getTipoCambio(), tipoCambioTexto(m.getTipoCambio()), m.isUrgente(),
                    posiciones.get(m.getId()), m.getEstado(), m.getTitulo(), m.isTituloDeIa(), m.getCreadaEn(), nombre(m.getCreadaPor()),
                    m.getVistaEn(), m.getHechaEn(), nombre(m.getHechaPor()), pendiente,
                    modelo == null ? null : modelo.getEtapa(), estados,
                    numeroFotos, audios, documentos, duracionAudio, ultimoMensajeDe);
        }

        private String nombre(Long usuarioId) {
            Usuario usuario = usuarios.get(usuarioId);
            return usuario == null ? null : usuario.getNombre();
        }
    }

    private Map<Long, Usuario> usuariosPorId(Collection<Long> ids) {
        Set<Long> sinNulos = new HashSet<>(ids);
        sinNulos.remove(null);
        Map<Long, Usuario> porId = new HashMap<>();
        if (!sinNulos.isEmpty()) {
            usuarios.findAllById(sinNulos).forEach(usuario -> porId.put(usuario.getId(), usuario));
        }
        return porId;
    }

    /** El texto del tipo de cambio en el yml; la clave si ya no está; null si no hay. */
    private String tipoCambioTexto(String clave) {
        if (clave == null) {
            return null;
        }
        return props.tipoCambio(clave).map(TipoCambio::texto).orElse(clave);
    }

    /** La clave tal cual si está en el yml; un 400 si no; null si falta y no es obligatoria. */
    private String tipoCambioDelYml(String clave, boolean obligatorio) {
        if (vacio(clave)) {
            if (obligatorio) {
                throw new IllegalArgumentException("Falta el tipo de cambio");
            }
            return null;
        }
        String limpia = clave.trim();
        return props.tipoCambio(limpia)
                .map(TipoCambio::clave)
                .orElseThrow(() -> new IllegalArgumentException("No existe el tipo de cambio " + limpia));
    }

    /**
     * Los destinos de {@code modelos.destinos-aplicacion}, en su orden. Uno
     * que no sea de {@link DestinoAplicacion} se salta: no hay dónde
     * guardarlo, y el desplegable no puede ofrecer algo que no se guarda.
     */
    private List<DestinoAplicacion> destinosDelYml() {
        List<DestinoAplicacion> lista = new ArrayList<>();
        if (props.destinosAplicacion() == null) {
            return lista;
        }
        for (String clave : props.destinosAplicacion()) {
            for (DestinoAplicacion destino : DestinoAplicacion.values()) {
                if (destino.name().equals(clave) && !lista.contains(destino)) {
                    lista.add(destino);
                }
            }
        }
        return lista;
    }

    private static boolean esDestinatario(Modificacion m, UsuarioActual quien) {
        if (quien == null || quien.rol() == null) {
            return false;
        }
        return m.esAviso() ? quien.esDespacho() : quien.rol() == Rol.TALLER;
    }

    /** TALLER o DESPACHO: un ADMIN habla por el despacho. */
    private static Rol ladoDe(Rol rol) {
        if (rol == null) {
            return null;
        }
        return rol.esDespacho() ? Rol.DESPACHO : Rol.TALLER;
    }

    private static Rol ladoContrario(Rol autor) {
        if (autor == null) {
            return null;
        }
        return autor.esDespacho() ? Rol.TALLER : Rol.DESPACHO;
    }

    private static void exigirDespacho(UsuarioActual quien) {
        if (quien == null || !quien.esDespacho()) {
            throw new AccessDeniedException("Solo el despacho puede hacer esto");
        }
    }

    private static void exigirNoDescartada(Modificacion m) {
        if (m.getEstado() == EstadoModificacion.DESCARTADA) {
            throw new IllegalArgumentException("La modificación está descartada");
        }
    }

    private void modeloQueExiste(Long modeloId) {
        if (catalogo.modelo(modeloId).isEmpty()) {
            throw new NoSuchElementException("No hay ningún modelo con id " + modeloId);
        }
    }

    private Modificacion modificacionQueExiste(Long id) {
        return (id == null ? Optional.<Modificacion>empty() : repositorio.findById(id))
                .orElseThrow(() -> new NoSuchElementException("No hay ninguna modificación con id " + id));
    }

    private static List<AdjuntoNuevo> sinNulos(List<AdjuntoNuevo> lista) {
        return lista == null ? List.of() : lista.stream().filter(Objects::nonNull).toList();
    }

    private static List<Long> ids(List<Modificacion> lista) {
        return lista.stream().map(Modificacion::getId).toList();
    }

    /**
     * {@code existente} es {@code nombre} tal cual o con un {@code " (n)"}
     * delante de la extensión (lo que pone {@link Modelos#nombreLibre}), sin
     * mirar mayúsculas.
     */
    private static boolean mismoNombreQuizaNumerado(String existente, String nombre) {
        String elExistente = existente.toLowerCase(Locale.ROOT);
        String elNombre = nombre.toLowerCase(Locale.ROOT);
        int punto = elNombre.lastIndexOf('.');
        String base = punto > 0 ? elNombre.substring(0, punto) : elNombre;
        String extension = punto > 0 ? elNombre.substring(punto) : "";
        if (elExistente.length() < base.length() + extension.length() || !elExistente.startsWith(base)
                || !elExistente.endsWith(extension)) {
            return false;
        }
        String enMedio = elExistente.substring(base.length(), elExistente.length() - extension.length());
        return enMedio.isEmpty() || enMedio.matches(" \\(\\d+\\)");
    }

    private static boolean contieneEn(String campo, String texto) {
        return campo != null && campo.toLowerCase(Locale.ROOT).contains(texto);
    }

    private static Long idDe(UsuarioActual quien) {
        return quien == null ? null : quien.id();
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
