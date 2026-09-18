package com.puntotres.packinglist.shipping.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.shipping.carrier.*;
import com.puntotres.packinglist.shipping.config.EnviosProperties;
import com.puntotres.packinglist.shipping.model.*;

class ShipmentServiceTest {

    /** Transportista de mentira: devuelve lo programado o lanza. */
    static class CarrierFalso implements CarrierClient {
        List<ProductOffer> ofertas = List.of(new ProductOffer("P", "EXPRESS WORLDWIDE", new BigDecimal("61.20"), "EUR", null));
        CreatedShipment creado = new CreatedShipment("1103733901", "https://dhl/1103733901", "PRG240918",
                List.of(new CarrierDocument("label", "PDF", new byte[] {1}), new CarrierDocument("invoice", "PDF", new byte[] {2})));
        CarrierException fallo;
        int llamadasCrear;
        String ultimoRequestId;

        public CarrierCode codigo() { return CarrierCode.DHL_EXPRESS; }
        public List<ProductOffer> getProducts(Shipment envio) { return ofertas; }
        public List<ProductOffer> quote(Shipment envio) { if (fallo != null) throw fallo; return ofertas; }
        public CreatedShipment createShipment(Shipment envio) {
            llamadasCrear++;
            ultimoRequestId = envio.getRequestId();
            if (fallo != null) throw fallo;
            return creado;
        }
        public AddressCheck validateAddress(Contact c, TipoDireccion t) { return new AddressCheck(true, ""); }
        public List<TrackingEvent> track(String guia) { throw new UnsupportedOperationException(); }
    }

    private ShipmentRepository envios;
    private ContactRepository contactos;
    private CarrierFalso carrier;
    private Notifier notifier;
    private ShipmentService servicio;
    private Shipment envio;
    private Contact origen, destino;

    @BeforeEach
    void preparar() {
        envios = mock(ShipmentRepository.class);
        contactos = mock(ContactRepository.class);
        BillingAccountRepository cuentas = mock(BillingAccountRepository.class);
        ShipmentReasonRepository motivos = mock(ShipmentReasonRepository.class);
        ArticleRepository articulos = mock(ArticleRepository.class);
        ShipmentDocumentRepository documentos = mock(ShipmentDocumentRepository.class);
        carrier = new CarrierFalso();
        notifier = mock(Notifier.class);
        EnviosProperties props = new EnviosProperties();
        props.setPaisesUnionAduanera(List.of("ES", "FR"));
        servicio = new ShipmentService(envios, contactos, cuentas, motivos, articulos, documentos,
                new CarrierClientRegistry(List.of(carrier)), notifier,
                new DirectionResolver(props), new CustomsChecker(props), props);

        origen = new Contact("PUNTOTRES", "C/Industria 585", "Badalona", "ES");
        origen.setTelefono("+34934425135");
        destino = new Contact("TALLER", "Zone franche", "Tanger", "MA");
        destino.setTelefono("+212539393394");
        BillingAccount cuenta = new BillingAccount("300112049", "Puntotres", true, java.util.EnumSet.allOf(BillingRole.class));
        ShipmentReason motivo = new ShipmentReason("Muestras", ExportReasonType.SAMPLE);

        envio = new Shipment("Jordi");
        when(envios.findById(1L)).thenReturn(Optional.of(envio));
        when(envios.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contactos.findById(10L)).thenReturn(Optional.of(origen));
        when(contactos.findById(20L)).thenReturn(Optional.of(destino));
        when(cuentas.findById(30L)).thenReturn(Optional.of(cuenta));
        when(motivos.findById(40L)).thenReturn(Optional.of(motivo));
    }

    private DatosEnvio datosCompletos() {
        return new DatosEnvio(10L, 20L, LocalDate.now().plusDays(1), Incoterm.DAP, 30L, null, 40L,
                "AMI", "H26", "MUESTRAS-1", null, "Jordi", true, "18:00", "Recepción",
                List.of(new DatosEnvio.DatosBulto(2.5, 40.0, 30.0, 20.0, null)),
                List.of(new DatosEnvio.DatosLinea("Bolsos de piel", "42022100", 3, new BigDecimal("40"), 1.0, 1.5, "MA", null)));
    }

    @Test
    void editarRellenaElEnvioYDeduceSentidoYAduana() {
        Shipment editado = servicio.editar(1L, datosCompletos());

        assertSame(origen, editado.getRemitente());
        assertEquals(Direction.EXPORT, editado.getSentido());
        assertTrue(editado.isAduanero());
        assertEquals(1, editado.getBultos().size());
        assertEquals(1, editado.getLineasAduana().size());
        assertEquals(ShipmentStatus.DRAFT, editado.getEstado());
    }

    @Test
    void editarUnEnvioTarifadoLoDevuelveABorradorYBorraLasOfertas() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);
        assertEquals(ShipmentStatus.QUOTED, envio.getEstado());

        servicio.editar(1L, datosCompletos());

        assertEquals(ShipmentStatus.DRAFT, envio.getEstado());
        assertTrue(envio.getOfertas().isEmpty());
    }

    @Test
    void tarifarConDatosIncompletosDevuelveProblemasYNoLlamaAlTransportista() {
        ResultadoTarifa resultado = servicio.tarifar(1L);

        assertFalse(resultado.ok());
        assertFalse(resultado.problemas().isEmpty());
        assertEquals(ShipmentStatus.DRAFT, envio.getEstado());
    }

    @Test
    void tarifarGuardaLasOfertasYPasaAQuoted() {
        servicio.editar(1L, datosCompletos());

        ResultadoTarifa resultado = servicio.tarifar(1L);

        assertTrue(resultado.ok());
        assertEquals("P", envio.getOfertas().get(0).getProductoCodigo());
        assertEquals(ShipmentStatus.QUOTED, envio.getEstado());
    }

    @Test
    void siElTransportistaFallaAlTarifarElEnvioSigueEnBorradorConElError() {
        servicio.editar(1L, datosCompletos());
        carrier.fallo = new CarrierException("DHL: código postal no válido");

        ResultadoTarifa resultado = servicio.tarifar(1L);

        assertFalse(resultado.ok());
        assertEquals("DHL: código postal no válido", resultado.errorTransportista());
        assertEquals(ShipmentStatus.DRAFT, envio.getEstado());
        assertEquals("DHL: código postal no válido", envio.getUltimoError());
    }

    @Test
    void confirmarFijaProductoCosteYRequestIdYPasaAConfirmed() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);

        servicio.confirmar(1L, "P");

        assertEquals(ShipmentStatus.CONFIRMED, envio.getEstado());
        assertEquals("P", envio.getProductoCodigo());
        assertEquals(new BigDecimal("61.20"), envio.getCosteEstimado());
        assertNotNull(envio.getRequestId());
        assertEquals(36, envio.getRequestId().length());
    }

    @Test
    void confirmarUnProductoQueNoEstaEnLasOfertasEsIlegal() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);

        assertThrows(TransicionIlegalException.class, () -> servicio.confirmar(1L, "N"));
    }

    @Test
    void confirmarDesdeBorradorEsIlegal() {
        assertThrows(TransicionIlegalException.class, () -> servicio.confirmar(1L, "P"));
    }

    @Test
    void crearGuardaGuiaYDocumentosPasaACreatedYAvisa() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);
        servicio.confirmar(1L, "P");

        servicio.crear(1L);

        assertEquals(ShipmentStatus.CREATED, envio.getEstado());
        assertEquals("1103733901", envio.getNumeroGuia());
        assertEquals("PRG240918", envio.getNumeroConfirmacionRecogida());
        assertEquals(2, envio.getDocumentos().size());
        assertNull(envio.getUltimoError());
        verify(notifier).envioCreado(envio);
    }

    @Test
    void unAvisoQueFallaNoDeshaceElEstadoDelEnvioNiPropagaLaExcepcion() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);
        servicio.confirmar(1L, "P");
        doThrow(new RuntimeException("smtp caído")).when(notifier).envioCreado(any());

        assertDoesNotThrow(() -> servicio.crear(1L));

        assertEquals(ShipmentStatus.CREATED, envio.getEstado());
        assertEquals("1103733901", envio.getNumeroGuia());
    }

    @Test
    void siElTransportistaFallaAlCrearPasaAErrorYReintentarReutilizaElMismoRequestId() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);
        servicio.confirmar(1L, "P");
        String requestId = envio.getRequestId();
        carrier.fallo = new CarrierException("DHL no ha respondido");

        servicio.crear(1L);
        assertEquals(ShipmentStatus.ERROR, envio.getEstado());
        assertEquals("DHL no ha respondido", envio.getUltimoError());
        verify(notifier).envioFallido(envio, "DHL no ha respondido");

        carrier.fallo = null;
        servicio.crear(1L);
        assertEquals(ShipmentStatus.CREATED, envio.getEstado());
        assertEquals(requestId, carrier.ultimoRequestId);
        assertEquals(2, carrier.llamadasCrear);
    }

    @Test
    void crearDesdeQuotedOCreatedEsIlegal() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);
        assertThrows(TransicionIlegalException.class, () -> servicio.crear(1L));

        servicio.confirmar(1L, "P");
        servicio.crear(1L);
        assertThrows(TransicionIlegalException.class, () -> servicio.crear(1L), "un envío creado no se crea dos veces");
        assertEquals(1, carrier.llamadasCrear);
    }

    @Test
    void cancelarValeDesdeCualquierEstadoMenosCreatedYCancelled() {
        servicio.cancelar(1L);
        assertEquals(ShipmentStatus.CANCELLED, envio.getEstado());
        assertThrows(TransicionIlegalException.class, () -> servicio.cancelar(1L));
        assertThrows(TransicionIlegalException.class, () -> servicio.editar(1L, datosCompletos()));
    }

    @Test
    void unEnvioQueNoExisteLanzaSuPropiaExcepcion() {
        assertThrows(EnvioNoEncontradoException.class, () -> servicio.cargar(99L));
    }

    @Test
    void losAvisosSenalanNacionalYPesosQueNoCuadran() {
        when(contactos.findById(20L)).thenReturn(Optional.of(origen)); // destino = origen: nacional
        servicio.editar(1L, datosCompletos());
        List<String> avisos = servicio.avisos(envio);
        assertTrue(avisos.stream().anyMatch(a -> a.contains("Nacional")), avisos.toString());

        when(contactos.findById(20L)).thenReturn(Optional.of(destino));
        DatosEnvio pesado = new DatosEnvio(10L, 20L, LocalDate.now().plusDays(1), Incoterm.DAP, 30L, null, 40L,
                null, null, null, null, "Jordi", true, "18:00", null,
                List.of(new DatosEnvio.DatosBulto(1.0, 40.0, 30.0, 20.0, null)),
                List.of(new DatosEnvio.DatosLinea("Bolsos", "42022100", 3, new BigDecimal("40"), 1.0, 5.0, "MA", null)));
        servicio.editar(1L, pesado);
        assertTrue(servicio.avisos(envio).stream().anyMatch(a -> a.contains("bruto")), "Σ bruto líneas 5 > Σ bultos 1");
    }

    @Test
    void editarConHoraCierreYLugarNulosConservaLoDelBorradorYConCadenaVaciaLosBorra() {
        Shipment borrador = servicio.crearBorrador("Jordi");
        when(envios.findById(1L)).thenReturn(Optional.of(borrador));
        assertEquals("18:00", borrador.getHoraCierreRecogida());
        assertEquals("Recepción", borrador.getLugarRecogida());

        // null en la entrada significa "no tocar": lo que puso crearBorrador sobrevive.
        DatosEnvio sinRecogida = new DatosEnvio(10L, 20L, LocalDate.now().plusDays(1), Incoterm.DAP, 30L, null, 40L,
                "AMI", "H26", "MUESTRAS-1", null, "Jordi", null, null, null, List.of(), List.of());
        Shipment editado = servicio.editar(1L, sinRecogida);
        assertEquals("18:00", editado.getHoraCierreRecogida());
        assertEquals("Recepción", editado.getLugarRecogida());

        // una cadena vacía sí es una orden explícita de borrar, vía limpiar().
        DatosEnvio vaciando = new DatosEnvio(10L, 20L, LocalDate.now().plusDays(1), Incoterm.DAP, 30L, null, 40L,
                "AMI", "H26", "MUESTRAS-1", null, "Jordi", null, "", "", List.of(), List.of());
        Shipment vaciado = servicio.editar(1L, vaciando);
        assertNull(vaciado.getHoraCierreRecogida());
        assertNull(vaciado.getLugarRecogida());
    }
}
