# Etiqueta de caja de AMI: imagen compuesta y hoja de códigos extra — diseño

Fecha: 2026-08-04

## Qué resuelve

La etiqueta de caja de AMI cambia de maqueta. El cliente ha entregado una
plantilla nueva (`docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx`, versión del
2026-08-04) en la que:

- El Code 128 del Product Order **desaparece**.
- El EAN-13 del artículo deja de ser una imagen de solo el código de barras y
  pasa a ser una **imagen compuesta**: los mismos cuatro textos de la etiqueta
  de artículo (referencia, talla, color, pedido) con el EAN-13 debajo, todo en
  un único PNG flotante.
- El Code 128 largo de la columna `EAN128` del excel de pedido **se reubica** en
  el hueco que deja el Code 128 del PO.
- `ORDER NUMBER` y `REFERENCE` pasan a **celda combinada** (dos celdas en una).
- `COLOR CODE` pasa a mostrar **solo el código numérico** (`221`), no el nombre.

Y la hoja `CODIGOS BARRAS EXTRA`, que hoy es una tabla de filas, pasa a ser una
**rejilla de etiquetas imprimibles**, la misma que ya usan las etiquetas de
artículo: 10 artículos por A4, cada uno con su información de caja, su EAN-13 y
su EAN128.

Alcance: **solo AMI**. APC no imprime códigos de barras, así que nada de esto le
aplica; el resto de clientes sigue con su mensaje de "en desarrollo". Los
**cinturones** conservan su `SIZE`/`QUANTITY` multi-talla tal cual.

## Hechos verificados sobre la plantilla nueva

Medidos descomprimiendo el `.xlsx`, no asumidos.

**Las tres hojas siguen siendo `AMI CHINA`, `AMI JAPAN` y `AMI FRANCE`**, con el
mismo par de etiquetas por hoja y la misma altura de bloque (34/17 en CHINA,
32/16 en JAPAN y FRANCE). Las dos etiquetas del par están **combinadas de forma
simétrica** en las tres hojas (`C9:C10`+`C11:C12` y sus copias en CHINA;
`C8:C9`+`C10:C11`+`C24:C25`+`C26:C27` en JAPAN y FRANCE), así que
`BloqueEtiquetaModelo` puede seguir replicando el par entero.

**Las filas de valor** (0-based, relativas a la base del bloque):

| Campo | CHINA | JAPAN | FRANCE | antes |
|---|---|---|---|---|
| `orderNumber` | 8 | 7 | 7 | 9 / 8 / 8 |
| `referencia` | 10 | 9 | 9 | 11 / 10 / 10 |
| `temporada` (col B) | 11 | 10 | 10 | igual |
| `colorCode` | 12 | 11 | 11 | igual |
| `talla` | 13 | 12 | 12 | igual |
| `cantidad` | 14 | 13 | 13 | igual |
| `peso` | 15 | 14 | 14 | igual |
| `parcel` | 16 | 15 | 15 | igual |

Solo se mueven las dos que se han combinado, y suben una fila porque el valor
de una celda combinada vive en su esquina superior izquierda.

**Los dos anclajes de imagen** (`AnclajeBloque(fila, dx, dy, cx, cy)`, columna C):

| | CHINA | JAPAN | FRANCE |
|---|---|---|---|
| imagen compuesta | `(10, 2481943, 54429, 1674091, 762000)` | `(9, 2241177, 26896, 1674091, 762000)` | `(9, 2937163, 69273, 1674091, 762000)` |
| EAN128 | `(8, 1352897, 143333, 2727960, 452413)` | `(7, 918884, 62682, 3009900, 502991)` | `(7, 2057400, 156331, 2575560, 430408)` |

El hueco de la imagen compuesta mide **1.674.091 × 762.000 EMU (4,65 × 2,12 cm)
en las tres destinaciones**, y el mock que trae la plantilla (`image2.png`,
290 × 132 px) confirma la proporción: **2,197:1**. La imagen-dirección de JAPAN
no se mueve: `(2, 66675, 95250, 2562225, 1143000)`.

**Los estilos de las celdas de valor no traen `wrapText`** y su alineación es
`horizontal=left, vertical=center`. El "texto centrado" del encargo es el
centrado vertical que ya trae la plantilla: el builder no toca la alineación.

## 1. Qué se comparte de verdad entre las tres piezas

El encargo pide que la imagen compuesta la usen tanto la etiqueta de caja como
la hoja extra, pero la hoja extra pide explícitamente **celdas**, no imagen. Lo
que comparten no es el dibujo: es el **contenido**.

Ese contenido ya existe y ya está formateado: el record
`EtiquetaArticulo(referencia, talla, color, pedido, ean13)`, con sus valores tal
como van a la etiqueta (`ULL163.AL0052`, `Size: U`, `221 DARK COFFEE`,
`Cde: 07703`). Verificado contra `AmiEtiquetasArticuloGenerador.etiquetaDe()`:
son exactamente esos cuatro y no los que decía el encargo — **el código y el
nombre de color van juntos en un mismo campo**, y el cuarto es el **PO**, no el
código de color. La plantilla de la hoja extra lo confirma literalmente en sus
shared strings.

`EtiquetaArticulo` vive hoy en `service/etiquetasarticulo/`, que depende de
`service/etiquetas/`. **Se baja a `service/etiquetas/`** para que las tres
piezas lo usen sin invertir la dependencia. Sobre él, dos renderizadores:

- **`ImagenEtiquetaArticulo`** — dibuja el PNG compuesto. Lo usa la etiqueta de
  caja.
- **`BloqueEtiquetaArticulo`** — escribe las 4 celdas y ancla su imagen de
  código de barras en `(hoja, filaBase, columnaIzquierda)`. Sale de extraer el
  `rellenar()` privado de `EtiquetasArticuloExcelBuilder`, que pasa a llamarlo;
  las etiquetas de artículo **no cambian de comportamiento**. Lo usa también la
  hoja extra.

Los generadores de cliente siguen decidiendo *qué* poner; la capa común decide
*cómo* se dibuja.

## 2. La imagen compuesta y la nitidez del código de barras

`ImagenEtiquetaArticulo.png(etiqueta, anchoPx, proporcion)` devuelve un PNG con
los cuatro textos arriba (referencia y color a la izquierda, talla y pedido a la
derecha, como en la etiqueta de artículo) y el EAN-13 centrado debajo.

`anchoPx` no es una constante suelta: se deriva del **ancho nativo del código de
barras** más su margen, para que el código quepa entero sin reescalar. Los
textos se dibujan con un cuerpo proporcional al lienzo, de modo que la imagen
final tenga las proporciones del mock del cliente sea cual sea la resolución de
partida. `proporcion` es la del hueco de la plantilla (2,197).

**El punto delicado es no perder la legibilidad del código de barras.**
`CodigoBarrasEan13.png()` renderiza hoy a 300 dpi: unos 440 × 213 px. Con la
proporción 2,197 que pide el hueco, un lienzo de ~460 px de ancho mide ~209 px
de alto, y el código de barras nativo ya son 213. **No cabe sin escalarlo**, que
es justo lo que hay que evitar: reescalar un código de barras con interpolación
lo deja bonito en pantalla e ilegible para un lector físico.

La salida no es escalar la imagen sino **generarla ya con la altura de barra que
toca**. Se añade a `CodigoBarrasEan13` un overload con altura de barras, igual
que el que ya tiene `CodigoBarrasCode128.png(texto, proporcion)`: barcode4j
dibuja las barras con esa geometría y `Graphics2D.drawImage` la pega **1:1, sin
`RenderingHints` de interpolación**. Cero resampleo.

Consecuencia asumida y que hay que verificar en papel: con la maqueta del
cliente las barras quedan en **~9,5 mm, un 42% de la altura nominal del EAN-13**
(22,85 mm). Un EAN-13 truncado lo lee bien la mayoría de lectores, pero es
exactamente el riesgo que señala el encargo. **La imagen compuesta no se da por
cerrada hasta imprimir una etiqueta real y pasarle el lector.** Si falla, la
palanca es crecer el hueco hacia abajo; con la geometría parametrizada en
`AmiEtiquetaLayout` es cambiar dos constantes.

La imagen se cachea por contenido en el mismo mapa que ya usa
`AmiEtiquetasExcelBuilder`: un envío repite mucho el mismo artículo y sin caché
el `.xlsx` guardaría el mismo PNG una vez por etiqueta.

## 3. Etiqueta de caja de AMI

- **`AmiEtiquetaLayout`** pierde el anclaje `po`. `ean13` pasa a llamarse
  `imagenArticulo` y toma las coordenadas de la tabla de arriba; `ean128` toma
  las suyas. `filaOrderNumber` y `filaReferencia` bajan de valor según la tabla.
  `AmiEtiquetaLayoutTest` ancla los valores nuevos contra la plantilla.
- **`AmiEtiquetasExcelBuilder`** deja de generar el Code 128 del PO y, en lugar
  del EAN-13 pelado, ancla la imagen compuesta.
- **`COLOR CODE` pasa a numérico**. Hoy `AmiPedidoExcel.FilaPedido.colorCode()`
  devuelve `coloris + " " + libellé`. Se parte en dos: `colorCode` (solo el
  código, que es lo que va a la celda de la etiqueta) y `colorCompleto`
  (`221 DARK COFFEE`, que es lo que va a la imagen compuesta y a la hoja extra).
  Cuando la referencia no aparece en el excel de pedido, el color sigue saliendo
  del JSON, que ya es el código numérico, y `colorCompleto` cae al mismo valor.
- **`EtiquetaCaja` gana un campo `EtiquetaArticulo`**: el del **primer
  artículo** de la caja, el mismo cuyo EAN-13 y cuyo EAN128 lleva la etiqueta.
  Con una caja de tres bolsos, la celda `REFERENCE` seguirá diciendo
  `A / B / C` —la concatenación de la feature anterior no se toca— y la imagen
  hablará solo de `A`. Los otros dos van completos a la hoja extra, igual que
  hoy pasa con sus códigos de barras.
- **El `Cde:` sale del excel de pedido** (`FilaPedido.orderNumber()`), como en la
  etiqueta de artículo, no del JSON. La celda `ORDER NUMBER` sigue saliendo del
  JSON, que es la regla vigente del proyecto; si los dos discrepan, el aviso que
  ya existe lo canta y la etiqueta mostrará el del JSON arriba y el del excel
  dentro de la imagen.
- **La talla de la imagen** es la del primer artículo: `U` en bolsos, la de la
  línea líder en cinturones. Es la que corresponde al EAN-13 que lleva al lado.

### El merge y el ajuste de fuente

`REFERENCE` pasa a ser una celda combinada en vertical, y eso tiene un efecto
lateral en `AjusteFuente`: **Excel ignora `shrinkToFit` en celdas combinadas**,
igual que lo ignora cuando hay `wrapText` —lo que ya se descubrió en las
plantillas de APC—. El tamaño calculado sigue siendo correcto porque el ancho
sigue siendo el de la columna C y el merge es vertical, no horizontal.

Se aplica la misma regla que se aplicó en APC: **no marcar el atributo cuando va
a ser inerte**. `AjusteFuente` deja de poner `shrinkToFit` si la celda está
dentro de una región combinada, y CLAUDE.md dice el cuadro real por cliente en
vez de prometer una red de seguridad que no existe. En AMI, a partir de ahora,
lo único que protege el texto de `REFERENCE` es el tamaño calculado.

No se añade `wrapText` a la celda combinada: el encargo pide que el merge no
rompa el ajuste dinámico de fuente, y envolver el texto en dos líneas sería otro
comportamiento.

## 4. Hoja `CODIGOS BARRAS EXTRA`

Se conserva el nombre en plural, el que ya genera el código hoy, aunque la
plantilla de referencia lo traiga en singular. Sigue viviendo **en el libro de su
destinación**, como segunda hoja, y sigue creándose **solo si hay al menos una
fila**: si ninguna caja tiene artículos sobrantes, el fichero es el de hoy.

Deja de ser una tabla y pasa a ser **la misma rejilla que las etiquetas de
artículo**: bloques de 8 filas, el primero en la fila 1 (0-based), separadora de
9,95 pt entre bloques, A4 vertical al 74%, márgenes de 1 mm. Esas constantes
**se reutilizan de `EtiquetasArticuloExcelBuilder`** en vez de declarar un
segundo juego: es la misma rejilla y debe tener una sola fuente de verdad. Las
medidas de la plantilla de referencia caen dentro del 3% de las que ya hay.

**Un artículo por bloque, 10 por página**, con salto de página cada 10. Esto
sustituye el apaisado con `fitToPage` de hoy.

Cada bloque usa 3 de las 4 columnas de la rejilla, como la plantilla de
referencia (`docs/Etiquetas cajas/AMI ETIQUETAS CAJA - CODE BARRAS EXTRA
TEMPLATE.xlsx`):

| A / B | D / E | G / H | J / K |
|---|---|---|---|
| `CAJA` + `1 / 15`<br>`Destinación` + `CHINA` | los 4 textos + **EAN-13** | los mismos 4 textos + **EAN128** | vacía |

- El bloque de caja va en dos pares de celdas combinadas en vertical:
  `A(base):A(base+1)` = `CAJA` y `B(base):B(base+1)` = el parcel, en Calibri
  negrita 18 y 20 pt centrados; `A(base+2):A(base+3)` = `Destinación` y
  `B(base+2):B(base+3)` = el nombre de la destinación, en negrita 14 pt
  centrados. La destinación es la del JSON (`DestinoData.getNombreDestino()`,
  p. ej. `PARIS`), la misma que ya va en el nombre del fichero, no el nombre de
  la hoja de la plantilla (`AMI FRANCE`).
- El parcel es **el mismo string que la etiqueta de esa caja** (`1 / 15`, sin
  cero delante), para que hoja y caja se casen a ojo. La plantilla de referencia
  lo escribe con cero (`01 / 15`); manda la coincidencia con la etiqueta.
- Los dos bloques de artículo llevan **los mismos cuatro textos**; lo único que
  cambia es el código de barras de debajo.
- El EAN-13 reutiliza el anclaje de `BloqueEtiquetaArticulo`. El EAN128 se ancla
  en la columna G con `(dx 60959, dy 129540, cx 2118833, cy 352239)` medidos de
  la plantilla de referencia, y se genera con esa proporción para que no se
  estire, como ya se hace en la etiqueta.
- Si el artículo no aparece en el excel de pedido, el bloque se escribe igual,
  sin imágenes, con el aviso que ya existe. **Nunca** se rellena con la fila de
  otro PO: regla vigente del proyecto.
- El `EAN128` se pasa a Code 128 **verbatim**, como en la etiqueta.

Sigue habiendo un bloque por artículo **2..N** de cada caja, en orden de caja y
de artículo, y sigue emitiéndose el aviso de que ese excel trae una hoja más que
imprimir.

`FilaCodigoBarrasExtra` cambia de forma: pasa de siete strings sueltos a llevar
el parcel, la destinación, la `EtiquetaArticulo` y los dos códigos.

## Errores y avisos

Se mantiene la regla del proyecto: **nunca fallar en silencio, nunca bloquear por
datos que un humano puede resolver**. Un artículo sin EAN-13 válido produce su
bloque sin imagen y su aviso; una imagen compuesta sin código de barras se
dibuja igual con sus cuatro textos y el hueco vacío. Ningún camino nuevo lanza
excepciones.

## Verificación

- Tests puros (JUnit 5, `new`) de `ImagenEtiquetaArticulo`: que el PNG sale con
  el tamaño pedido, que el código de barras se pega a resolución nativa (mismo
  número de píxeles que el PNG de origen) y que un EAN-13 inválido no rompe la
  imagen.
- `CodigoBarrasEan13`: el overload de altura de barras devuelve la geometría
  pedida y el código sigue siendo válido.
- `AmiEtiquetaLayoutTest`: las coordenadas nuevas contra la plantilla real.
- `AmiEtiquetasExcelBuilderTest`: reabrir el `.xlsx` y comprobar que ya **no**
  hay código de barras del PO, que hay una imagen compuesta por etiqueta, que
  `COLOR CODE` trae solo el código numérico y que los valores caen en las filas
  nuevas.
- `AmiPedidoRealTest`: `colorCode` y `colorCompleto` contra el fichero real del
  cliente.
- Test de maquetación de la hoja extra contra la plantilla de referencia, al
  estilo de `EtiquetasArticuloMaquetacionTest`: anchos, altos, `pageSetup`,
  merges y anclajes.
- No-regresión: la suite de etiquetas de artículo debe pasar **sin tocar** tras
  extraer `BloqueEtiquetaArticulo`.
- **Comprobación manual imprescindible**: dejar en `target/` un excel con una
  caja de varios artículos, imprimirlo y **pasar un lector físico** por el
  EAN-13 de la imagen compuesta y por los dos códigos de la hoja extra. Es la
  única parte que ningún test puede afirmar.
