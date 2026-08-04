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

    /**
     * El cliente trabaja con un excel de pedido que el usuario sube en la
     * pantalla de entrada (APC lo usa para completar el nº de pedido; AMI
     * solo lo guarda para reutilizarlo en las etiquetas). Los demás clientes
     * no ven ese campo.
     */
    private boolean pedidoCliente;

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

    public boolean isPedidoCliente() {
        return pedidoCliente;
    }

    public void setPedidoCliente(boolean pedidoCliente) {
        this.pedidoCliente = pedidoCliente;
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

    /** Un destino del catálogo resuelto desde su nombre o el de una hija suya. */
    public record DestinoResuelto(String nombrePadre, DestinoClienteConfig config) {
    }

    /**
     * Destino del catálogo bajo el que va la destinación indicada: ella misma
     * si es una clave del catálogo, o su padre si es una hija. Se mira primero
     * como clave para que un destino que además se lista como hija de sí mismo
     * (WHOLESALE) se resuelva a sí mismo sin recorrer nada.
     */
    public Optional<DestinoResuelto> destinoPadrePara(String destino) {
        if (destino == null) {
            return Optional.empty();
        }
        String buscado = normalizar(destino);
        DestinoClienteConfig directo = destinos.get(buscado);
        if (directo != null) {
            return Optional.of(new DestinoResuelto(buscado, directo));
        }
        for (Map.Entry<String, DestinoClienteConfig> entrada : destinos.entrySet()) {
            for (String hija : entrada.getValue().getDestinosHijo()) {
                if (normalizar(hija).equals(buscado)) {
                    return Optional.of(new DestinoResuelto(entrada.getKey(), entrada.getValue()));
                }
            }
        }
        return Optional.empty();
    }

    static String normalizar(String clave) {
        return clave.trim().toUpperCase();
    }
}
