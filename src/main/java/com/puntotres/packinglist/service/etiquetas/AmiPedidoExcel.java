package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Índice en memoria del excel de pedido de la temporada de AMI (el que sube
 * el usuario en el Paso 2, ej. "AMI EAN H26.xlsx").
 *
 * La hoja buena es la primera cuyo nombre empieza por "EAN" (la temporada
 * cambia: EAN H26, EAN E27...); el libro trae más hojas (bolsitas, copias
 * por artículo) que se ignoran. Las columnas se localizan por el texto de
 * la cabecera de la fila 1, no por posición.
 *
 * La columna PO codifica la destinación: "NNNNN CH" (China), "NNNNN JP"
 * (Japan) o un número sin sufijo (France). El order number de la etiqueta
 * es siempre la parte numérica con padding a 5 dígitos.
 */
public class AmiPedidoExcel {

    /** Una coincidencia del pedido: order number (5 dígitos) y color code ("001 BLACK"). */
    public record FilaPedido(String orderNumber, String colorCode) {
    }

    private record FilaCruda(String article, String coloris, String libelle,
                             String poNumerico, String poSufijo) {
    }

    private final List<FilaCruda> filas;

    private AmiPedidoExcel(List<FilaCruda> filas) {
        this.filas = filas;
    }

    public static AmiPedidoExcel desdeBytes(byte[] contenido) throws IOException {
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet hoja = hojaEan(libro);
            Row cabecera = hoja.getRow(hoja.getFirstRowNum());
            int colArticle = columna(cabecera, "ARTICLE");
            int colColoris = columna(cabecera, "COLORIS");
            int colLibelle = columna(cabecera, "LIBELL");
            int colPo = columna(cabecera, "PO");

            List<FilaCruda> filas = new ArrayList<>();
            for (int i = hoja.getFirstRowNum() + 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) {
                    continue;
                }
                String article = texto(fila.getCell(colArticle));
                String po = textoPo(fila.getCell(colPo));
                if (article.isBlank() || po.isBlank()) {
                    continue;
                }
                String numerico = po.replaceAll("[^0-9]", "");
                if (numerico.isBlank()) {
                    continue;
                }
                String sufijo = po.replaceAll("[0-9\\s]", "").toUpperCase(Locale.ROOT);
                filas.add(new FilaCruda(
                        article.trim().toUpperCase(Locale.ROOT),
                        texto(fila.getCell(colColoris)).trim(),
                        texto(fila.getCell(colLibelle)).trim(),
                        String.format("%05d", Long.parseLong(numerico)),
                        sufijo.isBlank() ? null : sufijo));
            }
            return new AmiPedidoExcel(filas);
        }
    }

    /**
     * Busca la fila del pedido para una referencia y destinación. Si hay
     * varias (cinturones: una por talla) da igual cuál: comparten PO y
     * color; se prefiere la que coincida en COLORIS con el color del JSON.
     */
    public Optional<FilaPedido> buscar(String referencia, String codigoColor, String sufijoPo) {
        String ref = referencia == null ? "" : referencia.trim().toUpperCase(Locale.ROOT);
        List<FilaCruda> candidatas = filas.stream()
                .filter(fila -> fila.article().equals(ref))
                .filter(fila -> sufijoPo == null
                        ? fila.poSufijo() == null
                        : sufijoPo.equalsIgnoreCase(fila.poSufijo()))
                .toList();
        if (candidatas.isEmpty()) {
            return Optional.empty();
        }
        FilaCruda elegida = candidatas.stream()
                .filter(fila -> fila.coloris().equalsIgnoreCase(codigoColor == null ? "" : codigoColor.trim()))
                .findFirst()
                .orElse(candidatas.get(0));
        String colorCode = elegida.libelle().isBlank()
                ? elegida.coloris()
                : elegida.coloris() + " " + elegida.libelle();
        return Optional.of(new FilaPedido(elegida.poNumerico(), colorCode));
    }

    private static Sheet hojaEan(Workbook libro) {
        for (int i = 0; i < libro.getNumberOfSheets(); i++) {
            if (libro.getSheetName(i).trim().toUpperCase(Locale.ROOT).startsWith("EAN")) {
                return libro.getSheetAt(i);
            }
        }
        throw new IllegalArgumentException(
                "El excel de pedido no tiene ninguna hoja 'EAN ...': ¿es el archivo correcto?");
    }

    private static int columna(Row cabecera, String titulo) {
        for (Cell celda : cabecera) {
            if (texto(celda).trim().toUpperCase(Locale.ROOT).startsWith(titulo)) {
                return celda.getColumnIndex();
            }
        }
        throw new IllegalArgumentException(
                "La hoja EAN del pedido no tiene la columna '" + titulo + "'");
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

    /** El PO puede ser texto ("07704 CH") o numérico (7672.0). */
    private static String textoPo(Cell celda) {
        if (celda == null) {
            return "";
        }
        if (celda.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) celda.getNumericCellValue());
        }
        return texto(celda);
    }
}
