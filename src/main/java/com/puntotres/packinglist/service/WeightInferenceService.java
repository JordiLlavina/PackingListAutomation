package com.puntotres.packinglist.service;

import java.util.ArrayList;
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
        // Un mismo tamaño sin tara puede aparecer en varias referencias:
        // el aviso se emite una sola vez.
        Set<String> avisos = new LinkedHashSet<>();
        for (List<CajaData> grupo : agruparPorReferencia(cajas).values()) {
            avisos.addAll(inferirPesos(grupo).getAvisos());
        }
        ResultadoInferencia resultado = new ResultadoInferencia();
        resultado.getAvisos().addAll(avisos);
        return resultado;
    }

    /**
     * Infiere los pesos de un envío completo: una lista de cajas por
     * destinación. El peso neto por unidad se promedia por referencia sobre
     * TODAS las destinaciones (mismo producto = mismo peso por unidad, así un
     * peso tecleado en una caja informa al mismo modelo en cualquier palet o
     * destinación), pero la identidad de la CAJA FÍSICA (qué líneas comparten
     * un único peso) es POR DESTINACIÓN: los números de caja se reinician en
     * cada una y no se pueden mezclar (la caja 5 de CHINA y la 5 de JAPAN son
     * cajas distintas).
     */
    public ResultadoInferencia inferirPesosDelEnvio(List<List<CajaData>> cajasPorDestino) {
        Map<String, Double> unitarioPorReferencia = pesoUnitarioPorReferencia(cajasPorDestino);
        Set<String> avisos = new LinkedHashSet<>();
        for (List<CajaData> cajasDestino : cajasPorDestino) {
            for (Map.Entry<String, List<CajaData>> grupo
                    : agruparPorReferencia(cajasDestino).entrySet()) {
                avisos.addAll(inferirPesos(grupo.getValue(),
                        unitarioPorReferencia.get(grupo.getKey())).getAvisos());
            }
        }
        ResultadoInferencia resultado = new ResultadoInferencia();
        resultado.getAvisos().addAll(avisos);
        return resultado;
    }

    /** Agrupa una lista de cajas por su referencia, conservando el orden. */
    private static Map<String, List<CajaData>> agruparPorReferencia(List<CajaData> cajas) {
        Map<String, List<CajaData>> porReferencia = new LinkedHashMap<>();
        for (CajaData caja : cajas) {
            porReferencia.computeIfAbsent(caja.getReferencia(), r -> new ArrayList<>()).add(caja);
        }
        return porReferencia;
    }

    /**
     * Peso neto medio por unidad de cada referencia, calculado sobre TODAS las
     * destinaciones a la vez. Las cajas físicas se agrupan DENTRO de cada
     * destinación (los nº de caja se repiten entre ellas) y cada una con algún
     * peso conocido aporta su (peso / unidades) al promedio de su referencia.
     * Una referencia sin ninguna caja conocida en todo el envío no aparece en
     * el mapa (no se puede inferir su unitario).
     */
    private Map<String, Double> pesoUnitarioPorReferencia(List<List<CajaData>> cajasPorDestino) {
        Map<String, double[]> acumulado = new LinkedHashMap<>(); // [suma, nº conocidas]
        for (List<CajaData> cajasDestino : cajasPorDestino) {
            for (Map.Entry<String, List<CajaData>> grupo
                    : agruparPorReferencia(cajasDestino).entrySet()) {
                for (List<CajaData> lineas : agruparEnCajasFisicas(grupo.getValue())) {
                    Double contribucion = contribucionUnitaria(lineas);
                    if (contribucion == null) {
                        continue;
                    }
                    double[] acc = acumulado.computeIfAbsent(grupo.getKey(), r -> new double[2]);
                    acc[0] += contribucion;
                    acc[1]++;
                }
            }
        }
        Map<String, Double> unitario = new LinkedHashMap<>();
        acumulado.forEach((referencia, acc) -> unitario.put(referencia, acc[0] / acc[1]));
        return unitario;
    }

    /**
     * Infiere los pesos que falten en una lista de cajas de la MISMA
     * referencia (mismo producto, luego mismo peso por unidad).
     *
     * El peso pertenece a la CAJA FÍSICA, no a cada línea: una caja mixta
     * (mismo color y nº de caja, varias tallas) se pesa una sola vez. Por eso
     * las líneas se agrupan en cajas físicas y solo la líder (la primera de
     * cada grupo) recibe el peso, calculado sobre las unidades de TODA la
     * caja y con UNA sola tara; las demás líneas quedan a null a propósito.
     */
    public ResultadoInferencia inferirPesos(List<CajaData> cajasMismaReferencia) {
        return inferirPesos(cajasMismaReferencia, null);
    }

    /**
     * Como {@link #inferirPesos(List)}, pero con un peso unitario ya calculado
     * fuera (el del envío entero, compartido entre destinaciones). Si es
     * {@code null} se calcula localmente sobre las cajas recibidas.
     */
    private ResultadoInferencia inferirPesos(List<CajaData> cajasMismaReferencia,
                                             Double unitarioGlobal) {
        List<List<CajaData>> cajasFisicas = agruparEnCajasFisicas(cajasMismaReferencia);
        Double pesoUnitario = (unitarioGlobal != null)
                ? unitarioGlobal : calcularPesoUnitarioMedio(cajasFisicas);
        Set<String> tamanosSinTara = new LinkedHashSet<>();

        for (List<CajaData> lineas : cajasFisicas) {
            CajaData lider = lineas.get(0);
            int unidades = unidadesTotales(lineas);
            Optional<Double> tara = taras.taraPara(lider.getTamanoCaja());

            if (lider.getPesoBrutoKg() != null) {
                // Bruto conocido: solo completar el neto si falta.
                if (lider.getPesoNetoKg() == null) {
                    if (tara.isPresent()) {
                        lider.setPesoNetoKg(redondear2(lider.getPesoBrutoKg() - tara.get()));
                    } else {
                        tamanosSinTara.add(nombreTamano(lider));
                    }
                }
                continue;
            }

            if (lider.getPesoNetoKg() != null) {
                // Neto conocido (p. ej. introducido a mano): completar el bruto.
                if (tara.isPresent()) {
                    lider.setPesoBrutoKg(redondear2(lider.getPesoNetoKg() + tara.get()));
                } else {
                    tamanosSinTara.add(nombreTamano(lider));
                }
                continue;
            }

            // Sin ningún peso: solo se puede inferir con peso unitario y tara.
            if (pesoUnitario != null) {
                if (tara.isPresent()) {
                    double neto = redondear2(unidades * pesoUnitario);
                    lider.setPesoNetoKg(neto);
                    lider.setPesoBrutoKg(redondear2(neto + tara.get()));
                } else {
                    tamanosSinTara.add(nombreTamano(lider));
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
     * Agrupa las líneas de una referencia en cajas físicas: misma caja física
     * = mismo color y mismo nº de caja (las varias tallas de una caja mixta).
     * Se conserva el orden de aparición, así la primera línea de cada grupo es
     * la líder (la que porta el peso de la caja entera).
     */
    private static List<List<CajaData>> agruparEnCajasFisicas(List<CajaData> cajas) {
        Map<String, List<CajaData>> porCaja = new LinkedHashMap<>();
        for (CajaData caja : cajas) {
            String clave = caja.getCodigoColor() + "|" + caja.getNumeroCaja();
            porCaja.computeIfAbsent(clave, c -> new ArrayList<>()).add(caja);
        }
        return new ArrayList<>(porCaja.values());
    }

    /** Unidades de la caja física entera (suma de las de todas sus líneas). */
    private static int unidadesTotales(List<CajaData> lineas) {
        int total = 0;
        for (CajaData linea : lineas) {
            total += linea.getCantidad();
        }
        return total;
    }

    /**
     * Promedio del peso neto por unidad sobre las CAJAS FÍSICAS con algún peso
     * conocido (en su líder) y unidades > 0. Se prefiere el neto (directo); si
     * solo hay bruto se usa (bruto - tara) / unidades. Null si no hay ninguna.
     */
    private Double calcularPesoUnitarioMedio(List<List<CajaData>> cajasFisicas) {
        double suma = 0;
        int conocidas = 0;
        for (List<CajaData> lineas : cajasFisicas) {
            Double contribucion = contribucionUnitaria(lineas);
            if (contribucion != null) {
                suma += contribucion;
                conocidas++;
            }
        }
        return (conocidas > 0) ? suma / conocidas : null;
    }

    /**
     * Aporte al peso neto por unidad de una sola caja física (leído de su
     * líder): neto directo (neto / unidades) o, si solo hay bruto,
     * (bruto - tara) / unidades. Null si la caja no tiene ningún peso conocido,
     * no tiene unidades, o su tamaño no tiene tara (no se puede pasar de bruto
     * a neto).
     */
    private Double contribucionUnitaria(List<CajaData> lineasCajaFisica) {
        CajaData lider = lineasCajaFisica.get(0);
        int unidades = unidadesTotales(lineasCajaFisica);
        if (unidades <= 0) {
            return null;
        }
        if (lider.getPesoNetoKg() != null) {
            return lider.getPesoNetoKg() / unidades;
        }
        if (lider.getPesoBrutoKg() == null) {
            return null;
        }
        Optional<Double> tara = taras.taraPara(lider.getTamanoCaja());
        return tara.map(t -> (lider.getPesoBrutoKg() - t) / unidades).orElse(null);
    }

    private static String nombreTamano(CajaData caja) {
        return caja.getTamanoCaja() == null ? "(sin tamaño)" : caja.getTamanoCaja();
    }

    private static double redondear2(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
