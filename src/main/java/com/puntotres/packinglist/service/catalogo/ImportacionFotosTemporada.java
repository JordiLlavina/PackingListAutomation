package com.puntotres.packinglist.service.catalogo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.service.corte.ConversionFotos;
import com.puntotres.packinglist.service.corte.ConversorFotos;
import com.puntotres.packinglist.service.corte.DocumentosCorteService;
import com.puntotres.packinglist.service.corte.FotoModelo;
import com.puntotres.packinglist.service.corte.FotosTemporada;
import com.puntotres.packinglist.service.corte.LectorFotos;
import com.puntotres.packinglist.service.corte.ReferenciaCorte;

import jakarta.annotation.PreDestroy;

/**
 * Las fotos de una temporada, subidas al guardarla en /temporadas (carpeta o
 * zip, con una subcarpeta por modelo, igual que en los Documentos del Corte),
 * entran al catálogo de modelos.
 *
 * Se leen y se reconocen dentro de la petición —es rápido y así los avisos de
 * carpetas que no son de ningún modelo salen en el emergente—, pero la
 * conversión a JPEG reducido y el guardado van en segundo plano: un HEIC por
 * el camino Java tarda segundos y una temporada son decenas de fotos, más de
 * lo que aguanta una petición. La pantalla pregunta por el {@link Estado}.
 *
 * Una carpeta vale para un modelo si se llama como un modelo del catálogo de
 * ese cliente o como una referencia suya partida con la regla del cliente
 * (la misma de siempre: {@code ClienteCorte.partir}). Por eso las fotos se
 * leen DESPUÉS de meter los excels de la temporada en el catálogo: así los
 * modelos nuevos de esta temporada ya están.
 */
@Service
public class ImportacionFotosTemporada {

    /** Cómo va la importación de las fotos de una temporada. */
    public record Estado(int total, int hechas, int guardadas, boolean terminada, List<String> avisos) {

        public Estado {
            avisos = List.copyOf(avisos);
        }
    }

    private final CatalogoModelos catalogo;
    private final FotosCatalogo fotosCatalogo;
    private final ConversorFotos conversor;
    private final DocumentosCorteService reglas;
    private final Map<Long, Trabajo> trabajos = new ConcurrentHashMap<>();
    private final ExecutorService guardado = Executors.newSingleThreadExecutor(tarea -> {
        Thread hilo = new Thread(tarea, "fotos-temporada");
        hilo.setDaemon(true);
        return hilo;
    });

    public ImportacionFotosTemporada(CatalogoModelos catalogo, FotosCatalogo fotosCatalogo,
                                     ConversorFotos conversor, DocumentosCorteService reglas) {
        this.catalogo = catalogo;
        this.fotosCatalogo = fotosCatalogo;
        this.conversor = conversor;
        this.reglas = reglas;
    }

    /** Las fotos de un zip. Lanza IOException si no es un zip. */
    public Estado importarZip(String cliente, Long temporadaId, LectorFotos.Contenido zip) throws IOException {
        Path directorio = Files.createTempDirectory(ConversorFotos.PREFIJO_DIRECTORIO);
        try {
            Path copia = directorio.resolve("temporada.zip");
            try (InputStream entrada = zip.abrir()) {
                Files.copy(entrada, copia);
            }
            FotosTemporada leidas;
            try {
                leidas = new LectorFotos().leer(copia, modelos(cliente), partir(cliente), directorio);
            } finally {
                Files.deleteIfExists(copia);
            }
            return arrancar(cliente, temporadaId, leidas, directorio);
        } catch (IOException | RuntimeException e) {
            borrar(directorio);
            throw e;
        }
    }

    /** Las fotos de una carpeta subida desde el navegador, cada una con su ruta relativa. */
    public Estado importarCarpeta(String cliente, Long temporadaId, List<LectorFotos.Fichero> ficheros)
            throws IOException {
        Path directorio = Files.createTempDirectory(ConversorFotos.PREFIJO_DIRECTORIO);
        try {
            FotosTemporada leidas = new LectorFotos().leer(ficheros, modelos(cliente), partir(cliente), directorio);
            return arrancar(cliente, temporadaId, leidas, directorio);
        } catch (IOException | RuntimeException e) {
            borrar(directorio);
            throw e;
        }
    }

    /** Cómo va la importación de esa temporada; vacío si no hay ninguna en esta ejecución. */
    public Optional<Estado> estado(Long temporadaId) {
        return Optional.ofNullable(temporadaId).map(trabajos::get).map(Trabajo::estado);
    }

    /** Espera a que acabe la de esa temporada (para los tests). */
    void esperar(Long temporadaId) throws InterruptedException {
        Trabajo trabajo = trabajos.get(temporadaId);
        while (trabajo != null && !trabajo.terminado) {
            Thread.sleep(50);
        }
    }

    @PreDestroy
    void parar() {
        guardado.shutdownNow();
    }

    private Estado arrancar(String cliente, Long temporadaId, FotosTemporada leidas, Path directorio)
            throws IOException {
        ConversionFotos conversion = conversor.convertir(leidas.todas(), directorio);
        Trabajo trabajo = new Trabajo(conversion, leidas);
        trabajo.avisos.addAll(leidas.avisos());
        trabajos.put(temporadaId, trabajo);
        guardado.submit(() -> guardar(cliente, trabajo, directorio));
        return trabajo.estado();
    }

    /** En segundo plano: espera a la conversión y guarda cada foto reducida en su modelo. */
    private void guardar(String cliente, Trabajo trabajo, Path directorio) {
        try {
            trabajo.conversion.esperar();
            trabajo.avisos.addAll(trabajo.conversion.avisos());
            for (Map.Entry<String, List<FotoModelo>> delModelo : trabajo.leidas.porModelo().entrySet()) {
                guardarModelo(cliente, delModelo.getKey(), delModelo.getValue(), trabajo);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            trabajo.avisos.add("Se ha interrumpido el guardado de las fotos: vuelve a subirlas.");
        } catch (RuntimeException e) {
            trabajo.avisos.add("No se han podido guardar las fotos en el catálogo: "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()) + ".");
        } finally {
            trabajo.terminado = true;
            borrar(directorio);
        }
    }

    private void guardarModelo(String cliente, String modelo, List<FotoModelo> fotos, Trabajo trabajo) {
        boolean teniaFotos = !fotosCatalogo.de(cliente, modelo).isEmpty();
        int indicePrincipal = trabajo.leidas.indicePorDefecto(modelo);
        Long principal = null;
        for (int indice = 0; indice < fotos.size(); indice++) {
            FotoModelo foto = fotos.get(indice);
            Optional<Path> reducida = trabajo.conversion.reducida(foto);
            if (reducida.isEmpty()) {
                continue;
            }
            try {
                Long id = fotosCatalogo.guardar(cliente, modelo, foto.nombreOriginal(),
                        Files.readAllBytes(reducida.get())).id();
                trabajo.guardadas.incrementAndGet();
                // La principal de la carpeta manda si el modelo no tenía fotos
                // o si la carpeta la nombra (principal, main, 0); si no, se
                // respeta la que ya se eligió en el catálogo.
                if (indice == indicePrincipal && (!teniaFotos || nombraPrincipal(foto))) {
                    principal = id;
                }
            } catch (IOException | RuntimeException e) {
                trabajo.avisos.add("La foto " + foto.nombreOriginal() + " de " + modelo
                        + " no se ha podido guardar en el catálogo.");
            }
        }
        if (principal != null) {
            fotosCatalogo.marcarPrincipal(cliente, modelo, principal);
        }
    }

    private static boolean nombraPrincipal(FotoModelo foto) {
        String nombre = foto.nombreOriginal();
        int punto = nombre.lastIndexOf('.');
        String sinExtension = (punto < 0 ? nombre : nombre.substring(0, punto)).trim().toLowerCase(Locale.ROOT);
        return Set.of("principal", "main", "0").contains(sinExtension);
    }

    private Set<String> modelos(String cliente) {
        return catalogo.modelosDe(cliente).stream().map(Modelo::getCodigo).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Function<String, ReferenciaCorte> partir(String cliente) {
        return reglas.reglasDeProduccion(cliente)::partir;
    }

    private static void borrar(Path directorio) {
        if (directorio == null || !Files.exists(directorio)) {
            return;
        }
        try (Stream<Path> todo = Files.walk(directorio)) {
            todo.sorted(Comparator.reverseOrder()).forEach(ruta -> {
                try {
                    Files.deleteIfExists(ruta);
                } catch (IOException e) {
                    // Un fichero abierto por otro proceso: lo limpiará LimpiezaDirectoriosCorte al arrancar.
                }
            });
        } catch (IOException e) {
            // Igual que arriba.
        }
    }

    /** Una importación en marcha o acabada. */
    private static final class Trabajo {

        private final ConversionFotos conversion;
        private final FotosTemporada leidas;
        private final Queue<String> avisos = new ConcurrentLinkedQueue<>();
        private final AtomicInteger guardadas = new AtomicInteger();
        private volatile boolean terminado;

        Trabajo(ConversionFotos conversion, FotosTemporada leidas) {
            this.conversion = conversion;
            this.leidas = leidas;
        }

        Estado estado() {
            return new Estado(conversion.total(), conversion.hechas(), guardadas.get(), terminado,
                    new ArrayList<>(avisos));
        }
    }
}
