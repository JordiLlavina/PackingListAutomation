# Packing List Taller — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir una cuarta vía de entrada al asistente de packing lists que digiere el packing list Excel del taller, lo completa con el excel de pedido del cliente y regenera cajas y palets desde cero con las normas de cada cliente, desembocando en el mismo `EnvioInput` que ya usan las otras tres entradas.

**Architecture:** Paquete nuevo `service/taller/` con servicios sin estado (lector de Excel, objetivos de pedido por cliente, reparto, agrupación en cajas, apilado en palets, generador). La salida es un `EnvioInput`, que se entrega a un `PreparacionRevisionService` extraído de `PackingListController` y compartido por las cuatro entradas. Estado nuevo (memoria de referencias y taras) en H2 embebido con JPA.

**Tech Stack:** Java 17, Spring Boot 3.5 (web, thymeleaf, validation, **data-jpa nuevo**), **H2 nuevo**, Apache POI 5.4.1, JUnit 5.

**Spec:** [docs/superpowers/specs/2026-09-05-packing-list-taller-design.md](../specs/2026-09-05-packing-list-taller-design.md)

## Global Constraints

- **Idioma:** nombres de clase en inglés o español según ya hace el proyecto; javadoc, comentarios, avisos y UI **en español**. Los avisos al usuario usan vocabulario de almacén: "la entrada", nunca "el JSON".
- **Nunca fallar en silencio, nunca bloquear por datos que un humano puede resolver.** Los servicios devuelven DTOs de resultado con avisos, no lanzan excepciones. Excepción admitida: columna obligatoria ausente en el Excel de taller, que sí bloquea.
- **La altura de caja es la ÚLTIMA dimensión**: `60x40x45` mide 45 cm de alto.
- **Ningún test fija un valor de tara de `application.yml`.** Los tests que necesitan una tara concreta la siembran ellos.
- Los pesos son `Double`; `null` significa "desconocido". No cambiar a primitivo.
- Tests con JUnit 5 puro y `new` por defecto; `@SpringBootTest` solo para capa web y cableado real.
- Los tests de lectores de Excel se anclan contra los **ficheros reales** del repo.
- Comandos: `mvn test`, `mvn test -Dtest=NombreTest`, `mvn spring-boot:run`.
- Rama: `packing-list-taller`. Un commit por tarea.

---

### Task 1: Catálogo de taras tras una interfaz

Prepara el terreno para mover las taras a base de datos sin romper `Main.java` ni los tests que construyen `TaraProperties` con `new`.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/config/CatalogoTaras.java`
- Modify: `src/main/java/com/puntotres/packinglist/config/TaraProperties.java`
- Modify: `src/main/java/com/puntotres/packinglist/service/WeightInferenceService.java:48-51`
- Modify: `src/main/java/com/puntotres/packinglist/web/PackingListController.java:95,111,126,319,586`

**Interfaces:**
- Produces: `interface CatalogoTaras { Optional<Double> taraPara(String tamanoCaja); List<String> tamanosDeMayorAMenor(); }`, implementada por `TaraProperties`.

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/com/puntotres/packinglist/config/CatalogoTarasTest.java`:

```java
class CatalogoTarasTest {

    @Test
    void taraPropertiesEsUnCatalogoDeTaras() {
        TaraProperties props = new TaraProperties();
        props.setTaras(Map.of("60x40x40", 1.6));

        CatalogoTaras catalogo = props;

        assertEquals(1.6, catalogo.taraPara("60X40X40 ").get());
        assertEquals(List.of("60x40x40"), catalogo.tamanosDeMayorAMenor());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=CatalogoTarasTest`
Expected: FAIL de compilación, `CatalogoTaras` no existe.

- [ ] **Step 3: Crear la interfaz e implementarla**

```java
package com.puntotres.packinglist.config;

import java.util.List;
import java.util.Optional;

/**
 * Tabla de taras (peso del cartón vacío en kg) por tamaño de caja.
 *
 * Existe como interfaz porque hay dos orígenes: el bloque
 * packing-list.taras de application.yml, que sirve de semilla y es lo que
 * usan Main.java y los tests unitarios, y la tabla de la base de datos,
 * que es la que manda en la aplicación arrancada.
 */
public interface CatalogoTaras {

    /** Tara del tamaño indicado, o vacío si no está en la tabla. */
    Optional<Double> taraPara(String tamanoCaja);

    /**
     * Los tamaños conocidos, de la caja más grande a la más pequeña, para
     * los desplegables de la web. El orden es por VOLUMEN y no alfabético.
     */
    List<String> tamanosDeMayorAMenor();
}
```

`TaraProperties` pasa a `public class TaraProperties implements CatalogoTaras` y sus dos métodos llevan `@Override`. Nada más cambia en esa clase.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=CatalogoTarasTest`
Expected: PASS

- [ ] **Step 5: Cambiar los consumidores al tipo de la interfaz**

En `WeightInferenceService`: el campo y el parámetro del constructor pasan de `TaraProperties` a `CatalogoTaras` (el import cambia; el cuerpo no).
En `PackingListController`: el campo `taraProperties` pasa a `CatalogoTaras taras` (renombrar el campo y sus tres usos, líneas 126, 319 y 586).
`Main.java` no cambia: sigue construyendo `new TaraProperties()` y ahora encaja por subtipo.

- [ ] **Step 6: Toda la suite en verde**

Run: `mvn test`
Expected: PASS. Es un refactor puro; ningún test debería cambiar de resultado.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/config/ src/main/java/com/puntotres/packinglist/service/WeightInferenceService.java src/main/java/com/puntotres/packinglist/web/PackingListController.java src/test/java/com/puntotres/packinglist/config/CatalogoTarasTest.java
git commit -m "el catalogo de taras tras una interfaz, para poder cambiarle el origen"
```

---

### Task 2: Base de datos embebida y taras persistidas

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/puntotres/packinglist/persistence/TaraCaja.java`
- Create: `src/main/java/com/puntotres/packinglist/persistence/TaraCajaRepository.java`
- Create: `src/main/java/com/puntotres/packinglist/persistence/CatalogoTarasJpa.java`
- Create: `src/test/resources/application-test.yml`
- Test: `src/test/java/com/puntotres/packinglist/persistence/CatalogoTarasJpaTest.java`

**Interfaces:**
- Consumes: `CatalogoTaras` (Task 1).
- Produces: `CatalogoTarasJpa` (bean `@Primary`) con `taraPara`, `tamanosDeMayorAMenor`, `guardar(String medida, double taraKg)`, `todas()` devolviendo `List<TaraCaja>` ordenada por volumen; `TaraCaja` con `getMedida()`, `getTaraKg()`, `getFechaActualizacion()`.

- [ ] **Step 1: Añadir dependencias**

En `pom.xml`, dentro de `<dependencies>`:

```xml
        <!-- Memoria del programa entre ejecuciones: medidas y unidades por
             caja aprendidas de cada referencia, y la tabla de taras. H2 en
             fichero para que sobreviva al reinicio sin instalar nada. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 2: Configurar la base de datos**

En `application.yml`, dentro del bloque `spring:`:

```yaml
  # Base de datos embebida en fichero: la memoria de referencias y la tabla
  # de taras tienen que sobrevivir al reinicio. El fichero vive junto al
  # ejecutable; borrarlo solo pierde lo aprendido, no rompe nada (las taras
  # se resiembran desde el bloque packing-list.taras de más abajo).
  datasource:
    url: jdbc:h2:file:./datos/packinglist;DB_CLOSE_DELAY=-1
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
```

Y en `.gitignore`, añadir `datos/`.

- [ ] **Step 3: Escribir el test que falla**

`src/test/java/com/puntotres/packinglist/persistence/CatalogoTarasJpaTest.java`:

```java
@SpringBootTest
@ActiveProfiles("test")
class CatalogoTarasJpaTest {

    @Autowired
    private CatalogoTarasJpa catalogo;

    @Autowired
    private TaraProperties semilla;

    @Test
    void alArrancarLaTablaSeSiembraConElYml() {
        // No se comprueba CUÁNTO pesa un cartón (eso es dato de almacén),
        // sino que el yml ha llegado entero a la tabla.
        assertEquals(semilla.tamanosDeMayorAMenor(), catalogo.tamanosDeMayorAMenor());
    }

    @Test
    void loGuardadoManda() {
        catalogo.guardar("99x99x99", 3.5);

        assertEquals(3.5, catalogo.taraPara(" 99X99X99 ").get());
        assertTrue(catalogo.tamanosDeMayorAMenor().contains("99x99x99"));
    }

    @Test
    void unTamanoQueNoEstaNoSeInventa() {
        assertTrue(catalogo.taraPara("11x11x11").isEmpty());
    }
}
```

`src/test/resources/application-test.yml` (base en memoria, para que los tests no toquen el fichero real):

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:test;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: create-drop
```

- [ ] **Step 4: Ejecutar y ver que falla**

Run: `mvn test -Dtest=CatalogoTarasJpaTest`
Expected: FAIL, `CatalogoTarasJpa` no existe.

- [ ] **Step 5: Implementar entidad, repositorio y catálogo**

`TaraCaja.java`:

```java
package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tara (peso del cartón vacío, en kg) de un tamaño de caja.
 *
 * La medida se guarda ya normalizada (minúsculas y sin espacios) porque es
 * la clave: "60X40X40 " de un excel y "60x40x40" del yml son la misma caja.
 */
@Entity
@Table(name = "tara_caja")
public class TaraCaja {

    @Id
    @Column(name = "medida", length = 40)
    private String medida;

    @Column(name = "tara_kg", nullable = false)
    private double taraKg;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected TaraCaja() {
    }

    public TaraCaja(String medida, double taraKg) {
        this.medida = medida;
        this.taraKg = taraKg;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public String getMedida() { return medida; }
    public double getTaraKg() { return taraKg; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }

    public void actualizar(double taraKg) {
        this.taraKg = taraKg;
        this.fechaActualizacion = LocalDateTime.now();
    }
}
```

`TaraCajaRepository.java`:

```java
package com.puntotres.packinglist.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaraCajaRepository extends JpaRepository<TaraCaja, String> {
}
```

`CatalogoTarasJpa.java`: implementa `CatalogoTaras`, anotado `@Service` y `@Primary`, con `TaraCajaRepository` y `TaraProperties` inyectados. Un `@EventListener(ApplicationReadyEvent.class)` siembra la tabla desde el yml **solo si está vacía**. Reutiliza las dos funciones estáticas de orden y normalización, que se extraen de `TaraProperties` a `MedidasCaja` (Task 5) — hasta entonces, se copian aquí y se unifican en la Task 5.

```java
    @Override
    public Optional<Double> taraPara(String tamanoCaja) {
        if (tamanoCaja == null) {
            return Optional.empty();
        }
        return repositorio.findById(normalizar(tamanoCaja)).map(TaraCaja::getTaraKg);
    }

    @Override
    public List<String> tamanosDeMayorAMenor() {
        return repositorio.findAll().stream()
                .map(TaraCaja::getMedida)
                .sorted(Comparator.comparingLong(CatalogoTarasJpa::volumen).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** Alta o actualización de una tara desde la pantalla /taras. */
    @Transactional
    public void guardar(String medida, double taraKg) {
        String clave = normalizar(medida);
        repositorio.findById(clave)
                .ifPresentOrElse(t -> t.actualizar(taraKg),
                        () -> repositorio.save(new TaraCaja(clave, taraKg)));
    }

    public List<TaraCaja> todas() {
        return tamanosDeMayorAMenor().stream()
                .map(m -> repositorio.findById(m).orElseThrow())
                .toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void sembrarSiEstaVacia() {
        if (repositorio.count() > 0) {
            return;
        }
        semilla.getTaras().forEach((medida, tara) ->
                repositorio.save(new TaraCaja(normalizar(medida), tara)));
    }
```

- [ ] **Step 6: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=CatalogoTarasJpaTest`
Expected: PASS

- [ ] **Step 7: Toda la suite en verde**

Run: `mvn test`
Expected: PASS. Los `@SpringBootTest` existentes ahora levantan también JPA; si alguno falla por no encontrar el perfil `test`, añadirle `@ActiveProfiles("test")`.

- [ ] **Step 8: Commit**

```bash
git add pom.xml .gitignore src/main/resources/application.yml src/main/java/com/puntotres/packinglist/persistence/ src/test/resources/application-test.yml src/test/java/com/puntotres/packinglist/persistence/
git commit -m "las taras viven en una base de datos embebida, sembrada desde el yml"
```

---

### Task 3: Pantalla de edición de taras

Sin ella, pesar un cartón pasaría de editar un yml a hacer un UPDATE a mano.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/web/TarasController.java`
- Create: `src/main/resources/templates/taras.html`
- Modify: `src/main/resources/templates/menu.html`
- Test: `src/test/java/com/puntotres/packinglist/web/TarasControllerTest.java`

**Interfaces:**
- Consumes: `CatalogoTarasJpa.todas()`, `.guardar(String, double)`.

- [ ] **Step 1: Escribir el test que falla**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TarasControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private CatalogoTarasJpa catalogo;

    @Test
    void laPantallaListaLasTarasConocidas() throws Exception {
        catalogo.guardar("77x77x77", 2.0);

        mvc.perform(get("/taras"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("taras"))
                .andExpect(content().string(containsString("77x77x77")));
    }

    @Test
    void guardarUnaTaraNuevaLaDejaEnElCatalogo() throws Exception {
        mvc.perform(post("/taras").param("medida", "50x30x20").param("taraKg", "0.9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/taras"));

        assertEquals(0.9, catalogo.taraPara("50x30x20").get());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=TarasControllerTest`
Expected: FAIL, no existe la ruta `/taras`.

- [ ] **Step 3: Implementar controlador y plantilla**

`TarasController`: `@GetMapping("/taras")` mete `catalogo.todas()` en el modelo y devuelve `"taras"`. `@PostMapping("/taras")` recibe `@RequestParam String medida, @RequestParam double taraKg`, llama a `guardar` y redirige a `/taras`. Una medida en blanco o una tara negativa vuelven con `mensajeError` en `RedirectAttributes`, sin guardar.

`taras.html`: reutiliza `~{fragmentos :: head}` y `~{fragmentos :: cabecera}` como las demás pantallas. Tabla de dos columnas (medida, tara kg) con un input por fila que hace `POST /taras` con la medida ya rellena, más una fila final vacía para dar de alta un tamaño nuevo. Nota de ayuda: *"La tara es el peso del cartón vacío. Un tamaño sin pesar se deja en 0,01 y el programa funciona igual: las cajas de ese tamaño saldrán con el peso pendiente."*

En `menu.html`, una tarjeta más que enlaza a `/taras`.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=TarasControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/web/TarasController.java src/main/resources/templates/taras.html src/main/resources/templates/menu.html src/test/java/com/puntotres/packinglist/web/TarasControllerTest.java
git commit -m "pantalla para pesar cartones sin tocar el yml"
```

---

### Task 4: Memoria de referencias

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/persistence/MemoriaCajaReferencia.java`
- Create: `src/main/java/com/puntotres/packinglist/persistence/MemoriaCajaReferenciaId.java`
- Create: `src/main/java/com/puntotres/packinglist/persistence/MemoriaCajaReferenciaRepository.java`
- Create: `src/main/java/com/puntotres/packinglist/persistence/MemoriaReferencias.java`
- Test: `src/test/java/com/puntotres/packinglist/persistence/MemoriaReferenciasTest.java`

**Interfaces:**
- Produces: `MemoriaReferencias` (`@Service`) con
  `Optional<DatosCaja> buscar(String cliente, String referencia)` y
  `void recordar(String cliente, String referencia, String medidaCaja, Integer unidadesPorCaja)`;
  `record DatosCaja(String medidaCaja, int unidadesPorCaja)`.

- [ ] **Step 1: Escribir el test que falla**

```java
@SpringBootTest
@ActiveProfiles("test")
class MemoriaReferenciasTest {

    @Autowired private MemoriaReferencias memoria;

    @Test
    void loRecordadoSeEncuentraPorClienteYReferencia() {
        memoria.recordar("AMI", "ULL164.AL0052", "60x40x40", 8);

        MemoriaReferencias.DatosCaja datos = memoria.buscar("AMI", "ULL164.AL0052").orElseThrow();
        assertEquals("60x40x40", datos.medidaCaja());
        assertEquals(8, datos.unidadesPorCaja());
    }

    @Test
    void laUltimaEjecucionGana() {
        memoria.recordar("AMI", "ULL700.AL0052", "60x40x40", 6);
        memoria.recordar("AMI", "ULL700.AL0052", "60x40x30", 4);

        assertEquals(4, memoria.buscar("AMI", "ULL700.AL0052").orElseThrow().unidadesPorCaja());
    }

    @Test
    void elColorNoFormaParteDeLaClaveYElClienteSi() {
        memoria.recordar("AMI", "REPE", "60x40x40", 8);

        assertTrue(memoria.buscar("APC", "REPE").isEmpty());
    }

    @Test
    void noSeMemorizaUnValorSinRellenar() {
        memoria.recordar("AMI", "SIN-UDS", "60x40x40", null);

        assertTrue(memoria.buscar("AMI", "SIN-UDS").isEmpty(),
                "memorizar el valor por defecto lo daría por bueno la próxima vez");
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=MemoriaReferenciasTest`
Expected: FAIL, no existen las clases.

- [ ] **Step 3: Implementar**

Entidad con `@IdClass(MemoriaCajaReferenciaId.class)`, dos `@Id` (`cliente`, `referencia`), columnas `medida_caja`, `unidades_por_caja`, `fecha_actualizacion`. `MemoriaReferencias` normaliza cliente y referencia a mayúsculas con trim antes de tocar el repositorio, y `recordar` **ignora** las llamadas con `unidadesPorCaja` nulo o `<= 0`.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=MemoriaReferenciasTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/persistence/ src/test/java/com/puntotres/packinglist/persistence/MemoriaReferenciasTest.java
git commit -m "el programa recuerda que carton y cuantas unidades lleva cada referencia"
```

---

### Task 5: `MedidaCaja`

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/taller/MedidaCaja.java`
- Test: `src/test/java/com/puntotres/packinglist/service/taller/MedidaCajaTest.java`

**Interfaces:**
- Produces: `record MedidaCaja(int largo, int ancho, int alto)` con
  `static Optional<MedidaCaja> parse(String texto)`, `long volumen()`, `String normalizada()`.

- [ ] **Step 1: Escribir el test que falla**

```java
class MedidaCajaTest {

    @Test
    void laAlturaEsLaUltimaDimension() {
        MedidaCaja medida = MedidaCaja.parse("60x40x45").orElseThrow();

        assertEquals(60, medida.largo());
        assertEquals(40, medida.ancho());
        assertEquals(45, medida.alto());
    }

    @Test
    void seNormalizaComoLasClavesDeLaTablaDeTaras() {
        assertEquals("60x40x45", MedidaCaja.parse(" 60 X 40 x 45 ").orElseThrow().normalizada());
    }

    @Test
    void unaMedidaIlegibleNoSeAdivina() {
        assertTrue(MedidaCaja.parse("60x40").isEmpty());
        assertTrue(MedidaCaja.parse("grande").isEmpty());
        assertTrue(MedidaCaja.parse(null).isEmpty());
    }

    @Test
    void ordenaPorVolumenYNoAlfabeticamente() {
        assertTrue(MedidaCaja.parse("100x40x40").orElseThrow().volumen()
                > MedidaCaja.parse("40x30x20").orElseThrow().volumen());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=MedidaCajaTest`
Expected: FAIL, no existe la clase.

- [ ] **Step 3: Implementar**

`parse` quita espacios, pasa a minúsculas, parte por `x`, exige tres enteros positivos y devuelve `Optional.empty()` en cualquier otro caso. `normalizada()` devuelve `largo + "x" + ancho + "x" + alto`.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=MedidaCajaTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/taller/MedidaCaja.java src/test/java/com/puntotres/packinglist/service/taller/MedidaCajaTest.java
git commit -m "la medida de caja como dato, con la altura en su sitio"
```

---

### Task 6: Lector del Excel de taller

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/taller/TallerColisExcel.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/LineaTaller.java`
- Create: `src/test/resources/ejemplos/taller/Packing List Taller Exemple.xlsx` (copia del de `docs/`)
- Test: `src/test/java/com/puntotres/packinglist/service/taller/TallerColisExcelTest.java`

**Interfaces:**
- Produces:
  `record LineaTaller(int fila, String cliente, String motivo, String referencia, String color, String talla, String destinoTaller, String code, String numeroExpedicion, Integer unidadesPorCajaTaller, int cantidad)`;
  `class TallerColisExcel` con
  `static TallerColisExcel desdeBytes(byte[] contenido) throws IOException, HojaNoEncontradaException, ColumnasAusentesException`,
  `static TallerColisExcel desdeBytes(byte[] contenido, String nombreHoja)`,
  `List<LineaTaller> lineas()`, `List<String> avisos()`;
  excepciones con `List<String> hojasEncontradas()` y `List<String> columnasAusentes()` / `List<String> cabecerasLeidas()`.

- [ ] **Step 1: Copiar el fichero real a los recursos de test**

```bash
mkdir -p src/test/resources/ejemplos/taller
cp "docs/Packing Lists/Packing List Taller/Packing List Taller Exemple.xlsx" src/test/resources/ejemplos/taller/
```

- [ ] **Step 2: Escribir el test que falla**

```java
class TallerColisExcelTest {

    private static TallerColisExcel real() throws IOException {
        try (InputStream in = TallerColisExcelTest.class
                .getResourceAsStream("/ejemplos/taller/Packing List Taller Exemple.xlsx")) {
            return TallerColisExcel.desdeBytes(in.readAllBytes());
        }
    }

    @Test
    void encuentraLaHojaYLaFilaDeCabeceraDelFicheroReal() throws IOException {
        // La cabecera está en la fila 9, no en la 1: el fichero lleva encima
        // el membrete del taller y una leyenda de campos.
        List<LineaTaller> lineas = real().lineas();

        assertEquals(15, lineas.size());
        assertEquals("AMI", lineas.get(0).cliente());
        assertEquals("ULL164.AL0052", lineas.get(0).referencia());
        assertEquals("KAKI", lineas.get(0).color());
        assertEquals("CH", lineas.get(0).destinoTaller());
        assertEquals(64, lineas.get(0).cantidad());
    }

    @Test
    void laUltimaFilaEsLaDeOtroCliente() throws IOException {
        // Fila 24 del fichero: CLIENT = MOD.TEST. El lector no la filtra;
        // quien decide qué hacer con ella es la digestión.
        List<LineaTaller> lineas = real().lineas();

        assertEquals("MOD.TEST", lineas.get(lineas.size() - 1).cliente());
    }

    @Test
    void losTextosLleganEnMayusculasYSinEspacios() throws IOException {
        assertTrue(real().lineas().stream()
                .allMatch(l -> l.referencia().equals(l.referencia().trim().toUpperCase())));
    }

    @Test
    void laColumnaQueNoEstaSaleComoAvisoYNoComoError() throws IOException {
        // El template no trae QTITE / COLIS: se avisa y las unidades por
        // caja quedarán a null, que es lo que bloquea después.
        assertTrue(real().avisos().stream().anyMatch(a -> a.contains("QTITE")));
        assertTrue(real().lineas().stream().allMatch(l -> l.unidadesPorCajaTaller() == null));
    }

    @Test
    void sinLaHojaDeColisSeDiceQueHojasHay() {
        byte[] libro = libroConHojas("FACTURE", "OTRA");

        HojaNoEncontradaException error = assertThrows(HojaNoEncontradaException.class,
                () -> TallerColisExcel.desdeBytes(libro));

        assertEquals(List.of("FACTURE", "OTRA"), error.hojasEncontradas());
    }

    @Test
    void sinUnaColumnaObligatoriaSeDiceCualFaltaYQueSeHaLeido() {
        byte[] libro = libroConCabecera("CLIENT", "MOTIF", "REFERENCE", "COULEUR");

        ColumnasAusentesException error = assertThrows(ColumnasAusentesException.class,
                () -> TallerColisExcel.desdeBytes(libro));

        assertEquals(List.of("QUANTITE"), error.columnasAusentes());
        assertTrue(error.cabecerasLeidas().contains("CLIENT"));
    }

    @Test
    void losSinonimosDeCabeceraCaenEnLaMismaColumna() {
        byte[] libro = libroConCabeceraYFila(
                List.of("CLIENTE", "MOTIF", "RÉFÉRENCE", "COLORIS", "QTÉ/COLIS", "QUANTITÉ"),
                List.of("AMI", "PROD", "ULL1", "KAKI", "8", "64"));

        LineaTaller linea = TallerColisExcel.desdeBytes(libro).lineas().get(0);

        assertEquals("ULL1", linea.referencia());
        assertEquals(8, linea.unidadesPorCajaTaller());
        assertEquals(64, linea.cantidad());
    }

    @Test
    void laLecturaParaTrasCincoFilasVacias() {
        byte[] libro = libroConFilasYHueco(3, 5, 2);   // 3 filas, 5 vacías, 2 más

        assertEquals(3, TallerColisExcel.desdeBytes(libro).lineas().size());
    }

    @Test
    void laCabeceraPuedeIrEnCeldasCombinadas() {
        byte[] libro = libroConCabeceraCombinada();

        assertEquals(1, TallerColisExcel.desdeBytes(libro).lineas().size());
    }
}
```

Los helpers `libroConHojas`, `libroConCabecera`, `libroConCabeceraYFila`, `libroConFilasYHueco` y `libroConCabeceraCombinada` construyen `.xlsx` con POI en memoria (`XSSFWorkbook` → `ByteArrayOutputStream`). Van en el mismo fichero de test, como métodos estáticos privados.

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `mvn test -Dtest=TallerColisExcelTest`
Expected: FAIL, no existe `TallerColisExcel`.

- [ ] **Step 4: Implementar el lector**

Puntos que la implementación tiene que respetar:

```java
    /** Normalización única para hojas y cabeceras: sin acentos, sin adornos. */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinAcentos.toUpperCase(Locale.ROOT)
                .replaceAll("[.:°º]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
```

- Hoja: la primera cuyo nombre normalizado sea `LISTE DE COLIS`; si no, `HojaNoEncontradaException` con los nombres tal cual.
- Cabecera: se recorren las filas 0..19 y se elige la **primera** que contenga todas las obligatorias (`CLIENT`, `MOTIF`, `REFERENCE`, `COULEUR`, `QUANTITE`) tras aplicar el mapa de sinónimos. Si ninguna, `ColumnasAusentesException` con las ausentes de la fila que más columnas conocidas tuviera, y las cabeceras leídas de esa fila.
- Celdas combinadas: al leer una celda de cabecera vacía se busca su región combinada (`hoja.getMergedRegions()`) y se toma el valor de la celda ancla.
- Datos: desde la fila siguiente hasta la última, cortando en cuanto haya 5 filas seguidas totalmente vacías. Una fila sin `REFERENCE` **y** sin `QUANTITE` cuenta como vacía.
- Textos en mayúsculas y con trim; numéricos sin transformar. `TAILLE` vacía o no numérica → `"U"`.
- `avisos()` recoge una línea por columna **opcional** no encontrada, con el nombre que se buscaba.
- Las celdas se leen con el mismo cuidado que `EscandalloReader`: los `.xlsx` generados por conversores traen `inlineStr` vacíos donde POI devuelve `null`.

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=TallerColisExcelTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/taller/ src/test/resources/ejemplos/taller/ src/test/java/com/puntotres/packinglist/service/taller/TallerColisExcelTest.java
git commit -m "leer la hoja LISTE DE COLIS del taller sin fiarse de las coordenadas"
```

---

### Task 7: Reglas de taller en configuración

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/config/ReglasTallerProperties.java`
- Create: `src/main/java/com/puntotres/packinglist/config/ReglaDestinoTaller.java`
- Create: `src/main/java/com/puntotres/packinglist/config/ReglaClienteTaller.java`
- Create: `src/main/java/com/puntotres/packinglist/config/TipoMezcla.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/puntotres/packinglist/PackingListApplication.java` (registrar el nuevo `@ConfigurationProperties`)
- Test: `src/test/java/com/puntotres/packinglist/config/ReglasTallerPropertiesTest.java`

**Interfaces:**
- Produces: `ReglasTallerProperties` con
  `int getAlturaPaletPorDefectoCm()`, `int getAlturaPropiaPaletCm()`, `int getPosicionesPalet()`, `String getMedidaCajaPorDefecto()`,
  `Optional<ReglaClienteTaller> clienteTaller(String clave)`,
  `int alturaUtilCm(String clienteClave, String destinoHija, String destinoPadre, Integer alturaTecleadaCm)`,
  `OptionalInt prioridadDe(String clienteClave, String destinoHija)`,
  `TipoMezcla mezclaDe(String clienteClave, String destinoPadre)`;
  `enum TipoMezcla { NINGUNA, MISMO_PEDIDO, LIBRE }`.

- [ ] **Step 1: Escribir la configuración**

En `application.yml`, bajo `packing-list:` (mismo prefijo que `taras` y `clientes`):

```yaml
  # Reglas de la entrada por packing list de taller. Añadir una destinación
  # es una línea aquí; añadir un cliente GENERIC no exige nada (destino
  # único, mezcla libre y la altura que teclee el usuario).
  #
  # prioridad: número menor = se sirve antes cuando falta género. Empate =
  #   mismo escalón, y entonces el reparto es proporcional.
  # mezcla: NINGUNA (una referencia y un color por caja), MISMO_PEDIDO
  #   (solo entre artículos del mismo pedido) o LIBRE.
  # altura-max-cm de una destinación hija cae en la de su padre si no la
  #   declara: CHINE FRANCH viaja a Crosslog como WHOLESALE, con sus 168.
  taller:
    altura-palet-por-defecto-cm: 168
    altura-propia-palet-cm: 11
    posiciones-palet: 4
    medida-caja-por-defecto: 60x40x40
    clientes:
      "[AMI]":
        numeracion-cajas: CONTINUA
        # El PO del pedido de AMI lleva la destinación pegada como sufijo
        # ("07704 CH"). Un sufijo que no esté aquí es aviso bloqueante.
        sufijos-po:
          "[CH]": CHINA
          "[JP]": JAPAN
        destino-sin-sufijo: PARIS
        destinos:
          "[CHINA]": { altura-max-cm: 158, prioridad: 1, mezcla: NINGUNA }
          "[JAPAN]": { altura-max-cm: 158, prioridad: 1, mezcla: NINGUNA }
          "[PARIS]": { altura-max-cm: 168, prioridad: 2, mezcla: MISMO_PEDIDO }
      "[APC]":
        numeracion-cajas: POR_DESTINACION
        destinos:
          "[CHINE FRANCH]": { prioridad: 1, mezcla: LIBRE }
          "[JAPAN]":        { altura-max-cm: 155, prioridad: 1, mezcla: LIBRE }
          "[KOREA]":        { altura-max-cm: 155, prioridad: 1, mezcla: LIBRE }
          "[D. USA]":       { altura-max-cm: 155, prioridad: 2, mezcla: LIBRE }
          "[RETAIL]":       { altura-max-cm: 168, prioridad: 3, mezcla: LIBRE }
          "[WHOLESALE]":    { altura-max-cm: 168, prioridad: 4, mezcla: LIBRE }
```

(`CHINE FRANCH` deliberadamente **sin** `altura-max-cm`: hereda los 168 de su padre `WHOLESALE`, y así el yml documenta la herencia en vez de repetir el número.)

- [ ] **Step 2: Escribir el test que falla**

```java
@SpringBootTest
@ActiveProfiles("test")
class ReglasTallerPropertiesTest {

    @Autowired private ReglasTallerProperties reglas;

    @Test
    void elYmlSeEnlaza() {
        assertEquals(11, reglas.getAlturaPropiaPaletCm());
        assertEquals(4, reglas.getPosicionesPalet());
        assertTrue(reglas.clienteTaller("AMI").isPresent());
    }

    @Test
    void laHijaHeredaLaAlturaDeSuPadre() {
        // CHINE FRANCH tiene prioridad máxima pero viaja al almacén de
        // WHOLESALE, así que se apila a la altura de WHOLESALE.
        int hija = reglas.alturaUtilCm("APC", "CHINE FRANCH", "WHOLESALE", null);
        int padre = reglas.alturaUtilCm("APC", "WHOLESALE", "WHOLESALE", null);

        assertEquals(padre, hija);
    }

    @Test
    void laAlturaUtilDescuentaLoQueLevantaElPalet() {
        assertEquals(158 - 11, reglas.alturaUtilCm("AMI", "CHINA", "CHINA", null));
    }

    @Test
    void unClienteSinReglasUsaLaAlturaQueTecleaElUsuario() {
        assertEquals(200 - 11, reglas.alturaUtilCm("PALOMA WOOL", "PALOMA WOOL", "PALOMA WOOL", 200));
    }

    @Test
    void unClienteSinReglasYSinAlturaTecleadaCaeEnLaPorDefecto() {
        assertEquals(168 - 11, reglas.alturaUtilCm("PALOMA WOOL", "PALOMA WOOL", "PALOMA WOOL", null));
    }

    @Test
    void laPrioridadSeEvaluaSobreLaHijaYNoSobreElPadre() {
        // CHINE FRANCH cuelga de WHOLESALE, que es la última en prioridad,
        // pero ella es de las primeras.
        assertTrue(reglas.prioridadDe("APC", "CHINE FRANCH").getAsInt()
                < reglas.prioridadDe("APC", "WHOLESALE").getAsInt());
    }

    @Test
    void unaDestinacionDesconocidaNoTienePrioridad() {
        assertTrue(reglas.prioridadDe("APC", "MARTE").isEmpty());
    }

    @Test
    void elSufijoDelPoDaLaDestinacionYSinSufijoEsParis() {
        ReglaClienteTaller ami = reglas.clienteTaller("AMI").orElseThrow();

        assertEquals("CHINA", ami.destinoDeSufijo("CH").orElseThrow());
        assertEquals("JAPAN", ami.destinoDeSufijo("JP").orElseThrow());
        assertEquals("PARIS", ami.destinoDeSufijo(null).orElseThrow());
        assertTrue(ami.destinoDeSufijo("XX").isEmpty());
    }
}
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `mvn test -Dtest=ReglasTallerPropertiesTest`
Expected: FAIL, no existen las clases de configuración.

- [ ] **Step 4: Implementar**

Clases POJO con getters/setters (patrón de `ClientesProperties`), `@ConfigurationProperties(prefix = "packing-list")` con campo `taller`, registradas en `PackingListApplication` junto a las que ya hay. Las claves de destinación se normalizan a mayúsculas con trim al enlazar, igual que hace `TaraProperties` con las medidas. `mezclaDe` devuelve `LIBRE` cuando no hay regla.

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=ReglasTallerPropertiesTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/application.yml src/main/java/com/puntotres/packinglist/config/ src/test/java/com/puntotres/packinglist/config/ReglasTallerPropertiesTest.java
git commit -m "alturas, prioridades y reglas de mezcla del taller como configuracion"
```

---

### Task 8: Objetivos de pedido de AMI

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/ObjetivosPedido.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/ObjetivoDestino.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/ResultadoObjetivos.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/ObjetivosPedidoAmi.java`
- Test: `src/test/java/com/puntotres/packinglist/service/taller/ObjetivosPedidoAmiTest.java`

**Interfaces:**
- Consumes: `LineaTaller` (Task 6), `ReglaClienteTaller.destinoDeSufijo` (Task 7).
- Produces:
  `record ObjetivoDestino(String destino, int cantidad, String pedido)`;
  `class ResultadoObjetivos` con `List<ObjetivoDestino> objetivosDe(LineaTaller linea)`, `List<String> getAvisos()`, `List<String> getBloqueos()`;
  `interface ObjetivosPedido { String clienteSoportado(); ResultadoObjetivos objetivosPara(List<LineaTaller> lineas, byte[] excelPedido); }`;
  `AmiPedidoExcel.cantidadPedida(String referencia, String color, String talla, String sufijoPo)` → `OptionalInt`,
  `AmiPedidoExcel.sufijosPoDe(String referencia, String color, String talla)` → `List<String>` (el sufijo vacío se representa como `null`),
  `AmiPedidoExcel.poDe(String referencia, String color, String talla, String sufijoPo)` → `Optional<String>`.

- [ ] **Step 1: Escribir el test que falla**

```java
class ObjetivosPedidoAmiTest {

    private static byte[] pedidoReal() throws IOException {
        try (InputStream in = ObjetivosPedidoAmiTest.class
                .getResourceAsStream("/ejemplos/EAN PUNTOTRES H26.xlsx")) {
            return in.readAllBytes();
        }
    }

    @Test
    void laCantidadObjetivoSaleDeLaColumnaCommande() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoReal());

        // Cualquier fila del fichero real tiene Commandé; se comprueba que
        // se lee, no cuánto vale (eso es dato de cliente y cambia cada temporada).
        assertTrue(pedido.cantidadPedida("ULL754.AL0218", "A328", "U", "JP").isPresent()
                || pedido.cantidadPedida("ULL754.AL0218", "A328", "U", "CH").isPresent());
    }

    @Test
    void elSufijoDelPoSeparaLasDestinaciones() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoReal());

        // En el fichero real solo hay POs de China y Japón.
        assertEquals(Set.of("CH", "JP"), new HashSet<>(pedido.sufijosPo()));
    }

    @Test
    void unaLineaDeTallerSeConvierteEnObjetivosPorDestinacion() throws IOException {
        LineaTaller linea = lineaDeTaller("ULL754.AL0218", "A328", "U");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedidoReal());

        List<String> destinos = resultado.objetivosDe(linea).stream()
                .map(ObjetivoDestino::destino).toList();
        assertTrue(destinos.contains("CHINA") || destinos.contains("JAPAN"));
        assertTrue(resultado.getBloqueos().isEmpty());
    }

    @Test
    void unaReferenciaQueNoEstaEnElPedidoAvisaYNoRompe() throws IOException {
        LineaTaller linea = lineaDeTaller("NO-EXISTE", "0000", "U");

        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(linea), pedidoReal());

        assertTrue(resultado.objetivosDe(linea).isEmpty());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("NO-EXISTE")));
        assertTrue(resultado.getBloqueos().isEmpty(), "una referencia sin pedido se arregla a mano");
    }

    @Test
    void unSufijoDePoDesconocidoEsBloqueante() {
        // Pedido sintético con un PO "07777 XX": el mapa del yml no lo tiene,
        // así que no se puede saber a qué destinación va.
        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(lineaDeTaller("REF1", "0001", "U")), pedidoConSufijo("XX"));

        assertTrue(resultado.getBloqueos().stream().anyMatch(b -> b.contains("XX")));
    }

    @Test
    void variasTallasDeLaMismaDestinacionSeSuman() {
        // Cinturón con tallas 75 y 85 en el mismo PO: la línea de taller de
        // talla 75 solo se lleva lo suyo, no la suma de las dos.
        ResultadoObjetivos resultado = new ObjetivosPedidoAmi(reglasDeAmi())
                .objetivosPara(List.of(lineaDeTaller("UBL1", "2221", "75")),
                        pedidoConTallas("UBL1", "2221", Map.of("75", 10, "85", 4), "CH"));

        assertEquals(10, resultado.objetivosDe(lineaDeTaller("UBL1", "2221", "75")).get(0).cantidad());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=ObjetivosPedidoAmiTest`
Expected: FAIL, no existen los métodos nuevos de `AmiPedidoExcel` ni las clases.

- [ ] **Step 3: Ampliar `AmiPedidoExcel`**

En `desdeBytes`, añadir `int colCommande = hoja.columnaOpcional("COMMAND")` (el prefijo cubre `Commandé` con y sin acento tras la normalización de `HojaEan`), un aviso si falta, y un campo más en `FilaCruda`. Añadir los tres métodos de consulta declarados arriba más `List<String> sufijosPo()`. **No se toca** nada de lo que ya usa `AmiEtiquetasGenerador`.

- [ ] **Step 4: Implementar `ObjetivosPedidoAmi`**

Para cada línea de taller: busca en el pedido todas las filas con esa referencia, color y talla; de cada una saca su sufijo de PO; traduce el sufijo a destinación con `ReglaClienteTaller.destinoDeSufijo`; suma `Commandé` por destinación; y produce un `ObjetivoDestino` por destinación con el PO de 5 dígitos. Sufijo sin traducción → bloqueo. Referencia sin filas → aviso.

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=ObjetivosPedidoAmiTest`
Expected: PASS

- [ ] **Step 6: Los tests de etiquetas siguen verdes**

Run: `mvn test -Dtest=AmiPedidoRealTest,AmiEtiquetasGeneradorTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java src/main/java/com/puntotres/packinglist/service/taller/ src/test/java/com/puntotres/packinglist/service/taller/ObjetivosPedidoAmiTest.java
git commit -m "cuanto pide AMI de cada cosa y para donde, del mismo excel de siempre"
```

---

### Task 9: Objetivos de pedido de APC

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/ApcPedidoExcel.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/ObjetivosPedidoApc.java`
- Test: `src/test/java/com/puntotres/packinglist/service/taller/ObjetivosPedidoApcTest.java`

**Interfaces:**
- Produces: `ApcPedidoExcel.FilaPedido` gana `String destino` y `int cantidad`
  → `record FilaPedido(String referencia, String pedido, String destino, int cantidad)`;
  `ObjetivosPedidoApc` implementa `ObjetivosPedido`.

- [ ] **Step 1: Escribir el test que falla**

```java
class ObjetivosPedidoApcTest {

    private static byte[] pedidoReal() throws IOException {
        try (InputStream in = ObjetivosPedidoApcTest.class
                .getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            return in.readAllBytes();
        }
    }

    @Test
    void elCodeDeTresDigitosIdentificaPedidoYDestinacion() throws IOException {
        // 4100128863 -> Chine franch, según el fichero real.
        LineaTaller linea = lineaApc("PXCBC-F67008", "863");

        ResultadoObjetivos resultado = new ObjetivosPedidoApc().objetivosPara(List.of(linea), pedidoReal());

        List<ObjetivoDestino> objetivos = resultado.objetivosDe(linea);
        assertEquals(1, objetivos.size(), "un Document d'achat es una sola destinación");
        assertEquals("CHINE FRANCH", objetivos.get(0).destino());
        assertEquals("4100128863", objetivos.get(0).pedido());
    }

    @Test
    void laReferenciaPuedeVenirComoSufijo() throws IOException {
        // El taller escribe "F67008"; el Article real es "PXCBC-F67008".
        LineaTaller linea = lineaApc("F67008", "863");

        assertEquals("CHINE FRANCH",
                new ObjetivosPedidoApc().objetivosPara(List.of(linea), pedidoReal())
                        .objetivosDe(linea).get(0).destino());
    }

    @Test
    void lasFilasDeTallaDelMismoPedidoSeSuman() throws IOException {
        // 4100128721 (Australia) tiene varias filas, una por talla.
        LineaTaller linea = lineaApc("PXBHZ-H65077", "721");

        int cantidad = new ObjetivosPedidoApc().objetivosPara(List.of(linea), pedidoReal())
                .objetivosDe(linea).get(0).cantidad();

        assertTrue(cantidad > 3, "no puede ser el de una sola talla");
    }

    @Test
    void unaLineaSinCodeEsBloqueante() throws IOException {
        LineaTaller linea = lineaApc("PXCBC-F67008", null);

        ResultadoObjetivos resultado = new ObjetivosPedidoApc().objetivosPara(List.of(linea), pedidoReal());

        assertTrue(resultado.getBloqueos().stream().anyMatch(b -> b.contains("CODE")));
    }

    @Test
    void unCodeQueNoEstaEnElPedidoAvisaYNoRompe() throws IOException {
        LineaTaller linea = lineaApc("PXCBC-F67008", "000");

        ResultadoObjetivos resultado = new ObjetivosPedidoApc().objetivosPara(List.of(linea), pedidoReal());

        assertTrue(resultado.objetivosDe(linea).isEmpty());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("000")));
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=ObjetivosPedidoApcTest`
Expected: FAIL

- [ ] **Step 3: Ampliar `ApcPedidoExcel`**

`FilaPedido` gana `destino` (de `Notre référence`, en mayúsculas y con espacios colapsados, para que `CHINE FRANCH` case con la clave del yml) y `cantidad` (de `Quantité échéancée`). La columna de cantidad se busca por prefijo `QUANTIT` con `columnaOpcional`; si falta, aviso de libro y cantidad `0`. `filasPara(referencia, pedidoParcial)` no cambia de firma.

Los tests actuales de `ApcPedidoExcelTest` que construyen `FilaPedido` se actualizan al nuevo record.

- [ ] **Step 4: Implementar `ObjetivosPedidoApc`**

`clienteSoportado()` devuelve `"APC"`. Para cada línea: `code` en blanco → bloqueo; si no, `filasPara(referencia, code)`; sin filas → aviso; con filas → un `ObjetivoDestino` con el `destino` de la primera, la suma de `cantidad` y el `pedido` completo. Si las filas discrepan de destinación entre sí (no debería pasar), aviso y se toma la primera.

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=ObjetivosPedidoApcTest,ApcPedidoExcelTest,PedidoCompletionServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/ApcPedidoExcel.java src/main/java/com/puntotres/packinglist/service/taller/ObjetivosPedidoApc.java src/test/java/com/puntotres/packinglist/service/
git commit -m "en APC el codigo de tres digitos ya dice la destinacion y la cantidad"
```

---

### Task 10: Reparto por destinación

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/taller/RepartoDestinaciones.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/ArticuloDestinado.java`
- Test: `src/test/java/com/puntotres/packinglist/service/taller/RepartoDestinacionesTest.java`

**Interfaces:**
- Produces:
  `record ArticuloDestinado(String destino, String referencia, String color, String talla, String pedido, int cantidad, String medidaCaja, int unidadesPorCaja)`;
  `RepartoDestinaciones.repartir(String clienteClave, List<FilaAjustada> filas)` → `ResultadoReparto` con `List<ArticuloDestinado> getArticulos()`, `List<String> getAvisos()`, `List<String> getBloqueos()`;
  `record FilaAjustada(String referencia, String color, String talla, int recibido, String medidaCaja, Integer unidadesPorCaja, List<ObjetivoDestino> objetivos)`.

- [ ] **Step 1: Escribir el test que falla**

```java
class RepartoDestinacionesTest {

    @Test
    void loQueSobraDeUnaDestinacionPrioritariaNoSeLeQuita() {
        // 31 recibidas, CHINA pide 20 y PARIS 20: CHINA completa, PARIS corta.
        FilaAjustada fila = fila("BAG-A", "NOIR", 31,
                objetivo("CHINA", 20), objetivo("PARIS", 20));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertEquals(20, cantidadDe(reparto, "CHINA", "BAG-A", "NOIR"));
        assertEquals(11, cantidadDe(reparto, "PARIS", "BAG-A", "NOIR"));
        assertTrue(reparto.getAvisos().stream().anyMatch(a -> a.contains("PARIS")));
    }

    @Test
    void siLlegaDeSobraSeSirveElObjetivoYSeAvisaDelSobrante() {
        FilaAjustada fila = fila("BAG-A", "NOIR", 50, objetivo("CHINA", 20), objetivo("PARIS", 20));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertEquals(20, cantidadDe(reparto, "CHINA", "BAG-A", "NOIR"));
        assertEquals(20, cantidadDe(reparto, "PARIS", "BAG-A", "NOIR"));
        assertTrue(reparto.getAvisos().stream().anyMatch(a -> a.contains("sobran 10")));
    }

    @Test
    void conPrioridadEmpatadaElRepartoEsProporcional() {
        // CHINA y JAPAN empatan en prioridad 1; llegan 15 para 20+10.
        FilaAjustada fila = fila("BAG-A", "NOIR", 15, objetivo("CHINA", 20), objetivo("JAPAN", 10));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertEquals(10, cantidadDe(reparto, "CHINA", "BAG-A", "NOIR"));
        assertEquals(5, cantidadDe(reparto, "JAPAN", "BAG-A", "NOIR"));
    }

    @Test
    void elRestoEnteroVaALaDestinacionDeMayorObjetivo() {
        FilaAjustada fila = fila("BAG-A", "NOIR", 5, objetivo("CHINA", 20), objetivo("JAPAN", 10));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertEquals(4, cantidadDe(reparto, "CHINA", "BAG-A", "NOIR"));
        assertEquals(1, cantidadDe(reparto, "JAPAN", "BAG-A", "NOIR"));
    }

    @Test
    void unaDestinacionQueNoEstaEnLasReglasBloquea() {
        FilaAjustada fila = fila("BAG-A", "NOIR", 10, objetivo("MARTE", 10));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertTrue(reparto.getBloqueos().stream().anyMatch(b -> b.contains("MARTE")));
    }

    @Test
    void unaFilaSinUnidadesPorCajaBloquea() {
        FilaAjustada fila = filaSinUnidadesPorCaja("BAG-A", "NOIR", 10, objetivo("CHINA", 10));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertTrue(reparto.getBloqueos().stream().anyMatch(b -> b.contains("BAG-A")));
    }

    @Test
    void unaDestinacionConObjetivoCeroNoGeneraArticulo() {
        FilaAjustada fila = fila("BAG-A", "NOIR", 10, objetivo("CHINA", 10), objetivo("PARIS", 0));

        ResultadoReparto reparto = new RepartoDestinaciones(reglas()).repartir("AMI", List.of(fila));

        assertTrue(reparto.getArticulos().stream().noneMatch(a -> a.destino().equals("PARIS")));
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=RepartoDestinacionesTest`
Expected: FAIL

- [ ] **Step 3: Implementar**

Por fila: se ordenan sus objetivos por prioridad (menor primero). Se recorre escalón a escalón con el stock restante:

```java
// Dentro de un escalón de prioridad, si el stock no llega para todos, se
// reparte proporcionalmente al objetivo y el resto entero va al objetivo
// mayor: dejar a uno a cero mientras otro va completo sería peor.
int pedidoDelEscalon = escalon.stream().mapToInt(ObjetivoDestino::cantidad).sum();
if (restante >= pedidoDelEscalon) {
    escalon.forEach(o -> asignar(o, o.cantidad()));
    restante -= pedidoDelEscalon;
} else {
    repartirProporcional(escalon, restante);
    restante = 0;
}
```

`repartirProporcional` calcula `objetivo * disponible / pedidoDelEscalon` por división entera y adjudica el resto a la destinación de mayor objetivo (empate: la primera por nombre, para que el resultado sea reproducible).

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=RepartoDestinacionesTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/taller/RepartoDestinaciones.java src/main/java/com/puntotres/packinglist/service/taller/ArticuloDestinado.java src/test/java/com/puntotres/packinglist/service/taller/RepartoDestinacionesTest.java
git commit -m "cuando no llega el genero, se sirve primero lo que mas urge"
```

---

### Task 11: Agrupación en cajas

El corazón del algoritmo. Aquí viven D9 (mezclar exige el mismo cartón) y D10 (se mezcla solo si ahorra una caja).

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/taller/AgrupadorCajas.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/CajaGenerada.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/ContenidoCaja.java`
- Test: `src/test/java/com/puntotres/packinglist/service/taller/AgrupadorCajasTest.java`

**Interfaces:**
- Consumes: `ArticuloDestinado` (Task 10), `TipoMezcla` (Task 7), `MedidaCaja` (Task 5).
- Produces:
  `record ContenidoCaja(String referencia, String color, String talla, String pedido, int unidades)`;
  `record CajaGenerada(String destino, String medidaCaja, List<ContenidoCaja> contenido)` con `int unidades()` y `int alturaCm()`;
  `AgrupadorCajas.agrupar(List<ArticuloDestinado> articulos, TipoMezcla mezcla)` → `List<CajaGenerada>`.

- [ ] **Step 1: Escribir el test que falla**

```java
class AgrupadorCajasTest {

    @Test
    void sinMezclaCadaArticuloVaEnSusPropiasCajas() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("CHINA", "BAG-A", "NOIR", 20, "60x40x40", 10),
                        articulo("CHINA", "BAG-A", "BEIGE", 10, "60x40x40", 10)),
                TipoMezcla.NINGUNA);

        assertEquals(3, cajas.size());
        assertTrue(cajas.stream().allMatch(c -> c.contenido().size() == 1));
    }

    @Test
    void elRepartoEsEquitativoYNoDejaUnaCajaCasiVacia() {
        // 31 unidades con 10 por caja: 8+8+8+7, nunca 10+10+10+1.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("CHINA", "BAG-A", "NOIR", 31, "60x40x40", 10)),
                TipoMezcla.NINGUNA);

        assertEquals(List.of(8, 8, 8, 7), cajas.stream().map(CajaGenerada::unidades).toList());
    }

    @Test
    void ningunaCajaPasaDeSuCapacidad() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("CHINA", "BAG-A", "NOIR", 97, "60x40x40", 10)),
                TipoMezcla.NINGUNA);

        assertTrue(cajas.stream().allMatch(c -> c.unidades() <= 10));
        assertEquals(97, cajas.stream().mapToInt(CajaGenerada::unidades).sum());
    }

    @Test
    void dosReferenciasConCartonDistintoNoCompartenCajaAunqueSePuedaMezclar() {
        // D9: la caja ES un cartón concreto.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-A", "NOIR", 3, "60x40x40", 10),
                        articulo("PARIS", "BAG-B", "NOIR", 2, "60x40x30", 6)),
                TipoMezcla.LIBRE);

        assertEquals(2, cajas.size());
        assertTrue(cajas.stream().allMatch(c -> c.contenido().size() == 1));
    }

    @Test
    void noSeMezclaSiNoAhorraNingunaCaja() {
        // D10: puras 2+1 = 3 cajas; mezcladas ceil(21/10) = 3. Empate -> puras.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-A", "NOIR", 11, "60x40x40", 10),
                        articulo("PARIS", "BAG-A", "BEIGE", 10, "60x40x40", 10)),
                TipoMezcla.LIBRE);

        assertEquals(3, cajas.size());
        assertTrue(cajas.stream().allMatch(c -> c.contenido().size() == 1),
                "mezclar complica la etiqueta; solo se hace si ahorra un bulto");
        assertEquals(List.of(6, 5, 10), cajas.stream().map(CajaGenerada::unidades).toList());
    }

    @Test
    void seMezclaCuandoAhorraUnaCaja() {
        // Puras: ceil(4/10) + ceil(4/10) = 2. Mezcladas: ceil(8/10) = 1.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-A", "NOIR", 4, "60x40x40", 10),
                        articulo("PARIS", "BAG-A", "BEIGE", 4, "60x40x40", 10)),
                TipoMezcla.LIBRE);

        assertEquals(1, cajas.size());
        assertEquals(2, cajas.get(0).contenido().size());
        assertEquals(8, cajas.get(0).unidades());
    }

    @Test
    void seAgotanLosColoresDeUnaReferenciaAntesDeMeterOtra() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-B", "NOIR", 2, "60x40x40", 10),
                        articulo("PARIS", "BAG-A", "NOIR", 2, "60x40x40", 10),
                        articulo("PARIS", "BAG-A", "BEIGE", 2, "60x40x40", 10)),
                TipoMezcla.LIBRE);

        // Una sola caja, y dentro los dos colores de BAG-A seguidos.
        assertEquals(1, cajas.size());
        assertEquals(List.of("BAG-B", "BAG-A", "BAG-A"),
                cajas.get(0).contenido().stream().map(ContenidoCaja::referencia).toList());
    }

    @Test
    void conMezclaDeMismoPedidoNoSeJuntanPedidosDistintos() {
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articuloConPedido("PARIS", "BAG-A", "NOIR", 2, "07001"),
                        articuloConPedido("PARIS", "BAG-A", "BEIGE", 2, "07002")),
                TipoMezcla.MISMO_PEDIDO);

        assertEquals(2, cajas.size());
    }

    @Test
    void enUnaCajaMezcladaCadaUnidadOcupaSegunSuCapacidad() {
        // BAG-A: 10/caja. BAG-C: 5/caja. 5 de A (0,5) + 2 de C (0,4) = 0,9 -> una caja.
        List<CajaGenerada> cajas = new AgrupadorCajas().agrupar(
                List.of(articulo("PARIS", "BAG-A", "NOIR", 5, "60x40x40", 10),
                        articulo("PARIS", "BAG-C", "NOIR", 2, "60x40x40", 5)),
                TipoMezcla.LIBRE);

        assertEquals(1, cajas.size());
    }

    @Test
    void laAlturaDeLaCajaEsLaUltimaDimension() {
        CajaGenerada caja = new AgrupadorCajas().agrupar(
                List.of(articulo("CHINA", "BAG-A", "NOIR", 1, "60x40x45", 10)),
                TipoMezcla.NINGUNA).get(0);

        assertEquals(45, caja.alturaCm());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=AgrupadorCajasTest`
Expected: FAIL

- [ ] **Step 3: Implementar**

```java
/**
 * Agrupa los artículos de una destinación en cajas físicas.
 *
 * Dos reglas que no son obvias y que conviene no "simplificar":
 *
 * 1. Mezclar exige el MISMO cartón. La caja no es una capacidad abstracta,
 *    es un cartón de una medida concreta: dos referencias con medidas
 *    distintas no pueden compartir bulto por mucho que el cliente permita
 *    mezclar modelos.
 * 2. Se mezcla solo si ahorra una caja. Mezclar complica la etiqueta y el
 *    packing list, así que se comparan los dos recuentos y solo gana la
 *    mezcla cuando de verdad quita un bulto de encima. En caso de empate,
 *    cajas puras.
 *
 * En una caja mezclada cada unidad ocupa 1/unidadesPorCaja de la caja: es
 * la única forma de dar sentido a "10 por caja" cuando dentro hay dos
 * artículos que se cuentan distinto.
 */
```

- Orden estable de artículos: por orden de aparición de la referencia y, dentro, por orden de aparición del color. Así se agotan los colores de una referencia antes de pasar a otra.
- Claves de grupo: destino + medidaCaja + (pedido si `MISMO_PEDIDO`) + (referencia+color+talla si `NINGUNA`).
- Por grupo: `cajasPuras = Σ ceil(qᵢ/capᵢ)`, `cajasMezcladas = ceil(Σ qᵢ/capᵢ)`. Si `cajasMezcladas < cajasPuras`, se reparte el grupo entero en `cajasMezcladas` cajas; si no, se trata cada artículo por separado con su propio `ceil`.
- Reparto de un artículo suelto en `n` cajas: `base = q/n`, `resto = q%n`; las primeras `resto` cajas llevan `base+1`.
- Reparto de un grupo mezclado en `n` cajas: llenado objetivo `Σ(qᵢ/capᵢ)/n`; se recorren los artículos en orden metiendo unidades en la caja actual mientras su ocupación siga por debajo del objetivo y no pase de 1; al llegar, se abre la siguiente. Una pasada final mueve a la siguiente caja con hueco lo que se hubiera pasado de 1.
- `alturaCm()` sale de `MedidaCaja.parse(medidaCaja).alto()`; una medida ilegible a estas alturas es imposible (la valida el paso 1b), pero si llegara, `alturaCm()` devuelve `0` y el apilador lo convierte en bloqueo.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=AgrupadorCajasTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/taller/AgrupadorCajas.java src/main/java/com/puntotres/packinglist/service/taller/CajaGenerada.java src/main/java/com/puntotres/packinglist/service/taller/ContenidoCaja.java src/test/java/com/puntotres/packinglist/service/taller/AgrupadorCajasTest.java
git commit -m "llenar cajas por igual, y mezclar solo cuando ahorra un bulto"
```

---

### Task 12: Apilado en palets

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/taller/ApiladorPalets.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/PaletGenerado.java`
- Test: `src/test/java/com/puntotres/packinglist/service/taller/ApiladorPaletsTest.java`

**Interfaces:**
- Produces:
  `class PaletGenerado` con `List<List<CajaGenerada>> pilas()`, `List<CajaGenerada> cajas()` (aplanadas en orden de pila), `int alturaMaximaCm()`;
  `ApiladorPalets.apilar(List<CajaGenerada> cajas, int alturaUtilCm, int posiciones)` → `ResultadoApilado` con `List<PaletGenerado> getPalets()` y `List<String> getBloqueos()`.

- [ ] **Step 1: Escribir el test que falla**

```java
class ApiladorPaletsTest {

    @Test
    void cuatroCajasQueCabenVanEnUnPaletUnaPorPila() {
        List<CajaGenerada> cajas = List.of(caja(40), caja(40), caja(40), caja(30));

        ResultadoApilado apilado = new ApiladorPalets().apilar(cajas, 147, 4);

        assertEquals(1, apilado.getPalets().size());
        assertEquals(4, apilado.getPalets().get(0).pilas().size());
        assertEquals(40, apilado.getPalets().get(0).alturaMaximaCm());
    }

    @Test
    void seApilaEnLaPilaMasBajaQueTengaSitio() {
        List<CajaGenerada> cajas = List.of(caja(40), caja(40), caja(40), caja(40), caja(40));

        PaletGenerado palet = new ApiladorPalets().apilar(cajas, 147, 4).getPalets().get(0);

        assertEquals(5, palet.cajas().size());
        assertEquals(80, palet.alturaMaximaCm(), "la quinta se apila encima de la primera");
    }

    @Test
    void cuandoNoCabeEnNingunaPilaSeAbrePaletNuevo() {
        // Altura útil 100: caben 2 cajas de 40 por pila = 8 en total.
        List<CajaGenerada> cajas = new ArrayList<>(Collections.nCopies(9, caja(40)));

        ResultadoApilado apilado = new ApiladorPalets().apilar(cajas, 100, 4);

        assertEquals(2, apilado.getPalets().size());
        assertEquals(1, apilado.getPalets().get(1).cajas().size());
    }

    @Test
    void lasCajasSeColocanDeMayorAMenorAltura() {
        List<CajaGenerada> cajas = List.of(caja(20), caja(50), caja(30));

        PaletGenerado palet = new ApiladorPalets().apilar(cajas, 147, 4).getPalets().get(0);

        assertEquals(List.of(50, 30, 20),
                palet.cajas().stream().map(CajaGenerada::alturaCm).toList());
    }

    @Test
    void unaCajaMasAltaQueElPaletBloquea() {
        ResultadoApilado apilado = new ApiladorPalets().apilar(List.of(caja(200)), 147, 4);

        assertTrue(apilado.getBloqueos().stream().anyMatch(b -> b.contains("200")));
        assertTrue(apilado.getPalets().isEmpty());
    }

    @Test
    void seAceptaElUltimoPaletAMediaAltura() {
        ResultadoApilado apilado = new ApiladorPalets().apilar(List.of(caja(40)), 147, 4);

        assertEquals(1, apilado.getPalets().size());
        assertTrue(apilado.getBloqueos().isEmpty());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=ApiladorPaletsTest`
Expected: FAIL

- [ ] **Step 3: Implementar**

Voraz, tal cual el spec: ordenar de mayor a menor altura; para cada caja, la pila del palet **actual** con menos altura acumulada donde quepa; si en ninguna cabe, palet nuevo. Una caja con `alturaCm() > alturaUtilCm` (o `alturaCm() <= 0`) es bloqueo y no se coloca. Es una heurística y así se documenta: no busca el óptimo, y no hace falta.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=ApiladorPaletsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/taller/ApiladorPalets.java src/main/java/com/puntotres/packinglist/service/taller/PaletGenerado.java src/test/java/com/puntotres/packinglist/service/taller/ApiladorPaletsTest.java
git commit -m "apilar en cuatro pilas sin pasarse de la altura del destino"
```

---

### Task 13: Generador del `EnvioInput`

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/taller/GeneradorPackingTaller.java`
- Test: `src/test/java/com/puntotres/packinglist/service/taller/GeneradorPackingTallerTest.java`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: `GeneradorPackingTaller.generar(String clienteClave, List<FilaAjustada> filas, Integer alturaTecleadaCm)` → `ResultadoPackingTaller` con `EnvioInput getEnvio()`, `List<ResumenDestino> getResumen()`, `List<String> getAvisos()`, `List<String> getBloqueos()`;
  `record ResumenDestino(String destino, int unidades, int cajas, int palets, int alturaUltimoPaletCm, int alturaUtilCm)`.

- [ ] **Step 1: Escribir el test de aceptación que falla**

```java
class GeneradorPackingTallerTest {

    // El ejemplo trabajado del diseño, §10.
    private List<FilaAjustada> ejemplo() {
        return List.of(
            fila("BAG-A", "NOIR", "U", 31, "60x40x40", 10,
                 objetivo("CHINA", 20, "07001"), objetivo("PARIS", 20, "07001")),
            fila("BAG-A", "BEIGE", "U", 20, "60x40x40", 10,
                 objetivo("CHINA", 10, "07001"), objetivo("PARIS", 10, "07001")),
            fila("BAG-B", "NOIR", "U", 12, "60x40x30", 6,
                 objetivo("CHINA", 6, "07001"), objetivo("PARIS", 6, "07001")));
    }

    @Test
    void chinaTienePrioridadYParisSeQuedaCorta() {
        EnvioInput envio = generar(ejemplo()).getEnvio();

        assertEquals(20, unidadesDe(envio, "CHINA", "BAG-A", "NOIR"));
        assertEquals(11, unidadesDe(envio, "PARIS", "BAG-A", "NOIR"));
    }

    @Test
    void chinaSaleConCuatroCajasSinMezclarNada() {
        EnvioInput envio = generar(ejemplo()).getEnvio();

        assertEquals(4, cajasDe(envio, "CHINA"));
        assertEquals(List.of(10, 10), unidadesPorCajaDe(envio, "CHINA", "BAG-A", "NOIR"));
        assertEquals(List.of(10), unidadesPorCajaDe(envio, "CHINA", "BAG-A", "BEIGE"));
        assertEquals(List.of(6), unidadesPorCajaDe(envio, "CHINA", "BAG-B", "NOIR"));
    }

    @Test
    void parisSaleConCuatroCajasYLosColoresNoSeMezclan() {
        EnvioInput envio = generar(ejemplo()).getEnvio();

        assertEquals(4, cajasDe(envio, "PARIS"));
        assertEquals(List.of(6, 5), unidadesPorCajaDe(envio, "PARIS", "BAG-A", "NOIR"));
    }

    @Test
    void lasCuatroCajasDeChinaCabenEnUnPalet() {
        EnvioInput envio = generar(ejemplo()).getEnvio();

        assertEquals(1, paletsDe(envio, "CHINA").size());
    }

    @Test
    void cadaPaletEsUnRangoContiguoDeNumerosDeCaja() {
        // Sin esto, PaletAssignmentService no podría volver a asignar los
        // palets al reimportar el JSON generado.
        EnvioInput envio = generar(ejemplo()).getEnvio();

        for (EnvioInput.DestinoInput destino : envio.getDestinos()) {
            List<Integer> numeros = numerosDeCaja(destino);
            for (EnvioInput.PaletInput palet : destino.getPalets()) {
                long dentro = numeros.stream()
                        .filter(n -> n >= palet.getCajaInicio() && n <= palet.getCajaFin())
                        .count();
                assertEquals(palet.getCajaFin() - palet.getCajaInicio() + 1, dentro);
            }
        }
    }

    @Test
    void amiNumeraLasCajasSeguidoEntreDestinaciones() {
        EnvioInput envio = generar(ejemplo()).getEnvio();

        List<Integer> todos = envio.getDestinos().stream()
                .flatMap(d -> numerosDeCaja(d).stream()).sorted().toList();
        assertEquals(IntStream.rangeClosed(1, 8).boxed().toList(), todos);
    }

    @Test
    void apcReiniciaLaNumeracionEnCadaDestinacion() {
        EnvioInput envio = generarComoApc(ejemplo()).getEnvio();

        assertTrue(envio.getDestinos().stream()
                .allMatch(d -> numerosDeCaja(d).contains(1)));
    }

    @Test
    void losPesosVanEnBlancoParaQueLosInfieraElPasoDeSiempre() {
        EnvioInput envio = generar(ejemplo()).getEnvio();

        assertTrue(envio.getDestinos().stream()
                .flatMap(d -> d.getReferencias().stream())
                .flatMap(r -> r.getCajas().stream())
                .allMatch(c -> c.getPesoBruto() == null));
    }

    @Test
    void elResumenCuentaLoMismoQueElEnvio() {
        ResultadoPackingTaller resultado = generar(ejemplo());

        ResumenDestino china = resumenDe(resultado, "CHINA");
        assertEquals(36, china.unidades());
        assertEquals(4, china.cajas());
        assertEquals(1, china.palets());
        assertEquals(147, china.alturaUtilCm());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=GeneradorPackingTallerTest`
Expected: FAIL

- [ ] **Step 3: Implementar**

Orquesta: reparto → por destinación (en orden de primera aparición) agrupar con la mezcla del padre → apilar con la altura útil → numerar → montar `EnvioInput`.

Numeración: se recorren los palets en orden y, dentro, sus cajas en orden de pila, asignando números correlativos. `CONTINUA` no reinicia el contador entre destinaciones; `POR_DESTINACION` sí. Cada `PaletInput` recibe el primer y el último número que ha repartido.

Montaje del `EnvioInput`: una `ReferenciaInput` por (referencia, color, talla, pedido) dentro de la destinación, con `medidaCaja` y `canal` (la hija, si difiere del padre), y sus `CajaRangoInput`: cajas consecutivas con las mismas unidades se emiten como rango, y el resto como cajas sueltas. `pesoBruto` siempre `null`.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=GeneradorPackingTallerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/taller/GeneradorPackingTaller.java src/test/java/com/puntotres/packinglist/service/taller/GeneradorPackingTallerTest.java
git commit -m "de lo que manda el taller al mismo JSON de envio de siempre"
```

---

### Task 14: Digestión

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/taller/DigestionTallerService.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/DigestionTaller.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/GrupoReferencia.java`
- Create: `src/main/java/com/puntotres/packinglist/service/taller/FilaDigerida.java`
- Test: `src/test/java/com/puntotres/packinglist/service/taller/DigestionTallerServiceTest.java`

**Interfaces:**
- Produces:
  `DigestionTallerService.digerir(String clienteClave, byte[] excelTaller, byte[] excelPedido)` → `DigestionTaller`;
  `DigestionTaller` con `List<GrupoReferencia> getGrupos()`, `List<String> getDestinosActivos()`, `List<String> getAvisos()`, `List<String> getBloqueos()`;
  `GrupoReferencia` con `String getReferencia()`, `String getMedidaCaja()`, `Integer getUnidadesPorCaja()`, `OrigenDato getOrigen()`, `List<FilaDigerida> getFilas()`;
  `enum OrigenDato { MEMORIA, TALLER, POR_DEFECTO }`;
  `FilaDigerida` con `getColor()`, `getTalla()`, `getRecibido()`, `Map<String,Integer> getObjetivos()`, `boolean isSinPedido()`.

- [ ] **Step 1: Escribir el test que falla**

```java
class DigestionTallerServiceTest {

    @Test
    void lasFilasSeAgrupanPorReferencia() {
        DigestionTaller digestion = digerir(
                lineas("AMI", "BAG-A", "NOIR", "U", 31, "AMI", "BAG-A", "BEIGE", "U", 20));

        assertEquals(1, digestion.getGrupos().size());
        assertEquals(2, digestion.getGrupos().get(0).getFilas().size());
    }

    @Test
    void laMemoriaGanaAlExcelDeTaller() {
        memoria.recordar("AMI", "BAG-A", "60x40x30", 6);

        GrupoReferencia grupo = digerir(lineaConUnidadesPorCaja("BAG-A", 10)).getGrupos().get(0);

        assertEquals("60x40x30", grupo.getMedidaCaja());
        assertEquals(6, grupo.getUnidadesPorCaja());
        assertEquals(OrigenDato.MEMORIA, grupo.getOrigen());
    }

    @Test
    void sinMemoriaValeLoQueTraeElTaller() {
        GrupoReferencia grupo = digerir(lineaConUnidadesPorCaja("BAG-A", 10)).getGrupos().get(0);

        assertEquals(10, grupo.getUnidadesPorCaja());
        assertEquals(OrigenDato.TALLER, grupo.getOrigen());
    }

    @Test
    void sinMemoriaYSinDatoDelTallerQuedaPendienteYBloquea() {
        GrupoReferencia grupo = digerir(lineaSinUnidadesPorCaja("BAG-A")).getGrupos().get(0);

        assertEquals("60x40x40", grupo.getMedidaCaja());
        assertNull(grupo.getUnidadesPorCaja(), "null, nunca un centinela como -1");
        assertEquals(OrigenDato.POR_DEFECTO, grupo.getOrigen());
    }

    @Test
    void unClienteDistintoDelSeleccionadoEsBloqueante() {
        DigestionTaller digestion = digerir(lineas("MOD.TEST", "BAG-A", "NOIR", "U", 5));

        assertTrue(digestion.getBloqueos().stream().anyMatch(b -> b.contains("MOD.TEST")));
    }

    @Test
    void laDestinacionDelTallerQueNoCuadraConElPedidoSoloAvisa() {
        // El taller escribe JA y el pedido manda a CHINA: manda el pedido.
        DigestionTaller digestion = digerirConDestinoTaller("JA", "CHINA");

        assertTrue(digestion.getAvisos().stream().anyMatch(a -> a.contains("JA")));
        assertTrue(digestion.getBloqueos().isEmpty());
        assertEquals(List.of("CHINA"), digestion.getDestinosActivos());
    }

    @Test
    void laTallaSeparaFilasDeLaMismaReferenciaYColor() {
        DigestionTaller digestion = digerir(
                lineas("AMI", "UBL1", "2221", "75", 10, "AMI", "UBL1", "2221", "85", 4));

        assertEquals(1, digestion.getGrupos().size());
        assertEquals(2, digestion.getGrupos().get(0).getFilas().size());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=DigestionTallerServiceTest`
Expected: FAIL

- [ ] **Step 3: Implementar**

Lee el Excel de taller, comprueba que todas las filas traen el cliente seleccionado (si no, bloqueo nombrando los otros), pide los objetivos al `ObjetivosPedido` del cliente (o los deja como la `QUANTITE` del taller si no hay ninguno), resuelve medida y unidades por caja con la cascada memoria → taller → por defecto, y agrupa por referencia con una fila por color+talla. Los destinos activos son los que aparezcan en algún objetivo, en orden de primera aparición.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=DigestionTallerServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/taller/ src/test/java/com/puntotres/packinglist/service/taller/DigestionTallerServiceTest.java
git commit -m "digerir lo del taller cruzandolo con el pedido y con lo aprendido"
```

---

### Task 15: `PreparacionRevisionService`

Extrae de `PackingListController` el tramo `EnvioInput` → pantalla de revisión, hoy en las líneas 228-303, para que lo compartan las cuatro entradas.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/PreparacionRevisionService.java`
- Modify: `src/main/java/com/puntotres/packinglist/web/PackingListController.java:228-303,705-713`
- Test: `src/test/java/com/puntotres/packinglist/service/PreparacionRevisionServiceTest.java`

**Interfaces:**
- Produces: `PreparacionRevisionService.preparar(EnvioInput envio, DatosEnvio cabecera, ClienteConfig cliente, byte[] excelPedido, List<String> avisosPrevios, EnvioEnCurso envioEnCurso)` → `void` (deja el envío listo en `envioEnCurso`), y `void reinferir(EnvioEnCurso envioEnCurso)`.

- [ ] **Step 1: Escribir el test que falla**

```java
@SpringBootTest
@ActiveProfiles("test")
class PreparacionRevisionServiceTest {

    @Autowired private PreparacionRevisionService preparacion;
    @Autowired private EnvioEnCurso envioEnCurso;
    @Autowired private ClientesProperties clientes;

    @Test
    void dejaElEnvioListoParaLaPantallaDeRevision() {
        EnvioInput envio = envioDeUnaCajaEn("CHINA");

        preparacion.preparar(envio, cabecera(), clientes.clientePara("AMI").orElseThrow(),
                null, List.of("aviso previo"), envioEnCurso);

        assertFalse(envioEnCurso.estaVacio());
        assertEquals(1, envioEnCurso.getImportado().getDestinos().size());
        assertTrue(envioEnCurso.getImportado().getAvisos().contains("aviso previo"));
    }

    @Test
    void asignaLosPaletsYInfiereLosPesos() {
        preparacion.preparar(envioConPaletYTamanoConTara(), cabecera(),
                clientes.clientePara("AMI").orElseThrow(), null, List.of(), envioEnCurso);

        CajaData caja = envioEnCurso.getImportado().getDestinos().get(0)
                .getDestino().getCajas().get(0);
        assertEquals(1, caja.getNumeroPalet());
        assertNotNull(caja.getPesoBrutoKg());
    }

    @Test
    void resuelveLasDestinacionesHijasAntesDeAsignarPalets() {
        preparacion.preparar(envioDeUnaCajaEn("CHINE FRANCH"), cabecera(),
                clientes.clientePara("APC").orElseThrow(), null, List.of(), envioEnCurso);

        assertEquals("WHOLESALE", envioEnCurso.getImportado().getDestinos().get(0)
                .getDestino().getNombreDestino());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=PreparacionRevisionServiceTest`
Expected: FAIL

- [ ] **Step 3: Extraer el servicio**

Mueve, sin cambiar comportamiento: `envioEnCurso.reiniciar()`, guardado de cabecera e importado, guardado del excel de pedido, `ResolutorDestinosPadre`, `PedidoCompletionService` para APC, bucle de `PaletAssignmentService` y `reinferirTodoElEnvio()` (que pasa a ser `reinferir(EnvioEnCurso)` público del servicio). El controlador lo llama y `reinferirTodoElEnvio()` privado delega en él.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=PreparacionRevisionServiceTest`
Expected: PASS

- [ ] **Step 5: Toda la suite en verde**

Run: `mvn test`
Expected: PASS. `PackingListControllerTest` es el que valida que la extracción no ha cambiado nada.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/PreparacionRevisionService.java src/main/java/com/puntotres/packinglist/web/PackingListController.java src/test/java/com/puntotres/packinglist/service/PreparacionRevisionServiceTest.java
git commit -m "el tramo de JSON a pantalla de revision, compartido por las cuatro entradas"
```

---

### Task 16: Paso 1a en la web

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/web/EnvioForm.java`
- Modify: `src/main/resources/templates/entrada.html`
- Create: `src/main/java/com/puntotres/packinglist/web/PackingListTallerController.java`
- Create: `src/main/java/com/puntotres/packinglist/web/TallerEnCurso.java`
- Test: `src/test/java/com/puntotres/packinglist/web/PackingListTallerControllerTest.java`

**Interfaces:**
- Produces: `TallerEnCurso` (`@Component @SessionScope`) con los bytes y nombres de los dos excels, `DigestionTaller`, `DatosEnvio`, clave de cliente, altura tecleada y los ajustes del usuario; `estaVacio()` y `reiniciar()`.

- [ ] **Step 1: Escribir el test que falla**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PackingListTallerControllerTest {

    @Test
    void digerirLlevaAlPasoDeAjuste() throws Exception {
        mvc.perform(multipart("/packing-list/taller/digerir")
                        .file(excelTallerReal())
                        .file(excelPedidoAmi())
                        .param("cliente", "AMI").param("temporada", "H26")
                        .param("numeroFactura", "FA-1").param("fechaFactura", "05/09/2026")
                        .param("fechaEnvio", "05/09/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("taller-ajuste"))
                .andExpect(model().attributeExists("digestion"));
    }

    @Test
    void sinExcelDeTallerSeVuelveALaEntradaConElError() throws Exception {
        mvc.perform(multipart("/packing-list/taller/digerir")
                        .param("cliente", "AMI").param("temporada", "H26")
                        .param("numeroFactura", "FA-1").param("fechaFactura", "05/09/2026")
                        .param("fechaEnvio", "05/09/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"));
    }

    @Test
    void sinLaHojaDeColisSeDiceQueHojasTieneElFichero() throws Exception {
        mvc.perform(multipart("/packing-list/taller/digerir")
                        .file(excelSinHojaDeColis())
                        .file(excelPedidoAmi())
                        .param("cliente", "AMI").param("temporada", "H26")
                        .param("numeroFactura", "FA-1").param("fechaFactura", "05/09/2026")
                        .param("fechaEnvio", "05/09/2026"))
                .andExpect(view().name("entrada"))
                .andExpect(model().attribute("errorJson", containsString("FACTURE")));
    }

    @Test
    void laEntradaOfreceElModoTaller() throws Exception {
        mvc.perform(get("/packing-list"))
                .andExpect(content().string(containsString("data-modo=\"TALLER\"")));
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=PackingListTallerControllerTest`
Expected: FAIL

- [ ] **Step 3: Implementar**

`EnvioForm` gana `MultipartFile excelTaller` e `Integer alturaMaximaPaletCm`.

`entrada.html`: cuarto botón `<button type="button" id="botonModoTALLER" data-modo="TALLER">TALLER</button>`; bloque `bloqueTaller` con el input de fichero y el de altura; y en `seleccionarModo`, además de lo que ya hace, cambiar `form.action` a `/packing-list/taller/digerir` cuando el modo sea `TALLER` (y a `/importar` en los demás), poner el texto del botón a `Digerir Datos Taller`, mostrar el bloque de pedido de cliente también en modo TALLER, y ocultar el campo de altura si el cliente es AMI o APC.

`PackingListTallerController.digerir`: valida, lee los dos ficheros, llama a `DigestionTallerService`, guarda todo en `TallerEnCurso` y devuelve la vista `taller-ajuste`. Los errores de lectura vuelven a `entrada` con `errorJson`, igual que hace hoy el modo CLAUDE.

Cuando el error sea `HojaNoEncontradaException`, además del mensaje se mete en el modelo `hojasDelLibro` y el fichero subido se guarda en `TallerEnCurso`: `entrada.html` pinta entonces un `<select name="hojaTaller">` con esos nombres y un botón que reenvía a `/packing-list/taller/digerir` sin volver a subir nada, que es la elección manual de hoja que pide el diseño (§3.1). El parámetro `hojaTaller`, cuando llega, se pasa a `TallerColisExcel.desdeBytes(contenido, nombreHoja)`.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=PackingListTallerControllerTest`
Expected: PASS (la plantilla `taller-ajuste.html` puede ser todavía un esqueleto; se completa en la Task 17).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/web/ src/main/resources/templates/entrada.html src/test/java/com/puntotres/packinglist/web/PackingListTallerControllerTest.java
git commit -m "el boton PACKING LIST TALLER y la pantalla de carga"
```

---

### Task 17: Paso 1b en la web

**Files:**
- Create: `src/main/resources/templates/taller-ajuste.html`
- Create: `src/main/java/com/puntotres/packinglist/web/AjusteTallerForm.java`
- Modify: `src/main/java/com/puntotres/packinglist/web/PackingListTallerController.java`
- Modify: `src/main/resources/static/estilo.css`
- Test: añadir a `PackingListTallerControllerTest`

**Interfaces:**
- Produces: `AjusteTallerForm` con `List<GrupoEditado> grupos` (`referencia`, `medidaCaja`, `unidadesPorCaja`) y `List<FilaEditada> filas` (`referencia`, `color`, `talla`, `Map<String,Integer> objetivos`).

- [ ] **Step 1: Escribir el test que falla**

```java
    @Test
    void previsualizarDevuelveElResumenSinAvanzarDeEtapa() throws Exception {
        digerirUnEnvioDePrueba();

        mvc.perform(post("/packing-list/taller/previsualizar")
                        .param("grupos[0].referencia", "ULL164.AL0052")
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "8"))
                .andExpect(view().name("taller-ajuste"))
                .andExpect(model().attributeExists("resumen"));
    }

    @Test
    void loTecleadoSeConservaAlPrevisualizar() throws Exception {
        digerirUnEnvioDePrueba();

        mvc.perform(post("/packing-list/taller/previsualizar")
                        .param("grupos[0].referencia", "ULL164.AL0052")
                        .param("grupos[0].medidaCaja", "60x40x30")
                        .param("grupos[0].unidadesPorCaja", "4"))
                .andExpect(content().string(containsString("60x40x30")));
    }

    @Test
    void unaMedidaYUnasUnidadesSonPorReferenciaYNoPorColor() throws Exception {
        digerirUnEnvioConDosColores();

        mvc.perform(post("/packing-list/taller/previsualizar")
                        .param("grupos[0].referencia", "ULL164.AL0052")
                        .param("grupos[0].medidaCaja", "60x40x30")
                        .param("grupos[0].unidadesPorCaja", "4"))
                .andExpect(model().attribute("resumen", hasSize(greaterThan(0))));
        // Los dos colores han usado 4 uds/caja: el total de cajas lo demuestra.
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=PackingListTallerControllerTest`
Expected: FAIL

- [ ] **Step 3: Implementar la plantilla y el endpoint**

`taller-ajuste.html`: bloque de bloqueos (rojo) arriba, bloque de avisos debajo, y una `section` por grupo de referencia con la cabecera editable (medida en un `<select>` con las medidas conocidas más una opción "otra" que revela un input de texto, y unidades por caja como `<input type="number">`), la marca de origen del dato, y su tabla de colores con una columna por destinación activa. Al final, panel de resumen si viene en el modelo, y los dos botones (`Previsualizar` y `Generar Packing`), el segundo con `th:disabled="${!bloqueos.isEmpty()}"`.

Los dos botones son submits del mismo formulario, como en la revisión: así lo tecleado no se pierde nunca.

En `estilo.css`, las clases del grupo (`.grupo-referencia`, `.origen-dato`, `.fila-bloqueada`), siguiendo la paleta que ya hay.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=PackingListTallerControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/taller-ajuste.html src/main/resources/static/estilo.css src/main/java/com/puntotres/packinglist/web/ src/test/java/com/puntotres/packinglist/web/PackingListTallerControllerTest.java
git commit -m "la pantalla de ajuste: medida y unidades por referencia, objetivos por color"
```

---

### Task 18: Generar y volver

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/web/PackingListTallerController.java`
- Modify: `src/main/resources/templates/revision.html` (enlace de vuelta)
- Test: añadir a `PackingListTallerControllerTest`

- [ ] **Step 1: Escribir el test que falla**

```java
    @Test
    void generarLlevaALaRevisionConElEnvioListo() throws Exception {
        digerirUnEnvioDePrueba();

        mvc.perform(post("/packing-list/taller/generar")
                        .param("grupos[0].referencia", "ULL164.AL0052")
                        .param("grupos[0].medidaCaja", "60x40x40")
                        .param("grupos[0].unidadesPorCaja", "8"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/revision"));

        assertFalse(envioEnCurso.estaVacio());
    }

    @Test
    void generarRecuerdaLasReferenciasParaLaProximaVez() throws Exception {
        digerirUnEnvioDePrueba();

        mvc.perform(post("/packing-list/taller/generar")
                .param("grupos[0].referencia", "ULL164.AL0052")
                .param("grupos[0].medidaCaja", "60x40x40")
                .param("grupos[0].unidadesPorCaja", "8"));

        assertEquals(8, memoria.buscar("AMI", "ULL164.AL0052").orElseThrow().unidadesPorCaja());
    }

    @Test
    void conBloqueosNoSeGenera() throws Exception {
        digerirUnEnvioSinUnidadesPorCaja();

        mvc.perform(post("/packing-list/taller/generar"))
                .andExpect(view().name("taller-ajuste"));

        assertTrue(envioEnCurso.estaVacio());
    }

    @Test
    void seVuelveAlAjusteSinResubirLosFicheros() throws Exception {
        digerirUnEnvioDePrueba();

        mvc.perform(get("/packing-list/taller/ajuste"))
                .andExpect(status().isOk())
                .andExpect(view().name("taller-ajuste"))
                .andExpect(model().attributeExists("digestion"));
    }

    @Test
    void volverAlAjusteConservaLoTecleado() throws Exception {
        digerirUnEnvioDePrueba();
        mvc.perform(post("/packing-list/taller/previsualizar")
                .param("grupos[0].referencia", "ULL164.AL0052")
                .param("grupos[0].medidaCaja", "60x40x30")
                .param("grupos[0].unidadesPorCaja", "4"));

        mvc.perform(get("/packing-list/taller/ajuste"))
                .andExpect(content().string(containsString("60x40x30")));
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=PackingListTallerControllerTest`
Expected: FAIL

- [ ] **Step 3: Implementar**

`generar`: aplica los ajustes sobre la digestión guardada, llama a `GeneradorPackingTaller`; si hay bloqueos, vuelve a `taller-ajuste` con ellos; si no, guarda la memoria de referencias (solo entradas válidas), llama a `PreparacionRevisionService.preparar` con el `EnvioInput`, el excel de pedido ya subido y los avisos de la digestión como avisos previos, y redirige a `/revision`.

`ajuste` (GET): si `TallerEnCurso` está vacío, redirige a `/packing-list` con mensaje; si no, repinta el paso 1b con los ajustes guardados.

En `revision.html`, un enlace "← Volver al ajuste del taller" visible solo cuando el envío venga del taller (`th:if="${vieneDeTaller}"`, atributo que añade `PackingListController.revision` preguntando a `TallerEnCurso`).

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=PackingListTallerControllerTest`
Expected: PASS

- [ ] **Step 5: Toda la suite en verde**

Run: `mvn test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/web/ src/main/resources/templates/revision.html src/test/java/com/puntotres/packinglist/web/PackingListTallerControllerTest.java
git commit -m "generar el packing y poder volver a ajustarlo sin resubir nada"
```

---

### Task 19: Documentación y cierre

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`
- Modify: `src/test/resources/ejemplos/README.md`
- Modify: `src/test/java/com/puntotres/packinglist/web/RutasTest.java`
- Modify: `TODO`

- [ ] **Step 1: Añadir las rutas nuevas a `RutasTest`**

```java
    @Test
    void lasRutasDelFlujoDeTallerResponden() throws Exception {
        mvc.perform(get("/taras")).andExpect(status().isOk());
        mvc.perform(get("/packing-list/taller/ajuste")).andExpect(status().is3xxRedirection());
    }
```

- [ ] **Step 2: Ejecutar**

Run: `mvn test -Dtest=RutasTest`
Expected: PASS

- [ ] **Step 3: Documentar en `CLAUDE.md`**

Sección nueva **"Entrada por packing list de taller"** con lo que no se deduce del código: que el packing del taller se descarta a propósito; que la altura es la última dimensión y por qué (el catálogo de taras); que el pedido de AMI trae `Commandé` y la destinación pegada al PO; que en APC un `Document d'achat` es una destinación, un artículo y un color, y por eso el `CODE` basta; las dos reglas de mezcla (D9 y D10) con su motivo; que las taras se han movido a base de datos y `TaraProperties` es solo la semilla; y que el `EnvioInput` generado se ofrece en el paso 1b para poder mirar el algoritmo sin depurador.

- [ ] **Step 4: Actualizar `README.md` y el README de ejemplos**

En `README.md`, la cuarta vía de entrada y la pantalla `/taras`. En `src/test/resources/ejemplos/README.md`, que el `.xlsx` de taller es un **template del taller sin datos utilizables** y que su cabecera está desalineada (`F9` dice `U` donde debería decir `TAILLE`), para que nadie deduzca de él cómo son los datos reales.

- [ ] **Step 5: Anotar el pendiente del template**

En `TODO`, una línea: `- [ ] Corregir el template de packing list de taller (TAILLE en F9, borrar M6) y actualizar la copia de src/test/resources/ejemplos/taller/ con sus aserciones.`

- [ ] **Step 6: Toda la suite en verde**

Run: `mvn test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add CLAUDE.md README.md TODO src/test/resources/ejemplos/README.md src/test/java/com/puntotres/packinglist/web/RutasTest.java
git commit -m "documentar la entrada por taller y lo que no se deduce leyendo el codigo"
```
