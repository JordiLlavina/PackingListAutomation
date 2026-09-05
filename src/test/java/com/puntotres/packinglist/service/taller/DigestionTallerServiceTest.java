package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.persistence.MemoriaReferencias;
import com.puntotres.packinglist.testutil.PackingTallerExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;

/**
 * La digestión cruza las tres fuentes: lo que ha llegado del taller, lo que
 * pide el cliente y lo que el programa recuerda de envíos anteriores.
 */
@SpringBootTest
class DigestionTallerServiceTest {

    @Autowired
    private DigestionTallerService digestion;

    @Autowired
    private MemoriaReferencias memoria;

    private static byte[] pedidoDe(String referencia, String color, int cantidad) {
        return PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida(referencia, color, "U", "07001 CH", cantidad));
    }

    private DigestionTaller digerir(byte[] taller, byte[] pedido) throws Exception {
        return digestion.digerir("AMI", taller, pedido);
    }

    @Test
    void lasFilasSeAgrupanPorReferenciaYNoPorColor() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "NOIR", 31),
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "BEIGE", 20));

        DigestionTaller resultado = digerir(taller, pedidoDe("BAG-A", "NOIR", 20));

        assertEquals(1, resultado.getGrupos().size());
        assertEquals(2, resultado.getGrupos().get(0).getFilas().size());
    }

    @Test
    void laTallaSeparaFilasDeLaMismaReferenciaYColor() throws Exception {
        // En cinturones cada talla es un artículo con su propio pedido:
        // juntarlas perdería el dato.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "UBL1", "2221", 10).conTalla("75"),
                PackingTallerExcel.Fila.de("AMI", "UBL1", "2221", 4).conTalla("85"));

        DigestionTaller resultado = digerir(taller, pedidoDe("UBL1", "2221", 10));

        assertEquals(1, resultado.getGrupos().size());
        assertEquals(2, resultado.getGrupos().get(0).getFilas().size());
    }

    // --- La cascada del cartón ---

    @Test
    void laMemoriaGanaALoQueSugiereElTaller() throws Exception {
        memoria.recordar("AMI", "CON-MEMORIA", "60x40x30", 6);
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "CON-MEMORIA", "NOIR", 20)
                        .conUnidadesPorCaja(10));

        GrupoReferencia grupo = digerir(taller, pedidoDe("CON-MEMORIA", "NOIR", 20))
                .getGrupos().get(0);

        assertEquals("60x40x30", grupo.getMedidaCaja());
        assertEquals(6, grupo.getUnidadesPorCaja());
        assertEquals(OrigenDato.MEMORIA, grupo.getOrigen());
    }

    @Test
    void sinMemoriaValeLoQueTraeElTaller() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "SIN-MEMORIA", "NOIR", 20)
                        .conUnidadesPorCaja(10));

        GrupoReferencia grupo = digerir(taller, pedidoDe("SIN-MEMORIA", "NOIR", 20))
                .getGrupos().get(0);

        assertEquals(10, grupo.getUnidadesPorCaja());
        assertEquals(OrigenDato.TALLER, grupo.getOrigen());
    }

    @Test
    void sinMemoriaYSinDatoDelTallerQuedaPendienteYNoSePuedeGenerar() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "SIN-NADA", "NOIR", 20));

        DigestionTaller resultado = digerir(taller, pedidoDe("SIN-NADA", "NOIR", 20));
        GrupoReferencia grupo = resultado.getGrupos().get(0);

        assertNull(grupo.getUnidadesPorCaja(), "null, nunca un centinela ni un cero");
        assertEquals(OrigenDato.POR_DEFECTO, grupo.getOrigen());
        assertTrue(grupo.estaPendiente());
        assertTrue(resultado.referenciasPendientes().contains("SIN-NADA"));
        assertTrue(!resultado.sePuedeGenerar());
    }

    @Test
    void loQueTecleaElUsuarioDesbloqueaElEnvio() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "SIN-NADA-2", "NOIR", 20));
        DigestionTaller resultado = digerir(taller, pedidoDe("SIN-NADA-2", "NOIR", 20));

        resultado.getGrupos().get(0).corregir("60x40x45", 8);

        assertTrue(resultado.sePuedeGenerar());
        assertEquals(8, resultado.getGrupos().get(0).getUnidadesPorCaja());
    }

    // --- Cruce con el pedido ---

    @Test
    void lasDestinacionesActivasSalenDelPedido() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "NOIR", 31).conUnidadesPorCaja(10));
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("BAG-A", "NOIR", "U", "07001 CH", 20),
                PedidoAmiExcel.Fila.pedida("BAG-A", "NOIR", "U", "07002 JP", 10));

        DigestionTaller resultado = digerir(taller, pedido);

        assertEquals(List.of("CHINA", "JAPAN"), resultado.getDestinosActivos());
        assertEquals(20, resultado.getGrupos().get(0).getFilas().get(0).cantidadPara("CHINA"));
    }

    @Test
    void unaReferenciaQueNoEstaEnElPedidoSeMarcaYNoBloquea() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "FANTASMA", "NOIR", 10).conUnidadesPorCaja(10));

        DigestionTaller resultado = digerir(taller, pedidoDe("OTRA", "NOIR", 20));

        assertTrue(resultado.getGrupos().get(0).getFilas().get(0).isSinPedido());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("FANTASMA")));
        assertTrue(resultado.getBloqueos().isEmpty());
    }

    @Test
    void elMismoAvisoNoSeRepiteUnaVezPorFila() throws Exception {
        // Una referencia ocupa varias filas del packing del taller (un tramo
        // de cajas por destinación). Sin deduplicar, su aviso salía seis veces
        // y la lista se volvía ilegible justo cuando hay que leerla.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "REPETIDA", "NOIR", 10).conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("AMI", "REPETIDA", "NOIR", 10).conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("AMI", "REPETIDA", "NOIR", 10).conUnidadesPorCaja(10));

        DigestionTaller resultado = digerir(taller, pedidoDe("OTRA", "NOIR", 20));

        assertEquals(1, resultado.getAvisos().stream()
                .filter(aviso -> aviso.contains("REPETIDA")).count());
    }

    @Test
    void laDestinacionDelTallerQueNoCuadraConElPedidoSoloAvisa() throws Exception {
        // El taller apunta "JA" y el pedido manda a CHINA: manda el pedido.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "NOIR", 20)
                        .conUnidadesPorCaja(10).conDestino("JA"));

        DigestionTaller resultado = digerir(taller, pedidoDe("BAG-A", "NOIR", 20));

        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("JA")));
        assertTrue(resultado.getBloqueos().isEmpty());
        assertEquals(List.of("CHINA"), resultado.getDestinosActivos());
    }

    @Test
    void sinExcelDePedidoElObjetivoEsLoQueHaLlegado() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "NOIR", 20).conUnidadesPorCaja(10));

        DigestionTaller resultado = digestion.digerir("AMI", taller, null);

        assertEquals(20, resultado.getGrupos().get(0).getFilas().get(0)
                .getObjetivos().get(0).cantidad());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("pedido")));
    }

    // --- Bloqueos ---

    @Test
    void unaHojaConDosClientesDistintosBloquea() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "NOIR", 20).conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("PALOMA WOOL", "MOD-X", "ROUGE", 5)
                        .conUnidadesPorCaja(10));

        DigestionTaller resultado = digerir(taller, pedidoDe("BAG-A", "NOIR", 20));

        assertTrue(resultado.getBloqueos().stream().anyMatch(b -> b.contains("PALOMA WOOL")));
    }

    // --- Salida hacia el algoritmo ---

    @Test
    void laDigestionSeConvierteEnLasFilasQueEntranAlAlgoritmo() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "NOIR", 31).conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "BEIGE", 20).conUnidadesPorCaja(10));
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("BAG-A", "NOIR", "U", "07001 CH", 20),
                PedidoAmiExcel.Fila.pedida("BAG-A", "BEIGE", "U", "07001 CH", 10));

        List<FilaAjustada> filas = digerir(taller, pedido).aFilasAjustadas();

        assertEquals(2, filas.size());
        assertEquals(31, filas.get(0).recibido());
        assertEquals(10, filas.get(0).unidadesPorCaja());
        assertEquals("60x40x40", filas.get(0).medidaCaja());
    }

    @Test
    void generarMemorizaLoQueSeHaUsado() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "APRENDIDA", "NOIR", 20).conUnidadesPorCaja(7));
        DigestionTaller resultado = digerir(taller, pedidoDe("APRENDIDA", "NOIR", 20));

        digestion.memorizar("AMI", resultado);

        assertEquals(7, memoria.buscar("AMI", "APRENDIDA").orElseThrow().unidadesPorCaja());
    }

    @Test
    void noSeMemorizaUnaReferenciaQueSeHaQuedadoPendiente() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "NO-APRENDIDA", "NOIR", 20));
        DigestionTaller resultado = digerir(taller, pedidoDe("NO-APRENDIDA", "NOIR", 20));

        digestion.memorizar("AMI", resultado);

        assertTrue(memoria.buscar("AMI", "NO-APRENDIDA").isEmpty());
    }
}
