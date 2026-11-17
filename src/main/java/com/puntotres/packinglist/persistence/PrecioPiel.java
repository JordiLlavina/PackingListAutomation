package com.puntotres.packinglist.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un tramo de precio de una {@link FichaPiel}: desde cuántos m² y a cuántos
 * €/m² (siempre €/m², decisión del usuario). Son los precios VIGENTES: al
 * llegar una oferta nueva se sustituyen todos. El tramo de menor mínimo es el
 * MOQ de la piel, y un mínimo del que aún no se sabe el precio lleva el precio
 * vacío; un precio sin mínimo (el de stock service) lleva el mínimo vacío.
 */
@Entity
@Table(name = "precio_piel")
public class PrecioPiel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ficha_piel_id", nullable = false)
    private Long fichaPielId;

    @Column(name = "moq_m2")
    private Double moqM2;

    @Column(name = "precio_eur_m2")
    private Double precioEurM2;

    protected PrecioPiel() {
        // Constructor para JPA.
    }

    public PrecioPiel(Long fichaPielId, Double moqM2, Double precioEurM2) {
        this.fichaPielId = fichaPielId;
        this.moqM2 = moqM2;
        this.precioEurM2 = precioEurM2;
    }

    public Long getId() { return id; }
    public Long getFichaPielId() { return fichaPielId; }
    public Double getMoqM2() { return moqM2; }
    public Double getPrecioEurM2() { return precioEurM2; }
}
