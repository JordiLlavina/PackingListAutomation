package com.puntotres.packinglist.service.etiquetas;

import java.util.Set;

import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.xssf.usermodel.XSSFPrintSetup;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * La rejilla de 4 columnas de etiquetas que cabe justa en un A4: anchos,
 * altos, pageSetup, márgenes y la maquetación de bloques (referencia+talla en
 * la fila base, color+pedido en la siguiente, código de barras debajo).
 *
 * La maquetación está medida del fichero real del cliente
 * "AMI CODE BARRE H26 MOROCCO.xlsx" y la fija EtiquetasArticuloMaquetacionTest,
 * que compara lo generado contra ese fichero. NO hay plantilla .xlsx: POI no
 * copia el pageSetup al clonar hojas ("Cloning sheets with page setup is not
 * yet supported"), que es justo lo único que interesaba heredar, así que
 * heredarla no servía de nada.
 *
 * Lo usan las etiquetas de artículo (una hoja por fila del pedido, 40
 * etiquetas idénticas por hoja) y la hoja "CODIGOS BARRAS EXTRA" de las
 * etiquetas de caja de AMI, que reutiliza la misma rejilla con menos bloques.
 */
public final class RejillaEtiquetas {

    /** Anchos de columna en unidades POI (caracteres × 256). */
    private static final int[] ANCHOS_COLUMNA =
            {3766, 4425, 621, 3766, 4534, 512, 3766, 4534, 621, 3766, 4534};

    /**
     * Columna izquierda de cada par de columnas de etiqueta. La derecha es
     * la siguiente; las columnas 2, 5 y 8 son separadores estrechos.
     */
    public static final int[] COLUMNAS_IZQUIERDA = {0, 3, 6, 9};

    public static final int BLOQUES_POR_PAGINA = 10;
    public static final int FILAS_POR_BLOQUE = 8;
    /** Fila 0-based del primer bloque: la 0 es el margen superior. */
    private static final int PRIMERA_FILA_BLOQUE = 1;
    public static final int ETIQUETAS_POR_HOJA = COLUMNAS_IZQUIERDA.length * BLOQUES_POR_PAGINA;

    private static final float ALTO_MARGEN_SUPERIOR = 6f;
    private static final float ALTO_SEPARADORA = 9.95f;
    private static final float ALTO_DEFECTO = 15f;

    private static final short ESCALA = 74;
    private static final double MARGEN_IZQUIERDO = 0.0;
    /** 1 mm en pulgadas, que es lo que guarda el fichero del cliente. */
    private static final double MARGEN = 0.03937007874015748;
    /** Margen de cabecera/pie del fichero del cliente, en pulgadas. */
    private static final double MARGEN_CABECERA_PIE = 0.31496062992125984;

    /**
     * Longitud máxima de un nombre de hoja en Excel. Es la restricción de
     * Excel que esta rejilla impone al crear la hoja, y
     * AmiEtiquetasArticuloGenerador (y cualquier otro cliente futuro) la
     * reutiliza al componer el nombre en vez de declarar la suya.
     */
    public static final int MAX_NOMBRE_HOJA = 31;

    private RejillaEtiquetas() {
    }

    /** Fila 0-based donde arranca el bloque: 1, 9, 17 ... 73. */
    public static int filaBase(int bloque) {
        return PRIMERA_FILA_BLOQUE + bloque * FILAS_POR_BLOQUE;
    }

    /**
     * Una hoja con la rejilla maquetada para {@code bloques} etiquetas.
     *
     * La separadora se omite en el último bloque de cada página y en el
     * último de la hoja: crearla haría la página 9,95 pt más alta y sacaría
     * una página de más al imprimir (el fichero real del cliente termina en
     * la fila 74). Con bloques = BLOQUES_POR_PAGINA sale exactamente la hoja
     * de las etiquetas de artículo de siempre.
     */
    public static XSSFSheet crearHojaMaquetada(XSSFWorkbook libro, String nombre, int bloques) {
        XSSFSheet hoja = libro.createSheet(nombre);
        for (int columna = 0; columna < ANCHOS_COLUMNA.length; columna++) {
            hoja.setColumnWidth(columna, ANCHOS_COLUMNA[columna]);
        }
        hoja.setDefaultRowHeightInPoints(ALTO_DEFECTO);
        hoja.createRow(0).setHeightInPoints(ALTO_MARGEN_SUPERIOR);
        for (int bloque = 0; bloque < bloques; bloque++) {
            int base = filaBase(bloque);
            hoja.createRow(base);
            hoja.createRow(base + 1);
            boolean ultimoDePagina = (bloque + 1) % BLOQUES_POR_PAGINA == 0;
            boolean ultimoDeLaHoja = bloque == bloques - 1;
            if (!ultimoDePagina && !ultimoDeLaHoja) {
                hoja.createRow(base + FILAS_POR_BLOQUE - 1)
                        .setHeightInPoints(ALTO_SEPARADORA);
            }
            if (ultimoDePagina && !ultimoDeLaHoja) {
                hoja.setRowBreak(base + FILAS_POR_BLOQUE - 1);
            }
        }
        XSSFPrintSetup impresion = hoja.getPrintSetup();
        impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
        impresion.setScale(ESCALA);
        impresion.setLandscape(false);
        hoja.setMargin(PageMargin.LEFT, MARGEN_IZQUIERDO);
        hoja.setMargin(PageMargin.RIGHT, MARGEN);
        hoja.setMargin(PageMargin.TOP, MARGEN);
        hoja.setMargin(PageMargin.BOTTOM, MARGEN);
        hoja.setMargin(PageMargin.HEADER, MARGEN_CABECERA_PIE);
        hoja.setMargin(PageMargin.FOOTER, MARGEN_CABECERA_PIE);
        return hoja;
    }

    /**
     * Excel no admite dos hojas con el mismo nombre. El nombre llega ya
     * saneado y recortado desde el generador del cliente; esto es la última
     * red: dos filas idénticas en el pedido no deben romper la generación.
     */
    public static String nombreUnico(Set<String> usados, String nombre) {
        if (usados.add(nombre)) {
            return nombre;
        }
        for (int n = 2; ; n++) {
            String sufijo = "-" + n;
            String candidato = nombre.length() + sufijo.length() <= MAX_NOMBRE_HOJA
                    ? nombre + sufijo
                    : nombre.substring(0, MAX_NOMBRE_HOJA - sufijo.length()) + sufijo;
            if (usados.add(candidato)) {
                return candidato;
            }
        }
    }
}
