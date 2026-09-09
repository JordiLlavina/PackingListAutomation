package com.puntotres.packinglist.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.TemporadaGuardada;

/**
 * Pantalla del archivo de temporadas: guardar el excel de pedido de una
 * temporada una sola vez y elegirla luego en la entrada del envío.
 *
 * El excel de pedido de un cliente es el mismo documento durante toda una
 * temporada, así que subirlo en cada envío es trabajo repetido y una ocasión
 * de equivocarse de fichero. Aquí se da de alta, se corrige y se borra.
 *
 * Solo se ofrecen los clientes que trabajan con excel de pedido
 * ({@code pedido-cliente} en application.yml): para el resto, una temporada
 * guardada no tendría ningún fichero que ahorrar.
 */
@Controller
public class TemporadasController {

    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ArchivoTemporadas archivo;
    private final ClientesProperties clientesProperties;

    public TemporadasController(ArchivoTemporadas archivo, ClientesProperties clientesProperties) {
        this.archivo = archivo;
        this.clientesProperties = clientesProperties;
    }

    @GetMapping("/temporadas")
    public String temporadas(Model model) {
        model.addAttribute("temporadas", archivo.todas());
        model.addAttribute("clientes", clientesConPedido());
        return "temporadas";
    }

    /**
     * Alta (sin {@code id}) o corrección (con él). Un fichero vacío al editar
     * significa "deja el que ya tenía": se corrige el nombre de la temporada
     * sin tener que volver a buscar el excel en el disco.
     */
    @PostMapping("/temporadas")
    public String guardar(@RequestParam(required = false) Long id,
                          @RequestParam String cliente,
                          @RequestParam String temporada,
                          @RequestParam(required = false) MultipartFile pedido,
                          RedirectAttributes redirect) {
        ClienteConfig config = clientesProperties.clientePara(cliente).orElse(null);
        if (config == null || !config.isPedidoCliente()) {
            return error(redirect, "Cliente desconocido o que no trabaja con excel de pedido: "
                    + cliente + ".");
        }
        String nombreTemporada = temporada == null ? "" : temporada.trim();
        if (nombreTemporada.isBlank()) {
            return error(redirect, "La temporada necesita un nombre: es el que se escribe "
                    + "en los documentos del cliente (H26, FALL26...).");
        }
        if (archivo.yaExiste(cliente, nombreTemporada, id)) {
            return error(redirect, "Ya hay una temporada " + nombreTemporada + " guardada de "
                    + config.getNombre() + ". Edita esa en vez de crear otra igual.");
        }

        boolean esAlta = id == null;
        byte[] excel = null;
        String nombreFichero = null;
        if (pedido != null && !pedido.isEmpty()) {
            if (!esExcel(pedido.getOriginalFilename())) {
                return error(redirect, "El pedido del cliente es un excel (.xlsx): '"
                        + pedido.getOriginalFilename() + "' no lo es.");
            }
            try {
                excel = pedido.getBytes();
                nombreFichero = pedido.getOriginalFilename();
            } catch (IOException e) {
                return error(redirect, "No se ha podido leer el excel subido: " + e.getMessage());
            }
        }
        // Al dar de alta el fichero es obligatorio: una temporada sin él no
        // ahorra nada y en la entrada saldría un desplegable que no trae
        // ningún pedido, sin decir por qué.
        if (esAlta && excel == null) {
            return error(redirect, "Sube el excel de pedido de la temporada: es lo que se "
                    + "guarda para no tener que buscarlo en cada envío.");
        }

        archivo.guardar(id, cliente, nombreTemporada, nombreFichero, excel);
        redirect.addFlashAttribute("mensaje", "Guardada la temporada " + nombreTemporada
                + " de " + config.getNombre() + ".");
        return "redirect:/temporadas";
    }

    @PostMapping("/temporadas/borrar")
    public String borrar(@RequestParam Long id, RedirectAttributes redirect) {
        archivo.conFichero(id).ifPresent(temporada -> redirect.addFlashAttribute("mensaje",
                "Borrada la temporada " + temporada.getTemporada() + "."));
        archivo.borrar(id);
        return "redirect:/temporadas";
    }

    /**
     * Descarga del excel guardado. Sirve para comprobar cuál es el que hay
     * dentro: el nombre del fichero no siempre lo dice.
     */
    @GetMapping("/temporadas/descargar/{id}")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        TemporadaGuardada temporada = archivo.conFichero(id).orElse(null);
        if (temporada == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(TIPO_XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(temporada.getNombreFichero()).build().toString())
                .body(temporada.getExcel());
    }

    private Map<String, ClienteConfig> clientesConPedido() {
        Map<String, ClienteConfig> conPedido = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> {
            if (config.isPedidoCliente()) {
                conPedido.put(clave, config);
            }
        });
        return conPedido;
    }

    private static boolean esExcel(String nombre) {
        return nombre != null && nombre.toLowerCase().endsWith(".xlsx");
    }

    private static String error(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("mensajeError", mensaje);
        return "redirect:/temporadas";
    }
}
