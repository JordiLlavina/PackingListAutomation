package com.puntotres.packinglist.config;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Las dos listas cerradas de la biblioteca de pieles, de application.yml:
 *
 * <pre>
 * packing-list:
 *   biblioteca-pieles:
 *     animales: [Vaca, Cabra]
 *     tipos: [Napa, Serraje, ...]
 * </pre>
 *
 * Son listas cerradas por decisión del usuario: en la pantalla se elige, no
 * se teclea, y el día que un agente lea fichas técnicas tendrá que elegir de
 * aquí. Los animales conservan el orden del yml, porque el primero es el que
 * sale elegido al dar de alta; los tipos se devuelven en orden alfabético.
 */
@ConfigurationProperties(prefix = "packing-list.biblioteca-pieles")
public class BibliotecaPielesProperties {

    private List<String> animales = new ArrayList<>();
    private List<String> tipos = new ArrayList<>();

    public List<String> getAnimales() {
        return animales;
    }

    public void setAnimales(List<String> animales) {
        this.animales = limpia(animales);
    }

    /** Los tipos por orden alfabético, sin mirar mayúsculas ni acentos. */
    public List<String> getTipos() {
        return tipos;
    }

    public void setTipos(List<String> tipos) {
        List<String> ordenados = limpia(tipos);
        Collator alfabetico = Collator.getInstance(Locale.forLanguageTag("es"));
        alfabetico.setStrength(Collator.PRIMARY);
        ordenados.sort(alfabetico);
        this.tipos = ordenados;
    }

    /** El animal de una piel nueva: el primero de la lista, o vacío si no hay lista. */
    public String animalPorDefecto() {
        return animales.isEmpty() ? "" : animales.get(0);
    }

    /**
     * El valor de la lista que es {@code texto} sin mirar mayúsculas ni
     * espacios de sobra ("vaca" → "Vaca"), para que se guarde siempre escrito
     * igual; si no está en la lista, el texto recortado tal cual.
     */
    public String animal(String texto) {
        return deLaLista(animales, texto);
    }

    /** Como {@link #animal(String)}, con la lista de tipos. */
    public String tipo(String texto) {
        return deLaLista(tipos, texto);
    }

    private static String deLaLista(List<String> lista, String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String buscado = texto.trim();
        return lista.stream().filter(valor -> valor.equalsIgnoreCase(buscado)).findFirst().orElse(buscado);
    }

    private static List<String> limpia(List<String> valores) {
        List<String> limpios = new ArrayList<>();
        if (valores != null) {
            for (String valor : valores) {
                if (valor != null && !valor.isBlank() && !limpios.contains(valor.trim())) {
                    limpios.add(valor.trim());
                }
            }
        }
        return limpios;
    }
}
