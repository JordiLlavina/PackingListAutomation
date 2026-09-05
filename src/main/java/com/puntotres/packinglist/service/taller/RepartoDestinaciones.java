package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.ReglasTallerProperties;
import com.puntotres.packinglist.model.MedidaCaja;

/**
 * Decide cuántas unidades de cada artículo van a cada destinación.
 *
 * Lo que ha mandado el taller casi nunca cuadra con lo que ha pedido el
 * cliente, y entonces hay que elegir. La regla es de negocio, no técnica: las
 * destinaciones de mayor prioridad se sirven enteras y las de menor se quedan
 * cortas. Vale más un mercado servido y otro a medias que los dos a medias.
 *
 * Cada artículo se reparte por su cuenta: que a una referencia le falte
 * género no le quita nada a las demás.
 *
 * Dentro de un mismo escalón de prioridad no hay a quién preferir, así que el
 * reparto es proporcional a lo pedido y el resto entero va al objetivo mayor.
 * Sale siempre el mismo resultado con los mismos datos: un reparto que
 * dependiera del orden de las filas sería imposible de comprobar.
 */
@Service
public class RepartoDestinaciones {

    private final ReglasTallerProperties reglas;

    public RepartoDestinaciones(ReglasTallerProperties reglas) {
        this.reglas = reglas;
    }

    public ResultadoReparto repartir(String clienteClave, List<FilaAjustada> filas) {
        ResultadoReparto resultado = new ResultadoReparto();
        // Un cliente sin bloque de reglas es de destino único: no hay nada que
        // arbitrar, y exigirle prioridades lo bloquearía sin motivo.
        boolean exigePrioridad = reglas.clienteTaller(clienteClave).isPresent();

        for (FilaAjustada fila : filas) {
            if (!esUtilizable(fila, resultado)) {
                continue;
            }
            if (exigePrioridad && !tienenPrioridad(clienteClave, fila, resultado)) {
                continue;
            }
            repartirFila(clienteClave, fila, exigePrioridad, resultado);
        }
        return resultado;
    }

    /** Datos sin los que no se puede empaquetar: los pide la pantalla de ajuste. */
    private boolean esUtilizable(FilaAjustada fila, ResultadoReparto resultado) {
        if (fila.unidadesPorCaja() == null || fila.unidadesPorCaja() <= 0) {
            resultado.getBloqueos().add("De " + fila.descripcion()
                    + " no se sabe cuántas unidades entran en una caja");
            return false;
        }
        if (MedidaCaja.parse(fila.medidaCaja()).isEmpty()) {
            resultado.getBloqueos().add("La medida de caja de " + fila.descripcion()
                    + " ('" + fila.medidaCaja() + "') no se entiende: se escribe largo x ancho "
                    + "x alto, en centímetros y con la altura al final");
            return false;
        }
        if (fila.recibido() <= 0) {
            resultado.getAvisos().add("Del taller no ha llegado nada de " + fila.descripcion()
                    + ": no entra en este envío");
            return false;
        }
        return true;
    }

    private boolean tienenPrioridad(String clienteClave, FilaAjustada fila,
                                    ResultadoReparto resultado) {
        boolean todas = true;
        for (ObjetivoDestino objetivo : fila.objetivos()) {
            if (reglas.prioridadDe(clienteClave, objetivo.destino()).isEmpty()) {
                resultado.getBloqueos().add("La destinación '" + objetivo.destino()
                        + "' no está en las normas de " + clienteClave + ": no se sabe a qué "
                        + "altura se apila ni con qué preferencia se sirve");
                todas = false;
            }
        }
        return todas;
    }

    private void repartirFila(String clienteClave, FilaAjustada fila, boolean exigePrioridad,
                              ResultadoReparto resultado) {
        Map<String, Integer> asignado = new LinkedHashMap<>();
        int restante = fila.recibido();

        for (List<ObjetivoDestino> escalon : porEscalones(clienteClave, fila, exigePrioridad)) {
            if (restante <= 0) {
                break;
            }
            int pedidoDelEscalon = escalon.stream().mapToInt(ObjetivoDestino::cantidad).sum();
            if (pedidoDelEscalon <= 0) {
                continue;
            }
            if (restante >= pedidoDelEscalon) {
                escalon.forEach(o -> asignado.merge(o.destino(), o.cantidad(), Integer::sum));
                restante -= pedidoDelEscalon;
            } else {
                repartirProporcional(escalon, restante, asignado);
                restante = 0;
            }
        }

        avisarDeLoQueFaltaOSobra(fila, asignado, restante, resultado);
        volcarArticulos(fila, asignado, resultado);
    }

    /**
     * Los objetivos agrupados por prioridad, de la más urgente a la menos. Sin
     * reglas de cliente todos comparten escalón, que es lo correcto cuando no
     * hay norma que diga lo contrario.
     */
    private List<List<ObjetivoDestino>> porEscalones(String clienteClave, FilaAjustada fila,
                                                     boolean exigePrioridad) {
        Map<Integer, List<ObjetivoDestino>> porPrioridad = new LinkedHashMap<>();
        for (ObjetivoDestino objetivo : fila.objetivos()) {
            OptionalInt prioridad = exigePrioridad
                    ? reglas.prioridadDe(clienteClave, objetivo.destino())
                    : OptionalInt.empty();
            porPrioridad.computeIfAbsent(prioridad.orElse(Integer.MAX_VALUE),
                    clave -> new ArrayList<>()).add(objetivo);
        }
        return porPrioridad.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Reparte lo que hay entre destinaciones que empatan en prioridad, en
     * proporción a lo que pide cada una. El resto de la división entera va a
     * la que más pide: dejar a una en cero mientras otra va completa sería
     * peor, y sin criterio el resultado dependería del orden de las filas.
     */
    private static void repartirProporcional(List<ObjetivoDestino> escalon, int disponible,
                                             Map<String, Integer> asignado) {
        int pedido = escalon.stream().mapToInt(ObjetivoDestino::cantidad).sum();
        int repartido = 0;
        for (ObjetivoDestino objetivo : escalon) {
            int parte = (int) ((long) objetivo.cantidad() * disponible / pedido);
            asignado.merge(objetivo.destino(), parte, Integer::sum);
            repartido += parte;
        }
        int resto = disponible - repartido;
        if (resto <= 0) {
            return;
        }
        ObjetivoDestino mayor = escalon.stream()
                .max(Comparator.comparingInt(ObjetivoDestino::cantidad)
                        .thenComparing(Comparator.comparing(ObjetivoDestino::destino).reversed()))
                .orElseThrow();
        asignado.merge(mayor.destino(), resto, Integer::sum);
    }

    private static void avisarDeLoQueFaltaOSobra(FilaAjustada fila, Map<String, Integer> asignado,
                                                 int restante, ResultadoReparto resultado) {
        for (ObjetivoDestino objetivo : fila.objetivos()) {
            int servido = asignado.getOrDefault(objetivo.destino(), 0);
            if (servido < objetivo.cantidad()) {
                resultado.getAvisos().add(objetivo.destino() + ": de " + fila.descripcion()
                        + " se piden " + objetivo.cantidad() + " y solo se envían " + servido
                        + ". Faltan " + (objetivo.cantidad() - servido));
            }
        }
        if (restante > 0) {
            resultado.getAvisos().add("De " + fila.descripcion() + " han llegado "
                    + fila.recibido() + " y el pedido cubre el resto: sobran " + restante
                    + ", que no entran en este envío");
        }
    }

    private static void volcarArticulos(FilaAjustada fila, Map<String, Integer> asignado,
                                        ResultadoReparto resultado) {
        for (ObjetivoDestino objetivo : fila.objetivos()) {
            int cantidad = asignado.getOrDefault(objetivo.destino(), 0);
            if (cantidad <= 0) {
                continue;
            }
            resultado.getArticulos().add(new ArticuloDestinado(
                    objetivo.destino(), fila.referencia(), fila.color(), fila.talla(),
                    objetivo.pedido(), cantidad,
                    MedidaCaja.parse(fila.medidaCaja()).orElseThrow().normalizada(),
                    fila.unidadesPorCaja()));
        }
    }
}
