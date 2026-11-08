package com.puntotres.packinglist.service.etiquetasarticulo;

import java.util.List;
import java.util.Locale;

/**
 * Una etiqueta pequeña de una muestra de venta (SMS): las siete líneas que se
 * imprimen en su casilla, todas en mayúsculas como en la plantilla del
 * usuario ("ETIQUETAS PEQUEÑAS Template.doc"):
 *
 * <ol>
 *   <li>PUNTOTRES</li>
 *   <li>la temporada ("FALL 27")</li>
 *   <li>SMS (en las de prototipos irá TEST)</li>
 *   <li>la referencia entera ("PXCBC-F67008")</li>
 *   <li>el nombre del modelo ("LE NEIGE")</li>
 *   <li>el nombre de la piel ("ALPACA TOUCH")</li>
 *   <li>el color de la piel ("VERT FONCE")</li>
 * </ol>
 *
 * Un dato que no se sabe deja su línea en blanco y no se corre la de debajo:
 * cada línea está siempre en el mismo sitio de la etiqueta.
 */
public record EtiquetaSms(String temporada, String referencia, String nombreModelo, String piel,
                          String colorPiel) {

    static final String REMITENTE = "PUNTOTRES";
    static final String TIPO = "SMS";
    private static final Locale ESPANOL = Locale.forLanguageTag("es");

    public List<String> lineas() {
        return List.of(REMITENTE, mayusculas(temporada), TIPO, mayusculas(referencia),
                mayusculas(nombreModelo), mayusculas(piel), mayusculas(colorPiel));
    }

    private static String mayusculas(String texto) {
        return texto == null ? "" : texto.trim().toUpperCase(ESPANOL);
    }
}
