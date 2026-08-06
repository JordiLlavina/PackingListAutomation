package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.DestinoClienteConfig;
import com.puntotres.packinglist.model.EnvioInput;

class ValidadorResumenExtraccionTest {

    private final ValidadorResumenExtraccion validador = new ValidadorResumenExtraccion();

    // --- helpers ---

    private static EnvioInput.DestinoInput destino(String nombre, int palets) {
        EnvioInput.DestinoInput destino = new EnvioInput.DestinoInput();
        destino.setDestino(nombre);
        destino.setReferencias(List.of());
        if (palets > 0) {
            destino.setPalets(new java.util.ArrayList<>());
            for (int i = 1; i <= palets; i++) {
                EnvioInput.PaletInput palet = new EnvioInput.PaletInput();
                palet.setPalet(i);
                destino.getPalets().add(palet);
            }
        }
        return destino;
    }

    private static EnvioInput.ResumenPaletsInput declarado(String destino, Integer palets) {
        EnvioInput.ResumenPaletsInput resumen = new EnvioInput.ResumenPaletsInput();
        resumen.setDestino(destino);
        resumen.setPalets(palets);
        return resumen;
    }

    private static EnvioInput envio(List<EnvioInput.DestinoInput> destinos,
                                    List<EnvioInput.ResumenPaletsInput> resumen) {
        EnvioInput envio = new EnvioInput();
        envio.setDestinos(destinos);
        envio.setResumenPalets(resumen);
        return envio;
    }

    /** Cliente con WHOLESALE como destino padre de AUSTRALIA, como APC. */
    private static ClienteConfig clienteConPadres() {
        ClienteConfig cliente = new ClienteConfig();
        DestinoClienteConfig wholesale = new DestinoClienteConfig();
        wholesale.setDestinosHijo(List.of("AUSTRALIA", "CHINE FRANCH"));
        cliente.setDestinos(java.util.Map.of("WHOLESALE", wholesale));
        return cliente;
    }

    // --- tests ---

    @Test
    void sinResumenNoHayNadaQueContrastarYTodoPasa() {
        ValidadorResumenExtraccion.ResultadoResumen resultado = validador.validar(
                envio(List.of(destino("JAPAN", 0)), null), null);
        assertTrue(resultado.errores().isEmpty());
        assertTrue(resultado.avisos().isEmpty());
    }

    @Test
    void unaDestinacionDeclaradaSinHojaDePackingEsError() {
        // El caso real: el resumen dice "wh. 5 palet" y el documento no trae
        // ninguna hoja de WHOLESALE — falta una hoja en el escaneo.
        ValidadorResumenExtraccion.ResultadoResumen resultado = validador.validar(
                envio(List.of(destino("JAPAN", 1)),
                        List.of(declarado("WHOLESALE", 5))), null);
        assertEquals(1, resultado.errores().size());
        assertTrue(resultado.errores().get(0).contains("WHOLESALE"));
        assertTrue(resultado.errores().get(0).contains("falta una hoja"));
    }

    @Test
    void unRecuentoQueNoCuadraConLosPaletsLeidosEsError() {
        ValidadorResumenExtraccion.ResultadoResumen resultado = validador.validar(
                envio(List.of(destino("JAPAN", 2)),
                        List.of(declarado("Japan", 3))), null);
        assertEquals(1, resultado.errores().size());
        assertTrue(resultado.errores().get(0).contains("declara 3"));
        assertTrue(resultado.errores().get(0).contains("2"));
    }

    @Test
    void unRecuentoSinRepartoDeCajasSoloAvisa() {
        // "Korea - 1 palet" sin tabla de cajas: se completa en la revisión,
        // no bloquea.
        ValidadorResumenExtraccion.ResultadoResumen resultado = validador.validar(
                envio(List.of(destino("KOREA", 0)),
                        List.of(declarado("Korea", 1))), null);
        assertTrue(resultado.errores().isEmpty());
        assertEquals(1, resultado.avisos().size());
        assertTrue(resultado.avisos().get(0).contains("revisión"));
    }

    @Test
    void elRecuentoDelPadreCasaConLasHojasDeSusHijas() {
        // "wh. 5 palet" con hojas de AUSTRALIA (2) y CHINE FRANCH (3): las
        // hijas se resuelven al padre y los palets se suman.
        ValidadorResumenExtraccion.ResultadoResumen resultado = validador.validar(
                envio(List.of(destino("Australia", 2), destino("Chine franch", 3)),
                        List.of(declarado("wholesale", 5))),
                clienteConPadres());
        assertTrue(resultado.errores().isEmpty());
        assertTrue(resultado.avisos().isEmpty());
    }

    @Test
    void losRecuentosQueCuadranNoDicenNada() {
        ValidadorResumenExtraccion.ResultadoResumen resultado = validador.validar(
                envio(List.of(destino("JAPAN", 1), destino("KOREA", 1)),
                        List.of(declarado("JAPAN", 1), declarado("KOREA", 1))), null);
        assertTrue(resultado.errores().isEmpty());
        assertTrue(resultado.avisos().isEmpty());
    }

    @Test
    void unaDestinacionConHojasPeroSinRecuentoNoMolesta() {
        // El resumen de palets del operario solo lista las destinaciones
        // paletizadas: D. USA puede viajar sin palet y no aparecer.
        ValidadorResumenExtraccion.ResultadoResumen resultado = validador.validar(
                envio(List.of(destino("JAPAN", 1), destino("D. USA", 0)),
                        List.of(declarado("JAPAN", 1))), null);
        assertTrue(resultado.errores().isEmpty());
        assertTrue(resultado.avisos().isEmpty());
    }
}
