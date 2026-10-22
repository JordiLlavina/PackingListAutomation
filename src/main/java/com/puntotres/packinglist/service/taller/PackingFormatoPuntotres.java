package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * El packing list de un taller pasado al formato Puntotres: lo que devuelve
 * Claude al convertir un excel de taller con formato propio (el de Fez, el de
 * Tánger...), lo que {@link AjustadorConversion} completa después contra el
 * pedido del cliente, y lo que {@link FormatoPuntotresExcelBuilder} escribe en
 * la hoja "LISTE DE COLIS" que ya sabe leer {@link TallerColisExcel}.
 *
 * Es un calco del JSON de la respuesta, frágil a propósito como
 * {@code EnvioInput}: nadie más que la conversión lo conoce, y lo que sale de
 * aquí es un excel que pasa por la digestión de siempre. El esquema con el
 * que se le pide a Claude ({@link ConversionFormatoTallerService#ESQUEMA})
 * tiene que seguir a este record: un campo nuevo aquí sin su línea en el
 * esquema llega siempre a null.
 *
 * {@code notas} es todo lo que el taller apunta FUERA de la tabla —cajas con
 * defectos, bolsas que faltan, totales—, fila a fila y celda a celda, tal
 * cual. Se copia debajo de la tabla para que el usuario no lo pierda, pero el
 * programa no lo lee nunca: el lector se para antes de llegar.
 *
 * {@code resumen} son los totales que la hoja del taller declara, tal como
 * los lee el modelo; Java los compara con la suma de las filas convertidas.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PackingFormatoPuntotres(
        String hoja,
        List<String> hojasNoConvertidas,
        Cabecera cabecera,
        List<Fila> filas,
        List<List<String>> notas,
        Resumen resumen,
        List<Aviso> avisos) {

    public PackingFormatoPuntotres {
        hojasNoConvertidas = hojasNoConvertidas == null ? List.of()
                : hojasNoConvertidas.stream().filter(h -> h != null && !h.isBlank()).toList();
        cabecera = cabecera == null ? new Cabecera(null, null, null, null) : cabecera;
        filas = filas == null ? List.of() : List.copyOf(filas);
        notas = notas == null ? List.of()
                : notas.stream().map(fila -> fila == null ? List.<String>of()
                        : fila.stream().map(celda -> celda == null ? "" : celda).toList())
                        .toList();
        resumen = resumen == null ? new Resumen(null, null) : resumen;
        avisos = avisos == null ? List.of()
                : avisos.stream().filter(a -> a != null && a.texto() != null
                        && !a.texto().isBlank()).toList();
    }

    /** El membrete del taller, que va encima de la tabla como en el formato Puntotres. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cabecera(String taller, String direccion, String factura, String fecha) {
    }

    /** Los totales que declara la hoja del taller, si los declara. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resumen(Integer unidadesTotales, Integer cajasTotales) {
    }

    /**
     * Un aviso: a qué fila convertida se refiere ({@code fila} empieza en 1 y
     * es la posición en {@link #filas}; null = habla del fichero entero), qué
     * pasa, y si pide que alguien lo mire ({@code revisar}) o solo deja
     * constancia de algo que se ha completado de forma unívoca desde el
     * pedido (informativo).
     *
     * La distinción es de pantalla y de excel: lo que pide revisión va en
     * amarillo y a la vista; lo informativo va solo como comentario en la
     * celda y plegado en la lista. Sin ella, veintiséis «referencia
     * completada» enterraban los tres avisos que había que leer. Lo que
     * devuelve el modelo es siempre para revisar (una duda suya); por eso
     * {@code revisar} sin valor cuenta como verdadero.
     */
    /**
     * Las columnas de la tabla Puntotres, en su orden, nombradas como los
     * campos de {@link Fila}: es el nombre que el modelo pone en
     * {@code Aviso.columna} y el que el excel traduce a una celda.
     */
    public enum Columna {
        ENVIO("envio"), CLIENTE("cliente"), MOTIVO("motivo"), REFERENCIA("referencia"),
        COLOR("color"), TALLA("talla"), DESTINACION("destinacion"), CODIGO_CLIENTE("codigoCliente"),
        UNIDADES_POR_CAJA("unidadesPorCaja"), CANTIDAD("cantidad"), CAJAS("cajas"),
        PESO_BRUTO_CAJA("pesoBrutoCaja"), MEDIDA_CAJA("medidaCaja");

        private final String campo;

        Columna(String campo) {
            this.campo = campo;
        }

        public String campo() {
            return campo;
        }

        /** Posición en la tabla, de 0 a 12. */
        public int indice() {
            return ordinal();
        }

        /** La columna de un nombre de campo; REFERENCIA si no es ninguno o está vacío. */
        public static Columna de(String campo) {
            for (Columna columna : values()) {
                if (columna.campo.equalsIgnoreCase(campo == null ? "" : campo.trim())) {
                    return columna;
                }
            }
            return REFERENCIA;
        }
    }

    /**
     * Un aviso: a qué fila convertida se refiere ({@code fila} empieza en 1 y
     * es la posición en {@link #filas}; null = habla del fichero entero), en
     * qué {@code columna} (el campo de la fila con la duda; vacío = no se
     * sabe, y entonces va a la referencia), qué pasa, y si pide que alguien
     * lo mire ({@code revisar}) o solo deja constancia de algo completado de
     * forma unívoca desde el pedido (informativo).
     *
     * La columna manda dónde se pinta: el amarillo y el comentario van en
     * ESA celda y no en la fila entera, por decisión del usuario — una fila
     * entera amarilla no dice qué dato es el dudoso.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Aviso(Integer fila, String columna, String texto, Boolean revisar,
                        String consecuencia) {

        /** Lo que se lee a la vista de un aviso del modelo: todos son dudas suyas. */
        public static final String DUDA_DE_LECTURA = "duda de lectura de la IA";

        public Aviso {
            columna = columna == null ? "" : columna.trim();
            revisar = revisar == null || revisar;
            consecuencia = consecuencia == null || consecuencia.isBlank() ? DUDA_DE_LECTURA
                    : consecuencia;
        }

        public static Aviso general(String texto) {
            return new Aviso(null, null, texto, true, null);
        }

        /**
         * @param consecuencia lo que queda a la vista en el resumen plegado
         *                     ("varios pedidos posibles: el código va a mano");
         *                     el texto largo con el hecho se pliega
         */
        public static Aviso deFila(int fila, Columna columna, String consecuencia, String texto) {
            return new Aviso(fila, columna.campo(), texto, true, consecuencia);
        }

        public static Aviso informativo(int fila, Columna columna, String texto) {
            return new Aviso(fila, columna.campo(), texto, false, null);
        }

        /** La celda de la fila a la que se refiere. */
        public Columna celda() {
            return Columna.de(columna);
        }

        /** La frase con la fila delante, para listas que no enseñan el excel. */
        public String frase() {
            return fila == null ? texto : "Fila " + fila + ": " + texto;
        }
    }

    /**
     * Una línea de la tabla: un grupo de cajas seguidas con el mismo artículo,
     * las mismas unidades por caja y la misma destinación. Los números son
     * objetos y no primitivos: {@code null} es "el taller no lo dice", que el
     * excel deja en blanco, y no un cero que alguien daría por bueno.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fila(
            String envio,
            String cliente,
            String motivo,
            String referencia,
            String color,
            String talla,
            String destinacion,
            String codigoCliente,
            Integer unidadesPorCaja,
            Integer cantidad,
            String cajas,
            Double pesoBrutoCaja,
            String medidaCaja) {

        public Fila conReferencia(String nueva) {
            return new Fila(envio, cliente, motivo, nueva, color, talla, destinacion, codigoCliente,
                    unidadesPorCaja, cantidad, cajas, pesoBrutoCaja, medidaCaja);
        }

        public Fila conColor(String nuevo) {
            return new Fila(envio, cliente, motivo, referencia, nuevo, talla, destinacion,
                    codigoCliente, unidadesPorCaja, cantidad, cajas, pesoBrutoCaja, medidaCaja);
        }

        public Fila conCodigoCliente(String nuevo) {
            return new Fila(envio, cliente, motivo, referencia, color, talla, destinacion, nuevo,
                    unidadesPorCaja, cantidad, cajas, pesoBrutoCaja, medidaCaja);
        }

        /**
         * Los números de caja que nombra {@code cajas}: "1-3" son 1, 2 y 3;
         * "61" uno; vacío ninguno. Lo que no se entiende no cuenta, que es lo
         * que hace que un descuadre salga en el aviso de totales en vez de
         * pasar en silencio.
         */
        public Set<Integer> numerosDeCaja() {
            Set<Integer> numeros = new LinkedHashSet<>();
            if (cajas == null || cajas.isBlank()) {
                return numeros;
            }
            for (String tramo : cajas.split("[,;]")) {
                String limpio = tramo.trim();
                if (limpio.matches("\\d+")) {
                    numeros.add(Integer.parseInt(limpio));
                } else if (limpio.matches("\\d+\\s*-\\s*\\d+")) {
                    String[] extremos = limpio.split("-");
                    int desde = Integer.parseInt(extremos[0].trim());
                    int hasta = Integer.parseInt(extremos[1].trim());
                    for (int n = desde; n <= hasta; n++) {
                        numeros.add(n);
                    }
                }
            }
            return numeros;
        }

        public int numeroDeCajas() {
            return numerosDeCaja().size();
        }
    }

    public PackingFormatoPuntotres conFilas(List<Fila> nuevas) {
        return new PackingFormatoPuntotres(hoja, hojasNoConvertidas, cabecera, nuevas, notas,
                resumen, avisos);
    }

    public PackingFormatoPuntotres conAvisosAnadidos(List<Aviso> mas) {
        List<Aviso> todos = new ArrayList<>(avisos);
        todos.addAll(mas);
        return new PackingFormatoPuntotres(hoja, hojasNoConvertidas, cabecera, filas, notas,
                resumen, todos);
    }

    /** La suma de "cantidad" de las filas, para contrastarla con el resumen de la hoja. */
    public int unidadesConvertidas() {
        return filas.stream().mapToInt(f -> f.cantidad() == null ? 0 : f.cantidad()).sum();
    }

    /**
     * Cuántas cajas físicas DISTINTAS nombran las filas. Una caja mixta (la
     * misma caja en dos líneas, que el taller escribe combinando la celda del
     * número) cuenta una vez, o el contraste con el total de la hoja saltaría
     * en todos los envíos con una caja compartida.
     */
    public int cajasConvertidas() {
        Set<Integer> numeros = new LinkedHashSet<>();
        filas.forEach(fila -> numeros.addAll(fila.numerosDeCaja()));
        return numeros.size();
    }

    /** Los avisos que piden que alguien los mire. */
    public List<Aviso> avisosParaRevisar() {
        return avisos.stream().filter(Aviso::revisar).toList();
    }

    /** Los que solo dejan constancia de lo completado desde el pedido. */
    public List<Aviso> avisosInformativos() {
        return avisos.stream().filter(aviso -> !aviso.revisar()).toList();
    }
}
