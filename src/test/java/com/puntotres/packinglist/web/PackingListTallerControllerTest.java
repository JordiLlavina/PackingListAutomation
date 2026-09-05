package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.puntotres.packinglist.persistence.MemoriaReferencias;
import com.puntotres.packinglist.testutil.PackingTallerExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;

/**
 * El asistente de la entrada por taller: subir los dos excels, ajustar y
 * generar, con la vuelta atrás desde la revisión sin resubir nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PackingListTallerControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MemoriaReferencias memoria;

    /** Una sesión propia por test: el estado del taller es de sesión. */
    private MockHttpSession sesion;

    /**
     * Una referencia distinta por test. La memoria de referencias es una
     * tabla compartida por toda la clase, y una que se aprendiera en un test
     * dejaría de estar pendiente en el siguiente: el test pasaría por el
     * motivo equivocado.
     */
    private String referencia;

    @BeforeEach
    void nuevaSesion(org.junit.jupiter.api.TestInfo info) {
        sesion = new MockHttpSession();
        referencia = "REF-" + info.getTestMethod().orElseThrow().getName().toUpperCase();
    }

    private byte[] tallerConUnaReferencia(Integer unidadesPorCaja) {
        return PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", referencia, "KAKI", 31)
                        .conUnidadesPorCaja(unidadesPorCaja));
    }

    private byte[] pedidoDeAmi() {
        return PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida(referencia, "KAKI", "U", "07001 CH", 20),
                PedidoAmiExcel.Fila.pedida(referencia, "KAKI", "U", "07002 JP", 10));
    }

    /**
     * El envío se comprueba por HTTP y con la misma sesión, como el resto de
     * tests de la web: el bean del envío en curso es de sesión, y uno
     * autoinyectado no sería el de la sesión de MockMvc.
     */
    private String revision() throws Exception {
        return mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void revisionVacia() throws Exception {
        mvc.perform(get("/revision").session(sesion))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/packing-list"));
    }

    private MockMultipartHttpServletRequestBuilder peticionDeDigerir(byte[] taller, byte[] pedido) {
        MockMultipartHttpServletRequestBuilder peticion =
                multipart("/packing-list/taller/digerir");
        peticion.session(sesion);
        if (taller != null) {
            peticion.file(new MockMultipartFile("excelTaller", "PACKING TALLER.xlsx",
                    null, taller));
        }
        if (pedido != null) {
            peticion.file(new MockMultipartFile("pedidoCliente", "EAN H26.xlsx", null, pedido));
        }
        return peticion;
    }

    private void digerirUnaEntrega(byte[] taller) throws Exception {
        mvc.perform(peticionDeDigerir(taller, pedidoDeAmi())
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(view().name("taller-ajuste"));
    }

    // --- Paso 1a ---

    @Test
    void laEntradaOfreceElModoTaller() throws Exception {
        mvc.perform(get("/packing-list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-modo=\"TALLER\"")));
    }

    @Test
    void digerirLlevaAlPasoDeAjuste() throws Exception {
        mvc.perform(peticionDeDigerir(tallerConUnaReferencia(8), pedidoDeAmi())
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("taller-ajuste"))
                .andExpect(model().attributeExists("digestion"))
                .andExpect(content().string(containsString(referencia)));
    }

    @Test
    void sinExcelDeTallerSeVuelveALaEntradaConElError() throws Exception {
        mvc.perform(peticionDeDigerir(null, pedidoDeAmi())
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"));
    }

    @Test
    void sinExcelDePedidoDeUnClienteQueLoUsaSeVuelveALaEntrada() throws Exception {
        mvc.perform(peticionDeDigerir(tallerConUnaReferencia(8), null)
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(view().name("entrada"));
    }

    @Test
    void sinLaHojaDeColisSeOfreceElegirlaAMano() throws Exception {
        mvc.perform(peticionDeDigerir(libroSinHojaDeColis(), pedidoDeAmi())
                        .param("modo", "TALLER").param("cliente", "AMI")
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "05/09/2026").param("fechaEnvio", "05/09/2026"))
                .andExpect(view().name("entrada"))
                .andExpect(model().attributeExists("hojasDelTaller"))
                .andExpect(content().string(containsString("FACTURA")));
    }

    // --- Paso 1b ---

    @Test
    void previsualizarDevuelveElResumenSinAvanzarDeEtapa() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/previsualizar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "8"))
                .andExpect(view().name("taller-ajuste"))
                .andExpect(model().attributeExists("resumen"));

        revisionVacia();
    }

    @Test
    void loTecleadoSeConservaAlPrevisualizar() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/previsualizar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x30")
                        .param("grupos[0].unidadesPorCaja", "4"))
                .andExpect(content().string(containsString("60x40x30")))
                .andExpect(content().string(containsString("value=\"4\"")));
    }

    @Test
    void unaReferenciaSinUnidadesPorCajaBloqueaLaGeneracion() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(null));

        mvc.perform(post("/packing-list/taller/generar").session(sesion))
                .andExpect(view().name("taller-ajuste"))
                .andExpect(content().string(containsString(referencia)));

        revisionVacia();
    }

    // --- Generar y volver ---

    @Test
    void generarLlevaALaRevisionConElEnvioListo() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "8"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/revision"));

        // El pedido reparte esa referencia entre China y Japón: la revisión
        // tiene que enseñar las dos destinaciones.
        String html = revision();
        assertTrue(html.contains("CHINA"));
        assertTrue(html.contains("JAPAN"));
    }

    @Test
    void elEnvioGeneradoAvisaDeQueElRepartoNoEsDelTaller() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                .param("grupos[0].medidaCaja", "60x40x40")
                .param("grupos[0].unidadesPorCaja", "8"));

        assertTrue(revision().contains("PACKING TALLER.xlsx"),
                "quien mira la revisión tiene que saber que el reparto lo ha hecho el programa");
    }

    @Test
    void generarRecuerdaLaReferenciaParaLaProximaEntrega() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(post("/packing-list/taller/generar").session(sesion)
                        .param("grupos[0].medidaCaja", "60x40x45")
                        .param("grupos[0].unidadesPorCaja", "9"))
                .andExpect(redirectedUrl("/revision"));

        MemoriaReferencias.DatosCaja recordado =
                memoria.buscar("AMI", referencia).orElseThrow();
        assertEquals("60x40x45", recordado.medidaCaja());
        assertEquals(9, recordado.unidadesPorCaja());
    }

    @Test
    void seVuelveAlAjusteSinResubirLosFicheros() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));

        mvc.perform(get("/packing-list/taller/ajuste").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("taller-ajuste"))
                .andExpect(model().attributeExists("digestion"));
    }

    @Test
    void volverAlAjusteConservaLoTecleado() throws Exception {
        digerirUnaEntrega(tallerConUnaReferencia(8));
        mvc.perform(post("/packing-list/taller/previsualizar").session(sesion)
                .param("grupos[0].medidaCaja", "60x40x30")
                .param("grupos[0].unidadesPorCaja", "4"));

        mvc.perform(get("/packing-list/taller/ajuste").session(sesion))
                .andExpect(content().string(containsString("60x40x30")));
    }

    @Test
    void sinEntregaEnCursoElAjusteDevuelveALaEntrada() throws Exception {
        mvc.perform(get("/packing-list/taller/ajuste").session(new MockHttpSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/packing-list"));
    }

    // --- Fixtures ---

    private static byte[] libroSinHojaDeColis() {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.createSheet("FACTURA");
            libro.createSheet("OTRA COSA");
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
