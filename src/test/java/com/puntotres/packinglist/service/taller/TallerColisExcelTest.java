package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * El excel del taller no es una tabla limpia: lleva membrete, una leyenda de
 * campos y totales debajo, la cabecera está en la fila 9 y cada taller
 * escribe los títulos a su manera. El lector se ancla contra el fichero real
 * del taller y contra fixtures para los casos que ese fichero no cubre.
 */
class TallerColisExcelTest {

    private static final String FICHERO_REAL = "/ejemplos/taller/Packing List Taller Exemple.xlsx";

    /** La plantilla con las dos columnas de caja que el taller añadió después. */
    private static final String CON_CAJA_MEDIDA =
            "/ejemplos/taller/plantilla-taller-con-peso-y-medida.xlsx";

    private static TallerColisExcel real() throws IOException, TallerColisExcel.TallerExcelException {
        try (InputStream in = TallerColisExcelTest.class.getResourceAsStream(FICHERO_REAL)) {
            return TallerColisExcel.desdeBytes(in.readAllBytes());
        }
    }

    private static TallerColisExcel conCajaMedida()
            throws IOException, TallerColisExcel.TallerExcelException {
        try (InputStream in = TallerColisExcelTest.class.getResourceAsStream(CON_CAJA_MEDIDA)) {
            return TallerColisExcel.desdeBytes(in.readAllBytes());
        }
    }

    // --- Contra el fichero real del taller ---

    @Test
    void encuentraLaHojaYLaCabeceraDelFicheroReal() throws Exception {
        // La cabecera está en la fila 9: encima van el membrete del taller,
        // la factura, la fecha y una leyenda de qué campos son obligatorios.
        List<LineaTaller> lineas = real().lineas();

        assertEquals(15, lineas.size());
        assertEquals("AMI", lineas.get(0).cliente());
        assertEquals("PROD", lineas.get(0).motivo());
        assertEquals("ULL164.AL0052", lineas.get(0).referencia());
        assertEquals("KAKI", lineas.get(0).color());
        assertEquals("U", lineas.get(0).talla());
        assertEquals("CH", lineas.get(0).destinoTaller());
        assertEquals(8, lineas.get(0).unidadesPorCajaTaller());
        assertEquals(64, lineas.get(0).cantidad());
    }

    @Test
    void unTituloPartidoEnDosLineasSeSigueReconociendo() throws Exception {
        // En el fichero real la columna se titula "QTITE /\nCOLIS", con el
        // salto de línea dentro de la celda para que quepa en el ancho.
        assertEquals(8, real().lineas().get(0).unidadesPorCajaTaller());
    }

    @Test
    void elFicheroRealTraeTodasLasColumnasYNoGeneraAvisos() throws Exception {
        assertEquals(List.of(), real().avisos());
    }

    @Test
    void losTotalesDeAbajoNoSeLeenComoFilas() throws Exception {
        // Bajo la última fila hay "Soit : 40 colis", "Poids Brut" y "Poids Net":
        // filas con contenido que no son artículos.
        assertTrue(real().lineas().stream().noneMatch(l -> l.referencia().contains("POIDS")));
    }

    @Test
    void unaFilaDeOtroClienteNoSeFiltraAqui() throws Exception {
        // La última fila del fichero real es de PALOMA WOOL y las catorce
        // anteriores de AMI. El lector no filtra: quien decide qué hacer con
        // una hoja que mezcla clientes es la digestión, que sí lo bloquea.
        List<LineaTaller> lineas = real().lineas();

        assertEquals("PALOMA WOOL", lineas.get(lineas.size() - 1).cliente());
        assertEquals("MOD.TEST", lineas.get(lineas.size() - 1).referencia());
    }

    @Test
    void losTextosLleganEnMayusculasYSinEspacios() throws Exception {
        assertTrue(real().lineas().stream()
                .allMatch(l -> l.referencia().equals(l.referencia().trim().toUpperCase())));
    }

    @Test
    void laColumnaOpcionalQueNoEstaSeDiceYNoEsUnError() throws Exception {
        // Sin QTITE / COLIS las unidades por caja quedan a null —nunca a
        // cero— y es el paso de ajuste el que obliga a rellenarlas.
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "64")));

        TallerColisExcel excel = TallerColisExcel.desdeBytes(libro);

        assertTrue(excel.opcionalesAusentes().contains(TallerColisExcel.Columna.QTITE_COLIS));
        assertTrue(excel.lineas().stream().allMatch(l -> l.unidadesPorCajaTaller() == null));
    }

    @Test
    void elLectorNoJuzgaSiLaColumnaQueFaltaImporta() throws Exception {
        // Quién necesita CODE depende del cliente, y el cliente aquí no se
        // conoce: el lector dice qué falta y la digestión decide si avisar.
        // Por eso una columna ausente no genera aviso por sí sola.
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "64")));

        TallerColisExcel excel = TallerColisExcel.desdeBytes(libro);

        assertTrue(excel.opcionalesAusentes().contains(TallerColisExcel.Columna.CODE));
        assertEquals(List.of(), excel.avisos());
    }

    @Test
    void sinColumnaDeTallaTodoEsTallaUnica() throws Exception {
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE"),
                List.of(List.of("AMI", "PROD", "UBL1", "KAKI", "64")));

        assertTrue(TallerColisExcel.desdeBytes(libro).lineas().stream()
                .allMatch(l -> l.talla().equals("U")));
    }

    // --- Casos que el fichero real no cubre ---

    @Test
    void sinLaHojaDeColisSeDiceQueHojasTieneElLibro() {
        byte[] libro = libro(hoja("FACTURE"), hoja("OTRA COSA"));

        TallerColisExcel.HojaNoEncontradaException error =
                assertThrows(TallerColisExcel.HojaNoEncontradaException.class,
                        () -> TallerColisExcel.desdeBytes(libro));

        assertEquals(List.of("FACTURE", "OTRA COSA"), error.hojasEncontradas());
    }

    @Test
    void elNombreDeLaHojaSeBuscaNormalizado() throws Exception {
        byte[] libro = libroConCabeceraYFilas("  liste  de   colis ",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "64")));

        assertEquals(1, TallerColisExcel.desdeBytes(libro).lineas().size());
    }

    @Test
    void sePuedeElegirLaHojaAMano() throws Exception {
        byte[] libro = libroConCabeceraYFilas("MI PACKING",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "64")));

        assertEquals(1, TallerColisExcel.desdeBytes(libro, "MI PACKING").lineas().size());
    }

    @Test
    void sinUnaColumnaObligatoriaSeDiceCualFaltaYQueSeHaLeido() {
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI")));

        TallerColisExcel.ColumnasAusentesException error =
                assertThrows(TallerColisExcel.ColumnasAusentesException.class,
                        () -> TallerColisExcel.desdeBytes(libro));

        assertEquals(List.of("QUANTITE"), error.columnasAusentes());
        assertTrue(error.cabecerasLeidas().contains("CLIENT"));
    }

    @Test
    void losSinonimosYLosAcentosCaenEnLaMismaColumna() throws Exception {
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENTE", "MOTIF", "RÉFÉRENCE", "COLORIS", "QTÉ / COLIS", "QUANTITÉ"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "8", "64")));

        LineaTaller linea = TallerColisExcel.desdeBytes(libro).lineas().get(0);

        assertEquals("ULL1", linea.referencia());
        assertEquals("KAKI", linea.color());
        assertEquals(8, linea.unidadesPorCajaTaller());
        assertEquals(64, linea.cantidad());
    }

    @Test
    void laCabeceraSeBuscaEnLasPrimerasFilasYNoSeAsumeLaPrimera() throws Exception {
        byte[] libro = libroConCabeceraEnLaFila(9);

        assertEquals(1, TallerColisExcel.desdeBytes(libro).lineas().size());
    }

    @Test
    void unaCabeceraMasAllaDeLasVeintePrimerasFilasNoSeBusca() {
        byte[] libro = libroConCabeceraEnLaFila(25);

        assertThrows(TallerColisExcel.ColumnasAusentesException.class,
                () -> TallerColisExcel.desdeBytes(libro));
    }

    @Test
    void laLecturaParaTrasCincoFilasVacias() throws Exception {
        // Tres filas, cinco vacías, y debajo dos más que ya no son del packing.
        byte[] libro = libroConHueco(3, 5, 2);

        assertEquals(3, TallerColisExcel.desdeBytes(libro).lineas().size());
    }

    @Test
    void unHuecoCortoNoCortaLaLectura() throws Exception {
        byte[] libro = libroConHueco(3, 2, 2);

        assertEquals(5, TallerColisExcel.desdeBytes(libro).lineas().size());
    }

    @Test
    void laCabeceraConCeldasCombinadasSeSigueLeyendo() throws Exception {
        byte[] libro = libroConCabeceraCombinada();

        LineaTaller linea = TallerColisExcel.desdeBytes(libro).lineas().get(0);

        assertEquals("ULL1", linea.referencia());
        assertEquals(64, linea.cantidad());
    }

    @Test
    void laTallaNumericaSeConservaYLaQueNoLoEsEsTallaUnica() throws Exception {
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "TAILLE", "QUANTITE"),
                List.of(List.of("AMI", "PROD", "UBL1", "NOIR", "75", "10"),
                        List.of("AMI", "PROD", "ULL1", "NOIR", "", "10"),
                        List.of("AMI", "PROD", "ULL2", "NOIR", "TU", "10")));

        List<LineaTaller> lineas = TallerColisExcel.desdeBytes(libro).lineas();

        assertEquals("75", lineas.get(0).talla());
        assertEquals("U", lineas.get(1).talla());
        assertEquals("U", lineas.get(2).talla());
    }

    @Test
    void unaCantidadQueNoSeLeeDejaLaFilaEnCeroYAvisa() throws Exception {
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "un monton")));

        TallerColisExcel excel = TallerColisExcel.desdeBytes(libro);

        assertEquals(0, excel.lineas().get(0).cantidad());
        assertTrue(excel.avisos().stream().anyMatch(a -> a.contains("ULL1")));
    }

    @Test
    void elCodeSeLeeAunqueVengaComoNumero() throws Exception {
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "CODE", "QUANTITE"),
                List.of(List.of("APC", "PROD", "F67008", "CAMEL", "863", "10")));

        assertEquals("863", TallerColisExcel.desdeBytes(libro).lineas().get(0).code());
    }

    @Test
    void elCodeSeTituleCodeOCodeClient() throws Exception {
        // La hoja del taller lo titula "CODE CLIENT" —es el código del pedido
        // del cliente—, pero las hojas antiguas ponen solo "CODE". Con una
        // sola de las dos formas reconocida, en APC se perdía el pedido y la
        // destinación de todas las filas.
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "CODE CLIENT", "QUANTITE"),
                List.of(List.of("APC", "PROD", "F67008", "CAMEL", "863", "10")));

        TallerColisExcel excel = TallerColisExcel.desdeBytes(libro);

        assertEquals("863", excel.lineas().get(0).code());
        assertFalse(excel.opcionalesAusentes().contains(TallerColisExcel.Columna.CODE),
                "y la columna no se echa de menos");
    }

    @Test
    void laFilaConocesuNumeroEnElExcelParaPoderNombrarlaEnUnAviso() throws Exception {
        // Fila 1 = cabecera, fila 2 = primer dato (numeración de Excel, no de POI).
        assertEquals(2, TallerColisExcel.desdeBytes(libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "64")))).lineas().get(0).fila());
    }

    // --- Las dos columnas de la caja: peso bruto y dimensiones ---

    @Test
    void elPesoYLaMedidaDeLaCajaSeLeenDeLaPlantillaConLasColumnasNuevas() throws Exception {
        // Los títulos vienen partidos en dos líneas dentro de la celda, igual
        // que "QTITE / COLIS": "POIDS BRUT\nCAISSE" y "DIMENSIONS \nCAISSE".
        List<LineaTaller> lineas = conCajaMedida().lineas();

        assertEquals("ULL163.AL0052", lineas.get(0).referencia());
        assertEquals(10.0, lineas.get(0).pesoBrutoCajaKg());
        assertEquals("60X60X40", lineas.get(0).medidaCajaTaller());
    }

    @Test
    void elTotalDePesoDeDebajoDeLaTablaNoSeConfundeConLaColumna() throws Exception {
        // Bajo la tabla hay un total titulado "Poids Brut (Kg)". Es el motivo
        // de que POIDS BRUT CAISSE no tenga un sinónimo suelto "POIDS BRUT".
        TallerColisExcel excel = conCajaMedida();

        assertEquals(List.of(), excel.avisos());
        assertTrue(excel.lineas().stream().noneMatch(l -> l.referencia().contains("POIDS")));
    }

    @Test
    void elPesoDeLaCajaAdmiteDecimalesConComa() throws Exception {
        // La celda de verdad es numérica, pero alguien puede teclearla a mano.
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE",
                        "POIDS BRUT CAISSE", "DIMENSIONS CAISSE"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "64", "10,5", "60x40x40")));

        LineaTaller linea = TallerColisExcel.desdeBytes(libro).lineas().get(0);

        assertEquals(10.5, linea.pesoBrutoCajaKg());
        assertEquals("60X40X40", linea.medidaCajaTaller());
    }

    @Test
    void unPesoQueNoSeEntiendeSeQuedaEnNadaYNoEnCero() throws Exception {
        // El peso es opcional en todo el programa: un cero acabaría escrito en
        // el packing list que lee el cliente, y un hueco no.
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE",
                        "POIDS BRUT CAISSE"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "64", "unos 10 kilos")));

        assertNull(TallerColisExcel.desdeBytes(libro).lineas().get(0).pesoBrutoCajaKg());
    }

    @Test
    void unaHojaSinLasColumnasDeCajaSeSigueLeyendoIgual() throws Exception {
        // Son las últimas columnas en llegar: las hojas anteriores a ellas
        // siguen valiendo, y el peso y el cartón salen de la memoria.
        byte[] libro = libroConCabeceraYFilas("LISTE DE COLIS",
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "64")));

        TallerColisExcel excel = TallerColisExcel.desdeBytes(libro);

        assertNull(excel.lineas().get(0).pesoBrutoCajaKg());
        assertEquals("", excel.lineas().get(0).medidaCajaTaller());
        assertTrue(excel.opcionalesAusentes()
                .contains(TallerColisExcel.Columna.POIDS_BRUT_CAISSE));
        assertEquals(List.of(), excel.avisos(), "y no se avisa: no las traía nadie");
    }

    // --- Constructores de libros de prueba ---

    private record Hoja(String nombre) {
    }

    private static Hoja hoja(String nombre) {
        return new Hoja(nombre);
    }

    private static byte[] libro(Hoja... hojas) {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            for (Hoja hoja : hojas) {
                libro.createSheet(hoja.nombre());
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] libroConCabeceraYFilas(String nombreHoja, List<String> cabecera,
                                                 List<List<String>> filas) {
        return construir(nombreHoja, 0, cabecera, filas, false);
    }

    private static byte[] libroConCabeceraEnLaFila(int filaCabecera) {
        return construir("LISTE DE COLIS", filaCabecera,
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "64")), false);
    }

    private static byte[] libroConCabeceraCombinada() {
        return construir("LISTE DE COLIS", 0,
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE"),
                List.of(List.of("AMI", "PROD", "ULL1", "KAKI", "64")), true);
    }

    private static byte[] libroConHueco(int antes, int vacias, int despues) {
        List<List<String>> filas = new java.util.ArrayList<>();
        for (int i = 0; i < antes; i++) {
            filas.add(List.of("AMI", "PROD", "ULL" + i, "KAKI", "10"));
        }
        for (int i = 0; i < vacias; i++) {
            filas.add(List.of("", "", "", "", ""));
        }
        for (int i = 0; i < despues; i++) {
            filas.add(List.of("AMI", "PROD", "USL" + i, "NOIR", "10"));
        }
        return construir("LISTE DE COLIS", 0,
                List.of("CLIENT", "MOTIF", "REFERENCE", "COULEUR", "QUANTITE"), filas, false);
    }

    private static byte[] construir(String nombreHoja, int filaCabecera, List<String> cabecera,
                                    List<List<String>> filas, boolean combinarCabecera) {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet(nombreHoja);

            var filaTitulos = hoja.createRow(filaCabecera);
            for (int c = 0; c < cabecera.size(); c++) {
                filaTitulos.createCell(c).setCellValue(cabecera.get(c));
            }
            if (combinarCabecera) {
                // La cabecera ocupa dos filas combinadas verticalmente, como
                // hacen los talleres que ponen el título en un recuadro alto.
                hoja.createRow(filaCabecera + 1);
                for (int c = 0; c < cabecera.size(); c++) {
                    hoja.addMergedRegion(new CellRangeAddress(
                            filaCabecera, filaCabecera + 1, c, c));
                }
            }

            int primeraFilaDatos = filaCabecera + (combinarCabecera ? 2 : 1);
            for (int f = 0; f < filas.size(); f++) {
                var fila = hoja.createRow(primeraFilaDatos + f);
                List<String> valores = filas.get(f);
                for (int c = 0; c < valores.size(); c++) {
                    if (!valores.get(c).isEmpty()) {
                        fila.createCell(c).setCellValue(valores.get(c));
                    }
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
