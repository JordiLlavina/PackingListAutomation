# Ejemplos de entrada

## Los `.json` son fixtures, no datos del cliente

`envio-*.json` los mantiene **el usuario** para pruebas visuales rápidas, y los
**generó una IA** a partir de la forma del JSON, sin conocer la realidad del
almacén: números de caja, unidades, pesos, medidas y reparto en palets están
puestos a ojo. Sirven para ejercitar el código de punta a punta y como fixture
de los tests, y nada más. Lo mismo vale para los JSON de ejemplo que aparecen
dentro de los documentos de `docs/`, empezando por
`docs/Packing Lists/campos-json-por-cliente.md`.

**No son autoridad sobre cómo son los datos reales de un cliente.** No deducir
de ellos qué colores tiene una referencia, qué POs usa una destinación ni cómo
se comporta el fichero de un cliente.

La única excepción parcial es `envio-ami-bags-y-belts.json`: sus **claves**
(referencia, color, talla, PO) sí se copiaron del pedido real, así que sus
etiquetas salen con EAN de verdad. Las cantidades y los pesos siguen inventados,
y la correspondencia no está verificada por ningún test: si una edición futura
pone un PO que no existe, las etiquetas salen sin EAN y con aviso — no falla
nada, simplemente el ejemplo deja de ser realista.

## Lo que sí es un fichero real

**Los datos reales de cliente son los excels de pedido, y solo esos.** Hay cuatro:

- `docs/Etiquetas cajas/AMI documentos test/EAN PUNTOTRES H26.xlsx` (AMI, temporada H26), copiado
  aquí como `EAN PUNTOTRES H26.xlsx`.
- `docs/Etiquetas cajas/AMI documentos test/EAN PUNTOTRES SS27.xlsx` (AMI, temporada SS27), copiado
  aquí como `EAN PUNTOTRES SS27.xlsx`. Es el **formato nuevo** del cliente: hoja
  `Feuil1 Original`, cabeceras `P.O.` / ` DESCRIPTION COLORIS` / `EAN 13 ` /
  `CODE 128`, sin columna Made in, y el sufijo de PO `PRESS` (marketing).
  `AmiPedidoSs27RealTest` lo ancla; sus 100 filas tienen un Code 128 coherente
  con su EAN13 y su PO.
- `docs/Etiquetas cajas/APC documentos test/APC_PEDIDO_FALL26.xlsx` (APC, Fall 26), copiado aquí
  como `APC_PEDIDO_FALL26.xlsx`. Lo lee `ApcPedidoExcel` para completar el
  número de pedido, y `ApcPedidoExcelTest` ancla su comportamiento: la hoja se
  localiza por sus tres cabeceras (`Article`, `Document d'achat` y `Notre
  référence`, porque otras dos hojas del libro tienen las dos primeras) y
  `Article` + los tres últimos dígitos del pedido identifican una única fila de
  las 130.
- `docs/Etiquetas cajas/APC documentos test/LCT_Spring 27_Puntotres.XLSX` (APC, Spring 27),
  copiado aquí como `APC_PEDIDO_SPRING27.xlsx`. Mismo libro que el de Fall 26
  pero **sin la columna `Couleurs`**: el color sale del `sku` (`Article` +
  color, cierto en sus 162 filas). Trae una destinación nueva, `Retail USA`,
  hija de `D. USA`. Lo anclan `ApcPedidoExcelTest` y `PedidoCorteRealTest`.

De los demás `.xlsx` de `docs/` lo real es la **maquetación** (son las
plantillas y las salidas que usa el cliente); su contenido es de relleno.

La copia del pedido de AMI describe los datos del cliente con sus rarezas (hay
8 filas cuyo EAN128 no cuadra con sus propias columnas, y 147 de sus 151 filas
están ocultas por un filtro guardado); `AmiPedidoRealTest` ancla su
comportamiento. Si se actualiza el de `docs/`, actualizar también la copia:

    cp "docs/Etiquetas cajas/AMI documentos test/EAN PUNTOTRES H26.xlsx" \
       "src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx"
    cp "docs/Etiquetas cajas/AMI documentos test/EAN PUNTOTRES SS27.xlsx" \
       "src/test/resources/ejemplos/EAN PUNTOTRES SS27.xlsx"

Lo mismo con el de APC:

    cp "docs/Etiquetas cajas/APC documentos test/APC_PEDIDO_FALL26.xlsx" \
       "src/test/resources/ejemplos/APC_PEDIDO_FALL26.xlsx"
    cp "docs/Etiquetas cajas/APC documentos test/LCT_Spring 27_Puntotres.XLSX" \
       "src/test/resources/ejemplos/APC_PEDIDO_SPRING27.xlsx"

`escandallos/` son también copias de escandallos reales del ERP.

`taller/Packing List Taller Exemple.xlsx` es el **template** que usa el taller
para mandar su packing list. De él lo real es la **maquetación**, no los
valores: la cabecera en la fila 9 con membrete y leyenda encima, el título de
`QTITE / COLIS` partido en dos líneas dentro de la celda, los totales de bultos
y peso debajo de la última fila, y una fila de otro cliente entre las de AMI.
Todo eso es lo que `TallerColisExcelTest` ancla, y nada de ello lo habría
traído un fixture inventado. Las referencias y las cantidades son de relleno.

    cp "docs/Packing Lists/Packing List Excel Taller tests/Packing List Taller Exemple.xlsx" \
       "src/test/resources/ejemplos/taller/Packing List Taller Exemple.xlsx"

`corte/gimp_rgb_420_with_alpha.heic` es el HEIC de prueba de los documentos del
corte: 430×430 px y 8 KB, sacado del repositorio de Openize.HEIC
(`TestsData/samples/`, con la licencia de ese repo). **No es una foto de
producto ni personal**, y así tiene que seguir: en el repo no entra ninguna foto
real. Las demás imágenes de esos tests las dibuja `FotosDePrueba`.

`corte/PROD_APC_SPRING27.xlsx` es el **excel de producción de Puntotres** de APC
Spring 27 (`docs/Documentos de Corte/prod APC excel.xlsx`), el que lee
`ProduccionPuntotresExcel`. Es un documento nuestro y sus cantidades son
reales (la columna F cuadra exacta con el pedido de Spring 27), pero la copia
va **sin las fotos de producto** que el original lleva incrustadas en la
columna B: se copiaron solo las celdas, las fórmulas y las celdas combinadas.
Para regenerarla hay que volver a copiar las celdas, no el fichero entero.
`corte/PROD_AMI_SS27.xlsx` es lo mismo para AMI SS27 (`docs/Documentos de
Corte/prod AMI SS27 excel.xlsx`), también sin sus fotos. Su disposición es
distinta de la de APC (ver `ProduccionPuntotresExcel`), y su pedido (E) más su
PRESS (G) cuadran exactos con `EAN PUNTOTRES SS27.xlsx`.

`taller/packing taller fez ejemplo.xlsx` y `taller/packing taller tanger ejemplos.xlsx`
son packings de taller con **formato propio**, los que entran por "Excel Formato
Taller" y Claude pasa al formato Puntotres (`ConversionFormatoTallerService`).
El de Fez tiene cuatro hojas y el packing está en `LISTE DE COL`, con color y
destinación mezclados en `DESIGNATION`; el de Tánger trae dos envíos, uno por
hoja, con la destinación y el código juntos (`Korea 690`) y una tablita de cajas
con defectos debajo. `ConversionFormatoTallerServiceTest` comprueba que el
volcado a texto que viaja a Claude no se deja nada de eso.

    cp "docs/Packing Lists/Packing List Excel Taller tests/packing taller fez ejemplo.xlsx" \
       "src/test/resources/ejemplos/taller/packing taller fez ejemplo.xlsx"
    cp "docs/Packing Lists/Packing List Excel Taller tests/packing taller tanger ejemplos.xlsx" \
       "src/test/resources/ejemplos/taller/packing taller tanger ejemplos.xlsx"
Medido en el de Tánger: las filas de la tablita de cajas «MAL» van con **fondo
rojo**, que es lo que el volcado anota como `[fondo #FF0000]`.
