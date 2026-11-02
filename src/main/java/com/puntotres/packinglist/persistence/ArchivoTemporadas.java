package com.puntotres.packinglist.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El archivo de temporadas: qué temporadas hay guardadas de cada cliente y con
 * qué excels, para no volver a subirlos en cada envío.
 *
 * Una temporada guarda hasta dos excels: el de PEDIDO del cliente (AMI, APC),
 * que el packing list, las etiquetas y /colores leen como pedido, y el de
 * PRODUCCION de Puntotres (obligatorio en los genéricos, opcional en AMI y
 * APC), que leen el corte y el catálogo de modelos. Por eso hay dos
 * desplegables y varias formas de pedir una temporada "para el envío": los
 * sitios que leen el excel como pedido no deben ver una sin pedido.
 */
@Service
public class ArchivoTemporadas {

    private final TemporadaGuardadaRepository repositorio;

    public ArchivoTemporadas(TemporadaGuardadaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Todas, ordenadas por cliente y temporada, sin los excels. */
    public List<ResumenTemporada> todas() {
        return repositorio.resumenes();
    }

    /** Todas las de un cliente, tengan los excels que tengan. */
    public List<ResumenTemporada> de(String cliente) {
        return cliente == null ? List.of() : repositorio.resumenesDe(cliente);
    }

    /** Solo las que tienen excel de pedido: las que /colores puede leer. */
    public List<ResumenTemporada> dePedido(String cliente) {
        return de(cliente).stream().filter(ResumenTemporada::conPedido).toList();
    }

    /** Las que tienen excel de pedido, agrupadas por clave de cliente. */
    public Map<String, List<ResumenTemporada>> porCliente() {
        return agrupar(ResumenTemporada::conPedido);
    }

    private Map<String, List<ResumenTemporada>> agrupar(Predicate<ResumenTemporada> filtro) {
        Map<String, List<ResumenTemporada>> agrupadas = new LinkedHashMap<>();
        for (ResumenTemporada temporada : todas()) {
            if (filtro.test(temporada)) {
                agrupadas.computeIfAbsent(temporada.cliente(), clave -> new ArrayList<>())
                        .add(temporada);
            }
        }
        return agrupadas;
    }

    /**
     * Las temporadas de cada cliente tal como las necesita el desplegable de
     * las pantallas que leen el excel como PEDIDO (packing list, etiquetas de
     * artículo, info de embalaje): solo las que lo tienen. Van al navegador
     * como un mapa por cliente porque el desplegable se rehace al cambiar de
     * cliente sin volver al servidor. Una sola copia para todas las
     * pantallas: con una en cada controlador, un dato nuevo llega a unas y a
     * otras no.
     */
    public Map<String, List<Map<String, String>>> paraElDesplegable() {
        return paraElDesplegable(ResumenTemporada::conPedido);
    }

    /**
     * Lo mismo con TODAS las temporadas, también las que no tienen pedido: el
     * corte sabe leer los dos excels. Cada entrada lleva "pedido" y
     * "produccion" ("true"/"false"), que dicen qué excels guarda, para que la
     * pantalla aparte las que no sirven con el origen elegido y esconda el
     * campo del fichero que ya está guardado.
     */
    public Map<String, List<Map<String, String>>> paraElDesplegableDelCorte() {
        return paraElDesplegable(temporada -> true);
    }

    private Map<String, List<Map<String, String>>> paraElDesplegable(Predicate<ResumenTemporada> filtro) {
        Map<String, List<Map<String, String>>> porCliente = new LinkedHashMap<>();
        agrupar(filtro).forEach((cliente, temporadas) -> {
            List<Map<String, String>> lista = new ArrayList<>();
            for (ResumenTemporada temporada : temporadas) {
                Map<String, String> datos = new LinkedHashMap<>();
                datos.put("id", String.valueOf(temporada.id()));
                datos.put("temporada", temporada.temporada());
                datos.put("fichero", temporada.conPedido() ? temporada.nombreFicheroPedido() : "");
                datos.put("ficheroProduccion",
                        temporada.conProduccion() ? temporada.nombreFicheroProduccion() : "");
                datos.put("pedido", String.valueOf(temporada.conPedido()));
                datos.put("produccion", String.valueOf(temporada.conProduccion()));
                lista.add(datos);
            }
            porCliente.put(cliente, lista);
        });
        return porCliente;
    }

    /** La temporada con sus excels, para descargarlos o editarla. */
    public Optional<TemporadaGuardada> conFichero(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id);
    }

    /**
     * La temporada elegida en la pantalla de entrada, solo si es de ese
     * cliente, tenga los excels que tenga. Vale para quien solo necesita su
     * NOMBRE; quien va a leer un excel usa {@link #conPedidoParaElEnvio} o
     * {@link #conProduccionParaElEnvio}.
     *
     * La comprobación del cliente no es paranoia: el desplegable se rellena en
     * el navegador y cambiar de cliente después de haber elegido temporada
     * dejaría enviado el id de una temporada de otro. Sin este filtro, el
     * envío se generaría con el excel de pedido del cliente equivocado, que es
     * exactamente el error que no se ve hasta que el packing list llega mal.
     */
    public Optional<TemporadaGuardada> paraElEnvio(Long id, String cliente) {
        return conFichero(id)
                .filter(temporada -> temporada.getCliente().equals(cliente));
    }

    /**
     * Como {@link #paraElEnvio}, y además solo si guarda el excel de PEDIDO
     * del cliente: una temporada de un genérico no lo tiene, y leer en su
     * lugar el de producción como pedido no daría error, daría un documento
     * plausible y equivocado.
     */
    public Optional<TemporadaGuardada> conPedidoParaElEnvio(Long id, String cliente) {
        return paraElEnvio(id, cliente).filter(TemporadaGuardada::tienePedido);
    }

    /** Como {@link #paraElEnvio}, y además solo si guarda el excel de producción de Puntotres. */
    public Optional<TemporadaGuardada> conProduccionParaElEnvio(Long id, String cliente) {
        return paraElEnvio(id, cliente).filter(TemporadaGuardada::tieneProduccion);
    }

    /**
     * ¿Hay ya otra temporada con ese nombre para el cliente? Sin distinguir
     * mayúsculas: "H26" y "h26" son la misma temporada y tener las dos deja
     * dos entradas indistinguibles en el desplegable.
     */
    public boolean yaExiste(String cliente, String temporada, Long exceptoId) {
        return repositorio.findByClienteAndTemporadaIgnoreCase(cliente, temporada)
                .filter(existente -> !existente.getId().equals(exceptoId))
                .isPresent();
    }

    /** Alta o corrección con solo el excel de pedido; ver la versión con los dos. */
    @Transactional
    public TemporadaGuardada guardar(Long id, String cliente, String temporada,
                                     String nombreFichero, byte[] excel) {
        return guardar(id, cliente, temporada,
                excel == null ? null : new FicheroExcel(nombreFichero, excel), null);
    }

    /**
     * Alta o corrección. Un {@code id} nulo da de alta; un fichero nulo
     * conserva el que ya tuviera (se editó el nombre, o solo se adjuntó el
     * otro excel). Qué excel es obligatorio lo comprueba quien llama: depende
     * del cliente.
     */
    @Transactional
    public TemporadaGuardada guardar(Long id, String cliente, String temporada,
                                     FicheroExcel pedido, FicheroExcel produccion) {
        TemporadaGuardada guardada = conFichero(id)
                .orElseGet(() -> new TemporadaGuardada(cliente, temporada));
        guardada.renombrar(cliente, temporada);
        guardada.sustituirPedido(pedido);
        guardada.sustituirProduccion(produccion);
        return repositorio.save(guardada);
    }

    /** Quita el excel de pedido: la temporada ha pasado a un cliente que no tiene. */
    @Transactional
    public void quitarPedido(Long id) {
        conFichero(id).ifPresent(TemporadaGuardada::quitarPedido);
    }

    @Transactional
    public void borrar(Long id) {
        if (id != null) {
            repositorio.deleteById(id);
        }
    }
}
