package com.puntotres.packinglist.testutil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Para leer en los tests los Excel generados: el texto de las celdas y las fotos. */
public final class ExcelDePrueba {

    private ExcelDePrueba() {
    }

    public static XSSFWorkbook abrir(byte[] excel) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(excel));
    }

    /** El texto de todas las celdas de una hoja, una por línea, en orden de filas y columnas. */
    public static String texto(Sheet hoja) {
        StringBuilder texto = new StringBuilder();
        for (Row fila : hoja) {
            for (Cell celda : fila) {
                if (celda.getCellType() == CellType.STRING) {
                    texto.append(celda.getStringCellValue()).append('\n');
                } else if (celda.getCellType() == CellType.NUMERIC) {
                    texto.append(celda.getNumericCellValue()).append('\n');
                }
            }
        }
        return texto.toString();
    }

    /** El texto de todas las hojas del libro. */
    public static String texto(XSSFWorkbook libro) {
        StringBuilder texto = new StringBuilder();
        libro.forEach(hoja -> texto.append(texto(hoja)));
        return texto.toString();
    }

    public static List<String> nombresDeHoja(XSSFWorkbook libro) {
        List<String> nombres = new ArrayList<>();
        libro.forEach(hoja -> nombres.add(hoja.getSheetName()));
        return nombres;
    }

    /** Las fotos dibujadas en una hoja, en el orden en que se añadieron. */
    public static List<XSSFPicture> fotosDe(XSSFSheet hoja) {
        List<XSSFPicture> fotos = new ArrayList<>();
        if (hoja.getDrawingPatriarch() == null) {
            return fotos;
        }
        for (XSSFShape forma : hoja.getDrawingPatriarch().getShapes()) {
            if (forma instanceof XSSFPicture foto) {
                fotos.add(foto);
            }
        }
        return fotos;
    }
}
