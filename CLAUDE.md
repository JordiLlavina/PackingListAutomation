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
- El modo CLAUDE de la pantalla de entrada (fotos → JSON) necesita la variable de entorno `ANTHROPIC_API_KEY`. Sin ella la app arranca igual y el resto de modos funciona (cliente HTTP perezoso).
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
      → excels de etiquetas de caja (opcional: Paso 2 web con archivos extra)
```

Tres capas de modelo, separadas a propósito (ver ARCHITECTURE.md):
1. `model/EnvioInput` — calco literal del JSON de entrada, frágil a propósito; solo `EnvioImportService` lo conoce.
2. `model/CajaData`, `DestinoData`, `PaletData` — dominio: una caja física por objeto; los servicios trabajan solo contra esto.
3. `PackingListData` — vocabulario de la plantilla Excel de un cliente concreto.

**Multi-cliente**: `GeneradorPackingListCliente` es la interfaz; `AmiGenerador`/`AmiExcelBuilder`, `ApcExcelBuilder` y `GenericoExcelBuilder` la implementan, y `PackingListGenerationService` despacha según `ClienteConfig.getPlantilla()` (`TipoPlantilla`: AMI, APC, GENERIC). El catálogo de clientes vive en `application.yml` (bloque `packing-list.clientes`): **añadir un cliente de plantilla GENERIC es solo configuración** (nombre-legal + direccion-entrega), sin tocar Java. Igual con las taras: un tamaño de caja nuevo es una línea en `packing-list.taras`.

**Nombre de los excels de packing list**: APC y GENERIC usan `PKL_<destino>_<factura>.xlsx`, pero **AMI tiene el formato que exige el cliente** y vive en `AmiNombreFichero`: `<fecha envío yyyy.MM.dd>_PUN_<product order>_<referencia>.<color>_<temporada>_<destinación abreviada>.xlsx`. La destinación se abrevia (FRANCE/FRANCIA/PARIS → FR, CHINA, JAPAN) y una destinación desconocida no bloquea: va en mayúsculas y sin espacios. Los campos que falten se omiten en vez de dejar separadores sueltos.

**Etiquetas de caja** (`service/etiquetas/`): estrategia propia `GeneradorEtiquetasCliente` despachada por **clave de cliente** (no por TipoPlantilla); cada implementación declara qué destinaciones soporta y qué archivos extra pide al usuario en la vista `/etiquetas` (Paso 2). Implementado: AMI (China/Japan/France; hoja por destinación, par de etiquetas A4 por caja). Plantillas en `src/main/resources/client-labels/` — misma regla que las de packing list: **no editarlas sin revisar su builder** (`AmiEtiquetasExcelBuilder`/`AmiEtiquetaLayout`, cuyas coordenadas ancla `AmiEtiquetaLayoutTest`).

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

**Web** (`web/`): asistente de 3 pantallas — `entrada` (pegar JSON o subir fotos) → `revision` (avisos, pesos editables, "↻ modelo" propaga un peso a toda su referencia) → `resultados` (descarga individual, ZIP y volcado ERP). Estado del envío en sesión HTTP (`EnvioEnCurso`). Las cajas en la revisión se localizan por **posición** (`indicesCaja`), no por número de caja, porque los números pueden repetirse (cajas mixtas).

La tabla de revisión va **compactada** (`AgrupadorFilasRevision`): un tramo de cajas consecutivas equivalentes se pinta en una sola fila con el rango en la columna CAJA (`4-8`), y esa fila manda un índice por cada caja del tramo, así que el peso tecleado se aplica a todas. Regla del criterio de agrupación: **una fila compactada nunca muestra un valor que no sea cierto para todas sus cajas** — cualquier diferencia en referencia, color, PO, cantidad, tamaño, palet, pesos o *talla* parte el grupo, y las cajas mixtas no se compactan. Ojo al escribir tests de esto: con una tara conocida la inferencia rellena el resto de la referencia por su cuenta y **enmascara** un fallo de propagación; usar un tamaño sin tara para probarlo de verdad.

## Reglas del proyecto

- **Nunca fallar en silencio, nunca bloquear por datos que un humano puede resolver**: los servicios devuelven DTOs de resultado con avisos (`EnvioImportado`, `ResultadoAsignacion`, `ResultadoInferencia`, `ExcelGenerado.cajasPendientes`) en vez de lanzar excepciones. Un excel con pesos pendientes se genera igual, con las celdas en blanco.
- Los pesos son `Double`, no `double`: `null` significa "desconocido" y es lo que dispara la inferencia y las celdas vacías. No cambiar a primitivo.
- El JSON puede traer `pesoBruto` (kg) **opcional** por caja cuando la imagen lo indica; si viene se usa tal cual (y la inferencia solo deriva el neto = bruto − tara), si falta se infiere como siempre. El peso neto nunca viene en el JSON.
- **Un peso por caja física, en su primera línea** (`model/CajaFisica`): una caja puede ocupar varias líneas del JSON (tallas, colores, referencias) pero se pesa una sola vez y ese peso es el de su **línea líder**; las demás no aportan peso y una caja está pendiente si su líder no lo trae. Vale para todos los clientes y para packing lists y etiquetas: **nunca sumar las líneas de una caja**.
- Las plantillas de `src/main/resources/client-packinglist/` tienen filas modelo y coordenadas de las que dependen los builders: **no editar la fila modelo ni los encabezados** de una plantilla sin revisar su builder.
- **Todos los JSON de envío del repo son fixtures generados por IA**, no datos de cliente: los de `src/test/resources/ejemplos/`, el `packing_list_ami_test.json` del `Main` y los recortados dentro de los documentos de `docs/`. Se escribieron sin conocer la realidad del almacén (cajas, unidades, pesos, medidas y palets puestos a ojo) para pruebas visuales del usuario y como fixture. **No deducir de ellos cómo son los datos reales de un cliente ni citarlos como evidencia.** Única excepción parcial: las claves (referencia, color, talla, PO) de `envio-ami-bags-y-belts.json` sí se copiaron del pedido real de AMI, correspondencia que **ningún test verifica**.
- **Los únicos datos reales de cliente son los dos excels de pedido**: `docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx` (AMI, copiado a `src/test/resources/ejemplos/`) y `docs/Etiquetas cajas/APC_PEDIDO_FALL26.xlsx` (APC, todavía sin usar en el código). De los demás `.xlsx` de `docs/` lo real es la **maquetación** (plantillas y salidas del cliente), no el contenido. Ver [src/test/resources/ejemplos/README.md](src/test/resources/ejemplos/README.md).
- `Main.java` se mantiene como prueba manual por petición explícita del usuario; no eliminarlo aunque la web cubra el mismo flujo.
- Los tests instancian los servicios con `new` (JUnit 5 puro) por defecto; solo levantan contexto Spring con `@SpringBootTest` los que necesitan cableado real que no se puede replicar a mano: los de la capa web (mapeo de rutas, redirecciones, MockMvc) y los que verifican los JSON de ejemplo contra el catálogo real de `application.yml` (`EjemplosJsonTest`). Los tests de builders reabren el `.xlsx` generado con POI y comprueban celdas reales — seguir ese patrón.
