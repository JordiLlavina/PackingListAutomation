package com.puntotres.packinglist.service.catalogo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * El excel de "Exportar / Importar" de la pantalla Colores y Pieles de
 * Cliente: las dos tablas de un cliente, una por hoja, para rellenarlas en
 * Excel y volver a subirlas.
 *
 * Cada hoja lleva arriba el cliente ("Cliente" | "APC"), para que un excel
 * de APC no se importe por descuido en la pantalla de AMI, y debajo la
 * cabecera y una fila por código. La columna del código va en formato texto:
 * un código como "001" tecleado en una fila nueva se quedaría en "1".
 *
 * Al leer, una hoja que no viene se devuelve nula (no hay nada que importar de
 * esa tabla) y el excel tiene que traer al menos una de las dos.
 */
public final class ExcelColoresPieles {

    public static final String HOJA_COLORES = "Colores";
    public static final String HOJA_PIELES = "Pieles";
    static final String ROTULO_CLIENTE = "Cliente";
    static final String CABECERA_COLORES = "Código";
    static final String CABECERA_PIELES = "Ref. piel";
    private static final int FILA_CABECERA = 2;
    private static final String NOTA = "Rellena la segunda columna y vuelve a subir el excel con «Importar». "
            + "Se pueden añadir filas al final. Una casilla vacía no borra nada; para borrar, hazlo en la pantalla.";

    /** Una fila de cualquiera de las dos tablas: el código (o la ref de piel) y su texto. */
    public record Fila(String codigo, String valor) {
    }

    /**
     * Lo leído: el cliente que dice el excel (vacío si no lo dice) y, de cada
     * hoja, código → texto en el orden del excel; {@code null} la hoja que no
     * viene. Los códigos van en mayúsculas y sin espacios alrededor; los
     * textos, sin espacios alrededor y vacíos si la casilla lo está.
     */
    public record Leido(String cliente, Map<String, String> colores, Map<String, String> pieles) {
    }

    private ExcelColoresPieles() {
    }

    public static byte[] escribir(String cliente, List<Fila> colores, List<Fila> pieles) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            hoja(libro, estilos, HOJA_COLORES, cliente, CABECERA_COLORES, "Descripción", colores);
            hoja(libro, estilos, HOJA_PIELES, cliente, CABECERA_PIELES, "Nombre de la piel", pieles);
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    /** @throws IllegalArgumentException si no es un excel o no trae ninguna de las dos hojas con su cabecera */
    public static Leido leer(byte[] excel) {
        Workbook abierto;
        try {
            abierto = WorkbookFactory.create(new ByteArrayInputStream(excel));
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("no es un excel que se pueda abrir (.xlsx)", e);
        }
        try (Workbook libro = abierto) {
            FormulaEvaluator evaluador = libro.getCreationHelper().createFormulaEvaluator();
            DataFormatter formato = new DataFormatter();
            Sheet deColores = buscarHoja(libro, HOJA_COLORES);
            Sheet dePieles = buscarHoja(libro, HOJA_PIELES);
            if (deColores == null && dePieles == null) {
                throw new IllegalArgumentException("no tiene ninguna hoja «" + HOJA_COLORES + "» ni «"
                        + HOJA_PIELES + "»: tiene que ser el excel que se exporta desde esta pantalla");
            }
            String cliente = cliente(deColores != null ? deColores : dePieles, formato, evaluador);
            if (cliente.isEmpty() && deColores != null && dePieles != null) {
                cliente = cliente(dePieles, formato, evaluador);
            }
            return new Leido(cliente,
                    deColores == null ? null : filas(deColores, CABECERA_COLORES, formato, evaluador),
                    dePieles == null ? null : filas(dePieles, CABECERA_PIELES, formato, evaluador));
        } catch (IOException e) {
            // Solo al cerrar: lo leído ya está.
            throw new IllegalArgumentException("no es un excel que se pueda abrir (.xlsx)", e);
        }
    }

    // --- Escribir ---

    private static void hoja(Workbook libro, Estilos estilos, String nombre, String cliente,
                             String cabeceraCodigo, String cabeceraValor, List<Fila> filas) {
        Sheet hoja = libro.createSheet(nombre);
        hoja.setColumnWidth(0, 16 * 256);
        hoja.setColumnWidth(1, 42 * 256);
        // El código en texto también en las filas que se añadan a mano.
        hoja.setDefaultColumnStyle(0, estilos.texto);
        hoja.setDefaultColumnStyle(1, estilos.texto);

        Row deCliente = hoja.createRow(0);
        celda(deCliente, 0, ROTULO_CLIENTE, estilos.rotulo);
        celda(deCliente, 1, cliente, estilos.rotulo);
        celda(deCliente, 2, NOTA, estilos.nota);

        Row cabecera = hoja.createRow(FILA_CABECERA);
        celda(cabecera, 0, cabeceraCodigo, estilos.cabecera);
        celda(cabecera, 1, cabeceraValor, estilos.cabecera);

        int numero = FILA_CABECERA + 1;
        for (Fila fila : filas) {
            Row row = hoja.createRow(numero++);
            celda(row, 0, fila.codigo(), estilos.texto);
            celda(row, 1, fila.valor() == null ? "" : fila.valor(), estilos.texto);
        }
        hoja.createFreezePane(0, FILA_CABECERA + 1);
    }

    private static void celda(Row fila, int columna, String valor, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor);
        celda.setCellStyle(estilo);
    }

    private static final class Estilos {

        final CellStyle texto;
        final CellStyle cabecera;
        final CellStyle rotulo;
        final CellStyle nota;

        Estilos(Workbook libro) {
            short arroba = libro.createDataFormat().getFormat("@");
            texto = libro.createCellStyle();
            texto.setDataFormat(arroba);

            Font negrita = libro.createFont();
            negrita.setBold(true);
            cabecera = libro.createCellStyle();
            cabecera.setDataFormat(arroba);
            cabecera.setFont(negrita);
            cabecera.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            rotulo = libro.createCellStyle();
            rotulo.setDataFormat(arroba);
            rotulo.setFont(negrita);

            Font cursiva = libro.createFont();
            cursiva.setItalic(true);
            cursiva.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            nota = libro.createCellStyle();
            nota.setFont(cursiva);
        }
    }

    // --- Leer ---

    private static Sheet buscarHoja(Workbook libro, String nombre) {
        for (Sheet hoja : libro) {
            if (hoja.getSheetName().trim().equalsIgnoreCase(nombre)) {
                return hoja;
            }
        }
        return null;
    }

    /** El cliente de la fila "Cliente" de arriba, o vacío si se ha borrado. */
    private static String cliente(Sheet hoja, DataFormatter formato, FormulaEvaluator evaluador) {
        for (int numero = 0; numero < FILA_CABECERA && numero <= hoja.getLastRowNum(); numero++) {
            Row fila = hoja.getRow(numero);
            if (fila != null && texto(fila, 0, formato, evaluador).equalsIgnoreCase(ROTULO_CLIENTE)) {
                return clave(texto(fila, 1, formato, evaluador));
            }
        }
        return "";
    }

    private static Map<String, String> filas(Sheet hoja, String cabecera, DataFormatter formato,
                                             FormulaEvaluator evaluador) {
        int numeroCabecera = -1;
        for (int numero = 0; numero <= hoja.getLastRowNum(); numero++) {
            Row fila = hoja.getRow(numero);
            if (fila != null && texto(fila, 0, formato, evaluador).equalsIgnoreCase(cabecera)) {
                numeroCabecera = numero;
                break;
            }
        }
        if (numeroCabecera < 0) {
            throw new IllegalArgumentException("la hoja «" + hoja.getSheetName() + "» no tiene la cabecera «"
                    + cabecera + "»: tiene que ser el excel que se exporta desde esta pantalla");
        }
        Map<String, String> filas = new LinkedHashMap<>();
        for (int numero = numeroCabecera + 1; numero <= hoja.getLastRowNum(); numero++) {
            Row fila = hoja.getRow(numero);
            if (fila == null) {
                continue;
            }
            String codigo = clave(texto(fila, 0, formato, evaluador));
            if (!codigo.isEmpty()) {
                filas.put(codigo, texto(fila, 1, formato, evaluador));
            }
        }
        return filas;
    }

    /** Lo que se ve en la casilla, sin espacios alrededor: "001" sigue siendo "001" y una fórmula, su resultado. */
    private static String texto(Row fila, int columna, DataFormatter formato, FormulaEvaluator evaluador) {
        Cell celda = fila.getCell(columna);
        return celda == null ? "" : formato.formatCellValue(celda, evaluador).trim();
    }

    private static String clave(String texto) {
        return texto.trim().toUpperCase(Locale.ROOT);
    }
}
