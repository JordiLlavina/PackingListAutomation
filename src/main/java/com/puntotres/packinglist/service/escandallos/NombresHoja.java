package com.puntotres.packinglist.service.escandallos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reparte nombres de hoja entre los escandallos de un mismo libro.
 *
 * El nombre es <em>modelo + color</em> y no solo el modelo porque los
 * escandallos del ERP son por modelo <strong>y</strong> color: los dos
 * ejemplos reales (ULL770 NOIR y ULL770 SAND) comparten el mismo MODEL
 * «ULL770.AL245», y Excel no admite dos hojas con el mismo nombre.
 *
 * Es un objeto con estado a propósito: necesita recordar los nombres que ya ha
 * dado para desempatar. Uno por libro generado.
 */
public class NombresHoja {

    /** Límite de Excel para el nombre de una hoja. */
    private static final int MAXIMO = 31;

    /** Caracteres que Excel prohíbe en el nombre de una hoja. */
    private static final String PROHIBIDOS = "[\\\\/?*\\[\\]:]";

    private static final String SIN_NOMBRE = "Escandallo";

    private final Set<String> usados = new LinkedHashSet<>();
    private final List<String> avisos = new ArrayList<>();

    public String para(Escandallo escandallo) {
        String nombre = recortar(sanear(base(escandallo)), MAXIMO);
        if (nombre.isEmpty()) {
            nombre = SIN_NOMBRE;
        }
        String unico = desempatar(nombre);
        if (!unico.equals(nombre)) {
            avisos.add("«" + escandallo.origen() + "»: ya había una hoja llamada «" + nombre
                    + "», esta se ha llamado «" + unico + "»");
        }
        usados.add(unico);
        return unico;
    }

    public List<String> avisos() {
        return List.copyOf(avisos);
    }

    // --- Internos ---

    /**
     * Sin modelo no hay nada del ERP con lo que nombrar la hoja: se tira del
     * nombre del fichero, que es lo que Jordi ha escrito y normalmente ya
     * lleva modelo y color («ULL770 NOIR.xlsx»). En ese caso no se le añade el
     * color, que lo repetiría.
     */
    private static String base(Escandallo escandallo) {
        String modelo = escandallo.modelo() == null ? "" : escandallo.modelo().trim();
        if (modelo.isEmpty()) {
            return sinExtension(escandallo.origen());
        }
        String color = colorAbreviado(escandallo.color());
        return color.isEmpty() ? modelo : modelo + " " + color;
    }

    /**
     * El campo COLOR del ERP viene como «000    0014 NOIR»: códigos internos y
     * al final el color de verdad. Se descartan los grupos puramente
     * numéricos, salvo que no quede nada (entonces el código <em>es</em> el
     * color).
     */
    private static String colorAbreviado(String color) {
        if (color == null || color.isBlank()) {
            return "";
        }
        String[] partes = color.trim().split("\\s+");
        StringBuilder legible = new StringBuilder();
        for (String parte : partes) {
            if (parte.matches("\\d+")) {
                continue;
            }
            legible.append(legible.isEmpty() ? "" : " ").append(parte);
        }
        return legible.isEmpty() ? String.join(" ", partes) : legible.toString();
    }

    private static String sinExtension(String origen) {
        return origen == null ? "" : origen.replaceFirst("(?i)\\.xlsx?$", "");
    }

    private static String sanear(String nombre) {
        return nombre.replaceAll(PROHIBIDOS, " ").replaceAll("\\s+", " ").trim();
    }

    private static String recortar(String nombre, int limite) {
        return nombre.length() <= limite ? nombre : nombre.substring(0, limite).trim();
    }

    /**
     * Ante un choque se prueba « (2)», « (3)»… recortando el nombre lo justo
     * para que el sufijo quepa dentro de los 31 caracteres.
     */
    private String desempatar(String nombre) {
        if (!usados.contains(nombre)) {
            return nombre;
        }
        for (int intento = 2; ; intento++) {
            String sufijo = " (" + intento + ")";
            String candidato = recortar(nombre, MAXIMO - sufijo.length()) + sufijo;
            if (!usados.contains(candidato)) {
                return candidato;
            }
        }
    }
}
