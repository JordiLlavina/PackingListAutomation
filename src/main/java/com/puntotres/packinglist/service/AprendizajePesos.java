package com.puntotres.packinglist.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.CatalogoTaras;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.CajaFisica;
import com.puntotres.packinglist.persistence.MemoriaReferencias;

/**
 * Aprende lo que pesa una unidad de cada referencia a partir de un envío que
 * se acaba de generar, y lo guarda en el artículo del catálogo de modelos.
 * Es el único sitio que guarda pesos: corre al pulsar «Generar Excels
 * Cliente», venga el envío de donde venga (JSON, fotos o taller).
 *
 * <p>Lo que no se deduce del código, y por qué:
 * <ul>
 *   <li><b>Solo se aprende de lo MEDIDO</b> ({@link CajaData#tienePesoMedido}):
 *       lo inferido y lo que vino del catálogo no cuenta, o el programa se
 *       aprendería a sí mismo y un peso equivocado se perpetuaría solo.</li>
 *   <li><b>Solo de cajas de una referencia</b>: en un bulto con dos no se sabe
 *       qué parte del peso es de cada una. Los colores y tallas de una
 *       referencia sí cuentan juntos, como en la inferencia.</li>
 *   <li><b>Solo con el cartón pesado de verdad</b>
 *       ({@link CatalogoTaras#cartonPesado}): con la tara de relleno el neto
 *       es casi un bruto. Se exige también cuando el neto se ha tecleado,
 *       porque no se sabe si lo tecleado fue el neto o el bruto.</li>
 *   <li><b>Solo para los artículos que ya están en el catálogo</b> (decisión
 *       del usuario): no se da de alta nada, y se dice cuáles se han quedado
 *       sin guardar para que se carguen en /carga-modelos.</li>
 *   <li>Un peso que se aparta más de un {@value #DESVIACION_AVISO_PCT} % del
 *       guardado <b>se guarda igual y se avisa</b>: quien acaba de pesar la
 *       caja la tiene delante, pero un cambio así suele ser un error de
 *       tecleo o de caja.</li>
 * </ul>
 */
@Service
public class AprendizajePesos {

    /** A partir de qué diferencia con el peso guardado se avisa, en tanto por ciento. */
    public static final int DESVIACION_AVISO_PCT = 15;

    /**
     * Lo que se ha medido en un envío: el peso por unidad de cada referencia,
     * y las referencias que tenían cajas pesadas pero solo en cartones sin
     * pesar (con el cartón de una de ellas, para decir cuál falta pesar).
     */
    public record Medidos(Map<String, Double> pesoPorUnidad, Map<String, String> cartonSinPesar) {
    }

    private final MemoriaReferencias memoria;
    private final CatalogoTaras taras;

    public AprendizajePesos(MemoriaReferencias memoria, CatalogoTaras taras) {
        this.memoria = memoria;
        this.taras = taras;
    }

    /**
     * Guarda el peso por unidad de cada referencia pesada en el envío y
     * devuelve los avisos para la pantalla de resultados.
     */
    public List<String> aprender(String cliente, List<List<CajaData>> cajasPorDestino) {
        Medidos medidos = medir(cajasPorDestino);
        List<String> avisos = new ArrayList<>();
        List<String> fueraDelCatalogo = new ArrayList<>();
        medidos.pesoPorUnidad().forEach((referencia, peso) -> {
            Optional<Double> anterior = memoria.pesoNetoUnidad(cliente, referencia);
            if (!memoria.recordarPesoUnidad(cliente, referencia, peso)) {
                fueraDelCatalogo.add(referencia);
                return;
            }
            Optional<Double> guardado = memoria.pesoNetoUnidad(cliente, referencia);
            if (anterior.isPresent() && guardado.isPresent() && seAparta(guardado.get(), anterior.get())) {
                avisos.add(referencia + ": el peso por unidad guardado pasa de " + enKilos(anterior.get())
                        + " kg a " + enKilos(guardado.get()) + " kg, más de un " + DESVIACION_AVISO_PCT
                        + " % de diferencia. Si no es correcto, corrígelo en el catálogo de modelos");
            }
        });
        if (!fueraDelCatalogo.isEmpty()) {
            avisos.add("Peso no guardado de " + String.join(", ", fueraDelCatalogo)
                    + ": no están en el catálogo de modelos. Dalos de alta en la carga de modelos"
                    + " para que se recuerde su peso");
        }
        medidos.cartonSinPesar().forEach((referencia, medida) -> avisos.add(
                "Peso no guardado de " + referencia + ": su cartón " + medida
                        + " todavía no está pesado en la pantalla de taras"));
        return avisos;
    }

    /**
     * El peso neto por unidad de cada referencia, medio entre todas sus cajas
     * pesadas del envío (todas las destinaciones: el peso es del producto).
     */
    public Medidos medir(List<List<CajaData>> cajasPorDestino) {
        Map<String, double[]> acumulado = new LinkedHashMap<>(); // [suma, cajas]
        Map<String, String> cartonSinPesar = new LinkedHashMap<>();
        for (List<CajaData> cajasDestino : cajasPorDestino) {
            for (CajaFisica caja : CajaFisica.agrupar(cajasDestino)) {
                CajaData lider = caja.lider();
                if (!lider.tienePesoMedido() || mezclaReferencias(caja) || caja.unidades() <= 0
                        || lider.getReferencia() == null) {
                    continue;
                }
                if (!taras.cartonPesado(lider.getTamanoCaja())) {
                    if (lider.getTamanoCaja() != null && !lider.getTamanoCaja().isBlank()) {
                        cartonSinPesar.putIfAbsent(lider.getReferencia(), lider.getTamanoCaja());
                    }
                    continue;
                }
                Double neto = netoDe(lider);
                if (neto == null || neto <= 0) {
                    continue;
                }
                double[] acc = acumulado.computeIfAbsent(lider.getReferencia(), r -> new double[2]);
                acc[0] += neto / caja.unidades();
                acc[1]++;
            }
        }
        Map<String, Double> pesoPorUnidad = new LinkedHashMap<>();
        acumulado.forEach((referencia, acc) -> pesoPorUnidad.put(referencia, acc[0] / acc[1]));
        pesoPorUnidad.keySet().forEach(cartonSinPesar::remove);
        return new Medidos(pesoPorUnidad, cartonSinPesar);
    }

    /** El neto de la caja: el que lleva o, si solo tiene bruto, bruto − tara. */
    private Double netoDe(CajaData lider) {
        if (lider.getPesoNetoKg() != null) {
            return lider.getPesoNetoKg();
        }
        return taras.taraPara(lider.getTamanoCaja())
                .map(tara -> lider.getPesoBrutoKg() - tara)
                .orElse(null);
    }

    private static boolean mezclaReferencias(CajaFisica caja) {
        return caja.lineas().stream().map(CajaData::getReferencia).distinct().count() > 1;
    }

    private static boolean seAparta(double nuevo, double anterior) {
        return anterior > 0 && Math.abs(nuevo - anterior) / anterior * 100 > DESVIACION_AVISO_PCT;
    }

    /** Un peso tal como se lee en español, sin ceros de más: "1", "0,4512". */
    private static String enKilos(double kilos) {
        return BigDecimal.valueOf(kilos).setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
