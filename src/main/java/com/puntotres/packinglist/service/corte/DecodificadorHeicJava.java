package com.puntotres.packinglist.service.corte;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

import openize.heic.decoder.HeicImage;
import openize.heic.decoder.PixelFormat;
import openize.io.IOFileStream;
import openize.io.IOMode;

/**
 * Decodifica HEIC en Java puro con Openize.HEIC: funciona igual en Windows y
 * en el servidor Linux, sin instalar nada.
 *
 * <b>Es caro, y está medido</b> (fotos reales de iPhone, 2026-09-29): ~5 s y
 * ~0,8 GB de heap una foto de 12 MP; ~14 s y ~1,8 GB una de 24 MP.
 * Decodificar por franjas no baja el pico, porque la librería reconstruye la
 * cuadrícula entera. Por eso cuántas fotos se decodifican a la vez lo decide
 * un presupuesto de memoria —un semáforo en MB sobre el heap máximo menos una
 * reserva para el resto de la aplicación—, que es COMPARTIDO por todas las
 * sesiones: dos personas convirtiendo a la vez no pueden sumar el doble.
 *
 * Una foto que pide MÁS que el presupuesto entero ni se intenta: Openize llena
 * el heap poco a poco y el OutOfMemoryError puede caer en cualquier hilo
 * (una petición de otra persona, la base de datos), no solo en el de la foto.
 * Sale como aviso de esa foto diciendo cómo arreglarlo: más memoria a la
 * aplicación (con -Xmx3g ya caben las de 24 MP) o la foto en JPG.
 */
public class DecodificadorHeicJava {

    /** Lo medido son ~65-75 bytes por píxel; 80 deja margen. */
    private static final long BYTES_POR_PIXEL = 80;
    private static final long RESERVA_MB = 512;
    private static final long MINIMO_MB = 256;

    private final int presupuestoMb;
    private final Semaphore presupuesto;

    public DecodificadorHeicJava() {
        this(presupuestoPorDefecto());
    }

    DecodificadorHeicJava(int presupuestoMb) {
        this.presupuestoMb = Math.max(1, presupuestoMb);
        this.presupuesto = new Semaphore(this.presupuestoMb, true);
    }

    private static int presupuestoPorDefecto() {
        long maximoMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(MINIMO_MB, maximoMb - RESERVA_MB));
    }

    public BufferedImage decodificar(Path fichero) throws IOException, InterruptedException {
        try (IOFileStream flujo = new IOFileStream(fichero.toFile(), IOMode.READ)) {
            HeicImage imagen = HeicImage.load(flujo);
            int ancho = (int) imagen.getWidth();
            int alto = (int) imagen.getHeight();
            long estimados = Math.max(1, (long) ancho * alto * BYTES_POR_PIXEL / (1024 * 1024));
            if (estimados > presupuestoMb) {
                throw new FotoDemasiadoGrande("necesita unos " + estimados + " MB de memoria y la "
                        + "aplicación solo tiene " + presupuestoMb + " MB para fotos: arráncala con "
                        + "más memoria (-Xmx) o pasa la foto a JPG");
            }
            int necesarios = (int) estimados;
            presupuesto.acquire(necesarios);
            try {
                int[] pixeles = imagen.getInt32Array(PixelFormat.Argb32);
                BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
                salida.setRGB(0, 0, ancho, alto, pixeles, 0, ancho);
                return salida;
            } catch (OutOfMemoryError e) {
                throw new IOException("no cabe en la memoria de la aplicación ("
                        + ancho + "x" + alto + " px)");
            } finally {
                presupuesto.release(necesarios);
            }
        } catch (FotoDemasiadoGrande e) {
            throw new IOException(e.getMessage());
        } catch (RuntimeException e) {
            // Las excepciones de Openize (openize.io.IOException incluida) son de tiempo de ejecución.
            throw new IOException("no es un HEIC legible"
                    + (e.getMessage() != null ? ": " + e.getMessage() : ""), e);
        }
    }

    /** Sale de dentro del try para no confundirse con un fallo de lectura de Openize. */
    private static final class FotoDemasiadoGrande extends RuntimeException {
        FotoDemasiadoGrande(String mensaje) {
            super(mensaje);
        }
    }
}
