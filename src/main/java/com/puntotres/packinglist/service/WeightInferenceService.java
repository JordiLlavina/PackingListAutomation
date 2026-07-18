package com.puntotres.packinglist.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.TaraProperties;
import com.puntotres.packinglist.model.CajaData;

/**
 * Completa los pesos que faltan en las cajas a partir de las cajas con peso
 * bruto conocido de la misma referencia y de la tabla de taras por tamaño
 * de caja ({@link TaraProperties}, ampliable desde application.yml).
 *
 * Regla: peso neto por unidad = (pesoBruto - tara) / cantidad, promediado
 * sobre las cajas conocidas. Lo que no se puede inferir (tara desconocida,
 * ninguna caja con peso) se queda a null para que la pantalla de revisión
 * lo remarque como pendiente.
 */
@Service
public class WeightInferenceService {

    private final TaraProperties taras;

    public WeightInferenceService(TaraProperties taras) {
        this.taras = taras;
    }

    /** Agrupa por referencia y aplica {@link #inferirPesos} a cada grupo. */
    public void inferirPesosPorReferencia(List<CajaData> cajas) {
        Map<String, List<CajaData>> porReferencia = new LinkedHashMap<>();
        for (CajaData caja : cajas) {
            porReferencia.computeIfAbsent(caja.getReferencia(), r -> new java.util.ArrayList<>())
                    .add(caja);
        }
        porReferencia.values().forEach(this::inferirPesos);
    }

    /**
     * Infiere los pesos que falten en una lista de cajas de la MISMA
     * referencia (mismo producto, luego mismo peso por unidad).
     */
    public void inferirPesos(List<CajaData> cajasMismaReferencia) {
        Double pesoUnitario = calcularPesoUnitarioMedio(cajasMismaReferencia);

        for (CajaData caja : cajasMismaReferencia) {
            Optional<Double> tara = taras.taraPara(caja.getTamanoCaja());

            if (caja.getPesoBrutoKg() != null) {
                // Bruto conocido: solo completar el neto si falta y hay tara.
                if (caja.getPesoNetoKg() == null && tara.isPresent()) {
                    caja.setPesoNetoKg(redondear2(caja.getPesoBrutoKg() - tara.get()));
                }
                continue;
            }

            // Sin bruto: solo se puede inferir con peso unitario y tara conocidos.
            if (pesoUnitario != null && tara.isPresent()) {
                double neto = redondear2(caja.getCantidad() * pesoUnitario);
                caja.setPesoNetoKg(neto);
                caja.setPesoBrutoKg(redondear2(neto + tara.get()));
            }
            // Si no, la caja queda con pesos null: pendiente de revisión
            // (introducir el peso a mano o añadir la tara al application.yml).
        }
    }

    /**
     * Promedio de (bruto - tara) / cantidad sobre las cajas con peso bruto
     * conocido, tara disponible y cantidad > 0. Null si no hay ninguna.
     */
    private Double calcularPesoUnitarioMedio(List<CajaData> cajas) {
        double suma = 0;
        int conocidas = 0;
        for (CajaData caja : cajas) {
            if (caja.getPesoBrutoKg() == null || caja.getCantidad() <= 0) {
                continue;
            }
            Optional<Double> tara = taras.taraPara(caja.getTamanoCaja());
            if (tara.isEmpty()) {
                continue;
            }
            suma += (caja.getPesoBrutoKg() - tara.get()) / caja.getCantidad();
            conocidas++;
        }
        return (conocidas > 0) ? suma / conocidas : null;
    }

    private static double redondear2(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
