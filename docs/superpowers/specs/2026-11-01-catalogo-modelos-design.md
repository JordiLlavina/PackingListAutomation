# Catálogo de modelos: una memoria única por cliente — diseño

Fecha: 2026-11-01. Sustituye las cuatro memorias sueltas de hoy
(`memoria_caja_referencia`, `memoria_piel`, `memoria_pieles_articulo`,
`memoria_colores_piel`) por un catálogo de modelos enlazado, del que leen
todas las features. Relacionado con
[2026-09-29-documentos-del-corte-design.md](2026-09-29-documentos-del-corte-design.md)
y [2026-10-08-colores-corte-design.md](2026-10-08-colores-corte-design.md).

Estado: **implementado** (2026-11-02, rama `catalogo-modelos`, plan en
`docs/superpowers/plans/2026-11-02-catalogo-modelos.md`). Un desvío respecto de
lo escrito abajo: un excel de temporada que **no se deja leer guarda la temporada
y avisa**, en vez de rechazarla, por la regla del proyecto de no bloquear por
datos que un humano puede resolver.

## Qué pidió el usuario

Que toda la información de todos los modelos de todos los clientes se guarde
en la base de datos, en varias tablas enlazadas entre sí para no repetir datos,
y que las features que hoy sacan información "de memoria" la saquen de ahí. Lo
que necesita el excel de pedido del cliente (EAN, PO, cantidades, destinaciones)
sigue saliendo del excel: eso no cambia.

Además, una pantalla nueva donde se vea en **una tabla** todo lo de un cliente,
con un filtro de columnas moderno (no una fila de checkboxes) para poder ir a
cambiar un dato concreto —"los bolsos por caja"— sin ruido.

## Decisiones tomadas con el usuario

| Pregunta | Decisión |
|---|---|
| ¿Un solo nivel "modelo"? | **No.** Dos niveles: el **modelo** (`ULL027`, la forma del bolso) y el **artículo** = modelo + piel (`ULL027.AL0103`, la referencia que escribe el cliente). Las fotos y el nombre son del modelo; el cartón, las unidades por caja, el peso y las pieles son del artículo. |
| ¿Cartón y unidades por caja en modelo o en artículo? | **En artículo.** El usuario no puede garantizar que dos pieles del mismo modelo lleven siempre las mismas unidades. Para no teclearlo por cada piel: herencia al nacer y propagación al editar (ver "Comportamiento"). |
| ¿El forro y las combinaciones son pieles dadas de alta? | **No, texto libre.** Los forros no tienen referencia de piel, solo nombre y color, y darlos de alta sería más trabajo. Se normalizan al guardar y se autocompletan con los ya tecleados del cliente. |
| ¿Tabla hija para las combinaciones? | **No.** Cuatro columnas `combinacion1..4`, como hoy. |
| ¿Temporada es solo un nombre? | **No.** Es la tabla `temporada_guardada` que ya existe, con su excel de pedido. El excel pasa a ser **opcional**. |
| ¿Relación modelo ↔ temporada? | **N:M**, tabla `modelo_temporada`. |
| ¿Cómo entran los modelos al catálogo? | **Por los dos caminos**: a mano desde la pantalla nueva, y **solos al guardar una temporada nueva** con excel, con un aviso emergente que diga que se han guardado los artículos de la temporada. |
| ¿El cartón depende de la destinación? | **No.** Una caja a China lleva las mismas unidades que a cualquier otra destinación. Que dos pieles no compartan caja lo decide `TipoMezcla` del yml, no el catálogo. |
| ¿El `tipo` bolso/cinturón del catálogo gobierna el packing? | **No, es informativo.** La regla del prefijo `UBL` (`CajaData.esCinturon`) sigue mandando en el packing (plantilla de cinturones de AMI), en la separación de cajas y en el corte. La pantalla lo dice junto al campo. Que gobierne será otra feature. |
| `AL245` (excel de producción) y `AL0245` (pedido) son la misma piel de AMI. ¿Se normaliza? | **No.** No se puede garantizar que las referencias de AMI sean siempre dos letras + cifras, así que no hay regla segura: si llegan las dos formas, **son dos pieles y dos artículos** en el catálogo. En el futuro el excel de producción se generará desde el de pedido y el duplicado desaparece solo. |
| ¿Sobre qué rama y qué Flyway? | **Sobre `main`.** `envios-dhl` no se va a fusionar; `login-microsoft` entrará en el futuro y renumerará sus migraciones detrás de las de aquí. |
| ¿Hay que migrar los datos de las memorias actuales? | **No.** La app no está desplegada todavía: la base de `./datos` se borra y se crea con el esquema nuevo. Lo que sí vale como principio de aquí en adelante es que **nada de lo que se recuerda se pierda**. |
| ¿De dónde salen las temporadas y los modelos de un cliente genérico? | **De importar el excel de producción de Puntotres**, igual que los de AMI y APC salen del excel de pedido. |

## Supuestos (míos, no corregidos por el usuario)

- El **nombre del bolso** se rellena de donde venga (`Désignation` de APC, la
  `Ref. Puntotres` del excel de producción) y se puede corregir a mano. Lo que ya
  está tecleado no se pisa al volver a cargar un excel.
- Las fotos se guardan **dentro de la base de datos**, reducidas a 1.600 px como
  ya hace el corte, en su propia tabla para que listar modelos no cargue los bytes.
- **Flyway** entra en el proyecto con esta feature, con la disposición de la rama
  `envios-dhl` (`db/migration/V1__...sql`, `ddl-auto: validate`) pero con su propia V1.
- Los **clientes siguen en `application.yml`**; en las tablas son una columna de texto,
  como hoy.
- **Colores sin código** (el bicolor de AMI `AMARILLO CLARO/ GRIS GUIJARRO`, `ORO` a
  secas en APC, todos los de un genérico): la clave de `articulo_color` es el código o,
  si no hay, la **descripción entera**, que es como ya los identifica el corte. Por eso
  `articulo_color` **no lleva FK a `color_cliente`**: esas descripciones no deben
  aparecer en `/colores`.
- **Referencias que la regla del cliente no sabe partir** (las parciales de APC que
  llegan del taller sin completar, `F63023`): se guardan como artículo con la
  referencia tal cual, modelo = la referencia y `ref_piel` vacío, y los artículos sin
  piel **quedan fuera** de la herencia y la propagación entre hermanos. Nada de lo que
  el taller recuerda se pierde.
- El **excel de producción no escribe el nombre de la piel** (ahí es texto
  concatenado, `SERRAJE ALABAMA`): lo lee del catálogo, y escribe forro, combinaciones
  y colores de piel como el camino del pedido.
- `medida_caja` admite `null`: una fila con forro y sin cartón es legal.

## Modelo de datos

Clave de todo: `cliente` (texto, la clave del yml) + código. Claves naturales
compuestas como el resto de `persistence/`, salvo fotos y temporadas, que ya tienen
id. Las claves se normalizan como hoy (`trim` + mayúsculas).

```
piel                               color_cliente                 temporada_guardada
(cliente, ref_piel)                (cliente, codigo)             (id) cliente, temporada,
nombre                             descripcion                   tipo_excel, nombre_fichero, excel
   ▲                               (sin FK desde articulo_color)      ▲
   │                                                                  │
   │     modelo ─────────────────────────────── modelo_temporada ─────┘
   │     (cliente, codigo)                      (cliente, codigo_modelo, temporada_id)
   │     nombre, tipo [informativo]
   │        ▲        ▲
   │        │        └── foto_modelo
   │        │            (id) cliente, codigo_modelo, nombre_fichero,
   │        │            principal, orden, bytes
   │        │
   └── articulo
       (cliente, referencia)
       codigo_modelo → modelo
       ref_piel → piel [null si la regla no da piel]
       forro, combinacion1..4 (texto)
       medida_caja, unidades_por_caja,
       peso_neto_kg
            ▲
            │
       articulo_color
       (cliente, referencia, codigo_color)   codigo_color = código, o la descripción si no hay
       color_piel, color_forro, color_combinacion1..4
```

### Tablas

**`piel`** (hoy `memoria_piel`, se renombra): `cliente`, `ref_piel` (PK),
`nombre`, `fecha_actualizacion`. Es del material: la misma piel se llama igual en
todos los bolsos.

**`color_cliente`**: igual que hoy. `cliente`, `codigo` (PK), `descripcion`.

**`temporada_guardada`**: igual que hoy más una columna **`tipo_excel`**
(`PEDIDO` / `PRODUCCION`). El excel sigue siendo obligatorio: para un cliente con
`pedido-cliente: true` (AMI, APC) es el **excel de pedido del cliente**, como hoy; para
un cliente genérico es el **excel de producción de Puntotres**, que es de donde salen
sus modelos. Todo lo que hoy lee "el excel de la temporada" como pedido
(`ArchivoTemporadas.paraElEnvio`, los desplegables del packing list, las etiquetas,
`/colores`) **solo debe ofrecer las de tipo `PEDIDO`**; las de `PRODUCCION` salen en el
corte (que ya sabe leer ese excel) y en el catálogo.

**`modelo`** (nueva): `cliente`, `codigo` (PK, `ULL027`, `F67008`), `nombre`
(texto, puede estar vacío), `tipo` (`BOLSO` / `CINTURON`, **informativo**: no lo lee
ningún generador, manda la regla `UBL`), `fecha_actualizacion`.

**`modelo_temporada`** (nueva): `cliente`, `codigo_modelo`, `temporada_id` (PK),
FK a `modelo` y a `temporada_guardada`. Borrar una temporada borra sus filas aquí y
nada más. Borrar un modelo borra sus filas aquí, sus artículos, sus fotos y sus
colores de artículo.

**`foto_modelo`** (nueva): `id` (PK generada), `cliente`, `codigo_modelo` (FK),
`nombre_fichero`, `principal` (booleano; como mucho una por modelo, lo garantiza el
servicio y no una restricción de la base de datos), `orden`, `bytes` (`@Lob`, JPEG
reducido a 1.600 px), `fecha_actualizacion`.

**`articulo`** (hoy `memoria_caja_referencia` + `memoria_pieles_articulo`, se
fusionan): `cliente`, `referencia` (PK, la referencia entera tal como la escribe el
cliente), `codigo_modelo` (FK a `modelo`, obligatorio), `ref_piel` (FK a `piel`, puede
ser `null` para un cliente cuya referencia no lleva piel), `forro`, `combinacion1..4`
(texto, vacío = no lleva), `medida_caja`, `unidades_por_caja` (`Integer`, `null` = no se
sabe, como hoy), `peso_neto_kg` (`Double`, `null` = no se ha pesado),
`fecha_actualizacion`.

**`articulo_color`** (hoy `memoria_colores_piel`, se renombra): `cliente`,
`referencia`, `codigo_color` (PK), FK a `articulo` **y no a `color_cliente`**,
`color_piel`, `color_forro`, `color_combinacion1..4`, `fecha_actualizacion`.
`codigo_color` es el código del color del bolso (`001`, `LZZ`) o, para un color que
no tiene código (bicolor de AMI, `ORO` de APC, todo genérico), su **descripción
entera**, que es como ya lo identifica el corte. Los colores de combinación van
**emparejados por posición** con `articulo.combinacionN`, como hoy.

### Qué NO se guarda aquí, a propósito

EAN13, EAN128, PO / Document d'achat, cantidades pedidas, destinaciones, tallas
pedidas, prensa. Son de una temporada o de una línea de producción y viven en el
excel de pedido. La prensa es un atributo de una línea, no del modelo.

### Cómo se parte una referencia en modelo y piel

Con la regla que ya existe por cliente: `ClienteCorte.partir` / `ReferenciaCorte`
(AMI: `ULL027.AL0103` → modelo `ULL027`, piel `AL0103`; APC: `PXCBC-F67008` → piel
`PXCBC`, modelo `F67008`; genérico: todo es modelo, sin piel). Esa regla **se mueve a
un sitio común** del que dependan el catálogo y el corte, para que no haya dos copias.
**No normaliza nada**: `AL245` y `AL0245` son dos pieles (decisión del usuario, ver
tabla). Una referencia que la regla no sabe partir en un cliente que sí tiene pieles
se guarda tal cual, con modelo = la referencia y sin piel.

## Comportamiento

### Herencia y propagación entre pieles de un modelo

- **Herencia al nacer**: un artículo nuevo de un modelo que ya tiene artículos
  arranca con `medida_caja` y `unidades_por_caja` copiados de sus hermanos **si
  todos los hermanos con dato dicen lo mismo**; si no coinciden, arranca vacío. El
  peso neto **no** se hereda (cambia con la piel).
- **Propagación al editar**: al cambiar `medida_caja` o `unidades_por_caja` de un
  artículo en la pantalla, el cambio se copia a los hermanos del mismo modelo que
  tenían **el mismo valor antiguo o estaban vacíos**, y no toca a los que tenían otro.
  Es la misma regla que el nombre de piel en la tabla del corte. La pantalla lo dice
  al guardar ("aplicado también a 3 pieles de este modelo").
- Lo mismo vale para `forro` y las combinaciones, que casi siempre son del modelo
  y no de la piel. El color de piel (`articulo_color`) **no** se propaga entre pieles:
  es de esa piel en ese color.
- Los **artículos sin piel** (`ref_piel` vacío: referencias que la regla no supo
  partir) no son hermanos de nadie: ni heredan ni reciben propagación. Un `F63023`
  parcial no debe arrastrar nada hacia `PXCBC-F63023` ni al revés.

### Alta automática al guardar una temporada

Al guardar en `/temporadas` una temporada con su excel, después de guardarla:

1. Se leen las líneas del excel con el lector que toca: el de **pedido** del cliente
   (`ClienteCorte` de AMI o APC, el mismo que usa el corte) o el de **producción**
   (`ProduccionPuntotresExcel`, para un genérico, con `ClienteCorteGenerico`). Las dos
   fuentes dan lo mismo hacia dentro: referencia, nombre del bolso si lo hay, y colores
   (código + descripción, o solo descripción).
2. Por cada referencia distinta: se parte en modelo + piel; se da de alta la **piel**
   si no existe (nombre vacío), el **modelo** si no existe (nombre el que traiga el
   excel, tipo por la regla del prefijo) y el **artículo** si no existe (hereda cartón
   y unidades de sus hermanos según la regla de arriba).
3. Por cada color de cada referencia: si el color tiene código, se da de alta en
   `color_cliente` si no existe (descripción la del excel si la trae, como hoy
   `TablaColores.incorporar`); y se crea la fila de **`articulo_color`** si no existe,
   con el color de la piel principal arrancando en la descripción del color del bolso
   (regla actual del corte).
4. Se vinculan todos los modelos del excel a la temporada en `modelo_temporada`.
5. **Lo que ya existía no se toca**: ni nombres, ni cartones, ni pesos, ni colores de
   piel. Solo se rellenan huecos (un nombre de modelo vacío con el del excel, una
   descripción de color vacía con la del excel).
6. Al volver a la pantalla, un **aviso emergente** (modal o toast, no un `alert()` del
   navegador) dice qué ha pasado: "Se han guardado los artículos de la temporada SS27:
   38 artículos (12 nuevos), 13 colores (2 nuevos)". Con un botón que lleva a la
   pantalla del catálogo filtrada por esa temporada.

Al **sustituir el excel** de una temporada que ya existía se repite lo mismo: entran
los artículos nuevos y los que ya estaban se vinculan. Nada se borra. Al **borrar**
una temporada solo desaparecen sus filas de `modelo_temporada`.

`/temporadas` pasa a ofrecer **todos los clientes** del catálogo, no solo los de
`pedido-cliente: true`; el rótulo del fichero cambia según el cliente ("Excel de pedido
del cliente" / "Excel de producción de Puntotres") y `tipo_excel` se fija por el
cliente, no lo elige el usuario. Un excel que el lector rechaza (no es del formato que
toca) no guarda la temporada y lo dice.

### Alta y edición a mano

Desde la pantalla del catálogo se pueden dar de alta un modelo (código, nombre,
tipo), un artículo (referencia; el modelo y la piel salen de partirla con la regla
del cliente y se crean si no existen; si la regla no sabe partirla, se piden a mano),
y subir fotos. Todo campo es editable en su celda. Borrar un artículo o un modelo
pide confirmación y dice qué se lleva (fotos, colores).

### Quién lee el catálogo

Las tres fachadas actuales se conservan **con su interfaz** y cambian solo por dentro,
para que los servicios que las usan no se enteren:

- `MemoriaReferencias` (`buscar`/`recordar` de cartón, unidades y peso) → lee y
  escribe `articulo`. Sigue siendo la entrada por taller quien la escribe al generar,
  con las mismas reglas (peso vacío no borra; sin tara no se guarda peso). Si la
  referencia no existe todavía como artículo, **se crea** (y su modelo y su piel).
- `MemoriaPieles` (nombres, forro, combinaciones, colores de piel) → lee y escribe
  `piel`, `articulo` y `articulo_color`.
- `TablaColores` → `color_cliente`, igual que hoy.

Y dos cambios de comportamiento que sí se ven:

- **Documentos del Corte** con el **excel de producción**, que hoy no toca memoria a
  propósito, pasa a **leer** el forro, las combinaciones y los colores de piel del
  catálogo (el excel de producción no trae forro) y a escribir como el camino del
  pedido. La CLAUDE.md dice hoy lo contrario; hay que actualizarla.
- **Las fotos del corte dejan de tirarse al acabar.** Al cargar una carpeta de fotos,
  cada foto reconocida de un modelo que no la tenga ya (mismo nombre de fichero) se
  guarda en `foto_modelo`, y la elegida como principal en la tabla de pieles pasa a ser
  la `principal` del modelo. Si el modelo ya tiene fotos y no se sube carpeta, la orden
  de corte y el Word de fotos **salen con las del catálogo**. El directorio temporal de
  la sesión sigue existiendo para la conversión HEIC y se borra igual.

### Normalización de textos libres

`forro`, `combinacionN`, `color_*` y `nombre` de piel y modelo se guardan con `trim`
y en mayúsculas (como hoy las claves), para que `badana` y `BADANA` sean lo mismo.
Los inputs de la pantalla autocompletan con los valores distintos ya guardados del
mismo cliente (`<datalist>` basta).

## Pantalla: Catálogo de modelos

Ruta `/catalogo`, panel nuevo en `/menu` junto a "Colores y Pieles de Cliente".
Controlador propio (`CatalogoController`). Las filas se guardan **una a una por
`fetch`** con respuesta JSON, como `/colores`: un formulario por fila recargaba la
página y se perdía lo tecleado en las demás.

### Cabecera (fija arriba, en este orden)

1. **Cliente**: desplegable, todos los del catálogo del yml.
2. **Buscar**: un campo de texto que filtra al teclear por referencia, código de
   modelo, nombre del bolso y nombre de piel.
3. **Temporada**: desplegable con las del cliente y "Todas" por defecto; filtra por
   `modelo_temporada`.
4. **Vistas**: botones segmentados, una activa: **Packaging**, **Corte**, **Fotos**,
   **Todo**. Cada una fija qué columnas se ven (abajo). Es el filtro de columnas
   principal: quien viene a cambiar los bolsos por caja pulsa Packaging y no ve nada
   más.
5. **Columnas activas como chips** debajo de las vistas, cada una con una × para
   quitarla, y un último chip **+ columna** que abre un desplegable con buscador para
   añadir cualquiera de las que faltan. Quitar o añadir una columna convierte la vista
   en "Personalizada". Checkboxes solo dentro de ese desplegable, nunca como fila
   encima de la tabla. La selección y la vista se recuerdan en `localStorage` por
   cliente.

Vistas:

| Vista | Columnas |
|---|---|
| Packaging | foto, referencia, nombre, tipo, cartón, unidades/caja, peso neto caja llena |
| Corte | foto, referencia, modelo, nombre, piel (nombre), forro, combinaciones 1-4 |
| Fotos | foto, modelo, nombre, nº de fotos, temporadas |
| Todo | todas |

### Tabla

**Una fila por artículo**, agrupadas por modelo: cada modelo es un `<tbody>` y sus
columnas (foto, código, nombre, tipo, temporadas, nº de fotos) van **una vez con
`rowspan`** sobre sus artículos, igual que la tabla de pieles del corte. Así se ve a la
vez `ULL027.AL0103` y `ULL027.AL0216` y se ve qué es del modelo y qué de la piel.
Orden por código de modelo y, dentro, por referencia.

Celdas editables en sitio (click → input, Enter o perder el foco guarda esa celda,
Esc cancela), con el guardado confirmado en la propia celda (breve resaltado), no con
un mensaje arriba. El cartón es un desplegable con las taras del catálogo ordenadas
por volumen y la opción "(sin tara)" si el valor no está, como en la revisión. El tipo
es un desplegable de dos valores. La propagación a hermanos se anuncia al lado de la
celda y se puede deshacer en ese mismo aviso.

Debajo de cada fila de artículo, un **desplegable** (`<details>`) con la **tabla de
colores del artículo**: una fila por `articulo_color`, columnas color del bolso
(código + descripción), color de la piel, color del forro, color de cada combinación.
Es una matriz y no cabe como columnas de la tabla principal. Un botón "+ color" añade
un código de `color_cliente` que aún no tenga.

Al desplegar la celda de la **foto** del modelo se abre su galería: miniaturas, la
principal marcada, botones para marcar otra como principal, subir (varias a la vez,
HEIC incluido con el conversor del corte) y borrar.

Pie de tabla: botones **+ modelo** y **+ artículo** que abren un formulario corto en
línea (no otra página).

### Estado vacío y errores

Sin cliente elegido: la tabla dice que elija uno. Cliente sin modelos: "Este cliente
no tiene modelos todavía. Guarda una temporada con su excel en Temporadas, o añade
un modelo aquí." Un guardado que falla deja la celda en rojo con el motivo al
lado, y el valor tecleado no se pierde.

### Lo que NO entra en esta pantalla

Colores del cliente (código → descripción) y nombres de las pieles: siguen en
`/colores`, que ya los edita. Temporadas: en `/temporadas`. Desde el catálogo hay
enlaces a las tres.

## Esquema y Flyway

Entra **Flyway** (`flyway-core` en el pom, `db/migration/`, `ddl-auto: validate`),
sobre `main`. **No hay migración de datos**: la app no está desplegada y la base de
`./datos` se borra y se crea de cero. Una sola migración, `V1__esquema_inicial.sql`,
con el esquema **final**: las tablas que se conservan tal cual (`tara_caja`,
`color_cliente`, `temporada_guardada` con `tipo_excel`), `piel`, y las cinco nuevas
(`modelo`, `modelo_temporada`, `foto_modelo`, `articulo`, `articulo_color`). Las cuatro
entidades de memoria actuales (`MemoriaCajaReferencia`, `MemoriaPiel`,
`MemoriaPielesArticulo`, `MemoriaColoresPiel`) desaparecen del código con sus
repositorios; las semillas de taras y colores siguen funcionando igual (se aplican si
la tabla está vacía).

Los tests siguen con H2 en memoria y perfil `test`; Flyway corre también ahí, así que
un desajuste entre entidad y SQL sale en el primer `@SpringBootTest`. Cuando entre la
rama `login-microsoft`, sus migraciones se renumeran detrás de esta V1.

De aquí en adelante vale el principio que el usuario ha dado: **nada de lo que se
recuerde se pierde**. Cualquier cambio de esquema posterior a que la app esté en uso
va con migración de datos.

## Piezas

- `persistence/`: entidades `Modelo`, `ModeloTemporada`, `FotoModelo`, `Articulo`,
  `ArticuloColor`, `Piel` (renombrado) con sus `Id` y repositorios; `MemoriaReferencias`,
  `MemoriaPieles` y `TablaColores` reescritas por dentro; servicio nuevo
  `CatalogoModelos` (alta desde el pedido, herencia, propagación, fotos).
- `service/corte/`: `ReferenciaCorte`/`ClienteCorte.partir` movidos a un sitio común
  (`model/` o `service/catalogo/`); `DocumentosCorteService` lee y guarda fotos y pieles
  del catálogo también con el excel de producción.
- `web/`: `CatalogoController`, plantilla `catalogo.html` + fragmentos, JS de la
  cabecera de filtros y la edición en celda; `TemporadasController.guardar` acepta
  todos los clientes, fija `tipo_excel`, llama al alta automática y deja el resumen en
  un atributo flash para el emergente; los desplegables de temporada del packing list,
  etiquetas y `/colores` filtran por `tipo_excel = PEDIDO`.
- `db/migration/V1__esquema_inicial.sql`; `flyway-core` en el pom.
- `application.yml`: Flyway activado, `ddl-auto: validate`.
- `CLAUDE.md`: sección nueva del catálogo; corregir lo que dice del excel de
  producción y la memoria, la lista de memorias y la nota de Flyway de la rama DHL.

## Tests que anclan el diseño

- Un artículo nuevo hereda cartón y unidades solo si todos sus hermanos coinciden; un
  artículo sin piel ni hereda ni recibe propagación.
- Propagar al editar toca los hermanos con el mismo valor antiguo o vacío y respeta
  los demás.
- Guardar una temporada con el pedido real de AMI (`EAN PUNTOTRES H26.xlsx`) da de alta
  todos sus artículos y colores sin pisar un nombre ya tecleado; repetirlo no
  duplica nada. Lo mismo con el excel de producción real de APC
  (`PROD_APC_SPRING27.xlsx`) para un cliente genérico de prueba.
- `MemoriaReferencias.buscar`/`recordar` y `MemoriaPieles` dan lo mismo que antes a los
  servicios del taller y del corte (los tests actuales de esos servicios siguen en
  verde sin tocarlos).
- Una temporada de tipo `PRODUCCION` no sale en el desplegable del packing list.
- Borrar una temporada no borra ningún modelo.
- `/catalogo` filtra por temporada y guarda una celda por `fetch` sin perder las demás.
- `tipo` del modelo cambiado a mano no cambia la plantilla del packing de AMI.

## Lo que queda al constructor

El aspecto exacto de la cabecera de filtros y del emergente (siguiendo el estilo
de las pantallas que ya hay), el detalle del JS de edición en celda, la dedupe de
fotos por nombre de fichero, y cómo partir en commits.
