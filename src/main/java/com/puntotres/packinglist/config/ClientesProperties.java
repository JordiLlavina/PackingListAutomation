package com.puntotres.packinglist.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catálogo de clientes cargado desde application.yml:
 *
 * <pre>
 * packing-list:
 *   clientes:
 *     "[AMI]":
 *       nombre: AMI
 *       plantilla: AMI
 *       placeholder-temporada: H26
 *     "[ACKERMANN]":
 *       plantilla: GENERIC
 *       nombre-legal: ACKERMANN HOHMANN UND SEDLACEK OHG
 *       direccion-entrega: Goseburgstraße 27, 21339 Lüneburg, Germany
 * </pre>
 *
 * Las claves van entre [] y comillas para que Spring respete espacios y
 * signos ("PAUL &amp; JOE", "D. USA"). Se conserva el orden del yml, que es
 * el orden del desplegable de la pantalla de entrada.
 */
@ConfigurationProperties(prefix = "packing-list")
public class ClientesProperties {

    private Map<String, ClienteConfig> clientes = new LinkedHashMap<>();

    public Map<String, ClienteConfig> getClientes() {
        return clientes;
    }

    public void setClientes(Map<String, ClienteConfig> clientes) {
        this.clientes = new LinkedHashMap<>();
        clientes.forEach((clave, cliente) ->
                this.clientes.put(ClienteConfig.normalizar(clave), cliente));
    }

    /** Configuración del cliente indicado, o vacío si no está en el catálogo. */
    public Optional<ClienteConfig> clientePara(String clave) {
        if (clave == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(clientes.get(ClienteConfig.normalizar(clave)));
    }
}
