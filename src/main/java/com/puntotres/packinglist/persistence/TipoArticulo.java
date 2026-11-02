package com.puntotres.packinglist.persistence;

import com.puntotres.packinglist.model.CajaData;

/**
 * Lo que es un modelo: un bolso o un cinturón.
 *
 * Es INFORMATIVO: lo enseña y lo edita la pantalla del catálogo, pero no lo
 * lee ningún generador. La plantilla del packing de AMI, que un bolso y un
 * cinturón no compartan caja y que el corte deje fuera los cinturones siguen
 * decidiéndose con la regla del prefijo UBL ({@link CajaData#esCinturon}),
 * que no necesita base de datos y corre también en Main.java y en los tests
 * sin Spring. Que este campo gobierne eso será otra feature.
 */
public enum TipoArticulo {
    BOLSO, CINTURON;

    /** El valor con el que nace un modelo: la regla del prefijo UBL, la misma del packing. */
    public static TipoArticulo porReferencia(String referencia) {
        return CajaData.esCinturon(referencia) ? CINTURON : BOLSO;
    }
}
