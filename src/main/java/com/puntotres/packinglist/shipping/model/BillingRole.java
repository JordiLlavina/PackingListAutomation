package com.puntotres.packinglist.shipping.model;

/** Para qué puede usarse una cuenta DHL: remitente, pagadora del transporte, pagadora de aranceles. */
public enum BillingRole {
    SHIPPER("Remitente"), PAYER("Transporte"), DUTIES("Aranceles");

    private final String etiqueta;
    BillingRole(String etiqueta) { this.etiqueta = etiqueta; }
    public String getEtiqueta() { return etiqueta; }
}
