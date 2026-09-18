package com.puntotres.packinglist.shipping.importacion;

import java.util.List;

import com.puntotres.packinglist.shipping.service.DatosContacto;

/** Una fila del CSV ya interpretada. numero es la fila de datos (1 = primera tras la cabecera). */
public record FilaLibreta(int numero, DatosContacto datos, String cuentaRemitente, String cuentaTransporte, String cuentaAranceles,
                          List<String> errores, String duplicadoDe) {
    public boolean valida() { return errores.isEmpty(); }
    public boolean duplicada() { return duplicadoDe != null; }
    public boolean lista() { return valida() && !duplicada(); }
}
