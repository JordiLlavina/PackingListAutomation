# Etiquetas de palet de AMI

Diseño validado el 2026-08-05.

## Objetivo

Que el excel de etiquetas de caja de AMI de cada destinación traiga **una hoja
más con las etiquetas de palet**, igual que ya hacen los de APC. Una etiqueta
por palet, media página cada una (dos por A4).

Afecta a las tres destinaciones que ya tienen etiquetas de caja: FRANCE, CHINA
y JAPAN.

## Lo que trae la plantilla

El usuario ha añadido tres hojas a `docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx`:
`Etiquetas Palets JAPAN`, `Etiquetas Palets CHINA` y `Etiquetas Palets FRANCE`.
Comprobado abriendo el XML del fichero:

- Las tres hojas son **estructuralmente idénticas**: bloque de 7 filas que
  arranca en la fila 2 (índice 1 de POI), dos bloques de ejemplo, y un área de
  impresión `$A$1:$D$15` que abarca exactamente esos dos bloques más la fila 1.
- Encima del primer bloque hay una fila con un **contador suelto en `E1`**
  (valor `1`), fuera del área de impresión. Es un apunte a mano del cliente,
  como el que APC trae en la fila 1 de sus hojas de palet.
- Dentro del bloque: `EXPEDITEUR` y `DESTINATAIRE` (valores fijos por
  destinación, ya horneados en la plantilla), `DESTINATION`, una fila en
  blanco, `Nombre total de colis sur la palette` y `Poids brut`. Los dos
  valores que hay que rellenar van en la columna C (índice 2), filas de índice
  5 y 6.
- **No hay imágenes ni celdas combinadas** en estas hojas: replicar el bloque
  es solo copiar filas, estilos y altos.
- Las tres hojas de etiquetas de caja **no han cambiado**: el diff del XML
  normalizado (sin los índices de estilo, que Excel renumera al guardar, ni el
  orden de los `mergeCell`) sale vacío. `AmiEtiquetaLayout` y sus anclajes
  siguen valiendo tal cual.

## Comportamiento

### Cuándo se genera la hoja

El campo `palet` es opcional en el JSON de entrada. Regla:

> Si a **una sola caja** de la destinación le falta el palet asignado —o si la
> destinación no trae ningún palet—, ese excel sale **sin hoja de palets** y
> con un aviso al usuario.

Es más estricto que APC, que genera la hoja igualmente en blanco o a medias.
El motivo: una hoja de palets incompleta se imprime y se pega en bultos
reales; media hoja de etiquetas correctas invita a asumir que están todas.

### Qué lleva cada etiqueta

- **Nombre total de colis sur la palette**: `Nº <mínimo> à Nº <máximo>` con los
  números de caja de las cajas que tienen **ese palet asignado**, leídos del
  dato. No se usa el rango `cajaInicio..cajaFin` de `PaletData`: ese es el del
  JSON original y se queda viejo en cuanto el usuario corrige un palet en la
  pantalla de revisión. Un palet de una sola caja imprime `Nº 5 à Nº 5`: el
  formato no cambia nunca, así que nada depende del número de cajas.
- **Poids brut**: la suma de los pesos brutos de las **cajas físicas** del
  palet (uno por caja, el de su línea líder — nunca sumar líneas) más la tara
  del palet: la del JSON si viene, 10 kg si no. Mismo cálculo que APC.
  Formato `64,58 Kg`, siguiendo el mock de la plantilla de palet (las
  etiquetas de caja de AMI usan `KGS`, que es otro rótulo del cliente).
- El resto de la etiqueta (expedidor, destinatario, destinación) es estático y
  viaja en la plantilla.

### Avisos

Nunca se bloquea ni se lanza excepción; se avisa y se genera lo que se pueda,
como el resto del proyecto:

- Falta el palet de alguna caja, o no hay palets: la destinación va sin hoja de
  palets, con aviso que lo diga explícitamente.
- Alguna caja de un palet sin peso: esa etiqueta va con el peso en blanco y su
  aviso. La hoja se genera igual.
- Un palet declarado al que no apunta ninguna caja: se omite su etiqueta, con
  aviso.

## Diseño técnico

Espejo del builder de APC, que ya resuelve este mismo problema. Nada de capa
nueva: la lógica de palets son ~60 líneas y no tiene entidad propia como
módulo.

### `AmiEtiquetaLayout`

Un campo más en el record, `nombreHojaPalets`, y cuatro estáticos compartidos
por las tres destinaciones, porque las tres hojas son idénticas:

```
FILA_PRIMER_PALET   = 1   // el bloque no arranca en la fila 0: encima va el contador
ALTURA_BLOQUE_PALET = 7
FILA_PALET_COLIS    = 5
FILA_PALET_PESO     = 6
```

Las filas se expresan como absolutas del primer bloque, igual que en
`ApcEtiquetaLayout`; el palet i-ésimo se escribe desplazado
`i * ALTURA_BLOQUE_PALET`.

### `BloqueEtiquetaModelo`

Sobrecarga `capturar(hoja, filaInicio, altura)`, que guarda filas y celdas
combinadas **relativas** a `filaInicio`. La firma actual delega con
`filaInicio = 0` y no cambia de comportamiento, así que APC y la hoja de cajas
de AMI no se enteran. Hace falta porque el bloque de palet de AMI empieza en la
fila 1, no en la 0.

### `AmiEtiquetasExcelBuilder`

- `generar(...)` recibe además `List<EtiquetaPaletAmi>` (record nuevo:
  `String colis, String poidsBrut`, ya formateados, como `EtiquetaPaletApc`).
- `dejarSoloLaHoja` pasa a `dejarHojas(libro, hojaCajas, hojaPalets)`: la de
  cajas queda **en el índice 0** —el código existente lo asume en
  `getSheetAt(0)`, `actualizarAreaImpresion` y `setActiveSheet(0)`— y la de
  palets en el 1. Con la lista vacía, la hoja de palets se borra del libro.
- `escribirHojaPalets`: blanquea el contador de `E1`, captura el bloque desde
  la fila 1, lo replica por palet, escribe colis y peso, pone salto de página
  **cada dos palets** (media página cada uno) y estira el área de impresión de
  esa hoja hasta la última fila escrita.
- No hace falta replicar imágenes: estas hojas no traen ninguna.

`HojaCodigosBarrasExtra` sigue creando su hoja al final; con palets el libro
queda como cajas / palets / códigos de barras extra.

### `AmiEtiquetasGenerador`

Un método `etiquetasDePalet(cajasFisicas, palets, nombreDestino, avisos)` con
las reglas de arriba, y la llamada al builder pasando su resultado.
`generar()` ya tiene los palets a mano (`DestinoImportado.getPalets()`) y hoy
los ignora; solo hay que pasarlos a `generarDestino`, que recibe el
`DestinoData` pelado, exactamente como hace `ApcEtiquetasGenerador`.

El cálculo del peso queda duplicado con el de `ApcEtiquetasGenerador` (~10
líneas). Es deliberado: los avisos y el comportamiento ante datos incompletos
son distintos en cada cliente, y extraerlo obligaría a parametrizar justo eso.

## Tests

Siguiendo el patrón del proyecto: reabrir el `.xlsx` generado con POI y
comprobar celdas reales.

- `AmiEtiquetaLayoutTest`: las tres hojas de palet existen en la plantilla, y
  las filas de colis y peso se localizan **por su rótulo** de la columna B
  (`Nombre total de colis sur la palette`, `Poids brut`), no por un literal
  contra otro literal. Ancla también la altura del bloque contra la separación
  real entre los dos bloques de ejemplo.
- `AmiEtiquetasExcelBuilderTest`: con tres palets, la hoja está, tiene tres
  bloques, los valores caen en su celda, hay salto de página tras el segundo y
  el área de impresión llega a la última fila. Con la lista vacía, el libro
  **no** tiene la hoja de palets.
- `AmiEtiquetasGeneradorTest`: el rango sale de las cajas y no del `PaletData`,
  el peso suma cajas más tara, una caja sin palet deja la destinación sin hoja
  y con aviso, y una caja sin peso deja esa etiqueta sin peso pero con hoja.

## Fuera de alcance

- Los demás clientes: APC ya tiene sus etiquetas de palet y no se toca.
- La pantalla de revisión: los palets ya se editan ahí.
- El contador manual de `E1`: se blanquea, no se rellena. Es un apunte del
  cliente y está fuera del área de impresión.
