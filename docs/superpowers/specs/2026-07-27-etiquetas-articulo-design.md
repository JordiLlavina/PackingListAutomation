# Etiquetas de artículo (código de barras EAN13) + menú de entrada

Fecha: 2026-07-27 · Estado: aprobado, pendiente de plan de implementación

## 1. Qué se construye

Dos cosas, en un solo trabajo porque la segunda necesita la primera para ser
alcanzable:

1. **Vista `/menu`**: portada con dos tarjetas, una por familia de salidas
   (packing list + etiquetas de caja + volcado ERP; etiquetas de artículo).
2. **Generación de excels de etiquetas de artículo**: a partir del excel de
   pedido del cliente (`docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx`), tres
   ficheros `.xlsx` con matrices de 40 etiquetas por hoja para imprimir en A4
   y enganchar a bolsos y cinturones. Solo AMI implementado; la estructura
   queda preparada para más clientes.

Es un flujo **independiente** del envío: no necesita JSON, ni packing list
generado, ni `EnvioEnCurso`. Su única entrada es el excel de pedido.

## 2. Datos de entrada: el excel de pedido

Una sola hoja, `EAN H26` (la temporada cambia: `EAN E27`…). 151 filas de
datos + cabecera en la fila 1. Columnas localizadas **por el texto de la
cabecera**, no por posición:

| Col | Cabecera | Ejemplo | Uso |
|---|---|---|---|
| A | `Made in` | `SPAIN` / `MOROCCO` | parte el fichero |
| B | `ARTICLE` | `UBL029.AL0104` | referencia de la etiqueta + clasifica bolso/cinturón |
| C | `COLORIS` | `0014` | código de color |
| D | `Libellé coloris` | `NOIR/ARGENT VIBRE` | nombre de color |
| F | `TAILLE` | `75` (cinturón) / `U` (bolso) | talla de la etiqueta |
| G | `PO` | `07704 CH` | código de pedido |
| H | `Commandé` | `13` | no se usa |
| I | `EAN13` | `3666598543892` | código de barras |
| J | `EAN128` | `3666…ES` | se lee, **no se usa** (ver §9) |

Reparto real del pedido de H26, que es la referencia contra la que se
validan los tests:

| | MOROCCO | SPAIN | total |
|---|---|---|---|
| Bolsos (`ULL`/`USL`) | 46 | 26 | 72 |
| Cinturones (`UBL`) | 0 | 79 | 79 |

Todas las filas de bolso tienen `TAILLE = U`; todas las de cinturón tienen
talla numérica (75/85/95/105).

## 3. Salida: tres ficheros, una hoja por fila del pedido

Se agrupa por **(tipo, Made in)**. Cada grupo con al menos una fila produce
un fichero; un grupo vacío simplemente no genera fichero (sin error).

| Fichero | Hojas con el pedido de H26 |
|---|---|
| `AMI CODE BARRE H26 MOROCCO.xlsx` | 46 |
| `AMI CODE BARRE H26 SPAIN.xlsx` | 26 |
| `AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx` | 79 |

Patrones, con `{temporada}` y `{país}` como únicas partes variables:

```
bolsos      AMI CODE BARRE {temporada} {país}.xlsx
cinturones  AMI CODE BARRE ITEMS {temporada} {país} CINTURONES.xlsx
```

`ITEMS` y `CINTURONES` marcan los dos el fichero de cinturones, como en los
ficheros del cliente; **`CINTURONES` va siempre al final del nombre**. El
país va **siempre**, por regla uniforme (los ficheros actuales del cliente lo
omiten en cinturones porque hoy solo hay uno).

La temporada (`H26`) se lee del nombre de la hoja (`EAN H26` → `H26`). Si no
se puede extraer, la pantalla ofrece un campo de texto prellenado con el
`placeholder-temporada` del cliente en `application.yml`.

**Una hoja = una fila del pedido**, es decir un EAN13. Dentro de la hoja, las
40 etiquetas son idénticas.

Orden de las hojas: **todo descendente**, igual que los ejemplos —
`ARTICLE` ↓ (texto), `COLORIS` ↓ (texto), `PO` ↓ (texto, numérico+sufijo),
`TALLA` ↓ (**numérico**, para que salga 105 · 95 · 85 · 75 y no 95 · 85 ·
75 · 105; las tallas no numéricas como `U` van al final).

### Nombre de hoja

Formato: `{ARTICLE} {color} {PO}{sufijo}` y, en cinturones, ` {TALLA}`.

- `PO`: la parte numérica con padding a 5 dígitos, pegada al sufijo sin
  espacio (`07704 CH` → `07704CH`; sin sufijo → `07714`).
- `color`: el **libellé** si el nombre de hoja resultante cabe en los 31
  caracteres que permite Excel *y* no colisiona con otra hoja del mismo
  fichero; si no, el **código COLORIS** (4 caracteres). Nunca se trunca a
  media palabra.
- Saneado: se sustituyen los caracteres que Excel prohíbe (`/ \ ? * : [ ]`)
  por `-` antes de medir.
- La unicidad está garantizada porque `(ARTICLE, COLORIS, PO, TALLA)` es la
  clave natural de la fila y el fallback a COLORIS la reproduce entera. Si
  aun así hubiera colisión (dos filas idénticas en el pedido), se añade
  `-2`, `-3`.

```
USL738.AL0137 TRUFFLE 07714CH      bolso, el libellé cabe          (29)
ULL754.AL0218 A328 07683           bolso, "SAND/CHOCOLATE" no cabe (24)
UBL214.AL0223 BLACK 07694JP 85     cinturón, el libellé cabe       (30)
UBL029.AL0104 0014 07704CH 75      cinturón, "NOIR/ARGENT…" no cabe (29)
```

## 4. Maquetación de la hoja

Medidas extraídas del XML de `docs/Etiquetas para etiquetas/AMI CODE BARRE
H26 MOROCCO.xlsx`. Bolsos y cinturones son estructuralmente **idénticos**:
una sola plantilla sirve para los tres ficheros.

- **Rejilla**: 4 columnas × 10 filas = **40 etiquetas**.
- **Columnas de etiqueta** (índice POI, 0-based): pares `(0,1)`, `(3,4)`,
  `(6,7)`, `(9,10)`. Las columnas 2, 5 y 8 son separadores estrechos.
- **Anchos** (atributo `width` del XML → `width × 256` en POI):
  `A/D/G/J = 14.7109375 (3766)`, `B = 17.28515625 (4425)`,
  `E/H/K = 17.7109375 (4534)`, `C/I = 2.42578125 (621)`, `F = 2.0 (512)`.
- **Bloques**: fila base 0-based ∈ `{1, 9, 17, 25, 33, 41, 49, 57, 65, 73}`
  (paso 8).
- **Altos de fila**: fila 0 = 6pt · filas de texto y de barcode = 15pt
  (defecto) · fila separadora (`base+7`) = 9.95pt.
- **Ajuste al A4**: **no hay print area**. Es
  `pageSetup paperSize=9 (A4), scale=74, orientation=portrait` +
  `pageMargins left=0, right/top/bottom=0.03937 (1mm)`.

Contenido de una etiqueta, con `base` = fila base 0-based, `cIzq`/`cDer` las
dos columnas del par:

| Celda | Contenido | Estilo |
|---|---|---|
| `(base, cIzq)` | `{ARTICLE}` | defecto, 11pt |
| `(base, cDer)` | `Size: {TAILLE}` | alineado a la derecha |
| `(base+1, cIzq)` | `{COLORIS} {libellé}` | izquierda, cuerpo variable (abajo) |
| `(base+1, cDer)` | `Cde: {PO 5 dígitos, sin sufijo}` | alineado a la derecha |
| `base+2 … base+6` | imagen EAN13 flotante | — |

El cuerpo de la celda de color se reduce por longitud del texto, que es lo
que los ejemplos hacen a mano de forma desigual (se observan 10,5 / 10 / 9 /
6pt para longitudes solapadas). Regla determinista: **≤14 → 10,5pt ·
15-17 → 9pt · ≥18 → 8pt**.

### Anclaje de la imagen

Todos los anclajes del ejemplo son idénticos salvo la columna y la fila:

```
from: col ∈ {0,3,6,9}, colOff = 342901 EMU
      row = base + 2,  rowOff = 9525 EMU
size: cx = 1463802 EMU (1,60")   cy = 647700 EMU (0,71")
```

`colOff = 342901` centra el código de barras sobre el par de columnas. POI no
expone `oneCellAnchor` desde `createPicture`, así que se usa el mismo truco
que ya hay en el proyecto: un `XSSFClientAnchor` de dos celdas con
`AnchorType.MOVE_DONT_RESIZE` cuyo extremo se calcula recorriendo anchos y
altos reales. Ese cálculo ya existe como método privado
`anclaje(...)` en `AmiEtiquetasExcelBuilder`; se **extrae** a una clase
compartida (§5).

**Una imagen, 40 anclajes**: en el fichero del cliente las 40 etiquetas de
una hoja apuntan al mismo `xl/media/imageN.gif`. Se replica: un
`workbook.addPicture()` por hoja y 40 `drawing.createPicture()` reusando el
índice. (El builder de etiquetas de caja llama a `addPicture` dentro del
bucle y duplica los bytes; aquí no se repite ese patrón.)

## 5. Estructura de código

Paquete nuevo `service/etiquetasarticulo/`, en paralelo a `service/etiquetas/`
—que se queda intacto: el javadoc de `GeneradorEtiquetasCliente` ya declara
que la etiqueta de artículo no forma parte de su contrato.

```
excel de pedido (bytes)
  → AmiCatalogoEan.desdeBytes()        List<FilaEan>
  → AmiEtiquetasArticuloGenerador      clasifica, agrupa, ordena, nombra
  → EtiquetasArticuloExcelBuilder      plantilla + cloneSheet + celdas + imágenes
  → ResultadoEtiquetasArticulo         List<ExcelEtiquetasArticulo> + avisos
```

| Clase | Paquete | Responsabilidad |
|---|---|---|
| `HojaEan` | `etiquetas` | Lo que hoy está duplicado: localizar la hoja `EAN…`, resolver columnas por texto de cabecera, leer texto de celda (`STRING`/`NUMERIC`/`FORMULA`). `AmiPedidoExcel` pasa a usarla sin cambiar su API pública. |
| `AnclajeImagen` | `etiquetas` | El `anclaje(...)` extraído de `AmiEtiquetasExcelBuilder`, ahora público y compartido. |
| `CodigoBarrasEan13` | `etiquetas` | barcode4j `EAN13Bean` → PNG. Hermano de `CodigoBarrasCode128`. |
| `FilaEan` | `etiquetasarticulo` | `record(madeIn, article, coloris, libelle, taille, po, poSufijo, ean13)`. Calco de una fila del pedido. |
| `AmiCatalogoEan` | `etiquetasarticulo` | Todas las filas del pedido de AMI, sin interpretar. |
| `EtiquetaArticulo` | `etiquetasarticulo` | `record(referencia, talla, color, pedido, ean13)`, las 5 partes **ya formateadas** (`"Size: U"`, `"Cde: 07714"`, `"A236 TRUFFLE"`). `ean13 == null` → hoja sin código de barras. |
| `GeneradorEtiquetasArticuloCliente` | `etiquetasarticulo` | Interfaz: `claveCliente()`, `tituloCampoPedido()`, `generar(byte[] pedido, String temporada)`. **Un cliente nuevo = una implementación.** |
| `AmiEtiquetasArticuloGenerador` | `etiquetasarticulo` | Única implementación por ahora. `esCinturon = ARTICLE.startsWith("UBL")`. |
| `HojaEtiquetas` | `etiquetasarticulo` | `record(nombreHoja, List<EtiquetaArticulo>)`: lo que el builder necesita, sin vocabulario de AMI. |
| `EtiquetasArticuloExcelBuilder` | `etiquetasarticulo` | Compartido entre clientes: la rejilla 4×10 no es de AMI. Un cliente con otra etiqueta traería su propio builder. |
| `ExcelEtiquetasArticulo` | `etiquetasarticulo` | `record(descripcion, nombreFichero, byte[] contenido)`. **No** se reutiliza `ExcelGenerado`: su vocabulario es de packing list (destino, referencia, color, cajasPendientes) y aquí no aplica ninguno. |
| `ResultadoEtiquetasArticulo` | `etiquetasarticulo` | Excels + avisos, como `ResultadoEtiquetas`. |
| `EtiquetasArticuloGenerationService` | `etiquetasarticulo` | Despacho por clave de cliente. Calco de `EtiquetasGenerationService`. |

`AnclajeImagen`, `CodigoBarrasEan13` y `HojaEan` van en `service/etiquetas/`
en vez de en un paquete nuevo: son primitivas de dibujo/lectura, dos de las
tres ya tienen ahí su hermana, e inventar un tercer paquete para tres clases
de métodos estáticos sería peor. `etiquetasarticulo` las importa.

### Plantilla

`src/main/resources/client-labels/etiquetas-articulo-template.xlsx`: **una
sola hoja**, extraída del `AMI CODE BARRE H26 MOROCCO.xlsx` real, con

- las 40 etiquetas ya maquetadas (anchos, altos, estilos, celdas),
- los textos de ejemplo borrados (celdas presentes pero en blanco, para
  conservar el estilo de cada una),
- **sin imágenes** ni anclajes,
- **sin `printerSettings`** (evita que `cloneSheet` duplique la relación
  binaria 46 veces),
- `pageSetup` y `pageMargins` intactos.

Aplica la regla del proyecto: **no editar esta plantilla sin revisar su
builder**.

### Cómo genera el builder

```java
try (XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {
    // 1. una hoja por variante: clonar la plantilla y renombrar
    for (HojaEtiquetas h : hojas) { libro.cloneSheet(0); ... setSheetName ... }
    // 2. borrar la hoja plantilla original
    // 3. por hoja: 40 × 4 celdas + addPicture(1) + createPicture(40)
}
```

`cloneSheet` copia anchos, altos, estilos, `pageSetup` y márgenes, que es
exactamente lo que se quiere heredar del fichero real. Riesgo conocido:
`cloneSheet` arrastra el `r:id` del `pageSetup`; se neutraliza quitando
`printerSettings` de la plantilla. El test que reabre el `.xlsx` con POI y
comprueba `scale=74` cubre esto. **Fallback si diera problemas**: construir
cada hoja desde cero con las constantes de §4, sin cambiar nada más del
diseño.

## 6. Avisos y errores

Regla del proyecto: nunca fallar en silencio, nunca bloquear por datos que un
humano puede resolver.

| Situación | Qué pasa |
|---|---|
| `EAN13` vacío, no de 13 dígitos, o con checksum inválido | La hoja se genera con sus textos, **sin** código de barras, + aviso indicando ref / color / talla / PO |
| Fila sin `ARTICLE` o sin `PO` | Se salta + aviso |
| `TAILLE` vacía | `Size:` sin valor + aviso |
| Un grupo (tipo, país) sin filas | No se genera ese fichero, sin aviso |
| Cliente sin generador | El desplegable lo marca "en desarrollo"; no se puede enviar |
| El excel no tiene hoja `EAN…`, o le falta `ARTICLE` / `PO` / `EAN13` | Error visible en la pantalla de entrada: no hay nada útil que generar |

## 7. Web

```
/                                  → redirect → /menu
/menu                              → dos tarjetas
/packing-list                      → paso 1 del asistente (hoy es "/")
/importar /revision /recalcular /generar /resultados /etiquetas … → sin cambios
/etiquetas-articulo                → [cliente ▾] [excel de pedido] → Generar
/etiquetas-articulo/generar        → POST
/etiquetas-articulo/resultados     → descarga individual + ZIP
/etiquetas-articulo/descargar/{f}
/etiquetas-articulo/descargar-todo
```

- `PackingListController`: `@GetMapping("/")` pasa a `@GetMapping("/packing-list")`;
  se añade un `@GetMapping("/")` que redirige a `/menu`. Los `return "redirect:/"`
  internos pasan a `/packing-list` para que "no hay envío en curso" siga
  llevando al paso 1.
- `MenuController` nuevo, trivial: sirve `menu.html`.
- `EtiquetasArticuloController` nuevo, con su estado en sesión
  `EtiquetasArticuloEnCurso` (`@SessionScope`), independiente de
  `EnvioEnCurso`: los dos flujos pueden coexistir sin pisarse.
- Plantillas nuevas: `menu.html`, `etiquetas-articulo.html`,
  `etiquetas-articulo-resultados.html`. Reutilizan los fragmentos `head` y
  `cabecera` de `fragmentos.html` y `estilo.css`; en `/menu` el `paso` de la
  cabecera va vacío.
- Los estilos de las tarjetas se añaden a `estilo.css` (no hay CSS nuevo por
  pantalla en este proyecto).

Maqueta de `/menu`:

```
┌──────────────────────────────────┐  ┌──────────────────────────────────┐
│ 📦  Packing List y albarán       │  │ 🏷️  Etiquetas de artículo        │
│                                  │  │                                  │
│ Del JSON del envío (o de fotos)  │  │ Del excel de pedido del cliente, │
│ a los excels del cliente, con    │  │ los códigos de barras EAN13 para │
│ pesos inferidos y revisables.    │  │ imprimir y enganchar.            │
│                                  │  │                                  │
│ · packing list por destinación   │  │ · un excel para bolsos           │
│ · etiquetas de caja y palet      │  │ · un excel para cinturones       │
│ · volcado de albarán para el ERP │  │ · 40 etiquetas por A4            │
│                                  │  │                                  │
│            [ Empezar → ]         │  │            [ Empezar → ]         │
└──────────────────────────────────┘  └──────────────────────────────────┘
```

El desplegable de cliente de `/etiquetas-articulo` lista el catálogo de
`application.yml`; los clientes sin generador salen marcados "en desarrollo"
y deshabilitados, igual que el resto de funcionalidades pendientes.

## 8. Tests

Patrón del proyecto: JUnit 5 puro, servicios con `new`, y los builders
reabren el `.xlsx` generado con POI para comprobar celdas reales. Los
fixtures del pedido **se sintetizan**, no se commitean: ya existe
`testutil/PedidoAmiExcel.crear(nombreHoja, Fila...)`.

**Cambio previo necesario**: `PedidoAmiExcel.Fila` escribe las cabeceras
`EAN13`/`EAN128` pero nunca rellena esas celdas. Se le añade un componente
`ean13` (7º) **más un constructor compacto de 6 argumentos** que delega con
`ean13 = null`, para que los tests que ya usan `Fila` sigan compilando sin
tocarlos.

- **`AmiCatalogoEanTest`** — sobre pedidos sintéticos: lee las 8 columnas;
  PO `07704 CH` → numérico `07704` + sufijo `CH`; PO numérico sin sufijo;
  fila sin `ARTICLE` o sin `PO` se salta; `TAILLE` numérica llega como texto
  (`75`, no `75.0`).
- **`AmiEtiquetasArticuloGeneradorTest`** — agrupación en (tipo, país):
  un pedido con bolsos MOROCCO + bolsos SPAIN + cinturones SPAIN da 3
  ficheros con los nombres esperados, y uno sin filas MOROCCO da 2 sin
  aviso; los cuatro casos de nombre de hoja de §3 (libellé cabe / no cabe,
  con y sin talla); orden de hojas de §3 incluida la talla numérica
  descendente; aviso cuando una fila trae un EAN13 inválido y esa hoja se
  genera igual.
- **`EtiquetasArticuloExcelBuilderTest`** — reabre el generado y comprueba:
  `A2/B2/A3/B3` del primer bloque y del último (fila base 73); las 4
  columnas de etiqueta del mismo bloque; 40 imágenes en el `drawing` y
  **una sola** `PictureData` por hoja; anchos de columna de §4; `pageSetup`
  (A4, `scale=74`, portrait) y `pageMargins`; que una etiqueta sin EAN13 no
  añade imagen; que el cuerpo de la celda de color cambia con la longitud.
- **`CodigoBarrasEan13Test`** — un EAN13 válido produce PNG; uno con
  checksum malo, longitud distinta o no numérico se rechaza de forma
  controlada (sin excepción de barcode4j sin envolver).
- **`AmiPedidoExcelTest`** (existe) debe seguir en verde tras extraer
  `HojaEan`: es la red de seguridad de ese refactor.
- **`EtiquetasArticuloGenerationServiceTest`** — despacho por clave, cliente
  sin generador devuelve vacío. Calco de `EtiquetasGenerationServiceTest`.
- **Test end-to-end con el pedido real.** Se copia
  `docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx` (23 KB) a
  `src/test/resources/ejemplos/` y un test comprueba el reparto real
  (46 / 26 / 79 hojas) y deja los 3 `.xlsx` en `target/` para inspección e
  impresión manual, como `flujoCompletoGeneraExcelsAbribles`. Es el único
  test que usa un binario commiteado, y a cambio es el que valida que el
  formato real del cliente se lee bien.

## 9. Fuera de alcance

- **Hojas EAN128.** Los ejemplos del cliente tienen 14 hojas hechas a mano
  (nombre acabado en `128` / `EAN128`) que llevan el código de la columna J
  en vez del EAN13: 3 en el fichero SPAIN de bolsos y 11 en el de
  cinturones. Eso explica que los ejemplos tengan 29 y 90 hojas en vez de 26
  y 79. Se lee la columna J pero no se usa. Añadirlo después es una hoja más
  por fila y un `Code128Bean`, sin tocar el resto del diseño.
- **Otros clientes.** La interfaz y el despacho quedan listos; solo AMI
  implementado. Un cliente con la misma rejilla 4×10 reutiliza
  `EtiquetasArticuloExcelBuilder` y solo aporta su lector de pedido y su
  clasificación.
- **Filtrar por PO o por país** desde la pantalla: se generan siempre todas
  las filas del pedido.
- Actualizar `README.md` / `ARCHITECTURE.md`, que ya están desactualizados y
  tienen su propia entrada en `TODO`.
