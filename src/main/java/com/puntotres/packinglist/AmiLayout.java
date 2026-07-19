package com.puntotres.packinglist;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describe dónde escribe cada dato la plantilla "STANDARD PKL" de AMI:
 * bolsos/carteras (talla única "U") y cinturones (matriz de tallas 70-110)
 * comparten estructura y cabecera, solo cambian plantilla, filas de
 * totales/resumen y columnas a partir de la talla.
 *
 * Todos los índices son 0-based de POI (fila/columna N de Excel = índice N-1).
 */
public record AmiLayout(
        String rutaPlantilla,
        String nombreHoja,
        int idxFilaModelo,
        int idxFilaTotales,
        int idxResumenTotalQty,
        int idxResumenNumCajas,
        int idxResumenPesoBruto,
        int idxResumenPesoNeto,
        int idxResumenVolumen,
        int colTemporada,
        int colPedido,
        int colReferencia,
        int colColor,
        int colNumCaja,
        int colSizeGrid,
        int colPrimeraTalla,
        int colUltimaTalla,
        int colQntyTotal,
        int colTamanoCaja,
        int colPesoNeto,
        int colPesoBruto,
        /** talla -> columna; vacío en bolsos (talla única, sin matriz). */
        Map<String, Integer> columnaPorTalla) {

    public int ultimaColumna() {
        return colPesoBruto;
    }

    /** Bolsos y carteras (ULL/USL): grid de talla única "U" en la columna G. */
    public static final AmiLayout BAGS = new AmiLayout(
            "/client-packinglist/ami-bags-packing-list-template.xlsx",
            "STANDARD PKL H26",
            19, 20,
            23, 24, 25, 26, 27,
            0, 1, 2, 3, 4, 5, 6, 17, 18, 19, 20, 21,
            Map.of());

    /** Cinturones (UBL): matriz de tallas 70-110 en columnas G-O. */
    public static final AmiLayout BELTS = new AmiLayout(
            "/client-packinglist/ami-belts-packing-list-template.xlsx",
            "STANDARD PKL E25",
            18, 19,
            22, 23, 24, 25, 26,
            0, 1, 2, 3, 4, 5, 6, 16, 17, 18, 19, 20,
            tallasBelts());

    private static Map<String, Integer> tallasBelts() {
        Map<String, Integer> tallas = new LinkedHashMap<>();
        int col = 6; // G
        for (int talla = 70; talla <= 110; talla += 5) {
            tallas.put(String.valueOf(talla), col++);
        }
        return tallas;
    }
}
