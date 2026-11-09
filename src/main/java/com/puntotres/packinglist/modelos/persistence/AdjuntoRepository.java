package com.puntotres.packinglist.modelos.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Los adjuntos de los mensajes. Los listados devuelven {@link ResumenAdjunto}
 * y no la entidad, para no arrastrar los bytes de cada foto y cada nota de voz
 * cada vez que se pinta una lista.
 */
public interface AdjuntoRepository extends JpaRepository<Adjunto, Long> {

    @Query("""
            select new com.puntotres.packinglist.modelos.persistence.ResumenAdjunto(
                a.id, a.mensajeId, a.tipo, a.nombre, a.mime, a.tamano, a.duracionSeg, a.transcripcion,
                a.resumenIa, a.estadoTranscripcion, a.error, a.fecha)
            from Adjunto a
            where a.mensajeId in :mensajes
            order by a.mensajeId, a.id""")
    List<ResumenAdjunto> resumenes(@Param("mensajes") Collection<Long> mensajes);
}
