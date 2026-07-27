package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
