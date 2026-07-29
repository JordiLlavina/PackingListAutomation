package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Acceso de bajo nivel a la hoja "EAN ..." del excel de pedido de un
 * cliente, compartido por todo lo que la lee (etiquetas de caja y etiquetas
 * de artículo).
 *
 * La hoja buena es la primera cuyo nombre empieza por "EAN" (la temporada
 * cambia: EAN H26, EAN E27...); el libro trae más hojas (bolsitas, copias
 * por artículo) que se ignoran. Las columnas se localizan por el texto de la
 * cabecera de la primera fila, no por posición, porque el cliente reordena
 * columnas entre temporadas.
 *
 * Es AutoCloseable porque mantiene abierto el Workbook de POI: usar siempre
 * dentro de un try-with-resources y no dejar escapar nada que dependa de él.
 */
public final class HojaEan implements AutoCloseable {

    private final Workbook libro;
    private final Sheet hoja;
    private final Row cabecera;

    private HojaEan(Workbook libro, Sheet hoja) {
        this.libro = libro;
        this.hoja = hoja;
        this.cabecera = hoja.getRow(hoja.getFirstRowNum());
        if (cabecera == null) {
            throw new IllegalArgumentException(
                    "La hoja '" + hoja.getSheetName() + "' del excel de pedido está vacía");
        }
    }

    public static HojaEan abrir(byte[] contenido) throws IOException {
        Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido));
        try {
            return new HojaEan(libro, primeraHojaEan(libro));
        } catch (RuntimeException e) {
            libro.close();
            throw e;
        }
    }

    /** Nombre de la hoja, ej. "EAN H26": de ahí sale la temporada. */
    public String nombre() {
        return hoja.getSheetName().trim();
    }

    /**
     * Índice de la primera columna cuya cabecera empieza por el título dado
     * (indiferente a mayúsculas). Lanza si no está: sin ella no se puede
     * hacer nada útil con el fichero, así que aquí sí se bloquea.
     */
    public int columna(String titulo) {
        return columna(titulo, titulo);
    }

    /**
     * Igual que {@link #columna(String)}, pero con un rótulo distinto para
     * el mensaje de error: la clave de búsqueda no siempre es cómoda de leer
     * ("LIBELL" para encontrar cualquier variante de "Libellé coloris"), y el
     * usuario que lee el aviso conoce su fichero por el nombre de columna
     * real, no por la clave interna.
     */
    public int columna(String titulo, String rotuloEnElError) {
        int indice = columnaOpcional(titulo);
        if (indice < 0) {
            throw new IllegalArgumentException("La hoja '" + nombre()
                    + "' del excel de pedido no tiene la columna '" + rotuloEnElError + "'");
        }
        return indice;
    }

    /**
     * Igual que {@link #columna(String)} pero devuelve -1 en vez de lanzar
     * cuando la columna no está. Para las columnas de las que se puede
     * prescindir: sin ellas la salida sale incompleta y con aviso, pero sale.
     */
    public int columnaOpcional(String titulo) {
        String buscado = titulo.toUpperCase(Locale.ROOT);
        for (Cell celda : cabecera) {
            if (texto(celda).trim().toUpperCase(Locale.ROOT).startsWith(buscado)) {
                return celda.getColumnIndex();
            }
        }
        return -1;
    }

    public int primeraFilaDatos() {
        return hoja.getFirstRowNum() + 1;
    }

    public int ultimaFila() {
        return hoja.getLastRowNum();
    }

    /**
     * Texto de una celda, o "" si la fila o la celda no existen. Los
     * numéricos se leen como enteros: el PO (7672) y la talla (75) están
     * guardados como número y no deben salir como "7672.0".
     */
    public String texto(int fila, int columna) {
        Row f = hoja.getRow(fila);
        return f == null ? "" : texto(f.getCell(columna));
    }

    @Override
    public void close() throws IOException {
        libro.close();
    }

    private static Sheet primeraHojaEan(Workbook libro) {
        for (int i = 0; i < libro.getNumberOfSheets(); i++) {
            if (libro.getSheetName(i).trim().toUpperCase(Locale.ROOT).startsWith("EAN")) {
                return libro.getSheetAt(i);
            }
        }
        throw new IllegalArgumentException(
                "El excel de pedido no tiene ninguna hoja 'EAN ...': ¿es el archivo correcto?");
    }

    private static String texto(Cell celda) {
        if (celda == null) {
            return "";
        }
        return switch (celda.getCellType()) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> String.valueOf((long) celda.getNumericCellValue());
            case FORMULA -> celda.getCachedFormulaResultType() == CellType.STRING
                    ? celda.getStringCellValue() : "";
            default -> "";
        };
    }
}
