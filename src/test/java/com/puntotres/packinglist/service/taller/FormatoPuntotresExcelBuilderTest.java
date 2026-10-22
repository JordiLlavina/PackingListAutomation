package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres.Aviso;
import com.puntotres.packinglist.service.taller.PackingFormatoPuntotres.Columna;

/**
 * El excel que sale de una conversión tiene que leerse con
 * {@link TallerColisExcel} exactamente como uno hecho a mano en el formato
 * Puntotres, y las notas del taller tienen que estar en la hoja sin que el
 * lector las vea nunca.
 */
class FormatoPuntotresExcelBuilderTest {

    private static PackingFormatoPuntotres.Fila fila(String cliente, String referencia,
                                                     String color, String destino, String code,
                                                     int porCaja, int cantidad, String cajas) {
        return new PackingFormatoPuntotres.Fila("263", cliente, null, referencia, color, "U",
                destino, code, porCaja, cantidad, cajas, null, null);
    }

    private static PackingFormatoPuntotres packing(List<List<String>> notas, List<Aviso> avisos) {
        return new PackingFormatoPuntotres("11-07", List.of(),
                new PackingFormatoPuntotres.Cabecera("SWEET BAG SARL", "FES", "336/26", "7/11/26"),
                List.of(
                        fila("APC", "F67008", "LZZ", "KOREA", "690", 10, 30, "1-3"),
                        fila("APC", "F67008", "LZZ", "DOUANES USA", "711", 10, 20, "4-5"),
                        new PackingFormatoPuntotres.Fila(null, "AMI", "PROD", "ULL163.AL0052",
                                "DARK COFFEE", "U", "PARIS", null, 8, 8, "6", 10.5, "60x40x40")),
                notas,
                null,
                avisos);
    }

    @Test
    void elLectorDelTallerLeeLaTablaComoUnaHechaAMano() throws Exception {
        byte[] excel = FormatoPuntotresExcelBuilder.construir(packing(List.of(), List.of()));

        TallerColisExcel leido = TallerColisExcel.desdeBytes(excel);

        assertEquals(3, leido.lineas().size());
        LineaTaller primera = leido.lineas().get(0);
        assertEquals("APC", primera.cliente());
        assertEquals("F67008", primera.referencia());
        assertEquals("LZZ", primera.color());
        assertEquals("KOREA", primera.destinoTaller());
        assertEquals("690", primera.code());
        assertEquals(10, primera.unidadesPorCajaTaller());
        assertEquals(30, primera.cantidad());
        assertNull(primera.pesoBrutoCajaKg());

        LineaTaller ami = leido.lineas().get(2);
        assertEquals("PROD", ami.motivo());
        assertEquals(10.5, ami.pesoBrutoCajaKg());
        assertEquals("60X40X40", ami.medidaCajaTaller());
        assertTrue(leido.avisos().isEmpty(), leido.avisos().toString());
    }

    /**
     * La nota más traicionera posible: una fila de la tabla de defectos que
     * cae entera en las columnas de un artículo, con referencia, color,
     * destinación y cantidad. Si el lector llegara hasta ella entraría al
     * packing como mercancía; las filas vacías de separación lo impiden.
     */
    @Test
    void lasNotasEstanEnLaHojaPeroNoEntranEnElPacking() throws Exception {
        List<String> notaConPintaDeArticulo = List.of(
                "CAJA 27", "MAL", "x", "ULL999.AL0001", "BLACK", "U", "CH", "", "8", "12", "27");
        byte[] excel = FormatoPuntotresExcelBuilder.construir(packing(List.of(
                List.of("CAJA 27", "MAL"),
                notaConPintaDeArticulo,
                List.of("Poids Brut Total KG", "790,00")), List.of()));

        TallerColisExcel leido = TallerColisExcel.desdeBytes(excel);
        assertEquals(3, leido.lineas().size());
        assertTrue(leido.lineas().stream().noneMatch(l -> l.referencia().equals("ULL999.AL0001")));
        assertTrue(leido.avisos().isEmpty(), leido.avisos().toString());

        List<String> textos = textosDe(excel);
        assertTrue(textos.contains(FormatoPuntotresExcelBuilder.TITULO_NOTAS));
        assertTrue(textos.contains("CAJA 27"));
        assertTrue(textos.contains("MAL"));
        assertTrue(textos.contains("Poids Brut Total KG"));
    }

    @Test
    void elMembreteYLaCabeceraVanDondeEnElFormatoPuntotres() throws Exception {
        byte[] excel = FormatoPuntotresExcelBuilder.construir(packing(List.of(), List.of()));
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = libro.getSheet(TallerColisExcel.HOJA_COLIS);
            assertEquals("SWEET BAG SARL", hoja.getRow(1).getCell(2).getStringCellValue());
            assertEquals("Facture 336/26", hoja.getRow(4).getCell(3).getStringCellValue());
            assertEquals("REFERENCE", hoja.getRow(FormatoPuntotresExcelBuilder.FILA_CABECERA)
                    .getCell(4).getStringCellValue());
            // Los números son números, no texto: el cliente los suma en Excel.
            assertEquals(30, hoja.getRow(FormatoPuntotresExcelBuilder.FILA_CABECERA + 1)
                    .getCell(10).getNumericCellValue());
        }
    }

    /**
     * Una fila con aviso se ve en el propio excel: amarilla y con el aviso
     * como comentario en la referencia. Quien revisa no tiene que cruzar una
     * lista aparte con la tabla, y el lector del taller no se entera.
     */
    /**
     * El amarillo y el comentario van a LA CELDA de la columna con la duda,
     * nunca a la fila entera: la duda del envío se ve en PUNTOTRES ENVOI, la
     * del color en COULEUR. Lo informativo lleva comentario pero no amarillo.
     */
    @Test
    void cadaAvisoVaASuCeldaYSoloLaDudaSePintaDeAmarillo() throws Exception {
        byte[] excel = FormatoPuntotresExcelBuilder.construir(packing(List.of(), List.of(
                Aviso.deFila(2, Columna.COLOR, "el color no cuadra",
                        "El pedido 711 es en LZZ y la hoja dice KAN"),
                new Aviso(2, "envio", "El envío 134 sale de la hoja Facture", true,
                        "envío tomado de la factura"),
                Aviso.informativo(2, Columna.REFERENCIA, "Referencia completada a PXCBC-F67008"),
                Aviso.informativo(3, Columna.REFERENCIA, "Referencia completada a ULL163.AL0052"),
                Aviso.general("La hoja declara 60 unidades y las filas suman 58"))));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = libro.getSheet(TallerColisExcel.HOJA_COLIS);
            Row sinAviso = hoja.getRow(FormatoPuntotresExcelBuilder.FILA_CABECERA + 1);
            Row conDudas = hoja.getRow(FormatoPuntotresExcelBuilder.FILA_CABECERA + 2);
            Row informativa = hoja.getRow(FormatoPuntotresExcelBuilder.FILA_CABECERA + 3);
            int colEnvio = 1 + Columna.ENVIO.indice();
            int colReferencia = 1 + Columna.REFERENCIA.indice();
            int colColor = 1 + Columna.COLOR.indice();
            int colCantidad = 1 + Columna.CANTIDAD.indice();

            // Las dos celdas con duda, amarillas y con su comentario.
            assertEquals(FillPatternType.SOLID_FOREGROUND,
                    conDudas.getCell(colColor).getCellStyle().getFillPattern());
            assertTrue(conDudas.getCell(colColor).getCellComment().getString().getString()
                    .contains("KAN"));
            assertEquals(FillPatternType.SOLID_FOREGROUND,
                    conDudas.getCell(colEnvio).getCellStyle().getFillPattern());
            assertTrue(conDudas.getCell(colEnvio).getCellComment().getString().getString()
                    .contains("Facture"));
            // El resto de la fila, intacto: ni amarillo ni comentario.
            assertEquals(FillPatternType.NO_FILL,
                    conDudas.getCell(colCantidad).getCellStyle().getFillPattern());
            assertNull(conDudas.getCell(colCantidad).getCellComment());
            // La referencia de esa fila solo tiene lo informativo: comentario sin amarillo.
            assertEquals(FillPatternType.NO_FILL,
                    conDudas.getCell(colReferencia).getCellStyle().getFillPattern());
            assertTrue(conDudas.getCell(colReferencia).getCellComment().getString().getString()
                    .contains("PXCBC-F67008"));

            assertEquals(FillPatternType.NO_FILL,
                    informativa.getCell(colReferencia).getCellStyle().getFillPattern());
            assertNotNull(informativa.getCell(colReferencia).getCellComment());

            assertEquals(FillPatternType.NO_FILL,
                    sinAviso.getCell(colColor).getCellStyle().getFillPattern());
            assertNull(sinAviso.getCell(colColor).getCellComment());
        }
        assertEquals(3, TallerColisExcel.desdeBytes(excel).lineas().size());
    }

    /** Un aviso sin columna (o con una que no existe) cae a la referencia. */
    @Test
    void unAvisoSinColumnaVaALaReferencia() {
        assertEquals(Columna.REFERENCIA, new Aviso(1, "", "x", true, null).celda());
        assertEquals(Columna.REFERENCIA, new Aviso(1, "loQueSea", "x", true, null).celda());
        assertEquals(Columna.ENVIO, new Aviso(1, "envio", "x", true, null).celda());
    }

    private static List<String> textosDe(byte[] excel) throws Exception {
        List<String> textos = new ArrayList<>();
        DataFormatter formato = new DataFormatter();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            for (Row fila : libro.getSheetAt(0)) {
                for (Cell celda : fila) {
                    textos.add(formato.formatCellValue(celda));
                }
            }
        }
        return textos;
    }
}
