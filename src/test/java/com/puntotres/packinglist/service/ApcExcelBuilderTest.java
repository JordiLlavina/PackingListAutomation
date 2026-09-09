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

    /**
     * 2 cajas de bolsos en el palet 1 y una caja de cinturones de 3 líneas
     * en el 2. El peso de la caja 3 (15.26 kg del bulto entero) va una sola
     * vez, en su primera línea: es la convención de entrada del JSON.
     */
    private static DestinoData destinoIvry() {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("IVRY");
        destino.setCajas(List.of(
                linea(1, 1, "LE NEIGE", "PXCBC-F67008", "WHOLESALE", null, 11, 8.18),
                linea(2, 1, "LE NEIGE", "PXCBC-F67008", "WHOLESALE", null, 11, 8.18),
                linea(3, 2, "CEINTURE PARIS", "PXBHZ-H65077", "WHOLESALE", "85", 7, 15.26),
                linea(3, 2, "CEINTURE PARIS", "PXBHZ-H65077", "WHOLESALE", "90", 8, null),
                linea(3, 2, "CEINTURE PARIS", "PXBHZ-H65077", "AUSTRALIA", "85", 5, null)));
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
            // La fecha va como texto dd.MM.yyyy (como el original del cliente):
            // la celda F12 tiene formato General y un LocalDate se vería "46213".
            assertEquals("10.07.2026", hoja.getRow(11).getCell(5).getStringCellValue()); // F12
            assertEquals("FA-1", hoja.getRow(13).getCell(15).getStringCellValue());   // P14
        }
    }

    /**
     * Las tres columnas que rellena esta feature, de punta a punta: el
     * Livraison code (F) y el pedido completo (G) los trae ya la caja desde el
     * import, y la columna DESTINATION (K) lleva la destinación HIJA, que es
     * lo único que sobrevive de ella cuando el excel es del padre.
     */
    @Test
    void escribeLivraisonCodePedidoYLaHijaEnLaColumnaDestination() throws Exception {
        CajaData linea = linea(1, 1, "LE NEIGE", "PXCBC-F67008", "AUSTRALIA", null, 11, 8.18);
        linea.setNumeroPedido("4100128721");
        linea.setLivraisonCode("PUN20260428WH1");
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("IVRY");
        destino.setCajas(List.of(linea));

        List<ExcelGenerado> excels =
                builder.generar(destino, List.of(palet("IVRY", 1, 1, 1)), envio(), apc());

        try (XSSFWorkbook wb = new XSSFWorkbook(
                new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Row fila = wb.getSheetAt(0).getRow(17); // primera fila de caja
            assertEquals("PUN20260428WH1", fila.getCell(5).getStringCellValue());   // F
            assertEquals("4100128721", fila.getCell(6).getStringCellValue());       // G
            assertEquals("AUSTRALIA", fila.getCell(10).getStringCellValue());       // K
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

    /**
     * Pie de 6 líneas con rótulo en la D y valor en la E, tal como lo pide el
     * ejemplo del cliente. Los pesos y volúmenes son NÚMEROS, no texto con
     * las unidades pegadas.
     */
    @Test
    void escribeElResumenDeSeisLineas() throws Exception {
        List<ExcelGenerado> excels = builder.generar(destinoIvry(), palets(), envio(), apc());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);

            // 2 palets, uno con medidas en el JSON y otro sin ellas.
            assertEquals("PALLETS", hoja.getRow(26).getCell(3).getStringCellValue());
            assertEquals("2 (1*80x120x130+1*?)", hoja.getRow(26).getCell(4).getStringCellValue());
            assertEquals("CARTONS", hoja.getRow(27).getCell(3).getStringCellValue());
            assertEquals("3 (60x40x40cm)", hoja.getRow(27).getCell(4).getStringCellValue());
            // Cartones: 31.62 kg; + taras (8.04 + 10) = 49.66 kg de bruto.
            assertEquals("CARTON WEIGHT", hoja.getRow(28).getCell(3).getStringCellValue());
            assertEquals(31.62, hoja.getRow(28).getCell(4).getNumericCellValue());
            // 3 cajas físicas de 60x40x40: 0.288 m3; + 2 palets * 0.168 = 0.624.
            assertEquals("CARTONS VOLUME", hoja.getRow(29).getCell(3).getStringCellValue());
            assertEquals(0.288, hoja.getRow(29).getCell(4).getNumericCellValue());
            assertEquals("GROSS WEIGHT", hoja.getRow(30).getCell(3).getStringCellValue());
            assertEquals(49.66, hoja.getRow(30).getCell(4).getNumericCellValue());
            assertEquals("GROSS VOLUME", hoja.getRow(31).getCell(3).getStringCellValue());
            assertEquals(0.624, hoja.getRow(31).getCell(4).getNumericCellValue());
        }
    }

    /**
     * Un envío que va suelto (cajas con {@link CajaData#SIN_PALET}) no cuenta
     * ningún palet: ni suma su tara al bruto ni su volumen. GROSS y CARTON
     * valen lo mismo, que es la verdad de lo que se entrega.
     */
    @Test
    void unEnvioSueltoNoCuentaNingunPaletEnElPie() throws Exception {
        DestinoData destino = destinoIvry();
        destino.getCajas().forEach(caja -> caja.setNumeroPalet(CajaData.SIN_PALET));

        List<ExcelGenerado> excels = builder.generar(destino, List.of(), envio(), apc());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);
            // El rótulo del bloque va en inglés, como el resto de la
            // plantilla: este excel lo lee el cliente.
            assertEquals("NO PALLET", hoja.getRow(16).getCell(1).getStringCellValue());
            // Un bloque menos que con dos palets: el pie sube una fila.
            assertEquals("PALLETS", hoja.getRow(25).getCell(3).getStringCellValue());
            assertEquals("0", hoja.getRow(25).getCell(4).getStringCellValue());
            assertEquals(31.62, hoja.getRow(27).getCell(4).getNumericCellValue()); // CARTON WEIGHT
            assertEquals(31.62, hoja.getRow(29).getCell(4).getNumericCellValue()); // GROSS WEIGHT
            assertEquals(hoja.getRow(28).getCell(4).getNumericCellValue(),         // CARTONS VOLUME
                    hoja.getRow(30).getCell(4).getNumericCellValue());             // GROSS VOLUME
        }
    }

    /**
     * La medida de caja se escribe una sola vez para un grupo entero y a
     * veces de lado en el margen: la extracción por hojas no siempre la
     * encuentra. Que falte no puede tumbar la generación —es un dato que se
     * completa en la revisión— así que el excel sale igual: la caja se
     * cuenta como cartón, no suma volumen, y el desglose lo dice con "?" en
     * vez de callarlo o de inventar una medida.
     */
    @Test
    void unaCajaSinMedidaSaleEnElExcelSinVolumenEnVezDeReventar() throws Exception {
        DestinoData destino = destinoIvry();
        destino.getCajas().get(1).setTamanoCaja(null); // la caja 2

        List<ExcelGenerado> excels = builder.generar(destino, palets(), envio(), apc());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);
            assertEquals("3 (2*60x40x40cm+1*?cm)",
                    hoja.getRow(27).getCell(4).getStringCellValue());
            // Solo las dos cajas medidas suman: 2 * 0.096 = 0.192 m3.
            assertEquals(0.192, hoja.getRow(29).getCell(4).getNumericCellValue());
        }
    }

    /**
     * El peso es de la caja física y viene UNA sola vez, en su primera
     * línea: las demás líneas de una caja mixta no aportan peso ni cuentan
     * como pendientes.
     */
    @Test
    void unaCajaMixtaConSuPesoEnLaPrimeraLineaNoQuedaPendiente() throws Exception {
        List<ExcelGenerado> excels = builder.generar(destinoIvry(), palets(), envio(), apc());

        assertTrue(excels.get(0).getCajasPendientes().isEmpty(),
                "la caja 3 lleva su peso en la primera línea: no está pendiente");
    }

    @Test
    void unaCajaSinPesoEnSuLiderCuentaUnaSolaVez() throws Exception {
        DestinoData destino = destinoIvry();
        destino.getCajas().get(2).setPesoBrutoKg(null); // líder de la caja 3
        destino.getCajas().get(2).setPesoNetoKg(null);

        List<ExcelGenerado> excels = builder.generar(destino, palets(), envio(), apc());

        List<CajaData> pendientes = excels.get(0).getCajasPendientes();
        assertEquals(1, pendientes.size(), "una entrada por caja física, no por línea");
        assertEquals(3, pendientes.get(0).getNumeroCaja());
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
