package com.puntotres.packinglist.service.etiquetasarticulo;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Punto de entrada de las etiquetas de artículo: localiza el generador del
 * cliente por su clave de catálogo. Un cliente sin generador simplemente no
 * tiene esta funcionalidad todavía y la pantalla lo marca "en desarrollo".
 *
 * Calco de EtiquetasGenerationService, que hace lo mismo para las etiquetas
 * de caja.
 */
@Service
public class EtiquetasArticuloGenerationService {

    private final Map<String, GeneradorEtiquetasArticuloCliente> porCliente = new HashMap<>();

    public EtiquetasArticuloGenerationService(
            List<GeneradorEtiquetasArticuloCliente> generadores) {
        for (GeneradorEtiquetasArticuloCliente generador : generadores) {
            porCliente.put(normalizar(generador.claveCliente()), generador);
        }
    }

    public Optional<GeneradorEtiquetasArticuloCliente> generadorPara(String claveCliente) {
        if (claveCliente == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(porCliente.get(normalizar(claveCliente)));
    }

    private static String normalizar(String clave) {
        return clave.trim().toUpperCase(Locale.ROOT);
    }
}
