package com.puntotres.packinglist.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Cada packing list lleva la factura de SU destinación; la del envío (la de
 * la pantalla de entrada, que ya no es obligatoria) solo es la de partida.
 */
class DatosEnvioTest {

    private static DestinoData destino(String factura) {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("WHOLESALE");
        destino.setNumeroFactura(factura);
        return destino;
    }

    @Test
    void laFacturaDeLaDestinacionMandaSobreLaDelEnvio() {
        DatosEnvio envio = new DatosEnvio();
        envio.setNumeroFactura("FA-1");

        assertEquals("FA-7", envio.facturaPara(destino(" FA-7 ")));
    }

    @Test
    void sinFacturaPropiaLaDestinacionLlevaLaDelEnvio() {
        DatosEnvio envio = new DatosEnvio();
        envio.setNumeroFactura("FA-1");

        assertEquals("FA-1", envio.facturaPara(destino(null)));
        assertEquals("FA-1", envio.facturaPara(destino("  ")));
    }

    @Test
    void sinNingunaDeLasDosNoSeInventaNinguna() {
        assertNull(new DatosEnvio().facturaPara(destino(null)));
    }
}
