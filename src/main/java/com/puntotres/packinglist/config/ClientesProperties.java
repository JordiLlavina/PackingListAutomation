package com.puntotres.packinglist.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Los clientes PROGRAMADOS, cargados desde application.yml:
 *
 * <pre>
 * packing-list:
 *   clientes:
 *     "[AMI]":
 *       nombre: AMI
 *       plantilla: AMI
 *       placeholder-temporada: H26
 * </pre>
 *
 * Solo quedan aquí AMI y APC, cuyo packing list es código. Los clientes de
 * plantilla genérica viven en la base de datos y se dan de alta desde
 * /clientes (ver {@link CatalogoClientes}); en la aplicación arrancada manda
 * {@code CatalogoClientesJpa}, que junta los dos orígenes.
 *
 * Las claves van entre [] y comillas para que Spring respete espacios y
 * signos ("D. USA"). Se conserva el orden del yml, que es el orden con el que
 * empiezan los desplegables.
 */
@ConfigurationProperties(prefix = "packing-list")
public class ClientesProperties implements CatalogoClientes {

    private Map<String, ClienteConfig> clientes = new LinkedHashMap<>();

    @Override
    public Map<String, ClienteConfig> getClientes() {
        return clientes;
    }

    public void setClientes(Map<String, ClienteConfig> clientes) {
        this.clientes = new LinkedHashMap<>();
        clientes.forEach((clave, cliente) ->
                this.clientes.put(ClienteConfig.normalizar(clave), cliente));
    }
}
