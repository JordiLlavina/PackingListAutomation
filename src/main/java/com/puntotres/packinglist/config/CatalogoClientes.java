package com.puntotres.packinglist.config;

import java.util.Map;
import java.util.Optional;

/**
 * El catálogo de clientes: los desplegables de todas las pantallas y la
 * configuración con la que se rellena el packing list de cada uno.
 *
 * Existe como interfaz porque los clientes tienen dos orígenes, igual que las
 * taras ({@link CatalogoTaras}):
 * <ul>
 *   <li>los <b>programados</b> —AMI y APC—, que siguen en el bloque
 *       {@code packing-list.clientes} de application.yml
 *       ({@link ClientesProperties}): su packing list es código y sus
 *       destinos con dirección y destinaciones hijas son configuración ligada
 *       a ese código, así que no se crean ni se borran desde una pantalla;</li>
 *   <li>los de <b>plantilla genérica</b>, que viven en la tabla {@code cliente}
 *       y se dan de alta, se corrigen y se eliminan desde /clientes
 *       ({@code CatalogoClientesJpa}, el que manda en la aplicación
 *       arrancada).</li>
 * </ul>
 * Los tests unitarios y {@code Main.java}, que no levantan Spring, siguen
 * usando {@link ClientesProperties} construido a mano.
 */
public interface CatalogoClientes {

    /**
     * Todos los clientes por su clave (normalizada: mayúsculas y sin espacios
     * a los lados), en el orden del desplegable.
     */
    Map<String, ClienteConfig> getClientes();

    /** Configuración del cliente indicado, o vacío si no está en el catálogo. */
    default Optional<ClienteConfig> clientePara(String clave) {
        if (clave == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(getClientes().get(ClienteConfig.normalizar(clave)));
    }
}
