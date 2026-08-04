package com.puntotres.packinglist.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.WebUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.config.TaraProperties;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.model.VolcadoErpData;
import com.puntotres.packinglist.service.ClaudeEnvioExtractionService;
import com.puntotres.packinglist.service.EnvioImportService;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.PackingListGenerationService;
import com.puntotres.packinglist.service.PaletAssignmentService;
import com.puntotres.packinglist.service.PedidoCompletionService;
import com.puntotres.packinglist.service.ResolutorDestinosPadre;
import com.puntotres.packinglist.service.ResultadoAsignacion;
import com.puntotres.packinglist.service.ResultadoDestinos;
import com.puntotres.packinglist.service.VolcadoErpExcelBuilder;
import com.puntotres.packinglist.service.VolcadoErpGenerationService;
import com.puntotres.packinglist.service.WeightInferenceService;
import com.puntotres.packinglist.service.etiquetas.CampoEtiquetas;
import com.puntotres.packinglist.service.etiquetas.EtiquetasGenerationService;
import com.puntotres.packinglist.service.etiquetas.GeneradorEtiquetasCliente;
import com.puntotres.packinglist.service.etiquetas.ResultadoEtiquetas;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Asistente web de generación de packing lists en tres pantallas:
 * entrada (pegar JSON + cabecera) → revisión (avisos y pesos editables)
 * → resultados (descarga de excels). Orquesta los mismos servicios que
 * {@code Main.java}, con el estado del envío en la sesión HTTP.
 *
 * Su primera pantalla vive en /packing-list: la raíz es el menú
 * (MenuController), desde el que se llega también a las etiquetas de
 * artículo, que son un flujo aparte.
 */
@Controller
public class PackingListController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final MediaType TIPO_XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EnvioImportService importador;
    private final ClaudeEnvioExtractionService extractorClaude;
    private final PaletAssignmentService asignadorPalets;
    private final WeightInferenceService inferidorPesos;
    private final ResolutorDestinosPadre resolutorDestinos;
    private final PedidoCompletionService completadorPedidos;
    private final PackingListGenerationService generador;
    private final VolcadoErpGenerationService generadorVolcado;
    private final VolcadoErpExcelBuilder constructorVolcado;
    private final EtiquetasGenerationService etiquetasService;
    private final ClientesProperties clientesProperties;
    private final TaraProperties taraProperties;
    private final ObjectMapper mapper;
    private final EnvioEnCurso envioEnCurso;

    public PackingListController(EnvioImportService importador,
                                 ClaudeEnvioExtractionService extractorClaude,
                                 PaletAssignmentService asignadorPalets,
                                 WeightInferenceService inferidorPesos,
                                 ResolutorDestinosPadre resolutorDestinos,
                                 PedidoCompletionService completadorPedidos,
                                 PackingListGenerationService generador,
                                 VolcadoErpGenerationService generadorVolcado,
                                 VolcadoErpExcelBuilder constructorVolcado,
                                 EtiquetasGenerationService etiquetasService,
                                 ClientesProperties clientesProperties,
                                 TaraProperties taraProperties,
                                 ObjectMapper mapper,
                                 EnvioEnCurso envioEnCurso) {
        this.importador = importador;
        this.extractorClaude = extractorClaude;
        this.asignadorPalets = asignadorPalets;
        this.inferidorPesos = inferidorPesos;
        this.resolutorDestinos = resolutorDestinos;
        this.completadorPedidos = completadorPedidos;
        this.generador = generador;
        this.generadorVolcado = generadorVolcado;
        this.constructorVolcado = constructorVolcado;
        this.etiquetasService = etiquetasService;
        this.clientesProperties = clientesProperties;
        this.taraProperties = taraProperties;
        this.mapper = mapper;
        this.envioEnCurso = envioEnCurso;
    }

    // --- Paso 1: entrada ---

    @GetMapping("/packing-list")
    public String entrada(Model model) {
        if (!model.containsAttribute("envioForm")) {
            EnvioForm form = new EnvioForm();
            String hoy = LocalDate.now().format(FORMATO_FECHA);
            form.setFechaFactura(hoy);
            form.setFechaEnvio(hoy);
            model.addAttribute("envioForm", form);
        }
        anadirAtributosDeClientes(model);
        return "entrada";
    }

    @PostMapping("/importar")
    public String importar(@Valid @ModelAttribute EnvioForm envioForm,
                           BindingResult errores, Model model) {
        boolean modoClaude = "CLAUDE".equals(envioForm.getModo());

        // El JSON y las imágenes se validan aquí y no con @NotBlank porque
        // solo es obligatorio el del modo activo. El modo FORMULARIO
        // serializa el formulario al mismo campo json antes de enviar, así
        // que a partir de aquí es indistinguible del modo JSON.
        if (modoClaude) {
            if (imagenesDe(envioForm).isEmpty()) {
                errores.rejectValue("imagenes", "imagenes.obligatorias",
                        "Sube al menos una imagen del packing list");
            }
        } else if (envioForm.getJson() == null || envioForm.getJson().isBlank()) {
            errores.rejectValue("json", "json.obligatorio",
                    "FORMULARIO".equals(envioForm.getModo())
                            ? "El formulario está vacío: añade al menos una destinación con sus cajas"
                            : "Pega el JSON del envío");
        }
        if (errores.hasErrors()) {
            anadirAtributosDeClientes(model);
            return "entrada";
        }

        ClienteConfig cliente = clientesProperties.clientePara(envioForm.getCliente()).orElse(null);
        if (cliente == null) {
            errores.rejectValue("cliente", "cliente.desconocido", "Cliente desconocido: " + envioForm.getCliente());
            anadirAtributosDeClientes(model);
            return "entrada";
        }

        EnvioInput envio;
        if (modoClaude) {
            try {
                envio = extractorClaude.extraer(aImagenesDeServicio(imagenesDe(envioForm)));
            } catch (ClaudeEnvioExtractionService.ExtraccionException | IOException e) {
                model.addAttribute("errorJson",
                        "No se pudo extraer el packing list con Claude: " + e.getMessage());
                anadirAtributosDeClientes(model);
                return "entrada";
            }
        } else {
            try {
                envio = mapper.readValue(envioForm.getJson(), EnvioInput.class);
            } catch (JsonProcessingException e) {
                model.addAttribute("errorJson", "El JSON no es válido: " + e.getOriginalMessage());
                anadirAtributosDeClientes(model);
                return "entrada";
            }
        }
        if (envio.getDestinos() == null || envio.getDestinos().isEmpty()) {
            model.addAttribute("errorJson", modoClaude
                    ? "Claude no ha encontrado ninguna destinación en las imágenes"
                    : "El JSON no contiene ninguna destinación ('destinos')");
            anadirAtributosDeClientes(model);
            return "entrada";
        }

        DatosEnvio cabecera = new DatosEnvio();
        cabecera.setTemporada(envioForm.getTemporada());
        cabecera.setNumeroFactura(envioForm.getNumeroFactura());
        cabecera.setFechaFactura(envioForm.getFechaFactura());
        cabecera.setFechaEnvio(envioForm.getFechaEnvio());
        cabecera.setClaveCliente(envioForm.getCliente());
        // Solo lo usa el volcado ERP; vacío significa columna "Comanda" en blanco.
        cabecera.setNumeroComanda(envioForm.getNumeroComanda());
        // Ciudad/país del proveedor: solo tienen sentido para AMI; en blanco
        // se aplica el valor por defecto (BADALONA/SPAIN) en el generador.
        cabecera.setCiudadProveedor(envioForm.getCiudadProveedor());
        cabecera.setPaisProveedor(envioForm.getPaisProveedor());

        // Mismo encadenado que Main.java: importar -> asignar -> inferir.
        EnvioImportado importado = importador.importar(envio);

        if (modoClaude) {
            importado.getAvisos().add(0, "Datos extraídos por Claude a partir de "
                    + imagenesDe(envioForm).size()
                    + " imagen(es): revisa referencias, tallas y cantidades antes de generar");
        }

        // El "cliente" del JSON es informativo (viene de las imágenes); si no
        // coincide con el seleccionado en el desplegable, se avisa mas no
        // bloquea: el desplegable manda.
        if (envio.getCliente() != null && !envio.getCliente().isBlank()
                && clientesProperties.clientePara(envio.getCliente())
                        .map(c -> c != cliente).orElse(true)) {
            importado.getAvisos().add("Los datos de entrada indican que el cliente es '"
                    + envio.getCliente() + "' pero has seleccionado '" + cliente.getNombre() + "'");
        }

        envioEnCurso.reiniciar();
        envioEnCurso.setCabecera(cabecera);
        envioEnCurso.setImportado(importado);

        // El excel de pedido se guarda aunque el cliente no lo use en el
        // packing list: AMI lo reutiliza en el Paso 2 de etiquetas.
        byte[] excelPedido = null;
        MultipartFile pedidoSubido = envioForm.getPedidoCliente();
        if (pedidoSubido != null && !pedidoSubido.isEmpty()) {
            try {
                excelPedido = pedidoSubido.getBytes();
                envioEnCurso.setExcelPedidoCliente(excelPedido, pedidoSubido.getOriginalFilename());
            } catch (IOException e) {
                importado.getAvisos().add("No se ha podido leer el excel de pedido subido: "
                        + e.getMessage());
            }
        }

        // Las destinaciones hijas se resuelven a su padre ANTES de asignar
        // palets, para que la asignación y la inferencia trabajen ya sobre
        // las destinaciones definitivas.
        ResultadoDestinos resueltos = resolutorDestinos.resolver(
                importado.getDestinos(), cliente, cabecera.getFechaEnvio());
        importado.getDestinos().clear();
        importado.getDestinos().addAll(resueltos.getDestinos());
        importado.getAvisos().addAll(resueltos.getAvisos());

        // Solo APC completa el pedido: es el único con un excel de pedido del
        // que sacar el número entero a partir de la referencia. Corre aquí y
        // no en los recálculos de la revisión, que pisarían lo tecleado.
        if (cliente.getPlantilla() == TipoPlantilla.APC) {
            importado.getAvisos().addAll(
                    completadorPedidos.completar(importado.getDestinos(), excelPedido).getAvisos());
        }

        for (EnvioImportado.DestinoImportado destino : importado.getDestinos()) {
            ResultadoAsignacion asignacion =
                    asignadorPalets.asignar(destino.getDestino(), destino.getPalets());
            envioEnCurso.getAvisosPalets().addAll(asignacion.getAvisos());
            envioEnCurso.getCajasSinPalet().addAll(asignacion.getCajasSinPalet());
        }
        reinferirTodoElEnvio();
        return "redirect:/revision";
    }

    // --- Paso 2: revisión ---

    @GetMapping("/revision")
    public String revision(Model model, RedirectAttributes redirect) {
        if (envioEnCurso.estaVacio()) {
            return sinEnvio(redirect);
        }
        model.addAttribute("destinos", montarVistaDestinos());
        model.addAttribute("avisosImportacion", envioEnCurso.getImportado().getAvisos());
        model.addAttribute("avisosPalets", envioEnCurso.getAvisosPalets());
        model.addAttribute("avisosInferencia", envioEnCurso.getAvisosInferencia());
        model.addAttribute("cajasSinPalet", envioEnCurso.getCajasSinPalet());
        // Tamaños con tara conocida para el desplegable de la columna TAMAÑO,
        // de la caja más grande a la más pequeña.
        model.addAttribute("tamanosCaja", taraProperties.tamanosDeMayorAMenor());
        return "revision";
    }

    @PostMapping("/recalcular")
    public String recalcular(@ModelAttribute RevisionForm revisionForm, RedirectAttributes redirect) {
        if (envioEnCurso.estaVacio()) {
            return sinEnvio(redirect);
        }
        aplicarEdicionesYReinferir(revisionForm);
        return "redirect:/revision";
    }

    /**
     * Despliega una fila compactada en sus cajas (o la vuelve a plegar). Aplica
     * ANTES las ediciones del formulario: el triángulo es un submit de la misma
     * tabla, así que lo ya tecleado no se pierde al desplegar.
     */
    @PostMapping("/alternar-fila")
    public String alternarFila(@RequestParam int destino, @RequestParam int indice,
                               @ModelAttribute RevisionForm revisionForm,
                               RedirectAttributes redirect) {
        if (envioEnCurso.estaVacio()) {
            return sinEnvio(redirect);
        }
        aplicarEdicionesYReinferir(revisionForm);
        envioEnCurso.alternarFilaDesplegada(destino, indice);
        return "redirect:/revision";
    }

    @PostMapping("/generar")
    public String generar(@ModelAttribute RevisionForm revisionForm, RedirectAttributes redirect) {
        if (envioEnCurso.estaVacio()) {
            return sinEnvio(redirect);
        }
        aplicarEdicionesYReinferir(revisionForm);

        List<ExcelGenerado> excels = new ArrayList<>();
        List<String> avisosGeneracion = new ArrayList<>();
        VolcadoErpData volcado;
        try {
            DatosEnvio cabecera = envioEnCurso.getCabecera();
            ClienteConfig cliente = clientesProperties.clientePara(cabecera.getClaveCliente())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cliente desconocido: " + cabecera.getClaveCliente()));
            List<CajaData> todasLasCajas = new ArrayList<>();
            for (EnvioImportado.DestinoImportado destino : envioEnCurso.getImportado().getDestinos()) {
                DestinoData datos = destino.getDestino();
                // Cliente con catálogo de destinos (APC): una destinación
                // fuera del catálogo no tiene datos con que rellenar su
                // plantilla; se avisa y se salta en vez de abortar el envío.
                if (!cliente.getDestinos().isEmpty()
                        && cliente.destinoPara(datos.getNombreDestino()).isEmpty()) {
                    avisosGeneracion.add("Destinación '" + datos.getNombreDestino()
                            + "' sin datos configurados para " + cliente.getNombre()
                            + ": packing no generado");
                    // El volcado ERP es del envío completo: sus cajas cuentan igual.
                    todasLasCajas.addAll(datos.getCajas());
                    continue;
                }
                excels.addAll(generador.generar(datos, destino.getPalets(), cabecera, cliente));
                todasLasCajas.addAll(datos.getCajas());
            }
            // El volcado ERP agrupa TODAS las cajas del envío (todas las
            // destinaciones) por referencia+talla+color, un excel por envío.
            volcado = generadorVolcado.generar(todasLasCajas, cabecera);
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error",
                    "No se pudieron generar los excels: " + e.getMessage());
            return "redirect:/revision";
        }
        if (excels.isEmpty() && !avisosGeneracion.isEmpty()) {
            redirect.addFlashAttribute("error", "No se pudo generar ningún packing list. "
                    + String.join(" · ", avisosGeneracion));
            return "redirect:/revision";
        }
        envioEnCurso.getExcels().clear();
        envioEnCurso.getExcels().addAll(excels);
        envioEnCurso.getAvisosGeneracion().clear();
        envioEnCurso.getAvisosGeneracion().addAll(avisosGeneracion);
        envioEnCurso.setVolcadoErp(volcado);
        // Regenerar invalida las etiquetas ya hechas (pesos/cajas cambiados).
        envioEnCurso.getEtiquetas().clear();
        envioEnCurso.getAvisosEtiquetas().clear();
        return "redirect:/resultados";
    }

    // --- Paso 3: resultados y descargas ---

    @GetMapping("/resultados")
    public String resultados(Model model, RedirectAttributes redirect) {
        if (envioEnCurso.estaVacio()) {
            return sinEnvio(redirect);
        }
        if (envioEnCurso.getExcels().isEmpty()) {
            return "redirect:/revision";
        }
        model.addAttribute("excels", envioEnCurso.getExcels());
        model.addAttribute("avisosGeneracion", envioEnCurso.getAvisosGeneracion());
        model.addAttribute("cabecera", envioEnCurso.getCabecera());
        model.addAttribute("volcadoErp", envioEnCurso.getVolcadoErp());
        model.addAttribute("etiquetas", envioEnCurso.getEtiquetas());
        model.addAttribute("avisosEtiquetas", envioEnCurso.getAvisosEtiquetas());
        model.addAttribute("hayGeneradorEtiquetas", generadorEtiquetasDelEnvio().isPresent());
        return "resultados";
    }

    @GetMapping("/descargar/{nombreFichero}")
    public ResponseEntity<byte[]> descargar(@PathVariable String nombreFichero) {
        return envioEnCurso.getExcels().stream()
                .filter(excel -> excel.getNombreFichero().equals(nombreFichero))
                .findFirst()
                .map(excel -> ResponseEntity.ok()
                        .contentType(TIPO_XLSX)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                                .attachment().filename(excel.getNombreFichero()).build().toString())
                        .body(excel.getContenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/descargar-todo")
    public ResponseEntity<byte[]> descargarTodo() throws IOException {
        if (envioEnCurso.estaVacio() || envioEnCurso.getExcels().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (ExcelGenerado excel : envioEnCurso.getExcels()) {
                zip.putNextEntry(new ZipEntry(excel.getNombreFichero()));
                zip.write(excel.getContenido());
                zip.closeEntry();
            }
        }
        String nombreZip = ("PKL_" + envioEnCurso.getCabecera().getNumeroFactura() + ".zip")
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                        .attachment().filename(nombreZip).build().toString())
                .body(salida.toByteArray());
    }

    @GetMapping("/descargar-volcado-erp")
    public ResponseEntity<byte[]> descargarVolcadoErp() throws IOException {
        VolcadoErpData volcado = envioEnCurso.getVolcadoErp();
        if (envioEnCurso.estaVacio() || volcado == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(TIPO_XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                        .attachment().filename(volcado.getNombreFichero()).build().toString())
                .body(constructorVolcado.generar(volcado));
    }

    // --- Paso extra: etiquetas de caja ---

    /**
     * Paso 2 de etiquetas: según las destinaciones del envío, el generador
     * del cliente declara qué archivos extra necesita (ej. el excel del
     * pedido de la temporada de AMI) antes de poder generar.
     */
    @GetMapping("/etiquetas")
    public String etiquetas(Model model, RedirectAttributes redirect) {
        if (envioEnCurso.estaVacio()) {
            return sinEnvio(redirect);
        }
        if (envioEnCurso.getExcels().isEmpty()) {
            return "redirect:/revision";
        }
        GeneradorEtiquetasCliente generador = generadorEtiquetasDelEnvio().orElse(null);
        if (generador == null) {
            return "redirect:/resultados";
        }
        List<DestinoData> destinos = destinosDelEnvio();
        model.addAttribute("campos", generador.camposRequeridos(destinos));
        model.addAttribute("destinos", destinos.stream()
                .map(destino -> Map.of(
                        "nombre", destino.getNombreDestino(),
                        "soportado", generador.soportaDestino(destino.getNombreDestino())))
                .toList());
        boolean haySoportadas = destinos.stream()
                .anyMatch(destino -> generador.soportaDestino(destino.getNombreDestino()));
        model.addAttribute("haySoportadas", haySoportadas);
        model.addAttribute("cabecera", envioEnCurso.getCabecera());
        // Si el excel de pedido ya se subió en el paso 1, su input deja de ser
        // obligatorio aquí y la vista dice cuál hay puesto.
        model.addAttribute("nombrePedidoEnSesion", envioEnCurso.getNombreExcelPedidoCliente());
        return "etiquetas";
    }

    /**
     * Recibe {@link HttpServletRequest} y no {@code MultipartHttpServletRequest}
     * a propósito: desde que el excel de pedido puede venir de la sesión, este
     * formulario se puede enviar sin ningún fichero, y con el tipo multipart
     * una petición que no lo sea revienta con un 500 antes de entrar aquí.
     */
    @PostMapping("/etiquetas/generar")
    public String generarEtiquetas(HttpServletRequest peticion,
                                   RedirectAttributes redirect) {
        if (envioEnCurso.estaVacio()) {
            return sinEnvio(redirect);
        }
        GeneradorEtiquetasCliente generador = generadorEtiquetasDelEnvio().orElse(null);
        if (generador == null) {
            return "redirect:/resultados";
        }
        List<DestinoData> destinos = destinosDelEnvio();
        Map<String, byte[]> archivos = new LinkedHashMap<>();
        try {
            // getNativeRequest desenvuelve los decoradores de Tomcat/Spring;
            // null = la petición no traía ningún fichero.
            MultipartHttpServletRequest multipart =
                    WebUtils.getNativeRequest(peticion, MultipartHttpServletRequest.class);
            for (CampoEtiquetas campo : generador.camposRequeridos(destinos)) {
                MultipartFile archivo =
                        (multipart == null) ? null : multipart.getFile(campo.nombre());
                if (archivo != null && !archivo.isEmpty()) {
                    archivos.put(campo.nombre(), archivo.getBytes());
                    continue;
                }
                // El excel de pedido puede venir de la pantalla de entrada:
                // si ya está en sesión, no se vuelve a pedir.
                byte[] deSesion = campo.esPedidoCliente()
                        ? envioEnCurso.getExcelPedidoCliente() : null;
                if (deSesion == null) {
                    redirect.addFlashAttribute("error",
                            "Falta el archivo: " + campo.titulo());
                    return "redirect:/etiquetas";
                }
                archivos.put(campo.nombre(), deSesion);
            }
            ResultadoEtiquetas resultado = generador.generar(
                    envioEnCurso.getImportado().getDestinos(), envioEnCurso.getCabecera(), archivos);
            envioEnCurso.getEtiquetas().clear();
            envioEnCurso.getEtiquetas().addAll(resultado.getExcels());
            envioEnCurso.getAvisosEtiquetas().clear();
            envioEnCurso.getAvisosEtiquetas().addAll(resultado.getAvisos());
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error",
                    "No se pudieron generar las etiquetas: " + e.getMessage());
            return "redirect:/etiquetas";
        }
        return "redirect:/resultados";
    }

    @GetMapping("/descargar-etiquetas/{nombreFichero}")
    public ResponseEntity<byte[]> descargarEtiquetas(@PathVariable String nombreFichero) {
        return envioEnCurso.getEtiquetas().stream()
                .filter(excel -> excel.getNombreFichero().equals(nombreFichero))
                .findFirst()
                .map(excel -> ResponseEntity.ok()
                        .contentType(TIPO_XLSX)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                                .attachment().filename(excel.getNombreFichero()).build().toString())
                        .body(excel.getContenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/nuevo")
    public String nuevo() {
        envioEnCurso.reiniciar();
        return "redirect:/packing-list";
    }

    // --- Internos ---

    /** Imágenes realmente subidas: el input file manda una entrada vacía si no eliges nada. */
    private static List<MultipartFile> imagenesDe(EnvioForm form) {
        if (form.getImagenes() == null) {
            return List.of();
        }
        return form.getImagenes().stream()
                .filter(imagen -> imagen != null && !imagen.isEmpty())
                .toList();
    }

    private static List<ClaudeEnvioExtractionService.Imagen> aImagenesDeServicio(
            List<MultipartFile> ficheros) throws IOException {
        List<ClaudeEnvioExtractionService.Imagen> imagenes = new ArrayList<>();
        for (MultipartFile fichero : ficheros) {
            imagenes.add(new ClaudeEnvioExtractionService.Imagen(
                    fichero.getContentType(), fichero.getBytes()));
        }
        return imagenes;
    }

    private Optional<GeneradorEtiquetasCliente> generadorEtiquetasDelEnvio() {
        return etiquetasService.generadorPara(envioEnCurso.getCabecera().getClaveCliente());
    }

    private List<DestinoData> destinosDelEnvio() {
        return envioEnCurso.getImportado().getDestinos().stream()
                .map(EnvioImportado.DestinoImportado::getDestino)
                .toList();
    }

    private String sinEnvio(RedirectAttributes redirect) {
        redirect.addFlashAttribute("mensaje", "No hay ningún envío en curso: empieza pegando el JSON.");
        return "redirect:/packing-list";
    }

    /**
     * Catálogo de clientes para el desplegable y su configuración (tipo de
     * plantilla, placeholder de temporada) para el JS de la pantalla de
     * entrada, que ajusta los campos visibles al cambiar de cliente.
     */
    private void anadirAtributosDeClientes(Model model) {
        model.addAttribute("clientes", clientesProperties.getClientes());
        // Tamaños de caja con tara conocida, para el datalist del modo
        // FORMULARIO (evita teclear un tamaño que luego no tendría tara).
        model.addAttribute("tamanosCaja", taraProperties.tamanosDeMayorAMenor());
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
        model.addAttribute("clientesJs", clientesJs);
    }

    /**
     * Aplica lo editado a mano sobre las CajaData de la sesión y re-ejecuta la
     * inferencia: un peso añadido a mano (neto o bruto) puede desbloquear el
     * resto de su referencia, y corregir un tamaño de caja cambia la tara con
     * la que se infiere (la inferencia solo rellena nulls, nunca pisa un valor
     * manual). La caja se localiza por su POSICIÓN en la destinación, no por su
     * número, que puede repetirse (caja mixta con dos colores).
     *
     * Una entrada del formulario puede apuntar a VARIAS cajas: las filas
     * compactadas ("4-8") llevan un valor para todo su tramo. Aplicarlo a todas
     * es imprescindible aunque ya coincidieran al agruparse — si no, el grupo
     * se partiría en el siguiente render y el usuario vería la caja 4 con el
     * valor nuevo y las 5-8 con el viejo.
     *
     * Un campo que llega vacío significa "no tocar": permite corregir cualquier
     * dato mal leído sin obligar a reescribir la fila entera.
     */
    private void aplicarEdicionesYReinferir(RevisionForm form) {
        List<EnvioImportado.DestinoImportado> destinos = envioEnCurso.getImportado().getDestinos();
        // Cabecera de la destinación (Livraison code): vacío = "no tocar",
        // igual que en las cajas, así que no se puede borrar sin querer.
        for (RevisionForm.DestinoEditado edicion : form.getDestinos()) {
            if (edicion == null || edicion.getIndiceDestino() < 0
                    || edicion.getIndiceDestino() >= destinos.size()
                    || !tieneTexto(edicion.getLivraisonCode())) {
                continue;
            }
            destinos.get(edicion.getIndiceDestino()).getDestino().getCajas()
                    .forEach(caja -> caja.setLivraisonCode(edicion.getLivraisonCode().trim()));
        }
        for (RevisionForm.CajaEditada edicion : form.getCajas()) {
            if (edicion == null || edicion.getIndiceDestino() < 0
                    || edicion.getIndiceDestino() >= destinos.size()) {
                continue;
            }
            List<CajaData> cajas = destinos.get(edicion.getIndiceDestino()).getDestino().getCajas();
            for (Integer indice : edicion.getIndicesCaja()) {
                if (indice == null || indice < 0 || indice >= cajas.size()) {
                    continue;
                }
                aplicarA(cajas.get(indice), edicion);
            }
        }
        reinferirTodoElEnvio();
        recalcularCajasSinPalet();
    }

    private static void aplicarA(CajaData caja, RevisionForm.CajaEditada edicion) {
        if (edicion.getNumeroCaja() != null) {
            caja.setNumeroCaja(edicion.getNumeroCaja());
        }
        if (tieneTexto(edicion.getReferencia())) {
            caja.setReferencia(edicion.getReferencia().trim());
        }
        if (tieneTexto(edicion.getCodigoColor())) {
            caja.setCodigoColor(edicion.getCodigoColor().trim());
        }
        if (tieneTexto(edicion.getNumeroPedido())) {
            caja.setNumeroPedido(edicion.getNumeroPedido().trim());
        }
        if (tieneTexto(edicion.getTalla())) {
            caja.setTalla(edicion.getTalla().trim());
        }
        if (tieneTexto(edicion.getTamanoCaja())) {
            caja.setTamanoCaja(edicion.getTamanoCaja().trim());
        }
        if (edicion.getCantidad() != null) {
            caja.setCantidad(edicion.getCantidad());
        }
        if (edicion.getNumeroPalet() != null) {
            caja.setNumeroPalet(edicion.getNumeroPalet());
        }
        if (edicion.getPesoNetoKg() != null) {
            caja.setPesoNetoKg(edicion.getPesoNetoKg());
        }
        if (edicion.getPesoBrutoKg() != null) {
            caja.setPesoBrutoKg(edicion.getPesoBrutoKg());
        }
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    /**
     * Rehace la lista de cajas sin palet leyendo el dato real, en vez de volver
     * a ejecutar el PaletAssignmentService: sus rangos son los del JSON de
     * entrada y machacarían el palet que el usuario acaba de teclear a mano.
     */
    private void recalcularCajasSinPalet() {
        envioEnCurso.getCajasSinPalet().clear();
        for (EnvioImportado.DestinoImportado destino : envioEnCurso.getImportado().getDestinos()) {
            destino.getDestino().getCajas().stream()
                    .filter(caja -> caja.getNumeroPalet() == null)
                    .forEach(envioEnCurso.getCajasSinPalet()::add);
        }
    }

    /**
     * Re-ejecuta la inferencia de pesos sobre TODAS las cajas del envío a la
     * vez (todas las destinaciones juntas), no destinación por destinación: el
     * peso neto por unidad es propiedad de la referencia (el producto), así que
     * un peso tecleado en una caja de un modelo debe completar ese mismo modelo
     * esté en el palet o la destinación que esté, no solo en la suya.
     */
    private void reinferirTodoElEnvio() {
        List<List<CajaData>> cajasPorDestino = new ArrayList<>();
        for (EnvioImportado.DestinoImportado destino : envioEnCurso.getImportado().getDestinos()) {
            cajasPorDestino.add(destino.getDestino().getCajas());
        }
        envioEnCurso.getAvisosInferencia().clear();
        envioEnCurso.getAvisosInferencia().addAll(
                inferidorPesos.inferirPesosDelEnvio(cajasPorDestino).getAvisos());
    }

    /**
     * El peso es de la caja física entera (un bulto = un numeroCaja) y solo lo
     * lleva su primera línea, la "líder": es la única con campos de peso
     * editables. Las demás líneas de la caja —otras tallas, colores o
     * referencias del mismo bulto— comparten ese peso y no se editan por
     * separado. Quien decide eso, y qué cajas se compactan en una sola fila,
     * es {@link AgrupadorFilasRevision}.
     */
    private List<DestinoVista> montarVistaDestinos() {
        List<DestinoVista> vista = new ArrayList<>();
        int indiceGlobal = 0;
        List<EnvioImportado.DestinoImportado> destinos = envioEnCurso.getImportado().getDestinos();
        for (int i = 0; i < destinos.size(); i++) {
            List<CajaData> cajas = destinos.get(i).getDestino().getCajas();
            List<FilaCaja> filas = AgrupadorFilasRevision.agrupar(
                    cajas, indiceGlobal, envioEnCurso.filasDesplegadasDe(i));
            // El índice global nombra los inputs y es único en toda la página:
            // la siguiente destinación arranca donde acabó esta.
            indiceGlobal += filas.size();
            // El Livraison code es de la destinación entera: todas sus cajas
            // llevan el mismo, así que basta con mirar la primera.
            String livraisonCode = cajas.isEmpty() ? null : cajas.get(0).getLivraisonCode();
            vista.add(new DestinoVista(i, destinos.get(i).getDestino().getNombreDestino(),
                    filas, contarCajasFisicas(cajas), livraisonCode));
        }
        return vista;
    }

    /** Bultos reales de la destinación: las filas ya no los cuentan (van compactadas). */
    private static int contarCajasFisicas(List<CajaData> cajas) {
        return (int) cajas.stream().map(CajaData::getNumeroCaja).distinct().count();
    }
}
