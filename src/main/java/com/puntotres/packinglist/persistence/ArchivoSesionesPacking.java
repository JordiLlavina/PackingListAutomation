package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * El archivo de sesiones del asistente de packing list: cada envío que llega
 * a la revisión se guarda aquí y se va actualizando, para poder volver a él
 * días después desde «Cargar Sesión».
 *
 * Mismo patrón que {@link ArchivoHojasProduccion}: el envío entero en JSON y
 * las columnas sueltas solo para listar.
 */
@Service
@Transactional
public class ArchivoSesionesPacking {

    /** Una sesión cargada: el envío y el excel de pedido con el que se importó. */
    public record SesionCargada(Long id, SesionPackingDatos datos,
                                byte[] excelPedido, String nombreExcelPedido) {
    }

    private final SesionPackingRepository repositorio;
    private final ObjectMapper json;

    public ArchivoSesionesPacking(SesionPackingRepository repositorio, ObjectMapper json) {
        this.repositorio = repositorio;
        // Copia propia: una sesión guardada con una versión anterior del
        // programa puede traer un campo que ya no existe, y eso no puede
        // impedir cargarla.
        this.json = json.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Guarda el envío: actualiza la sesión {@code id} o, si es null o ya no
     * existe (se borró desde otra pestaña), crea una nueva. Devuelve el id de
     * la sesión en la que ha quedado.
     *
     * El excel de pedido solo se escribe al crear: no cambia durante la
     * revisión.
     */
    public Long guardar(Long id, String facturas, String destinos, SesionPackingDatos datos,
                        byte[] excelPedido, String nombreExcelPedido) {
        SesionPacking sesion = id == null ? null : repositorio.findById(id).orElse(null);
        if (sesion == null) {
            sesion = new SesionPacking(datos.cabecera().getClaveCliente());
            sesion.setExcelPedido(excelPedido, nombreExcelPedido);
        }
        sesion.actualizar(recortar(datos.cabecera().getTemporada(), 60), recortar(facturas, 400),
                recortar(destinos, 1000), serializar(datos));
        return repositorio.save(sesion).getId();
    }

    /** Todas las sesiones, la más reciente primero, sin el envío. */
    @Transactional(readOnly = true)
    public List<ResumenSesionPacking> todas() {
        return repositorio.resumenes();
    }

    @Transactional(readOnly = true)
    public Optional<SesionCargada> cargar(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id).map(sesion -> new SesionCargada(
                sesion.getId(), deserializar(sesion.getDatos()),
                sesion.getExcelPedido(), sesion.getNombreExcelPedido()));
    }

    /** Borra la sesión y devuelve su cliente, o vacío si ya no estaba. */
    public Optional<String> eliminar(Long id) {
        Optional<SesionPacking> sesion = id == null ? Optional.empty() : repositorio.findById(id);
        sesion.ifPresent(repositorio::delete);
        return sesion.map(SesionPacking::getCliente);
    }

    private String serializar(SesionPackingDatos datos) {
        try {
            return json.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la sesión del packing list", e);
        }
    }

    private SesionPackingDatos deserializar(String texto) {
        try {
            return json.readValue(texto, SesionPackingDatos.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer la sesión guardada", e);
        }
    }

    /** null si no hay texto, y nunca más largo que su columna: el resumen no puede tumbar el guardado. */
    private static String recortar(String texto, int maximo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo - 1) + "…";
    }
}
