package com.puntotres.packinglist.service.corte;

import static com.puntotres.packinglist.testutil.WordDePrueba.altoEmu;
import static com.puntotres.packinglist.testutil.WordDePrueba.anchoEmu;
import static com.puntotres.packinglist.testutil.WordDePrueba.fotosDe;
import static com.puntotres.packinglist.testutil.WordDePrueba.saltosDePagina;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class FotosCorteDocBuilderTest {

    @Test
    void seisFotosPorA4EnDosColumnasYTresFilas() throws IOException {
        XWPFDocument doc = generar(fotos(7));

        CTPageSz pagina = doc.getDocument().getBody().getSectPr().getPgSz();
        assertEquals(BigInteger.valueOf(11906), pagina.getW());
        assertEquals(BigInteger.valueOf(16838), pagina.getH());
        assertNull(pagina.getOrient(), "vertical");
        assertEquals(2, doc.getTables().size(), "7 fotos son dos hojas");
        assertEquals(3, doc.getTables().get(0).getRows().size());
        assertEquals(2, doc.getTables().get(0).getRow(0).getTableCells().size());
        assertEquals(1, doc.getTables().get(1).getRows().size());
        assertEquals(6, fotosDe(doc.getTables().get(0)).size());
        assertEquals(1, fotosDe(doc.getTables().get(1)).size());
        assertEquals(1, saltosDePagina(doc));
    }

    @Test
    void laRejillaLlenaLaPaginaYCadaFotoCabeEnSuCelda() throws IOException {
        XWPFDocument doc = generar(fotos(6));

        XWPFTable rejilla = doc.getTables().get(0);
        int alto = rejilla.getRows().stream().mapToInt(fila -> fila.getHeight()).sum();
        alto += FotosCorteDocBuilder.ALTO_TITULO;
        assertTrue(alto <= FotosCorteDocBuilder.ALTO_UTIL);
        assertTrue(alto >= FotosCorteDocBuilder.ALTO_UTIL - 200, "llena la hoja, sin franja vacía abajo");
        for (XWPFPicture foto : fotosDe(rejilla)) {
            assertTrue(anchoEmu(foto) <= (long) FotosCorteDocBuilder.ANCHO_CELDA * WordCorte.EMU_POR_TWIP);
            assertTrue(altoEmu(foto) <= (long) FotosCorteDocBuilder.ALTO_CELDA * WordCorte.EMU_POR_TWIP);
        }
    }

    @Test
    void laRejillaNoLlevaMargenDeCeldaArribaNiAbajo() throws IOException {
        // Word suma el margen de arriba y de abajo a las filas de alto exacto
        // (medido con la orden de corte): tres filas con 0,1 cm por lado
        // empujarían la última a otra hoja.
        XWPFTable rejilla = generar(fotos(6)).getTables().get(0);

        assertEquals(0, rejilla.getCellMarginTop());
        assertEquals(0, rejilla.getCellMarginBottom());
    }

    @Test
    void cadaHojaEmpiezaPorElModeloEnTitulo1NegritaCentradoYNegro() throws IOException {
        // En el cuerpo y no en la cabecera de página: Word pinta la cabecera en
        // gris mientras se edita el documento, y el usuario la quiere a todo color.
        XWPFDocument doc = generar(fotos(7));

        assertTrue(doc.getHeaderList().isEmpty());
        List<XWPFParagraph> titulos = doc.getParagraphs().stream()
                .filter(parrafo -> "Heading1".equals(parrafo.getStyleID())).toList();
        assertEquals(2, titulos.size(), "uno por hoja");
        XWPFParagraph titulo = titulos.get(0);
        assertEquals(titulo, doc.getParagraphs().get(0), "lo primero de la hoja");
        assertEquals("ULL729", titulo.getText(), "ni temporada ni pieles");
        assertEquals("heading 1", doc.getStyles().getStyle("Heading1").getName(),
                "el estilo integrado de Word, que en español se ve como Título 1");
        assertEquals(ParagraphAlignment.CENTER, titulo.getAlignment());
        var texto = titulo.getRuns().get(0);
        assertEquals(22.0, texto.getFontSizeAsDouble());
        assertTrue(texto.isBold());
        assertEquals("000000", texto.getColor());
    }

    @Test
    void conElNombreDelBolsoElTituloEsModeloGuionNombre() {
        assertEquals("F67043 - Sac Le Neige", new FotosCorte("F67043", "Sac Le Neige", List.of()).titulo());
        assertEquals("F67043", new FotosCorte("F67043", "", List.of()).titulo());
    }

    @Test
    void dejaUnEjemploEnTargetParaRevisarAOjo() throws IOException {
        byte[] word = new FotosCorteDocBuilder().generar(new FotosCorte("ULL729", "", fotos(8)));

        Path destino = Path.of("target", "Fotos ejemplo.docx");
        Files.createDirectories(destino.getParent());
        Files.write(destino, word);
        assertTrue(Files.size(destino) > 0);
    }

    private static XWPFDocument generar(List<Imagen> fotos) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(new FotosCorteDocBuilder()
                .generar(new FotosCorte("ULL729", "", fotos))));
    }

    /** Alterna apaisadas y verticales, para ver cómo encaja cada una. */
    private static List<Imagen> fotos(int cuantas) {
        List<Imagen> fotos = new ArrayList<>();
        for (int i = 1; i <= cuantas; i++) {
            boolean apaisada = i % 2 == 1;
            int ancho = apaisada ? 1600 : 1200;
            int alto = apaisada ? 1200 : 1600;
            fotos.add(new Imagen(FotosDePrueba.relleno(ancho, alto, "FOTO " + i,
                    new Color(70 + 15 * i, 90, 110)), ancho, alto));
        }
        return fotos;
    }
}
