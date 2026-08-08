package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

import static com.puntotres.packinglist.testutil.TestDatos.caja;
import static com.puntotres.packinglist.testutil.TestDatos.cajaConTalla;
import static com.puntotres.packinglist.testutil.TestDatos.palet;

/**
 * Builder de la plantilla estándar Puntotres: un excel por destino, cajas
 * agrupadas en bloques "PALET n" (con su tara y medidas) y el bloque
 * SHIPMENT DETAILS de la cabecera.
 */
class GenericoExcelBuilderTest {

    private final GenericoExcelBuilder builder = new GenericoExcelBuilder();

    private static ClienteConfig ackermann() {
        ClienteConfig cliente = new ClienteConfig();
        cliente.setNombre("Ackermann");
        cliente.setPlantilla(TipoPlantilla.GENERIC);
        cliente.setNombreLegal("ACKERMANN HOHMANN UND SEDLACEK OHG");
        cliente.setDireccionEntrega("Goseburgstraße 27, 21339 Lüneburg, Germany");
        return cliente;
    }

    private static DatosEnvio envio() {
        DatosEnvio envio = new DatosEnvio();
        envio.setTemporada("SPRING 25");
        envio.setNumeroFactura("250121");
        envio.setFechaFactura("05/05/2025");
        envio.setFechaEnvio("06/05/2025");
        return envio;
    }

    private static CajaData cajaConPalet(int numero, int paletNum, int cantidad, Double bruto) {
        CajaData resultado = caja(numero, "250121", "A204", "BROWN", cantidad, bruto, bruto);
        resultado.setNumeroPalet(paletNum);
        resultado.setModelo("BRIEFCASE POST Q PO3");
        return resultado;
    }

    private static DestinoData destino() {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("LUNEBURG");
        destino.setCajas(List.of(
                cajaConPalet(1, 1, 5, 10.08),
                cajaConPalet(2, 1, 5, 10.08),
                cajaConPalet(3, 2, 4, 9.0)));
        return destino;
    }

    private static List<PaletData> palets() {
        PaletData palet1 = palet("LUNEBURG", 1, 1, 2);
        palet1.setMedidas("80x120x170");
        palet1.setTara(12.5);
        PaletData palet2 = palet("LUNEBURG", 2, 3, 3); // sin tara -> 10 por defecto
        return List.of(palet1, palet2);
    }

    @Test
    void generaUnExcelPorDestinoConCabeceraDelCatalogo() throws Exception {
        List<ExcelGenerado> excels = builder.generar(destino(), palets(), envio(), ackermann());

        assertEquals(1, excels.size());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);
            assertEquals("INV 250121", hoja.getSheetName());
            assertEquals("ACKERMANN HOHMANN UND SEDLACEK OHG",
                    hoja.getRow(9).getCell(2).getStringCellValue());              // C10
            assertTrue(hoja.getRow(11).getCell(2).getStringCellValue().contains("Lüneburg")); // C12
            assertEquals("250121", hoja.getRow(15).getCell(2).getStringCellValue()); // C16
        }
    }

    @Test
    void agrupaPorPaletConTaraMedidasYFilasDeCaja() throws Exception {
        List<ExcelGenerado> excels = builder.generar(destino(), palets(), envio(), ackermann());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);

            // PALET 1 (idx 20): tara 12.5 del JSON y medidas en la columna SIZE.
            assertEquals("PALET 1", hoja.getRow(20).getCell(1).getStringCellValue());
            assertEquals(12.5, hoja.getRow(20).getCell(7).getNumericCellValue());
            assertEquals("80X120X170 cm", hoja.getRow(20).getCell(8).getStringCellValue());

            // Fila de caja (idx 21): referencia, modelo, temporada, unidades, peso.
            assertEquals(1, (int) hoja.getRow(21).getCell(1).getNumericCellValue());
            assertEquals("A204", hoja.getRow(21).getCell(2).getStringCellValue());
            assertEquals("BRIEFCASE POST Q PO3", hoja.getRow(21).getCell(3).getStringCellValue());
            assertEquals("SPRING 25", hoja.getRow(21).getCell(4).getStringCellValue());
            assertEquals(5, (int) hoja.getRow(21).getCell(6).getNumericCellValue());
            assertEquals(10.08, hoja.getRow(21).getCell(7).getNumericCellValue());

            // PALET 2 (idx 23): tara 10 por defecto.
            assertEquals("PALET 2", hoja.getRow(23).getCell(1).getStringCellValue());
            assertEquals(10.0, hoja.getRow(23).getCell(7).getNumericCellValue());

            // TOTAL (idx 25): rango completo, las filas PALET aportan su tara.
            assertEquals("SUM(G21:G25)", hoja.getRow(25).getCell(6).getCellFormula());
            assertEquals("SUM(H21:H25)", hoja.getRow(25).getCell(7).getCellFormula());
        }
    }

    @Test
    void rellenaElBloqueShipmentDetails() throws Exception {
        List<ExcelGenerado> excels = builder.generar(destino(), palets(), envio(), ackermann());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);

            // Cartones 29.16 kg; + taras (12.5 + 10) = 51.66 kg.
            assertEquals("29.16 Kg", hoja.getRow(11).getCell(8).getStringCellValue());  // TOTAL WEIGHT
            assertEquals("51.66 Kg", hoja.getRow(12).getCell(8).getStringCellValue());  // TOTAL GROSS WEIGHT
            assertEquals(3, (int) hoja.getRow(15).getCell(8).getNumericCellValue());    // TOTAL CARTONS
            assertEquals("3 (60x40x40cm)", hoja.getRow(16).getCell(8).getStringCellValue()); // DIMENTIONS
        }
    }

    /**
     * La medida de caja puede faltar (la extracción por hojas no siempre la
     * encuentra escrita): es un dato que se completa en la revisión y no
     * puede tumbar la generación. La caja cuenta como cartón, no suma
     * volumen y el desglose lo marca con "?" en vez de callarlo.
     */
    @Test
    void unaCajaSinMedidaSaleEnElExcelSinVolumenEnVezDeReventar() throws Exception {
        DestinoData destino = destino();
        destino.getCajas().get(2).setTamanoCaja(null); // la caja 3

        List<ExcelGenerado> excels = builder.generar(destino, palets(), envio(), ackermann());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);
            // Solo las dos cajas medidas suman: 2 * 0.096 = 0.192 m3.
            assertEquals("0.19 m3", hoja.getRow(13).getCell(8).getStringCellValue());
            assertEquals(3, (int) hoja.getRow(15).getCell(8).getNumericCellValue());
            assertEquals("3 (2*60x40x40cm+1*?cm)",
                    hoja.getRow(16).getCell(8).getStringCellValue());
        }
    }

    /** Caja 3 partida en dos tallas: el peso de la caja vive en su líder. */
    private static DestinoData destinoConCajaMixta(Double brutoLider) {
        DestinoData destino = destino();
        CajaData lider = cajaConTalla(3, "250121", "A204", "BROWN", "85", 2, null, brutoLider);
        lider.setNumeroPalet(2);
        CajaData segunda = cajaConTalla(3, "250121", "A204", "BROWN", "90", 2, null, null);
        segunda.setNumeroPalet(2);
        destino.setCajas(List.of(destino.getCajas().get(0), destino.getCajas().get(1), lider, segunda));
        return destino;
    }

    @Test
    void lasLineasExtraDeUnaCajaMixtaNoCuentanComoPendientes() throws Exception {
        List<ExcelGenerado> excels = builder.generar(destinoConCajaMixta(9.0), palets(), envio(), ackermann());

        assertTrue(excels.get(0).getCajasPendientes().isEmpty(),
                "el peso de la caja 3 está en su línea líder: no hay nada pendiente");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);
            // La caja 3 ocupa dos filas (idx 24 y 25) pero pesa una sola vez.
            assertEquals(9.0, hoja.getRow(24).getCell(7).getNumericCellValue());
            assertEquals(CellType.BLANK, hoja.getRow(25).getCell(7).getCellType());
            // Y cuenta como UN cartón, no como dos.
            assertEquals(3, (int) hoja.getRow(15).getCell(8).getNumericCellValue());
        }
    }

    @Test
    void unaCajaMixtaSinPesoCuentaUnaSolaVez() throws Exception {
        List<ExcelGenerado> excels = builder.generar(destinoConCajaMixta(null), palets(), envio(), ackermann());

        List<CajaData> pendientes = excels.get(0).getCajasPendientes();
        assertEquals(1, pendientes.size(), "una entrada por caja física, no por línea");
        assertEquals(3, pendientes.get(0).getNumeroCaja());
    }
}
