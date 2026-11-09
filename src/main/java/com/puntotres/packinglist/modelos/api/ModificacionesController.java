package com.puntotres.packinglist.modelos.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
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

import com.puntotres.packinglist.modelos.api.dto.MensajeVista;
import com.puntotres.packinglist.modelos.api.dto.ModificacionDetalle;
import com.puntotres.packinglist.modelos.api.dto.ModificacionResumen;
import com.puntotres.packinglist.modelos.persistence.DestinoAplicacion;
import com.puntotres.packinglist.modelos.persistence.Direccion;
import com.puntotres.packinglist.modelos.persistence.EstadoModificacion;
import com.puntotres.packinglist.modelos.persistence.Mensaje;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.Prioridad;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.TipoAdjunto;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.AdjuntoNuevo;
import com.puntotres.packinglist.modelos.service.Modificaciones;

/**
 * Las modificaciones en la app: crear, listar, abrir, el ciclo (vista,
 * prioridad, título, destinos, hecha, descartada) y los mensajes.
 *
 * <b>La dirección la decide el rol de quien la crea, nunca lo que mande</b>:
 * lo que crea el taller es siempre un aviso para el despacho (su título y su
 * prioridad se ignoran: la prioridad la pone el despacho) y lo que crea el
 * despacho, una orden para el taller. Las reglas del ciclo y quién puede
 * qué dentro de él son de {@link Modificaciones}; aquí solo se pasa del
 * multipart a {@link AdjuntoNuevo} y se contesta con el resumen que ve la
 * bandeja.
 */
@RestController
@RequestMapping("/api/modificaciones")
public class ModificacionesController {

    private static final String DESPACHO = "hasAnyRole('DESPACHO','ADMIN')";

    private final Modificaciones modificaciones;

    public ModificacionesController(Modificaciones modificaciones) {
        this.modificaciones = modificaciones;
    }

    /** La prioridad que pone el despacho. */
    public record CambioPrioridad(Prioridad prioridad) {
    }

    /** Si el aviso aplica en un destino y si ya está hecho ahí. */
    public record MarcaDestino(Boolean aplica, Boolean hecho) {
    }

    /** Por qué se descarta; obligatorio. */
    public record Descarte(String motivo) {
    }

    /** El título que pone o corrige el despacho; obligatorio. */
    public record CambioTitulo(String titulo) {
    }

    @PostMapping
    public ResponseEntity<ModificacionResumen> crear(@RequestParam(required = false) Long modeloId,
                                                     @RequestParam(required = false) String tipoCambio,
                                                     @RequestParam(required = false) String titulo,
                                                     @RequestParam(required = false) String texto,
                                                     @RequestParam(required = false) Prioridad prioridad,
                                                     @RequestParam(required = false) MultipartFile audio,
                                                     @RequestParam(required = false) String audioDuracionSeg,
                                                     @RequestParam(name = "fotos", required = false)
                                                     List<MultipartFile> fotos,
                                                     @RequestParam(name = "documentos", required = false)
                                                     List<MultipartFile> documentos,
                                                     @RequestParam(required = false) Boolean guardarEnModelo,
                                                     @AuthenticationPrincipal UsuarioActual quien)
            throws IOException {
        if (modeloId == null) {
            throw new IllegalArgumentException("Falta el modelo");
        }
        boolean conDocumentos = !conContenido(documentos).isEmpty();
        boolean esTaller = quien.rol() == Rol.TALLER;
        if (esTaller && conDocumentos) {
            throw new IllegalArgumentException("El taller no adjunta documentos");
        }
        List<AdjuntoNuevo> adjuntos = adjuntos(audio, audioDuracionSeg, fotos);
        // guardarEnModelo: guardar además los documentos en documento_modelo es de la tarea 23.
        Modificacion creada;
        if (esTaller) {
            creada = modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modeloId, tipoCambio,
                    adjuntos), quien);
        } else {
            adjuntos.addAll(convertir(documentos, TipoAdjunto.DOCUMENTO));
            creada = modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(modeloId, tipoCambio,
                    titulo, texto, prioridad, adjuntos), quien);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(resumen(creada.getId()));
    }

    @GetMapping
    public List<ModificacionResumen> listar(@RequestParam(required = false) Direccion direccion,
                                            @RequestParam(name = "estado", required = false)
                                            Set<EstadoModificacion> estados,
                                            @RequestParam(required = false) Prioridad prioridad,
                                            @RequestParam(required = false) Long modelo,
                                            @RequestParam(required = false) String cliente,
                                            @RequestParam(required = false) String q,
                                            @AuthenticationPrincipal UsuarioActual quien) {
        return modificaciones.listar(new Modificaciones.Filtro(direccion, estados, prioridad, modelo, cliente, q),
                quien);
    }

    @GetMapping("/{id}")
    public ModificacionDetalle detalle(@PathVariable Long id) {
        return modificaciones.detalle(id);
    }

    @PostMapping("/{id}/vista")
    public ModificacionResumen marcarVista(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual quien) {
        modificaciones.marcarVista(id, quien);
        return resumen(id);
    }

    @PostMapping("/{id}/prioridad")
    @PreAuthorize(DESPACHO)
    public ModificacionResumen ponerPrioridad(@PathVariable Long id, @RequestBody CambioPrioridad cambio,
                                              @AuthenticationPrincipal UsuarioActual quien) {
        modificaciones.ponerPrioridad(id, cambio.prioridad(), quien);
        return resumen(id);
    }

    @PostMapping("/{id}/destinos/{destino}")
    @PreAuthorize(DESPACHO)
    public ModificacionResumen marcarDestino(@PathVariable Long id, @PathVariable DestinoAplicacion destino,
                                             @RequestBody MarcaDestino marca,
                                             @AuthenticationPrincipal UsuarioActual quien) {
        if (marca.aplica() == null || marca.hecho() == null) {
            throw new IllegalArgumentException("Falta decir si aplica y si está hecho");
        }
        modificaciones.marcarDestino(id, destino, marca.aplica(), marca.hecho(), quien);
        return resumen(id);
    }

    @PostMapping("/{id}/hecha")
    public ModificacionResumen marcarHecha(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual quien) {
        modificaciones.marcarHecha(id, quien);
        return resumen(id);
    }

    /** Corrige el título (el que puso la IA en un aviso, o el de una orden): deja de ser de la IA. */
    @PatchMapping("/{id}/titulo")
    @PreAuthorize(DESPACHO)
    public ModificacionResumen cambiarTitulo(@PathVariable Long id, @RequestBody CambioTitulo cambio,
                                             @AuthenticationPrincipal UsuarioActual quien) {
        modificaciones.cambiarTitulo(id, cambio.titulo(), quien);
        return resumen(id);
    }

    @PostMapping("/{id}/descartar")
    @PreAuthorize(DESPACHO)
    public ModificacionResumen descartar(@PathVariable Long id, @RequestBody Descarte descarte,
                                         @AuthenticationPrincipal UsuarioActual quien) {
        modificaciones.descartar(id, descarte.motivo(), quien);
        return resumen(id);
    }

    /** Una respuesta con texto, nota de voz o fotos (de los dos lados; sin documentos). */
    @PostMapping("/{id}/mensajes")
    public ResponseEntity<MensajeVista> responder(@PathVariable Long id,
                                                  @RequestParam(required = false) String texto,
                                                  @RequestParam(required = false) MultipartFile audio,
                                                  @RequestParam(required = false) String audioDuracionSeg,
                                                  @RequestParam(name = "fotos", required = false)
                                                  List<MultipartFile> fotos,
                                                  @AuthenticationPrincipal UsuarioActual quien) throws IOException {
        Mensaje mensaje = modificaciones.responder(id, texto, adjuntos(audio, audioDuracionSeg, fotos), quien);
        MensajeVista vista = modificaciones.detalle(id).mensajes().stream()
                .filter(m -> m.id().equals(mensaje.getId()))
                .findFirst()
                .orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(vista);
    }

    private ModificacionResumen resumen(Long id) {
        return modificaciones.detalle(id).resumen();
    }

    /** La nota de voz (con su duración, obligatoria) y las fotos, en ese orden. */
    private static List<AdjuntoNuevo> adjuntos(MultipartFile audio, String audioDuracionSeg,
                                               List<MultipartFile> fotos) throws IOException {
        List<AdjuntoNuevo> adjuntos = new ArrayList<>();
        if (audio != null && !audio.isEmpty()) {
            adjuntos.add(new AdjuntoNuevo(TipoAdjunto.AUDIO, nombreBase(audio.getOriginalFilename()),
                    audio.getContentType(), audio.getBytes(), duracion(audioDuracionSeg)));
        }
        adjuntos.addAll(convertir(fotos, TipoAdjunto.FOTO));
        return adjuntos;
    }

    private static List<AdjuntoNuevo> convertir(List<MultipartFile> ficheros, TipoAdjunto tipo) throws IOException {
        List<AdjuntoNuevo> nuevos = new ArrayList<>();
        for (MultipartFile fichero : conContenido(ficheros)) {
            nuevos.add(new AdjuntoNuevo(tipo, nombreBase(fichero.getOriginalFilename()), fichero.getContentType(),
                    fichero.getBytes(), null));
        }
        return nuevos;
    }

    /**
     * Sin las partes vacías: un formulario HTML manda una parte de 0 bytes y
     * sin nombre por cada campo de fichero que no se ha tocado, y eso no es
     * un adjunto.
     */
    private static List<MultipartFile> conContenido(List<MultipartFile> ficheros) {
        return ficheros == null ? List.of() : ficheros.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }

    /** El nombre sin la ruta que mandan algunos navegadores ({@code C:\fakepath\x.jpg} → {@code x.jpg}). */
    private static String nombreBase(String original) {
        if (original == null) {
            return null;
        }
        int barra = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
        return original.substring(barra + 1);
    }

    /** Los segundos de la nota de voz; sin ellos (o si no son un entero desde 0) no entra. */
    private static int duracion(String audioDuracionSeg) {
        try {
            int segundos = Integer.parseInt(audioDuracionSeg == null ? "" : audioDuracionSeg.trim());
            if (segundos >= 0) {
                return segundos;
            }
        } catch (NumberFormatException noEsUnNumero) {
            // Igual que si faltara: el mensaje de abajo.
        }
        throw new IllegalArgumentException("Falta la duración de la nota de voz");
    }
}
