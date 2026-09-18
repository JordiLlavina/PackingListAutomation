package com.puntotres.packinglist.shipping.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.shipping.model.Direction;

class DirectionResolverTest {

    private final DirectionResolver resolver = new DirectionResolver("ES");

    @Test
    void desdeEspanaEsExportacionYHaciaEspanaImportacion() {
        assertEquals(Direction.EXPORT, resolver.resolver("ES", "MA"));
        assertEquals(Direction.IMPORT, resolver.resolver("MA", "ES"));
    }

    @Test
    void dentroDeEspanaEsNacionalYEntreOtrosDosPaisesEsCrossTrade() {
        assertEquals(Direction.DOMESTIC, resolver.resolver("ES", "ES"));
        assertEquals(Direction.CROSS_TRADE, resolver.resolver("MA", "FR"));
    }

    @Test
    void noDistingueMayusculasYSinUnPaisNoResuelve() {
        assertEquals(Direction.EXPORT, resolver.resolver("es", "ma"));
        assertNull(resolver.resolver(null, "MA"));
        assertNull(resolver.resolver("ES", null));
    }
}
