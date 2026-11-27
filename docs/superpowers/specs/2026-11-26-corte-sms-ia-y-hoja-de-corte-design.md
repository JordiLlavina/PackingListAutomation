# Corte de SMS con IA, novedades al sistema y Hoja de corte — diseño

Fecha: 2026-11-26. Tres piezas que se construyen en este orden; cada una se puede
entregar sola.

## Por qué

- Las hojas de corte de SMS solo funcionan con APC (`CorteSmsService.CON_LECTOR`);
  el resto de clientes sale «en desarrollo» y sus muestras se cortan a mano. Cada
  cliente manda su pedido de SMS a su manera (excel o PDF) y escribir un lector por
  cliente no compensa: se lee con Claude, como ya se hace con el excel del taller.
- Las SMS traen casi siempre modelos, pieles y colores que el catálogo no tiene, y
  hoy no se guarda nada. El usuario quiere verlos al terminar y meterlos en el
  sistema con un botón, uno a uno o todos.
- Falta una forma de sacar la orden de corte de **un modelo** sin pasar por un
  excel de pedido o de producción: elegirlo del catálogo (o escribirlo desde cero si
  no existe), retocar lo que haga falta e imprimir.

## Decisiones del usuario (2026-11-26)

1. **APC sigue directo**: entrada → descarga, como hoy. Solo los clientes leídos con
   IA pasan por una pantalla de revisión.
2. **Una Hoja de corte es un modelo con varios colores**, como la Hoja de Producción:
   el modelo arriba y una línea por color («+ Añadir color»). Sale un excel con una
   ficha por color.
3. **Pieles de la Hoja de corte: las de siempre** — 1 piel principal, 1 forro y
   hasta 4 combinaciones, cada una con su color. La ficha del excel no cambia.
4. **La Hoja de corte guarda histórico**, como las Hojas de Producción: verlas,
   bajarlas otra vez e importarlas al formulario.
5. Petición original: alerta si el documento no es del cliente elegido; zona de
   novedades editables «debajo de todo» con «Entrar en el Sistema» por elemento y
   «Entrar todo en el Sistema»; Hoja de corte como **tercer botón** junto a
   Producción y SMS, con desplegables de cliente, temporada y artículo **como en
   `/info-embalaje/formulario`**; también desde cero.

## Pieza 1 — Corte de SMS con IA para los clientes sin lector

### Flujo

`/documentos-corte/sms` (entrada) → **`/documentos-corte/sms/revision`** (solo IA) →
`/documentos-corte/sms/resultados` (descarga + novedades).

- En la entrada **ningún cliente sale apagado**. APC usa su lector (`ApcSmsExcel`) y
  va directo a resultados. Cualquier otro cliente (AMI incluido, que hoy no tiene
  lector de SMS) usa la IA. El campo admite `.xlsx` y `.pdf` para los de IA; para
  APC sigue siendo `.xlsx`. La ayuda de la pantalla dice qué camino toma el cliente
  elegido.
- Sin `ANTHROPIC_API_KEY` la pantalla arranca igual y el camino IA da un error claro
  al enviar (el cliente HTTP perezoso de los otros servicios).

### Lectura con Claude (`LectorSmsIa`, en `service/corte/`)

Mismo patrón que `ConversionFormatoTallerService`: modelo `claude-opus-5-5`,
streaming, salida estructurada con esquema JSON, esfuerzo HIGH, tiempo máximo
generoso. **Claude lee y separa; no consulta el catálogo ni inventa**: lo que no
viene en el documento va vacío.

- **Excel**: se vuelca a texto hoja a hoja, una línea por fila con su número de fila
  (se extrae el volcado de `ConversionFormatoTallerService` a una clase compartida
  en vez de copiarlo). **PDF**: se manda como bloque de documento.
- El prompt recibe el **nombre y la clave del cliente elegido** y explica qué es una
  hoja de corte de muestras: una línea por referencia + color con muestras que
  cortar, la piel (el material del proveedor) y su color, el forro, las
  combinaciones. Lo que no es del corte (cremalleras, logos, metales, tintes) no
  sale.
- Esquema de respuesta:
  - `cliente`: `{ nombreDetectado, esDelCliente (true/false/null), motivo }` — de
    quién parece el documento y por qué (logo, cabecera, formato de referencias).
  - `esPedidoDeSms`: true/false + motivo (para avisar si han subido otra cosa).
  - `lineas[]`: `hoja`, `fila` (del excel; null en PDF), `referencia`, `modelo`
    (código del modelo si el documento lo separa; si no, vacío), `nombreModelo`,
    `codigoColor`, `descripcionColor`, `piel`, `colorPiel`, `forro`, `colorForro`,
    `combinaciones[] {nombre, color}` (máx. 4), `muestras` (entero o null),
    `comentario`.
  - `avisos[]`: dudas de lectura, citando la fila o la página.
- El código del modelo, si Claude no lo da, sale de la referencia con la regla del
  cliente (`ClienteCorte.partir`; un genérico no la parte: referencia = modelo).
- Una línea sin muestras o con 0 se trae igual, **desmarcada** (se ve y se decide).

### Alerta de documento de otro cliente

Si `esDelCliente` es `false`, o `esPedidoDeSms` es `false`, la pantalla de revisión
lleva arriba una **alerta a la vista** (no en los avisos plegables): «Este documento
parece de \<nombreDetectado\>, no de \<cliente elegido\>: \<motivo\>». **No bloquea**
(regla del proyecto): se puede volver a la entrada o seguir. Con `null` (no se sabe),
nada.

### Pantalla de revisión

Una tabla editable, una fila por línea leída: casilla de incluir, referencia, nombre
del bolso, código y descripción del color, muestras, piel + color, forro + color, y
las combinaciones (desplegadas bajo un «+ combinaciones» para que la tabla no sea
interminable). Se pueden **añadir y quitar filas**. Los avisos de Claude, plegables
debajo de la alerta. «Generar hojas de corte» genera con lo que haya en pantalla; una
fila incluida sin referencia o sin muestras vuelve a la revisión con su error
marcado. La revisión vive en la sesión (`CorteSmsEnCurso`).

### Imagen de cada ficha

Por orden: las imágenes ancladas en **la fila** del excel que dio Claude (cualquier
columna, con el recorte `srcRect` aplicado como en `ApcSmsExcel.recortar`; se
reutiliza ese código); la de otra fila del mismo modelo; la foto principal del modelo
en el catálogo; ninguna, con el aviso de siempre. En PDF no se extraen imágenes.

### Generación

Las fichas se construyen igual que hoy (`OrdenCorte` con `temporada | SMS`, el color
del bolso en la piel principal) y salen en el mismo excel
`<clave> <temporada> - Hojas de Corte.xlsx`. Sin redondita de color en los de IA (no
hay elipse que leer).

## Pieza 2 — Novedades: entrar en el sistema (todos los clientes)

Al pie de la pantalla de **resultados** del SMS (APC y los de IA), una sección
«Nuevos en el sistema» con tres listas. Solo sale si hay algo nuevo. Se calcula con
lo que se ha generado (las líneas validadas, no lo que leyó la IA antes de
corregirlo).

| Lista | Qué es nuevo | Campos editables | «Entrar en el Sistema» escribe |
|---|---|---|---|
| **Modelos** | Código de modelo que no está en el catálogo del cliente; o un modelo que existe con una **referencia** (modelo + piel) que no tiene, rotulado «nueva piel de \<modelo\>» | nombre del bolso (**obligatorio**, como en la carga de modelos), tipo (bolso/cinturón), y por referencia: forro y combinaciones | Modelo nuevo con `Modelos.darDeAlta` en etapa **Muestras** (la de SMS en la carga de modelos), sin temporada; sus artículos con `asegurarArticulo`; forro, combinaciones y los colores de cada piel por color del bolso (`articulo_color`); el croquis como foto del modelo si es nuevo |
| **Pieles** | Ref. de piel (`PXCBC`, `AL0103`) sin fila en `piel`, o con fila pero sin nombre | nombre de la piel | `MemoriaPieles.guardarPiel` (en mayúsculas, como `/colores`) |
| **Colores** | Código de color sin fila en `color_cliente`, o con fila sin descripción | descripción | `TablaColores.guardar` |

- Cada elemento tiene su «Entrar en el Sistema» (por `fetch`, sin recargar, como las
  filas de `/colores`); al entrar se pone en verde «en el sistema» y deja de
  contar. «Entrar todo en el Sistema» entra todos los pendientes de las tres listas
  en una transacción; un modelo sin nombre se marca en rojo y **no entra ninguno**
  (el «todo o nada» de la carga de modelos).
- **Lo que ya está en el catálogo no se pisa nunca**: aquí solo se dan de alta cosas
  nuevas y se rellenan huecos.
- Los clientes genéricos no parten la referencia, así que **no tienen lista de
  pieles** (no hay ref. de piel donde guardarla) y sus colores sin código tampoco
  van a `color_cliente`: entran como parte de su modelo (`articulo_color` por
  descripción, como ya hace el catálogo).
- La regla de `/temporadas` de no tocar el catálogo sin revisión se respeta: aquí
  nada entra sin pulsar un botón.

## Pieza 3 — Hoja de corte (tercer botón)

### Dónde

`/documentos-corte` gana una tercera tarjeta **«Hoja de corte»** junto a Producción
y SMS. Pantallas: `/documentos-corte/hoja` (formulario) y
`/documentos-corte/hoja/antiguas` (histórico). Todo en una página y sin sesión, como
`/hojas-produccion`.

### El formulario

- **Arriba**: cliente, temporada guardada (`paraElDesplegableDelCorte`, todas las
  del cliente) y **artículo** con el desplegable tabulado de la info de embalaje
  (`OpcionesArticuloEmbalaje.alinear`, letra de ancho fijo), cargado por `fetch`
  del catálogo (`ArticulosProduccion.de(cliente, temporadaId)`; se reutiliza el
  endpoint `GET /hojas-produccion/catalogo` o se saca a uno común). Un botón
  **«Empezar desde cero»** vacía el formulario para un modelo que no está en el
  sistema.
- **Cabecera** (editable): referencia, modelo (lo que sale de título en la ficha),
  nombre del bolso, temporada (texto que va en la ficha) y foto (la principal del
  catálogo o una subida, HEIC incluido, como en la Hoja de Producción).
- **Una línea por color** («+ Añadir color», quitar): código y descripción del
  color, cantidad, piel principal + color, forro + color, y hasta 4 combinaciones +
  color.
- **Autorrelleno al elegir el artículo**: referencia, modelo, nombre del bolso, foto
  y la línea de su color con lo que el catálogo sabe (piel, forro, combinaciones y
  sus colores de `articulo_color`). Los desplegables de piel/color de las demás
  líneas se llenan con las pieles del modelo (`CatalogoProduccion.PielesModelo`),
  como en la Hoja de Producción. Lo tecleado a mano manda.

### Lo que sale

Un excel `<clave> <temporada> - <modelo> - Hoja de Corte.xlsx` con **una ficha por
línea de color** hecha con `OrdenCorteExcelBuilder` (la misma ficha que producción
y SMS), subtítulo `<cliente> | <temporada>`, la foto de la cabecera en todas. Se
genera por `fetch` y se descarga sin recargar. Una hoja sin cliente, sin modelo o
sin ninguna línea con cantidad es un 400 y no guarda nada.

### Histórico

Tabla nueva `hoja_corte` (migración `V18`): cliente, temporada, modelo, fecha,
foto subida (bytes, opcional) y el **snapshot entero en JSON**, el patrón de
`hoja_produccion`. La pantalla de antiguas lista (cliente, temporada, modelo, fecha,
colores) por una proyección sin fotos, y cada una se puede **bajar otra vez**
(regenerar con lo guardado) o **importar al formulario** (`?importar=<id>`).
**Sin numeración**: la Hoja de Producción numera por taller porque se manda fuera;
la de corte se queda en casa.

**No escribe en el catálogo**: lo que se teclea en la Hoja de corte se queda en la
hoja. Un modelo nuevo entra por la carga de modelos.

## No-objetivos

- No se escriben lectores de SMS por cliente: los que no son APC van por IA.
- APC no cambia de flujo ni de lector.
- La ficha del excel no cambia (nada de varios forros).
- La Hoja de corte no guarda en el catálogo ni numera.
- No se extraen imágenes de los PDF.
- La temporada del SMS sigue sin guardarse.

## Anti-objetivos

- Que la IA invente un dato que no está en el documento (una piel o un color
  «probable»): acaba en la mesa del cortador.
- Que «Entrar en el Sistema» pise algo que alguien ya tecleó en el catálogo.
- Que la alerta de otro cliente bloquee: a veces el documento es de una marca del
  grupo y está bien.

## Lo que se deja al que lo construya

- Partir la pantalla de revisión en fragmento o JS propio; cómo pintar las
  combinaciones plegadas.
- Reutilizar `CargadorModelos` para entrar los modelos o llamar directamente a
  `Modelos.darDeAlta` + `CatalogoModelos`; lo que importa es que se cumplan sus
  reglas (nombre obligatorio, alta antes que artículos, una transacción).
- Si la pantalla de resultados del SMS se pinta en el servidor o con un script.
- Tests: `LectorSmsIa` con la respuesta de Claude simulada (sin red), el cálculo de
  novedades contra una base en memoria, el controlador con MockMvc y el builder de
  la Hoja de corte reabriendo el excel con POI. `HojaCorteTest` deja
  `target/Hoja de corte ejemplo.xlsx` para revisarla a ojo.
- Documentar en `.claude/rules/corte.md` (SMS y Hoja de corte) y una línea en la
  tabla de CLAUDE.md si la Hoja de corte estrena su fichero de reglas.
