-- Una temporada guarda los DOS excels que la describen, en la misma fila:
--
--   excel_pedido      el excel de pedido del cliente (AMI, APC). Obligatorio
--                     para los clientes con pedido-cliente; los genéricos no
--                     tienen.
--   excel_produccion  el excel de producción de Puntotres. Obligatorio para
--                     los genéricos; opcional para AMI y APC.
--
-- Hasta aquí una temporada guardaba UN excel y tipo_excel decía cuál era. Lo
-- guardado no se pierde: el excel de una temporada de tipo PRODUCCION pasa a
-- su columna de producción, y el de una de tipo PEDIDO se queda donde estaba
-- con el nombre nuevo.

alter table temporada_guardada add column excel_produccion blob;
alter table temporada_guardada add column nombre_fichero_produccion varchar(255);

update temporada_guardada
   set excel_produccion = excel,
       nombre_fichero_produccion = nombre_fichero
 where tipo_excel = 'PRODUCCION';

alter table temporada_guardada alter column excel set null;
alter table temporada_guardada alter column nombre_fichero set null;

update temporada_guardada
   set excel = null,
       nombre_fichero = null
 where tipo_excel = 'PRODUCCION';

alter table temporada_guardada alter column excel rename to excel_pedido;
alter table temporada_guardada alter column nombre_fichero rename to nombre_fichero_pedido;
alter table temporada_guardada drop column tipo_excel;
