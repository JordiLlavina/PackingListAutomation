package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaCajaApc;
import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaPaletApc;

/**
 * Herramienta manual (no es un test de nada): deja en
 * target/muestras-etiquetas-apc/ un excel de etiquetas por cada una de las
 * cinco destinaciones de APC, para abrirlos e IMPRIMIRLOS y decidir qué altos
 * de fila, anchos de columna y área de impresión hay que corregir.
 *
 * <p>Cada libro lleva TRES cajas y DOS palets a propósito: los ajustes del
 * área de impresión solo se ven con más de un bloque, que es donde se
 * descubre si la segunda etiqueta del par se parte entre dos hojas o si el
 * área de impresión guardada en la plantilla se queda corta.
 *
 * <p>La tercera caja lleva dos artículos concatenados con " / ", como una
 * caja mixta real, para ver si el texto cabe en la celda.
 *
 * <p>Ejecutar: mvn test -Dtest=ApcEtiquetasMuestrasTool
 */
class ApcEtiquetasMuestrasTool {

    private static final Path DESTINO = Path.of("target", "muestras-etiquetas-apc");

    private final ApcEtiquetasExcelBuilder builder = new ApcEtiquetasExcelBuilder();

    @Test
    void generarLasCincoMuestras() throws IOException {
        Files.createDirectories(DESTINO);
        generar("JAPAN", ApcEtiquetaLayout.JAPAN);
        generar("KOREA", ApcEtiquetaLayout.KOREA);
        generar("D. USA", ApcEtiquetaLayout.USA);
        generar("WHOLESALE", ApcEtiquetaLayout.WH_CROSSLOG);
        generar("RETAIL", ApcEtiquetaLayout.RETAIL);
        System.out.println("Muestras en " + DESTINO.toAbsolutePath());
    }

    private void generar(String nombre, ApcEtiquetaLayout layout) throws IOException {
        List<EtiquetaCajaApc> cajas = List.of(
                new EtiquetaCajaApc("4100128863", "PUN20260928WH1", "PXCBC-F67008",
                        "LZZ-NOIR", "U", "11", "1 / 3", "7,60 Kg"),
                new EtiquetaCajaApc("4100128863", "PUN20260928WH1", "PXCBC-F67008",
                        "LZZ-NOIR", "U", "11", "2 / 3", "7,60 Kg"),
                // Caja mixta: dos artículos concatenados, como en el envío real.
                new EtiquetaCajaApc("4100128863 / 4100128685", "PUN20260928WH1",
                        "PXCBC-F67008 / PXCEI-F67043", "LZZ-NOIR / GAU-CAMEL",
                        "U", "6", "3 / 3", "6,20 Kg"));
        List<EtiquetaPaletApc> palets = List.of(
                new EtiquetaPaletApc(2, "25,20 Kg"),
                new EtiquetaPaletApc(1, "16,20 Kg"));
        byte[] excel = builder.generar(layout, cajas, palets);
        Files.write(DESTINO.resolve("Etiquetas APC " + nombre + ".xlsx"), excel);
    }

    /**
     * Informe de lo que hay HOY en cada muestra: área de impresión, ajuste de
     * página, márgenes y suma de altos de fila de cada bloque. Un A4 vertical
     * son 842 puntos menos los márgenes, así que un bloque que sume más ya se
     * parte solo. Es el punto de partida para decidir qué corregir.
     *
     * <p>Ejecutar: mvn test -Dtest=ApcEtiquetasMuestrasTool#informeDeLaMaquetacionActual
     */
    @Test
    void informeDeLaMaquetacionActual() throws IOException {
        for (Object[] fila : new Object[][] {
                {"JAPAN", ApcEtiquetaLayout.JAPAN}, {"KOREA", ApcEtiquetaLayout.KOREA},
                {"D. USA", ApcEtiquetaLayout.USA}, {"WHOLESALE", ApcEtiquetaLayout.WH_CROSSLOG},
                {"RETAIL", ApcEtiquetaLayout.RETAIL}}) {
            String nombre = (String) fila[0];
            ApcEtiquetaLayout layout = (ApcEtiquetaLayout) fila[1];
            byte[] excel = builder.generar(layout,
                    List.of(new EtiquetaCajaApc("1", "1", "1", "1", "U", "1", "1 / 1", "1 Kg")),
                    List.of(new EtiquetaPaletApc(1, "1 Kg")));
            try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                System.out.println("=== " + nombre + "  " + layout.rutaPlantilla());
                for (int i = 0; i < libro.getNumberOfSheets(); i++) {
                    informarHoja(libro, i,
                            i == 0 ? layout.alturaBloque() : layout.palet().altura(),
                            i == 0 ? layout.offsetSegundaEtiqueta() : -1);
                }
            }
        }
    }

    private static void informarHoja(XSSFWorkbook libro, int indice, int altura, int offsetPar) {
        XSSFSheet hoja = libro.getSheetAt(indice);
        float suma = 0;
        StringBuilder altos = new StringBuilder();
        for (int f = 0; f < altura; f++) {
            float alto = hoja.getRow(f) != null
                    ? hoja.getRow(f).getHeightInPoints()
                    : hoja.getDefaultRowHeightInPoints();
            suma += alto;
            altos.append(f + 1).append('=').append(alto).append("  ");
        }
        System.out.println("  hoja '" + libro.getSheetName(indice) + "': bloque de " + altura
                + " filas" + (offsetPar > 0 ? " (2a etiqueta del par en la fila " + (offsetPar + 1) + ")" : "")
                + ", suma de altos " + Math.round(suma) + " pt");
        System.out.println("    area de impresion: " + libro.getPrintArea(indice));
        System.out.println("    pageSetup: paper=" + hoja.getPrintSetup().getPaperSize()
                + " landscape=" + hoja.getPrintSetup().getLandscape()
                + " scale=" + hoja.getPrintSetup().getScale()
                + " fitToPage=" + hoja.getFitToPage()
                + " fitW=" + hoja.getPrintSetup().getFitWidth()
                + " fitH=" + hoja.getPrintSetup().getFitHeight());
        System.out.println("    margenes (pulgadas): sup=" + hoja.getMargin(Sheet.TopMargin)
                + " inf=" + hoja.getMargin(Sheet.BottomMargin)
                + " izq=" + hoja.getMargin(Sheet.LeftMargin)
                + " der=" + hoja.getMargin(Sheet.RightMargin));
        System.out.println("    anchos A-G: " + anchos(hoja));
        System.out.println("    altos de fila: " + altos);
    }

    private static String anchos(XSSFSheet hoja) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < 7; c++) {
            sb.append((char) ('A' + c)).append('=')
                    .append(Math.round(hoja.getColumnWidth(c) / 25.6) / 10.0).append("  ");
        }
        return sb.toString();
    }
}
