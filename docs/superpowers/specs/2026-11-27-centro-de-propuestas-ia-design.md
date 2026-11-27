# Centro de propuestas de la IA — diseño

Fecha: 2026-11-27.
Estado: **diseño, sin implementar**.
Alcance de este documento: el **núcleo** de propuestas, el **primer tipo** (ficha de
piel) y los **dos primeros canales** (soltar un fichero en la app y reenviar un email).
Lo que viene después (más tipos, la orden a Bachir, el MCP) se describe al final solo
para que lo de ahora no lo estorbe.

Cada decisión va marcada: **(usuario)** lo decidió Jordi en la conversación de diseño;
**(asistente)** lo propuso el asistente y Jordi no lo ha discutido: se puede cambiar.

## 0. Dónde se quedó (para seguir en otra sesión)

A 2026-11-27. Este apartado se borra cuando el diseño esté aprobado y haya plan.

**Hecho.** El diseño está escrito y lo leyó un revisor independiente, que encontró
seis huecos: cinco están cerrados en este documento como decisiones **(asistente)** y
el sexto es el material real de más abajo. Está escrita también la guía para el
informático: `docs/Propuestas IA/buzon de propuestas y Microsoft - guia para el
informatico.md`. **No hay plan de implementación ni una línea de código.**

**Siguiente paso.** Jordi lee este documento y lo aprueba o dice qué cambia. Con su
aprobación, y no antes, se escribe el plan en `docs/superpowers/plans/` y se construye.

**Lo que hace falta de Jordi**

1. **Aprobar el diseño**, o corregirlo.
2. **Material real**: tres o cuatro fichas técnicas de piel de proveedores distintos y
   un par de emails reenviados de verdad (§11). De ellos sale además en qué unidades
   vienen los precios (por pie², por m², por kilo, por piel), que decide si la regla
   de conversión de §10.2 vale tal cual.
3. **El buzón**: pasar la guía al informático. Mientras no exista, el canal de correo
   se construye contra un buzón falso; lo demás no depende de él.

**Las decisiones del asistente que más cambian el resultado** (todas discutibles):

- Una propuesta es **una cosa que dar de alta**, no un email: un PDF con seis pieles
  da seis propuestas bajo su entrada, con tope de 30 (§6.2).
- Piel que ya existe: solo se rellenan huecos; los precios se sustituyen únicamente
  si se marca (§10.3).
- Solo se convierte superficie a superficie (pie² y dm² a m²). Otra moneda, o precio
  por kilo o por piel: casilla vacía, aviso y el texto a comentarios (§10.2).
- El proveedor nunca se elige solo: si no coincide exacto, se propone como nuevo con
  los parecidos a la vista (§10.2).
- Fotos: las adjuntas y las de dentro del PDF, todas sin marcar (§10.4).
- Excel y Word adjuntos se guardan pero no se leen en esta fase (§6.4).
- Sin IA también funciona: se elige el tipo y se rellena a mano (§6.4).
- Si no se puede separar con seguridad lo que escribió quien reenvía, no se busca
  palabra clave y clasifica la IA (§7.2).
- Palabras clave de la piel: `piel`, `ficha de piel`, `ficha tecnica de piel` (§10).
- Hay que añadir la edición del **correo** de cada usuario (servidor y pestaña
  «Usuarios» de `/app`): hoy la columna existe pero no se puede escribir, y el filtro
  de remitentes depende de ella (§8).

**Hablado en la conversación y que no está en otro sitio**

- **Dónde corre el «agente de Outlook»**: es una tarea programada dentro del mismo jar,
  en el servidor de la empresa, que sale a preguntar a Microsoft y llama a la API de
  Anthropic. No es un agente en la nube (no llegaría a la app, que solo vive en la red
  local) ni una sesión de Claude en un PC.
- **Se paga por uso, no con la suscripción.** Estimación sin medir: unos 25.000 tokens
  de entrada y 3.000 de salida por email con uno o dos PDF, alrededor de 0,15 $ con
  `claude-opus-5-5` (supone ~2.500 tokens por página de PDF). Se mide de verdad con la
  herramienta `…RealTool` de §11 antes de fiarse del número. Las imágenes que solo se
  guardan no se mandan al modelo y no cuestan.
- **La guía del informático tiene dos cosas provisionales**: los comandos de Exchange
  están escritos de memoria (que los valide él antes de ejecutarlos) y la dirección
  `ia@puntotres.com` y los nombres `BUZON_*` son provisionales.
- **MCP**: que solo funcione desde un PC de la red local (Claude Desktop o Claude Code)
  está por comprobar cuando llegue su fase.
- **Aceptar una propuesta contestando al email** se descartó de momento (decisión del
  usuario); también ahorra gasto de API.

## 1. Para qué

Meter datos en el sistema hoy es abrir la pantalla, teclear y adjuntar. Se quiere que
baste con **reenviar un email** (desde el móvil, desde fuera o desde la oficina) o
**soltar un fichero** en la app: la IA lee, propone, y una persona acepta.

Ejemplos que dio el usuario: reenviar la ficha técnica de una piel; reenviar el email
de un prototipo con su ficha técnica; mandar una modificación a Bachir.

Éxito: reenviar un email con la ficha técnica de una piel y, un par de minutos
después, encontrar en la pantalla de propuestas la ficha ya rellena, con el PDF
guardado, a falta de un clic.

## 2. Objetivos, lo que no entra y lo que sería un fracaso

**Objetivos**

- Un **centro de propuestas** genérico: cualquier cosa que la IA quiera meter en el
  sistema pasa por ahí, sea del tipo que sea **(usuario)**.
- Dos canales que acaban en el mismo sitio: **fichero soltado** y **email reenviado**
  **(usuario)**. El MCP será el tercero, más adelante.
- El tipo de tarea lo **decide la IA**, con una **palabra o frase clave opcional**
  que manda sobre ella **(usuario)**.
- Un primer tipo completo de punta a punta: **ficha de piel**.
- Añadir un tipo nuevo después es escribir ese tipo, no tocar el núcleo ni los canales.

**No entra en esta fase**

- Los tipos prototipo, modelo nuevo y orden a Bachir (fases siguientes, §12).
- El MCP para sesiones de Claude (§12).
- Login: la pantalla de propuestas es abierta, como el resto de la app del menú
  **(usuario: «de momento dejamos el login aparte… luego haremos lo de Microsoft»)**.
- Aceptar una propuesta respondiendo al email **(usuario: descartado por ahora)**.
- Alias de correo por función **(usuario: descartado; una sola dirección)**.
- Soltar un `.eml` o `.msg` en la app; ficheros que no sean PDF o imagen se guardan
  pero no se leen (§6.4) **(asistente)**.

**Sería un fracaso aunque funcione**

- Que algo se escriba en el catálogo, en la biblioteca de pieles o llegue a Bachir
  **sin que una persona lo haya aceptado**. Es la regla que hace aceptable todo lo
  demás (remitente suplantado, email con instrucciones escondidas, lectura equivocada).
- Que una entrada se pierda en silencio: un email que falla al leerse tiene que
  quedar a la vista como error.
- Que la IA «encaje a la fuerza»: inventar un proveedor que casi coincide, convertir
  unidades por su cuenta o pisar un precio que ya estaba.
- Que la bandeja se convierta en un segundo montón que nadie mira.

## 3. Lo que ya existe y se reutiliza

- **El patrón «Claude lee y separa; Java decide; la persona confirma»** de
  `ConversionFormatoTallerService` (ver `.claude/rules/taller.md`): salida
  estructurada con JSON Schema, cruces contra los datos reales en Java, avisos por
  campo. Aquí se sigue igual.
- **La biblioteca de pieles** (`service/pieles/BibliotecaPieles`, ver
  `.claude/rules/biblioteca-pieles.md`) ya tiene todo lo que hace falta para escribir:
  `crear`, `guardar`, `guardarPrecios`, `guardarDocumento`, `guardarFoto`, `apuntar`,
  `asegurarProveedor`, y la columna `origen` con el valor `AGENTE` reservado.
  Ojo: dentro de `BibliotecaPieles` ya hay un record `Propuesta` (la propuesta de
  enlace piel de cliente ↔ ficha). No tiene nada que ver con esto; conviene que la
  entidad nueva no se confunda con él al importar.
- **La transcripción de notas de voz** (`modelos/service/transcripcion/`) es el
  modelo de trabajo en segundo plano: evento tras el commit, un solo hilo, cada paso
  en su transacción, reencolar lo pendiente al arrancar, y «accesorio»: sin clave
  arranca apagado y se dice.
- **`@EnableScheduling`** ya está activo (`Recordatorios`, `ImagenesDePdf`).
- **`usuario.correo`** existe en la tabla de usuarios de la app de modelos.
- **El conversor de fotos del corte** (`ReductorImagen`, HEIC incluido, 1.600 px).

## 4. Enfoques considerados

Tres preguntas tuvieron alternativas reales.

**4.1 Dónde corre la lectura del correo**

| Enfoque | A favor | En contra |
|---|---|---|
| **Tarea programada dentro del mismo jar** que sondea el buzón por Microsoft Graph | Nada nuevo que desplegar; el servidor ya está siempre encendido; solo tráfico de salida | Hay que escribir el cliente de Graph |
| Agente en la nube (tareas programadas de Claude, Managed Agents) | Sin código de sondeo | No puede llegar a la app, que solo vive en la red local |
| Claude Code sin ventana en el servidor, con la suscripción | Sin coste por uso | Frágil (la sesión caduca sin avisar), comparte límites con el uso humano, y no está claro que los términos lo permitan |

**Elegido: el primero (usuario, tras la explicación).** La IA se paga por uso con la
clave de API que el servidor ya tiene.

**4.2 Cómo se sabe qué tarea es**

| Enfoque | A favor | En contra |
|---|---|---|
| Un alias de correo por función (`pieles@`, `prototipos@`…) | No falla nunca | Una dirección más por cada función nueva; no sirve para el fichero soltado |
| **Una dirección; la IA clasifica; palabra clave opcional manda** | Una dirección para siempre; el mismo camino que el fichero soltado | La IA puede clasificar mal: se corrige cambiando el tipo en la propuesta |

**Elegido: el segundo (usuario).**

**4.3 Dónde vive la pantalla**

| Enfoque | A favor | En contra |
|---|---|---|
| Solo en `/app` (PWA), con una tarjeta en el menú | Login, roles y push desde el primer día | El usuario la quiere en la app del menú |
| **Pantalla principal en la app del menú, sin login; en `/app` solo las propuestas de modelos** | Donde el usuario la quiere; coherente con la biblioteca de pieles, que es abierta | Cualquiera en la red puede aceptar; no se sabe quién aceptó hasta que haya login |

**Elegido: el segundo (usuario).** En esta fase solo existe el tipo «ficha de piel»,
que es de la app del menú, así que **`/app` no recibe nada todavía**: su vista llega
con el primer tipo de modelos (§12).

## 5. Vista general

```
  fichero soltado ─┐
                   ├─▶ ENTRADA (texto + documentos, guardados tal cual)
  email reenviado ─┘        │
                            ▼   hilo «propuestas», fuera de la petición
                     ¿palabra clave? ── sí ──▶ tipo
                            │ no
                            ▼
                     Claude CLASIFICA ──▶ tipo, o «sin clasificar»
                            │
                            ▼
                     Claude EXTRAE con el esquema de ese tipo
                            │
                            ▼
                     Java CASA con lo que ya existe y añade avisos
                            │
                            ▼
                     PROPUESTA pendiente ──▶ pantalla /propuestas
                                              │
                               persona corrige y ACEPTA / DESCARTA
                                              │
                                              ▼
                               el tipo ESCRIBE en su sitio (biblioteca de pieles…)
```

Tres piezas, separadas para que crecer sea añadir y no tocar:

- **Canales**: convierten lo que llega en una *entrada*. No saben de tipos.
- **Núcleo**: guarda entradas y propuestas, las procesa en segundo plano, ofrece la
  pantalla. No sabe de pieles ni de modelos.
- **Tipos**: cada uno sabe qué extraer, cómo casar con lo existente, cómo se revisa y
  qué hace «aceptar».

## 6. El núcleo

### 6.1 Paquete

Paquete nuevo y neutral `com.puntotres.packinglist.propuestas` **(asistente)**, hermano
de `modelos` y de `service`. Puede usar los servicios de la biblioteca de pieles y,
más adelante, los de `modelos.service` (el sentido que la regla del paquete `modelos`
permite; el contrario sigue prohibido: nada de `modelos.*` importa de aquí).

### 6.2 Datos (migración `V19`)

SQL en el subconjunto común a H2 y PostgreSQL, binarios `bytea` sin `@Lob`, columnas
planas sin `@ManyToOne` (convenciones de `.claude/rules/app-modelos.md`).

- **`entrada_propuesta`** — lo que llegó, tal cual: `id`, `canal` (`FICHERO`, `CORREO`,
  y más adelante `CLAUDE`), `id_mensaje` (el identificador del email; **único**, nulo
  en los demás canales: es lo que impide procesar dos veces el mismo correo),
  `solicitante_nombre`, `solicitante_correo`, `asunto`, `texto` (el cuerpo del email o
  lo escrito al soltar el fichero), `instruccion` (lo que la persona escribió encima
  del reenvío, §7.2), `tipo_pedido` (el que fijó la palabra clave o el desplegable; nulo
  si decide la IA), `recibida`, `estado` (`PENDIENTE`, `LEYENDO`, `LEIDA`, `ERROR`),
  `error`, `intentos`.
- **`documento_entrada`** — sus adjuntos: `id`, `entrada_id`, `nombre`, `mime`,
  `tamano`, `contenido`. Se guardan **todos**, se lean o no: son el original.
- **`propuesta`** — `id`, `entrada_id`, `tipo` (clave del tipo; **nula = sin
  clasificar**), `estado` (`PENDIENTE`, `ACEPTADA`, `DESCARTADA`), `titulo` (una línea
  para la lista: «Piel ALPACA TOUCH de CONCERIA TOSCANA»), `datos` (texto JSON: lo
  extraído, y encima lo que la persona corrija), `avisos` (texto JSON), `resultado`
  (a dónde fue al aceptarla, p. ej. `ficha_piel:12`, para enlazar), `creada`,
  `resuelta_en`, `resuelta_por` (nula mientras no haya login; la columna queda puesta
  para entonces), `motivo_descarte`, `tokens_entrada`, `tokens_salida`.

- **`propuesta_documento`** — qué documentos de la entrada son de cada propuesta:
  `propuesta_id`, `documento_id`. Un documento puede servir a varias propuestas (una
  lista de precios con varias pieles).

**La unidad de una propuesta es UNA cosa que se da de alta** (una piel, un prototipo),
no un email ni un fichero **(asistente)**. Una entrada puede dar **varias propuestas**
—un email con dos fichas técnicas, o un solo PDF que lista seis pieles— y la lista de
la pantalla las enseña juntas, bajo su entrada. Puede no dar ninguna útil: entonces da
**una sin clasificar**, para que no se pierda. Tope: **30 propuestas por entrada**
**(asistente)**; si el documento trae más (un catálogo entero), se crean las 30
primeras y un aviso de la entrada dice cuántas se han quedado fuera.

Nada de esto se borra al aceptar o descartar: es el historial. Borrar una propuesta
descartada a mano sí se permite desde la pantalla **(asistente)**.

### 6.3 El contrato de un tipo

Cada tipo es un bean que implementa una interfaz del núcleo (nombre a elegir por
quien lo construya) y declara:

- **clave** estable (`FICHA_PIEL`) y **nombre** para la pantalla («Ficha de piel»);
- **palabras clave** que lo fijan sin preguntar a la IA (`piel`, `ficha de piel`…);
- **descripción para el clasificador**: una o dos frases de cuándo es este tipo;
- **esquema de extracción** (JSON Schema) e **instrucciones** para el modelo. La
  extracción devuelve siempre una **lista** de elementos, cada uno con los documentos
  de los que sale: es el tipo, y no el clasificador, quien separa las seis pieles de
  una lista de precios;
- **datos vacíos**: el formulario en blanco de ese tipo, para rellenarlo a mano
  cuando no hay IA o la persona elige el tipo de una entrada sin clasificar y la
  extracción no da nada (§6.4);
- **casar**: recibe lo extraído y devuelve los datos de la propuesta más sus avisos,
  mirando lo que ya existe en el sistema. Aquí vive todo lo que el modelo tiene
  prohibido;
- **plantilla de revisión**: el fragmento de pantalla con sus campos editables;
- **aceptar**: recibe los datos ya corregidos por la persona y escribe. **Todo o
  nada**, en una transacción; devuelve el `resultado`. Si lo que hay ya no permite
  aceptar (alguien creó esa piel entretanto), devuelve un error legible y la propuesta
  sigue pendiente.

El núcleo descubre los tipos por inyección (`List<…>`). Lección de
`.claude/rules/taller.md`: un tipo sin su anotación de bean compila, pasa sus tests
unitarios y **no existe** en la aplicación; tiene que haber un test por el contenedor
real que compruebe que los tipos esperados están registrados.

### 6.4 Procesado en segundo plano

Igual que la transcripción: guardar la entrada publica un evento; un oyente
`AFTER_COMMIT` la encola en **un solo hilo** (`propuestas`); cada paso va en su
transacción y nunca hay una abierta mientras se espera a la red; al arrancar se
reencolan las que quedaron `PENDIENTE` o `LEYENDO`.

Pasos:

1. **Tipo**: si la entrada trae `tipo_pedido`, es ese. Si no, **clasificar** con Claude:
   recibe el texto, los documentos y la lista de tipos (clave + descripción) y
   devuelve una lista de `{tipo, documentos a los que se refiere}`; puede devolver
   «ninguno». El clasificador solo reparte los documentos entre tipos.
2. **Extraer**, una vez por tipo, con su esquema y sus instrucciones, sobre los
   documentos que le tocan (todos, si el tipo venía fijado). Devuelve la lista de
   elementos: tres PDF soltados como «Ficha de piel» son tres propuestas, y un PDF
   con seis pieles, seis.
3. **Casar** en Java, elemento a elemento.
4. Guardar una propuesta `PENDIENTE` por elemento y marcar la entrada `LEIDA`.

Lo que se le manda al modelo: el texto, los **PDF** como documento y las **imágenes**
como imagen. Cualquier otro adjunto (excel, word, zip) **no se lee** en esta fase: se
guarda, se enseña en la propuesta para descargarlo y se avisa de que no se ha leído
**(asistente)**.

Un fallo de la IA o de la red se reintenta hasta **3 veces** en vueltas sucesivas
**(asistente)**; después la entrada queda en `ERROR` con el motivo y **sale en la
pantalla**, con un botón de reintentar. Sin `ANTHROPIC_API_KEY`, las entradas se
guardan igual y quedan como «sin leer (IA no configurada)». **Aceptar sin IA es un
camino válido** **(asistente)**: al elegir el tipo a mano, la propuesta nace con los
«datos vacíos» del tipo y los documentos al lado, y se rellena tecleando. Con IA,
«Cambiar tipo» vuelve a extraer.

Modelo: `claude-opus-5-5`, salida estructurada, razonamiento adaptativo, como el resto
del programa. Dos notas para quien lo construya:

- La API limita a 16 los campos con unión de tipos por esquema (`.claude/rules/taller.md`):
  en los esquemas, solo los números admiten `null`; un texto que falta es `""`.
- La clasificación y la extracción mandan los mismos documentos: hay que evitar
  pagarlos dos veces (caché de prompt con los documentos delante y las instrucciones
  del tipo detrás, o lo que resulte al medirlo). Con `tipo_pedido` fijado no hay
  clasificación. Se deja a quien lo construya, **midiéndolo**: se guardan los tokens
  de cada propuesta precisamente para eso.

### 6.5 Límites

Cada adjunto, hasta **25 MB**, mirado con `getSize()` antes de `getBytes()` (el
multipart admite 1 GB), como en la biblioteca de pieles. Una entrada, hasta **10
adjuntos** **(asistente)**. Un PDF de más de 100 páginas no se manda al modelo: se
guarda y se avisa **(asistente)**. Las imágenes de menos de 300 px de lado se ignoran
(logos de firma), el mismo umbral que `ImagenesDePdf`.

## 7. Los canales

### 7.1 Soltar un fichero

En la pantalla `/propuestas`, arriba: una zona para soltar o elegir uno o varios
ficheros, un desplegable **«Tipo»** con «Que lo decida la IA» por defecto y los tipos
registrados, y una caja opcional «¿Algo que deba saber?» (texto libre que va a
`instruccion`). Al enviar, la entrada aparece en la lista como «Leyendo…» y la página
la refresca sola hasta que pasa a propuesta o a error (sondeo, sin WebSockets, como el
resto del programa).

### 7.2 Email reenviado

**Una sola dirección (usuario).** Lo que hace falta de Microsoft está en
`docs/Propuestas IA/buzon de propuestas y Microsoft - guia para el informatico.md`:
buzón compartido que solo acepta correo interno, registro de aplicación limitado a ese
buzón, y cuatro variables (`BUZON_TENANT_ID`, `BUZON_CLIENT_ID`, `BUZON_CLIENT_SECRET`,
`BUZON_DIRECCION`).

Una tarea `@Scheduled` cada **60 s** (configurable) **(asistente)**:

1. Pide a Microsoft Graph los correos de la bandeja de entrada del buzón.
2. Por cada uno, comprueba el **remitente** (§8). Si no pasa: se mueve a la carpeta
   `Rechazados`, se apunta una línea en el log y **no se contesta ni se llama a la IA**.
3. Si pasa: guarda la entrada con sus adjuntos en una transacción y **después** mueve
   el correo a `Procesados`. Si el programa cae entre las dos cosas, en la vuelta
   siguiente el `id_mensaje` único evita la segunda entrada y solo se mueve.
4. El procesado sigue por el hilo de §6.4.
5. Al terminar, **contesta al remitente** con un email corto: qué se ha entendido
   (tipo y título de cada propuesta, y los avisos que piden revisar) y que está
   pendiente de aceptar en la pantalla de propuestas. Si falló, lo dice. No lleva
   enlace: desde fuera de la oficina no abriría. La respuesta **no gasta IA**: sale de
   lo ya extraído.

**La palabra o frase clave (usuario):** es lo que la persona escribe **encima** del
reenvío. Si su **primera línea no vacía**, sin mayúsculas ni acentos ni puntuación,
es una palabra clave de un tipo, fija el tipo; el resto de lo escrito va a
`instruccion` y se le pasa al modelo como indicación de quien reenvía («para AMI,
temporada SS27»). Si no coincide con ninguna, todo lo escrito es `instruccion` y
clasifica la IA. Graph ofrece la parte del mensaje que es propia de este correo
(`uniqueBody`) separada del hilo reenviado; hay que **comprobarlo con un reenvío real
desde Outlook de escritorio y desde el móvil** antes de fiarse **(asistente)**. Si no
la separa, se corta el cuerpo en la primera cabecera de reenvío reconocible (`De:` /
`From:` seguido de `Enviado:` / `Sent:`, «Mensaje original», «Forwarded message»). Y
si tampoco se reconoce ninguna, **no hay instrucción**: todo el cuerpo es material a
leer, no se busca palabra clave y clasifica la IA. Es el fallo seguro: lo que no se
sabe que escribió la persona nunca se trata como una indicación suya.

Cliente de Graph: REST con `RestClient` y el flujo *client credentials*, sin el SDK de
Microsoft, como se hizo con Azure Speech **(asistente)**. Detrás de una interfaz del
núcleo para poder probar el canal con un buzón falso.

**Accesorio**: sin las variables del buzón, el canal arranca apagado y la pantalla lo
dice en una línea («La entrada por correo no está configurada»). Un fallo al hablar
con Microsoft (secreto caducado, red) va al log y **a esa misma línea de la pantalla**,
con la hora del último sondeo bueno: es la única forma de enterarse de que el secreto
ha caducado. En `application-test.yml`, apagado.

## 8. Seguridad

- **Nada se escribe sin aceptar.** Es la defensa principal: un remitente suplantado o
  un PDF con instrucciones escondidas solo consiguen una propuesta rara en una lista.
- **Remitentes permitidos (usuario: «emails permitidos, me parece bien»).** Dos
  cerrojos: Exchange rechaza lo que no venga de una cuenta autenticada de la empresa
  (lo pone el informático), y el programa solo acepta un remitente cuyo correo sea el
  `correo` de un **usuario activo con rol DESPACHO o ADMIN** de la app de modelos
  (comparado sin mayúsculas). Consecuencia: quien vaya a reenviar tiene que estar dado
  de alta como usuario y con su correo puesto. **Hoy el correo no se puede poner**: la
  columna existe pero `Usuario` no tiene setter, ni la API ni la pestaña «Usuarios» de
  `/app` lo enseñan. Añadirlo (servidor y PWA) **es parte de esta fase**.
- **El modelo no tiene herramientas.** Solo devuelve JSON con la forma del esquema. El
  texto de los emails y de los PDF se le presenta como material a leer, y las
  instrucciones del sistema dicen que nada de lo que haya dentro es una orden.
- **Solo se contesta al remitente ya validado**, nunca a otra dirección que aparezca
  en el correo.
- **Descargas**: los documentos de una entrada se sirven como en la biblioteca de
  pieles: solo el PDF y las imágenes en línea, lo demás como adjunto binario con
  `nosniff` (un HTML servido en línea sería una página de este sitio).
- **Sin login**, cualquiera en la red local puede aceptar o descartar. Aceptado por
  el usuario para esta fase. Por eso mismo, **ningún tipo de esta fase dispara nada
  fuera del programa** (la orden a Bachir, que sí lo hace, se acepta en `/app`: §12).
- **Privacidad**: lo reenviado sale hacia la API de Anthropic, como ya ocurre con las
  fotos de las hojas del almacén. Solo lo que alguien reenvía a propósito.

## 9. La pantalla

Ruta `/propuestas` en la app del menú (Thymeleaf y `fetch` con JSON, como
`/biblioteca-pieles`), con su **tarjeta en el menú**, que enseña **cuántas hay
pendientes**: es lo que evita el montón olvidado.

**Lista**

```
 Propuestas                                   [ Pendientes 3 ] [ Aceptadas ] [ Descartadas ] [ Todas ]

 ┌ Suelta aquí un PDF o una foto, o elige ficheros ─────────────────────────────┐
 │  Tipo: [ Que lo decida la IA ▾ ]   ¿Algo que deba saber? [________________]  │
 └──────────────────────────────────────────────────────────────────────────────┘
 La entrada por correo funciona · último sondeo 10:42

 ● Ficha de piel   ALPACA TOUCH · CONCERIA TOSCANA      ✉ Jordi · hoy 10:31    2 avisos
 ● Ficha de piel   NAPPA SOFT · proveedor nuevo: RUSSO  ⬆ fichero · hoy 09:58
 ○ Sin clasificar  «Fwd: listino prezzi 2027»           ✉ Marta · ayer 17:12
 ✕ Error al leer   «Fwd: scheda tecnica»                ✉ Jordi · ayer 16:40   [Reintentar]
 … Leyendo         catalogo-pelle.pdf                   ⬆ fichero · ahora
```

**Detalle** (`/propuestas/{id}`): a la izquierda, lo que llegó (el texto del email y
cada documento, el PDF visible en la propia página); a la derecha, el formulario del
tipo, **ya relleno y editable**, con cada aviso junto a su campo. Abajo: **Aceptar**,
**Descartar** (motivo opcional) y **Cambiar tipo**, que vuelve a extraer con el tipo
elegido (es también como se resuelve una «sin clasificar»). Una propuesta aceptada
enseña un enlace a lo que creó.

Los avisos de una propuesta siguen la regla de la casa: van **en su campo**, y solo
los que piden revisar se destacan.

## 10. El primer tipo: ficha de piel

Palabras clave: `piel`, `ficha de piel`, `ficha tecnica de piel` **(asistente; el
usuario puede dar las suyas)**.

### 10.1 Qué extrae el modelo

Del documento y del texto, **tal como viene escrito**: proveedor, nombre de la piel,
animal y tipo (eligiendo de las listas cerradas del yml, que se le dan; si no encaja
ninguno, vacío), grueso y superficie con **su unidad**, los tramos de precio con **su
unidad y su moneda** y el mínimo de cada uno con su unidad, si el mínimo es por color
(`moq_por_color`), la fecha de la oferta, si hay stock service, el plazo, comentarios,
y —si quien reenvía lo dice— para qué cliente se ha buscado. Más los datos de
contacto del proveedor que aparezcan (contacto, email, teléfono, país).

### 10.2 Lo que el modelo tiene prohibido y hace Java

- **Convertir unidades.** Las pieles se venden muchas veces por pie cuadrado y la
  biblioteca guarda siempre **€/m²** y m². El modelo copia número y unidad; Java
  convierte (1 m² = 10,7639 ft²; y dm² a m²) y deja un aviso con la cuenta en ese
  campo. **Solo se convierte superficie a superficie.** Una moneda que no sea el euro,
  un precio por kilo o por piel, o un mínimo en pieles o en kilos **no se convierten**:
  la casilla se queda vacía con un aviso que dice lo que ponía, y lo escrito va además
  a los comentarios para que no se pierda **(asistente)**. Qué unidades traen de verdad
  las fichas se sabrá con el material real (§11); si casi todas vienen por piel o por
  kilo, esta regla hay que volver a hablarla con el usuario.
- **Decidir qué proveedor es.** Java normaliza el nombre como la biblioteca
  (mayúsculas, espacios a uno). Si coincide **exacto**, es ese. Si no, la propuesta
  dice «proveedor nuevo» y enseña los que se parecen para elegir uno
  (`CONCERIA TOSCANA SRL` → ¿`CONCERIA TOSCANA`?). **Nunca se elige solo**: no hay
  fusión de fichas y un proveedor duplicado se queda.
- **Decidir si la piel ya existe.** Proveedor + nombre exactos = ya existe, y la
  propuesta pasa a ser una **actualización** (§10.3).
- **Validar** grueso y superficie con `Rango` y animal y tipo contra las listas: lo
  que no pasa se queda en su casilla tal cual, marcado, para que la persona lo arregle.

### 10.3 Crear o actualizar

- **Piel nueva**: el formulario con todo lo extraído.
- **Piel que ya existe**: cada campo enseña **lo que hay** y **lo que trae el
  documento**. Por defecto solo se rellenan los **huecos**; un campo que ya tenía
  valor **no se pisa** salvo que la persona lo marque **(asistente)**. Los tramos de
  precio van aparte y como un bloque: guardar precios los **sustituye enteros** y no
  hay histórico, así que solo se cambian si se marca «sustituir los precios», con los
  viejos y los nuevos a la vista.

### 10.4 Aceptar

En una transacción: asegura el proveedor (creándolo solo si la persona dejó «nuevo»),
crea o actualiza la ficha, guarda los precios si toca, guarda **los documentos de la
propuesta** (el PDF original) como documentos de la piel, guarda como fotos las
**imágenes marcadas** y, si se dijo para qué cliente, apunta el paso «Buscada» en su
recorrido con la fecha de hoy.

- **`origen`** dice cómo **nació** la ficha **(asistente)**: una creada aquí lleva
  `AGENTE`; una que ya existía y se actualiza **conserva el suyo**. Hoy
  `BibliotecaPieles.guardar` no toca `origen` (solo existe `FichaPiel.setOrigen`), así
  que la creación desde una propuesta necesita su propia entrada en el servicio.
- **Los datos de contacto del proveedor** van a `proveedor`, que comparten todas sus
  pieles, y por eso **solo rellenan huecos** **(asistente)**: salen en el formulario
  en su propio bloque, editables, y un dato que el proveedor ya tenía no se pisa.
- **Las fotos candidatas** son las imágenes adjuntas **y las incrustadas en el PDF**
  **(asistente)**: la foto de la piel suele ir dentro de la ficha técnica. Se sacan
  con `ImagenesDePdf` (solo las imágenes incrustadas de al menos 300 px; las páginas
  pintadas no, que una página de ficha no es una foto de la piel). Ojo: `ImagenesDePdf`
  guarda sus candidatas media hora en disco, así que aquí se extraen **al abrir el
  detalle**, como hace la carga de modelos, y lo marcado se copia al aceptar.
- Todas llegan **sin marcar** **(asistente)**: un email arrastra logos y firmas, y la
  regla de la casa es que una foto dudosa no entra sin elegirla.

## 11. Pruebas

- Los tipos y el casado, con `new` y sin Spring, contra una biblioteca de pieles con
  sus propias claves (la base es la misma para toda la suite).
- El modelo y el buzón, detrás de interfaces con dobles: ningún test llama a la API ni
  a Microsoft.
- Un test por el contenedor: los tipos están registrados; la ruta `/propuestas` y sus
  estáticos responden.
- Migración `V19` probada sobre una base llevada hasta la `V18`, con el patrón de
  `MigracionTemporadaDosExcelsTest`.
- Una herramienta que **sí** gasta API (`…RealTool`, como
  `ConversionFormatoTallerRealTool`), que lee fichas técnicas reales, deja el JSON en
  `target/` e imprime segundos y tokens: es con lo que se afinan el prompt y el coste.
- **Hace falta material real del usuario**: tres o cuatro fichas técnicas de piel de
  proveedores distintos y un par de emails reenviados de verdad. Sin ellos el prompt
  se escribe a ciegas.
- **El canal de correo se construye y se prueba contra un buzón falso** mientras el
  informático no entregue el de verdad; el criterio de éxito de §1 solo se puede
  comprobar cuando exista. El núcleo, el tipo y el fichero soltado no dependen de él.

## 12. Lo que viene después (no es de esta fase)

En el orden acordado con el usuario:

1. **Prototipo y modelo nuevo.** Extraen cliente, nombre, temporada, piel, la ficha
   técnica y las imágenes candidatas; aceptar pasa por las reglas de `Modelos.darDeAlta`
   y por las candidatas de `ImagenesDePdf`. Con ellos llega la **vista de propuestas
   en `/app`** (pestaña del despacho, solo las de tipos de modelos) **(usuario)**.
2. **Orden a Bachir.** Dos formas **(usuario)**: la propuesta que el despacho acepta
   en `/app` añadiendo la nota de voz y eligiendo el puesto en la cola; y la **directa**,
   que se envía sin pasar por la bandeja. Condiciones de la directa **(asistente)**:
   solo con su frase clave explícita (nunca por clasificación de la IA), con el texto
   de la orden literal de quien la manda, con el modelo identificado sin duda por
   Java, y entrando al final de la cola, nunca como urgente.
3. **MCP para sesiones de Claude.** Herramientas de buscar y de proponer; al confirmar
   la persona en la conversación, la propuesta **se acepta** **(usuario)** y queda en
   el historial marcada como venida de Claude y a nombre del usuario configurado en
   ese PC. Solo funciona desde un PC de la red local.
4. **Login** para la app del menú, con las cuentas de Microsoft: entonces
   `resuelta_por` empieza a rellenarse.

Lo que este diseño deja preparado para eso: el canal `CLAUDE`, la columna
`resuelta_por`, el contrato de tipo (§6.3) y que una entrada pueda dar varias
propuestas enlazadas.

## 13. Lo que se deja a quien lo construya

- Los nombres de clases, de la interfaz de tipo y de las rutas del JSON.
- La forma exacta de los esquemas y los prompts (con las prohibiciones de §10.2).
- Cómo no pagar dos veces los documentos entre clasificar y extraer (§6.4).
- La maquetación de la pantalla, sobre los estilos que ya usa `/biblioteca-pieles`.
- El texto del email de respuesta.

## 14. Documentación al terminar

Un fichero nuevo `.claude/rules/propuestas.md` con sus `paths:` y su línea en la tabla
de `CLAUDE.md`; una línea en `.claude/rules/biblioteca-pieles.md` diciendo que `origen
= AGENTE` ya se usa; y las variables `BUZON_*` en el README.
