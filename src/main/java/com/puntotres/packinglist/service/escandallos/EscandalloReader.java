package com.puntotres.packinglist.service.escandallos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Lee un listado de escandallo tal y como lo escupe ICSUITE.
 *
 * Ese fichero es un PDF convertido a excel: columnas de 0,16 de ancho, celdas
 * combinadas a mano y —lo importante— <strong>los valores no caen en la
 * columna de su propio encabezado</strong>: «Article» está en B9 pero los
 * códigos en A10, «Quantitat» en X9 pero las cantidades en W10. Además el
 * número de materiales cambia de un artículo a otro, así que las filas
 * tampoco son fijas.
 *
 * Por eso no se ancla a coordenadas sino a los encabezados: se localiza la
 * fila que contiene «Article» y «Quantitat», se anotan <em>todas</em> las
 * columnas con encabezado de esa fila (también «Preu Ult.», «Imp. Material»…)
 * y cada celda de una fila de datos se asigna al encabezado que tenga más
 * cerca. Los encabezados que no interesan son justamente los que impiden que
 * el precio de la columna AB acabe leyéndose como cantidad.
 */
@Service
public class EscandalloReader {

    private static final String ENCABEZADO_ARTICLE = "ARTICLE";
    private static final String ENCABEZADO_DESCRIPCIO = "DESCRIPCIO";
    private static final String ENCABEZADO_QUANTITAT = "QUANTITAT";
    private static final String ETIQUETA_MODEL = "MODEL";
    private static final String ETIQUETA_COLOR = "COLOR";

    /** Palabras que cierran la tabla de materiales: detrás vienen las fases. */
    private static final List<String> FIN_DE_TABLA = List.of("TOTAL", "FASE");

    public LecturaEscandallo leer(byte[] contenido, String origen) {
        List<String> avisos = new ArrayList<>();
        try (XSSFWorkbook libro = abrir(contenido, origen)) {
            if (libro.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException(prefijo(origen)
                        + "el excel no tiene ninguna hoja");
            }
            Sheet hoja = libro.getSheetAt(0);
            int filaEncabezados = buscarFilaDeEncabezados(hoja, origen);
            Map<Integer, String> anclas = anclasDe(hoja.getRow(filaEncabezados));

            String modelo = valorDeEtiqueta(hoja, filaEncabezados, ETIQUETA_MODEL);
            String descripcion = valorDeEtiqueta(hoja, filaEncabezados, ENCABEZADO_DESCRIPCIO);
            String color = valorDeEtiqueta(hoja, filaEncabezados, ETIQUETA_COLOR);
            avisarSiFalta(avisos, origen, modelo, ETIQUETA_MODEL);
            avisarSiFalta(avisos, origen, descripcion, "DESCRIPCIO");
            avisarSiFalta(avisos, origen, color, ETIQUETA_COLOR);

            List<LineaEscandallo> lineas =
                    leerLineas(hoja, filaEncabezados, anclas, origen, avisos);
            if (lineas.isEmpty()) {
                avisos.add(prefijo(origen) + "el escandallo no tiene ninguna línea de material");
            }
            return new LecturaEscandallo(
                    new Escandallo(modelo, descripcion, color, lineas, origen), avisos);
        } catch (IOException e) {
            throw new IllegalArgumentException(prefijo(origen)
                    + "no se ha podido leer el excel", e);
        }
    }

    // --- Localizar la tabla ---

    /**
     * La fila de encabezados es la única que trae «Article» y «Quantitat» a la
     * vez. Buscarla por las dos palabras y no por una evita confundirla con
     * cualquier otro rótulo suelto del listado.
     */
    private static int buscarFilaDeEncabezados(Sheet hoja, String origen) {
        for (int fila = hoja.getFirstRowNum(); fila <= hoja.getLastRowNum(); fila++) {
            Map<Integer, String> anclas = anclasDe(hoja.getRow(fila));
            if (anclas.containsValue(ENCABEZADO_ARTICLE)
                    && anclas.containsValue(ENCABEZADO_QUANTITAT)) {
                return fila;
            }
        }
        throw new IllegalArgumentException(prefijo(origen)
                + "no se ha encontrado la fila de encabezados «Article … Quantitat»: "
                + "¿seguro que es un escandallo del ERP?");
    }

    /** Columna → encabezado normalizado, para todas las celdas con texto de la fila. */
    private static Map<Integer, String> anclasDe(Row fila) {
        Map<Integer, String> anclas = new LinkedHashMap<>();
        if (fila == null) {
            return anclas;
        }
        for (Cell celda : fila) {
            String texto = normalizar(texto(celda));
            if (!texto.isEmpty()) {
                anclas.put(celda.getColumnIndex(), texto);
            }
        }
        return anclas;
    }

    // --- Bloque de cabecera (MODEL / DESCRIPCIO / COLOR) ---

    /**
     * Busca la etiqueta solo <em>por encima</em> de la tabla —si no, el
     * «Descripcio» del encabezado de la tabla se colaría como descripción del
     * artículo— y devuelve la primera celda con texto a su derecha.
     */
    private static String valorDeEtiqueta(Sheet hoja, int filaEncabezados, String etiqueta) {
        for (int numeroFila = hoja.getFirstRowNum(); numeroFila < filaEncabezados; numeroFila++) {
            Row fila = hoja.getRow(numeroFila);
            if (fila == null) {
                continue;
            }
            for (Cell celda : fila) {
                if (!etiqueta.equals(normalizar(texto(celda)))) {
                    continue;
                }
                String valor = primerTextoALaDerecha(fila, celda.getColumnIndex());
                if (!valor.isEmpty()) {
                    return valor;
                }
            }
        }
        return null;
    }

    private static String primerTextoALaDerecha(Row fila, int columnaEtiqueta) {
        for (Cell celda : fila) {
            if (celda.getColumnIndex() <= columnaEtiqueta) {
                continue;
            }
            String texto = texto(celda);
            if (!texto.isEmpty()) {
                return texto;
            }
        }
        return "";
    }

    // --- Tabla de materiales ---

    private static List<LineaEscandallo> leerLineas(Sheet hoja, int filaEncabezados,
                                                    Map<Integer, String> anclas, String origen,
                                                    List<String> avisos) {
        List<LineaEscandallo> lineas = new ArrayList<>();
        for (int numeroFila = filaEncabezados + 1; numeroFila <= hoja.getLastRowNum();
                numeroFila++) {
            Row fila = hoja.getRow(numeroFila);
            if (fila == null) {
                continue;
            }
            if (cierraLaTabla(fila)) {
                break;
            }
            Map<String, Cell> porEncabezado = repartirPorEncabezado(fila, anclas);
            String article = texto(porEncabezado.get(ENCABEZADO_ARTICLE));
            String descripcion = texto(porEncabezado.get(ENCABEZADO_DESCRIPCIO));
            if (article.isEmpty() && descripcion.isEmpty()) {
                continue;
            }
            lineas.add(new LineaEscandallo(article, descripcion,
                    cantidad(porEncabezado.get(ENCABEZADO_QUANTITAT), article, origen, avisos)));
        }
        return lineas;
    }

    private static boolean cierraLaTabla(Row fila) {
        return anclasDe(fila).values().stream().anyMatch(FIN_DE_TABLA::contains);
    }

    /**
     * El reparto por cercanía: cada celda con contenido va al encabezado cuya
     * columna esté más cerca. Con la geometría del ERP, W (23) cae en
     * «Quantitat» (24) y AB (28) en «Preu Ult.» (29), que es exactamente lo
     * que hace falta. Si dos celdas caen en el mismo encabezado se queda la
     * primera, la de más a la izquierda.
     */
    private static Map<String, Cell> repartirPorEncabezado(Row fila, Map<Integer, String> anclas) {
        Map<String, Cell> reparto = new LinkedHashMap<>();
        for (Cell celda : fila) {
            if (texto(celda).isEmpty()) {
                continue;
            }
            encabezadoMasCercano(anclas, celda.getColumnIndex())
                    .ifPresent(encabezado -> reparto.putIfAbsent(encabezado, celda));
        }
        return reparto;
    }

    private static java.util.Optional<String> encabezadoMasCercano(Map<Integer, String> anclas,
                                                                   int columna) {
        return anclas.entrySet().stream()
                .min(java.util.Comparator.comparingInt(
                        ancla -> Math.abs(ancla.getKey() - columna)))
                .map(Map.Entry::getValue);
    }

    // --- Lectura de celdas ---

    private static Double cantidad(Cell celda, String article, String origen,
                                   List<String> avisos) {
        if (celda == null) {
            avisos.add(prefijo(origen) + "«" + article + "» no trae cantidad, "
                    + "se deja la celda en blanco");
            return null;
        }
        if (celda.getCellType() == CellType.NUMERIC) {
            return celda.getNumericCellValue();
        }
        String texto = texto(celda);
        try {
            return Double.valueOf(aDecimalConPunto(texto));
        } catch (NumberFormatException e) {
            avisos.add(prefijo(origen) + "la cantidad de «" + article + "» no es un número («"
                    + texto + "»), se deja la celda en blanco");
            return null;
        }
    }

    /**
     * El ERP escribe las cantidades con punto decimal («0.505») y los importes
     * con coma («0,0000»), pero no hay garantía de que no cambie: si el texto
     * trae coma y no trae punto, la coma es el decimal; si trae los dos, la
     * coma es separador de miles.
     */
    private static String aDecimalConPunto(String texto) {
        String limpio = texto.replace(" ", "");
        if (limpio.contains(",") && !limpio.contains(".")) {
            return limpio.replace(',', '.');
        }
        return limpio.replace(",", "");
    }

    private static String texto(Cell celda) {
        if (celda == null) {
            return "";
        }
        return switch (celda.getCellType()) {
            // El escandallo del ERP viene lleno de celdas «inlineStr» sin
            // contenido (<c t="inlineStr"/>), y para esas POI devuelve null,
            // no cadena vacía.
            case STRING -> celda.getStringCellValue() == null
                    ? "" : celda.getStringCellValue().trim();
            case NUMERIC -> BigDecimal.valueOf(celda.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(celda.getBooleanCellValue());
            case FORMULA -> textoDeFormula(celda);
            default -> "";
        };
    }

    private static String textoDeFormula(Cell celda) {
        try {
            String valor = celda.getStringCellValue();
            return valor == null ? "" : valor.trim();
        } catch (IllegalStateException e) {
            return BigDecimal.valueOf(celda.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
        }
    }

    /** Mayúsculas y sin acentos: en el mismo fichero conviven DESCRIPCIÓ y DESCRIPCIO. */
    private static String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    // --- Varios ---

    private static XSSFWorkbook abrir(byte[] contenido, String origen) throws IOException {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(contenido));
        } catch (RuntimeException e) {
            // POI lanza NotOfficeXmlFileException y compañía, con mensaje en
            // inglés, cuando el fichero no es un .xlsx. Para quien lo sube es
            // el mismo caso que un excel que no es un escandallo.
            throw new IllegalArgumentException(prefijo(origen)
                    + "no parece un excel .xlsx", e);
        }
    }

    private static void avisarSiFalta(List<String> avisos, String origen, String valor,
                                      String etiqueta) {
        if (valor == null || valor.isBlank()) {
            avisos.add(prefijo(origen) + "no se ha encontrado el «" + etiqueta
                    + "» del artículo, la celda queda en blanco");
        }
    }

    private static String prefijo(String origen) {
        return "«" + origen + "»: ";
    }
}
