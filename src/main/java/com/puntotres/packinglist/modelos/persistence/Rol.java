package com.puntotres.packinglist.modelos.persistence;

/**
 * Qué cara de la app de modelos ve un usuario. El TALLER da avisos y hace las
 * órdenes; el DESPACHO las da y aplica los avisos; el ADMIN es un despacho que
 * además gestiona usuarios y dispositivos.
 */
public enum Rol {
    TALLER, DESPACHO, ADMIN;

    /** DESPACHO o ADMIN: los que ven la cara del despacho. */
    public boolean esDespacho() {
        return this == DESPACHO || this == ADMIN;
    }
}
