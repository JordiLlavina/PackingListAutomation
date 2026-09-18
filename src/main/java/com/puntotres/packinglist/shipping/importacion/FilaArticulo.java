package com.puntotres.packinglist.shipping.importacion;

import java.math.BigDecimal;
import java.util.List;

/** Una fila del CSV de artículos, ya interpretada. */
public record FilaArticulo(int numero, String referencia, String descripcion, String codigoHs, String paisOrigen,
                           Double pesoNeto, Double pesoBruto, BigDecimal valorUnitario, List<String> errores, boolean yaExiste) {
    public boolean valida() { return errores.isEmpty(); }
}
