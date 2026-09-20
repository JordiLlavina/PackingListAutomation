package com.puntotres.packinglist.service.taller;

/**
 * Un aviso de la entrada por taller, sabiendo de qué referencia habla.
 *
 * La pantalla de ajuste tiene una tarjeta por referencia, y un aviso que
 * habla de una de ellas se lee mucho mejor dentro de su tarjeta que en una
 * lista general de veinte líneas donde hay que ir buscando a cuál toca. Para
 * poder repartirlos hace falta saber de quién es cada uno.
 *
 * <p><b>Por qué la referencia va aparte y no se busca dentro de la frase.</b>
 * El texto ya la nombra, así que sería tentador localizarla con un
 * {@code contains}. Pero eso es deducir la estructura de lo que hay escrito,
 * que es justo lo que este proyecto evita: el día que alguien reescriba una
 * frase, el aviso se caería a la sección general y nadie se enteraría. Con la
 * pieza suelta, quien crea el aviso dice de quién es, y el que no lo sabe
 * deja {@code null} a propósito.
 *
 * <p>Mismo reparto de papeles que {@code AvisoEtiqueta}: esta es la lista que
 * se rellena, y {@code getAvisos()} de quien lo contenga sigue dando las
 * frases de siempre como vista derivada, para quien solo quiera leerlas.
 *
 * @param referencia referencia de la que habla, o null si habla del fichero
 *                   entero (una columna que falta, un pedido que no se ha
 *                   subido): esos van a la sección general de la pantalla
 * @param texto      la frase que lee el usuario
 */
public record AvisoTaller(String referencia, String texto) {

    /** Un aviso del envío o del fichero entero, sin referencia a la que ir. */
    public static AvisoTaller general(String texto) {
        return new AvisoTaller(null, texto);
    }

    /** Un aviso de una referencia concreta, el que baja a su tarjeta. */
    public static AvisoTaller de(String referencia, String texto) {
        return new AvisoTaller(referencia, texto);
    }
}
