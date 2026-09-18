package com.puntotres.packinglist.shipping.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.puntotres.packinglist.shipping.carrier.*;
import com.puntotres.packinglist.shipping.model.*;

/**
 * La ficha del envío (envio.html) cambia de forma según el estado: los radios
 * de la tarifa, el botón que toca (Tarifar / Confirmar / Reintentar), la
 * sección de documentos... Un repaso de código no detecta una expresión de
 * Thymeleaf que solo se evalúa en un estado que ningún otro test visita
 * (QUOTED, CONFIRMED, ERROR, CREATED); este test lleva un envío real por la
 * máquina de estados entera pidiendo la página en cada paso, con un
 * transportista de mentira (como en ShipmentServiceTest) detrás de MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnvioEstadosRenderTest {

    @Autowired private MockMvc mvc;
    @Autowired private ContactRepository contactos;
    @Autowired private BillingAccountRepository cuentas;
    @Autowired private ShipmentReasonRepository motivos;

    @MockitoBean private CarrierClientRegistry registro;

    /** Transportista de mentira: devuelve lo programado o lanza, igual que en ShipmentServiceTest. */
    static class CarrierFalso implements CarrierClient {
        List<ProductOffer> ofertas = List.of(new ProductOffer("P", "EXPRESS WORLDWIDE", new BigDecimal("61.20"), "EUR", null));
        CreatedShipment creado = new CreatedShipment("1103733901", "https://dhl/1103733901", "PRG1",
                List.of(new CarrierDocument("label", "PDF", "%PDF-1.4".getBytes())));
        CarrierException fallo;

        public CarrierCode codigo() { return CarrierCode.DHL_EXPRESS; }
        public List<ProductOffer> getProducts(Shipment envio) { return ofertas; }
        public List<ProductOffer> quote(Shipment envio) { if (fallo != null) throw fallo; return ofertas; }
        public CreatedShipment createShipment(Shipment envio) { if (fallo != null) throw fallo; return creado; }
        public AddressCheck validateAddress(Contact c, TipoDireccion t) { return new AddressCheck(true, ""); }
        public List<TrackingEvent> track(String guia) { throw new UnsupportedOperationException(); }
    }

    private CarrierFalso carrier;
    private Long remitenteId, destinatarioId, cuentaId, motivoId;

    @BeforeEach
    void preparar() throws Exception {
        carrier = new CarrierFalso();
        when(registro.para(any())).thenReturn(carrier);

        // Dos contactos de la libreta (uno en España, uno en Marruecos: así el
        // envío es de exportación con aduana, el caso real) y una cuenta que
        // puede pagar transporte y aranceles. El perfil de test no trae ninguna
        // cuenta sembrada, así que hace falta darla de alta aquí.
        mvc.perform(post("/envios/contactos")
                .param("empresa", "PUNTOTRES SEDE").param("direccion1", "C/Industria 585")
                .param("ciudad", "Badalona").param("pais", "ES").param("telefono", "+34934425135"));
        mvc.perform(post("/envios/contactos")
                .param("empresa", "TALLER MARRUECOS").param("direccion1", "Zone franche")
                .param("ciudad", "Tanger").param("pais", "MA").param("telefono", "+212539393394"));
        mvc.perform(post("/envios/cuentas")
                .param("numero", "300112049").param("titular", "Puntotres")
                .param("roles", "PAYER").param("roles", "DUTIES"));

        remitenteId = idDe(contactos, "PUNTOTRES SEDE");
        destinatarioId = idDe(contactos, "TALLER MARRUECOS");
        cuentaId = cuentas.findByNumero("300112049").orElseThrow().getId();
        // "Muestras" está sembrado por la migración V2__envios.sql: no hace falta darlo de alta.
        motivoId = motivos.findByNombreIgnoreCase("Muestras").orElseThrow().getId();
    }

    private static Long idDe(ContactRepository repo, String empresa) {
        return repo.findByActivoTrueOrderByEmpresaAscPersonaAsc().stream()
                .filter(c -> empresa.equals(c.getEmpresa())).findFirst().orElseThrow().getId();
    }

    private String nuevoEnvio() throws Exception {
        return mvc.perform(post("/envios/nuevo")).andReturn().getResponse().getRedirectedUrl();
    }

    /**
     * Los mismos campos que exige problemasParaTarifar. Hacen falta tanto en
     * "guardar" como en "tarifar": el controlador aplica el formulario con
     * ShipmentService.editar antes de las dos acciones, así que un campo que
     * no viaje en la petición se borra, igual que en un navegador que
     * reenviara solo parte del formulario.
     */
    private MockHttpServletRequestBuilder conDatosCompletos(MockHttpServletRequestBuilder peticion) {
        return peticion
                .param("remitenteId", String.valueOf(remitenteId))
                .param("destinatarioId", String.valueOf(destinatarioId))
                .param("motivoId", String.valueOf(motivoId))
                .param("cuentaTransporteId", String.valueOf(cuentaId))
                .param("responsable", "Jordi")
                .param("fechaRecogida", LocalDate.now().plusDays(1).toString())
                .param("horaCierreRecogida", "18:00")
                .param("pedirRecogida", "true")
                .param("bultos[0].peso", "2.5").param("bultos[0].largo", "40")
                .param("bultos[0].ancho", "30").param("bultos[0].alto", "20")
                .param("lineas[0].descripcion", "Bolsos de piel").param("lineas[0].codigoHs", "42022100")
                .param("lineas[0].cantidad", "3").param("lineas[0].valorUnitario", "40")
                .param("lineas[0].pesoNeto", "1.0").param("lineas[0].pesoBruto", "1.5")
                .param("lineas[0].paisOrigen", "MA");
    }

    private void guardarYTarifar(String destino) throws Exception {
        mvc.perform(conDatosCompletos(post(destino).param("accion", "guardar")));
        mvc.perform(conDatosCompletos(post(destino).param("accion", "tarifar")));
    }

    @Test
    void elEnvioLlegaAQuotedYLuegoACreatedConSusDocumentos() throws Exception {
        String destino = nuevoEnvio();
        guardarYTarifar(destino);

        mvc.perform(get(destino)).andExpect(status().isOk())
                .andExpect(content().string(containsString("QUOTED")))
                .andExpect(content().string(containsString("EXPRESS WORLDWIDE")))
                .andExpect(content().string(containsString("Confirmar y crear")));

        mvc.perform(post(destino).param("accion", "confirmar").param("responsable", "Jordi").param("productoCodigo", "P"));

        mvc.perform(get(destino)).andExpect(status().isOk())
                .andExpect(content().string(containsString("CREATED")))
                .andExpect(content().string(containsString("1103733901")))
                .andExpect(content().string(containsString(destino + "/documentos/label.pdf")));

        mvc.perform(get(destino + "/documentos/label.pdf")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mvc.perform(get("/envios")).andExpect(status().isOk())
                .andExpect(content().string(containsString("1103733901")));
    }

    @Test
    void siElTransportistaFallaAlCrearQuedaEnErrorYReintentarLoCrea() throws Exception {
        String destino = nuevoEnvio();
        guardarYTarifar(destino);

        carrier.fallo = new CarrierException("DHL caído");
        mvc.perform(post(destino).param("accion", "confirmar").param("responsable", "Jordi").param("productoCodigo", "P"));

        mvc.perform(get(destino)).andExpect(status().isOk())
                .andExpect(content().string(containsString("ERROR")))
                .andExpect(content().string(containsString("DHL caído")))
                .andExpect(content().string(containsString("Reintentar")));

        carrier.fallo = null;
        mvc.perform(post(destino).param("accion", "reintentar").param("responsable", "Jordi"));

        mvc.perform(get(destino)).andExpect(status().isOk())
                .andExpect(content().string(containsString("CREATED")));
    }

    @Test
    void modificarDesdeQuotedVuelveABorradorYQuitaElBotonDeConfirmar() throws Exception {
        String destino = nuevoEnvio();
        guardarYTarifar(destino);

        mvc.perform(post(destino).param("accion", "modificar").param("responsable", "Jordi"));

        mvc.perform(get(destino)).andExpect(status().isOk())
                .andExpect(content().string(containsString("DRAFT")))
                .andExpect(content().string(not(containsString("Confirmar y crear"))));
    }
}
