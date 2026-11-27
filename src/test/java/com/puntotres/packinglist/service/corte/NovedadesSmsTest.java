package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.corte.NovedadesSms.ModeloNuevo;
import com.puntotres.packinglist.service.corte.NovedadesSms.Novedades;

/**
 * Qué cuenta como nuevo en un pedido de SMS y cómo entra en el sistema, contra
 * la base en memoria. Cada test usa sus propias referencias: la base es la
 * misma para toda la suite.
 */
@SpringBootTest
class NovedadesSmsTest {

    @Autowired
    private NovedadesSms novedades;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private MemoriaPieles memoriaPieles;

    @Autowired
    private TablaColores tablaColores;

    private static LineaSms linea(String referencia, String modelo, String codigo, String color, String piel) {
        return new LineaSms("SMS", 2, referencia, modelo, "BOLSO " + modelo, new ColorCorte(codigo, color, 1), "",
                new PielesArticulo(new PielCorte(piel, color), new PielCorte("STILLA", "999"),
                        List.of(new PielCorte("NOBILE", "ORO"))), 1, List.of());
    }

    @Test
    void modeloPielYColorDesconocidosSonNovedad() {
        Novedades nuevas = novedades.calcular("AMI", List.of(linea("ULL8N01.AL8N01", "ULL8N01", "8N1", "NOIR",
                "ALPACA")));

        assertEquals(1, nuevas.modelos().size());
        ModeloNuevo modelo = nuevas.modelos().get(0);
        assertEquals("ULL8N01", modelo.codigo());
        assertFalse(modelo.existe());
        assertEquals("BOLSO ULL8N01", modelo.nombre());
        assertEquals(List.of("NOBILE"), modelo.articulos().get(0).combinaciones());
        assertEquals("AL8N01", nuevas.pieles().get(0).refPiel());
        assertEquals("ALPACA", nuevas.pieles().get(0).nombre());
        assertEquals("8N1", nuevas.colores().get(0).codigo());
        assertEquals(3, nuevas.pendientes());
    }

    @Test
    void loQueYaEstaEnElCatalogoNoEsNovedadYUnaReferenciaNuevaDeUnModeloQueExisteSi() {
        catalogo.asegurarArticulo("AMI", "ULL8N02.AL8N02");
        memoriaPieles.guardarPiel("AMI", "AL8N02", "SERRAJE");
        tablaColores.guardar("AMI", "8N2", "NOIR");

        Novedades nuevas = novedades.calcular("AMI", List.of(linea("ULL8N02.AL8N02", "ULL8N02", "8N2", "NOIR",
                "ALPACA"), linea("ULL8N02.AL8N03", "ULL8N02", "8N2", "NOIR", "PIPER")));

        assertEquals(1, nuevas.modelos().size());
        assertTrue(nuevas.modelos().get(0).existe(), "el modelo ya está: solo trae una piel nueva");
        assertEquals(List.of("ULL8N02.AL8N03"), nuevas.modelos().get(0).articulos().stream()
                .map(NovedadesSms.ArticuloNuevo::referencia).toList());
        assertEquals(List.of("AL8N03"), nuevas.pieles().stream().map(NovedadesSms.PielNueva::refPiel).toList());
        assertTrue(nuevas.colores().isEmpty());
    }

    @Test
    void unaPielConFilaSinNombreEsNovedadSiElPedidoTraeElNombre() {
        catalogo.asegurarArticulo("AMI", "ULL8N04.AL8N04");

        Novedades nuevas = novedades.calcular("AMI", List.of(linea("ULL8N04.AL8N04", "ULL8N04", "", "NOIR",
                "ALPACA")));

        assertEquals(List.of("AL8N04"), nuevas.pieles().stream().map(NovedadesSms.PielNueva::refPiel).toList());
        assertTrue(nuevas.modelos().isEmpty());
    }

    @Test
    void entrarUnModeloLoDaDeAltaEnMuestrasConSusPielesYColores() {
        Novedades nuevas = novedades.calcular("AMI", List.of(linea("ULL8N05.AL8N05", "ULL8N05", "8N5", "NOIR",
                "ALPACA")));

        novedades.entrarModelo("AMI", nuevas.modelos().get(0));

        assertEquals(Etapa.SMS, catalogo.modelo("AMI", "ULL8N05").orElseThrow().getEtapa());
        assertEquals("STILLA", catalogo.articulo("AMI", "ULL8N05.AL8N05").orElseThrow().getForro());
        assertEquals(List.of("NOBILE"), catalogo.articulo("AMI", "ULL8N05.AL8N05").orElseThrow().getCombinaciones());
        var color = catalogo.coloresDe("AMI", "ULL8N05.AL8N05").get(0);
        assertEquals("NOIR", color.getColorPiel());
        assertEquals("999", color.getColorForro());
        assertEquals(List.of("ORO"), color.getColoresCombinacion().stream().filter(c -> !c.isEmpty()).toList());
    }

    @Test
    void entrarUnaPielOUnColorNoPisaLoQueAlguienYaPuso() {
        Novedades nuevas = novedades.calcular("AMI", List.of(linea("ULL8N06.AL8N06", "ULL8N06", "8N6", "", "ALPACA")));
        // Entretanto, alguien les pone nombre en /colores.
        memoriaPieles.guardarPiel("AMI", "AL8N06", "SERRAJE");
        tablaColores.guardar("AMI", "8N6", "VERT");

        novedades.entrarPiel("AMI", nuevas.pieles().get(0));
        novedades.entrarColor("AMI", new NovedadesSms.ColorNuevo("c0", "8N6", "ROUGE", false));

        assertEquals("SERRAJE", memoriaPieles.pielesDe("AMI").stream()
                .filter(p -> p.getRefPiel().equals("AL8N06")).findFirst().orElseThrow().getNombre());
        assertEquals("VERT", tablaColores.buscar("AMI", "8N6").orElseThrow().getDescripcion());
    }

    @Test
    void entrarTodoConUnModeloSinNombreNoEntraNada() {
        Novedades nuevas = novedades.calcular("AMI", List.of(linea("ULL8N07.AL8N07", "ULL8N07", "8N7", "NOIR",
                "ALPACA")));
        ModeloNuevo sinNombre = nuevas.modelos().get(0);
        Novedades editadas = nuevas.con(new ModeloNuevo(sinNombre.id(), sinNombre.codigo(), false, "",
                sinNombre.tipo(), sinNombre.articulos(), null, false));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> novedades.entrarTodo(editadas));

        assertTrue(error.getMessage().startsWith("ULL8N07: falta el nombre"), error.getMessage());
        assertTrue(catalogo.modelo("AMI", "ULL8N07").isEmpty());
        assertTrue(tablaColores.buscar("AMI", "8N7").isEmpty(), "tampoco el color");
    }

    @Test
    void unGenericoNoTieneListaDePielesYSuModeloEsLaReferencia() {
        Novedades nuevas = novedades.calcular("SANDRO8N", List.of(linea("SH8N01", "SH8N01", "", "NOIR", "ALPACA")));

        assertTrue(nuevas.pieles().isEmpty());
        assertEquals("SH8N01", nuevas.modelos().get(0).codigo());
    }
}
