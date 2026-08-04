package com.puntotres.packinglist.service.etiquetas;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Escribe una etiqueta de artículo dentro de la rejilla de RejillaEtiquetas:
 * las cuatro celdas (referencia y talla arriba, color y pedido abajo) y el
 * anclaje de su código de barras.
 *
 * Los estilos se crean UNA vez por libro y se cachean: POI los acumula por
 * libro, no por hoja, y un fichero de cinturones llega a 79 hojas. Por eso
 * hay que crear UNA instancia por libro y reutilizarla.
 *
 * Lo usan las etiquetas de artículo (una hoja por fila del pedido, 40
 * etiquetas idénticas por hoja) y la hoja "CODIGOS BARRAS EXTRA" de las
 * etiquetas de caja de AMI (un artículo por bloque, dos bloques por artículo:
 * uno con el EAN-13 y otro con el EAN128).
 */
public final class BloqueEtiquetaArticulo {

    /** Filas por debajo de la base donde arranca el código de barras. */
    public static final int BARCODE_OFFSET_FILA = 2;
    /** Desplazamiento y tamaño del código de barras, en EMU. dx lo centra. */
    private static final long BARCODE_DX = 342901;
    private static final long BARCODE_DY = 9525;
    private static final long BARCODE_CX = 1463802;
    private static final long BARCODE_CY = 647700;

    /**
     * Cuerpo de la celda de color en veinteavos de punto, según la longitud
     * del texto: {longitud máxima, cuerpo}. Los ficheros del cliente lo
     * hacen a mano y de forma desigual (se ven 10,5 / 10 / 9 / 6pt para
     * longitudes solapadas); aquí es una regla determinista.
     */
    private static final int[][] CUERPO_COLOR_POR_LONGITUD = {
            {14, 210},                // hasta 14 caracteres: 10,5 pt
            {17, 180},                // 15 a 17:              9 pt
            {Integer.MAX_VALUE, 160}  // 18 o más:             8 pt
    };

    private final Estilos estilos;

    public BloqueEtiquetaArticulo(XSSFWorkbook libro) {
        this.estilos = new Estilos(libro);
    }

    /**
     * Escribe las cuatro celdas en las filas filaBase y filaBase+1, que
     * RejillaEtiquetas.crearHojaMaquetada ya ha creado.
     */
    public void escribirTextos(XSSFSheet hoja, int filaBase, int columnaIzquierda,
                               EtiquetaArticulo etiqueta) {
        escribir(hoja, filaBase, columnaIzquierda, etiqueta.referencia(), null);
        escribir(hoja, filaBase, columnaIzquierda + 1, etiqueta.talla(), estilos.derecha());
        escribir(hoja, filaBase + 1, columnaIzquierda, etiqueta.color(),
                estilos.color(etiqueta.color()));
        escribir(hoja, filaBase + 1, columnaIzquierda + 1, etiqueta.pedido(),
                estilos.derecha());
    }

    /** Dónde va el código de barras de ese bloque. */
    public static XSSFClientAnchor anclajeCodigo(XSSFSheet hoja, int filaBase,
                                                 int columnaIzquierda) {
        return AnclajeImagen.fijo(hoja, columnaIzquierda, BARCODE_DX,
                filaBase + BARCODE_OFFSET_FILA, BARCODE_DY, BARCODE_CX, BARCODE_CY);
    }

    /** Cuerpo en veinteavos de punto para un nombre de color. */
    public static int cuerpoPara(String texto) {
        int longitud = texto == null ? 0 : texto.length();
        for (int[] tramo : CUERPO_COLOR_POR_LONGITUD) {
            if (longitud <= tramo[0]) {
                return tramo[1];
            }
        }
        return CUERPO_COLOR_POR_LONGITUD[CUERPO_COLOR_POR_LONGITUD.length - 1][1];
    }

    /**
     * Escribe en una celda de una fila que crearHojaMaquetada ya ha creado
     * (siempre base o base+1): no hay rama defensiva de "por si no existe",
     * porque con la maquetación fija esas filas siempre existen.
     */
    private static void escribir(XSSFSheet hoja, int fila, int columna, String valor,
                                 CellStyle estilo) {
        Cell celda = hoja.getRow(fila).createCell(columna);
        if (valor == null || valor.isBlank()) {
            celda.setBlank();
        } else {
            celda.setCellValue(valor);
        }
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    /**
     * Estilos creados UNA sola vez por libro y cacheados: POI los acumula
     * por libro, no por hoja, y un fichero de cinturones llega a 79 hojas.
     */
    private static final class Estilos {

        private final XSSFWorkbook libro;
        private final Map<Integer, CellStyle> porCuerpo = new HashMap<>();
        private CellStyle derecha;

        Estilos(XSSFWorkbook libro) {
            this.libro = libro;
        }

        /** Talla y pedido, en la columna derecha de la etiqueta. */
        CellStyle derecha() {
            if (derecha == null) {
                derecha = libro.createCellStyle();
                derecha.setAlignment(HorizontalAlignment.RIGHT);
            }
            return derecha;
        }

        /** Color, con el cuerpo reducido si el nombre es largo. */
        CellStyle color(String texto) {
            return porCuerpo.computeIfAbsent(cuerpoPara(texto), cuerpo -> {
                Font fuente = libro.createFont();
                // setFontHeight va en veinteavos de punto:
                // setFontHeightInPoints solo acepta puntos enteros y hacen
                // falta los 10,5pt del fichero del cliente.
                fuente.setFontHeight((short) cuerpo.intValue());
                CellStyle estilo = libro.createCellStyle();
                estilo.setFont(fuente);
                return estilo;
            });
        }
    }
}
