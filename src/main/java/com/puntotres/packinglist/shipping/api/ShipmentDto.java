package com.puntotres.packinglist.shipping.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.puntotres.packinglist.shipping.model.*;

/** Vista JSON de un envío. Sin los PDF: esos van por /documents/{tipo}.pdf. */
public record ShipmentDto(Long id, CarrierCode transportista, ShipmentStatus estado, Direction sentido, boolean aduanero,
                          ContactDto remitente, ContactDto destinatario, LocalDate fechaRecogida, String productoCodigo, String productoNombre,
                          Incoterm incoterm, String cuentaTransporte, String cuentaAranceles, String motivo,
                          String cliente, String temporada, String referencia, String notas, String responsable,
                          boolean pedirRecogida, String horaCierreRecogida, String lugarRecogida, String numeroConfirmacionRecogida,
                          BigDecimal costeEstimado, String moneda, BigDecimal costeFacturado, BigDecimal valorDeclarado,
                          String numeroGuia, String urlSeguimiento, String ultimoError, String requestId,
                          LocalDateTime fechaCreacion, LocalDateTime fechaActualizacion,
                          List<Bulto> bultos, List<Linea> lineasAduana, List<OfferDto> ofertas, List<String> documentos, List<Historial> historial) {

    public record Bulto(Double peso, Double largo, Double ancho, Double alto, String descripcion) { }
    public record Linea(String descripcion, String codigoHs, Integer cantidad, String unidad, BigDecimal valorUnitario, Double pesoNeto, Double pesoBruto, String paisOrigen, Long articuloId) { }
    public record Historial(ShipmentStatus estadoAnterior, ShipmentStatus estadoNuevo, LocalDateTime fecha, String responsable, String detalle) { }

    static ShipmentDto de(Shipment e, List<String> documentos) {
        return new ShipmentDto(e.getId(), e.getTransportista(), e.getEstado(), e.getSentido(), e.isAduanero(),
                e.getRemitente() == null ? null : ContactDto.de(e.getRemitente()), e.getDestinatario() == null ? null : ContactDto.de(e.getDestinatario()),
                e.getFechaRecogida(), e.getProductoCodigo(), e.getProductoNombre(), e.getIncoterm(),
                e.getCuentaTransporte() == null ? null : e.getCuentaTransporte().getNumero(), e.getCuentaAranceles() == null ? null : e.getCuentaAranceles().getNumero(),
                e.getMotivo() == null ? null : e.getMotivo().getNombre(),
                e.getCliente(), e.getTemporada(), e.getReferencia(), e.getNotas(), e.getResponsable(),
                e.isPedirRecogida(), e.getHoraCierreRecogida(), e.getLugarRecogida(), e.getNumeroConfirmacionRecogida(),
                e.getCosteEstimado(), e.getMoneda(), e.getCosteFacturado(), e.valorDeclarado(),
                e.getNumeroGuia(), e.getUrlSeguimiento(), e.getUltimoError(), e.getRequestId(), e.getFechaCreacion(), e.getFechaActualizacion(),
                e.getBultos().stream().map(b -> new Bulto(b.getPeso(), b.getLargo(), b.getAncho(), b.getAlto(), b.getDescripcion())).toList(),
                e.getLineasAduana().stream().map(l -> new Linea(l.getDescripcion(), l.getCodigoHs(), l.getCantidad(), l.getUnidad(), l.getValorUnitario(), l.getPesoNeto(), l.getPesoBruto(), l.getPaisOrigen(), l.getArticuloId())).toList(),
                e.getOfertas().stream().map(OfferDto::de).toList(),
                documentos,
                e.getHistorial().stream().map(h -> new Historial(h.getEstadoAnterior(), h.getEstadoNuevo(), h.getFecha(), h.getResponsable(), h.getDetalle())).toList());
    }
}
