package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Flujo completo con el excel de pedido REAL de la temporada H26.
 *
 * Además de comprobar el reparto (46 bolsos MOROCCO, 26 bolsos SPAIN, 79
 * cinturones), deja los tres .xlsx en target/ para abrirlos e imprimirlos a
 * mano, igual que hace flujoCompletoGeneraExcelsAbribles con los packing
 * lists.
 */
class EtiquetasArticuloFlujoRealTest {

    private static final String RECURSO = "/ejemplos/EAN PUNTOTRES H26.xlsx";

    private static byte[] pedidoReal() throws Exception {
        try (InputStream entrada =
                     EtiquetasArticuloFlujoRealTest.class.getResourceAsStream(RECURSO)) {
            assertNotNull(entrada, "falta el recurso de test " + RECURSO);
            return entrada.readAllBytes();
        }
    }

    @Test
    void generaLosTresFicherosConSuNumeroDeHojasYLosDejaEnTarget() throws Exception {
        AmiEtiquetasArticuloGenerador generador =
                new AmiEtiquetasArticuloGenerador(new EtiquetasArticuloExcelBuilder());

        ResultadoEtiquetasArticulo resultado = generador.generar(pedidoReal(), "H26");

        Map<String, Integer> hojasPorFichero = new LinkedHashMap<>();
        Path destino = Path.of("target");
        for (ExcelEtiquetasArticulo excel : resultado.getExcels()) {
            try (XSSFWorkbook libro =
                         new XSSFWorkbook(new ByteArrayInputStream(excel.contenido()))) {
                hojasPorFichero.put(excel.nombreFichero(), libro.getNumberOfSheets());
            }
            // Para inspección manual: abrir y comprobar que cabe en un A4.
            Files.write(destino.resolve(excel.nombreFichero()), excel.contenido());
        }

        assertEquals(Map.of(
                        "AMI CODE BARRE H26 MOROCCO.xlsx", 46,
                        "AMI CODE BARRE H26 SPAIN.xlsx", 26,
                        "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx", 79),
                hojasPorFichero);
    }

    @Test
    void elPedidoRealNoProduceNingunAviso() throws Exception {
        AmiEtiquetasArticuloGenerador generador =
                new AmiEtiquetasArticuloGenerador(new EtiquetasArticuloExcelBuilder());

        ResultadoEtiquetasArticulo resultado = generador.generar(pedidoReal(), "H26");

        // Todas las filas de H26 traen ARTICLE, PO, talla y un EAN13 válido:
        // si algún día aparece un aviso aquí, el fichero del cliente ha
        // cambiado de forma y hay que mirarlo.
        assertTrue(resultado.getAvisos().isEmpty(), resultado.getAvisos().toString());
    }

    @Test
    void cadaHojaDelFicheroRealLleva40EtiquetasYSuCodigoDeBarras() throws Exception {
        AmiEtiquetasArticuloGenerador generador =
                new AmiEtiquetasArticuloGenerador(new EtiquetasArticuloExcelBuilder());

        ResultadoEtiquetasArticulo resultado = generador.generar(pedidoReal(), "H26");
        ExcelEtiquetasArticulo cinturones = resultado.getExcels().stream()
                .filter(excel -> excel.nombreFichero().contains("CINTURONES"))
                .findFirst().orElseThrow();

        try (XSSFWorkbook libro =
                     new XSSFWorkbook(new ByteArrayInputStream(cinturones.contenido()))) {
            // Una imagen por hoja, no una por etiqueta.
            assertEquals(libro.getNumberOfSheets(), libro.getAllPictures().size());
            for (int i = 0; i < libro.getNumberOfSheets(); i++) {
                assertEquals(40, libro.getSheetAt(i).getDrawingPatriarch().getShapes().size(),
                        "hoja " + libro.getSheetName(i));
            }
        }
    }
}
