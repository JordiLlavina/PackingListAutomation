package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.corte.LecturaSmsIa;
import com.puntotres.packinglist.service.corte.LectorSmsIa;
import com.puntotres.packinglist.testutil.FotosDePrueba;

/** El flujo web de las hojas de corte de SMS, con los beans reales. */
@SpringBootTest
@AutoConfigureMockMvc
class CorteSmsControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FotosCatalogo fotosCatalogo;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private MemoriaPieles memoriaPieles;

    @Autowired
    private TablaColores tablaColores;

    /** Sin red: lo que «lee» Claude lo pone cada test. */
    @MockitoBean
    private LectorSmsIa lectorIa;

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
    void laEntradaOfreceTodosLosClientesYArrancaEnApcSinPedirFotos() throws Exception {
        String pagina = mvc.perform(get("/documentos-corte/sms"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(pagina.matches("(?s).*<option value=\"APC\"[^>]*data-con-lector=\"true\"[^>]*selected[^>]*>"
                + "A\\.P\\.C\\.</option>.*"), pagina);
        assertTrue(pagina.matches("(?s).*<option value=\"AMI\"[^>]*data-con-lector=\"false\"[^>]*>AMI</option>.*"),
                pagina);
        assertFalse(pagina.matches("(?s).*<option[^>]*disabled.*") || pagina.contains("en desarrollo"),
                "ya no hay clientes apagados: los que no tienen lector van por la IA");
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
            // El color de la piel es el del bolso, de COLORIS A.P.C., y no el «VERT FONCE»
            // de COLORIS FOURNISSEURS (decisión del usuario).
            assertTrue(textos.contains("COLOR\nKAF VERT FONCÉ"), textos.toString());
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

    // --- Clientes sin lector propio: lectura con IA (simulada) y revisión ---

    private static LecturaSmsIa.Linea leida(int fila, String referencia, String codigo, String color,
                                            Integer muestras) {
        return new LecturaSmsIa.Linea("SMS", fila, referencia, "", "BOLSO PRUEBA", codigo, color, "ALPACA",
                "NEGRO", "STILLA", "999", List.of(new LecturaSmsIa.Combinacion("NOBILE", "ORO")), muestras, "");
    }

    private void simularLectura(Boolean esDelCliente, LecturaSmsIa.Linea... lineas) {
        when(lectorIa.leer(any(), any(), eq("AMI"), any())).thenReturn(new LecturaSmsIa("SANDRO", esDelCliente,
                "logo SANDRO en la cabecera", true, "", List.of(lineas), List.of("hoja SMS, fila 9: dudosa")));
    }

    @Test
    void unClienteSinLectorSeLeeConIaYVaALaRevisionConLaAlertaDeOtroCliente() throws Exception {
        simularLectura(false, leida(2, "ULL9S01.AL9S01", "9S1", "NOIR", 2), leida(3, "ULL9S01.AL9S01", "9S2",
                "ROUGE", null));

        mvc.perform(generar("AMI", "SS27", real())).andExpect(redirectedUrl("/documentos-corte/sms/revision"));

        String pagina = mvc.perform(get("/documentos-corte/sms/revision").session(sesion))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains("Este documento parece de SANDRO, no de AMI: logo SANDRO en la cabecera"), pagina);
        assertTrue(pagina.contains("value=\"ULL9S01.AL9S01\"") && pagina.contains("hoja SMS, fila 9: dudosa"));
        assertTrue(casilla(pagina, 0).contains("checked"), "con muestras: " + casilla(pagina, 0));
        assertFalse(casilla(pagina, 1).isEmpty() || casilla(pagina, 1).contains("checked"),
                "sin muestras viene desmarcada: " + casilla(pagina, 1));
    }

    @Test
    void unaFilaIncluidaSinMuestrasVuelveALaRevisionConSuError() throws Exception {
        simularLectura(true, leida(2, "ULL9S01.AL9S01", "9S1", "NOIR", 2));
        mvc.perform(generar("AMI", "SS27", real())).andExpect(redirectedUrl("/documentos-corte/sms/revision"));

        mvc.perform(revisar(fila(0, "ULL9S01.AL9S01", "9S1", "NOIR", "")))
                .andExpect(redirectedUrl("/documentos-corte/sms/revision"))
                .andExpect(flash().attribute("errores", List.of("ULL9S01.AL9S01 (SMS, fila 2): faltan las muestras")));
    }

    @Test
    void generarDesdeLaRevisionDescargaYEnsenaLoNuevoQueSePuedeEntrarEnElSistema() throws Exception {
        simularLectura(true, leida(2, "ULL9S02.AL9S02", "9S3", "NOIR", 2));
        mvc.perform(generar("AMI", "SS27", real())).andExpect(redirectedUrl("/documentos-corte/sms/revision"));

        mvc.perform(revisar(fila(0, "ULL9S02.AL9S02", "9S3", "NOIR", "2"),
                        fila(1, "ULL9S02.AL9S02", "9S4", "BLEU", "1 SMS")))
                .andExpect(redirectedUrl("/documentos-corte/sms/resultados"));

        String pagina = mvc.perform(get("/documentos-corte/sms/resultados").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains("Nuevos en el sistema") && pagina.contains("ULL9S02")
                && pagina.contains("AL9S02") && pagina.contains("9S4"), pagina);
        byte[] excel = mvc.perform(get("/documentos-corte/sms/descargar").session(sesion))
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals(2, libro.getNumberOfSheets());
            assertTrue(textos(libro.getSheetAt(1)).contains("AMI | Temporada SS27 | SMS"));
        }

        // Entrar el modelo sin nombre no deja; con nombre entra en Muestras con su piel y su forro.
        mvc.perform(post("/documentos-corte/sms/novedades/m0").session(sesion).param("m0.nombre", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("falta el nombre")));
        mvc.perform(post("/documentos-corte/sms/novedades/m0").session(sesion).param("m0.nombre", "BOLSO PRUEBA")
                        .param("m0.a0.forro", "STILLA 999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entrados[0]").value("m0"));
        Modelo modelo = catalogo.modelo("AMI", "ULL9S02").orElseThrow();
        assertEquals(Etapa.SMS, modelo.getEtapa());
        assertEquals("BOLSO PRUEBA", modelo.getNombre());
        assertEquals("STILLA 999", catalogo.articulo("AMI", "ULL9S02.AL9S02").orElseThrow().getForro());

        // «Entrar todo» entra lo que queda: la piel y los colores.
        mvc.perform(post("/documentos-corte/sms/novedades").session(sesion).param("p0.nombre", "alpaca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendientes").value(0));
        assertEquals("ALPACA", memoriaPieles.pielesDe("AMI").stream()
                .filter(p -> p.getRefPiel().equals("AL9S02")).findFirst().orElseThrow().getNombre());
        assertEquals("BLEU", tablaColores.buscar("AMI", "9S4").orElseThrow().getDescripcion());
    }

    /** La etiqueta <input> de la casilla de incluir de la fila i. */
    private static String casilla(String pagina, int i) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<input[^>]*name=\"filas\\[" + i + "]\\.incluir\"[^>]*>").matcher(pagina);
        return m.find() ? m.group() : "";
    }

    private MockHttpServletRequestBuilder revisar(String[]... filas) {
        MockHttpServletRequestBuilder peticion = post("/documentos-corte/sms/revision").session(sesion);
        for (String[] campo : filas) {
            for (int i = 0; i < campo.length; i += 2) {
                peticion.param(campo[i], campo[i + 1]);
            }
        }
        return peticion;
    }

    private static String[] fila(int i, String referencia, String codigo, String color, String muestras) {
        String p = "filas[" + i + "].";
        return new String[] {p + "incluir", "true", "_" + p + "incluir", "on", p + "hoja", "SMS",
                p + "fila", String.valueOf(i + 2), p + "referencia", referencia, p + "codigoColor", codigo,
                p + "descripcionColor", color, p + "muestras", muestras, p + "piel", "ALPACA",
                p + "colorPiel", color, p + "forro", "STILLA"};
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
