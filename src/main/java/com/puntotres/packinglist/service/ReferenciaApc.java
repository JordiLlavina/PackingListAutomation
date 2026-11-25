package com.puntotres.packinglist.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La referencia de APC es <b>REF.PIEL-REF.ARTÍCULO</b> ("PXCBC-F67008"): así
 * la escribe su excel de pedido y así va en el packing list. La operaria a
 * veces la escribe al revés, artículo-piel ("F67008-PXCBC"), y entonces no es
 * sufijo de ningún {@code Article} del pedido: no se encuentra el número de
 * pedido y la revisión enseña una referencia que no es la del cliente.
 *
 * <p>La forma invertida se reconoce sin adivinar, porque las dos mitades no se
 * parecen (medido en los pedidos reales de Fall 26 y Spring 27): la piel son
 * solo letras ({@code PXCBC}, {@code COHIZ}) y el artículo lleva cifras, con
 * una letra delante o sin ella ({@code F67008}, {@code H65077},
 * {@code 67043}). Se exigen al menos cuatro letras en la piel para no tomar
 * por piel las siglas sueltas que el operario pone detrás del código
 * ({@code 67043 SJ}), que no son parte de la referencia.
 *
 * <p>Lo que no tiene esa forma se devuelve tal cual: un recorte ("67043") lo
 * completa después el excel de pedido, y una referencia de otro cliente no es
 * cosa de esta clase.
 */
public final class ReferenciaApc {

    /** Artículo (letra opcional + cifras), separador y piel (solo letras). */
    private static final Pattern AL_REVES = Pattern.compile(
            "([A-Z]?\\d{4,})\\s*(?:-|\\s)\\s*([A-Z]{4,})");

    private ReferenciaApc() {
    }

    /**
     * "F67008-PXCBC" → "PXCBC-F67008", también en minúsculas, con espacios o
     * sin el guion. Cualquier otra cosa sale como llegó.
     */
    public static String normalizar(String referencia) {
        if (referencia == null) {
            return null;
        }
        Matcher alReves = AL_REVES.matcher(referencia.trim().toUpperCase(Locale.ROOT));
        if (!alReves.matches()) {
            return referencia;
        }
        return alReves.group(2) + "-" + alReves.group(1);
    }
}
