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
    void unaReferenciaManuscritaSeCompletaConLaDelExcel() throws IOException {
        // De las hojas llega "H65077" (lo que escribe el operario), no el
        // Article completo: la fila se encuentra por sufijo y se estampan el
        // pedido Y la referencia enteros.
        CajaData linea = caja(1, "721", "H65077", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado = servicio.completar(envioCon(linea), pedidoReal());

        assertEquals("4100128721", linea.getNumeroPedido());
        assertEquals("PXBHZ-H65077", linea.getReferencia());
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

    // --- nombre del modelo (MODÈLE) ---

    /**
     * El MODÈLE sale de la Désignation del pedido, y manda sobre lo que
     * traiga la entrada: es el nombre oficial del cliente.
     */
    @Test
    void elModeloSaleDelPedidoYMandaSobreElDeLaEntrada() throws IOException {
        CajaData sinModelo = caja(1, "4100128721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        CajaData conOtro = caja(2, "4100128710", "PXCBC-F67008", "LZZ-NOIR", 3, null, 4.2);
        conOtro.setModelo("sac le neige");

        List<String> avisos = servicio.completarModelos(envioCon(sinModelo, conOtro), pedidoReal());

        assertEquals("CEINTURE PARIS", sinModelo.getModelo());
        assertEquals("LE NEIGE", conOtro.getModelo());
        assertTrue(avisos.isEmpty(), avisos.toString());
    }

    /**
     * Una referencia que el pedido no conoce se queda con lo que trajo la
     * entrada; si no trajo nada, se avisa: la columna saldría en blanco.
     */
    @Test
    void sinNombreEnElPedidoSeQuedaElDeLaEntradaYSiNoHayNingunoSeAvisa() throws IOException {
        CajaData conModelo = caja(1, "4100128721", "PXZZZ-F99999", "LZZ-NOIR", 3, null, 4.2);
        conModelo.setModelo("modelo de la hoja");
        CajaData sinNada = caja(2, "4100128721", "PXZZZ-F99998", "LZZ-NOIR", 3, null, 4.2);

        List<String> avisos = servicio.completarModelos(envioCon(conModelo, sinNada), pedidoReal());

        assertEquals("modelo de la hoja", conModelo.getModelo());
        assertNull(sinNada.getModelo());
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).contains("PXZZZ-F99998") && !avisos.get(0).contains("F99999"),
                avisos.get(0));
    }

    @Test
    void sinExcelDePedidoAvisaDeLasReferenciasQueSeQuedanSinModelo() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        List<String> avisos = servicio.completarModelos(envioCon(linea), null);

        assertNull(linea.getModelo());
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).contains("MODÈLE"), avisos.get(0));
    }

    @Test
    void unaCajaSinPedidoSeSaltaSinAvisoDeBusqueda() throws IOException {
        CajaData linea = caja(1, null, "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado = servicio.completar(envioCon(linea), pedidoReal());

        assertNull(linea.getNumeroPedido());
        assertTrue(resultado.getAvisos().isEmpty());
    }
}
