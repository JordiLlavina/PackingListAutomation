package com.puntotres.packinglist.shipping.api;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ShipmentsApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private long crearContacto(String empresa, String pais) throws Exception {
        mvc.perform(post("/envios/contactos").param("empresa", empresa).param("direccion1", "Calle 1")
                .param("ciudad", "Ciudad").param("pais", pais).param("telefono", "+34600000000"));
        JsonNode lista = json.readTree(mvc.perform(get("/api/contacts").param("q", empresa)).andReturn().getResponse().getContentAsString());
        return lista.get(0).get("id").asLong();
    }

    @Test
    void crearBorradorLeerEditarYCancelar() throws Exception {
        long origen = crearContacto("API ORIGEN", "ES");
        long destino = crearContacto("API DESTINO", "MA");
        String entrada = """
            {"remitenteId": %d, "destinatarioId": %d, "fechaRecogida": "2030-01-10", "incoterm": "DAP",
             "responsable": "Jordi", "cliente": "AMI", "referencia": "API-1",
             "bultos": [{"peso": 2.5, "largo": 40, "ancho": 30, "alto": 20}],
             "lineasAduana": [{"descripcion": "Bolsos", "codigoHs": "42022100", "cantidad": 3, "valorUnitario": 40, "pesoNeto": 1, "pesoBruto": 1.5, "paisOrigen": "MA"}]}
            """.formatted(origen, destino);

        String respuesta = mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("DRAFT"))
                .andExpect(jsonPath("$.sentido").value("EXPORT"))
                .andExpect(jsonPath("$.aduanero").value(true))
                .andExpect(jsonPath("$.valorDeclarado", org.hamcrest.Matchers.closeTo(120.0, 0.001)))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();

        mvc.perform(get("/api/shipments/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.bultos", hasSize(1)));
        mvc.perform(get("/api/shipments").param("estado", "DRAFT")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == %d)]".formatted(id)).exists());

        mvc.perform(put("/api/shipments/{id}", id).contentType(MediaType.APPLICATION_JSON).content(entrada.replace("\"API-1\"", "\"API-2\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.referencia").value("API-2"));

        mvc.perform(post("/api/shipments/{id}/cancel", id)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELLED"));
        mvc.perform(post("/api/shipments/{id}/cancel", id)).andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("CANCELLED")));
    }

    @Test
    void tarifarSinDatosDevuelve422ConLosProblemas() throws Exception {
        String respuesta = mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content("{\"responsable\": \"Jordi\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();

        mvc.perform(post("/api/shipments/{id}/quote", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detalles", hasItem(containsString("remitente"))));
    }

    @Test
    void tarifarSinCredencialesDeDhlDevuelve502ConElMensajeDelTransportista() throws Exception {
        long origen = crearContacto("API ORIGEN 2", "ES");
        long destino = crearContacto("API DESTINO 2", "MA");
        long cuenta = json.readTree(mvc.perform(get("/api/billing-accounts")).andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        long motivo = json.readTree(mvc.perform(get("/api/reasons")).andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        String entrada = """
            {"remitenteId": %d, "destinatarioId": %d, "fechaRecogida": "2030-01-10", "responsable": "Jordi",
             "cuentaTransporteId": %d, "motivoId": %d,
             "bultos": [{"peso": 2.5, "largo": 40, "ancho": 30, "alto": 20}],
             "lineasAduana": [{"descripcion": "Bolsos", "codigoHs": "42022100", "cantidad": 3, "valorUnitario": 40, "pesoNeto": 1, "pesoBruto": 1.5, "paisOrigen": "MA"}]}
            """.formatted(origen, destino, cuenta, motivo);
        long id = json.readTree(mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/shipments/{id}/quote", id))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensaje", containsString("DHL_API_KEY")));
    }

    @Test
    void unEnvioInexistenteEs404YUnaTransicionIlegal409() throws Exception {
        mvc.perform(get("/api/shipments/999999")).andExpect(status().isNotFound());
        String respuesta = mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content("{\"responsable\": \"Jordi\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();
        mvc.perform(post("/api/shipments/{id}/confirm", id).contentType(MediaType.APPLICATION_JSON).content("{\"productCode\": \"P\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/shipments/{id}/create", id)).andExpect(status().isConflict());
    }

    @Test
    void unaReferenciaDemasiadoLargaEs422ConElCampo() throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsable\": \"Jordi\", \"referencia\": \"" + "X".repeat(40) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detalles[0]", containsString("referencia")));
    }

    /** Un id que no existe (un contacto borrado entre que un script lo leyó y lo mandó) no se traga en silencio como null. */
    @Test
    void unRemitenteQueNoExisteEs422ConElId() throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsable\": \"Jordi\", \"remitenteId\": 999999}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje", containsString("999999")));
    }
}
