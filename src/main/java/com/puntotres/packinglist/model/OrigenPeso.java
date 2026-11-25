package com.puntotres.packinglist.model;

/**
 * De dónde ha salido el peso de una caja física (lo lleva su línea líder).
 *
 * Hace falta saberlo por dos cosas: la revisión pinta en gris lo que viene
 * del catálogo, para que se vea qué no se ha pesado en este envío; y al
 * generar solo se aprende de lo MEDIDO, o el programa se aprendería sus
 * propias estimaciones y un peso equivocado se perpetuaría solo.
 *
 * Una caja con peso y sin origen es MEDIDA: es lo que llega en el JSON o la
 * foto, o del packing del taller, antes de que la inferencia lo toque.
 */
public enum OrigenPeso {

    /** Tecleado en la revisión o llegado con el envío: alguien lo ha pesado. */
    MEDIDO,

    /** Calculado con el peso por unidad de cajas pesadas de este mismo envío. */
    INFERIDO,

    /** Calculado (al menos en parte) con el peso por unidad guardado en el catálogo. */
    CATALOGO
}
