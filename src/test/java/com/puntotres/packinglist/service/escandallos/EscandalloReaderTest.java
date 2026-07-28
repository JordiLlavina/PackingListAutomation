package com.puntotres.packinglist.service.escandallos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * El escandallo real del ERP es un PDF convertido a excel: los valores no
 * caen en la columna de su propio encabezado (Article en B9 pero los códigos
 * en A10; Quantitat en X9 pero las cantidades en W10). Estos tests fijan que
 * la lectura aguante ese desajuste.
 */
class EscandalloReaderTest {

    private static final String NOIR = "/ejemplos/escandallos/ULL770 NOIR.xlsx";

    private final EscandalloReader reader = new EscandalloReader();

    private static byte[] recurso(String ruta) throws Exception {
        try (InputStream entrada = EscandalloReaderTest.class.getResourceAsStream(ruta)) {
            assertNotNull(entrada, "falta el recurso de test " + ruta);
            return entrada.readAllBytes();
        }
    }

    @Test
    void leeElBloqueDeCabeceraDelEscandalloReal() throws Exception {
        Escandallo escandallo = reader.leer(recurso(NOIR), "ULL770 NOIR.xlsx").escandallo();

        assertEquals("ULL770.AL245", escandallo.modelo());
        assertEquals("SAC CANDY RABAT LARGE UNISEX", escandallo.descripcion());
        assertEquals("000    0014 NOIR", escandallo.color());
    }

    @Test
    void leeLasVeinticincoLineasDeMaterialDelEscandalloReal() throws Exception {
        List<LineaEscandallo> lineas =
                reader.leer(recurso(NOIR), "ULL770 NOIR.xlsx").escandallo().lineas();

        assertEquals(25, lineas.size());
        assertEquals(new LineaEscandallo("P-FOUB01", "PIEL/FOULARD NEGRO", 1.2),
                lineas.get(0));
        assertEquals(new LineaEscandallo("R-TT00", "TINTA/ TRANSPARENTE AR6250P", 0.008),
                lineas.get(24));
    }

    @Test
    void noSeCuelaNiElTotalNiLaTablaDeFases() throws Exception {
        List<LineaEscandallo> lineas =
                reader.leer(recurso(NOIR), "ULL770 NOIR.xlsx").escandallo().lineas();

        assertTrue(lineas.stream().noneMatch(linea -> linea.descripcion().contains("CORTAR")),
                "las fases no son materiales");
        assertTrue(lineas.stream().allMatch(linea -> linea.article().startsWith("P-")
                        || linea.article().startsWith("F-") || linea.article().startsWith("R-")),
                "solo códigos de material: " + lineas);
    }

    /**
     * La columna de precios (AB) queda a 4 columnas de "Quantitat" (X) y a 1
     * de "Preu Ult." (AC): si solo se buscara la celda más cercana a los tres
     * encabezados que interesan, el precio acabaría de cantidad.
     */
    @Test
    void elPrecioNoSeConfundeConLaCantidad() throws Exception {
        List<LineaEscandallo> lineas =
                reader.leer(recurso(NOIR), "ULL770 NOIR.xlsx").escandallo().lineas();

        assertEquals(4.2, lineas.get(11).cantidad(), 0.0001);   // R-CD81, cinta de refuerzo
        assertEquals(5.0, lineas.get(19).cantidad(), 0.0001);   // R-PP00, 5 hojas de papel
    }

    @Test
    void guardaElNombreDelFicheroDeOrigen() throws Exception {
        Escandallo escandallo = reader.leer(recurso(NOIR), "ULL770 NOIR.xlsx").escandallo();

        assertEquals("ULL770 NOIR.xlsx", escandallo.origen());
    }

    @Test
    void unExcelSinLaFilaDeEncabezadosNoEsUnEscandallo() throws Exception {
        byte[] cualquierExcel = excelDePrueba(hoja -> {
            hoja.createRow(0).createCell(0).setCellValue("Hola");
        });

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> reader.leer(cualquierExcel, "otro.xlsx"));
        assertTrue(error.getMessage().contains("Article"), error.getMessage());
    }

    @Test
    void unaCantidadIlegibleDejaLaLineaSinCantidadYAvisa() throws Exception {
        byte[] excel = escandalloSintetico("no es un número");

        LecturaEscandallo lectura = reader.leer(excel, "raro.xlsx");

        assertNull(lectura.escandallo().lineas().get(0).cantidad());
        assertTrue(lectura.avisos().stream().anyMatch(aviso -> aviso.contains("P-FOUB01")),
                lectura.avisos().toString());
    }

    @Test
    void unaCantidadConComaDecimalSeLeeIgual() throws Exception {
        byte[] excel = escandalloSintetico("0,505");

        Escandallo escandallo = reader.leer(excel, "coma.xlsx").escandallo();

        assertEquals(0.505, escandallo.lineas().get(0).cantidad(), 0.0001);
    }

    @Test
    void siFaltaElModeloSeAvisaPeroSeLeeElResto() throws Exception {
        byte[] excel = excelDePrueba(hoja -> {
            Row cabecera = hoja.createRow(5);
            cabecera.createCell(1).setCellValue("Article");
            cabecera.createCell(10).setCellValue("Descripcio");
            cabecera.createCell(23).setCellValue("Quantitat");
            Row datos = hoja.createRow(6);
            datos.createCell(0).setCellValue("P-FOUB01");
            datos.createCell(10).setCellValue("PIEL/FOULARD NEGRO");
            datos.createCell(22).setCellValue(1.2);
        });

        LecturaEscandallo lectura = reader.leer(excel, "sin-modelo.xlsx");

        assertNull(lectura.escandallo().modelo());
        assertEquals(1, lectura.escandallo().lineas().size());
        assertTrue(lectura.avisos().stream().anyMatch(aviso -> aviso.contains("MODEL")),
                lectura.avisos().toString());
    }

    // --- Ayudas ---

    /**
     * Escandallo mínimo con la misma geometría torcida que el del ERP:
     * encabezados en B/K/X y valores en A/K/W.
     */
    private static byte[] escandalloSintetico(String cantidad) throws Exception {
        return excelDePrueba(hoja -> {
            Row modelo = hoja.createRow(3);
            modelo.createCell(13).setCellValue("MODEL");
            modelo.createCell(18).setCellValue("ULL770.AL245");
            modelo.createCell(27).setCellValue("COLOR");
            modelo.createCell(33).setCellValue("000    0014 NOIR");
            Row descripcion = hoja.createRow(4);
            descripcion.createCell(13).setCellValue("DESCRIPCIO");
            descripcion.createCell(18).setCellValue("SAC CANDY RABAT LARGE UNISEX");

            Row cabecera = hoja.createRow(8);
            cabecera.createCell(1).setCellValue("Article");
            cabecera.createCell(10).setCellValue("Descripcio");
            cabecera.createCell(23).setCellValue("Quantitat");
            cabecera.createCell(28).setCellValue("Preu Ult.");

            Row datos = hoja.createRow(9);
            datos.createCell(0).setCellValue("P-FOUB01");
            datos.createCell(10).setCellValue("PIEL/FOULARD NEGRO");
            datos.createCell(22).setCellValue(cantidad);
            datos.createCell(27).setCellValue("0,0000");

            hoja.createRow(10).createCell(31).setCellValue("Total");
        });
    }

    private static byte[] excelDePrueba(java.util.function.Consumer<Sheet> relleno)
            throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            relleno.accept(libro.createSheet("Escandall"));
            libro.write(salida);
            return salida.toByteArray();
        }
    }
}
