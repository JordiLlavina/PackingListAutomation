package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.EnvioInput;

/**
 * Los JSON de src/test/resources/ejemplos son la referencia documentada
 * (docs/Packing Lists/campos-json-por-cliente.md) de qué campos espera
 * cada cliente: este test los importa y genera de verdad con el catálogo
 * real de application.yml para garantizar que siguen siendo válidos.
 */
@SpringBootTest
class EjemplosJsonTest {

    @Autowired
    private EnvioImportService importador;
    @Autowired
    private ResolutorDestinosPadre resolutorDestinos;
    @Autowired
    private PaletAssignmentService asignadorPalets;
    @Autowired
    private PackingListGenerationService generador;
    @Autowired
    private ClientesProperties clientes;

    private List<ExcelGenerado> importarYGenerar(String fichero) throws Exception {
        EnvioInput envio;
        try (InputStream json = getClass().getResourceAsStream("/ejemplos/" + fichero)) {
            envio = new ObjectMapper().readValue(json, EnvioInput.class);
        }
        ClienteConfig cliente = clientes.clientePara(envio.getCliente()).orElseThrow();

        DatosEnvio cabecera = new DatosEnvio();
        cabecera.setTemporada("H26");
        cabecera.setNumeroFactura("FA-1");
        cabecera.setFechaFactura("10/07/2026");
        cabecera.setFechaEnvio("24/07/2026");

        EnvioImportado importado = importador.importar(envio);
        assertTrue(importado.getAvisos().isEmpty(),
                "El ejemplo no debe generar avisos: " + importado.getAvisos());

        // Como en el flujo real: resolver los destinos padre ANTES de asignar
        // palets (fusiona las hijas de APC y estampa el Livraison code; los
        // clientes sin catálogo de destinos pasan intactos).
        ResultadoDestinos resueltos = resolutorDestinos.resolver(
                importado.getDestinos(), cliente, cabecera.getFechaEnvio());
        assertTrue(resueltos.getAvisos().isEmpty(),
                "El ejemplo no debe generar avisos al resolver destinos: " + resueltos.getAvisos());

        List<ExcelGenerado> excels = new ArrayList<>();
        for (EnvioImportado.DestinoImportado destino : resueltos.getDestinos()) {
            ResultadoAsignacion asignacion =
                    asignadorPalets.asignar(destino.getDestino(), destino.getPalets());
            assertTrue(asignacion.todoAsignado());
            excels.addAll(generador.generar(destino.getDestino(), destino.getPalets(),
                    cabecera, cliente));
        }
        return excels;
    }

    @Test
    void elEnvioAmiGeneraBolsosYCinturones() throws Exception {
        List<ExcelGenerado> excels = importarYGenerar("envio-ami-bags-y-belts.json");

        // Tres destinaciones (China/Japan/France) y un excel por referencia+color:
        // CHINA: ULL729 en dos colores (caja 1 mixta), USL737, UBL029.AL0104, UBL214 (5)
        // JAPAN: ULL163, UBL214 (2)
        // FRANCE: ULL163, ULL737.AL0206, ULL754.AL0206, UBL029.AL0216 (4)
        assertEquals(11, excels.size());

        // La caja 1 de China mezcla dos artículos (ULL729 en 001 y 718), así
        // que ese bulto aparece en DOS packing lists. El peso es del cartón
        // entero y sale en los dos: el del 718 describe la misma caja, y sin
        // peso no se puede expedir. En el JSON solo lo trae la línea líder.
        try (XSSFWorkbook wb = abrir(excels, "2026.07.24_PUN_07706_ULL729.AL0103.001_H26_CHINA.xlsx")) {
            Sheet hoja = wb.getSheet("STANDARD PKL H26");
            assertEquals(12, (int) hoja.getRow(19).getCell(6).getNumericCellValue()); // talla única U
            assertEquals(6.3, hoja.getRow(19).getCell(21).getNumericCellValue());
        }
        try (XSSFWorkbook wb = abrir(excels, "2026.07.24_PUN_07706_ULL729.AL0103.718_H26_CHINA.xlsx")) {
            Sheet hoja = wb.getSheet("STANDARD PKL H26");
            assertEquals(10, (int) hoja.getRow(19).getCell(6).getNumericCellValue());
            assertEquals(6.3, hoja.getRow(19).getCell(21).getNumericCellValue(),
                    "El bulto compartido pesa lo mismo en los dos packing lists");
        }

        // El cinturón de France (UBL029.AL0216) reúne las cajas 8 y 9: la 8 es
        // talla única (75) y la 9 mezcla tres tallas (85-95-105).
        ExcelGenerado belt = abrirGenerado(excels,
                "2026.07.24_PUN_7672_UBL029.AL0216.001_H26_FR.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(belt.getContenido()))) {
            Sheet hoja = wb.getSheet("STANDARD PKL E25");
            // Las filas de cinturón arrancan en idx 18 y van por posición: 1ª caja
            // (talla única 75) y 2ª caja (tres tallas).
            assertEquals("75", hoja.getRow(18).getCell(5).getStringCellValue());
            assertEquals("85-95-105", hoja.getRow(19).getCell(5).getStringCellValue());
            assertEquals(31, (int) hoja.getRow(19).getCell(11).getNumericCellValue()); // L: talla 95

            // El pesoBruto del JSON llega a la celda de peso bruto (col 20) de cada
            // caja física; el neto (col 19) queda vacío porque este test no corre la
            // inferencia (sin ella no hay tara aplicada).
            assertEquals(10.2, hoja.getRow(18).getCell(20).getNumericCellValue());
            assertEquals(20.8, hoja.getRow(19).getCell(20).getNumericCellValue());
        }
    }

    @Test
    void elEnvioApcFusionaLasHijasDeWholesaleYGeneraTresExcels() throws Exception {
        List<ExcelGenerado> excels = importarYGenerar("envio-apc.json");

        // El JSON trae Korea, Australia, Wholesale y Retail; Australia y
        // Wholesale son hijas del padre WHOLESALE, así que salen tres excels.
        assertEquals(3, excels.size());

        try (XSSFWorkbook wb = abrir(excels, "PKL_APC_KOREA_FA-1.xlsx")) {
            Sheet hoja = wb.getSheetAt(0);
            assertEquals("APC INV FA-1 KOREA", hoja.getSheetName());
            assertEquals("PALET 1", hoja.getRow(16).getCell(1).getStringCellValue());
            // Tara 8.04 del JSON en el palet 1.
            assertTrue(hoja.getRow(16).getCell(14).getCellFormula().endsWith("+8.04"));
            // La caja 1 es multifila: cinturones con tallas 85 y 80.
            assertEquals("85", hoja.getRow(17).getCell(13).getStringCellValue());
            assertEquals("80", hoja.getRow(18).getCell(13).getStringCellValue());
            // Sin excel de pedido el PO se queda en los tres dígitos del JSON,
            // y el Livraison code lo genera el resolutor (el JSON ya no lo trae).
            assertEquals("689", hoja.getRow(17).getCell(6).getStringCellValue());
            assertEquals("PUN20260724KRT1", hoja.getRow(17).getCell(5).getStringCellValue());
        }

        try (XSSFWorkbook wb = abrir(excels, "PKL_APC_WHOLESALE_FA-1.xlsx")) {
            Sheet hoja = wb.getSheetAt(0);
            // Palet 1 con las cajas de Australia (1-2) y palet 2 con las de
            // Wholesale (3-6), sin renumerar nada; 10 kg de tara por defecto.
            assertEquals("PALET 1", hoja.getRow(16).getCell(1).getStringCellValue());
            assertEquals("PALET 2", hoja.getRow(21).getCell(1).getStringCellValue());
            assertTrue(hoja.getRow(21).getCell(14).getCellFormula().endsWith("+10"));
            // La línea de la talla 90 llega sin canal y hereda el de su hija.
            assertEquals("AUSTRALIA", hoja.getRow(17).getCell(10).getStringCellValue());
            assertEquals("AUSTRALIA", hoja.getRow(18).getCell(10).getStringCellValue());
            assertEquals("WHOLESALE", hoja.getRow(22).getCell(10).getStringCellValue());
        }

        try (XSSFWorkbook wb = abrir(excels, "PKL_APC_RETAIL_FA-1.xlsx")) {
            assertEquals("APC INV FA-1 RETAIL", wb.getSheetAt(0).getSheetName());
        }
    }

    private XSSFWorkbook abrir(List<ExcelGenerado> excels, String nombreFichero) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(
                abrirGenerado(excels, nombreFichero).getContenido()));
    }

    private ExcelGenerado abrirGenerado(List<ExcelGenerado> excels, String nombreFichero) {
        return excels.stream()
                .filter(e -> e.getNombreFichero().equals(nombreFichero))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "No se generó " + nombreFichero + "; hay: " + excels.stream()
                                .map(ExcelGenerado::getNombreFichero).toList()));
    }

    @Test
    void elEnvioGenericoGeneraUnExcelConElCatalogoReal() throws Exception {
        List<ExcelGenerado> excels = importarYGenerar("envio-generico.json");

        assertEquals(1, excels.size());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);
            assertEquals("ACKERMANN HOHMANN UND SEDLACEK OHG",
                    hoja.getRow(9).getCell(2).getStringCellValue());
            assertEquals("PALET 1", hoja.getRow(20).getCell(1).getStringCellValue());
            assertEquals(16, (int) hoja.getRow(15).getCell(8).getNumericCellValue()); // TOTAL CARTONS
        }
    }
}
