package com.puntotres.packinglist.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a las fotos de las fichas de piel. Los listados devuelven
 * {@link ResumenFotoPiel} para no arrastrar los bytes al pintar una tabla.
 */
public interface FotoPielRepository extends JpaRepository<FotoPiel, Long> {

    @Query("""
            select new com.puntotres.packinglist.persistence.ResumenFotoPiel(
                f.id, f.fichaPielId, f.nombreFichero, f.principal, f.orden)
            from FotoPiel f
            where f.fichaPielId in :fichas
            order by f.fichaPielId, f.orden, f.id""")
    List<ResumenFotoPiel> resumenes(@Param("fichas") Collection<Long> fichas);

    List<FotoPiel> findByFichaPielIdOrderByOrdenAscIdAsc(Long fichaPielId);
}
