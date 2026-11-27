package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;

class LectorSmsIaTest {

    private final LectorSmsIa lector = new LectorSmsIa(new ObjectMapper());

    private static final String RESPUESTA = """
            {"clienteDetectado": "SANDRO", "esDelCliente": false, "motivoCliente": "logo SANDRO en la cabecera",
             "esPedidoDeSms": true, "motivoSms": "",
             "lineas": [
               {"hoja": "SMS", "fila": 7, "referencia": "SHAGB 0123", "modelo": "", "nombreModelo": "Tote",
                "codigoColor": "001", "descripcionColor": "BLACK", "piel": "ALPACA", "colorPiel": "NEGRO",
                "forro": "STILLA", "colorForro": "999",
                "combinaciones": [{"nombre": "NOBILE", "color": "ORO"}, {"nombre": "", "color": "x"}],
                "muestras": 3, "comentario": ""},
               {"hoja": "SMS", "fila": 8, "referencia": "SHAGB0124", "modelo": "", "nombreModelo": "",
                "codigoColor": "", "descripcionColor": "ROUGE", "piel": "", "colorPiel": "", "forro": "",
                "colorForro": "", "combinaciones": [], "muestras": null, "comentario": ""}
             ],
             "avisos": ["hoja SMS, fila 8: no se entiende la cantidad", " "]}
            """;

    @Test
    void parseaLaRespuestaConLineasYCliente() {
        LecturaSmsIa lectura = lector.parsear("texto delante " + RESPUESTA);

        assertEquals("SANDRO", lectura.clienteDetectado());
        assertEquals(Boolean.FALSE, lectura.esDelCliente());
        assertEquals(2, lectura.lineas().size());
        LecturaSmsIa.Linea primera = lectura.lineas().get(0);
        assertEquals("SHAGB0123", primera.referencia(), "sin espacios, como el CODIGO de APC");
        assertEquals(7, primera.fila());
        assertEquals(3, primera.muestras());
        assertEquals(1, primera.combinaciones().size(), "una combinación sin nombre no existe");
        assertEquals("NOBILE", primera.combinaciones().get(0).nombre());
        assertNull(lectura.lineas().get(1).muestras());
        assertEquals(List.of("hoja SMS, fila 8: no se entiende la cantidad"), lectura.avisos());
    }

    @Test
    void unaRespuestaSinJsonEsUnaLecturaFallida() {
        assertThrows(LectorSmsIa.LecturaFallida.class, () -> lector.parsear("no puedo"));
    }

    @Test
    void peticionDeExcelLlevaElVolcadoYElCliente() throws IOException {
        byte[] excel = libro(false);

        MessageCreateParams peticion = LectorSmsIa.peticionPara(
                LectorSmsIa.documento(excel, "sms.xlsx"), "SANDRO", "Sandro Paris");

        List<String> textos = peticion.messages().get(0).content().asBlockParams().stream()
                .filter(ContentBlockParam::isText).map(b -> b.asText().text()).toList();
        assertTrue(textos.get(0).contains("Sandro Paris") && textos.get(0).contains("SANDRO"));
        assertTrue(textos.stream().anyMatch(t -> t.contains("A2=SHAGB0123") && t.contains("sms.xlsx")),
                "el excel va volcado con la dirección de cada celda: " + textos);
        assertEquals(LectorSmsIa.MODELO, peticion.model().asString());
    }

    @Test
    void peticionDePdfLlevaBloqueDeDocumento() throws IOException {
        byte[] pdf = "%PDF-1.4 falso".getBytes();

        List<ContentBlockParam> bloques = LectorSmsIa.documento(pdf, "sms.pdf");

        assertTrue(bloques.stream().anyMatch(ContentBlockParam::isDocument));
        assertTrue(LectorSmsIa.esPdf(pdf));
        assertFalse(LectorSmsIa.esPdf(libro(false)));
    }

    @Test
    void imagenesSeAgrupanPorHojaYFila() throws IOException {
        List<String> avisos = new ArrayList<>();

        Map<String, Map<Integer, List<Imagen>>> imagenes = ImagenesPorFila.de(libro(true), avisos);

        assertEquals(1, imagenes.get("SMS").get(3).size(), "la imagen anclada en la fila 3 de Excel");
        assertTrue(avisos.isEmpty(), avisos.toString());
        assertTrue(ImagenesPorFila.de("%PDF".getBytes(), avisos).isEmpty(), "un PDF no tiene imágenes");
    }

    /** Un excel de SMS de mentira: títulos, dos filas y, si se pide, una imagen anclada en la fila 3. */
    private static byte[] libro(boolean conImagen) throws IOException {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XSSFSheet hoja = (XSSFSheet) libro.createSheet("SMS");
            hoja.createRow(0).createCell(0).setCellValue("REF");
            hoja.createRow(1).createCell(0).setCellValue("SHAGB0123");
            hoja.createRow(2).createCell(0).setCellValue("SHAGB0124");
            if (conImagen) {
                BufferedImage imagen = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(imagen, "png", png);
                int indice = libro.addPicture(png.toByteArray(), Workbook.PICTURE_TYPE_PNG);
                XSSFDrawing dibujo = hoja.createDrawingPatriarch();
                ClientAnchor ancla = libro.getCreationHelper().createClientAnchor();
                ancla.setCol1(3);
                ancla.setRow1(2);
                ancla.setCol2(4);
                ancla.setRow2(3);
                dibujo.createPicture(ancla, indice);
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }
}
