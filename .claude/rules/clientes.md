---
paths:
  - "src/**/packinglist/config/CatalogoClientes*"
  - "src/**/packinglist/config/ClientesProperties*"
  - "src/**/packinglist/config/ClienteConfig*"
  - "src/**/packinglist/persistence/Cliente*"
  - "src/**/packinglist/persistence/CatalogoClientes*"
  - "src/**/packinglist/web/ClientesController*"
  - "src/main/resources/templates/clientes.html"
  - "src/main/resources/templates/menu.html"
  - "src/main/resources/db/migration/V12__clientes.sql"
  - "src/main/resources/db/migration/V17__cliente_inactivo.sql"
---

# Pantalla de clientes: alta, edición, inactivación y eliminación

> Se cruza con `packing-list.md` (la plantilla GENERIC que usan estos clientes) y con `catalogo.md` (las tablas que guardan datos por cliente).

**Dos orígenes, una interfaz** (`CatalogoClientes`, noviembre de 2026). Los **programados** —AMI y APC— siguen en `application.yml` (`ClientesProperties`): su packing list es código y sus destinos con dirección, abreviaturas e hijas son configuración ligada a ese código. Los de **plantilla genérica** viven en la tabla `cliente` y se gestionan en `/clientes`. En la aplicación arrancada manda `CatalogoClientesJpa` (`@Primary`): programados primero, en el orden del yml, y detrás los genéricos activos por nombre. **Inyectar siempre `CatalogoClientes`**: `ClientesProperties` solo ve el yml y un desplegable hecho con él perdería a todos los genéricos sin fallar. Los tests unitarios y `Main.java` siguen construyendo `ClientesProperties` a mano, que implementa la misma interfaz.

**Los siete genéricos que había en el yml los trajo la migración V12**, con INSERTs, y se borraron del yml. Va en la migración y no en una siembra "si la tabla está vacía" (el patrón de las taras) a propósito: eliminar los siete desde la pantalla los volvería a traer en el siguiente arranque.

**La clave no cambia nunca.** Es el nombre en mayúsculas al dar de alta, y es lo que guardan en su columna `cliente` las temporadas, los modelos, los colores, las pieles, las hojas de embalaje… Editar el nombre cambia lo que se ve en los desplegables, no la clave; cambiarla dejaría huérfano todo lo guardado. Un nombre nuevo no puede coincidir con la clave de otro cliente (tampoco con AMI o APC, ni con un inactivo: el mensaje dice que se reactive). El botón se llamaba «Corregir» y pasó a «Editar» (petición del usuario, noviembre de 2026; `CatalogoClientesJpa.editar`).

**Hacer inactivo** (petición del usuario, noviembre de 2026; columna `cliente.activo` de la `V17`, todos los existentes activos): un inactivo **no sale en ningún desplegable ni filtro del programa** y no se borra nada. Cómo se consigue sin tocar cada pantalla:
- **`getClientes()` devuelve solo los activos** y **`clientePara()` encuentra también a los inactivos** (`CatalogoClientesJpa` sobrescribe el `default` de la interfaz). Todos los desplegables y filtros —también los de la app de modelos, `GET /api/clientes`— se construyen recorriendo `getClientes()`, así que se quedan sin él solos; y lo ya guardado (una sesión del packing, una hoja de embalaje antigua, una temporada) sigue encontrando su cliente y su nombre por `clientePara`. **Regla para código nuevo**: para listar opciones, `getClientes()`; para resolver una clave guardada, `clientePara()`. Usar `getClientes().get(clave)` para resolver hace que lo de un inactivo salga con la clave cruda en vez del nombre (`InfoEmbalajeController.nombreDe` lo hacía y se cambió).
- Lo que **no** se esconde: los registros de un inactivo en listados que no filtran por cliente siguen saliendo (lo pedido fue que el cliente no salga en desplegables ni filtros). Dar de alta algo nuevo para él (temporada o modelo en la app de modelos, que validan contra `getClientes()`) ya no se puede.
- **No pide contraseña** (no borra nada y se deshace con «Reactivar», en la misma fila). En `/clientes` sigue en la lista, atenuado y con la etiqueta «Inactivo» (`.cliente-inactivo`, `.etiqueta-inactivo`); se puede editar y, si no tiene nada guardado, eliminar.
- **Solo genéricos**: AMI y APC no se hacen inactivos (su estado viviría en otra tabla y nadie lo ha pedido). Es la salida para un cliente que ya no trabaja con nosotros y tiene datos guardados, que por eso no se puede eliminar.

**Eliminar** (decisiones del usuario, noviembre de 2026):
- Pide la contraseña **`ELIMINAR`** en un emergente (`<dialog>`). La comprueba **solo el servidor** (`ClientesController.CONTRASENA_ELIMINAR`), exacta y en mayúsculas; la página no la lleva escrita.
- **Solo se puede eliminar un cliente sin nada guardado** (la opción C de las tres que se le dieron: borrar solo el cliente dejando sus datos ocultos, borrarlo todo, o no dejar). `CatalogoClientesJpa.datosGuardados` busca **todas las tablas con una columna `cliente`** en `information_schema`, no una lista fija: una tabla nueva con datos de cliente queda protegida sin tocar nada. La pantalla dice qué tiene en vez de enseñar el botón ("No se puede eliminar: tiene 2 temporadas guardadas y 1 color"). `NOMBRE_DE_LA_TABLA` solo da el texto; una tabla que no esté ahí sale como "filas en <tabla>".
- AMI y APC salen en la lista pero sin botones.

**Campos de un cliente genérico**: nombre (desplegables), nombre legal (C10 del packing) y dirección de entrega (C12, una celda; las líneas van separadas por `;`), obligatorios; temporada de ejemplo (`placeholderTemporada`) y rótulo del nº de pedido (`etiquetaPedido`, sin él "Nº pedido"), opcionales. `pedidoCliente` no se ofrece: ningún genérico tiene lector de excel de pedido.

**El botón del menú** (decisión del usuario): los cinco paneles pequeños van al **final** de la rejilla, abajo a la derecha, y Clientes en una fila aparte debajo de ellos (`.menu-pie`), **del alto de uno de los cinco** —una quinta parte de la celda menos sus huecos, con las variables `--menu-fila` y `--menu-hueco-paneles` de `.menu-principal`—. Es una rejilla aparte porque en `.menu-principal` todas las filas miden lo de una tarjeta grande. `ClientesControllerTest` ancla el orden.
