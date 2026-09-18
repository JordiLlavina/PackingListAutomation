package com.puntotres.packinglist.shipping.importacion;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** CSV con cabecera, UTF-8, coma y comillas. Quita todos los BOM del principio (MyDHL+ exporta dos). */
public final class LectorCsv {

    private LectorCsv() { }

    public static List<CSVRecord> leer(byte[] bytes) {
        String texto = new String(bytes, StandardCharsets.UTF_8);
        while (texto.startsWith("﻿")) texto = texto.substring(1);
        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true).setAllowMissingColumnNames(true).build();
        try (CSVParser parser = CSVParser.parse(new StringReader(texto), formato)) {
            return parser.getRecords();
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("El fichero no se puede leer como CSV: " + e.getMessage(), e);
        }
    }

    /** El valor de una columna, o null si la columna no existe o está vacía. */
    public static String columna(CSVRecord fila, String nombre) {
        if (!fila.isMapped(nombre) || !fila.isSet(nombre)) return null;
        String v = fila.get(nombre);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
