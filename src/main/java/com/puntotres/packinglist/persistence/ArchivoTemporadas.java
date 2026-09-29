package com.puntotres.packinglist.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El archivo de temporadas: qué temporadas hay guardadas de cada cliente y con
 * qué excel de pedido, para no volver a subirlo en cada envío.
 *
 * Lo consumen la pantalla que las mantiene (/temporadas) y las dos vías de
 * entrada del asistente, que sacan de aquí el excel cuando el usuario elige
 * una temporada en vez de subir un fichero.
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

    public List<ResumenTemporada> de(String cliente) {
        return cliente == null ? List.of() : repositorio.resumenesDe(cliente);
    }

    /** Agrupadas por clave de cliente, para el desplegable de la entrada. */
    public Map<String, List<ResumenTemporada>> porCliente() {
        Map<String, List<ResumenTemporada>> agrupadas = new LinkedHashMap<>();
        for (ResumenTemporada temporada : todas()) {
            agrupadas.computeIfAbsent(temporada.cliente(), clave -> new ArrayList<>())
                    .add(temporada);
        }
        return agrupadas;
    }

    /**
     * Las temporadas de cada cliente tal como las necesita el desplegable de
     * las pantallas de entrada (packing list, documentos del corte y
     * etiquetas de artículo): id, nombre y fichero. Van al navegador como un
     * mapa por cliente porque el desplegable se rehace al cambiar de cliente
     * sin volver al servidor. Una sola copia para las tres pantallas: con una
     * en cada controlador, un dato nuevo llega a unas y a otras no.
     */
    public Map<String, List<Map<String, String>>> paraElDesplegable() {
        Map<String, List<Map<String, String>>> porCliente = new LinkedHashMap<>();
        porCliente().forEach((cliente, temporadas) -> {
            List<Map<String, String>> lista = new ArrayList<>();
            for (ResumenTemporada temporada : temporadas) {
                Map<String, String> datos = new LinkedHashMap<>();
                datos.put("id", String.valueOf(temporada.id()));
                datos.put("temporada", temporada.temporada());
                datos.put("fichero", temporada.nombreFichero());
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
     * cliente.
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
     * ¿Hay ya otra temporada con ese nombre para el cliente? Sin distinguir
     * mayúsculas: "H26" y "h26" son la misma temporada y tener las dos deja
     * dos entradas indistinguibles en el desplegable.
     */
    public boolean yaExiste(String cliente, String temporada, Long exceptoId) {
        return repositorio.findByClienteAndTemporadaIgnoreCase(cliente, temporada)
                .filter(existente -> !existente.getId().equals(exceptoId))
                .isPresent();
    }

    /**
     * Alta o corrección. Un {@code id} nulo da de alta; un {@code excel} nulo
     * conserva el que ya tuviera (se editó el nombre, no el fichero).
     */
    @Transactional
    public TemporadaGuardada guardar(Long id, String cliente, String temporada,
                                     String nombreFichero, byte[] excel) {
        Optional<TemporadaGuardada> existente = conFichero(id);
        if (existente.isPresent()) {
            existente.get().actualizar(cliente, temporada, nombreFichero, excel);
            return existente.get();
        }
        return repositorio.save(new TemporadaGuardada(cliente, temporada, nombreFichero, excel));
    }

    @Transactional
    public void borrar(Long id) {
        if (id != null) {
            repositorio.deleteById(id);
        }
    }
}
