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
        Palet palet) {

    /**
     * Geometría de la hoja de etiquetas de palet, que también es distinta en
     * cada plantilla. colValor es casi siempre la columna C, pero en
     * WHOLESALE el cliente trabaja sin la columna A y los valores caen en la
     * B: escribir en la C dejaría la etiqueta con el número de cajas y el
     * peso fuera del recuadro, en una columna que ni siquiera se imprime.
     */
    record Palet(int altura, int filaNumCajas, int filaPeso, int colValor) {
    }

    /** Columna C: los valores de la hoja de cajas en las cinco plantillas. */
    public static final int COL_VALOR = 2;

    public int colValor() {
        return COL_VALOR;
    }

    public static final ApcEtiquetaLayout JAPAN = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-japan.xlsx",
            "Etiquette colis Bolloré ", "Etiquette Palette Bolloré",
            36, 18, 8, 9, 10, 11, 12, 13, 16, 17,
            new Palet(14, 10, 11, COL_VALOR));

    public static final ApcEtiquetaLayout KOREA = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-korea.xlsx",
            "Etiquette colis FC Logistique", "Etiquette Palette FC logistique",
            36, 19, 7, 8, 9, 10, 11, 12, 15, 16,
            new Palet(14, 10, 11, COL_VALOR));

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
            41, 21, 9, 10, 11, 13, 14, 15, 18, 19,
            new Palet(19, 12, 13, COL_VALOR));

    public static final ApcEtiquetaLayout WH_CROSSLOG = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-wh-crosslog.xlsx",
            "Etiquette colis Crosslog", "Etiquette Palette Crosslog",
            37, 19, 10, 9, 11, 12, 13, 14, 16, 17,
            new Palet(14, 10, 11, 1));

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
            39, 20, 11, 10, 12, 13, 14, 15, 17, 18,
            new Palet(14, 10, 11, COL_VALOR));

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
