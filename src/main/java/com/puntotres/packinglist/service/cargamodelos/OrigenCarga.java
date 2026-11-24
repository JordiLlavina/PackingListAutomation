package com.puntotres.packinglist.service.cargamodelos;

import com.puntotres.packinglist.persistence.Etapa;

/**
 * De dónde salen los modelos de una carga. Lo elige el usuario con los
 * botones de arriba de /carga-modelos.
 */
public enum OrigenCarga {

    /** Una temporada guardada: su excel de pedido del cliente (AMI y APC). */
    TEMPORADA(Etapa.PRODUCCION, true),
    /** El excel de pedido de SMS del cliente (de momento solo APC tiene lector). */
    SMS(Etapa.SMS, true),
    /** El excel de producción de Puntotres: todos los clientes. */
    PRODUCCION(Etapa.PRODUCCION, true),
    /** Una carpeta de carpetas: cada subcarpeta es un modelo. */
    GRUPO(Etapa.PROTOTIPO, false),
    /** A mano, de uno en uno. */
    MANUAL(Etapa.PROTOTIPO, false);

    private final Etapa etapaDePartida;
    private final boolean conExcel;

    OrigenCarga(Etapa etapaDePartida, boolean conExcel) {
        this.etapaDePartida = etapaDePartida;
        this.conExcel = conExcel;
    }

    /** La etapa con la que nace un modelo NUEVO de este origen; la de uno que ya existe no se toca. */
    public Etapa etapaDePartida() {
        return etapaDePartida;
    }

    /** Los modelos salen de un excel: se puede descargar su esqueleto de carpetas. */
    public boolean conExcel() {
        return conExcel;
    }
}
