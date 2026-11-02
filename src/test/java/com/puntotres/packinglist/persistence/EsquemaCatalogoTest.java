package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Las tablas del catálogo de modelos tal como las crea Flyway y las lee JPA:
 * un recorrido por cada entidad para que un desajuste entre el SQL y la clase
 * salga aquí y no al abrir la pantalla. Cliente de prueba propio ("CAT1")
 * porque la base en memoria es la misma para toda la suite.
 */
@SpringBootTest
class EsquemaCatalogoTest {

    @Autowired
    private ModeloRepository modelos;
    @Autowired
    private ArticuloRepository articulos;
    @Autowired
    private ArticuloColorRepository colores;
    @Autowired
    private PielRepository pieles;
    @Autowired
    private ModeloTemporadaRepository modeloTemporadas;
    @Autowired
    private FotoCatalogoRepository fotos;
    @Autowired
    private TemporadaGuardadaRepository temporadas;

    @Test
    void seGuardaYLeeUnModeloConSusArticulosYColores() {
        pieles.save(new Piel("CAT1", "AL0103", "ALPACA"));
        modelos.save(new Modelo("CAT1", "ULL027", "Candy", TipoArticulo.BOLSO));
        Articulo articulo = new Articulo("CAT1", "ULL027.AL0103", "ULL027", "AL0103");
        articulo.setCombinaciones(List.of("ANTE"));
        articulo.setUnidadesPorCaja(6);
        articulos.save(articulo);
        colores.save(new ArticuloColor("CAT1", "ULL027.AL0103", "001", "NEGRO", "NEGRO", List.of("GRIS")));

        Articulo leido = articulos.findById(new ArticuloId("CAT1", "ULL027.AL0103")).orElseThrow();
        assertEquals(List.of("ANTE"), leido.getCombinaciones());
        assertEquals(6, leido.getUnidadesPorCaja());
        assertEquals("", leido.getForro(), "un texto sin rellenar se lee vacío, no null");
        assertTrue(leido.tienePiel());
        ArticuloColor color = colores.findById(new ArticuloColorId("CAT1", "ULL027.AL0103", "001")).orElseThrow();
        assertEquals(List.of("GRIS"), color.getColoresCombinacion());
        assertEquals("Candy", modelos.findById(new ModeloId("CAT1", "ULL027")).orElseThrow().getNombre());
    }

    @Test
    void unArticuloSinPielYSinCartonEsLegal() {
        modelos.save(new Modelo("CAT1", "F63023", "", TipoArticulo.BOLSO));
        articulos.save(new Articulo("CAT1", "F63023", "F63023", null));

        Articulo leido = articulos.findById(new ArticuloId("CAT1", "F63023")).orElseThrow();
        assertFalse(leido.tienePiel());
        assertEquals("", leido.getMedidaCaja());
        assertEquals(null, leido.getUnidadesPorCaja());
    }

    @Test
    void laFotoGuardaSusBytesYElResumenNoLosCarga() {
        modelos.save(new Modelo("CAT1", "ULL028", "", TipoArticulo.BOLSO));
        fotos.save(new FotoCatalogo("CAT1", "ULL028", "principal.jpg", 0, new byte[] {1, 2, 3}));

        List<ResumenFoto> resumen = fotos.resumenes("CAT1", List.of("ULL028"));
        assertEquals(1, resumen.size());
        assertEquals("principal.jpg", resumen.get(0).nombreFichero());
        assertFalse(resumen.get(0).principal(), "la foto nace sin marcar: la marca el servicio");
        assertEquals(3, fotos.findById(resumen.get(0).id()).orElseThrow().getJpeg().length);
    }

    @Test
    void laTemporadaGuardaSusDosExcelsYElResumenDiceCualesTiene() {
        TemporadaGuardada conPedido = temporadas.save(
                new TemporadaGuardada("CAT1", "X", "p.xlsx", new byte[] {1}));
        assertTrue(conPedido.tienePedido());
        assertFalse(conPedido.tieneProduccion());

        TemporadaGuardada soloProduccion = new TemporadaGuardada("CAT1", "Y");
        soloProduccion.sustituirProduccion(new FicheroExcel("prod.xlsx", new byte[] {2}));
        TemporadaGuardada guardada = temporadas.save(soloProduccion);
        ResumenTemporada resumen = temporadas.resumenesDe("CAT1").stream()
                .filter(t -> t.id().equals(guardada.getId())).findFirst().orElseThrow();
        assertFalse(resumen.conPedido());
        assertTrue(resumen.conProduccion());
        assertEquals("prod.xlsx", resumen.nombreFicheroProduccion());

        modelos.save(new Modelo("CAT1", "ULL029", "", TipoArticulo.BOLSO));
        modeloTemporadas.save(new ModeloTemporada("CAT1", "ULL029", conPedido.getId()));
        assertEquals(1, modeloTemporadas.findByClienteAndTemporadaId("CAT1", conPedido.getId()).size());
    }

    @Test
    void tipoArticuloPorReferencia() {
        assertEquals(TipoArticulo.CINTURON, TipoArticulo.porReferencia("UBL029.AL0104"));
        assertEquals(TipoArticulo.BOLSO, TipoArticulo.porReferencia("ULL027.AL0103"));
    }
}
