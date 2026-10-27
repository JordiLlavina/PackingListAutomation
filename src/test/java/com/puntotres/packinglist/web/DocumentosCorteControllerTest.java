package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.testutil.FotosDePrueba;
import com.puntotres.packinglist.testutil.ExcelDePrueba;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

/**
 * El flujo web de los documentos del corte con los beans reales. Cada test
 * usa referencias propias: la memoria de pieles es la misma base de datos en
 * memoria para toda la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentosCorteControllerTest {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArchivoTemporadas archivoTemporadas;

    @Autowired
    private MemoriaPieles memoria;

    private final MockHttpSession sesion = new MockHttpSession();

    @AfterEach
    void borrarLoDeLaSesion() throws Exception {
        mvc.perform(get("/documentos-corte/nuevo").session(sesion));
    }

    @Test
    void elMenuEnlazaLosDocumentosDelCorte() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(content().string(containsString("href=\"/documentos-corte\"")));
    }

    /**
     * Arranca en el excel de producción, que vale para todos los clientes:
     * ninguno sale apagado desde el servidor. Cuáles tienen lector de pedido
     * va en la opción, y el script apaga los demás al pasar al excel de pedido.
     */
    @Test
    void laEntradaArrancaEnElExcelDeProduccionConTodosLosClientes() throws Exception {
        String pagina = mvc.perform(get("/documentos-corte"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int selector = pagina.indexOf("id=\"selectorOrigen\"");
        assertTrue(selector > 0 && selector < pagina.indexOf("class=\"tarjeta\""),
                "los dos botones van arriba del todo");
        assertTrue(pagina.contains("Excel Producción de Puntotres")
                && pagina.contains("Excel de Pedido de Cliente"), "los dos orígenes");
        assertTrue(pagina.contains("id=\"origen\" name=\"origen\" value=\"PRODUCCION\""),
                "el de producción, por defecto");
        assertTrue(pagina.contains("id=\"produccion\" name=\"produccion\""), "el excel de producción");
        assertTrue(!pagina.matches("(?s).*<option[^>]*disabled.*"), "ningún cliente apagado de entrada");
        assertTrue(pagina.matches("(?s).*<option value=\"ACKERMANN\"[^>]*data-pedido=\"false\".*"), pagina);
        assertTrue(pagina.matches("(?s).*<option value=\"APC\"[^>]*data-pedido=\"true\"[^>]*"
                + "data-temporadas=\"true\".*"), pagina);
    }

    @Test
    void laEntradaVaEnDosIslasConElIconoDePielYLaCarpetaYElZipEnUnaFila() throws Exception {
        String pagina = mvc.perform(get("/documentos-corte"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(pagina.contains("class=\"icono-titulo\"") && !pagina.contains("✂"), "icono de piel");
        int pedido = pagina.indexOf("id=\"cliente\"");
        int fotos = pagina.indexOf("class=\"tarjeta isla-fotos\"");
        assertTrue(pedido > 0 && fotos > pedido, "cliente y temporada en una isla, las fotos en otra");
        String islaFotos = pagina.substring(fotos);
        assertTrue(!islaFotos.substring(0, islaFotos.indexOf("</div>")).contains("<h3"),
                "la isla de las fotos no lleva título");
        assertTrue(islaFotos.contains("class=\"fila-fotos\"") && islaFotos.contains("id=\"carpeta\"")
                && islaFotos.contains("id=\"fotos\""), "carpeta y zip en la misma fila");
        assertTrue(islaFotos.contains("<strong>Estructura</strong>: Una carpeta con el nombre de la "
                + "temporada"), islaFotos);
        // Las fotos son opcionales y la pantalla lo dice antes de subir nada.
        assertTrue(islaFotos.contains("Carpeta de fotos de la temporada (opcional)")
                && islaFotos.contains("Sin fotos salen solo las órdenes de corte"), islaFotos);
    }

    @Test
    void unClienteGenericoNoPasaConElExcelDePedido() throws Exception {
        mvc.perform(cargar("ACKERMANN", "H26", pedidoAmi("ULL100"), zipFotos("ULL100")))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("usa el excel de producción")))
                .andExpect(flash().attribute("origen", "PEDIDO"));
    }

    // --- Excel de producción ---

    /**
     * Con el excel de producción real de APC: la tabla sale con la piel y su
     * color escritos en él, sale una orden por referencia y color con la
     * cantidad del taller y, como hay fotos, el Word de fotos, igual que con
     * el excel de pedido. La memoria de pieles no se escribe.
     */
    @Test
    void conElExcelDeProduccionLaTablaSaleRellenaYSalenLasOrdenesYElWordDeFotos() throws Exception {
        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("produccion", "prod.xlsx", XLSX, produccionApc()))
                        .file(new MockMultipartFile("fotos", "S27.zip", "application/zip", zipFotos("F67008")))
                        .param("origen", "PRODUCCION").param("cliente", "APC")
                        .param("temporada", "SPRING 27").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        String tabla = mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(tabla.contains("18</span> referencias"), tabla.substring(0, 800));
        assertTrue(tabla.contains("value=\"ALPACA\"") && tabla.contains("value=\"NOBILE\""), "las pieles escritas");
        assertTrue(tabla.contains("value=\"AMARILLO CLARO\"") && tabla.contains("value=\"GRIS GUIJARRO\""),
                "el bicolor: la piel principal y su combinación");
        assertTrue(tabla.contains("value=\"520\""), "la cantidad del taller del negro de PXCBC-F67008");
        assertTrue(tabla.contains("falta el forro"), "la ayuda del excel de producción");

        esperarFotos();
        mvc.perform(post("/documentos-corte/generar").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/resultados"));

        String resultados = mvc.perform(get("/documentos-corte/resultados").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(resultados.contains("29 hojas"), resultados);
        // F67008 es un modelo con cuatro referencias (cuatro pieles): sale una vez, con sus dos fotos.
        assertTrue(resultados.contains("Fotos de artículo (1 modelo, 2 fotos)"), resultados);
        assertTrue(resultados.contains("Fotos Artículos.docx"), resultados);
        assertEquals("", memoria.buscar("APC", "PXCEU", "PXCEU-F67008").nombrePiel(),
                "el excel de producción no escribe la memoria de pieles");
    }

    /** La prensa del excel de AMI sale marcada en la tabla y en sus propias hojas, con PRENSA al final. */
    @Test
    void conElExcelDeProduccionLaPrensaVaEnSusPropiasHojas() throws Exception {
        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("produccion", "prod.xlsx", XLSX, produccion("PROD_AMI_SS27.xlsx")))
                        .param("origen", "PRODUCCION").param("cliente", "AMI")
                        .param("temporada", "SS27").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("class=\"marca-prensa\"")));

        mvc.perform(post("/documentos-corte/generar").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/resultados"));
        // 12 colores de venta y 6 de prensa.
        mvc.perform(get("/documentos-corte/resultados").session(sesion))
                .andExpect(content().string(containsString("18 hojas")));
        byte[] excel = mvc.perform(get("/documentos-corte/descargar/Ordenes de corte AMI SS27.xlsx")
                        .session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        List<String> hojas = ExcelDePrueba.nombresDeHoja(ExcelDePrueba.abrir(excel));
        assertTrue(hojas.contains("ULL772.AL245 - NEGRO") && hojas.contains("ULL772.AL245 - NEGRO PRENSA"),
                hojas.toString());
    }

    @Test
    void conElExcelDeProduccionPasaUnClienteGenerico() throws Exception {
        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("produccion", "prod.xlsx", XLSX, produccionApc()))
                        .param("origen", "PRODUCCION").param("cliente", "ACKERMANN")
                        .param("temporada", "SS27").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        // Sin regla para partirla, la referencia entera es el modelo.
        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("PXCBC-F67008")))
                .andExpect(content().string(containsString("value=\"ALPACA\"")));
    }

    /** La temporada guardada solo pone el nombre: su excel de pedido no se lee. */
    @Test
    void conElExcelDeProduccionLaTemporadaGuardadaSoloPoneElNombre() throws Exception {
        Long id = archivoTemporadas.guardar(null, "APC", "SP27-GUARDADA", "pedido.xlsx",
                "no es un excel".getBytes()).getId();

        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("produccion", "prod.xlsx", XLSX, produccionApc()))
                        .param("origen", "PRODUCCION").param("cliente", "APC")
                        .param("temporadaGuardadaId", String.valueOf(id)).session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("SP27-GUARDADA")));
    }

    @Test
    void conElExcelDeProduccionSinFicheroOConElPedidoNoPasa() throws Exception {
        mvc.perform(multipart("/documentos-corte/cargar")
                        .param("origen", "PRODUCCION").param("cliente", "APC")
                        .param("temporada", "SPRING 27").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("Sube el excel de producción")))
                .andExpect(flash().attribute("origen", "PRODUCCION"));

        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("produccion", "pedido.xlsx", XLSX, pedidoAmi("ULL120")))
                        .param("origen", "PRODUCCION").param("cliente", "AMI")
                        .param("temporada", "H26").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("excel de producción")));

        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("produccion", "prod.xlsx", XLSX, produccionApc()))
                        .param("origen", "PRODUCCION").param("cliente", "APC").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("Falta el nombre de la temporada")));
    }

    /**
     * Las fotos son opcionales: sin ellas se pasa a la tabla, se genera solo el
     * documento de órdenes de corte y las dos pantallas lo dicen a la vista, no
     * dentro de los avisos plegables.
     */
    @Test
    void sinFotosSeGeneraSoloElDocumentoDeOrdenes() throws Exception {
        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("pedido", "pedido.xlsx", XLSX, pedidoAmi("ULL101")))
                        // Lo que manda el selector de carpeta cuando no se ha elegido ninguna.
                        .file(new MockMultipartFile("carpeta", "", "application/octet-stream", new byte[0]))
                        .param("cliente", "AMI").param("temporada", "H26").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        String tabla = mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(tabla.contains("id=\"avisoSinFotos\""), "la notificación de que no hay fotos");
        assertTrue(tabla.contains("No se han adjuntado fotos, así que solo se genera el documento de "
                + "órdenes de corte"), tabla.substring(tabla.indexOf("fila-progreso")));
        assertTrue(!tabla.contains("id=\"progresoFotos\""), "sin fotos no hay progreso que enseñar");
        // El botón de generar no se queda apagado esperando fotos que no hay.
        assertTrue(!tabla.contains("id=\"botonGenerar\" disabled"), "el botón de generar, encendido");

        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].nombrePiel", "Box calf")
                        .param("filas[0].bolsos[0]", "10"))
                .andExpect(redirectedUrl("/documentos-corte/resultados"));

        String resultados = mvc.perform(get("/documentos-corte/resultados").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(resultados.contains("id=\"avisoSinFotos\""), resultados);
        assertTrue(resultados.contains("Ordenes de corte AMI H26.xlsx"), resultados);
        assertTrue(resultados.contains("No se han adjuntado fotos de la temporada."),
                resultados.substring(resultados.indexOf("Fotos de Artículo")));

        byte[] excel = mvc.perform(get("/documentos-corte/descargar/Ordenes de corte AMI H26.xlsx")
                        .session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals('P', excel[0]);
        assertEquals('K', excel[1]);
        // Ningún Word de fotos.
        mvc.perform(get("/documentos-corte/descargar/AMI - H26 - Fotos Artículos.docx").session(sesion))
                .andExpect(status().isNotFound());
    }

    @Test
    void unaCarpetaSubidaSinComprimirLlevaALaTablaConSusFotos() throws Exception {
        // El navegador manda cada fichero con su ruta relativa como nombre.
        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("pedido", "pedido.xlsx", XLSX, pedidoAmi("ULL110")))
                        .file(new MockMultipartFile("carpeta", "H26/ULL110.AL103/b.png", "image/png",
                                FotosDePrueba.jpeg(300, 200, Color.BLUE)))
                        .file(new MockMultipartFile("carpeta", "H26/ULL110.AL103/a.jpg", "image/jpeg",
                                FotosDePrueba.jpeg(200, 300, Color.RED)))
                        .param("cliente", "AMI").param("temporada", "H26").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("a.jpg")))
                .andExpect(content().string(containsString("b.png")))
                .andExpect(content().string(not(containsString("no es ningún modelo"))));
    }

    @Test
    void laCarpetaYSuZipALaVezNoPasan() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL111"), zipFotos("ULL111"))
                        .file(new MockMultipartFile("carpeta", "H26/ULL111/a.jpg", "image/jpeg",
                                FotosDePrueba.jpeg(20, 20, Color.RED))))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("no los dos")));
    }

    @Test
    void losModelosSinCarpetaNoSeAvisanPorqueYaLoDiceLaTabla() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL112"), zipFotos("ULL112")));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("sin fotos")))
                .andExpect(content().string(not(containsString("sin carpeta de fotos"))));
    }

    @Test
    void sinTemporadaNiExcelNoPasa() throws Exception {
        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("fotos", "H26.zip", "application/zip", zipFotos("ULL102")))
                        .param("cliente", "AMI").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("temporada guardada")));
    }

    @Test
    void cargarLlevaALaTablaDePieles() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL103"), zipFotos("ULL103")))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ULL103")))
                .andExpect(content().string(containsString("AL0103")))
                .andExpect(content().string(containsString("BLACK")))
                .andExpect(content().string(containsString("value=\"10\"")))
                .andExpect(content().string(containsString("VANILLA CREAM")))
                .andExpect(content().string(containsString("a.jpg")))
                .andExpect(content().string(containsString("sin fotos")))
                .andExpect(content().string(containsString("ULL999")))
                .andExpect(content().string(containsString("ULL745")));
    }

    @Test
    void anadirUnaCombinacionConservaLoTecleado() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL104"), zipFotos("ULL104")));

        mvc.perform(post("/documentos-corte/pieles/combinacion").session(sesion)
                        .param("filas[0].nombrePiel", "Box calf"))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("value=\"Box calf\"")))
                .andExpect(content().string(containsString("name=\"filas[0].combinaciones[0]\"")))
                .andExpect(content().string(containsString("Combinación 1")));
    }

    @Test
    void generarDejaLasDescargas() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL105"), zipFotos("ULL105")));
        esperarFotos();

        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].nombrePiel", "Box calf")
                        .param("filas[0].bolsos[0]", "10")
                        .param("filas[0].bolsos[1]", "0")
                        .param("filas[0].fotoPrincipal", "1")
                        .param("filas[1].bolsos[0]", "5"))
                .andExpect(redirectedUrl("/documentos-corte/resultados"));

        String resultados = mvc.perform(get("/documentos-corte/resultados").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Dos apartados, como en los resultados del packing list: órdenes y fotos.
        int ordenes = resultados.indexOf("Ordenes de Corte");
        int fotos = resultados.indexOf("Fotos de Artículo");
        assertTrue(ordenes > 0 && fotos > ordenes, resultados);
        String apartadoOrdenes = resultados.substring(ordenes, fotos);
        String apartadoFotos = resultados.substring(fotos);
        assertTrue(apartadoOrdenes.contains("Ordenes de corte AMI H26.xlsx"), apartadoOrdenes);
        // Un solo Word con todos los modelos, y lo que lleva dentro en un desplegable.
        assertTrue(apartadoFotos.contains("AMI - H26 - Fotos Artículos.docx"), apartadoFotos);
        assertTrue(apartadoFotos.contains("Fotos de artículo (1 modelo, 2 fotos)"), apartadoFotos);
        String desplegable = apartadoFotos.substring(apartadoFotos.indexOf("id=\"contenidoFotos\""),
                apartadoFotos.indexOf("</details>"));
        assertTrue(desplegable.contains("<td>ULL105</td>") && desplegable.contains("<td>ULL105.AL0103</td>")
                && desplegable.contains("<td>2</td>"), desplegable);
        assertTrue(!apartadoFotos.contains("ZIP"), "con un solo Word no hay zip");
        // Salir de la pantalla, al pie y en gris: menú, pieles y otra temporada.
        String pie = resultados.substring(resultados.indexOf("salidas-pantalla"));
        for (String salida : List.of("href=\"/menu\"", "href=\"/documentos-corte/pieles\"",
                "href=\"/documentos-corte/nuevo\"")) {
            assertTrue(pie.contains(salida), "falta " + salida + " en el pie");
        }
        assertEquals(3, pie.split("boton neutro", -1).length - 1);

        byte[] excel = mvc.perform(get("/documentos-corte/descargar/Ordenes de corte AMI H26.xlsx")
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals('P', excel[0]);
        assertEquals('K', excel[1]);
        String disposicion = mvc.perform(get("/documentos-corte/descargar/AMI - H26 - Fotos Artículos.docx")
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andReturn().getResponse().getHeader("Content-Disposition");
        assertTrue(disposicion.contains("filename*=UTF-8''AMI%20-%20H26%20-%20Fotos%20Art%C3%ADculos.docx"),
                "el nombre con su tilde: " + disposicion);
    }

    @Test
    void volverALasPielesConLaSesionPerdidaLoDiceEnVezDeIrseSinMas() throws Exception {
        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("caducado")));
    }

    @Test
    void laTablaTraeElBotonDeMarcarTodasEnGrisJuntoAlProgresoYLosTextosNuevos() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL116"), zipFotos("ULL116")));

        String pagina = mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andReturn().getResponse().getContentAsString();

        String progreso = pagina.substring(pagina.indexOf("class=\"fila-progreso\""),
                pagina.indexOf("id=\"formPieles\""));
        assertTrue(progreso.contains("id=\"progresoFotos\"") && progreso.contains("id=\"botonTodas\"")
                && progreso.contains("boton neutro"), progreso);
        assertTrue(pagina.contains("Los campos se pueden dejar vacíos. Se puede generar sin fotografías."));
        assertTrue(!pagina.contains("Los nombres valen para todos los colores"));
        assertTrue(pagina.contains("<th>Foto para Orden de Corte</th>")
                && !pagina.contains("Foto principal"), "cabecera de la columna de la foto");
    }

    @Test
    void elBotonDeCombinacionDiceQueEsUnaPiel() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL115"), zipFotos("ULL115")));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString(">+ piel combinación</button>")));
    }

    @Test
    void desdeLosResultadosSeVuelveALasPielesConLoTecleado() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL114"), zipFotos("ULL114")));
        esperarFotos();
        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].nombrePiel", "Box de vuelta"))
                .andExpect(redirectedUrl("/documentos-corte/resultados"));

        String resultados = mvc.perform(get("/documentos-corte/resultados").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertTrue(resultados.contains("href=\"/documentos-corte/pieles\""), resultados);

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Box de vuelta\"")));
    }

    @Test
    void unaFilaDesmarcadaNoSaleEnLosDocumentosYLaCasillaRecuerdaComoQuedo() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL113"), zipFotos("ULL113")));
        esperarFotos();
        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(matchesPattern(
                        "(?s).*name=\"filas\\[0\\]\\.incluir\"[^>]*checked.*")));

        // Una casilla desmarcada no se envía: solo llega su marcador "_".
        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("_filas[0].incluir", "on")
                        .param("filas[1].incluir", "true").param("_filas[1].incluir", "on"))
                .andExpect(redirectedUrl("/documentos-corte/resultados"));

        mvc.perform(get("/documentos-corte/resultados").session(sesion))
                .andExpect(content().string(not(containsString("Fotos Artículos"))));
        byte[] excel = mvc.perform(get("/documentos-corte/descargar/Ordenes de corte AMI H26.xlsx")
                .session(sesion)).andReturn().getResponse().getContentAsByteArray();
        String texto = ExcelDePrueba.texto(ExcelDePrueba.abrir(excel));
        assertTrue(texto.contains("ULL745") && !texto.contains("ULL113"), texto);

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<tbody class=\"referencia fila-excluida\">.*")));
    }

    @Test
    void unNumeroDeBolsosQueNoEsNumeroVuelveALaTablaSinGenerar() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL106"), zipFotos("ULL106")));

        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].bolsos[0]", "diez"))
                .andExpect(redirectedUrl("/documentos-corte/pieles"))
                .andExpect(flash().attribute("errores", org.hamcrest.Matchers.hasItem(containsString("diez"))));
    }

    @Test
    void conTemporadaGuardadaNoHaceFaltaSubirElExcel() throws Exception {
        Long id = archivoTemporadas.guardar(null, "AMI", "H27", "pedido.xlsx", pedidoAmi("ULL107")).getId();

        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("fotos", "H27.zip", "application/zip", zipFotos("ULL107")))
                        .param("cliente", "AMI").param("temporadaGuardadaId", String.valueOf(id))
                        .session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("H27")))
                .andExpect(content().string(containsString("ULL107")));
    }

    @Test
    void unaTemporadaGuardadaDeOtroClienteNoSeUsa() throws Exception {
        Long id = archivoTemporadas.guardar(null, "APC", "E28", "apc.xlsx", pedidoAmi("ULL108")).getId();

        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("fotos", "H26.zip", "application/zip", zipFotos("ULL108")))
                        .param("cliente", "AMI").param("temporadaGuardadaId", String.valueOf(id))
                        .session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("no es de este cliente")));
    }

    @Test
    void losNombresDePielSeRecuerdanParaLaSiguienteVez() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL109", "AL0909"), zipFotos("ULL109")));
        esperarFotos();
        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].nombrePiel", "Vachette recordada")
                        .param("filas[0].forro", "Cabretilla recordada"))
                .andExpect(redirectedUrl("/documentos-corte/resultados"));
        mvc.perform(get("/documentos-corte/nuevo").session(sesion));

        mvc.perform(cargar("AMI", "H27", pedidoAmi("ULL109", "AL0909"), zipFotos("ULL109")));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("value=\"Vachette recordada\"")))
                .andExpect(content().string(containsString("value=\"Cabretilla recordada\"")));
    }

    @Test
    void laTablaEnsenaElColorConSuCodigoYUnColorDePielPorColorQueEmpiezaConElDelBolso()
            throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL114", "AL0914"), zipFotos("ULL114")));

        String tabla = mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(tabla.contains(">001 BLACK<") && tabla.contains(">718 VANILLA CREAM<"), tabla);
        assertTrue(tabla.matches("(?s).*name=\"filas\\[0\\]\\.colores\\[0\\]\\.piel\"[^>]*value=\"BLACK\".*"),
                "la piel principal empieza con la descripción del color del bolso");
        assertTrue(tabla.matches(
                "(?s).*name=\"filas\\[0\\]\\.colores\\[1\\]\\.piel\"[^>]*value=\"VANILLA CREAM\".*"));
        assertTrue(tabla.contains("name=\"filas[0].colores[1].forro\""));
        // El forro y las combinaciones se copian a los modelos del mismo color: por su código.
        assertTrue(tabla.contains("data-color-modelo=\"001\""), tabla);
        assertTrue(tabla.contains("copiarAlMismoColor('input.color-forro'"));
        // El nombre de la piel es uno por referencia: ocupa todas sus filas de color.
        assertTrue(tabla.matches("(?s).*<td rowspan=\"2\">\\s*<input type=\"text\" class=\"c-nombre-piel\".*"),
                tabla);
    }

    @Test
    void losColoresDePielSalenEnSuFichaYSeRecuerdanParaLaSiguienteVez() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL115", "AL0915"), zipFotos("ULL115")));
        esperarFotos();
        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].nombrePiel", "Box calf")
                        .param("filas[0].forro", "Cabretilla")
                        .param("filas[0].colores[0].piel", "NEGRO")
                        .param("filas[0].colores[0].forro", "NOIR")
                        .param("filas[0].colores[1].forro", "CRUDO"))
                .andExpect(redirectedUrl("/documentos-corte/resultados"));

        byte[] excel = mvc.perform(get("/documentos-corte/descargar/Ordenes de corte AMI H26.xlsx")
                .session(sesion)).andReturn().getResponse().getContentAsByteArray();
        var libro = ExcelDePrueba.abrir(excel);
        assertEquals(List.of("ULL115.AL0915 - 001 BLACK", "ULL115.AL0915 - 718 VANILLA CRE", "ULL745.AL0915 - 001 BLACK"),
                ExcelDePrueba.nombresDeHoja(libro));
        String negro = ExcelDePrueba.texto(libro.getSheetAt(0));
        assertTrue(negro.contains("COLOR\nNEGRO") && negro.contains("COLOR\nNOIR"), negro);
        String crudo = ExcelDePrueba.texto(libro.getSheetAt(1));
        assertTrue(crudo.contains("COLOR\nVANILLA CREAM") && crudo.contains("COLOR\nCRUDO"), crudo);

        mvc.perform(get("/documentos-corte/nuevo").session(sesion));
        mvc.perform(cargar("AMI", "H27", pedidoAmi("ULL115", "AL0915"), zipFotos("ULL115")));

        String tabla = mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertTrue(tabla.matches("(?s).*name=\"filas\\[0\\]\\.colores\\[0\\]\\.piel\"[^>]*value=\"NEGRO\".*"));
        assertTrue(tabla.matches("(?s).*name=\"filas\\[0\\]\\.colores\\[0\\]\\.forro\"[^>]*value=\"NOIR\".*"));
        assertTrue(tabla.matches("(?s).*name=\"filas\\[0\\]\\.colores\\[1\\]\\.forro\"[^>]*value=\"CRUDO\".*"));
    }

    @Test
    void laPielGuardadaEnColoresYPielesSaleEnLaTablaDePieles() throws Exception {
        mvc.perform(post("/colores/pieles/fila").param("cliente", "AMI").param("codigo", "AL0920")
                        .param("valor", "Vachette de la tabla"))
                .andExpect(status().isOk());

        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL116", "AL0920"), zipFotos("ULL116")));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("value=\"Vachette de la tabla\"")));
    }

    @Test
    void enApcElColorSeDescribeConLaTablaYDeLosQueNoTienenDescripcionSeAvisa() throws Exception {
        byte[] pedido;
        try (var entrada = getClass().getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            pedido = entrada.readAllBytes();
        }
        mvc.perform(cargar("APC", "FALL26", pedido, zipFotos("F67008")))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        String tabla = mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertTrue(tabla.contains(">LZZ NOIR<"), "la descripción sale de la tabla de colores");
        assertTrue(tabla.contains(">CAB<"), "sin descripción, el código a secas");
        assertTrue(tabla.contains("Colores sin descripción en la Tabla colores Clientes"), tabla);
    }

    @Test
    void siLaSesionHaCaducadoSeDiceEnVezDeVolverALaEntradaSinMas() throws Exception {
        // Rellenar la tabla de una temporada no hace ninguna petición: tras
        // media hora sin actividad la sesión caducaba, y generar mandaba a la
        // entrada sin decir por qué, con todo lo tecleado perdido.
        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].nombrePiel", "Box"))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("caducado")));
        mvc.perform(post("/documentos-corte/pieles/combinacion").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("caducado")));
    }

    @Test
    void laTablaDePielesMantieneVivaLaSesionMientrasEstaAbierta() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL111"), zipFotos("ULL111")));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("MANTENER_SESION_MS")));
    }

    @Test
    void elProgresoDeLasFotosSeConsultaSinRecargar() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL110"), zipFotos("ULL110")));

        mvc.perform(get("/documentos-corte/progreso").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"total\":2")));
    }

    /**
     * Generar ya no espera a las fotos dentro de la petición (el botón se
     * enciende al acabar), así que el test espera como lo haría la pantalla:
     * preguntando por el progreso.
     */
    private void esperarFotos() throws Exception {
        for (int intento = 0; intento < 300; intento++) {
            String estado = mvc.perform(get("/documentos-corte/progreso").session(sesion))
                    .andReturn().getResponse().getContentAsString();
            if (estado.contains("\"terminada\":true")) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("las fotos no han acabado de prepararse en 30 s");
    }

    private MockMultipartHttpServletRequestBuilder cargar(String cliente, String temporada,
                                                         byte[] pedido, byte[] fotos) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/documentos-corte/cargar")
                .file(new MockMultipartFile("pedido", "pedido.xlsx", XLSX, pedido))
                .file(new MockMultipartFile("fotos", temporada + ".zip", "application/zip", fotos))
                .param("cliente", cliente)
                .param("temporada", temporada)
                .session(sesion);
    }

    /** La copia sin fotos del excel de producción real de APC, Spring 27. */
    static byte[] produccionApc() throws IOException {
        return produccion("PROD_APC_SPRING27.xlsx");
    }

    /** Una copia sin fotos de un excel de producción real. */
    static byte[] produccion(String fichero) throws IOException {
        try (var entrada = DocumentosCorteControllerTest.class
                .getResourceAsStream("/ejemplos/corte/" + fichero)) {
            return entrada.readAllBytes();
        }
    }

    /** Un modelo con dos colores y ULL745, que no trae carpeta de fotos. */
    static byte[] pedidoAmi(String modelo) {
        return pedidoAmi(modelo, "AL0103");
    }

    static byte[] pedidoAmi(String modelo, String piel) {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", modelo + "." + piel, "001", "BLACK", "U", "07704 CH", null, null, 4),
                new Fila("SPAIN", modelo + "." + piel, "001", "BLACK", "U", 7665, null, null, 6),
                new Fila("SPAIN", modelo + "." + piel, "718", "VANILLA CREAM", "U", 7665, null, null, 3),
                new Fila("SPAIN", "ULL745." + piel, "001", "BLACK", "U", 7665, null, null, 5));
    }

    /** Dos fotos del modelo y una carpeta que no está en el pedido. */
    static byte[] zipFotos(String modelo) throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("H26/" + modelo + "/b.jpg", FotosDePrueba.jpeg(300, 200, Color.BLUE));
        entradas.put("H26/" + modelo + "/a.jpg", FotosDePrueba.jpeg(200, 300, Color.RED));
        entradas.put("H26/ULL999/x.jpg", FotosDePrueba.jpeg(10, 10, Color.GRAY));
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(entrada.getKey()));
                zip.write(entrada.getValue());
                zip.closeEntry();
            }
        }
        return salida.toByteArray();
    }
}
