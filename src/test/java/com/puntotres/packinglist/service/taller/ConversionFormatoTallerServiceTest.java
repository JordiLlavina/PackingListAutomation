package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.config.TipoPlantilla;

/**
 * Lo que se le manda a Claude al convertir un excel de taller y cómo se lee
 * lo que devuelve. La llamada en sí no se prueba aquí (necesita la API); el
 * controlador la sustituye en sus tests.
 */
class ConversionFormatoTallerServiceTest {

    private static final String FEZ = "/ejemplos/taller/packing taller fez ejemplo.xlsx";
    private static final String TANGER = "/ejemplos/taller/packing taller tanger ejemplos.xlsx";

    private static byte[] leer(String recurso) throws Exception {
        try (InputStream in = ConversionFormatoTallerServiceTest.class.getResourceAsStream(recurso)) {
            return in.readAllBytes();
        }
    }

    // --- El libro como texto ---

    /**
     * El de Fez tiene cuatro hojas y el packing en "LISTE DE COL": van todas,
     * para que el modelo elija, y del packing va lo que solo él sabe separar
     * (la DESIGNATION con color y destinación) y lo que va a notas (los
     * totales de peso).
     */
    @Test
    void elDeFezViajaConTodasSusHojasYSusTotales() throws Exception {
        String texto = ConversionFormatoTallerService.textoDelLibro(leer(FEZ), "fez.xlsx");

        assertTrue(texto.contains("\"LISTE DE COL\""), texto);
        assertTrue(texto.contains("\"Facture\""), texto);
        assertTrue(texto.contains("B7=ULL163AL052"), texto);
        assertTrue(texto.contains("C7=SAC MARRON PA"), texto);
        assertTrue(texto.contains("F7=1-69"), texto);
        assertTrue(texto.contains("Poids Brut Total KG"), texto);
    }

    /**
     * El de Tánger trae un envío por hoja, la destinación pegada a su código y
     * la tabla de cajas con defectos debajo: si esa tabla no viajara, no
     * habría notas que copiar.
     */
    @Test
    void elDeTangerViajaConSusDosEnviosYLaTablaDeDefectos() throws Exception {
        String texto = ConversionFormatoTallerService.textoDelLibro(leer(TANGER), null);

        assertTrue(texto.contains("Hoja 1 de 2: \"11-07\""), texto);
        assertTrue(texto.contains("Hoja 2 de 2: \"01-08\""), texto);
        assertTrue(texto.contains("D2=Korea 690"), texto);
        // El taller pinta en rojo las cajas malas: el volcado lo dice.
        assertTrue(texto.contains("A29=CAJA 27 [fondo #FF0000] | B29=MAL [fondo #FF0000]"), texto);
        assertTrue(texto.contains("B26=2 MAL SIN ETIQETA SIN BOLSAS"), texto);
        assertTrue(texto.contains("(celdas combinadas: "), texto);
    }

    /**
     * Lo que no es el valor y sí dice algo: un tachado (esa fila no va), un
     * fondo de color (así marcan algunos talleres las cajas malas), un
     * comentario y las celdas combinadas. Sin anotarlo, la única señal de
     * "esta fila no existe" se perdería en el volcado.
     */
    @Test
    void elVolcadoAnotaTachadosFondosComentariosYCombinadas() throws Exception {
        byte[] excel;
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XSSFSheet hoja = libro.createSheet("PACKING");
            Font tachada = libro.createFont();
            tachada.setStrikeout(true);
            CellStyle estiloTachado = libro.createCellStyle();
            estiloTachado.setFont(tachada);
            Cell c1 = hoja.createRow(0).createCell(0);
            c1.setCellValue("F63023");
            c1.setCellStyle(estiloTachado);

            CellStyle rojo = libro.createCellStyle();
            rojo.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xFF, 0, 0}, null));
            rojo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Cell c2 = hoja.createRow(1).createCell(0);
            c2.setCellValue("CAJA 27");
            c2.setCellStyle(rojo);

            Cell c3 = hoja.createRow(2).createCell(0);
            c3.setCellValue("67008");
            var dibujo = hoja.createDrawingPatriarch();
            var ancla = libro.getCreationHelper().createClientAnchor();
            ancla.setCol1(0); ancla.setCol2(2); ancla.setRow1(2); ancla.setRow2(4);
            var comentario = dibujo.createCellComment(ancla);
            comentario.setString(libro.getCreationHelper().createRichTextString("va sin bolsa"));
            c3.setCellComment(comentario);

            hoja.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(5, 6, 0, 0));
            hoja.createRow(5).createCell(0).setCellValue("ENVOI");
            libro.write(salida);
            excel = salida.toByteArray();
        }

        String texto = ConversionFormatoTallerService.textoDelLibro(excel, null);

        assertTrue(texto.contains("A1=~~F63023~~"), texto);
        assertTrue(texto.contains("A2=CAJA 27 [fondo #FF0000]"), texto);
        assertTrue(texto.contains("A3=67008 [nota: va sin bolsa]"), texto);
        assertTrue(texto.contains("(celdas combinadas: A6:A7)"), texto);
    }

    @Test
    void unFicheroQueNoEsExcelSeDiceConPalabrasDeUsuario() {
        ConversionFormatoTallerService servicio = new ConversionFormatoTallerService(new ObjectMapper());
        ConversionFormatoTallerService.ConversionException error = assertThrows(
                ConversionFormatoTallerService.ConversionException.class,
                () -> servicio.convertir("no soy un excel".getBytes(), "x.xlsx", "APC",
                        PedidoParaConversion.vacio(TipoPlantilla.APC, null), null));
        assertTrue(error.getMessage().startsWith("No se ha podido abrir el excel del taller"),
                error.getMessage());
    }

    // --- La petición ---

    @Test
    void laPeticionLlevaElIndiceDelanteElModeloElEsfuerzoYElEsquema() {
        MessageCreateParams peticion = ConversionFormatoTallerService.peticionPara(
                "LIBRO", "APC", TipoPlantilla.APC, "INDICE", null);

        assertEquals(ConversionFormatoTallerService.MODELO, peticion.model().asString());
        OutputConfig salida = peticion.outputConfig().orElseThrow();
        assertEquals(OutputConfig.Effort.HIGH, salida.effort().orElseThrow());
        assertTrue(salida.format().isPresent(), "la salida va con esquema JSON");
        assertTrue(peticion.thinking().orElseThrow().isAdaptive());

        List<String> textos = textosDe(peticion);
        assertEquals("INDICE", textos.get(0));
        assertEquals("Cliente elegido en la aplicación: APC", textos.get(1));
        assertEquals("LIBRO", textos.get(2));
        assertEquals(ConversionFormatoTallerService.MENSAJE_USUARIO, textos.get(3));
    }

    @Test
    void sinIndiceNoViajaNingunBloqueVacioYLaHojaElegidaSePideAparte() {
        MessageCreateParams sinIndice = ConversionFormatoTallerService.peticionPara(
                "LIBRO", "AMI", TipoPlantilla.AMI, "  ", null);
        assertEquals(3, textosDe(sinIndice).size());

        MessageCreateParams conHoja = ConversionFormatoTallerService.peticionPara(
                "LIBRO", "AMI", TipoPlantilla.AMI, null, "01-08");
        assertTrue(textosDe(conHoja).contains("Convierte SOLO la hoja \"01-08\" y ninguna otra."));
    }

    /**
     * El esquema tiene que seguir al record: un campo del record que no esté
     * en el esquema llegaría siempre a null y nadie lo vería.
     */
    @Test
    void elEsquemaCubreTodosLosCamposDelRecord() throws Exception {
        JsonNode esquema = new ObjectMapper().readTree(ConversionFormatoTallerService.ESQUEMA);
        assertEquals(List.of("hoja", "hojasNoConvertidas", "cabecera", "filas", "notas",
                "resumen", "avisos"), nombres(esquema.get("required")));
        JsonNode fila = esquema.at("/properties/filas/items");
        assertEquals(List.of("envio", "cliente", "motivo", "referencia", "color", "talla",
                "destinacion", "codigoCliente", "unidadesPorCaja", "cantidad", "cajas",
                "pesoBrutoCaja", "medidaCaja"), nombres(fila.get("required")));
        assertFalse(fila.get("additionalProperties").asBoolean());
        assertEquals(List.of("fila", "columna", "texto", "consecuencia"),
                nombres(esquema.at("/properties/avisos/items/required")));
        // Las columnas que el modelo puede nombrar son las de la tabla, por su campo.
        List<String> columnas = nombres(esquema.at("/properties/avisos/items/properties/columna/enum"));
        for (PackingFormatoPuntotres.Columna columna : PackingFormatoPuntotres.Columna.values()) {
            assertTrue(columnas.contains(columna.campo()), columna.campo());
        }
        // Solo los números admiten null: la API limita a 16 los campos con unión.
        assertEquals(6, ConversionFormatoTallerService.ESQUEMA.split("\"null\"").length - 1);
        // Y el SDK lo acepta tal cual.
        assertTrue(ConversionFormatoTallerService.esquema()._additionalProperties()
                .containsKey("required"));
    }

    /**
     * Las reglas que no se pueden perder al tocar el prompt: las notas no son
     * filas, el modelo no resuelve el pedido ni completa referencias, y el
     * color solo se traduce con la tabla.
     */
    @Test
    void elPromptSeparaLasNotasYDejaLasDecisionesAJava() {
        String apc = ConversionFormatoTallerService.promptPara(TipoPlantilla.APC)
                .replaceAll("\\s+", " ");
        assertTrue(apc.contains("nunca las conviertas en filas"));
        assertTrue(apc.contains("Lo que NO debes hacer con él: elegir o deducir el código de pedido"));
        assertTrue(apc.contains("NO la completes con el prefijo del pedido"));
        assertTrue(apc.contains("NUNCA deduzcas el color por el código de pedido"));
        assertTrue(apc.contains("Son ORIENTATIVOS: la aplicación rehace el packing"));
        assertTrue(apc.contains("COPIANDO sus datos tal cual, sin normalizar ni avisar"));
        assertTrue(apc.contains("Douanes USA / D. USA / USA / UST -> DOUANES USA"));
        assertTrue(apc.contains("B9=PUNTOTRES ENVOI"), "lleva el ejemplo del formato destino");
        // El envío vacío se rellena desde la factura, fila a fila y avisando:
        // es el único dato que se toma de otra hoja.
        assertTrue(apc.contains("pon ese número en las filas de ese artículo y añade un aviso POR FILA"));
        assertTrue(apc.contains("Es el ÚNICO dato que se toma de otra hoja"));

        String ami = ConversionFormatoTallerService.promptPara(TipoPlantilla.AMI)
                .replaceAll("\\s+", " ");
        assertTrue(ami.contains("CHINA, JAPAN o PARIS"));
        assertFalse(ami.contains("DOUANES USA"));
    }

    // --- La respuesta ---

    @Test
    void laRespuestaSeLeeConSusAvisosPorFilaYSuResumen() {
        ConversionFormatoTallerService servicio = new ConversionFormatoTallerService(new ObjectMapper());
        PackingFormatoPuntotres packing = servicio.parsear("""
                {"hoja": "11-07", "hojasNoConvertidas": ["01-08"],
                 "cabecera": {"taller": "TANGER", "direccion": null, "factura": null, "fecha": null},
                 "filas": [{"envio": null, "cliente": "APC", "motivo": null, "referencia": "F67008",
                            "color": "LZZ", "talla": "U", "destinacion": "KOREA",
                            "codigoCliente": "690", "unidadesPorCaja": 10, "cantidad": 30,
                            "cajas": "1-3", "pesoBrutoCaja": null, "medidaCaja": null}],
                 "notas": [["CAJA 27", "MAL"], [null, "x"]],
                 "resumen": {"unidadesTotales": 30, "cajasTotales": null},
                 "avisos": [{"fila": 1, "columna": "referencia", "texto": "El modelo pone G7008", "consecuencia": "lectura dudosa"},
                            {"fila": 1, "columna": "envio", "texto": "Envío 134 de la factura", "consecuencia": ""},
                            {"fila": null, "columna": "", "texto": "", "consecuencia": ""}]}
                """);

        assertEquals("11-07", packing.hoja());
        assertEquals(List.of("01-08"), packing.hojasNoConvertidas());
        assertEquals(1, packing.filas().size());
        assertEquals(30, packing.filas().get(0).cantidad());
        assertEquals(List.of("CAJA 27", "MAL"), packing.notas().get(0));
        assertEquals(List.of("", "x"), packing.notas().get(1));
        assertEquals(30, packing.resumen().unidadesTotales());
        assertNull(packing.resumen().cajasTotales());
        // El aviso sin texto se descarta; el de fila conserva su fila y su
        // clase, y sin clase cae a la duda de lectura.
        assertEquals(2, packing.avisos().size());
        assertEquals(1, packing.avisos().get(0).fila());
        assertEquals("Fila 1: El modelo pone G7008", packing.avisos().get(0).frase());
        assertEquals("lectura dudosa", packing.avisos().get(0).consecuencia());
        assertEquals(PackingFormatoPuntotres.Aviso.DUDA_DE_LECTURA,
                packing.avisos().get(1).consecuencia());
        assertTrue(packing.avisos().get(1).revisar());
        assertEquals(PackingFormatoPuntotres.Columna.ENVIO, packing.avisos().get(1).celda());
    }

    @Test
    void unaRespuestaSinJsonSeDiceConPalabrasDeUsuario() {
        ConversionFormatoTallerService servicio = new ConversionFormatoTallerService(new ObjectMapper());
        assertThrows(ConversionFormatoTallerService.ConversionException.class,
                () -> servicio.parsear("No he encontrado ningún packing"));
    }

    private static List<String> textosDe(MessageCreateParams peticion) {
        return peticion.messages().get(0).content().asBlockParams().stream()
                .map(ContentBlockParam::asText)
                .map(bloque -> bloque.text())
                .toList();
    }

    private static List<String> nombres(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText).toList();
    }

}
