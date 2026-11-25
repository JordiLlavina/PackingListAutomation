-- El peso del artículo pasa de "neto de una caja llena" a "neto por unidad"
-- (decisión del usuario, noviembre de 2026). El de la caja llena solo se
-- podía guardar sabiendo cuántas unidades caben en la caja, y la revisión del
-- packing casi nunca lo sabe: el de la unidad es del artículo y no depende ni
-- del cartón ni de cómo de llena vaya cada caja.
--
-- Conversión: el de la caja llena entre las unidades por caja, a cuatro
-- decimales (un cinturón pesa del orden de 0,15 kg por unidad y con dos se
-- perdería). Un peso sin unidades por caja no tiene de dónde sacar la unidad
-- y se pierde: no hay otro dato del que deducirlo.
--
-- SQL del subconjunto común a H2 y PostgreSQL: el round va sobre numeric
-- porque PostgreSQL no tiene round(double precision, int).

alter table articulo add column peso_neto_unidad_kg float(53);

update articulo
   set peso_neto_unidad_kg = round(cast(peso_neto_kg / unidades_por_caja as numeric(20, 10)), 4)
 where peso_neto_kg is not null
   and unidades_por_caja is not null
   and unidades_por_caja > 0;

alter table articulo drop column peso_neto_kg;
