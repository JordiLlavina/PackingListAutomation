package com.puntotres.packinglist.service.embalaje;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;

/**
 * Genera la imagen PNG de un código QR para la etiqueta de información de
 * embalaje del taller (el QR lleva al vídeo de cómo embalar).
 *
 * El dibujo es propio, no el de ZXing ({@code MatrixToImageWriter}, que pinta
 * cuadrados): el usuario lo quería más bonito. ZXing solo <b>codifica</b>
 * ({@link Encoder#encode}, la matriz de módulos sin zona muda) y aquí se pinta
 * con {@link Graphics2D}: los tres <b>localizadores</b> de las esquinas como
 * cuadrados redondeados concéntricos (el anillo de 7×7 y el centro de 3×3) y
 * los <b>módulos de datos como puntos</b> de 0,85 módulos de diámetro, en
 * negro sobre blanco. Lo que lo mantiene legible: la zona muda de 4 módulos,
 * la corrección de errores <b>Q</b> (un 25 %, más margen que la M de antes
 * para los bordes que se comen los puntos y las esquinas redondas) y que cada
 * módulo mida un número <b>entero</b> de píxeles, nunca menos de 12, para que
 * el PNG salga nítido a 3,5 cm. {@code CodigoQrTest} lo decodifica con ZXing.
 *
 * Igual que el EAN-13 de las etiquetas, el PNG no se reescala con
 * interpolación después de dibujarlo: un QR emborronado deja de leerse con el
 * móvil.
 */
public final class CodigoQr {

    /** Zona muda alrededor del símbolo, en módulos: los 4 que exige la norma ISO para que lo lea cualquier móvil. */
    private static final int ZONA_MUDA_MODULOS = 4;
    /** Píxeles mínimos de un módulo: por debajo, a 3,5 cm, los puntos y las curvas se pixelan. */
    private static final int PIXELES_MINIMOS_POR_MODULO = 12;
    /** Diámetro de cada punto de datos, en módulos: deja un hilo blanco entre dos puntos vecinos. */
    private static final double DIAMETRO_PUNTO = 0.85;

    /** Lado de un localizador y de su centro, en módulos (lo fija la norma). */
    private static final int LADO_LOCALIZADOR = 7;
    private static final int LADO_CENTRO_LOCALIZADOR = 3;
    /**
     * Diámetro del redondeo de las esquinas, en módulos: el del anillo de fuera,
     * el del hueco blanco de dentro y el del centro. Van menguando para que las
     * tres curvas sean paralelas.
     */
    private static final double REDONDEO_ANILLO = 3.5;
    private static final double REDONDEO_HUECO = 2.5;
    private static final double REDONDEO_CENTRO = 1.5;

    private static final Color NEGRO = Color.BLACK;
    private static final Color BLANCO = Color.WHITE;

    private CodigoQr() {
    }

    /**
     * PNG cuadrado con el QR de {@code texto}, corrección de errores Q y la
     * zona muda de 4 módulos. {@code pixeles} es el lado <b>mínimo</b>: el
     * lado real es el primer múltiplo del número de módulos que llega a él (y
     * nunca menos de 12 píxeles por módulo), así que un texto largo, que
     * necesita más módulos, da una imagen mayor. Quien la pega fija su tamaño
     * en centímetros, así que no importa. El texto ya viene recortado por quien
     * llama (aquí no se limpia ni se valida).
     */
    public static byte[] png(String texto, int pixeles) {
        ByteMatrix matriz = matriz(texto);
        int modulos = matriz.getWidth();
        int total = modulos + 2 * ZONA_MUDA_MODULOS;
        int pixelesModulo = Math.max(PIXELES_MINIMOS_POR_MODULO, (pixeles + total - 1) / total);
        int lado = pixelesModulo * total;

        BufferedImage imagen = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(BLANCO);
            g.fillRect(0, 0, lado, lado);
            g.setColor(NEGRO);
            dibujarPuntos(g, matriz, pixelesModulo);
            for (int[] esquina : esquinasLocalizadores(modulos)) {
                dibujarLocalizador(g, (ZONA_MUDA_MODULOS + esquina[0]) * pixelesModulo,
                        (ZONA_MUDA_MODULOS + esquina[1]) * pixelesModulo, pixelesModulo);
            }
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "PNG", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el código QR de '" + texto + "'", e);
        }
    }

    /** La matriz de módulos del QR (1 = negro), sin zona muda. */
    private static ByteMatrix matriz(String texto) {
        Map<EncodeHintType, Object> pistas = Map.of(
                EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        try {
            return Encoder.encode(texto, ErrorCorrectionLevel.Q, pistas).getMatrix();
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el código QR de '" + texto + "'", e);
        }
    }

    /** Esquina superior izquierda, en módulos, de los tres localizadores: arriba a la izquierda, arriba a la derecha y abajo a la izquierda. */
    private static int[][] esquinasLocalizadores(int modulos) {
        int lejos = modulos - LADO_LOCALIZADOR;
        return new int[][] {{0, 0}, {lejos, 0}, {0, lejos}};
    }

    private static boolean enLocalizador(int x, int y, int modulos) {
        for (int[] esquina : esquinasLocalizadores(modulos)) {
            if (x >= esquina[0] && x < esquina[0] + LADO_LOCALIZADOR
                    && y >= esquina[1] && y < esquina[1] + LADO_LOCALIZADOR) {
                return true;
            }
        }
        return false;
    }

    /** Cada módulo negro que no es de un localizador, como un punto centrado en su casilla. */
    private static void dibujarPuntos(Graphics2D g, ByteMatrix matriz, int pixelesModulo) {
        int modulos = matriz.getWidth();
        double diametro = DIAMETRO_PUNTO * pixelesModulo;
        double hueco = (pixelesModulo - diametro) / 2;
        for (int y = 0; y < modulos; y++) {
            for (int x = 0; x < modulos; x++) {
                if (matriz.get(x, y) == 1 && !enLocalizador(x, y, modulos)) {
                    g.fill(new Ellipse2D.Double((ZONA_MUDA_MODULOS + x) * pixelesModulo + hueco,
                            (ZONA_MUDA_MODULOS + y) * pixelesModulo + hueco, diametro, diametro));
                }
            }
        }
    }

    /**
     * Un localizador: el anillo de 7×7 (un cuadrado redondeado menos el hueco
     * de 5×5) y el centro de 3×3, todos con las esquinas redondeadas.
     */
    private static void dibujarLocalizador(Graphics2D g, int x, int y, int m) {
        int ladoHueco = LADO_LOCALIZADOR - 2;
        Area anillo = new Area(new RoundRectangle2D.Double(x, y, LADO_LOCALIZADOR * m, LADO_LOCALIZADOR * m,
                REDONDEO_ANILLO * m, REDONDEO_ANILLO * m));
        anillo.subtract(new Area(new RoundRectangle2D.Double(x + m, y + m, ladoHueco * m, ladoHueco * m,
                REDONDEO_HUECO * m, REDONDEO_HUECO * m)));
        g.fill(anillo);
        int desplazamiento = (LADO_LOCALIZADOR - LADO_CENTRO_LOCALIZADOR) / 2;
        g.fill(new RoundRectangle2D.Double(x + desplazamiento * m, y + desplazamiento * m,
                LADO_CENTRO_LOCALIZADOR * m, LADO_CENTRO_LOCALIZADOR * m, REDONDEO_CENTRO * m, REDONDEO_CENTRO * m));
    }
}
