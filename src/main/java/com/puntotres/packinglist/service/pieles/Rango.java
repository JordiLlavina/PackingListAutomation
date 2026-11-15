package com.puntotres.packinglist.service.pieles;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un rango de una medida de la piel (el grueso en mm, la superficie en m²),
 * tal como se teclea en UNA casilla: "1,2" es un valor fijo y "1,1-1,3" un
 * rango. Una casilla y no dos porque así se escribe en una ficha técnica y es
 * la mitad de trabajo al dar de alta.
 *
 * Admite coma o punto decimal, guion o "a" entre los dos números y la unidad
 * detrás ("1,1 - 1,3 mm", "4 a 5 m2"). Un rango escrito al revés se ordena.
 * Vacío es "no se sabe"; cualquier otra cosa es un error que se le dice a
 * quien teclea, nunca un número adivinado.
 */
public record Rango(Double minimo, Double maximo) {

    public static final Rango VACIO = new Rango(null, null);

    private static final String NUMERO = "(\\d+(?:[.,]\\d+)?)";
    private static final Pattern FORMA = Pattern.compile(
            "^" + NUMERO + "(?:\\s*(?:-|–|—|a|/)\\s*" + NUMERO + ")?\\s*(?:mm|m2|m²)?$",
            Pattern.CASE_INSENSITIVE);

    public static Rango de(String texto) {
        if (texto == null || texto.isBlank()) {
            return VACIO;
        }
        Matcher forma = FORMA.matcher(texto.trim());
        if (!forma.matches()) {
            throw new IllegalArgumentException("«" + texto.trim() + "» no es un valor ni un rango: "
                    + "escribe 1,2 o 1,1-1,3.");
        }
        double primero = numero(forma.group(1));
        double segundo = forma.group(2) == null ? primero : numero(forma.group(2));
        return new Rango(Math.min(primero, segundo), Math.max(primero, segundo));
    }

    public boolean vacio() {
        return minimo == null && maximo == null;
    }

    /** "1,2" o "1,1-1,3", como se tecleó; vacío si no se sabe. */
    public String texto() {
        Double desde = minimo != null ? minimo : maximo;
        Double hasta = maximo != null ? maximo : minimo;
        if (desde == null) {
            return "";
        }
        return desde.equals(hasta) ? formato(desde) : formato(desde) + "-" + formato(hasta);
    }

    /** ¿Tiene algo en común con el rango pedido? Un extremo vacío del filtro no limita. */
    public boolean solapa(Double desde, Double hasta) {
        if (vacio()) {
            return false;
        }
        double mio = minimo != null ? minimo : maximo;
        double mia = maximo != null ? maximo : minimo;
        return (hasta == null || mio <= hasta) && (desde == null || mia >= desde);
    }

    /** Un número con coma decimal y sin ceros de sobra: 1,2 · 40 · 0,85. */
    public static String formato(Double valor) {
        if (valor == null) {
            return "";
        }
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es")))
                .format(valor);
    }

    private static double numero(String texto) {
        return Double.parseDouble(texto.replace(',', '.'));
    }
}
