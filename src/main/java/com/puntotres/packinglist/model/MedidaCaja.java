package com.puntotres.packinglist.model;

import java.util.Optional;

/**
 * Las tres dimensiones de un cartón, en centímetros, tal como se escriben en
 * todo el proyecto: <strong>largo x ancho x alto</strong>. En "60x40x45" la
 * caja mide 45 cm de alto.
 *
 * Que la altura sea la ÚLTIMA y no la de en medio se ve en el propio catálogo
 * de taras: los cartones comparten el suelo (60x40) y se diferencian en el
 * tercer número (51, 45, 40, 30). Importa porque de la altura salen las pilas
 * del palet y, con ellas, cuántos palets lleva un envío.
 *
 * Una medida que no se pueda leer así devuelve vacío: nunca se adivina, ni se
 * completa con la del cartón vecino.
 */
public record MedidaCaja(int largo, int ancho, int alto) {

    /** Lee "60x40x45" en cualquier combinación de mayúsculas y espacios. */
    public static Optional<MedidaCaja> parse(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String[] partes = texto.trim().toLowerCase().replace(" ", "").split("x");
        if (partes.length != 3) {
            return Optional.empty();
        }
        int[] dimensiones = new int[3];
        for (int i = 0; i < 3; i++) {
            try {
                dimensiones[i] = Integer.parseInt(partes[i]);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (dimensiones[i] <= 0) {
                return Optional.empty();
            }
        }
        return Optional.of(new MedidaCaja(dimensiones[0], dimensiones[1], dimensiones[2]));
    }

    /** La forma canónica, la misma que usan las claves de la tabla de taras. */
    public String normalizada() {
        return largo + "x" + ancho + "x" + alto;
    }

    public long volumen() {
        return (long) largo * ancho * alto;
    }
}
