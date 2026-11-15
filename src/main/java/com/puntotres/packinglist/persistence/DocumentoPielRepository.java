package com.puntotres.packinglist.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a los documentos de las fichas de piel; los listados van sin bytes. */
public interface DocumentoPielRepository extends JpaRepository<DocumentoPiel, Long> {

    @Query("""
            select new com.puntotres.packinglist.persistence.ResumenDocumentoPiel(
                d.id, d.fichaPielId, d.nombreFichero, d.tamano, d.fecha)
            from DocumentoPiel d
            where d.fichaPielId = :ficha
            order by d.fecha, d.id""")
    List<ResumenDocumentoPiel> resumenes(@Param("ficha") Long ficha);
}
