package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Los documentos del corte: a qué cliente le toca cada pedido y, a partir de
 * lo tecleado en la pantalla de pieles, los documentos que salen.
 *
 * Salen un Excel de órdenes con una hoja por referencia (modelo + piel) y
 * color con bolsos, y un solo Word de fotos con todos los MODELOS con bolsos
 * y fotos ("cliente - temporada - Fotos Artículos.docx"): la carpeta ULL027
 * vale para ULL027.AL0103 y para ULL027.AL0216, así que cada modelo sale una
 * sola vez. Una fila desmarcada en la tabla no saca nada.
 */
@Service
public class DocumentosCorteService {

    /** Lo que Windows no admite en un nombre de fichero, y la barra de la URL de descarga. */
    private static final Pattern CARACTERES_INSEGUROS = Pattern.compile("[\\\\/:*?\"<>|]+");

    private final List<ClienteCorte> clientes;
    private final OrdenCorteExcelBuilder ordenes = new OrdenCorteExcelBuilder();
    private final FotosCorteDocBuilder albumes = new FotosCorteDocBuilder();

    public DocumentosCorteService(List<ClienteCorte> clientes) {
        this.clientes = List.copyOf(clientes);
    }

    /**
     * Las reglas del cliente para el excel de producción, que vale para todos:
     * las suyas si tiene documentos del corte propios (cómo parte la
     * referencia y el color, qué no se corta) y, si no, las de un genérico.
     */
    public ClienteCorte reglasDeProduccion(String clave) {
        return clientePara(clave).orElseGet(() -> new ClienteCorteGenerico(clave));
    }

    /** El cliente con esa clave, o vacío si todavía no tiene documentos del corte. */
    public Optional<ClienteCorte> clientePara(String clave) {
        String buscada = ReferenciaCorte.normalizar(clave);
        return clientes.stream()
                .filter(cliente -> cliente.clave().equals(buscada))
                .findFirst();
    }

    /**
     * Escribe los documentos en {@code destino}. {@code reducida} da el JPEG
     * reducido de cada foto, o vacío si no se pudo convertir (de eso ya avisó
     * la conversión).
     */
    public ResultadoCorte generar(String cliente, String temporada, List<FilaCorte> filas,
                                  FotosTemporada fotos,
                                  Function<FotoModelo, Optional<Path>> reducida,
                                  Path destino) throws IOException {
        Files.createDirectories(destino);
        Map<Path, Optional<Imagen>> leidas = new HashMap<>();
        List<String> avisos = new ArrayList<>();
        List<OrdenCorte> fichas = new ArrayList<>();

        // Modelo → nombre del bolso y sus referencias, en el orden de las filas:
        // las fotos son del modelo, así que cada uno sale una sola vez en el Word.
        Map<String, String> modelos = new LinkedHashMap<>();
        Map<String, Set<String>> referencias = new LinkedHashMap<>();
        for (FilaCorte fila : filas) {
            if (!fila.isIncluida() || !fila.tieneBolsos()) {
                continue;
            }
            String modelo = fila.referencia().modelo();
            List<FotoModelo> delModelo = fotos.de(modelo);
            Imagen principal = principal(delModelo, fila.getFotoPrincipal(), reducida, leidas, avisos);
            List<ColorCorte> colores = fila.colores();
            for (int indice = 0; indice < colores.size(); indice++) {
                ColorCorte color = colores.get(indice);
                if (color.bolsos() > 0) {
                    fichas.add(new OrdenCorte(cliente, temporada, fila.referencia().referencia(), modelo,
                            color.nombre(), color.bolsos(), fila.pieles(indice), principal, color.prensa()));
                }
            }
            modelos.merge(modelo, fila.nombreModelo(),
                    (anterior, nuevo) -> anterior.isEmpty() ? nuevo : anterior);
            referencias.computeIfAbsent(modelo, clave -> new LinkedHashSet<>())
                    .add(fila.referencia().referencia());
        }

        List<FotosCorte> albumModelos = new ArrayList<>();
        for (Map.Entry<String, String> modelo : modelos.entrySet()) {
            List<Imagen> imagenes = new ArrayList<>();
            for (FotoModelo foto : fotos.de(modelo.getKey())) {
                imagen(foto, reducida, leidas, avisos).ifPresent(imagenes::add);
            }
            if (!imagenes.isEmpty()) {
                albumModelos.add(new FotosCorte(modelo.getKey(), modelo.getValue(),
                        List.copyOf(referencias.get(modelo.getKey())), imagenes));
            }
        }
        DocumentoCorte documentoFotos = null;
        if (!albumModelos.isEmpty()) {
            String nombre = sanear(cliente + " - " + temporada + " - Fotos Artículos.docx");
            Path fichero = destino.resolve(nombre);
            Files.write(fichero, albumes.generar(albumModelos));
            int total = albumModelos.stream().mapToInt(modelo -> modelo.fotos().size()).sum();
            List<DocumentoCorte.ContenidoModelo> contenido = albumModelos.stream()
                    .map(modelo -> new DocumentoCorte.ContenidoModelo(modelo.titulo(),
                            modelo.referencias(), modelo.fotos().size()))
                    .toList();
            documentoFotos = new DocumentoCorte(DocumentoCorte.Tipo.FOTOS, "Fotos de artículo ("
                    + plural(albumModelos.size(), "modelo", "modelos") + ", "
                    + plural(total, "foto", "fotos") + ")", nombre, fichero, contenido);
        }

        List<DocumentoCorte> documentos = new ArrayList<>();
        if (fichas.isEmpty()) {
            avisos.add("Ninguna referencia tiene bolsos que cortar: no sale el documento de "
                    + "órdenes de corte");
        } else {
            String nombre = sanear("Ordenes de corte " + cliente + " " + temporada + ".xlsx");
            Path fichero = destino.resolve(nombre);
            Files.write(fichero, ordenes.generar(fichas));
            documentos.add(new DocumentoCorte(DocumentoCorte.Tipo.ORDENES, "Órdenes de corte ("
                    + fichas.size() + (fichas.size() == 1 ? " hoja)" : " hojas)"), nombre, fichero));
        }
        if (documentoFotos != null) {
            documentos.add(documentoFotos);
        }
        return new ResultadoCorte(documentos, avisos);
    }

    private static String plural(int cuantos, String uno, String varios) {
        return cuantos + " " + (cuantos == 1 ? uno : varios);
    }

    /**
     * La elegida y, si no se pudo convertir, la primera legible del modelo:
     * una orden sin foto teniendo fotos del bolso sería un fallo que no se ve
     * hasta el puesto de corte.
     */
    private static Imagen principal(List<FotoModelo> fotos, int elegida,
                                    Function<FotoModelo, Optional<Path>> reducida,
                                    Map<Path, Optional<Imagen>> leidas, List<String> avisos) {
        List<FotoModelo> candidatas = new ArrayList<>();
        if (elegida >= 0 && elegida < fotos.size()) {
            candidatas.add(fotos.get(elegida));
        }
        candidatas.addAll(fotos);
        for (FotoModelo foto : candidatas) {
            Optional<Imagen> imagen = imagen(foto, reducida, leidas, avisos);
            if (imagen.isPresent()) {
                return imagen.get();
            }
        }
        return null;
    }

    private static Optional<Imagen> imagen(FotoModelo foto,
                                           Function<FotoModelo, Optional<Path>> reducida,
                                           Map<Path, Optional<Imagen>> leidas, List<String> avisos) {
        Optional<Path> fichero = reducida.apply(foto);
        if (fichero.isEmpty()) {
            return Optional.empty();
        }
        return leidas.computeIfAbsent(fichero.get(), ruta -> {
            try {
                return Optional.of(Imagen.de(ruta));
            } catch (IOException e) {
                avisos.add("La foto " + foto.nombreOriginal() + " de " + foto.modelo()
                        + " no se ha podido meter en el Word: no sale");
                return Optional.empty();
            }
        });
    }

    private static String sanear(String nombre) {
        return CARACTERES_INSEGUROS.matcher(nombre).replaceAll("_").replaceAll("\\s+", " ");
    }
}
