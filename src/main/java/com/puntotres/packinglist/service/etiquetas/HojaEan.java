package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Acceso de bajo nivel a la hoja "EAN ..." del excel de pedido de un
 * cliente, compartido por todo lo que la lee (etiquetas de caja y etiquetas
 * de artículo).
 *
 * La hoja buena es la primera cuyo nombre empieza por "EAN" (la temporada
 * cambia: EAN H26, EAN E27...); el libro trae más hojas (bolsitas, copias
 * por artículo) que se ignoran. Las columnas se localizan por el texto de la
 * cabecera de la primera fila, no por posición, porque el cliente reordena
 * columnas entre temporadas.
 *
 * Es AutoCloseable porque mantiene abierto el Workbook de POI: usar siempre
 * dentro de un try-with-resources y no dejar escapar nada que dependa de él.
 */
public final class HojaEan implements AutoCloseable {

    private final Workbook libro;
    private final Sheet hoja;
    private final Row cabecera;

    private HojaEan(Workbook libro, Sheet hoja) {
        this.libro = libro;
        this.hoja = hoja;
        this.cabecera = hoja.getRow(hoja.getFirstRowNum());
        if (cabecera == null) {
            throw new IllegalArgumentException(
                    "La hoja '" + hoja.getSheetName() + "' del excel de pedido está vacía");
        }
    }

    public static HojaEan abrir(byte[] contenido) throws IOException {
        Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido));
        try {
            return new HojaEan(libro, primeraHojaEan(libro));
        } catch (RuntimeException e) {
            libro.close();
            throw e;
        }
    }

    /** Nombre de la hoja, ej. "EAN H26": de ahí sale la temporada. */
    public String nombre() {
        return hoja.getSheetName().trim();
    }

    /**
     * Índice de la primera columna cuya cabecera empieza por el título dado.
     * Se compara sin mayúsculas, espacios, puntos ni acentos: "PO" casa con
     * "P.O." y "EAN13" con "EAN 13 ". Lanza si no está: sin ella no se puede
     * hacer nada útil con el fichero, así que aquí sí se bloquea.
     */
    public int columna(String titulo) {
        return columnaEntre(titulo, titulo);
    }

    /**
     * Igual que {@link #columna(String)}, pero buscando la columna por
     * VARIOS nombres (el cliente renombra columnas entre temporadas:
     * "Libellé coloris" en H26 es "DESCRIPTION COLORIS" en SS27) y con un
     * rótulo distinto para el mensaje de error: la clave de búsqueda no
     * siempre es cómoda de leer ("LIBELL" para encontrar cualquier variante
     * de "Libellé coloris"), y el usuario que lee el aviso conoce su fichero
     * por el nombre de columna real, no por la clave interna.
     */
    public int columnaEntre(String rotuloEnElError, String... titulos) {
        int indice = columnaOpcional(titulos);
        if (indice < 0) {
            throw new IllegalArgumentException("La hoja '" + nombre()
                    + "' del excel de pedido no tiene la columna '" + rotuloEnElError + "'");
        }
        return indice;
    }

    /**
     * Igual que {@link #columna(String)} pero devuelve -1 en vez de lanzar
     * cuando la columna no está. Para las columnas de las que se puede
     * prescindir: sin ellas la salida sale incompleta y con aviso, pero sale.
     *
     * Con varios títulos manda el PRIMERO que casa con alguna cabecera, no
     * la columna más a la izquierda: el orden de los nombres es el de
     * preferencia.
     */
    public int columnaOpcional(String... titulos) {
        for (String titulo : titulos) {
            int indice = indice(cabecera, titulo);
            if (indice >= 0) {
                return indice;
            }
        }
        return -1;
    }

    public int primeraFilaDatos() {
        return hoja.getFirstRowNum() + 1;
    }

    public int ultimaFila() {
        return hoja.getLastRowNum();
    }

    /**
     * Texto de una celda, o "" si la fila o la celda no existen. Los
     * numéricos se leen como enteros: el PO (7672) y la talla (75) están
     * guardados como número y no deben salir como "7672.0".
     */
    public String texto(int fila, int columna) {
        Row f = hoja.getRow(fila);
        return f == null ? "" : texto(f.getCell(columna));
    }

    @Override
    public void close() throws IOException {
        libro.close();
    }

    /**
     * La primera hoja "EAN ..." y, si no hay ninguna, la primera cuya primera
     * fila lleva las cabeceras ARTICLE, COLORIS y TAILLE. Lo segundo es por
     * SS27, que llegó con la hoja llamada "Feuil1 Original"; se exigen las
     * tres cabeceras para no quedarse con una hoja auxiliar del libro que
     * casualmente nombre los artículos.
     */
    private static Sheet primeraHojaEan(Workbook libro) {
        for (int i = 0; i < libro.getNumberOfSheets(); i++) {
            if (libro.getSheetName(i).trim().toUpperCase(Locale.ROOT).startsWith("EAN")) {
                return libro.getSheetAt(i);
            }
        }
        for (int i = 0; i < libro.getNumberOfSheets(); i++) {
            Sheet hoja = libro.getSheetAt(i);
            Row cabecera = hoja.getRow(hoja.getFirstRowNum());
            if (cabecera != null && indice(cabecera, "ARTICLE") >= 0
                    && indice(cabecera, "COLORIS") >= 0 && indice(cabecera, "TAILLE") >= 0) {
                return hoja;
            }
        }
        throw new IllegalArgumentException("El excel de pedido no tiene ninguna hoja 'EAN ...'"
                + " ni ninguna con las columnas ARTICLE, COLORIS y TAILLE:"
                + " ¿es el archivo correcto?");
    }

    /**
     * El país de fabricación que va al final del EAN128 ("ES" SPAIN,
     * "MA" MOROCCO), con el mismo nombre que la columna Made in; "" si el
     * código no acaba en ninguno de los dos.
     *
     * Existe porque el excel de SS27 ya no trae la columna Made in, y el país
     * sigue estando dentro del código: el cliente lo compone así en todas
     * las filas medidas (las 100 del SS27 y las del H26, salvo las 8 que no
     * cuadran y que avisan).
     */
    public static String madeInDeEan128(String ean128) {
        String codigo = ean128 == null ? "" : ean128.trim().toUpperCase(Locale.ROOT);
        if (codigo.endsWith("ES")) {
            return "SPAIN";
        }
        if (codigo.endsWith("MA")) {
            return "MOROCCO";
        }
        return "";
    }

    private static int indice(Row cabecera, String titulo) {
        String buscado = normalizar(titulo);
        for (Cell celda : cabecera) {
            if (normalizar(texto(celda)).startsWith(buscado)) {
                return celda.getColumnIndex();
            }
        }
        return -1;
    }

    /**
     * Cabecera sin nada más que letras sin acento y dígitos: el cliente
     * escribe la misma columna como "PO" o "P.O.", "EAN13" o "EAN 13 ",
     * "Libellé" con o sin acento. Comparando así casan todas.
     */
    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
    }

    private static String texto(Cell celda) {
        if (celda == null) {
            return "";
        }
        return switch (celda.getCellType()) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> String.valueOf((long) celda.getNumericCellValue());
            case FORMULA -> celda.getCachedFormulaResultType() == CellType.STRING
                    ? celda.getStringCellValue() : "";
            default -> "";
        };
    }
}
