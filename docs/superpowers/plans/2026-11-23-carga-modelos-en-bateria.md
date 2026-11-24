# Carga de modelos en batería — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Una puerta de entrada revisada para los modelos: elegir el origen, completar cada modelo en una pantalla de lista + panel, y cargar al catálogo solo los aceptados.

**Architecture:** Pantallas de la aplicación vieja (Thymeleaf + JavaScript propio). El lote vive en la sesión (`CargaModelosEnCurso`) con sus ficheros en un directorio temporal; el servidor es la única fuente de verdad y la batería se pinta desde `GET /carga-modelos/estado`. Los lectores de excel, el conversor de fotos y la extracción de imágenes de PDF son los que ya existen.

**Tech Stack:** Java 17, Spring Boot 3.5, Thymeleaf, JavaScript sin framework, JUnit 5 + MockMvc.

**Spec:** `docs/superpowers/specs/2026-11-23-carga-modelos-en-bateria-design.md`

## Global Constraints

- Idioma del código, comentarios y pantalla: español.
- La carga **solo añade y rellena huecos**; pisa un valor guardado únicamente si el campo se ha tocado en el panel.
- Ninguna imagen de una ficha técnica entra sin elegirla.
- Cargar es **todo o nada** (una transacción).
- El nombre del modelo es obligatorio **siempre**.
- Nada de `modelos.*` importa de `web/` ni de `service/cargamodelos/`; al revés sí.
- Los tests usan claves propias (prefijo `CM`), por la base compartida de la suite.
- No se hace commit: el usuario los pide.

## Review Focus

1. Un modelo nuevo cuyo código ya existe en el catálogo al cargar (alguien lo dio de alta entretanto) → se trata como existente, no revienta.
2. Dos modelos del lote con la misma referencia → no se puede aceptar el segundo.
3. Una carpeta de modelo que no casa con ningún modelo del excel → aviso, no crea modelo.
4. Un PDF que no se deja leer dentro de una carpeta → se guarda como ficha y el emergente dice que no hay imágenes; no aborta la carga.
5. Una foto que no se puede convertir → se marca y no se carga; las demás siguen.

---

## Estructura de ficheros

`service/cargamodelos/` (nuevo):
- `OrigenCarga` — enum `TEMPORADA, SMS, PRODUCCION, GRUPO, MANUAL` con `etapaDePartida()`.
- `LecturaOrigen` — records `LecturaOrigen(articulos, croquis, avisos)`, `ArticuloLeido`, `ColorLeido`.
- `LectorOrigenes` (@Service) — `dePedido`, `deSms`, `deProduccion`, `admite(origen, cliente)`.
- `CarpetaModelos` — lee carpeta o zip: raíz, ficheros por subcarpeta (fotos y PDF), avisos.
- `EsqueletoCarpetas` — `byte[] zip(raiz, carpetas)`.
- `LoteCarga`, `ModeloEnCarga`, `PielEnCarga`, `ColorEnCarga`, `FotoEnCarga`, `FichaEnCarga`, `EstadoModelo` — el estado editable.
- `ConstructorLote` (@Service) — monta el lote, casa cada modelo con el catálogo, aplica ediciones.
- `CargadorModelos` (@Service) — `@Transactional ResumenCarga cargar(LoteCarga)`.

`web/`: `CargaModelosEnCurso` (@SessionScope), `CargaModelosController`.
Plantillas: `carga-modelos.html`, `carga-modelos-bateria.html`. Estáticos: `carga-modelos.js`, y CSS en `estilo.css`.

## Tareas

### Task 1: `/temporadas` solo guarda ficheros
- [ ] Quitar de `TemporadasController` la alta al catálogo, las fotos y `/temporadas/{id}/fotos`; quitar de `temporadas.html` el emergente, el campo de fotos y sus textos; borrar `ImportacionFotosTemporada`.
- [ ] Reescribir en `TemporadasControllerTest` los tests del emergente y de las fotos: `guardarUnaTemporadaNoTocaElCatalogo`.
- [ ] `mvn test -Dskip.frontend=true -Dtest=TemporadasControllerTest` en verde.

### Task 2: lectores de origen
- [ ] `LectorOrigenesTest` contra los excels reales: pedido de APC (trae nombre), pedido de AMI + producción (el nombre sale del de producción), SMS de APC (piel, forro, etapa), producción de APC.
- [ ] Implementar `LectorOrigenes`.

### Task 3: carpeta de modelos y esqueleto
- [ ] `CarpetaModelosTest`: raíz = temporada; PDF = ficha; imagen = foto; ficheros sueltos y otros tipos = aviso; zip y carpeta dan lo mismo. `EsqueletoCarpetasTest`: el zip se vuelve a leer con las mismas carpetas.
- [ ] Implementar.

### Task 4: el lote y su carga
- [ ] `CargadorModelosTest` (@SpringBootTest): nuevo desde manual; existente que solo rellena huecos; campo tocado que pisa; etapa por origen; fotos en el orden de la tira; ficha como documento; todo o nada; los cinco puntos de Review Focus.
- [ ] Implementar estado, `ConstructorLote`, `CargadorModelos`.

### Task 5: controlador y sesión
- [ ] `CargaModelosControllerTest` (MockMvc): origen → batería → editar → aceptar → cargar; esqueleto; menú enlaza.
- [ ] Implementar.

### Task 6: pantallas
- [ ] Menú (panel nuevo, tarjetas más altas), pantalla de origen, batería (lista + panel con rejilla, tira de fotos arrastrable, emergente de ficha técnica).
- [ ] Probar en el navegador con la aplicación arrancada.

### Task 7: cierre
- [ ] Suite completa en verde; `CLAUDE.md` actualizado.
