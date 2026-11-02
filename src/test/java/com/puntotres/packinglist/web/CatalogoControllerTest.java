package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoArticulo;
import com.puntotres.packinglist.service.corte.ArticuloCorte;
import com.puntotres.packinglist.service.corte.ColorCorte;
import com.puntotres.packinglist.service.corte.PedidoCorte;
import com.puntotres.packinglist.service.corte.ReferenciaCorte;

/**
 * La pantalla del catálogo de modelos: una tabla por cliente con una fila por
 * artículo agrupada por modelo, que se edita celda a celda por fetch y se
 * filtra por temporada. Cada test usa referencias propias (ULL93x).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private ArchivoTemporadas archivo;

    @SuppressWarnings("unchecked")
    private List<FilaCatalogoVista> filas(MvcResult resultado) {
        return (List<FilaCatalogoVista>) resultado.getModelAndView().getModel().get("filas");
    }

    private FilaCatalogoVista filaDe(List<FilaCatalogoVista> filas, String modelo) {
        return filas.stream().filter(fila -> fila.codigoModelo().equals(modelo)).findFirst().orElseThrow();
    }

    @Test
    void elMenuEnlazaElCatalogo() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(content().string(containsString("href=\"/catalogo\"")))
                .andExpect(content().string(containsString("Catálogo de modelos")));
    }

    @Test
    void laPantallaAgrupaLosArticulosPorModelo() throws Exception {
        catalogo.asegurarArticulo("AMI", "ULL950.AL0001");
        catalogo.asegurarArticulo("AMI", "ULL950.AL0002");

        MvcResult resultado = mvc.perform(get("/catalogo").param("cliente", "AMI"))
                .andExpect(status().isOk())
                .andReturn();

        FilaCatalogoVista fila = filaDe(filas(resultado), "ULL950");
        assertEquals(2, fila.articulos().size());
        assertEquals(List.of("ULL950.AL0001", "ULL950.AL0002"),
                fila.articulos().stream().map(FilaCatalogoVista.ArticuloVista::referencia).toList());
        assertEquals("AL0001", fila.articulos().get(0).refPiel());
    }

    @Test
    void filtraPorTemporada() throws Exception {
        TemporadaGuardada temporada = archivo.guardar(null, "AMI", "CAT-PANTALLA", "p.xlsx", new byte[] {1});
        catalogo.incorporar("AMI", new PedidoCorte(List.of(new ArticuloCorte(
                new ReferenciaCorte("ULL951.AL0001", "ULL951", "AL0001"),
                List.of(new ColorCorte("001", "BLACK", 2)))), List.of()), temporada.getId());
        catalogo.asegurarArticulo("AMI", "ULL952.AL0001");

        List<FilaCatalogoVista> filtradas = filas(mvc.perform(get("/catalogo")
                .param("cliente", "AMI").param("temporada", String.valueOf(temporada.getId()))).andReturn());

        assertTrue(filtradas.stream().anyMatch(fila -> fila.codigoModelo().equals("ULL951")));
        assertFalse(filtradas.stream().anyMatch(fila -> fila.codigoModelo().equals("ULL952")));
        assertEquals(List.of("CAT-PANTALLA"), filaDe(filtradas, "ULL951").temporadas());
        assertEquals("001", filaDe(filtradas, "ULL951").articulos().get(0).colores().get(0).codigoColor());
    }

    @Test
    void guardarUnaCeldaContestaJsonConLasPropagadas() throws Exception {
        catalogo.asegurarArticulo("AMI", "ULL953.AL0001");
        catalogo.asegurarArticulo("AMI", "ULL953.AL0002");

        mvc.perform(post("/catalogo/articulo/celda").param("cliente", "AMI")
                        .param("referencia", "ULL953.AL0001").param("campo", "UNIDADES_POR_CAJA").param("valor", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.propagadas[0].referencia").value("ULL953.AL0002"))
                .andExpect(jsonPath("$.propagadas[0].anterior").value(""));

        assertEquals(7, catalogo.articulo("AMI", "ULL953.AL0002").orElseThrow().getUnidadesPorCaja());
    }

    @Test
    void unValorInvalidoDa400ConMensaje() throws Exception {
        catalogo.asegurarArticulo("AMI", "ULL954.AL0001");

        mvc.perform(post("/catalogo/articulo/celda").param("cliente", "AMI")
                        .param("referencia", "ULL954.AL0001").param("campo", "UNIDADES_POR_CAJA").param("valor", "siete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.mensaje").value(containsString("entero")));
    }

    @Test
    void elTipoDelModeloEsInformativo() throws Exception {
        catalogo.asegurarArticulo("AMI", "ULL955.AL0001");

        mvc.perform(post("/catalogo/modelo/celda").param("cliente", "AMI")
                        .param("codigo", "ULL955").param("campo", "TIPO").param("valor", "CINTURON"))
                .andExpect(status().isOk());

        assertEquals(TipoArticulo.CINTURON, catalogo.modelo("AMI", "ULL955").orElseThrow().getTipo());
        assertEquals("CINTURON", filaDe(filas(mvc.perform(get("/catalogo").param("cliente", "AMI")).andReturn()),
                "ULL955").tipo());
        assertFalse(CajaData.esCinturon("ULL955.AL0001"), "el packing sigue mandando por el prefijo UBL");
    }

    @Test
    void nuevoArticuloSinPielAvisa() throws Exception {
        mvc.perform(post("/catalogo/articulo/nuevo").param("cliente", "APC").param("referencia", "F63099"))
                .andExpect(redirectedUrl("/catalogo?cliente=APC"))
                .andExpect(flash().attribute("aviso", containsString("sin piel")));

        assertTrue(catalogo.articulo("APC", "F63099").isPresent());
    }

    @Test
    void losColoresDelArticuloSeEditanYSeAnaden() throws Exception {
        catalogo.asegurarArticulo("AMI", "ULL956.AL0001");

        mvc.perform(post("/catalogo/color/nuevo").param("cliente", "AMI")
                        .param("referencia", "ULL956.AL0001").param("codigoColor", "001"))
                .andExpect(redirectedUrl("/catalogo?cliente=AMI"));
        mvc.perform(post("/catalogo/color/celda").param("cliente", "AMI").param("referencia", "ULL956.AL0001")
                        .param("codigoColor", "001").param("campo", "COLOR_FORRO").param("valor", "negro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        FilaCatalogoVista.ColorVista color = filaDe(filas(mvc.perform(get("/catalogo").param("cliente", "AMI"))
                .andReturn()), "ULL956").articulos().get(0).colores().get(0);
        assertEquals("001", color.codigoColor());
        assertEquals("NEGRO", color.colorForro());
    }

    @Test
    void borrarUnModeloLoQuitaDeLaPantalla() throws Exception {
        catalogo.asegurarArticulo("AMI", "ULL957.AL0001");

        mvc.perform(post("/catalogo/modelo/borrar").param("cliente", "AMI").param("codigo", "ULL957"))
                .andExpect(redirectedUrl("/catalogo?cliente=AMI"))
                .andExpect(flash().attribute("mensaje", containsString("ULL957")));

        assertTrue(catalogo.modelo("AMI", "ULL957").isEmpty());
    }

    @Test
    void sinClienteLaPantallaPideElegirUno() throws Exception {
        mvc.perform(get("/catalogo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Elige un cliente")));
    }

    @Test
    void laCabeceraLlevaLasVistasYLosChipsDeColumnas() throws Exception {
        // La tabla (y sus data-col) solo se pinta si el cliente tiene algún modelo.
        catalogo.asegurarArticulo("AMI", "ULL958.AL0001");
        String pagina = mvc.perform(get("/catalogo").param("cliente", "AMI"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(pagina.contains("data-vista=\"packaging\"") && pagina.contains("data-vista=\"corte\"")
                && pagina.contains("data-vista=\"fotos\"") && pagina.contains("data-vista=\"todo\""), "las vistas");
        assertTrue(pagina.contains("id=\"columnasActivas\"") && pagina.contains("chip-anadir"), "los chips");
        assertTrue(pagina.contains("id=\"buscar\""), "el buscador");
        assertTrue(pagina.contains("data-col=\"unidades\"") && pagina.contains("data-col=\"forro\""),
                "las columnas se marcan por nombre para poder ocultarlas");
    }
}
