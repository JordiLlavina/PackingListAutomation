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
    void amiNumeraTambienLosPaletsSeguidoEntreDestinaciones() {
        // Con las cajas 1-47 seguidas, dos palets llamados "1" serían dos
        // bultos físicos con el mismo número en el mismo envío; en la
        // revisión, además, la columna PALET volvería a 1 en cada destinación
        // y parecería que todo el envío cabe en un palet.
        EnvioInput envio = generar().getEnvio();

        List<Integer> numerosDePalet = envio.getDestinos().stream()
                .flatMap(d -> d.getPalets().stream())
                .map(EnvioInput.PaletInput::getPalet)
                .toList();
        assertEquals(IntStream.rangeClosed(1, numerosDePalet.size()).boxed().toList(),
                numerosDePalet,
                "los palets de AMI se numeran seguidos, como las cajas");
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
        // Y los palets igual que las cajas: cada destinación de APC es una
        // entrega aparte, así que su primer palet vuelve a ser el 1.
        assertTrue(envio.getDestinos().stream()
                .allMatch(d -> d.getPalets().get(0).getPalet() == 1));
    }

    // --- Destinaciones hijas ---

    /** Las destinaciones hijas de las cajas que caen dentro de un palet. */
    private static java.util.Set<String> hijasDe(EnvioInput.DestinoInput destino,
                                                 EnvioInput.PaletInput palet) {
        java.util.Set<String> hijas = new java.util.LinkedHashSet<>();
        for (EnvioInput.ReferenciaInput referencia : destino.getReferencias()) {
            for (EnvioInput.CajaRangoInput caja : referencia.getCajas()) {
                int desde = caja.esRango() ? caja.getCajaInicio() : caja.getCaja();
                int hasta = caja.esRango() ? caja.getCajaFin() : caja.getCaja();
                if (hasta >= palet.getCajaInicio() && desde <= palet.getCajaFin()) {
                    // El canal va vacío cuando la hija ES el padre.
                    hijas.add(String.valueOf(referencia.getCanal()));
                }
            }
        }
        return hijas;
    }

    @Test
    void dosHijasDelMismoPadreNoCompartenPalet() {
        // WHOLESALE y CHINE FRANCH viajan al mismo almacén y salen en el mismo
        // excel, pero allí se reciben por separado: un palet mixto habría que
        // deshacerlo al llegar. Las dos cajas cabrían de sobra en un solo
        // palet —caben cuatro pilas de 157 cm y estas miden 40—, y aun así
        // salen dos.
        List<FilaAjustada> filas = List.of(
                new FilaAjustada("PXCBC-F67008", "CAMEL", "U", 10, "60x40x40", 10,
                        List.of(new ObjetivoDestino("WHOLESALE", 5, "4100000001"),
                                new ObjetivoDestino("CHINE FRANCH", 5, "4100000002"))));

        EnvioInput.DestinoInput destino = destino(
                generador.generar("APC", filas, null).getEnvio(), "WHOLESALE");

        assertEquals(2, destino.getPalets().size(), "un palet por hija");
        for (EnvioInput.PaletInput palet : destino.getPalets()) {
            assertEquals(1, hijasDe(destino, palet).size(),
                    "ningún palet mezcla dos destinaciones hijas");
        }
    }

    @Test
    void lasHijasSiguenSaliendoEnUnSoloFicheroYConNumeracionSeguida() {
        // Lo que NO cambia: fichero, hoja y dirección son del padre, así que
        // las dos hijas van en una sola destinación del envío y sus cajas se
        // numeran seguidas. Cada palet sigue siendo un rango contiguo, que es
        // lo que después permite reasignar los palets al importar.
        List<FilaAjustada> filas = List.of(
                new FilaAjustada("PXCBC-F67008", "CAMEL", "U", 10, "60x40x40", 10,
                        List.of(new ObjetivoDestino("WHOLESALE", 5, "4100000001"),
                                new ObjetivoDestino("CHINE FRANCH", 5, "4100000002"))));

        EnvioInput envio = generador.generar("APC", filas, null).getEnvio();

        assertEquals(1, envio.getDestinos().size());
        assertEquals(List.of(1, 2), numerosDeCaja(destino(envio, "WHOLESALE")));
        assertEquals(List.of(1, 2), destino(envio, "WHOLESALE").getPalets().stream()
                .map(EnvioInput.PaletInput::getCajaInicio).toList());
    }

    // --- Peso bruto declarado en el ajuste ---

    /** BAG-A con 25 unidades a CHINA y 10 por caja: dos llenas y una de 5. */
    private static List<FilaAjustada> conPesoBruto(Double pesoBrutoKg) {
        return List.of(new FilaAjustada("BAG-A", "NOIR", "U", 25, "60x40x40", 10, pesoBrutoKg,
                List.of(new ObjetivoDestino("CHINA", 25, "07001"))));
    }

    private static List<Double> pesosDe(EnvioInput envio, String destino) {
        List<Double> pesos = new ArrayList<>();
        for (EnvioInput.ReferenciaInput referencia : destino(envio, destino).getReferencias()) {
            for (EnvioInput.CajaRangoInput caja : referencia.getCajas()) {
                int cuantas = caja.esRango()
                        ? caja.getCajaFin() - caja.getCajaInicio() + 1
                        : 1;
                IntStream.range(0, cuantas).forEach(n -> pesos.add(caja.getPesoBruto()));
            }
        }
        return pesos;
    }

    @Test
    void unaCajaLlenaSeLlevaElPesoDeclaradoTalCual() {
        // 20 unidades a 10 por caja: dos cajas llenas, las dos con su peso.
        List<FilaAjustada> filas = List.of(
                new FilaAjustada("BAG-A", "NOIR", "U", 20, "60x40x40", 10, 12.5,
                        List.of(new ObjetivoDestino("CHINA", 20, "07001"))));

        EnvioInput envio = generador.generar("AMI", filas, null).getEnvio();

        assertEquals(List.of(12.5, 12.5), pesosDe(envio, "CHINA"));
    }

    @Test
    void unaCajaAMediasEscalaLaMercanciaPeroNoElCarton() {
        // Con reparto equitativo casi ninguna caja sale llena: 25 unidades de
        // a 10 por caja son 9+8+8. Si el peso declarado solo valiera para las
        // llenas, aquí no se pondría en ninguna.
        EnvioInput envio = generador.generar("AMI", conPesoBruto(12.5), null).getEnvio();

        assertEquals(List.of(9, 8, 8), unidadesPorCajaDe(envio, "CHINA", "BAG-A", "NOIR"));
        List<Double> pesos = pesosDe(envio, "CHINA");
        assertTrue(pesos.stream().noneMatch(peso -> peso == null), "las tres llevan peso");
        assertTrue(pesos.get(0) > pesos.get(1), "la de nueve pesa más que las de ocho");
        assertEquals(pesos.get(1), pesos.get(2), "las dos de ocho pesan igual");

        // El cartón pesa lo mismo vaya lleno o a medias, así que la caja de
        // ocho tiene que pesar MÁS que los cuatro quintos de una llena: si se
        // escalara el peso entero saldría justo 12,5 x 0,8 = 10. Se comprueba
        // así, y no contra la tara del yml, porque la tara es un dato del
        // almacén que cambia cada vez que se pesa un cartón.
        assertTrue(pesos.get(1) > 12.5 * 8 / 10,
                "escalar el peso entero se comería parte del cartón");

        // Y el modelo es lineal en la mercancía: de peso(9) y peso(8) se
        // vuelve al peso de la caja llena sin saber cuánto pesa el cartón.
        assertEquals(12.5, 2 * pesos.get(0) - pesos.get(1), 0.02);
    }

    @Test
    void sinPesoDeclaradoLosPesosSiguenEnBlanco() {
        EnvioInput envio = generador.generar("AMI", conPesoBruto(null), null).getEnvio();

        assertTrue(pesosDe(envio, "CHINA").stream().allMatch(peso -> peso == null));
    }

    @Test
    void sinTaraConocidaUnaCajaAMediasSeQuedaSinPesoYSeAvisa() {
        // Sin tara no se puede separar lo que pesa el cartón de lo que pesa la
        // mercancía, así que no hay forma de escalar. Antes que inventar un
        // número, se deja en blanco y se dice. (El cartón de este test no está
        // en el catálogo de taras a propósito.)
        List<FilaAjustada> filas = List.of(
                new FilaAjustada("BAG-A", "NOIR", "U", 25, "77x77x77", 10, 12.5,
                        List.of(new ObjetivoDestino("CHINA", 25, "07001"))));

        ResultadoPackingTaller resultado = generador.generar("AMI", filas, null);

        assertTrue(pesosDe(resultado.getEnvio(), "CHINA").stream().allMatch(peso -> peso == null));
        assertTrue(resultado.getAvisos().stream().anyMatch(aviso ->
                aviso.contains("BAG-A") && aviso.contains("peso bruto")),
                "callarlo dejaría al usuario creyendo que ya ha pesado ese material");
    }

    @Test
    void unBultoMixtoNoSeLlevaElPesoDeclaradoDeNadie() {
        // El peso declarado es de una caja llena de UN artículo; en un bulto
        // compartido no vale ni el de uno ni el de otro. PARIS mezcla dentro
        // del mismo pedido, y 4 + 4 de a diez por caja caben en un bulto.
        List<FilaAjustada> filas = List.of(
                new FilaAjustada("BAG-A", "NOIR", "U", 4, "60x40x40", 10, 12.5,
                        List.of(new ObjetivoDestino("PARIS", 4, "07001"))),
                new FilaAjustada("BAG-A", "BEIGE", "U", 4, "60x40x40", 10, 12.5,
                        List.of(new ObjetivoDestino("PARIS", 4, "07001"))));

        ResultadoPackingTaller resultado = generador.generar("AMI", filas, null);

        assertEquals(1, cajasDe(resultado.getEnvio(), "PARIS"), "van juntas en un bulto");
        assertTrue(pesosDe(resultado.getEnvio(), "PARIS").stream().allMatch(peso -> peso == null));
        assertTrue(resultado.getAvisos().stream().anyMatch(aviso -> aviso.contains("peso bruto")));
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
