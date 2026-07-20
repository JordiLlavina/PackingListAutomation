package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.AmiExcelBuilder;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TaraProperties;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

import static com.puntotres.packinglist.testutil.TestDatos.caja;
import static com.puntotres.packinglist.testutil.TestDatos.cajaConTalla;
import static com.puntotres.packinglist.testutil.TestDatos.palet;

/**
 * Generador AMI de bolsos: agrupa por referencia+color y delega en
 * {@link AmiExcelBuilder}. Estos tests reemplazan a los que antes vivían
 * en PackingListGenerationServiceTest (ahora un simple despachador).
 */
class AmiGeneradorTest {

    private final AmiGenerador generador = new AmiGenerador(new AmiExcelBuilder());
    private final ClienteConfig ami = new ClienteConfig();

    @Test
    void tipoEsAmi() {
        assertEquals(TipoPlantilla.AMI, generador.tipo());
    }

    @Test
    void generaUnExcelPorModeloYColor() throws Exception {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("France");
        destino.setCajas(List.of(
                caja(1, "OF-1", "BOLSO", "NAT03", 25, 11.5, 12.7),
                caja(2, "OF-1", "BOLSO", "NAT03", 25, 11.5, 12.7),
                caja(3, "OF-1", "BOLSO", "ROJO02", 40, 8.0, 9.2)));

        List<ExcelGenerado> excels = generador.generar(destino, List.of(), envio(), ami);

        // Misma referencia pero distinto color -> dos packing lists.
        assertEquals(2, excels.size());
        assertEquals("PKL_France_BOLSO_NAT03.xlsx", excels.get(0).getNombreFichero());
        assertEquals("PKL_France_BOLSO_ROJO02.xlsx", excels.get(1).getNombreFichero());
        assertEquals("France · BOLSO NAT03", excels.get(0).getDescripcion());

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
    void elNombreDeFicheroSaneaCaracteresInvalidosEnWindows() throws Exception {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("New York/Boston");
        destino.setCajas(List.of(caja(1, "OF-1", "BOLSO: TOTE", "NAT 03", 25, 11.5, 12.7)));

        List<ExcelGenerado> excels = generador.generar(destino, List.of(), envio(), ami);

        assertEquals("PKL_New_York_Boston_BOLSO_TOTE_NAT_03.xlsx", excels.get(0).getNombreFichero());
    }

    @Test
    void pesosPendientesGeneranExcelConCeldasVaciasYSeReportan() throws Exception {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("France");
        CajaData sinPeso = caja(2, "OF-1", "BOLSO", "NAT03", 25, null, null);
        destino.setCajas(List.of(caja(1, "OF-1", "BOLSO", "NAT03", 25, 11.5, 12.7), sinPeso));

        List<ExcelGenerado> excels = generador.generar(destino, List.of(), envio(), ami);

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

        List<ExcelGenerado> excels = generador.generar(paris, palets, envio(), ami);
        assertEquals(2, excels.size()); // BOLSO TOTE/NAT03 (3 cajas, 2 pedidos) y CINTURON/NEG01

        for (ExcelGenerado excel : excels) {
            assertFalse(excel.tienePesosPendientes(),
                    "Pendiente inesperado: " + excel.getCajasPendientes());
            Path destinoFichero = Path.of("target", excel.getNombreFichero());
            Files.createDirectories(destinoFichero.getParent());
            Files.write(destinoFichero, excel.getContenido());
        }
    }

    @Test
    void referenciaUblAgregaLasTallasEnUnaFilaConElPesoDeLaCajaFisica() throws Exception {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("PARIS");
        // Caja física 14 con tres tallas: el peso es de la caja entera y lo
        // lleva la primera línea (talla 85); las demás van a null (una caja
        // mixta se pesa una sola vez, no se suman las tallas).
        destino.setCajas(List.of(
                cajaConTalla(14, "07672", "UBL029.AL0216", "001", "85", 3, 8.0, 10.0),
                cajaConTalla(14, "07672", "UBL029.AL0216", "001", "95", 31, null, null),
                cajaConTalla(14, "07672", "UBL029.AL0216", "001", "105", 3, null, null)));

        List<ExcelGenerado> excels = generador.generar(destino, List.of(), envio(), ami);

        assertEquals(1, excels.size());
        // La caja tiene su peso (en la líder): no debe quedar pendiente aunque
        // las otras tallas estén a null.
        assertFalse(excels.get(0).tienePesosPendientes(),
                "Pendiente inesperado: " + excels.get(0).getCajasPendientes());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheet("STANDARD PKL E25");
            Row fila = hoja.getRow(18);
            assertEquals("85-95-105", fila.getCell(5).getStringCellValue());  // F: SIZE GRID
            assertEquals(3, (int) fila.getCell(9).getNumericCellValue());     // J: talla 85
            assertEquals(31, (int) fila.getCell(11).getNumericCellValue());   // L: talla 95
            assertEquals(3, (int) fila.getCell(13).getNumericCellValue());    // N: talla 105
            assertEquals(8.0, fila.getCell(19).getNumericCellValue());        // T: NET de la caja
            assertEquals(10.0, fila.getCell(20).getNumericCellValue());       // U: GROSS de la caja
        }
    }

    @Test
    void referenciaUslUsaLaPlantillaDeBolsos() throws Exception {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("PARIS");
        destino.setCajas(List.of(caja(1, "OF-1", "USL728.AL217", "NOIR", 50, 11.0, 12.0)));

        List<ExcelGenerado> excels = generador.generar(destino, List.of(), envio(), ami);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            assertTrue(wb.getSheet("STANDARD PKL H26") != null);
        }
    }

    @Test
    void cajaDeCinturonSinTallaLanzaExcepcionClara() {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("PARIS");
        destino.setCajas(List.of(caja(1, "OF-1", "UBL029.AL0216", "001", 10, 8.0, 10.0))); // sin talla

        assertThrows(IllegalArgumentException.class,
                () -> generador.generar(destino, List.of(), envio(), ami));
    }

    private static DatosEnvio envio() {
        DatosEnvio envio = new DatosEnvio();
        envio.setTemporada("H26");
        envio.setNumeroFactura("FA-26-1189");
        envio.setFechaFactura("10/07/2026");
        envio.setFechaEnvio("24/07/2026");
        return envio;
    }
}
