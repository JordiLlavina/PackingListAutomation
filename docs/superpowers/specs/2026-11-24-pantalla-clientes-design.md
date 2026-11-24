# Pantalla de clientes — diseño

**Fecha:** 2026-11-24 · **Tamaño:** cambio pequeño

## Qué y por qué

Poder dar de alta clientes nuevos desde la aplicación. Hasta ahora había que editar el bloque `packing-list.clientes` de `application.yml` y reiniciar. También se pueden corregir y eliminar.

## Lo que decidió el usuario

- Un **botón pequeño en el menú**. Los cinco paneles pequeños pasan al final de la rejilla, abajo a la derecha. El de Clientes va justo debajo de ellos y ocupa una quinta parte de la casilla de abajo.
- Los clientes **pasan a la base de datos**.
- **Eliminar** pide en un emergente la contraseña `ELIMINAR`, en mayúsculas.
- **Solo se puede eliminar un cliente que no tenga nada guardado** (opción C). Se descartaron dos opciones:
  - A: borrar solo el cliente y dejar sus datos ocultos.
  - B: borrarlo todo.

## Enfoques considerados

1. **Todo a la base de datos, también AMI y APC.** Descartado. Sus destinos con dirección, sus abreviaturas y sus destinaciones hijas son configuración ligada al código de sus packing lists. Llevarlos a tablas es mucho trabajo y no lo pide nadie.
2. **Genéricos a la base de datos y AMI/APC en el yml, con una interfaz `CatalogoClientes` que junta los dos** (elegido). Es el mismo patrón que `CatalogoTaras`. AMI y APC salen en la lista bloqueados.
3. **Siembra desde el yml al arrancar si la tabla está vacía**, como las taras. Descartado. Si se eliminan todos los genéricos, el siguiente arranque los volvería a traer. Los siete clientes que había se insertan en la migración V12.

## Decisiones del asistente

- **Campos del formulario.**
  - Obligatorios: nombre, nombre legal y dirección de entrega.
  - Opcionales: temporada de ejemplo y cómo llama el cliente a su número de pedido.
  - No se ofrece "excel de pedido", porque ningún cliente genérico tiene lector de ese excel.
- **La clave sale del nombre en mayúsculas y no cambia al corregir.** Es lo que guardan las demás tablas.
- **Se puede corregir un cliente.** Esta opción se propuso al usuario y no la objetó.
- **Para saber si un cliente tiene datos guardados** se buscan todas las tablas con columna `cliente` en `information_schema`.
- **La contraseña solo la comprueba el servidor.** Así no queda escrita en la página.

Detalle para quien toque el código: `.claude/rules/clientes.md`.
