package com.puntotres.packinglist.modelos.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.modelos.api.dto.CodigoHistorico;
import com.puntotres.packinglist.modelos.api.dto.Evento;
import com.puntotres.packinglist.modelos.api.dto.ModeloFicha;
import com.puntotres.packinglist.modelos.api.dto.ModeloResumen;
import com.puntotres.packinglist.modelos.persistence.CambioEtapa;
import com.puntotres.packinglist.modelos.persistence.CambioEtapaRepository;
import com.puntotres.packinglist.modelos.persistence.ModeloCodigoHistorico;
import com.puntotres.packinglist.modelos.persistence.ModeloCodigoHistoricoRepository;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.ResumenTemporada;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;

/**
 * Los modelos tal como los ve la app de modelos: la lista con sus filtros,
 * la ficha con su línea de tiempo, el alta a mano (sobre todo de
 * prototipos, que ningún pedido trae), la etapa y la etiqueta de patrones.
 *
 * Trabaja sobre el catálogo de siempre ({@link ModeloRepository},
 * {@link CatalogoModelos}, {@link FotosCatalogo}): un modelo es uno solo,
 * lo dé de alta un pedido o una persona. Lo que añade la app es la traza: cada
 * cambio de etapa va a {@code cambio_etapa} y cada nombre del modelo a
 * {@code modelo_codigo_historico}.
 *
 * El código y el nombre de prototipo se guardan y se buscan recortados y en
 * mayúsculas, como hace el catálogo con los códigos: "Proto A" y "PROTO A "
 * son el mismo prototipo y la restricción única de la base de datos no lo
 * sabría.
 */
@Service
public class Modelos {

    /** Lo que sale en la línea de tiempo cuando no hay nadie a quien atribuirlo. */
    private static final String NADIE = "—";

    /** Lo que cabe en {@code modelo_codigo_historico.codigo}, donde va también el nombre de prototipo. */
    private static final int LARGO_CODIGO = 80;

    /** Lo que cabe en el nombre del modelo y en el motivo de un cambio de etapa. */
    private static final int LARGO_TEXTO = 200;

    private final ModeloRepository repositorio;
    private final CatalogoModelos catalogo;
    private final FotosCatalogo fotos;
    private final ArchivoTemporadas temporadas;
    private final ClientesProperties clientes;
    private final CambioEtapaRepository cambiosEtapa;
    private final ModeloCodigoHistoricoRepository codigos;
    private final UsuarioRepository usuarios;
    private final List<FuenteEventos> fuentes;

    public Modelos(ModeloRepository repositorio, CatalogoModelos catalogo, FotosCatalogo fotos,
                   ArchivoTemporadas temporadas, ClientesProperties clientes, CambioEtapaRepository cambiosEtapa,
                   ModeloCodigoHistoricoRepository codigos, UsuarioRepository usuarios,
                   List<FuenteEventos> fuentes) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.fotos = fotos;
        this.temporadas = temporadas;
        this.clientes = clientes;
        this.cambiosEtapa = cambiosEtapa;
        this.codigos = codigos;
        this.usuarios = usuarios;
        this.fuentes = fuentes == null ? List.of() : List.copyOf(fuentes);
    }

    /**
     * Los filtros de la lista; un campo nulo no filtra.
     *
     * @param texto       casa con el código, el nombre de prototipo o el nombre, sin mirar mayúsculas
     * @param sinEtiqueta true: solo los que todavía no tienen la etiqueta de patrones impresa
     */
    public record Filtro(String cliente, Etapa etapa, Long temporadaId, String texto, Boolean sinEtiqueta) {
    }

    /**
     * Un modelo dado de alta a mano: con su código, o con su nombre de
     * prototipo y la temporada en que nace. Etapa y tipo son opcionales.
     */
    public record AltaManual(String cliente, Long temporadaId, String codigo, String nombrePrototipo, String nombre,
                             TipoArticulo tipo, Etapa etapa) {
    }

    /**
     * Quien tenga más cosas que contar de un modelo (las modificaciones) las
     * añade a su línea de tiempo implementando esto; {@link Modelos} no
     * depende de él, así que no hay ciclo.
     */
    public interface FuenteEventos {
        List<Evento> eventosDe(Long modeloId);
    }

    /** Ordenados por cliente y por cómo se enseñan (código o nombre de prototipo). */
    @Transactional(readOnly = true)
    public List<ModeloResumen> listar(Filtro filtro) {
        Filtro f = filtro == null ? new Filtro(null, null, null, null, null) : filtro;
        String cliente = vacio(f.cliente()) ? null : clave(f.cliente());
        List<Modelo> candidatos;
        if (f.temporadaId() != null) {
            String clienteDeLaTemporada = cliente != null ? cliente : clienteDeTemporada(f.temporadaId());
            candidatos = repositorio.findAllById(catalogo.idsDeModeloEn(clienteDeLaTemporada, f.temporadaId()));
        } else if (cliente != null) {
            candidatos = repositorio.findByClienteOrderByCodigoAscNombrePrototipoAsc(cliente);
        } else {
            candidatos = repositorio.findAll();
        }
        String texto = vacio(f.texto()) ? null : f.texto().trim().toUpperCase(Locale.ROOT);
        List<Modelo> elegidos = candidatos.stream()
                .filter(modelo -> cliente == null || cliente.equals(modelo.getCliente()))
                .filter(modelo -> f.etapa() == null || f.etapa() == modelo.getEtapa())
                .filter(modelo -> !Boolean.TRUE.equals(f.sinEtiqueta()) || modelo.getEtiquetaImpresaEn() == null)
                .filter(modelo -> texto == null || casa(modelo, texto))
                .sorted(Comparator.comparing(Modelo::getCliente).thenComparing(Modelo::nombreVisible))
                .toList();
        Map<Long, Long> principales = fotos.principalesDe(elegidos.stream().map(Modelo::getId).toList());
        return elegidos.stream()
                .map(modelo -> ModeloResumen.de(modelo, principales.get(modelo.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ModeloFicha ficha(Long id) {
        Modelo modelo = modeloQueExiste(id);
        Set<Long> suyas = new HashSet<>(catalogo.temporadasDeModelo(id));
        List<String> nombresTemporada = temporadas.de(modelo.getCliente()).stream()
                .filter(temporada -> suyas.contains(temporada.id()))
                .map(ResumenTemporada::temporada)
                .toList();
        List<ModeloCodigoHistorico> historico = codigos.findByModeloIdOrderByDesdeDesc(id);
        List<CodigoHistorico> nombres = historico.stream()
                .map(apunte -> new CodigoHistorico(apunte.getCodigo(), apunte.getDesde(), apunte.getHasta(),
                        apunte.getMotivo()))
                .toList();
        return new ModeloFicha(ModeloResumen.de(modelo, fotos.principalDe(id).orElse(null)), nombresTemporada,
                nombres, fotos.de(id), List.of(), lineaTiempo(modelo, historico));
    }

    @Transactional
    public Modelo darDeAlta(AltaManual alta, UsuarioActual quien) {
        String cliente = claveDeCliente(alta.cliente());
        String codigo = normalizar(alta.codigo());
        String prototipo = normalizar(alta.nombrePrototipo());
        if (codigo == null && prototipo == null) {
            throw new IllegalArgumentException("Falta la referencia o la referencia de prototipo");
        }
        String nombreInicial = codigo != null ? codigo : prototipo;
        if (nombreInicial.length() > LARGO_CODIGO) {
            throw new IllegalArgumentException("La referencia no puede pasar de " + LARGO_CODIGO + " caracteres");
        }
        if (alta.temporadaId() != null && temporadas.de(cliente).stream()
                .noneMatch(temporada -> temporada.id().equals(alta.temporadaId()))) {
            throw new IllegalArgumentException("No hay ninguna temporada " + alta.temporadaId() + " de " + cliente);
        }
        if (codigo != null) {
            if (repositorio.findByClienteAndCodigo(cliente, codigo).isPresent()) {
                throw new IllegalArgumentException("Ya existe " + codigo
                        + ": asigna la referencia al prototipo o fusiona");
            }
        } else {
            if (alta.temporadaId() == null) {
                throw new IllegalArgumentException("Un prototipo necesita su temporada");
            }
            if (repositorio.findByClienteAndNombrePrototipoAndTemporadaAltaId(cliente, prototipo,
                    alta.temporadaId()).isPresent()) {
                throw new IllegalArgumentException("Ya hay un " + prototipo + " en esa temporada");
            }
        }
        Etapa etapa = alta.etapa() != null ? alta.etapa() : Etapa.PROTOTIPO;
        TipoArticulo tipo = alta.tipo() != null ? alta.tipo()
                : codigo != null ? TipoArticulo.porReferencia(codigo) : TipoArticulo.BOLSO;
        String nombre = limitado(alta.nombre(), LARGO_TEXTO, "El nombre");
        Modelo modelo = repositorio.save(new Modelo(cliente, codigo, prototipo, nombre, tipo, etapa,
                alta.temporadaId()));
        catalogo.vincularTemporada(modelo.getId(), alta.temporadaId());
        LocalDateTime ahora = LocalDateTime.now();
        codigos.save(new ModeloCodigoHistorico(modelo.getId(), nombreInicial, ahora, "ALTA"));
        cambiosEtapa.save(new CambioEtapa(modelo.getId(), null, etapa, ahora, idDe(quien), null));
        return modelo;
    }

    /** Un nombre nulo o en blanco y un tipo nulo no se tocan. */
    @Transactional
    public void editar(Long id, String nombre, TipoArticulo tipo) {
        Modelo modelo = modeloQueExiste(id);
        if (!vacio(nombre)) {
            modelo.setNombre(limitado(nombre, LARGO_TEXTO, "El nombre"));
        }
        if (tipo != null) {
            modelo.setTipo(tipo);
        }
    }

    @Transactional
    public void cambiarEtapa(Long id, Etapa nueva, String motivo, UsuarioActual quien) {
        if (nueva == null) {
            throw new IllegalArgumentException("Falta la etapa");
        }
        Modelo modelo = modeloQueExiste(id);
        Etapa anterior = modelo.getEtapa();
        if (anterior == nueva) {
            throw new IllegalArgumentException("El modelo ya está en " + nueva.texto());
        }
        String elMotivo = limitado(motivo, LARGO_TEXTO, "El motivo");
        modelo.cambiarEtapa(nueva);
        cambiosEtapa.save(new CambioEtapa(id, anterior, nueva, modelo.getFechaEtapa(), idDe(quien), elMotivo));
    }

    /** Apunta ahora como fecha de impresión de la etiqueta de patrones; los ids que no existen se saltan. */
    @Transactional
    public void marcarEtiquetaImpresa(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now();
        repositorio.findAllById(ids).forEach(modelo -> modelo.marcarEtiquetaImpresa(ahora));
    }

    /**
     * El modelo al que lleva un id, aunque ya no exista: el mismo si existe;
     * si no, el que lo absorbió (apuntado en su histórico como {@code "#" + id});
     * vacío si ninguno. Es lo que deja seguir valiendo el QR de un modelo
     * fusionado.
     */
    @Transactional(readOnly = true)
    public Optional<Long> resolverId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        if (repositorio.existsById(id)) {
            return Optional.of(id);
        }
        return codigos.findFirstByCodigo("#" + id).map(ModeloCodigoHistorico::getModeloId);
    }

    private List<Evento> lineaTiempo(Modelo modelo, List<ModeloCodigoHistorico> historico) {
        List<CambioEtapa> etapas = cambiosEtapa.findByModeloIdOrderByFechaDesc(modelo.getId());
        Map<Long, String> nombres = nombresDeUsuario(etapas);
        List<Evento> eventos = new ArrayList<>();
        for (CambioEtapa cambio : etapas) {
            String texto = "Pasa a " + cambio.getA().texto()
                    + (vacio(cambio.getMotivo()) ? "" : " · " + cambio.getMotivo());
            eventos.add(new Evento(cambio.getFecha(), "ETAPA", texto,
                    nombres.getOrDefault(cambio.getUsuarioId(), NADIE), null, null));
        }
        for (ModeloCodigoHistorico apunte : historico) {
            eventos.add(new Evento(apunte.getDesde(), "CODIGO", textoDeCodigo(modelo, apunte.getCodigo()), NADIE,
                    null, null));
        }
        for (FuenteEventos fuente : fuentes) {
            eventos.addAll(fuente.eventosDe(modelo.getId()));
        }
        // Estable: a la misma hora, la etapa antes que el código y estos antes que lo de las fuentes.
        eventos.sort(Comparator.comparing(Evento::fecha, Comparator.nullsLast(Comparator.reverseOrder())));
        return eventos;
    }

    /** Un nombre de prototipo se dice como tal; cualquier otro es una referencia del cliente. */
    private static String textoDeCodigo(Modelo modelo, String codigo) {
        boolean esPrototipo = !modelo.getNombrePrototipo().isEmpty() && modelo.getNombrePrototipo().equals(codigo);
        return (esPrototipo ? "Prototipo " : "Referencia ") + codigo;
    }

    private Map<Long, String> nombresDeUsuario(List<CambioEtapa> etapas) {
        Set<Long> ids = etapas.stream().map(CambioEtapa::getUsuarioId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> nombres = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Usuario usuario : usuarios.findAllById(ids)) {
                nombres.put(usuario.getId(), usuario.getNombre());
            }
        }
        return nombres;
    }

    private Modelo modeloQueExiste(Long id) {
        return (id == null ? Optional.<Modelo>empty() : repositorio.findById(id))
                .orElseThrow(() -> new NoSuchElementException("No hay ningún modelo con id " + id));
    }

    /** El cliente de la temporada, para filtrar por temporada sin haber elegido cliente; null si no existe. */
    private String clienteDeTemporada(Long temporadaId) {
        return temporadas.todas().stream()
                .filter(temporada -> temporada.id().equals(temporadaId))
                .map(ResumenTemporada::cliente)
                .findFirst()
                .orElse(null);
    }

    /** La clave del catálogo de clientes que corresponde a lo recibido, sin mirar mayúsculas ni espacios. */
    private String claveDeCliente(String cliente) {
        if (vacio(cliente)) {
            throw new IllegalArgumentException("Falta el cliente");
        }
        return clientes.getClientes().keySet().stream()
                .filter(clave -> clave.equalsIgnoreCase(cliente.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay ningún cliente " + cliente.trim()));
    }

    private static boolean casa(Modelo modelo, String texto) {
        return contiene(modelo.getCodigo(), texto) || contiene(modelo.getNombrePrototipo(), texto)
                || contiene(modelo.getNombre(), texto);
    }

    private static boolean contiene(String campo, String texto) {
        return campo != null && campo.toUpperCase(Locale.ROOT).contains(texto);
    }

    /** Recortado; null si está en blanco; un 400 si no cabe en su columna. */
    private static String limitado(String texto, int largo, String queEs) {
        if (vacio(texto)) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.length() > largo) {
            throw new IllegalArgumentException(queEs + " no puede pasar de " + largo + " caracteres");
        }
        return limpio;
    }

    private static Long idDe(UsuarioActual quien) {
        return quien == null ? null : quien.id();
    }

    /** Recortado y en mayúsculas; un blanco es que no hay. */
    private static String normalizar(String texto) {
        return vacio(texto) ? null : clave(texto);
    }

    private static String clave(String texto) {
        return texto.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
