package com.puntotres.packinglist.service.taller;

import java.util.ArrayList;
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
        // Lo asignado se lleva por POSICIÓN del objetivo, nunca por nombre de
        // destinación. El mismo artículo puede estar pedido dos veces para el
        // mismo sitio en dos pedidos distintos, y con una entrada por
        // destinación los dos objetivos leerían la suma de los dos y cada uno
        // se la llevaría entera: se empaquetaría el doble de género, y como
        // "servido >= pedido" se cumple de sobra, ningún aviso saltaría.
        int[] asignado = new int[fila.objetivos().size()];
        int restante = fila.recibido();

        for (List<Integer> escalon : porEscalones(clienteClave, fila, exigePrioridad)) {
            if (restante <= 0) {
                break;
            }
            int pedidoDelEscalon = escalon.stream()
                    .mapToInt(indice -> fila.objetivos().get(indice).cantidad())
                    .sum();
            if (pedidoDelEscalon <= 0) {
                continue;
            }
            if (restante >= pedidoDelEscalon) {
                for (int indice : escalon) {
                    asignado[indice] += fila.objetivos().get(indice).cantidad();
                }
                restante -= pedidoDelEscalon;
            } else {
                repartirProporcional(fila, escalon, restante, asignado);
                restante = 0;
            }
        }

        avisarDeLoQueFaltaOSobra(fila, asignado, restante, resultado);
        volcarArticulos(fila, asignado, resultado);
    }

    /**
     * Las POSICIONES de los objetivos agrupadas por prioridad, de la más
     * urgente a la menos. Sin reglas de cliente todos comparten escalón, que
     * es lo correcto cuando no hay norma que diga lo contrario.
     */
    private List<List<Integer>> porEscalones(String clienteClave, FilaAjustada fila,
                                             boolean exigePrioridad) {
        Map<Integer, List<Integer>> porPrioridad = new LinkedHashMap<>();
        for (int indice = 0; indice < fila.objetivos().size(); indice++) {
            OptionalInt prioridad = exigePrioridad
                    ? reglas.prioridadDe(clienteClave, fila.objetivos().get(indice).destino())
                    : OptionalInt.empty();
            porPrioridad.computeIfAbsent(prioridad.orElse(Integer.MAX_VALUE),
                    clave -> new ArrayList<>()).add(indice);
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
    private static void repartirProporcional(FilaAjustada fila, List<Integer> escalon,
                                             int disponible, int[] asignado) {
        int pedido = escalon.stream()
                .mapToInt(indice -> fila.objetivos().get(indice).cantidad())
                .sum();
        int repartido = 0;
        for (int indice : escalon) {
            int parte = (int) ((long) fila.objetivos().get(indice).cantidad() * disponible / pedido);
            asignado[indice] += parte;
            repartido += parte;
        }
        int resto = disponible - repartido;
        if (resto <= 0) {
            return;
        }
        asignado[elQueMasPideDe(fila, escalon)] += resto;
    }

    /**
     * A quién va el resto de la división entera: al objetivo que más pide y,
     * si empatan, al primero por nombre de destinación. El criterio da igual
     * mientras sea fijo; lo que no vale es que dependa del orden de las filas,
     * porque entonces el reparto no se puede comprobar.
     */
    private static int elQueMasPideDe(FilaAjustada fila, List<Integer> escalon) {
        int mayor = escalon.get(0);
        for (int indice : escalon) {
            ObjetivoDestino candidato = fila.objetivos().get(indice);
            ObjetivoDestino actual = fila.objetivos().get(mayor);
            if (candidato.cantidad() > actual.cantidad()
                    || (candidato.cantidad() == actual.cantidad()
                            && candidato.destino().compareTo(actual.destino()) < 0)) {
                mayor = indice;
            }
        }
        return mayor;
    }

    private static void avisarDeLoQueFaltaOSobra(FilaAjustada fila, int[] asignado,
                                                 int restante, ResultadoReparto resultado) {
        for (int indice = 0; indice < fila.objetivos().size(); indice++) {
            ObjetivoDestino objetivo = fila.objetivos().get(indice);
            int servido = asignado[indice];
            if (servido < objetivo.cantidad()) {
                resultado.getAvisos().add(nombreDe(fila, indice) + ": de " + fila.descripcion()
                        + " se piden " + objetivo.cantidad() + " y solo se envían " + servido
                        + ". Faltan " + (objetivo.cantidad() - servido));
            }
        }
        if (restante > 0) {
            resultado.getAvisos().add("De " + fila.descripcion() + " han llegado "
                    + fila.recibido() + " y solo se utilizan " + (fila.recibido() - restante)
                    + ". Sobran " + restante);
        }
    }

    /**
     * Cómo se nombra una destinación en un aviso. Lleva el pedido pegado solo
     * cuando la fila tiene dos objetivos para el mismo sitio: sin él, los dos
     * avisos parecerían el mismo repetido dos veces.
     */
    private static String nombreDe(FilaAjustada fila, int indice) {
        ObjetivoDestino objetivo = fila.objetivos().get(indice);
        boolean repetida = fila.objetivos().stream()
                .filter(otro -> otro.destino().equals(objetivo.destino()))
                .count() > 1;
        return repetida && objetivo.pedido() != null
                ? objetivo.destino() + " (pedido " + objetivo.pedido() + ")"
                : objetivo.destino();
    }

    private static void volcarArticulos(FilaAjustada fila, int[] asignado,
                                        ResultadoReparto resultado) {
        for (int indice = 0; indice < fila.objetivos().size(); indice++) {
            if (asignado[indice] <= 0) {
                continue;
            }
            ObjetivoDestino objetivo = fila.objetivos().get(indice);
            resultado.getArticulos().add(new ArticuloDestinado(
                    objetivo.destino(), fila.referencia(), fila.color(), fila.talla(),
                    objetivo.pedido(), asignado[indice],
                    MedidaCaja.parse(fila.medidaCaja()).orElseThrow().normalizada(),
                    fila.unidadesPorCaja(), fila.pesoBrutoKg()));
        }
    }
}
