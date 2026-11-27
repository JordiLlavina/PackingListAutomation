package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;

/**
 * Las imágenes de un excel de SMS de un cliente sin lector propio, por hoja y
 * por fila: la de su esquina de arriba, en cualquier columna. Es lo que se
 * cruza con la fila que dice Claude de cada línea, porque aquí no se sabe en
 * qué columna pone el cliente el croquis.
 *
 * Mismo tratamiento que los croquis de APC ({@link ApcSmsExcel}): con el
 * recorte de Excel aplicado, reducidas y en JPEG; la misma imagen pegada en
 * varias filas es el mismo objeto {@link Imagen}, que el Excel de órdenes
 * guarda una vez.
 */
public final class ImagenesPorFila {

    private ImagenesPorFila() {
    }

    /**
     * Hoja → fila de Excel (desde 1) → sus imágenes, sin repetir. Un fichero
     * que no es un xlsx (un PDF) no tiene ninguna. Lo que no se puede leer
     * (un dibujo EMF) se queda fuera con aviso.
     */
    public static Map<String, Map<Integer, List<Imagen>>> de(byte[] fichero, List<String> avisos) {
        Map<String, Map<Integer, List<Imagen>>> porHoja = new LinkedHashMap<>();
        if (!esXlsx(fichero)) {
            return porHoja;
        }
        Path temporal = null;
        try {
            // Desde un fichero, como el lector de APC: desde memoria POI rechaza
            // algunos dibujos incrustados por su protección contra «zip bombs».
            temporal = Files.createTempFile("excel-sms-ia-", ".xlsx");
            Files.write(temporal, fichero);
            try (Workbook libro = WorkbookFactory.create(temporal.toFile(), null, true)) {
                Map<String, Optional<Imagen>> leidas = new HashMap<>();
                for (Sheet hoja : libro) {
                    Map<Integer, List<Imagen>> porFila = deLaHoja(hoja, leidas, avisos);
                    if (!porFila.isEmpty()) {
                        porHoja.put(hoja.getSheetName(), porFila);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            avisos.add("No se han podido leer las imágenes del excel (" + e.getMessage()
                    + "): las hojas salen con la foto del catálogo o sin imagen");
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignorada) {
                    // Un temporal que no se borra no es un error del usuario.
                }
            }
        }
        return porHoja;
    }

    private static Map<Integer, List<Imagen>> deLaHoja(Sheet hoja, Map<String, Optional<Imagen>> leidas,
                                                       List<String> avisos) {
        Map<Integer, List<Imagen>> porFila = new HashMap<>();
        if (!(hoja instanceof XSSFSheet xssf) || xssf.getDrawingPatriarch() == null) {
            return porFila;
        }
        for (XSSFShape forma : xssf.getDrawingPatriarch().getShapes()) {
            if (!(forma instanceof XSSFPicture imagen)
                    || !(imagen.getAnchor() instanceof XSSFClientAnchor ancla)) {
                continue;
            }
            int fila = ancla.getRow1() + 1;
            ApcSmsExcel.Croquis croquis = new ApcSmsExcel.Croquis(imagen.getPictureData().getData(),
                    ApcSmsExcel.recorte(imagen));
            String donde = hoja.getSheetName() + ", fila " + fila;
            ApcSmsExcel.imagen(croquis, leidas, donde, avisos).ifPresent(leida -> {
                List<Imagen> deLaFila = porFila.computeIfAbsent(fila, f -> new ArrayList<>());
                if (!deLaFila.contains(leida)) {
                    deLaFila.add(leida);
                }
            });
        }
        return porFila;
    }

    /** Un xlsx es un zip: empieza por «PK». */
    static boolean esXlsx(byte[] fichero) {
        return fichero != null && fichero.length > 4 && fichero[0] == 'P' && fichero[1] == 'K';
    }
}
