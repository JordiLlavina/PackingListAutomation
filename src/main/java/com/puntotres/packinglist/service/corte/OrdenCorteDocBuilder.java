package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * Escribe las órdenes de corte de una temporada: un solo Word apaisado con
 * una página por modelo + piel + color.
 *
 * Arriba, la foto del modelo a la izquierda y a la derecha lo que el cortador
 * tiene que ver de un vistazo —la referencia, el color y cuántos bolsos, en
 * grande—, con cliente y temporada en pequeño encima como subtítulo. El resto
 * de la página son las pieles —principal, de combinación y forro—, una por
 * renglón y separadas, cada una con un recuadro vacío a la derecha donde se
 * pega la muestra de piel una vez impresa.
 *
 * Cada orden tiene que caber en UNA página, porque se le pega la muestra y se
 * cuelga en el puesto: por eso las alturas son exactas y la de cada piel se
 * calcula con lo que queda debajo de la banda. Sin plantilla .docx: la
 * maquetación son las constantes de esta clase.
 */
public class OrdenCorteDocBuilder {

    /** A4 apaisado con márgenes de 1,2 cm. */
    static final int ANCHO_PAGINA = 16838;
    static final int ALTO_PAGINA = 11906;
    static final int MARGEN = 680;
    static final int ANCHO_UTIL = ANCHO_PAGINA - 2 * MARGEN;
    static final int ALTO_UTIL = ALTO_PAGINA - 2 * MARGEN;

    /** Banda superior de 7 cm: foto de 10,5 cm a la izquierda, datos a la derecha. */
    static final int ALTO_BANDA = 3969;
    static final int ANCHO_FOTO = 5953;
    /** Aire entre la banda y la primera piel. */
    static final int SEPARACION = 340;
    /** Aire entre dos pieles, para que los recuadros no se toquen. */
    static final int ESPACIO_ENTRE_PIELES = 227;
    /** Una piel sola no necesita media página: 6 cm de recuadro sobran. */
    static final int ALTO_MAXIMO_PIEL = 3402;
    /** Los separadores de 1 pt y un margen para que Word no se pase de página. */
    static final int RESERVA = 170;
    /** La columna de rótulo y nombre, alineada con la foto. */
    static final int ANCHO_NOMBRE = ANCHO_FOTO;
    /**
     * Margen de celda solo a los lados: Word SUMA el de arriba y el de abajo
     * a una fila de alto exacto, y con él una orden con forro se iba a dos
     * páginas. Medido abriendo el ejemplo en Word; en el XML no se ve.
     */
    static final int MARGEN_CELDA = 85;

    /**
     * El nombre de cada piel va a 20 pt, pero con seis pieles el renglón mide
     * 1,45 cm y un nombre que no quepa en una línea perdería la segunda fuera
     * de la fila exacta. A 20 pt caben unas 24 letras en la columna; los más
     * largos bajan a 14, donde caben dos líneas holgadas.
     */
    private static final int TAMANO_NOMBRE = 20;
    private static final int TAMANO_NOMBRE_LARGO = 14;
    private static final int LETRAS_NOMBRE_GRANDE = 24;

    private static final String GRIS = "595959";
    private static final String NEGRO = "000000";
    private static final int GROSOR_RECUADRO = 8;

    public byte[] generar(List<OrdenCorte> ordenes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            WordCorte.configurarPagina(doc, ANCHO_PAGINA, ALTO_PAGINA, true,
                    MARGEN, MARGEN, MARGEN, MARGEN / 2);
            for (int i = 0; i < ordenes.size(); i++) {
                escribirBanda(doc, ordenes.get(i));
                WordCorte.parrafoDeAlto(doc, SEPARACION);
                escribirPieles(doc, ordenes.get(i).pieles());
                WordCorte.separador(doc, i < ordenes.size() - 1);
            }
            return WordCorte.escribir(doc);
        }
    }

    /** Alto de cada renglón de piel: lo que queda debajo de la banda, a partes iguales. */
    static int altoPiel(int pieles) {
        int disponible = ALTO_UTIL - ALTO_BANDA - SEPARACION - RESERVA
                - (pieles - 1) * ESPACIO_ENTRE_PIELES;
        return Math.min(ALTO_MAXIMO_PIEL, disponible / pieles);
    }

    private void escribirBanda(XWPFDocument doc, OrdenCorte orden) throws IOException {
        XWPFTable banda = WordCorte.tabla(doc, 1, ANCHO_FOTO, ANCHO_UTIL - ANCHO_FOTO);
        banda.setCellMargins(0, MARGEN_CELDA, 0, MARGEN_CELDA);
        XWPFTableRow fila = banda.getRow(0);
        WordCorte.altoExacto(fila, ALTO_BANDA);

        XWPFTableCell celdaFoto = fila.getCell(0);
        celdaFoto.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph parrafoFoto = WordCorte.primerParrafo(celdaFoto);
        parrafoFoto.setAlignment(ParagraphAlignment.CENTER);
        if (orden.fotoPrincipal() != null) {
            WordCorte.imagen(parrafoFoto, orden.fotoPrincipal(),
                    ANCHO_FOTO - 2 * MARGEN_CELDA, ALTO_BANDA - 2 * MARGEN_CELDA - 60);
        } else {
            WordCorte.texto(parrafoFoto, "Sin foto del modelo", 11, false, GRIS);
        }

        XWPFTableCell celdaDatos = fila.getCell(1);
        celdaDatos.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);
        XWPFParagraph subtitulo = WordCorte.primerParrafo(celdaDatos);
        subtitulo.setAlignment(ParagraphAlignment.RIGHT);
        WordCorte.texto(subtitulo, (orden.cliente() + " · " + orden.temporada())
                .toUpperCase(Locale.ROOT), 12, false, GRIS);

        XWPFParagraph referencia = WordCorte.parrafo(celdaDatos, ParagraphAlignment.RIGHT);
        WordCorte.texto(referencia, orden.referencia(), 40, true, NEGRO);

        XWPFParagraph color = WordCorte.parrafo(celdaDatos, ParagraphAlignment.RIGHT);
        WordCorte.texto(color, "Color  ", 12, false, GRIS);
        WordCorte.texto(color, orden.color(), 28, true, NEGRO);

        XWPFParagraph bolsos = WordCorte.parrafo(celdaDatos, ParagraphAlignment.RIGHT);
        WordCorte.texto(bolsos, "Bolsos a cortar  ", 12, false, GRIS);
        WordCorte.texto(bolsos, String.valueOf(orden.bolsos()), 40, true, NEGRO);
    }

    private void escribirPieles(XWPFDocument doc, PielesArticulo pieles) {
        List<String[]> renglones = new ArrayList<>();
        renglones.add(new String[] {"Piel", pieles.nombrePiel()});
        for (int i = 0; i < pieles.combinaciones().size(); i++) {
            renglones.add(new String[] {"Combinación " + (i + 1), pieles.combinaciones().get(i)});
        }
        if (pieles.tieneForro()) {
            renglones.add(new String[] {"Forro", pieles.forro()});
        }

        int alto = altoPiel(renglones.size());
        XWPFTable tabla = WordCorte.tabla(doc, 2 * renglones.size() - 1,
                ANCHO_NOMBRE, ANCHO_UTIL - ANCHO_NOMBRE);
        tabla.setCellMargins(0, MARGEN_CELDA, 0, MARGEN_CELDA);
        for (int i = 0; i < renglones.size(); i++) {
            XWPFTableRow fila = tabla.getRow(2 * i);
            WordCorte.altoExacto(fila, alto);
            XWPFTableCell nombre = fila.getCell(0);
            nombre.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            WordCorte.texto(WordCorte.primerParrafo(nombre),
                    renglones.get(i)[0].toUpperCase(Locale.ROOT), 10, false, GRIS);
            String nombrePiel = renglones.get(i)[1];
            WordCorte.texto(WordCorte.parrafo(nombre, ParagraphAlignment.LEFT), nombrePiel,
                    nombrePiel.length() > LETRAS_NOMBRE_GRANDE ? TAMANO_NOMBRE_LARGO : TAMANO_NOMBRE,
                    true, NEGRO);
            WordCorte.bordeCaja(fila.getCell(1), GROSOR_RECUADRO, NEGRO);
            if (i < renglones.size() - 1) {
                WordCorte.altoExacto(tabla.getRow(2 * i + 1), ESPACIO_ENTRE_PIELES);
            }
        }
    }
}
