package com.puntotres.packinglist.modelos.persistence;

/**
 * La prioridad de una modificación, de más a menos urgente (el orden de las
 * constantes es el de la lista). La pone siempre el despacho: obligatoria en
 * sus órdenes y nula en los avisos del taller hasta que la pone.
 */
public enum Prioridad {
    ALTA, NORMAL, BAJA
}
