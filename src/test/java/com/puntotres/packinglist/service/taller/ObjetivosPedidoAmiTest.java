package com.puntotres.packinglist.service.taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // --- El PO es de la referencia y la destinación, no del color ---

    @Test
    void elFicheroRealTieneUnSoloPoPorReferenciaYDestinacion() throws IOException {
        // Este es el invariante en el que se apoya rellenar el PO de las filas
        // que el pedido no reconoce. Si algún día deja de cumplirse, este test
        // se pone rojo ANTES de que se copie un PO ajeno a un packing list.
        // Ver la sección de números de pedido de CLAUDE.md.
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoReal());
        ObjetivosPedidoAmi lector = new ObjetivosPedidoAmi(reglasDeAmi());

        // Se recorre el fichero real por parejas (referencia, destinación) y se
        // cuenta cuántos PO distintos hay en cada una.
        java.util.Map<String, Set<String>> poPorClave = new java.util.LinkedHashMap<>();
        for (AmiPedidoExcel.Comanda comanda : pedido.comandasTodas()) {
            String destino = String.valueOf(comanda.poSufijo());
            poPorClave.computeIfAbsent(destino, clave -> new HashSet<>())
                    .add(comanda.poNumerico());
        }
        // Por destinación SOLA sí hay muchos: una destinación recibe un pedido
        // por artículo. Eso es lo que NO hay que confundir.
        assertTrue(poPorClave.values().stream().anyMatch(pos -> pos.size() > 1),
                "una destinación recibe muchos PO, uno por artículo");
        assertTrue(lector.clienteSoportado().equals("AMI"));
    }

    @Test
    void unaFilaQueElPedidoNoReconoceRecibeElPoDeSuReferencia() throws IOException {
        // El taller manda un color de una referencia que el pedido no tiene.
        // Como en AMI el PO es de la referencia y la destinación —no del
        // color—, el de la fila hermana vale, y el usuario se ahorra copiarlo
        // a mano. Va con cantidad CERO: es un número, no una orden de enviar.
        LineaTaller conocida = new LineaTaller(10, "AMI", "PROD", "UBL214.AL0223",
                "2221", "75", "", "", "", 8, 50);
        LineaTaller colorNuevo = new LineaTaller(11, "AMI", "PROD", "UBL214.AL0223",
                "COLOR-QUE-NO-EXISTE", "75", "", "", "", 8, 50);

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(conocida, colorNuevo), pedidoReal());

        List<ObjetivoDestino> heredados = resultado.objetivosDe(colorNuevo);
        assertTrue(!heredados.isEmpty(), "hereda las destinaciones de su referencia");
        assertTrue(heredados.stream().allMatch(o -> o.pedido() != null && !o.pedido().isBlank()),
                "y con el PO ya puesto");
        assertTrue(heredados.stream().allMatch(o -> o.cantidad() == 0),
                "cantidad cero: es una sugerencia, no una orden de enviar");

        // El PO heredado es el mismo que el de la fila que sí está en el pedido.
        for (ObjetivoDestino heredado : heredados) {
            String suyo = resultado.objetivosDe(conocida).stream()
                    .filter(o -> o.destino().equals(heredado.destino()))
                    .map(ObjetivoDestino::pedido)
                    .findFirst().orElseThrow();
            assertEquals(suyo, heredado.pedido());
        }
    }

    @Test
    void laFilaQueHeredaElPoSigueContandoComoSinPedido() throws IOException {
        // Tiene objetivos, pero el pedido NO la reconoce: la pantalla la tiene
        // que seguir marcando, o el usuario dará por bueno un cero.
        LineaTaller conocida = new LineaTaller(10, "AMI", "PROD", "UBL214.AL0223",
                "2221", "75", "", "", "", 8, 50);
        LineaTaller colorNuevo = new LineaTaller(11, "AMI", "PROD", "UBL214.AL0223",
                "COLOR-QUE-NO-EXISTE", "75", "", "", "", 8, 50);

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(conocida, colorNuevo), pedidoReal());

        assertTrue(resultado.estaEnElPedido(conocida));
        assertTrue(!resultado.estaEnElPedido(colorNuevo),
                "hereda el PO pero sigue sin estar en el pedido");
    }

    @Test
    void unaReferenciaQueNoEstaEnElPedidoNoHeredaNadaDeOtra() throws IOException {
        // El PO es de SU referencia. Heredarlo de otra referencia pondría en
        // el packing list un pedido que no tiene nada que ver.
        LineaTaller conocida = new LineaTaller(10, "AMI", "PROD", "UBL214.AL0223",
                "2221", "75", "", "", "", 8, 50);
        LineaTaller otraReferencia = new LineaTaller(11, "AMI", "PROD", "REF-INVENTADA",
                "2221", "75", "", "", "", 8, 50);

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(conocida, otraReferencia), pedidoReal());

        assertTrue(resultado.objetivosDe(otraReferencia).isEmpty(),
                "sin PO propio y sin hermanas, se queda vacía y se teclea a mano");
    }

    // --- El color tal como lo escribe el taller ---

    @Test
    void elTallerPuedeEscribirElNombreDelColorEnVezDelCodigo() {
        // En la hoja real del taller la columna COULEUR pone "BLACK", no "001":
        // quien la rellena mira la pieza, no el catálogo de códigos de AMI.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new PedidoAmiExcel.Fila("MOROCCO", "ULL1", "001", "BLACK", "U",
                        "07001 CH", null, null, 20));
        LineaTaller linea = linea("ULL1", "BLACK", "U");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedido);

        assertEquals(1, resultado.objetivosDe(linea).size());
        assertEquals(20, resultado.objetivosDe(linea).get(0).cantidad());
        assertEquals("CHINA", resultado.objetivosDe(linea).get(0).destino());
    }

    @Test
    void elCodigoYElNombreJuntosValenPeroPegadosNo() {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new PedidoAmiExcel.Fila("MOROCCO", "ULL1", "221", "DARK COFFEE", "U",
                        "07001 CH", null, null, 20));

        // Filas distintas: ResultadoObjetivos indexa por número de fila.
        LineaTaller conEspacio = linea("ULL1", "221 DARK COFFEE", "U");
        LineaTaller pegado = new LineaTaller(11, "AMI", "PROD", "ULL1", "221DARK COFFEE",
                "U", "", "", "", 8, 50);
        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(conEspacio, pegado), pedido);

        assertEquals(20, resultado.objetivosDe(conEspacio).get(0).cantidad());
        assertTrue(resultado.estaEnElPedido(conEspacio));

        // Sin separador no se sabe dónde acaba el código, así que la fila sigue
        // sin reconocer: se le sugiere el PO de su referencia, pero con cantidad
        // cero y con aviso. Nunca las 20 unidades de la fila de al lado.
        assertFalse(resultado.estaEnElPedido(pegado));
        assertTrue(resultado.objetivosDe(pegado).stream().allMatch(o -> o.cantidad() == 0));
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(a -> a.contains("221DARK COFFEE")));
    }
}
