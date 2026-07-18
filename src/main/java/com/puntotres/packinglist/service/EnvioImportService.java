package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.model.PaletData;

/**
 * Traduce el JSON del envío (EnvioInput, tal como sale de las imágenes)
 * al modelo de dominio: expande los rangos de cajas a una CajaData por
 * caja física y monta los PaletData de cada destinación.
 *
 * Valida sin bloquear: las inconsistencias (cantidadTotal que no cuadra,
 * números de caja duplicados) se devuelven como avisos para la pantalla
 * de revisión, nunca se corrigen en silencio.
 */
@Service
public class EnvioImportService {

    public EnvioImportado importar(EnvioInput envio) {
        EnvioImportado resultado = new EnvioImportado();

        for (EnvioInput.DestinoInput destinoInput : envio.getDestinos()) {
            DestinoData destino = new DestinoData();
            destino.setNombreDestino(destinoInput.getDestino());
            destino.setCajas(new ArrayList<>());

            for (EnvioInput.ReferenciaInput referencia : destinoInput.getReferencias()) {
                destino.getCajas().addAll(expandirCajas(destinoInput, referencia, resultado));
            }
            destino.getCajas().sort(Comparator.comparingInt(CajaData::getNumeroCaja));
            detectarDuplicados(destino, resultado);

            resultado.getDestinos().add(new EnvioImportado.DestinoImportado(
                    destino, mapearPalets(destinoInput)));
        }
        return resultado;
    }

    /** Expande las entradas de caja (sueltas o rangos) de una referencia. */
    private List<CajaData> expandirCajas(EnvioInput.DestinoInput destinoInput,
                                         EnvioInput.ReferenciaInput referencia,
                                         EnvioImportado resultado) {
        List<CajaData> cajas = new ArrayList<>();
        for (EnvioInput.CajaRangoInput entrada : referencia.getCajas()) {
            if (entrada.esRango()) {
                for (int numero = entrada.getCajaInicio(); numero <= entrada.getCajaFin(); numero++) {
                    cajas.add(crearCaja(referencia, numero, entrada.getUnidadesPorCaja()));
                }
            } else {
                cajas.add(crearCaja(referencia, entrada.getCaja(), entrada.getUnidades()));
            }
        }

        if (referencia.getCantidadTotal() != null) {
            int suma = cajas.stream().mapToInt(CajaData::getCantidad).sum();
            if (suma != referencia.getCantidadTotal()) {
                resultado.getAvisos().add(String.format(
                        "%s / %s %s: la suma de unidades de las cajas (%d) no cuadra con cantidadTotal (%d)",
                        destinoInput.getDestino(), referencia.getReferencia(), referencia.getColor(),
                        suma, referencia.getCantidadTotal()));
            }
        }
        return cajas;
    }

    private CajaData crearCaja(EnvioInput.ReferenciaInput referencia, int numero, Integer unidades) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(numero);
        caja.setNumeroPedido(referencia.getPedido());
        caja.setReferencia(referencia.getReferencia());
        caja.setCodigoColor(referencia.getColor());
        caja.setTamanoCaja(referencia.getMedidaCaja());
        caja.setCantidad(unidades != null ? unidades : 0);
        // Los pesos no vienen en el JSON: quedan a null hasta que los
        // complete el WeightInferenceService o la revisión manual.
        return caja;
    }

    private List<PaletData> mapearPalets(EnvioInput.DestinoInput destinoInput) {
        List<PaletData> palets = new ArrayList<>();
        if (destinoInput.getPalets() == null) {
            return palets;
        }
        for (EnvioInput.PaletInput entrada : destinoInput.getPalets()) {
            PaletData palet = new PaletData();
            // En el JSON los palets van anidados dentro de su destinación.
            palet.setDestino(destinoInput.getDestino());
            palet.setNumeroPalet(entrada.getPalet());
            palet.setCajaInicio(entrada.getCajaInicio());
            palet.setCajaFin(entrada.getCajaFin());
            palets.add(palet);
        }
        return palets;
    }

    private void detectarDuplicados(DestinoData destino, EnvioImportado resultado) {
        Set<Integer> vistos = new HashSet<>();
        for (CajaData caja : destino.getCajas()) {
            if (!vistos.add(caja.getNumeroCaja())) {
                resultado.getAvisos().add(String.format(
                        "%s: el número de caja %d aparece más de una vez",
                        destino.getNombreDestino(), caja.getNumeroCaja()));
            }
        }
    }
}
