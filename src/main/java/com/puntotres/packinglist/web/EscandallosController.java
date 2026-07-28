package com.puntotres.packinglist.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

import com.puntotres.packinglist.service.escandallos.EscandallosGenerationService;
import com.puntotres.packinglist.service.escandallos.ExcelEscandallos;
import com.puntotres.packinglist.service.escandallos.FicheroEscandallo;

/**
 * Procesado de escandallos del ERP, en una sola pantalla.
 *
 * No tiene nada que ver con el asistente de packing lists ni con las
 * etiquetas: no hay envío, ni cliente, ni pesos. Se suben los escandallos y
 * sale un excel.
 *
 * El caso normal es <strong>descarga directa</strong>: el POST responde con
 * los bytes del {@code .xlsx} y no hay pantalla de resultados. La pantalla
 * intermedia solo aparece si hay algo que contar (un fichero ilegible, una
 * hoja renombrada), y entonces el excel se guarda en sesión para el botón
 * «Descargar igualmente». De ahí que procesar() devuelva Object: una respuesta
 * HTTP es o el fichero o el HTML, nunca las dos cosas.
 */
@Controller
public class EscandallosController {

    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final String NOMBRE_FICHERO = "Escandallos ICSUITE.xlsx";

    private static final String VISTA = "escandallos";

    private final EscandallosGenerationService escandallosService;
    private final EscandallosEnCurso enCurso;

    public EscandallosController(EscandallosGenerationService escandallosService,
                                 EscandallosEnCurso enCurso) {
        this.escandallosService = escandallosService;
        this.enCurso = enCurso;
    }

    @GetMapping("/escandallos")
    public String entrada() {
        enCurso.reiniciar();
        return VISTA;
    }

    @PostMapping("/escandallos/procesar")
    public Object procesar(@RequestParam(name = "escandallos", required = false)
                           List<MultipartFile> ficheros, Model model) throws IOException {
        List<FicheroEscandallo> subidos = aFicheros(ficheros);
        if (subidos.isEmpty()) {
            model.addAttribute("error", "No se ha subido ningún excel de escandallo");
            return VISTA;
        }

        ExcelEscandallos excel = escandallosService.procesar(subidos);
        if (excel.estaVacio()) {
            enCurso.reiniciar();
            model.addAttribute("error", "No se ha podido procesar ningún escandallo: "
                    + "revisa que sean los excels que imprime el ERP");
            model.addAttribute("avisos", excel.avisos());
            return VISTA;
        }

        if (excel.avisos().isEmpty()) {
            enCurso.reiniciar();
            return descarga(excel.contenido());
        }

        enCurso.guardar(excel.contenido(), excel.hojas(), excel.avisos());
        model.addAttribute("hojas", excel.hojas());
        model.addAttribute("avisos", excel.avisos());
        return VISTA;
    }

    @GetMapping("/escandallos/descargar")
    public Object descargar() {
        if (enCurso.estaVacio()) {
            return "redirect:/escandallos";
        }
        return descarga(enCurso.getExcel());
    }

    // --- Internos ---

    private static ResponseEntity<byte[]> descarga(byte[] contenido) {
        return ResponseEntity.ok()
                .contentType(TIPO_XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                        .attachment().filename(NOMBRE_FICHERO).build().toString())
                .body(contenido);
    }

    /**
     * Un input de fichero vacío llega igualmente como parte del multipart; se
     * descartan aquí para que «no has subido nada» y «lo que has subido no
     * vale» sean dos mensajes distintos.
     */
    private static List<FicheroEscandallo> aFicheros(List<MultipartFile> ficheros)
            throws IOException {
        List<FicheroEscandallo> subidos = new ArrayList<>();
        if (ficheros == null) {
            return subidos;
        }
        for (MultipartFile fichero : ficheros) {
            if (fichero != null && !fichero.isEmpty()) {
                subidos.add(new FicheroEscandallo(nombreDe(fichero), fichero.getBytes()));
            }
        }
        return subidos;
    }

    private static String nombreDe(MultipartFile fichero) {
        String nombre = fichero.getOriginalFilename();
        return nombre == null || nombre.isBlank() ? "escandallo.xlsx" : nombre;
    }
}
