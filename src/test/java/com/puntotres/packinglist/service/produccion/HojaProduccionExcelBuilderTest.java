package com.puntotres.packinglist.service.produccion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.DatosHojaProduccion;
import com.puntotres.packinglist.model.DatosHojaProduccion.LineaProduccion;
import com.puntotres.packinglist.testutil.FotosDePrueba;

/**
 * El excel de la Hoja de Producción, reabierto con POI. Deja
 * {@code target/Hoja de produccion ejemplo.xlsx} para revisar la maquetación a ojo.
 */
class HojaProduccionExcelBuilderTest {

    private final HojaProduccionExcelBuilder builder = new HojaProduccionExcelBuilder();

    @Test
    void laCabeceraLlevaElModeloElNumeroYLasCuatroFichas() throws Exception {
        try (XSSFWorkbook libro = abrir(builder.generar(datos()))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            int titulo = HojaProduccionExcelBuilder.TITULO_DESDE;
            assertEquals("MODELO", texto(hoja, HojaProduccionExcelBuilder.FILA_ROTULOS, titulo));
            assertEquals("F67104", texto(hoja, HojaProduccionExcelBuilder.FILA_MODELO, titulo));
            assertEquals("LE NEIGE", texto(hoja, HojaProduccionExcelBuilder.FILA_BOLSO, titulo));
            int numero = HojaProduccionExcelBuilder.NUMERO_DESDE;
            assertEquals("HOJA DE PRODUCCIÓN", texto(hoja, HojaProduccionExcelBuilder.FILA_ROTULOS, numero));
            assertEquals("Nº 325", texto(hoja, HojaProduccionExcelBuilder.FILA_MODELO, numero));
            // Los títulos, en un gris un par de tonos más oscuro que el de los rótulos, no en tinta.
            for (int[] celda : new int[][] {{HojaProduccionExcelBuilder.FILA_MODELO, titulo},
                    {HojaProduccionExcelBuilder.FILA_MODELO, numero}, {HojaProduccionExcelBuilder.FILA_BOLSO, titulo}}) {
                XSSFRichTextString rico = hoja.getRow(celda[0]).getCell(celda[1]).getRichStringCellValue();
                assertArrayEquals(HojaProduccionExcelBuilder.COLOR_TITULO, rico.getFontAtIndex(0).getXSSFColor().getRGB());
            }

            int rotulos = HojaProduccionExcelBuilder.FILA_FICHA_ROTULO;
            int valores = HojaProduccionExcelBuilder.FILA_FICHA_VALOR;
            assertEquals("CLIENTE", texto(hoja, rotulos, titulo));
            assertEquals("A.P.C.", texto(hoja, valores, titulo));
            assertEquals("TEMPORADA", texto(hoja, rotulos, titulo + 2));
            assertEquals("SMS FALL 27", texto(hoja, valores, titulo + 2));
            assertEquals("TALLER", texto(hoja, rotulos, titulo + 4));
            assertEquals("C&C LEATHER", texto(hoja, valores, titulo + 4));
            assertEquals("FECHA DE SALIDA", texto(hoja, rotulos, titulo + 6));
            assertEquals("24/11/2026", texto(hoja, valores, titulo + 6));
        }
    }

    @Test
    void laFotoVaEnGrandeALaIzquierdaDeLaCabecera() throws Exception {
        byte[] foto = FotosDePrueba.jpeg(400, 300, Color.ORANGE);
        try (XSSFWorkbook libro = abrir(builder.generar(datos(), foto))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            XSSFDrawing dibujo = hoja.getDrawingPatriarch();
            List<XSSFPicture> fotos = dibujo.getShapes().stream()
                    .filter(XSSFPicture.class::isInstance).map(XSSFPicture.class::cast).toList();
            assertEquals(1, fotos.size());
            XSSFClientAnchor ancla = fotos.get(0).getClientAnchor();
            assertTrue(ancla.getCol1() >= HojaProduccionExcelBuilder.PRIMERA
                    && ancla.getCol2() <= HojaProduccionExcelBuilder.FOTO_HASTA, "dentro de las columnas de la foto");
            assertTrue(ancla.getRow2() - ancla.getRow1() >= 3, "ocupa casi toda la cabecera a lo alto");
            assertTrue(hoja.getMergedRegions().contains(new CellRangeAddress(HojaProduccionExcelBuilder.FILA_ROTULOS,
                    HojaProduccionExcelBuilder.FILA_FICHA_VALOR, HojaProduccionExcelBuilder.PRIMERA,
                    HojaProduccionExcelBuilder.FOTO_HASTA)));
            assertArrayEquals(foto, fotos.get(0).getPictureData().getData());
        }
    }

    @Test
    void sinFotoElHuecoLoDiceYLaHojaSaleIgual() throws Exception {
        try (XSSFWorkbook libro = abrir(builder.generar(datos(), new byte[] {1, 2, 3}))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertEquals("SIN FOTO", texto(hoja, HojaProduccionExcelBuilder.FILA_ROTULOS, HojaProduccionExcelBuilder.PRIMERA));
            assertNull(hoja.getDrawingPatriarch(), "una foto que no se deja leer no se pega");
        }
    }

    @Test
    void laTablaYaNoTieneColumnaDeModeloYAgrupaLasPieles() throws Exception {
        try (XSSFWorkbook libro = abrir(builder.generar(datos()))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            int grupos = HojaProduccionExcelBuilder.FILA_GRUPOS;
            int columnas = HojaProduccionExcelBuilder.FILA_COLUMNAS;
            assertEquals("UNIDADES", texto(hoja, grupos, HojaProduccionExcelBuilder.PRIMERA));
            assertEquals("PIEL BASE", texto(hoja, grupos, HojaProduccionExcelBuilder.PIEL_BASE));
            assertEquals("PIEL COMBINACIÓN", texto(hoja, grupos, HojaProduccionExcelBuilder.PIEL_COMBINACION));
            assertEquals("MARCADO", texto(hoja, grupos, HojaProduccionExcelBuilder.MARCADO_INTERIOR));
            assertEquals("PIEL", texto(hoja, columnas, HojaProduccionExcelBuilder.PIEL_BASE));
            assertEquals("COLOR", texto(hoja, columnas, HojaProduccionExcelBuilder.COLOR_COMBINACION));
            assertEquals("EXTERIOR", texto(hoja, columnas, HojaProduccionExcelBuilder.MARCADO_EXTERIOR));
            for (Cell celda : hoja.getRow(grupos)) {
                assertFalse("MODELO".equals(celda.getStringCellValue()), "el modelo va en el título, no en la tabla");
            }
            // La fila de grupos, en tinta con la letra blanca: el estilo de tabla que prefiere el usuario.
            XSSFCellStyle estiloGrupo = hoja.getRow(grupos).getCell(HojaProduccionExcelBuilder.PIEL_BASE).getCellStyle();
            assertArrayEquals(HojaProduccionExcelBuilder.RELLENO_GRUPOS, estiloGrupo.getFillForegroundXSSFColor().getRGB());
            XSSFRichTextString rotuloGrupo = hoja.getRow(grupos).getCell(HojaProduccionExcelBuilder.PIEL_BASE)
                    .getRichStringCellValue();
            assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                    rotuloGrupo.getFontAtIndex(0).getXSSFColor().getRGB());

            int linea = HojaProduccionExcelBuilder.PRIMERA_LINEA;
            assertEquals(3, (int) hoja.getRow(linea).getCell(HojaProduccionExcelBuilder.UNIDADES).getNumericCellValue());
            assertEquals("BADANA", texto(hoja, linea, HojaProduccionExcelBuilder.PIEL_BASE));
            assertEquals("NOIR", texto(hoja, linea, HojaProduccionExcelBuilder.COLOR_BASE));
            assertEquals("Gütermann\n181\n3 CM", texto(hoja, linea, HojaProduccionExcelBuilder.HILO),
                    "marca, número y cm, cada uno en su línea");
            assertEquals("MADE IN MOROCCO", texto(hoja, linea, HojaProduccionExcelBuilder.MARCADO_INTERIOR));
            assertEquals("APC ORO", texto(hoja, linea, HojaProduccionExcelBuilder.MARCADO_EXTERIOR));
            assertEquals("SARJA 005", texto(hoja, linea, HojaProduccionExcelBuilder.FORRO),
                    "las minúsculas del formulario salen en mayúsculas");
            assertEquals("CH 1999", texto(hoja, linea, HojaProduccionExcelBuilder.TINTA_COL));
            assertTrue(hoja.getRow(linea + 1) == null, "con un solo color no hay fila de total");
        }
    }

    @Test
    void todasLasFilasDeLaTablaMidenLoMismoYAlMenos110() throws Exception {
        String largo = "PIEL DE BECERRO GRANULADO CON ACABADO MATE Y TACTO SUAVE, PARTIDA ESPECIAL DE LA "
                + "CURTIDURÍA, REVISAR CADA PIEZA ANTES DE CORTAR Y APARTAR LAS QUE TENGAN MARCAS";
        DatosHojaProduccion corta = datos();
        DatosHojaProduccion conLarga = new DatosHojaProduccion("APC", "A.P.C.", "SMS FALL 27", "F67104", "", "LE NEIGE",
                "C&C LEATHER", 326, LocalDate.of(2026, 11, 24), List.of(linea("BADANA"), linea(largo)));

        try (XSSFWorkbook libro = abrir(builder.generar(corta))) {
            Row fila = libro.getSheetAt(0).getRow(HojaProduccionExcelBuilder.PRIMERA_LINEA);
            assertEquals(HojaProduccionExcelBuilder.ALTO_LINEA, fila.getHeightInPoints(), 0.01);
        }
        try (XSSFWorkbook libro = abrir(builder.generar(conLarga))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            Row primera = hoja.getRow(HojaProduccionExcelBuilder.PRIMERA_LINEA);
            Row segunda = hoja.getRow(HojaProduccionExcelBuilder.PRIMERA_LINEA + 1);
            assertEquals(primera.getHeightInPoints(), segunda.getHeightInPoints(), 0.01, "el mismo alto en todas");
            assertTrue(segunda.getHeightInPoints() > HojaProduccionExcelBuilder.ALTO_LINEA,
                    "un texto que no cabe en 110 las hace crecer a todas");
            assertTrue(segunda.getCell(HojaProduccionExcelBuilder.PIEL_BASE).getCellStyle().getWrapText());
            Row total = hoja.getRow(HojaProduccionExcelBuilder.PRIMERA_LINEA + 2);
            assertEquals(6, (int) total.getCell(HojaProduccionExcelBuilder.UNIDADES).getNumericCellValue());
            assertEquals("UNIDADES EN TOTAL", total.getCell(HojaProduccionExcelBuilder.PIEL_BASE).getStringCellValue());
        }
    }

    @Test
    void seImprimeApaisadoAjustadoAlAnchoDeUnaPagina() throws Exception {
        try (XSSFWorkbook libro = abrir(builder.generar(datos()))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertTrue(hoja.getPrintSetup().getLandscape());
            assertEquals(1, hoja.getPrintSetup().getFitWidth());
            assertEquals(0, hoja.getPrintSetup().getFitHeight());
            assertTrue(hoja.getFitToPage());
            assertFalse(hoja.isDisplayGridlines());
        }
    }

    @Test
    void elNombreDelFicheroLlevaNumeroTallerClienteYModelo() {
        assertEquals("Hoja de producción 325 C&C LEATHER - APC F67104.xlsx",
                HojaProduccionExcelBuilder.nombreFichero(datos()));
        DatosHojaProduccion sinNada = new DatosHojaProduccion("AMI", "", "", "", "", "", "Sweet/Bag", 1, null, List.of());
        assertEquals("Hoja de producción 1 Sweet Bag - AMI.xlsx", HojaProduccionExcelBuilder.nombreFichero(sinNada));
    }

    @Test
    void dejaElEjemploEnTargetParaRevisarloAOjo() throws Exception {
        Path destino = Path.of("target", "Hoja de produccion ejemplo.xlsx");
        Files.createDirectories(destino.getParent());
        LineaProduccion segunda = new LineaProduccion(12, "BECERRO GRANULADO", "CAMEL", "", "", "Serafil", "120",
                "3,5", "MADE IN SPAIN", "APC", "NIQUEL", "3CM", "NIQUEL", "SARJA 005 NEGRO",
                "CH 1999 - DILUIR AL 50% ANTES DE APLICAR EN LOS CANTOS");
        DatosHojaProduccion datos = new DatosHojaProduccion("APC", "A.P.C.", "SMS FALL 27", "F67104", "", "LE NEIGE",
                "C&C LEATHER", 325, LocalDate.of(2026, 11, 24), List.of(linea("Badana"), segunda));
        byte[] foto = FotosDePrueba.relleno(800, 600, "F67104", new Color(0xC8, 0x9B, 0x6E));
        Files.write(destino, builder.generar(datos, foto));
        assertTrue(Files.size(destino) > 0);
    }

    private static DatosHojaProduccion datos() {
        return new DatosHojaProduccion("APC", "A.P.C.", "SMS FALL 27", "F67104", "F67104", "LE NEIGE", "C&C LEATHER",
                325, LocalDate.of(2026, 11, 24), List.of(linea("Badana")));
    }

    private static LineaProduccion linea(String pielBase) {
        return new LineaProduccion(3, pielBase, "NOIR", "NOMBRE PIEL COMB.", "COLOR PIEL COMB", "Gütermann", "181",
                "3", "MADE IN MOROCCO", "APC", "ORO", "", "-", "Sarja 005", "CH 1999");
    }

    private static XSSFWorkbook abrir(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static String texto(XSSFSheet hoja, int fila, int col) {
        Cell celda = hoja.getRow(fila).getCell(col);
        return celda == null ? "" : celda.getStringCellValue();
    }
}
