package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Lee la carpeta de temporada, en zip o subida tal cual desde el navegador:
 * una subcarpeta por modelo con sus fotos dentro.
 *
 * La carpeta de una foto es el antecesor MÁS CERCANO que nombra un modelo del
 * pedido, así que da igual que traiga la carpeta de temporada como raíz
 * ("H26/ULL027/foto.jpg") o que un modelo tenga subcarpetas propias
 * ("ULL027/detalles/foto.jpg"). Una carpeta nombra un modelo si se llama
 * como él o como una referencia suya: "ULL027.AL103" es el modelo ULL027
 * aunque el pedido escriba la piel AL0103, porque la foto es del modelo y no
 * de la piel. La referencia se parte con la regla del cliente (ver
 * ReferenciaCorte). Lo que no cae en ningún modelo se avisa y no se usa: una
 * foto en la carpeta equivocada acabaría en el documento de otro bolso.
 *
 * Los nombres de las entradas NUNCA se usan como ruta al copiar (zip slip):
 * cada foto se escribe con un nombre generado dentro del directorio de
 * trabajo, y el original solo sirve para ordenarla y enseñarla. Los topes de
 * tamaño y de ficheros paran lo que no es la carpeta de una temporada antes
 * de que llene el disco.
 */
public class LectorFotos {

    static final long MAXIMO_BYTES = 3L * 1024 * 1024 * 1024;
    static final int MAXIMO_ENTRADAS = 5_000;

    private static final Set<String> EXTENSIONES = Set.of("heic", "heif", "jpg", "jpeg", "png");
    /** Lo que los sistemas operativos siembran en las carpetas: se ignora sin avisar. */
    private static final Set<String> BASURA = Set.of(".DS_STORE", "THUMBS.DB", "DESKTOP.INI");
    private static final int MAXIMO_NOMBRES_EN_AVISO = 10;

    /** Un fichero de una carpeta subida: su ruta relativa ("H26/ULL027/a.jpg") y su contenido. */
    public record Fichero(String ruta, Contenido contenido) {
    }

    @FunctionalInterface
    public interface Contenido {
        InputStream abrir() throws IOException;
    }

    private final long maximoBytes;
    private final int maximoEntradas;

    public LectorFotos() {
        this(MAXIMO_BYTES, MAXIMO_ENTRADAS);
    }

    LectorFotos(long maximoBytes, int maximoEntradas) {
        this.maximoBytes = maximoBytes;
        this.maximoEntradas = maximoEntradas;
    }

    /** Lee un zip. Lanza IOException si no es un zip. */
    public FotosTemporada leer(Path zip, Set<String> modelos, Function<String, ReferenciaCorte> partir,
                               Path destino) throws IOException {
        Recogida recogida = new Recogida(modelos, partir, destino);
        try (ZipFile archivo = abrir(zip)) {
            Enumeration<? extends ZipEntry> todas = archivo.entries();
            while (todas.hasMoreElements()) {
                ZipEntry entrada = todas.nextElement();
                recogida.contar();
                if (!entrada.isDirectory()) {
                    recogida.anadir(entrada.getName(), () -> archivo.getInputStream(entrada));
                }
            }
        }
        return recogida.resultado();
    }

    /** Lee los ficheros de una carpeta subida desde el navegador, cada uno con su ruta relativa. */
    public FotosTemporada leer(List<Fichero> ficheros, Set<String> modelos,
                               Function<String, ReferenciaCorte> partir, Path destino) throws IOException {
        Recogida recogida = new Recogida(modelos, partir, destino);
        for (Fichero fichero : ficheros) {
            recogida.contar();
            recogida.anadir(fichero.ruta(), fichero.contenido());
        }
        return recogida.resultado();
    }

    /** Lo que se va reuniendo al recorrer los ficheros, venga de un zip o de una carpeta. */
    private final class Recogida {

        private final Set<String> buscados = new HashSet<>();
        private final Function<String, ReferenciaCorte> partir;
        private final Path originales;
        private final Map<String, List<FotoModelo>> porModelo = new TreeMap<>();
        private final Map<String, Integer> carpetasFuera = new TreeMap<>();
        private final List<String> sueltas = new ArrayList<>();
        private final List<String> noSonFotos = new ArrayList<>();
        private int entradas;
        private long bytes;
        private int numero;

        Recogida(Set<String> modelos, Function<String, ReferenciaCorte> partir, Path destino)
                throws IOException {
            modelos.forEach(modelo -> buscados.add(ReferenciaCorte.normalizar(modelo)));
            this.partir = partir;
            this.originales = Files.createDirectories(destino.resolve("originales"));
        }

        void contar() {
            if (++entradas > maximoEntradas) {
                throw new IllegalArgumentException("trae más de " + maximoEntradas
                        + " ficheros: no parece la carpeta de fotos de una temporada");
            }
        }

        void anadir(String ruta, Contenido contenido) throws IOException {
            List<String> segmentos = Arrays.stream(ruta.replace('\\', '/').split("/"))
                    .filter(segmento -> !segmento.isBlank())
                    .toList();
            if (segmentos.isEmpty()) {
                return;
            }
            String nombre = segmentos.get(segmentos.size() - 1);
            List<String> carpetas = segmentos.subList(0, segmentos.size() - 1);
            if (esBasura(nombre, carpetas)) {
                return;
            }
            String extension = extensionDe(nombre);
            if (!EXTENSIONES.contains(extension)) {
                noSonFotos.add(String.join("/", segmentos));
                return;
            }
            Optional<String> modelo = modeloDe(carpetas);
            if (modelo.isEmpty()) {
                if (carpetas.isEmpty()) {
                    sueltas.add(nombre);
                } else {
                    carpetasFuera.merge(carpetas.get(carpetas.size() - 1), 1, Integer::sum);
                }
                return;
            }
            Path copia = originales.resolve(String.format("%05d.%s", ++numero, extension));
            try (InputStream origen = contenido.abrir()) {
                bytes += copiar(origen, copia, maximoBytes - bytes);
            }
            porModelo.computeIfAbsent(modelo.get(), clave -> new ArrayList<>())
                    .add(new FotoModelo(modelo.get(), nombre, copia));
        }

        private Optional<String> modeloDe(List<String> carpetas) {
            for (int i = carpetas.size() - 1; i >= 0; i--) {
                String carpeta = ReferenciaCorte.normalizar(carpetas.get(i));
                if (buscados.contains(carpeta)) {
                    return Optional.of(carpeta);
                }
                String modelo = partir.apply(carpeta).modelo();
                if (buscados.contains(modelo)) {
                    return Optional.of(modelo);
                }
            }
            return Optional.empty();
        }

        FotosTemporada resultado() {
            porModelo.values().forEach(fotos -> fotos.sort(
                    Comparator.comparing(FotoModelo::nombreOriginal, String.CASE_INSENSITIVE_ORDER)));
            List<String> avisos = new ArrayList<>();
            carpetasFuera.forEach((carpeta, fotos) -> avisos.add("La carpeta " + carpeta
                    + " no es ningún modelo del pedido: "
                    + (fotos == 1 ? "su foto no se usa" : "sus " + fotos + " fotos no se usan")));
            if (!sueltas.isEmpty()) {
                avisos.add("Fotos sueltas, fuera de las carpetas de modelo, que no se usan: "
                        + listaCorta(sueltas));
            }
            if (!noSonFotos.isEmpty()) {
                avisos.add("Ficheros que no son fotos HEIC, JPG ni PNG y se ignoran: "
                        + listaCorta(noSonFotos));
            }
            return new FotosTemporada(porModelo, avisos);
        }
    }

    /**
     * El Explorador de Windows escribe los nombres en la página de códigos de
     * la consola (CP437) sin marcar la entrada como UTF-8, y leídos como UTF-8
     * Java los rechaza. CP437 no rechaza ningún byte, así que es la segunda
     * lectura; un fichero que no es un zip falla en las dos.
     */
    private static ZipFile abrir(Path zip) throws IOException {
        try {
            return comprobado(new ZipFile(zip.toFile(), StandardCharsets.UTF_8));
        } catch (ZipException | IllegalArgumentException e) {
            return comprobado(new ZipFile(zip.toFile(), Charset.forName("IBM437")));
        }
    }

    /** Recorre los nombres una vez: según la versión, Java no protesta hasta leerlos. */
    private static ZipFile comprobado(ZipFile archivo) throws IOException {
        try {
            Enumeration<? extends ZipEntry> entradas = archivo.entries();
            while (entradas.hasMoreElements()) {
                entradas.nextElement().getName();
            }
            return archivo;
        } catch (RuntimeException e) {
            archivo.close();
            throw e;
        }
    }

    private long copiar(InputStream origen, Path destino, long margen) throws IOException {
        byte[] bufer = new byte[64 * 1024];
        long copiados = 0;
        try (OutputStream salida = Files.newOutputStream(destino)) {
            int leidos;
            while ((leidos = origen.read(bufer)) != -1) {
                copiados += leidos;
                if (copiados > margen) {
                    throw new IllegalArgumentException("ocupa más de "
                            + maximoBytes / (1024 * 1024) + " MB: no parece la carpeta de fotos de "
                            + "una temporada");
                }
                salida.write(bufer, 0, leidos);
            }
        }
        return copiados;
    }

    private static boolean esBasura(String nombre, List<String> carpetas) {
        return carpetas.contains("__MACOSX")
                || nombre.startsWith("._")
                || BASURA.contains(nombre.toUpperCase(Locale.ROOT));
    }

    private static String extensionDe(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private static String listaCorta(List<String> nombres) {
        if (nombres.size() <= MAXIMO_NOMBRES_EN_AVISO) {
            return String.join(", ", nombres);
        }
        return String.join(", ", nombres.subList(0, MAXIMO_NOMBRES_EN_AVISO))
                + " y " + (nombres.size() - MAXIMO_NOMBRES_EN_AVISO) + " más";
    }
}
