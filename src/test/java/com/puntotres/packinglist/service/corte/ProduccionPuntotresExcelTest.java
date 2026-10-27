package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * El excel de producción de Puntotres. El de APC es una copia del real de
 * Spring 27 sin las fotos incrustadas: las celdas y las fórmulas son las del
 * fichero, y las cifras se han contado sobre él. Los casos que el real no
 * trae van con libros montados aquí, con la misma disposición de columnas.
 */
class ProduccionPuntotresExcelTest {

    // --- El fichero real de APC ---

    @Test
    void apcSacaLasDieciochoReferenciasDeBolsoConLaCantidadDelTaller() throws IOException {
        ProduccionCorte produccion = leerApcReal();

        assertEquals(18, produccion.pedido().articulos().size());
        int bolsos = produccion.pedido().articulos().stream()
                .flatMap(articulo -> articulo.colores().stream())
                .mapToInt(ColorCorte::bolsos).sum();
        // La suma de la columna G (cantidad taller), no la de la F (pedido, 4.529).
        assertEquals(4565, bolsos);
        assertTrue(produccion.avisos().isEmpty(), produccion.avisos().toString());
    }

    @Test
    void apcLosCinturonesDelFinalNoEntran() throws IOException {
        List<String> referencias = leerApcReal().pedido().articulos().stream()
                .map(articulo -> articulo.referencia().referencia())
                .toList();

        assertTrue(referencias.stream().noneMatch(referencia -> referencia.startsWith("PXBHZ")
                || referencia.endsWith("H65077") || referencia.endsWith("M65222")), referencias.toString());
        // El último bloque de bolsos no se lleva las filas de cinturón de debajo.
        assertEquals(Map.of("FAB ROSE PALLIDO", 232, "KBG ALGA MILITAR", 71),
                bolsosDe(leerApcReal(), "PXCEU-F67081"));
    }

    @Test
    void apcPartePielYModeloYCodigoYDescripcionDelColor() throws IOException {
        ProduccionCorte produccion = leerApcReal();
        ArticuloCorte articulo = articulo(produccion, "PXCBC-F67008");

        assertEquals("PXCBC", articulo.referencia().piel());
        assertEquals("F67008", articulo.referencia().modelo());
        assertEquals("LE NEIGE", articulo.nombreModelo());
        assertEquals(Map.of("DAB AMARILLO CLARO", 454, "LZZ NEGRO", 520, "LAO GRIS GUIJARRO", 130),
                bolsosDe(produccion, "PXCBC-F67008"));
        assertEquals("LZZ", articulo.colores().get(1).codigo());
        assertEquals("NEGRO", articulo.colores().get(1).descripcion());
    }

    @Test
    void apcRellenaLaPielYSuColorDeCadaColorDelBolso() throws IOException {
        ProduccionCorte produccion = leerApcReal();
        FilaCorte fila = fila(produccion, "PXCBC-F67008");

        assertEquals("ALPACA", fila.getNombrePiel());
        assertEquals("NEGRO", fila.getColorPiel(1));
        assertEquals("", fila.getForro());
        assertTrue(fila.getCombinaciones().isEmpty());
        // Es la piel escrita en la fila, no la de la Tabla colores Clientes ("LZZ NOIR").
        assertEquals("NOIR", fila(produccion, "PXCBS-F63037").getColorPiel(0));
    }

    @Test
    void apcVariasPielesEscritasEnUnaReferenciaVanJuntas() throws IOException {
        // SERRAJE en el FAB y ALABAMA en el KBG: no se sabe cuál es la buena y
        // las dos dicen algo (ALABAMA es un serraje), así que van las dos.
        assertEquals("SERRAJE ALABAMA", fila(leerApcReal(), "PXCEU-F67008").getNombrePiel());
        assertEquals("NOBILE (NICKEL) NOBILE (ORO)", fila(leerApcReal(), "PXCEV-F63034").getNombrePiel());
        // La misma piel en todos los colores sale una vez.
        assertEquals("ALPACA", fila(leerApcReal(), "PXCBC-F67081").getNombrePiel());
    }

    // --- El fichero real de AMI: otra disposición de columnas ---

    /**
     * Una columna más a la izquierda que el de APC, la referencia DEBAJO de
     * las líneas de color, el nombre del bolso en la A partido en dos filas y
     * la misma referencia en dos "drops". La columna "PRESS 25/11 max" es de
     * prensa y va en sus propias órdenes.
     */
    @Test
    void amiSacaLasCuatroReferenciasConElTallerYLaPrensaAparte() throws IOException {
        ProduccionCorte produccion = leerAmiReal();

        assertEquals(List.of("ULL770.AL245", "ULL771.AL245", "ULL772.AL245", "USL745.AL245"),
                produccion.pedido().articulos().stream().map(articulo -> articulo.referencia().referencia())
                        .toList());
        // «Cantidad taller»; el pedido suma 4.011 y la prensa, 13: los dos son los 4.024 del pedido real.
        assertEquals(4061, bolsos(produccion, false));
        assertEquals(13, bolsos(produccion, true));
        assertTrue(produccion.avisos().isEmpty(), produccion.avisos().toString());
    }

    @Test
    void amiCadaReferenciaJuntaSusDropsYSuNombreEnDosFilas() throws IOException {
        ProduccionCorte produccion = leerAmiReal();

        // El NEGRO y el SABLE del drop 1A, el VANILLE del 1C, y la prensa detrás.
        assertEquals(Map.of("NEGRO", 271, "255 SABLE SAND", 159, "709 VANILLE", 32,
                        "NEGRO PRENSA", 6, "255 SABLE SAND PRENSA", 3),
                bolsosDe(produccion, "ULL772.AL245"));
        assertEquals(List.of(false, false, false, true, true), articulo(produccion, "ULL772.AL245").colores()
                .stream().map(ColorCorte::prensa).toList(), "la prensa va detrás de los colores de venta");
        ArticuloCorte articulo = articulo(produccion, "ULL772.AL245");
        assertEquals("ULL772", articulo.referencia().modelo());
        assertEquals("AL245", articulo.referencia().piel());
        assertEquals("AMI CANDY POUCH BAG", articulo.nombreModelo());
        assertEquals("AMI CANDY FOLDED WALLET", articulo(produccion, "USL745.AL245").nombreModelo());
        // "NEGRO" no lleva cifras: no hay código, el color es su descripción.
        assertEquals("", articulo.colores().get(0).codigo());
        assertEquals("255", articulo.colores().get(1).codigo());

        FilaCorte fila = fila(produccion, "ULL772.AL245");
        assertEquals("FOULARD", fila.getNombrePiel());
        assertEquals("SABLE SAND", fila.getColorPiel(1));
    }

    @Test
    void apcElBicolorVaSinCodigoYLaBarraSeparaLaPielPrincipalDeLaCombinacion() throws IOException {
        ProduccionCorte produccion = leerApcReal();
        ColorCorte color = articulo(produccion, "PXCFV-F63023").colores().get(0);

        assertEquals("", color.codigo());
        assertEquals("AMARILLO CLARO / GRIS GUIJARRO", color.descripcion());
        assertEquals(55, color.bolsos());

        FilaCorte fila = fila(produccion, "PXCFV-F63023");
        assertEquals("ALPACA BICOLOR", fila.getNombrePiel());
        assertEquals("AMARILLO CLARO", fila.getColorPiel(0));
        assertEquals(List.of("ALPACA BICOLOR"), fila.getCombinaciones());
        assertEquals(List.of("GRIS GUIJARRO"), fila.getColoresCombinacion(0));
    }

    // --- Casos montados ---

    /** Lo que se corta es la cantidad del taller: con solo el pedido no se corta nada, y se dice. */
    @Test
    void sinCantidadDelTallerNoSeCortaYSeAvisa() throws IOException {
        byte[] excel = libro(
                fila("PXCBC-F67008", "", "67008", "", "", 10, null),
                fila("", "", "LE NEIGE", "", "", null, null),
                fila("", "", "", "ALPACA", "LZZ NEGRO", 10, null),
                fila("", "", "", "ALPACA", "LAO GRIS", 5, 5));

        ProduccionCorte produccion = ProduccionPuntotresExcel.leer(excel, new ApcCorte());

        assertEquals(Map.of("LAO GRIS", 5), bolsosDe(produccion, "PXCBC-F67008"));
        assertEquals(1, produccion.avisos().size(), produccion.avisos().toString());
        assertTrue(produccion.avisos().get(0).contains("ninguno en «Cantidad taller»"),
                produccion.avisos().toString());
    }

    /**
     * Las columnas se buscan por su título, estén donde estén: aquí van en
     * otro orden, con los títulos en otra fila y en minúsculas, y con dos
     * columnas de prensa, que se suman en su propia orden.
     */
    @Test
    void lasColumnasSeBuscanPorSuTituloYLaPrensaVaAparte() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Sheet hoja = libro.createSheet("OTRO");
            escribir(hoja.createRow(1), "cantidad taller", "color", "Premsa 1", "piel", "REF. KAMI", "Prensa extra");
            Row sumas = hoja.createRow(3);
            sumas.createCell(0).setCellFormula("SUM(A5:A6)");
            escribir(hoja.createRow(4), null, "NEGRO", null, "NAPA", "BOLSO", null);
            hoja.getRow(4).createCell(0).setCellValue(40);
            hoja.getRow(4).createCell(2).setCellValue(3);
            hoja.getRow(4).createCell(5).setCellValue(2);
            escribir(hoja.createRow(5), null, null, null, null, "KM-100", null);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);

            ProduccionCorte produccion = ProduccionPuntotresExcel.leer(salida.toByteArray(),
                    new ClienteCorteGenerico("KAMI"));

            ArticuloCorte articulo = articulo(produccion, "KM-100");
            assertEquals("BOLSO", articulo.nombreModelo());
            assertEquals(List.of(new ColorCorte("", "NEGRO", 40), new ColorCorte("", "NEGRO", 5, true)),
                    articulo.colores());
            FilaCorte fila = fila(produccion, "KM-100");
            assertEquals("NAPA", fila.getNombrePiel());
            assertEquals("NEGRO", fila.getColorPiel(1), "la prensa lleva la piel en su color");
        }
    }

    /**
     * El real de APC lleva un EMF de 104 KB que se comprime en 731 bytes, y
     * POI, abriendo desde memoria, lo toma por una «zip bomb» y no abre el
     * fichero. Las imágenes no se leen nunca: no pueden impedir la lectura.
     */
    @Test
    void unaImagenQueSeComprimeMuchoNoImpideLeerElExcel() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(new java.io.ByteArrayInputStream(libro(
                fila("PXCBC-F67008", "", "67008", "", "", null, null),
                fila("", "", "", "ALPACA", "LZZ NEGRO", 10, 11))))) {
            int imagen = libro.addPicture(new byte[200_000], XSSFWorkbook.PICTURE_TYPE_EMF);
            var ancla = libro.getCreationHelper().createClientAnchor();
            ancla.setCol1(1);
            ancla.setRow1(6);
            libro.getSheetAt(0).createDrawingPatriarch().createPicture(ancla, imagen);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);

            ProduccionCorte produccion = ProduccionPuntotresExcel.leer(salida.toByteArray(), new ApcCorte());

            assertEquals(Map.of("LZZ NEGRO", 11), bolsosDe(produccion, "PXCBC-F67008"));
        }
    }

    private static void escribir(Row fila, String... textos) {
        for (int i = 0; i < textos.length; i++) {
            if (textos[i] != null) {
                fila.createCell(i).setCellValue(textos[i]);
            }
        }
    }

    @Test
    void unaCantidadSinColorNoEntraYSeAvisaYElLogoSinCantidadSeIgnora() throws IOException {
        byte[] excel = libro(
                fila("PXCBC-F67008", "", "67008", "", "", 30, 31),
                fila("", "", "", "ALPACA", "LZZ NEGRO", 20, 21),
                fila("", "", "", "ALPACA", "", 10, 10),
                fila("", "", "", "LOGO ORO", "", null, null),
                fila("", "", "", "", "(STUDS NICKEL)", null, null));

        ProduccionCorte produccion = ProduccionPuntotresExcel.leer(excel, new ApcCorte());

        assertEquals(Map.of("LZZ NEGRO", 21), bolsosDe(produccion, "PXCBC-F67008"));
        assertEquals(1, produccion.avisos().size(), produccion.avisos().toString());
        assertTrue(produccion.avisos().get(0).contains("sin color"), produccion.avisos().toString());
    }

    @Test
    void amiTomaElCodigoConCifrasYDejaFueraLosCinturones() throws IOException {
        byte[] excel = libro(
                fila("ULL712.AL0103", "", "712", "", "", null, null),
                fila("", "", "", "BOX", "0014 NOIR", 50, 52),
                fila("", "", "", "BOX", "A237 MOCHA", 10, 11),
                fila("UBL029.AL0104", "", "29", "", "", null, null),
                fila("", "", "", "BOX", "001 NOIR", 50, 52));

        ProduccionCorte produccion = ProduccionPuntotresExcel.leer(excel, new AmiCorte());

        assertEquals(1, produccion.pedido().articulos().size());
        ArticuloCorte articulo = articulo(produccion, "ULL712.AL0103");
        assertEquals("ULL712", articulo.referencia().modelo());
        assertEquals("AL0103", articulo.referencia().piel());
        assertEquals("0014", articulo.colores().get(0).codigo());
        assertEquals("NOIR", articulo.colores().get(0).descripcion());
        assertEquals("A237", articulo.colores().get(1).codigo());
    }

    @Test
    void unGenericoNoPartePielNiCodigoDeColor() throws IOException {
        byte[] excel = libro(
                fila("BOLSO-77", "", "77", "", "", null, null),
                fila("", "", "", "NAPA", "NEGRO", 5, 6));

        ProduccionCorte produccion = ProduccionPuntotresExcel.leer(excel, new ClienteCorteGenerico("ACKERMANN"));

        ArticuloCorte articulo = articulo(produccion, "BOLSO-77");
        assertEquals("BOLSO-77", articulo.referencia().modelo());
        assertEquals("", articulo.referencia().piel());
        assertEquals(new ColorCorte("", "NEGRO", 6), articulo.colores().get(0));
        // Sin piel separada, pero no se avisa: el nombre de la piel ya viene escrito.
        assertTrue(produccion.avisos().isEmpty(), produccion.avisos().toString());
        assertEquals("NAPA", fila(produccion, "BOLSO-77").getNombrePiel());
    }

    @Test
    void unaReferenciaSinNingunaLineaDeColorSeAvisa() throws IOException {
        byte[] excel = libro(
                fila("PXCBC-F67008", "", "67008", "", "", null, null),
                fila("", "", "", "LOGO ORO", "", null, null),
                fila("PXCBC-F67043", "", "67043", "", "", null, null),
                fila("", "", "", "ALPACA", "LZZ NEGRO", 4, 4));

        ProduccionCorte produccion = ProduccionPuntotresExcel.leer(excel, new ApcCorte());

        assertEquals(1, produccion.pedido().articulos().size());
        assertTrue(produccion.avisos().stream().anyMatch(aviso -> aviso.contains("PXCBC-F67008")),
                produccion.avisos().toString());
    }

    @Test
    void unExcelSinLaFilaDeTitulosNoEsDeProduccion() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("Hoja").createRow(0).createCell(0).setCellValue("PXCBC-F67008");
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ProduccionPuntotresExcel.leer(salida.toByteArray(), new ApcCorte()));
            assertTrue(error.getMessage().contains("producción"), error.getMessage());
        }
    }

    // --- Ayudas ---

    private static ProduccionCorte leerApcReal() throws IOException {
        return leerReal("PROD_APC_SPRING27.xlsx", new ApcCorte());
    }

    private static ProduccionCorte leerAmiReal() throws IOException {
        return leerReal("PROD_AMI_SS27.xlsx", new AmiCorte());
    }

    private static ProduccionCorte leerReal(String fichero, ClienteCorte cliente) throws IOException {
        try (InputStream entrada = ProduccionPuntotresExcelTest.class
                .getResourceAsStream("/ejemplos/corte/" + fichero)) {
            return ProduccionPuntotresExcel.leer(entrada.readAllBytes(), cliente);
        }
    }

    private static ArticuloCorte articulo(ProduccionCorte produccion, String referencia) {
        return produccion.pedido().articulos().stream()
                .filter(articulo -> articulo.referencia().referencia().equals(referencia))
                .findFirst().orElseThrow(() -> new AssertionError("no está " + referencia));
    }

    private static FilaCorte fila(ProduccionCorte produccion, String referencia) {
        FilaCorte fila = new FilaCorte(articulo(produccion, referencia));
        produccion.rellenar(List.of(fila));
        return fila;
    }

    private static int bolsos(ProduccionCorte produccion, boolean prensa) {
        return produccion.pedido().articulos().stream()
                .flatMap(articulo -> articulo.colores().stream())
                .filter(color -> color.prensa() == prensa)
                .mapToInt(ColorCorte::bolsos).sum();
    }

    private static Map<String, Integer> bolsosDe(ProduccionCorte produccion, String referencia) {
        Map<String, Integer> bolsos = new LinkedHashMap<>();
        articulo(produccion, referencia).colores().forEach(color ->
                bolsos.put(color.nombre() + (color.prensa() ? " PRENSA" : ""), color.bolsos()));
        return bolsos;
    }

    /** Las celdas A..G de una fila: referencia, sección, Ref. Puntotres o nombre, piel, color, pedido, taller. */
    private static Object[] fila(String a, String b, String c, String d, String e, Integer f, Integer g) {
        return new Object[] {a, b, c, d, e, f, g};
    }

    /**
     * Como el real de APC: temporada arriba, títulos en la fila 4 y los
     * bloques debajo. Una fila con referencia en la A es la de sumas del
     * bloque: lleva =SUM(...) en F y G, que es lo que separa un bloque de otro.
     */
    private static byte[] libro(Object[]... filas) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Sheet hoja = libro.createSheet("PRUEBA");
            hoja.createRow(1).createCell(0).setCellValue("SPRING 27");
            Row titulos = hoja.createRow(3);
            String[] textos = {"Ref. APC", "Foto", "Ref. Puntotres", "PIEL", "COLOR",
                    "Cantidad pedido", "Cantidad taller"};
            for (int i = 0; i < textos.length; i++) {
                if (!textos[i].isEmpty()) {
                    titulos.createCell(i).setCellValue(textos[i]);
                }
            }
            for (int i = 0; i < filas.length; i++) {
                Row fila = hoja.createRow(6 + i);
                for (int columna = 0; columna < filas[i].length; columna++) {
                    Object valor = filas[i][columna];
                    if (valor instanceof Integer numero) {
                        fila.createCell(columna).setCellValue(numero);
                    } else if (valor instanceof String texto && !texto.isEmpty()) {
                        fila.createCell(columna).setCellValue(texto);
                    }
                }
                if (filas[i][0] instanceof String referencia && !referencia.isEmpty()) {
                    int primera = 6 + i + 2;
                    fila.createCell(5).setCellFormula("SUM(F" + primera + ":F" + (primera + 4) + ")");
                    fila.createCell(6).setCellFormula("SUM(G" + primera + ":G" + (primera + 4) + ")");
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }
}
