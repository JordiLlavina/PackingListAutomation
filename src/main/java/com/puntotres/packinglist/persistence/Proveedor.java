package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un proveedor de pieles de la biblioteca. Además de sus datos, lleva SU
 * valoración: la responsabilidad (calidad del servicio, cuánto tarda en
 * responder) y la entrega a tiempo son del proveedor y no de cada piel, y son
 * una sola nota que se va corrigiendo, sin histórico (decisión del usuario).
 * La calidad sí es de cada piel y vive en {@link FichaPiel}.
 */
@Entity
@Table(name = "proveedor")
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nombre", length = 200, nullable = false)
    private String nombre;

    @Column(name = "contacto", length = 200)
    private String contacto;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "telefono", length = 60)
    private String telefono;

    @Column(name = "pais", length = 80)
    private String pais;

    @Column(name = "condiciones", length = 1000)
    private String condiciones;

    @Column(name = "responsabilidad")
    private Integer responsabilidad;

    @Column(name = "entrega_a_tiempo")
    private Integer entregaATiempo;

    @Column(name = "nota_valoracion", length = 2000)
    private String notaValoracion;

    @Column(name = "fecha_valoracion")
    private LocalDateTime fechaValoracion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected Proveedor() {
        // Constructor para JPA.
    }

    public Proveedor(String nombre) {
        this.nombre = nombre;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getContacto() { return texto(contacto); }
    public String getEmail() { return texto(email); }
    public String getTelefono() { return texto(telefono); }
    public String getPais() { return texto(pais); }
    public String getCondiciones() { return texto(condiciones); }
    public Integer getResponsabilidad() { return responsabilidad; }
    public Integer getEntregaATiempo() { return entregaATiempo; }
    public String getNotaValoracion() { return texto(notaValoracion); }
    public LocalDateTime getFechaValoracion() { return fechaValoracion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }

    public void setNombre(String nombre) { this.nombre = nombre; tocar(); }
    public void setContacto(String contacto) { this.contacto = contacto; tocar(); }
    public void setEmail(String email) { this.email = email; tocar(); }
    public void setTelefono(String telefono) { this.telefono = telefono; tocar(); }
    public void setPais(String pais) { this.pais = pais; tocar(); }
    public void setCondiciones(String condiciones) { this.condiciones = condiciones; tocar(); }

    public void setResponsabilidad(Integer responsabilidad) {
        this.responsabilidad = responsabilidad;
        valorar();
    }

    public void setEntregaATiempo(Integer entregaATiempo) {
        this.entregaATiempo = entregaATiempo;
        valorar();
    }

    public void setNotaValoracion(String notaValoracion) {
        this.notaValoracion = notaValoracion;
        valorar();
    }

    /** La valoración lleva su propia fecha: dice de cuándo es la nota, no cuándo se cambió el teléfono. */
    private void valorar() {
        this.fechaValoracion = LocalDateTime.now();
        tocar();
    }

    private void tocar() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
