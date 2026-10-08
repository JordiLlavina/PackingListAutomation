package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

/**
 * La pantalla de la Tabla colores Clientes. Las altas van a un cliente que
 * no es APC (LGN) para no tocar la tabla que usan los tests del corte: la
 * base de datos en memoria es la misma para toda la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ColoresControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArchivoTemporadas archivoTemporadas;

    @Test
    void elMenuEnlazaLaTablaDeColoresDebajoDeLasTemporadas() throws Exception {
        String menu = mvc.perform(get("/menu")).andReturn().getResponse().getContentAsString();

        int temporadas = menu.indexOf("href=\"/temporadas\"");
        int colores = menu.indexOf("href=\"/colores\"");
        assertTrue(temporadas > 0 && colores > temporadas, "el panel va debajo del de temporadas");
        assertTrue(menu.contains("Colores y Pieles de Cliente"));
    }

    @Test
    void laTablaDeApcTraeLosColoresDeSuPedidoConElDesplegableDeClientes() throws Exception {
        mvc.perform(get("/colores").param("cliente", "apc"))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*<option value=\"APC\"[^>]*selected[^>]*>.*")))
                .andExpect(content().string(matchesPattern("(?s).*<option value=\"AMI\">AMI<.*")))
                .andExpect(content().string(containsString(">LZZ<")))
                .andExpect(content().string(containsString("value=\"NOIR\"")))
                .andExpect(content().string(containsString(">KBE<")));
    }

    @Test
    void elClienteEsUnTituloConElDesplegableGrandeYSinElTextoDeAyuda() throws Exception {
        mvc.perform(get("/colores").param("cliente", "APC"))
                .andExpect(content().string(containsString("id=\"tituloCliente\">Cliente</h3>")))
                .andExpect(content().string(containsString("class=\"selector-cliente\"")))
                .andExpect(content().string(not(containsString("Cada color tiene un código"))));
    }

    @Test
    void elExcelDePedidoSoloSeOfreceParaLosClientesQueSeSabenLeer() throws Exception {
        for (String cliente : java.util.List.of("AMI", "APC")) {
            mvc.perform(get("/colores").param("cliente", cliente))
                    .andExpect(content().string(containsString("action=\"/colores/pedido\"")));
        }
        mvc.perform(get("/colores").param("cliente", "LGN"))
                .andExpect(content().string(not(containsString("action=\"/colores/pedido\""))));
    }

    @Test
    void delExcelDeAmiSeAnadenLosNuevosConSuDescripcionYNoSePisaLoQueYaEstaba() throws Exception {
        // Un hueco que el pedido rellena y uno ya escrito que no se toca.
        mvc.perform(post("/colores/colores/nuevo").param("cliente", "AMI").param("codigo", "Z92").param("valor", ""));
        mvc.perform(post("/colores/colores/nuevo").param("cliente", "AMI").param("codigo", "Z93")
                .param("valor", "PUESTO A MANO"));
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL901.AL0103", "Z91", "ZETA ROJO", "U", "07704 CH", null, null, 4),
                new Fila("SPAIN", "ULL901.AL0103", "Z91", "ZETA ROJO", "U", 7665, null, null, 2),
                new Fila("SPAIN", "ULL902.AL0103", "Z92", "ZETA AZUL", "U", 7665, null, null, 3),
                new Fila("SPAIN", "UBL903.AL0103", "Z93", "OTRO NOMBRE", "85", 7665, null, null, 3));

        mvc.perform(multipart("/colores/pedido").file(excel(pedido)).param("cliente", "AMI").param("que", "colores"))
                .andExpect(redirectedUrl("/colores?cliente=AMI"))
                .andExpect(flash().attribute("mensaje", "Del excel de pedido salen 3 colores. Añadidos (1): "
                        + "Z91. Con la descripción del pedido (1): Z92. Ya estaban: 1."));
        mvc.perform(get("/colores").param("cliente", "AMI"))
                .andExpect(content().string(containsString("value=\"ZETA ROJO\"")))
                .andExpect(content().string(containsString("value=\"ZETA AZUL\"")))
                .andExpect(content().string(containsString("value=\"PUESTO A MANO\"")))
                .andExpect(content().string(not(containsString("OTRO NOMBRE"))));

        mvc.perform(multipart("/colores/pedido").file(excel(pedido)).param("cliente", "AMI").param("que", "colores"))
                .andExpect(flash().attribute("mensaje",
                        "Los 3 colores del excel de pedido ya estaban en la tabla: no se ha añadido ninguno."));
    }

    @Test
    void delExcelRealDeApcSalenSusNueveCodigos() throws Exception {
        byte[] pedido;
        try (var entrada = getClass().getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            pedido = entrada.readAllBytes();
        }

        mvc.perform(multipart("/colores/pedido").file(excel(pedido)).param("cliente", "APC").param("que", "colores"))
                .andExpect(redirectedUrl("/colores?cliente=APC"))
                .andExpect(flash().attribute("mensaje",
                        "Los 9 colores del excel de pedido ya estaban en la tabla: no se ha añadido ninguno."));
    }

    @Test
    void unExcelQueNoEsElPedidoDelClienteOUnClienteSinLectorNoPasan() throws Exception {
        byte[] deAmi = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL904.AL0103", "Z94", "ZETA", "U", 7665, null, null, 1));

        mvc.perform(multipart("/colores/pedido").file(excel(deAmi)).param("cliente", "APC"))
                .andExpect(redirectedUrl("/colores?cliente=APC"))
                .andExpect(flash().attribute("mensajeError", containsString("No se pudo leer el excel de pedido")));
        mvc.perform(multipart("/colores/pedido").file(excel(deAmi)).param("cliente", "LGN"))
                .andExpect(flash().attribute("mensajeError", containsString("solo de AMI y APC")));
        mvc.perform(multipart("/colores/pedido").file(excel(new byte[0])).param("cliente", "AMI"))
                .andExpect(flash().attribute("mensajeError",
                        containsString("Elige una temporada guardada o sube el excel")));
    }

    @Test
    void elDesplegableDeTemporadasGuardadasVaConSuLapizEnLaZonaDelExcel() throws Exception {
        Long id = archivoTemporadas.guardar(null, "AMI", "COLORES1", "pedido.xlsx", PedidoAmiExcel.crear(
                "EAN H26", new Fila("SPAIN", "ULL905.AL0103", "Z95", "ZETA", "U", 7665, null, null, 1))).getId();

        String pagina = mvc.perform(get("/colores").param("cliente", "AMI"))
                .andReturn().getResponse().getContentAsString();
        String zona = pagina.substring(pagina.indexOf("action=\"/colores/pedido\""));
        assertTrue(zona.contains("name=\"temporadaGuardadaId\""), zona);
        assertTrue(zona.matches("(?s).*<option value=\"" + id + "\"\\s+data-fichero=\"pedido.xlsx\">COLORES1</option>.*"),
                zona);
        assertTrue(zona.contains("class=\"lapiz\" href=\"/temporadas\""), "el lápiz lleva a mantenerlas");
        assertTrue(pagina.contains("class=\"tarjeta cabecera-cliente\""), "título y desplegable en una línea");
    }

    @Test
    void conUnaTemporadaGuardadaNoHaceFaltaSubirElExcel() throws Exception {
        Long id = archivoTemporadas.guardar(null, "AMI", "COLORES2", "pedido.xlsx", PedidoAmiExcel.crear(
                "EAN H26", new Fila("SPAIN", "ULL906.AL0103", "Z96", "ZETA VERDE", "U", 7665, null, null, 1))).getId();

        mvc.perform(multipart("/colores/pedido").param("cliente", "AMI")
                        .param("temporadaGuardadaId", String.valueOf(id)).param("que", "colores"))
                .andExpect(redirectedUrl("/colores?cliente=AMI"))
                .andExpect(flash().attribute("mensaje",
                        "Del excel de pedido de la temporada COLORES2 salen 1 color. Añadidos (1): Z96."));
        mvc.perform(get("/colores").param("cliente", "AMI"))
                .andExpect(content().string(containsString("value=\"ZETA VERDE\"")));
    }

    @Test
    void unaTemporadaGuardadaDeOtroClienteNoSeUsa() throws Exception {
        Long id = archivoTemporadas.guardar(null, "APC", "COLORES3", "apc.xlsx", PedidoAmiExcel.crear(
                "EAN H26", new Fila("SPAIN", "ULL907.AL0103", "Z97", "ZETA", "U", 7665, null, null, 1))).getId();

        mvc.perform(multipart("/colores/pedido").param("cliente", "AMI")
                        .param("temporadaGuardadaId", String.valueOf(id)))
                .andExpect(redirectedUrl("/colores?cliente=AMI"))
                .andExpect(flash().attribute("mensajeError", containsString("no es de este cliente")));
    }

    @Test
    void delExcelSeSacanLasPielesLosColoresOLasDosCosas() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL910.AL0911", "Z81", "ZETA UNO", "U", 7665, null, null, 1),
                new Fila("SPAIN", "UBL911.AL0912", "Z82", "ZETA DOS", "85", 7665, null, null, 1));

        mvc.perform(multipart("/colores/pedido").file(excel(pedido)).param("cliente", "AMI")
                        .param("que", "pieles"))
                .andExpect(flash().attribute("mensaje",
                        "Del excel de pedido salen 2 pieles. Añadidas (2): AL0911, AL0912."));
        mvc.perform(get("/colores").param("cliente", "AMI"))
                .andExpect(content().string(containsString(">AL0911<")))
                .andExpect(content().string(not(containsString(">Z81<"))));

        // Las dos cosas a la vez: los colores eran nuevos, las pieles ya estaban.
        mvc.perform(multipart("/colores/pedido").file(excel(pedido)).param("cliente", "AMI")
                        .param("que", "todo"))
                .andExpect(flash().attribute("mensaje", "Del excel de pedido salen 2 colores. Añadidos (2): "
                        + "Z81, Z82. Las 2 pieles del excel de pedido ya estaban en la tabla: no se ha añadido ninguna."));
    }

    @Test
    void laZonaDelExcelTieneSiempreLosTresBotones() throws Exception {
        String pagina = mvc.perform(get("/colores").param("cliente", "APC"))
                .andReturn().getResponse().getContentAsString();

        for (String boton : java.util.List.of("value=\"todo\">Extraer Colores y Pieles<",
                "value=\"colores\" class=\"secundario\">Extraer Colores<",
                "value=\"pieles\" class=\"secundario\">Extraer Pieles<")) {
            assertTrue(pagina.contains(boton), "falta " + boton);
        }
    }

    @Test
    void delExcelRealDeApcSalenSusPielesPorLaParteDelanteDelGuion() throws Exception {
        byte[] pedido;
        try (var entrada = getClass().getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            pedido = entrada.readAllBytes();
        }

        mvc.perform(multipart("/colores/pedido").file(excel(pedido)).param("cliente", "APC").param("que", "pieles"))
                .andExpect(flash().attribute("mensaje", containsString("pieles")));
        mvc.perform(get("/colores").param("cliente", "APC"))
                .andExpect(content().string(containsString(">PXCBC<")))
                .andExpect(content().string(containsString(">PXCDS<")));
    }

    @Test
    void guardarUnaFilaNoRecargaYContestaEnJson() throws Exception {
        mvc.perform(post("/colores/pieles/fila").param("cliente", "LGN").param("codigo", "pl01")
                        .param("valor", "Vachette"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"ok\":true")));
        mvc.perform(get("/colores").param("cliente", "LGN"))
                .andExpect(content().string(containsString(">PL01<")))
                .andExpect(content().string(containsString("value=\"Vachette\"")));

        mvc.perform(post("/colores/pieles/fila").param("cliente", "LGN").param("codigo", " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"ok\":false")));
        mvc.perform(post("/colores/otra/fila").param("cliente", "LGN").param("codigo", "X"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lasFilasSeGuardanSinEnviarElFormularioYDebajoHayUnGuardarTodo() throws Exception {
        mvc.perform(post("/colores/colores/nuevo").param("cliente", "LGN").param("codigo", "X7").param("valor", ""));

        String pagina = mvc.perform(get("/colores").param("cliente", "LGN"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(pagina.contains("action=\"/colores/colores/todo\""), pagina);
        assertTrue(pagina.contains(">Guardar todo</button>"));
        assertTrue(pagina.matches("(?s).*<button type=\"button\" class=\"mini guardar-fila\".*"),
                "el guardar de la fila no envía el formulario");
    }

    @Test
    void guardarTodoGuardaLaTablaEntera() throws Exception {
        mvc.perform(post("/colores/colores/todo").param("cliente", "LGN")
                        .param("filas[0].codigo", "X5").param("filas[0].valor", "AZUL, MATE")
                        .param("filas[1].codigo", "X6").param("filas[1].valor", "VERDE"))
                .andExpect(redirectedUrl("/colores?cliente=LGN"))
                .andExpect(flash().attribute("mensaje", "Guardados 2 colores."));
        mvc.perform(get("/colores").param("cliente", "LGN"))
                .andExpect(content().string(containsString("value=\"AZUL, MATE\"")))
                .andExpect(content().string(containsString("value=\"VERDE\"")));
    }

    private static MockMultipartFile excel(byte[] contenido) {
        return new MockMultipartFile("pedido", "pedido.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", contenido);
    }

    @Test
    void seAnadeSeCorrigeYSeBorraUnColor() throws Exception {
        mvc.perform(post("/colores/colores/nuevo").param("cliente", "LGN").param("codigo", " x1 ")
                        .param("valor", "ROUGE"))
                .andExpect(redirectedUrl("/colores?cliente=LGN"))
                .andExpect(flash().attribute("mensaje", containsString("X1")));
        mvc.perform(get("/colores").param("cliente", "LGN"))
                .andExpect(content().string(containsString(">X1<")))
                .andExpect(content().string(containsString("value=\"ROUGE\"")));

        mvc.perform(post("/colores/colores/fila").param("cliente", "LGN").param("codigo", "X1")
                .param("valor", "ROJO"));
        mvc.perform(get("/colores").param("cliente", "LGN"))
                .andExpect(content().string(containsString("value=\"ROJO\"")))
                .andExpect(content().string(not(containsString("value=\"ROUGE\""))));

        mvc.perform(post("/colores/colores/fila/borrar").param("cliente", "LGN").param("codigo", "X1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"borrado\":true")));
        mvc.perform(get("/colores").param("cliente", "LGN"))
                .andExpect(content().string(not(containsString(">X1<"))));
    }

    @Test
    void unClienteQueNoEstaEnElCatalogoOUnColorSinCodigoNoSeGuardan() throws Exception {
        mvc.perform(post("/colores/colores/nuevo").param("cliente", "NADIE").param("codigo", "X2"))
                .andExpect(redirectedUrl("/colores"))
                .andExpect(flash().attribute("mensajeError", containsString("NADIE")));
        mvc.perform(post("/colores/colores/nuevo").param("cliente", "LGN").param("codigo", "  "))
                .andExpect(redirectedUrl("/colores?cliente=LGN"))
                .andExpect(flash().attribute("mensajeError", containsString("código")));
    }
}
