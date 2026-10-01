package com.puntotres.packinglist.service.etiquetas;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Coordenadas (0-based de POI) de las dos hojas de la plantilla de una
 * destinación de APC (client-labels/apc-etiquetas-*.xlsx). La hoja de cajas
 * trae UN par de etiquetas modelo apilado en vertical (= una hoja A4): la
 * caja i-ésima se escribe desplazada i*alturaBloque y la segunda etiqueta
 * del par a +offsetSegundaEtiqueta. Los valores van en la columna C.
 *
 * <p>filaLivraison: en WH CROSSLOG y en RETAIL es la fila "ASN N°" (esas
 * plantillas no tienen Livraison); recibe el mismo valor.
 *
 * <p>filaDestino: la fila del valor de "DESTINATION", SOLO en las plantillas
 * donde ese valor es el nombre de la destinación (WHOLESALE y RETAIL, que
 * llevan destinaciones hijas). Ahí se reescribe cuando una caja lleva
 * material de varias hijas ("WHOLESALE / AUSTRALIA"). En JAPAN, KOREA y USA
 * es null: su DESTINATION es un aeropuerto o una ciudad ("TOKYO", "JFK") y
 * no se toca nunca.
 *
 * <p>Las cinco plantillas están maquetadas cada una por su lado, así que
 * NADA de esto se puede compartir entre destinaciones: ni el alto del
 * bloque, ni dónde cae cada campo, ni la geometría de la hoja de palet. La
 * segunda etiqueta del par suele estar recortada por abajo respecto de la
 * primera (por eso alturaBloque no es 2*offsetSegundaEtiqueta), y eso es
 * correcto: el bloque se copia entero, recorte incluido.
 *
 * <p>NO cambiar estas coordenadas sin revisar la plantilla, y viceversa:
 * ApcEtiquetaLayoutTest las ancla contra los ficheros reales.
 */
record ApcEtiquetaLayout(
        String rutaPlantilla, String hojaCajas, String hojaPalet,
        int alturaBloque, int offsetSegundaEtiqueta,
        int filaOrder, int filaLivraison, int filaReferencia, int filaColor,
        int filaTalla, int filaPiezas, int filaColisage, int filaPeso,
        Integer filaDestino, Palet palet) {

    /**
     * Geometría de la hoja de etiquetas de palet, que también es distinta en
     * cada plantilla. colValor es casi siempre la columna C, pero en
     * WHOLESALE el cliente trabaja sin la columna A y los valores caen en la
     * B: escribir en la C dejaría la etiqueta con el número de cajas y el
     * peso fuera del recuadro, en una columna que ni siquiera se imprime.
     *
     * colNumeroPalet es la última columna del área de impresión: el "Nº3"
     * del palet va en la esquina superior derecha de su etiqueta, en la
     * primera fila del bloque. Por el mismo motivo es la B en WHOLESALE y la
     * D en las demás.
     *
     * filaDestino es la del valor de "DESTINATION", con la misma regla que
     * la de la hoja de cajas: solo en WHOLESALE y RETAIL, donde nombra la
     * destinación y se reescribe cuando el palet lleva cajas de varias hijas;
     * null en las demás, donde es un aeropuerto.
     */
    record Palet(int altura, int filaNumCajas, int filaPeso, int colValor, int colNumeroPalet,
                 Integer filaDestino) {
    }

    /** Columna C: los valores de la hoja de cajas en las cinco plantillas. */
    public static final int COL_VALOR = 2;

    public int colValor() {
        return COL_VALOR;
    }

    /** La D: última columna del área de impresión de las hojas de palet con columna A. */
    private static final int COL_D = 3;

    public static final ApcEtiquetaLayout JAPAN = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-japan.xlsx",
            "Etiquette colis Bolloré ", "Etiquette Palette Bolloré",
            36, 18, 8, 9, 10, 11, 12, 13, 16, 17, null,
            new Palet(14, 10, 11, COL_VALOR, COL_D, null));

    public static final ApcEtiquetaLayout KOREA = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-korea.xlsx",
            "Etiquette colis FC Logistique", "Etiquette Palette FC logistique",
            36, 19, 7, 8, 9, 10, 11, 12, 15, 16, null,
            new Palet(14, 10, 11, COL_VALOR, COL_D, null));

    /**
     * filaReferencia = 11 y no 12: en esta plantilla la celda de valor de
     * Reference está COMBINADA (C12:C13 en 1-based), así que su rótulo cae
     * una fila por debajo de la celda que hay que escribir. Escribir en la
     * fila del rótulo no da error ni celda vacía: Excel ignora lo escrito en
     * una celda tapada por una combinación y se queda a la vista la
     * referencia de ejemplo de la plantilla, que es de otro artículo.
     */
    public static final ApcEtiquetaLayout USA = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-usa.xlsx",
            "ETIQUETTE COLIS", "PALET",
            41, 21, 9, 10, 11, 13, 14, 15, 18, 19, null,
            new Palet(19, 12, 13, COL_VALOR, COL_D, null));

    public static final ApcEtiquetaLayout WH_CROSSLOG = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-wh-crosslog.xlsx",
            "Etiquette colis Crosslog", "Etiquette Palette Crosslog",
            37, 19, 10, 9, 11, 12, 13, 14, 16, 17, 8,
            new Palet(14, 10, 11, 1, 1, 8));

    /**
     * RETAIL y WHOLESALE van al mismo almacén (Crosslog) y el cliente solo
     * partió la plantilla para que se imprima RETAIL o WHOLESALE en la línea
     * DESTINATION, pero desde que se ajustó el reparto en el A4 sus dos
     * ficheros YA NO comparten maquetación: aquí el bloque son 39 filas y
     * allí 37, y cada campo cae en una fila distinta. Antes se copiaban las
     * coordenadas de WH_CROSSLOG; hacerlo ahora escribiría cada valor una o
     * dos filas por encima de su rótulo.
     */
    public static final ApcEtiquetaLayout RETAIL = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-retail.xlsx",
            "Etiquette colis Retail", "Etiquette Palette Retail",
            39, 20, 11, 10, 12, 13, 14, 15, 17, 18, 9,
            new Palet(14, 10, 11, COL_VALOR, COL_D, 8));

    /**
     * Se aceptan la clave del catálogo de packing (D. USA, WHOLESALE) y el
     * nombre de la plantilla del cliente (USA, WH CROSSLOG). RETAIL se llama
     * igual en los dos sitios. Las destinaciones hijas no llegan aquí:
     * ResolutorDestinosPadre ya las ha resuelto a su padre al importar.
     */
    private static final Map<String, ApcEtiquetaLayout> POR_DESTINO = Map.of(
            "JAPAN", JAPAN,
            "KOREA", KOREA,
            "D. USA", USA, "USA", USA,
            "WHOLESALE", WH_CROSSLOG, "WH CROSSLOG", WH_CROSSLOG,
            "RETAIL", RETAIL);

    public static Optional<ApcEtiquetaLayout> paraDestino(String nombreDestino) {
        if (nombreDestino == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                POR_DESTINO.get(nombreDestino.trim().toUpperCase(Locale.ROOT)));
    }
}
