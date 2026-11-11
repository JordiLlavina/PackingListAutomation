package com.puntotres.packinglist.modelos.api;

import static com.puntotres.packinglist.modelos.ComoUsuario.comoUsuario;
import static com.puntotres.packinglist.modelos.ComoUsuario.usuarioDePrueba;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.modelos.persistence.Dispositivo;
import com.puntotres.packinglist.modelos.persistence.DispositivoRepository;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.SuscripcionPush;
import com.puntotres.packinglist.modelos.persistence.SuscripcionPushRepository;
import com.puntotres.packinglist.modelos.persistence.Usuario;
import com.puntotres.packinglist.modelos.seguridad.AutenticacionModelos;
import com.puntotres.packinglist.modelos.service.EnviadorPush;
import com.puntotres.packinglist.modelos.service.EnviadorPushDesactivado;
import com.puntotres.packinglist.modelos.service.Usuarios;

/**
 * La API del Web Push: la clave pública que necesita el navegador para
 * suscribirse, y dar de alta y de baja la suscripción. Sin sesión contesta
 * 401, como toda la API (lo comprueba {@code CoberturaDeRutasTest}). Los
 * endpoints de este test se llaman {@code …/T17-…}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PushControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private DispositivoRepository dispositivos;

    @Autowired
    private SuscripcionPushRepository suscripciones;

    @Autowired
    private EnviadorPush enviador;

    @Test
    void laClavePublicaDiceSiHayPushYSinClavesEstaApagado() throws Exception {
        mvc.perform(get("/api/push/clave-publica").with(comoUsuario(usuarioDePrueba(usuarios, Rol.TALLER))))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"clave\":\"\",\"configurado\":false}", true));
        assertInstanceOf(EnviadorPushDesactivado.class, enviador,
                "sin claves VAPID en los tests, el push está apagado");
    }

    @Test
    void suscribirseYDarseDeBajaContestan204() throws Exception {
        Usuario taller = usuarioDePrueba(usuarios, Rol.TALLER);
        Dispositivo tablet = dispositivos.save(new Dispositivo(taller.getId(), "Tablet T17", "hash-t17-tablet"));
        String endpoint = "https://push.example/T17-controlador";

        mvc.perform(post("/api/push/suscripcion")
                        .with(authentication(AutenticacionModelos.de(taller, tablet.getId()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + endpoint + "\",\"p256dh\":\"BPclave\",\"auth\":\"secreto\"}"))
                .andExpect(status().isNoContent());
        List<SuscripcionPush> suyas = conEndpoint(endpoint);
        assertEquals(1, suyas.size());
        assertEquals(taller.getId(), suyas.get(0).getUsuarioId());
        assertEquals(tablet.getId(), suyas.get(0).getDispositivoId(), "se apunta con qué aparato se suscribió");
        assertEquals("BPclave", suyas.get(0).getP256dh());
        assertEquals("secreto", suyas.get(0).getAuth());

        mvc.perform(post("/api/push/suscripcion").with(comoUsuario(taller)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\" \",\"p256dh\":\"x\",\"auth\":\"y\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/push/suscripcion")
                        .with(authentication(AutenticacionModelos.de(taller, tablet.getId()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}"))
                .andExpect(status().isNoContent());
        assertTrue(conEndpoint(endpoint).isEmpty());
    }

    @Test
    void unaSuscripcionQueNoEsDeUnServidorDePushContesta400() throws Exception {
        Usuario taller = usuarioDePrueba(usuarios, Rol.TALLER);
        String largo = "https://push.example/T17-" + "x".repeat(2000);
        for (String cuerpo : List.of(
                "{\"endpoint\":\"http://push.example/T17-sin-tls\",\"p256dh\":\"BPclave\",\"auth\":\"secreto\"}",
                "{\"endpoint\":\"file:///etc/passwd\",\"p256dh\":\"BPclave\",\"auth\":\"secreto\"}",
                "{\"endpoint\":\"http://localhost.otro.example/T17-sin-tls\",\"p256dh\":\"BPclave\",\"auth\":\"x\"}",
                "{\"endpoint\":\"" + largo + "\",\"p256dh\":\"BPclave\",\"auth\":\"secreto\"}",
                "{\"endpoint\":\"https://push.example/T17-p256dh\",\"p256dh\":\"" + "p".repeat(201)
                        + "\",\"auth\":\"secreto\"}",
                "{\"endpoint\":\"https://push.example/T17-auth\",\"p256dh\":\"BPclave\",\"auth\":\"" + "a".repeat(101)
                        + "\"}")) {
            mvc.perform(post("/api/push/suscripcion").with(comoUsuario(taller)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"mensaje\":\"Suscripción no válida\"}"));
        }
        assertTrue(suscripciones.findAll().stream().noneMatch(s -> s.getEndpoint().contains("T17-sin-tls")
                || s.getEndpoint().contains("T17-p256dh") || s.getEndpoint().contains("T17-auth")));

        // En desarrollo, el navegador de la propia máquina.
        for (String local : List.of("http://localhost:8080/T17-local", "http://127.0.0.1:8080/T17-local")) {
            mvc.perform(post("/api/push/suscripcion").with(comoUsuario(taller)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"endpoint\":\"" + local + "\",\"p256dh\":\"BPclave\",\"auth\":\"secreto\"}"))
                    .andExpect(status().isNoContent());
            assertEquals(1, conEndpoint(local).size());
        }
    }

    private List<SuscripcionPush> conEndpoint(String endpoint) {
        return suscripciones.findAll().stream().filter(s -> endpoint.equals(s.getEndpoint())).toList();
    }
}
