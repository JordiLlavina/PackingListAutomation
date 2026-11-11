package com.puntotres.packinglist.modelos.service;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.modelos.ModelosProperties;
import com.puntotres.packinglist.service.embalaje.CodigoQr;

/**
 * Lo que va en la etiqueta de patrones de un modelo: su QR, que lleva a la
 * dirección pública del modelo ({@code <url-publica>/m/<id>}) y, desde ahí, a
 * su ficha en la app.
 *
 * Usa {@link CodigoQr} de {@code service.embalaje}, que es una excepción
 * declarada a la disciplina de paquete de la app de modelos (que no toca los
 * servicios de la app vieja): es una utilidad estática y sin estado, la misma
 * que dibuja el QR de la hoja de embalaje, y moverla no ganaría nada.
 */
@Service
public class EtiquetasPatrones {

    /** Lado mínimo del PNG del QR, en píxeles: de sobra para imprimirlo nítido en una etiqueta. */
    private static final int PIXELES_QR = 600;

    private final ModelosProperties props;

    public EtiquetasPatrones(ModelosProperties props) {
        this.props = props;
    }

    /** El PNG del QR del modelo. No comprueba que el modelo exista: eso es de quien lo pide. */
    public byte[] qrPng(Long modeloId) {
        return CodigoQr.png(props.urlDeModelo(modeloId), PIXELES_QR);
    }
}
