package com.puntotres.packinglist.service.cargamodelos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.cargamodelos.LecturaOrigen.ArticuloLeido;
import com.puntotres.packinglist.service.corte.AmiCorte;
import com.puntotres.packinglist.service.corte.ApcCorte;
import com.puntotres.packinglist.service.corte.CorteSmsService;
import com.puntotres.packinglist.service.corte.DocumentosCorteService;

/**
 * Qué trae de verdad cada excel, contra los ficheros reales: de eso depende
 * qué campos salen rellenos en la batería y cuáles hay que teclear.
 */
class LectorOrigenesTest {

    private final DocumentosCorteService reglas = new DocumentosCorteService(List.of(new AmiCorte(), new ApcCorte()));
    private final LectorOrigenes lector = new LectorOrigenes(reglas, new CorteSmsService(reglas, null));

    private static byte[] recurso(String ruta) throws Exception {
        try (var entrada = LectorOrigenesTest.class.getResourceAsStream(ruta)) {
            return entrada.readAllBytes();
        }
    }

    @Test
    void elPedidoDeApcTraeElNombreDelBolsoYLaPielSinNombre() throws Exception {
        LecturaOrigen lectura = lector.dePedido("APC", recurso("/ejemplos/APC_PEDIDO_FALL26.xlsx"), null);

        ArticuloLeido articulo = lectura.articulos().stream()
                .filter(leido -> leido.referencia().equals("PXCBC-F67008")).findFirst().orElseThrow();
        assertEquals("F67008", articulo.modelo());
        assertEquals("PXCBC", articulo.refPiel());
        assertFalse(articulo.nombreModelo().isBlank(), "la Désignation del pedido es el nombre del bolso");
        assertEquals("", articulo.nombrePiel(), "ningún pedido trae el nombre de la piel");
        assertFalse(articulo.colores().isEmpty());
        assertTrue(articulo.colores().stream().allMatch(color -> !color.codigo().isEmpty()));
    }

    @Test
    void elPedidoDeAmiNoTraeNombreYLoCogeDelExcelDeProduccionDeLaTemporada() throws Exception {
        byte[] pedido = recurso("/ejemplos/EAN PUNTOTRES SS27.xlsx");

        LecturaOrigen solo = lector.dePedido("AMI", pedido, null);
        assertTrue(solo.articulos().stream().allMatch(articulo -> articulo.nombreModelo().isEmpty()),
                "el pedido de AMI no trae el nombre del bolso");
        assertTrue(solo.articulos().stream().anyMatch(articulo -> articulo.modelo().startsWith("UBL")),
                "entra el pedido entero, cinturones incluidos");

        LecturaOrigen conProduccion = lector.dePedido("AMI", pedido, recurso("/ejemplos/corte/PROD_AMI_SS27.xlsx"));
        assertEquals(solo.articulos().size(), conProduccion.articulos().size(),
                "el de producción solo rellena huecos: no añade artículos");
        assertTrue(conProduccion.articulos().stream().anyMatch(articulo -> !articulo.nombreModelo().isEmpty()),
                "el nombre del bolso sale del excel de producción");
    }

    @Test
    void unExcelDeProduccionIlegibleNoImpideLeerElPedido() throws Exception {
        LecturaOrigen lectura = lector.dePedido("APC", recurso("/ejemplos/APC_PEDIDO_FALL26.xlsx"),
                "no es un excel".getBytes());

        assertFalse(lectura.articulos().isEmpty());
        assertTrue(lectura.avisos().stream().anyMatch(aviso -> aviso.contains("producción")));
    }

    @Test
    void elExcelDeProduccionTraeElNombreDeLaPielYDelBolso() throws Exception {
        LecturaOrigen lectura = lector.deProduccion("APC", recurso("/ejemplos/corte/PROD_APC_SPRING27.xlsx"));

        assertFalse(lectura.articulos().isEmpty());
        assertTrue(lectura.articulos().stream().anyMatch(articulo -> !articulo.nombrePiel().isEmpty()));
        assertTrue(lectura.articulos().stream().anyMatch(articulo -> !articulo.nombreModelo().isEmpty()));
        assertTrue(lectura.articulos().stream().flatMap(articulo -> articulo.colores().stream())
                .anyMatch(color -> !color.colorPiel().isEmpty()));
    }

    @Test
    void elExcelDeProduccionValeParaUnGenericoYNoParteLaReferencia() throws Exception {
        LecturaOrigen lectura = lector.deProduccion("KAMI", recurso("/ejemplos/corte/PROD_APC_SPRING27.xlsx"));

        ArticuloLeido articulo = lectura.articulos().get(0);
        assertEquals(articulo.referencia(), articulo.modelo());
        assertEquals("", articulo.refPiel());
    }

    @Test
    void elExcelDeSmsJuntaLosColoresDeUnaReferenciaYTraePielYForro() throws Exception {
        LecturaOrigen lectura = lector.deSms("APC", recurso("/ejemplos/corte/COMMANDE_SMS_APC_FALL27.xlsx"));

        assertFalse(lectura.articulos().isEmpty());
        long referencias = lectura.articulos().stream().map(ArticuloLeido::referencia).distinct().count();
        assertEquals(lectura.articulos().size(), referencias, "una entrada por referencia");
        assertTrue(lectura.articulos().stream().anyMatch(articulo -> !articulo.nombrePiel().isEmpty()));
        assertTrue(lectura.articulos().stream().anyMatch(articulo -> !articulo.forro().isEmpty()));
    }

    @Test
    void soloApcTieneLectorDeSms() {
        assertTrue(lector.admite(OrigenCarga.SMS, "APC"));
        assertFalse(lector.admite(OrigenCarga.SMS, "AMI"));
        assertFalse(lector.admite(OrigenCarga.TEMPORADA, "KAMI"));
        assertTrue(lector.admite(OrigenCarga.PRODUCCION, "KAMI"));
        assertThrows(IllegalArgumentException.class, () -> lector.deSms("AMI", new byte[0]));
    }
}
