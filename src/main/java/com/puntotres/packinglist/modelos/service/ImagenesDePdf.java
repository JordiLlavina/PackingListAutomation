package com.puntotres.packinglist.modelos.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.modelos.api.dto.CandidataImagen;

/**
 * Las imágenes de un PDF que podrían ser fotos del modelo, para que el
 * despacho elija cuáles lo son: las imágenes incrustadas de al menos
 * {@value #LADO_MINIMO} × {@value #LADO_MINIMO} px (lo más pequeño son logos,
 * iconos y muestras de color) y cada página pintada a {@value #PPP_PAGINA}
 * ppp, hasta {@value #MAX_PAGINAS} páginas, porque muchas fichas técnicas son
 * dibujo vectorial y no tienen ninguna imagen que extraer.
 *
 * Las candidatas no van a la base de datos: se escriben como PNG en un
 * directorio temporal por subida ({@value #PREFIJO}{@code <lote>/}, bajo
 * {@code java.io.tmpdir}) y caducan a los 30 minutos ({@link #VIGENCIA}), que
 * es lo que tiene el despacho para elegir. Una candidata caducada ya no se
 * sirve aunque la limpieza no haya pasado todavía, y la limpieza
 * ({@link #limpiarCaducados}) borra los lotes viejos cada 10 minutos, también
 * los que dejó un arranque anterior.
 *
 * El lote y la clave llegan en la URL, así que solo se admiten letras, cifras
 * y guiones: ni {@code ..} ni barras pueden sacar la lectura del directorio
 * del lote.
 *
 * Leer el PDF con PDFBox es lento y gasta memoria: no se hace nunca dentro de
 * una transacción de la base de datos (quien llama guarda el documento antes o
 * después, no alrededor).
 */
@Service
public class ImagenesDePdf {

    /** El lado más corto de una imagen incrustada para que pueda ser una foto. */
    public static final int LADO_MINIMO = 300;

    /** La resolución a la que se pinta cada página. */
    public static final int PPP_PAGINA = 100;

    /** Cuántas páginas se miran como mucho (imágenes y página pintada). */
    public static final int MAX_PAGINAS = 20;

    /** Lo que vive un lote de candidatas desde que se escribe. */
    public static final Duration VIGENCIA = Duration.ofMinutes(30);

    /** Lo más que puede pesar un PDF del que se sacan candidatas: 50 MB. */
    public static final long MAX_BYTES = 50L * 1024 * 1024;

    /** Cómo empieza el nombre del directorio temporal de cada lote. */
    public static final String PREFIJO = "modelos-pdf-";

    /**
     * Una imagen incrustada de más píxeles no se decodifica: una foto de 40
     * MP ya ocupa 160 MB en memoria, y un PDF hecho con un escáner a toda
     * resolución tiraría el servidor. La página pintada la sigue enseñando.
     */
    static final long MAX_PIXELES = 40_000_000L;

    /** Lo más que puede medir de lado una página para pintarla: más que eso no es una ficha técnica. */
    static final int MAX_PULGADAS_PAGINA = 200;

    private static final int PUNTOS_POR_PULGADA = 72;

    /** Lo hondo que se baja por los formularios anidados buscando imágenes. */
    private static final int MAX_ANIDAMIENTO = 5;

    private static final Pattern NOMBRE_SEGURO = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private static final String NO_ES_PDF = "El fichero no es un PDF que se pueda leer";

    private static final Logger LOG = LoggerFactory.getLogger(ImagenesDePdf.class);

    private final Path base;

    /** El de la aplicación: los lotes van al directorio temporal del sistema. */
    public ImagenesDePdf() {
        this(Path.of(System.getProperty("java.io.tmpdir")));
    }

    /** Con los lotes en otro directorio, para los tests. */
    ImagenesDePdf(Path base) {
        this.base = base;
    }

    /**
     * Escribe las candidatas del PDF en el directorio del lote y las devuelve:
     * primero las imágenes incrustadas (sin repetir la misma imagen aunque
     * aparezca varias veces), en el orden en que salen, y después las páginas.
     * Una imagen o una página que PDFBox no sabe pintar se salta.
     *
     * @throws IllegalArgumentException si no es un PDF que se deje leer (también
     *                                  uno con contraseña), si pesa más de 50
     *                                  MB o si el lote no es un nombre válido
     */
    public List<CandidataImagen> extraer(String loteId, byte[] pdf) {
        if (!seguro(loteId)) {
            throw new IllegalArgumentException("El lote " + loteId + " no es válido");
        }
        if (pdf == null || pdf.length == 0) {
            throw new IllegalArgumentException("El documento está vacío");
        }
        if (pdf.length > MAX_BYTES) {
            throw new IllegalArgumentException("El documento pesa más de 50 MB");
        }
        PDDocument documento;
        try {
            documento = Loader.loadPDF(pdf);
        } catch (IOException | RuntimeException ilegible) {
            throw new IllegalArgumentException(NO_ES_PDF);
        }
        Path directorio = directorio(loteId);
        try (documento) {
            Files.createDirectories(directorio);
            int paginas = Math.min(documento.getNumberOfPages(), MAX_PAGINAS);
            List<CandidataImagen> candidatas = new ArrayList<>();
            Set<COSBase> vistas = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int pagina = 0; pagina < paginas; pagina++) {
                try {
                    buscarImagenes(documento.getPage(pagina).getResources(), vistas, candidatas, directorio, 0);
                } catch (RuntimeException rota) {
                    // Un diccionario de recursos roto: esa página se queda sin imágenes, las demás siguen.
                    LOG.warn("No se han podido leer las imágenes de la página {} del PDF del lote {}", pagina + 1,
                            loteId, rota);
                }
            }
            PDFRenderer pintor = new PDFRenderer(documento);
            for (int pagina = 0; pagina < paginas; pagina++) {
                pintarPagina(documento, pintor, pagina, directorio, loteId).ifPresent(candidatas::add);
            }
            if (candidatas.isEmpty()) {
                // Nada que elegir: no se deja un directorio vacío esperando a la limpieza.
                borrar(directorio);
            }
            return candidatas;
        } catch (IOException disco) {
            borrar(directorio);
            throw new UncheckedIOException("No se han podido guardar las imágenes del PDF", disco);
        } catch (RuntimeException roto) {
            // PDFBox ha podido abrirlo pero no recorrerlo (sin páginas legibles, un árbol de páginas roto).
            borrar(directorio);
            LOG.warn("No se ha podido recorrer el PDF del lote {}", loteId, roto);
            throw new IllegalArgumentException(NO_ES_PDF);
        }
    }

    /**
     * La página pintada a {@value #PPP_PAGINA} ppp o, si así pasaría de
     * {@link #MAX_PIXELES}, a la resolución que la deja en ese tope: el
     * tamaño de una página lo declara el propio PDF (hasta 14.400 puntos de
     * lado, y un lienzo de Illustrator aún más), y a 100 ppp una de 200
     * pulgadas serían 20.000 × 20.000 px, 1,6 GB en memoria para una sola
     * imagen. Una página de más de {@value #MAX_PULGADAS_PAGINA} pulgadas de
     * lado no es una ficha técnica: se salta con un aviso en el log. Vacío
     * también si PDFBox no sabe pintarla.
     */
    private Optional<CandidataImagen> pintarPagina(PDDocument documento, PDFRenderer pintor, int pagina,
                                                   Path directorio, String loteId) throws IOException {
        BufferedImage pintada;
        try {
            PDRectangle caja = documento.getPage(pagina).getCropBox();
            float ancho = caja.getWidth();
            float alto = caja.getHeight();
            if (!(ancho > 0) || !(alto > 0) || ancho > MAX_PULGADAS_PAGINA * PUNTOS_POR_PULGADA
                    || alto > MAX_PULGADAS_PAGINA * PUNTOS_POR_PULGADA) {
                LOG.warn("La página {} del PDF del lote {} mide {} × {} puntos: no se pinta", pagina + 1, loteId,
                        ancho, alto);
                return Optional.empty();
            }
            pintada = pintor.renderImageWithDPI(pagina, pppPara(ancho, alto));
        } catch (IOException | RuntimeException sinPintar) {
            LOG.warn("No se ha podido pintar la página {} del PDF del lote {}", pagina + 1, loteId, sinPintar);
            return Optional.empty();
        }
        // Escribirla sí puede fallar de verdad (el disco): eso no se salta, sube.
        return Optional.of(escribir(directorio, "pagina-" + (pagina + 1), pintada, "página " + (pagina + 1)));
    }

    /** {@value #PPP_PAGINA} ppp, o menos si con ellos la página pasaría de {@link #MAX_PIXELES}. */
    static float pppPara(float anchoPuntos, float altoPuntos) {
        double tope = PUNTOS_POR_PULGADA * Math.sqrt(MAX_PIXELES / ((double) anchoPuntos * altoPuntos));
        return (float) Math.min(PPP_PAGINA, tope);
    }

    /**
     * Los bytes del PNG de esa candidata; vacío si el lote o la clave no son
     * nombres válidos, si el lote ya no existe o ha caducado, o si no tiene
     * esa candidata.
     */
    public Optional<byte[]> candidata(String loteId, String clave) {
        if (!seguro(loteId) || !seguro(clave)) {
            return Optional.empty();
        }
        Path directorio = directorio(loteId);
        Path fichero = directorio.resolve(clave + ".png").normalize();
        if (!fichero.startsWith(directorio)) {
            return Optional.empty();
        }
        try {
            if (caducado(directorio)) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(fichero));
        } catch (IOException noEsta) {
            return Optional.empty();
        }
    }

    /**
     * Borra los lotes que llevan más de {@link #VIGENCIA} sin tocarse. Cada
     * 10 minutos, y no lanza nunca: una excepción aquí pararía las
     * limpiezas siguientes.
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void limpiarCaducados() {
        try (DirectoryStream<Path> lotes = Files.newDirectoryStream(base, PREFIJO + "*")) {
            for (Path lote : lotes) {
                try {
                    if (Files.isDirectory(lote) && caducado(lote)) {
                        borrar(lote);
                    }
                } catch (IOException | RuntimeException deEste) {
                    LOG.warn("No se ha podido limpiar el lote de imágenes {}", lote, deEste);
                }
            }
        } catch (IOException | RuntimeException fallo) {
            LOG.warn("No se han podido limpiar los lotes de imágenes de PDF", fallo);
        }
    }

    /**
     * Las imágenes de esos recursos que pueden ser fotos, y las de los
     * formularios que contengan (un PDF hecho con Illustrator suele meter las
     * imágenes dentro de uno).
     */
    private void buscarImagenes(PDResources recursos, Set<COSBase> vistas, List<CandidataImagen> candidatas,
                                Path directorio, int nivel) throws IOException {
        if (recursos == null || nivel > MAX_ANIDAMIENTO) {
            return;
        }
        for (COSName nombre : recursos.getXObjectNames()) {
            PDXObject objeto;
            try {
                objeto = recursos.getXObject(nombre);
            } catch (IOException | RuntimeException roto) {
                continue;
            }
            if (objeto == null || !vistas.add(objeto.getCOSObject())) {
                continue;
            }
            if (objeto instanceof PDImageXObject imagen) {
                guardarSiPuedeSerFoto(imagen, candidatas, directorio);
            } else if (objeto instanceof PDFormXObject formulario) {
                buscarImagenes(formulario.getResources(), vistas, candidatas, directorio, nivel + 1);
            }
        }
    }

    private void guardarSiPuedeSerFoto(PDImageXObject imagen, List<CandidataImagen> candidatas, Path directorio)
            throws IOException {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        if (ancho < LADO_MINIMO || alto < LADO_MINIMO || (long) ancho * alto > MAX_PIXELES) {
            return;
        }
        BufferedImage leida;
        try {
            leida = imagen.getImage();
        } catch (IOException | RuntimeException sinDecodificar) {
            // Un JPEG 2000 sin su lector, un espacio de color raro: la página pintada la sigue enseñando.
            return;
        }
        if (leida != null) {
            candidatas.add(escribir(directorio, "imagen-" + (contar(candidatas, "imagen") + 1), leida, "imagen"));
        }
    }

    private static long contar(List<CandidataImagen> candidatas, String origen) {
        return candidatas.stream().filter(candidata -> candidata.origen().equals(origen)).count();
    }

    private static CandidataImagen escribir(Path directorio, String clave, BufferedImage imagen, String origen)
            throws IOException {
        Path fichero = directorio.resolve(clave + ".png");
        if (!ImageIO.write(imagen, "png", fichero.toFile())) {
            // Un tipo de imagen que el escritor de PNG no sabe guardar: se pasa a ARGB, que sí.
            BufferedImage argb = new BufferedImage(imagen.getWidth(), imagen.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D lienzo = argb.createGraphics();
            lienzo.drawImage(imagen, 0, 0, null);
            lienzo.dispose();
            ImageIO.write(argb, "png", fichero.toFile());
        }
        return new CandidataImagen(clave, imagen.getWidth(), imagen.getHeight(), origen);
    }

    private Path directorio(String loteId) {
        return base.resolve(PREFIJO + loteId).normalize();
    }

    private static boolean caducado(Path directorio) throws IOException {
        Instant tocado = Files.getLastModifiedTime(directorio).toInstant();
        return tocado.isBefore(Instant.now().minus(VIGENCIA));
    }

    private static boolean seguro(String nombre) {
        return nombre != null && NOMBRE_SEGURO.matcher(nombre).matches();
    }

    /** Borra el directorio con lo que tenga dentro; lo que no se deje borrar se queda para la próxima limpieza. */
    private static void borrar(Path directorio) {
        if (!Files.exists(directorio)) {
            return;
        }
        try (Stream<Path> dentro = Files.walk(directorio)) {
            dentro.sorted(Comparator.reverseOrder()).forEach(ruta -> {
                try {
                    Files.deleteIfExists(ruta);
                } catch (IOException ocupado) {
                    LOG.debug("No se ha podido borrar {}", ruta, ocupado);
                }
            });
        } catch (IOException | RuntimeException fallo) {
            LOG.warn("No se ha podido borrar el lote de imágenes {}", directorio, fallo);
        }
    }
}
