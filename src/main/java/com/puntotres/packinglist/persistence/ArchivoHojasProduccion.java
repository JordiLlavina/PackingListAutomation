package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.model.DatosHojaProduccion;

/**
 * El archivo de Hojas de Producción: guarda cada hoja generada y responde a
 * lo que la pantalla necesita saber de las anteriores (siguiente número de
 * cada taller, talleres usados).
 *
 * Mismo patrón que {@link ArchivoHojasEmbalaje}: el snapshot entero en JSON y
 * las columnas sueltas solo para listar y filtrar. Cada taller lleva su propio
 * contador (decisión del usuario): Sweet Bag va por la 325 y Adrian por la 12.
 */
@Service
@Transactional
public class ArchivoHojasProduccion {

    private final HojaProduccionRepository repositorio;
    private final ObjectMapper json;

    public ArchivoHojasProduccion(HojaProduccionRepository repositorio, ObjectMapper json) {
        this.repositorio = repositorio;
        this.json = json;
    }

    /** Guarda la hoja sin foto propia: al regenerarla se usa la del catálogo. */
    public HojaProduccion guardar(DatosHojaProduccion datos) {
        return guardar(datos, null);
    }

    /**
     * Guarda la hoja. {@code foto} es la subida en el formulario (JPEG ya
     * reducido) o null si la hoja usa la principal del modelo en el catálogo.
     */
    public HojaProduccion guardar(DatosHojaProduccion datos, byte[] foto) {
        HojaProduccion hoja = new HojaProduccion(datos.cliente(), nuloSiVacio(datos.temporada()),
                nuloSiVacio(datos.modelo()), nuloSiVacio(datos.nombreBolso()), datos.taller(), datos.numero(),
                datos.fechaSalida(), serializar(datos));
        hoja.setFoto(foto == null || foto.length == 0 ? null : foto);
        return repositorio.save(hoja);
    }

    /** Todas las hojas, la más reciente primero, sin el snapshot ni la foto. */
    @Transactional(readOnly = true)
    public List<ResumenHojaProduccion> todas() {
        return repositorio.resumenes();
    }

    /** La foto subida con la hoja, o vacío si usa la del catálogo. */
    @Transactional(readOnly = true)
    public Optional<byte[]> fotoDe(Long id) {
        return id == null ? Optional.empty() : repositorio.fotoDe(id);
    }

    /** Los datos tal como se imprimieron. */
    @Transactional(readOnly = true)
    public Optional<DatosHojaProduccion> datosDe(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id).map(h -> deserializar(h.getDatos()));
    }

    /**
     * El siguiente número de hoja de un taller: su mayor número guardado más
     * uno, o 1 si no tiene ninguna. El taller se compara recortado y sin
     * mayúsculas ({@link #claveTaller}); un taller vacío empieza en 1.
     */
    @Transactional(readOnly = true)
    public int siguienteNumero(String taller) {
        String clave = claveTaller(taller);
        if (clave.isEmpty()) {
            return 1;
        }
        Integer maximo = repositorio.maximoNumeroDe(clave);
        return maximo == null ? 1 : maximo + 1;
    }

    /**
     * El siguiente número de cada taller con hojas, por su {@link #claveTaller}.
     * Va entero a la pantalla, que propone el número al teclear el taller sin
     * volver al servidor.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> siguientesPorTaller() {
        Map<String, Integer> siguientes = new TreeMap<>();
        for (Object[] fila : repositorio.maximosPorTaller()) {
            if (fila[1] != null) {
                siguientes.merge(claveTaller((String) fila[0]), ((Number) fila[1]).intValue() + 1, Math::max);
            }
        }
        return siguientes;
    }

    /** El taller tal como se compara: recortado y en mayúsculas, sin quitar acentos. */
    public static String claveTaller(String taller) {
        return taller == null ? "" : taller.trim().toUpperCase(Locale.ROOT);
    }

    /** Los talleres usados antes: distintos, sin vacíos y ordenados. */
    @Transactional(readOnly = true)
    public List<String> talleres() {
        return repositorio.talleres();
    }

    private String serializar(DatosHojaProduccion datos) {
        try {
            return json.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la hoja de producción", e);
        }
    }

    private DatosHojaProduccion deserializar(String texto) {
        try {
            return json.readValue(texto, DatosHojaProduccion.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer la hoja de producción guardada", e);
        }
    }

    private static String nuloSiVacio(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }
}
