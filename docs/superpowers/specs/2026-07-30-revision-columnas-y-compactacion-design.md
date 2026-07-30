# Revisión: reorden de columnas y compactación de cajas por rango

Fecha: 2026-07-30

## Problema

La tabla de la pantalla de revisión (`/revision`) pinta **una fila por línea de
caja**. En un envío normal eso son cientos de filas casi idénticas: tramos
enteros de cajas consecutivas con la misma referencia, color, product order y
cantidad. Revisarlas a ojo es incómodo y el orden de columnas actual no sigue el
orden con el que el usuario lee un packing list.

## Objetivo

1. Reordenar las columnas a:
   `REFERENCIA · COLOR · PEDIDO · CAJA · TAMAÑO · CANTIDAD · PALET · PESO BRUTO · PESO NETO`
   (además del reorden, **bruto y neto quedan intercambiados** respecto al orden
   actual, que era neto → bruto).
2. Compactar en una sola fila las cajas consecutivas equivalentes, poniendo en la
   columna `CAJA` el rango `primera + "-" + última` (ej. `4-8`).

Es un cambio **solo de vista**: el dominio sigue teniendo una `CajaData` por caja
física y los builders de Excel no se tocan.

## Criterio de agrupación

Se agrupan cajas que cumplan **todas** estas condiciones:

- Son **cajas de una sola línea**. Una caja mixta (varias líneas con el mismo
  `numeroCaja` en la destinación) nunca se agrupa: se sigue pintando con sus
  líneas, y solo la líder lleva pesos, como hasta ahora.
- Son **consecutivas en los dos sentidos**: adyacentes en la lista de cajas de la
  destinación *y* con `numeroCaja` correlativo (4,5,6,7,8). Sin la segunda
  condición un rango `4-8` podría estar tapando que las cajas 6 y 7 fueron a otro
  sitio: el rango prometería cinco cajas y solo representaría tres.
- Coinciden en la clave pedida por el usuario: `referencia`, `codigoColor`,
  `numeroPedido`, `cantidad`.
- Y coinciden además en todo lo que la fila muestra o implica: `tamanoCaja`,
  `numeroPalet`, `pesoNetoKg`, `pesoBrutoKg` y `talla`.

`talla` no es una columna visible, pero entra en la clave: en cinturones AMI dos
cajas pueden compartir referencia/color/PO/cantidad y llevar tallas distintas, y
esconderlas bajo un `4-8` haría invisible una diferencia que sí importa aguas
abajo (las etiquetas usan la talla de la línea líder de la caja).

La regla de fondo: **una fila compactada nunca muestra un valor que no sea cierto
para todas sus cajas**. Si la caja 6 cambia de palet, el resultado es `4-5 / 6 /
7-8`, no un `4-8` con el palet de la primera.

`CAJA` muestra `4-8` para un grupo y `4` a secas para una caja suelta (nunca
`4-4`). `CANTIDAD` sigue siendo la de **una** caja, no la suma del grupo: es el
dato con el que se compara contra el albarán.

## Diseño

### `AgrupadorFilasRevision` (nuevo, `web/`)

Clase sin estado que recibe la `List<CajaData>` de una destinación y devuelve las
filas ya agrupadas. Vive fuera de `PackingListController` porque el controller ya
es grande y este es el único algoritmo no trivial de la clase; separado se prueba
con JUnit puro (`new`), sin levantar contexto Spring.

La definición de "caja física" se mantiene igual que en
`montarVistaDestinos()`: todas las líneas con el mismo `numeroCaja` dentro de la
destinación (`peso-caja-fisica-unificado`). No se cambia esa semántica.

### `FilaCaja`

`indiceEnDestino` (int) pasa a `indicesEnDestino` (`List<Integer>`): las
posiciones de **todas** las cajas del grupo. Gana `rangoCajas` (String).

### `DestinoVista`

Gana `totalCajas`, porque el `<h2>` mostraba `${destino.filas.size()} cajas` y
tras la compactación el número de filas ya no es el número de cajas.

### `RevisionForm.PesoEditado`

`indiceCaja` (int) pasa a `indicesCaja` (`List<Integer>`), y la plantilla emite un
`<input type="hidden">` por cada índice del grupo. Al teclear un peso en la fila
`4-8` se aplica a las cinco cajas.

Es imprescindible aunque las cinco compartieran ya el mismo peso al agruparse: el
peso **editado** tiene que llegar a todas, o el grupo se rompería en el siguiente
render y el usuario vería la caja 4 con su peso nuevo y las 5-8 con el viejo.

`aplicarPesosYReinferir()` recorre la lista de índices en lugar de uno solo.

### Sin cambios

El botón "recalcula peso" sigue propagando el peso a todas las cajas del mismo
modelo; ahora hay uno por fila compactada. El resaltado `pendiente`, los avisos y
el flujo POST-redirect-GET con restauración de scroll se mantienen.

## Tests

- `AgrupadorFilasRevisionTest` (JUnit puro):
  - cinco cajas iguales y correlativas → una fila con `rangoCajas = "4-8"`;
  - corte por palet distinto → `4-5 / 6 / 7-8`;
  - corte por peso distinto;
  - `numeroCaja` no correlativo → no se agrupa;
  - caja mixta → sus líneas se pintan sueltas, sin agrupar;
  - caja suelta → `rangoCajas = "4"`, no `"4-4"`.
- `PackingListControllerTest`: un POST a `/recalcular` con un peso en una fila
  compactada deja las cinco `CajaData` de la sesión con ese peso.
