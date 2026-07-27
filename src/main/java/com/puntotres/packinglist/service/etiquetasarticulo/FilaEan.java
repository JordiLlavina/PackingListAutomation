package com.puntotres.packinglist.service.etiquetasarticulo;

/**
 * Una fila del excel de pedido del cliente, tal cual: calco literal, sin
 * interpretar. Quién es bolso y quién cinturón, cómo se agrupan y cómo se
 * nombran las hojas es cosa del generador del cliente.
 *
 * poNumerico: la parte numérica del PO con padding a 5 dígitos ("07704").
 * poSufijo: el sufijo de destinación ("CH", "JP") o null (France).
 * ean13: tal como viene, sin validar; "" si la celda estaba vacía.
 */
public record FilaEan(String madeIn, String article, String coloris, String libelle,
                      String taille, String poNumerico, String poSufijo, String ean13) {

    /** "07704CH" / "07714": el PO tal como aparece en el nombre de hoja. */
    public String poCompacto() {
        return poSufijo == null ? poNumerico : poNumerico + poSufijo;
    }

    /** "A236 TRUFFLE", o solo el código si la fila no trae libellé. */
    public String colorCompleto() {
        return libelle.isBlank() ? coloris : coloris + " " + libelle;
    }
}
