package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import com.puntotres.packinglist.config.CatalogoTaras;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.ResumenTemporada;

/**
 * Lo que necesita la pantalla de entrada para pintarse, venga de donde venga.
 *
 * Vive aparte porque a esa pantalla se vuelve desde dos controladores —el del
 * asistente normal y el de la entrada por packing list del taller— y cada uno
 * la montaba por su cuenta. Con dos copias, un dato nuevo (el desplegable de
 * temporadas guardadas, sin ir más lejos) se añade en una y desaparece en
 * silencio al volver por el otro camino.
 */
@Component
public class AtributosEntrada {

    private final ClientesProperties clientesProperties;
    private final CatalogoTaras catalogoTaras;
    private final ArchivoTemporadas archivoTemporadas;

    public AtributosEntrada(ClientesProperties clientesProperties, CatalogoTaras catalogoTaras,
                            ArchivoTemporadas archivoTemporadas) {
        this.clientesProperties = clientesProperties;
        this.catalogoTaras = catalogoTaras;
        this.archivoTemporadas = archivoTemporadas;
    }

    public void anadir(Model model) {
        model.addAttribute("clientes", clientesProperties.getClientes());
        // Tamaños de caja con tara conocida, para el datalist del modo
        // FORMULARIO (evita teclear un tamaño que luego no tendría tara).
        model.addAttribute("tamanosCaja", catalogoTaras.tamanosDeMayorAMenor());
        model.addAttribute("clientesJs", clientesJs());
        model.addAttribute("temporadasJs", temporadasJs());
    }

    private Map<String, Map<String, String>> clientesJs() {
        Map<String, Map<String, String>> clientesJs = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> {
            Map<String, String> datos = new LinkedHashMap<>();
            datos.put("plantilla", config.getPlantilla().name());
            datos.put("placeholderTemporada",
                    config.getPlaceholderTemporada() != null ? config.getPlaceholderTemporada() : "");
            // Solo los clientes que trabajan con excel de pedido ven su input.
            datos.put("pedidoCliente", String.valueOf(config.isPedidoCliente()));
            clientesJs.put(clave, datos);
        });
        return clientesJs;
    }

    /**
     * Las temporadas guardadas de cada cliente, para el desplegable que las
     * ofrece. Van al navegador como un mapa por cliente y no como una lista
     * plana porque el desplegable se rehace al cambiar de cliente sin volver
     * al servidor.
     */
    private Map<String, List<Map<String, String>>> temporadasJs() {
        Map<String, List<Map<String, String>>> porCliente = new LinkedHashMap<>();
        archivoTemporadas.porCliente().forEach((cliente, temporadas) -> {
            List<Map<String, String>> lista = new ArrayList<>();
            for (ResumenTemporada temporada : temporadas) {
                Map<String, String> datos = new LinkedHashMap<>();
                datos.put("id", String.valueOf(temporada.id()));
                datos.put("temporada", temporada.temporada());
                datos.put("fichero", temporada.nombreFichero());
                lista.add(datos);
            }
            porCliente.put(cliente, lista);
        });
        return porCliente;
    }
}
