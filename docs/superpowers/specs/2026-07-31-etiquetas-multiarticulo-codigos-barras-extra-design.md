# Etiquetas de caja con varios artículos + hoja "CODIGOS BARRAS EXTRA" — diseño

Fecha: 2026-07-31

## Qué resuelve

Hoy, cuando una caja física lleva **más de un artículo**, la etiqueta de caja
solo habla del primero. Lo decide `AmiEtiquetasGenerador.etiquetaDe()` a través
de `CajaFisica.lider()` (= la primera línea de esa caja en el packing list): de
ahí salen `REFERENCE`, `COLOR CODE`, la decisión bolso/cinturón y la clave de
búsqueda de los dos EAN en el excel de pedido. `ApcEtiquetasGenerador` hace lo
mismo. Ya existe un aviso para el caso, pero la etiqueta sale incompleta: los
demás artículos de la caja no aparecen y sus códigos de barras no se imprimen en
ningún sitio.

Un matiz que conviene dejar escrito porque cambia con este diseño: en bolsos, la
`QUANTITY` de hoy **no** es la del primer artículo, es la **suma de todas las
líneas** de la caja, incluidas las de otras referencias. Es decir, la etiqueta
actual mezcla ya dos criterios distintos.

Esta feature hace dos cosas:

1. **La etiqueta muestra los N artículos** (bolsos), concatenando los campos que
   son por artículo con `" / "` y encogiendo la fuente si hace falta.
2. **Los códigos de barras que no caben** se vuelcan a una hoja
   `CODIGOS BARRAS EXTRA` dentro del mismo libro de esa destinación, para que el
   operario tenga algo escaneable de cada artículo.

## Alcance

- **AMI**: concatenación + hoja de códigos de barras extra.
- **APC**: solo concatenación. `ApcEtiquetasExcelBuilder` **no imprime ningún
  código de barras** (solo replica el logo de la plantilla), así que la hoja
  extra no tiene contenido posible. Cuando APC tenga barcodes, la pieza
  compartida ya estará ahí.
- **Resto de clientes**: sin cambios; sigue el mensaje de "en desarrollo".
- **Cinturones**: la etiqueta **no cambia** en absoluto. Sí generan filas en la
  hoja extra (ver más abajo).
- **Billeteros**: fuera de alcance, pendiente de confirmar si se comportan como
  bolsos o como cinturones.
- **Cajas que mezclan un bolso y un cinturón**: decisión del usuario — *no
  ocurre en el almacén*. Se conserva el comportamiento actual (manda el tipo del
  líder) y no se añade código para ese caso.

## Qué es "un artículo" de una caja

La pieza que faltaba: pasar de "líneas de la caja" a "artículos de la caja".

| Tipo de caja | Clave de artículo | Cantidad |
|---|---|---|
| Bolsos (líder no cinturón) | `referencia` + `codigoColor` | suma de sus líneas |
| Cinturones (líder cinturón) | `referencia` + `codigoColor` + `talla` | suma de sus líneas |

- El orden es el de **primera aparición en el packing list**.
- Varias líneas del mismo artículo se **funden sumando cantidades**. Con una
  sola línea, o con varias del mismo artículo, sale un único artículo: el
  comportamiento de hoy es el caso particular de N=1.
- Dos artículos distintos que coincidan en el valor de un campo (p.ej. el mismo
  color code) lo **repiten** en la concatenación, no se deduplica.
- En cinturones la talla entra en la clave porque **cada talla tiene su propio
  EAN-13** en el excel de pedido de AMI. Una caja de una sola referencia con
  tallas 85/90/95 son tres artículos, y dos de ellos van a la hoja extra.

## 1. Concatenación en la etiqueta (bolsos)

Campos de la etiqueta de AMI y su tratamiento:

| Campo | Origen |
|---|---|
| `REFERENCE` | concatenado `" / "`, un valor por artículo |
| `COLOR CODE` | concatenado `" / "`, el resuelto del excel de pedido de cada artículo |
| `QUANTITY` | concatenado `" / "` (`3 / 5`), en vez de la suma total de hoy |
| `SIZE` | `"U"`, único (no `"U / U"`) |
| `ORDER Nº`, temporada, `GROSS WEIGHT`, `PARCEL` | de la caja, sin cambios |
| Códigos de barras de la etiqueta | los del **primer** artículo, más el Code 128 del PO (que es de la caja) |

En APC, lo mismo sobre `REFERENCE`, `COLOUR` y `PIECES`.

Consecuencia técnica: la búsqueda en el excel de pedido pasa de hacerse **una
vez, para el líder**, a hacerse **una por artículo** — cada uno tiene su color
code, su EAN-13 y su EAN128. Los avisos por artículo no encontrado o por EAN128
incoherente se emiten igual, indicando la caja.

### Aviso al usuario

El aviso existente se conserva y cambia de texto, porque ahora informa de algo
accionable — hay una hoja más que imprimir:

- Varias referencias/colores en la caja:
  `La caja N de <destino> mezcla varias referencias/colores: se han generado códigos de barra aparte para imprimir`
- Cinturones, una sola referencia con varias tallas:
  `La caja N de <destino> lleva varias tallas: se han generado códigos de barra aparte para imprimir`

Se emite exactamente cuando esa caja produce filas en la hoja extra.

## 2. Ajuste del tamaño de fuente

Se aplica a las celdas concatenadas. Vía elegida: **tamaño calculado +
`shrinkToFit` como red de seguridad**.

Comprobaciones hechas sobre `client-labels/ami-etiquetas-template.xlsx` antes de
decidir:

- Las celdas de valor de la columna C **no están combinadas** (los únicos
  `mergeCell` son `B:C` de cabecera y de la dirección de JAPAN) y **no tienen
  `wrapText`** — el `styles.xml` sí trae 9 estilos con `wrapText="1"`, pero
  ninguno cae en esas celdas. Es decir, Excel **sí** honraría `shrinkToFit` aquí:
  no es un callejón sin salida.
- La columna C mide 61,5 unidades de ancho (calibradas a 11 pt), así que a la
  fuente grande de `REFERENCE` caben ~28 caracteres.
  `ULL163.AL052 / ULL753.AL0168` son exactamente 28: con 2 artículos va al
  límite y con 3 se desborda seguro.

Por qué no `shrinkToFit` a secas:

- POI no recalcula nada, así que un test solo podría afirmar el **atributo**, no
  el efecto. El proyecto prueba los builders reabriendo el `.xlsx` y comprobando
  celdas reales; un tamaño de fuente calculado sí es afirmable.
- No tiene suelo: con 5 artículos Excel encogería hasta lo ilegible.
- Si alguna vez se activa `wrapText` en la plantilla, deja de funcionar en
  silencio.

Regla: capacidad estimada `= anchoColumnaEnChars * 11 / tamañoPt`; si el texto
no cabe, se reduce el tamaño proporcionalmente con **suelo de 8 pt**, y se marca
además `shrinkToFit(true)` por si aun así se pasa. Con un solo artículo el
tamaño calculado coincide con el de la plantilla y **el fichero sale igual que
hoy**.

Cuidado con el tope de POI de ~64.000 `CellStyle` por libro: el estilo clonado
se **cachea por (estilo original, tamaño)**, porque estas celdas se escriben dos
veces por caja y un envío grande tiene muchas cajas.

## 3. Hoja `CODIGOS BARRAS EXTRA` (solo AMI)

- Va **en el mismo libro de la destinación**, como segunda hoja, con ese nombre
  exacto.
- Se crea **solo si hay al menos una fila**. Si ninguna caja tiene artículos
  sobrantes, el fichero es el de hoy.
- Una fila por artículo **2..N** de cada caja, todas las cajas seguidas en una
  sola hoja, en orden de caja y de artículo.

Columnas:

| CAJA | REFERENCE | COLOR CODE | SIZE | QUANTITY | EAN-13 | EAN128 |
|---|---|---|---|---|---|---|

- `CAJA` es el número de caja: sin él la fila no se puede casar con su etiqueta.
- Los dos últimos son **imagen de código de barras escaneable**, con su valor en
  texto debajo. Se reutilizan `CodigoBarrasEan13` y `CodigoBarrasCode128`, y la
  caché de imágenes del libro (un envío repite mucho el mismo código).
- El Code 128 del PO **no se repite**: es de la caja y ya está en la etiqueta.
- Si el artículo no aparece en el excel de pedido: la fila se escribe igual, sin
  imágenes, con el aviso ya existente. **Nunca** se rellena con la fila de otro
  PO (regla vigente del proyecto).
- El `EAN128` se pasa a Code 128 **verbatim**, como en la etiqueta.

## Arquitectura

Tres clases nuevas en `service/etiquetas/`, que ya es la capa común de AMI y
APC:

- **`ArticulosDeCaja`** (+ record `ArticuloEtiqueta(referencia, codigoColor,
  talla, cantidad)`): convierte una `CajaFisica` en su lista de artículos según
  la tabla de claves de arriba, y expone el `unir(articulos, campo)` que hace el
  `" / "`. Sin dependencias de cliente ni de POI.
- **`AjusteFuente`**: dado el texto, la celda y el ancho de su columna, calcula
  el tamaño y aplica un `CellStyle` clonado con ese `Font` y `shrinkToFit`.
  Mantiene la caché de estilos.
- **`HojaCodigosBarrasExtra`**: escribe la hoja en un `XSSFWorkbook` dado a
  partir de una lista de filas. **Sin plantilla `.xlsx`**: la maquetación son
  constantes, igual que `EscandallosExcelBuilder` y
  `EtiquetasArticuloExcelBuilder`. Ancla las imágenes con `AnclajeImagen.fijo`.

Los generadores de cliente siguen decidiendo **qué** poner (AMI resuelve sus EAN
contra el excel de pedido; APC no tiene barcodes); la capa común decide **cómo**
se agrupa, se concatena y se maqueta.

`AmiEtiquetasExcelBuilder.generar()` recibe, además de las etiquetas, la lista
de filas extra, y llama a `HojaCodigosBarrasExtra` después de
`dejarSoloLaHoja(...)` para que no se la lleve por delante.

## Errores y avisos

Se mantiene la regla del proyecto: **nunca fallar en silencio, nunca bloquear
por datos que un humano puede resolver**. Todo lo anterior produce avisos en
`ResultadoEtiquetas`, y el excel se genera igual con las celdas o las imágenes
que falten en blanco.

## Verificación

- Tests puros (JUnit 5, `new`) para `ArticulosDeCaja` (agrupación, orden, fusión
  de líneas repetidas, bolsos vs cinturones) y `AjusteFuente` (tamaño por
  longitud, suelo de 8 pt, caché de estilos).
- `AmiEtiquetasGeneradorTest`: caja de 1 artículo → mismos valores que hoy; caja
  de bolsos con 2 y con 3 artículos → campos concatenados; caja de cinturones
  con varias tallas → etiqueta idéntica a hoy y filas extra generadas; avisos
  con el texto nuevo.
- `AmiEtiquetasExcelBuilderTest`: reabrir el `.xlsx` y comprobar que la hoja
  `CODIGOS BARRAS EXTRA` existe con las filas y valores esperados y con el
  número de imágenes esperado; y que **no existe** cuando no hay sobrantes.
- `ApcEtiquetasGeneradorTest`: concatenación en bolsos, cinturones sin cambios.
- No-regresión: la suite actual de etiquetas debe pasar sin tocar.
- Comprobación manual: dejar en `target/` un excel con una caja de 3 artículos
  para abrirlo en Excel y ver el encogido real de la fuente. Es la única parte
  que ningún test puede afirmar del todo.
