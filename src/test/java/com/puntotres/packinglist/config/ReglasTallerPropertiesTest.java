package com.puntotres.packinglist.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Las alturas de palet, las prioridades de reparto y las reglas de mezcla son
 * configuración, no código: una destinación nueva tiene que ser una línea de
 * yml. Aquí se comprueba el enlace y las reglas de herencia, nunca cuánto mide
 * un palet concreto.
 */
@SpringBootTest
class ReglasTallerPropertiesTest {

    @Autowired
    private ReglasTallerProperties reglas;

    @Autowired
    private ClientesProperties clientes;

    @Test
    void elYmlSeEnlaza() {
        assertTrue(reglas.getAlturaPropiaPaletCm() > 0);
        assertTrue(reglas.getPosicionesPalet() > 0);
        assertTrue(reglas.clienteTaller("AMI").isPresent());
        assertTrue(reglas.clienteTaller("APC").isPresent());
    }

    @Test
    void laAlturaUtilDescuentaLoQueLevantaElPaletVacio() {
        int maxima = reglas.clienteTaller("AMI").orElseThrow()
                .destinoPara("CHINA").orElseThrow().getAlturaMaxCm();

        assertEquals(maxima - reglas.getAlturaPropiaPaletCm(),
                reglas.alturaUtilCm("AMI", "CHINA", "CHINA", null));
    }

    @Test
    void laHijaSinAlturaPropiaHeredaLaDeSuPadre() {
        // CHINE FRANCH tiene prioridad máxima pero viaja al almacén de
        // WHOLESALE, así que se apila a la altura de WHOLESALE.
        assertEquals(reglas.alturaUtilCm("APC", "WHOLESALE", "WHOLESALE", null),
                reglas.alturaUtilCm("APC", "CHINE FRANCH", "WHOLESALE", null));
    }

    @Test
    void unClienteSinReglasUsaLaAlturaQueTecleaElUsuario() {
        assertEquals(200 - reglas.getAlturaPropiaPaletCm(),
                reglas.alturaUtilCm("PALOMA WOOL", "PALOMA WOOL", "PALOMA WOOL", 200));
    }

    @Test
    void unClienteSinReglasYSinAlturaTecleadaCaeEnLaPorDefecto() {
        assertEquals(reglas.getAlturaPaletPorDefectoCm() - reglas.getAlturaPropiaPaletCm(),
                reglas.alturaUtilCm("PALOMA WOOL", "PALOMA WOOL", "PALOMA WOOL", null));
    }

    @Test
    void laAlturaDeLaDestinacionManDaSobreLaTecleada() {
        // Si el cliente tiene regla, el usuario no ve el campo de altura; y
        // si llegara un valor, no puede saltarse la norma del cliente.
        assertEquals(reglas.alturaUtilCm("AMI", "CHINA", "CHINA", null),
                reglas.alturaUtilCm("AMI", "CHINA", "CHINA", 200));
    }

    @Test
    void laPrioridadSeEvaluaSobreLaHijaYNoSobreElPadre() {
        // CHINE FRANCH cuelga de WHOLESALE, la última en prioridad, pero ella
        // es de las primeras: por eso el reparto mira la hija.
        assertTrue(reglas.prioridadDe("APC", "CHINE FRANCH").orElseThrow()
                < reglas.prioridadDe("APC", "WHOLESALE").orElseThrow());
    }

    @Test
    void chinaYJapanEmpatanEnPrioridadYParisVaDespues() {
        assertEquals(reglas.prioridadDe("AMI", "CHINA"), reglas.prioridadDe("AMI", "JAPAN"));
        assertTrue(reglas.prioridadDe("AMI", "CHINA").orElseThrow()
                < reglas.prioridadDe("AMI", "PARIS").orElseThrow());
    }

    @Test
    void unaDestinacionDesconocidaNoTienePrioridad() {
        assertTrue(reglas.prioridadDe("APC", "MARTE").isEmpty(),
                "sin prioridad, el reparto la marca como bloqueo en vez de colocarla a ojo");
    }

    @Test
    void chinaYJapanNoMezclanNadaYParisMezclaDentroDelMismoPedido() {
        assertEquals(TipoMezcla.NINGUNA, reglas.mezclaDe("AMI", "CHINA"));
        assertEquals(TipoMezcla.NINGUNA, reglas.mezclaDe("AMI", "JAPAN"));
        assertEquals(TipoMezcla.MISMO_PEDIDO, reglas.mezclaDe("AMI", "PARIS"));
    }

    @Test
    void unClienteSinReglasMezclaLibremente() {
        assertEquals(TipoMezcla.LIBRE, reglas.mezclaDe("PALOMA WOOL", "PALOMA WOOL"));
    }

    @Test
    void elSufijoDelPoDaLaDestinacionYSinSufijoEsParis() {
        ReglaClienteTaller ami = reglas.clienteTaller("AMI").orElseThrow();

        assertEquals("CHINA", ami.destinoDeSufijo("CH").orElseThrow());
        assertEquals("JAPAN", ami.destinoDeSufijo("JP").orElseThrow());
        assertEquals("PARIS", ami.destinoDeSufijo(null).orElseThrow());
        assertEquals("PARIS", ami.destinoDeSufijo("  ").orElseThrow());
        assertTrue(ami.destinoDeSufijo("XX").isEmpty(), "un sufijo desconocido no se adivina");
    }

    @Test
    void amiNumeraSeguidoEntreDestinacionesYApcReinicia() {
        assertEquals(NumeracionCajas.CONTINUA,
                reglas.clienteTaller("AMI").orElseThrow().getNumeracionCajas());
        assertEquals(NumeracionCajas.POR_DESTINACION,
                reglas.clienteTaller("APC").orElseThrow().getNumeracionCajas());
    }

    @Test
    void todaDestinacionDeApcConReglaExisteEnElCatalogoDeClientes() {
        // Si alguien añade aquí una destinación que el catálogo no conoce, el
        // envío se generaría con un destino que después no tiene dirección.
        ClienteConfig apc = clientes.clientePara("APC").orElseThrow();

        reglas.clienteTaller("APC").orElseThrow().getDestinos().keySet()
                .forEach(destino -> assertTrue(apc.destinoPadrePara(destino).isPresent(),
                        "la destinación '" + destino + "' no está en packing-list.clientes"));
    }
}
