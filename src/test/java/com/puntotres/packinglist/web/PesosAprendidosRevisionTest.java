package com.puntotres.packinglist.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.OrigenPeso;
import com.puntotres.packinglist.persistence.MemoriaReferencias;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;

/**
 * Los pesos conocidos de los artículos, de punta a punta por la revisión: se
 * cargan del catálogo al abrirla (en gris) y se aprenden al pulsar «Generar
 * Excels Cliente», solo de lo que se ha pesado de verdad y solo para los
 * artículos que ya están en el catálogo.
 *
 * Las taras van fijadas, como en PackingListControllerTest, para que la
 * aritmética se pueda leer: la tabla de taras es un dato del almacén. El
 * 60x40x51 se deja con la tara de relleno (un cartón sin pesar). Cada test usa
 * referencias propias: la base es la del contexto, compartida por sus tests.
 */
@SpringBootTest(properties = {
        "packing-list.taras.[60x40x40]=0.6",
        "packing-list.taras.[60x40x51]=0.01",
        "packing-list.separadores-carton.por-caja=0"})
@AutoConfigureMockMvc
class PesosAprendidosRevisionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private MemoriaReferencias memoria;

    /** Un envío de AMI con una caja de la referencia, con su peso bruto o sin él. */
    private static String envio(String referencia, String medida, int unidades, Double pesoBruto) {
        String peso = pesoBruto == null ? "" : ", \"pesoBruto\": " + pesoBruto;
        return """
                {"cliente": "AMI", "destinos": [
                  {"destino": "PARIS",
                   "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 1}],
                   "referencias": [{"referencia": "%s", "color": "NOIR",
                     "medidaCaja": "%s", "pedido": "P1",
                     "cajas": [{"caja": 1, "unidades": %d%s}]}]}
                ]}
                """.formatted(referencia, medida, unidades, peso);
    }

    private MockHttpSession importar(String json) throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        // FORMULARIO y no el JSON por defecto: «CON TEXTO» (modo JSON) es la
        // vía de pruebas y no guarda pesos; FORMULARIO llega por el mismo
        // campo json pero es un envío de verdad.
        mvc.perform(post("/importar").session(sesion).param("modo", "FORMULARIO")
                        .param("cliente", "AMI").param("json", json)
                        .param("temporada", "H26").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "10/07/2026").param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));
        return sesion;
    }

    private void generar(MockHttpSession sesion) throws Exception {
        mvc.perform(post("/generar").session(sesion)).andExpect(redirectedUrl("/resultados"));
    }

    private static EnvioEnCurso envioDe(MockHttpSession sesion) {
        return (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
    }

    private static CajaData primeraCaja(MockHttpSession sesion) {
        return envioDe(sesion).getImportado().getDestinos().get(0).getDestino().getCajas().get(0);
    }

    private static List<String> avisos(MockHttpSession sesion) {
        return envioDe(sesion).getAvisosGeneracion();
    }

    private void articuloConPeso(String referencia, Double pesoUnidad) {
        catalogo.asegurarArticulo("AMI", referencia);
        if (pesoUnidad != null) {
            memoria.recordarPesoUnidad("AMI", referencia, pesoUnidad);
        }
    }

    // --- Al abrir la revisión ---

    @Test
    void elPesoDelCatalogoSeCargaAlAbrirLaRevisionYSeVeEnGris() throws Exception {
        articuloConPeso("PESO-GRIS", 0.5);

        MockHttpSession sesion = importar(envio("PESO-GRIS", "60x40x40", 10, null));

        CajaData caja = primeraCaja(sesion);
        assertEquals(5.0, caja.getPesoNetoKg());     // 10 * 0.5
        assertEquals(5.6, caja.getPesoBrutoKg());    // + tara 0.6
        assertEquals(OrigenPeso.CATALOGO, caja.getOrigenPeso());
        String html = mvc.perform(get("/revision").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertEquals(2, ocurrencias(html, "c-peso peso-catalogo"), "los dos pesos de la caja, en gris");
    }

    @Test
    void unPesoPesadoEnElEnvioNoSePintaEnGris() throws Exception {
        articuloConPeso("PESO-NEGRO", 0.5);

        MockHttpSession sesion = importar(envio("PESO-NEGRO", "60x40x40", 10, 10.6));

        assertEquals(10.0, primeraCaja(sesion).getPesoNetoKg(), "manda el pesado, no el catálogo");
        String html = mvc.perform(get("/revision").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, ocurrencias(html, "c-peso peso-catalogo"));
    }

    @Test
    void teclearOtroPesoEnUnaCajaGrisLaDaPorPesada() throws Exception {
        articuloConPeso("PESO-CORREGIDO", 0.5);
        MockHttpSession sesion = importar(envio("PESO-CORREGIDO", "60x40x40", 10, null));

        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].pesoBrutoKg", "8.6")
                        .param("cajas[0].pesoNetoKg", "5.0"))   // el neto gris, sin tocar
                .andExpect(redirectedUrl("/revision"));

        CajaData caja = primeraCaja(sesion);
        assertEquals(OrigenPeso.MEDIDO, caja.getOrigenPeso());
        assertEquals(8.6, caja.getPesoBrutoKg());
        assertEquals(8.0, caja.getPesoNetoKg(), "el neto viejo no se queda al lado del bruto nuevo");
    }

    @Test
    void devolverElPesoGrisSinTocarloNoLoDaPorPesado() throws Exception {
        // El formulario manda siempre los valores que enseña.
        articuloConPeso("PESO-SIN-TOCAR", 0.5);
        MockHttpSession sesion = importar(envio("PESO-SIN-TOCAR", "60x40x40", 10, null));

        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].pesoBrutoKg", "5.6")
                        .param("cajas[0].pesoNetoKg", "5.0"))
                .andExpect(redirectedUrl("/revision"));

        assertEquals(OrigenPeso.CATALOGO, primeraCaja(sesion).getOrigenPeso());
    }

    // --- Al generar ---

    @Test
    void generarGuardaElPesoPorUnidadDeLoQueSeHaPesado() throws Exception {
        articuloConPeso("APRENDE-1", null);
        MockHttpSession sesion = importar(envio("APRENDE-1", "60x40x40", 10, 12.6));

        generar(sesion);

        assertEquals(1.2, memoria.pesoNetoUnidad("AMI", "APRENDE-1").orElseThrow());  // (12.6-0.6)/10
    }

    @Test
    void unPesoDelCatalogoNoSeVuelveAGuardarComoPesado() throws Exception {
        articuloConPeso("SOLO-CATALOGO", 0.5);
        MockHttpSession sesion = importar(envio("SOLO-CATALOGO", "60x40x40", 7, null));

        generar(sesion);

        assertEquals(0.5, memoria.pesoNetoUnidad("AMI", "SOLO-CATALOGO").orElseThrow());
        assertTrue(avisos(sesion).stream().noneMatch(a -> a.contains("SOLO-CATALOGO")), avisos(sesion).toString());
    }

    @Test
    void sinElArticuloEnElCatalogoNoSeGuardaYSeDice() throws Exception {
        MockHttpSession sesion = importar(envio("FUERA-DEL-CATALOGO", "60x40x40", 10, 12.6));

        generar(sesion);

        assertTrue(catalogo.articulo("AMI", "FUERA-DEL-CATALOGO").isEmpty(), "no se da de alta");
        assertTrue(avisos(sesion).stream()
                .anyMatch(a -> a.contains("FUERA-DEL-CATALOGO") && a.contains("catálogo")), avisos(sesion).toString());
    }

    @Test
    void conUnCartonSinPesarNoSeGuardaElPesoYSeDice() throws Exception {
        articuloConPeso("CARTON-SIN-PESAR", null);
        MockHttpSession sesion = importar(envio("CARTON-SIN-PESAR", "60x40x51", 10, 12.6));

        generar(sesion);

        assertTrue(memoria.pesoNetoUnidad("AMI", "CARTON-SIN-PESAR").isEmpty());
        assertTrue(avisos(sesion).stream()
                .anyMatch(a -> a.contains("CARTON-SIN-PESAR") && a.contains("60x40x51")), avisos(sesion).toString());
    }

    @Test
    void unPesoMuyDistintoDelGuardadoSeGuardaYSeAvisa() throws Exception {
        articuloConPeso("SE-DESVIA", 1.0);
        MockHttpSession sesion = importar(envio("SE-DESVIA", "60x40x40", 10, 15.6));   // 1.5 por unidad

        generar(sesion);

        assertEquals(1.5, memoria.pesoNetoUnidad("AMI", "SE-DESVIA").orElseThrow());
        assertTrue(avisos(sesion).stream()
                .anyMatch(a -> a.contains("SE-DESVIA") && a.contains("1,5") && a.contains("1")), avisos(sesion).toString());
    }

    @Test
    void unPesoParecidoAlGuardadoNoAvisa() throws Exception {
        articuloConPeso("SE-PARECE", 1.0);
        MockHttpSession sesion = importar(envio("SE-PARECE", "60x40x40", 10, 11.1));   // 1.05 por unidad

        generar(sesion);

        assertEquals(1.05, memoria.pesoNetoUnidad("AMI", "SE-PARECE").orElseThrow());
        assertFalse(avisos(sesion).stream().anyMatch(a -> a.contains("SE-PARECE")), avisos(sesion).toString());
    }

    private static int ocurrencias(String texto, String buscado) {
        int cuenta = 0;
        for (int i = texto.indexOf(buscado); i >= 0; i = texto.indexOf(buscado, i + 1)) {
            cuenta++;
        }
        return cuenta;
    }
}
