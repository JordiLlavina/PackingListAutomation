package com.puntotres.packinglist.modelos.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Freno a quien prueba códigos de alta a ciegas: un código son seis cifras,
 * un millón de combinaciones que en una red local se recorren en los diez
 * minutos que vale. Con más de {@link #MAXIMO_FALLOS} canjes fallidos desde la
 * misma IP en {@link #VENTANA}, esa IP no puede canjear (ni siquiera un código
 * bueno) hasta que el más antiguo salga de la ventana. Un canje bueno borra
 * los fallos de su IP: quien se equivoca tecleando no se queda frenado.
 *
 * En memoria a propósito: un reinicio lo vacía, y no pasa nada, porque lo que
 * frena es la velocidad de prueba, no a una IP para siempre.
 */
@Component
public class FrenoCanje {

    /** Canjes fallidos que se toleran dentro de la ventana. */
    public static final int MAXIMO_FALLOS = 10;

    /** Lo que se recuerda un canje fallido. */
    public static final Duration VENTANA = Duration.ofMinutes(10);

    private static final int IPS_ANTES_DE_LIMPIAR = 1000;

    /** Las horas de los fallos de cada IP, del más antiguo al más reciente; se tocan solo dentro de compute. */
    private final Map<String, Deque<Instant>> fallos = new ConcurrentHashMap<>();

    /** Si esa IP ya ha fallado demasiadas veces en la ventana. */
    public boolean frenada(String ip) {
        boolean[] frenada = {false};
        fallos.computeIfPresent(ip, (clave, deIp) -> {
            olvidarViejos(deIp);
            frenada[0] = deIp.size() >= MAXIMO_FALLOS;
            return deIp.isEmpty() ? null : deIp;
        });
        return frenada[0];
    }

    /** Apunta un canje fallido de esa IP. */
    public void fallo(String ip) {
        fallos.compute(ip, (clave, deIp) -> {
            Deque<Instant> lista = deIp == null ? new ArrayDeque<>() : deIp;
            olvidarViejos(lista);
            lista.addLast(Instant.now());
            return lista;
        });
        if (fallos.size() > IPS_ANTES_DE_LIMPIAR) {
            // Que el mapa no crezca sin fin con IP que ya no fallan.
            for (String otra : fallos.keySet()) {
                fallos.computeIfPresent(otra, (clave, deIp) -> {
                    olvidarViejos(deIp);
                    return deIp.isEmpty() ? null : deIp;
                });
            }
        }
    }

    /** Un canje bueno: esa IP vuelve a empezar de cero. */
    public void acierto(String ip) {
        fallos.remove(ip);
    }

    private static void olvidarViejos(Deque<Instant> deIp) {
        Instant limite = Instant.now().minus(VENTANA);
        while (!deIp.isEmpty() && deIp.peekFirst().isBefore(limite)) {
            deIp.pollFirst();
        }
    }
}
