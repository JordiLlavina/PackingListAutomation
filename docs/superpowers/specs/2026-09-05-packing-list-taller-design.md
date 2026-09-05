# Packing List Taller — diseño

Fecha: 2026-09-05. Estado: aprobado para implementar.

## 0. Objetivo

Cuarta vía de entrada del asistente de packing lists. El taller manda su propio
packing list en Excel junto con el material. El sistema lo digiere, lo completa
con el excel de pedido del cliente y con decisiones del usuario, y **regenera el
packing desde cero** —cajas y palets— siguiendo las normas del cliente.

El packing del taller **no se respeta**: su numeración de cajas, su medida de
caja y su cantidad por caja son orientativas. Lo que se aprovecha es la
información de artículo: cliente, motivo, referencia, color, talla, código de
pedido y cantidades.

La salida es un `EnvioInput` idéntico al que produce cualquier otra entrada, así
que revisión, packing lists, etiquetas y volcado ERP funcionan sin cambios.

## 1. Decisiones cerradas

Preguntadas y respondidas antes de escribir esto. Se listan porque varias
contradicen la primera redacción de la petición.

| # | Decisión | Nota |
|---|---|---|
| D1 | La **altura es la última** dimensión: `60x40x45` mide 45 cm de alto | Corrige la petición original, que la ponía en medio. Coincide con `TaraProperties` y con el catálogo de taras del yml |
| D2 | La cantidad objetivo de AMI sale de la columna **`Commandé`** del excel de pedido de AMI | Columna H de `EAN PUNTOTRES H26.xlsx`, que hoy no se lee |
| D3 | La destinación de AMI sale del **sufijo del PO**: `CH`→CHINA, `JP`→JAPAN, sin sufijo→PARIS | Mapa en `application.yml`. Sufijo desconocido = aviso bloqueante |
| D4 | En el excel de taller **manda la cabecera**: cada columna se identifica por su título, nunca deduciéndola de lo que haya escrito debajo | Adivinar por el contenido daría un packing plausible y equivocado (ver §3.4) |
| D5 | Persistencia: H2 en fichero + JPA, con **las dos** tablas (memoria de referencias y taras) | Incluye pantalla de edición de taras |
| D6 | En APC **el `CODE` manda**: una fila de taller = un pedido = una destinación | El reparto por prioridad solo actúa entre filas del mismo material |
| D7 | Si el taller manda de más, **se envía el objetivo** y el sobrante se queda, con aviso no bloqueante | Nunca se manda al cliente más de lo que pidió |
| D8 | El flujo desemboca en un `EnvioInput` y reutiliza la cadena existente, extrayendo el tramo común a un `PreparacionRevisionService` | Opción A + trozo de C |
| D9 | Mezclar en una caja exige **la misma `medidaCaja`** | Decisión de diseño, ver §7.3 |
| D10 | Se mezcla **solo si ahorra una caja** | Decisión de diseño, ver §7.3 |

## 2. Flujo de usuario

Se mantienen las tres etapas de `/packing-list`. La novedad vive en la etapa de
carga, partida en dos sub-pasos.

### 2.1 Paso 1a — Carga y digestión

Cuarto botón **`TALLER`** en el `.selector-modo` de `entrada.html`, junto a JSON,
CLAUDE y FORMULARIO. En ese modo el formulario muestra:

- Selector de cliente (el de siempre).
- Excel de **packing list de taller** (obligatorio).
- Excel de **pedido de cliente**: obligatorio en modo TALLER para los clientes
  que lo declaran (`pedido-cliente: true`, hoy AMI y APC), porque sin él no hay
  cantidad objetivo. Para los demás clientes no se pide (§4.3). En el resto de
  modos sigue siendo opcional, como hoy.
- **Altura máxima de palet** en cm, solo si el cliente no es AMI ni APC. Por
  defecto 168, editable.
- La cabecera de siempre: temporada, nº de factura, fechas y nº de comanda. Hacen
  falta igual para generar, así que se piden aquí y no se repiten después.

El botón de envío pasa a **"Digerir Datos Taller"** y apunta a
`POST /packing-list/taller/digerir`.

`EnvioForm` gana dos campos (`excelTaller`, `alturaMaximaPaletCm`) y el valor
`TALLER` en `modo`; no nace un formulario paralelo.

### 2.2 Paso 1b — Ajuste y generación

Pantalla propia (`taller-ajuste.html`): lista de avisos, lista de bloqueos, tabla
editable agrupada por referencia y botón **"Generar Packing"**, deshabilitado
mientras haya bloqueos. Detalle en §9.

### 2.3 Vuelta atrás

Los dos ficheros subidos, la digestión y los ajustes tecleados viven en
`TallerEnCurso` (`@SessionScope`). Desde la revisión se vuelve al paso 1b con
`GET /packing-list/taller/ajuste` sin resubir nada, se corrige y se regenera.

### 2.4 Rutas

```
/packing-list                            modo TALLER = paso 1a
POST /packing-list/taller/digerir     -> paso 1b
POST /packing-list/taller/previsualizar -> paso 1b + panel de resumen
POST /packing-list/taller/generar     -> EnvioInput -> PreparacionRevisionService -> /revision
GET  /packing-list/taller/ajuste      <- volver desde la revisión
```

## 3. Entrada A: excel de packing list de taller

### 3.1 Localización de la hoja

El libro trae varias hojas (en el ejemplar real, `FACTURE` y `LISTE DE COLIS`).
Se busca `LISTE DE COLIS` con el nombre **normalizado**: trim, mayúsculas, sin
acentos, espacios múltiples colapsados.

Si no aparece: error explícito con la lista de hojas encontradas y un desplegable
para que el usuario elija la hoja a mano, que se reenvía al mismo endpoint.

### 3.2 Localización de columnas

La cabecera **no está en la fila 1**: en el ejemplar real está en la fila 9. Se
busca dentro de las **primeras 20 filas**: es cabecera la primera fila que
contenga todas las columnas obligatorias.

- Nombres normalizados igual que la hoja, e ignorando además `.`, `:` y `°`/`º`.
- Mapa de sinónimos por columna.
- Soporte de celdas combinadas en la cabecera (se lee la celda ancla).
- La lectura de filas para al encontrar **5 filas consecutivas totalmente
  vacías**.
- Si falta una columna obligatoria: error bloqueante que nombra exactamente las
  columnas que no se han encontrado **y** las cabeceras que sí se han leído, con
  su fila. Nunca una tabla vacía en silencio.

### 3.3 Columnas

| Columna | Obligatoria | Uso |
|---|---|---|
| `CLIENT` | Sí | Se contrasta con el cliente del selector |
| `MOTIF` | Sí | Motivo del artículo; informativo |
| `REFERENCE` | Sí | Referencia del modelo |
| `COULEUR` | Sí | Color |
| `TAILLE` | No | Talla. Vacía o no numérica → `U`; numérica → esa talla |
| `DESTINATION` | No | Solo informativa (§5.1) |
| `CODE` | Solo APC | Tres últimos dígitos de la comanda |
| `Nº EXPEDITION PUNTOTRES` | No | Trazabilidad; se guarda y no se usa en el packing |
| `QTITE / COLIS` | No | Sugerencia de unidades por caja (§8.1) |
| `QUANTITE` | Sí | Cantidad total recibida de esa referencia + color + talla |
| `N° DE COLIS` | No | Numeración del taller; **se descarta** |

Sinónimos conocidos, ampliables sin tocar la lógica:

```
QTITE / COLIS  ~ QTITE/COLIS ~ QTE / COLIS ~ QTE/COLIS ~ QTE/COLIS
QUANTITE       ~ QUANTITE ~ QTE TOTALE ~ QUANTITE TOTALE
N DE COLIS     ~ COLIS
REFERENCE      ~ REF
COULEUR        ~ COLORIS ~ COLOR
TAILLE         ~ SIZE ~ TALLA
CLIENT         ~ CLIENTE
```

(La normalización quita acentos y signos, así que `QTÉ/COLIS`, `N° DE COLIS` y
`RÉFÉRENCE` caen solos en la misma clave que sus variantes sin adornos.)

Todos los campos de texto se leen en **mayúsculas y con trim**; los numéricos no
se transforman.

### 3.4 El fichero de ejemplo

`docs/Packing Lists/Packing List Taller/Packing List Taller Exemple.xlsx` es un
template del taller: la maquetación es real, los valores no. Se copia a
`src/test/resources/ejemplos/taller/` y el test del lector se ancla contra él,
como `ApcPedidoExcelTest` contra el pedido de APC.

Lo que aporta y ningún fixture inventado habría aportado:

- La cabecera está en la **fila 9**, con el membrete del taller, la factura, la
  fecha y una leyenda de campos obligatorios encima.
- El título de `QTITE / COLIS` viene **partido en dos líneas** dentro de la
  celda (`"QTITE /\nCOLIS"`), para que quepa en el ancho de columna. La
  normalización tiene que colapsar ese salto de línea o la columna no aparece.
- Debajo de la última fila hay **totales con contenido** (`Soit : 40 colis`,
  `Poids Brut`, `Poids Net`) que no son artículos, y una fila en blanco antes.
- La hoja **mezcla dos clientes**: catorce filas de `AMI` y una de
  `PALOMA WOOL`. El lector no la filtra; es la digestión la que bloquea.
- Varias filas comparten el mismo `N° DE COLIS` (la caja 40): así anota el
  taller un bulto mixto. Da igual, porque su packing se descarta entero.

**Sobre D4.** Cada columna se identifica por su título y nunca por lo que haya
escrito debajo. Es tentador hacer lo contrario cuando un título parece no cuadrar
con sus datos —a mí me pasó leyendo este fichero a mano—, pero deducir la columna
del contenido produce un packing con números plausibles y equivocados, y eso no
lo detecta nadie hasta el almacén. Si un taller titula mal una columna, la
respuesta es un sinónimo nuevo en el lector, no una heurística.

## 4. Entrada B: excel de pedido de cliente

Interfaz `ObjetivosPedido`, despachada **por clave de cliente** —no por
`TipoPlantilla`—, igual que `GeneradorEtiquetasCliente`: de dónde sale la
cantidad pedida depende del fichero que manda ese cliente, no de la plantilla que
se le imprime.

```java
interface ObjetivosPedido {
    String clienteSoportado();
    ResultadoObjetivos objetivosPara(List<LineaTaller> lineas, byte[] excelPedido);
}
```

`ResultadoObjetivos` trae, por línea de taller, la lista de
`(destinación, cantidadObjetivo, pedidoCompleto)`, más avisos y bloqueos. Nunca
lanza por datos que un humano puede arreglar.

### 4.1 AMI — `ObjetivosPedidoAmi`

Fichero: el mismo `EAN PUNTOTRES H26.xlsx` que ya se sube. Columnas reales:

```
A Made in | B ARTICLE | C COLORIS | D Libelle coloris | E (formula)
F TAILLE  | G PO      | H Commande | I EAN13 | J EAN128
```

- `AmiPedidoExcel` gana la lectura de `Commandé` y un método de consulta de
  cantidades. El resto del lector no cambia: ya parte el PO en número y sufijo,
  que es exactamente la clave que hace falta.
- Clave de cruce: `ARTICLE` = referencia, `COLORIS` = color, `TAILLE` = talla.
- Destinación = sufijo del PO por el mapa de `application.yml` (**D3**).
- Cantidad objetivo = `Commandé` de esa fila. Varias filas de la misma clave y
  destinación se **suman**.
- `pedido` del `EnvioInput` = el PO de 5 dígitos, sin el sufijo.

### 4.2 APC — `ObjetivosPedidoApc`

Fichero: `APC_PEDIDO_FALL26.xlsx`. Columnas relevantes de la hoja de pedido:

```
D Notre reference (destinacion)  E Categorie de stock (abreviatura)
H Document d'achat (pedido)      J Article (referencia)
M Quantite echeancee (cantidad)  N Couleurs   AD Taille
```

- `ApcPedidoExcel` gana la lectura de `Notre référence` como destinación y de
  `Quantité échéancée`. Ya localiza la hoja exigiendo tres cabeceras.
- Clave de cruce (**D6**): sufijo de `Article` = `REFERENCE` del taller **y**
  `Document d'achat` acabado en los tres dígitos de `CODE`.
- Un `Document d'achat` es una destinación, un artículo y un color, con una fila
  por talla. Así que el `CODE` **ya identifica la destinación**: no hay que
  cruzar por color, que en el pedido viene como código (`LZZ`, `CAW`) y en el
  taller como nombre.
- Cantidad objetivo = suma de `Quantité échéancée` de las filas de ese pedido.
- `pedido` del `EnvioInput` = `Document d'achat` entero.
- Fila de taller **sin `CODE`**: aviso bloqueante; sin él no hay destinación.

### 4.3 Otros clientes

Sin `ObjetivosPedido` propio. Destino único, cantidad objetivo = la `QUANTITE`
del taller, editable en el paso 1b. El excel de pedido no se pide.

### 4.4 Cuando no hay fila

Una referencia + color del taller que no aparece en el pedido: **aviso visible**
en el paso 1b con la fila marcada, y su cantidad objetivo a cero (editable).
Nunca excepción, nunca un dato adivinado.

## 5. Reparto de cantidades por destinación

### 5.1 Origen de las cantidades

La `QUANTITE` recibida del taller **no es** la que se envía. La cantidad objetivo
por destinación sale del pedido de cliente.

La columna `DESTINATION` del taller es **solo informativa**. Si no coincide con
la destinación que sale del pedido, se genera un aviso y **manda el pedido**.

La cantidad objetivo es **editable** en el paso 1b: puede haber entregas
anteriores que restar.

### 5.2 Faltantes

Si lo recibido no cubre todas las destinaciones, se sirven completas las de mayor
prioridad y se quedan cortas las de menor. La prioridad se evalúa sobre la
**destinación hija**, no sobre la padre.

- **AMI**: `CHINA` = `JAPAN` (máxima) > `PARIS`
- **APC**: `CHINE FRANCH` = `JAPAN` = `KOREA` (máxima) > `D. USA` > `RETAIL` > `WHOLESALE`
- **Otros**: destino único, no aplica.

Empate de prioridad con género insuficiente: reparto proporcional a los
objetivos; el resto entero va a la destinación de mayor objetivo, y se avisa.

Una destinación que aparezca y no esté en las reglas del cliente: **aviso
bloqueante**; el usuario la mapea a una conocida antes de generar.

### 5.3 Sobrantes

Recibido > objetivo (**D7**): se empaqueta el objetivo y el resto se queda, con
aviso no bloqueante que nombra referencia, color y unidades sobrantes.

### 5.4 Destinaciones padre de APC

Se reutiliza el `destinos-hijo` que ya existe en `application.yml`:
`CHINE FRANCH` y `AUSTRALIA` cuelgan de `WHOLESALE`; `WHOLESALE CONCESS` cuelga
de `RETAIL`. El pedido real lo confirma: las filas de `Chine franch` llevan
`Catégorie de stock = WH`.

Reparto y prioridad usan la **hija**; el `EnvioInput` lleva la hija en `canal` y
la padre en `destino`, que es lo que `ResolutorDestinosPadre` espera encontrar.

## 6. Reglas por cliente

Todo en `application.yml`, bajo `packing-list.taller`. Añadir una destinación es
una línea; añadir un cliente GENERIC no exige nada.

```yaml
packing-list:
  taller:
    altura-palet-por-defecto-cm: 168   # clientes sin regla propia
    altura-propia-palet-cm: 11         # lo que levanta el palet vacío
    posiciones-palet: 4                # 2x2 sobre la base
    medida-caja-por-defecto: 60x40x40
    clientes:
      "[AMI]":
        numeracion-cajas: CONTINUA     # entre destinaciones
        sufijos-po:
          CH: CHINA
          JP: JAPAN
          "": PARIS
        destinos:
          "[CHINA]": { altura-max-cm: 158, prioridad: 1, mezcla: NINGUNA }
          "[JAPAN]": { altura-max-cm: 158, prioridad: 1, mezcla: NINGUNA }
          "[PARIS]": { altura-max-cm: 168, prioridad: 2, mezcla: MISMO_PEDIDO }
      "[APC]":
        numeracion-cajas: POR_DESTINACION
        destinos:
          "[CHINE FRANCH]": { altura-max-cm: 168, prioridad: 1, mezcla: LIBRE }
          "[JAPAN]":        { altura-max-cm: 155, prioridad: 1, mezcla: LIBRE }
          "[KOREA]":        { altura-max-cm: 155, prioridad: 1, mezcla: LIBRE }
          "[D. USA]":       { altura-max-cm: 155, prioridad: 2, mezcla: LIBRE }
          "[RETAIL]":       { altura-max-cm: 168, prioridad: 3, mezcla: LIBRE }
          "[WHOLESALE]":    { altura-max-cm: 168, prioridad: 4, mezcla: LIBRE }
```

- `prioridad`: número menor = más prioritaria. Empate = mismo escalón.
- `mezcla`:
  - `NINGUNA` — una referencia y un color por caja.
  - `MISMO_PEDIDO` — se mezcla solo entre artículos del mismo número de pedido.
  - `LIBRE` — se mezclan modelos y colores.
- `altura-max-cm` de una **hija** sin regla propia cae en la de su **padre**, y
  si tampoco la tiene, en `altura-palet-por-defecto-cm`. Así `CHINE FRANCH`
  hereda los 168 de `WHOLESALE` aunque tenga prioridad máxima (supuesto 2 de la
  petición, confirmado).
- Clientes sin bloque: destino único, mezcla `LIBRE`, altura la que teclea el
  usuario en el paso 1a y numeración `POR_DESTINACION`.

## 7. Algoritmo de generación de cajas y palets

### 7.1 Geometría

- Medida de caja `LargoxAnchoxAlto` en cm (**D1**): en `60x40x45` la altura es 45.
- El palet tiene **4 posiciones** (2x2). Cada posición es una **pila
  independiente**.
- `alturaUtil = alturaMaxDestinacion − 11`.
- Restricción: la suma de alturas de cada pila no puede superar `alturaUtil`. Las
  cuatro pilas pueden acabar con alturas distintas.
- El **peso no es restricción**. El máximo de 18 kg por caja de AMI se valida en
  la revisión, no aquí.
- Un palet **nunca** mezcla destinaciones. Se acepta el último palet a media
  altura y con pilas incompletas.

### 7.2 Pasos

1. **Reparto por destinación** (§5).
2. **Agrupación en cajas** por destinación, con la regla de mezcla del cliente.
3. **Reparto equitativo** dentro del grupo (§7.3).
4. **Apilado en palets** (§7.4).
5. **Numeración** (§7.5).

### 7.3 Agrupación y reparto equitativo

El **artículo** es la unidad indivisible: referencia + color + talla, con su
`medidaCaja` y `unidadesPorCaja` (por referencia, §8).

**Grupo de mezcla** = artículos de la misma destinación que además comparten:

- la **misma `medidaCaja`** (**D9**) — la caja *es* un cartón concreto: dos
  referencias con cartón distinto no pueden compartir bulto, permita o no
  mezclar el cliente;
- el **mismo pedido**, si la regla es `MISMO_PEDIDO`;
- **nada más**, si la regla es `LIBRE`;
- y con `NINGUNA`, cada artículo es su propio grupo.

Orden dentro del grupo: por referencia en orden de aparición, y dentro de cada
referencia por color. Así se agotan primero los colores de una referencia y solo
se pasa a otra referencia si queda hueco, que es lo que pide la petición.

**Cuándo se mezcla de verdad (D10).** Se comparan dos recuentos:

```
cajasPuras     = suma de ceil(cantidad_i / capacidad_i)   por artículo
cajasMezcladas = ceil( suma de (cantidad_i / capacidad_i) )
```

Si `cajasMezcladas < cajasPuras`, se mezcla; si empatan, cada artículo va en sus
propias cajas. Mezclar complica la etiqueta y el packing list, así que solo se
hace cuando ahorra un bulto de verdad.

**Reparto dentro de las cajas resultantes.** `nCajas` es el recuento ganador y la
cantidad se reparte lo más uniformemente posible **sin superar la capacidad**:

```
base  = cantidad / nCajas        (división entera)
resto = cantidad % nCajas
-> `resto` cajas llevan base+1, el resto llevan base
```

31 unidades con 10 por caja → 4 cajas de 8, 8, 8, 7. Nunca 10, 10, 10, 1.

En un grupo mezclado, cada unidad del artículo *i* ocupa `1/capacidad_i` de caja;
se rellenan las cajas en el orden del grupo hasta el llenado objetivo
(`ocupaciónTotal / nCajas`), de modo que los artículos salen contiguos y solo se
mezclan en las fronteras. Ninguna caja pasa de ocupación 1.

El reparto es **por destinación**: no se equilibran cajas de destinaciones
distintas.

### 7.4 Apilado

Por destinación, con su `alturaUtil`:

1. Ordenar las cajas de mayor a menor altura.
2. Colocar cada caja en la pila del palet actual con **menos altura acumulada**,
   siempre que quepa.
3. Si no cabe en ninguna de las 4 pilas, abrir palet nuevo.

Heurística voraz, suficiente a propósito: no se busca el óptimo. Una caja más
alta que `alturaUtil` no se puede apilar: aviso bloqueante nombrando la medida y
la destinación.

### 7.5 Numeración

- **Cajas**: `1..N`, asignadas **palet a palet** para que cada palet sea un rango
  contiguo — que es lo que `PaletData.contiene()` necesita para que la cadena
  existente vuelva a asignar los palets sin sorpresas. `CONTINUA` numera
  seguido entre destinaciones (AMI); `POR_DESTINACION` reinicia en cada una
  (APC y GENERIC).
- **Palets**: `1..M` por destinación.
- Orden de destinaciones: el de primera aparición en el excel de taller.

### 7.6 Salida

`GeneradorPackingTaller` devuelve un `EnvioInput` con, por destinación: sus
`palets[]` (`palet`, `cajaInicio`, `cajaFin`) y sus `referencias[]` (referencia,
color, talla, pedido, `medidaCaja`, `canal` cuando hay hija, y las entradas de
caja como rangos o cajas sueltas). Los pesos van a `null`: los rellena la
inferencia existente y se ajustan en la revisión.

El JSON generado se guarda en `TallerEnCurso` y se ofrece en un `<textarea>`
plegado del paso 1b, para poder inspeccionar el algoritmo sin depurador.

## 8. Persistencia (H2 + JPA)

Base de datos embebida en fichero, `./datos/packinglist.mv.db`, con
`spring-boot-starter-data-jpa` y H2. `ddl-auto: update`.

### 8.1 `memoria_caja_referencia`

| Campo | Notas |
|---|---|
| `cliente` | Parte de la clave |
| `referencia` | Parte de la clave |
| `medida_caja` | Ej. `60x40x40` |
| `unidades_por_caja` | Entero > 0 |
| `fecha_actualizacion` | La última ejecución gana |

Clave = **cliente + referencia**. El color **no** forma parte: todos los colores
de una referencia comparten cartón y unidades por caja.

**Cascada de resolución al digerir:**

1. Memoria (`cliente + referencia`).
2. Lo que traiga el excel de taller (`QTITE / COLIS`, y medida de caja si
   existiera la columna).
3. `medidaCaja = 60x40x40` y `unidadesPorCaja = null`.

`null` en unidades por caja **bloquea** "Generar Packing" y marca la fila. No se
usa `-1` ni ningún centinela.

**Escritura:** al generar se guardan o actualizan las entradas de todas las
referencias procesadas, y **solo las válidas** (`unidadesPorCaja > 0`): nunca se
memoriza un valor por defecto sin rellenar.

### 8.2 `tara_caja`

| Campo | Notas |
|---|---|
| `medida` | Clave, normalizada como hoy (minúsculas, sin espacios) |
| `tara_kg` | Peso del cartón vacío |
| `fecha_actualizacion` | |

La tabla de taras se **mueve** de `application.yml` a la base de datos, con tres
cuidados:

- `TaraProperties` sobrevive como **semilla**: si la tabla está vacía al
  arrancar, se siembra con el bloque `packing-list.taras` del yml. Así ni el
  arranque en limpio ni los tests que declaran taras por propiedades cambian de
  comportamiento.
- `TaraService` pasa a ser la única puerta: `taraPara(medida)` y
  `tamanosDeMayorAMenor()` (mismo orden por volumen, no alfabético). Los
  consumidores actuales —`WeightInferenceService`, el desplegable TAMAÑO de la
  revisión, el modo FORMULARIO— pasan a usarlo.
- Pantalla **`/taras`** para listarlas, editarlas y añadir una nueva. Sin ella,
  pesar un cartón pasaría de editar un yml a hacer un `UPDATE` a mano, que es
  peor que hoy. Se enlaza desde el menú.

Se mantiene la regla del proyecto: **ningún test fija un valor de tara**. Los
tests que necesitan una tara concreta la siembran ellos.

## 9. UX del paso 1b

Tabla agrupada por **referencia**, con los dos campos que son *por referencia* a
nivel de grupo y no repetidos por color:

```
┌ ULL164.AL0052 ──────────────────────────────────────────────┐
│  Medida de caja [60x40x40 v]  Uds/caja [ 8 ]   (de memoria) │
├─────────────┬────────┬──────────┬────────┬────────┬─────────┤
│ COLOR       │ TALLA  │ RECIBIDO │ CHINA  │ JAPAN  │ PARIS   │
│ KAKI        │ U      │      165 │ [ 64 ] │ [ 29 ] │ [ 72 ]  │
│ NOIR        │ U      │       40 │ [ 20 ] │ [  0 ] │ [ 20 ]  │
└─────────────┴────────┴──────────┴────────┴────────┴─────────┘
```

- **Medida y unidades por caja se editan una vez por referencia** y aplican a
  todos sus colores. Al estar a nivel de grupo, no hay que propagar nada ni
  explicar por qué cambiar una fila cambia otras.
- **Clave de fila**: referencia + color + **talla**. La petición decía referencia
  + color; se añade la talla porque en cinturones de AMI una misma
  referencia+color tiene varias tallas con objetivos distintos, y agruparlas
  perdería el dato. En bolsos la talla es siempre `U` y la fila queda igual que
  lo pedido.
- **Origen del dato**: cada grupo indica si su medida viene de *memoria*, del
  *excel de taller* o del *valor por defecto*.
- **Columna por destinación activa**, con la cantidad objetivo editable.
- **Bloqueos** (impiden generar, con la fila marcada): unidades por caja sin
  valor, destinación sin mapear, fila de APC sin `CODE`. Uno más aparece solo al
  previsualizar o generar, porque depende de la medida tecleada: una caja más
  alta que la altura útil de su destinación.
- **Avisos** (no bloquean, se listan arriba): referencia no encontrada en el
  pedido, discrepancia entre la destinación del taller y la del pedido, columnas
  opcionales ausentes, sobrantes, cliente de la columna `CLIENT` distinto del
  seleccionado.
- **Varios clientes distintos** en la columna `CLIENT`: bloqueante.
- Los avisos usan el vocabulario del almacén, no el del programa: "la entrada",
  nunca "el JSON". Se reutiliza el estilo de `AvisoEtiquetas`.

**Botón "Previsualizar"**: mismo formulario, corre el algoritmo y vuelve al paso
1b con un panel de resumen, sin avanzar de etapa:

```
PARIS   | 240 uds | 24 cajas | 2 palets | último palet 0,92 m de 1,68 m
JAPAN   | 120 uds | 12 cajas | 1 palet  | 1,44 m de 1,58 m
```

Todo por servidor, como el resto del asistente: los botones son submits del mismo
formulario, así que lo tecleado nunca se pierde al recargar.

## 10. Ejemplo trabajado (test de aceptación)

Cliente AMI. Datos inventados, sirven de anclaje.

**Taller:** BAG-A NOIR 31, BAG-A BEIGE 20, BAG-B NOIR 12.

**Pedido:** BAG-A NOIR → CHINA 20, PARIS 20. BAG-A BEIGE → CHINA 10, PARIS 10.
BAG-B NOIR → CHINA 6, PARIS 6.

**Ajustes:** BAG-A → `60x40x40` (40 cm de alto), 10 uds/caja. BAG-B →
`60x40x30` (30 cm de alto), 6 uds/caja.

**Reparto** (CHINA tiene prioridad sobre PARIS):

| Artículo | CHINA | PARIS |
|---|---|---|
| BAG-A NOIR | 20 | 11 (faltan 9) |
| BAG-A BEIGE | 10 | 10 |
| BAG-B NOIR | 6 | 6 |

**Cajas en CHINA** (mezcla `NINGUNA`): 2 de BAG-A NOIR (10+10), 1 de BAG-A BEIGE
(10), 1 de BAG-B NOIR (6). Total 4.

**Cajas en PARIS** (mezcla `MISMO_PEDIDO`): BAG-A NOIR 11 → 2 cajas (6+5);
BAG-A BEIGE 10 → 1 caja; BAG-B NOIR 6 → 1 caja. Total 4.
BAG-A NOIR y BEIGE **no se mezclan**: puras son 2+1 = 3 cajas y mezcladas
`ceil(21/10)` = 3, empate, y por **D10** se dejan puras. BAG-B no entra en el
grupo por **D9**: su cartón es otro.

**Palets en CHINA**: altura útil = 158 − 11 = 147 cm. Las 4 cajas (tres de 40 cm,
una de 30) caben en un palet, una por pila.

## 11. Plan de pruebas

Se sigue la costumbre del proyecto: JUnit 5 puro con `new`, y `@SpringBootTest`
solo donde hace falta cableado real.

| Test | Qué ancla |
|---|---|
| `TallerColisExcelTest` | El `.xlsx` real: hoja, fila de cabecera 9, columnas, 15 filas de datos, corte por 5 vacías |
| `TallerColisExcelCasosRarosTest` | Fixtures sintéticos: hoja ausente, columna obligatoria ausente, cabecera combinada, sinónimos |
| `ObjetivosPedidoAmiTest` | Contra `EAN PUNTOTRES H26.xlsx` real: `Commandé`, sufijos CH/JP, suma por destinación |
| `ObjetivosPedidoApcTest` | Contra `APC_PEDIDO_FALL26.xlsx` real: `CODE` → pedido → destinación → cantidad |
| `RepartoDestinacionesTest` | Prioridades, faltantes, empates, sobrantes |
| `AgrupadorCajasTest` | D9, D10, reparto equitativo (31/10 = 8,8,8,7), las tres reglas de mezcla |
| `ApiladorPaletsTest` | 4 pilas, altura útil, palet nuevo, caja demasiado alta |
| `GeneradorPackingTallerTest` | El ejemplo de §10, extremo a extremo, sobre el `EnvioInput` resultante |
| `MemoriaCajaReferenciaTest` | Cascada de resolución y escritura solo de entradas válidas |
| `TaraServiceTest` | Siembra desde el yml, orden por volumen, edición |
| `PackingListTallerControllerTest` | `@SpringBootTest` + MockMvc: 1a → 1b → generar → `/revision`, y la vuelta atrás sin resubir |
| `RutasTest` | Las rutas nuevas responden |

Los tests existentes que tocan taras siguen pasando: la siembra desde
`TaraProperties` mantiene el comportamiento.

## 12. Fuera de alcance

- No se cambia la pantalla de revisión ni ningún builder de Excel.
- No se toca el modo CLAUDE ni el prompt de extracción.
- No se guarda historial de envíos: la memoria de referencias guarda cartón y
  unidades por caja, nada más.
- El contador del Livraison code de APC sigue arrancando en 1 y editándose en la
  revisión, como hoy.
