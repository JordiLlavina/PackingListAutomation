package com.puntotres.packinglist.service.corte;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Una ficha del Excel de órdenes de corte: una referencia (modelo + piel) en
 * un color. Se titula con el {@code modelo} a secas, que es como la quiere el
 * cortador, y debajo el {@code nombreModelo} del bolso ("LE NEIGE"), vacío si
 * no se sabe (el pedido de AMI no lo trae); la piel ya la dicen los nombres
 * de debajo. La {@code referencia} entera ("ULL027.AL0103", "PXCBC-F67008")
 * es la que nombra su hoja: el mismo modelo en el mismo color va a menudo en
 * dos pieles. {@code color} es el color del bolso tal como se enseña, código
 * y descripción ("LZZ NOIR"), y las pieles llevan el suyo de ese color.
 * {@code fotos}: las del modelo que van en la ficha, de 0 a 3, en el orden en
 * que se colocan de izquierda a derecha. {@code prensa}: los bolsos de prensa
 * de ese color, en su propia hoja con PRENSA al final del nombre.
 */
public record OrdenCorte(String cliente, String temporada, String referencia, String modelo,
                         String nombreModelo, String color, int bolsos, PielesArticulo pieles,
                         List<Imagen> fotos, boolean prensa) {

    /** Lo más que cabe en la banda de arriba de la ficha. */
    public static final int MAXIMO_FOTOS = 3;

    public OrdenCorte {
        nombreModelo = nombreModelo == null ? "" : nombreModelo.trim();
        fotos = fotos == null ? List.of() : fotos.stream().filter(Objects::nonNull)
                .limit(MAXIMO_FOTOS).toList();
    }

    /** Sin nombre del bolso. */
    public OrdenCorte(String cliente, String temporada, String referencia, String modelo,
                      String color, int bolsos, PielesArticulo pieles, List<Imagen> fotos, boolean prensa) {
        this(cliente, temporada, referencia, modelo, "", color, bolsos, pieles, fotos, prensa);
    }

    /** Sin nombre del bolso y con una foto o ninguna (null). */
    public OrdenCorte(String cliente, String temporada, String referencia, String modelo,
                      String color, int bolsos, PielesArticulo pieles, Imagen foto) {
        this(cliente, temporada, referencia, modelo, "", color, bolsos, pieles, unaOninguna(foto), false);
    }

    private static List<Imagen> unaOninguna(Imagen foto) {
        List<Imagen> fotos = new ArrayList<>();
        if (foto != null) {
            fotos.add(foto);
        }
        return fotos;
    }
}
