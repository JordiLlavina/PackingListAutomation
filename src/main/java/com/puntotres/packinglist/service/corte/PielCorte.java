package com.puntotres.packinglist.service.corte;

/**
 * Una piel de una orden de corte: su nombre, que es del material y vale para
 * todos los colores del bolso, y su color, que es de ESTE color del bolso
 * (la piel principal de un bolso negro es negra; la de uno crudo, cruda). Los
 * dos se teclean en la pantalla de pieles; ninguno sale del pedido.
 */
public record PielCorte(String nombre, String color) {

    public static final PielCorte NINGUNA = new PielCorte("", "");

    public PielCorte {
        nombre = limpio(nombre);
        color = limpio(color);
    }

    /** Una piel sin nombre no existe: un forro o una combinación vacíos no salen. */
    public boolean existe() {
        return !nombre.isEmpty();
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
