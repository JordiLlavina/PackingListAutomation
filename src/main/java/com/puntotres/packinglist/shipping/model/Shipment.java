package com.puntotres.packinglist.shipping.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

/**
 * Un envío por transportista. Es un dato desde el primer guardado (DRAFT), por
 * eso no vive en sesión: un borrador tiene que sobrevivir a cerrar el navegador.
 *
 * Las transiciones de estado las decide ShipmentService; aquí solo se aplican
 * y se anotan en el historial. requestId se fija al confirmar y viaja a DHL
 * como Message-Reference: reintentar tras un error reutiliza el mismo, para
 * que un envío confirmado no se cree dos veces.
 */
@Entity
@Table(name = "shipment", uniqueConstraints = @UniqueConstraint(name = "uk_shipment_request_id", columnNames = "request_id"))
public class Shipment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(name = "transportista", length = 20, nullable = false)
    private CarrierCode transportista = CarrierCode.DHL_EXPRESS;
    @Enumerated(EnumType.STRING) @Column(name = "estado", length = 12, nullable = false)
    private ShipmentStatus estado = ShipmentStatus.DRAFT;
    @Enumerated(EnumType.STRING) @Column(name = "sentido", length = 12)
    private Direction sentido;
    @Column(name = "aduanero", nullable = false) private boolean aduanero = true;

    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "remitente_id") private Contact remitente;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "destinatario_id") private Contact destinatario;
    @Column(name = "fecha_recogida") private LocalDate fechaRecogida;
    /** Recogida del mensajero en la misma llamada (pickup.isRequested), con hora de cierre HH:MM y lugar; sin pickupDetails, DHL recoge en la dirección del remitente. */
    @Column(name = "pedir_recogida", nullable = false) private boolean pedirRecogida = true;
    @Column(name = "hora_cierre_recogida", length = 5) private String horaCierreRecogida;
    @Column(name = "lugar_recogida", length = 80) private String lugarRecogida;
    @Column(name = "numero_confirmacion_recogida", length = 40) private String numeroConfirmacionRecogida;
    @Column(name = "producto_codigo", length = 10) private String productoCodigo;
    @Column(name = "producto_nombre", length = 80) private String productoNombre;

    @Enumerated(EnumType.STRING) @Column(name = "incoterm", length = 3, nullable = false) private Incoterm incoterm = Incoterm.DAP;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "cuenta_transporte_id") private BillingAccount cuentaTransporte;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "cuenta_aranceles_id") private BillingAccount cuentaAranceles;

    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "motivo_id") private ShipmentReason motivo;
    @Column(name = "cliente", length = 60) private String cliente;
    @Column(name = "temporada", length = 60) private String temporada;
    @Column(name = "referencia", length = 35) private String referencia;
    @Column(name = "notas", length = 1000) private String notas;
    @Column(name = "responsable", length = 120, nullable = false) private String responsable;

    @Column(name = "coste_estimado", precision = 12, scale = 2) private BigDecimal costeEstimado;
    @Column(name = "moneda", length = 3, nullable = false) private String moneda = "EUR";
    @Column(name = "coste_facturado", precision = 12, scale = 2) private BigDecimal costeFacturado;

    @Column(name = "numero_guia", length = 40) private String numeroGuia;
    @Column(name = "url_seguimiento", length = 255) private String urlSeguimiento;
    @Column(name = "ultimo_error", length = 4000) private String ultimoError;
    @Column(name = "request_id", length = 36) private String requestId;

    @Column(name = "fecha_creacion", nullable = false) private LocalDateTime fechaCreacion = LocalDateTime.now();
    @Column(name = "fecha_actualizacion", nullable = false) private LocalDateTime fechaActualizacion = LocalDateTime.now();
    @Version @Column(name = "version", nullable = false) private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true) @JoinColumn(name = "shipment_id", nullable = false)
    @OrderColumn(name = "orden")
    private List<ShipmentPackage> bultos = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true) @JoinColumn(name = "shipment_id", nullable = false)
    @OrderColumn(name = "orden")
    private List<CustomsLineItem> lineasAduana = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true) @JoinColumn(name = "shipment_id", nullable = false)
    @OrderColumn(name = "orden")
    private List<RateOffer> ofertas = new ArrayList<>();

    @OneToMany(mappedBy = "envio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShipmentDocument> documentos = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true) @JoinColumn(name = "shipment_id", nullable = false)
    @OrderBy("fecha asc, id asc")
    private List<ShipmentStatusHistory> historial = new ArrayList<>();

    protected Shipment() { }

    public Shipment(String responsable) {
        this.responsable = responsable;
    }

    public void cambiarEstado(ShipmentStatus nuevo, String quien, String detalle) {
        historial.add(new ShipmentStatusHistory(estado, nuevo, quien, detalle));
        estado = nuevo;
        tocar();
    }

    public void reemplazarOfertas(List<RateOffer> nuevas) {
        ofertas.clear();
        ofertas.addAll(nuevas);
    }

    public void borrarOfertas() { ofertas.clear(); }

    public void anadirDocumento(String tipo, String formato, byte[] contenido) {
        documentos.add(new ShipmentDocument(this, tipo, formato, contenido));
    }

    public BigDecimal valorDeclarado() {
        return lineasAduana.stream().map(CustomsLineItem::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public double pesoBultos() {
        return bultos.stream().map(ShipmentPackage::getPeso).filter(p -> p != null).mapToDouble(Double::doubleValue).sum();
    }

    public double pesoBrutoLineas() {
        return lineasAduana.stream().map(CustomsLineItem::getPesoBruto).filter(p -> p != null).mapToDouble(Double::doubleValue).sum();
    }

    public boolean editable() { return estado == ShipmentStatus.DRAFT || estado == ShipmentStatus.QUOTED; }

    public void tocar() { fechaActualizacion = LocalDateTime.now(); }

    // getters; setters solo de lo que edita el usuario o fija el servicio
    public Long getId() { return id; }
    public CarrierCode getTransportista() { return transportista; }
    public ShipmentStatus getEstado() { return estado; }
    public Direction getSentido() { return sentido; } public void setSentido(Direction v) { sentido = v; }
    public boolean isAduanero() { return aduanero; } public void setAduanero(boolean v) { aduanero = v; }
    public Contact getRemitente() { return remitente; } public void setRemitente(Contact v) { remitente = v; }
    public Contact getDestinatario() { return destinatario; } public void setDestinatario(Contact v) { destinatario = v; }
    public LocalDate getFechaRecogida() { return fechaRecogida; } public void setFechaRecogida(LocalDate v) { fechaRecogida = v; }
    public boolean isPedirRecogida() { return pedirRecogida; } public void setPedirRecogida(boolean v) { pedirRecogida = v; }
    public String getHoraCierreRecogida() { return horaCierreRecogida; } public void setHoraCierreRecogida(String v) { horaCierreRecogida = v; }
    public String getLugarRecogida() { return lugarRecogida; } public void setLugarRecogida(String v) { lugarRecogida = v; }
    public String getNumeroConfirmacionRecogida() { return numeroConfirmacionRecogida; } public void setNumeroConfirmacionRecogida(String v) { numeroConfirmacionRecogida = v; }
    public String getProductoCodigo() { return productoCodigo; } public void setProductoCodigo(String v) { productoCodigo = v; }
    public String getProductoNombre() { return productoNombre; } public void setProductoNombre(String v) { productoNombre = v; }
    public Incoterm getIncoterm() { return incoterm; } public void setIncoterm(Incoterm v) { incoterm = v; }
    public BillingAccount getCuentaTransporte() { return cuentaTransporte; } public void setCuentaTransporte(BillingAccount v) { cuentaTransporte = v; }
    public BillingAccount getCuentaAranceles() { return cuentaAranceles; } public void setCuentaAranceles(BillingAccount v) { cuentaAranceles = v; }
    public ShipmentReason getMotivo() { return motivo; } public void setMotivo(ShipmentReason v) { motivo = v; }
    public String getCliente() { return cliente; } public void setCliente(String v) { cliente = v; }
    public String getTemporada() { return temporada; } public void setTemporada(String v) { temporada = v; }
    public String getReferencia() { return referencia; } public void setReferencia(String v) { referencia = v; }
    public String getNotas() { return notas; } public void setNotas(String v) { notas = v; }
    public String getResponsable() { return responsable; } public void setResponsable(String v) { responsable = v; }
    public BigDecimal getCosteEstimado() { return costeEstimado; } public void setCosteEstimado(BigDecimal v) { costeEstimado = v; }
    public String getMoneda() { return moneda; } public void setMoneda(String v) { moneda = v; }
    public BigDecimal getCosteFacturado() { return costeFacturado; }
    public String getNumeroGuia() { return numeroGuia; } public void setNumeroGuia(String v) { numeroGuia = v; }
    public String getUrlSeguimiento() { return urlSeguimiento; } public void setUrlSeguimiento(String v) { urlSeguimiento = v; }
    public String getUltimoError() { return ultimoError; } public void setUltimoError(String v) { ultimoError = v == null ? null : v.substring(0, Math.min(4000, v.length())); }
    public String getRequestId() { return requestId; } public void setRequestId(String v) { requestId = v; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public long getVersion() { return version; }
    public List<ShipmentPackage> getBultos() { return bultos; }
    public List<CustomsLineItem> getLineasAduana() { return lineasAduana; }
    public List<RateOffer> getOfertas() { return ofertas; }
    public List<ShipmentDocument> getDocumentos() { return documentos; }
    public List<ShipmentStatusHistory> getHistorial() { return historial; }
}
