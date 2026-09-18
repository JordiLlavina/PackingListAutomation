package com.puntotres.packinglist.shipping.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.puntotres.packinglist.shipping.model.Direction;
import com.puntotres.packinglist.shipping.model.ShipmentStatus;
import com.puntotres.packinglist.shipping.service.FiltroEnvios;

/** Lo que se teclea en el formulario de filtros del listado de /envios. */
public class FiltroEnviosForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate desde;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate hasta;
    private ShipmentStatus estado;
    private Long motivoId;
    private String cliente;
    private Direction sentido;

    public FiltroEnvios aFiltro() { return new FiltroEnvios(desde, hasta, estado, motivoId, cliente, sentido); }

    public LocalDate getDesde() { return desde; } public void setDesde(LocalDate v) { desde = v; }
    public LocalDate getHasta() { return hasta; } public void setHasta(LocalDate v) { hasta = v; }
    public ShipmentStatus getEstado() { return estado; } public void setEstado(ShipmentStatus v) { estado = v; }
    public Long getMotivoId() { return motivoId; } public void setMotivoId(Long v) { motivoId = v; }
    public String getCliente() { return cliente; } public void setCliente(String v) { cliente = v; }
    public Direction getSentido() { return sentido; } public void setSentido(Direction v) { sentido = v; }
}
