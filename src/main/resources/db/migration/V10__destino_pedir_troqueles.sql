-- Un tercer sitio donde aplicar un aviso del taller: pedir troqueles nuevos
-- (DestinoAplicacion.PEDIR_TROQUELES). Los avisos nuevos lo traen solo; aquí
-- se añade a los que siguen abiertos, con la misma regla que al crearlos
-- (Modificaciones.aplicaDeEntrada): aplica si entre sus razones está
-- «Medidas / Patrones» o «Cambio de troqueles», y si no nace con «No aplica».
--
-- Los avisos ya cerrados (HECHA, DESCARTADA) no lo ganan: a uno hecho le
-- abriría una tarea que nadie pidió, y ponerle «No aplica» sería decir algo
-- que nadie dijo. Las razones van separadas por comas en tipo_cambio (V8),
-- así que se buscan con comas alrededor para no casar media clave.
insert into modificacion_destino (modificacion_id, destino, aplica)
select m.id,
       'PEDIR_TROQUELES',
       case
           when (',' || coalesce(m.tipo_cambio, '') || ',') like '%,MEDIDAS_PATRONES,%'
             or (',' || coalesce(m.tipo_cambio, '') || ',') like '%,TROQUELES,%'
           then true
           else false
       end
from modificacion m
where m.direccion = 'TALLER_A_DESPACHO'
  and m.estado in ('NUEVA', 'VISTA', 'EN_CURSO')
  and not exists (select 1 from modificacion_destino d
                  where d.modificacion_id = m.id and d.destino = 'PEDIR_TROQUELES');
