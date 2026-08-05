package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor.AnchorType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Ancla las coordenadas de AmiEtiquetaLayout abriendo de verdad
 * client-labels/ami-etiquetas-template.xlsx: cada assert compara un valor
 * leído del propio fichero (por el nombre de la etiqueta de la columna B,
 * por el mimetype/tipo de anclaje de la imagen) contra la constante Java, no
 * un literal contra otro. Si un test de aquí falla es que alguien ha tocado
 * la plantilla o el layout sin el otro: comparar contra docs/Etiquetas
 * cajas/ETIQUETA CAJA AMI.xlsx.
 */
class AmiEtiquetaLayoutTest {

    private static final String RUTA_PLANTILLA = "/client-labels/ami-etiquetas-template.xlsx";

    private static XSSFWorkbook plantilla;

    @BeforeAll
    static void cargarPlantilla() throws IOException {
        try (InputStream in = AmiEtiquetaLayoutTest.class.getResourceAsStream(RUTA_PLANTILLA)) {
            plantilla = new XSSFWorkbook(in);
        }
    }

    @AfterAll
    static void cerrarPlantilla() throws IOException {
        plantilla.close();
    }

    @Test
    void chinaTieneLaImagenYElEan128EnSuSitio() {
        verificarImagenes(AmiEtiquetaLayout.CHINA);
    }

    @Test
    void japanTieneLaImagenElEan128YLaDireccion() {
        verificarImagenes(AmiEtiquetaLayout.JAPAN);
        // Solo JAPAN lleva imagen-dirección, y es un PNG anclado a tamaño
        // fijo de una sola celda (MOVE_DONT_RESIZE), a diferencia de los
        // mocks de imagenArticulo/ean128 (MOVE_AND_RESIZE): eso es lo que la
        // distingue de la imagen compuesta sin usar ya la fila esperada.
        ImagenAnclada direccion = imagenDelTipo(hoja(AmiEtiquetaLayout.JAPAN),
                "image/png", AnchorType.MOVE_DONT_RESIZE);
        assertEquals(AmiEtiquetaLayout.JAPAN_DIRECCION.fila(), direccion.fila());
        assertEquals(AmiEtiquetaLayout.JAPAN_DIRECCION.dx(), direccion.dx());
        assertEquals(AmiEtiquetaLayout.JAPAN_DIRECCION.dy(), direccion.dy());
    }

    @Test
    void franceTieneLaImagenYElEan128EnSuSitio() {
        verificarImagenes(AmiEtiquetaLayout.FRANCE);
    }

    /**
     * La imagen compuesta del artículo es siempre el PNG anclado a
     * tamaño-y-posición-relativos (MOVE_AND_RESIZE, el mismo que usa el
     * builder al insertarla); el Code128 del EAN128 es el GIF con el mismo
     * tipo de anclaje. El mimetype y el tipo de anclaje son propiedades del
     * fichero, no del layout que se está comprobando: identificar así las
     * imágenes no es circular.
     */
    private static void verificarImagenes(AmiEtiquetaLayout layout) {
        XSSFSheet hoja = hoja(layout);

        ImagenAnclada ean128 = imagenDelTipo(hoja, "image/gif", AnchorType.MOVE_AND_RESIZE);
        assertEquals(layout.ean128().fila(), ean128.fila(), layout.nombreHoja());
        assertEquals(layout.ean128().dx(), ean128.dx(), layout.nombreHoja());
        assertEquals(layout.ean128().dy(), ean128.dy(), layout.nombreHoja());

        ImagenAnclada articulo = imagenDelTipo(hoja, "image/png", AnchorType.MOVE_AND_RESIZE);
        assertEquals(layout.imagenArticulo().fila(), articulo.fila(), layout.nombreHoja());
        assertEquals(layout.imagenArticulo().dx(), articulo.dx(), layout.nombreHoja());
        assertEquals(layout.imagenArticulo().dy(), articulo.dy(), layout.nombreHoja());
    }

    @Test
    void lasFilasDeValorSonLasDeLaPlantillaNueva() {
        for (AmiEtiquetaLayout layout : todos()) {
            XSSFSheet hoja = hoja(layout);

            int filaOrderNumber = filaDeLaEtiquetaDeTexto(hoja, "ORDER NUMBER");
            assertEquals(layout.filaOrderNumber(), filaOrderNumber, layout.nombreHoja());
            asertarEsCimaDeCeldaCombinada(hoja, filaOrderNumber, layout.nombreHoja());

            int filaReferencia = filaDeLaEtiquetaDeTexto(hoja, "REFERENCE");
            assertEquals(layout.filaReferencia(), filaReferencia, layout.nombreHoja());
            asertarEsCimaDeCeldaCombinada(hoja, filaReferencia, layout.nombreHoja());
        }
    }

    @Test
    void elHuecoDeLaImagenEsElMismoEnLasTresDestinaciones() {
        // La imagen compuesta se genera con la proporción del hueco: si una
        // destinación tuviera otra, saldría deformada en esa.
        for (AmiEtiquetaLayout layout : todos()) {
            assertEquals(1674091, layout.imagenArticulo().cx(), layout.nombreHoja());
            assertEquals(762000, layout.imagenArticulo().cy(), layout.nombreHoja());
        }
    }

    @Test
    void todasLasImagenesCabenDentroDeSuBloque() {
        // Las imágenes se replican a +offsetSegundaEtiqueta, así que ninguna
        // puede arrancar más allá de esa mitad o pisaría la etiqueta de abajo.
        for (AmiEtiquetaLayout layout : todos()) {
            for (AnclajeBloque anclaje : new AnclajeBloque[] {
                    layout.imagenArticulo(), layout.ean128() }) {
                assertTrue(anclaje.fila() < layout.offsetSegundaEtiqueta(),
                        layout.nombreHoja() + ": el anclaje de la fila " + anclaje.fila()
                                + " se sale de la primera etiqueta del par");
            }
        }
    }

    // --- hojas de etiquetas de palet ---

    @Test
    void cadaDestinacionTieneSuHojaDeEtiquetasDePalet() {
        for (AmiEtiquetaLayout layout : todos()) {
            assertTrue(plantilla.getSheet(layout.nombreHojaPalets()) != null,
                    () -> "la plantilla no tiene la hoja '" + layout.nombreHojaPalets() + "'");
        }
    }

    /**
     * El bloque de palet no arranca en la fila 0: encima lleva una fila con
     * el contador que el cliente apunta a mano. La altura sale de la
     * separación real entre los dos bloques de ejemplo de la plantilla.
     */
    @Test
    void elBloqueDePaletArrancaTrasElContadorYMideLoQueSepara() {
        for (AmiEtiquetaLayout layout : todos()) {
            List<Integer> bloques = filasDeLaEtiquetaDeTexto(
                    hojaPalets(layout), "EXPEDITEUR");
            assertEquals(2, bloques.size(),
                    layout.nombreHojaPalets() + ": se esperaban dos bloques de ejemplo");
            assertEquals(AmiEtiquetaLayout.FILA_PRIMER_PALET, bloques.get(0),
                    layout.nombreHojaPalets());
            assertEquals(AmiEtiquetaLayout.ALTURA_BLOQUE_PALET,
                    bloques.get(1) - bloques.get(0), layout.nombreHojaPalets());
        }
    }

    @Test
    void lasFilasDeValorDelPaletSonLasDeLaPlantilla() {
        for (AmiEtiquetaLayout layout : todos()) {
            XSSFSheet hoja = hojaPalets(layout);
            assertEquals(AmiEtiquetaLayout.FILA_PALET_COLIS,
                    filasDeLaEtiquetaDeTexto(hoja, "Nombre total de colis sur la palette")
                            .get(0),
                    layout.nombreHojaPalets());
            assertEquals(AmiEtiquetaLayout.FILA_PALET_PESO,
                    filasDeLaEtiquetaDeTexto(hoja, "Poids brut").get(0),
                    layout.nombreHojaPalets());
        }
    }

    /**
     * Las dos filas que se rellenan tienen que caer dentro del primer bloque:
     * los palets se escriben desplazados i*ALTURA_BLOQUE_PALET y una fila de
     * fuera pisaría la etiqueta siguiente.
     */
    @Test
    void lasFilasDeValorDelPaletCabenDentroDeSuBloque() {
        int ultima = AmiEtiquetaLayout.FILA_PRIMER_PALET
                + AmiEtiquetaLayout.ALTURA_BLOQUE_PALET;
        for (int fila : new int[] {AmiEtiquetaLayout.FILA_PALET_COLIS,
                AmiEtiquetaLayout.FILA_PALET_PESO}) {
            assertTrue(fila >= AmiEtiquetaLayout.FILA_PRIMER_PALET && fila < ultima,
                    "la fila " + fila + " se sale del primer bloque de palet");
        }
    }

    // --- lectura de la plantilla ---

    private record ImagenAnclada(int fila, long dx, long dy) {
    }

    private static XSSFSheet hojaPalets(AmiEtiquetaLayout layout) {
        XSSFSheet hoja = plantilla.getSheet(layout.nombreHojaPalets());
        assertTrue(hoja != null,
                () -> "la plantilla no tiene la hoja '" + layout.nombreHojaPalets() + "'");
        return hoja;
    }

    /**
     * Todas las filas cuya celda de columna B lleva ese rótulo, en orden.
     * Los rótulos de las hojas de palet son bilingües y de varias líneas
     * ("EXPEDITEUR\n(Shipper / Sender)\nPROVENANCE\n(Origin)"), así que se
     * compara solo la PRIMERA línea, y sin espacios de sobra: la plantilla
     * trae "Poids brut " con uno al final.
     */
    private static List<Integer> filasDeLaEtiquetaDeTexto(XSSFSheet hoja, String rotulo) {
        List<Integer> filas = new ArrayList<>();
        for (Row fila : hoja) {
            Cell celda = fila.getCell(AmiEtiquetaLayout.COL_TEMPORADA);
            if (celda != null && celda.getCellType() == CellType.STRING
                    && celda.getStringCellValue().lines().findFirst()
                            .map(String::trim).filter(rotulo::equals).isPresent()) {
                filas.add(fila.getRowNum());
            }
        }
        assertTrue(!filas.isEmpty(),
                () -> "no se encontró el rótulo '" + rotulo + "' en " + hoja.getSheetName());
        return filas;
    }

    private static XSSFSheet hoja(AmiEtiquetaLayout layout) {
        XSSFSheet hoja = plantilla.getSheet(layout.nombreHoja());
        assertTrue(hoja != null, () -> "la plantilla no tiene la hoja '" + layout.nombreHoja() + "'");
        return hoja;
    }

    /** La primera (fila más baja) de las imágenes del mimetype y anclaje pedidos. */
    private static ImagenAnclada imagenDelTipo(XSSFSheet hoja, String mime, AnchorType... tipos) {
        return hoja.getDrawingPatriarch().getShapes().stream()
                .filter(XSSFPicture.class::isInstance)
                .map(XSSFPicture.class::cast)
                .filter(imagen -> mime.equals(imagen.getPictureData().getMimeType()))
                .filter(imagen -> esDeAlgunTipo(imagen.getClientAnchor().getAnchorType(), tipos))
                .min(Comparator.comparingInt(imagen -> imagen.getClientAnchor().getFrom().getRow()))
                .map(imagen -> new ImagenAnclada(imagen.getClientAnchor().getFrom().getRow(),
                        // getColOff()/getRowOff() son Object por una manía de
                        // XMLBeans: en realidad siempre traen un Number.
                        ((Number) imagen.getClientAnchor().getFrom().getColOff()).longValue(),
                        ((Number) imagen.getClientAnchor().getFrom().getRowOff()).longValue()))
                .orElseThrow(() -> new AssertionError(
                        "no hay ninguna imagen " + mime + " en " + hoja.getSheetName()));
    }

    private static boolean esDeAlgunTipo(AnchorType real, AnchorType... esperados) {
        for (AnchorType esperado : esperados) {
            if (real == esperado) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fila de la celda de columna B (la de las etiquetas de campo, no la de
     * valores) cuyo texto es exactamente el pedido. Encontrar así la fila de
     * "ORDER NUMBER"/"REFERENCE" es independiente de AmiEtiquetaLayout: solo
     * depende de lo que trae la plantilla.
     */
    private static int filaDeLaEtiquetaDeTexto(XSSFSheet hoja, String textoEtiqueta) {
        for (Row fila : hoja) {
            Cell celda = fila.getCell(AmiEtiquetaLayout.COL_TEMPORADA);
            if (celda != null && celda.getCellType() == CellType.STRING
                    && textoEtiqueta.equals(celda.getStringCellValue())) {
                return fila.getRowNum();
            }
        }
        throw new AssertionError(
                "no se encontró la etiqueta '" + textoEtiqueta + "' en " + hoja.getSheetName());
    }

    /**
     * La fila pedida tiene que ser la fila superior de la celda combinada de
     * la columna de valores que la contiene: es lo que hace visible el valor
     * de una celda combinada en Excel.
     */
    private static void asertarEsCimaDeCeldaCombinada(XSSFSheet hoja, int fila, String contexto) {
        CellRangeAddress combinada = hoja.getMergedRegions().stream()
                .filter(merge -> merge.isInRange(fila, AmiEtiquetaLayout.COL_VALOR))
                .findFirst()
                .orElseThrow(() -> new AssertionError(contexto + ": la fila " + fila
                        + " no está dentro de ninguna celda combinada en columna de valores"));
        assertEquals(fila, combinada.getFirstRow(), contexto
                + ": la fila " + fila + " no es la fila superior de su celda combinada");
    }

    private static AmiEtiquetaLayout[] todos() {
        return new AmiEtiquetaLayout[] {
                AmiEtiquetaLayout.CHINA, AmiEtiquetaLayout.JAPAN, AmiEtiquetaLayout.FRANCE };
    }
}
