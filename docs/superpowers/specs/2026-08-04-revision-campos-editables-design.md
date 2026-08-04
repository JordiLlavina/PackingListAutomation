# Pantalla de revisión: todos los campos editables

Fecha: 2026-08-04

## Problema

En la pantalla de revisión del asistente de packing list (`/revision`) solo son
editables los dos pesos. El resto de la tabla —referencia, color, pedido,
número de caja, tamaño, cantidad, palet— se pinta con `th:text` y no se puede
tocar: un dato mal leído obliga a volver al Paso 1 y rehacer el JSON entero.

Eso duele especialmente con el modo CLAUDE de la pantalla de entrada, donde los
datos salen de fotos: la extracción puede leer mal cualquier campo, no solo un
peso. La revisión es justo la pantalla que existe para corregir antes de
generar, así que debe poder corregirlo todo.

Aparte, los `input[type=number]` muestran las flechitas de incremento del
navegador, que estorban y no aportan nada en una tabla de teclear.

## Alcance

1. Los diez campos de la tabla de revisión pasan a ser editables (los nueve de
   hoy más **Talla**, que hoy no es ni columna).
2. Una fila compactada (`4-8`) puede **desplegarse** en sus cajas individuales
   con un triángulo, para editar el número de caja o cualquier campo de una sola
   de ellas.
3. Desaparecen las flechitas de los inputs numéricos en toda la aplicación.

Fuera de alcance: la pantalla de entrada, el volcado ERP, las etiquetas y los
builders de Excel. Esto es una feature de la capa `web/` y de los `CajaData` que
ya viven en la sesión.

## Decisiones tomadas

| Decisión | Elección | Motivo |
|---|---|---|
| Columna CAJA en filas compactadas | No editable; se despliega la fila | Editar "4-8" no tiene un significado único |
| Quién gestiona desplegar/plegar | El servidor (estado en sesión + submit) | Al plegar, el agrupador decide solo si las cajas siguen siendo equivalentes; ningún valor se pisa en silencio |
| Campo vaciado | "No tocar" (regla actual) | Se conserva la semántica de hoy. Consecuencia asumida: se puede sobrescribir cualquier campo, pero no dejarlo en blanco |
| Columnas nuevas | Solo Talla | Es SKU propio en cinturones AMI (cada talla tiene su EAN-13) y parte grupos en la compactación; hoy no hay forma de corregirla. Modelo, canal y livraison-code se quedan fuera |
| Palets tras editar | No se re-ejecuta `PaletAssignmentService` | Sus rangos son los del JSON original y machacarían el palet tecleado a mano |

## Diseño

### Campos editables

| Columna | Control | Ámbito de la edición |
|---|---|---|
| Caja | `number` min=1, **solo en filas de una sola caja** | esa línea |
| Referencia | `text` | todas las cajas de la fila |
| Color | `text` | todas las cajas de la fila |
| Pedido | `text` | todas las cajas de la fila |
| Talla | `text` | todas las cajas de la fila |
| Tamaño | `text` + `datalist` de tamaños con tara conocida | todas las cajas de la fila |
| Cantidad | `number` min=0 | todas las cajas de la fila |
| Palet | `number` min=1 | todas las cajas de la fila |
| Bruto (kg) | `number` step=0.01 min=0, **solo en la línea líder** | la caja física entera |
| Neto (kg) | `number` step=0.01 min=0, **solo en la línea líder** | la caja física entera |

Dos reglas del proyecto que no cambian:

- **Un peso por caja física, en su línea líder.** Bruto y Neto siguen
  apareciendo solo en la primera línea de cada bulto.
- **Una fila compactada aplica lo tecleado a todas sus cajas.** Ya es así para
  los pesos (`indicesCaja` es una lista); ahora vale para todos los campos.

Lo nuevo es que las líneas **no líderas** de una caja mixta, que hoy salen
mudas, sí editan referencia, color, pedido, talla, tamaño, cantidad, palet y
número de caja: esos datos son de la línea, no del bulto. Como una caja mixta
nunca se compacta (`esDeUnaSolaLinea`), sus filas siempre representan una sola
caja y por tanto su columna CAJA siempre es editable.

El `datalist` de Tamaño no es adorno: la tara sale de `packing-list.taras` en
`application.yml` y es lo que permite derivar el neto del bruto. Un tamaño sin
tara deja la caja sin inferencia posible. El controlador ya monta ese conjunto
para el modo FORMULARIO (`taraProperties.getTaras().keySet()`); basta con
añadirlo también al modelo de `/revision`.

### Desplegar y plegar una fila compactada

Estado en sesión, no en el DOM:

- `EnvioEnCurso` guarda un `Set<ClaveFila>`, donde `ClaveFila` es un record
  `(int destino, int indice)` y `indice` es la **posición de la primera caja del
  grupo dentro de su destinación**. Posicional, como todo en esta pantalla:
  nunca por número de caja, que puede repetirse en cajas mixtas. `reiniciar()`
  lo vacía.
- `AgrupadorFilasRevision.agrupar(cajas, primerIndiceGlobal, indicesDesplegados)`:
  cuando iba a formar un grupo de más de una caja cuyo índice de inicio está en
  el set, emite **una fila por caja** en vez de la compactada. Se mantiene la
  sobrecarga de dos argumentos delegando con `Set.of()`, para no tocar los tests
  existentes.
- `FilaCaja` gana tres campos: `indiceInicioGrupo` (posición de la primera caja
  del grupo al que pertenece la fila), `agrupable` (el grupo tiene más de una
  caja) y `desplegada`.
- Un grupo solo se forma con cajas de una sola línea, así que al desplegarlo
  **cada fila resultante es la líder de su propia caja física**: todas muestran
  sus dos pesos y su propio `cajaPendiente`.
- Render en la celda CAJA: `▶` en la fila compactada (`agrupable && !desplegada`),
  `▼` en la primera fila de un grupo desplegado
  (`desplegada && indiceInicioGrupo == su propio índice`), nada en las demás
  filas del grupo ni en las filas de una sola caja.
- `POST /alternar-fila?destino=N&indice=M`: aplica primero las ediciones del
  formulario (mismo camino que `/recalcular`), luego conmuta la clave en el set,
  y redirige a `/revision`. Al ser un submit del mismo formulario, lo ya
  tecleado no se pierde al desplegar.

Consecuencia buscada: si despliegas un `4-8`, cambias el color de la caja 6 y
vuelves a plegar, `sonEquivalentes` devuelve `false` y las filas **no se
reagrupan**. No existe un valor de color que la fila plegada pudiera mostrar sin
mentir, y la regla del agrupador —*una fila compactada nunca muestra un valor
que no sea cierto para todas sus cajas*— se cumple sin código extra.

### Formulario y aplicación de las ediciones

- `RevisionForm.PesoEditado` pasa a llamarse **`CajaEditada`** y los inputs
  pasan de `pesos[i].*` a `cajas[i].*`: ya no son solo pesos. Se actualizan las
  llamadas de `PackingListControllerTest`.
- Campos del DTO: `indiceDestino`, `indicesCaja` (lista, sin cambios),
  `numeroCaja`, `numeroPedido`, `referencia`, `codigoColor`, `talla`,
  `tamanoCaja`, `cantidad`, `numeroPalet`, `pesoBrutoKg`, `pesoNetoKg`.
- `numeroCaja`, `cantidad` y `numeroPalet` viajan como `Integer` aunque
  `CajaData` tenga los dos primeros como primitivos: es la única forma de
  distinguir "vacío" de "cero".
- `aplicarPesosYReinferir` pasa a `aplicarEdicionesYReinferir` y aplica cada
  campo **solo si viene relleno** (`null` para los numéricos, en blanco para los
  de texto). Es la regla de hoy extendida a todos los campos.
- Después de aplicar y re-inferir, se **recalcula la lista de cajas sin palet**
  leyendo qué `CajaData` tienen `numeroPalet` nulo, para que el aviso deje de
  quedarse obsoleto en cuanto se teclea un palet a mano. `avisosPalets` (los de
  los rangos del JSON) se dejan como están: hablan de la importación.

### Estilo

- Flechitas fuera, global en `estilo.css`:

  ```css
  input[type="number"] { appearance: textfield; -moz-appearance: textfield; }
  input[type="number"]::-webkit-outer-spin-button,
  input[type="number"]::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
  ```

  Global, así también desaparecen en el modo FORMULARIO de la pantalla de
  entrada.
- La tabla pasa de 9 a 10 columnas casi todas con `input`. Anchos por columna
  vía clase (referencia ~9rem, color ~7rem, pedido ~7rem, talla ~4rem, tamaño
  ~8rem, cantidad ~5rem, palet ~4rem, caja ~4.5rem, pesos 6.5rem) y
  `overflow-x: auto` en `section.destino` para que en pantalla estrecha la tabla
  desplace en horizontal en vez de romper la página. El `input[type=text]` global
  es `width: 100%`, así que los de la tabla necesitan su propia regla
  (`section.destino td input`).
- El JS de restauración del scroll pasa de `button[formaction$="/recalcular"]` a
  un selector con `*=`, porque `/alternar-fila` lleva query params.

## Tests

`AgrupadorFilasRevisionTest`:

- Un grupo cuyo índice de inicio está desplegado emite una fila por caja, cada
  una con su propio `indicesEnDestino` de un solo elemento.
- Las filas de un grupo desplegado llevan `indiceInicioGrupo` del grupo y solo
  la primera es la que pinta el `▼`.
- Un grupo desplegado cuyas cajas dejan de ser equivalentes no se reagrupa al
  quitar la marca.
- La sobrecarga de dos argumentos sigue comportándose igual que antes.

`PackingListControllerTest`:

- Editar referencia, color, pedido, talla y cantidad por posición cambia la
  `CajaData` de la sesión.
- Una edición en fila compactada llega a las cinco cajas del tramo.
- Un campo enviado vacío no borra el valor que ya había.
- Editar el tamaño cambia la tara aplicada y por tanto el neto inferido.
  **Cuidado**: con una tara conocida la inferencia rellena sola el resto de la
  referencia y enmascara el fallo; el test parte de un tamaño **sin** tara y
  comprueba que al corregirlo aparece el neto que faltaba.
- Editar el palet a mano quita esa caja de la lista de "sin palet".
- `POST /alternar-fila` conmuta el estado, conserva lo tecleado en el mismo
  submit y redirige a `/revision`.

## Documentación a actualizar

`CLAUDE.md`, párrafo de la tabla de revisión: hoy dice que se editan los pesos.
Debe decir que se edita todo, que una fila compactada se puede desplegar, y que
el estado de desplegado vive en la sesión.
