package com.puntotres.packinglist.service.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.PielId;
import com.puntotres.packinglist.persistence.PielRepository;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.ModelosQuitados;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.Propagacion;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.Propagada;
import com.puntotres.packinglist.service.corte.ArticuloCorte;
import com.puntotres.packinglist.service.corte.ColorCorte;
import com.puntotres.packinglist.service.corte.PedidoCorte;
import com.puntotres.packinglist.service.corte.ReferenciaCorte;

/**
 * El alta de artículos en el catálogo y lo que una piel de un modelo hereda y
 * propaga a sus hermanas. Va por el contenedor porque partir la referencia
 * depende de las reglas de cada cliente (AMI por el punto, APC por el guion)
 * y cada test usa referencias propias: la base en memoria es la misma para
 * toda la suite.
 */
@SpringBootTest
class CatalogoModelosTest {

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private PielRepository pieles;

    @Autowired
    private ArchivoTemporadas archivo;

    @Test
    void asegurarCreaPielModeloYArticulo() {
        catalogo.asegurarArticulo("AMI", "ull900.al0100");

        Articulo articulo = catalogo.articulo("AMI", "ULL900.AL0100").orElseThrow();
        assertEquals("ULL900", catalogo.modelo(articulo.getModeloId()).orElseThrow().getCodigo());
        assertEquals("AL0100", articulo.getRefPiel());
        Modelo modelo = catalogo.modelo("AMI", "ULL900").orElseThrow();
        assertEquals(TipoArticulo.BOLSO, modelo.getTipo());
        assertEquals("", modelo.getNombre());
        assertEquals("", pieles.findById(new PielId("AMI", "AL0100")).orElseThrow().getNombre());
    }

    @Test
    void unCinturonNaceComoCinturon() {
        catalogo.asegurarArticulo("AMI", "UBL900.AL0100");
        assertEquals(TipoArticulo.CINTURON, catalogo.modelo("AMI", "UBL900").orElseThrow().getTipo());
    }

    @Test
    void elArticuloNuevoHeredaSoloLoQueTodosLosHermanosComparten() {
        catalogo.asegurarArticulo("AMI", "ULL901.AL0001");
        catalogo.guardarCampo("AMI", "ULL901.AL0001", CampoArticulo.MEDIDA_CAJA, "60x40x40", false);
        catalogo.guardarCampo("AMI", "ULL901.AL0001", CampoArticulo.UNIDADES_POR_CAJA, "6", false);
        catalogo.guardarCampo("AMI", "ULL901.AL0001", CampoArticulo.PESO_NETO_KG, "3,5", false);
        catalogo.asegurarArticulo("AMI", "ULL901.AL0002");
        catalogo.guardarCampo("AMI", "ULL901.AL0002", CampoArticulo.UNIDADES_POR_CAJA, "8", false);

        Articulo tercero = catalogo.asegurarArticulo("AMI", "ULL901.AL0003");

        assertEquals("60x40x40", tercero.getMedidaCaja(), "el cartón lo dicen igual todos los hermanos");
        assertNull(tercero.getUnidadesPorCaja(), "6 y 8 no coinciden: no se hereda");
        assertNull(tercero.getPesoNetoKg(), "el peso cambia con la piel y nunca se hereda");
    }

    @Test
    void propagarTocaLosHermanosConElMismoValorOVaciosYRespetaLosDemas() {
        for (String piel : List.of("AL0001", "AL0002", "AL0003", "AL0004")) {
            catalogo.asegurarArticulo("AMI", "ULL902." + piel);
        }
        catalogo.guardarCampo("AMI", "ULL902.AL0001", CampoArticulo.UNIDADES_POR_CAJA, "6", false);
        catalogo.guardarCampo("AMI", "ULL902.AL0002", CampoArticulo.UNIDADES_POR_CAJA, "6", false);
        catalogo.guardarCampo("AMI", "ULL902.AL0003", CampoArticulo.UNIDADES_POR_CAJA, "9", false);

        Propagacion propagacion = catalogo.guardarCampo("AMI", "ULL902.AL0001",
                CampoArticulo.UNIDADES_POR_CAJA, "7", true);

        assertEquals(List.of(new Propagada("ULL902.AL0002", "6"), new Propagada("ULL902.AL0004", "")),
                propagacion.propagadas());
        assertEquals(7, catalogo.articulo("AMI", "ULL902.AL0002").orElseThrow().getUnidadesPorCaja());
        assertEquals(9, catalogo.articulo("AMI", "ULL902.AL0003").orElseThrow().getUnidadesPorCaja(),
                "quien tenía otro valor no se toca");
        assertEquals(7, catalogo.articulo("AMI", "ULL902.AL0004").orElseThrow().getUnidadesPorCaja(),
                "el que estaba vacío lo recibe");
    }

    @Test
    void elPesoNoSePropaga() {
        catalogo.asegurarArticulo("AMI", "ULL903.AL0001");
        catalogo.asegurarArticulo("AMI", "ULL903.AL0002");

        Propagacion propagacion = catalogo.guardarCampo("AMI", "ULL903.AL0001",
                CampoArticulo.PESO_NETO_KG, "2", true);

        assertTrue(propagacion.propagadas().isEmpty());
        assertNull(catalogo.articulo("AMI", "ULL903.AL0002").orElseThrow().getPesoNetoKg());
    }

    @Test
    void unArticuloSinPielNiHeredaNiRecibe() {
        catalogo.asegurarArticulo("APC", "PXCBC-F63023");
        catalogo.guardarCampo("APC", "PXCBC-F63023", CampoArticulo.UNIDADES_POR_CAJA, "4", false);

        Articulo parcial = catalogo.asegurarArticulo("APC", "F63023");
        assertFalse(parcial.tienePiel());
        assertEquals("F63023", catalogo.modelo(parcial.getModeloId()).orElseThrow().getCodigo());
        assertNull(parcial.getUnidadesPorCaja(), "una referencia parcial no es hermana de nadie");

        catalogo.guardarCampo("APC", "PXCBC-F63023", CampoArticulo.UNIDADES_POR_CAJA, "5", true);
        assertNull(catalogo.articulo("APC", "F63023").orElseThrow().getUnidadesPorCaja());
    }

    @Test
    void unidadesNoNumericasLanzan() {
        catalogo.asegurarArticulo("AMI", "ULL904.AL0001");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> catalogo
                .guardarCampo("AMI", "ULL904.AL0001", CampoArticulo.UNIDADES_POR_CAJA, "seis", false));
        assertTrue(error.getMessage().contains("entero"), error.getMessage());
    }

    @Test
    void losTextosSeGuardanEnMayusculas() {
        catalogo.asegurarArticulo("AMI", "ULL905.AL0001");
        catalogo.guardarCampo("AMI", "ULL905.AL0001", CampoArticulo.FORRO, " badana ", false);
        catalogo.guardarCampo("AMI", "ULL905.AL0001", CampoArticulo.COMBINACION2, "ante", false);

        Articulo articulo = catalogo.articulo("AMI", "ULL905.AL0001").orElseThrow();
        assertEquals("BADANA", articulo.getForro());
        // Escrita en la casilla 2 con la 1 vacía, se compacta: el corte las lee sin huecos.
        assertEquals(List.of("ANTE", "", "", ""), articulo.getCasillasCombinacion());
    }

    @Test
    void vaciarUnaCombinacionCompactaLosNombresYSusColores() {
        // El corte lee las combinaciones compactadas y las empareja por posición
        // con sus colores: un hueco dejado desde la pantalla desalinearía ambos.
        catalogo.asegurarArticulo("AMI", "ULL936.AL0001");
        catalogo.guardarCampo("AMI", "ULL936.AL0001", CampoArticulo.COMBINACION1, "A", false);
        catalogo.guardarCampo("AMI", "ULL936.AL0001", CampoArticulo.COMBINACION2, "B", false);
        catalogo.asegurarColor("AMI", "ULL936.AL0001", "001");
        catalogo.guardarCampoColor("AMI", "ULL936.AL0001", "001", CatalogoModelos.CampoColor.COLOR_COMBINACION1, "CA");
        catalogo.guardarCampoColor("AMI", "ULL936.AL0001", "001", CatalogoModelos.CampoColor.COLOR_COMBINACION2, "CB");

        catalogo.guardarCampo("AMI", "ULL936.AL0001", CampoArticulo.COMBINACION1, "", false);

        Articulo articulo = catalogo.articulo("AMI", "ULL936.AL0001").orElseThrow();
        assertEquals(List.of("B", "", "", ""), articulo.getCasillasCombinacion());
        assertEquals(List.of("CB"), catalogo.coloresDe("AMI", "ULL936.AL0001").get(0).getColoresCombinacion());
    }

    @Test
    void borrarModeloSeLlevaSusArticulos() {
        catalogo.asegurarArticulo("AMI", "ULL906.AL0001");
        catalogo.asegurarArticulo("AMI", "ULL906.AL0002");

        catalogo.borrarModelo("AMI", "ULL906");

        assertTrue(catalogo.modelo("AMI", "ULL906").isEmpty());
        assertTrue(catalogo.articulo("AMI", "ULL906.AL0001").isEmpty());
    }

    @Test
    void quitarLosModelosDeUnaTemporadaBorraLosSuyosYSoloDesvinculaLosCompartidos() {
        Long esta = archivo.guardar(null, "AMI", "CAT-QUITAR-1", "p.xlsx", new byte[] {1}).getId();
        Long otra = archivo.guardar(null, "AMI", "CAT-QUITAR-2", "p.xlsx", new byte[] {1}).getId();
        catalogo.incorporar("AMI", pedidoDe("ULL937.AL0001", "ULL938.AL0001"), esta);
        catalogo.incorporar("AMI", pedidoDe("ULL938.AL0001"), otra);

        ModelosQuitados quitados = catalogo.quitarModelosDeTemporada("AMI", esta);

        assertEquals(new ModelosQuitados(1, 1, 0), quitados);
        assertTrue(catalogo.modelo("AMI", "ULL937").isEmpty(), "solo iba en esta: se borra");
        assertTrue(catalogo.articulo("AMI", "ULL937.AL0001").isEmpty());
        assertTrue(catalogo.modelo("AMI", "ULL938").isPresent(), "también va en otra: se queda");
        assertEquals(List.of(otra), catalogo.temporadasDe("AMI", "ULL938"));
        assertTrue(catalogo.codigosDeModeloEn("AMI", esta).isEmpty());
    }

    private static PedidoCorte pedidoDe(String... referencias) {
        return new PedidoCorte(java.util.Arrays.stream(referencias)
                .map(referencia -> new ArticuloCorte(new ReferenciaCorte(referencia,
                        referencia.substring(0, referencia.indexOf('.')),
                        referencia.substring(referencia.indexOf('.') + 1)),
                        List.of(new ColorCorte("001", "NOIR", 1))))
                .toList(), List.of());
    }
}
