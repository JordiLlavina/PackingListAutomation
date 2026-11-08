package com.puntotres.packinglist.service.etiquetasarticulo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.service.corte.ApcSmsExcel;
import com.puntotres.packinglist.service.corte.ClienteCorte;
import com.puntotres.packinglist.service.corte.DocumentosCorteService;
import com.puntotres.packinglist.service.corte.LineaSms;
import com.puntotres.packinglist.service.corte.PielCorte;
import com.puntotres.packinglist.service.corte.PielesArticulo;

/**
 * Las etiquetas pequeñas de las SMS (las muestras de venta) a partir del
 * excel de pedido de SMS del cliente: un Word con una etiqueta por muestra,
 * en el orden del excel, con la maquetación de {@link EtiquetasSmsDocBuilder}.
 *
 * El excel se lee con el mismo lector que las hojas de corte de SMS
 * ({@link ApcSmsExcel}), así que lo que cuenta como muestra es lo mismo en los
 * dos documentos: el número de «Commande SMS» de cada fila ("4 SMS" son
 * cuatro etiquetas; «PAS DE SMS» o un cero, ninguna).
 *
 * De momento solo APC, y nada se guarda: la temporada solo va escrita en la
 * etiqueta y en el nombre del documento.
 */
@Service
public class EtiquetasSmsService {

    /** Los clientes cuyo excel de SMS se sabe leer. */
    private static final Set<String> CON_LECTOR = Set.of("APC");
    private static final Pattern CARACTERES_INSEGUROS = Pattern.compile("[\\\\/:*?\"<>|]");

    private final DocumentosCorteService documentosCorte;
    private final EtiquetasSmsDocBuilder builder = new EtiquetasSmsDocBuilder();

    /** El Word generado, en memoria (una temporada de SMS son unos centenares de etiquetas). */
    public record ResultadoEtiquetasSms(String nombreFichero, byte[] word, int etiquetas, int referencias,
                                        List<String> avisos) {

        public ResultadoEtiquetasSms {
            avisos = List.copyOf(avisos);
        }

        /** Hojas A4 que hay que poner en la impresora. */
        public int hojas() {
            return (etiquetas + EtiquetasSmsDocBuilder.ETIQUETAS_POR_PAGINA - 1)
                    / EtiquetasSmsDocBuilder.ETIQUETAS_POR_PAGINA;
        }
    }

    public EtiquetasSmsService(DocumentosCorteService documentosCorte) {
        this.documentosCorte = documentosCorte;
    }

    public boolean admite(String claveCliente) {
        return claveCliente != null && CON_LECTOR.contains(claveCliente)
                && documentosCorte.clientePara(claveCliente).isPresent();
    }

    /**
     * Lanza IllegalArgumentException si el cliente no tiene lector, el fichero
     * no es su excel de SMS o ninguna fila tiene muestras; todo lo demás son
     * avisos.
     */
    public ResultadoEtiquetasSms generar(String claveCliente, String nombreCliente, String temporada,
                                         byte[] excel) throws IOException {
        if (!admite(claveCliente)) {
            throw new IllegalArgumentException("las etiquetas de SMS de " + nombreCliente
                    + " todavía están en desarrollo");
        }
        ClienteCorte cliente = documentosCorte.clientePara(claveCliente).orElseThrow();
        ApcSmsExcel.Lectura lectura = ApcSmsExcel.leer(excel, cliente);
        List<String> avisos = new ArrayList<>(lectura.avisos());
        List<EtiquetaSms> etiquetas = new ArrayList<>();
        for (LineaSms linea : lectura.lineas()) {
            EtiquetaSms etiqueta = new EtiquetaSms(temporada, linea.referencia(), linea.nombreModelo(),
                    piel(linea.pieles()), colorPiel(linea.pieles()));
            avisar(linea, etiqueta, avisos);
            for (int muestra = 0; muestra < linea.muestras(); muestra++) {
                etiquetas.add(etiqueta);
            }
        }
        if (etiquetas.isEmpty()) {
            throw new IllegalArgumentException("ninguna fila tiene muestras en «Commande SMS»: no hay "
                    + "ninguna etiqueta que imprimir");
        }
        // Con la clave y no con el nombre: "APC FALL 27 - Etiquetas SMS.docx", no "A.P.C. FALL 27 …".
        String nombre = sanear(claveCliente + " " + temporada.trim() + " - Etiquetas SMS.docx");
        return new ResultadoEtiquetasSms(nombre, builder.generar(etiquetas), etiquetas.size(),
                lectura.lineas().size(), avisos);
    }

    /**
     * El nombre de la piel tal como lo escribe el pedido en «ARTICLE»: las de
     * un bolso de dos pieles, juntas con «+».
     */
    static String piel(PielesArticulo pieles) {
        return String.join(" + ", Stream.concat(Stream.of(pieles.principal()), pieles.combinaciones().stream())
                .map(PielCorte::nombre).filter(nombre -> !nombre.isEmpty()).toList());
    }

    /**
     * El color de la piel de «COLORIS FOURNISSEURS»: el de cada piel, juntos
     * con «/» como en el pedido. El lector solo los reparte entre las pieles
     * si hay tantos colores como pieles; si no, el texto entero va en la
     * principal, y juntarlos lo devuelve igual.
     */
    static String colorPiel(PielesArticulo pieles) {
        return String.join("/", Stream.concat(Stream.of(pieles.principal()), pieles.combinaciones().stream())
                .map(PielCorte::color).filter(color -> !color.isEmpty()).toList());
    }

    /** Un dato que falta deja su línea en blanco; una línea que no cabe ni a la letra mínima, se sale. */
    private static void avisar(LineaSms linea, EtiquetaSms etiqueta, List<String> avisos) {
        List<String> faltan = new ArrayList<>();
        if (etiqueta.nombreModelo() == null || etiqueta.nombreModelo().isBlank()) {
            faltan.add("el nombre del modelo («MODELE»)");
        }
        if (etiqueta.piel().isEmpty()) {
            faltan.add("la piel («ARTICLE»)");
        }
        if (etiqueta.colorPiel().isEmpty()) {
            faltan.add("el color de la piel («COLORIS FOURNISSEURS»)");
        }
        if (!faltan.isEmpty()) {
            avisos.add(linea.donde() + " (" + linea.referencia() + "): sin " + String.join(", ni ", faltan)
                    + ": su línea sale en blanco en la etiqueta");
        }
        for (String texto : etiqueta.lineas()) {
            if (!EtiquetasSmsDocBuilder.cabe(texto)) {
                avisos.add(linea.donde() + " (" + linea.referencia() + "): «" + texto + "» no cabe en la "
                        + "etiqueta ni con la letra más pequeña: ocupa dos líneas y la última no se ve. "
                        + "Revísala en el Word antes de imprimir");
            }
        }
    }

    private static String sanear(String nombre) {
        return CARACTERES_INSEGUROS.matcher(nombre).replaceAll("_").replaceAll("\\s+", " ");
    }
}
