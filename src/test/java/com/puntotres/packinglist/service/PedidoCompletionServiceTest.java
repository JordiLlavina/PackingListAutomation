package com.puntotres.packinglist.service;

import static com.puntotres.packinglist.testutil.TestDatos.caja;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DestinoData;

class PedidoCompletionServiceTest {

    private final PedidoCompletionService servicio = new PedidoCompletionService();

    private static byte[] pedidoReal() throws IOException {
        try (InputStream in = PedidoCompletionServiceTest.class
                .getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            return in.readAllBytes();
        }
    }

    private static List<EnvioImportado.DestinoImportado> envioCon(CajaData... cajas) {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("WHOLESALE");
        destino.setCajas(new ArrayList<>(List.of(cajas)));
        return List.of(new EnvioImportado.DestinoImportado(destino, List.of()));
    }

    @Test
    void completaElNumeroDePedidoDeCadaCaja() throws IOException {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado = servicio.completar(envioCon(linea), pedidoReal());

        assertEquals("4100128721", linea.getNumeroPedido());
        assertTrue(resultado.getAvisos().isEmpty());
    }

    @Test
    void sinExcelDePedidoAvisaUnaVezYNoTocaNada() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado = servicio.completar(envioCon(linea), null);

        assertEquals("721", linea.getNumeroPedido());
        assertEquals(1, resultado.getAvisos().size());
    }

    @Test
    void unaReferenciaQueNoEstaEnElPedidoAvisaUnaSolaVezPorClave() throws IOException {
        CajaData una = caja(1, "999", "NO-EXISTE", "LZZ-NOIR", 3, null, 4.2);
        CajaData otra = caja(2, "999", "NO-EXISTE", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado = servicio.completar(envioCon(una, otra), pedidoReal());

        assertEquals("999", una.getNumeroPedido());
        assertEquals(1, resultado.getAvisos().size());
        assertTrue(resultado.getAvisos().get(0).contains("NO-EXISTE"));
    }

    @Test
    void unFicheroIlegibleAvisaEnVezDeRomperElEnvio() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado =
                servicio.completar(envioCon(linea), "esto no es un xlsx".getBytes());

        assertEquals("721", linea.getNumeroPedido());
        assertEquals(1, resultado.getAvisos().size());
    }

    @Test
    void unaCajaSinPedidoSeSaltaSinAvisoDeBusqueda() throws IOException {
        CajaData linea = caja(1, null, "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado = servicio.completar(envioCon(linea), pedidoReal());

        assertNull(linea.getNumeroPedido());
        assertTrue(resultado.getAvisos().isEmpty());
    }
}
