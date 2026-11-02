package com.puntotres.packinglist.service.embalaje;

import java.util.List;

import com.puntotres.packinglist.model.DatosHojaEmbalaje;

/**
 * Un artículo (referencia + código de color) del pedido, con todo lo que el
 * formulario de «Info de embalaje» puede prerrellenar sin que nadie lo teclee.
 * Todo es editable en la pantalla: esto es solo el valor de partida.
 *
 * {@code fotoId} apunta a la foto principal del modelo en el catálogo, o es
 * {@code null} si el modelo no tiene ninguna.
 */
public record ArticuloEmbalaje(
        String referencia,
        String codigoColor,
        String descripcionColor,
        String nombreBolso,
        String codigoModelo,
        Long fotoId,
        String medidaCaja,
        Integer unidadesPorCaja,
        String bolsaTela,
        String etiquetaInterior,
        String instruccionEspecial,
        List<DatosHojaEmbalaje.DestinacionHoja> destinaciones) {

    public ArticuloEmbalaje {
        destinaciones = destinaciones == null ? List.of() : List.copyOf(destinaciones);
    }

    /**
     * El artículo en una línea: «PXCBC-F67008 · LZZ NOIR — SAC LE NEIGE». El
     * desplegable del formulario no la usa tal cual: alinea el nombre del
     * bolso en columna ({@code OpcionesArticuloEmbalaje} en la capa web).
     */
    public String etiqueta() {
        StringBuilder texto = new StringBuilder(referenciaYColor());
        if (tieneNombre()) {
            texto.append(SEPARADOR_NOMBRE).append(nombreBolso);
        }
        return texto.toString();
    }

    /** Lo que identifica al artículo, sin el nombre del bolso: «PXCBC-F67008 · LZZ NOIR». */
    public String referenciaYColor() {
        StringBuilder texto = new StringBuilder(referencia).append(" · ").append(codigoColor);
        if (descripcionColor != null && !descripcionColor.isBlank()) {
            texto.append(' ').append(descripcionColor);
        }
        return texto.toString();
    }

    /** Si hay nombre del bolso que poner detrás de {@link #referenciaYColor()}. */
    public boolean tieneNombre() {
        return nombreBolso != null && !nombreBolso.isBlank();
    }

    /** Lo que separa el nombre del bolso del resto en {@link #etiqueta()}. */
    public static final String SEPARADOR_NOMBRE = " — ";
}
