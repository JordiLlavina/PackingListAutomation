package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.service.etiquetasarticulo.EtiquetasSmsService;
import com.puntotres.packinglist.service.etiquetasarticulo.EtiquetasSmsService.ResultadoEtiquetasSms;

/**
 * Etiquetas de artículo de las SMS (muestras de venta): entrada (cliente,
 * temporada escrita a mano y el excel de pedido de SMS) → descarga del Word.
 * Se llega desde el menú de etiquetas de artículo ({@code /etiquetas-articulo},
 * en MenuController). Es el mismo excel que el de las hojas de corte de SMS
 * (CorteSmsController), y como allí no se guarda nada, tampoco la temporada.
 */
@Controller
public class EtiquetasSmsController {

    private static final MediaType TIPO_DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    /** Un cliente del desplegable; los que no tienen lector salen apagados, «en desarrollo». */
    public record OpcionCliente(String nombre, boolean disponible) {
    }

    private final EtiquetasSmsService servicio;
    private final ClientesProperties clientesProperties;
    private final EtiquetasSmsEnCurso enCurso;

    public EtiquetasSmsController(EtiquetasSmsService servicio, ClientesProperties clientesProperties,
                                  EtiquetasSmsEnCurso enCurso) {
        this.servicio = servicio;
        this.clientesProperties = clientesProperties;
        this.enCurso = enCurso;
    }

    @GetMapping("/etiquetas-articulo-sms")
    public String entrada(Model model) {
        Map<String, OpcionCliente> clientes = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) ->
                clientes.put(clave, new OpcionCliente(nombreDe(config, clave), servicio.admite(clave))));
        model.addAttribute("clientes", clientes);
        // Arranca en el primero que funciona, no en el primero de la lista; tras
        // un error, en el que se había elegido.
        if (!model.containsAttribute("clienteElegido")) {
            model.addAttribute("clienteElegido", clientes.entrySet().stream()
                    .filter(entrada -> entrada.getValue().disponible())
                    .map(Map.Entry::getKey).findFirst().orElse(null));
        }
        return "etiquetas-articulo-sms";
    }

    @PostMapping("/etiquetas-articulo-sms/generar")
    public String generar(@RequestParam String cliente,
                          @RequestParam(required = false) String temporada,
                          @RequestParam(required = false) MultipartFile excel,
                          RedirectAttributes redirect) {
        // Si algo falla, la pantalla vuelve con lo elegido y lo escrito; el fichero hay que volver a elegirlo.
        redirect.addFlashAttribute("clienteElegido", cliente);
        redirect.addFlashAttribute("temporada", temporada);
        String nombreCliente = clientesProperties.clientePara(cliente)
                .map(config -> nombreDe(config, cliente)).orElse(cliente);
        if (!servicio.admite(cliente)) {
            return error(redirect, "Las etiquetas de SMS de " + nombreCliente + " todavía están en desarrollo");
        }
        if (temporada == null || temporada.isBlank()) {
            return error(redirect, "Escribe la temporada: va impresa en cada etiqueta");
        }
        if (excel == null || excel.isEmpty()) {
            return error(redirect, "Sube el excel de pedido de SMS");
        }
        try {
            ResultadoEtiquetasSms resultado = servicio.generar(cliente, nombreCliente, temporada,
                    excel.getBytes());
            enCurso.guardar(nombreCliente, temporada.trim(), resultado);
        } catch (IllegalArgumentException e) {
            return error(redirect, "El excel " + excel.getOriginalFilename() + " " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            return error(redirect, "No se ha podido leer el excel " + excel.getOriginalFilename()
                    + ": revisa que sea un .xlsx (" + (e.getMessage() != null ? e.getMessage()
                    : e.getClass().getSimpleName()) + ")");
        }
        return "redirect:/etiquetas-articulo-sms/resultados";
    }

    @GetMapping("/etiquetas-articulo-sms/resultados")
    public String resultados(Model model) {
        if (enCurso.getResultado() == null) {
            return "redirect:/etiquetas-articulo-sms";
        }
        model.addAttribute("cliente", enCurso.getCliente());
        model.addAttribute("temporada", enCurso.getTemporada());
        model.addAttribute("resultado", enCurso.getResultado());
        return "etiquetas-articulo-sms-resultados";
    }

    @GetMapping("/etiquetas-articulo-sms/descargar")
    public ResponseEntity<byte[]> descargar() {
        ResultadoEtiquetasSms resultado = enCurso.getResultado();
        if (resultado == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(TIPO_DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(resultado.nombreFichero(), StandardCharsets.UTF_8).build().toString())
                .body(resultado.word());
    }

    private static String error(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("error", mensaje);
        return "redirect:/etiquetas-articulo-sms";
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank() ? config.getNombre() : clave;
    }
}
