package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.etiquetas.AmiEtiquetasExcelBuilder.EtiquetaCaja;

class AmiEtiquetasExcelBuilderTest {

    private final AmiEtiquetasExcelBuilder builder = new AmiEtiquetasExcelBuilder();

    private static EtiquetaCaja etiquetaBolso(String parcel) {
        return new EtiquetaCaja("H26", "ULL163.AL0052", "221 BLACK",
                "U", "50", "5,28 KGS", parcel, "07703");
    }

    @Test
    void generaUnaHojaSoloConLaDestinacionPedida() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(etiquetaBolso("1 / 1")));
        try (XSSFWorkbook libro = abrir(excel)) {
            assertEquals(1, libro.getNumberOfSheets());
            assertEquals("AMI CHINA", libro.getSheetName(0));
        }
    }

    @Test
    void escribeLosValoresEnLasDosEtiquetasDelPar() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(etiquetaBolso("1 / 1")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // Etiqueta 1 (bloque 0)
            assertEquals("H26", texto(hoja, 11, 1));
            assertEquals("ULL163.AL0052", texto(hoja, 11, 2));
            assertEquals("221 BLACK", texto(hoja, 12, 2));
            assertEquals("U", texto(hoja, 13, 2));
            assertEquals("50", texto(hoja, 14, 2));
            assertEquals("5,28 KGS", texto(hoja, 15, 2));
            assertEquals("1 / 1", texto(hoja, 16, 2));
            // Etiqueta 2 = mismas celdas + offset 17
            assertEquals("ULL163.AL0052", texto(hoja, 11 + 17, 2));
            assertEquals("1 / 1", texto(hoja, 16 + 17, 2));
        }
    }

    @Test
    void replicaElBloqueParaCadaCajaYSeparaLasPaginas() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.FRANCE, List.of(
                new EtiquetaCaja("H26", "UBL029.AL0216", "001 BLACK", "85-95",
                        "4-85,33-95", "9,93 KGS", "1 / 2", "07672"),
                new EtiquetaCaja("H26", "UBL029.AL0216", "001 BLACK", "105",
                        "3-105", null, "2 / 2", "07672")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // Caja 1, etiqueta 1 (FRANCE: bloque de 32 filas, valores desde fila 10).
            assertEquals("85-95", texto(hoja, 12, 2));
            assertEquals("4-85,33-95", texto(hoja, 13, 2));
            // Caja 2 = bloque desplazado 32 filas; peso null = celda en blanco.
            assertEquals("UBL029.AL0216", texto(hoja, 10 + 32, 2));
            assertEquals("3-105", texto(hoja, 13 + 32, 2));
            assertEquals("", texto(hoja, 14 + 32, 2));
            assertEquals("2 / 2", texto(hoja, 15 + 32, 2));
            // Salto de página entre las dos cajas (cada par en su A4).
            assertTrue(hoja.getRowBreaks().length >= 1);
            assertEquals(31, hoja.getRowBreaks()[0]);
            // Los estilos/altos del bloque copiado se conservan (fila de
            // cabecera de la 2ª caja con el alto de la plantilla).
            assertEquals(hoja.getRow(2).getHeightInPoints(),
                    hoja.getRow(2 + 32).getHeightInPoints(), 0.01);
        }
    }

    @Test
    void insertaUnCodigoDeBarrasPorEtiquetaYNingunaImagenDeEjemplo() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                etiquetaBolso("1 / 2"), etiquetaBolso("2 / 2")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            XSSFDrawing dibujo = hoja.getDrawingPatriarch();
            assertNotNull(dibujo);
            // 2 cajas x 2 etiquetas = 4 códigos de barras, y nada más
            // (las imágenes de ejemplo de la plantilla se limpian).
            assertEquals(4, dibujo.getShapes().size());
        }
    }

    @Test
    void enJapanCadaParLlevaAdemasLaDireccionComoImagen() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.JAPAN, List.of(
                etiquetaBolso("1 / 2"), etiquetaBolso("2 / 2")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // 2 cajas x (2 barcodes + 2 direcciones) = 8 imágenes.
            assertEquals(8, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void sinOrderNumberNoHayBarcodePeroElExcelSaleIgual() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                new EtiquetaCaja("H26", "ULL163.AL0052", null, "U", "50",
                        null, "1 / 1", null)));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertEquals("ULL163.AL0052", texto(hoja, 11, 2));
            XSSFDrawing dibujo = hoja.getDrawingPatriarch();
            assertTrue(dibujo == null || dibujo.getShapes().isEmpty());
        }
        // Copia para inspección manual, como hace el e2e de packing lists.
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "etiquetas-ami-china-sin-barcode.xlsx"), excel);
    }

    private static XSSFWorkbook abrir(byte[] contenido) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(contenido));
    }

    private static String texto(XSSFSheet hoja, int fila, int col) {
        if (hoja.getRow(fila) == null || hoja.getRow(fila).getCell(col) == null) {
            return "";
        }
        return hoja.getRow(fila).getCell(col).toString().trim();
    }
}
