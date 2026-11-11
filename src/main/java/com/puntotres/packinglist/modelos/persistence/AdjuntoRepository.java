package com.puntotres.packinglist.modelos.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Apunta cómo ha ido la transcripción solo si el adjunto sigue en
     * {@code pendiente}, en una sola sentencia: si mientras se transcribía
     * alguien lo ha marcado a mano o lo ha vuelto a encolar, no se pisa.
     * Devuelve cuántas filas ha cambiado (0 o 1).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Adjunto a
            set a.estadoTranscripcion = :estado, a.transcripcion = :transcripcion, a.error = :error
            where a.id = :id and a.estadoTranscripcion = :pendiente""")
    int marcarTranscripcionSiSigue(@Param("id") Long id, @Param("pendiente") EstadoTranscripcion pendiente,
                                   @Param("estado") EstadoTranscripcion estado,
                                   @Param("transcripcion") String transcripcion, @Param("error") String error);

    /** Los ids de las notas de voz en ese estado, de la más vieja a la más nueva. */
    @Query("select a.id from Adjunto a where a.tipo = :tipo and a.estadoTranscripcion = :estado order by a.id")
    List<Long> idsPorTipoYEstado(@Param("tipo") TipoAdjunto tipo, @Param("estado") EstadoTranscripcion estado);
}
