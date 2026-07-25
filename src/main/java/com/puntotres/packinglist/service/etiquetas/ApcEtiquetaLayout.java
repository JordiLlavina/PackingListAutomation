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
 * filaLivraison: en WH CROSSLOG es la fila "ASN N°" (esa plantilla no
 * tiene Livraison); recibe el mismo valor.
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

    public static final ApcEtiquetaLayout USA = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-usa.xlsx",
            "ETIQUETTE COLIS", "PALET",
            46, 23, 9, 10, 12, 13, 14, 15, 18, 19);

    public static final ApcEtiquetaLayout WH_CROSSLOG = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-wh-crosslog.xlsx",
            "Etiquette colis Crosslog", "Etiquette Palette Crosslog",
            40, 20, 12, 11, 13, 14, 15, 16, 18, 19);

    /**
     * Se aceptan la clave del catálogo de packing (D. USA, C-LOG) y el
     * nombre de la plantilla del cliente (USA, WH CROSSLOG).
     */
    private static final Map<String, ApcEtiquetaLayout> POR_DESTINO = Map.of(
            "JAPAN", JAPAN,
            "KOREA", KOREA,
            "D. USA", USA, "USA", USA,
            "C-LOG", WH_CROSSLOG, "WH CROSSLOG", WH_CROSSLOG);

    public static Optional<ApcEtiquetaLayout> paraDestino(String nombreDestino) {
        if (nombreDestino == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                POR_DESTINO.get(nombreDestino.trim().toUpperCase(Locale.ROOT)));
    }
}
