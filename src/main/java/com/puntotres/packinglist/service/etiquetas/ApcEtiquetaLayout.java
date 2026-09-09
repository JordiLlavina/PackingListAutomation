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
 * La hoja de palet es uniforme en las 4 plantillas: etiqueta modelo en las
 * filas 0..13, nº de cajas en C13 y peso en C14 (1-based).
 *
 * filaLivraison: en WH CROSSLOG y en RETAIL es la fila "ASN N°" (esas
 * plantillas no tienen Livraison); recibe el mismo valor.
 *
 * NO cambiar estas coordenadas sin revisar la plantilla, y viceversa.
 */
record ApcEtiquetaLayout(
        String rutaPlantilla, String hojaCajas, String hojaPalet,
        int alturaBloque, int offsetSegundaEtiqueta,
        int filaOrder, int filaLivraison, int filaReferencia, int filaColor,
        int filaTalla, int filaPiezas, int filaColisage, int filaPeso) {

    public static final int COL_VALOR = 2;          // columna C
    public static final int ALTURA_BLOQUE_PALET = 14;
    public static final int FILA_PALET_NUM_CAJAS = 12; // C13
    public static final int FILA_PALET_PESO = 13;      // C14

    public static final ApcEtiquetaLayout JAPAN = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-japan.xlsx",
            "Etiquette colis Bolloré ", "Etiquette Palette Bolloré",
            42, 21, 9, 10, 11, 12, 13, 14, 17, 18);

    public static final ApcEtiquetaLayout KOREA = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-korea.xlsx",
            "Etiquette colis FC Logistique", "Etiquette Palette FC logistique",
            44, 22, 11, 12, 13, 14, 15, 16, 19, 20);

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
            46, 23, 9, 10, 11, 13, 14, 15, 18, 19);

    public static final ApcEtiquetaLayout WH_CROSSLOG = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-wh-crosslog.xlsx",
            "Etiquette colis Crosslog", "Etiquette Palette Crosslog",
            40, 20, 12, 11, 13, 14, 15, 16, 18, 19);

    /**
     * Mismas coordenadas que WH_CROSSLOG: las dos etiquetas van al mismo
     * almacén (Crosslog) y el cliente solo partió la plantilla para que se
     * imprima RETAIL o WHOLESALE en la línea DESTINATION. Aun así son DOS
     * ficheros distintos y ese estático es lo único que los diferencia, así
     * que confundirlos no rompe nada visible: lo ancla ApcEtiquetaLayoutTest.
     */
    public static final ApcEtiquetaLayout RETAIL = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-retail.xlsx",
            "Etiquette colis Retail", "Etiquette Palette Retail",
            40, 20, 12, 11, 13, 14, 15, 16, 18, 19);

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
