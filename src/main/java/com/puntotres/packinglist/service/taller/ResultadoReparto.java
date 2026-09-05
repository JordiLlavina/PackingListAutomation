package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.List;

/**
 * Qué se envía, a dónde y cuánto, más lo que hay que contarle a quien prepara
 * el envío.
 *
 * Los avisos son informativos —"a PARIS le faltan 9", "sobran 10 que se
 * quedan"— y no impiden generar: quien prepara el envío tiene que saberlo,
 * pero el packing es correcto igualmente. Los bloqueos sí paran: son datos
 * que faltan o normas que no existen, y seguir significaría inventárselos.
 */
public class ResultadoReparto {

    private final List<ArticuloDestinado> articulos = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();
    private final List<String> bloqueos = new ArrayList<>();

    public List<ArticuloDestinado> getArticulos() {
        return articulos;
    }

    public List<String> getAvisos() {
        return avisos;
    }

    public List<String> getBloqueos() {
        return bloqueos;
    }
}
