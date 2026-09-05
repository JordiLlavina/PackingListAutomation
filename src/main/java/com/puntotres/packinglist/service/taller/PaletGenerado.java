package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.List;

/**
 * Un palet con sus pilas. Las cuatro posiciones (2x2 sobre la base) son
 * independientes y pueden acabar a alturas distintas: se acepta el último
 * palet a media altura y con pilas incompletas.
 */
public class PaletGenerado {

    private final List<List<CajaGenerada>> pilas = new ArrayList<>();

    public PaletGenerado(int posiciones) {
        for (int i = 0; i < posiciones; i++) {
            pilas.add(new ArrayList<>());
        }
    }

    public List<List<CajaGenerada>> pilas() {
        return pilas;
    }

    /**
     * Todas sus cajas, pila a pila y de abajo arriba. Este es el orden en el
     * que se numeran: así el palet ocupa un rango contiguo de números de caja,
     * que es lo que el resto del programa espera de un palet.
     */
    public List<CajaGenerada> cajas() {
        return pilas.stream().flatMap(List::stream).toList();
    }

    public int alturaMaximaCm() {
        return pilas.stream().mapToInt(PaletGenerado::alturaDe).max().orElse(0);
    }

    public boolean estaVacio() {
        return pilas.stream().allMatch(List::isEmpty);
    }

    /** Coloca la caja en la pila más baja donde quepa; false si no cabe en ninguna. */
    boolean colocar(CajaGenerada caja, int alturaUtilCm) {
        List<CajaGenerada> elegida = null;
        int menor = Integer.MAX_VALUE;
        for (List<CajaGenerada> pila : pilas) {
            int altura = alturaDe(pila);
            if (altura + caja.alturaCm() <= alturaUtilCm && altura < menor) {
                elegida = pila;
                menor = altura;
            }
        }
        if (elegida == null) {
            return false;
        }
        elegida.add(caja);
        return true;
    }

    private static int alturaDe(List<CajaGenerada> pila) {
        return pila.stream().mapToInt(CajaGenerada::alturaCm).sum();
    }
}
