package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.DestinoClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

import static com.puntotres.packinglist.testutil.TestDatos.caja;
import static com.puntotres.packinglist.testutil.TestDatos.palet;

/**
 * Builder APC contra el layout del ejemplo real del cliente
 * (apc-bags-and-belts-complete-example.xlsx): un excel por destino, cajas
 * multifila (talla/canal), taras de palet del JSON y resumen de 5 líneas.
 */
class ApcExcelBuilderTest {

    private final ApcExcelBuilder builder = new ApcExcelBuilder();

    private static ClienteConfig apc() {
        ClienteConfig apc = new ClienteConfig();
        apc.setNombre("A.P.C.");
        apc.setPlantilla(TipoPlantilla.APC);
        DestinoClienteConfig ivry = new DestinoClienteConfig();
        ivry.setNombreCliente("A.P.C.");
        ivry.setDireccion("74 BIS AV MAURICE THOREZ 94200 IVRY SUR SEINE FRANCE");
        apc.setDestinos(java.util.Map.of("IVRY", ivry));
        return apc;
    }

    private static DatosEnvio envio() {
        DatosEnvio envio = new DatosEnvio();
        envio.setTemporada("E25");
        envio.setNumeroFactura("FA-1");
        envio.setFechaFactura("10/07/2026");
        envio.setFechaEnvio("24/07/2026");
        return envio;
    }

    private static CajaData linea(int numeroCaja, Integer palet, String modelo, String referencia,
                                  String canal, String talla, int cantidad, Double bruto) {
        CajaData linea = caja(numeroCaja, "4100126780", referencia, "LZZ-NOIR", cantidad, bruto, bruto);
        linea.setNumeroPalet(palet);
        linea.setModelo(modelo);
        linea.setLivraisonCode("PUN20260428WH1");
        linea.setCanal(canal);
        linea.setTalla(talla);
        return linea;
    }

    /** 2 cajas de bolsos en el palet 1 y una caja de cinturones de 3 líneas en el 2. */
    private static DestinoData destinoIvry() {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("IVRY");
        destino.setCajas(List.of(
                linea(1, 1, "LE NEIGE", "PXCBC-F67008", "WHOLESALE", null, 11, 8.18),
                linea(2, 1, "LE NEIGE", "PXCBC-F67008", "WHOLESALE", null, 11, 8.18),
                linea(3, 2, "CEINTURE PARIS", "PXBHZ-H65077", "WHOLESALE", "85", 7, 5.0),
                linea(3, 2, "CEINTURE PARIS", "PXBHZ-H65077", "WHOLESALE", "90", 8, 6.0),
                linea(3, 2, "CEINTURE PARIS", "PXBHZ-H65077", "AUSTRALIA", "85", 5, 4.26)));
        return destino;
    }

    private static List<PaletData> palets() {
        PaletData palet1 = palet("IVRY", 1, 1, 2);
        palet1.setMedidas("80x120x130");
        palet1.setTara(8.04);
        PaletData palet2 = palet("IVRY", 2, 3, 3); // sin tara -> 10 por defecto
        return List.of(palet1, palet2);
    }

    @Test
    void generaUnExcelPorDestinoConCabeceraYNombreDeHoja() throws Exception {
        List<ExcelGenerado> excels = builder.generar(destinoIvry(), palets(), envio(), apc());

        assertEquals(1, excels.size());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);
            assertEquals("APC INV FA-1 IVRY", hoja.getSheetName());
            assertEquals("A.P.C.", hoja.getRow(7).getCell(5).getStringCellValue());   // F8
            assertTrue(hoja.getRow(9).getCell(5).getStringCellValue().contains("IVRY SUR SEINE")); // F10
            assertEquals("FA-1", hoja.getRow(13).getCell(15).getStringCellValue());   // P14
        }
    }

    @Test
    void agrupaPorPaletConSuTaraYSubtotales() throws Exception {
        List<ExcelGenerado> excels = builder.generar(destinoIvry(), palets(), envio(), apc());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);

            // PALET 1 (idx 16): tara 8.04 del JSON.
            assertEquals("PALET 1", hoja.getRow(16).getCell(1).getStringCellValue());
            assertEquals("SUM(O18:O19)+8.04", hoja.getRow(16).getCell(14).getCellFormula());

            // PALET 2 (idx 19): tara 10 por defecto.
            assertEquals("PALET 2", hoja.getRow(19).getCell(1).getStringCellValue());
            assertEquals("SUM(O21:O23)+10", hoja.getRow(19).getCell(14).getCellFormula());

            // Totales: peso por bloques (sin las filas PALET) y unidades del rango.
            assertEquals("SUM(O18:O19)+SUM(O21:O23)", hoja.getRow(23).getCell(14).getCellFormula());
            assertEquals("SUM(P18:P23)", hoja.getRow(24).getCell(15).getCellFormula());
        }
    }

    @Test
    void unaCajaConVariasLineasSoloLlevaNumeroYPesoEnLaPrimera() throws Exception {
        List<ExcelGenerado> excels = builder.generar(destinoIvry(), palets(), envio(), apc());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);

            // Primera línea de la caja 3 (idx 20): número, peso de la caja
            // ENTERA (5.0+6.0+4.26), talla y canal de SU línea.
            Row primera = hoja.getRow(20);
            assertEquals(3, (int) primera.getCell(1).getNumericCellValue());
            assertEquals(15.26, primera.getCell(14).getNumericCellValue());
            assertEquals("85", primera.getCell(13).getStringCellValue());
            assertEquals("WHOLESALE", primera.getCell(10).getStringCellValue());
            assertEquals(7, (int) primera.getCell(15).getNumericCellValue());

            // Segunda línea (idx 21): sin número ni peso; talla 90.
            Row segunda = hoja.getRow(21);
            assertEquals(CellType.BLANK, segunda.getCell(1).getCellType());
            assertEquals(CellType.BLANK, segunda.getCell(14).getCellType());
            assertEquals("90", segunda.getCell(13).getStringCellValue());

            // Tercera línea (idx 22): canal AUSTRALIA.
            assertEquals("AUSTRALIA", hoja.getRow(22).getCell(10).getStringCellValue());

            // Las filas nuevas recrean las fusiones de la plantilla (D:E de MODÈLE).
            assertTrue(hoja.getMergedRegions().contains(CellRangeAddress.valueOf("D22:E22")));
        }
    }

    @Test
    void escribeElResumenDeCincoLineas() throws Exception {
        List<ExcelGenerado> excels = builder.generar(destinoIvry(), palets(), envio(), apc());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);

            // Cartones: 31.62 kg; + taras (8.04 + 10) = 49.66 kg.
            assertEquals("TOTAL WEIGHT", hoja.getRow(26).getCell(3).getStringCellValue());
            assertEquals("31,62 KG", hoja.getRow(26).getCell(4).getStringCellValue());
            assertTrue(hoja.getRow(27).getCell(3).getStringCellValue().contains("49,66 KG"));
            // 3 cajas físicas de 60x40x40: 0.288 m3; + 2 palets * 0.168 = 0.624.
            assertTrue(hoja.getRow(28).getCell(3).getStringCellValue().contains("0,288 M3"));
            assertEquals("3 CARTONS 60 x 40 x 40 cm", hoja.getRow(29).getCell(3).getStringCellValue());
            assertTrue(hoja.getRow(30).getCell(3).getStringCellValue().contains("0,624 M3"));
        }
    }

    @Test
    void unDestinoSinConfiguracionLanzaExcepcionClara() {
        DestinoData destino = destinoIvry();
        destino.setNombreDestino("TOKIO");

        Exception ex = assertThrows(IllegalStateException.class,
                () -> builder.generar(destino, palets(), envio(), apc()));
        assertTrue(ex.getMessage().contains("TOKIO"));
    }
}
