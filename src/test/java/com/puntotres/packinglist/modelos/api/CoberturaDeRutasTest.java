package com.puntotres.packinglist.modelos.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Ninguna ruta de la API de modelos puede quedarse abierta por descuido: toda
 * la que declare un controlador de {@code modelos.api} vive bajo {@code /api/},
 * {@code /m/} o {@code /app} (las rutas que protege la cadena de seguridad de
 * la app nueva) y, salvo las de {@link #PUBLICAS}, contesta 401 a quien no ha
 * entrado. Un controlador nuevo queda cubierto sin tocar este test; abrir una
 * ruta obliga a escribirla aquí.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CoberturaDeRutasTest {

    private static final String PAQUETE_API = "com.puntotres.packinglist.modelos.api";

    /** Las únicas rutas a las que se llega sin haber entrado. */
    private static final Set<String> PUBLICAS = Set.of(
            "POST /api/sesion",
            "POST /api/sesion/dispositivo",
            "GET /api/sesion",
            "GET /m/{id}",
            "GET /app/**");

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeo;

    @Test
    void todaRutaDeLaApiEstaBajoLaCadenaNuevaYCerradaSalvoLasPublicas() throws Exception {
        List<String> comprobadas = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeo.getHandlerMethods().entrySet()) {
            if (!entrada.getValue().getBeanType().getPackageName().equals(PAQUETE_API)) {
                continue;
            }
            RequestMappingInfo info = entrada.getKey();
            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
            for (String patron : info.getPatternValues()) {
                assertTrue(patron.startsWith("/api/") || patron.startsWith("/m/") || patron.startsWith("/app"),
                        "ruta fuera de la cadena de la app de modelos: " + patron);
                for (RequestMethod metodo : metodos.isEmpty() ? Set.of(RequestMethod.GET) : metodos) {
                    String ruta = metodo.name() + " " + patron;
                    if (PUBLICAS.contains(ruta)) {
                        continue;
                    }
                    String url = patron.replace("/**", "/x").replaceAll("\\{[^}]+\\}", "1");
                    // Con token CSRF: lo que se comprueba aquí es la falta de sesión, no la de token.
                    int estado = mvc.perform(request(HttpMethod.valueOf(metodo.name()), url).with(csrf()))
                            .andReturn().getResponse().getStatus();
                    assertEquals(401, estado, ruta + " contesta sin haber entrado");
                    comprobadas.add(ruta);
                }
            }
        }
        assertFalse(comprobadas.isEmpty(), "no se ha comprobado ninguna ruta: ¿ha cambiado el paquete de la API?");
        assertTrue(comprobadas.contains("DELETE /api/sesion"), comprobadas.toString());
    }
}
