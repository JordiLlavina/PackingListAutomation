package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.DatosHojaCorte;
import com.puntotres.packinglist.model.DatosHojaCorte.Linea;
import com.puntotres.packinglist.model.DatosHojaCorte.Piel;

/** La Hoja de corte de un modelo: una ficha por color con cantidad. */
class HojaCorteServiceTest {

    private final HojaCorteService servicio = new HojaCorteService();

    static DatosHojaCorte ejemplo() {
        return new DatosHojaCorte("AMI", "AMI", "SS27", "ULL027.AL0103", "ULL027", "Candy pouch", "ULL027",
                LocalDate.of(2026, 11, 26), List.of(
                        new Linea("001", "BLACK", 12, new Piel("ALPACA", "NEGRO"), new Piel("STILLA", "999"),
                                List.of(new Piel("NOBILE", "ORO"), new Piel("SERRAJE", "NEGRO"))),
                        new Linea("", "", null, new Piel("ALPACA", ""), Piel.NINGUNA, List.of()),
                        new Linea("2221", "CHOCOLATE", 5, new Piel("ALPACA", "MARRON"), Piel.NINGUNA, List.of())));
    }

    @Test
    void unaFichaPorLineaConCantidadYTituladaConElModelo() throws Exception {
        byte[] excel = servicio.excel(ejemplo(), null);

        Files.write(Path.of("target", "Hoja de corte ejemplo.xlsx"), excel);
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals(2, libro.getNumberOfSheets(), "la línea sin cantidad no sale");
            assertEquals("ULL027.AL0103 - 001 BLACK", libro.getSheetAt(0).getSheetName());
            List<String> textos = textos(libro.getSheetAt(0));
            assertTrue(textos.contains("ULL027") && textos.contains("CANDY POUCH"), textos.toString());
            assertTrue(textos.contains("AMI | Temporada SS27"), textos.toString());
            assertTrue(textos.contains("Cantidad  12"), textos.toString());
            assertTrue(textos.stream().anyMatch(t -> t.contains("STILLA")), "el forro: " + textos);
            assertTrue(textos.stream().anyMatch(t -> t.contains("SERRAJE")), "la segunda combinación: " + textos);
        }
    }

    @Test
    void sinNingunaLineaConCantidadNoHayHoja() {
        DatosHojaCorte vacia = new DatosHojaCorte("AMI", "AMI", "SS27", "", "ULL027", "", "", null,
                List.of(new Linea("001", "BLACK", 0, Piel.NINGUNA, Piel.NINGUNA, List.of())));

        assertThrows(IllegalArgumentException.class, () -> servicio.excel(vacia, null));
    }

    @Test
    void elFicheroLlevaClienteTemporadaYModelo() {
        assertEquals("AMI SS27 - ULL027 - Hoja de Corte.xlsx", servicio.nombreFichero(ejemplo()));
        assertEquals("APC - PXCBC-F67008 - Hoja de Corte.xlsx", servicio.nombreFichero(new DatosHojaCorte("APC",
                "A.P.C.", "", "PXCBC-F67008", "", "", "", null, List.of())), "sin modelo, la referencia");
    }

    private static List<String> textos(Sheet hoja) {
        DataFormatter formato = new DataFormatter();
        List<String> textos = new ArrayList<>();
        hoja.forEach(fila -> fila.forEach(celda -> {
            String texto = formato.formatCellValue(celda);
            if (!texto.isBlank()) {
                textos.add(texto);
            }
        }));
        return textos;
    }
}
