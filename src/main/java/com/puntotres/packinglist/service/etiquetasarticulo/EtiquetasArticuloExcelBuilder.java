package com.puntotres.packinglist.service.etiquetasarticulo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPrintSetup;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.service.etiquetas.AnclajeImagen;
import com.puntotres.packinglist.service.etiquetas.CodigoBarrasEan13;
import com.puntotres.packinglist.service.etiquetas.EtiquetaArticulo;

/**
 * Escribe un excel de etiquetas de artículo: una hoja por HojaEtiquetas y,
 * dentro de cada hoja, la MISMA etiqueta repetida en una rejilla de 4 × 10
 * que cabe justa en un A4 para imprimir, recortar y enganchar.
 *
 * La maquetación (anchos, altos, pageSetup, márgenes y anclaje de la imagen)
 * está medida del fichero real del cliente "AMI CODE BARRE H26 MOROCCO.xlsx"
 * y la fija EtiquetasArticuloMaquetacionTest, que compara lo generado contra
 * ese fichero. NO hay plantilla .xlsx: POI no copia el pageSetup al clonar
 * hojas ("Cloning sheets with page setup is not yet supported"), que es justo
 * lo único que interesaba heredar, así que heredarla no servía de nada.
 *
 * No es específico de AMI: cualquier cliente con esta misma rejilla lo
 * reutiliza pasándole sus HojaEtiquetas ya formateadas.
 */
@Service
public class EtiquetasArticuloExcelBuilder {

    /** Anchos de columna en unidades POI (caracteres × 256). */
    private static final int[] ANCHOS_COLUMNA =
            {3766, 4425, 621, 3766, 4534, 512, 3766, 4534, 621, 3766, 4534};

    /**
     * Columna izquierda de cada par de columnas de etiqueta. La derecha es
     * la siguiente; las columnas 2, 5 y 8 son separadores estrechos.
     *
     * Package-private: EtiquetasArticuloExcelBuilderTest la recorre para
     * comprobar las cuatro columnas repetidas de la rejilla.
     */
    static final int[] COLUMNAS_IZQUIERDA = {0, 3, 6, 9};

    static final int BLOQUES = 10;
    static final int FILAS_POR_BLOQUE = 8;
    /** Fila 0-based del primer bloque: la 0 es el margen superior. */
    private static final int PRIMERA_FILA_BLOQUE = 1;
    static final int ETIQUETAS_POR_HOJA = COLUMNAS_IZQUIERDA.length * BLOQUES;

    private static final float ALTO_MARGEN_SUPERIOR = 6f;
    private static final float ALTO_SEPARADORA = 9.95f;
    private static final float ALTO_DEFECTO = 15f;

    private static final short ESCALA = 74;
    private static final double MARGEN_IZQUIERDO = 0.0;
    /** 1 mm en pulgadas, que es lo que guarda el fichero del cliente. */
    private static final double MARGEN = 0.03937007874015748;
    /** Margen de cabecera/pie del fichero del cliente, en pulgadas. */
    private static final double MARGEN_CABECERA_PIE = 0.31496062992125984;

    /**
     * Longitud máxima de un nombre de hoja en Excel. Package-private: es la
     * restricción de Excel que este builder impone al crear la hoja, y
     * AmiEtiquetasArticuloGenerador (y cualquier otro cliente futuro) la
     * reutiliza al componer el nombre en vez de declarar la suya.
     */
    static final int MAX_NOMBRE_HOJA = 31;

    /** Desplazamiento del código de barras respecto a la fila base del bloque. */
    private static final int BARCODE_OFFSET_FILA = 2;
    /** Desplazamiento y tamaño del código de barras, en EMU. dx lo centra. */
    private static final long BARCODE_DX = 342901;
    private static final long BARCODE_DY = 9525;
    private static final long BARCODE_CX = 1463802;
    private static final long BARCODE_CY = 647700;

    /**
     * Cuerpo de la celda de color en veinteavos de punto, según la longitud
     * del texto: {longitud máxima, cuerpo}. Los ficheros del cliente lo
     * hacen a mano y de forma desigual (se ven 10,5 / 10 / 9 / 6pt para
     * longitudes solapadas); aquí es una regla determinista.
     */
    private static final int[][] CUERPO_COLOR_POR_LONGITUD = {
            {14, 210},                // hasta 14 caracteres: 10,5 pt
            {17, 180},                // 15 a 17:              9 pt
            {Integer.MAX_VALUE, 160}  // 18 o más:             8 pt
    };

    public byte[] generar(List<HojaEtiquetas> hojas) throws IOException {
        if (hojas.isEmpty()) {
            throw new IllegalArgumentException(
                    "No hay ninguna hoja que generar: Excel no abre un libro sin hojas");
        }
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Estilos estilos = new Estilos(libro);
            // POI compara los nombres de hoja con equalsIgnoreCase (locale-
            // independiente): la red de unicidad tiene que usar la misma
            // semántica o dos nombres que solo difieran en mayúsculas
            // ("NOIR" / "Noir") tumban la generación entera.
            Set<String> nombresUsados = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (HojaEtiquetas hoja : hojas) {
                XSSFSheet destino =
                        crearHojaMaquetada(libro, nombreUnico(nombresUsados, hoja.nombreHoja()));
                rellenar(libro, destino, hoja.etiqueta(), estilos);
            }
            libro.setActiveSheet(0);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    /** Fila 0-based donde arranca el bloque: 1, 9, 17 ... 73. */
    static int filaBase(int bloque) {
        return PRIMERA_FILA_BLOQUE + bloque * FILAS_POR_BLOQUE;
    }

    /** Cuerpo en veinteavos de punto para un nombre de color. */
    static int cuerpoPara(String texto) {
        int longitud = texto == null ? 0 : texto.length();
        for (int[] tramo : CUERPO_COLOR_POR_LONGITUD) {
            if (longitud <= tramo[0]) {
                return tramo[1];
            }
        }
        return CUERPO_COLOR_POR_LONGITUD[CUERPO_COLOR_POR_LONGITUD.length - 1][1];
    }

    /**
     * Excel no admite dos hojas con el mismo nombre. El nombre llega ya
     * saneado y recortado desde el generador del cliente; esto es la última
     * red: dos filas idénticas en el pedido no deben romper la generación.
     */
    static String nombreUnico(Set<String> usados, String nombre) {
        if (usados.add(nombre)) {
            return nombre;
        }
        for (int n = 2; ; n++) {
            String sufijo = "-" + n;
            String candidato = nombre.length() + sufijo.length() <= MAX_NOMBRE_HOJA
                    ? nombre + sufijo
                    : nombre.substring(0, MAX_NOMBRE_HOJA - sufijo.length()) + sufijo;
            if (usados.add(candidato)) {
                return candidato;
            }
        }
    }

    // --- pasos ---

    private static XSSFSheet crearHojaMaquetada(XSSFWorkbook libro, String nombre) {
        XSSFSheet hoja = libro.createSheet(nombre);
        for (int columna = 0; columna < ANCHOS_COLUMNA.length; columna++) {
            hoja.setColumnWidth(columna, ANCHOS_COLUMNA[columna]);
        }
        hoja.setDefaultRowHeightInPoints(ALTO_DEFECTO);
        hoja.createRow(0).setHeightInPoints(ALTO_MARGEN_SUPERIOR);
        for (int bloque = 0; bloque < BLOQUES; bloque++) {
            int base = filaBase(bloque);
            hoja.createRow(base);        // referencia + talla
            hoja.createRow(base + 1);    // color + pedido
            // Las filas del código de barras (base+2 a base+6) no se crean:
            // la imagen flota sobre ellas y AnclajeImagen.altoFilaEmu ya cae
            // al alto por defecto cuando la fila no existe. Y el último
            // bloque no lleva separadora, igual que el fichero del cliente:
            // crearla haría la hoja 9,95pt más alta y sacaría una segunda
            // página al imprimir (el fichero real termina en la fila 74).
            if (bloque < BLOQUES - 1) {
                hoja.createRow(base + FILAS_POR_BLOQUE - 1)
                        .setHeightInPoints(ALTO_SEPARADORA);
            }
        }
        XSSFPrintSetup impresion = hoja.getPrintSetup();
        impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
        impresion.setScale(ESCALA);
        impresion.setLandscape(false);
        hoja.setMargin(PageMargin.LEFT, MARGEN_IZQUIERDO);
        hoja.setMargin(PageMargin.RIGHT, MARGEN);
        hoja.setMargin(PageMargin.TOP, MARGEN);
        hoja.setMargin(PageMargin.BOTTOM, MARGEN);
        hoja.setMargin(PageMargin.HEADER, MARGEN_CABECERA_PIE);
        hoja.setMargin(PageMargin.FOOTER, MARGEN_CABECERA_PIE);
        return hoja;
    }

    private static void rellenar(XSSFWorkbook libro, XSSFSheet hoja,
                                 EtiquetaArticulo etiqueta, Estilos estilos) {
        int imagen = indiceImagen(libro, etiqueta.ean13());
        XSSFDrawing dibujo = imagen >= 0 ? hoja.createDrawingPatriarch() : null;
        CellStyle derecha = estilos.derecha();
        CellStyle color = estilos.color(etiqueta.color());
        for (int bloque = 0; bloque < BLOQUES; bloque++) {
            int base = filaBase(bloque);
            for (int izquierda : COLUMNAS_IZQUIERDA) {
                escribir(hoja, base, izquierda, etiqueta.referencia(), null);
                escribir(hoja, base, izquierda + 1, etiqueta.talla(), derecha);
                escribir(hoja, base + 1, izquierda, etiqueta.color(), color);
                escribir(hoja, base + 1, izquierda + 1, etiqueta.pedido(), derecha);
                if (dibujo != null) {
                    dibujo.createPicture(AnclajeImagen.fijo(hoja, izquierda, BARCODE_DX,
                            base + BARCODE_OFFSET_FILA, BARCODE_DY,
                            BARCODE_CX, BARCODE_CY), imagen);
                }
            }
        }
    }

    /**
     * Añade el PNG del código de barras al libro UNA vez y devuelve su
     * índice para que los 40 anclajes de la hoja lo reutilicen: así lo
     * guarda Excel en los ficheros del cliente, una imagen y 40 anclajes.
     * -1 si la etiqueta no trae un EAN13 válido.
     */
    private static int indiceImagen(XSSFWorkbook libro, String ean13) {
        Optional<byte[]> png = CodigoBarrasEan13.png(ean13);
        return png.map(bytes -> libro.addPicture(bytes, Workbook.PICTURE_TYPE_PNG)).orElse(-1);
    }

    /**
     * Escribe en una celda de una fila que crearHojaMaquetada ya ha creado
     * (siempre base o base+1): no hay rama defensiva de "por si no existe",
     * porque con la maquetación fija esas filas siempre existen.
     */
    private static void escribir(XSSFSheet hoja, int fila, int columna, String valor,
                                 CellStyle estilo) {
        Cell celda = hoja.getRow(fila).createCell(columna);
        if (valor == null || valor.isBlank()) {
            celda.setBlank();
        } else {
            celda.setCellValue(valor);
        }
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    /**
     * Estilos creados UNA sola vez por libro y cacheados: POI los acumula
     * por libro, no por hoja, y un fichero de cinturones llega a 79 hojas.
     */
    private static final class Estilos {

        private final XSSFWorkbook libro;
        private final Map<Integer, CellStyle> porCuerpo = new HashMap<>();
        private CellStyle derecha;

        Estilos(XSSFWorkbook libro) {
            this.libro = libro;
        }

        /** Talla y pedido, en la columna derecha de la etiqueta. */
        CellStyle derecha() {
            if (derecha == null) {
                derecha = libro.createCellStyle();
                derecha.setAlignment(HorizontalAlignment.RIGHT);
            }
            return derecha;
        }

        /** Color, con el cuerpo reducido si el nombre es largo. */
        CellStyle color(String texto) {
            return porCuerpo.computeIfAbsent(cuerpoPara(texto), cuerpo -> {
                Font fuente = libro.createFont();
                // setFontHeight va en veinteavos de punto:
                // setFontHeightInPoints solo acepta puntos enteros y hacen
                // falta los 10,5pt del fichero del cliente.
                fuente.setFontHeight((short) cuerpo.intValue());
                CellStyle estilo = libro.createCellStyle();
                estilo.setFont(fuente);
                return estilo;
            });
        }
    }
}
