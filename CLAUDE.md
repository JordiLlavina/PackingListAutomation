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

**Etiquetas de caja** (`service/etiquetas/`): estrategia propia `GeneradorEtiquetasCliente` despachada por **clave de cliente** (no por TipoPlantilla); cada implementación declara qué destinaciones soporta y qué archivos extra pide al usuario en la vista `/etiquetas` (Paso 2). Implementado: AMI (China/Japan/France; hoja por destinación, par de etiquetas A4 por caja, barcode Code 128 del PO del excel de pedido EAN). Plantillas en `src/main/resources/client-labels/` — misma regla que las de packing list: **no editarlas sin revisar su builder** (`AmiEtiquetasExcelBuilder`/`AmiEtiquetaLayout`).

**Etiquetas de artículo** (`service/etiquetasarticulo/`): flujo **independiente
del envío** (`/etiquetas-articulo`), su única entrada es el excel de pedido del
cliente. `GeneradorEtiquetasArticuloCliente` es la interfaz, despachada por
clave de cliente; implementado AMI. Una hoja por fila del pedido (= por EAN13),
40 etiquetas idénticas por hoja en una rejilla 4×10 que cabe en un A4, y un
fichero por (tipo, Made in): bolsos MOROCCO, bolsos SPAIN, cinturones
(`UBL*`). **No hay plantilla `.xlsx`**: la maquetación son constantes en
`EtiquetasArticuloExcelBuilder`, ancladas al fichero real del cliente por
`EtiquetasArticuloMaquetacionTest` — POI no copia el `pageSetup` al clonar
hojas, así que heredarla de una plantilla no servía.

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

**Web** (`web/`): asistente de 3 pantallas — `entrada` (pegar JSON o subir fotos) → `revision` (avisos, pesos editables, "↻ modelo" propaga un peso a toda su referencia) → `resultados` (descarga individual, ZIP y volcado ERP). Estado del envío en sesión HTTP (`EnvioEnCurso`). Las cajas en la revisión se localizan por **posición** (`indiceCaja`), no por número de caja, porque los números pueden repetirse (cajas mixtas).

## Reglas del proyecto

- **Nunca fallar en silencio, nunca bloquear por datos que un humano puede resolver**: los servicios devuelven DTOs de resultado con avisos (`EnvioImportado`, `ResultadoAsignacion`, `ResultadoInferencia`, `ExcelGenerado.cajasPendientes`) en vez de lanzar excepciones. Un excel con pesos pendientes se genera igual, con las celdas en blanco.
- Los pesos son `Double`, no `double`: `null` significa "desconocido" y es lo que dispara la inferencia y las celdas vacías. No cambiar a primitivo.
- El JSON puede traer `pesoBruto` (kg) **opcional** por caja cuando la imagen lo indica; si viene se usa tal cual (y la inferencia solo deriva el neto = bruto − tara), si falta se infiere como siempre. El peso neto nunca viene en el JSON.
- **Un peso por caja física, en su primera línea** (`model/CajaFisica`): una caja puede ocupar varias líneas del JSON (tallas, colores, referencias) pero se pesa una sola vez y ese peso es el de su **línea líder**; las demás no aportan peso y una caja está pendiente si su líder no lo trae. Vale para todos los clientes y para packing lists y etiquetas: **nunca sumar las líneas de una caja**.
- Las plantillas de `src/main/resources/client-packinglist/` tienen filas modelo y coordenadas de las que dependen los builders: **no editar la fila modelo ni los encabezados** de una plantilla sin revisar su builder.
- `Main.java` se mantiene como prueba manual por petición explícita del usuario; no eliminarlo aunque la web cubra el mismo flujo.
- Los tests instancian los servicios con `new` (JUnit 5 puro) por defecto; solo levantan contexto Spring con `@SpringBootTest` los que necesitan cableado real que no se puede replicar a mano: los de la capa web (mapeo de rutas, redirecciones, MockMvc) y los que verifican los JSON de ejemplo contra el catálogo real de `application.yml` (`EjemplosJsonTest`). Los tests de builders reabren el `.xlsx` generado con POI y comprueban celdas reales — seguir ese patrón.
