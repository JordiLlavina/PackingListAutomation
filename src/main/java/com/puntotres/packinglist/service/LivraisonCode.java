package com.puntotres.packinglist.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import com.puntotres.packinglist.config.DestinoClienteConfig;

/**
 * Livraison code de APC: {@code "PUN" + fecha de envío (yyyyMMdd) +
 * abreviatura del destino padre + contador}, p. ej. {@code PUN20260717WH1}.
 * El formato está tomado de la etiqueta de caja real del cliente.
 *
 * El contador arranca siempre en 1 porque la aplicación NO puede saber el
 * correcto: no hay historial de envíos. Por eso el código se muestra entero
 * y editable en la pantalla de revisión.
 *
 * Nada de aquí lanza: una fecha ilegible o una abreviatura sin configurar
 * dan un código degradado y un aviso, nunca un envío bloqueado.
 */
public final class LivraisonCode {

    private static final String PREFIJO = "PUN";
    private static final String CONTADOR_INICIAL = "1";
    private static final DateTimeFormatter FORMATO_ENTRADA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_CODIGO = DateTimeFormatter.ofPattern("yyyyMMdd");

    private LivraisonCode() {
    }

    public static String generar(String nombrePadre, DestinoClienteConfig config,
                                 String fechaEnvio, List<String> avisos) {
        return PREFIJO + fecha(fechaEnvio, avisos) + abreviatura(nombrePadre, config, avisos)
                + CONTADOR_INICIAL;
    }

    private static String fecha(String fechaEnvio, List<String> avisos) {
        if (fechaEnvio == null || fechaEnvio.isBlank()) {
            avisos.add("Sin fecha de envío: el Livraison code sale sin fecha, corrígelo a mano");
            return "";
        }
        try {
            return LocalDate.parse(fechaEnvio.trim(), FORMATO_ENTRADA).format(FORMATO_CODIGO);
        } catch (DateTimeParseException e) {
            avisos.add("La fecha de envío '" + fechaEnvio + "' no es dd/MM/yyyy: "
                    + "el Livraison code sale sin fecha, corrígelo a mano");
            return "";
        }
    }

    /**
     * Sin abreviatura configurada se cae al nombre del destino en mayúsculas
     * y sin espacios, igual que hace AmiNombreFichero con una destinación
     * desconocida: un destino mal configurado no debe impedir generar.
     */
    private static String abreviatura(String nombrePadre, DestinoClienteConfig config,
                                      List<String> avisos) {
        if (config != null && config.getAbreviatura() != null && !config.getAbreviatura().isBlank()) {
            return config.getAbreviatura().trim();
        }
        avisos.add("El destino '" + nombrePadre + "' no tiene abreviatura configurada: "
                + "el Livraison code usa su nombre, revísalo");
        return nombrePadre == null ? ""
                : nombrePadre.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
