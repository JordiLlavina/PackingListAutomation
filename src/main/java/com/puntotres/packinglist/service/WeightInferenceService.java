package com.puntotres.packinglist.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.VolumenUtil;
import com.puntotres.packinglist.config.CatalogoTaras;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.CajaFisica;
import com.puntotres.packinglist.model.OrigenPeso;

/**
 * Completa los pesos que faltan a partir de las cajas con algún peso
 * conocido de la misma referencia y de la tara de su tamaño de caja
 * ({@link CatalogoTaras}: el yml siembra la tabla de cartones y la pantalla
 * /taras la corrige a medida que se pesa cada uno). La tara que se suma y se
 * resta es la del EMBALAJE entero —el cartón más los separadores que van
 * dentro de cada caja—; de juntar los dos se encarga el propio catálogo.
 *
 * La unidad de peso es la CAJA FÍSICA ({@link CajaFisica}): una caja puede
 * ocupar varias líneas (tallas, colores, referencias) pero se pesa una sola
 * vez y ese peso lo lleva su línea líder; las demás quedan a null a
 * propósito. Los números de caja se reinician en cada destinación, así que
 * las cajas se agrupan DENTRO de cada una.
 *
 * Regla: el peso neto por unidad es el mismo en toda la referencia (mismo
 * producto). Se promedia sobre las cajas con peso conocido que llevan una
 * sola referencia, prefiriendo el neto (neto / unidades) y usando el bruto
 * como alternativa ((bruto - tara) / unidades). Un solo peso introducido a
 * mano — neto o bruto — desbloquea el resto de su referencia. Una referencia
 * sin ninguna caja pesada en el envío tira del peso por unidad que recuerda
 * el catálogo de modelos, si se le pasa, y esas cajas quedan marcadas
 * {@link OrigenPeso#CATALOGO}.
 *
 * Lo que no se puede inferir se queda a null (nunca se inventa un número),
 * pero no en silencio: los tamaños de caja sin tara configurada y las cajas
 * que llegan SIN medida se devuelven como avisos en
 * {@link ResultadoInferencia} para que la pantalla de revisión los muestre.
 * Son dos problemas distintos y se dicen por separado: el primero se arregla
 * pesando el cartón en la pantalla /taras y el segundo eligiendo el tamaño en
 * la propia revisión. Los dos se resuelven tecleando, ninguno editando un
 * fichero: mandar a alguien a un yml por un dato del almacén es peor que no
 * avisar.
 * Que la caja sin medida se avise desde aquí (y no al importar) es lo que
 * hace que el aviso desaparezca en cuanto se corrige: la revisión reinfiere
 * el envío entero tras cada edición, pero no vuelve a importar.
 */
@Service
public class WeightInferenceService {

    private final CatalogoTaras taras;

    public WeightInferenceService(CatalogoTaras taras) {
        this.taras = taras;
    }

    /**
     * Infiere los pesos de una sola destinación (todas sus referencias).
     */
    public ResultadoInferencia inferirPesosPorReferencia(List<CajaData> cajas) {
        return inferirPesosDelEnvio(List.of(cajas));
    }

    /**
     * Infiere los pesos de una lista de cajas de la MISMA referencia.
     */
    public ResultadoInferencia inferirPesos(List<CajaData> cajasMismaReferencia) {
        return inferirPesosDelEnvio(List.of(cajasMismaReferencia));
    }

    /**
     * Infiere los pesos de un envío completo: una lista de cajas por
     * destinación. El peso neto por unidad se promedia por referencia sobre
     * TODAS las destinaciones (mismo producto = mismo peso por unidad, así un
     * peso tecleado en una caja informa al mismo modelo en cualquier palet o
     * destinación), pero la identidad de la CAJA FÍSICA es POR DESTINACIÓN:
     * la caja 5 de CHINA y la 5 de JAPAN son cajas distintas.
     */
    public ResultadoInferencia inferirPesosDelEnvio(List<List<CajaData>> cajasPorDestino) {
        return inferirPesosDelEnvio(cajasPorDestino, referencia -> Optional.empty());
    }

    /**
     * Lo mismo, con el peso neto por unidad que el catálogo recuerda de cada
     * referencia como ÚLTIMO recurso: solo se usa para una referencia sin
     * ninguna caja pesada en este envío, y lo que sale de él se marca
     * {@link OrigenPeso#CATALOGO} para que la revisión lo pinte en gris.
     *
     * Lo calculado (inferido o del catálogo) se olvida al empezar y se vuelve
     * a calcular: así sigue a lo que se acaba de pesar o de corregir —un
     * cartón cambiado, una caja pesada que deja atrás el peso del catálogo—
     * en vez de quedarse con el número de la vez anterior. Lo medido no se
     * toca nunca.
     *
     * La consulta al catálogo se hace una vez por referencia y llamada.
     */
    public ResultadoInferencia inferirPesosDelEnvio(List<List<CajaData>> cajasPorDestino,
                                                    Function<String, Optional<Double>> pesoUnidadCatalogo) {
        olvidarLoCalculado(cajasPorDestino);
        Map<String, Double> unitarioPorReferencia = pesoUnitarioPorReferencia(cajasPorDestino);
        Map<String, Optional<Double>> consultadas = new HashMap<>();
        Function<String, Optional<Double>> catalogo = referencia -> referencia == null
                ? Optional.empty()
                : consultadas.computeIfAbsent(referencia, pesoUnidadCatalogo);
        // Un mismo tamaño sin tara puede aparecer en varias cajas: el aviso
        // se emite una sola vez.
        Set<String> tamanosSinTara = new LinkedHashSet<>();
        Set<Integer> cajasSinMedida = new LinkedHashSet<>();
        for (List<CajaData> cajasDestino : cajasPorDestino) {
            for (CajaFisica caja : CajaFisica.agrupar(cajasDestino)) {
                if (!tieneMedida(caja.lider())) {
                    cajasSinMedida.add(caja.lider().getNumeroCaja());
                }
                completarPesos(caja, unitarioPorReferencia, catalogo, tamanosSinTara);
            }
        }

        ResultadoInferencia resultado = new ResultadoInferencia();
        if (!cajasSinMedida.isEmpty()) {
            resultado.getAvisos().add("Cajas sin medida: " + cajasSinMedida.stream()
                    .map(String::valueOf).collect(Collectors.joining(", "))
                    + ". Elige su tamaño en la columna TAMAÑO: sin medida no hay tara"
                    + " para calcular el peso, y esas cajas no suman volumen en el packing list");
        }
        for (String tamano : tamanosSinTara) {
            resultado.getAvisos().add("No se sabe lo que pesa el cartón '" + tamano
                    + "' vacío: pésalo en la pantalla de taras para poder inferir sus pesos");
        }
        return resultado;
    }

    /**
     * Completa los pesos de UNA caja física, siempre sobre su línea líder:
     * con el bruto conocido solo falta el neto (bruto − tara); con el neto
     * conocido (tecleado a mano) se deriva el bruto; sin ninguno se estima
     * el neto con el peso unitario de cada línea y se le suma UNA tara. Una,
     * porque el cartón y sus separadores son los de la caja y no los de cada
     * línea que lleve dentro.
     */
    private void completarPesos(CajaFisica caja, Map<String, Double> unitarioPorReferencia,
                                Function<String, Optional<Double>> catalogo,
                                Set<String> tamanosSinTara) {
        CajaData lider = caja.lider();
        Optional<Double> tara = taras.taraPara(lider.getTamanoCaja());

        if (lider.getPesoBrutoKg() != null) {
            if (lider.getPesoNetoKg() == null) {
                if (tara.isPresent()) {
                    lider.setPesoNetoKg(redondear2(lider.getPesoBrutoKg() - tara.get()));
                } else {
                    anotarTamanoSinTara(lider, tamanosSinTara);
                }
            }
            return;
        }

        if (lider.getPesoNetoKg() != null) {
            if (tara.isPresent()) {
                lider.setPesoBrutoKg(redondear2(lider.getPesoNetoKg() + tara.get()));
            } else {
                anotarTamanoSinTara(lider, tamanosSinTara);
            }
            return;
        }

        // Sin ningún peso: solo se puede inferir con peso unitario y tara. Si
        // no hay unitario la caja queda pendiente, pero eso ya se ve en la
        // tabla de revisión: no es un aviso de configuración.
        NetoEstimado neto = netoEstimado(caja, unitarioPorReferencia, catalogo);
        if (neto == null) {
            return;
        }
        if (tara.isEmpty()) {
            anotarTamanoSinTara(lider, tamanosSinTara);
            return;
        }
        lider.setPesoNetoKg(redondear2(neto.kilos()));
        lider.setPesoBrutoKg(redondear2(neto.kilos() + tara.get()));
        lider.setOrigenPeso(neto.delCatalogo() ? OrigenPeso.CATALOGO : OrigenPeso.INFERIDO);
    }

    /** El neto calculado de una caja y si alguna de sus líneas ha tirado del catálogo. */
    private record NetoEstimado(double kilos, boolean delCatalogo) {
    }

    /**
     * Peso neto estimado de la caja ENTERA: cada línea aporta sus unidades
     * por el unitario de SU referencia, el del envío o, si no hay ninguna
     * caja pesada de esa referencia, el del catálogo. Null si alguna línea
     * no tiene ni uno ni otro.
     */
    private static NetoEstimado netoEstimado(CajaFisica caja, Map<String, Double> unitarioPorReferencia,
                                             Function<String, Optional<Double>> catalogo) {
        double total = 0;
        boolean delCatalogo = false;
        for (CajaData linea : caja.lineas()) {
            Double unitario = unitarioPorReferencia.get(linea.getReferencia());
            if (unitario == null) {
                unitario = catalogo.apply(linea.getReferencia()).orElse(null);
                if (unitario == null) {
                    return null;
                }
                delCatalogo = true;
            }
            total += linea.getCantidad() * unitario;
        }
        return new NetoEstimado(total, delCatalogo);
    }

    /**
     * Deja sin peso las cajas cuyo peso calculó una pasada anterior, para que
     * esta lo vuelva a calcular con lo de ahora. Los pesos viven en la línea
     * líder, así que basta con mirar esa.
     */
    private static void olvidarLoCalculado(List<List<CajaData>> cajasPorDestino) {
        for (List<CajaData> cajasDestino : cajasPorDestino) {
            for (CajaFisica caja : CajaFisica.agrupar(cajasDestino)) {
                CajaData lider = caja.lider();
                if (lider.getOrigenPeso() == OrigenPeso.INFERIDO
                        || lider.getOrigenPeso() == OrigenPeso.CATALOGO) {
                    lider.setPesoNetoKg(null);
                    lider.setPesoBrutoKg(null);
                    lider.setOrigenPeso(null);
                }
            }
        }
    }

    /**
     * Peso neto medio por unidad de cada referencia, calculado sobre TODAS
     * las destinaciones a la vez. Solo aportan las cajas de una sola
     * referencia: en una caja que mezcla varias no se sabe qué parte del
     * peso es de cada producto.
     */
    private Map<String, Double> pesoUnitarioPorReferencia(List<List<CajaData>> cajasPorDestino) {
        Map<String, double[]> acumulado = new LinkedHashMap<>(); // [suma, nº conocidas]
        for (List<CajaData> cajasDestino : cajasPorDestino) {
            for (CajaFisica caja : CajaFisica.agrupar(cajasDestino)) {
                if (mezclaReferencias(caja)) {
                    continue;
                }
                Double contribucion = contribucionUnitaria(caja);
                if (contribucion == null) {
                    continue;
                }
                double[] acc = acumulado.computeIfAbsent(
                        caja.lider().getReferencia(), r -> new double[2]);
                acc[0] += contribucion;
                acc[1]++;
            }
        }
        Map<String, Double> unitario = new LinkedHashMap<>();
        acumulado.forEach((referencia, acc) -> unitario.put(referencia, acc[0] / acc[1]));
        return unitario;
    }

    private static boolean mezclaReferencias(CajaFisica caja) {
        return caja.lineas().stream().map(CajaData::getReferencia).distinct().count() > 1;
    }

    /**
     * Aporte al peso neto por unidad de una sola caja física (leído de su
     * líder): neto directo (neto / unidades) o, si solo hay bruto,
     * (bruto - tara) / unidades. Null si la caja no tiene ningún peso
     * conocido, no tiene unidades, o su tamaño no tiene tara (no se puede
     * pasar de bruto a neto).
     */
    private Double contribucionUnitaria(CajaFisica caja) {
        int unidades = caja.unidades();
        if (unidades <= 0) {
            return null;
        }
        if (caja.pesoNetoKg() != null) {
            return caja.pesoNetoKg() / unidades;
        }
        if (caja.pesoBrutoKg() == null) {
            return null;
        }
        Optional<Double> tara = taras.taraPara(caja.lider().getTamanoCaja());
        return tara.map(t -> (caja.pesoBrutoKg() - t) / unidades).orElse(null);
    }

    private static boolean tieneMedida(CajaData caja) {
        return VolumenUtil.tieneMedida(caja.getTamanoCaja());
    }

    /**
     * Una caja SIN medida no es un problema de configuración: no hay ninguna
     * tara que añadir al yml, hay una medida que elegir en la revisión, y de
     * eso avisa su propia línea. Solo se anotan aquí los tamaños que sí están
     * escritos y no tienen tara.
     */
    private static void anotarTamanoSinTara(CajaData lider, Set<String> tamanosSinTara) {
        if (tieneMedida(lider)) {
            tamanosSinTara.add(lider.getTamanoCaja());
        }
    }

    private static double redondear2(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
