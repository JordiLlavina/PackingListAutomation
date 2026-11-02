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

    /** El texto del desplegable: «PXCBC-F67008 · LZZ NOIR — SAC LE NEIGE». */
    public String etiqueta() {
        StringBuilder texto = new StringBuilder(referencia).append(" · ").append(codigoColor);
        if (descripcionColor != null && !descripcionColor.isBlank()) {
            texto.append(' ').append(descripcionColor);
        }
        if (nombreBolso != null && !nombreBolso.isBlank()) {
            texto.append(" — ").append(nombreBolso);
        }
        return texto.toString();
    }
}
