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
 * tiene que ver de un vistazo —el modelo, el color y cuántos bolsos, en
 * grande—, con cliente y temporada en pequeño encima como subtítulo. El resto
 * de la página son las pieles —principal, de combinación y forro—, una por
 * renglón y separadas, cada una con un recuadro vacío a la derecha donde se
 * pega la muestra de piel una vez impresa.
 *
 * Cada orden tiene que caber en UNA página, porque se le pega la muestra y se
 * cuelga en el puesto: por eso las alturas son exactas y la de cada piel se
 * calcula con lo que queda debajo de la banda. Sin plantilla .docx: la
 * maquetación son las constantes de esta clase, copiadas del ejemplo que el
 * usuario retocó a mano en Word (subtítulo "AMI | Temporada H26", título solo
 * el modelo, color solo el nombre, pieles alineadas a la derecha).
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
    /** La columna de rótulo y nombre: 10 cm, como la dejó el usuario en el ejemplo. */
    static final int ANCHO_NOMBRE = 5670;
    /**
     * Margen de celda solo a los lados: Word SUMA el de arriba y el de abajo
     * a una fila de alto exacto, y con él una orden con forro se iba a dos
     * páginas. Medido abriendo el ejemplo en Word; en el XML no se ve.
     */
    static final int MARGEN_CELDA = 85;

    /**
     * El rótulo de cada piel va a 14 pt y el nombre a 28 en negrita, los dos
     * a la derecha (el formato que el usuario dejó en el ejemplo). Pero el
     * renglón mide lo que quepa en la página —1,45 cm con seis pieles— y lo
     * que no cabe en una fila de alto exacto no se ve, así que el nombre baja
     * de tamaño lo justo para caber con sus líneas debajo del rótulo.
     */
    private static final int TAMANO_ROTULO = 14;
    private static final int[] TAMANOS_NOMBRE = {28, 26, 24, 22, 20, 18, 16, 14, 12, 11, 10};
    /** Alto de una línea de Calibri a interlineado sencillo: 1,22 veces el cuerpo. */
    private static final double INTERLINEADO = 1.22;
    /**
     * Ancho de una letra de Calibri en negrita, en cuerpos, un poco por lo
     * alto para que sobre y no falte: medido en Word, una minúscula ocupa
     * unos 0,48; una mayúscula, más. Los nombres pueden llegar en mayúsculas.
     */
    private static final double ANCHO_MINUSCULA = 0.52;
    private static final double ANCHO_MAYUSCULA = 0.64;
    private static final double ANCHO_ESPACIO = 0.25;
    /** Al partir en líneas se pierde el final de cada una: la palabra que no cabe baja entera. */
    private static final double PERDIDA_AL_PARTIR = 1.15;
    /** Lo que se deja libre en cada renglón, para no apurar la última línea. */
    private static final int HOLGURA = 60;

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
        // Sin foto el hueco se queda en blanco: un rótulo ahí no le dice nada al cortador.
        if (orden.fotoPrincipal() != null) {
            WordCorte.imagen(parrafoFoto, orden.fotoPrincipal(),
                    ANCHO_FOTO - 2 * MARGEN_CELDA, ALTO_BANDA - 2 * MARGEN_CELDA - 60);
        }

        XWPFTableCell celdaDatos = fila.getCell(1);
        celdaDatos.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);
        XWPFParagraph subtitulo = WordCorte.primerParrafo(celdaDatos);
        subtitulo.setAlignment(ParagraphAlignment.RIGHT);
        WordCorte.texto(subtitulo, orden.cliente().toUpperCase(Locale.ROOT) + " | Temporada "
                + orden.temporada(), 12, false, GRIS);

        XWPFParagraph modelo = WordCorte.parrafo(celdaDatos, ParagraphAlignment.RIGHT);
        WordCorte.texto(modelo, orden.modelo(), 40, true, NEGRO);

        XWPFParagraph color = WordCorte.parrafo(celdaDatos, ParagraphAlignment.RIGHT);
        WordCorte.texto(color, "Color  ", 12, false, GRIS);
        WordCorte.texto(color, orden.color(), 28, true, NEGRO);

        XWPFParagraph bolsos = WordCorte.parrafo(celdaDatos, ParagraphAlignment.RIGHT);
        WordCorte.texto(bolsos, "Bolsos  ", 12, false, GRIS);
        WordCorte.texto(bolsos, String.valueOf(orden.bolsos()), 40, true, NEGRO);
    }

    /** El mayor tamaño de {@link #TAMANOS_NOMBRE} con el que el nombre cabe en su renglón. */
    static int tamanoNombre(String nombre, int altoRenglon) {
        for (int tamano : TAMANOS_NOMBRE) {
            if (altoNecesario(nombre, tamano) <= altoRenglon - HOLGURA) {
                return tamano;
            }
        }
        return TAMANOS_NOMBRE[TAMANOS_NOMBRE.length - 1];
    }

    /** Lo que ocupan el rótulo y el nombre partido en las líneas que le hagan falta, en twips. */
    static int altoNecesario(String nombre, int tamano) {
        int anchoUtil = ANCHO_NOMBRE - 2 * MARGEN_CELDA;
        double anchoTexto = cuerpos(nombre) * tamano * 20;
        int lineas = anchoTexto <= anchoUtil ? 1
                : (int) Math.ceil(anchoTexto * PERDIDA_AL_PARTIR / anchoUtil);
        return altoLinea(TAMANO_ROTULO) + lineas * altoLinea(tamano);
    }

    /** Lo que mide el texto en cuerpos de letra. */
    private static double cuerpos(String texto) {
        double ancho = 0;
        for (char letra : texto.toCharArray()) {
            ancho += Character.isWhitespace(letra) ? ANCHO_ESPACIO
                    : Character.isLowerCase(letra) ? ANCHO_MINUSCULA : ANCHO_MAYUSCULA;
        }
        return Math.max(ANCHO_MAYUSCULA, ancho);
    }

    private static int altoLinea(int tamano) {
        return (int) Math.ceil(tamano * 20 * INTERLINEADO);
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
            XWPFParagraph rotulo = WordCorte.primerParrafo(nombre);
            rotulo.setAlignment(ParagraphAlignment.RIGHT);
            WordCorte.texto(rotulo, renglones.get(i)[0].toUpperCase(Locale.ROOT), TAMANO_ROTULO,
                    false, GRIS);
            String nombrePiel = renglones.get(i)[1];
            WordCorte.texto(WordCorte.parrafo(nombre, ParagraphAlignment.RIGHT), nombrePiel,
                    tamanoNombre(nombrePiel, alto), true, NEGRO);
            WordCorte.bordeCaja(fila.getCell(1), GROSOR_RECUADRO, NEGRO);
            if (i < renglones.size() - 1) {
                WordCorte.altoExacto(tabla.getRow(2 * i + 1), ESPACIO_ENTRE_PIELES);
            }
        }
    }
}
