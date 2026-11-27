# Corte de SMS con IA, novedades al sistema y Hoja de corte — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el corte de SMS funcione para todos los clientes (APC con su lector, el resto leído con Claude y revisado), que lo nuevo de un SMS se pueda meter en el catálogo con un botón, y una «Hoja de corte» de un modelo con histórico.

**Architecture:** Servicios Spring sin estado en `service/corte/` (lector IA, novedades, hoja de corte), estado de sesión en `CorteSmsEnCurso`, pantallas Thymeleaf con un script pequeño cada una. La ficha sigue siendo `OrdenCorteExcelBuilder`. El histórico copia el patrón de `hoja_produccion` (snapshot JSON + foto) en una tabla nueva con migración `V18`.

**Tech Stack:** Java 17, Spring Boot 3.5, Thymeleaf, Apache POI, Jackson, SDK Java de Anthropic (`claude-opus-5-5`), Flyway/H2, JUnit 5 + MockMvc.

**Spec:** `docs/superpowers/specs/2026-11-26-corte-sms-ia-y-hoja-de-corte-design.md`

## Global Constraints

- Modelo de IA `claude-opus-5-5`, streaming, salida estructurada con esquema, esfuerzo HIGH, cliente HTTP perezoso (sin `ANTHROPIC_API_KEY` la app arranca).
- Nunca bloquear por datos que un humano puede resolver: la alerta de otro cliente avisa, no corta.
- APC no cambia de flujo ni de lector (`ApcSmsExcel`).
- «Entrar en el Sistema» nunca pisa lo que ya hay en el catálogo; solo da de alta y rellena huecos.
- Un modelo nuevo de SMS entra en etapa Muestras, con nombre obligatorio, `Modelos.darDeAlta` antes que sus artículos.
- Hoja de corte: 1 piel principal, 1 forro, hasta 4 combinaciones, cada una con color; una ficha por línea de color; histórico sin numeración; no escribe en el catálogo.
- Cambio de esquema solo en una migración nueva (`V18__hoja_corte.sql`), SQL común a H2 y PostgreSQL.
- Código en español (clases en inglés si ya lo son), tests con `new` salvo los web.

## Review Focus

- Claude devuelve una línea con `muestras` null/0 o sin referencia: se trae desmarcada y generar no la usa; una incluida sin referencia/muestras vuelve a la revisión con el error (test en Task 3).
- Excel con imágenes ancladas en otra fila que la que dice Claude, o PDF: la ficha cae al catálogo o sale sin imagen con aviso, nunca falla (test en Task 1).
- «Entrar todo» con un modelo sin nombre: no entra nada y se dice cuál (test en Task 4).
- Un modelo/piel/color que alguien da de alta entre que se calcula la novedad y se pulsa: no se duplica ni se pisa (test en Task 4).
- Hoja de corte con líneas vacías intercaladas o sin ninguna con cantidad: las vacías se ignoran; sin ninguna, 400 sin guardar (test en Task 5).

---

### Task 1: Lector de SMS con IA

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/VolcadoExcel.java` (el `textoDelLibro`/`volcarHoja`/`anotar`/`valorDe`/`letraDe`/`fondoDe` sacados de `ConversionFormatoTallerService`, que pasa a llamarlo)
- Create: `src/main/java/com/puntotres/packinglist/service/corte/ImagenesPorFila.java` (`recorte`, `recortar`, `imagen` de `ApcSmsExcel`, que pasa a usarlo)
- Create: `src/main/java/com/puntotres/packinglist/service/corte/LectorSmsIa.java`, `LecturaSmsIa.java`
- Test: `src/test/java/com/puntotres/packinglist/service/corte/LectorSmsIaTest.java`

**Interfaces:**
- Produces:
  - `VolcadoExcel.texto(byte[] excel, String titulo, int maxFilasPorHoja, int maxColumnas) throws IOException → String`
  - `ImagenesPorFila.de(byte[] excel, List<String> avisos) → Map<String /*hoja*/, Map<Integer /*fila Excel desde 1*/, List<Imagen>>>`
  - `record LecturaSmsIa(String clienteDetectado, Boolean esDelCliente, String motivoCliente, Boolean esPedidoDeSms, String motivoSms, List<Linea> lineas, List<String> avisos)` con `record Linea(String hoja, Integer fila, String referencia, String modelo, String nombreModelo, String codigoColor, String descripcionColor, String piel, String colorPiel, String forro, String colorForro, List<Combinacion> combinaciones, Integer muestras, String comentario)` y `record Combinacion(String nombre, String color)`.
  - `LectorSmsIa.leer(byte[] fichero, String nombreFichero, String claveCliente, String nombreCliente) → LecturaSmsIa` (lanza `LectorSmsIa.LecturaFallida` con mensaje en español). `LectorSmsIa.parsear(String json)` package-private. `LectorSmsIa.peticionPara(...)` package-private.

- [ ] Test `parseaLaRespuestaConLineasYCliente` (JSON de ejemplo → campos), `peticionDeExcelLlevaElVolcadoYElCliente`, `peticionDePdfLlevaBloqueDeDocumento`, `imagenesSeAgrupanPorHojaYFila` (excel POI con una imagen anclada en la fila 3 → `de(...).get("H").get(3)` tiene 1).
- [ ] Extraer `VolcadoExcel` e `ImagenesPorFila`; `ConversionFormatoTallerServiceTest` y `ApcSmsExcelTest` siguen en verde.
- [ ] Implementar `LectorSmsIa` (esquema con números nulables, textos ""; PDF por `Base64PdfSource`; prompt: leer y separar, no inventar, qué no es del corte).
- [ ] `mvn test -Dskip.frontend=true -Dtest='LectorSmsIaTest,ConversionFormatoTallerServiceTest,ApcSmsExcel*Test'` en verde. Commit.

### Task 2: Generar SMS desde líneas revisadas

**Files:**
- Modify: `service/corte/CorteSmsService.java` — `admite` → `tieneLector`; nuevo `generarDesdeRevision`.
- Create: `service/corte/LineaSmsRevisada.java`
- Test: `CorteSmsServiceTest` (añadir)

**Interfaces:**
- Produces:
  - `record LineaSmsRevisada(boolean incluir, String hoja, Integer fila, String referencia, String nombreModelo, String codigoColor, String descripcionColor, String piel, String colorPiel, String forro, String colorForro, List<PielCorte> combinaciones, Integer muestras)` con `static List<LineaSmsRevisada> de(LecturaSmsIa)` (incluir = muestras > 0 y referencia no vacía) y `List<String> errores()` ("falta la referencia", "faltan las muestras").
  - `CorteSmsService.tieneLector(String clave) → boolean` (solo APC); `CorteSmsService.generarDesdeRevision(String claveCliente, String nombreCliente, String temporada, List<LineaSmsRevisada> lineas, Map<String, Map<Integer, List<Imagen>>> imagenes) → ResultadoSms`. El modelo sale de `documentosCorte.reglasDeProduccion(clave).partir(referencia).modelo()`.
  - `ResultadoSms` gana `List<LineaSms> lineas` (las que salieron), para las novedades.

- [ ] Tests: `generaUnaFichaPorLineaIncluida`, `lineaSinImagenUsaLaDeOtraFilaDelModeloYLuegoElCatalogo`, `lineasDesmarcadasNoSalen`.
- [ ] Implementar. Verde. Commit.

### Task 3: Pantallas del SMS (entrada, revisión, alerta)

**Files:**
- Modify: `web/CorteSmsController.java`, `web/CorteSmsEnCurso.java`, `templates/documentos-corte-sms.html`, `templates/documentos-corte-sms-resultados.html`
- Create: `templates/documentos-corte-sms-revision.html`, `static/corte-sms-revision.js`, `web/RevisionSmsForm.java`
- Test: `web/CorteSmsControllerTest.java` (añadir; `LectorSmsIa` con `@MockitoBean`)

**Interfaces:**
- `POST /documentos-corte/sms/generar`: APC → como hoy; otros → `LectorSmsIa.leer`, guarda en `CorteSmsEnCurso` (lectura, líneas, imágenes, nombre fichero) y redirige a `/documentos-corte/sms/revision`.
- `GET /documentos-corte/sms/revision`, `POST /documentos-corte/sms/revision` (form `filas[i].*` + `filas[i].combinaciones[j].nombre/color`, `_filas[i].incluir`) → genera y redirige a resultados; con errores, vuelve a revisión con ellos.
- La alerta: `alertaCliente` = «Este documento parece de X, no de Y: motivo» si `esDelCliente == false`; `alertaSms` si `esPedidoDeSms == false`. Clase `alerta aviso-fuerte` a la vista.

- [ ] Tests: `clienteSinLectorVaALaRevision`, `laRevisionEnsenaLaAlertaDeOtroCliente`, `generarDesdeLaRevisionDescarga`, `filaIncluidaSinMuestrasVuelveALaRevision`, `apcSigueDirecto`, ningún `<option disabled>` en la entrada.
- [ ] Implementar. Verde. Commit.

### Task 4: Novedades — entrar en el sistema

**Files:**
- Create: `service/corte/NovedadesSms.java` (bean), `web/NovedadesSmsController.java`, fragmento `templates/documentos-corte-sms-novedades.html`, `static/novedades-sms.js`
- Modify: `documentos-corte-sms-resultados.html` (incluir fragmento), `CorteSmsEnCurso` (guarda las novedades)
- Test: `service/corte/NovedadesSmsTest.java` (`@SpringBootTest`, base en memoria), `web/NovedadesSmsControllerTest.java`

**Interfaces:**
- `record Novedades(List<ModeloNuevo> modelos, List<PielNueva> pieles, List<ColorNuevo> colores)`; `ModeloNuevo(String id, String codigo, boolean existe, String nombre, String tipo, List<ArticuloNuevo> articulos, Imagen croquis, boolean entrado)`; `ArticuloNuevo(String referencia, String forro, List<String> combinaciones, List<ColorArticulo> colores)`; `PielNueva(String id, String refPiel, String nombre, boolean entrado)`; `ColorNuevo(String id, String codigo, String descripcion, boolean entrado)`.
- `NovedadesSms.calcular(String cliente, List<LineaSms> lineas) → Novedades`
- `NovedadesSms.entrarModelo(String cliente, ModeloNuevo) / entrarPiel(String cliente, PielNueva) / entrarColor(String cliente, ColorNuevo)`; `entrarTodo(String cliente, Novedades)` en una transacción, `IllegalArgumentException("<modelo>: falta el nombre")` sin escribir nada.
- Endpoints JSON: `POST /documentos-corte/sms/novedades/{tipo}/{id}` (campos editados) y `POST /documentos-corte/sms/novedades/todo`; errores `{mensaje}` con 400.

- [ ] Tests: `modeloPielYColorDesconocidosSonNovedad`, `loQueYaEstaNoEsNovedad`, `pielConFilaSinNombreEsNovedad`, `entrarModeloLoDaDeAltaEnMuestrasConSusArticulos`, `entrarPielNoPisaUnNombreExistente`, `entrarTodoSinNombreNoEntraNada`, `genericoNoTieneListaDePieles`.
- [ ] Implementar. Verde. Commit.

### Task 5: Hoja de corte — servicio, histórico y excel

**Files:**
- Create: `src/main/resources/db/migration/V18__hoja_corte.sql`, `persistence/HojaCorte.java`, `HojaCorteRepository.java`, `ResumenHojaCorte.java`, `ArchivoHojasCorte.java`, `model/DatosHojaCorte.java`, `service/corte/HojaCorteService.java`
- Test: `service/corte/HojaCorteServiceTest.java` (deja `target/Hoja de corte ejemplo.xlsx`), `persistence/ArchivoHojasCorteTest.java`

**Interfaces:**
- `record DatosHojaCorte(String cliente, String nombreCliente, String temporada, String referencia, String modelo, String nombreBolso, LocalDate fecha, List<Linea> lineas)`; `Linea(String codigoColor, String descripcionColor, Integer cantidad, PielCorte principal, PielCorte forro, List<PielCorte> combinaciones)`; `Linea.estaVacia()`.
- `HojaCorteService.excel(DatosHojaCorte, Imagen foto) → byte[]`, `nombreFichero(DatosHojaCorte) → "<cliente> <temporada> - <modelo> - Hoja de Corte.xlsx"` saneado.
- `ArchivoHojasCorte.guardar(DatosHojaCorte, byte[] foto) → HojaCorte`, `todas() → List<ResumenHojaCorte>`, `datosDe(Long)`, `fotoDe(Long)`.
- Tabla `hoja_corte(id, cliente, temporada, modelo, nombre_bolso, fecha timestamp, datos clob, foto bytea)`.

- [ ] Tests: `unaFichaPorLineaConCantidad` (reabrir con POI: nº de hojas = líneas con cantidad; título = modelo), `nombreDelFichero`, `guardaYLeeElSnapshot`.
- [ ] Implementar. Verde. Commit.

### Task 6: Hoja de corte — pantallas

**Files:**
- Create: `web/HojaCorteController.java`, `web/HojaCorteForm.java`, `templates/hoja-corte.html`, `templates/hoja-corte-antiguas.html`, `static/hoja-corte.js`
- Modify: `templates/documentos-corte-menu.html` (tercera tarjeta), `web/HojasProduccionController.catalogo` se reutiliza tal cual desde el script
- Test: `web/HojaCorteControllerTest.java`

**Interfaces:**
- `GET /documentos-corte/hoja[?importar=id]`, `POST /documentos-corte/hoja/generar` (multipart, `fotoSubida`, `fotoId`, `fotoHojaId`) → xlsx o 400 texto, `GET /documentos-corte/hoja/antiguas`, `GET /documentos-corte/hoja/{id}/excel`.
- El script pide `GET /hojas-produccion/catalogo?cliente=&temporadaId=`, rellena cabecera y la línea del color del artículo; «Empezar desde cero» vacía todo.

- [ ] Tests: `elMenuDelCorteTieneLaHojaDeCorte`, `generarDevuelveElExcelYLoGuarda`, `sinLineasEs400YNoGuarda`, `importarRellenaElFormulario`, `antiguasListaLaGuardada`, `bajarOtraVez`.
- [ ] Implementar. Verde. Prueba en el navegador (Playwright con el Chrome del equipo, puerto 8091, base en memoria). Commit.

### Task 7: Documentación

- [ ] `.claude/rules/corte.md`: SMS con IA, novedades, Hoja de corte (y sus `paths:` nuevos). `mvn test -Dskip.frontend=true` completo en verde. Commit.
