package com.puntotres.packinglist.service.catalogo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.persistence.FotoCatalogo;
import com.puntotres.packinglist.persistence.FotoCatalogoRepository;
import com.puntotres.packinglist.persistence.Modelo;
import com.puntotres.packinglist.persistence.ResumenFoto;
import com.puntotres.packinglist.service.corte.FotoModelo;
import com.puntotres.packinglist.service.corte.FotosTemporada;

/**
 * Las fotos de los modelos del catálogo. Entran ya reducidas (JPEG de 1.600
 * px de lado como mucho, lo que deja la conversión del corte) desde la
 * carpeta de fotos de una temporada o desde la pantalla del catálogo, y
 * vuelven a salir hacia el corte como si fueran esa carpeta: así una
 * temporada nueva del mismo modelo no necesita volver a subir sus fotos.
 *
 * Una foto se identifica por su nombre de fichero dentro del modelo: la
 * misma carpeta subida dos veces no duplica nada. Como mucho una por modelo
 * es la principal (la de la orden de corte); la primera que entra lo es
 * hasta que alguien marca otra.
 *
 * Las fotos cuelgan del id del modelo. Las versiones por (cliente, código)
 * se conservan para el corte, el embalaje y la pantalla, que hablan en
 * códigos; las versiones por id sirven también para un prototipo sin código.
 */
@Service
public class FotosCatalogo {

    /** El nombre con el que sale la principal hacia el corte: el que {@link FotosTemporada#indicePorDefecto} elige. */
    static final String NOMBRE_PRINCIPAL = "principal.jpg";

    private final FotoCatalogoRepository repositorio;
    private final CatalogoModelos catalogo;

    public FotosCatalogo(FotoCatalogoRepository repositorio, CatalogoModelos catalogo) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
    }

    public List<ResumenFoto> de(String cliente, String codigoModelo) {
        return catalogo.modelo(cliente, codigoModelo).map(modelo -> de(modelo.getId())).orElse(List.of());
    }

    public List<ResumenFoto> de(Long modeloId) {
        return modeloId == null ? List.of() : repositorio.resumenes(List.of(modeloId));
    }

    /**
     * La foto que representa al modelo en un documento de una sola foto (la
     * hoja de embalaje): la principal; si ninguna lo es, la primera; si no
     * tiene fotos, vacío. Una sola regla para todos los que la necesitan: con
     * una copia en cada sitio, la misma hoja salía con foto o sin ella según
     * por dónde se generara.
     */
    public Optional<Long> principalDe(String cliente, String codigoModelo) {
        return catalogo.modelo(cliente, codigoModelo).flatMap(modelo -> principalDe(modelo.getId()));
    }

    /** La misma regla que {@link #principalDe(String, String)}, por el id del modelo. */
    public Optional<Long> principalDe(Long modeloId) {
        return principalEntre(de(modeloId));
    }

    /**
     * La misma regla que {@link #principalDe(Long)} para muchos modelos a la
     * vez, con una sola consulta: id del modelo → id de su foto, solo para los
     * que tienen alguna. Para listas, donde una consulta por modelo serían
     * cientos.
     */
    public Map<Long, Long> principalesDe(Collection<Long> modeloIds) {
        Map<Long, Long> principales = new HashMap<>();
        if (modeloIds == null || modeloIds.isEmpty()) {
            return principales;
        }
        Map<Long, List<ResumenFoto>> porModelo = new LinkedHashMap<>();
        for (ResumenFoto foto : repositorio.resumenes(modeloIds)) {
            porModelo.computeIfAbsent(foto.modeloId(), modelo -> new ArrayList<>()).add(foto);
        }
        porModelo.forEach((modelo, fotos) -> principalEntre(fotos).ifPresent(foto -> principales.put(modelo, foto)));
        return principales;
    }

    private static Optional<Long> principalEntre(List<ResumenFoto> fotos) {
        return fotos.stream().filter(ResumenFoto::principal).findFirst()
                .or(() -> fotos.stream().findFirst())
                .map(ResumenFoto::id);
    }

    /**
     * Código del modelo → sus fotos, solo para los modelos que tienen alguna,
     * por orden de código. La clave sigue siendo el código porque quien lo
     * pide (el corte, la pantalla) habla en códigos: se traducen a ids con
     * una consulta y se vuelve a agrupar por código.
     */
    public Map<String, List<ResumenFoto>> de(String cliente, Collection<String> modelos) {
        Map<String, List<ResumenFoto>> porModelo = new LinkedHashMap<>();
        if (vacio(cliente) || modelos == null || modelos.isEmpty()) {
            return porModelo;
        }
        Set<String> claves = modelos.stream().filter(modelo -> !vacio(modelo)).map(FotosCatalogo::clave)
                .collect(Collectors.toSet());
        Map<Long, String> codigoDe = new HashMap<>();
        for (Modelo modelo : catalogo.modelosDe(cliente)) {
            if (modelo.getCodigo() != null && claves.contains(modelo.getCodigo())) {
                codigoDe.put(modelo.getId(), modelo.getCodigo());
            }
        }
        if (codigoDe.isEmpty()) {
            return porModelo;
        }
        List<ResumenFoto> fotos = new ArrayList<>(repositorio.resumenes(codigoDe.keySet()));
        fotos.sort(Comparator.comparing((ResumenFoto foto) -> codigoDe.get(foto.modeloId()))
                .thenComparingInt(ResumenFoto::orden)
                .thenComparing(ResumenFoto::id));
        for (ResumenFoto foto : fotos) {
            porModelo.computeIfAbsent(codigoDe.get(foto.modeloId()), modelo -> new ArrayList<>()).add(foto);
        }
        return porModelo;
    }

    public Optional<FotoCatalogo> conBytes(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id);
    }

    /**
     * Guarda un JPEG ya reducido. Si el modelo ya tiene una foto con ese
     * nombre, devuelve esa y no guarda nada. La primera foto de un modelo
     * nace principal. El modelo se da de alta si no existía.
     */
    @Transactional
    public ResumenFoto guardar(String cliente, String codigoModelo, String nombreFichero, byte[] jpeg) {
        if (vacio(cliente) || vacio(codigoModelo) || vacio(nombreFichero) || jpeg == null || jpeg.length == 0) {
            throw new IllegalArgumentException("La foto necesita cliente, modelo, nombre y contenido");
        }
        Modelo modelo = catalogo.asegurarModelo(clave(cliente), clave(codigoModelo), "");
        return guardar(modelo.getId(), nombreFichero, jpeg);
    }

    /** Como {@link #guardar(String, String, String, byte[])}, en un modelo que ya existe. */
    @Transactional
    public ResumenFoto guardar(Long modeloId, String nombreFichero, byte[] jpeg) {
        if (modeloId == null || vacio(nombreFichero) || jpeg == null || jpeg.length == 0) {
            throw new IllegalArgumentException("La foto necesita modelo, nombre y contenido");
        }
        String elNombre = nombreFichero.trim();
        Optional<FotoCatalogo> existente = repositorio.findByModeloIdAndNombreFichero(modeloId, elNombre);
        if (existente.isPresent()) {
            return existente.get().resumen();
        }
        List<FotoCatalogo> delModelo = repositorio.findByModeloIdOrderByOrdenAscIdAsc(modeloId);
        int orden = delModelo.isEmpty() ? 0 : delModelo.get(delModelo.size() - 1).getOrden() + 1;
        FotoCatalogo nueva = new FotoCatalogo(modeloId, elNombre, orden, jpeg);
        nueva.setPrincipal(delModelo.stream().noneMatch(FotoCatalogo::isPrincipal));
        return repositorio.save(nueva).resumen();
    }

    @Transactional
    public void marcarPrincipal(String cliente, String codigoModelo, Long fotoId) {
        if (fotoId == null) {
            return;
        }
        catalogo.modelo(cliente, codigoModelo).ifPresent(modelo -> marcarPrincipal(modelo.getId(), fotoId));
    }

    @Transactional
    public void marcarPrincipal(Long modeloId, Long fotoId) {
        if (modeloId == null || fotoId == null) {
            return;
        }
        for (FotoCatalogo foto : repositorio.findByModeloIdOrderByOrdenAscIdAsc(modeloId)) {
            foto.setPrincipal(foto.getId().equals(fotoId));
        }
    }

    /** Si era la principal, la siguiente por orden pasa a serlo: un modelo con fotos siempre tiene una. */
    @Transactional
    public void borrar(Long id) {
        FotoCatalogo foto = conBytes(id).orElse(null);
        if (foto == null) {
            return;
        }
        repositorio.delete(foto);
        if (foto.isPrincipal()) {
            repositorio.findByModeloIdOrderByOrdenAscIdAsc(foto.getModeloId())
                    .stream().findFirst().ifPresent(siguiente -> siguiente.setPrincipal(true));
        }
    }

    /**
     * Las fotos de esos modelos escritas en {@code directorio/catalogo/<modelo>/}
     * y devueltas como si fueran la carpeta de fotos de la temporada, para que
     * el corte las trate igual que las subidas. La principal sale con el
     * nombre {@value #NOMBRE_PRINCIPAL}, que es el que
     * {@link FotosTemporada#indicePorDefecto} elige antes que ningún otro.
     */
    public FotosTemporada comoFotosTemporada(String cliente, Collection<String> modelos, Path directorio)
            throws IOException {
        Map<String, List<FotoModelo>> porModelo = new LinkedHashMap<>();
        for (Map.Entry<String, List<ResumenFoto>> entrada : de(cliente, modelos).entrySet()) {
            Path carpeta = Files.createDirectories(directorio.resolve("catalogo").resolve(entrada.getKey()));
            List<FotoModelo> fotos = new ArrayList<>();
            for (ResumenFoto resumen : entrada.getValue()) {
                FotoCatalogo foto = repositorio.findById(resumen.id()).orElse(null);
                if (foto == null) {
                    continue;
                }
                String nombre = foto.isPrincipal() ? NOMBRE_PRINCIPAL : foto.getNombreFichero();
                Path copia = carpeta.resolve(String.format("%05d.jpg", foto.getId()));
                Files.write(copia, foto.getJpeg());
                fotos.add(new FotoModelo(entrada.getKey(), nombre, copia));
            }
            porModelo.put(entrada.getKey(), fotos);
        }
        return new FotosTemporada(porModelo, List.of());
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String clave(String texto) {
        return texto.trim().toUpperCase(Locale.ROOT);
    }
}
