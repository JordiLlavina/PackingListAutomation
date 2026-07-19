package com.puntotres.packinglist.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.model.VolcadoErpData;
import com.puntotres.packinglist.service.EnvioImportService;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.PackingListGenerationService;
import com.puntotres.packinglist.service.PaletAssignmentService;
import com.puntotres.packinglist.service.ResultadoAsignacion;
import com.puntotres.packinglist.service.VolcadoErpExcelBuilder;
import com.puntotres.packinglist.service.VolcadoErpGenerationService;
import com.puntotres.packinglist.service.WeightInferenceService;

import jakarta.validation.Valid;

/**
 * Asistente web de generación de packing lists en tres pantallas:
 * entrada (pegar JSON + cabecera) → revisión (avisos y pesos editables)
 * → resultados (descarga de excels). Orquesta los mismos servicios que
 * {@code Main.java}, con el estado del envío en la sesión HTTP.
 */
@Controller
public class PackingListController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final MediaType TIPO_XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EnvioImportService importador;
    private final PaletAssignmentService asignadorPalets;
    private final WeightInferenceService inferidorPesos;
    private final PackingListGenerationService generador;
    private final VolcadoErpGenerationService generadorVolcado;
    private final VolcadoErpExcelBuilder constructorVolcado;
    private final ClientesProperties clientesProperties;
    private final ObjectMapper mapper;
    private final EnvioEnCurso envioEnCurso;

    public PackingListController(EnvioImportService importador,
                                 PaletAssignmentService asignadorPalets,
                                 WeightInferenceService inferidorPesos,
                                 PackingListGenerationService generador,
                                 VolcadoErpGenerationService generadorVolcado,
                                 VolcadoErpExcelBuilder constructorVolcado,
                                 ClientesProperties clientesProperties,
                                 ObjectMapper mapper,
                                 EnvioEnCurso envioEnCurso) {
        this.importador = importador;
        this.asignadorPalets = asignadorPalets;
        this.inferidorPesos = inferidorPesos;
        this.generador = generador;
        this.generadorVolcado = generadorVolcado;
        this.constructorVolcado = constructorVolcado;
        this.clientesProperties = clientesProperties;
        this.mapper = mapper;
        this.envioEnCurso = envioEnCurso;
    }

    // --- Paso 1: entrada ---

    @GetMapping("/")
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
        try {
            envio = mapper.readValue(envioForm.getJson(), EnvioInput.class);
        } catch (JsonProcessingException e) {
            model.addAttribute("errorJson", "El JSON no es válido: " + e.getOriginalMessage());
            anadirAtributosDeClientes(model);
            return "entrada";
        }
        if (envio.getDestinos() == null || envio.getDestinos().isEmpty()) {
            model.addAttribute("errorJson", "El JSON no contiene ninguna destinación ('destinos')");
            anadirAtributosDeClientes(model);
            return "entrada";
        }

        DatosEnvio cabecera = new DatosEnvio();
        cabecera.setTemporada(envioForm.getTemporada());
        cabecera.setNumeroFactura(envioForm.getNumeroFactura());
        cabecera.setFechaFactura(envioForm.getFechaFactura());
        cabecera.setFechaEnvio(envioForm.getFechaEnvio());
        cabecera.setClaveCliente(envioForm.getCliente());
        // Ciudad/país del proveedor: solo tienen sentido para AMI; en blanco
        // se aplica el valor por defecto (BADALONA/SPAIN) en el generador.
        cabecera.setCiudadProveedor(envioForm.getCiudadProveedor());
        cabecera.setPaisProveedor(envioForm.getPaisProveedor());

        // Mismo encadenado que Main.java: importar -> asignar -> inferir.
        EnvioImportado importado = importador.importar(envio);

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
        for (EnvioImportado.DestinoImportado destino : importado.getDestinos()) {
            ResultadoAsignacion asignacion =
                    asignadorPalets.asignar(destino.getDestino(), destino.getPalets());
            envioEnCurso.getAvisosPalets().addAll(asignacion.getAvisos());
            envioEnCurso.getCajasSinPalet().addAll(asignacion.getCajasSinPalet());
            envioEnCurso.getAvisosInferencia().addAll(
                    inferidorPesos.inferirPesosPorReferencia(destino.getDestino().getCajas())
                            .getAvisos());
        }
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
        return "revision";
    }

    @PostMapping("/recalcular")
    public String recalcular(@ModelAttribute RevisionForm revisionForm, RedirectAttributes redirect) {
        if (envioEnCurso.estaVacio()) {
            return sinEnvio(redirect);
        }
        aplicarPesosYReinferir(revisionForm);
        return "redirect:/revision";
    }

    @PostMapping("/generar")
    public String generar(@ModelAttribute RevisionForm revisionForm, RedirectAttributes redirect) {
        if (envioEnCurso.estaVacio()) {
            return sinEnvio(redirect);
        }
        aplicarPesosYReinferir(revisionForm);

        List<ExcelGenerado> excels = new ArrayList<>();
        VolcadoErpData volcado;
        try {
            DatosEnvio cabecera = envioEnCurso.getCabecera();
            ClienteConfig cliente = clientesProperties.clientePara(cabecera.getClaveCliente())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cliente desconocido: " + cabecera.getClaveCliente()));
            List<CajaData> todasLasCajas = new ArrayList<>();
            for (EnvioImportado.DestinoImportado destino : envioEnCurso.getImportado().getDestinos()) {
                excels.addAll(generador.generar(
                        destino.getDestino(), destino.getPalets(), cabecera, cliente));
                todasLasCajas.addAll(destino.getDestino().getCajas());
            }
            // El volcado ERP agrupa TODAS las cajas del envío (todas las
            // destinaciones) por referencia+talla+color, un excel por envío.
            volcado = generadorVolcado.generar(todasLasCajas, cabecera);
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error",
                    "No se pudieron generar los excels: " + e.getMessage());
            return "redirect:/revision";
        }
        envioEnCurso.getExcels().clear();
        envioEnCurso.getExcels().addAll(excels);
        envioEnCurso.setVolcadoErp(volcado);
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
        model.addAttribute("cabecera", envioEnCurso.getCabecera());
        model.addAttribute("volcadoErp", envioEnCurso.getVolcadoErp());
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

    /** Etiquetas: pendiente de implementar (stub que devuelve 404). */
    @GetMapping("/descargar-etiquetas")
    public ResponseEntity<byte[]> descargarEtiquetas() {
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/nuevo")
    public String nuevo() {
        envioEnCurso.reiniciar();
        return "redirect:/";
    }

    // --- Internos ---

    private String sinEnvio(RedirectAttributes redirect) {
        redirect.addFlashAttribute("mensaje", "No hay ningún envío en curso: empieza pegando el JSON.");
        return "redirect:/";
    }

    /**
     * Catálogo de clientes para el desplegable y su configuración (tipo de
     * plantilla, placeholder de temporada) para el JS de la pantalla de
     * entrada, que ajusta los campos visibles al cambiar de cliente.
     */
    private void anadirAtributosDeClientes(Model model) {
        model.addAttribute("clientes", clientesProperties.getClientes());
        Map<String, Map<String, String>> clientesJs = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> {
            Map<String, String> datos = new LinkedHashMap<>();
            datos.put("plantilla", config.getPlantilla().name());
            datos.put("placeholderTemporada",
                    config.getPlaceholderTemporada() != null ? config.getPlaceholderTemporada() : "");
            clientesJs.put(clave, datos);
        });
        model.addAttribute("clientesJs", clientesJs);
    }

    /**
     * Aplica los pesos introducidos a mano sobre las CajaData de la sesión y
     * re-ejecuta la inferencia: un peso añadido a mano (neto o bruto) puede
     * desbloquear el resto de su referencia (la inferencia solo rellena
     * nulls, nunca pisa un valor manual). La caja se localiza por su
     * POSICIÓN en la destinación, no por su número, que puede repetirse
     * (caja mixta con dos colores).
     */
    private void aplicarPesosYReinferir(RevisionForm form) {
        List<EnvioImportado.DestinoImportado> destinos = envioEnCurso.getImportado().getDestinos();
        for (RevisionForm.PesoEditado peso : form.getPesos()) {
            if (peso == null || peso.getIndiceDestino() < 0
                    || peso.getIndiceDestino() >= destinos.size()) {
                continue;
            }
            List<CajaData> cajas = destinos.get(peso.getIndiceDestino()).getDestino().getCajas();
            if (peso.getIndiceCaja() < 0 || peso.getIndiceCaja() >= cajas.size()) {
                continue;
            }
            CajaData caja = cajas.get(peso.getIndiceCaja());
            if (peso.getPesoNetoKg() != null) {
                caja.setPesoNetoKg(peso.getPesoNetoKg());
            }
            if (peso.getPesoBrutoKg() != null) {
                caja.setPesoBrutoKg(peso.getPesoBrutoKg());
            }
        }
        envioEnCurso.getAvisosInferencia().clear();
        for (EnvioImportado.DestinoImportado destino : destinos) {
            envioEnCurso.getAvisosInferencia().addAll(
                    inferidorPesos.inferirPesosPorReferencia(destino.getDestino().getCajas())
                            .getAvisos());
        }
    }

    private List<DestinoVista> montarVistaDestinos() {
        List<DestinoVista> vista = new ArrayList<>();
        int indiceGlobal = 0;
        List<EnvioImportado.DestinoImportado> destinos = envioEnCurso.getImportado().getDestinos();
        for (int i = 0; i < destinos.size(); i++) {
            List<CajaData> cajas = destinos.get(i).getDestino().getCajas();
            List<FilaCaja> filas = new ArrayList<>();
            for (int j = 0; j < cajas.size(); j++) {
                filas.add(new FilaCaja(indiceGlobal++, j, cajas.get(j)));
            }
            vista.add(new DestinoVista(i, destinos.get(i).getDestino().getNombreDestino(), filas));
        }
        return vista;
    }

    /** Una destinación en la pantalla de revisión. */
    public record DestinoVista(int indice, String nombre, List<FilaCaja> filas) {
    }

    /**
     * Una fila de la tabla de revisión: la caja, su índice global en el
     * formulario (los inputs se llaman pesos[indiceGlobal].*) y su posición
     * dentro de la destinación (para localizarla al aplicar los pesos).
     */
    public record FilaCaja(int indiceGlobal, int indiceEnDestino, CajaData caja) {
    }
}
