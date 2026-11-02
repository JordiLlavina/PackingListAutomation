package com.puntotres.packinglist.service.embalaje;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.model.DatosHojaEmbalaje;
import com.puntotres.packinglist.model.DatosHojaEmbalaje.DestinacionHoja;
import com.puntotres.packinglist.persistence.ArchivoHojasEmbalaje;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.CampoArticulo;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;

/**
 * Del excel de pedido (reales de APC Spring 27 y AMI SS27) a los artículos del
 * formulario de «Info de embalaje».
 */
@SpringBootTest
class PedidoEmbalajeServiceTest {

    @Autowired
    private PedidoEmbalajeService servicio;

    @Autowired
    private CatalogoModelos catalogo;

    @Autowired
    private ArchivoHojasEmbalaje archivoHojas;

    private static byte[] recurso(String nombre) throws IOException {
        try (InputStream entrada = PedidoEmbalajeServiceTest.class
                .getResourceAsStream("/ejemplos/" + nombre)) {
            assertNotNull(entrada, "Falta src/test/resources/ejemplos/" + nombre);
            return entrada.readAllBytes();
        }
    }

    @Test
    void apcSpring27DaLasCuatroDestinacionesDelNeigeNoir() throws IOException {
        ArticulosEmbalaje resultado = servicio.articulosDe("APC", recurso("APC_PEDIDO_SPRING27.xlsx"));

        ArticuloEmbalaje neige = resultado.articulos().stream()
                .filter(a -> a.referencia().equals("PXCBC-F67008") && a.codigoColor().equals("LZZ"))
                .findFirst().orElseThrow();

        Map<String, String> destinoPorPedido = new LinkedHashMap<>();
        neige.destinaciones().forEach(d -> destinoPorPedido.put(d.pedido(), d.destino()));
        assertEquals(Map.of(
                "4100132915", "JAPAN",
                "4100132811", "DOUANES USA",
                "4100132847", "WHOLESALE",
                "4100132835", "RETAIL"), destinoPorPedido);
        assertTrue(neige.destinaciones().stream().allMatch(d -> d.cantidad() > 0));
        // El pedido real trae la designación "le neige" (sin "sac"): sale en mayúsculas.
        assertEquals("LE NEIGE", neige.nombreBolso());
        assertEquals("F67008", neige.codigoModelo());
    }

    @Test
    void amiSs27DaElPoYElDestinoDelCandyVanille() throws IOException {
        ArticulosEmbalaje resultado = servicio.articulosDe("AMI", recurso("EAN PUNTOTRES SS27.xlsx"));

        ArticuloEmbalaje candy = resultado.articulos().stream()
                .filter(a -> a.referencia().equals("USL745.AL0245") && a.codigoColor().equals("709"))
                .findFirst().orElseThrow();

        assertEquals("VANILLE", candy.descripcionColor());
        assertEquals("USL745", candy.codigoModelo());
        // En el pedido real el Candy Vanille va a tres sitios (sin sufijo = PARIS, CH y JP),
        // con tallas únicas: un pedido por destinación, en el orden del fichero.
        DestinacionHoja paris = candy.destinaciones().stream()
                .filter(d -> d.pedido().equals("09012")).findFirst().orElseThrow();
        assertEquals("PARIS", paris.destino());
        assertTrue(paris.cantidad() > 0);
        assertEquals(List.of("09012", "09005 CH", "08997 JP"),
                candy.destinaciones().stream().map(DestinacionHoja::pedido).toList());
        assertEquals(List.of("PARIS", "CHINA", "JAPAN"),
                candy.destinaciones().stream().map(DestinacionHoja::destino).toList());
    }

    /**
     * Una fila con Commandé vacío se lee como cero unidades: el artículo sigue
     * en el desplegable, pero no genera una destinación de cero.
     */
    @Test
    void unArticuloSinComandasSaleSinDestinaciones() throws IOException {
        byte[] excel = PedidoAmiExcel.crear("EAN TEST",
                new PedidoAmiExcel.Fila("SPAIN", "UBL999.AL0001", "001", "ROJO", "U", "12345 CH"));

        ArticulosEmbalaje resultado = servicio.articulosDe("AMI", excel);

        assertEquals(1, resultado.articulos().size());
        ArticuloEmbalaje articulo = resultado.articulos().get(0);
        assertEquals("UBL999.AL0001", articulo.referencia());
        assertTrue(articulo.destinaciones().isEmpty(), articulo.destinaciones().toString());
    }

    @Test
    void lasTallasDeUnMismoPoSeSumanYUnSufijoDesconocidoAvisaUnaVez() throws IOException {
        byte[] excel = PedidoAmiExcel.crear("EAN TEST",
                PedidoAmiExcel.Fila.pedida("UBL998.AL0001", "001", "75", "11111 ZZ", 3),
                PedidoAmiExcel.Fila.pedida("UBL998.AL0001", "001", "80", "11111 ZZ", 4),
                PedidoAmiExcel.Fila.pedida("UBL998.AL0001", "001", "75", "22222 ZZ", 5));

        ArticulosEmbalaje resultado = servicio.articulosDe("AMI", excel);

        List<DestinacionHoja> destinaciones = resultado.articulos().get(0).destinaciones();
        assertEquals(List.of(new DestinacionHoja("ZZ", "11111 ZZ", 7),
                new DestinacionHoja("ZZ", "22222 ZZ", 5)), destinaciones);
        assertEquals(1, resultado.avisos().size(), resultado.avisos().toString());
    }

    @Test
    void loDelCatalogoYDeLaUltimaHojaPrerrellenan() throws IOException {
        catalogo.asegurarArticulo("APC", "PXCBC-F67008");
        catalogo.guardarCampo("APC", "PXCBC-F67008", CampoArticulo.MEDIDA_CAJA, "60x40x20", false);
        catalogo.guardarCampo("APC", "PXCBC-F67008", CampoArticulo.UNIDADES_POR_CAJA, "10", false);
        archivoHojas.guardar(new DatosHojaEmbalaje("APC", "A.P.C.", "Spring 27", "Taller", 1, "",
                LocalDate.of(2026, 11, 2), "PXCBC-F67008", "LZZ", "NOIR", "SAC LE NEIGE", "F67008",
                "60x40x20", 10, "BOLSA TELA APC 15X15", "PARIS", "GRABADO LOGO NIKEL",
                List.of(), "", "", ""), null);

        ArticulosEmbalaje resultado = servicio.articulosDe("APC", recurso("APC_PEDIDO_SPRING27.xlsx"));

        ArticuloEmbalaje neige = resultado.articulos().stream()
                .filter(a -> a.referencia().equals("PXCBC-F67008") && a.codigoColor().equals("LZZ"))
                .findFirst().orElseThrow();
        assertEquals("60x40x20", neige.medidaCaja());
        assertEquals(10, neige.unidadesPorCaja());
        assertEquals("BOLSA TELA APC 15X15", neige.bolsaTela());
        assertFalse(neige.etiqueta().isBlank());
    }

    @Test
    void soportaSoloAmiYApc() {
        assertTrue(servicio.soporta("AMI"));
        assertTrue(servicio.soporta("apc"));
        assertFalse(servicio.soporta("GENERICO"));
        assertFalse(servicio.soporta(null));
    }
}
