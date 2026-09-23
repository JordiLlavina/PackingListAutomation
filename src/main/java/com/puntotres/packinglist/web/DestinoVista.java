package com.puntotres.packinglist.web;

import java.util.List;

/**
 * Una destinación en la pantalla de revisión: su índice en el envío (que
 * localiza sus cajas al aplicar los pesos), su nombre, sus filas ya
 * compactadas y el Livraison code de la destinación entera (null si el
 * cliente no lo usa: solo APC lo tiene).
 *
 * <p><b>Una destinación puede llevar dentro varias destinaciones hijas</b>, y
 * por eso las filas van repartidas en {@link Bloque}s en vez de en una sola
 * tabla. En APC, {@code AUSTRALIA}, {@code CHINE FRANCH} y {@code WHOLESALE}
 * viajan bajo {@code WHOLESALE}: comparten fichero, hoja y dirección, pero
 * <b>no comparten bulto ni palet</b>, así que una tabla titulada solo
 * "WHOLESALE (2 cajas)" enseñaba dos cajas en dos palets distintos sin decir
 * en ningún sitio por qué. El bloque pone el nombre de la hija encima de sus
 * filas.
 *
 * <p>La sección sigue siendo UNA por destinación padre, que es lo que de
 * verdad es un fichero: partirla en dos secciones prometería dos excels y
 * dejaría el Livraison code —que es de la destinación entera— repetido.
 *
 * {@code totalCajas} son los bultos reales y NO coinciden con el número de
 * filas: una fila puede representar un tramo compactado ("4-8").
 */
public record DestinoVista(int indice, String nombre, List<Bloque> bloques, int totalCajas,
                           String livraisonCode) {

    /**
     * Las filas de una destinación hija dentro de su padre. Un cliente sin
     * destinaciones hijas (AMI, los genéricos) tiene un bloque único que se
     * llama como la destinación, y la pantalla no pinta ningún título extra.
     */
    public record Bloque(String nombre, List<FilaCaja> filas, int totalCajas) {
    }

    /** Todas las filas de la destinación, de todos sus bloques. */
    public List<FilaCaja> filas() {
        return bloques.stream().flatMap(bloque -> bloque.filas().stream()).toList();
    }

    /**
     * Si las tablas llevan encima el nombre de su destinación hija.
     *
     * Se calla en un solo caso: cuando hay un bloque único que se llama igual
     * que la destinación, que es lo que pasa en AMI y en los genéricos —no
     * tienen hijas— y en una destinación de APC cuya única hija es ella
     * misma. Ahí el título sería el de la sección repetido.
     *
     * Con una hija sola pero de otro nombre SÍ se pinta: la sección dice
     * "WHOLESALE" porque es el fichero que va a salir, y que dentro solo haya
     * material de "AUSTRALIA" es justo lo que hay que poder leer.
     */
    public boolean muestraNombreDeHija() {
        if (bloques.isEmpty()) {
            return false;
        }
        return bloques.size() > 1 || !bloques.get(0).nombre().equals(nombre);
    }
}
