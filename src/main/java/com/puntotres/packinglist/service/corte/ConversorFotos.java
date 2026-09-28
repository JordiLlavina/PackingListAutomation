package com.puntotres.packinglist.service.corte;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.service.corte.ConversorHeicWindows.Trabajo;

/**
 * Convierte las fotos del zip en JPEG reducidos, en segundo plano.
 *
 * Tres caminos: los JPG, con ImageIO y enderezados por su EXIF; los HEIC en
 * Windows, por lotes con el decodificador del sistema (rápido); y los HEIC
 * que Windows no lee, o todos fuera de Windows, con Openize en Java puro
 * (lento y con mucha memoria, ver DecodificadorHeicJava). Los lotes de
 * Windows se lanzan los primeros para que no esperen detrás de los JPG.
 */
@Service
public class ConversorFotos {

    /** Hilos del camino Java: pocos, porque cada foto ya pide mucha memoria. */
    private static final int HILOS_JAVA = Math.max(1,
            Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    /** Procesos de PowerShell a la vez; cada uno convierte su lote en serie. */
    private static final int PROCESOS_WINDOWS = Math.max(1,
            Math.min(3, Runtime.getRuntime().availableProcessors() / 4));

    private final DecodificadorHeicJava heicJava;
    /** null = no se usa el camino de Windows. */
    private final ConversorHeicWindows heicWindows;

    @Autowired
    public ConversorFotos() {
        this(new DecodificadorHeicJava(),
                ConversorHeicWindows.disponible() ? new ConversorHeicWindows() : null);
    }

    ConversorFotos(DecodificadorHeicJava heicJava, ConversorHeicWindows heicWindows) {
        this.heicJava = heicJava;
        this.heicWindows = heicWindows;
    }

    public ConversionFotos convertir(List<FotoModelo> fotos, Path directorio) throws IOException {
        Path destino = Files.createDirectories(directorio.resolve("reducidas"));
        List<FotoModelo> heic = fotos.stream().filter(ConversorFotos::esHeic).toList();
        List<FotoModelo> jpg = fotos.stream().filter(foto -> !esHeic(foto)).toList();
        List<List<FotoModelo>> lotes = heicWindows == null ? List.of() : lotes(heic, PROCESOS_WINDOWS);

        ExecutorService hilos = Executors.newFixedThreadPool(HILOS_JAVA + lotes.size(), hilosDemonio());
        ConversionFotos conversion = new ConversionFotos(fotos.size(), hilos);
        List<CompletableFuture<Void>> tareas = new ArrayList<>();
        for (List<FotoModelo> lote : lotes) {
            tareas.add(CompletableFuture.runAsync(
                    () -> convertirEnWindows(lote, destino, directorio, conversion), hilos));
        }
        if (heicWindows == null) {
            for (FotoModelo foto : heic) {
                tareas.add(CompletableFuture.runAsync(() -> conversion.convertir(
                        foto, salidaDe(foto, destino), heicJava::decodificar, false), hilos));
            }
        }
        for (FotoModelo foto : jpg) {
            tareas.add(CompletableFuture.runAsync(() -> conversion.convertir(
                    foto, salidaDe(foto, destino), ConversorFotos::leerJpeg, true), hilos));
        }
        CompletableFuture.allOf(tareas.toArray(CompletableFuture[]::new))
                .whenComplete((nada, error) -> {
                    hilos.shutdown();
                    conversion.terminar();
                });
        return conversion;
    }

    private void convertirEnWindows(List<FotoModelo> lote, Path destino, Path directorio,
                                    ConversionFotos conversion) {
        Map<Path, FotoModelo> porEntrada = new LinkedHashMap<>();
        List<Trabajo> trabajos = new ArrayList<>();
        for (FotoModelo foto : lote) {
            trabajos.add(new Trabajo(foto.original(), salidaDe(foto, destino)));
            porEntrada.put(foto.original(), foto);
        }
        List<Trabajo> pendientes;
        try {
            pendientes = heicWindows.convertir(trabajos, directorio, conversion::registrar,
                    trabajo -> conversion.anotar(porEntrada.get(trabajo.entrada()), trabajo.salida()));
        } catch (IOException e) {
            pendientes = trabajos;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        for (Trabajo trabajo : pendientes) {
            conversion.convertir(porEntrada.get(trabajo.entrada()), trabajo.salida(),
                    heicJava::decodificar, false);
        }
    }

    static BufferedImage leerJpeg(Path fichero) throws IOException {
        BufferedImage imagen = ImageIO.read(fichero.toFile());
        if (imagen == null) {
            throw new IOException("no es un JPG legible");
        }
        return imagen;
    }

    private static boolean esHeic(FotoModelo foto) {
        String nombre = foto.nombreOriginal().toLowerCase(Locale.ROOT);
        return nombre.endsWith(".heic") || nombre.endsWith(".heif");
    }

    /** "00012.heic" → "reducidas/00012.jpg": el nombre generado ya es único. */
    private static Path salidaDe(FotoModelo foto, Path destino) {
        String nombre = foto.original().getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        return destino.resolve((punto < 0 ? nombre : nombre.substring(0, punto)) + ".jpg");
    }

    private static List<List<FotoModelo>> lotes(List<FotoModelo> fotos, int maximo) {
        if (fotos.isEmpty()) {
            return List.of();
        }
        int numero = Math.min(maximo, fotos.size());
        int tamano = (fotos.size() + numero - 1) / numero;
        List<List<FotoModelo>> lotes = new ArrayList<>();
        for (int desde = 0; desde < fotos.size(); desde += tamano) {
            lotes.add(fotos.subList(desde, Math.min(fotos.size(), desde + tamano)));
        }
        return lotes;
    }

    private static ThreadFactory hilosDemonio() {
        AtomicInteger contador = new AtomicInteger();
        return tarea -> {
            Thread hilo = new Thread(tarea, "fotos-corte-" + contador.incrementAndGet());
            hilo.setDaemon(true);
            return hilo;
        };
    }
}
