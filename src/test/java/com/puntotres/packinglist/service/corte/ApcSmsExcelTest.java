package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.ShapeTypes;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFSimpleShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.drawingml.x2006.main.STSchemeColorVal;

import com.puntotres.packinglist.testutil.FotosDePrueba;

/**
 * El lector del excel de SMS de APC, contra la copia del real de FALL 27
 * (sin los croquis: en el repo no entra ninguna imagen real) y, para los
 * croquis, contra un libro hecho aquí.
 */
class ApcSmsExcelTest {

    private final ClienteCorte apc = new ApcCorte();

    private static byte[] real() throws IOException {
        try (InputStream entrada = ApcSmsExcelTest.class
                .getResourceAsStream("/ejemplos/corte/COMMANDE_SMS_APC_FALL27.xlsx")) {
            return entrada.readAllBytes();
        }
    }

    private Map<String, LineaSms> porDonde(ApcSmsExcel.Lectura lectura) {
        return lectura.lineas().stream().collect(Collectors.toMap(LineaSms::donde, Function.identity()));
    }

    @Test
    void leeLasDosHojasYSaltaLasQueNoLlevanSms() throws IOException {
        ApcSmsExcel.Lectura lectura = ApcSmsExcel.leer(real(), apc);

        // FEMME: 31 filas, todas con muestras. HOMME: 10, cuatro con «PAS DE SMS».
        assertEquals(31, lectura.lineas().stream().filter(linea -> linea.hoja().equals("FEMME")).count());
        assertEquals(6, lectura.lineas().stream().filter(linea -> linea.hoja().equals("HOMME")).count());
        assertTrue(lectura.avisos().stream().noneMatch(aviso -> aviso.contains("PAS DE SMS")), lectura.avisos()
                .toString());
    }

    @Test
    void cadaFilaTraeReferenciaModeloNombreColorYMuestras() throws IOException {
        LineaSms linea = porDonde(ApcSmsExcel.leer(real(), apc)).get("FEMME, fila 2");

        assertEquals("PXCBC-F67008", linea.referencia());
        assertEquals("F67008", linea.modelo());
        assertEquals("LE NEIGE", linea.nombreModelo());
        assertEquals("KAF", linea.color().codigo());
        assertEquals("VERT FONCÉ", linea.color().descripcion());
        assertEquals(2, linea.muestras());
        assertEquals(new PielCorte("ALPACA TOUCH", "VERT FONCE"), linea.pieles().principal());
        assertTrue(!linea.pieles().tieneForro() && linea.pieles().combinaciones().isEmpty());
    }

    @Test
    void laReferenciaSeLimpiaDeTabuladoresYDeEspaciosAlrededorDelGuion() throws IOException {
        Map<String, LineaSms> lineas = porDonde(ApcSmsExcel.leer(real(), apc));

        assertEquals("PXCGX-F67008", lineas.get("FEMME, fila 4").referencia());
        assertEquals("PXCBC-H63044", lineas.get("HOMME, fila 5").referencia());
        assertEquals("H63044", lineas.get("HOMME, fila 5").modelo());
    }

    @Test
    void dosPielesConMasYSusColoresConBarraSonLaPrincipalYUnaCombinacion() throws IOException {
        PielesArticulo pieles = porDonde(ApcSmsExcel.leer(real(), apc)).get("FEMME, fila 15").pieles();

        assertEquals(new PielCorte("BOMBARDIER BLK FIX ENC 12MM CM CAMEL", "ECRU"), pieles.principal());
        assertEquals(List.of(new PielCorte("NOBILE", "CAE MARRON FONCÉ")), pieles.combinaciones());
    }

    @Test
    void sinTantosColoresComoPielesElColorEnteroVaALaPrincipal() {
        PielesArticulo pieles = ApcSmsExcel.pieles("UNA + OTRA", "NEGRO", "");

        assertEquals(new PielCorte("UNA", "NEGRO"), pieles.principal());
        assertEquals(List.of(new PielCorte("OTRA", "")), pieles.combinaciones());
    }

    @Test
    void elForroSaleDeColorisDoublureEnFemmeYDeForroEnHomme() throws IOException {
        Map<String, LineaSms> lineas = porDonde(ApcSmsExcel.leer(real(), apc));

        assertEquals("STILLA 999", lineas.get("FEMME, fila 21").pieles().forro().nombre());
        assertEquals("STILLA 175", lineas.get("HOMME, fila 5").pieles().forro().nombre());
    }

    @Test
    void elMismoModeloConDosNombresSeAvisa() throws IOException {
        // En el real, la fila 5 de FEMME dice F67081 «LE NEIGE» y la 10, F67081 «LE NEIGE MINI».
        List<String> avisos = ApcSmsExcel.leer(real(), apc).avisos();

        assertTrue(avisos.stream().anyMatch(aviso -> aviso.startsWith("El modelo F67081 se llama «LE NEIGE» en "
                + "FEMME, fila 5 (PXCHC-F67081) y «LE NEIGE MINI» en FEMME, fila 10 (PXCBC-F67081)")), avisos.toString());
    }

    @Test
    void lasMuestrasSonElNumeroDelPrincipio() {
        assertEquals(4, ApcSmsExcel.muestras("4"));
        assertEquals(4, ApcSmsExcel.muestras("4 SMS"));
        assertEquals(12, ApcSmsExcel.muestras(" 12 sms"));
        assertEquals(0, ApcSmsExcel.muestras("PAS DE SMS"));
        assertEquals(0, ApcSmsExcel.muestras("Pas de SMS"));
        assertEquals(-1, ApcSmsExcel.muestras(""));
        assertEquals(-1, ApcSmsExcel.muestras("TBC"));
    }

    @Test
    void unaCantidadQueNoEsUnNumeroNoSeCortaYSeAvisa() throws IOException {
        byte[] excel = libro(hoja -> {
            fila(hoja, 1, "PXCBC-F67008", "LZZ NOIR", "ALPACA TOUCH", "4 SMS");
            fila(hoja, 2, "PXCGY-F67008", "LZZ NOIR", "PIPER", "TBC");
            fila(hoja, 3, "PXCGX-F67008", "LZZ NOIR", "ALASKA", "");
        });

        ApcSmsExcel.Lectura lectura = ApcSmsExcel.leer(excel, apc);

        assertEquals(1, lectura.lineas().size());
        assertEquals(4, lectura.lineas().get(0).muestras());
        assertTrue(lectura.avisos().contains("FEMME, fila 3 (PXCGY-F67008): «Commande SMS» dice «TBC», que no "
                + "es un número: no se corta"), lectura.avisos().toString());
        assertTrue(lectura.avisos().contains("FEMME, fila 4 (PXCGX-F67008): «Commande SMS» está vacía: no se "
                + "corta"), lectura.avisos().toString());
    }

    @Test
    void elCroquisEsLaImagenDeSuFilaYUnaPegadaDosVecesCuentaUna() throws IOException {
        byte[] croquis = FotosDePrueba.jpeg(300, 200, Color.RED);
        byte[] excel = libro(hoja -> {
            fila(hoja, 1, "PXCBC-F67008", "LZZ NOIR", "ALPACA TOUCH", "2");
            fila(hoja, 2, "PXCGY-F67008", "LZZ NOIR", "PIPER", "3");
            fila(hoja, 3, "PXCGX-F67008", "LZZ NOIR", "ALASKA", "1");
            XSSFDrawing dibujo = hoja.createDrawingPatriarch();
            int indice = hoja.getWorkbook().addPicture(croquis, Workbook.PICTURE_TYPE_JPEG);
            imagenEn(dibujo, indice, 1, 0);
            imagenEn(dibujo, indice, 1, 0);
            imagenEn(dibujo, indice, 2, 0);
            // En otra columna no es el croquis.
            imagenEn(dibujo, indice, 3, 2);
        });

        List<LineaSms> lineas = ApcSmsExcel.leer(excel, apc).lineas();

        assertEquals(1, lineas.get(0).croquis().size());
        assertEquals(300, lineas.get(0).croquis().get(0).ancho());
        assertSame(lineas.get(0).croquis().get(0), lineas.get(1).croquis().get(0),
                "la misma imagen es el mismo objeto: el Excel de órdenes la guarda una vez");
        assertTrue(lineas.get(2).croquis().isEmpty());
    }

    /**
     * El cliente recorta los croquis en Excel y lo recortado sigue en el
     * fichero (en el real, una franja negra encima del bolso): la hoja de
     * corte enseña lo mismo que Excel.
     */
    @Test
    void elCroquisSaleRecortadoComoEnExcel() throws IOException {
        byte[] croquis = FotosDePrueba.jpeg(400, 200, Color.RED);
        byte[] excel = libro(hoja -> {
            fila(hoja, 1, "PXCBC-F67008", "LZZ NOIR", "ALPACA TOUCH", "2");
            XSSFPicture imagen = hoja.createDrawingPatriarch().createPicture(
                    new XSSFClientAnchor(0, 0, 0, 0, 0, 1, 1, 2),
                    hoja.getWorkbook().addPicture(croquis, Workbook.PICTURE_TYPE_JPEG));
            var recorte = imagen.getCTPicture().getBlipFill().addNewSrcRect();
            recorte.setL(25000);
            recorte.setR(25000);
            recorte.setT(-1);
            recorte.setB(50000);
        });

        Imagen leida = ApcSmsExcel.leer(excel, apc).lineas().get(0).croquis().get(0);

        assertEquals(200, leida.ancho());
        assertEquals(100, leida.alto());
    }

    /**
     * En el real, cada celda de COLORIS A.P.C. lleva una elipse rellena del
     * color del bolso: en RGB ("273C18") o con un color del tema y un
     * «más oscuro» ("accent2" al 50 %, el «Naranja, Énfasis 2, más oscuro
     * 50 %» de Excel, que en HSL da 843C0B). Hay también elipses de alto cero, invisibles,
     * que no cuentan; con dos en la celda manda la de encima, la última.
     */
    @Test
    void laRedonditaDeLaCeldaDelColorEsElColorDeLaMuestra() throws IOException {
        byte[] excel = libro(hoja -> {
            fila(hoja, 1, "PXCBC-F67008", "KAF VERT FONCÉ", "ALPACA TOUCH", "2");
            fila(hoja, 2, "PXCGY-F67008", "LZZ NOIR", "PIPER", "3");
            fila(hoja, 3, "PXCGX-F67008", "TTG MARRON", "ALASKA", "1");
            fila(hoja, 4, "PXCGX-F67009", "LZZ NOIR", "ALASKA", "1");
            XSSFDrawing dibujo = hoja.createDrawingPatriarch();
            redonda(dibujo, 1, 2).setFillColor(0x27, 0x3C, 0x18);
            // De alto cero: no se ve y no cuenta.
            dibujo.createSimpleShape(new XSSFClientAnchor(100, 0, 300, 0, 2, 2, 2, 2))
                    .setShapeType(ShapeTypes.ELLIPSE);
            redonda(dibujo, 2, 2).setFillColor(0xB4, 0, 0);
            redonda(dibujo, 2, 2).setFillColor(0x23, 0x10, 0x03);
            var tema = redonda(dibujo, 3, 2).getCTShape().getSpPr().addNewSolidFill().addNewSchemeClr();
            tema.setVal(STSchemeColorVal.ACCENT_2);
            tema.addNewLumMod().setVal(50000);
            // Fuera de la columna del color, o un rectángulo: no es la redondita.
            redonda(dibujo, 4, 3).setFillColor(0xB4, 0, 0);
            XSSFSimpleShape rectangulo = dibujo.createSimpleShape(
                    new XSSFClientAnchor(0, 0, Units.EMU_PER_POINT * 20, Units.EMU_PER_POINT * 20, 2, 4, 2, 4));
            rectangulo.setShapeType(ShapeTypes.RECT);
            rectangulo.setFillColor(0xB4, 0, 0);
        });

        List<LineaSms> lineas = ApcSmsExcel.leer(excel, apc).lineas();

        assertEquals("273C18", lineas.get(0).colorMuestra());
        assertEquals("231003", lineas.get(1).colorMuestra());
        assertEquals("843C0B", lineas.get(2).colorMuestra());
        assertEquals("", lineas.get(3).colorMuestra());
    }

    @Test
    void unExcelSinLasColumnasNoEsElDeSms() throws IOException {
        byte[] excel;
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("Hoja1").createRow(0).createCell(0).setCellValue("ARTICLE");
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            excel = salida.toByteArray();
        }

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ApcSmsExcel.leer(excel, apc));
        assertTrue(error.getMessage().contains("«Commande SMS»"), error.getMessage());
    }

    // --- Un libro de SMS hecho a mano: CROQUIS, CODIGO, COLORIS A.P.C., ARTICLE, Commande SMS ---

    private interface Relleno {
        void rellenar(XSSFSheet hoja) throws IOException;
    }

    private static byte[] libro(Relleno relleno) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet("FEMME");
            fila(hoja, 0, "CODIGO", "COLORIS \nA.P.C.", "ARTICLE", "Commande SMS");
            hoja.getRow(0).createCell(0).setCellValue("CROQUIS");
            relleno.rellenar(hoja);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static void fila(XSSFSheet hoja, int numero, String... valores) {
        Row fila = hoja.createRow(numero);
        for (int i = 0; i < valores.length; i++) {
            fila.createCell(i + 1).setCellValue(valores[i]);
        }
    }

    /** Una elipse de 20 x 20 puntos dentro de la celda, como las del real. */
    private static XSSFSimpleShape redonda(XSSFDrawing dibujo, int fila, int columna) {
        XSSFSimpleShape elipse = dibujo.createSimpleShape(new XSSFClientAnchor(Units.EMU_PER_POINT * 10,
                Units.EMU_PER_POINT * 5, Units.EMU_PER_POINT * 30, Units.EMU_PER_POINT * 25,
                columna, fila, columna, fila));
        elipse.setShapeType(ShapeTypes.ELLIPSE);
        return elipse;
    }

    private static void imagenEn(XSSFDrawing dibujo, int indice, int fila, int columna) {
        XSSFClientAnchor ancla = new XSSFClientAnchor(0, 0, 0, 0, columna, fila, columna + 1, fila + 1);
        dibujo.createPicture(ancla, indice);
    }
}
