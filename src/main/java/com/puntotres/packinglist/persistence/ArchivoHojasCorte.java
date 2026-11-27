package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.model.DatosHojaCorte;

/**
 * El archivo de Hojas de corte: guarda cada hoja generada y la devuelve para
 * bajarla otra vez o importarla al formulario. Mismo patrón que
 * {@link ArchivoHojasProduccion}, sin numeración: la hoja de corte no sale de
 * casa.
 */
@Service
@Transactional
public class ArchivoHojasCorte {

    private final HojaCorteRepository repositorio;
    private final ObjectMapper json;

    public ArchivoHojasCorte(HojaCorteRepository repositorio, ObjectMapper json) {
        this.repositorio = repositorio;
        // Una hoja guardada con una versión anterior tiene que seguir leyéndose.
        this.json = json.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** {@code foto}: la subida en el formulario (JPEG ya reducido), o null si usa la del catálogo. */
    public HojaCorte guardar(DatosHojaCorte datos, byte[] foto) {
        return repositorio.save(new HojaCorte(datos.cliente(), nuloSiVacio(datos.temporada()),
                nuloSiVacio(datos.modelo()), nuloSiVacio(datos.nombreBolso()), datos.lineasConCantidad().size(),
                serializar(datos), foto == null || foto.length == 0 ? null : foto));
    }

    @Transactional(readOnly = true)
    public List<ResumenHojaCorte> todas() {
        return repositorio.resumenes();
    }

    @Transactional(readOnly = true)
    public Optional<DatosHojaCorte> datosDe(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id).map(h -> deserializar(h.getDatos()));
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> fotoDe(Long id) {
        return id == null ? Optional.empty() : repositorio.fotoDe(id);
    }

    private String serializar(DatosHojaCorte datos) {
        try {
            return json.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la hoja de corte", e);
        }
    }

    private DatosHojaCorte deserializar(String texto) {
        try {
            return json.readValue(texto, DatosHojaCorte.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer la hoja de corte guardada", e);
        }
    }

    private static String nuloSiVacio(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }
}
