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

**Los datos reales de cliente son los excels de pedido, y solo esos.** Hay dos:

- `docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx` (AMI, temporada H26), copiado
  aquí como `EAN PUNTOTRES H26.xlsx`.
- `docs/Etiquetas cajas/APC_PEDIDO_FALL26.xlsx` (APC, Fall 26), que hoy no lo
  lee ni el código ni ningún test.

De los demás `.xlsx` de `docs/` lo real es la **maquetación** (son las
plantillas y las salidas que usa el cliente); su contenido es de relleno.

La copia del pedido de AMI describe los datos del cliente con sus rarezas (hay
8 filas cuyo EAN128 no cuadra con sus propias columnas, y 147 de sus 151 filas
están ocultas por un filtro guardado); `AmiPedidoRealTest` ancla su
comportamiento. Si se actualiza el de `docs/`, actualizar también la copia:

    cp "docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx" \
       "src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx"

`escandallos/` son también copias de escandallos reales del ERP.
