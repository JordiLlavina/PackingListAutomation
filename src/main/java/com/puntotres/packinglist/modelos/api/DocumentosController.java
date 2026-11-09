package com.puntotres.packinglist.modelos.api;

import java.io.IOException;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.puntotres.packinglist.modelos.persistence.DocumentoModelo;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.Documentos;
import com.puntotres.packinglist.modelos.service.ImagenesDePdf;
import com.puntotres.packinglist.persistence.ResumenFoto;

/**
 * Los documentos de un modelo: subirlos (si es un PDF, con las candidatas a
 * foto que trae), elegir cuáles de esas candidatas son fotos del modelo,
 * abrirlos y quitarlos. Abrir es de cualquiera que haya entrado (el taller
 * los consulta en la tablet); lo demás, del despacho.
 *
 * Los documentos se sirven con {@link DescargaFichero}, la misma regla que los
 * adjuntos de los mensajes: un PDF o una imagen se abren en el navegador, un
 * {@code text/html} se descarga.
 */
@RestController
@RequestMapping("/api")
public class DocumentosController {

    private static final String DESPACHO = "hasAnyRole('DESPACHO','ADMIN')";

    /** Una candidata vive media hora y no cambia: se puede guardar ese rato en el navegador, sin compartir. */
    private static final String CACHE_CANDIDATA = "private, max-age=1800";

    private final Documentos documentos;
    private final ImagenesDePdf imagenes;

    public DocumentosController(Documentos documentos, ImagenesDePdf imagenes) {
        this.documentos = documentos;
        this.imagenes = imagenes;
    }

    /** Las candidatas que el despacho ha marcado como fotos del modelo y la que hace de principal. */
    public record Eleccion(String lote, List<String> claves, String principal) {
    }

    /**
     * Sube un documento (parte {@code documento}). Contesta 201 con el
     * documento y, si es un PDF, el lote y sus candidatas; ninguna entra en
     * las fotos del modelo hasta que se eligen.
     */
    @PostMapping("/modelos/{id}/documentos")
    @PreAuthorize(DESPACHO)
    public ResponseEntity<Documentos.Subida> subir(@PathVariable Long id,
                                                   @RequestParam(name = "documento", required = false)
                                                   MultipartFile fichero,
                                                   @AuthenticationPrincipal UsuarioActual quien) throws IOException {
        if (fichero == null || fichero.isEmpty()) {
            throw new IllegalArgumentException("No has elegido ningún documento");
        }
        Documentos.Subida subida = documentos.subir(id, fichero.getOriginalFilename(), fichero.getContentType(),
                fichero.getBytes(), quien);
        return ResponseEntity.status(HttpStatus.CREATED).body(subida);
    }

    @GetMapping("/modelos/{id}/documentos/candidatas/{lote}/{clave}")
    public ResponseEntity<byte[]> candidata(@PathVariable Long id, @PathVariable String lote,
                                            @PathVariable String clave) {
        byte[] png = imagenes.candidata(lote, clave).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Las imágenes del PDF han caducado, vuelve a subirlo"));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CANDIDATA)
                .body(png);
    }

    /** Guarda en las fotos del modelo SOLO las candidatas elegidas y contesta con todas sus fotos. */
    @PostMapping("/modelos/{id}/documentos/{documentoId}/elegir-fotos")
    @PreAuthorize(DESPACHO)
    public List<ResumenFoto> elegirFotos(@PathVariable Long id, @PathVariable Long documentoId,
                                         @RequestBody Eleccion eleccion,
                                         @AuthenticationPrincipal UsuarioActual quien) {
        return documentos.elegirFotos(id, documentoId, eleccion.lote(), eleccion.claves(), eleccion.principal(),
                quien);
    }

    @GetMapping("/documentos/{id}")
    public ResponseEntity<Resource> abrir(@PathVariable Long id) {
        DocumentoModelo documento = documentos.conBytes(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay ningún documento con id " + id));
        return DescargaFichero.servir(documento.getNombre(), documento.getMime(), documento.getBytes());
    }

    @DeleteMapping("/documentos/{id}")
    @PreAuthorize(DESPACHO)
    public ResponseEntity<Void> quitar(@PathVariable Long id) {
        documentos.borrar(id);
        return ResponseEntity.noContent().build();
    }
}
