package com.puntotres.packinglist.service.embalaje;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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

    /** Todo el texto del documento: párrafos del cuerpo y celdas de tablas. */
    private static String texto(XWPFDocument doc) {
        StringBuilder texto = new StringBuilder();
        for (XWPFParagraph parrafo : doc.getParagraphs()) {
            texto.append(parrafo.getText()).append('\n');
        }
        for (XWPFTable tabla : doc.getTables()) {
            for (XWPFTableRow fila : tabla.getRows()) {
                for (XWPFTableCell celda : fila.getTableCells()) {
                    texto.append(celda.getText()).append('\n');
                }
            }
        }
        return texto.toString();
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
                    "INSTRUCCIONES DE EMBALAJE", "A.P.C.", "Spring 27", "TÁNGER",
                    "Nº ENVÍO", "270", "NOTA ICSUITE", "12662", "FECHA", "02/11/2026",
                    "PXCBC-F67008", "LZZ NOIR", "SAC LE NEIGE ALPACA NOIR",
                    "60x40x40", "BOLSA DE TELA NEGRA", "GRABADO LOGO NIKEL",
                    "EMBALAJE", "DESTINACIONES",
                    "JAPAN", "4100132915", "DOUANES USA", "4100132811",
                    "WHOLESALE", "4100132847", "RETAIL", "4100132835",
                    "TOTAL 520 UNIDADES",
                    "REPARTO EN CAJAS", "CAJA 1 A 12: 10 UDS", "CAJA 13: 8 UDS",
                    "CAJAS 14 A 20: 10 UDS", "CAJA 21: 3 UDS", "CAJA 22: 7 UDS",
                    "COMENTARIOS", "SACAR LOS BOLSOS DEL SOBRE CON CUIDADO",
                    "NO MEZCLAR COLORES"}) {
                assertTrue(texto.contains(esperado), "falta en la hoja: " + esperado);
            }
        }
    }

    @Test
    void laTablaDeDestinacionesLlevaCabeceraFilasYTotal() throws Exception {
        try (XWPFDocument doc = abrir(datosEjemplo(), null)) {
            XWPFTable tabla = doc.getTables().stream()
                    .filter(t -> t.getRow(0).getTableCells().size() == 3
                            && t.getRow(0).getCell(0).getText().trim().equals("Destinación"))
                    .findFirst().orElseThrow();
            assertEquals(6, tabla.getRows().size(), "cabecera + 4 destinaciones + total");
            assertEquals("Nº pedido", tabla.getRow(0).getCell(1).getText().trim());
            assertEquals("Cantidad", tabla.getRow(0).getCell(2).getText().trim());
            assertEquals("243", tabla.getRow(2).getCell(2).getText().trim());
            assertEquals("Total", tabla.getRow(5).getCell(0).getText().trim());
            assertEquals("520", tabla.getRow(5).getCell(2).getText().trim());
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
            assertFalse(texto.contains("REPARTO EN CAJAS"));
            assertFalse(texto.contains("COMENTARIOS"));
            assertFalse(texto.contains("Instrucción"));
            assertTrue(texto.contains("EMBALAJE"));
            assertTrue(texto.contains("DESTINACIONES"));
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
    void laFotoConservaLaProporcionYNoPasaDeCuatroCentimetrosDeAlto() throws Exception {
        try (XWPFDocument apaisada = abrir(datosEjemplo(), jpeg(800, 600));
             XWPFDocument vertical = abrir(datosEjemplo(), jpeg(600, 800))) {
            double[] a = medidaDeLaFotoEnCm(apaisada);
            assertEquals(5.0, a[0], 0.01, "ancho de 5 cm");
            assertEquals(3.75, a[1], 0.01, "proporción 4:3");
            double[] v = medidaDeLaFotoEnCm(vertical);
            assertEquals(4.0, v[1], 0.01, "tope de alto");
            assertEquals(3.0, v[0], 0.01, "se encoge entera, sin deformarse");
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

    /**
     * Deja {@code target/Info embalaje ejemplo.docx} para abrirlo en Word y
     * revisar la maquetación a ojo, con una foto de relleno (en el repo no
     * entra ninguna foto real) y el QR.
     */
    @Test
    void dejaElEjemploEnTarget() throws Exception {
        // El caso más cargado que la hoja tiene que caber en un A4: 6 destinaciones,
        // 8 líneas de reparto y 4 de comentarios, con una foto apaisada de móvil (4:3).
        DatosHojaEmbalaje base = con(datosEjemplo(), "PARIS", "GRABADO LOGO NIKEL",
                datosEjemplo().repartoCajas() + "\nCAJA 23: 5 UDS\nCAJA 24: 6 UDS\nCAJA 25: 2 UDS",
                datosEjemplo().comentarios() + "\nREVISAR LAS COSTURAS\nCOLOCAR PAPEL DE SEDA",
                datosEjemplo().enlaceVideo());
        List<DestinacionHoja> seis = new java.util.ArrayList<>(base.destinaciones());
        seis.add(new DestinacionHoja("KOREA", "4100132901", 25));
        seis.add(new DestinacionHoja("AUSTRALIA", "4100132877", 18));
        byte[] bytes = builder.generar(new DatosHojaEmbalaje(base.cliente(), base.nombreCliente(),
                base.temporada(), base.taller(), base.numeroEnvio(), base.nota(), base.fecha(),
                base.referencia(), base.codigoColor(), base.descripcionColor(), base.nombreBolso(),
                base.codigoModelo(), base.medidaCaja(), base.unidadesPorCaja(), base.bolsaTela(),
                base.etiquetaInterior(), base.instruccionEspecial(), seis, base.repartoCajas(),
                base.comentarios(), base.enlaceVideo()), jpeg(800, 600));
        Path fichero = Path.of("target", "Info embalaje ejemplo.docx");
        Files.createDirectories(fichero.getParent());
        try {
            Files.write(fichero, bytes);
        } catch (FileSystemException abierto) {
            // Word bloquea el fichero mientras lo tiene abierto, que es justo lo
            // que pasa mientras se revisa la maquetación: se deja al lado con
            // otro nombre en vez de fallar o de no dejar nada.
            fichero = fichero.resolveSibling("Info embalaje ejemplo (nuevo).docx");
            Files.write(fichero, bytes);
        }
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(fichero))) {
            assertEquals(2, doc.getAllPictures().size());
        }
    }
}
