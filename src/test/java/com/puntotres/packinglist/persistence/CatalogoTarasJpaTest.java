package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.config.TaraProperties;

/**
 * La tabla de taras vive en la base de datos, pero el yml sigue siendo su
 * semilla: en una máquina nueva, arrancar deja la tabla con lo que haya
 * configurado, y a partir de ahí manda lo que se edite en /taras.
 *
 * Aquí no se comprueba CUÁNTO pesa ningún cartón —eso es dato de almacén y
 * cambia cada vez que se pesa uno—, solo que el trasvase y la edición
 * funcionan.
 */
@SpringBootTest
class CatalogoTarasJpaTest {

    @Autowired
    private CatalogoTarasJpa catalogo;

    @Autowired
    private TaraProperties semilla;

    @Test
    void alArrancarLaTablaSeSiembraConElYml() {
        assertFalse(semilla.tamanosDeMayorAMenor().isEmpty(), "el yml de test trae taras");
        assertTrue(catalogo.tamanosDeMayorAMenor().containsAll(semilla.tamanosDeMayorAMenor()));
    }

    @Test
    void loGuardadoManda() {
        catalogo.guardar("99x99x99", 3.5);

        assertEquals(3.5, catalogo.taraPara(" 99X99X99 ").orElseThrow());
        assertTrue(catalogo.tamanosDeMayorAMenor().contains("99x99x99"));
    }

    @Test
    void volverAGuardarUnaMedidaLaCorrige() {
        catalogo.guardar("98x98x98", 1.0);
        catalogo.guardar("98x98x98", 2.5);

        assertEquals(2.5, catalogo.taraPara("98x98x98").orElseThrow());
        assertEquals(1, catalogo.todas().stream()
                .filter(t -> t.getMedida().equals("98x98x98")).count());
    }

    @Test
    void unTamanoQueNoEstaNoSeInventa() {
        assertTrue(catalogo.taraPara("11x11x11").isEmpty());
        assertTrue(catalogo.taraPara(null).isEmpty());
    }

    @Test
    void elListadoVaDeLaCajaMasGrandeALaMasPequena() {
        catalogo.guardar("100x40x40", 1.0);
        catalogo.guardar("40x30x20", 1.0);

        var tamanos = catalogo.tamanosDeMayorAMenor();

        assertTrue(tamanos.indexOf("100x40x40") < tamanos.indexOf("40x30x20"),
                "el orden es por volumen; como texto '100x40x40' iría antes igualmente, "
                        + "pero '40x30x20' iría antes que '60x40x40' y sería falso");
    }
}
