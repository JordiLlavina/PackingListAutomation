package com.puntotres.packinglist.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.TaraProperties;
import com.puntotres.packinglist.model.CajaData;

/**
 * Completa los pesos que faltan en las cajas a partir de las cajas con
 * algún peso conocido de la misma referencia y de la tabla de taras por
 * tamaño de caja ({@link TaraProperties}, ampliable desde application.yml).
 *
 * Regla: el peso neto por unidad es el mismo en toda la referencia (mismo
 * producto). Se calcula promediando las cajas con peso conocido, prefiriendo
 * el neto (neto / cantidad, directo) y usando el bruto como alternativa
 * ((bruto - tara) / cantidad). Un solo peso introducido a mano — neto o
 * bruto — desbloquea el resto de su referencia.
 *
 * Lo que no se puede inferir se queda a null (nunca se inventa un número),
 * pero ya no en silencio: los tamaños de caja sin tara configurada se
 * devuelven como avisos en {@link ResultadoInferencia} para que la pantalla
 * de revisión los muestre.
 */
@Service
public class WeightInferenceService {

    private final TaraProperties taras;

    public WeightInferenceService(TaraProperties taras) {
        this.taras = taras;
    }

    /** Agrupa por referencia y aplica {@link #inferirPesos} a cada grupo. */
    public ResultadoInferencia inferirPesosPorReferencia(List<CajaData> cajas) {
        Map<String, List<CajaData>> porReferencia = new LinkedHashMap<>();
        for (CajaData caja : cajas) {
            porReferencia.computeIfAbsent(caja.getReferencia(), r -> new java.util.ArrayList<>())
                    .add(caja);
        }
        // Un mismo tamaño sin tara puede aparecer en varias referencias:
        // el aviso se emite una sola vez.
        Set<String> avisos = new LinkedHashSet<>();
        for (List<CajaData> grupo : porReferencia.values()) {
            avisos.addAll(inferirPesos(grupo).getAvisos());
        }
        ResultadoInferencia resultado = new ResultadoInferencia();
        resultado.getAvisos().addAll(avisos);
        return resultado;
    }

    /**
     * Infiere los pesos que falten en una lista de cajas de la MISMA
     * referencia (mismo producto, luego mismo peso por unidad).
     */
    public ResultadoInferencia inferirPesos(List<CajaData> cajasMismaReferencia) {
        Double pesoUnitario = calcularPesoUnitarioMedio(cajasMismaReferencia);
        Set<String> tamanosSinTara = new LinkedHashSet<>();

        for (CajaData caja : cajasMismaReferencia) {
            Optional<Double> tara = taras.taraPara(caja.getTamanoCaja());

            if (caja.getPesoBrutoKg() != null) {
                // Bruto conocido: solo completar el neto si falta.
                if (caja.getPesoNetoKg() == null) {
                    if (tara.isPresent()) {
                        caja.setPesoNetoKg(redondear2(caja.getPesoBrutoKg() - tara.get()));
                    } else {
                        tamanosSinTara.add(nombreTamano(caja));
                    }
                }
                continue;
            }

            if (caja.getPesoNetoKg() != null) {
                // Neto conocido (p. ej. introducido a mano): completar el bruto.
                if (tara.isPresent()) {
                    caja.setPesoBrutoKg(redondear2(caja.getPesoNetoKg() + tara.get()));
                } else {
                    tamanosSinTara.add(nombreTamano(caja));
                }
                continue;
            }

            // Sin ningún peso: solo se puede inferir con peso unitario y tara.
            if (pesoUnitario != null) {
                if (tara.isPresent()) {
                    double neto = redondear2(caja.getCantidad() * pesoUnitario);
                    caja.setPesoNetoKg(neto);
                    caja.setPesoBrutoKg(redondear2(neto + tara.get()));
                } else {
                    tamanosSinTara.add(nombreTamano(caja));
                }
            }
            // Sin peso unitario la caja queda pendiente, pero eso ya se ve
            // en la tabla de revisión: no es un aviso de configuración.
        }

        ResultadoInferencia resultado = new ResultadoInferencia();
        for (String tamano : tamanosSinTara) {
            resultado.getAvisos().add("Sin tara configurada para el tamaño de caja '" + tamano
                    + "': añádela en application.yml para poder inferir sus pesos");
        }
        return resultado;
    }

    /**
     * Promedio del peso neto por unidad sobre las cajas con algún peso
     * conocido y cantidad > 0. Se prefiere el neto (directo); si solo hay
     * bruto se usa (bruto - tara) / cantidad. Null si no hay ninguna.
     */
    private Double calcularPesoUnitarioMedio(List<CajaData> cajas) {
        double suma = 0;
        int conocidas = 0;
        for (CajaData caja : cajas) {
            if (caja.getCantidad() <= 0) {
                continue;
            }
            if (caja.getPesoNetoKg() != null) {
                suma += caja.getPesoNetoKg() / caja.getCantidad();
                conocidas++;
                continue;
            }
            if (caja.getPesoBrutoKg() == null) {
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

    private static String nombreTamano(CajaData caja) {
        return caja.getTamanoCaja() == null ? "(sin tamaño)" : caja.getTamanoCaja();
    }

    private static double redondear2(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
