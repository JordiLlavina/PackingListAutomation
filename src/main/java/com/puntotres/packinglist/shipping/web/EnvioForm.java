package com.puntotres.packinglist.shipping.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.puntotres.packinglist.shipping.model.*;
import com.puntotres.packinglist.shipping.service.DatosEnvio;

/**
 * Lo que se teclea en /envios/{id}. Las listas se enlazan por índice
 * (bultos[0].peso); añadir y quitar filas son submits que las modifican en el
 * servidor y vuelven a pintar, como en la revisión del packing list.
 */
public class EnvioForm {

    private Long remitenteId, destinatarioId, cuentaTransporteId, cuentaArancelesId, motivoId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fechaRecogida;
    private Incoterm incoterm = Incoterm.DAP;
    private String cliente, temporada, referencia, notas, responsable, productoCodigo, horaCierreRecogida, lugarRecogida;
    private boolean pedirRecogida = true;
    private List<BultoForm> bultos = new ArrayList<>();
    private List<LineaForm> lineas = new ArrayList<>();

    public static EnvioForm de(Shipment e) {
        EnvioForm f = new EnvioForm();
        f.remitenteId = id(e.getRemitente()); f.destinatarioId = id(e.getDestinatario());
        f.fechaRecogida = e.getFechaRecogida(); f.incoterm = e.getIncoterm();
        f.cuentaTransporteId = e.getCuentaTransporte() == null ? null : e.getCuentaTransporte().getId();
        f.cuentaArancelesId = e.getCuentaAranceles() == null ? null : e.getCuentaAranceles().getId();
        f.motivoId = e.getMotivo() == null ? null : e.getMotivo().getId();
        f.cliente = e.getCliente(); f.temporada = e.getTemporada(); f.referencia = e.getReferencia(); f.notas = e.getNotas();
        f.responsable = e.getResponsable(); f.productoCodigo = e.getProductoCodigo();
        f.pedirRecogida = e.isPedirRecogida(); f.horaCierreRecogida = e.getHoraCierreRecogida(); f.lugarRecogida = e.getLugarRecogida();
        for (ShipmentPackage b : e.getBultos()) f.bultos.add(new BultoForm(b.getPeso(), b.getLargo(), b.getAncho(), b.getAlto(), b.getDescripcion()));
        for (CustomsLineItem l : e.getLineasAduana()) f.lineas.add(new LineaForm(l.getDescripcion(), l.getCodigoHs(), l.getCantidad(), l.getValorUnitario(), l.getPesoNeto(), l.getPesoBruto(), l.getPaisOrigen(), l.getArticuloId()));
        return f;
    }

    public DatosEnvio aDatos() {
        return new DatosEnvio(remitenteId, destinatarioId, fechaRecogida, incoterm, cuentaTransporteId, cuentaArancelesId, motivoId,
                cliente, temporada, referencia, notas, responsable, pedirRecogida, horaCierreRecogida, lugarRecogida,
                bultos.stream().map(b -> new DatosEnvio.DatosBulto(b.peso, b.largo, b.ancho, b.alto, b.descripcion)).toList(),
                lineas.stream().map(l -> new DatosEnvio.DatosLinea(l.descripcion, l.codigoHs, l.cantidad, l.valorUnitario, l.pesoNeto, l.pesoBruto, l.paisOrigen, l.articuloId)).toList());
    }

    /** Preselecciona cuentas e incoterm del destinatario si el envío aún no los tiene. */
    public void heredarDe(Contact destinatario) {
        if (destinatario == null) return;
        if (cuentaTransporteId == null && destinatario.getCuentaTransportePorDefecto() != null) cuentaTransporteId = destinatario.getCuentaTransportePorDefecto().getId();
        if (cuentaArancelesId == null && destinatario.getCuentaArancelesPorDefecto() != null) cuentaArancelesId = destinatario.getCuentaArancelesPorDefecto().getId();
        if (destinatario.getIncotermPorDefecto() != null && incoterm == Incoterm.DAP) incoterm = destinatario.getIncotermPorDefecto();
    }

    public void anadirLineaDe(Article a) {
        lineas.add(new LineaForm(a.getDescripcionAduanera(), a.getCodigoHs(), 1, a.getValorUnitario(), a.getPesoNetoUnitario(), a.getPesoBrutoUnitario(), a.getPaisOrigen(), a.getId()));
    }

    private static Long id(Contact c) { return c == null ? null : c.getId(); }

    public static class BultoForm {
        private Double peso, largo, ancho, alto; private String descripcion;
        public BultoForm() { }
        public BultoForm(Double peso, Double largo, Double ancho, Double alto, String descripcion) { this.peso = peso; this.largo = largo; this.ancho = ancho; this.alto = alto; this.descripcion = descripcion; }
        public Double getPeso() { return peso; } public void setPeso(Double v) { peso = v; }
        public Double getLargo() { return largo; } public void setLargo(Double v) { largo = v; }
        public Double getAncho() { return ancho; } public void setAncho(Double v) { ancho = v; }
        public Double getAlto() { return alto; } public void setAlto(Double v) { alto = v; }
        public String getDescripcion() { return descripcion; } public void setDescripcion(String v) { descripcion = v; }
    }

    public static class LineaForm {
        private String descripcion, codigoHs, paisOrigen; private Integer cantidad; private BigDecimal valorUnitario; private Double pesoNeto, pesoBruto; private Long articuloId;
        public LineaForm() { }
        public LineaForm(String descripcion, String codigoHs, Integer cantidad, BigDecimal valorUnitario, Double pesoNeto, Double pesoBruto, String paisOrigen, Long articuloId) {
            this.descripcion = descripcion; this.codigoHs = codigoHs; this.cantidad = cantidad; this.valorUnitario = valorUnitario; this.pesoNeto = pesoNeto; this.pesoBruto = pesoBruto; this.paisOrigen = paisOrigen; this.articuloId = articuloId;
        }
        public String getDescripcion() { return descripcion; } public void setDescripcion(String v) { descripcion = v; }
        public String getCodigoHs() { return codigoHs; } public void setCodigoHs(String v) { codigoHs = v; }
        public String getPaisOrigen() { return paisOrigen; } public void setPaisOrigen(String v) { paisOrigen = v; }
        public Integer getCantidad() { return cantidad; } public void setCantidad(Integer v) { cantidad = v; }
        public BigDecimal getValorUnitario() { return valorUnitario; } public void setValorUnitario(BigDecimal v) { valorUnitario = v; }
        public Double getPesoNeto() { return pesoNeto; } public void setPesoNeto(Double v) { pesoNeto = v; }
        public Double getPesoBruto() { return pesoBruto; } public void setPesoBruto(Double v) { pesoBruto = v; }
        public Long getArticuloId() { return articuloId; } public void setArticuloId(Long v) { articuloId = v; }
    }

    // getters y setters
    public Long getRemitenteId() { return remitenteId; } public void setRemitenteId(Long v) { remitenteId = v; }
    public Long getDestinatarioId() { return destinatarioId; } public void setDestinatarioId(Long v) { destinatarioId = v; }
    public Long getCuentaTransporteId() { return cuentaTransporteId; } public void setCuentaTransporteId(Long v) { cuentaTransporteId = v; }
    public Long getCuentaArancelesId() { return cuentaArancelesId; } public void setCuentaArancelesId(Long v) { cuentaArancelesId = v; }
    public Long getMotivoId() { return motivoId; } public void setMotivoId(Long v) { motivoId = v; }
    public LocalDate getFechaRecogida() { return fechaRecogida; } public void setFechaRecogida(LocalDate v) { fechaRecogida = v; }
    public Incoterm getIncoterm() { return incoterm; } public void setIncoterm(Incoterm v) { incoterm = v; }
    public String getCliente() { return cliente; } public void setCliente(String v) { cliente = v; }
    public String getTemporada() { return temporada; } public void setTemporada(String v) { temporada = v; }
    public String getReferencia() { return referencia; } public void setReferencia(String v) { referencia = v; }
    public String getNotas() { return notas; } public void setNotas(String v) { notas = v; }
    public String getResponsable() { return responsable; } public void setResponsable(String v) { responsable = v; }
    public String getProductoCodigo() { return productoCodigo; } public void setProductoCodigo(String v) { productoCodigo = v; }
    public boolean isPedirRecogida() { return pedirRecogida; } public void setPedirRecogida(boolean v) { pedirRecogida = v; }
    public String getHoraCierreRecogida() { return horaCierreRecogida; } public void setHoraCierreRecogida(String v) { horaCierreRecogida = v; }
    public String getLugarRecogida() { return lugarRecogida; } public void setLugarRecogida(String v) { lugarRecogida = v; }
    public List<BultoForm> getBultos() { return bultos; } public void setBultos(List<BultoForm> v) { bultos = v; }
    public List<LineaForm> getLineas() { return lineas; } public void setLineas(List<LineaForm> v) { lineas = v; }
}
