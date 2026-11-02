package com.puntotres.packinglist.service.embalaje;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * Genera la imagen PNG de un código QR para la etiqueta de información de
 * embalaje del taller (el QR lleva al vídeo de cómo embalar).
 *
 * Igual que el EAN-13 de las etiquetas, el PNG sale ya con el tamaño exacto
 * con el que se va a pegar en el Word y se inserta 1:1, sin reescalar: un QR
 * reescalado con interpolación se emborrona y deja de leerse con el móvil.
 * Por eso quien llama pide los píxeles del lado y no se hace ningún cambio de
 * tamaño después.
 */
public final class CodigoQr {

    /** Zona muda alrededor del símbolo, en módulos: los 4 que exige la norma ISO para que lo lea cualquier móvil. */
    private static final int ZONA_MUDA_MODULOS = 4;

    private CodigoQr() {
    }

    /**
     * PNG cuadrado de {@code pixeles} de lado con el QR de {@code texto},
     * corrección de errores M y la zona muda de 4 módulos. El texto ya viene
     * recortado por quien llama (aquí no se limpia ni se valida).
     */
    public static byte[] png(String texto, int pixeles) {
        Map<EncodeHintType, Object> pistas = Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, ZONA_MUDA_MODULOS,
                EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        try {
            BitMatrix matriz = new QRCodeWriter()
                    .encode(texto, BarcodeFormat.QR_CODE, pixeles, pixeles, pistas);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matriz, "PNG", salida);
            return salida.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException(
                    "No se pudo generar el código QR de '" + texto + "'", e);
        }
    }
}
