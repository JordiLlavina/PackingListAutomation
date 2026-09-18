package com.puntotres.packinglist.carrier.dhl;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.puntotres.packinglist.carrier.dhl.dto.*;
import com.puntotres.packinglist.shipping.model.*;

/**
 * Del envío del núcleo al JSON de MyDHL API (OpenAPI 3.3.2). Todo lo que aquí
 * es un literal ("shipper", "outbound", "PCS", "QDDC") es un enum del contrato.
 *
 * Las listas vacías se devuelven como null: el esquema exige minItems 1 en
 * varias de ellas y una lista vacía da un 400 donde null simplemente no viaja
 * (la serialización omite nulos, ver DhlRestClientFactory).
 */
public class DhlRequestMapper {

    // El OpenAPI trae ejemplos con y sin espacio antes de GMT; la descripción
    // del campo (la fuente normativa) lo pone con espacio: 2025-01-18T17:00:00 GMT+01:00.
    // TODO DHL: confirmar contra el entorno de test que acepta el espacio.
    private static final DateTimeFormatter FECHA_DHL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss 'GMT'xxx");
    private static final DateTimeFormatter FECHA_ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final DhlProperties propiedades;

    public DhlRequestMapper(DhlProperties propiedades) { this.propiedades = propiedades; }

    public String fechaPlanificada(Shipment envio) {
        String pais = envio.getRemitente() == null ? null : envio.getRemitente().getPais();
        ZoneId zona = ZoneId.of(propiedades.getZonasHorarias().getOrDefault(pais, "Europe/Madrid"));
        ZonedDateTime momento = envio.getFechaRecogida().atTime(propiedades.getHoraRecogida()).atZone(zona);
        return FECHA_DHL.format(momento);
    }

    public DhlRateRequest tarifa(Shipment envio) {
        return new DhlRateRequest(
                new DhlRateCustomerDetails(direccionTarifa(envio.getRemitente()), direccionTarifa(envio.getDestinatario())),
                List.of(new DhlAccount("shipper", propiedades.getShipperAccount())),
                fechaPlanificada(envio),
                "metric",
                envio.isAduanero(),
                List.of(new DhlMonetaryAmount("declaredValue", envio.valorDeclarado(), envio.getMoneda())),
                new DhlEstimatedDeliveryDate(true, "QDDC"),
                "all",
                envio.getBultos().stream().map(b -> new DhlRatePackage(b.getPeso(), dimensiones(b))).toList());
    }

    public DhlShipmentRequest envio(Shipment envio) {
        return new DhlShipmentRequest(
                fechaPlanificada(envio),
                new DhlPickup(envio.isPedirRecogida(), envio.isPedirRecogida() ? envio.getHoraCierreRecogida() : null,
                        envio.isPedirRecogida() ? envio.getLugarRecogida() : null),
                envio.getProductoCodigo(),
                cuentas(envio),
                documentos(envio),
                referencias(envio),
                new DhlCustomerDetails(parte(envio.getRemitente()), parte(envio.getDestinatario())),
                contenido(envio));
    }

    private List<DhlAccount> cuentas(Shipment envio) {
        List<DhlAccount> cuentas = new ArrayList<>();
        String propia = propiedades.getShipperAccount();
        cuentas.add(new DhlAccount("shipper", propia));
        if (envio.getCuentaTransporte() != null && !envio.getCuentaTransporte().getNumero().equals(propia)) {
            cuentas.add(new DhlAccount("payer", envio.getCuentaTransporte().getNumero()));
        }
        if (envio.getCuentaAranceles() != null) {
            cuentas.add(new DhlAccount("duties-taxes", envio.getCuentaAranceles().getNumero()));
        }
        return cuentas;
    }

    private DhlOutputImageProperties documentos(Shipment envio) {
        List<DhlImageOption> opciones = new ArrayList<>();
        opciones.add(new DhlImageOption("label", propiedades.getPlantillaEtiqueta(), null, null, null, null, null));
        opciones.add(new DhlImageOption("waybillDoc", "ARCH_8x4", true, null, null, 1, null));
        if (envio.isAduanero()) {
            opciones.add(new DhlImageOption("invoice", "COMMERCIAL_INVOICE_P_10", true, "commercial", "eng", null, null));
        }
        return new DhlOutputImageProperties(300, "pdf", opciones, true, false);
    }

    private List<DhlCustomerReference> referencias(Shipment envio) {
        if (envio.getReferencia() == null) return null;
        return List.of(new DhlCustomerReference(envio.getReferencia(), "CU"));
    }

    private DhlContent contenido(Shipment envio) {
        List<DhlPackage> bultos = envio.getBultos().stream()
                .map(b -> new DhlPackage(b.getPeso(), dimensiones(b), referencias(envio), b.getDescripcion()))
                .toList();
        String descripcion = envio.getMotivo() == null ? "Shipment" : envio.getMotivo().getNombre();
        return new DhlContent(bultos, envio.isAduanero(), envio.valorDeclarado(), envio.getMoneda(),
                envio.isAduanero() ? declaracion(envio) : null, descripcion, envio.getIncoterm().name(), "metric");
    }

    private DhlExportDeclaration declaracion(Shipment envio) {
        String motivo = envio.getMotivo().getMotivoDhl().codigoDhl();
        List<DhlLineItem> lineas = new ArrayList<>();
        for (int i = 0; i < envio.getLineasAduana().size(); i++) {
            CustomsLineItem l = envio.getLineasAduana().get(i);
            lineas.add(new DhlLineItem(i + 1, l.getDescripcion(), l.getValorUnitario(),
                    new DhlQuantity(l.getCantidad(), l.getUnidad()),
                    List.of(new DhlCommodityCode("outbound", l.getCodigoHs())),
                    motivo, l.getPaisOrigen(), new DhlLineWeight(l.getPesoNeto(), l.getPesoBruto())));
        }
        // TODO DHL: si la factura comercial necesita el número de factura del ERP, añadir campo al envío.
        DhlInvoice factura = new DhlInvoice("PT-" + envio.getFechaRecogida().getYear() + "-" + envio.getId(), FECHA_ISO.format(envio.getFechaRecogida()));
        return new DhlExportDeclaration(lineas, factura, motivo, envio.getDestinatario().getCiudad());
    }

    private DhlParty parte(Contact c) {
        String persona = c.getPersona() == null || c.getPersona().isBlank() ? c.getEmpresa() : c.getPersona();
        List<DhlRegistrationNumber> registros = new ArrayList<>();
        if (c.getNifVat() != null && !c.getNifVat().isBlank()) registros.add(new DhlRegistrationNumber("VAT", c.getNifVat(), c.getPais()));
        if (c.getEori() != null && !c.getEori().isBlank()) registros.add(new DhlRegistrationNumber("EOR", c.getEori(), c.getPais()));
        // TODO DHL: con qué typeCode viaja el ICE marroquí (FTN, CIC o VAT). Hasta saberlo, VAT si no hay NIF.
        if (registros.isEmpty() && c.getIdentificadorMarroqui() != null && !c.getIdentificadorMarroqui().isBlank())
            registros.add(new DhlRegistrationNumber("VAT", c.getIdentificadorMarroqui(), c.getPais()));
        return new DhlParty(
                new DhlPostalAddress(c.getCodigoPostal(), c.getCiudad(), c.getPais(), c.getProvinciaCodigo(),
                        c.getDireccion1(), c.getDireccion2(), c.getDireccion3()),
                new DhlContactInformation(c.getEmail(), c.getTelefono(), c.getEmpresa(), persona),
                registros.isEmpty() ? null : registros,
                c.getTipo() == ContactType.PRIVATE ? "direct_consumer" : "business");
    }

    private static DhlRateAddress direccionTarifa(Contact c) {
        return new DhlRateAddress(c.getCodigoPostal(), c.getCiudad(), c.getPais(), c.getDireccion1(), c.getDireccion2(), c.getDireccion3());
    }

    private static DhlDimensions dimensiones(ShipmentPackage b) {
        return new DhlDimensions(b.getLargo(), b.getAncho(), b.getAlto());
    }
}
