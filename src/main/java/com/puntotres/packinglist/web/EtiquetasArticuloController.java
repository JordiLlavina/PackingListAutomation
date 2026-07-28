package com.puntotres.packinglist.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import com.puntotres.packinglist.service.etiquetasarticulo.EtiquetasArticuloGenerationService;
import com.puntotres.packinglist.service.etiquetasarticulo.ExcelEtiquetasArticulo;
import com.puntotres.packinglist.service.etiquetasarticulo.GeneradorEtiquetasArticuloCliente;
import com.puntotres.packinglist.service.etiquetasarticulo.ResultadoEtiquetasArticulo;

/**
 * Flujo de etiquetas de artículo, en dos pantallas: elegir cliente y subir su
 * excel de pedido → descargar los excels generados.
 *
 * No tiene nada que ver con el asistente de packing lists: no necesita JSON,
 * ni envío en curso, ni pesos. Su única entrada es el excel de pedido.
 */
@Controller
public class EtiquetasArticuloController {

    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /**
     * Caracteres que no deben acabar en un nombre de fichero que nosotros
     * escribimos como segmento de URL ({@code /descargar/{nombreFichero}}) o
     * como nombre de entrada de ZIP: separadores de ruta y el resto de
     * caracteres que Windows prohíbe en un nombre de fichero.
     *
     * A propósito NO se incluye el espacio (a diferencia del patrón de
     * PackingListController.descargarTodo, que sanea un nombre de ZIP
     * sintético donde la legibilidad de los espacios da igual): aquí
     * nombreFichero es el nombre real y con espacios que espera el cliente
     * ("AMI CODE BARRE H26 MOROCCO.xlsx"), y componerlo con AmiEtiquetasArticuloGenerador
     * no lo sanea porque "Made in" y la temporada tecleada por el usuario
     * pueden traer un carácter conflictivo sin que sea culpa de un espacio.
     */
    private static final Pattern CARACTERES_INSEGUROS = Pattern.compile("[\\\\/:*?\"<>|]+");

    private final EtiquetasArticuloGenerationService etiquetasArticuloService;
    private final ClientesProperties clientesProperties;
    private final EtiquetasArticuloEnCurso enCurso;

    public EtiquetasArticuloController(
            EtiquetasArticuloGenerationService etiquetasArticuloService,
            ClientesProperties clientesProperties,
            EtiquetasArticuloEnCurso enCurso) {
        this.etiquetasArticuloService = etiquetasArticuloService;
        this.clientesProperties = clientesProperties;
        this.enCurso = enCurso;
    }

    // --- Paso 1: elegir cliente y subir el pedido ---

    @GetMapping("/etiquetas-articulo")
    public String entrada(Model model) {
        model.addAttribute("clientes", vistaDeClientes());
        return "etiquetas-articulo";
    }

    @PostMapping("/etiquetas-articulo/generar")
    public String generar(@RequestParam String cliente,
                          @RequestParam(required = false) String temporada,
                          @RequestParam MultipartFile pedido,
                          RedirectAttributes redirect) {
        GeneradorEtiquetasArticuloCliente generador =
                etiquetasArticuloService.generadorPara(cliente).orElse(null);
        if (generador == null) {
            redirect.addFlashAttribute("error",
                    "El cliente '" + cliente + "' no tiene etiquetas de artículo implementadas");
            return "redirect:/etiquetas-articulo";
        }
        if (pedido == null || pedido.isEmpty()) {
            redirect.addFlashAttribute("error", "Falta el " + generador.tituloCampoPedido());
            return "redirect:/etiquetas-articulo";
        }

        ResultadoEtiquetasArticulo resultado;
        try {
            resultado = generador.generar(pedido.getBytes(), temporada);
        } catch (IOException | RuntimeException e) {
            // Un excel que no es el que toca (sin hoja EAN, sin columna
            // EAN13) llega aquí: es lo único que bloquea, porque no hay nada
            // útil que generar.
            redirect.addFlashAttribute("error",
                    "No se pudieron generar las etiquetas: " + e.getMessage());
            return "redirect:/etiquetas-articulo";
        }
        if (resultado.getExcels().isEmpty()) {
            redirect.addFlashAttribute("error", "El excel de pedido no tiene ninguna fila "
                    + "utilizable: no se ha generado nada");
            return "redirect:/etiquetas-articulo";
        }

        enCurso.reiniciar();
        enCurso.setClaveCliente(cliente);
        // Se sanea el nombre de fichero aquí, en el único punto de entrada a
        // la sesión: a partir de aquí tanto la pantalla de resultados (que
        // construye la URL de descarga) como descargar() y descargarTodo()
        // pueden confiar en que nombreFichero no trae "/" ni similares.
        resultado.getExcels().forEach(excel -> enCurso.getExcels().add(sanear(excel)));
        enCurso.getAvisos().addAll(resultado.getAvisos());
        return "redirect:/etiquetas-articulo/resultados";
    }

    // --- Paso 2: resultados y descargas ---

    @GetMapping("/etiquetas-articulo/resultados")
    public String resultados(Model model) {
        if (enCurso.estaVacio()) {
            return "redirect:/etiquetas-articulo";
        }
        model.addAttribute("excels", enCurso.getExcels());
        model.addAttribute("avisos", enCurso.getAvisos());
        model.addAttribute("cliente", enCurso.getClaveCliente());
        return "etiquetas-articulo-resultados";
    }

    @GetMapping("/etiquetas-articulo/descargar/{nombreFichero}")
    public ResponseEntity<byte[]> descargar(@PathVariable String nombreFichero) {
        return enCurso.getExcels().stream()
                .filter(excel -> excel.nombreFichero().equals(nombreFichero))
                .findFirst()
                .map(excel -> ResponseEntity.ok()
                        .contentType(TIPO_XLSX)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                                .attachment().filename(excel.nombreFichero()).build().toString())
                        .body(excel.contenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/etiquetas-articulo/descargar-todo")
    public ResponseEntity<byte[]> descargarTodo() throws IOException {
        if (enCurso.estaVacio()) {
            return ResponseEntity.notFound().build();
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (ExcelEtiquetasArticulo excel : enCurso.getExcels()) {
                zip.putNextEntry(new ZipEntry(excel.nombreFichero()));
                zip.write(excel.contenido());
                zip.closeEntry();
            }
        }
        String nombreZip = ("CODE_BARRE_" + enCurso.getClaveCliente() + ".zip")
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                        .attachment().filename(nombreZip).build().toString())
                .body(salida.toByteArray());
    }

    @GetMapping("/etiquetas-articulo/nuevo")
    public String nuevo() {
        enCurso.reiniciar();
        return "redirect:/etiquetas-articulo";
    }

    // --- Internos ---

    /**
     * Catálogo para el desplegable: todos los clientes, marcando los que aún
     * no tienen etiquetas de artículo implementadas para que la pantalla los
     * deshabilite en vez de esconderlos.
     */
    private Map<String, Map<String, String>> vistaDeClientes() {
        Map<String, Map<String, String>> vista = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> {
            GeneradorEtiquetasArticuloCliente generador =
                    etiquetasArticuloService.generadorPara(clave).orElse(null);
            Map<String, String> datos = new LinkedHashMap<>();
            datos.put("nombre", nombreDe(config, clave));
            datos.put("disponible", String.valueOf(generador != null));
            datos.put("tituloCampoPedido",
                    generador != null ? generador.tituloCampoPedido() : "");
            datos.put("placeholderTemporada", config.getPlaceholderTemporada() != null
                    ? config.getPlaceholderTemporada() : "");
            vista.put(clave, datos);
        });
        return vista;
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank()
                ? config.getNombre() : clave;
    }

    /**
     * Copia el excel con su nombre de fichero saneado. AmiEtiquetasArticuloGenerador
     * compone nombreFichero con la columna "Made in" del pedido y la
     * temporada tecleada por el usuario, sin sanear: ninguno de los dos es un
     * dato que controlemos, y ambos acaban en sitios sensibles que sí
     * escribimos nosotros ({@code /descargar/{nombreFichero}} y el nombre de
     * entrada del ZIP). No se toca el generador (no es su responsabilidad
     * defenderse de cómo se usa su salida); se sanea aquí, en el borde.
     */
    private static ExcelEtiquetasArticulo sanear(ExcelEtiquetasArticulo excel) {
        String nombreSeguro = CARACTERES_INSEGUROS.matcher(excel.nombreFichero())
                .replaceAll("_");
        return new ExcelEtiquetasArticulo(excel.descripcion(), nombreSeguro, excel.contenido());
    }
}
