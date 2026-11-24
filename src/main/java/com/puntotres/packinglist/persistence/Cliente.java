package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;

/**
 * Un cliente de plantilla genérica, dado de alta desde /clientes.
 *
 * La clave es la que guardan todas las demás tablas en su columna
 * {@code cliente} (temporadas, modelos, colores, pieles, hojas de embalaje),
 * así que no cambia nunca: se saca del nombre al dar de alta y, aunque luego
 * se corrija el nombre, la clave se queda. Cambiarla dejaría huérfano todo lo
 * guardado del cliente.
 */
@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @Column(name = "clave", length = 60)
    private String clave;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "nombre_legal", nullable = false, length = 200)
    private String nombreLegal;

    @Column(name = "direccion_entrega", nullable = false, length = 500)
    private String direccionEntrega;

    @Column(name = "placeholder_temporada", length = 20)
    private String placeholderTemporada;

    @Column(name = "etiqueta_pedido", length = 60)
    private String etiquetaPedido;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected Cliente() {
        // Constructor para JPA.
    }

    public Cliente(String clave, String nombre, String nombreLegal, String direccionEntrega,
                   String placeholderTemporada, String etiquetaPedido) {
        this.clave = clave;
        actualizar(nombre, nombreLegal, direccionEntrega, placeholderTemporada, etiquetaPedido);
    }

    /** Corrige los datos del cliente; la clave no se toca. */
    public void actualizar(String nombre, String nombreLegal, String direccionEntrega,
                           String placeholderTemporada, String etiquetaPedido) {
        this.nombre = nombre;
        this.nombreLegal = nombreLegal;
        this.direccionEntrega = direccionEntrega;
        this.placeholderTemporada = placeholderTemporada;
        this.etiquetaPedido = etiquetaPedido;
        this.fechaActualizacion = LocalDateTime.now();
    }

    /** La configuración con la que lo ven el resto de pantallas y el builder genérico. */
    public ClienteConfig comoConfig() {
        ClienteConfig config = new ClienteConfig();
        config.setNombre(nombre);
        config.setPlantilla(TipoPlantilla.GENERIC);
        config.setNombreLegal(nombreLegal);
        config.setDireccionEntrega(direccionEntrega);
        config.setPlaceholderTemporada(placeholderTemporada);
        if (etiquetaPedido != null) {
            config.setEtiquetaPedido(etiquetaPedido);
        }
        return config;
    }

    public String getClave() {
        return clave;
    }

    public String getNombre() {
        return nombre;
    }

    public String getNombreLegal() {
        return nombreLegal;
    }

    public String getDireccionEntrega() {
        return direccionEntrega;
    }

    public String getPlaceholderTemporada() {
        return placeholderTemporada;
    }

    public String getEtiquetaPedido() {
        return etiquetaPedido;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
