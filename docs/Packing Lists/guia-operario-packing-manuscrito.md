# Cómo escribir el packing a mano para que el escáner lo lea bien

Guía para el operario de almacén. Las hojas se escanean y las lee una IA que
las convierte en el packing list de Excel. La IA lee bien la letra normal:
casi todo es **mantener las costumbres de siempre**. Solo se pide **una cosa
nueva**, la regla 1, y es un trazo de bolígrafo por artículo.

## Las 4 reglas de oro

### 1. Separa con una barra "/" los campos de la línea del artículo (NUEVO)

En la línea de arriba de cada bloque (la del `MOD`) van juntos la referencia,
el nombre del artículo y el color. Escritos seguidos, no hay forma de saber
dónde acaba uno y empieza otro. Una barra entre ellos lo resuelve:

```
✓  MOD 67043 SJ / sac Le Neige / CLOU CAMEL
✗  MOD 67043 SJ sac Le Neige CLOU CAMEL
```

- **El orden es siempre el mismo**: referencia primero, color al final.
- Si el artículo **no tiene nombre**, dos campos y una sola barra:
  `MOD 67043 / CLOU CAMEL`.
- **Solo en esa línea.** En las de cajas la barra sigue significando lo de
  siempre (`122/75` = 122 unidades de la talla 75) y no hay que cambiar nada.
- Es lo que más ayuda en **APC**, donde el nombre del artículo y el color son
  varias palabras seguidas (`sac Le Neige CLOU CAMEL`) y hoy hay que
  adivinar el corte. En AMI ayuda menos porque los puntos de la referencia ya
  marcan el final, pero ponerla no molesta.

### 2. Apunta SIEMPRE el reparto de palets, con sus cajas

Al final de la última hoja de cada destinación (o en una hoja aparte),
la tabla de qué cajas van en cada palet:

```
PALETS:
1   1-12
2   13-24
3   25-36
```

✗ `wh. 5 palets` a secas **no sirve para las etiquetas**: sin el reparto,
las etiquetas de palet no se pueden imprimir y hay que teclearlo después a
mano. Un recuento sí ayuda como comprobación — mejor las dos cosas.

### 3. Un solo peso por caja, con coma

- El peso es el de la **caja entera ya cerrada** (bruto), una vez por caja.
- Con **coma o punto** para los decimales, no apóstrofo: ✓ `13,52 kg`
  ✗ `13'52kg`.
- Dos decimales bastan: ✓ `12,82` ✗ `12'820`.
- Si necesitas apuntar dos pesos (neto y bruto), márcalos: `N 12,82  B 13,94`.
  Un número suelto al lado de otro no se sabe cuál es cuál.

### 4. Corrige tachando, no escribiendo encima

- **Tacha entero** lo que ya no vale y escribe el valor nuevo **al lado**,
  no encima del viejo.
- **Rodea con un círculo el valor definitivo** cuando haya varios números
  cerca. Lo rodeado manda: así se lee hoy y funciona bien.
- Lo tachado se ignora por completo — tachar un bloque entero es la forma
  correcta de anularlo.

## Referencias y artículos

- **APC**: mejor el código completo tal como sale del pedido impreso
  (`PXCBS-F67043`); si no, como mínimo con la letra: `F67043`. El programa
  completa el resto, pero cuanto más código, menos fallos.
- **AMI**: referencia completa con sus puntos (`UBL029.AL0216.2221`): los dos
  primeros grupos son el modelo y el tercero el color. Mayúscula de imprenta,
  cuidando `O`/`0` y `L`/`C`, que son lo que más se confunde.
- Las siglas tipo `SJ`/`SS` mejor junto al nombre del artículo, no pegadas al
  código. Con la barra de la regla 1 da igual de qué lado caigan: nunca se
  cuentan como parte de la referencia.

## Cajas y cantidades

La notación de siempre funciona — mantenerla tal cual:

- `Nº 1, 2 x 61: 122/75  13,52kg` → cajas 1 y 2, 61 unidades cada una.
  El total detrás de los dos puntos (122) es una comprobación valiosa:
  **seguir apuntándolo**.
- `Nº 15 - 83 x 5 sacs (69 CAJAS)` → el recuento entre paréntesis también
  ayuda a detectar errores: **seguir apuntándolo**.
- `TODO Nº 1` debajo de varios artículos → todos van en la caja 1. Funciona;
  también vale poner `Nº 1` delante de cada artículo.
- La medida de caja (`60x40x40`) escrita una vez por grupo de cajas, debajo
  del grupo al que aplica. **Que no falte en ningún grupo**: la IA tiene
  prohibido copiar la del grupo de al lado (podría no ser la misma), así que
  un grupo sin medida hay que completarlo a mano en la pantalla de revisión.
- **No reutilizar números de caja** dentro de una misma destinación (salvo
  cajas mixtas, que repiten número a propósito).

## Las hojas

- **Numerar cada hoja** arriba (1, 2, 3…) — ya se hace y es clave para leer
  en orden. Mejor aún con el total: `1/3`, `2/3`, `3/3`.
- Arriba de cada hoja, **cliente y destinación** (`AMI PARIS`, `APC Japan`).
- Si un artículo no cabe al final de la hoja, **tachar lo empezado** y
  reescribirlo entero en la hoja siguiente.
- Las dudas, con `?` y **al margen**, no encima del dato. La IA las recoge y
  las enseña como aviso, no se pierden.

## Qué pasa si algo falta

Nada se rompe: los pesos que falten los calcula el programa, los palets sin
reparto se rellenan en la pantalla de revisión y cualquier descuadre sale
como aviso para corregirlo ahí. Estas reglas solo ahorran ese trabajo de
después.
