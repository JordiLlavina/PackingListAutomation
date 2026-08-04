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
 * (APC_PEDIDO_FALL26.xlsx), del que sale el número de pedido COMPLETO.
 *
 * Lo que llega de la imagen o del formulario son los <b>tres últimos
 * dígitos</b> del pedido. La clave de búsqueda es por tanto
 * {@code Article + esos tres dígitos}: en el fichero real esa clave
 * identifica una única fila de las 130, mientras que la referencia sola no
 * vale (PXCBC-F63023 aparece en 8 pedidos distintos).
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

    /** clave "REFERENCIA|3 dígitos" -> número de pedido completo. */
    private final Map<String, String> pedidosPorClave;
    private final List<String> avisos;

    private ApcPedidoExcel(Map<String, String> pedidosPorClave, List<String> avisos) {
        this.pedidosPorClave = Map.copyOf(pedidosPorClave);
        this.avisos = List.copyOf(avisos);
    }

    public static ApcPedidoExcel desdeBytes(byte[] contenido) throws IOException {
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet hoja = hojaDePedido(libro);
            Row cabecera = hoja.getRow(hoja.getFirstRowNum());
            int colArticulo = columna(cabecera, CABECERA_ARTICULO);
            int colPedido = columna(cabecera, CABECERA_PEDIDO);

            Map<String, String> pedidos = new LinkedHashMap<>();
            Set<String> ambiguas = new LinkedHashSet<>();
            for (int fila = hoja.getFirstRowNum() + 1; fila <= hoja.getLastRowNum(); fila++) {
                String referencia = texto(hoja, fila, colArticulo);
                String pedido = texto(hoja, fila, colPedido);
                if (referencia.isBlank() || pedido.isBlank()) {
                    continue;
                }
                String clave = clave(referencia, pedido);
                String anterior = pedidos.put(clave, pedido.trim());
                if (anterior != null && !anterior.equals(pedido.trim())) {
                    ambiguas.add(clave);
                }
            }

            List<String> avisos = new ArrayList<>();
            for (String clave : ambiguas) {
                pedidos.remove(clave);
                avisos.add("En el excel de pedido, " + clave.replace("|", " + ")
                        + " apunta a más de un pedido: esas líneas se quedan como llegaron");
            }
            return new ApcPedidoExcel(pedidos, avisos);
        }
    }

    /**
     * Número de pedido completo de una línea, o vacío si no hay fila que case.
     * Funciona igual si {@code pedidoParcial} ya viene completo: sus tres
     * últimos dígitos encuentran la misma fila.
     */
    public Optional<String> pedidoCompleto(String referencia, String pedidoParcial) {
        if (referencia == null || referencia.isBlank()
                || pedidoParcial == null || pedidoParcial.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pedidosPorClave.get(clave(referencia, pedidoParcial)));
    }

    /** Avisos de nivel de fichero (claves ambiguas). Nunca null. */
    public List<String> avisos() {
        return avisos;
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
