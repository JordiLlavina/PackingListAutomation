package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.CatalogoClientes;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.corte.CorteSmsService;
import com.puntotres.packinglist.service.corte.CorteSmsService.ResultadoSms;
import com.puntotres.packinglist.service.corte.ImagenesPorFila;
import com.puntotres.packinglist.service.corte.LecturaSmsIa;
import com.puntotres.packinglist.service.corte.LectorSmsIa;
import com.puntotres.packinglist.service.corte.LineaSmsRevisada;
import com.puntotres.packinglist.service.corte.NovedadesSms;
import com.puntotres.packinglist.service.corte.NovedadesSms.ArticuloNuevo;
import com.puntotres.packinglist.service.corte.NovedadesSms.ColorNuevo;
import com.puntotres.packinglist.service.corte.NovedadesSms.ModeloNuevo;
import com.puntotres.packinglist.service.corte.NovedadesSms.Novedades;
import com.puntotres.packinglist.service.corte.NovedadesSms.PielNueva;

/**
 * Hojas de corte de las SMS (muestras de venta). Dos caminos según el cliente:
 * <ul>
 *   <li><b>APC</b>, con su lector ({@code ApcSmsExcel}): entrada → descarga,
 *       como siempre.</li>
 *   <li><b>Los demás</b>: el excel o PDF se lee con IA ({@link LectorSmsIa}) y
 *       pasa por una pantalla de revisión editable antes de generar; arriba,
 *       una alerta a la vista si el documento parece de otro cliente o no es
 *       un pedido de SMS (avisa, no bloquea).</li>
 * </ul>
 * Al pie de la descarga, en los dos, los modelos, pieles y colores que el
 * sistema no tiene ({@link NovedadesSms}), editables y con «Entrar en el
 * Sistema». Generar no guarda nada; la temporada solo nombra el documento.
 */
@Controller
public class CorteSmsController {

    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /** Un cliente del desplegable: los que tienen lector propio no pasan por la IA. */
    public record OpcionCliente(String nombre, boolean conLector) {
    }

    private final CorteSmsService servicio;
    private final LectorSmsIa lectorIa;
    private final NovedadesSms novedadesSms;
    private final CatalogoClientes catalogoClientes;
    private final CorteSmsEnCurso enCurso;

    public CorteSmsController(CorteSmsService servicio, LectorSmsIa lectorIa, NovedadesSms novedadesSms,
                              CatalogoClientes catalogoClientes, CorteSmsEnCurso enCurso) {
        this.servicio = servicio;
        this.lectorIa = lectorIa;
        this.novedadesSms = novedadesSms;
        this.catalogoClientes = catalogoClientes;
        this.enCurso = enCurso;
    }

    // --- Entrada ---

    @GetMapping("/documentos-corte/sms")
    public String entrada(Model model) {
        Map<String, OpcionCliente> clientes = new LinkedHashMap<>();
        catalogoClientes.getClientes().forEach((clave, config) ->
                clientes.put(clave, new OpcionCliente(nombreDe(config, clave), servicio.admite(clave))));
        model.addAttribute("clientes", clientes);
        // Arranca en el primero con lector propio; tras un error, en el que se había elegido.
        if (!model.containsAttribute("clienteElegido")) {
            model.addAttribute("clienteElegido", clientes.entrySet().stream()
                    .filter(entrada -> entrada.getValue().conLector())
                    .map(Map.Entry::getKey).findFirst()
                    .orElse(clientes.keySet().stream().findFirst().orElse(null)));
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
        if (catalogoClientes.clientePara(cliente).isEmpty()) {
            return error(redirect, "Elige un cliente de la lista");
        }
        String nombreCliente = nombreDe(cliente);
        if (temporada == null || temporada.isBlank()) {
            return error(redirect, "Escribe la temporada: va en el nombre del documento");
        }
        if (excel == null || excel.isEmpty()) {
            return error(redirect, servicio.admite(cliente) ? "Sube el excel de pedido de SMS"
                    : "Sube el pedido de SMS (excel o PDF)");
        }
        byte[] bytes;
        try {
            bytes = excel.getBytes();
        } catch (IOException e) {
            return error(redirect, "No se ha podido leer el fichero " + excel.getOriginalFilename());
        }
        if (!servicio.admite(cliente)) {
            return leerConIa(cliente, nombreCliente, temporada.trim(), excel.getOriginalFilename(), bytes, redirect);
        }
        try {
            ResultadoSms resultado = servicio.generar(cliente, nombreCliente, temporada, bytes);
            enCurso.olvidarRevision();
            guardarResultado(cliente, nombreCliente, temporada.trim(), resultado);
        } catch (IllegalArgumentException e) {
            return error(redirect, "El excel " + excel.getOriginalFilename() + " " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            return error(redirect, "No se ha podido leer el excel " + excel.getOriginalFilename()
                    + ": revisa que sea un .xlsx (" + (e.getMessage() != null ? e.getMessage()
                    : e.getClass().getSimpleName()) + ")");
        }
        return "redirect:/documentos-corte/sms/resultados";
    }

    private String leerConIa(String cliente, String nombreCliente, String temporada, String nombreFichero,
                             byte[] bytes, RedirectAttributes redirect) {
        LecturaSmsIa lectura;
        try {
            lectura = lectorIa.leer(bytes, nombreFichero, cliente, nombreCliente);
        } catch (LectorSmsIa.LecturaFallida e) {
            return error(redirect, e.getMessage());
        }
        if (lectura.lineas().isEmpty()) {
            return error(redirect, "La IA no ha encontrado ninguna línea de muestras en " + nombreFichero
                    + (lectura.avisos().isEmpty() ? "" : ": " + String.join(" ", lectura.avisos())));
        }
        List<String> avisosImagenes = new ArrayList<>();
        var imagenes = ImagenesPorFila.de(bytes, avisosImagenes);
        enCurso.empezarRevision(cliente, nombreCliente, temporada, nombreFichero, lectura, imagenes, avisosImagenes);
        return "redirect:/documentos-corte/sms/revision";
    }

    // --- Revisión (solo los leídos con IA) ---

    @GetMapping("/documentos-corte/sms/revision")
    public String revision(Model model) {
        if (!enCurso.enRevision()) {
            return "redirect:/documentos-corte/sms";
        }
        LecturaSmsIa lectura = enCurso.getLectura();
        model.addAttribute("cliente", enCurso.getCliente());
        model.addAttribute("temporada", enCurso.getTemporada());
        model.addAttribute("nombreFichero", enCurso.getNombreFichero());
        model.addAttribute("formulario", RevisionSmsForm.desde(enCurso.getLineas()));
        model.addAttribute("avisos", enCurso.getAvisosLectura());
        model.addAttribute("alertas", alertas(lectura, enCurso.getCliente()));
        // "hoja#fila" de las filas del excel que llevan imagen: la tabla lo dice en cada línea.
        java.util.Set<String> conImagen = new java.util.HashSet<>();
        enCurso.getImagenes().forEach((hoja, filas) -> filas.keySet().forEach(fila -> conImagen.add(hoja + "#" + fila)));
        model.addAttribute("conImagen", conImagen);
        return "documentos-corte-sms-revision";
    }

    /** Las alertas a la vista de la revisión: documento de otro cliente, o que no es un pedido de SMS. */
    static List<String> alertas(LecturaSmsIa lectura, String nombreCliente) {
        List<String> alertas = new ArrayList<>();
        if (Boolean.FALSE.equals(lectura.esDelCliente())) {
            alertas.add("Este documento parece "
                    + (lectura.clienteDetectado().isEmpty() ? "de otro cliente" : "de " + lectura.clienteDetectado())
                    + ", no de " + nombreCliente
                    + (lectura.motivoCliente().isEmpty() ? "" : ": " + lectura.motivoCliente())
                    + ". Revisa que hayas elegido el cliente bueno antes de cortar.");
        }
        if (Boolean.FALSE.equals(lectura.esPedidoDeSms())) {
            alertas.add("Esto no parece un pedido de muestras (SMS)"
                    + (lectura.motivoSms().isEmpty() ? "" : ": " + lectura.motivoSms()) + ".");
        }
        return alertas;
    }

    @PostMapping("/documentos-corte/sms/revision")
    public String generarDesdeRevision(@ModelAttribute RevisionSmsForm formulario, RedirectAttributes redirect) {
        if (!enCurso.enRevision()) {
            return "redirect:/documentos-corte/sms";
        }
        List<LineaSmsRevisada> lineas = formulario.aLineas();
        enCurso.actualizarLineas(lineas);
        List<String> errores = new ArrayList<>();
        for (LineaSmsRevisada linea : lineas) {
            if (!linea.errores().isEmpty()) {
                errores.add((linea.referencia().isEmpty() ? linea.donde() : linea.referencia() + " ("
                        + linea.donde() + ")") + ": " + String.join(", ", linea.errores()));
            }
        }
        if (!errores.isEmpty()) {
            redirect.addFlashAttribute("errores", errores);
            return "redirect:/documentos-corte/sms/revision";
        }
        try {
            ResultadoSms resultado = servicio.generarDesdeRevision(enCurso.getClaveCliente(), enCurso.getCliente(),
                    enCurso.getTemporada(), lineas, enCurso.getImagenes(), enCurso.getAvisosLectura());
            guardarResultado(enCurso.getClaveCliente(), enCurso.getCliente(), enCurso.getTemporada(), resultado);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errores", List.of(capitalizar(e.getMessage())));
            return "redirect:/documentos-corte/sms/revision";
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("errores", List.of("No se han podido generar las hojas de corte: "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
            return "redirect:/documentos-corte/sms/revision";
        }
        return "redirect:/documentos-corte/sms/resultados";
    }

    // --- Descarga ---

    @GetMapping("/documentos-corte/sms/resultados")
    public String resultados(Model model) {
        if (enCurso.getResultado() == null) {
            return "redirect:/documentos-corte/sms";
        }
        model.addAttribute("cliente", enCurso.getCliente());
        model.addAttribute("temporada", enCurso.getTemporada());
        model.addAttribute("resultado", enCurso.getResultado());
        model.addAttribute("novedades", enCurso.getNovedades());
        model.addAttribute("vieneDeRevision", enCurso.enRevision());
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

    // --- Novedades: entrar en el sistema ---

    /**
     * Entra un elemento de las novedades (modelo {@code m}, piel {@code p} o
     * color {@code c}, por su id) con lo editado en pantalla. Contesta JSON
     * con cuántos quedan pendientes; un error es un 400 con {@code mensaje}.
     */
    @PostMapping(value = "/documentos-corte/sms/novedades/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> entrar(@PathVariable String id,
                                                      @RequestParam Map<String, String> campos) {
        Novedades novedades = enCurso.getNovedades();
        if (novedades == null) {
            return mensaje(HttpStatus.GONE, "La sesión ha caducado: vuelve a generar las hojas de corte");
        }
        Novedades editadas = aplicar(novedades, campos);
        try {
            Novedades despues = switch (id.isEmpty() ? ' ' : id.charAt(0)) {
                case 'm' -> editadas.modelo(id).map(m -> editadas.con(novedadesSms.entrarModelo(editadas.cliente(), m)))
                        .orElse(null);
                case 'p' -> editadas.piel(id).map(p -> editadas.con(novedadesSms.entrarPiel(editadas.cliente(), p)))
                        .orElse(null);
                case 'c' -> editadas.color(id).map(c -> editadas.con(novedadesSms.entrarColor(editadas.cliente(), c)))
                        .orElse(null);
                default -> null;
            };
            if (despues == null) {
                return mensaje(HttpStatus.NOT_FOUND, "No hay ningún elemento " + id + " en las novedades");
            }
            enCurso.guardar(enCurso.getClaveCliente(), enCurso.getCliente(), enCurso.getTemporada(),
                    enCurso.getResultado(), despues);
            return respuesta(despues, List.of(id));
        } catch (IllegalArgumentException e) {
            enCurso.guardar(enCurso.getClaveCliente(), enCurso.getCliente(), enCurso.getTemporada(),
                    enCurso.getResultado(), editadas);
            return mensaje(HttpStatus.BAD_REQUEST, capitalizar(e.getMessage()));
        }
    }

    @PostMapping(value = "/documentos-corte/sms/novedades", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> entrarTodo(@RequestParam Map<String, String> campos) {
        Novedades novedades = enCurso.getNovedades();
        if (novedades == null) {
            return mensaje(HttpStatus.GONE, "La sesión ha caducado: vuelve a generar las hojas de corte");
        }
        Novedades editadas = aplicar(novedades, campos);
        enCurso.guardar(enCurso.getClaveCliente(), enCurso.getCliente(), enCurso.getTemporada(),
                enCurso.getResultado(), editadas);
        try {
            Novedades despues = novedadesSms.entrarTodo(editadas);
            enCurso.guardar(enCurso.getClaveCliente(), enCurso.getCliente(), enCurso.getTemporada(),
                    enCurso.getResultado(), despues);
            List<String> entrados = new ArrayList<>();
            despues.modelos().stream().filter(ModeloNuevo::entrado).forEach(m -> entrados.add(m.id()));
            despues.pieles().stream().filter(PielNueva::entrado).forEach(p -> entrados.add(p.id()));
            despues.colores().stream().filter(ColorNuevo::entrado).forEach(c -> entrados.add(c.id()));
            return respuesta(despues, entrados);
        } catch (IllegalArgumentException e) {
            return mensaje(HttpStatus.BAD_REQUEST, capitalizar(e.getMessage()));
        }
    }

    /**
     * Lo tecleado en pantalla sobre las novedades: {@code m0.nombre},
     * {@code m0.tipo}, {@code m0.a1.forro}, {@code m0.a1.c2} (combinación),
     * {@code p0.nombre}, {@code c0.descripcion}. Lo que no llega no se toca;
     * el nombre y el tipo de un modelo que ya existe no se cambian aquí.
     */
    static Novedades aplicar(Novedades novedades, Map<String, String> campos) {
        Novedades resultado = novedades;
        for (ModeloNuevo modelo : novedades.modelos()) {
            if (modelo.entrado()) {
                continue;
            }
            String nombre = modelo.existe() ? modelo.nombre() : campos.getOrDefault(modelo.id() + ".nombre",
                    modelo.nombre());
            TipoArticulo tipo = modelo.tipo();
            String tipoPedido = campos.get(modelo.id() + ".tipo");
            if (!modelo.existe() && tipoPedido != null) {
                try {
                    tipo = TipoArticulo.valueOf(tipoPedido);
                } catch (IllegalArgumentException ignorado) {
                    // Un tipo que no existe deja el que había.
                }
            }
            List<ArticuloNuevo> articulos = new ArrayList<>();
            for (int a = 0; a < modelo.articulos().size(); a++) {
                ArticuloNuevo articulo = modelo.articulos().get(a);
                String prefijo = modelo.id() + ".a" + a + ".";
                List<String> combinaciones = new ArrayList<>();
                for (int c = 0; c < 4; c++) {
                    String valor = campos.get(prefijo + "c" + c);
                    if (valor == null && c < articulo.combinaciones().size()) {
                        valor = articulo.combinaciones().get(c);
                    }
                    combinaciones.add(valor == null ? "" : valor);
                }
                articulos.add(new ArticuloNuevo(articulo.referencia(),
                        campos.getOrDefault(prefijo + "forro", articulo.forro()), combinaciones, articulo.colores()));
            }
            resultado = resultado.con(new ModeloNuevo(modelo.id(), modelo.codigo(), modelo.existe(), nombre, tipo,
                    articulos, modelo.croquis(), false));
        }
        for (PielNueva piel : novedades.pieles()) {
            if (!piel.entrado() && campos.containsKey(piel.id() + ".nombre")) {
                resultado = resultado.con(new PielNueva(piel.id(), piel.refPiel(), campos.get(piel.id() + ".nombre"),
                        false));
            }
        }
        for (ColorNuevo color : novedades.colores()) {
            if (!color.entrado() && campos.containsKey(color.id() + ".descripcion")) {
                resultado = resultado.con(new ColorNuevo(color.id(), color.codigo(),
                        campos.get(color.id() + ".descripcion"), false));
            }
        }
        return resultado;
    }

    private static ResponseEntity<Map<String, Object>> respuesta(Novedades novedades, List<String> entrados) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("entrados", entrados);
        cuerpo.put("pendientes", novedades.pendientes());
        return ResponseEntity.ok(cuerpo);
    }

    private static ResponseEntity<Map<String, Object>> mensaje(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("mensaje", mensaje));
    }

    private void guardarResultado(String clave, String nombre, String temporada, ResultadoSms resultado) {
        Novedades novedades;
        try {
            novedades = novedadesSms.calcular(clave, resultado.lineas());
        } catch (RuntimeException e) {
            // Las novedades son una ayuda: un fallo al calcularlas no puede quitar la descarga.
            novedades = null;
        }
        enCurso.guardar(clave, nombre, temporada, resultado, novedades);
    }

    private static String error(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("error", mensaje);
        return "redirect:/documentos-corte/sms";
    }

    private static String capitalizar(String texto) {
        return texto == null || texto.isEmpty() ? "Error" : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private String nombreDe(String clave) {
        return catalogoClientes.clientePara(clave).map(config -> nombreDe(config, clave)).orElse(clave);
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank() ? config.getNombre() : clave;
    }
}
