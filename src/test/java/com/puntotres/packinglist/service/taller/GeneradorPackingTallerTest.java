package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.model.EnvioInput;

/**
 * El ejemplo trabajado del diseño, extremo a extremo y contra la
 * configuración real de application.yml: reparto, cajas, palets y numeración.
 *
 * Va con contexto de Spring a propósito. Con reglas de pega comprobaría mi
 * idea de las normas de AMI; así comprueba las que están escritas en el yml,
 * que son las que van a correr.
 */
@SpringBootTest
class GeneradorPackingTallerTest {

    @Autowired
    private GeneradorPackingTaller generador;

    /** BAG-A en cartón de 40 de alto, BAG-B en uno de 30. */
    private static List<FilaAjustada> ejemplo() {
        return List.of(
                new FilaAjustada("BAG-A", "NOIR", "U", 31, "60x40x40", 10,
                        List.of(new ObjetivoDestino("CHINA", 20, "07001"),
                                new ObjetivoDestino("PARIS", 20, "07001"))),
                new FilaAjustada("BAG-A", "BEIGE", "U", 20, "60x40x40", 10,
                        List.of(new ObjetivoDestino("CHINA", 10, "07001"),
                                new ObjetivoDestino("PARIS", 10, "07001"))),
                new FilaAjustada("BAG-B", "NOIR", "U", 12, "60x40x30", 6,
                        List.of(new ObjetivoDestino("CHINA", 6, "07001"),
                                new ObjetivoDestino("PARIS", 6, "07001"))));
    }

    private ResultadoPackingTaller generar() {
        return generador.generar("AMI", ejemplo(), null);
    }

    private static EnvioInput.DestinoInput destino(EnvioInput envio, String nombre) {
        return envio.getDestinos().stream()
                .filter(d -> d.getDestino().equals(nombre))
                .findFirst().orElseThrow();
    }

    private static List<Integer> numerosDeCaja(EnvioInput.DestinoInput destino) {
        List<Integer> numeros = new ArrayList<>();
        for (EnvioInput.ReferenciaInput referencia : destino.getReferencias()) {
            for (EnvioInput.CajaRangoInput caja : referencia.getCajas()) {
                if (caja.esRango()) {
                    IntStream.rangeClosed(caja.getCajaInicio(), caja.getCajaFin())
                            .forEach(numeros::add);
                } else {
                    numeros.add(caja.getCaja());
                }
            }
        }
        return numeros.stream().distinct().sorted().toList();
    }

    private static int cajasDe(EnvioInput envio, String nombre) {
        return numerosDeCaja(destino(envio, nombre)).size();
    }

    private static List<Integer> unidadesPorCajaDe(EnvioInput envio, String nombre,
                                                   String referencia, String color) {
        return destino(envio, nombre).getReferencias().stream()
                .filter(r -> r.getReferencia().equals(referencia) && r.getColor().equals(color))
                .flatMap(r -> r.getCajas().stream())
                .flatMap(caja -> caja.esRango()
                        ? IntStream.rangeClosed(caja.getCajaInicio(), caja.getCajaFin())
                                .mapToObj(n -> caja.getUnidadesPorCaja())
                        : java.util.stream.Stream.of(caja.getUnidades()))
                .toList();
    }

    private static int unidadesDe(EnvioInput envio, String nombre,
                                  String referencia, String color) {
        return unidadesPorCajaDe(envio, nombre, referencia, color).stream()
                .mapToInt(Integer::intValue).sum();
    }

    // --- Reparto ---

    @Test
    void chinaTienePrioridadYParisSeQuedaCorta() {
        EnvioInput envio = generar().getEnvio();

        assertEquals(20, unidadesDe(envio, "CHINA", "BAG-A", "NOIR"));
        assertEquals(11, unidadesDe(envio, "PARIS", "BAG-A", "NOIR"));
    }

    @Test
    void loQueSobraSeCuentaComoAviso() {
        assertTrue(generar().getAvisos().stream().anyMatch(a -> a.contains("PARIS")));
    }

    // --- Cajas ---

    @Test
    void chinaSaleConCuatroCajasSinMezclarNada() {
        EnvioInput envio = generar().getEnvio();

        assertEquals(4, cajasDe(envio, "CHINA"));
        assertEquals(List.of(10, 10), unidadesPorCajaDe(envio, "CHINA", "BAG-A", "NOIR"));
        assertEquals(List.of(10), unidadesPorCajaDe(envio, "CHINA", "BAG-A", "BEIGE"));
        assertEquals(List.of(6), unidadesPorCajaDe(envio, "CHINA", "BAG-B", "NOIR"));
    }

    @Test
    void parisSaleConCuatroCajasYLosColoresNoSeMezclan() {
        // BAG-A NOIR (11) y BEIGE (10) comparten pedido y cartón, así que
        // podrían mezclarse; no lo hacen porque no ahorraría ninguna caja.
        EnvioInput envio = generar().getEnvio();

        assertEquals(4, cajasDe(envio, "PARIS"));
        assertEquals(List.of(6, 5), unidadesPorCajaDe(envio, "PARIS", "BAG-A", "NOIR"));
        assertEquals(List.of(10), unidadesPorCajaDe(envio, "PARIS", "BAG-A", "BEIGE"));
    }

    // --- Palets y numeración ---

    @Test
    void lasCuatroCajasDeChinaCabenEnUnPalet() {
        // Altura útil 158 - 11 = 147 cm; tres cajas de 40 y una de 30.
        assertEquals(1, destino(generar().getEnvio(), "CHINA").getPalets().size());
    }

    @Test
    void cadaPaletEsUnRangoContiguoDeNumerosDeCaja() {
        // Sin esto, PaletAssignmentService no podría volver a asignar los
        // palets al importar el envío generado.
        EnvioInput envio = generar().getEnvio();

        for (EnvioInput.DestinoInput destino : envio.getDestinos()) {
            List<Integer> numeros = numerosDeCaja(destino);
            for (EnvioInput.PaletInput palet : destino.getPalets()) {
                long dentro = numeros.stream()
                        .filter(n -> n >= palet.getCajaInicio() && n <= palet.getCajaFin())
                        .count();
                assertEquals(palet.getCajaFin() - palet.getCajaInicio() + 1, dentro,
                        "el palet " + palet.getPalet() + " de " + destino.getDestino()
                                + " no cubre un rango contiguo");
            }
        }
    }

    @Test
    void todaCajaTieneUnPalet() {
        EnvioInput envio = generar().getEnvio();

        for (EnvioInput.DestinoInput destino : envio.getDestinos()) {
            for (int numero : numerosDeCaja(destino)) {
                assertTrue(destino.getPalets().stream()
                        .anyMatch(p -> numero >= p.getCajaInicio() && numero <= p.getCajaFin()),
                        "la caja " + numero + " de " + destino.getDestino() + " no está en ningún palet");
            }
        }
    }

    @Test
    void amiNumeraLasCajasSeguidoEntreDestinaciones() {
        EnvioInput envio = generar().getEnvio();

        List<Integer> todos = envio.getDestinos().stream()
                .flatMap(d -> numerosDeCaja(d).stream())
                .sorted().toList();
        assertEquals(IntStream.rangeClosed(1, 8).boxed().toList(), todos);
    }

    @Test
    void apcReiniciaLaNumeracionEnCadaDestinacion() {
        List<FilaAjustada> filas = List.of(
                new FilaAjustada("PXCBC-F67008", "CAMEL", "U", 30, "60x40x40", 10,
                        List.of(new ObjetivoDestino("JAPAN", 10, "4100000001"),
                                new ObjetivoDestino("KOREA", 10, "4100000002"))));

        EnvioInput envio = generador.generar("APC", filas, null).getEnvio();

        assertEquals(2, envio.getDestinos().size());
        assertTrue(envio.getDestinos().stream()
                .allMatch(d -> numerosDeCaja(d).contains(1)));
    }

    // --- Lo que queda para los pasos siguientes ---

    @Test
    void losPesosVanEnBlancoParaQueLosInfieraElPasoDeSiempre() {
        EnvioInput envio = generar().getEnvio();

        assertTrue(envio.getDestinos().stream()
                .flatMap(d -> d.getReferencias().stream())
                .flatMap(r -> r.getCajas().stream())
                .allMatch(c -> c.getPesoBruto() == null));
    }

    @Test
    void cadaReferenciaLlevaSuCartonParaQueLaTaraSeaLaSuya() {
        EnvioInput envio = generar().getEnvio();

        assertEquals("60x40x40", destino(envio, "CHINA").getReferencias().stream()
                .filter(r -> r.getReferencia().equals("BAG-A"))
                .findFirst().orElseThrow().getMedidaCaja());
        assertEquals("60x40x30", destino(envio, "CHINA").getReferencias().stream()
                .filter(r -> r.getReferencia().equals("BAG-B"))
                .findFirst().orElseThrow().getMedidaCaja());
    }

    @Test
    void elEnvioLlevaElClienteSeleccionado() {
        assertEquals("AMI", generar().getEnvio().getCliente());
    }

    // --- Resumen ---

    @Test
    void elResumenCuentaLoMismoQueElEnvio() {
        ResultadoPackingTaller resultado = generar();

        ResumenDestino china = resultado.getResumen().stream()
                .filter(r -> r.destino().equals("CHINA")).findFirst().orElseThrow();
        assertEquals(36, china.unidades());
        assertEquals(4, china.cajas());
        assertEquals(1, china.palets());
        assertEquals(147, china.alturaUtilCm());
        assertEquals(40, china.alturaUltimoPaletCm());
    }

    // --- Bloqueos ---

    @Test
    void conUnaFilaSinUnidadesPorCajaNoSeGeneraNada() {
        List<FilaAjustada> filas = List.of(
                new FilaAjustada("BAG-A", "NOIR", "U", 10, "60x40x40", null,
                        List.of(new ObjetivoDestino("CHINA", 10, "07001"))));

        ResultadoPackingTaller resultado = generador.generar("AMI", filas, null);

        assertTrue(resultado.getBloqueos().stream().anyMatch(b -> b.contains("BAG-A")));
        assertTrue(resultado.getEnvio().getDestinos().isEmpty());
    }

    @Test
    void unaCajaMasAltaQueElPaletBloquea() {
        List<FilaAjustada> filas = List.of(
                new FilaAjustada("BAG-A", "NOIR", "U", 10, "60x40x200", 10,
                        List.of(new ObjetivoDestino("CHINA", 10, "07001"))));

        ResultadoPackingTaller resultado = generador.generar("AMI", filas, null);

        assertTrue(resultado.getBloqueos().stream().anyMatch(b -> b.contains("200")));
    }
}
