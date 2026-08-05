package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Ancla la maquetación de la hoja "CODIGOS BARRAS EXTRA" contra la plantilla
 * de referencia del cliente. No hay .xlsx de plantilla en resources: la
 * maquetación son constantes, y esto es lo que impide que se desvíen.
 */
class HojaCodigosBarrasExtraMaquetacionTest {

    private static final Path REFERENCIA = Path.of(
            "docs/Etiquetas cajas/AMI ETIQUETAS CAJA - CODE BARRAS EXTRA TEMPLATE.xlsx");

    /** Diferencia máxima admitida en el ancho de columna, en unidades POI. */
    private static final int TOLERANCIA_ANCHO = 100;

    /**
     * Diferencia máxima admitida en el anclaje de una imagen, en EMU (9525 =
     * 1 píxel). Las imágenes de la plantilla están colocadas a mano y no
     * caen las tres en la misma x, así que se comparan con holgura.
     */
    private static final int TOLERANCIA_ANCLAJE = 9525;

    private static XSSFSheet referencia(XSSFWorkbook libro) {
        return libro.getSheetAt(0);
    }

    private static XSSFSheet generada(XSSFWorkbook libro) {
        HojaCodigosBarrasExtra.escribir(libro, List.of(
                new FilaCodigoBarrasExtra("1 / 15", "CHINA",
                        new EtiquetaArticulo("ULL163.AL0052", "Size: U",
                                "221 DARK COFFEE", "Cde: 07703", "3666598354771"),
                        "366659835477100001000077030000000000000000ES")));
        return libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
    }

    @Test
    void losAnchosDeColumnaCoincidenConLaPlantillaDeReferencia() {
        try (XSSFWorkbook plantilla = new XSSFWorkbook(Files.newInputStream(REFERENCIA));
             XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet esperada = referencia(plantilla);
            XSSFSheet obtenida = generada(libro);
            for (int columna = 0; columna < 11; columna++) {
                assertEquals(esperada.getColumnWidth(columna),
                        obtenida.getColumnWidth(columna), TOLERANCIA_ANCHO,
                        "columna " + columna);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void elPrimerBloqueArrancaEnLaMismaFilaQueLaPlantillaDeReferencia() {
        // En la plantilla el rótulo "CAJA " está en A2 (1-based) = fila 1.
        try (XSSFWorkbook plantilla = new XSSFWorkbook(Files.newInputStream(REFERENCIA))) {
            assertEquals(RejillaEtiquetas.filaBase(0),
                    referencia(plantilla).getRow(1).getRowNum());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void losRotulosSonLosDeLaPlantillaSalvoElEspacioFinal() {
        try (XSSFWorkbook plantilla = new XSSFWorkbook(Files.newInputStream(REFERENCIA));
             XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet esperada = referencia(plantilla);
            XSSFSheet obtenida = generada(libro);
            int base = RejillaEtiquetas.filaBase(0);
            assertEquals(esperada.getRow(base).getCell(0).getStringCellValue().trim(),
                    obtenida.getRow(base).getCell(0).getStringCellValue());
            assertEquals(esperada.getRow(base + 2).getCell(0).getStringCellValue().trim(),
                    obtenida.getRow(base + 2).getCell(0).getStringCellValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void elCode128SeAnclaDondeLoDejaLaPlantillaDeReferencia() {
        // Solo la x: el Code 128 sobresalía por la derecha de la etiqueta y
        // en la plantilla se corrigió moviéndolo, sin tocar su tamaño.
        try (XSSFWorkbook plantilla = new XSSFWorkbook(Files.newInputStream(REFERENCIA));
             XSSFWorkbook libro = new XSSFWorkbook()) {
            List<XSSFClientAnchor> esperados = anclajesCode128(referencia(plantilla));
            assertEquals(3, esperados.size(), "imágenes de Code 128 en la plantilla");
            int obtenido = anclajesCode128(generada(libro)).get(0).getDx1();
            for (XSSFClientAnchor esperado : esperados) {
                assertEquals(esperado.getDx1(), obtenido, TOLERANCIA_ANCLAJE, "dx del Code 128");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Las imágenes ancladas en la columna del Code 128 de cada bloque. */
    private static List<XSSFClientAnchor> anclajesCode128(XSSFSheet hoja) {
        return hoja.getDrawingPatriarch().getShapes().stream()
                .filter(XSSFPicture.class::isInstance)
                .map(forma -> ((XSSFPicture) forma).getClientAnchor())
                .filter(ancla -> ancla.getCol1() == RejillaEtiquetas.COLUMNAS_IZQUIERDA[2])
                .toList();
    }

    @Test
    void laEscalaYLaOrientacionSonLasDeLaPlantilla() {
        // generada() crea la hoja "CODIGOS BARRAS EXTRA" en el libro que
        // recibe: llamarla dos veces sobre el mismo libro (una por
        // assertEquals) reventaría con nombre de hoja duplicado, así que se
        // llama una sola vez y se reutiliza la hoja para las dos comprobaciones.
        try (XSSFWorkbook plantilla = new XSSFWorkbook(Files.newInputStream(REFERENCIA));
             XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet obtenida = generada(libro);
            assertEquals(referencia(plantilla).getPrintSetup().getScale(),
                    obtenida.getPrintSetup().getScale());
            assertEquals(referencia(plantilla).getPrintSetup().getLandscape(),
                    obtenida.getPrintSetup().getLandscape());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
