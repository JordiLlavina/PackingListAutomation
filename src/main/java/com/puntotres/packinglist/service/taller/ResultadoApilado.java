package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.List;

/** Los palets de una destinación, y lo que ha impedido apilar alguna caja. */
public class ResultadoApilado {

    private final List<PaletGenerado> palets = new ArrayList<>();
    private final List<String> bloqueos = new ArrayList<>();

    public List<PaletGenerado> getPalets() {
        return palets;
    }

    public List<String> getBloqueos() {
        return bloqueos;
    }
}
