package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.puntotres.packinglist.service.etiquetas.AvisoEtiqueta;

/**
 * Prepara los avisos de etiquetas para la pantalla de resultados: los reparte
 * por destinación, hace un resumen corto de cada una y deja el texto largo en
 * el detalle.
 *
 * <p><b>Por qué es de pantalla y no del generador.</b> El generador emite un
 * aviso por caja, y eso es la verdad del envío: si la caja 2 sale sin EAN, es
 * un hecho de la caja 2. Lo que no aporta nada es <i>leer</i> ese mismo aviso
 * nueve veces seguidas —empuja hacia abajo los que sí hay que atender—, así
 * que se junta al pintarlo, igual que AgrupadorFilasRevision compacta filas
 * sin tocar el modelo. El generador y sus tests no se enteran.
 *
 * <p><b>Qué se ve y qué se esconde.</b> A la vista queda el recuento y la
 * consecuencia ("3 cajas · etiqueta sin EAN13 ni EAN128"), que es lo que el
 * usuario busca de un vistazo. El hecho que la causó (el pedido no tiene fila
 * de tal referencia, tal color y tal talla para tal destinación) es largo y
 * solo hace falta cuando se va a arreglar: va al detalle, tras el pliegue.
 *
 * <p>El resumen agrupa por <b>consecuencia</b> y no por hecho a propósito:
 * dos referencias distintas que no están en el pedido son, en resumen, dos
 * cajas sin EAN. El detalle sí las separa, que es donde importa cuál era.
 */
public final class AgrupadorAvisosEtiquetas {

    private AgrupadorAvisosEtiquetas() {
    }

    /**
     * @param generales avisos que no son de ninguna destinación (columnas que
     *                  faltan en el excel de pedido); salen sueltos y arriba
     * @param bloques   una destinación cada uno, en el orden en que aparecieron
     */
    public record AvisosAgrupados(List<LineaAviso> generales, List<BloqueDestino> bloques) {
        public boolean vacio() {
            return generales.isEmpty() && bloques.isEmpty();
        }
    }

    /** Una destinación: lo que se lee siempre (resumen) y lo que se pliega (detalle). */
    public record BloqueDestino(String destino, List<LineaResumen> resumen,
                                List<LineaAviso> detalle) {
        /** Para el rótulo del pliegue: "ver detalle (4)". */
        public int cuantos() {
            return detalle.size();
        }
    }

    /**
     * Una línea del resumen. {@code recuento} va vacío en los avisos de la
     * destinación entera, que no cuentan cajas ni palets.
     */
    public record LineaResumen(String recuento, String consecuencia, boolean palet) {
    }

    /**
     * Una línea del detalle. {@code rotulo} es "Caja 7", "Cajas 1-3, 5",
     * "Palet 2" o vacío.
     */
    public record LineaAviso(String rotulo, String hecho, String consecuencia, boolean palet) {
    }

    public static AvisosAgrupados agrupar(List<AvisoEtiqueta> avisos) {
        List<LineaAviso> generales = new ArrayList<>();
        // Clave = destinación; el LinkedHashMap conserva el orden de aparición,
        // que es el orden en que se generaron los excels.
        Map<String, List<AvisoEtiqueta>> porDestino = new LinkedHashMap<>();

        for (AvisoEtiqueta aviso : avisos) {
            if (aviso.ambito() == AvisoEtiqueta.Ambito.FICHERO) {
                generales.add(new LineaAviso("", aviso.hecho(), aviso.consecuencia(), false));
            } else {
                porDestino.computeIfAbsent(aviso.destino(), d -> new ArrayList<>()).add(aviso);
            }
        }

        List<BloqueDestino> bloques = new ArrayList<>();
        porDestino.forEach((destino, suyos) ->
                bloques.add(new BloqueDestino(destino, resumir(suyos), detallar(suyos))));
        return new AvisosAgrupados(generales, bloques);
    }

    /**
     * Una línea por consecuencia distinta, con cuántas cajas (o palets) la
     * sufren. Cuenta números <b>distintos</b>: una caja con dos problemas
     * aparece en las dos líneas, pero el mismo problema repetido en la misma
     * caja no la cuenta dos veces.
     */
    private static List<LineaResumen> resumir(List<AvisoEtiqueta> avisos) {
        Map<String, TreeSet<Integer>> numerosPorClave = new LinkedHashMap<>();
        Map<String, AvisoEtiqueta> muestraPorClave = new LinkedHashMap<>();
        for (AvisoEtiqueta aviso : avisos) {
            String clave = aviso.ambito() + "|" + resumenDe(aviso);
            muestraPorClave.putIfAbsent(clave, aviso);
            TreeSet<Integer> numeros =
                    numerosPorClave.computeIfAbsent(clave, c -> new TreeSet<>());
            if (aviso.numero() != null) {
                numeros.add(aviso.numero());
            }
        }
        List<LineaResumen> resumen = new ArrayList<>();
        muestraPorClave.forEach((clave, muestra) -> resumen.add(new LineaResumen(
                recuento(muestra.ambito(), numerosPorClave.get(clave).size()),
                resumenDe(muestra), esPalet(muestra))));
        return resumen;
    }

    /**
     * Una línea por hecho distinto, con sus cajas compactadas en rangos. El
     * orden es el de la primera aparición de cada hecho, no el de las cajas:
     * así el bloque se lee en el mismo orden en que se generó.
     */
    private static List<LineaAviso> detallar(List<AvisoEtiqueta> avisos) {
        Map<String, TreeSet<Integer>> numerosPorClave = new LinkedHashMap<>();
        Map<String, AvisoEtiqueta> muestraPorClave = new LinkedHashMap<>();
        for (AvisoEtiqueta aviso : avisos) {
            String clave = aviso.ambito() + "|" + aviso.hecho() + "|" + aviso.consecuencia();
            muestraPorClave.putIfAbsent(clave, aviso);
            TreeSet<Integer> numeros =
                    numerosPorClave.computeIfAbsent(clave, c -> new TreeSet<>());
            if (aviso.numero() != null) {
                numeros.add(aviso.numero());
            }
        }
        List<LineaAviso> detalle = new ArrayList<>();
        muestraPorClave.forEach((clave, muestra) -> detalle.add(new LineaAviso(
                rotulo(muestra.ambito(), numerosPorClave.get(clave)),
                muestra.hecho(), muestra.consecuencia(), esPalet(muestra))));
        return detalle;
    }

    /** Lo que se lee en el resumen: la consecuencia, o el hecho si no la hay. */
    private static String resumenDe(AvisoEtiqueta aviso) {
        return aviso.consecuencia() == null || aviso.consecuencia().isBlank()
                ? aviso.hecho() : aviso.consecuencia();
    }

    private static boolean esPalet(AvisoEtiqueta aviso) {
        return aviso.ambito() == AvisoEtiqueta.Ambito.PALET;
    }

    private static String recuento(AvisoEtiqueta.Ambito ambito, int cuantos) {
        return switch (ambito) {
            case CAJA -> cuantos + (cuantos == 1 ? " caja" : " cajas");
            case PALET -> cuantos + (cuantos == 1 ? " palet" : " palets");
            default -> "";
        };
    }

    /**
     * "Caja 7", "Cajas 1-3, 5", "Palet 2". Los números no consecutivos NO se
     * funden: un rango "1-5" que incluyera cajas sin problema sería mentira, y
     * el usuario iría a mirar cajas que están bien.
     */
    private static String rotulo(AvisoEtiqueta.Ambito ambito, TreeSet<Integer> numeros) {
        if (numeros.isEmpty()) {
            return "";
        }
        String nombre = ambito == AvisoEtiqueta.Ambito.PALET
                ? (numeros.size() == 1 ? "Palet" : "Palets")
                : (numeros.size() == 1 ? "Caja" : "Cajas");
        return nombre + " " + rangos(numeros);
    }

    private static String rangos(TreeSet<Integer> numeros) {
        StringBuilder texto = new StringBuilder();
        Integer inicio = null;
        Integer anterior = null;
        for (int numero : numeros) {
            if (inicio == null) {
                inicio = numero;
            } else if (numero != anterior + 1) {
                anadirTramo(texto, inicio, anterior);
                inicio = numero;
            }
            anterior = numero;
        }
        anadirTramo(texto, inicio, anterior);
        return texto.toString();
    }

    private static void anadirTramo(StringBuilder texto, int inicio, int fin) {
        if (!texto.isEmpty()) {
            texto.append(", ");
        }
        texto.append(inicio == fin ? String.valueOf(inicio) : inicio + "-" + fin);
    }
}
