package com.puntotres.packinglist.service.produccion;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.DatosHojaProduccion;
import com.puntotres.packinglist.model.DatosHojaProduccion.LineaProduccion;

/**
 * Escribe el excel de la Hoja de Producción para un taller externo. Sin
 * plantilla: la maquetación son constantes, como en {@code OrdenCorteExcelBuilder}.
 *
 * <b>Una hoja es un modelo</b> (decisión del usuario, segunda revisión): el
 * modelo no es una columna de la tabla sino el título de la hoja.
 * <ul>
 * <li>Cabecera: a la izquierda la <b>foto del modelo en grande</b> (la
 * principal del catálogo); a la derecha el modelo con letra grande y el
 * nombre del bolso debajo, «HOJA DE PRODUCCIÓN» con su número igual de grande
 * y cuatro fichas: cliente, temporada, taller y fecha de salida.</li>
 * <li>Tabla: una fila de grupos (PIEL BASE, PIEL COMBINACIÓN, MARCADO) en tinta
 * con la letra blanca y, debajo, la fila de columnas en gris claro. Cada fila
 * es un color del modelo. En la segunda revisión se aclaró esta fila por un
 * malentendido: lo que el usuario quería más claro eran los TÍTULOS de la
 * cabecera ({@link #COLOR_TITULO}), y la tabla volvió a este estilo.</li>
 * <li>Todas las filas de la tabla miden lo mismo: {@value #ALTO_LINEA} puntos
 * (petición del usuario), o más si un texto largo lo pide
 * ({@link AlturaFila}): nada se corta ni se sale de la celda.</li>
 * </ul>
 * Todo el texto va en mayúsculas salvo la marca del hilo. Apaisado, A4,
 * ajustado al ancho de una página. Sin colores de marca: tinta, grises y
 * rellenos claros, que imprimen bien en blanco y negro.
 */
@Service
public class HojaProduccionExcelBuilder {

    static final String FUENTE = "Arial";

    private static final byte[] TINTA = HexFormat.of().parseHex("1C2733");
    private static final byte[] GRIS = HexFormat.of().parseHex("6B7680");
    private static final byte[] RELLENO_FICHA = HexFormat.of().parseHex("F3F5F7");
    /** La fila de columnas (PIEL, COLOR, INTERIOR…). */
    static final byte[] RELLENO_COLUMNAS = HexFormat.of().parseHex("E9EDF1");
    /** La fila de grupos de la tabla: en tinta con la letra blanca (el estilo de la primera versión, que el usuario prefiere). */
    static final byte[] RELLENO_GRUPOS = TINTA;
    private static final byte[] BLANCO = HexFormat.of().parseHex("FFFFFF");
    /**
     * Los títulos de la cabecera (modelo, número y nombre del bolso): un gris
     * un par de tonos más oscuro que el de los rótulos, y no la tinta negra
     * azulada (petición del usuario, segunda revisión).
     */
    static final byte[] COLOR_TITULO = HexFormat.of().parseHex("4A5560");
    private static final byte[] FILETE = HexFormat.of().parseHex("C9D1D9");
    private static final byte[] FILETE_FICHA = HexFormat.of().parseHex("9AA5B0");

    /** Columna A: un margen estrecho; la hoja va de la B a la L. */
    static final int UNIDADES = 1;
    static final int PIEL_BASE = 2;
    static final int COLOR_BASE = 3;
    static final int PIEL_COMBINACION = 4;
    static final int COLOR_COMBINACION = 5;
    static final int HILO = 6;
    static final int MARCADO_INTERIOR = 7;
    static final int MARCADO_EXTERIOR = 8;
    static final int FORNITURA = 9;
    static final int FORRO = 10;
    static final int TINTA_COL = 11;
    static final int PRIMERA = UNIDADES;
    static final int ULTIMA = TINTA_COL;

    /** Ancho de cada columna en caracteres de Excel, de la A a la L. */
    private static final int[] ANCHOS = {2, 12, 20, 17, 20, 17, 15, 18, 18, 14, 18, 16};

    /** La foto ocupa las columnas B a D de la cabecera; el título, de la E a la L. */
    static final int FOTO_HASTA = COLOR_BASE;
    static final int TITULO_DESDE = PIEL_COMBINACION;
    static final int TITULO_HASTA = MARCADO_INTERIOR;
    static final int NUMERO_DESDE = MARCADO_EXTERIOR;

    static final int FILA_ROTULOS = 1;
    static final int FILA_MODELO = 2;
    static final int FILA_BOLSO = 3;
    static final int FILA_FICHA_ROTULO = 5;
    static final int FILA_FICHA_VALOR = 6;
    static final int FILA_GRUPOS = 8;
    static final int FILA_COLUMNAS = 9;
    static final int PRIMERA_LINEA = 10;

    /** Alto de la cabecera, fila a fila desde FILA_ROTULOS: es también el alto de la foto. */
    private static final float[] ALTOS_CABECERA = {18, 58, 30, 40, 16, 42};

    private static final int LETRA_TITULO = 30;
    private static final int LETRA_BOLSO = 14;
    private static final int LETRA_ROTULO = 9;
    private static final int LETRA_FICHA = 13;
    private static final int LETRA_CABECERA = 9;
    private static final int LETRA_TEXTO = 11;
    private static final int LETRA_UNIDADES = 20;
    private static final int LETRA_MARCADO = 12;
    private static final int LETRA_HILO_NUMERO = 14;
    /** El alto de todas las filas de la tabla (petición del usuario). */
    static final double ALTO_LINEA = 110;
    /** Aire alrededor de la foto, en píxeles. */
    private static final int MARGEN_FOTO = 6;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** El excel sin foto. */
    public byte[] generar(DatosHojaProduccion datos) throws IOException {
        return generar(datos, null);
    }

    /** El excel con la foto del modelo ({@code foto}: los bytes JPEG o PNG, o null si no tiene). */
    public byte[] generar(DatosHojaProduccion datos, byte[] foto) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet("Hoja de producción");
            Estilos estilos = new Estilos(libro);
            for (int c = 0; c < ANCHOS.length; c++) {
                hoja.setColumnWidth(c, ANCHOS[c] * 256);
            }
            hoja.setDisplayGridlines(false);

            cabecera(hoja, estilos, datos);
            foto(libro, hoja, estilos, foto);
            cabeceraTabla(hoja, estilos);
            double alto = altoDeLasLineas(datos.lineas());
            int fila = PRIMERA_LINEA;
            for (LineaProduccion linea : datos.lineas()) {
                escribirLinea(hoja, estilos, fila++, linea, alto);
            }
            if (datos.lineas().size() > 1) {
                total(hoja, estilos, fila++, datos.totalUnidades());
            }

            impresion(libro, hoja, fila - 1);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    /** El nombre del fichero que se descarga: «Hoja de producción 325 C&C LEATHER - APC F67008.xlsx». */
    public static String nombreFichero(DatosHojaProduccion datos) {
        StringBuilder nombre = new StringBuilder("Hoja de producción ").append(datos.numero());
        if (!datos.taller().isBlank()) {
            nombre.append(' ').append(datos.taller());
        }
        String articulo = (datos.cliente() + " " + datos.modelo()).trim();
        if (!articulo.isBlank()) {
            nombre.append(" - ").append(articulo);
        }
        return nombre.toString().replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").trim() + ".xlsx";
    }

    // --- Cabecera ---

    private void cabecera(XSSFSheet hoja, Estilos estilos, DatosHojaProduccion datos) {
        for (int i = 0; i < ALTOS_CABECERA.length; i++) {
            hoja.createRow(FILA_ROTULOS + i).setHeightInPoints(ALTOS_CABECERA[i]);
        }

        // El modelo, grande, con su rótulo encima y el nombre del bolso debajo.
        textoCombinado(hoja, FILA_ROTULOS, TITULO_DESDE, TITULO_HASTA,
                estilos.texto("MODELO", LETRA_ROTULO + 1, false, GRIS), estilos.rotuloAbajo);
        textoCombinado(hoja, FILA_MODELO, TITULO_DESDE, TITULO_HASTA,
                estilos.texto(mayusculas(datos.modelo()), LETRA_TITULO, true, COLOR_TITULO), estilos.titulo);
        textoCombinado(hoja, FILA_BOLSO, TITULO_DESDE, TITULO_HASTA,
                estilos.texto(mayusculas(datos.nombreBolso()), LETRA_BOLSO, true, COLOR_TITULO), estilos.rotuloArriba);

        // A la derecha, el número igual de grande que el modelo: es lo que el taller busca primero.
        textoCombinado(hoja, FILA_ROTULOS, NUMERO_DESDE, ULTIMA,
                estilos.texto("HOJA DE PRODUCCIÓN", LETRA_ROTULO + 1, false, GRIS), estilos.rotuloDerecha);
        textoCombinado(hoja, FILA_MODELO, NUMERO_DESDE, ULTIMA,
                estilos.texto("Nº " + datos.numero(), LETRA_TITULO, true, COLOR_TITULO), estilos.numeroDerecha);

        // Cuatro fichas a la misma altura, de dos columnas cada una.
        String cliente = datos.nombreCliente().isBlank() ? datos.cliente() : datos.nombreCliente();
        String fecha = datos.fechaSalida() == null ? "" : FECHA.format(datos.fechaSalida());
        String[][] fichas = {
            {"CLIENTE", mayusculas(cliente)},
            {"TEMPORADA", mayusculas(datos.temporada())},
            {"TALLER", mayusculas(datos.taller())},
            {"FECHA DE SALIDA", fecha},
        };
        XSSFRow valores = hoja.getRow(FILA_FICHA_VALOR);
        double alto = ALTOS_CABECERA[FILA_FICHA_VALOR - FILA_ROTULOS];
        for (int i = 0; i < fichas.length; i++) {
            int desde = TITULO_DESDE + i * 2;
            int hasta = desde + 1;
            textoCombinado(hoja, FILA_FICHA_ROTULO, desde, hasta,
                    estilos.texto(fichas[i][0], LETRA_ROTULO, false, GRIS), estilos.fichaRotulo);
            textoCombinado(hoja, FILA_FICHA_VALOR, desde, hasta,
                    estilos.texto(fichas[i][1], LETRA_FICHA, true, TINTA), estilos.fichaValor);
            alto = Math.max(alto, AlturaFila.puntos(fichas[i][1], anchoDeColumnas(desde, hasta), LETRA_FICHA));
        }
        valores.setHeightInPoints((float) alto);
    }

    /**
     * La foto, lo más grande que quepa en las columnas B a D a lo alto de la
     * cabecera, sin deformarla y centrada. Sin foto (o una que no se deja
     * leer) el hueco se queda con «SIN FOTO»: la hoja se genera igual.
     */
    private void foto(XSSFWorkbook libro, XSSFSheet hoja, Estilos estilos, byte[] foto) {
        int ultimaFila = FILA_ROTULOS + ALTOS_CABECERA.length - 1;
        for (int f = FILA_ROTULOS; f <= ultimaFila; f++) {
            for (int c = PRIMERA; c <= FOTO_HASTA; c++) {
                hoja.getRow(f).createCell(c).setCellStyle(estilos.marcoFoto);
            }
        }
        hoja.addMergedRegion(new CellRangeAddress(FILA_ROTULOS, ultimaFila, PRIMERA, FOTO_HASTA));

        BufferedImage imagen = leer(foto);
        if (imagen == null) {
            hoja.getRow(FILA_ROTULOS).getCell(PRIMERA)
                    .setCellValue(estilos.texto("SIN FOTO", LETRA_ROTULO + 1, false, GRIS));
            return;
        }
        double anchoCaja = 0;
        for (int c = PRIMERA; c <= FOTO_HASTA; c++) {
            anchoCaja += hoja.getColumnWidthInPixels(c);
        }
        double altoCaja = 0;
        for (int f = FILA_ROTULOS; f <= ultimaFila; f++) {
            altoCaja += hoja.getRow(f).getHeightInPoints() * 96 / 72;
        }
        double escala = Math.min((anchoCaja - 2 * MARGEN_FOTO) / imagen.getWidth(),
                (altoCaja - 2 * MARGEN_FOTO) / imagen.getHeight());
        double ancho = imagen.getWidth() * escala;
        double alto = imagen.getHeight() * escala;
        double x = (anchoCaja - ancho) / 2;
        double y = (altoCaja - alto) / 2;

        int formato = esPng(foto) ? Workbook.PICTURE_TYPE_PNG : Workbook.PICTURE_TYPE_JPEG;
        int indice = libro.addPicture(foto, formato);
        XSSFDrawing dibujo = hoja.createDrawingPatriarch();
        int[] desde = posicion(hoja, x, y);
        int[] hasta = posicion(hoja, x + ancho, y + alto);
        XSSFClientAnchor ancla = new XSSFClientAnchor(desde[1], desde[3], hasta[1], hasta[3],
                desde[0], desde[2], hasta[0], hasta[2]);
        dibujo.createPicture(ancla, indice);
    }

    /**
     * Pasa un punto en píxeles, medido desde la esquina de arriba a la
     * izquierda de la caja de la foto, a {columna, desplazamiento EMU, fila,
     * desplazamiento EMU}: lo que pide el ancla de una imagen.
     */
    private static int[] posicion(XSSFSheet hoja, double x, double y) {
        int columna = PRIMERA;
        double resto = x;
        while (resto >= hoja.getColumnWidthInPixels(columna) && columna < ULTIMA) {
            resto -= hoja.getColumnWidthInPixels(columna);
            columna++;
        }
        int fila = FILA_ROTULOS;
        double restoY = y;
        while (hoja.getRow(fila + 1) != null && restoY >= hoja.getRow(fila).getHeightInPoints() * 96 / 72) {
            restoY -= hoja.getRow(fila).getHeightInPoints() * 96 / 72;
            fila++;
        }
        return new int[] {columna, (int) Math.round(resto * Units.EMU_PER_PIXEL), fila,
                (int) Math.round(restoY * Units.EMU_PER_PIXEL)};
    }

    private static BufferedImage leer(byte[] foto) {
        if (foto == null || foto.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(foto));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean esPng(byte[] bytes) {
        return bytes.length > 4 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }

    // --- Tabla ---

    private void cabeceraTabla(XSSFSheet hoja, Estilos estilos) {
        XSSFRow grupos = hoja.createRow(FILA_GRUPOS);
        grupos.setHeightInPoints(20);
        XSSFRow columnas = hoja.createRow(FILA_COLUMNAS);
        columnas.setHeightInPoints(18);

        // Las columnas sin subdivisión ocupan las dos filas.
        grupoSimple(hoja, estilos, grupos, columnas, UNIDADES, "UNIDADES");
        grupoDoble(hoja, estilos, grupos, columnas, PIEL_BASE, "PIEL BASE", "PIEL", "COLOR");
        grupoDoble(hoja, estilos, grupos, columnas, PIEL_COMBINACION, "PIEL COMBINACIÓN", "PIEL", "COLOR");
        grupoSimple(hoja, estilos, grupos, columnas, HILO, "HILO");
        grupoDoble(hoja, estilos, grupos, columnas, MARCADO_INTERIOR, "MARCADO", "INTERIOR", "EXTERIOR");
        grupoSimple(hoja, estilos, grupos, columnas, FORNITURA, "FORNITURA");
        grupoSimple(hoja, estilos, grupos, columnas, FORRO, "FORRO");
        grupoSimple(hoja, estilos, grupos, columnas, TINTA_COL, "TINTA");
    }

    private void grupoSimple(XSSFSheet hoja, Estilos estilos, XSSFRow grupos, XSSFRow columnas, int col, String texto) {
        celda(grupos, col, estilos.texto(texto, LETRA_CABECERA, true, BLANCO), estilos.grupo);
        columnas.createCell(col).setCellStyle(estilos.grupo);
        hoja.addMergedRegion(new CellRangeAddress(FILA_GRUPOS, FILA_COLUMNAS, col, col));
    }

    private void grupoDoble(XSSFSheet hoja, Estilos estilos, XSSFRow grupos, XSSFRow columnas, int col,
                            String grupo, String primera, String segunda) {
        celda(grupos, col, estilos.texto(grupo, LETRA_CABECERA, true, BLANCO), estilos.grupo);
        grupos.createCell(col + 1).setCellStyle(estilos.grupo);
        hoja.addMergedRegion(new CellRangeAddress(FILA_GRUPOS, FILA_GRUPOS, col, col + 1));
        celda(columnas, col, estilos.texto(primera, LETRA_CABECERA, true, TINTA), estilos.columna);
        celda(columnas, col + 1, estilos.texto(segunda, LETRA_CABECERA, true, TINTA), estilos.columna);
    }

    /**
     * El alto común de las filas de la tabla: {@link #ALTO_LINEA}, o el que
     * pida la celda más cargada de todas si no cabe en él. Todas las filas
     * miden lo mismo (petición del usuario).
     */
    static double altoDeLasLineas(List<LineaProduccion> lineas) {
        double alto = ALTO_LINEA;
        for (LineaProduccion l : lineas) {
            alto = Math.max(alto, AlturaFila.puntos(mayusculas(l.pielBase()), anchoDe(PIEL_BASE), LETRA_TEXTO));
            alto = Math.max(alto, AlturaFila.puntos(mayusculas(l.colorBase()), anchoDe(COLOR_BASE), LETRA_TEXTO));
            alto = Math.max(alto, AlturaFila.puntos(mayusculas(l.pielCombinacion()), anchoDe(PIEL_COMBINACION), LETRA_TEXTO));
            alto = Math.max(alto, AlturaFila.puntos(mayusculas(l.colorCombinacion()), anchoDe(COLOR_COMBINACION), LETRA_TEXTO));
            alto = Math.max(alto, altoHilo(l));
            alto = Math.max(alto, AlturaFila.puntos(mayusculas(l.marcadoInterior()), anchoDe(MARCADO_INTERIOR), LETRA_MARCADO));
            alto = Math.max(alto, AlturaFila.puntos(mayusculas(l.marcadoExterior()), anchoDe(MARCADO_EXTERIOR), LETRA_MARCADO));
            alto = Math.max(alto, AlturaFila.puntos(mayusculas(l.fornitura()), anchoDe(FORNITURA), LETRA_MARCADO));
            alto = Math.max(alto, AlturaFila.puntos(mayusculas(l.forro()), anchoDe(FORRO), LETRA_TEXTO));
            alto = Math.max(alto, AlturaFila.puntos(mayusculas(l.tinta()), anchoDe(TINTA_COL), LETRA_TEXTO));
        }
        return alto;
    }

    private void escribirLinea(XSSFSheet hoja, Estilos estilos, int indice, LineaProduccion linea, double alto) {
        XSSFRow fila = hoja.createRow(indice);
        XSSFCell unidades = fila.createCell(UNIDADES);
        unidades.setCellStyle(estilos.unidades);
        if (linea.unidades() != null) {
            unidades.setCellValue(linea.unidades());
        }
        celda(fila, PIEL_BASE, estilos.texto(mayusculas(linea.pielBase()), LETRA_TEXTO, false, TINTA), estilos.texto);
        celda(fila, COLOR_BASE, estilos.texto(mayusculas(linea.colorBase()), LETRA_TEXTO, false, TINTA), estilos.texto);
        celda(fila, PIEL_COMBINACION, estilos.texto(mayusculas(linea.pielCombinacion()), LETRA_TEXTO, false, TINTA), estilos.texto);
        celda(fila, COLOR_COMBINACION, estilos.texto(mayusculas(linea.colorCombinacion()), LETRA_TEXTO, false, TINTA), estilos.texto);
        hilo(fila, estilos, linea);
        celda(fila, MARCADO_INTERIOR, estilos.texto(mayusculas(linea.marcadoInterior()), LETRA_MARCADO, true, TINTA), estilos.centrada);
        celda(fila, MARCADO_EXTERIOR, estilos.texto(mayusculas(linea.marcadoExterior()), LETRA_MARCADO, true, TINTA), estilos.centrada);
        celda(fila, FORNITURA, estilos.texto(mayusculas(linea.fornitura()), LETRA_MARCADO, true, TINTA), estilos.centrada);
        celda(fila, FORRO, estilos.texto(mayusculas(linea.forro()), LETRA_TEXTO, false, TINTA), estilos.texto);
        celda(fila, TINTA_COL, estilos.texto(mayusculas(linea.tinta()), LETRA_TEXTO, false, TINTA), estilos.texto);
        fila.setHeightInPoints((float) alto);
    }

    /**
     * «Gütermann» en pequeño, debajo el número en negrita y mayor, y debajo
     * los cm. Lo que falte no deja línea en blanco.
     */
    private void hilo(XSSFRow fila, Estilos estilos, LineaProduccion linea) {
        XSSFCell celda = fila.createCell(HILO);
        celda.setCellStyle(estilos.centrada);
        String marca = linea.marcaHilo().trim();
        String numero = linea.numeroHilo().trim();
        String cm = linea.cmHiloConUnidad();
        StringBuilder texto = new StringBuilder();
        int[][] tramos = new int[3][];
        String[] partes = {marca, numero, cm};
        for (int i = 0; i < partes.length; i++) {
            if (partes[i].isEmpty()) {
                continue;
            }
            if (texto.length() > 0) {
                texto.append('\n');
            }
            tramos[i] = new int[] {texto.length(), texto.length() + partes[i].length()};
            texto.append(partes[i]);
        }
        XSSFRichTextString rico = new XSSFRichTextString(texto.toString());
        if (tramos[0] != null) {
            rico.applyFont(tramos[0][0], tramos[0][1], estilos.fuente(LETRA_TEXTO - 1, false, GRIS));
        }
        if (tramos[1] != null) {
            rico.applyFont(tramos[1][0], tramos[1][1], estilos.fuente(LETRA_HILO_NUMERO, true, TINTA));
        }
        if (tramos[2] != null) {
            rico.applyFont(tramos[2][0], tramos[2][1], estilos.fuente(LETRA_TEXTO, false, TINTA));
        }
        celda.setCellValue(rico);
    }

    private static double altoHilo(LineaProduccion linea) {
        int lineas = 0;
        lineas += linea.marcaHilo().isBlank() ? 0 : AlturaFila.lineas(linea.marcaHilo(), anchoDe(HILO), LETRA_TEXTO - 1);
        lineas += linea.numeroHilo().isBlank() ? 0 : AlturaFila.lineas(linea.numeroHilo(), anchoDe(HILO), LETRA_HILO_NUMERO);
        lineas += linea.cmHilo().isBlank() ? 0 : AlturaFila.lineas(linea.cmHiloConUnidad(), anchoDe(HILO), LETRA_TEXTO);
        return AlturaFila.puntos(Math.max(1, lineas), LETRA_HILO_NUMERO);
    }

    private void total(XSSFSheet hoja, Estilos estilos, int indice, int totalUnidades) {
        XSSFRow fila = hoja.createRow(indice);
        fila.setHeightInPoints(26);
        XSSFCell total = fila.createCell(UNIDADES);
        total.setCellValue(totalUnidades);
        total.setCellStyle(estilos.totalValor);
        celda(fila, PIEL_BASE, estilos.texto("UNIDADES EN TOTAL", LETRA_TEXTO - 1, false, GRIS), estilos.totalRotulo);
    }

    private void impresion(XSSFWorkbook libro, XSSFSheet hoja, int ultimaFila) {
        PrintSetup impresion = hoja.getPrintSetup();
        impresion.setLandscape(true);
        impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
        impresion.setFitWidth((short) 1);
        impresion.setFitHeight((short) 0);
        hoja.setFitToPage(true);
        hoja.setHorizontallyCenter(true);
        hoja.setMargin(PageMargin.LEFT, 0.4);
        hoja.setMargin(PageMargin.RIGHT, 0.4);
        hoja.setMargin(PageMargin.TOP, 0.5);
        hoja.setMargin(PageMargin.BOTTOM, 0.5);
        libro.setPrintArea(0, PRIMERA, ULTIMA, FILA_ROTULOS, ultimaFila);
    }

    // --- Utilidades ---

    /** Escribe en la primera celda, da el estilo a todas (si no, relleno y filetes se cortan) y las combina. */
    private static void textoCombinado(XSSFSheet hoja, int fila, int desde, int hasta, XSSFRichTextString texto,
                                       XSSFCellStyle estilo) {
        XSSFRow row = hoja.getRow(fila);
        celda(row, desde, texto, estilo);
        for (int c = desde + 1; c <= hasta; c++) {
            row.createCell(c).setCellStyle(estilo);
        }
        if (hasta > desde) {
            hoja.addMergedRegion(new CellRangeAddress(fila, fila, desde, hasta));
        }
    }

    private static void celda(XSSFRow fila, int col, XSSFRichTextString texto, XSSFCellStyle estilo) {
        XSSFCell celda = fila.createCell(col);
        celda.setCellValue(texto);
        celda.setCellStyle(estilo);
    }

    private static int anchoDe(int col) {
        return ANCHOS[col] * 256;
    }

    private static int anchoDeColumnas(int desde, int hasta) {
        int total = 0;
        for (int c = desde; c <= hasta; c++) {
            total += ANCHOS[c];
        }
        return total * 256;
    }

    private static String mayusculas(String texto) {
        return texto == null ? "" : texto.trim().toUpperCase(Locale.forLanguageTag("es"));
    }

    /** Fuentes y estilos del libro, creados una vez: Excel tiene un tope de unos y otros. */
    private static final class Estilos {

        private final XSSFWorkbook libro;
        private final Map<String, XSSFFont> fuentes = new HashMap<>();
        final XSSFCellStyle titulo;
        final XSSFCellStyle rotuloAbajo;
        final XSSFCellStyle rotuloArriba;
        final XSSFCellStyle rotuloDerecha;
        final XSSFCellStyle numeroDerecha;
        final XSSFCellStyle fichaRotulo;
        final XSSFCellStyle fichaValor;
        final XSSFCellStyle marcoFoto;
        final XSSFCellStyle grupo;
        final XSSFCellStyle columna;
        final XSSFCellStyle texto;
        final XSSFCellStyle centrada;
        final XSSFCellStyle unidades;
        final XSSFCellStyle totalRotulo;
        final XSSFCellStyle totalValor;

        Estilos(XSSFWorkbook libro) {
            this.libro = libro;
            titulo = estilo(HorizontalAlignment.LEFT, VerticalAlignment.CENTER, null, false);
            titulo.setIndention((short) 1);
            rotuloAbajo = estilo(HorizontalAlignment.LEFT, VerticalAlignment.BOTTOM, null, false);
            rotuloAbajo.setIndention((short) 1);
            rotuloArriba = estilo(HorizontalAlignment.LEFT, VerticalAlignment.TOP, null, true);
            rotuloArriba.setIndention((short) 1);
            rotuloDerecha = estilo(HorizontalAlignment.RIGHT, VerticalAlignment.BOTTOM, null, false);
            numeroDerecha = estilo(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER, null, false);

            fichaRotulo = estilo(HorizontalAlignment.LEFT, VerticalAlignment.BOTTOM, RELLENO_FICHA, false);
            fichaRotulo.setIndention((short) 1);
            fichaValor = estilo(HorizontalAlignment.LEFT, VerticalAlignment.CENTER, RELLENO_FICHA, true);
            fichaValor.setIndention((short) 1);
            fichaValor.setBorderBottom(BorderStyle.MEDIUM);
            fichaValor.setBottomBorderColor(new XSSFColor(FILETE_FICHA, null));

            marcoFoto = estilo(HorizontalAlignment.CENTER, VerticalAlignment.CENTER, null, false);
            bordes(marcoFoto);

            grupo = estilo(HorizontalAlignment.CENTER, VerticalAlignment.CENTER, RELLENO_GRUPOS, true);
            columna = estilo(HorizontalAlignment.CENTER, VerticalAlignment.CENTER, RELLENO_COLUMNAS, true);
            bordes(columna);

            texto = estilo(HorizontalAlignment.LEFT, VerticalAlignment.CENTER, null, true);
            texto.setIndention((short) 1);
            bordes(texto);
            centrada = estilo(HorizontalAlignment.CENTER, VerticalAlignment.CENTER, null, true);
            bordes(centrada);
            unidades = estilo(HorizontalAlignment.CENTER, VerticalAlignment.CENTER, null, false);
            unidades.setFont(fuente(LETRA_UNIDADES, true, TINTA));
            bordes(unidades);

            totalRotulo = estilo(HorizontalAlignment.LEFT, VerticalAlignment.CENTER, null, false);
            totalRotulo.setIndention((short) 1);
            totalValor = estilo(HorizontalAlignment.CENTER, VerticalAlignment.CENTER, null, false);
            totalValor.setFont(fuente(LETRA_BOLSO, true, TINTA));
            totalValor.setBorderTop(BorderStyle.MEDIUM);
            totalValor.setTopBorderColor(new XSSFColor(FILETE_FICHA, null));
        }

        private XSSFCellStyle estilo(HorizontalAlignment horizontal, VerticalAlignment vertical, byte[] relleno,
                                     boolean ajustar) {
            XSSFCellStyle estilo = libro.createCellStyle();
            estilo.setAlignment(horizontal);
            estilo.setVerticalAlignment(vertical);
            estilo.setWrapText(ajustar);
            if (relleno != null) {
                estilo.setFillForegroundColor(new XSSFColor(relleno, null));
                estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return estilo;
        }

        private static void bordes(XSSFCellStyle estilo) {
            XSSFColor color = new XSSFColor(FILETE, null);
            estilo.setBorderTop(BorderStyle.THIN);
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBorderLeft(BorderStyle.THIN);
            estilo.setBorderRight(BorderStyle.THIN);
            estilo.setTopBorderColor(color);
            estilo.setBottomBorderColor(color);
            estilo.setLeftBorderColor(color);
            estilo.setRightBorderColor(color);
        }

        XSSFRichTextString texto(String texto, int tamano, boolean negrita, byte[] color) {
            XSSFRichTextString rico = new XSSFRichTextString(texto == null ? "" : texto);
            rico.applyFont(fuente(tamano, negrita, color));
            return rico;
        }

        XSSFFont fuente(int tamano, boolean negrita, byte[] color) {
            String clave = tamano + "|" + negrita + "|" + HexFormat.of().formatHex(color);
            return fuentes.computeIfAbsent(clave, k -> {
                XSSFFont fuente = libro.createFont();
                fuente.setFontName(FUENTE);
                fuente.setFontHeightInPoints((short) tamano);
                fuente.setBold(negrita);
                fuente.setColor(new XSSFColor(color, null));
                return fuente;
            });
        }
    }
}
