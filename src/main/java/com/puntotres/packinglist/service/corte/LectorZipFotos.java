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
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Lee el zip de la carpeta de temporada: una subcarpeta por modelo con sus
 * fotos dentro.
 *
 * La carpeta de una foto es el antecesor MÁS CERCANO cuyo nombre es un modelo
 * del pedido, así que da igual que el zip traiga la carpeta de temporada como
 * raíz ("H26/ULL027/foto.jpg") o que un modelo tenga subcarpetas propias
 * ("ULL027/detalles/foto.jpg"). Lo que no cae en ningún modelo se avisa y no
 * se usa: una foto en la carpeta equivocada acabaría en el documento de otro
 * bolso.
 *
 * Los nombres de las entradas NUNCA se usan como ruta al descomprimir (zip
 * slip): cada foto se escribe con un nombre generado dentro del directorio de
 * trabajo, y el original solo sirve para ordenarla y enseñarla. Los topes de
 * tamaño y de entradas paran un zip que no es la carpeta de una temporada
 * antes de que llene el disco.
 */
public class LectorZipFotos {

    static final long MAXIMO_BYTES = 3L * 1024 * 1024 * 1024;
    static final int MAXIMO_ENTRADAS = 5_000;

    private static final Set<String> EXTENSIONES = Set.of("heic", "heif", "jpg", "jpeg");
    /** Lo que los sistemas operativos siembran en las carpetas: se ignora sin avisar. */
    private static final Set<String> BASURA = Set.of(".DS_STORE", "THUMBS.DB", "DESKTOP.INI");
    private static final int MAXIMO_NOMBRES_EN_AVISO = 10;

    private final long maximoBytes;
    private final int maximoEntradas;

    public LectorZipFotos() {
        this(MAXIMO_BYTES, MAXIMO_ENTRADAS);
    }

    LectorZipFotos(long maximoBytes, int maximoEntradas) {
        this.maximoBytes = maximoBytes;
        this.maximoEntradas = maximoEntradas;
    }

    public FotosTemporada leer(Path zip, Set<String> modelos, Path destino) throws IOException {
        Set<String> buscados = new HashSet<>();
        modelos.forEach(modelo -> buscados.add(ReferenciaCorte.normalizar(modelo)));
        Path originales = Files.createDirectories(destino.resolve("originales"));

        Map<String, List<FotoModelo>> porModelo = new TreeMap<>();
        Map<String, Integer> carpetasFuera = new TreeMap<>();
        List<String> sueltas = new ArrayList<>();
        List<String> noSonFotos = new ArrayList<>();
        try (ZipFile archivo = abrir(zip)) {
            int entradas = 0;
            long bytes = 0;
            int numero = 0;
            Enumeration<? extends ZipEntry> todas = archivo.entries();
            while (todas.hasMoreElements()) {
                ZipEntry entrada = todas.nextElement();
                if (++entradas > maximoEntradas) {
                    throw new IllegalArgumentException("trae más de " + maximoEntradas
                            + " ficheros: no parece la carpeta de fotos de una temporada");
                }
                if (entrada.isDirectory()) {
                    continue;
                }
                List<String> segmentos = Arrays.stream(entrada.getName().replace('\\', '/').split("/"))
                        .filter(segmento -> !segmento.isBlank())
                        .toList();
                if (segmentos.isEmpty()) {
                    continue;
                }
                String nombre = segmentos.get(segmentos.size() - 1);
                List<String> carpetas = segmentos.subList(0, segmentos.size() - 1);
                if (esBasura(nombre, carpetas)) {
                    continue;
                }
                String extension = extensionDe(nombre);
                if (!EXTENSIONES.contains(extension)) {
                    noSonFotos.add(String.join("/", segmentos));
                    continue;
                }
                Optional<String> modelo = modeloDe(carpetas, buscados);
                if (modelo.isEmpty()) {
                    if (carpetas.isEmpty()) {
                        sueltas.add(nombre);
                    } else {
                        carpetasFuera.merge(carpetas.get(carpetas.size() - 1), 1, Integer::sum);
                    }
                    continue;
                }
                Path copia = originales.resolve(String.format("%05d.%s", ++numero, extension));
                try (InputStream contenido = archivo.getInputStream(entrada)) {
                    bytes += copiar(contenido, copia, maximoBytes - bytes);
                }
                porModelo.computeIfAbsent(modelo.get(), clave -> new ArrayList<>())
                        .add(new FotoModelo(modelo.get(), nombre, copia));
            }
        }
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
            avisos.add("Ficheros que no son fotos HEIC ni JPG y se ignoran: " + listaCorta(noSonFotos));
        }
        return new FotosTemporada(porModelo, avisos);
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
                    throw new IllegalArgumentException("descomprimido ocupa más de "
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

    private static Optional<String> modeloDe(List<String> carpetas, Set<String> modelos) {
        for (int i = carpetas.size() - 1; i >= 0; i--) {
            String carpeta = ReferenciaCorte.normalizar(carpetas.get(i));
            if (modelos.contains(carpeta)) {
                return Optional.of(carpeta);
            }
        }
        return Optional.empty();
    }

    private static String listaCorta(List<String> nombres) {
        if (nombres.size() <= MAXIMO_NOMBRES_EN_AVISO) {
            return String.join(", ", nombres);
        }
        return String.join(", ", nombres.subList(0, MAXIMO_NOMBRES_EN_AVISO))
                + " y " + (nombres.size() - MAXIMO_NOMBRES_EN_AVISO) + " más";
    }
}
