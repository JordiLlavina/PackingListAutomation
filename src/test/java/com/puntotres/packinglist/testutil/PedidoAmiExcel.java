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

    /**
     * po: String ("07704 CH") o Number (7672 = PO de France sin sufijo).
     * ean13: null para los tests que no lo necesitan (etiquetas de caja).
     */
    public record Fila(String madeIn, String article, String coloris,
                       String libelle, String taille, Object po, String ean13) {

        /** Sin EAN13: firma que ya usaban los tests de etiquetas de caja. */
        public Fila(String madeIn, String article, String coloris,
                    String libelle, String taille, Object po) {
            this(madeIn, article, coloris, libelle, taille, po, null);
        }
    }

    private PedidoAmiExcel() {
    }

    public static byte[] crear(String nombreHojaEan, Fila... filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("BOLSITAS ANTIHUMEDAD"); // señuelo: no es la primera hoja EAN
            Sheet hoja = libro.createSheet(nombreHojaEan);
            Row cabecera = hoja.createRow(0);
            String[] titulos = {"Made in", "ARTICLE", "COLORIS", "Libellé coloris",
                    "", "TAILLE", "PO", "Commandé", "EAN13", "EAN128"};
            for (int i = 0; i < titulos.length; i++) {
                cabecera.createCell(i).setCellValue(titulos[i]);
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
                if (fila.ean13() != null) {
                    f.createCell(8).setCellValue(fila.ean13());
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
