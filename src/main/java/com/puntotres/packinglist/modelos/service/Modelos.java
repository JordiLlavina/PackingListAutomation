package com.puntotres.packinglist.modelos.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.modelos.api.dto.CodigoHistorico;
import com.puntotres.packinglist.modelos.api.dto.Evento;
import com.puntotres.packinglist.modelos.api.dto.Fusion;
import com.puntotres.packinglist.modelos.api.dto.ModeloFicha;
import com.puntotres.packinglist.modelos.api.dto.ModeloResumen;
import com.puntotres.packinglist.modelos.persistence.CambioEtapa;
import com.puntotres.packinglist.modelos.persistence.CambioEtapaRepository;
import com.puntotres.packinglist.modelos.persistence.ModeloCodigoHistorico;
import com.puntotres.packinglist.modelos.persistence.ModeloCodigoHistoricoRepository;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.ModificacionRepository;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.persistence.UsuarioRepository;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.FotoCatalogo;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.persistence.ResumenTemporada;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.corte.ReductorImagen;

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
 *
 * Las fotos se reducen con {@link ReductorImagen} de {@code service.corte},
 * una excepción declarada a la disciplina de paquete de la app de modelos:
 * es una utilidad estática y sin estado, la misma que deja las fotos del
 * corte y del catálogo a 1.600 px.
 */
@Service
public class Modelos {

    /** Lo que sale en la línea de tiempo cuando no hay nadie a quien atribuirlo. */
    private static final String NADIE = "—";

    /** Lo que cabe en {@code modelo_codigo_historico.codigo}, donde va también el nombre de prototipo. */
    private static final int LARGO_CODIGO = 80;

    /** Lo que cabe en el nombre del modelo y en el motivo de un cambio de etapa. */
    private static final int LARGO_TEXTO = 200;

    /** Lo que se apunta en el histórico del superviviente por cada modelo que absorbe: {@code "#" + id}. */
    private static final String PREFIJO_FUSIONADO = "#";

    /** Cuántos saltos sigue {@link #resolverId}; una cadena más larga sería un error de datos, no una fusión. */
    private static final int MAXIMO_SALTOS = 10;

    private final ModeloRepository repositorio;
    private final CatalogoModelos catalogo;
    private final FotosCatalogo fotos;
    private final ArchivoTemporadas temporadas;
    private final ClientesProperties clientes;
    private final CambioEtapaRepository cambiosEtapa;
    private final ModeloCodigoHistoricoRepository codigos;
    private final UsuarioRepository usuarios;
    private final ModificacionRepository modificaciones;
    private final List<FuenteEventos> fuentes;

    public Modelos(ModeloRepository repositorio, CatalogoModelos catalogo, FotosCatalogo fotos,
                   ArchivoTemporadas temporadas, ClientesProperties clientes, CambioEtapaRepository cambiosEtapa,
                   ModeloCodigoHistoricoRepository codigos, UsuarioRepository usuarios,
                   ModificacionRepository modificaciones, List<FuenteEventos> fuentes) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.fotos = fotos;
        this.temporadas = temporadas;
        this.clientes = clientes;
        this.cambiosEtapa = cambiosEtapa;
        this.codigos = codigos;
        this.usuarios = usuarios;
        this.modificaciones = modificaciones;
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
        List<ModeloCodigoHistorico> apuntes = codigos.findByModeloIdOrderByDesdeDescIdDesc(id);
        // Los "#id" de los modelos absorbidos no son nombres: van aparte, para el «antes era el #12».
        List<ModeloCodigoHistorico> historico = apuntes.stream()
                .filter(apunte -> !esDeFusion(apunte))
                .toList();
        List<Long> fusionadosDesde = apuntes.stream()
                .filter(Modelos::esDeFusion)
                .map(apunte -> idFusionado(apunte.getCodigo()))
                .filter(absorbido -> absorbido != null)
                .toList();
        List<CodigoHistorico> nombres = historico.stream()
                .map(apunte -> new CodigoHistorico(apunte.getCodigo(), apunte.getDesde(), apunte.getHasta(),
                        apunte.getMotivo()))
                .toList();
        return new ModeloFicha(ModeloResumen.de(modelo, fotos.principalDe(id).orElse(null)), nombresTemporada,
                nombres, fotos.de(id), List.of(), lineaTiempo(modelo, historico), fusionadosDesde);
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

    /** Un fichero de foto tal como llega: su nombre original y sus bytes, sin leer todavía. */
    public record FotoSubida(String nombre, byte[] bytes) {
    }

    /**
     * Sube una o varias fotos al modelo, reducidas a {@value ReductorImagen#LADO_MAXIMO}
     * px. Se leen y se reducen todas antes de guardar ninguna, y se guardan en
     * una sola transacción: si una no es una imagen (400 que la nombra) o la
     * base de datos falla a mitad, no se queda la mitad de la subida.
     *
     * El catálogo identifica una foto por su nombre dentro del modelo, y un
     * móvil llama {@code image.jpg} a todas las de la cámara: con el nombre
     * tal cual, la segunda se descartaría en silencio. Por eso cada una se
     * guarda con un nombre libre ({@code image (2).jpg}) frente a las que ya
     * tiene el modelo y a las de la misma subida.
     *
     * @return todas las fotos del modelo
     */
    @Transactional
    public List<ResumenFoto> subirFotos(Long modeloId, List<FotoSubida> ficheros) throws IOException {
        modeloQueExiste(modeloId);
        List<FotoSubida> conContenido = ficheros == null ? List.of() : ficheros.stream()
                .filter(fichero -> fichero != null && fichero.bytes() != null && fichero.bytes().length > 0)
                .toList();
        if (conContenido.isEmpty()) {
            throw new IllegalArgumentException("No has elegido ninguna foto");
        }
        List<String> nombres = new ArrayList<>();
        List<byte[]> jpegs = new ArrayList<>();
        for (FotoSubida fichero : conContenido) {
            String nombre = vacio(fichero.nombre()) ? "foto.jpg" : fichero.nombre().trim();
            BufferedImage imagen = leerImagen(fichero.bytes(), nombre);
            nombres.add(nombre);
            jpegs.add(ReductorImagen.jpeg(ReductorImagen.reducir(imagen, ReductorImagen.LADO_MAXIMO)));
        }
        Set<String> usados = new HashSet<>();
        for (ResumenFoto existente : fotos.de(modeloId)) {
            usados.add(existente.nombreFichero().toLowerCase(Locale.ROOT));
        }
        for (int i = 0; i < jpegs.size(); i++) {
            String libre = nombreLibre(nombres.get(i), usados);
            usados.add(libre.toLowerCase(Locale.ROOT));
            fotos.guardar(modeloId, libre, jpegs.get(i));
        }
        return fotos.de(modeloId);
    }

    /**
     * El nombre tal cual si está libre; si no, con {@code " (2)"},
     * {@code " (3)"}... delante de la extensión. La regla es la de
     * {@link FotoCatalogo#nombreLibre}, la misma que usa el catálogo al juntar
     * las fotos de dos modelos.
     */
    static String nombreLibre(String nombre, Set<String> usados) {
        return FotoCatalogo.nombreLibre(nombre, usados);
    }

    /** La imagen de esos bytes; un 400 que nombra el fichero si no es una imagen que Java sepa leer. */
    private static BufferedImage leerImagen(byte[] bytes, String nombre) {
        try {
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(bytes));
            if (imagen != null) {
                return imagen;
            }
        } catch (IOException ilegible) {
            // Igual que si no fuera una imagen: el mensaje de abajo.
        }
        throw new IllegalArgumentException("El fichero " + nombre + " no es una imagen");
    }

    /**
     * El modelo al que lleva un id, aunque ya no exista: el mismo si existe;
     * si no, el que lo absorbió (apuntado en su histórico como {@code "#" + id}),
     * siguiendo la cadena si ese también se juntó con otro; vacío si no se
     * llega a ninguno. Es lo que deja seguir valiendo el QR de un modelo
     * fusionado.
     *
     * {@link #fusionar} ya deja los {@code "#"} de una cadena colgando del
     * último superviviente, así que normalmente basta un salto; seguir la
     * cadena (con tope y sin volver a un id ya visto) es por si unos datos
     * viejos o tocados a mano no lo cumplen.
     */
    @Transactional(readOnly = true)
    public Optional<Long> resolverId(Long id) {
        Long actual = id;
        Set<Long> vistos = new HashSet<>();
        for (int salto = 0; actual != null && salto <= MAXIMO_SALTOS; salto++) {
            if (repositorio.existsById(actual)) {
                return Optional.of(actual);
            }
            if (!vistos.add(actual)) {
                return Optional.empty();
            }
            actual = codigos.findFirstByCodigo(PREFIJO_FUSIONADO + actual)
                    .map(ModeloCodigoHistorico::getModeloId)
                    .orElse(null);
        }
        return Optional.empty();
    }

    /**
     * Qué pasaría al asignar esa referencia al modelo: si ningún otro modelo
     * del cliente la tiene, nada que juntar ({@code absorbido} nulo); si otro
     * la tiene, quién sobreviviría y cuánto se llevaría el absorbido.
     */
    @Transactional(readOnly = true)
    public Fusion previsualizarReferencia(Long id, String codigo) {
        Modelo modelo = modeloQueExiste(id);
        String elCodigo = referenciaValida(modelo, codigo);
        return repositorio.findByClienteAndCodigo(modelo.getCliente(), elCodigo)
                .map(otro -> fusionEntre(modelo, otro))
                .orElse(new Fusion(modelo.getId(), null, 0, 0, 0, 0));
    }

    /**
     * Da al modelo la referencia del cliente (normalmente a un prototipo que
     * por fin la tiene). Si está libre, se escribe: el nombre de prototipo se
     * conserva en su columna y su apunte del histórico deja de ser el vigente,
     * y la referencia se apunta con motivo {@code REFERENCIA}. Si ya la tiene
     * otro modelo del cliente (el que ha entrado por el pedido), asignarla es
     * juntar los dos: sin {@code confirmar} no se toca nada y se lanza
     * {@link FusionPendiente} con lo que se juntaría; con {@code confirmar}, se
     * juntan ({@link #fusionar}).
     *
     * @return el modelo con la referencia: el mismo, o el superviviente si se han juntado
     */
    @Transactional
    public Modelo asignarReferencia(Long id, String codigo, boolean confirmar, UsuarioActual quien) {
        Modelo modelo = modeloQueExiste(id);
        String elCodigo = referenciaValida(modelo, codigo);
        Optional<Modelo> otro = repositorio.findByClienteAndCodigo(modelo.getCliente(), elCodigo);
        if (otro.isPresent()) {
            if (!confirmar) {
                throw new FusionPendiente("Ya existe " + elCodigo + ": se juntará con el prototipo",
                        fusionEntre(modelo, otro.get()));
            }
            return fusionar(modelo.getId(), otro.get().getId(), quien);
        }
        LocalDateTime ahora = LocalDateTime.now();
        cerrarNombresVigentes(modelo.getId(), ahora);
        modelo.asignarCodigo(elCodigo);
        codigos.save(new ModeloCodigoHistorico(modelo.getId(), elCodigo, ahora, "REFERENCIA"));
        return modelo;
    }

    /**
     * Junta dos modelos del mismo cliente en uno, sin vuelta atrás. Sobrevive
     * el de menor id (casi siempre el prototipo, que es el que tiene fotos e
     * histórico), que recibe el código del otro si no tenía y su nombre si no
     * tenía. La etapa final es la del que tenía código (lo que dice el
     * pedido); si los dos o ninguno lo tenían, la del superviviente. Todo lo
     * del absorbido pasa al superviviente: artículos, fotos y temporadas (del
     * catálogo), modificaciones, cambios de etapa y su histórico de nombres
     * (con los {@code "#"} de los que él ya había absorbido, para que una
     * cadena de fusiones lleve al último). Después el absorbido se borra y su
     * id queda en el histórico del superviviente como {@code "#" + id}, con
     * motivo {@code FUSION}, para que su QR siga valiendo.
     *
     * Los documentos del modelo todavía no existen; cuando existan, también
     * tendrán que pasar aquí, o el {@code on delete cascade} se los llevaría.
     *
     * @return el superviviente
     */
    @Transactional
    public Modelo fusionar(Long unId, Long otroId, UsuarioActual quien) {
        if (unId != null && unId.equals(otroId)) {
            throw new IllegalArgumentException("No se puede fusionar un modelo consigo mismo");
        }
        Modelo uno = modeloQueExiste(unId);
        Modelo otro = modeloQueExiste(otroId);
        if (!uno.getCliente().equals(otro.getCliente())) {
            throw new IllegalArgumentException("Los dos modelos tienen que ser del mismo cliente");
        }
        Modelo superviviente = uno.getId() < otro.getId() ? uno : otro;
        Modelo absorbido = superviviente == uno ? otro : uno;
        Long idAbsorbido = absorbido.getId();
        String codigoQueRecibe = superviviente.getCodigo() == null ? absorbido.getCodigo() : null;
        String nombreQueRecibe = superviviente.getNombre().isEmpty() && !absorbido.getNombre().isEmpty()
                ? absorbido.getNombre() : null;
        Etapa etapaFinal = absorbido.getCodigo() != null && superviviente.getCodigo() == null
                ? absorbido.getEtapa() : superviviente.getEtapa();

        catalogo.moverHijos(idAbsorbido, superviviente.getId());
        for (Modificacion modificacion : modificaciones.findByModeloIdOrderByCreadaEnDescIdDesc(idAbsorbido)) {
            modificacion.moverA(superviviente.getId());
        }
        for (CambioEtapa cambio : cambiosEtapa.findByModeloIdOrderByFechaDescIdDesc(idAbsorbido)) {
            cambio.moverA(superviviente.getId());
        }
        for (ModeloCodigoHistorico apunte : codigos.findByModeloIdOrderByDesdeDescIdDesc(idAbsorbido)) {
            apunte.moverA(superviviente.getId());
        }
        // Se borra antes de dar su código al superviviente: el código es único por cliente. Lo movido se
        // escribe en el mismo flush, antes que el borrado, así que la cascada del SQL no se lleva nada.
        repositorio.delete(absorbido);
        repositorio.flush();

        LocalDateTime ahora = LocalDateTime.now();
        if (codigoQueRecibe != null) {
            cerrarNombresVigentes(superviviente.getId(), ahora, codigoQueRecibe);
            superviviente.asignarCodigo(codigoQueRecibe);
            boolean yaApuntado = codigos.findByModeloIdOrderByDesdeDescIdDesc(superviviente.getId()).stream()
                    .anyMatch(apunte -> codigoQueRecibe.equals(apunte.getCodigo()) && apunte.getHasta() == null);
            if (!yaApuntado) {
                codigos.save(new ModeloCodigoHistorico(superviviente.getId(), codigoQueRecibe, ahora,
                        "REFERENCIA"));
            }
        }
        if (nombreQueRecibe != null) {
            superviviente.setNombre(nombreQueRecibe);
        }
        Etapa anterior = superviviente.getEtapa();
        if (etapaFinal != anterior) {
            superviviente.cambiarEtapa(etapaFinal);
            cambiosEtapa.save(new CambioEtapa(superviviente.getId(), anterior, etapaFinal,
                    superviviente.getFechaEtapa(), idDe(quien), "Fusión con #" + idAbsorbido));
        }
        codigos.save(new ModeloCodigoHistorico(superviviente.getId(), PREFIJO_FUSIONADO + idAbsorbido, ahora,
                "FUSION"));
        return superviviente;
    }

    /** Lo que pasaría al juntar esos dos: sobrevive el de menor id y se cuenta lo que se lleva el otro. */
    private Fusion fusionEntre(Modelo uno, Modelo otro) {
        Long superviviente = Math.min(uno.getId(), otro.getId());
        Long absorbido = Math.max(uno.getId(), otro.getId());
        return new Fusion(superviviente, absorbido, catalogo.articulosDelModelo(absorbido).size(),
                fotos.de(absorbido).size(), (int) modificaciones.countByModeloId(absorbido), 0);
    }

    /** La referencia recortada y en mayúsculas; un 400 si está en blanco, no cabe o es la que ya tiene. */
    private static String referenciaValida(Modelo modelo, String codigo) {
        String elCodigo = normalizar(codigo);
        if (elCodigo == null) {
            throw new IllegalArgumentException("Falta la referencia");
        }
        if (elCodigo.length() > LARGO_CODIGO) {
            throw new IllegalArgumentException("La referencia no puede pasar de " + LARGO_CODIGO + " caracteres");
        }
        if (elCodigo.startsWith(PREFIJO_FUSIONADO)) {
            // Se confundiría con el apunte de un modelo absorbido.
            throw new IllegalArgumentException("La referencia no puede empezar por " + PREFIJO_FUSIONADO);
        }
        if (elCodigo.equals(modelo.getCodigo())) {
            throw new IllegalArgumentException("El modelo ya tiene esa referencia");
        }
        return elCodigo;
    }

    /**
     * Da por acabados los nombres vigentes del modelo (el de prototipo, o la
     * referencia que tenía), salvo los {@code "#"} de las fusiones, que no son
     * nombres, y los que se llamen {@code salvo}.
     */
    private void cerrarNombresVigentes(Long modeloId, LocalDateTime cuando, String... salvo) {
        Set<String> seQuedan = Set.of(salvo);
        for (ModeloCodigoHistorico apunte : codigos.findByModeloIdOrderByDesdeDescIdDesc(modeloId)) {
            if (!esDeFusion(apunte) && !seQuedan.contains(apunte.getCodigo())) {
                apunte.cerrar(cuando);
            }
        }
    }

    private static boolean esDeFusion(ModeloCodigoHistorico apunte) {
        return apunte.getCodigo().startsWith(PREFIJO_FUSIONADO);
    }

    /** El id de un apunte {@code "#12"}; null si lo de detrás no es un número. */
    private static Long idFusionado(String codigo) {
        try {
            return Long.valueOf(codigo.substring(PREFIJO_FUSIONADO.length()));
        } catch (NumberFormatException noEsUnId) {
            return null;
        }
    }

    private List<Evento> lineaTiempo(Modelo modelo, List<ModeloCodigoHistorico> historico) {
        List<CambioEtapa> etapas = cambiosEtapa.findByModeloIdOrderByFechaDescIdDesc(modelo.getId());
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
        // Estable: a la misma hora, la etapa antes que el código y estos antes que lo de las fuentes, y
        // dentro de cada uno el orden en que llegan (etapas y códigos, el último apuntado primero: los
        // repositorios desempatan por id, porque dos apuntes seguidos pueden caer en el mismo instante).
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
