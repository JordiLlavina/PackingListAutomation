package com.puntotres.packinglist.modelos.api;

import static com.puntotres.packinglist.modelos.ComoUsuario.comoUsuario;
import static com.puntotres.packinglist.modelos.ComoUsuario.usuarioDePrueba;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.service.Documentos;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Una orden con «guardar también en el modelo» se crea primero, así que un
 * fallo que nadie esperaba al copiar el documento al modelo no puede dar un
 * 500: el despacho creería que la orden no ha salido y la mandaría dos veces.
 * Va en su propia clase porque sustituye {@link Documentos} en el contexto.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CopiaAlModeloFallidaTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Usuarios usuarios;

    @MockitoBean
    private Documentos documentos;

    @Test
    void unErrorInesperadoAlCopiarDejaLaOrdenCreadaYUnAviso() throws Exception {
        when(documentos.subir(anyLong(), anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("fallo que nadie esperaba"));
        Usuario despacho = usuarioDePrueba(usuarios, Rol.DESPACHO);
        String alta = mvc.perform(post("/api/modelos").with(comoUsuario(despacho)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"AMI\",\"codigo\":\"T27-COPIA-FALLA\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long modeloId = new ObjectMapper().readTree(alta).get("id").asLong();

        mvc.perform(multipart("/api/modificaciones")
                        .file(new MockMultipartFile("documentos", "x.pdf", MediaType.APPLICATION_PDF_VALUE,
                                "%PDF-1.4 de prueba".getBytes()))
                        .param("modeloId", String.valueOf(modeloId))
                        .param("titulo", "Nuevo patrón").param("prioridad", "NORMAL")
                        .param("guardarEnModelo", "true")
                        .with(comoUsuario(despacho)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modificacion.documentos").value(1))
                .andExpect(jsonPath("$.documentosGuardados", hasSize(0)))
                .andExpect(jsonPath("$.avisos", hasSize(1)))
                .andExpect(jsonPath("$.avisos[0]").value("No se pudo guardar x.pdf en el modelo: error inesperado"));
    }
}
