package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        // Acepta tanto la clave de la config de packing ("D. USA", "WHOLESALE")
        // como el nombre del fichero de plantilla ("USA", "WH CROSSLOG").
        assertSame(ApcEtiquetaLayout.JAPAN, ApcEtiquetaLayout.paraDestino(" japan ").orElseThrow());
        assertSame(ApcEtiquetaLayout.KOREA, ApcEtiquetaLayout.paraDestino("KOREA").orElseThrow());
        assertSame(ApcEtiquetaLayout.USA, ApcEtiquetaLayout.paraDestino("D. USA").orElseThrow());
        assertSame(ApcEtiquetaLayout.USA, ApcEtiquetaLayout.paraDestino("USA").orElseThrow());
        assertSame(ApcEtiquetaLayout.WH_CROSSLOG,
                ApcEtiquetaLayout.paraDestino("WHOLESALE").orElseThrow());
        assertSame(ApcEtiquetaLayout.WH_CROSSLOG, ApcEtiquetaLayout.paraDestino("WH CROSSLOG").orElseThrow());
        assertSame(ApcEtiquetaLayout.RETAIL, ApcEtiquetaLayout.paraDestino(" retail ").orElseThrow());
        assertTrue(ApcEtiquetaLayout.paraDestino("IVRY").isEmpty());
        assertTrue(ApcEtiquetaLayout.paraDestino(null).isEmpty());
    }

    @Test
    void retailYWholesaleCompartenCoordenadasPeroNoPlantilla() {
        // Las dos etiquetas van al mismo almacén (Crosslog) y el cliente solo
        // partió la plantilla para que se imprima RETAIL o WHOLESALE: la
        // maquetación es idéntica. Se ancla aquí porque, siendo iguales las
        // coordenadas, ninguna aserción de celdas detectaría que RETAIL apunta
        // al fichero equivocado.
        ApcEtiquetaLayout retail = ApcEtiquetaLayout.RETAIL;
        ApcEtiquetaLayout wholesale = ApcEtiquetaLayout.WH_CROSSLOG;
        assertEquals(wholesale.alturaBloque(), retail.alturaBloque());
        assertEquals(wholesale.offsetSegundaEtiqueta(), retail.offsetSegundaEtiqueta());
        assertEquals(wholesale.filaOrder(), retail.filaOrder());
        assertEquals(wholesale.filaLivraison(), retail.filaLivraison());
        assertEquals(wholesale.filaReferencia(), retail.filaReferencia());
        assertEquals(wholesale.filaColor(), retail.filaColor());
        assertEquals(wholesale.filaTalla(), retail.filaTalla());
        assertEquals(wholesale.filaPiezas(), retail.filaPiezas());
        assertEquals(wholesale.filaColisage(), retail.filaColisage());
        assertEquals(wholesale.filaPeso(), retail.filaPeso());
        assertNotEquals(wholesale.rutaPlantilla(), retail.rutaPlantilla());
        assertNotEquals(wholesale.hojaCajas(), retail.hojaCajas());
        assertNotEquals(wholesale.hojaPalet(), retail.hojaPalet());
    }

    @Test
    void cadaPlantillaExisteEnElClasspathYTieneSusDosHojas() throws IOException {
        for (ApcEtiquetaLayout layout : List.of(ApcEtiquetaLayout.JAPAN, ApcEtiquetaLayout.KOREA,
                ApcEtiquetaLayout.USA, ApcEtiquetaLayout.WH_CROSSLOG, ApcEtiquetaLayout.RETAIL)) {
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
