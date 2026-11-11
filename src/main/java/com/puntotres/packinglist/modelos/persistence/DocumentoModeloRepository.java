package com.puntotres.packinglist.modelos.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.puntotres.packinglist.modelos.api.dto.DocumentoResumen;

/**
 * Los documentos de los modelos. El listado devuelve {@link DocumentoResumen}
 * y no la entidad, para no traerse los bytes de cada PDF cada vez que se
 * pinta una ficha.
 */
public interface DocumentoModeloRepository extends JpaRepository<DocumentoModelo, Long> {

    @Query("""
            select new com.puntotres.packinglist.modelos.api.dto.DocumentoResumen(
                d.id, d.nombre, d.mime, d.tamano, d.fecha)
            from DocumentoModelo d
            where d.modeloId = :modelo
            order by d.fecha desc, d.id desc""")
    List<DocumentoResumen> resumenes(@Param("modelo") Long modeloId);

    /** Con sus bytes: solo para pasarlos a otro modelo al juntar dos. */
    List<DocumentoModelo> findByModeloIdOrderByIdAsc(Long modeloId);

    long countByModeloId(Long modeloId);

    /** Si el modelo tiene algún documento: es historial del taller y el catálogo no puede borrarlo. */
    boolean existsByModeloId(Long modeloId);
}
