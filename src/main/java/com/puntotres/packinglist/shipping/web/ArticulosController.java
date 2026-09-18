package com.puntotres.packinglist.shipping.web;

import java.io.IOException;
import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.shipping.importacion.ImportadorArticulosCsv;
import com.puntotres.packinglist.shipping.importacion.PrevisualizacionArticulos;
import com.puntotres.packinglist.shipping.importacion.ResultadoImportacion;
import com.puntotres.packinglist.shipping.service.ArticleService;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;

/**
 * Mantenimiento del catálogo aduanero de artículos: alta y edición manual, y
 * la importación en dos pasos (previsualizar → confirmar) del CSV propio del
 * catálogo.
 */
@Controller
@RequestMapping("/envios/articulos")
public class ArticulosController {

    private final ArticleService articulos;
    private final ImportadorArticulosCsv importador;
    private final ImportacionEnCurso enCurso;

    public ArticulosController(ArticleService articulos, ImportadorArticulosCsv importador, ImportacionEnCurso enCurso) {
        this.articulos = articulos; this.importador = importador; this.enCurso = enCurso;
    }

    @GetMapping
    public String listado(Model model) {
        model.addAttribute("articulos", articulos.todos());
        return "articulos";
    }

    @PostMapping
    public String guardar(@RequestParam(required = false) Long id, @RequestParam String referencia, @RequestParam String descripcion,
                          @RequestParam String codigoHs, @RequestParam String paisOrigen,
                          @RequestParam(required = false) Double pesoNeto, @RequestParam(required = false) Double pesoBruto,
                          @RequestParam(required = false) BigDecimal valorUnitario, RedirectAttributes redirect) {
        try {
            articulos.guardar(id, referencia, descripcion, codigoHs, paisOrigen, pesoNeto, pesoBruto, valorUnitario);
            redirect.addFlashAttribute("mensaje", "Guardado el artículo " + referencia.trim() + ".");
        } catch (DatoInvalidoException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/envios/articulos";
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id, @RequestParam boolean activo) {
        articulos.activar(id, activo);
        return "redirect:/envios/articulos";
    }

    @GetMapping("/importar")
    public String importar(Model model) {
        model.addAttribute("previsualizacion", enCurso.getArticulos());
        return "articulos-importar";
    }

    @PostMapping("/importar")
    public String previsualizar(@RequestParam MultipartFile fichero, RedirectAttributes redirect) throws IOException {
        try {
            enCurso.setArticulos(importador.previsualizar(fichero.getBytes(), fichero.getOriginalFilename()));
        } catch (DatoInvalidoException | IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/envios/articulos/importar";
    }

    @PostMapping("/importar/confirmar")
    public String confirmar(RedirectAttributes redirect) {
        PrevisualizacionArticulos prev = enCurso.getArticulos();
        if (prev == null) return "redirect:/envios/articulos/importar";
        ResultadoImportacion r = importador.importar(prev);
        enCurso.setArticulos(null);
        redirect.addFlashAttribute("mensaje", r.altas() + " artículos nuevos, " + r.actualizadas() + " actualizados.");
        if (!r.errores().isEmpty()) redirect.addFlashAttribute("errores", r.errores());
        return "redirect:/envios/articulos";
    }
}
