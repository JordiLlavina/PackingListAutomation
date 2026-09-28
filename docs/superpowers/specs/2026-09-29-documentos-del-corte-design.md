# Documentos del Corte — diseño

Fecha: 2026-09-29. Estado: aprobado, en implementación (rama `documentos-del-corte`).

## 1. Qué se construye

Una sección nueva del menú, **Documentos del Corte**, que genera en masa los documentos que
necesita la sección de corte al empezar una temporada:

1. **Órdenes de corte**: un solo Word apaisado con **una página por modelo + piel + color** de
   todo el pedido del cliente. Lleva cliente, temporada, referencia, color, nº de bolsos a cortar,
   los nombres de las pieles (principal, de 0 a 4 de combinación y forro) con un recuadro al lado
   de cada una para pegar la muestra, y la foto principal del modelo.
2. **Fotos del artículo**: un Word por **modelo + piel** cuyo modelo tenga fotos, con temporada,
   referencia, los nombres de las pieles y todas las fotos en una cuadrícula de 2×3 por A4.

Clientes: **AMI y APC**. Los genéricos salen en el desplegable deshabilitados, "en desarrollo".

Fuera de alcance: los genéricos (piden los nombres sin referencia de piel en el pedido), elegir la
foto principal con miniaturas, cualquier edición del Word desde la aplicación.

## 2. Entrada

- **Cliente** (AMI o APC).
- **Temporada**: una temporada guardada (`ArchivoTemporadas`, filtrada por cliente con
  `paraElEnvio`) **o** el excel de pedido subido a mano junto con el nombre de la temporada.
  Un excel subido manda sobre el guardado, igual que en el asistente.
- **Zip de la carpeta de temporada**: una subcarpeta por **modelo** (la referencia del bolso sin la
  piel: `ULL712`, `F67008`) con sus fotos dentro, en HEIC, JPG o JPEG.

## 3. Del pedido a las filas

La referencia del pedido lleva dentro la piel, y cada cliente la pone en un sitio:

| Cliente | Referencia del pedido | Modelo | Piel |
|---|---|---|---|
| AMI | `ULL712.AL0103` (columna `ARTICLE`) | `ULL712` (antes del primer punto) | `AL0103` |
| APC | `PXCBC-F67008` (columna `Article`) | `F67008` (después del primer guion) | `PXCBC` |

Un mismo modelo va en varias pieles (en el pedido real de AMI, `ULL754` va en `AL0137`, `AL0206`,
`AL0218` y `AL0219`; en el de APC, `F67008` va en `PXCBC` y `PXCDS`).

- **Una fila de la pantalla = un modelo + piel** (= una referencia del pedido). Los nombres de
  piel, combinaciones y forro son **los mismos para todos sus colores** (decisión del usuario), así
  que se teclean una vez por fila.
- **Nº de bolsos por color** = suma de la cantidad de las líneas con la misma referencia y color,
  sin importar destinación, talla ni pedido. AMI: columna `Commandé`; APC: `Quantité échéancée`.
  Color de AMI = `COLORIS` + `Libellé coloris` (`001 BLACK`); de APC = `Couleurs` (`LZZ`), que en
  el fichero es una fórmula `LEFT(...)` y se lee por su valor en caché.
- Salen **todas** las referencias del pedido, cinturones incluidos: el alcance de las órdenes es
  el pedido, no las carpetas.
- El método que da líneas **con cantidad** es nuevo en `AmiPedidoExcel` y `ApcPedidoExcel`. El
  `catalogo()` que se le enseña a Claude no se toca: su regla "sin cantidades" la ancla un test.

## 4. Fotos

- **La foto es del modelo, no de la piel**: la carpeta `ULL027` vale para `ULL027.AL0103` y para
  `ULL027.AL0216`.
- La carpeta de una foto es **el antecesor más cercano cuyo nombre sea un modelo del pedido**
  (mayúsculas y espacios aparte), así que da igual que el zip traiga una carpeta raíz o que un
  modelo tenga subcarpetas. Si ningún antecesor casa, la foto es de una carpeta que no está en el
  pedido: **aviso** y no se usa.
- Se ignoran en silencio `__MACOSX/`, `.DS_Store`, `Thumbs.db` y `desktop.ini`. Cualquier otro
  fichero que no sea `.heic`/`.heif`/`.jpg`/`.jpeg` se ignora **con aviso**.
- El zip no se descomprime usando sus nombres como rutas (zip slip): cada foto se escribe con un
  nombre generado dentro del directorio temporal de la sesión, y el nombre original solo sirve
  para ordenarla y enseñarla. Tope de 3 GB descomprimidos y 5.000 entradas. Nombres de entrada en
  UTF-8 y, si no se dejan leer, en CP437 (lo que escribe el Explorador de Windows).
- **Foto principal**: la primera por nombre de fichero, cambiable en la tabla con un desplegable
  por fila con los nombres de las fotos de su modelo.
- Un modelo del pedido **sin carpeta**: su orden de corte sale sin foto y no lleva Word de fotos,
  con un aviso que los lista.

### 4.1 HEIC: medido, no supuesto

Probado contra fotos reales de iPhone (12 MP y 24 MP) el 2026-09-29:

| Decodificador | 12 MP | 24 MP | Memoria | Dónde funciona |
|---|---|---|---|---|
| Openize.HEIC 26.5 (Java puro, BSD) | ~5 s | ~14 s | **~0,8 GB / ~1,8 GB de heap por foto** | Cualquier JVM |
| WIC de Windows por lotes (PowerShell) | ~1,7 s | ~4 s | fuera de la JVM | Windows con las extensiones HEIF + HEVC |

Decodificar por franjas no reduce el pico: Openize reconstruye la cuadrícula entera. Con el heap
por defecto de un PC de 8 GB (2 GB) una foto de 24 MP no cabe. Por eso hay **dos caminos**:

1. **Windows**: las fotos HEIC se convierten por lotes con WIC (un script de PowerShell empaquetado
   en `resources`, varios procesos en paralelo). Las que WIC no pueda leer pasan al camino 2.
2. **Java (Openize)**: el resto, y todas en Linux (el servidor previsto es una VM Linux). Cuántas
   se decodifican a la vez lo limita un **presupuesto de memoria** (semáforo en MB, ~80 bytes por
   píxel, sobre el heap máximo menos una reserva); una foto que no quepa ni sola sale como aviso y
   no tumba la aplicación.

Todas las fotos, JPG incluidos, se guardan **reducidas** (lado mayor 1.600 px, JPEG): sin eso un
Word de 8 fotos pesa 30 MB. Los JPG se enderezan con la orientación EXIF (metadata-extractor),
porque las fotos de móvil llegan tumbadas si no. La conversión **arranca al subir el zip, en
segundo plano**, mientras se rellena la tabla; la pantalla enseña el progreso y generar espera a
que termine.

Openize avisa de que no licencia las patentes de HEVC; para uso interno se acepta.

## 5. Pantalla de pieles

Una fila por modelo + piel, ordenadas por modelo y piel:

| Modelo | Piel | Bolsos por color | Nombre piel | Forro | Combinación 1…N | Foto principal |
|---|---|---|---|---|---|---|
| `ULL729` | `AL0103` | `001 BLACK [169]` `718 VANILLA CREAM [102]` `A237 MOCHA [31]` | | | | `IMG_001.HEIC ▾` |

- **Nº de bolsos editable** por color. Vacío o 0 = esa orden no sale.
- **"+ combinación"** añade una columna (hasta 4). Es un submit del mismo formulario, como el
  desplegar filas de la revisión: aplica lo tecleado y vuelve a pintar. Una combinación vacía no
  existe.
- **Forro**: un solo campo (el "nombre de la piel del forro" y el "nombre del forro si es de piel"
  del encargo son el mismo dato); vacío = no lleva forro de piel.
- Al teclear un nombre de piel se copia a las filas **vacías** con la misma ref de piel (JS).
- Un aviso de progreso de las fotos ("Preparando fotos: 37 de 120") se actualiza solo.

## 6. Memoria de pieles

Tablas nuevas en la H2 (`ddl-auto: update`):

- `memoria_piel` — `cliente + ref piel` → **nombre de la piel**. Es del material (`AL0103` se
  llama igual en los cinco bolsos de AMI que la usan).
- `memoria_pieles_articulo` — `cliente + referencia` (modelo + piel) → **forro y combinaciones**.
  Dependen del bolso: guardadas solo por piel, las combinaciones de `ULL027.AL0103` aparecerían en
  `ULL712.AL0103`, que no las lleva.

Se escriben **al generar**. Un nombre de piel vacío **no borra** el recordado. El forro y las
combinaciones se guardan tal cual (vacío = no tiene) **solo si la fila trae nombre de piel**: una
fila que nadie ha rellenado no ha dado nada por bueno. Si algún día entra Flyway (rama
`envios-dhl`), estas dos tablas necesitan su migración.

## 7. Documentos

**Orden de corte** (`OrdenCorteDocBuilder`), A4 apaisado, una página por modelo + piel + color,
ordenadas como la tabla:

- Banda superior: a la izquierda la foto principal (encajada sin deformar); a la derecha, en
  pequeño "CLIENTE · TEMPORADA" como subtítulo y debajo, en grande, la referencia, el color y el
  nº de bolsos a cortar.
- Resto de la página: una fila por piel (principal, combinaciones, forro), repartidas en vertical
  con altura fija. Rótulo y nombre a la izquierda; a la derecha, un recuadro grande vacío para
  pegar la muestra.
- Alturas exactas y un salto de página entre órdenes, para que cada orden ocupe exactamente una
  página.

**Fotos** (`FotosCorteDocBuilder`), A4 vertical con márgenes estrechos, un documento por
modelo + piel con fotos:

- Cabecera de página (se repite en cada hoja): temporada, referencia y los nombres de las pieles.
- Cuadrícula de 2 columnas × 3 filas que llena el resto del A4, cada foto encajada en su celda sin
  deformar y sin pie de foto. Tantas hojas como hagan falta.

Nombres: `Ordenes de corte <CLIENTE> <TEMPORADA>.docx` y `Fotos <REFERENCIA>.docx`, saneados.
Sin plantilla `.docx`: la maquetación son constantes de cada builder.

## 8. Web

`DocumentosCorteController`, estado en `CorteEnCurso` (`@SessionScope`, independiente del resto de
flujos). Las fotos convertidas y los documentos generados viven en un **directorio temporal de la
sesión**, no en memoria; se borra al empezar otro, al caducar la sesión (`@PreDestroy`) y con el
botón de empezar de nuevo.

| Ruta | Qué hace |
|---|---|
| `GET /documentos-corte` | entrada |
| `POST /documentos-corte/cargar` | lee pedido y zip, arranca la conversión, → pieles |
| `GET /documentos-corte/pieles` | tabla |
| `POST /documentos-corte/pieles/combinacion` | aplica lo tecleado y añade una columna |
| `GET /documentos-corte/progreso` | JSON con fotos hechas / total |
| `POST /documentos-corte/generar` | aplica, recuerda, espera a las fotos, genera, → resultados |
| `GET /documentos-corte/resultados` | descargas y avisos |
| `GET /documentos-corte/descargar/{fichero}`, `/descargar-todo` | un documento o el ZIP |
| `GET /documentos-corte/nuevo` | borra el estado y vuelve a la entrada |

El límite de subida pasa a 1 GB (el zip de una temporada con fotos de 24 MP pesa cientos de MB).

Errores: sin pedido, pedido ilegible o zip ilegible → vuelta a la entrada con el error. Todo lo
demás (carpetas sin pedido, modelos sin fotos, fotos ilegibles, ficheros que no son fotos) es
**aviso** y no bloquea, como en el resto de la aplicación.

## 9. Tests

- Partir referencias de AMI y APC (con y sin separador).
- Filas y sumas contra **los dos pedidos reales**: `ULL729.AL0103` → 169 / 102 / 31;
  `PXCBC-F67008` → `LZZ` 569, `LAW` 200, `KAN` 111, `CAW` 97.
- Lectura del zip: carpeta raíz, subcarpeta dentro de un modelo, `__MACOSX`, extensión en
  mayúsculas, fichero que no es foto, carpeta fuera del pedido.
- Conversión: JPG con orientación EXIF 6 sale girado; JPG grande sale a 1.600 px; el HEIC de
  prueba de Openize (`gimp_rgb_420_with_alpha.heic`, 8 KB, licencia del repo de Openize) decodifica
  por el camino Java, y por WIC solo en Windows con el códec presente. **Ninguna foto personal**
  entra en el repo.
- Builders reabiertos con POI: orientación, páginas, textos, nº de imágenes. Dejan
  `target/Ordenes de corte ejemplo.docx` y `target/Fotos ejemplo.docx` con imágenes de relleno
  dibujadas, para revisar la maquetación a ojo.
- Memoria: el nombre vacío no borra; forro y combinaciones solo con nombre.
- Controlador con MockMvc: entrada con genéricos deshabilitados, carga → pieles, columna nueva,
  generar → resultados → descarga.
