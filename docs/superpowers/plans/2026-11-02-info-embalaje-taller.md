# Etiqueta Info de Embalaje para Taller — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generar desde la aplicación la hoja A4 en Word que va pegada en el sobre de etiquetas que se manda al taller, guardarla, poder reimportarla y llevar un QR a la guía de embalaje.

**Architecture:** Flujo independiente del envío (`service/embalaje/`, `web/InfoEmbalajeController`): el pedido del cliente y el catálogo de modelos prerrellenan un formulario de una pantalla; al generar se guarda un snapshot JSON en `hoja_embalaje` (migración V2) y se devuelve el `.docx` (POI XWPF, sin plantilla). Comentarios tipo en `comentario_embalaje` con pantalla gemela de `/temporadas`.

**Tech Stack:** Java 17, Spring Boot 3.5, Thymeleaf, Apache POI XWPF, Jackson, ZXing 3.5.3 (nuevo), H2 + Flyway.

**Spec:** `docs/superpowers/specs/2026-11-02-info-embalaje-taller-design.md`

## Global Constraints

- Idioma: javadoc, comentarios, UI y mensajes en **español**; nombres de clases en inglés o como el resto del paquete (`ArchivoTemporadas`, `CatalogoModelos`).
- **La migración V1 no se toca**: todo cambio de esquema va en `src/main/resources/db/migration/V2__info_embalaje.sql`. Hibernate corre con `ddl-auto: validate`: entidad y SQL tienen que cuadrar.
- Entidades JPA con columnas planas (sin `@ManyToOne`), `@IdClass` o identity, constructor protegido para JPA, `fecha_actualizacion`/`fecha` con `LocalDateTime` — como `TemporadaGuardada` y `Articulo`.
- Los `@SpringBootTest` comparten la base en memoria de toda la suite: cada test usa **claves propias** (cliente `EMB-TEST`, nombres de comentario únicos) y no asume que la tabla está vacía salvo donde el plan lo dice.
- Nunca fallar en silencio ni bloquear por datos que un humano puede teclear: lo que no se encuentra se deja vacío y se avisa.
- Los tests de builders reabren el `.docx` con POI y comprueban contenido real; el de la hoja deja `target/Info embalaje ejemplo.docx`.
- Todo texto que escribe el usuario se guarda tal cual (no se pasa a mayúsculas: no es el catálogo).
- Commits pequeños, uno por tarea, con el mensaje en español y el pie `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Excel de pedido con un artículo sin ninguna línea con cantidad** (p. ej. AMI con `Commandé` vacío): el artículo tiene que salir en el desplegable con destinaciones vacías, no desaparecer. Test en Task 3 (`unArticuloSinComandasSaleSinDestinaciones`).
2. **Número de envío tecleado por debajo del máximo** (el usuario corrige una hoja vieja): se guarda tal cual y el siguiente sugerido sigue siendo max+1, no el tecleado+1. Test en Task 2 (`elSiguienteNumeroEsElMaximoMasUnoAunqueSeGuardeUnoMenor`).
3. **Formulario sin ninguna destinación o con filas vacías**: las filas sin destino y sin cantidad se descartan; una hoja sin destinaciones se genera igual con total 0. Test en Task 5 (`lasFilasDeDestinacionVaciasSeDescartan`).
4. **Importar una hoja cuya temporada ya no existe**: el formulario se abre con los valores del snapshot y el desplegable solo trae ese artículo; nada de error. Test en Task 5 (`importarUnaHojaSinTemporadaAbreElFormularioConSuArticulo`).
5. **Enlace con espacios o vacío**: vacío o solo espacios = sin QR; con espacios alrededor se recorta antes de codificar. Test en Task 4 (`unEnlaceEnBlancoNoPintaQr`).

---

### Task 1: QR con ZXing

**Files:**
- Modify: `pom.xml` (junto a barcode4j)
- Create: `src/main/java/com/puntotres/packinglist/service/embalaje/CodigoQr.java`
- Test: `src/test/java/com/puntotres/packinglist/service/embalaje/CodigoQrTest.java`

**Interfaces:**
- Produces: `public final class CodigoQr { public static byte[] png(String texto, int pixeles) }` — PNG cuadrado de `pixeles` de lado, corrección de errores `M`, margen de 1 módulo. `texto` ya recortado por quien llama.

- [ ] **Step 1: Añadir dependencias** `com.google.zxing:core:3.5.3` y `com.google.zxing:javase:3.5.3` al `pom.xml`, con un comentario de dos líneas (para qué: el QR de la guía de embalaje; barcode4j no hace QR).

- [ ] **Step 2: Escribir el test**

```java
@Test
void elPngSeDecodificaAlMismoTexto() throws Exception {
    byte[] png = CodigoQr.png("https://youtu.be/abc123", 300);
    BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
    assertEquals(300, imagen.getWidth());
    assertEquals(300, imagen.getHeight());
    var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen)));
    assertEquals("https://youtu.be/abc123", new MultiFormatReader().decode(bitmap).getText());
}
```

- [ ] **Step 3: Ejecutar** `mvn -q test -Dtest=CodigoQrTest` → falla por clase inexistente.
- [ ] **Step 4: Implementar `CodigoQr.png`** con `QRCodeWriter.encode(texto, BarcodeFormat.QR_CODE, pixeles, pixeles, hints)` y `MatrixToImageWriter.writeToStream(matrix, "PNG", salida)`. Javadoc: por qué va 1:1 en el Word (como el EAN-13, no se reescala).
- [ ] **Step 5: Ejecutar** `mvn -q test -Dtest=CodigoQrTest` → PASS.
- [ ] **Step 6: Commit** `embalaje: código QR con ZXing`.

---

### Task 2: Persistencia — hojas guardadas y comentarios tipo

**Files:**
- Create: `src/main/resources/db/migration/V2__info_embalaje.sql` (SQL literal del spec, sección «Persistencia»)
- Create: `src/main/java/com/puntotres/packinglist/model/DatosHojaEmbalaje.java`
- Create: `src/main/java/com/puntotres/packinglist/persistence/HojaEmbalaje.java`, `HojaEmbalajeRepository.java`, `ResumenHojaEmbalaje.java`, `ArchivoHojasEmbalaje.java`
- Create: `src/main/java/com/puntotres/packinglist/persistence/ComentarioEmbalaje.java`, `ComentarioEmbalajeRepository.java`, `ArchivoComentariosEmbalaje.java`
- Test: `src/test/java/com/puntotres/packinglist/persistence/ArchivoHojasEmbalajeTest.java`, `ArchivoComentariosEmbalajeTest.java`

**Interfaces:**
- Produces:
  - `record DatosHojaEmbalaje(String cliente, String nombreCliente, String temporada, String taller, int numeroEnvio, String nota, LocalDate fecha, String referencia, String codigoColor, String descripcionColor, String nombreBolso, String codigoModelo, String medidaCaja, Integer unidadesPorCaja, String bolsaTela, String etiquetaInterior, String instruccionEspecial, List<DestinacionHoja> destinaciones, String repartoCajas, String comentarios, String enlaceVideo)` con `record DestinacionHoja(String destino, String pedido, int cantidad)` anidado, `int totalUnidades()` (suma) y `boolean tieneEnlace()` (no nulo ni en blanco). Compact constructor: strings nulos → `""`, lista copiada.
  - `record ResumenHojaEmbalaje(Long id, LocalDateTime fecha, String cliente, String temporada, int numeroEnvio, String referencia, String codigoColor, String taller)`.
  - `@Service @Transactional class ArchivoHojasEmbalaje`: `HojaEmbalaje guardar(DatosHojaEmbalaje datos, Long temporadaId)`; `List<ResumenHojaEmbalaje> todas()` (fecha descendente); `Optional<DatosHojaEmbalaje> datosDe(Long id)`; `Optional<HojaEmbalaje> hoja(Long id)`; `int siguienteNumeroEnvio()`; `Optional<DatosHojaEmbalaje> ultimaDe(String cliente, String codigoModelo)`; `List<String> talleres()` (distintos, sin vacíos, ordenados).
  - `HojaEmbalaje` entidad con getters de todas las columnas y `getTemporadaId()` nullable.
  - `@Service @Transactional class ArchivoComentariosEmbalaje`: `List<ComentarioEmbalaje> todos()` (por nombre); `ComentarioEmbalaje guardar(Long id, String nombre, String texto)` — `id` nulo = alta; nombre repetido (sin mayúsculas, sin espacios de sobra) en otra fila → `IllegalArgumentException("Ya hay un comentario llamado «X»")`; `Optional<ComentarioEmbalaje> uno(Long id)`; `void borrar(Long id)`.
  - `ComentarioEmbalaje` con `getId()`, `getNombre()`, `getTexto()`.

- [ ] **Step 1: Escribir los tests**

`ArchivoHojasEmbalajeTest` (`@SpringBootTest`, cliente `EMB-TEST`, con un helper `datos(int numeroEnvio, String codigoModelo)` que monta un `DatosHojaEmbalaje` completo con dos destinaciones):

```java
@Test void sinHojasElSiguienteNumeroEsUno()  // solo vale si la tabla está vacía: ejecutar PRIMERO con @Order(1) y @TestMethodOrder; si otro test ya guardó, comprobar >= 1
@Test void elSiguienteNumeroEsElMaximoMasUnoAunqueSeGuardeUnoMenor()
    // guarda 500 y luego 7 → siguienteNumeroEnvio() == 501
@Test void elSnapshotVuelveEnteroYLaFotoNoViajaEnEl()
    // guardar(datos) → datosDe(id) equals datos (record equals), y hoja(id).getCodigoModelo() == "ULL027"
@Test void laUltimaDelModeloEsLaMasRecienteDelMismoClienteYModelo()
    // dos hojas de ULL027 con bolsaTela "A" y luego "B", una de otro modelo con "C" → ultimaDe("EMB-TEST","ULL027").bolsaTela == "B"
@Test void losTalleresSonLosDistintosSinVacios()
```

`ArchivoComentariosEmbalajeTest`:

```java
@Test void altaEdicionYBorrado()
@Test void unNombreRepetidoSeRechazaSinMirarMayusculasNiEspacios()
    // guardar(null,"Cajas pesadas","…") y luego guardar(null," cajas PESADAS ","…") → IllegalArgumentException; editar la misma fila con su propio nombre NO falla
```

- [ ] **Step 2: Ejecutar** `mvn -q test -Dtest=ArchivoHojasEmbalajeTest+ArchivoComentariosEmbalajeTest` → falla por compilación.
- [ ] **Step 3: Escribir `V2__info_embalaje.sql`** con el SQL del spec y un comentario de cabecera (qué guarda cada tabla y por qué `datos` es un clob con el snapshot).
- [ ] **Step 4: Escribir `DatosHojaEmbalaje`** en `model/` (va en `model` y no en `service/embalaje` para que `persistence` no dependa de `service`).
- [ ] **Step 5: Escribir las entidades y repositorios.** `HojaEmbalaje.datos` con `@Lob @Column(name = "datos", nullable = false) String`. Repositorio: `findAllByOrderByFechaDesc()`, `findFirstByClienteAndCodigoModeloOrderByFechaDesc(String, String)`, `@Query("select max(h.numeroEnvio) from HojaEmbalaje h") Integer maximoNumeroEnvio()`, `@Query("select distinct h.taller from HojaEmbalaje h where h.taller is not null and h.taller <> '' order by h.taller") List<String> talleres()`. `ComentarioEmbalajeRepository`: `findAllByOrderByNombreAsc()`.
- [ ] **Step 6: Escribir las dos fachadas.** `ArchivoHojasEmbalaje` recibe el `ObjectMapper` de Spring por constructor y serializa/deserializa `DatosHojaEmbalaje` (`LocalDate` va con el módulo JavaTime que Boot ya registra). `ultimaDe` deserializa la fila más reciente.
- [ ] **Step 7: Ejecutar** la suite de los dos tests → PASS; después `mvn -q test -Dtest=EsquemaCatalogoTest` para comprobar que Flyway + validate siguen arrancando.
- [ ] **Step 8: Commit** `embalaje: hojas guardadas y comentarios tipo en la base de datos (V2)`.

---

### Task 3: Del pedido y el catálogo al formulario — `PedidoEmbalajeService`

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/embalaje/ArticuloEmbalaje.java`, `ArticulosEmbalaje.java`, `PedidoEmbalajeService.java`
- Test: `src/test/java/com/puntotres/packinglist/service/embalaje/PedidoEmbalajeServiceTest.java`

**Interfaces:**
- Consumes: `AmiPedidoExcel.desdeBytes`, `.catalogo()` (`LineaCatalogo(referencia, colorCode, colorNombre, talla, poNumerico, poSufijo)`), `.comandasDe(referencia, color, talla)` (`Comanda(poNumerico, poSufijo, cantidad)`); `ApcPedidoExcel.desdeBytes`, `.catalogo()` (`LineaCatalogo(referencia, pedido, destino, designacion, color, talla)`), `.comandaDe(pedido)` (`Comanda(pedido, destino, cantidad)`); `ReglasTallerProperties.clienteTaller("AMI").get().destinoDeSufijo(sufijo)`; `TablaColores.buscar(cliente, codigo)` → `ColorCliente.getDescripcion()`; `CatalogoModelos.articulo(cliente, referencia)` / `.modelo(cliente, codigo)`; `FotosCatalogo.de(cliente, codigoModelo)` → `ResumenFoto(id, …, principal, …)`; `DocumentosCorteService.reglasDeProduccion(cliente).partir(referencia).modelo()` (comprobar el nombre del accesor en `ReferenciaCorte`); `ArchivoHojasEmbalaje.ultimaDe`.
- Produces:
  - `record ArticuloEmbalaje(String referencia, String codigoColor, String descripcionColor, String nombreBolso, String codigoModelo, Long fotoId, String medidaCaja, Integer unidadesPorCaja, String bolsaTela, String etiquetaInterior, String instruccionEspecial, List<DatosHojaEmbalaje.DestinacionHoja> destinaciones)` con `String etiqueta()` = `referencia + " · " + codigoColor + (descripción ? " " + descripción : "") + (nombre ? " — " + nombre : "")` para el desplegable.
  - `record ArticulosEmbalaje(List<ArticuloEmbalaje> articulos, List<String> avisos)`.
  - `@Service class PedidoEmbalajeService`: `boolean soporta(String cliente)` (AMI o APC, comparando la clave sin mayúsculas); `ArticulosEmbalaje articulosDe(String cliente, byte[] excelPedido) throws IOException`. Un artículo = referencia + código de color, en el orden del fichero. AMI: destinaciones agrupadas por `poNumerico` sumando tallas, destino de `destinoDeSufijo` en mayúsculas (sin traducción: el sufijo en mayúsculas y un aviso una vez por sufijo), `pedido` = `poNumerico` o `poNumerico + " " + poSufijo`. APC: `Document d'achat` distintos de las líneas del artículo, `comandaDe` para destino (mayúsculas) y cantidad; nombre = designación en mayúsculas. Nombre vacío → `modelo.nombre` del catálogo. Caja/unidades de `catalogo.articulo`. `fotoId` = la principal de `fotos.de`, si no la primera, si no `null`. Bolsa, etiqueta interior e instrucción de `ultimaDe(cliente, codigoModelo)` (vacíos si no hay).

- [ ] **Step 1: Escribir los tests** (`@SpringBootTest`, con los excels de `src/test/resources/ejemplos/`):

```java
@Test void apcSpring27DaLasCuatroDestinacionesDelNeigeNoir()
    // articulosDe("APC", APC_PEDIDO_SPRING27) → el artículo referencia "PXCBC-F67008" y codigoColor "LZZ":
    // pedidos en orden del fichero contienen exactamente {4100132915→"JAPAN", 4100132811→"DOUANES USA", 4100132847→"WHOLESALE", 4100132835→"RETAIL"};
    // cada cantidad > 0 y total == suma; nombreBolso empieza por "SAC LE NEIGE"; codigoModelo == "F67008"
@Test void amiSs27DaElPoYElDestinoDelCandyVanille()
    // articulosDe("AMI", EAN PUNTOTRES SS27) → referencia "USL745.AL0245", codigoColor "709", descripcionColor "VANILLE",
    // una destinación con pedido "09012", destino "PARIS", cantidad > 0; codigoModelo == "USL745"
@Test void unArticuloSinComandasSaleSinDestinaciones()
    // excel AMI construido con testutil/PedidoAmiExcel (ver cómo lo usan otros tests) con una fila con Commandé vacío → el artículo está y destinaciones.isEmpty()
@Test void loDelCatalogoYDeLaUltimaHojaPrerrellenan()
    // catalogo.asegurarArticulo("APC","PXCBC-F67008") + guardarCampo medida "60x40x20" y unidades 10 (ver CampoArticulo);
    // archivoHojas.guardar(datos con cliente "APC", codigoModelo "F67008", bolsaTela "BOLSA TELA APC 15X15");
    // → el artículo trae medidaCaja "60x40x20", unidadesPorCaja 10, bolsaTela "BOLSA TELA APC 15X15"
@Test void soportaSoloAmiYApc()
```

- [ ] **Step 2: Ejecutar** → falla por compilación.
- [ ] **Step 3: Implementar** los dos records y el servicio. Javadoc del servicio: por qué aquí sí se suman tallas (la hoja no lleva EAN) y por qué la memoria del artículo solo se lee.
- [ ] **Step 4: Ejecutar** `mvn -q test -Dtest=PedidoEmbalajeServiceTest` → PASS.
- [ ] **Step 5: Commit** `embalaje: artículos y destinaciones del pedido con lo que ya sabe el catálogo`.

---

### Task 4: El Word — `InfoEmbalajeDocBuilder`

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/embalaje/InfoEmbalajeDocBuilder.java`
- Test: `src/test/java/com/puntotres/packinglist/service/embalaje/InfoEmbalajeDocBuilderTest.java`

**Interfaces:**
- Consumes: `DatosHojaEmbalaje`, `CodigoQr.png`.
- Produces: `@Service class InfoEmbalajeDocBuilder { public byte[] generar(DatosHojaEmbalaje datos, byte[] fotoJpeg) throws IOException }` (`fotoJpeg` nullable); `public static String nombreFichero(DatosHojaEmbalaje datos)` → `Info embalaje <nombreCliente> <numeroEnvio> <referencia> <codigoColor>.docx` saneado con `[\\/:*?"<>|]+` → `_`.

Maquetación: la de la sección «El documento Word» del spec, literal (tamaños, colores `1C2733`/`F2F4F6`/`FF8900`/`FFF7E0`/`E6C34A`/`D4DAE1`, títulos de sección «EMBALAJE», «DESTINACIONES», «REPARTO EN CAJAS», «COMENTARIOS», título del QR «Guide d'emballage du sac», fichas «Nº ENVÍO», «NOTA ICSUITE», «FECHA» con fecha `dd/MM/yyyy`, frase de etiqueta interior). Constantes con nombre, como en `PackingPuntotresDocBuilder`; sombreado de celda con `CTShd` (`setFill`), bordes con `CTTcBorders`, imagen con `run.addPicture(..., PICTURE_TYPE_PNG/JPEG, nombre, Units.toEMU(cm), Units.toEMU(cm))` (la foto conserva proporción: leer sus dimensiones con `ImageIO` y escalar al ancho de 5 cm).

- [ ] **Step 1: Escribir el test.** Helper `texto(XWPFDocument)` que concatena párrafos y celdas de tablas; `datosEjemplo()` con los valores de la foto de Tánger (`PXCBC-F67008`, `LZZ NOIR`, `SAC LE NEIGE ALPACA NOIR`, envío 270, nota 12662, cuatro destinaciones, reparto de 5 líneas, instrucción `GRABADO LOGO NIKEL`, comentarios, enlace `https://youtu.be/guia`).

```java
@Test void laHojaLlevaTodosLosDatos()        // referencia, "LZZ NOIR", "270", "12662", "JAPAN", "4100132915", "TOTAL 520 UNIDADES", "GRABADO LOGO NIKEL", cada línea del reparto, el comentario
@Test void elQrSoloSaleConEnlace()            // con enlace: doc.getAllPictures() contiene un PNG y el texto "Guide d'emballage du sac"; sin enlace: ni lo uno ni lo otro
@Test void unEnlaceEnBlancoNoPintaQr()        // enlaceVideo "   " → sin QR
@Test void lasSeccionesVaciasNoSalen()        // sin instrucción, sin reparto, sin comentarios → el texto no contiene "REPARTO EN CAJAS" ni "COMENTARIOS" ni "Instrucción"
@Test void laEtiquetaInteriorSeFrasea()       // "" → "NO LLEVA ETIQUETA DE CARTÓN INTERIOR"; "PARIS" → "LLEVA ETIQUETA DE CARTÓN INTERIOR PARIS"
@Test void laFotoVaSiLaHay()                  // con un JPEG de 10x10 generado con ImageIO → getAllPictures() tiene 2 (foto + QR)
@Test void elNombreDeFicheroSeSanea()         // "Info embalaje A.P.C. 270 PXCBC-F67008 LZZ.docx"; una referencia con "/" sale con "_"
@Test void dejaElEjemploEnTarget()            // escribe target/Info embalaje ejemplo.docx con foto de relleno y QR
```

- [ ] **Step 2: Ejecutar** → falla.
- [ ] **Step 3: Implementar el builder.**
- [ ] **Step 4: Ejecutar** `mvn -q test -Dtest=InfoEmbalajeDocBuilderTest` → PASS. Abrir `target/Info embalaje ejemplo.docx` con Word por COM si está disponible (`PowerShell: $w=New-Object -ComObject Word.Application; $d=$w.Documents.Open(...); $d.ComputeStatistics(2)` = páginas) y comprobar que son **1** página; si no hay Word, dejar constancia en el commit de que no se ha comprobado.
- [ ] **Step 5: Commit** `embalaje: hoja A4 de instrucciones de embalaje en Word con QR`.

---

### Task 5: Pantallas — entrada, formulario, antiguos y menú

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/web/EmbalajeEnCurso.java`, `InfoEmbalajeController.java`, `HojaEmbalajeForm.java`
- Create: `src/main/resources/templates/info-embalaje.html`, `info-embalaje-formulario.html`, `info-embalaje-antiguos.html`
- Modify: `src/main/resources/templates/menu.html` (quinta tarjeta `menu-tarjeta` → `/info-embalaje`, entre «Documentos del corte» y «Escandallos», con icono SVG de sobre), `src/main/resources/static/estilo.css` (clases nuevas con prefijo `.embalaje-`: formulario en dos columnas, tabla de destinaciones, lista + panel de antiguos)
- Test: `src/test/java/com/puntotres/packinglist/web/InfoEmbalajeControllerTest.java`; Modify `RutasTest` (añadir `/info-embalaje` a la lista de tarjetas del menú)

**Interfaces:**
- Consumes: `PedidoEmbalajeService`, `ArchivoHojasEmbalaje`, `ArchivoComentariosEmbalaje.todos()` (de Task 2) para el desplegable, `InfoEmbalajeDocBuilder`, `FotosCatalogo.conBytes(id)`, `ArchivoTemporadas.paraElDesplegable()` / `conPedidoParaElEnvio(id, cliente)`, `ClientesProperties`.
- Produces:
  - `@Component @SessionScope class EmbalajeEnCurso`: `cliente`, `nombreCliente`, `temporada`, `temporadaId` (nullable), `List<ArticuloEmbalaje> articulos`, `List<String> avisos`, `DatosHojaEmbalaje partida` (nullable: valores de una importación), `reiniciar()`, `boolean estaVacio()` (sin cliente).
  - `HojaEmbalajeForm` (bean con getters/setters): `taller, numeroEnvio (Integer), nota, referencia, codigoColor, descripcionColor, nombreBolso, codigoModelo, medidaCaja, unidadesPorCaja (Integer), bolsaTela, etiquetaInterior, instruccionEspecial, List<Destino> destinos (Destino: destino, pedido, cantidad Integer), repartoCajas, comentarios, enlaceVideo`; `DatosHojaEmbalaje aDatos(EmbalajeEnCurso enCurso, LocalDate hoy)` que descarta filas sin destino y sin cantidad y recorta el enlace.
  - Rutas: `GET /info-embalaje` (entrada; atributos `clientes` como `EtiquetasArticuloController.vistaDeClientes` pero `disponible` = `pedidoEmbalaje.soporta`, `temporadasJs`), `POST /info-embalaje/cargar` (`cliente`, `temporadaGuardadaId`, `temporada`, `pedido`) → sesión → `redirect:/info-embalaje/formulario`; errores por flash `error` como en etiquetas de artículo. `GET /info-embalaje/formulario` (sin sesión → redirect a la entrada): atributos `enCurso`, `articulosJs` (lista de `ArticuloEmbalaje` tal cual, Thymeleaf la serializa con `th:inline="javascript"`), `partida` (o `null`), `siguienteNumero`, `talleres`, `comentariosGuardados`, `hoy`. `POST /info-embalaje/generar` (`HojaEmbalajeForm`) → guarda → responde el `.docx` (`Content-Disposition: attachment`, cabeceras `X-Hoja-Id` y `X-Numero-Envio`). `GET /info-embalaje/antiguos` (`id` opcional): `hojas` (resumen), `elegida` (`DatosHojaEmbalaje`) y `elegidaId`. `GET /info-embalaje/antiguos/{id}/docx` (regenera con la foto principal del modelo del catálogo). `POST /info-embalaje/importar` (`id`): mete `partida` en sesión, carga el pedido de `temporadaId` si sigue existiendo y es del cliente (si no, `articulos` = solo el artículo del snapshot, construido con `ArticuloEmbalaje` a partir de él) → `redirect:/info-embalaje/formulario`.
  - Formulario (HTML): desplegable `#articulo` con `etiqueta()`; al elegir, el JS rellena todos los campos y la tabla de destinaciones (filas `destinos[i].destino/pedido/cantidad`, botón `+ destinación` y `×` por fila, total recalculado al teclear); foto `<img th:src="@{/catalogo/foto/{id}}">` si `fotoId`; `#comentarioGuardado` con `-- comentario guardado --` + opciones, al elegir **añade** el texto al textarea (con salto de línea si ya había algo) y vuelve a la opción vacía; lápiz a `/comentarios-embalaje`; `<datalist id="talleres">`; el botón «Generar hoja» hace `fetch` del POST con `FormData`, descarga el blob con el nombre de `Content-Disposition`, enseña «Guardada como envío N» y pone `numeroEnvio` a N+1. Con `partida`, los campos arrancan con sus valores y el desplegable preseleccionado. Botón «Importar antiguos» en la entrada y enlace «Empezar de nuevo» en el formulario (`GET /info-embalaje`).
  - Antiguos (HTML): dos columnas; izquierda `<ul>` de enlaces `?id=` con `dd/MM/yyyy · temporada · envío N · referencia color`; derecha, el resumen en tabla rótulo/valor con las destinaciones y los textos, botones «Descargar Word» e «Importar al formulario» (form POST). Sin hojas: «Todavía no hay ninguna hoja guardada».

- [ ] **Step 1: Escribir los tests** (`@SpringBootTest @AutoConfigureMockMvc`, con `MockHttpSession` compartida entre peticiones como `TemporadasControllerTest`):

```java
@Test void laEntradaOfreceClientesYSoloAmiYApcDisponibles()
@Test void cargarConElExcelRealLlevaAlFormularioConLosArticulos()
    // multipart /info-embalaje/cargar cliente=APC pedido=APC_PEDIDO_SPRING27 → redirect formulario; GET formulario contiene "PXCBC-F67008" y "4100132915"
@Test void generarGuardaLaHojaYDevuelveElWord()
    // POST generar con numeroEnvio=270, nota, referencia PXCBC-F67008, 2 destinos → status 200, content-type docx, cabecera X-Numero-Envio "270";
    // archivo.todas() contiene una hoja con referencia PXCBC-F67008 y envío 270; GET /info-embalaje/antiguos?id=<X-Hoja-Id> contiene "12662"
@Test void lasFilasDeDestinacionVaciasSeDescartan()
    // destinos[0] completo, destinos[1] todo vacío → datosDe(id).destinaciones().size() == 1
@Test void importarPrerrellenaElFormulario()
    // POST importar id → GET formulario contiene el valor de nota y bolsaTela del snapshot
@Test void importarUnaHojaSinTemporadaAbreElFormularioConSuArticulo()
    // hoja guardada con temporadaId 999999 (inexistente) → POST importar → formulario 200, contiene su referencia
@Test void elSiguienteNumeroDeEnvioSubeTrasGenerar()
    // siguienteNumeroEnvio() tras generar 270 >= 271 (otro test puede haber guardado más)
@Test void descargarUnaAntiguaRegeneraElWord()
```

- [ ] **Step 2: Ejecutar** → falla.
- [ ] **Step 3: Implementar** sesión, formulario, controlador, las tres plantillas, el CSS y la tarjeta del menú. El JS del desplegable de temporadas se copia de `etiquetas-articulo-produccion.html`.
- [ ] **Step 4: Ejecutar** `mvn -q test -Dtest=InfoEmbalajeControllerTest+RutasTest` → PASS. Arrancar `mvn spring-boot:run` y recorrer las tres pantallas con una temporada real guardada; matar el `java` del 8080 al acabar (`netstat -ano | findstr :8080` + `taskkill /F /PID`).
- [ ] **Step 5: Commit** `embalaje: pantallas de entrada, formulario y hojas antiguas`.

---

### Task 6: Comentarios tipo — pantalla `/comentarios-embalaje`

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/web/ComentariosEmbalajeController.java`, `src/main/resources/templates/comentarios-embalaje.html`
- Modify: `menu.html` (panel `menu-panel` «Comentarios de embalaje» → `/comentarios-embalaje`, tras «Catálogo de modelos»), `estilo.css` si hace falta (reutilizar `.temporada-guardada`/`.form-temporada`)
- Test: `src/test/java/com/puntotres/packinglist/web/ComentariosEmbalajeControllerTest.java`

**Interfaces:**
- Consumes: `ArchivoComentariosEmbalaje`.
- Produces: `GET /comentarios-embalaje` (lista editable + alta, como `/temporadas`: una fila por comentario con `nombre`, `texto` en `<textarea>`, Guardar y Borrar; abajo «Añadir un comentario»), `POST /comentarios-embalaje` (`id` opcional, `nombre`, `texto`) y `POST /comentarios-embalaje/borrar` (`id`), con flash `mensaje`/`mensajeError`. «← Volver» a `/info-embalaje`.

- [ ] **Step 1: Escribir los tests**

```java
@Test void altaEdicionYBorradoPorLaPantalla()
@Test void unNombreRepetidoVuelveConError()     // flash mensajeError contiene "Ya hay un comentario"
@Test void elFormularioDeEmbalajeOfreceLosComentarios()  // tras dar de alta "Frágil EMB" y cargar un pedido, GET /info-embalaje/formulario contiene "Frágil EMB"
```

- [ ] **Step 2: Ejecutar** → falla.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Ejecutar** `mvn -q test -Dtest=ComentariosEmbalajeControllerTest+RutasTest` → PASS.
- [ ] **Step 5: Commit** `embalaje: comentarios tipo guardados con su pantalla`.

---

### Task 7: Documentación

**Files:**
- Modify: `CLAUDE.md` (sección nueva «**Etiqueta Info de Embalaje para Taller**» tras «Colores y Pieles de Cliente», con lo que no se deduce del código: contador único de envío, snapshot JSON y por qué, bolsa/etiqueta/instrucción de la última hoja y no del catálogo, memoria del artículo solo lectura, reparto a mano hasta nuevo aviso, QR solo con enlace, vídeo en YouTube no listado o Drive; y en «Comandos» la línea del `target/Info embalaje ejemplo.docx`), `README.md` (una línea en la lista de funciones).
- [ ] **Step 1: Escribir** las dos secciones.
- [ ] **Step 2: `mvn -q test`** completo → todo en verde (anotar en el commit cuántos tests).
- [ ] **Step 3: Commit** `docs: etiqueta info de embalaje para taller`.
