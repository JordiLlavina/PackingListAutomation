-- Los clientes de plantilla genérica pasan del yml a la base de datos, para
-- darlos de alta, corregirlos y eliminarlos desde /clientes sin tocar
-- ficheros ni reiniciar. AMI y APC se quedan en el yml: su packing list es
-- código (CatalogoClientes).
--
-- `clave` es lo que las demás tablas guardan en su columna `cliente`; no
-- cambia aunque se corrija el nombre.
create table cliente (
    clave varchar(60) not null,
    nombre varchar(120) not null,
    nombre_legal varchar(200) not null,
    direccion_entrega varchar(500) not null,
    placeholder_temporada varchar(20),
    etiqueta_pedido varchar(60),
    fecha_actualizacion timestamp not null,
    primary key (clave)
);

-- Los siete que había en application.yml, tal cual. Van en la migración y no
-- en una siembra al arrancar "si la tabla está vacía": eliminar los siete
-- desde la pantalla los volvería a traer en el siguiente arranque.
insert into cliente (clave, nombre, nombre_legal, direccion_entrega, fecha_actualizacion) values
    ('ACKERMANN', 'Ackermann', 'ACKERMANN HOHMANN UND SEDLACEK OHG',
     'Goseburgstraße 27, 21339 Lüneburg, Germany', current_timestamp),
    ('DROLE DU MONSIEUR', 'Drole du Monsieur', 'MSDS',
     'ENTREPOT LOGISTIQUE NC; 17 Rue du noyer à la malice; 95380 LOUVRES, France', current_timestamp),
    ('KAMI', 'Kami', 'KAMI SAS',
     'Ploiesti West Park DN72 PLOIESTI Targoviste-Road; 107025 Aricestii Rahtivani communne; ROMANIA', current_timestamp),
    ('LGN', 'LGN', 'LGN C/O - STELLAE INTERNATIONAL FRANCE',
     '47 BIS RUE ERNEST RENAN; 94200 IVRY SUR SEINE', current_timestamp),
    ('PALOMA WOOL', 'Paloma Wool', 'PURO PASEO, S.L.',
     'GRUPO SEVICA - C/ DEL PROGRES, 4-6, POL.IND. LA POST- NAVE 3, 08850 GAVÀ (BARCELONA)', current_timestamp),
    ('PAUL & JOE', 'Paul & Joe', 'MANEKI SAS',
     'PAUL&JOE; 14 RUE COMMINES, 75003 PARIS', current_timestamp),
    ('SONIA RYKIEL', 'Sonia Rykiel', 'SONIA RYKIEL INTERNATIONAL',
     'BLECKMANN - TRB INTERNATIONAL; INDUSTRIEWEG 38C; QUAI B12; 2280 GROBBENDONK, BELGIUM', current_timestamp);
