# Catálogo de modelos — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir las cuatro memorias sueltas por un catálogo de modelos enlazado (modelo → artículo → artículo-color, con pieles, colores y temporadas), del que leen el taller y el corte, que se rellena solo al guardar una temporada y que se mantiene en una pantalla nueva `/catalogo`.

**Architecture:** Flyway crea el esquema final de cero (la base de `./datos` se borra: la app no está desplegada). Las fachadas `MemoriaReferencias`, `MemoriaPieles` y `TablaColores` conservan su interfaz y pasan a leer y escribir las tablas nuevas, así que el taller y el corte no cambian de contrato. Un servicio nuevo `CatalogoModelos` concentra el alta (desde un `PedidoCorte`, que ya producen los dos lectores: pedido del cliente y excel de producción), la herencia y la propagación entre pieles de un modelo; `FotosCatalogo` guarda las fotos. La pantalla `/catalogo` edita celda a celda por `fetch`, como `/colores`.

**Tech Stack:** Java 17, Spring Boot 3.5 (web, thymeleaf, data-jpa), H2, Flyway (`flyway-core`, gestionado por el BOM de Spring Boot), Apache POI, JUnit 5 + MockMvc.

**Spec:** [docs/superpowers/specs/2026-11-01-catalogo-modelos-design.md](../specs/2026-11-01-catalogo-modelos-design.md)

## Global Constraints

- Idioma: nombres de clases en inglés o español como el resto del paquete (`Modelo`, `Articulo`…), javadoc, comentarios, mensajes y UI en **español**.
- Claves de texto normalizadas con `trim().toUpperCase(Locale.ROOT)` como hoy (`MemoriaPieles.clave`). **No se normaliza la referencia de piel** (`AL245` ≠ `AL0245`).
- `Integer`/`Double` para `unidades_por_caja` y `peso_neto_kg`: `null` = no se sabe. `medida_caja` admite `null`.
- Una sola migración `V1__esquema_inicial.sql`. **Se edita en cada tarea que añada o quite tablas**: no hay ninguna base fuera del repo que la haya ejecutado (la de `./datos` se borra; los tests la crean en memoria).
- `ddl-auto: validate` en los dos perfiles: un desajuste entre entidad y SQL tiene que romper el primer `@SpringBootTest`.
- Tests de persistencia y web con `@SpringBootTest` (y `@AutoConfigureMockMvc`), como `MemoriaPielesTest` y `TemporadasControllerTest`; cada test usa sus propias claves porque la base en memoria es la misma para toda la suite.
- Nunca fallar en silencio ni bloquear por datos que un humano puede resolver: un excel de temporada que no se deja leer **guarda la temporada y avisa** (desvío respecto del spec, por la regla del proyecto; se le dice al usuario).
- La regla `UBL` (`CajaData.esCinturon`) sigue mandando en packing, cajas y corte; `Modelo.tipo` es informativo.
- No tocar la propagación desde el taller: `MemoriaReferencias.recordar` escribe **solo** el artículo que recibe.
- Commits pequeños, en español, con `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## Review Focus

1. Una referencia del taller que la regla no sabe partir (`F63023` en APC) se guarda como artículo sin piel y **no** hereda ni recibe propagación de `PXCBC-F63023`. (Task 3.)
2. Guardar dos veces la misma temporada no duplica artículos, no pisa un nombre de modelo tecleado y deja `nuevos = 0`. (Task 6.)
3. Un desplegable de temporadas de la entrada del packing list no ofrece una temporada `PRODUCCION`, y `conPedidoParaElEnvio` la rechaza aunque llegue su id. (Task 6.)
4. Con el excel de producción y sin carpeta de fotos, si el catálogo tiene fotos del modelo, la orden de corte sale con ellas y `sinFotos` es falso. (Task 7.)
5. Un color sin código (bicolor de AMI) entra en `articulo_color` con su descripción como clave y **no** aparece en `color_cliente`. (Task 6.)

---

### Task 1: Flyway con el esquema actual

**Files:**
- Modify: `pom.xml` (dependencia `org.flywaydb:flyway-core`, sin versión)
- Modify: `src/main/resources/application.yml` (`spring.jpa.hibernate.ddl-auto: validate`; quitar el comentario de "las taras se resiembran… borrarlo" solo si deja de ser cierto: sigue siéndolo)
- Modify: `src/test/resources/application-test.yml`
- Create: `src/main/resources/db/migration/V1__esquema_inicial.sql`

**Interfaces:**
- Produces: el esquema de hoy en SQL, tabla por tabla (`memoria_caja_referencia`, `tara_caja`, `temporada_guardada`, `color_cliente`, `memoria_piel`, `memoria_pieles_articulo`, `memoria_colores_piel`).

- [ ] **Step 1: Sacar el DDL que Hibernate genera hoy**

Run: `mvn -q test -Dtest=MemoriaPielesTest -Dspring.jpa.show-sql=true -Dspring.jpa.properties.hibernate.format_sql=true 2>&1 | grep -A20 "create table"` (o arrancar con `logging.level.org.hibernate.SQL=DEBUG`). Copiar cada `create table` al fichero V1, en el estilo del de la rama `envios-dhl` (`git show envios-dhl:src/main/resources/db/migration/V1__esquema_inicial.sql`): tipos H2 `varchar(n)`, `integer`, `float(53)`, `timestamp(6)`, `blob`, `bigint generated by default as identity`.

- [ ] **Step 2: Añadir Flyway y pasar a `validate`**

`pom.xml`: dependencia `flyway-core` (el BOM pone la versión). `application.yml`: `ddl-auto: validate`. `application-test.yml`: copiar el de `envios-dhl` (`git show envios-dhl:src/test/resources/application-test.yml`) **sin** el bloque `envios:` — URL `jdbc:h2:mem:test-${random.uuid};DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE` y `ddl-auto: validate`, con su comentario de por qué.

- [ ] **Step 3: Suite entera en verde**

Run: `mvn -q test`
Expected: BUILD SUCCESS, mismo número de tests que antes. Si Hibernate se queja de un tipo (`wrong column type encountered`), corregir el SQL, no la entidad.

- [ ] **Step 4: Borrar la base de desarrollo y arrancar**

Run: borrar `./datos/` (si existe) y `mvn -q spring-boot:run` hasta ver `Started PackingListApplication`; abrir `/taras` y comprobar que las taras se han resembrado. Matar el proceso (ver CLAUDE.md, `netstat`/`taskkill`).

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/application.yml src/test/resources/application-test.yml src/main/resources/db/migration/V1__esquema_inicial.sql
git commit -m "persistencia: Flyway con el esquema actual y ddl-auto validate"
```

---

### Task 2: Entidades del catálogo

**Files:**
- Create: `persistence/TipoArticulo.java`, `persistence/TipoExcelTemporada.java`
- Create: `persistence/Modelo.java`, `ModeloId.java`, `ModeloRepository.java`
- Create: `persistence/Articulo.java`, `ArticuloId.java`, `ArticuloRepository.java`
- Create: `persistence/ArticuloColor.java`, `ArticuloColorId.java`, `ArticuloColorRepository.java` (es `MemoriaColoresPiel` renombrada: copiar y renombrar, tabla `articulo_color`)
- Create: `persistence/Piel.java`, `PielId.java`, `PielRepository.java` (es `MemoriaPiel` renombrada, tabla `piel`)
- Create: `persistence/ModeloTemporada.java`, `ModeloTemporadaId.java`, `ModeloTemporadaRepository.java`
- Create: `persistence/FotoCatalogo.java`, `FotoCatalogoRepository.java`, `persistence/ResumenFoto.java`
- Modify: `persistence/TemporadaGuardada.java`, `ResumenTemporada.java`, `TemporadaGuardadaRepository.java` (JPQL del resumen)
- Modify: `db/migration/V1__esquema_inicial.sql`
- Test: `src/test/java/com/puntotres/packinglist/persistence/EsquemaCatalogoTest.java`

Todas las rutas Java bajo `src/main/java/com/puntotres/packinglist/`.

**Interfaces (Produces):**
```java
public enum TipoArticulo { BOLSO, CINTURON;
    /** La regla del prefijo UBL, la misma del packing (CajaData.esCinturon). */
    public static TipoArticulo porReferencia(String referencia); }
public enum TipoExcelTemporada { PEDIDO, PRODUCCION }

@Entity @Table(name = "modelo") @IdClass(ModeloId.class)
public class Modelo { String cliente; String codigo; String nombre; @Enumerated(STRING) TipoArticulo tipo; LocalDateTime fechaActualizacion;
    public Modelo(String cliente, String codigo, String nombre, TipoArticulo tipo);
    getters; public String getNombre() /* "" si null */; public void setNombre(String); public void setTipo(TipoArticulo); }
public record ModeloId(String cliente, String codigo) implements Serializable {}

@Entity @Table(name = "articulo") @IdClass(ArticuloId.class)
public class Articulo { cliente; referencia; codigoModelo; String refPiel /* null = sin piel */; forro; combinacion1..4; String medidaCaja; Integer unidadesPorCaja; Double pesoNetoKg; fechaActualizacion;
    public Articulo(String cliente, String referencia, String codigoModelo, String refPiel);
    getters ("" en los textos nulos); public List<String> getCombinaciones() /* rellenas, en orden, como MemoriaPielesArticulo */;
    public void setCombinaciones(List<String>); public void setForro(String); public void setMedidaCaja(String); public void setUnidadesPorCaja(Integer); public void setPesoNetoKg(Double);
    public boolean tienePiel(); /* todo setter actualiza fechaActualizacion */ }
public record ArticuloId(String cliente, String referencia) implements Serializable {}

@Entity @Table(name = "articulo_color") @IdClass(ArticuloColorId.class)
public class ArticuloColor { /* = MemoriaColoresPiel: cliente, referencia, codigoColor, colorPiel, colorForro, colorCombinacion1..4, fecha; mismo constructor, getColorPiel/getColorForro/getColoresCombinacion/actualizar */ }
public record ArticuloColorId(String cliente, String referencia, String codigoColor) implements Serializable {}

@Entity @Table(name = "piel") @IdClass(PielId.class)
public class Piel { /* = MemoriaPiel: cliente, refPiel, nombre, fecha; actualizar(String) */ }
public record PielId(String cliente, String refPiel) implements Serializable {}

@Entity @Table(name = "modelo_temporada") @IdClass(ModeloTemporadaId.class)
public class ModeloTemporada { cliente; codigoModelo; Long temporadaId; public ModeloTemporada(String cliente, String codigoModelo, Long temporadaId); }
public record ModeloTemporadaId(String cliente, String codigoModelo, Long temporadaId) implements Serializable {}

@Entity @Table(name = "foto_modelo")
public class FotoCatalogo { @Id @GeneratedValue(IDENTITY) Long id; cliente; codigoModelo; nombreFichero; boolean principal; int orden; @Lob @Column(name = "bytes") byte[] jpeg; fechaActualizacion;
    public FotoCatalogo(String cliente, String codigoModelo, String nombreFichero, int orden, byte[] jpeg); getters; setPrincipal(boolean); }
/** Sin los bytes: lo que la pantalla y el corte necesitan para listar. */
public record ResumenFoto(Long id, String codigoModelo, String nombreFichero, boolean principal, int orden) {}
```
Repositorios (`JpaRepository` con su `Id`):
- `ModeloRepository.findByClienteOrderByCodigo(String)`
- `ArticuloRepository.findByClienteOrderByReferencia(String)`, `findByClienteAndCodigoModeloOrderByReferencia(String, String)`, `deleteByClienteAndCodigoModelo(String, String)`
- `ArticuloColorRepository.findByClienteAndReferenciaOrderByCodigoColor(String, String)`, `deleteByClienteAndReferencia(String, String)`, `deleteByClienteAndReferenciaIn(String, Collection<String>)`
- `PielRepository.findByClienteOrderByRefPiel(String)`
- `ModeloTemporadaRepository.findByClienteAndTemporadaId(String, Long)`, `findByClienteAndCodigoModelo(String, String)`, `deleteByTemporadaId(Long)`, `deleteByClienteAndCodigoModelo(String, String)`
- `FotoCatalogoRepository`: `@Query("select new …ResumenFoto(f.id, f.codigoModelo, f.nombreFichero, f.principal, f.orden) from FotoCatalogo f where f.cliente = :cliente and f.codigoModelo in :modelos order by f.codigoModelo, f.orden") List<ResumenFoto> resumenes(String cliente, Collection<String> modelos)`, `findByClienteAndCodigoModeloAndNombreFichero(String, String, String)`, `deleteByClienteAndCodigoModelo(String, String)`, `countByClienteAndCodigoModelo(String, String)`.
- `TemporadaGuardada`: campo `@Enumerated(STRING) @Column(name = "tipo_excel", length = 20, nullable = false) TipoExcelTemporada tipoExcel`; constructor `TemporadaGuardada(String cliente, String temporada, String nombreFichero, byte[] excel, TipoExcelTemporada tipoExcel)` (el de cuatro parámetros se queda y pone `PEDIDO`); `getTipoExcel()`. `ResumenTemporada` gana `TipoExcelTemporada tipoExcel` como último componente y el JPQL `RESUMEN` lo añade.

- [ ] **Step 1: Test que recorre el esquema**

`EsquemaCatalogoTest` (`@SpringBootTest`, `@Autowired` de los seis repositorios y `TemporadaGuardadaRepository`):
- `seGuardaYLeeUnModeloConSusArticulosYColores`: guarda `Modelo("CAT1","ULL027","Candy",BOLSO)`, `Articulo("CAT1","ULL027.AL0103","ULL027","AL0103")` con `setCombinaciones(List.of("ANTE"))`, `setUnidadesPorCaja(6)`, y `ArticuloColor("CAT1","ULL027.AL0103","001","NEGRO","NEGRO",List.of("GRIS"))`; relee por id y comprueba `getCombinaciones()`, `getUnidadesPorCaja()`, `getColoresCombinacion()`.
- `unArticuloSinPielYSinCartonEsLegal`: `Articulo("CAT1","F63023","F63023",null)` se guarda; `tienePiel()` es falso, `getMedidaCaja()` es `""`.
- `laFotoGuardaSusBytesYElResumenNoLosCarga`: guarda `FotoCatalogo` con 3 bytes; `resumenes("CAT1", List.of("ULL027"))` devuelve uno con `nombreFichero` y `principal`.
- `laTemporadaLlevaSuTipoDeExcel`: `new TemporadaGuardada("CAT1","X","p.xlsx",new byte[]{1})` → `getTipoExcel() == PEDIDO`; `resumenes()` del repositorio devuelve el tipo.
- `tipoArticuloPorReferencia`: `porReferencia("UBL029.AL0104") == CINTURON`, `porReferencia("ULL027.AL0103") == BOLSO`.

- [ ] **Step 2: Comprobar que falla**

Run: `mvn -q test -Dtest=EsquemaCatalogoTest` → compilación fallida (clases inexistentes).

- [ ] **Step 3: Entidades, ids, repositorios y SQL**

Crear las clases de arriba. En V1 añadir las tablas con FKs `on delete cascade`: `articulo(cliente, codigo_modelo) → modelo`, `articulo(cliente, ref_piel) → piel` (**sin** cascade: borrar una piel no borra el artículo; `on delete set null`), `articulo_color(cliente, referencia) → articulo`, `foto_modelo(cliente, codigo_modelo) → modelo`, `modelo_temporada(cliente, codigo_modelo) → modelo` y `modelo_temporada(temporada_id) → temporada_guardada(id)`. Columna `temporada_guardada.tipo_excel varchar(20) not null`. **No** usar `@ManyToOne` en las entidades: columnas planas como el resto del paquete; la integridad la pone el SQL.

- [ ] **Step 4: Verde**

Run: `mvn -q test -Dtest=EsquemaCatalogoTest,TemporadasControllerTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/persistence src/main/resources/db/migration src/test/java/com/puntotres/packinglist/persistence/EsquemaCatalogoTest.java
git commit -m "catalogo: entidades modelo, articulo, articulo_color, piel, fotos y temporadas de modelo"
```

---

### Task 3: `CatalogoModelos`: alta de artículos, herencia y propagación

**Files:**
- Create: `service/catalogo/CatalogoModelos.java`
- Modify: `service/corte/ClienteCorte.java` (nada; se usa `partir` vía `DocumentosCorteService.reglasDeProduccion`)
- Test: `src/test/java/com/puntotres/packinglist/service/catalogo/CatalogoModelosTest.java`

**Interfaces:**
- Consumes: `DocumentosCorteService.reglasDeProduccion(String clave) → ClienteCorte` (`.partir(ref) → ReferenciaCorte(referencia, modelo, piel)`), repositorios de Task 2, `TipoArticulo.porReferencia`.
- Produces:
```java
@Service public class CatalogoModelos {
    public static final int MAXIMO_COMBINACIONES = 4;
    public enum CampoArticulo { MEDIDA_CAJA, UNIDADES_POR_CAJA, PESO_NETO_KG, FORRO, COMBINACION1, COMBINACION2, COMBINACION3, COMBINACION4;
        public boolean sePropaga() /* todos menos PESO_NETO_KG */ }
    public enum CampoModelo { NOMBRE, TIPO }
    /** Una referencia tocada por la propagación y lo que decía antes, para poder deshacer. */
    public record Propagada(String referencia, String anterior) {}
    public record Propagacion(List<Propagada> propagadas) {}

    public CatalogoModelos(ModeloRepository, ArticuloRepository, PielRepository, DocumentosCorteService reglas);

    public Optional<Modelo> modelo(String cliente, String codigo);
    public Optional<Articulo> articulo(String cliente, String referencia);
    /** Los artículos del modelo que tienen piel, por referencia. */
    public List<Articulo> hermanos(String cliente, String codigoModelo);

    /** Devuelve el artículo, creando piel, modelo y artículo si faltan (con herencia). Clave normalizada. */
    @Transactional public Articulo asegurarArticulo(String cliente, String referencia);
    @Transactional public Modelo asegurarModelo(String cliente, String codigo, String nombre);

    /** Escribe el campo y propaga a los hermanos con piel cuyo valor era el antiguo o vacío. propagar=false solo escribe. Valor vacío borra el campo. */
    @Transactional public Propagacion guardarCampo(String cliente, String referencia, CampoArticulo campo, String valor, boolean propagar);
    @Transactional public void guardarCampoModelo(String cliente, String codigo, CampoModelo campo, String valor);
    @Transactional public void borrarModelo(String cliente, String codigo);     // también sus artículos, colores, fotos y temporadas (cascade del SQL + repos)
    @Transactional public void borrarArticulo(String cliente, String referencia);
    public List<Modelo> modelosDe(String cliente);
    public List<Articulo> articulosDe(String cliente);
}
```
Herencia en `asegurarArticulo`: si el artículo nuevo **tiene piel** y el modelo ya tiene hermanos con piel, copia `medidaCaja`, `unidadesPorCaja`, `forro` y las combinaciones **solo si todos los hermanos con ese dato relleno dicen lo mismo**; el peso nunca. `UNIDADES_POR_CAJA` en `guardarCampo` parsea entero positivo y lanza `IllegalArgumentException("Las unidades por caja son un número entero mayor que cero")`; `PESO_NETO_KG` admite coma decimal y positivo.

- [ ] **Step 1: Tests**

`CatalogoModelosTest` (`@SpringBootTest`), cliente de prueba `"AMI"` (parte por el punto) y `"APC"` (por el guion):
- `asegurarCreaPielModeloYArticulo`: `asegurarArticulo("AMI","ull900.al0100")` → `articulo("AMI","ULL900.AL0100")` presente con `codigoModelo="ULL900"`, `refPiel="AL0100"`; `modelo("AMI","ULL900")` presente con tipo `BOLSO` y nombre `""`; `PielRepository.findById(PielId("AMI","AL0100"))` presente con nombre `""`.
- `unCinturonNaceComoCinturon`: `asegurarArticulo("AMI","UBL900.AL0100")` → modelo tipo `CINTURON`.
- `elArticuloNuevoHeredaSoloLoQueTodosLosHermanosComparten`: `asegurarArticulo("AMI","ULL901.AL0001")` y `guardarCampo(..., MEDIDA_CAJA, "60x40x40", false)`, `UNIDADES_POR_CAJA "6"`, `PESO_NETO_KG "3,5"`; `asegurarArticulo("AMI","ULL901.AL0002")` y `UNIDADES_POR_CAJA "8"`; `asegurarArticulo("AMI","ULL901.AL0003")` → `medidaCaja="60x40x40"`, `unidadesPorCaja==null`, `pesoNetoKg==null`.
- `propagarTocaLosHermanosConElMismoValorOVaciosYRespetaLosDemas`: tres hermanos `ULL902.AL000{1,2,3}`; 1 y 2 con `UNIDADES_POR_CAJA "6"`, 3 con `"9"`; `guardarCampo(1, UNIDADES_POR_CAJA, "7", true)` → `propagadas` = `[Propagada("ULL902.AL0002","6")]`; 2 vale 7, 3 sigue 9; y un cuarto hermano vacío también recibe 7 (su `anterior` es `""`).
- `elPesoNoSePropaga`: `guardarCampo(..., PESO_NETO_KG, "2", true)` → `propagadas` vacía y el hermano sin peso.
- `unArticuloSinPielNiHeredaNiRecibe` (Review Focus 1): `asegurarArticulo("APC","PXCBC-F63023")` con `UNIDADES_POR_CAJA "4"`; `asegurarArticulo("APC","F63023")` → `tienePiel()` falso, `codigoModelo="F63023"`, `unidadesPorCaja==null`; `guardarCampo("APC","PXCBC-F63023", UNIDADES_POR_CAJA, "5", true)` no lo toca.
- `unidadesNoNumericasLanzan`: `assertThrows(IllegalArgumentException.class, () -> guardarCampo(..., UNIDADES_POR_CAJA, "seis", false))`.
- `borrarModeloSeLlevaSusArticulos`: tras `borrarModelo("AMI","ULL902")`, `articulo("AMI","ULL902.AL0001")` vacío.

- [ ] **Step 2: Rojo** — `mvn -q test -Dtest=CatalogoModelosTest` no compila.

- [ ] **Step 3: Implementar `CatalogoModelos`** en `service/catalogo/`. Normalizar claves con un `clave(String)` privado igual al de `MemoriaPieles`. Para partir: `reglas.reglasDeProduccion(cliente).partir(referencia)`; una referencia sin piel (`!tienePiel()`) crea el modelo con `codigo = referencia` y `refPiel = null`.

- [ ] **Step 4: Verde** — `mvn -q test -Dtest=CatalogoModelosTest` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "catalogo: alta de artículos con herencia y propagación entre pieles de un modelo"`.

---

### Task 4: `MemoriaReferencias` sobre `articulo`

**Files:**
- Modify: `persistence/MemoriaReferencias.java` (misma API pública)
- Delete: `persistence/MemoriaCajaReferencia.java`, `MemoriaCajaReferenciaId.java`, `MemoriaCajaReferenciaRepository.java`
- Modify: `db/migration/V1__esquema_inicial.sql` (quitar `memoria_caja_referencia`)
- Test: `persistence/MemoriaReferenciasTest.java` (existente; añadir un test)

**Interfaces:**
- Consumes: `CatalogoModelos.asegurarArticulo`, `ArticuloRepository`.
- Produces: `MemoriaReferencias.buscar(cliente, referencia) → Optional<DatosCaja>` y `recordar(...)` **sin cambios de firma**. `buscar` devuelve vacío si el artículo existe pero no tiene `medidaCaja` ni `unidadesPorCaja` (el taller necesita las dos; `DatosCaja.unidadesPorCaja` sigue siendo `int`).

- [ ] **Step 1: Añadir test** a `MemoriaReferenciasTest`: `recordarDaDeAltaElArticuloEnElCatalogo`: `recordar("AMI","ULL910.AL0100","60x40x40",6, 2.0)` → `CatalogoModelos.articulo("AMI","ULL910.AL0100")` presente, con `codigoModelo="ULL910"`, `unidadesPorCaja=6`, `pesoNetoKg=2.0`; y `unArticuloSinCartonNoSaleEnBuscar`: `catalogo.asegurarArticulo("AMI","ULL911.AL0100")` → `buscar` vacío.

- [ ] **Step 2: Rojo** — `mvn -q test -Dtest=MemoriaReferenciasTest`.

- [ ] **Step 3: Reescribir** `MemoriaReferencias` con constructor `(ArticuloRepository, CatalogoModelos)`: `recordar` llama a `asegurarArticulo` y escribe `medidaCaja`, `unidadesPorCaja` y, si viene positivo, `pesoNetoKg` (un `null` **no** borra el que había, como hoy). Borrar las tres clases viejas y su tabla del SQL. Comprobar con `grep -rn "MemoriaCajaReferencia" src` que no queda nada.

- [ ] **Step 4: Verde** — `mvn -q test -Dtest=MemoriaReferenciasTest,DigestionTallerServiceTest,PackingListTallerControllerTest` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "taller: la memoria de cartón y unidades vive en el artículo del catálogo"`.

---

### Task 5: `MemoriaPieles` sobre `piel`, `articulo` y `articulo_color`

**Files:**
- Modify: `persistence/MemoriaPieles.java`
- Delete: `persistence/MemoriaPiel.java`, `MemoriaPielId.java`, `MemoriaPielRepository.java`, `MemoriaPielesArticulo.java`, `MemoriaPielesArticuloId.java`, `MemoriaPielesArticuloRepository.java`, `MemoriaColoresPiel.java`, `MemoriaColoresPielId.java`, `MemoriaColoresPielRepository.java`
- Modify: `db/migration/V1__esquema_inicial.sql` (quitar las tres tablas viejas)
- Modify: `web/ColoresController.java` y `templates/colores*.html` solo si nombran `MemoriaPiel` (el tipo de `pielesDe` pasa a `List<Piel>`)
- Test: `persistence/MemoriaPielesTest.java` (existente; añadir dos)

**Interfaces:**
- Produces (sin cambio): `Recordadas`, `ColoresRecordados`, `buscar`, `recordar`, `buscarColores`, `recordarColores`, `guardarPiel`, `borrarPiel`, `incorporarPieles`, `MAXIMO_COMBINACIONES` (= `CatalogoModelos.MAXIMO_COMBINACIONES`). Cambia solo `pielesDe(String) → List<Piel>`.
- Produces (nuevo): `@Transactional public void recordarPielesDelArticulo(String cliente, String referencia, String forro, List<String> combinaciones)`: escribe forro y combinaciones del artículo (creándolo si falta) **sin tocar el nombre de la piel**; es lo que usa el camino del excel de producción.

- [ ] **Step 1: Tests nuevos** en `MemoriaPielesTest`: `recordarPielesDelArticuloNoTocaElNombreDeLaPiel`: `guardarPiel("AMI","AL0920","ALPACA")`; `recordarPielesDelArticulo("AMI","ULL920.AL0920","BADANA",List.of("ANTE"))` → `buscar("AMI","AL0920","ULL920.AL0920")` da `nombrePiel="ALPACA"`, `forro="BADANA"`, `combinaciones=[ANTE]`. Y `loRecordadoSeVeComoArticuloDelCatalogo`: tras `recordar(...)`, `CatalogoModelos.articulo(...)` presente con ese forro.

- [ ] **Step 2: Rojo.**

- [ ] **Step 3: Reescribir** `MemoriaPieles` con constructor `(PielRepository, ArticuloRepository, ArticuloColorRepository, CatalogoModelos)`. `recordar` → `asegurarArticulo` + `Piel` + forro/combinaciones; `recordarColores` → `ArticuloColor` con la misma clave de hoy; la clave de color es el código o, si llega vacío, **la descripción** (quien llama ya pasa `color.codigo()`; añadir en `recordarColores`/`buscarColores` que un `codigoColor` vacío devuelva `NINGUNO`/no guarde, como hoy). **Los textos libres se guardan normalizados** (`trim` + mayúsculas: nombre de piel, forro, combinaciones y colores de piel), para que `badana` y `BADANA` sean lo mismo; añadir a `MemoriaPielesTest` `losTextosSeGuardanEnMayusculas` (`recordar(..., "badana ", ...)` → `forro == "BADANA"`). La misma normalización en `CatalogoModelos.guardarCampo` para FORRO y COMBINACIONn. Borrar las nueve clases viejas y sus tablas del SQL; `grep -rn "MemoriaPiel\b\|MemoriaPielesArticulo\|MemoriaColoresPiel" src` vacío.

- [ ] **Step 4: Verde** — `mvn -q test -Dtest=MemoriaPielesTest,ColoresControllerTest,DocumentosCorteControllerTest` → PASS. Después `mvn -q test` entero.

- [ ] **Step 5: Commit** — `git commit -m "corte: nombres, forro, combinaciones y colores de piel viven en el catálogo"`.

---

### Task 6: Alta automática al guardar una temporada

**Files:**
- Modify: `service/catalogo/CatalogoModelos.java` (`incorporar`, `incorporarTemporada`, `temporadasDe`)
- Modify: `service/corte/ClienteCorte.java`, `AmiCorte.java` (`pedidoEntero`)
- Modify: `persistence/ArchivoTemporadas.java`, `web/TemporadasController.java`, `templates/temporadas.html`
- Modify: call sites de `paraElEnvio` y `paraElDesplegable` (lista en Step 3)
- Modify: `templates/documentos-corte.html` (filtrar por tipo en el script del desplegable)
- Test: `service/catalogo/CatalogoModelosIncorporarTest.java`, `web/TemporadasControllerTest.java`

**Interfaces:**
- Consumes: `ClienteCorte.leerPedido(byte[]) → PedidoCorte` (`articulos(): ArticuloCorte(referencia: ReferenciaCorte, colores: List<ColorCorte(codigo, descripcion, bolsos, prensa)>, nombreModelo)`), `ProduccionPuntotresExcel.leer(byte[], ClienteCorte) → ProduccionCorte.pedido()`, `TablaColores.incorporar(cliente, Map<String,String>) → Incorporacion(anadidos, completados, yaEstaban)`.
- Produces:
```java
// ClienteCorte
/** El pedido ENTERO, con lo que el corte deja fuera (los cinturones de AMI): es lo que entra al catálogo. */
default PedidoCorte pedidoEntero(byte[] excel) throws IOException { return leerPedido(excel); }   // AmiCorte lo sobreescribe sin el filtro seCorta

// CatalogoModelos
public record ResumenAlta(int articulos, int articulosNuevos, int modelosNuevos, int colores, int coloresNuevos, List<String> avisos) {}
@Transactional public ResumenAlta incorporar(String cliente, PedidoCorte pedido, Long temporadaId);
/** Lee el excel con el lector de su tipo y llama a incorporar. Lanza IllegalArgumentException/IOException si no se deja leer. */
@Transactional public ResumenAlta incorporarTemporada(TemporadaGuardada temporada) throws IOException;
public List<Long> temporadasDe(String cliente, String codigoModelo);
public Set<String> codigosDeModeloEn(String cliente, Long temporadaId);

// ArchivoTemporadas
public TemporadaGuardada guardar(Long id, String cliente, String temporada, String nombreFichero, byte[] excel, TipoExcelTemporada tipo);  // el de 5 parámetros se queda y pone PEDIDO
/** Solo las de tipo PEDIDO: el packing list, las etiquetas y /colores leen el excel como pedido del cliente. */
public Optional<TemporadaGuardada> conPedidoParaElEnvio(Long id, String cliente);
public Map<String, List<Map<String, String>>> paraElDesplegable();          // solo PEDIDO (sin cambio de firma)
public Map<String, List<Map<String, String>>> paraElDesplegableDelCorte();  // todas; cada entrada lleva además "tipo"
public List<ResumenTemporada> dePedido(String cliente);                      // para /colores
```
`incorporar`, por cada `ArticuloCorte`: `asegurarModelo(cliente, modelo, nombreModelo)` (nombre solo rellena hueco), `asegurarArticulo`; por cada color: clave = `codigo` o, vacío, `descripcion`; si tiene código, acumular `codigo → descripcion` para un único `TablaColores.incorporar` al final; crear `ArticuloColor` si falta con `colorPiel = descripcion` del color del bolso; vincular `ModeloTemporada(cliente, modelo, temporadaId)` si `temporadaId != null` y no existe. Cuenta nuevos contra existentes.

- [ ] **Step 1: Tests de servicio** (`CatalogoModelosIncorporarTest`, `@SpringBootTest`, leyendo `/ejemplos/EAN PUNTOTRES H26.xlsx` y `/ejemplos/corte/PROD_APC_SPRING27.xlsx` del classpath; cliente de prueba para no chocar con otros tests: usar un `temporadaId` propio guardado por `ArchivoTemporadas`):
- `elPedidoRealDeAmiDaDeAltaTodosSusArticulosYColores`: `incorporarTemporada` de una temporada AMI con ese excel → `articulos > 30`, `coloresNuevos > 0` o `colores > 0`, hay un modelo que empieza por `UBL` (los cinturones entran), `codigosDeModeloEn(cliente, id)` no vacío.
- `repetirNoDuplicaNiPisa` (Review Focus 2): `guardarCampoModelo(cliente, <primer modelo>, NOMBRE, "MI NOMBRE")`; segunda llamada → `articulosNuevos == 0`, `modelosNuevos == 0`, el nombre sigue siendo `"MI NOMBRE"`.
- `elExcelDeProduccionDeUnGenericoEntraEnteroComoModelo`: temporada `PRODUCCION` del cliente `KAMI` con `PROD_APC_SPRING27.xlsx` → hay artículos, todos `!tienePiel()`, `modelo.codigo == referencia`.
- `unColorSinCodigoEntraPorSuDescripcionYNoEnLaTablaDeColores` (Review Focus 5): `incorporar("KAMI", PedidoCorte con ArticuloCorte(ref "K900", [ColorCorte("", "AMARILLO/ GRIS", 3)]), null)` → `ArticuloColorRepository.findById(ArticuloColorId("KAMI","K900","AMARILLO/ GRIS"))` presente; `TablaColores.de("KAMI")` sin ese código.

- [ ] **Step 2: Tests web** en `TemporadasControllerTest`:
- Cambiar los que esperan rechazo de un cliente sin `pedido-cliente` (`grep -n "no trabaja con excel" src/test`) por: se guarda, con `tipoExcel == PRODUCCION`.
- `guardarUnaTemporadaConExcelRealDejaElAvisoEmergente`: `multipart("/temporadas").file(pedido real H26).param("cliente","AMI").param("temporada","CAT-H26")` → `flash().attributeExists("altaCatalogo")`, y `catalogo.codigosDeModeloEn("AMI", id)` no vacío.
- `unExcelQueNoSeDejaLeerGuardaLaTemporadaYAvisa`: el `alta()` de siempre (bytes falsos) sigue redirigiendo a `/temporadas`, y `flash().attribute("aviso", containsString("no se han podido extraer"))`.
- `borrarUnaTemporadaNoBorraNingunModelo`: alta con el H26, `POST /temporadas/borrar` → `codigosDeModeloEn` vacío pero `catalogo.modelo("AMI", <ese modelo>)` sigue presente.
- `elDesplegableDeLaEntradaNoOfreceTemporadasDeProduccion` (Review Focus 3): alta de `KAMI` → `archivo.paraElDesplegable()` sin `KAMI`; `paraElDesplegableDelCorte()` con él y `tipo == "PRODUCCION"`; `conPedidoParaElEnvio(id, "KAMI")` vacío.

- [ ] **Step 3: Implementar**
- `ArchivoTemporadas` como en Interfaces; `ResumenTemporada.tipoExcel` ya existe (Task 2).
- Cambiar los sitios que leen el excel como pedido a `conPedidoParaElEnvio`: `PackingListController:320`, `PackingListTallerController:155` y `:225`, `EtiquetasArticuloController:118`, `ColoresController:156` (y `:125` → `dePedido`), `DocumentosCorteController:193` (camino PEDIDO). `DocumentosCorteController:155` (producción, solo el nombre) se queda en `paraElEnvio`; `DocumentosCorteController:119` → `paraElDesplegableDelCorte()`, y en `documentos-corte.html` el script que rellena el desplegable salta las entradas con `tipo === 'PRODUCCION'` cuando el origen es `PEDIDO`.
- `TemporadasController`: ofrecer todos los clientes (`clientesProperties.getClientes()`), `tipo = config.isPedidoCliente() ? PEDIDO : PRODUCCION`, mensaje del fichero según tipo, tras `guardar` con excel llamar a `catalogo.incorporarTemporada(guardada)` en `try`: éxito → `redirect.addFlashAttribute("altaCatalogo", resumen)`, `("altaTemporadaId", id)`, `("altaCliente", cliente)`; `IllegalArgumentException | IOException | RuntimeException` → `addFlashAttribute("aviso", "Guardada la temporada, pero no se han podido extraer sus artículos al catálogo: " + mensaje)`.
- `temporadas.html`: columna "Excel" (pedido / producción); rótulo del `<input type=file>` que cambia con el cliente elegido (`data-tipo` en cada `<option>`, script pequeño); `<dialog id="altaCatalogo" open>` cuando hay `altaCatalogo` con el texto "Se han guardado los artículos de la temporada X: N artículos (M nuevos), C colores (D nuevos)" y un enlace `th:href="@{/catalogo(cliente=${altaCliente}, temporada=${altaTemporadaId})}"` «Ver en el catálogo» más «Cerrar». Estilos del `dialog` en `estilo.css` (`.emergente`).

- [ ] **Step 4: Verde** — `mvn -q test -Dtest=CatalogoModelosIncorporarTest,TemporadasControllerTest,ColoresControllerTest,PackingListControllerTest,EtiquetasArticuloControllerTest,DocumentosCorteControllerTest`; luego la suite entera.

- [ ] **Step 5: Commit** — `git commit -m "temporadas: al guardar una temporada sus artículos entran al catálogo; temporadas de producción para los genéricos"`.

---

### Task 7: El corte lee y escribe el catálogo también con el excel de producción, y las fotos se guardan

**Files:**
- Create: `service/catalogo/FotosCatalogo.java`
- Modify: `web/DocumentosCorteController.java`, `web/CorteEnCurso.java`
- Test: `service/catalogo/FotosCatalogoTest.java`, `web/DocumentosCorteControllerTest.java` (añadir)

**Interfaces:**
- Consumes: `FotosTemporada(Map<String,List<FotoModelo>>, avisos)`, `FotoModelo(modelo, nombreOriginal, Path original)`, `ConversionFotos.reducida(FotoModelo) → Optional<Path>`, `MemoriaPieles.recordarPielesDelArticulo` (Task 5), `ReductorImagen.LADO_MAXIMO`.
- Produces:
```java
@Service public class FotosCatalogo {
    public FotosCatalogo(FotoCatalogoRepository, CatalogoModelos);
    public List<ResumenFoto> de(String cliente, String codigoModelo);
    public Map<String, List<ResumenFoto>> de(String cliente, Collection<String> modelos);
    public Optional<FotoCatalogo> conBytes(Long id);
    /** Guarda un JPEG ya reducido. Si el modelo ya tiene una foto con ese nombre, devuelve esa y no guarda nada. La primera foto de un modelo nace principal. */
    @Transactional public ResumenFoto guardar(String cliente, String codigoModelo, String nombreFichero, byte[] jpeg);
    @Transactional public void marcarPrincipal(String cliente, String codigoModelo, Long fotoId);
    @Transactional public void borrar(Long id);  // si era principal, la siguiente por orden pasa a serlo
    /** Escribe las fotos de esos modelos en directorio/catalogo/<modelo>/<nombre> y las devuelve como FotosTemporada, con la principal llamada "principal.jpg" para que indicePorDefecto la elija. */
    public FotosTemporada comoFotosTemporada(String cliente, Collection<String> modelos, Path directorio) throws IOException;
}
// CorteEnCurso: campo boolean fotosDelCatalogo + getter, parámetro nuevo en cargar(..., OrigenCorte origen, boolean fotosDelCatalogo); el de 10 parámetros queda y pone false.
```

- [ ] **Step 1: Tests de `FotosCatalogo`**: `laPrimeraFotoNaceComoPrincipalYNoSeDuplicaPorNombre` (guardar dos veces "a.jpg" → una fila; `principal` true), `marcarPrincipalQuitaLaAnterior`, `comoFotosTemporadaEscribeLosFicherosYLaPrincipalSeLlamaPrincipal` (`indicePorDefecto(modelo)` apunta a la marcada).

- [ ] **Step 2: Tests del controlador** en `DocumentosCorteControllerTest`, siguiendo sus helpers de carga por producción (`produccion("PROD_APC_SPRING27.xlsx")`):
- `conElExcelDeProduccionElForroSaleDelCatalogo`: `memoriaPieles.recordarPielesDelArticulo("APC", <una referencia del excel>, "BADANA", List.of())`; cargar por producción → la fila de esa referencia tiene `forro == "BADANA"`; su `nombrePiel` es el del excel, no el de `Piel`.
- `generarConProduccionGuardaForroYColoresPeroNoElNombreDePiel`: `guardarPiel("APC", <refPiel>, "NOMBRE MIO")`; cargar por producción, aplicar un forro en el formulario, generar → `buscar(...)` da `forro` tecleado y `nombrePiel == "NOMBRE MIO"`.
- `sinCarpetaDeFotosSeUsanLasDelCatalogo` (Review Focus 4): `fotosCatalogo.guardar("APC", <modelo del excel>, "a.jpg", <jpeg pequeño generado con ImageIO>)`; cargar por producción sin fotos → la pantalla de pieles **no** lleva el aviso de sin fotos (`model().attribute("sinFotos", false)`) y la fila del modelo lista `a.jpg`.
- `lasFotosSubidasSeGuardanEnElCatalogoAlGenerar`: cargar con un zip de una foto JPG en carpeta `<modelo>/` (helper existente del test de fotos, o crear el zip con `ZipOutputStream`), esperar `/documentos-corte/progreso` hasta `terminada`, generar → `fotosCatalogo.de("APC", modelo)` tiene una foto principal.

- [ ] **Step 3: Implementar**
- `FotosCatalogo` como arriba.
- `DocumentosCorteController.cargar`: con `sinFotos`, `leidas = fotosCatalogo.comoFotosTemporada(clienteCorte.clave(), pedidoCorte.modelos(), directorio)`, `sinFotos = leidas.total() == 0`, `fotosDelCatalogo = !sinFotos`. Tras `deLaProduccion.rellenar(filas)` llamar a un `completarDesdeCatalogo(cliente)` nuevo que, por fila, rellena **solo lo vacío**: forro, combinaciones (si la fila no trae ninguna), `colorForro` y `coloresCombinacion` de `buscarColores`. Nunca `nombrePiel` ni `colorPiel`.
- `generar`: `if (enCurso.isDeProduccion()) guardarPielesDeProduccion(); else guardarPieles();` donde la de producción usa `recordarPielesDelArticulo` + `recordarColores`. Tras `esperar()` y si `!enCurso.isFotosDelCatalogo()`: por cada fila incluida, por cada `FotoModelo` de su modelo, `reducida(foto)` presente → `fotosCatalogo.guardar(cliente, modelo, nombreOriginal, Files.readAllBytes(path))`; la del índice `fila.getFotoPrincipal()` → `marcarPrincipal`. Un modelo se procesa una vez aunque tenga varias filas.

- [ ] **Step 4: Verde** — `mvn -q test -Dtest=FotosCatalogoTest,DocumentosCorteControllerTest`, luego la suite.

- [ ] **Step 5: Commit** — `git commit -m "corte: con el excel de produccion lee y escribe el catalogo, y las fotos se guardan en el"`.

---

### Task 8: Pantalla `/catalogo`

**Files:**
- Create: `web/CatalogoController.java`, `web/FilaCatalogoVista.java`
- Create: `templates/catalogo.html`
- Modify: `static/estilo.css`, `templates/menu.html` (cuarto panel; `.menu-paneles` pasa a `grid-template-columns: repeat(2, 1fr)` con cuatro paneles, o la rejilla que quepa: comprobar a ojo)
- Test: `web/CatalogoControllerTest.java`

**Interfaces:**
- Consumes: `CatalogoModelos` (Task 3/6), `FotosCatalogo.de(cliente, modelos)`, `ArchivoTemporadas.de(cliente)`, `CatalogoTaras.tamanosDeMayorAMenor()`, `ClientesProperties.getClientes()` / `clientePara`, `TablaColores.de(cliente)`, `ArticuloColorRepository` vía `CatalogoModelos.coloresDe`.
- Produces:
```java
// CatalogoModelos (añadir)
public List<ArticuloColor> coloresDe(String cliente, String referencia);
@Transactional public ArticuloColor asegurarColor(String cliente, String referencia, String codigoColor);
public enum CampoColor { COLOR_PIEL, COLOR_FORRO, COLOR_COMBINACION1, COLOR_COMBINACION2, COLOR_COMBINACION3, COLOR_COMBINACION4 }
@Transactional public void guardarCampoColor(String cliente, String referencia, String codigoColor, CampoColor campo, String valor);

// web
public record FilaCatalogoVista(String codigoModelo, String nombre, String tipo, List<String> temporadas, int numeroFotos, Long fotoPrincipalId, List<ArticuloVista> articulos) {
    public record ArticuloVista(String referencia, String refPiel, String nombrePiel, String forro, List<String> combinaciones /* 4 */, String medidaCaja, boolean medidaConTara, Integer unidadesPorCaja, Double pesoNetoKg, List<ColorVista> colores) {}
    public record ColorVista(String codigoColor, String descripcion, String colorPiel, String colorForro, List<String> coloresCombinacion /* 4 */) {} }

@Controller public class CatalogoController {
    @GetMapping("/catalogo") String catalogo(@RequestParam(required=false) String cliente, @RequestParam(required=false) Long temporada, Model model); // atributos: clientes (Map clave→nombre), cliente, temporadas (List<ResumenTemporada>), temporadaId, filas (List<FilaCatalogoVista>), tamanos (List<String>), coloresCliente (List<ColorCliente>), mensaje/mensajeError flash
    @PostMapping("/catalogo/articulo/celda") @ResponseBody ResponseEntity<Map<String,Object>> celdaArticulo(cliente, referencia, campo, valor, @RequestParam(defaultValue="true") boolean propagar); // {ok, mensaje, propagadas:[{referencia, anterior}]}
    @PostMapping("/catalogo/modelo/celda") @ResponseBody ... celdaModelo(cliente, codigo, campo, valor);
    @PostMapping("/catalogo/color/celda") @ResponseBody ... celdaColor(cliente, referencia, codigoColor, campo, valor);
    @PostMapping("/catalogo/color/nuevo") String nuevoColor(cliente, referencia, codigoColor, temporada);   // redirect a /catalogo?cliente&temporada
    @PostMapping("/catalogo/modelo/nuevo") String nuevoModelo(cliente, codigo, nombre, tipo, temporada);
    @PostMapping("/catalogo/articulo/nuevo") String nuevoArticulo(cliente, referencia, temporada);           // asegurarArticulo; si no tiene piel y el cliente parte referencias, flash aviso "sin piel: no hereda"
    @PostMapping("/catalogo/modelo/borrar") String borrarModelo(cliente, codigo, temporada);
    @PostMapping("/catalogo/articulo/borrar") String borrarArticulo(cliente, referencia, temporada);
}
```
Errores de celda: `IllegalArgumentException` → `400 {ok:false, mensaje}`. Campos por nombre del enum (`UNIDADES_POR_CAJA`), `IllegalArgumentException` de `valueOf` → 400.

- [ ] **Step 1: Tests** (`CatalogoControllerTest`, `@SpringBootTest @AutoConfigureMockMvc`):
- `laPantallaAgrupaLosArticulosPorModelo`: dos artículos `ULL930.AL0001/2` → `GET /catalogo?cliente=AMI` → `model().attribute("filas", hasItem(hasProperty("codigoModelo", is("ULL930"))))` y esa fila con 2 artículos.
- `filtraPorTemporada`: modelo vinculado a una temporada y otro no → con `?temporada=id` solo sale el vinculado.
- `guardarUnaCeldaContestaJsonConLasPropagadas`: `POST /catalogo/articulo/celda` `campo=UNIDADES_POR_CAJA valor=7` → `jsonPath("$.ok", true)`, `jsonPath("$.propagadas[0].referencia")` del hermano.
- `unValorInvalidoDa400ConMensaje`: `valor=siete` → `status().isBadRequest()`, `jsonPath("$.mensaje", containsString("entero"))`.
- `elTipoDelModeloEsInformativo`: `POST /catalogo/modelo/celda campo=TIPO valor=CINTURON` sobre `ULL931` → `GET` lo enseña como `CINTURON`, y `CajaData.esCinturon("ULL931.AL0001")` sigue siendo falso (anclar la frase del spec).
- `nuevoArticuloSinPielAvisa`: `POST /catalogo/articulo/nuevo cliente=APC referencia=F63099` → `flash().attribute("aviso", containsString("sin piel"))`.

- [ ] **Step 2: Rojo.**

- [ ] **Step 3: Implementar controlador, vista y estilos.**

`catalogo.html` (cabecera `fragmentos :: cabecera('Catálogo de modelos')`):
1. `<section class="tarjeta catalogo-cabecera">`: `<select name=cliente>` (navega con `GET`), `<input type=search id=buscar placeholder="Referencia, modelo, nombre o piel">` (filtra en el navegador: oculta `<tbody>` sin coincidencia), `<select name=temporada>` ("Todas" + las del cliente), y el grupo de **vistas**: `<div class="vistas" role="tablist">` con botones `data-vista="packaging|corte|fotos|todo"` y uno `personalizada` que solo aparece al tocar columnas.
2. `<div class="chips" id="columnasActivas">`: la rellena el script; cada chip `<button class="chip" data-col>` con `×`; último `<button class="chip chip-anadir">+ columna</button>` que abre `<div class="desplegable-columnas" hidden>` con `<input type=search>` y una casilla por columna oculta.
3. `<table class="tabla-catalogo">`: `<thead>` con un `<th data-col="…">` por columna: `foto, modelo, nombre, tipo, temporadas, nfotos, referencia, piel, nombrePiel, forro, comb1, comb2, comb3, comb4, carton, unidades, peso, acciones`. Un `<tbody class="modelo" data-modelo data-buscar="…">` por fila; la primera `<tr>` lleva las celdas del modelo con `th:rowspan="${#lists.size(fila.articulos)}"`: `foto` (miniatura `<img th:src="@{/catalogo/foto/{id}(id=${fila.fotoPrincipalId})}">` si hay; Task 9 la hace desplegable), `nombre` y `tipo` editables (`input.celda` / `select.celda` con `data-url="/catalogo/modelo/celda"`, `data-campo`), `temporadas` como `<span class="etiqueta">`, `nfotos`. Cada `<tr>` de artículo: `referencia`, `piel` (texto), `nombrePiel` (texto, enlace a `/colores?cliente=`), `forro`, `comb1..4`, `carton` (`<select class="celda">` con `tamanos` y la opción `(sin tara)` si el valor no está, como `revision.html`), `unidades`, `peso` (`inputmode=decimal`), `acciones` (`▸ colores`, borrar). Cada celda editable lleva al lado `<span class="estado-celda" aria-live="polite">`.
4. Debajo de cada `<tr>` de artículo, `<tr class="detalle-colores" hidden>` con un `<td colspan>` que contiene la tabla de `ColorVista` (celdas editables con `data-url="/catalogo/color/celda"`, `data-referencia`, `data-codigo-color`) y el formulario `+ color` (`<select>` de `coloresCliente` que aún no tenga el artículo, más un `<input>` libre para un color sin código).
5. Pie: dos `<details>` «+ modelo» y «+ artículo» con sus formularios.
6. Estado vacío según `cliente == null` / `filas` vacío, con el texto del spec. Enlaces a `/colores?cliente=` y `/temporadas` en la cabecera.
7. Autocompletado: `<datalist id="forros">` y `<datalist id="combinaciones">` con los valores distintos ya guardados del cliente (atributos del modelo `forrosConocidos` y `combinacionesConocidas`, de `CatalogoModelos.textosConocidos(cliente) → record TextosConocidos(List<String> forros, List<String> combinaciones)`), referenciados con `list=` desde las celdas de forro y combinación.

Script en línea (como `colores.html`):
- `VISTAS = {packaging: [foto, referencia, nombre, tipo, carton, unidades, peso], corte: [foto, referencia, modelo, nombre, nombrePiel, forro, comb1, comb2, comb3, comb4], fotos: [foto, modelo, nombre, nfotos, temporadas], todo: TODAS}`; `acciones` siempre visible. Aplicar = añadir/quitar la clase `oculta` a todas las celdas con ese `data-col`. Recordar en `localStorage` (`catalogo.vista.<cliente>`, `catalogo.columnas.<cliente>`); quitar o añadir una columna pone la vista en `personalizada`.
- Edición: `change` en `.celda` → `fetch` `POST` (`URLSearchParams` con cliente, los `data-*` y `valor`), estado «Guardando…» → «Guardado» (clase `guardado`, se apaga sola a los 2 s) o el `mensaje` en rojo con el valor tecleado conservado; `Escape` restaura `defaultValue`; `Enter` hace `blur`. Con `propagadas` no vacías, el estado dice «aplicado también a N pieles» con un botón «deshacer» que reenvía a cada `referencia` su `anterior` con `propagar=false` y actualiza esas celdas en la tabla.
- `▸ colores` alterna el `hidden` de la fila de detalle.
- Buscador: oculta cada `tbody.modelo` cuyo `data-buscar` (referencias, modelo, nombre, pieles en mayúsculas) no contenga el texto.

`estilo.css`: `.catalogo-cabecera` (flex, wrap, gap), `.vistas button` (segmentados: borde compartido, el activo relleno), `.chips`/`.chip` (píldora con `×`), `.desplegable-columnas` (posición absoluta bajo el chip, lista con scroll), `.tabla-catalogo .oculta { display: none }`, `.tabla-catalogo tbody.modelo { border-top: 2px solid … }`, `input.celda, select.celda` (sin borde hasta `:focus`, ancho 100 %), `.estado-celda.guardado`, `.estado-celda.error`, `.etiqueta`, `.emergente` (del Task 6 si no se hizo allí). Reusar tokens y tipografía del CSS existente; sin colores nuevos salvo los de estado.

- [ ] **Step 4: Verde** — `mvn -q test -Dtest=CatalogoControllerTest,RutasTest` y la suite.

- [ ] **Step 5: Comprobación a ojo** — `mvn -q spring-boot:run`, abrir `/catalogo?cliente=AMI` con la temporada de prueba guardada: cambiar unidades por caja de un artículo y ver la nota de propagación y el deshacer; cambiar de vista y recargar (se recuerda); quitar una columna con la × y añadirla con `+ columna`. Matar el proceso.

- [ ] **Step 6: Commit** — `git commit -m "catalogo: pantalla /catalogo con vistas por tarea, chips de columnas y edicion en celda"`.

---

### Task 9: Fotos en la pantalla

**Files:**
- Modify: `web/CatalogoController.java`, `templates/catalogo.html`, `static/estilo.css`
- Test: `web/CatalogoControllerTest.java` (añadir)

**Interfaces:**
- Consumes: `FotosCatalogo` (Task 7), `ConversorFotos.convertir(List<FotoModelo>, Path) → ConversionFotos` (`esperar()`, `reducida(FotoModelo)`), `CorteEnCurso.PREFIJO_DIRECTORIO` (reutilizar el prefijo o uno propio `catalogo-fotos-`).
- Produces:
```java
@GetMapping("/catalogo/foto/{id}") ResponseEntity<byte[]> foto(@PathVariable Long id);                       // image/jpeg, Cache-Control: private, max-age=86400; 404 si no existe
@PostMapping("/catalogo/foto/subir") String subir(cliente, codigo, @RequestParam List<MultipartFile> fotos, temporada); // directorio temporal, convertir (HEIC incluido), esperar, guardar cada reducida con su nombre original; borrar el directorio; flash mensaje "N fotos guardadas en <modelo>"
@PostMapping("/catalogo/foto/principal") String principal(cliente, codigo, id, temporada);
@PostMapping("/catalogo/foto/borrar") String borrarFoto(id, cliente, temporada);
```

- [ ] **Step 1: Tests**: `laFotoSeSirveComoJpeg` (guardar vía `FotosCatalogo`, `GET /catalogo/foto/{id}` → `content().contentType(IMAGE_JPEG)` y los bytes); `subirUnaFotoLaGuardaReducidaYComoPrincipal` (multipart con un JPG de 2000×1000 generado con `ImageIO` → `fotosCatalogo.de(...)` una foto, y al leerla con `ImageIO` el lado mayor ≤ `ReductorImagen.LADO_MAXIMO`); `borrarLaPrincipalPasaElTestigo` (dos fotos, borrar la principal → la otra es principal).

- [ ] **Step 2: Rojo.**

- [ ] **Step 3: Implementar** los cuatro endpoints y, en `catalogo.html`, la celda `foto` del modelo como `<details class="galeria">`: `<summary>` con la miniatura (o un recuadro «sin foto»), dentro una rejilla de miniaturas con la principal marcada, por foto botones «Principal» y «Borrar» (formularios `POST` pequeños), y el formulario «Subir fotos» (`<input type=file multiple accept="image/*,.heic">`). CSS: `.galeria` (miniaturas 96 px, la principal con borde de acento, rejilla).

- [ ] **Step 4: Verde** — `mvn -q test -Dtest=CatalogoControllerTest` y la suite.

- [ ] **Step 5: Commit** — `git commit -m "catalogo: galeria de fotos por modelo, subir, principal y borrar"`.

---

### Task 10: Documentación y cierre

**Files:**
- Modify: `CLAUDE.md` (sección nueva «Catálogo de modelos»; corregir «la tabla de pieles … no consulta ni escribe la memoria» del bloque del excel de producción; la frase «Si entra Flyway (rama envios-dhl), las tablas … necesitan su migración» desaparece; la lista de memorias del bloque «Memoria de referencias y taras»; mencionar que `./datos` se borra al pasar a esta versión)
- Modify: `README.md` (una línea sobre el catálogo y `/catalogo` en el índice de pantallas, si lo hay)
- Modify: `docs/superpowers/specs/2026-11-01-catalogo-modelos-design.md` (estado → implementado; anotar el desvío: excel ilegible guarda y avisa)

- [ ] **Step 1: Escribir las secciones.** En CLAUDE.md, lo que no se deduce del código: dos niveles modelo/artículo y por qué; herencia y propagación (solo desde la pantalla, nunca desde el taller); `tipo` informativo; `AL245`/`AL0245` sin normalizar a propósito; clave de color = descripción cuando no hay código y sin FK a `color_cliente`; `tipo_excel` y qué desplegables filtran; producción no escribe el nombre de la piel; fotos del catálogo cuando no se sube carpeta; V1 única y por qué; `CASE_INSENSITIVE_IDENTIFIERS` del perfil test.

- [ ] **Step 2: Suite entera** — `mvn -q clean test` → BUILD SUCCESS (el `clean` por el aviso de CLAUDE.md sobre clases a medio compilar).

- [ ] **Step 3: Commit** — `git commit -m "docs: catalogo de modelos en CLAUDE.md y README"`.
