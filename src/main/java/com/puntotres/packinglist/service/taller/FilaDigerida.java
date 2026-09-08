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
 *
 * Un artículo es UNA fila. El taller puede escribirlo en varias —una por
 * destinación suya, o dos entregas del mismo color— y todas se fusionan aquí
 * con {@link #fusionar}: son las mismas unidades y comparten pedido, cartón y
 * unidades por caja. Verlas separadas hacía que cada una leyera el pedido
 * entero y el objetivo del envío saliera multiplicado por el número de filas.
 */
public class FilaDigerida {

    private final String color;
    private final String talla;
    private int recibido;
    private boolean sinPedido;
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

    /** Lo que se reparte entre todas las destinaciones. */
    public int totalObjetivo() {
        return objetivos.stream().mapToInt(ObjetivoDestino::cantidad).sum();
    }

    /**
     * Se está mandando más género del que ha llegado del taller.
     *
     * No se puede empaquetar lo que no está en el almacén, y el reparto no lo
     * arregla: recorta por prioridad y sirve de menos a las destinaciones de
     * abajo, así que el envío saldría plausible y equivocado.
     */
    public boolean repartoImposible() {
        return totalObjetivo() > recibido;
    }

    /**
     * Se traga otra fila del taller del mismo artículo.
     *
     * Lo <b>recibido sí se suma</b> —son unidades físicas que han llegado en
     * dos apuntes—, pero los <b>objetivos de una destinación no</b>: la
     * cantidad objetivo la dice el pedido del cliente, que habla de la
     * referencia y la destinación, no de cuántas veces la haya escrito el
     * taller. Sumarlos pediría el doble de lo pedido. Las destinaciones que
     * la otra fila trae y esta no, se añaden: es el caso del cliente sin
     * excel de pedido, donde cada fila del taller lleva su propia
     * destinación.
     *
     * Una destinación repetida con cantidad cero sí cede: es lo que vale una
     * sugerencia (en AMI, el PO que se copia de una fila hermana), y dejarla
     * ganar borraría una cantidad de verdad.
     */
    public void fusionar(int masRecibido, List<ObjetivoDestino> masObjetivos,
                         boolean otraSinPedido) {
        recibido += masRecibido;
        // Sin pedido solo si NINGUNA de las filas fusionadas lo tenía: basta
        // que una casara para que la fila ya no haya que mirarla a mano.
        sinPedido = sinPedido && otraSinPedido;
        for (ObjetivoDestino otro : masObjetivos) {
            int ya = indiceDe(otro.destino());
            if (ya < 0) {
                objetivos.add(otro);
            } else if (objetivos.get(ya).cantidad() == 0 && otro.cantidad() > 0) {
                objetivos.set(ya, otro);
            }
        }
    }

    private int indiceDe(String destino) {
        for (int i = 0; i < objetivos.size(); i++) {
            if (objetivos.get(i).destino().equals(destino)) {
                return i;
            }
        }
        return -1;
    }

    public int cantidadPara(String destino) {
        return objetivos.stream()
                .filter(objetivo -> objetivo.destino().equals(destino))
                .mapToInt(ObjetivoDestino::cantidad)
                .findFirst()
                .orElse(0);
    }

    /**
     * El número de pedido con el que va esa destinación, o cadena vacía si no
     * se sabe. Sale del excel de pedido del cliente —el PO de AMI, el
     * {@code Document d'achat} de APC— y se puede corregir a mano.
     */
    public String pedidoPara(String destino) {
        return objetivos.stream()
                .filter(objetivo -> objetivo.destino().equals(destino))
                .map(ObjetivoDestino::pedido)
                .filter(pedido -> pedido != null)
                .findFirst()
                .orElse("");
    }

    public void corregirCantidad(String destino, int cantidad) {
        corregirObjetivo(destino, cantidad, null);
    }

    /**
     * Corrige a mano lo que se envía a una destinación: cuánto y con qué
     * número de pedido.
     *
     * Un campo vacío significa "no tocar", igual que en la pantalla de
     * revisión, así que se puede corregir solo el pedido sin volver a teclear
     * la cantidad y al revés.
     *
     * Si esa destinación no venía del excel de pedido, se AÑADE. Antes se
     * ignoraba en silencio, y una referencia que el pedido no reconocía —una
     * muestra, un color nuevo, un artículo que llegó con otro nombre— se
     * quedaba sin ninguna casilla donde escribir cuánto se envía: la fila se
     * veía en pantalla, aceptaba lo tecleado y no lo aplicaba.
     */
    public void corregirObjetivo(String destino, Integer cantidad, String pedido) {
        for (int i = 0; i < objetivos.size(); i++) {
            ObjetivoDestino actual = objetivos.get(i);
            if (actual.destino().equals(destino)) {
                objetivos.set(i, new ObjetivoDestino(destino,
                        cantidad == null ? actual.cantidad() : Math.max(0, cantidad),
                        enBlanco(pedido) ? actual.pedido() : pedido.trim()));
                return;
            }
        }
        // Un cero solo no crea destinación: es el valor con el que llega una
        // casilla vacía, y acabaría dándole a todas las filas todas las
        // columnas. Un pedido tecleado sí, aunque todavía no haya cantidad:
        // perderlo al recargar sería el mismo fallo silencioso de antes.
        if ((cantidad != null && cantidad > 0) || !enBlanco(pedido)) {
            objetivos.add(new ObjetivoDestino(destino,
                    cantidad == null ? 0 : Math.max(0, cantidad),
                    enBlanco(pedido) ? null : pedido.trim()));
        }
    }

    private static boolean enBlanco(String texto) {
        return texto == null || texto.isBlank();
    }
}
