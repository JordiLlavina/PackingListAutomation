package com.puntotres.packinglist.modelos.api;

import static com.puntotres.packinglist.modelos.ComoUsuario.comoUsuario;
import static com.puntotres.packinglist.modelos.ComoUsuario.usuarioDePrueba;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.modelos.PdfDePrueba;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.service.Documentos;
import com.puntotres.packinglist.modelos.service.ImagenesDePdf;
import com.puntotres.packinglist.modelos.service.Usuarios;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;

/**
 * Los documentos de un modelo y la elección de las imágenes de un PDF:
 * subir un PDF guarda el documento y devuelve candidatas, pero en las fotos
 * del modelo solo entra lo que el despacho elige. La base de datos es la
 * misma para toda la suite: los códigos de este test empiezan por {@code T22-}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentosControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private FotosCatalogo fotos;

    /** Los lotes de candidatas que ha dejado cada test en el directorio temporal de verdad, para borrarlos. */
    private final List<String> lotes = new ArrayList<>();

    @AfterEach
    void borrarLotes() throws IOException {
        for (String lote : lotes) {
            Path directorio = directorioDe(lote);
            if (Files.exists(directorio)) {
                try (Stream<Path> dentro = Files.walk(directorio)) {
                    for (Path ruta : dentro.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(ruta);
                    }
                }
            }
        }
    }

    @Test
    void unDocumentoDeMasDe50MbSeRechazaSinGuardarlo() throws Exception {
        long modelo = alta("T22-GRANDE");
        MockMultipartFile grande = new MockMultipartFile("documento", "grande.pdf", "application/pdf",
                new byte[(int) Documentos.MAX_BYTES + 1]);

        mvc.perform(multipart("/api/modelos/" + modelo + "/documentos").file(grande)
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El documento pesa más de 50 MB"));
        mvc.perform(get("/api/modelos/" + modelo).with(comoUsuario(despacho())))
                .andExpect(jsonPath("$.documentos", hasSize(0)));
    }

    @Test
    void nadaEntraEnFotoModeloSinElegir() throws Exception {
        long modelo = alta("T22-SIN-ELEGIR");

        JsonNode subida = subirPdf(modelo, "Ficha técnica.pdf");

        assertEquals("Ficha técnica.pdf", subida.get("documento").get("nombre").asText());
        assertEquals("application/pdf", subida.get("documento").get("mime").asText());
        assertTrue(subida.get("loteId").isTextual());
        assertEquals(PdfDePrueba.CANDIDATAS, subida.get("candidatas").size());
        assertTrue(fotos.de(modelo).isEmpty(), "subir un PDF no mete ninguna foto en el modelo");

        String lote = subida.get("loteId").asText();
        String clave = subida.get("candidatas").get(0).get("clave").asText();
        mvc.perform(get("/api/modelos/" + modelo + "/documentos/candidatas/" + lote + "/" + clave)
                        .with(comoUsuario(despacho())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "private, max-age=1800"));

        mvc.perform(get("/api/modelos/" + modelo).with(comoUsuario(despacho())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentos", hasSize(1)))
                .andExpect(jsonPath("$.documentos[0].nombre").value("Ficha técnica.pdf"))
                .andExpect(jsonPath("$.fotos", hasSize(0)));
    }

    @Test
    void elegirDosDejaDosFotosYLaPrincipal() throws Exception {
        long modelo = alta("T22-ELEGIR");
        JsonNode subida = subirPdf(modelo, "ficha.pdf");
        long documento = subida.get("documento").get("id").asLong();
        String lote = subida.get("loteId").asText();
        List<String> claves = StreamSupport.stream(subida.get("candidatas").spliterator(), false)
                .map(candidata -> candidata.get("clave").asText()).toList();

        mvc.perform(post(elegir(modelo, documento)).with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lote\":\"" + lote + "\",\"claves\":[],\"principal\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Elige al menos una imagen"));

        mvc.perform(post(elegir(modelo, documento)).with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new Eleccion(lote, List.of(claves.get(0), claves.get(2)),
                                claves.get(2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        List<ResumenFoto> suyas = fotos.de(modelo);
        assertEquals(List.of("ficha-" + claves.get(0) + ".jpg", "ficha-" + claves.get(2) + ".jpg"),
                suyas.stream().map(ResumenFoto::nombreFichero).toList());
        assertEquals(List.of("ficha-" + claves.get(2) + ".jpg"), suyas.stream().filter(ResumenFoto::principal)
                .map(ResumenFoto::nombreFichero).toList());
    }

    @Test
    void unLoteCaducadoDa404() throws Exception {
        long modelo = alta("T22-CADUCADO");
        JsonNode subida = subirPdf(modelo, "ficha.pdf");
        long documento = subida.get("documento").get("id").asLong();
        String lote = subida.get("loteId").asText();
        String clave = subida.get("candidatas").get(0).get("clave").asText();
        Path directorio = directorioDe(lote);
        Files.setLastModifiedTime(directorio,
                FileTime.from(Instant.now().minus(ImagenesDePdf.VIGENCIA).minusSeconds(60)));

        mvc.perform(get("/api/modelos/" + modelo + "/documentos/candidatas/" + lote + "/" + clave)
                        .with(comoUsuario(despacho())))
                .andExpect(status().isNotFound());
        mvc.perform(post(elegir(modelo, documento)).with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new Eleccion(lote, List.of(clave), clave))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Las imágenes del PDF han caducado, vuelve a subirlo"));
        assertTrue(fotos.de(modelo).isEmpty());
    }

    @Test
    void unaClaveQueNoEsDelLoteDa404YNoGuardaNinguna() throws Exception {
        long modelo = alta("T22-CLAVE");
        JsonNode subida = subirPdf(modelo, "ficha.pdf");
        long documento = subida.get("documento").get("id").asLong();
        String lote = subida.get("loteId").asText();
        String buena = subida.get("candidatas").get(0).get("clave").asText();

        mvc.perform(post(elegir(modelo, documento)).with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new Eleccion(lote, List.of(buena, "../x"), buena))))
                .andExpect(status().isNotFound());
        assertTrue(fotos.de(modelo).isEmpty(), "ni la buena: se guardan todas o ninguna");
    }

    @Test
    void unDocumentoQueNoEsPdfSeGuardaSinCandidatasYUnHtmlSeDescarga() throws Exception {
        long modelo = alta("T22-HTML");
        MockMultipartFile html = new MockMultipartFile("documento", "C:\\fakepath\\nota.html", "text/html",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        String cuerpo = mvc.perform(multipart("/api/modelos/" + modelo + "/documentos").file(html)
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento.nombre").value("nota.html"))
                .andExpect(jsonPath("$.loteId").doesNotExist())
                .andExpect(jsonPath("$.candidatas", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        long documento = JSON.readTree(cuerpo).get("documento").get("id").asLong();

        mvc.perform(get("/api/documentos/" + documento).with(comoUsuario(taller())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")));

        long pdf = subirPdf(modelo, "ficha.pdf").get("documento").get("id").asLong();
        mvc.perform(get("/api/documentos/" + pdf).with(comoUsuario(taller())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"ficha.pdf\""));
    }

    @Test
    void unPdfQueNoSeDejaLeerNoSeGuarda() throws Exception {
        long modelo = alta("T22-ROTO");
        MockMultipartFile roto = new MockMultipartFile("documento", "roto.pdf", "application/pdf",
                "no es un PDF".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/modelos/" + modelo + "/documentos").file(roto)
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El fichero no es un PDF que se pueda leer"));
        mvc.perform(get("/api/modelos/" + modelo).with(comoUsuario(despacho())))
                .andExpect(jsonPath("$.documentos", hasSize(0)));
    }

    @Test
    void borrarDa204YDespuesYaNoEsta() throws Exception {
        long modelo = alta("T22-BORRAR");
        long documento = subirPdf(modelo, "ficha.pdf").get("documento").get("id").asLong();

        mvc.perform(delete("/api/documentos/" + documento).with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/documentos/" + documento).with(comoUsuario(despacho())))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/documentos/" + documento).with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/modelos/" + modelo).with(comoUsuario(despacho())))
                .andExpect(jsonPath("$.documentos", hasSize(0)));
    }

    @Test
    void elTallerAbrePeroNoSubeNiEligeNiBorra() throws Exception {
        long modelo = alta("T22-TALLER");
        JsonNode subida = subirPdf(modelo, "ficha.pdf");
        long documento = subida.get("documento").get("id").asLong();
        String lote = subida.get("loteId").asText();
        String clave = subida.get("candidatas").get(0).get("clave").asText();
        MockMultipartFile pdf = new MockMultipartFile("documento", "otra.pdf", "application/pdf",
                PdfDePrueba.fichaTecnica());

        mvc.perform(multipart("/api/modelos/" + modelo + "/documentos").file(pdf)
                        .with(comoUsuario(taller())).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(elegir(modelo, documento)).with(comoUsuario(taller())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new Eleccion(lote, List.of(clave), clave))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/documentos/" + documento).with(comoUsuario(taller())).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/documentos/" + documento).with(comoUsuario(taller())))
                .andExpect(status().isOk());
        assertTrue(fotos.de(modelo).isEmpty());
    }

    /** Lo que manda la pantalla al guardar las elegidas. */
    record Eleccion(String lote, List<String> claves, String principal) {
    }

    private JsonNode subirPdf(long modelo, String nombre) throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("documento", nombre, "application/pdf",
                PdfDePrueba.fichaTecnica());
        String cuerpo = mvc.perform(multipart("/api/modelos/" + modelo + "/documentos").file(pdf)
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode subida = JSON.readTree(cuerpo);
        lotes.add(subida.get("loteId").asText());
        return subida;
    }

    private static Path directorioDe(String lote) {
        return Path.of(System.getProperty("java.io.tmpdir"), ImagenesDePdf.PREFIJO + lote);
    }

    private static String elegir(long modelo, long documento) {
        return "/api/modelos/" + modelo + "/documentos/" + documento + "/elegir-fotos";
    }

    private long alta(String codigo) throws Exception {
        String cuerpo = mvc.perform(post("/api/modelos").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"AMI\",\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(cuerpo).get("id").asLong();
    }

    private Usuario despacho() {
        return usuarioDePrueba(usuarios, Rol.DESPACHO);
    }

    private Usuario taller() {
        return usuarioDePrueba(usuarios, Rol.TALLER);
    }
}
