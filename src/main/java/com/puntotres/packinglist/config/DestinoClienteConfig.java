package com.puntotres.packinglist.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Datos fijos de un destino de un cliente: el nombre legal del receptor y
 * la dirección de entrega que van en la cabecera del packing list.
 * Solo los usan las plantillas que imprimen dirección por destino (APC).
 *
 * {@code abreviatura} y {@code destinos-hijo} solo los usa APC: ver
 * {@code ResolutorDestinosPadre} y {@code LivraisonCode}.
 */
public class DestinoClienteConfig {

    private String nombreCliente;
    private String direccion;

    /**
     * Abreviatura del destino en el Livraison code de APC ("WH", "RT"...).
     * Null si no está configurada: entonces se usa el nombre del destino en
     * mayúsculas y sin espacios, con aviso.
     */
    private String abreviatura;

    /**
     * Destinaciones que el cliente agrupa bajo este destino y que se tratan
     * como si fueran él: dirección, nombre de fichero y hoja son los del
     * padre, y la hija solo sobrevive en la columna DESTINATION de su línea.
     * Vacía = este destino no agrupa a nadie.
     */
    private List<String> destinosHijo = new ArrayList<>();

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getAbreviatura() {
        return abreviatura;
    }

    public void setAbreviatura(String abreviatura) {
        this.abreviatura = abreviatura;
    }

    public List<String> getDestinosHijo() {
        return destinosHijo;
    }

    public void setDestinosHijo(List<String> destinosHijo) {
        this.destinosHijo = (destinosHijo == null) ? new ArrayList<>() : new ArrayList<>(destinosHijo);
    }
}
