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
 *
 * Los títulos se escriben tal como vienen del taller, con el salto de línea
 * dentro de la celda ("QTITE /\nCOLIS", "POIDS BRUT\nCAISSE"): es parte de lo
 * que el lector tiene que saber resolver.
 */
public final class PackingTallerExcel {

    private static final String[] TITULOS = {"", "CLIENT", "MOTIF", "REFERENCE", "COULEUR",
            "TAILLE", "DESTINATION", "CODE", "Nº EXPEDITION PUNTOTRES", "QTITE /\nCOLIS",
            "QUANTITE", "N° DE COLIS", "POIDS BRUT\nCAISSE", "DIMENSIONS \nCAISSE"};
    private static final int FILA_CABECERA = 8;
    private static final int COLUMNA_PESO = 12;
    private static final int COLUMNA_MEDIDA = 13;

    /**
     * Una fila del packing del taller. unidadesPorCaja, pesoBrutoCaja y
     * medidaCaja a null = columna vacía en esa fila, que es lo normal: el
     * taller solo pesa y mide algunas.
     */
    public record Fila(String cliente, String motivo, String referencia, String color,
                       String talla, String destino, String code, Integer unidadesPorCaja,
                       int cantidad, Double pesoBrutoCaja, String medidaCaja) {

        /** Lo mínimo: un artículo de AMI sin talla ni código. */
        public static Fila de(String cliente, String referencia, String color, int cantidad) {
            return new Fila(cliente, "PROD", referencia, color, "U", "", "", null, cantidad,
                    null, null);
        }

        public Fila conUnidadesPorCaja(Integer unidades) {
            return new Fila(cliente, motivo, referencia, color, talla, destino, code,
                    unidades, cantidad, pesoBrutoCaja, medidaCaja);
        }

        public Fila conTalla(String nuevaTalla) {
            return new Fila(cliente, motivo, referencia, color, nuevaTalla, destino, code,
                    unidadesPorCaja, cantidad, pesoBrutoCaja, medidaCaja);
        }

        public Fila conDestino(String nuevoDestino) {
            return new Fila(cliente, motivo, referencia, color, talla, nuevoDestino, code,
                    unidadesPorCaja, cantidad, pesoBrutoCaja, medidaCaja);
        }

        public Fila conCode(String nuevoCode) {
            return new Fila(cliente, motivo, referencia, color, talla, destino, nuevoCode,
                    unidadesPorCaja, cantidad, pesoBrutoCaja, medidaCaja);
        }

        /** El peso bruto de la caja de ESTA fila, con el cartón. */
        public Fila conPesoBrutoCaja(Double peso) {
            return new Fila(cliente, motivo, referencia, color, talla, destino, code,
                    unidadesPorCaja, cantidad, peso, medidaCaja);
        }

        /** El cartón de ESTA fila, tal como lo escribe el taller. */
        public Fila conMedidaCaja(String medida) {
            return new Fila(cliente, motivo, referencia, color, talla, destino, code,
                    unidadesPorCaja, cantidad, pesoBrutoCaja, medida);
        }
    }

    private PackingTallerExcel() {
    }

    public static byte[] crear(Fila... filas) {
        return crear(List.of(filas));
    }

    public static byte[] crear(List<Fila> filas) {
        return crearSin(List.of(), filas);
    }

    /**
     * El mismo packing pero sin alguna columna opcional, para los casos en
     * que un taller no la escribe. Los títulos se dan tal como salen en
     * {@link #TITULOS} ("Nº EXPEDITION PUNTOTRES", "QTITE /\nCOLIS").
     *
     * <p>Se quita solo el TÍTULO y no las celdas de debajo: sin cabecera la
     * columna no se reconoce, que es justo lo que pasa en el fichero real
     * cuando el taller no la rellena.
     */
    public static byte[] crearSin(List<String> titulosFuera, Fila... filas) {
        return crearSin(titulosFuera, List.of(filas));
    }

    public static byte[] crearSin(List<String> titulosFuera, List<Fila> filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.createSheet("FACTURE");
            Sheet hoja = libro.createSheet("LISTE DE COLIS");

            // Membrete: lo que hay encima de la cabecera en el fichero real.
            hoja.createRow(1).createCell(3).setCellValue("TALLER DE PRUEBA");
            hoja.createRow(4).createCell(3).setCellValue("COLISAGE:");

            Row cabecera = hoja.createRow(FILA_CABECERA);
            for (int c = 1; c < TITULOS.length; c++) {
                if (!titulosFuera.contains(TITULOS[c])) {
                    cabecera.createCell(c).setCellValue(TITULOS[c]);
                }
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
                if (fila.pesoBrutoCaja() != null) {
                    f.createCell(COLUMNA_PESO).setCellValue(fila.pesoBrutoCaja());
                }
                if (fila.medidaCaja() != null) {
                    f.createCell(COLUMNA_MEDIDA).setCellValue(fila.medidaCaja());
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
