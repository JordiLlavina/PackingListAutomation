package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.List;

/**
 * Una línea de la tabla de ajuste: un color y una talla de una referencia,
 * con lo que ha llegado del taller y lo que pide el cliente en cada
 * destinación.
 *
 * Es mutable porque el usuario edita las cantidades objetivo en pantalla: una
 * entrega anterior puede haber cubierto ya parte del pedido, y eso no lo sabe
 * ningún fichero.
 */
public class FilaDigerida {

    private final String color;
    private final String talla;
    private final int recibido;
    private final boolean sinPedido;
    private final List<ObjetivoDestino> objetivos = new ArrayList<>();

    public FilaDigerida(String color, String talla, int recibido,
                        List<ObjetivoDestino> objetivos, boolean sinPedido) {
        this.color = color;
        this.talla = talla;
        this.recibido = recibido;
        this.sinPedido = sinPedido;
        this.objetivos.addAll(objetivos);
    }

    public String getColor() {
        return color;
    }

    public String getTalla() {
        return talla;
    }

    public int getRecibido() {
        return recibido;
    }

    /** La referencia no aparecía en el excel de pedido: la fila va marcada. */
    public boolean isSinPedido() {
        return sinPedido;
    }

    public List<ObjetivoDestino> getObjetivos() {
        return objetivos;
    }

    public int cantidadPara(String destino) {
        return objetivos.stream()
                .filter(objetivo -> objetivo.destino().equals(destino))
                .mapToInt(ObjetivoDestino::cantidad)
                .findFirst()
                .orElse(0);
    }

    /**
     * Corrige a mano la cantidad de una destinación, conservando su número de
     * pedido. Una destinación que no venía del pedido no se puede añadir
     * aquí: sin pedido no habría con qué rellenar el packing list.
     */
    public void corregirCantidad(String destino, int cantidad) {
        for (int i = 0; i < objetivos.size(); i++) {
            if (objetivos.get(i).destino().equals(destino)) {
                objetivos.set(i, new ObjetivoDestino(destino, Math.max(0, cantidad),
                        objetivos.get(i).pedido()));
                return;
            }
        }
    }
}
