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
 * De momento solo APC, y nada se guarda: ni la temporada, ni las pieles ni
 * los croquis entran al catálogo. Las SMS mezclan modelos que ya están en el
 * catálogo con modelos nuevos, y casi siempre colores nuevos.
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

    /** El Excel generado, en memoria (una temporada de SMS son unas decenas de fichas). */
    public record ResultadoSms(String nombreFichero, byte[] excel, int fichas, int muestras,
                               List<String> avisos) {

        public ResultadoSms {
            avisos = List.copyOf(avisos);
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
        List<String> avisos = new ArrayList<>(lectura.avisos());
        avisarRepetidas(lectura.lineas(), avisos);

        Map<String, List<Imagen>> croquisDelModelo = new HashMap<>();
        for (LineaSms linea : lectura.lineas()) {
            if (!linea.croquis().isEmpty()) {
                croquisDelModelo.putIfAbsent(linea.modelo(), linea.croquis());
            }
        }
        Map<String, List<Imagen>> delCatalogo = new HashMap<>();
        // En la ficha, debajo del cliente: que se vea en el taller que no es producción.
        String rotuloTemporada = temporada.trim() + " | SMS";
        List<OrdenCorte> fichas = new ArrayList<>();
        int muestras = 0;
        for (LineaSms linea : lectura.lineas()) {
            List<Imagen> imagenes = linea.croquis();
            if (imagenes.isEmpty()) {
                imagenes = croquisDelModelo.getOrDefault(linea.modelo(), List.of());
            }
            if (imagenes.isEmpty()) {
                imagenes = delCatalogo.computeIfAbsent(linea.modelo(),
                        modelo -> fotoDelCatalogo(claveCliente, modelo));
            }
            fichas.add(new OrdenCorte(nombreCliente, rotuloTemporada, linea.referencia(), linea.modelo(),
                    linea.nombreModelo(), linea.color().nombre(), linea.muestras(), linea.pieles(),
                    imagenes, false));
            muestras += linea.muestras();
        }
        if (fichas.isEmpty()) {
            throw new IllegalArgumentException("ninguna fila tiene muestras que cortar en «"
                    + ApcSmsExcel.TITULO_MUESTRAS + "»");
        }
        long sinImagen = fichas.stream().filter(ficha -> ficha.fotos().isEmpty()).count();
        if (sinImagen > 0) {
            avisos.add(sinImagen + (sinImagen == 1 ? " hoja sale" : " hojas salen") + " sin imagen: su fila no "
                    + "lleva croquis y el catálogo de modelos no tiene fotos de ese modelo");
        }
        // Con la clave y no con el nombre: "APC FALL 27 - Hojas de Corte.xlsx", no "A.P.C. FALL 27 …".
        String nombre = sanear(claveCliente + " " + temporada.trim() + " - Hojas de Corte.xlsx");
        return new ResultadoSms(nombre, ordenes.generar(fichas), fichas.size(), muestras, avisos);
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
