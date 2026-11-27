package com.puntotres.packinglist.service.corte;

import java.util.List;

/**
 * Lo que Claude ha leído de un pedido de SMS de un cliente sin lector propio
 * ({@link LectorSmsIa}). Sigue al esquema de respuesta: un campo que no esté
 * en él llega a null. Los textos que el documento no trae son la cadena
 * vacía; los números y los sí/no que no se saben, null.
 *
 * {@code esDelCliente}: si el documento parece del cliente elegido (false =
 * de otro, que dice {@code clienteDetectado}); null si no hay nada que lo
 * diga. {@code esPedidoDeSms}: false si lo subido es otra cosa (un packing,
 * una factura).
 */
public record LecturaSmsIa(String clienteDetectado, Boolean esDelCliente, String motivoCliente,
                           Boolean esPedidoDeSms, String motivoSms, List<Linea> lineas, List<String> avisos) {

    public LecturaSmsIa {
        clienteDetectado = limpio(clienteDetectado);
        motivoCliente = limpio(motivoCliente);
        motivoSms = limpio(motivoSms);
        lineas = lineas == null ? List.of() : List.copyOf(lineas);
        avisos = avisos == null ? List.of() : avisos.stream().map(LecturaSmsIa::limpio)
                .filter(aviso -> !aviso.isEmpty()).toList();
    }

    /**
     * Una referencia en un color con sus muestras. {@code hoja}: la del excel
     * o «Página N» del PDF; {@code fila}: la de Excel (B7 → 7), null en un PDF.
     */
    public record Linea(String hoja, Integer fila, String referencia, String modelo, String nombreModelo,
                        String codigoColor, String descripcionColor, String piel, String colorPiel,
                        String forro, String colorForro, List<Combinacion> combinaciones, Integer muestras,
                        String comentario) {

        public Linea {
            hoja = limpio(hoja);
            // Como en el excel de APC: el código llega con espacios alrededor del guion.
            referencia = limpio(referencia).replaceAll("\\s+", "");
            modelo = limpio(modelo).replaceAll("\\s+", "");
            nombreModelo = limpio(nombreModelo);
            codigoColor = limpio(codigoColor);
            descripcionColor = limpio(descripcionColor);
            piel = limpio(piel);
            colorPiel = limpio(colorPiel);
            forro = limpio(forro);
            colorForro = limpio(colorForro);
            combinaciones = combinaciones == null ? List.of() : combinaciones.stream()
                    .filter(c -> c != null && !c.nombre().isEmpty()).toList();
            comentario = limpio(comentario);
        }
    }

    /** Otra piel del bolso, con su color. */
    public record Combinacion(String nombre, String color) {

        public Combinacion {
            nombre = limpio(nombre);
            color = limpio(color);
        }
    }

    static String limpio(String texto) {
        return texto == null ? "" : texto.replace(' ', ' ').trim();
    }
}
