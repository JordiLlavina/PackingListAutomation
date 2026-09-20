package com.puntotres.packinglist.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.puntotres.packinglist.actividad.ActividadExcelBuilder;
import com.puntotres.packinglist.actividad.ConsultaActividad;
import com.puntotres.packinglist.actividad.FiltroActividad;
import com.puntotres.packinglist.actividad.ResumenActividad;
import com.puntotres.packinglist.seguridad.Seccion;
import com.puntotres.packinglist.seguridad.UsuarioRepository;

/** Quién ha usado la aplicación y qué ha salido de ella. */
@Controller
public class ActividadController {

    private final ConsultaActividad consulta;
    private final UsuarioRepository usuarios;

    public ActividadController(ConsultaActividad consulta, UsuarioRepository usuarios) {
        this.consulta = consulta;
        this.usuarios = usuarios;
    }

    @GetMapping("/actividad")
    public String actividad(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) Seccion seccion,
            Model model) {

        FiltroActividad filtro = filtroDe(desde, hasta, usuarioId, seccion);
        ResumenActividad resumen = consulta.resumir(filtro);

        model.addAttribute("filtro", filtro);
        model.addAttribute("resumen", resumen);
        model.addAttribute("usuarios", usuarios.findAllByOrderByNombreAsc());
        model.addAttribute("secciones", Seccion.values());
        model.addAttribute("maximoSeccion", resumen.porSeccion().stream()
                .mapToLong(ResumenActividad.LineaSeccion::acciones).max().orElse(0L));
        return "actividad";
    }

    /** El mismo filtro que la pantalla, en un excel con una hoja por bloque. */
    @GetMapping("/actividad/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) Seccion seccion) {

        FiltroActividad filtro = filtroDe(desde, hasta, usuarioId, seccion);
        byte[] excel = new ActividadExcelBuilder().construir(consulta.resumir(filtro), filtro);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Actividad " + filtro.desde() + " a "
                                + filtro.hasta() + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    /** Sin fechas, los últimos treinta días: lo que se quiere ver por defecto. */
    static FiltroActividad filtroDe(LocalDate desde, LocalDate hasta, Long usuarioId, Seccion seccion) {
        return new FiltroActividad(
                desde != null ? desde : LocalDate.now().minusDays(30),
                hasta != null ? hasta : LocalDate.now(),
                usuarioId,
                seccion);
    }
}
