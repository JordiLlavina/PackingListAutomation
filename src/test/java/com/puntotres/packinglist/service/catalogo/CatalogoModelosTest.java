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

import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.PielId;
import com.puntotres.packinglist.persistence.PielRepository;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.Propagacion;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.Propagada;

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

    @Test
    void asegurarCreaPielModeloYArticulo() {
        catalogo.asegurarArticulo("AMI", "ull900.al0100");

        Articulo articulo = catalogo.articulo("AMI", "ULL900.AL0100").orElseThrow();
        assertEquals("ULL900", articulo.getCodigoModelo());
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
        assertEquals("F63023", parcial.getCodigoModelo());
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
        assertEquals(List.of("", "ANTE", "", ""), articulo.getCasillasCombinacion());
    }

    @Test
    void borrarModeloSeLlevaSusArticulos() {
        catalogo.asegurarArticulo("AMI", "ULL906.AL0001");
        catalogo.asegurarArticulo("AMI", "ULL906.AL0002");

        catalogo.borrarModelo("AMI", "ULL906");

        assertTrue(catalogo.modelo("AMI", "ULL906").isEmpty());
        assertTrue(catalogo.articulo("AMI", "ULL906.AL0001").isEmpty());
    }
}
