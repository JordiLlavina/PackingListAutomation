package com.puntotres.packinglist.service.cargamodelos;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import com.puntotres.packinglist.service.corte.LectorFotos;

/**
 * Lee la «carpeta de carpetas» de una carga de modelos, tal cual o en zip:
 * una carpeta raíz con el nombre de la temporada y, dentro, una subcarpeta
 * por modelo con sus fotos (HEIC, JPG, PNG) y sus fichas técnicas (PDF).
 *
 * No decide de qué modelo es cada subcarpeta —eso depende del origen y lo
 * hace {@code ConstructorLote}—: devuelve el nombre de cada una tal como está
 * escrito y sus ficheros, copiados al directorio del lote.
 *
 * Es otro lector y no una ampliación de {@code LectorFotos} porque aquel
 * necesita saber de antemano qué modelos busca (los del pedido) y descarta lo
 * que no sea una foto; aquí los modelos pueden salir de la propia carpeta y
 * los PDF cuentan.
 *
 * La raíz: si TODOS los ficheros cuelgan de la misma primera carpeta, esa es
 * la raíz (el navegador la manda siempre al elegir una carpeta, y un zip
 * hecho de la carpeta también); si no —un zip hecho del contenido—, no hay
 * raíz y la primera carpeta de cada fichero ya es su modelo.
 *
 * Los nombres de las entradas NUNCA se usan como ruta al copiar (zip slip):
 * cada fichero se escribe con un nombre generado.
 */
public final class CarpetaModelos {

    static final long MAXIMO_BYTES = 3L * 1024 * 1024 * 1024;
    static final int MAXIMO_ENTRADAS = 5_000;

    private static final Set<String> FOTOS = Set.of("heic", "heif", "jpg", "jpeg", "png");
    private static final String PDF = "pdf";
    /** Lo que los sistemas operativos siembran en las carpetas: se ignora sin avisar. */
    private static final Set<String> BASURA = Set.of(".DS_STORE", "THUMBS.DB", "DESKTOP.INI");
    private static final int MAXIMO_NOMBRES_EN_AVISO = 10;

    /** Un fichero de la carpeta de un modelo, ya copiado al directorio del lote. */
    public record FicheroModelo(String nombre, Path copia, boolean pdf) {
    }

    /**
     * @param raiz       el nombre de la carpeta raíz (la temporada), o vacío si no la hay
     * @param porCarpeta nombre de cada subcarpeta, tal como está escrito → sus fotos y PDF, por nombre
     */
    public record Lectura(String raiz, Map<String, List<FicheroModelo>> porCarpeta, List<String> avisos) {

        public Lectura {
            Map<String, List<FicheroModelo>> copia = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            porCarpeta.forEach((carpeta, ficheros) -> copia.put(carpeta, List.copyOf(ficheros)));
            porCarpeta = java.util.Collections.unmodifiableMap(copia);
            avisos = List.copyOf(avisos);
        }

        public static Lectura vacia() {
            return new Lectura("", Map.of(), List.of());
        }

        public boolean estaVacia() {
            return porCarpeta.isEmpty();
        }
    }

    private record Entrada(List<String> segmentos, LectorFotos.Contenido contenido) {
    }

    private CarpetaModelos() {
    }

    /** Los ficheros de una carpeta subida desde el navegador, cada uno con su ruta relativa. */
    public static Lectura leer(List<LectorFotos.Fichero> ficheros, Path destino) throws IOException {
        List<Entrada> entradas = new ArrayList<>();
        for (LectorFotos.Fichero fichero : ficheros) {
            anadir(entradas, fichero.ruta(), fichero.contenido());
        }
        return repartir(entradas, destino);
    }

    /** Un zip. Lanza IOException si no lo es. */
    public static Lectura leerZip(Path zip, Path destino) throws IOException {
        try (ZipFile archivo = abrir(zip)) {
            List<Entrada> entradas = new ArrayList<>();
            Enumeration<? extends ZipEntry> todas = archivo.entries();
            while (todas.hasMoreElements()) {
                ZipEntry entrada = todas.nextElement();
                if (!entrada.isDirectory()) {
                    anadir(entradas, entrada.getName(), () -> archivo.getInputStream(entrada));
                }
            }
            return repartir(entradas, destino);
        }
    }

    private static void anadir(List<Entrada> entradas, String ruta, LectorFotos.Contenido contenido) {
        if (ruta == null) {
            return;
        }
        List<String> segmentos = Arrays.stream(ruta.replace('\\', '/').split("/"))
                .map(String::trim)
                .filter(segmento -> !segmento.isEmpty())
                .toList();
        if (segmentos.isEmpty() || esBasura(segmentos)) {
            return;
        }
        if (entradas.size() >= MAXIMO_ENTRADAS) {
            throw new IllegalArgumentException("trae más de " + MAXIMO_ENTRADAS
                    + " ficheros: no parece una carpeta de modelos");
        }
        entradas.add(new Entrada(segmentos, contenido));
    }

    private static Lectura repartir(List<Entrada> entradas, Path destino) throws IOException {
        if (entradas.isEmpty()) {
            return Lectura.vacia();
        }
        String raiz = raizDe(entradas);
        int desde = raiz.isEmpty() ? 0 : 1;
        Path originales = Files.createDirectories(destino.resolve("carpeta"));
        Map<String, List<FicheroModelo>> porCarpeta = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<String> sueltos = new ArrayList<>();
        List<String> otros = new ArrayList<>();
        long bytes = 0;
        int numero = 0;
        for (Entrada entrada : entradas) {
            List<String> segmentos = entrada.segmentos();
            String nombre = segmentos.get(segmentos.size() - 1);
            String extension = extensionDe(nombre);
            boolean pdf = PDF.equals(extension);
            if (!pdf && !FOTOS.contains(extension)) {
                otros.add(String.join("/", segmentos));
                continue;
            }
            // Raíz / modelo / … / fichero: sin carpeta de modelo, el fichero va suelto.
            if (segmentos.size() - desde < 2) {
                sueltos.add(nombre);
                continue;
            }
            Path copia = originales.resolve(String.format("%05d.%s", ++numero, extension));
            try (InputStream origen = entrada.contenido().abrir()) {
                bytes += copiar(origen, copia, MAXIMO_BYTES - bytes);
            }
            porCarpeta.computeIfAbsent(segmentos.get(desde), carpeta -> new ArrayList<>())
                    .add(new FicheroModelo(nombre, copia, pdf));
        }
        porCarpeta.values().forEach(ficheros -> ficheros.sort(
                Comparator.comparing(FicheroModelo::nombre, String.CASE_INSENSITIVE_ORDER)));
        List<String> avisos = new ArrayList<>();
        if (!sueltos.isEmpty()) {
            avisos.add("Ficheros sueltos, fuera de las carpetas de modelo, que no se usan: " + listaCorta(sueltos));
        }
        if (!otros.isEmpty()) {
            avisos.add("Ficheros que no son fotos (HEIC, JPG, PNG) ni PDF y se ignoran: " + listaCorta(otros));
        }
        return new Lectura(raiz, porCarpeta, avisos);
    }

    /** La primera carpeta, si todos los ficheros cuelgan de la misma; si no, vacío. */
    private static String raizDe(List<Entrada> entradas) {
        String raiz = null;
        for (Entrada entrada : entradas) {
            if (entrada.segmentos().size() < 2) {
                // Un fichero sin ninguna carpeta: no hay raíz común.
                return "";
            }
            String primera = entrada.segmentos().get(0);
            if (raiz == null) {
                raiz = primera;
            } else if (!raiz.equalsIgnoreCase(primera)) {
                return "";
            }
        }
        return raiz == null ? "" : raiz;
    }

    /**
     * El Explorador de Windows escribe los nombres en CP437 sin marcar la
     * entrada como UTF-8, y leídos como UTF-8 Java los rechaza (la misma
     * doble lectura que {@code LectorFotos}).
     */
    private static ZipFile abrir(Path zip) throws IOException {
        try {
            return comprobado(new ZipFile(zip.toFile(), StandardCharsets.UTF_8));
        } catch (ZipException | IllegalArgumentException e) {
            return comprobado(new ZipFile(zip.toFile(), Charset.forName("IBM437")));
        }
    }

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

    private static long copiar(InputStream origen, Path destino, long margen) throws IOException {
        byte[] bufer = new byte[64 * 1024];
        long copiados = 0;
        try (OutputStream salida = Files.newOutputStream(destino)) {
            int leidos;
            while ((leidos = origen.read(bufer)) != -1) {
                copiados += leidos;
                if (copiados > margen) {
                    throw new IllegalArgumentException("ocupa más de " + MAXIMO_BYTES / (1024 * 1024)
                            + " MB: no parece una carpeta de modelos");
                }
                salida.write(bufer, 0, leidos);
            }
        }
        return copiados;
    }

    private static boolean esBasura(List<String> segmentos) {
        String nombre = segmentos.get(segmentos.size() - 1);
        return segmentos.contains("__MACOSX")
                || nombre.startsWith("._")
                || BASURA.contains(nombre.toUpperCase(Locale.ROOT));
    }

    static String extensionDe(String nombre) {
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
