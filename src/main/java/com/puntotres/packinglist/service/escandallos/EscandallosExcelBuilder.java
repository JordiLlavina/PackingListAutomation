package com.puntotres.packinglist.service.escandallos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Escribe el libro de salida: un solo {@code .xlsx} con una hoja por
 * escandallo, en el orden en que llegaron los ficheros.
 *
 * A diferencia de los builders de packing list, aquí <strong>no hay
 * plantilla</strong>: la hoja son cuatro rótulos y una tabla, y arrastrar un
 * {@code .xlsx} de plantilla solo para eso complicaría el clonado de hojas sin
 * ganar nada. La maquetación son las constantes de esta clase, ancladas por
 * EscandallosExcelBuilderTest contra el ejemplo real
 * (docs/Procesado Escandallos ICSUITE/escandallos_extraidos_ejemplo.xlsx).
 */
@Service
public class EscandallosExcelBuilder {

    private static final int FILA_MODELO = 1;
    private static final int FILA_DESCRIPCION = 2;
    private static final int FILA_COLOR = 3;
    private static final int FILA_ENCABEZADOS = 5;
    private static final int PRIMERA_FILA_DATOS = 6;

    private static final int COLUMNA_ETIQUETA = 0;
    private static final int COLUMNA_VALOR = 1;
    private static final int COLUMNA_CANTIDAD = 2;

    /** Anchos en 1/256 de carácter, los del ejemplo real. */
    private static final int ANCHO_ARTICLE = 11 * 256;
    private static final int ANCHO_DESCRIPCION = 41 * 256;
    private static final int ANCHO_CANTIDAD = 10 * 256;

    public ExcelEscandallos construir(List<Escandallo> escandallos) {
        NombresHoja nombres = new NombresHoja();
        List<String> hojas = new ArrayList<>();
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            for (Escandallo escandallo : escandallos) {
                String nombre = nombres.para(escandallo);
                hojas.add(nombre);
                escribirHoja(libro.createSheet(nombre), escandallo, estilos);
            }
            libro.write(salida);
            return new ExcelEscandallos(salida.toByteArray(), List.copyOf(hojas),
                    nombres.avisos());
        } catch (IOException e) {
            throw new UncheckedIOException("no se ha podido escribir el excel de escandallos", e);
        }
    }

    // --- Una hoja ---

    private static void escribirHoja(Sheet hoja, Escandallo escandallo, Estilos estilos) {
        hoja.setColumnWidth(COLUMNA_ETIQUETA, ANCHO_ARTICLE);
        hoja.setColumnWidth(COLUMNA_VALOR, ANCHO_DESCRIPCION);
        hoja.setColumnWidth(COLUMNA_CANTIDAD, ANCHO_CANTIDAD);

        etiqueta(hoja, FILA_MODELO, "MODEL", escandallo.modelo(), estilos);
        etiqueta(hoja, FILA_DESCRIPCION, "DESCRIPCIÓ", escandallo.descripcion(), estilos);
        etiqueta(hoja, FILA_COLOR, "COLOR", escandallo.color(), estilos);

        Row encabezados = hoja.createRow(FILA_ENCABEZADOS);
        texto(encabezados, COLUMNA_ETIQUETA, "Article", estilos.encabezado);
        texto(encabezados, COLUMNA_VALOR, "Descripció", estilos.encabezado);
        texto(encabezados, COLUMNA_CANTIDAD, "Quantitat", estilos.encabezado);

        int fila = PRIMERA_FILA_DATOS;
        for (LineaEscandallo linea : escandallo.lineas()) {
            escribirLinea(hoja.createRow(fila++), linea, estilos);
        }
    }

    private static void escribirLinea(Row fila, LineaEscandallo linea, Estilos estilos) {
        texto(fila, COLUMNA_ETIQUETA, linea.article(), estilos.dato);
        texto(fila, COLUMNA_VALOR, linea.descripcion(), estilos.dato);
        if (linea.cantidad() == null) {
            // Una cantidad desconocida se queda en blanco: un 0 parecería un
            // dato real, y la misma regla que los pesos del packing list.
            fila.createCell(COLUMNA_CANTIDAD).setCellStyle(estilos.dato);
            return;
        }
        var celda = fila.createCell(COLUMNA_CANTIDAD);
        celda.setCellValue(linea.cantidad());
        celda.setCellStyle(estilos.dato);
    }

    private static void etiqueta(Sheet hoja, int numeroFila, String rotulo, String valor,
                                 Estilos estilos) {
        Row fila = hoja.createRow(numeroFila);
        texto(fila, COLUMNA_ETIQUETA, rotulo, estilos.rotulo);
        texto(fila, COLUMNA_VALOR, valor, estilos.valorCabecera);
    }

    private static void texto(Row fila, int columna, String valor, CellStyle estilo) {
        var celda = fila.createCell(columna);
        if (valor != null && !valor.isEmpty()) {
            celda.setCellValue(valor);
        }
        celda.setCellStyle(estilo);
    }

    // --- Estilos ---

    /**
     * Los estilos se crean una vez por libro, no por celda: POI tiene un tope
     * de 64.000 estilos por libro y aquí puede haber decenas de escandallos
     * con decenas de líneas cada uno.
     */
    private static final class Estilos {

        private final CellStyle rotulo;
        private final CellStyle valorCabecera;
        private final CellStyle encabezado;
        private final CellStyle dato;

        private Estilos(Workbook libro) {
            Font negrita = libro.createFont();
            negrita.setFontHeightInPoints((short) 8);
            negrita.setBold(true);
            Font normal = libro.createFont();
            normal.setFontHeightInPoints((short) 8);

            rotulo = libro.createCellStyle();
            rotulo.setFont(negrita);
            rotulo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            rotulo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            recuadrar(rotulo);

            valorCabecera = libro.createCellStyle();
            valorCabecera.setFont(negrita);
            recuadrar(valorCabecera);

            encabezado = libro.createCellStyle();
            encabezado.setFont(negrita);
            encabezado.setBorderBottom(BorderStyle.THIN);

            dato = libro.createCellStyle();
            dato.setFont(normal);
        }

        private static void recuadrar(CellStyle estilo) {
            estilo.setBorderTop(BorderStyle.THIN);
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBorderLeft(BorderStyle.THIN);
            estilo.setBorderRight(BorderStyle.THIN);
        }
    }
}
