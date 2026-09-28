package com.puntotres.packinglist.service.corte;

import java.util.Locale;

/**
 * Una referencia del pedido partida en lo que necesita el corte: el MODELO
 * del bolso, que es lo que nombra la carpeta de fotos, y la referencia de la
 * PIEL, que es lo que decide qué material se corta.
 *
 * Cada cliente pone la piel en un sitio: AMI detrás del punto
 * ("ULL712.AL0103" = modelo ULL712 + piel AL0103) y APC delante del guion
 * ("PXCBC-F67008" = piel PXCBC + modelo F67008). Una referencia sin
 * separador se queda entera como modelo y sin piel: no se adivina.
 */
public record ReferenciaCorte(String referencia, String modelo, String piel) {

    public static ReferenciaCorte deAmi(String articulo) {
        String referencia = normalizar(articulo);
        int punto = referencia.indexOf('.');
        if (punto <= 0 || punto == referencia.length() - 1) {
            return new ReferenciaCorte(referencia, referencia, "");
        }
        return new ReferenciaCorte(referencia, referencia.substring(0, punto),
                referencia.substring(punto + 1));
    }

    public static ReferenciaCorte deApc(String articulo) {
        String referencia = normalizar(articulo);
        int guion = referencia.indexOf('-');
        if (guion <= 0 || guion == referencia.length() - 1) {
            return new ReferenciaCorte(referencia, referencia, "");
        }
        return new ReferenciaCorte(referencia, referencia.substring(guion + 1),
                referencia.substring(0, guion));
    }

    public boolean tienePiel() {
        return !piel.isEmpty();
    }

    /**
     * Mayúsculas y sin espacios alrededor. La usan también la lectura del zip
     * y el servicio: una carpeta "ull712" es el modelo ULL712.
     */
    public static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT);
    }
}
