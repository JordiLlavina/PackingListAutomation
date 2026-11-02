package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.persistence.ArchivoComentariosEmbalaje;
import com.puntotres.packinglist.persistence.ComentarioEmbalaje;

/**
 * La pantalla de comentarios tipo de embalaje: alta, corrección y borrado, el
 * nombre repetido como error visible y que lo guardado aparezca de verdad en
 * el desplegable del formulario de la hoja, que es para lo que existe.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ComentariosEmbalajeControllerTest {

    private static final String TIPO_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PREFIJO = "EMB6 ";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArchivoComentariosEmbalaje archivo;

    /** Las pruebas comparten base: se borra solo lo propio, por el prefijo. */
    @AfterEach
    void limpiar() {
        archivo.todos().stream()
                .filter(c -> c.getNombre().startsWith(PREFIJO))
                .forEach(c -> archivo.borrar(c.getId()));
    }

    private ComentarioEmbalaje porNombre(String nombre) {
        return archivo.todos().stream().filter(c -> c.getNombre().equals(nombre))
                .findFirst().orElse(null);
    }

    @Test
    void altaEdicionYBorradoPorLaPantalla() throws Exception {
        mvc.perform(post("/comentarios-embalaje")
                        .param("nombre", PREFIJO + "Cajas pesadas")
                        .param("texto", "Cajas de más de 15 kg"))
                .andExpect(redirectedUrl("/comentarios-embalaje"))
                .andExpect(flash().attributeExists("mensaje"));
        ComentarioEmbalaje alta = porNombre(PREFIJO + "Cajas pesadas");
        assertEquals("Cajas de más de 15 kg", alta.getTexto());

        // La pantalla lo lista, con su texto en un textarea.
        mvc.perform(get("/comentarios-embalaje"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(PREFIJO + "Cajas pesadas")))
                .andExpect(content().string(containsString("Cajas de más de 15 kg")))
                .andExpect(content().string(containsString("<textarea")))
                .andExpect(content().string(containsString("href=\"/info-embalaje\"")));

        // Corregir: mismo id, otro nombre y otro texto.
        mvc.perform(post("/comentarios-embalaje")
                        .param("id", String.valueOf(alta.getId()))
                        .param("nombre", PREFIJO + "Cajas muy pesadas")
                        .param("texto", "Más de 20 kg"))
                .andExpect(redirectedUrl("/comentarios-embalaje"))
                .andExpect(flash().attributeExists("mensaje"));
        ComentarioEmbalaje editado = archivo.uno(alta.getId()).orElseThrow();
        assertEquals(PREFIJO + "Cajas muy pesadas", editado.getNombre());
        assertEquals("Más de 20 kg", editado.getTexto());

        mvc.perform(post("/comentarios-embalaje/borrar").param("id", String.valueOf(alta.getId())))
                .andExpect(redirectedUrl("/comentarios-embalaje"))
                .andExpect(flash().attributeExists("mensaje"));
        assertTrue(archivo.uno(alta.getId()).isEmpty());
    }

    @Test
    void unNombreRepetidoVuelveConError() throws Exception {
        archivo.guardar(null, PREFIJO + "Frágil", "Manejar con cuidado");

        mvc.perform(post("/comentarios-embalaje")
                        .param("nombre", PREFIJO + "FRÁGIL")
                        .param("texto", "otro texto"))
                .andExpect(redirectedUrl("/comentarios-embalaje"))
                .andExpect(flash().attribute("mensajeError", containsString("Ya hay un comentario")));
        assertEquals(1, archivo.todos().stream()
                .filter(c -> c.getNombre().toUpperCase().startsWith(PREFIJO)).count());
    }

    @Test
    void volverLlevaALaEntradaSinPedidoYAlFormularioConPedidoCargado() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(get("/comentarios-embalaje").session(sesion))
                .andExpect(content().string(containsString("href=\"/info-embalaje\">← Volver")));

        try (var entrada = getClass().getResourceAsStream("/ejemplos/APC_PEDIDO_SPRING27.xlsx")) {
            mvc.perform(multipart("/info-embalaje/cargar")
                            .file(new MockMultipartFile("pedido", "APC_PEDIDO_SPRING27.xlsx",
                                    TIPO_XLSX, entrada.readAllBytes()))
                            .param("cliente", "APC")
                            .param("temporada", "Spring 27")
                            .session(sesion))
                    .andExpect(redirectedUrl("/info-embalaje/formulario"));
        }
        mvc.perform(get("/comentarios-embalaje").session(sesion))
                .andExpect(content().string(containsString("href=\"/info-embalaje/formulario\">← Volver")));
    }

    @Test
    void elFormularioDeEmbalajeOfreceLosComentarios() throws Exception {
        mvc.perform(post("/comentarios-embalaje")
                        .param("nombre", PREFIJO + "Frágil EMB")
                        .param("texto", "Texto largo del comentario"));

        MockHttpSession sesion = new MockHttpSession();
        try (var entrada = getClass().getResourceAsStream("/ejemplos/APC_PEDIDO_SPRING27.xlsx")) {
            mvc.perform(multipart("/info-embalaje/cargar")
                            .file(new MockMultipartFile("pedido", "APC_PEDIDO_SPRING27.xlsx",
                                    TIPO_XLSX, entrada.readAllBytes()))
                            .param("cliente", "APC")
                            .param("temporada", "Spring 27")
                            .session(sesion))
                    .andExpect(redirectedUrl("/info-embalaje/formulario"));
        }
        mvc.perform(get("/info-embalaje/formulario").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(PREFIJO + "Frágil EMB")));
    }
}
