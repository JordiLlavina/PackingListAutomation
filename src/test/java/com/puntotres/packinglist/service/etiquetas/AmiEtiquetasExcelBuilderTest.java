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
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.etiquetas.AmiEtiquetasExcelBuilder.EtiquetaCaja;

class AmiEtiquetasExcelBuilderTest {

    private final AmiEtiquetasExcelBuilder builder = new AmiEtiquetasExcelBuilder();

    private static final String EAN13_BOLSO = "3666598354771";
    private static final String EAN128_BOLSO =
            "366659835477100001000077030000000000000000MA";

    private static EtiquetaCaja etiquetaBolso(String parcel) {
        return new EtiquetaCaja("H26", "ULL163.AL0052", "221 BLACK",
                "U", "50", "5,28 KGS", parcel, "07703", EAN13_BOLSO, EAN128_BOLSO);
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
            // Etiqueta 1 (bloque 0). El order number pisa el valor de
            // ejemplo que trae la plantilla (7703 numérico en C10).
            assertEquals("07703", texto(hoja, 9, 2));
            assertEquals("H26", texto(hoja, 11, 1));
            assertEquals("ULL163.AL0052", texto(hoja, 11, 2));
            assertEquals("221 BLACK", texto(hoja, 12, 2));
            assertEquals("U", texto(hoja, 13, 2));
            assertEquals("50", texto(hoja, 14, 2));
            assertEquals("5,28 KGS", texto(hoja, 15, 2));
            assertEquals("1 / 1", texto(hoja, 16, 2));
            // Etiqueta 2 = mismas celdas + offset 17
            assertEquals("07703", texto(hoja, 9 + 17, 2));
            assertEquals("ULL163.AL0052", texto(hoja, 11 + 17, 2));
            assertEquals("1 / 1", texto(hoja, 16 + 17, 2));
        }
    }

    @Test
    void replicaElBloqueParaCadaCajaYSeparaLasPaginas() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.FRANCE, List.of(
                new EtiquetaCaja("H26", "UBL029.AL0216", "001 BLACK", "85-95",
                        "4-85,33-95", "9,93 KGS", "1 / 2", "07672",
                        EAN13_BOLSO, EAN128_BOLSO),
                new EtiquetaCaja("H26", "UBL029.AL0216", "001 BLACK", "105",
                        "3-105", null, "2 / 2", "07672",
                        EAN13_BOLSO, EAN128_BOLSO)));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // Caja 1, etiqueta 1 (FRANCE: bloque de 32 filas, valores desde fila 10).
            assertEquals("85-95", texto(hoja, 12, 2));
            assertEquals("4-85,33-95", texto(hoja, 13, 2));
            // Caja 2 = bloque desplazado 32 filas; peso null = celda en blanco.
            // El order number también se escribe en los bloques copiados.
            assertEquals("07672", texto(hoja, 8 + 32, 2));
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
    void insertaLosTresCodigosPorEtiquetaYNingunaImagenDeEjemplo() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                etiquetaBolso("1 / 2"), etiquetaBolso("2 / 2")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            XSSFDrawing dibujo = hoja.getDrawingPatriarch();
            assertNotNull(dibujo);
            // 2 cajas x 2 etiquetas x 3 códigos = 12 imágenes, y nada más
            // (las imágenes de ejemplo de la plantilla se limpian).
            assertEquals(12, dibujo.getShapes().size());
        }
    }

    @Test
    void cadaCodigoSeAnclaEnLaFilaQueDiceElLayout() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA,
                List.of(etiquetaBolso("1 / 1")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFDrawing dibujo = libro.getSheetAt(0).getDrawingPatriarch();
            List<Integer> filas = dibujo.getShapes().stream()
                    .map(forma -> ((XSSFPicture) forma).getClientAnchor().getRow1())
                    .sorted()
                    .toList();
            // Las tres filas del layout de CHINA (8, 10, 12) y las mismas
            // + offsetSegundaEtiqueta (17) para la etiqueta de abajo.
            assertEquals(List.of(8, 10, 12, 8 + 17, 10 + 17, 12 + 17), filas);
        }
    }

    @Test
    void laTemporadaConservaElEstiloRojoDeLaPlantilla() throws IOException {
        // En la plantilla de AMI FRANCE la celda de temporada va en rojo y
        // así la quiere el cliente: el builder no debe alterar su estilo.
        byte[] excel = builder.generar(AmiEtiquetaLayout.FRANCE, List.of(
                new EtiquetaCaja("H26", "UBL029.AL0216", "001 BLACK", "85",
                        "4", "9,93 KGS", "1 / 1", "07672", EAN13_BOLSO, EAN128_BOLSO)));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            for (int fila : new int[] {10, 10 + 16}) {
                var fuente = hoja.getRow(fila).getCell(1).getCellStyle().getFont();
                boolean roja = fuente.getXSSFColor() != null
                        && "FFFF0000".equals(fuente.getXSSFColor().getARGBHex());
                assertTrue(roja, "La temporada de la fila " + fila + " ya no está en rojo");
            }
        }
    }

    @Test
    void enJapanCadaParLlevaAdemasLaDireccionComoImagen() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.JAPAN, List.of(
                etiquetaBolso("1 / 2"), etiquetaBolso("2 / 2")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // 2 cajas x 2 etiquetas x (3 códigos + 1 dirección) = 16 imágenes.
            assertEquals(16, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void sinEanLosOtrosCodigosSiguenSaliendo() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                new EtiquetaCaja("H26", "ULL163.AL0052", "221 BLACK", "U", "50",
                        "5,28 KGS", "1 / 1", "07703", null, null)));
        try (XSSFWorkbook libro = abrir(excel)) {
            // Solo el barcode del PO, en las dos etiquetas del par.
            assertEquals(2, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void unEan13InvalidoNoRompeElExcel() throws IOException {
        // El generador ya lo filtra, pero el builder no debe reventar si le
        // llega uno malo: la etiqueta sale sin ese código.
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                new EtiquetaCaja("H26", "ULL163.AL0052", "221 BLACK", "U", "50",
                        "5,28 KGS", "1 / 1", "07703", "123", EAN128_BOLSO)));
        try (XSSFWorkbook libro = abrir(excel)) {
            // PO + EAN128 en las dos etiquetas = 4 (el EAN13 malo no se dibuja).
            assertEquals(4, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void elMismoCodigoNoSeGuardaDosVecesEnElLibro() throws IOException {
        // 3 cajas iguales = 18 imágenes ancladas, pero el .xlsx no debe
        // guardar el PNG de cada código más de una vez. Se compara contra una
        // sola caja en vez de contra un número fijo porque el libro arrastra
        // además las imágenes de ejemplo de la plantilla (solo se les quita
        // el anclaje, la parte de imagen se queda).
        byte[] unaCaja = builder.generar(AmiEtiquetaLayout.CHINA,
                List.of(etiquetaBolso("1 / 1")));
        byte[] tresCajas = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                etiquetaBolso("1 / 3"), etiquetaBolso("2 / 3"), etiquetaBolso("3 / 3")));
        try (XSSFWorkbook conUna = abrir(unaCaja); XSSFWorkbook conTres = abrir(tresCajas)) {
            assertEquals(6, conUna.getSheetAt(0).getDrawingPatriarch().getShapes().size());
            assertEquals(18, conTres.getSheetAt(0).getDrawingPatriarch().getShapes().size());
            assertEquals(conUna.getAllPictures().size(), conTres.getAllPictures().size(),
                    "tres cajas iguales han guardado más PNGs que una: falta la caché");
        }
    }

    @Test
    void sinOrderNumberNoHayBarcodePeroElExcelSaleIgual() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                new EtiquetaCaja("H26", "ULL163.AL0052", null, "U", "50",
                        null, "1 / 1", null, null, null)));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertEquals("ULL163.AL0052", texto(hoja, 11, 2));
            // Sin order number la celda queda en blanco (el 7703 de ejemplo
            // de la plantilla no debe sobrevivir).
            assertEquals("", texto(hoja, 9, 2));
            XSSFDrawing dibujo = hoja.getDrawingPatriarch();
            assertTrue(dibujo == null || dibujo.getShapes().isEmpty());
        }
        // Copia para inspección manual, como hace el e2e de packing lists.
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "etiquetas-ami-china-sin-barcode.xlsx"), excel);
    }

    @Test
    void sinFilasExtraElLibroSoloTieneLaHojaDeEtiquetas() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA,
                List.of(etiquetaBolso("1 / 1")), List.of());

        try (XSSFWorkbook libro = abrir(excel)) {
            assertEquals(1, libro.getNumberOfSheets());
        }
    }

    @Test
    void conFilasExtraElLibroAnadeLaHojaDeCodigosDeBarras() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA,
                List.of(etiquetaBolso("1 / 1")),
                List.of(new FilaCodigoBarrasExtra(1, "ULL753.AL0168", "001 IVORY", "U", "5",
                        "3666598313495", null)));

        try (XSSFWorkbook libro = abrir(excel)) {
            assertEquals(2, libro.getNumberOfSheets());
            assertEquals("AMI CHINA", libro.getSheetName(0));
            assertEquals(HojaCodigosBarrasExtra.NOMBRE_HOJA, libro.getSheetName(1));
        }
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
