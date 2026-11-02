package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los comentarios tipo de la hoja de «Info de embalaje».
 *
 * La restricción única de la base distingue mayúsculas, pero para quien teclea
 * «Cajas pesadas» y « cajas PESADAS » son el mismo comentario, así que el
 * repetido se detecta aquí sin mirar mayúsculas ni espacios de sobra.
 */
@Service
@Transactional
public class ArchivoComentariosEmbalaje {

    private final ComentarioEmbalajeRepository repositorio;

    public ArchivoComentariosEmbalaje(ComentarioEmbalajeRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Todos, por nombre. */
    @Transactional(readOnly = true)
    public List<ComentarioEmbalaje> todos() {
        return repositorio.findAllByOrderByNombreAsc();
    }

    /**
     * Da de alta ({@code id} nulo) o corrige un comentario. Un nombre ya usado
     * por otra fila lanza {@link IllegalArgumentException}; la propia fila puede
     * conservar el suyo.
     */
    public ComentarioEmbalaje guardar(Long id, String nombre, String texto) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("El comentario necesita un nombre");
        }
        boolean repetido = repositorio.findAll().stream()
                .anyMatch(c -> !c.getId().equals(id) && c.getNombre().trim().equalsIgnoreCase(limpio));
        if (repetido) {
            throw new IllegalArgumentException("Ya hay un comentario llamado «" + limpio + "»");
        }
        String contenido = texto == null ? "" : texto;
        if (id == null) {
            return repositorio.save(new ComentarioEmbalaje(limpio, contenido));
        }
        ComentarioEmbalaje existente = repositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El comentario ya no existe"));
        existente.actualizar(limpio, contenido);
        return repositorio.save(existente);
    }

    @Transactional(readOnly = true)
    public Optional<ComentarioEmbalaje> uno(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id);
    }

    public void borrar(Long id) {
        if (id != null) {
            repositorio.deleteById(id);
        }
    }
}
