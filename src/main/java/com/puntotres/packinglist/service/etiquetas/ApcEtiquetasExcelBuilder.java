package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Escribe el excel de etiquetas de UNA destinación de APC a partir de su
 * plantilla real (client-labels/apc-etiquetas-*.xlsx). Se conservan las DOS
 * hojas del libro: en la de cajas se replica el par de etiquetas modelo por
 * caja física y en la de palet la etiqueta modelo por palet, cada bloque
 * con su salto de página (un A4 por par de caja y por palet). El logo de la
 * plantilla se clona en cada bloque copiado leyendo sus anclajes reales.
 */
@Service
public class ApcEtiquetasExcelBuilder {

    /** A4 en puntos (1/72"), que es la unidad de los altos de fila de POI. */
    private static final double ALTO_A4_PT = 841.89;
    private static final double ANCHO_A4_PT = 595.28;
    private static final double PUNTOS_POR_PULGADA = 72;
    /** POI mide los anchos de columna en píxeles a 96 ppp. */
    private static final double PUNTOS_POR_PIXEL = 72.0 / 96.0;

    /**
     * Datos ya formateados de la etiqueta de una caja. null = en blanco,
     * salvo destino: null ahí significa "lo que ya dice la plantilla", que es
     * lo normal; solo trae valor cuando la caja lleva material de varias
     * destinaciones hijas ("WHOLESALE / AUSTRALIA").
     */
    public record EtiquetaCajaApc(String orderNumber, String livraisonCode, String referencia,
                                  String colour, String size, String piecesBySize,
                                  String colisage, String poidsBrut, String destino) {
    }

    /**
     * Datos de la etiqueta de un palet. poidsBrut null = en blanco. destino,
     * como en la caja: null = lo que dice la plantilla; solo trae valor
     * cuando el palet lleva cajas de varias destinaciones hijas.
     */
    public record EtiquetaPaletApc(int numeroPalet, int numeroCajas, String poidsBrut,
                                   String destino) {
    }

    public byte[] generar(ApcEtiquetaLayout layout, List<EtiquetaCajaApc> cajas,
                          List<EtiquetaPaletApc> palets) throws IOException {
        try (InputStream plantilla = getClass().getResourceAsStream(layout.rutaPlantilla());
             XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {
            AjusteFuente ajuste = new AjusteFuente(libro);
            XSSFSheet hojaCajas = hoja(libro, layout.hojaCajas(), layout);
            escribirHojaCajas(hojaCajas, layout, cajas, ajuste);
            // Sin ningún palet que etiquetar la hoja no se conserva: la
            // etiqueta modelo de la plantilla se imprimiría en blanco y se
            // pegaría en un bulto igual que una buena.
            XSSFSheet hojaPalet = palets.isEmpty() ? null : hoja(libro, layout.hojaPalet(), layout);
            if (palets.isEmpty()) {
                quitarHojaPalets(libro, layout);
            } else {
                escribirHojaPalets(hojaPalet, layout, palets, new NumeroPaletEtiqueta(libro), ajuste);
            }
            // Al final y sobre lo que queda vivo en el libro: quitar una hoja
            // remapea los índices, y el área de impresión se pide por índice.
            ajustarImpresion(libro, hojaCajas, layout.alturaBloque(), cajas.size());
            if (hojaPalet != null) {
                ajustarImpresion(libro, hojaPalet, layout.palet().altura(), palets.size());
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static XSSFSheet hoja(XSSFWorkbook libro, String nombre, ApcEtiquetaLayout layout) {
        XSSFSheet hoja = libro.getSheet(nombre);
        if (hoja == null) {
            throw new IllegalStateException("La plantilla " + layout.rutaPlantilla()
                    + " no tiene la hoja '" + nombre + "': revisar client-labels/");
        }
        return hoja;
    }

    private static void escribirHojaCajas(XSSFSheet hoja, ApcEtiquetaLayout layout,
                                          List<EtiquetaCajaApc> cajas, AjusteFuente ajuste) {
        if (cajas.isEmpty()) {
            return;
        }
        BloqueEtiquetaModelo modelo = BloqueEtiquetaModelo.capturar(hoja, layout.alturaBloque());
        for (int i = 1; i < cajas.size(); i++) {
            modelo.copiarEn(hoja, i * layout.alturaBloque());
        }
        replicarImagenes(hoja, layout.alturaBloque(), cajas.size());
        for (int i = 0; i < cajas.size(); i++) {
            int base = i * layout.alturaBloque();
            escribirEtiquetaCaja(hoja, layout, base, cajas.get(i), ajuste);
            escribirEtiquetaCaja(hoja, layout, base + layout.offsetSegundaEtiqueta(),
                    cajas.get(i), ajuste);
            if (i < cajas.size() - 1) {
                hoja.setRowBreak(base + layout.alturaBloque() - 1);
            }
        }
    }

    private static void escribirEtiquetaCaja(XSSFSheet hoja, ApcEtiquetaLayout layout,
                                             int base, EtiquetaCajaApc etiqueta,
                                             AjusteFuente ajuste) {
        int col = layout.colValor();
        // DESTINATION solo se reescribe cuando la caja lleva varias
        // destinaciones hijas y la plantilla rotula ahí la destinación (no
        // un aeropuerto): si no, se queda lo que dice la plantilla.
        if (layout.filaDestino() != null && etiqueta.destino() != null) {
            ajuste.ajustar(escribir(hoja, base + layout.filaDestino(), col, etiqueta.destino()));
        }
        // El Order N° también lleva un valor por artículo desde que una caja
        // mixta los enseña todos, así que encoge igual que la referencia.
        ajuste.ajustar(escribir(hoja, base + layout.filaOrder(), col, etiqueta.orderNumber()));
        escribir(hoja, base + layout.filaLivraison(), col, etiqueta.livraisonCode());
        // Estas tres pueden llevar varios artículos concatenados y crecer.
        ajuste.ajustar(escribir(hoja, base + layout.filaReferencia(), col, etiqueta.referencia()));
        ajuste.ajustar(escribir(hoja, base + layout.filaColor(), col, etiqueta.colour()));
        escribir(hoja, base + layout.filaTalla(), col, etiqueta.size());
        ajuste.ajustar(escribir(hoja, base + layout.filaPiezas(), col, etiqueta.piecesBySize()));
        escribir(hoja, base + layout.filaColisage(), col, etiqueta.colisage());
        escribir(hoja, base + layout.filaPeso(), col, etiqueta.poidsBrut());
    }

    private static Cell escribir(XSSFSheet hoja, int fila, int col, String valor) {
        Cell celda = celda(hoja, fila, col);
        if (valor == null || valor.isBlank()) {
            celda.setBlank();
        } else {
            celda.setCellValue(valor);
        }
        return celda;
    }

    private static Cell celda(XSSFSheet hoja, int fila, int col) {
        XSSFRow f = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        return f.getCell(col) != null ? f.getCell(col) : f.createCell(col);
    }

    /**
     * Clona las imágenes de la plantilla (el logo del cliente) en cada
     * bloque copiado, desplazando sus anclajes reales i*alturaBloque filas:
     * así no hay que hardcodear EMUs por plantilla como en AMI.
     */
    private static void replicarImagenes(XSSFSheet hoja, int alturaBloque, int numBloques) {
        XSSFDrawing dibujo = hoja.getDrawingPatriarch();
        if (dibujo == null || numBloques <= 1) {
            return;
        }
        List<XSSFPicture> originales = new ArrayList<>();
        for (XSSFShape forma : dibujo.getShapes()) {
            if (forma instanceof XSSFPicture imagen) {
                originales.add(imagen);
            }
        }
        for (XSSFPicture imagen : originales) {
            XSSFClientAnchor origen = imagen.getClientAnchor();
            int indice = hoja.getWorkbook().addPicture(imagen.getPictureData().getData(),
                    imagen.getPictureData().getPictureType());
            for (int i = 1; i < numBloques; i++) {
                XSSFClientAnchor ancla = new XSSFClientAnchor(
                        origen.getDx1(), origen.getDy1(), origen.getDx2(), origen.getDy2(),
                        origen.getCol1(), origen.getRow1() + i * alturaBloque,
                        origen.getCol2(), origen.getRow2() + i * alturaBloque);
                ancla.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
                dibujo.createPicture(ancla, indice);
            }
        }
    }

    /**
     * Deja el libro con una sola hoja, la de cajas. La plantilla trae una
     * hoja marcada como seleccionada: con la de palets fuera hay que dejar
     * seleccionada la que queda, o Excel abre el libro sin pestaña activa.
     */
    private static void quitarHojaPalets(XSSFWorkbook libro, ApcEtiquetaLayout layout) {
        libro.removeSheetAt(libro.getSheetIndex(hoja(libro, layout.hojaPalet(), layout)));
        for (int i = 0; i < libro.getNumberOfSheets(); i++) {
            libro.getSheetAt(i).setSelected(i == 0);
        }
        libro.setActiveSheet(0);
    }

    private static void escribirHojaPalets(XSSFSheet hoja, ApcEtiquetaLayout layout,
                                           List<EtiquetaPaletApc> palets,
                                           NumeroPaletEtiqueta numeroPalet, AjusteFuente ajuste) {
        ApcEtiquetaLayout.Palet geo = layout.palet();
        limpiarContadorManual(hoja);
        BloqueEtiquetaModelo modelo = BloqueEtiquetaModelo.capturar(hoja, geo.altura());
        for (int i = 1; i < palets.size(); i++) {
            modelo.copiarEn(hoja, i * geo.altura());
        }
        replicarImagenes(hoja, geo.altura(), palets.size());
        for (int i = 0; i < palets.size(); i++) {
            int base = i * geo.altura();
            // "Nº3" arriba a la derecha, en la fila donde el cliente apuntaba
            // a mano su contador (que limpiarContadorManual ya ha blanqueado).
            numeroPalet.escribir(hoja, base, geo.colNumeroPalet(), palets.get(i).numeroPalet());
            // DESTINATION, igual que en la caja: solo con varias destinaciones
            // hijas en el palet y donde la plantilla rotula la destinación.
            if (geo.filaDestino() != null && palets.get(i).destino() != null) {
                ajuste.ajustar(escribir(hoja, base + geo.filaDestino(), geo.colValor(),
                        palets.get(i).destino()));
            }
            celda(hoja, base + geo.filaNumCajas(), geo.colValor())
                    .setCellValue(palets.get(i).numeroCajas());
            escribir(hoja, base + geo.filaPeso(), geo.colValor(), palets.get(i).poidsBrut());
            if (i < palets.size() - 1) {
                hoja.setRowBreak(base + geo.altura() - 1);
            }
        }
    }

    /**
     * La fila 1 de la hoja de palet trae una celda suelta con un contador
     * apuntado a mano (E1/C1/D1 según plantilla) que no debe replicarse. Su
     * sitio lo ocupa ahora el número de palet que escribe el builder.
     */
    private static void limpiarContadorManual(XSSFSheet hoja) {
        if (hoja.getRow(0) != null) {
            hoja.getRow(0).forEach(Cell::setBlank);
        }
    }

    /**
     * Deja la hoja imprimiendo UN bloque por A4, que es lo que se ajustó a
     * mano sobre las plantillas y lo que hay que reproducir con N bloques.
     *
     * <p>Dos cosas, y ninguna se puede copiar tal cual de la plantilla:
     *
     * <p>El <b>área de impresión</b> de la plantilla cubre su único bloque,
     * así que se estira hasta la última fila escrita conservando las
     * columnas que eligió el cliente (en WHOLESALE la hoja de palet llega
     * hasta la B y las demás hasta la D). Sin esto solo se imprimiría la
     * primera caja.
     *
     * <p>El <b>ajuste de página</b> de la plantilla es "ajustar todas las
     * filas en una página", que sobre un bloque es justo lo que se quiere
     * pero sobre N le pide a Excel que meta TODAS las etiquetas en un solo
     * A4, y de paso ignora los saltos de página. Se traduce a la escala fija
     * que hace que quepa un bloque, calculada de las medidas reales del
     * bloque y de los márgenes de la hoja; los saltos por bloque ya están
     * puestos. No se amplía por encima del 100%, como tampoco lo hace Excel.
     *
     * <p>La escala mira el <b>alto y el ancho</b>, y hace falta mirar los
     * dos: en las hojas de cajas manda el alto (el par de etiquetas llena la
     * página a lo largo), pero en las de palet la etiqueta sobra de alto y lo
     * que se sale es el ancho, así que con solo el alto saldría al 100% y se
     * partiría en dos hojas por el lado derecho.
     */
    private static void ajustarImpresion(XSSFWorkbook libro, XSSFSheet hoja,
                                         int alturaBloque, int numBloques) {
        if (numBloques <= 0) {
            return;
        }
        int indice = libro.getSheetIndex(hoja);
        String areaActual = libro.getPrintArea(indice);
        if (areaActual == null) {
            return;
        }
        AreaReference area = new AreaReference(areaActual, SpreadsheetVersion.EXCEL2007);
        int primeraCol = area.getFirstCell().getCol();
        int ultimaCol = area.getLastCell().getCol();
        libro.setPrintArea(indice, primeraCol, ultimaCol,
                area.getFirstCell().getRow(), numBloques * alturaBloque - 1);

        double alto = 0;
        for (int f = 0; f < alturaBloque; f++) {
            alto += hoja.getRow(f) != null
                    ? hoja.getRow(f).getHeightInPoints() : hoja.getDefaultRowHeightInPoints();
        }
        double ancho = 0;
        for (int c = primeraCol; c <= ultimaCol; c++) {
            ancho += hoja.getColumnWidthInPixels(c) * PUNTOS_POR_PIXEL;
        }
        if (alto <= 0 || ancho <= 0) {
            return;
        }
        boolean apaisado = hoja.getPrintSetup().getLandscape();
        double utilAlto = (apaisado ? ANCHO_A4_PT : ALTO_A4_PT)
                - (hoja.getMargin(Sheet.TopMargin)
                        + hoja.getMargin(Sheet.BottomMargin)) * PUNTOS_POR_PULGADA;
        double utilAncho = (apaisado ? ALTO_A4_PT : ANCHO_A4_PT)
                - (hoja.getMargin(Sheet.LeftMargin)
                        + hoja.getMargin(Sheet.RightMargin)) * PUNTOS_POR_PULGADA;
        int escala = (int) Math.floor(100 * Math.min(utilAlto / alto, utilAncho / ancho));
        hoja.setFitToPage(false);
        hoja.getPrintSetup().setScale((short) Math.max(10, Math.min(100, escala)));
    }
}
