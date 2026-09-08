package com.puntotres.packinglist.service.taller;

import java.util.List;

/**
 * Un artículo del taller con todo lo que hace falta para empaquetarlo, ya
 * cruzado con el pedido del cliente y con lo que el usuario haya corregido en
 * la pantalla de ajuste.
 *
 * El artículo es referencia + color + <b>talla</b>. La talla forma parte de la
 * identidad porque en cinturones cada talla es un artículo distinto, con su
 * propio código de barras y su propia cantidad pedida; en bolsos es siempre
 * "U" y no estorba.
 *
 * {@code recibido} es lo que ha mandado el taller; {@code objetivos}, lo que
 * pide el cliente. Casi nunca coinciden, y de ahí sale todo el reparto.
 *
 * {@code unidadesPorCaja} puede ser null: significa "todavía no se sabe", y es
 * lo que impide generar hasta que alguien lo teclee. Nunca se sustituye por un
 * cero ni por un valor de relleno.
 */
public record FilaAjustada(
        String referencia,
        String color,
        String talla,
        int recibido,
        String medidaCaja,
        Integer unidadesPorCaja,
        Double pesoBrutoKg,
        List<ObjetivoDestino> objetivos) {

    public FilaAjustada {
        objetivos = List.copyOf(objetivos);
    }

    /** Sin peso bruto declarado: los pesos se rellenan en la revisión. */
    public FilaAjustada(String referencia, String color, String talla, int recibido,
                        String medidaCaja, Integer unidadesPorCaja,
                        List<ObjetivoDestino> objetivos) {
        this(referencia, color, talla, recibido, medidaCaja, unidadesPorCaja, null, objetivos);
    }

    /** Cómo se nombra este artículo en un aviso dirigido a una persona. */
    public String descripcion() {
        return referencia + " " + color + ("U".equals(talla) ? "" : " talla " + talla);
    }
}
