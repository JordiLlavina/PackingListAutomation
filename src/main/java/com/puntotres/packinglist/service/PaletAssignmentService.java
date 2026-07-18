package com.puntotres.packinglist.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

/**
 * Cruza las cajas de una destinación con la distribución de palets del
 * envío: a cada caja le asigna el palet en cuyo rango de números cae.
 */
@Service
public class PaletAssignmentService {

    /**
     * Asigna numeroPalet a cada caja del destino. La lista de palets es la
     * del envío completo (todas las destinaciones); aquí se filtran los de
     * esta destinación.
     *
     * Una caja cuyo número no cae en ningún rango se queda con
     * numeroPalet = null y se devuelve en cajasSinPalet: la pantalla de
     * revisión debe mostrarla, nunca se falla en silencio.
     */
    public ResultadoAsignacion asignar(DestinoData destino, List<PaletData> palets) {
        ResultadoAsignacion resultado = new ResultadoAsignacion();

        List<PaletData> paletsDelDestino = palets.stream()
                .filter(p -> p.getDestino() != null
                        && p.getDestino().equalsIgnoreCase(destino.getNombreDestino()))
                .toList();

        if (paletsDelDestino.isEmpty()) {
            resultado.getAvisos().add(
                    "No hay palets en la distribución para la destinación '"
                            + destino.getNombreDestino() + "'");
        }

        for (CajaData caja : destino.getCajas()) {
            List<PaletData> candidatos = paletsDelDestino.stream()
                    .filter(p -> p.contiene(caja.getNumeroCaja()))
                    .toList();

            if (candidatos.isEmpty()) {
                caja.setNumeroPalet(null);
                resultado.getCajasSinPalet().add(caja);
            } else {
                caja.setNumeroPalet(candidatos.get(0).getNumeroPalet());
                if (candidatos.size() > 1) {
                    resultado.getAvisos().add(
                            "La caja " + caja.getNumeroCaja() + " de '" + destino.getNombreDestino()
                                    + "' cae en varios palets solapados; se asigna el palet "
                                    + candidatos.get(0).getNumeroPalet());
                }
            }
        }

        return resultado;
    }
}
