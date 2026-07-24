package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static byte[] pedido() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07703 CH"),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "85", 7672),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "95", 7672),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "105", 7672));
    }

    // --- tests ---

    @Test
    void generaUnExcelPorDestinacionSoportada() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665")),
                        destino("CHINA", caja(1, "ULL163.AL0052", "221", null, 40, 4.10, "07703"))),
                cabecera(), Map.of("pedido", pedido()));

        assertEquals(2, resultado.getExcels().size());
        ExcelGenerado paris = resultado.getExcels().get(0);
        assertEquals("PARIS", paris.getDestino());
        assertEquals("Etiquetas_AMI_PARIS_F-123.xlsx", paris.getNombreFichero());
        try (XSSFWorkbook libro = abrir(paris)) {
            assertEquals("AMI FRANCE", libro.getSheetName(0));
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
        ResultadoEtiquetas resultado = generador.generar(List.of(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665"),
                        caja(2, "UBL029.AL0216", "001", "95", 33, 9.93, "07672"),
                        caja(2, "UBL029.AL0216", "001", "85", 4, null, "07672"),
                        caja(2, "UBL029.AL0216", "001", "105", 3, null, "07672"))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // Caja 2 = segundo bloque (FRANCE: 32 filas por bloque).
            assertEquals("UBL029.AL0216", texto(hoja, 10 + 32, 2));
            assertEquals("85-95-105", texto(hoja, 12 + 32, 2));
            assertEquals("4-85,33-95,3-105", texto(hoja, 13 + 32, 2));
            assertEquals("9,93 KGS", texto(hoja, 14 + 32, 2));
            assertEquals("2 / 2", texto(hoja, 15 + 32, 2));
            // La caja 1 (bolso) es talla única.
            assertEquals("U", texto(hoja, 12, 2));
            assertEquals("50", texto(hoja, 13, 2));
            assertEquals("1 / 2", texto(hoja, 15, 2));
            // COLOR CODE sale del excel de pedido (coloris + libellé).
            assertEquals("001 BLACK", texto(hoja, 11 + 32, 2));
        }
        // Las tallas de la misma caja no cuentan como cajas con peso pendiente:
        // el peso es de la caja física entera y lo lleva la línea líder.
        assertTrue(resultado.getExcels().get(0).getCajasPendientes().isEmpty());
    }

    @Test
    void cinturonDeTallaUnicaLlevaCantidadSimple() throws IOException {
        // Una sola talla en la caja: SIZE la talla y QUANTITY a secas (los
        // pares cantidad-talla son solo del caso especial multi-talla).
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("PARIS", caja(1, "UBL029.AL0216", "001", "75", 45, 8.5, "07672"))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertEquals("75", texto(hoja, 12, 2));
            assertEquals("45", texto(hoja, 13, 2));
        }
    }

    @Test
    void destinacionNoReconocidaSeOmiteConAviso() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("HONG KONG", caja(1, "ULL163.AL0052", "221", null, 10, 1.0, null))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getExcels().isEmpty());
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("HONG KONG")));
    }

    @Test
    void referenciaAusenteDelPedidoAvisaYGeneraConFallback() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("PARIS", caja(1, "USL999.XX0000", "007", null, 10, 2.0, "07699"))),
                cabecera(), Map.of("pedido", pedido()));

        assertEquals(1, resultado.getExcels().size());
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("USL999.XX0000")));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            // Fallback: color del JSON tal cual.
            assertEquals("007", texto(libro.getSheetAt(0), 11, 2));
        }
    }

    @Test
    void poDelExcelDiscrepanteDelJsonAvisa() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07777"))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("07777") && aviso.contains("07665")));
    }

    @Test
    void pesoPendienteDejaLaCajaEnCajasPendientes() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, null, "07665"))),
                cabecera(), Map.of("pedido", pedido()));

        assertEquals(1, resultado.getExcels().get(0).getCajasPendientes().size());
    }

    @Test
    void declaraElCampoDelExcelDePedidoSoloSiHayDestinosSoportados() {
        assertEquals("pedido", generador.camposRequeridos(
                List.of(destino("CHINA"))).get(0).nombre());
        assertTrue(generador.camposRequeridos(List.of(destino("HONG KONG"))).isEmpty());
        assertTrue(generador.soportaDestino("paris"));
        assertFalse(generador.soportaDestino("HONG KONG"));
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
