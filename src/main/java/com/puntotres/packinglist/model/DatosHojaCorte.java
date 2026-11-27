package com.puntotres.packinglist.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Una «Hoja de corte» de un modelo tal como se imprimió: la cabecera del
 * modelo y una línea por color, cada una con su cantidad y sus pieles (1
 * principal, 1 forro y hasta 4 combinaciones, cada una con su color). Es lo
 * que se guarda entero en JSON en {@code hoja_corte.datos}.
 *
 * {@code cliente} es la clave (AMI) y {@code nombreCliente} el que se
 * imprime (A.P.C.). {@code codigoModelo}: el del artículo elegido en el
 * catálogo, con el que se busca la foto; vacío si la hoja se hizo desde cero.
 */
public record DatosHojaCorte(String cliente, String nombreCliente, String temporada, String referencia,
                             String modelo, String nombreBolso, String codigoModelo, LocalDate fecha,
                             List<Linea> lineas) {

    public DatosHojaCorte {
        cliente = limpio(cliente);
        nombreCliente = limpio(nombreCliente);
        temporada = limpio(temporada);
        referencia = limpio(referencia);
        modelo = limpio(modelo);
        nombreBolso = limpio(nombreBolso);
        codigoModelo = limpio(codigoModelo);
        lineas = lineas == null ? List.of() : List.copyOf(lineas);
    }

    /** El modelo con el que se busca la foto en el catálogo: el código elegido o, si no, el tecleado. */
    public String modeloParaFoto() {
        return codigoModelo.isEmpty() ? modelo : codigoModelo;
    }

    /** Las líneas que se imprimen: las que tienen cantidad. */
    public List<Linea> lineasConCantidad() {
        return lineas.stream().filter(linea -> !linea.estaVacia()).toList();
    }

    public record Linea(String codigoColor, String descripcionColor, Integer cantidad, Piel principal, Piel forro,
                        List<Piel> combinaciones) {

        public Linea {
            codigoColor = limpio(codigoColor);
            descripcionColor = limpio(descripcionColor);
            principal = principal == null ? Piel.NINGUNA : principal;
            forro = forro == null ? Piel.NINGUNA : forro;
            combinaciones = combinaciones == null ? List.of() : combinaciones.stream()
                    .filter(c -> c != null && !c.nombre().isEmpty()).limit(4).toList();
        }

        /** Sin cantidad no hay nada que cortar: la línea no sale (aunque tenga pieles puestas por defecto). */
        public boolean estaVacia() {
            return cantidad == null || cantidad <= 0;
        }

        /** «LZZ NOIR», o lo que haya de los dos. */
        public String color() {
            if (descripcionColor.isEmpty()) {
                return codigoColor;
            }
            return codigoColor.isEmpty() ? descripcionColor : codigoColor + " " + descripcionColor;
        }
    }

    public record Piel(String nombre, String color) {

        public static final Piel NINGUNA = new Piel("", "");

        public Piel {
            nombre = limpio(nombre);
            color = limpio(color);
        }
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
