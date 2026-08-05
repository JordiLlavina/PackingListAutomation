package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.model.PaletData;
import com.puntotres.packinglist.service.EnvioImportService;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.PaletAssignmentService;

class ApcEtiquetasGeneradorTest {

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
        assertEquals("4100128725", texto(hoja, 9, 2));    // Order N°
        assertEquals("PUN20260717WH1", texto(hoja, 10, 2)); // Livraison
    }

    @Test
    void sinPedidoNiLivraisonCodeLaEtiquetaSigueDiciendoNotFound() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("NOT FOUND", texto(hoja, 9, 2));   // Order N°
        assertEquals("NOT FOUND", texto(hoja, 10, 2));  // Livraison
        assertEquals("PXCBC-F67008", texto(hoja, 11, 2));
        assertEquals("LZZ-NOIR", texto(hoja, 12, 2));
        assertEquals("U", texto(hoja, 13, 2));          // bolso: sin talla
        assertEquals("11", texto(hoja, 14, 2));
        assertEquals("1 / 1", texto(hoja, 17, 2));
        assertEquals("7,60 Kg", texto(hoja, 18, 2));
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
            // RETAIL comparte coordenadas con WHOLESALE, así que este estático
            // de la plantilla es lo ÚNICO que distingue haber abierto una u
            // otra: sin él, apuntar RETAIL al fichero de Crosslog pasaría
            // todas las demás aserciones.
            assertEquals("RETAIL", texto(hoja, 10, 2));
            assertEquals("PUN20260717RT1", texto(hoja, 11, 2)); // ASN N°
            assertEquals("4100128725", texto(hoja, 12, 2));     // Order N°
            assertEquals("PXCBS-F67066", texto(hoja, 13, 2));
            assertEquals("LZZ-NOIR", texto(hoja, 14, 2));
            assertEquals("U", texto(hoja, 15, 2));
            assertEquals("2", texto(hoja, 16, 2));
            assertEquals("1 / 1", texto(hoja, 18, 2));
            assertEquals("3,00 Kg", texto(hoja, 19, 2));
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
        assertEquals("PXCBC-F67008 / PXCBC-F67009", texto(hoja, 11, 2));
        assertEquals("LZZ-NOIR / LZZ-BLANC", texto(hoja, 12, 2));
        // SIZE no se concatena: "U / U" no dice nada.
        assertEquals("U", texto(hoja, 13, 2));
        assertEquals("3 / 5", texto(hoja, 14, 2));
        // Peso y colisage son de la caja, no del artículo.
        assertEquals("7,60 Kg", texto(hoja, 18, 2));
        assertEquals("1 / 1", texto(hoja, 17, 2));
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
        assertEquals("PXCBC-F67008", texto(hoja, 11, 2));
        assertEquals("LZZ-NOIR", texto(hoja, 12, 2));
        assertEquals("11", texto(hoja, 14, 2));
    }

    @Test
    void losCinturonesAgrupanUnidadesPorTalla() throws IOException {
        // Caso real de envio-apc.json: caja 3 con tallas 85 (7u), 90 (8u) y
        // 85 (5u de otro pedido/canal) de la misma referencia y color.
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 3, 3, null)),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "85", 7, 6.0, 1),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "90", 8, null, 1),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "85", 5, null, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("85-90", texto(hoja, 13, 2));
        assertEquals("12-85,8-90", texto(hoja, 14, 2));
        // El peso de la caja entera viene una sola vez, en su primera línea.
        assertEquals("6,00 Kg", texto(hoja, 18, 2));
        assertEquals("1 / 1", texto(hoja, 17, 2));
    }

    @Test
    void unaCajaDeCinturonesConDosReferenciasAvisaDeMezcla() throws IOException {
        // Los cinturones no muestran todos los artículos (a diferencia de los
        // bolsos): la etiqueta sigue llevando solo la línea líder, así que
        // una caja mixta de verdad sigue siendo un aviso.
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 3, 3, null)),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "85", 7, 6.0, 1),
                                caja(3, "PXBHZ-H65078", "LZZ-NOIR", "90", 8, null, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("PXBHZ-H65077", texto(hoja, 11, 2));
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("La caja 3 de JAPAN")
                && a.contains("mezcla varias referencias/colores")
                && a.contains("la etiqueta lleva PXBHZ-H65077 LZZ-NOIR")));
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
            assertEquals("14,04 Kg", texto(palet, 13, 2)); // 6.0 + 8.04 de tara
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
        assertEquals("85", texto(hoja, 13, 2));
        assertEquals("7", texto(hoja, 14, 2));
    }

    @Test
    void cajaSinPesoSaleEnBlancoYQuedaPendiente() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, null, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("", texto(hoja, 18, 2));
        assertEquals(1, resultado.getExcels().get(0).getCajasPendientes().size());
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
            assertEquals(2, palet.getRow(12).getCell(2).getNumericCellValue(), 0.001);
            assertEquals("23,84 Kg", texto(palet, 13, 2));
            // Palet 2: 1 caja, 2.0 + 10 de tara por defecto = 12.00.
            assertEquals(1, palet.getRow(12 + 14).getCell(2).getNumericCellValue(), 0.001);
            assertEquals("12,00 Kg", texto(palet, 13 + 14, 2));
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
            assertEquals("", texto(palet, 13, 2));
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
    void elEnvioDeEjemploCompletoSoloAvisaDeIvry() throws IOException {
        // envio-apc.json solo trae IVRY (sin plantilla de etiquetas): no se
        // genera ningún excel pero tampoco se lanza nada.
        EnvioInput envioInput;
        try (InputStream json = getClass().getResourceAsStream("/ejemplos/envio-apc.json")) {
            envioInput = new ObjectMapper().readValue(json, EnvioInput.class);
        }
        EnvioImportado importado = new EnvioImportService().importar(envioInput);
        PaletAssignmentService asignador = new PaletAssignmentService();
        for (EnvioImportado.DestinoImportado destino : importado.getDestinos()) {
            asignador.asignar(destino.getDestino(), destino.getPalets());
        }
        ResultadoEtiquetas resultado =
                generador.generar(importado.getDestinos(), envio(), Map.of());
        assertEquals(0, resultado.getExcels().size());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("IVRY")));
    }
}
