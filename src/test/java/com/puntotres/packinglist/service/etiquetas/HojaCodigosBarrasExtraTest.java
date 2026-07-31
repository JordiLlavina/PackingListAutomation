package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class HojaCodigosBarrasExtraTest {

    private static final String EAN13 = "3666598890064";
    private static final String EAN128 =
            "366659889006400001000076720000000000000000MA";

    private static FilaCodigoBarrasExtra fila(int caja, String referencia, String talla) {
        return new FilaCodigoBarrasExtra(caja, referencia, "001 BLACK", talla, "4",
                EAN13, EAN128);
    }

    /** Escribe la hoja en un libro nuevo y lo reabre desde bytes. */
    private static XSSFWorkbook generarYReabrir(List<FilaCodigoBarrasExtra> filas)
            throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("ETIQUETAS");
            HojaCodigosBarrasExtra.escribir(libro, filas);
            libro.write(salida);
        }
        return new XSSFWorkbook(new ByteArrayInputStream(salida.toByteArray()));
    }

    @Test
    void sinFilasNoCreaLaHoja() throws IOException {
        try (XSSFWorkbook libro = generarYReabrir(List.of())) {
            assertEquals(1, libro.getNumberOfSheets());
            assertNull(libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA));
        }
    }

    @Test
    void escribeUnaFilaPorArticuloConSusDatos() throws IOException {
        try (XSSFWorkbook libro = generarYReabrir(List.of(
                fila(7, "ULL753.AL0168", "U"),
                fila(9, "UBL029.AL0216", "95")))) {

            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertNotNull(hoja);
            assertEquals("CAJA", texto(hoja, 0, 0));
            assertEquals("REFERENCE", texto(hoja, 0, 1));
            assertEquals("COLOR CODE", texto(hoja, 0, 2));
            assertEquals("SIZE", texto(hoja, 0, 3));
            assertEquals("QUANTITY", texto(hoja, 0, 4));
            assertEquals("EAN-13", texto(hoja, 0, 5));
            assertEquals("EAN128", texto(hoja, 0, 6));

            assertEquals("7", texto(hoja, 1, 0));
            assertEquals("ULL753.AL0168", texto(hoja, 1, 1));
            assertEquals("001 BLACK", texto(hoja, 1, 2));
            assertEquals("U", texto(hoja, 1, 3));
            assertEquals("4", texto(hoja, 1, 4));
            // El valor del código va como texto además de como imagen.
            assertEquals(EAN13, texto(hoja, 1, 5));
            assertEquals(EAN128, texto(hoja, 1, 6));

            assertEquals("9", texto(hoja, 2, 0));
            assertEquals("95", texto(hoja, 2, 3));
        }
    }

    @Test
    void cadaFilaLlevaSusDosCodigosDeBarrasComoImagen() throws IOException {
        try (XSSFWorkbook libro = generarYReabrir(List.of(
                fila(7, "ULL753.AL0168", "U"),
                fila(9, "UBL029.AL0216", "95")))) {

            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals(4, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void unArticuloSinCodigosSaleIgualPeroSinImagenes() throws IOException {
        try (XSSFWorkbook libro = generarYReabrir(List.of(
                new FilaCodigoBarrasExtra(7, "USL999.XX0000", "007", "U", "2",
                        null, null)))) {

            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals("USL999.XX0000", texto(hoja, 1, 1));
            assertEquals("", texto(hoja, 1, 5));
            assertEquals(0, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void unEan13InvalidoNoSeDibujaPeroSuTextoSiSeVe() throws IOException {
        // Mismo criterio que la etiqueta: no se imprime un código que el
        // escáner no va a leer, pero el dato no se oculta al operario.
        try (XSSFWorkbook libro = generarYReabrir(List.of(
                new FilaCodigoBarrasExtra(7, "ULL753.AL0168", "001", "U", "5",
                        "1234567890123", EAN128)))) {

            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals("1234567890123", texto(hoja, 1, 5));
            // Solo la imagen del EAN128.
            assertEquals(1, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void elMismoCodigoEnVariasFilasSeGuardaUnaSolaVez() throws IOException {
        // Un envío repite mucho el mismo artículo: sin caché el .xlsx
        // guardaría el mismo PNG una vez por fila.
        try (XSSFWorkbook libro = generarYReabrir(List.of(
                fila(7, "UBL029.AL0216", "95"),
                fila(8, "UBL029.AL0216", "95")))) {

            assertEquals(2, libro.getAllPictures().size());
        }
    }

    private static String texto(XSSFSheet hoja, int fila, int col) {
        if (hoja.getRow(fila) == null || hoja.getRow(fila).getCell(col) == null) {
            return "";
        }
        return hoja.getRow(fila).getCell(col).toString().trim();
    }
}
