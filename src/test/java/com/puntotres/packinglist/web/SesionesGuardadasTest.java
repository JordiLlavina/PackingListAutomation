package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.persistence.ArchivoSesionesPacking;
import com.puntotres.packinglist.persistence.MemoriaReferencias;
import com.puntotres.packinglist.persistence.ResumenSesionPacking;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;

/**
 * Las sesiones guardadas del asistente, de punta a punta: el envío se guarda
 * solo al llegar a la revisión y con cada cambio, se carga en otra sesión del
 * navegador tal como estaba y se elimina; y «CON TEXTO», que es de pruebas,
 * no deja nada en la base de datos.
 *
 * Cada test usa su propia temporada para reconocer su sesión: la base es la de
 * toda la suite y otros tests también guardan sesiones de AMI.
 */
@SpringBootTest(properties = {
        "packing-list.taras.[60x40x40]=0.6",
        "packing-list.separadores-carton.por-caja=0"})
@AutoConfigureMockMvc
class SesionesGuardadasTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArchivoSesionesPacking archivo;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private MemoriaReferencias memoria;

    /** Un envío de AMI con dos cajas iguales de una referencia, sin pesos y con el color en minúsculas. */
    private static String envio(String referencia) {
        return """
                {"cliente": "AMI", "destinos": [
                  {"destino": "PARIS",
                   "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 2}],
                   "referencias": [{"referencia": "%s", "color": "noir",
                     "medidaCaja": "60x40x40", "pedido": "P1",
                     "cajas": [{"caja": 1, "unidades": 10}, {"caja": 2, "unidades": 10}]}]}
                ]}
                """.formatted(referencia);
    }

    private MockHttpSession importar(String modo, String temporada, String json) throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        mvc.perform(post("/importar").session(sesion).param("modo", modo)
                        .param("cliente", "AMI").param("json", json)
                        .param("temporada", temporada).param("numeroFactura", "FA-" + temporada)
                        .param("fechaFactura", "10/07/2026").param("fechaEnvio", "24/07/2026"))
                .andExpect(redirectedUrl("/revision"));
        return sesion;
    }

    private List<ResumenSesionPacking> sesionesDe(String temporada) {
        return archivo.todas().stream()
                .filter(sesion -> temporada.equals(sesion.temporada()))
                .toList();
    }

    private static EnvioEnCurso envioDe(MockHttpSession sesion) {
        return (EnvioEnCurso) sesion.getAttribute("scopedTarget.envioEnCurso");
    }

    private static List<CajaData> cajasDe(MockHttpSession sesion) {
        return envioDe(sesion).getImportado().getDestinos().get(0).getDestino().getCajas();
    }

    @Test
    void elEnvioSeGuardaSoloAlLlegarALaRevisionConLoJustoParaReconocerlo() throws Exception {
        importar("FORMULARIO", "SES-ALTA", envio("SES-REF-1"));

        List<ResumenSesionPacking> guardadas = sesionesDe("SES-ALTA");
        assertEquals(1, guardadas.size());
        assertEquals("AMI", guardadas.get(0).cliente());
        assertEquals("FA-SES-ALTA", guardadas.get(0).facturas());
        assertEquals("PARIS (2 cajas)", guardadas.get(0).destinos());
        assertNotNull(guardadas.get(0).creada());
    }

    @Test
    void loTecleadoEnLaRevisionSeGuardaYVuelveAlCargarLaSesionOtroDia() throws Exception {
        MockHttpSession primera = importar("FORMULARIO", "SES-CARGA", envio("SES-REF-2"));
        // Se despliega el tramo 1-2, se pesa la caja 1, se le pone factura a
        // la destinación y tara al palet.
        mvc.perform(post("/alternar-fila").session(primera)
                        .param("destino", "0").param("indice", "0"))
                .andExpect(redirectedUrl("/revision"));
        mvc.perform(post("/recalcular").session(primera)
                        .param("destinos[0].indiceDestino", "0")
                        .param("destinos[0].numeroFactura", "FA-NUEVA")
                        .param("palets[0].indiceDestino", "0")
                        .param("palets[0].numeroPalet", "1")
                        .param("palets[0].pesoKg", "14.5")
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].pesoBrutoKg", "8.6"))
                .andExpect(redirectedUrl("/revision"));
        List<ResumenSesionPacking> guardadas = sesionesDe("SES-CARGA");
        assertEquals(1, guardadas.size(), "los cambios reescriben la misma sesión, no abren otra");
        assertEquals("FA-NUEVA", guardadas.get(0).facturas());

        // Otro día: otra sesión del navegador, sin nada en curso.
        MockHttpSession otroDia = new MockHttpSession();
        mvc.perform(post("/packing-list/sesiones/" + guardadas.get(0).id() + "/cargar").session(otroDia))
                .andExpect(redirectedUrl("/revision"));

        EnvioEnCurso cargado = envioDe(otroDia);
        assertEquals(guardadas.get(0).id(), cargado.getSesionId());
        assertEquals("SES-CARGA", cargado.getCabecera().getTemporada());
        List<CajaData> cajas = cajasDe(otroDia);
        assertEquals(2, cajas.size());
        assertEquals(8.6, cajas.get(0).getPesoBrutoKg());
        assertEquals(8.0, cajas.get(0).getPesoNetoKg(), "bruto - tara 0.6");
        assertTrue(cajas.get(0).tienePesoMedido(), "el peso tecleado sigue siendo un peso pesado");
        assertEquals(1, cajas.get(0).getNumeroPalet());
        assertEquals("FA-NUEVA", cargado.getImportado().getDestinos().get(0).getDestino().getNumeroFactura());
        assertEquals(14.5, cargado.getImportado().getDestinos().get(0).getPalets().get(0).getTara());
        assertEquals(1, cargado.filasDesplegadas().size(), "la fila desplegada sigue desplegada");
        mvc.perform(get("/revision").session(otroDia))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SES-REF-2")));

        // Y seguir trabajando escribe en la misma sesión guardada.
        mvc.perform(post("/recalcular").session(otroDia)).andExpect(redirectedUrl("/revision"));
        assertEquals(1, sesionesDe("SES-CARGA").size());
    }

    @Test
    void eliminarBorraLaSesionYVuelveALaListaDeSuCliente() throws Exception {
        importar("FORMULARIO", "SES-BORRA", envio("SES-REF-3"));
        Long id = sesionesDe("SES-BORRA").get(0).id();

        mvc.perform(post("/packing-list/sesiones/" + id + "/eliminar"))
                .andExpect(redirectedUrl("/packing-list"));

        assertTrue(sesionesDe("SES-BORRA").isEmpty());
        // Cargar una que ya no está vuelve a la entrada y lo dice.
        mvc.perform(post("/packing-list/sesiones/" + id + "/cargar"))
                .andExpect(redirectedUrl("/packing-list"));
    }

    @Test
    void laEntradaListaLasSesionesConSusBotones() throws Exception {
        importar("FORMULARIO", "SES-LISTA", envio("SES-REF-4"));
        Long id = sesionesDe("SES-LISTA").get(0).id();

        mvc.perform(get("/packing-list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cargar Sesión")))
                .andExpect(content().string(containsString("SES-LISTA")))
                .andExpect(content().string(containsString("/packing-list/sesiones/" + id + "/cargar")))
                .andExpect(content().string(containsString("/packing-list/sesiones/" + id + "/eliminar")));
    }

    @Test
    void conTextoNoGuardaNiSesionNiPesos() throws Exception {
        catalogo.asegurarArticulo("AMI", "SES-REF-PRUEBA");
        MockHttpSession sesion = importar("JSON", "SES-PRUEBA", envio("SES-REF-PRUEBA"));
        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].indicesCaja", "1")
                        .param("cajas[0].pesoBrutoKg", "8.6"))
                .andExpect(redirectedUrl("/revision"));
        mvc.perform(post("/generar").session(sesion)).andExpect(redirectedUrl("/resultados"));

        assertTrue(sesionesDe("SES-PRUEBA").isEmpty(), "CON TEXTO no deja sesión guardada");
        assertNull(envioDe(sesion).getSesionId());
        assertFalse(memoria.pesoNetoUnidad("AMI", "SES-REF-PRUEBA").isPresent(),
                "CON TEXTO no guarda el peso del artículo en el catálogo");
    }

    @Test
    void elColorSaleSiempreEnMayusculas() throws Exception {
        MockHttpSession sesion = importar("FORMULARIO", "SES-COLOR", envio("SES-REF-5"));
        assertEquals("NOIR", cajasDe(sesion).get(0).getCodigoColor(), "llega en minúsculas y se sube");

        mvc.perform(post("/recalcular").session(sesion)
                        .param("cajas[0].indiceDestino", "0")
                        .param("cajas[0].indicesCaja", "0")
                        .param("cajas[0].indicesCaja", "1")
                        .param("cajas[0].codigoColor", "bleu marine"))
                .andExpect(redirectedUrl("/revision"));

        assertEquals("BLEU MARINE", cajasDe(sesion).get(0).getCodigoColor(), "y lo tecleado también");
        assertEquals("BLEU MARINE", cajasDe(sesion).get(1).getCodigoColor());
    }
}
