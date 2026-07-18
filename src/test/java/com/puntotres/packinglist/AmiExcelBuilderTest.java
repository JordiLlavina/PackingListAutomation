package com.puntotres.packinglist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Tests directos del builder de POI: la clase con más riesgo real del
 * proyecto (shiftRows, clonado de estilos, fórmulas). Antes de estos tests
 * solo se ejercía indirectamente vía PackingListGenerationServiceTest, que
 * nunca comprobaba el bloque "SUM UP" (resumen).
 */
class AmiExcelBuilderTest {

    private static final String NOMBRE_HOJA = "STANDARD PKL H26";
    private static final int COL_PESO_BRUTO = 21; // V

    private final AmiExcelBuilder builder = new AmiExcelBuilder();

    @Test
    void unaCajaEscribeCabeceraFilaDeTotalesYResumenCorrectos() throws Exception {
        PackingListData data = data(caja(1, "OF-1", "BOLSO", "NAT03", 10, "60x40x30", 8.0, 9.2));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(builder.generar(data)))) {
            Sheet hoja = wb.getSheet(NOMBRE_HOJA);

            // Cabecera.
            assertEquals("PUNTOTRES", texto(hoja, 2, 1));   // B3
            assertEquals("BADALONA", texto(hoja, 2, 4));    // E3
            assertEquals("PUN", texto(hoja, 3, 1));         // B4
            assertEquals("SPAIN", texto(hoja, 3, 4));       // E4
            assertEquals("FA-1", texto(hoja, 5, 1));        // B6
            assertEquals(LocalDate.of(2026, 7, 10), fecha(hoja, 6, 1));  // B7
            assertEquals(LocalDate.of(2026, 7, 24), fecha(hoja, 8, 1));  // B9
            assertEquals("France", texto(hoja, 9, 1));      // B10

            // Fila de la única caja (índice 19, la fila modelo reutilizada).
            Row filaCaja = hoja.getRow(19);
            assertEquals("H26", filaCaja.getCell(0).getStringCellValue());
            assertEquals("OF-1", filaCaja.getCell(1).getStringCellValue());
            assertEquals("BOLSO", filaCaja.getCell(2).getStringCellValue());
            assertEquals("NAT03", filaCaja.getCell(3).getStringCellValue());
            assertEquals(1, (int) filaCaja.getCell(4).getNumericCellValue());
            assertEquals(10, (int) filaCaja.getCell(6).getNumericCellValue());
            assertEquals("SUM(G20:R20)", filaCaja.getCell(18).getCellFormula());
            assertEquals("60x40x30", filaCaja.getCell(19).getStringCellValue());
            assertEquals(8.0, filaCaja.getCell(20).getNumericCellValue());
            assertEquals(9.2, filaCaja.getCell(21).getNumericCellValue());

            // Fila de totales: con 1 caja no hay shiftRows, se queda en el
            // índice 20 (Excel 21), sumando sobre el único rango de datos (20:20).
            Row filaTotales = hoja.getRow(20);
            assertEquals("SUM(G20:G20)", filaTotales.getCell(6).getCellFormula());
            assertEquals("SUM(U20:U20)", filaTotales.getCell(20).getCellFormula());
            assertEquals("SUM(V20:V20)", filaTotales.getCell(21).getCellFormula());

            // Bloque resumen (sin desplazamiento porque numCajas == 1).
            assertEquals("+S21", celda(hoja, 23, COL_PESO_BRUTO).getCellFormula());
            assertEquals(1, celda(hoja, 24, COL_PESO_BRUTO).getNumericCellValue());
            assertEquals("V21", celda(hoja, 25, COL_PESO_BRUTO).getCellFormula());
            assertEquals("U21", celda(hoja, 26, COL_PESO_BRUTO).getCellFormula());
            assertEquals(0.072, celda(hoja, 27, COL_PESO_BRUTO).getNumericCellValue(), 0.0001); // 0.6*0.4*0.3
        }
    }

    @Test
    void variasCajasDesplazaLaFilaDeTotalesYElResumen() throws Exception {
        PackingListData data = data(
                caja(1, "OF-1", "BOLSO", "NAT03", 10, "60x40x40", 8.0, 9.6),
                caja(2, "OF-1", "BOLSO", "NAT03", 10, "60x40x40", 8.0, 9.6),
                caja(3, "OF-1", "BOLSO", "NAT03", 10, "60x40x40", 8.0, 9.6),
                caja(4, "OF-1", "BOLSO", "NAT03", 10, "60x40x40", 8.0, 9.6));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(builder.generar(data)))) {
            Sheet hoja = wb.getSheet(NOMBRE_HOJA);

            // 4 cajas: filas de datos en índices 19-22 (Excel 20-23).
            for (int i = 0; i < 4; i++) {
                assertEquals(i + 1, (int) hoja.getRow(19 + i).getCell(4).getNumericCellValue());
            }

            // Fila de totales desplazada al índice 23 (Excel 24), rango 20:23.
            Row filaTotales = hoja.getRow(23);
            assertEquals("SUM(G20:G23)", filaTotales.getCell(6).getCellFormula());

            // Resumen desplazado 3 filas (desplazamiento = numCajas - 1 = 3).
            assertEquals("+S24", celda(hoja, 26, COL_PESO_BRUTO).getCellFormula());
            assertEquals(4, celda(hoja, 27, COL_PESO_BRUTO).getNumericCellValue());
            assertEquals("V24", celda(hoja, 28, COL_PESO_BRUTO).getCellFormula());
            assertEquals("U24", celda(hoja, 29, COL_PESO_BRUTO).getCellFormula());
            assertEquals(0.384, celda(hoja, 30, COL_PESO_BRUTO).getNumericCellValue(), 0.0001); // 4 * 0.6*0.4*0.4
        }
    }

    @Test
    void pesosNulosDejanLasCeldasUyVEnBlanco() throws Exception {
        PackingListData data = data(caja(1, "OF-1", "BOLSO", "NAT03", 10, "60x40x30", null, null));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(builder.generar(data)))) {
            Row filaCaja = wb.getSheet(NOMBRE_HOJA).getRow(19);
            assertEquals(CellType.BLANK, filaCaja.getCell(20).getCellType());
            assertEquals(CellType.BLANK, filaCaja.getCell(21).getCellType());
        }
    }

    @Test
    void listaDeCajasVaciaLanzaExcepcion() {
        PackingListData data = data();

        assertThrows(IllegalArgumentException.class, () -> builder.generar(data));
    }

    @Test
    void tamanoCajaMalFormadoLanzaExcepcionAlCalcularElVolumen() {
        PackingListData data = data(caja(1, "OF-1", "BOLSO", "NAT03", 10, "60x40", 8.0, 9.2));

        assertThrows(IllegalArgumentException.class, () -> builder.generar(data));
    }

    private static PackingListData data(PackingListData.Caja... cajas) {
        PackingListData data = new PackingListData();
        data.setDestino("France");
        data.setTemporada("H26");
        data.setNumeroFactura("FA-1");
        data.setFechaFactura("10/07/2026");
        data.setFechaEnvio("24/07/2026");
        List<PackingListData.Caja> lista = new ArrayList<>(List.of(cajas));
        data.setCajas(lista);
        return data;
    }

    private static PackingListData.Caja caja(int numero, String pedido, String referencia, String color,
                                              int cantidad, String tamano, Double neto, Double bruto) {
        PackingListData.Caja caja = new PackingListData.Caja();
        caja.setNumeroCaja(numero);
        caja.setNumeroPedido(pedido);
        caja.setReferencia(referencia);
        caja.setCodigoColor(color);
        caja.setCantidad(cantidad);
        caja.setTamanoCaja(tamano);
        caja.setPesoNetoKg(neto);
        caja.setPesoBrutoKg(bruto);
        return caja;
    }

    private static Cell celda(Sheet hoja, int idxFila, int idxCol) {
        return hoja.getRow(idxFila).getCell(idxCol);
    }

    private static String texto(Sheet hoja, int idxFila, int idxCol) {
        return celda(hoja, idxFila, idxCol).getStringCellValue();
    }

    private static LocalDate fecha(Sheet hoja, int idxFila, int idxCol) {
        return celda(hoja, idxFila, idxCol).getLocalDateTimeCellValue().toLocalDate();
    }
}
