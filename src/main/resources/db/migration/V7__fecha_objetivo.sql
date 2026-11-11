-- La fecha objetivo de una orden para el taller (opcional).
--
-- El despacho puede decir para qué día debería estar hecha una orden. No es
-- un plazo que caduque ni un recordatorio: es una etiqueta ("Fecha: 11
-- noviembre") que va cambiando de color según se acerca o se pasa el día,
-- para que el taller vea la urgencia sin que nadie se lo diga en imperativo.
-- Nula en los avisos del taller y en las órdenes sin fecha.
--
-- Subconjunto común a H2 y PostgreSQL, como desde la V4.

alter table modificacion add column fecha_objetivo date;
