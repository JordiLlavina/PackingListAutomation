package com.puntotres.packinglist.testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Construye en memoria un packing list de taller con la misma forma que el
 * real: una hoja FACTURE delante, la hoja LISTE DE COLIS con membrete encima
 * y la cabecera en la fila 9.
 */
public final class PackingTallerExcel {

    private static final String[] TITULOS = {"", "CLIENT", "MOTIF", "REFERENCE", "COULEUR",
            "TAILLE", "DESTINATION", "CODE", "Nº EXPEDITION PUNTOTRES", "QTITE /\nCOLIS",
            "QUANTITE", "N° DE COLIS"};
    private static final int FILA_CABECERA = 8;

    /** Una fila del packing del taller. unidadesPorCaja null = columna vacía. */
    public record Fila(String cliente, String motivo, String referencia, String color,
                       String talla, String destino, String code, Integer unidadesPorCaja,
                       int cantidad) {

        /** Lo mínimo: un artículo de AMI sin talla ni código. */
        public static Fila de(String cliente, String referencia, String color, int cantidad) {
            return new Fila(cliente, "PROD", referencia, color, "U", "", "", null, cantidad);
        }

        public Fila conUnidadesPorCaja(Integer unidades) {
            return new Fila(cliente, motivo, referencia, color, talla, destino, code,
                    unidades, cantidad);
        }

        public Fila conTalla(String nuevaTalla) {
            return new Fila(cliente, motivo, referencia, color, nuevaTalla, destino, code,
                    unidadesPorCaja, cantidad);
        }

        public Fila conDestino(String nuevoDestino) {
            return new Fila(cliente, motivo, referencia, color, talla, nuevoDestino, code,
                    unidadesPorCaja, cantidad);
        }

        public Fila conCode(String nuevoCode) {
            return new Fila(cliente, motivo, referencia, color, talla, destino, nuevoCode,
                    unidadesPorCaja, cantidad);
        }
    }

    private PackingTallerExcel() {
    }

    public static byte[] crear(Fila... filas) {
        return crear(List.of(filas));
    }

    public static byte[] crear(List<Fila> filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.createSheet("FACTURE");
            Sheet hoja = libro.createSheet("LISTE DE COLIS");

            // Membrete: lo que hay encima de la cabecera en el fichero real.
            hoja.createRow(1).createCell(3).setCellValue("TALLER DE PRUEBA");
            hoja.createRow(4).createCell(3).setCellValue("COLISAGE:");

            Row cabecera = hoja.createRow(FILA_CABECERA);
            for (int c = 1; c < TITULOS.length; c++) {
                cabecera.createCell(c).setCellValue(TITULOS[c]);
            }

            int numFila = FILA_CABECERA + 1;
            for (Fila fila : filas) {
                Row f = hoja.createRow(numFila++);
                f.createCell(1).setCellValue(fila.cliente());
                f.createCell(2).setCellValue(fila.motivo());
                f.createCell(3).setCellValue(fila.referencia());
                f.createCell(4).setCellValue(fila.color());
                f.createCell(5).setCellValue(fila.talla());
                f.createCell(6).setCellValue(fila.destino());
                f.createCell(7).setCellValue(fila.code());
                if (fila.unidadesPorCaja() != null) {
                    f.createCell(9).setCellValue(fila.unidadesPorCaja());
                }
                f.createCell(10).setCellValue(fila.cantidad());
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
