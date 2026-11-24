package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.FicheroExcel;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;

/**
 * La carga de modelos por sus dos pantallas: elegir el origen y revisar el
 * lote en la batería hasta cargarlo. Las reglas de qué se escribe están en
 * CargadorModelosTest; aquí, que las rutas las encadenen bien y que la sesión
 * guarde el lote.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CargaModelosControllerTest {

    private static final String TIPO_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private CatalogoModelos catalogo;
    @Autowired
    private FotosCatalogo fotosCatalogo;
    @Autowired
    private ArchivoTemporadas temporadas;

    private static byte[] recurso(String ruta) throws Exception {
        try (var entrada = CargaModelosControllerTest.class.getResourceAsStream(ruta)) {
            return entrada.readAllBytes();
        }
    }

    private static byte[] jpeg() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB), "jpg", salida);
        return salida.toByteArray();
    }

    private JsonNode estado(MockHttpSession sesion) throws Exception {
        return json.readTree(mvc.perform(get("/carga-modelos/estado").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void esperarFotos(MockHttpSession sesion) throws Exception {
        long limite = System.currentTimeMillis() + 30_000;
        while (!estado(sesion).path("fotos").path("terminadas").asBoolean()) {
            assertTrue(System.currentTimeMillis() < limite, "las fotos no han acabado en 30 s");
            Thread.sleep(50);
        }
    }

    @Test
    void elMenuLlevaALaCargaDeModelosDebajoDeLasTemporadas() throws Exception {
        String menu = mvc.perform(get("/menu")).andReturn().getResponse().getContentAsString();

        assertTrue(menu.contains("href=\"/carga-modelos\""));
        assertTrue(menu.indexOf("href=\"/temporadas\"") < menu.indexOf("href=\"/carga-modelos\""));
        assertTrue(menu.indexOf("href=\"/carga-modelos\"") < menu.indexOf("href=\"/catalogo\""),
                "justo debajo de las temporadas");
    }

    @Test
    void laPantallaDeOrigenOfreceLosCincoOrigenes() throws Exception {
        mvc.perform(get("/carga-modelos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-origen=\"TEMPORADA\"")))
                .andExpect(content().string(containsString("data-origen=\"SMS\"")))
                .andExpect(content().string(containsString("data-origen=\"PRODUCCION\"")))
                .andExpect(content().string(containsString("data-origen=\"GRUPO\"")))
                .andExpect(content().string(containsString("data-origen=\"MANUAL\"")))
                .andExpect(content().string(containsString("Ir a Cargar Modelos")))
                .andExpect(content().string(containsString("Descargar el esqueleto de carpetas")));
    }

    @Test
    void sinCargaLaBateriaVuelveAlOrigenYSusPeticionesLoDicen() throws Exception {
        mvc.perform(get("/carga-modelos/bateria")).andExpect(redirectedUrl("/carga-modelos"));
        mvc.perform(get("/carga-modelos/estado"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.mensaje").value(containsString("ya no está")));
    }

    @Test
    void anadirModeloRecorreLaBateriaHastaElCatalogo() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/carga-modelos/origen").param("origen", "MANUAL").param("cliente", "AMI")
                        .session(sesion))
                .andExpect(redirectedUrl("/carga-modelos/bateria"));
        mvc.perform(get("/carga-modelos/bateria").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cargar modelos al Sistema")));

        JsonNode lote = estado(sesion);
        assertEquals("MANUAL", lote.path("origen").asText());
        int id = lote.path("modelos").get(0).path("id").asInt();
        assertTrue(lote.path("modelos").get(0).path("faltan").size() > 0, "en blanco, le faltan los obligatorios");

        // Sin los obligatorios no se acepta.
        mvc.perform(post("/carga-modelos/modelos/{id}/estado", id).param("estado", "ACEPTADO").session(sesion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("Todavía no se puede aceptar")));

        mvc.perform(put("/carga-modelos/modelos/{id}", id).session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cliente":"AMI","codigo":"cmw700","nombre":"Bolso por pantalla","tipo":"BOLSO",
                                 "etapa":"SMS","temporadaId":null,
                                 "pieles":[{"referencia":"CMW700.AL9100","nombrePiel":"napa","forro":"badana",
                                            "colores":[{"codigo":"9100","descripcion":"Azul"}]}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo.codigo").value("CMW700"))
                .andExpect(jsonPath("$.modelo.faltan", hasSize(0)))
                .andExpect(jsonPath("$.modelo.pieles[0].nombrePiel").value("NAPA"))
                .andExpect(jsonPath("$.modelo.pieles[0].colores[0].colorPiel").value("AZUL"));

        // Una foto subida desde el panel.
        mvc.perform(multipart("/carga-modelos/modelos/{id}/ficheros", id)
                        .file(new MockMultipartFile("ficheros", "frente.jpg", "image/jpeg", jpeg()))
                        .file(new MockMultipartFile("ficheros", "notas.txt", "text/plain", "x".getBytes()))
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo.fotos", hasSize(1)))
                .andExpect(jsonPath("$.modelo.fotos[0].seleccionada").value(true))
                .andExpect(jsonPath("$.modelo.sinFotos").value(false))
                .andExpect(jsonPath("$.avisos[0]").value(containsString("notas.txt")));
        String fotoId = estado(sesion).path("modelos").get(0).path("fotos").get(0).path("id").asText();
        mvc.perform(get("/carga-modelos/modelos/{id}/fotos/{foto}", id, fotoId).session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        mvc.perform(post("/carga-modelos/modelos/{id}/estado", id).param("estado", "ACEPTADO").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuentas.aceptados").value(1));

        // «Añadir otro» deja el anterior en la lista y abre uno en blanco con el mismo cliente.
        mvc.perform(post("/carga-modelos/modelos").param("cliente", "AMI").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lote.modelos", hasSize(2)))
                .andExpect(jsonPath("$.lote.cuentas.pendientes").value(1));

        mvc.perform(post("/carga-modelos/cargar").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.modelosNuevos").value(1))
                .andExpect(jsonPath("$.resumen.fotos").value(1))
                .andExpect(jsonPath("$.lote.modelos", hasSize(1)));

        Modelo guardado = catalogo.modelo("AMI", "CMW700").orElseThrow();
        assertEquals(Etapa.SMS, guardado.getEtapa());
        assertEquals("BADANA", catalogo.articulo("AMI", "CMW700.AL9100").orElseThrow().getForro());
        assertEquals(1, fotosCatalogo.de(guardado.getId()).size());
    }

    @Test
    void elExcelDeProduccionConSuCarpetaMontaElLoteYSuEsqueleto() throws Exception {
        byte[] excel = recurso("/ejemplos/corte/PROD_APC_SPRING27.xlsx");
        byte[] esqueleto = mvc.perform(multipart("/carga-modelos/esqueleto")
                        .file(new MockMultipartFile("excel", "PROD APC SPRING27.xlsx", TIPO_XLSX, excel))
                        .param("origen", "PRODUCCION").param("cliente", "APC"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        List<String> carpetas = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(esqueleto))) {
            for (ZipEntry entrada = zip.getNextEntry(); entrada != null; entrada = zip.getNextEntry()) {
                carpetas.add(entrada.getName());
            }
        }
        assertEquals("PROD APC SPRING27/", carpetas.get(0), "sin temporada, la raíz se llama como el excel");
        assertTrue(carpetas.size() > 2);

        // Se sube el esqueleto con una foto en la carpeta del primer modelo.
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/carga-modelos/origen")
                        .file(new MockMultipartFile("excel", "PROD APC SPRING27.xlsx", TIPO_XLSX, excel))
                        .file(new MockMultipartFile("carpeta", carpetas.get(1) + "frente.jpg", "image/jpeg", jpeg()))
                        .param("origen", "PRODUCCION").param("cliente", "APC").session(sesion))
                .andExpect(redirectedUrl("/carga-modelos/bateria"));
        esperarFotos(sesion);

        JsonNode lote = estado(sesion);
        assertEquals(carpetas.size() - 1, lote.path("modelos").size(), "una carpeta del esqueleto por modelo");
        long conFoto = 0;
        long conNombre = 0;
        for (JsonNode modelo : lote.path("modelos")) {
            conFoto += modelo.path("fotos").size();
            conNombre += modelo.path("nombre").asText().isEmpty() ? 0 : 1;
            assertEquals("PRODUCCION", modelo.path("etapa").asText());
        }
        assertEquals(1, conFoto, "la carpeta del esqueleto se reconoce al volver a subirla");
        assertTrue(conNombre > 0, "el excel de producción trae el nombre del bolso");
        mvc.perform(post("/carga-modelos/aceptar-completos").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuentas.aceptados").value(greaterThan(0)));
    }

    @Test
    void anadirEnGrupoSacaLosModelosDeLaCarpetaYLaTemporadaDeSuRaiz() throws Exception {
        Long temporadaId = temporadas.guardar(null, "KAMI", "CMG-GRUPO", null,
                new FicheroExcel("prod.xlsx", "x".getBytes())).getId();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (String ruta : List.of("cmg-grupo/CMZ800 bolso uno/a.jpg", "cmg-grupo/CMZ801/b.jpg")) {
                zip.putNextEntry(new ZipEntry(ruta));
                zip.write(jpeg());
                zip.closeEntry();
            }
        }
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/carga-modelos/origen")
                        .file(new MockMultipartFile("fotos", "modelos.zip", "application/zip", salida.toByteArray()))
                        .param("origen", "GRUPO").param("cliente", "KAMI").session(sesion))
                .andExpect(redirectedUrl("/carga-modelos/bateria"));
        esperarFotos(sesion);

        JsonNode lote = estado(sesion);
        assertEquals(2, lote.path("modelos").size());
        JsonNode primero = lote.path("modelos").get(0);
        assertEquals("CMZ800", primero.path("codigo").asText());
        assertEquals("bolso uno", primero.path("nombre").asText());
        assertEquals(temporadaId.longValue(), primero.path("temporadaId").asLong(),
                "la carpeta raíz es el nombre de la temporada");
        assertEquals("PROTOTIPO", primero.path("etapa").asText());
        assertEquals(0, lote.path("avisos").size());
    }

    @Test
    void unOrigenQueNoSePuedeLeerVuelveALaPantallaConSuMotivo() throws Exception {
        mvc.perform(multipart("/carga-modelos/origen").param("origen", "TEMPORADA").param("cliente", "AMI"))
                .andExpect(redirectedUrl("/carga-modelos"))
                .andExpect(flash().attribute("error", containsString("temporada guardada")))
                .andExpect(flash().attribute("origen", "TEMPORADA"));
        mvc.perform(multipart("/carga-modelos/origen")
                        .file(new MockMultipartFile("excel", "sms.xlsx", TIPO_XLSX, "x".getBytes()))
                        .param("origen", "SMS").param("cliente", "AMI"))
                .andExpect(redirectedUrl("/carga-modelos"))
                .andExpect(flash().attribute("error", containsString("en desarrollo")));
        mvc.perform(multipart("/carga-modelos/origen").param("origen", "GRUPO").param("cliente", "AMI"))
                .andExpect(redirectedUrl("/carga-modelos"))
                .andExpect(flash().attribute("error", containsString("carpeta")));
        mvc.perform(multipart("/carga-modelos/origen")
                        .file(new MockMultipartFile("excel", "malo.xlsx", TIPO_XLSX, "no es un excel".getBytes()))
                        .param("origen", "PRODUCCION").param("cliente", "APC"))
                .andExpect(redirectedUrl("/carga-modelos"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void laTemporadaGuardadaDaSusModelosYSeVinculaAlCargar() throws Exception {
        Long temporadaId = temporadas.guardar(null, "APC", "CMT-FALL26",
                new FicheroExcel("APC_PEDIDO_FALL26.xlsx", recurso("/ejemplos/APC_PEDIDO_FALL26.xlsx")), null).getId();
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(multipart("/carga-modelos/origen").param("origen", "TEMPORADA").param("cliente", "APC")
                        .param("temporadaId", String.valueOf(temporadaId)).session(sesion))
                .andExpect(redirectedUrl("/carga-modelos/bateria"));

        JsonNode lote = estado(sesion);
        assertTrue(lote.path("modelos").size() > 5);
        JsonNode modelo = lote.path("modelos").get(0);
        assertEquals(temporadaId.longValue(), modelo.path("temporadaId").asLong());
        assertTrue(modelo.path("pieles").size() > 0);
        assertTrue(modelo.path("pieles").get(0).path("colores").size() > 0);
        assertTrue(catalogo.codigosDeModeloEn("APC", temporadaId).isEmpty(), "hasta cargar, nada está en el catálogo");

        mvc.perform(post("/carga-modelos/descartar").session(sesion)).andExpect(redirectedUrl("/carga-modelos"));
        mvc.perform(get("/carga-modelos/estado").session(sesion)).andExpect(status().isGone());
    }
}
