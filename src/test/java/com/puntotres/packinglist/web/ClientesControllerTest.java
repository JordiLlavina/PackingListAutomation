package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.puntotres.packinglist.config.CatalogoClientes;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;

/**
 * Los clientes de plantilla genérica se dan de alta, se editan, se hacen
 * inactivos y se eliminan desde /clientes, sin tocar el yml ni reiniciar.
 *
 * La base de datos es la misma para toda la suite: cada test usa su propio
 * nombre de cliente.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClientesControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CatalogoClientes catalogo;

    @Autowired
    private JdbcTemplate jdbc;

    private static MockHttpServletRequestBuilder alta(String nombre) {
        return post("/clientes")
                .param("nombre", nombre)
                .param("nombreLegal", nombre.toUpperCase() + " SL")
                .param("direccionEntrega", "C/ Mayor 1; 08001 BARCELONA")
                .param("placeholderTemporada", "E27")
                .param("etiquetaPedido", "Order nº");
    }

    @Test
    void unClienteDadoDeAltaSaleEnElCatalogoConLaPlantillaGenerica() throws Exception {
        mvc.perform(alta("Alta Nueva"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clientes"));

        ClienteConfig cliente = catalogo.clientePara("ALTA NUEVA").orElseThrow();
        assertEquals("Alta Nueva", cliente.getNombre());
        assertEquals(TipoPlantilla.GENERIC, cliente.getPlantilla());
        assertEquals("ALTA NUEVA SL", cliente.getNombreLegal());
        assertEquals("C/ Mayor 1; 08001 BARCELONA", cliente.getDireccionEntrega());
        assertEquals("E27", cliente.getPlaceholderTemporada());
        assertEquals("Order nº", cliente.getEtiquetaPedido());

        mvc.perform(get("/clientes")).andExpect(content().string(containsString("ALTA NUEVA SL")));
        // Y en los desplegables de las demás pantallas, sin reiniciar.
        mvc.perform(get("/temporadas")).andExpect(content().string(containsString("Alta Nueva")));
    }

    @Test
    void sinNombreLegalNoSeDaDeAlta() throws Exception {
        mvc.perform(post("/clientes").param("nombre", "Sin Legal").param("direccionEntrega", "Calle 1"))
                .andExpect(redirectedUrl("/clientes"))
                .andExpect(flash().attributeExists("mensajeError"));

        assertTrue(catalogo.clientePara("SIN LEGAL").isEmpty());
    }

    @Test
    void unNombreQueYaExisteNoSeDaDeAltaOtraVez() throws Exception {
        mvc.perform(alta("ami")).andExpect(flash().attributeExists("mensajeError"));
        mvc.perform(alta(" ackermann ")).andExpect(flash().attributeExists("mensajeError"));

        assertEquals(TipoPlantilla.AMI, catalogo.clientePara("AMI").orElseThrow().getPlantilla());
        assertEquals("Ackermann", catalogo.clientePara("ACKERMANN").orElseThrow().getNombre());
    }

    @Test
    void editarUnClienteCambiaSusDatosPeroNoSuClave() throws Exception {
        mvc.perform(alta("Corregible"));

        mvc.perform(post("/clientes")
                        .param("clave", "CORREGIBLE")
                        .param("nombre", "Corregible S.A.")
                        .param("nombreLegal", "CORREGIBLE SOCIEDAD ANONIMA")
                        .param("direccionEntrega", "Otra calle 2"))
                .andExpect(flash().attributeExists("mensaje"));

        // La clave es la que guardan sus temporadas y modelos: no cambia.
        ClienteConfig cliente = catalogo.clientePara("CORREGIBLE").orElseThrow();
        assertEquals("Corregible S.A.", cliente.getNombre());
        assertEquals("CORREGIBLE SOCIEDAD ANONIMA", cliente.getNombreLegal());
        assertEquals("Otra calle 2", cliente.getDireccionEntrega());
        assertEquals("Nº pedido", cliente.getEtiquetaPedido(), "vaciar el rótulo vuelve al genérico");
        assertTrue(catalogo.clientePara("CORREGIBLE S.A.").isEmpty());
    }

    @Test
    void eliminarSinLaContrasenaNoEliminaNada() throws Exception {
        mvc.perform(alta("Protegido"));

        for (String mala : new String[] {"", "eliminar", "Eliminar", "ELIMINAR "}) {
            mvc.perform(post("/clientes/eliminar").param("clave", "PROTEGIDO").param("contrasena", mala))
                    .andExpect(redirectedUrl("/clientes"))
                    .andExpect(flash().attributeExists("mensajeError"));
        }
        mvc.perform(post("/clientes/eliminar").param("clave", "PROTEGIDO"))
                .andExpect(flash().attributeExists("mensajeError"));

        assertTrue(catalogo.clientePara("PROTEGIDO").isPresent());
    }

    @Test
    void conLaContrasenaSeEliminaUnClienteSinNadaGuardado() throws Exception {
        mvc.perform(alta("Efimero"));

        mvc.perform(post("/clientes/eliminar").param("clave", "EFIMERO").param("contrasena", "ELIMINAR"))
                .andExpect(redirectedUrl("/clientes"))
                .andExpect(flash().attributeExists("mensaje"));

        assertTrue(catalogo.clientePara("EFIMERO").isEmpty());
    }

    @Test
    void unClienteConDatosGuardadosNoSeEliminaYLaPantallaDiceQueTiene() throws Exception {
        mvc.perform(alta("Con Colores"));
        jdbc.update("insert into color_cliente (cliente, codigo, descripcion, fecha_actualizacion) "
                + "values ('CON COLORES', 'NE', 'NEGRO', current_timestamp)");

        mvc.perform(post("/clientes/eliminar").param("clave", "CON COLORES").param("contrasena", "ELIMINAR"))
                .andExpect(flash().attribute("mensajeError", containsString("1 color.")));

        assertTrue(catalogo.clientePara("CON COLORES").isPresent());
        String pagina = mvc.perform(get("/clientes")).andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains("No se puede eliminar: tiene 1 color."));
        assertFalse(pagina.contains("data-clave=\"CON COLORES\""), "sin botón de eliminar");
    }

    @Test
    void losClientesProgramadosNoSeEliminan() throws Exception {
        mvc.perform(post("/clientes/eliminar").param("clave", "APC").param("contrasena", "ELIMINAR"))
                .andExpect(flash().attributeExists("mensajeError"));

        assertEquals(TipoPlantilla.APC, catalogo.clientePara("APC").orElseThrow().getPlantilla());
        assertFalse(mvc.perform(get("/clientes")).andReturn().getResponse().getContentAsString()
                .contains("data-clave=\"APC\""));
    }

    @Test
    void unClienteInactivoNoSaleEnLosDesplegablesPeroNoSePierde() throws Exception {
        mvc.perform(alta("Cliente Dormido"));
        jdbc.update("insert into color_cliente (cliente, codigo, descripcion, fecha_actualizacion) "
                + "values ('CLIENTE DORMIDO', 'NE', 'NEGRO', current_timestamp)");

        mvc.perform(post("/clientes/inactivar").param("clave", "CLIENTE DORMIDO"))
                .andExpect(redirectedUrl("/clientes"))
                .andExpect(flash().attribute("mensaje", containsString("Cliente Dormido")));

        // Fuera de todos los desplegables y filtros, que salen de getClientes()…
        assertFalse(catalogo.getClientes().containsKey("CLIENTE DORMIDO"));
        assertFalse(mvc.perform(get("/temporadas")).andReturn().getResponse().getContentAsString()
                .contains("Cliente Dormido"));
        // …pero lo que tiene guardado sigue encontrando a su cliente.
        assertEquals("Cliente Dormido", catalogo.clientePara("CLIENTE DORMIDO").orElseThrow().getNombre());

        // En /clientes sigue en la lista, marcado y con el botón para reactivarlo.
        String pagina = mvc.perform(get("/clientes")).andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains("Cliente Dormido"));
        assertTrue(pagina.contains("Inactivo"));
        assertTrue(pagina.contains("Reactivar"));
    }

    @Test
    void reactivarDevuelveElClienteALosDesplegables() throws Exception {
        mvc.perform(alta("Cliente Vuelve"));
        mvc.perform(post("/clientes/inactivar").param("clave", "CLIENTE VUELVE"));
        assertFalse(catalogo.getClientes().containsKey("CLIENTE VUELVE"));

        mvc.perform(post("/clientes/reactivar").param("clave", "CLIENTE VUELVE"))
                .andExpect(redirectedUrl("/clientes"))
                .andExpect(flash().attributeExists("mensaje"));

        assertTrue(catalogo.getClientes().containsKey("CLIENTE VUELVE"));
        mvc.perform(get("/temporadas")).andExpect(content().string(containsString("Cliente Vuelve")));
    }

    @Test
    void unNombreDeUnClienteInactivoNoSeDaDeAltaOtraVez() throws Exception {
        mvc.perform(alta("Cliente Repetido"));
        mvc.perform(post("/clientes/inactivar").param("clave", "CLIENTE REPETIDO"));

        mvc.perform(alta("cliente repetido"))
                .andExpect(flash().attribute("mensajeError", containsString("inactivo")));
    }

    @Test
    void losClientesProgramadosNoSeHacenInactivos() throws Exception {
        mvc.perform(post("/clientes/inactivar").param("clave", "AMI"))
                .andExpect(flash().attributeExists("mensajeError"));

        assertTrue(catalogo.getClientes().containsKey("AMI"));
    }

    @Test
    void laPantallaDiceEditarYNoCorregir() throws Exception {
        mvc.perform(alta("Cliente Editable"));

        String pagina = mvc.perform(get("/clientes")).andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains(">Editar<"));
        assertFalse(pagina.contains("Corregir"));
        String editando = mvc.perform(get("/clientes").param("editar", "CLIENTE EDITABLE"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(editando.contains("Editar Cliente Editable"));
    }

    @Test
    void elMenuLlevaClientesEnElPieYLosPanelesAlFinalDeLaRejilla() throws Exception {
        String menu = mvc.perform(get("/menu")).andReturn().getResponse().getContentAsString();

        // Los paneles pequeños, detrás de la última tarjeta grande (abajo a la
        // derecha), y Clientes en el pie, debajo de ellos.
        int hojasProduccion = menu.indexOf("href=\"/hojas-produccion\"");
        int paneles = menu.indexOf("class=\"menu-paneles\"");
        int pie = menu.indexOf("menu-pie");
        int escandallos = menu.indexOf("href=\"/clickup\"");
        int clientes = menu.indexOf("href=\"/clientes\"");
        assertTrue(hojasProduccion > 0 && hojasProduccion < paneles, "los paneles van detrás de las tarjetas");
        // ClickUp bajó al pie para dejar su sitio a las Hojas de Producción, y
        // Clientes sigue detrás, en la última columna.
        assertTrue(paneles < pie && pie < escandallos && escandallos < clientes,
                "ClickUp y Clientes van en el pie, debajo de los paneles");
    }
}
