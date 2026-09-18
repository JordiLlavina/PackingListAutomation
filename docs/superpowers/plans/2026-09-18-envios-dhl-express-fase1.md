# Envíos DHL Express (Fase 1) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sección nueva de la app para crear envíos con DHL Express (MyDHL API): libreta de contactos, cuentas DHL, catálogo de artículos aduaneros, borrador → tarifa → confirmación → creación con guía y PDFs, pantallas Thymeleaf y API REST.

**Architecture:** Núcleo `shipping` (entidades JPA, máquina de estados en `ShipmentService`, puerto `CarrierClient`) sin nada de DHL; adaptador `carrier.dhl` con `RestClient`, DTOs propios y mappers; Flyway con baseline sobre el esquema H2 actual; web sin JavaScript con submits que recargan, como el resto de la app; el estado del envío vive en base de datos, no en sesión.

**Tech Stack:** Java 17, Spring Boot 3.5.3 (web, thymeleaf, validation, data-jpa), H2, Flyway, `RestClient`, Jackson, Apache Commons CSV, WireMock 3 (tests), JUnit 5, Mockito (viene con `spring-boot-starter-test`).

**Spec:** `docs/superpowers/specs/2026-09-18-envios-dhl-express-fase1-design.md` — leerla entera antes de empezar. Este plan la implementa; cuando dude, manda la spec.

## Global Constraints

- Idioma: **nombres de clase en inglés, campos, métodos, javadoc, comentarios y UI en español**. URLs de pantallas en español (`/envios`), API en inglés (`/api/shipments`), tal cual la spec.
- Cada tarea termina con **`mvn -q test` en verde** y un commit en la rama `envios-dhl`. Todos los tests corren con perfil `test` (lo fija surefire; no añadir `@ActiveProfiles`).
- Tests unitarios con `new` (y Mockito donde haga falta un repositorio); `@SpringBootTest` solo para rutas, contexto y Flyway.
- **Nunca fallar en silencio, nunca bloquear por lo que un humano puede resolver**: problemas de datos → listas de textos que se pintan; excepciones solo para transiciones ilegales, envío inexistente y fallos del transportista (`CarrierException`).
- Los DTOs de DHL **no salen** de `com.puntotres.packinglist.carrier.dhl`. `shipping` no importa nada de `carrier.dhl`.
- Nada de JavaScript en las plantillas: añadir/quitar filas son submits del formulario.
- `spring.jpa.open-in-view: false` ya está puesto: toda colección que use una vista se inicializa dentro del servicio (`@Transactional`).
- Pesos `Double` (`null` = desconocido), importes `BigDecimal`, moneda `EUR` por defecto (`envios.moneda`).
- No tocar `Main.java`, las plantillas `.xlsx` ni nada del flujo de packing lists.
- Comentarios de código en español explicando el **porqué** que no se ve en el código (estilo del proyecto). Sin comentarios de relleno.
- El fichero real `docs/Envios DHL/address-book-*.csv` tiene datos personales de terceros: **no copiarlo a `src/test/resources`**.

## Estructura de ficheros

```
src/main/resources/db/migration/
  V1__esquema_inicial.sql                       tablas que hoy crea Hibernate (baseline)
  V2__envios.sql                                tablas nuevas + semilla de motivos
src/main/java/com/puntotres/packinglist/shipping/
  config/EnviosProperties.java                  bloque envios.* del yml
  model/                                        entidades, enums y repositorios
    CarrierCode, ShipmentStatus, Direction, Incoterm, BillingRole, ContactType,
    ExportReasonType, Shipment, ShipmentPackage, CustomsLineItem, RateOffer,
    ShipmentDocument, ShipmentStatusHistory, Contact, BillingAccount,
    ShipmentReason, Article, *Repository
  carrier/                                      puerto hacia los transportistas
    CarrierClient, CarrierClientRegistry, CarrierException, ProductOffer,
    CreatedShipment, CarrierDocument, AddressCheck, TipoDireccion, TrackingEvent
  service/
    DirectionResolver, CustomsChecker, DatosEnvio (+DatosBulto, DatosLinea),
    FiltroEnvios, ResultadoTarifa, ShipmentService, EnvioNoEncontradoException,
    TransicionIlegalException, Notifier, NoOpNotifier,
    ContactService, BillingAccountService, ShipmentReasonService, ArticleService
  importacion/
    LectorCsv, FilaLibreta, PrevisualizacionLibreta, ImportadorLibretaMyDhl,
    FilaArticulo, PrevisualizacionArticulos, ImportadorArticulosCsv,
    ResultadoImportacion
  web/
    EnviosController, EnvioForm (+BultoForm, LineaForm), FiltroEnviosForm,
    ContactosController, ContactoForm, ImportacionEnCurso,
    CuentasController, MotivosController, ArticulosController
  api/
    ShipmentsApiController, CatalogosApiController, ApiExceptionHandler,
    ShipmentDto, ShipmentInput, OfferDto, ContactDto, ErrorApi
src/main/java/com/puntotres/packinglist/carrier/dhl/
  DhlProperties, DhlRestClientFactory, DhlLogInterceptor, DhlExpressClient,
  DhlRequestMapper, DhlResponseMapper, DhlErrorTranslator,
  dto/ (records request/response con los nombres del OpenAPI)
src/main/resources/templates/
  envios.html, envio.html, contactos.html, contacto.html, contactos-importar.html,
  cuentas.html, motivos.html, articulos.html, articulos-importar.html
src/test/resources/ejemplos/envios/
  libreta-mydhl-recorte.csv, articulos.csv,
  __files/dhl-rates-response.json, __files/dhl-shipment-response.json, __files/dhl-error-400.json
  (los JSON van en __files/ porque WireMock los sirve desde ahí con withBodyFile)
docs/Envios DHL/README.md
```

---

### Task 1: Flyway con baseline sobre el esquema actual

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml` (bloque `spring`)
- Modify: `src/test/resources/application-test.yml`
- Create: `src/main/resources/db/migration/V1__esquema_inicial.sql`
- Test: `src/test/java/com/puntotres/packinglist/persistence/EsquemaFlywayTest.java`

**Interfaces:**
- Produces: la convención `db/migration/V<n>__<nombre>.sql` que usan las tareas siguientes, y `ddl-auto: validate` en todos los perfiles.

- [ ] **Step 1: Crear la rama**

```bash
git checkout -b envios-dhl
```

- [ ] **Step 2: Escribir el test que exige que Flyway haya creado el esquema**

`src/test/java/com/puntotres/packinglist/persistence/EsquemaFlywayTest.java`:

```java
package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El esquema lo crea Flyway y Hibernate solo lo valida. Si alguien vuelve a
 * poner ddl-auto: update, la tabla de historial de Flyway sigue existiendo
 * pero las tablas nuevas se crearían por dos caminos distintos y acabarían
 * divergiendo entre la base del almacén y la de los tests.
 */
@SpringBootTest
class EsquemaFlywayTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayHaAplicadoElBaselineYLasTablasExisten() {
        List<String> versiones = jdbc.queryForList(
                "select version from flyway_schema_history where success = true order by installed_rank",
                String.class);
        assertTrue(versiones.contains("1"), "V1 aplicada: " + versiones);

        Integer tablas = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'PUBLIC' "
                        + "and table_name in ('TARA_CAJA', 'MEMORIA_CAJA_REFERENCIA', 'TEMPORADA_GUARDADA')",
                Integer.class);
        assertEquals(3, tablas);
    }
}
```

- [ ] **Step 3: Ejecutar el test y ver que falla**

Run: `mvn -q test -Dtest=EsquemaFlywayTest`
Expected: FAIL — `flyway_schema_history` no existe (Flyway no está en el classpath).

- [ ] **Step 4: Añadir Flyway al pom**

En `pom.xml`, después de la dependencia `h2`:

```xml
        <!-- Migraciones de esquema. Hasta aquí lo creaba Hibernate con
             ddl-auto: update; con más de tres tablas eso deja de ser
             controlable y una columna renombrada se convertiría en una
             columna nueva vacía sin avisar. La base del almacén, que ya
             existe, entra por baseline (ver application.yml). -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
```

- [ ] **Step 5: Escribir V1 con el DDL exacto que Hibernate generó**

`src/main/resources/db/migration/V1__esquema_inicial.sql` — es el DDL que Hibernate 6 produce para las tres entidades de `persistence/` (obtenido con `jakarta.persistence.schema-generation.scripts.action=create`); no cambiar ni un tipo, o `validate` fallará contra la base del almacén:

```sql
-- Esquema que Hibernate creaba con ddl-auto: update antes de Flyway. En una
-- base que ya lo tiene, Flyway marca esta versión como baseline y no la
-- ejecuta; en una base nueva (tests, otro ordenador) la crea desde cero.
create table memoria_caja_referencia (
    peso_neto_kg float(53),
    unidades_por_caja integer not null,
    fecha_actualizacion timestamp(6) not null,
    medida_caja varchar(40) not null,
    cliente varchar(60) not null,
    referencia varchar(80) not null,
    primary key (cliente, referencia)
);

create table tara_caja (
    tara_kg float(53) not null,
    fecha_actualizacion timestamp(6) not null,
    medida varchar(40) not null,
    primary key (medida)
);

create table temporada_guardada (
    fecha_actualizacion timestamp(6) not null,
    id bigint generated by default as identity,
    cliente varchar(60) not null,
    temporada varchar(60) not null,
    nombre_fichero varchar(255) not null,
    excel blob not null,
    primary key (id),
    constraint uk_temporada_por_cliente unique (cliente, temporada)
);
```

- [ ] **Step 6: Configurar Flyway y pasar Hibernate a validate**

En `src/main/resources/application.yml`, sustituir el bloque `jpa`:

```yaml
  jpa:
    hibernate:
      # El esquema lo crea Flyway (db/migration). Hibernate solo comprueba
      # que las entidades cuadran con él y arranca en error si no.
      ddl-auto: validate
    open-in-view: false

  # La base del almacén ya tenía las tablas de V1 creadas por Hibernate:
  # baseline-on-migrate la marca en la versión 1 sin tocarla y aplica el
  # resto. Una base vacía ejecuta todo desde V1.
  flyway:
    baseline-on-migrate: true
    baseline-version: 1
```

En `src/test/resources/application-test.yml`, sustituir `ddl-auto: create-drop` por `ddl-auto: validate` (en memoria la base está vacía, Flyway la crea entera).

- [ ] **Step 7: Ejecutar la suite completa**

Run: `mvn -q test`
Expected: PASS — todos, incluido `EsquemaFlywayTest`. Si `validate` protesta por alguna columna, el DDL de V1 no coincide con la entidad: corregir el SQL, nunca la entidad.

- [ ] **Step 8: Verificar contra una copia de la base real (manual, obligatorio)**

Con la aplicación parada (la base está bloqueada mientras corre):

```bash
mkdir -p /tmp/prueba-baseline && cp datos/packinglist.mv.db /tmp/prueba-baseline/packinglist.mv.db
mvn -q spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:file:/tmp/prueba-baseline/packinglist;DB_CLOSE_DELAY=-1"
```

Expected: arranca sin error, el log dice `Successfully baselined schema with version: 1`, y `/taras` y `/temporadas` enseñan los datos de siempre. Parar y borrar `/tmp/prueba-baseline`.

- [ ] **Step 9: Commit**

```bash
git add pom.xml src/main/resources/application.yml src/test/resources/application-test.yml src/main/resources/db/migration/V1__esquema_inicial.sql src/test/java/com/puntotres/packinglist/persistence/EsquemaFlywayTest.java
git commit -m "el esquema lo crea flyway, con baseline sobre la base que ya existe"
```

---

### Task 2: Núcleo — enums, entidades, repositorios y V2

**Files:**
- Create: `src/main/resources/db/migration/V2__envios.sql`
- Create: `src/main/java/com/puntotres/packinglist/shipping/model/*.java` (enums, entidades, repositorios)
- Test: `src/test/java/com/puntotres/packinglist/shipping/model/ShipmentTest.java`
- Test: `src/test/java/com/puntotres/packinglist/shipping/model/EsquemaEnviosTest.java`

**Interfaces:**
- Produces: las entidades y repositorios que usan todas las tareas siguientes. Nombres exactos abajo.

- [ ] **Step 1: Enums**

`shipping/model/CarrierCode.java`:
```java
package com.puntotres.packinglist.shipping.model;

/** Transportistas con adaptador. Añadir uno es un valor aquí y un paquete carrier.<nombre>. */
public enum CarrierCode { DHL_EXPRESS }
```

`shipping/model/ShipmentStatus.java`:
```java
package com.puntotres.packinglist.shipping.model;

public enum ShipmentStatus { DRAFT, QUOTED, CONFIRMED, CREATED, CANCELLED, ERROR }
```

`shipping/model/Direction.java`:
```java
package com.puntotres.packinglist.shipping.model;

/**
 * Sentido respecto a España, donde está la sede. CROSS_TRADE es un envío que
 * ni sale ni entra en España (Marruecos → Francia); DOMESTIC, dentro de España.
 */
public enum Direction {
    EXPORT("Exportación"), IMPORT("Importación"), DOMESTIC("Nacional"), CROSS_TRADE("Entre terceros países");

    private final String etiqueta;
    Direction(String etiqueta) { this.etiqueta = etiqueta; }
    public String getEtiqueta() { return etiqueta; }
}
```

`shipping/model/Incoterm.java` (los 16 del OpenAPI, en el mismo orden):
```java
package com.puntotres.packinglist.shipping.model;

public enum Incoterm { EXW, FCA, CPT, CIP, DPU, DAP, DDP, FAS, FOB, CFR, CIF, DAF, DAT, DDU, DEQ, DES }
```

`shipping/model/BillingRole.java`:
```java
package com.puntotres.packinglist.shipping.model;

/** Para qué puede usarse una cuenta DHL: remitente, pagadora del transporte, pagadora de aranceles. */
public enum BillingRole {
    SHIPPER("Remitente"), PAYER("Transporte"), DUTIES("Aranceles");

    private final String etiqueta;
    BillingRole(String etiqueta) { this.etiqueta = etiqueta; }
    public String getEtiqueta() { return etiqueta; }
}
```

`shipping/model/ContactType.java`:
```java
package com.puntotres.packinglist.shipping.model;

public enum ContactType { BUSINESS, PRIVATE }
```

`shipping/model/ExportReasonType.java`:
```java
package com.puntotres.packinglist.shipping.model;

/** Los exportReasonType del OpenAPI de MyDHL (3.3.2). codigoDhl() es el literal que viaja en el JSON. */
public enum ExportReasonType {
    PERMANENT("permanent"), TEMPORARY("temporary"), RETURN("return"),
    USED_EXHIBITION_GOODS_TO_ORIGIN("used_exhibition_goods_to_origin"),
    INTERCOMPANY_USE("intercompany_use"), COMMERCIAL_PURPOSE_OR_SALE("commercial_purpose_or_sale"),
    PERSONAL_BELONGINGS_OR_PERSONAL_USE("personal_belongings_or_personal_use"),
    SAMPLE("sample"), GIFT("gift"), RETURN_TO_ORIGIN("return_to_origin"),
    WARRANTY_REPLACEMENT("warranty_replacement");

    private final String codigoDhl;
    ExportReasonType(String codigoDhl) { this.codigoDhl = codigoDhl; }
    public String codigoDhl() { return codigoDhl; }
}
```

- [ ] **Step 2: Entidades de catálogo — BillingAccount, ShipmentReason, Article, Contact**

`shipping/model/BillingAccount.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.*;

/**
 * Una cuenta DHL: la propia o la de un cliente que paga él el transporte o los
 * aranceles. Los roles dicen en qué desplegable puede aparecer.
 */
@Entity
@Table(name = "billing_account", uniqueConstraints = @UniqueConstraint(name = "uk_billing_account_numero", columnNames = "numero"))
public class BillingAccount {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero", length = 12, nullable = false)
    private String numero;

    @Column(name = "titular", length = 120, nullable = false)
    private String titular;

    @Column(name = "propia", nullable = false)
    private boolean propia;

    @Column(name = "activa", nullable = false)
    private boolean activa = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "billing_account_roles", joinColumns = @JoinColumn(name = "billing_account_id"))
    @Column(name = "rol", length = 10, nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<BillingRole> roles = EnumSet.noneOf(BillingRole.class);

    protected BillingAccount() { }

    public BillingAccount(String numero, String titular, boolean propia, Set<BillingRole> roles) {
        this.numero = numero;
        this.titular = titular;
        this.propia = propia;
        this.roles = EnumSet.copyOf(roles);
    }

    public boolean permite(BillingRole rol) { return roles.contains(rol); }

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public String getTitular() { return titular; }
    public boolean isPropia() { return propia; }
    public boolean isActiva() { return activa; }
    public Set<BillingRole> getRoles() { return roles; }
    public void setNumero(String numero) { this.numero = numero; }
    public void setTitular(String titular) { this.titular = titular; }
    public void setPropia(boolean propia) { this.propia = propia; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public void setRoles(Set<BillingRole> roles) { this.roles = roles.isEmpty() ? EnumSet.noneOf(BillingRole.class) : EnumSet.copyOf(roles); }
    public void anadirRol(BillingRole rol) { roles.add(rol); }
}
```

`shipping/model/ShipmentReason.java`:
```java
package com.puntotres.packinglist.shipping.model;

import jakarta.persistence.*;

/** Motivo del envío (muestras, producción...). motivoDhl es lo que va en la declaración de exportación. */
@Entity
@Table(name = "shipment_reason", uniqueConstraints = @UniqueConstraint(name = "uk_shipment_reason_nombre", columnNames = "nombre"))
public class ShipmentReason {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", length = 60, nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_dhl", length = 40, nullable = false)
    private ExportReasonType motivoDhl;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected ShipmentReason() { }

    public ShipmentReason(String nombre, ExportReasonType motivoDhl) {
        this.nombre = nombre;
        this.motivoDhl = motivoDhl;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public ExportReasonType getMotivoDhl() { return motivoDhl; }
    public boolean isActivo() { return activo; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setMotivoDhl(ExportReasonType motivoDhl) { this.motivoDhl = motivoDhl; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
```

`shipping/model/Article.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.math.BigDecimal;

import jakarta.persistence.*;

/** Artículo del catálogo aduanero: lo que hace falta para rellenar una línea de aduana sin teclearla. */
@Entity
@Table(name = "article", uniqueConstraints = @UniqueConstraint(name = "uk_article_referencia", columnNames = "referencia"))
public class Article {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referencia", length = 60, nullable = false)
    private String referencia;

    @Column(name = "descripcion_aduanera", length = 255, nullable = false)
    private String descripcionAduanera;

    @Column(name = "codigo_hs", length = 20, nullable = false)
    private String codigoHs;

    @Column(name = "pais_origen", length = 2, nullable = false)
    private String paisOrigen;

    @Column(name = "peso_neto_unitario")
    private Double pesoNetoUnitario;

    @Column(name = "peso_bruto_unitario")
    private Double pesoBrutoUnitario;

    @Column(name = "valor_unitario", precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "moneda", length = 3, nullable = false)
    private String moneda = "EUR";

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected Article() { }

    public Article(String referencia, String descripcionAduanera, String codigoHs, String paisOrigen) {
        this.referencia = referencia;
        this.descripcionAduanera = descripcionAduanera;
        this.codigoHs = codigoHs;
        this.paisOrigen = paisOrigen;
    }

    public Long getId() { return id; }
    public String getReferencia() { return referencia; }
    public String getDescripcionAduanera() { return descripcionAduanera; }
    public String getCodigoHs() { return codigoHs; }
    public String getPaisOrigen() { return paisOrigen; }
    public Double getPesoNetoUnitario() { return pesoNetoUnitario; }
    public Double getPesoBrutoUnitario() { return pesoBrutoUnitario; }
    public BigDecimal getValorUnitario() { return valorUnitario; }
    public String getMoneda() { return moneda; }
    public boolean isActivo() { return activo; }
    public void setReferencia(String v) { referencia = v; }
    public void setDescripcionAduanera(String v) { descripcionAduanera = v; }
    public void setCodigoHs(String v) { codigoHs = v; }
    public void setPaisOrigen(String v) { paisOrigen = v; }
    public void setPesoNetoUnitario(Double v) { pesoNetoUnitario = v; }
    public void setPesoBrutoUnitario(Double v) { pesoBrutoUnitario = v; }
    public void setValorUnitario(BigDecimal v) { valorUnitario = v; }
    public void setMoneda(String v) { moneda = v; }
    public void setActivo(boolean v) { activo = v; }
}
```

`shipping/model/Contact.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

/**
 * Entrada de la libreta: remitente o destinatario. Los "por defecto" vienen
 * de la libreta de MyDHL+ y preseleccionan cuenta e incoterm en el formulario.
 * Baja lógica (activo=false): un contacto usado en envíos no se borra.
 */
@Entity
@Table(name = "contact")
public class Contact {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa", length = 120, nullable = false) private String empresa;
    @Column(name = "persona", length = 120) private String persona;
    @Column(name = "direccion1", length = 45, nullable = false) private String direccion1;
    @Column(name = "direccion2", length = 45) private String direccion2;
    @Column(name = "direccion3", length = 45) private String direccion3;
    @Column(name = "codigo_postal", length = 12) private String codigoPostal;
    @Column(name = "ciudad", length = 45, nullable = false) private String ciudad;
    @Column(name = "provincia", length = 45) private String provincia;
    @Column(name = "provincia_codigo", length = 35) private String provinciaCodigo;
    /** ISO 3166 alfa-2, en mayúsculas. */
    @Column(name = "pais", length = 2, nullable = false) private String pais;
    /** Con prefijo internacional: +34 934425135. */
    @Column(name = "telefono", length = 35) private String telefono;
    @Column(name = "email", length = 100) private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 10, nullable = false) private ContactType tipo = ContactType.BUSINESS;
    @Column(name = "residencial", nullable = false) private boolean residencial;

    @Column(name = "nif_vat", length = 35) private String nifVat;
    @Column(name = "eori", length = 35) private String eori;
    /** ICE de las empresas marroquíes. No viene en la libreta de MyDHL+. */
    @Column(name = "identificador_marroqui", length = 35) private String identificadorMarroqui;
    @Column(name = "notas", length = 1000) private String notas;

    @Enumerated(EnumType.STRING)
    @Column(name = "incoterm_por_defecto", length = 3) private Incoterm incotermPorDefecto;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "cuenta_transporte_por_defecto_id")
    private BillingAccount cuentaTransportePorDefecto;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "cuenta_aranceles_por_defecto_id")
    private BillingAccount cuentaArancelesPorDefecto;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "contact_alias", joinColumns = @JoinColumn(name = "contact_id"))
    @Column(name = "alias", length = 120, nullable = false)
    private List<String> alias = new ArrayList<>();

    @Column(name = "activo", nullable = false) private boolean activo = true;
    @Column(name = "fecha_actualizacion", nullable = false) private LocalDateTime fechaActualizacion = LocalDateTime.now();

    protected Contact() { }

    public Contact(String empresa, String direccion1, String ciudad, String pais) {
        this.empresa = empresa;
        this.direccion1 = direccion1;
        this.ciudad = ciudad;
        this.pais = pais;
    }

    /** Lo que se ve en el desplegable: EMPRESA · Persona · Ciudad (PAÍS). */
    public String etiqueta() {
        StringBuilder sb = new StringBuilder(empresa);
        if (persona != null && !persona.isBlank() && !persona.equalsIgnoreCase(empresa)) sb.append(" · ").append(persona);
        sb.append(" · ").append(ciudad).append(" (").append(pais).append(")");
        return sb.toString();
    }

    public void tocar() { fechaActualizacion = LocalDateTime.now(); }

    // getters y setters de todos los campos (id solo getter)
    public Long getId() { return id; }
    public String getEmpresa() { return empresa; } public void setEmpresa(String v) { empresa = v; }
    public String getPersona() { return persona; } public void setPersona(String v) { persona = v; }
    public String getDireccion1() { return direccion1; } public void setDireccion1(String v) { direccion1 = v; }
    public String getDireccion2() { return direccion2; } public void setDireccion2(String v) { direccion2 = v; }
    public String getDireccion3() { return direccion3; } public void setDireccion3(String v) { direccion3 = v; }
    public String getCodigoPostal() { return codigoPostal; } public void setCodigoPostal(String v) { codigoPostal = v; }
    public String getCiudad() { return ciudad; } public void setCiudad(String v) { ciudad = v; }
    public String getProvincia() { return provincia; } public void setProvincia(String v) { provincia = v; }
    public String getProvinciaCodigo() { return provinciaCodigo; } public void setProvinciaCodigo(String v) { provinciaCodigo = v; }
    public String getPais() { return pais; } public void setPais(String v) { pais = v == null ? null : v.toUpperCase(); }
    public String getTelefono() { return telefono; } public void setTelefono(String v) { telefono = v; }
    public String getEmail() { return email; } public void setEmail(String v) { email = v; }
    public ContactType getTipo() { return tipo; } public void setTipo(ContactType v) { tipo = v; }
    public boolean isResidencial() { return residencial; } public void setResidencial(boolean v) { residencial = v; }
    public String getNifVat() { return nifVat; } public void setNifVat(String v) { nifVat = v; }
    public String getEori() { return eori; } public void setEori(String v) { eori = v; }
    public String getIdentificadorMarroqui() { return identificadorMarroqui; } public void setIdentificadorMarroqui(String v) { identificadorMarroqui = v; }
    public String getNotas() { return notas; } public void setNotas(String v) { notas = v; }
    public Incoterm getIncotermPorDefecto() { return incotermPorDefecto; } public void setIncotermPorDefecto(Incoterm v) { incotermPorDefecto = v; }
    public BillingAccount getCuentaTransportePorDefecto() { return cuentaTransportePorDefecto; } public void setCuentaTransportePorDefecto(BillingAccount v) { cuentaTransportePorDefecto = v; }
    public BillingAccount getCuentaArancelesPorDefecto() { return cuentaArancelesPorDefecto; } public void setCuentaArancelesPorDefecto(BillingAccount v) { cuentaArancelesPorDefecto = v; }
    public List<String> getAlias() { return alias; } public void setAlias(List<String> v) { alias = new ArrayList<>(v); }
    public boolean isActivo() { return activo; } public void setActivo(boolean v) { activo = v; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
```

- [ ] **Step 3: Entidades del envío — ShipmentPackage, CustomsLineItem, RateOffer, ShipmentDocument, ShipmentStatusHistory**

`shipping/model/ShipmentPackage.java`:
```java
package com.puntotres.packinglist.shipping.model;

import jakarta.persistence.*;

/** Un bulto físico: peso en kg y medidas en cm. */
@Entity
@Table(name = "shipment_package")
public class ShipmentPackage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "peso") private Double peso;
    @Column(name = "largo") private Double largo;
    @Column(name = "ancho") private Double ancho;
    @Column(name = "alto") private Double alto;
    @Column(name = "descripcion", length = 70) private String descripcion;

    protected ShipmentPackage() { }

    public ShipmentPackage(Double peso, Double largo, Double ancho, Double alto, String descripcion) {
        this.peso = peso; this.largo = largo; this.ancho = ancho; this.alto = alto; this.descripcion = descripcion;
    }

    public boolean completo() { return peso != null && largo != null && ancho != null && alto != null; }

    public Long getId() { return id; }
    public Double getPeso() { return peso; }
    public Double getLargo() { return largo; }
    public Double getAncho() { return ancho; }
    public Double getAlto() { return alto; }
    public String getDescripcion() { return descripcion; }
}
```

`shipping/model/CustomsLineItem.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.math.BigDecimal;

import jakarta.persistence.*;

/** Una línea de la declaración de exportación (factura comercial). */
@Entity
@Table(name = "customs_line_item")
public class CustomsLineItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "descripcion", length = 255) private String descripcion;
    @Column(name = "codigo_hs", length = 20) private String codigoHs;
    @Column(name = "cantidad") private Integer cantidad;
    @Column(name = "unidad", length = 5, nullable = false) private String unidad = "PCS";
    @Column(name = "valor_unitario", precision = 12, scale = 2) private BigDecimal valorUnitario;
    @Column(name = "moneda", length = 3, nullable = false) private String moneda = "EUR";
    @Column(name = "peso_neto") private Double pesoNeto;
    @Column(name = "peso_bruto") private Double pesoBruto;
    @Column(name = "pais_origen", length = 2) private String paisOrigen;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "article_id")
    private Article articulo;

    protected CustomsLineItem() { }

    public CustomsLineItem(String descripcion, String codigoHs, Integer cantidad, BigDecimal valorUnitario,
                           Double pesoNeto, Double pesoBruto, String paisOrigen, Article articulo) {
        this.descripcion = descripcion; this.codigoHs = codigoHs; this.cantidad = cantidad;
        this.valorUnitario = valorUnitario; this.pesoNeto = pesoNeto; this.pesoBruto = pesoBruto;
        this.paisOrigen = paisOrigen == null ? null : paisOrigen.toUpperCase(); this.articulo = articulo;
    }

    /** cantidad × valor unitario, o cero si falta algo. */
    public BigDecimal valorTotal() {
        if (cantidad == null || valorUnitario == null) return BigDecimal.ZERO;
        return valorUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public boolean completa() {
        return descripcion != null && !descripcion.isBlank() && codigoHs != null && !codigoHs.isBlank()
                && cantidad != null && cantidad > 0 && valorUnitario != null && paisOrigen != null && !paisOrigen.isBlank();
    }

    public Long getId() { return id; }
    public String getDescripcion() { return descripcion; }
    public String getCodigoHs() { return codigoHs; }
    public Integer getCantidad() { return cantidad; }
    public String getUnidad() { return unidad; }
    public BigDecimal getValorUnitario() { return valorUnitario; }
    public String getMoneda() { return moneda; }
    public Double getPesoNeto() { return pesoNeto; }
    public Double getPesoBruto() { return pesoBruto; }
    public String getPaisOrigen() { return paisOrigen; }
    public Article getArticulo() { return articulo; }
    public Long getArticuloId() { return articulo == null ? null : articulo.getId(); }
}
```

`shipping/model/RateOffer.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

/** Un producto de DHL con su precio, tal cual lo devolvió la tarifa. Se borra al volver a DRAFT. */
@Entity
@Table(name = "rate_offer")
public class RateOffer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producto_codigo", length = 10, nullable = false) private String productoCodigo;
    @Column(name = "producto_nombre", length = 80, nullable = false) private String productoNombre;
    @Column(name = "precio", precision = 12, scale = 2) private BigDecimal precio;
    @Column(name = "moneda", length = 3) private String moneda;
    @Column(name = "entrega_estimada") private LocalDateTime entregaEstimada;

    protected RateOffer() { }

    public RateOffer(String productoCodigo, String productoNombre, BigDecimal precio, String moneda, LocalDateTime entregaEstimada) {
        this.productoCodigo = productoCodigo; this.productoNombre = productoNombre;
        this.precio = precio; this.moneda = moneda; this.entregaEstimada = entregaEstimada;
    }

    public Long getId() { return id; }
    public String getProductoCodigo() { return productoCodigo; }
    public String getProductoNombre() { return productoNombre; }
    public BigDecimal getPrecio() { return precio; }
    public String getMoneda() { return moneda; }
    public LocalDateTime getEntregaEstimada() { return entregaEstimada; }
}
```

`shipping/model/ShipmentDocument.java`:
```java
package com.puntotres.packinglist.shipping.model;

import jakarta.persistence.*;

/**
 * Un PDF devuelto por el transportista (label, invoice, waybillDoc...). Se
 * guarda entero en la base y no como ruta: la base viaja de un ordenador a
 * otro y una ruta se rompe al mover la carpeta.
 */
@Entity
@Table(name = "shipment_document")
public class ShipmentDocument {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "shipment_id")
    private Shipment envio;

    @Column(name = "tipo", length = 20, nullable = false) private String tipo;
    @Column(name = "formato", length = 10, nullable = false) private String formato;
    @Lob @Column(name = "contenido", nullable = false) private byte[] contenido;

    protected ShipmentDocument() { }

    public ShipmentDocument(Shipment envio, String tipo, String formato, byte[] contenido) {
        this.envio = envio; this.tipo = tipo; this.formato = formato; this.contenido = contenido;
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public String getFormato() { return formato; }
    public byte[] getContenido() { return contenido; }
}
```

`shipping/model/ShipmentStatusHistory.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/** Una transición de estado, con quién y por qué. */
@Entity
@Table(name = "shipment_status_history")
public class ShipmentStatusHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(name = "estado_anterior", length = 12) private ShipmentStatus estadoAnterior;
    @Enumerated(EnumType.STRING) @Column(name = "estado_nuevo", length = 12, nullable = false) private ShipmentStatus estadoNuevo;
    @Column(name = "fecha", nullable = false) private LocalDateTime fecha = LocalDateTime.now();
    @Column(name = "responsable", length = 120) private String responsable;
    @Column(name = "detalle", length = 4000) private String detalle;

    protected ShipmentStatusHistory() { }

    public ShipmentStatusHistory(ShipmentStatus estadoAnterior, ShipmentStatus estadoNuevo, String responsable, String detalle) {
        this.estadoAnterior = estadoAnterior; this.estadoNuevo = estadoNuevo;
        this.responsable = responsable; this.detalle = detalle == null ? null : detalle.substring(0, Math.min(4000, detalle.length()));
    }

    public Long getId() { return id; }
    public ShipmentStatus getEstadoAnterior() { return estadoAnterior; }
    public ShipmentStatus getEstadoNuevo() { return estadoNuevo; }
    public LocalDateTime getFecha() { return fecha; }
    public String getResponsable() { return responsable; }
    public String getDetalle() { return detalle; }
}
```

- [ ] **Step 4: Test de la entidad Shipment (cálculos y transición)**

`src/test/java/com/puntotres/packinglist/shipping/model/ShipmentTest.java`:
```java
package com.puntotres.packinglist.shipping.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class ShipmentTest {

    @Test
    void elValorDeclaradoEsLaSumaDeCantidadPorValorUnitario() {
        Shipment envio = new Shipment("Jordi");
        envio.getLineasAduana().add(new CustomsLineItem("Bolsos", "42022100", 10, new BigDecimal("12.50"), 0.4, 0.5, "MA", null));
        envio.getLineasAduana().add(new CustomsLineItem("Cinturones", "42033000", 4, new BigDecimal("3.00"), 0.1, 0.12, "MA", null));

        assertEquals(new BigDecimal("137.00"), envio.valorDeclarado());
    }

    @Test
    void elPesoDeLosBultosYElBrutoDeLasLineasSeSumanSaltandoLosNulos() {
        Shipment envio = new Shipment("Jordi");
        envio.getBultos().add(new ShipmentPackage(5.0, 40.0, 30.0, 20.0, null));
        envio.getBultos().add(new ShipmentPackage(null, 40.0, 30.0, 20.0, null));
        envio.getLineasAduana().add(new CustomsLineItem("Bolsos", "42022100", 10, BigDecimal.ONE, 0.4, 4.5, "MA", null));

        assertEquals(5.0, envio.pesoBultos(), 0.001);
        assertEquals(4.5, envio.pesoBrutoLineas(), 0.001);
    }

    @Test
    void cambiarDeEstadoDejaUnaFilaDeHistorialConElAnteriorYElNuevo() {
        Shipment envio = new Shipment("Jordi");

        envio.cambiarEstado(ShipmentStatus.QUOTED, "Jordi", "3 productos");

        assertEquals(ShipmentStatus.QUOTED, envio.getEstado());
        assertEquals(1, envio.getHistorial().size());
        assertEquals(ShipmentStatus.DRAFT, envio.getHistorial().get(0).getEstadoAnterior());
        assertEquals("3 productos", envio.getHistorial().get(0).getDetalle());
    }

    @Test
    void reemplazarOfertasBorraLasAnterioresYBorrarOfertasDejaLaListaVacia() {
        Shipment envio = new Shipment("Jordi");
        envio.reemplazarOfertas(List.of(new RateOffer("P", "EXPRESS WORLDWIDE", new BigDecimal("50"), "EUR", null)));
        envio.reemplazarOfertas(List.of(new RateOffer("N", "DOMESTIC", new BigDecimal("10"), "EUR", null)));
        assertEquals(List.of("N"), envio.getOfertas().stream().map(RateOffer::getProductoCodigo).toList());

        envio.borrarOfertas();
        assertTrue(envio.getOfertas().isEmpty());
    }
}
```

- [ ] **Step 5: Ejecutar y ver que no compila**

Run: `mvn -q test -Dtest=ShipmentTest`
Expected: error de compilación, `Shipment` no existe.

- [ ] **Step 6: Entidad Shipment**

`shipping/model/Shipment.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

/**
 * Un envío por transportista. Es un dato desde el primer guardado (DRAFT), por
 * eso no vive en sesión: un borrador tiene que sobrevivir a cerrar el navegador.
 *
 * Las transiciones de estado las decide ShipmentService; aquí solo se aplican
 * y se anotan en el historial. requestId se fija al confirmar y viaja a DHL
 * como Message-Reference: reintentar tras un error reutiliza el mismo, para
 * que un envío confirmado no se cree dos veces.
 */
@Entity
@Table(name = "shipment", uniqueConstraints = @UniqueConstraint(name = "uk_shipment_request_id", columnNames = "request_id"))
public class Shipment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(name = "transportista", length = 20, nullable = false)
    private CarrierCode transportista = CarrierCode.DHL_EXPRESS;
    @Enumerated(EnumType.STRING) @Column(name = "estado", length = 12, nullable = false)
    private ShipmentStatus estado = ShipmentStatus.DRAFT;
    @Enumerated(EnumType.STRING) @Column(name = "sentido", length = 12)
    private Direction sentido;
    @Column(name = "aduanero", nullable = false) private boolean aduanero = true;

    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "remitente_id") private Contact remitente;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "destinatario_id") private Contact destinatario;
    @Column(name = "fecha_recogida") private LocalDate fechaRecogida;
    /** Recogida del mensajero en la misma llamada (pickup.isRequested), con hora de cierre HH:MM y lugar; sin pickupDetails, DHL recoge en la dirección del remitente. */
    @Column(name = "pedir_recogida", nullable = false) private boolean pedirRecogida = true;
    @Column(name = "hora_cierre_recogida", length = 5) private String horaCierreRecogida;
    @Column(name = "lugar_recogida", length = 80) private String lugarRecogida;
    @Column(name = "numero_confirmacion_recogida", length = 40) private String numeroConfirmacionRecogida;
    @Column(name = "producto_codigo", length = 10) private String productoCodigo;
    @Column(name = "producto_nombre", length = 80) private String productoNombre;

    @Enumerated(EnumType.STRING) @Column(name = "incoterm", length = 3, nullable = false) private Incoterm incoterm = Incoterm.DAP;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "cuenta_transporte_id") private BillingAccount cuentaTransporte;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "cuenta_aranceles_id") private BillingAccount cuentaAranceles;

    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "motivo_id") private ShipmentReason motivo;
    @Column(name = "cliente", length = 60) private String cliente;
    @Column(name = "temporada", length = 60) private String temporada;
    @Column(name = "referencia", length = 35) private String referencia;
    @Column(name = "notas", length = 1000) private String notas;
    @Column(name = "responsable", length = 120, nullable = false) private String responsable;

    @Column(name = "coste_estimado", precision = 12, scale = 2) private BigDecimal costeEstimado;
    @Column(name = "moneda", length = 3, nullable = false) private String moneda = "EUR";
    @Column(name = "coste_facturado", precision = 12, scale = 2) private BigDecimal costeFacturado;

    @Column(name = "numero_guia", length = 40) private String numeroGuia;
    @Column(name = "url_seguimiento", length = 255) private String urlSeguimiento;
    @Column(name = "ultimo_error", length = 4000) private String ultimoError;
    @Column(name = "request_id", length = 36) private String requestId;

    @Column(name = "fecha_creacion", nullable = false) private LocalDateTime fechaCreacion = LocalDateTime.now();
    @Column(name = "fecha_actualizacion", nullable = false) private LocalDateTime fechaActualizacion = LocalDateTime.now();
    @Version @Column(name = "version", nullable = false) private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true) @JoinColumn(name = "shipment_id", nullable = false)
    @OrderColumn(name = "orden")
    private List<ShipmentPackage> bultos = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true) @JoinColumn(name = "shipment_id", nullable = false)
    @OrderColumn(name = "orden")
    private List<CustomsLineItem> lineasAduana = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true) @JoinColumn(name = "shipment_id", nullable = false)
    @OrderColumn(name = "orden")
    private List<RateOffer> ofertas = new ArrayList<>();

    @OneToMany(mappedBy = "envio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShipmentDocument> documentos = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true) @JoinColumn(name = "shipment_id", nullable = false)
    @OrderBy("fecha asc, id asc")
    private List<ShipmentStatusHistory> historial = new ArrayList<>();

    protected Shipment() { }

    public Shipment(String responsable) {
        this.responsable = responsable;
    }

    public void cambiarEstado(ShipmentStatus nuevo, String quien, String detalle) {
        historial.add(new ShipmentStatusHistory(estado, nuevo, quien, detalle));
        estado = nuevo;
        tocar();
    }

    public void reemplazarOfertas(List<RateOffer> nuevas) {
        ofertas.clear();
        ofertas.addAll(nuevas);
    }

    public void borrarOfertas() { ofertas.clear(); }

    public void anadirDocumento(String tipo, String formato, byte[] contenido) {
        documentos.add(new ShipmentDocument(this, tipo, formato, contenido));
    }

    public BigDecimal valorDeclarado() {
        return lineasAduana.stream().map(CustomsLineItem::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public double pesoBultos() {
        return bultos.stream().map(ShipmentPackage::getPeso).filter(p -> p != null).mapToDouble(Double::doubleValue).sum();
    }

    public double pesoBrutoLineas() {
        return lineasAduana.stream().map(CustomsLineItem::getPesoBruto).filter(p -> p != null).mapToDouble(Double::doubleValue).sum();
    }

    public boolean editable() { return estado == ShipmentStatus.DRAFT || estado == ShipmentStatus.QUOTED; }

    public void tocar() { fechaActualizacion = LocalDateTime.now(); }

    // getters; setters solo de lo que edita el usuario o fija el servicio
    public Long getId() { return id; }
    public CarrierCode getTransportista() { return transportista; }
    public ShipmentStatus getEstado() { return estado; }
    public Direction getSentido() { return sentido; } public void setSentido(Direction v) { sentido = v; }
    public boolean isAduanero() { return aduanero; } public void setAduanero(boolean v) { aduanero = v; }
    public Contact getRemitente() { return remitente; } public void setRemitente(Contact v) { remitente = v; }
    public Contact getDestinatario() { return destinatario; } public void setDestinatario(Contact v) { destinatario = v; }
    public LocalDate getFechaRecogida() { return fechaRecogida; } public void setFechaRecogida(LocalDate v) { fechaRecogida = v; }
    public boolean isPedirRecogida() { return pedirRecogida; } public void setPedirRecogida(boolean v) { pedirRecogida = v; }
    public String getHoraCierreRecogida() { return horaCierreRecogida; } public void setHoraCierreRecogida(String v) { horaCierreRecogida = v; }
    public String getLugarRecogida() { return lugarRecogida; } public void setLugarRecogida(String v) { lugarRecogida = v; }
    public String getNumeroConfirmacionRecogida() { return numeroConfirmacionRecogida; } public void setNumeroConfirmacionRecogida(String v) { numeroConfirmacionRecogida = v; }
    public String getProductoCodigo() { return productoCodigo; } public void setProductoCodigo(String v) { productoCodigo = v; }
    public boolean isPedirRecogida() { return pedirRecogida; } public void setPedirRecogida(boolean v) { pedirRecogida = v; }
    public String getHoraCierreRecogida() { return horaCierreRecogida; } public void setHoraCierreRecogida(String v) { horaCierreRecogida = v; }
    public String getLugarRecogida() { return lugarRecogida; } public void setLugarRecogida(String v) { lugarRecogida = v; }
    public String getProductoNombre() { return productoNombre; } public void setProductoNombre(String v) { productoNombre = v; }
    public Incoterm getIncoterm() { return incoterm; } public void setIncoterm(Incoterm v) { incoterm = v; }
    public BillingAccount getCuentaTransporte() { return cuentaTransporte; } public void setCuentaTransporte(BillingAccount v) { cuentaTransporte = v; }
    public BillingAccount getCuentaAranceles() { return cuentaAranceles; } public void setCuentaAranceles(BillingAccount v) { cuentaAranceles = v; }
    public ShipmentReason getMotivo() { return motivo; } public void setMotivo(ShipmentReason v) { motivo = v; }
    public String getCliente() { return cliente; } public void setCliente(String v) { cliente = v; }
    public String getTemporada() { return temporada; } public void setTemporada(String v) { temporada = v; }
    public String getReferencia() { return referencia; } public void setReferencia(String v) { referencia = v; }
    public String getNotas() { return notas; } public void setNotas(String v) { notas = v; }
    public String getResponsable() { return responsable; } public void setResponsable(String v) { responsable = v; }
    public BigDecimal getCosteEstimado() { return costeEstimado; } public void setCosteEstimado(BigDecimal v) { costeEstimado = v; }
    public String getMoneda() { return moneda; } public void setMoneda(String v) { moneda = v; }
    public BigDecimal getCosteFacturado() { return costeFacturado; }
    public String getNumeroGuia() { return numeroGuia; } public void setNumeroGuia(String v) { numeroGuia = v; }
    public String getUrlSeguimiento() { return urlSeguimiento; } public void setUrlSeguimiento(String v) { urlSeguimiento = v; }
    public String getUltimoError() { return ultimoError; } public void setUltimoError(String v) { ultimoError = v == null ? null : v.substring(0, Math.min(4000, v.length())); }
    public String getRequestId() { return requestId; } public void setRequestId(String v) { requestId = v; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public long getVersion() { return version; }
    public List<ShipmentPackage> getBultos() { return bultos; }
    public List<CustomsLineItem> getLineasAduana() { return lineasAduana; }
    public List<RateOffer> getOfertas() { return ofertas; }
    public List<ShipmentDocument> getDocumentos() { return documentos; }
    public List<ShipmentStatusHistory> getHistorial() { return historial; }
}
```

- [ ] **Step 7: Repositorios**

`shipping/model/ShipmentRepository.java`:
```java
package com.puntotres.packinglist.shipping.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> { }
```

`shipping/model/ShipmentDocumentRepository.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentDocumentRepository extends JpaRepository<ShipmentDocument, Long> {

    Optional<ShipmentDocument> findFirstByEnvioIdAndTipo(Long envioId, String tipo);

    /** Solo los tipos, sin cargar los PDF: es lo que necesita la pantalla para pintar los botones. */
    @Query("select d.tipo from ShipmentDocument d where d.envio.id = :envioId order by d.id")
    List<String> tiposDe(@Param("envioId") Long envioId);
}
```

`shipping/model/ContactRepository.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByActivoTrueOrderByEmpresaAscPersonaAsc();
    List<Contact> findByEmpresaIgnoreCaseAndPersonaIgnoreCase(String empresa, String persona);
}
```

`shipping/model/BillingAccountRepository.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingAccountRepository extends JpaRepository<BillingAccount, Long> {
    Optional<BillingAccount> findByNumero(String numero);
    List<BillingAccount> findByActivaTrueOrderByPropiaDescTitularAsc();
    List<BillingAccount> findAllByOrderByPropiaDescTitularAsc();
}
```

`shipping/model/ShipmentReasonRepository.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentReasonRepository extends JpaRepository<ShipmentReason, Long> {
    List<ShipmentReason> findByActivoTrueOrderByNombreAsc();
    List<ShipmentReason> findAllByOrderByNombreAsc();
    Optional<ShipmentReason> findByNombreIgnoreCase(String nombre);
}
```

`shipping/model/ArticleRepository.java`:
```java
package com.puntotres.packinglist.shipping.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    Optional<Article> findByReferenciaIgnoreCase(String referencia);
    List<Article> findByActivoTrueOrderByReferenciaAsc();
    List<Article> findAllByOrderByReferenciaAsc();
}
```

- [ ] **Step 8: Ejecutar ShipmentTest**

Run: `mvn -q test -Dtest=ShipmentTest`
Expected: PASS (4 tests). La suite completa aún fallará en `validate` porque las tablas no existen: es el paso siguiente.

- [ ] **Step 9: V2 con las tablas y la semilla de motivos**

`src/main/resources/db/migration/V2__envios.sql`:
```sql
-- Sección de envíos por transportista (fase 1: DHL Express). Los tipos
-- siguen los que Hibernate espera para cada campo Java (ddl-auto: validate):
-- Double -> float(53), BigDecimal(12,2) -> numeric(12,2), enum -> varchar,
-- LocalDate -> date, LocalDateTime -> timestamp(6), byte[] @Lob -> blob.

create table billing_account (
    id bigint generated by default as identity,
    numero varchar(12) not null,
    titular varchar(120) not null,
    propia boolean not null,
    activa boolean not null,
    primary key (id),
    constraint uk_billing_account_numero unique (numero)
);

create table billing_account_roles (
    billing_account_id bigint not null,
    rol varchar(10) not null,
    primary key (billing_account_id, rol),
    constraint fk_billing_account_roles foreign key (billing_account_id) references billing_account (id)
);

create table shipment_reason (
    id bigint generated by default as identity,
    nombre varchar(60) not null,
    motivo_dhl varchar(40) not null,
    activo boolean not null,
    primary key (id),
    constraint uk_shipment_reason_nombre unique (nombre)
);

create table article (
    id bigint generated by default as identity,
    referencia varchar(60) not null,
    descripcion_aduanera varchar(255) not null,
    codigo_hs varchar(20) not null,
    pais_origen varchar(2) not null,
    peso_neto_unitario float(53),
    peso_bruto_unitario float(53),
    valor_unitario numeric(12,2),
    moneda varchar(3) not null,
    activo boolean not null,
    primary key (id),
    constraint uk_article_referencia unique (referencia)
);

create table contact (
    id bigint generated by default as identity,
    empresa varchar(120) not null,
    persona varchar(120),
    direccion1 varchar(45) not null,
    direccion2 varchar(45),
    direccion3 varchar(45),
    codigo_postal varchar(12),
    ciudad varchar(45) not null,
    provincia varchar(45),
    provincia_codigo varchar(35),
    pais varchar(2) not null,
    telefono varchar(35),
    email varchar(100),
    tipo varchar(10) not null,
    residencial boolean not null,
    nif_vat varchar(35),
    eori varchar(35),
    identificador_marroqui varchar(35),
    notas varchar(1000),
    incoterm_por_defecto varchar(3),
    cuenta_transporte_por_defecto_id bigint,
    cuenta_aranceles_por_defecto_id bigint,
    activo boolean not null,
    fecha_actualizacion timestamp(6) not null,
    primary key (id),
    constraint fk_contact_cuenta_transporte foreign key (cuenta_transporte_por_defecto_id) references billing_account (id),
    constraint fk_contact_cuenta_aranceles foreign key (cuenta_aranceles_por_defecto_id) references billing_account (id)
);

create table contact_alias (
    contact_id bigint not null,
    alias varchar(120) not null,
    constraint fk_contact_alias foreign key (contact_id) references contact (id)
);

create table shipment (
    id bigint generated by default as identity,
    transportista varchar(20) not null,
    estado varchar(12) not null,
    sentido varchar(12),
    aduanero boolean not null,
    remitente_id bigint,
    destinatario_id bigint,
    fecha_recogida date,
    pedir_recogida boolean not null,
    hora_cierre_recogida varchar(5),
    lugar_recogida varchar(80),
    numero_confirmacion_recogida varchar(40),
    producto_codigo varchar(10),
    producto_nombre varchar(80),
    incoterm varchar(3) not null,
    cuenta_transporte_id bigint,
    cuenta_aranceles_id bigint,
    motivo_id bigint,
    cliente varchar(60),
    temporada varchar(60),
    referencia varchar(35),
    notas varchar(1000),
    responsable varchar(120) not null,
    coste_estimado numeric(12,2),
    moneda varchar(3) not null,
    coste_facturado numeric(12,2),
    numero_guia varchar(40),
    url_seguimiento varchar(255),
    ultimo_error varchar(4000),
    request_id varchar(36),
    fecha_creacion timestamp(6) not null,
    fecha_actualizacion timestamp(6) not null,
    version bigint not null,
    primary key (id),
    constraint uk_shipment_request_id unique (request_id),
    constraint fk_shipment_remitente foreign key (remitente_id) references contact (id),
    constraint fk_shipment_destinatario foreign key (destinatario_id) references contact (id),
    constraint fk_shipment_cuenta_transporte foreign key (cuenta_transporte_id) references billing_account (id),
    constraint fk_shipment_cuenta_aranceles foreign key (cuenta_aranceles_id) references billing_account (id),
    constraint fk_shipment_motivo foreign key (motivo_id) references shipment_reason (id)
);

create table shipment_package (
    id bigint generated by default as identity,
    shipment_id bigint not null,
    orden integer,
    peso float(53),
    largo float(53),
    ancho float(53),
    alto float(53),
    descripcion varchar(70),
    primary key (id),
    constraint fk_shipment_package foreign key (shipment_id) references shipment (id)
);

create table customs_line_item (
    id bigint generated by default as identity,
    shipment_id bigint not null,
    orden integer,
    descripcion varchar(255),
    codigo_hs varchar(20),
    cantidad integer,
    unidad varchar(5) not null,
    valor_unitario numeric(12,2),
    moneda varchar(3) not null,
    peso_neto float(53),
    peso_bruto float(53),
    pais_origen varchar(2),
    article_id bigint,
    primary key (id),
    constraint fk_customs_line_shipment foreign key (shipment_id) references shipment (id),
    constraint fk_customs_line_article foreign key (article_id) references article (id)
);

create table rate_offer (
    id bigint generated by default as identity,
    shipment_id bigint not null,
    orden integer,
    producto_codigo varchar(10) not null,
    producto_nombre varchar(80) not null,
    precio numeric(12,2),
    moneda varchar(3),
    entrega_estimada timestamp(6),
    primary key (id),
    constraint fk_rate_offer_shipment foreign key (shipment_id) references shipment (id)
);

create table shipment_document (
    id bigint generated by default as identity,
    shipment_id bigint not null,
    tipo varchar(20) not null,
    formato varchar(10) not null,
    contenido blob not null,
    primary key (id),
    constraint fk_shipment_document foreign key (shipment_id) references shipment (id)
);

create table shipment_status_history (
    id bigint generated by default as identity,
    shipment_id bigint not null,
    estado_anterior varchar(12),
    estado_nuevo varchar(12) not null,
    fecha timestamp(6) not null,
    responsable varchar(120),
    detalle varchar(4000),
    primary key (id),
    constraint fk_shipment_history foreign key (shipment_id) references shipment (id)
);

-- Motivos habituales. Editables en /envios/motivos; lo que no se use se desactiva.
insert into shipment_reason (nombre, motivo_dhl, activo) values
    ('Muestras', 'SAMPLE', true),
    ('Producción', 'COMMERCIAL_PURPOSE_OR_SALE', true),
    ('Reposición', 'COMMERCIAL_PURPOSE_OR_SALE', true),
    ('Devolución', 'RETURN', true),
    ('Reparación', 'TEMPORARY', true);
```

- [ ] **Step 10: Test del esquema V2 (SpringBootTest)**

`src/test/java/com/puntotres/packinglist/shipping/model/EsquemaEnviosTest.java`:
```java
package com.puntotres.packinglist.shipping.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Que V2 y las entidades cuadren lo comprueba ddl-auto: validate al arrancar;
 * aquí se comprueba además que se puede guardar y releer un envío completo
 * con sus colecciones, que es donde un @OrderColumn o una FK mal puesta se
 * manifiesta.
 */
@SpringBootTest
@Transactional
class EsquemaEnviosTest {

    @Autowired private ShipmentRepository envios;
    @Autowired private ContactRepository contactos;
    @Autowired private BillingAccountRepository cuentas;
    @Autowired private ShipmentReasonRepository motivos;

    @Test
    void laSemillaDeMotivosEstaCargada() {
        assertEquals(5, motivos.findByActivoTrueOrderByNombreAsc().size());
        assertEquals(ExportReasonType.SAMPLE, motivos.findByNombreIgnoreCase("muestras").orElseThrow().getMotivoDhl());
    }

    @Test
    void unEnvioCompletoSeGuardaYSeRelee() {
        Contact origen = contactos.save(new Contact("PUNTOTRES", "C/Industria 585", "Badalona", "ES"));
        Contact destino = contactos.save(new Contact("TALLER", "Zone franche", "Tanger", "MA"));
        BillingAccount cuenta = cuentas.save(new BillingAccount("300112049", "Puntotres", true, EnumSet.allOf(BillingRole.class)));

        Shipment envio = new Shipment("Jordi");
        envio.setRemitente(origen);
        envio.setDestinatario(destino);
        envio.setCuentaTransporte(cuenta);
        envio.setMotivo(motivos.findByNombreIgnoreCase("Muestras").orElseThrow());
        envio.getBultos().add(new ShipmentPackage(2.5, 40.0, 30.0, 20.0, null));
        envio.getLineasAduana().add(new CustomsLineItem("Bolsos de piel", "42022100", 3, new BigDecimal("40.00"), 1.2, 1.5, "MA", null));
        envio.reemplazarOfertas(java.util.List.of(new RateOffer("P", "EXPRESS WORLDWIDE", new BigDecimal("61.20"), "EUR", null)));
        envio.anadirDocumento("label", "PDF", new byte[] {1, 2, 3});
        envio.cambiarEstado(ShipmentStatus.QUOTED, "Jordi", null);
        Long id = envios.saveAndFlush(envio).getId();

        Shipment releido = envios.findById(id).orElseThrow();
        assertEquals(1, releido.getBultos().size());
        assertEquals(1, releido.getLineasAduana().size());
        assertEquals("P", releido.getOfertas().get(0).getProductoCodigo());
        assertEquals(1, releido.getDocumentos().size());
        assertEquals(ShipmentStatus.QUOTED, releido.getHistorial().get(0).getEstadoNuevo());
        assertEquals(new BigDecimal("120.00"), releido.valorDeclarado());
    }
}
```

- [ ] **Step 11: Suite completa**

Run: `mvn -q test`
Expected: PASS. Si `validate` protesta (p. ej. `wrong column type encountered`), corregir la columna en **V2** para que coincida con la anotación.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/shipping/model src/main/resources/db/migration/V2__envios.sql src/test/java/com/puntotres/packinglist/shipping/model
git commit -m "modelo de envios por transportista: entidades, repositorios y migracion V2"
```

---

### Task 3: Puerto `CarrierClient`, tipos del núcleo y `Notifier`

**Files:**
- Create: `shipping/carrier/CarrierClient.java`, `CarrierClientRegistry.java`, `CarrierException.java`, `ProductOffer.java`, `CreatedShipment.java`, `CarrierDocument.java`, `AddressCheck.java`, `TipoDireccion.java`, `TrackingEvent.java`
- Create: `shipping/service/Notifier.java`, `NoOpNotifier.java`
- Test: `src/test/java/com/puntotres/packinglist/shipping/carrier/CarrierClientRegistryTest.java`

**Interfaces:**
- Produces (exactas, las usan `ShipmentService` y `DhlExpressClient`):
  - `interface CarrierClient { CarrierCode codigo(); List<ProductOffer> getProducts(Shipment); List<ProductOffer> quote(Shipment); CreatedShipment createShipment(Shipment); AddressCheck validateAddress(Contact, TipoDireccion); List<TrackingEvent> track(String numeroGuia); }`
  - `record ProductOffer(String codigo, String nombre, BigDecimal precio, String moneda, LocalDateTime entregaEstimada)`
  - `record CreatedShipment(String numeroGuia, String urlSeguimiento, String numeroConfirmacionRecogida, List<CarrierDocument> documentos)` (el número de confirmación de la recogida es null si no se pidió)
  - `record CarrierDocument(String tipo, String formato, byte[] contenido)`
  - `record AddressCheck(boolean valida, String detalle)`
  - `enum TipoDireccion { RECOGIDA, ENTREGA }`
  - `record TrackingEvent(LocalDateTime fecha, String codigo, String descripcion, String lugar)`
  - `class CarrierException extends RuntimeException` con `getMensajeUsuario()`
  - `CarrierClientRegistry.para(CarrierCode)` → `CarrierClient`
  - `interface Notifier { void envioCreado(Shipment); void envioFallido(Shipment, String error); }`

- [ ] **Step 1: Test del registro**

```java
package com.puntotres.packinglist.shipping.carrier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.shipping.model.CarrierCode;
import com.puntotres.packinglist.shipping.model.Contact;
import com.puntotres.packinglist.shipping.model.Shipment;

class CarrierClientRegistryTest {

    private static CarrierClient cliente(CarrierCode codigo) {
        return new CarrierClient() {
            public CarrierCode codigo() { return codigo; }
            public List<ProductOffer> getProducts(Shipment envio) { return List.of(); }
            public List<ProductOffer> quote(Shipment envio) { return List.of(); }
            public CreatedShipment createShipment(Shipment envio) { return null; }
            public AddressCheck validateAddress(Contact contacto, TipoDireccion tipo) { return null; }
            public List<TrackingEvent> track(String numeroGuia) { return List.of(); }
        };
    }

    @Test
    void devuelveElClienteDeCadaTransportista() {
        CarrierClient dhl = cliente(CarrierCode.DHL_EXPRESS);
        CarrierClientRegistry registro = new CarrierClientRegistry(List.of(dhl));

        assertSame(dhl, registro.para(CarrierCode.DHL_EXPRESS));
    }

    @Test
    void sinAdaptadorParaUnTransportistaLanzaConMensajeLegible() {
        CarrierClientRegistry registro = new CarrierClientRegistry(List.of());

        CarrierException e = assertThrows(CarrierException.class, () -> registro.para(CarrierCode.DHL_EXPRESS));
        assertTrue(e.getMensajeUsuario().contains("DHL_EXPRESS"));
    }
}
```

- [ ] **Step 2: Ejecutar y ver que no compila**

Run: `mvn -q test -Dtest=CarrierClientRegistryTest` → error de compilación.

- [ ] **Step 3: Tipos del puerto**

`shipping/carrier/CarrierException.java`:
```java
package com.puntotres.packinglist.shipping.carrier;

/**
 * Fallo hablando con un transportista, ya traducido a un texto que se puede
 * enseñar al usuario (sin URLs internas ni trazas). La causa técnica va en
 * getCause() para el log.
 */
public class CarrierException extends RuntimeException {

    private final String mensajeUsuario;

    public CarrierException(String mensajeUsuario) { this(mensajeUsuario, null); }

    public CarrierException(String mensajeUsuario, Throwable causa) {
        super(mensajeUsuario, causa);
        this.mensajeUsuario = mensajeUsuario;
    }

    public String getMensajeUsuario() { return mensajeUsuario; }
}
```

Records (un fichero cada uno, mismo paquete `com.puntotres.packinglist.shipping.carrier`):
```java
public record ProductOffer(String codigo, String nombre, java.math.BigDecimal precio, String moneda, java.time.LocalDateTime entregaEstimada) { }
public record CarrierDocument(String tipo, String formato, byte[] contenido) { }
public record CreatedShipment(String numeroGuia, String urlSeguimiento, String numeroConfirmacionRecogida, java.util.List<CarrierDocument> documentos) { }
public record AddressCheck(boolean valida, String detalle) { }
public enum TipoDireccion { RECOGIDA, ENTREGA }
public record TrackingEvent(java.time.LocalDateTime fecha, String codigo, String descripcion, String lugar) { }
```

`shipping/carrier/CarrierClient.java`:
```java
package com.puntotres.packinglist.shipping.carrier;

import java.util.List;

import com.puntotres.packinglist.shipping.model.CarrierCode;
import com.puntotres.packinglist.shipping.model.Contact;
import com.puntotres.packinglist.shipping.model.Shipment;

/**
 * Lo que el núcleo necesita de un transportista. Habla solo tipos del núcleo:
 * los DTOs de cada API se quedan en su paquete carrier.<nombre>.
 * Todas las operaciones lanzan CarrierException si el transportista falla.
 */
public interface CarrierClient {

    CarrierCode codigo();

    /** Productos disponibles para el envío, sin precio. */
    List<ProductOffer> getProducts(Shipment envio);

    /** Productos con precio. */
    List<ProductOffer> quote(Shipment envio);

    /** Crea el envío y devuelve guía y documentos. El envío ya trae productoCodigo y requestId. */
    CreatedShipment createShipment(Shipment envio);

    AddressCheck validateAddress(Contact contacto, TipoDireccion tipo);

    /** Fase 2. Los adaptadores de la fase 1 lanzan UnsupportedOperationException. */
    List<TrackingEvent> track(String numeroGuia);
}
```

`shipping/carrier/CarrierClientRegistry.java`:
```java
package com.puntotres.packinglist.shipping.carrier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.shipping.model.CarrierCode;

/** Adaptador de cada transportista, por código. Spring inyecta todos los CarrierClient que haya. */
@Component
public class CarrierClientRegistry {

    private final Map<CarrierCode, CarrierClient> clientes = new EnumMap<>(CarrierCode.class);

    public CarrierClientRegistry(List<CarrierClient> disponibles) {
        for (CarrierClient cliente : disponibles) {
            clientes.put(cliente.codigo(), cliente);
        }
    }

    public CarrierClient para(CarrierCode codigo) {
        CarrierClient cliente = clientes.get(codigo);
        if (cliente == null) {
            throw new CarrierException("No hay adaptador para el transportista " + codigo);
        }
        return cliente;
    }
}
```

- [ ] **Step 4: Notifier y NoOpNotifier**

`shipping/service/Notifier.java`:
```java
package com.puntotres.packinglist.shipping.service;

import com.puntotres.packinglist.shipping.model.Shipment;

/**
 * Avisos al exterior cuando un envío cambia. En la fase 1 no hay ninguno;
 * las fases siguientes añaden correo (Microsoft 365) y Telegram.
 */
public interface Notifier {
    void envioCreado(Shipment envio);
    void envioFallido(Shipment envio, String error);
}
```

`shipping/service/NoOpNotifier.java`:
```java
package com.puntotres.packinglist.shipping.service;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.shipping.model.Shipment;

@Component
public class NoOpNotifier implements Notifier {
    @Override public void envioCreado(Shipment envio) { }
    @Override public void envioFallido(Shipment envio, String error) { }
}
```

- [ ] **Step 5: Ejecutar test y suite**

Run: `mvn -q test -Dtest=CarrierClientRegistryTest` → PASS. Run: `mvn -q test` → PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/shipping/carrier src/main/java/com/puntotres/packinglist/shipping/service src/test/java/com/puntotres/packinglist/shipping/carrier
git commit -m "puerto CarrierClient hacia los transportistas y Notifier vacio"
```

---

### Task 4: Configuración `envios.*`, `DirectionResolver` y `CustomsChecker`

**Files:**
- Create: `shipping/config/EnviosProperties.java`
- Create: `shipping/service/DirectionResolver.java`, `CustomsChecker.java`
- Modify: `src/main/resources/application.yml` (bloque nuevo `envios`)
- Test: `src/test/java/com/puntotres/packinglist/shipping/service/DirectionResolverTest.java`, `CustomsCheckerTest.java`

**Interfaces:**
- Produces: `EnviosProperties` (`getMoneda()`, `getPaisSede()`, `getPaisesUnionAduanera()`, `getResponsables()`), `DirectionResolver.resolver(String origen, String destino)` → `Direction` (null si falta un país), `CustomsChecker.esAduanero(String origen, String destino)` → `boolean`.

- [ ] **Step 1: Tests**

`DirectionResolverTest.java`:
```java
package com.puntotres.packinglist.shipping.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.shipping.model.Direction;

class DirectionResolverTest {

    private final DirectionResolver resolver = new DirectionResolver("ES");

    @Test
    void desdeEspanaEsExportacionYHaciaEspanaImportacion() {
        assertEquals(Direction.EXPORT, resolver.resolver("ES", "MA"));
        assertEquals(Direction.IMPORT, resolver.resolver("MA", "ES"));
    }

    @Test
    void dentroDeEspanaEsNacionalYEntreOtrosDosPaisesEsCrossTrade() {
        assertEquals(Direction.DOMESTIC, resolver.resolver("ES", "ES"));
        assertEquals(Direction.CROSS_TRADE, resolver.resolver("MA", "FR"));
    }

    @Test
    void noDistingueMayusculasYSinUnPaisNoResuelve() {
        assertEquals(Direction.EXPORT, resolver.resolver("es", "ma"));
        assertNull(resolver.resolver(null, "MA"));
        assertNull(resolver.resolver("ES", null));
    }
}
```

`CustomsCheckerTest.java`:
```java
package com.puntotres.packinglist.shipping.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class CustomsCheckerTest {

    private final CustomsChecker checker = new CustomsChecker(List.of("ES", "FR", "IT", "DE"));

    @Test
    void entreDosPaisesDeLaUnionNoHayAduana() {
        assertFalse(checker.esAduanero("ES", "FR"));
        assertFalse(checker.esAduanero("ES", "ES"));
    }

    @Test
    void conUnPaisFueraDeLaUnionSiHay() {
        assertTrue(checker.esAduanero("ES", "MA"));
        assertTrue(checker.esAduanero("MA", "FR"));
        assertTrue(checker.esAduanero("MA", "MA"), "dos países fuera también declaran: solo la unión aduanera del yml se libra");
    }

    @Test
    void sinPaisSeAsumeAduaneroParaNoOcultarLaDeclaracion() {
        assertTrue(checker.esAduanero(null, "MA"));
    }
}
```

- [ ] **Step 2: Ejecutar → no compila**

- [ ] **Step 3: Implementación**

`shipping/config/EnviosProperties.java`:
```java
package com.puntotres.packinglist.shipping.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bloque envios.* de application.yml. Ver el yml para qué es cada cosa. */
@ConfigurationProperties(prefix = "envios")
public class EnviosProperties {

    private String moneda = "EUR";
    private String paisSede = "ES";
    private List<String> paisesUnionAduanera = new ArrayList<>();
    private List<String> responsables = new ArrayList<>();
    private String horaCierreRecogida = "18:00";
    private String lugarRecogida = "Recepción";

    public String getHoraCierreRecogida() { return horaCierreRecogida; }
    public void setHoraCierreRecogida(String v) { this.horaCierreRecogida = v; }
    public String getLugarRecogida() { return lugarRecogida; }
    public void setLugarRecogida(String v) { this.lugarRecogida = v; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
    public String getPaisSede() { return paisSede; }
    public void setPaisSede(String paisSede) { this.paisSede = paisSede; }
    public List<String> getPaisesUnionAduanera() { return paisesUnionAduanera; }
    public void setPaisesUnionAduanera(List<String> v) { this.paisesUnionAduanera = v; }
    public List<String> getResponsables() { return responsables; }
    public void setResponsables(List<String> v) { this.responsables = v; }
}
```

`shipping/service/DirectionResolver.java`:
```java
package com.puntotres.packinglist.shipping.service;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.shipping.config.EnviosProperties;
import com.puntotres.packinglist.shipping.model.Direction;

/**
 * Sentido del envío respecto al país de la sede (España). No es respecto a
 * la UE: eso lo decide CustomsChecker, y son cosas distintas (ES→FR es
 * exportación sin aduana).
 */
@Component
public class DirectionResolver {

    private final String paisSede;

    public DirectionResolver(EnviosProperties propiedades) { this(propiedades.getPaisSede()); }

    DirectionResolver(String paisSede) { this.paisSede = paisSede.toUpperCase(); }

    public Direction resolver(String origen, String destino) {
        if (origen == null || destino == null) return null;
        boolean saleDeSede = paisSede.equalsIgnoreCase(origen);
        boolean llegaASede = paisSede.equalsIgnoreCase(destino);
        if (saleDeSede && llegaASede) return Direction.DOMESTIC;
        if (saleDeSede) return Direction.EXPORT;
        if (llegaASede) return Direction.IMPORT;
        return Direction.CROSS_TRADE;
    }
}
```

`shipping/service/CustomsChecker.java`:
```java
package com.puntotres.packinglist.shipping.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.shipping.config.EnviosProperties;

/** Hay declaración de aduana salvo cuando los dos países están en la unión aduanera del yml. */
@Component
public class CustomsChecker {

    private final Set<String> unionAduanera;

    public CustomsChecker(EnviosProperties propiedades) { this(propiedades.getPaisesUnionAduanera()); }

    CustomsChecker(List<String> paises) {
        this.unionAduanera = paises.stream().map(String::toUpperCase).collect(Collectors.toSet());
    }

    public boolean esAduanero(String origen, String destino) {
        if (origen == null || destino == null) return true;
        return !(unionAduanera.contains(origen.toUpperCase()) && unionAduanera.contains(destino.toUpperCase()));
    }
}
```

- [ ] **Step 4: Bloque `envios` en application.yml** (al final del fichero, nivel raíz)

```yaml
# Envíos por transportista (sección /envios).
envios:
  moneda: EUR
  # El sentido (exportación/importación) se mide respecto a la sede.
  pais-sede: ES
  # Dos países de esta lista no declaran aduana entre sí. Es la UE; si algún
  # día se envía a Suiza o Reino Unido, no están y por eso declaran.
  paises-union-aduanera: [AT, BE, BG, CY, CZ, DE, DK, EE, ES, FI, FR, GR, HR, HU, IE, IT, LT, LU, LV, MT, NL, PL, PT, RO, SE, SI, SK]
  # Quién hace envíos. Es lo que se elige en el formulario y por lo que se
  # agruparán los reports; cuando llegue el login de Microsoft vendrá de ahí.
  responsables:
    - Pol
    - Susana
    - Carme
  # Recogida del mensajero: el envío nace con "Pedir recogida" marcado y estos
  # valores, que se cambian en la ficha. La hora es la de cierre del local (HH:MM).
  hora-cierre-recogida: "18:00"
  lugar-recogida: Recepción
  dhl:
    # Entorno de test de MyDHL API por defecto; producción es la misma URL sin /test.
    base-url: ${DHL_BASE_URL:https://express.api.dhl.com/mydhlapi/test}
    api-key: ${DHL_API_KEY:}
    api-secret: ${DHL_API_SECRET:}
    # Cuenta bajo la que se crean los envíos (accounts[shipper]).
    shipper-account: ${DHL_SHIPPER_ACCOUNT:}
    # ECOM26_84_A4_001 imprime la etiqueta en una hoja A4; ECOM26_84_001 es
    # para impresora térmica de etiquetas.
    plantilla-etiqueta: ECOM26_84_A4_001
    hora-recogida: "12:00"
    timeout-segundos: 30
    # Zona horaria del país de origen, para plannedShippingDateAndTime.
    zonas-horarias:
      ES: Europe/Madrid
      FR: Europe/Paris
      MA: Africa/Casablanca
      IT: Europe/Rome
      PT: Europe/Lisbon
```

- [ ] **Step 5: Ejecutar tests y suite** → PASS (`@ConfigurationPropertiesScan` ya está en `PackingListApplication`).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/shipping/config src/main/java/com/puntotres/packinglist/shipping/service src/main/resources/application.yml src/test/java/com/puntotres/packinglist/shipping/service
git commit -m "configuracion envios.*, sentido respecto a España y aduana respecto a la UE"
```

---

### Task 5: `ShipmentService` — máquina de estados (TDD)

**Files:**
- Create: `shipping/service/DatosEnvio.java`, `FiltroEnvios.java`, `ResultadoTarifa.java`, `EnvioNoEncontradoException.java`, `TransicionIlegalException.java`, `ShipmentService.java`
- Test: `src/test/java/com/puntotres/packinglist/shipping/service/ShipmentServiceTest.java`

**Interfaces:**
- Consumes: entidades y repos (Task 2), `CarrierClient`/`CarrierClientRegistry`/`Notifier` (Task 3), `DirectionResolver`/`CustomsChecker`/`EnviosProperties` (Task 4).
- Produces:
  - `record DatosEnvio(Long remitenteId, Long destinatarioId, LocalDate fechaRecogida, Incoterm incoterm, Long cuentaTransporteId, Long cuentaArancelesId, Long motivoId, String cliente, String temporada, String referencia, String notas, String responsable, Boolean pedirRecogida, String horaCierreRecogida, String lugarRecogida, List<DatosBulto> bultos, List<DatosLinea> lineasAduana)` con records anidados `DatosBulto(Double peso, Double largo, Double ancho, Double alto, String descripcion)` y `DatosLinea(String descripcion, String codigoHs, Integer cantidad, BigDecimal valorUnitario, Double pesoNeto, Double pesoBruto, String paisOrigen, Long articuloId)`.
  - `record FiltroEnvios(LocalDate desde, LocalDate hasta, ShipmentStatus estado, Long motivoId, String cliente, Direction sentido)`.
  - `record ResultadoTarifa(Shipment envio, List<String> problemas, String errorTransportista)` con `boolean ok()`.
  - `ShipmentService`: `Shipment crearBorrador(String responsable)`, `Shipment cargar(Long id)`, `List<Shipment> listar(FiltroEnvios)`, `Shipment editar(Long id, DatosEnvio datos)`, `List<String> problemasParaTarifar(Shipment)`, `List<String> avisos(Shipment)`, `ResultadoTarifa tarifar(Long id)`, `Shipment confirmar(Long id, String productoCodigo)`, `Shipment crear(Long id)`, `Shipment cancelar(Long id)`, `Optional<ShipmentDocument> documento(Long id, String tipo)`, `List<String> tiposDocumento(Long id)`.

- [ ] **Step 1: Test de la máquina de estados**

`ShipmentServiceTest.java` (Mockito para los repositorios; un `CarrierClient` falso escrito a mano):
```java
package com.puntotres.packinglist.shipping.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.shipping.carrier.*;
import com.puntotres.packinglist.shipping.config.EnviosProperties;
import com.puntotres.packinglist.shipping.model.*;

class ShipmentServiceTest {

    /** Transportista de mentira: devuelve lo programado o lanza. */
    static class CarrierFalso implements CarrierClient {
        List<ProductOffer> ofertas = List.of(new ProductOffer("P", "EXPRESS WORLDWIDE", new BigDecimal("61.20"), "EUR", null));
        CreatedShipment creado = new CreatedShipment("1103733901", "https://dhl/1103733901", "PRG240918",
                List.of(new CarrierDocument("label", "PDF", new byte[] {1}), new CarrierDocument("invoice", "PDF", new byte[] {2})));
        CarrierException fallo;
        int llamadasCrear;
        String ultimoRequestId;

        public CarrierCode codigo() { return CarrierCode.DHL_EXPRESS; }
        public List<ProductOffer> getProducts(Shipment envio) { return ofertas; }
        public List<ProductOffer> quote(Shipment envio) { if (fallo != null) throw fallo; return ofertas; }
        public CreatedShipment createShipment(Shipment envio) {
            llamadasCrear++;
            ultimoRequestId = envio.getRequestId();
            if (fallo != null) throw fallo;
            return creado;
        }
        public AddressCheck validateAddress(Contact c, TipoDireccion t) { return new AddressCheck(true, ""); }
        public List<TrackingEvent> track(String guia) { throw new UnsupportedOperationException(); }
    }

    private ShipmentRepository envios;
    private ContactRepository contactos;
    private CarrierFalso carrier;
    private Notifier notifier;
    private ShipmentService servicio;
    private Shipment envio;
    private Contact origen, destino;

    @BeforeEach
    void preparar() {
        envios = mock(ShipmentRepository.class);
        contactos = mock(ContactRepository.class);
        BillingAccountRepository cuentas = mock(BillingAccountRepository.class);
        ShipmentReasonRepository motivos = mock(ShipmentReasonRepository.class);
        ArticleRepository articulos = mock(ArticleRepository.class);
        ShipmentDocumentRepository documentos = mock(ShipmentDocumentRepository.class);
        carrier = new CarrierFalso();
        notifier = mock(Notifier.class);
        EnviosProperties props = new EnviosProperties();
        props.setPaisesUnionAduanera(List.of("ES", "FR"));
        servicio = new ShipmentService(envios, contactos, cuentas, motivos, articulos, documentos,
                new CarrierClientRegistry(List.of(carrier)), notifier,
                new DirectionResolver(props), new CustomsChecker(props), props);

        origen = new Contact("PUNTOTRES", "C/Industria 585", "Badalona", "ES");
        origen.setTelefono("+34934425135");
        destino = new Contact("TALLER", "Zone franche", "Tanger", "MA");
        destino.setTelefono("+212539393394");
        BillingAccount cuenta = new BillingAccount("300112049", "Puntotres", true, java.util.EnumSet.allOf(BillingRole.class));
        ShipmentReason motivo = new ShipmentReason("Muestras", ExportReasonType.SAMPLE);

        envio = new Shipment("Jordi");
        when(envios.findById(1L)).thenReturn(Optional.of(envio));
        when(envios.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contactos.findById(10L)).thenReturn(Optional.of(origen));
        when(contactos.findById(20L)).thenReturn(Optional.of(destino));
        when(cuentas.findById(30L)).thenReturn(Optional.of(cuenta));
        when(motivos.findById(40L)).thenReturn(Optional.of(motivo));
    }

    private DatosEnvio datosCompletos() {
        return new DatosEnvio(10L, 20L, LocalDate.now().plusDays(1), Incoterm.DAP, 30L, null, 40L,
                "AMI", "H26", "MUESTRAS-1", null, "Jordi", true, "18:00", "Recepción",
                List.of(new DatosEnvio.DatosBulto(2.5, 40.0, 30.0, 20.0, null)),
                List.of(new DatosEnvio.DatosLinea("Bolsos de piel", "42022100", 3, new BigDecimal("40"), 1.0, 1.5, "MA", null)));
    }

    @Test
    void editarRellenaElEnvioYDeduceSentidoYAduana() {
        Shipment editado = servicio.editar(1L, datosCompletos());

        assertSame(origen, editado.getRemitente());
        assertEquals(Direction.EXPORT, editado.getSentido());
        assertTrue(editado.isAduanero());
        assertEquals(1, editado.getBultos().size());
        assertEquals(1, editado.getLineasAduana().size());
        assertEquals(ShipmentStatus.DRAFT, editado.getEstado());
    }

    @Test
    void editarUnEnvioTarifadoLoDevuelveABorradorYBorraLasOfertas() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);
        assertEquals(ShipmentStatus.QUOTED, envio.getEstado());

        servicio.editar(1L, datosCompletos());

        assertEquals(ShipmentStatus.DRAFT, envio.getEstado());
        assertTrue(envio.getOfertas().isEmpty());
    }

    @Test
    void tarifarConDatosIncompletosDevuelveProblemasYNoLlamaAlTransportista() {
        ResultadoTarifa resultado = servicio.tarifar(1L);

        assertFalse(resultado.ok());
        assertFalse(resultado.problemas().isEmpty());
        assertEquals(ShipmentStatus.DRAFT, envio.getEstado());
    }

    @Test
    void tarifarGuardaLasOfertasYPasaAQuoted() {
        servicio.editar(1L, datosCompletos());

        ResultadoTarifa resultado = servicio.tarifar(1L);

        assertTrue(resultado.ok());
        assertEquals("P", envio.getOfertas().get(0).getProductoCodigo());
        assertEquals(ShipmentStatus.QUOTED, envio.getEstado());
    }

    @Test
    void siElTransportistaFallaAlTarifarElEnvioSigueEnBorradorConElError() {
        servicio.editar(1L, datosCompletos());
        carrier.fallo = new CarrierException("DHL: código postal no válido");

        ResultadoTarifa resultado = servicio.tarifar(1L);

        assertFalse(resultado.ok());
        assertEquals("DHL: código postal no válido", resultado.errorTransportista());
        assertEquals(ShipmentStatus.DRAFT, envio.getEstado());
        assertEquals("DHL: código postal no válido", envio.getUltimoError());
    }

    @Test
    void confirmarFijaProductoCosteYRequestIdYPasaAConfirmed() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);

        servicio.confirmar(1L, "P");

        assertEquals(ShipmentStatus.CONFIRMED, envio.getEstado());
        assertEquals("P", envio.getProductoCodigo());
        assertEquals(new BigDecimal("61.20"), envio.getCosteEstimado());
        assertNotNull(envio.getRequestId());
        assertEquals(36, envio.getRequestId().length());
    }

    @Test
    void confirmarUnProductoQueNoEstaEnLasOfertasEsIlegal() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);

        assertThrows(TransicionIlegalException.class, () -> servicio.confirmar(1L, "N"));
    }

    @Test
    void confirmarDesdeBorradorEsIlegal() {
        assertThrows(TransicionIlegalException.class, () -> servicio.confirmar(1L, "P"));
    }

    @Test
    void crearGuardaGuiaYDocumentosPasaACreatedYAvisa() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);
        servicio.confirmar(1L, "P");

        servicio.crear(1L);

        assertEquals(ShipmentStatus.CREATED, envio.getEstado());
        assertEquals("1103733901", envio.getNumeroGuia());
        assertEquals("PRG240918", envio.getNumeroConfirmacionRecogida());
        assertEquals(2, envio.getDocumentos().size());
        assertNull(envio.getUltimoError());
        verify(notifier).envioCreado(envio);
    }

    @Test
    void siElTransportistaFallaAlCrearPasaAErrorYReintentarReutilizaElMismoRequestId() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);
        servicio.confirmar(1L, "P");
        String requestId = envio.getRequestId();
        carrier.fallo = new CarrierException("DHL no ha respondido");

        servicio.crear(1L);
        assertEquals(ShipmentStatus.ERROR, envio.getEstado());
        assertEquals("DHL no ha respondido", envio.getUltimoError());
        verify(notifier).envioFallido(envio, "DHL no ha respondido");

        carrier.fallo = null;
        servicio.crear(1L);
        assertEquals(ShipmentStatus.CREATED, envio.getEstado());
        assertEquals(requestId, carrier.ultimoRequestId);
        assertEquals(2, carrier.llamadasCrear);
    }

    @Test
    void crearDesdeQuotedOCreatedEsIlegal() {
        servicio.editar(1L, datosCompletos());
        servicio.tarifar(1L);
        assertThrows(TransicionIlegalException.class, () -> servicio.crear(1L));

        servicio.confirmar(1L, "P");
        servicio.crear(1L);
        assertThrows(TransicionIlegalException.class, () -> servicio.crear(1L), "un envío creado no se crea dos veces");
        assertEquals(1, carrier.llamadasCrear);
    }

    @Test
    void cancelarValeDesdeCualquierEstadoMenosCreatedYCancelled() {
        servicio.cancelar(1L);
        assertEquals(ShipmentStatus.CANCELLED, envio.getEstado());
        assertThrows(TransicionIlegalException.class, () -> servicio.cancelar(1L));
        assertThrows(TransicionIlegalException.class, () -> servicio.editar(1L, datosCompletos()));
    }

    @Test
    void unEnvioQueNoExisteLanzaSuPropiaExcepcion() {
        assertThrows(EnvioNoEncontradoException.class, () -> servicio.cargar(99L));
    }

    @Test
    void losAvisosSenalanNacionalYPesosQueNoCuadran() {
        when(contactos.findById(20L)).thenReturn(Optional.of(origen)); // destino = origen: nacional
        servicio.editar(1L, datosCompletos());
        List<String> avisos = servicio.avisos(envio);
        assertTrue(avisos.stream().anyMatch(a -> a.contains("Nacional")), avisos.toString());

        when(contactos.findById(20L)).thenReturn(Optional.of(destino));
        DatosEnvio pesado = new DatosEnvio(10L, 20L, LocalDate.now().plusDays(1), Incoterm.DAP, 30L, null, 40L,
                null, null, null, null, "Jordi", true, "18:00", null,
                List.of(new DatosEnvio.DatosBulto(1.0, 40.0, 30.0, 20.0, null)),
                List.of(new DatosEnvio.DatosLinea("Bolsos", "42022100", 3, new BigDecimal("40"), 1.0, 5.0, "MA", null)));
        servicio.editar(1L, pesado);
        assertTrue(servicio.avisos(envio).stream().anyMatch(a -> a.contains("bruto")), "Σ bruto líneas 5 > Σ bultos 1");
    }
}
```

- [ ] **Step 2: Ejecutar → no compila**

- [ ] **Step 3: Records y excepciones**

`shipping/service/DatosEnvio.java`:
```java
package com.puntotres.packinglist.shipping.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.puntotres.packinglist.shipping.model.Incoterm;

/**
 * Lo que el usuario teclea de un envío, con ids en vez de entidades. Lo
 * construyen el formulario web y la API, y ShipmentService lo aplica: así los
 * dos caminos resuelven contactos, cuentas y motivo en un solo sitio.
 */
public record DatosEnvio(Long remitenteId, Long destinatarioId, LocalDate fechaRecogida, Incoterm incoterm,
                         Long cuentaTransporteId, Long cuentaArancelesId, Long motivoId,
                         String cliente, String temporada, String referencia, String notas, String responsable,
                         Boolean pedirRecogida, String horaCierreRecogida, String lugarRecogida,
                         List<DatosBulto> bultos, List<DatosLinea> lineasAduana) {

    public record DatosBulto(Double peso, Double largo, Double ancho, Double alto, String descripcion) { }

    public record DatosLinea(String descripcion, String codigoHs, Integer cantidad, BigDecimal valorUnitario,
                             Double pesoNeto, Double pesoBruto, String paisOrigen, Long articuloId) { }

    public DatosEnvio {
        bultos = bultos == null ? List.of() : List.copyOf(bultos);
        lineasAduana = lineasAduana == null ? List.of() : List.copyOf(lineasAduana);
    }
}
```

`shipping/service/FiltroEnvios.java`:
```java
package com.puntotres.packinglist.shipping.service;

import java.time.LocalDate;

import com.puntotres.packinglist.shipping.model.Direction;
import com.puntotres.packinglist.shipping.model.ShipmentStatus;

/** Filtros del listado. Todo opcional; null = no filtrar por eso. */
public record FiltroEnvios(LocalDate desde, LocalDate hasta, ShipmentStatus estado, Long motivoId, String cliente, Direction sentido) {
    public static FiltroEnvios vacio() { return new FiltroEnvios(null, null, null, null, null, null); }
}
```

`shipping/service/ResultadoTarifa.java`:
```java
package com.puntotres.packinglist.shipping.service;

import java.util.List;

import com.puntotres.packinglist.shipping.model.Shipment;

/** Resultado de tarifar: o hay ofertas, o hay problemas de datos, o el transportista ha fallado. */
public record ResultadoTarifa(Shipment envio, List<String> problemas, String errorTransportista) {
    public boolean ok() { return problemas.isEmpty() && errorTransportista == null; }
}
```

```java
package com.puntotres.packinglist.shipping.service;

public class EnvioNoEncontradoException extends RuntimeException {
    public EnvioNoEncontradoException(Long id) { super("No existe el envío " + id); }
}
```

```java
package com.puntotres.packinglist.shipping.service;

/** Una transición que la máquina de estados no permite. Es un fallo de programa o una carrera, no un dato mal tecleado. */
public class TransicionIlegalException extends RuntimeException {
    public TransicionIlegalException(String mensaje) { super(mensaje); }
}
```

- [ ] **Step 4: ShipmentService**

`shipping/service/ShipmentService.java`:
```java
package com.puntotres.packinglist.shipping.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.carrier.*;
import com.puntotres.packinglist.shipping.config.EnviosProperties;
import com.puntotres.packinglist.shipping.model.*;

/**
 * La máquina de estados del envío:
 *
 *   DRAFT ─tarifar→ QUOTED ─confirmar→ CONFIRMED ─crear→ CREATED | ERROR ─crear→ ...
 *   editar desde QUOTED vuelve a DRAFT; cancelar desde todo menos CREATED/CANCELLED.
 *
 * Confirmar persiste el requestId ANTES de hablar con el transportista, y
 * crear lo reutiliza: así un envío confirmado nunca se crea dos veces aunque
 * el proceso muera a medias o el usuario haga doble clic (@Version en Shipment
 * hace que el segundo clic falle con una excepción de concurrencia).
 */
@Service
public class ShipmentService {

    private final ShipmentRepository envios;
    private final ContactRepository contactos;
    private final BillingAccountRepository cuentas;
    private final ShipmentReasonRepository motivos;
    private final ArticleRepository articulos;
    private final ShipmentDocumentRepository documentos;
    private final CarrierClientRegistry transportistas;
    private final Notifier notifier;
    private final DirectionResolver sentidos;
    private final CustomsChecker aduanas;
    private final EnviosProperties propiedades;

    public ShipmentService(ShipmentRepository envios, ContactRepository contactos, BillingAccountRepository cuentas,
                           ShipmentReasonRepository motivos, ArticleRepository articulos, ShipmentDocumentRepository documentos,
                           CarrierClientRegistry transportistas, Notifier notifier,
                           DirectionResolver sentidos, CustomsChecker aduanas, EnviosProperties propiedades) {
        this.envios = envios; this.contactos = contactos; this.cuentas = cuentas; this.motivos = motivos;
        this.articulos = articulos; this.documentos = documentos; this.transportistas = transportistas;
        this.notifier = notifier; this.sentidos = sentidos; this.aduanas = aduanas; this.propiedades = propiedades;
    }

    @Transactional
    public Shipment crearBorrador(String responsable) {
        Shipment envio = new Shipment(responsable);
        envio.setMoneda(propiedades.getMoneda());
        envio.setPedirRecogida(true);
        envio.setHoraCierreRecogida(propiedades.getHoraCierreRecogida());
        envio.setLugarRecogida(propiedades.getLugarRecogida());
        envio.cambiarEstado(ShipmentStatus.DRAFT, responsable, "Borrador creado");
        return envios.save(envio);
    }

    /** El envío con todas sus colecciones inicializadas (open-in-view está apagado). */
    @Transactional(readOnly = true)
    public Shipment cargar(Long id) {
        Shipment envio = buscar(id);
        envio.getBultos().size();
        envio.getLineasAduana().size();
        envio.getOfertas().size();
        envio.getHistorial().size();
        return envio;
    }

    @Transactional(readOnly = true)
    public List<Shipment> listar(FiltroEnvios filtro) {
        Specification<Shipment> spec = (raiz, consulta, cb) -> {
            List<jakarta.persistence.criteria.Predicate> condiciones = new ArrayList<>();
            if (filtro.desde() != null) condiciones.add(cb.greaterThanOrEqualTo(raiz.get("fechaCreacion"), filtro.desde().atStartOfDay()));
            if (filtro.hasta() != null) condiciones.add(cb.lessThan(raiz.get("fechaCreacion"), filtro.hasta().plusDays(1).atStartOfDay()));
            if (filtro.estado() != null) condiciones.add(cb.equal(raiz.get("estado"), filtro.estado()));
            if (filtro.motivoId() != null) condiciones.add(cb.equal(raiz.get("motivo").get("id"), filtro.motivoId()));
            if (filtro.cliente() != null && !filtro.cliente().isBlank())
                condiciones.add(cb.like(cb.upper(raiz.get("cliente")), "%" + filtro.cliente().trim().toUpperCase() + "%"));
            if (filtro.sentido() != null) condiciones.add(cb.equal(raiz.get("sentido"), filtro.sentido()));
            consulta.orderBy(cb.desc(raiz.get("fechaCreacion")));
            return cb.and(condiciones.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return envios.findAll(spec);
    }

    @Transactional
    public Shipment editar(Long id, DatosEnvio datos) {
        Shipment envio = buscar(id);
        if (!envio.editable()) {
            throw new TransicionIlegalException("El envío " + id + " está en " + envio.getEstado() + " y ya no se edita");
        }
        envio.setRemitente(datos.remitenteId() == null ? null : contactos.findById(datos.remitenteId()).orElse(null));
        envio.setDestinatario(datos.destinatarioId() == null ? null : contactos.findById(datos.destinatarioId()).orElse(null));
        envio.setFechaRecogida(datos.fechaRecogida());
        if (datos.incoterm() != null) envio.setIncoterm(datos.incoterm());
        envio.setCuentaTransporte(datos.cuentaTransporteId() == null ? null : cuentas.findById(datos.cuentaTransporteId()).orElse(null));
        envio.setCuentaAranceles(datos.cuentaArancelesId() == null ? null : cuentas.findById(datos.cuentaArancelesId()).orElse(null));
        envio.setMotivo(datos.motivoId() == null ? null : motivos.findById(datos.motivoId()).orElse(null));
        envio.setCliente(limpiar(datos.cliente()));
        envio.setTemporada(limpiar(datos.temporada()));
        envio.setReferencia(limpiar(datos.referencia()));
        envio.setNotas(limpiar(datos.notas()));
        if (datos.responsable() != null && !datos.responsable().isBlank()) envio.setResponsable(datos.responsable());
        envio.setPedirRecogida(datos.pedirRecogida() == null || datos.pedirRecogida());
        envio.setHoraCierreRecogida(limpiar(datos.horaCierreRecogida()));
        envio.setLugarRecogida(limpiar(datos.lugarRecogida()));

        envio.getBultos().clear();
        for (DatosEnvio.DatosBulto b : datos.bultos()) {
            envio.getBultos().add(new ShipmentPackage(b.peso(), b.largo(), b.ancho(), b.alto(), limpiar(b.descripcion())));
        }
        envio.getLineasAduana().clear();
        for (DatosEnvio.DatosLinea l : datos.lineasAduana()) {
            Article articulo = l.articuloId() == null ? null : articulos.findById(l.articuloId()).orElse(null);
            envio.getLineasAduana().add(new CustomsLineItem(limpiar(l.descripcion()), limpiar(l.codigoHs()), l.cantidad(),
                    l.valorUnitario(), l.pesoNeto(), l.pesoBruto(), limpiar(l.paisOrigen()), articulo));
        }

        // Sentido y aduana se deducen siempre de los países: no son opinables y
        // desactivar la aduana a mano dejaría un envío internacional sin declarar.
        String origen = envio.getRemitente() == null ? null : envio.getRemitente().getPais();
        String destino = envio.getDestinatario() == null ? null : envio.getDestinatario().getPais();
        envio.setSentido(sentidos.resolver(origen, destino));
        envio.setAduanero(aduanas.esAduanero(origen, destino));

        if (envio.getEstado() == ShipmentStatus.QUOTED) {
            envio.borrarOfertas();
            envio.setCosteEstimado(null);
            envio.cambiarEstado(ShipmentStatus.DRAFT, envio.getResponsable(), "Editado tras tarifar: la tarifa ya no vale");
        }
        envio.tocar();
        return envios.save(envio);
    }

    /** Lo que impide tarifar. Vacío = se puede. */
    public List<String> problemasParaTarifar(Shipment envio) {
        List<String> problemas = new ArrayList<>();
        if (envio.getRemitente() == null) problemas.add("Falta el remitente.");
        if (envio.getDestinatario() == null) problemas.add("Falta el destinatario.");
        for (Contact c : List.of(Optional.ofNullable(envio.getRemitente()), Optional.ofNullable(envio.getDestinatario())).stream()
                .flatMap(Optional::stream).toList()) {
            if (c.getTelefono() == null || c.getTelefono().isBlank()) problemas.add("El contacto " + c.getEmpresa() + " no tiene teléfono; DHL lo exige.");
        }
        if (envio.getFechaRecogida() == null) problemas.add("Falta la fecha de recogida.");
        else if (envio.getFechaRecogida().isBefore(java.time.LocalDate.now())) problemas.add("La fecha de recogida ya ha pasado.");
        if (envio.getMotivo() == null) problemas.add("Falta el motivo del envío.");
        if (envio.isPedirRecogida()
                && (envio.getHoraCierreRecogida() == null || !envio.getHoraCierreRecogida().matches("([0-1][0-9]|2[0-3]):[0-5][0-9]")))
            problemas.add("Se pide recogida y falta la hora de cierre (HH:MM).");
        if (envio.getResponsable() == null || envio.getResponsable().isBlank()) problemas.add("Falta el responsable.");
        if (envio.getBultos().isEmpty()) problemas.add("No hay bultos.");
        for (int i = 0; i < envio.getBultos().size(); i++) {
            if (!envio.getBultos().get(i).completo()) problemas.add("El bulto " + (i + 1) + " no tiene peso o medidas.");
        }
        if (envio.getCuentaTransporte() == null) problemas.add("Falta la cuenta que paga el transporte.");
        else if (!envio.getCuentaTransporte().permite(BillingRole.PAYER)) problemas.add("La cuenta " + envio.getCuentaTransporte().getNumero() + " no puede pagar transporte.");
        if (envio.getCuentaAranceles() != null && !envio.getCuentaAranceles().permite(BillingRole.DUTIES)) problemas.add("La cuenta " + envio.getCuentaAranceles().getNumero() + " no puede pagar aranceles.");
        if (envio.isAduanero()) {
            if (envio.getLineasAduana().isEmpty()) problemas.add("Es un envío con aduana y no hay líneas de mercancía.");
            for (int i = 0; i < envio.getLineasAduana().size(); i++) {
                if (!envio.getLineasAduana().get(i).completa()) problemas.add("La línea de aduana " + (i + 1) + " está incompleta (descripción, código HS, cantidad, valor y país de origen).");
            }
        }
        return problemas;
    }

    /** Lo que conviene mirar pero no impide seguir. */
    public List<String> avisos(Shipment envio) {
        List<String> avisos = new ArrayList<>();
        if (envio.getSentido() == Direction.DOMESTIC) avisos.add("Sentido: Nacional. Los dos contactos están en " + propiedades.getPaisSede() + ".");
        if (envio.getSentido() == Direction.CROSS_TRADE) avisos.add("Sentido: Entre terceros países. El envío ni sale ni entra en " + propiedades.getPaisSede() + ".");
        for (int i = 0; i < envio.getLineasAduana().size(); i++) {
            CustomsLineItem l = envio.getLineasAduana().get(i);
            if (l.getPesoNeto() != null && l.getPesoBruto() != null && l.getPesoNeto() > l.getPesoBruto())
                avisos.add("La línea " + (i + 1) + " pesa más en neto que en bruto.");
        }
        double bultos = envio.pesoBultos();
        double lineas = envio.pesoBrutoLineas();
        if (bultos > 0 && lineas > bultos + 0.001)
            avisos.add(String.format("El peso bruto de las líneas (%.2f kg) supera el de los bultos (%.2f kg).", lineas, bultos));
        return avisos;
    }

    @Transactional
    public ResultadoTarifa tarifar(Long id) {
        Shipment envio = buscar(id);
        if (!envio.editable()) throw new TransicionIlegalException("Solo se tarifa un borrador; el envío está en " + envio.getEstado());
        List<String> problemas = problemasParaTarifar(envio);
        if (!problemas.isEmpty()) return new ResultadoTarifa(envio, problemas, null);
        try {
            List<ProductOffer> ofertas = transportistas.para(envio.getTransportista()).quote(envio);
            envio.reemplazarOfertas(ofertas.stream()
                    .map(o -> new RateOffer(o.codigo(), o.nombre(), o.precio(), o.moneda(), o.entregaEstimada())).toList());
            envio.setUltimoError(null);
            envio.cambiarEstado(ShipmentStatus.QUOTED, envio.getResponsable(), ofertas.size() + " productos disponibles");
            return new ResultadoTarifa(envios.save(envio), List.of(), null);
        } catch (CarrierException e) {
            envio.setUltimoError(e.getMensajeUsuario());
            envios.save(envio);
            return new ResultadoTarifa(envio, List.of(), e.getMensajeUsuario());
        }
    }

    @Transactional
    public Shipment confirmar(Long id, String productoCodigo) {
        Shipment envio = buscar(id);
        if (envio.getEstado() != ShipmentStatus.QUOTED) throw new TransicionIlegalException("Solo se confirma un envío tarifado; está en " + envio.getEstado());
        RateOffer elegida = envio.getOfertas().stream().filter(o -> o.getProductoCodigo().equals(productoCodigo)).findFirst()
                .orElseThrow(() -> new TransicionIlegalException("El producto " + productoCodigo + " no está entre los tarifados"));
        envio.setProductoCodigo(elegida.getProductoCodigo());
        envio.setProductoNombre(elegida.getProductoNombre());
        envio.setCosteEstimado(elegida.getPrecio());
        if (elegida.getMoneda() != null) envio.setMoneda(elegida.getMoneda());
        envio.setRequestId(UUID.randomUUID().toString());
        envio.cambiarEstado(ShipmentStatus.CONFIRMED, envio.getResponsable(), "Producto " + elegida.getProductoNombre());
        return envios.save(envio);
    }

    @Transactional
    public Shipment crear(Long id) {
        Shipment envio = buscar(id);
        if (envio.getEstado() != ShipmentStatus.CONFIRMED && envio.getEstado() != ShipmentStatus.ERROR)
            throw new TransicionIlegalException("Solo se crea un envío confirmado; está en " + envio.getEstado());
        try {
            CreatedShipment creado = transportistas.para(envio.getTransportista()).createShipment(envio);
            envio.setNumeroGuia(creado.numeroGuia());
            envio.setUrlSeguimiento(creado.urlSeguimiento());
            envio.setNumeroConfirmacionRecogida(creado.numeroConfirmacionRecogida());
            envio.getDocumentos().clear();
            for (CarrierDocument d : creado.documentos()) envio.anadirDocumento(d.tipo(), d.formato(), d.contenido());
            envio.setUltimoError(null);
            envio.cambiarEstado(ShipmentStatus.CREATED, envio.getResponsable(), "Guía " + creado.numeroGuia());
            Shipment guardado = envios.save(envio);
            notifier.envioCreado(guardado);
            return guardado;
        } catch (CarrierException e) {
            envio.setUltimoError(e.getMensajeUsuario());
            envio.cambiarEstado(ShipmentStatus.ERROR, envio.getResponsable(), e.getMensajeUsuario());
            Shipment guardado = envios.save(envio);
            notifier.envioFallido(guardado, e.getMensajeUsuario());
            return guardado;
        }
    }

    @Transactional
    public Shipment cancelar(Long id) {
        Shipment envio = buscar(id);
        if (envio.getEstado() == ShipmentStatus.CREATED || envio.getEstado() == ShipmentStatus.CANCELLED)
            throw new TransicionIlegalException("Un envío en " + envio.getEstado() + " no se cancela desde aquí");
        envio.cambiarEstado(ShipmentStatus.CANCELLED, envio.getResponsable(), null);
        return envios.save(envio);
    }

    @Transactional(readOnly = true)
    public Optional<ShipmentDocument> documento(Long id, String tipo) {
        return documentos.findFirstByEnvioIdAndTipo(id, tipo);
    }

    @Transactional(readOnly = true)
    public List<String> tiposDocumento(Long id) {
        return documentos.tiposDe(id);
    }

    private Shipment buscar(Long id) {
        return envios.findById(id).orElseThrow(() -> new EnvioNoEncontradoException(id));
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
```

- [ ] **Step 5: Ejecutar el test**

Run: `mvn -q test -Dtest=ShipmentServiceTest`
Expected: PASS (14 tests).

- [ ] **Step 6: Suite y commit**

```bash
mvn -q test
git add src/main/java/com/puntotres/packinglist/shipping/service src/test/java/com/puntotres/packinglist/shipping/service
git commit -m "ShipmentService: maquina de estados con requestId fijado antes de llamar al transportista"
```

---

### Task 6: Adaptador DHL — propiedades, DTOs, mappers y traductor de errores (TDD)

**Files:**
- Create: `carrier/dhl/DhlProperties.java`, `DhlRequestMapper.java`, `DhlResponseMapper.java`, `DhlErrorTranslator.java`
- Create: `carrier/dhl/dto/*.java` (records listados abajo)
- Test: `src/test/java/com/puntotres/packinglist/carrier/dhl/DhlRequestMapperTest.java`, `DhlResponseMapperTest.java`, `DhlErrorTranslatorTest.java`
- Fixtures: `src/test/resources/ejemplos/envios/__files/dhl-rates-response.json`, `dhl-shipment-response.json`, `dhl-error-400.json` (en `__files/` porque la Task 7 los sirve con WireMock)

**Interfaces:**
- Consumes: `Shipment`, `Contact`, `BillingAccount`, `ExportReasonType`; `ProductOffer`, `CreatedShipment`, `CarrierDocument`.
- Produces:
  - `DhlProperties` (`getBaseUrl()`, `getApiKey()`, `getApiSecret()`, `getShipperAccount()`, `getPlantillaEtiqueta()`, `getHoraRecogida()` `LocalTime`, `getTimeoutSegundos()`, `getZonasHorarias()` `Map<String,String>`, `boolean configurado()`).
  - `DhlRequestMapper(DhlProperties)`: `DhlRateRequest tarifa(Shipment)`, `DhlShipmentRequest envio(Shipment)`, `String fechaPlanificada(Shipment)`.
  - `DhlResponseMapper`: `List<ProductOffer> ofertas(DhlRatesResponse)`, `CreatedShipment creado(DhlShipmentResponse)`.
  - `DhlErrorTranslator(ObjectMapper)`: `String traducir(int status, String cuerpo)`.

- [ ] **Step 1: DTOs (records, paquete `com.puntotres.packinglist.carrier.dhl.dto`)**

Nombres de campo **exactos** del OpenAPI 3.3.2; la serialización omite nulos (lo configura `DhlRestClientFactory` en la Task 7). Un fichero por record, o varios records en `DhlRateDto.java`/`DhlShipmentDto.java` como clases anidadas estáticas — a elección; los nombres de tipo son estos:

```java
// --- comunes
public record DhlAccount(String typeCode, String number) { }               // typeCode: shipper | payer | duties-taxes
public record DhlDimensions(Double length, Double width, Double height) { }

// --- POST /rates
public record DhlRateAddress(String postalCode, String cityName, String countryCode,
                             String addressLine1, String addressLine2, String addressLine3) { }
public record DhlRateCustomerDetails(DhlRateAddress shipperDetails, DhlRateAddress receiverDetails) { }
public record DhlMonetaryAmount(String typeCode, BigDecimal value, String currency) { }   // typeCode: declaredValue
public record DhlEstimatedDeliveryDate(Boolean isRequested, String typeCode) { }          // QDDC
public record DhlRatePackage(Double weight, DhlDimensions dimensions) { }
public record DhlRateRequest(DhlRateCustomerDetails customerDetails, List<DhlAccount> accounts,
                             String plannedShippingDateAndTime, String unitOfMeasurement, Boolean isCustomsDeclarable,
                             List<DhlMonetaryAmount> monetaryAmount, DhlEstimatedDeliveryDate estimatedDeliveryDate,
                             String productTypeCode, List<DhlRatePackage> packages) { }

public record DhlTotalPrice(String currencyType, String priceCurrency, BigDecimal price) { }   // currencyType: BILLC | PULCL | BASEC
public record DhlDeliveryCapabilities(String estimatedDeliveryDateAndTime) { }
public record DhlProduct(String productName, String productCode, String localProductCode,
                         List<DhlTotalPrice> totalPrice, DhlDeliveryCapabilities deliveryCapabilities) { }
public record DhlRatesResponse(List<DhlProduct> products) { }

// --- POST /shipments
public record DhlPickup(Boolean isRequested, String closeTime, String location) { }   // closeTime HH:MM; sin pickupDetails recoge en el remitente
public record DhlCustomerReference(String value, String typeCode) { }        // typeCode CU = referencia del cliente
public record DhlImageOption(String typeCode, String templateName, Boolean isRequested, String invoiceType,
                             String languageCode, Integer numberOfCopies, Boolean fitLabelsToA4) { }
public record DhlOutputImageProperties(Integer printerDPI, String encodingFormat, List<DhlImageOption> imageOptions,
                                       Boolean splitTransportAndWaybillDocLabels, Boolean allDocumentsInOneImage) { }
public record DhlPostalAddress(String postalCode, String cityName, String countryCode, String provinceCode,
                               String addressLine1, String addressLine2, String addressLine3) { }
public record DhlContactInformation(String email, String phone, String companyName, String fullName) { }
public record DhlRegistrationNumber(String typeCode, String number, String issuerCountryCode) { }
public record DhlParty(DhlPostalAddress postalAddress, DhlContactInformation contactInformation,
                       List<DhlRegistrationNumber> registrationNumbers, String typeCode) { }   // typeCode business | direct_consumer
public record DhlCustomerDetails(DhlParty shipperDetails, DhlParty receiverDetails) { }
public record DhlPackage(Double weight, DhlDimensions dimensions, List<DhlCustomerReference> customerReferences, String description) { }
public record DhlQuantity(Integer value, String unitOfMeasurement) { }         // PCS
public record DhlCommodityCode(String typeCode, String value) { }              // outbound
public record DhlLineWeight(Double netValue, Double grossValue) { }
public record DhlLineItem(Integer number, String description, BigDecimal price, DhlQuantity quantity,
                          List<DhlCommodityCode> commodityCodes, String exportReasonType, String manufacturerCountry,
                          DhlLineWeight weight) { }
public record DhlInvoice(String number, String date) { }
public record DhlExportDeclaration(List<DhlLineItem> lineItems, DhlInvoice invoice, String exportReasonType, String placeOfIncoterm) { }
public record DhlContent(List<DhlPackage> packages, Boolean isCustomsDeclarable, BigDecimal declaredValue, String declaredValueCurrency,
                         DhlExportDeclaration exportDeclaration, String description, String incoterm, String unitOfMeasurement) { }
public record DhlShipmentRequest(String plannedShippingDateAndTime, DhlPickup pickup, String productCode, List<DhlAccount> accounts,
                                 DhlOutputImageProperties outputImageProperties, List<DhlCustomerReference> customerReferences,
                                 DhlCustomerDetails customerDetails, DhlContent content) { }

public record DhlResponseDocument(String imageFormat, String content, String typeCode) { }  // content en base64
public record DhlResponsePackage(Integer referenceNumber, String trackingNumber, String trackingUrl) { }
public record DhlShipmentResponse(String shipmentTrackingNumber, String trackingUrl, String dispatchConfirmationNumber,
                                  List<DhlResponseDocument> documents, List<DhlResponsePackage> packages) { }

// --- GET /address-validate
public record DhlServiceArea(String code, String description, String GMTOffset) { }
public record DhlValidatedAddress(String countryCode, String postalCode, String cityName, String countyName, DhlServiceArea serviceArea) { }
public record DhlAddressValidateResponse(List<String> warnings, List<DhlValidatedAddress> address) { }

// --- errores (application/problem+json)
public record DhlErrorResponse(String instance, String detail, String title, String message, List<String> additionalDetails, String status) { }
```

- [ ] **Step 2: DhlProperties**

`carrier/dhl/DhlProperties.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bloque envios.dhl del yml. Las credenciales llegan de variables de entorno (ver README de la feature). */
@ConfigurationProperties(prefix = "envios.dhl")
public class DhlProperties {

    private String baseUrl = "https://express.api.dhl.com/mydhlapi/test";
    private String apiKey = "";
    private String apiSecret = "";
    private String shipperAccount = "";
    private String plantillaEtiqueta = "ECOM26_84_A4_001";
    private LocalTime horaRecogida = LocalTime.of(12, 0);
    private int timeoutSegundos = 30;
    private Map<String, String> zonasHorarias = new LinkedHashMap<>();

    /** Sin api-key, api-secret y cuenta no se puede hablar con DHL; la sección lo avisa en vez de fallar. */
    public boolean configurado() {
        return !apiKey.isBlank() && !apiSecret.isBlank() && !shipperAccount.isBlank();
    }

    public String getBaseUrl() { return baseUrl; } public void setBaseUrl(String v) { baseUrl = v; }
    public String getApiKey() { return apiKey; } public void setApiKey(String v) { apiKey = v == null ? "" : v; }
    public String getApiSecret() { return apiSecret; } public void setApiSecret(String v) { apiSecret = v == null ? "" : v; }
    public String getShipperAccount() { return shipperAccount; } public void setShipperAccount(String v) { shipperAccount = v == null ? "" : v; }
    public String getPlantillaEtiqueta() { return plantillaEtiqueta; } public void setPlantillaEtiqueta(String v) { plantillaEtiqueta = v; }
    public LocalTime getHoraRecogida() { return horaRecogida; } public void setHoraRecogida(LocalTime v) { horaRecogida = v; }
    public int getTimeoutSegundos() { return timeoutSegundos; } public void setTimeoutSegundos(int v) { timeoutSegundos = v; }
    public Map<String, String> getZonasHorarias() { return zonasHorarias; } public void setZonasHorarias(Map<String, String> v) { zonasHorarias = v; }
}
```

- [ ] **Step 3: Test del mapper de petición**

`DhlRequestMapperTest.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.carrier.dhl.dto.*;
import com.puntotres.packinglist.shipping.model.*;

class DhlRequestMapperTest {

    private DhlRequestMapper mapper;
    private Shipment envio;

    @BeforeEach
    void preparar() {
        DhlProperties props = new DhlProperties();
        props.setShipperAccount("300112049");
        props.setHoraRecogida(LocalTime.of(12, 0));
        props.setZonasHorarias(Map.of("ES", "Europe/Madrid", "MA", "Africa/Casablanca"));
        props.setPlantillaEtiqueta("ECOM26_84_A4_001");
        mapper = new DhlRequestMapper(props);

        Contact origen = new Contact("PUNTOTRES BAGS & BELTS SLU", "C/Industria 585-587", "Badalona", "ES");
        origen.setPersona("Carme Fabrega"); origen.setCodigoPostal("08918"); origen.setTelefono("+34934425135");
        origen.setEmail("export@puntotres.com"); origen.setNifVat("ESB67565291"); origen.setEori("ESB67565291");
        Contact destino = new Contact("MILITZER & MUNCH", "Zone logistique Tanger", "Tangiers", "MA");
        destino.setDireccion2("Free zone gate 2"); destino.setCodigoPostal("90000"); destino.setTelefono("+212539393394");
        destino.setTipo(ContactType.BUSINESS);

        BillingAccount propia = new BillingAccount("300112049", "Puntotres", true, EnumSet.allOf(BillingRole.class));
        BillingAccount ami = new BillingAccount("956483537", "AMI PARIS", false, EnumSet.of(BillingRole.PAYER, BillingRole.DUTIES));

        envio = new Shipment("Jordi");
        envio.setRemitente(origen); envio.setDestinatario(destino);
        envio.setFechaRecogida(LocalDate.of(2026, 9, 21));
        envio.setIncoterm(Incoterm.DAP);
        envio.setCuentaTransporte(ami); envio.setCuentaAranceles(ami);
        envio.setMotivo(new ShipmentReason("Muestras", ExportReasonType.SAMPLE));
        envio.setReferencia("MUESTRAS-H26-01");
        envio.setPedirRecogida(true); envio.setHoraCierreRecogida("18:00"); envio.setLugarRecogida("Recepción");
        envio.setAduanero(true);
        envio.setProductoCodigo("P");
        envio.setRequestId("d0e7832e-5c98-11ea-bc55-0242ac130003");
        envio.getBultos().add(new ShipmentPackage(2.5, 40.0, 30.0, 20.0, null));
        envio.getLineasAduana().add(new CustomsLineItem("Bolsos de piel", "42022100", 3, new BigDecimal("40.00"), 1.0, 1.5, "MA", null));
        envio.getLineasAduana().add(new CustomsLineItem("Cinturones", "42033000", 10, new BigDecimal("8.50"), 0.5, 0.6, "MA", null));
    }

    @Test
    void laFechaPlanificadaLlevaLaHoraDeRecogidaEnLaZonaDelOrigen() {
        // 21 de septiembre en Madrid es horario de verano: +02:00. Formato del OpenAPI: yyyy-MM-ddTHH:mm:ss GMT+hh:mm
        assertEquals("2026-09-21T12:00:00 GMT+02:00", mapper.fechaPlanificada(envio));
    }

    @Test
    void laTarifaLlevaDireccionesCuentaPropiaBultosYValorDeclarado() {
        DhlRateRequest peticion = mapper.tarifa(envio);

        assertEquals("08918", peticion.customerDetails().shipperDetails().postalCode());
        assertEquals("MA", peticion.customerDetails().receiverDetails().countryCode());
        assertEquals(List.of(new DhlAccount("shipper", "300112049")), peticion.accounts());
        assertEquals("metric", peticion.unitOfMeasurement());
        assertTrue(peticion.isCustomsDeclarable());
        assertEquals(new BigDecimal("205.00"), peticion.monetaryAmount().get(0).value());
        assertEquals("EUR", peticion.monetaryAmount().get(0).currency());
        assertEquals("all", peticion.productTypeCode());
        assertEquals(2.5, peticion.packages().get(0).weight());
        assertEquals(20.0, peticion.packages().get(0).dimensions().height());
    }

    @Test
    void elEnvioLlevaLasTresCuentasCuandoPagaUnTercero() {
        DhlShipmentRequest peticion = mapper.envio(envio);

        assertEquals(List.of(new DhlAccount("shipper", "300112049"), new DhlAccount("payer", "956483537"),
                new DhlAccount("duties-taxes", "956483537")), peticion.accounts());
        assertEquals(new DhlPickup(true, "18:00", "Recepción"), peticion.pickup());
        assertEquals("P", peticion.productCode());
    }

    @Test
    void sinPedirRecogidaElPickupVaAFalseYSinHoraNiLugar() {
        envio.setPedirRecogida(false);

        assertEquals(new DhlPickup(false, null, null), mapper.envio(envio).pickup());
    }

    @Test
    void siLaCuentaPropiaPagaElTransporteNoSeRepiteComoPayer() {
        envio.setCuentaTransporte(new BillingAccount("300112049", "Puntotres", true, EnumSet.allOf(BillingRole.class)));
        envio.setCuentaAranceles(null);

        assertEquals(List.of(new DhlAccount("shipper", "300112049")), mapper.envio(envio).accounts());
    }

    @Test
    void losContactosLlevanDireccionContactoYRegistrosFiscales() {
        DhlParty remitente = mapper.envio(envio).customerDetails().shipperDetails();

        assertEquals("Badalona", remitente.postalAddress().cityName());
        assertEquals("C/Industria 585-587", remitente.postalAddress().addressLine1());
        assertEquals("PUNTOTRES BAGS & BELTS SLU", remitente.contactInformation().companyName());
        assertEquals("Carme Fabrega", remitente.contactInformation().fullName());
        assertEquals("+34934425135", remitente.contactInformation().phone());
        assertEquals("business", remitente.typeCode());
        assertEquals(List.of(new DhlRegistrationNumber("VAT", "ESB67565291", "ES"), new DhlRegistrationNumber("EOR", "ESB67565291", "ES")),
                remitente.registrationNumbers());
    }

    @Test
    void sinPersonaElFullNameEsLaEmpresaYSinRegistrosLaListaEsNula() {
        DhlParty destinatario = mapper.envio(envio).customerDetails().receiverDetails();

        assertEquals("MILITZER & MUNCH", destinatario.contactInformation().fullName());
        assertNull(destinatario.registrationNumbers(), "una lista vacía la rechaza el esquema; nula no se serializa");
    }

    @Test
    void elContenidoLlevaLaDeclaracionDeExportacionConLineasNumeradas() {
        DhlContent contenido = mapper.envio(envio).content();

        assertTrue(contenido.isCustomsDeclarable());
        assertEquals(new BigDecimal("205.00"), contenido.declaredValue());
        assertEquals("EUR", contenido.declaredValueCurrency());
        assertEquals("DAP", contenido.incoterm());
        assertEquals("Muestras", contenido.description());
        assertEquals("MUESTRAS-H26-01", contenido.packages().get(0).customerReferences().get(0).value());

        DhlExportDeclaration declaracion = contenido.exportDeclaration();
        assertEquals("sample", declaracion.exportReasonType());
        assertEquals("Tangiers", declaracion.placeOfIncoterm());
        assertEquals("2026-09-21", declaracion.invoice().date());
        DhlLineItem primera = declaracion.lineItems().get(0);
        assertEquals(1, primera.number());
        assertEquals(new DhlQuantity(3, "PCS"), primera.quantity());
        assertEquals(List.of(new DhlCommodityCode("outbound", "42022100")), primera.commodityCodes());
        assertEquals("MA", primera.manufacturerCountry());
        assertEquals(new DhlLineWeight(1.0, 1.5), primera.weight());
        assertEquals(2, declaracion.lineItems().get(1).number());
    }

    @Test
    void sinAduanaNoHayDeclaracionNiFacturaEnLosDocumentos() {
        envio.setAduanero(false);

        DhlShipmentRequest peticion = mapper.envio(envio);

        assertNull(peticion.content().exportDeclaration());
        assertFalse(peticion.content().isCustomsDeclarable());
        assertTrue(peticion.outputImageProperties().imageOptions().stream().noneMatch(o -> o.typeCode().equals("invoice")));
    }

    @Test
    void losDocumentosPedidosSonEtiquetaA4WaybillYFacturaComercialEnPdf() {
        DhlOutputImageProperties salida = mapper.envio(envio).outputImageProperties();

        assertEquals("pdf", salida.encodingFormat());
        assertEquals(300, salida.printerDPI());
        List<String> tipos = salida.imageOptions().stream().map(DhlImageOption::typeCode).toList();
        assertEquals(List.of("label", "waybillDoc", "invoice"), tipos);
        assertEquals("ECOM26_84_A4_001", salida.imageOptions().get(0).templateName());
        assertEquals("COMMERCIAL_INVOICE_P_10", salida.imageOptions().get(2).templateName());
        assertTrue(salida.splitTransportAndWaybillDocLabels());
    }
}
```

- [ ] **Step 4: Ejecutar → no compila**

- [ ] **Step 5: DhlRequestMapper**

`carrier/dhl/DhlRequestMapper.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.puntotres.packinglist.carrier.dhl.dto.*;
import com.puntotres.packinglist.shipping.model.*;

/**
 * Del envío del núcleo al JSON de MyDHL API (OpenAPI 3.3.2). Todo lo que aquí
 * es un literal ("shipper", "outbound", "PCS", "QDDC") es un enum del contrato.
 *
 * Las listas vacías se devuelven como null: el esquema exige minItems 1 en
 * varias de ellas y una lista vacía da un 400 donde null simplemente no viaja
 * (la serialización omite nulos, ver DhlRestClientFactory).
 */
public class DhlRequestMapper {

    // El OpenAPI trae ejemplos con y sin espacio antes de GMT; la descripción
    // del campo (la fuente normativa) lo pone con espacio: 2025-01-18T17:00:00 GMT+01:00.
    // TODO DHL: confirmar contra el entorno de test que acepta el espacio.
    private static final DateTimeFormatter FECHA_DHL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss 'GMT'xxx");
    private static final DateTimeFormatter FECHA_ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final DhlProperties propiedades;

    public DhlRequestMapper(DhlProperties propiedades) { this.propiedades = propiedades; }

    public String fechaPlanificada(Shipment envio) {
        String pais = envio.getRemitente() == null ? null : envio.getRemitente().getPais();
        ZoneId zona = ZoneId.of(propiedades.getZonasHorarias().getOrDefault(pais, "Europe/Madrid"));
        ZonedDateTime momento = envio.getFechaRecogida().atTime(propiedades.getHoraRecogida()).atZone(zona);
        return FECHA_DHL.format(momento);
    }

    public DhlRateRequest tarifa(Shipment envio) {
        return new DhlRateRequest(
                new DhlRateCustomerDetails(direccionTarifa(envio.getRemitente()), direccionTarifa(envio.getDestinatario())),
                List.of(new DhlAccount("shipper", propiedades.getShipperAccount())),
                fechaPlanificada(envio),
                "metric",
                envio.isAduanero(),
                List.of(new DhlMonetaryAmount("declaredValue", envio.valorDeclarado(), envio.getMoneda())),
                new DhlEstimatedDeliveryDate(true, "QDDC"),
                "all",
                envio.getBultos().stream().map(b -> new DhlRatePackage(b.getPeso(), dimensiones(b))).toList());
    }

    public DhlShipmentRequest envio(Shipment envio) {
        return new DhlShipmentRequest(
                fechaPlanificada(envio),
                new DhlPickup(envio.isPedirRecogida(), envio.isPedirRecogida() ? envio.getHoraCierreRecogida() : null,
                        envio.isPedirRecogida() ? envio.getLugarRecogida() : null),
                envio.getProductoCodigo(),
                cuentas(envio),
                documentos(envio),
                referencias(envio),
                new DhlCustomerDetails(parte(envio.getRemitente()), parte(envio.getDestinatario())),
                contenido(envio));
    }

    private List<DhlAccount> cuentas(Shipment envio) {
        List<DhlAccount> cuentas = new ArrayList<>();
        String propia = propiedades.getShipperAccount();
        cuentas.add(new DhlAccount("shipper", propia));
        if (envio.getCuentaTransporte() != null && !envio.getCuentaTransporte().getNumero().equals(propia)) {
            cuentas.add(new DhlAccount("payer", envio.getCuentaTransporte().getNumero()));
        }
        if (envio.getCuentaAranceles() != null) {
            cuentas.add(new DhlAccount("duties-taxes", envio.getCuentaAranceles().getNumero()));
        }
        return cuentas;
    }

    private DhlOutputImageProperties documentos(Shipment envio) {
        List<DhlImageOption> opciones = new ArrayList<>();
        opciones.add(new DhlImageOption("label", propiedades.getPlantillaEtiqueta(), null, null, null, null, null));
        opciones.add(new DhlImageOption("waybillDoc", "ARCH_8x4", true, null, null, 1, null));
        if (envio.isAduanero()) {
            opciones.add(new DhlImageOption("invoice", "COMMERCIAL_INVOICE_P_10", true, "commercial", "eng", null, null));
        }
        return new DhlOutputImageProperties(300, "pdf", opciones, true, false);
    }

    private List<DhlCustomerReference> referencias(Shipment envio) {
        if (envio.getReferencia() == null) return null;
        return List.of(new DhlCustomerReference(envio.getReferencia(), "CU"));
    }

    private DhlContent contenido(Shipment envio) {
        List<DhlPackage> bultos = envio.getBultos().stream()
                .map(b -> new DhlPackage(b.getPeso(), dimensiones(b), referencias(envio), b.getDescripcion()))
                .toList();
        String descripcion = envio.getMotivo() == null ? "Shipment" : envio.getMotivo().getNombre();
        return new DhlContent(bultos, envio.isAduanero(), envio.valorDeclarado(), envio.getMoneda(),
                envio.isAduanero() ? declaracion(envio) : null, descripcion, envio.getIncoterm().name(), "metric");
    }

    private DhlExportDeclaration declaracion(Shipment envio) {
        String motivo = envio.getMotivo().getMotivoDhl().codigoDhl();
        List<DhlLineItem> lineas = new ArrayList<>();
        for (int i = 0; i < envio.getLineasAduana().size(); i++) {
            CustomsLineItem l = envio.getLineasAduana().get(i);
            lineas.add(new DhlLineItem(i + 1, l.getDescripcion(), l.getValorUnitario(),
                    new DhlQuantity(l.getCantidad(), l.getUnidad()),
                    List.of(new DhlCommodityCode("outbound", l.getCodigoHs())),
                    motivo, l.getPaisOrigen(), new DhlLineWeight(l.getPesoNeto(), l.getPesoBruto())));
        }
        // TODO DHL: si la factura comercial necesita el número de factura del ERP, añadir campo al envío.
        DhlInvoice factura = new DhlInvoice("PT-" + envio.getFechaRecogida().getYear() + "-" + envio.getId(), FECHA_ISO.format(envio.getFechaRecogida()));
        return new DhlExportDeclaration(lineas, factura, motivo, envio.getDestinatario().getCiudad());
    }

    private DhlParty parte(Contact c) {
        String persona = c.getPersona() == null || c.getPersona().isBlank() ? c.getEmpresa() : c.getPersona();
        List<DhlRegistrationNumber> registros = new ArrayList<>();
        if (c.getNifVat() != null && !c.getNifVat().isBlank()) registros.add(new DhlRegistrationNumber("VAT", c.getNifVat(), c.getPais()));
        if (c.getEori() != null && !c.getEori().isBlank()) registros.add(new DhlRegistrationNumber("EOR", c.getEori(), c.getPais()));
        // TODO DHL: con qué typeCode viaja el ICE marroquí (FTN, CIC o VAT). Hasta saberlo, VAT si no hay NIF.
        if (registros.isEmpty() && c.getIdentificadorMarroqui() != null && !c.getIdentificadorMarroqui().isBlank())
            registros.add(new DhlRegistrationNumber("VAT", c.getIdentificadorMarroqui(), c.getPais()));
        return new DhlParty(
                new DhlPostalAddress(c.getCodigoPostal(), c.getCiudad(), c.getPais(), c.getProvinciaCodigo(),
                        c.getDireccion1(), c.getDireccion2(), c.getDireccion3()),
                new DhlContactInformation(c.getEmail(), c.getTelefono(), c.getEmpresa(), persona),
                registros.isEmpty() ? null : registros,
                c.getTipo() == ContactType.PRIVATE ? "direct_consumer" : "business");
    }

    private static DhlRateAddress direccionTarifa(Contact c) {
        return new DhlRateAddress(c.getCodigoPostal(), c.getCiudad(), c.getPais(), c.getDireccion1(), c.getDireccion2(), c.getDireccion3());
    }

    private static DhlDimensions dimensiones(ShipmentPackage b) {
        return new DhlDimensions(b.getLargo(), b.getAncho(), b.getAlto());
    }
}
```

- [ ] **Step 6: Ejecutar DhlRequestMapperTest** → PASS (9 tests). Si `fechaPlanificada` da `+01:00`, la zona no se está leyendo del mapa: revisar `getOrDefault`.

- [ ] **Step 7: Fixtures JSON de respuesta**

`src/test/resources/ejemplos/envios/__files/dhl-rates-response.json` (recorte del ejemplo `nonDocInternationalShipmentRatesResponse` del OpenAPI, con precios en EUR):
```json
{
  "products": [
    {
      "productName": "EXPRESS WORLDWIDE NONDOC",
      "productCode": "P",
      "localProductCode": "P",
      "networkTypeCode": "TD",
      "weight": { "volumetric": 4.8, "provided": 2.5, "unitOfMeasurement": "metric" },
      "totalPrice": [
        { "currencyType": "BILLC", "priceCurrency": "EUR", "price": 61.2 },
        { "currencyType": "PULCL", "priceCurrency": "EUR", "price": 61.2 },
        { "currencyType": "BASEC", "priceCurrency": "EUR", "price": 61.2 }
      ],
      "deliveryCapabilities": {
        "deliveryTypeCode": "QDDC",
        "estimatedDeliveryDateAndTime": "2026-09-23T23:59:00",
        "totalTransitDays": 2
      }
    },
    {
      "productName": "EXPRESS 12:00 NONDOC",
      "productCode": "Y",
      "localProductCode": "Y",
      "totalPrice": [
        { "currencyType": "BILLC", "priceCurrency": "EUR", "price": 78.35 }
      ],
      "deliveryCapabilities": { "estimatedDeliveryDateAndTime": "2026-09-23T12:00:00" }
    }
  ]
}
```

`dhl-shipment-response.json` (estructura del ejemplo `nonDocInternationalShipmentResponse`; `content` son PDFs mínimos en base64, basta con que decodifiquen a bytes que empiecen por `%PDF`):
```json
{
  "shipmentTrackingNumber": "1103733901",
  "trackingUrl": "https://express.api.dhl.com/mydhlapi/shipments/1103733901/tracking",
  "dispatchConfirmationNumber": "PRG240918123",
  "packages": [
    { "referenceNumber": 1, "trackingNumber": "JD014600004617230770", "trackingUrl": "https://express.api.dhl.com/mydhlapi/shipments/1103733901/tracking?pieceTrackingNumber=JD014600004617230770" }
  ],
  "documents": [
    { "imageFormat": "PDF", "content": "JVBERi0xLjQKZXRpcXVldGE=", "typeCode": "label" },
    { "imageFormat": "PDF", "content": "JVBERi0xLjQKd2F5YmlsbA==", "typeCode": "waybillDoc" },
    { "imageFormat": "PDF", "content": "JVBERi0xLjQKZmFjdHVyYQ==", "typeCode": "invoice" }
  ]
}
```

`dhl-error-400.json` (forma de `supermodelIoLogisticsExpressErrorResponse`):
```json
{
  "instance": "/expressapi/shipments",
  "detail": "#/customerDetails/receiverDetails/postalAddress: required key [postalCode] not found",
  "title": "Validation error",
  "message": "Bad request",
  "additionalDetails": [
    "customerDetails.receiverDetails.postalAddress.postalCode: is missing",
    "content.packages[0].weight: must be greater than 0"
  ],
  "status": "400"
}
```

- [ ] **Step 8: Tests del mapper de respuesta y del traductor de errores**

`DhlResponseMapperTest.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.carrier.dhl.dto.DhlRatesResponse;
import com.puntotres.packinglist.carrier.dhl.dto.DhlShipmentResponse;
import com.puntotres.packinglist.shipping.carrier.CreatedShipment;
import com.puntotres.packinglist.shipping.carrier.ProductOffer;

class DhlResponseMapperTest {

    private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final DhlResponseMapper mapper = new DhlResponseMapper();

    private <T> T leer(String fichero, Class<T> tipo) throws Exception {
        return json.readValue(new ClassPathResource("ejemplos/envios/__files/" + fichero).getInputStream(), tipo);
    }

    @Test
    void lasOfertasLlevanElPrecioFacturado_BILLC_YLaEntregaEstimada() throws Exception {
        List<ProductOffer> ofertas = mapper.ofertas(leer("dhl-rates-response.json", DhlRatesResponse.class));

        assertEquals(2, ofertas.size());
        assertEquals(new ProductOffer("P", "EXPRESS WORLDWIDE NONDOC", new BigDecimal("61.2"), "EUR", LocalDateTime.of(2026, 9, 23, 23, 59)), ofertas.get(0));
        assertEquals("Y", ofertas.get(1).codigo());
    }

    @Test
    void unProductoSinPrecioOSinFechaNoRompeElMapeo() throws Exception {
        DhlRatesResponse respuesta = json.readValue("{\"products\":[{\"productName\":\"X\",\"productCode\":\"X\"}]}", DhlRatesResponse.class);

        ProductOffer oferta = mapper.ofertas(respuesta).get(0);

        assertNull(oferta.precio());
        assertNull(oferta.entregaEstimada());
    }

    @Test
    void elEnvioCreadoDecodificaLosDocumentosBase64() throws Exception {
        CreatedShipment creado = mapper.creado(leer("dhl-shipment-response.json", DhlShipmentResponse.class));

        assertEquals("1103733901", creado.numeroGuia());
        assertEquals("PRG240918123", creado.numeroConfirmacionRecogida());
        assertEquals(3, creado.documentos().size());
        assertEquals("label", creado.documentos().get(0).tipo());
        assertEquals("PDF", creado.documentos().get(0).formato());
        assertTrue(new String(creado.documentos().get(0).contenido(), StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    }
}
```

`DhlErrorTranslatorTest.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;

class DhlErrorTranslatorTest {

    private final DhlErrorTranslator traductor = new DhlErrorTranslator(new ObjectMapper());

    @Test
    void unErrorDeValidacionSeLeeEnteroConSusDetalles() throws Exception {
        String cuerpo = new String(new ClassPathResource("ejemplos/envios/__files/dhl-error-400.json").getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        String texto = traductor.traducir(400, cuerpo);

        assertTrue(texto.startsWith("DHL (400) Validation error: #/customerDetails/receiverDetails/postalAddress: required key [postalCode] not found"));
        assertTrue(texto.contains("\n- customerDetails.receiverDetails.postalAddress.postalCode: is missing"));
        assertTrue(texto.contains("\n- content.packages[0].weight: must be greater than 0"));
        assertFalse(texto.contains("/expressapi"), "la ruta interna de DHL no le dice nada al usuario");
    }

    @Test
    void unCuerpoQueNoEsJsonSeDevuelveRecortado() {
        String texto = traductor.traducir(502, "<html>Bad gateway</html>");

        assertEquals("DHL (502): <html>Bad gateway</html>", texto);
    }

    @Test
    void unCuerpoVacioSoloDiceElCodigo() {
        assertEquals("DHL (500): sin detalle", traductor.traducir(500, ""));
        assertEquals("DHL (500): sin detalle", traductor.traducir(500, null));
    }
}
```

- [ ] **Step 9: Ejecutar → no compila**

- [ ] **Step 10: DhlResponseMapper y DhlErrorTranslator**

`carrier/dhl/DhlResponseMapper.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.puntotres.packinglist.carrier.dhl.dto.*;
import com.puntotres.packinglist.shipping.carrier.CarrierDocument;
import com.puntotres.packinglist.shipping.carrier.CreatedShipment;
import com.puntotres.packinglist.shipping.carrier.ProductOffer;

/** De las respuestas de MyDHL API a los tipos del núcleo. */
public class DhlResponseMapper {

    public List<ProductOffer> ofertas(DhlRatesResponse respuesta) {
        List<ProductOffer> ofertas = new ArrayList<>();
        if (respuesta == null || respuesta.products() == null) return ofertas;
        for (DhlProduct p : respuesta.products()) {
            // BILLC es la moneda en la que DHL factura la cuenta; es el precio que se pagará.
            DhlTotalPrice facturado = p.totalPrice() == null ? null : p.totalPrice().stream()
                    .filter(t -> "BILLC".equals(t.currencyType())).findFirst()
                    .orElse(p.totalPrice().isEmpty() ? null : p.totalPrice().get(0));
            LocalDateTime entrega = p.deliveryCapabilities() == null ? null : fecha(p.deliveryCapabilities().estimatedDeliveryDateAndTime());
            ofertas.add(new ProductOffer(p.productCode(), p.productName(),
                    facturado == null ? null : facturado.price(), facturado == null ? null : facturado.priceCurrency(), entrega));
        }
        return ofertas;
    }

    public CreatedShipment creado(DhlShipmentResponse respuesta) {
        List<CarrierDocument> documentos = new ArrayList<>();
        if (respuesta.documents() != null) {
            for (DhlResponseDocument d : respuesta.documents()) {
                documentos.add(new CarrierDocument(d.typeCode(), d.imageFormat(), Base64.getDecoder().decode(d.content())));
            }
        }
        return new CreatedShipment(respuesta.shipmentTrackingNumber(), respuesta.trackingUrl(), respuesta.dispatchConfirmationNumber(), documentos);
    }

    private static LocalDateTime fecha(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDateTime.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
```

`carrier/dhl/DhlErrorTranslator.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.carrier.dhl.dto.DhlErrorResponse;

/**
 * Convierte una respuesta de error de DHL en un texto para el usuario: código,
 * título, detalle y una línea por cada additionalDetails. Sin la ruta interna
 * (instance) ni trazas.
 */
public class DhlErrorTranslator {

    private static final int MAXIMO = 1500;

    private final ObjectMapper json;

    public DhlErrorTranslator(ObjectMapper json) {
        this.json = json.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String traducir(int status, String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) return "DHL (" + status + "): sin detalle";
        try {
            DhlErrorResponse error = json.readValue(cuerpo, DhlErrorResponse.class);
            StringBuilder sb = new StringBuilder("DHL (").append(status).append(")");
            if (error.title() != null) sb.append(" ").append(error.title());
            sb.append(":");
            if (error.detail() != null) sb.append(" ").append(error.detail());
            else if (error.message() != null) sb.append(" ").append(error.message());
            if (error.additionalDetails() != null) {
                for (String detalle : error.additionalDetails()) sb.append("\n- ").append(detalle);
            }
            return recortar(sb.toString());
        } catch (Exception noEsJson) {
            return recortar("DHL (" + status + "): " + cuerpo.trim());
        }
    }

    private static String recortar(String texto) {
        return texto.length() <= MAXIMO ? texto : texto.substring(0, MAXIMO) + "…";
    }
}
```

- [ ] **Step 11: Ejecutar los tres tests y la suite** → PASS.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/carrier src/test/java/com/puntotres/packinglist/carrier src/test/resources/ejemplos/envios
git commit -m "adaptador DHL: propiedades, DTOs del OpenAPI 3.3.2, mappers y traductor de errores"
```

---

### Task 7: `DhlExpressClient` con `RestClient`, log sin credenciales y WireMock

**Files:**
- Modify: `pom.xml` (WireMock, test scope)
- Create: `carrier/dhl/DhlRestClientFactory.java`, `DhlLogInterceptor.java`, `DhlExpressClient.java`
- Test: `src/test/java/com/puntotres/packinglist/carrier/dhl/DhlExpressClientWireMockTest.java`, `DhlLogInterceptorTest.java`

**Interfaces:**
- Consumes: `CarrierClient` (Task 3), mappers y DTOs (Task 6).
- Produces: bean `DhlExpressClient implements CarrierClient` (`codigo()` = `DHL_EXPRESS`). Constructor público `DhlExpressClient(DhlProperties, RestClient.Builder, ObjectMapper)`.

- [ ] **Step 1: WireMock en el pom (test)**

```xml
        <!-- Servidor HTTP falso para probar el cliente de DHL contra respuestas
             reales del OpenAPI sin credenciales ni red. La variante standalone
             lleva sus dependencias empaquetadas y no choca con el Jetty/Tomcat
             de Boot. -->
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <version>3.13.1</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Test del interceptor de log**

`DhlLogInterceptorTest.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DhlLogInterceptorTest {

    @Test
    void losPdfEnBase64SeSustituyenPorSuTamano() {
        String cuerpo = "{\"documents\":[{\"imageFormat\":\"PDF\",\"content\":\"JVBERi0xLjQKZXRpcXVldGFKVkJFUmkweExqUUtaWFJwY1hWbGRHRT1KVkJFUmkweExqUUta\",\"typeCode\":\"label\"}]}";

        String limpio = DhlLogInterceptor.sinDocumentos(cuerpo);

        assertFalse(limpio.contains("JVBERi0"));
        assertTrue(limpio.contains("\"content\":\"<documento base64, 72 caracteres>\""));
        assertTrue(limpio.contains("\"typeCode\":\"label\""));
    }

    @Test
    void unContentCortoNoSeToca() {
        // "content" es también un campo legítimo corto en otras respuestas; solo se enmascara lo que parece un fichero.
        assertEquals("{\"content\":\"abc\"}", DhlLogInterceptor.sinDocumentos("{\"content\":\"abc\"}"));
    }
}
```

- [ ] **Step 3: Test de integración con WireMock**

`DhlExpressClientWireMockTest.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.puntotres.packinglist.shipping.carrier.*;
import com.puntotres.packinglist.shipping.model.*;

class DhlExpressClientWireMockTest {

    private static WireMockServer dhl;
    private DhlExpressClient cliente;
    private Shipment envio;

    @BeforeAll
    static void arrancar() {
        dhl = new WireMockServer(WireMockConfiguration.options().dynamicPort().usingFilesUnderClasspath("ejemplos/envios"));
        dhl.start();
    }

    @AfterAll
    static void parar() { dhl.stop(); }

    @BeforeEach
    void preparar() {
        dhl.resetAll();
        DhlProperties props = new DhlProperties();
        props.setBaseUrl(dhl.baseUrl());
        props.setApiKey("clave-api");
        props.setApiSecret("secreto");
        props.setShipperAccount("300112049");
        props.setZonasHorarias(Map.of("ES", "Europe/Madrid"));
        cliente = new DhlExpressClient(props, RestClient.builder(), new ObjectMapper());

        Contact origen = new Contact("PUNTOTRES", "C/Industria 585", "Badalona", "ES");
        origen.setCodigoPostal("08918"); origen.setTelefono("+34934425135");
        Contact destino = new Contact("TALLER", "Zone franche", "Tanger", "MA");
        destino.setCodigoPostal("90000"); destino.setTelefono("+212539393394");
        envio = new Shipment("Jordi");
        envio.setRemitente(origen); envio.setDestinatario(destino);
        envio.setFechaRecogida(LocalDate.now().plusDays(1));
        envio.setMotivo(new ShipmentReason("Muestras", ExportReasonType.SAMPLE));
        envio.setCuentaTransporte(new BillingAccount("300112049", "Puntotres", true, EnumSet.allOf(BillingRole.class)));
        envio.setProductoCodigo("P");
        envio.setHoraCierreRecogida("18:00");
        envio.setRequestId("d0e7832e-5c98-11ea-bc55-0242ac130003");
        envio.getBultos().add(new ShipmentPackage(2.5, 40.0, 30.0, 20.0, null));
        envio.getLineasAduana().add(new CustomsLineItem("Bolsos", "42022100", 3, new BigDecimal("40"), 1.0, 1.5, "MA", null));
    }

    @Test
    void tarifarLlamaAPostRatesConBasicAuthYCabecerasYDevuelveLasOfertas() {
        dhl.stubFor(post(urlEqualTo("/rates")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("dhl-rates-response.json")));

        List<ProductOffer> ofertas = cliente.quote(envio);

        assertEquals(2, ofertas.size());
        assertEquals(new BigDecimal("61.2"), ofertas.get(0).precio());
        dhl.verify(postRequestedFor(urlEqualTo("/rates"))
                .withBasicAuth(new com.github.tomakehurst.wiremock.client.BasicCredentials("clave-api", "secreto"))
                .withHeader("Message-Reference", matching("[0-9a-f-]{36}"))
                .withHeader("Message-Reference-Date", matching(".+GMT"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(matchingJsonPath("$.accounts[0].number", equalTo("300112049")))
                .withRequestBody(matchingJsonPath("$.packages[0].weight", equalTo("2.5")))
                .withRequestBody(notMatching(".*null.*")));
    }

    @Test
    void crearMandaElRequestIdComoMessageReferenceYDevuelveGuiaYDocumentos() {
        dhl.stubFor(post(urlEqualTo("/shipments")).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBodyFile("dhl-shipment-response.json")));

        CreatedShipment creado = cliente.createShipment(envio);

        assertEquals("1103733901", creado.numeroGuia());
        assertEquals(3, creado.documentos().size());
        dhl.verify(postRequestedFor(urlEqualTo("/shipments"))
                .withHeader("Message-Reference", equalTo("d0e7832e-5c98-11ea-bc55-0242ac130003"))
                .withRequestBody(matchingJsonPath("$.productCode", equalTo("P")))
                .withRequestBody(matchingJsonPath("$.pickup.isRequested", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.pickup.closeTime", equalTo("18:00")))
                .withRequestBody(matchingJsonPath("$.content.exportDeclaration.lineItems[0].commodityCodes[0].value", equalTo("42022100"))));
    }

    @Test
    void unErrorDeValidacionSeTraduceACarrierExceptionLegible() {
        dhl.stubFor(post(urlEqualTo("/shipments")).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/problem+json").withBodyFile("dhl-error-400.json")));

        CarrierException e = assertThrows(CarrierException.class, () -> cliente.createShipment(envio));

        assertTrue(e.getMensajeUsuario().startsWith("DHL (400) Validation error:"));
        assertTrue(e.getMensajeUsuario().contains("postalCode: is missing"));
    }

    @Test
    void sinRespuestaDelServidorLaExcepcionDiceQueDhlNoHaRespondido() {
        dhl.stubFor(post(urlEqualTo("/rates")).willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        CarrierException e = assertThrows(CarrierException.class, () -> cliente.quote(envio));

        assertTrue(e.getMensajeUsuario().startsWith("DHL no ha respondido"), e.getMensajeUsuario());
    }

    @Test
    void validarDireccionLlamaAGetAddressValidateConLosParametros() {
        dhl.stubFor(get(urlPathEqualTo("/address-validate")).willReturn(okJson(
                "{\"warnings\":[],\"address\":[{\"countryCode\":\"MA\",\"postalCode\":\"90000\",\"cityName\":\"TANGER\",\"serviceArea\":{\"code\":\"TNG\",\"description\":\"TANGIER-MOROCCO\"}}]}")));

        AddressCheck resultado = cliente.validateAddress(envio.getDestinatario(), TipoDireccion.ENTREGA);

        assertTrue(resultado.valida());
        assertTrue(resultado.detalle().contains("TANGIER-MOROCCO"));
        dhl.verify(getRequestedFor(urlPathEqualTo("/address-validate"))
                .withQueryParam("type", equalTo("delivery"))
                .withQueryParam("countryCode", equalTo("MA"))
                .withQueryParam("postalCode", equalTo("90000"))
                .withQueryParam("cityName", equalTo("Tanger"))
                .withQueryParam("strictValidation", equalTo("false")));
    }

    @Test
    void sinCredencialesNoLlamaYExplicaQueVariablesFaltan() {
        DhlProperties vacias = new DhlProperties();
        vacias.setBaseUrl(dhl.baseUrl());
        DhlExpressClient sinConfigurar = new DhlExpressClient(vacias, RestClient.builder(), new ObjectMapper());

        CarrierException e = assertThrows(CarrierException.class, () -> sinConfigurar.quote(envio));

        assertTrue(e.getMensajeUsuario().contains("DHL_API_KEY"));
        dhl.verify(0, postRequestedFor(urlEqualTo("/rates")));
    }

    @Test
    void trackEsDeLaFase2() {
        assertThrows(UnsupportedOperationException.class, () -> cliente.track("1103733901"));
    }
}
```

Nota: `usingFilesUnderClasspath("ejemplos/envios")` hace que `withBodyFile("x.json")` busque `ejemplos/envios/__files/x.json`, que es donde los dejó la Task 6.

- [ ] **Step 4: Ejecutar → no compila**

- [ ] **Step 5: DhlLogInterceptor**

`carrier/dhl/DhlLogInterceptor.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

/**
 * Registra cada petición y respuesta a DHL. A INFO, método, ruta, código y
 * duración; a DEBUG, los cuerpos. Nunca las cabeceras: ahí va Authorization.
 * Los PDF en base64 se sustituyen por su tamaño para no llenar el log.
 * Necesita BufferingClientHttpRequestFactory para poder leer el cuerpo de la
 * respuesta dos veces (aquí y en el cliente).
 */
public class DhlLogInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger("dhl.api");
    private static final Pattern DOCUMENTO = Pattern.compile("\"content\"\\s*:\\s*\"([A-Za-z0-9+/=]{40,})\"");

    @Override
    public ClientHttpResponse intercept(HttpRequest peticion, byte[] cuerpo, ClientHttpRequestExecution ejecucion) throws IOException {
        long inicio = System.nanoTime();
        if (log.isDebugEnabled()) {
            log.debug("→ {} {} {}", peticion.getMethod(), peticion.getURI().getPath(), sinDocumentos(new String(cuerpo, StandardCharsets.UTF_8)));
        }
        ClientHttpResponse respuesta = ejecucion.execute(peticion, cuerpo);
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        log.info("DHL {} {} → {} en {} ms", peticion.getMethod(), peticion.getURI().getPath(), respuesta.getStatusCode().value(), ms);
        if (log.isDebugEnabled()) {
            String texto = StreamUtils.copyToString(respuesta.getBody(), StandardCharsets.UTF_8);
            log.debug("← {}", sinDocumentos(texto));
        }
        return respuesta;
    }

    static String sinDocumentos(String json) {
        Matcher m = DOCUMENTO.matcher(json);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("\"content\":\"<documento base64, " + m.group(1).length() + " caracteres>\""));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
```

- [ ] **Step 6: DhlRestClientFactory y DhlExpressClient**

`carrier/dhl/DhlRestClientFactory.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Monta el RestClient de DHL: base URL, Basic Auth, timeouts, JSON sin nulos y log. */
final class DhlRestClientFactory {

    private DhlRestClientFactory() { }

    static RestClient crear(DhlProperties props, RestClient.Builder builder, ObjectMapper base) {
        // Sin nulos: el esquema de DHL usa additionalProperties: false y minItems
        // en varias listas; un null serializado da 400. Sin fallar por campos
        // desconocidos: las respuestas traen mucho más de lo que se lee.
        ObjectMapper json = base.copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(props.getTimeoutSegundos())).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(props.getTimeoutSegundos()));
        return builder.clone()
                .baseUrl(props.getBaseUrl())
                .requestFactory(new BufferingClientHttpRequestFactory(fabrica))
                .messageConverters(convertidores -> {
                    convertidores.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    convertidores.add(0, new MappingJackson2HttpMessageConverter(json));
                })
                .requestInterceptor(new DhlLogInterceptor())
                .defaultHeaders(h -> {
                    h.setBasicAuth(props.getApiKey(), props.getApiSecret());
                    h.set("Plugin-Name", "PuntotresPackingList");
                    h.set("Plugin-Version", "1.0");
                })
                .build();
    }
}
```

`carrier/dhl/DhlExpressClient.java`:
```java
package com.puntotres.packinglist.carrier.dhl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.carrier.dhl.dto.*;
import com.puntotres.packinglist.shipping.carrier.*;
import com.puntotres.packinglist.shipping.model.CarrierCode;
import com.puntotres.packinglist.shipping.model.Contact;
import com.puntotres.packinglist.shipping.model.Shipment;

/**
 * MyDHL API (OpenAPI 3.3.2): POST /rates, POST /shipments, GET /address-validate.
 * El RestClient se construye perezosamente y solo si hay credenciales: sin
 * variables de entorno la aplicación arranca igual y la sección avisa.
 */
@Component
public class DhlExpressClient implements CarrierClient {

    private static final DateTimeFormatter FECHA_CABECERA = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final DhlProperties props;
    private final RestClient.Builder builder;
    private final ObjectMapper json;
    private final DhlRequestMapper peticiones;
    private final DhlResponseMapper respuestas = new DhlResponseMapper();
    private final DhlErrorTranslator errores;
    private volatile RestClient rest;

    public DhlExpressClient(DhlProperties props, RestClient.Builder builder, ObjectMapper json) {
        this.props = props;
        this.builder = builder;
        this.json = json;
        this.peticiones = new DhlRequestMapper(props);
        this.errores = new DhlErrorTranslator(json);
    }

    @Override public CarrierCode codigo() { return CarrierCode.DHL_EXPRESS; }

    @Override
    public List<ProductOffer> getProducts(Shipment envio) {
        return quote(envio).stream().map(o -> new ProductOffer(o.codigo(), o.nombre(), null, null, o.entregaEstimada())).toList();
    }

    @Override
    public List<ProductOffer> quote(Shipment envio) {
        DhlRatesResponse respuesta = ejecutar(() -> rest().post().uri("/rates")
                .headers(h -> referencia(h, UUID.randomUUID().toString()))
                .body(peticiones.tarifa(envio))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::traducirError)
                .body(DhlRatesResponse.class));
        return respuestas.ofertas(respuesta);
    }

    @Override
    public CreatedShipment createShipment(Shipment envio) {
        DhlShipmentResponse respuesta = ejecutar(() -> rest().post().uri("/shipments")
                .headers(h -> referencia(h, envio.getRequestId()))
                .body(peticiones.envio(envio))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::traducirError)
                .body(DhlShipmentResponse.class));
        return respuestas.creado(respuesta);
    }

    @Override
    public AddressCheck validateAddress(Contact contacto, TipoDireccion tipo) {
        DhlAddressValidateResponse respuesta = ejecutar(() -> rest().get()
                .uri(b -> b.path("/address-validate")
                        .queryParam("type", tipo == TipoDireccion.RECOGIDA ? "pickup" : "delivery")
                        .queryParam("countryCode", contacto.getPais())
                        .queryParamIfPresent("postalCode", java.util.Optional.ofNullable(contacto.getCodigoPostal()))
                        .queryParamIfPresent("cityName", java.util.Optional.ofNullable(contacto.getCiudad()))
                        .queryParam("strictValidation", "false")
                        .build())
                .headers(h -> referencia(h, UUID.randomUUID().toString()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::traducirError)
                .body(DhlAddressValidateResponse.class));
        boolean valida = respuesta != null && respuesta.address() != null && !respuesta.address().isEmpty();
        StringBuilder detalle = new StringBuilder();
        if (valida) {
            DhlValidatedAddress a = respuesta.address().get(0);
            detalle.append("DHL sirve ").append(a.cityName()).append(" ").append(a.postalCode() == null ? "" : a.postalCode());
            if (a.serviceArea() != null) detalle.append(" (").append(a.serviceArea().description()).append(")");
        } else {
            detalle.append("DHL no reconoce la dirección");
        }
        if (respuesta != null && respuesta.warnings() != null) respuesta.warnings().forEach(w -> detalle.append(". ").append(w));
        return new AddressCheck(valida, detalle.toString());
    }

    @Override
    public List<TrackingEvent> track(String numeroGuia) {
        throw new UnsupportedOperationException("El seguimiento es de la fase 2");
    }

    private RestClient rest() {
        if (!props.configurado()) {
            throw new CarrierException("Faltan las credenciales de DHL: variables de entorno DHL_API_KEY, DHL_API_SECRET y DHL_SHIPPER_ACCOUNT.");
        }
        RestClient actual = rest;
        if (actual == null) {
            synchronized (this) {
                if (rest == null) rest = DhlRestClientFactory.crear(props, builder, json);
                actual = rest;
            }
        }
        return actual;
    }

    private static void referencia(org.springframework.http.HttpHeaders h, String requestId) {
        h.set("Message-Reference", requestId);
        h.set("Message-Reference-Date", FECHA_CABECERA.format(ZonedDateTime.now(ZoneOffset.UTC)));
    }

    private void traducirError(org.springframework.http.HttpRequest peticion, ClientHttpResponse respuesta) throws IOException {
        String cuerpo = StreamUtils.copyToString(respuesta.getBody(), StandardCharsets.UTF_8);
        throw new CarrierException(errores.traducir(respuesta.getStatusCode().value(), cuerpo));
    }

    private static <T> T ejecutar(java.util.function.Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (CarrierException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new CarrierException("DHL no ha respondido (" + e.getMostSpecificCause().getClass().getSimpleName() + "). Reintentar en unos minutos.", e);
        } catch (RuntimeException e) {
            throw new CarrierException("Error hablando con DHL: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 7: Ejecutar los dos tests**

Run: `mvn -q test -Dtest='DhlLogInterceptorTest,DhlExpressClientWireMockTest'`
Expected: PASS. Si `notMatching(".*null.*")` falla, la serialización no está omitiendo nulos: revisar que el `MappingJackson2HttpMessageConverter` propio se ha añadido en la posición 0.

- [ ] **Step 8: Suite completa** (el contexto de Spring debe arrancar sin variables DHL: `DhlExpressClient` es un bean, pero no construye el `RestClient` hasta la primera llamada). Run: `mvn -q test` → PASS.

- [ ] **Step 9: Commit**

```bash
git add pom.xml src/main/java/com/puntotres/packinglist/carrier src/test/java/com/puntotres/packinglist/carrier src/test/resources/ejemplos/envios
git commit -m "DhlExpressClient: RestClient con Basic Auth, log sin credenciales y tests con WireMock"
```

---

### Task 8: Mantenimientos — cuentas DHL, motivos y artículos (servicios, pantallas)

**Files:**
- Create: `shipping/service/DatoInvalidoException.java`, `BillingAccountService.java`, `ShipmentReasonService.java`, `ArticleService.java`
- Create: `carrier/dhl/CuentaPropiaInicial.java`
- Create: `shipping/web/CuentasController.java`, `MotivosController.java`, `ArticulosController.java`
- Create: `templates/cuentas.html`, `motivos.html`, `articulos.html`
- Modify: `templates/menu.html` (tarjeta nueva), `static/estilo.css`
- Test: `src/test/java/com/puntotres/packinglist/shipping/service/BillingAccountServiceTest.java`, `src/test/java/com/puntotres/packinglist/shipping/web/RutasEnviosTest.java`

**Interfaces:**
- Produces:
  - `DatoInvalidoException(String mensaje)` — la lanzan los servicios de mantenimiento ante un dato que no se puede guardar; los controladores la convierten en `mensajeError`.
  - `BillingAccountService`: `List<BillingAccount> todas()`, `List<BillingAccount> activasConRol(BillingRole)`, `BillingAccount guardar(Long id, String numero, String titular, boolean propia, Set<BillingRole> roles)`, `void activar(Long id, boolean activa)`, `BillingAccount asegurar(String numero, String titular, boolean propia, Set<BillingRole> rolesSiNueva)` (alta si no existe; si existe, añade los roles y devuelve la existente).
  - `ShipmentReasonService`: `todos()`, `activos()`, `guardar(Long id, String nombre, ExportReasonType motivoDhl)`, `activar(Long id, boolean)`.
  - `ArticleService`: `todos()`, `activos()`, `Article guardar(Long id, String referencia, String descripcion, String codigoHs, String paisOrigen, Double pesoNeto, Double pesoBruto, BigDecimal valor)`, `activar(Long id, boolean)`, `Article guardarPorReferencia(String referencia, String descripcion, String codigoHs, String paisOrigen, Double pesoNeto, Double pesoBruto, BigDecimal valor)` (alta o actualización por referencia, para el importador).

- [ ] **Step 1: Test del servicio de cuentas**

```java
package com.puntotres.packinglist.shipping.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.shipping.model.BillingAccount;
import com.puntotres.packinglist.shipping.model.BillingAccountRepository;
import com.puntotres.packinglist.shipping.model.BillingRole;

class BillingAccountServiceTest {

    private final BillingAccountRepository repo = mock(BillingAccountRepository.class);
    private final BillingAccountService servicio = new BillingAccountService(repo);

    @Test
    void elNumeroSeNormalizaYNoPuedeRepetirse() {
        when(repo.findByNumero("956483537")).thenReturn(Optional.of(new BillingAccount("956483537", "AMI", false, EnumSet.of(BillingRole.PAYER))));

        DatoInvalidoException e = assertThrows(DatoInvalidoException.class,
                () -> servicio.guardar(null, " 956 483 537 ", "AMI PARIS", false, EnumSet.of(BillingRole.PAYER)));
        assertTrue(e.getMessage().contains("956483537"));
    }

    @Test
    void unaCuentaSinRolesNoSirveParaNada() {
        assertThrows(DatoInvalidoException.class, () -> servicio.guardar(null, "123456789", "X", false, EnumSet.noneOf(BillingRole.class)));
    }

    @Test
    void asegurarDaDeAltaSiNoExisteYSiExisteSoloAnadeRoles() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByNumero("300112049")).thenReturn(Optional.empty());

        BillingAccount nueva = servicio.asegurar("300112049", "Puntotres", true, EnumSet.of(BillingRole.SHIPPER));
        assertTrue(nueva.isPropia());
        assertEquals(EnumSet.of(BillingRole.SHIPPER), nueva.getRoles());

        when(repo.findByNumero("300112049")).thenReturn(Optional.of(nueva));
        BillingAccount misma = servicio.asegurar("300112049", "Otro titular", false, EnumSet.of(BillingRole.PAYER));
        assertSame(nueva, misma);
        assertEquals("Puntotres", misma.getTitular(), "el titular no se pisa");
        assertTrue(misma.isPropia(), "propia no se pisa");
        assertEquals(EnumSet.of(BillingRole.SHIPPER, BillingRole.PAYER), misma.getRoles());
    }
}
```

- [ ] **Step 2: Ejecutar → no compila**

- [ ] **Step 3: Servicios**

`shipping/service/DatoInvalidoException.java`:
```java
package com.puntotres.packinglist.shipping.service;

/** Un dato de mantenimiento que no se puede guardar (número repetido, campo vacío). El mensaje es para el usuario. */
public class DatoInvalidoException extends RuntimeException {
    public DatoInvalidoException(String mensaje) { super(mensaje); }
}
```

`shipping/service/BillingAccountService.java`:
```java
package com.puntotres.packinglist.shipping.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.model.BillingAccount;
import com.puntotres.packinglist.shipping.model.BillingAccountRepository;
import com.puntotres.packinglist.shipping.model.BillingRole;

@Service
public class BillingAccountService {

    private final BillingAccountRepository repo;

    public BillingAccountService(BillingAccountRepository repo) { this.repo = repo; }

    public List<BillingAccount> todas() { return repo.findAllByOrderByPropiaDescTitularAsc(); }

    public List<BillingAccount> activasConRol(BillingRole rol) {
        return repo.findByActivaTrueOrderByPropiaDescTitularAsc().stream().filter(c -> c.permite(rol)).toList();
    }

    @Transactional
    public BillingAccount guardar(Long id, String numero, String titular, boolean propia, Set<BillingRole> roles) {
        String limpio = normalizar(numero);
        if (limpio.isEmpty()) throw new DatoInvalidoException("El número de cuenta no puede estar vacío.");
        if (titular == null || titular.isBlank()) throw new DatoInvalidoException("La cuenta necesita un titular.");
        if (roles == null || roles.isEmpty()) throw new DatoInvalidoException("Marca al menos un uso: remitente, transporte o aranceles.");
        Optional<BillingAccount> misma = repo.findByNumero(limpio);
        if (misma.isPresent() && !misma.get().getId().equals(id)) {
            throw new DatoInvalidoException("Ya existe la cuenta " + limpio + " (" + misma.get().getTitular() + ").");
        }
        BillingAccount cuenta = id == null ? null : repo.findById(id).orElse(null);
        if (cuenta == null) return repo.save(new BillingAccount(limpio, titular.trim(), propia, roles));
        cuenta.setNumero(limpio);
        cuenta.setTitular(titular.trim());
        cuenta.setPropia(propia);
        cuenta.setRoles(roles);
        return repo.save(cuenta);
    }

    @Transactional
    public void activar(Long id, boolean activa) {
        repo.findById(id).ifPresent(c -> { c.setActiva(activa); repo.save(c); });
    }

    /** Alta si no existe. Si existe, solo se le añaden roles: lo tecleado a mano manda sobre lo importado. */
    @Transactional
    public BillingAccount asegurar(String numero, String titular, boolean propia, Set<BillingRole> rolesSiNueva) {
        String limpio = normalizar(numero);
        Optional<BillingAccount> existente = repo.findByNumero(limpio);
        if (existente.isPresent()) {
            rolesSiNueva.forEach(existente.get()::anadirRol);
            return repo.save(existente.get());
        }
        return repo.save(new BillingAccount(limpio, titular == null || titular.isBlank() ? limpio : titular.trim(), propia, rolesSiNueva));
    }

    static String normalizar(String numero) {
        return numero == null ? "" : numero.replaceAll("\\s+", "");
    }
}
```

`shipping/service/ShipmentReasonService.java`:
```java
package com.puntotres.packinglist.shipping.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.model.ExportReasonType;
import com.puntotres.packinglist.shipping.model.ShipmentReason;
import com.puntotres.packinglist.shipping.model.ShipmentReasonRepository;

@Service
public class ShipmentReasonService {

    private final ShipmentReasonRepository repo;

    public ShipmentReasonService(ShipmentReasonRepository repo) { this.repo = repo; }

    public List<ShipmentReason> todos() { return repo.findAllByOrderByNombreAsc(); }
    public List<ShipmentReason> activos() { return repo.findByActivoTrueOrderByNombreAsc(); }

    @Transactional
    public ShipmentReason guardar(Long id, String nombre, ExportReasonType motivoDhl) {
        if (nombre == null || nombre.isBlank()) throw new DatoInvalidoException("El motivo necesita un nombre.");
        if (motivoDhl == null) throw new DatoInvalidoException("Elige a qué motivo de DHL corresponde.");
        Optional<ShipmentReason> mismo = repo.findByNombreIgnoreCase(nombre.trim());
        if (mismo.isPresent() && !mismo.get().getId().equals(id)) throw new DatoInvalidoException("Ya existe el motivo " + nombre.trim() + ".");
        ShipmentReason motivo = id == null ? null : repo.findById(id).orElse(null);
        if (motivo == null) return repo.save(new ShipmentReason(nombre.trim(), motivoDhl));
        motivo.setNombre(nombre.trim());
        motivo.setMotivoDhl(motivoDhl);
        return repo.save(motivo);
    }

    @Transactional
    public void activar(Long id, boolean activo) {
        repo.findById(id).ifPresent(m -> { m.setActivo(activo); repo.save(m); });
    }
}
```

`shipping/service/ArticleService.java`:
```java
package com.puntotres.packinglist.shipping.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.model.Article;
import com.puntotres.packinglist.shipping.model.ArticleRepository;

@Service
public class ArticleService {

    private final ArticleRepository repo;

    public ArticleService(ArticleRepository repo) { this.repo = repo; }

    public List<Article> todos() { return repo.findAllByOrderByReferenciaAsc(); }
    public List<Article> activos() { return repo.findByActivoTrueOrderByReferenciaAsc(); }
    public Optional<Article> porId(Long id) { return id == null ? Optional.empty() : repo.findById(id); }

    @Transactional
    public Article guardar(Long id, String referencia, String descripcion, String codigoHs, String paisOrigen,
                           Double pesoNeto, Double pesoBruto, BigDecimal valor) {
        validar(referencia, descripcion, codigoHs, paisOrigen);
        Optional<Article> mismo = repo.findByReferenciaIgnoreCase(referencia.trim());
        if (mismo.isPresent() && !mismo.get().getId().equals(id)) throw new DatoInvalidoException("Ya existe la referencia " + referencia.trim() + ".");
        Article articulo = id == null ? null : repo.findById(id).orElse(null);
        if (articulo == null) articulo = new Article(referencia.trim(), descripcion.trim(), codigoHs.trim(), paisOrigen.trim().toUpperCase());
        return repo.save(rellenar(articulo, referencia, descripcion, codigoHs, paisOrigen, pesoNeto, pesoBruto, valor));
    }

    /** Para el importador: la referencia manda; si ya existe, se actualiza. */
    @Transactional
    public Article guardarPorReferencia(String referencia, String descripcion, String codigoHs, String paisOrigen,
                                        Double pesoNeto, Double pesoBruto, BigDecimal valor) {
        validar(referencia, descripcion, codigoHs, paisOrigen);
        Article articulo = repo.findByReferenciaIgnoreCase(referencia.trim())
                .orElseGet(() -> new Article(referencia.trim(), descripcion.trim(), codigoHs.trim(), paisOrigen.trim().toUpperCase()));
        return repo.save(rellenar(articulo, referencia, descripcion, codigoHs, paisOrigen, pesoNeto, pesoBruto, valor));
    }

    @Transactional
    public void activar(Long id, boolean activo) {
        repo.findById(id).ifPresent(a -> { a.setActivo(activo); repo.save(a); });
    }

    private static void validar(String referencia, String descripcion, String codigoHs, String paisOrigen) {
        if (referencia == null || referencia.isBlank()) throw new DatoInvalidoException("El artículo necesita una referencia.");
        if (descripcion == null || descripcion.isBlank()) throw new DatoInvalidoException("El artículo " + referencia + " necesita descripción aduanera.");
        if (codigoHs == null || codigoHs.isBlank()) throw new DatoInvalidoException("El artículo " + referencia + " necesita código HS.");
        if (paisOrigen == null || paisOrigen.trim().length() != 2) throw new DatoInvalidoException("El país de origen de " + referencia + " es el código de dos letras (MA, ES).");
    }

    private static Article rellenar(Article a, String referencia, String descripcion, String codigoHs, String paisOrigen,
                                    Double pesoNeto, Double pesoBruto, BigDecimal valor) {
        a.setReferencia(referencia.trim());
        a.setDescripcionAduanera(descripcion.trim());
        a.setCodigoHs(codigoHs.trim());
        a.setPaisOrigen(paisOrigen.trim().toUpperCase());
        a.setPesoNetoUnitario(pesoNeto);
        a.setPesoBrutoUnitario(pesoBruto);
        a.setValorUnitario(valor);
        return a;
    }
}
```

`carrier/dhl/CuentaPropiaInicial.java` (vive en `carrier.dhl` porque es quien conoce `shipper-account`; `shipping` no puede depender de él):
```java
package com.puntotres.packinglist.carrier.dhl;

import java.util.EnumSet;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.puntotres.packinglist.shipping.model.BillingRole;
import com.puntotres.packinglist.shipping.service.BillingAccountService;

/**
 * Da de alta la cuenta de DHL_SHIPPER_ACCOUNT como cuenta propia al arrancar,
 * si no está: DHL exige siempre accounts[shipper] con ella, y sin fila en la
 * tabla no aparecería en el desplegable de "cuenta de transporte".
 */
@Component
public class CuentaPropiaInicial {

    private final DhlProperties props;
    private final BillingAccountService cuentas;

    public CuentaPropiaInicial(DhlProperties props, BillingAccountService cuentas) {
        this.props = props;
        this.cuentas = cuentas;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void asegurar() {
        if (!props.getShipperAccount().isBlank()) {
            cuentas.asegurar(props.getShipperAccount(), "Puntotres (cuenta propia DHL)", true, EnumSet.allOf(BillingRole.class));
        }
    }
}
```

- [ ] **Step 4: Ejecutar `BillingAccountServiceTest`** → PASS.

- [ ] **Step 5: Controladores de mantenimiento**

`shipping/web/CuentasController.java`:
```java
package com.puntotres.packinglist.shipping.web;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.shipping.model.BillingRole;
import com.puntotres.packinglist.shipping.service.BillingAccountService;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;

@Controller
@RequestMapping("/envios/cuentas")
public class CuentasController {

    private final BillingAccountService cuentas;

    public CuentasController(BillingAccountService cuentas) { this.cuentas = cuentas; }

    @GetMapping
    public String listado(Model model) {
        model.addAttribute("cuentas", cuentas.todas());
        model.addAttribute("roles", BillingRole.values());
        return "cuentas";
    }

    @PostMapping
    public String guardar(@RequestParam(required = false) Long id, @RequestParam String numero, @RequestParam String titular,
                          @RequestParam(defaultValue = "false") boolean propia,
                          @RequestParam(required = false) Set<BillingRole> roles, RedirectAttributes redirect) {
        try {
            cuentas.guardar(id, numero, titular, propia, roles == null ? EnumSet.noneOf(BillingRole.class) : roles);
            redirect.addFlashAttribute("mensaje", "Guardada la cuenta " + numero.trim() + ".");
        } catch (DatoInvalidoException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/envios/cuentas";
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id, @RequestParam boolean activa) {
        cuentas.activar(id, activa);
        return "redirect:/envios/cuentas";
    }
}
```

`shipping/web/MotivosController.java`:
```java
package com.puntotres.packinglist.shipping.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.shipping.model.ExportReasonType;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;
import com.puntotres.packinglist.shipping.service.ShipmentReasonService;

@Controller
@RequestMapping("/envios/motivos")
public class MotivosController {

    private final ShipmentReasonService motivos;

    public MotivosController(ShipmentReasonService motivos) { this.motivos = motivos; }

    @GetMapping
    public String listado(Model model) {
        model.addAttribute("motivos", motivos.todos());
        model.addAttribute("motivosDhl", ExportReasonType.values());
        return "motivos";
    }

    @PostMapping
    public String guardar(@RequestParam(required = false) Long id, @RequestParam String nombre,
                          @RequestParam(required = false) ExportReasonType motivoDhl, RedirectAttributes redirect) {
        try {
            motivos.guardar(id, nombre, motivoDhl);
            redirect.addFlashAttribute("mensaje", "Guardado el motivo " + nombre.trim() + ".");
        } catch (DatoInvalidoException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/envios/motivos";
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id, @RequestParam boolean activo) {
        motivos.activar(id, activo);
        return "redirect:/envios/motivos";
    }
}
```

`shipping/web/ArticulosController.java` (la importación se añade en la Task 10; aquí solo el mantenimiento):
```java
package com.puntotres.packinglist.shipping.web;

import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.shipping.service.ArticleService;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;

@Controller
@RequestMapping("/envios/articulos")
public class ArticulosController {

    private final ArticleService articulos;

    public ArticulosController(ArticleService articulos) { this.articulos = articulos; }

    @GetMapping
    public String listado(Model model) {
        model.addAttribute("articulos", articulos.todos());
        return "articulos";
    }

    @PostMapping
    public String guardar(@RequestParam(required = false) Long id, @RequestParam String referencia, @RequestParam String descripcion,
                          @RequestParam String codigoHs, @RequestParam String paisOrigen,
                          @RequestParam(required = false) Double pesoNeto, @RequestParam(required = false) Double pesoBruto,
                          @RequestParam(required = false) BigDecimal valorUnitario, RedirectAttributes redirect) {
        try {
            articulos.guardar(id, referencia, descripcion, codigoHs, paisOrigen, pesoNeto, pesoBruto, valorUnitario);
            redirect.addFlashAttribute("mensaje", "Guardado el artículo " + referencia.trim() + ".");
        } catch (DatoInvalidoException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/envios/articulos";
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id, @RequestParam boolean activo) {
        articulos.activar(id, activo);
        return "redirect:/envios/articulos";
    }
}
```

- [ ] **Step 6: Plantillas**

Las tres siguen el patrón de `taras.html`: cabecera con `fragmentos`, alertas `mensaje`/`mensajeError`, una tabla con un formulario por fila para editar y un formulario de alta debajo. Subnavegación común de la sección arriba (`<nav class="subnav">`), que en la Task 11 se saca a `fragmentos.html` como `subnavEnvios`; aquí se escribe inline.

`templates/cuentas.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragmentos :: head('Cuentas DHL')}"></head>
<body>
<header th:replace="~{fragmentos :: cabecera('Envíos · Cuentas DHL')}"></header>
<main>
    <nav class="subnav">
        <a th:href="@{/envios}">Envíos</a>
        <a th:href="@{/envios/contactos}">Libreta</a>
        <a th:href="@{/envios/cuentas}" class="activo">Cuentas DHL</a>
        <a th:href="@{/envios/motivos}">Motivos</a>
        <a th:href="@{/envios/articulos}">Artículos</a>
    </nav>
    <div class="alerta ok" th:if="${mensaje}" th:text="${mensaje}"></div>
    <div class="alerta error" th:if="${mensajeError}" th:text="${mensajeError}"></div>

    <section class="tarjeta">
        <p class="ayuda">Las cuentas con las que se paga a DHL: la nuestra y las de los clientes que pagan ellos el
            transporte o los aranceles. Los usos dicen en qué desplegable del envío aparece cada una.</p>
        <table class="tabla-mantenimiento">
            <thead><tr><th>Número</th><th>Titular</th><th>Propia</th><th>Usos</th><th></th></tr></thead>
            <tbody>
            <tr th:each="c : ${cuentas}" th:classappend="${!c.activa} ? 'inactiva'">
                <td colspan="5">
                    <form th:action="@{/envios/cuentas}" method="post" class="form-fila">
                        <input type="hidden" name="id" th:value="${c.id}">
                        <input type="text" name="numero" th:value="${c.numero}" size="12" required>
                        <input type="text" name="titular" th:value="${c.titular}" size="30" required>
                        <label><input type="checkbox" name="propia" value="true" th:checked="${c.propia}"> propia</label>
                        <span th:each="r : ${roles}">
                            <label><input type="checkbox" name="roles" th:value="${r}" th:checked="${c.roles.contains(r)}"> <span th:text="${r.etiqueta}"></span></label>
                        </span>
                        <button type="submit" class="mini">Guardar</button>
                    </form>
                    <form th:action="@{/envios/cuentas/{id}/activar(id=${c.id})}" method="post" class="form-inline">
                        <input type="hidden" name="activa" th:value="${!c.activa}">
                        <button type="submit" class="mini secundario" th:text="${c.activa} ? 'Desactivar' : 'Reactivar'"></button>
                    </form>
                </td>
            </tr>
            </tbody>
        </table>
    </section>

    <section class="tarjeta">
        <h3 class="titulo-seccion">Añadir una cuenta</h3>
        <form th:action="@{/envios/cuentas}" method="post" class="form-fila">
            <label>Número <input type="text" name="numero" required placeholder="956483537"></label>
            <label>Titular <input type="text" name="titular" required placeholder="AMI PARIS"></label>
            <label><input type="checkbox" name="propia" value="true"> propia</label>
            <span th:each="r : ${roles}"><label><input type="checkbox" name="roles" th:value="${r}"> <span th:text="${r.etiqueta}"></span></label></span>
            <button type="submit">Añadir</button>
        </form>
    </section>
</main>
</body>
</html>
```

`templates/motivos.html`: misma estructura; columnas `Nombre`, `Motivo DHL` (un `<select name="motivoDhl">` con `th:each="m : ${motivosDhl}"`, `th:value="${m}"`, `th:text="${m.codigoDhl()}"`, `th:selected="${m == motivo.motivoDhl}"`), botón Guardar y Desactivar/Reactivar (`/envios/motivos/{id}/activar`, parámetro `activo`). Alta con nombre y el mismo select. Enlace `activo` en la subnav: Motivos.

`templates/articulos.html`: columnas `Referencia`, `Descripción aduanera`, `HS`, `Origen`, `Neto`, `Bruto`, `Valor`; inputs `referencia`, `descripcion`, `codigoHs`, `paisOrigen` (size 2, `maxlength="2"`), `pesoNeto`, `pesoBruto` (`type="number" step="0.001"`), `valorUnitario` (`step="0.01"`); Desactivar/Reactivar en `/envios/articulos/{id}/activar` con `activo`. Encima de la tabla, un enlace `<a class="boton secundario" th:href="@{/envios/articulos/importar}">Importar CSV</a>` (la ruta la crea la Task 10). Enlace `activo`: Artículos.

- [ ] **Step 7: Tarjeta en el menú y CSS**

En `templates/menu.html`, después de la tarjeta de escandallos:
```html
        <section class="tarjeta menu-tarjeta">
            <h2 class="menu-titulo">
                <svg class="menu-icono" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="1.6" stroke-linejoin="round" aria-hidden="true">
                    <path d="M3 7h11v9H3zM14 10h4l3 3v3h-7z"/>
                    <circle cx="7" cy="18" r="1.8"/><circle cx="17.5" cy="18" r="1.8"/>
                </svg>
                Envíos por transportista
            </h2>
            <p class="menu-entrada">Un paquete que sale o llega por DHL Express.</p>
            <ul class="menu-lista">
                <li>Remitente y destinatario de la libreta, aduana desde el catálogo</li>
                <li>Tarifa y creación del envío en DHL, con etiqueta y factura</li>
                <li>Listado de envíos por fecha, motivo y cliente</li>
            </ul>
            <a class="boton" th:href="@{/envios}">Empezar</a>
        </section>
```

En `static/estilo.css`, al final:
```css
/* ---- Envíos por transportista ---- */
.subnav { display: flex; gap: 1rem; margin: 0 0 1rem; padding: 0.4rem 0; border-bottom: 1px solid #d9dee3; }
.subnav a { text-decoration: none; color: #2c3e50; padding: 0.2rem 0.4rem; }
.subnav a.activo { font-weight: bold; border-bottom: 2px solid #2c3e50; }
.tabla-mantenimiento tr.inactiva { color: #8a949e; }
.tabla-mantenimiento tr.inactiva input { color: #8a949e; }
.form-fila { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
.form-inline { display: inline-block; margin-left: 0.5rem; }
```

- [ ] **Step 8: Test de rutas de la sección**

`src/test/java/com/puntotres/packinglist/shipping/web/RutasEnviosTest.java` (se irá ampliando en las tareas siguientes):
```java
package com.puntotres.packinglist.shipping.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RutasEnviosTest {

    @Autowired private MockMvc mvc;

    @Test
    void elMenuEnlazaLaSeccionDeEnvios() throws Exception {
        mvc.perform(get("/menu")).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/envios\"")));
    }

    @Test
    void losMantenimientosSePintan() throws Exception {
        mvc.perform(get("/envios/cuentas")).andExpect(status().isOk()).andExpect(view().name("cuentas"));
        mvc.perform(get("/envios/motivos")).andExpect(status().isOk()).andExpect(view().name("motivos"))
                .andExpect(content().string(containsString("Muestras")));
        mvc.perform(get("/envios/articulos")).andExpect(status().isOk()).andExpect(view().name("articulos"));
    }

    @Test
    void unaCuentaSinUsosVuelveConMensajeDeError() throws Exception {
        mvc.perform(post("/envios/cuentas").param("numero", "111111111").param("titular", "X"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("mensajeError"));
    }

    @Test
    void unaCuentaCompletaSeGuardaYAparece() throws Exception {
        mvc.perform(post("/envios/cuentas").param("numero", "111222333").param("titular", "CUENTA DE PRUEBA").param("roles", "PAYER", "DUTIES"))
                .andExpect(flash().attributeExists("mensaje"));
        mvc.perform(get("/envios/cuentas")).andExpect(content().string(containsString("111222333")));
    }
}
```

- [ ] **Step 9: Suite** → PASS. `GET /envios` aún no existe (Task 11); el test del menú solo mira el `href`.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/shipping src/main/java/com/puntotres/packinglist/carrier/dhl/CuentaPropiaInicial.java src/main/resources/templates src/main/resources/static/estilo.css src/test/java/com/puntotres/packinglist/shipping
git commit -m "mantenimiento de cuentas DHL, motivos y articulos, y tarjeta de envios en el menu"
```

---

### Task 9: Libreta de contactos e importador de la libreta de MyDHL+

**Files:**
- Modify: `pom.xml` (commons-csv)
- Create: `shipping/service/DatosContacto.java`, `ContactService.java`
- Create: `shipping/importacion/LectorCsv.java`, `FilaLibreta.java`, `PrevisualizacionLibreta.java`, `ResultadoImportacion.java`, `ImportadorLibretaMyDhl.java`
- Create: `shipping/web/ImportacionEnCurso.java`, `ContactosController.java`
- Create: `templates/contactos.html`, `contacto.html`, `contactos-importar.html`
- Create: `src/test/resources/ejemplos/envios/libreta-mydhl-recorte.csv`
- Test: `src/test/java/com/puntotres/packinglist/shipping/importacion/ImportadorLibretaMyDhlTest.java`, ampliar `RutasEnviosTest`

**Interfaces:**
- Produces:
  - `record DatosContacto(String empresa, String persona, String direccion1, String direccion2, String direccion3, String codigoPostal, String ciudad, String provincia, String provinciaCodigo, String pais, String telefono, String email, ContactType tipo, boolean residencial, String nifVat, String eori, String identificadorMarroqui, String notas, Incoterm incotermPorDefecto, Long cuentaTransportePorDefectoId, Long cuentaArancelesPorDefectoId, List<String> alias)`.
  - `ContactService`: `List<Contact> activos()`, `List<Contact> buscar(String q)` (activos que contengan `q` en empresa, persona, ciudad o alias; vacío = todos), `Optional<Contact> porId(Long)`, `Contact guardar(Long id, DatosContacto)`, `void darDeBaja(Long id)`, `AddressCheck comprobarDireccion(Long id, TipoDireccion)`, `boolean existe(String empresa, String persona)`.
  - `LectorCsv.leer(byte[])` → `List<CSVRecord>` con cabecera, BOMs quitados.
  - `record FilaLibreta(int numero, DatosContacto datos, String cuentaRemitente, String cuentaTransporte, String cuentaAranceles, List<String> errores, String duplicadoDe)` con `boolean valida()` y `boolean duplicada()`.
  - `record PrevisualizacionLibreta(String nombreFichero, List<FilaLibreta> filas)` con `listas()`, `duplicadas()`, `erroneas()`.
  - `record ResultadoImportacion(int altas, int actualizadas, int omitidas, List<String> errores)`.
  - `ImportadorLibretaMyDhl(ContactService, BillingAccountService)`: `PrevisualizacionLibreta previsualizar(byte[] csv, String nombreFichero)`, `ResultadoImportacion importar(PrevisualizacionLibreta, Set<Integer> duplicadasAImportar)`.

- [ ] **Step 1: commons-csv en el pom**

```xml
        <!-- Lectura de los CSV de la libreta de MyDHL+ y del catálogo de
             artículos: campos entrecomillados con comas dentro, que un split
             a mano acabaría reproduciendo a medias. Boot no gestiona esta
             versión. -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-csv</artifactId>
            <version>1.14.0</version>
        </dependency>
```

- [ ] **Step 2: Fixture recortado y anonimizado**

`src/test/resources/ejemplos/envios/libreta-mydhl-recorte.csv`. **Debe empezar por dos BOM** (`EF BB BF EF BB BF`), como el export real; escribirlo con un editor que los respete o con `printf '\xEF\xBB\xBF\xEF\xBB\xBF' > fichero && cat cuerpo >> fichero`. Contenido (cabecera real de 44 columnas; 8 filas inventadas; ninguna es una persona real):

```
Nombre y apellidos,Empresa,Alias,Email 1,Email 2,Email 3,Email 4,Email 5,Tipo de teléfono (M/H/O),Número de teléfono Código de país,Número de teléfono,Extensión,Fax,NIF/CIF,Alias 2,Número EORI,Tipo de Impuesto CNPJ / CPF,CNPJ / CPF de identificación fiscal,IE/RG,País,Código de país,Dirección 1,Dirección 2,Dirección 3,CP Código Postal,Ciudad,Suburbio,Estado / Provincia,Estado / Provincia Código,Por Defecto Remitente Cuenta,Por Defecto Enviar Cargos (Fracturar a),Por Defecto Arancel/Impuestos (Fracturar a),Por Defecto Arancel (Fracturar a),Por Defecto Impuesto (Fracturar a),Términos aduaneros,Notas,Referencia 1,Referencia 2,Referencia 3,Referencia 4,Parte adicional Rol,Entregas residenciales,Tipo de impuesto,Número de identificación fiscal
ANA PEREZ,TALLER EJEMPLO SARL,ANA PEREZ at ,ana@ejemplo.ma,,,,,OFFICE,212,0539000000,,,,,,,,,,MA,"Zone franche, lot 12",,,90000,TANGER,,,,,,,,,,,,,,,BU,N,,
CARME FABREGA,PUNTOTRES BAGS & BELTS SLU,Lis Pamias at Puntotres,export@puntotres.com,,,,,OFFICE,34,934425135,,,B67565291,,,,,,,ES,C/Industria 585-587,,,08918,Badalona,,Barcelona,Ba,300112049,,,,,,,,,,,BU,N,,
JEAN CLIENT,MAISON CLIENTE,JEAN CLIENT at MAISON CLIENTE,jean@maison.fr,compta@maison.fr,,,,MOBILE,33,0612345678,12,,FR12345678901,,FR12345678901000,,,,,FR,2 Rue Exemple,Bâtiment B,,75002,Paris,,Ile-de-France,,,956483537,956483537,,,DAP,Entregar en recepción,,,,,BU,N,,
JEAN CLIENT,MAISON CLIENTE, at MAISON CLIENTE,jean@maison.fr,,,,,OFFICE,33,0144000000,,,,,,,,,,FR,2 Rue Exemple,,,75002,Paris,,,,,,,,,,,,,,,BU,N,,
MARIO ROSSI,PELLI ESEMPIO SRL,MARIO ROSSI at PELLI ESEMPIO SRL,mario@pelli.it,,,,,OFFICE,39,0571000000,,,,,,,,,,IT,"Via Esempio, 7",,,56029,SANTA CROCE SULL ARNO,,PI,,300112049,300112049,,,,,,,,,,PR,N,,
SIN PAIS,EMPRESA ROTA,SIN PAIS,,,,,,OFFICE,34,600000000,,,,,,,,,,,Calle Falsa 1,,,,Madrid,,,,,,,,,,,,,,,BU,N,,
SIN DIRECCION,EMPRESA SIN CALLE,X at ,,,,,,OFFICE,34,600000001,,,,,,,,,,ES,,,,,Madrid,,,,,,,,,,,,,,,BU,Y,,
LUCIA PARTICULAR,LUCIA PARTICULAR,LUCIA PARTICULAR,lucia@correo.es,,,,,MOBILE,34,611111111,,,,,,,,,,ES,"Av. Ejemplo 10, 3º 2ª",,,08005,BARCELONA,,,,,,,,,,,,,,,PR,Y,,
```

Lo que reproduce cada fila: (1) Marruecos con coma dentro de la dirección y alias basura `X at `; (2) la propia Puntotres con NIF y cuenta remitente; (3) cliente francés con Email 2, extensión, NIF, EORI, cuenta de cargos y aranceles, incoterm y notas; (4) **duplicado** de la 3 (misma empresa y persona) con alias ` at MAISON CLIENTE`; (5) italiano con cuenta propia 300112049 como remitente y cargos, tipo PR; (6) **error**: sin país; (7) **error**: sin dirección 1; (8) particular con residencial Y.

- [ ] **Step 3: Test del importador**

```java
package com.puntotres.packinglist.shipping.importacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.puntotres.packinglist.shipping.model.*;
import com.puntotres.packinglist.shipping.service.BillingAccountService;
import com.puntotres.packinglist.shipping.service.ContactService;
import com.puntotres.packinglist.shipping.service.DatosContacto;

class ImportadorLibretaMyDhlTest {

    private ContactService contactos;
    private BillingAccountService cuentas;
    private ImportadorLibretaMyDhl importador;
    private byte[] csv;

    @BeforeEach
    void preparar() throws Exception {
        contactos = mock(ContactService.class);
        cuentas = mock(BillingAccountService.class);
        when(cuentas.asegurar(anyString(), anyString(), anyBoolean(), any()))
                .thenAnswer(inv -> new BillingAccount(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
        when(contactos.guardar(isNull(), any())).thenAnswer(inv -> new Contact("x", "x", "x", "ES"));
        importador = new ImportadorLibretaMyDhl(contactos, cuentas);
        csv = new ClassPathResource("ejemplos/envios/libreta-mydhl-recorte.csv").getInputStream().readAllBytes();
    }

    @Test
    void leeLas44ColumnasConElDobleBomYClasificaLasFilas() {
        PrevisualizacionLibreta prev = importador.previsualizar(csv, "libreta.csv");

        assertEquals(8, prev.filas().size());
        assertEquals(5, prev.listas().size());
        assertEquals(1, prev.duplicadas().size());
        assertEquals(2, prev.erroneas().size());
    }

    @Test
    void elMapeoDeColumnasEsElDeLaLibretaDeMyDhl() {
        FilaLibreta jean = importador.previsualizar(csv, "l.csv").filas().get(2);
        DatosContacto d = jean.datos();

        assertEquals("MAISON CLIENTE", d.empresa());
        assertEquals("JEAN CLIENT", d.persona());
        assertEquals("+33612345678", d.telefono(), "prefijo + número sin el cero inicial");
        assertEquals("jean@maison.fr", d.email());
        assertEquals("FR12345678901", d.nifVat());
        assertEquals("FR12345678901000", d.eori());
        assertEquals("FR", d.pais());
        assertEquals("2 Rue Exemple", d.direccion1());
        assertEquals("Bâtiment B", d.direccion2());
        assertEquals("75002", d.codigoPostal());
        assertEquals("Paris", d.ciudad());
        assertEquals("Ile-de-France", d.provincia());
        assertEquals(ContactType.BUSINESS, d.tipo());
        assertFalse(d.residencial());
        assertEquals(Incoterm.DAP, d.incotermPorDefecto());
        assertTrue(d.notas().contains("Entregar en recepción"));
        assertTrue(d.notas().contains("compta@maison.fr"), "Email 2 va a notas");
        assertTrue(d.notas().contains("ext. 12"));
        assertEquals(List.of("JEAN CLIENT at MAISON CLIENTE"), d.alias());
        assertEquals("956483537", jean.cuentaTransporte());
        assertEquals("956483537", jean.cuentaAranceles());
        assertNull(jean.cuentaRemitente());
    }

    @Test
    void losAliasVaciosOQueSoloDicenAtSeDescartanYElParticularEsPrivate() {
        List<FilaLibreta> filas = importador.previsualizar(csv, "l.csv").filas();

        assertEquals(List.of(), filas.get(0).datos().alias(), "'ANA PEREZ at ' es la persona más basura");
        assertEquals(List.of(), filas.get(3).datos().alias(), "' at MAISON CLIENTE' es la empresa más basura");
        assertEquals(ContactType.PRIVATE, filas.get(7).datos().tipo());
        assertTrue(filas.get(7).datos().residencial());
        assertEquals("Av. Ejemplo 10, 3º 2ª", filas.get(7).datos().direccion1());
    }

    @Test
    void losErroresDicenQueFalta() {
        List<FilaLibreta> erroneas = importador.previsualizar(csv, "l.csv").erroneas();

        assertTrue(erroneas.get(0).errores().get(0).contains("país"));
        assertTrue(erroneas.get(1).errores().get(0).contains("dirección"));
    }

    @Test
    void unDuplicadoDentroDelCsvSeSenalaConLaFilaOriginal() {
        FilaLibreta dup = importador.previsualizar(csv, "l.csv").duplicadas().get(0);

        assertEquals(4, dup.numero());
        assertEquals("fila 3", dup.duplicadoDe());
    }

    @Test
    void unContactoQueYaEstaEnLaLibretaTambienEsDuplicado() {
        when(contactos.existe("TALLER EJEMPLO SARL", "ANA PEREZ")).thenReturn(true);

        PrevisualizacionLibreta prev = importador.previsualizar(csv, "l.csv");

        assertEquals(2, prev.duplicadas().size());
        assertEquals("ya en la libreta", prev.duplicadas().get(0).duplicadoDe());
    }

    @Test
    void importarDaDeAltaLasListasYLosDuplicadosMarcadosYCreaLasCuentas() {
        PrevisualizacionLibreta prev = importador.previsualizar(csv, "l.csv");

        ResultadoImportacion resultado = importador.importar(prev, Set.of(4));

        assertEquals(6, resultado.altas());
        assertEquals(0, resultado.omitidas());
        assertEquals(2, resultado.errores().size());
        verify(contactos, times(6)).guardar(isNull(), any());
        verify(cuentas).asegurar("956483537", "MAISON CLIENTE", false, EnumSet.of(BillingRole.PAYER, BillingRole.DUTIES));
        verify(cuentas).asegurar("300112049", "PUNTOTRES BAGS & BELTS SLU", false, EnumSet.of(BillingRole.SHIPPER));
        verify(cuentas).asegurar("300112049", "PELLI ESEMPIO SRL", false, EnumSet.of(BillingRole.SHIPPER, BillingRole.PAYER));
    }

    @Test
    void losDuplicadosNoMarcadosSeOmiten() {
        ResultadoImportacion resultado = importador.importar(importador.previsualizar(csv, "l.csv"), Set.of());

        assertEquals(5, resultado.altas());
        assertEquals(1, resultado.omitidas());
    }
}
```

- [ ] **Step 4: Ejecutar → no compila**

- [ ] **Step 5: DatosContacto y ContactService**

`shipping/service/DatosContacto.java`:
```java
package com.puntotres.packinglist.shipping.service;

import java.util.List;

import com.puntotres.packinglist.shipping.model.ContactType;
import com.puntotres.packinglist.shipping.model.Incoterm;

/** Lo que se teclea o se importa de un contacto. Ids para las cuentas por defecto. */
public record DatosContacto(String empresa, String persona, String direccion1, String direccion2, String direccion3,
                            String codigoPostal, String ciudad, String provincia, String provinciaCodigo, String pais,
                            String telefono, String email, ContactType tipo, boolean residencial,
                            String nifVat, String eori, String identificadorMarroqui, String notas,
                            Incoterm incotermPorDefecto, Long cuentaTransportePorDefectoId, Long cuentaArancelesPorDefectoId,
                            List<String> alias) {
    public DatosContacto {
        alias = alias == null ? List.of() : List.copyOf(alias);
        tipo = tipo == null ? ContactType.BUSINESS : tipo;
    }
}
```

`shipping/service/ContactService.java`:
```java
package com.puntotres.packinglist.shipping.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.carrier.AddressCheck;
import com.puntotres.packinglist.shipping.carrier.CarrierClientRegistry;
import com.puntotres.packinglist.shipping.carrier.TipoDireccion;
import com.puntotres.packinglist.shipping.model.*;

@Service
public class ContactService {

    private final ContactRepository repo;
    private final BillingAccountRepository cuentas;
    private final CarrierClientRegistry transportistas;

    public ContactService(ContactRepository repo, BillingAccountRepository cuentas, CarrierClientRegistry transportistas) {
        this.repo = repo; this.cuentas = cuentas; this.transportistas = transportistas;
    }

    public List<Contact> activos() { return repo.findByActivoTrueOrderByEmpresaAscPersonaAsc(); }

    /** Búsqueda en memoria: la libreta tiene unos cientos de contactos y así entra también por alias. */
    public List<Contact> buscar(String q) {
        if (q == null || q.isBlank()) return activos();
        String aguja = q.trim().toLowerCase(Locale.ROOT);
        return activos().stream().filter(c -> contiene(c.getEmpresa(), aguja) || contiene(c.getPersona(), aguja)
                || contiene(c.getCiudad(), aguja) || c.getAlias().stream().anyMatch(a -> contiene(a, aguja))).toList();
    }

    public Optional<Contact> porId(Long id) { return id == null ? Optional.empty() : repo.findById(id); }

    public boolean existe(String empresa, String persona) {
        return !repo.findByEmpresaIgnoreCaseAndPersonaIgnoreCase(empresa == null ? "" : empresa.trim(), persona == null ? "" : persona.trim()).isEmpty();
    }

    @Transactional
    public Contact guardar(Long id, DatosContacto d) {
        String empresa = primeroNoVacio(d.empresa(), d.persona());
        if (empresa == null) throw new DatoInvalidoException("El contacto necesita empresa o persona.");
        if (vacio(d.direccion1())) throw new DatoInvalidoException("El contacto " + empresa + " necesita dirección.");
        if (vacio(d.ciudad())) throw new DatoInvalidoException("El contacto " + empresa + " necesita ciudad.");
        if (d.pais() == null || d.pais().trim().length() != 2) throw new DatoInvalidoException("El país de " + empresa + " es el código de dos letras (ES, MA, FR).");

        Contact c = id == null ? null : repo.findById(id).orElse(null);
        if (c == null) c = new Contact(empresa, d.direccion1().trim(), d.ciudad().trim(), d.pais().trim().toUpperCase());
        c.setEmpresa(empresa);
        c.setPersona(limpiar(d.persona()));
        c.setDireccion1(d.direccion1().trim());
        c.setDireccion2(limpiar(d.direccion2()));
        c.setDireccion3(limpiar(d.direccion3()));
        c.setCodigoPostal(limpiar(d.codigoPostal()));
        c.setCiudad(d.ciudad().trim());
        c.setProvincia(limpiar(d.provincia()));
        c.setProvinciaCodigo(limpiar(d.provinciaCodigo()));
        c.setPais(d.pais().trim());
        c.setTelefono(limpiar(d.telefono()));
        c.setEmail(limpiar(d.email()));
        c.setTipo(d.tipo());
        c.setResidencial(d.residencial());
        c.setNifVat(limpiar(d.nifVat()));
        c.setEori(limpiar(d.eori()));
        c.setIdentificadorMarroqui(limpiar(d.identificadorMarroqui()));
        c.setNotas(limpiar(d.notas()));
        c.setIncotermPorDefecto(d.incotermPorDefecto());
        c.setCuentaTransportePorDefecto(d.cuentaTransportePorDefectoId() == null ? null : cuentas.findById(d.cuentaTransportePorDefectoId()).orElse(null));
        c.setCuentaArancelesPorDefecto(d.cuentaArancelesPorDefectoId() == null ? null : cuentas.findById(d.cuentaArancelesPorDefectoId()).orElse(null));
        c.setAlias(d.alias().stream().map(String::trim).filter(a -> !a.isEmpty()).toList());
        c.tocar();
        return repo.save(c);
    }

    @Transactional
    public void darDeBaja(Long id) {
        repo.findById(id).ifPresent(c -> { c.setActivo(false); c.tocar(); repo.save(c); });
    }

    public AddressCheck comprobarDireccion(Long id, TipoDireccion tipo) {
        Contact c = repo.findById(id).orElseThrow(() -> new DatoInvalidoException("No existe el contacto " + id));
        return transportistas.para(CarrierCode.DHL_EXPRESS).validateAddress(c, tipo);
    }

    private static boolean contiene(String texto, String aguja) {
        return texto != null && texto.toLowerCase(Locale.ROOT).contains(aguja);
    }
    private static boolean vacio(String s) { return s == null || s.isBlank(); }
    private static String limpiar(String s) { return vacio(s) ? null : s.trim(); }
    private static String primeroNoVacio(String a, String b) { return !vacio(a) ? a.trim() : !vacio(b) ? b.trim() : null; }
}
```

- [ ] **Step 6: Importador**

`shipping/importacion/LectorCsv.java`:
```java
package com.puntotres.packinglist.shipping.importacion;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** CSV con cabecera, UTF-8, coma y comillas. Quita todos los BOM del principio (MyDHL+ exporta dos). */
public final class LectorCsv {

    private LectorCsv() { }

    public static List<CSVRecord> leer(byte[] bytes) {
        String texto = new String(bytes, StandardCharsets.UTF_8);
        while (texto.startsWith("﻿")) texto = texto.substring(1);
        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true).setAllowMissingColumnNames(true).build();
        try (CSVParser parser = CSVParser.parse(new StringReader(texto), formato)) {
            return parser.getRecords();
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("El fichero no se puede leer como CSV: " + e.getMessage(), e);
        }
    }

    /** El valor de una columna, o null si la columna no existe o está vacía. */
    public static String columna(CSVRecord fila, String nombre) {
        if (!fila.isMapped(nombre) || !fila.isSet(nombre)) return null;
        String v = fila.get(nombre);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
```

`shipping/importacion/ResultadoImportacion.java`:
```java
package com.puntotres.packinglist.shipping.importacion;

import java.util.List;

public record ResultadoImportacion(int altas, int actualizadas, int omitidas, List<String> errores) { }
```

`shipping/importacion/FilaLibreta.java`:
```java
package com.puntotres.packinglist.shipping.importacion;

import java.util.List;

import com.puntotres.packinglist.shipping.service.DatosContacto;

/** Una fila del CSV ya interpretada. numero es la fila de datos (1 = primera tras la cabecera). */
public record FilaLibreta(int numero, DatosContacto datos, String cuentaRemitente, String cuentaTransporte, String cuentaAranceles,
                          List<String> errores, String duplicadoDe) {
    public boolean valida() { return errores.isEmpty(); }
    public boolean duplicada() { return duplicadoDe != null; }
    public boolean lista() { return valida() && !duplicada(); }
}
```

`shipping/importacion/PrevisualizacionLibreta.java`:
```java
package com.puntotres.packinglist.shipping.importacion;

import java.util.List;

public record PrevisualizacionLibreta(String nombreFichero, List<FilaLibreta> filas) {
    public List<FilaLibreta> listas() { return filas.stream().filter(FilaLibreta::lista).toList(); }
    public List<FilaLibreta> duplicadas() { return filas.stream().filter(f -> f.valida() && f.duplicada()).toList(); }
    public List<FilaLibreta> erroneas() { return filas.stream().filter(f -> !f.valida()).toList(); }
}
```

`shipping/importacion/ImportadorLibretaMyDhl.java`:
```java
package com.puntotres.packinglist.shipping.importacion;

import static com.puntotres.packinglist.shipping.importacion.LectorCsv.columna;

import java.util.*;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.model.BillingAccount;
import com.puntotres.packinglist.shipping.model.BillingRole;
import com.puntotres.packinglist.shipping.model.ContactType;
import com.puntotres.packinglist.shipping.model.Incoterm;
import com.puntotres.packinglist.shipping.service.BillingAccountService;
import com.puntotres.packinglist.shipping.service.ContactService;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;
import com.puntotres.packinglist.shipping.service.DatosContacto;

/**
 * Importa el export CSV de la libreta de direcciones de MyDHL+ (44 columnas,
 * cabeceras en español, UTF-8 con dos BOM). El mapeo de columnas está fijado
 * con el export real de septiembre de 2026; ver la spec, sección 9.
 *
 * Las columnas "Por Defecto ..." traen números de cuenta DHL de terceros:
 * se dan de alta como BillingAccount y quedan como cuentas por defecto del
 * contacto, para que al elegirlo en un envío ya salga la cuenta que paga.
 */
@Service
public class ImportadorLibretaMyDhl {

    static final String NOMBRE = "Nombre y apellidos", EMPRESA = "Empresa", ALIAS = "Alias", ALIAS2 = "Alias 2",
            EMAIL1 = "Email 1", EMAIL2 = "Email 2", PREFIJO = "Número de teléfono Código de país", TELEFONO = "Número de teléfono",
            EXTENSION = "Extensión", NIF = "NIF/CIF", EORI = "Número EORI", PAIS = "Código de país",
            DIR1 = "Dirección 1", DIR2 = "Dirección 2", DIR3 = "Dirección 3", CP = "CP Código Postal", CIUDAD = "Ciudad",
            PROVINCIA = "Estado / Provincia", PROVINCIA_COD = "Estado / Provincia Código",
            CTA_REMITENTE = "Por Defecto Remitente Cuenta", CTA_CARGOS = "Por Defecto Enviar Cargos (Fracturar a)",
            CTA_ARANCELES_IMP = "Por Defecto Arancel/Impuestos (Fracturar a)", CTA_ARANCELES = "Por Defecto Arancel (Fracturar a)",
            CTA_IMPUESTOS = "Por Defecto Impuesto (Fracturar a)", INCOTERM = "Términos aduaneros", NOTAS = "Notas",
            ROL = "Parte adicional Rol", RESIDENCIAL = "Entregas residenciales";

    private final ContactService contactos;
    private final BillingAccountService cuentas;

    public ImportadorLibretaMyDhl(ContactService contactos, BillingAccountService cuentas) {
        this.contactos = contactos;
        this.cuentas = cuentas;
    }

    public PrevisualizacionLibreta previsualizar(byte[] csv, String nombreFichero) {
        List<CSVRecord> registros = LectorCsv.leer(csv);
        if (registros.isEmpty() || !registros.get(0).isMapped(EMPRESA) || !registros.get(0).isMapped(PAIS)) {
            throw new DatoInvalidoException("Esto no parece la libreta de MyDHL+: faltan las columnas '" + EMPRESA + "' y '" + PAIS + "'.");
        }
        List<FilaLibreta> filas = new ArrayList<>();
        Map<String, Integer> vistas = new HashMap<>();
        int numero = 0;
        for (CSVRecord r : registros) {
            numero++;
            DatosContacto datos = datos(r);
            List<String> errores = errores(datos);
            String duplicadoDe = null;
            if (errores.isEmpty()) {
                String clave = (datos.empresa() + "|" + (datos.persona() == null ? "" : datos.persona())).toUpperCase(Locale.ROOT);
                Integer anterior = vistas.putIfAbsent(clave, numero);
                if (anterior != null) duplicadoDe = "fila " + anterior;
                else if (contactos.existe(datos.empresa(), datos.persona())) duplicadoDe = "ya en la libreta";
            }
            filas.add(new FilaLibreta(numero, datos, columna(r, CTA_REMITENTE), columna(r, CTA_CARGOS),
                    primero(columna(r, CTA_ARANCELES_IMP), columna(r, CTA_ARANCELES), columna(r, CTA_IMPUESTOS)),
                    errores, duplicadoDe));
        }
        return new PrevisualizacionLibreta(nombreFichero, filas);
    }

    @Transactional
    public ResultadoImportacion importar(PrevisualizacionLibreta prev, Set<Integer> duplicadasAImportar) {
        int altas = 0, omitidas = 0;
        List<String> errores = new ArrayList<>();
        for (FilaLibreta fila : prev.filas()) {
            if (!fila.valida()) {
                errores.add("Fila " + fila.numero() + ": " + String.join(" ", fila.errores()));
                continue;
            }
            if (fila.duplicada() && !duplicadasAImportar.contains(fila.numero())) {
                omitidas++;
                continue;
            }
            try {
                DatosContacto d = fila.datos();
                Long transporte = cuenta(fila.cuentaTransporte(), d.empresa(), BillingRole.PAYER,
                        cuentaSi(fila.cuentaRemitente(), fila.cuentaTransporte(), BillingRole.SHIPPER),
                        cuentaSi(fila.cuentaAranceles(), fila.cuentaTransporte(), BillingRole.DUTIES));
                Long aranceles = fila.cuentaAranceles() == null || fila.cuentaAranceles().equals(fila.cuentaTransporte()) ? transporte
                        : cuenta(fila.cuentaAranceles(), d.empresa(), BillingRole.DUTIES,
                        cuentaSi(fila.cuentaRemitente(), fila.cuentaAranceles(), BillingRole.SHIPPER));
                if (fila.cuentaRemitente() != null && !fila.cuentaRemitente().equals(fila.cuentaTransporte())
                        && !fila.cuentaRemitente().equals(fila.cuentaAranceles())) {
                    cuenta(fila.cuentaRemitente(), d.empresa(), BillingRole.SHIPPER);
                }
                if (fila.cuentaAranceles() == null) aranceles = null;
                contactos.guardar(null, new DatosContacto(d.empresa(), d.persona(), d.direccion1(), d.direccion2(), d.direccion3(),
                        d.codigoPostal(), d.ciudad(), d.provincia(), d.provinciaCodigo(), d.pais(), d.telefono(), d.email(),
                        d.tipo(), d.residencial(), d.nifVat(), d.eori(), d.identificadorMarroqui(), d.notas(),
                        d.incotermPorDefecto(), transporte, aranceles, d.alias()));
                altas++;
            } catch (DatoInvalidoException e) {
                errores.add("Fila " + fila.numero() + ": " + e.getMessage());
            }
        }
        return new ResultadoImportacion(altas, 0, omitidas, errores);
    }

    /** Alta o roles añadidos de una cuenta; null si no hay número. Devuelve el id. */
    private Long cuenta(String numero, String titular, BillingRole rol, BillingRole... extra) {
        if (numero == null) return null;
        EnumSet<BillingRole> roles = EnumSet.of(rol);
        for (BillingRole r : extra) if (r != null) roles.add(r);
        BillingAccount c = cuentas.asegurar(numero, titular, false, roles);
        return c.getId();
    }

    private static BillingRole cuentaSi(String candidata, String misma, BillingRole rol) {
        return candidata != null && candidata.equals(misma) ? rol : null;
    }

    private static DatosContacto datos(CSVRecord r) {
        String persona = columna(r, NOMBRE);
        String empresa = columna(r, EMPRESA);
        if (empresa == null) empresa = persona;
        List<String> alias = new ArrayList<>();
        for (String a : new String[] {columna(r, ALIAS), columna(r, ALIAS2)}) {
            String limpio = aliasUtil(a, persona, empresa);
            if (limpio != null) alias.add(limpio);
        }
        StringBuilder notas = new StringBuilder();
        if (columna(r, NOTAS) != null) notas.append(columna(r, NOTAS));
        if (columna(r, EMAIL2) != null) notas.append(notas.isEmpty() ? "" : ". ").append("Otro email: ").append(columna(r, EMAIL2));
        if (columna(r, EXTENSION) != null) notas.append(notas.isEmpty() ? "" : ". ").append("Tel. ext. ").append(columna(r, EXTENSION));
        Incoterm incoterm = null;
        try { if (columna(r, INCOTERM) != null) incoterm = Incoterm.valueOf(columna(r, INCOTERM).toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignorado) { }
        return new DatosContacto(empresa, persona, columna(r, DIR1), columna(r, DIR2), columna(r, DIR3), columna(r, CP), columna(r, CIUDAD),
                columna(r, PROVINCIA), columna(r, PROVINCIA_COD), columna(r, PAIS) == null ? null : columna(r, PAIS).toUpperCase(Locale.ROOT),
                telefono(columna(r, PREFIJO), columna(r, TELEFONO)), columna(r, EMAIL1),
                "PR".equalsIgnoreCase(columna(r, ROL)) ? ContactType.PRIVATE : ContactType.BUSINESS,
                "Y".equalsIgnoreCase(columna(r, RESIDENCIAL)),
                columna(r, NIF), columna(r, EORI), null, notas.isEmpty() ? null : notas.toString(), incoterm, null, null, alias);
    }

    /** +prefijo y número sin espacios ni el cero nacional inicial (0612345678 → +33612345678). */
    static String telefono(String prefijo, String numero) {
        if (numero == null) return null;
        String digitos = numero.replaceAll("[^0-9]", "");
        if (prefijo == null || prefijo.isBlank()) return digitos;
        digitos = digitos.replaceFirst("^0+", "");
        return "+" + prefijo.replaceAll("[^0-9]", "") + digitos;
    }

    /** MyDHL+ compone "Persona at Empresa" y deja restos como "X at " o " at Y"; se descarta lo que no añade nada. */
    static String aliasUtil(String alias, String persona, String empresa) {
        if (alias == null) return null;
        String limpio = alias.trim().replaceFirst("(?i)\\s+at$", "").replaceFirst("(?i)^at\\s+", "").trim();
        if (limpio.isEmpty() || limpio.equalsIgnoreCase("at")) return null;
        if (limpio.equalsIgnoreCase(persona) || limpio.equalsIgnoreCase(empresa)) return null;
        return limpio;
    }

    private static List<String> errores(DatosContacto d) {
        List<String> errores = new ArrayList<>();
        if (d.empresa() == null && d.persona() == null) errores.add("Sin empresa ni persona.");
        if (d.pais() == null || d.pais().length() != 2) errores.add("Sin código de país.");
        if (d.direccion1() == null) errores.add("Sin dirección.");
        if (d.ciudad() == null) errores.add("Sin ciudad.");
        return errores;
    }

    private static String primero(String... valores) {
        for (String v : valores) if (v != null) return v;
        return null;
    }
}
```

- [ ] **Step 7: Ejecutar `ImportadorLibretaMyDhlTest`**

Run: `mvn -q test -Dtest=ImportadorLibretaMyDhlTest`
Expected: PASS (8 tests). Si falla la lectura de columnas con acento, comprobar que el fichero se guardó en UTF-8 y que empieza por los dos BOM.

- [ ] **Step 7b: Test opcional contra el export real (solo si el fichero está en `docs/`)**

Añadir a `ImportadorLibretaMyDhlTest`:
```java
    @Test
    @org.junit.jupiter.api.condition.EnabledIf("hayExportReal")
    void elExportRealDeMyDhlSeLeeEnteroYSinErrores() throws Exception {
        java.nio.file.Path real = exportReal();
        PrevisualizacionLibreta prev = importador.previsualizar(java.nio.file.Files.readAllBytes(real), real.getFileName().toString());

        assertEquals(213, prev.filas().size());
        assertEquals(0, prev.erroneas().size(), prev.erroneas().toString());
        assertTrue(prev.duplicadas().size() >= 4, "el export real trae al menos cuatro grupos repetidos");
    }

    static boolean hayExportReal() { return exportReal() != null; }

    static java.nio.file.Path exportReal() {
        try (var ficheros = java.nio.file.Files.list(java.nio.file.Path.of("docs", "Envios DHL"))) {
            return ficheros.filter(f -> f.getFileName().toString().startsWith("address-book-") && f.toString().endsWith(".csv")).findFirst().orElse(null);
        } catch (java.io.IOException e) {
            return null;
        }
    }
```
Solo comprueba recuentos: el fichero tiene datos personales y no entra en el repo, así que el test no puede depender de él.

- [ ] **Step 8: Estado de sesión y controlador de contactos**

`shipping/web/ImportacionEnCurso.java`:
```java
package com.puntotres.packinglist.shipping.web;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.shipping.importacion.PrevisualizacionArticulos;
import com.puntotres.packinglist.shipping.importacion.PrevisualizacionLibreta;

/** La vista previa de una importación entre el "subir" y el "confirmar". Como EnvioEnCurso: por sesión. */
@Component
@SessionScope
public class ImportacionEnCurso {
    private PrevisualizacionLibreta libreta;
    private PrevisualizacionArticulos articulos;

    public PrevisualizacionLibreta getLibreta() { return libreta; }
    public void setLibreta(PrevisualizacionLibreta v) { libreta = v; }
    public PrevisualizacionArticulos getArticulos() { return articulos; }
    public void setArticulos(PrevisualizacionArticulos v) { articulos = v; }
}
```
(`PrevisualizacionArticulos` se crea en la Task 10; hasta entonces, dejar solo el campo `libreta` y añadir el otro en esa tarea.)

`shipping/web/ContactosController.java`:
```java
package com.puntotres.packinglist.shipping.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.shipping.carrier.AddressCheck;
import com.puntotres.packinglist.shipping.carrier.CarrierException;
import com.puntotres.packinglist.shipping.carrier.TipoDireccion;
import com.puntotres.packinglist.shipping.importacion.ImportadorLibretaMyDhl;
import com.puntotres.packinglist.shipping.importacion.PrevisualizacionLibreta;
import com.puntotres.packinglist.shipping.importacion.ResultadoImportacion;
import com.puntotres.packinglist.shipping.model.*;
import com.puntotres.packinglist.shipping.service.*;

@Controller
@RequestMapping("/envios/contactos")
public class ContactosController {

    private final ContactService contactos;
    private final BillingAccountService cuentas;
    private final ImportadorLibretaMyDhl importador;
    private final ImportacionEnCurso enCurso;

    public ContactosController(ContactService contactos, BillingAccountService cuentas, ImportadorLibretaMyDhl importador, ImportacionEnCurso enCurso) {
        this.contactos = contactos; this.cuentas = cuentas; this.importador = importador; this.enCurso = enCurso;
    }

    @GetMapping
    public String listado(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("contactos", contactos.buscar(q));
        return "contactos";
    }

    @GetMapping("/nuevo")
    public String nuevo(@RequestParam(required = false) String volver, Model model) {
        return ficha(model, new ContactoForm(), volver);
    }

    @GetMapping("/{id}")
    public String editar(@PathVariable Long id, @RequestParam(required = false) String volver, Model model) {
        Contact c = contactos.porId(id).orElseThrow(() -> new DatoInvalidoException("No existe el contacto " + id));
        return ficha(model, ContactoForm.de(c), volver);
    }

    @PostMapping
    public String guardar(@ModelAttribute ContactoForm form, @RequestParam(required = false) String volver, RedirectAttributes redirect) {
        try {
            Contact c = contactos.guardar(form.getId(), form.aDatos());
            redirect.addFlashAttribute("mensaje", "Guardado " + c.getEmpresa() + ".");
            return "redirect:" + (volver == null || volver.isBlank() ? "/envios/contactos" : volver);
        } catch (DatoInvalidoException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:" + (form.getId() == null ? "/envios/contactos/nuevo" : "/envios/contactos/" + form.getId())
                    + (volver == null ? "" : "?volver=" + volver);
        }
    }

    @PostMapping("/{id}/baja")
    public String baja(@PathVariable Long id, RedirectAttributes redirect) {
        contactos.darDeBaja(id);
        redirect.addFlashAttribute("mensaje", "Contacto dado de baja. Los envíos que lo usaban lo conservan.");
        return "redirect:/envios/contactos";
    }

    @PostMapping("/{id}/comprobar")
    public String comprobar(@PathVariable Long id, @RequestParam TipoDireccion tipo, RedirectAttributes redirect) {
        try {
            AddressCheck r = contactos.comprobarDireccion(id, tipo);
            redirect.addFlashAttribute(r.valida() ? "mensaje" : "mensajeError", r.detalle());
        } catch (CarrierException e) {
            redirect.addFlashAttribute("mensajeError", e.getMensajeUsuario());
        }
        return "redirect:/envios/contactos/" + id;
    }

    @GetMapping("/importar")
    public String importar(Model model) {
        model.addAttribute("previsualizacion", enCurso.getLibreta());
        return "contactos-importar";
    }

    @PostMapping("/importar")
    public String previsualizar(@RequestParam MultipartFile fichero, RedirectAttributes redirect) throws IOException {
        try {
            enCurso.setLibreta(importador.previsualizar(fichero.getBytes(), fichero.getOriginalFilename()));
        } catch (DatoInvalidoException | IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/envios/contactos/importar";
    }

    @PostMapping("/importar/confirmar")
    public String confirmar(@RequestParam(required = false) List<Integer> duplicadas, RedirectAttributes redirect) {
        PrevisualizacionLibreta prev = enCurso.getLibreta();
        if (prev == null) return "redirect:/envios/contactos/importar";
        ResultadoImportacion r = importador.importar(prev, duplicadas == null ? Set.of() : Set.copyOf(duplicadas));
        enCurso.setLibreta(null);
        redirect.addFlashAttribute("mensaje", r.altas() + " contactos dados de alta, " + r.omitidas() + " omitidos por duplicados.");
        if (!r.errores().isEmpty()) redirect.addFlashAttribute("errores", r.errores());
        return "redirect:/envios/contactos";
    }

    private String ficha(Model model, ContactoForm form, String volver) {
        model.addAttribute("form", form);
        model.addAttribute("volver", volver);
        model.addAttribute("tipos", ContactType.values());
        model.addAttribute("incoterms", Incoterm.values());
        model.addAttribute("cuentasTransporte", cuentas.activasConRol(BillingRole.PAYER));
        model.addAttribute("cuentasAranceles", cuentas.activasConRol(BillingRole.DUTIES));
        model.addAttribute("tiposDireccion", TipoDireccion.values());
        return "contacto";
    }

    /** Formulario de la ficha. Los alias van en un textarea, uno por línea. */
    public static class ContactoForm {
        private Long id;
        private String empresa, persona, direccion1, direccion2, direccion3, codigoPostal, ciudad, provincia, provinciaCodigo, pais,
                telefono, email, nifVat, eori, identificadorMarroqui, notas, aliasTexto;
        private ContactType tipo = ContactType.BUSINESS;
        private boolean residencial;
        private Incoterm incotermPorDefecto;
        private Long cuentaTransportePorDefectoId, cuentaArancelesPorDefectoId;

        static ContactoForm de(Contact c) {
            ContactoForm f = new ContactoForm();
            f.id = c.getId(); f.empresa = c.getEmpresa(); f.persona = c.getPersona();
            f.direccion1 = c.getDireccion1(); f.direccion2 = c.getDireccion2(); f.direccion3 = c.getDireccion3();
            f.codigoPostal = c.getCodigoPostal(); f.ciudad = c.getCiudad(); f.provincia = c.getProvincia(); f.provinciaCodigo = c.getProvinciaCodigo();
            f.pais = c.getPais(); f.telefono = c.getTelefono(); f.email = c.getEmail(); f.tipo = c.getTipo(); f.residencial = c.isResidencial();
            f.nifVat = c.getNifVat(); f.eori = c.getEori(); f.identificadorMarroqui = c.getIdentificadorMarroqui(); f.notas = c.getNotas();
            f.incotermPorDefecto = c.getIncotermPorDefecto();
            f.cuentaTransportePorDefectoId = c.getCuentaTransportePorDefecto() == null ? null : c.getCuentaTransportePorDefecto().getId();
            f.cuentaArancelesPorDefectoId = c.getCuentaArancelesPorDefecto() == null ? null : c.getCuentaArancelesPorDefecto().getId();
            f.aliasTexto = String.join("\n", c.getAlias());
            return f;
        }

        DatosContacto aDatos() {
            List<String> alias = aliasTexto == null ? List.of() : Arrays.stream(aliasTexto.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            return new DatosContacto(empresa, persona, direccion1, direccion2, direccion3, codigoPostal, ciudad, provincia, provinciaCodigo, pais,
                    telefono, email, tipo, residencial, nifVat, eori, identificadorMarroqui, notas, incotermPorDefecto,
                    cuentaTransportePorDefectoId, cuentaArancelesPorDefectoId, alias);
        }

        // getters y setters de todos los campos (necesarios para @ModelAttribute y th:field)
        public Long getId() { return id; } public void setId(Long v) { id = v; }
        public String getEmpresa() { return empresa; } public void setEmpresa(String v) { empresa = v; }
        public String getPersona() { return persona; } public void setPersona(String v) { persona = v; }
        public String getDireccion1() { return direccion1; } public void setDireccion1(String v) { direccion1 = v; }
        public String getDireccion2() { return direccion2; } public void setDireccion2(String v) { direccion2 = v; }
        public String getDireccion3() { return direccion3; } public void setDireccion3(String v) { direccion3 = v; }
        public String getCodigoPostal() { return codigoPostal; } public void setCodigoPostal(String v) { codigoPostal = v; }
        public String getCiudad() { return ciudad; } public void setCiudad(String v) { ciudad = v; }
        public String getProvincia() { return provincia; } public void setProvincia(String v) { provincia = v; }
        public String getProvinciaCodigo() { return provinciaCodigo; } public void setProvinciaCodigo(String v) { provinciaCodigo = v; }
        public String getPais() { return pais; } public void setPais(String v) { pais = v; }
        public String getTelefono() { return telefono; } public void setTelefono(String v) { telefono = v; }
        public String getEmail() { return email; } public void setEmail(String v) { email = v; }
        public String getNifVat() { return nifVat; } public void setNifVat(String v) { nifVat = v; }
        public String getEori() { return eori; } public void setEori(String v) { eori = v; }
        public String getIdentificadorMarroqui() { return identificadorMarroqui; } public void setIdentificadorMarroqui(String v) { identificadorMarroqui = v; }
        public String getNotas() { return notas; } public void setNotas(String v) { notas = v; }
        public String getAliasTexto() { return aliasTexto; } public void setAliasTexto(String v) { aliasTexto = v; }
        public ContactType getTipo() { return tipo; } public void setTipo(ContactType v) { tipo = v; }
        public boolean isResidencial() { return residencial; } public void setResidencial(boolean v) { residencial = v; }
        public Incoterm getIncotermPorDefecto() { return incotermPorDefecto; } public void setIncotermPorDefecto(Incoterm v) { incotermPorDefecto = v; }
        public Long getCuentaTransportePorDefectoId() { return cuentaTransportePorDefectoId; } public void setCuentaTransportePorDefectoId(Long v) { cuentaTransportePorDefectoId = v; }
        public Long getCuentaArancelesPorDefectoId() { return cuentaArancelesPorDefectoId; } public void setCuentaArancelesPorDefectoId(Long v) { cuentaArancelesPorDefectoId = v; }
    }
}
```

- [ ] **Step 9: Plantillas**

`templates/contactos.html`: subnav (activo Libreta), alertas (`mensaje`, `mensajeError`, y `<ul th:if="${errores}"><li th:each="e : ${errores}" th:text="${e}">`), un formulario GET con `<input type="search" name="q" th:value="${q}" placeholder="Empresa, persona, ciudad o alias">` y botón Buscar, dos enlaces-botón (`/envios/contactos/nuevo` "Nuevo contacto", `/envios/contactos/importar` "Importar libreta de MyDHL+"), y una tabla con columnas Empresa, Persona, Ciudad, País, Teléfono, Email, y un enlace "Editar" a `/envios/contactos/{id}` por fila. Filas: `th:each="c : ${contactos}"`.

`templates/contacto.html`: subnav (activo Libreta), alertas, un `<form th:action="@{/envios/contactos}" th:object="${form}" method="post" class="form-ficha">` con `<input type="hidden" th:field="*{id}">`, `<input type="hidden" name="volver" th:value="${volver}">` y, en `<label>`s en una rejilla de dos columnas (`class="rejilla-2"`): Empresa (`*{empresa}`, required), Persona, Dirección 1 (required, `maxlength="45"`), Dirección 2, Dirección 3, Código postal (`maxlength="12"`), Ciudad (required, `maxlength="45"`), Provincia, Código de provincia, País (`maxlength="2"`, required, placeholder ES), Teléfono (placeholder +34 934425135), Email, Tipo (`<select th:field="*{tipo}"><option th:each="t : ${tipos}" th:value="${t}" th:text="${t}">`), Entrega residencial (checkbox `*{residencial}`), NIF/VAT, EORI, ICE marroquí, Incoterm por defecto (select con opción vacía "—" y `th:each="i : ${incoterms}"`), Cuenta de transporte por defecto (select `*{cuentaTransportePorDefectoId}` con opción vacía y `th:each="c : ${cuentasTransporte}" th:value="${c.id}" th:text="|${c.numero} · ${c.titular}|"`), Cuenta de aranceles por defecto (igual con `cuentasAranceles`), Alias (`<textarea th:field="*{aliasTexto}" rows="3">`, ayuda "uno por línea"), Notas (`textarea`). Botones: Guardar; si `form.id != null`, un segundo formulario "Dar de baja" a `/envios/contactos/{id}/baja` y dos botones "Comprobar como recogida" / "Comprobar como entrega" que hacen POST a `/envios/contactos/{id}/comprobar` con `tipo=RECOGIDA` / `ENTREGA`. Enlace "Volver" a `${volver}` o a `/envios/contactos`.

`templates/contactos-importar.html`: subnav (activo Libreta), alertas, un formulario `enctype="multipart/form-data"` con `<input type="file" name="fichero" accept=".csv" required>` y botón "Ver qué se importaría". Si `${previsualizacion}` no es nulo: tres secciones `tarjeta`:
- "Listas para importar (N)": tabla Fila, Empresa, Persona, Ciudad, País, Teléfono, Cuentas (concatenando `cuentaRemitente`, `cuentaTransporte`, `cuentaAranceles` no nulas).
- "Duplicadas (N)": misma tabla más una columna con `<input type="checkbox" name="duplicadas" th:value="${f.numero}">` "importar igual" y una columna "Motivo" (`f.duplicadoDe`).
- "Con errores (N)": Fila, Empresa, Persona y los errores (`th:text="${#strings.listJoin(f.errores, ' ')}"`).
Los checkboxes van dentro de un `<form th:action="@{/envios/contactos/importar/confirmar}" method="post">` que envuelve las tres tablas y termina con el botón "Importar". Un párrafo `ayuda` encima: "Las filas con error no se importan; las duplicadas solo si las marcas."

- [ ] **Step 10: Ampliar RutasEnviosTest**

```java
    @Test
    void laLibretaSePintaYUnContactoSeDaDeAltaYSeBusca() throws Exception {
        mvc.perform(get("/envios/contactos")).andExpect(status().isOk()).andExpect(view().name("contactos"));
        mvc.perform(get("/envios/contactos/nuevo")).andExpect(status().isOk()).andExpect(view().name("contacto"));
        mvc.perform(post("/envios/contactos").param("empresa", "TALLER PRUEBA").param("direccion1", "Zone franche")
                        .param("ciudad", "Tanger").param("pais", "MA").param("telefono", "+212500000000"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/envios/contactos"));
        mvc.perform(get("/envios/contactos").param("q", "tanger"))
                .andExpect(content().string(containsString("TALLER PRUEBA")));
    }

    @Test
    void laImportacionDeLaLibretaPrevisualizaYConfirma() throws Exception {
        byte[] csv = new org.springframework.core.io.ClassPathResource("ejemplos/envios/libreta-mydhl-recorte.csv").getInputStream().readAllBytes();
        org.springframework.mock.web.MockMultipartFile fichero = new org.springframework.mock.web.MockMultipartFile("fichero", "libreta.csv", "text/csv", csv);
        org.springframework.mock.web.MockHttpSession sesion = new org.springframework.mock.web.MockHttpSession();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/envios/contactos/importar").file(fichero).session(sesion))
                .andExpect(redirectedUrl("/envios/contactos/importar"));
        mvc.perform(get("/envios/contactos/importar").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("MAISON CLIENTE")))
                .andExpect(content().string(containsString("Con errores (2)")));
        mvc.perform(post("/envios/contactos/importar/confirmar").session(sesion))
                .andExpect(redirectedUrl("/envios/contactos")).andExpect(flash().attributeExists("errores"));
        mvc.perform(get("/envios/contactos").param("q", "PELLI ESEMPIO")).andExpect(content().string(containsString("PELLI ESEMPIO SRL")));
        mvc.perform(get("/envios/cuentas")).andExpect(content().string(containsString("956483537")));
    }
```

- [ ] **Step 11: Suite** → PASS. **Commit**:

```bash
git add pom.xml src/main/java/com/puntotres/packinglist/shipping src/main/resources/templates src/test/java/com/puntotres/packinglist/shipping src/test/resources/ejemplos/envios/libreta-mydhl-recorte.csv
git commit -m "libreta de contactos con importador del CSV de MyDHL+ y comprobacion de direccion con DHL"
```

---

### Task 10: Importador CSV de artículos

**Files:**
- Create: `shipping/importacion/FilaArticulo.java`, `PrevisualizacionArticulos.java`, `ImportadorArticulosCsv.java`
- Modify: `shipping/web/ImportacionEnCurso.java` (campo `articulos`), `shipping/web/ArticulosController.java`
- Create: `templates/articulos-importar.html`, `src/test/resources/ejemplos/envios/articulos.csv`
- Test: `src/test/java/com/puntotres/packinglist/shipping/importacion/ImportadorArticulosCsvTest.java`

**Interfaces:**
- Produces: `record FilaArticulo(int numero, String referencia, String descripcion, String codigoHs, String paisOrigen, Double pesoNeto, Double pesoBruto, BigDecimal valorUnitario, List<String> errores, boolean yaExiste)`; `record PrevisualizacionArticulos(String nombreFichero, List<FilaArticulo> filas)` con `validas()`/`erroneas()`; `ImportadorArticulosCsv(ArticleService)`: `previsualizar(byte[], String)`, `ResultadoImportacion importar(PrevisualizacionArticulos)`.

- [ ] **Step 1: Fixture** `src/test/resources/ejemplos/envios/articulos.csv` (cabecera fija; la coma decimal se acepta):

```
referencia,descripcion,codigo_hs,pais_origen,peso_neto,peso_bruto,valor_unitario
UBL029,Cinturón de piel de vacuno,42033000,MA,0.180,0.200,"8,50"
PXCEI-F67043,Bolso de piel de vacuno con forro textil,42022100,MA,0.650,0.720,42.00
SINHS,Bolso sin código,,MA,,,
,Sin referencia,42022100,ES,,,
```

- [ ] **Step 2: Test**

```java
package com.puntotres.packinglist.shipping.importacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.puntotres.packinglist.shipping.model.Article;
import com.puntotres.packinglist.shipping.model.ArticleRepository;
import com.puntotres.packinglist.shipping.service.ArticleService;

class ImportadorArticulosCsvTest {

    private final ArticleRepository repo = mock(ArticleRepository.class);
    private final ArticleService servicio = new ArticleService(repo);
    private final ImportadorArticulosCsv importador = new ImportadorArticulosCsv(servicio);

    private byte[] csv() throws Exception {
        return new ClassPathResource("ejemplos/envios/articulos.csv").getInputStream().readAllBytes();
    }

    @Test
    void separaValidasDeErroneasYLeeLaComaDecimal() throws Exception {
        PrevisualizacionArticulos prev = importador.previsualizar(csv(), "articulos.csv");

        assertEquals(2, prev.validas().size());
        assertEquals(2, prev.erroneas().size());
        FilaArticulo cinturon = prev.validas().get(0);
        assertEquals("UBL029", cinturon.referencia());
        assertEquals(new BigDecimal("8.50"), cinturon.valorUnitario());
        assertEquals(0.18, cinturon.pesoNeto(), 0.0001);
        assertTrue(prev.erroneas().get(0).errores().get(0).contains("código HS"));
        assertTrue(prev.erroneas().get(1).errores().get(0).contains("referencia"));
    }

    @Test
    void unaReferenciaQueYaExisteSeMarcaYAlImportarSeActualiza() throws Exception {
        Article existente = new Article("UBL029", "Cinturón", "42033000", "MA");
        when(repo.findByReferenciaIgnoreCase("UBL029")).thenReturn(Optional.of(existente));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PrevisualizacionArticulos prev = importador.previsualizar(csv(), "a.csv");
        assertTrue(prev.validas().get(0).yaExiste());
        assertFalse(prev.validas().get(1).yaExiste());

        ResultadoImportacion r = importador.importar(prev);

        assertEquals(1, r.altas());
        assertEquals(1, r.actualizadas());
        assertEquals(2, r.errores().size());
        assertEquals("Cinturón de piel de vacuno", existente.getDescripcionAduanera());
    }

    @Test
    void sinLaCabeceraEsperadaAvisa() {
        assertThrows(com.puntotres.packinglist.shipping.service.DatoInvalidoException.class,
                () -> importador.previsualizar("ref,desc\nA,B\n".getBytes(), "x.csv"));
    }
}
```

- [ ] **Step 3: Implementación**

```java
package com.puntotres.packinglist.shipping.importacion;

import java.math.BigDecimal;
import java.util.List;

public record FilaArticulo(int numero, String referencia, String descripcion, String codigoHs, String paisOrigen,
                           Double pesoNeto, Double pesoBruto, BigDecimal valorUnitario, List<String> errores, boolean yaExiste) {
    public boolean valida() { return errores.isEmpty(); }
}
```

```java
package com.puntotres.packinglist.shipping.importacion;

import java.util.List;

public record PrevisualizacionArticulos(String nombreFichero, List<FilaArticulo> filas) {
    public List<FilaArticulo> validas() { return filas.stream().filter(FilaArticulo::valida).toList(); }
    public List<FilaArticulo> erroneas() { return filas.stream().filter(f -> !f.valida()).toList(); }
}
```

`shipping/importacion/ImportadorArticulosCsv.java`:
```java
package com.puntotres.packinglist.shipping.importacion;

import static com.puntotres.packinglist.shipping.importacion.LectorCsv.columna;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.service.ArticleService;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;

/**
 * CSV propio del catálogo aduanero: referencia, descripcion, codigo_hs,
 * pais_origen, peso_neto, peso_bruto, valor_unitario. Las tres primeras y el
 * país son obligatorias. La referencia manda: reimportar corrige.
 */
@Service
public class ImportadorArticulosCsv {

    static final String REFERENCIA = "referencia", DESCRIPCION = "descripcion", HS = "codigo_hs", PAIS = "pais_origen",
            NETO = "peso_neto", BRUTO = "peso_bruto", VALOR = "valor_unitario";

    private final ArticleService articulos;

    public ImportadorArticulosCsv(ArticleService articulos) { this.articulos = articulos; }

    public PrevisualizacionArticulos previsualizar(byte[] csv, String nombreFichero) {
        List<CSVRecord> registros = LectorCsv.leer(csv);
        if (registros.isEmpty() || !registros.get(0).isMapped(REFERENCIA) || !registros.get(0).isMapped(HS)) {
            throw new DatoInvalidoException("El CSV de artículos necesita las columnas: referencia, descripcion, codigo_hs, pais_origen, peso_neto, peso_bruto, valor_unitario.");
        }
        List<FilaArticulo> filas = new ArrayList<>();
        int numero = 0;
        for (CSVRecord r : registros) {
            numero++;
            List<String> errores = new ArrayList<>();
            String referencia = columna(r, REFERENCIA), descripcion = columna(r, DESCRIPCION), hs = columna(r, HS), pais = columna(r, PAIS);
            if (referencia == null) errores.add("Sin referencia.");
            if (descripcion == null) errores.add("Sin descripción aduanera.");
            if (hs == null) errores.add("Sin código HS.");
            if (pais == null || pais.length() != 2) errores.add("El país de origen es el código de dos letras.");
            boolean existe = referencia != null && articulos.todos().stream().anyMatch(a -> a.getReferencia().equalsIgnoreCase(referencia));
            filas.add(new FilaArticulo(numero, referencia, descripcion, hs, pais == null ? null : pais.toUpperCase(),
                    decimal(columna(r, NETO)), decimal(columna(r, BRUTO)), importe(columna(r, VALOR)), errores, existe));
        }
        return new PrevisualizacionArticulos(nombreFichero, filas);
    }

    @Transactional
    public ResultadoImportacion importar(PrevisualizacionArticulos prev) {
        int altas = 0, actualizadas = 0;
        List<String> errores = new ArrayList<>();
        for (FilaArticulo f : prev.filas()) {
            if (!f.valida()) { errores.add("Fila " + f.numero() + ": " + String.join(" ", f.errores())); continue; }
            try {
                articulos.guardarPorReferencia(f.referencia(), f.descripcion(), f.codigoHs(), f.paisOrigen(), f.pesoNeto(), f.pesoBruto(), f.valorUnitario());
                if (f.yaExiste()) actualizadas++; else altas++;
            } catch (DatoInvalidoException e) {
                errores.add("Fila " + f.numero() + ": " + e.getMessage());
            }
        }
        return new ResultadoImportacion(altas, actualizadas, 0, errores);
    }

    static Double decimal(String s) {
        if (s == null) return null;
        try { return Double.valueOf(s.replace(',', '.')); } catch (NumberFormatException e) { return null; }
    }

    static BigDecimal importe(String s) {
        if (s == null) return null;
        try { return new BigDecimal(s.replace(',', '.')).setScale(2, java.math.RoundingMode.HALF_UP); } catch (NumberFormatException e) { return null; }
    }
}
```

En `ImportadorArticulosCsvTest.unaReferenciaQueYaExiste...` la detección de `yaExiste` usa `articulos.todos()` → el mock debe devolverla: añadir al test `when(repo.findAllByOrderByReferenciaAsc()).thenReturn(java.util.List.of(existente));` justo después del `when(repo.findByReferenciaIgnoreCase(...))`.

Añadir a `ArticulosController`:
```java
    @GetMapping("/importar")
    public String importar(Model model) {
        model.addAttribute("previsualizacion", enCurso.getArticulos());
        return "articulos-importar";
    }

    @PostMapping("/importar")
    public String previsualizar(@RequestParam MultipartFile fichero, RedirectAttributes redirect) throws IOException {
        try {
            enCurso.setArticulos(importador.previsualizar(fichero.getBytes(), fichero.getOriginalFilename()));
        } catch (DatoInvalidoException | IllegalArgumentException e) {
            redirect.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/envios/articulos/importar";
    }

    @PostMapping("/importar/confirmar")
    public String confirmar(RedirectAttributes redirect) {
        PrevisualizacionArticulos prev = enCurso.getArticulos();
        if (prev == null) return "redirect:/envios/articulos/importar";
        ResultadoImportacion r = importador.importar(prev);
        enCurso.setArticulos(null);
        redirect.addFlashAttribute("mensaje", r.altas() + " artículos nuevos, " + r.actualizadas() + " actualizados.");
        if (!r.errores().isEmpty()) redirect.addFlashAttribute("errores", r.errores());
        return "redirect:/envios/articulos";
    }
```
con `ImportadorArticulosCsv importador` e `ImportacionEnCurso enCurso` inyectados en el constructor, y el campo `articulos` en `ImportacionEnCurso`.

`templates/articulos-importar.html`: como `contactos-importar.html` pero con dos secciones (válidas, con una columna "Ya existe → se actualizará" cuando `f.yaExiste`; erróneas) y sin checkboxes; formulario de confirmación a `/envios/articulos/importar/confirmar`. Encima, la ayuda con la cabecera esperada: `referencia,descripcion,codigo_hs,pais_origen,peso_neto,peso_bruto,valor_unitario`.

- [ ] **Step 4: Ejecutar test, suite y commit**

```bash
mvn -q test
git add src/main/java/com/puntotres/packinglist/shipping src/main/resources/templates/articulos-importar.html src/test/java/com/puntotres/packinglist/shipping/importacion src/test/resources/ejemplos/envios/articulos.csv
git commit -m "importador CSV del catalogo de articulos aduaneros"
```

---

### Task 11: Pantalla del envío, listado y estilos

**Files:**
- Create: `shipping/web/EnvioForm.java`, `FiltroEnviosForm.java`, `EnviosController.java`
- Create: `templates/envios.html`, `envio.html`
- Modify: `templates/fragmentos.html` (fragmento `subnavEnvios(activo)`), `cuentas.html`, `motivos.html`, `articulos.html`, `contactos*.html`, `contacto.html` (usar el fragmento), `static/estilo.css`
- Test: ampliar `RutasEnviosTest`

**Interfaces:**
- Consumes: `ShipmentService` (Task 5), `ContactService`, `BillingAccountService`, `ShipmentReasonService`, `ArticleService` (Tasks 8-9), `EnviosProperties`, `ClientesProperties.getClientes()` (existente), `ArchivoTemporadas.todas()` (existente, `ResumenTemporada.temporada()`).
- Produces: rutas `/envios`, `/envios/nuevo`, `/envios/{id}`, `/envios/{id}/documentos/{tipo}.pdf`.

- [ ] **Step 1: Fragmento de subnavegación**

En `templates/fragmentos.html`, dentro de `<body>`, añadir:
```html
<nav class="subnav" th:fragment="subnavEnvios(activo)">
    <a th:href="@{/envios}" th:classappend="${activo == 'envios'} ? 'activo'">Envíos</a>
    <a th:href="@{/envios/contactos}" th:classappend="${activo == 'contactos'} ? 'activo'">Libreta</a>
    <a th:href="@{/envios/cuentas}" th:classappend="${activo == 'cuentas'} ? 'activo'">Cuentas DHL</a>
    <a th:href="@{/envios/motivos}" th:classappend="${activo == 'motivos'} ? 'activo'">Motivos</a>
    <a th:href="@{/envios/articulos}" th:classappend="${activo == 'articulos'} ? 'activo'">Artículos</a>
</nav>
```
y sustituir la `<nav class="subnav">` inline de las plantillas de las Tasks 8-10 por `<nav th:replace="~{fragmentos :: subnavEnvios('cuentas')}"></nav>` (con su clave: `motivos`, `articulos`, `contactos`).

- [ ] **Step 2: Formularios**

`shipping/web/FiltroEnviosForm.java`:
```java
package com.puntotres.packinglist.shipping.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.puntotres.packinglist.shipping.model.Direction;
import com.puntotres.packinglist.shipping.model.ShipmentStatus;
import com.puntotres.packinglist.shipping.service.FiltroEnvios;

public class FiltroEnviosForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate desde;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate hasta;
    private ShipmentStatus estado;
    private Long motivoId;
    private String cliente;
    private Direction sentido;

    public FiltroEnvios aFiltro() { return new FiltroEnvios(desde, hasta, estado, motivoId, cliente, sentido); }

    public LocalDate getDesde() { return desde; } public void setDesde(LocalDate v) { desde = v; }
    public LocalDate getHasta() { return hasta; } public void setHasta(LocalDate v) { hasta = v; }
    public ShipmentStatus getEstado() { return estado; } public void setEstado(ShipmentStatus v) { estado = v; }
    public Long getMotivoId() { return motivoId; } public void setMotivoId(Long v) { motivoId = v; }
    public String getCliente() { return cliente; } public void setCliente(String v) { cliente = v; }
    public Direction getSentido() { return sentido; } public void setSentido(Direction v) { sentido = v; }
}
```

`shipping/web/EnvioForm.java`:
```java
package com.puntotres.packinglist.shipping.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.puntotres.packinglist.shipping.model.*;
import com.puntotres.packinglist.shipping.service.DatosEnvio;

/**
 * Lo que se teclea en /envios/{id}. Las listas se enlazan por índice
 * (bultos[0].peso); añadir y quitar filas son submits que las modifican en el
 * servidor y vuelven a pintar, como en la revisión del packing list.
 */
public class EnvioForm {

    private Long remitenteId, destinatarioId, cuentaTransporteId, cuentaArancelesId, motivoId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fechaRecogida;
    private Incoterm incoterm = Incoterm.DAP;
    private String cliente, temporada, referencia, notas, responsable, productoCodigo, horaCierreRecogida, lugarRecogida;
    private boolean pedirRecogida = true;
    private List<BultoForm> bultos = new ArrayList<>();
    private List<LineaForm> lineas = new ArrayList<>();

    public static EnvioForm de(Shipment e) {
        EnvioForm f = new EnvioForm();
        f.remitenteId = id(e.getRemitente()); f.destinatarioId = id(e.getDestinatario());
        f.fechaRecogida = e.getFechaRecogida(); f.incoterm = e.getIncoterm();
        f.cuentaTransporteId = e.getCuentaTransporte() == null ? null : e.getCuentaTransporte().getId();
        f.cuentaArancelesId = e.getCuentaAranceles() == null ? null : e.getCuentaAranceles().getId();
        f.motivoId = e.getMotivo() == null ? null : e.getMotivo().getId();
        f.cliente = e.getCliente(); f.temporada = e.getTemporada(); f.referencia = e.getReferencia(); f.notas = e.getNotas();
        f.responsable = e.getResponsable(); f.productoCodigo = e.getProductoCodigo();
        f.pedirRecogida = e.isPedirRecogida(); f.horaCierreRecogida = e.getHoraCierreRecogida(); f.lugarRecogida = e.getLugarRecogida();
        for (ShipmentPackage b : e.getBultos()) f.bultos.add(new BultoForm(b.getPeso(), b.getLargo(), b.getAncho(), b.getAlto(), b.getDescripcion()));
        for (CustomsLineItem l : e.getLineasAduana()) f.lineas.add(new LineaForm(l.getDescripcion(), l.getCodigoHs(), l.getCantidad(), l.getValorUnitario(), l.getPesoNeto(), l.getPesoBruto(), l.getPaisOrigen(), l.getArticuloId()));
        return f;
    }

    public DatosEnvio aDatos() {
        return new DatosEnvio(remitenteId, destinatarioId, fechaRecogida, incoterm, cuentaTransporteId, cuentaArancelesId, motivoId,
                cliente, temporada, referencia, notas, responsable, pedirRecogida, horaCierreRecogida, lugarRecogida,
                bultos.stream().map(b -> new DatosEnvio.DatosBulto(b.peso, b.largo, b.ancho, b.alto, b.descripcion)).toList(),
                lineas.stream().map(l -> new DatosEnvio.DatosLinea(l.descripcion, l.codigoHs, l.cantidad, l.valorUnitario, l.pesoNeto, l.pesoBruto, l.paisOrigen, l.articuloId)).toList());
    }

    /** Preselecciona cuentas e incoterm del destinatario si el envío aún no los tiene. */
    public void heredarDe(Contact destinatario) {
        if (destinatario == null) return;
        if (cuentaTransporteId == null && destinatario.getCuentaTransportePorDefecto() != null) cuentaTransporteId = destinatario.getCuentaTransportePorDefecto().getId();
        if (cuentaArancelesId == null && destinatario.getCuentaArancelesPorDefecto() != null) cuentaArancelesId = destinatario.getCuentaArancelesPorDefecto().getId();
        if (destinatario.getIncotermPorDefecto() != null && incoterm == Incoterm.DAP) incoterm = destinatario.getIncotermPorDefecto();
    }

    public void anadirLineaDe(Article a) {
        lineas.add(new LineaForm(a.getDescripcionAduanera(), a.getCodigoHs(), 1, a.getValorUnitario(), a.getPesoNetoUnitario(), a.getPesoBrutoUnitario(), a.getPaisOrigen(), a.getId()));
    }

    private static Long id(Contact c) { return c == null ? null : c.getId(); }

    public static class BultoForm {
        private Double peso, largo, ancho, alto; private String descripcion;
        public BultoForm() { }
        public BultoForm(Double peso, Double largo, Double ancho, Double alto, String descripcion) { this.peso = peso; this.largo = largo; this.ancho = ancho; this.alto = alto; this.descripcion = descripcion; }
        public Double getPeso() { return peso; } public void setPeso(Double v) { peso = v; }
        public Double getLargo() { return largo; } public void setLargo(Double v) { largo = v; }
        public Double getAncho() { return ancho; } public void setAncho(Double v) { ancho = v; }
        public Double getAlto() { return alto; } public void setAlto(Double v) { alto = v; }
        public String getDescripcion() { return descripcion; } public void setDescripcion(String v) { descripcion = v; }
    }

    public static class LineaForm {
        private String descripcion, codigoHs, paisOrigen; private Integer cantidad; private BigDecimal valorUnitario; private Double pesoNeto, pesoBruto; private Long articuloId;
        public LineaForm() { }
        public LineaForm(String descripcion, String codigoHs, Integer cantidad, BigDecimal valorUnitario, Double pesoNeto, Double pesoBruto, String paisOrigen, Long articuloId) {
            this.descripcion = descripcion; this.codigoHs = codigoHs; this.cantidad = cantidad; this.valorUnitario = valorUnitario; this.pesoNeto = pesoNeto; this.pesoBruto = pesoBruto; this.paisOrigen = paisOrigen; this.articuloId = articuloId;
        }
        public String getDescripcion() { return descripcion; } public void setDescripcion(String v) { descripcion = v; }
        public String getCodigoHs() { return codigoHs; } public void setCodigoHs(String v) { codigoHs = v; }
        public String getPaisOrigen() { return paisOrigen; } public void setPaisOrigen(String v) { paisOrigen = v; }
        public Integer getCantidad() { return cantidad; } public void setCantidad(Integer v) { cantidad = v; }
        public BigDecimal getValorUnitario() { return valorUnitario; } public void setValorUnitario(BigDecimal v) { valorUnitario = v; }
        public Double getPesoNeto() { return pesoNeto; } public void setPesoNeto(Double v) { pesoNeto = v; }
        public Double getPesoBruto() { return pesoBruto; } public void setPesoBruto(Double v) { pesoBruto = v; }
        public Long getArticuloId() { return articuloId; } public void setArticuloId(Long v) { articuloId = v; }
    }

    // getters y setters
    public Long getRemitenteId() { return remitenteId; } public void setRemitenteId(Long v) { remitenteId = v; }
    public Long getDestinatarioId() { return destinatarioId; } public void setDestinatarioId(Long v) { destinatarioId = v; }
    public Long getCuentaTransporteId() { return cuentaTransporteId; } public void setCuentaTransporteId(Long v) { cuentaTransporteId = v; }
    public Long getCuentaArancelesId() { return cuentaArancelesId; } public void setCuentaArancelesId(Long v) { cuentaArancelesId = v; }
    public Long getMotivoId() { return motivoId; } public void setMotivoId(Long v) { motivoId = v; }
    public LocalDate getFechaRecogida() { return fechaRecogida; } public void setFechaRecogida(LocalDate v) { fechaRecogida = v; }
    public Incoterm getIncoterm() { return incoterm; } public void setIncoterm(Incoterm v) { incoterm = v; }
    public String getCliente() { return cliente; } public void setCliente(String v) { cliente = v; }
    public String getTemporada() { return temporada; } public void setTemporada(String v) { temporada = v; }
    public String getReferencia() { return referencia; } public void setReferencia(String v) { referencia = v; }
    public String getNotas() { return notas; } public void setNotas(String v) { notas = v; }
    public String getResponsable() { return responsable; } public void setResponsable(String v) { responsable = v; }
    public String getProductoCodigo() { return productoCodigo; } public void setProductoCodigo(String v) { productoCodigo = v; }
    public List<BultoForm> getBultos() { return bultos; } public void setBultos(List<BultoForm> v) { bultos = v; }
    public List<LineaForm> getLineas() { return lineas; } public void setLineas(List<LineaForm> v) { lineas = v; }
}
```

- [ ] **Step 3: Controlador**

`shipping/web/EnviosController.java`:
```java
package com.puntotres.packinglist.shipping.web;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.ResumenTemporada;
import com.puntotres.packinglist.shipping.config.EnviosProperties;
import com.puntotres.packinglist.shipping.model.*;
import com.puntotres.packinglist.shipping.service.*;

/**
 * El envío es un solo formulario y cada botón es un submit con un parámetro
 * "accion": guardar, añadir/quitar filas, tarifar, confirmar y crear,
 * reintentar, cancelar. Todo lo tecleado se aplica antes de la acción, así que
 * nunca se pierde nada al pulsar un botón.
 *
 * En QUOTED el formulario se pinta de solo lectura: cualquier edición devuelve
 * a DRAFT y borra la tarifa, y eso tiene que ser una decisión visible
 * ("Modificar datos"), no un efecto de tocar una casilla.
 */
@Controller
@RequestMapping("/envios")
public class EnviosController {

    private final ShipmentService envios;
    private final ContactService contactos;
    private final BillingAccountService cuentas;
    private final ShipmentReasonService motivos;
    private final ArticleService articulos;
    private final EnviosProperties propiedades;
    private final ClientesProperties clientes;
    private final ArchivoTemporadas temporadas;

    public EnviosController(ShipmentService envios, ContactService contactos, BillingAccountService cuentas, ShipmentReasonService motivos,
                            ArticleService articulos, EnviosProperties propiedades, ClientesProperties clientes, ArchivoTemporadas temporadas) {
        this.envios = envios; this.contactos = contactos; this.cuentas = cuentas; this.motivos = motivos;
        this.articulos = articulos; this.propiedades = propiedades; this.clientes = clientes; this.temporadas = temporadas;
    }

    @GetMapping
    public String listado(@ModelAttribute("filtro") FiltroEnviosForm filtro, Model model) {
        model.addAttribute("envios", envios.listar(filtro.aFiltro()));
        model.addAttribute("estados", ShipmentStatus.values());
        model.addAttribute("sentidos", Direction.values());
        model.addAttribute("motivos", motivos.todos());
        return "envios";
    }

    @PostMapping("/nuevo")
    public String nuevo() {
        String responsable = propiedades.getResponsables().isEmpty() ? "" : propiedades.getResponsables().get(0);
        Shipment envio = envios.crearBorrador(responsable);
        return "redirect:/envios/" + envio.getId();
    }

    @GetMapping("/{id}")
    public String ficha(@PathVariable Long id, Model model) {
        Shipment envio = envios.cargar(id);
        EnvioForm form = EnvioForm.de(envio);
        form.heredarDe(envio.getDestinatario());
        return pintar(envio, form, model);
    }

    @PostMapping("/{id}")
    public String accion(@PathVariable Long id, @ModelAttribute("form") EnvioForm form, @RequestParam String accion,
                         @RequestParam(required = false) Integer indice, @RequestParam(required = false) Long articuloId,
                         RedirectAttributes redirect) {
        try {
            switch (accion) {
                case "guardar" -> { envios.editar(id, form.aDatos()); redirect.addFlashAttribute("mensaje", "Guardado."); }
                case "anadirBulto" -> { form.getBultos().add(new EnvioForm.BultoForm()); envios.editar(id, form.aDatos()); }
                case "quitarBulto" -> { if (indice != null && indice < form.getBultos().size()) form.getBultos().remove(indice.intValue()); envios.editar(id, form.aDatos()); }
                case "anadirLinea" -> { form.getLineas().add(new EnvioForm.LineaForm()); envios.editar(id, form.aDatos()); }
                case "anadirArticulo" -> { articulos.porId(articuloId).ifPresent(form::anadirLineaDe); envios.editar(id, form.aDatos()); }
                case "quitarLinea" -> { if (indice != null && indice < form.getLineas().size()) form.getLineas().remove(indice.intValue()); envios.editar(id, form.aDatos()); }
                case "intercambiar" -> { Long r = form.getRemitenteId(); form.setRemitenteId(form.getDestinatarioId()); form.setDestinatarioId(r); envios.editar(id, form.aDatos()); }
                case "tarifar" -> {
                    envios.editar(id, form.aDatos());
                    ResultadoTarifa r = envios.tarifar(id);
                    if (!r.problemas().isEmpty()) redirect.addFlashAttribute("problemas", r.problemas());
                    else if (r.errorTransportista() != null) redirect.addFlashAttribute("mensajeError", r.errorTransportista());
                    else redirect.addFlashAttribute("mensaje", r.envio().getOfertas().size() + " productos disponibles. Elige uno y confirma.");
                }
                case "modificar" -> { envios.editar(id, EnvioForm.de(envios.cargar(id)).aDatos()); redirect.addFlashAttribute("mensaje", "De vuelta a borrador; la tarifa anterior ya no vale."); }
                case "confirmar" -> {
                    if (form.getProductoCodigo() == null || form.getProductoCodigo().isBlank()) {
                        redirect.addFlashAttribute("mensajeError", "Elige un producto de la tarifa antes de confirmar.");
                        break;
                    }
                    envios.confirmar(id, form.getProductoCodigo());
                    Shipment creado = envios.crear(id);
                    if (creado.getEstado() == ShipmentStatus.CREATED) redirect.addFlashAttribute("mensaje", "Envío creado en DHL. Guía " + creado.getNumeroGuia() + ".");
                    else redirect.addFlashAttribute("mensajeError", creado.getUltimoError());
                }
                case "reintentar" -> {
                    Shipment creado = envios.crear(id);
                    if (creado.getEstado() == ShipmentStatus.CREATED) redirect.addFlashAttribute("mensaje", "Envío creado en DHL. Guía " + creado.getNumeroGuia() + ".");
                    else redirect.addFlashAttribute("mensajeError", creado.getUltimoError());
                }
                case "cancelar" -> { envios.cancelar(id); redirect.addFlashAttribute("mensaje", "Envío cancelado."); }
                default -> redirect.addFlashAttribute("mensajeError", "Acción desconocida: " + accion);
            }
        } catch (TransicionIlegalException | ObjectOptimisticLockingFailureException e) {
            // Doble clic o dos pestañas: la pantalla se recarga con el estado real, que es lo que hay que ver.
            redirect.addFlashAttribute("mensajeError", "El envío ha cambiado de estado mientras tanto. Se muestra tal como está ahora.");
        }
        return "redirect:/envios/" + id;
    }

    @GetMapping("/{id}/documentos/{tipo}.pdf")
    public ResponseEntity<byte[]> documento(@PathVariable Long id, @PathVariable String tipo) {
        Optional<ShipmentDocument> doc = envios.documento(id, tipo);
        if (doc.isEmpty()) return ResponseEntity.notFound().build();
        Shipment envio = envios.cargar(id);
        String nombre = "DHL_" + envio.getNumeroGuia() + "_" + tipo + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(doc.get().getContenido());
    }

    @ExceptionHandler(EnvioNoEncontradoException.class)
    public String noEncontrado(EnvioNoEncontradoException e, RedirectAttributes redirect) {
        redirect.addFlashAttribute("mensajeError", e.getMessage());
        return "redirect:/envios";
    }

    private String pintar(Shipment envio, EnvioForm form, Model model) {
        boolean editable = envio.getEstado() == ShipmentStatus.DRAFT;
        model.addAttribute("envio", envio);
        model.addAttribute("form", form);
        model.addAttribute("editable", editable);
        model.addAttribute("problemas", editable || envio.getEstado() == ShipmentStatus.QUOTED ? envios.problemasParaTarifar(envio) : List.of());
        model.addAttribute("avisos", envios.avisos(envio));
        model.addAttribute("contactos", contactos.activos());
        model.addAttribute("motivos", motivos.activos());
        model.addAttribute("responsables", propiedades.getResponsables());
        model.addAttribute("incoterms", Incoterm.values());
        model.addAttribute("cuentasTransporte", cuentas.activasConRol(BillingRole.PAYER));
        model.addAttribute("cuentasAranceles", cuentas.activasConRol(BillingRole.DUTIES));
        model.addAttribute("articulos", articulos.activos());
        model.addAttribute("clientes", clientes.getClientes().keySet());
        model.addAttribute("temporadas", temporadas.todas().stream().map(ResumenTemporada::temporada).distinct().toList());
        model.addAttribute("tiposDocumento", envio.getEstado() == ShipmentStatus.CREATED ? envios.tiposDocumento(envio.getId()) : List.of());
        return "envio";
    }
}
```

- [ ] **Step 4: Plantilla del listado** `templates/envios.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragmentos :: head('Envíos')}"></head>
<body>
<header th:replace="~{fragmentos :: cabecera('Envíos por transportista')}"></header>
<main>
    <nav th:replace="~{fragmentos :: subnavEnvios('envios')}"></nav>
    <div class="alerta ok" th:if="${mensaje}" th:text="${mensaje}"></div>
    <div class="alerta error" th:if="${mensajeError}" th:text="${mensajeError}"></div>

    <section class="tarjeta">
        <form th:action="@{/envios/nuevo}" method="post" class="form-inline">
            <button type="submit">Nuevo envío DHL</button>
        </form>
        <form th:action="@{/envios}" method="get" th:object="${filtro}" class="form-fila filtros">
            <label>Desde <input type="date" th:field="*{desde}"></label>
            <label>Hasta <input type="date" th:field="*{hasta}"></label>
            <label>Estado <select th:field="*{estado}"><option value="">todos</option><option th:each="e : ${estados}" th:value="${e}" th:text="${e}"></option></select></label>
            <label>Motivo <select th:field="*{motivoId}"><option value="">todos</option><option th:each="m : ${motivos}" th:value="${m.id}" th:text="${m.nombre}"></option></select></label>
            <label>Cliente <input type="text" th:field="*{cliente}" size="10"></label>
            <label>Sentido <select th:field="*{sentido}"><option value="">todos</option><option th:each="s : ${sentidos}" th:value="${s}" th:text="${s.etiqueta}"></option></select></label>
            <button type="submit" class="secundario">Filtrar</button>
        </form>
    </section>

    <section class="tarjeta">
        <table class="tabla-envios">
            <thead><tr><th>Fecha</th><th>Guía</th><th>De → a</th><th>Motivo</th><th>Cliente</th><th>Responsable</th><th>Coste</th><th>Estado</th></tr></thead>
            <tbody>
            <tr th:each="e : ${envios}">
                <td><a th:href="@{/envios/{id}(id=${e.id})}" th:text="${#temporals.format(e.fechaCreacion, 'dd/MM/yyyy')}"></a></td>
                <td th:text="${e.numeroGuia} ?: '—'"></td>
                <td th:text="|${e.remitente?.empresa ?: '?'} → ${e.destinatario?.empresa ?: '?'}|"></td>
                <td th:text="${e.motivo?.nombre}"></td>
                <td th:text="${e.cliente}"></td>
                <td th:text="${e.responsable}"></td>
                <td th:text="${e.costeEstimado != null} ? |${#numbers.formatDecimal(e.costeEstimado, 1, 2)} ${e.moneda}| : ''"></td>
                <td><span class="estado" th:classappend="'estado-' + ${e.estado}" th:text="${e.estado}"></span></td>
            </tr>
            <tr th:if="${#lists.isEmpty(envios)}"><td colspan="8" class="ayuda">No hay envíos con esos filtros.</td></tr>
            </tbody>
        </table>
    </section>
</main>
</body>
</html>
```

- [ ] **Step 5: Plantilla del envío** `templates/envio.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragmentos :: head('Envío')}"></head>
<body>
<header th:replace="~{fragmentos :: cabecera(|Envío DHL nº ${envio.id}|)}"></header>
<main>
    <nav th:replace="~{fragmentos :: subnavEnvios('envios')}"></nav>
    <div class="alerta ok" th:if="${mensaje}" th:text="${mensaje}"></div>
    <div class="alerta error multilinea" th:if="${mensajeError}" th:text="${mensajeError}"></div>
    <div class="alerta error" th:if="${problemas != null and !#lists.isEmpty(problemas)}">
        <strong>Falta para tarifar:</strong>
        <ul><li th:each="p : ${problemas}" th:text="${p}"></li></ul>
    </div>
    <div class="alerta aviso" th:if="${!#lists.isEmpty(avisos)}">
        <ul><li th:each="a : ${avisos}" th:text="${a}"></li></ul>
    </div>

    <p class="estado-envio">
        Estado: <span class="estado" th:classappend="'estado-' + ${envio.estado}" th:text="${envio.estado}"></span>
        <span th:if="${envio.sentido}" th:text="| · ${envio.sentido.etiqueta}|"></span>
        <span th:text="${envio.aduanero} ? ' · con aduana' : ' · sin aduana'"></span>
        <span th:if="${envio.numeroGuia}"> · Guía <strong th:text="${envio.numeroGuia}"></strong>
            <a th:if="${envio.urlSeguimiento}" th:href="${envio.urlSeguimiento}" target="_blank">seguimiento</a></span>
        <span th:if="${envio.numeroConfirmacionRecogida}" th:text="| · Recogida nº ${envio.numeroConfirmacionRecogida}|"></span>
    </p>

    <form th:action="@{/envios/{id}(id=${envio.id})}" th:object="${form}" method="post" class="form-envio">

        <section class="tarjeta">
            <h3 class="titulo-seccion">1. Remitente y destinatario</h3>
            <div class="rejilla-2">
                <label>Remitente
                    <select th:field="*{remitenteId}" th:disabled="${!editable}">
                        <option value="">— elegir —</option>
                        <option th:each="c : ${contactos}" th:value="${c.id}" th:text="${c.etiqueta()}"></option>
                    </select>
                </label>
                <label>Destinatario
                    <select th:field="*{destinatarioId}" th:disabled="${!editable}">
                        <option value="">— elegir —</option>
                        <option th:each="c : ${contactos}" th:value="${c.id}" th:text="${c.etiqueta()}"></option>
                    </select>
                </label>
            </div>
            <p th:if="${editable}" class="acciones-fila">
                <button type="submit" name="accion" value="intercambiar" class="mini secundario">Intercambiar</button>
                <a class="boton mini secundario" th:href="@{/envios/contactos/nuevo(volver=|/envios/${envio.id}|)}">Alta rápida de contacto</a>
            </p>
        </section>

        <section class="tarjeta">
            <h3 class="titulo-seccion">2. Motivo, cliente y responsable</h3>
            <div class="rejilla-3">
                <label>Motivo
                    <select th:field="*{motivoId}" th:disabled="${!editable}">
                        <option value="">— elegir —</option>
                        <option th:each="m : ${motivos}" th:value="${m.id}" th:text="${m.nombre}"></option>
                    </select>
                </label>
                <label>Cliente <input type="text" th:field="*{cliente}" list="clientes" th:readonly="${!editable}"></label>
                <datalist id="clientes"><option th:each="c : ${clientes}" th:value="${c}"></option></datalist>
                <label>Temporada <input type="text" th:field="*{temporada}" list="temporadas" th:readonly="${!editable}"></label>
                <datalist id="temporadas"><option th:each="t : ${temporadas}" th:value="${t}"></option></datalist>
                <label>Referencia <input type="text" th:field="*{referencia}" maxlength="35" th:readonly="${!editable}"></label>
                <label>Responsable
                    <select th:field="*{responsable}" th:disabled="${!editable}">
                        <option th:each="r : ${responsables}" th:value="${r}" th:text="${r}"></option>
                    </select>
                </label>
                <label>Fecha de envío <input type="date" th:field="*{fechaRecogida}" th:readonly="${!editable}"></label>
            </div>
            <div class="rejilla-3 recogida">
                <label class="check"><input type="checkbox" th:field="*{pedirRecogida}" th:disabled="${!editable}"> Pedir recogida al mensajero</label>
                <label>Hora de cierre <input type="time" th:field="*{horaCierreRecogida}" th:readonly="${!editable}"></label>
                <label>Lugar de recogida <input type="text" th:field="*{lugarRecogida}" maxlength="80" th:readonly="${!editable}"></label>
            </div>
            <label>Notas <textarea th:field="*{notas}" rows="2" th:readonly="${!editable}"></textarea></label>
        </section>

        <section class="tarjeta">
            <h3 class="titulo-seccion">3. Bultos</h3>
            <table class="tabla-bultos">
                <thead><tr><th>#</th><th>Peso (kg)</th><th>Largo (cm)</th><th>Ancho (cm)</th><th>Alto (cm)</th><th>Descripción</th><th></th></tr></thead>
                <tbody>
                <tr th:each="b, it : *{bultos}">
                    <td th:text="${it.count}"></td>
                    <td><input type="number" step="0.01" min="0" th:field="*{bultos[__${it.index}__].peso}" th:readonly="${!editable}"></td>
                    <td><input type="number" step="0.1" min="0" th:field="*{bultos[__${it.index}__].largo}" th:readonly="${!editable}"></td>
                    <td><input type="number" step="0.1" min="0" th:field="*{bultos[__${it.index}__].ancho}" th:readonly="${!editable}"></td>
                    <td><input type="number" step="0.1" min="0" th:field="*{bultos[__${it.index}__].alto}" th:readonly="${!editable}"></td>
                    <td><input type="text" maxlength="70" th:field="*{bultos[__${it.index}__].descripcion}" th:readonly="${!editable}"></td>
                    <td><button th:if="${editable}" type="submit" name="accion" value="quitarBulto" th:formaction="@{/envios/{id}(id=${envio.id}, indice=${it.index})}" class="mini gris">Quitar</button></td>
                </tr>
                </tbody>
            </table>
            <button th:if="${editable}" type="submit" name="accion" value="anadirBulto" class="mini secundario">Añadir bulto</button>
        </section>

        <section class="tarjeta" th:if="${envio.aduanero}">
            <h3 class="titulo-seccion">4. Aduana</h3>
            <table class="tabla-aduana">
                <thead><tr><th>#</th><th>Descripción</th><th>HS</th><th>Cant.</th><th>Valor ud.</th><th>Neto (kg)</th><th>Bruto (kg)</th><th>Origen</th><th></th></tr></thead>
                <tbody>
                <tr th:each="l, it : *{lineas}">
                    <td th:text="${it.count}"></td>
                    <td><input type="text" maxlength="255" th:field="*{lineas[__${it.index}__].descripcion}" th:readonly="${!editable}"></td>
                    <td><input type="text" maxlength="20" size="10" th:field="*{lineas[__${it.index}__].codigoHs}" th:readonly="${!editable}"></td>
                    <td><input type="number" min="1" step="1" th:field="*{lineas[__${it.index}__].cantidad}" th:readonly="${!editable}"></td>
                    <td><input type="number" min="0" step="0.01" th:field="*{lineas[__${it.index}__].valorUnitario}" th:readonly="${!editable}"></td>
                    <td><input type="number" min="0" step="0.001" th:field="*{lineas[__${it.index}__].pesoNeto}" th:readonly="${!editable}"></td>
                    <td><input type="number" min="0" step="0.001" th:field="*{lineas[__${it.index}__].pesoBruto}" th:readonly="${!editable}"></td>
                    <td><input type="text" maxlength="2" size="2" th:field="*{lineas[__${it.index}__].paisOrigen}" th:readonly="${!editable}"></td>
                    <td><input type="hidden" th:field="*{lineas[__${it.index}__].articuloId}">
                        <button th:if="${editable}" type="submit" name="accion" value="quitarLinea" th:formaction="@{/envios/{id}(id=${envio.id}, indice=${it.index})}" class="mini gris">Quitar</button></td>
                </tr>
                </tbody>
                <tfoot><tr><td colspan="4">Valor declarado</td><td th:text="|${#numbers.formatDecimal(envio.valorDeclarado(), 1, 2)} ${envio.moneda}|"></td>
                    <td></td><td th:text="${#numbers.formatDecimal(envio.pesoBrutoLineas(), 1, 2)}"></td><td colspan="2"></td></tr></tfoot>
            </table>
            <p th:if="${editable}" class="acciones-fila">
                <button type="submit" name="accion" value="anadirLinea" class="mini secundario">Añadir línea vacía</button>
                <select name="articuloId"><option value="">— artículo del catálogo —</option>
                    <option th:each="a : ${articulos}" th:value="${a.id}" th:text="|${a.referencia} · ${a.descripcionAduanera}|"></option></select>
                <button type="submit" name="accion" value="anadirArticulo" class="mini secundario">Añadir del catálogo</button>
            </p>
        </section>

        <section class="tarjeta">
            <h3 class="titulo-seccion">5. Pago</h3>
            <div class="rejilla-3">
                <label>Cuenta que paga el transporte
                    <select th:field="*{cuentaTransporteId}" th:disabled="${!editable}">
                        <option value="">— elegir —</option>
                        <option th:each="c : ${cuentasTransporte}" th:value="${c.id}" th:text="|${c.numero} · ${c.titular}|"></option>
                    </select>
                </label>
                <label>Cuenta que paga los aranceles
                    <select th:field="*{cuentaArancelesId}" th:disabled="${!editable}">
                        <option value="">— el destinatario a la entrega —</option>
                        <option th:each="c : ${cuentasAranceles}" th:value="${c.id}" th:text="|${c.numero} · ${c.titular}|"></option>
                    </select>
                </label>
                <label>Incoterm
                    <select th:field="*{incoterm}" th:disabled="${!editable}">
                        <option th:each="i : ${incoterms}" th:value="${i}" th:text="${i}"></option>
                    </select>
                </label>
            </div>
        </section>

        <section class="tarjeta" th:if="${!#lists.isEmpty(envio.ofertas)}">
            <h3 class="titulo-seccion">6. Tarifa</h3>
            <table class="tabla-ofertas">
                <thead><tr><th></th><th>Producto</th><th>Precio</th><th>Entrega estimada</th></tr></thead>
                <tbody>
                <tr th:each="o : ${envio.ofertas}">
                    <td><input type="radio" th:field="*{productoCodigo}" th:value="${o.productoCodigo}" th:disabled="${envio.estado.name() != 'QUOTED'}"></td>
                    <td th:text="|${o.productoNombre} (${o.productoCodigo})|"></td>
                    <td th:text="${o.precio != null} ? |${#numbers.formatDecimal(o.precio, 1, 2)} ${o.moneda}| : 'sin precio'"></td>
                    <td th:text="${o.entregaEstimada != null} ? ${#temporals.format(o.entregaEstimada, 'dd/MM/yyyy HH:mm')} : ''"></td>
                </tr>
                </tbody>
            </table>
        </section>

        <section class="tarjeta" th:if="${envio.estado.name() == 'CREATED'}">
            <h3 class="titulo-seccion">Documentos</h3>
            <p><a th:each="t : ${tiposDocumento}" class="boton secundario" th:href="@{/envios/{id}/documentos/{t}.pdf(id=${envio.id}, t=${t})}" th:text="${t}"></a></p>
        </section>

        <section class="acciones">
            <th:block th:if="${editable}">
                <button type="submit" name="accion" value="guardar" class="secundario">Guardar</button>
                <button type="submit" name="accion" value="tarifar">Tarifar</button>
            </th:block>
            <th:block th:if="${envio.estado.name() == 'QUOTED'}">
                <button type="submit" name="accion" value="modificar" class="secundario">Modificar datos</button>
                <button type="submit" name="accion" value="confirmar">Confirmar y crear</button>
            </th:block>
            <th:block th:if="${envio.estado.name() == 'CONFIRMED' or envio.estado.name() == 'ERROR'}">
                <button type="submit" name="accion" value="reintentar">Reintentar la creación en DHL</button>
            </th:block>
            <button th:if="${envio.estado.name() != 'CREATED' and envio.estado.name() != 'CANCELLED'}" type="submit" name="accion" value="cancelar" class="gris"
                    onclick="return confirm('¿Cancelar este envío?')">Cancelar envío</button>
        </section>
    </form>

    <section class="tarjeta historial" th:if="${!#lists.isEmpty(envio.historial)}">
        <h3 class="titulo-seccion">Historial</h3>
        <ul>
            <li th:each="h : ${envio.historial}" th:text="|${#temporals.format(h.fecha, 'dd/MM/yyyy HH:mm')} · ${h.estadoNuevo} · ${h.responsable ?: ''} ${h.detalle != null ? '· ' + h.detalle : ''}|"></li>
        </ul>
    </section>
</main>
</body>
</html>
```

El `onclick="return confirm(...)"` del botón Cancelar es la única línea de JavaScript de la sección y es deliberada: cancelar no se deshace y un clic de más al lado de "Reintentar" no debe borrar el envío. Si se prefiere cero JavaScript, sustituir por una pantalla intermedia `/envios/{id}/cancelar` con GET de confirmación y POST.

- [ ] **Step 6: CSS**

Añadir al bloque `/* ---- Envíos por transportista ---- */` de `estilo.css`:
```css
.rejilla-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem 1rem; }
.rejilla-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.6rem 1rem; }
.rejilla-2 label, .rejilla-3 label, .form-envio > .tarjeta > label { display: flex; flex-direction: column; gap: 0.2rem; font-size: 0.9rem; }
.form-envio select, .form-envio input[type="date"] { padding: 0.35rem; font: inherit; }
.form-envio input:read-only, .form-envio select:disabled { background: #f3f5f7; color: #3a4550; }
.acciones { display: flex; gap: 0.6rem; flex-wrap: wrap; margin: 1rem 0; }
.acciones-fila { display: flex; gap: 0.5rem; align-items: center; margin: 0.5rem 0 0; }
.estado { display: inline-block; padding: 0.1rem 0.5rem; border-radius: 0.8rem; font-size: 0.8rem; font-weight: bold; background: #e6e9ed; }
.estado-QUOTED { background: #e3ecfa; } .estado-CONFIRMED { background: #fff1cc; }
.estado-CREATED { background: #d9f2df; } .estado-ERROR { background: #f9d6d5; } .estado-CANCELLED { background: #e6e9ed; color: #7a8590; }
.alerta.aviso { background: #fff8e1; border-left: 4px solid #e0a800; }
.alerta.multilinea { white-space: pre-line; }
.alerta ul { margin: 0.3rem 0 0 1rem; }
.historial ul { margin: 0; padding-left: 1rem; font-size: 0.85rem; color: #5a6673; }
.tabla-bultos input[type="number"], .tabla-aduana input[type="number"] { width: 5.5rem; }
.tabla-aduana input[type="text"] { width: 100%; box-sizing: border-box; }
.filtros { margin-top: 0.6rem; }
```
Comprobar que `.alerta.aviso` no existe ya con otro sentido (`grep -n "alerta.aviso" estilo.css`); si existe, reutilizarla y no redefinirla.

- [ ] **Step 7: Ampliar RutasEnviosTest**

```java
    @Test
    void elListadoSePintaYNuevoCreaUnBorradorYRedirigeASuFicha() throws Exception {
        mvc.perform(get("/envios")).andExpect(status().isOk()).andExpect(view().name("envios"));
        String destino = mvc.perform(post("/envios/nuevo")).andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        org.junit.jupiter.api.Assertions.assertTrue(destino.matches("/envios/\\d+"), destino);
        mvc.perform(get(destino)).andExpect(status().isOk()).andExpect(view().name("envio"))
                .andExpect(content().string(containsString("DRAFT")))
                .andExpect(content().string(containsString("Tarifar")));
    }

    @Test
    void anadirUnBultoYGuardarSeReflejaEnLaFicha() throws Exception {
        String destino = mvc.perform(post("/envios/nuevo")).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(post(destino).param("accion", "anadirBulto").param("responsable", "Jordi"))
                .andExpect(redirectedUrl(destino));
        mvc.perform(post(destino).param("accion", "guardar").param("responsable", "Jordi")
                        .param("bultos[0].peso", "2.5").param("bultos[0].largo", "40").param("bultos[0].ancho", "30").param("bultos[0].alto", "20"))
                .andExpect(flash().attribute("mensaje", "Guardado."));
        mvc.perform(get(destino)).andExpect(content().string(containsString("value=\"2.5\"")));
    }

    @Test
    void tarifarSinDatosVuelveConLaListaDeProblemas() throws Exception {
        String destino = mvc.perform(post("/envios/nuevo")).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(post(destino).param("accion", "tarifar").param("responsable", "Jordi"))
                .andExpect(flash().attributeExists("problemas"));
    }

    @Test
    void unEnvioQueNoExisteVuelveAlListadoConMensaje() throws Exception {
        mvc.perform(get("/envios/999999")).andExpect(redirectedUrl("/envios")).andExpect(flash().attributeExists("mensajeError"));
    }
```

- [ ] **Step 8: Suite** → PASS. Arrancar `mvn spring-boot:run`, entrar en `/envios`, crear un envío, añadir bultos, elegir contactos importados y comprobar a ojo que las filas se añaden y quitan, que la aduana desaparece al elegir dos contactos de la UE y que "Tarifar" sin credenciales devuelve el aviso de variables de entorno en rojo. Parar el servidor (recordar `netstat`/`taskkill` en Windows).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/shipping/web src/main/resources/templates src/main/resources/static/estilo.css src/test/java/com/puntotres/packinglist/shipping/web
git commit -m "pantalla del envio DHL: borrador, bultos, aduana, pago, tarifa y creacion; listado con filtros"
```

---

### Task 12: API REST `/api/shipments`

**Files:**
- Create: `shipping/api/ShipmentDto.java`, `ShipmentInput.java`, `OfferDto.java`, `ContactDto.java`, `ErrorApi.java`, `ApiExceptionHandler.java`, `ShipmentsApiController.java`, `CatalogosApiController.java`
- Test: `src/test/java/com/puntotres/packinglist/shipping/api/ShipmentsApiTest.java`

**Interfaces:**
- Consumes: `ShipmentService` y `DatosEnvio` (Task 5), servicios de catálogo (Tasks 8-9).
- Produces: las rutas de la sección 11 de la spec. JSON con Jackson; ningún endpoint devuelve entidades.

- [ ] **Step 1: Test de la API (MockMvc, flujo completo con el transportista real sin credenciales → ERROR)**

```java
package com.puntotres.packinglist.shipping.api;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ShipmentsApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private long crearContacto(String empresa, String pais) throws Exception {
        String cuerpo = mvc.perform(post("/envios/contactos").param("empresa", empresa).param("direccion1", "Calle 1")
                .param("ciudad", "Ciudad").param("pais", pais).param("telefono", "+34600000000")).andReturn().getResponse().getRedirectedUrl();
        JsonNode lista = json.readTree(mvc.perform(get("/api/contacts").param("q", empresa)).andReturn().getResponse().getContentAsString());
        return lista.get(0).get("id").asLong();
    }

    @Test
    void crearBorradorLeerEditarYCancelar() throws Exception {
        long origen = crearContacto("API ORIGEN", "ES");
        long destino = crearContacto("API DESTINO", "MA");
        String entrada = """
            {"remitenteId": %d, "destinatarioId": %d, "fechaRecogida": "2030-01-10", "incoterm": "DAP",
             "responsable": "Jordi", "cliente": "AMI", "referencia": "API-1",
             "bultos": [{"peso": 2.5, "largo": 40, "ancho": 30, "alto": 20}],
             "lineasAduana": [{"descripcion": "Bolsos", "codigoHs": "42022100", "cantidad": 3, "valorUnitario": 40, "pesoNeto": 1, "pesoBruto": 1.5, "paisOrigen": "MA"}]}
            """.formatted(origen, destino);

        String respuesta = mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("DRAFT"))
                .andExpect(jsonPath("$.sentido").value("EXPORT"))
                .andExpect(jsonPath("$.aduanero").value(true))
                .andExpect(jsonPath("$.valorDeclarado").value(120.0))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();

        mvc.perform(get("/api/shipments/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.bultos", hasSize(1)));
        mvc.perform(get("/api/shipments").param("estado", "DRAFT")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == %d)]".formatted(id)).exists());

        mvc.perform(put("/api/shipments/{id}", id).contentType(MediaType.APPLICATION_JSON).content(entrada.replace("\"API-1\"", "\"API-2\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.referencia").value("API-2"));

        mvc.perform(post("/api/shipments/{id}/cancel", id)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELLED"));
        mvc.perform(post("/api/shipments/{id}/cancel", id)).andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("CANCELLED")));
    }

    @Test
    void tarifarSinDatosDevuelve422ConLosProblemas() throws Exception {
        String respuesta = mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content("{\"responsable\": \"Jordi\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();

        mvc.perform(post("/api/shipments/{id}/quote", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detalles", hasItem(containsString("remitente"))));
    }

    @Test
    void tarifarSinCredencialesDeDhlDevuelve502ConElMensajeDelTransportista() throws Exception {
        long origen = crearContacto("API ORIGEN 2", "ES");
        long destino = crearContacto("API DESTINO 2", "MA");
        long cuenta = json.readTree(mvc.perform(get("/api/billing-accounts")).andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        long motivo = json.readTree(mvc.perform(get("/api/reasons")).andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        String entrada = """
            {"remitenteId": %d, "destinatarioId": %d, "fechaRecogida": "2030-01-10", "responsable": "Jordi",
             "cuentaTransporteId": %d, "motivoId": %d,
             "bultos": [{"peso": 2.5, "largo": 40, "ancho": 30, "alto": 20}],
             "lineasAduana": [{"descripcion": "Bolsos", "codigoHs": "42022100", "cantidad": 3, "valorUnitario": 40, "pesoNeto": 1, "pesoBruto": 1.5, "paisOrigen": "MA"}]}
            """.formatted(origen, destino, cuenta, motivo);
        long id = json.readTree(mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/shipments/{id}/quote", id))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensaje", containsString("DHL_API_KEY")));
    }

    @Test
    void unEnvioInexistenteEs404YUnaTransicionIlegal409() throws Exception {
        mvc.perform(get("/api/shipments/999999")).andExpect(status().isNotFound());
        String respuesta = mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content("{\"responsable\": \"Jordi\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();
        mvc.perform(post("/api/shipments/{id}/confirm", id).contentType(MediaType.APPLICATION_JSON).content("{\"productCode\": \"P\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/shipments/{id}/create", id)).andExpect(status().isConflict());
    }
}
```

El test de 502 necesita que en el perfil `test` exista al menos una cuenta con rol `PAYER`: `CuentaPropiaInicial` no la crea porque no hay `DHL_SHIPPER_ACCOUNT`. Añadir en `src/test/resources/application-test.yml`:
```yaml
envios:
  dhl:
    shipper-account: "300112049"
    # api-key y api-secret vacías a propósito: los tests comprueban el aviso de credenciales.
```

- [ ] **Step 2: Ejecutar → no compila**

- [ ] **Step 3: DTOs**

```java
package com.puntotres.packinglist.shipping.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.puntotres.packinglist.shipping.model.Incoterm;
import com.puntotres.packinglist.shipping.service.DatosEnvio;

/** Cuerpo de POST y PUT /api/shipments. Misma forma que DatosEnvio; existe para que la API no dependa del record del servicio. */
public record ShipmentInput(Long remitenteId, Long destinatarioId, LocalDate fechaRecogida, Incoterm incoterm,
                            Long cuentaTransporteId, Long cuentaArancelesId, Long motivoId,
                            String cliente, String temporada, String referencia, String notas, String responsable,
                            Boolean pedirRecogida, String horaCierreRecogida, String lugarRecogida,
                            List<PackageInput> bultos, List<CustomsLineInput> lineasAduana) {

    public record PackageInput(Double peso, Double largo, Double ancho, Double alto, String descripcion) { }
    public record CustomsLineInput(String descripcion, String codigoHs, Integer cantidad, BigDecimal valorUnitario,
                                   Double pesoNeto, Double pesoBruto, String paisOrigen, Long articuloId) { }

    public DatosEnvio aDatos() {
        return new DatosEnvio(remitenteId, destinatarioId, fechaRecogida, incoterm, cuentaTransporteId, cuentaArancelesId, motivoId,
                cliente, temporada, referencia, notas, responsable, pedirRecogida, horaCierreRecogida, lugarRecogida,
                bultos == null ? List.of() : bultos.stream().map(b -> new DatosEnvio.DatosBulto(b.peso(), b.largo(), b.ancho(), b.alto(), b.descripcion())).toList(),
                lineasAduana == null ? List.of() : lineasAduana.stream().map(l -> new DatosEnvio.DatosLinea(l.descripcion(), l.codigoHs(), l.cantidad(), l.valorUnitario(), l.pesoNeto(), l.pesoBruto(), l.paisOrigen(), l.articuloId())).toList());
    }
}
```

```java
package com.puntotres.packinglist.shipping.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.puntotres.packinglist.shipping.model.RateOffer;

public record OfferDto(String productCode, String productName, BigDecimal price, String currency, LocalDateTime estimatedDelivery) {
    static OfferDto de(RateOffer o) { return new OfferDto(o.getProductoCodigo(), o.getProductoNombre(), o.getPrecio(), o.getMoneda(), o.getEntregaEstimada()); }
}
```

```java
package com.puntotres.packinglist.shipping.api;

import com.puntotres.packinglist.shipping.model.Contact;

public record ContactDto(Long id, String empresa, String persona, String ciudad, String pais, String telefono, String email, String etiqueta) {
    static ContactDto de(Contact c) { return new ContactDto(c.getId(), c.getEmpresa(), c.getPersona(), c.getCiudad(), c.getPais(), c.getTelefono(), c.getEmail(), c.etiqueta()); }
}
```

```java
package com.puntotres.packinglist.shipping.api;

import java.util.List;

public record ErrorApi(String mensaje, List<String> detalles) {
    static ErrorApi de(String mensaje) { return new ErrorApi(mensaje, List.of()); }
}
```

`shipping/api/ShipmentDto.java`:
```java
package com.puntotres.packinglist.shipping.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.puntotres.packinglist.shipping.model.*;

/** Vista JSON de un envío. Sin los PDF: esos van por /documents/{tipo}.pdf. */
public record ShipmentDto(Long id, CarrierCode transportista, ShipmentStatus estado, Direction sentido, boolean aduanero,
                          ContactDto remitente, ContactDto destinatario, LocalDate fechaRecogida, String productoCodigo, String productoNombre,
                          Incoterm incoterm, String cuentaTransporte, String cuentaAranceles, String motivo,
                          String cliente, String temporada, String referencia, String notas, String responsable,
                          boolean pedirRecogida, String horaCierreRecogida, String lugarRecogida, String numeroConfirmacionRecogida,
                          BigDecimal costeEstimado, String moneda, BigDecimal costeFacturado, BigDecimal valorDeclarado,
                          String numeroGuia, String urlSeguimiento, String ultimoError, String requestId,
                          LocalDateTime fechaCreacion, LocalDateTime fechaActualizacion,
                          List<Bulto> bultos, List<Linea> lineasAduana, List<OfferDto> ofertas, List<String> documentos, List<Historial> historial) {

    public record Bulto(Double peso, Double largo, Double ancho, Double alto, String descripcion) { }
    public record Linea(String descripcion, String codigoHs, Integer cantidad, String unidad, BigDecimal valorUnitario, Double pesoNeto, Double pesoBruto, String paisOrigen, Long articuloId) { }
    public record Historial(ShipmentStatus estadoAnterior, ShipmentStatus estadoNuevo, LocalDateTime fecha, String responsable, String detalle) { }

    static ShipmentDto de(Shipment e, List<String> documentos) {
        return new ShipmentDto(e.getId(), e.getTransportista(), e.getEstado(), e.getSentido(), e.isAduanero(),
                e.getRemitente() == null ? null : ContactDto.de(e.getRemitente()), e.getDestinatario() == null ? null : ContactDto.de(e.getDestinatario()),
                e.getFechaRecogida(), e.getProductoCodigo(), e.getProductoNombre(), e.getIncoterm(),
                e.getCuentaTransporte() == null ? null : e.getCuentaTransporte().getNumero(), e.getCuentaAranceles() == null ? null : e.getCuentaAranceles().getNumero(),
                e.getMotivo() == null ? null : e.getMotivo().getNombre(),
                e.getCliente(), e.getTemporada(), e.getReferencia(), e.getNotas(), e.getResponsable(),
                e.isPedirRecogida(), e.getHoraCierreRecogida(), e.getLugarRecogida(), e.getNumeroConfirmacionRecogida(),
                e.getCosteEstimado(), e.getMoneda(), e.getCosteFacturado(), e.valorDeclarado(),
                e.getNumeroGuia(), e.getUrlSeguimiento(), e.getUltimoError(), e.getRequestId(), e.getFechaCreacion(), e.getFechaActualizacion(),
                e.getBultos().stream().map(b -> new Bulto(b.getPeso(), b.getLargo(), b.getAncho(), b.getAlto(), b.getDescripcion())).toList(),
                e.getLineasAduana().stream().map(l -> new Linea(l.getDescripcion(), l.getCodigoHs(), l.getCantidad(), l.getUnidad(), l.getValorUnitario(), l.getPesoNeto(), l.getPesoBruto(), l.getPaisOrigen(), l.getArticuloId())).toList(),
                e.getOfertas().stream().map(OfferDto::de).toList(),
                documentos,
                e.getHistorial().stream().map(h -> new Historial(h.getEstadoAnterior(), h.getEstadoNuevo(), h.getFecha(), h.getResponsable(), h.getDetalle())).toList());
    }
}
```

- [ ] **Step 4: Controladores y manejador de errores**

`shipping/api/ApiExceptionHandler.java`:
```java
package com.puntotres.packinglist.shipping.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.puntotres.packinglist.shipping.carrier.CarrierException;
import com.puntotres.packinglist.shipping.service.DatoInvalidoException;
import com.puntotres.packinglist.shipping.service.EnvioNoEncontradoException;
import com.puntotres.packinglist.shipping.service.TransicionIlegalException;

/** Solo para la API: la web traduce las mismas excepciones a mensajes en pantalla. */
@RestControllerAdvice(basePackages = "com.puntotres.packinglist.shipping.api")
public class ApiExceptionHandler {

    @ExceptionHandler(EnvioNoEncontradoException.class)
    public ResponseEntity<ErrorApi> noEncontrado(EnvioNoEncontradoException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorApi.de(e.getMessage())); }

    @ExceptionHandler({TransicionIlegalException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorApi> conflicto(RuntimeException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorApi.de(e.getMessage())); }

    @ExceptionHandler(DatoInvalidoException.class)
    public ResponseEntity<ErrorApi> invalido(DatoInvalidoException e) { return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorApi.de(e.getMessage())); }

    @ExceptionHandler(CarrierException.class)
    public ResponseEntity<ErrorApi> transportista(CarrierException e) { return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorApi.de(e.getMensajeUsuario())); }
}
```

`shipping/api/ShipmentsApiController.java`:
```java
package com.puntotres.packinglist.shipping.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.puntotres.packinglist.shipping.model.*;
import com.puntotres.packinglist.shipping.service.*;

/**
 * El mismo flujo que la pantalla, para un agente o un script: borrador →
 * quote → confirm → create. Sin autenticación en esta fase (como el resto de
 * la app, solo dentro de la red de la empresa).
 */
@RestController
@RequestMapping(value = "/api/shipments", produces = MediaType.APPLICATION_JSON_VALUE)
public class ShipmentsApiController {

    public record ConfirmInput(String productCode) { }

    private final ShipmentService envios;

    public ShipmentsApiController(ShipmentService envios) { this.envios = envios; }

    @PostMapping
    public ResponseEntity<ShipmentDto> crear(@RequestBody ShipmentInput entrada) {
        String responsable = entrada.responsable() == null || entrada.responsable().isBlank() ? "api" : entrada.responsable();
        Shipment borrador = envios.crearBorrador(responsable);
        Shipment editado = envios.editar(borrador.getId(), entrada.aDatos());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto(editado));
    }

    @GetMapping
    public List<ShipmentDto> listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                    @RequestParam(required = false) ShipmentStatus estado, @RequestParam(required = false) Long motivoId,
                                    @RequestParam(required = false) String cliente, @RequestParam(required = false) Direction sentido) {
        return envios.listar(new FiltroEnvios(desde, hasta, estado, motivoId, cliente, sentido)).stream()
                .map(e -> ShipmentDto.de(e, List.of())).toList();
    }

    @GetMapping("/{id}")
    public ShipmentDto uno(@PathVariable Long id) { return dto(envios.cargar(id)); }

    @PutMapping("/{id}")
    public ShipmentDto editar(@PathVariable Long id, @RequestBody ShipmentInput entrada) { return dto(envios.editar(id, entrada.aDatos())); }

    @PostMapping("/{id}/quote")
    public ResponseEntity<?> tarifar(@PathVariable Long id) {
        ResultadoTarifa r = envios.tarifar(id);
        if (!r.problemas().isEmpty()) return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorApi("El envío no está listo para tarifar.", r.problemas()));
        if (r.errorTransportista() != null) return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorApi.de(r.errorTransportista()));
        return ResponseEntity.ok(r.envio().getOfertas().stream().map(OfferDto::de).toList());
    }

    @PostMapping("/{id}/confirm")
    public ShipmentDto confirmar(@PathVariable Long id, @RequestBody ConfirmInput entrada) { return dto(envios.confirmar(id, entrada.productCode())); }

    /** Devuelve 200 tanto en CREATED como en ERROR: el estado del envío es la verdad y va en el cuerpo. */
    @PostMapping("/{id}/create")
    public ShipmentDto crear(@PathVariable Long id) { return dto(envios.crear(id)); }

    @PostMapping("/{id}/cancel")
    public ShipmentDto cancelar(@PathVariable Long id) { return dto(envios.cancelar(id)); }

    @GetMapping(value = "/{id}/documents/{tipo}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> documento(@PathVariable Long id, @PathVariable String tipo) {
        Optional<ShipmentDocument> doc = envios.documento(id, tipo);
        return doc.map(d -> ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + tipo + "-" + id + ".pdf\"").body(d.getContenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ShipmentDto dto(Shipment e) {
        Shipment cargado = envios.cargar(e.getId());
        return ShipmentDto.de(cargado, cargado.getEstado() == ShipmentStatus.CREATED ? envios.tiposDocumento(cargado.getId()) : List.of());
    }
}
```

`shipping/api/CatalogosApiController.java`:
```java
package com.puntotres.packinglist.shipping.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.puntotres.packinglist.shipping.model.BillingRole;
import com.puntotres.packinglist.shipping.service.*;

/** Catálogos de solo lectura para que un agente rellene un envío: contactos, cuentas, motivos y artículos. */
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogosApiController {

    public record CuentaDto(Long id, String numero, String titular, boolean propia, Set<BillingRole> roles) { }
    public record MotivoDto(Long id, String nombre, String motivoDhl) { }
    public record ArticuloDto(Long id, String referencia, String descripcionAduanera, String codigoHs, String paisOrigen, Double pesoNeto, Double pesoBruto, BigDecimal valorUnitario) { }

    private final ContactService contactos;
    private final BillingAccountService cuentas;
    private final ShipmentReasonService motivos;
    private final ArticleService articulos;

    public CatalogosApiController(ContactService contactos, BillingAccountService cuentas, ShipmentReasonService motivos, ArticleService articulos) {
        this.contactos = contactos; this.cuentas = cuentas; this.motivos = motivos; this.articulos = articulos;
    }

    @GetMapping("/contacts")
    public List<ContactDto> contactos(@RequestParam(required = false) String q) { return contactos.buscar(q).stream().map(ContactDto::de).toList(); }

    @GetMapping("/billing-accounts")
    public List<CuentaDto> cuentas() {
        return cuentas.todas().stream().filter(c -> c.isActiva()).map(c -> new CuentaDto(c.getId(), c.getNumero(), c.getTitular(), c.isPropia(), c.getRoles())).toList();
    }

    @GetMapping("/reasons")
    public List<MotivoDto> motivos() { return motivos.activos().stream().map(m -> new MotivoDto(m.getId(), m.getNombre(), m.getMotivoDhl().codigoDhl())).toList(); }

    @GetMapping("/articles")
    public List<ArticuloDto> articulos(@RequestParam(required = false) String q) {
        return articulos.activos().stream()
                .filter(a -> q == null || q.isBlank() || a.getReferencia().toLowerCase().contains(q.toLowerCase()) || a.getDescripcionAduanera().toLowerCase().contains(q.toLowerCase()))
                .map(a -> new ArticuloDto(a.getId(), a.getReferencia(), a.getDescripcionAduanera(), a.getCodigoHs(), a.getPaisOrigen(), a.getPesoNetoUnitario(), a.getPesoBrutoUnitario(), a.getValorUnitario())).toList();
    }
}
```

- [ ] **Step 4b: Bean Validation en la entrada de la API**

En `ShipmentInput`, anotar con los máximos del OpenAPI (importar `jakarta.validation.constraints.*` y `jakarta.validation.Valid`): `@Size(max = 60) String cliente`, `@Size(max = 60) String temporada`, `@Size(max = 35) String referencia`, `@Size(max = 1000) String notas`, `@Size(max = 120) String responsable`, `@Valid List<PackageInput> bultos`, `@Valid List<CustomsLineInput> lineasAduana`; en `PackageInput`: `@PositiveOrZero Double peso`, `@PositiveOrZero Double largo/ancho/alto`, `@Size(max = 70) String descripcion`; en `CustomsLineInput`: `@Size(max = 255) String descripcion`, `@Size(max = 20) String codigoHs`, `@Positive Integer cantidad`, `@PositiveOrZero BigDecimal valorUnitario`, `@Size(min = 2, max = 2) String paisOrigen`. En `ShipmentsApiController`, `@RequestBody @Valid ShipmentInput` en `crear` y `editar`. En `ApiExceptionHandler`:

```java
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorApi> validacion(org.springframework.web.bind.MethodArgumentNotValidException e) {
        List<String> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorApi("Datos no válidos.", detalles));
    }
```
(con `import java.util.List;`). Añadir a `ShipmentsApiTest`:
```java
    @Test
    void unaReferenciaDemasiadoLargaEs422ConElCampo() throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsable\": \"Jordi\", \"referencia\": \"" + "X".repeat(40) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detalles[0]", containsString("referencia")));
    }
```

- [ ] **Step 5: Ejecutar `ShipmentsApiTest` y la suite** → PASS. Si Jackson no serializa `LocalDate`, falta nada: Boot registra `jackson-datatype-jsr310` solo. Si `jsonPath("$.valorDeclarado").value(120.0)` falla por `120.00` vs `120.0`, usar `.value(closeTo(120.0, 0.001))` con `org.hamcrest.Matchers.closeTo` y `jsonPath(..., Double.class)`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/shipping/api src/test/java/com/puntotres/packinglist/shipping/api src/test/resources/application-test.yml
git commit -m "API REST /api/shipments con el mismo flujo que la pantalla"
```

---

### Task 13: Documentación

**Files:**
- Create: `docs/Envios DHL/README.md`
- Modify: `CLAUDE.md` (sección nueva), `README.md` (una línea en las secciones de la app), `docs/superpowers/specs/2026-09-18-envios-dhl-express-fase1-design.md` (estado → implementado)

- [ ] **Step 1: README de la feature** `docs/Envios DHL/README.md`

Contenido mínimo, en español:

```markdown
# Envíos por transportista — DHL Express (fase 1)

## Qué hace
Crea envíos con DHL Express desde `/envios`: libreta de contactos, cuentas DHL, catálogo de
artículos aduaneros, borrador → tarifa → confirmación → creación con guía y PDFs (etiqueta,
waybill, factura comercial). API REST equivalente en `/api/shipments`.

## Variables de entorno
| Variable | Qué es | Sin ella |
|---|---|---|
| `DHL_API_KEY` | API key de MyDHL API | La app arranca; tarifar/crear avisan "Faltan las credenciales de DHL" |
| `DHL_API_SECRET` | Secreto de la API key | Igual |
| `DHL_SHIPPER_ACCOUNT` | Cuenta DHL propia (9 dígitos) bajo la que se crean los envíos | Igual; además no se da de alta la cuenta propia en /envios/cuentas |
| `DHL_BASE_URL` | Opcional. Por defecto `https://express.api.dhl.com/mydhlapi/test` | Se usa el entorno de test |

Producción: `DHL_BASE_URL=https://express.api.dhl.com/mydhlapi`.

## Probar contra el entorno de test de DHL
1. Pedir credenciales de test en https://developer.dhl.com (app sobre "DHL Express - MyDHL API").
2. Exportar las cuatro variables y arrancar `mvn spring-boot:run`.
3. `/envios/contactos/importar`: subir el export CSV de la libreta de MyDHL+ (44 columnas; el fichero
   de referencia está en esta carpeta y **no** se copia al repo por llevar datos personales).
4. `/envios/cuentas`: comprobar que la cuenta propia aparece como "propia"; añadir las de los clientes que pagan.
5. `/envios` → "Nuevo envío DHL": remitente, destinatario, motivo, bultos, líneas de aduana, cuenta → Tarifar → elegir producto → Confirmar y crear.
6. Descargar la etiqueta desde la ficha. En el entorno de test la guía y los PDF son ficticios.

## Dónde está cada cosa
- Núcleo: `shipping/` (modelo, `ShipmentService`, puerto `CarrierClient`, importadores, web, api).
- Adaptador: `carrier/dhl/` (DTOs del OpenAPI 3.3.2 de esta carpeta, mappers, `DhlExpressClient`).
- Esquema: `db/migration/V1` (baseline del esquema anterior) y `V2` (envíos). Flyway; Hibernate solo valida.
- Log de DHL: logger `dhl.api` (INFO: método, ruta, código, ms; DEBUG: cuerpos sin PDF ni credenciales).

## Dudas abiertas con el contrato de DHL (buscar `TODO DHL` en el código)
- Formato exacto de `plannedShippingDateAndTime` (con o sin espacio antes de `GMT`).
- `typeCode` del identificador ICE marroquí en `registrationNumbers`.
- Número de factura comercial: ahora `PT-<año>-<id>`; si hace falta el del ERP, campo nuevo.

## Pendiente para las fases siguientes
- Seguimiento: `CarrierClient.track` (firma ya definida), tabla de eventos, notificaciones por `Notifier`
  (correo Microsoft 365, Telegram). `Shipment.urlSeguimiento` ya se guarda.
- Recogidas por API (`pickup.isRequested`), cancelación de guías en DHL, coste facturado (`costeFacturado`).
- Reports de costes por responsable, motivo y cliente (los campos ya están en `shipment`).
- Login de Microsoft: sustituir el desplegable de `envios.responsables` por el usuario autenticado.
- Otros transportistas: paquete `carrier.<nombre>` que implemente `CarrierClient` + valor en `CarrierCode`.
```

- [ ] **Step 2: CLAUDE.md**

Añadir, después de la sección de "Memoria de referencias y taras", una sección **Envíos por transportista (DHL Express)** con lo que no se deduce del código, copiando las decisiones de la spec (sección 3) en el estilo del fichero: sentido respecto a España y aduana respecto a la UE son cosas distintas y se deducen siempre; `requestId` se persiste en `CONFIRMED` antes de llamar a DHL y se reutiliza al reintentar; PDFs en base de datos; Flyway con baseline y `ddl-auto: validate` (tocar una entidad exige una migración nueva, nunca `update`); los DTOs de DHL no salen de `carrier.dhl`; el CSV de la libreta lleva datos personales y no se copia al repo; en `QUOTED` el formulario es de solo lectura y "Modificar datos" vuelve a `DRAFT`; el `TODO DHL` marca las dudas del contrato. Añadir a la lista de comandos: `mvn test -Dtest=DhlExpressClientWireMockTest`.

- [ ] **Step 3: README.md**

En la lista de secciones de la aplicación, una línea: "**Envíos por transportista** (`/envios`): envíos DHL Express con libreta, aduana, tarifa y creación; ver `docs/Envios DHL/README.md`." Y en la tabla de variables de entorno (o junto a `ANTHROPIC_API_KEY`), las cuatro de DHL.

- [ ] **Step 4: Estado de la spec**

Cambiar la línea `Estado:` de la spec a `implementado en la rama envios-dhl (fase 1)`.

- [ ] **Step 5: Suite completa y commit**

```bash
mvn -q test
git add "docs/Envios DHL/README.md" CLAUDE.md README.md docs/superpowers/specs/2026-09-18-envios-dhl-express-fase1-design.md
git commit -m "documentacion de la seccion de envios DHL: variables, pruebas contra test y pendientes"
```

---

## Verificación final antes de dar la rama por terminada

1. `mvn -q test` en verde.
2. Paso 8 de la Task 1 repetido con la base real copiada: arranca con baseline y las pantallas viejas funcionan.
3. Con credenciales de test de DHL exportadas: tarifar y crear un envío ES → MA desde la pantalla; descargar los tres PDF; comprobar en el log `dhl.api` que no aparece `Authorization` ni base64.
4. `git log --oneline main..envios-dhl` muestra un commit por tarea.
