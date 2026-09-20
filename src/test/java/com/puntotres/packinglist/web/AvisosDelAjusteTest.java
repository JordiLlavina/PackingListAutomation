package com.puntotres.packinglist.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.taller.AvisoTaller;

/**
 * Cómo se reparten los avisos en la pantalla de ajuste del taller.
 *
 * La pantalla tiene una tarjeta por referencia, así que un aviso que habla de
 * una de ellas se lee mucho mejor dentro de su tarjeta que en una lista
 * general donde hay que ir buscando a cuál toca. Repartirlos es decisión de
 * pantalla y por eso vive aquí y no en la digestión, igual que
 * {@code AgrupadorAvisosEtiquetas}.
 */
class AvisosDelAjusteTest {

    private static final List<String> REFERENCIAS = List.of("BAG-A", "UBL1");

    @Test
    void unAvisoSinReferenciaVaALaListaGeneral() {
        AvisosDelAjuste repartidos = AvisosDelAjuste.repartir(
                List.of(AvisoTaller.general("No se ha subido el excel de pedido de AMI")),
                REFERENCIAS);

        assertEquals(List.of("No se ha subido el excel de pedido de AMI"),
                repartidos.getGenerales());
        assertTrue(repartidos.getPorReferencia().isEmpty());
    }

    @Test
    void unAvisoDeUnaReferenciaVaASuTarjeta() {
        AvisosDelAjuste repartidos = AvisosDelAjuste.repartir(
                List.of(AvisoTaller.de("BAG-A", "El pedido no tiene esa línea")),
                REFERENCIAS);

        assertEquals(List.of(), repartidos.getGenerales());
        assertEquals(List.of("El pedido no tiene esa línea"),
                repartidos.getPorReferencia().get("BAG-A"));
    }

    @Test
    void variosAvisosDeLaMismaReferenciaSeAcumulanEnSuOrden() {
        AvisosDelAjuste repartidos = AvisosDelAjuste.repartir(
                List.of(AvisoTaller.de("BAG-A", "primero"),
                        AvisoTaller.de("UBL1", "de otra"),
                        AvisoTaller.de("BAG-A", "segundo")),
                REFERENCIAS);

        assertEquals(List.of("primero", "segundo"), repartidos.getPorReferencia().get("BAG-A"));
        assertEquals(List.of("de otra"), repartidos.getPorReferencia().get("UBL1"));
    }

    @Test
    void elAvisoDeUnaReferenciaQueNoTieneTarjetaNoSePierde() {
        // Una fila puede quedarse fuera de la tabla —apartada por ser de otro
        // cliente, por ejemplo— y su aviso seguir siendo cierto. Sin tarjeta
        // donde pintarlo desaparecería de la pantalla sin decir nada, que es
        // peor que enseñarlo en la lista general.
        AvisosDelAjuste repartidos = AvisosDelAjuste.repartir(
                List.of(AvisoTaller.de("HUERFANA", "algo pasa con esta")),
                REFERENCIAS);

        assertEquals(List.of("algo pasa con esta"), repartidos.getGenerales());
        assertTrue(repartidos.getPorReferencia().isEmpty());
    }

    @Test
    void unaReferenciaSinAvisosNoSaleEnElMapa() {
        // La plantilla pregunta por la referencia y espera null: una lista
        // vacía pintaría el hueco de la lista encima de cada tabla.
        AvisosDelAjuste repartidos = AvisosDelAjuste.repartir(
                List.of(AvisoTaller.de("BAG-A", "algo")), REFERENCIAS);

        assertEquals(null, repartidos.getPorReferencia().get("UBL1"));
    }
}
