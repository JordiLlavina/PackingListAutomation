package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.DestinoClienteConfig;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.model.PaletData;
import com.puntotres.packinglist.service.EnvioImportService;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.PaletAssignmentService;
import com.puntotres.packinglist.service.ResolutorDestinosPadre;
import com.puntotres.packinglist.service.ResultadoDestinos;

class ApcEtiquetasGeneradorTest {

    // Casi todas las aserciones son sobre JAPAN: las filas se le piden al
    // layout y no se escriben a mano, porque cada plantilla de APC tiene la
    // suya y el cliente las mueve al reajustar el reparto en el A4.
    private static final ApcEtiquetaLayout J = ApcEtiquetaLayout.JAPAN;
    private static final ApcEtiquetaLayout.Palet PALET = J.palet();

    private final ApcEtiquetasGenerador generador =
            new ApcEtiquetasGenerador(new ApcEtiquetasExcelBuilder());

    // --- helpers ---

    private static DatosEnvio envio() {
        DatosEnvio envio = new DatosEnvio();
        envio.setTemporada("E25");
        envio.setNumeroFactura("26071");
        envio.setClaveCliente("APC");
        return envio;
    }

    private static CajaData caja(int numero, String referencia, String color, String talla,
                                 int cantidad, Double pesoBruto, Integer palet) {
        CajaData caja = new CajaData(referencia, color, talla, cantidad, null, pesoBruto);
        caja.setNumeroCaja(numero);
        caja.setNumeroPalet(palet);
        return caja;
    }

    private static PaletData palet(int numero, int inicio, int fin, Double tara) {
        PaletData palet = new PaletData();
        palet.setNumeroPalet(numero);
        palet.setCajaInicio(inicio);
        palet.setCajaFin(fin);
        palet.setTara(tara);
        return palet;
    }

    private static EnvioImportado.DestinoImportado destino(String nombre,
            List<PaletData> palets, CajaData... cajas) {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino(nombre);
        destino.setCajas(List.of(cajas));
        return new EnvioImportado.DestinoImportado(destino, palets);
    }

    private static XSSFSheet hojaCajas(byte[] excel) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(excel))
                .getSheet(ApcEtiquetaLayout.JAPAN.hojaCajas());
    }

    private static String texto(XSSFSheet hoja, int fila, int col) {
        if (hoja.getRow(fila) == null || hoja.getRow(fila).getCell(col) == null) {
            return "";
        }
        return hoja.getRow(fila).getCell(col).toString().trim();
    }

    // --- tests ---

    @Test
    void generaUnExcelPorDestinacionSoportadaYAvisaDeLasNoSoportadas() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1)),
                        destino("IVRY", List.of(),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, null))),
                envio(), Map.of());
        assertEquals(1, resultado.getExcels().size());
        assertEquals("Etiquetas_APC_JAPAN_26071.xlsx",
                resultado.getExcels().get(0).getNombreFichero());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("IVRY")));
    }

    @Test
    void laEtiquetaLlevaElPedidoYElLivraisonCodeDeLaCaja() throws IOException {
        CajaData linea = caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1);
        // Los dos los rellena el packing list al importar: el pedido lo
        // completa PedidoCompletionService y el código, ResolutorDestinosPadre.
        linea.setNumeroPedido("4100128725");
        linea.setLivraisonCode("PUN20260717WH1");

        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("JAPAN", List.of(palet(1, 1, 1, null)), linea)),
                envio(), Map.of());

        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("4100128725", texto(hoja, J.filaOrder(), 2));    // Order N°
        assertEquals("PUN20260717WH1", texto(hoja, J.filaLivraison(), 2)); // Livraison
    }

    @Test
    void sinPedidoNiLivraisonCodeLaEtiquetaSigueDiciendoNotFound() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("NOT FOUND", texto(hoja, J.filaOrder(), 2));   // Order N°
        assertEquals("NOT FOUND", texto(hoja, J.filaLivraison(), 2));  // Livraison
        assertEquals("PXCBC-F67008", texto(hoja, J.filaReferencia(), 2));
        assertEquals("LZZ-NOIR", texto(hoja, J.filaColor(), 2));
        assertEquals("U", texto(hoja, J.filaTalla(), 2));          // bolso: sin talla
        assertEquals("11", texto(hoja, J.filaPiezas(), 2));
        assertEquals("1 / 1", texto(hoja, J.filaColisage(), 2));
        assertEquals("7,60 Kg", texto(hoja, J.filaPeso(), 2));
    }

    @Test
    void retailGeneraSusEtiquetasConSuPropiaPlantilla() throws IOException {
        CajaData linea = caja(1, "PXCBS-F67066", "LZZ-NOIR", null, 2, 3.0, 1);
        linea.setNumeroPedido("4100128725");
        linea.setLivraisonCode("PUN20260717RT1");

        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("RETAIL", List.of(palet(1, 1, 1, null)), linea)),
                envio(), Map.of());

        assertEquals(1, resultado.getExcels().size());
        assertEquals("Etiquetas_APC_RETAIL_26071.xlsx",
                resultado.getExcels().get(0).getNombreFichero());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            XSSFSheet hoja = libro.getSheet(ApcEtiquetaLayout.RETAIL.hojaCajas());
            assertNotNull(hoja);
            assertNotNull(libro.getSheet(ApcEtiquetaLayout.RETAIL.hojaPalet()));
            // RETAIL y WHOLESALE van al mismo almacén y el cliente solo partió
            // la plantilla para que se imprima una palabra u otra en
            // DESTINATION: ese estático es lo que dice qué fichero se ha
            // abierto. Va justo encima del ASN.
            ApcEtiquetaLayout r = ApcEtiquetaLayout.RETAIL;
            assertEquals("RETAIL", texto(hoja, r.filaLivraison() - 1, 2));
            assertEquals("PUN20260717RT1", texto(hoja, r.filaLivraison(), 2)); // ASN N°
            assertEquals("4100128725", texto(hoja, r.filaOrder(), 2));
            assertEquals("PXCBS-F67066", texto(hoja, r.filaReferencia(), 2));
            assertEquals("LZZ-NOIR", texto(hoja, r.filaColor(), 2));
            assertEquals("U", texto(hoja, r.filaTalla(), 2));
            assertEquals("2", texto(hoja, r.filaPiezas(), 2));
            assertEquals("1 / 1", texto(hoja, r.filaColisage(), 2));
            assertEquals("3,00 Kg", texto(hoja, r.filaPeso(), 2));
        }
    }

    @Test
    void unaCajaDeBolsosConDosArticulosConcatenaReferenciaColorYPiezas() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 3, 7.6, 1),
                                caja(1, "PXCBC-F67009", "LZZ-BLANC", null, 5, null, 1))),
                envio(), Map.of());

        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("PXCBC-F67008 / PXCBC-F67009", texto(hoja, J.filaReferencia(), 2));
        assertEquals("LZZ-NOIR / LZZ-BLANC", texto(hoja, J.filaColor(), 2));
        // SIZE no se concatena: "U / U" no dice nada.
        assertEquals("U", texto(hoja, J.filaTalla(), 2));
        assertEquals("3 / 5", texto(hoja, J.filaPiezas(), 2));
        // Peso y colisage son de la caja, no del artículo.
        assertEquals("7,60 Kg", texto(hoja, J.filaPeso(), 2));
        assertEquals("1 / 1", texto(hoja, J.filaColisage(), 2));
        // La etiqueta ya muestra todos los artículos: no hay nada que avisar.
        assertTrue(resultado.getAvisos().isEmpty());
    }

    @Test
    void unaCajaDeUnSoloBolsoSaleExactamenteIgualQueAntes() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1))),
                envio(), Map.of());

        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("PXCBC-F67008", texto(hoja, J.filaReferencia(), 2));
        assertEquals("LZZ-NOIR", texto(hoja, J.filaColor(), 2));
        assertEquals("11", texto(hoja, J.filaPiezas(), 2));
    }

    @Test
    void losCinturonesAgrupanUnidadesPorTalla() throws IOException {
        // Caja de cinturones con tallas 85 (7u), 90 (8u) y 85 (5u de otro
        // pedido/canal) de la misma referencia y color.
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 3, 3, null)),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "85", 7, 6.0, 1),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "90", 8, null, 1),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "85", 5, null, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("85-90", texto(hoja, J.filaTalla(), 2));
        assertEquals("12-85,8-90", texto(hoja, J.filaPiezas(), 2));
        // El peso de la caja entera viene una sola vez, en su primera línea.
        assertEquals("6,00 Kg", texto(hoja, J.filaPeso(), 2));
        assertEquals("1 / 1", texto(hoja, J.filaColisage(), 2));
    }

    /**
     * En una caja mixta de cinturones la referencia SÍ las lleva las dos: lo
     * que no cabe más que para un artículo son SIZE y PIECES BY SIZE, y de
     * eso es de lo que avisa.
     */
    @Test
    void unaCajaDeCinturonesConDosReferenciasAvisaDeMezcla() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 3, 3, null)),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "85", 7, 6.0, 1),
                                caja(3, "PXBHZ-H65078", "LZZ-NOIR", "90", 8, null, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("PXBHZ-H65077 / PXBHZ-H65078", texto(hoja, J.filaReferencia(), 2));
        assertEquals("85", texto(hoja, J.filaTalla(), 2));
        assertTrue(resultado.getAvisos().contains("JAPAN: Caja 3. Mezcla de referencias/colores. "
                        + "SIZE y PIECES BY SIZE son solo de PXBHZ-H65077 LZZ-NOIR"),
                resultado.getAvisos().toString());
    }

    /**
     * Una caja con varios artículos lleva TODOS los Order N° y TODAS las
     * referencias, en el mismo orden, para poder leerlos en paralelo: en APC
     * un Document d'achat es una destinación, un artículo y un color, así que
     * el de la línea líder no vale para los demás artículos de la caja.
     */
    @Test
    void unaCajaConVariosArticulosLlevaTodosLosPedidosYReferencias() throws IOException {
        CajaData bolso = caja(1, "PXCBC-F63024", "LIQUEN", null, 3, 7.96, 1);
        bolso.setNumeroPedido("4100128710");
        CajaData otroColor = caja(1, "PXCBC-F63024", "NOIR", null, 16, null, 1);
        otroColor.setNumeroPedido("4100128711");

        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("JAPAN", List.of(palet(1, 1, 1, null)), bolso, otroColor)),
                envio(), Map.of());

        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("4100128710 / 4100128711", texto(hoja, J.filaOrder(), 2));
        assertEquals("PXCBC-F63024 / PXCBC-F63024", texto(hoja, J.filaReferencia(), 2));
        assertEquals("LIQUEN / NOIR", texto(hoja, J.filaColor(), 2));
        assertEquals("3 / 16", texto(hoja, J.filaPiezas(), 2));
    }

    /**
     * Los cinturones también: la caja de la hoja real de D. USA lleva dos
     * cinturones y dos monederos, cada uno con su pedido.
     */
    @Test
    void unaCajaDeCinturonesYBolsosLlevaTodosLosPedidosEnElOrdenDeLasReferencias()
            throws IOException {
        CajaData cinturon = caja(1, "PXBHZ-F65101", "KBE-OCRE", "75", 5, 7.96, 1);
        cinturon.setNumeroPedido("4100128715");
        CajaData otraTalla = caja(1, "PXBHZ-F65101", "KBE-OCRE", "80", 5, null, 1);
        otraTalla.setNumeroPedido("4100128715");
        CajaData monedero = caja(1, "PXCBC-F63024", "LIQUEN", null, 3, null, 1);
        monedero.setNumeroPedido("4100128710");

        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("JAPAN", List.of(palet(1, 1, 1, null)),
                        cinturon, otraTalla, monedero)),
                envio(), Map.of());

        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        // Las dos tallas del cinturón son UN artículo (referencia + color).
        assertEquals("4100128715 / 4100128710", texto(hoja, J.filaOrder(), 2));
        assertEquals("PXBHZ-F65101 / PXCBC-F63024", texto(hoja, J.filaReferencia(), 2));
    }

    /**
     * Un artículo sin pedido deja "NOT FOUND" en SU posición: un hueco haría
     * dudar de a qué referencia le falta.
     */
    @Test
    void elArticuloSinPedidoDejaNotFoundEnSuPosicion() throws IOException {
        CajaData conPedido = caja(1, "PXCBC-F63024", "LIQUEN", null, 3, 7.96, 1);
        conPedido.setNumeroPedido("4100128710");
        CajaData sinPedido = caja(1, "PXCBC-F63024", "NOIR", null, 16, null, 1);

        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("JAPAN", List.of(palet(1, 1, 1, null)), conPedido, sinPedido)),
                envio(), Map.of());

        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("4100128710 / NOT FOUND", texto(hoja, J.filaOrder(), 2));
    }

    /**
     * La referencia se escribe en la fila que de verdad se ve: en la
     * plantilla de USA la celda de valor está combinada y su rótulo cae una
     * fila más abajo, así que escribir en la del rótulo dejaba a la vista la
     * referencia de ejemplo de la plantilla.
     */
    @Test
    void enUsaLaReferenciaSustituyeALaDeLaPlantillaYNoSeQuedaTapada() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("D. USA", List.of(palet(1, 1, 1, null)),
                        caja(1, "PXBHZ-F65101", "KBE-OCRE", "75", 5, 7.96, 1))),
                envio(), Map.of());

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            XSSFSheet hoja = libro.getSheet(ApcEtiquetaLayout.USA.hojaCajas());
            assertEquals("PXBHZ-F65101",
                    texto(hoja, ApcEtiquetaLayout.USA.filaReferencia(), 2));
        }
    }

    /**
     * Un envío que va suelto (de 1 a 3 cajas, todas con SIN_PALET) no avisa
     * de que falten los palets: no hay ninguno que etiquetar.
     */
    @Test
    void unEnvioSueltoNoAvisaDeQueFaltenLosPalets() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("D. USA", List.of(),
                        caja(1, "PXBHZ-F65101", "KBE-OCRE", "75", 5, 7.96, CajaData.SIN_PALET))),
                envio(), Map.of());

        assertTrue(resultado.getAvisos().isEmpty(), resultado.getAvisos().toString());
    }

    @Test
    void elPaletCeroNoLlevaHojaDeEtiquetasDePalet() throws IOException {
        // Palet 0 = la caja va suelta, no en un palet. El excel salía con la
        // hoja de palets de la plantilla y su etiqueta modelo en blanco, que
        // se imprime y se pega en un bulto igual que una buena.
        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("D. USA", List.of(),
                        caja(1, "PXBHZ-F65101", "KBE-OCRE", "75", 5, 7.96, CajaData.SIN_PALET))),
                envio(), Map.of());

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            assertNotNull(libro.getSheet(ApcEtiquetaLayout.USA.hojaCajas()),
                    "las etiquetas de caja sí se generan");
            assertNull(libro.getSheet(ApcEtiquetaLayout.USA.hojaPalet()));
        }
    }

    @Test
    void unPaletAlQueSeLeHanQuitadoLasCajasNoSeEtiqueta() throws IOException {
        // Pasar las cajas de un palet al 0 en la revisión deja el PaletData
        // del envío original sin ninguna caja: su etiqueta diría un número de
        // cajas y un peso que ya no son de nadie.
        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("JAPAN", List.of(palet(1, 1, 2, null)),
                        caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, CajaData.SIN_PALET),
                        caja(2, "PXCBC-F67008", "LZZ-NOIR", null, 11, 8.2, CajaData.SIN_PALET))),
                envio(), Map.of());

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            assertNull(libro.getSheet(ApcEtiquetaLayout.JAPAN.hojaPalet()));
        }
        assertTrue(resultado.getAvisos().isEmpty(), resultado.getAvisos().toString());
    }

    @Test
    void lasCajasDelPaletSeCuentanDeLasCajasYNoDelRangoViejo() throws IOException {
        // El palet declara 1..3 pero una de sus cajas se ha pasado al 0 en la
        // revisión: la etiqueta dice 2 cajas, las que de verdad lleva.
        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("JAPAN", List.of(palet(1, 1, 3, 8.0)),
                        caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.0, 1),
                        caja(2, "PXCBC-F67008", "LZZ-NOIR", null, 11, 8.0, 1),
                        caja(3, "PXCBC-F67008", "LZZ-NOIR", null, 11, 9.0, CajaData.SIN_PALET))),
                envio(), Map.of());

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            XSSFSheet palet = libro.getSheet(ApcEtiquetaLayout.JAPAN.hojaPalet());
            assertEquals(2, palet.getRow(PALET.filaNumCajas())
                    .getCell(ApcEtiquetaLayout.COL_VALOR).getNumericCellValue(), 0.001);
            // Y el peso, el de esas dos más la tara: 7 + 8 + 8.
            assertEquals("23,00 Kg", texto(palet, PALET.filaPeso(),
                    ApcEtiquetaLayout.COL_VALOR));
        }
    }

    @Test
    void elPaletSumaUnPesoPorCajaFisicaNoPorLinea() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, 8.04)),
                                caja(1, "PXBHZ-H65077", "LZZ-NOIR", "85", 7, 6.0, 1),
                                caja(1, "PXBHZ-H65077", "LZZ-NOIR", "90", 8, null, 1))),
                envio(), Map.of());

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            XSSFSheet palet = libro.getSheet(ApcEtiquetaLayout.JAPAN.hojaPalet());
            assertEquals("14,04 Kg", texto(palet, PALET.filaPeso(), PALET.colValor())); // 6.0 + 8.04 de tara
        }
        assertTrue(resultado.getAvisos().stream().noneMatch(a -> a.contains("sin peso")));
    }

    @Test
    void unaSolaTallaEscribeLaCantidadASecas() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXBHZ-H65077", "LZZ-NOIR", "85", 7, 2.0, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("85", texto(hoja, J.filaTalla(), 2));
        assertEquals("7", texto(hoja, J.filaPiezas(), 2));
    }

    @Test
    void cajaSinPesoSaleEnBlancoYQuedaPendiente() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, null, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("", texto(hoja, J.filaPeso(), 2));
        assertEquals(1, resultado.getExcels().get(0).getCajasPendientes().size());
    }

    /**
     * Una caja de WHOLESALE con material de WHOLESALE y de AUSTRALIA (dos
     * destinaciones hijas que comparten fichero): la línea DESTINATION las
     * dice las dos y Order N° lleva el pedido de cada una en el mismo orden.
     * El mismo artículo a dos destinaciones son DOS artículos, porque en APC
     * el pedido es de la destinación: juntarlos dejaría uno de los dos.
     */
    @Test
    void unaCajaConDosDestinacionesHijasLasNombraYLlevaElPedidoDeCadaUna() throws IOException {
        CajaData wholesale = caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 6, 7.6, 1);
        wholesale.setCanal("WHOLESALE");
        wholesale.setNumeroPedido("4100128710");
        CajaData australia = caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 4, null, 1);
        australia.setCanal("AUSTRALIA");
        australia.setNumeroPedido("4100128799");

        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("WHOLESALE", List.of(palet(1, 1, 1, null)), wholesale, australia)),
                envio(), Map.of());

        ApcEtiquetaLayout wh = ApcEtiquetaLayout.WH_CROSSLOG;
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            XSSFSheet hoja = libro.getSheet(wh.hojaCajas());
            assertEquals("WHOLESALE / AUSTRALIA", texto(hoja, wh.filaDestino(), 2));
            assertEquals("4100128710 / 4100128799", texto(hoja, wh.filaOrder(), 2));
            assertEquals("PXCBC-F67008 / PXCBC-F67008", texto(hoja, wh.filaReferencia(), 2));
            assertEquals("6 / 4", texto(hoja, wh.filaPiezas(), 2));
            // Y en la segunda etiqueta del par, igual.
            assertEquals("WHOLESALE / AUSTRALIA",
                    texto(hoja, wh.filaDestino() + wh.offsetSegundaEtiqueta(), 2));
        }
    }

    /** Una caja que va entera a un sitio deja la DESTINATION de la plantilla. */
    @Test
    void unaCajaDeUnaSolaDestinacionHijaDejaLaDestinacionDeLaPlantilla() throws IOException {
        CajaData australia = caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 4, 7.6, 1);
        australia.setCanal("AUSTRALIA");

        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("WHOLESALE", List.of(palet(1, 1, 1, null)), australia)),
                envio(), Map.of());

        ApcEtiquetaLayout wh = ApcEtiquetaLayout.WH_CROSSLOG;
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            assertEquals("WHOLESALE", texto(libro.getSheet(wh.hojaCajas()), wh.filaDestino(), 2));
        }
    }

    /**
     * La misma regla en la etiqueta de PALET: un palet con cajas de WHOLESALE
     * y de AUSTRALIA dice las dos en DESTINATION; uno con cajas de una sola
     * se queda con lo que dice la plantilla.
     */
    @Test
    void unPaletConCajasDeDosDestinacionesHijasLasNombraAmbas() throws IOException {
        CajaData wholesale = caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 6, 7.6, 1);
        wholesale.setCanal("WHOLESALE");
        CajaData australia = caja(2, "PXCBC-F67008", "LZZ-NOIR", null, 4, 5.0, 1);
        australia.setCanal("AUSTRALIA");
        CajaData soloAustralia = caja(3, "PXCBC-F67008", "LZZ-NOIR", null, 4, 5.0, 2);
        soloAustralia.setCanal("AUSTRALIA");

        ResultadoEtiquetas resultado = generador.generar(List.of(destino("WHOLESALE",
                        List.of(palet(1, 1, 2, null), palet(2, 3, 3, null)),
                        wholesale, australia, soloAustralia)),
                envio(), Map.of());

        ApcEtiquetaLayout.Palet geo = ApcEtiquetaLayout.WH_CROSSLOG.palet();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            XSSFSheet palet = libro.getSheet(ApcEtiquetaLayout.WH_CROSSLOG.hojaPalet());
            assertEquals("WHOLESALE / AUSTRALIA", texto(palet, geo.filaDestino(), geo.colValor()));
            assertEquals("WHOLESALE", texto(palet, geo.filaDestino() + geo.altura(), geo.colValor()));
        }
    }

    /** Cada etiqueta de palet lleva su número arriba a la derecha, a 11 puntos. */
    @Test
    void cadaEtiquetaDePaletLlevaSuNumeroArribaALaDerecha() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null), palet(2, 2, 2, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1),
                                caja(2, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 2))),
                envio(), Map.of());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            XSSFSheet palet = libro.getSheet(ApcEtiquetaLayout.JAPAN.hojaPalet());
            assertEquals("Nº1", texto(palet, 0, PALET.colNumeroPalet()));
            assertEquals("Nº2", texto(palet, PALET.altura(), PALET.colNumeroPalet()));
            var estilo = palet.getRow(0).getCell(PALET.colNumeroPalet()).getCellStyle();
            assertEquals(11, libro.getFontAt(estilo.getFontIndex()).getFontHeightInPoints());
            assertEquals(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT, estilo.getAlignment());
        }
    }

    /** El nombre del fichero lleva la factura de SU destinación. */
    @Test
    void elNombreDelFicheroLlevaLaFacturaDeLaDestinacion() throws IOException {
        EnvioImportado.DestinoImportado japan = destino("JAPAN", List.of(palet(1, 1, 1, null)),
                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1));
        japan.getDestino().setNumeroFactura("26099");

        ResultadoEtiquetas resultado = generador.generar(List.of(japan), envio(), Map.of());

        assertEquals("Etiquetas_APC_JAPAN_26099.xlsx",
                resultado.getExcels().get(0).getNombreFichero());
    }

    @Test
    void laEtiquetaDePaletSumaLasCajasYSuTara() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN",
                                List.of(palet(1, 1, 2, 8.04), palet(2, 3, 3, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1),
                                caja(2, "PXCBC-F67008", "LZZ-NOIR", null, 11, 8.2, 1),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "85", 7, 2.0, 2))),
                envio(), Map.of());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            XSSFSheet palet = libro.getSheet(ApcEtiquetaLayout.JAPAN.hojaPalet());
            // Palet 1: 2 cajas, 7.6 + 8.2 + 8.04 de tara = 23.84.
            assertEquals(2, palet.getRow(PALET.filaNumCajas()).getCell(PALET.colValor())
                    .getNumericCellValue(), 0.001);
            assertEquals("23,84 Kg", texto(palet, PALET.filaPeso(), PALET.colValor()));
            // Palet 2: 1 caja, 2.0 + 10 de tara por defecto = 12.00.
            assertEquals(1, palet.getRow(PALET.filaNumCajas() + PALET.altura())
                    .getCell(PALET.colValor()).getNumericCellValue(), 0.001);
            assertEquals("12,00 Kg",
                    texto(palet, PALET.filaPeso() + PALET.altura(), PALET.colValor()));
        }
    }

    @Test
    void paletConCajasSinPesoAvisaYVaEnBlanco() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, null, 1))),
                envio(), Map.of());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            XSSFSheet palet = libro.getSheet(ApcEtiquetaLayout.JAPAN.hojaPalet());
            assertEquals("", texto(palet, PALET.filaPeso(), PALET.colValor()));
        }
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(a -> a.contains("Palet 1") && a.contains("sin peso")));
    }

    @Test
    void avisaDeCajasSinPaletYDeDestinacionSinPalets() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, null))),
                envio(), Map.of());
        assertEquals(1, resultado.getExcels().size());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("sin palet")));
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("sin palets")));
    }

    @Test
    void elEnvioDeEjemploCompletoGeneraEtiquetasDeTodasSusDestinaciones() throws IOException {
        // envio-apc.json trae Korea, Australia, Wholesale y Retail. Como en el
        // flujo real, el resolutor fusiona Australia y Wholesale bajo su padre
        // WHOLESALE antes de asignar palets: salen tres libros de etiquetas y
        // ninguna destinación se queda sin plantilla.
        EnvioInput envioInput;
        try (InputStream json = getClass().getResourceAsStream("/ejemplos/envio-apc.json")) {
            envioInput = new ObjectMapper().readValue(json, EnvioInput.class);
        }
        EnvioImportado importado = new EnvioImportService().importar(envioInput);
        ResultadoDestinos resueltos = new ResolutorDestinosPadre()
                .resolver(importado.getDestinos(), catalogoApc(), "17/07/2026");
        PaletAssignmentService asignador = new PaletAssignmentService();
        for (EnvioImportado.DestinoImportado destino : resueltos.getDestinos()) {
            asignador.asignar(destino.getDestino(), destino.getPalets());
        }
        ResultadoEtiquetas resultado =
                generador.generar(resueltos.getDestinos(), envio(), Map.of());

        assertEquals(List.of("Etiquetas_APC_KOREA_26071.xlsx",
                        "Etiquetas_APC_WHOLESALE_26071.xlsx",
                        "Etiquetas_APC_RETAIL_26071.xlsx"),
                resultado.getExcels().stream()
                        .map(e -> e.getNombreFichero()).toList());
        assertTrue(resultado.getAvisos().stream()
                .noneMatch(a -> a.contains("sin etiquetas de APC implementadas")));
    }

    /** Catálogo de destinos de APC calcado del application.yml real. */
    private static ClienteConfig catalogoApc() {
        DestinoClienteConfig wholesale = new DestinoClienteConfig();
        wholesale.setAbreviatura("WH");
        wholesale.setDestinosHijo(List.of("AUSTRALIA", "WHOLESALE", "CHINE FRANCH"));
        DestinoClienteConfig retail = new DestinoClienteConfig();
        retail.setAbreviatura("RT");
        retail.setDestinosHijo(List.of("RETAIL", "WHOLESALE CONCESS"));
        DestinoClienteConfig korea = new DestinoClienteConfig();
        korea.setAbreviatura("KRT");
        ClienteConfig apc = new ClienteConfig();
        apc.setDestinos(Map.of("WHOLESALE", wholesale, "RETAIL", retail, "KOREA", korea));
        return apc;
    }
}
