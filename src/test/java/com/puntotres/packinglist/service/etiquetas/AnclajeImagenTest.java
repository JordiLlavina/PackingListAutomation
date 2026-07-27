package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class AnclajeImagenTest {

    @Test
    void unaColumnaDeAnchoCeroNoCuelgaElCalculo() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet("H");
            hoja.createRow(0).setHeightInPoints(15f);
            hoja.setColumnWidth(0, 0);   // columna oculta

            // Sin la guarda, este cálculo no termina nunca.
            XSSFClientAnchor ancla = AnclajeImagen.fijo(hoja, 0, 0, 0, 0, 1463802, 647700);

            assertTrue(ancla.getCol2() >= ancla.getCol1());
        }
    }

    @Test
    void conAnchosNormalesElAnclajeAbarcaLasColumnasQueTocan() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet("H");
            hoja.createRow(0).setHeightInPoints(15f);
            hoja.setColumnWidth(0, 3766);
            hoja.setColumnWidth(1, 4425);

            // 1463802 EMU de ancho arrancando en 342901 no caben en la
            // columna 0 sola: el anclaje debe llegar a la 1.
            XSSFClientAnchor ancla = AnclajeImagen.fijo(hoja, 0, 342901, 0, 9525, 1463802, 647700);

            assertEquals(0, ancla.getCol1());
            assertEquals(1, ancla.getCol2());
        }
    }
}
