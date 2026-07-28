package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class AmiEtiquetasArticuloGeneradorTest {

    private final AmiEtiquetasArticuloGenerador generador =
            new AmiEtiquetasArticuloGenerador(new EtiquetasArticuloExcelBuilder());

    /** EAN13 válidos y distintos, para no depender del dígito de control al variar filas. */
    private static final String EAN_A = "3666598543892";
    private static final String EAN_B = "3666598543915";

    private static List<String> nombresDeHoja(byte[] xlsx) throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            return java.util.stream.IntStream.range(0, libro.getNumberOfSheets())
                    .mapToObj(libro::getSheetName)
                    .toList();
        }
    }

    private static ExcelEtiquetasArticulo porNombre(ResultadoEtiquetasArticulo resultado,
                                                   String nombreFichero) {
        return resultado.getExcels().stream()
                .filter(excel -> excel.nombreFichero().equals(nombreFichero))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no se generó " + nombreFichero
                        + "; sí: " + resultado.getExcels().stream()
                                .map(ExcelEtiquetasArticulo::nombreFichero).toList()));
    }

    @Test
    void parteEnTresFicherosPorTipoYPais() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A),
                new Fila("SPAIN", "ULL754.AL0218", "A328", "SAND/CHOCOLATE", "U", 7683, EAN_A),
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR/ARGENT VIBRE", "75",
                        "07704 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(3, resultado.getExcels().size());
        assertEquals(List.of(
                        "AMI CODE BARRE H26 MOROCCO.xlsx",
                        "AMI CODE BARRE H26 SPAIN.xlsx",
                        "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx"),
                resultado.getExcels().stream()
                        .map(ExcelEtiquetasArticulo::nombreFichero).sorted().toList());
    }

    @Test
    void sinFilasDeUnGrupoNoSeGeneraEseFicheroNiSeAvisa() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL754.AL0218", "A328", "SAND", "U", 7683, EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(1, resultado.getExcels().size());
        assertEquals("AMI CODE BARRE H26 SPAIN.xlsx",
                resultado.getExcels().get(0).nombreFichero());
        assertTrue(resultado.getAvisos().isEmpty());
    }

    @Test
    void unaHojaPorFilaDelPedido() throws Exception {
        // Mismo artículo y color, dos PO: dos hojas.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A),
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", 7687, EAN_B));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(2, nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx").contenido()).size());
    }

    @Test
    void elNombreDeHojaUsaElLibelleCuandoCabe() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("USL738.AL0137 TRUFFLE 07714CH"), nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx").contenido()));
    }

    @Test
    void elNombreDeHojaCaeAlColorisCuandoElLibelleNoCabe() throws Exception {
        // "USL738.AL0137 SAND-CHOCOLATE 07683" son 34 caracteres: no cabe.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A328", "SAND/CHOCOLATE", "U", 7683, EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("USL738.AL0137 A328 07683"), nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx").contenido()));
    }

    @Test
    void enCinturonesElNombreDeHojaLlevaLaTalla() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL214.AL0223", "001", "BLACK", "85", "07694 JP", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("UBL214.AL0223 BLACK 07694JP 85"), nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx")
                        .contenido()));
    }

    @Test
    void enCinturonesConLibelleLargoCaeAlColoris() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR/ARGENT VIBRE", "75",
                        "07704 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("UBL029.AL0104 0014 07704CH 75"), nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx")
                        .contenido()));
    }

    @Test
    void dosColorisConElMismoLibelleCaenLosDosAlColoris() throws Exception {
        // Si se usara el libellé, las dos hojas se llamarían igual.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "BLACK", "U", 7687, EAN_A),
                new Fila("MOROCCO", "USL738.AL0137", "A237", "BLACK", "U", 7687, EAN_B));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("USL738.AL0137 A237 07687", "USL738.AL0137 A236 07687"),
                nombresDeHoja(porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx")
                        .contenido()));
    }

    @Test
    void dosColorisConLibelleQueSoloDifiereEnMayusculasCaenLosDosAlColoris() throws Exception {
        // POI compara nombres de hoja con equalsIgnoreCase: "NOIR" y "Noir"
        // tienen que tratarse como el mismo nombre repetido, igual que si
        // fueran idénticos, y caer los dos al fallback de COLORIS. Antes del
        // arreglo el fallback no se disparaba y la generación entera fallaba
        // con "The workbook already contains a sheet named...".
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "NOIR", "U", 7704, EAN_A),
                new Fila("MOROCCO", "USL738.AL0137", "A237", "Noir", "U", 7704, EAN_B));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(1, resultado.getExcels().size());
        assertEquals(List.of("USL738.AL0137 A237 07704", "USL738.AL0137 A236 07704"),
                nombresDeHoja(porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx")
                        .contenido()));
    }

    @Test
    void ordenaTodoDescendenteYLaTallaNumericamente() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR", "75", 7704, EAN_A),
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR", "105", 7704, EAN_A),
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR", "95", 7704, EAN_A),
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR", "85", 7704, EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        // 105 antes que 95: si se ordenara como texto saldría 95, 85, 75, 105.
        assertEquals(List.of(
                        "UBL029.AL0104 NOIR 07704 105",
                        "UBL029.AL0104 NOIR 07704 95",
                        "UBL029.AL0104 NOIR 07704 85",
                        "UBL029.AL0104 NOIR 07704 75"),
                nombresDeHoja(porNombre(resultado,
                        "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx").contenido()));
    }

    @Test
    void ordenaLosArticulosDescendente() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "ULL163.AL0052", "221", "BLACK", "U", 7663, EAN_A),
                new Fila("MOROCCO", "USL738.AL0137", "A236", "BLACK", "U", 7687, EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("USL738.AL0137 BLACK 07687", "ULL163.AL0052 BLACK 07663"),
                nombresDeHoja(porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx")
                        .contenido()));
    }

    @Test
    void unEan13InvalidoAvisaPeroGeneraLaHojaSinCodigoDeBarras() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH",
                        "3666598543893"));   // dígito de control incorrecto

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(1, resultado.getExcels().size());
        assertEquals(1, resultado.getAvisos().size());
        String aviso = resultado.getAvisos().get(0);
        assertTrue(aviso.contains("USL738.AL0137"), aviso);
        assertTrue(aviso.contains("3666598543893"), aviso);
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).contenido()))) {
            assertEquals(1, libro.getNumberOfSheets());
            assertTrue(libro.getAllPictures().isEmpty());
        }
    }

    @Test
    void sinEan13AvisaIndicandoQueNoLoTrae() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH"));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(1, resultado.getAvisos().size());
        assertTrue(resultado.getAvisos().get(0).contains("no trae EAN13"),
                resultado.getAvisos().get(0));
    }

    @Test
    void unaTallaVaciaAvisa() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "", "07714 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("Sin talla")),
                resultado.getAvisos().toString());
    }

    @Test
    void unCinturonSinTallaAvisaYSuHojaNoQuedaConEspacioFinal() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL214.AL0223", "001", "BLACK", "", "07694 JP", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("Sin talla")),
                resultado.getAvisos().toString());
        assertEquals(List.of("UBL214.AL0223 BLACK 07694JP"), nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx")
                        .contenido()));
    }

    @Test
    void unCaracterProhibidoEnElPoNoTumbaLaGeneracion() throws Exception {
        // Un '/' tecleado por error en la celda PO llegaría a createSheet y
        // POI rechazaría la hoja: debe quedar saneado antes.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714/CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(1, resultado.getExcels().size());
        List<String> hojas = nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx").contenido());
        assertEquals(1, hojas.size());
        assertFalse(hojas.get(0).contains("/"), hojas.get(0));
    }

    @Test
    void arrastraLosAvisosDeLecturaDelCatalogo() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "", "A236", "TRUFFLE", "U", "07714 CH", EAN_A),
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("ARTICLE")),
                resultado.getAvisos().toString());
    }

    @Test
    void laTemporadaDelExcelManda() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN E27",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A));

        // Aunque el usuario escriba H26, la hoja dice E27.
        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals("AMI CODE BARRE E27 MOROCCO.xlsx",
                resultado.getExcels().get(0).nombreFichero());
    }

    @Test
    void sinTemporadaEnLaHojaSeUsaLaDelFormulario() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals("AMI CODE BARRE H26 MOROCCO.xlsx",
                resultado.getExcels().get(0).nombreFichero());
    }

    @Test
    void sinTemporadaEnNingunSitioSeOmiteYSeAvisa() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "  ");

        assertEquals("AMI CODE BARRE MOROCCO.xlsx",
                resultado.getExcels().get(0).nombreFichero());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("temporada")),
                resultado.getAvisos().toString());
    }

    @Test
    void laDescripcionDiceTipoPaisYNumeroDeHojas() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL214.AL0223", "001", "BLACK", "85", "07694 JP", EAN_A),
                new Fila("SPAIN", "UBL214.AL0223", "001", "BLACK", "75", "07694 JP", EAN_B));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals("Cinturones · SPAIN · 2 hojas",
                resultado.getExcels().get(0).descripcion());
    }

    @Test
    void identificaAlClienteYSuCampoDeArchivo() {
        assertEquals("AMI", generador.claveCliente());
        assertTrue(generador.tituloCampoPedido().contains("AMI"));
    }
}
