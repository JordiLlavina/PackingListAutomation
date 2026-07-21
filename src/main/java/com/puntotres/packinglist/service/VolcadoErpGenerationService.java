package com.puntotres.packinglist.service;

import org.springframework.stereotype.Service;
import com.puntotres.packinglist.model.*;
import java.util.*;

/**
 * Volcado de albarán para ICSuite: una línea por artículo con su cantidad
 * total en el envío (todas las destinaciones juntas).
 *
 * El nº de comanda no sale del JSON: viene de la pantalla de entrada en
 * {@link DatosEnvio} y se repite en todas las líneas. Si no se rellenó,
 * las líneas van sin comanda y el excel se genera igual.
 */
@Service
public class VolcadoErpGenerationService {

    /** Unidad por defecto: los cinturones la sustituyen por su talla. */
    private static final String UNIDAD_UNICA = "U";

    public VolcadoErpData generar(List<CajaData> cajas, DatosEnvio envio) {
        // Agrupar por referencia + talla + color (una línea por combinación única)
        Map<String, Grupo> grupos = new LinkedHashMap<>();

        for (CajaData caja : cajas) {
            String key = caja.getReferencia() + "|" + caja.getTalla() + "|" + caja.getCodigoColor();
            grupos.computeIfAbsent(key, k -> new Grupo(caja)).cantidad += caja.getCantidad();
        }

        // Construir las líneas finales, numeradas de 1 en 1 en el orden en
        // que aparecieron los grupos.
        List<VolcadoErpLinea> lineas = new ArrayList<>();
        int numeroLinea = 1;
        for (Grupo grupo : grupos.values()) {
            CajaData caja = grupo.caja;
            // Los cinturones se venden por talla (75-100) y esa es su unidad;
            // el resto de artículos son de talla única.
            String uni = caja.esCinturon() && caja.getTalla() != null
                    ? caja.getTalla()
                    : UNIDAD_UNICA;
            lineas.add(new VolcadoErpLinea(numeroLinea++, caja.getReferencia(),
                    envio.getNumeroComanda(), grupo.cantidad, uni,
                    caja.getCodigoColor(), caja.getTalla()));
        }

        // Misma sanitización que el nombre del ZIP en /descargar-todo
        String facturaSaneada = envio.getNumeroFactura().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String nombreFichero = "Volcado_ICSUITE_" + facturaSaneada + ".xlsx";
        return new VolcadoErpData(lineas, nombreFichero);
    }

    /** Cajas agrupadas: la primera caja aporta los datos, el resto solo cantidad. */
    private static class Grupo {
        private final CajaData caja;
        private int cantidad;

        Grupo(CajaData caja) {
            this.caja = caja;
        }
    }
}
