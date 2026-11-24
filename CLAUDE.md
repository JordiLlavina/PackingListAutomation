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
mvn verify -Pe2e                              # recorrido de humo de la PWA con Playwright: arranca el jar en el 8089 con base en memoria (la 1ª vez se baja Chromium)
mvn test -Dskip.frontend=true                 # solo Java: se salta Node, npm ci, el build de la PWA y Vitest (~30 s menos por build)
MODELOS_ADMIN_PASSWORD=prueba mvn spring-boot:run   # con la app de modelos: http://localhost:8080/app/ como admin (solo si ./datos no tiene usuarios)
```

- **La app de modelos** (`/app/`, ver `.claude/rules/app-modelos.md`): en PowerShell, `$env:MODELOS_ADMIN_PASSWORD="prueba"; mvn spring-boot:run "-Dskip.frontend=true"`. **En PowerShell toda opción `-D` con un punto va entre comillas**: sin ellas PowerShell la parte por el punto y Maven falla con `Unknown lifecycle phase ".frontend=true"` (en Git Bash y cmd no hace falta). Para probar cámara, micrófono y push desde una tablet de la red sin HTTPS: en su Chrome, `chrome://flags/#unsafely-treat-insecure-origin-as-secure` con `http://<ip-del-pc>:8080`. Las claves VAPID del push se generan con `GenerarClavesVapid` (comando en el README).
- El test end-to-end `flujoCompletoGeneraExcelsAbribles` deja excels reales en `target/` para inspección manual.
- El modo CLAUDE de la pantalla de entrada (fotos o PDFs → JSON) necesita la variable de entorno `ANTHROPIC_API_KEY`. Sin ella la app arranca igual y el resto de modos funciona (cliente HTTP perezoso).
- **Windows**: matar `mvn spring-boot:run` no mata el proceso `java` hijo y el 8080 queda ocupado. Liberarlo: `netstat -ano | findstr :8080` + `taskkill /F /PID <pid>`.
- La base de datos vive en `./datos/` y la crea **Flyway**; si al arrancar sale `Validate failed: Migrations have failed validation`, es una base de otra versión de la V1 (o anterior a Flyway): apartar la carpeta `datos` y volver a arrancar. Solo se pierde lo aprendido (las taras se resiembran).
- La portada es `/menu`; el asistente de packing list vive en `/packing-list` y `/` redirige al menú. La app de modelos vive en `/app/` (su tarjeta del menú lleva `href="/app/"` plano y no `@{/app/}`: ver `.claude/rules/app-modelos.md`); detrás de nginx, `https://modelos.puntotres.com/` irá a `/app/` y `/menu` seguirá siendo la portada vieja.
- `PackingPuntotresDocBuilderTest` deja `target/Packing Puntotres ejemplo.docx` para abrirlo en Word y revisar la maquetación a ojo.
- `EscandallosFlujoRealTest` deja `target/Escandallos ICSUITE.xlsx` con los dos escandallos reales, para compararlo a mano con el ejemplo de `docs/Procesado Escandallos ICSUITE/`.
- `OrdenCorteExcelBuilderTest` y `FotosCorteDocBuilderTest` dejan `target/Ordenes de corte ejemplo.xlsx` y `target/Fotos ejemplo.docx`, con imágenes de relleno, para revisar la maquetación de los documentos del corte en Excel y en Word.
- `InfoEmbalajeDocBuilderTest` deja `target/Info embalaje ejemplo.docx` (el caso más cargado: 6 destinaciones, 10 líneas de reparto en la columna CAJAS —cuatro destinaciones en dos filas—, 4 de comentarios) para abrirlo en Word; exportado a mano con Word por COM queda al lado `target/Info embalaje ejemplo.pdf`, que es como se comprobó que cabe en un A4.

## Documentación que ya existe (y su estado)

- [README.md](README.md) — uso, formato del JSON de entrada, taras.
- [ARCHITECTURE.md](ARCHITECTURE.md) — recorrido interno del pipeline y decisiones de diseño. **Lectura recomendada antes de tocar los servicios.**
- ⚠️ Ambos están **parcialmente desactualizados**: dicen que no hay capa web ni endpoint y que solo existe el builder AMI. En realidad ya existen la web completa (`web/`), la extracción por imágenes (`ClaudeEnvioExtractionService`), los builders APC y genérico, y el volcado ERP. Lo que cuentan del pipeline de dominio sigue siendo válido.
- [docs/Packing Lists/campos-json-por-cliente.md](docs/Packing%20Lists/campos-json-por-cliente.md) — campos del JSON por cliente; en `docs/Packing Lists/` hay ejemplos `.xlsx` completos de cada plantilla.
- [docs/Procesado Escandallos ICSUITE/](docs/Procesado%20Escandallos%20ICSUITE/) — dos escandallos reales del ERP (`ULL*.xlsx`) y el excel de salida de ejemplo; los dos escandallos están copiados en `src/test/resources/ejemplos/escandallos/`.
- [docs/superpowers/specs/2026-07-29-procesado-escandallos-icsuite-design.md](docs/superpowers/specs/2026-07-29-procesado-escandallos-icsuite-design.md) — diseño de la feature de escandallos.
- [docs/superpowers/specs/2026-11-01-catalogo-modelos-design.md](docs/superpowers/specs/2026-11-01-catalogo-modelos-design.md) — diseño del catálogo de modelos (la memoria única) y su pantalla `/catalogo`; el plan ejecutado está en `docs/superpowers/plans/2026-11-02-catalogo-modelos.md`.
- [docs/superpowers/specs/2026-11-02-info-embalaje-taller-design.md](docs/superpowers/specs/2026-11-02-info-embalaje-taller-design.md) — diseño de la hoja de info de embalaje para el taller; el plan ejecutado está en `docs/superpowers/plans/2026-11-02-info-embalaje-taller.md`. Las fotos de las hojas que se escribían a mano están en `docs/Info Embalaje Taller/`.
- [docs/superpowers/specs/2026-11-15-biblioteca-pieles-design.md](docs/superpowers/specs/2026-11-15-biblioteca-pieles-design.md) — diseño de la biblioteca de pieles (`/biblioteca-pieles`): las decisiones del usuario y los límites conocidos.
- [docs/superpowers/specs/2026-11-23-carga-modelos-en-bateria-design.md](docs/superpowers/specs/2026-11-23-carga-modelos-en-bateria-design.md) — diseño de la carga de modelos en batería (`/carga-modelos`): qué decidió el usuario y qué el asistente; el plan ejecutado está en `docs/superpowers/plans/2026-11-23-carga-modelos-en-bateria.md`.
- [docs/superpowers/specs/2026-11-24-pantalla-clientes-design.md](docs/superpowers/specs/2026-11-24-pantalla-clientes-design.md) — diseño de la pantalla de clientes (`/clientes`): alta, corrección y eliminación con contraseña.
- [TODO](TODO) — lista de pendientes que mantiene **el usuario**; consultarla al empezar, no reescribirla por tu cuenta.
- [SESSION_HANDOFF.md](SESSION_HANDOFF.md) — traspaso de la última sesión ("stop the session" lo regenera, no es un log).

## ⚠️ Números de pedido y destinaciones por cliente — LEER ANTES DE TOCAR NADA DE ESTO

**Esta sección es de lectura obligatoria antes de escribir código que toque
destinaciones de cliente o números de pedido.** No se deduce del código ni de
los nombres de las clases, y equivocarse aquí escribe un número de pedido
erróneo en un documento que va al cliente.

Los dos hechos, **verificados contra los ficheros de pedido reales**
(`EAN PUNTOTRES H26.xlsx` y `APC_PEDIDO_FALL26.xlsx`), no supuestos:

| | AMI | APC |
|---|---|---|
| Cómo lo llama el cliente | **PO** (`07704 CH`) | **Document d'achat** (`4100128863`) |
| Granularidad del número | **UNO por `referencia` + `destinación`** | **UNO por `referencia` + `color` + `destinación`** |
| Por destinación sola | **VARIOS** (12 en CHINA, 15 en JAPAN, 25 en PARIS) | **VARIOS** (de 4 en Australia a 16 en Wholesale) |
| ¿Se puede propagar el número entre colores de una referencia? | **SÍ** | **NO, nunca** |

**Lo que esto permite y lo que prohíbe:**

- En **AMI** el PO no depende del color: todos los colores y tallas de una
  referencia que van a la misma destinación comparten PO. Por eso se puede
  rellenar solo en las filas que el excel de pedido no reconoce, copiándolo de
  una fila hermana de la misma referencia (`ObjetivosPedidoAmi`). Medido: 52
  claves `referencia+destinación` en el fichero real, **ninguna** con más de un
  PO; y lo mismo bajando a `referencia+color+talla+destinación` (151 claves,
  ninguna con más de uno).
- En **APC** eso sería un error: un `Document d'achat` es una destinación, un
  artículo **y un color**, con una fila por talla, así que dos colores de la
  misma referencia a la misma destinación llevan números distintos. Copiar el
  de un color al otro pondría en el packing list un pedido que no es el suyo.
  Por eso `ObjetivosPedidoApc` cruza por el `CODE` de tres dígitos de cada fila
  del taller y **no propaga nada**.

**El error a evitar** es leer "un PO por destinación" como "un PO para toda la
destinación". Es falso en los dos clientes: una destinación recibe decenas de
pedidos, uno por artículo. Lo que es único es el número **dentro de una
referencia** (AMI) o **dentro de una referencia y un color** (APC).

Si algún día hay que volver a comprobarlo, se mide leyendo los dos excels de
pedido de `src/test/resources/ejemplos/` y contando claves distintas; no se
deduce de los JSON de ejemplo, que son fixtures inventados.

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

**Multi-cliente**: `GeneradorPackingListCliente` es la interfaz; `AmiGenerador`/`AmiExcelBuilder`, `ApcExcelBuilder` y `GenericoExcelBuilder` la implementan, y `PackingListGenerationService` despacha según `ClienteConfig.getPlantilla()` (`TipoPlantilla`: AMI, APC, GENERIC). El catálogo de clientes es `CatalogoClientes`: AMI y APC (los **programados**) siguen en `application.yml` (`packing-list.clientes`) y los de plantilla GENERIC viven en la tabla `cliente` y **se dan de alta desde la pantalla `/clientes`**, sin tocar Java ni el yml (ver `.claude/rules/clientes.md`). Inyectar siempre `CatalogoClientes`, nunca `ClientesProperties`, que solo ve el yml. Las taras igual: se pesan en `/taras`.

**Nombre de los excels de packing list**: APC y GENERIC usan `PKL_<destino>_<factura>.xlsx`, pero **AMI tiene el formato que exige el cliente** y vive en `AmiNombreFichero`: `<fecha envío yyyy.MM.dd>_PUN_<product order>_<referencia>.<color>_<temporada>_<destinación abreviada>.xlsx`. La destinación se abrevia (FRANCE/FRANCIA/PARIS → FR, CHINA, JAPAN) y una destinación desconocida no bloquea: va en mayúsculas y sin espacios. Los campos que falten se omiten en vez de dejar separadores sueltos.

## Avisos que valen para todo

Estaban dentro de la sección de una función, pero valen para cualquier cambio.

- **Ojo con el compilador del IDE**: más de una vez ha dejado en `target/` una clase a medio compilar ("Unresolved compilation problems") que Maven da por buena, y los tests fallan en cascada sin motivo. `mvn clean` y repetir.

- **Flyway.** `V1__esquema_inicial.sql` era el esquema final cuando la aplicación no estaba desplegada y la base de `./datos` se apartó y se creó de cero (una base anterior a Flyway, o creada con otra V1, no arranca: `Validate failed`). A partir de que la aplicación esté en uso, **cualquier cambio de esquema va en una migración nueva con su migración de datos: nada de lo que se recuerda se pierde**. `V3` (dos excels por temporada) es la primera con datos y `MigracionTemporadaDosExcelsTest` la prueba sobre una base llevada hasta la V2: es el patrón para las siguientes. El perfil `test` usa una base en memoria con nombre aleatorio por contexto (`${random.uuid}`) y `CASE_INSENSITIVE_IDENTIFIERS=TRUE` (Flyway crea `flyway_schema_history` entrecomillada y en minúsculas; nuestro SQL sin entrecomillar sale en mayúsculas). Ojo al escribir tests: la base es **la misma para toda la suite** y ya no hay `create-drop` que la vacíe por accidente al levantar otro contexto, así que cada test usa sus propias claves; dos clases que guarden la misma temporada o la misma piel se pisan.

- Para probar estas pantallas sin abrirlas a mano se usó **Playwright con el Chrome del equipo**: `frontend/node/node.exe` + `frontend/node_modules/playwright-core`, `chromium.launch({ channel: 'chrome' })`, contra una instancia arrancada en otro puerto con base en memoria (`mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8091 --spring.datasource.url=jdbc:h2:mem:prueba;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE"`). `setInputFiles` sobre el input `webkitdirectory` acepta una carpeta.

Los estáticos se sirven con **hash del contenido en el nombre** (`/estilo-a1b2c3.css`, `spring.web.resources.chain` en `application.yml`, anclado por `RutasTest`). Sin eso el navegador reutiliza el CSS viejo tras cada cambio de estilos y parece que la hoja no se aplica; es un fallo invisible desde el código.

## Detalle de cada función: `.claude/rules/`

El detalle de cada función vive en un fichero de `.claude/rules/` que **se carga solo cuando se lee o se edita un fichero de sus `paths:`** (la lista va en su cabecera). Buscar con grep sin abrir nada **no** lo carga: antes de cambiar algo de un tema, abrir su fichero de reglas si no está ya en el contexto.

| Fichero | Tema |
|---|---|
| [packing-list.md](.claude/rules/packing-list.md) | Packing list por cliente: AMI, APC, palets, facturas, taras y memoria |
| [revision.md](.claude/rules/revision.md) | Pantallas del asistente de packing list: entrada, revisión y resultados |
| [extraccion-fotos.md](.claude/rules/extraccion-fotos.md) | Extracción del envío desde fotos y PDFs con Claude |
| [etiquetas.md](.claude/rules/etiquetas.md) | Etiquetas de caja, de palet y de artículo (producción y SMS) |
| [escandallos.md](.claude/rules/escandallos.md) | Procesado de escandallos ICSUITE |
| [corte.md](.claude/rules/corte.md) | Documentos del Corte (producción) y hojas de corte de SMS |
| [embalaje.md](.claude/rules/embalaje.md) | Etiqueta Info de Embalaje para Taller |
| [taller.md](.claude/rules/taller.md) | Entrada por packing list de taller y conversión de su excel con IA |
| [catalogo.md](.claude/rules/catalogo.md) | Catálogo de modelos, temporadas guardadas y Colores y Pieles de Cliente |
| [app-modelos.md](.claude/rules/app-modelos.md) | App de modelos y modificaciones (taller y despacho, PWA en /app/) |
| [carga-modelos.md](.claude/rules/carga-modelos.md) | Carga de modelos en batería |
| [biblioteca-pieles.md](.claude/rules/biblioteca-pieles.md) | Biblioteca de pieles |
| [clientes.md](.claude/rules/clientes.md) | Pantalla de clientes: alta, corrección y eliminación |

Cómo se cruzan: el taller usa los destinos padre de APC, las taras y la memoria de `packing-list.md`; etiquetas y revisión dependen también de `packing-list.md`; corte, carga de modelos y app de modelos escriben en el catálogo (`catalogo.md`). Cada fichero lo dice en su primera línea.

**Dónde documentar**: lo transversal (comandos, reglas del proyecto, números de pedido, arquitectura del pipeline) va aquí; **todo lo de una función va a su fichero de `.claude/rules/`**, y una función nueva estrena el suyo con sus `paths:` y una línea en esta tabla. Así este fichero no vuelve a crecer.

## Reglas del proyecto

- **Nunca fallar en silencio, nunca bloquear por datos que un humano puede resolver**: los servicios devuelven DTOs de resultado con avisos (`EnvioImportado`, `ResultadoAsignacion`, `ResultadoInferencia`, `ExcelGenerado.cajasPendientes`) en vez de lanzar excepciones. Un excel con pesos pendientes se genera igual, con las celdas en blanco.
- Los pesos son `Double`, no `double`: `null` significa "desconocido" y es lo que dispara la inferencia y las celdas vacías. No cambiar a primitivo.
- El JSON puede traer `pesoBruto` (kg) **opcional** por caja cuando la imagen lo indica; si viene se usa tal cual (y la inferencia solo deriva el neto = bruto − tara), si falta se infiere como siempre. El peso neto nunca viene en el JSON.
- **`medidaCaja` es obligatoria en el papel pero puede faltar de verdad**: se escribe una sola vez por grupo de cajas, a veces de lado en el margen, y el prompt prohíbe copiar la del grupo vecino (una medida inventada acaba en el volumen declarado de un envío real), así que omitirla es la respuesta correcta del modelo. Una medida ausente **no bloquea**: `VolumenUtil.volumenTotalM3` la salta —la caja cuenta como cartón pero no suma volumen— y el desglose la rotula `?` en vez de escribir `null` en una celda que lee el cliente. Una medida **presente pero ilegible** (`60x40`) sí lanza: ahí no hay nada que interpretar. El aviso vive en `WeightInferenceService` y **no** en el importador, porque la revisión reinfiere tras cada edición pero no reimporta: solo así desaparece al elegir el tamaño. Y por eso mismo distingue "caja sin medida" (se arregla en la columna TAMAÑO) de "cartón sin pesar" (se arregla en la pantalla **`/taras`**, no en el yml: la tabla de taras vive en la base de datos desde que existe esa pantalla): mandar al usuario a editar un fichero por un dato que se teclea en pantalla es peor que no avisar.
- **Un peso por caja física, en su primera línea** (`model/CajaFisica`): una caja puede ocupar varias líneas del JSON (tallas, colores, referencias) pero se pesa una sola vez y ese peso es el de su **línea líder**; las demás no aportan peso y una caja está pendiente si su líder no lo trae. Vale para todos los clientes y para packing lists y etiquetas: **nunca sumar las líneas de una caja**. Ojo a la diferencia entre *dónde vive* el peso y *dónde se escribe*: un bulto compartido por dos artículos sale en **dos packing lists de AMI** (uno por referencia+color) y lleva su peso en **los dos** —es el mismo cartón y uno sin peso no se expide—, pero dentro de cada excel se escribe **una sola vez**, o el `SUM` de la fila de totales lo contaría dos veces. Como el líder puede caer en el excel de otro artículo, `AmiGenerador` resuelve los bultos de la destinación entera **antes** de partir en grupos (`cajaCompartidaPorDosArticulosLlevaElPesoDelBultoEnLosDosExcels` lo ancla). APC y GENERIC no repiten nada: ahí las líneas de un bulto son filas consecutivas del **mismo** fichero y la plantilla real del cliente deja en blanco las de continuación.
- Las plantillas de `src/main/resources/client-packinglist/` tienen filas modelo y coordenadas de las que dependen los builders: **no editar la fila modelo ni los encabezados** de una plantilla sin revisar su builder.
- **Todos los JSON de envío del repo son fixtures generados por IA**, no datos de cliente: los de `src/test/resources/ejemplos/`, el `packing_list_ami_test.json` del `Main` y los recortados dentro de los documentos de `docs/`. Se escribieron sin conocer la realidad del almacén (cajas, unidades, pesos, medidas y palets puestos a ojo) para pruebas visuales del usuario y como fixture. **No deducir de ellos cómo son los datos reales de un cliente ni citarlos como evidencia.** Única excepción parcial: las claves (referencia, color, talla, PO) de `envio-ami-bags-y-belts.json` sí se copiaron del pedido real de AMI, correspondencia que **ningún test verifica**.
- **Los únicos datos reales de cliente son los excels de pedido**: `docs/Etiquetas cajas/AMI documentos test/EAN PUNTOTRES H26.xlsx` y `EAN PUNTOTRES SS27.xlsx` (AMI, dos temporadas con formatos distintos, copiados a `src/test/resources/ejemplos/`) y `docs/Etiquetas cajas/APC documentos test/APC_PEDIDO_FALL26.xlsx` y `LCT_Spring 27_Puntotres.XLSX` (APC, copiados también a `src/test/resources/ejemplos/`, el segundo como `APC_PEDIDO_SPRING27.xlsx`; los lee `ApcPedidoExcel`). De los demás `.xlsx` de `docs/` lo real es la **maquetación** (plantillas y salidas del cliente), no el contenido. Ver [src/test/resources/ejemplos/README.md](src/test/resources/ejemplos/README.md).
- `Main.java` se mantiene como prueba manual por petición explícita del usuario; no eliminarlo aunque la web cubra el mismo flujo.
- Los tests instancian los servicios con `new` (JUnit 5 puro) por defecto; solo levantan contexto Spring con `@SpringBootTest` los que necesitan cableado real que no se puede replicar a mano: los de la capa web (mapeo de rutas, redirecciones, MockMvc) y los que verifican los JSON de ejemplo contra el catálogo real de `application.yml` (`EjemplosJsonTest`). Los tests de builders reabren el `.xlsx` generado con POI y comprueban celdas reales — seguir ese patrón.
- **La tabla de taras es un dato del almacén, no una constante del programa**: entran tamaños nuevos y los pesos se corrigen a medida que se pesa cada cartón (los que aún no se han pesado están puestos a `0.01`, y el programa tiene que funcionar igual); a lo que se pesa se le suman además los **separadores de cartón** de `packing-list.separadores-carton`, que sí siguen en el yml. Por eso **ningún test fija un valor de `application.yml`**: `PackingListControllerTest` declara las suyas en `@SpringBootTest(properties = ...)` porque lo que comprueba es la aritmética de la inferencia, y `PackingListApplicationTest` comprueba que el yml se enlaza y que las claves se normalizan, nunca cuánto pesa un cartón. Escribir `assertEquals(0.6, ...)` contra el yml convierte pesar una caja en un cambio de código y deja media docena de tests en rojo por algo que no es un fallo.
