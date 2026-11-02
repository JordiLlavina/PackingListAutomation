package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.ResumenTemporada;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.persistence.TipoExcelTemporada;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;

/**
 * El archivo de temporadas y su efecto en la entrada del envío: guardar el
 * excel de pedido una vez y no volver a subirlo.
 *
 * Lo que de verdad hay que anclar no es la pantalla, sino que el envío acabe
 * trabajando con el excel guardado —y con el del cliente correcto—: un envío
 * generado con el pedido de otro cliente sale plausible y equivocado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TemporadasControllerTest {

    private static final String TIPO_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArchivoTemporadas archivo;

    @Autowired
    private CatalogoModelos catalogo;

    private static byte[] recurso(String ruta) throws Exception {
        try (var entrada = TemporadasControllerTest.class.getResourceAsStream(ruta)) {
            return entrada.readAllBytes();
        }
    }

    private static MockMultipartFile pedido(String nombre, String contenido) {
        return new MockMultipartFile("pedido", nombre, TIPO_XLSX, contenido.getBytes());
    }

    /** Da de alta una temporada por la pantalla y devuelve su id. */
    private Long alta(String cliente, String temporada, String fichero) throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(pedido(fichero, "pedido de " + temporada))
                        .param("cliente", cliente)
                        .param("temporada", temporada))
                .andExpect(redirectedUrl("/temporadas"))
                // Un alta rechazada también redirige aquí: sin esto, el helper
                // devolvería el id de la temporada que ya existía.
                .andExpect(flash().attribute("mensaje", containsString("Guardada")));
        return archivo.de(cliente).stream()
                .filter(guardada -> guardada.temporada().equals(temporada))
                .map(ResumenTemporada::id)
                .findFirst().orElseThrow();
    }

    @Test
    void guardarUnaTemporadaLaDejaEnElArchivoConSuExcel() throws Exception {
        Long id = alta("APC", "FALL26", "APC_PEDIDO_FALL26.xlsx");

        TemporadaGuardada guardada = archivo.conFichero(id).orElseThrow();
        assertEquals("APC", guardada.getCliente());
        assertEquals("APC_PEDIDO_FALL26.xlsx", guardada.getNombreFichero());
        assertArrayEquals("pedido de FALL26".getBytes(), guardada.getExcel());
    }

    @Test
    void laPantallaListaLasTemporadasGuardadas() throws Exception {
        alta("AMI", "H29", "EAN H29.xlsx");

        mvc.perform(get("/temporadas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("H29")))
                .andExpect(content().string(containsString("EAN H29.xlsx")));
    }

    @Test
    void editarElNombreSinSubirFicheroConservaElExcel() throws Exception {
        // Corregir una temporada mal tecleada no puede costar volver a buscar
        // el excel en el disco: es justo el trabajo que esto ahorra.
        // Clave propia: la base en memoria es la misma para toda la suite y
        // DocumentosCorteControllerTest ya guarda una "H27" de AMI.
        Long id = alta("AMI", "H27-EDITADA", "EAN H27.xlsx");

        mvc.perform(multipart("/temporadas")
                        .param("id", String.valueOf(id))
                        .param("cliente", "AMI")
                        .param("temporada", "H27-EDITADA CORREGIDA"))
                .andExpect(redirectedUrl("/temporadas"));

        TemporadaGuardada guardada = archivo.conFichero(id).orElseThrow();
        assertEquals("H27-EDITADA CORREGIDA", guardada.getTemporada());
        assertEquals("EAN H27.xlsx", guardada.getNombreFichero());
        assertArrayEquals("pedido de H27-EDITADA".getBytes(), guardada.getExcel());
    }

    @Test
    void subirOtroExcelSustituyeElGuardado() throws Exception {
        Long id = alta("AMI", "H28", "EAN H28.xlsx");

        mvc.perform(multipart("/temporadas")
                        .file(pedido("EAN H28 v2.xlsx", "pedido corregido"))
                        .param("id", String.valueOf(id))
                        .param("cliente", "AMI")
                        .param("temporada", "H28"));

        TemporadaGuardada guardada = archivo.conFichero(id).orElseThrow();
        assertEquals("EAN H28 v2.xlsx", guardada.getNombreFichero());
        assertArrayEquals("pedido corregido".getBytes(), guardada.getExcel());
    }

    @Test
    void borrarUnaTemporadaLaQuitaDelArchivo() throws Exception {
        Long id = alta("APC", "E27", "APC E27.xlsx");

        mvc.perform(post("/temporadas/borrar").param("id", String.valueOf(id)))
                .andExpect(redirectedUrl("/temporadas"));

        assertTrue(archivo.conFichero(id).isEmpty());
    }

    @Test
    void unaTemporadaRepetidaDelMismoClienteNoSeGuardaDosVeces() throws Exception {
        // Dos entradas con el mismo nombre son indistinguibles en el
        // desplegable, y elegir la que no toca no se ve hasta el documento.
        alta("APC", "E28-REPETIDA", "APC E28.xlsx");

        mvc.perform(multipart("/temporadas")
                        .file(pedido("otro.xlsx", "otro"))
                        .param("cliente", "APC")
                        .param("temporada", "e28-repetida"))
                .andExpect(redirectedUrl("/temporadas"));

        List<ResumenTemporada> deApc = archivo.de("APC").stream()
                .filter(temporada -> temporada.temporada().equalsIgnoreCase("E28-REPETIDA"))
                .toList();
        assertEquals(1, deApc.size(), "el nombre no distingue mayúsculas");
    }

    @Test
    void unaTemporadaSinExcelNoSeDaDeAlta() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .param("cliente", "AMI")
                        .param("temporada", "SIN FICHERO"))
                .andExpect(redirectedUrl("/temporadas"));

        assertTrue(archivo.de("AMI").stream()
                .noneMatch(temporada -> temporada.temporada().equals("SIN FICHERO")),
                "una temporada sin excel no ahorra nada y el desplegable no diría por qué");
    }

    @Test
    void unClienteSinExcelDePedidoGuardaElExcelDeProduccion() throws Exception {
        // Los genéricos no tienen pedido del cliente que leer: su temporada
        // guarda el excel de producción de Puntotres, de donde salen sus modelos.
        mvc.perform(multipart("/temporadas")
                        .file(pedido("prod.xlsx", "x"))
                        .param("cliente", "ACKERMANN")
                        .param("temporada", "H26"))
                .andExpect(redirectedUrl("/temporadas"));

        ResumenTemporada guardada = archivo.de("ACKERMANN").stream()
                .filter(temporada -> temporada.temporada().equals("H26")).findFirst().orElseThrow();
        assertEquals(TipoExcelTemporada.PRODUCCION, guardada.tipoExcel());
    }

    @Test
    void laEntradaOfreceElDesplegableDeTemporadasYElLapizParaMantenerlas() throws Exception {
        // El desplegable lo rellena el navegador con el mapa que va inline en la
        // página: si el atributo del modelo no llega, la constante sale nula y
        // el JS de la pantalla revienta entero, no solo esta parte.
        alta("AMI", "H31-LISTADA", "EAN H31.xlsx");

        mvc.perform(get("/packing-list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"temporadaGuardada\"")))
                .andExpect(content().string(containsString("href=\"/temporadas\"")))
                .andExpect(content().string(containsString("H31")));
    }

    @Test
    void elMenuLlevaAlArchivoDeTemporadas() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(content().string(containsString("href=\"/temporadas\"")))
                .andExpect(content().string(containsString("Guarda Temporadas y Ficheros")));
    }

    // --- Lo que hace por el envío ---

    @Test
    void elEnvioTrabajaConElExcelDeLaTemporadaElegidaSinVolverASubirlo() throws Exception {
        Long id = alta("AMI", "H30", "EAN PUNTOTRES H30.xlsx");
        MockHttpSession sesion = new MockHttpSession();

        importar(sesion, "AMI", id);

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        assertEquals("EAN PUNTOTRES H30.xlsx", envio.getNombreExcelPedidoCliente());
        assertArrayEquals("pedido de H30".getBytes(), envio.getExcelPedidoCliente());
    }

    @Test
    void unaTemporadaDeOtroClienteNoLeCuelaSuExcelAlEnvio() throws Exception {
        // El desplegable se rellena en el navegador: cambiar de cliente después
        // de elegir temporada dejaría enviado el id de la de otro, y el envío
        // saldría con el pedido equivocado sin que nada avisara.
        Long deApc = alta("APC", "E29", "APC E29.xlsx");
        MockHttpSession sesion = new MockHttpSession();

        importar(sesion, "AMI", deApc);

        EnvioEnCurso envio = (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
        assertNull(envio.getExcelPedidoCliente());
        assertTrue(envio.getImportado().getAvisos().stream()
                        .anyMatch(aviso -> aviso.contains("temporada guardada")),
                "y se dice, que quedarse sin pedido en silencio es peor");
    }

    private void importar(MockHttpSession sesion, String cliente, Long temporadaGuardadaId)
            throws Exception {
        String json;
        try (var entrada = getClass()
                .getResourceAsStream("/client-packinglist/packing_list_ami_test.json")) {
            json = new String(entrada.readAllBytes());
        }
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", cliente)
                        .param("json", json)
                        .param("temporada", "H26")
                        .param("temporadaGuardadaId", String.valueOf(temporadaGuardadaId))
                        .param("numeroFactura", "FA-26-1189")
                        .param("fechaFactura", "10/07/2026")
                        .param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));
    }

    // --- Lo que hace por el catálogo de modelos ---

    @Test
    void guardarUnaTemporadaConExcelRealDejaElAvisoEmergente() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(new MockMultipartFile("pedido", "EAN PUNTOTRES H26.xlsx", TIPO_XLSX,
                                recurso("/ejemplos/EAN PUNTOTRES H26.xlsx")))
                        .param("cliente", "AMI")
                        .param("temporada", "CAT-H26"))
                .andExpect(redirectedUrl("/temporadas"))
                .andExpect(flash().attributeExists("altaCatalogo"))
                .andExpect(flash().attribute("altaCliente", "AMI"));

        Long id = archivo.de("AMI").stream()
                .filter(temporada -> temporada.temporada().equals("CAT-H26"))
                .map(ResumenTemporada::id).findFirst().orElseThrow();
        assertTrue(!catalogo.codigosDeModeloEn("AMI", id).isEmpty(), "los modelos del pedido entran al catálogo");
    }

    @Test
    void unExcelQueNoSeDejaLeerGuardaLaTemporadaYAvisa() throws Exception {
        // La regla del proyecto: no bloquear por algo que se arregla después.
        // Se guarda, y el aviso dice que el catálogo no ha cogido nada.
        mvc.perform(multipart("/temporadas")
                        .file(pedido("raro.xlsx", "esto no es un excel"))
                        .param("cliente", "AMI")
                        .param("temporada", "CAT-ILEGIBLE"))
                .andExpect(redirectedUrl("/temporadas"))
                .andExpect(flash().attribute("mensaje", containsString("Guardada")))
                .andExpect(flash().attribute("aviso", containsString("no se han podido extraer")));

        assertTrue(archivo.de("AMI").stream().anyMatch(t -> t.temporada().equals("CAT-ILEGIBLE")));
    }

    @Test
    void borrarUnaTemporadaNoBorraNingunModelo() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(new MockMultipartFile("pedido", "EAN PUNTOTRES H26.xlsx", TIPO_XLSX,
                                recurso("/ejemplos/EAN PUNTOTRES H26.xlsx")))
                        .param("cliente", "AMI")
                        .param("temporada", "CAT-BORRABLE"))
                .andExpect(redirectedUrl("/temporadas"));
        Long id = archivo.de("AMI").stream()
                .filter(temporada -> temporada.temporada().equals("CAT-BORRABLE"))
                .map(ResumenTemporada::id).findFirst().orElseThrow();
        String modelo = catalogo.codigosDeModeloEn("AMI", id).iterator().next();

        mvc.perform(post("/temporadas/borrar").param("id", String.valueOf(id)))
                .andExpect(redirectedUrl("/temporadas"));

        assertTrue(catalogo.codigosDeModeloEn("AMI", id).isEmpty());
        assertTrue(catalogo.modelo("AMI", modelo).isPresent(), "el modelo sobrevive a su temporada");
    }

    @Test
    void cambiarElClienteDeUnaTemporadaCambiaSuTipoYSueltaSusModelos() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(new MockMultipartFile("pedido", "EAN PUNTOTRES H26.xlsx", TIPO_XLSX,
                                recurso("/ejemplos/EAN PUNTOTRES H26.xlsx")))
                        .param("cliente", "AMI")
                        .param("temporada", "CAT-MUDA"))
                .andExpect(redirectedUrl("/temporadas"));
        Long id = archivo.de("AMI").stream()
                .filter(temporada -> temporada.temporada().equals("CAT-MUDA"))
                .map(ResumenTemporada::id).findFirst().orElseThrow();
        assertTrue(!catalogo.codigosDeModeloEn("AMI", id).isEmpty());

        // Se edita sin fichero: el excel se queda, pero el cliente nuevo es un
        // genérico, así que el tipo pasa a producción y los vínculos con los
        // modelos de AMI no pueden quedarse.
        mvc.perform(multipart("/temporadas")
                        .param("id", String.valueOf(id))
                        .param("cliente", "KAMI")
                        .param("temporada", "CAT-MUDA"))
                .andExpect(redirectedUrl("/temporadas"));

        TemporadaGuardada mudada = archivo.conFichero(id).orElseThrow();
        assertEquals("KAMI", mudada.getCliente());
        assertEquals(TipoExcelTemporada.PRODUCCION, mudada.getTipoExcel());
        assertTrue(catalogo.codigosDeModeloEn("AMI", id).isEmpty(), "los modelos de AMI ya no son de esta temporada");
    }

    @Test
    void elDesplegableDeLaEntradaNoOfreceTemporadasDeProduccion() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(pedido("prod.xlsx", "x"))
                        .param("cliente", "KAMI")
                        .param("temporada", "CAT-PROD"))
                .andExpect(redirectedUrl("/temporadas"));
        Long id = archivo.de("KAMI").stream()
                .filter(temporada -> temporada.temporada().equals("CAT-PROD"))
                .map(ResumenTemporada::id).findFirst().orElseThrow();

        assertTrue(!archivo.paraElDesplegable().containsKey("KAMI"),
                "el packing list lee el excel como pedido: una de producción no sirve ahí");
        List<java.util.Map<String, String>> delCorte = archivo.paraElDesplegableDelCorte().get("KAMI");
        assertTrue(delCorte.stream().anyMatch(t -> t.get("id").equals(String.valueOf(id))
                && "PRODUCCION".equals(t.get("tipo"))));
        assertTrue(archivo.conPedidoParaElEnvio(id, "KAMI").isEmpty());
        assertTrue(archivo.paraElEnvio(id, "KAMI").isPresent(), "por el nombre sí sigue valiendo");
    }
}
