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
     * Hasta aquí una destinación viaja SIN palet: las cajas se mandan
     * sueltas, así que la hoja de palets no existe y no falta ningún dato.
     * Por encima de esta cuenta, una destinación sin palets sí es un dato
     * que falta y se avisa.
     */
    public static final int MAX_CAJAS_SIN_PALET = 3;

    /**
     * Asigna numeroPalet a cada caja del destino. La lista de palets es la
     * del envío completo (todas las destinaciones); aquí se filtran los de
     * esta destinación.
     *
     * Una caja cuyo número no cae en ningún rango se queda con
     * numeroPalet = null y se devuelve en cajasSinPalet: la pantalla de
     * revisión debe mostrarla, nunca se falla en silencio.
     *
     * Excepción: una destinación de pocas cajas y sin ningún palet declarado
     * no es un dato incompleto, es un envío suelto (ver
     * {@link #MAX_CAJAS_SIN_PALET}). Sus cajas se marcan con
     * {@link CajaData#SIN_PALET} y no sale ningún aviso: avisar de lo que
     * pasa en todos los envíos pequeños solo entierra los avisos que sí hay
     * que leer.
     */
    public ResultadoAsignacion asignar(DestinoData destino, List<PaletData> palets) {
        ResultadoAsignacion resultado = new ResultadoAsignacion();

        List<PaletData> paletsDelDestino = palets.stream()
                .filter(p -> p.getDestino() != null
                        && p.getDestino().equalsIgnoreCase(destino.getNombreDestino()))
                .toList();

        if (paletsDelDestino.isEmpty()) {
            if (vaSuelta(destino)) {
                destino.getCajas().forEach(caja -> caja.setNumeroPalet(CajaData.SIN_PALET));
                return resultado;
            }
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

    /**
     * Se cuentan las cajas FÍSICAS (números de caja distintos), no las líneas:
     * una sola caja con cuatro referencias dentro sigue siendo un bulto que se
     * manda suelto, y contando líneas pediría palets que nadie va a poner.
     */
    private static boolean vaSuelta(DestinoData destino) {
        long cajasFisicas = destino.getCajas().stream()
                .map(CajaData::getNumeroCaja)
                .distinct()
                .count();
        return cajasFisicas > 0 && cajasFisicas <= MAX_CAJAS_SIN_PALET;
    }
}
