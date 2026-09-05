package com.puntotres.packinglist.testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Construye en memoria un excel de pedido de AMI con la misma forma que el
 * real ("AMI EAN H26.xlsx"): una hoja señuelo delante y la hoja EAN con la
 * cabecera Made in/ARTICLE/COLORIS/Libellé coloris/(vacía)/TAILLE/PO/... .
 */
public final class PedidoAmiExcel {

    private static final String[] TITULOS = {"Made in", "ARTICLE", "COLORIS",
            "Libellé coloris", "", "TAILLE", "PO", "Commandé", "EAN13", "EAN128"};

    /**
     * po: String ("07704 CH") o Number (7672 = PO de France sin sufijo).
     * ean13/ean128: null para los tests que no los necesitan.
     */
    public record Fila(String madeIn, String article, String coloris, String libelle,
                       String taille, Object po, String ean13, String ean128,
                       Integer commande) {

        /** Sin EAN: firma que ya usaban los tests de etiquetas de caja. */
        public Fila(String madeIn, String article, String coloris,
                    String libelle, String taille, Object po) {
            this(madeIn, article, coloris, libelle, taille, po, null, null, null);
        }

        /** Solo con EAN13: firma que ya usaban los tests de etiquetas de artículo. */
        public Fila(String madeIn, String article, String coloris, String libelle,
                    String taille, Object po, String ean13) {
            this(madeIn, article, coloris, libelle, taille, po, ean13, null, null);
        }

        /** Con los dos EAN: firma que ya usaban los tests de códigos de barras. */
        public Fila(String madeIn, String article, String coloris, String libelle,
                    String taille, Object po, String ean13, String ean128) {
            this(madeIn, article, coloris, libelle, taille, po, ean13, ean128, null);
        }

        /**
         * Con cantidad pedida y sin EAN: lo que necesitan los tests de la
         * entrada por taller, donde lo que importa es "Commandé".
         */
        public static Fila pedida(String article, String coloris, String taille,
                                  Object po, int commande) {
            return new Fila("SPAIN", article, coloris, "", taille, po, null, null, commande);
        }
    }

    private PedidoAmiExcel() {
    }

    public static byte[] crear(String nombreHojaEan, Fila... filas) {
        return crear(nombreHojaEan, TITULOS.length, filas);
    }

    /**
     * El mismo libro pero sin las columnas EAN13/EAN128 en la cabecera, para
     * los tests de excels de pedido de temporadas antiguas.
     */
    public static byte[] crearSinColumnasEan(String nombreHojaEan, Fila... filas) {
        return crear(nombreHojaEan, 8, filas);
    }

    private static byte[] crear(String nombreHojaEan, int numColumnas, Fila... filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("BOLSITAS ANTIHUMEDAD"); // señuelo: no es la primera hoja EAN
            Sheet hoja = libro.createSheet(nombreHojaEan);
            Row cabecera = hoja.createRow(0);
            for (int i = 0; i < numColumnas; i++) {
                cabecera.createCell(i).setCellValue(TITULOS[i]);
            }
            int numFila = 1;
            for (Fila fila : filas) {
                Row f = hoja.createRow(numFila++);
                f.createCell(0).setCellValue(fila.madeIn());
                f.createCell(1).setCellValue(fila.article());
                f.createCell(2).setCellValue(fila.coloris());
                f.createCell(3).setCellValue(fila.libelle());
                f.createCell(5).setCellValue(fila.taille());
                if (fila.po() instanceof Number n) {
                    f.createCell(6).setCellValue(n.doubleValue());
                } else {
                    f.createCell(6).setCellValue((String) fila.po());
                }
                if (numColumnas > 7 && fila.commande() != null) {
                    f.createCell(7).setCellValue(fila.commande());
                }
                if (numColumnas > 8 && fila.ean13() != null) {
                    f.createCell(8).setCellValue(fila.ean13());
                }
                if (numColumnas > 9 && fila.ean128() != null) {
                    f.createCell(9).setCellValue(fila.ean128());
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
