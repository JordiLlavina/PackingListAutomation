package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;

/**
 * Pesos editados a mano en la pantalla de revisión. Cada entrada apunta a
 * una caja concreta por (indiceDestino, indiceCaja) — la POSICIÓN de la
 * caja en la lista de su destinación, no su número: el número de caja puede
 * repetirse (caja mixta con dos colores). Un campo de peso vacío llega como
 * null y significa "no tocar lo que haya".
 */
public class RevisionForm {

    private List<PesoEditado> pesos = new ArrayList<>();

    public List<PesoEditado> getPesos() { return pesos; }
    public void setPesos(List<PesoEditado> pesos) { this.pesos = pesos; }

    public static class PesoEditado {

        private int indiceDestino;
        private int indiceCaja;
        private Double pesoNetoKg;
        private Double pesoBrutoKg;

        public int getIndiceDestino() { return indiceDestino; }
        public void setIndiceDestino(int indiceDestino) { this.indiceDestino = indiceDestino; }

        public int getIndiceCaja() { return indiceCaja; }
        public void setIndiceCaja(int indiceCaja) { this.indiceCaja = indiceCaja; }

        public Double getPesoNetoKg() { return pesoNetoKg; }
        public void setPesoNetoKg(Double pesoNetoKg) { this.pesoNetoKg = pesoNetoKg; }

        public Double getPesoBrutoKg() { return pesoBrutoKg; }
        public void setPesoBrutoKg(Double pesoBrutoKg) { this.pesoBrutoKg = pesoBrutoKg; }
    }
}
