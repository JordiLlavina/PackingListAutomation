package com.puntotres.packinglist.shipping.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bloque envios.* de application.yml. Ver el yml para qué es cada cosa. */
@ConfigurationProperties(prefix = "envios")
public class EnviosProperties {

    private String moneda = "EUR";
    private String paisSede = "ES";
    private List<String> paisesUnionAduanera = new ArrayList<>();
    private List<String> responsables = new ArrayList<>();
    private String horaCierreRecogida = "18:00";
    private String lugarRecogida = "Recepción";

    public String getHoraCierreRecogida() { return horaCierreRecogida; }
    public void setHoraCierreRecogida(String v) { this.horaCierreRecogida = v; }
    public String getLugarRecogida() { return lugarRecogida; }
    public void setLugarRecogida(String v) { this.lugarRecogida = v; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
    public String getPaisSede() { return paisSede; }
    public void setPaisSede(String paisSede) { this.paisSede = paisSede; }
    public List<String> getPaisesUnionAduanera() { return paisesUnionAduanera; }
    public void setPaisesUnionAduanera(List<String> v) { this.paisesUnionAduanera = v; }
    public List<String> getResponsables() { return responsables; }
    public void setResponsables(List<String> v) { this.responsables = v; }
}
