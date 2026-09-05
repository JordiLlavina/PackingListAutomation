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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.config.CatalogoTaras;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.model.VolcadoErpData;
import com.puntotres.packinglist.service.ClaudeEnvioExtractionService;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.PackingListGenerationService;
import com.puntotres.packinglist.service.ValidadorResumenExtraccion;
import com.puntotres.packinglist.service.VolcadoErpExcelBuilder;
import com.puntotres.packinglist.service.VolcadoErpGenerationService;
import com.puntotres.packinglist.service.etiquetas.CampoEtiquetas;
import com.puntotres.packinglist.service.etiquetas.EtiquetasGenerationService;
import com.puntotres.packinglist.service.etiquetas.GeneradorEtiquetasCliente;
import com.puntotres.packinglist.service.etiquetas.ResultadoEtiquetas;

import jakarta.validation.Valid;

/**
 * Asistente web de generación de packing lists en tres pantallas:
 * entrada (pegar JSON + cabecera) → revisión (avisos y pesos editables)
 * → resultados (descarga de excels). Orquesta los mismos servicios que
 * {@code Main.java}, con el estado del envío en la sesión HTTP.
 *
 * Las etiquetas de caja salen en el mismo /generar que los packing lists:
 * el único archivo que hacían falta —el excel de pedido del cliente— se
 * sube en la pantalla de entrada, así que no hay paso intermedio.
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

    private final PreparacionRevisionService preparacion;
    private final ClaudeEnvioExtractionService extractorClaude;
    private final ValidadorResumenExtraccion validadorResumen;
    private final PackingListGenerationService generador;
    private final VolcadoErpGenerationService generadorVolcado;
    private final VolcadoErpExcelBuilder constructorVolcado;
    private final EtiquetasGenerationService etiquetasService;
    private final ClientesProperties clientesProperties;
    private final CatalogoTaras catalogoTaras;
    private final ObjectMapper mapper;
    private final EnvioEnCurso envioEnCurso;

    public PackingListController(PreparacionRevisionService preparacion,
                                 ClaudeEnvioExtractionService extractorClaude,
                                 ValidadorResumenExtraccion validadorResumen,
                                 PackingListGenerationService generador,
                                 VolcadoErpGenerationService generadorVolcado,
                                 VolcadoErpExcelBuilder constructorVolcado,
                                 EtiquetasGenerationService etiquetasService,
                                 ClientesProperties clientesProperties,
                                 CatalogoTaras catalogoTaras,
                                 ObjectMapper mapper,
                                 EnvioEnCurso envioEnCurso) {
        this.preparacion = preparacion;
        this.extractorClaude = extractorClaude;
        this.validadorResumen = validadorResumen;
        this.generador = generador;
        this.generadorVolcado = generadorVolcado;
        this.constructorVolcado = constructorVolcado;
        this.etiquetasService = etiquetasService;
        this.clientesProperties = clientesProperties;
        this.catalogoTaras = catalogoTaras;
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
                        "Sube al menos una imagen o un PDF del packing list");
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
                envio = extractorClaude.extraer(aAdjuntos(imagenesDe(envioForm)),
                        cliente.getPlantilla());
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
                    ? "Claude no ha encontrado ninguna destinación en los documentos"
                    : "El JSON no contiene ninguna destinación ('destinos')");
            anadirAtributosDeClientes(model);
            return "entrada";
        }

        // Único punto del proyecto que BLOQUEA: si la extracción no cuadra
        // con los recuentos que las propias hojas declaran, lo más probable
        // es que falte una hoja en el escaneo, y eso no se arregla en la
        // revisión. El JSON extraído se vuelca al textarea (modo JSON) para
        // no perder el trabajo de la API y poder corregirlo a mano.
        ValidadorResumenExtraccion.ResultadoResumen resumen =
                validadorResumen.validar(envio, cliente);
        if (!resumen.errores().isEmpty()) {
            model.addAttribute("errorJson", "La extracción no cuadra con el resumen de "
                    + "las hojas: " + String.join(" · ", resumen.errores()));
            try {
                envioForm.setJson(mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(envio));
                envioForm.setModo("JSON");
            } catch (JsonProcessingException e) {
                // Sin JSON que enseñar, el error ya explica el problema.
            }
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

        // La cadena que va del JSON a la pantalla de revisión —importar,
        // resolver destinaciones, completar pedidos, asignar palets, inferir
        // pesos— la comparten las cuatro vías de entrada: vive en
        // PreparacionRevisionService.
        List<String> avisosPrevios = new ArrayList<>(resumen.avisos());
        if (modoClaude) {
            avisosPrevios.add(0, "Datos extraídos por Claude a partir de "
                    + imagenesDe(envioForm).size()
                    + " documento(s): revisa referencias, tallas y cantidades antes de generar");
        }

        // El excel de pedido se guarda aunque el cliente no lo use en el
        // packing list: AMI lo necesita para sus etiquetas de caja.
        byte[] excelPedido = null;
        String nombreExcelPedido = null;
        MultipartFile pedidoSubido = envioForm.getPedidoCliente();
        if (pedidoSubido != null && !pedidoSubido.isEmpty()) {
            try {
                excelPedido = pedidoSubido.getBytes();
                nombreExcelPedido = pedidoSubido.getOriginalFilename();
            } catch (IOException e) {
                avisosPrevios.add("No se ha podido leer el excel de pedido subido: "
                        + e.getMessage());
            }
        }

        preparacion.preparar(envio, cabecera, cliente, excelPedido, nombreExcelPedido,
                avisosPrevios, envioEnCurso);
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
        model.addAttribute("tamanosCaja", catalogoTaras.tamanosDeMayorAMenor());
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
        // Las etiquetas se rehacen en el mismo paso: regenerar invalidaría las
        // ya hechas de todos modos (pesos y cajas pueden haber cambiado).
        generarEtiquetasDelEnvio();
        return "redirect:/resultados";
    }

    /**
     * Etiquetas de caja del envío, generadas junto con los packing lists: ya
     * no hay un paso intermedio que pida archivos, porque el único que hacía
     * falta —el excel de pedido del cliente— se sube en la pantalla de entrada.
     *
     * Nada de esto bloquea el envío: un archivo que falte o un fallo del
     * generador dejan un aviso y las etiquetas sin generar, con los packing
     * lists ya hechos intactos. Un campo requerido que NO sea el excel de
     * pedido hoy no lo puede aportar nadie (ningún generador declara otro);
     * si algún día aparece, el usuario verá qué falta en vez de nada.
     */
    private void generarEtiquetasDelEnvio() {
        envioEnCurso.getEtiquetas().clear();
        envioEnCurso.getAvisosEtiquetas().clear();
        GeneradorEtiquetasCliente generadorEtiquetas = generadorEtiquetasDelEnvio().orElse(null);
        if (generadorEtiquetas == null) {
            return;
        }
        List<CampoEtiquetas> campos = generadorEtiquetas.camposRequeridos(destinosDelEnvio());
        Map<String, byte[]> archivos = new LinkedHashMap<>();
        for (CampoEtiquetas campo : campos) {
            byte[] contenido = campo.esPedidoCliente()
                    ? envioEnCurso.getExcelPedidoCliente() : null;
            if (contenido == null) {
                envioEnCurso.getAvisosEtiquetas().add(
                        "Falta el " + campo.titulo() + ". No se generan las etiquetas de caja");
            } else {
                archivos.put(campo.nombre(), contenido);
            }
        }
        if (archivos.size() < campos.size()) {
            return;
        }
        try {
            ResultadoEtiquetas resultado = generadorEtiquetas.generar(
                    envioEnCurso.getImportado().getDestinos(), envioEnCurso.getCabecera(), archivos);
            envioEnCurso.getEtiquetas().addAll(resultado.getExcels());
            envioEnCurso.getAvisosEtiquetas().addAll(resultado.getAvisos());
        } catch (IOException | RuntimeException e) {
            envioEnCurso.getAvisosEtiquetas().add(
                    "No se pudieron generar las etiquetas de caja: " + e.getMessage());
        }
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
        // Ya no hay pantalla que confirme qué excel de pedido se subió, así
        // que la tarjeta de etiquetas nombra el que las ha generado.
        model.addAttribute("nombrePedidoCliente", envioEnCurso.getNombreExcelPedidoCliente());
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

    private static List<ClaudeEnvioExtractionService.Adjunto> aAdjuntos(
            List<MultipartFile> ficheros) throws IOException {
        List<ClaudeEnvioExtractionService.Adjunto> adjuntos = new ArrayList<>();
        for (MultipartFile fichero : ficheros) {
            adjuntos.add(new ClaudeEnvioExtractionService.Adjunto(
                    fichero.getContentType(), fichero.getBytes()));
        }
        return adjuntos;
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
        model.addAttribute("tamanosCaja", catalogoTaras.tamanosDeMayorAMenor());
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
        preparacion.reinferir(envioEnCurso);
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
