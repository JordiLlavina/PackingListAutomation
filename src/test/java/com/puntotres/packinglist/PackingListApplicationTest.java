package com.puntotres.packinglist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.config.TaraProperties;
import com.puntotres.packinglist.config.TipoPlantilla;

/**
 * Levanta el contexto real de Spring y comprueba que application.yml se
 * enlaza en las properties: la tabla de taras (incluida la normalización
 * de claves) y el catálogo de clientes.
 */
@SpringBootTest
class PackingListApplicationTest {

    @Autowired
    private TaraProperties taras;

    @Autowired
    private ClientesProperties clientes;

    /**
     * Las taras son DATOS DEL ALMACÉN: entran tamaños de caja nuevos y los
     * pesos se van corrigiendo según se pesa cada cartón. Este test comprueba
     * que el yml se enlaza y que las claves se normalizan, nunca cuánto pesa
     * un cartón concreto: fijar aquí un número convierte pesar una caja en un
     * cambio de código, que es justo lo que la tabla de taras evita.
     */
    @Test
    void cargaLasTarasDesdeApplicationYml() {
        assertFalse(taras.getTaras().isEmpty(), "application.yml debe traer la tabla de taras");

        String tamano = taras.tamanosDeMayorAMenor().get(0);
        Optional<Double> tara = taras.taraPara(tamano);
        assertTrue(tara.isPresent(), "todo tamaño del catálogo tiene su tara");

        // Normalización de claves: un tamaño leído de las hojas llega con
        // mayúsculas y espacios ("60X40X40 ") y tiene que casar igual.
        assertEquals(tara, taras.taraPara(" " + tamano.toUpperCase() + " "));

        assertTrue(taras.taraPara("99x99x99").isEmpty(), "un tamaño que no está no se inventa");
    }

    @Test
    void cargaElCatalogoDeClientesDesdeApplicationYml() {
        assertEquals(TipoPlantilla.AMI,
                clientes.clientePara("ami").orElseThrow().getPlantilla());
        assertEquals("H26",
                clientes.clientePara("AMI").orElseThrow().getPlaceholderTemporada());

        ClienteConfig apc = clientes.clientePara("APC").orElseThrow();
        assertEquals(TipoPlantilla.APC, apc.getPlantilla());
        assertEquals(6, apc.getDestinos().size());
        assertEquals("I.D.LOOK. LTD.",
                apc.destinoPara("korea").orElseThrow().getNombreCliente());
        assertTrue(apc.destinoPara("D. USA").orElseThrow()
                .getDireccion().contains("43 BROOME STREET"));

        // Los campos de la jerarquía de APC: si Spring dejara de enlazar
        // "destinos-hijo" a destinosHijo, las hijas se quedarían sin padre y
        // sus envíos volverían a no generar packing list, en silencio.
        assertEquals("WH", apc.destinoPara("WHOLESALE").orElseThrow().getAbreviatura());
        assertEquals(List.of("AUSTRALIA", "WHOLESALE", "CHINE FRANCH"),
                apc.destinoPara("WHOLESALE").orElseThrow().getDestinosHijo());
        assertEquals("WHOLESALE", apc.destinoPadrePara("Chine franch").orElseThrow().nombrePadre());
        assertEquals("RETAIL", apc.destinoPadrePara("Wholesale concess").orElseThrow().nombrePadre());
        assertEquals("IVRY", apc.destinoPara("IVRY").orElseThrow().getAbreviatura());
        assertTrue(apc.destinoPara("IVRY").orElseThrow().getDestinosHijo().isEmpty());

        // Solo APC y AMI piden el excel de pedido en la pantalla de entrada.
        assertTrue(apc.isPedidoCliente());
        assertTrue(clientes.clientePara("AMI").orElseThrow().isPedidoCliente());
        assertFalse(clientes.clientePara("ACKERMANN").orElseThrow().isPedidoCliente());

        ClienteConfig sonia = clientes.clientePara("SONIA RYKIEL").orElseThrow();
        assertEquals(TipoPlantilla.GENERIC, sonia.getPlantilla());
        assertEquals("SONIA RYKIEL INTERNATIONAL", sonia.getNombreLegal());
        assertTrue(sonia.getDireccionEntrega().contains("GROBBENDONK"));

        // 1 AMI + 1 APC + 7 genéricos.
        assertEquals(9, clientes.getClientes().size());
    }
}
