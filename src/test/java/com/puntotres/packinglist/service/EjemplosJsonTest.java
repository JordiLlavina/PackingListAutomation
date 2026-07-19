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

        List<ExcelGenerado> excels = new ArrayList<>();
        for (EnvioImportado.DestinoImportado destino : importado.getDestinos()) {
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

        // Un excel por referencia+color: carteras USL728 y cinturones UBL029.
        assertEquals(2, excels.size());
        assertEquals("PKL_PARIS_USL728.AL217_NOIR.xlsx", excels.get(0).getNombreFichero());
        assertEquals("PKL_PARIS_UBL029.AL0216_001.xlsx", excels.get(1).getNombreFichero());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(1).getContenido()))) {
            Sheet hoja = wb.getSheet("STANDARD PKL E25");
            // Caja 4 (idx 18): talla única 75. Caja 5 (idx 19): tres tallas.
            assertEquals("75", hoja.getRow(18).getCell(5).getStringCellValue());
            assertEquals("85-95-105", hoja.getRow(19).getCell(5).getStringCellValue());
            assertEquals(31, (int) hoja.getRow(19).getCell(11).getNumericCellValue()); // L: talla 95
        }
    }

    @Test
    void elEnvioApcGeneraUnExcelConCajaMultifila() throws Exception {
        List<ExcelGenerado> excels = importarYGenerar("envio-apc.json");

        assertEquals(1, excels.size());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Sheet hoja = wb.getSheetAt(0);
            assertEquals("APC INV FA-1 IVRY", hoja.getSheetName());
            assertEquals("PALET 1", hoja.getRow(16).getCell(1).getStringCellValue());
            // Tara 8.04 del JSON en el palet 1; 10 por defecto en el 2.
            assertTrue(hoja.getRow(16).getCell(14).getCellFormula().endsWith("+8.04"));
            assertEquals("PALET 2", hoja.getRow(19).getCell(1).getStringCellValue());
            assertTrue(hoja.getRow(19).getCell(14).getCellFormula().endsWith("+10"));
            // La caja 3 tiene tres líneas de cinturones (tallas 85/90 y canal AUSTRALIA).
            assertEquals("85", hoja.getRow(20).getCell(13).getStringCellValue());
            assertEquals("90", hoja.getRow(21).getCell(13).getStringCellValue());
            assertEquals("AUSTRALIA", hoja.getRow(22).getCell(10).getStringCellValue());
        }
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
