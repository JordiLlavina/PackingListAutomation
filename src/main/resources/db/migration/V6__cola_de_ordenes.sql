-- La cola de órdenes del taller sustituye a las prioridades.
--
-- Las prioridades alta / normal / baja no servían: cambian mucho y lo que
-- importa es saber cuál es la siguiente. Desde ahora las órdenes del despacho
-- al taller que siguen abiertas van en una sola cola, cada una con su
-- posición (1ª, 2ª…), y la marca "urgente" la pone el despacho a mano.
--
-- posicion_cola: el orden dentro de la cola. Nulo en los avisos del taller,
-- que no entran en la cola. Una orden que se cierra conserva el suyo, que ya
-- no cuenta: la cola son las órdenes abiertas ordenadas por este número.
--
-- La columna prioridad se queda sin usar, con lo que tenía: nada de lo
-- guardado se pierde.
--
-- Subconjunto común a H2 y PostgreSQL, como desde la V4.

alter table modificacion add column posicion_cola integer;
alter table modificacion add column urgente boolean default false not null;

-- Las órdenes abiertas de prioridad alta eran las que corrían: pasan a urgentes.
update modificacion set urgente = true
where direccion = 'DESPACHO_A_TALLER' and prioridad = 'ALTA'
  and estado in ('NUEVA', 'VISTA', 'EN_CURSO');

-- La cola se siembra con el orden que tenían: alta, normal, baja (o sin
-- prioridad, que contaba como normal) y, a igualdad, la más antigua antes.
update modificacion set posicion_cola = 1 + (
    select count(*) from modificacion o
    where o.direccion = 'DESPACHO_A_TALLER'
      and o.estado in ('NUEVA', 'VISTA', 'EN_CURSO')
      and (
        (case o.prioridad when 'ALTA' then 0 when 'BAJA' then 2 else 1 end)
          < (case modificacion.prioridad when 'ALTA' then 0 when 'BAJA' then 2 else 1 end)
        or ((case o.prioridad when 'ALTA' then 0 when 'BAJA' then 2 else 1 end)
              = (case modificacion.prioridad when 'ALTA' then 0 when 'BAJA' then 2 else 1 end)
            and (o.creada_en < modificacion.creada_en
                 or (o.creada_en = modificacion.creada_en and o.id < modificacion.id)))
      )
)
where direccion = 'DESPACHO_A_TALLER' and estado in ('NUEVA', 'VISTA', 'EN_CURSO');
