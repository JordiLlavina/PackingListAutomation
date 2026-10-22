package com.puntotres.packinglist.service.taller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Escribe un {@link PackingFormatoPuntotres} en el formato Puntotres: la hoja
 * "LISTE DE COLIS" con el membrete del taller arriba, la cabecera en la fila 9
 * y una fila por línea del packing, igual que el ejemplo
 * docs/Packing Lists/Packing List Excel Taller tests/Packing List Puntotres Exemple.xlsx.
 *
 * Sin plantilla {@code .xlsx}: la maquetación son las constantes de esta
 * clase. Los títulos de columna son los del ejemplo, saltos de línea
 * incluidos, porque son los que {@link TallerColisExcel} busca: el excel que
 * sale de aquí tiene que leerse exactamente igual que uno que hubiera hecho
 * el taller a mano en el formato bueno.
 *
 * Las NOTAS del taller van debajo, separadas por {@link #FILAS_ENTRE_TABLA_Y_NOTAS}
 * filas vacías. Esa separación es la que garantiza que no entran en el
 * packing: el lector se para tras cinco filas seguidas sin referencia, así
 * que una nota que por casualidad cayera en la columna REFERENCE nunca se
 * leería como artículo. Que el lector no las vea lo ancla
 * {@code FormatoPuntotresExcelBuilderTest}.
 */
public final class FormatoPuntotresExcelBuilder {

    static final String HOJA = TallerColisExcel.HOJA_COLIS;

    /** Fila (0-based) de la cabecera de la tabla: la 9 de Excel, como el ejemplo. */
    static final int FILA_CABECERA = 8;

    /**
     * Filas vacías entre la tabla (o su total) y las notas. Una más de las
     * cinco con las que se para el lector del taller, para que ninguna nota
     * llegue a mirarse.
     */
    static final int FILAS_ENTRE_TABLA_Y_NOTAS = 6;

    static final String TITULO_NOTAS = "NOTAS DEL TALLER";
    static final String AYUDA_NOTAS = "Copiadas tal cual del packing del taller. "
            + "Solo informativas: el programa no las usa para hacer el packing.";

    /** Primera columna de la tabla (B), como en el ejemplo. */
    private static final int COL_INICIO = 1;

    private static final String[] TITULOS = {
            "PUNTOTRES\nENVOI", "CLIENT", "MOTIF", "REFERENCE", "COULEUR", "TAILLE",
            "DESTINATION", "CODE CLIENT", "QTITE /\nCOLIS", "QUANTITE", "Nº DE COLIS",
            "POIDS BRUT\nCAISSE", "DIMENSIONS\nCAISSE"};

    private static final int[] ANCHOS = {
            11, 9, 9, 18, 16, 8, 16, 11, 9, 11, 12, 11, 13};

    /** Columna de QUANTITE dentro de la tabla, para el total de debajo. */
    private static final int INDICE_QUANTITE = 9;


    private FormatoPuntotresExcelBuilder() {
    }

    public static byte[] construir(PackingFormatoPuntotres packing) {
        try (Workbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            Sheet hoja = libro.createSheet(HOJA);
            for (int i = 0; i < ANCHOS.length; i++) {
                hoja.setColumnWidth(COL_INICIO + i, ANCHOS[i] * 256);
            }

            escribirMembrete(hoja, packing.cabecera(), estilos);
            escribirCabecera(hoja, estilos);
            int siguiente = escribirFilas(hoja, packing, estilos);
            siguiente = escribirTotal(hoja, packing.filas(), siguiente, estilos);
            escribirNotas(hoja, packing.notas(), siguiente + FILAS_ENTRE_TABLA_Y_NOTAS, estilos);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void escribirMembrete(Sheet hoja, PackingFormatoPuntotres.Cabecera cabecera,
                                         Estilos estilos) {
        texto(hoja, 1, 2, cabecera.taller(), estilos.negrita);
        texto(hoja, 2, 2, cabecera.direccion(), null);
        texto(hoja, 4, 2, "COLISAGE:", estilos.negrita);
        texto(hoja, 4, 3, vacioSiNulo(cabecera.factura()).isBlank() ? null
                : "Facture " + cabecera.factura(), null);
        texto(hoja, 5, 2, "DATE :", estilos.negrita);
        texto(hoja, 5, 3, cabecera.fecha(), null);
    }

    private static void escribirCabecera(Sheet hoja, Estilos estilos) {
        Row fila = hoja.createRow(FILA_CABECERA);
        fila.setHeightInPoints(32);
        for (int i = 0; i < TITULOS.length; i++) {
            Cell celda = fila.createCell(COL_INICIO + i);
            celda.setCellValue(TITULOS[i]);
            celda.setCellStyle(estilos.cabecera);
        }
    }

    /** Una celda de la tabla: fila convertida (desde 1) y columna. */
    private record Celda(int fila, PackingFormatoPuntotres.Columna columna) {
    }

    /**
     * Devuelve la primera fila libre después de la tabla. Cada aviso va a LA
     * CELDA de su columna: ahí el comentario y, si pide revisión, el
     * amarillo. Nunca la fila entera, por decisión del usuario: una fila
     * entera amarilla no dice qué dato es el dudoso, y la duda del envío
     * tiene que verse en PUNTOTRES ENVOI, no en REFERENCE.
     */
    private static int escribirFilas(Sheet hoja, PackingFormatoPuntotres packing,
                                     Estilos estilos) {
        Map<Celda, List<String>> avisosPorCelda = new HashMap<>();
        Set<Celda> celdasARevisar = new HashSet<>();
        for (PackingFormatoPuntotres.Aviso aviso : packing.avisos()) {
            if (aviso.fila() != null) {
                Celda celda = new Celda(aviso.fila(), aviso.celda());
                avisosPorCelda.computeIfAbsent(celda, c -> new ArrayList<>()).add(aviso.texto());
                if (aviso.revisar()) {
                    celdasARevisar.add(celda);
                }
            }
        }
        Drawing<?> dibujo = hoja.createDrawingPatriarch();
        CreationHelper ayudante = hoja.getWorkbook().getCreationHelper();

        int r = FILA_CABECERA + 1;
        List<PackingFormatoPuntotres.Fila> filas = packing.filas();
        for (int f = 0; f < filas.size(); f++) {
            PackingFormatoPuntotres.Fila dato = filas.get(f);
            Row fila = hoja.createRow(r++);
            // En el orden de PackingFormatoPuntotres.Columna.
            Object[] valores = {
                    dato.envio(), dato.cliente(), dato.motivo(), dato.referencia(), dato.color(),
                    dato.talla(), dato.destinacion(), dato.codigoCliente(),
                    dato.unidadesPorCaja(), dato.cantidad(), dato.cajas(),
                    dato.pesoBrutoCaja(), dato.medidaCaja()};
            for (PackingFormatoPuntotres.Columna columna : PackingFormatoPuntotres.Columna.values()) {
                Celda posicion = new Celda(f + 1, columna);
                Cell celda = fila.createCell(COL_INICIO + columna.indice());
                celda.setCellStyle(celdasARevisar.contains(posicion) ? estilos.datoConAviso
                        : estilos.dato);
                Object valor = valores[columna.indice()];
                if (valor instanceof Number numero) {
                    celda.setCellValue(numero.doubleValue());
                } else if (valor != null && !valor.toString().isBlank()) {
                    celda.setCellValue(valor.toString().trim());
                }
                List<String> avisos = avisosPorCelda.get(posicion);
                if (avisos != null) {
                    comentar(celda, avisos, dibujo, ayudante);
                }
            }
        }
        return r;
    }

    private static void comentar(Cell celda, List<String> avisos, Drawing<?> dibujo,
                                 CreationHelper ayudante) {
        ClientAnchor ancla = ayudante.createClientAnchor();
        ancla.setCol1(celda.getColumnIndex());
        ancla.setCol2(celda.getColumnIndex() + 4);
        ancla.setRow1(celda.getRowIndex());
        ancla.setRow2(celda.getRowIndex() + 2 + avisos.size());
        Comment comentario = dibujo.createCellComment(ancla);
        comentario.setString(ayudante.createRichTextString(String.join("\n", avisos)));
        comentario.setAuthor("Conversión");
        celda.setCellComment(comentario);
    }

    /**
     * El total de unidades debajo de la tabla, como en el ejemplo: una fórmula
     * y no el número, para que se recalcule si alguien corrige una fila. Va en
     * una fila sin referencia, así que el lector del taller no la cuenta.
     */
    private static int escribirTotal(Sheet hoja, List<PackingFormatoPuntotres.Fila> filas,
                                     int siguiente, Estilos estilos) {
        if (filas.isEmpty()) {
            return siguiente;
        }
        int filaTotal = siguiente + 1;
        Row fila = hoja.createRow(filaTotal);
        int columna = COL_INICIO + INDICE_QUANTITE;
        Cell rotulo = fila.createCell(columna - 1);
        rotulo.setCellValue("TOTALE");
        rotulo.setCellStyle(estilos.negrita);
        String letra = CellReference.convertNumToColString(columna);
        Cell total = fila.createCell(columna);
        total.setCellFormula("SUM(" + letra + (FILA_CABECERA + 2) + ":" + letra + siguiente + ")");
        total.setCellStyle(estilos.negrita);
        return filaTotal + 1;
    }

    private static void escribirNotas(Sheet hoja, List<List<String>> notas, int inicio,
                                      Estilos estilos) {
        if (notas.isEmpty()) {
            return;
        }
        texto(hoja, inicio, COL_INICIO, TITULO_NOTAS, estilos.tituloNotas);
        texto(hoja, inicio + 1, COL_INICIO, AYUDA_NOTAS, estilos.ayuda);
        int r = inicio + 2;
        for (List<String> nota : notas) {
            Row fila = hoja.createRow(r++);
            for (int i = 0; i < nota.size(); i++) {
                String valor = nota.get(i);
                if (valor != null && !valor.isBlank()) {
                    Cell celda = fila.createCell(COL_INICIO + i);
                    celda.setCellValue(valor.trim());
                    celda.setCellStyle(estilos.nota);
                }
            }
        }
    }

    private static void texto(Sheet hoja, int fila, int columna, String valor, CellStyle estilo) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        Row r = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell celda = r.createCell(columna);
        celda.setCellValue(valor.trim());
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    private static String vacioSiNulo(String texto) {
        return texto == null ? "" : texto;
    }

    private static final class Estilos {
        final CellStyle cabecera;
        final CellStyle dato;
        final CellStyle datoConAviso;
        final CellStyle negrita;
        final CellStyle tituloNotas;
        final CellStyle ayuda;
        final CellStyle nota;

        Estilos(Workbook libro) {
            Font fuenteNegrita = libro.createFont();
            fuenteNegrita.setBold(true);

            cabecera = libro.createCellStyle();
            cabecera.setFont(fuenteNegrita);
            cabecera.setWrapText(true);
            cabecera.setAlignment(HorizontalAlignment.CENTER);
            cabecera.setVerticalAlignment(VerticalAlignment.CENTER);
            cabecera.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bordes(cabecera);

            dato = libro.createCellStyle();
            dato.setAlignment(HorizontalAlignment.CENTER);
            bordes(dato);

            datoConAviso = libro.createCellStyle();
            datoConAviso.cloneStyleFrom(dato);
            datoConAviso.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            datoConAviso.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            negrita = libro.createCellStyle();
            negrita.setFont(fuenteNegrita);

            Font fuenteTitulo = libro.createFont();
            fuenteTitulo.setBold(true);
            fuenteTitulo.setFontHeightInPoints((short) 12);
            tituloNotas = libro.createCellStyle();
            tituloNotas.setFont(fuenteTitulo);

            Font fuenteAyuda = libro.createFont();
            fuenteAyuda.setItalic(true);
            fuenteAyuda.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            ayuda = libro.createCellStyle();
            ayuda.setFont(fuenteAyuda);

            nota = libro.createCellStyle();
            bordes(nota);
        }

        private static void bordes(CellStyle estilo) {
            estilo.setBorderTop(BorderStyle.THIN);
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBorderLeft(BorderStyle.THIN);
            estilo.setBorderRight(BorderStyle.THIN);
        }
    }
}
