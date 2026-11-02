package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los comentarios tipo de la hoja de embalaje. */
public interface ComentarioEmbalajeRepository extends JpaRepository<ComentarioEmbalaje, Long> {

    List<ComentarioEmbalaje> findAllByOrderByNombreAsc();
}
