package com.puntotres.packinglist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void cargaLasTarasDesdeApplicationYml() {
        assertEquals(Optional.of(0.6), taras.taraPara("60X40X40"));
        assertEquals(Optional.of(0.2), taras.taraPara(" 60x40x30 "));
        assertEquals(Optional.of(0.2), taras.taraPara("40x30x20"));
        assertTrue(taras.taraPara("99x99x99").isEmpty());
    }

    @Test
    void cargaElCatalogoDeClientesDesdeApplicationYml() {
        assertEquals(TipoPlantilla.AMI,
                clientes.clientePara("ami").orElseThrow().getPlantilla());
        assertEquals("H26",
                clientes.clientePara("AMI").orElseThrow().getPlaceholderTemporada());

        ClienteConfig apc = clientes.clientePara("APC").orElseThrow();
        assertEquals(TipoPlantilla.APC, apc.getPlantilla());
        assertEquals(5, apc.getDestinos().size());
        assertEquals("I.D.LOOK. LTD.",
                apc.destinoPara("korea").orElseThrow().getNombreCliente());
        assertTrue(apc.destinoPara("D. USA").orElseThrow()
                .getDireccion().contains("43 BROOME STREET"));

        ClienteConfig sonia = clientes.clientePara("SONIA RYKIEL").orElseThrow();
        assertEquals(TipoPlantilla.GENERIC, sonia.getPlantilla());
        assertEquals("SONIA RYKIEL INTERNATIONAL", sonia.getNombreLegal());
        assertTrue(sonia.getDireccionEntrega().contains("GROBBENDONK"));

        // 1 AMI + 1 APC + 7 genéricos.
        assertEquals(9, clientes.getClientes().size());
    }
}
