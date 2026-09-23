package com.puntotres.packinglist.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Índice en memoria del excel de pedido de la temporada de APC
 * (APC_PEDIDO_FALL26.xlsx), del que salen el número de pedido COMPLETO y la
 * referencia COMPLETA.
 *
 * De las hojas manuscritas llegan los dos datos incompletos: del pedido, sus
 * <b>tres últimos dígitos</b>; de la referencia, lo que el operario escribe
 * ("67043", "F63023"), que es un SUFIJO del Article real del excel
 * ("PXCBS-F67043"). La búsqueda casa por tanto por sufijo en los dos campos a
 * la vez: en el fichero real, Article + tres dígitos identifica una única
 * fila de las 130, mientras que la referencia sola no vale (PXCBC-F63023
 * aparece en 8 pedidos distintos). Una referencia escrita entera casa igual
 * (todo texto es sufijo de sí mismo), y si hay una fila cuyo Article es
 * EXACTAMENTE lo escrito, esa gana sobre las que solo terminan igual.
 *
 * La hoja buena es la primera cuya fila de cabecera trae las TRES columnas
 * {@code Article}, {@code Document d'achat} y {@code Notre référence}. Las
 * tres, no dos: el libro trae otras hojas ("LCT", "Sheet2") con las dos
 * primeras que darían la hoja equivocada. Las columnas se localizan por el
 * texto de su cabecera, nunca por posición, y por prefijo para no depender de
 * acentos ni del tipo de apóstrofo.
 *
 * Como en los escandallos del ERP, hay celdas que POI devuelve como null o
 * vacías: la lectura no asume que haya texto.
 */
public final class ApcPedidoExcel {

    private static final String CABECERA_ARTICULO = "ARTICLE";
    private static final String CABECERA_PEDIDO = "DOCUMENT D";
    private static final String CABECERA_DESTINO = "NOTRE R";
    private static final int DIGITOS_PARCIALES = 3;

    private static final String CABECERA_CANTIDAD = "QUANTIT";

    /**
     * Las tres columnas que solo usa el catálogo del pedido que se le enseña
     * a Claude al leer las hojas manuscritas. La designación se busca por
     * "SIGNATION" y no por prefijo como las demás: "Désignation" lleva el
     * acento en la segunda letra, así que un prefijo sin acento no casa y uno
     * de una sola letra ("D") casaría también con "Document d'achat" y con
     * las media docena de columnas de fecha.
     */
    private static final String CABECERA_DESIGNACION = "SIGNATION";
    private static final String CABECERA_COLOR = "COULEUR";
    private static final String CABECERA_TALLA = "TAILLE";

    /** Una fila del excel: referencia (Article) y pedido completos. */
    public record FilaPedido(String referencia, String pedido) {
    }

    /**
     * Una fila vista desde el catálogo que se le enseña a Claude para que
     * contraste lo que lee en las hojas manuscritas. Trae justo los campos
     * que el operario escribe en la cabecera de artículo y que no hay forma
     * de reparar después: el nombre del modelo ("le neige clou") y el color
     * ("CAB"), que son la separación más difícil de toda la hoja.
     *
     * <b>No trae cantidades a propósito</b>: lo pedido y lo empaquetado
     * pueden diferir de verdad (una entrega parcial), y un descuadre ahí es
     * justo lo que el operario tiene que ver, no algo que el modelo deba
     * cuadrar por su cuenta.
     */
    public record LineaCatalogo(String referencia, String pedido, String destino,
                                String designacion, String color, String talla) {
    }

    /**
     * Lo que hay detrás de un número de pedido: a dónde va y cuántas unidades
     * son en total.
     *
     * En el fichero real un "Document d'achat" es UNA destinación, UN artículo
     * y UN color, con una fila por talla. Por eso la cantidad se suma —las
     * tallas de un mismo bolso van al mismo sitio y al mismo bulto— y la
     * destinación es una sola. Y por eso el código de tres dígitos que escribe
     * el taller basta para saber a dónde va la mercancía, sin cruzar por color
     * (que en el pedido viene como código, "LZZ", y en la hoja del taller como
     * nombre, "CAMEL").
     */
    public record Comanda(String pedido, String destino, int cantidad) {
    }

    /** Filas únicas (referencia + pedido), en el orden del fichero. */
    private final List<FilaPedido> filas;
    private final Map<String, Comanda> comandas;
    private final List<LineaCatalogo> catalogo;
    private final List<String> avisos;

    private ApcPedidoExcel(List<FilaPedido> filas, Map<String, Comanda> comandas,
                           List<LineaCatalogo> catalogo, List<String> avisos) {
        this.filas = List.copyOf(filas);
        this.comandas = Map.copyOf(comandas);
        this.catalogo = List.copyOf(catalogo);
        this.avisos = List.copyOf(avisos);
    }

    public static ApcPedidoExcel desdeBytes(byte[] contenido) throws IOException {
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet hoja = hojaDePedido(libro);
            Row cabecera = hoja.getRow(hoja.getFirstRowNum());
            int colArticulo = columna(cabecera, CABECERA_ARTICULO);
            int colPedido = columna(cabecera, CABECERA_PEDIDO);
            // Solo las usa la entrada por taller. Opcionales para no romper la
            // lectura de un fichero de una temporada anterior que no las traiga.
            int colDestino = columnaOpcional(cabecera, CABECERA_DESTINO);
            int colCantidad = columnaOpcional(cabecera, CABECERA_CANTIDAD);
            // Solo las usa el catálogo que se le enseña a Claude: opcionales
            // por lo mismo, para no romper la lectura de un fichero viejo.
            int colDesignacion = columnaQueContenga(cabecera, CABECERA_DESIGNACION);
            int colColor = columnaOpcional(cabecera, CABECERA_COLOR);
            int colTalla = columnaOpcional(cabecera, CABECERA_TALLA);

            List<FilaPedido> filas = new ArrayList<>();
            List<LineaCatalogo> catalogo = new ArrayList<>();
            Set<FilaPedido> vistas = new LinkedHashSet<>();
            Map<String, String> pedidoPorClave = new LinkedHashMap<>();
            Set<String> ambiguas = new LinkedHashSet<>();
            Map<String, Comanda> comandas = new LinkedHashMap<>();
            Set<String> destinosMezclados = new LinkedHashSet<>();
            for (int fila = hoja.getFirstRowNum() + 1; fila <= hoja.getLastRowNum(); fila++) {
                String referencia = texto(hoja, fila, colArticulo);
                String pedido = texto(hoja, fila, colPedido);
                if (referencia.isBlank() || pedido.isBlank()) {
                    continue;
                }
                FilaPedido entrada = new FilaPedido(referencia.trim(), pedido.trim());
                if (vistas.add(entrada)) {
                    filas.add(entrada);
                }
                catalogo.add(new LineaCatalogo(entrada.referencia(), entrada.pedido(),
                        textoOpcional(hoja, fila, colDestino),
                        textoOpcional(hoja, fila, colDesignacion),
                        textoOpcional(hoja, fila, colColor),
                        textoOpcional(hoja, fila, colTalla)));
                acumularComanda(comandas, destinosMezclados, entrada.pedido(),
                        colDestino < 0 ? "" : texto(hoja, fila, colDestino),
                        colCantidad < 0 ? 0 : entero(texto(hoja, fila, colCantidad)));
                // El aviso de clave ambigua se calcula al cargar, una vez,
                // para que la revisión lo enseñe aunque nadie busque esa clave.
                String clave = clave(referencia, pedido);
                String anterior = pedidoPorClave.put(clave, entrada.pedido());
                if (anterior != null && !anterior.equals(entrada.pedido())) {
                    ambiguas.add(clave);
                }
            }

            List<String> avisos = new ArrayList<>();
            for (String clave : ambiguas) {
                avisos.add("En el excel de pedido, " + clave.replace("|", " + ")
                        + " apunta a más de un pedido: esas líneas se quedan como llegaron");
            }
            for (String pedido : destinosMezclados) {
                avisos.add("En el excel de pedido, el pedido " + pedido + " aparece con más de "
                        + "una destinación: se toma la primera, pero conviene revisarlo");
            }
            if (colCantidad < 0) {
                avisos.add("El excel de pedido no tiene la columna 'Quantité échéancée': "
                        + "las cantidades a enviar hay que teclearlas a mano");
            }
            return new ApcPedidoExcel(filas, comandas, catalogo, avisos);
        }
    }

    /**
     * Filas cuyo Article TERMINA en la referencia escrita y cuyo pedido
     * termina en los tres dígitos. Vacía = no hay fila; una = el dato bueno;
     * varias = ambigua (el mismo modelo con dos prefijos, p. ej.
     * PXBHZ-F65101 y PXCBT-F65101), y entonces quien llama avisa y no toca
     * nada. Si alguna fila casa EXACTA por referencia, las de solo-sufijo se
     * descartan: una referencia completa nunca compite con recortes.
     */
    public List<FilaPedido> filasPara(String referencia, String pedidoParcial) {
        if (referencia == null || referencia.isBlank()
                || pedidoParcial == null || pedidoParcial.isBlank()) {
            return List.of();
        }
        String buscada = referencia.trim().toUpperCase(Locale.ROOT);
        String digitos = sufijo(pedidoParcial);
        List<FilaPedido> exactas = new ArrayList<>();
        List<FilaPedido> porSufijo = new ArrayList<>();
        for (FilaPedido fila : filas) {
            String articulo = fila.referencia().toUpperCase(Locale.ROOT);
            if (!sufijo(fila.pedido()).equals(digitos)) {
                continue;
            }
            if (articulo.equals(buscada)) {
                exactas.add(fila);
            } else if (articulo.endsWith(buscada)) {
                porSufijo.add(fila);
            }
        }
        return exactas.isEmpty() ? porSufijo : exactas;
    }

    /**
     * Número de pedido completo cuando la búsqueda es unívoca, o vacío.
     * Funciona igual si {@code pedidoParcial} ya viene completo: sus tres
     * últimos dígitos encuentran la misma fila.
     */
    public Optional<String> pedidoCompleto(String referencia, String pedidoParcial) {
        List<FilaPedido> candidatas = filasPara(referencia, pedidoParcial);
        return candidatas.size() == 1
                ? Optional.of(candidatas.get(0).pedido())
                : Optional.empty();
    }

    /**
     * La destinación y la cantidad total de un número de pedido, o vacío si
     * ese pedido no está en el fichero. La entrada por taller lo usa para
     * saber a dónde va y cuánto se envía de lo que ha llegado.
     */
    public Optional<Comanda> comandaDe(String pedido) {
        if (pedido == null || pedido.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(comandas.get(pedido.trim()));
    }

    /**
     * Los colores que lleva un número de pedido, en el orden del fichero y ya
     * en mayúsculas.
     *
     * Sirve para comprobar que la fila del taller y la del pedido hablan del
     * mismo artículo: el código de tres dígitos identifica el
     * "Document d'achat", pero si el color no es el suyo es que uno de los dos
     * está mal escrito, y empaquetar por el pedido equivocado manda el bulto a
     * otro sitio con el número de otro.
     *
     * Devuelve <b>varios</b> a propósito: aunque un Document d'achat suele ser
     * un artículo y un color, en el fichero real hay pedidos con dos
     * (4100128685 lleva LZZ y GAU), así que basta con que el color case con
     * uno. Vacío = el fichero no trae columna "Couleur" y no hay nada que
     * comprobar.
     */
    public Set<String> coloresDe(String pedido) {
        if (pedido == null || pedido.isBlank()) {
            return Set.of();
        }
        String buscado = pedido.trim();
        Set<String> colores = new LinkedHashSet<>();
        for (LineaCatalogo linea : catalogo) {
            if (linea.pedido().equals(buscado) && !linea.color().isBlank()) {
                colores.add(linea.color().trim().toUpperCase(Locale.ROOT));
            }
        }
        return colores;
    }

    /** Avisos de nivel de fichero (claves ambiguas). Nunca null. */
    public List<String> avisos() {
        return avisos;
    }

    /**
     * Una línea por fila del fichero (o sea, por talla), en su orden, para
     * montar el catálogo que se le enseña a Claude. Agruparlas es cosa de
     * quien lo formatea: aquí no se decide cómo se lee.
     */
    public List<LineaCatalogo> catalogo() {
        return catalogo;
    }

    /**
     * Suma las unidades de las filas de un mismo pedido —una por talla— y se
     * queda con su destinación. Un pedido con dos destinaciones distintas no
     * debería existir; si aparece, se anota para avisar y se respeta la
     * primera, que es la que ya tiene cantidad acumulada.
     */
    private static void acumularComanda(Map<String, Comanda> comandas, Set<String> mezclados,
                                        String pedido, String destino, int cantidad) {
        String limpio = destino.trim();
        Comanda previa = comandas.get(pedido);
        if (previa == null) {
            comandas.put(pedido, new Comanda(pedido, limpio, cantidad));
            return;
        }
        if (!limpio.isEmpty() && !previa.destino().isEmpty()
                && !previa.destino().equalsIgnoreCase(limpio)) {
            mezclados.add(pedido);
        }
        comandas.put(pedido, new Comanda(pedido,
                previa.destino().isEmpty() ? limpio : previa.destino(),
                previa.cantidad() + cantidad));
    }

    /** Cantidad de una celda; lo que no se entienda cuenta como cero. */
    private static int entero(String crudo) {
        String limpio = crudo.trim();
        if (limpio.isEmpty()) {
            return 0;
        }
        try {
            return new java.math.BigDecimal(limpio).intValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String clave(String referencia, String pedido) {
        return referencia.trim().toUpperCase(Locale.ROOT) + "|" + sufijo(pedido);
    }

    /** Los tres últimos caracteres del pedido, o el pedido entero si es más corto. */
    private static String sufijo(String pedido) {
        String limpio = pedido.trim();
        return limpio.length() <= DIGITOS_PARCIALES
                ? limpio
                : limpio.substring(limpio.length() - DIGITOS_PARCIALES);
    }

    private static Sheet hojaDePedido(Workbook libro) {
        for (int i = 0; i < libro.getNumberOfSheets(); i++) {
            Sheet hoja = libro.getSheetAt(i);
            Row cabecera = hoja.getRow(hoja.getFirstRowNum());
            if (cabecera == null) {
                continue;
            }
            if (columnaOpcional(cabecera, CABECERA_ARTICULO) >= 0
                    && columnaOpcional(cabecera, CABECERA_PEDIDO) >= 0
                    && columnaOpcional(cabecera, CABECERA_DESTINO) >= 0) {
                return hoja;
            }
        }
        throw new IllegalArgumentException("El excel de pedido de APC no tiene ninguna hoja con "
                + "las columnas 'Article', 'Document d'achat' y 'Notre référence': "
                + "¿es el archivo correcto?");
    }

    private static int columna(Row cabecera, String prefijo) {
        int indice = columnaOpcional(cabecera, prefijo);
        if (indice < 0) {
            throw new IllegalArgumentException(
                    "El excel de pedido de APC no tiene la columna '" + prefijo + "'");
        }
        return indice;
    }

    private static int columnaOpcional(Row cabecera, String prefijo) {
        for (Cell celda : cabecera) {
            if (texto(celda).trim().toUpperCase(Locale.ROOT).startsWith(prefijo)) {
                return celda.getColumnIndex();
            }
        }
        return -1;
    }

    /**
     * Como {@link #columnaOpcional} pero por un trozo del centro del título.
     * Es la salida para las cabeceras cuyo acento cae en las primeras letras
     * ("Désignation"), donde un prefijo obligaría a elegir entre depender del
     * acento o quedarse en una sola letra que casa con media hoja.
     */
    private static int columnaQueContenga(Row cabecera, String trozo) {
        for (Cell celda : cabecera) {
            if (texto(celda).trim().toUpperCase(Locale.ROOT).contains(trozo)) {
                return celda.getColumnIndex();
            }
        }
        return -1;
    }

    /** Texto de una celda cuya columna puede no existir (-1 = "" sin leer). */
    private static String textoOpcional(Sheet hoja, int fila, int columna) {
        return columna < 0 ? "" : texto(hoja, fila, columna).trim();
    }

    private static String texto(Sheet hoja, int fila, int columna) {
        Row f = hoja.getRow(fila);
        return f == null ? "" : texto(f.getCell(columna));
    }

    /**
     * Texto de una celda, "" si no hay nada. Los numéricos se leen como
     * enteros: un pedido guardado como número no debe salir "4100128721.0".
     */
    private static String texto(Cell celda) {
        if (celda == null) {
            return "";
        }
        String valor = switch (celda.getCellType()) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> String.valueOf((long) celda.getNumericCellValue());
            case FORMULA -> celda.getCachedFormulaResultType() == CellType.STRING
                    ? celda.getStringCellValue() : "";
            default -> "";
        };
        return valor == null ? "" : valor;
    }
}
