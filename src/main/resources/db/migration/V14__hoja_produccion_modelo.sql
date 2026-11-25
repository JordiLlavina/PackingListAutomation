-- La Hoja de Producción pasa a ser de UN modelo (decisión del usuario): el
-- modelo sale de las líneas y va a la cabecera, con su foto.
--
-- `modelo`: para listar las hojas por él. Las guardadas antes se quedan sin
-- modelo en el listado; su snapshot sigue entero en `datos`.
--
-- `foto`: la foto SUBIDA en el formulario cuando el modelo no tiene ninguna
-- en el catálogo (JPEG ya reducido). Va con la hoja y no al catálogo, que
-- esta pantalla solo lee; una hoja sin foto propia usa la principal del
-- modelo en el catálogo, como la info de embalaje.
--
-- Columnas nuevas y no un cambio de la V13, que ya puede estar aplicada. SQL
-- del subconjunto común a H2 y PostgreSQL, binarios como bytea.

alter table hoja_produccion add column modelo varchar(80);
alter table hoja_produccion add column foto bytea;
