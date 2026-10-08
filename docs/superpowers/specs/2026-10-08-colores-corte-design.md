# Colores en los Documentos del Corte y órdenes en Excel — diseño

Fecha: 2026-10-08. Amplía [2026-09-29-documentos-del-corte-design.md](2026-09-29-documentos-del-corte-design.md).

## Qué pidió el usuario

1. **Color del modelo** en los documentos del corte, como pareja código + descripción
   (`LZZ NOIR`, `001 BLACK`).
   - AMI: código de `COLORIS` y descripción de `Libellé coloris`, los dos del pedido.
   - APC: el pedido solo trae el código (`Couleurs`). La descripción vive en una tabla
     nueva que se mantiene a mano, porque la pareja no cambia nunca.
2. **Tabla colores Clientes**: pantalla nueva, enlazada desde `/menu` debajo de
   "Guarda Temporadas y Ficheros", con un desplegable de cliente para ver, añadir,
   corregir y borrar colores. Arranca con los códigos del pedido real de APC.
3. **Color de cada piel**, a mano: un nombre de piel por referencia, pero un color de
   piel por cada color del bolso y por cada piel (principal, forro, combinaciones).
   - El de la piel principal arranca con la descripción del color del bolso.
   - Los del forro y las combinaciones, al rellenarlos por primera vez, se copian a los
     modelos del mismo color.
4. **Las órdenes de corte pasan de Word a Excel**: una hoja por modelo, llamada
   `modelo - color` (sin la descripción si no la hay). El Word de fotos no cambia.

## Decisiones tomadas con el usuario

| Pregunta | Decisión |
|---|---|
| El mismo modelo y color en dos pieles (3 casos en cada pedido real) daría dos hojas con el mismo nombre | Primero, una hoja por modelo + color con una ficha por piel. Después, a petición del usuario, **una hoja por modelo + piel + color**, con la referencia entera en el nombre (`ULL027.AL0103 - 001 BLACK`) |
| ¿Recordar los colores de piel entre temporadas? | **Sí**, por cliente + referencia + código de color, como los nombres de piel |
| Un código de color del pedido que no está en la tabla | **Se añade a la tabla vacío y se avisa** |

## Supuestos (no corregidos por el usuario)

- En AMI manda el pedido; la tabla solo rellena descripciones que el pedido no trae.
- La copia del color "a los modelos del mismo color" es por **código** de color, a la
  misma casilla (forro con forro, combinación N con combinación N) y solo a las
  **vacías**.
- La semilla solo describe `LZZ` (`NOIR`, el ejemplo del usuario). Las otras ocho
  descripciones no se saben y no se inventan.

## Piezas

- `ColorCorte(codigo, descripcion, bolsos)` y `nombre()`; `PedidoCorte` suma por código
  y tiene `codigosSinDescripcion()` y `conDescripciones(mapa)`.
- `persistence/TablaColores` + `ColorCliente` (tabla `color_cliente`, con semilla al
  arrancar si la tabla está vacía). `ColoresController` + `colores.html` en `/colores`.
- `PielCorte(nombre, color)`; `PielesArticulo` lleva una por piel. `FilaCorte` guarda
  los colores de piel por color del bolso, y `pieles(indiceColor)` empareja cada
  combinación con el color de su columna antes de quitar las vacías.
- `MemoriaPieles.buscarColores` / `recordarColores` (tabla `memoria_colores_piel`).
- Tabla de pieles: un `<tbody>` por referencia y una fila por color, con `rowspan` en lo
  que es de la referencia y una columna de color al lado de cada piel.
- `OrdenCorteExcelBuilder` sustituye a `OrdenCorteDocBuilder`.
  - La ficha mantiene la maquetación del Word que el usuario retocó, con una columna
    más para el color de cada piel.
  - Se imprime a escala fija del 90 %, porque Excel imprime las columnas un 4-5 % más
    anchas de lo que las pinta.
  - Lleva un salto de página entre las fichas de una misma hoja.

## Ampliación: colores desde el excel de pedido

Pedido después por el usuario:

- La pantalla de colores pierde el párrafo de ayuda.
- "Cliente" pasa a ser el título de la tarjeta, con un desplegable más grande.
- Se puede subir el excel de pedido de AMI o APC para añadir de una vez sus colores nuevos.
- Después: el título "Cliente" y el desplegable van en la misma línea, y el excel también se
  puede coger de una temporada guardada, con el desplegable y el lápiz del packing list.

Supuestos:

- Entran todos los colores del pedido, también los de los cinturones de AMI.
- Un código que ya estaba no se toca, salvo para rellenar su descripción si estaba vacía y el
  pedido la trae.

## Verificación

- Tests de dominio, persistencia, generador y web.
- Comprobado en Excel real por COM: una página impresa por ficha, también con seis
  pieles y nombres largos. Además, se exportó a PDF para revisarlo a ojo.

## Ampliación: Colores y Pieles de Cliente

Pedido después por el usuario:

- El apartado pasa a llamarse "Colores y Pieles de Cliente" y guarda también las pieles
  principales: la parte de la referencia que es la piel (AL0201 en AMI, PXBHZ en
  PXBHZ-H65077 en APC) y su nombre. Esos nombres rellenan la tabla de `/pieles` del corte.
- Guardar una fila no recarga la página: antes se perdía lo tecleado en las demás. Debajo
  de cada tabla hay un "Guardar todo".
- Desde el excel de pedido (subido o de una temporada) hay tres botones: "Extraer Colores
  y Pieles", "Extraer Colores" y "Extraer Pieles".

Decisión de diseño: la tabla de pieles es la memoria de nombres de piel que ya usaba el
corte (`memoria_piel`), no una tabla nueva, para que haya una sola fuente.
