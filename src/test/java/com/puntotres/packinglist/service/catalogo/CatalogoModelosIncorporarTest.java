package com.puntotres.packinglist.service.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.Articulo;
import com.puntotres.packinglist.persistence.ArticuloColorId;
import com.puntotres.packinglist.persistence.ArticuloColorRepository;
import com.puntotres.packinglist.persistence.ColorCliente;
import com.puntotres.packinglist.persistence.TablaColores;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.persistence.FicheroExcel;
import com.puntotres.packinglist.persistence.Piel;
import com.puntotres.packinglist.persistence.PielId;
import com.puntotres.packinglist.persistence.PielRepository;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoModelo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.ResumenAlta;
import com.puntotres.packinglist.service.corte.ArticuloCorte;
import com.puntotres.packinglist.service.corte.ColorCorte;
import com.puntotres.packinglist.service.corte.PedidoCorte;
import com.puntotres.packinglist.service.corte.ReferenciaCorte;

/**
 * El alta automática del catálogo al guardar una temporada, contra los excels
 * reales: el pedido de AMI (H26) y el excel de producción de Puntotres (APC
 * Spring 27, leído aquí como si fuera de un cliente genérico, que es el único
 * camino que tienen los genéricos).
 */
@SpringBootTest
class CatalogoModelosIncorporarTest {

    @Autowired
    private CatalogoModelos catalogo;
    @Autowired
    private ArchivoTemporadas archivo;
    @Autowired
    private ArticuloColorRepository coloresDeArticulo;
    @Autowired
    private TablaColores tablaColores;
    @Autowired
    private PielRepository pieles;

    private static byte[] recurso(String ruta) throws IOException {
        try (InputStream entrada = CatalogoModelosIncorporarTest.class.getResourceAsStream(ruta)) {
            return entrada.readAllBytes();
        }
    }

    private TemporadaGuardada temporadaAmiH26(String nombre) throws IOException {
        return archivo.guardar(null, "AMI", nombre, "EAN PUNTOTRES H26.xlsx",
                recurso("/ejemplos/EAN PUNTOTRES H26.xlsx"));
    }

    @Test
    void elPedidoRealDeAmiDaDeAltaTodosSusArticulosYColores() throws IOException {
        TemporadaGuardada temporada = temporadaAmiH26("CAT-INC-H26");

        ResumenAlta alta = catalogo.incorporarPedido(temporada);

        assertTrue(alta.articulos() >= 25, "el H26 trae 25 referencias distintas: " + alta.articulos());
        assertTrue(alta.colores() > 0);
        Set<String> modelos = catalogo.codigosDeModeloEn("AMI", temporada.getId());
        assertFalse(modelos.isEmpty());
        assertTrue(modelos.stream().anyMatch(modelo -> modelo.startsWith("UBL")),
                "los cinturones entran al catálogo aunque el corte los deje fuera");
        Articulo alguno = catalogo.articulosDe("AMI").stream()
                .filter(articulo -> modelos.contains(articulo.getCodigoModelo()))
                .findFirst().orElseThrow();
        assertTrue(alguno.tienePiel(), "AMI parte la referencia por el punto");
    }

    @Test
    void repetirNoDuplicaNiPisa() throws IOException {
        TemporadaGuardada temporada = temporadaAmiH26("CAT-INC-H26-BIS");
        catalogo.incorporarPedido(temporada);
        String modelo = catalogo.codigosDeModeloEn("AMI", temporada.getId()).iterator().next();
        catalogo.guardarCampoModelo("AMI", modelo, CampoModelo.NOMBRE, "Mi nombre");

        ResumenAlta segunda = catalogo.incorporarPedido(temporada);

        assertEquals(0, segunda.articulosNuevos());
        assertEquals(0, segunda.modelosNuevos());
        assertEquals("MI NOMBRE", catalogo.modelo("AMI", modelo).orElseThrow().getNombre());
    }

    @Test
    void elExcelDeProduccionDeUnGenericoEntraEnteroComoModelo() throws IOException {
        TemporadaGuardada temporada = archivo.guardar(null, "KAMI", "CAT-INC-PROD", null,
                new FicheroExcel("prod.xlsx", recurso("/ejemplos/corte/PROD_APC_SPRING27.xlsx")));

        ResumenAlta alta = catalogo.incorporarProduccion(temporada);

        assertTrue(alta.articulos() > 0);
        assertTrue(alta.modelosNuevos() > 0, "el emergente dice cuántos modelos son nuevos: " + alta);
        List<Articulo> articulos = catalogo.articulosDe("KAMI");
        assertFalse(articulos.isEmpty());
        assertTrue(articulos.stream().noneMatch(Articulo::tienePiel), "un genérico no parte la referencia");
        assertTrue(articulos.stream().allMatch(a -> a.getCodigoModelo().equals(a.getReferencia())));
    }

    @Test
    void elExcelDeProduccionPoneNombreALasPielesQueNoLoTienenYRespetaLoTecleado() throws IOException {
        // APC: su excel de producción escribe la piel de cada referencia en la
        // columna PIEL, que es el único sitio de donde sale su nombre: el
        // pedido del cliente no lo trae.
        TemporadaGuardada temporada = archivo.guardar(null, "APC", "CAT-INC-PROD-APC", null,
                new FicheroExcel("prod.xlsx", recurso("/ejemplos/corte/PROD_APC_SPRING27.xlsx")));

        ResumenAlta alta = catalogo.incorporarProduccion(temporada);

        Set<String> modelos = catalogo.codigosDeModeloEn("APC", temporada.getId());
        List<Articulo> suyos = catalogo.articulosDe("APC").stream()
                .filter(articulo -> modelos.contains(articulo.getCodigoModelo()) && articulo.tienePiel())
                .toList();
        assertFalse(suyos.isEmpty());
        assertTrue(alta.pielesNombradas() > 0, "el emergente dice cuántas pieles han cogido nombre: " + alta);
        Articulo uno = suyos.get(0);
        Piel piel = pieles.findById(new PielId("APC", uno.getRefPiel())).orElseThrow();
        assertFalse(piel.getNombre().isEmpty(), "la piel de " + uno.getReferencia() + " sale con nombre");
        assertEquals(piel.getNombre().toUpperCase(), piel.getNombre(), "texto libre, en mayúsculas");

        piel.actualizar("TECLEADA A MANO");
        pieles.save(piel);
        catalogo.incorporarProduccion(temporada);

        assertEquals("TECLEADA A MANO", pieles.findById(new PielId("APC", uno.getRefPiel()))
                .orElseThrow().getNombre(), "solo se rellenan huecos");
    }

    @Test
    void unaTemporadaSinExcelDeProduccionNoIncorporaNada() throws IOException {
        TemporadaGuardada temporada = temporadaAmiH26("CAT-INC-SIN-PROD");

        ResumenAlta alta = catalogo.incorporarProduccion(temporada);

        assertEquals(0, alta.articulos());
    }

    @Test
    void unColorSinCodigoEntraPorSuDescripcionYNoEnLaTablaDeColores() {
        PedidoCorte pedido = new PedidoCorte(List.of(new ArticuloCorte(
                new ReferenciaCorte("K900", "K900", ""),
                List.of(new ColorCorte("", "AMARILLO/ GRIS", 3)))), List.of());

        catalogo.incorporar("KAMI", pedido, null);

        assertTrue(coloresDeArticulo.findById(new ArticuloColorId("KAMI", "K900", "AMARILLO/ GRIS")).isPresent());
        assertTrue(tablaColores.de("KAMI").stream().map(ColorCliente::getCodigo)
                .noneMatch(codigo -> codigo.contains("AMARILLO")));
    }
}
