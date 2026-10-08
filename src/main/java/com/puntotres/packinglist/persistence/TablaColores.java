package com.puntotres.packinglist.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La Tabla colores Clientes: la descripción de cada código de color, por
 * cliente ("APC" + "LZZ" = "NOIR").
 *
 * Existe porque el pedido de APC solo trae el código y el cortador necesita
 * leer el color. La pareja código-descripción no cambia nunca, así que se
 * teclea una vez en la pantalla /colores y vale para siempre. AMI no la
 * necesita —su pedido trae el Libellé coloris— y solo se consultaría si un
 * pedido suyo llegara sin él: lo que trae el pedido manda.
 *
 * Un código que llega en un pedido y no está en la tabla se AÑADE con la
 * descripción vacía (y quien carga el pedido lo ve en un aviso), para que
 * aparezca en la pantalla listo para rellenar en vez de tener que teclearlo.
 */
@Service
public class TablaColores {

    /**
     * Lo que se mete la primera vez que arranca la aplicación con la tabla
     * vacía: los nueve códigos de color del pedido real de APC
     * (APC_PEDIDO_FALL26.xlsx, columna Couleurs). Solo LZZ lleva descripción,
     * la que dio el usuario; las demás no se saben y no se inventan.
     */
    static final Map<String, Map<String, String>> SEMILLA = Map.of("APC", semillaApc());

    private static Map<String, String> semillaApc() {
        Map<String, String> colores = new LinkedHashMap<>();
        for (String codigo : List.of("CAB", "CAE", "CAW", "GAE", "GAU", "KAN", "KBE", "LAW")) {
            colores.put(codigo, "");
        }
        colores.put("LZZ", "NOIR");
        return colores;
    }

    /** Lo que se sabe de unos códigos: su descripción (vacía si no la tienen) y los recién añadidos. */
    public record Consulta(Map<String, String> descripciones, List<String> anadidos) {

        public Consulta {
            descripciones = Collections.unmodifiableMap(new LinkedHashMap<>(descripciones));
            anadidos = List.copyOf(anadidos);
        }

        /** Los códigos que siguen sin descripción, en el orden en que se preguntaron. */
        public List<String> sinDescripcion() {
            return descripciones.entrySet().stream()
                    .filter(entrada -> entrada.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }

    private final ColorClienteRepository repositorio;

    public TablaColores(ColorClienteRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Los colores de un cliente, por código. */
    public List<ColorCliente> de(String cliente) {
        if (vacio(cliente)) {
            return List.of();
        }
        return repositorio.findByClienteOrderByCodigo(clave(cliente));
    }

    public Optional<ColorCliente> buscar(String cliente, String codigo) {
        if (vacio(cliente) || vacio(codigo)) {
            return Optional.empty();
        }
        return repositorio.findById(new ColorClienteId(clave(cliente), clave(codigo)));
    }

    /** Alta o corrección de un color. El código se guarda en mayúsculas y sin espacios alrededor. */
    @Transactional
    public void guardar(String cliente, String codigo, String descripcion) {
        if (vacio(cliente) || vacio(codigo)) {
            throw new IllegalArgumentException("El color necesita cliente y código");
        }
        ColorClienteId id = new ColorClienteId(clave(cliente), clave(codigo));
        repositorio.findById(id).ifPresentOrElse(
                color -> color.actualizar(descripcion),
                () -> repositorio.save(new ColorCliente(id.cliente(), id.codigo(), descripcion)));
    }

    /** Devuelve si había algo que borrar. */
    @Transactional
    public boolean borrar(String cliente, String codigo) {
        Optional<ColorCliente> color = buscar(cliente, codigo);
        color.ifPresent(repositorio::delete);
        return color.isPresent();
    }

    /**
     * La descripción de cada código, añadiendo vacíos los que no estén. Se
     * llama al cargar un pedido, solo con los códigos que el pedido no
     * describe.
     */
    @Transactional
    public Consulta consultar(String cliente, Collection<String> codigos) {
        Map<String, String> descripciones = new LinkedHashMap<>();
        List<String> anadidos = new ArrayList<>();
        if (vacio(cliente)) {
            return new Consulta(descripciones, anadidos);
        }
        String elCliente = clave(cliente);
        for (String codigo : codigos) {
            if (vacio(codigo) || descripciones.containsKey(clave(codigo))) {
                continue;
            }
            String elCodigo = clave(codigo);
            Optional<ColorCliente> guardado = repositorio.findById(new ColorClienteId(elCliente, elCodigo));
            if (guardado.isEmpty()) {
                repositorio.save(new ColorCliente(elCliente, elCodigo, ""));
                anadidos.add(elCodigo);
            }
            descripciones.put(elCodigo, guardado.map(ColorCliente::getDescripcion).orElse(""));
        }
        return new Consulta(descripciones, anadidos);
    }

    /**
     * Mete en la tabla los colores de un excel de pedido (código →
     * descripción, vacía si el pedido no la trae). Un código que no estaba se
     * añade con la descripción del pedido. Uno que ya estaba NO se toca —lo
     * de la tabla lo ha tecleado alguien—, salvo que estuviera sin
     * descripción y el pedido la traiga: rellenar un hueco no pisa nada.
     */
    @Transactional
    public Incorporacion incorporar(String cliente, Map<String, String> colores) {
        List<String> anadidos = new ArrayList<>();
        List<String> completados = new ArrayList<>();
        List<String> yaEstaban = new ArrayList<>();
        if (vacio(cliente)) {
            return new Incorporacion(anadidos, completados, yaEstaban);
        }
        String elCliente = clave(cliente);
        colores.forEach((codigo, descripcion) -> {
            if (vacio(codigo)) {
                return;
            }
            String elCodigo = clave(codigo);
            String laDescripcion = descripcion == null ? "" : descripcion.trim();
            Optional<ColorCliente> guardado = repositorio.findById(new ColorClienteId(elCliente, elCodigo));
            if (guardado.isEmpty()) {
                repositorio.save(new ColorCliente(elCliente, elCodigo, laDescripcion));
                anadidos.add(elCodigo);
            } else if (guardado.get().getDescripcion().isEmpty() && !laDescripcion.isEmpty()) {
                guardado.get().actualizar(laDescripcion);
                completados.add(elCodigo);
            } else if (!anadidos.contains(elCodigo) && !completados.contains(elCodigo)
                    && !yaEstaban.contains(elCodigo)) {
                yaEstaban.add(elCodigo);
            }
        });
        return new Incorporacion(anadidos, completados, yaEstaban);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void sembrarSiEstaVacia() {
        if (repositorio.count() > 0) {
            return;
        }
        SEMILLA.forEach((cliente, colores) -> colores.forEach((codigo, descripcion) ->
                repositorio.save(new ColorCliente(cliente, codigo, descripcion))));
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** Las claves se teclean o se leen de un excel: "lzz " y "LZZ" son el mismo color. */
    private static String clave(String texto) {
        return texto.trim().toUpperCase(Locale.ROOT);
    }
}
