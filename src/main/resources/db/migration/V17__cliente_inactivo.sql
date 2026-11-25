-- Un cliente de plantilla genérica se puede hacer inactivo desde /clientes
-- (decisión del usuario, noviembre de 2026): deja de salir en todos los
-- desplegables y filtros del programa, pero no se borra nada y se puede
-- reactivar. Es la salida para un cliente que ya no trabaja con nosotros y
-- tiene datos guardados, que por eso no se puede eliminar.
--
-- Todos los que ya existen siguen activos. SQL del subconjunto común a H2 y
-- PostgreSQL, como desde la V4.

alter table cliente add column activo boolean default true not null;
