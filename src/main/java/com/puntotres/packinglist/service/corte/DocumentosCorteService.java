package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Los documentos del corte: a qué cliente le toca cada pedido y, a partir de
 * lo tecleado en la pantalla de pieles, los Word que salen.
 *
 * Salen un Word de órdenes con una página por referencia y color con bolsos,
 * y un Word de fotos por referencia con bolsos cuyo modelo tenga fotos. La
 * foto es del MODELO: la carpeta ULL027 vale para ULL027.AL0103 y para
 * ULL027.AL0216, así que las dos llevan su Word con las mismas fotos.
 */
@Service
public class DocumentosCorteService {

    /** Lo que Windows no admite en un nombre de fichero, y la barra de la URL de descarga. */
    private static final Pattern CARACTERES_INSEGUROS = Pattern.compile("[\\\\/:*?\"<>|]+");

    private final List<ClienteCorte> clientes;
    private final OrdenCorteDocBuilder ordenes = new OrdenCorteDocBuilder();
    private final FotosCorteDocBuilder albumes = new FotosCorteDocBuilder();

    public DocumentosCorteService(List<ClienteCorte> clientes) {
        this.clientes = List.copyOf(clientes);
    }

    /** El cliente con esa clave, o vacío si todavía no tiene documentos del corte. */
    public Optional<ClienteCorte> clientePara(String clave) {
        String buscada = ReferenciaCorte.normalizar(clave);
        return clientes.stream()
                .filter(cliente -> cliente.clave().equals(buscada))
                .findFirst();
    }

    /**
     * Escribe los Word en {@code destino}. {@code reducida} da el JPEG
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
        List<OrdenCorte> paginas = new ArrayList<>();
        List<DocumentoCorte> documentosFotos = new ArrayList<>();

        for (FilaCorte fila : filas) {
            if (!fila.tieneBolsos()) {
                continue;
            }
            String referencia = fila.referencia().referencia();
            List<FotoModelo> delModelo = fotos.de(fila.referencia().modelo());
            Imagen principal = principal(delModelo, fila.getFotoPrincipal(), reducida, leidas, avisos);
            for (ColorCorte color : fila.colores()) {
                if (color.bolsos() > 0) {
                    paginas.add(new OrdenCorte(cliente, temporada, referencia, color.color(),
                            color.bolsos(), fila.pieles(), principal));
                }
            }

            List<Imagen> imagenes = new ArrayList<>();
            for (FotoModelo foto : delModelo) {
                imagen(foto, reducida, leidas, avisos).ifPresent(imagenes::add);
            }
            if (!imagenes.isEmpty()) {
                String nombre = sanear("Fotos " + referencia + ".docx");
                Path fichero = destino.resolve(nombre);
                Files.write(fichero, albumes.generar(
                        new FotosCorte(temporada, referencia, fila.pieles(), imagenes)));
                documentosFotos.add(new DocumentoCorte("Fotos de " + referencia + " ("
                        + imagenes.size() + (imagenes.size() == 1 ? " foto)" : " fotos)"),
                        nombre, fichero));
            }
        }

        List<DocumentoCorte> documentos = new ArrayList<>();
        if (paginas.isEmpty()) {
            avisos.add("Ninguna referencia tiene bolsos que cortar: no sale el documento de "
                    + "órdenes de corte");
        } else {
            String nombre = sanear("Ordenes de corte " + cliente + " " + temporada + ".docx");
            Path fichero = destino.resolve(nombre);
            Files.write(fichero, ordenes.generar(paginas));
            documentos.add(new DocumentoCorte("Órdenes de corte (" + paginas.size()
                    + (paginas.size() == 1 ? " página)" : " páginas)"), nombre, fichero));
        }
        documentos.addAll(documentosFotos);
        return new ResultadoCorte(documentos, avisos);
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
