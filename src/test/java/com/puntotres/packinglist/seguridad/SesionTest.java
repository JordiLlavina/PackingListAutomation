package com.puntotres.packinglist.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * La sesión dura ocho horas porque CONTIENE el envío en curso: un timeout de
 * media hora le borra a un operario el trabajo de la mañana en mitad de una
 * revisión de doscientas cajas.
 */
@SpringBootTest
class SesionTest {

    @Autowired
    private ServerProperties servidor;

    @Test
    void laSesionDuraOchoHoras() {
        assertEquals(Duration.ofHours(8), servidor.getServlet().getSession().getTimeout());
    }

    @Test
    void laCookieDeSesionNoLaPuedeLeerElJavaScriptNiViajarPorHttp() {
        assertTrue(servidor.getServlet().getSession().getCookie().getHttpOnly());
        assertTrue(servidor.getServlet().getSession().getCookie().getSecure());
        assertEquals("Lax", servidor.getServlet().getSession().getCookie().getSameSite().attributeValue());
    }
}
