package com.puntotres.packinglist.persistence;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un paso del recorrido de una piel, apuntado a mano: en tal fecha, con tal
 * cliente, pasó tal cosa ("2026-03-12 · APC · Mostrada"). Es lo que permite
 * saber que una piel que usó AMI se le enseñó después a APC y cuándo.
 *
 * El modelo es TEXTO ("PROTO B", "ULL027") y no un enlace al catálogo: al
 * juntar en la app de modelos un prototipo con su referencia, un enlace se
 * perdería sin avisar, y con el texto se sabe lo mismo.
 */
@Entity
@Table(name = "evento_piel")
public class EventoPiel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ficha_piel_id", nullable = false)
    private Long fichaPielId;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "cliente", length = 60, nullable = false)
    private String cliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoEventoPiel tipo;

    @Column(name = "modelo", length = 200)
    private String modelo;

    @Column(name = "nota", length = 2000)
    private String nota;

    protected EventoPiel() {
        // Constructor para JPA.
    }

    public EventoPiel(Long fichaPielId, LocalDate fecha, String cliente, TipoEventoPiel tipo, String modelo,
                      String nota) {
        this.fichaPielId = fichaPielId;
        this.fecha = fecha;
        this.cliente = cliente;
        this.tipo = tipo;
        this.modelo = modelo;
        this.nota = nota;
    }

    public Long getId() { return id; }
    public Long getFichaPielId() { return fichaPielId; }
    public LocalDate getFecha() { return fecha; }
    public String getCliente() { return cliente; }
    public TipoEventoPiel getTipo() { return tipo; }
    public String getModelo() { return modelo == null ? "" : modelo; }
    public String getNota() { return nota == null ? "" : nota; }
}
