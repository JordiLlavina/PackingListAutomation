-- Una fila por sección concedida. Los permisos son datos de la aplicación y
-- no grupos de Entra a propósito: añadir o quitar una sección a alguien no
-- puede depender de que un informático abra el portal de Azure. Las bajas
-- siguen siendo automáticas, que es lo que sí toca al directorio: cuenta
-- desactivada en Entra = no entra.
create table usuario_permiso (
    usuario_id  bigint      not null,
    seccion     varchar(32) not null,
    primary key (usuario_id, seccion),
    constraint fk_usuario_permiso_usuario
        foreign key (usuario_id) references usuario (id) on delete cascade
);
