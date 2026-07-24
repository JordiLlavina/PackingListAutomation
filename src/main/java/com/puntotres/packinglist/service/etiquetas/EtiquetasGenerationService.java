package com.puntotres.packinglist.service.etiquetas;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Punto de entrada de las etiquetas de caja: localiza el generador del
 * cliente por su clave de catálogo. Un cliente sin generador simplemente
 * no tiene etiquetas implementadas todavía (la web muestra "en
 * desarrollo", igual que el resto de funcionalidades pendientes).
 */
@Service
public class EtiquetasGenerationService {

    private final Map<String, GeneradorEtiquetasCliente> porCliente = new HashMap<>();

    public EtiquetasGenerationService(List<GeneradorEtiquetasCliente> generadores) {
        for (GeneradorEtiquetasCliente generador : generadores) {
            porCliente.put(normalizar(generador.claveCliente()), generador);
        }
    }

    public Optional<GeneradorEtiquetasCliente> generadorPara(String claveCliente) {
        if (claveCliente == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(porCliente.get(normalizar(claveCliente)));
    }

    private static String normalizar(String clave) {
        return clave.trim().toUpperCase(Locale.ROOT);
    }
}
