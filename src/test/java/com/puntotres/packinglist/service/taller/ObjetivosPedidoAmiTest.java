package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.NumeracionCajas;
import com.puntotres.packinglist.config.ReglaClienteTaller;
import com.puntotres.packinglist.config.ReglasTallerProperties;
import com.puntotres.packinglist.service.etiquetas.AmiPedidoExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;

/**
 * En AMI la cantidad objetivo y la destinación salen del mismo excel de
 * pedido que ya se sube para las etiquetas: la cantidad de la columna
 * "Commandé" y la destinación del sufijo del PO ("07704 CH").
 */
class ObjetivosPedidoAmiTest {

    private static byte[] pedidoReal() throws IOException {
        try (InputStream in = ObjetivosPedidoAmiTest.class
                .getResourceAsStream("/ejemplos/EAN PUNTOTRES H26.xlsx")) {
            return in.readAllBytes();
        }
    }

    /** Las reglas de AMI, montadas a mano para no depender del yml. */
    private static ReglasTallerProperties reglasDeAmi() {
        ReglaClienteTaller ami = new ReglaClienteTaller();
        ami.setNumeracionCajas(NumeracionCajas.CONTINUA);
        ami.setSufijosPo(java.util.Map.of("CH", "CHINA", "JP", "JAPAN"));
        ami.setDestinoSinSufijo("PARIS");

        ReglasTallerProperties reglas = new ReglasTallerProperties();
        reglas.setClientes(java.util.Map.of("AMI", ami));
        return reglas;
    }

    private static LineaTaller linea(String referencia, String color, String talla) {
        return new LineaTaller(10, "AMI", "PROD", referencia, color, talla,
                "", "", "", 8, 50);
    }

    // --- Contra el excel de pedido real de AMI ---

    @Test
    void elFicheroRealSoloTraePedidosDeChinaYJapon() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoReal());

        assertEquals(Set.of("CH", "JP"), new HashSet<>(pedido.sufijosPo()));
    }

    @Test
    void laCantidadObjetivoSaleDeLaColumnaCommande() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoReal());

        // No se comprueba CUÁNTO pide el cliente —eso cambia cada temporada—,
        // sino que la columna se lee y trae cantidades de verdad.
        assertTrue(pedido.comandasTodas().stream().anyMatch(c -> c.cantidad() > 0),
                "el pedido real tiene cantidades en 'Commandé'");
    }

    @Test
    void unaLineaDeTallerSeConvierteEnObjetivosPorDestinacion() throws IOException {
        LineaTaller linea = linea("UBL214.AL0223", "2221", "75");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedidoReal());

        List<String> destinos = resultado.objetivosDe(linea).stream()
                .map(ObjetivoDestino::destino).toList();
        assertTrue(destinos.contains("CHINA") || destinos.contains("JAPAN"),
                "esa referencia está en el pedido real, con PO de CH o de JP");
        assertTrue(resultado.getBloqueos().isEmpty());
    }

    @Test
    void elPedidoQueViajaAlPackingEsElNumeroSinElSufijo() throws IOException {
        LineaTaller linea = linea("UBL214.AL0223", "2221", "75");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedidoReal());

        assertTrue(resultado.objetivosDe(linea).stream()
                .allMatch(o -> o.pedido().matches("\\d{5}")),
                "el sufijo dice la destinación, no forma parte del nº de pedido");
    }

    // --- Con pedidos de prueba ---

    @Test
    void variasTallasDelMismoDestinoNoSeSuman() {
        // Un cinturón con tallas 75 y 85 en el mismo PO: la línea de taller de
        // la talla 75 se lleva lo suyo, no la suma de las dos.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("UBL1", "2221", "75", "07001 CH", 10),
                PedidoAmiExcel.Fila.pedida("UBL1", "2221", "85", "07001 CH", 4));
        LineaTaller linea = linea("UBL1", "2221", "75");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedido);

        assertEquals(1, resultado.objetivosDe(linea).size());
        assertEquals(10, resultado.objetivosDe(linea).get(0).cantidad());
    }

    @Test
    void variasFilasDeLaMismaClaveYDestinoSiSeSuman() {
        // El mismo artículo repartido en dos líneas de pedido del mismo PO.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("ULL1", "A236", "U", "07001 CH", 10),
                PedidoAmiExcel.Fila.pedida("ULL1", "A236", "U", "07001 CH", 6));
        LineaTaller linea = linea("ULL1", "A236", "U");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedido);

        assertEquals(1, resultado.objetivosDe(linea).size());
        assertEquals(16, resultado.objetivosDe(linea).get(0).cantidad());
    }

    @Test
    void cadaSufijoDePoEsUnaDestinacionDistinta() {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("ULL1", "A236", "U", "07001 CH", 20),
                PedidoAmiExcel.Fila.pedida("ULL1", "A236", "U", "07002 JP", 12),
                PedidoAmiExcel.Fila.pedida("ULL1", "A236", "U", 7003, 30));
        LineaTaller linea = linea("ULL1", "A236", "U");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedido);

        assertEquals(List.of("CHINA", "JAPAN", "PARIS"),
                resultado.objetivosDe(linea).stream().map(ObjetivoDestino::destino).toList());
        assertEquals(List.of(20, 12, 30),
                resultado.objetivosDe(linea).stream().map(ObjetivoDestino::cantidad).toList());
    }

    @Test
    void unPoSinSufijoVaALaDestinacionPorDefecto() {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("ULL1", "A236", "U", 7003, 30));
        LineaTaller linea = linea("ULL1", "A236", "U");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedido);

        assertEquals("PARIS", resultado.objetivosDe(linea).get(0).destino());
        assertEquals("07003", resultado.objetivosDe(linea).get(0).pedido());
    }

    @Test
    void unaReferenciaQueNoEstaEnElPedidoAvisaYNoRompe() {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("ULL1", "A236", "U", "07001 CH", 20));
        LineaTaller linea = linea("NO-EXISTE", "0000", "U");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedido);

        assertTrue(resultado.objetivosDe(linea).isEmpty());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("NO-EXISTE")));
        assertTrue(resultado.getBloqueos().isEmpty(),
                "una referencia sin pedido se arregla tecleando la cantidad");
    }

    @Test
    void unColorQueNoEstaEnElPedidoAvisaAunqueLaReferenciaSiEste() {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("ULL1", "A236", "U", "07001 CH", 20));
        LineaTaller linea = linea("ULL1", "OTRO", "U");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedido);

        assertTrue(resultado.objetivosDe(linea).isEmpty());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("OTRO")));
    }

    @Test
    void unSufijoDePoDesconocidoEsBloqueante() {
        // Sin saber a qué destinación va ese PO, cualquier reparto sería un
        // bulto camino del sitio equivocado.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("ULL1", "A236", "U", "07001 XX", 20));
        LineaTaller linea = linea("ULL1", "A236", "U");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedido);

        assertTrue(resultado.getBloqueos().stream().anyMatch(b -> b.contains("XX")));
        assertTrue(resultado.objetivosDe(linea).isEmpty());
    }

    @Test
    void unExcelDePedidoIlegibleAvisaYNoRompe() {
        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea("ULL1", "A236", "U")), new byte[] {1, 2, 3});

        assertTrue(resultado.getAvisos().stream()
                .anyMatch(a -> a.toLowerCase().contains("pedido")));
    }
}
