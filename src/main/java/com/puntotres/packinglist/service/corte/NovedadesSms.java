package com.puntotres.packinglist.service.corte;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.modelos.service.Modelos;
import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloColor;
import com.puntotres.packinglist.persistence.ColorCliente;
import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.Piel;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoColor;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;

/**
 * Lo que un pedido de SMS trae y el sistema no tiene —modelos, pieles y
 * colores—, para enseñarlo al pie de la descarga y entrarlo con un botón,
 * uno a uno o todo junto (petición del usuario).
 *
 * Dos reglas que no se deducen del código:
 * <ul>
 *   <li><b>Lo que ya está en el catálogo no se pisa nunca</b>: aquí solo se
 *       da de alta lo nuevo y se rellenan huecos. Por eso todo se vuelve a
 *       mirar en el momento de entrar: alguien ha podido darlo de alta
 *       mientras tanto.</li>
 *   <li>Un modelo nuevo entra como en la carga de modelos con origen SMS:
 *       en etapa Muestras, con su nombre obligatorio y con
 *       {@link Modelos#darDeAlta} ANTES que sus artículos
 *       ({@code asegurarArticulo} solo lo crearía en Producción y sin
 *       histórico).</li>
 * </ul>
 * Un genérico no parte la referencia, así que no tiene lista de pieles: no
 * hay referencia de piel donde guardarla.
 */
@Service
public class NovedadesSms {

    /** Un color del bolso de un artículo nuevo, con los colores de sus pieles. */
    public record ColorArticulo(String clave, String colorPiel, String colorForro, List<String> coloresCombinacion) {
        public ColorArticulo {
            coloresCombinacion = List.copyOf(coloresCombinacion);
        }
    }

    /** Una referencia (modelo + piel) que el catálogo no tiene. */
    public record ArticuloNuevo(String referencia, String forro, List<String> combinaciones,
                                List<ColorArticulo> colores) {
        public ArticuloNuevo {
            forro = limpio(forro);
            combinaciones = combinaciones == null ? List.of()
                    : combinaciones.stream().map(NovedadesSms::limpio).filter(c -> !c.isEmpty()).limit(4).toList();
            colores = List.copyOf(colores);
        }

        ArticuloNuevo con(String otroForro, List<String> otras) {
            return new ArticuloNuevo(referencia, otroForro, otras, colores);
        }
    }

    /**
     * Un modelo nuevo, o uno que existe con referencias nuevas
     * ({@code existe}: entonces su nombre y su tipo no se tocan).
     */
    public record ModeloNuevo(String id, String codigo, boolean existe, String nombre, TipoArticulo tipo,
                              List<ArticuloNuevo> articulos, Imagen croquis, boolean entrado) {
        public ModeloNuevo {
            nombre = limpio(nombre);
            tipo = tipo == null ? TipoArticulo.BOLSO : tipo;
            articulos = List.copyOf(articulos);
        }

        ModeloNuevo yaEntrado() {
            return new ModeloNuevo(id, codigo, existe, nombre, tipo, articulos, croquis, true);
        }
    }

    public record PielNueva(String id, String refPiel, String nombre, boolean entrado) {
        public PielNueva {
            nombre = limpio(nombre);
        }
    }

    public record ColorNuevo(String id, String codigo, String descripcion, boolean entrado) {
        public ColorNuevo {
            descripcion = limpio(descripcion);
        }
    }

    /** Lo nuevo de un pedido de SMS, de un cliente. */
    public record Novedades(String cliente, List<ModeloNuevo> modelos, List<PielNueva> pieles,
                            List<ColorNuevo> colores) {
        public Novedades {
            modelos = List.copyOf(modelos);
            pieles = List.copyOf(pieles);
            colores = List.copyOf(colores);
        }

        public boolean vacia() {
            return modelos.isEmpty() && pieles.isEmpty() && colores.isEmpty();
        }

        public long pendientes() {
            return modelos.stream().filter(m -> !m.entrado()).count()
                    + pieles.stream().filter(p -> !p.entrado()).count()
                    + colores.stream().filter(c -> !c.entrado()).count();
        }

        public Optional<ModeloNuevo> modelo(String id) {
            return modelos.stream().filter(m -> m.id().equals(id)).findFirst();
        }

        public Optional<PielNueva> piel(String id) {
            return pieles.stream().filter(p -> p.id().equals(id)).findFirst();
        }

        public Optional<ColorNuevo> color(String id) {
            return colores.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        public Novedades con(ModeloNuevo otro) {
            return new Novedades(cliente, cambiar(modelos, m -> m.id().equals(otro.id()) ? otro : m), pieles, colores);
        }

        public Novedades con(PielNueva otra) {
            return new Novedades(cliente, modelos, cambiar(pieles, p -> p.id().equals(otra.id()) ? otra : p), colores);
        }

        public Novedades con(ColorNuevo otro) {
            return new Novedades(cliente, modelos, pieles, cambiar(colores, c -> c.id().equals(otro.id()) ? otro : c));
        }

        private static <T> List<T> cambiar(List<T> lista, UnaryOperator<T> cambio) {
            return lista.stream().map(cambio).toList();
        }
    }

    private final CatalogoModelos catalogo;
    private final Modelos modelos;
    private final MemoriaPieles memoriaPieles;
    private final TablaColores tablaColores;
    private final FotosCatalogo fotos;
    private final DocumentosCorteService reglas;

    public NovedadesSms(CatalogoModelos catalogo, Modelos modelos, MemoriaPieles memoriaPieles,
                        TablaColores tablaColores, FotosCatalogo fotos, DocumentosCorteService reglas) {
        this.catalogo = catalogo;
        this.modelos = modelos;
        this.memoriaPieles = memoriaPieles;
        this.tablaColores = tablaColores;
        this.fotos = fotos;
        this.reglas = reglas;
    }

    /** Lo que las líneas que han salido traen y el catálogo del cliente no tiene. */
    @Transactional(readOnly = true)
    public Novedades calcular(String cliente, List<LineaSms> lineas) {
        ClienteCorte regla = reglas.reglasDeProduccion(cliente);
        Map<String, List<LineaSms>> porModelo = new LinkedHashMap<>();
        Map<String, String> pielesLeidas = new LinkedHashMap<>();
        Map<String, String> coloresLeidos = new LinkedHashMap<>();
        for (LineaSms linea : lineas) {
            porModelo.computeIfAbsent(clave(linea.modelo()), m -> new ArrayList<>()).add(linea);
            ReferenciaCorte partida = regla.partir(linea.referencia());
            if (partida.tienePiel()) {
                pielesLeidas.merge(clave(partida.piel()), linea.pieles().principal().nombre(),
                        (antes, ahora) -> antes.isEmpty() ? ahora : antes);
            }
            if (!linea.color().codigo().isEmpty()) {
                coloresLeidos.merge(clave(linea.color().codigo()), linea.color().descripcion(),
                        (antes, ahora) -> antes.isEmpty() ? ahora : antes);
            }
        }

        List<ModeloNuevo> modelosNuevos = new ArrayList<>();
        for (Map.Entry<String, List<LineaSms>> entrada : porModelo.entrySet()) {
            Optional<Modelo> guardado = catalogo.modelo(cliente, entrada.getKey());
            List<ArticuloNuevo> articulos = articulosNuevos(cliente, entrada.getValue());
            if (guardado.isPresent() && articulos.isEmpty()) {
                continue;
            }
            LineaSms primera = entrada.getValue().get(0);
            Imagen croquis = entrada.getValue().stream().flatMap(l -> l.croquis().stream()).findFirst().orElse(null);
            modelosNuevos.add(new ModeloNuevo("m" + modelosNuevos.size(), entrada.getKey(), guardado.isPresent(),
                    guardado.map(Modelo::getNombre).orElse(entrada.getValue().stream().map(LineaSms::nombreModelo)
                            .filter(n -> !n.isBlank()).findFirst().orElse("")),
                    guardado.map(Modelo::getTipo).orElse(TipoArticulo.porReferencia(primera.referencia())),
                    articulos, guardado.isPresent() ? null : croquis, false));
        }

        Map<String, String> pielesGuardadas = new LinkedHashMap<>();
        for (Piel piel : memoriaPieles.pielesDe(cliente)) {
            pielesGuardadas.put(clave(piel.getRefPiel()), piel.getNombre() == null ? "" : piel.getNombre());
        }
        List<PielNueva> pielesNuevas = new ArrayList<>();
        pielesLeidas.forEach((ref, nombre) -> {
            String guardada = pielesGuardadas.get(ref);
            if (guardada == null || guardada.isBlank() && !nombre.isBlank()) {
                pielesNuevas.add(new PielNueva("p" + pielesNuevas.size(), ref, nombre, false));
            }
        });

        List<ColorNuevo> coloresNuevos = new ArrayList<>();
        coloresLeidos.forEach((codigo, descripcion) -> {
            Optional<ColorCliente> guardado = tablaColores.buscar(cliente, codigo);
            if (guardado.isEmpty() || vacio(guardado.get().getDescripcion()) && !descripcion.isBlank()) {
                coloresNuevos.add(new ColorNuevo("c" + coloresNuevos.size(), codigo, descripcion, false));
            }
        });
        return new Novedades(clave(cliente), modelosNuevos, pielesNuevas, coloresNuevos);
    }

    /** Las referencias de un modelo que el catálogo no tiene, con sus pieles y sus colores. */
    private List<ArticuloNuevo> articulosNuevos(String cliente, List<LineaSms> lineas) {
        Map<String, List<LineaSms>> porReferencia = new LinkedHashMap<>();
        for (LineaSms linea : lineas) {
            porReferencia.computeIfAbsent(clave(linea.referencia()), r -> new ArrayList<>()).add(linea);
        }
        List<ArticuloNuevo> nuevos = new ArrayList<>();
        porReferencia.forEach((referencia, deLaReferencia) -> {
            if (catalogo.articulo(cliente, referencia).isPresent()) {
                return;
            }
            PielesArticulo pieles = deLaReferencia.get(0).pieles();
            Map<String, ColorArticulo> colores = new LinkedHashMap<>();
            for (LineaSms linea : deLaReferencia) {
                PielesArticulo suyas = linea.pieles();
                colores.putIfAbsent(clave(linea.color().claveCatalogo()), new ColorArticulo(
                        clave(linea.color().claveCatalogo()), suyas.principal().color(), suyas.forro().color(),
                        suyas.combinaciones().stream().map(PielCorte::color).toList()));
            }
            nuevos.add(new ArticuloNuevo(referencia, pieles.forro().nombre(),
                    pieles.combinaciones().stream().map(PielCorte::nombre).toList(), List.copyOf(colores.values())));
        });
        return nuevos;
    }

    // --- Entrar en el sistema ---

    /**
     * Da de alta el modelo (o completa el que existe) con sus artículos nuevos.
     * Lanza IllegalArgumentException si un modelo nuevo no tiene nombre.
     */
    @Transactional
    public ModeloNuevo entrarModelo(String cliente, ModeloNuevo nuevo) {
        if (nuevo.entrado()) {
            return nuevo;
        }
        Optional<Modelo> guardado = catalogo.modelo(cliente, nuevo.codigo());
        Modelo modelo;
        boolean recienCreado = guardado.isEmpty();
        if (recienCreado) {
            if (nuevo.nombre().isEmpty()) {
                throw new IllegalArgumentException(nuevo.codigo() + ": falta el nombre del modelo");
            }
            modelo = modelos.darDeAlta(new Modelos.AltaManual(cliente, null, nuevo.codigo(), null, nuevo.nombre(),
                    nuevo.tipo(), Etapa.SMS), null);
        } else {
            modelo = guardado.get();
        }
        for (ArticuloNuevo articulo : nuevo.articulos()) {
            entrarArticulo(cliente, articulo);
        }
        if (recienCreado && nuevo.croquis() != null) {
            fotos.guardar(modelo.getId(), "croquis SMS.jpg", nuevo.croquis().jpeg());
        }
        return nuevo.yaEntrado();
    }

    /** Un artículo que alguien ha dado de alta entretanto no se toca. */
    private void entrarArticulo(String cliente, ArticuloNuevo nuevo) {
        if (catalogo.articulo(cliente, nuevo.referencia()).isPresent()) {
            return;
        }
        Articulo articulo = catalogo.asegurarArticulo(cliente, nuevo.referencia());
        // Lo que ha heredado de sus hermanos manda: solo se rellenan huecos.
        if (articulo.getForro().isEmpty() && !nuevo.forro().isEmpty()) {
            catalogo.guardarCampo(cliente, nuevo.referencia(), CampoArticulo.FORRO, nuevo.forro(), false);
        }
        boolean sinCombinaciones = articulo.getCombinaciones().isEmpty();
        List<CampoArticulo> casillas = List.of(CampoArticulo.COMBINACION1, CampoArticulo.COMBINACION2,
                CampoArticulo.COMBINACION3, CampoArticulo.COMBINACION4);
        if (sinCombinaciones) {
            for (int i = 0; i < nuevo.combinaciones().size(); i++) {
                catalogo.guardarCampo(cliente, nuevo.referencia(), casillas.get(i), nuevo.combinaciones().get(i),
                        false);
            }
        }
        List<CampoColor> coloresCombinacion = List.of(CampoColor.COLOR_COMBINACION1, CampoColor.COLOR_COMBINACION2,
                CampoColor.COLOR_COMBINACION3, CampoColor.COLOR_COMBINACION4);
        for (ColorArticulo color : nuevo.colores()) {
            if (color.clave().isEmpty()) {
                continue;
            }
            ArticuloColor guardado = catalogo.asegurarColor(cliente, nuevo.referencia(), color.clave());
            rellenar(cliente, nuevo.referencia(), color.clave(), CampoColor.COLOR_PIEL, guardado.getColorPiel(),
                    color.colorPiel());
            rellenar(cliente, nuevo.referencia(), color.clave(), CampoColor.COLOR_FORRO, guardado.getColorForro(),
                    color.colorForro());
            if (sinCombinaciones) {
                for (int i = 0; i < color.coloresCombinacion().size() && i < nuevo.combinaciones().size(); i++) {
                    rellenar(cliente, nuevo.referencia(), color.clave(), coloresCombinacion.get(i), "",
                            color.coloresCombinacion().get(i));
                }
            }
        }
    }

    private void rellenar(String cliente, String referencia, String clave, CampoColor campo, String guardado,
                          String leido) {
        if (vacio(guardado) && !vacio(leido)) {
            catalogo.guardarCampoColor(cliente, referencia, clave, campo, leido);
        }
    }

    /** El nombre de la piel; uno que alguien ya puso no se pisa. */
    @Transactional
    public PielNueva entrarPiel(String cliente, PielNueva nueva) {
        if (nueva.entrado()) {
            return nueva;
        }
        boolean conNombre = memoriaPieles.pielesDe(cliente).stream()
                .anyMatch(p -> clave(p.getRefPiel()).equals(clave(nueva.refPiel())) && !vacio(p.getNombre()));
        if (!conNombre) {
            memoriaPieles.guardarPiel(cliente, nueva.refPiel(), nueva.nombre().toUpperCase(Locale.ROOT));
        }
        return new PielNueva(nueva.id(), nueva.refPiel(), nueva.nombre(), true);
    }

    /** La descripción del color; una que alguien ya puso no se pisa. */
    @Transactional
    public ColorNuevo entrarColor(String cliente, ColorNuevo nuevo) {
        if (nuevo.entrado()) {
            return nuevo;
        }
        Optional<ColorCliente> guardado = tablaColores.buscar(cliente, nuevo.codigo());
        if (guardado.isEmpty() || vacio(guardado.get().getDescripcion())) {
            tablaColores.guardar(cliente, nuevo.codigo(), nuevo.descripcion());
        }
        return new ColorNuevo(nuevo.id(), nuevo.codigo(), nuevo.descripcion(), true);
    }

    /**
     * Todo lo pendiente, en una transacción: si un modelo nuevo no tiene
     * nombre no entra nada (IllegalArgumentException con cuál), como «Cargar
     * modelos al Sistema» de la carga de modelos.
     */
    @Transactional
    public Novedades entrarTodo(Novedades novedades) {
        String cliente = novedades.cliente();
        for (ModeloNuevo modelo : novedades.modelos()) {
            if (!modelo.entrado() && modelo.nombre().isEmpty() && catalogo.modelo(cliente, modelo.codigo()).isEmpty()) {
                throw new IllegalArgumentException(modelo.codigo() + ": falta el nombre del modelo. No ha entrado nada");
            }
        }
        Novedades resultado = novedades;
        for (ModeloNuevo modelo : novedades.modelos()) {
            resultado = resultado.con(entrarModelo(cliente, modelo));
        }
        for (PielNueva piel : novedades.pieles()) {
            resultado = resultado.con(entrarPiel(cliente, piel));
        }
        for (ColorNuevo color : novedades.colores()) {
            resultado = resultado.con(entrarColor(cliente, color));
        }
        return resultado;
    }

    private static String clave(String texto) {
        return texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT);
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
