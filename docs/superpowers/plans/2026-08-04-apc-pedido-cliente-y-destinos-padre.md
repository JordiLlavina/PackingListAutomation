# Excel de pedido en la entrada, destinos padre de APC y Livraison code — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que APC digiera bien sus inputs — destinaciones hijas que generan packing list bajo su padre, Livraison code generado y editable, y número de pedido completado desde el excel de pedido que ahora se sube en la pantalla de entrada.

**Architecture:** Se añaden tres piezas sin estado al pipeline que ya existe (`EnvioImportService` → `PaletAssignmentService` → `WeightInferenceService`): `ResolutorDestinosPadre` (fusiona hijas en su padre), `LivraisonCode` (genera el código) y `PedidoCompletionService` (completa el PO leyendo `ApcPedidoExcel`). Las tres se llaman desde `PackingListController.importar`, entre importar y asignar palets, y **solo ahí**: volver a ejecutarlas en cada submit de la revisión machacaría lo que el usuario teclee a mano. La jerarquía padre/hija y las abreviaturas son configuración de `application.yml`, no código.

**Tech Stack:** Java 17, Spring Boot 3.5 (web + thymeleaf + validation), Apache POI, JUnit 5, Maven.

## Global Constraints

- **Idioma:** clases y métodos en inglés; javadoc, comentarios, avisos al usuario y textos de UI en **español**. Los tests se llaman en español (`clienteParaNormalizaMayusculasYEspacios`).
- **Nunca fallar en silencio, nunca bloquear por datos que un humano puede resolver.** Los servicios devuelven un DTO de resultado con `avisos`, nunca lanzan excepción por datos incompletos. El excel se genera igual, con celdas en blanco.
- **Los pesos son `Double`, no `double`.** `null` = desconocido. No cambiar a primitivo.
- **Un peso por caja física, en su línea líder.** Nunca sumar las líneas de una caja.
- Los tests instancian los servicios con `new` (JUnit 5 puro). Solo llevan `@SpringBootTest` los de la capa web y los que verifican `application.yml` real.
- Los tests de builder **reabren el `.xlsx` generado con POI** y comprueban celdas reales.
- No editar las filas modelo ni los encabezados de las plantillas de `src/main/resources/client-packinglist/` ni `client-labels/`.
- Branch de trabajo: `apc-pedido-cliente-y-destinos-padre`. Un commit por tarea.
- Suite completa: `mvn test`. Una clase: `mvn test -Dtest=NombreTest`.
- Valores exactos del catálogo (copiar literal):
  - `WHOLESALE` → abreviatura `WH`, hijas `AUSTRALIA`, `WHOLESALE`, `CHINE FRANCH`
  - `RETAIL` → abreviatura `RT`, hijas `RETAIL`, `WHOLESALE CONCESS`
  - `D. USA` → `UST` · `JAPAN` → `JPT` · `KOREA` → `KRT` · `IVRY` → `IVRY`
  - `RETAIL` usa nombre-cliente `A.P.C.` y dirección `74 BIS AV MAURICE THOREZ 94200 IVRY SUR SEINE FRANCE`
  - Formato del Livraison code: `PUN` + fecha de envío `yyyyMMdd` + abreviatura + contador. Ejemplo real: `PUN20260717WH1`.

---

## File Structure

**Se crean:**

| Fichero | Responsabilidad |
|---|---|
| `service/LivraisonCode.java` | Construir el código a partir de destino + abreviatura + fecha. Puro, sin estado, sin Spring. |
| `service/ResolutorDestinosPadre.java` | Resolver hija→padre, fusionar destinaciones del mismo padre, rellenar `canal`, estampar el Livraison code. |
| `service/ResultadoDestinos.java` | DTO de resultado del anterior (destinos + avisos). |
| `service/ApcPedidoExcel.java` | Índice en memoria del excel de pedido de APC. Localiza hoja y columnas por cabecera. |
| `service/PedidoCompletionService.java` | Completar `numeroPedido` de las cajas usando `ApcPedidoExcel`. |
| `service/ResultadoPedidos.java` | DTO de resultado del anterior (avisos). |

**Se modifican:** `config/DestinoClienteConfig`, `config/ClienteConfig`, `application.yml`, `service/etiquetas/ApcEtiquetaLayout`, `service/etiquetas/CampoEtiquetas`, `service/etiquetas/AmiEtiquetasGenerador`, `service/etiquetas/ApcEtiquetasGenerador`, `web/EnvioForm`, `web/EnvioEnCurso`, `web/DestinoVista`, `web/RevisionForm`, `web/PackingListController`, `templates/entrada.html`, `templates/revision.html`, `templates/etiquetas.html`, `src/test/resources/ejemplos/envio-apc-etiquetas.json`.

**Por qué `ApcPedidoExcel` no reutiliza `HojaEan`:** `HojaEan` está clavada a hojas cuyo nombre empieza por "EAN" y la comparten los dos flujos de etiquetas de AMI (`CLAUDE.md`: tocar uno afecta a los dos). Generalizarla arriesga dos flujos en producción para ahorrar ~30 líneas. `ApcPedidoExcel` lee su propio libro.

---

### Task 1: Catálogo de destinos padre/hija en la configuración

Deja el yml y las clases de configuración capaces de expresar la jerarquía, y retira `C-LOG` de todo el repo. Sin esto ninguna tarea posterior tiene de dónde sacar la abreviatura ni el padre.

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/config/DestinoClienteConfig.java`
- Modify: `src/main/java/com/puntotres/packinglist/config/ClienteConfig.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetaLayout.java:57-61`
- Modify: `src/test/resources/ejemplos/envio-apc-etiquetas.json`
- Modify: `src/test/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetaLayoutTest.java`
- Test: `src/test/java/com/puntotres/packinglist/config/ClienteConfigTest.java` (crear)

**Interfaces:**
- Consumes: nada.
- Produces:
  - `DestinoClienteConfig.getAbreviatura() : String` (null si no configurada)
  - `DestinoClienteConfig.getDestinosHijo() : List<String>` (nunca null; vacía por defecto)
  - `ClienteConfig.isPedidoCliente() : boolean`
  - `ClienteConfig.destinoPadrePara(String destino) : Optional<ClienteConfig.DestinoResuelto>`
  - `record ClienteConfig.DestinoResuelto(String nombrePadre, DestinoClienteConfig config)`

- [ ] **Step 1: Escribir el test que falla**

Crear `src/test/java/com/puntotres/packinglist/config/ClienteConfigTest.java`:

```java
package com.puntotres.packinglist.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ClienteConfigTest {

    private static ClienteConfig apc() {
        DestinoClienteConfig wholesale = new DestinoClienteConfig();
        wholesale.setNombreCliente("A.P.C.");
        wholesale.setDireccion("CROSSLOG, 104 RUE DENIS PAPIN, 77550 MOISSY CRAMAYEL, FRANCE");
        wholesale.setAbreviatura("WH");
        wholesale.setDestinosHijo(List.of("AUSTRALIA", "WHOLESALE", "CHINE FRANCH"));

        DestinoClienteConfig ivry = new DestinoClienteConfig();
        ivry.setNombreCliente("A.P.C.");
        ivry.setDireccion("74 BIS AV MAURICE THOREZ 94200 IVRY SUR SEINE FRANCE");
        ivry.setAbreviatura("IVRY");

        ClienteConfig apc = new ClienteConfig();
        apc.setPlantilla(TipoPlantilla.APC);
        apc.setDestinos(Map.of("WHOLESALE", wholesale, "IVRY", ivry));
        return apc;
    }

    @Test
    void unaHijaResuelveASuDestinoPadre() {
        ClienteConfig.DestinoResuelto resuelto =
                apc().destinoPadrePara("Chine franch").orElseThrow();

        assertEquals("WHOLESALE", resuelto.nombrePadre());
        assertEquals("WH", resuelto.config().getAbreviatura());
    }

    @Test
    void elPadreSeResuelveASiMismoAunqueSeaTambienHijaDeSuPropiaLista() {
        ClienteConfig.DestinoResuelto resuelto =
                apc().destinoPadrePara("wholesale ").orElseThrow();

        assertEquals("WHOLESALE", resuelto.nombrePadre());
    }

    @Test
    void unDestinoSinHijasSeResuelveASiMismo() {
        assertEquals("IVRY", apc().destinoPadrePara("IVRY").orElseThrow().nombrePadre());
    }

    @Test
    void destinoDesconocidoONuloDevuelveVacio() {
        assertTrue(apc().destinoPadrePara("MARTE").isEmpty());
        assertTrue(apc().destinoPadrePara(null).isEmpty());
    }

    @Test
    void unDestinoSinHijasConfiguradasDevuelveListaVaciaNoNull() {
        assertTrue(new DestinoClienteConfig().getDestinosHijo().isEmpty());
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `mvn test -Dtest=ClienteConfigTest`
Expected: FAIL — no compila (`setAbreviatura`, `setDestinosHijo`, `destinoPadrePara` no existen).

- [ ] **Step 3: Añadir los campos a `DestinoClienteConfig`**

Añadir a `src/main/java/com/puntotres/packinglist/config/DestinoClienteConfig.java`, y ampliar su javadoc de clase con la frase «`abreviatura` y `destinos-hijo` solo los usa APC: ver `ResolutorDestinosPadre` y `LivraisonCode`»:

```java
    /**
     * Abreviatura del destino en el Livraison code de APC ("WH", "RT"...).
     * Null si no está configurada: entonces se usa el nombre del destino en
     * mayúsculas y sin espacios, con aviso.
     */
    private String abreviatura;

    /**
     * Destinaciones que el cliente agrupa bajo este destino y que se tratan
     * como si fueran él: dirección, nombre de fichero y hoja son los del
     * padre, y la hija solo sobrevive en la columna DESTINATION de su línea.
     * Vacía = este destino no agrupa a nadie.
     */
    private List<String> destinosHijo = new ArrayList<>();

    public String getAbreviatura() {
        return abreviatura;
    }

    public void setAbreviatura(String abreviatura) {
        this.abreviatura = abreviatura;
    }

    public List<String> getDestinosHijo() {
        return destinosHijo;
    }

    public void setDestinosHijo(List<String> destinosHijo) {
        this.destinosHijo = (destinosHijo == null) ? new ArrayList<>() : new ArrayList<>(destinosHijo);
    }
```

Añadir los imports `java.util.ArrayList` y `java.util.List`.

- [ ] **Step 4: Añadir `pedidoCliente` y `destinoPadrePara` a `ClienteConfig`**

En `src/main/java/com/puntotres/packinglist/config/ClienteConfig.java`:

```java
    /**
     * El cliente trabaja con un excel de pedido que el usuario sube en la
     * pantalla de entrada (APC lo usa para completar el nº de pedido; AMI
     * solo lo guarda para reutilizarlo en las etiquetas). Los demás clientes
     * no ven ese campo.
     */
    private boolean pedidoCliente;

    public boolean isPedidoCliente() {
        return pedidoCliente;
    }

    public void setPedidoCliente(boolean pedidoCliente) {
        this.pedidoCliente = pedidoCliente;
    }

    /** Un destino del catálogo resuelto desde su nombre o el de una hija suya. */
    public record DestinoResuelto(String nombrePadre, DestinoClienteConfig config) {
    }

    /**
     * Destino del catálogo bajo el que va la destinación indicada: ella misma
     * si es una clave del catálogo, o su padre si es una hija. Se mira primero
     * como clave para que un destino que además se lista como hija de sí mismo
     * (WHOLESALE) se resuelva a sí mismo sin recorrer nada.
     */
    public Optional<DestinoResuelto> destinoPadrePara(String destino) {
        if (destino == null) {
            return Optional.empty();
        }
        String buscado = normalizar(destino);
        DestinoClienteConfig directo = destinos.get(buscado);
        if (directo != null) {
            return Optional.of(new DestinoResuelto(buscado, directo));
        }
        for (Map.Entry<String, DestinoClienteConfig> entrada : destinos.entrySet()) {
            for (String hija : entrada.getValue().getDestinosHijo()) {
                if (normalizar(hija).equals(buscado)) {
                    return Optional.of(new DestinoResuelto(entrada.getKey(), entrada.getValue()));
                }
            }
        }
        return Optional.empty();
    }
```

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

Run: `mvn test -Dtest=ClienteConfigTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Actualizar el catálogo de `application.yml`**

Sustituir el bloque `"[APC]"` de `src/main/resources/application.yml` por:

```yaml
    "[APC]":
      nombre: A.P.C.
      plantilla: APC
      placeholder-temporada: E25
      # APC manda su excel de pedido de la temporada: de ahí sale el número
      # de pedido completo (ver PedidoCompletionService).
      pedido-cliente: true
      # abreviatura: la que APC usa en su Livraison code (columna
      #   "Catégorie de stock" de su excel de pedido).
      # destinos-hijo: destinaciones que viajan bajo este destino y se tratan
      #   como él; la hija solo sale en la columna DESTINATION de su línea.
      #   Añadir una hija nueva es UNA LÍNEA aquí, sin tocar Java.
      destinos:
        "[WHOLESALE]":
          nombre-cliente: A.P.C.
          direccion: CROSSLOG, 104 RUE DENIS PAPIN, 77550 MOISSY CRAMAYEL, FRANCE
          abreviatura: WH
          destinos-hijo: [AUSTRALIA, WHOLESALE, CHINE FRANCH]
        "[RETAIL]":
          nombre-cliente: A.P.C.
          direccion: 74 BIS AV MAURICE THOREZ 94200 IVRY SUR SEINE FRANCE
          abreviatura: RT
          destinos-hijo: [RETAIL, WHOLESALE CONCESS]
        "[D. USA]":
          nombre-cliente: A.P.C.-USNY-TOUITOU
          direccion: 43 BROOME STREET, 5TH FLOOR; 10013 NEW YORK; USA
          abreviatura: UST
        "[IVRY]":
          nombre-cliente: A.P.C.
          direccion: 74 BIS AV MAURICE THOREZ 94200 IVRY SUR SEINE FRANCE
          abreviatura: IVRY
        "[JAPAN]":
          nombre-cliente: A.P.C. JAPAN LTD.
          direccion: 4F AOYAMA 1-CHOME BUILDING; 8-5-30, AKASAKA,MINATO-KU; TOKYO
          abreviatura: JPT
        "[KOREA]":
          nombre-cliente: I.D.LOOK. LTD.
          direccion: 6TH FL. PUREUN BLDG. 581, GANGNAMDE-RO, SEOCHO-GU, 06530 SEOUL,SOUTH KOREA
          abreviatura: KRT
```

Y en el bloque `"[AMI]"`, justo debajo de `placeholder-temporada: H26`, añadir:

```yaml
      # AMI no usa el pedido para el packing list, pero subirlo aquí evita
      # que el Paso 2 de etiquetas lo vuelva a pedir.
      pedido-cliente: true
```

`C-LOG` desaparece: era el nombre viejo de WHOLESALE, no un destino aparte.

- [ ] **Step 7: Migrar `C-LOG` en `ApcEtiquetaLayout` y en el fixture**

En `src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetaLayout.java`, sustituir el mapa y su javadoc:

```java
    /**
     * Se aceptan la clave del catálogo de packing (D. USA, WHOLESALE) y el
     * nombre de la plantilla del cliente (USA, WH CROSSLOG).
     */
    private static final Map<String, ApcEtiquetaLayout> POR_DESTINO = Map.of(
            "JAPAN", JAPAN,
            "KOREA", KOREA,
            "D. USA", USA, "USA", USA,
            "WHOLESALE", WH_CROSSLOG, "WH CROSSLOG", WH_CROSSLOG);
```

En `src/test/resources/ejemplos/envio-apc-etiquetas.json`, cambiar la destinación `"destino" : "C-LOG"` por `"destino" : "Wholesale"`. Es la única aparición.

En `src/test/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetaLayoutTest.java`, sustituir cualquier aserción sobre `"C-LOG"` por `"WHOLESALE"` (mismo layout esperado, `WH_CROSSLOG`).

- [ ] **Step 8: Ejecutar la suite completa**

Run: `mvn test`
Expected: PASS. Vigilar `EjemplosJsonTest` (usa el catálogo real del yml) y `ApcEtiquetaLayoutTest`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/config/DestinoClienteConfig.java \
        src/main/java/com/puntotres/packinglist/config/ClienteConfig.java \
        src/main/resources/application.yml \
        src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetaLayout.java \
        src/test/resources/ejemplos/envio-apc-etiquetas.json \
        src/test/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetaLayoutTest.java \
        src/test/java/com/puntotres/packinglist/config/ClienteConfigTest.java
git commit -m "el catalogo de APC declara destinos padre con sus hijas y su abreviatura"
```

---

### Task 2: `LivraisonCode`

Construye el código. Puro y sin dependencias para poder probar el formato sin montar un envío.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/LivraisonCode.java`
- Test: `src/test/java/com/puntotres/packinglist/service/LivraisonCodeTest.java`

**Interfaces:**
- Consumes: `DestinoClienteConfig.getAbreviatura()` (Task 1).
- Produces: `LivraisonCode.generar(String nombrePadre, DestinoClienteConfig config, String fechaEnvio, List<String> avisos) : String` — devuelve el código completo y **añade** avisos a la lista recibida.

- [ ] **Step 1: Escribir el test que falla**

Crear `src/test/java/com/puntotres/packinglist/service/LivraisonCodeTest.java`:

```java
package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.DestinoClienteConfig;

class LivraisonCodeTest {

    private static DestinoClienteConfig destino(String abreviatura) {
        DestinoClienteConfig config = new DestinoClienteConfig();
        config.setAbreviatura(abreviatura);
        return config;
    }

    @Test
    void componeElCodigoConLaEstructuraDelCliente() {
        List<String> avisos = new ArrayList<>();

        String codigo = LivraisonCode.generar("WHOLESALE", destino("WH"), "28/04/2026", avisos);

        assertEquals("PUN20260428WH1", codigo);
        assertTrue(avisos.isEmpty());
    }

    @Test
    void sinAbreviaturaUsaElNombreEnMayusculasSinEspaciosYAvisa() {
        List<String> avisos = new ArrayList<>();

        String codigo = LivraisonCode.generar("D. USA", destino(null), "17/07/2026", avisos);

        assertEquals("PUN20260717D.USA1", codigo);
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).contains("D. USA"));
    }

    @Test
    void unaFechaIlegibleNoRompeElEnvio() {
        List<String> avisos = new ArrayList<>();

        String codigo = LivraisonCode.generar("WHOLESALE", destino("WH"), "no es fecha", avisos);

        assertEquals("PUNWH1", codigo);
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).contains("fecha de envío"));
    }

    @Test
    void fechaNulaSeTrataComoIlegible() {
        List<String> avisos = new ArrayList<>();

        assertEquals("PUNWH1", LivraisonCode.generar("WHOLESALE", destino("WH"), null, avisos));
        assertEquals(1, avisos.size());
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `mvn test -Dtest=LivraisonCodeTest`
Expected: FAIL — no compila, `LivraisonCode` no existe.

- [ ] **Step 3: Implementar `LivraisonCode`**

Crear `src/main/java/com/puntotres/packinglist/service/LivraisonCode.java`:

```java
package com.puntotres.packinglist.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import com.puntotres.packinglist.config.DestinoClienteConfig;

/**
 * Livraison code de APC: {@code "PUN" + fecha de envío (yyyyMMdd) +
 * abreviatura del destino padre + contador}, p. ej. {@code PUN20260717WH1}.
 * El formato está tomado de la etiqueta de caja real del cliente.
 *
 * El contador arranca siempre en 1 porque la aplicación NO puede saber el
 * correcto: no hay historial de envíos. Por eso el código se muestra entero
 * y editable en la pantalla de revisión.
 *
 * Nada de aquí lanza: una fecha ilegible o una abreviatura sin configurar
 * dan un código degradado y un aviso, nunca un envío bloqueado.
 */
public final class LivraisonCode {

    private static final String PREFIJO = "PUN";
    private static final String CONTADOR_INICIAL = "1";
    private static final DateTimeFormatter FORMATO_ENTRADA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_CODIGO = DateTimeFormatter.ofPattern("yyyyMMdd");

    private LivraisonCode() {
    }

    public static String generar(String nombrePadre, DestinoClienteConfig config,
                                 String fechaEnvio, List<String> avisos) {
        return PREFIJO + fecha(fechaEnvio, avisos) + abreviatura(nombrePadre, config, avisos)
                + CONTADOR_INICIAL;
    }

    private static String fecha(String fechaEnvio, List<String> avisos) {
        if (fechaEnvio == null || fechaEnvio.isBlank()) {
            avisos.add("Sin fecha de envío: el Livraison code sale sin fecha, corrígelo a mano");
            return "";
        }
        try {
            return LocalDate.parse(fechaEnvio.trim(), FORMATO_ENTRADA).format(FORMATO_CODIGO);
        } catch (DateTimeParseException e) {
            avisos.add("La fecha de envío '" + fechaEnvio + "' no es dd/MM/yyyy: "
                    + "el Livraison code sale sin fecha, corrígelo a mano");
            return "";
        }
    }

    /**
     * Sin abreviatura configurada se cae al nombre del destino en mayúsculas
     * y sin espacios, igual que hace AmiNombreFichero con una destinación
     * desconocida: un destino mal configurado no debe impedir generar.
     */
    private static String abreviatura(String nombrePadre, DestinoClienteConfig config,
                                      List<String> avisos) {
        if (config != null && config.getAbreviatura() != null && !config.getAbreviatura().isBlank()) {
            return config.getAbreviatura().trim();
        }
        avisos.add("El destino '" + nombrePadre + "' no tiene abreviatura configurada: "
                + "el Livraison code usa su nombre, revísalo");
        return nombrePadre == null ? ""
                : nombrePadre.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `mvn test -Dtest=LivraisonCodeTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/LivraisonCode.java \
        src/test/java/com/puntotres/packinglist/service/LivraisonCodeTest.java
git commit -m "generar el Livraison code de APC a partir del destino y la fecha de envio"
```

---

### Task 3: `ResolutorDestinosPadre`

Fusiona las hijas en su padre, avisa de números repetidos y estampa el Livraison code.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/ResultadoDestinos.java`
- Create: `src/main/java/com/puntotres/packinglist/service/ResolutorDestinosPadre.java`
- Test: `src/test/java/com/puntotres/packinglist/service/ResolutorDestinosPadreTest.java`

**Interfaces:**
- Consumes: `ClienteConfig.destinoPadrePara(String)` (Task 1), `LivraisonCode.generar(...)` (Task 2), `EnvioImportado.DestinoImportado`.
- Produces:
  - `ResolutorDestinosPadre.resolver(List<EnvioImportado.DestinoImportado> destinos, ClienteConfig cliente, String fechaEnvio) : ResultadoDestinos`
  - `ResultadoDestinos.getDestinos() : List<EnvioImportado.DestinoImportado>`
  - `ResultadoDestinos.getAvisos() : List<String>`

- [ ] **Step 1: Escribir el test que falla**

Crear `src/test/java/com/puntotres/packinglist/service/ResolutorDestinosPadreTest.java`:

```java
package com.puntotres.packinglist.service;

import static com.puntotres.packinglist.testutil.TestDatos.caja;
import static com.puntotres.packinglist.testutil.TestDatos.palet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.DestinoClienteConfig;
import com.puntotres.packinglist.config.TipoPlantilla;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

class ResolutorDestinosPadreTest {

    private final ResolutorDestinosPadre resolutor = new ResolutorDestinosPadre();

    private static ClienteConfig apc() {
        DestinoClienteConfig wholesale = new DestinoClienteConfig();
        wholesale.setNombreCliente("A.P.C.");
        wholesale.setDireccion("CROSSLOG");
        wholesale.setAbreviatura("WH");
        wholesale.setDestinosHijo(List.of("AUSTRALIA", "WHOLESALE", "CHINE FRANCH"));

        DestinoClienteConfig japan = new DestinoClienteConfig();
        japan.setNombreCliente("A.P.C. JAPAN LTD.");
        japan.setDireccion("TOKYO");
        japan.setAbreviatura("JPT");

        ClienteConfig apc = new ClienteConfig();
        apc.setNombre("A.P.C.");
        apc.setPlantilla(TipoPlantilla.APC);
        Map<String, DestinoClienteConfig> destinos = new LinkedHashMap<>();
        destinos.put("WHOLESALE", wholesale);
        destinos.put("JAPAN", japan);
        apc.setDestinos(destinos);
        return apc;
    }

    /** Cliente sin catálogo de destinos, como AMI o los genéricos. */
    private static ClienteConfig ami() {
        ClienteConfig ami = new ClienteConfig();
        ami.setNombre("AMI");
        ami.setPlantilla(TipoPlantilla.AMI);
        return ami;
    }

    private static EnvioImportado.DestinoImportado destino(String nombre, List<CajaData> cajas,
                                                           List<PaletData> palets) {
        DestinoData datos = new DestinoData();
        datos.setNombreDestino(nombre);
        datos.setCajas(new ArrayList<>(cajas));
        return new EnvioImportado.DestinoImportado(datos, new ArrayList<>(palets));
    }

    @Test
    void unaHijaTomaElNombreDelPadreYDejaSuNombreEnElCanal() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        ResultadoDestinos resultado = resolutor.resolver(
                List.of(destino("Australia", List.of(linea), List.of(palet("Australia", 1, 1, 4)))),
                apc(), "28/04/2026");

        assertEquals(1, resultado.getDestinos().size());
        assertEquals("WHOLESALE", resultado.getDestinos().get(0).getDestino().getNombreDestino());
        assertEquals("AUSTRALIA", linea.getCanal());
    }

    @Test
    void elCanalQueYaVieneEnElJsonManda() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        linea.setCanal("DOUANES USA");

        resolutor.resolver(List.of(destino("Australia", List.of(linea), List.of())),
                apc(), "28/04/2026");

        assertEquals("DOUANES USA", linea.getCanal());
    }

    @Test
    void todasLasCajasDelPadreLlevanSuLivraisonCode() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        resolutor.resolver(List.of(destino("Chine franch", List.of(linea), List.of())),
                apc(), "28/04/2026");

        assertEquals("PUN20260428WH1", linea.getLivraisonCode());
    }

    @Test
    void dosHijasDelMismoPadreSeFusionanEnUnSoloDestino() {
        CajaData deAustralia = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        CajaData deChina = caja(9, "719", "PXBHZ-F65101", "LZZ-NOIR", 1, null, 3.5);

        ResultadoDestinos resultado = resolutor.resolver(List.of(
                destino("Australia", List.of(deAustralia), List.of(palet("Australia", 1, 1, 4))),
                destino("Chine franch", List.of(deChina), List.of(palet("Chine franch", 2, 9, 9)))),
                apc(), "28/04/2026");

        assertEquals(1, resultado.getDestinos().size());
        EnvioImportado.DestinoImportado fusionado = resultado.getDestinos().get(0);
        assertEquals("WHOLESALE", fusionado.getDestino().getNombreDestino());
        assertEquals(2, fusionado.getDestino().getCajas().size());
        assertEquals(2, fusionado.getPalets().size());
        assertTrue(resultado.getAvisos().isEmpty());
    }

    @Test
    void avisaDeLosNumerosDeCajaRepetidosEntreHijasSinRenumerar() {
        CajaData deAustralia = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        CajaData deChina = caja(1, "719", "PXBHZ-F65101", "LZZ-NOIR", 1, null, 3.5);

        ResultadoDestinos resultado = resolutor.resolver(List.of(
                destino("Australia", List.of(deAustralia), List.of()),
                destino("Chine franch", List.of(deChina), List.of())),
                apc(), "28/04/2026");

        assertEquals(1, deAustralia.getNumeroCaja());
        assertEquals(1, deChina.getNumeroCaja());
        assertEquals(1, resultado.getAvisos().size());
        assertTrue(resultado.getAvisos().get(0).contains("caja 1"));
        assertTrue(resultado.getAvisos().get(0).contains("Australia"));
        assertTrue(resultado.getAvisos().get(0).contains("Chine franch"));
    }

    @Test
    void avisaDeLosPaletsRepetidosEntreHijasSinRenumerar() {
        CajaData deAustralia = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        CajaData deChina = caja(9, "719", "PXBHZ-F65101", "LZZ-NOIR", 1, null, 3.5);

        ResultadoDestinos resultado = resolutor.resolver(List.of(
                destino("Australia", List.of(deAustralia), List.of(palet("Australia", 1, 1, 4))),
                destino("Chine franch", List.of(deChina), List.of(palet("Chine franch", 1, 9, 9)))),
                apc(), "28/04/2026");

        assertEquals(1, resultado.getAvisos().size());
        assertTrue(resultado.getAvisos().get(0).contains("palet 1"));
    }

    @Test
    void unClienteSinCatalogoDeDestinosPasaIntacto() {
        CajaData linea = caja(1, "PO", "ULL728", "NOIR", 3, null, 4.2);
        ResultadoDestinos resultado = resolutor.resolver(
                List.of(destino("CHINA", List.of(linea), List.of())), ami(), "28/04/2026");

        assertEquals("CHINA", resultado.getDestinos().get(0).getDestino().getNombreDestino());
        assertEquals(null, linea.getLivraisonCode());
        assertTrue(resultado.getAvisos().isEmpty());
    }

    @Test
    void unDestinoFueraDelCatalogoSeDejaComoEstaParaQueAviseLaGeneracion() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);
        ResultadoDestinos resultado = resolutor.resolver(
                List.of(destino("MARTE", List.of(linea), List.of())), apc(), "28/04/2026");

        assertEquals("MARTE", resultado.getDestinos().get(0).getDestino().getNombreDestino());
        assertEquals(null, linea.getLivraisonCode());
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `mvn test -Dtest=ResolutorDestinosPadreTest`
Expected: FAIL — no compila, `ResolutorDestinosPadre` y `ResultadoDestinos` no existen.

- [ ] **Step 3: Crear el DTO de resultado**

Crear `src/main/java/com/puntotres/packinglist/service/ResultadoDestinos.java`:

```java
package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de resolver las destinaciones de un envío contra el catálogo del
 * cliente: la lista ya fusionada por destino padre y los avisos de lo que el
 * usuario tiene que mirar (números de caja o de palet repetidos entre hijas,
 * abreviatura sin configurar, fecha ilegible).
 */
public class ResultadoDestinos {

    private final List<EnvioImportado.DestinoImportado> destinos = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();

    public List<EnvioImportado.DestinoImportado> getDestinos() { return destinos; }
    public List<String> getAvisos() { return avisos; }
}
```

- [ ] **Step 4: Implementar `ResolutorDestinosPadre`**

Crear `src/main/java/com/puntotres/packinglist/service/ResolutorDestinosPadre.java`:

```java
package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.PaletData;

/**
 * Deja las destinaciones de un envío listas para generar: resuelve cada una
 * a su destino padre del catálogo del cliente, fusiona en uno solo las que
 * comparten padre y les estampa su Livraison code.
 *
 * Vive fuera de {@link EnvioImportService} a propósito: el importador traduce
 * el JSON a dominio y no conoce el catálogo de clientes. Se llama desde el
 * controlador ANTES de asignar palets, para que la asignación y la inferencia
 * trabajen ya sobre las destinaciones definitivas.
 *
 * <b>Nunca renumera cajas ni palets.</b> Si dos hijas del mismo padre traen
 * el mismo número de caja, se avisa y se dejan como están: el bulto lleva ese
 * número pegado físicamente, y el packing list tiene que coincidir con la
 * etiqueta, no al revés. Se corrige en la revisión, donde caja y palet son
 * editables.
 *
 * Un cliente sin catálogo de destinos (AMI, genéricos) pasa intacto.
 */
@Service
public class ResolutorDestinosPadre {

    public ResultadoDestinos resolver(List<EnvioImportado.DestinoImportado> destinos,
                                      ClienteConfig cliente, String fechaEnvio) {
        ResultadoDestinos resultado = new ResultadoDestinos();
        if (cliente == null || cliente.getDestinos().isEmpty()) {
            resultado.getDestinos().addAll(destinos);
            return resultado;
        }

        // Clave = nombre del padre; valor = las hijas que van bajo él, en el
        // orden en que llegaron. Los destinos sin padre conocido conservan su
        // sitio con su propio nombre como clave.
        Map<String, List<EnvioImportado.DestinoImportado>> porPadre = new LinkedHashMap<>();
        Map<String, ClienteConfig.DestinoResuelto> configPorPadre = new LinkedHashMap<>();

        for (EnvioImportado.DestinoImportado importado : destinos) {
            String nombreHija = importado.getDestino().getNombreDestino();
            Optional<ClienteConfig.DestinoResuelto> resuelto =
                    cliente.destinoPadrePara(nombreHija);
            String clave = resuelto.map(ClienteConfig.DestinoResuelto::nombrePadre)
                    .orElse(nombreHija);
            resuelto.ifPresent(valor -> configPorPadre.put(clave, valor));
            // El canal (columna DESTINATION) es lo único donde sobrevive la
            // hija. Si el JSON ya trae uno, manda el JSON.
            if (resuelto.isPresent()) {
                for (CajaData caja : importado.getDestino().getCajas()) {
                    if (caja.getCanal() == null || caja.getCanal().isBlank()) {
                        caja.setCanal(nombreHija.trim().toUpperCase());
                    }
                }
            }
            porPadre.computeIfAbsent(clave, k -> new ArrayList<>()).add(importado);
        }

        porPadre.forEach((nombrePadre, hijas) -> {
            EnvioImportado.DestinoImportado fusionado =
                    fusionar(nombrePadre, hijas, resultado.getAvisos());
            ClienteConfig.DestinoResuelto config = configPorPadre.get(nombrePadre);
            if (config != null) {
                String codigo = LivraisonCode.generar(nombrePadre, config.config(),
                        fechaEnvio, resultado.getAvisos());
                fusionado.getDestino().getCajas()
                        .forEach(caja -> caja.setLivraisonCode(codigo));
            }
            resultado.getDestinos().add(fusionado);
        });
        return resultado;
    }

    /**
     * Una sola destinación con las cajas y los palets de todas sus hijas, en
     * el orden en que llegaron. Con una única hija se reutiliza su objeto:
     * no hay nada que fusionar y así se conservan sus listas tal cual.
     */
    private EnvioImportado.DestinoImportado fusionar(
            String nombrePadre, List<EnvioImportado.DestinoImportado> hijas, List<String> avisos) {
        if (hijas.size() == 1) {
            hijas.get(0).getDestino().setNombreDestino(nombrePadre);
            hijas.get(0).getPalets().forEach(palet -> palet.setDestino(nombrePadre));
            return hijas.get(0);
        }

        avisarDeNumerosRepetidos(nombrePadre, hijas, avisos);

        List<CajaData> cajas = new ArrayList<>();
        List<PaletData> palets = new ArrayList<>();
        for (EnvioImportado.DestinoImportado hija : hijas) {
            cajas.addAll(hija.getDestino().getCajas());
            hija.getPalets().forEach(palet -> palet.setDestino(nombrePadre));
            palets.addAll(hija.getPalets());
        }
        DestinoData destino = new DestinoData();
        destino.setNombreDestino(nombrePadre);
        destino.setCajas(cajas);
        return new EnvioImportado.DestinoImportado(destino, palets);
    }

    private void avisarDeNumerosRepetidos(String nombrePadre,
                                          List<EnvioImportado.DestinoImportado> hijas,
                                          List<String> avisos) {
        avisarDe("caja", nombrePadre, hijas, avisos, hija -> {
            Set<Integer> numeros = new TreeSet<>();
            hija.getDestino().getCajas().forEach(caja -> numeros.add(caja.getNumeroCaja()));
            return numeros;
        });
        avisarDe("palet", nombrePadre, hijas, avisos, hija -> {
            Set<Integer> numeros = new TreeSet<>();
            hija.getPalets().forEach(palet -> numeros.add(palet.getNumeroPalet()));
            return numeros;
        });
    }

    private void avisarDe(String queEs, String nombrePadre,
                          List<EnvioImportado.DestinoImportado> hijas, List<String> avisos,
                          java.util.function.Function<EnvioImportado.DestinoImportado,
                                  Set<Integer>> numerosDe) {
        Map<Integer, Set<String>> hijasPorNumero = new LinkedHashMap<>();
        for (EnvioImportado.DestinoImportado hija : hijas) {
            for (Integer numero : numerosDe.apply(hija)) {
                hijasPorNumero.computeIfAbsent(numero, n -> new LinkedHashSet<>())
                        .add(hija.getDestino().getNombreDestino());
            }
        }
        hijasPorNumero.forEach((numero, nombres) -> {
            if (nombres.size() > 1) {
                avisos.add(nombrePadre + ": el " + queEs + " " + numero + " llega de "
                        + String.join(" y de ", nombres)
                        + "; se fusionan sin renumerar, revísalo en la tabla");
            }
        });
    }
}
```

Ojo: en `fusionar` con una sola hija se llama a `setNombreDestino` **después**
de haber leído el nombre original para el canal y para los avisos. El orden
del método `resolver` ya lo garantiza (el canal se rellena en el primer bucle).

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

Run: `mvn test -Dtest=ResolutorDestinosPadreTest`
Expected: PASS (8 tests).

Si `avisaDeLosNumerosDeCajaRepetidosEntreHijasSinRenumerar` falla porque el
aviso dice "WHOLESALE" en vez de "Australia": el aviso se construye con
`hija.getDestino().getNombreDestino()` **antes** de renombrar, y con dos hijas
el renombrado ocurre al crear el `DestinoData` nuevo, así que el nombre viejo
sigue disponible. Verificar que `avisarDeNumerosRepetidos` se llama antes del
bucle que copia cajas y palets.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/ResolutorDestinosPadre.java \
        src/main/java/com/puntotres/packinglist/service/ResultadoDestinos.java \
        src/test/java/com/puntotres/packinglist/service/ResolutorDestinosPadreTest.java
git commit -m "las destinaciones hijas de APC se generan bajo su destino padre"
```

---

### Task 4: `ApcPedidoExcel`

Lector del excel de pedido de APC, anclado al fichero real del cliente.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/ApcPedidoExcel.java`
- Create: `src/test/resources/ejemplos/APC_PEDIDO_FALL26.xlsx` (copia del real)
- Test: `src/test/java/com/puntotres/packinglist/service/ApcPedidoExcelTest.java`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `ApcPedidoExcel.desdeBytes(byte[] contenido) : ApcPedidoExcel` (lanza `IOException` si el fichero no es un `.xlsx` legible)
  - `ApcPedidoExcel.pedidoCompleto(String referencia, String pedidoParcial) : Optional<String>`
  - `ApcPedidoExcel.avisos() : List<String>`

- [ ] **Step 1: Copiar el fichero real a los recursos de test**

```bash
cp "docs/Etiquetas cajas/APC_PEDIDO_FALL26.xlsx" \
   "src/test/resources/ejemplos/APC_PEDIDO_FALL26.xlsx"
```

Es un **dato real de cliente**, no un fixture inventado: no editarlo.

- [ ] **Step 2: Escribir el test que falla**

Crear `src/test/java/com/puntotres/packinglist/service/ApcPedidoExcelTest.java`:

```java
package com.puntotres.packinglist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Contra el excel de pedido REAL de APC (APC_PEDIDO_FALL26.xlsx, 130 líneas).
 * El libro trae cuatro hojas y dos de ellas (LCT y Sheet2) también tienen
 * "Article" y "Document d'achat": la hoja buena se distingue porque además
 * lleva "Notre référence".
 */
class ApcPedidoExcelTest {

    private static ApcPedidoExcel pedidoReal() throws IOException {
        try (InputStream in = ApcPedidoExcelTest.class
                .getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            return ApcPedidoExcel.desdeBytes(in.readAllBytes());
        }
    }

    @Test
    void encuentraElPedidoCompletoPorReferenciaYTresDigitos() throws IOException {
        assertEquals(Optional.of("4100128721"),
                pedidoReal().pedidoCompleto("PXBHZ-H65077", "721"));
    }

    @Test
    void laMismaReferenciaEnOtroPedidoDaOtroNumero() throws IOException {
        ApcPedidoExcel pedido = pedidoReal();

        assertEquals(Optional.of("4100128707"), pedido.pedidoCompleto("PXBHZ-H65077", "707"));
        assertEquals(Optional.of("4100128720"), pedido.pedidoCompleto("PXBHZ-H65077", "720"));
    }

    @Test
    void unNumeroYaCompletoEncuentraSuPropiaFila() throws IOException {
        assertEquals(Optional.of("4100128721"),
                pedidoReal().pedidoCompleto("PXBHZ-H65077", "4100128721"));
    }

    @Test
    void laReferenciaSeNormalizaEnMayusculasYSinEspacios() throws IOException {
        assertEquals(Optional.of("4100128721"),
                pedidoReal().pedidoCompleto(" pxbhz-h65077 ", "721"));
    }

    @Test
    void referenciaOTresDigitosQueNoExistenNoDevuelvenNada() throws IOException {
        ApcPedidoExcel pedido = pedidoReal();

        assertTrue(pedido.pedidoCompleto("NO-EXISTE", "721").isEmpty());
        assertTrue(pedido.pedidoCompleto("PXBHZ-H65077", "999").isEmpty());
        assertTrue(pedido.pedidoCompleto(null, "721").isEmpty());
        assertTrue(pedido.pedidoCompleto("PXBHZ-H65077", null).isEmpty());
    }

    @Test
    void elFicheroRealNoTieneNingunaClaveAmbigua() throws IOException {
        assertTrue(pedidoReal().avisos().isEmpty(),
                "referencia + 3 dígitos identifica una sola fila en el pedido real");
    }
}
```

- [ ] **Step 3: Ejecutar el test y verificar que falla**

Run: `mvn test -Dtest=ApcPedidoExcelTest`
Expected: FAIL — no compila, `ApcPedidoExcel` no existe.

- [ ] **Step 4: Implementar `ApcPedidoExcel`**

Crear `src/main/java/com/puntotres/packinglist/service/ApcPedidoExcel.java`:

```java
package com.puntotres.packinglist.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Índice en memoria del excel de pedido de la temporada de APC
 * (APC_PEDIDO_FALL26.xlsx), del que sale el número de pedido COMPLETO.
 *
 * Lo que llega de la imagen o del formulario son los <b>tres últimos
 * dígitos</b> del pedido. La clave de búsqueda es por tanto
 * {@code Article + esos tres dígitos}: en el fichero real esa clave
 * identifica una única fila de las 130, mientras que la referencia sola no
 * vale (PXCBC-F63023 aparece en 8 pedidos distintos).
 *
 * La hoja buena es la primera cuya fila de cabecera trae las TRES columnas
 * {@code Article}, {@code Document d'achat} y {@code Notre référence}. Las
 * tres, no dos: el libro trae otras hojas ("LCT", "Sheet2") con las dos
 * primeras que darían la hoja equivocada. Las columnas se localizan por el
 * texto de su cabecera, nunca por posición, y por prefijo para no depender de
 * acentos ni del tipo de apóstrofo.
 *
 * Como en los escandallos del ERP, hay celdas que POI devuelve como null o
 * vacías: la lectura no asume que haya texto.
 */
public final class ApcPedidoExcel {

    private static final String CABECERA_ARTICULO = "ARTICLE";
    private static final String CABECERA_PEDIDO = "DOCUMENT D";
    private static final String CABECERA_DESTINO = "NOTRE R";
    private static final int DIGITOS_PARCIALES = 3;

    /** clave "REFERENCIA|3 dígitos" -> número de pedido completo. */
    private final Map<String, String> pedidosPorClave;
    private final List<String> avisos;

    private ApcPedidoExcel(Map<String, String> pedidosPorClave, List<String> avisos) {
        this.pedidosPorClave = Map.copyOf(pedidosPorClave);
        this.avisos = List.copyOf(avisos);
    }

    public static ApcPedidoExcel desdeBytes(byte[] contenido) throws IOException {
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet hoja = hojaDePedido(libro);
            Row cabecera = hoja.getRow(hoja.getFirstRowNum());
            int colArticulo = columna(cabecera, CABECERA_ARTICULO);
            int colPedido = columna(cabecera, CABECERA_PEDIDO);

            Map<String, String> pedidos = new LinkedHashMap<>();
            Set<String> ambiguas = new LinkedHashSet<>();
            for (int fila = hoja.getFirstRowNum() + 1; fila <= hoja.getLastRowNum(); fila++) {
                String referencia = texto(hoja, fila, colArticulo);
                String pedido = texto(hoja, fila, colPedido);
                if (referencia.isBlank() || pedido.isBlank()) {
                    continue;
                }
                String clave = clave(referencia, pedido);
                String anterior = pedidos.put(clave, pedido.trim());
                if (anterior != null && !anterior.equals(pedido.trim())) {
                    ambiguas.add(clave);
                }
            }

            List<String> avisos = new ArrayList<>();
            for (String clave : ambiguas) {
                pedidos.remove(clave);
                avisos.add("En el excel de pedido, " + clave.replace("|", " + ")
                        + " apunta a más de un pedido: esas líneas se quedan como llegaron");
            }
            return new ApcPedidoExcel(pedidos, avisos);
        }
    }

    /**
     * Número de pedido completo de una línea, o vacío si no hay fila que case.
     * Funciona igual si {@code pedidoParcial} ya viene completo: sus tres
     * últimos dígitos encuentran la misma fila.
     */
    public Optional<String> pedidoCompleto(String referencia, String pedidoParcial) {
        if (referencia == null || referencia.isBlank()
                || pedidoParcial == null || pedidoParcial.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pedidosPorClave.get(clave(referencia, pedidoParcial)));
    }

    /** Avisos de nivel de fichero (claves ambiguas). Nunca null. */
    public List<String> avisos() {
        return avisos;
    }

    private static String clave(String referencia, String pedido) {
        return referencia.trim().toUpperCase(Locale.ROOT) + "|" + sufijo(pedido);
    }

    /** Los tres últimos caracteres del pedido, o el pedido entero si es más corto. */
    private static String sufijo(String pedido) {
        String limpio = pedido.trim();
        return limpio.length() <= DIGITOS_PARCIALES
                ? limpio
                : limpio.substring(limpio.length() - DIGITOS_PARCIALES);
    }

    private static Sheet hojaDePedido(Workbook libro) {
        for (int i = 0; i < libro.getNumberOfSheets(); i++) {
            Sheet hoja = libro.getSheetAt(i);
            Row cabecera = hoja.getRow(hoja.getFirstRowNum());
            if (cabecera == null) {
                continue;
            }
            if (columnaOpcional(cabecera, CABECERA_ARTICULO) >= 0
                    && columnaOpcional(cabecera, CABECERA_PEDIDO) >= 0
                    && columnaOpcional(cabecera, CABECERA_DESTINO) >= 0) {
                return hoja;
            }
        }
        throw new IllegalArgumentException("El excel de pedido de APC no tiene ninguna hoja con "
                + "las columnas 'Article', 'Document d'achat' y 'Notre référence': "
                + "¿es el archivo correcto?");
    }

    private static int columna(Row cabecera, String prefijo) {
        int indice = columnaOpcional(cabecera, prefijo);
        if (indice < 0) {
            throw new IllegalArgumentException(
                    "El excel de pedido de APC no tiene la columna '" + prefijo + "'");
        }
        return indice;
    }

    private static int columnaOpcional(Row cabecera, String prefijo) {
        for (Cell celda : cabecera) {
            if (texto(celda).trim().toUpperCase(Locale.ROOT).startsWith(prefijo)) {
                return celda.getColumnIndex();
            }
        }
        return -1;
    }

    private static String texto(Sheet hoja, int fila, int columna) {
        Row f = hoja.getRow(fila);
        return f == null ? "" : texto(f.getCell(columna));
    }

    /**
     * Texto de una celda, "" si no hay nada. Los numéricos se leen como
     * enteros: un pedido guardado como número no debe salir "4100128721.0".
     */
    private static String texto(Cell celda) {
        if (celda == null) {
            return "";
        }
        String valor = switch (celda.getCellType()) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> String.valueOf((long) celda.getNumericCellValue());
            case FORMULA -> celda.getCachedFormulaResultType() == CellType.STRING
                    ? celda.getStringCellValue() : "";
            default -> "";
        };
        return valor == null ? "" : valor;
    }
}
```

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

Run: `mvn test -Dtest=ApcPedidoExcelTest`
Expected: PASS (6 tests). El último (`elFicheroRealNoTieneNingunaClaveAmbigua`)
es el que ancla que la regla de los tres dígitos vale para el fichero real.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/ApcPedidoExcel.java \
        src/test/java/com/puntotres/packinglist/service/ApcPedidoExcelTest.java \
        "src/test/resources/ejemplos/APC_PEDIDO_FALL26.xlsx"
git commit -m "leer el excel de pedido de APC por referencia y tres digitos"
```

---

### Task 5: `PedidoCompletionService`

Aplica el índice sobre las cajas del envío.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/ResultadoPedidos.java`
- Create: `src/main/java/com/puntotres/packinglist/service/PedidoCompletionService.java`
- Test: `src/test/java/com/puntotres/packinglist/service/PedidoCompletionServiceTest.java`

**Interfaces:**
- Consumes: `ApcPedidoExcel.desdeBytes(byte[])`, `ApcPedidoExcel.pedidoCompleto(String, String)`, `ApcPedidoExcel.avisos()` (Task 4).
- Produces:
  - `PedidoCompletionService.completar(List<EnvioImportado.DestinoImportado> destinos, byte[] excelPedido) : ResultadoPedidos`
  - `ResultadoPedidos.getAvisos() : List<String>`

- [ ] **Step 1: Escribir el test que falla**

Crear `src/test/java/com/puntotres/packinglist/service/PedidoCompletionServiceTest.java`:

```java
package com.puntotres.packinglist.service;

import static com.puntotres.packinglist.testutil.TestDatos.caja;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DestinoData;

class PedidoCompletionServiceTest {

    private final PedidoCompletionService servicio = new PedidoCompletionService();

    private static byte[] pedidoReal() throws IOException {
        try (InputStream in = PedidoCompletionServiceTest.class
                .getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            return in.readAllBytes();
        }
    }

    private static List<EnvioImportado.DestinoImportado> envioCon(CajaData... cajas) {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("WHOLESALE");
        destino.setCajas(new ArrayList<>(List.of(cajas)));
        return List.of(new EnvioImportado.DestinoImportado(destino, List.of()));
    }

    @Test
    void completaElNumeroDePedidoDeCadaCaja() throws IOException {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado = servicio.completar(envioCon(linea), pedidoReal());

        assertEquals("4100128721", linea.getNumeroPedido());
        assertTrue(resultado.getAvisos().isEmpty());
    }

    @Test
    void sinExcelDePedidoAvisaUnaVezYNoTocaNada() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado = servicio.completar(envioCon(linea), null);

        assertEquals("721", linea.getNumeroPedido());
        assertEquals(1, resultado.getAvisos().size());
    }

    @Test
    void unaReferenciaQueNoEstaEnElPedidoAvisaUnaSolaVezPorClave() throws IOException {
        CajaData una = caja(1, "999", "NO-EXISTE", "LZZ-NOIR", 3, null, 4.2);
        CajaData otra = caja(2, "999", "NO-EXISTE", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado = servicio.completar(envioCon(una, otra), pedidoReal());

        assertEquals("999", una.getNumeroPedido());
        assertEquals(1, resultado.getAvisos().size());
        assertTrue(resultado.getAvisos().get(0).contains("NO-EXISTE"));
    }

    @Test
    void unFicheroIlegibleAvisaEnVezDeRomperElEnvio() {
        CajaData linea = caja(1, "721", "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado =
                servicio.completar(envioCon(linea), "esto no es un xlsx".getBytes());

        assertEquals("721", linea.getNumeroPedido());
        assertEquals(1, resultado.getAvisos().size());
    }

    @Test
    void unaCajaSinPedidoSeSaltaSinAvisoDeBusqueda() throws IOException {
        CajaData linea = caja(1, null, "PXBHZ-H65077", "LZZ-NOIR", 3, null, 4.2);

        ResultadoPedidos resultado = servicio.completar(envioCon(linea), pedidoReal());

        assertEquals(null, linea.getNumeroPedido());
        assertTrue(resultado.getAvisos().isEmpty());
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `mvn test -Dtest=PedidoCompletionServiceTest`
Expected: FAIL — no compila, `PedidoCompletionService` no existe.

- [ ] **Step 3: Crear el DTO de resultado**

Crear `src/main/java/com/puntotres/packinglist/service/ResultadoPedidos.java`:

```java
package com.puntotres.packinglist.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de completar los números de pedido desde el excel del cliente:
 * solo avisos, porque el número completo se escribe sobre las propias
 * CajaData, igual que hace la asignación de palets.
 */
public class ResultadoPedidos {

    private final List<String> avisos = new ArrayList<>();

    public List<String> getAvisos() { return avisos; }
}
```

- [ ] **Step 4: Implementar `PedidoCompletionService`**

Crear `src/main/java/com/puntotres/packinglist/service/PedidoCompletionService.java`:

```java
package com.puntotres.packinglist.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.CajaData;

/**
 * Completa el número de pedido de las cajas de APC: lo que llega de la imagen
 * o del formulario son los tres últimos dígitos, y el número entero vive en el
 * excel de pedido del cliente.
 *
 * Corre UNA sola vez, al importar, y nunca en los recálculos de la pantalla de
 * revisión: volver a ejecutarlo pisaría el pedido que el usuario acabe de
 * corregir a mano, igual que pasaría con {@link PaletAssignmentService}.
 *
 * Sin excel, sin fila que case o con clave ambigua: aviso y se deja el dato
 * como llegó. Un PO inventado sería peor que uno incompleto, que se ve.
 */
@Service
public class PedidoCompletionService {

    public ResultadoPedidos completar(List<EnvioImportado.DestinoImportado> destinos,
                                      byte[] excelPedido) {
        ResultadoPedidos resultado = new ResultadoPedidos();
        if (excelPedido == null || excelPedido.length == 0) {
            resultado.getAvisos().add("No se ha subido el excel de pedido del cliente: "
                    + "los números de pedido se quedan como llegaron (tres dígitos)");
            return resultado;
        }

        ApcPedidoExcel pedido;
        try {
            pedido = ApcPedidoExcel.desdeBytes(excelPedido);
        } catch (Exception e) {
            resultado.getAvisos().add("No se ha podido leer el excel de pedido del cliente ("
                    + e.getMessage() + "): los números de pedido se quedan como llegaron");
            return resultado;
        }
        resultado.getAvisos().addAll(pedido.avisos());

        // Una misma referencia+parcial aparece en muchas cajas: se avisa una
        // vez por clave, no una por caja, o la revisión se llena de ruido.
        Set<String> yaAvisadas = new LinkedHashSet<>();
        for (EnvioImportado.DestinoImportado destino : destinos) {
            for (CajaData caja : destino.getDestino().getCajas()) {
                completarCaja(caja, pedido, yaAvisadas, resultado);
            }
        }
        return resultado;
    }

    private void completarCaja(CajaData caja, ApcPedidoExcel pedido,
                               Set<String> yaAvisadas, ResultadoPedidos resultado) {
        String parcial = caja.getNumeroPedido();
        if (parcial == null || parcial.isBlank()) {
            return; // sin pedido no hay nada que completar; ya avisa la importación
        }
        Optional<String> completo = pedido.pedidoCompleto(caja.getReferencia(), parcial);
        if (completo.isPresent()) {
            caja.setNumeroPedido(completo.get());
            return;
        }
        String clave = caja.getReferencia() + "|" + parcial;
        if (yaAvisadas.add(clave)) {
            resultado.getAvisos().add("El excel de pedido no tiene ninguna línea de '"
                    + caja.getReferencia() + "' con un pedido acabado en '" + parcial
                    + "': se queda como llegó");
        }
    }
}
```

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

Run: `mvn test -Dtest=PedidoCompletionServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/PedidoCompletionService.java \
        src/main/java/com/puntotres/packinglist/service/ResultadoPedidos.java \
        src/test/java/com/puntotres/packinglist/service/PedidoCompletionServiceTest.java
git commit -m "completar el numero de pedido de APC desde el excel del cliente"
```

---

### Task 6: Campo de excel de pedido en la pantalla de entrada

Sube el fichero, lo guarda en sesión y engancha las tres piezas anteriores al pipeline.

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/web/EnvioForm.java`
- Modify: `src/main/java/com/puntotres/packinglist/web/EnvioEnCurso.java`
- Modify: `src/main/java/com/puntotres/packinglist/web/PackingListController.java`
- Modify: `src/main/resources/templates/entrada.html`
- Test: `src/test/java/com/puntotres/packinglist/web/PackingListControllerTest.java`

**Interfaces:**
- Consumes: `ResolutorDestinosPadre.resolver(...)` (Task 3), `PedidoCompletionService.completar(...)` (Task 5), `ClienteConfig.isPedidoCliente()` (Task 1).
- Produces:
  - `EnvioForm.getPedidoCliente() : MultipartFile` / `setPedidoCliente(MultipartFile)`
  - `EnvioEnCurso.getExcelPedidoCliente() : byte[]` (null si no se subió)
  - `EnvioEnCurso.getNombreExcelPedidoCliente() : String`
  - `EnvioEnCurso.setExcelPedidoCliente(byte[] contenido, String nombre)`
  - Atributo de modelo `clientesJs[clave].pedidoCliente` con valor `"true"`/`"false"`.

- [ ] **Step 1: Escribir el test que falla**

`PackingListControllerTest` es `@SpringBootTest` + `@AutoConfigureMockMvc`, su
campo de MockMvc se llama **`mvc`** y el estado del envío se comprueba
**renderizando `/revision` y mirando el HTML** con un `MockHttpSession`
compartido — `EnvioEnCurso` es `@SessionScope` y no se puede inyectar desde
fuera de una petición. Seguir ese patrón.

Añadir a `src/test/java/com/puntotres/packinglist/web/PackingListControllerTest.java`:

```java
    /** POST /importar de un envío de APC a Australia, una hija de WHOLESALE. */
    private void importarApcAustralia(MockHttpSession sesion) throws Exception {
        String json = """
                {"cliente": "APC", "destinos": [{"destino": "Australia",
                  "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 1}],
                  "referencias": [
                    {"referencia": "PXBHZ-H65077", "color": "LZZ-NOIR",
                     "medidaCaja": "40x30x20", "pedido": "721",
                     "cajas": [{"caja": 1, "unidades": 3, "pesoBruto": 4.2}]}
                  ]}]}
                """;
        mvc.perform(post("/importar").session(sesion)
                        .param("cliente", "APC").param("json", json)
                        .param("temporada", "E25").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "28/04/2026").param("fechaEnvio", "28/04/2026"))
                .andExpect(redirectedUrl("/revision"));
    }

    private String revision(MockHttpSession sesion) throws Exception {
        return mvc.perform(get("/revision").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @SuppressWarnings("unchecked")
    @Test
    void soloLosClientesConPedidoDeclaradoPidenSuExcelEnLaEntrada() throws Exception {
        Map<String, Map<String, String>> clientesJs =
                (Map<String, Map<String, String>>) mvc.perform(get("/packing-list"))
                        .andExpect(status().isOk())
                        .andReturn().getModelAndView().getModel().get("clientesJs");

        assertEquals("true", clientesJs.get("APC").get("pedidoCliente"));
        assertEquals("true", clientesJs.get("AMI").get("pedidoCliente"));
        assertEquals("false", clientesJs.get("ACKERMANN").get("pedidoCliente"));
    }

    @Test
    void unaHijaDeApcSeRevisaBajoSuPadreYConSuLivraisonCode() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarApcAustralia(sesion);

        String html = revision(sesion);

        assertTrue(html.contains("WHOLESALE"));
        assertTrue(html.contains("PUN20260428WH1"));
    }

    @Test
    void sinExcelDePedidoElNumeroSeQuedaEnLosTresDigitosYSeAvisa() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarApcAustralia(sesion);

        String html = revision(sesion);

        assertTrue(html.contains("721"));
        assertTrue(html.contains("No se ha subido el excel de pedido"));
    }

    @Test
    void conElExcelDePedidoSubidoElNumeroDePedidoSaleCompleto() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        byte[] pedido;
        try (var in = getClass().getResourceAsStream("/ejemplos/APC_PEDIDO_FALL26.xlsx")) {
            pedido = in.readAllBytes();
        }
        String json = """
                {"cliente": "APC", "destinos": [{"destino": "Australia",
                  "palets": [{"palet": 1, "cajaInicio": 1, "cajaFin": 1}],
                  "referencias": [
                    {"referencia": "PXBHZ-H65077", "color": "LZZ-NOIR",
                     "medidaCaja": "40x30x20", "pedido": "721",
                     "cajas": [{"caja": 1, "unidades": 3, "pesoBruto": 4.2}]}
                  ]}]}
                """;

        mvc.perform(multipart("/importar").file(new MockMultipartFile(
                                "pedidoCliente", "APC_PEDIDO_FALL26.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                pedido))
                        .session(sesion)
                        .param("cliente", "APC").param("json", json)
                        .param("temporada", "E25").param("numeroFactura", "FA-1")
                        .param("fechaFactura", "28/04/2026").param("fechaEnvio", "28/04/2026"))
                .andExpect(redirectedUrl("/revision"));

        assertTrue(revision(sesion).contains("4100128721"));
    }
```

Añadir el import `java.util.Map` (los demás — `MockHttpSession`,
`MockMultipartFile`, `multipart`, `assertEquals`, `assertTrue` — ya están).

Este último test depende del fichero copiado en la Task 4.

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `mvn test -Dtest=PackingListControllerTest`
Expected: FAIL — `clientesJs` no tiene la clave `pedidoCliente`, la destinación
sigue llamándose `Australia` y no aparece ningún Livraison code.

- [ ] **Step 3: Añadir el campo al formulario y a la sesión**

En `src/main/java/com/puntotres/packinglist/web/EnvioForm.java`:

```java
    /**
     * Excel de pedido de la temporada del cliente. Opcional: APC lo usa para
     * completar el nº de pedido y AMI para no volver a pedirlo en el Paso 2
     * de etiquetas. Solo se muestra a los clientes que lo declaran.
     */
    private MultipartFile pedidoCliente;

    public MultipartFile getPedidoCliente() { return pedidoCliente; }
    public void setPedidoCliente(MultipartFile pedidoCliente) { this.pedidoCliente = pedidoCliente; }
```

En `src/main/java/com/puntotres/packinglist/web/EnvioEnCurso.java`, añadir los
campos, sus accesores y limpiarlos en `reiniciar()`:

```java
    private byte[] excelPedidoCliente;
    private String nombreExcelPedidoCliente;
```

```java
    public byte[] getExcelPedidoCliente() { return excelPedidoCliente; }
    public String getNombreExcelPedidoCliente() { return nombreExcelPedidoCliente; }

    /** El excel de pedido subido en la entrada; lo reutiliza el Paso 2 de etiquetas. */
    public void setExcelPedidoCliente(byte[] contenido, String nombre) {
        this.excelPedidoCliente = contenido;
        this.nombreExcelPedidoCliente = nombre;
    }
```

Dentro de `reiniciar()`:

```java
        excelPedidoCliente = null;
        nombreExcelPedidoCliente = null;
```

- [ ] **Step 4: Cablear el controlador**

En `PackingListController`, añadir al constructor y a los campos:

```java
    private final ResolutorDestinosPadre resolutorDestinos;
    private final PedidoCompletionService completadorPedidos;
```

(inyectarlos como los demás servicios, respetando el orden del constructor).

En `anadirAtributosDeClientes`, dentro del `forEach` que llena `clientesJs`:

```java
            datos.put("pedidoCliente", String.valueOf(config.isPedidoCliente()));
```

En `importar(...)`, justo **después** de `EnvioImportado importado = importador.importar(envio);`
y **antes** del bucle que asigna palets:

```java
        // El excel de pedido se guarda aunque el cliente no lo use en el
        // packing list: AMI lo reutiliza en el Paso 2 de etiquetas.
        byte[] excelPedido = null;
        MultipartFile subido = envioForm.getPedidoCliente();
        if (subido != null && !subido.isEmpty()) {
            try {
                excelPedido = subido.getBytes();
                envioEnCurso.setExcelPedidoCliente(excelPedido, subido.getOriginalFilename());
            } catch (IOException e) {
                importado.getAvisos().add("No se ha podido leer el excel de pedido subido: "
                        + e.getMessage());
            }
        }

        // Las destinaciones hijas se resuelven a su padre ANTES de asignar
        // palets, para que la asignación y la inferencia trabajen ya sobre
        // las destinaciones definitivas.
        ResultadoDestinos resueltos = resolutorDestinos.resolver(
                importado.getDestinos(), cliente, cabecera.getFechaEnvio());
        importado.getDestinos().clear();
        importado.getDestinos().addAll(resueltos.getDestinos());
        importado.getAvisos().addAll(resueltos.getAvisos());

        // Solo APC completa el pedido: es el único con excel de pedido que
        // trae el número entero por referencia.
        if (cliente.getPlantilla() == TipoPlantilla.APC) {
            importado.getAvisos().addAll(
                    completadorPedidos.completar(importado.getDestinos(), excelPedido).getAvisos());
        }
```

Ojo: la asignación de `envioEnCurso.setExcelPedidoCliente(...)` va **después**
de `envioEnCurso.reiniciar()`. Si `reiniciar()` se llama más abajo (hoy está
justo antes de `setCabecera`), mover el `setExcelPedidoCliente` a continuación
de `reiniciar()` y guardar los bytes en la variable local mientras tanto.

Añadir los imports `TipoPlantilla`, `ResolutorDestinosPadre`,
`ResultadoDestinos`, `PedidoCompletionService`.

- [ ] **Step 5: Añadir el input a `entrada.html`**

Justo después del bloque `<div id="camposAmi" ...>` (línea 39), insertar:

```html
        <!-- Excel de pedido de la temporada. Solo lo ven los clientes que lo
             declaran (pedido-cliente en application.yml): APC lo usa para
             completar el nº de pedido, AMI para no repetirlo en etiquetas. -->
        <div id="bloquePedidoCliente" style="display: none">
            <label for="pedidoCliente">Excel de pedido del cliente</label>
            <p class="ayuda">Opcional. En APC se usa para completar el número de pedido
                (de las fotos solo salen sus tres últimos dígitos). En AMI evita tener que
                volver a subirlo al generar las etiquetas de caja.</p>
            <input type="file" id="pedidoCliente" name="pedidoCliente" accept=".xlsx">
        </div>
```

Y en `actualizarCamposCliente()` (línea 211), después de la línea de `camposAmi`:

```javascript
            document.getElementById('bloquePedidoCliente').style.display =
                (config && config.pedidoCliente === 'true') ? '' : 'none';
```

- [ ] **Step 6: Ejecutar los tests**

Run: `mvn test -Dtest=PackingListControllerTest`
Expected: PASS.

Run: `mvn test`
Expected: PASS.

- [ ] **Step 7: Prueba manual**

```bash
mvn spring-boot:run
```

Abrir `http://localhost:8080/packing-list`, elegir APC → aparece el campo de
excel de pedido; elegir Ackermann → desaparece. Subir
`docs/Etiquetas cajas/APC_PEDIDO_FALL26.xlsx` con el JSON de
`src/test/resources/ejemplos/envio-apc-etiquetas.json` y comprobar en la
revisión que las destinaciones son WHOLESALE / D. USA / JAPAN / KOREA / RETAIL
y que los pedidos salen con diez dígitos.

Para parar: `Ctrl+C` y, si el 8080 queda ocupado,
`netstat -ano | findstr :8080` + `taskkill /F /PID <pid>`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/web/EnvioForm.java \
        src/main/java/com/puntotres/packinglist/web/EnvioEnCurso.java \
        src/main/java/com/puntotres/packinglist/web/PackingListController.java \
        src/main/resources/templates/entrada.html \
        src/test/java/com/puntotres/packinglist/web/PackingListControllerTest.java
git commit -m "subir el excel de pedido del cliente en la pantalla de entrada"
```

---

### Task 7: Livraison code editable en la revisión

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/web/DestinoVista.java`
- Modify: `src/main/java/com/puntotres/packinglist/web/RevisionForm.java`
- Modify: `src/main/java/com/puntotres/packinglist/web/PackingListController.java`
- Modify: `src/main/resources/templates/revision.html`
- Test: `src/test/java/com/puntotres/packinglist/web/PackingListControllerTest.java`

**Interfaces:**
- Consumes: `CajaData.getLivraisonCode()`, el pipeline de Task 6.
- Produces:
  - `record DestinoVista(int indice, String nombre, List<FilaCaja> filas, int totalCajas, String livraisonCode)` — `livraisonCode` null cuando la destinación no lo usa.
  - `RevisionForm.getDestinos() : List<RevisionForm.DestinoEditado>`
  - `RevisionForm.DestinoEditado` con `indiceDestino : int` y `livraisonCode : String`.

- [ ] **Step 1: Escribir el test que falla**

Añadir a `PackingListControllerTest`, reutilizando los helpers
`importarApcAustralia(sesion)` y `revision(sesion)` de la Task 6:

```java
    @Test
    void editarElLivraisonCodeLoReescribeEnSuDestinacion() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarApcAustralia(sesion);

        mvc.perform(post("/recalcular").session(sesion)
                        .param("destinos[0].indiceDestino", "0")
                        .param("destinos[0].livraisonCode", "PUN20260428WH3"))
                .andExpect(redirectedUrl("/revision"));

        String html = revision(sesion);
        assertTrue(html.contains("PUN20260428WH3"));
        assertFalse(html.contains("PUN20260428WH1"));
    }

    @Test
    void unLivraisonCodeVacioNoBorraElGenerado() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        importarApcAustralia(sesion);

        mvc.perform(post("/recalcular").session(sesion)
                        .param("destinos[0].indiceDestino", "0")
                        .param("destinos[0].livraisonCode", ""))
                .andExpect(redirectedUrl("/revision"));

        assertTrue(revision(sesion).contains("PUN20260428WH1"));
    }
```

Añadir el import estático `org.junit.jupiter.api.Assertions.assertFalse`.

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `mvn test -Dtest=PackingListControllerTest`
Expected: FAIL — `RevisionForm` no tiene `destinos`, el parámetro se ignora y
el código sigue siendo `PUN20260428WH1` en el primer test.

- [ ] **Step 3: Ampliar `RevisionForm`**

Añadir a `src/main/java/com/puntotres/packinglist/web/RevisionForm.java`:

```java
    /**
     * Lo editado en la CABECERA de cada destinación (hoy solo el Livraison
     * code de APC). Va aparte de las cajas porque es un dato de la
     * destinación entera: como columna se repetiría en cada fila y ensuciaría
     * el criterio de compactación de AgrupadorFilasRevision.
     */
    private List<DestinoEditado> destinos = new ArrayList<>();

    public List<DestinoEditado> getDestinos() { return destinos; }
    public void setDestinos(List<DestinoEditado> destinos) { this.destinos = destinos; }

    public static class DestinoEditado {

        private int indiceDestino;
        private String livraisonCode;

        public int getIndiceDestino() { return indiceDestino; }
        public void setIndiceDestino(int indiceDestino) { this.indiceDestino = indiceDestino; }

        public String getLivraisonCode() { return livraisonCode; }
        public void setLivraisonCode(String livraisonCode) { this.livraisonCode = livraisonCode; }
    }
```

- [ ] **Step 4: Ampliar `DestinoVista` y el controlador**

`src/main/java/com/puntotres/packinglist/web/DestinoVista.java`:

```java
/**
 * Una destinación en la pantalla de revisión: su índice en el envío (que
 * localiza sus cajas al aplicar las ediciones), su nombre, sus filas ya
 * compactadas y el Livraison code de la destinación entera (null si el
 * cliente no lo usa: solo APC lo tiene).
 *
 * {@code totalCajas} son los bultos reales de la destinación y NO coincide con
 * {@code filas.size()}: una fila puede representar un tramo compactado ("4-8").
 */
public record DestinoVista(int indice, String nombre, List<FilaCaja> filas, int totalCajas,
                           String livraisonCode) {
}
```

En `PackingListController.montarVistaDestinos()`, cambiar la construcción:

```java
            // El Livraison code es de la destinación entera: todas sus cajas
            // llevan el mismo, así que basta con mirar la primera.
            String livraisonCode = cajas.isEmpty() ? null : cajas.get(0).getLivraisonCode();
            vista.add(new DestinoVista(i, destinos.get(i).getDestino().getNombreDestino(),
                    filas, contarCajasFisicas(cajas), livraisonCode));
```

En `aplicarEdicionesYReinferir(RevisionForm form)`, antes del bucle de cajas:

```java
        for (RevisionForm.DestinoEditado edicion : form.getDestinos()) {
            if (edicion == null || edicion.getIndiceDestino() < 0
                    || edicion.getIndiceDestino() >= destinos.size()
                    || !tieneTexto(edicion.getLivraisonCode())) {
                continue; // vacío = "no tocar", igual que en las cajas
            }
            destinos.get(edicion.getIndiceDestino()).getDestino().getCajas()
                    .forEach(caja -> caja.setLivraisonCode(edicion.getLivraisonCode().trim()));
        }
```

(la variable `destinos` ya está declarada al principio del método).

- [ ] **Step 5: Añadir el input a `revision.html`**

Sustituir el `<h2>` de la sección de destino por:

```html
            <h2 th:text="|${destino.nombre} (${destino.totalCajas} cajas)|"></h2>
            <!-- El Livraison code es de la destinación entera, no de una caja.
                 Se genera con el contador a 1 porque la app no tiene historial
                 de envíos: si el envío no es el primero del día, corrígelo. -->
            <div class="campo-destino" th:if="${destino.livraisonCode != null}">
                <input type="hidden" th:name="|destinos[${destino.indice}].indiceDestino|"
                       th:value="${destino.indice}">
                <label th:for="|livraison-${destino.indice}|">Livraison code</label>
                <input type="text" th:id="|livraison-${destino.indice}|"
                       th:name="|destinos[${destino.indice}].livraisonCode|"
                       th:value="${destino.livraisonCode}">
            </div>
```

- [ ] **Step 6: Ejecutar los tests**

Run: `mvn test -Dtest=PackingListControllerTest`
Expected: PASS.

Run: `mvn test`
Expected: PASS. Si falla algún test que construye `DestinoVista` a mano,
añadirle el quinto argumento (`null` cuando no aplique).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/web/DestinoVista.java \
        src/main/java/com/puntotres/packinglist/web/RevisionForm.java \
        src/main/java/com/puntotres/packinglist/web/PackingListController.java \
        src/main/resources/templates/revision.html \
        src/test/java/com/puntotres/packinglist/web/PackingListControllerTest.java
git commit -m "el Livraison code se edita por destinacion en la revision"
```

---

### Task 8: Reutilizar el pedido en etiquetas y rellenar los NOT FOUND de APC

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/CampoEtiquetas.java`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java:40-41`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasGenerador.java:187-188`
- Modify: `src/main/java/com/puntotres/packinglist/web/PackingListController.java` (`/etiquetas` y `/etiquetas/generar`)
- Modify: `src/main/resources/templates/etiquetas.html`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasGeneradorTest.java`

**Interfaces:**
- Consumes: `EnvioEnCurso.getExcelPedidoCliente()` y `getNombreExcelPedidoCliente()` (Task 6), `CajaData.getLivraisonCode()` (Task 3).
- Produces: `record CampoEtiquetas(String nombre, String titulo, boolean esPedidoCliente)`.

- [ ] **Step 1: Escribir el test que falla**

`ApcEtiquetasGeneradorTest` ya tiene los helpers
`caja(numero, referencia, color, talla, cantidad, pesoBruto, palet)`,
`palet(numero, inicio, fin, tara)` y
`destino(nombre, palets, cajas...)`, y vive en el mismo paquete que
`ApcEtiquetaLayout` (que es package-private), así que puede usar sus
constantes. Añadir:

```java
    /** Hoja de cajas de la plantilla de WHOLESALE (Crosslog). */
    private static XSSFSheet hojaCajasWholesale(byte[] excel) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(excel))
                .getSheet(ApcEtiquetaLayout.WH_CROSSLOG.hojaCajas());
    }

    @Test
    void laEtiquetaLlevaElPedidoYElLivraisonCodeDeLaCaja() throws Exception {
        CajaData linea = caja(1, "PXCBS-F67066", "LZZ-NOIR", null, 2, 3.0, 1);
        linea.setNumeroPedido("4100128725");
        linea.setLivraisonCode("PUN20260717WH1");

        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("WHOLESALE", List.of(palet(1, 1, 1, null)), linea)),
                envio(), Map.of());

        XSSFSheet hoja = hojaCajasWholesale(resultado.getExcels().get(0).getContenido());
        assertEquals("4100128725",
                hoja.getRow(ApcEtiquetaLayout.WH_CROSSLOG.filaOrder())
                        .getCell(ApcEtiquetaLayout.COL_VALOR).getStringCellValue());
        assertEquals("PUN20260717WH1",
                hoja.getRow(ApcEtiquetaLayout.WH_CROSSLOG.filaLivraison())
                        .getCell(ApcEtiquetaLayout.COL_VALOR).getStringCellValue());
    }

    @Test
    void sinPedidoNiLivraisonCodeLaEtiquetaSigueDiciendoNotFound() throws Exception {
        CajaData linea = caja(1, "PXCBS-F67066", "LZZ-NOIR", null, 2, 3.0, 1);

        ResultadoEtiquetas resultado = generador.generar(
                List.of(destino("WHOLESALE", List.of(palet(1, 1, 1, null)), linea)),
                envio(), Map.of());

        XSSFSheet hoja = hojaCajasWholesale(resultado.getExcels().get(0).getContenido());
        assertEquals("NOT FOUND",
                hoja.getRow(ApcEtiquetaLayout.WH_CROSSLOG.filaLivraison())
                        .getCell(ApcEtiquetaLayout.COL_VALOR).getStringCellValue());
    }
```

El destino se llama `WHOLESALE` porque es la clave que la Task 1 dejó en
`ApcEtiquetaLayout.POR_DESTINO` en lugar de `C-LOG`.

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `mvn test -Dtest=ApcEtiquetasGeneradorTest`
Expected: FAIL — la celda dice "NOT FOUND" en vez del pedido.

- [ ] **Step 3: Rellenar los NOT FOUND en `ApcEtiquetasGenerador`**

Sustituir el `return` final de `etiquetaDe(...)`:

```java
        // El Order N° y el Livraison code los trae ya la caja: el packing list
        // los completó al importar (PedidoCompletionService y
        // ResolutorDestinosPadre). "NOT FOUND" queda solo para cuando falten.
        return new EtiquetaCajaApc(oNoDisponible(lider.getNumeroPedido()),
                oNoDisponible(lider.getLivraisonCode()), referencia,
                colour, size, piezas, posicion + " / " + total, kg(peso));
```

y añadir el helper junto a `kg(...)`:

```java
    private static String oNoDisponible(String valor) {
        return (valor == null || valor.isBlank()) ? NO_DISPONIBLE : valor;
    }
```

Actualizar el javadoc de clase: quitar la frase «el Order N° y el Livraison
code salen como "NOT FOUND" hasta que se implemente su búsqueda (iteración
futura)» y poner que ambos vienen de la caja.

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `mvn test -Dtest=ApcEtiquetasGeneradorTest`
Expected: PASS.

- [ ] **Step 5: Marcar el campo de pedido y reutilizarlo desde la sesión**

`src/main/java/com/puntotres/packinglist/service/etiquetas/CampoEtiquetas.java`:

```java
/**
 * Un input del Paso 2 de etiquetas que el generador de un cliente pide al
 * usuario ANTES de generar (siempre un archivo .xlsx en esta fase; si un
 * cliente futuro necesita un texto/código, se ampliará con un tipo).
 *
 * nombre: name del input HTML y clave del mapa de archivos.
 * titulo: etiqueta visible, ej. "Introducir excel del pedido de AMI".
 * esPedidoCliente: es EL excel de pedido de la temporada, el mismo que se
 *   sube en la pantalla de entrada; si ya está en sesión no hace falta
 *   volver a subirlo. Se declara explícitamente en vez de deducirlo del
 *   nombre porque un generador futuro puede pedir dos ficheros.
 */
public record CampoEtiquetas(String nombre, String titulo, boolean esPedidoCliente) {
}
```

En `AmiEtiquetasGenerador`:

```java
    static final CampoEtiquetas CAMPO_PEDIDO =
            new CampoEtiquetas("pedido", "Introducir excel del pedido de AMI", true);
```

En `PackingListController.generarEtiquetas(...)`, sustituir el cuerpo del bucle
que recoge los archivos:

```java
            for (CampoEtiquetas campo : generador.camposRequeridos(destinos)) {
                MultipartFile archivo = peticion.getFile(campo.nombre());
                if (archivo != null && !archivo.isEmpty()) {
                    archivos.put(campo.nombre(), archivo.getBytes());
                    continue;
                }
                // El excel de pedido puede venir de la pantalla de entrada:
                // si ya está en sesión, no se vuelve a pedir.
                byte[] deSesion = campo.esPedidoCliente()
                        ? envioEnCurso.getExcelPedidoCliente() : null;
                if (deSesion == null) {
                    redirect.addFlashAttribute("error", "Falta el archivo: " + campo.titulo());
                    return "redirect:/etiquetas";
                }
                archivos.put(campo.nombre(), deSesion);
            }
```

En `PackingListController.etiquetas(Model, RedirectAttributes)`, añadir antes
del `return "etiquetas";`:

```java
        model.addAttribute("nombrePedidoEnSesion", envioEnCurso.getNombreExcelPedidoCliente());
```

En `src/main/resources/templates/etiquetas.html`, sustituir el bloque del campo:

```html
            <div class="campo" th:each="campo : ${campos}">
                <label th:for="${campo.nombre}" th:text="${campo.titulo}"></label>
                <p class="ayuda" th:if="${campo.esPedidoCliente and nombrePedidoEnSesion != null}"
                   th:text="|Ya subiste '${nombrePedidoEnSesion}' en el paso 1: súbelo otra vez solo si quieres cambiarlo.|"></p>
                <input type="file" th:id="${campo.nombre}" th:name="${campo.nombre}"
                       accept=".xlsx"
                       th:required="${!(campo.esPedidoCliente and nombrePedidoEnSesion != null)}">
            </div>
```

- [ ] **Step 6: Ejecutar la suite completa**

Run: `mvn test`
Expected: PASS. Cualquier test que construya `new CampoEtiquetas(a, b)` necesita
el tercer argumento.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/CampoEtiquetas.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasGenerador.java \
        src/main/java/com/puntotres/packinglist/web/PackingListController.java \
        src/main/resources/templates/etiquetas.html \
        src/test/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasGeneradorTest.java
git commit -m "las etiquetas de APC ya traen pedido y Livraison code, y el pedido no se pide dos veces"
```

---

### Task 9: Verificación de punta a punta y documentación

**Files:**
- Modify: `src/test/java/com/puntotres/packinglist/service/ApcExcelBuilderTest.java`
- Modify: `docs/Packing Lists/campos-json-por-cliente.md`
- Modify: `src/test/resources/ejemplos/README.md`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: nada de código.

- [ ] **Step 1: Test del builder con la hija en la columna DESTINATION**

Añadir a `ApcExcelBuilderTest` un caso que reabra el `.xlsx` y compruebe las
tres columnas afectadas. Reutilizar los helpers `linea(...)` y `envio()` que ya
tiene la clase, y el destino `IVRY` de su `apc()`:

```java
    @Test
    void escribeLivraisonCodePedidoYLaHijaEnLaColumnaDestination() throws Exception {
        CajaData linea = linea(1, 1, "LE NEIGE", "PXCBC-F67008", "AUSTRALIA", null, 11, 8.18);
        linea.setNumeroPedido("4100128721");
        linea.setLivraisonCode("PUN20260428WH1");
        DestinoData destino = new DestinoData();
        destino.setNombreDestino("IVRY");
        destino.setCajas(List.of(linea));

        List<ExcelGenerado> excels =
                builder.generar(destino, List.of(palet("IVRY", 1, 1, 1)), envio(), apc());

        try (XSSFWorkbook wb = new XSSFWorkbook(
                new ByteArrayInputStream(excels.get(0).getContenido()))) {
            Row fila = wb.getSheetAt(0).getRow(17); // primera fila de caja
            assertEquals("PUN20260428WH1", fila.getCell(5).getStringCellValue());   // F
            assertEquals("4100128721", fila.getCell(6).getStringCellValue());       // G
            assertEquals("AUSTRALIA", fila.getCell(10).getStringCellValue());       // K
        }
    }
```

Run: `mvn test -Dtest=ApcExcelBuilderTest`
Expected: PASS (el builder ya escribía esas tres columnas; el test ancla que
siguen llegando llenas de punta a punta).

- [ ] **Step 2: Actualizar `campos-json-por-cliente.md`**

En la sección "## APC", sustituir el párrafo de destinos y las filas
correspondientes de la tabla:

```markdown
Bolsos y cinturones comparten plantilla. Destinos configurados en
`application.yml`: **WHOLESALE**, **RETAIL**, D. USA, IVRY, JAPAN y KOREA.

WHOLESALE y RETAIL son **destinos padre**: agrupan destinaciones hijas que se
tratan como si fueran ellos (mismo fichero, misma hoja, misma dirección), y la
hija solo sobrevive en la columna DESTINATION de sus líneas.

| Padre | Hijas |
|---|---|
| WHOLESALE | AUSTRALIA, WHOLESALE, CHINE FRANCH |
| RETAIL | RETAIL, WHOLESALE CONCESS |

Dos hijas del mismo padre en un envío salen en UN solo excel, con sus cajas
concatenadas. Si sus números de caja o de palet se repiten se avisa y **no se
renumera nada**: el número está pegado físicamente en el bulto. Un destino que
no sea ni clave ni hija sigue sin generar, con aviso.
```

Y en la tabla de campos:

```markdown
| `livraisonCode` | **se ignora**: lo genera la aplicación (`PUN` + fecha de envío + abreviatura del padre + contador, p. ej. `PUN20260428WH1`) y se edita en la pantalla de revisión |
| `pedido` | los **tres últimos dígitos** del pedido; el número entero se busca por referencia en el excel de pedido del cliente que se sube en la entrada. Sin excel o sin fila, se queda como llegó, con aviso. Columna COMMANDE |
```

- [ ] **Step 3: Actualizar `src/test/resources/ejemplos/README.md`**

Sustituir la línea del pedido de APC:

```markdown
- `docs/Etiquetas cajas/APC_PEDIDO_FALL26.xlsx` (APC, Fall 26), copiado aquí
  como `APC_PEDIDO_FALL26.xlsx`. Lo lee `ApcPedidoExcel` para completar el
  número de pedido, y `ApcPedidoExcelTest` ancla su comportamiento: hoja
  localizada por sus tres cabeceras y unicidad de referencia + tres dígitos.
  Si se actualiza el de `docs/`, actualizar también la copia:

      cp "docs/Etiquetas cajas/APC_PEDIDO_FALL26.xlsx" \
         "src/test/resources/ejemplos/APC_PEDIDO_FALL26.xlsx"
```

- [ ] **Step 4: Actualizar `CLAUDE.md`**

En la sección de arquitectura, añadir después del párrafo de "Nombre de los
excels de packing list":

```markdown
**Destinos padre de APC**: `WHOLESALE` y `RETAIL` agrupan destinaciones hijas
(`AUSTRALIA`/`WHOLESALE`/`CHINE FRANCH` y `RETAIL`/`WHOLESALE CONCESS`) que se
tratan como el padre: mismo fichero, misma hoja y misma dirección, y la hija
solo sobrevive en la columna `DESTINATION`. `ResolutorDestinosPadre` lo hace
tras importar y **antes** de asignar palets, y fusiona en un solo `DestinoData`
las hijas del mismo padre. **Nunca renumera cajas ni palets**: si dos hijas
repiten un número, avisa y lo deja — el número va pegado al bulto. Añadir una
hija es una línea de `destinos-hijo` en `application.yml`. `C-LOG` era el
nombre viejo de `WHOLESALE` y ya no existe.

**Livraison code de APC**: lo genera `LivraisonCode` (`PUN` + fecha de envío
`yyyyMMdd` + `abreviatura` del padre + contador), no el JSON — el
`livraisonCode` de entrada se ignora. El contador arranca en 1 porque no hay
historial de envíos, así que el código se muestra **entero y editable en la
cabecera de cada destinación** de la revisión, no como columna de la tabla
(como columna se repetiría en cada fila y ensuciaría la compactación).

**Número de pedido de APC**: de las fotos solo salen sus **tres últimos
dígitos**. `PedidoCompletionService` busca el número entero en el excel de
pedido del cliente (`ApcPedidoExcel`, clave `Article` + esos tres dígitos, que
en el fichero real identifica una única fila de 130). Sin excel o sin fila:
aviso y se deja lo que llegó, nunca un PO adivinado. **Corre solo al importar**,
igual que `PaletAssignmentService`: reejecutarlo en los recálculos de la
revisión machacaría lo tecleado a mano.

**Excel de pedido en la entrada**: los clientes con `pedido-cliente: true` en
`application.yml` (APC y AMI) muestran un input de fichero opcional en
`/packing-list`. Se guarda en `EnvioEnCurso` y lo reutiliza el Paso 2 de
etiquetas, que deja de exigirlo (`CampoEtiquetas.esPedidoCliente`).
```

Y en la sección "Reglas del proyecto", donde dice que el pedido de APC
«todavía sin usar en el código», cambiarlo por que lo lee `ApcPedidoExcel`.

- [ ] **Step 5: Ejecutar la suite completa**

Run: `mvn test`
Expected: PASS, toda la suite.

- [ ] **Step 6: Prueba manual del flujo entero**

```bash
mvn spring-boot:run
```

Con `src/test/resources/ejemplos/envio-apc-etiquetas.json` y el excel de pedido:
1. `/packing-list` → APC, subir el excel de pedido, pegar el JSON.
2. Revisión: destinaciones WHOLESALE (Australia + Chine franch + Wholesale
   fusionadas), D. USA, JAPAN, KOREA, RETAIL. Livraison code editable por
   destinación. Avisos de cajas repetidas entre hijas.
3. Generar: un solo `PKL_APC_WHOLESALE_<factura>.xlsx`. Abrirlo y comprobar
   columnas F (Livraison), G (pedido de 10 dígitos) y K (AUSTRALIA / CHINE
   FRANCH / WHOLESALE según la línea).
4. Etiquetas: no vuelve a pedir el excel de pedido; las etiquetas de WHOLESALE
   traen `ASN N°` y `Order N°` rellenos.

Parar con `Ctrl+C` (+ `taskkill` si el 8080 queda ocupado).

- [ ] **Step 7: Commit**

```bash
git add src/test/java/com/puntotres/packinglist/service/ApcExcelBuilderTest.java \
        "docs/Packing Lists/campos-json-por-cliente.md" \
        src/test/resources/ejemplos/README.md \
        CLAUDE.md
git commit -m "documentar los destinos padre, el Livraison code y el pedido de APC"
```
