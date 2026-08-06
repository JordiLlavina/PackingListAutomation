# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Qué es este proyecto

Generador de packing lists en Excel para clientes de un proveedor de marroquinería (Punto Tres). A partir de un JSON de envío (pegado a mano o extraído de fotos con la API de Anthropic), asigna palets, infiere pesos que faltan y genera un `.xlsx` por grupo rellenando la plantilla real de cada cliente con Apache POI. Incluye un asistente web (Spring Boot + Thymeleaf) de 3 pantallas y un volcado de albarán para el ERP.

Stack: Java 17, Spring Boot 3.5 (web + thymeleaf + validation), Apache POI, Jackson, SDK Java de Anthropic. Idioma del código y la documentación: **español** (nombres de clases en inglés, javadoc/comentarios/UI en español).

## Comandos

```bash
mvn test                                      # toda la suite
mvn test -Dtest=PackingListGenerationServiceTest   # una clase de test
mvn -q compile exec:java                      # prueba manual: Main.java, flujo completo → excels en target/
mvn spring-boot:run                           # asistente web en http://localhost:8080
```

- El test end-to-end `flujoCompletoGeneraExcelsAbribles` deja excels reales en `target/` para inspección manual.
- El modo CLAUDE de la pantalla de entrada (fotos o PDFs → JSON) necesita la variable de entorno `ANTHROPIC_API_KEY`. Sin ella la app arranca igual y el resto de modos funciona (cliente HTTP perezoso).
- **Windows**: matar `mvn spring-boot:run` no mata el proceso `java` hijo y el 8080 queda ocupado. Liberarlo: `netstat -ano | findstr :8080` + `taskkill /F /PID <pid>`.
- La portada es `/menu`; el asistente de packing list vive en `/packing-list` y `/` redirige al menú.
- `EscandallosFlujoRealTest` deja `target/Escandallos ICSUITE.xlsx` con los dos escandallos reales, para compararlo a mano con el ejemplo de `docs/Procesado Escandallos ICSUITE/`.

## Documentación que ya existe (y su estado)

- [README.md](README.md) — uso, formato del JSON de entrada, taras.
- [ARCHITECTURE.md](ARCHITECTURE.md) — recorrido interno del pipeline y decisiones de diseño. **Lectura recomendada antes de tocar los servicios.**
- ⚠️ Ambos están **parcialmente desactualizados**: dicen que no hay capa web ni endpoint y que solo existe el builder AMI. En realidad ya existen la web completa (`web/`), la extracción por imágenes (`ClaudeEnvioExtractionService`), los builders APC y genérico, y el volcado ERP. Lo que cuentan del pipeline de dominio sigue siendo válido.
- [docs/Packing Lists/campos-json-por-cliente.md](docs/Packing%20Lists/campos-json-por-cliente.md) — campos del JSON por cliente; en `docs/Packing Lists/` hay ejemplos `.xlsx` completos de cada plantilla.
- [docs/Procesado Escandallos ICSUITE/](docs/Procesado%20Escandallos%20ICSUITE/) — dos escandallos reales del ERP (`ULL*.xlsx`) y el excel de salida de ejemplo; los dos escandallos están copiados en `src/test/resources/ejemplos/escandallos/`.
- [docs/superpowers/specs/2026-07-29-procesado-escandallos-icsuite-design.md](docs/superpowers/specs/2026-07-29-procesado-escandallos-icsuite-design.md) — diseño de la feature de escandallos.
- [TODO](TODO) — lista de pendientes que mantiene **el usuario**; consultarla al empezar, no reescribirla por tu cuenta.
- [SESSION_HANDOFF.md](SESSION_HANDOFF.md) — traspaso de la última sesión ("stop the session" lo regenera, no es un log).

## Arquitectura (lo esencial)

Pipeline de servicios Spring sin estado, encadenados por `PackingListController` (web) o `Main.java` (prueba manual):

```
EnvioInput (calco del JSON)
  → EnvioImportService      expande rangos de cajas, valida sin bloquear
  → PaletAssignmentService  asigna palet por rango de nº de caja
  → WeightInferenceService  infiere pesos que faltan (taras de application.yml)
  → PackingListGenerationService  despacha al builder del cliente → excels
  → VolcadoErpGenerationService / VolcadoErpExcelBuilder  albarán para el ERP
  → EtiquetasGenerationService → GeneradorEtiquetasCliente (AmiEtiquetasGenerador)
      → excels de etiquetas de caja (mismo paso que los packing lists)
```

Tres capas de modelo, separadas a propósito (ver ARCHITECTURE.md):
1. `model/EnvioInput` — calco literal del JSON de entrada, frágil a propósito; solo `EnvioImportService` lo conoce.
2. `model/CajaData`, `DestinoData`, `PaletData` — dominio: una caja física por objeto; los servicios trabajan solo contra esto.
3. `PackingListData` — vocabulario de la plantilla Excel de un cliente concreto.

**Multi-cliente**: `GeneradorPackingListCliente` es la interfaz; `AmiGenerador`/`AmiExcelBuilder`, `ApcExcelBuilder` y `GenericoExcelBuilder` la implementan, y `PackingListGenerationService` despacha según `ClienteConfig.getPlantilla()` (`TipoPlantilla`: AMI, APC, GENERIC). El catálogo de clientes vive en `application.yml` (bloque `packing-list.clientes`): **añadir un cliente de plantilla GENERIC es solo configuración** (nombre-legal + direccion-entrega), sin tocar Java. Igual con las taras: un tamaño de caja nuevo es una línea en `packing-list.taras`.

**Nombre de los excels de packing list**: APC y GENERIC usan `PKL_<destino>_<factura>.xlsx`, pero **AMI tiene el formato que exige el cliente** y vive en `AmiNombreFichero`: `<fecha envío yyyy.MM.dd>_PUN_<product order>_<referencia>.<color>_<temporada>_<destinación abreviada>.xlsx`. La destinación se abrevia (FRANCE/FRANCIA/PARIS → FR, CHINA, JAPAN) y una destinación desconocida no bloquea: va en mayúsculas y sin espacios. Los campos que falten se omiten en vez de dejar separadores sueltos.

**Destinos padre de APC**: `WHOLESALE` y `RETAIL` agrupan destinaciones hijas (`AUSTRALIA`/`WHOLESALE`/`CHINE FRANCH` y `RETAIL`/`WHOLESALE CONCESS`) que se tratan como el padre: mismo fichero, misma hoja y misma dirección, y la hija solo sobrevive en la columna `DESTINATION` (si la línea no trae `canal`, se rellena con ella). Lo hace `ResolutorDestinosPadre` tras importar y **antes** de asignar palets, fusionando en un solo `DestinoData` las hijas del mismo padre —lo que además evita dos ficheros con el mismo nombre, que `/descargar/{nombreFichero}` resuelve con `findFirst()` y dejaría el segundo inalcanzable en silencio. **Nunca renumera cajas ni palets**: si dos hijas repiten un número, avisa y lo deja, porque ese número va pegado al bulto. Añadir una hija es una línea de `destinos-hijo` en `application.yml`. `C-LOG` era el nombre viejo de `WHOLESALE` y ya no existe.

**Livraison code de APC**: lo genera `LivraisonCode` (`PUN` + fecha de envío `yyyyMMdd` + `abreviatura` del padre + contador), no el JSON — el `livraisonCode` de entrada **se ignora**. Las abreviaturas del yml (WH, RT, UST, JPT, KRT) son las de la columna `Catégorie de stock` del pedido real; un destino sin abreviatura cae a su nombre en mayúsculas y sin espacios, con aviso. El contador arranca en 1 porque no hay historial de envíos, así que el código se muestra **entero y editable en la cabecera de cada destinación** de la revisión, no como columna de la tabla: como columna se repetiría en cada fila y ensuciaría el criterio de compactación.

**Número de pedido y referencia de APC**: de las hojas manuscritas los dos llegan **incompletos** — del pedido, sus **tres últimos dígitos**; de la referencia, lo que escribe el operario (`67043`, `F63023`), que es un **sufijo** del `Article` real (`PXCEI-F67043`). `PedidoCompletionService` busca la fila en el excel de pedido del cliente (`ApcPedidoExcel.filasPara`, sufijo de `Article` + esos tres dígitos; una fila exacta por referencia gana sobre las de solo-sufijo) y, si es **única**, estampa el pedido Y la referencia enteros; en el fichero real todas las referencias de las hojas escaneadas son unívocas (`ApcPedidoExcelTest` lo ancla). La hoja se localiza exigiendo **tres** cabeceras (`Article`, `Document d'achat` y `Notre référence`), porque otras dos hojas del libro tienen las dos primeras. Sin excel, sin fila o con varias candidatas: aviso y se deja lo que llegó, nunca un dato adivinado. **Corre solo al importar**, igual que `PaletAssignmentService`: reejecutarlo en los recálculos de la revisión machacaría lo tecleado a mano.

**Extracción por imágenes** (`ClaudeEnvioExtractionService`): acepta **fotos y PDFs** (los PDFs viajan como `DocumentBlockParam`; `bloquesDe` es el seam testeable). Las hojas reales son **notas manuscritas en taquigrafía de operario**, no tablas — hojas de ejemplo en `docs/Packing Lists/*IMAGENES.pdf`, análisis completo y catálogo de fallos en [docs/Packing Lists/prompt-extraccion-claude.md](docs/Packing%20Lists/prompt-extraccion-claude.md), y la guía de escritura para el operario en [guia-operario-packing-manuscrito.md](docs/Packing%20Lists/guia-operario-packing-manuscrito.md). El prompt se monta **núcleo + bloque por `TipoPlantilla`** (`promptPara`): el núcleo enseña la taquigrafía (rangos, cajas mixtas "TODO Nº 1", tachados = no existen, apóstrofo decimal) y el bloque lo específico del cliente (AMI: partir `UBL029.AL0216.2221` en referencia+color, numeración continua; APC: referencia y pedido parciales a propósito, hoja por destinación, numeración que reinicia). El JSON extraído trae dos campos que el pegado a mano no suele traer: `avisos` (dudas de lectura → `EnvioImportado.getAvisos()` con prefijo "Lectura de las hojas:") y `resumenPalets` (recuentos declarados, "wh. 5 palet"). `ValidadorResumenExtraccion` contrasta ese resumen con lo extraído y es **la única validación del proyecto que bloquea** (vuelta a la entrada sin pasar a revisión, con el JSON extraído volcado al textarea en modo JSON): un descuadre ahí significa casi siempre que falta una hoja en el escaneo, que no se arregla en la revisión. Recuento sin reparto de cajas = aviso, no bloqueo (los palets se completan en la revisión); `parsear` normaliza el apóstrofo decimal (`13'52` → `13.52`) como red del prompt, y el importador limpia el prefijo de talla manuscrito (`t75`/`+75` → `75`).

**Presupuesto de tokens de la extracción**: el razonamiento y el JSON de salida **comparten el techo de `max_tokens`**. Con `ThinkingConfigAdaptive` descifrar siete páginas de caligrafía se comía el techo entero y la respuesta llegaba cortada (`stop_reason: max_tokens`) — el usuario veía "se ha cortado por longitud" y ningún JSON. Por eso el presupuesto de razonamiento es **explícito** (`ThinkingConfigEnabled.budgetTokens`) y `maxTokens` es la **suma** de razonamiento + salida: la salida tiene sitio garantizado pase lo que pase (`elRazonamientoTieneSuPropioPresupuestoYDejaSitioALaSalida` lo ancla). La petición va en **streaming acumulado** (`createStreaming` + `MessageAccumulator`) y el cliente lleva timeout de 15 min: no es por enseñar nada al usuario, es lo que permite pedir un presupuesto grande sin morir por timeout de lectura.

**Excel de pedido en la entrada**: los clientes con `pedido-cliente: true` en `application.yml` (APC y AMI) muestran un input de fichero opcional en `/packing-list`. Se guarda en `EnvioEnCurso` y de ahí lo saca todo lo demás; es **la única** vez que se pide. Como ya no hay pantalla que confirme cuál se subió, la tarjeta de etiquetas de `/resultados` nombra el fichero con el que se generaron: el pedido equivocado daría etiquetas malas sin decir nada.

**Etiquetas de caja** (`service/etiquetas/`): estrategia propia `GeneradorEtiquetasCliente` despachada por **clave de cliente** (no por TipoPlantilla); cada implementación declara qué destinaciones soporta y qué archivos necesita (`CampoEtiquetas`). **No hay paso intermedio**: se generan dentro del mismo `POST /generar` que los packing lists, y `CampoEtiquetas` ya no pinta inputs — le dice al controlador qué sacar de la sesión. Lo que no se pueda resolver sale como aviso en `/resultados` y deja el envío sin etiquetas, nunca sin packing lists. Implementado: AMI (China/Japan/France; hoja por destinación, par de etiquetas A4 por caja, más su hoja de palets) y APC (JAPAN, KOREA, D. USA, WHOLESALE y RETAIL, **una plantilla por destinación** y dos hojas por libro, cajas y palets; sin archivos extra, porque el pedido y el Livraison code ya vienen rellenos del packing list). `IVRY` sigue sin etiqueta: genera packing list y avisa. Plantillas en `src/main/resources/client-labels/` — misma regla que las de packing list: **no editarlas sin revisar su builder** (`AmiEtiquetasExcelBuilder`/`AmiEtiquetaLayout`, cuyas coordenadas ancla `AmiEtiquetaLayoutTest`; `ApcEtiquetasExcelBuilder`/`ApcEtiquetaLayout`, las de APC).

**Etiquetas de palet de AMI**: una hoja más (`Etiquetas Palets CHINA/JAPAN/FRANCE`) dentro del mismo libro de etiquetas de caja, con **dos etiquetas por A4** (media página cada una, salto de página cada dos palets). Sus coordenadas son constantes compartidas de `AmiEtiquetaLayout` (`FILA_PRIMER_PALET`, `ALTURA_BLOQUE_PALET`, `FILA_PALET_COLIS`, `FILA_PALET_PESO`) porque las tres hojas son idénticas salvo los textos fijos del destinatario. Tres cosas que no son obvias: el bloque **arranca en la fila 1**, no en la 0 (encima va un contador que el cliente apunta a mano, se blanquea y no se replica — por eso existe `BloqueEtiquetaModelo.capturar(hoja, filaInicio, altura)`); la plantilla trae **dos bloques de ejemplo** y lo que sobre tras el último palet **hay que borrarlo**, o la etiqueta de ejemplo del cliente se imprime y acaba pegada en un bulto; y los rótulos de la columna B son **bilingües y multilínea** (`EXPEDITEUR\n(Shipper / Sender)…`), así que buscarlos por texto exige comparar solo la primera línea.

El campo palet es **opcional**, y basta con que **una** caja de la destinación no lo traiga para que ese excel salga **sin hoja de palets** y con aviso: una hoja a medias se imprime y se pega igual que una completa, y quien la mire asumirá que están todas. Es más estricto que APC, que genera la hoja aunque esté vacía. Un peso que falta sí es distinto: solo deja en blanco su celda. El rango `Nº X à Nº Y` se lee de **las cajas**, no del `cajaInicio..cajaFin` del `PaletData`: ese es el del JSON original y se queda viejo en cuanto el usuario corrige un palet en la revisión. El peso es la suma de las cajas físicas más la tara (10 kg si el JSON no la trae), como en APC, pero se rotula `Kg` y no `KGS` porque es lo que pone la plantilla de palet.

**RETAIL y WHOLESALE de APC comparten maquetación pero no plantilla**: las dos etiquetas van al mismo almacén (Crosslog) y el cliente solo partió el fichero para que se imprima `RETAIL` o `WHOLESALE` en la línea `DESTINATION`; todo lo demás —filas, celdas combinadas, anclajes del logo— es idéntico. Por eso `ApcEtiquetaLayout.RETAIL` repite las coordenadas de `WH_CROSSLOG` y **ese estático es lo único que distingue haber abierto una plantilla u otra**: apuntar RETAIL al fichero de Crosslog pasaría cualquier aserción de celdas, así que se ancla aparte (`retailYWholesaleCompartenCoordenadasPeroNoPlantilla` y la aserción de `DESTINATION` en `ApcEtiquetasGeneradorTest`).

Cada etiqueta lleva **dos imágenes**, no tres: la **imagen compuesta** del artículo (los cuatro textos de la etiqueta de artículo más su EAN-13, generados en un solo PNG por `ImagenEtiquetaArticulo`, que solo habla del **primer** artículo de la caja) y el Code 128 del `EAN128` del excel de pedido. El Code 128 del PO **ya no existe**: el cliente lo quitó de su plantilla. `COLOR CODE` en la etiqueta es **solo el código** (`221`); el color completo (`221 DARK COFFEE`) va dentro de la imagen compuesta y en la hoja extra — `AmiPedidoExcel.FilaPedido` da los dos. `REFERENCE` y `ORDER NUMBER` son celdas combinadas en la plantilla nueva (ver más abajo por qué importa para `shrinkToFit`). La dirección de JAPAN se extrae **por su anclaje** en la plantilla, no cogiendo el primer PNG del libro: la plantilla trae dos PNG (el mock de la imagen compuesta y la propia dirección) y coger "el primero" sacaría el equivocado.

**El código de barras nunca se reescala**: se genera ya con la altura de barras que le toca (`CodigoBarrasEan13.png(ean13, proporcion)`) y se pega 1:1 con `Graphics2D.drawImage`; reescalarlo con interpolación lo deja bonito en pantalla e ilegible para un lector físico. La proporción barras:dígitos (5,5:1) sale medida del mock del cliente y deja las barras a ~42% de la altura nominal de un EAN-13 en la maqueta del cliente — **eso solo lo puede verificar un lector físico, ningún test lo cubre**. `CodigoBarrasEan13.png(String)` sin proporción sigue saliendo **byte a byte igual** que antes de esta feature, porque lo usan caminos ya en producción (hay un test que lo fija). La zona muda (`doQuietZone(true)`) es parte del símbolo, no un margen decorativo: sin ella muchos lectores no leen.

**Los dos EAN de las etiquetas de AMI** salen del excel de pedido con **clave exacta** `ARTICLE`+`COLORIS`+`TAILLE`+sufijo de PO (en el fichero real esa clave identifica una sola fila de 151; el color es obligatorio porque hay 59 claves con varios colores). Si no hay fila exacta: aviso y etiqueta sin esos códigos, nunca la fila de otro PO. La talla es la de la **línea líder** de la caja, porque en una caja de cinturones con varias tallas solo cabe un par de EAN. El `EAN128` se pasa a Code 128 **verbatim**, nunca compuesto: lleva dentro el EAN13 y el PO, y en el fichero real hay 8 filas donde eso no cuadra con sus propias columnas — se avisa y se imprime igual, porque es el código del cliente el que espera su escáner. `AmiPedidoRealTest` ancla todo esto contra el fichero real.

**Varios artículos en una caja** (`ArticulosDeCaja`): una caja física puede
llevar varios artículos y la etiqueta ya no los colapsa a la línea líder. Un
artículo es referencia+color en bolsos y referencia+color+**talla** en
cinturones (cada talla es un SKU con su propio EAN-13). En **bolsos** la
etiqueta concatena `REFERENCE`, `COLOR CODE` y `QUANTITY` con `" / "` en el
orden del packing list —`SIZE` sigue siendo `U`— y `AjusteFuente` encoge la
fuente si el texto no cabe (tamaño calculado con suelo de 8 pt **y**,
solo cuando el estilo original no tiene `wrapText` ni la celda está
combinada, `shrinkToFit` como red de seguridad adicional). **Excel ignora
`shrinkToFit` tanto en celdas con `wrapText` como en celdas combinadas.** En
las cuatro plantillas de **APC** el motivo es `wrapText`. En **AMI**, con la
plantilla nueva, la celda `REFERENCE` está **combinada**, así que ahí lo único
que protege el texto es el tamaño calculado de `AjusteFuente` — igual que en
APC, aunque por un motivo distinto. Los **cinturones no cambian de valor** en
`SIZE`/`QUANTITY`: `SIZE` se escribe sin pasar por `AjusteFuente` y nunca
encoge, pero `QUANTITY` sí pasa por él como cualquier caja, así que su fuente
puede encoger si el texto no cabe (`AjusteFuente` se aplica por celda, no por
tipo de caja).
Como en una etiqueta solo cabe un par de EAN, los artículos 2..N van a la hoja
`CODIGOS BARRAS EXTRA` (`HojaCodigosBarrasExtra`, sin plantilla propia, maquetación
en constantes) del mismo libro de la destinación, con un aviso al usuario de que
hay una hoja más que imprimir. Usa **la misma rejilla que las etiquetas de
artículo** (`RejillaEtiquetas`), 10 artículos por A4, con tres columnas por
bloque: caja, EAN-13 y EAN128. Solo AMI: **APC no imprime códigos de barras**,
solo hereda la concatenación.

`EtiquetaArticulo`, `RejillaEtiquetas` y `BloqueEtiquetaArticulo` viven en
`service/etiquetas/` (no en `service/etiquetasarticulo/`) y los comparten los
dos flujos, el de etiquetas de caja y el de etiquetas de artículo: **tocar uno
afecta a los dos**.

**Etiquetas de artículo** (`service/etiquetasarticulo/`): flujo **independiente
del envío** (`/etiquetas-articulo`), su única entrada es el excel de pedido del
cliente. `GeneradorEtiquetasArticuloCliente` es la interfaz, despachada por
clave de cliente; implementado AMI. Una hoja por fila del pedido (= por EAN13),
40 etiquetas idénticas por hoja en una rejilla 4×10 que cabe en un A4, y un
fichero por (tipo, Made in): bolsos MOROCCO, bolsos SPAIN, cinturones
(`UBL*`). **No hay plantilla `.xlsx`**: la maquetación son las constantes de
`RejillaEtiquetas` y `BloqueEtiquetaArticulo` (capa común, `service/etiquetas/`);
`EtiquetasArticuloExcelBuilder` es solo el orquestador. Todo eso está anclado
al fichero real del cliente por `EtiquetasArticuloMaquetacionTest` — POI no
copia el `pageSetup` al clonar hojas, así que heredarla de una plantilla no
servía.

**Procesado de escandallos ICSUITE** (`service/escandallos/`): flujo
**totalmente independiente** del resto (`/escandallos`) — no hay envío, ni
cliente, ni catálogo. Entran N excels de escandallo del ERP y sale **un solo
`.xlsx` con una hoja por escandallo** (`Escandallos ICSUITE.xlsx`). Los
escandallos son PDFs convertidos a excel: **los valores no caen en la columna de
su encabezado** (`Article` en B9 pero los códigos en A10, `Quantitat` en X9 pero
las cantidades en W10) y el número de materiales varía, así que `EscandalloReader`
**no usa coordenadas**: localiza la fila `Article…Quantitat`, anota *todas* sus
columnas con encabezado —también `Preu Ult.` e `Imp. Material`, que son las que
impiden que el precio se lea como cantidad— y asigna cada celda al encabezado más
cercano. Ojo: esos ficheros traen celdas `inlineStr` vacías y POI devuelve `null`
en `getStringCellValue()`. Nombre de hoja = modelo + color (`NombresHoja`), porque
hay un escandallo por color y todos comparten `MODEL`. Sin plantilla `.xlsx`: la
maquetación son constantes de `EscandallosExcelBuilder`.

**Web** (`web/`): asistente de 3 pantallas — `entrada` (pegar JSON o subir fotos) → `revision` (avisos y tabla editable) → `resultados` (descarga individual, ZIP, volcado ERP y etiquetas de caja, todo ya generado). Estado del envío en sesión HTTP (`EnvioEnCurso`).

Los avisos de etiquetas los formatea `AvisoEtiquetas` (`DESTINO: Caja N. hecho. consecuencia`), compartido por los dos generadores a propósito: **el formato de un aviso es una sola decisión, no una por cliente**. Sin vocabulario técnico —"la entrada", nunca "el JSON"—: quien los lee no sabe de qué formato salieron los datos. Las cajas en la revisión se localizan por **posición** (`indicesCaja`), no por número de caja, porque los números pueden repetirse (cajas mixtas).

En la revisión **todos los campos son editables** (`RevisionForm.CajaEditada`, inputs `cajas[i].*`), no solo los pesos: la extracción por fotos puede leer mal cualquier dato y esta es la pantalla donde se corrige. Reglas: un campo que llega **vacío significa "no tocar"** (así que se puede sobrescribir cualquier valor, pero no dejarlo en blanco); los dos pesos siguen saliendo solo en la **línea líder** de cada caja física; un **bulto mixto** (varios artículos o varias tallas, `FilaCaja.bultoMixto`) edita su peso a mano pero **no ofrece el botón "recalcula peso"** (`FilaCaja.puedeRecalcularPeso`), porque ese peso es el de varios artículos juntos y no hay peso por unidad que propagar al resto del modelo — el botón prometía un cálculo que ahí no se puede hacer; corregir el **tamaño** cambia la tara y por eso se re-infiere el envío entero tras cada edición; y editar el **palet** solo recalcula la lista de "sin palet" leyendo el dato — **no** se vuelve a ejecutar `PaletAssignmentService`, cuyos rangos son los del JSON original y machacarían lo tecleado a mano.

La tabla de revisión va **compactada** (`AgrupadorFilasRevision`): un tramo de cajas consecutivas equivalentes se pinta en una sola fila con el rango en la columna CAJA (`4-8`), y esa fila manda un índice por cada caja del tramo, así que lo tecleado se aplica a todas. Regla del criterio de agrupación: **una fila compactada nunca muestra un valor que no sea cierto para todas sus cajas** — cualquier diferencia en referencia, color, PO, cantidad, tamaño, palet, pesos o *talla* parte el grupo, y las cajas mixtas no se compactan. Ojo al escribir tests de esto: con una tara conocida la inferencia rellena el resto de la referencia por su cuenta y **enmascara** un fallo de propagación; usar un tamaño sin tara para probarlo de verdad.

La columna TAMAÑO es un **desplegable** con las taras de `application.yml` ordenadas de mayor a menor **por volumen**, no alfabéticamente (`TaraProperties.tamanosDeMayorAMenor`; `100x40x40` es la caja más grande y como texto iría antes que `40x30x20`). Un tamaño que **no** esté en el catálogo se añade como opción propia marcada `(sin tara)` y ya seleccionada: si solo se ofreciera el catálogo, el navegador elegiría el primero por su cuenta y guardar la fila cambiaría el tamaño del envío en silencio.

Los estáticos se sirven con **hash del contenido en el nombre** (`/estilo-a1b2c3.css`, `spring.web.resources.chain` en `application.yml`, anclado por `RutasTest`). Sin eso el navegador reutiliza el CSS viejo tras cada cambio de estilos y parece que la hoja no se aplica; es un fallo invisible desde el código.

El número de caja de un tramo compactado no se teclea: la fila se **despliega** con el triángulo (`▶`/`▼`), que es un `POST /alternar-fila?destino&indice` — un submit del mismo formulario, así que aplica lo tecleado antes de recargar. Qué filas están desplegadas vive en la **sesión** (`EnvioEnCurso.filasDesplegadasDe`), con clave posicional `(destino, índice de la primera caja del grupo)`; una marca que ya no arranca ningún grupo se ignora. Se hizo por servidor y no con JavaScript **a propósito**: si despliegas, cambias un dato de una caja y vuelves a plegar, `sonEquivalentes` deja las filas separadas por su cuenta y ningún valor se pisa en silencio. `section.destino` no lleva `overflow-x` por el mismo cuidado: convertiría la sección en el contenedor de scroll y rompería las cabeceras sticky.

## Reglas del proyecto

- **Nunca fallar en silencio, nunca bloquear por datos que un humano puede resolver**: los servicios devuelven DTOs de resultado con avisos (`EnvioImportado`, `ResultadoAsignacion`, `ResultadoInferencia`, `ExcelGenerado.cajasPendientes`) en vez de lanzar excepciones. Un excel con pesos pendientes se genera igual, con las celdas en blanco.
- Los pesos son `Double`, no `double`: `null` significa "desconocido" y es lo que dispara la inferencia y las celdas vacías. No cambiar a primitivo.
- El JSON puede traer `pesoBruto` (kg) **opcional** por caja cuando la imagen lo indica; si viene se usa tal cual (y la inferencia solo deriva el neto = bruto − tara), si falta se infiere como siempre. El peso neto nunca viene en el JSON.
- **Un peso por caja física, en su primera línea** (`model/CajaFisica`): una caja puede ocupar varias líneas del JSON (tallas, colores, referencias) pero se pesa una sola vez y ese peso es el de su **línea líder**; las demás no aportan peso y una caja está pendiente si su líder no lo trae. Vale para todos los clientes y para packing lists y etiquetas: **nunca sumar las líneas de una caja**. Ojo a la diferencia entre *dónde vive* el peso y *dónde se escribe*: un bulto compartido por dos artículos sale en **dos packing lists de AMI** (uno por referencia+color) y lleva su peso en **los dos** —es el mismo cartón y uno sin peso no se expide—, pero dentro de cada excel se escribe **una sola vez**, o el `SUM` de la fila de totales lo contaría dos veces. Como el líder puede caer en el excel de otro artículo, `AmiGenerador` resuelve los bultos de la destinación entera **antes** de partir en grupos (`cajaCompartidaPorDosArticulosLlevaElPesoDelBultoEnLosDosExcels` lo ancla). APC y GENERIC no repiten nada: ahí las líneas de un bulto son filas consecutivas del **mismo** fichero y la plantilla real del cliente deja en blanco las de continuación.
- Las plantillas de `src/main/resources/client-packinglist/` tienen filas modelo y coordenadas de las que dependen los builders: **no editar la fila modelo ni los encabezados** de una plantilla sin revisar su builder.
- **Todos los JSON de envío del repo son fixtures generados por IA**, no datos de cliente: los de `src/test/resources/ejemplos/`, el `packing_list_ami_test.json` del `Main` y los recortados dentro de los documentos de `docs/`. Se escribieron sin conocer la realidad del almacén (cajas, unidades, pesos, medidas y palets puestos a ojo) para pruebas visuales del usuario y como fixture. **No deducir de ellos cómo son los datos reales de un cliente ni citarlos como evidencia.** Única excepción parcial: las claves (referencia, color, talla, PO) de `envio-ami-bags-y-belts.json` sí se copiaron del pedido real de AMI, correspondencia que **ningún test verifica**.
- **Los únicos datos reales de cliente son los dos excels de pedido**: `docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx` (AMI, copiado a `src/test/resources/ejemplos/`) y `docs/Etiquetas cajas/APC_PEDIDO_FALL26.xlsx` (APC, copiado también a `src/test/resources/ejemplos/`, lo lee `ApcPedidoExcel`). De los demás `.xlsx` de `docs/` lo real es la **maquetación** (plantillas y salidas del cliente), no el contenido. Ver [src/test/resources/ejemplos/README.md](src/test/resources/ejemplos/README.md).
- `Main.java` se mantiene como prueba manual por petición explícita del usuario; no eliminarlo aunque la web cubra el mismo flujo.
- Los tests instancian los servicios con `new` (JUnit 5 puro) por defecto; solo levantan contexto Spring con `@SpringBootTest` los que necesitan cableado real que no se puede replicar a mano: los de la capa web (mapeo de rutas, redirecciones, MockMvc) y los que verifican los JSON de ejemplo contra el catálogo real de `application.yml` (`EjemplosJsonTest`). Los tests de builders reabren el `.xlsx` generado con POI y comprueban celdas reales — seguir ese patrón.
