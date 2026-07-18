package com.puntotres.packinglist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.config.TaraProperties;

/**
 * Levanta el contexto real de Spring y comprueba que la tabla de taras
 * de application.yml se enlaza en TaraProperties (incluida la
 * normalización de claves).
 */
@SpringBootTest
class PackingListApplicationTest {

    @Autowired
    private TaraProperties taras;

    @Test
    void cargaLasTarasDesdeApplicationYml() {
        assertEquals(Optional.of(1.6), taras.taraPara("60X40X40"));
        assertEquals(Optional.of(1.2), taras.taraPara(" 60x40x30 "));
        assertTrue(taras.taraPara("99x99x99").isEmpty());
    }
}
