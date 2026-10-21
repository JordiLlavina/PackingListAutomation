package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class AmiCatalogoEanTest {

    @Test
    void leeTodasLasColumnasDeUnaFila() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR/ARGENT VIBRE",
                        "75", "07704 CH", "3666598543892")));

        FilaEan fila = catalogo.filas().get(0);
        assertEquals("SPAIN", fila.madeIn());
        assertEquals("UBL029.AL0104", fila.article());
        assertEquals("0014", fila.coloris());
        assertEquals("NOIR/ARGENT VIBRE", fila.libelle());
        assertEquals("75", fila.taille());
        assertEquals("07704", fila.poNumerico());
        assertEquals("CH", fila.poSufijo());
        assertEquals("3666598543892", fila.ean13());
    }

    @Test
    void elPoNumericoLlevaPaddingYNoTieneSufijo() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672, "3666598543892")));

        FilaEan fila = catalogo.filas().get(0);
        assertEquals("07672", fila.poNumerico());
        assertNull(fila.poSufijo());
        assertEquals("07672", fila.poCompacto());
    }

    @Test
    void elPoCompactoPegaElSufijoSinEspacio() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07703 CH",
                        "3666598543892")));

        assertEquals("07703CH", catalogo.filas().get(0).poCompacto());
    }

    @Test
    void elColorCompletoEsColorisMasLibelle() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "A236", "TRUFFLE", "U", 7672,
                        "3666598543892")));

        assertEquals("A236 TRUFFLE", catalogo.filas().get(0).colorCompleto());
    }

    @Test
    void unaFilaSinArticuloSeOmiteConAviso() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "", "221", "BLACK", "U", 7672, "3666598543892"),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672,
                        "3666598543892")));

        assertEquals(1, catalogo.filas().size());
        assertEquals(1, catalogo.avisos().size());
        assertTrue(catalogo.avisos().get(0).contains("ARTICLE"));
    }

    @Test
    void unaFilaConDatosPeroSinArticuloNiPoAvisaEnVezDeDesaparecer() throws Exception {
        // Trae Made in, color, talla y un EAN13: no está "del todo vacía",
        // así que perderla en silencio ocultaría un error del fichero.
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "", "221", "BLACK", "U", "", "3666598543892")));

        assertTrue(catalogo.filas().isEmpty());
        assertEquals(1, catalogo.avisos().size());
        assertTrue(catalogo.avisos().get(0).contains("ARTICLE"), catalogo.avisos().get(0));
    }

    @Test
    void unaFilaDelTodoVaciaSeIgnoraSinAviso() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("", "", "", "", "", ""),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672, "3666598543892")));

        assertEquals(1, catalogo.filas().size());
        assertTrue(catalogo.avisos().isEmpty(), catalogo.avisos().toString());
    }

    @Test
    void unaFilaSinPoSeOmiteConAviso() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "", "3666598543892")));

        assertTrue(catalogo.filas().isEmpty());
        assertEquals(1, catalogo.avisos().size());
        assertTrue(catalogo.avisos().get(0).contains("PO"));
    }

    @Test
    void laTemporadaSaleDelNombreDeLaHoja() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672, "3666598543892"));

        assertEquals("H26", AmiCatalogoEan.desdeBytes(pedido).temporada().orElseThrow());
    }

    @Test
    void unaHojaLlamadaSoloEanNoDaTemporada() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672, "3666598543892"));

        assertTrue(AmiCatalogoEan.desdeBytes(pedido).temporada().isEmpty());
    }

    @Test
    void elEan13VacioLlegaComoCadenaVaciaNoComoNull() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672)));

        assertEquals("", catalogo.filas().get(0).ean13());
    }

    /** Mismas columnas que PedidoAmiExcel.crear, pero sin "Libellé coloris". */
    private static byte[] pedidoSinColumnaLibelle() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            var hoja = libro.createSheet("EAN H26");
            Row cabecera = hoja.createRow(0);
            String[] titulos = {"Made in", "ARTICLE", "COLORIS", "TAILLE", "PO", "EAN13"};
            for (int i = 0; i < titulos.length; i++) {
                cabecera.createCell(i).setCellValue(titulos[i]);
            }
            Row fila = hoja.createRow(1);
            fila.createCell(0).setCellValue("SPAIN");
            fila.createCell(1).setCellValue("ULL163.AL0052");
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    @Test
    void sinLaColumnaDeLibelleElMensajeDeErrorUsaElRotuloHumano() throws Exception {
        // La clave de búsqueda interna es "LIBELL"; el usuario conoce la
        // columna por su nombre real en el excel, "Libellé coloris".
        byte[] pedido = pedidoSinColumnaLibelle();

        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class,
                () -> AmiCatalogoEan.desdeBytes(pedido));

        assertTrue(excepcion.getMessage().contains("Libellé coloris"), excepcion.getMessage());
        assertFalse(excepcion.getMessage().contains("LIBELL'"), excepcion.getMessage());
    }

    /**
     * El SS27 no trae columna Made in, que es la que reparte los ficheros
     * (bolsos MOROCCO, bolsos SPAIN): el país sale del final del Code 128.
     * Su hoja tampoco se llama "EAN ...", así que no da temporada.
     */
    @Test
    void enElFormatoSs27ElMadeInSaleDelCodigo128() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(
                PedidoAmiExcel.crearFormatoSs27("Feuil1 Original",
                        new Fila(null, "ULL770.AL0245", "001", "NOIR", "U", "09839 PRESS",
                                "3667549163510",
                                "366754916351000001000098390000000000000000MA"),
                        new Fila(null, "UBL029.AL0246", "0014", "NOIR/ARGENT VIBRE", "75",
                                "09006", "3667549161264",
                                "366754916126400001000090060000000000000000ES")));

        assertTrue(catalogo.avisos().isEmpty(), catalogo.avisos().toString());
        FilaEan bolso = catalogo.filas().get(0);
        assertEquals("MOROCCO", bolso.madeIn());
        assertEquals("NOIR", bolso.libelle());
        assertEquals("09839", bolso.poNumerico());
        assertEquals("PRESS", bolso.poSufijo());
        assertEquals("3667549163510", bolso.ean13());
        assertEquals("SPAIN", catalogo.filas().get(1).madeIn());
        assertTrue(catalogo.temporada().isEmpty());
    }

    /** Contra el fichero real: 100 filas, bolsos de Marruecos y cinturones de España. */
    @Test
    void elPedidoRealSs27SeLeeEntero() throws Exception {
        byte[] contenido;
        try (java.io.InputStream entrada = AmiCatalogoEanTest.class
                .getResourceAsStream("/ejemplos/EAN PUNTOTRES SS27.xlsx")) {
            contenido = entrada.readAllBytes();
        }
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(contenido);

        assertTrue(catalogo.avisos().isEmpty(), catalogo.avisos().toString());
        assertEquals(100, catalogo.filas().size());
        for (FilaEan fila : catalogo.filas()) {
            assertEquals(fila.article().startsWith("UBL") ? "SPAIN" : "MOROCCO", fila.madeIn(),
                    fila.article() + " " + fila.coloris() + " " + fila.taille());
        }
    }

    @Test
    void conservaElOrdenDeAparicionDelExcel() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR", "75", 7704, "3666598543892"),
                new Fila("MOROCCO", "ULL163.AL0052", "221", "BLACK", "U", 7672,
                        "3666598543892")));

        List<FilaEan> filas = catalogo.filas();
        assertEquals("UBL029.AL0104", filas.get(0).article());
        assertEquals("ULL163.AL0052", filas.get(1).article());
    }
}
