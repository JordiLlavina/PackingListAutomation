package com.puntotres.packinglist.service.corte;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Una conversión de fotos en marcha: arranca al subir el zip y trabaja en
 * segundo plano mientras se rellena la tabla de pieles. La pantalla pregunta
 * por el progreso y generar espera a que acabe.
 *
 * Una foto que no se deja leer es un aviso con su nombre, nunca el final de
 * la conversión: las demás siguen.
 */
public final class ConversionFotos {

    /** Cómo se lee una foto a imagen; cada camino tiene el suyo. */
    @FunctionalInterface
    interface Decodificador {
        BufferedImage leer(Path fichero) throws Exception;
    }

    /** Más que lo que tarda la foto más lenta medida (24 MP en Java: ~14 s). */
    private static final long SEGUNDOS_MAXIMOS_AL_CANCELAR = 60;

    private final int total;
    private final ExecutorService hilos;
    private final AtomicInteger hechas = new AtomicInteger();
    private final Map<Path, Path> reducidas = new ConcurrentHashMap<>();
    private final Queue<String> avisos = new ConcurrentLinkedQueue<>();
    private final List<Process> procesos = new CopyOnWriteArrayList<>();
    private final CountDownLatch fin = new CountDownLatch(1);
    private volatile boolean cancelada;

    ConversionFotos(int total, ExecutorService hilos) {
        this.total = total;
        this.hilos = hilos;
    }

    public int total() {
        return total;
    }

    /** Las ya procesadas, hayan salido bien o no. */
    public int hechas() {
        return hechas.get();
    }

    public boolean terminada() {
        return fin.getCount() == 0;
    }

    public void esperar() throws InterruptedException {
        fin.await();
    }

    /** El JPEG reducido de una foto, o vacío si no se ha podido convertir. */
    public Optional<Path> reducida(FotoModelo foto) {
        return Optional.ofNullable(reducidas.get(foto.original()));
    }

    public List<String> avisos() {
        return List.copyOf(avisos);
    }

    /**
     * Para lo que quede: al empezar otra carga o al caducar la sesión.
     *
     * Espera a que acaben las fotos que ya se estaban leyendo, porque ni
     * ImageIO ni Openize se dejan interrumpir a mitad de una foto y quien
     * cancela borra el directorio justo después: en Windows un fichero
     * abierto no se puede borrar y se quedaría en temporales. Es como mucho
     * lo que tarde una foto por hilo.
     */
    public void cancelar() {
        cancelada = true;
        hilos.shutdownNow();
        procesos.forEach(Process::destroyForcibly);
        try {
            hilos.awaitTermination(SEGUNDOS_MAXIMOS_AL_CANCELAR, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        fin.countDown();
    }

    void registrar(Process proceso) {
        procesos.add(proceso);
        if (cancelada) {
            proceso.destroyForcibly();
        }
    }

    /** Una foto que ha convertido otro camino (Windows) y ya está en disco. */
    void anotar(FotoModelo foto, Path salida) {
        reducidas.put(foto.original(), salida);
        descartarOriginal(foto);
        hechas.incrementAndGet();
    }

    void convertir(FotoModelo foto, Path salida, Decodificador decodificador, boolean enderezarExif) {
        if (cancelada) {
            return;
        }
        try {
            BufferedImage reducida = ReductorImagen.reducir(
                    decodificador.leer(foto.original()), ReductorImagen.LADO_MAXIMO);
            if (enderezarExif) {
                reducida = ReductorImagen.orientar(reducida,
                        ReductorImagen.orientacionExif(foto.original()));
            }
            ReductorImagen.escribirJpeg(reducida, salida);
            reducidas.put(foto.original(), salida);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception | OutOfMemoryError e) {
            if (!cancelada) {
                avisos.add("La foto " + foto.nombreOriginal() + " de " + foto.modelo()
                        + " no se ha podido leer (" + explicar(e) + "): no sale en los documentos");
            }
        } finally {
            descartarOriginal(foto);
            hechas.incrementAndGet();
        }
    }

    /**
     * El original ya no hace falta en cuanto se ha convertido o se ha dado
     * por ilegible en su último intento: una temporada de fotos de 24 MP son
     * cientos de megas, y guardarlos junto a las reducidas mientras se
     * rellena la tabla duplicaba el disco. Un fallo al borrar no importa: el
     * directorio entero se borra al acabar la sesión.
     */
    private static void descartarOriginal(FotoModelo foto) {
        try {
            Files.deleteIfExists(foto.original());
        } catch (IOException e) {
            // Se queda hasta que se borre el directorio de la sesión.
        }
    }

    void terminar() {
        fin.countDown();
    }

    private static String explicar(Throwable error) {
        if (error instanceof OutOfMemoryError) {
            return "no cabe en la memoria de la aplicación";
        }
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }
}
