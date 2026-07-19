package com.puntotres.packinglist.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ClientesPropertiesTest {

    private static ClientesProperties conClientes(Map<String, ClienteConfig> clientes) {
        ClientesProperties props = new ClientesProperties();
        props.setClientes(clientes);
        return props;
    }

    private static ClienteConfig cliente(TipoPlantilla plantilla) {
        ClienteConfig config = new ClienteConfig();
        config.setPlantilla(plantilla);
        return config;
    }

    @Test
    void clienteParaNormalizaMayusculasYEspacios() {
        ClientesProperties props = conClientes(Map.of("AMI", cliente(TipoPlantilla.AMI)));

        assertTrue(props.clientePara(" ami ").isPresent());
        assertTrue(props.clientePara("Ami").isPresent());
    }

    @Test
    void clienteDesconocidoONuloDevuelveVacio() {
        ClientesProperties props = conClientes(Map.of("AMI", cliente(TipoPlantilla.AMI)));

        assertTrue(props.clientePara("OTRO").isEmpty());
        assertTrue(props.clientePara(null).isEmpty());
    }

    @Test
    void conservaElOrdenDeConfiguracionParaElDesplegable() {
        Map<String, ClienteConfig> clientes = new LinkedHashMap<>();
        clientes.put("AMI", cliente(TipoPlantilla.AMI));
        clientes.put("APC", cliente(TipoPlantilla.APC));
        clientes.put("ACKERMANN", cliente(TipoPlantilla.GENERIC));

        ClientesProperties props = conClientes(clientes);

        assertEquals(List.of("AMI", "APC", "ACKERMANN"),
                List.copyOf(props.getClientes().keySet()));
    }

    @Test
    void destinoParaNormalizaLaClave() {
        ClienteConfig apc = cliente(TipoPlantilla.APC);
        DestinoClienteConfig ivry = new DestinoClienteConfig();
        ivry.setNombreCliente("A.P.C.");
        ivry.setDireccion("74 BIS AV MAURICE THOREZ 94200 IVRY SUR SEINE FRANCE");
        apc.setDestinos(Map.of("IVRY", ivry));

        assertEquals("A.P.C.", apc.destinoPara(" ivry ").get().getNombreCliente());
        assertTrue(apc.destinoPara("TOKIO").isEmpty());
        assertTrue(apc.destinoPara(null).isEmpty());
    }

    /**
     * Enlaza un fragmento equivalente al application.yml con el Binder real
     * de Spring para validar la estructura anidada (enum, destinos).
     */
    @Test
    void enlazaLaEstructuraAnidadaDesdePropiedades() {
        Map<String, String> fuente = new LinkedHashMap<>();
        fuente.put("packing-list.clientes.[AMI].nombre", "AMI");
        fuente.put("packing-list.clientes.[AMI].plantilla", "AMI");
        fuente.put("packing-list.clientes.[AMI].placeholder-temporada", "H26");
        fuente.put("packing-list.clientes.[APC].nombre", "A.P.C.");
        fuente.put("packing-list.clientes.[APC].plantilla", "APC");
        fuente.put("packing-list.clientes.[APC].destinos.[D. USA].nombre-cliente", "A.P.C.-USNY-TOUITOU");
        fuente.put("packing-list.clientes.[APC].destinos.[D. USA].direccion",
                "43 BROOME STREET, 5TH FLOOR; 10013 NEW YORK; USA");
        fuente.put("packing-list.clientes.[PAUL & JOE].nombre", "Paul & Joe");
        fuente.put("packing-list.clientes.[PAUL & JOE].plantilla", "GENERIC");
        fuente.put("packing-list.clientes.[PAUL & JOE].nombre-legal", "MANEKI SAS");
        fuente.put("packing-list.clientes.[PAUL & JOE].direccion-entrega",
                "PAUL&JOE; 14 RUE COMMINES, 75003 PARIS");

        ClientesProperties props = new Binder(new MapConfigurationPropertySource(fuente))
                .bind("packing-list", ClientesProperties.class)
                .get();

        ClienteConfig ami = props.clientePara("AMI").orElseThrow();
        assertEquals(TipoPlantilla.AMI, ami.getPlantilla());
        assertEquals("H26", ami.getPlaceholderTemporada());

        ClienteConfig apc = props.clientePara("APC").orElseThrow();
        assertEquals(TipoPlantilla.APC, apc.getPlantilla());
        assertEquals("A.P.C.-USNY-TOUITOU", apc.destinoPara("D. USA").orElseThrow().getNombreCliente());

        ClienteConfig paulJoe = props.clientePara("PAUL & JOE").orElseThrow();
        assertEquals(TipoPlantilla.GENERIC, paulJoe.getPlantilla());
        assertEquals("MANEKI SAS", paulJoe.getNombreLegal());
    }
}
