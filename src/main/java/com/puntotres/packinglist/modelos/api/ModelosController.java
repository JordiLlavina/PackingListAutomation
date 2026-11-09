package com.puntotres.packinglist.modelos.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.puntotres.packinglist.modelos.api.dto.Fusion;
import com.puntotres.packinglist.modelos.api.dto.ModeloFicha;
import com.puntotres.packinglist.modelos.api.dto.ModeloResumen;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.EtiquetasPatrones;
import com.puntotres.packinglist.modelos.service.Modelos;
import com.puntotres.packinglist.persistence.Etapa;
import com.puntotres.packinglist.persistence.FotoCatalogo;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.persistence.TipoArticulo;
import com.puntotres.packinglist.service.catalogo.CatalogoModelos;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;

/**
 * Los modelos en la app: lista, ficha, alta a mano, etapa, QR de la etiqueta
 * de patrones y fotos. Mirar es de cualquiera que haya entrado (el taller
 * también); cambiar algo es del despacho.
 */
@RestController
@RequestMapping("/api")
public class ModelosController {

    private static final String DESPACHO = "hasAnyRole('DESPACHO','ADMIN')";

    /** Una foto no cambia nunca con el mismo id: se puede guardar un día en el navegador, sin compartir. */
    private static final String CACHE_FOTO = "private, max-age=86400";

    private final Modelos modelos;
    private final EtiquetasPatrones etiquetas;
    private final CatalogoModelos catalogo;
    private final FotosCatalogo fotos;

    public ModelosController(Modelos modelos, EtiquetasPatrones etiquetas, CatalogoModelos catalogo,
                             FotosCatalogo fotos) {
        this.modelos = modelos;
        this.etiquetas = etiquetas;
        this.catalogo = catalogo;
        this.fotos = fotos;
    }

    /** Lo que se cambia de un modelo: lo que no llega, no se toca. */
    public record CambiosModelo(String nombre, TipoArticulo tipo) {
    }

    /** El paso a otra etapa y, si se quiere, por qué. */
    public record CambioDeEtapa(Etapa etapa, String motivo) {
    }

    /** La referencia que se da al modelo; sin {@code confirmar}, si ya la tiene otro modelo no se juntan. */
    public record AsignacionReferencia(String codigo, Boolean confirmar) {
    }

    @GetMapping("/modelos")
    public List<ModeloResumen> listar(@RequestParam(required = false) String cliente,
                                      @RequestParam(required = false) Etapa etapa,
                                      @RequestParam(required = false) Long temporada,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) Boolean sinEtiqueta) {
        return modelos.listar(new Modelos.Filtro(cliente, etapa, temporada, q, sinEtiqueta));
    }

    @GetMapping("/modelos/{id}")
    public ModeloFicha ficha(@PathVariable Long id) {
        return modelos.ficha(id);
    }

    @PostMapping("/modelos")
    @PreAuthorize(DESPACHO)
    public ResponseEntity<ModeloResumen> darDeAlta(@RequestBody Modelos.AltaManual alta,
                                                   @AuthenticationPrincipal UsuarioActual quien) {
        Modelo modelo = modelos.darDeAlta(alta, quien);
        return ResponseEntity.status(HttpStatus.CREATED).body(resumen(modelo.getId()));
    }

    @PatchMapping("/modelos/{id}")
    @PreAuthorize(DESPACHO)
    public ModeloResumen editar(@PathVariable Long id, @RequestBody CambiosModelo cambios) {
        modelos.editar(id, cambios.nombre(), cambios.tipo());
        return resumen(id);
    }

    @PostMapping("/modelos/{id}/etapa")
    @PreAuthorize(DESPACHO)
    public ModeloResumen cambiarEtapa(@PathVariable Long id, @RequestBody CambioDeEtapa cambio,
                                      @AuthenticationPrincipal UsuarioActual quien) {
        modelos.cambiarEtapa(id, cambio.etapa(), cambio.motivo(), quien);
        return resumen(id);
    }

    /**
     * Da la referencia al modelo. Si ya la tiene otro modelo del cliente y no
     * se confirma, contesta 409 con {@code {mensaje, fusion}} (lo que se
     * juntaría); confirmado, los junta y contesta con el superviviente.
     */
    @PostMapping("/modelos/{id}/referencia")
    @PreAuthorize(DESPACHO)
    public ModeloResumen asignarReferencia(@PathVariable Long id, @RequestBody AsignacionReferencia asignacion,
                                           @AuthenticationPrincipal UsuarioActual quien) {
        Modelo modelo = modelos.asignarReferencia(id, asignacion.codigo(),
                Boolean.TRUE.equals(asignacion.confirmar()), quien);
        return resumen(modelo.getId());
    }

    /** Qué pasaría al dar esa referencia al modelo, sin tocar nada. */
    @GetMapping("/modelos/{id}/referencia/previsualizar")
    @PreAuthorize(DESPACHO)
    public Fusion previsualizarReferencia(@PathVariable Long id, @RequestParam(required = false) String codigo) {
        return modelos.previsualizarReferencia(id, codigo);
    }

    /** Junta los dos modelos (sobrevive el de menor id) y contesta con el superviviente. No se deshace. */
    @PostMapping("/modelos/{id}/fusionar-con/{otroId}")
    @PreAuthorize(DESPACHO)
    public ModeloResumen fusionar(@PathVariable Long id, @PathVariable Long otroId,
                                  @AuthenticationPrincipal UsuarioActual quien) {
        return resumen(modelos.fusionar(id, otroId, quien).getId());
    }

    @PostMapping("/modelos/{id}/etiqueta-impresa")
    @PreAuthorize(DESPACHO)
    public ResponseEntity<Void> marcarEtiquetaImpresa(@PathVariable Long id) {
        modeloQueExiste(id);
        modelos.marcarEtiquetaImpresa(List.of(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/modelos/{id}/qr.png")
    public ResponseEntity<byte[]> qr(@PathVariable Long id) {
        modeloQueExiste(id);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(etiquetas.qrPng(id));
    }

    @GetMapping("/modelos/{id}/fotos")
    public List<ResumenFoto> fotos(@PathVariable Long id) {
        modeloQueExiste(id);
        return fotos.de(id);
    }

    /**
     * Sube una o varias fotos (parte {@code fotos}, repetida). Leerlas,
     * reducirlas, darles un nombre libre y guardarlas es de
     * {@link Modelos#subirFotos}; aquí solo se pasa del multipart a
     * {@link Modelos.FotoSubida}. Contesta con todas las fotos del modelo.
     */
    @PostMapping("/modelos/{id}/fotos")
    @PreAuthorize(DESPACHO)
    public ResponseEntity<List<ResumenFoto>> subirFotos(@PathVariable Long id,
                                                        @RequestParam(name = "fotos", required = false)
                                                        List<MultipartFile> ficheros) throws IOException {
        modeloQueExiste(id);
        List<Modelos.FotoSubida> subidas = new ArrayList<>();
        for (MultipartFile fichero : ficheros == null ? List.<MultipartFile>of() : ficheros) {
            subidas.add(new Modelos.FotoSubida(fichero.getOriginalFilename(), fichero.getBytes()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(modelos.subirFotos(id, subidas));
    }

    @PostMapping("/modelos/{id}/fotos/{fotoId}/principal")
    @PreAuthorize(DESPACHO)
    public List<ResumenFoto> marcarPrincipal(@PathVariable Long id, @PathVariable Long fotoId) {
        modeloQueExiste(id);
        if (fotos.de(id).stream().noneMatch(foto -> foto.id().equals(fotoId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No hay ninguna foto con id " + fotoId + " en este modelo");
        }
        fotos.marcarPrincipal(id, fotoId);
        return fotos.de(id);
    }

    @GetMapping("/fotos/{fotoId}")
    public ResponseEntity<byte[]> foto(@PathVariable Long fotoId) {
        FotoCatalogo foto = fotos.conBytes(fotoId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay ninguna foto con id " + fotoId));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, CACHE_FOTO)
                .body(foto.getJpeg());
    }

    private ModeloResumen resumen(Long id) {
        return ModeloResumen.de(modeloQueExiste(id), fotos.principalDe(id).orElse(null));
    }

    private Modelo modeloQueExiste(Long id) {
        return catalogo.modelo(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay ningún modelo con id " + id));
    }
}
