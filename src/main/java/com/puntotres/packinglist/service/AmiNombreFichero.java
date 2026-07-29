package com.puntotres.packinglist.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Nombre del fichero de un packing list AMI, en el formato que pide el
 * cliente:
 *
 * <pre>yyyy.MM.dd_PUN_&lt;product order&gt;_&lt;referencia&gt;.&lt;color&gt;_&lt;TEMPORADA&gt;_&lt;DEST&gt;.xlsx</pre>
 *
 * Ejemplo real: {@code 2026.05.21_PUN_07705_ULL027.AL0103.001_H26_CHINA.xlsx}.
 *
 * La fecha es la de envío que teclea el usuario en la pantalla de entrada
 * (llega en dd/MM/yyyy y aquí se le da la vuelta), el product order es el
 * {@code pedido} del JSON y la referencia ya viene compuesta
 * ({@code ULL027.AL0103}), así que el bloque de artículo es simplemente
 * referencia + "." + color.
 *
 * Igual que el resto del pipeline, esto <b>nunca bloquea</b>: si un campo
 * falta se omite su segmento (en vez de dejar un "__" suelto) y si la fecha
 * no viene en dd/MM/yyyy se escribe tal cual. Un nombre raro es un problema
 * que el humano ve y arregla; una excepción aquí tiraría la generación
 * entera de un excel que por lo demás está bien.
 */
final class AmiNombreFichero {

    /** Marca del proveedor, fija en todos los nombres. */
    private static final String MARCA = "PUN";

    private static final DateTimeFormatter FECHA_ENTRADA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_NOMBRE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * Abreviaturas de destinación acordadas con el cliente. PARIS y FRANCE
     * son la misma destinación escrita de dos formas en los packing lists
     * de origen. Una destinación que no esté aquí no es un error: se usa su
     * nombre en mayúsculas y sin espacios.
     */
    private static final Map<String, String> ABREVIATURAS = Map.of(
            "FRANCE", "FR",
            "FRANCIA", "FR",
            "PARIS", "FR",
            "CHINA", "CHINA",
            "JAPAN", "JAPAN",
            "JAPON", "JAPAN");

    /** Separadores de ruta y caracteres que Windows prohíbe, más espacios. */
    private static final String CARACTERES_INVALIDOS = "[\\\\/:*?\"<>|\\s]+";

    private AmiNombreFichero() {
    }

    static String componer(String fechaEnvio, String pedido, String referencia,
                           String color, String temporada, String destino) {
        List<String> segmentos = new ArrayList<>();
        anadir(segmentos, fecha(fechaEnvio));
        segmentos.add(MARCA);
        anadir(segmentos, pedido);
        anadir(segmentos, articulo(referencia, color));
        anadir(segmentos, temporada);
        anadir(segmentos, abreviatura(destino));
        return String.join("_", segmentos).replaceAll(CARACTERES_INVALIDOS, "_") + ".xlsx";
    }

    private static void anadir(List<String> segmentos, String valor) {
        if (valor != null && !valor.isBlank()) {
            segmentos.add(valor.trim());
        }
    }

    private static String fecha(String fechaEnvio) {
        if (fechaEnvio == null || fechaEnvio.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(fechaEnvio.trim(), FECHA_ENTRADA).format(FECHA_NOMBRE);
        } catch (DateTimeParseException e) {
            return fechaEnvio;
        }
    }

    /** Referencia y color van pegados con punto, como en las referencias del ERP. */
    private static String articulo(String referencia, String color) {
        List<String> partes = new ArrayList<>();
        anadir(partes, referencia);
        anadir(partes, color);
        return String.join(".", partes);
    }

    private static String abreviatura(String destino) {
        if (destino == null || destino.isBlank()) {
            return null;
        }
        String normalizado = destino.trim().toUpperCase(Locale.ROOT);
        return ABREVIATURAS.getOrDefault(normalizado, normalizado.replaceAll("\\s+", ""));
    }
}
