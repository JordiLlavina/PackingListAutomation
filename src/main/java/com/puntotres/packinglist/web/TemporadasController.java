package com.puntotres.packinglist.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
import com.puntotres.packinglist.config.CatalogoClientes;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.FicheroExcel;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;

/**
 * Pantalla del archivo de temporadas: aquí se guardan, una vez, los FICHEROS
 * de una temporada (sus excels), y de aquí sale el desplegable de la entrada
 * del envío, del corte, de las etiquetas y de la carga de modelos.
 *
 * Solo guarda ficheros. Hasta noviembre de 2026 guardar una temporada metía
 * además sus modelos y sus fotos en el catálogo, sin revisión; el usuario lo
 * quitó: los modelos y sus fotos entran por la Carga de modelos
 * (/carga-modelos), donde se revisan uno a uno.
 *
 * Cada temporada guarda dos excels, y cuál es obligatorio lo decide el
 * cliente ({@code pedido-cliente} en application.yml):
 * <ul>
 *   <li>AMI y APC: el excel de PEDIDO del cliente es obligatorio y el de
 *       PRODUCCIÓN de Puntotres, opcional (trae lo que el pedido no dice: el
 *       nombre de la piel, y en AMI el del bolso).</li>
 *   <li>Los genéricos no tienen excel de pedido: el de producción es el
 *       obligatorio.</li>
 * </ul>
 */
@Controller
public class TemporadasController {

    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    static final String ROTULO_PEDIDO = "Excel de Pedido de Cliente";
    static final String ROTULO_PRODUCCION = "Excel de Producción de Puntotres";

    private final ArchivoTemporadas archivo;
    private final CatalogoClientes catalogoClientes;
    private final CatalogoModelos catalogo;

    public TemporadasController(ArchivoTemporadas archivo, CatalogoClientes catalogoClientes,
                                CatalogoModelos catalogo) {
        this.archivo = archivo;
        this.catalogoClientes = catalogoClientes;
        this.catalogo = catalogo;
    }

    /**
     * Con {@code editar}, el formulario de arriba sale con esa temporada
     * cargada: corregir el nombre, adjuntar el excel que falta o sustituir
     * uno, todo desde el mismo sitio que el alta.
     */
    @GetMapping("/temporadas")
    public String temporadas(@RequestParam(required = false) Long editar, Model model) {
        model.addAttribute("temporadas", archivo.todas());
        model.addAttribute("clientes", catalogoClientes.getClientes());
        model.addAttribute("conPedido", conPedidoPorCliente());
        archivo.todas().stream()
                .filter(temporada -> temporada.id().equals(editar))
                .findFirst()
                .ifPresent(temporada -> model.addAttribute("editando", temporada));
        return "temporadas";
    }

    /**
     * Alta (sin {@code id}) o corrección (con él). Un fichero vacío significa
     * "deja el que ya tenía": se corrige el nombre o se adjunta el otro excel
     * sin volver a buscar en el disco el que ya está guardado.
     */
    @PostMapping("/temporadas")
    public String guardar(@RequestParam(required = false) Long id,
                          @RequestParam String cliente,
                          @RequestParam String temporada,
                          @RequestParam(required = false) MultipartFile pedido,
                          @RequestParam(required = false) MultipartFile produccion,
                          RedirectAttributes redirect) {
        ClienteConfig config = catalogoClientes.clientePara(cliente).orElse(null);
        if (config == null) {
            return error(redirect, "Cliente desconocido: " + cliente + ".", id);
        }
        boolean clienteConPedido = config.isPedidoCliente();
        String nombreTemporada = temporada == null ? "" : temporada.trim();
        if (nombreTemporada.isBlank()) {
            return error(redirect, "La temporada necesita un nombre: es el que se escribe "
                    + "en los documentos del cliente (H26, FALL26...).", id);
        }
        if (archivo.yaExiste(cliente, nombreTemporada, id)) {
            return error(redirect, "Ya hay una temporada " + nombreTemporada + " guardada de "
                    + config.getNombre() + ". Edita esa en vez de crear otra igual.", id);
        }
        Optional<TemporadaGuardada> anterior = archivo.conFichero(id);
        if (id != null && anterior.isEmpty()) {
            return error(redirect, "La temporada que se estaba editando ya no existe.", null);
        }

        FicheroExcel excelPedido;
        FicheroExcel excelProduccion;
        try {
            excelPedido = excelSubido(pedido, ROTULO_PEDIDO);
            excelProduccion = excelSubido(produccion, ROTULO_PRODUCCION);
        } catch (IllegalArgumentException e) {
            return error(redirect, e.getMessage(), id);
        }
        if (!clienteConPedido && excelPedido != null) {
            return error(redirect, config.getNombre() + " no trabaja con excel de pedido: su temporada "
                    + "se guarda con el " + ROTULO_PRODUCCION.toLowerCase() + ".", id);
        }
        // El obligatorio tiene que quedar guardado: subido ahora o de antes.
        boolean tendraPedido = excelPedido != null
                || (clienteConPedido && anterior.map(TemporadaGuardada::tienePedido).orElse(false));
        boolean tendraProduccion = excelProduccion != null
                || anterior.map(TemporadaGuardada::tieneProduccion).orElse(false);
        if (clienteConPedido && !tendraPedido) {
            return error(redirect, "Sube el " + ROTULO_PEDIDO.toLowerCase() + ": para "
                    + config.getNombre() + " es obligatorio, es el que leen el packing list y las etiquetas.", id);
        }
        if (!clienteConPedido && !tendraProduccion) {
            return error(redirect, "Sube el " + ROTULO_PRODUCCION.toLowerCase() + ": para "
                    + config.getNombre() + " es obligatorio, porque no tiene excel de pedido.", id);
        }

        // Si una temporada cambia de cliente, sus vínculos con los modelos del
        // cliente viejo no pueden quedarse: se sueltan. Los del nuevo se hacen
        // en la carga de modelos, como todos.
        boolean cambiaDeCliente = anterior.map(vieja -> !vieja.getCliente().equals(cliente)).orElse(false);
        Long guardadaId = archivo.guardar(id, cliente, nombreTemporada, excelPedido, excelProduccion).getId();
        TemporadaGuardada recienGuardada = archivo.conFichero(guardadaId).orElseThrow();
        if (!clienteConPedido && recienGuardada.tienePedido()) {
            // Ha pasado a un genérico: guardado, su packing list lo leería como pedido.
            archivo.quitarPedido(guardadaId);
        }
        if (cambiaDeCliente) {
            catalogo.desvincularTemporada(guardadaId);
        }
        redirect.addFlashAttribute("mensaje", "Guardada la temporada " + nombreTemporada
                + " de " + config.getNombre() + ".");
        // Para el enlace «Cargar sus modelos» del mensaje.
        redirect.addFlashAttribute("guardadaCliente", cliente);
        redirect.addFlashAttribute("guardadaId", guardadaId);
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
     * Descarga de un excel guardado ({@code pedido} si no se dice). Sirve para
     * comprobar cuál es el que hay dentro: el nombre del fichero no siempre
     * lo dice.
     */
    @GetMapping({"/temporadas/descargar/{id}", "/temporadas/descargar/{id}/{cual}"})
    public ResponseEntity<byte[]> descargar(@PathVariable Long id,
                                            @PathVariable(required = false) String cual) {
        TemporadaGuardada temporada = archivo.conFichero(id).orElse(null);
        boolean deProduccion = "produccion".equals(cual);
        byte[] excel = temporada == null ? null
                : deProduccion ? temporada.getExcelProduccion() : temporada.getExcelPedido();
        if (excel == null) {
            return ResponseEntity.notFound().build();
        }
        String nombre = deProduccion ? temporada.getNombreFicheroProduccion() : temporada.getNombreFicheroPedido();
        return ResponseEntity.ok()
                .contentType(TIPO_XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(nombre).build().toString())
                .body(excel);
    }

    /** Clave de cliente → si trabaja con excel de pedido, para que la pantalla pinte el obligatorio. */
    private Map<String, Boolean> conPedidoPorCliente() {
        Map<String, Boolean> conPedido = new LinkedHashMap<>();
        catalogoClientes.getClientes().forEach((clave, config) -> conPedido.put(clave, config.isPedidoCliente()));
        return conPedido;
    }

    /** El excel subido, o null si no se ha subido ninguno. Lanza si no es un .xlsx o no se lee. */
    private static FicheroExcel excelSubido(MultipartFile fichero, String rotulo) {
        if (fichero == null || fichero.isEmpty()) {
            return null;
        }
        String nombre = fichero.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("El " + rotulo.toLowerCase() + " es un excel (.xlsx): '"
                    + nombre + "' no lo es.");
        }
        try {
            return new FicheroExcel(nombre, fichero.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("No se ha podido leer el " + rotulo.toLowerCase()
                    + " subido: " + e.getMessage());
        }
    }

    /** Vuelve al formulario donde se estaba: el de editar esa temporada o el de alta. */
    private static String error(RedirectAttributes redirect, String mensaje, Long id) {
        redirect.addFlashAttribute("mensajeError", mensaje);
        return id == null ? "redirect:/temporadas" : "redirect:/temporadas?editar=" + id;
    }
}
