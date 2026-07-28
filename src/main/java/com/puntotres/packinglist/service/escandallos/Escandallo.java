package com.puntotres.packinglist.service.escandallos;

import java.util.List;

/**
 * Un escandallo del ERP ya limpio: el bloque de cabecera del artículo y su
 * tabla de materiales. Se descarta todo lo demás que trae el listado del ERP
 * (precios, importes, tabla de fases y totales).
 *
 * Los escandallos son por modelo <em>y color</em>: dos ficheros distintos
 * pueden traer el mismo {@code modelo}. Por eso se guarda también el
 * {@code origen} (el nombre del fichero subido), que es lo único que
 * identifica al escandallo cuando hay que avisar de algo.
 */
public record Escandallo(String modelo, String descripcion, String color,
                         List<LineaEscandallo> lineas, String origen) {
}
