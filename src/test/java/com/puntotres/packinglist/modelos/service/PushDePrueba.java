package com.puntotres.packinglist.modelos.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.puntotres.packinglist.modelos.persistence.SuscripcionPush;

/**
 * Los falsos de los tests de los avisos al móvil ({@link PushTest},
 * {@link RecordatoriosTest}): un servidor de push de mentira y un reloj que
 * se puede parar. Los dos tests importan esta misma clase para compartir el
 * contexto de Spring: con un {@code @TestConfiguration} propio en cada uno,
 * la suite levantaría dos contextos iguales.
 */
@TestConfiguration
public class PushDePrueba {

    @Bean
    @Primary
    EnviadorFalso enviadorFalso() {
        return new EnviadorFalso();
    }

    @Bean
    @Primary
    RelojDePrueba relojDePrueba() {
        return new RelojDePrueba();
    }

    /**
     * Un servidor de push de mentira: apunta cada envío, da por caducados los
     * endpoints de {@code caducados} y falla con un error cualquiera los de
     * {@code fallidos}.
     */
    public static class EnviadorFalso implements EnviadorPush {

        public record Envio(Long usuarioId, String endpoint, String payload) {
        }

        final List<Envio> envios = new CopyOnWriteArrayList<>();
        final Set<String> caducados = ConcurrentHashMap.newKeySet();
        final Set<String> fallidos = ConcurrentHashMap.newKeySet();

        @Override
        public void enviar(SuscripcionPush s, String payloadJson) throws IOException {
            if (caducados.contains(s.getEndpoint())) {
                throw new SuscripcionCaducada("410 Gone");
            }
            if (fallidos.contains(s.getEndpoint())) {
                // Un fallo que no es una suscripción caducada aunque lleve un 410 en el texto.
                throw new IOException("El proxy contesta 410 por error");
            }
            envios.add(new Envio(s.getUsuarioId(), s.getEndpoint(), payloadJson));
        }
    }

    /** El reloj del sistema, salvo cuando un test lo fija en una hora. */
    public static class RelojDePrueba extends Clock {

        private volatile Instant fijo;

        /** A partir de ahora la hora es esa (en la zona del sistema). */
        public void fijar(LocalDateTime cuando) {
            fijo = cuando.atZone(getZone()).toInstant();
        }

        /** Vuelve a la hora de verdad. */
        public void soltar() {
            fijo = null;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.system(zone);
        }

        @Override
        public Instant instant() {
            Instant parado = fijo;
            return parado == null ? Instant.now() : parado;
        }
    }
}
