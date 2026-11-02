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
import com.puntotres.packinglist.persistence.TipoExcelTemporada;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos.ResumenAlta;

/**
 * Pantalla del archivo de temporadas: guardar el excel de una temporada una
 * sola vez y elegirla luego en la entrada del envío, del corte o del
 * catálogo.
 *
 * Para los clientes que trabajan con excel de pedido ({@code pedido-cliente}
 * en application.yml: AMI, APC) el fichero es ese pedido, el mismo documento
 * durante toda la temporada. Para los demás es el excel de producción de
 * Puntotres, que es de donde salen sus modelos. El tipo lo fija el cliente,
 * no lo elige el usuario.
 *
 * Al guardar una temporada con su excel, sus artículos entran solos en el
 * catálogo de modelos y un aviso emergente dice qué ha entrado. Un excel que
 * no se deja leer no impide guardar la temporada: se guarda y se avisa de
 * que el catálogo no ha cogido nada (regla del proyecto: no bloquear por
 * algo que se arregla después).
 */
@Controller
public class TemporadasController {

    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ArchivoTemporadas archivo;
    private final ClientesProperties clientesProperties;
    private final CatalogoModelos catalogo;

    public TemporadasController(ArchivoTemporadas archivo, ClientesProperties clientesProperties,
                                CatalogoModelos catalogo) {
        this.archivo = archivo;
        this.clientesProperties = clientesProperties;
        this.catalogo = catalogo;
    }

    @GetMapping("/temporadas")
    public String temporadas(Model model) {
        model.addAttribute("temporadas", archivo.todas());
        model.addAttribute("clientes", clientesProperties.getClientes());
        model.addAttribute("tiposExcel", tiposPorCliente());
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
        if (config == null) {
            return error(redirect, "Cliente desconocido: " + cliente + ".");
        }
        TipoExcelTemporada tipo = tipoDe(config);
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
                return error(redirect, rotuloDe(tipo) + " es un excel (.xlsx): '"
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
        // ningún excel, sin decir por qué.
        if (esAlta && excel == null) {
            return error(redirect, "Sube " + rotuloDe(tipo).toLowerCase() + " de la temporada: es lo que se "
                    + "guarda para no tener que buscarlo en cada envío.");
        }

        TemporadaGuardada guardada = archivo.guardar(id, cliente, nombreTemporada, nombreFichero, excel, tipo);
        redirect.addFlashAttribute("mensaje", "Guardada la temporada " + nombreTemporada
                + " de " + config.getNombre() + ".");
        if (excel != null) {
            incorporarAlCatalogo(guardada, redirect);
        }
        return "redirect:/temporadas";
    }

    /**
     * Los artículos del excel entran al catálogo. Si el excel no se deja
     * leer, la temporada ya está guardada y se avisa: el aviso dice lo que ha
     * pasado y el catálogo se puede rellenar a mano o sustituyendo el excel.
     */
    private void incorporarAlCatalogo(TemporadaGuardada guardada, RedirectAttributes redirect) {
        try {
            ResumenAlta alta = catalogo.incorporarTemporada(guardada);
            redirect.addFlashAttribute("altaCatalogo", alta);
            redirect.addFlashAttribute("altaTemporada", guardada.getTemporada());
            redirect.addFlashAttribute("altaTemporadaId", guardada.getId());
            redirect.addFlashAttribute("altaCliente", guardada.getCliente());
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("aviso", "Guardada la temporada, pero no se han podido extraer sus "
                    + "artículos al catálogo: " + (e.getMessage() != null ? e.getMessage()
                    : "el excel no tiene el formato esperado") + ".");
        }
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

    /** Clave de cliente → qué excel guarda su temporada, para que la pantalla rotule el fichero. */
    private Map<String, TipoExcelTemporada> tiposPorCliente() {
        Map<String, TipoExcelTemporada> tipos = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> tipos.put(clave, tipoDe(config)));
        return tipos;
    }

    private static TipoExcelTemporada tipoDe(ClienteConfig config) {
        return config.isPedidoCliente() ? TipoExcelTemporada.PEDIDO : TipoExcelTemporada.PRODUCCION;
    }

    private static String rotuloDe(TipoExcelTemporada tipo) {
        return tipo == TipoExcelTemporada.PEDIDO ? "El excel de pedido del cliente"
                : "El excel de producción de Puntotres";
    }

    private static boolean esExcel(String nombre) {
        return nombre != null && nombre.toLowerCase().endsWith(".xlsx");
    }

    private static String error(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("mensajeError", mensaje);
        return "redirect:/temporadas";
    }
}
