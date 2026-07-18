package com.puntotres.packinglist.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.service.EnvioImportService;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.PackingListGenerationService;
import com.puntotres.packinglist.service.PaletAssignmentService;
import com.puntotres.packinglist.service.ResultadoAsignacion;
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
    private final ObjectMapper mapper;
    private final EnvioEnCurso envioEnCurso;

    public PackingListController(EnvioImportService importador,
                                 PaletAssignmentService asignadorPalets,
                                 WeightInferenceService inferidorPesos,
                                 PackingListGenerationService generador,
                                 ObjectMapper mapper,
                                 EnvioEnCurso envioEnCurso) {
        this.importador = importador;
        this.asignadorPalets = asignadorPalets;
        this.inferidorPesos = inferidorPesos;
        this.generador = generador;
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
        return "entrada";
    }

    @PostMapping("/importar")
    public String importar(@Valid @ModelAttribute EnvioForm envioForm,
                           BindingResult errores, Model model) {
        if (errores.hasErrors()) {
            return "entrada";
        }

        EnvioInput envio;
        try {
            envio = mapper.readValue(envioForm.getJson(), EnvioInput.class);
        } catch (JsonProcessingException e) {
            model.addAttribute("errorJson", "El JSON no es válido: " + e.getOriginalMessage());
            return "entrada";
        }
        if (envio.getDestinos() == null || envio.getDestinos().isEmpty()) {
            model.addAttribute("errorJson", "El JSON no contiene ninguna destinación ('destinos')");
            return "entrada";
        }

        DatosEnvio cabecera = new DatosEnvio();
        cabecera.setTemporada(envioForm.getTemporada());
        cabecera.setNumeroFactura(envioForm.getNumeroFactura());
        cabecera.setFechaFactura(envioForm.getFechaFactura());
        cabecera.setFechaEnvio(envioForm.getFechaEnvio());

        // Mismo encadenado que Main.java: importar -> asignar -> inferir.
        EnvioImportado importado = importador.importar(envio);
        envioEnCurso.reiniciar();
        envioEnCurso.setCabecera(cabecera);
        envioEnCurso.setImportado(importado);
        for (EnvioImportado.DestinoImportado destino : importado.getDestinos()) {
            ResultadoAsignacion asignacion =
                    asignadorPalets.asignar(destino.getDestino(), destino.getPalets());
            envioEnCurso.getAvisosPalets().addAll(asignacion.getAvisos());
            envioEnCurso.getCajasSinPalet().addAll(asignacion.getCajasSinPalet());
            inferidorPesos.inferirPesosPorReferencia(destino.getDestino().getCajas());
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
        try {
            for (EnvioImportado.DestinoImportado destino : envioEnCurso.getImportado().getDestinos()) {
                excels.addAll(generador.generarPorModeloYColor(
                        destino.getDestino(), envioEnCurso.getCabecera()));
            }
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error",
                    "No se pudieron generar los excels: " + e.getMessage());
            return "redirect:/revision";
        }
        envioEnCurso.getExcels().clear();
        envioEnCurso.getExcels().addAll(excels);
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
     * Aplica los pesos introducidos a mano sobre las CajaData de la sesión y
     * re-ejecuta la inferencia: un bruto añadido a mano puede desbloquear el
     * resto de su referencia (la inferencia solo rellena nulls, nunca pisa
     * un valor manual).
     */
    private void aplicarPesosYReinferir(RevisionForm form) {
        List<EnvioImportado.DestinoImportado> destinos = envioEnCurso.getImportado().getDestinos();
        for (RevisionForm.PesoEditado peso : form.getPesos()) {
            if (peso == null || peso.getIndiceDestino() < 0
                    || peso.getIndiceDestino() >= destinos.size()) {
                continue;
            }
            destinos.get(peso.getIndiceDestino()).getDestino().getCajas().stream()
                    .filter(caja -> caja.getNumeroCaja() == peso.getNumeroCaja())
                    .findFirst()
                    .ifPresent(caja -> {
                        if (peso.getPesoNetoKg() != null) {
                            caja.setPesoNetoKg(peso.getPesoNetoKg());
                        }
                        if (peso.getPesoBrutoKg() != null) {
                            caja.setPesoBrutoKg(peso.getPesoBrutoKg());
                        }
                    });
        }
        for (EnvioImportado.DestinoImportado destino : destinos) {
            inferidorPesos.inferirPesosPorReferencia(destino.getDestino().getCajas());
        }
    }

    private List<DestinoVista> montarVistaDestinos() {
        List<DestinoVista> vista = new ArrayList<>();
        int indiceGlobal = 0;
        List<EnvioImportado.DestinoImportado> destinos = envioEnCurso.getImportado().getDestinos();
        for (int i = 0; i < destinos.size(); i++) {
            List<FilaCaja> filas = new ArrayList<>();
            for (CajaData caja : destinos.get(i).getDestino().getCajas()) {
                filas.add(new FilaCaja(indiceGlobal++, caja));
            }
            vista.add(new DestinoVista(i, destinos.get(i).getDestino().getNombreDestino(), filas));
        }
        return vista;
    }

    /** Una destinación en la pantalla de revisión. */
    public record DestinoVista(int indice, String nombre, List<FilaCaja> filas) {
    }

    /**
     * Una fila de la tabla de revisión: la caja más su índice global en el
     * formulario (los inputs de pesos se llaman pesos[indiceGlobal].*).
     */
    public record FilaCaja(int indiceGlobal, CajaData caja) {
    }
}
