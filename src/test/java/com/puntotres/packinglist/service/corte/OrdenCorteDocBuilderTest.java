package com.puntotres.packinglist.service.corte;

import static com.puntotres.packinglist.testutil.WordDePrueba.altoEmu;
import static com.puntotres.packinglist.testutil.WordDePrueba.anchoEmu;
import static com.puntotres.packinglist.testutil.WordDePrueba.fotosDe;
import static com.puntotres.packinglist.testutil.WordDePrueba.saltosDePagina;
import static com.puntotres.packinglist.testutil.WordDePrueba.texto;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class OrdenCorteDocBuilderTest {

    private static final Imagen APAISADA = imagen(1600, 1200, "ULL729", new Color(120, 90, 70));
    private static final Imagen VERTICAL = imagen(1200, 1600, "ULL027", new Color(60, 60, 70));

    @Test
    void esUnA4ApaisadoConUnaPaginaPorOrden() throws IOException {
        XWPFDocument doc = generar(List.of(
                orden("ULL729.AL0103", "001 BLACK", 169, sinCombinaciones(), APAISADA),
                orden("ULL729.AL0103", "718 VANILLA CREAM", 102, sinCombinaciones(), APAISADA),
                orden("ULL745.AL0103", "001 BLACK", 45, sinCombinaciones(), null)));

        CTPageSz pagina = doc.getDocument().getBody().getSectPr().getPgSz();
        assertEquals(BigInteger.valueOf(16838), pagina.getW());
        assertEquals(BigInteger.valueOf(11906), pagina.getH());
        assertEquals(STPageOrientation.LANDSCAPE, pagina.getOrient());
        assertEquals(6, doc.getTables().size(), "banda y pieles por cada orden");
        assertEquals(2, saltosDePagina(doc));
    }

    @Test
    void llevaLosDatosQueElCortadorTieneQueVer() throws IOException {
        String texto = texto(generar(List.of(orden("ULL729.AL0103", "001 BLACK", 169,
                new PielesArticulo("Box calf", "Cabretilla", List.of("Ante", "Charol")), APAISADA))));

        for (String esperado : List.of("AMI · H26", "ULL729.AL0103", "001 BLACK", "169",
                "Box calf", "COMBINACIÓN 1", "Ante", "COMBINACIÓN 2", "Charol", "FORRO", "Cabretilla")) {
            assertTrue(texto.contains(esperado), "falta '" + esperado + "' en:\n" + texto);
        }
    }

    @Test
    void cadaPielEsUnRenglonConSuRecuadroYSinCombinacionesNiForroSoloSaleLaPiel() throws IOException {
        XWPFDocument doc = generar(List.of(
                orden("A.P1", "001", 1, sinCombinaciones(), null),
                orden("B.P2", "001", 1, new PielesArticulo("X", "F", List.of("C1", "C2")), null)));

        assertEquals(1, doc.getTables().get(1).getRows().size());
        assertEquals(7, doc.getTables().get(3).getRows().size(), "4 pieles y 3 separaciones");
        assertTrue(doc.getTables().get(3).getRow(0).getCell(1).getCTTc().getTcPr().isSetTcBorders(),
                "el recuadro para pegar la muestra");
    }

    @Test
    void laFotoPrincipalEncajaEnSuHuecoSinDeformarseYSinFotoLoDice() throws IOException {
        XWPFDocument doc = generar(List.of(
                orden("A.P1", "001", 1, sinCombinaciones(), VERTICAL),
                orden("B.P1", "001", 1, sinCombinaciones(), null)));

        var fotos = fotosDe(doc.getTables().get(0));
        assertEquals(1, fotos.size());
        long ancho = anchoEmu(fotos.get(0));
        long alto = altoEmu(fotos.get(0));
        assertTrue(ancho <= (long) OrdenCorteDocBuilder.ANCHO_FOTO * WordCorte.EMU_POR_TWIP);
        assertTrue(alto <= (long) OrdenCorteDocBuilder.ALTO_BANDA * WordCorte.EMU_POR_TWIP);
        assertEquals(1200.0 / 1600.0, (double) ancho / alto, 0.01);
        assertTrue(fotosDe(doc.getTables().get(2)).isEmpty());
        assertTrue(texto(doc).contains("Sin foto del modelo"));
    }

    @Test
    void cadaOrdenCabeEnUnaPaginaTengaLasPielesQueTenga() throws IOException {
        for (int combinaciones = 0; combinaciones <= 4; combinaciones++) {
            List<String> nombres = java.util.Collections.nCopies(combinaciones, "Ante");
            XWPFDocument doc = generar(List.of(orden("A.P1", "001", 1,
                    new PielesArticulo("Box", "Tela", nombres), APAISADA)));

            int alto = alturaTabla(doc.getTables().get(0)) + OrdenCorteDocBuilder.SEPARACION
                    + alturaTabla(doc.getTables().get(1)) + 2 * WordCorte.ALTO_SEPARADOR;
            assertTrue(alto <= OrdenCorteDocBuilder.ALTO_UTIL,
                    combinaciones + " combinaciones ocupan " + alto + " de " + OrdenCorteDocBuilder.ALTO_UTIL);
        }
    }

    @Test
    void unNombreDePielLargoSeEscribeMasPequenoParaQueNoSeCorte() throws IOException {
        // Con seis pieles cada renglón mide 1,45 cm: un nombre largo a 20 pt
        // se parte en dos líneas y la segunda queda fuera de la fila exacta.
        XWPFDocument doc = generar(List.of(orden("A.P1", "001", 1,
                new PielesArticulo("Vachette grainée pleine fleur tannage végétal", "", List.of("Ante")),
                null)));

        var pieles = doc.getTables().get(1);
        var largo = pieles.getRow(0).getCell(0).getParagraphs().get(1).getRuns().get(0);
        var corto = pieles.getRow(2).getCell(0).getParagraphs().get(1).getRuns().get(0);
        assertEquals(20.0, corto.getFontSizeAsDouble());
        assertTrue(largo.getFontSizeAsDouble() < 20.0);
    }

    @Test
    void lasTablasNoLlevanMargenDeCeldaArribaNiAbajo() throws IOException {
        // Word suma el margen de celda de arriba y de abajo a una fila de alto
        // "exacto": con 85 twips por lado, una orden con forro se iba a dos
        // páginas (medido abriendo el ejemplo en Word). Ningún cálculo de
        // alturas lo ve; solo se ve en el papel.
        XWPFDocument doc = generar(List.of(orden("A.P1", "001", 1,
                new PielesArticulo("Box", "Tela", List.of("Ante")), APAISADA)));

        for (XWPFTable tabla : doc.getTables()) {
            assertEquals(0, tabla.getCellMarginTop());
            assertEquals(0, tabla.getCellMarginBottom());
        }
    }

    @Test
    void dejaUnEjemploEnTargetParaRevisarAOjo() throws IOException {
        byte[] word = new OrdenCorteDocBuilder().generar(List.of(
                orden("ULL729.AL0103", "001 BLACK", 169,
                        new PielesArticulo("Vachette grainée", "Cabretilla", List.of("Ante")), APAISADA),
                orden("ULL745.AL0103", "001 BLACK", 45, sinCombinaciones(), null),
                orden("ULL027.AL0216", "2221 CHOCOLATE BROWN", 135,
                        new PielesArticulo("Box calf", "", List.of("Ante", "Charol", "Nappa")), VERTICAL)));

        Path destino = Path.of("target", "Ordenes de corte ejemplo.docx");
        Files.createDirectories(destino.getParent());
        Files.write(destino, word);
        assertTrue(Files.size(destino) > 0);
    }

    private static XWPFDocument generar(List<OrdenCorte> ordenes) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(new OrdenCorteDocBuilder().generar(ordenes)));
    }

    private static OrdenCorte orden(String referencia, String color, int bolsos,
                                    PielesArticulo pieles, Imagen foto) {
        return new OrdenCorte("AMI", "H26", referencia, color, bolsos, pieles, foto);
    }

    private static PielesArticulo sinCombinaciones() {
        return new PielesArticulo("Box calf", "", List.of());
    }

    private static int alturaTabla(XWPFTable tabla) {
        return tabla.getRows().stream().mapToInt(XWPFTableRow::getHeight).sum();
    }

    private static Imagen imagen(int ancho, int alto, String texto, Color color) {
        return new Imagen(FotosDePrueba.relleno(ancho, alto, texto, color), ancho, alto);
    }
}
