package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.EnvioInput;

/**
 * Valida la importación contra el JSON real de ejemplo
 * (client-packinglist/packing_list_ami_test.json).
 */
class EnvioImportServiceTest {

    private final EnvioImportService service = new EnvioImportService();

    private EnvioImportado importarJsonDePrueba() throws Exception {
        try (InputStream json = getClass()
                .getResourceAsStream("/client-packinglist/packing_list_ami_test.json")) {
            EnvioInput envio = new ObjectMapper().readValue(json, EnvioInput.class);
            return service.importar(envio);
        }
    }

    @Test
    void expandeLosRangosDeCajasYRellenaLosDatosDeLaReferencia() throws Exception {
        EnvioImportado importado = importarJsonDePrueba();

        assertEquals(3, importado.getDestinos().size());

        // PARIS: rango 1-30 + cajas 31,32 (USL728) y 33,35 en DOS colores de
        // USL737 (caja mixta: el mismo número aparece en ROJO y NOIR) = 36 cajas
        List<CajaData> paris = importado.getDestinos().get(0).getDestino().getCajas();
        assertEquals(36, paris.size());

        CajaData caja30 = paris.get(29);
        assertEquals(30, caja30.getNumeroCaja());
        assertEquals(50, caja30.getCantidad());               // del rango, unidadesPorCaja
        assertEquals("USL728.AL217", caja30.getReferencia());
        assertEquals("NOIR", caja30.getCodigoColor());
        assertEquals("60x40x40", caja30.getTamanoCaja());
        assertEquals("07685", caja30.getNumeroPedido());

        CajaData caja32 = paris.get(31);
        assertEquals(47, caja32.getCantidad());               // caja suelta

        CajaData caja33 = paris.get(32);
        assertEquals("USL737.ACO137", caja33.getReferencia());
        assertEquals("60x40x30", caja33.getTamanoCaja());

        // JAPAN y CHINA
        assertEquals(3, importado.getDestinos().get(1).getDestino().getCajas().size());
        assertEquals(2, importado.getDestinos().get(2).getDestino().getCajas().size());
    }

    @Test
    void montaLosPaletsConSuDestinacion() throws Exception {
        EnvioImportado importado = importarJsonDePrueba();

        assertEquals(3, importado.getDestinos().get(0).getPalets().size());
        assertEquals("PARIS", importado.getDestinos().get(0).getPalets().get(0).getDestino());
        assertEquals(25, importado.getDestinos().get(0).getPalets().get(2).getCajaInicio());
        assertEquals(35, importado.getDestinos().get(0).getPalets().get(2).getCajaFin());
    }

    @Test
    void avisaCuandoLaSumaDeUnidadesNoCuadraConCantidadTotal() throws Exception {
        EnvioImportado importado = importarJsonDePrueba();

        // El JSON de prueba contiene 4 avisos a propósito. Dos descuadres:
        // USL728: 30x50+50+47 = 1597 vs cantidadTotal 1897
        // USL737 NOIR: 34+37 = 71 vs cantidadTotal 67
        // Y dos duplicados: las cajas 33 y 35 aparecen en dos colores de
        // USL737 (caja mixta), que hoy se reporta como número repetido.
        assertEquals(4, importado.getAvisos().size());
        assertTrue(importado.getAvisos().get(0).contains("1597"));
        assertTrue(importado.getAvisos().get(0).contains("1897"));
        assertTrue(importado.getAvisos().get(1).contains("71"));
        assertTrue(importado.getAvisos().get(1).contains("67"));
        assertTrue(importado.getAvisos().get(2).contains("33"));
        assertTrue(importado.getAvisos().get(2).contains("más de una vez"));
        assertTrue(importado.getAvisos().get(3).contains("35"));
    }

    @Test
    void sinCantidadTotalNoGeneraAvisoDeDescuadre() {
        EnvioInput envio = envioConUnaReferencia(referencia -> {
            referencia.setCantidadTotal(null);
            referencia.setCajas(List.of(cajaSuelta(1, 50)));
        });

        EnvioImportado importado = service.importar(envio);

        assertTrue(importado.getAvisos().isEmpty());
        assertEquals(1, importado.getDestinos().get(0).getDestino().getCajas().size());
    }

    /**
     * Comportamiento actual, documentado a propósito: un rango invertido
     * (cajaInicio > cajaFin, p. ej. por un error de lectura de imagen) no
     * genera ninguna caja ni ningún aviso. Es un gap conocido y aceptado
     * (no se corrige aquí); este test existe para que un cambio futuro en
     * este comportamiento sea una decisión explícita, no un efecto colateral.
     */
    @Test
    void rangoInvertidoNoGeneraCajasNiAviso() {
        EnvioInput envio = envioConUnaReferencia(referencia -> {
            referencia.setCantidadTotal(null);
            referencia.setCajas(List.of(rango(10, 5, 50)));
        });

        EnvioImportado importado = service.importar(envio);

        assertTrue(importado.getDestinos().get(0).getDestino().getCajas().isEmpty());
        assertTrue(importado.getAvisos().isEmpty());
    }

    private static EnvioInput envioConUnaReferencia(
            java.util.function.Consumer<EnvioInput.ReferenciaInput> configurar) {
        EnvioInput.ReferenciaInput referencia = new EnvioInput.ReferenciaInput();
        referencia.setReferencia("USL728.AL217.001");
        referencia.setColor("NOIR");
        referencia.setMedidaCaja("60x40x40");
        referencia.setPedido("07685");
        configurar.accept(referencia);

        EnvioInput.DestinoInput destino = new EnvioInput.DestinoInput();
        destino.setDestino("PARIS");
        destino.setReferencias(List.of(referencia));
        destino.setPalets(List.of());

        EnvioInput envio = new EnvioInput();
        envio.setDestinos(List.of(destino));
        return envio;
    }

    private static EnvioInput.CajaRangoInput cajaSuelta(int numero, int unidades) {
        EnvioInput.CajaRangoInput entrada = new EnvioInput.CajaRangoInput();
        entrada.setCaja(numero);
        entrada.setUnidades(unidades);
        return entrada;
    }

    private static EnvioInput.CajaRangoInput rango(int inicio, int fin, int unidadesPorCaja) {
        EnvioInput.CajaRangoInput entrada = new EnvioInput.CajaRangoInput();
        entrada.setCajaInicio(inicio);
        entrada.setCajaFin(fin);
        entrada.setUnidadesPorCaja(unidadesPorCaja);
        return entrada;
    }
}
