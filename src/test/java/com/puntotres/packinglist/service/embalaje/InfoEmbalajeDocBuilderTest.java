package com.puntotres.packinglist.service.embalaje;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.DatosHojaEmbalaje;
import com.puntotres.packinglist.model.DatosHojaEmbalaje.DestinacionHoja;

/**
 * La hoja de embalaje se reabre con POI y se lee su contenido de verdad, como
 * hacen los tests de los demás builders de Word.
 */
class InfoEmbalajeDocBuilderTest {

    private final InfoEmbalajeDocBuilder builder = new InfoEmbalajeDocBuilder();

    /** Los valores de la hoja de Tánger que el usuario nos pasó en foto. */
    private static DatosHojaEmbalaje datosEjemplo() {
        return new DatosHojaEmbalaje("APC", "A.P.C.", "Spring 27", "TÁNGER", 270, "12662",
                LocalDate.of(2026, 11, 2), "PXCBC-F67008", "LZZ", "NOIR",
                "SAC LE NEIGE ALPACA NOIR", "F67008", "60x40x40", 10,
                "BOLSA DE TELA NEGRA", "PARIS", "GRABADO LOGO NIKEL",
                List.of(new DestinacionHoja("JAPAN", "4100132915", 40),
                        new DestinacionHoja("DOUANES USA", "4100132811", 243),
                        new DestinacionHoja("WHOLESALE", "4100132847", 110),
                        new DestinacionHoja("RETAIL", "4100132835", 127)),
                "CAJA 1 A 12: 10 UDS\nCAJA 13: 8 UDS\nCAJAS 14 A 20: 10 UDS\nCAJA 21: 3 UDS\nCAJA 22: 7 UDS",
                "SACAR LOS BOLSOS DEL SOBRE CON CUIDADO\nNO MEZCLAR COLORES",
                "https://youtu.be/guia");
    }

    private static DatosHojaEmbalaje con(DatosHojaEmbalaje d, String etiquetaInterior,
                                        String instruccion, String reparto, String comentarios,
                                        String enlace) {
        return new DatosHojaEmbalaje(d.cliente(), d.nombreCliente(), d.temporada(), d.taller(),
                d.numeroEnvio(), d.nota(), d.fecha(), d.referencia(), d.codigoColor(),
                d.descripcionColor(), d.nombreBolso(), d.codigoModelo(), d.medidaCaja(),
                d.unidadesPorCaja(), d.bolsaTela(), etiquetaInterior, instruccion,
                d.destinaciones(), reparto, comentarios, enlace);
    }

    /**
     * Todo el texto del documento: párrafos del cuerpo y celdas de tablas,
     * también las tablas que van dentro de una celda (las fichas del envío y
     * el embalaje).
     */
    private static String texto(XWPFDocument doc) {
        StringBuilder texto = new StringBuilder();
        for (XWPFParagraph parrafo : doc.getParagraphs()) {
            texto.append(parrafo.getText()).append('\n');
        }
        for (XWPFTable tabla : doc.getTables()) {
            for (XWPFTableRow fila : tabla.getRows()) {
                for (XWPFTableCell celda : fila.getTableCells()) {
                    texto.append(celda.getTextRecursively()).append('\n');
                }
            }
        }
        return texto.toString();
    }

    /** Cada párrafo del documento, entrando en las tablas y en las tablas de dentro de las celdas. */
    private static List<XWPFParagraph> parrafos(XWPFDocument doc) {
        List<XWPFParagraph> parrafos = new ArrayList<>(doc.getParagraphs());
        for (XWPFTable tabla : doc.getTables()) {
            parrafosDe(tabla, parrafos);
        }
        return parrafos;
    }

    private static void parrafosDe(XWPFTable tabla, List<XWPFParagraph> parrafos) {
        for (XWPFTableRow fila : tabla.getRows()) {
            for (XWPFTableCell celda : fila.getTableCells()) {
                parrafos.addAll(celda.getParagraphs());
                for (XWPFTable dentro : celda.getTables()) {
                    parrafosDe(dentro, parrafos);
                }
            }
        }
    }

    /** La tabla de destinaciones: la de tres columnas que empieza por «Destinación». */
    private static XWPFTable tablaDestinaciones(XWPFDocument doc) {
        return doc.getTables().stream()
                .filter(t -> t.getRow(0).getTableCells().size() == 3
                        && t.getRow(0).getCell(0).getText().trim().equals("Destinación"))
                .findFirst().orElseThrow();
    }

    /** La fila de la cabecera: foto | artículo | QR, la primera tabla del documento. */
    private static XWPFTableRow cabecera(XWPFDocument doc) {
        return doc.getTables().get(0).getRow(0);
    }

    /** Las imágenes de una celda, también las de las tablas que lleva dentro (el marco del QR). */
    private static List<XWPFPicture> imagenesEn(XWPFTableCell celda) {
        List<XWPFPicture> imagenes = new ArrayList<>();
        for (XWPFParagraph parrafo : celda.getParagraphs()) {
            for (XWPFRun run : parrafo.getRuns()) {
                imagenes.addAll(run.getEmbeddedPictures());
            }
        }
        for (XWPFTable tabla : celda.getTables()) {
            for (XWPFTableRow fila : tabla.getRows()) {
                for (XWPFTableCell dentro : fila.getTableCells()) {
                    imagenes.addAll(imagenesEn(dentro));
                }
            }
        }
        return imagenes;
    }

    private XWPFDocument abrir(DatosHojaEmbalaje datos, byte[] foto) throws Exception {
        return new XWPFDocument(new ByteArrayInputStream(builder.generar(datos, foto)));
    }

    private static byte[] jpeg(int ancho, int alto) throws Exception {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(new Color(0xDDDDDD));
        g.fillRect(0, 0, ancho, alto);
        g.setColor(new Color(0x555555));
        g.fillRect(ancho / 5, alto / 4, ancho * 3 / 5, alto / 2);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpg", salida);
        return salida.toByteArray();
    }

    @Test
    void laHojaLlevaTodosLosDatos() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            String texto = texto(doc);
            for (String esperado : new String[] {
                    "A.P.C. · Spring 27 · TÁNGER",
                    "Nº ENVÍO", "270", "NOTA ICSUITE", "12662",
                    "PXCBC-F67008", "LZZ NOIR", "SAC LE NEIGE ALPACA NOIR",
                    "60x40x40", "BOLSA DE TELA NEGRA", "GRABADO LOGO NIKEL",
                    "JAPAN", "4100132915", "DOUANES USA", "4100132811",
                    "WHOLESALE", "4100132847", "RETAIL", "4100132835",
                    "TOTAL 520 UNIDADES",
                    "CAJA 1 A 12: 10 UDS", "CAJA 13: 8 UDS",
                    "CAJAS 14 A 20: 10 UDS", "CAJA 21: 3 UDS", "CAJA 22: 7 UDS",
                    "SACAR LOS BOLSOS DEL SOBRE CON CUIDADO",
                    "NO MEZCLAR COLORES"}) {
                assertTrue(texto.contains(esperado), "falta en la hoja: " + esperado);
            }
        }
    }

    @Test
    void laTablaDeDestinacionesLlevaCabeceraFilasYTotal() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            XWPFTable tabla = tablaDestinaciones(doc);
            assertEquals(6, tabla.getRows().size(), "cabecera + 4 destinaciones + total");
            assertEquals("Nº pedido", tabla.getRow(0).getCell(1).getText().trim());
            assertEquals("Cantidad", tabla.getRow(0).getCell(2).getText().trim());
            assertEquals("243", tabla.getRow(2).getCell(2).getText().trim());
            assertEquals("Total", tabla.getRow(5).getCell(0).getText().trim());
            assertEquals("520", tabla.getRow(5).getCell(2).getText().trim());
        }
    }

    @Test
    void sinDestinacionesElTotalEsCero() throws Exception {
        DatosHojaEmbalaje d = datosEjemplo();
        DatosHojaEmbalaje sin = new DatosHojaEmbalaje(d.cliente(), d.nombreCliente(), d.temporada(),
                d.taller(), d.numeroEnvio(), d.nota(), d.fecha(), d.referencia(), d.codigoColor(),
                d.descripcionColor(), d.nombreBolso(), d.codigoModelo(), d.medidaCaja(),
                d.unidadesPorCaja(), d.bolsaTela(), d.etiquetaInterior(), d.instruccionEspecial(),
                List.of(), d.repartoCajas(), d.comentarios(), d.enlaceVideo());
        try (XWPFDocument doc = abrir(sin, null)) {
            XWPFTable tabla = tablaDestinaciones(doc);
            XWPFTableRow ultima = tabla.getRow(tabla.getRows().size() - 1);
            assertEquals("Total", ultima.getCell(0).getText().trim());
            assertEquals("0", ultima.getCell(2).getText().trim());
            assertTrue(texto(doc).contains("TOTAL 0 UNIDADES"));
        }
    }

    @Test
    void elQrSoloSaleConEnlace() throws Exception {
        try (XWPFDocument conEnlace = abrir(datosEjemplo(), null);
             XWPFDocument sinEnlace = abrir(con(datosEjemplo(), "PARIS", "X", "R", "C", ""), null)) {
            assertEquals(1, conEnlace.getAllPictures().size());
            assertEquals("png", conEnlace.getAllPictures().get(0).suggestFileExtension());
            // En versalitas: POI devuelve el texto en mayúsculas, aunque en el XML esté tal cual.
            assertTrue(texto(conEnlace).toLowerCase().contains("guide d'emballage du sac"));
            assertEquals(0, sinEnlace.getAllPictures().size());
            assertFalse(texto(sinEnlace).toLowerCase().contains("guide d'emballage du sac"));
        }
    }

    @Test
    void unEnlaceEnBlancoNoPintaQr() throws Exception {
        try (XWPFDocument doc = abrir(con(datosEjemplo(), "PARIS", "X", "R", "C", "   "), null)) {
            assertEquals(0, doc.getAllPictures().size());
            assertFalse(texto(doc).toLowerCase().contains("guide d'emballage du sac"));
        }
    }

    @Test
    void lasSeccionesVaciasNoSalen() throws Exception {
        try (XWPFDocument doc = abrir(con(datosEjemplo(), "PARIS", "", "", "", ""), null)) {
            String texto = texto(doc);
            assertFalse(texto.contains("SACAR LOS BOLSOS"));
            assertFalse(texto.contains("Instrucción"));
            assertFalse(texto.contains("CAJA 1 A 12"));
            assertEquals(3, doc.getTables().size(), "cabecera, embalaje y destinaciones; sin recuadro de comentarios");
        }
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            assertEquals(4, doc.getTables().size(), "con comentarios, su recuadro es la cuarta tabla");
        }
    }

    @Test
    void noLlevaTitulosDeSeccion() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            List<String> sueltos = parrafos(doc).stream().map(p -> p.getText().trim()).toList();
            String texto = texto(doc);
            assertFalse(texto.contains("INSTRUCCIONES DE EMBALAJE"));
            assertFalse(sueltos.contains("EMBALAJE"), "sin título «EMBALAJE»");
            assertFalse(texto.contains("DESTINACIONES"));
            assertFalse(texto.contains("REPARTO EN CAJAS"));
        }
    }

    @Test
    void noLlevaTituloDeComentarios() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            List<String> sueltos = parrafos(doc).stream().map(p -> p.getText().trim()).toList();
            assertFalse(sueltos.contains("COMENTARIOS"), "el recuadro de comentarios va sin título");
            assertFalse(texto(doc).contains("COMENTARIOS"));
            assertTrue(texto(doc).contains("SACAR LOS BOLSOS DEL SOBRE CON CUIDADO"), "el comentario sigue");
        }
    }

    @Test
    void noLlevaFecha() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), jpeg(40, 30))) {
            String texto = texto(doc);
            assertFalse(texto.contains("FECHA"));
            assertFalse(Pattern.compile("\\d{2}/\\d{2}/\\d{4}").matcher(texto).find(), "ninguna fecha impresa");
        }
    }

    @Test
    void elTituloEsDeDoceSinNegrita() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            XWPFParagraph titulo = cabecera(doc).getCell(1).getParagraphs().get(0);
            assertEquals("A.P.C. · Spring 27 · TÁNGER", titulo.getText());
            assertFalse(titulo.getRuns().isEmpty());
            for (XWPFRun run : titulo.getRuns()) {
                assertEquals(12.0, run.getFontSizeAsDouble(), 0.01);
                assertFalse(run.isBold());
                assertEquals("404040", run.getColor());
            }
        }
    }

    @Test
    void elTituloSaltaLoQueFalta() {
        DatosHojaEmbalaje d = datosEjemplo();
        DatosHojaEmbalaje sinTaller = new DatosHojaEmbalaje(d.cliente(), "", d.temporada(), "  ",
                d.numeroEnvio(), d.nota(), d.fecha(), d.referencia(), d.codigoColor(),
                d.descripcionColor(), d.nombreBolso(), d.codigoModelo(), d.medidaCaja(),
                d.unidadesPorCaja(), d.bolsaTela(), d.etiquetaInterior(), d.instruccionEspecial(),
                d.destinaciones(), d.repartoCajas(), d.comentarios(), d.enlaceVideo());
        assertEquals("APC · Spring 27", InfoEmbalajeDocBuilder.titulo(sinTaller));
    }

    @Test
    void laCabeceraEsFotoInfoYQrEnUnaFila() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), jpeg(600, 800))) {
            XWPFTableRow fila = cabecera(doc);
            assertEquals(3, fila.getTableCells().size(), "foto | artículo | QR");

            List<XWPFPicture> foto = imagenesEn(fila.getCell(0));
            assertEquals(1, foto.size(), "la foto, arriba a la izquierda");
            assertEquals("jpeg", foto.get(0).getPictureData().suggestFileExtension());

            String articulo = fila.getCell(1).getTextRecursively();
            for (String esperado : new String[] {"A.P.C. · Spring 27 · TÁNGER", "PXCBC-F67008", "LZZ NOIR",
                    "SAC LE NEIGE ALPACA NOIR", "Nº ENVÍO", "270", "NOTA ICSUITE", "12662"}) {
                assertTrue(articulo.contains(esperado), "falta en el artículo: " + esperado);
            }

            List<XWPFPicture> qr = imagenesEn(fila.getCell(2));
            assertEquals(1, qr.size(), "el QR, a la derecha");
            assertEquals("png", qr.get(0).getPictureData().suggestFileExtension());
            // En versalitas: POI devuelve el texto en mayúsculas, aunque en el XML esté tal cual.
            assertTrue(fila.getCell(2).getTextRecursively().toLowerCase().contains("guide d'emballage du sac"));
        }
    }

    @Test
    void sinEnlaceLaCabeceraNoTieneColumnaDelQr() throws Exception {
        try (XWPFDocument doc = abrir(con(datosEjemplo(), "PARIS", "X", "R", "C", ""), jpeg(40, 30))) {
            XWPFTableRow fila = cabecera(doc);
            assertEquals(2, fila.getTableCells().size(), "foto | artículo");
            assertEquals(18.0, (fila.getCell(0).getWidth() + fila.getCell(1).getWidth()) / 567.0, 0.01,
                    "el artículo se queda con el ancho del QR");
        }
    }

    @Test
    void sinFotoElHuecoDeLaFotoSeConserva() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            XWPFTableRow fila = cabecera(doc);
            assertEquals(3, fila.getTableCells().size());
            XWPFTableCell foto = fila.getCell(0);
            assertEquals("", foto.getTextRecursively().trim());
            assertTrue(imagenesEn(foto).isEmpty(), "sin imagen");
            assertEquals(4.5, foto.getWidth() / 567.0, 0.01, "conserva los 4,5 cm de la columna de la foto");
            assertTrue(fila.getCell(1).getTextRecursively().contains("PXCBC-F67008"),
                    "el artículo no se corre a la izquierda");
        }
    }

    @Test
    void laFuenteEsGentiumBasicYVaIncrustada() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            for (XWPFParagraph parrafo : parrafos(doc)) {
                for (XWPFRun run : parrafo.getRuns()) {
                    if (!run.text().isEmpty()) {
                        assertEquals("Gentium Basic", run.getFontFamily(), "run «" + run.text() + "»");
                    }
                }
            }
            String estilos = doc.getStyle().getDocDefaults().xmlText();
            assertTrue(estilos.contains("w:ascii=\"Gentium Basic\""), "fuente por defecto del documento");
            assertTrue(doc.getSettings().getCTSettings().isSetEmbedTrueTypeFonts());

            List<PackagePart> fuentes = doc.getPackage()
                    .getPartsByContentType("application/vnd.openxmlformats-officedocument.obfuscatedFont");
            assertEquals(2, fuentes.size(), "normal y negrita");

            // La tabla de fuentes dice qué parte es la normal y con qué clave está ofuscada.
            PackagePart tablaFuentes = doc.getPackagePart().getRelatedPart(doc.getPackagePart()
                    .getRelationshipsByType(
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable")
                    .getRelationship(0));
            String xml = new String(tablaFuentes.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("w:name=\"Gentium Basic\""));
            Matcher normal = Pattern.compile("<w:embedRegular r:id=\"([^\"]+)\" w:fontKey=\"([^\"]+)\"").matcher(xml);
            assertTrue(normal.find(), xml);
            assertTrue(xml.contains("<w:embedBold "));
            PackagePart parteNormal = tablaFuentes.getRelatedPart(tablaFuentes.getRelationship(normal.group(1)));
            byte[] ofuscada = parteNormal.getInputStream().readAllBytes();

            byte[] desofuscada = desofuscar(ofuscada, normal.group(2));
            assertArrayEquals(new byte[] {0, 1, 0, 0}, Arrays.copyOf(desofuscada, 4),
                    "desofuscada con su clave vuelve a ser un TrueType");
            assertFalse(Arrays.equals(new byte[] {0, 1, 0, 0}, Arrays.copyOf(ofuscada, 4)), "y dentro va ofuscada");
        }
    }

    /**
     * La desofuscación de ECMA-376 parte 2, escrita aquí aparte para no
     * comprobar el builder consigo mismo: los 16 bytes del GUID en orden
     * inverso, XOR con los 32 primeros bytes.
     */
    private static byte[] desofuscar(byte[] fuente, String guid) {
        String hex = guid.replaceAll("[{}-]", "");
        byte[] guidBytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            guidBytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        byte[] salida = fuente.clone();
        for (int i = 0; i < 32; i++) {
            salida[i] ^= guidBytes[15 - (i % 16)];
        }
        return salida;
    }

    @Test
    void noHayNaranjaNiAmarillo() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), jpeg(40, 30))) {
            String xml = doc.getDocument().xmlText().toUpperCase();
            for (String color : new String[] {"FF8900", "FFF7E0", "E6C34A"}) {
                assertFalse(xml.contains(color), "queda el color " + color);
            }
        }
    }

    @Test
    void laFilaDelTotalVaEnLetraMasGrande() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            XWPFTable tabla = tablaDestinaciones(doc);
            XWPFTableRow total = tabla.getRow(tabla.getRows().size() - 1);
            XWPFTableRow dato = tabla.getRow(1);
            double tamanoDato = dato.getCell(0).getParagraphs().get(0).getRuns().get(0).getFontSizeAsDouble();
            for (XWPFTableCell celda : total.getTableCells()) {
                for (XWPFRun run : celda.getParagraphs().get(0).getRuns()) {
                    assertEquals(14.0, run.getFontSizeAsDouble(), 0.01);
                    assertTrue(run.isBold());
                    assertTrue(run.getFontSizeAsDouble() > tamanoDato);
                }
            }
        }
    }

    @Test
    void lasSeccionesConDatosSalen() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            assertTrue(texto(doc).contains("Instrucción especial"));
        }
    }

    @Test
    void laEtiquetaInteriorSeFrasea() throws Exception {
        try (XWPFDocument sin = abrir(con(datosEjemplo(), "", "", "", "", ""), null);
             XWPFDocument con = abrir(con(datosEjemplo(), "PARIS", "", "", "", ""), null)) {
            assertTrue(texto(sin).contains("NO LLEVA ETIQUETA DE CARTÓN INTERIOR"));
            assertTrue(texto(con).contains("LLEVA ETIQUETA DE CARTÓN INTERIOR PARIS"));
            assertFalse(texto(con).contains("NO LLEVA"));
        }
    }

    @Test
    void laFotoVaSiLaHay() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), jpeg(10, 10))) {
            assertEquals(2, doc.getAllPictures().size(), "foto + QR");
        }
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            assertEquals(1, doc.getAllPictures().size(), "solo el QR");
        }
    }

    /** Alto y ancho, en cm, de la primera imagen del documento que sea un JPEG (la foto, no el QR). */
    private static double[] medidaDeLaFotoEnCm(XWPFDocument doc) {
        for (XWPFTable tabla : doc.getTables()) {
            for (XWPFTableRow fila : tabla.getRows()) {
                for (XWPFTableCell celda : fila.getTableCells()) {
                    for (XWPFParagraph parrafo : celda.getParagraphs()) {
                        for (var run : parrafo.getRuns()) {
                            for (var foto : run.getEmbeddedPictures()) {
                                if ("jpeg".equals(foto.getPictureData().suggestFileExtension())) {
                                    var ext = foto.getCTPicture().getSpPr().getXfrm().getExt();
                                    return new double[] {ext.getCx() / 360000.0, ext.getCy() / 360000.0};
                                }
                            }
                        }
                    }
                }
            }
        }
        throw new AssertionError("el documento no lleva foto");
    }

    @Test
    void laFotoConservaLaProporcionYNoPasaDeCincoCentimetrosDeAlto() throws Exception {
        try (XWPFDocument apaisada = abrir(datosEjemplo(), jpeg(800, 600));
             XWPFDocument vertical = abrir(datosEjemplo(), jpeg(600, 800))) {
            double[] a = medidaDeLaFotoEnCm(apaisada);
            assertEquals(3.9, a[0], 0.01, "el ancho de la columna menos el hueco con el artículo");
            assertEquals(3.9 * 3 / 4, a[1], 0.01, "proporción 4:3");
            double[] v = medidaDeLaFotoEnCm(vertical);
            assertEquals(5.0, v[1], 0.01, "tope de alto");
            assertEquals(3.75, v[0], 0.01, "se encoge entera, sin deformarse");
        }
    }

    @Test
    void unaFotoIlegibleDejaLaCeldaVaciaSinFallar() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), "no soy un jpeg".getBytes())) {
            assertEquals(1, doc.getAllPictures().size(), "solo el QR");
            assertTrue(texto(doc).contains("PXCBC-F67008"));
            assertTrue(texto(doc).contains("TOTAL 520 UNIDADES"));
        }
    }

    @Test
    void elNombreDeFicheroSeSanea() {
        assertEquals("Info embalaje A.P.C. 270 PXCBC-F67008 LZZ.docx",
                InfoEmbalajeDocBuilder.nombreFichero(datosEjemplo()));
        DatosHojaEmbalaje conBarra = new DatosHojaEmbalaje("APC", "A.P.C.", "S27", "", 270, "",
                LocalDate.of(2026, 11, 2), "PXCBC/F67008", "LZZ", "", "", "", "", null, "", "", "",
                List.of(), "", "", "");
        assertEquals("Info embalaje A.P.C. 270 PXCBC_F67008 LZZ.docx",
                InfoEmbalajeDocBuilder.nombreFichero(conBarra));
    }

    /** El caso más cargado que la hoja tiene que caber en un A4: 6 destinaciones, 8 líneas de reparto y 4 de comentarios. */
    private static DatosHojaEmbalaje masCargado(String enlace) {
        DatosHojaEmbalaje base = con(datosEjemplo(), "PARIS", "GRABADO LOGO NIKEL",
                datosEjemplo().repartoCajas() + "\nCAJA 23: 5 UDS\nCAJA 24: 6 UDS\nCAJA 25: 2 UDS",
                datosEjemplo().comentarios() + "\nREVISAR LAS COSTURAS\nCOLOCAR PAPEL DE SEDA", enlace);
        List<DestinacionHoja> seis = new ArrayList<>(base.destinaciones());
        seis.add(new DestinacionHoja("KOREA", "4100132901", 25));
        seis.add(new DestinacionHoja("AUSTRALIA", "4100132877", 18));
        return new DatosHojaEmbalaje(base.cliente(), base.nombreCliente(),
                base.temporada(), base.taller(), base.numeroEnvio(), base.nota(), base.fecha(),
                base.referencia(), base.codigoColor(), base.descripcionColor(), base.nombreBolso(),
                base.codigoModelo(), base.medidaCaja(), base.unidadesPorCaja(), base.bolsaTela(),
                base.etiquetaInterior(), base.instruccionEspecial(), seis, base.repartoCajas(),
                base.comentarios(), base.enlaceVideo());
    }

    /**
     * Escribe el ejemplo en {@code target/}. Word bloquea el fichero mientras
     * lo tiene abierto, que es justo lo que pasa mientras se revisa la
     * maquetación: se deja al lado con otro nombre en vez de fallar.
     */
    private static Path dejarEnTarget(String nombre, byte[] bytes) throws Exception {
        Path fichero = Path.of("target", nombre + ".docx");
        Files.createDirectories(fichero.getParent());
        try {
            Files.write(fichero, bytes);
        } catch (FileSystemException abierto) {
            fichero = fichero.resolveSibling(nombre + " (nuevo).docx");
            Files.write(fichero, bytes);
        }
        return fichero;
    }

    /**
     * Deja {@code target/Info embalaje ejemplo.docx} para abrirlo en Word y
     * revisar la maquetación a ojo, con una foto de relleno vertical de móvil
     * (3:4, la que topa con el alto máximo; en el repo no entra ninguna foto
     * real) y el QR.
     */
    @Test
    void dejaElEjemploEnTarget() throws Exception {
        Path fichero = dejarEnTarget("Info embalaje ejemplo",
                builder.generar(masCargado(datosEjemplo().enlaceVideo()), jpeg(600, 800)));
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(fichero))) {
            assertEquals(2, doc.getAllPictures().size());
        }
    }

    /**
     * Deja {@code target/Info embalaje ejemplo sin foto.docx}, sin foto ni QR,
     * para ver que el hueco de la foto se conserva en blanco.
     */
    @Test
    void dejaElEjemploSinFotoNiQrEnTarget() throws Exception {
        Path fichero = dejarEnTarget("Info embalaje ejemplo sin foto", builder.generar(masCargado(""), null));
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(fichero))) {
            assertEquals(0, doc.getAllPictures().size());
        }
    }
}
