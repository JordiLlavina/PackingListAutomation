package com.puntotres.packinglist.modelos.api;

import static com.puntotres.packinglist.modelos.ComoUsuario.comoUsuario;
import static com.puntotres.packinglist.modelos.ComoUsuario.usuarioDePrueba;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * La API de modificaciones, la bandeja y los adjuntos: la dirección la decide
 * el rol, quién puede qué, y cómo se sirven los adjuntos. La base de datos es
 * la misma para toda la suite: los códigos de este test empiezan por
 * {@code T12-} y los recuentos de la bandeja se comparan antes y después.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModificacionesControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Lo que se manda como nota de voz: no hace falta que suene, solo que se sirva entera o a trozos. */
    private static final byte[] AUDIO = {26, 69, -33, -93, 1, 2, 3, 4, 5, 6};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Usuarios usuarios;

    @Test
    void elTallerCreaUnAvisoConFotoYDespachoLoVeEnSuBandeja() throws Exception {
        long modeloId = alta("T12-AVISO");
        int nuevasAntes = resumenDespacho().get("nuevas").asInt();

        String creada = mvc.perform(nueva(modeloId).file(foto("foto.png", Color.RED))
                        .param("tipoCambio", "PIEL")
                        .with(comoUsuario(taller())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modeloId").value(modeloId))
                .andExpect(jsonPath("$.modeloReferencia").value("T12-AVISO"))
                .andExpect(jsonPath("$.direccion").value("TALLER_A_DESPACHO"))
                .andExpect(jsonPath("$.estado").value("NUEVA"))
                .andExpect(jsonPath("$.tipoCambioTexto").value("Piel / material"))
                .andExpect(jsonPath("$.fotos").value(1))
                .andExpect(jsonPath("$.destinos", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        long id = JSON.readTree(creada).get("id").asLong();

        mvc.perform(get("/api/modificaciones").param("direccion", "TALLER_A_DESPACHO")
                        .param("modelo", String.valueOf(modeloId)).with(comoUsuario(despacho())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/modificaciones").param("direccion", "DESPACHO_A_TALLER")
                        .param("modelo", String.valueOf(modeloId)).with(comoUsuario(despacho())))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/modificaciones").param("estado", "NUEVA").param("estado", "VISTA")
                        .param("cliente", "AMI").param("q", "t12-nada-de-esto")
                        .param("modelo", String.valueOf(modeloId)).with(comoUsuario(despacho())))
                .andExpect(jsonPath("$", hasSize(0)));
        assertEquals(nuevasAntes + 1, resumenDespacho().get("nuevas").asInt());

        mvc.perform(get("/api/modificaciones/" + id).with(comoUsuario(despacho())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.id").value(id))
                .andExpect(jsonPath("$.mensajes", hasSize(1)))
                .andExpect(jsonPath("$.mensajes[0].rolAutor").value("TALLER"))
                .andExpect(jsonPath("$.mensajes[0].adjuntos[0].tipo").value("FOTO"))
                .andExpect(jsonPath("$.mensajes[0].adjuntos[0].nombre").value("foto.jpg"))
                .andExpect(jsonPath("$.mensajes[0].adjuntos[0].bytes").doesNotExist());

        mvc.perform(post("/api/modificaciones/" + id + "/vista").with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VISTA"));
        assertEquals(nuevasAntes, resumenDespacho().get("nuevas").asInt());

        mvc.perform(multipart("/api/modificaciones/" + id + "/mensajes").param("texto", "¿Qué piel?")
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value("¿Qué piel?"))
                .andExpect(jsonPath("$.rolAutor").value("DESPACHO"))
                .andExpect(jsonPath("$.adjuntos", hasSize(0)));
        mvc.perform(get("/api/modificaciones/" + id).with(comoUsuario(taller())))
                .andExpect(jsonPath("$.resumen.respuestaPendienteDe").value("TALLER"))
                .andExpect(jsonPath("$.mensajes", hasSize(2)));

        mvc.perform(post("/api/modificaciones/" + id + "/destinos/ICSUITE").with(comoUsuario(despacho()))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aplica\":true,\"hecho\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"));
        mvc.perform(post("/api/modificaciones/" + id + "/hecha").with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("HECHA"))
                .andExpect(jsonPath("$.respuestaPendienteDe").value(nullValue()));
    }

    @Test
    void elTallerNoPuedeCrearOrdenesNiPonerPrioridad() throws Exception {
        long modeloId = alta("T12-ROL");

        String creada = mvc.perform(nueva(modeloId).file(foto("foto.png", Color.BLUE))
                        .param("tipoCambio", "FORRO").param("titulo", "Orden del taller")
                        .param("prioridad", "ALTA")
                        .with(comoUsuario(taller())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direccion").value("TALLER_A_DESPACHO"))
                .andExpect(jsonPath("$.titulo").value(nullValue()))
                .andExpect(jsonPath("$.prioridad").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        long id = JSON.readTree(creada).get("id").asLong();

        mvc.perform(post("/api/modificaciones/" + id + "/prioridad").with(comoUsuario(taller())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prioridad\":\"ALTA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/modificaciones/" + id + "/destinos/ICSUITE").with(comoUsuario(taller()))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aplica\":true,\"hecho\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/modificaciones/" + id + "/descartar").with(comoUsuario(taller())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"no\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/modificaciones/" + id + "/hecha").with(comoUsuario(taller())).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/modificaciones/" + id + "/prioridad").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prioridad\":\"ALTA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prioridad").value("ALTA"));

        // El despacho, con los mismos campos, crea una orden: también la decide el rol.
        mvc.perform(nueva(modeloId).param("titulo", "Cambiar el forro").param("texto", "Ante marrón")
                        .param("prioridad", "NORMAL")
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direccion").value("DESPACHO_A_TALLER"))
                .andExpect(jsonPath("$.titulo").value("Cambiar el forro"))
                .andExpect(jsonPath("$.prioridad").value("NORMAL"));

        mvc.perform(post("/api/modificaciones/" + id + "/descartar").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Repetido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DESCARTADA"));
        mvc.perform(post("/api/modificaciones/" + id + "/descartar").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Otra vez\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La modificación ya está descartada"));
    }

    @Test
    void elTallerNoAdjuntaDocumentos() throws Exception {
        long modeloId = alta("T12-DOCS");

        mvc.perform(nueva(modeloId).file(foto("foto.png", Color.RED)).file(documento("plano.pdf"))
                        .param("tipoCambio", "PIEL")
                        .with(comoUsuario(taller())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El taller no adjunta documentos"));
        mvc.perform(get("/api/modificaciones").param("modelo", String.valueOf(modeloId))
                        .with(comoUsuario(despacho())))
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(nueva(modeloId).file(documento("plano.pdf")).param("titulo", "Nuevo patrón")
                        .param("prioridad", "BAJA").param("guardarEnModelo", "true")
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direccion").value("DESPACHO_A_TALLER"))
                .andExpect(jsonPath("$.documentos").value(1));
    }

    @Test
    void unaNotaDeVozSinDuracionNoEntra() throws Exception {
        long modeloId = alta("T12-SINDURACION");

        for (String duracion : new String[] {null, "", "tres", "-1"}) {
            MockMultipartHttpServletRequestBuilder peticion = nueva(modeloId).file(audio());
            peticion.param("tipoCambio", "PIEL");
            if (duracion != null) {
                peticion.param("audioDuracionSeg", duracion);
            }
            mvc.perform(peticion.with(comoUsuario(taller())).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("Falta la duración de la nota de voz"));
        }
        mvc.perform(get("/api/modificaciones").param("modelo", String.valueOf(modeloId))
                        .with(comoUsuario(despacho())))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unaNotaDeVozQueNoEsAudioNoEntra() throws Exception {
        long modeloId = alta("T12-NOAUDIO");

        mvc.perform(nueva(modeloId)
                        .file(new MockMultipartFile("audio", "nota.html", "text/html",
                                "<script>alert(1)</script>".getBytes()))
                        .param("audioDuracionSeg", "5").param("tipoCambio", "PIEL")
                        .with(comoUsuario(taller())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La nota de voz no es un fichero de audio"));
        mvc.perform(get("/api/modificaciones").param("modelo", String.valueOf(modeloId))
                        .with(comoUsuario(despacho())))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unaParteVaciaNoCuentaYElNombreSeQuedaSinRuta() throws Exception {
        long modeloId = alta("T12-VACIAS");
        MockMultipartFile sinElegir = new MockMultipartFile("documentos", "", "application/octet-stream",
                new byte[0]);
        MockMultipartFile fotoSinElegir = new MockMultipartFile("fotos", "", "application/octet-stream",
                new byte[0]);
        MockMultipartFile audioSinElegir = new MockMultipartFile("audio", "", "application/octet-stream",
                new byte[0]);

        String creada = mvc.perform(nueva(modeloId).file(foto("C:\\fakepath\\foto.png", Color.RED))
                        .file(fotoSinElegir).file(sinElegir).file(audioSinElegir).param("tipoCambio", "PIEL")
                        .with(comoUsuario(taller())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fotos").value(1))
                .andExpect(jsonPath("$.audios").value(0))
                .andExpect(jsonPath("$.documentos").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = JSON.readTree(creada).get("id").asLong();
        mvc.perform(get("/api/modificaciones/" + id).with(comoUsuario(despacho())))
                .andExpect(jsonPath("$.mensajes[0].adjuntos", hasSize(1)))
                .andExpect(jsonPath("$.mensajes[0].adjuntos[0].nombre").value("foto.jpg"));

        mvc.perform(multipart("/api/modificaciones/" + id + "/mensajes")
                        .file(foto("../../otra.png", Color.BLUE)).file(fotoSinElegir)
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adjuntos", hasSize(1)))
                .andExpect(jsonPath("$.adjuntos[0].nombre").value("otra.jpg"));
    }

    @Test
    void elAudioSeSirveConRangos() throws Exception {
        long modeloId = alta("T12-AUDIO");
        String creada = mvc.perform(nueva(modeloId).file(audio()).param("audioDuracionSeg", "5")
                        .param("tipoCambio", "COSTURA")
                        .with(comoUsuario(taller())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.audios").value(1))
                .andReturn().getResponse().getContentAsString();
        long adjuntoId = primerAdjunto(JSON.readTree(creada).get("id").asLong());

        byte[] trozo = mvc.perform(get("/api/adjuntos/" + adjuntoId).header(HttpHeaders.RANGE, "bytes=0-3")
                        .with(comoUsuario(despacho())))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-3/" + AUDIO.length))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/webm;codecs=opus"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"nota.webm\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(Arrays.copyOf(AUDIO, 4), trozo);

        byte[] entero = mvc.perform(get("/api/adjuntos/" + adjuntoId).with(comoUsuario(taller())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/webm;codecs=opus"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(AUDIO, entero);
    }

    @Test
    void elAdjuntoSeSirveSinOlfatearloYConSuNombre() throws Exception {
        long modeloId = alta("T12-SERVIR");
        String aviso = mvc.perform(nueva(modeloId).file(foto("foto.png", Color.GREEN)).param("tipoCambio", "PIEL")
                        .with(comoUsuario(taller())).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fotoId = primerAdjunto(JSON.readTree(aviso).get("id").asLong());

        mvc.perform(get("/api/adjuntos/" + fotoId).with(comoUsuario(taller())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"foto.jpg\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=86400"));

        String orden = mvc.perform(nueva(modeloId).file(documento("ñandú \"1\".pdf")).param("titulo", "Plano")
                        .param("prioridad", "NORMAL")
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long documentoId = primerAdjunto(JSON.readTree(orden).get("id").asLong());
        mvc.perform(get("/api/adjuntos/" + documentoId).with(comoUsuario(taller())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"_and_ \\\"1\\\".pdf\"; filename*=UTF-8''%C3%B1and%C3%BA%20%221%22.pdf"));

        // Lo que el navegador pintaría como página o como imagen con scripts se descarga, nunca se abre aquí.
        for (String peligroso : new String[] {"text/html", "image/svg+xml", "application/xhtml+xml"}) {
            String conHtml = mvc.perform(nueva(modeloId)
                            .file(new MockMultipartFile("documentos", "pagina.html", peligroso,
                                    "<script>alert(1)</script>".getBytes()))
                            .param("titulo", "Página").param("prioridad", "NORMAL")
                            .with(comoUsuario(despacho())).with(csrf()))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long htmlId = primerAdjunto(JSON.readTree(conHtml).get("id").asLong());
            mvc.perform(get("/api/adjuntos/" + htmlId).with(comoUsuario(taller())))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"pagina.html\""))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
            mvc.perform(get("/api/adjuntos/" + htmlId).header(HttpHeaders.RANGE, "bytes=0-3")
                            .with(comoUsuario(taller())))
                    .andExpect(status().isPartialContent())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"pagina.html\""))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }

        mvc.perform(get("/api/adjuntos/987654321").with(comoUsuario(taller())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No hay ningún adjunto con id 987654321"));
    }

    @Test
    void aGaleriaDejaLaFotoEnElModelo() throws Exception {
        long modeloId = alta("T12-GALERIA");
        String aviso = mvc.perform(nueva(modeloId).file(foto("foto.png", Color.RED)).param("tipoCambio", "PIEL")
                        .with(comoUsuario(taller())).with(csrf()))
                .andReturn().getResponse().getContentAsString();
        long fotoId = primerAdjunto(JSON.readTree(aviso).get("id").asLong());

        mvc.perform(post("/api/adjuntos/" + fotoId + "/a-galeria").with(comoUsuario(taller())).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/adjuntos/" + fotoId + "/a-galeria").with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].modeloId").value(modeloId))
                .andExpect(jsonPath("$[0].nombreFichero").value("foto.jpg"))
                .andExpect(jsonPath("$[0].principal").value(true));

        // La misma foto otra vez no se duplica; otra que se llama igual sí entra, con otro nombre.
        mvc.perform(post("/api/adjuntos/" + fotoId + "/a-galeria").with(comoUsuario(despacho())).with(csrf()))
                .andExpect(jsonPath("$", hasSize(1)));
        String otroAviso = mvc.perform(nueva(modeloId).file(foto("foto.png", Color.BLUE))
                        .param("tipoCambio", "PIEL").with(comoUsuario(taller())).with(csrf()))
                .andReturn().getResponse().getContentAsString();
        long otraFotoId = primerAdjunto(JSON.readTree(otroAviso).get("id").asLong());
        mvc.perform(post("/api/adjuntos/" + otraFotoId + "/a-galeria").with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].nombreFichero").value("foto (2).jpg"));
        mvc.perform(post("/api/adjuntos/" + fotoId + "/a-galeria").with(comoUsuario(despacho())).with(csrf()))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/modelos/" + modeloId + "/fotos").with(comoUsuario(taller())))
                .andExpect(jsonPath("$", hasSize(2)));

        String conAudio = mvc.perform(nueva(modeloId).file(audio()).param("audioDuracionSeg", "3")
                        .param("tipoCambio", "PIEL").with(comoUsuario(taller())).with(csrf()))
                .andReturn().getResponse().getContentAsString();
        long audioId = primerAdjunto(JSON.readTree(conAudio).get("id").asLong());
        mvc.perform(post("/api/adjuntos/" + audioId + "/a-galeria").with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Solo se puede pasar una foto a la galería"));
    }

    @Test
    void unValorQueNoExisteEs400ConMensaje() throws Exception {
        mvc.perform(get("/api/modificaciones").param("prioridad", "URGENTE").with(comoUsuario(despacho())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Valor no válido: URGENTE"));
        mvc.perform(get("/api/modificaciones").param("estado", "ABIERTA").with(comoUsuario(despacho())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Valor no válido: ABIERTA"));
        mvc.perform(post("/api/modificaciones/1/destinos/CORREO").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aplica\":true,\"hecho\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Valor no válido: CORREO"));
        mvc.perform(post("/api/modificaciones/1/prioridad").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prioridad\":\"URGENTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Valor no válido: URGENTE"));
        mvc.perform(get("/api/modificaciones/987654321").with(comoUsuario(despacho())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No hay ninguna modificación con id 987654321"));
    }

    private MockMultipartHttpServletRequestBuilder nueva(long modeloId) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/modificaciones")
                .param("modeloId", String.valueOf(modeloId));
    }

    /** El id del primer adjunto del primer mensaje de esa modificación. */
    private long primerAdjunto(long modificacionId) throws Exception {
        String detalle = mvc.perform(get("/api/modificaciones/" + modificacionId).with(comoUsuario(despacho())))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(detalle).get("mensajes").get(0).get("adjuntos").get(0).get("id").asLong();
    }

    private JsonNode resumenDespacho() throws Exception {
        return JSON.readTree(mvc.perform(get("/api/bandeja/resumen").with(comoUsuario(despacho())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** Da de alta un modelo con código por la API, como el despacho, y devuelve su id. */
    private long alta(String codigo) throws Exception {
        String respuesta = mvc.perform(post("/api/modelos").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"AMI\",\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(respuesta).get("id").asLong();
    }

    private Usuario taller() {
        return usuarioDePrueba(usuarios, Rol.TALLER);
    }

    private Usuario despacho() {
        return usuarioDePrueba(usuarios, Rol.DESPACHO);
    }

    private static MockMultipartFile audio() {
        return new MockMultipartFile("audio", "nota.webm", "audio/webm;codecs=opus", AUDIO);
    }

    private static MockMultipartFile documento(String nombre) {
        return new MockMultipartFile("documentos", nombre, MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4 de prueba".getBytes());
    }

    private static MockMultipartFile foto(String nombre, Color color) throws Exception {
        BufferedImage imagen = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 40, 30);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return new MockMultipartFile("fotos", nombre, MediaType.IMAGE_PNG_VALUE, salida.toByteArray());
    }
}
