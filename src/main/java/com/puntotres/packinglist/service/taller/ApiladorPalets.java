package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Coloca las cajas de una destinación en palets.
 *
 * El palet tiene cuatro posiciones (2x2 sobre la base) y cada una es una pila
 * independiente: lo que limita es que la suma de alturas de UNA pila no pase
 * de la altura útil, no la altura del palet en conjunto. Las cuatro pueden
 * acabar a alturas distintas.
 *
 * El peso no entra aquí. El máximo de 18 kg por caja de AMI se comprueba en la
 * pantalla de revisión, cuando ya hay pesos: en este punto todavía no los hay.
 *
 * Es una heurística voraz —caja más alta primero, a la pila más baja donde
 * quepa— y se queda así a propósito. Buscar el apilado óptimo es un problema
 * caro y el almacén reordena a mano de todas formas; lo que hace falta es un
 * reparto razonable y siempre igual.
 */
@Service
public class ApiladorPalets {

    /**
     * @param alturaUtilCm altura máxima de cada pila, ya descontado lo que
     *                     levanta el palet vacío
     * @param posiciones   pilas por palet (cuatro, salvo que el yml diga otra cosa)
     */
    public ResultadoApilado apilar(List<CajaGenerada> cajas, int alturaUtilCm, int posiciones) {
        ResultadoApilado resultado = new ResultadoApilado();

        List<CajaGenerada> apilables = new ArrayList<>();
        for (CajaGenerada caja : cajas) {
            if (caja.alturaCm() <= 0) {
                resultado.getBloqueos().add("La medida de caja '" + caja.medidaCaja()
                        + "' de " + caja.destino() + " no se entiende: no se sabe cuánto mide "
                        + "de alto y no se puede apilar");
            } else if (caja.alturaCm() > alturaUtilCm) {
                resultado.getBloqueos().add("En " + caja.destino() + " hay una caja de "
                        + caja.alturaCm() + " cm de alto y en el palet solo caben "
                        + alturaUtilCm + " cm: hay que usar un cartón más bajo");
            } else {
                apilables.add(caja);
            }
        }
        if (!resultado.getBloqueos().isEmpty()) {
            return resultado;
        }

        // De la más alta a la más baja: si se empieza por las bajas, las
        // cuatro pilas se llenan a media altura y una caja alta se queda sin
        // sitio en un palet que aún tiene hueco de sobra.
        apilables.sort(Comparator.comparingInt(CajaGenerada::alturaCm).reversed());

        PaletGenerado actual = null;
        for (CajaGenerada caja : apilables) {
            if (actual == null || !actual.colocar(caja, alturaUtilCm)) {
                actual = new PaletGenerado(posiciones);
                actual.colocar(caja, alturaUtilCm);
                resultado.getPalets().add(actual);
            }
        }
        return resultado;
    }
}
