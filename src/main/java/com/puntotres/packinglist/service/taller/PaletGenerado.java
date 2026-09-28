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

    /**
     * Las mismas cajas que las pilas, en el orden en el que llegaron. Es una
     * segunda vista de lo mismo, no otro contenido, y existe porque el orden
     * de numeración no puede ser el de las pilas (ver {@link #cajas()}).
     */
    private final List<CajaGenerada> enOrdenDeLlegada = new ArrayList<>();

    public PaletGenerado(int posiciones) {
        for (int i = 0; i < posiciones; i++) {
            pilas.add(new ArrayList<>());
        }
    }

    public List<List<CajaGenerada>> pilas() {
        return pilas;
    }

    /**
     * Todas sus cajas <b>en el orden en que se generaron</b>, que es el orden
     * en el que se numeran.
     *
     * No es el orden de las pilas, y la diferencia se ve en el papel. Las
     * cajas llegan agrupadas por artículo —todas las de un modelo seguidas—,
     * pero se reparten entre las cuatro posiciones del palet: con cajas de la
     * misma altura, cada una va a una pila distinta por turnos. Leyendo pila a
     * pila, esas cuatro cajas consecutivas del mismo modelo acababan numeradas
     * 1, 4, 7 y 10, y en la hoja de trabajo del operario un palet se veía como
     * modelo A, modelo B, modelo A, modelo B... Preparar eso obliga a ir y
     * venir entre dos montones de mercancía por cada caja.
     *
     * Numerar por orden de llegada deja cada modelo en números seguidos hasta
     * donde se pueda. Lo que sigue estando garantizado —y es lo único que el
     * resto del programa necesita de un palet— es que sus cajas ocupan un
     * <b>rango contiguo</b>: se numeran todas las de un palet antes de pasar
     * al siguiente, ordenadas como se ordenen.
     *
     * Las pilas no se ven en ningún documento: ni el packing list, ni las
     * etiquetas, ni la hoja del operario dicen qué caja va en qué posición, y
     * el almacén monta el palet como le conviene. Por eso se puede numerar en
     * un orden y apilar en otro sin que nada quede descuadrado.
     */
    public List<CajaGenerada> cajas() {
        return List.copyOf(enOrdenDeLlegada);
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
        enOrdenDeLlegada.add(caja);
        return true;
    }

    private static int alturaDe(List<CajaGenerada> pila) {
        return pila.stream().mapToInt(CajaGenerada::alturaCm).sum();
    }
}
