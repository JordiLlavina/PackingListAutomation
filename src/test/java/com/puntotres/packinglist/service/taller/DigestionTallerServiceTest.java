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

    /** Para comprobar que las destinaciones del pedido llegan a empaquetarse. */
    @Autowired
    private GeneradorPackingTaller generador;

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

    // --- El cartón y el peso que escribe el taller en su hoja ---

    @Test
    void elCartonYElPesoDelTallerGananALaMemoria() throws Exception {
        // La hoja del taller describe el bulto que acaba de salir de allí y
        // el peso que alguien acaba de poner en la báscula; la memoria, el
        // envío anterior. Las unidades por caja no cambian de fuente: siguen
        // siendo las de la memoria, que es lo que una persona dio por bueno.
        memoria.recordar("AMI", "CAJA-TALLER", "60x40x30", 6);
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "CAJA-TALLER", "NOIR", 20)
                        .conUnidadesPorCaja(6)
                        .conMedidaCaja("60X40X40")
                        .conPesoBrutoCaja(12.0));

        GrupoReferencia grupo = digerir(taller, pedidoDe("CAJA-TALLER", "NOIR", 20))
                .getGrupos().get(0);

        assertEquals("60x40x40", grupo.getMedidaCaja(), "en la forma del catálogo de taras");
        assertEquals(12.0, grupo.getPesoBrutoKg());
        assertEquals(6, grupo.getUnidadesPorCaja());
    }

    @Test
    void siElTallerYLaMemoriaNoCuadranSeAvisaEnLaReferencia() throws Exception {
        memoria.recordar("AMI", "NO-CUADRA", "60x40x30", 6, 5.0);
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "NO-CUADRA", "NOIR", 20)
                        .conUnidadesPorCaja(6)
                        .conMedidaCaja("60x40x40")
                        .conPesoBrutoCaja(12.0));

        DigestionTaller resultado = digerir(taller, pedidoDe("NO-CUADRA", "NOIR", 20));

        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("cartón")),
                "el cartón cambia la tara, el volumen y las cajas que caben en un palet");
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("pesa la caja")));
        assertTrue(resultado.getDetalle().stream()
                .allMatch(aviso -> "NO-CUADRA".equals(aviso.referencia())),
                "y van a la tarjeta de su referencia, no a la lista general");
    }

    @Test
    void siElTallerYLaMemoriaDicenLoMismoNoSeAvisaDeNada() throws Exception {
        // Lo normal es que coincidan, y un aviso que sale siempre entierra a
        // los que sí hay que leer.
        memoria.recordar("AMI", "CUADRA", "60x40x40", 6);
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "CUADRA", "NOIR", 20)
                        .conUnidadesPorCaja(6)
                        .conMedidaCaja("60X40X40"));

        assertEquals(List.of(), digerir(taller, pedidoDe("CUADRA", "NOIR", 20)).getAvisos());
    }

    @Test
    void elPesoSaleDeLaCajaMasLlenaYLaMedidaDeEsaMismaFila() throws Exception {
        // Una referencia ocupa varias filas y no todas esas cajas van llenas:
        // la última de un color lleva lo que sobra. La que describe a la
        // referencia es la de más unidades por caja.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "MAS-LLENA", "NOIR", 5)
                        .conUnidadesPorCaja(5).conMedidaCaja("60x40x30").conPesoBrutoCaja(7.0),
                PackingTallerExcel.Fila.de("AMI", "MAS-LLENA", "BEIGE", 8)
                        .conUnidadesPorCaja(8).conMedidaCaja("60x40x40").conPesoBrutoCaja(12.0));
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                PedidoAmiExcel.Fila.pedida("MAS-LLENA", "NOIR", "U", "07001 CH", 5),
                PedidoAmiExcel.Fila.pedida("MAS-LLENA", "BEIGE", "U", "07001 CH", 8));

        GrupoReferencia grupo = digerir(taller, pedido).getGrupos().get(0);

        assertEquals(12.0, grupo.getPesoBrutoKg());
        assertEquals("60x40x40", grupo.getMedidaCaja(), "el cartón es el de la fila del peso");
        assertEquals(8, grupo.getUnidadesPorCaja());
    }

    @Test
    void siElPesoEsDeUnaCajaQueNoLlevaLasUnidadesQueVanAIrSeAvisa() throws Exception {
        // El peso que se enseña es el de una caja LLENA y el programa escala
        // con él las que van a medias. Si la fila pesada llevaba otra
        // cantidad, no es un peso de menos: es el de otro bulto.
        memoria.recordar("AMI", "PESO-A-MEDIAS", "60x40x40", 10);
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "PESO-A-MEDIAS", "NOIR", 6)
                        .conUnidadesPorCaja(6).conPesoBrutoCaja(9.0));

        DigestionTaller resultado = digerir(taller, pedidoDe("PESO-A-MEDIAS", "NOIR", 6));

        assertEquals(9.0, resultado.getGrupos().get(0).getPesoBrutoKg(), "se enseña igual");
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(a -> a.contains("caja de 6") && a.contains("10")));
    }

    @Test
    void sinPesoEnLaHojaMandaElDeLaMemoria() throws Exception {
        digestion.memorizar("AMI", pesada("SOLO-MEMORIA", 12.5));
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "SOLO-MEMORIA", "NOIR", 20)
                        .conMedidaCaja("60x40x40"));

        GrupoReferencia grupo = digerir(taller, pedidoDe("SOLO-MEMORIA", "NOIR", 20))
                .getGrupos().get(0);

        assertEquals(12.5, grupo.getPesoBrutoKg(), 0.011);
    }

    @Test
    void unCartonDelTallerSinMemoriaYaNoEsUnValorPorDefecto() throws Exception {
        // Antes de estas columnas, sin memoria el cartón era el estándar y
        // nadie lo había mirado. Ahora lo dice quien ha hecho la caja.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "SOLO-TALLER", "NOIR", 20)
                        .conUnidadesPorCaja(8).conMedidaCaja("60X40X45").conPesoBrutoCaja(9.0));

        GrupoReferencia grupo = digerir(taller, pedidoDe("SOLO-TALLER", "NOIR", 20))
                .getGrupos().get(0);

        assertEquals("60x40x45", grupo.getMedidaCaja());
        assertEquals(9.0, grupo.getPesoBrutoKg());
        assertEquals(OrigenDato.TALLER, grupo.getOrigen());
    }

    @Test
    void laPlantillaRealDelTallerLlegaConSuPesoYSuCartonALaPantalla() throws Exception {
        // De punta a punta contra el fichero que manda el taller: sus dos
        // columnas nuevas tienen que acabar en las casillas que se ven en el
        // ajuste, sin teclear nada.
        byte[] taller;
        try (var in = DigestionTallerServiceTest.class
                .getResourceAsStream("/ejemplos/taller/plantilla-taller-con-peso-y-medida.xlsx")) {
            taller = in.readAllBytes();
        }

        DigestionTaller resultado = digestion.digerir("AMI", taller, null);
        GrupoReferencia grupo = resultado.getGrupos().get(0);

        assertEquals("ULL163.AL0052", grupo.getReferencia());
        assertEquals(10.0, grupo.getPesoBrutoKg());
        assertEquals("60x60x40", grupo.getMedidaCaja());
        assertEquals(8, grupo.getUnidadesPorCaja());
    }

    // --- Las abreviaturas de destinación del taller ---

    /**
     * Las siete destinaciones del pedido real con la abreviatura que el taller
     * escribe para cada una: la de su destino PADRE. Es la tabla del cliente.
     */
    private static byte[] tallerApcConAbreviaturas() {
        return PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("APC", "PXBHZ-H65077", "CAMEL", 20)
                        .conCode("721").conUnidadesPorCaja(10).conDestino("WH"),
                PackingTallerExcel.Fila.de("APC", "PXBHZ-F65101", "CAMEL", 20)
                        .conCode("719").conUnidadesPorCaja(10).conDestino("WH"),
                PackingTallerExcel.Fila.de("APC", "PXBHZ-F65101", "NOIR", 20)
                        .conCode("706").conUnidadesPorCaja(10).conDestino("UST"),
                PackingTallerExcel.Fila.de("APC", "PXCBC-F63023", "CAMEL", 20)
                        .conCode("681").conUnidadesPorCaja(10).conDestino("JPT"),
                PackingTallerExcel.Fila.de("APC", "PXBHZ-F65101", "BEIGE", 20)
                        .conCode("689").conUnidadesPorCaja(10).conDestino("KRT"),
                PackingTallerExcel.Fila.de("APC", "PXCBC-F63023", "NOIR", 20)
                        .conCode("694").conUnidadesPorCaja(10).conDestino("RT"),
                PackingTallerExcel.Fila.de("APC", "PXBHZ-F65101", "KAKI", 20)
                        .conCode("718").conUnidadesPorCaja(10).conDestino("WH"));
    }

    @Test
    void laAbreviaturaDelTallerYLaDestinacionDelPedidoSonElMismoSitio() throws Exception {
        // El taller escribe la abreviatura del destino PADRE ("UST", "WH") y
        // el pedido nombra la destinación HIJA ("Douanes USA", "Australia").
        // Comparando el texto saltaba un aviso por fila diciendo que no cuadra
        // lo que sí cuadra, y un aviso que sale siempre entierra a los demás.
        DigestionTaller resultado = digestion.digerir("APC", tallerApcConAbreviaturas(),
                pedidoRealDeApc());

        assertEquals(List.of(), resultado.getAvisos().stream()
                .filter(aviso -> aviso.contains("Manda el pedido"))
                .toList());
    }

    @Test
    void unaAbreviaturaQueDeVerdadEsDeOtroSitioSiAvisa() throws Exception {
        // La otra mitad: resolver las abreviaturas no puede acabar dando todo
        // por bueno. JPT es JAPAN, y ese pedido va a Retail.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("APC", "PXCBC-F63023", "NOIR", 20)
                        .conCode("694").conUnidadesPorCaja(10).conDestino("JPT"));

        DigestionTaller resultado = digestion.digerir("APC", taller, pedidoRealDeApc());

        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("'JPT'") && aviso.contains("RETAIL")));
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

    // --- APC: el pedido manda, no la hoja del taller ---

    private static byte[] pedidoRealDeApc() throws Exception {
        try (java.io.InputStream in = DigestionTallerServiceTest.class
                .getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            return in.readAllBytes();
        }
    }

    @Test
    void enApcLaDestinacionYLaCantidadSalenDelPedidoYNoDeLaHojaDelTaller() throws Exception {
        // El lector de APC estaba escrito y probado pero no cableado, así que
        // en la aplicación no se usaba nunca: el envío caía al camino
        // genérico y se quedaba con lo que hubiera apuntado el taller. Este
        // test va por el contenedor real, que es donde se ve.
        // 4100128681 va a "Japan" en el fichero real de pedido.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("APC", "F63023", "CAMEL", 30)
                        .conCode("681").conDestino("PARIS").conUnidadesPorCaja(10));

        DigestionTaller resultado = digestion.digerir("APC", taller, pedidoRealDeApc());

        assertEquals(List.of("JAPAN"), resultado.getDestinosActivos(),
                "la destinación es la del Document d'achat, no el 'PARIS' de la hoja");
        assertEquals("4100128681", resultado.getGrupos().get(0).getFilas().get(0)
                .getObjetivos().get(0).pedido());
    }

    @Test
    void lasSieteDestinacionesDelPedidoRealDeApcSePuedenEmpaquetar() throws Exception {
        // Las siete que trae APC_PEDIDO_FALL26. El reparto pregunta la
        // prioridad por la destinación HIJA, así que una que no esté en las
        // normas de taller bloquea el envío entero ("no se sabe a qué altura
        // se apila ni con qué preferencia se sirve"). Antes de cablear el
        // lector nadie recorría este camino, y a AUSTRALIA y DOUANES USA les
        // faltaba la norma.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("APC", "PXBHZ-H65077", "CAMEL", 20)
                        .conCode("721").conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("APC", "PXBHZ-F65101", "CAMEL", 20)
                        .conCode("719").conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("APC", "PXBHZ-F65101", "NOIR", 20)
                        .conCode("706").conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("APC", "PXCBC-F63023", "CAMEL", 20)
                        .conCode("681").conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("APC", "PXBHZ-F65101", "BEIGE", 20)
                        .conCode("689").conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("APC", "PXCBC-F63023", "NOIR", 20)
                        .conCode("694").conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("APC", "PXBHZ-F65101", "KAKI", 20)
                        .conCode("718").conUnidadesPorCaja(10));

        DigestionTaller digerido = digestion.digerir("APC", taller, pedidoRealDeApc());
        ResultadoPackingTaller packing =
                generador.generar("APC", digerido.aFilasAjustadas(), null);

        assertEquals(List.of(), packing.getBloqueos());
        assertEquals(List.of("AUSTRALIA", "CHINE FRANCH", "DOUANES USA", "JAPAN", "KOREA",
                "RETAIL", "WHOLESALE"), digerido.getDestinosActivos());
    }

    @Test
    void enApcLasHijasDeWholesaleSalenBajoSuDestinacionPadre() throws Exception {
        // AUSTRALIA, CHINE FRANCH y WHOLESALE viajan al mismo almacén
        // (Crosslog) y salen en el mismo fichero; la hija solo sobrevive en
        // la columna DESTINATION. DOUANES USA es la hija única de D. USA.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("APC", "PXBHZ-H65077", "CAMEL", 20)
                        .conCode("721").conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("APC", "PXBHZ-F65101", "KAKI", 20)
                        .conCode("718").conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("APC", "PXBHZ-F65101", "NOIR", 20)
                        .conCode("706").conUnidadesPorCaja(10));

        DigestionTaller digerido = digestion.digerir("APC", taller, pedidoRealDeApc());
        ResultadoPackingTaller packing =
                generador.generar("APC", digerido.aFilasAjustadas(), null);

        assertEquals(List.of("WHOLESALE", "D. USA"), packing.getEnvio().getDestinos().stream()
                .map(d -> d.getDestino()).toList());
    }

    // --- De qué columnas que faltan vale la pena avisar ---

    @Test
    void enAmiNoSeAvisaDeQueFalteLaColumnaCode() throws Exception {
        // CODE solo lo lee APC, donde el código de tres dígitos es el que
        // dice a qué pedido y a qué destinación va la fila. En AMI el aviso
        // mandaba a rellenar una columna que el programa ni va a mirar.
        byte[] taller = PackingTallerExcel.crearSin(List.of("CODE"),
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "NOIR", 20).conUnidadesPorCaja(10));

        DigestionTaller resultado = digerir(taller, pedidoDe("BAG-A", "NOIR", 20));

        assertTrue(resultado.getAvisos().stream().noneMatch(a -> a.contains("CODE")));
    }

    @Test
    void enApcSiSeAvisaDeQueFalteLaColumnaCode() throws Exception {
        byte[] taller = PackingTallerExcel.crearSin(List.of("CODE"),
                PackingTallerExcel.Fila.de("APC", "F67008", "CAMEL", 20).conUnidadesPorCaja(10));

        DigestionTaller resultado = digestion.digerir("APC", taller, null);

        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("CODE")));
    }

    @Test
    void noSeAvisaDeLasColumnasQueNadieLee() throws Exception {
        // "Nº EXPEDITION PUNTOTRES" es trazabilidad del taller y "Nº DE
        // COLIS" su numeración de cajas, que se descarta a propósito porque
        // el packing se regenera desde cero. Ninguno de los dos se lee en
        // ningún sitio, así que pedir que se rellenen es ruido que empuja
        // hacia abajo los avisos que sí hay que atender.
        byte[] taller = PackingTallerExcel.crearSin(
                List.of("Nº EXPEDITION PUNTOTRES", "N° DE COLIS"),
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "NOIR", 20).conUnidadesPorCaja(10));

        DigestionTaller resultado = digerir(taller, pedidoDe("BAG-A", "NOIR", 20));

        assertTrue(resultado.getAvisos().stream().noneMatch(a -> a.contains("EXPEDITION")));
        assertTrue(resultado.getAvisos().stream().noneMatch(a -> a.contains("DE COLIS")));
    }

    @Test
    void siSeAvisaDeUnaColumnaQueElProgramaSiUsa() throws Exception {
        // Sin QTITE / COLIS no se sabe cuántas unidades entran en una caja,
        // y eso hay que teclearlo en esta misma pantalla.
        byte[] taller = PackingTallerExcel.crearSin(List.of("QTITE /\nCOLIS"),
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "NOIR", 20));

        DigestionTaller resultado = digerir(taller, pedidoDe("BAG-A", "NOIR", 20));

        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("QTITE")));
    }

    @Test
    void elAvisoDeUnaColumnaQueFaltaEsDelFicheroYNoDeUnaReferencia() throws Exception {
        // Habla de la hoja entera: va a la sección general de la pantalla,
        // no a la tarjeta de ninguna referencia en concreto.
        byte[] taller = PackingTallerExcel.crearSin(List.of("QTITE /\nCOLIS"),
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "NOIR", 20));

        DigestionTaller resultado = digerir(taller, pedidoDe("BAG-A", "NOIR", 20));

        assertTrue(resultado.getDetalle().stream()
                .filter(a -> a.texto().contains("QTITE"))
                .allMatch(a -> a.referencia() == null));
    }

    // --- Bloqueos ---

    @Test
    void lasFilasDeOtroClienteSeApartanEnSilencio() throws Exception {
        // El taller trabaja para varios clientes y manda una sola hoja con
        // todos mezclados: encontrar otro nombre es lo NORMAL, no un error, y
        // avisar de ello en todas las entregas solo empujaba hacia abajo los
        // avisos que sí hay que leer.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "BAG-A", "NOIR", 20).conUnidadesPorCaja(10),
                PackingTallerExcel.Fila.de("KAMI", "MOD-X", "ROUGE", 5)
                        .conUnidadesPorCaja(10));

        DigestionTaller resultado = digerir(taller, pedidoDe("BAG-A", "NOIR", 20));

        assertTrue(resultado.getBloqueos().isEmpty(), "no bloquea: se sigue con lo de AMI");
        assertTrue(resultado.getAvisos().stream().noneMatch(a -> a.contains("KAMI")));
        assertEquals(List.of("BAG-A"), resultado.getGrupos().stream()
                .map(GrupoReferencia::getReferencia).toList(),
                "y el material del otro cliente no entra en el packing");
    }

    @Test
    void elNombreDelClienteSeComparaSinPuntosNiEspacios() throws Exception {
        // El taller escribe "A.P.C." donde el catálogo dice "APC". Comparando
        // al pie de la letra se apartaría el envío entero.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("A.P.C.", "BAG-A", "NOIR", 20).conUnidadesPorCaja(10));

        DigestionTaller resultado = digestion.digerir("APC", taller, null);

        assertEquals(1, resultado.getGrupos().size());
        assertTrue(resultado.getBloqueos().isEmpty());
    }

    @Test
    void siNoQuedaNingunaFilaDelClienteElegidoSiBloquea() throws Exception {
        // Aquí el cliente elegido no es el de la hoja. Generar un packing
        // vacío sin decir nada sería peor que parar.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("KAMI", "MOD-X", "ROUGE", 5)
                        .conUnidadesPorCaja(10));

        DigestionTaller resultado = digerir(taller, pedidoDe("BAG-A", "NOIR", 20));

        assertTrue(resultado.getBloqueos().stream().anyMatch(b -> b.contains("AMI")));
        assertTrue(resultado.getGrupos().isEmpty());
    }

    // --- Cantidad para cliente editable ---

    @Test
    void unaFilaQueElPedidoNoReconoceAceptaLaCantidadQueSeTeclea() throws Exception {
        // Antes se ignoraba en silencio: la fila se veía en pantalla, aceptaba
        // lo tecleado y no lo aplicaba, así que no había forma de enviar una
        // referencia que el excel de pedido no reconociera.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "NO-EN-PEDIDO", "NOIR", 10)
                        .conUnidadesPorCaja(10));
        DigestionTaller resultado = digerir(taller, pedidoDe("OTRA", "NOIR", 20));
        FilaDigerida fila = resultado.getGrupos().get(0).getFilas().get(0);

        fila.corregirCantidad("CHINA", 7);

        assertEquals(7, fila.cantidadPara("CHINA"));
        assertEquals(7, resultado.aFilasAjustadas().get(0).objetivos().get(0).cantidad());
    }

    @Test
    void sinNingunaFilaEnElPedidoSeOfrecenLasDestinacionesDelCliente() throws Exception {
        // Si no hubiera columnas, la pantalla de ajuste se quedaría sin
        // ninguna casilla donde teclear y el envío sin salida.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "NO-EN-PEDIDO", "NOIR", 10)
                        .conUnidadesPorCaja(10));

        DigestionTaller resultado = digerir(taller, pedidoDe("OTRA", "NOIR", 20));

        assertTrue(resultado.getDestinosActivos().contains("CHINA"));
        assertTrue(resultado.getDestinosActivos().contains("PARIS"));
    }

    @Test
    void unaCantidadCeroNoInventaDestinaciones() throws Exception {
        // Un cero es lo que llega de una casilla vacía: si creara objetivo,
        // todas las filas acabarían con todas las columnas.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "NO-EN-PEDIDO-2", "NOIR", 10)
                        .conUnidadesPorCaja(10));
        FilaDigerida fila = digerir(taller, pedidoDe("OTRA", "NOIR", 20))
                .getGrupos().get(0).getFilas().get(0);

        fila.corregirCantidad("CHINA", 0);

        assertTrue(fila.getObjetivos().isEmpty());
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

    // --- El peso se recuerda en neto ---

    private DigestionTaller pesada(String referencia, double pesoBrutoKg) throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", referencia, "NOIR", 20).conUnidadesPorCaja(10));
        DigestionTaller resultado = digerir(taller, pedidoDe(referencia, "NOIR", 20));
        resultado.getGrupos().get(0).corregir("60x40x40", 10, pesoBrutoKg);
        return resultado;
    }

    @Test
    void loQueSeRecuerdaEsElPesoNetoNoElBruto() throws Exception {
        // El bruto lleva dentro la tara del cartón, que es un dato del almacén
        // que se corrige cada vez que se vuelve a pesar y que cambia entero si
        // la referencia pasa a otra caja. El neto es de la mercancía y no
        // cambia. Se comprueba que le han quitado el cartón, y no contra el
        // valor de la tara, que ningún test debe fijar.
        digestion.memorizar("AMI", pesada("NETO-NO-BRUTO", 12.5));

        Double neto = memoria.buscar("AMI", "NETO-NO-BRUTO").orElseThrow().pesoNetoKg();
        assertTrue(neto != null && neto > 0 && neto < 12.5, "guardado sin el cartón");
    }

    @Test
    void elPesoRecordadoVuelveComoBrutoEnLaSiguienteEntrega() throws Exception {
        // Ida y vuelta: la tara se resta al guardar y se suma al recuperar, así
        // que quien preparó el envío anterior ve el mismo peso que tecleó.
        digestion.memorizar("AMI", pesada("IDA-Y-VUELTA", 12.5));

        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "IDA-Y-VUELTA", "NOIR", 20));
        GrupoReferencia grupo = digerir(taller, pedidoDe("IDA-Y-VUELTA", "NOIR", 20))
                .getGrupos().get(0);

        assertEquals(12.5, grupo.getPesoBrutoKg(), 0.011);
        assertEquals(OrigenDato.MEMORIA, grupo.getOrigen());
    }

    @Test
    void generarSinPesarNoBorraElPesoQueYaHabia() throws Exception {
        digestion.memorizar("AMI", pesada("NO-SE-BORRA", 12.5));
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "NO-SE-BORRA", "NOIR", 20)
                        .conUnidadesPorCaja(10));

        // Segunda entrega, esta vez sin tocar la casilla del peso.
        DigestionTaller sinPesar = digerir(taller, pedidoDe("NO-SE-BORRA", "NOIR", 20));
        sinPesar.getGrupos().get(0).corregir("60x40x40", 10, null);
        digestion.memorizar("AMI", sinPesar);

        assertTrue(memoria.buscar("AMI", "NO-SE-BORRA").orElseThrow().pesoNetoKg() != null,
                "pesar una caja cuesta bajarla a la báscula: no se tira por no repetirlo");
    }

    @Test
    void sinTaraConocidaNoSeRecuerdaUnPesoQueNoSeSabeSeparar() throws Exception {
        // Sin tara no hay forma de separar cartón de mercancía. Antes que
        // guardar un neto que en realidad es un bruto, no se guarda: la
        // próxima vez se vuelve a pedir. (Ese cartón no está en el catálogo.)
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "SIN-TARA", "NOIR", 20).conUnidadesPorCaja(10));
        DigestionTaller resultado = digerir(taller, pedidoDe("SIN-TARA", "NOIR", 20));
        resultado.getGrupos().get(0).corregir("77x77x77", 10, 12.5);

        digestion.memorizar("AMI", resultado);

        assertNull(memoria.buscar("AMI", "SIN-TARA").orElseThrow().pesoNetoKg());
    }

    @Test
    void noSeMemorizaUnaReferenciaQueSeHaQuedadoPendiente() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "NO-APRENDIDA", "NOIR", 20));
        DigestionTaller resultado = digerir(taller, pedidoDe("NO-APRENDIDA", "NOIR", 20));

        digestion.memorizar("AMI", resultado);

        assertTrue(memoria.buscar("AMI", "NO-APRENDIDA").isEmpty());
    }

    // --- Filas repetidas del mismo artículo ---

    @Test
    void variasFilasDelMismoArticuloSeSumanEnUnaSola() throws Exception {
        // El caso de la hoja real: el taller escribe una fila por destinación
        // suya, y las dos son el mismo artículo. Son 466 unidades de una cosa,
        // no dos artículos de 200 y 266.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "ULL1", "BLACK", 200).conDestino("JA"),
                PackingTallerExcel.Fila.de("AMI", "ULL1", "BLACK", 266).conDestino("PA"));

        DigestionTaller resultado = digerir(taller, pedidoDe("ULL1", "BLACK", 200));

        assertEquals(1, resultado.getGrupos().size());
        assertEquals(1, resultado.getGrupos().get(0).getFilas().size(),
                "un artículo es una fila, por muchas veces que lo escriba el taller");
        assertEquals(466, resultado.getGrupos().get(0).getFilas().get(0).getRecibido());
    }

    @Test
    void alFusionarNoSeDuplicaLoQuePideElCliente() throws Exception {
        // Las dos filas preguntan al pedido por la MISMA clave, así que las
        // dos traen el mismo objetivo. Sumarlos pediría 400 de un pedido de
        // 200 y el reparto empaquetaría el doble de género.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "ULL1", "BLACK", 100).conDestino("JA"),
                PackingTallerExcel.Fila.de("AMI", "ULL1", "BLACK", 100).conDestino("PA"));

        FilaDigerida fila = digerir(taller, pedidoDe("ULL1", "BLACK", 200))
                .getGrupos().get(0).getFilas().get(0);

        assertEquals(200, fila.cantidadPara("CHINA"),
                "la cantidad la dice el pedido, no cuántas filas haya escrito el taller");
        assertEquals(200, fila.totalObjetivo());
    }

    @Test
    void elColorYLaTallaSiguenSeparandoFilas() throws Exception {
        // La fusión es por referencia + color + talla: no puede tragarse dos
        // artículos distintos que comparten referencia.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "UBL1", "2221", 10).conTalla("75"),
                PackingTallerExcel.Fila.de("AMI", "UBL1", "2221", 10).conTalla("75"),
                PackingTallerExcel.Fila.de("AMI", "UBL1", "2221", 4).conTalla("85"),
                PackingTallerExcel.Fila.de("AMI", "UBL1", "001", 7).conTalla("75"));

        List<FilaDigerida> filas = digerir(taller, pedidoDe("UBL1", "2221", 20))
                .getGrupos().get(0).getFilas();

        assertEquals(3, filas.size());
        assertEquals(20, filas.get(0).getRecibido(), "las dos de 2221 talla 75, sumadas");
        assertEquals(4, filas.get(1).getRecibido());
        assertEquals(7, filas.get(2).getRecibido());
    }

    @Test
    void sinExcelDePedidoLoQueLlegaDeCadaDestinacionSeConserva() throws Exception {
        // Sin pedido, el objetivo ES lo que ha llegado y la destinación la
        // pone el taller. Al fusionar las filas, las dos destinaciones tienen
        // que sobrevivir con lo suyo: si no, se perdería material.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("KAMI", "MOD1", "ROUGE", 30)
                        .conDestino("BARCELONA"),
                PackingTallerExcel.Fila.de("KAMI", "MOD1", "ROUGE", 20)
                        .conDestino("MADRID"));

        DigestionTaller resultado = digestion.digerir("KAMI", taller, null);
        FilaDigerida fila = resultado.getGrupos().get(0).getFilas().get(0);

        assertEquals(50, fila.getRecibido());
        assertEquals(30, fila.cantidadPara("BARCELONA"));
        assertEquals(20, fila.cantidadPara("MADRID"));
        assertEquals(50, fila.totalObjetivo(), "no se pierde ni se inventa nada");
    }

    @Test
    void sinExcelDePedidoDosFilasAlMismoSitioSeSuman() throws Exception {
        // Aquí el objetivo sí se suma, porque no lo dice ningún pedido: es lo
        // que ha llegado, y ha llegado en dos veces.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("KAMI", "MOD1", "ROUGE", 30)
                        .conDestino("BARCELONA"),
                PackingTallerExcel.Fila.de("KAMI", "MOD1", "ROUGE", 20)
                        .conDestino("BARCELONA"));

        FilaDigerida fila = digestion.digerir("KAMI", taller, null)
                .getGrupos().get(0).getFilas().get(0);

        assertEquals(50, fila.getRecibido());
        assertEquals(50, fila.cantidadPara("BARCELONA"));
    }

    // --- No se puede enviar más de lo que ha llegado ---

    @Test
    void repartirMasUnidadesDeLasQueHanLlegadoBloquea() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "ULL1", "BLACK", 50).conUnidadesPorCaja(10));
        DigestionTaller resultado = digerir(taller, pedidoDe("ULL1", "BLACK", 50));

        FilaDigerida fila = resultado.getGrupos().get(0).getFilas().get(0);
        fila.corregirCantidad("CHINA", 40);
        fila.corregirCantidad("JAPAN", 30);

        assertTrue(!resultado.sePuedeGenerar(),
                "70 unidades repartidas de 50 que han llegado");
        assertTrue(resultado.repartosImposibles().stream()
                .anyMatch(b -> b.contains("ULL1") && b.contains("70") && b.contains("50")));
    }

    @Test
    void repartirExactamenteLoQueHaLlegadoNoBloquea() throws Exception {
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "ULL1", "BLACK", 50).conUnidadesPorCaja(10));
        DigestionTaller resultado = digerir(taller, pedidoDe("ULL1", "BLACK", 50));

        FilaDigerida fila = resultado.getGrupos().get(0).getFilas().get(0);
        fila.corregirCantidad("CHINA", 20);
        fila.corregirCantidad("JAPAN", 30);

        assertTrue(resultado.repartosImposibles().isEmpty());
        assertTrue(resultado.sePuedeGenerar());
    }

    @Test
    void repartirDeMenosSigueSiendoLegal() throws Exception {
        // Lo que sobra se queda para la siguiente entrega: eso ya se avisa en
        // el reparto y nunca ha bloqueado.
        byte[] taller = PackingTallerExcel.crear(
                PackingTallerExcel.Fila.de("AMI", "ULL1", "BLACK", 50).conUnidadesPorCaja(10));
        DigestionTaller resultado = digerir(taller, pedidoDe("ULL1", "BLACK", 50));

        resultado.getGrupos().get(0).getFilas().get(0).corregirCantidad("CHINA", 10);

        assertTrue(resultado.sePuedeGenerar());
    }
}
