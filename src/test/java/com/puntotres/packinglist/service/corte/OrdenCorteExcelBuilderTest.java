package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.testutil.ExcelDePrueba;
import com.puntotres.packinglist.testutil.FotosDePrueba;

class OrdenCorteExcelBuilderTest {

    private static final Imagen APAISADA = imagen(1600, 1200, "ULL729", new Color(120, 90, 70));
    private static final Imagen VERTICAL = imagen(1200, 1600, "ULL027", new Color(60, 60, 70));

    @Test
    void unaHojaPorFichaConLaReferenciaEnteraYElColorEnA4ApaisadoAEscalaFija() throws IOException {
        XSSFWorkbook libro = generar(List.of(
                orden("ULL729.AL0103", "001 BLACK", 169, sinCombinaciones(), APAISADA),
                orden("ULL729.AL0103", "718 VANILLA CREAM", 102, sinCombinaciones(), APAISADA),
                orden("PXCBC-F67008", "LZZ", 45, sinCombinaciones(), null)));

        // Excel corta a 31 caracteres: se pierde el final de la descripción, pero la
        // referencia y el código de color, que son los que la hacen única, caben siempre.
        assertEquals(List.of("ULL729.AL0103 - 001 BLACK", "ULL729.AL0103 - 718 VANILLA CRE",
                        "PXCBC-F67008 - LZZ"),
                ExcelDePrueba.nombresDeHoja(libro), "sin descripción, el color es solo el código");
        for (int indice = 0; indice < libro.getNumberOfSheets(); indice++) {
            XSSFSheet hoja = libro.getSheetAt(indice);
            PrintSetup impresion = hoja.getPrintSetup();
            assertTrue(impresion.getLandscape());
            assertEquals(PrintSetup.A4_PAPERSIZE, impresion.getPaperSize());
            assertEquals(OrdenCorteExcelBuilder.ESCALA_IMPRESION, impresion.getScale());
            // Escala fija y no "ajustar a una página": ver ESCALA_IMPRESION.
            assertFalse(hoja.getFitToPage());
            assertEquals(0, hoja.getRowBreaks().length, "una ficha por hoja: ningún salto");
        }
    }

    @Test
    void elMismoModeloYColorEnDosPielesVaEnDosHojasQueSeDistinguenPorLaPiel() throws IOException {
        XSSFWorkbook libro = generar(List.of(
                orden("ULL027.AL0103", "001 BLACK", 10, new PielesArticulo("Box calf", "", List.of()), VERTICAL),
                orden("ULL027.AL0216", "001 BLACK", 5, new PielesArticulo("Nappa", "", List.of()), VERTICAL),
                orden("PXCBC-F67008", "LAW", 3, sinCombinaciones(), null),
                orden("PXCDS-F67008", "LAW", 3, sinCombinaciones(), null)));

        assertEquals(List.of("ULL027.AL0103 - 001 BLACK", "ULL027.AL0216 - 001 BLACK",
                "PXCBC-F67008 - LAW", "PXCDS-F67008 - LAW"), ExcelDePrueba.nombresDeHoja(libro));
        assertTrue(ExcelDePrueba.texto(libro.getSheetAt(0)).contains("Box calf"));
        assertTrue(ExcelDePrueba.texto(libro.getSheetAt(1)).contains("Nappa"));
        // El título de la ficha sigue siendo el modelo a secas.
        assertEquals("ULL027", texto(libro.getSheetAt(1).getRow(1), 1));
        assertEquals("F67008", texto(libro.getSheetAt(2).getRow(1), 1));
    }

    @Test
    void llevaLosDatosQueElCortadorTieneQueVerConElColorDeCadaPiel() throws IOException {
        PielesArticulo pieles = new PielesArticulo(new PielCorte("Box calf", "NOIR"),
                new PielCorte("Cabretilla", "NEGRO"),
                List.of(new PielCorte("Ante", "MARRON"), new PielCorte("Charol", "")));
        String texto = ExcelDePrueba.texto(generar(List.of(
                orden("PXCBC-F67008", "LZZ NOIR", 169, pieles, APAISADA))));

        for (String esperado : List.of("AMI | Temporada H26", "F67008", "Color  LZZ NOIR", "Bolsos  169",
                "PIEL\nBox calf", "COLOR\nNOIR", "COMBINACIÓN 1\nAnte", "COLOR\nMARRON",
                "COMBINACIÓN 2\nCharol", "FORRO\nCabretilla", "COLOR\nNEGRO")) {
            assertTrue(texto.contains(esperado), "falta '" + esperado + "' en:\n" + texto);
        }
    }

    @Test
    void cadaPielEsUnRenglonConSuColorYSuRecuadroYSinCombinacionesNiForroSoloSaleLaPiel()
            throws IOException {
        XSSFWorkbook libro = generar(List.of(
                orden("A", "001", 1, sinCombinaciones(), null),
                orden("B", "001", 1, new PielesArticulo("X", "F", List.of("C1", "C2")), null)));

        assertEquals(1, renglonesDePiel(libro.getSheetAt(0)).size());
        List<XSSFRow> renglones = renglonesDePiel(libro.getSheetAt(1));
        assertEquals(4, renglones.size(), "piel, dos combinaciones y forro");
        XSSFCell recuadro = renglones.get(0).getCell(2);
        assertEquals(BorderStyle.THIN, recuadro.getCellStyle().getBorderTop());
        assertEquals(BorderStyle.THIN, recuadro.getCellStyle().getBorderLeft());
        assertEquals("COLOR", renglones.get(0).getCell(1).getStringCellValue(),
                "sin color tecleado sale el rótulo solo");
    }

    @Test
    void elRotuloVaA14EnGrisYElNombreEnNegritaALaDerecha() throws IOException {
        XSSFWorkbook libro = generar(List.of(orden("A", "001", 1,
                new PielesArticulo(new PielCorte("Vachette grainée", "NOIR"), PielCorte.NINGUNA, List.of()),
                null)));

        XSSFCell nombre = renglonesDePiel(libro.getSheetAt(0)).get(0).getCell(0);
        var rico = nombre.getRichStringCellValue();
        XSSFFont rotulo = rico.getFontAtIndex(0);
        XSSFFont valor = rico.getFontAtIndex("PIEL\n".length());
        assertEquals(14, rotulo.getFontHeightInPoints());
        assertFalse(rotulo.getBold());
        assertEquals(28, valor.getFontHeightInPoints());
        assertTrue(valor.getBold());
        assertEquals(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT,
                nombre.getCellStyle().getAlignment());
        assertTrue(nombre.getCellStyle().getWrapText(), "sin ajustar texto el salto de línea no se ve");
    }

    @Test
    void laFotoEncajaEnSuHuecoSinDeformarseYSinFotoNoHayNinguna() throws IOException {
        XSSFWorkbook libro = generar(List.of(
                orden("A", "001", 1, sinCombinaciones(), VERTICAL),
                orden("B", "001", 1, sinCombinaciones(), null)));

        List<XSSFPicture> fotos = ExcelDePrueba.fotosDe(libro.getSheetAt(0));
        assertEquals(1, fotos.size());
        XSSFClientAnchor ancla = fotos.get(0).getClientAnchor();
        assertEquals(0, ancla.getCol1());
        assertEquals(0, ancla.getCol2(), "la foto no se sale de la primera columna");
        double ancho = (double) (ancla.getDx2() - ancla.getDx1()) / Units.EMU_PER_POINT;
        double alto = altoEnPuntos(libro.getSheetAt(0), ancla);
        assertEquals(1200.0 / 1600.0, ancho / alto, 0.01);
        assertTrue(alto <= OrdenCorteExcelBuilder.ALTO_BANDA, "alto " + alto);
        assertTrue(ExcelDePrueba.fotosDe(libro.getSheetAt(1)).isEmpty());
    }

    @Test
    void unaFotoQueSaleEnVariasFichasSeGuardaUnaSolaVez() throws IOException {
        XSSFWorkbook libro = generar(List.of(
                orden("A", "001", 1, sinCombinaciones(), APAISADA),
                orden("A", "002", 1, sinCombinaciones(), APAISADA)));

        assertEquals(1, libro.getAllPictures().size());
        assertEquals(1, ExcelDePrueba.fotosDe(libro.getSheetAt(1)).size());
    }

    @Test
    void cadaFichaCabeEnUnaPaginaTengaLasPielesQueTenga() throws IOException {
        for (int combinaciones = 0; combinaciones <= 4; combinaciones++) {
            List<String> nombres = Collections.nCopies(combinaciones, "Ante");
            XSSFSheet hoja = generar(List.of(orden("A", "001", 1,
                    new PielesArticulo("Box", "Tela", nombres), APAISADA))).getSheetAt(0);

            double alto = 0;
            for (int fila = 0; fila <= hoja.getLastRowNum(); fila++) {
                alto += hoja.getRow(fila).getHeightInPoints();
            }
            assertTrue(alto <= OrdenCorteExcelBuilder.ALTO_UTIL,
                    combinaciones + " combinaciones ocupan " + alto + " de " + OrdenCorteExcelBuilder.ALTO_UTIL);
        }
    }

    @Test
    void elNombreYElColorEncogenLoJustoParaCaberEnSuRenglon() {
        // Con seis pieles cada renglón mide 1,45 cm: a 28 pt ni siquiera cabe
        // una línea debajo del rótulo, y lo que no cabe en una fila de alto
        // fijo no se ve. Se comprueba además abriendo el ejemplo en Excel.
        String largo = "Vachette grainée pleine fleur tannage végétal";
        double anchoNombre = OrdenCorteExcelBuilder.ANCHO_NOMBRE_CM * 72 / 2.54;
        double anchoColor = OrdenCorteExcelBuilder.ANCHO_COLOR_CM * 72 / 2.54;
        for (int pieles = 1; pieles <= 6; pieles++) {
            float alto = OrdenCorteExcelBuilder.altoPiel(pieles);
            for (String nombre : List.of("Box", "Vachette grainée", largo)) {
                int tamano = OrdenCorteExcelBuilder.tamanoNombre(nombre, alto);
                assertTrue(OrdenCorteExcelBuilder.altoNecesario(nombre, tamano, anchoNombre) <= alto,
                        nombre + " a " + tamano + " pt no cabe con " + pieles + " pieles");
            }
            for (String color : List.of("NOIR", "CHOCOLATE BROWN", "SAND/CHOCOLATE BROWN")) {
                int tamano = OrdenCorteExcelBuilder.tamanoColor(color, alto);
                assertTrue(OrdenCorteExcelBuilder.altoNecesario(color, tamano, anchoColor) <= alto,
                        color + " a " + tamano + " pt no cabe con " + pieles + " pieles");
            }
        }
        assertEquals(28, OrdenCorteExcelBuilder.tamanoNombre("Box", OrdenCorteExcelBuilder.altoPiel(3)));
        assertTrue(OrdenCorteExcelBuilder.tamanoNombre("Box", OrdenCorteExcelBuilder.altoPiel(6)) < 28);
        assertTrue(OrdenCorteExcelBuilder.tamanoNombre(largo, OrdenCorteExcelBuilder.altoPiel(3))
                < OrdenCorteExcelBuilder.tamanoNombre("Box", OrdenCorteExcelBuilder.altoPiel(3)));
    }

    @Test
    void losNombresDeHojaSonLosQueAdmiteExcel() {
        Set<String> usados = new LinkedHashSet<>();

        // Los colores de AMI traen barras, que Excel no admite en un nombre de hoja.
        assertEquals("ULL754 - 0014 NOIR-ARGENT VIBRE",
                OrdenCorteExcelBuilder.nombreUnico("ULL754 - 0014 NOIR/ARGENT VIBRE", usados));
        String largo = OrdenCorteExcelBuilder.nombreUnico("ULL754 - 225 CHOCOLATE/LAITON VIBRE", usados);
        assertEquals(31, largo.length(), largo);
        assertTrue(largo.startsWith("ULL754 - 225 CHOCOLATE-LAITON"), largo);
        // Dos colores que, recortados a 31, se llamarían igual.
        String otro = OrdenCorteExcelBuilder.nombreUnico("ULL754 - 225 CHOCOLATE/LAITON VIBRE MATE", usados);
        assertTrue(otro.endsWith(" (2)") && otro.length() <= 31, otro);
        assertEquals("A - B (2)", OrdenCorteExcelBuilder.nombreUnico("A - B", new LinkedHashSet<>(Set.of("a - b"))),
                "Excel no distingue mayúsculas en los nombres de hoja");
    }

    @Test
    void dejaUnEjemploEnTargetParaRevisarAOjo() throws IOException {
        byte[] excel = new OrdenCorteExcelBuilder().generar(List.of(
                orden("ULL729.AL0103", "001 BLACK", 169, new PielesArticulo(new PielCorte("Vachette grainée", "BLACK"),
                        new PielCorte("Cabretilla", "NOIR"), List.of(new PielCorte("Ante", "BLACK"))), APAISADA),
                orden("ULL729.AL0216", "001 BLACK", 20, new PielesArticulo(new PielCorte("Box calf", "BLACK"),
                        PielCorte.NINGUNA, List.of()), APAISADA),
                orden("ULL745.AL0103", "718 VANILLA CREAM", 45, new PielesArticulo(
                        new PielCorte("Box calf", "VANILLA CREAM"), PielCorte.NINGUNA, List.of()), null),
                orden("ULL027.AL0206", "2221 CHOCOLATE BROWN", 135, new PielesArticulo(
                        new PielCorte("Box calf", "CHOCOLATE BROWN"), PielCorte.NINGUNA,
                        List.of(new PielCorte("Ante", "MARRON"), new PielCorte("Charol", "NEGRO"),
                                new PielCorte("Nappa", ""))), VERTICAL),
                // El peor caso: seis pieles, nombres largos y un color que no cabe en el nombre de hoja.
                orden("ULL754.AL0219", "225 CHOCOLATE/LAITON VIBRE", 12, new PielesArticulo(
                        new PielCorte("Vachette grainée pleine fleur tannage végétal", "CHOCOLATE/LAITON"),
                        new PielCorte("Cabretilla doublure", "SAND/CHOCOLATE BROWN"),
                        List.of(new PielCorte("Ante velours", "CHOCOLATE BROWN"), new PielCorte("Charol", "NOIR"),
                                new PielCorte("Nappa agneau", "MASTIC BEIGE"), new PielCorte("Box calf", "TRUFFLE"))),
                        APAISADA)));

        Path destino = Path.of("target", "Ordenes de corte ejemplo.xlsx");
        Files.createDirectories(destino.getParent());
        Files.write(destino, excel);
        assertTrue(Files.size(destino) > 0);
    }

    private static XSSFWorkbook generar(List<OrdenCorte> ordenes) throws IOException {
        return ExcelDePrueba.abrir(new OrdenCorteExcelBuilder().generar(ordenes));
    }

    /** Las filas de piel: las que llevan el recuadro de la muestra en la tercera columna. */
    private static List<XSSFRow> renglonesDePiel(XSSFSheet hoja) {
        List<XSSFRow> renglones = new java.util.ArrayList<>();
        for (int fila = 0; fila <= hoja.getLastRowNum(); fila++) {
            XSSFRow suya = hoja.getRow(fila);
            if (suya != null && suya.getCell(2) != null
                    && suya.getCell(2).getCellStyle().getBorderTop() == BorderStyle.THIN) {
                renglones.add(suya);
            }
        }
        return renglones;
    }

    private static String texto(XSSFRow fila, int columna) {
        return fila == null || fila.getCell(columna) == null ? "" : fila.getCell(columna).getStringCellValue();
    }

    private static double altoEnPuntos(XSSFSheet hoja, XSSFClientAnchor ancla) {
        double alto = 0;
        for (int fila = ancla.getRow1(); fila < ancla.getRow2(); fila++) {
            alto += hoja.getRow(fila).getHeightInPoints();
        }
        return alto + (double) (ancla.getDy2() - ancla.getDy1()) / Units.EMU_PER_POINT;
    }

    /** La referencia como la escribe el cliente: con guion es de APC (piel delante), si no de AMI. */
    private static OrdenCorte orden(String referencia, String color, int bolsos,
                                    PielesArticulo pieles, Imagen foto) {
        ReferenciaCorte partida = referencia.contains("-") ? ReferenciaCorte.deApc(referencia)
                : ReferenciaCorte.deAmi(referencia);
        return new OrdenCorte("AMI", "H26", partida.referencia(), partida.modelo(), color, bolsos, pieles, foto);
    }

    private static PielesArticulo sinCombinaciones() {
        return new PielesArticulo("Box calf", "", List.of());
    }

    private static Imagen imagen(int ancho, int alto, String texto, Color color) {
        return new Imagen(FotosDePrueba.relleno(ancho, alto, texto, color), ancho, alto);
    }
}
