# EAN13 y EAN128 en las etiquetas de caja de AMI

Fecha: 2026-07-29

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
H26.xlsx` es el excel de pedido de ejemplo (copiado en
`src/test/resources/ejemplos/`).

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

**Consecuencia de diseño**: el EAN13 es común a las tres destinaciones (es el
código del producto), pero **el EAN128 es distinto por destinación** porque
lleva el PO dentro. `ULL163.AL0052` tiene el mismo `3666598354771` en las tres
y tres EAN128 distintos (PO 07703 CH, 07688 JP, 7663).

El EAN128 **no se compone en código**: se lee verbatim de la columna y se pasa
tal cual a Code 128. Componerlo sería duplicar una regla del cliente que puede
cambiar.

Tampoco se valida el PO que va dentro contra la columna `PO`: en el fichero de
ejemplo hay filas donde no cuadran (`ULL027.AL0103` 718 aparece con `07691 JP`
y EAN128 de 07705, y con `07705 CH` y EAN128 de 07691 — parece que el cliente
los intercambió). Comprobarlo solo generaría avisos que el usuario no puede
resolver.

## Decisiones

### Qué EAN lleva una caja de cinturones con varias tallas

Una caja de cinturones mezcla tallas y cada talla tiene su propio EAN13
(`UBL029.AL0216` 001: 75→`3666598890040`, 85→`…64`, 95→`…88`, 105→`…101`), pero
en la etiqueta solo cabe un par de códigos.

**Decisión**: el EAN de la **línea líder** de la caja física, es decir la
primera talla — la misma línea de la que ya salen el peso, la referencia y el
color (`model/CajaFisica`). Así la etiqueta es coherente consigo misma y nunca
se queda sin códigos. Para los bolsos (talla `U`) no hay ambigüedad.

### Cómo se localiza la fila del pedido

`AmiPedidoExcel.buscar` filtra hoy por `ARTICLE` + sufijo de PO y usa el
`COLORIS` como **preferencia**, no como filtro. Eso es deliberado y no se toca:
`ULL163.AL0052` viene en el JSON con color `001` y en el excel solo existe con
`COLORIS 221`, y la etiqueta imprime hoy "221 …" — el excel es la fuente buena
del color code.

Para los EAN esa laxitud no vale: imprimir el código de barras equivocado es
peor que no imprimirlo. Regla nueva, **solo para EAN13/EAN128**:

1. Filtrar por `ARTICLE` + sufijo de PO de la destinación, y además por
   `TAILLE` si la caja es de cinturones (talla de la línea líder).
2. Si queda **un único candidato**, se usa.
3. Si quedan varios, estrechar por `COLORIS`.
4. Si tras eso hay más de uno, o ninguno → **aviso y etiqueta sin EAN13 ni
   EAN128**.

**Invariante**: nunca se cae a la fila de otro PO. Si la talla de esa
destinación no está en el pedido (`UBL029.AL0104` talla 105 no existe con
`07690 JP`), la caja va sin esos dos códigos y con aviso.

El `colorCode` mantiene exactamente el comportamiento actual: la regla nueva
decide qué EAN se imprime, no qué color se imprime.

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

Si el excel de pedido no trae `EAN13` o `EAN128` (temporada antigua), se avisa
y las etiquetas salen sin esos códigos, en vez de reventar el flujo. Requiere
un `HojaEan.columnaOpcional` hermano del `columna` actual, que lanza.

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

## Cambios por fichero

- `client-labels/ami-etiquetas-template.xlsx` — sustituir por el
  `ETIQUETA CAJA AMI.xlsx` actualizado.
- `AnclajeBloque` (nuevo) — record de geometría relativa al bloque.
- `AmiEtiquetaLayout` — `po`, `ean13`, `ean128` y `JAPAN_DIRECCION` como
  `AnclajeBloque`; fuera los 5 campos del barcode del PO y las 5 constantes
  `JAPAN_DIRECCION_*`.
- `HojaEan` — `columnaOpcional(String)` que devuelve -1 si no está.
- `AmiPedidoExcel` — lee `EAN13` y `EAN128`; `FilaPedido` gana `ean13` y
  `ean128`; `buscar` recibe la talla de la línea líder (`null` en bolsos, y
  entonces no filtra por `TAILLE`); expone `avisos()` (columna ausente).
- `AmiEtiquetasExcelBuilder` — `EtiquetaCaja` gana `ean13` y `ean128`;
  `insertarImagenes` dibuja los tres códigos por bucle.
- `AmiEtiquetasGenerador` — pasa la talla de la línea líder a `buscar`,
  traslada `ean13`/`ean128` a `EtiquetaCaja`, acumula los avisos nuevos y los
  de `AmiPedidoExcel`.

## Avisos

Todos siguen la regla del proyecto (la etiqueta se genera igual, sin el código
que falte):

- referencia no encontrada en el excel de pedido para esa destinación;
- talla del cinturón ausente en el pedido de esa destinación;
- varios candidatos y ninguno resuelve la ambigüedad;
- `EAN13` presente pero inválido (no son 13 dígitos o falla el dígito de
  control) — lo detecta `CodigoBarrasEan13.esValido`;
- `EAN128` vacío;
- columna `EAN13` o `EAN128` ausente del fichero.

## Tests

- `AmiPedidoExcelTest`, con el `EAN PUNTOTRES H26.xlsx` real: bolso
  (`ULL027.AL0103` 001 CH → `3666598550098`), cada talla de un cinturón
  (`UBL029.AL0216` 001 France: 75/85/95/105 → EAN distintos), candidato único
  con color distinto al del JSON (`ULL163.AL0052` 001 vs `COLORIS 221` → sí
  devuelve EAN), talla inexistente en esa destinación (`UBL029.AL0104` 105 en
  `07690 JP` → sin EAN), y columna ausente → aviso.
- `AmiEtiquetasExcelBuilderTest`: reabrir el `.xlsx` generado y comprobar **3
  imágenes por etiqueta** (4 en Japan, por la dirección), con la fila y la
  columna de cada anclaje.
- `AmiEtiquetasGeneradorTest`: con `envio-ami-bags-y-belts.json`, la caja 9 de
  FRANCE (`UBL029.AL0216`, tallas 85-95-105) lleva `3666598890064` (talla 85,
  la líder) y no el de 95 ni el de 105; y el EAN128 de cada destinación lleva
  su propio PO — `ULL027.AL0103` en China con `…00007705…`, y
  `ULL163.AL0052` con `…00007688…` en Japan y `…00007663…` en France (misma
  referencia, mismo EAN13 `3666598354771`, EAN128 distinto).
- `HojaEanTest`: `columnaOpcional` devuelve -1 sin lanzar.
- El e2e sigue dejando los excels en `target/` para revisión visual.
