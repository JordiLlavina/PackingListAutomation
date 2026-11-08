package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.corte.ApcCorte;
import com.puntotres.packinglist.service.corte.ApcSmsExcel;
import com.puntotres.packinglist.service.corte.DocumentosCorteService;
import com.puntotres.packinglist.service.corte.LineaSms;
import com.puntotres.packinglist.service.corte.PielCorte;
import com.puntotres.packinglist.service.corte.PielesArticulo;

/**
 * Las etiquetas de SMS contra la copia del excel de SMS real de APC FALL 27
 * y, para los casos raros, contra libros hechos aquí. Deja
 * {@code target/Etiquetas SMS APC FALL 27.docx}, el de verdad, para abrirlo en
 * Word.
 */
class EtiquetasSmsServiceTest {

    private final EtiquetasSmsService servicio =
            new EtiquetasSmsService(new DocumentosCorteService(List.of(new ApcCorte())));

    private static byte[] real() throws IOException {
        try (InputStream entrada = EtiquetasSmsServiceTest.class
                .getResourceAsStream("/ejemplos/corte/COMMANDE_SMS_APC_FALL27.xlsx")) {
            return entrada.readAllBytes();
        }
    }

    private static List<List<String>> etiquetas(byte[] word) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word))) {
            XWPFTable tabla = doc.getTables().get(0);
            return tabla.getRows().stream().flatMap(fila -> fila.getTableCells().stream())
                    .map(celda -> celda.getParagraphs().stream().map(XWPFParagraph::getText).toList())
                    .filter(lineas -> lineas.size() == 7)
                    .toList();
        }
    }

    @Test
    void unaEtiquetaPorMuestraYEnElOrdenDelExcel() throws IOException {
        List<String> esperadas = new ArrayList<>();
        for (LineaSms linea : ApcSmsExcel.leer(real(), new ApcCorte()).lineas()) {
            for (int i = 0; i < linea.muestras(); i++) {
                esperadas.add(linea.referencia());
            }
        }

        EtiquetasSmsService.ResultadoEtiquetasSms resultado =
                servicio.generar("APC", "A.P.C.", " fall 27 ", real());

        assertEquals(esperadas.size(), resultado.etiquetas());
        assertEquals("APC fall 27 - Etiquetas SMS.docx", resultado.nombreFichero());
        List<List<String>> etiquetas = etiquetas(resultado.word());
        assertEquals(esperadas, etiquetas.stream().map(lineas -> lineas.get(3)).toList());
        // FEMME, fila 2: PXCBC-F67008 LE NEIGE, ALPACA TOUCH en VERT FONCE, 2 muestras.
        List<String> primera = List.of("PUNTOTRES", "FALL 27", "SMS", "PXCBC-F67008", "LE NEIGE", "ALPACA TOUCH",
                "VERT FONCE");
        assertEquals(primera, etiquetas.get(0));
        assertEquals(primera, etiquetas.get(1));
        // Para abrirlo en Word y compararlo con la plantilla del usuario.
        Files.write(Path.of("target", "Etiquetas SMS APC FALL 27.docx"), resultado.word());
    }

    @Test
    void unBolsoDeDosPielesLasEscribeTalComoElPedido() {
        PielesArticulo emparejadas = new PielesArticulo(new PielCorte("BOMBARDIER", "ECRU"), PielCorte.NINGUNA,
                List.of(new PielCorte("NOBILE", "CAE MARRON FONCÉ")));
        assertEquals("BOMBARDIER + NOBILE", EtiquetasSmsService.piel(emparejadas));
        assertEquals("ECRU/CAE MARRON FONCÉ", EtiquetasSmsService.colorPiel(emparejadas));

        // Sin tantos colores como pieles, el lector deja el color entero en la principal.
        PielesArticulo sinEmparejar = new PielesArticulo(new PielCorte("UNA", "NEGRO"), PielCorte.NINGUNA,
                List.of(new PielCorte("OTRA", "")));
        assertEquals("UNA + OTRA", EtiquetasSmsService.piel(sinEmparejar));
        assertEquals("NEGRO", EtiquetasSmsService.colorPiel(sinEmparejar));
    }

    @Test
    void loQueFaltaSaleEnBlancoYSeAvisaSinBloquear() throws IOException {
        byte[] excel = libro(new String[][] {
                {"PXCBC-F67008", "", "LZZ NOIR", "ALPACA TOUCH", "", "3"},
                {"PXCBC-F67009", "LE NEIGE", "LZZ NOIR", "UNA PIEL CON UN NOMBRE LARGUISIMO QUE NO CABE NI A CUATRO",
                        "NEGRO", "1"},
        });

        EtiquetasSmsService.ResultadoEtiquetasSms resultado = servicio.generar("APC", "APC", "FALL 27", excel);

        assertEquals(4, resultado.etiquetas());
        assertEquals("", etiquetas(resultado.word()).get(0).get(4));
        assertTrue(resultado.avisos().stream().anyMatch(aviso -> aviso.startsWith("FEMME, fila 2 (PXCBC-F67008): "
                + "sin el nombre del modelo («MODELE»), ni el color de la piel")), resultado.avisos().toString());
        assertTrue(resultado.avisos().stream().anyMatch(aviso -> aviso.contains("no cabe en la etiqueta")),
                resultado.avisos().toString());
    }

    @Test
    void sinMuestrasOConOtroClienteNoHayEtiquetas() throws IOException {
        byte[] sinMuestras = libro(new String[][] {{"PXCBC-F67008", "LE NEIGE", "LZZ NOIR", "ALPACA", "NEGRO",
                "PAS DE SMS"}});
        assertThrows(IllegalArgumentException.class, () -> servicio.generar("APC", "APC", "FALL 27", sinMuestras));
        assertFalse(servicio.admite("AMI"));
        assertTrue(servicio.admite("APC"));
        assertThrows(IllegalArgumentException.class, () -> servicio.generar("AMI", "AMI", "FALL 27", real()));
    }

    /** Un excel de SMS mínimo: CODIGO, MODELE, COLORIS A.P.C., ARTICLE, COLORIS FOURNISSEURS, Commande SMS. */
    private static byte[] libro(String[][] filas) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XSSFSheet hoja = libro.createSheet("FEMME");
            String[] titulos = {"CODIGO", "MODELE", "COLORIS \nA.P.C.", "ARTICLE", "COLORIS FOURNISSEURS",
                    "Commande SMS"};
            var cabecera = hoja.createRow(0);
            for (int columna = 0; columna < titulos.length; columna++) {
                cabecera.createCell(columna).setCellValue(titulos[columna]);
            }
            for (int i = 0; i < filas.length; i++) {
                var fila = hoja.createRow(i + 1);
                for (int columna = 0; columna < filas[i].length; columna++) {
                    fila.createCell(columna).setCellValue(filas[i][columna]);
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }
}
