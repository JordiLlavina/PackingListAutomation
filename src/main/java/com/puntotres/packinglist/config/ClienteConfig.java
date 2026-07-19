package com.puntotres.packinglist.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuración estática de un cliente cargada desde application.yml
 * (ver {@link ClientesProperties}). Añadir un cliente nuevo de plantilla
 * genérica = añadir su bloque al yml, sin tocar Java.
 *
 * No todos los campos aplican a todas las plantillas: nombreLegal y
 * direccionEntrega los usa la plantilla GENERIC; destinos con dirección
 * los usa APC; AMI solo lista sus destinos conocidos a título informativo.
 */
public class ClienteConfig {

    private String nombre;
    private TipoPlantilla plantilla;
    private String placeholderTemporada;
    private String nombreLegal;
    private String direccionEntrega;
    private Map<String, DestinoClienteConfig> destinos = new LinkedHashMap<>();

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public TipoPlantilla getPlantilla() {
        return plantilla;
    }

    public void setPlantilla(TipoPlantilla plantilla) {
        this.plantilla = plantilla;
    }

    public String getPlaceholderTemporada() {
        return placeholderTemporada;
    }

    public void setPlaceholderTemporada(String placeholderTemporada) {
        this.placeholderTemporada = placeholderTemporada;
    }

    public String getNombreLegal() {
        return nombreLegal;
    }

    public void setNombreLegal(String nombreLegal) {
        this.nombreLegal = nombreLegal;
    }

    public String getDireccionEntrega() {
        return direccionEntrega;
    }

    public void setDireccionEntrega(String direccionEntrega) {
        this.direccionEntrega = direccionEntrega;
    }

    public Map<String, DestinoClienteConfig> getDestinos() {
        return destinos;
    }

    public void setDestinos(Map<String, DestinoClienteConfig> destinos) {
        this.destinos = new LinkedHashMap<>();
        destinos.forEach((clave, destino) -> this.destinos.put(normalizar(clave), destino));
    }

    /**
     * Configuración del destino indicado, o vacío si el cliente no lo tiene.
     * La clave se normaliza igual que al cargar el yml para que "ivry " de
     * una imagen case con "IVRY" de la configuración.
     */
    public Optional<DestinoClienteConfig> destinoPara(String destino) {
        if (destino == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(destinos.get(normalizar(destino)));
    }

    static String normalizar(String clave) {
        return clave.trim().toUpperCase();
    }
}
