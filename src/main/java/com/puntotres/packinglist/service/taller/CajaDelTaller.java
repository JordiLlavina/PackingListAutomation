package com.puntotres.packinglist.service.taller;

import java.util.Comparator;
import java.util.List;

/**
 * Lo que la hoja del taller dice de la caja de UNA referencia: qué cartón,
 * cuántas unidades le caben y cuánto pesa llena.
 *
 * Es un dato por referencia y no por fila, y ahí está todo el asunto. El
 * taller escribe la misma referencia en varias filas —un color por fila, una
 * destinación por fila— y cada una lleva su peso y su cartón, pero
 * <b>no todas esas cajas van llenas</b>: la última de un color lleva lo que
 * sobra. La caja que describe a la referencia es la de la fila con
 * <b>más unidades por caja</b> ("QTITE / COLIS"), que es la única que está
 * llena de verdad.
 *
 * El peso y la medida salen de la <b>misma fila</b>, no cada uno de la suya:
 * son dos propiedades del mismo bulto, y cruzarlos pondría el peso de una
 * caja de 60x60x40 en una de 60x40x30.
 *
 * Una fila sin peso no es candidata a darlo, pero su medida sigue valiendo:
 * un cartón escrito sin pesar es un dato bueno, solo que incompleto.
 *
 * @param medidaCaja         cartón tal como lo escribe el taller, sin normalizar
 * @param unidadesPorCaja    el tope de la referencia: las de una caja llena
 * @param pesoBrutoKg        lo que pesa esa caja, con el cartón
 * @param unidadesDeLaPesada unidades por caja de la fila de la que se ha
 *                           sacado el peso; si es menor que
 *                           {@code unidadesPorCaja}, el peso es de una caja a
 *                           medias y hay que avisar en vez de creérselo
 */
public record CajaDelTaller(String medidaCaja, Integer unidadesPorCaja, Double pesoBrutoKg,
                            Integer unidadesDeLaPesada) {

    /** Lo que sabe una hoja que no trae ninguna de las dos columnas nuevas. */
    public static final CajaDelTaller VACIA = new CajaDelTaller(null, null, null, null);

    /** De más unidades por caja a menos; las filas que no lo dicen, al final. */
    private static final Comparator<LineaTaller> DE_MAS_LLENA_A_MENOS =
            Comparator.comparing(LineaTaller::unidadesPorCajaTaller,
                    Comparator.nullsLast(Comparator.reverseOrder()));

    /**
     * Lee las filas de UNA referencia. El orden de las filas no cambia el
     * resultado salvo en los empates, donde gana la primera: es la que el
     * taller escribió antes.
     */
    public static CajaDelTaller de(List<LineaTaller> lineasDeLaReferencia) {
        LineaTaller pesada = lineasDeLaReferencia.stream()
                .filter(linea -> esPositivo(linea.pesoBrutoCajaKg()))
                .min(DE_MAS_LLENA_A_MENOS)
                .orElse(null);
        LineaTaller conMedida = lineasDeLaReferencia.stream()
                .filter(linea -> !enBlanco(linea.medidaCajaTaller()))
                .min(DE_MAS_LLENA_A_MENOS)
                .orElse(null);

        // La medida es la de la fila pesada, y solo si esa no la trae se coge
        // de otra: el peso y el cartón tienen que ser del mismo bulto.
        String medida = pesada != null && !enBlanco(pesada.medidaCajaTaller())
                ? pesada.medidaCajaTaller()
                : (conMedida == null ? null : conMedida.medidaCajaTaller());

        return new CajaDelTaller(medida, tope(lineasDeLaReferencia),
                pesada == null ? null : pesada.pesoBrutoCajaKg(),
                pesada == null ? null : pesada.unidadesPorCajaTaller());
    }

    /** Las unidades de la caja más llena que haya escrito el taller. */
    private static Integer tope(List<LineaTaller> lineas) {
        return lineas.stream()
                .map(LineaTaller::unidadesPorCajaTaller)
                .filter(unidades -> unidades != null && unidades > 0)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    /** La hoja no ha dicho nada de la caja de esta referencia. */
    public boolean noDiceNada() {
        return medidaCaja == null && unidadesPorCaja == null && pesoBrutoKg == null;
    }

    private static boolean esPositivo(Double peso) {
        return peso != null && peso > 0;
    }

    private static boolean enBlanco(String texto) {
        return texto == null || texto.isBlank();
    }
}
