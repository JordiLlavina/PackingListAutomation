package com.puntotres.packinglist.service.catalogo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.persistence.FotoCatalogo;
import com.puntotres.packinglist.persistence.FotoCatalogoRepository;
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
        if (vacio(cliente) || vacio(codigoModelo)) {
            return List.of();
        }
        return repositorio.resumenes(clave(cliente), List.of(clave(codigoModelo)));
    }

    /** Modelo → sus fotos, solo para los modelos que tienen alguna. */
    public Map<String, List<ResumenFoto>> de(String cliente, Collection<String> modelos) {
        Map<String, List<ResumenFoto>> porModelo = new LinkedHashMap<>();
        if (vacio(cliente) || modelos == null || modelos.isEmpty()) {
            return porModelo;
        }
        List<String> claves = modelos.stream().filter(modelo -> !vacio(modelo)).map(FotosCatalogo::clave).toList();
        for (ResumenFoto foto : repositorio.resumenes(clave(cliente), claves)) {
            porModelo.computeIfAbsent(foto.codigoModelo(), modelo -> new ArrayList<>()).add(foto);
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
        String elCliente = clave(cliente);
        String elModelo = clave(codigoModelo);
        String elNombre = nombreFichero.trim();
        Optional<FotoCatalogo> existente = repositorio.findByClienteAndCodigoModeloAndNombreFichero(
                elCliente, elModelo, elNombre);
        if (existente.isPresent()) {
            return existente.get().resumen();
        }
        catalogo.asegurarModelo(elCliente, elModelo, "");
        List<FotoCatalogo> delModelo = repositorio.findByClienteAndCodigoModeloOrderByOrdenAscIdAsc(elCliente, elModelo);
        int orden = delModelo.isEmpty() ? 0 : delModelo.get(delModelo.size() - 1).getOrden() + 1;
        FotoCatalogo nueva = new FotoCatalogo(elCliente, elModelo, elNombre, orden, jpeg);
        nueva.setPrincipal(delModelo.stream().noneMatch(FotoCatalogo::isPrincipal));
        return repositorio.save(nueva).resumen();
    }

    @Transactional
    public void marcarPrincipal(String cliente, String codigoModelo, Long fotoId) {
        if (vacio(cliente) || vacio(codigoModelo) || fotoId == null) {
            return;
        }
        for (FotoCatalogo foto : repositorio.findByClienteAndCodigoModeloOrderByOrdenAscIdAsc(
                clave(cliente), clave(codigoModelo))) {
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
            repositorio.findByClienteAndCodigoModeloOrderByOrdenAscIdAsc(foto.getCliente(), foto.getCodigoModelo())
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
