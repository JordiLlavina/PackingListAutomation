package com.puntotres.packinglist.modelos.api;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

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
import com.puntotres.packinglist.service.corte.ReductorImagen;

/**
 * Los modelos en la app: lista, ficha, alta a mano, etapa, QR de la etiqueta
 * de patrones y fotos. Mirar es de cualquiera que haya entrado (el taller
 * también); cambiar algo es del despacho.
 *
 * Las fotos se reducen con {@link ReductorImagen} de {@code service.corte},
 * una excepción declarada a la disciplina de paquete de la app de modelos:
 * es una utilidad estática y sin estado, la misma que deja las fotos del
 * corte y del catálogo a 1.600 px.
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
     * Sube una o varias fotos. Se leen y se reducen todas antes de guardar
     * ninguna: si una no es una imagen, no se queda la mitad de la subida
     * guardada. Contesta con todas las fotos del modelo.
     */
    @PostMapping("/modelos/{id}/fotos")
    @PreAuthorize(DESPACHO)
    public ResponseEntity<List<ResumenFoto>> subirFotos(@PathVariable Long id,
                                                        @RequestParam(name = "fotos", required = false)
                                                        List<MultipartFile> ficheros) throws IOException {
        modeloQueExiste(id);
        if (ficheros == null || ficheros.stream().allMatch(MultipartFile::isEmpty)) {
            throw new IllegalArgumentException("No has elegido ninguna foto");
        }
        List<String> nombres = new ArrayList<>();
        List<byte[]> jpegs = new ArrayList<>();
        for (MultipartFile fichero : ficheros) {
            if (fichero.isEmpty()) {
                continue;
            }
            String nombre = nombreDe(fichero);
            BufferedImage imagen = leer(fichero, nombre);
            nombres.add(nombre);
            jpegs.add(ReductorImagen.jpeg(ReductorImagen.reducir(imagen, ReductorImagen.LADO_MAXIMO)));
        }
        for (int i = 0; i < jpegs.size(); i++) {
            fotos.guardar(id, nombres.get(i), jpegs.get(i));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(fotos.de(id));
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

    private static String nombreDe(MultipartFile fichero) {
        String original = fichero.getOriginalFilename();
        return original == null || original.isBlank() ? "foto.jpg" : original.trim();
    }

    /** La imagen del fichero; un 400 que lo nombra si no es una imagen que Java sepa leer. */
    private static BufferedImage leer(MultipartFile fichero, String nombre) {
        try (InputStream entrada = fichero.getInputStream()) {
            BufferedImage imagen = ImageIO.read(entrada);
            if (imagen != null) {
                return imagen;
            }
        } catch (IOException ilegible) {
            // Igual que si no fuera una imagen: el mensaje de abajo.
        }
        throw new IllegalArgumentException("El fichero " + nombre + " no es una imagen");
    }
}
