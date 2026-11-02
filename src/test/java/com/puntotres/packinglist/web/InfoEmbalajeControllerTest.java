package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.puntotres.packinglist.model.DatosHojaEmbalaje;
import com.puntotres.packinglist.model.DatosHojaEmbalaje.DestinacionHoja;
import com.puntotres.packinglist.persistence.ArchivoHojasEmbalaje;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.HojaEmbalaje;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.testutil.FotosDePrueba;

/**
 * Las pantallas de «Info de embalaje para taller»: cargar el pedido, rellenar
 * el formulario, generar (que guarda y descarga) y volver a una hoja antigua.
 *
 * Lo que de verdad hay que anclar es que lo generado quede GUARDADO tal como
 * se tecleó —la pantalla de antiguos y la importación viven de ese snapshot—
 * y que importar una hoja cuya temporada ya no existe no deje al usuario sin
 * formulario.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InfoEmbalajeControllerTest {

    private static final String TIPO_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String TIPO_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArchivoHojasEmbalaje archivo;

    @Autowired
    private ArchivoTemporadas temporadas;

    @Autowired
    private FotosCatalogo fotos;

    @Autowired
    private CatalogoModelos catalogo;

    private static byte[] recurso(String ruta) throws Exception {
        try (var entrada = InfoEmbalajeControllerTest.class.getResourceAsStream(ruta)) {
            return entrada.readAllBytes();
        }
    }

    private static MockMultipartFile pedidoApc() throws Exception {
        return new MockMultipartFile("pedido", "APC_PEDIDO_SPRING27.xlsx", TIPO_XLSX,
                recurso("/ejemplos/APC_PEDIDO_SPRING27.xlsx"));
    }

    /** Carga el pedido real de APC en una sesión nueva y la devuelve. */
    private MockHttpSession cargarApc() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/info-embalaje/cargar")
                        .file(pedidoApc())
                        .param("cliente", "APC")
                        .param("temporada", "Spring 27")
                        .session(sesion))
                .andExpect(redirectedUrl("/info-embalaje/formulario"));
        return sesion;
    }

    /** El POST de generar con los datos de la foto de Tánger y dos destinaciones. */
    private static MockHttpServletRequestBuilder generar(MockHttpSession sesion, String nota) {
        return generar(sesion, nota, "270", "PXCBC-F67008");
    }

    private static MockHttpServletRequestBuilder generar(MockHttpSession sesion, String nota,
                                                         String numeroEnvio, String referencia) {
        return generar(sesion, nota, numeroEnvio, referencia, "TÁNGER");
    }

    private static MockHttpServletRequestBuilder generar(MockHttpSession sesion, String nota,
                                                         String numeroEnvio, String referencia, String taller) {
        return post("/info-embalaje/generar").session(sesion)
                .param("taller", taller)
                .param("numeroEnvio", numeroEnvio)
                .param("nota", nota)
                .param("referencia", referencia)
                .param("codigoColor", "LZZ")
                .param("descripcionColor", "NOIR")
                .param("nombreBolso", "SAC LE NEIGE")
                .param("codigoModelo", "F67008")
                .param("medidaCaja", "60x40x40")
                .param("unidadesPorCaja", "10")
                .param("bolsaTela", "BOLSA TELA BEIGE 40x50")
                .param("etiquetaInterior", "PARIS")
                .param("instruccionEspecial", "GRABADO LOGO NIKEL")
                .param("destinos[0].destino", "JAPAN")
                .param("destinos[0].pedido", "4100132915")
                .param("destinos[0].cantidad", "120")
                .param("destinos[1].destino", "KOREA")
                .param("destinos[1].pedido", "4100132916")
                .param("destinos[1].cantidad", "80")
                .param("repartoCajas", "12 cajas de 10\n8 cajas de 10")
                .param("comentarios", "Cuidado con las asas")
                .param("enlaceVideo", "  https://youtu.be/guia  ");
    }

    private Long idGenerado(MockHttpServletResponse respuesta) {
        return Long.valueOf(respuesta.getHeader("X-Hoja-Id"));
    }

    @Test
    void laEntradaOfreceClientesYSoloAmiYApcDisponibles() throws Exception {
        String html = mvc.perform(get("/info-embalaje"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("value=\"AMI\""), html);
        assertTrue(html.contains("value=\"APC\""), html);
        // Los genéricos salen, pero apagados y «en desarrollo».
        assertTrue(html.contains("en desarrollo"), "algún cliente sin lector de pedido");
        assertTrue(html.matches("(?s).*<option[^>]*value=\"AMI\"(?![^>]*disabled)[^>]*>.*"), "AMI encendido");
        assertTrue(html.matches("(?s).*<option[^>]*value=\"APC\"(?![^>]*disabled)[^>]*>.*"), "APC encendido");
        assertTrue(html.contains("href=\"/info-embalaje/antiguos\">Importar Antiguos Envios<"),
                "botón Importar Antiguos Envios");
        // Los comentarios tipo se mantienen desde aquí, no desde un panel del menú.
        assertTrue(html.contains("href=\"/comentarios-embalaje\">Editar Comentarios de Embalaje<"),
                "botón Editar Comentarios de Embalaje");
        assertTrue(html.indexOf("Importar Antiguos Envios") < html.indexOf("Editar Comentarios de Embalaje"),
                "el de comentarios va a la derecha del de importar");
        assertTrue(html.contains("href=\"/temporadas\""), "lápiz a las temporadas");
    }

    @Test
    void cargarConElExcelRealLlevaAlFormularioConLosArticulos() throws Exception {
        MockHttpSession sesion = cargarApc();

        String html = mvc.perform(get("/info-embalaje/formulario").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("PXCBC-F67008"), "la referencia en el desplegable");
        assertTrue(html.contains("4100132915"), "el Document d'achat en los datos del artículo");
        // Los artículos van al navegador como JSON con sus campos, no solo la etiqueta.
        assertTrue(html.contains("\"referencia\":\"PXCBC-F67008\""), "artículos serializados");
        assertTrue(html.contains("href=\"/comentarios-embalaje\""), "lápiz a los comentarios");
        assertTrue(html.contains("<datalist id=\"talleres\""), "sugerencias de taller");
        assertTrue(html.contains("href=\"/info-embalaje\""), "empezar de nuevo");
        // Cambiar de artículo vacía lo que es de cada artículo (reparto y enlace)
        // y no el comentario; el lápiz abre los comentarios en otra pestaña.
        assertTrue(html.contains("const POR_ARTICULO = ['repartoCajas', 'enlaceVideo']"),
                "reparto y enlace se vacían al cambiar de artículo");
        assertTrue(html.contains("target=\"_blank\" rel=\"noopener\""), "lápiz en otra pestaña");
    }

    @Test
    void elFormularioCalculaElRepartoYAvisaCuandoSeEditaAMano() throws Exception {
        String html = mvc.perform(get("/info-embalaje/formulario").session(cargarApc()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // El cálculo vive en su propio estático (con el hash del contenido en el nombre).
        assertTrue(html.matches("(?s).*<script src=\"/reparto-cajas[^\"]*\\.js\"></script>.*"),
                "carga reparto-cajas.js");
        assertTrue(html.contains("textoReparto(filasDeLaTabla(), unidadesPorCaja.value)"),
                "el formulario usa la función del cálculo");
        assertTrue(html.contains("id=\"repartoManual\""), "el aviso de modo manual");
        assertTrue(html.contains("Editado a mano: ya no sigue a la tabla"), "su texto");
        assertTrue(html.matches("(?s).*<button[^>]*id=\"volverACalcular\"[^>]*>Volver a calcular</button>.*"),
                "el botón para volver al cálculo");
        assertTrue(html.contains("Se calcula con la tabla y las unidades por caja"), "la ayuda del modo automático");
    }

    @Test
    void elDesplegableDeArticulosAlineaElNombreDelBolso() throws Exception {
        String html = mvc.perform(get("/info-embalaje/formulario").session(cargarApc()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("<select id=\"articulo\" class=\"embalaje-articulo-select\">"),
                "el desplegable lleva la clase de letra de ancho fijo");
        // Cada opción con nombre: «referencia · color» rellenado con espacios duros
        // hasta la más larga y luego «— nombre», así que el «—» cae en la misma columna.
        java.util.regex.Matcher opcion = java.util.regex.Pattern
                .compile("<option value=\"\\d+\"[^>]*>([^<]*)</option>").matcher(html);
        java.util.Set<Integer> columnas = new java.util.HashSet<>();
        int conNombre = 0;
        while (opcion.find()) {
            String texto = opcion.group(1);
            int raya = texto.indexOf("\u00A0—\u00A0");
            if (raya >= 0) {
                conNombre++;
                columnas.add(raya);
            }
        }
        assertTrue(conNombre > 1, "hay opciones con nombre de bolso");
        assertEquals(1, columnas.size(), "el nombre empieza en la misma columna en todas: " + columnas);
        assertTrue(html.matches("(?s).*<option value=\"\\d+\"[^>]*>[^<]*\u00A0\u00A0[^<]*</option>.*"),
                "alguna opción va rellenada con espacios duros");
    }

    @Test
    void elFormularioSinPedidoCargadoVuelveALaEntrada() throws Exception {
        mvc.perform(get("/info-embalaje/formulario").session(new MockHttpSession()))
                .andExpect(redirectedUrl("/info-embalaje"));
    }

    @Test
    void cargarSinExcelNiTemporadaVuelveConError() throws Exception {
        mvc.perform(multipart("/info-embalaje/cargar").param("cliente", "APC"))
                .andExpect(redirectedUrl("/info-embalaje"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("error", containsString("Falta el excel de pedido")));
    }

    @Test
    void generarGuardaLaHojaYDevuelveElWord() throws Exception {
        MockHttpSession sesion = cargarApc();

        MockHttpServletResponse respuesta = mvc.perform(generar(sesion, "12662"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(TIPO_DOCX))
                .andExpect(header().string("X-Numero-Envio", "270"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("PXCBC-F67008")))
                .andReturn().getResponse();

        // Es un Word de verdad, no solo bytes con la cabecera puesta.
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(respuesta.getContentAsByteArray()))) {
            assertTrue(doc.getTables().size() > 0);
        }

        Long id = idGenerado(respuesta);
        assertTrue(archivo.todas().stream().anyMatch(hoja -> hoja.id().equals(id)
                && hoja.referencia().equals("PXCBC-F67008") && hoja.numeroEnvio() == 270));

        DatosHojaEmbalaje guardada = archivo.datosDe(id).orElseThrow();
        assertEquals("APC", guardada.cliente());
        assertEquals("Spring 27", guardada.temporada());
        assertEquals(LocalDate.now(), guardada.fecha());
        assertEquals("https://youtu.be/guia", guardada.enlaceVideo(), "el enlace, recortado");
        assertEquals(200, guardada.totalUnidades());

        mvc.perform(get("/info-embalaje/antiguos").param("id", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("12662")))
                .andExpect(content().string(containsString("GRABADO LOGO NIKEL")))
                .andExpect(content().string(containsString("Descargar Word")));
    }

    @Test
    void lasFilasDeDestinacionVaciasSeDescartan() throws Exception {
        MockHttpSession sesion = cargarApc();

        MockHttpServletResponse respuesta = mvc.perform(post("/info-embalaje/generar").session(sesion)
                        .param("numeroEnvio", "271")
                        .param("referencia", "PXCBC-F67008")
                        .param("codigoColor", "LZZ")
                        .param("destinos[0].destino", "JAPAN")
                        .param("destinos[0].pedido", "4100132915")
                        .param("destinos[0].cantidad", "")
                        .param("destinos[1].destino", "")
                        .param("destinos[1].pedido", "")
                        .param("destinos[1].cantidad", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        List<DestinacionHoja> destinaciones = archivo.datosDe(idGenerado(respuesta)).orElseThrow().destinaciones();
        assertEquals(1, destinaciones.size());
        assertEquals(new DestinacionHoja("JAPAN", "4100132915", 0), destinaciones.get(0),
                "una cantidad vacía es un cero, no un error");
    }

    @Test
    void generarSinPedidoCargadoNoGuardaNada() throws Exception {
        int antes = archivo.todas().size();
        mvc.perform(generar(new MockHttpSession(), "sin sesión"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("vuelve a cargar el pedido")));
        assertEquals(antes, archivo.todas().size());
    }

    @Test
    void importarPrerrellenaElFormulario() throws Exception {
        // El pedido sale de una temporada guardada, que sigue existiendo al importar.
        TemporadaGuardada temporada = temporadas.guardar(null, "APC", "Embalaje " + System.nanoTime(),
                "APC_PEDIDO_SPRING27.xlsx", recurso("/ejemplos/APC_PEDIDO_SPRING27.xlsx"));
        try {
            MockHttpSession sesion = new MockHttpSession();
            mvc.perform(multipart("/info-embalaje/cargar")
                            .param("cliente", "APC")
                            .param("temporadaGuardadaId", String.valueOf(temporada.getId()))
                            .session(sesion))
                    .andExpect(redirectedUrl("/info-embalaje/formulario"));
            Long id = idGenerado(mvc.perform(generar(sesion, "NOTA-IMPORT-77"))
                    .andExpect(status().isOk()).andReturn().getResponse());
            assertEquals(temporada.getId(), archivo.hoja(id).map(HojaEmbalaje::getTemporadaId).orElseThrow());

            MockHttpSession otra = new MockHttpSession();
            mvc.perform(post("/info-embalaje/importar").param("id", String.valueOf(id)).session(otra))
                    .andExpect(redirectedUrl("/info-embalaje/formulario"));
            String html = mvc.perform(get("/info-embalaje/formulario").session(otra))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertTrue(html.contains("value=\"NOTA-IMPORT-77\""), "la nota de la hoja importada");
            assertTrue(html.contains("value=\"BOLSA TELA BEIGE 40x50\""), "la bolsa de tela");
            assertTrue(html.contains("value=\"4100132916\""), "sus destinaciones, no las del pedido");
            // El pedido de la temporada se ha vuelto a cargar: hay más artículos que el importado.
            assertTrue(html.contains("PXCDS-F67008") || html.split("<option").length > 5,
                    "el desplegable trae el pedido entero");
            assertTrue(html.matches("(?s).*<option[^>]*selected[^>]*>PXCBC-F67008 · LZZ.*"),
                    "el artículo importado, preseleccionado");
        } finally {
            temporadas.borrar(temporada.getId());
        }
    }

    @Test
    void importarUnaHojaSinTemporadaAbreElFormularioConSuArticulo() throws Exception {
        DatosHojaEmbalaje datos = new DatosHojaEmbalaje("APC", "A.P.C.", "Fall 26", "FEZ", 99,
                "nota huérfana", LocalDate.of(2026, 1, 15), "PXTEST-H99999", "CAW", "CAMEL", "SAC PRUEBA",
                "H99999", "60x40x30", 6, "BOLSA SIN TEMPORADA", "", "",
                List.of(new DestinacionHoja("JAPAN", "4100999999", 12)), "", "", "");
        Long id = archivo.guardar(datos, 999999L).getId();

        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(post("/info-embalaje/importar").param("id", String.valueOf(id)).session(sesion))
                .andExpect(redirectedUrl("/info-embalaje/formulario"));

        mvc.perform(get("/info-embalaje/formulario").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PXTEST-H99999")))
                .andExpect(content().string(containsString("value=\"BOLSA SIN TEMPORADA\"")))
                .andExpect(content().string(containsString("4100999999")))
                .andExpect(content().string(not(containsString("class=\"alerta error\""))));
    }

    @Test
    void elSiguienteNumeroDeEnvioSubeTrasGenerar() throws Exception {
        MockHttpSession sesion = cargarApc();
        mvc.perform(generar(sesion, "contador")).andExpect(status().isOk());

        // El contador es del taller de la hoja generada (TÁNGER), y la
        // pantalla recibe el de cada taller para proponerlo al teclearlo.
        int siguiente = archivo.siguienteNumeroEnvio("TÁNGER");
        assertTrue(siguiente >= 271);
        mvc.perform(get("/info-embalaje/formulario").session(sesion))
                // Thymeleaf escapa en el JavaScript lo que no es ASCII: la Á va como escape unicode.
                .andExpect(content().string(containsString("\"T" + "\\" + "u00C1NGER\":" + siguiente)));
    }

    @Test
    void generarSinNumeroUsaElSiguienteDeSuTaller() throws Exception {
        MockHttpSession sesion = cargarApc();
        int esperado = archivo.siguienteNumeroEnvio("EMBT Ubrique ctrl");

        mvc.perform(generar(sesion, "sin número", "", "PXCBC-F67008", "EMBT Ubrique ctrl"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Numero-Envio", String.valueOf(esperado)))
                .andExpect(header().string("X-Siguiente-Numero", String.valueOf(esperado + 1)));
    }

    @Test
    void elSiguienteNumeroLoDiceElContadorYNoElEnvioMasUno() throws Exception {
        // Un envío alto guardado antes y luego se repite el 270 (importado):
        // el siguiente libre no es el 271 sino el del contador de su taller.
        MockHttpSession sesion = cargarApc();
        mvc.perform(generar(sesion, "alto", "5000", "PXCBC-F67008")).andExpect(status().isOk());

        MockHttpServletResponse respuesta = mvc.perform(generar(sesion, "repetido"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Numero-Envio", "270"))
                .andReturn().getResponse();

        int siguiente = Integer.parseInt(respuesta.getHeader("X-Siguiente-Numero"));
        assertEquals(archivo.siguienteNumeroEnvio("TÁNGER"), siguiente);
        assertTrue(siguiente >= 5001, "el 271 podría estar ya usado: manda el contador");
    }

    @Test
    void generarSinReferenciaNoGuardaNada() throws Exception {
        MockHttpSession sesion = cargarApc();
        int antes = archivo.todas().size();
        mvc.perform(generar(sesion, "sin artículo", "270", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Elige o escribe el artículo")));
        assertEquals(antes, archivo.todas().size());

        String html = mvc.perform(get("/info-embalaje/formulario").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.matches("(?s).*<input[^>]*\\brequired\\b[^>]*name=\"referencia\"[^>]*>.*")
                || html.matches("(?s).*<input[^>]*name=\"referencia\"[^>]*\\brequired\\b[^>]*>.*"),
                "la referencia es obligatoria también en el navegador");
    }

    @Test
    void importarSinTemporadaTraeLaFotoDelCatalogoYLaHojaLaImprime() throws Exception {
        catalogo.asegurarModelo("APC", "H99997", "");
        ResumenFoto foto = fotos.guardar("APC", "H99997", "principal.jpg", FotosDePrueba.jpeg(40, 40, Color.RED));
        DatosHojaEmbalaje datos = new DatosHojaEmbalaje("APC", "A.P.C.", "Fall 26", "FEZ", 98, "con foto",
                LocalDate.of(2026, 1, 15), "PXTEST-H99997", "CAW", "CAMEL", "SAC FOTO", "H99997", "", null,
                "", "", "", List.of(new DestinacionHoja("JAPAN", "4100999998", 6)), "", "", "");
        Long id = archivo.guardar(datos, 999999L).getId();

        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(post("/info-embalaje/importar").param("id", String.valueOf(id)).session(sesion))
                .andExpect(redirectedUrl("/info-embalaje/formulario"));
        String html = mvc.perform(get("/info-embalaje/formulario").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("name=\"fotoId\" value=\"" + foto.id() + "\""), "el fotoId del catálogo");
        assertTrue(html.contains("\"fotoId\":" + foto.id()), "también en el artículo del desplegable");

        // Generada desde el formulario, la hoja lleva la foto y el QR, como al
        // descargarla desde antiguos.
        byte[] word = mvc.perform(post("/info-embalaje/generar").session(sesion)
                        .param("referencia", "PXTEST-H99997")
                        .param("codigoModelo", "H99997")
                        .param("fotoId", String.valueOf(foto.id()))
                        .param("enlaceVideo", "https://youtu.be/guia"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word))) {
            assertEquals(2, doc.getAllPictures().size(), "foto del modelo + QR");
        }
    }

    @Test
    void descargarUnaAntiguaRegeneraElWord() throws Exception {
        MockHttpSession sesion = cargarApc();
        Long id = idGenerado(mvc.perform(generar(sesion, "para regenerar"))
                .andExpect(status().isOk()).andReturn().getResponse());

        byte[] word = mvc.perform(get("/info-embalaje/antiguos/" + id + "/docx"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(TIPO_DOCX))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word))) {
            assertTrue(doc.getTables().size() > 0);
        }

        mvc.perform(get("/info-embalaje/antiguos/987654321/docx")).andExpect(status().isNotFound());
    }

    @Test
    void unaHojaQueNoExisteNoRompeLaPantallaDeAntiguos() throws Exception {
        // Un enlace viejo (la hoja se borró de la base de datos) no tiene que
        // dejar la pantalla en blanco: se dice y se puede elegir otra.
        mvc.perform(get("/info-embalaje/antiguos").param("id", "987654321"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ya no existe")));
    }
}
