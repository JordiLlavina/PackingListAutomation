# Ejemplos de entrada

## Los `.json` son fixtures, no datos del cliente

`envio-*.json` los mantiene **el usuario** para pruebas visuales rápidas: son
envíos inventados a mano, con referencias, colores, pesos y números de pedido
puestos a ojo. Sirven para ejercitar el código de punta a punta y como fixture
de los tests, y nada más.

**No son autoridad sobre cómo son los datos reales de un cliente.** No deducir
de ellos qué colores tiene una referencia, qué POs usa una destinación ni cómo
se comporta el fichero de un cliente. Para eso están los ficheros de `docs/`.

## Lo que sí es un fichero real

`EAN PUNTOTRES H26.xlsx` es copia literal de
`docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx`, el excel de pedido real de AMI de
la temporada H26. Ese sí describe los datos del cliente, con sus rarezas (hay 8
filas cuyo EAN128 no cuadra con sus propias columnas, y 147 de sus 151 filas
están ocultas por un filtro guardado). `AmiPedidoRealTest` ancla su
comportamiento. Si se actualiza el de `docs/`, actualizar también esta copia:

    cp "docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx" \
       "src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx"

`escandallos/` son también copias de escandallos reales del ERP.
