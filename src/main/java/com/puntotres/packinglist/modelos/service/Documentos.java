package com.puntotres.packinglist.modelos.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.modelos.api.dto.CandidataImagen;
import com.puntotres.packinglist.modelos.api.dto.DocumentoResumen;
import com.puntotres.packinglist.modelos.persistence.DocumentoModelo;
import com.puntotres.packinglist.modelos.persistence.DocumentoModeloRepository;
import com.puntotres.packinglist.modelos.seguridad.UsuarioActual;
import com.puntotres.packinglist.persistence.FotoCatalogo;
import com.puntotres.packinglist.persistence.ModeloRepository;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.service.catalogo.FotosCatalogo;
import com.puntotres.packinglist.service.corte.ReductorImagen;

/**
 * Los documentos de un modelo (fichas técnicas, PDF del cliente, hojas de
 * medidas) y la elección de las fotos que trae un PDF.
 *
 * Un documento se guarda tal cual llega, sea del tipo que sea. Si es un PDF,
 * al subirlo salen además sus candidatas ({@link ImagenesDePdf}): las imágenes
 * que podrían ser fotos del modelo, en un directorio temporal. En las fotos
 * del modelo ({@code foto_modelo}) solo entra lo que el despacho elige con
 * {@link #elegirFotos}; nunca ninguna sin elegir, porque un PDF trae logos,
 * muestras de color y páginas de texto que llenarían la galería.
 */
@Service
public class Documentos {

    /** Lo más que puede pesar un documento: 50 MB, lo mismo que se admite para sacar imágenes de un PDF. */
    public static final long MAX_BYTES = ImagenesDePdf.MAX_BYTES;

    /** Lo que cabe en {@code documento_modelo.mime}. */
    private static final int LARGO_MIME = 100;

    private static final String MIME_DESCONOCIDO = "application/octet-stream";

    private static final String CADUCADAS = "Las imágenes del PDF han caducado, vuelve a subirlo";

    private final DocumentoModeloRepository repositorio;
    private final ModeloRepository modelos;
    private final ImagenesDePdf imagenes;
    private final FotosCatalogo fotos;

    public Documentos(DocumentoModeloRepository repositorio, ModeloRepository modelos, ImagenesDePdf imagenes,
                      FotosCatalogo fotos) {
        this.repositorio = repositorio;
        this.modelos = modelos;
        this.imagenes = imagenes;
        this.fotos = fotos;
    }

    /**
     * Lo que devuelve subir un documento: el documento guardado y, si era un
     * PDF, el lote y las candidatas para elegir; si no, {@code loteId} nulo y
     * ninguna candidata.
     */
    public record Subida(DocumentoResumen documento, String loteId, List<CandidataImagen> candidatas) {
    }

    /**
     * Sube un documento al modelo. Si es un PDF (por su tipo o por su
     * extensión), saca antes sus candidatas, fuera de cualquier transacción:
     * un PDF que no se deja leer no se guarda (400). Después guarda el
     * documento.
     */
    public Subida subir(Long modeloId, String nombre, String mime, byte[] bytes, UsuarioActual quien) {
        modeloQueExiste(modeloId);
        comprobarContenido(bytes);
        String loteId = null;
        List<CandidataImagen> candidatas = List.of();
        if (esPdf(nombre, mime)) {
            loteId = UUID.randomUUID().toString();
            candidatas = imagenes.extraer(loteId, bytes);
        }
        return new Subida(guardar(modeloId, nombre, mime, bytes, quien), loteId, candidatas);
    }

    /**
     * Guarda el documento tal cual: el nombre sin la ruta que mandan algunos
     * navegadores y cortado para que quepa en su columna, y un tipo que no
     * quepa o no llegue queda como binario genérico. 400 si está vacío o pesa
     * más de 50 MB; 404 si el modelo no existe.
     */
    @Transactional
    public DocumentoResumen guardar(Long modeloId, String nombre, String mime, byte[] bytes, UsuarioActual quien) {
        modeloQueExiste(modeloId);
        comprobarContenido(bytes);
        DocumentoModelo documento = repositorio.save(new DocumentoModelo(modeloId, nombreGuardado(nombre),
                mimeGuardado(mime), bytes, quien == null ? null : quien.id(), LocalDateTime.now()));
        return documento.resumen();
    }

    /** Los del modelo, sin bytes, del más nuevo al más viejo. */
    @Transactional(readOnly = true)
    public List<DocumentoResumen> de(Long modeloId) {
        return modeloId == null ? List.of() : repositorio.resumenes(modeloId);
    }

    /** El documento con sus bytes, para servirlo. */
    @Transactional(readOnly = true)
    public Optional<DocumentoModelo> conBytes(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id);
    }

    /** 404 si no existe. */
    @Transactional
    public void borrar(Long id) {
        DocumentoModelo documento = conBytes(id)
                .orElseThrow(() -> new NoSuchElementException("No hay ningún documento con id " + id));
        repositorio.delete(documento);
    }

    /**
     * Guarda en las fotos del modelo las candidatas elegidas de un PDF
     * suyo, reducidas a {@value ReductorImagen#LADO_MAXIMO} px y en JPEG como
     * todas las del catálogo, con el nombre del documento y la clave
     * ({@code ficha-imagen-1.jpg}, o {@code ficha-imagen-1 (2).jpg} si ya
     * existe). La que se llame como {@code principal} pasa a ser la principal
     * del modelo; sin {@code principal}, la principal no cambia (salvo que el
     * modelo no tuviera fotos: la primera nace principal).
     *
     * Se leen y se reducen todas antes de guardar ninguna: si una ha caducado
     * o no existe, no se guarda ninguna (404).
     *
     * @return todas las fotos del modelo
     * @throws IllegalArgumentException si no se elige ninguna o la principal no es una de las elegidas
     * @throws NoSuchElementException   si el documento no es del modelo o el lote ha caducado
     */
    @Transactional
    public List<ResumenFoto> elegirFotos(Long modeloId, Long documentoId, String loteId, List<String> claves,
                                         String principal, UsuarioActual quien) {
        Set<String> elegidas = new LinkedHashSet<>();
        if (claves != null) {
            claves.stream().filter(clave -> clave != null && !clave.isBlank()).map(String::trim)
                    .forEach(elegidas::add);
        }
        if (elegidas.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos una imagen");
        }
        String laPrincipal = principal == null || principal.isBlank() ? null : principal.trim();
        if (laPrincipal != null && !elegidas.contains(laPrincipal)) {
            throw new IllegalArgumentException("La principal tiene que ser una de las elegidas");
        }
        DocumentoModelo documento = conBytes(documentoId)
                .filter(doc -> doc.getModeloId().equals(modeloId))
                .orElseThrow(() -> new NoSuchElementException("No hay ningún documento con id " + documentoId
                        + " en este modelo"));

        List<String> ordenadas = new ArrayList<>(elegidas);
        List<byte[]> jpegs = new ArrayList<>();
        for (String clave : ordenadas) {
            byte[] png = imagenes.candidata(loteId, clave).orElseThrow(() -> new NoSuchElementException(CADUCADAS));
            jpegs.add(jpegReducido(png));
        }

        String base = sinExtension(documento.getNombre());
        Set<String> usados = new HashSet<>();
        for (ResumenFoto existente : fotos.de(modeloId)) {
            usados.add(existente.nombreFichero().toLowerCase(Locale.ROOT));
        }
        Long idPrincipal = null;
        for (int i = 0; i < ordenadas.size(); i++) {
            String nombre = FotoCatalogo.nombreLibre(base + "-" + ordenadas.get(i) + ".jpg", usados);
            usados.add(nombre.toLowerCase(Locale.ROOT));
            ResumenFoto guardada = fotos.guardar(modeloId, nombre, jpegs.get(i));
            if (ordenadas.get(i).equals(laPrincipal)) {
                idPrincipal = guardada.id();
            }
        }
        if (idPrincipal != null) {
            fotos.marcarPrincipal(modeloId, idPrincipal);
        }
        return fotos.de(modeloId);
    }

    /** Un PDF por su tipo o, si el navegador no lo dice bien, por su extensión. */
    static boolean esPdf(String nombre, String mime) {
        return (mime != null && mime.trim().toLowerCase(Locale.ROOT).startsWith("application/pdf"))
                || (nombre != null && nombre.trim().toLowerCase(Locale.ROOT).endsWith(".pdf"));
    }

    private void modeloQueExiste(Long modeloId) {
        if (modeloId == null || !modelos.existsById(modeloId)) {
            throw new NoSuchElementException("No hay ningún modelo con id " + modeloId);
        }
    }

    private static void comprobarContenido(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("El documento está vacío");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("El documento pesa más de 50 MB");
        }
    }

    /** Sin la ruta ({@code C:\fakepath\ficha.pdf} → {@code ficha.pdf}) y cortado a lo que cabe en la columna. */
    private static String nombreGuardado(String original) {
        String nombre = original == null ? "" : original;
        int barra = Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\'));
        nombre = nombre.substring(barra + 1).trim();
        return FotoCatalogo.nombreLibre(nombre.isEmpty() ? "documento" : nombre, Set.of());
    }

    private static String mimeGuardado(String mime) {
        if (mime == null || mime.isBlank() || mime.trim().length() > LARGO_MIME) {
            return MIME_DESCONOCIDO;
        }
        return mime.trim();
    }

    /** {@code Ficha técnica.pdf} → {@code Ficha técnica}; un nombre sin extensión, tal cual. */
    private static String sinExtension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }

    private static byte[] jpegReducido(byte[] png) {
        try {
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
            if (imagen == null) {
                throw new NoSuchElementException(CADUCADAS);
            }
            return ReductorImagen.jpeg(ReductorImagen.reducir(imagen, ReductorImagen.LADO_MAXIMO));
        } catch (IOException ilegible) {
            throw new UncheckedIOException("No se ha podido pasar a JPEG una imagen del PDF", ilegible);
        }
    }
}
