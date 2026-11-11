package com.puntotres.packinglist.modelos.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.puntotres.packinglist.modelos.api.dto.MensajeVista;
import com.puntotres.packinglist.modelos.api.dto.ModificacionCreada;
import com.puntotres.packinglist.modelos.api.dto.ModificacionDetalle;
import com.puntotres.packinglist.modelos.api.dto.ModificacionResumen;
import com.puntotres.packinglist.modelos.persistence.DestinoAplicacion;
import com.puntotres.packinglist.modelos.persistence.Direccion;
import com.puntotres.packinglist.modelos.persistence.EstadoModificacion;
import com.puntotres.packinglist.modelos.persistence.Mensaje;
import com.puntotres.packinglist.modelos.persistence.Modificacion;
import com.puntotres.packinglist.modelos.persistence.Rol;
import com.puntotres.packinglist.modelos.persistence.TipoAdjunto;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.modelos.service.AdjuntoNuevo;
import com.puntotres.packinglist.modelos.service.Adjuntos;
import com.puntotres.packinglist.modelos.service.Documentos;
import com.puntotres.packinglist.modelos.service.Modificaciones;

/**
 * Las modificaciones en la app: crear, listar, abrir, el ciclo (vista,
 * urgente, título, destinos, hecha, descartada), los mensajes y la cola de
 * órdenes del taller ({@code /api/modificaciones/cola}).
 *
 * <b>La dirección la decide el rol de quien la crea, nunca lo que mande</b>:
 * lo que crea el taller es siempre un aviso para el despacho (su título, la
 * marca de urgente y la posición en la cola se ignoran) y lo que crea el
 * despacho, una orden para el taller. Las reglas del ciclo y quién puede
 * qué dentro de él son de {@link Modificaciones}; aquí solo se pasa del
 * multipart a {@link AdjuntoNuevo} y se contesta con el resumen que ve la
 * bandeja.
 */
@RestController
@RequestMapping("/api/modificaciones")
public class ModificacionesController {

    private static final String DESPACHO = "hasAnyRole('DESPACHO','ADMIN')";

    private static final Logger LOG = LoggerFactory.getLogger(ModificacionesController.class);

    private final Modificaciones modificaciones;
    private final Documentos documentosDelModelo;

    public ModificacionesController(Modificaciones modificaciones, Documentos documentos) {
        this.documentosDelModelo = documentos;
        this.modificaciones = modificaciones;
    }

    /** Marcar o quitar la marca de urgente. */
    public record CambioUrgente(Boolean urgente) {
    }

    /** La cola entera en su nuevo orden, de la primera a la última. */
    public record OrdenCola(List<Long> ids) {
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
    public ResponseEntity<ModificacionCreada> crear(@RequestParam(required = false) Long modeloId,
                                                    @RequestParam(required = false) String tipoCambio,
                                                    @RequestParam(required = false) String titulo,
                                                    @RequestParam(required = false) String texto,
                                                    @RequestParam(required = false) Boolean urgente,
                                                    @RequestParam(required = false) Long antesDe,
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
        Modificacion creada;
        List<Documentos.Subida> guardados = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        if (esTaller) {
            creada = modificaciones.crearAvisoDeTaller(new Modificaciones.AvisoTaller(modeloId, tipoCambio,
                    adjuntos), quien);
        } else {
            List<AdjuntoNuevo> losDocumentos = convertir(documentos, TipoAdjunto.DOCUMENTO);
            adjuntos.addAll(losDocumentos);
            // Primero la orden: sus adjuntos topan en 15 MB (Adjuntos), así que un documento mayor falla aquí
            // con ese mensaje, antes de llegar a la copia al modelo (tope de 50 MB) y sin dejar nada a medias.
            creada = modificaciones.crearOrdenParaTaller(new Modificaciones.OrdenDespacho(modeloId, tipoCambio,
                    titulo, texto, Boolean.TRUE.equals(urgente), antesDe, adjuntos), quien);
            if (Boolean.TRUE.equals(guardarEnModelo)) {
                copiarAlModelo(modeloId, losDocumentos, quien, guardados, avisos);
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ModificacionCreada(resumen(creada.getId()), guardados, avisos));
    }

    /**
     * Copia cada documento al modelo con el mismo camino que subirlo desde su
     * ficha ({@link Documentos#subir}: si es un PDF, saca sus candidatas fuera
     * de cualquier transacción). La orden ya está creada y no se deshace: lo
     * que no se pueda copiar se cuenta en los avisos, una línea por documento,
     * para que el despacho lo sepa y lo suba desde la ficha del modelo.
     */
    private void copiarAlModelo(Long modeloId, List<AdjuntoNuevo> losDocumentos, UsuarioActual quien,
                                List<Documentos.Subida> guardados, List<String> avisos) {
        for (AdjuntoNuevo documento : losDocumentos) {
            try {
                guardados.add(documentosDelModelo.subir(modeloId, documento.nombre(), documento.mime(), documento.bytes(),
                        quien));
            } catch (IllegalArgumentException | UncheckedIOException | NoSuchElementException e) {
                LOG.warn("No se ha podido copiar {} al modelo {}: {}", documento.nombre(), modeloId, e.getMessage());
                avisos.add("No se pudo guardar " + documento.nombre() + " en el modelo: " + e.getMessage());
            } catch (RuntimeException e) {
                // La orden ya está creada: un fallo que nadie esperaba aquí no puede convertirse en un 500 que
                // haga creer al despacho que la orden no ha salido (y que la mande dos veces).
                LOG.error("Error inesperado al copiar {} al modelo {}", documento.nombre(), modeloId, e);
                avisos.add("No se pudo guardar " + documento.nombre() + " en el modelo: error inesperado");
            }
        }
    }

    @GetMapping
    public List<ModificacionResumen> listar(@RequestParam(required = false) Direccion direccion,
                                            @RequestParam(name = "estado", required = false)
                                            Set<EstadoModificacion> estados,
                                            @RequestParam(required = false) Long modelo,
                                            @RequestParam(required = false) String cliente,
                                            @RequestParam(required = false) String q,
                                            @AuthenticationPrincipal UsuarioActual quien) {
        return modificaciones.listar(new Modificaciones.Filtro(direccion, estados, modelo, cliente, q),
                quien);
    }

    /** La cola de órdenes del taller, de la primera a la última (la leen los dos lados). */
    @GetMapping("/cola")
    public List<ModificacionResumen> cola() {
        return modificaciones.cola();
    }

    /**
     * Reordena la cola. Lleva la cola entera: si ya no es la que había (alguien
     * ha creado o cerrado una orden mientras tanto) es un 409 y no se toca nada.
     */
    @PutMapping("/cola")
    @PreAuthorize(DESPACHO)
    public List<ModificacionResumen> reordenarCola(@RequestBody OrdenCola orden,
                                                   @AuthenticationPrincipal UsuarioActual quien) {
        modificaciones.reordenarCola(orden.ids(), quien);
        return modificaciones.cola();
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

    /** Marca urgente una orden (y la sube la primera de la cola) o le quita la marca. */
    @PostMapping("/{id}/urgente")
    @PreAuthorize(DESPACHO)
    public ModificacionResumen marcarUrgente(@PathVariable Long id, @RequestBody CambioUrgente cambio,
                                             @AuthenticationPrincipal UsuarioActual quien) {
        if (cambio.urgente() == null) {
            throw new IllegalArgumentException("Falta decir si es urgente");
        }
        modificaciones.marcarUrgente(id, cambio.urgente(), quien);
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
            Adjuntos.comprobarTamano(nombreBase(audio.getOriginalFilename()), audio.getSize());
            adjuntos.add(new AdjuntoNuevo(TipoAdjunto.AUDIO, nombreBase(audio.getOriginalFilename()),
                    audio.getContentType(), audio.getBytes(), duracion(audioDuracionSeg)));
        }
        adjuntos.addAll(convertir(fotos, TipoAdjunto.FOTO));
        return adjuntos;
    }

    private static List<AdjuntoNuevo> convertir(List<MultipartFile> ficheros, TipoAdjunto tipo) throws IOException {
        List<AdjuntoNuevo> nuevos = new ArrayList<>();
        for (MultipartFile fichero : conContenido(ficheros)) {
            // El tamaño antes que los bytes: un fichero de 1 GB no se lee entero para rechazarlo.
            Adjuntos.comprobarTamano(nombreBase(fichero.getOriginalFilename()), fichero.getSize());
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
