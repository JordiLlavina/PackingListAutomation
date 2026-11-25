package com.puntotres.packinglist.service.produccion;

/**
 * Cuánto tiene que medir una fila para que un texto quepa entero en su celda
 * con ajuste de línea. Excel no recalcula la altura de una fila escrita por
 * POI, así que un texto largo se corta a la altura que tenga la fila; aquí se
 * estima cuántas líneas ocupará y se le da a la fila esa altura (petición del
 * usuario: «las celdas han de estar ajustadas al texto»).
 *
 * Es una estimación, con margen: cuenta los caracteres de cada párrafo con un
 * ancho medio de letra algo mayor que el real de Arial en mayúsculas, que es
 * como se escribe la hoja, para errar hacia una fila algo más alta y nunca
 * hacia una línea escondida.
 */
final class AlturaFila {

    /** Píxeles por carácter de ancho de columna (Calibri 11, 96 ppp): la unidad del ancho de columna de Excel. */
    private static final double PIXELES_POR_CARACTER_COLUMNA = 7;
    /** El relleno que Excel añade a cada columna, más el de la celda a cada lado. */
    private static final double PIXELES_RELLENO = 5 + 6;
    /** Ancho medio de una letra de Arial en mayúsculas, en «em» (la mayúscula media mide ~0,62; con margen). */
    private static final double EM_POR_LETRA = 0.66;
    private static final double PIXELES_POR_PUNTO = 96.0 / 72.0;
    /** Interlineado de Excel para Arial. */
    private static final double INTERLINEADO = 1.3;
    /** Aire de arriba y abajo de la celda, en puntos. */
    private static final double RELLENO_VERTICAL_PT = 8;

    private AlturaFila() {
    }

    /** Las líneas que ocupará el texto en una columna de ese ancho (unidades de Excel, 1/256 de carácter). */
    static int lineas(String texto, int anchoColumna, int puntosLetra) {
        if (texto == null || texto.isEmpty()) {
            return 1;
        }
        double anchoUtil = anchoColumna / 256.0 * PIXELES_POR_CARACTER_COLUMNA - PIXELES_RELLENO;
        double pixelesPorLetra = EM_POR_LETRA * puntosLetra * PIXELES_POR_PUNTO;
        double letrasPorLinea = Math.max(1, Math.floor(anchoUtil / pixelesPorLetra));
        int lineas = 0;
        for (String parrafo : texto.split("\n", -1)) {
            lineas += Math.max(1, (int) Math.ceil(parrafo.length() / letrasPorLinea));
        }
        return lineas;
    }

    /** La altura en puntos para ese número de líneas de esa letra. */
    static double puntos(int lineas, int puntosLetra) {
        return lineas * puntosLetra * INTERLINEADO + RELLENO_VERTICAL_PT;
    }

    /** La altura de una celda: sus líneas a su letra. */
    static double puntos(String texto, int anchoColumna, int puntosLetra) {
        return puntos(lineas(texto, anchoColumna, puntosLetra), puntosLetra);
    }
}
