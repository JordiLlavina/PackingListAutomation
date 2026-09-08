package com.puntotres.packinglist.service.etiquetas;

/**
 * Un aviso de etiquetas con sus piezas por separado, en vez de una frase ya
 * montada:
 *
 * <pre>
 * CHINA: Caja 4. Lleva varias tallas. Se generan códigos de barra aparte
 * JAPAN: Palet 2 con cajas sin peso. Etiqueta de palet sin peso
 * IVRY: sin palets. La hoja de etiquetas de palet sale en blanco
 * </pre>
 *
 * La destinación va delante porque es como el usuario mira la salida (un
 * excel por destinación) y porque deja el resto en frases cortas en vez de
 * una subordinada larga. Nada de vocabulario técnico: quien lee esto habla de
 * "la entrada", no del JSON ni de las fotos de las que salió.
 *
 * <p><b>Por qué las piezas van sueltas.</b> La pantalla de resultados
 * necesita cada una por su cuenta: resalta la <b>consecuencia</b> —que es lo
 * que el usuario busca de un vistazo, "sin EAN13 ni EAN128"—, agrupa por
 * <b>destinación</b>, compacta <b>números</b> de caja consecutivos en un
 * rango y tiñe los de <b>palet</b> aparte. Con la frase ya montada eso solo
 * se recuperaría partiendo el texto, y el texto no es partible con
 * seguridad: unos avisos separan hecho y consecuencia con '.', otros con ';'
 * y los de {@code AmiPedidoExcel.FilaPedido.avisosEan} con ':'. Deducir la
 * estructura de lo que hay escrito es justo lo que este proyecto evita.
 *
 * <p>{@link #texto()} vuelve a montar la frase para quien solo quiera leerla
 * (los tests, la consola). Junta siempre con ". ", así que la frase puede
 * variar en ese signo respecto a como se tecleó; el contenido no.
 *
 * <p>Los dos generadores lo comparten a propósito: el formato de un aviso es
 * una sola decisión, no una por cliente.
 *
 * @param destino      destinación a la que pertenece, o null si es del fichero entero
 * @param ambito       a qué se refiere: una caja, un palet, la destinación o el fichero
 * @param numero       nº de caja o de palet; null en los otros dos ámbitos
 * @param hecho        qué ha pasado
 * @param consecuencia qué implica para la etiqueta; puede faltar
 */
public record AvisoEtiqueta(String destino, Ambito ambito, Integer numero,
                            String hecho, String consecuencia) {

    /** A qué se refiere el aviso. Gobierna cómo se rotula y cómo se agrupa. */
    public enum Ambito { CAJA, PALET, DESTINO, FICHERO }

    public static AvisoEtiqueta deCaja(String destino, int numeroCaja,
                                       String hecho, String consecuencia) {
        return new AvisoEtiqueta(destino, Ambito.CAJA, numeroCaja, hecho, consecuencia);
    }

    public static AvisoEtiqueta dePalet(String destino, int numeroPalet,
                                        String hecho, String consecuencia) {
        return new AvisoEtiqueta(destino, Ambito.PALET, numeroPalet, hecho, consecuencia);
    }

    public static AvisoEtiqueta deDestino(String destino, String hecho, String consecuencia) {
        return new AvisoEtiqueta(destino, Ambito.DESTINO, null, hecho, consecuencia);
    }

    /** Aviso del fichero entero (columnas que faltan en el excel de pedido). */
    public static AvisoEtiqueta deFichero(String hecho, String consecuencia) {
        return new AvisoEtiqueta(null, Ambito.FICHERO, null, hecho, consecuencia);
    }

    /**
     * El mismo hecho, ya sabiendo en qué caja salió. Lo usan los avisos que
     * nacen en el lector del excel de pedido, que conoce el problema pero no
     * la caja en la que acabará notándose.
     */
    public AvisoEtiqueta enCaja(String destino, int numeroCaja) {
        return deCaja(destino, numeroCaja, hecho, consecuencia);
    }

    /** La frase entera, como se ha leído siempre en la pantalla de resultados. */
    public String texto() {
        String frase = consecuencia == null || consecuencia.isBlank()
                ? hecho : hecho + ". " + consecuencia;
        return switch (ambito) {
            case CAJA -> destino + ": Caja " + numero + ". " + frase;
            case PALET -> destino + ": Palet " + numero + " " + frase;
            case DESTINO -> destino + ": " + frase;
            case FICHERO -> frase;
        };
    }
}
