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
---

# Pantalla de clientes: alta, corrección y eliminación

> Se cruza con `packing-list.md` (la plantilla GENERIC que usan estos clientes) y con `catalogo.md` (las tablas que guardan datos por cliente).

**Dos orígenes, una interfaz** (`CatalogoClientes`, noviembre de 2026). Los **programados** —AMI y APC— siguen en `application.yml` (`ClientesProperties`): su packing list es código y sus destinos con dirección, abreviaturas e hijas son configuración ligada a ese código. Los de **plantilla genérica** viven en la tabla `cliente` y se gestionan en `/clientes`. En la aplicación arrancada manda `CatalogoClientesJpa` (`@Primary`): programados primero, en el orden del yml, y detrás los genéricos por nombre. **Inyectar siempre `CatalogoClientes`**: `ClientesProperties` solo ve el yml y un desplegable hecho con él perdería a todos los genéricos sin fallar. Los tests unitarios y `Main.java` siguen construyendo `ClientesProperties` a mano, que implementa la misma interfaz.

**Los siete genéricos que había en el yml los trajo la migración V12**, con INSERTs, y se borraron del yml. Va en la migración y no en una siembra "si la tabla está vacía" (el patrón de las taras) a propósito: eliminar los siete desde la pantalla los volvería a traer en el siguiente arranque.

**La clave no cambia nunca.** Es el nombre en mayúsculas al dar de alta, y es lo que guardan en su columna `cliente` las temporadas, los modelos, los colores, las pieles, las hojas de embalaje… Corregir el nombre cambia lo que se ve en los desplegables, no la clave; cambiarla dejaría huérfano todo lo guardado. Un nombre nuevo no puede coincidir con la clave de otro cliente (tampoco con AMI o APC).

**Eliminar** (decisiones del usuario, noviembre de 2026):
- Pide la contraseña **`ELIMINAR`** en un emergente (`<dialog>`). La comprueba **solo el servidor** (`ClientesController.CONTRASENA_ELIMINAR`), exacta y en mayúsculas; la página no la lleva escrita.
- **Solo se puede eliminar un cliente sin nada guardado** (la opción C de las tres que se le dieron: borrar solo el cliente dejando sus datos ocultos, borrarlo todo, o no dejar). `CatalogoClientesJpa.datosGuardados` busca **todas las tablas con una columna `cliente`** en `information_schema`, no una lista fija: una tabla nueva con datos de cliente queda protegida sin tocar nada. La pantalla dice qué tiene en vez de enseñar el botón ("No se puede eliminar: tiene 2 temporadas guardadas y 1 color"). `NOMBRE_DE_LA_TABLA` solo da el texto; una tabla que no esté ahí sale como "filas en <tabla>".
- AMI y APC salen en la lista pero sin botones.

**Campos de un cliente genérico**: nombre (desplegables), nombre legal (C10 del packing) y dirección de entrega (C12, una celda; las líneas van separadas por `;`), obligatorios; temporada de ejemplo (`placeholderTemporada`) y rótulo del nº de pedido (`etiquetaPedido`, sin él "Nº pedido"), opcionales. `pedidoCliente` no se ofrece: ningún genérico tiene lector de excel de pedido.

**El botón del menú** (decisión del usuario): los cinco paneles pequeños van al **final** de la rejilla, abajo a la derecha, y Clientes en una fila aparte debajo de ellos (`.menu-pie`), **del alto de uno de los cinco** —una quinta parte de la celda menos sus huecos, con las variables `--menu-fila` y `--menu-hueco-paneles` de `.menu-principal`—. Es una rejilla aparte porque en `.menu-principal` todas las filas miden lo de una tarjeta grande. `ClientesControllerTest` ancla el orden.
