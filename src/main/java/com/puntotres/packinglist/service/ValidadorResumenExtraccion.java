package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.model.EnvioInput;

/**
 * Contrasta lo extraído de las hojas con los recuentos que las propias hojas
 * declaran ({@code resumenPalets}): que cada destinación mencionada en el
 * resumen tenga hojas de packing, y que el número de palets extraído coincida
 * con el declarado.
 *
 * Es la única validación del proyecto que BLOQUEA (no se pasa a revisión):
 * un descuadre aquí significa casi siempre que falta una hoja en el escaneo
 * —el caso real que la motiva es un resumen con "wh. 5 palet" en un documento
 * sin las hojas de WHOLESALE—, y eso no se arregla editando la revisión, se
 * arregla volviendo a escanear. Sin resumen no hay nada que contrastar y todo
 * pasa de largo, así que el JSON pegado a mano no se ve afectado.
 *
 * Trabaja sobre {@link EnvioInput} porque corre ANTES de importar; las
 * destinaciones se comparan resueltas a su destino padre del catálogo
 * ({@link ClienteConfig#destinoPadrePara}) para que "wh. 5 palet" case con
 * unas hojas de AUSTRALIA y CHINE FRANCH.
 */
@Service
public class ValidadorResumenExtraccion {

    /** Errores = no pasar a revisión. Avisos = siguen el cauce normal. */
    public record ResultadoResumen(List<String> errores, List<String> avisos) {
    }

    public ResultadoResumen validar(EnvioInput envio, ClienteConfig cliente) {
        List<String> errores = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        if (envio.getResumenPalets() == null || envio.getResumenPalets().isEmpty()) {
            return new ResultadoResumen(errores, avisos);
        }

        // Palets extraídos por destinación, con el nombre resuelto al padre.
        Map<String, Integer> paletsPorDestino = new LinkedHashMap<>();
        if (envio.getDestinos() != null) {
            for (EnvioInput.DestinoInput destino : envio.getDestinos()) {
                int palets = destino.getPalets() == null ? 0 : destino.getPalets().size();
                paletsPorDestino.merge(resolver(destino.getDestino(), cliente), palets,
                        Integer::sum);
            }
        }

        for (EnvioInput.ResumenPaletsInput declarado : envio.getResumenPalets()) {
            String nombre = resolver(declarado.getDestino(), cliente);
            Integer extraidos = paletsPorDestino.get(nombre);
            if (extraidos == null) {
                errores.add("El resumen de palets menciona '" + declarado.getDestino()
                        + "' pero el documento no trae ninguna hoja de packing de esa "
                        + "destinación: ¿falta una hoja en el escaneo?");
                continue;
            }
            if (declarado.getPalets() == null) {
                continue;
            }
            if (extraidos == 0) {
                // Recuento sin reparto: es el caso esperado cuando el operario
                // no apuntó qué cajas van en cada palet. Se completa en la
                // revisión, no bloquea.
                avisos.add("La destinación " + nombre + " declara "
                        + declarado.getPalets() + " palet(s) pero no trae el reparto de "
                        + "cajas: completa los palets en la tabla de revisión");
            } else if (!declarado.getPalets().equals(extraidos)) {
                errores.add("La destinación " + nombre + " declara "
                        + declarado.getPalets() + " palet(s) pero se han leído "
                        + extraidos + ": revisa el documento antes de continuar");
            }
        }
        return new ResultadoResumen(errores, avisos);
    }

    /**
     * Nombre comparable de una destinación: su destino padre del catálogo si
     * el cliente lo tiene, y si no (o sin catálogo) el nombre en mayúsculas.
     */
    private static String resolver(String destino, ClienteConfig cliente) {
        String nombre = destino == null ? "" : destino.trim().toUpperCase(Locale.ROOT);
        if (cliente == null) {
            return nombre;
        }
        return cliente.destinoPadrePara(nombre)
                .map(ClienteConfig.DestinoResuelto::nombrePadre)
                .orElse(nombre);
    }
}
