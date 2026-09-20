package com.puntotres.packinglist.actividad;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.puntotres.packinglist.seguridad.Seccion;

/**
 * Las agregaciones son group by en la base de datos y no recuentos en Java: a
 * los dos años, cargar los eventos en memoria para contarlos haría que esa
 * página tardara segundos sin ninguna necesidad. Los tres índices de la V4
 * son los que las sostienen.
 *
 * Todas las consultas repiten el mismo bloque de filtros, con los parámetros
 * opcionales resueltos con ":x is null or campo = :x" para no tener que
 * montar criteria por cuatro combinaciones.
 */
public interface EventoActividadRepository extends JpaRepository<EventoActividad, Long> {

    String FILTRO = """
            e.momento >= :desde and e.momento < :hasta
            and (:usuarioId is null or e.usuarioId = :usuarioId)
            and (:seccion is null or e.seccion = :seccion)
            """;

    @Query("select count(distinct e.usuarioId) from EventoActividad e where " + FILTRO)
    long contarPersonas(@Param("desde") OffsetDateTime desde,
                        @Param("hasta") OffsetDateTime hasta,
                        @Param("usuarioId") Long usuarioId,
                        @Param("seccion") Seccion seccion);

    @Query("select count(e) from EventoActividad e where e.tipo = "
            + "com.puntotres.packinglist.actividad.TipoEvento.ENTRADA and " + FILTRO)
    long contarAccesos(@Param("desde") OffsetDateTime desde,
                       @Param("hasta") OffsetDateTime hasta,
                       @Param("usuarioId") Long usuarioId,
                       @Param("seccion") Seccion seccion);

    /**
     * Por tipo: lo que suman sus cantidades y cuántas veces ha pasado. Las dos
     * cosas hacen falta y no son la misma: "documentos" es la suma de
     * cantidades (cuatro packing lists en una sola generación son cuatro),
     * mientras que "envíos" es el recuento de veces (un envío importado es
     * uno, lleve las cajas que lleve).
     */
    @Query("select e.tipo, coalesce(sum(e.cantidad), 0), count(e) from EventoActividad e where "
            + FILTRO + " group by e.tipo")
    List<Object[]> agruparPorTipo(@Param("desde") OffsetDateTime desde,
                                  @Param("hasta") OffsetDateTime hasta,
                                  @Param("usuarioId") Long usuarioId,
                                  @Param("seccion") Seccion seccion);

    @Query("select e.correo, max(e.momento), "
            + "sum(case when e.tipo = com.puntotres.packinglist.actividad.TipoEvento.ENTRADA "
            + "then 1 else 0 end), count(e) "
            + "from EventoActividad e where " + FILTRO + " group by e.correo order by e.correo")
    List<Object[]> agruparPorUsuario(@Param("desde") OffsetDateTime desde,
                                     @Param("hasta") OffsetDateTime hasta,
                                     @Param("usuarioId") Long usuarioId,
                                     @Param("seccion") Seccion seccion);

    @Query("select e.seccion, count(e) from EventoActividad e where e.seccion is not null and "
            + FILTRO + " group by e.seccion order by count(e) desc")
    List<Object[]> agruparPorSeccion(@Param("desde") OffsetDateTime desde,
                                     @Param("hasta") OffsetDateTime hasta,
                                     @Param("usuarioId") Long usuarioId,
                                     @Param("seccion") Seccion seccion);

    @Query("select e.cliente, coalesce(sum(e.cantidad), 0), count(e) from EventoActividad e "
            + "where e.cliente is not null and " + FILTRO
            + " group by e.cliente order by coalesce(sum(e.cantidad), 0) desc")
    List<Object[]> agruparPorCliente(@Param("desde") OffsetDateTime desde,
                                     @Param("hasta") OffsetDateTime hasta,
                                     @Param("usuarioId") Long usuarioId,
                                     @Param("seccion") Seccion seccion);

    @Query("select e from EventoActividad e where " + FILTRO + " order by e.momento desc")
    List<EventoActividad> cronologia(@Param("desde") OffsetDateTime desde,
                                     @Param("hasta") OffsetDateTime hasta,
                                     @Param("usuarioId") Long usuarioId,
                                     @Param("seccion") Seccion seccion,
                                     Pageable pagina);
}
