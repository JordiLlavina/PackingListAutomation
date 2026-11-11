-- El historial del taller ya no cae con el modelo.
--
-- En la V4 las modificaciones y los documentos de un modelo colgaban de él
-- con "on delete cascade", como todo lo del catálogo. Pero los botones de
-- borrar de la pantalla vieja /catalogo ("Borrar modelo", "Quitar los modelos
-- de la temporada") no saben nada de la app del taller, y con la cascada un
-- clic allí se llevaba por delante avisos, órdenes, mensajes, notas de voz y
-- documentos que una modificación nunca debe perder (una modificación no se
-- borra nunca: DESCARTADA lo conserva todo).
--
-- Hay dos redes. La primera está en el código: CatalogoModelos pregunta al
-- puerto HistorialDelTaller y no borra un modelo con historial (solo lo
-- desvincula de la temporada). Esta es la segunda: aunque una ruta vieja o un
-- código futuro que no conozca el taller intente borrar el modelo, la base lo
-- rechaza. Las dos claves pasan a restringir (sin acción, que es "no action"
-- en los dos motores).
--
-- Fusionar dos modelos sigue funcionando porque Modelos.fusionar mueve antes
-- todos los hijos al superviviente y solo después borra el modelo vacío.
--
-- Subconjunto común a H2 y PostgreSQL, como desde la V4.

alter table modificacion drop constraint fk_modificacion_modelo;
alter table modificacion add constraint fk_modificacion_modelo foreign key (modelo_id)
    references modelo (id);

alter table documento_modelo drop constraint fk_documento_modelo;
alter table documento_modelo add constraint fk_documento_modelo foreign key (modelo_id)
    references modelo (id);
