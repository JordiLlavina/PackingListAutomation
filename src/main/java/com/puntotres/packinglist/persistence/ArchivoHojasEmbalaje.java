package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.model.DatosHojaEmbalaje;

/**
 * El archivo de hojas de «Info de embalaje»: guarda cada hoja generada y
 * responde a lo que la pantalla necesita saber de las anteriores (siguiente
 * número de envío, talleres usados, última hoja de un modelo).
 *
 * Se guarda el snapshot entero en JSON y no solo las columnas sueltas porque
 * una hoja antigua tiene que enseñar lo que de verdad se imprimió, aunque el
 * pedido o la memoria del artículo hayan cambiado después.
 */
@Service
@Transactional
public class ArchivoHojasEmbalaje {

    private final HojaEmbalajeRepository repositorio;
    private final ObjectMapper json;

    public ArchivoHojasEmbalaje(HojaEmbalajeRepository repositorio, ObjectMapper json) {
        this.repositorio = repositorio;
        this.json = json;
    }

    /**
     * Guarda la hoja. {@code temporadaId} es la temporada guardada de la que
     * salió el pedido, o null si el excel se subió a mano.
     */
    public HojaEmbalaje guardar(DatosHojaEmbalaje datos, Long temporadaId) {
        return repositorio.save(new HojaEmbalaje(datos.cliente(), datos.temporada(), temporadaId,
                datos.numeroEnvio(), nuloSiVacio(datos.nota()), nuloSiVacio(datos.taller()),
                datos.referencia(), datos.codigoColor(), nuloSiVacio(datos.codigoModelo()), serializar(datos)));
    }

    /** Todas las hojas, la más reciente primero, sin el snapshot. */
    @Transactional(readOnly = true)
    public List<ResumenHojaEmbalaje> todas() {
        return repositorio.findAllByOrderByFechaDesc().stream()
                .map(h -> new ResumenHojaEmbalaje(h.getId(), h.getFecha(), h.getCliente(), h.getTemporada(),
                        h.getNumeroEnvio(), h.getReferencia(), h.getCodigoColor(), h.getTaller()))
                .toList();
    }

    /** Los datos tal como se imprimieron. */
    @Transactional(readOnly = true)
    public Optional<DatosHojaEmbalaje> datosDe(Long id) {
        return hoja(id).map(h -> deserializar(h.getDatos()));
    }

    @Transactional(readOnly = true)
    public Optional<HojaEmbalaje> hoja(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id);
    }

    /** El contador único de Puntotres: el mayor número guardado más uno, o 1 si no hay ninguno. */
    @Transactional(readOnly = true)
    public int siguienteNumeroEnvio() {
        Integer maximo = repositorio.maximoNumeroEnvio();
        return maximo == null ? 1 : maximo + 1;
    }

    /** La última hoja del mismo cliente y modelo, de la que se prerrellenan bolsa, etiqueta e instrucción. */
    @Transactional(readOnly = true)
    public Optional<DatosHojaEmbalaje> ultimaDe(String cliente, String codigoModelo) {
        if (cliente == null || codigoModelo == null || codigoModelo.isBlank()) {
            return Optional.empty();
        }
        return repositorio.findFirstByClienteAndCodigoModeloOrderByFechaDesc(cliente, codigoModelo)
                .map(h -> deserializar(h.getDatos()));
    }

    /** Los talleres usados antes: distintos, sin vacíos y ordenados. */
    @Transactional(readOnly = true)
    public List<String> talleres() {
        return repositorio.talleres();
    }

    private String serializar(DatosHojaEmbalaje datos) {
        try {
            return json.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la hoja de embalaje", e);
        }
    }

    private DatosHojaEmbalaje deserializar(String texto) {
        try {
            return json.readValue(texto, DatosHojaEmbalaje.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer la hoja de embalaje guardada", e);
        }
    }

    private static String nuloSiVacio(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }
}
