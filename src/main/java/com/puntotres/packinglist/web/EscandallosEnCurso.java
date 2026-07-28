package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * El excel de escandallos ya generado, a la espera de que Jordi le dé a
 * «Descargar igualmente».
 *
 * Solo hace falta cuando el procesado ha dejado avisos: sin avisos el excel se
 * devuelve en la misma respuesta del POST y aquí no se guarda nada, porque no
 * hay ninguna pantalla intermedia de la que volver a pedirlo.
 */
@Component
@SessionScope
public class EscandallosEnCurso {

    private byte[] excel;
    private final List<String> hojas = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();

    public boolean estaVacio() {
        return excel == null;
    }

    public void guardar(byte[] excel, List<String> hojas, List<String> avisos) {
        reiniciar();
        this.excel = excel;
        this.hojas.addAll(hojas);
        this.avisos.addAll(avisos);
    }

    public void reiniciar() {
        excel = null;
        hojas.clear();
        avisos.clear();
    }

    public byte[] getExcel() { return excel; }
    public List<String> getHojas() { return hojas; }
    public List<String> getAvisos() { return avisos; }
}
