package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.testutil.FotosDePrueba;

/** El flujo web de las hojas de corte de SMS, con los beans reales. */
@SpringBootTest
@AutoConfigureMockMvc
class CorteSmsControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FotosCatalogo fotosCatalogo;

    private final MockHttpSession sesion = new MockHttpSession();

    private static byte[] real() throws IOException {
        try (InputStream entrada = CorteSmsControllerTest.class
                .getResourceAsStream("/ejemplos/corte/COMMANDE_SMS_APC_FALL27.xlsx")) {
            return entrada.readAllBytes();
        }
    }

    @Test
    void losDocumentosDelCorteSonUnSubmenuDeProduccionYSmsSoloConElTitulo() throws Exception {
        String menu = mvc.perform(get("/documentos-corte"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(menu.contains("href=\"/documentos-corte/produccion\"") && menu.contains("Producción"), menu);
        assertTrue(menu.contains("href=\"/documentos-corte/sms\"") && menu.contains("SMS"), menu);
        assertFalse(menu.contains("menu-entrada") || menu.contains("menu-lista"), "sin texto, solo el título");
        mvc.perform(get("/documentos-corte/produccion")).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/documentos-corte\"")));
    }

    @Test
    void laEntradaSoloDejaElegirApcYNoPideFotos() throws Exception {
        String pagina = mvc.perform(get("/documentos-corte/sms"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(pagina.matches("(?s).*<option value=\"APC\"(?![^>]*disabled)[^>]*selected[^>]*>A\\.P\\.C\\.</option>.*"),
                pagina);
        assertTrue(pagina.matches("(?s).*<option value=\"AMI\"[^>]*disabled[^>]*>AMI — en desarrollo</option>.*"),
                pagina);
        assertTrue(pagina.contains("id=\"temporada\" name=\"temporada\""));
        assertTrue(pagina.contains("id=\"excel\" name=\"excel\""));
        assertFalse(pagina.contains("data-campo-fotos") || pagina.contains("Word"), "nada del Word de fotos");
    }

    @Test
    void generaUnaHojaPorFilaConMuestrasYLaDescargaSeLlamaComoLaTemporada() throws Exception {
        mvc.perform(generar("APC", "FALL 27", real()))
                .andExpect(redirectedUrl("/documentos-corte/sms/resultados"));

        mvc.perform(get("/documentos-corte/sms/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("APC FALL 27 - Hojas de Corte.xlsx")))
                .andExpect(content().string(containsString("Hojas de corte (37 hojas)")));

        byte[] excel = mvc.perform(get("/documentos-corte/sms/descargar").session(sesion))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("APC%20FALL%2027%20-%20Hojas%20de%20Corte.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals(37, libro.getNumberOfSheets());
            Sheet primera = libro.getSheetAt(0);
            assertEquals("PXCBC-F67008 - KAF VERT FONCÉ", primera.getSheetName());
            List<String> textos = textos(primera);
            assertTrue(textos.contains("A.P.C. | Temporada FALL 27 | SMS"), textos.toString());
            assertTrue(textos.contains("F67008") && textos.contains("LE NEIGE"), textos.toString());
            assertTrue(textos.contains("Cantidad  2"), textos.toString());
            assertTrue(textos.stream().anyMatch(texto -> texto.contains("ALPACA TOUCH")), textos.toString());
        }
    }

    @Test
    void sinCroquisEnLaFilaUsaElDeOtraFilaDelModeloYSiNoElDelCatalogo() throws Exception {
        byte[] croquis = FotosDePrueba.jpeg(300, 200, Color.BLUE);
        fotosCatalogo.guardar("APC", "S99002", "principal.jpg", FotosDePrueba.jpeg(200, 300, Color.GREEN));
        byte[] entrada;
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet("FEMME");
            fila(hoja, 0, "CROQUIS", "CODIGO", "COLORIS A.P.C.", "ARTICLE", "Commande SMS");
            fila(hoja, 1, "", "PXCBC-S99001", "LZZ NOIR", "ALPACA TOUCH", "2");
            fila(hoja, 2, "", "PXCGY-S99001", "LZZ NOIR", "PIPER", "1 SMS");
            fila(hoja, 3, "", "PXCBC-S99002", "LZZ NOIR", "ALPACA TOUCH", "3");
            fila(hoja, 4, "", "PXCBC-S99003", "LZZ NOIR", "ALPACA TOUCH", "3");
            XSSFDrawing dibujo = hoja.createDrawingPatriarch();
            dibujo.createPicture(new XSSFClientAnchor(0, 0, 0, 0, 0, 1, 1, 2),
                    libro.addPicture(croquis, Workbook.PICTURE_TYPE_JPEG));
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            entrada = salida.toByteArray();
        }

        mvc.perform(generar("APC", "SS99", entrada)).andExpect(redirectedUrl("/documentos-corte/sms/resultados"));
        mvc.perform(get("/documentos-corte/sms/resultados").session(sesion))
                .andExpect(content().string(containsString("1 hoja sale sin imagen")));
        byte[] excel = mvc.perform(get("/documentos-corte/sms/descargar").session(sesion))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals(List.of(300), anchos(libro.getSheetAt(0)), "su croquis");
            assertEquals(List.of(300), anchos(libro.getSheetAt(1)), "el de la otra fila del modelo");
            assertEquals(List.of(200), anchos(libro.getSheetAt(2)), "la foto del catálogo");
            assertEquals(List.of(), anchos(libro.getSheetAt(3)), "nada");
        }
    }

    @Test
    void unClienteSinLectorNoPasa() throws Exception {
        mvc.perform(generar("AMI", "SS27", real()))
                .andExpect(redirectedUrl("/documentos-corte/sms"))
                .andExpect(flash().attribute("error", containsString("todavía están en desarrollo")));
    }

    @Test
    void sinTemporadaNoSeGeneraYSeVuelveConLoElegido() throws Exception {
        mvc.perform(generar("APC", " ", real()))
                .andExpect(redirectedUrl("/documentos-corte/sms"))
                .andExpect(flash().attribute("error", containsString("Escribe la temporada")))
                .andExpect(flash().attribute("clienteElegido", "APC"));
    }

    @Test
    void unExcelQueNoEsElDeSmsSeDice() throws Exception {
        byte[] otro;
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("Hoja1").createRow(0).createCell(0).setCellValue("REFERENCIA");
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            otro = salida.toByteArray();
        }

        mvc.perform(generar("APC", "FALL 27", otro))
                .andExpect(redirectedUrl("/documentos-corte/sms"))
                .andExpect(flash().attribute("error", containsString("revisa que sea el excel de pedido de SMS")));
    }

    private MockMultipartHttpServletRequestBuilder generar(String cliente, String temporada, byte[] excel) {
        MockMultipartHttpServletRequestBuilder peticion = multipart("/documentos-corte/sms/generar")
                .file(new MockMultipartFile("excel", "COMMANDE SMS.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel));
        peticion.param("cliente", cliente).param("temporada", temporada).session(sesion);
        return peticion;
    }

    private static void fila(XSSFSheet hoja, int numero, String... valores) {
        Row fila = hoja.createRow(numero);
        for (int i = 0; i < valores.length; i++) {
            fila.createCell(i).setCellValue(valores[i]);
        }
    }

    private static List<String> textos(Sheet hoja) {
        DataFormatter formato = new DataFormatter();
        List<String> textos = new ArrayList<>();
        hoja.forEach(fila -> fila.forEach(celda -> {
            String texto = formato.formatCellValue(celda);
            if (!texto.isBlank()) {
                textos.add(texto);
            }
        }));
        return textos;
    }

    private static List<Integer> anchos(Sheet hoja) throws IOException {
        List<Integer> anchos = new ArrayList<>();
        XSSFDrawing dibujo = ((XSSFSheet) hoja).getDrawingPatriarch();
        if (dibujo != null) {
            for (var forma : dibujo.getShapes()) {
                if (forma instanceof XSSFPicture imagen) {
                    anchos.add(javax.imageio.ImageIO.read(new ByteArrayInputStream(
                            imagen.getPictureData().getData())).getWidth());
                }
            }
        }
        return anchos;
    }
}
