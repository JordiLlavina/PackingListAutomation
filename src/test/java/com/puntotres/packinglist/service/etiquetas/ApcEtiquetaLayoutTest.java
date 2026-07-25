package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ApcEtiquetaLayoutTest {

    @Test
    void resuelveLosNombresDeDestinoConocidosYRechazaElResto() {
        // Acepta tanto la clave de la config de packing ("D. USA", "C-LOG")
        // como el nombre del fichero de plantilla ("USA", "WH CROSSLOG").
        assertSame(ApcEtiquetaLayout.JAPAN, ApcEtiquetaLayout.paraDestino(" japan ").orElseThrow());
        assertSame(ApcEtiquetaLayout.KOREA, ApcEtiquetaLayout.paraDestino("KOREA").orElseThrow());
        assertSame(ApcEtiquetaLayout.USA, ApcEtiquetaLayout.paraDestino("D. USA").orElseThrow());
        assertSame(ApcEtiquetaLayout.USA, ApcEtiquetaLayout.paraDestino("USA").orElseThrow());
        assertSame(ApcEtiquetaLayout.WH_CROSSLOG, ApcEtiquetaLayout.paraDestino("C-LOG").orElseThrow());
        assertSame(ApcEtiquetaLayout.WH_CROSSLOG, ApcEtiquetaLayout.paraDestino("WH CROSSLOG").orElseThrow());
        assertTrue(ApcEtiquetaLayout.paraDestino("IVRY").isEmpty());
        assertTrue(ApcEtiquetaLayout.paraDestino(null).isEmpty());
    }

    @Test
    void cadaPlantillaExisteEnElClasspathYTieneSusDosHojas() throws IOException {
        for (ApcEtiquetaLayout layout : List.of(ApcEtiquetaLayout.JAPAN, ApcEtiquetaLayout.KOREA,
                ApcEtiquetaLayout.USA, ApcEtiquetaLayout.WH_CROSSLOG)) {
            try (InputStream plantilla = getClass().getResourceAsStream(layout.rutaPlantilla())) {
                assertNotNull(plantilla, "Falta la plantilla " + layout.rutaPlantilla());
                try (XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {
                    assertNotNull(libro.getSheet(layout.hojaCajas()),
                            layout.rutaPlantilla() + " sin hoja '" + layout.hojaCajas() + "'");
                    assertNotNull(libro.getSheet(layout.hojaPalet()),
                            layout.rutaPlantilla() + " sin hoja '" + layout.hojaPalet() + "'");
                }
            }
        }
    }
}
