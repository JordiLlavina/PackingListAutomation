package com.puntotres.packinglist.service.produccion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoColor;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.produccion.CatalogoProduccion.PielConColores;
import com.puntotres.packinglist.service.produccion.CatalogoProduccion.PielesModelo;
import com.puntotres.packinglist.testutil.FotosDePrueba;

/**
 * Del catálogo a lo que carga el formulario de Hojas de Producción: los
 * artículos del desplegable y las pieles enlazadas a cada modelo. Códigos con
 * prefijo propio ("HPT") porque la base es la misma para toda la suite.
 */
@SpringBootTest
class ArticulosProduccionTest {

    @Autowired
    private ArticulosProduccion articulos;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private MemoriaPieles pieles;

    @Autowired
    private TablaColores colores;

    @Autowired
    private FotosCatalogo fotos;

    @Autowired
    private ArchivoTemporadas temporadas;

    @Test
    void unArticuloConColoresDaUnaEntradaPorColorConPielForroCombinacionYFoto() {
        catalogo.asegurarArticulo("AMI", "HPT027.HP0103");
        catalogo.guardarCampoModelo("AMI", "HPT027", CatalogoModelos.CampoModelo.NOMBRE, "BOLSO HPT");
        pieles.guardarPiel("AMI", "HP0103", "Badana HPT");
        catalogo.guardarCampo("AMI", "HPT027.HP0103", CampoArticulo.FORRO, "Sarja", false);
        catalogo.guardarCampo("AMI", "HPT027.HP0103", CampoArticulo.COMBINACION1, "Napa", false);
        colores.guardar("AMI", "HPT101", "NOIR HPT");
        catalogo.guardarCampoColor("AMI", "HPT027.HP0103", "HPT101", CampoColor.COLOR_PIEL, "Negro");
        catalogo.guardarCampoColor("AMI", "HPT027.HP0103", "HPT101", CampoColor.COLOR_FORRO, "Gris");
        catalogo.guardarCampoColor("AMI", "HPT027.HP0103", "HPT101", CampoColor.COLOR_COMBINACION1, "Crudo");
        catalogo.asegurarColor("AMI", "HPT027.HP0103", "HPT205");
        Long fotoId = fotos.guardar("AMI", "HPT027", "principal.jpg", FotosDePrueba.jpeg(20, 20, Color.RED)).id();

        List<ArticuloProduccion> deAmi = articulos.de("AMI", null).articulos().stream()
                .filter(a -> a.referencia().equals("HPT027.HP0103")).toList();
        assertEquals(2, deAmi.size(), "una entrada por color del bolso");

        ArticuloProduccion negro = deAmi.get(0);
        assertEquals("HPT101", negro.codigoColor());
        assertEquals("HPT027", negro.codigoModelo());
        assertEquals(fotoId, negro.fotoId());
        assertEquals("BOLSO HPT", negro.nombreBolso());
        assertEquals("BADANA HPT", negro.pielBase());
        assertEquals("NEGRO", negro.colorBase());
        assertEquals("NAPA", negro.pielCombinacion());
        assertEquals("CRUDO", negro.colorCombinacion());
        assertEquals("SARJA GRIS", negro.forro(), "el forro con su color detrás");
        assertEquals("HPT027.HP0103 · HPT101 NOIR HPT", negro.referenciaYColor());

        ArticuloProduccion sinColorDePiel = deAmi.get(1);
        assertEquals("HPT205", sinColorDePiel.colorBase(), "sin color de piel ni descripción, el código es lo que se sabe");
    }

    @Test
    void lasPielesDelModeloJuntanLasDeTodosSusArticulosConSusColores() {
        catalogo.asegurarArticulo("AMI", "HPT030.HP0301");
        catalogo.asegurarArticulo("AMI", "HPT030.HP0302");
        pieles.guardarPiel("AMI", "HP0301", "Becerro HPT");
        catalogo.guardarCampo("AMI", "HPT030.HP0301", CampoArticulo.COMBINACION1, "Ante HPT", false);
        catalogo.guardarCampoColor("AMI", "HPT030.HP0301", "HPT1", CampoColor.COLOR_PIEL, "Negro");
        catalogo.guardarCampoColor("AMI", "HPT030.HP0301", "HPT2", CampoColor.COLOR_PIEL, "Camel");
        catalogo.guardarCampoColor("AMI", "HPT030.HP0301", "HPT1", CampoColor.COLOR_COMBINACION1, "Gris");
        catalogo.guardarCampoColor("AMI", "HPT030.HP0302", "HPT1", CampoColor.COLOR_PIEL, "Blanco");

        PielesModelo delModelo = articulos.de("AMI", null).modelos().get("HPT030");
        assertNotNull(delModelo);
        assertEquals(List.of(new PielConColores("BECERRO HPT", List.of("NEGRO", "CAMEL")),
                new PielConColores("HP0302", List.of("BLANCO"))), delModelo.pieles(),
                "la piel sin nombre sale por su referencia");
        assertEquals(List.of(new PielConColores("ANTE HPT", List.of("GRIS"))), delModelo.combinaciones());
    }

    @Test
    void conTemporadaSoloSalenSusModelos() {
        catalogo.asegurarArticulo("AMI", "HPT040.HP0401");
        catalogo.asegurarArticulo("AMI", "HPT041.HP0411");
        TemporadaGuardada temporada = temporadas.guardar(null, "AMI", "HPT Temporada", (String) null, (byte[]) null);
        catalogo.vincularTemporada(catalogo.modelo("AMI", "HPT040").orElseThrow().getId(), temporada.getId());

        List<String> referencias = articulos.de("AMI", temporada.getId()).articulos().stream()
                .map(ArticuloProduccion::referencia).toList();
        assertEquals(List.of("HPT040.HP0401"), referencias);
        assertTrue(articulos.de("AMI", null).articulos().stream()
                .anyMatch(a -> a.referencia().equals("HPT041.HP0411")), "sin temporada, todo el catálogo");
    }

    @Test
    void sinClienteNoHayNada() {
        assertTrue(articulos.de("", null).articulos().isEmpty());
        assertTrue(articulos.de(null, null).modelos().isEmpty());
    }
}
