package com.puntotres.packinglist.service.corte;

/**
 * Un color de una referencia y cuántos bolsos de él hay que cortar.
 *
 * El color es la pareja código + descripción ("LZZ NOIR", "001 BLACK"): el
 * código es lo que identifica el color —con él se suma el pedido y se buscan
 * los colores de piel recordados— y la descripción es lo que lee el cortador.
 * AMI trae las dos cosas en su pedido; APC solo el código, y la descripción
 * sale de la Tabla colores Clientes. Sin descripción, el color es el código a
 * secas.
 */
public record ColorCorte(String codigo, String descripcion, int bolsos) {

    public ColorCorte {
        codigo = limpio(codigo);
        descripcion = limpio(descripcion);
    }

    /** Lo que se enseña: "LZZ NOIR", o "LZZ" si el color no tiene descripción. */
    public String nombre() {
        if (descripcion.isEmpty()) {
            return codigo;
        }
        return codigo.isEmpty() ? descripcion : codigo + " " + descripcion;
    }

    public ColorCorte conBolsos(int otros) {
        return new ColorCorte(codigo, descripcion, otros);
    }

    public ColorCorte conDescripcion(String otra) {
        return new ColorCorte(codigo, otra, bolsos);
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
