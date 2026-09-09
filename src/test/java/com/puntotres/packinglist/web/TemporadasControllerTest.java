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

    private static MockMultipartFile pedido(String nombre, String contenido) {
        return new MockMultipartFile("pedido", nombre, TIPO_XLSX, contenido.getBytes());
    }

    /** Da de alta una temporada por la pantalla y devuelve su id. */
    private Long alta(String cliente, String temporada, String fichero) throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(pedido(fichero, "pedido de " + temporada))
                        .param("cliente", cliente)
                        .param("temporada", temporada))
                .andExpect(redirectedUrl("/temporadas"));
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
        Long id = alta("AMI", "H27", "EAN H27.xlsx");

        mvc.perform(multipart("/temporadas")
                        .param("id", String.valueOf(id))
                        .param("cliente", "AMI")
                        .param("temporada", "H27 CORREGIDA"))
                .andExpect(redirectedUrl("/temporadas"));

        TemporadaGuardada guardada = archivo.conFichero(id).orElseThrow();
        assertEquals("H27 CORREGIDA", guardada.getTemporada());
        assertEquals("EAN H27.xlsx", guardada.getNombreFichero());
        assertArrayEquals("pedido de H27".getBytes(), guardada.getExcel());
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
        alta("APC", "E28", "APC E28.xlsx");

        mvc.perform(multipart("/temporadas")
                        .file(pedido("otro.xlsx", "otro"))
                        .param("cliente", "APC")
                        .param("temporada", "e28"))
                .andExpect(redirectedUrl("/temporadas"));

        List<ResumenTemporada> deApc = archivo.de("APC").stream()
                .filter(temporada -> temporada.temporada().equalsIgnoreCase("E28"))
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
    void unClienteQueNoTrabajaConExcelDePedidoNoTieneTemporadas() throws Exception {
        mvc.perform(multipart("/temporadas")
                        .file(pedido("pedido.xlsx", "x"))
                        .param("cliente", "ACKERMANN")
                        .param("temporada", "H26"))
                .andExpect(redirectedUrl("/temporadas"));

        assertTrue(archivo.de("ACKERMANN").isEmpty());
    }

    @Test
    void laEntradaOfreceElDesplegableDeTemporadasYElLapizParaMantenerlas() throws Exception {
        // El desplegable lo rellena el navegador con el mapa que va inline en la
        // página: si el atributo del modelo no llega, la constante sale nula y
        // el JS de la pantalla revienta entero, no solo esta parte.
        alta("AMI", "H31", "EAN H31.xlsx");

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
}
