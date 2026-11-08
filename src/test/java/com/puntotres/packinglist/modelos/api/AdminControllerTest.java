package com.puntotres.packinglist.modelos.api;

import static com.puntotres.packinglist.modelos.ComoUsuario.comoUsuario;
import static com.puntotres.packinglist.modelos.ComoUsuario.usuarioDePrueba;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.puntotres.packinglist.modelos.persistence.Dispositivo;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.service.Dispositivos;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * Usuarios y dispositivos: solo el admin. Los logins de este test llevan el
 * prefijo {@code t7-}: la base de datos es la misma para toda la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private Dispositivos dispositivos;

    @Test
    void soloElAdminGestionaUsuarios() throws Exception {
        Usuario despacho = usuarioDePrueba(usuarios, Rol.DESPACHO);
        Usuario admin = usuarioDePrueba(usuarios, Rol.ADMIN);

        mvc.perform(get("/api/usuarios").with(comoUsuario(despacho))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dispositivos").with(comoUsuario(despacho))).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(comoUsuario(despacho)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"login\":\"t7-x\",\"password\":\"p\",\"rol\":\"TALLER\"}"))
                .andExpect(status().isForbidden());

        MvcResult listado = mvc.perform(get("/api/usuarios").with(comoUsuario(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.login=='prueba-admin')].rol").value("ADMIN"))
                .andReturn();
        String cuerpo = listado.getResponse().getContentAsString();
        assertFalse(cuerpo.toLowerCase().contains("hash"), "nunca el hash: " + cuerpo);
        assertFalse(cuerpo.contains("$2"), "nunca el hash: " + cuerpo);
    }

    @Test
    void elAdminCreaYCambiaUsuarios() throws Exception {
        Usuario admin = usuarioDePrueba(usuarios, Rol.ADMIN);

        MvcResult creado = mvc.perform(post("/api/usuarios").with(comoUsuario(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Despacho T7\",\"login\":\"t7-despacho\","
                                + "\"password\":\"secreto\",\"rol\":\"DESPACHO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("t7-despacho"))
                .andExpect(jsonPath("$.rol").value("DESPACHO"))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn();
        assertFalse(creado.getResponse().getContentAsString().toLowerCase().contains("hash"));
        long id = JSON.readTree(creado.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/usuarios").with(comoUsuario(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Otro\",\"login\":\"T7-DESPACHO\",\"password\":\"p\",\"rol\":\"TALLER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Ya hay un usuario con el login T7-DESPACHO"));

        mvc.perform(patch("/api/usuarios/" + id).with(comoUsuario(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Despacho T7 bis\",\"rol\":\"ADMIN\",\"activo\":false,\"password\":\"nueva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Despacho T7 bis"))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.activo").value(false));
        assertTrue(usuarios.autenticar("t7-despacho", "nueva").isEmpty(), "inactivo no entra");

        // Un PATCH vacío no toca nada.
        mvc.perform(patch("/api/usuarios/" + id).with(comoUsuario(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Despacho T7 bis"))
                .andExpect(jsonPath("$.activo").value(false));

        mvc.perform(patch("/api/usuarios/999999").with(comoUsuario(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No hay ningún usuario con id 999999"));
    }

    @Test
    void elCodigoDeAltaCaducaEnDiezMinutos() throws Exception {
        Usuario admin = usuarioDePrueba(usuarios, Rol.ADMIN);
        Usuario taller = usuarios.crear(new Usuarios.Alta("Bachir T7", "t7-bachir", "", Rol.TALLER));

        MvcResult resultado = mvc.perform(post("/api/usuarios/" + taller.getId() + "/codigo-alta")
                        .with(comoUsuario(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode cuerpo = JSON.readTree(resultado.getResponse().getContentAsString());

        assertTrue(cuerpo.get("codigo").asText().matches("\\d{6}"), cuerpo.toString());
        LocalDateTime caduca = LocalDateTime.parse(cuerpo.get("caduca").asText());
        long segundos = Duration.between(LocalDateTime.now(), caduca).toSeconds();
        assertTrue(segundos > 9 * 60 && segundos <= 10 * 60, "caduca en " + segundos + " segundos");

        // El código sirve de verdad.
        Dispositivos.TokenDispositivo token = dispositivos.canjear(cuerpo.get("codigo").asText(), "Móvil T7");
        assertEquals(taller.getId(), token.dispositivo().getUsuarioId());

        mvc.perform(post("/api/usuarios/999999/codigo-alta").with(comoUsuario(admin)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/usuarios/" + taller.getId() + "/codigo-alta")
                        .with(comoUsuario(usuarioDePrueba(usuarios, Rol.DESPACHO))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdminListaYRevocaDispositivos() throws Exception {
        Usuario admin = usuarioDePrueba(usuarios, Rol.ADMIN);
        Usuario taller = usuarios.crear(new Usuarios.Alta("Taller T7 disp", "t7-disp", "", Rol.TALLER));
        String codigo = dispositivos.generarCodigoAlta(taller.getId());
        Dispositivo dispositivo = dispositivos.canjear(codigo, "Tablet T7").dispositivo();

        String filtro = "$[?(@.id==" + dispositivo.getId() + ")]";
        MvcResult listado = mvc.perform(get("/api/dispositivos").with(comoUsuario(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(filtro + ".usuarioId").value(taller.getId().intValue()))
                .andExpect(jsonPath(filtro + ".usuario").value("Taller T7 disp"))
                .andExpect(jsonPath(filtro + ".nombre").value("Tablet T7"))
                .andExpect(jsonPath(filtro + ".revocado").value(false))
                .andExpect(jsonPath(filtro + ".alta").exists())
                .andReturn();
        assertFalse(listado.getResponse().getContentAsString().toLowerCase().contains("hash"));

        mvc.perform(post("/api/dispositivos/" + dispositivo.getId() + "/revocar")
                        .with(comoUsuario(usuarioDePrueba(usuarios, Rol.DESPACHO))).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dispositivos/" + dispositivo.getId() + "/revocar")
                        .with(comoUsuario(admin)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/dispositivos").with(comoUsuario(admin)))
                .andExpect(jsonPath(filtro + ".revocado").value(true));

        mvc.perform(post("/api/dispositivos/999999/revocar").with(comoUsuario(admin)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No hay ningún dispositivo con id 999999"));
    }
}
