package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class AmiEtiquetasGeneradorTest {

    private final AmiEtiquetasGenerador generador =
            new AmiEtiquetasGenerador(new AmiEtiquetasExcelBuilder());

    // --- fixtures ---

    private static DatosEnvio cabecera() {
        DatosEnvio envio = new DatosEnvio();
        envio.setTemporada("H26");
        envio.setNumeroFactura("F-123");
        return envio;
    }

    private static CajaData caja(int numero, String referencia, String color, String talla,
                                 int cantidad, Double pesoBruto, String pedido) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(numero);
        caja.setReferencia(referencia);
        caja.setCodigoColor(color);
        caja.setTalla(talla);
        caja.setCantidad(cantidad);
        caja.setPesoBrutoKg(pesoBruto);
        caja.setNumeroPedido(pedido);
        return caja;
    }

    private static DestinoData destino(String nombre, CajaData... cajas) {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino(nombre);
        destino.setCajas(List.of(cajas));
        return destino;
    }

    private static EnvioImportado.DestinoImportado importado(DestinoData destino) {
        return new EnvioImportado.DestinoImportado(destino, List.of());
    }

    /**
     * Los avisos que NO son de la hoja de palets. Los fixtures de los tests
     * de etiquetas de caja no traen palets a propósito, así que todos reciben
     * el aviso de que esa destinación va sin hoja de palets; filtrarlo deja a
     * la vista lo que cada test sí vigila. Los tests de palets de más abajo
     * miran los avisos sin filtrar.
     */
    private static List<String> avisosDeCaja(ResultadoEtiquetas resultado) {
        return resultado.getAvisos().stream()
                .filter(aviso -> !aviso.contains("hoja de etiquetas de palet"))
                .toList();
    }

    /** EAN128 bien formado: EAN13 + 00001 + PO a 8 dígitos + 16 ceros + país. */
    private static String ean128(String ean13, int po, String pais) {
        return ean13 + "00001" + String.format("%08d", po) + "0000000000000000" + pais;
    }

    private static byte[] pedido() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665,
                        "3666598354771", ean128("3666598354771", 7665, "ES")),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07703 CH",
                        "3666598354771", ean128("3666598354771", 7703, "ES")),
                new Fila("SPAIN", "ULL753.AL0168", "001", "IVORY", "U", 7665,
                        "3666598313495", ean128("3666598313495", 7665, "ES")),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "85", 7672,
                        "3666598890064", ean128("3666598890064", 7672, "MA")),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "95", 7672,
                        "3666598890088", ean128("3666598890088", 7672, "MA")),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "105", 7672,
                        "3666598890101", ean128("3666598890101", 7672, "MA")));
    }

    // --- tests ---

    @Test
    void generaUnExcelPorDestinacionSoportada() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665"))),
                        importado(destino("CHINA", caja(1, "ULL163.AL0052", "221", null, 40, 4.10, "07703")))),
                cabecera(), Map.of("pedido", pedido()));

        assertEquals(2, resultado.getExcels().size());
        ExcelGenerado paris = resultado.getExcels().get(0);
        assertEquals("PARIS", paris.getDestino());
        assertEquals("Etiquetas_AMI_PARIS_F-123.xlsx", paris.getNombreFichero());
        try (XSSFWorkbook libro = abrir(paris)) {
            assertEquals("AMI FRANCE", libro.getSheetName(0));
            // El order number sale del campo 'pedido' del JSON.
            assertEquals("07665", texto(libro.getSheetAt(0),
                    AmiEtiquetaLayout.FRANCE.filaOrderNumber(), 2));
        }
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(1))) {
            assertEquals("AMI CHINA", libro.getSheetName(0));
        }
        // Para inspección manual, como el e2e de packing lists.
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", paris.getNombreFichero()), paris.getContenido());
    }

    @Test
    void cinturonesMultiTallaVanEnUnaEtiquetaConTallasYCantidades() throws IOException {
        // Caja 2 con tres tallas (mismo numeroCaja); solo la líder lleva peso.
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665"),
                        caja(2, "UBL029.AL0216", "001", "95", 33, 9.93, "07672"),
                        caja(2, "UBL029.AL0216", "001", "85", 4, null, "07672"),
                        caja(2, "UBL029.AL0216", "001", "105", 3, null, "07672")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            AmiEtiquetaLayout layout = AmiEtiquetaLayout.FRANCE;
            // Caja 2 = segundo bloque.
            int b = layout.alturaBloque();
            assertEquals("UBL029.AL0216", texto(hoja, layout.filaReferencia() + b, 2));
            assertEquals("85-95-105", texto(hoja, layout.filaTalla() + b, 2));
            assertEquals("4-85,33-95,3-105", texto(hoja, layout.filaCantidad() + b, 2));
            assertEquals("9,93 KGS", texto(hoja, layout.filaPeso() + b, 2));
            assertEquals("2 / 2", texto(hoja, layout.filaParcel() + b, 2));
            // La caja 1 (bolso) es talla única.
            assertEquals("U", texto(hoja, layout.filaTalla(), 2));
            assertEquals("50", texto(hoja, layout.filaCantidad(), 2));
            assertEquals("1 / 2", texto(hoja, layout.filaParcel(), 2));
            // COLOR CODE lleva solo el código numérico, no el nombre del
            // color: el nombre vive en la imagen compuesta y en la hoja extra.
            assertEquals("001", texto(hoja, layout.filaColor() + b, 2));
        }
        // Las tallas de la misma caja no cuentan como cajas con peso pendiente:
        // el peso es de la caja física entera y lo lleva la línea líder.
        assertTrue(resultado.getExcels().get(0).getCajasPendientes().isEmpty());
    }

    @Test
    void unaCajaConVariasReferenciasPesaLoQueDigaSuLineaLider() throws IOException {
        // Un solo peso por caja física, en su primera línea, aunque la caja
        // mezcle referencias: las demás líneas no aportan peso.
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665"),
                        caja(1, "USL728.AL0217", "001", null, 10, null, "07685")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals("5,28 KGS",
                    texto(libro.getSheetAt(0), AmiEtiquetaLayout.FRANCE.filaPeso(), 2));
        }
        assertTrue(resultado.getExcels().get(0).getCajasPendientes().isEmpty());
    }

    @Test
    void cinturonDeTallaUnicaLlevaCantidadSimple() throws IOException {
        // Una sola talla en la caja: SIZE la talla y QUANTITY a secas (los
        // pares cantidad-talla son solo del caso especial multi-talla).
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "UBL029.AL0216", "001", "75", 45, 8.5, "07672")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertEquals("75", texto(hoja, AmiEtiquetaLayout.FRANCE.filaTalla(), 2));
            assertEquals("45", texto(hoja, AmiEtiquetaLayout.FRANCE.filaCantidad(), 2));
        }
    }

    @Test
    void destinacionNoReconocidaSeOmiteConAviso() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("HONG KONG", caja(1, "ULL163.AL0052", "221", null, 10, 1.0, null)))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getExcels().isEmpty());
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("HONG KONG")));
    }

    @Test
    void referenciaAusenteDelPedidoAvisaYGeneraConFallback() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "USL999.XX0000", "007", null, 10, 2.0, "07699")))),
                cabecera(), Map.of("pedido", pedido()));

        assertEquals(1, resultado.getExcels().size());
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("USL999.XX0000")));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            // Fallback: color del JSON tal cual.
            assertEquals("007",
                    texto(libro.getSheetAt(0), AmiEtiquetaLayout.FRANCE.filaColor(), 2));
        }
    }

    @Test
    void poDelExcelDiscrepanteDelJsonAvisaYLaEtiquetaLlevaElDelJson() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07777")))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("07777") && aviso.contains("07665")));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals("07777",
                    texto(libro.getSheetAt(0), AmiEtiquetaLayout.FRANCE.filaOrderNumber(), 2));
        }
    }

    @Test
    void cajaSinPedidoEnElJsonAvisaYVaSinOrderNumber() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, null)))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.startsWith(
                        "PARIS: Caja 1. Sin número de pedido en la entrada.")),
                resultado.getAvisos().toString());
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals("",
                    texto(libro.getSheetAt(0), AmiEtiquetaLayout.FRANCE.filaOrderNumber(), 2));
        }
    }

    /**
     * El pedido que manda en la etiqueta es el de la entrada, pero si no
     * cuadra con el del excel de pedido hay que decirlo: uno de los dos está
     * mal y solo un humano sabe cuál.
     */
    @Test
    void unPedidoQueNoCuadraConElExcelAvisaYSeQuedaElDeLaEntrada() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07666")))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getAvisos().contains(
                        "PARIS: Caja 1. el pedido de entrada (07666) no coincide con el PO"
                        + " del excel de pedido (07665); la etiqueta lleva el de la entrada"),
                resultado.getAvisos().toString());
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals("07666", texto(libro.getSheetAt(0),
                    AmiEtiquetaLayout.FRANCE.filaOrderNumber(), 2));
        }
    }

    @Test
    void pesoPendienteDejaLaCajaEnCajasPendientes() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        assertEquals(1, resultado.getExcels().get(0).getCajasPendientes().size());
    }

    @Test
    void unaCajaDeBolsosConDosArticulosLosConcatenaConBarras() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            AmiEtiquetaLayout layout = AmiEtiquetaLayout.FRANCE;
            assertEquals("ULL163.AL0052 / ULL753.AL0168",
                    texto(hoja, layout.filaReferencia(), 2));
            // COLOR CODE lleva solo los códigos numéricos, no los nombres.
            assertEquals("221 / 001", texto(hoja, layout.filaColor(), 2));
            // SIZE sigue siendo único: "U / U" no aporta nada.
            assertEquals("U", texto(hoja, layout.filaTalla(), 2));
            assertEquals("3 / 5", texto(hoja, layout.filaCantidad(), 2));
            // Peso y parcel son de la caja, no del artículo.
            assertEquals("5,28 KGS", texto(hoja, layout.filaPeso(), 2));
            assertEquals("1 / 1", texto(hoja, layout.filaParcel(), 2));
        }
    }

    @Test
    void unaCajaDeBolsosConTresArticulosLosConcatenaTodos() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665"),
                        caja(1, "USL999.XX0000", "007", null, 2, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            AmiEtiquetaLayout layout = AmiEtiquetaLayout.FRANCE;
            assertEquals("ULL163.AL0052 / ULL753.AL0168 / USL999.XX0000",
                    texto(hoja, layout.filaReferencia(), 2));
            assertEquals("3 / 5 / 2", texto(hoja, layout.filaCantidad(), 2));
            // El tercero no está en el pedido: color del JSON tal cual.
            assertEquals("221 / 001 / 007", texto(hoja, layout.filaColor(), 2));
        }
    }

    @Test
    void laEtiquetaDeVariosArticulosLlevaLosCodigosDeBarrasDelPrimero() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            // EAN13 + EAN128 del primer artículo (dentro de la imagen
            // compuesta) + EAN128, en las dos etiquetas del par: dos
            // imágenes por etiqueta, no seis (el Code 128 del PO ya no existe).
            assertEquals(4, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void unaCajaDeUnSoloBolsoSaleExactamenteIgualQueAntes() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            AmiEtiquetaLayout layout = AmiEtiquetaLayout.FRANCE;
            assertEquals("ULL163.AL0052", texto(hoja, layout.filaReferencia(), 2));
            // COLOR CODE lleva solo el código numérico.
            assertEquals("221", texto(hoja, layout.filaColor(), 2));
            assertEquals("U", texto(hoja, layout.filaTalla(), 2));
            assertEquals("50", texto(hoja, layout.filaCantidad(), 2));
        }
        assertTrue(avisosDeCaja(resultado).isEmpty(), avisosDeCaja(resultado).toString());
    }

    @Test
    void dosLineasDelMismoBolsoEnUnaCajaSumanLaCantidadComoAntes() throws IOException {
        // Mismo artículo repartido en dos líneas: un solo artículo, cantidad
        // sumada. Nada de "30 / 20".
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 30, 5.28, "07665"),
                        caja(1, "ULL163.AL0052", "221", null, 20, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals("ULL163.AL0052",
                    texto(libro.getSheetAt(0), AmiEtiquetaLayout.FRANCE.filaReferencia(), 2));
            assertEquals("50",
                    texto(libro.getSheetAt(0), AmiEtiquetaLayout.FRANCE.filaCantidad(), 2));
        }
    }

    @Test
    void colorAusenteDeJsonYPedidoDejaLaCeldaColorCodeEnBlanco() throws IOException {
        // Caja de un solo bolso cuya referencia no está en el pedido y cuyo
        // JSON no trae color: antes de esta corrección, unir() imprimía el
        // literal "null" en la celda en vez de dejarla en blanco (regla
        // general del proyecto: dato ausente = celda vacía, nunca "null").
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "USL999.XX0000", null, null, 10, 2.0, "07699")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            AmiEtiquetaLayout layout = AmiEtiquetaLayout.FRANCE;
            assertEquals("USL999.XX0000", texto(hoja, layout.filaReferencia(), 2));
            assertEquals("", texto(hoja, layout.filaColor(), 2));
            assertEquals("10", texto(hoja, layout.filaCantidad(), 2));
        }
    }

    @Test
    void unArticuloSinColorDejaUnHuecoSinDescuadrarLasDemasPosiciones() throws IOException {
        // El segundo de tres artículos no tiene color (ni en el JSON ni en
        // el pedido): pierde su posición en COLOR CODE, pero REFERENCE y
        // QUANTITY conservan las tres posiciones alineadas con él, no dos.
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "USL999.XX0000", null, null, 2, null, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            AmiEtiquetaLayout layout = AmiEtiquetaLayout.FRANCE;
            assertEquals("ULL163.AL0052 / USL999.XX0000 / ULL753.AL0168",
                    texto(hoja, layout.filaReferencia(), 2));
            assertEquals("221 /  / 001", texto(hoja, layout.filaColor(), 2));
            assertEquals("3 / 2 / 5", texto(hoja, layout.filaCantidad(), 2));
        }
    }

    @Test
    void declaraElCampoDelExcelDePedidoSoloSiHayDestinosSoportados() {
        assertEquals("pedido", generador.camposRequeridos(
                List.of(destino("CHINA"))).get(0).nombre());
        assertTrue(generador.camposRequeridos(List.of(destino("HONG KONG"))).isEmpty());
        assertTrue(generador.soportaDestino("paris"));
        assertFalse(generador.soportaDestino("HONG KONG"));
    }

    // --- EAN13 y EAN128 ---

    @Test
    void laEtiquetaLlevaLosDosCodigosDeBarras() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("CHINA", caja(1, "ULL163.AL0052", "221", null, 40, 4.10, "07703")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            // Imagen compuesta (con el EAN13 dentro) + EAN128, en las dos
            // etiquetas del par. El Code 128 del PO ya no existe.
            assertEquals(4, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
        assertTrue(avisosDeCaja(resultado).isEmpty(), avisosDeCaja(resultado).toString());
    }

    @Test
    void enCinturonMultiTallaElEanEsElDeLaLineaLider() throws IOException {
        // Las líneas llegan 95, 85, 105: la líder es la 95 (la que lleva el
        // peso), así que el EAN13 es el de la 95, no el de la talla menor.
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(2, "UBL029.AL0216", "001", "95", 33, 9.93, "07672"),
                        caja(2, "UBL029.AL0216", "001", "85", 4, null, "07672"),
                        caja(2, "UBL029.AL0216", "001", "105", 3, null, "07672")))),
                cabecera(), Map.of("pedido", pedido()));

        assertEquals(List.of("PARIS: Caja 2. Lleva varias tallas. "
                + "Se generan códigos de barra aparte"), avisosDeCaja(resultado));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // SIZE sí sale ordenado, aunque el EAN sea el de la líder.
            assertEquals("85-95-105", texto(hoja, AmiEtiquetaLayout.FRANCE.filaTalla(), 2));
            // Imagen compuesta + EAN128 en las dos etiquetas del par.
            assertEquals(4, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void tallaAusenteDelPedidoAvisaConLaCajaYLaEtiquetaVaSinEan() throws IOException {
        // El pedido de PARIS no tiene la talla 75 de este cinturón.
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "UBL029.AL0216", "001", "75", 45, 8.5, "07672")))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("Caja 1") && aviso.contains("PARIS")
                        && aviso.contains("75")), resultado.getAvisos().toString());
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            // Sin EAN13 ni EAN128 solo queda la imagen compuesta, en las dos
            // etiquetas del par.
            assertEquals(2, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
            // El resto de la etiqueta sale igual. COLOR CODE solo el número.
            assertEquals("001",
                    texto(libro.getSheetAt(0), AmiEtiquetaLayout.FRANCE.filaColor(), 2));
        }
    }

    @Test
    void referenciaAusenteDelPedidoVaSinEan() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "USL999.XX0000", "007", null, 10, 2.0, "07699")))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("USL999.XX0000") && aviso.contains("EAN")),
                resultado.getAvisos().toString());
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals(2, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void cinturonConVariasTallasAusentesDelPedidoNoRepiteElAvisoDeReferencia()
            throws IOException {
        // "UBL999.XX0000" no está en el pedido para ninguna talla: buscar()
        // devuelve Optional.empty() para las tres, así que sin dedup el
        // mismo aviso saldría tres veces (uno por talla, resolver() corre
        // una vez por artículo). Es un solo hecho -no depende de la talla-
        // así que debe aparecer una sola vez, y la caja sigue siendo
        // identificable en el texto.
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(3, "UBL999.XX0000", "001", "85", 4, 8.5, "07672"),
                        caja(3, "UBL999.XX0000", "001", "95", 3, null, "07672"),
                        caja(3, "UBL999.XX0000", "001", "105", 2, null, "07672")))),
                cabecera(), Map.of("pedido", pedido()));

        long apariciones = resultado.getAvisos().stream()
                .filter(aviso -> aviso.contains("UBL999.XX0000")
                        && aviso.contains("no encontrada"))
                .count();
        assertEquals(1L, apariciones, resultado.getAvisos().toString());
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("Caja 3") && aviso.contains("PARIS")
                        && aviso.contains("UBL999.XX0000") && aviso.contains("no encontrada")),
                resultado.getAvisos().toString());
    }

    @Test
    void cinturonConVariasTallasAusentesDelPedidoSiguenDistinguiblesPorTalla()
            throws IOException {
        // Distinto de arriba: aquí la referencia SÍ está en el pedido, pero
        // le faltan filas exactas para dos tallas concretas. Eso pasa por
        // FilaPedido.avisosEan, que sí lleva la talla incrustada en el
        // texto: son dos hechos distintos y el dedup no debe colapsarlos.
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(4, "UBL029.AL0216", "001", "70", 4, 8.5, "07672"),
                        caja(4, "UBL029.AL0216", "001", "75", 3, null, "07672")))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getAvisos().stream().anyMatch(aviso -> aviso.contains("70")),
                resultado.getAvisos().toString());
        assertTrue(resultado.getAvisos().stream().anyMatch(aviso -> aviso.contains("75")),
                resultado.getAvisos().toString());
        assertEquals(resultado.getAvisos().size(),
                resultado.getAvisos().stream().distinct().count());
    }

    @Test
    void laCeldaColorCodeLlevaSoloElCodigoNumerico() throws IOException {
        // Antes ponía "001 BLACK": ahora el nombre del color vive en la
        // imagen compuesta y en la hoja extra, no en la celda.
        ResultadoEtiquetas resultado = generador.generar(
                List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", "U", 5, 5.28, "7665")))),
                cabecera(), Map.of("pedido", pedido()));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals("221", texto(libro.getSheetAt(0),
                    AmiEtiquetaLayout.FRANCE.filaColor(), 2));
        }
    }

    @Test
    void cadaEtiquetaLlevaDosImagenes() throws IOException {
        // La compuesta y el EAN128, duplicadas por el par de etiquetas. El
        // Code 128 del PO ya no existe: antes eran 6.
        ResultadoEtiquetas resultado = generador.generar(
                List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", "U", 5, 5.28, "7665")))),
                cabecera(), Map.of("pedido", pedido()));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals(4, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void laHojaExtraLlevaLosCuatroTextosDelArticuloSobrante() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(
                List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", "U", 5, 5.28, "7665"),
                        caja(1, "ULL753.AL0168", "001", "U", 3, null, "7665")))),
                cabecera(), Map.of("pedido", pedido()));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertNotNull(extra, "no se ha generado la hoja de códigos extra");
            int base = RejillaEtiquetas.filaBase(0);
            assertEquals("1 / 1", texto(extra, base, 1));
            assertEquals("PARIS", texto(extra, base + 2, 1));
            assertEquals("ULL753.AL0168", texto(extra, base, 3));
            assertEquals("Size: U", texto(extra, base, 4));
            assertEquals("001 IVORY", texto(extra, base + 1, 3));
            assertEquals("Cde: 07665", texto(extra, base + 1, 4));
        }
    }

    @Test
    void enCinturonesLaHojaExtraLlevaLaTallaDeCadaSobrante() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(
                List.of(importado(destino("PARIS",
                        caja(1, "UBL029.AL0216", "001", "85", 4, 9.93, "7672"),
                        caja(1, "UBL029.AL0216", "001", "95", 33, null, "7672"),
                        caja(1, "UBL029.AL0216", "001", "105", 4, null, "7672")))),
                cabecera(), Map.of("pedido", pedido()));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals("Size: 95", texto(extra, RejillaEtiquetas.filaBase(0), 4));
            assertEquals("Size: 105", texto(extra, RejillaEtiquetas.filaBase(1), 4));
        }
    }

    @Test
    void unArticuloQueNoEstaEnElPedidoSaleSinCdeYConSuColorDelJson()
            throws IOException {
        ResultadoEtiquetas resultado = generador.generar(
                List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", "U", 5, 5.28, "7665"),
                        caja(1, "ULL999.AL9999", "777", "U", 1, null, "7665")))),
                cabecera(), Map.of("pedido", pedido()));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            int base = RejillaEtiquetas.filaBase(0);
            assertEquals("777", texto(extra, base + 1, 3));
            assertEquals("", texto(extra, base + 1, 4));
        }
    }

    @Test
    void sinColumnasEanElAvisoDelLibroLlegaAlResultado() throws IOException {
        byte[] pedidoViejo = PedidoAmiExcel.crearSinColumnasEan("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665));
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665")))),
                cabecera(), Map.of("pedido", pedidoViejo));

        assertTrue(resultado.getAvisos().stream().anyMatch(aviso -> aviso.contains("EAN13")));
        assertEquals(1, resultado.getExcels().size());
    }

    // --- hoja de códigos de barras extra ---

    @Test
    void unaCajaDeBolsosConDosArticulosGeneraLaHojaExtraConElSegundo() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertNotNull(extra);
            // Solo el segundo artículo: el primero va entero en la etiqueta.
            int base = RejillaEtiquetas.filaBase(0);
            assertEquals("1 / 1", texto(extra, base, 1));
            assertEquals("PARIS", texto(extra, base + 2, 1));
            assertEquals("ULL753.AL0168", texto(extra, base, 3));
            assertEquals("Size: U", texto(extra, base, 4));
            assertEquals("001 IVORY", texto(extra, base + 1, 3));
            assertEquals("Cde: 07665", texto(extra, base + 1, 4));
        }
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.equals("PARIS: Caja 1. Mezcla de "
                        + "referencias/colores. Se generan códigos de barra aparte")),
                resultado.getAvisos().toString());
    }

    @Test
    void unCinturonMultiTallaGeneraUnaFilaExtraPorTallaNoLider() throws IOException {
        // La líder es la 95 (la que lleva el peso): la etiqueta imprime su
        // EAN y las tallas 85 y 105 van a la hoja extra.
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(2, "UBL029.AL0216", "001", "95", 33, 9.93, "07672"),
                        caja(2, "UBL029.AL0216", "001", "85", 4, null, "07672"),
                        caja(2, "UBL029.AL0216", "001", "105", 3, null, "07672")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            AmiEtiquetaLayout layout = AmiEtiquetaLayout.FRANCE;
            // La etiqueta no cambia.
            assertEquals("UBL029.AL0216", texto(hoja, layout.filaReferencia(), 2));
            assertEquals("85-95-105", texto(hoja, layout.filaTalla(), 2));
            assertEquals("4-85,33-95,3-105", texto(hoja, layout.filaCantidad(), 2));

            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals("Size: 85", texto(extra, RejillaEtiquetas.filaBase(0), 4));
            assertEquals("Size: 105", texto(extra, RejillaEtiquetas.filaBase(1), 4));
        }
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.equals("PARIS: Caja 2. Lleva varias tallas. "
                        + "Se generan códigos de barra aparte")),
                resultado.getAvisos().toString());
    }

    @Test
    void unaCajaDeUnSoloArticuloNoGeneraHojaExtraNiAviso() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals(1, libro.getNumberOfSheets());
        }
        assertTrue(avisosDeCaja(resultado).isEmpty(), avisosDeCaja(resultado).toString());
    }

    @Test
    void lasFilasExtraDeVariasCajasVanEnLaMismaHoja() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665"),
                        caja(2, "ULL163.AL0052", "221", null, 4, 6.10, "07665"),
                        caja(2, "ULL753.AL0168", "001", null, 6, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            // Un bloque por caja, distinguibles por su parcel (ya no se
            // guarda el número de caja en la hoja extra).
            assertEquals("1 / 2", texto(extra, RejillaEtiquetas.filaBase(0), 1));
            assertEquals("2 / 2", texto(extra, RejillaEtiquetas.filaBase(1), 1));
        }
    }

    @Test
    void dejaUnExcelDeVariosArticulosParaInspeccionManual() throws IOException {
        // Tres artículos: es donde el texto concatenado se pasa del ancho de
        // la columna y hay que ver en Excel que la fuente encoge de verdad.
        DatosEnvio envio = cabecera();
        envio.setNumeroFactura("F-MULTI");
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665"),
                        caja(1, "USL999.XX0000", "007", null, 2, null, "07665"),
                        caja(2, "UBL029.AL0216", "001", "95", 33, 9.93, "07672"),
                        caja(2, "UBL029.AL0216", "001", "85", 4, null, "07672"),
                        caja(2, "UBL029.AL0216", "001", "105", 3, null, "07672")))),
                envio, Map.of("pedido", pedido()));

        ExcelGenerado excel = resultado.getExcels().get(0);
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", excel.getNombreFichero()), excel.getContenido());
        assertEquals("Etiquetas_AMI_PARIS_F-MULTI.xlsx", excel.getNombreFichero());
    }

    // --- etiquetas de palet ---

    private static CajaData cajaEnPalet(int numero, Double pesoBruto, Integer palet) {
        CajaData caja = caja(numero, "ULL163.AL0052", "221", null, 10, pesoBruto, "07665");
        caja.setNumeroPalet(palet);
        return caja;
    }

    private static PaletData palet(int numero, int cajaInicio, int cajaFin, Double tara) {
        PaletData palet = new PaletData();
        palet.setNumeroPalet(numero);
        palet.setCajaInicio(cajaInicio);
        palet.setCajaFin(cajaFin);
        palet.setTara(tara);
        return palet;
    }

    private static EnvioImportado.DestinoImportado importado(DestinoData destino,
                                                             PaletData... palets) {
        return new EnvioImportado.DestinoImportado(destino, List.of(palets));
    }

    private static XSSFSheet hojaDePalets(ExcelGenerado excel, AmiEtiquetaLayout layout)
            throws IOException {
        return abrir(excel).getSheet(layout.nombreHojaPalets());
    }

    private static String colisDelPalet(XSSFSheet hoja, int indice) {
        return texto(hoja, AmiEtiquetaLayout.FILA_PALET_COLIS
                + indice * AmiEtiquetaLayout.ALTURA_BLOQUE_PALET, 2);
    }

    private static String pesoDelPalet(XSSFSheet hoja, int indice) {
        return texto(hoja, AmiEtiquetaLayout.FILA_PALET_PESO
                + indice * AmiEtiquetaLayout.ALTURA_BLOQUE_PALET, 2);
    }

    @Test
    void elRangoDeCajasSaleDeLasCajasYNoDelRangoDelJson() throws IOException {
        // El rango del PaletData es el del JSON original y se queda viejo en
        // cuanto el usuario corrige un palet en la pantalla de revisión: las
        // cajas 1..3 están en el palet 1 aunque el JSON dijera 1..9.
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(
                        destino("PARIS",
                                cajaEnPalet(1, 5.0, 1),
                                cajaEnPalet(2, 5.0, 1),
                                cajaEnPalet(3, 5.0, 1)),
                        palet(1, 1, 9, 8.0))),
                cabecera(), Map.of("pedido", pedido()));

        XSSFSheet hoja = hojaDePalets(resultado.getExcels().get(0), AmiEtiquetaLayout.FRANCE);
        assertEquals("Nº 1 à Nº 3", colisDelPalet(hoja, 0));
    }

    @Test
    void elPesoDelPaletSumaSusCajasMasLaTara() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(
                        destino("PARIS", cajaEnPalet(1, 5.28, 1), cajaEnPalet(2, 4.10, 1)),
                        palet(1, 1, 2, 8.0))),
                cabecera(), Map.of("pedido", pedido()));

        XSSFSheet hoja = hojaDePalets(resultado.getExcels().get(0), AmiEtiquetaLayout.FRANCE);
        assertEquals("17,38 Kg", pesoDelPalet(hoja, 0));
    }

    @Test
    void sinTaraEnElJsonElPaletPesaDiezKilosMas() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(
                        destino("PARIS", cajaEnPalet(1, 5.28, 1)),
                        palet(1, 1, 1, null))),
                cabecera(), Map.of("pedido", pedido()));

        XSSFSheet hoja = hojaDePalets(resultado.getExcels().get(0), AmiEtiquetaLayout.FRANCE);
        assertEquals("15,28 Kg", pesoDelPalet(hoja, 0));
    }

    @Test
    void cadaPaletTieneSuEtiquetaEnOrdenDeNumeroDePalet() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(
                        destino("PARIS",
                                cajaEnPalet(1, 5.0, 2),
                                cajaEnPalet(2, 5.0, 1),
                                cajaEnPalet(3, 5.0, 2)),
                        palet(2, 1, 3, 0.0), palet(1, 2, 2, 0.0))),
                cabecera(), Map.of("pedido", pedido()));

        XSSFSheet hoja = hojaDePalets(resultado.getExcels().get(0), AmiEtiquetaLayout.FRANCE);
        assertEquals("Nº 2 à Nº 2", colisDelPalet(hoja, 0));
        assertEquals("Nº 1 à Nº 3", colisDelPalet(hoja, 1));
    }

    @Test
    void unaCajaSinPaletDejaLaDestinacionSinHojaDePalets() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(
                        destino("PARIS", cajaEnPalet(1, 5.0, 1), cajaEnPalet(2, 5.0, null)),
                        palet(1, 1, 1, 8.0))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertNull(libro.getSheet(AmiEtiquetaLayout.FRANCE.nombreHojaPalets()));
        }
        assertTrue(resultado.getAvisos().stream()
                        .anyMatch(aviso -> aviso.contains("hoja de etiquetas de palet")
                                && aviso.contains("PARIS")),
                resultado.getAvisos().toString());
    }

    @Test
    void sinNingunPaletTampocoHayHojaDePalets() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(
                        destino("PARIS", cajaEnPalet(1, 5.0, null)))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertNull(libro.getSheet(AmiEtiquetaLayout.FRANCE.nombreHojaPalets()));
        }
        assertTrue(resultado.getAvisos().stream()
                        .anyMatch(aviso -> aviso.contains("hoja de etiquetas de palet")),
                resultado.getAvisos().toString());
    }

    @Test
    void unPaletConUnaCajaSinPesoSaleSinPesoPeroConHoja() throws IOException {
        // Falta un peso, no un palet: la hoja se genera igual y solo esa
        // etiqueta va sin peso, como el resto del proyecto.
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(
                        destino("PARIS", cajaEnPalet(1, 5.0, 1), cajaEnPalet(2, null, 1)),
                        palet(1, 1, 2, 8.0))),
                cabecera(), Map.of("pedido", pedido()));

        XSSFSheet hoja = hojaDePalets(resultado.getExcels().get(0), AmiEtiquetaLayout.FRANCE);
        assertNotNull(hoja, "la hoja de palets debe generarse: no falta ningún palet");
        assertEquals("Nº 1 à Nº 2", colisDelPalet(hoja, 0));
        assertEquals("", pesoDelPalet(hoja, 0));
        assertTrue(resultado.getAvisos().stream()
                        .anyMatch(aviso -> aviso.contains("Palet 1") && aviso.contains("peso")),
                resultado.getAvisos().toString());
    }

    @Test
    void unPaletSinNingunaCajaSeOmiteConAviso() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(
                        destino("PARIS", cajaEnPalet(1, 5.0, 1)),
                        palet(1, 1, 1, 8.0), palet(2, 2, 2, 8.0))),
                cabecera(), Map.of("pedido", pedido()));

        XSSFSheet hoja = hojaDePalets(resultado.getExcels().get(0), AmiEtiquetaLayout.FRANCE);
        // Solo la etiqueta del palet 1: la del 2 no se inventa.
        assertEquals("Nº 1 à Nº 1", colisDelPalet(hoja, 0));
        assertEquals("", colisDelPalet(hoja, 1));
        assertTrue(resultado.getAvisos().stream()
                        .anyMatch(aviso -> aviso.contains("Palet 2")
                                && aviso.contains("sin cajas")),
                resultado.getAvisos().toString());
    }

    @Test
    void conPaletsElLibroTraeLaHojaDeCajasPrimeroYLaDePaletsDespues() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(
                        destino("CHINA", cajaEnPalet(1, 5.0, 1)),
                        palet(1, 1, 1, 8.0))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals(2, libro.getNumberOfSheets());
            assertEquals("AMI CHINA", libro.getSheetName(0));
            assertEquals(AmiEtiquetaLayout.CHINA.nombreHojaPalets(), libro.getSheetName(1));
        }
    }

    @Test
    void dejaUnExcelConEtiquetasDePaletParaInspeccionManual() throws IOException {
        // No afirma casi nada: existe para abrirlo en Excel y comprobar con el
        // ojo la maquetación de la hoja de palets y su paginación (tres
        // palets = dos páginas, la segunda con una sola etiqueta).
        DatosEnvio envio = cabecera();
        envio.setNumeroFactura("F-PALETS");
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(
                        destino("PARIS",
                                cajaEnPalet(1, 5.28, 1), cajaEnPalet(2, 4.75, 1),
                                cajaEnPalet(3, 5.10, 1), cajaEnPalet(4, 6.02, 2),
                                cajaEnPalet(5, 4.98, 2), cajaEnPalet(6, 5.44, 3)),
                        palet(1, 1, 3, 12.0), palet(2, 4, 5, 12.0), palet(3, 6, 6, null))),
                envio, Map.of("pedido", pedido()));

        ExcelGenerado excel = resultado.getExcels().get(0);
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", excel.getNombreFichero()), excel.getContenido());

        XSSFSheet hoja = hojaDePalets(excel, AmiEtiquetaLayout.FRANCE);
        assertEquals("Nº 1 à Nº 3", colisDelPalet(hoja, 0));
        assertEquals("27,13 Kg", pesoDelPalet(hoja, 0));
        assertEquals("Nº 6 à Nº 6", colisDelPalet(hoja, 2));
        // El tercer palet no trae tara en el JSON: 5,44 + 10 por defecto.
        assertEquals("15,44 Kg", pesoDelPalet(hoja, 2));
    }

    private static XSSFWorkbook abrir(ExcelGenerado excel) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(excel.getContenido()));
    }

    private static String texto(XSSFSheet hoja, int fila, int col) {
        if (hoja.getRow(fila) == null || hoja.getRow(fila).getCell(col) == null) {
            return "";
        }
        return hoja.getRow(fila).getCell(col).toString().trim();
    }
}
