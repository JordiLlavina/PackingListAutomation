package com.puntotres.packinglist.modelos.api;

import static com.puntotres.packinglist.modelos.ComoUsuario.comoUsuario;
import static com.puntotres.packinglist.modelos.ComoUsuario.usuarioDePrueba;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.modelos.ModelosProperties;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.service.Usuarios;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.FicheroExcel;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;

/**
 * La API de modelos: quién puede qué, el QR, las fotos, la etiqueta impresa
 * y asignar referencia o juntar dos modelos. La base de datos es la misma
 * para toda la suite: los códigos de este test empiezan por {@code T9-API-}
 * o {@code T19-API-}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModelosControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private ModelosProperties props;

    @Autowired
    private ArchivoTemporadas temporadas;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private ModeloRepository repositorioModelos;

    @Test
    void elTallerListaYVeLaFichaPeroNoDaDeAlta() throws Exception {
        long id = alta("T9-API-TALLER");
        Usuario taller = usuarioDePrueba(usuarios, Rol.TALLER);

        mvc.perform(get("/api/modelos").param("cliente", "AMI").param("q", "t9-api-taller").with(comoUsuario(taller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].referencia").value("T9-API-TALLER"))
                .andExpect(jsonPath("$[0].etapa").value("PROTOTIPO"));

        mvc.perform(get("/api/modelos/" + id).with(comoUsuario(taller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo.codigo").value("T9-API-TALLER"))
                .andExpect(jsonPath("$.codigos[0].codigo").value("T9-API-TALLER"))
                .andExpect(jsonPath("$.documentos", hasSize(0)))
                .andExpect(jsonPath("$.lineaTiempo[?(@.texto=='Pasa a Prototipo')].quien").value("Prueba Despacho"));

        mvc.perform(post("/api/modelos").with(comoUsuario(taller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"AMI\",\"codigo\":\"T9-API-NO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/modelos/" + id + "/etapa").with(comoUsuario(taller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"etapa\":\"SMS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elDespachoDaDeAltaEditaYCambiaLaEtapa() throws Exception {
        long id = alta("T9-API-DESPACHO");

        mvc.perform(patch("/api/modelos/" + id).with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Bolso T9\",\"tipo\":\"CINTURON\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Bolso T9"))
                .andExpect(jsonPath("$.tipo").value("CINTURON"));

        mvc.perform(post("/api/modelos/" + id + "/etapa").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"etapa\":\"SMS\",\"motivo\":\"muestras pedidas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapa").value("SMS"));

        mvc.perform(post("/api/modelos/" + id + "/etapa").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"etapa\":\"SMS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El modelo ya está en Muestras"));

        mvc.perform(post("/api/modelos").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"AMI\",\"codigo\":\"T9-API-DESPACHO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("Ya existe T9-API-DESPACHO: asigna la referencia al prototipo o fusiona"));
    }

    @Test
    void unModeloQueNoExisteEs404ConMensaje() throws Exception {
        mvc.perform(get("/api/modelos/987654321").with(comoUsuario(despacho())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No hay ningún modelo con id 987654321"));
        mvc.perform(get("/api/modelos/987654321/qr.png").with(comoUsuario(despacho())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/modelos/987654321/fotos").with(comoUsuario(despacho())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/fotos/987654321").with(comoUsuario(despacho())))
                .andExpect(status().isNotFound());
    }

    @Test
    void elQrDiceLaDireccionPublicaDelModelo() throws Exception {
        long id = alta("T9-API-QR");

        byte[] png = mvc.perform(get("/api/modelos/" + id + "/qr.png").with(comoUsuario(usuarioDePrueba(usuarios,
                        Rol.TALLER))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertTrue(imagen.getWidth() >= 600, "lado mínimo de 600 px");
        String esperado = props.urlPublica().replaceAll("/+$", "") + "/m/" + id;
        assertEquals(esperado, decodificar(imagen));
    }

    @Test
    void unaFotoDe2000PxSeQuedaEn1600YEsLaPrincipal() throws Exception {
        long id = alta("T9-API-FOTO");
        MockMultipartFile foto = new MockMultipartFile("fotos", "grande.png", MediaType.IMAGE_PNG_VALUE,
                png(2000, 1500, Color.RED));

        String respuesta = mvc.perform(multipart("/api/modelos/" + id + "/fotos").file(foto)
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombreFichero").value("grande.png"))
                .andExpect(jsonPath("$[0].principal").value(true))
                .andReturn().getResponse().getContentAsString();
        long fotoId = JSON.readTree(respuesta).get(0).get("id").asLong();

        byte[] jpeg = mvc.perform(get("/api/fotos/" + fotoId).with(comoUsuario(usuarioDePrueba(usuarios,
                        Rol.TALLER))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "private, max-age=86400"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage guardada = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertEquals(1600, Math.max(guardada.getWidth(), guardada.getHeight()));
        assertEquals(1200, guardada.getHeight());

        mvc.perform(get("/api/modelos").param("q", "T9-API-FOTO").with(comoUsuario(despacho())))
                .andExpect(jsonPath("$[0].fotoPrincipalId").value(fotoId));
    }

    @Test
    void unaFotoDeMasDe15MbSeRechazaSinLeerlaYNoGuardaNinguna() throws Exception {
        long id = alta("T27-API-ENORME");

        mvc.perform(multipart("/api/modelos/" + id + "/fotos")
                        .file(new MockMultipartFile("fotos", "buena.png", MediaType.IMAGE_PNG_VALUE,
                                png(40, 30, Color.RED)))
                        .file(ModificacionesControllerTest.enorme("fotos", "enorme.jpg", MediaType.IMAGE_JPEG_VALUE))
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El fichero enorme.jpg pesa más de 15 MB"));

        mvc.perform(get("/api/modelos/" + id).with(comoUsuario(despacho())))
                .andExpect(jsonPath("$.fotos", hasSize(0)));
    }

    @Test
    void dosFotosLlamadasIgualSeGuardanLasDosConNombresDistintos() throws Exception {
        long id = alta("T9-API-MISMONOMBRE");

        mvc.perform(multipart("/api/modelos/" + id + "/fotos")
                        .file(new MockMultipartFile("fotos", "image.jpg", MediaType.IMAGE_PNG_VALUE,
                                png(40, 30, Color.RED)))
                        .file(new MockMultipartFile("fotos", "image.jpg", MediaType.IMAGE_PNG_VALUE,
                                png(40, 30, Color.BLUE)))
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombreFichero").value("image.jpg"))
                .andExpect(jsonPath("$[1].nombreFichero").value("image (2).jpg"));

        // Otra subida más tarde, como hace un móvil con cada foto de la cámara.
        mvc.perform(multipart("/api/modelos/" + id + "/fotos")
                        .file(new MockMultipartFile("fotos", "IMAGE.JPG", MediaType.IMAGE_PNG_VALUE,
                                png(40, 30, Color.GREEN)))
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].nombreFichero").value("IMAGE (3).JPG"));
    }

    @Test
    void marcarOtraFotoComoPrincipal() throws Exception {
        long id = alta("T9-API-PRINCIPAL");
        mvc.perform(multipart("/api/modelos/" + id + "/fotos")
                        .file(new MockMultipartFile("fotos", "a.png", MediaType.IMAGE_PNG_VALUE, png(40, 30, Color.RED)))
                        .file(new MockMultipartFile("fotos", "b.png", MediaType.IMAGE_PNG_VALUE, png(40, 30, Color.BLUE)))
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)));
        JsonNode fotos = JSON.readTree(mvc.perform(get("/api/modelos/" + id + "/fotos")
                .with(comoUsuario(despacho()))).andReturn().getResponse().getContentAsString());
        long segunda = fotos.get(1).get("id").asLong();

        mvc.perform(post("/api/modelos/" + id + "/fotos/" + segunda + "/principal")
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].principal").value(false))
                .andExpect(jsonPath("$[1].principal").value(true));

        long otro = alta("T9-API-OTRO");
        mvc.perform(post("/api/modelos/" + otro + "/fotos/" + segunda + "/principal")
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unFicheroQueNoEsUnaImagenDa400YNoGuardaNada() throws Exception {
        long id = alta("T9-API-NOIMAGEN");

        mvc.perform(multipart("/api/modelos/" + id + "/fotos")
                        .file(new MockMultipartFile("fotos", "buena.png", MediaType.IMAGE_PNG_VALUE,
                                png(40, 30, Color.RED)))
                        .file(new MockMultipartFile("fotos", "x.bin", "application/octet-stream",
                                new byte[] {1, 2, 3}))
                        .with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El fichero x.bin no es una imagen"));

        mvc.perform(get("/api/modelos/" + id + "/fotos").with(comoUsuario(despacho())))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void marcarLaEtiquetaImpresaLaSacaDeLasPendientes() throws Exception {
        long id = alta("T9-API-ETIQUETA");

        mvc.perform(get("/api/modelos").param("q", "T9-API-ETIQUETA").param("sinEtiqueta", "true")
                        .with(comoUsuario(despacho())))
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(post("/api/modelos/" + id + "/etiqueta-impresa").with(comoUsuario(despacho())).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/modelos").param("q", "T9-API-ETIQUETA").param("sinEtiqueta", "true")
                        .with(comoUsuario(despacho())))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/modelos").param("q", "T9-API-ETIQUETA").with(comoUsuario(despacho())))
                .andExpect(jsonPath("$[0].etiquetaImpresaEn").isNotEmpty());
    }

    @Test
    void asignarUnCodigoQueYaExisteSinConfirmarDevuelveLaPrevisualizacion() throws Exception {
        long proto = altaPrototipo("PROTO T19 API PREVIA", "T19-API-PREVIA");
        long importado = catalogo.asegurarArticulo("AMI", "T19-API-PREVIA.AL0001").getModeloId();

        mvc.perform(get("/api/modelos/" + proto + "/referencia/previsualizar").param("codigo", "t19-api-previa")
                        .with(comoUsuario(despacho())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superviviente").value(proto))
                .andExpect(jsonPath("$.absorbido").value(importado))
                .andExpect(jsonPath("$.articulos").value(1))
                .andExpect(jsonPath("$.fotos").value(0))
                .andExpect(jsonPath("$.modificaciones").value(0))
                .andExpect(jsonPath("$.documentos").value(0));
        mvc.perform(get("/api/modelos/" + proto + "/referencia/previsualizar").param("codigo", "T19-API-LIBRE-NO")
                        .with(comoUsuario(despacho())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.absorbido").isEmpty());

        mvc.perform(post("/api/modelos/" + proto + "/referencia").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"t19-api-previa\",\"confirmar\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya existe T19-API-PREVIA: se juntará con el prototipo"))
                .andExpect(jsonPath("$.fusion.superviviente").value(proto))
                .andExpect(jsonPath("$.fusion.absorbido").value(importado))
                .andExpect(jsonPath("$.fusion.articulos").value(1));
        assertTrue(repositorioModelos.existsById(importado));

        mvc.perform(post("/api/modelos/" + proto + "/referencia").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"t19-api-previa\",\"confirmar\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(proto))
                .andExpect(jsonPath("$.codigo").value("T19-API-PREVIA"))
                .andExpect(jsonPath("$.etapa").value("PRODUCCION"));
        mvc.perform(get("/api/modelos/" + proto).with(comoUsuario(despacho())))
                .andExpect(jsonPath("$.fusionadosDesde[0]").value(importado));
    }

    @Test
    void asignarUnCodigoLibrePorLaApiYFusionarDesdeElImportado() throws Exception {
        long proto = altaPrototipo("PROTO T19 API LIBRE", "T19-API-LIBRE");
        mvc.perform(post("/api/modelos/" + proto + "/referencia").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"t19-api-libre\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("T19-API-LIBRE"))
                .andExpect(jsonPath("$.nombrePrototipo").value("PROTO T19 API LIBRE"));

        long otroProto = altaPrototipo("PROTO T19 API OTRO", "T19-API-OTRO");
        long importado = catalogo.asegurarArticulo("AMI", "T19-API-OTRO.AL0001").getModeloId();
        mvc.perform(post("/api/modelos/" + importado + "/fusionar-con/" + otroProto).with(comoUsuario(despacho()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(otroProto))
                .andExpect(jsonPath("$.codigo").value("T19-API-OTRO"));
        mvc.perform(post("/api/modelos/" + otroProto + "/fusionar-con/" + otroProto).with(comoUsuario(despacho()))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No se puede fusionar un modelo consigo mismo"));
    }

    @Test
    void elTallerNoAsignaReferenciasNiFusiona() throws Exception {
        long proto = altaPrototipo("PROTO T19 API TALLER", "T19-API-TALLER");
        Usuario taller = usuarioDePrueba(usuarios, Rol.TALLER);

        mvc.perform(post("/api/modelos/" + proto + "/referencia").with(comoUsuario(taller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"T19-API-TALLER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/modelos/" + proto + "/referencia/previsualizar").param("codigo", "X")
                        .with(comoUsuario(taller)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/modelos/" + proto + "/fusionar-con/" + proto).with(comoUsuario(taller)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /** Da de alta un prototipo por la API, en una temporada nueva, y devuelve su id. */
    private long altaPrototipo(String nombre, String temporada) throws Exception {
        Long temporadaId = temporadas.guardar(null, "AMI", temporada, (FicheroExcel) null, null).getId();
        String respuesta = mvc.perform(post("/api/modelos").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"AMI\",\"nombrePrototipo\":\"" + nombre
                                + "\",\"temporadaId\":" + temporadaId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(respuesta).get("id").asLong();
    }

    /** Da de alta un modelo con código por la API, como el despacho, y devuelve su id. */
    private long alta(String codigo) throws Exception {
        String respuesta = mvc.perform(post("/api/modelos").with(comoUsuario(despacho())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"AMI\",\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(respuesta).get("id").asLong();
    }

    private Usuario despacho() {
        return usuarioDePrueba(usuarios, Rol.DESPACHO);
    }

    private static byte[] png(int ancho, int alto, Color color) throws Exception {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, ancho, alto);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    private static String decodificar(BufferedImage imagen) throws Exception {
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen)));
        return new MultiFormatReader().decode(bitmap).getText();
    }
}
