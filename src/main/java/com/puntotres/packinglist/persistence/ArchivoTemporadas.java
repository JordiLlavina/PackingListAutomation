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
 * qué excel, para no volver a subirlo en cada envío.
 *
 * Hay dos clases de excel ({@link TipoExcelTemporada}): el de PEDIDO del
 * cliente (AMI, APC), que el packing list, las etiquetas y /colores leen como
 * pedido; y el de PRODUCCION de Puntotres, el de los clientes genéricos, que
 * solo saben leer el corte y el catálogo de modelos. Por eso hay dos
 * desplegables y dos formas de pedir una temporada "para el envío": los
 * sitios que leen el excel como pedido no deben ver una de producción.
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

    /** Todas las de un cliente, de los dos tipos. */
    public List<ResumenTemporada> de(String cliente) {
        return cliente == null ? List.of() : repositorio.resumenesDe(cliente);
    }

    /** Solo las de tipo PEDIDO de un cliente: las que /colores puede leer. */
    public List<ResumenTemporada> dePedido(String cliente) {
        return de(cliente).stream().filter(ArchivoTemporadas::esDePedido).toList();
    }

    /** Agrupadas por clave de cliente, para el desplegable de la entrada. */
    public Map<String, List<ResumenTemporada>> porCliente() {
        return agrupar(ArchivoTemporadas::esDePedido);
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
     * las pantallas que leen el excel como PEDIDO (packing list y etiquetas
     * de artículo): id, nombre, fichero y tipo. Van al navegador como un mapa
     * por cliente porque el desplegable se rehace al cambiar de cliente sin
     * volver al servidor. Una sola copia para todas las pantallas: con una
     * en cada controlador, un dato nuevo llega a unas y a otras no.
     */
    public Map<String, List<Map<String, String>>> paraElDesplegable() {
        return paraElDesplegable(ArchivoTemporadas::esDePedido);
    }

    /**
     * Lo mismo con TODAS las temporadas, también las de producción: el corte
     * sabe leer los dos excels (y con el de producción la temporada solo pone
     * el nombre). Cada entrada lleva "tipo" para que la pantalla aparte las
     * de producción cuando el origen elegido es el excel de pedido.
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
                datos.put("fichero", temporada.nombreFichero());
                datos.put("tipo", temporada.tipoExcel().name());
                lista.add(datos);
            }
            porCliente.put(cliente, lista);
        });
        return porCliente;
    }

    /** La temporada con su excel, para descargarla o editarla. */
    public Optional<TemporadaGuardada> conFichero(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id);
    }

    /**
     * La temporada elegida en la pantalla de entrada, solo si es de ese
     * cliente, sea del tipo que sea. Vale para quien solo necesita su NOMBRE
     * (el corte con el excel de producción); quien va a leer el excel como
     * pedido usa {@link #conPedidoParaElEnvio}.
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
     * Como {@link #paraElEnvio}, y además solo si su excel es el de PEDIDO del
     * cliente: un excel de producción leído como pedido no daría error, daría
     * un documento plausible y equivocado.
     */
    public Optional<TemporadaGuardada> conPedidoParaElEnvio(Long id, String cliente) {
        return paraElEnvio(id, cliente)
                .filter(temporada -> temporada.getTipoExcel() == TipoExcelTemporada.PEDIDO);
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

    /** Alta o corrección de una temporada de PEDIDO; ver la versión con tipo. */
    @Transactional
    public TemporadaGuardada guardar(Long id, String cliente, String temporada,
                                     String nombreFichero, byte[] excel) {
        return guardar(id, cliente, temporada, nombreFichero, excel, TipoExcelTemporada.PEDIDO);
    }

    /**
     * Alta o corrección. Un {@code id} nulo da de alta; un {@code excel} nulo
     * conserva el que ya tuviera (se editó el nombre, no el fichero). El tipo
     * lo fija el cliente (con pedido-cliente, PEDIDO; si no, PRODUCCION) y
     * sigue al cliente también al editar.
     */
    @Transactional
    public TemporadaGuardada guardar(Long id, String cliente, String temporada,
                                     String nombreFichero, byte[] excel, TipoExcelTemporada tipo) {
        Optional<TemporadaGuardada> existente = conFichero(id);
        if (existente.isPresent()) {
            existente.get().actualizar(cliente, temporada, nombreFichero, excel, tipo);
            return existente.get();
        }
        return repositorio.save(new TemporadaGuardada(cliente, temporada, nombreFichero, excel, tipo));
    }

    @Transactional
    public void borrar(Long id) {
        if (id != null) {
            repositorio.deleteById(id);
        }
    }

    private static boolean esDePedido(ResumenTemporada temporada) {
        return temporada.tipoExcel() == TipoExcelTemporada.PEDIDO;
    }
}
