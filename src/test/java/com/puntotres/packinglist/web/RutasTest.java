package com.puntotres.packinglist.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RutasTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void laRaizLlevaAlMenu() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void elMenuMuestraLasDosSecciones() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(status().isOk())
                .andExpect(view().name("menu"))
                // Que sean href de un enlace, no solo texto suelto en la página.
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("href=\"/packing-list\"")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("href=\"/etiquetas-articulo\"")));
    }

    @Test
    void elCssSeSirveConElHashDeSuContenidoEnElNombre() throws Exception {
        // Sin esto el navegador se queda con la copia vieja de estilo.css tras
        // cada cambio de estilos y hay que vaciar la caché a mano: un fallo
        // invisible desde el código, que solo se nota mirando la pantalla.
        mvc.perform(get("/menu"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.matchesPattern(
                                "(?s).*href=\"/estilo-[0-9a-f]{32}\\.css\".*")));
    }

    @Test
    void laPestanaLlevaElIconoYElFicheroExiste() throws Exception {
        // El <link> y el PNG viven en sitios distintos —plantilla y static/—,
        // así que borrar uno deja al otro apuntando al vacío y el navegador se
        // limita a poner el icono en blanco, sin decir nada.
        mvc.perform(get("/menu"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.matchesPattern(
                                "(?s).*rel=\"icon\"[^>]*/icono-web-32-[0-9a-f]{32}\\.png\".*")));

        org.junit.jupiter.api.Assertions.assertTrue(
                new org.springframework.core.io.ClassPathResource(
                        "static/icono-web-180.png").exists(),
                "el icono grande también, que es el que usan pestaña y marcadores");
    }

    @Test
    void elAmarilloDeLosPesosQueFaltanNoTineLaFilaEntera() throws Exception {
        // Si vuelve a ser "tr.pendiente td", tapa la banda de color del palet:
        // las dos reglas tienen la misma especificidad y esta va después. Y en
        // un envío recién generado desde el packing del taller, que llega sin
        // ningún peso, la taparía en TODAS las filas. Desde el código no se ve.
        String css = new String(new org.springframework.core.io.ClassPathResource(
                "static/estilo.css").getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        org.junit.jupiter.api.Assertions.assertTrue(
                css.contains("tr.pendiente td.celda-peso"),
                "el amarillo va en la celda de peso, no en la fila");
        org.junit.jupiter.api.Assertions.assertFalse(
                css.contains("tr.pendiente td {"),
                "teñir la fila entera borra la banda del palet");
    }

    @Test
    void losAvisosDePaletDeLasEtiquetasSeTinenAparteDeLosDeCaja() throws Exception {
        // El usuario los mira en otro momento —cuando ya está montando el
        // bulto—, así que mezclados con los de caja se pierden. Y cada
        // destinación necesita margen propio: sin él los bloques se leen como
        // una sola lista larga, que es de lo que se venía. Desde el código no
        // se ve ninguna de las dos cosas.
        String css = new String(new org.springframework.core.io.ClassPathResource(
                "static/estilo.css").getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        org.junit.jupiter.api.Assertions.assertTrue(
                css.contains(".linea-detalle.palet"),
                "los avisos de palet llevan su propio matiz");
        org.junit.jupiter.api.Assertions.assertTrue(
                css.contains(".resumen-aviso.palet"),
                "también en el resumen, que es lo que se lee sin desplegar");
        org.junit.jupiter.api.Assertions.assertTrue(
                css.matches("(?s).*\\.bloque-avisos \\{[^}]*margin:[^}]*\\}.*"),
                "cada destinación separada de la siguiente");
    }

    @Test
    void elAsistenteDePackingListViveEnSuPropiaRuta() throws Exception {
        mvc.perform(get("/packing-list"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"));
    }

    @Test
    void laPantallaDeTarasResponde() throws Exception {
        mvc.perform(get("/taras"))
                .andExpect(status().isOk())
                .andExpect(view().name("taras"));
    }

    @Test
    void elMenuEnlazaLasTaras() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("href=\"/taras\"")));
    }

    @Test
    void elAjusteDelTallerSinEntregaEnCursoDevuelveALaEntrada() throws Exception {
        mvc.perform(get("/packing-list/taller/ajuste"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/packing-list"));
    }

    @Test
    void sinEnvioEnCursoLaRevisionVuelveAlPasoUno() throws Exception {
        mvc.perform(get("/revision"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/packing-list"));
    }

    @Test
    void lasEtiquetasDeArticuloAbrenSuPropioMenu() throws Exception {
        // Producción es lo único hecho; SMS y prototipos se enseñan apagados
        // en vez de esconderlos, para que se vea que están contemplados.
        mvc.perform(get("/etiquetas-articulo"))
                .andExpect(status().isOk())
                .andExpect(view().name("etiquetas-articulo"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/etiquetas-articulo-produccion\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SMS")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Prototipos")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("En desarrollo")));
    }

    @Test
    void laPantallaDeEtiquetasDeArticuloOfreceElFormulario() throws Exception {
        mvc.perform(get("/etiquetas-articulo-produccion"))
                .andExpect(status().isOk())
                .andExpect(view().name("etiquetas-articulo-produccion"))
                // El desplegable y el input del excel de pedido.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cliente")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pedido")));
    }

    @Test
    void sinNadaGeneradoLosResultadosVuelvenAlFormulario() throws Exception {
        mvc.perform(get("/etiquetas-articulo-produccion/resultados"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/etiquetas-articulo-produccion"));
    }

    @Test
    void laCabeceraDiceEnQueSeccionEstasYLaMarcaVuelveAlMenu() throws Exception {
        // El título de la barra era siempre el mismo, así que no informaba de
        // nada; ahora nombra la sección y el enlace de vuelta es la marca.
        String html = mvc.perform(get("/taras"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(
                html.contains("Taras de los cartones"), "el título es el de la sección");
        org.junit.jupiter.api.Assertions.assertFalse(
                html.contains("Automatizaciones de Puntotres"),
                "fuera de la portada, ese título no dice en qué pantalla estás");
        org.junit.jupiter.api.Assertions.assertTrue(
                html.matches("(?s).*<a href=\"/menu\" class=\"enlace-menu\".*<img class=\"marca\".*"),
                "la vuelta al menú es la marca, no el título");
    }
}
