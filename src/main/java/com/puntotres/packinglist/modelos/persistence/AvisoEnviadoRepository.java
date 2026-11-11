package com.puntotres.packinglist.modelos.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Los recordatorios ya mandados (una fila por destinatario). */
public interface AvisoEnviadoRepository extends JpaRepository<AvisoEnviado, Long> {

    /** El último envío de una modificación y un tipo. */
    interface Ultimo {

        Long getModificacionId();

        AvisoEnviado.Tipo getTipo();

        LocalDateTime getEnviadoEn();
    }

    /** El último envío de cada tipo de esas modificaciones, con una sola consulta. */
    @Query("""
            select a.modificacionId as modificacionId, a.tipo as tipo, max(a.enviadoEn) as enviadoEn
            from AvisoEnviado a where a.modificacionId in :ids group by a.modificacionId, a.tipo""")
    List<Ultimo> ultimosDe(@Param("ids") Collection<Long> modificacionIds);

    /** Si ya se mandó alguno de ese tipo entre esas dos horas (desde incluida, hasta no). */
    boolean existsByTipoAndEnviadoEnGreaterThanEqualAndEnviadoEnLessThan(AvisoEnviado.Tipo tipo,
                                                                         LocalDateTime desde, LocalDateTime hasta);
}
