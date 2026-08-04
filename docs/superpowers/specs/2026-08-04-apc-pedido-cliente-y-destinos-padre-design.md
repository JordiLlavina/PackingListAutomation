# Excel de pedido en la entrada, destinos padre de APC y Livraison code generado

Fecha: 2026-08-04

## Problema

Tres cosas de la digestión de datos de APC no se corresponden con la realidad
del cliente:

1. **Las destinaciones hijas no generan packing list.** APC agrupa varias
   destinaciones bajo un padre, pero el catálogo de `application.yml` solo
   conoce cinco destinos sueltos. `PackingListController` salta con aviso
   cualquier destinación fuera del catálogo, así que hoy un envío a
   `Australia`, `Chine franch`, `Retail` o `Wholesale` **no produce excel**.
   No es que se valide mal: es que no se genera.
2. **El Livraison code se teclea.** Llega por el JSON (`livraisonCode`) y no
   hay forma de comprobarlo. En realidad es un código con estructura fija que
   la aplicación puede construir sola a partir de datos que ya tiene.
3. **El número de pedido llega incompleto.** Lo que se lee de la imagen o del
   formulario son los **tres últimos dígitos** del pedido, no el pedido. El
   número completo vive en el excel de pedido del cliente, que la aplicación
   nunca ve en el flujo de packing list.

Además, el excel de pedido de AMI se pide en el Paso 2 de etiquetas
(`/etiquetas`) aunque el usuario ya lo tenga a mano al empezar el envío.

## Alcance

Entra:

- Campo de subida del excel de pedido del cliente en `/packing-list`, para
  APC y AMI (los demás clientes no lo ven).
- Destinaciones padre/hija de APC: resolución, fusión y avisos.
- Livraison code generado y editable.
- Número de pedido completado desde el excel de pedido de APC.
- Los `NOT FOUND` de `ASN N°`/`Livraison code` y `Order N°` de las etiquetas
  de caja de APC, que dejan de faltar porque los dos datos ya existen.

No entra:

- La etiqueta de caja de **RETAIL**. `ApcEtiquetaLayout` no tiene plantilla
  para ese destino, así que un envío a RETAIL genera packing list pero **no**
  etiquetas de caja. Queda para otra iteración.
- Cualquier uso del excel de pedido de AMI en el packing list. Para AMI el
  fichero solo se guarda en sesión y se reutiliza en `/etiquetas`.
- Renumerar cajas o palets.

## Decisiones tomadas

- `C-LOG` pasa a llamarse `WHOLESALE` (más claro; el fichero de plantilla de
  `docs/` ya está renombrado). `RETAIL` es un destino nuevo. `IVRY` es
  independiente: ni padre ni hija.
- **Solo WHOLESALE y RETAIL tienen hijas.** Los demás destinos son destinos
  normales que además declaran su abreviatura.
- Una hija toma **todo** del padre —nombre de fichero, hoja, nombre-cliente y
  dirección— y solo sobrevive en la columna `DESTINATION` de sus líneas.
- Dos hijas del mismo padre en un envío se fusionan en **un solo** excel.
- Si al fusionar se repite un `numeroCaja` o un `numeroPalet`, **aviso y no se
  toca ningún número**: el bulto lleva ese número pegado físicamente y el
  packing list debe coincidir con la etiqueta, no al revés. Se corrige en la
  revisión, donde caja y palet ya son editables.
- La abreviatura del padre se configura en `application.yml`, no se lee del
  excel de pedido: así el Livraison code existe aunque no se haya subido
  ningún fichero, y dar de alta una hija sigue siendo configuración.
- El Livraison code se muestra **entero y editable** por destinación, no como
  un campo de contador aparte.
- Sin excel de pedido, o si la búsqueda del PO falla, **se avisa y se deja el
  dato parcial**. Nunca se inventa un pedido ni se bloquea el envío.

## Diseño

### Catálogo de destinos de APC (`application.yml`)

`DestinoClienteConfig` gana dos campos: `abreviatura` y `destinosHijo`.

```yaml
"[WHOLESALE]":                    # antes C-LOG
  nombre-cliente: A.P.C.
  direccion: CROSSLOG, 104 RUE DENIS PAPIN, 77550 MOISSY CRAMAYEL, FRANCE
  abreviatura: WH
  destinos-hijo: [AUSTRALIA, WHOLESALE, CHINE FRANCH, C-LOG]
"[RETAIL]":
  nombre-cliente: A.P.C.
  direccion: 74 BIS AV MAURICE THOREZ 94200 IVRY SUR SEINE FRANCE
  abreviatura: RT
  destinos-hijo: [RETAIL, WHOLESALE CONCESS]
"[D. USA]":
  abreviatura: UST
"[JAPAN]":
  abreviatura: JPT
"[KOREA]":
  abreviatura: KRT
"[IVRY]":
  # sin abreviatura conocida todavía: ver más abajo
```

Las abreviaturas no son inventadas: son los valores de la columna
`Catégorie de stock` del pedido real de APC, donde cada línea trae su
destinación hija en `Notre référence` y su padre abreviado en esa columna.

- **`C-LOG` se mantiene como hija de WHOLESALE** aunque no esté en la lista
  del cliente: los JSON de ejemplo lo usan y `ApcEtiquetaLayout` lo mapea a la
  plantilla de Crosslog. Mantenerlo no cuesta nada y no rompe nada.
- **`IVRY` no tiene abreviatura conocida**: no aparece en el pedido de APC,
  que solo trae WH, RT, UST, JPT y KRT. Regla general para ese caso: un
  destino sin `abreviatura` usa **su nombre en mayúsculas y sin espacios**, y
  se avisa. Es la misma caída que ya usa `AmiNombreFichero` con una
  destinación desconocida. Cuando el cliente diga la abreviatura de IVRY es
  una línea de yml.
- Añadir una hija nueva = una línea en `destinos-hijo`, sin tocar Java. Misma
  regla que las taras y que los clientes de plantilla GENERIC.
- Un cliente que no declara `destinos-hijo` (AMI, genéricos) no cambia en nada.

`ClienteConfig` gana un `destinoPadrePara(String)` que busca primero por clave
de destino y luego por hija, normalizando igual que `destinoPara` (trim +
mayúsculas), para que `"chine franch "` de una imagen case con
`CHINE FRANCH` del yml.

### Resolución hija → padre (`ResolutorDestinosPadre`)

Servicio nuevo, sin estado. Entra la lista de `EnvioImportado.DestinoImportado`
y el `ClienteConfig`; sale la lista fusionada más sus avisos, en el DTO de
resultado de siempre.

Vive fuera de `EnvioImportService` **a propósito**: el importador traduce el
JSON a dominio y no conoce el catálogo de clientes. Se llama en
`PackingListController.importar`, justo después de importar y **antes** de
asignar palets, para que la asignación y la inferencia ya trabajen sobre los
destinos definitivos.

Reglas:

- Destinación que casa con una hija → se renombra al padre.
- Varias hijas del mismo padre → un `DestinoData` con las cajas de todas
  concatenadas en el orden en que llegaron, y los palets de todas.
- La hija original va a la columna `DESTINATION` (K): si la línea no trae
  `canal`, se rellena con el nombre de la hija; si lo trae, manda el JSON (y
  sigue siendo editable en la revisión).
- Un aviso por cada `numeroCaja` repetido entre hijas fusionadas y otro por
  cada `numeroPalet` repetido. Sin renumerar.
- Destinación que no casa con ninguna hija ni con ninguna clave: se deja tal
  cual y sigue el camino de hoy (aviso de destino no configurado al generar).

Como la fusión ocurre **antes** de generar, `ApcExcelBuilder` no se entera de
que existen las hijas: recibe un `DestinoData` que ya se llama `WHOLESALE` y
saca de ahí el nombre de fichero (`PKL_APC_WHOLESALE_<factura>.xlsx`), el
nombre de hoja y —vía `destinoPara`— la dirección de cabecera. Esto además
elimina el choque de nombres de fichero que habría si cada hija generase el
suyo con el nombre del padre: `ExcelGenerado` se descarga por nombre con un
`findFirst()`, y el segundo excel habría quedado inaccesible en silencio.

### Livraison code

Formato: `"PUN" + fecha de envío (yyyyMMdd) + abreviatura del padre + contador`.
Ejemplo real, tomado de la etiqueta de caja de WHOLESALE del cliente:
`PUN20260717WH1`.

- La fecha de envío es la de la pantalla de entrada (`dd/MM/yyyy` en el
  formulario), reformateada a `yyyyMMdd`.
- El contador por defecto es `1`. La aplicación **no puede** saber el correcto:
  no hay historial de envíos. Por eso el código se muestra entero y editable.
- Se calcula **una vez por destinación padre** al importar y se estampa en
  todas las `CajaData` de esa destinación. Así `ApcExcelBuilder` no cambia:
  sigue leyendo `linea.getLivraisonCode()` y escribiéndolo en la columna F.
- En la revisión es un **input de texto en la cabecera de la sección de la
  destinación**, no una columna de la tabla: es un dato de la destinación, y
  como columna se repetiría en cada fila y ensuciaría el criterio de
  compactación de `AgrupadorFilasRevision`. Al editarlo se reescribe en todas
  las cajas de esa destinación.
- El `livraisonCode` que traiga el JSON **se ignora** para APC: el generado
  siempre gana. Queda documentado en `campos-json-por-cliente.md`.

### Pedido completo (`ApcPedidoExcel` + `PedidoCompletionService`)

`ApcPedidoExcel` calca el patrón de `AmiPedidoExcel`: índice en memoria
construido desde `byte[]`, columnas localizadas **por el texto de su
cabecera**, nunca por coordenada.

- **Hoja**: la primera cuya fila de cabecera contenga `Article`,
  `Document d'achat` **y** `Notre référence`. Los tres, no dos: el libro trae
  otras hojas (`LCT`, `Sheet2`) con `Article` + `Document d'achat` que darían
  la hoja equivocada, sin las columnas de destinación.
- **Clave de búsqueda**: `Article` + los **tres últimos dígitos** del
  `Document d'achat`. Comprobado contra las 130 líneas del pedido real de la
  temporada: **0 colisiones**. La referencia sola no vale —`PXCBC-F63023`
  aparece en 8 pedidos distintos—, así que los tres dígitos no son un atajo,
  son imprescindibles.
- Como en los escandallos del ERP, las celdas pueden venir vacías y POI
  devolver `null`: la lectura no asume texto.

`PedidoCompletionService` recorre las cajas del envío, busca por
`referencia` + los tres últimos dígitos de `numeroPedido`, y sustituye el
valor por el PO completo.

- Funciona igual si el dato ya venía completo: sus tres últimos dígitos
  encuentran el mismo PO.
- Sin excel subido, sin fila que case, o clave ambigua (más de un PO) →
  **aviso y se deja lo que había**. Nunca se escribe un PO adivinado.
- Corre **solo al importar**, nunca en `recalcular`/`alternar-fila`. Misma
  razón que `PaletAssignmentService`: volver a ejecutarlo pisaría el pedido
  que el usuario acabe de corregir a mano en la revisión.

### Pantalla de entrada y sesión

- Input de fichero "Excel de pedido del cliente", opcional, visible solo si el
  cliente lo declara con `pedido-cliente: true` (APC y AMI). El JS de
  `entrada.html` ya muestra y oculta campos por cliente leyendo `clientesJs`;
  se le añade ese flag junto a `plantilla` y `placeholderTemporada`.
- El contenido se guarda en `EnvioEnCurso` y lo limpia `reiniciar()`, como
  todo lo demás del envío en curso.
- Lo consumen dos sitios: `PedidoCompletionService` (APC) y el **Paso 2 de
  etiquetas**, que deja de exigirlo. Si la sesión ya lo tiene, el input de
  `/etiquetas` pasa a ser opcional y la vista lo dice ("ya subiste *X* en el
  paso 1; súbelo otra vez solo para cambiarlo").
- Para que el controlador sepa **qué** campo del Paso 2 puede rellenar desde
  la sesión, `CampoEtiquetas` gana un flag `esPedidoCliente`. Es explícito a
  propósito: adivinarlo por el nombre del input funcionaría hoy, con un único
  campo, y se rompería en cuanto un generador pida dos ficheros.

### Etiquetas de caja de APC

`ApcEtiquetasGenerador` deja de escribir la constante `NOT FOUND` en
`ASN N°`/`Livraison code` y en `Order N°`: toma `livraisonCode` y
`numeroPedido` de la línea líder de la caja, y solo cae a `NOT FOUND` si
siguen siendo nulos. No hay lógica nueva; es dejar de descartar dos datos que
a partir de ahora existen.

## Tests

Como el resto del repo: JUnit 5 puro instanciando los servicios con `new`, y
`@SpringBootTest` solo donde hace falta cableado real (capa web y catálogo de
`application.yml`). Los tests de builder reabren el `.xlsx` generado con POI y
comprueban celdas reales.

| Test | Qué ancla |
|---|---|
| `ApcPedidoExcelTest` | Contra el fichero real copiado a `src/test/resources/ejemplos/`: hoja localizada por sus tres cabeceras, unicidad de `Article`+3 dígitos, celdas vacías sin reventar |
| `ResolutorDestinosPadreTest` | Hija → padre; fusión de dos hijas; avisos de caja y palet repetidos; `canal` relleno con la hija; cliente sin hijas intacto |
| `LivraisonCodeTest` | Formato completo, fecha reformateada, contador por defecto, destino sin abreviatura (caída + aviso) |
| `PedidoCompletionServiceTest` | Completa el PO; no pisa un valor tecleado a mano; avisa sin excel, sin fila y con clave ambigua |
| `ApcExcelBuilderTest` | Reabre el excel: columna F con el Livraison code, G con el PO completo, K con el nombre de la hija |
| Capa web (`@SpringBootTest`) | El input de pedido sale para APC y AMI y no para ACKERMANN; el Livraison code editable por destinación se aplica a todas sus cajas |
| `EjemplosJsonTest` | Sigue verde con el catálogo nuevo (clave `WHOLESALE` en vez de `C-LOG`) |

El excel de pedido de APC se copia a `src/test/resources/ejemplos/`, igual que
ya está el de AMI, y se anota en el README de esa carpeta como **dato real de
cliente** (no fixture inventado).

## Documentación a actualizar

- `docs/Packing Lists/campos-json-por-cliente.md`: tabla de APC — `pedido` son
  los tres últimos dígitos y se completa desde el excel de pedido;
  `livraisonCode` se ignora; lista de destinos padre e hijas.
- `CLAUDE.md`: destinos padre/hija de APC, Livraison code generado, la regla
  de no renumerar al fusionar, y que el excel de pedido se sube en la entrada.
- `src/test/resources/ejemplos/README.md`: el pedido de APC pasa a estar en
  uso por el código.
- Comentarios del bloque `packing-list.clientes` de `application.yml`.
