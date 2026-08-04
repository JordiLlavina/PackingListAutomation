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
    private static final EtiquetaArticulo ARTICULO_BOLSO = new EtiquetaArticulo(
            "ULL163.AL0052", "Size: U", "221 BLACK", "Cde: 07703", EAN13_BOLSO);

    private static final EtiquetaArticulo ARTICULO = new EtiquetaArticulo(
            "ULL163.AL0052", "Size: U", "221 DARK COFFEE", "Cde: 07703", "3666598354771");

    private static EtiquetaCaja etiquetaBolso(String parcel) {
        return new EtiquetaCaja("H26", "ULL163.AL0052", "221 BLACK",
                "U", "50", "5,28 KGS", parcel, "07703", EAN128_BOLSO, ARTICULO_BOLSO);
    }

    private static XSSFWorkbook generar(AmiEtiquetaLayout layout) throws IOException {
        AmiEtiquetasExcelBuilder builder = new AmiEtiquetasExcelBuilder();
        byte[] contenido = builder.generar(layout, List.of(new AmiEtiquetasExcelBuilder
                .EtiquetaCaja("H26", "ULL163.AL0052", "221", "U", "5", "5,28 KGS",
                        "1 / 1", "07703", "366659835477100001000077030000000000000000ES",
                        ARTICULO)));
        return new XSSFWorkbook(new ByteArrayInputStream(contenido));
    }

    private static XSSFWorkbook generarUnaEtiquetaFrance() throws IOException {
        return generar(AmiEtiquetaLayout.FRANCE);
    }

    private static XSSFWorkbook generarUnaEtiquetaJapan() throws IOException {
        return generar(AmiEtiquetaLayout.JAPAN);
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
            AmiEtiquetaLayout layout = AmiEtiquetaLayout.CHINA;
            // Etiqueta 1 (bloque 0). El order number pisa el valor de
            // ejemplo que trae la plantilla.
            assertEquals("07703", texto(hoja, layout.filaOrderNumber(), 2));
            assertEquals("H26", texto(hoja, layout.filaTemporada(), 1));
            assertEquals("ULL163.AL0052", texto(hoja, layout.filaReferencia(), 2));
            assertEquals("221 BLACK", texto(hoja, layout.filaColor(), 2));
            assertEquals("U", texto(hoja, layout.filaTalla(), 2));
            assertEquals("50", texto(hoja, layout.filaCantidad(), 2));
            assertEquals("5,28 KGS", texto(hoja, layout.filaPeso(), 2));
            assertEquals("1 / 1", texto(hoja, layout.filaParcel(), 2));
            // Etiqueta 2 = mismas celdas + offsetSegundaEtiqueta
            int offset = layout.offsetSegundaEtiqueta();
            assertEquals("07703", texto(hoja, layout.filaOrderNumber() + offset, 2));
            assertEquals("ULL163.AL0052", texto(hoja, layout.filaReferencia() + offset, 2));
            assertEquals("1 / 1", texto(hoja, layout.filaParcel() + offset, 2));
        }
    }

    @Test
    void replicaElBloqueParaCadaCajaYSeparaLasPaginas() throws IOException {
        AmiEtiquetaLayout layout = AmiEtiquetaLayout.FRANCE;
        byte[] excel = builder.generar(layout, List.of(
                new EtiquetaCaja("H26", "UBL029.AL0216", "001 BLACK", "85-95",
                        "4-85,33-95", "9,93 KGS", "1 / 2", "07672",
                        EAN128_BOLSO, ARTICULO_BOLSO),
                new EtiquetaCaja("H26", "UBL029.AL0216", "001 BLACK", "105",
                        "3-105", null, "2 / 2", "07672",
                        EAN128_BOLSO, ARTICULO_BOLSO)));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // Caja 1, etiqueta 1 (FRANCE: bloque de 32 filas).
            assertEquals("85-95", texto(hoja, layout.filaTalla(), 2));
            assertEquals("4-85,33-95", texto(hoja, layout.filaCantidad(), 2));
            // Caja 2 = bloque desplazado 32 filas; peso null = celda en blanco.
            // El order number también se escribe en los bloques copiados.
            int b = layout.alturaBloque();
            assertEquals("07672", texto(hoja, layout.filaOrderNumber() + b, 2));
            assertEquals("UBL029.AL0216", texto(hoja, layout.filaReferencia() + b, 2));
            assertEquals("3-105", texto(hoja, layout.filaCantidad() + b, 2));
            assertEquals("", texto(hoja, layout.filaPeso() + b, 2));
            assertEquals("2 / 2", texto(hoja, layout.filaParcel() + b, 2));
            // Salto de página entre las dos cajas (cada par en su A4).
            assertTrue(hoja.getRowBreaks().length >= 1);
            assertEquals(b - 1, hoja.getRowBreaks()[0]);
            // Los estilos/altos del bloque copiado se conservan (fila de
            // cabecera de la 2ª caja con el alto de la plantilla).
            assertEquals(hoja.getRow(2).getHeightInPoints(),
                    hoja.getRow(2 + b).getHeightInPoints(), 0.01);
        }
    }

    @Test
    void insertaLaImagenCompuestaYElEan128PorEtiquetaYNingunaImagenDeEjemplo() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                etiquetaBolso("1 / 2"), etiquetaBolso("2 / 2")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            XSSFDrawing dibujo = hoja.getDrawingPatriarch();
            assertNotNull(dibujo);
            // 2 cajas x 2 etiquetas x 2 imágenes (compuesta + EAN128) = 8, y
            // nada más (las imágenes de ejemplo de la plantilla se limpian).
            assertEquals(8, dibujo.getShapes().size());
        }
    }

    @Test
    void cadaImagenSeAnclaEnLaFilaQueDiceElLayout() throws IOException {
        AmiEtiquetaLayout layout = AmiEtiquetaLayout.CHINA;
        byte[] excel = builder.generar(layout, List.of(etiquetaBolso("1 / 1")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFDrawing dibujo = libro.getSheetAt(0).getDrawingPatriarch();
            List<Integer> filas = dibujo.getShapes().stream()
                    .map(forma -> ((XSSFPicture) forma).getClientAnchor().getRow1())
                    .sorted()
                    .toList();
            // Las dos filas del layout de CHINA (imagen compuesta y EAN128) y
            // las mismas + offsetSegundaEtiqueta para la etiqueta de abajo.
            int offset = layout.offsetSegundaEtiqueta();
            List<Integer> esperadas = List.of(
                    layout.imagenArticulo().fila(), layout.ean128().fila(),
                    layout.imagenArticulo().fila() + offset, layout.ean128().fila() + offset)
                    .stream().sorted().toList();
            assertEquals(esperadas, filas);
        }
    }

    @Test
    void laTemporadaConservaElEstiloRojoDeLaPlantilla() throws IOException {
        // En la plantilla de AMI FRANCE la celda de temporada va en rojo y
        // así la quiere el cliente: el builder no debe alterar su estilo.
        AmiEtiquetaLayout layout = AmiEtiquetaLayout.FRANCE;
        byte[] excel = builder.generar(layout, List.of(
                new EtiquetaCaja("H26", "UBL029.AL0216", "001 BLACK", "85",
                        "4", "9,93 KGS", "1 / 1", "07672", EAN128_BOLSO, ARTICULO_BOLSO)));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            for (int fila : new int[] {
                    layout.filaTemporada(), layout.filaTemporada() + layout.offsetSegundaEtiqueta() }) {
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
            // 2 cajas x 2 etiquetas x (2 imágenes + 1 dirección) = 12 imágenes.
            assertEquals(12, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void sinEan128SoloSaleLaImagenCompuesta() throws IOException {
        // El Code 128 del PO ya no existe en la plantilla: sin EAN128, lo
        // único que puede dibujarse es la imagen compuesta del artículo.
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                new EtiquetaCaja("H26", "ULL163.AL0052", "221 BLACK", "U", "50",
                        "5,28 KGS", "1 / 1", "07703", null, ARTICULO_BOLSO)));
        try (XSSFWorkbook libro = abrir(excel)) {
            // Solo la imagen compuesta, en las dos etiquetas del par.
            assertEquals(2, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void unEan13InvalidoDentroDelArticuloNoRompeElExcel() throws IOException {
        // ImagenEtiquetaArticulo ya filtra el EAN13 inválido puertas adentro
        // (la imagen sale sin el código de barras); el builder no debe
        // reventar ni dejar de insertar la imagen compuesta.
        EtiquetaArticulo articuloConEanInvalido = new EtiquetaArticulo(
                "ULL163.AL0052", "Size: U", "221 BLACK", "Cde: 07703", "123");
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                new EtiquetaCaja("H26", "ULL163.AL0052", "221 BLACK", "U", "50",
                        "5,28 KGS", "1 / 1", "07703", EAN128_BOLSO, articuloConEanInvalido)));
        try (XSSFWorkbook libro = abrir(excel)) {
            // Imagen compuesta + EAN128 en las dos etiquetas = 4.
            assertEquals(4, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void elMismoCodigoNoSeGuardaDosVecesEnElLibro() throws IOException {
        // 3 cajas iguales = 12 imágenes ancladas, pero el .xlsx no debe
        // guardar el PNG de cada imagen más de una vez. Se compara contra una
        // sola caja en vez de contra un número fijo porque el libro arrastra
        // además las imágenes de ejemplo de la plantilla (solo se les quita
        // el anclaje, la parte de imagen se queda).
        byte[] unaCaja = builder.generar(AmiEtiquetaLayout.CHINA,
                List.of(etiquetaBolso("1 / 1")));
        byte[] tresCajas = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                etiquetaBolso("1 / 3"), etiquetaBolso("2 / 3"), etiquetaBolso("3 / 3")));
        try (XSSFWorkbook conUna = abrir(unaCaja); XSSFWorkbook conTres = abrir(tresCajas)) {
            assertEquals(4, conUna.getSheetAt(0).getDrawingPatriarch().getShapes().size());
            assertEquals(12, conTres.getSheetAt(0).getDrawingPatriarch().getShapes().size());
            assertEquals(conUna.getAllPictures().size(), conTres.getAllPictures().size(),
                    "tres cajas iguales han guardado más PNGs que una: falta la caché");
        }
    }

    @Test
    void sinOrderNumberNoHayImagenesPeroElExcelSaleIgual() throws IOException {
        AmiEtiquetaLayout layout = AmiEtiquetaLayout.CHINA;
        byte[] excel = builder.generar(layout, List.of(
                new EtiquetaCaja("H26", "ULL163.AL0052", null, "U", "50",
                        null, "1 / 1", null, null, null)));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertEquals("ULL163.AL0052", texto(hoja, layout.filaReferencia(), 2));
            // Sin order number la celda queda en blanco (el 7703 de ejemplo
            // de la plantilla no debe sobrevivir).
            assertEquals("", texto(hoja, layout.filaOrderNumber(), 2));
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

    @Test
    void yaNoHayCodigoDeBarrasDelPo() throws Exception {
        // Antes había 3 imágenes por etiqueta (PO, EAN13, EAN128) y ahora 2
        // (imagen compuesta y EAN128), duplicadas por el par de etiquetas.
        XSSFWorkbook libro = generarUnaEtiquetaFrance();
        assertEquals(4, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
    }

    @Test
    void laImagenCompuestaSeAnclaEnElHuecoDeLaPlantilla() throws Exception {
        XSSFWorkbook libro = generarUnaEtiquetaFrance();
        AnclajeBloque hueco = AmiEtiquetaLayout.FRANCE.imagenArticulo();
        // getClientAnchor() y no getPreferredSize(): esta última intenta
        // reescalar a un anclaje de dos celdas y revienta con NPE en las
        // imágenes que el builder ancla con tamaño fijo (una sola celda).
        boolean encontrada = libro.getSheetAt(0).getDrawingPatriarch().getShapes().stream()
                .filter(XSSFPicture.class::isInstance)
                .map(forma -> ((XSSFPicture) forma).getClientAnchor().getFrom())
                .anyMatch(desde -> desde.getRow() == hueco.fila()
                        && ((Number) desde.getColOff()).longValue() == hueco.dx());
        assertTrue(encontrada, "no hay ninguna imagen anclada en el hueco de la plantilla");
    }

    @Test
    void japanConservaSuImagenDeDireccion() throws Exception {
        // La plantilla nueva tiene dos PNG: el mock de la imagen compuesta y
        // la dirección. Si se coge "el primer PNG del libro" sale el mock.
        XSSFWorkbook libro = generarUnaEtiquetaJapan();
        AnclajeBloque direccion = AmiEtiquetaLayout.JAPAN_DIRECCION;
        boolean encontrada = libro.getSheetAt(0).getDrawingPatriarch().getShapes().stream()
                .filter(XSSFPicture.class::isInstance)
                .map(XSSFPicture.class::cast)
                .anyMatch(imagen -> imagen.getClientAnchor().getFrom().getRow()
                                == direccion.fila()
                        && imagen.getPictureData().getData().length > 10_000);
        assertTrue(encontrada, "la dirección de JAPAN no está o es la imagen equivocada");
    }

    @Test
    void elOrderNumberYLaReferenciaCaenEnLasFilasNuevas() throws Exception {
        XSSFWorkbook libro = generarUnaEtiquetaFrance();
        XSSFSheet hoja = libro.getSheetAt(0);
        assertEquals("07703",
                hoja.getRow(AmiEtiquetaLayout.FRANCE.filaOrderNumber()).getCell(2)
                        .getStringCellValue());
        assertEquals("ULL163.AL0052",
                hoja.getRow(AmiEtiquetaLayout.FRANCE.filaReferencia()).getCell(2)
                        .getStringCellValue());
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
