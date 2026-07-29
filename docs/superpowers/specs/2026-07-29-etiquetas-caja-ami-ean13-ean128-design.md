# EAN13 y EAN128 en las etiquetas de caja de AMI

Fecha: 2026-07-29 (revisado 2026-07-30 tras verificar el fichero real)

## Problema

Las etiquetas de caja de AMI llevan hoy un solo código de barras: el Code 128
del ORDER NUMBER (el PO de 5 dígitos). El cliente pide **dos códigos más** en
las tres destinaciones (France, China, Japan):

- **EAN13**: el número de la columna `EAN13` del excel de pedido, como código
  de barras EAN-13.
- **EAN128**: el número de la columna `EAN128` del mismo excel, como código de
  barras Code 128.

El usuario ya ha actualizado `docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx`
colocando ambas imágenes en su sitio, y `docs/Etiquetas cajas/EAN PUNTOTRES
H26.xlsx` es el excel de pedido real (copiado en
`src/test/resources/ejemplos/`).

## Fuentes de verdad

Las afirmaciones de este spec sobre cómo son los datos de AMI salen **solo** de
`docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx` y de
`docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx`.

Los `.json` de `src/test/resources/ejemplos/` son **fixtures sintéticos** que
mantiene el usuario para pruebas visuales. Son coherentes con el fichero real
(ver la verificación de más abajo) y valen como fixture, pero **no son
autoridad** sobre el comportamiento del cliente y no se citan como evidencia.

## Lo que aporta el fichero del cliente

Comparando el `.xlsx` actualizado con la plantilla actual
(`src/main/resources/client-labels/ami-etiquetas-template.xlsx`), los únicos
cambios son **4 imágenes `.gif` nuevas** y unos bordes `thickBot`: mismos 42
strings compartidos, mismas filas, mismas alturas y mismas celdas combinadas.
Las coordenadas de `AmiEtiquetaLayout` siguen siendo válidas y sigue habiendo
**un solo PNG** en el libro (la dirección de entrega de Japan), así que
`AmiEtiquetasExcelBuilder.extraerPngDireccion` no se rompe.

Las imágenes están puestas **solo en la etiqueta de arriba de cada par**, en la
columna C, a la derecha del texto.

## Estructura del EAN128

El valor de la columna `EAN128` es
`<EAN13> + 00001 + <PO a 8 dígitos> + 16 ceros + <ES|MA>`, p.ej.
`366659835477100001000077030000000000000000MA`. El sufijo es el país de origen
(`ES` = SPAIN, `MA` = MOROCCO, la columna `Made in`).

La estructura se cumple en **143 de las 151 filas** del fichero real. Las 8
restantes son incoherencias del propio fichero del cliente:

- Filas 83 y 84 (`ULL027.AL0103` color 718): los EAN128 están **intercambiados**
  entre destinaciones — la fila `07691 JP` lleva 07705 dentro y la `07705 CH`
  lleva 07691.
- Filas 136-141 (`USL728.AL0217`, 3 colores × 2 POs): `Made in = MOROCCO` pero
  el EAN128 acaba en **`ES`** en vez de `MA`.

## Decisiones

### El EAN128 se lee verbatim

**Decisión**: leer la columna y pasarla tal cual a Code 128, sin componerla.

Esas 8 filas son la razón: si el EAN128 se compusiera en código a partir de
`EAN13` + `PO` + `Made in`, en esas 8 se generaría un código **distinto** al
que tiene el cliente en su fichero, y es el suyo el que espera su sistema al
escanear. Componerlo también duplicaría una regla que el cliente puede cambiar.

### Sí se avisa cuando el EAN128 no cuadra con sus columnas

**Decisión**: al preparar la etiqueta de una caja se comprueba que el EAN128 de
**la fila que se va a imprimir** cumple la estructura, y si no, se acumula un
aviso — pero el código se imprime verbatim igual.

Se valida solo la fila que sale impresa, no las 151: un aviso por cada
anomalía del fichero completo sería ruido sobre artículos que no van en este
envío. Y se avisa en vez de callar porque un EAN128 con el PO de otra
destinación es algo que el usuario puede reclamarle a AMI.

Si el excel no trae la columna `Made in`, se valida solo la parte de EAN13 + PO.

### Cómo se localiza la fila del pedido

**Decisión**: coincidencia **exacta** de cuatro campos —
`ARTICLE` + `COLORIS` + `TAILLE` + sufijo de PO de la destinación. Verificado
sobre las 151 filas: esa clave identifica **una sola fila**, sin un solo
duplicado. Si no hay fila con esa clave → **aviso y etiqueta sin EAN13 ni
EAN128**.

Los cuatro campos son necesarios:

- **`COLORIS`** no es un desempate opcional: hay **59 claves** en las que
  `ARTICLE` + sufijo + `TAILLE` tiene varios colores con EAN13 distintos
  (`UBL029.AL0104` France talla 75 → `0014`, `0015`, `225`).
- **el sufijo de PO** porque el EAN128 lleva el PO dentro, y el mismo producto
  tiene un EAN128 por destinación.
- **`TAILLE`** porque cada talla de cinturón tiene su propio EAN13.

`TAILLE` se resuelve así: `U` si la referencia no es de cinturón, y la talla de
la **línea líder** de la caja física si lo es. Verificado que cuadra al 100%
con el fichero real: las 79 filas `UBL*` tienen talla numérica y las 72
`ULL*`/`USL*` tienen `U`, que es exactamente lo que distingue
`CajaData.esCinturon()`.

Este spec **no toca** cómo se resuelve el `colorCode`, que hoy filtra por
`ARTICLE` + sufijo y usa el `COLORIS` como preferencia con caída a la primera
fila candidata. La regla nueva decide qué EAN se imprime, no qué color.
Imprimir un código de barras equivocado es peor que no imprimirlo; un color
code aproximado, no.

**Empates futuros**: hoy la clave es única, pero si una temporada trajera dos
POs para el mismo artículo+color+talla+destinación habría dos filas. En ese
caso → aviso y sin códigos. No se añade un desempate por el `pedido` del JSON
para un caso que no existe en los datos reales: fallar visiblemente es
suficiente y nunca se cae a la fila de otro PO.

### Qué EAN lleva una caja de cinturones con varias tallas

Una caja de cinturones mezcla tallas y cada talla tiene su propio EAN13
(`UBL029.AL0216` 001: 75→`3666598890040`, 85→`…64`, 95→`…88`, 105→`…101`), pero
en la etiqueta solo cabe un par de códigos.

**Decisión**: el EAN de la talla de la **línea líder** de la caja física — la
misma línea de la que ya salen el peso, la referencia y el color
(`model/CajaFisica`). Así la etiqueta es coherente consigo misma y nunca se
queda sin códigos.

Ojo: la línea líder es la **primera línea del JSON con ese número de caja**, no
la talla más pequeña. Suelen coincidir porque el JSON viene ordenado, pero no
está garantizado: en una caja cuyas líneas llegan 95, 85, 105 el `SIZE` de la
etiqueta se imprime ordenado (`85-95-105`) y el EAN es el de la 95. Es lo
coherente con el peso, que también es el de la 95.

### Las filas ocultas se leen

En el fichero real **147 de las 151 filas están ocultas** (`hidden="1"`), casi
seguro un filtro que quedó guardado. `HojaEan` las lee todas y así se queda:
ocultar es estado de vista, no de datos, y un filtro guardado por error no debe
cambiar las etiquetas. Ignorarlas dejaría 4 filas útiles de 151.

### Geometría: extraer `AnclajeBloque`

`AmiEtiquetaLayout` tiene 17 componentes, 5 de ellas solo para el barcode del
PO. Añadir dos códigos más en plano serían 27.

**Decisión**: extraer un record `AnclajeBloque(int fila, long dx, long dy,
long cx, long cy)` — un anclaje relativo al bloque de la etiqueta — y que el
layout tenga `po`, `ean13`, `ean128` de ese tipo. La constante
`JAPAN_DIRECCION` (la imagen-dirección de Japan) pasa también a `AnclajeBloque`
y sus 5 constantes sueltas desaparecen. El record baja de 17 a 15 componentes e
`insertarImagenes` pasa a ser un bucle sobre pares (anclaje, bytes).

La columna sigue fuera del record: las cuatro imágenes van en la columna C
(`COL_BARCODE = 2`), como hoy.

Se descartaron: (a) 10 campos planos más — record de 27 componentes y tres
bloques de código copiados; (b) una clase `AmiBarcodesLayout` aparte —
indirección sin ganancia, la geometría es parte del layout.

### Anclaje `oneCellAnchor`, no `twoCellAnchor`

El cliente las ha puesto como `twoCellAnchor` (se estiran con las celdas). Se
insertan con `AnclajeImagen.fijo` (`MOVE_DONT_RESIZE`), como el barcode del PO
y como las etiquetas de artículo: al replicar el bloque por caja las imágenes
no deben deformarse.

### Columnas ausentes no bloquean

Si el excel de pedido no trae `EAN13`, `EAN128` o `Made in` (temporada
antigua), se avisa y las etiquetas salen sin esos códigos, en vez de reventar
el flujo. Requiere un `HojaEan.columnaOpcional` hermano del `columna` actual,
que lanza.

## Coordenadas medidas

Fila relativa al bloque de la etiqueta; columna C; EMU (360 000 EMU = 1 cm).
Las tres del PO son las actuales, sin cambio.

| Destinación | Código | fila | dx | dy | cx | cy |
|---|---|---|---|---|---|---|
| CHINA | PO | 8 | 2 971 800 | 19 050 | 1 047 750 | 666 750 |
| CHINA | EAN13 | 10 | 2 613 660 | 162 388 | 1 478 280 | 652 951 |
| CHINA | EAN128 | 12 | 1 394 460 | 420 424 | 2 727 960 | 455 876 |
| JAPAN | PO | 7 | 2 857 500 | 19 050 | 990 600 | 628 650 |
| JAPAN | EAN13 | 9 | 2 430 780 | 68 580 | 1 478 280 | 652 951 |
| JAPAN | EAN128 | 12 | 899 160 | 15 168 | 3 009 900 | 502 991 |
| FRANCE | PO | 7 | 3 457 575 | 9 525 | 1 209 675 | 762 000 |
| FRANCE | EAN13 | 9 | 3 116 580 | 68 580 | 1 569 902 | 693 420 |
| FRANCE | EAN128 | 11 | 2 095 500 | 423 031 | 2 575 560 | 430 408 |

Cada imagen se repite en la segunda etiqueta del par a
`+offsetSegundaEtiqueta` y por caja a `+i*alturaBloque`, igual que el barcode
del PO.

## Verificación contra el fichero real

Comprobado sobre las 151 filas de datos de `EAN PUNTOTRES H26.xlsx`:

| Comprobación | Resultado |
|---|---|
| EAN13 de 13 dígitos con dígito de control correcto | 151/151 |
| `ARTICLE`+`COLORIS`+`TAILLE`+sufijo → una sola fila | 0 duplicados |
| Misma clave con dos EAN13 distintos | ninguno |
| Mismo artículo+color+talla con dos POs de la misma destinación | ninguno |
| `UBL*` con talla numérica y `ULL*`/`USL*` con `U` | 151/151 |
| `COLORIS`, `TAILLE`, `EAN13` y `EAN128` guardados como texto | sí (los ceros de `001`/`0014` se conservan) |
| `PO` guardado como número | 86 filas (France, sin sufijo); las 65 con sufijo son texto |
| EAN128 conforme a la estructura | 143/151 (las 8 anómalas, arriba) |
| Claves `ARTICLE`+sufijo+`TAILLE` con varios `COLORIS` | 59 |

Las 19 tuplas (destino, referencia, color, talla, PO) del fixture
`envio-ami-bags-y-belts.json` existen en el fichero real y el PO cuadra en
todas, así que sirve de fixture sin retocarlo. Una de ellas
(`USL728.AL0217` / 001 / France / 7685, fila 137) es de las 8 anómalas, así que
el aviso de EAN128 tiene caso real en los tests.

## Cambios por fichero

- `client-labels/ami-etiquetas-template.xlsx` — sustituir por el
  `ETIQUETA CAJA AMI.xlsx` actualizado.
- `AnclajeBloque` (nuevo) — record de geometría relativa al bloque.
- `AmiEtiquetaLayout` — `po`, `ean13`, `ean128` y `JAPAN_DIRECCION` como
  `AnclajeBloque`; fuera los 5 campos del barcode del PO y las 5 constantes
  `JAPAN_DIRECCION_*`.
- `HojaEan` — `columnaOpcional(String)` que devuelve -1 si no está.
- `AmiPedidoExcel` — lee `EAN13`, `EAN128` y `Made in`; `FilaPedido` gana
  `ean13` y `ean128`; `buscar` recibe la talla (`U` en bolsos, la de la línea
  líder en cinturones) y aplica la clave exacta para los EAN; valida la
  estructura del EAN128 de la fila elegida; expone `avisos()` para las columnas
  ausentes.
- `AmiEtiquetasExcelBuilder` — `EtiquetaCaja` gana `ean13` y `ean128`;
  `insertarImagenes` dibuja los tres códigos por bucle.
- `AmiEtiquetasGenerador` — pasa la talla de la línea líder a `buscar`,
  traslada `ean13`/`ean128` a `EtiquetaCaja`, acumula los avisos nuevos y los
  de `AmiPedidoExcel`.
- `src/test/resources/ejemplos/README.md` (nuevo) y una línea en `CLAUDE.md` —
  dejar escrito que los `.json` de ahí son fixtures sintéticos y que las
  afirmaciones sobre datos de cliente salen de `docs/`.

## Avisos

Todos siguen la regla del proyecto (la etiqueta se genera igual, sin el código
que falte):

- no hay fila con la clave exacta para esa referencia, color, talla y
  destinación;
- empate de dos filas con la misma clave exacta (no ocurre en los datos
  actuales, pero se avisa en vez de elegir a ciegas);
- `EAN13` presente pero inválido (no son 13 dígitos o falla el dígito de
  control) — lo detecta `CodigoBarrasEan13.esValido`;
- `EAN128` vacío;
- `EAN128` fuera de estructura (PO o país que no cuadran con sus columnas): se
  imprime igual, pero se avisa;
- columna `EAN13`, `EAN128` o `Made in` ausente del fichero.

## Tests

- `AmiPedidoExcelTest`, con el `EAN PUNTOTRES H26.xlsx` real:
  - bolso: `ULL027.AL0103` 001 U CH → `3666598550098` (fila 81);
  - cada talla de un cinturón: `UBL029.AL0216` 001 France 75/85/95/105 →
    `3666598890040` / `…64` / `…88` / `…101` (filas 28-31);
  - mismo producto, EAN13 común y EAN128 distinto por destinación:
    `ULL163.AL0052` 001 U → `3666598313495` en Japan (fila 119) y en France
    (fila 120), con EAN128 de 07688 y de 7663 respectivamente;
  - el color forma parte de la clave: `ULL163.AL0052` 221 U France → fila 123,
    `3666598354771`, distinto del de 001;
  - talla inexistente en esa destinación: `UBL029.AL0104` 0014 talla 105 con
    `07690 JP` no existe → sin EAN y con aviso;
  - EAN128 fuera de estructura: `USL728.AL0217` 001 U France (fila 137,
    `Made in` MOROCCO y sufijo `ES`) → devuelve el código verbatim **y** un
    aviso;
  - columna `EAN13`/`EAN128` ausente → aviso, sin lanzar.
- `AmiEtiquetasExcelBuilderTest`: reabrir el `.xlsx` generado y comprobar **3
  imágenes por etiqueta** (4 en Japan, por la dirección), con la fila y la
  columna de cada anclaje.
- `AmiEtiquetasGeneradorTest`, con `envio-ami-bags-y-belts.json`: la caja 9 de
  FRANCE (`UBL029.AL0216`, tallas 85-95-105) lleva `3666598890064` (talla 85,
  la líder) y no el de 95 ni el de 105.
- `HojaEanTest`: `columnaOpcional` devuelve -1 sin lanzar.
- El e2e sigue dejando los excels en `target/` para revisión visual.
