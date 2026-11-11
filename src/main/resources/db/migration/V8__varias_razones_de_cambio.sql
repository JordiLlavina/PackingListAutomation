-- Una modificación puede llevar varias razones de cambio, y la lista cambia.
--
-- Las razones (modelos.tipos-cambio del yml) pasan a ser ocho, en el orden
-- que pidió el usuario: Medidas / Patrones, Construcción, Mano de obra,
-- Consumo piel, Refuerzos, Fornituras, Cambio de troqueles, Otros. Y una
-- modificación puede llevar varias: se guardan en la misma columna separadas
-- por comas (CONSUMO_PIEL,FORNITURAS), así que la columna se ensancha.
--
-- Lo guardado con las claves viejas pasa a la clave nueva que mejor le
-- corresponde; FORRO no tiene equivalente y pasa a OTROS. Nada se pierde.
--
-- Subconjunto común a H2 y PostgreSQL, como desde la V4.

alter table modificacion alter column tipo_cambio set data type varchar(200);

update modificacion set tipo_cambio = 'CONSUMO_PIEL' where tipo_cambio = 'PIEL';
update modificacion set tipo_cambio = 'FORNITURAS' where tipo_cambio = 'HERRAJES';
update modificacion set tipo_cambio = 'CONSTRUCCION' where tipo_cambio = 'COSTURA';
update modificacion set tipo_cambio = 'MEDIDAS_PATRONES' where tipo_cambio in ('MEDIDAS', 'PATRON');
update modificacion set tipo_cambio = 'OTROS' where tipo_cambio in ('OTRO', 'FORRO');
