package com.puntotres.packinglist.service.cargamodelos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * El esqueleto vacío de la «carpeta de carpetas»: un zip con la carpeta raíz
 * (el nombre de la temporada) y, dentro, una carpeta vacía por modelo. El
 * usuario lo descomprime, mete en cada carpeta las fotos y las fichas
 * técnicas del modelo y lo sube tal cual.
 *
 * Cada carpeta se llama con el código del modelo y, detrás y separado por un
 * espacio, el nombre del bolso si se sabe ("F67008 LE NEIGE"): al leerla solo
 * cuenta la primera palabra, y el nombre ayuda a quien reparte las fotos.
 */
public final class EsqueletoCarpetas {

    /** Lo que Windows no admite en el nombre de una carpeta. */
    private static final String PROHIBIDOS = "[\\\\/:*?\"<>|\\p{Cntrl}]";
    private static final String RAIZ_POR_DEFECTO = "Modelos";

    private EsqueletoCarpetas() {
    }

    /** El nombre de la carpeta de un modelo: su código y, si se sabe, su nombre. */
    public static String carpetaDe(String codigo, String nombre) {
        String elNombre = nombre == null ? "" : nombre.trim();
        return saneado(elNombre.isEmpty() ? codigo : codigo + " " + elNombre);
    }

    /**
     * @param raiz     el nombre de la temporada; sin él, la raíz se llama «Modelos»
     * @param carpetas los nombres de las carpetas de modelo (ver {@link #carpetaDe})
     */
    public static byte[] zip(String raiz, Collection<String> carpetas) {
        String laRaiz = saneado(raiz);
        if (laRaiz.isEmpty()) {
            laRaiz = RAIZ_POR_DEFECTO;
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(laRaiz + "/"));
            zip.closeEntry();
            Set<String> usadas = new LinkedHashSet<>();
            for (String carpeta : carpetas) {
                String nombre = saneado(carpeta);
                if (nombre.isEmpty() || !usadas.add(nombre.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(laRaiz + "/" + nombre + "/"));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }

    /** Sin los caracteres que Windows prohíbe, sin espacios ni puntos al final, y sin pasarse de largo. */
    static String saneado(String nombre) {
        if (nombre == null) {
            return "";
        }
        String limpio = nombre.replaceAll(PROHIBIDOS, "-").replaceAll("\\s+", " ").trim();
        if (limpio.length() > 80) {
            limpio = limpio.substring(0, 80).trim();
        }
        while (limpio.endsWith(".")) {
            limpio = limpio.substring(0, limpio.length() - 1).trim();
        }
        return limpio;
    }
}
