package com.puntotres.packinglist.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.PaletData;

/**
 * Escribe el <b>Packing Puntotres</b>: un documento Word con toda la
 * información del packing ordenada y comprimida, pensado para imprimirlo y que
 * el operario lo tenga delante mientras hace las cajas.
 *
 * No es un documento para el cliente —eso son los packing lists— sino la hoja
 * de trabajo del almacén cuando el packing lo ha repartido el programa
 * (entrada por packing list de taller) y las cajas aún están por hacer. Por eso
 * lo que manda es el orden físico del trabajo: por destinación, dentro de ella
 * por palet y dentro del palet por número de caja.
 *
 * Comprimido como la tabla de revisión: un tramo de cajas consecutivas
 * iguales (misma referencia, color, talla, pedido, cantidad, cartón y palet)
 * es una sola línea con su rango ({@code 4-8}), y una línea nunca dice nada
 * que no sea cierto de todas sus cajas. Un bulto con varios artículos dentro
 * sale línea a línea, que es como se va a llenar.
 *
 * Maquetación en blanco y negro, para imprimir: ningún relleno, la jerarquía
 * la hacen el peso de la letra y los filetes horizontales —uno fino entre
 * cajas, uno grueso bajo la cabecera y sobre cada palet— y no hay filetes
 * verticales. Los pesos no salen a propósito: en un packing recién generado
 * casi nunca están, y el operario no los necesita para llenar las cajas. Sin
 * plantilla {@code .docx}: la maquetación son las constantes de esta clase.
 */
@Service
public class PackingPuntotresDocBuilder {

    /** A4 vertical en veinteavos de punto, con márgenes de 1,5 cm. */
    private static final int ANCHO_A4 = 11906;
    private static final int ALTO_A4 = 16838;
    private static final int MARGEN = 850;

    private static final String FUENTE = "Calibri";
    /** 18 y no más: la línea de cabecera entera tiene que caber en un renglón. */
    private static final int TAMANO_TITULO = 18;
    private static final int TAMANO_DESTINO = 13;
    private static final int TAMANO_RECUENTO = 14;
    private static final int TAMANO_TEXTO = 9;

    /** Filetes en octavos de punto: fino entre cajas, grueso bajo cabecera y sobre palet. */
    private static final int FILETE_FINO = 4;
    private static final int FILETE_GRUESO = 12;
    private static final String NEGRO = "000000";
    private static final String GRIS_FILETE = "A6A6A6";

    /** Sangría interior de las celdas (twips): aire arriba y abajo, poco a los lados. */
    private static final int MARGEN_CELDA_VERTICAL = 70;
    private static final int MARGEN_CELDA_HORIZONTAL = 60;

    private static final String ESTILO_TITULO = "Title";

    private static final String[] CABECERAS = {
            "Cajas", "Nº", "Referencia", "Color", "Talla", "Uds/caja", "Total", "Caja"};
    /** Anchos en porcentaje; suman 100. */
    private static final String[] ANCHOS = {
            "11%", "6%", "24%", "24%", "8%", "9%", "8%", "10%"};
    /** Columnas numéricas, alineadas a la derecha para que las cifras se sumen a ojo. */
    private static final boolean[] NUMERICA = {
            false, true, false, false, false, true, true, false};

    private static final String[] CABECERAS_RESUMEN = {"Destinación", "Cajas", "Palets", "Unidades"};
    private static final String[] ANCHOS_RESUMEN = {"46%", "18%", "18%", "18%"};
    private static final boolean[] NUMERICA_RESUMEN = {false, true, true, true};

    /**
     * Una destinación del envío tal como la conoce el documento: sus cajas y
     * los palets declarados, de los que solo se leen las medidas.
     */
    public record Destino(String nombre, List<CajaData> cajas, List<PaletData> palets) {
    }

    public byte[] generar(DatosEnvio cabecera, String nombreCliente, List<Destino> destinos)
            throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            configurarPagina(doc);
            definirEstiloTitulo(doc);
            escribirCabecera(doc, cabecera, nombreCliente);
            escribirResumen(doc, destinos);
            for (Destino destino : destinos) {
                escribirDestino(doc, destino);
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            doc.write(salida);
            return salida.toByteArray();
        }
    }

    // --- Cabecera del documento ---

    private static void configurarPagina(XWPFDocument doc) {
        CTSectPr seccion = doc.getDocument().getBody().addNewSectPr();
        CTPageSz tamano = seccion.addNewPgSz();
        tamano.setW(BigInteger.valueOf(ANCHO_A4));
        tamano.setH(BigInteger.valueOf(ALTO_A4));
        CTPageMar margen = seccion.addNewPgMar();
        margen.setTop(BigInteger.valueOf(MARGEN));
        margen.setBottom(BigInteger.valueOf(MARGEN));
        margen.setLeft(BigInteger.valueOf(MARGEN));
        margen.setRight(BigInteger.valueOf(MARGEN));
    }

    /**
     * Un documento nuevo de POI no trae estilos, así que el "Título" de Word
     * se declara aquí para que la línea de cabecera sea un Título de verdad
     * (sale como tal en el panel de estilos) y no solo texto grande.
     */
    private static void definirEstiloTitulo(XWPFDocument doc) {
        XWPFStyles estilos = doc.createStyles();
        CTStyle estilo = CTStyle.Factory.newInstance();
        estilo.setType(STStyleType.PARAGRAPH);
        estilo.setStyleId(ESTILO_TITULO);
        estilo.addNewName().setVal("Title");
        estilo.addNewQFormat();
        estilo.addNewPPr().addNewSpacing().setAfter(BigInteger.valueOf(200));
        var rPr = estilo.addNewRPr();
        rPr.addNewB();
        rPr.addNewSz().setVal(BigInteger.valueOf(TAMANO_TITULO * 2L));
        rPr.addNewRFonts().setAscii(FUENTE);
        estilos.addStyle(new org.apache.poi.xwpf.usermodel.XWPFStyle(estilo, estilos));
    }

    /**
     * La cabecera es una sola línea en estilo Título: cliente, temporada y
     * fecha de envío. Sin rótulo de documento —el papel se explica solo— y
     * sin la factura, que es un dato de facturación y no del trabajo.
     */
    private static void escribirCabecera(XWPFDocument doc, DatosEnvio cabecera, String nombreCliente) {
        XWPFParagraph titulo = doc.createParagraph();
        titulo.setStyle(ESTILO_TITULO);
        titulo.setSpacingAfter(200);
        texto(titulo, String.join("   ·   ", sinVacios(
                dato("Cliente", nombreCliente),
                dato("Temporada", cabecera.getTemporada()),
                dato("Fecha envío", cabecera.getFechaEnvio()))), TAMANO_TITULO, true);
    }

    private static String dato(String rotulo, String valor) {
        return valor == null || valor.isBlank() ? null : rotulo + ": " + valor;
    }

    private static List<String> sinVacios(String... piezas) {
        List<String> lista = new ArrayList<>();
        for (String pieza : piezas) {
            if (pieza != null) {
                lista.add(pieza);
            }
        }
        return lista;
    }

    /** Una línea por destinación con sus recuentos, y el total del envío. */
    private static void escribirResumen(XWPFDocument doc, List<Destino> destinos) {
        XWPFTable tabla = nuevaTabla(doc, CABECERAS_RESUMEN, ANCHOS_RESUMEN, NUMERICA_RESUMEN);

        int cajas = 0;
        int palets = 0;
        int unidades = 0;
        for (Destino destino : destinos) {
            Recuento recuento = Recuento.de(destino.cajas());
            filaSimple(tabla.createRow(), ANCHOS_RESUMEN, NUMERICA_RESUMEN, false,
                    destino.nombre(), String.valueOf(recuento.cajas),
                    String.valueOf(recuento.palets), String.valueOf(recuento.unidades));
            cajas += recuento.cajas;
            palets += recuento.palets;
            unidades += recuento.unidades;
        }
        XWPFTableRow total = filaSimple(tabla.createRow(), ANCHOS_RESUMEN, NUMERICA_RESUMEN, true,
                "TOTAL ENVÍO", String.valueOf(cajas), String.valueOf(palets), String.valueOf(unidades));
        for (XWPFTableCell celda : total.getTableCells()) {
            filete(celda, true, FILETE_GRUESO, NEGRO);
        }
        // Sin párrafo vacío detrás: el aire hasta la primera destinación lo
        // pone el spacingBefore de su título, y un renglón suelto aquí lo
        // duplicaba. Nada se pega, porque entre las dos tablas siempre queda
        // ese título —dos tablas seguidas sin párrafo en medio las fundiría
        // Word en una sola.
    }

    private record Recuento(int cajas, int palets, int unidades) {
        static Recuento de(List<CajaData> cajas) {
            List<Integer> numerosCaja = new ArrayList<>();
            List<Integer> numerosPalet = new ArrayList<>();
            int unidades = 0;
            for (CajaData caja : cajas) {
                if (!numerosCaja.contains(caja.getNumeroCaja())) {
                    numerosCaja.add(caja.getNumeroCaja());
                }
                Integer palet = caja.getNumeroPalet();
                if (palet != null && !CajaData.vaSuelta(palet) && !numerosPalet.contains(palet)) {
                    numerosPalet.add(palet);
                }
                unidades += caja.getCantidad();
            }
            return new Recuento(numerosCaja.size(), numerosPalet.size(), unidades);
        }
    }

    // --- Una destinación ---

    /**
     * El título de la destinación: el nombre en negrita y, tras dos
     * tabuladores, el recuento en letra normal y más pequeña. Sin guiones ni
     * separadores: el espacio y el peso de la letra ya separan.
     */
    private static void escribirDestino(XWPFDocument doc, Destino destino) {
        Recuento recuento = Recuento.de(destino.cajas());
        XWPFParagraph titulo = doc.createParagraph();
        titulo.setSpacingBefore(320);
        titulo.setSpacingAfter(100);
        titulo.setKeepNext(true);
        texto(titulo, destino.nombre().toUpperCase(), TAMANO_DESTINO, true);
        XWPFRun tabuladores = titulo.createRun();
        tabuladores.addTab();
        tabuladores.addTab();
        texto(titulo, recuento.cajas + " cajas · " + recuento.palets + " palets · "
                + recuento.unidades + " uds", TAMANO_RECUENTO, false);

        XWPFTable tabla = nuevaTabla(doc, CABECERAS, ANCHOS, NUMERICA);
        Map<String, String> medidasPorPalet = medidasPorPalet(destino.palets());
        for (Map.Entry<Integer, List<Linea>> palet : lineasPorPalet(destino.cajas()).entrySet()) {
            filaPalet(tabla.createRow(), rotuloPalet(palet.getKey(), palet.getValue(),
                    medidasPorPalet.get(String.valueOf(palet.getKey()))));
            for (Linea linea : palet.getValue()) {
                filaSimple(tabla.createRow(), ANCHOS, NUMERICA, false, linea.celdas());
            }
        }
    }

    private static Map<String, String> medidasPorPalet(List<PaletData> palets) {
        Map<String, String> medidas = new HashMap<>();
        if (palets != null) {
            for (PaletData palet : palets) {
                if (palet.getMedidas() != null && !palet.getMedidas().isBlank()) {
                    medidas.put(String.valueOf(palet.getNumeroPalet()), palet.getMedidas());
                }
            }
        }
        return medidas;
    }

    /**
     * "PALET 1 · cajas 1-12 · 12 cajas · 240 uds · 80x120x130". El rango es el
     * de las cajas de verdad, no el {@code cajaInicio..cajaFin} del palet
     * declarado, que se queda viejo en cuanto se corrige un palet en la
     * revisión. Las cajas que van sueltas y las que aún no tienen palet
     * llevan su propio rótulo: las dos son un dato, no un hueco.
     */
    private static String rotuloPalet(Integer palet, List<Linea> lineas, String medidas) {
        List<Integer> numeros = new ArrayList<>();
        int unidades = 0;
        for (Linea linea : lineas) {
            for (int numero = linea.primeraCaja; numero <= linea.ultimaCaja; numero++) {
                if (!numeros.contains(numero)) {
                    numeros.add(numero);
                }
            }
            unidades += linea.totalUnidades();
        }
        String nombre;
        if (palet == null) {
            nombre = "SIN PALET ASIGNADO";
        } else if (CajaData.vaSuelta(palet)) {
            nombre = "SIN PALET (cajas sueltas)";
        } else {
            nombre = "PALET " + palet;
        }
        boolean una = numeros.size() == 1;
        StringBuilder rotulo = new StringBuilder(nombre)
                .append(una ? " · caja " : " · cajas ").append(rangoDe(numeros))
                .append(" · ").append(numeros.size()).append(una ? " caja" : " cajas")
                .append(" · ").append(unidades).append(" uds");
        if (medidas != null) {
            rotulo.append(" · ").append(medidas);
        }
        return rotulo.toString();
    }

    /** "1-12", o "1-4, 7" si el palet tiene huecos: nunca un rango que tape uno. */
    private static String rangoDe(List<Integer> numeros) {
        List<Integer> ordenados = new ArrayList<>(numeros);
        ordenados.sort(Comparator.naturalOrder());
        StringBuilder texto = new StringBuilder();
        int i = 0;
        while (i < ordenados.size()) {
            int inicio = ordenados.get(i);
            int fin = inicio;
            while (i + 1 < ordenados.size() && ordenados.get(i + 1) == fin + 1) {
                fin = ordenados.get(++i);
            }
            if (texto.length() > 0) {
                texto.append(", ");
            }
            texto.append(inicio == fin ? String.valueOf(inicio) : inicio + "-" + fin);
            i++;
        }
        return texto.toString();
    }

    // --- Compactación ---

    /**
     * Una línea del documento: un tramo de cajas iguales o una línea de un
     * bulto mixto. {@code cuentaLaCaja} distingue las dos cosas: en un bulto
     * con varios artículos solo la primera línea cuenta la caja en la
     * columna Nº, o el recuento sumaría un bulto por artículo.
     */
    private record Linea(int primeraCaja, int ultimaCaja, int cajas, CajaData muestra,
                         boolean cuentaLaCaja) {

        int totalUnidades() {
            return muestra.getCantidad() * cajas;
        }

        String[] celdas() {
            return new String[] {
                    primeraCaja == ultimaCaja ? String.valueOf(primeraCaja)
                            : primeraCaja + "-" + ultimaCaja,
                    cuentaLaCaja ? String.valueOf(cajas) : "",
                    textoDe(muestra.getReferencia()),
                    textoDe(muestra.getCodigoColor()),
                    textoDe(muestra.getTalla()),
                    String.valueOf(muestra.getCantidad()),
                    String.valueOf(totalUnidades()),
                    textoDe(muestra.getTamanoCaja())};
        }
    }

    /**
     * Las líneas de la destinación agrupadas por palet, en el orden en que se
     * van a hacer: los palets de menor a mayor, las sueltas después y las
     * que no tienen palet al final; dentro de cada palet por número de caja.
     * La ordenación es estable para que las líneas de un mismo bulto mixto
     * sigan juntas y en el orden del packing list.
     */
    static Map<Integer, List<Linea>> lineasPorPalet(List<CajaData> cajas) {
        Map<Integer, Integer> lineasPorCaja = new HashMap<>();
        for (CajaData caja : cajas) {
            lineasPorCaja.merge(caja.getNumeroCaja(), 1, Integer::sum);
        }
        List<CajaData> ordenadas = new ArrayList<>(cajas);
        ordenadas.sort(Comparator
                .comparingInt(PackingPuntotresDocBuilder::ordenDePalet)
                .thenComparingInt(CajaData::getNumeroCaja));

        Map<Integer, List<Linea>> porPalet = new LinkedHashMap<>();
        int i = 0;
        while (i < ordenadas.size()) {
            CajaData primera = ordenadas.get(i);
            int ultimoNumero = primera.getNumeroCaja();
            int cajasDelTramo = 1;
            boolean bultoMixto = lineasPorCaja.get(primera.getNumeroCaja()) > 1;
            int j = i + 1;
            if (!bultoMixto) {
                while (j < ordenadas.size()
                        && lineasPorCaja.get(ordenadas.get(j).getNumeroCaja()) == 1
                        && ordenadas.get(j).getNumeroCaja() == ultimoNumero + 1
                        && sonEquivalentes(primera, ordenadas.get(j))) {
                    ultimoNumero = ordenadas.get(j).getNumeroCaja();
                    cajasDelTramo++;
                    j++;
                }
            }
            // En un bulto mixto solo la primera línea (la de arriba en el
            // packing list) cuenta la caja en la columna Nº.
            boolean cuentaLaCaja = !bultoMixto
                    || i == 0 || ordenadas.get(i - 1).getNumeroCaja() != primera.getNumeroCaja()
                    || !Objects.equals(ordenadas.get(i - 1).getNumeroPalet(), primera.getNumeroPalet());
            porPalet.computeIfAbsent(primera.getNumeroPalet(), k -> new ArrayList<>())
                    .add(new Linea(primera.getNumeroCaja(), ultimoNumero, cajasDelTramo, primera,
                            cuentaLaCaja));
            i = j;
        }
        return porPalet;
    }

    /** Palets en orden, las sueltas (0) tras el último palet y las sin palet al final. */
    private static int ordenDePalet(CajaData caja) {
        Integer palet = caja.getNumeroPalet();
        if (palet == null) {
            return Integer.MAX_VALUE;
        }
        return CajaData.vaSuelta(palet) ? Integer.MAX_VALUE - 1 : palet;
    }

    /**
     * Lo que la línea enseña o implica. Los pesos no se comparan porque no se
     * imprimen; el pedido sí, aunque tampoco salga: dos pedidos distintos son
     * dos bultos que el almacén separa.
     */
    private static boolean sonEquivalentes(CajaData a, CajaData b) {
        return Objects.equals(a.getReferencia(), b.getReferencia())
                && Objects.equals(a.getCodigoColor(), b.getCodigoColor())
                && Objects.equals(a.getTalla(), b.getTalla())
                && Objects.equals(a.getNumeroPedido(), b.getNumeroPedido())
                && a.getCantidad() == b.getCantidad()
                && Objects.equals(a.getTamanoCaja(), b.getTamanoCaja())
                && Objects.equals(a.getNumeroPalet(), b.getNumeroPalet());
    }

    private static String textoDe(String valor) {
        return valor == null ? "" : valor;
    }

    // --- Piezas de maquetación ---

    /**
     * Tabla a todo el ancho con su fila de cabecera. Sin bordes exteriores ni
     * verticales: solo un filete fino entre filas, que cada fila puede
     * sustituir por uno grueso (cabecera, palets, total).
     */
    private static XWPFTable nuevaTabla(XWPFDocument doc, String[] rotulos, String[] anchos,
                                        boolean[] numerica) {
        XWPFTable tabla = doc.createTable(1, rotulos.length);
        tabla.setWidth("100%");
        tabla.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, NEGRO);
        tabla.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, NEGRO);
        tabla.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, NEGRO);
        tabla.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, NEGRO);
        tabla.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, NEGRO);
        tabla.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, FILETE_FINO, 0, GRIS_FILETE);
        tabla.setCellMargins(MARGEN_CELDA_VERTICAL, MARGEN_CELDA_HORIZONTAL,
                MARGEN_CELDA_VERTICAL, MARGEN_CELDA_HORIZONTAL);

        XWPFTableRow fila = tabla.getRow(0);
        fila.setRepeatHeader(true);
        fila.setCantSplitRow(true);
        for (int i = 0; i < rotulos.length; i++) {
            XWPFTableCell celda = fila.getCell(i);
            celda.setWidth(anchos[i]);
            filete(celda, false, FILETE_GRUESO, NEGRO);
            texto(parrafo(celda, numerica[i]), rotulos[i], TAMANO_TEXTO, true);
        }
        return tabla;
    }

    private static XWPFTableRow filaSimple(XWPFTableRow fila, String[] anchos, boolean[] numerica,
                                           boolean negrita, String... valores) {
        fila.setCantSplitRow(true);
        for (int i = 0; i < valores.length; i++) {
            XWPFTableCell celda = i < fila.getTableCells().size() ? fila.getCell(i) : fila.addNewTableCell();
            celda.setWidth(anchos[i]);
            texto(parrafo(celda, numerica[i]), valores[i], TAMANO_TEXTO, negrita);
        }
        return fila;
    }

    /**
     * Una fila que ocupa toda la tabla con el rótulo del palet: en negrita y
     * con un filete grueso encima, que es lo que separa un palet del anterior.
     */
    private static void filaPalet(XWPFTableRow fila, String rotulo) {
        fila.setCantSplitRow(true);
        XWPFTableCell celda = fila.getCell(0);
        celda.setWidth("100%");
        filete(celda, true, FILETE_GRUESO, NEGRO);
        celda.getCTTc().getTcPr().addNewGridSpan().setVal(BigInteger.valueOf(CABECERAS.length));
        XWPFParagraph parrafo = parrafo(celda, false);
        parrafo.setSpacingBefore(80);
        texto(parrafo, rotulo, TAMANO_TEXTO, true);
        // POI crea la fila con tantas celdas como la tabla; las que tapa la
        // combinación sobran, o Word pintaría columnas de más a la derecha.
        while (fila.getTableCells().size() > 1) {
            fila.removeCell(1);
        }
    }

    /** Filete de una celda, arriba o abajo; pisa el fino que la tabla pone entre filas. */
    private static void filete(XWPFTableCell celda, boolean arriba, int grosor, String color) {
        CTTcPr tcPr = celda.getCTTc().isSetTcPr() ? celda.getCTTc().getTcPr()
                : celda.getCTTc().addNewTcPr();
        CTTcBorders bordes = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        CTBorder borde = arriba ? bordes.addNewTop() : bordes.addNewBottom();
        borde.setVal(STBorder.SINGLE);
        borde.setSz(BigInteger.valueOf(grosor));
        borde.setSpace(BigInteger.ZERO);
        borde.setColor(color);
    }

    private static XWPFParagraph parrafo(XWPFTableCell celda, boolean numerica) {
        XWPFParagraph parrafo = celda.getParagraphs().isEmpty()
                ? celda.addParagraph() : celda.getParagraphs().get(0);
        parrafo.setSpacingBefore(0);
        parrafo.setSpacingAfter(0);
        parrafo.setAlignment(numerica ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
        return parrafo;
    }

    private static void texto(XWPFParagraph parrafo, String valor, int tamano, boolean negrita) {
        XWPFRun run = parrafo.createRun();
        run.setFontFamily(FUENTE);
        run.setFontSize(tamano);
        run.setBold(negrita);
        run.setText(valor);
    }
}
