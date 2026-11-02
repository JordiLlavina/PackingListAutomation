package com.puntotres.packinglist.service.catalogo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloColor;
import com.puntotres.packinglist.persistence.ArticuloColorId;
import com.puntotres.packinglist.persistence.ArticuloColorRepository;
import com.puntotres.packinglist.persistence.ArticuloId;
import com.puntotres.packinglist.persistence.ArticuloRepository;
import com.puntotres.packinglist.persistence.Incorporacion;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ModeloId;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.ModeloTemporada;
import com.puntotres.packinglist.persistence.ModeloTemporadaId;
import com.puntotres.packinglist.persistence.ModeloTemporadaRepository;
import com.puntotres.packinglist.persistence.Piel;
import com.puntotres.packinglist.persistence.PielId;
import com.puntotres.packinglist.persistence.PielRepository;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.persistence.TipoExcelTemporada;
import com.puntotres.packinglist.service.corte.ArticuloCorte;
import com.puntotres.packinglist.service.corte.ClienteCorte;
import com.puntotres.packinglist.service.corte.ColorCorte;
import com.puntotres.packinglist.service.corte.DocumentosCorteService;
import com.puntotres.packinglist.service.corte.PedidoCorte;
import com.puntotres.packinglist.service.corte.ProduccionPuntotresExcel;
import com.puntotres.packinglist.service.corte.ReferenciaCorte;

/**
 * El catálogo de modelos: lo que el programa sabe de cada modelo de cada
 * cliente, en dos niveles. El MODELO es la forma del bolso ("ULL027"); el
 * ARTÍCULO es el modelo en una piel ("ULL027.AL0103"), que es la referencia
 * que escribe el cliente. Las memorias del taller y del corte
 * ({@code MemoriaReferencias}, {@code MemoriaPieles}) son fachadas sobre
 * estas tablas.
 *
 * Cómo se parte una referencia en modelo y piel lo dice la regla de cada
 * cliente ({@code ClienteCorte.partir}, vía {@link DocumentosCorteService}):
 * AMI por el punto, APC por el guion, un genérico no la parte. No se
 * normaliza nada más: "AL245" y "AL0245" son dos pieles (decisión del
 * usuario; el día que el excel de producción se genere desde el de pedido el
 * duplicado desaparece solo). Una referencia que la regla no sabe partir se
 * guarda tal cual, con modelo = la referencia y sin piel, y queda fuera de la
 * herencia y la propagación: no se sabe de qué modelo es de verdad.
 *
 * Herencia y propagación entre las pieles de un modelo: un artículo nuevo
 * arranca con el cartón, las unidades, el forro y las combinaciones que
 * TODOS sus hermanos con dato comparten (el peso no: cambia con la piel); y
 * al cambiar uno de esos campos desde la pantalla, el cambio se copia a los
 * hermanos que decían lo mismo o estaban vacíos y respeta a los que no. Es
 * la regla que ya usaba el nombre de piel en la tabla del corte. El taller
 * NO propaga: {@code MemoriaReferencias.recordar} escribe solo su artículo.
 */
@Service
public class CatalogoModelos {

    public static final int MAXIMO_COMBINACIONES = 4;

    /** Los campos del artículo que se editan celda a celda. */
    public enum CampoArticulo {
        MEDIDA_CAJA, UNIDADES_POR_CAJA, PESO_NETO_KG, FORRO, COMBINACION1, COMBINACION2, COMBINACION3, COMBINACION4;

        /** El peso es de la piel y no se copia a las hermanas. */
        public boolean sePropaga() {
            return this != PESO_NETO_KG;
        }

        /** Lo que vale ahora en el artículo, como texto: lo que la pantalla enseña y lo que se compara al propagar. */
        String leer(Articulo articulo) {
            return switch (this) {
                case MEDIDA_CAJA -> articulo.getMedidaCaja();
                case UNIDADES_POR_CAJA -> articulo.getUnidadesPorCaja() == null ? ""
                        : String.valueOf(articulo.getUnidadesPorCaja());
                case PESO_NETO_KG -> articulo.getPesoNetoKg() == null ? "" : String.valueOf(articulo.getPesoNetoKg());
                case FORRO -> articulo.getForro();
                case COMBINACION1, COMBINACION2, COMBINACION3, COMBINACION4 ->
                        articulo.getCasillasCombinacion().get(indiceCombinacion());
            };
        }

        /** Escribe el valor ya validado; vacío borra el campo. */
        void escribir(Articulo articulo, String valor) {
            switch (this) {
                case MEDIDA_CAJA -> articulo.setMedidaCaja(valor);
                case UNIDADES_POR_CAJA -> articulo.setUnidadesPorCaja(valor.isEmpty() ? null : Integer.valueOf(valor));
                case PESO_NETO_KG -> articulo.setPesoNetoKg(valor.isEmpty() ? null : Double.valueOf(valor));
                case FORRO -> articulo.setForro(valor);
                case COMBINACION1, COMBINACION2, COMBINACION3, COMBINACION4 ->
                        articulo.setCombinacion(indiceCombinacion(), valor);
            }
        }

        private int indiceCombinacion() {
            return ordinal() - COMBINACION1.ordinal();
        }

        /**
         * Normaliza y valida lo tecleado. Los textos van en mayúsculas y sin
         * espacios de sobra, para que "badana" y "BADANA" sean lo mismo; los
         * números tienen que serlo y ser positivos.
         */
        String validar(String valor) {
            String limpio = valor == null ? "" : valor.trim();
            if (limpio.isEmpty()) {
                return "";
            }
            return switch (this) {
                case UNIDADES_POR_CAJA -> {
                    try {
                        int unidades = Integer.parseInt(limpio);
                        if (unidades <= 0) {
                            throw new NumberFormatException();
                        }
                        yield String.valueOf(unidades);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "Las unidades por caja son un número entero mayor que cero");
                    }
                }
                case PESO_NETO_KG -> {
                    try {
                        double peso = Double.parseDouble(limpio.replace(',', '.'));
                        if (!(peso > 0)) {
                            throw new NumberFormatException();
                        }
                        yield String.valueOf(peso);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("El peso es un número de kilos mayor que cero");
                    }
                }
                case MEDIDA_CAJA -> limpio.toLowerCase(Locale.ROOT);
                default -> limpio.toUpperCase(Locale.ROOT);
            };
        }
    }

    /** Los campos del modelo que se editan celda a celda. */
    public enum CampoModelo { NOMBRE, TIPO }

    /** Una referencia tocada por la propagación y lo que decía antes, para poder deshacer. */
    public record Propagada(String referencia, String anterior) {
    }

    public record Propagacion(List<Propagada> propagadas) {

        public Propagacion {
            propagadas = List.copyOf(propagadas);
        }
    }

    /** Lo que ha pasado al meter un excel de temporada en el catálogo: para el aviso emergente. */
    public record ResumenAlta(int articulos, int articulosNuevos, int modelosNuevos, int colores,
                              int coloresNuevos, List<String> avisos) {

        public ResumenAlta {
            avisos = List.copyOf(avisos);
        }
    }

    private final ModeloRepository modelos;
    private final ArticuloRepository articulos;
    private final PielRepository pieles;
    private final ArticuloColorRepository coloresDeArticulo;
    private final ModeloTemporadaRepository temporadasDeModelo;
    private final TablaColores tablaColores;
    private final DocumentosCorteService reglas;

    public CatalogoModelos(ModeloRepository modelos, ArticuloRepository articulos, PielRepository pieles,
                           ArticuloColorRepository coloresDeArticulo, ModeloTemporadaRepository temporadasDeModelo,
                           TablaColores tablaColores, DocumentosCorteService reglas) {
        this.modelos = modelos;
        this.articulos = articulos;
        this.pieles = pieles;
        this.coloresDeArticulo = coloresDeArticulo;
        this.temporadasDeModelo = temporadasDeModelo;
        this.tablaColores = tablaColores;
        this.reglas = reglas;
    }

    // --- Alta automática desde el excel de una temporada ---

    /**
     * Lee el excel de la temporada con el lector de su tipo —el pedido del
     * cliente (entero, cinturones incluidos) o el excel de producción de
     * Puntotres— y mete lo que trae en el catálogo. Lanza
     * IllegalArgumentException o IOException si el excel no se deja leer;
     * quien llama decide qué hacer (la pantalla guarda la temporada y avisa).
     */
    @Transactional
    public ResumenAlta incorporarTemporada(TemporadaGuardada temporada) throws IOException {
        String cliente = temporada.getCliente();
        PedidoCorte pedido;
        if (temporada.getTipoExcel() == TipoExcelTemporada.PRODUCCION) {
            pedido = ProduccionPuntotresExcel.leer(temporada.getExcel(), reglas.reglasDeProduccion(cliente)).pedido();
        } else {
            ClienteCorte lector = reglas.clientePara(cliente).orElseThrow(() -> new IllegalArgumentException(
                    "El pedido de " + cliente + " todavía no se sabe leer"));
            pedido = lector.pedidoEntero(temporada.getExcel());
        }
        return incorporar(cliente, pedido, temporada.getId());
    }

    /**
     * Mete un pedido (visto desde el corte: una entrada por referencia con
     * sus colores) en el catálogo: pieles, modelos y artículos que falten,
     * el código de cada color en la tabla de colores del cliente, el
     * artículo en cada color, y el vínculo de cada modelo con la temporada.
     * Lo que ya existía NO se toca: solo se rellenan huecos (el nombre de un
     * modelo, la descripción de un color).
     *
     * Un color sin código (el bicolor de AMI, los de un genérico) entra en el
     * artículo por su descripción entera y no toca la tabla de colores: esa
     * tabla es de códigos.
     */
    @Transactional
    public ResumenAlta incorporar(String cliente, PedidoCorte pedido, Long temporadaId) {
        String elCliente = clave(cliente);
        int articulosNuevos = 0;
        int modelosNuevos = 0;
        Set<String> coloresVistos = new LinkedHashSet<>();
        Map<String, String> codigosDeColor = new LinkedHashMap<>();
        Set<String> modelosDelPedido = new LinkedHashSet<>();
        for (ArticuloCorte entrada : pedido.articulos()) {
            String referencia = clave(entrada.referencia().referencia());
            if (referencia.isEmpty()) {
                continue;
            }
            boolean habiaArticulo = articulos.existsById(new ArticuloId(elCliente, referencia));
            Articulo articulo = asegurarArticulo(elCliente, referencia);
            if (!habiaArticulo) {
                articulosNuevos++;
            }
            boolean habiaModelo = modelos.existsById(new ModeloId(elCliente, articulo.getCodigoModelo()));
            asegurarModelo(elCliente, articulo.getCodigoModelo(), entrada.nombreModelo());
            if (!habiaModelo) {
                modelosNuevos++;
            }
            modelosDelPedido.add(articulo.getCodigoModelo());
            for (ColorCorte color : entrada.colores()) {
                String codigo = clave(color.codigo());
                String descripcion = color.descripcion() == null ? "" : color.descripcion().trim();
                String claveColor = codigo.isEmpty() ? descripcion.toUpperCase(Locale.ROOT) : codigo;
                if (claveColor.isEmpty()) {
                    continue;
                }
                if (!codigo.isEmpty()) {
                    codigosDeColor.merge(codigo, descripcion, (antes, nueva) -> antes.isEmpty() ? nueva : antes);
                }
                coloresVistos.add(claveColor);
                ArticuloColorId id = new ArticuloColorId(elCliente, referencia, claveColor);
                if (!coloresDeArticulo.existsById(id)) {
                    // La piel principal arranca con el color del bolso, como en la tabla del corte.
                    coloresDeArticulo.save(new ArticuloColor(elCliente, referencia, claveColor,
                            descripcion.toUpperCase(Locale.ROOT), "", List.of()));
                }
            }
        }
        Incorporacion enLaTabla = tablaColores.incorporar(elCliente, codigosDeColor);
        if (temporadaId != null) {
            for (String modelo : modelosDelPedido) {
                ModeloTemporadaId id = new ModeloTemporadaId(elCliente, modelo, temporadaId);
                if (!temporadasDeModelo.existsById(id)) {
                    temporadasDeModelo.save(new ModeloTemporada(elCliente, modelo, temporadaId));
                }
            }
        }
        return new ResumenAlta(pedido.articulos().size(), articulosNuevos, modelosNuevos, coloresVistos.size(),
                enLaTabla.anadidos().size(), pedido.avisos());
    }

    // --- Los colores de un artículo (la piel, el forro y las combinaciones en cada color del bolso) ---

    /** Los campos del color de un artículo que se editan celda a celda. */
    public enum CampoColor {
        COLOR_PIEL, COLOR_FORRO, COLOR_COMBINACION1, COLOR_COMBINACION2, COLOR_COMBINACION3, COLOR_COMBINACION4
    }

    public List<ArticuloColor> coloresDe(String cliente, String referencia) {
        if (vacio(cliente) || vacio(referencia)) {
            return List.of();
        }
        return coloresDeArticulo.findByClienteAndReferenciaOrderByCodigoColor(clave(cliente), clave(referencia));
    }

    /** El artículo en ese color del bolso, creándolo vacío (y el artículo) si falta. */
    @Transactional
    public ArticuloColor asegurarColor(String cliente, String referencia, String codigoColor) {
        if (vacio(codigoColor)) {
            throw new IllegalArgumentException("El color necesita su código, o su descripción si no tiene");
        }
        Articulo articulo = asegurarArticulo(cliente, referencia);
        ArticuloColorId id = new ArticuloColorId(articulo.getCliente(), articulo.getReferencia(), clave(codigoColor));
        return coloresDeArticulo.findById(id).orElseGet(() -> coloresDeArticulo.save(
                new ArticuloColor(id.cliente(), id.referencia(), id.codigoColor(), "", "", List.of())));
    }

    /** Escribe un color de piel del artículo en ese color del bolso; vacío borra. En mayúsculas, como todo texto. */
    @Transactional
    public void guardarCampoColor(String cliente, String referencia, String codigoColor, CampoColor campo,
                                  String valor) {
        ArticuloColor color = asegurarColor(cliente, referencia, codigoColor);
        String limpio = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        List<String> combinaciones = new ArrayList<>(color.getColoresCombinacion());
        while (combinaciones.size() < MAXIMO_COMBINACIONES) {
            combinaciones.add("");
        }
        String piel = color.getColorPiel();
        String forro = color.getColorForro();
        switch (campo) {
            case COLOR_PIEL -> piel = limpio;
            case COLOR_FORRO -> forro = limpio;
            default -> combinaciones.set(campo.ordinal() - CampoColor.COLOR_COMBINACION1.ordinal(), limpio);
        }
        color.actualizar(piel, forro, combinaciones);
    }

    /** Los forros y las combinaciones distintos ya guardados de un cliente, para autocompletar. */
    public record TextosConocidos(List<String> forros, List<String> combinaciones) {
    }

    public TextosConocidos textosConocidos(String cliente) {
        Set<String> forros = new java.util.TreeSet<>();
        Set<String> combinaciones = new java.util.TreeSet<>();
        for (Articulo articulo : articulosDe(cliente)) {
            if (!articulo.getForro().isEmpty()) {
                forros.add(articulo.getForro());
            }
            combinaciones.addAll(articulo.getCombinaciones());
        }
        return new TextosConocidos(List.copyOf(forros), List.copyOf(combinaciones));
    }

    /** Los códigos de modelo que fueron en una temporada, en orden. */
    public Set<String> codigosDeModeloEn(String cliente, Long temporadaId) {
        if (vacio(cliente) || temporadaId == null) {
            return Set.of();
        }
        Set<String> codigos = new LinkedHashSet<>();
        temporadasDeModelo.findByClienteAndTemporadaId(clave(cliente), temporadaId)
                .forEach(vinculo -> codigos.add(vinculo.getCodigoModelo()));
        return codigos;
    }

    /** Los ids de las temporadas en que ha ido un modelo. */
    public List<Long> temporadasDe(String cliente, String codigoModelo) {
        if (vacio(cliente) || vacio(codigoModelo)) {
            return List.of();
        }
        return temporadasDeModelo.findByClienteAndCodigoModelo(clave(cliente), clave(codigoModelo)).stream()
                .map(ModeloTemporada::getTemporadaId)
                .toList();
    }

    public Optional<Modelo> modelo(String cliente, String codigo) {
        if (vacio(cliente) || vacio(codigo)) {
            return Optional.empty();
        }
        return modelos.findById(new ModeloId(clave(cliente), clave(codigo)));
    }

    public Optional<Articulo> articulo(String cliente, String referencia) {
        if (vacio(cliente) || vacio(referencia)) {
            return Optional.empty();
        }
        return articulos.findById(new ArticuloId(clave(cliente), clave(referencia)));
    }

    public List<Modelo> modelosDe(String cliente) {
        return vacio(cliente) ? List.of() : modelos.findByClienteOrderByCodigo(clave(cliente));
    }

    public List<Articulo> articulosDe(String cliente) {
        return vacio(cliente) ? List.of() : articulos.findByClienteOrderByReferencia(clave(cliente));
    }

    /** Los artículos del modelo que tienen piel, por referencia: los que heredan y propagan entre sí. */
    public List<Articulo> hermanos(String cliente, String codigoModelo) {
        if (vacio(cliente) || vacio(codigoModelo)) {
            return List.of();
        }
        return articulos.findByClienteAndCodigoModeloOrderByReferencia(clave(cliente), clave(codigoModelo)).stream()
                .filter(Articulo::tienePiel)
                .toList();
    }

    /** Devuelve el modelo, creándolo si falta. Un nombre que llega solo rellena el hueco: lo tecleado no se pisa. */
    @Transactional
    public Modelo asegurarModelo(String cliente, String codigo, String nombre) {
        return asegurarModelo(clave(cliente), clave(codigo), nombre, TipoArticulo.porReferencia(codigo));
    }

    private Modelo asegurarModelo(String elCliente, String elCodigo, String nombre, TipoArticulo tipo) {
        String elNombre = nombre == null ? "" : nombre.trim();
        Modelo modelo = modelos.findById(new ModeloId(elCliente, elCodigo)).orElse(null);
        if (modelo == null) {
            return modelos.save(new Modelo(elCliente, elCodigo, elNombre.isEmpty() ? null : elNombre, tipo));
        }
        if (modelo.getNombre().isEmpty() && !elNombre.isEmpty()) {
            modelo.setNombre(elNombre);
        }
        return modelo;
    }

    /**
     * Devuelve el artículo, creando la piel, el modelo y el artículo si
     * faltan. El artículo nuevo hereda de sus hermanos lo que todos
     * comparten; uno sin piel no hereda nada.
     */
    @Transactional
    public Articulo asegurarArticulo(String cliente, String referencia) {
        if (vacio(cliente) || vacio(referencia)) {
            throw new IllegalArgumentException("El artículo necesita cliente y referencia");
        }
        String elCliente = clave(cliente);
        String laReferencia = clave(referencia);
        Articulo existente = articulos.findById(new ArticuloId(elCliente, laReferencia)).orElse(null);
        if (existente != null) {
            return existente;
        }
        ReferenciaCorte partida = reglas.reglasDeProduccion(elCliente).partir(laReferencia);
        String refPiel = partida.tienePiel() ? partida.piel() : null;
        String codigoModelo = partida.tienePiel() ? partida.modelo() : laReferencia;
        if (refPiel != null && pieles.findById(new PielId(elCliente, refPiel)).isEmpty()) {
            pieles.save(new Piel(elCliente, refPiel, ""));
        }
        asegurarModelo(elCliente, codigoModelo, "", TipoArticulo.porReferencia(laReferencia));
        Articulo nuevo = new Articulo(elCliente, laReferencia, codigoModelo, refPiel);
        if (refPiel != null) {
            heredar(nuevo, hermanos(elCliente, codigoModelo));
        }
        return articulos.save(nuevo);
    }

    /** Copia a {@code nuevo} cada campo heredable en el que todos los hermanos con dato dicen lo mismo. */
    private static void heredar(Articulo nuevo, List<Articulo> hermanos) {
        if (hermanos.isEmpty()) {
            return;
        }
        for (CampoArticulo campo : CampoArticulo.values()) {
            if (!campo.sePropaga()) {
                continue;
            }
            String comun = valorComun(hermanos, campo::leer);
            if (comun != null) {
                campo.escribir(nuevo, comun);
            }
        }
    }

    /** El valor que todos los que lo tienen comparten, o null si ninguno lo tiene o no coinciden. */
    private static String valorComun(List<Articulo> hermanos, Function<Articulo, String> leer) {
        String comun = null;
        for (Articulo hermano : hermanos) {
            String valor = leer.apply(hermano);
            if (valor.isEmpty()) {
                continue;
            }
            if (comun == null) {
                comun = valor;
            } else if (!comun.equals(valor)) {
                return null;
            }
        }
        return comun;
    }

    /**
     * Escribe el campo en el artículo (creándolo si falta) y, con
     * {@code propagar}, lo copia a los hermanos con piel que tenían el mismo
     * valor antiguo o estaban vacíos. Vacío borra el campo. Lanza
     * IllegalArgumentException con un mensaje en español si el valor no vale.
     */
    @Transactional
    public Propagacion guardarCampo(String cliente, String referencia, CampoArticulo campo, String valor,
                                    boolean propagar) {
        String validado = campo.validar(valor);
        Articulo articulo = asegurarArticulo(cliente, referencia);
        String anterior = campo.leer(articulo);
        campo.escribir(articulo, validado);
        List<Propagada> propagadas = new ArrayList<>();
        if (propagar && campo.sePropaga() && articulo.tienePiel()) {
            for (Articulo hermano : hermanos(articulo.getCliente(), articulo.getCodigoModelo())) {
                if (hermano.getReferencia().equals(articulo.getReferencia())) {
                    continue;
                }
                String suyo = campo.leer(hermano);
                if (suyo.isEmpty() || suyo.equals(anterior)) {
                    campo.escribir(hermano, validado);
                    propagadas.add(new Propagada(hermano.getReferencia(), suyo));
                }
            }
        }
        return new Propagacion(propagadas);
    }

    @Transactional
    public void guardarCampoModelo(String cliente, String codigo, CampoModelo campo, String valor) {
        Modelo modelo = modelo(cliente, codigo).orElseThrow(() -> new IllegalArgumentException(
                "No hay ningún modelo " + codigo + " de " + cliente));
        String limpio = valor == null ? "" : valor.trim();
        switch (campo) {
            case NOMBRE -> modelo.setNombre(limpio.isEmpty() ? null : limpio.toUpperCase(Locale.ROOT));
            case TIPO -> {
                try {
                    modelo.setTipo(TipoArticulo.valueOf(limpio.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("El tipo es BOLSO o CINTURON");
                }
            }
        }
    }

    /** Borra el modelo; sus artículos, colores, fotos y temporadas caen con él (FK del esquema). */
    @Transactional
    public void borrarModelo(String cliente, String codigo) {
        modelo(cliente, codigo).ifPresent(modelos::delete);
    }

    /** Borra el artículo; sus colores caen con él (FK del esquema). El modelo se queda aunque sea el último. */
    @Transactional
    public void borrarArticulo(String cliente, String referencia) {
        articulo(cliente, referencia).ifPresent(articulos::delete);
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** Las claves se teclean o se leen de un excel: "ull027.al0103 " y "ULL027.AL0103" son el mismo artículo. */
    static String clave(String texto) {
        return Objects.requireNonNull(texto).trim().toUpperCase(Locale.ROOT);
    }
}
