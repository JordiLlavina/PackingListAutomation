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
import com.puntotres.packinglist.persistence.TipoExcelTemporada;
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

    private static byte[] recurso(String ruta) throws IOException {
        try (InputStream entrada = CatalogoModelosIncorporarTest.class.getResourceAsStream(ruta)) {
            return entrada.readAllBytes();
        }
    }

    private TemporadaGuardada temporadaAmiH26(String nombre) throws IOException {
        return archivo.guardar(null, "AMI", nombre, "EAN PUNTOTRES H26.xlsx",
                recurso("/ejemplos/EAN PUNTOTRES H26.xlsx"), TipoExcelTemporada.PEDIDO);
    }

    @Test
    void elPedidoRealDeAmiDaDeAltaTodosSusArticulosYColores() throws IOException {
        TemporadaGuardada temporada = temporadaAmiH26("CAT-INC-H26");

        ResumenAlta alta = catalogo.incorporarTemporada(temporada);

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
        catalogo.incorporarTemporada(temporada);
        String modelo = catalogo.codigosDeModeloEn("AMI", temporada.getId()).iterator().next();
        catalogo.guardarCampoModelo("AMI", modelo, CampoModelo.NOMBRE, "Mi nombre");

        ResumenAlta segunda = catalogo.incorporarTemporada(temporada);

        assertEquals(0, segunda.articulosNuevos());
        assertEquals(0, segunda.modelosNuevos());
        assertEquals("MI NOMBRE", catalogo.modelo("AMI", modelo).orElseThrow().getNombre());
    }

    @Test
    void elExcelDeProduccionDeUnGenericoEntraEnteroComoModelo() throws IOException {
        TemporadaGuardada temporada = archivo.guardar(null, "KAMI", "CAT-INC-PROD", "prod.xlsx",
                recurso("/ejemplos/corte/PROD_APC_SPRING27.xlsx"), TipoExcelTemporada.PRODUCCION);

        ResumenAlta alta = catalogo.incorporarTemporada(temporada);

        assertTrue(alta.articulos() > 0);
        List<Articulo> articulos = catalogo.articulosDe("KAMI");
        assertFalse(articulos.isEmpty());
        assertTrue(articulos.stream().noneMatch(Articulo::tienePiel), "un genérico no parte la referencia");
        assertTrue(articulos.stream().allMatch(a -> a.getCodigoModelo().equals(a.getReferencia())));
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
