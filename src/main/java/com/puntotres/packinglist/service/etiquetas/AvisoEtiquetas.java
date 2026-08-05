package com.puntotres.packinglist.service.etiquetas;

/**
 * Formato común de los avisos de etiquetas, para que la lista de la pantalla
 * de resultados se lea de un vistazo:
 *
 * <pre>
 * CHINA: Caja 4. Lleva varias tallas. Se generan códigos de barra aparte
 * IVRY: sin palets. La hoja de etiquetas de palet sale en blanco
 * </pre>
 *
 * La destinación va delante porque es como el usuario mira la salida (un
 * excel por destinación) y porque deja el resto en frases cortas en vez de
 * una subordinada larga. Nada de vocabulario técnico: quien lee esto habla de
 * "la entrada", no del JSON ni de las fotos de las que salió.
 *
 * Los dos generadores lo comparten a propósito: el formato de un aviso es una
 * sola decisión, no una por cliente.
 */
final class AvisoEtiquetas {

    private AvisoEtiquetas() {
    }

    /** Aviso de una caja concreta; texto empieza frase nueva tras el punto. */
    static String deCaja(String destino, int numeroCaja, String texto) {
        return destino + ": Caja " + numeroCaja + ". " + texto;
    }

    /**
     * Aviso de la destinación entera. Los de palet también pasan por aquí
     * ("JAPAN: Palet 2 con cajas sin peso. ..."): un palet no es una unidad
     * que el usuario busque por su cuenta, se mira dentro de su destinación.
     */
    static String deDestino(String destino, String texto) {
        return destino + ": " + texto;
    }
}
