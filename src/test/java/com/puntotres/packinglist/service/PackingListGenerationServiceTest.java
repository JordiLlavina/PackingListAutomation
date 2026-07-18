package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.AmiExcelBuilder;
import com.puntotres.packinglist.config.TaraProperties;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

class PackingListGenerationServiceTest {

    private final PackingListGenerationService service =
            new PackingListGenerationService(new AmiExcelBuilder());

    @Test
    void generaUnExcelPorModeloYColor() throws Exception {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("France");
        destino.setCajas(List.of(
                caja(1, "OF-1", "BOLSO", "NAT03", 25, 11.5, 12.7),
                caja(2, "OF-1", "BOLSO", "NAT03", 25, 11.5, 12.7),
                caja(3, "OF-1", "BOLSO", "ROJO02", 40, 8.0, 9.2)));

        List<ExcelGenerado> excels = service.generarPorModeloYColor(destino, envio());

        // Misma referencia pero distinto color -> dos packing lists.
        assertEquals(2, excels.size());
        assertEquals("PKL_France_BOLSO_NAT03.xlsx", excels.get(0).getNombreFichero());
        assertEquals("PKL_France_BOLSO_ROJO02.xlsx", excels.get(1).getNombreFichero());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheet("STANDARD PKL H26");
            assertEquals("France", hoja.getRow(9).getCell(1).getStringCellValue());   // B10
            assertEquals("OF-1", hoja.getRow(19).getCell(1).getStringCellValue());    // B20 pedido por fila
            assertEquals("BOLSO", hoja.getRow(19).getCell(2).getStringCellValue());   // C20 referencia
            assertEquals("NAT03", hoja.getRow(19).getCell(3).getStringCellValue());   // D20 color
            assertEquals(1, (int) hoja.getRow(19).getCell(4).getNumericCellValue());  // E20 nº caja
            assertEquals(2, (int) hoja.getRow(20).getCell(4).getNumericCellValue());  // E21 nº caja
            assertEquals("SUM(G20:G21)", hoja.getRow(21).getCell(6).getCellFormula());
        }
    }

    @Test
    void pesosPendientesGeneranExcelConCeldasVaciasYSeReportan() throws Exception {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("France");
        CajaData sinPeso = caja(2, "OF-1", "BOLSO", "NAT03", 25, null, null);
        destino.setCajas(List.of(caja(1, "OF-1", "BOLSO", "NAT03", 25, 11.5, 12.7), sinPeso));

        List<ExcelGenerado> excels = service.generarPorModeloYColor(destino, envio());

        assertEquals(1, excels.size());
        ExcelGenerado excel = excels.get(0);
        assertTrue(excel.tienePesosPendientes());
        assertEquals(List.of(sinPeso), excel.getCajasPendientes());

        // El excel se genera igualmente, con las celdas de peso de la caja 2 vacías.
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel.getContenido()))) {
            Sheet hoja = wb.getSheet("STANDARD PKL H26");
            assertEquals(11.5, hoja.getRow(19).getCell(20).getNumericCellValue());     // U20 conocido
            assertEquals(CellType.BLANK, hoja.getRow(20).getCell(20).getCellType());   // U21 vacío
            assertEquals(CellType.BLANK, hoja.getRow(20).getCell(21).getCellType());   // V21 vacío
        }
    }

    /**
     * Demo end-to-end del flujo completo: asignación de palets + inferencia
     * de pesos + generación por modelo y color. Deja los excels en target/
     * para poder abrirlos y compararlos con la plantilla a mano.
     */
    @Test
    void flujoCompletoGeneraExcelsAbribles() throws Exception {
        DestinoData paris = new DestinoData();
        paris.setNombreDestino("France");
        paris.setCajas(List.of(
                caja(1, "OF-0457", "BOLSO TOTE", "NAT03", 25, null, 26.6),  // bruto conocido
                caja(2, "OF-0457", "BOLSO TOTE", "NAT03", 25, null, null),  // a inferir
                caja(3, "OF-0458", "BOLSO TOTE", "NAT03", 10, null, null),  // a inferir (otro pedido, mismo excel)
                caja(4, "OF-0458", "CINTURON", "NEG01", 40, 8.0, 9.2)));

        List<PaletData> palets = List.of(palet("France", 1, 1, 2), palet("France", 2, 3, 4));

        PaletAssignmentService asignacion = new PaletAssignmentService();
        TaraProperties taras = new TaraProperties();
        taras.setTaras(Map.of("60x40x40", 1.6, "60x40x30", 1.2));
        WeightInferenceService pesos = new WeightInferenceService(taras);

        ResultadoAsignacion resultadoPalets = asignacion.asignar(paris, palets);
        assertTrue(resultadoPalets.todoAsignado());
        assertEquals(2, paris.getCajas().get(2).getNumeroPalet());

        pesos.inferirPesosPorReferencia(paris.getCajas());
        // (26.6 - 1.6) / 25 = 1.0 kg/unidad -> caja 2: neto 25, bruto 26.6; caja 3: neto 10, bruto 11.6
        assertEquals(25.0, paris.getCajas().get(1).getPesoNetoKg());
        assertEquals(11.6, paris.getCajas().get(2).getPesoBrutoKg());

        List<ExcelGenerado> excels = service.generarPorModeloYColor(paris, envio());
        assertEquals(2, excels.size()); // BOLSO TOTE/NAT03 (3 cajas, 2 pedidos) y CINTURON/NEG01

        for (ExcelGenerado excel : excels) {
            assertFalse(excel.tienePesosPendientes(),
                    "Pendiente inesperado: " + excel.getCajasPendientes());
            Path destinoFichero = Path.of("target", excel.getNombreFichero());
            Files.createDirectories(destinoFichero.getParent());
            Files.write(destinoFichero, excel.getContenido());
        }
    }

    private static DatosEnvio envio() {
        DatosEnvio envio = new DatosEnvio();
        envio.setTemporada("H26");
        envio.setNumeroFactura("FA-26-1189");
        envio.setFechaFactura("10/07/2026");
        envio.setFechaEnvio("24/07/2026");
        return envio;
    }

    private static CajaData caja(int numero, String pedido, String referencia, String color,
                                 int cantidad, Double neto, Double bruto) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(numero);
        caja.setNumeroPedido(pedido);
        caja.setReferencia(referencia);
        caja.setCodigoColor(color);
        caja.setTamanoCaja("60x40x40");
        caja.setCantidad(cantidad);
        caja.setPesoNetoKg(neto);
        caja.setPesoBrutoKg(bruto);
        return caja;
    }

    private static PaletData palet(String destino, int numero, int inicio, int fin) {
        PaletData palet = new PaletData();
        palet.setDestino(destino);
        palet.setNumeroPalet(numero);
        palet.setCajaInicio(inicio);
        palet.setCajaFin(fin);
        return palet;
    }
}
