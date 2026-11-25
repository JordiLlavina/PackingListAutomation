package com.puntotres.packinglist.service.produccion;

/**
 * Un artículo del catálogo en uno de sus colores, tal como lo ofrece el
 * desplegable «Artículo» de la Hoja de Producción, con lo que rellena al
 * elegirlo: el modelo y el nombre del bolso en la cabecera, la foto, y en la
 * primera línea la piel base y su color, la primera combinación y su color y
 * el forro. Todo es editable en la pantalla: esto es solo el valor de partida.
 * {@code codigoColor} está vacío cuando el catálogo no conoce ningún color
 * del artículo; {@code fotoId}, cuando el modelo no tiene fotos.
 */
public record ArticuloProduccion(
        String referencia,
        String codigoColor,
        String descripcionColor,
        String nombreBolso,
        String codigoModelo,
        Long fotoId,
        String pielBase,
        String colorBase,
        String pielCombinacion,
        String colorCombinacion,
        String forro) {

    /** Lo que identifica al artículo, sin el nombre del bolso: «PXCBC-F67008 · LZZ NOIR». */
    public String referenciaYColor() {
        String color = (codigoColor + " " + descripcionColor).trim();
        return color.isEmpty() ? referencia : referencia + " · " + color;
    }

    public boolean tieneNombre() {
        return nombreBolso != null && !nombreBolso.isBlank();
    }
}
