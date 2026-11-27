package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.service.catalogo.FotosCatalogo;

/**
 * Las hojas de corte de las SMS (las muestras de venta) a partir del excel de
 * pedido de SMS del cliente: una ficha por referencia y color, con la misma
 * maquetación que las órdenes de corte de producción
 * ({@link OrdenCorteExcelBuilder}), en un solo Excel.
 *
 * APC tiene lector propio ({@link ApcSmsExcel}, {@link #generar}); los demás
 * clientes se leen con IA ({@link LectorSmsIa}) y se revisan en pantalla
 * antes de llegar aquí ({@link #generarDesdeRevision}). Generar no guarda
 * nada: lo nuevo entra en el catálogo solo desde la zona de novedades
 * ({@link NovedadesSms}), con un botón.
 *
 * La imagen de cada ficha es el croquis de su fila; si no lleva, el de otra
 * fila del mismo modelo; y si ninguna lleva (la hoja HOMME no tiene columna
 * CROQUIS), la foto principal del modelo en el catálogo, que es lo único que
 * se lee de él.
 */
@Service
public class CorteSmsService {

    /** Los clientes cuyo excel de SMS se sabe leer. */
    private static final Set<String> CON_LECTOR = Set.of("APC");
    private static final Pattern CARACTERES_INSEGUROS = Pattern.compile("[\\\\/:*?\"<>|]");

    private final DocumentosCorteService documentosCorte;
    private final FotosCatalogo fotosCatalogo;
    private final OrdenCorteExcelBuilder ordenes = new OrdenCorteExcelBuilder();

    /**
     * El Excel generado, en memoria (una temporada de SMS son unas decenas de
     * fichas). {@code lineas}: las que han salido, de las que se sacan los
     * modelos, pieles y colores nuevos.
     */
    public record ResultadoSms(String nombreFichero, byte[] excel, int fichas, int muestras,
                               List<String> avisos, List<LineaSms> lineas) {

        public ResultadoSms {
            avisos = List.copyOf(avisos);
            lineas = lineas == null ? List.of() : List.copyOf(lineas);
        }
    }

    public CorteSmsService(DocumentosCorteService documentosCorte, FotosCatalogo fotosCatalogo) {
        this.documentosCorte = documentosCorte;
        this.fotosCatalogo = fotosCatalogo;
    }

    public boolean admite(String claveCliente) {
        return claveCliente != null && CON_LECTOR.contains(claveCliente)
                && documentosCorte.clientePara(claveCliente).isPresent();
    }

    /**
     * Lanza IllegalArgumentException si el cliente no tiene lector o el
     * fichero no es su excel de SMS; todo lo demás son avisos.
     */
    public ResultadoSms generar(String claveCliente, String nombreCliente, String temporada, byte[] excel)
            throws IOException {
        if (!admite(claveCliente)) {
            throw new IllegalArgumentException("las hojas de corte de SMS de " + nombreCliente
                    + " todavía están en desarrollo");
        }
        ClienteCorte cliente = documentosCorte.clientePara(claveCliente).orElseThrow();
        ApcSmsExcel.Lectura lectura = ApcSmsExcel.leer(excel, cliente);
        return generar(claveCliente, nombreCliente, temporada, lectura.lineas(), lectura.avisos(), true);
    }

    /**
     * Las hojas de un pedido de SMS leído con IA ({@link LectorSmsIa}) y
     * revisado en pantalla: solo las líneas incluidas. El modelo de cada una
     * sale de su referencia con la regla del cliente (un genérico no la parte:
     * la referencia es el modelo). {@code imagenes}: hoja → fila → las
     * imágenes de esa fila del excel ({@link ImagenesPorFila}); vacío en un PDF.
     * Sin redondita de color: no hay elipse que leer.
     *
     * Lanza IllegalArgumentException si no queda ninguna línea que cortar.
     */
    public ResultadoSms generarDesdeRevision(String claveCliente, String nombreCliente, String temporada,
                                             List<LineaSmsRevisada> revisadas,
                                             Map<String, Map<Integer, List<Imagen>>> imagenes,
                                             List<String> avisosDeLectura) throws IOException {
        ClienteCorte reglas = documentosCorte.reglasDeProduccion(claveCliente);
        List<LineaSms> lineas = new ArrayList<>();
        for (LineaSmsRevisada revisada : revisadas) {
            if (!revisada.incluir()) {
                continue;
            }
            if (!revisada.errores().isEmpty()) {
                throw new IllegalArgumentException(revisada.donde() + ": " + String.join(", ", revisada.errores()));
            }
            String modelo = reglas.partir(revisada.referencia()).modelo();
            List<Imagen> croquis = revisada.fila() == null ? List.of()
                    : imagenes.getOrDefault(revisada.hoja(), Map.of()).getOrDefault(revisada.fila(), List.of());
            lineas.add(new LineaSms(revisada.hoja(), revisada.fila() == null ? 0 : revisada.fila(),
                    revisada.referencia(), modelo.isEmpty() ? revisada.referencia() : modelo,
                    revisada.nombreModelo(), revisada.color(), "", revisada.pieles(), revisada.muestras(), croquis));
        }
        return generar(claveCliente, nombreCliente, temporada, lineas, avisosDeLectura, false);
    }

    /**
     * Las fichas de unas líneas ya leídas. {@code colorDelBolsoEnLaPiel}: el
     * camino de APC pone el color del bolso en la piel principal; en el de IA
     * ya viene puesto de la revisión, donde se ha podido cambiar.
     */
    private ResultadoSms generar(String claveCliente, String nombreCliente, String temporada, List<LineaSms> leidas,
                                 List<String> avisosDeLectura, boolean colorDelBolsoEnLaPiel) throws IOException {
        List<String> avisos = new ArrayList<>(avisosDeLectura);
        avisarRepetidas(leidas, avisos);

        Map<String, List<Imagen>> croquisDelModelo = new HashMap<>();
        for (LineaSms linea : leidas) {
            if (!linea.croquis().isEmpty()) {
                croquisDelModelo.putIfAbsent(linea.modelo(), linea.croquis());
            }
        }
        Map<String, List<Imagen>> delCatalogo = new HashMap<>();
        // En la ficha, debajo del cliente: que se vea en el taller que no es producción.
        String rotuloTemporada = temporada.trim() + " | SMS";
        List<OrdenCorte> fichas = new ArrayList<>();
        int muestras = 0;
        for (LineaSms linea : leidas) {
            List<Imagen> imagenes = linea.croquis();
            if (imagenes.isEmpty()) {
                imagenes = croquisDelModelo.getOrDefault(linea.modelo(), List.of());
            }
            if (imagenes.isEmpty()) {
                imagenes = delCatalogo.computeIfAbsent(linea.modelo(),
                        modelo -> fotoDelCatalogo(claveCliente, modelo));
            }
            fichas.add(new OrdenCorte(nombreCliente, rotuloTemporada, linea.referencia(), linea.modelo(),
                    linea.nombreModelo(), linea.color().nombre(), linea.muestras(),
                    colorDelBolsoEnLaPiel ? conColorDelBolso(linea) : linea.pieles(),
                    imagenes, false, linea.colorMuestra()));
            muestras += linea.muestras();
        }
        if (fichas.isEmpty()) {
            throw new IllegalArgumentException(colorDelBolsoEnLaPiel
                    ? "ninguna fila tiene muestras que cortar en «" + ApcSmsExcel.TITULO_MUESTRAS + "»"
                    : "no hay ninguna línea incluida que cortar");
        }
        long sinImagen = fichas.stream().filter(ficha -> ficha.fotos().isEmpty()).count();
        if (sinImagen > 0) {
            avisos.add(sinImagen + (sinImagen == 1 ? " hoja sale" : " hojas salen") + " sin imagen: su fila no "
                    + "lleva croquis y el catálogo de modelos no tiene fotos de ese modelo");
        }
        // Con la clave y no con el nombre: "APC FALL 27 - Hojas de Corte.xlsx", no "A.P.C. FALL 27 …".
        String nombre = sanear(claveCliente + " " + temporada.trim() + " - Hojas de Corte.xlsx");
        return new ResultadoSms(nombre, ordenes.generar(fichas), fichas.size(), muestras, avisos, leidas);
    }

    /**
     * En la hoja de corte, el color de la piel principal es el del bolso, el
     * de COLORIS A.P.C. tal cual, y no el de COLORIS FOURNISSEURS (decisión
     * del usuario): el cortador lee el mismo color arriba y en la piel. Las
     * combinaciones y el forro se quedan como vienen. Las etiquetas de
     * artículo de SMS siguen leyendo COLORIS FOURNISSEURS: por eso se cambia
     * aquí y no en el lector.
     */
    private static PielesArticulo conColorDelBolso(LineaSms linea) {
        PielesArticulo pieles = linea.pieles();
        return new PielesArticulo(new PielCorte(pieles.principal().nombre(), linea.color().nombre()),
                pieles.forro(), pieles.combinaciones());
    }

    /** La foto principal del modelo en el catálogo, o ninguna. */
    private List<Imagen> fotoDelCatalogo(String claveCliente, String modelo) {
        return fotosCatalogo.principalDe(claveCliente, modelo)
                .flatMap(fotosCatalogo::conBytes)
                .flatMap(foto -> {
                    try {
                        return Optional.of(Imagen.de(foto.getJpeg(), foto.getNombreFichero()));
                    } catch (IOException e) {
                        return Optional.empty();
                    }
                })
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * La misma referencia y color en dos filas sale en dos hojas, sin sumar:
     * casi siempre una de las dos es de otro modelo con el código mal copiado
     * y sumarlas escondería el error.
     */
    private static void avisarRepetidas(List<LineaSms> lineas, List<String> avisos) {
        Map<String, List<LineaSms>> porClave = new LinkedHashMap<>();
        for (LineaSms linea : lineas) {
            porClave.computeIfAbsent(linea.referencia() + " " + linea.color().claveCatalogo(),
                    clave -> new ArrayList<>()).add(linea);
        }
        for (List<LineaSms> repetidas : porClave.values()) {
            if (repetidas.size() > 1) {
                LineaSms primera = repetidas.get(0);
                avisos.add(primera.referencia() + " " + primera.color().nombre() + " está en "
                        + String.join(" y en ", repetidas.stream().map(LineaSms::donde).toList())
                        + ": sale una hoja por fila, sin sumar; revisa si alguna es de otro modelo");
            }
        }
    }

    private static String sanear(String nombre) {
        return CARACTERES_INSEGUROS.matcher(nombre).replaceAll("_").replaceAll("\\s+", " ");
    }
}
