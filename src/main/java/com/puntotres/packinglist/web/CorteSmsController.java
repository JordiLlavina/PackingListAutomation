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
import com.puntotres.packinglist.service.corte.CorteSmsService;
import com.puntotres.packinglist.service.corte.CorteSmsService.ResultadoSms;

/**
 * Hojas de corte de las SMS (muestras de venta): entrada (cliente, temporada
 * escrita a mano y el excel de pedido de SMS) → descarga. Sin tabla de pieles
 * en medio: el excel de SMS ya trae la piel, su color y el forro de cada fila.
 * Sin Word de fotos ni carpeta de fotos: la imagen de cada hoja es el croquis
 * del propio excel.
 *
 * Nada se guarda, tampoco la temporada: solo nombra el documento ("APC FALL
 * 27 - Hojas de Corte.xlsx").
 */
@Controller
public class CorteSmsController {

    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /** Un cliente del desplegable; los que no tienen lector salen apagados, «en desarrollo». */
    public record OpcionCliente(String nombre, boolean disponible) {
    }

    private final CorteSmsService servicio;
    private final ClientesProperties clientesProperties;
    private final CorteSmsEnCurso enCurso;

    public CorteSmsController(CorteSmsService servicio, ClientesProperties clientesProperties,
                              CorteSmsEnCurso enCurso) {
        this.servicio = servicio;
        this.clientesProperties = clientesProperties;
        this.enCurso = enCurso;
    }

    @GetMapping("/documentos-corte/sms")
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
        return "documentos-corte-sms";
    }

    @PostMapping("/documentos-corte/sms/generar")
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
            return error(redirect, "Las hojas de corte de SMS de " + nombreCliente + " todavía están en desarrollo");
        }
        if (temporada == null || temporada.isBlank()) {
            return error(redirect, "Escribe la temporada: va en el nombre del documento");
        }
        if (excel == null || excel.isEmpty()) {
            return error(redirect, "Sube el excel de pedido de SMS");
        }
        try {
            ResultadoSms resultado = servicio.generar(cliente, nombreCliente, temporada, excel.getBytes());
            enCurso.guardar(nombreCliente, temporada.trim(), resultado);
        } catch (IllegalArgumentException e) {
            return error(redirect, "El excel " + excel.getOriginalFilename() + " " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            return error(redirect, "No se ha podido leer el excel " + excel.getOriginalFilename()
                    + ": revisa que sea un .xlsx (" + (e.getMessage() != null ? e.getMessage()
                    : e.getClass().getSimpleName()) + ")");
        }
        return "redirect:/documentos-corte/sms/resultados";
    }

    @GetMapping("/documentos-corte/sms/resultados")
    public String resultados(Model model) {
        if (enCurso.getResultado() == null) {
            return "redirect:/documentos-corte/sms";
        }
        model.addAttribute("cliente", enCurso.getCliente());
        model.addAttribute("temporada", enCurso.getTemporada());
        model.addAttribute("resultado", enCurso.getResultado());
        return "documentos-corte-sms-resultados";
    }

    @GetMapping("/documentos-corte/sms/descargar")
    public ResponseEntity<byte[]> descargar() {
        ResultadoSms resultado = enCurso.getResultado();
        if (resultado == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(TIPO_XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(resultado.nombreFichero(), StandardCharsets.UTF_8).build().toString())
                .body(resultado.excel());
    }

    private static String error(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("error", mensaje);
        return "redirect:/documentos-corte/sms";
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank() ? config.getNombre() : clave;
    }
}
