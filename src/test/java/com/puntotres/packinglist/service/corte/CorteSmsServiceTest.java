package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.catalogo.FotosCatalogo;

/** Las hojas de corte de SMS de un pedido leído con IA y revisado en pantalla. */
class CorteSmsServiceTest {

    private final FotosCatalogo fotos = mock(FotosCatalogo.class);
    private final CorteSmsService servicio = new CorteSmsService(
            new DocumentosCorteService(List.of(new AmiCorte(), new ApcCorte())), fotos);

    private static LineaSmsRevisada linea(boolean incluir, int fila, String referencia, String color, Integer muestras) {
        return new LineaSmsRevisada(incluir, "SMS", fila, referencia, "TOTE", "", color, "ALPACA", color,
                "STILLA", "999", List.of(new PielCorte("NOBILE", "ORO")), muestras);
    }

    @Test
    void generaUnaFichaPorLineaIncluida() throws Exception {
        CorteSmsService.ResultadoSms resultado = servicio.generarDesdeRevision("SANDRO", "Sandro", "FALL 27",
                List.of(linea(true, 2, "SHA0123", "NOIR", 3), linea(true, 3, "SHA0123", "ROUGE", 2),
                        linea(false, 4, "SHA0999", "BLEU", 5)), Map.of(), List.of("un aviso de la IA"));

        assertEquals(2, resultado.fichas());
        assertEquals(5, resultado.muestras());
        assertEquals("SANDRO FALL 27 - Hojas de Corte.xlsx", resultado.nombreFichero());
        assertEquals(List.of("SHA0123", "SHA0123"), resultado.lineas().stream().map(LineaSms::referencia).toList());
        assertEquals("SHA0123", resultado.lineas().get(0).modelo(), "un genérico no parte la referencia");
        assertTrue(resultado.avisos().contains("un aviso de la IA"));
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(resultado.excel()))) {
            assertEquals(2, libro.getNumberOfSheets());
            Sheet primera = libro.getSheetAt(0);
            assertEquals("SHA0123 - NOIR", primera.getSheetName());
        }
    }

    @Test
    void elModeloSaleDeLaReferenciaConLaReglaDelCliente() throws Exception {
        CorteSmsService.ResultadoSms resultado = servicio.generarDesdeRevision("AMI", "AMI", "SS27",
                List.of(linea(true, 2, "ULL027.AL0103", "BLACK", 1)), Map.of(), List.of());

        assertEquals("ULL027", resultado.lineas().get(0).modelo());
    }

    @Test
    void lineaSinImagenUsaLaDeOtraFilaDelModeloYLuegoElCatalogo() throws Exception {
        when(fotos.principalDe(anyString(), anyString())).thenReturn(Optional.empty());

        CorteSmsService.ResultadoSms resultado = servicio.generarDesdeRevision("SANDRO", "Sandro", "FALL 27",
                List.of(linea(true, 2, "SHA0123", "NOIR", 3), linea(true, 3, "SHA0123", "ROUGE", 2),
                        linea(true, 4, "SHA0777", "BLEU", 1)),
                Map.of("SMS", Map.of(2, List.of(jpeg()))), List.of());

        assertEquals(1, resultado.lineas().get(0).croquis().size(), "la imagen de su fila");
        assertTrue(resultado.avisos().stream().anyMatch(a -> a.startsWith("1 hoja sale sin imagen")),
                resultado.avisos().toString());
        assertEquals(resultado.lineas().get(0).croquis(), resultado.lineas().get(0).croquis());
    }

    @Test
    void unaLineaIncluidaSinMuestrasNoSeGenera() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> servicio.generarDesdeRevision("SANDRO", "Sandro", "FALL 27",
                        List.of(linea(true, 7, "SHA0123", "NOIR", null)), Map.of(), List.of()));
        assertEquals("SMS, fila 7: faltan las muestras", error.getMessage());
    }

    @Test
    void sinNingunaIncluidaNoHayNadaQueCortar() {
        assertThrows(IllegalArgumentException.class, () -> servicio.generarDesdeRevision("SANDRO", "Sandro",
                "FALL 27", List.of(linea(false, 7, "SHA0123", "NOIR", 2)), Map.of(), List.of()));
    }

    @Test
    void laRevisionArrancaConLasDudosasDesmarcadasYElColorDelBolsoEnLaPiel() {
        LecturaSmsIa lectura = new LecturaSmsIa("", null, "", true, "", List.of(
                new LecturaSmsIa.Linea("SMS", 2, "SHA0123", "", "", "001", "BLACK", "ALPACA", "NEGRO", "", "",
                        List.of(), 3, ""),
                new LecturaSmsIa.Linea("SMS", 3, "SHA0124", "", "", "", "ROUGE", "", "", "", "", List.of(), 0, ""),
                new LecturaSmsIa.Linea("SMS", 4, "", "", "", "", "BLEU", "", "", "", "", List.of(), 2, "")),
                List.of());

        List<LineaSmsRevisada> lineas = LineaSmsRevisada.de(lectura);

        assertEquals(List.of(true, false, false), lineas.stream().map(LineaSmsRevisada::incluir).toList());
        assertEquals("001 BLACK", lineas.get(0).colorPiel());
    }

    private static Imagen jpeg() throws Exception {
        BufferedImage imagen = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        return new Imagen(ReductorImagen.jpeg(imagen), 40, 30);
    }
}
