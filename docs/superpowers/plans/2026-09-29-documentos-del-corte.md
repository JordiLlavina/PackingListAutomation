# Documentos del Corte — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** sección nueva del menú que, a partir del pedido de la temporada (AMI o APC) y un zip con las fotos de cada modelo, genera un Word apaisado con una orden de corte por modelo + piel + color y un Word de fotos por modelo + piel.

**Architecture:** paquete nuevo `service/corte/` (pedido → artículos, lectura del zip, conversión de fotos en segundo plano, dos builders de Word con POI) + persistencia de los nombres de piel (`persistence/MemoriaPieles`) + flujo web de tres pantallas (`DocumentosCorteController`, estado en `CorteEnCurso` de sesión, ficheros en un directorio temporal de la sesión).

**Tech Stack:** Java 17, Spring Boot 3.5.3, Apache POI 5.4.1 (XWPF), Openize.HEIC 26.5 (decodificador HEIC en Java puro), metadata-extractor 2.20.0 (orientación EXIF), WIC de Windows vía PowerShell (camino rápido de HEIC), Thymeleaf, JUnit 5, MockMvc.

**Spec:** [docs/superpowers/specs/2026-09-29-documentos-del-corte-design.md](../specs/2026-09-29-documentos-del-corte-design.md)

## Global Constraints

- Dependencias nuevas: `com.aspose:openize-heic:26.5` (repositorio `https://releases.aspose.com/java/repo/`, no está en Maven Central) y `com.drewnoakes:metadata-extractor:2.20.0`.
- Clientes: AMI y APC. Los genéricos salen deshabilitados como "en desarrollo".
- AMI parte `ULL712.AL0103` por el primer punto (modelo `ULL712`, piel `AL0103`); APC parte `PXCBC-F67008` por el primer guion (piel `PXCBC`, modelo `F67008`).
- Nº de bolsos = suma por referencia + color sin mirar destinación, talla ni pedido. AMI: `Commandé`, color `COLORIS + " " + Libellé coloris`; APC: `Quantité échéancée`, color `Couleurs`.
- Fotos reducidas a 1.600 px de lado mayor, JPEG; JPG enderezados con EXIF.
- Nunca fallar en silencio; solo bloquean el pedido ilegible, el zip ilegible y la falta de datos de entrada.
- Tests con `new` (JUnit 5 puro) salvo web y persistencia (`@SpringBootTest`; el perfil `test` lo fija surefire).
- Ninguna foto personal en el repo: el HEIC de prueba es `gimp_rgb_420_with_alpha.heic` del repo de Openize.
- Maquetación de los Word en constantes de cada builder, sin plantilla `.docx`.
- Todo lo visible (UI, avisos, javadoc) en español.

## Review Focus

1. **Zip hecho con el Explorador de Windows** (nombres en CP437, sin marca UTF-8): hay que leerlo igual, no dar "zip ilegible". Test en la tarea 2.
2. **Entrada del zip con `../`** (zip slip): nada se escribe fuera del directorio de trabajo. Test en la tarea 2.
3. **HEIC corrupto o que ningún decodificador entiende**: aviso con el nombre de la foto, la conversión termina y las demás salen. Test en la tarea 3.
4. **La foto principal elegida no se ha podido convertir**: la orden sale con la siguiente foto legible del modelo, no sin foto. Test en la tarea 7.
5. **Temporada guardada de otro cliente** (se cambió de cliente después de elegirla): error, nunca el pedido equivocado. Test en la tarea 8.

## Estructura de ficheros

```
src/main/java/com/puntotres/packinglist/service/corte/
  ReferenciaCorte, LineaCorte, ColorCorte, ArticuloCorte, PedidoCorte   (tarea 1)
  ClienteCorte, AmiCorte, ApcCorte, DocumentosCorteService              (tarea 1, generar() en la 7)
  FotoModelo, FotosTemporada, LectorZipFotos                            (tarea 2)
  ReductorImagen, DecodificadorHeicJava, ConversorHeicWindows,
  ConversionFotos, ConversorFotos                                        (tarea 3)
  WordCorte, Imagen, PielesArticulo, OrdenCorte, OrdenCorteDocBuilder    (tarea 5)
  FotosCorte, FotosCorteDocBuilder                                       (tarea 6)
  FilaCorte, DocumentoCorte, ResultadoCorte                              (tarea 7)
src/main/resources/corte/heic-a-jpeg.ps1                                 (tarea 3)
src/main/java/com/puntotres/packinglist/persistence/
  MemoriaPiel(+Id, Repository), MemoriaPielesArticulo(+Id, Repository),
  MemoriaPieles                                                          (tarea 4)
src/main/java/com/puntotres/packinglist/web/
  CorteEnCurso, PielesForm, FilaPielesVista, DocumentosCorteController   (tarea 8)
src/main/resources/templates/documentos-corte*.html                      (tarea 8)
Modificados: pom.xml, application.yml, menu.html, estilo.css,
  AmiPedidoExcel, ApcPedidoExcel, CLAUDE.md, ejemplos/README.md
```

Cada bloque de código de un fichero nuevo va precedido de `<!-- fichero: ruta -->`.

---

### Tarea 1: del pedido a los artículos de corte

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java` (método `lineasConCantidad()`)
- Modify: `src/main/java/com/puntotres/packinglist/service/ApcPedidoExcel.java` (método `lineasConCantidad()`)
- Create: `service/corte/ReferenciaCorte.java`, `LineaCorte.java`, `ColorCorte.java`, `ArticuloCorte.java`, `PedidoCorte.java`, `ClienteCorte.java`, `AmiCorte.java`, `ApcCorte.java`, `DocumentosCorteService.java`
- Test: `src/test/java/com/puntotres/packinglist/service/corte/ReferenciaCorteTest.java`, `PedidoCorteTest.java`, `PedidoCorteRealTest.java`

**Interfaces:**
- Produces: `ReferenciaCorte.deAmi(String)`, `ReferenciaCorte.deApc(String)`, `ReferenciaCorte.normalizar(String)`, `record ReferenciaCorte(String referencia, String modelo, String piel)` con `tienePiel()`; `record ColorCorte(String color, int bolsos)`; `record ArticuloCorte(ReferenciaCorte referencia, List<ColorCorte> colores)`; `record PedidoCorte(List<ArticuloCorte> articulos, List<String> avisos)` con `agrupar(List<LineaCorte>, Function<String, ReferenciaCorte>)` y `modelos()`; `interface ClienteCorte { String clave(); PedidoCorte leerPedido(byte[]) throws IOException; }`; `DocumentosCorteService(List<ClienteCorte>)` con `Optional<ClienteCorte> clientePara(String)`.

- [ ] **Step 1: tests que fallan**

<!-- fichero: src/test/java/com/puntotres/packinglist/service/corte/ReferenciaCorteTest.java -->
```java
package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReferenciaCorteTest {

    @Test
    void amiLlevaLaPielDetrasDelPunto() {
        ReferenciaCorte referencia = ReferenciaCorte.deAmi("ULL712.AL0103");

        assertEquals("ULL712.AL0103", referencia.referencia());
        assertEquals("ULL712", referencia.modelo());
        assertEquals("AL0103", referencia.piel());
        assertTrue(referencia.tienePiel());
    }

    @Test
    void apcLlevaLaPielDelanteDelGuion() {
        ReferenciaCorte referencia = ReferenciaCorte.deApc("PXCBC-F67008");

        assertEquals("F67008", referencia.modelo());
        assertEquals("PXCBC", referencia.piel());
    }

    @Test
    void seNormalizaComoLlegue() {
        ReferenciaCorte referencia = ReferenciaCorte.deAmi("  ull712.al0103 ");

        assertEquals("ULL712.AL0103", referencia.referencia());
        assertEquals("ULL712", referencia.modelo());
    }

    @Test
    void sinSeparadorLaReferenciaEsElModeloYNoSeAdivinaLaPiel() {
        ReferenciaCorte ami = ReferenciaCorte.deAmi("ULL712");
        ReferenciaCorte apc = ReferenciaCorte.deApc("F67008");
        ReferenciaCorte puntoAlPrincipio = ReferenciaCorte.deAmi(".AL0103");

        assertEquals("ULL712", ami.modelo());
        assertFalse(ami.tienePiel());
        assertEquals("F67008", apc.modelo());
        assertFalse(apc.tienePiel());
        assertEquals(".AL0103", puntoAlPrincipio.modelo());
        assertFalse(puntoAlPrincipio.tienePiel());
    }
}
```

<!-- fichero: src/test/java/com/puntotres/packinglist/service/corte/PedidoCorteTest.java -->
```java
package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PedidoCorteTest {

    @Test
    void sumaLasLineasDeLaMismaReferenciaYColorSinMirarNadaMas() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("ULL729.AL0103", "001 BLACK", 40),
                new LineaCorte("ULL729.AL0103", "718 VANILLA CREAM", 10),
                new LineaCorte("ULL729.AL0103", "001 BLACK", 60),
                new LineaCorte("ull729.al0103", "001 BLACK", 5)), ReferenciaCorte::deAmi);

        assertEquals(1, pedido.articulos().size());
        ArticuloCorte articulo = pedido.articulos().get(0);
        assertEquals(List.of(new ColorCorte("001 BLACK", 105), new ColorCorte("718 VANILLA CREAM", 10)),
                articulo.colores());
        assertTrue(pedido.avisos().isEmpty());
    }

    @Test
    void losArticulosSalenOrdenadosPorModeloYPiel() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("ULL754.AL0219", "2221", 1),
                new LineaCorte("ULL027.AL0216", "001", 1),
                new LineaCorte("ULL754.AL0137", "001", 1),
                new LineaCorte("ULL027.AL0103", "001", 1)), ReferenciaCorte::deAmi);

        assertEquals(List.of("ULL027.AL0103", "ULL027.AL0216", "ULL754.AL0137", "ULL754.AL0219"),
                pedido.articulos().stream().map(a -> a.referencia().referencia()).toList());
        assertEquals(Set.of("ULL027", "ULL754"), pedido.modelos());
    }

    @Test
    void unaReferenciaSinPielSaleIgualPeroSeAvisa() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("MUESTRA", "001", 3)), ReferenciaCorte::deAmi);

        assertEquals(1, pedido.articulos().size());
        assertTrue(pedido.avisos().get(0).contains("MUESTRA"));
    }

    @Test
    void unPedidoSinCantidadesAvisaDeQueHayQueTeclearlas() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("ULL729.AL0103", "001", 0)), ReferenciaCorte::deAmi);

        assertTrue(pedido.avisos().stream().anyMatch(aviso -> aviso.contains("cantidades")));
    }

    @Test
    void lasLineasSinReferenciaNoCuentan() {
        PedidoCorte pedido = PedidoCorte.agrupar(List.of(
                new LineaCorte("  ", "001", 3),
                new LineaCorte(null, "001", 3)), ReferenciaCorte::deAmi);

        assertTrue(pedido.articulos().isEmpty());
    }
}
```

<!-- fichero: src/test/java/com/puntotres/packinglist/service/corte/PedidoCorteRealTest.java -->
```java
package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Los dos excels de pedido REALES de la temporada, que son los únicos datos
 * de cliente del repo. Las cifras se han contado sobre el fichero, no sobre
 * los JSON de ejemplo.
 */
class PedidoCorteRealTest {

    @Test
    void amiSumaCadaColorSinMirarDestinacionNiTalla() throws IOException {
        PedidoCorte pedido = new AmiCorte().leerPedido(leer("EAN PUNTOTRES H26.xlsx"));

        assertEquals(Map.of("001 BLACK", 169, "718 VANILLA CREAM", 102, "A237 MOCHA", 31),
                bolsosDe(pedido, "ULL729.AL0103"));
        assertEquals(Map.of("001 BLACK", 1995, "A184 MASTIC BEIGE", 381, "A236 TRUFFLE", 809),
                bolsosDe(pedido, "USL728.AL0217"));
    }

    @Test
    void amiUnModeloVaEnVariasPieles() throws IOException {
        PedidoCorte pedido = new AmiCorte().leerPedido(leer("EAN PUNTOTRES H26.xlsx"));

        assertEquals(List.of("AL0137", "AL0206", "AL0218", "AL0219"), pedido.articulos().stream()
                .filter(articulo -> articulo.referencia().modelo().equals("ULL754"))
                .map(articulo -> articulo.referencia().piel())
                .toList());
    }

    @Test
    void amiSalenTodasLasReferenciasCinturonesIncluidos() throws IOException {
        PedidoCorte pedido = new AmiCorte().leerPedido(leer("EAN PUNTOTRES H26.xlsx"));

        assertEquals(25, pedido.articulos().size());
        assertTrue(pedido.articulos().stream()
                .anyMatch(articulo -> articulo.referencia().referencia().equals("UBL029.AL0104")));
        assertTrue(pedido.avisos().isEmpty(), pedido.avisos().toString());
    }

    @Test
    void apcSumaCadaColorYPartePorElGuion() throws IOException {
        PedidoCorte pedido = new ApcCorte().leerPedido(leer("APC_PEDIDO_FALL26.xlsx"));

        assertEquals(Map.of("LZZ", 569, "LAW", 200, "KAN", 111, "CAW", 97),
                bolsosDe(pedido, "PXCBC-F67008"));
        assertEquals(List.of("PXCBC", "PXCDS"), pedido.articulos().stream()
                .filter(articulo -> articulo.referencia().modelo().equals("F67008"))
                .map(articulo -> articulo.referencia().piel())
                .toList());
        assertEquals(16, pedido.articulos().size());
        assertTrue(pedido.avisos().isEmpty(), pedido.avisos().toString());
    }

    @Test
    void elServicioSoloConoceAmiYApc() {
        DocumentosCorteService servicio = new DocumentosCorteService(
                List.of(new AmiCorte(), new ApcCorte()));

        assertTrue(servicio.clientePara("ami").isPresent());
        assertTrue(servicio.clientePara("APC").isPresent());
        assertTrue(servicio.clientePara("ACKERMANN").isEmpty());
        assertTrue(servicio.clientePara(null).isEmpty());
    }

    private static Map<String, Integer> bolsosDe(PedidoCorte pedido, String referencia) {
        Map<String, Integer> bolsos = new LinkedHashMap<>();
        pedido.articulos().stream()
                .filter(articulo -> articulo.referencia().referencia().equals(referencia))
                .findFirst().orElseThrow()
                .colores().forEach(color -> bolsos.put(color.color(), color.bolsos()));
        return bolsos;
    }

    private static byte[] leer(String fichero) throws IOException {
        try (InputStream entrada = PedidoCorteRealTest.class.getResourceAsStream("/ejemplos/" + fichero)) {
            return entrada.readAllBytes();
        }
    }
}
```

- [ ] **Step 2: comprobar que fallan**

Run: `mvn -q test -Dtest="ReferenciaCorteTest,PedidoCorteTest,PedidoCorteRealTest"`
Expected: FAIL de compilación (no existen las clases del paquete `corte`).

- [ ] **Step 3: lectores de pedido con cantidad**

En `AmiPedidoExcel`, detrás del record `LineaCatalogo`, añadir el record y el método:

```java
    /**
     * Una fila del pedido con su cantidad, para los documentos del corte:
     * cuántos bolsos de cada referencia y color hay que cortar. El color va
     * con código y nombre ("001 BLACK"), que es como sale en la etiqueta y
     * como lo reconoce quien corta.
     *
     * Va aparte de {@link LineaCatalogo} a propósito: el catálogo que se le
     * enseña a Claude no debe llevar cantidades (ver su javadoc).
     */
    public record LineaConCantidad(String referencia, String color, int cantidad) {
    }

    /** Una por fila del fichero (o sea, por talla y PO), en su orden. */
    public List<LineaConCantidad> lineasConCantidad() {
        return filas.stream()
                .map(fila -> new LineaConCantidad(fila.article(),
                        fila.libelle().isBlank() ? fila.coloris()
                                : (fila.coloris() + " " + fila.libelle()).trim(),
                        fila.commande()))
                .toList();
    }
```

En `ApcPedidoExcel`: record `LineaConCantidad(String referencia, String color, int cantidad)` con el mismo javadoc adaptado ("el color es el código de la columna Couleurs, 'LZZ': el pedido de APC no trae su nombre"), un campo `private final List<LineaConCantidad> lineasConCantidad;` que se añade como quinto parámetro del constructor privado (`this.lineasConCantidad = List.copyOf(lineasConCantidad);`), una lista `lineasConCantidad` rellenada en el bucle de `desdeBytes` justo después de `catalogo.add(...)`:

```java
                lineasConCantidad.add(new LineaConCantidad(entrada.referencia(),
                        textoOpcional(hoja, fila, colColor),
                        colCantidad < 0 ? 0 : entero(texto(hoja, fila, colCantidad))));
```

y el acceso:

```java
    /** Una por fila del fichero (o sea, por talla), en su orden. */
    public List<LineaConCantidad> lineasConCantidad() {
        return lineasConCantidad;
    }
```

- [ ] **Step 4: el paquete `corte`**

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/ReferenciaCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.util.Locale;

/**
 * Una referencia del pedido partida en lo que necesita el corte: el MODELO
 * del bolso, que es lo que nombra la carpeta de fotos, y la referencia de la
 * PIEL, que es lo que decide qué material se corta.
 *
 * Cada cliente pone la piel en un sitio: AMI detrás del punto
 * ("ULL712.AL0103" = modelo ULL712 + piel AL0103) y APC delante del guion
 * ("PXCBC-F67008" = piel PXCBC + modelo F67008). Una referencia sin
 * separador se queda entera como modelo y sin piel: no se adivina.
 */
public record ReferenciaCorte(String referencia, String modelo, String piel) {

    public static ReferenciaCorte deAmi(String articulo) {
        String referencia = normalizar(articulo);
        int punto = referencia.indexOf('.');
        if (punto <= 0 || punto == referencia.length() - 1) {
            return new ReferenciaCorte(referencia, referencia, "");
        }
        return new ReferenciaCorte(referencia, referencia.substring(0, punto),
                referencia.substring(punto + 1));
    }

    public static ReferenciaCorte deApc(String articulo) {
        String referencia = normalizar(articulo);
        int guion = referencia.indexOf('-');
        if (guion <= 0 || guion == referencia.length() - 1) {
            return new ReferenciaCorte(referencia, referencia, "");
        }
        return new ReferenciaCorte(referencia, referencia.substring(guion + 1),
                referencia.substring(0, guion));
    }

    public boolean tienePiel() {
        return !piel.isEmpty();
    }

    /**
     * Mayúsculas y sin espacios alrededor. La usan también la lectura del zip
     * y el servicio: una carpeta "ull712" es el modelo ULL712.
     */
    public static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT);
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/LineaCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

/**
 * Una fila del excel de pedido tal como la necesita el corte: referencia
 * completa, color y unidades. La talla, la destinación y el número de pedido
 * van en filas distintas y aquí dan igual: se suman.
 */
public record LineaCorte(String referencia, String color, int cantidad) {
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/ColorCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

/** Un color de una referencia y cuántos bolsos de él hay que cortar. */
public record ColorCorte(String color, int bolsos) {
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/ArticuloCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.util.List;

/**
 * Una referencia del pedido (modelo + piel) con sus colores: una fila de la
 * pantalla de pieles. Los nombres de piel, combinaciones y forro son los
 * mismos para todos sus colores, así que se teclean una vez por artículo, y
 * de ella sale una orden de corte por color.
 */
public record ArticuloCorte(ReferenciaCorte referencia, List<ColorCorte> colores) {

    public ArticuloCorte {
        colores = List.copyOf(colores);
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/PedidoCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * El pedido de la temporada visto desde el corte: una entrada por referencia
 * (modelo + piel) con los bolsos de cada color.
 *
 * Los bolsos de un color son la suma de todas las líneas con esa referencia y
 * ese color, sin mirar destinación, talla ni número de pedido: al cortador le
 * da igual a dónde vaya el bolso, lo que corta es la piel. Salen TODAS las
 * referencias, cinturones incluidos: el alcance de las órdenes es el pedido.
 */
public record PedidoCorte(List<ArticuloCorte> articulos, List<String> avisos) {

    public PedidoCorte {
        articulos = List.copyOf(articulos);
        avisos = List.copyOf(avisos);
    }

    public static PedidoCorte agrupar(List<LineaCorte> lineas,
                                      Function<String, ReferenciaCorte> partir) {
        Map<String, Map<String, Integer>> porReferencia = new LinkedHashMap<>();
        for (LineaCorte linea : lineas) {
            String referencia = ReferenciaCorte.normalizar(linea.referencia());
            if (referencia.isEmpty()) {
                continue;
            }
            String color = linea.color() == null ? "" : linea.color().trim();
            porReferencia.computeIfAbsent(referencia, clave -> new LinkedHashMap<>())
                    .merge(color, Math.max(0, linea.cantidad()), Integer::sum);
        }

        List<ArticuloCorte> articulos = new ArrayList<>();
        List<String> sinPiel = new ArrayList<>();
        porReferencia.forEach((referencia, colores) -> {
            ReferenciaCorte partida = partir.apply(referencia);
            if (!partida.tienePiel()) {
                sinPiel.add(referencia);
            }
            articulos.add(new ArticuloCorte(partida, colores.entrySet().stream()
                    .map(color -> new ColorCorte(color.getKey(), color.getValue()))
                    .toList()));
        });
        articulos.sort(Comparator
                .comparing((ArticuloCorte articulo) -> articulo.referencia().modelo())
                .thenComparing(articulo -> articulo.referencia().piel()));

        List<String> avisos = new ArrayList<>();
        if (!sinPiel.isEmpty()) {
            avisos.add("Referencias del pedido sin la piel separada, que salen con el modelo "
                    + "entero y la piel en blanco: " + String.join(", ", sinPiel));
        }
        boolean sinCantidades = !articulos.isEmpty() && articulos.stream()
                .flatMap(articulo -> articulo.colores().stream())
                .allMatch(color -> color.bolsos() == 0);
        if (sinCantidades) {
            avisos.add("El excel de pedido no trae cantidades: el número de bolsos de cada "
                    + "color hay que teclearlo");
        }
        return new PedidoCorte(articulos, avisos);
    }

    /** Los modelos del pedido, sin repetir y en orden: los nombres de carpeta que se buscan en el zip. */
    public Set<String> modelos() {
        Set<String> modelos = new LinkedHashSet<>();
        articulos.forEach(articulo -> modelos.add(articulo.referencia().modelo()));
        return modelos;
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/ClienteCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.io.IOException;

/**
 * Lo que cambia de un cliente a otro en los documentos del corte: cómo se
 * lee su excel de pedido y dónde lleva la piel su referencia. Se despacha por
 * clave de cliente, como las etiquetas; un cliente sin implementación sale
 * "en desarrollo" en la pantalla.
 */
public interface ClienteCorte {

    /** Clave del cliente en {@code packing-list.clientes} (AMI, APC). */
    String clave();

    /**
     * Lanza IllegalArgumentException con un mensaje en español si el fichero
     * no es el pedido de este cliente.
     */
    PedidoCorte leerPedido(byte[] excel) throws IOException;
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/AmiCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.service.etiquetas.AmiPedidoExcel;

/**
 * El pedido de AMI para el corte: referencia "MODELO.PIEL" ("ULL712.AL0103")
 * y color con código y nombre ("001 BLACK").
 *
 * Los avisos del lector del pedido no se trasladan: hablan de las columnas de
 * EAN y de "Made in", que al corte no le afectan.
 */
@Component
public class AmiCorte implements ClienteCorte {

    @Override
    public String clave() {
        return "AMI";
    }

    @Override
    public PedidoCorte leerPedido(byte[] excel) throws IOException {
        List<LineaCorte> lineas = AmiPedidoExcel.desdeBytes(excel).lineasConCantidad().stream()
                .map(linea -> new LineaCorte(linea.referencia(), linea.color(), linea.cantidad()))
                .toList();
        return PedidoCorte.agrupar(lineas, ReferenciaCorte::deAmi);
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/ApcCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.puntotres.packinglist.service.ApcPedidoExcel;

/**
 * El pedido de APC para el corte: referencia "PIEL-MODELO" ("PXCBC-F67008")
 * y color con el código de la columna Couleurs ("LZZ"), porque el pedido de
 * APC no trae el nombre del color.
 *
 * Los avisos del lector del pedido no se trasladan: hablan de claves que la
 * entrada por taller no puede completar, que al corte no le afectan.
 */
@Component
public class ApcCorte implements ClienteCorte {

    @Override
    public String clave() {
        return "APC";
    }

    @Override
    public PedidoCorte leerPedido(byte[] excel) throws IOException {
        List<LineaCorte> lineas = ApcPedidoExcel.desdeBytes(excel).lineasConCantidad().stream()
                .map(linea -> new LineaCorte(linea.referencia(), linea.color(), linea.cantidad()))
                .toList();
        return PedidoCorte.agrupar(lineas, ReferenciaCorte::deApc);
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/DocumentosCorteService.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Los documentos del corte: a qué cliente le toca cada pedido y, a partir de
 * lo tecleado en la pantalla de pieles, los Word que salen.
 */
@Service
public class DocumentosCorteService {

    private final List<ClienteCorte> clientes;

    public DocumentosCorteService(List<ClienteCorte> clientes) {
        this.clientes = List.copyOf(clientes);
    }

    /** El cliente con esa clave, o vacío si todavía no tiene documentos del corte. */
    public Optional<ClienteCorte> clientePara(String clave) {
        String buscada = ReferenciaCorte.normalizar(clave);
        return clientes.stream()
                .filter(cliente -> cliente.clave().equals(buscada))
                .findFirst();
    }
}
```

- [ ] **Step 5: comprobar que pasan**

Run: `mvn -q test -Dtest="ReferenciaCorteTest,PedidoCorteTest,PedidoCorteRealTest"`
Expected: PASS. Si una cifra real no cuadra, contarla otra vez sobre el fichero (el lector de AMI se salta filas sin PO) y corregir el test solo si el fichero lo confirma.

- [ ] **Step 6: commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/corte src/test/java/com/puntotres/packinglist/service/corte src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java src/main/java/com/puntotres/packinglist/service/ApcPedidoExcel.java
git commit -m "el pedido se parte en modelo, piel y bolsos por color para el corte"
```

---

### Tarea 2: lectura del zip de fotos

**Files:**
- Create: `service/corte/FotoModelo.java`, `FotosTemporada.java`, `LectorZipFotos.java`
- Test: `src/test/java/com/puntotres/packinglist/service/corte/LectorZipFotosTest.java`

**Interfaces:**
- Consumes: `ReferenciaCorte.normalizar(String)`.
- Produces: `record FotoModelo(String modelo, String nombreOriginal, Path original)`; `record FotosTemporada(Map<String, List<FotoModelo>> porModelo, List<String> avisos)` con `de(String modelo)`, `todas()`, `total()`, `modelosSinFotos(Collection<String>)`; `new LectorZipFotos().leer(Path zip, Set<String> modelos, Path destino) → FotosTemporada` (lanza `IllegalArgumentException` si pasa los topes, `IOException` si no es un zip).

- [ ] **Step 1: test que falla**

<!-- fichero: src/test/java/com/puntotres/packinglist/service/corte/LectorZipFotosTest.java -->
```java
package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LectorZipFotosTest {

    private static final byte[] FOTO = {1, 2, 3, 4};
    private static final Set<String> MODELOS = Set.of("ULL027", "F67008");

    @TempDir
    Path dir;

    @Test
    void cadaFotoVaASuModeloAunqueHayaCarpetaRaizOSubcarpetas() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("H26/ULL027/b.jpg", FOTO);
        entradas.put("H26/ULL027/A.HEIC", FOTO);
        entradas.put("H26/ULL027/detalles/c.jpeg", FOTO);
        entradas.put("H26/f67008/y.jpg", FOTO);

        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, entradas));

        assertEquals(List.of("A.HEIC", "b.jpg", "c.jpeg"), nombres(fotos.de("ULL027")));
        assertEquals(List.of("y.jpg"), nombres(fotos.de("F67008")));
        assertEquals(4, fotos.total());
        assertTrue(fotos.avisos().isEmpty(), fotos.avisos().toString());
    }

    @Test
    void loQueNoCaeEnNingunModeloSeAvisaYNoSeUsa() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("H26/ULL999/x.jpg", FOTO);
        entradas.put("H26/ULL999/z.jpg", FOTO);
        entradas.put("suelta.jpg", FOTO);
        entradas.put("H26/ULL027/notas.txt", FOTO);

        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, entradas));

        assertEquals(0, fotos.total());
        String avisos = String.join("\n", fotos.avisos());
        assertTrue(avisos.contains("ULL999") && avisos.contains("sus 2 fotos"), avisos);
        assertTrue(avisos.contains("suelta.jpg"), avisos);
        assertTrue(avisos.contains("notas.txt"), avisos);
    }

    @Test
    void laBasuraDelSistemaSeIgnoraSinAvisar() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("__MACOSX/H26/ULL027/._b.jpg", FOTO);
        entradas.put("H26/ULL027/._b.jpg", FOTO);
        entradas.put("H26/ULL027/Thumbs.db", FOTO);
        entradas.put("H26/ULL027/.DS_Store", FOTO);
        entradas.put("H26/ULL027/b.jpg", FOTO);

        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, entradas));

        assertEquals(List.of("b.jpg"), nombres(fotos.de("ULL027")));
        assertTrue(fotos.avisos().isEmpty(), fotos.avisos().toString());
    }

    @Test
    void lasFotosSeCopianConNombreGeneradoYSuContenido() throws IOException {
        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, Map.of("ULL027/Foto 1.JPG", FOTO)));

        FotoModelo foto = fotos.de("ULL027").get(0);
        assertTrue(foto.original().getFileName().toString().matches("\\d{5}\\.jpg"));
        assertArrayEquals(FOTO, Files.readAllBytes(foto.original()));
    }

    @Test
    void unaEntradaConPuntosPuntosNoEscribeFueraDelDirectorioDeTrabajo() throws IOException {
        Path trabajo = dir.resolve("trabajo");
        leer(zip(StandardCharsets.UTF_8, Map.of("ULL027/../../../fuera.jpg", FOTO)), trabajo);

        try (Stream<Path> todo = Files.walk(dir)) {
            assertTrue(todo.filter(Files::isRegularFile)
                    .filter(fichero -> !fichero.getFileName().toString().equals("fotos.zip"))
                    .allMatch(fichero -> fichero.startsWith(trabajo)));
        }
        assertFalse(Files.exists(dir.getParent().resolve("fuera.jpg")));
    }

    @Test
    void unZipDelExploradorDeWindowsSinUtf8SeLeeIgual() throws IOException {
        FotosTemporada fotos = leer(zip(Charset.forName("IBM437"), Map.of("ULL027/foto ñ.jpg", FOTO)));

        assertEquals(List.of("foto ñ.jpg"), nombres(fotos.de("ULL027")));
    }

    @Test
    void losModelosSinFotosSeListan() throws IOException {
        FotosTemporada fotos = leer(zip(StandardCharsets.UTF_8, Map.of("ULL027/a.jpg", FOTO)));

        assertEquals(List.of("F67008", "ULL745"),
                fotos.modelosSinFotos(List.of("ULL027", "ull745", "F67008")));
    }

    @Test
    void losTopesParanUnZipDesproporcionado() throws IOException {
        Path grande = zip(StandardCharsets.UTF_8, Map.of("ULL027/a.jpg", new byte[2048]));
        Path muchos = zip(StandardCharsets.UTF_8, Map.of("ULL027/a.jpg", FOTO, "ULL027/b.jpg", FOTO,
                "ULL027/c.jpg", FOTO));

        assertThrows(IllegalArgumentException.class,
                () -> new LectorZipFotos(1024, 100).leer(grande, MODELOS, dir.resolve("t1")));
        assertThrows(IllegalArgumentException.class,
                () -> new LectorZipFotos(1024 * 1024, 2).leer(muchos, MODELOS, dir.resolve("t2")));
    }

    @Test
    void loQueNoEsUnZipEsUnaIOException() throws IOException {
        Path falso = dir.resolve("falso.zip");
        Files.writeString(falso, "no soy un zip");

        assertThrows(IOException.class,
                () -> new LectorZipFotos().leer(falso, MODELOS, dir.resolve("t")));
    }

    private FotosTemporada leer(Path zip) throws IOException {
        return leer(zip, dir.resolve("trabajo"));
    }

    private static FotosTemporada leer(Path zip, Path trabajo) throws IOException {
        return new LectorZipFotos().leer(zip, MODELOS, trabajo);
    }

    private Path zip(Charset charset, Map<String, byte[]> entradas) throws IOException {
        Path zip = dir.resolve("fotos.zip");
        try (ZipOutputStream salida = new ZipOutputStream(Files.newOutputStream(zip), charset)) {
            for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                salida.putNextEntry(new ZipEntry(entrada.getKey()));
                salida.write(entrada.getValue());
                salida.closeEntry();
            }
        }
        return zip;
    }

    private static List<String> nombres(List<FotoModelo> fotos) {
        return fotos.stream().map(FotoModelo::nombreOriginal).toList();
    }
}
```

- [ ] **Step 2: comprobar que falla**

Run: `mvn -q test -Dtest=LectorZipFotosTest`
Expected: FAIL de compilación (`LectorZipFotos` no existe).

- [ ] **Step 3: implementación**

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/FotoModelo.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.nio.file.Path;

/**
 * Una foto del zip, ya fuera de él: de qué modelo es, cómo se llamaba (para
 * ordenarla y enseñarla en el desplegable de foto principal) y dónde está la
 * copia, que lleva un nombre generado y no el del zip.
 */
public record FotoModelo(String modelo, String nombreOriginal, Path original) {
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/FotosTemporada.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Las fotos de la temporada por modelo, cada lista ordenada por nombre de
 * fichero, y los avisos de lo que había en el zip y no se usa.
 */
public record FotosTemporada(Map<String, List<FotoModelo>> porModelo, List<String> avisos) {

    public FotosTemporada {
        Map<String, List<FotoModelo>> copia = new TreeMap<>();
        porModelo.forEach((modelo, fotos) -> copia.put(modelo, List.copyOf(fotos)));
        porModelo = Collections.unmodifiableMap(copia);
        avisos = List.copyOf(avisos);
    }

    /** Las fotos de un modelo, o ninguna. La foto es del modelo: vale para todas sus pieles. */
    public List<FotoModelo> de(String modelo) {
        return porModelo.getOrDefault(ReferenciaCorte.normalizar(modelo), List.of());
    }

    public List<FotoModelo> todas() {
        return porModelo.values().stream().flatMap(List::stream).toList();
    }

    public int total() {
        return porModelo.values().stream().mapToInt(List::size).sum();
    }

    /** Los modelos que no han traído ninguna foto, sin repetir y ordenados. */
    public List<String> modelosSinFotos(Collection<String> modelos) {
        return modelos.stream()
                .map(ReferenciaCorte::normalizar)
                .filter(modelo -> !porModelo.containsKey(modelo))
                .distinct()
                .sorted()
                .toList();
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/LectorZipFotos.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Lee el zip de la carpeta de temporada: una subcarpeta por modelo con sus
 * fotos dentro.
 *
 * La carpeta de una foto es el antecesor MÁS CERCANO cuyo nombre es un modelo
 * del pedido, así que da igual que el zip traiga la carpeta de temporada como
 * raíz ("H26/ULL027/foto.jpg") o que un modelo tenga subcarpetas propias
 * ("ULL027/detalles/foto.jpg"). Lo que no cae en ningún modelo se avisa y no
 * se usa: una foto en la carpeta equivocada acabaría en el documento de otro
 * bolso.
 *
 * Los nombres de las entradas NUNCA se usan como ruta al descomprimir (zip
 * slip): cada foto se escribe con un nombre generado dentro del directorio de
 * trabajo, y el original solo sirve para ordenarla y enseñarla. Los topes de
 * tamaño y de entradas paran un zip que no es la carpeta de una temporada
 * antes de que llene el disco.
 */
public class LectorZipFotos {

    static final long MAXIMO_BYTES = 3L * 1024 * 1024 * 1024;
    static final int MAXIMO_ENTRADAS = 5_000;

    private static final Set<String> EXTENSIONES = Set.of("heic", "heif", "jpg", "jpeg");
    /** Lo que los sistemas operativos siembran en las carpetas: se ignora sin avisar. */
    private static final Set<String> BASURA = Set.of(".DS_STORE", "THUMBS.DB", "DESKTOP.INI");
    private static final int MAXIMO_NOMBRES_EN_AVISO = 10;

    private final long maximoBytes;
    private final int maximoEntradas;

    public LectorZipFotos() {
        this(MAXIMO_BYTES, MAXIMO_ENTRADAS);
    }

    LectorZipFotos(long maximoBytes, int maximoEntradas) {
        this.maximoBytes = maximoBytes;
        this.maximoEntradas = maximoEntradas;
    }

    public FotosTemporada leer(Path zip, Set<String> modelos, Path destino) throws IOException {
        Set<String> buscados = new HashSet<>();
        modelos.forEach(modelo -> buscados.add(ReferenciaCorte.normalizar(modelo)));
        Path originales = Files.createDirectories(destino.resolve("originales"));

        Map<String, List<FotoModelo>> porModelo = new TreeMap<>();
        Map<String, Integer> carpetasFuera = new TreeMap<>();
        List<String> sueltas = new ArrayList<>();
        List<String> noSonFotos = new ArrayList<>();
        try (ZipFile archivo = abrir(zip)) {
            int entradas = 0;
            long bytes = 0;
            int numero = 0;
            Enumeration<? extends ZipEntry> todas = archivo.entries();
            while (todas.hasMoreElements()) {
                ZipEntry entrada = todas.nextElement();
                if (++entradas > maximoEntradas) {
                    throw new IllegalArgumentException("trae más de " + maximoEntradas
                            + " ficheros: no parece la carpeta de fotos de una temporada");
                }
                if (entrada.isDirectory()) {
                    continue;
                }
                List<String> segmentos = Arrays.stream(entrada.getName().replace('\\', '/').split("/"))
                        .filter(segmento -> !segmento.isBlank())
                        .toList();
                if (segmentos.isEmpty()) {
                    continue;
                }
                String nombre = segmentos.get(segmentos.size() - 1);
                List<String> carpetas = segmentos.subList(0, segmentos.size() - 1);
                if (esBasura(nombre, carpetas)) {
                    continue;
                }
                String extension = extensionDe(nombre);
                if (!EXTENSIONES.contains(extension)) {
                    noSonFotos.add(String.join("/", segmentos));
                    continue;
                }
                Optional<String> modelo = modeloDe(carpetas, buscados);
                if (modelo.isEmpty()) {
                    if (carpetas.isEmpty()) {
                        sueltas.add(nombre);
                    } else {
                        carpetasFuera.merge(carpetas.get(carpetas.size() - 1), 1, Integer::sum);
                    }
                    continue;
                }
                Path copia = originales.resolve(String.format("%05d.%s", ++numero, extension));
                try (InputStream contenido = archivo.getInputStream(entrada)) {
                    bytes += copiar(contenido, copia, maximoBytes - bytes);
                }
                porModelo.computeIfAbsent(modelo.get(), clave -> new ArrayList<>())
                        .add(new FotoModelo(modelo.get(), nombre, copia));
            }
        }
        porModelo.values().forEach(fotos -> fotos.sort(
                Comparator.comparing(FotoModelo::nombreOriginal, String.CASE_INSENSITIVE_ORDER)));

        List<String> avisos = new ArrayList<>();
        carpetasFuera.forEach((carpeta, fotos) -> avisos.add("La carpeta " + carpeta
                + " no es ningún modelo del pedido: "
                + (fotos == 1 ? "su foto no se usa" : "sus " + fotos + " fotos no se usan")));
        if (!sueltas.isEmpty()) {
            avisos.add("Fotos sueltas, fuera de las carpetas de modelo, que no se usan: "
                    + listaCorta(sueltas));
        }
        if (!noSonFotos.isEmpty()) {
            avisos.add("Ficheros que no son fotos HEIC ni JPG y se ignoran: " + listaCorta(noSonFotos));
        }
        return new FotosTemporada(porModelo, avisos);
    }

    /**
     * El Explorador de Windows escribe los nombres en la página de códigos de
     * la consola (CP437) sin marcar la entrada como UTF-8, y leídos como UTF-8
     * Java los rechaza. CP437 no rechaza ningún byte, así que es la segunda
     * lectura; un fichero que no es un zip falla en las dos.
     */
    private static ZipFile abrir(Path zip) throws IOException {
        try {
            return comprobado(new ZipFile(zip.toFile(), StandardCharsets.UTF_8));
        } catch (ZipException | IllegalArgumentException e) {
            return comprobado(new ZipFile(zip.toFile(), Charset.forName("IBM437")));
        }
    }

    /** Recorre los nombres una vez: según la versión, Java no protesta hasta leerlos. */
    private static ZipFile comprobado(ZipFile archivo) throws IOException {
        try {
            Enumeration<? extends ZipEntry> entradas = archivo.entries();
            while (entradas.hasMoreElements()) {
                entradas.nextElement().getName();
            }
            return archivo;
        } catch (RuntimeException e) {
            archivo.close();
            throw e;
        }
    }

    private long copiar(InputStream origen, Path destino, long margen) throws IOException {
        byte[] bufer = new byte[64 * 1024];
        long copiados = 0;
        try (OutputStream salida = Files.newOutputStream(destino)) {
            int leidos;
            while ((leidos = origen.read(bufer)) != -1) {
                copiados += leidos;
                if (copiados > margen) {
                    throw new IllegalArgumentException("descomprimido ocupa más de "
                            + maximoBytes / (1024 * 1024) + " MB: no parece la carpeta de fotos de "
                            + "una temporada");
                }
                salida.write(bufer, 0, leidos);
            }
        }
        return copiados;
    }

    private static boolean esBasura(String nombre, List<String> carpetas) {
        return carpetas.contains("__MACOSX")
                || nombre.startsWith("._")
                || BASURA.contains(nombre.toUpperCase(Locale.ROOT));
    }

    private static String extensionDe(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private static Optional<String> modeloDe(List<String> carpetas, Set<String> modelos) {
        for (int i = carpetas.size() - 1; i >= 0; i--) {
            String carpeta = ReferenciaCorte.normalizar(carpetas.get(i));
            if (modelos.contains(carpeta)) {
                return Optional.of(carpeta);
            }
        }
        return Optional.empty();
    }

    private static String listaCorta(List<String> nombres) {
        if (nombres.size() <= MAXIMO_NOMBRES_EN_AVISO) {
            return String.join(", ", nombres);
        }
        return String.join(", ", nombres.subList(0, MAXIMO_NOMBRES_EN_AVISO))
                + " y " + (nombres.size() - MAXIMO_NOMBRES_EN_AVISO) + " más";
    }
}
```

- [ ] **Step 4: comprobar que pasa**

Run: `mvn -q test -Dtest=LectorZipFotosTest`
Expected: PASS.

- [ ] **Step 5: commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/corte src/test/java/com/puntotres/packinglist/service/corte/LectorZipFotosTest.java
git commit -m "el zip de la temporada se reparte por modelo sin fiarse de sus rutas"
```

---

### Tarea 3: conversión de fotos (HEIC y JPG → JPEG reducido)

**Files:**
- Modify: `pom.xml` (repositorio de Aspose, `openize-heic`, `metadata-extractor`)
- Create: `service/corte/ReductorImagen.java`, `DecodificadorHeicJava.java`, `ConversorHeicWindows.java`, `ConversionFotos.java`, `ConversorFotos.java`, `src/main/resources/corte/heic-a-jpeg.ps1`
- Create (test): `src/test/resources/ejemplos/corte/gimp_rgb_420_with_alpha.heic` (8 KB, del repo de Openize), `src/test/java/com/puntotres/packinglist/testutil/FotosDePrueba.java`
- Test: `ReductorImagenTest.java`, `DecodificadorHeicJavaTest.java`, `ConversorHeicWindowsTest.java`, `ConversorFotosTest.java` (en `service/corte`)

**Interfaces:**
- Consumes: `FotoModelo`.
- Produces: `ReductorImagen.LADO_MAXIMO`, `reducir(BufferedImage, int)`, `orientar(BufferedImage, int)`, `orientacionExif(Path)`, `escribirJpeg(BufferedImage, Path)`; `ConversorFotos.convertir(List<FotoModelo>, Path directorio) → ConversionFotos`; `ConversionFotos.total()`, `hechas()`, `terminada()`, `esperar()`, `reducida(FotoModelo) → Optional<Path>`, `avisos()`, `cancelar()`; `FotosDePrueba.jpeg(int, int, Color)`, `jpeg(BufferedImage)`, `relleno(int, int, String, Color)`, `conOrientacionExif(byte[], int)`, `heicDePrueba()`.

- [ ] **Step 1: dependencias y fixture**

En `pom.xml`, antes de `<dependencies>`:

```xml
    <!-- Openize.HEIC no está en Maven Central: lo publica Aspose en su repositorio. -->
    <repositories>
        <repository>
            <id>aspose</id>
            <name>Aspose (Openize.HEIC)</name>
            <url>https://releases.aspose.com/java/repo/</url>
        </repository>
    </repositories>
```

y dentro de `<dependencies>`, detrás de barcode4j:

```xml
        <!-- Documentos del corte: las fotos del iPhone llegan en HEIC y ni
             ImageIO ni POI saben leerlo. Java puro para que funcione igual en
             el PC del almacén y en el servidor Linux; en Windows se prueba
             antes el decodificador del sistema, que es más rápido. -->
        <dependency>
            <groupId>com.aspose</groupId>
            <artifactId>openize-heic</artifactId>
            <version>26.5</version>
        </dependency>

        <!-- Orientación EXIF de los JPG: sin ella las fotos de móvil salen tumbadas. -->
        <dependency>
            <groupId>com.drewnoakes</groupId>
            <artifactId>metadata-extractor</artifactId>
            <version>2.20.0</version>
        </dependency>
```

Fixture: `curl -sL -o "src/test/resources/ejemplos/corte/gimp_rgb_420_with_alpha.heic" https://raw.githubusercontent.com/openize-com/openize-heic-java/main/TestsData/samples/gimp_rgb_420_with_alpha.heic` (8.295 bytes, 430×430 con alfa).

- [ ] **Step 2: utilidades de test y tests que fallan**

<!-- fichero: src/test/java/com/puntotres/packinglist/testutil/FotosDePrueba.java -->
```java
package com.puntotres.packinglist.testutil;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * Fotos fabricadas para los tests de los documentos del corte. Ninguna es una
 * foto de verdad: en el repo no entra ninguna foto personal ni de producto.
 */
public final class FotosDePrueba {

    private FotosDePrueba() {
    }

    /** Un JPEG de un solo color. */
    public static byte[] jpeg(int ancho, int alto, Color color) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, ancho, alto);
        g.dispose();
        return jpeg(imagen);
    }

    public static byte[] jpeg(BufferedImage imagen) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "jpg", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Una imagen de relleno con la silueta de un bolso y un rótulo, para los
     * Word de ejemplo que se revisan a ojo: se ve dónde cae cada foto y cómo
     * encaja según sea apaisada o vertical.
     */
    public static byte[] relleno(int ancho, int alto, String texto, Color color) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, color.brighter(), 0, alto, color.darker()));
        g.fillRect(0, 0, ancho, alto);
        int cuerpoAncho = ancho * 3 / 5;
        int cuerpoAlto = Math.min(alto * 2 / 5, cuerpoAncho);
        int x = (ancho - cuerpoAncho) / 2;
        int y = alto / 2 - cuerpoAlto / 4;
        g.setColor(new Color(255, 255, 255, 210));
        g.setStroke(new BasicStroke(Math.max(4, ancho / 60f)));
        g.drawArc(x + cuerpoAncho / 4, y - cuerpoAlto / 2, cuerpoAncho / 2, cuerpoAlto, 0, 180);
        g.fillRoundRect(x, y, cuerpoAncho, cuerpoAlto, ancho / 15, ancho / 15);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, ancho / 14)));
        FontMetrics medidas = g.getFontMetrics();
        g.drawString(texto, (ancho - medidas.stringWidth(texto)) / 2,
                y + cuerpoAlto / 2 + medidas.getAscent() / 3);
        g.dispose();
        return jpeg(imagen);
    }

    /**
     * El mismo JPEG con un bloque EXIF que solo trae la orientación, como el
     * que escribe un móvil que guarda la foto sin girarla. Va detrás del APP0
     * JFIF: delante, el lector JPEG de Java protesta.
     */
    public static byte[] conOrientacionExif(byte[] jpeg, int orientacion) {
        byte[] tiff = {
                'M', 'M', 0, 42, 0, 0, 0, 8,
                0, 1,
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientacion, 0, 0,
                0, 0, 0, 0};
        byte[] cabecera = {'E', 'x', 'i', 'f', 0, 0};
        int longitud = 2 + cabecera.length + tiff.length;
        int posicion = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            posicion = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(jpeg, 0, posicion);
        salida.write(0xFF);
        salida.write(0xE1);
        salida.write(longitud >> 8);
        salida.write(longitud & 0xFF);
        salida.writeBytes(cabecera);
        salida.writeBytes(tiff);
        salida.write(jpeg, posicion, jpeg.length - posicion);
        return salida.toByteArray();
    }

    /**
     * Un HEIC de verdad y pequeño (430×430, 8 KB): el de prueba del propio
     * Openize, "gimp_rgb_420_with_alpha.heic", con la licencia de su repo.
     */
    public static byte[] heicDePrueba() {
        try (InputStream entrada = FotosDePrueba.class.getResourceAsStream(
                "/ejemplos/corte/gimp_rgb_420_with_alpha.heic")) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

<!-- fichero: src/test/java/com/puntotres/packinglist/service/corte/ReductorImagenTest.java -->
```java
package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class ReductorImagenTest {

    @Test
    void unaFotoGrandeSeQuedaEnElLadoMaximo() {
        BufferedImage reducida = ReductorImagen.reducir(
                new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB), 1600);

        assertEquals(1600, reducida.getWidth());
        assertEquals(1200, reducida.getHeight());
    }

    @Test
    void unaFotoPequenaNoSeAmpliaPeroSaleEnRgb() {
        BufferedImage reducida = ReductorImagen.reducir(
                new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB), 1600);

        assertEquals(800, reducida.getWidth());
        assertEquals(BufferedImage.TYPE_INT_RGB, reducida.getType());
    }

    @Test
    void loTransparenteSaleBlancoYNoNegro() {
        BufferedImage reducida = ReductorImagen.reducir(
                new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB), 1600);

        assertEquals(Color.WHITE.getRGB(), reducida.getRGB(5, 5));
    }

    @Test
    void laOrientacionSeisGiraNoventaGradosALaDerecha() {
        BufferedImage girada = ReductorImagen.orientar(marcaArribaIzquierda(), 6);

        assertEquals(20, girada.getWidth());
        assertEquals(40, girada.getHeight());
        assertTrue(esRojo(girada.getRGB(15, 5)), "la esquina de arriba a la izquierda pasa a arriba a la derecha");
        assertTrue(!esRojo(girada.getRGB(5, 5)));
    }

    @Test
    void laOrientacionOchoGiraALaIzquierdaYLaTresMediaVuelta() {
        BufferedImage ocho = ReductorImagen.orientar(marcaArribaIzquierda(), 8);
        BufferedImage tres = ReductorImagen.orientar(marcaArribaIzquierda(), 3);

        assertTrue(esRojo(ocho.getRGB(5, 35)), "abajo a la izquierda");
        assertTrue(esRojo(tres.getRGB(35, 15)), "abajo a la derecha");
    }

    @Test
    void sinOrientacionOConUnaDesconocidaSeQuedaIgual() {
        BufferedImage original = marcaArribaIzquierda();

        assertEquals(original, ReductorImagen.orientar(original, 1));
        assertEquals(original, ReductorImagen.orientar(original, 0));
    }

    @Test
    void laOrientacionSeLeeDelExifDelJpeg(@TempDir Path dir) throws IOException {
        Path conExif = dir.resolve("con.jpg");
        Path sinExif = dir.resolve("sin.jpg");
        Files.write(conExif, FotosDePrueba.conOrientacionExif(FotosDePrueba.jpeg(30, 20, Color.GRAY), 6));
        Files.write(sinExif, FotosDePrueba.jpeg(30, 20, Color.GRAY));

        assertEquals(6, ReductorImagen.orientacionExif(conExif));
        assertEquals(1, ReductorImagen.orientacionExif(sinExif));
        assertEquals(30, ImageIO.read(conExif.toFile()).getWidth(), "el JPEG con EXIF se sigue leyendo");
    }

    @Test
    void escribirSobreUnFicheroMasGrandeNoDejaRestos(@TempDir Path dir) throws IOException {
        Path destino = dir.resolve("f.jpg");
        Files.write(destino, new byte[500_000]);

        ReductorImagen.escribirJpeg(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), destino);

        assertTrue(Files.size(destino) < 10_000);
        assertEquals(20, ImageIO.read(destino.toFile()).getWidth());
    }

    /** 40×20 en blanco con un cuadrado rojo de 10×10 en la esquina de arriba a la izquierda. */
    private static BufferedImage marcaArribaIzquierda() {
        BufferedImage imagen = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 40, 20);
        g.setColor(Color.RED);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        return imagen;
    }

    private static boolean esRojo(int rgb) {
        Color color = new Color(rgb);
        return color.getRed() > 200 && color.getGreen() < 60;
    }
}
```

<!-- fichero: src/test/java/com/puntotres/packinglist/service/corte/DecodificadorHeicJavaTest.java -->
```java
package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class DecodificadorHeicJavaTest {

    @Test
    void decodificaUnHeicDeVerdad(@TempDir Path dir) throws Exception {
        Path heic = dir.resolve("a.heic");
        Files.write(heic, FotosDePrueba.heicDePrueba());

        BufferedImage imagen = new DecodificadorHeicJava().decodificar(heic);

        assertEquals(430, imagen.getWidth());
        assertEquals(430, imagen.getHeight());
    }

    @Test
    void conUnPresupuestoMinimoLaFotoSaleIgualAunqueVayaSola(@TempDir Path dir) throws Exception {
        Path heic = dir.resolve("a.heic");
        Files.write(heic, FotosDePrueba.heicDePrueba());

        assertEquals(430, new DecodificadorHeicJava(1).decodificar(heic).getWidth());
    }

    @Test
    void loQueNoEsUnHeicEsUnaIOException(@TempDir Path dir) throws IOException {
        Path falso = dir.resolve("falso.heic");
        Files.writeString(falso, "no soy un heic");

        assertThrows(IOException.class, () -> new DecodificadorHeicJava().decodificar(falso));
    }
}
```

<!-- fichero: src/test/java/com/puntotres/packinglist/service/corte/ConversorHeicWindowsTest.java -->
```java
package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.service.corte.ConversorHeicWindows.Trabajo;
import com.puntotres.packinglist.testutil.FotosDePrueba;

/**
 * El camino rápido de Windows. Solo corre en Windows, y el primer test se
 * salta si el sistema no trae el códec HEIF/HEVC: sin él, todas las fotos
 * pasan al camino Java, que ya tiene sus tests.
 */
@EnabledOnOs(OS.WINDOWS)
class ConversorHeicWindowsTest {

    @Test
    void convierteElHeicAJpeg(@TempDir Path dir) throws Exception {
        Path heic = dir.resolve("a.heic");
        Files.write(heic, FotosDePrueba.heicDePrueba());
        Path salida = dir.resolve("a.jpg");
        List<Trabajo> convertidos = new ArrayList<>();

        List<Trabajo> pendientes = new ConversorHeicWindows().convertir(
                List.of(new Trabajo(heic, salida)), dir, proceso -> { }, convertidos::add);

        assumeTrue(pendientes.isEmpty(), "este Windows no trae el códec HEIF/HEVC");
        assertEquals(1, convertidos.size());
        assertEquals(430, ImageIO.read(salida.toFile()).getWidth());
    }

    @Test
    void loQueWindowsNoLeeVuelveComoPendienteSinDejarFichero(@TempDir Path dir) throws Exception {
        Path roto = dir.resolve("roto.heic");
        Files.writeString(roto, "no soy una foto");
        Path salida = dir.resolve("roto.jpg");

        List<Trabajo> pendientes = new ConversorHeicWindows().convertir(
                List.of(new Trabajo(roto, salida)), dir, proceso -> { }, trabajo -> { });

        assertEquals(1, pendientes.size());
        assertFalse(Files.exists(salida));
    }
}
```

<!-- fichero: src/test/java/com/puntotres/packinglist/service/corte/ConversorFotosTest.java -->
```java
package com.puntotres.packinglist.service.corte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class ConversorFotosTest {

    @TempDir
    Path dir;

    @Test
    void convierteJpgYHeicYAvisaDeLoQueNoSeLee() throws Exception {
        FotoModelo grande = foto("ULL027", "grande.jpg", "00001.jpg",
                FotosDePrueba.conOrientacionExif(FotosDePrueba.jpeg(3000, 2000, Color.GRAY), 6));
        FotoModelo heic = foto("ULL027", "b.HEIC", "00002.heic", FotosDePrueba.heicDePrueba());
        FotoModelo rota = foto("ULL712", "rota.jpg", "00003.jpg",
                "no soy un jpeg".getBytes(StandardCharsets.UTF_8));
        FotoModelo heicRoto = foto("ULL712", "rota.heic", "00004.heic",
                "tampoco soy un heic".getBytes(StandardCharsets.UTF_8));

        ConversionFotos conversion = new ConversorFotos(new DecodificadorHeicJava(), null)
                .convertir(List.of(grande, heic, rota, heicRoto), dir);
        conversion.esperar();

        assertTrue(conversion.terminada());
        assertEquals(4, conversion.total());
        assertEquals(4, conversion.hechas());
        BufferedImage enderezada = ImageIO.read(conversion.reducida(grande).orElseThrow().toFile());
        assertEquals(1067, enderezada.getWidth(), "3000x2000 girada por el EXIF y reducida");
        assertEquals(1600, enderezada.getHeight());
        assertEquals(430, ImageIO.read(conversion.reducida(heic).orElseThrow().toFile()).getWidth());
        assertTrue(conversion.reducida(rota).isEmpty());
        assertTrue(conversion.reducida(heicRoto).isEmpty());
        String avisos = String.join("\n", conversion.avisos());
        assertTrue(avisos.contains("rota.jpg") && avisos.contains("rota.heic"), avisos);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void enWindowsElHeicSaleIgualPorUnCaminoOPorElOtro() throws Exception {
        FotoModelo heic = foto("ULL027", "b.HEIC", "00001.heic", FotosDePrueba.heicDePrueba());
        FotoModelo roto = foto("ULL027", "c.heic", "00002.heic",
                "no soy un heic".getBytes(StandardCharsets.UTF_8));

        ConversionFotos conversion = new ConversorFotos(new DecodificadorHeicJava(),
                new ConversorHeicWindows()).convertir(List.of(heic, roto), dir);
        conversion.esperar();

        assertEquals(430, ImageIO.read(conversion.reducida(heic).orElseThrow().toFile()).getWidth());
        assertEquals(2, conversion.hechas());
        assertEquals(1, conversion.avisos().size(), conversion.avisos().toString());
    }

    @Test
    void sinFotosTerminaEnseguida() throws Exception {
        ConversionFotos conversion = new ConversorFotos(new DecodificadorHeicJava(), null)
                .convertir(List.of(), dir);

        conversion.esperar();
        assertTrue(conversion.terminada());
        assertEquals(0, conversion.total());
    }

    @Test
    void cancelarNoDejaAnadidasLasQueFaltan() throws Exception {
        FotoModelo grande = foto("ULL027", "a.jpg", "00001.jpg",
                FotosDePrueba.jpeg(3000, 2000, Color.GRAY));

        ConversionFotos conversion = new ConversorFotos(new DecodificadorHeicJava(), null)
                .convertir(List.of(grande), dir);
        conversion.cancelar();
        conversion.esperar();

        assertTrue(conversion.terminada());
        assertTrue(conversion.avisos().isEmpty(), "una cancelación no es un fallo de la foto");
    }

    private FotoModelo foto(String modelo, String nombre, String copia, byte[] contenido)
            throws IOException {
        Path fichero = Files.createDirectories(dir.resolve("originales")).resolve(copia);
        Files.write(fichero, contenido);
        return new FotoModelo(modelo, nombre, fichero);
    }
}
```

- [ ] **Step 3: comprobar que fallan**

Run: `mvn -q test -Dtest="ReductorImagenTest,DecodificadorHeicJavaTest,ConversorHeicWindowsTest,ConversorFotosTest"`
Expected: FAIL de compilación.

- [ ] **Step 4: implementación**

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/ReductorImagen.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

/**
 * Deja una foto lista para meterla en un Word: en RGB, derecha y con el lado
 * mayor a {@value #LADO_MAXIMO} px como mucho.
 *
 * Reducir no es cosmético: una foto de móvil pesa 3-5 MB y un Word con ocho
 * pasa de 30 MB. A 1.600 px, una foto de la cuadrícula de 2×3 (unos 9 cm de
 * ancho) sale a más de 400 ppp, más de lo que imprime una impresora de
 * oficina.
 */
public final class ReductorImagen {

    public static final int LADO_MAXIMO = 1600;
    private static final float CALIDAD_JPEG = 0.88f;

    private ReductorImagen() {
    }

    /**
     * Reduce sin deformar y aplana la transparencia sobre blanco (dibujada
     * sobre RGB sin fondo saldría negra). Una foto que ya cabe no se amplía.
     */
    public static BufferedImage reducir(BufferedImage original, int ladoMaximo) {
        double escala = Math.min(1.0,
                (double) ladoMaximo / Math.max(original.getWidth(), original.getHeight()));
        int ancho = Math.max(1, (int) Math.round(original.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(original.getHeight() * escala));
        BufferedImage actual = original;
        // A saltos de la mitad: de golpe, la interpolación bilineal solo mira
        // los cuatro píxeles vecinos y una reducción grande sale con dientes.
        while (actual.getWidth() / 2 >= ancho && actual.getHeight() / 2 >= alto) {
            actual = escalar(actual, actual.getWidth() / 2, actual.getHeight() / 2);
        }
        return escalar(actual, ancho, alto);
    }

    /**
     * Endereza según la etiqueta de orientación EXIF (1-8). Los móviles
     * guardan la foto como sale del sensor y apuntan el giro en el EXIF; Java
     * no lo aplica al leer, así que sin esto las fotos verticales salen
     * tumbadas.
     */
    public static BufferedImage orientar(BufferedImage imagen, int orientacion) {
        if (orientacion < 2 || orientacion > 8) {
            return imagen;
        }
        int w = imagen.getWidth();
        int h = imagen.getHeight();
        // AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00·x + m01·y + m02, y' = m10·x + m11·y + m12
        AffineTransform giro = switch (orientacion) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        boolean cambiaDeLado = orientacion >= 5;
        BufferedImage destino = new BufferedImage(cambiaDeLado ? h : w, cambiaDeLado ? w : h,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.drawImage(imagen, giro, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    /** La orientación EXIF de un fichero, o 1 (tal cual) si no la trae o no se deja leer. */
    public static int orientacionExif(Path fichero) {
        try {
            Metadata metadatos = ImageMetadataReader.readMetadata(fichero.toFile());
            ExifIFD0Directory exif = metadatos.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            // Sin EXIF legible la foto se queda como está: no es motivo para perderla.
        }
        return 1;
    }

    public static void escribirJpeg(BufferedImage imagen, Path destino) throws IOException {
        // Sin borrar antes, un fichero más grande que el nuevo conserva su cola.
        Files.deleteIfExists(destino);
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(CALIDAD_JPEG);
        try (ImageOutputStream salida = ImageIO.createImageOutputStream(destino.toFile())) {
            escritor.setOutput(salida);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
    }

    private static BufferedImage escalar(BufferedImage origen, int ancho, int alto) {
        BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ancho, alto);
            g.drawImage(origen, 0, 0, ancho, alto, null);
        } finally {
            g.dispose();
        }
        return destino;
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/DecodificadorHeicJava.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

import openize.heic.decoder.HeicImage;
import openize.heic.decoder.PixelFormat;
import openize.io.IOFileStream;
import openize.io.IOMode;

/**
 * Decodifica HEIC en Java puro con Openize.HEIC: funciona igual en Windows y
 * en el servidor Linux, sin instalar nada.
 *
 * <b>Es caro, y está medido</b> (fotos reales de iPhone, 2026-09-29): ~5 s y
 * ~0,8 GB de heap una foto de 12 MP; ~14 s y ~1,8 GB una de 24 MP.
 * Decodificar por franjas no baja el pico, porque la librería reconstruye la
 * cuadrícula entera. Por eso cuántas fotos se decodifican a la vez lo decide
 * un presupuesto de memoria —un semáforo en MB sobre el heap máximo menos una
 * reserva para el resto de la aplicación—, que es COMPARTIDO por todas las
 * sesiones: dos personas convirtiendo a la vez no pueden sumar el doble. Una
 * foto que pide más que el presupuesto entero se decodifica sola, y si aun
 * así no cabe sale como error de esa foto, no como caída de la aplicación.
 */
public class DecodificadorHeicJava {

    /** Lo medido son ~65-75 bytes por píxel; 80 deja margen. */
    private static final long BYTES_POR_PIXEL = 80;
    private static final long RESERVA_MB = 512;
    private static final long MINIMO_MB = 256;

    private final int presupuestoMb;
    private final Semaphore presupuesto;

    public DecodificadorHeicJava() {
        this(presupuestoPorDefecto());
    }

    DecodificadorHeicJava(int presupuestoMb) {
        this.presupuestoMb = Math.max(1, presupuestoMb);
        this.presupuesto = new Semaphore(this.presupuestoMb, true);
    }

    private static int presupuestoPorDefecto() {
        long maximoMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(MINIMO_MB, maximoMb - RESERVA_MB));
    }

    public BufferedImage decodificar(Path fichero) throws IOException, InterruptedException {
        try (IOFileStream flujo = new IOFileStream(fichero.toFile(), IOMode.READ)) {
            HeicImage imagen = HeicImage.load(flujo);
            int ancho = (int) imagen.getWidth();
            int alto = (int) imagen.getHeight();
            int necesarios = (int) Math.min(presupuestoMb,
                    Math.max(1, (long) ancho * alto * BYTES_POR_PIXEL / (1024 * 1024)));
            presupuesto.acquire(necesarios);
            try {
                int[] pixeles = imagen.getInt32Array(PixelFormat.Argb32);
                BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
                salida.setRGB(0, 0, ancho, alto, pixeles, 0, ancho);
                return salida;
            } catch (OutOfMemoryError e) {
                throw new IOException("no cabe en la memoria de la aplicación ("
                        + ancho + "x" + alto + " px)");
            } finally {
                presupuesto.release(necesarios);
            }
        } catch (RuntimeException e) {
            // Las excepciones de Openize (openize.io.IOException incluida) son de tiempo de ejecución.
            throw new IOException("no es un HEIC legible"
                    + (e.getMessage() != null ? ": " + e.getMessage() : ""), e);
        }
    }
}
```

<!-- fichero: src/main/resources/corte/heic-a-jpeg.ps1 -->
```powershell
# Convierte fotos HEIC a JPEG reducido con el decodificador de Windows (WIC).
# Lo lanza ConversorHeicWindows. Cada línea de la lista es "entrada<TAB>salida"
# y por cada una se escribe "OK<TAB>n" o "ERR<TAB>n<TAB>mensaje", con n el
# número de línea: los caminos no vuelven por la salida para no depender de
# la página de códigos de la consola.
param(
    [Parameter(Mandatory = $true)][string]$lista,
    [int]$max = 1600,
    [int]$calidad = 88
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationCore
$n = 0
foreach ($linea in [IO.File]::ReadAllLines($lista, [Text.Encoding]::UTF8)) {
    $n++
    if ([string]::IsNullOrWhiteSpace($linea)) { continue }
    $partes = $linea.Split("`t")
    try {
        $decodificador = [System.Windows.Media.Imaging.BitmapDecoder]::Create(
            [Uri]$partes[0], 'IgnoreColorProfile', 'OnLoad')
        $marco = $decodificador.Frames[0]
        $escala = [Math]::Min(1.0, $max / [Math]::Max($marco.PixelWidth, $marco.PixelHeight))
        $fuente = $marco
        if ($escala -lt 1.0) {
            $fuente = New-Object System.Windows.Media.Imaging.TransformedBitmap(
                $marco, (New-Object System.Windows.Media.ScaleTransform($escala, $escala)))
        }
        $codificador = New-Object System.Windows.Media.Imaging.JpegBitmapEncoder
        $codificador.QualityLevel = $calidad
        $codificador.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($fuente))
        $salida = [IO.File]::Create($partes[1])
        try { $codificador.Save($salida) } finally { $salida.Close() }
        [Console]::Out.WriteLine("OK`t$n")
    } catch {
        $mensaje = $_.Exception.Message -replace "[`r`n`t]", ' '
        [Console]::Out.WriteLine("ERR`t$n`t$mensaje")
    }
    [Console]::Out.Flush()
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/ConversorHeicWindows.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * El camino rápido de los HEIC en Windows: el decodificador del sistema
 * (WIC) a través de PowerShell, un proceso por lote.
 *
 * Medido con las mismas fotos que el camino Java: ~1,7 s una foto de 12 MP y
 * ~4 s una de 24 MP, con la memoria fuera de la JVM. Necesita las
 * extensiones HEIF y HEVC de Windows; si no están, cada foto falla deprisa y
 * vuelve como pendiente para el camino Java, así que no hace falta
 * comprobarlas antes.
 */
public class ConversorHeicWindows {

    /** Una foto que convertir y dónde dejar su JPEG. */
    public record Trabajo(Path entrada, Path salida) {
    }

    private static final String SCRIPT = "/corte/heic-a-jpeg.ps1";
    private static final long MINUTOS_MAXIMOS = 30;

    public static boolean disponible() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /**
     * Convierte el lote en un proceso de PowerShell y avisa de cada foto
     * según sale, para que el progreso se vea. Devuelve las que NO han salido
     * (Windows no las lee, o el proceso se ha cortado); lanza IOException
     * solo si PowerShell ni siquiera ha arrancado.
     */
    public List<Trabajo> convertir(List<Trabajo> trabajos, Path carpeta,
                                   Consumer<Process> registrar, Consumer<Trabajo> alConvertir)
            throws IOException, InterruptedException {
        if (trabajos.isEmpty()) {
            return List.of();
        }
        Path script = Files.createTempFile(carpeta, "heic-a-jpeg-", ".ps1");
        Path lista = Files.createTempFile(carpeta, "heic-lista-", ".txt");
        try {
            try (InputStream contenido = ConversorHeicWindows.class.getResourceAsStream(SCRIPT)) {
                if (contenido == null) {
                    throw new IOException("falta el script " + SCRIPT);
                }
                Files.copy(contenido, script, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(lista, trabajos.stream()
                    .map(trabajo -> trabajo.entrada().toAbsolutePath() + "\t"
                            + trabajo.salida().toAbsolutePath())
                    .toList(), StandardCharsets.UTF_8);

            Process proceso = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", script.toAbsolutePath().toString(),
                    "-lista", lista.toAbsolutePath().toString(),
                    "-max", String.valueOf(ReductorImagen.LADO_MAXIMO))
                    .redirectErrorStream(true)
                    .start();
            registrar.accept(proceso);

            Set<Integer> convertidas = new HashSet<>();
            try (BufferedReader lector = new BufferedReader(
                    new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    int indice = indiceConvertido(linea);
                    if (indice >= 0 && indice < trabajos.size()
                            && Files.isRegularFile(trabajos.get(indice).salida())
                            && convertidas.add(indice)) {
                        alConvertir.accept(trabajos.get(indice));
                    }
                }
            } catch (IOException e) {
                // Salida cortada (proceso cancelado o muerto): lo no confirmado vuelve como pendiente.
            }
            if (!proceso.waitFor(MINUTOS_MAXIMOS, TimeUnit.MINUTES)) {
                proceso.destroyForcibly();
            }

            List<Trabajo> pendientes = new ArrayList<>();
            for (int i = 0; i < trabajos.size(); i++) {
                if (!convertidas.contains(i)) {
                    pendientes.add(trabajos.get(i));
                }
            }
            return pendientes;
        } finally {
            Files.deleteIfExists(lista);
            Files.deleteIfExists(script);
        }
    }

    /** "OK<TAB>n" → n-1; cualquier otra línea → -1. */
    private static int indiceConvertido(String linea) {
        String[] partes = linea.split("\t", 3);
        if (partes.length < 2 || !"OK".equals(partes[0].trim())) {
            return -1;
        }
        try {
            return Integer.parseInt(partes[1].trim()) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/ConversionFotos.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Una conversión de fotos en marcha: arranca al subir el zip y trabaja en
 * segundo plano mientras se rellena la tabla de pieles. La pantalla pregunta
 * por el progreso y generar espera a que acabe.
 *
 * Una foto que no se deja leer es un aviso con su nombre, nunca el final de
 * la conversión: las demás siguen.
 */
public final class ConversionFotos {

    /** Cómo se lee una foto a imagen; cada camino tiene el suyo. */
    @FunctionalInterface
    interface Decodificador {
        BufferedImage leer(Path fichero) throws Exception;
    }

    private final int total;
    private final ExecutorService hilos;
    private final AtomicInteger hechas = new AtomicInteger();
    private final Map<Path, Path> reducidas = new ConcurrentHashMap<>();
    private final Queue<String> avisos = new ConcurrentLinkedQueue<>();
    private final List<Process> procesos = new CopyOnWriteArrayList<>();
    private final CountDownLatch fin = new CountDownLatch(1);
    private volatile boolean cancelada;

    ConversionFotos(int total, ExecutorService hilos) {
        this.total = total;
        this.hilos = hilos;
    }

    public int total() {
        return total;
    }

    /** Las ya procesadas, hayan salido bien o no. */
    public int hechas() {
        return hechas.get();
    }

    public boolean terminada() {
        return fin.getCount() == 0;
    }

    public void esperar() throws InterruptedException {
        fin.await();
    }

    /** El JPEG reducido de una foto, o vacío si no se ha podido convertir. */
    public Optional<Path> reducida(FotoModelo foto) {
        return Optional.ofNullable(reducidas.get(foto.original()));
    }

    public List<String> avisos() {
        return List.copyOf(avisos);
    }

    /** Para lo que quede: al empezar otra carga o al caducar la sesión. */
    public void cancelar() {
        cancelada = true;
        hilos.shutdownNow();
        procesos.forEach(Process::destroyForcibly);
        fin.countDown();
    }

    void registrar(Process proceso) {
        procesos.add(proceso);
        if (cancelada) {
            proceso.destroyForcibly();
        }
    }

    /** Una foto que ha convertido otro camino (Windows) y ya está en disco. */
    void anotar(FotoModelo foto, Path salida) {
        reducidas.put(foto.original(), salida);
        hechas.incrementAndGet();
    }

    void convertir(FotoModelo foto, Path salida, Decodificador decodificador, boolean enderezarExif) {
        if (cancelada) {
            return;
        }
        try {
            BufferedImage reducida = ReductorImagen.reducir(
                    decodificador.leer(foto.original()), ReductorImagen.LADO_MAXIMO);
            if (enderezarExif) {
                reducida = ReductorImagen.orientar(reducida,
                        ReductorImagen.orientacionExif(foto.original()));
            }
            ReductorImagen.escribirJpeg(reducida, salida);
            reducidas.put(foto.original(), salida);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception | OutOfMemoryError e) {
            if (!cancelada) {
                avisos.add("La foto " + foto.nombreOriginal() + " de " + foto.modelo()
                        + " no se ha podido leer (" + explicar(e) + "): no sale en los documentos");
            }
        } finally {
            hechas.incrementAndGet();
        }
    }

    void terminar() {
        fin.countDown();
    }

    private static String explicar(Throwable error) {
        if (error instanceof OutOfMemoryError) {
            return "no cabe en la memoria de la aplicación";
        }
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/ConversorFotos.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.service.corte.ConversorHeicWindows.Trabajo;

/**
 * Convierte las fotos del zip en JPEG reducidos, en segundo plano.
 *
 * Tres caminos: los JPG, con ImageIO y enderezados por su EXIF; los HEIC en
 * Windows, por lotes con el decodificador del sistema (rápido); y los HEIC
 * que Windows no lee, o todos fuera de Windows, con Openize en Java puro
 * (lento y con mucha memoria, ver DecodificadorHeicJava). Los lotes de
 * Windows se lanzan los primeros para que no esperen detrás de los JPG.
 */
@Service
public class ConversorFotos {

    /** Hilos del camino Java: pocos, porque cada foto ya pide mucha memoria. */
    private static final int HILOS_JAVA = Math.max(1,
            Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    /** Procesos de PowerShell a la vez; cada uno convierte su lote en serie. */
    private static final int PROCESOS_WINDOWS = Math.max(1,
            Math.min(3, Runtime.getRuntime().availableProcessors() / 4));

    private final DecodificadorHeicJava heicJava;
    /** null = no se usa el camino de Windows. */
    private final ConversorHeicWindows heicWindows;

    @Autowired
    public ConversorFotos() {
        this(new DecodificadorHeicJava(),
                ConversorHeicWindows.disponible() ? new ConversorHeicWindows() : null);
    }

    ConversorFotos(DecodificadorHeicJava heicJava, ConversorHeicWindows heicWindows) {
        this.heicJava = heicJava;
        this.heicWindows = heicWindows;
    }

    public ConversionFotos convertir(List<FotoModelo> fotos, Path directorio) throws IOException {
        Path destino = Files.createDirectories(directorio.resolve("reducidas"));
        List<FotoModelo> heic = fotos.stream().filter(ConversorFotos::esHeic).toList();
        List<FotoModelo> jpg = fotos.stream().filter(foto -> !esHeic(foto)).toList();
        List<List<FotoModelo>> lotes = heicWindows == null ? List.of() : lotes(heic, PROCESOS_WINDOWS);

        ExecutorService hilos = Executors.newFixedThreadPool(HILOS_JAVA + lotes.size(), hilosDemonio());
        ConversionFotos conversion = new ConversionFotos(fotos.size(), hilos);
        List<CompletableFuture<Void>> tareas = new ArrayList<>();
        for (List<FotoModelo> lote : lotes) {
            tareas.add(CompletableFuture.runAsync(
                    () -> convertirEnWindows(lote, destino, directorio, conversion), hilos));
        }
        if (heicWindows == null) {
            for (FotoModelo foto : heic) {
                tareas.add(CompletableFuture.runAsync(() -> conversion.convertir(
                        foto, salidaDe(foto, destino), heicJava::decodificar, false), hilos));
            }
        }
        for (FotoModelo foto : jpg) {
            tareas.add(CompletableFuture.runAsync(() -> conversion.convertir(
                    foto, salidaDe(foto, destino), ConversorFotos::leerJpeg, true), hilos));
        }
        CompletableFuture.allOf(tareas.toArray(CompletableFuture[]::new))
                .whenComplete((nada, error) -> {
                    hilos.shutdown();
                    conversion.terminar();
                });
        return conversion;
    }

    private void convertirEnWindows(List<FotoModelo> lote, Path destino, Path directorio,
                                    ConversionFotos conversion) {
        Map<Path, FotoModelo> porEntrada = new LinkedHashMap<>();
        List<Trabajo> trabajos = new ArrayList<>();
        for (FotoModelo foto : lote) {
            trabajos.add(new Trabajo(foto.original(), salidaDe(foto, destino)));
            porEntrada.put(foto.original(), foto);
        }
        List<Trabajo> pendientes;
        try {
            pendientes = heicWindows.convertir(trabajos, directorio, conversion::registrar,
                    trabajo -> conversion.anotar(porEntrada.get(trabajo.entrada()), trabajo.salida()));
        } catch (IOException e) {
            pendientes = trabajos;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        for (Trabajo trabajo : pendientes) {
            conversion.convertir(porEntrada.get(trabajo.entrada()), trabajo.salida(),
                    heicJava::decodificar, false);
        }
    }

    static BufferedImage leerJpeg(Path fichero) throws IOException {
        BufferedImage imagen = ImageIO.read(fichero.toFile());
        if (imagen == null) {
            throw new IOException("no es un JPG legible");
        }
        return imagen;
    }

    private static boolean esHeic(FotoModelo foto) {
        String nombre = foto.nombreOriginal().toLowerCase(Locale.ROOT);
        return nombre.endsWith(".heic") || nombre.endsWith(".heif");
    }

    /** "00012.heic" → "reducidas/00012.jpg": el nombre generado ya es único. */
    private static Path salidaDe(FotoModelo foto, Path destino) {
        String nombre = foto.original().getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        return destino.resolve((punto < 0 ? nombre : nombre.substring(0, punto)) + ".jpg");
    }

    private static List<List<FotoModelo>> lotes(List<FotoModelo> fotos, int maximo) {
        if (fotos.isEmpty()) {
            return List.of();
        }
        int numero = Math.min(maximo, fotos.size());
        int tamano = (fotos.size() + numero - 1) / numero;
        List<List<FotoModelo>> lotes = new ArrayList<>();
        for (int desde = 0; desde < fotos.size(); desde += tamano) {
            lotes.add(fotos.subList(desde, Math.min(fotos.size(), desde + tamano)));
        }
        return lotes;
    }

    private static ThreadFactory hilosDemonio() {
        AtomicInteger contador = new AtomicInteger();
        return tarea -> {
            Thread hilo = new Thread(tarea, "fotos-corte-" + contador.incrementAndGet());
            hilo.setDaemon(true);
            return hilo;
        };
    }
}
```

- [ ] **Step 5: comprobar que pasan**

Run: `mvn -q test -Dtest="ReductorImagenTest,DecodificadorHeicJavaTest,ConversorHeicWindowsTest,ConversorFotosTest"`
Expected: PASS (en Windows también los dos tests de WIC).

- [ ] **Step 6: commit**

```bash
git add pom.xml src/main/java/com/puntotres/packinglist/service/corte src/main/resources/corte src/test/java/com/puntotres/packinglist/service/corte src/test/java/com/puntotres/packinglist/testutil/FotosDePrueba.java src/test/resources/ejemplos/corte
git commit -m "las fotos del zip se convierten a jpeg reducido en segundo plano, heic incluido"
```

---

### Tarea 4: memoria de pieles

**Files:**
- Create: `persistence/MemoriaPiel.java`, `MemoriaPielId.java`, `MemoriaPielRepository.java`, `MemoriaPielesArticulo.java`, `MemoriaPielesArticuloId.java`, `MemoriaPielesArticuloRepository.java`, `MemoriaPieles.java`
- Test: `src/test/java/com/puntotres/packinglist/persistence/MemoriaPielesTest.java`

**Interfaces:**
- Produces: `MemoriaPieles.MAXIMO_COMBINACIONES` (4); `record MemoriaPieles.Recordadas(String nombrePiel, String forro, List<String> combinaciones)`; `MemoriaPieles.buscar(String cliente, String refPiel, String referencia) → Recordadas` (nunca null; cadenas vacías si no sabe nada); `MemoriaPieles.recordar(String cliente, String refPiel, String referencia, String nombrePiel, String forro, List<String> combinaciones)`.

- [ ] **Step 1: test que falla**

<!-- fichero: src/test/java/com/puntotres/packinglist/persistence/MemoriaPielesTest.java -->
```java
package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Lo que el programa recuerda de las pieles entre temporadas. Cada test usa
 * sus propias referencias: la base de datos en memoria es la misma para toda
 * la suite.
 */
@SpringBootTest
class MemoriaPielesTest {

    @Autowired
    private MemoriaPieles memoria;

    @Test
    void elNombreDeLaPielValeParaTodosLosBolsosQueLaUsan() {
        memoria.recordar("AMI", "AL0901", "ULL729.AL0901", "Box calf", "", List.of());

        MemoriaPieles.Recordadas otroBolso = memoria.buscar("AMI", "AL0901", "ULL712.AL0901");
        assertEquals("Box calf", otroBolso.nombrePiel());
        assertTrue(otroBolso.combinaciones().isEmpty(), "las combinaciones son del bolso, no de la piel");
        assertEquals("", otroBolso.forro());
    }

    @Test
    void elForroYLasCombinacionesSonDelBolso() {
        memoria.recordar("AMI", "AL0902", "ULL027.AL0902", "Vachette",
                "Cabretilla", Arrays.asList("Ante", "", null, "Charol"));

        MemoriaPieles.Recordadas recordadas = memoria.buscar("AMI", "AL0902", "ULL027.AL0902");
        assertEquals("Cabretilla", recordadas.forro());
        assertEquals(List.of("Ante", "Charol"), recordadas.combinaciones());
    }

    @Test
    void sinNombreDePielNoSeGuardaNiSeBorraNada() {
        memoria.recordar("AMI", "AL0903", "ULL027.AL0903", "Nappa", "Tela", List.of("Ante"));
        memoria.recordar("AMI", "AL0903", "ULL027.AL0903", "  ", "", List.of());

        MemoriaPieles.Recordadas recordadas = memoria.buscar("AMI", "AL0903", "ULL027.AL0903");
        assertEquals("Nappa", recordadas.nombrePiel());
        assertEquals("Tela", recordadas.forro());
        assertEquals(List.of("Ante"), recordadas.combinaciones());
    }

    @Test
    void quitarUnaCombinacionConNombreSiSeRecuerda() {
        memoria.recordar("AMI", "AL0904", "ULL027.AL0904", "Nappa", "Tela", List.of("Ante"));
        memoria.recordar("AMI", "AL0904", "ULL027.AL0904", "Nappa", "", List.of());

        MemoriaPieles.Recordadas recordadas = memoria.buscar("AMI", "AL0904", "ULL027.AL0904");
        assertTrue(recordadas.combinaciones().isEmpty());
        assertEquals("", recordadas.forro());
    }

    @Test
    void lasClavesNoDistinguenMayusculasPeroElNombreSeGuardaComoSeTeclea() {
        memoria.recordar("ami", " al0905 ", "ull729.al0905", "Vachette Grainée", "", List.of());

        assertEquals("Vachette Grainée", memoria.buscar("AMI", "AL0905", "ULL729.AL0905").nombrePiel());
    }

    @Test
    void loQueNoSeConoceVuelveVacioYNoNull() {
        MemoriaPieles.Recordadas nada = memoria.buscar("APC", "PXZZZ", "PXZZZ-F00000");

        assertEquals("", nada.nombrePiel());
        assertEquals("", nada.forro());
        assertTrue(nada.combinaciones().isEmpty());
    }

    @Test
    void unaReferenciaSinPielGuardaSoloElBolso() {
        memoria.recordar("AMI", "", "MUESTRA9", "Nappa", "Tela", List.of("Ante"));

        MemoriaPieles.Recordadas recordadas = memoria.buscar("AMI", "", "MUESTRA9");
        assertEquals("", recordadas.nombrePiel(), "sin ref de piel no hay dónde guardar su nombre");
        assertEquals(List.of("Ante"), recordadas.combinaciones());
    }
}
```

- [ ] **Step 2: comprobar que falla**

Run: `mvn -q test -Dtest=MemoriaPielesTest`
Expected: FAIL de compilación.

- [ ] **Step 3: implementación**

<!-- fichero: src/main/java/com/puntotres/packinglist/persistence/MemoriaPielId.java -->
```java
package com.puntotres.packinglist.persistence;

import java.io.Serializable;

/** Clave del nombre de una piel: cliente + referencia de la piel ("AMI" + "AL0103"). */
public record MemoriaPielId(String cliente, String refPiel) implements Serializable {

    /** JPA exige un constructor sin argumentos para la clase de la clave. */
    public MemoriaPielId() {
        this(null, null);
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/persistence/MemoriaPiel.java -->
```java
package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Cómo se llama una piel del cliente. El pedido solo trae su referencia
 * ("AL0103"); el nombre lo teclea quien prepara el corte, y es del MATERIAL:
 * la misma piel se llama igual en todos los bolsos que la usan.
 */
@Entity
@Table(name = "memoria_piel")
@IdClass(MemoriaPielId.class)
public class MemoriaPiel {

    @Id
    @Column(name = "cliente", length = 60)
    private String cliente;

    @Id
    @Column(name = "ref_piel", length = 80)
    private String refPiel;

    @Column(name = "nombre", length = 200, nullable = false)
    private String nombre;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected MemoriaPiel() {
        // Constructor para JPA.
    }

    public MemoriaPiel(String cliente, String refPiel, String nombre) {
        this.cliente = cliente;
        this.refPiel = refPiel;
        actualizar(nombre);
    }

    public String getNombre() {
        return nombre;
    }

    public void actualizar(String nombre) {
        this.nombre = nombre;
        this.fechaActualizacion = LocalDateTime.now();
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/persistence/MemoriaPielRepository.java -->
```java
package com.puntotres.packinglist.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los nombres de piel recordados. La clave es cliente + referencia de piel. */
public interface MemoriaPielRepository extends JpaRepository<MemoriaPiel, MemoriaPielId> {
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/persistence/MemoriaPielesArticuloId.java -->
```java
package com.puntotres.packinglist.persistence;

import java.io.Serializable;

/** Clave del forro y las combinaciones: cliente + referencia (modelo + piel, "ULL027.AL0103"). */
public record MemoriaPielesArticuloId(String cliente, String referencia) implements Serializable {

    /** JPA exige un constructor sin argumentos para la clase de la clave. */
    public MemoriaPielesArticuloId() {
        this(null, null);
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/persistence/MemoriaPielesArticulo.java -->
```java
package com.puntotres.packinglist.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * El forro y las pieles de combinación de una referencia. Van por
 * referencia (modelo + piel) y no por piel porque dependen del BOLSO: dos
 * bolsos con la misma piel principal pueden llevar combinaciones distintas,
 * o ninguna. Cuatro columnas fijas porque la pantalla admite cuatro como
 * mucho y lo normal es una o ninguna.
 */
@Entity
@Table(name = "memoria_pieles_articulo")
@IdClass(MemoriaPielesArticuloId.class)
public class MemoriaPielesArticulo {

    @Id
    @Column(name = "cliente", length = 60)
    private String cliente;

    @Id
    @Column(name = "referencia", length = 80)
    private String referencia;

    @Column(name = "forro", length = 200)
    private String forro;

    @Column(name = "combinacion1", length = 200)
    private String combinacion1;

    @Column(name = "combinacion2", length = 200)
    private String combinacion2;

    @Column(name = "combinacion3", length = 200)
    private String combinacion3;

    @Column(name = "combinacion4", length = 200)
    private String combinacion4;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected MemoriaPielesArticulo() {
        // Constructor para JPA.
    }

    public MemoriaPielesArticulo(String cliente, String referencia, String forro,
                                 List<String> combinaciones) {
        this.cliente = cliente;
        this.referencia = referencia;
        actualizar(forro, combinaciones);
    }

    public String getForro() {
        return forro == null ? "" : forro;
    }

    /** Las combinaciones rellenas, en su orden. */
    public List<String> getCombinaciones() {
        return Stream.of(combinacion1, combinacion2, combinacion3, combinacion4)
                .filter(combinacion -> combinacion != null && !combinacion.isBlank())
                .toList();
    }

    /** La última generación gana entera: una combinación quitada es una combinación que ya no lleva. */
    public void actualizar(String forro, List<String> combinaciones) {
        this.forro = forro;
        this.combinacion1 = posicion(combinaciones, 0);
        this.combinacion2 = posicion(combinaciones, 1);
        this.combinacion3 = posicion(combinaciones, 2);
        this.combinacion4 = posicion(combinaciones, 3);
        this.fechaActualizacion = LocalDateTime.now();
    }

    private static String posicion(List<String> combinaciones, int indice) {
        return indice < combinaciones.size() ? combinaciones.get(indice) : null;
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/persistence/MemoriaPielesArticuloRepository.java -->
```java
package com.puntotres.packinglist.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso al forro y las combinaciones recordados. La clave es cliente + referencia. */
public interface MemoriaPielesArticuloRepository
        extends JpaRepository<MemoriaPielesArticulo, MemoriaPielesArticuloId> {
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/persistence/MemoriaPieles.java -->
```java
package com.puntotres.packinglist.persistence;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que el programa recuerda de las pieles para los documentos del corte,
 * para no teclearlo cada temporada.
 *
 * Dos claves a propósito: el NOMBRE de la piel es del material y se guarda
 * por cliente + referencia de piel; el FORRO y las COMBINACIONES son del
 * bolso y se guardan por cliente + referencia (modelo + piel). Guardadas solo
 * por piel, las combinaciones de ULL027.AL0103 aparecerían en ULL712.AL0103,
 * que no las lleva.
 *
 * Solo se guarda lo que alguien ha dado por bueno: una fila sin nombre de
 * piel no ha rellenado nada y no toca la memoria (ni el nombre ni el forro ni
 * las combinaciones). Con nombre, el forro y las combinaciones se guardan tal
 * cual, vacío incluido: quitar una combinación es decir que ya no la lleva.
 */
@Service
public class MemoriaPieles {

    public static final int MAXIMO_COMBINACIONES = 4;

    /** Lo recordado de una fila; cadenas vacías y lista vacía cuando no se sabe nada. */
    public record Recordadas(String nombrePiel, String forro, List<String> combinaciones) {

        public Recordadas {
            combinaciones = List.copyOf(combinaciones);
        }
    }

    private final MemoriaPielRepository pieles;
    private final MemoriaPielesArticuloRepository articulos;

    public MemoriaPieles(MemoriaPielRepository pieles, MemoriaPielesArticuloRepository articulos) {
        this.pieles = pieles;
        this.articulos = articulos;
    }

    public Recordadas buscar(String cliente, String refPiel, String referencia) {
        if (vacio(cliente)) {
            return new Recordadas("", "", List.of());
        }
        String nombre = vacio(refPiel) ? "" : pieles.findById(
                        new MemoriaPielId(clave(cliente), clave(refPiel)))
                .map(MemoriaPiel::getNombre)
                .orElse("");
        if (vacio(referencia)) {
            return new Recordadas(nombre, "", List.of());
        }
        return articulos.findById(new MemoriaPielesArticuloId(clave(cliente), clave(referencia)))
                .map(fila -> new Recordadas(nombre, fila.getForro(), fila.getCombinaciones()))
                .orElse(new Recordadas(nombre, "", List.of()));
    }

    @Transactional
    public void recordar(String cliente, String refPiel, String referencia,
                         String nombrePiel, String forro, List<String> combinaciones) {
        if (vacio(cliente) || vacio(referencia) || vacio(nombrePiel)) {
            return;
        }
        String elCliente = clave(cliente);
        if (!vacio(refPiel)) {
            MemoriaPielId id = new MemoriaPielId(elCliente, clave(refPiel));
            String nombre = nombrePiel.trim();
            pieles.findById(id).ifPresentOrElse(
                    fila -> fila.actualizar(nombre),
                    () -> pieles.save(new MemoriaPiel(id.cliente(), id.refPiel(), nombre)));
        }
        List<String> rellenas = combinaciones == null ? List.of() : combinaciones.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(combinacion -> !combinacion.isEmpty())
                .limit(MAXIMO_COMBINACIONES)
                .toList();
        String elForro = forro == null ? "" : forro.trim();
        MemoriaPielesArticuloId id = new MemoriaPielesArticuloId(elCliente, clave(referencia));
        articulos.findById(id).ifPresentOrElse(
                fila -> fila.actualizar(elForro, rellenas),
                () -> articulos.save(new MemoriaPielesArticulo(
                        id.cliente(), id.referencia(), elForro, rellenas)));
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** Las claves se teclean o se leen de un excel: "al0103 " y "AL0103" son la misma. */
    private static String clave(String texto) {
        return texto.trim().toUpperCase(Locale.ROOT);
    }
}
```

- [ ] **Step 4: comprobar que pasa**

Run: `mvn -q test -Dtest=MemoriaPielesTest`
Expected: PASS.

- [ ] **Step 5: commit**

```bash
git add src/main/java/com/puntotres/packinglist/persistence src/test/java/com/puntotres/packinglist/persistence/MemoriaPielesTest.java
git commit -m "los nombres de piel se recuerdan por piel y el forro y las combinaciones por bolso"
```

---

### Tarea 5: el Word de órdenes de corte

**Files:**
- Create: `service/corte/WordCorte.java`, `Imagen.java`, `PielesArticulo.java`, `OrdenCorte.java`, `OrdenCorteDocBuilder.java`
- Test: `src/test/java/com/puntotres/packinglist/service/corte/OrdenCorteDocBuilderTest.java`, `src/test/java/com/puntotres/packinglist/testutil/WordDePrueba.java`

**Interfaces:**
- Consumes: `FotosDePrueba` (tarea 3).
- Produces: `record Imagen(byte[] jpeg, int ancho, int alto)` con `Imagen.de(Path)`; `record PielesArticulo(String nombrePiel, String forro, List<String> combinaciones)` (normaliza: sin null, sin combinaciones vacías) con `tieneForro()`; `record OrdenCorte(String cliente, String temporada, String referencia, String color, int bolsos, PielesArticulo pieles, Imagen fotoPrincipal)` (`fotoPrincipal` puede ser null); `new OrdenCorteDocBuilder().generar(List<OrdenCorte>) → byte[]`; helpers de `WordCorte` (package-private) que usa también la tarea 6; `WordDePrueba.fotosDe(XWPFTable)`, `saltosDePagina(XWPFDocument)`, `texto(XWPFDocument)`.

- [ ] **Step 1: test que falla**

<!-- fichero: src/test/java/com/puntotres/packinglist/testutil/WordDePrueba.java -->
```java
package com.puntotres.packinglist.testutil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType;

/** Lecturas de un Word reabierto con POI, para los tests de los documentos del corte. */
public final class WordDePrueba {

    private WordDePrueba() {
    }

    /** Las imágenes de una tabla, en orden de celda. */
    public static List<XWPFPicture> fotosDe(XWPFTable tabla) {
        List<XWPFPicture> fotos = new ArrayList<>();
        for (XWPFTableRow fila : tabla.getRows()) {
            for (XWPFTableCell celda : fila.getTableCells()) {
                for (XWPFParagraph parrafo : celda.getParagraphs()) {
                    for (XWPFRun run : parrafo.getRuns()) {
                        fotos.addAll(run.getEmbeddedPictures());
                    }
                }
            }
        }
        return fotos;
    }

    /** Los saltos de página del cuerpo (fuera de las tablas). */
    public static int saltosDePagina(XWPFDocument doc) {
        int saltos = 0;
        for (XWPFParagraph parrafo : doc.getParagraphs()) {
            for (XWPFRun run : parrafo.getRuns()) {
                for (CTBr salto : run.getCTR().getBrList()) {
                    if (salto.getType() == STBrType.PAGE) {
                        saltos++;
                    }
                }
            }
        }
        return saltos;
    }

    /** Todo el texto, tablas y cabeceras incluidas. */
    public static String texto(XWPFDocument doc) {
        try (XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Ancho de una imagen en EMU, tal como lo guarda Word. */
    public static long anchoEmu(XWPFPicture foto) {
        return foto.getCTPicture().getSpPr().getXfrm().getExt().getCx();
    }

    /** Alto de una imagen en EMU. */
    public static long altoEmu(XWPFPicture foto) {
        return foto.getCTPicture().getSpPr().getXfrm().getExt().getCy();
    }
}
```

<!-- fichero: src/test/java/com/puntotres/packinglist/service/corte/OrdenCorteDocBuilderTest.java -->
```java
package com.puntotres.packinglist.service.corte;

import static com.puntotres.packinglist.testutil.WordDePrueba.altoEmu;
import static com.puntotres.packinglist.testutil.WordDePrueba.anchoEmu;
import static com.puntotres.packinglist.testutil.WordDePrueba.fotosDe;
import static com.puntotres.packinglist.testutil.WordDePrueba.saltosDePagina;
import static com.puntotres.packinglist.testutil.WordDePrueba.texto;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class OrdenCorteDocBuilderTest {

    private static final Imagen APAISADA = imagen(1600, 1200, "ULL729", new Color(120, 90, 70));
    private static final Imagen VERTICAL = imagen(1200, 1600, "ULL027", new Color(60, 60, 70));

    @Test
    void esUnA4ApaisadoConUnaPaginaPorOrden() throws IOException {
        XWPFDocument doc = generar(List.of(
                orden("ULL729.AL0103", "001 BLACK", 169, sinCombinaciones(), APAISADA),
                orden("ULL729.AL0103", "718 VANILLA CREAM", 102, sinCombinaciones(), APAISADA),
                orden("ULL745.AL0103", "001 BLACK", 45, sinCombinaciones(), null)));

        CTPageSz pagina = doc.getDocument().getBody().getSectPr().getPgSz();
        assertEquals(BigInteger.valueOf(16838), pagina.getW());
        assertEquals(BigInteger.valueOf(11906), pagina.getH());
        assertEquals(STPageOrientation.LANDSCAPE, pagina.getOrient());
        assertEquals(6, doc.getTables().size(), "banda y pieles por cada orden");
        assertEquals(2, saltosDePagina(doc));
    }

    @Test
    void llevaLosDatosQueElCortadorTieneQueVer() throws IOException {
        String texto = texto(generar(List.of(orden("ULL729.AL0103", "001 BLACK", 169,
                new PielesArticulo("Box calf", "Cabretilla", List.of("Ante", "Charol")), APAISADA))));

        for (String esperado : List.of("AMI · H26", "ULL729.AL0103", "001 BLACK", "169",
                "Box calf", "COMBINACIÓN 1", "Ante", "COMBINACIÓN 2", "Charol", "FORRO", "Cabretilla")) {
            assertTrue(texto.contains(esperado), "falta '" + esperado + "' en:\n" + texto);
        }
    }

    @Test
    void cadaPielEsUnRenglonConSuRecuadroYSinCombinacionesNiForroSoloSaleLaPiel() throws IOException {
        XWPFDocument doc = generar(List.of(
                orden("A.P1", "001", 1, sinCombinaciones(), null),
                orden("B.P2", "001", 1, new PielesArticulo("X", "F", List.of("C1", "C2")), null)));

        assertEquals(1, doc.getTables().get(1).getRows().size());
        assertEquals(7, doc.getTables().get(3).getRows().size(), "4 pieles y 3 separaciones");
        assertTrue(doc.getTables().get(3).getRow(0).getCell(1).getCTTc().getTcPr().isSetTcBorders(),
                "el recuadro para pegar la muestra");
    }

    @Test
    void laFotoPrincipalEncajaEnSuHuecoSinDeformarseYSinFotoLoDice() throws IOException {
        XWPFDocument doc = generar(List.of(
                orden("A.P1", "001", 1, sinCombinaciones(), VERTICAL),
                orden("B.P1", "001", 1, sinCombinaciones(), null)));

        var fotos = fotosDe(doc.getTables().get(0));
        assertEquals(1, fotos.size());
        long ancho = anchoEmu(fotos.get(0));
        long alto = altoEmu(fotos.get(0));
        assertTrue(ancho <= (long) OrdenCorteDocBuilder.ANCHO_FOTO * WordCorte.EMU_POR_TWIP);
        assertTrue(alto <= (long) OrdenCorteDocBuilder.ALTO_BANDA * WordCorte.EMU_POR_TWIP);
        assertEquals(1200.0 / 1600.0, (double) ancho / alto, 0.01);
        assertTrue(fotosDe(doc.getTables().get(2)).isEmpty());
        assertTrue(texto(doc).contains("Sin foto del modelo"));
    }

    @Test
    void cadaOrdenCabeEnUnaPaginaTengaLasPielesQueTenga() throws IOException {
        for (int combinaciones = 0; combinaciones <= 4; combinaciones++) {
            List<String> nombres = java.util.Collections.nCopies(combinaciones, "Ante");
            XWPFDocument doc = generar(List.of(orden("A.P1", "001", 1,
                    new PielesArticulo("Box", "Tela", nombres), APAISADA)));

            int alto = alturaTabla(doc.getTables().get(0)) + OrdenCorteDocBuilder.SEPARACION
                    + alturaTabla(doc.getTables().get(1)) + 2 * WordCorte.ALTO_SEPARADOR;
            assertTrue(alto <= OrdenCorteDocBuilder.ALTO_UTIL,
                    combinaciones + " combinaciones ocupan " + alto + " de " + OrdenCorteDocBuilder.ALTO_UTIL);
        }
    }

    @Test
    void dejaUnEjemploEnTargetParaRevisarAOjo() throws IOException {
        byte[] word = new OrdenCorteDocBuilder().generar(List.of(
                orden("ULL729.AL0103", "001 BLACK", 169,
                        new PielesArticulo("Vachette grainée", "Cabretilla", List.of("Ante")), APAISADA),
                orden("ULL745.AL0103", "001 BLACK", 45, sinCombinaciones(), null),
                orden("ULL027.AL0216", "2221 CHOCOLATE BROWN", 135,
                        new PielesArticulo("Box calf", "", List.of("Ante", "Charol", "Nappa")), VERTICAL)));

        Path destino = Path.of("target", "Ordenes de corte ejemplo.docx");
        Files.createDirectories(destino.getParent());
        Files.write(destino, word);
        assertTrue(Files.size(destino) > 0);
    }

    private static XWPFDocument generar(List<OrdenCorte> ordenes) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(new OrdenCorteDocBuilder().generar(ordenes)));
    }

    private static OrdenCorte orden(String referencia, String color, int bolsos,
                                    PielesArticulo pieles, Imagen foto) {
        return new OrdenCorte("AMI", "H26", referencia, color, bolsos, pieles, foto);
    }

    private static PielesArticulo sinCombinaciones() {
        return new PielesArticulo("Box calf", "", List.of());
    }

    private static int alturaTabla(XWPFTable tabla) {
        return tabla.getRows().stream().mapToInt(XWPFTableRow::getHeight).sum();
    }

    private static Imagen imagen(int ancho, int alto, String texto, Color color) {
        return new Imagen(FotosDePrueba.relleno(ancho, alto, texto, color), ancho, alto);
    }
}
```

- [ ] **Step 2: comprobar que falla**

Run: `mvn -q test -Dtest=OrdenCorteDocBuilderTest`
Expected: FAIL de compilación.

- [ ] **Step 3: implementación**

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/Imagen.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/** Una foto ya reducida, lista para el Word: el JPEG y sus medidas en píxeles. */
public record Imagen(byte[] jpeg, int ancho, int alto) {

    public static Imagen de(Path fichero) throws IOException {
        byte[] bytes = Files.readAllBytes(fichero);
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(bytes));
        if (imagen == null) {
            throw new IOException("no es una imagen legible: " + fichero.getFileName());
        }
        return new Imagen(bytes, imagen.getWidth(), imagen.getHeight());
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/PielesArticulo.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.util.List;
import java.util.Objects;

/**
 * Los nombres de las pieles de un artículo tal como se teclean en la pantalla
 * de pieles: la principal, el forro (solo si es de piel; vacío = no lleva) y
 * de cero a cuatro de combinación. Una combinación vacía no existe.
 */
public record PielesArticulo(String nombrePiel, String forro, List<String> combinaciones) {

    public PielesArticulo {
        nombrePiel = limpio(nombrePiel);
        forro = limpio(forro);
        combinaciones = combinaciones == null ? List.of() : combinaciones.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(combinacion -> !combinacion.isEmpty())
                .toList();
    }

    public boolean tieneForro() {
        return !forro.isEmpty();
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/OrdenCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

/**
 * Una página del Word de órdenes de corte: un modelo + piel en un color.
 * {@code fotoPrincipal} es null cuando el modelo no ha traído fotos legibles.
 */
public record OrdenCorte(String cliente, String temporada, String referencia, String color,
                         int bolsos, PielesArticulo pieles, Imagen fotoPrincipal) {
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/WordCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Arrays;

import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.apache.poi.xwpf.usermodel.TableWidthType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;

/**
 * Lo que comparten los dos Word del corte: página, tablas de medidas
 * exactas, textos y fotos. Todo en twips (veinteavos de punto), que es como
 * mide Word la página y las tablas, salvo las imágenes, que van en EMU.
 *
 * Las tablas van con medidas y alturas EXACTAS a propósito: es lo único que
 * garantiza que una orden de corte o una hoja de fotos ocupe exactamente una
 * página, en vez de dejar que Word reparta y empuje media tabla a la
 * siguiente.
 */
final class WordCorte {

    static final String FUENTE = "Calibri";
    /** Word mide las imágenes en EMU: 635 por twip. */
    static final int EMU_POR_TWIP = 635;
    /** Alto de los párrafos separadores: 1 pt, lo mínimo que Word respeta. */
    static final int ALTO_SEPARADOR = 20;

    private WordCorte() {
    }

    static void configurarPagina(XWPFDocument doc, int ancho, int alto, boolean apaisado,
                                 int arriba, int lados, int abajo, int cabecera) {
        CTBody cuerpo = doc.getDocument().getBody();
        CTSectPr seccion = cuerpo.isSetSectPr() ? cuerpo.getSectPr() : cuerpo.addNewSectPr();
        CTPageSz tamano = seccion.isSetPgSz() ? seccion.getPgSz() : seccion.addNewPgSz();
        tamano.setW(BigInteger.valueOf(ancho));
        tamano.setH(BigInteger.valueOf(alto));
        if (apaisado) {
            tamano.setOrient(STPageOrientation.LANDSCAPE);
        }
        CTPageMar margen = seccion.isSetPgMar() ? seccion.getPgMar() : seccion.addNewPgMar();
        margen.setTop(BigInteger.valueOf(arriba));
        margen.setBottom(BigInteger.valueOf(abajo));
        margen.setLeft(BigInteger.valueOf(lados));
        margen.setRight(BigInteger.valueOf(lados));
        margen.setHeader(BigInteger.valueOf(cabecera));
        margen.setFooter(BigInteger.valueOf(cabecera));
    }

    /** Una tabla sin bordes, de anchos fijos: Word no la ensancha según el contenido. */
    static XWPFTable tabla(XWPFDocument doc, int filas, int... anchos) {
        XWPFTable tabla = doc.createTable(filas, anchos.length);
        tabla.removeBorders();
        tabla.setWidth(Arrays.stream(anchos).sum());
        tabla.setWidthType(TableWidthType.DXA);
        CTTblPr propiedades = tabla.getCTTbl().getTblPr();
        CTTblLayoutType disposicion = propiedades.isSetTblLayout()
                ? propiedades.getTblLayout() : propiedades.addNewTblLayout();
        disposicion.setType(STTblLayoutType.FIXED);
        CTTblGrid rejilla = tabla.getCTTbl().getTblGrid() != null
                ? tabla.getCTTbl().getTblGrid() : tabla.getCTTbl().addNewTblGrid();
        while (rejilla.sizeOfGridColArray() > 0) {
            rejilla.removeGridCol(0);
        }
        for (int ancho : anchos) {
            rejilla.addNewGridCol().setW(BigInteger.valueOf(ancho));
        }
        for (XWPFTableRow fila : tabla.getRows()) {
            for (int columna = 0; columna < anchos.length; columna++) {
                XWPFTableCell celda = fila.getCell(columna);
                celda.setWidth(String.valueOf(anchos[columna]));
                celda.setWidthType(TableWidthType.DXA);
            }
        }
        return tabla;
    }

    /** Alto exacto y sin partirse entre páginas. */
    static void altoExacto(XWPFTableRow fila, int twips) {
        fila.setHeight(twips);
        fila.setHeightRule(TableRowHeightRule.EXACT);
        fila.setCantSplitRow(true);
    }

    static XWPFParagraph primerParrafo(XWPFTableCell celda) {
        XWPFParagraph parrafo = celda.getParagraphs().isEmpty()
                ? celda.addParagraph() : celda.getParagraphs().get(0);
        sinEspacio(parrafo);
        return parrafo;
    }

    static XWPFParagraph parrafo(XWPFTableCell celda, ParagraphAlignment alineacion) {
        XWPFParagraph parrafo = celda.addParagraph();
        sinEspacio(parrafo);
        parrafo.setAlignment(alineacion);
        return parrafo;
    }

    static void sinEspacio(XWPFParagraph parrafo) {
        parrafo.setSpacingBefore(0);
        parrafo.setSpacingAfter(0);
    }

    static XWPFRun texto(XWPFParagraph parrafo, String texto, int tamano, boolean negrita,
                         String color) {
        XWPFRun run = parrafo.createRun();
        run.setFontFamily(FUENTE);
        run.setFontSize(tamano);
        run.setBold(negrita);
        if (color != null) {
            run.setColor(color);
        }
        run.setText(texto == null ? "" : texto);
        return run;
    }

    /** La foto encajada en el hueco sin deformarla: manda el lado que antes llega al borde. */
    static void imagen(XWPFParagraph parrafo, Imagen imagen, int anchoMaximo, int altoMaximo)
            throws IOException {
        double escala = Math.min((double) anchoMaximo / imagen.ancho(),
                (double) altoMaximo / imagen.alto());
        int ancho = (int) Math.floor(imagen.ancho() * escala) * EMU_POR_TWIP;
        int alto = (int) Math.floor(imagen.alto() * escala) * EMU_POR_TWIP;
        try (InputStream datos = new ByteArrayInputStream(imagen.jpeg())) {
            parrafo.createRun().addPicture(datos, PictureType.JPEG, "foto.jpg", ancho, alto);
        } catch (InvalidFormatException e) {
            throw new IOException("no se ha podido meter la foto en el Word", e);
        }
    }

    static void bordeCaja(XWPFTableCell celda, int grosor, String color) {
        CTTcPr propiedades = celda.getCTTc().isSetTcPr()
                ? celda.getCTTc().getTcPr() : celda.getCTTc().addNewTcPr();
        CTTcBorders bordes = propiedades.isSetTcBorders()
                ? propiedades.getTcBorders() : propiedades.addNewTcBorders();
        for (CTBorder borde : new CTBorder[] {bordes.addNewTop(), bordes.addNewLeft(),
                bordes.addNewBottom(), bordes.addNewRight()}) {
            borde.setVal(STBorder.SINGLE);
            borde.setSz(BigInteger.valueOf(grosor));
            borde.setSpace(BigInteger.ZERO);
            borde.setColor(color);
        }
    }

    /** Un párrafo vacío de alto exacto: el aire entre dos tablas, que sin él Word fusiona. */
    static XWPFParagraph parrafoDeAlto(XWPFDocument doc, int twips) {
        XWPFParagraph parrafo = doc.createParagraph();
        CTPPr propiedades = parrafo.getCTP().isSetPPr()
                ? parrafo.getCTP().getPPr() : parrafo.getCTP().addNewPPr();
        CTSpacing espaciado = propiedades.isSetSpacing()
                ? propiedades.getSpacing() : propiedades.addNewSpacing();
        espaciado.setBefore(BigInteger.ZERO);
        espaciado.setAfter(BigInteger.ZERO);
        espaciado.setLine(BigInteger.valueOf(twips));
        espaciado.setLineRule(STLineSpacingRule.EXACT);
        return parrafo;
    }

    /**
     * El párrafo de 1 pt que cierra una página, con su salto si hay otra
     * detrás. Tan bajo porque lo que va detrás del salto (la marca de
     * párrafo) abre la página siguiente y le roba ese alto.
     */
    static void separador(XWPFDocument doc, boolean saltoDePagina) {
        XWPFRun run = parrafoDeAlto(doc, ALTO_SEPARADOR).createRun();
        run.setFontSize(1);
        if (saltoDePagina) {
            run.addBreak(BreakType.PAGE);
        }
    }

    static byte[] escribir(XWPFDocument doc) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        doc.write(salida);
        return salida.toByteArray();
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/OrdenCorteDocBuilder.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * Escribe las órdenes de corte de una temporada: un solo Word apaisado con
 * una página por modelo + piel + color.
 *
 * Arriba, la foto del modelo a la izquierda y a la derecha lo que el cortador
 * tiene que ver de un vistazo —la referencia, el color y cuántos bolsos, en
 * grande—, con cliente y temporada en pequeño encima como subtítulo. El resto
 * de la página son las pieles —principal, de combinación y forro—, una por
 * renglón y separadas, cada una con un recuadro vacío a la derecha donde se
 * pega la muestra de piel una vez impresa.
 *
 * Cada orden tiene que caber en UNA página, porque se le pega la muestra y se
 * cuelga en el puesto: por eso las alturas son exactas y la de cada piel se
 * calcula con lo que queda debajo de la banda. Sin plantilla .docx: la
 * maquetación son las constantes de esta clase.
 */
public class OrdenCorteDocBuilder {

    /** A4 apaisado con márgenes de 1,2 cm. */
    static final int ANCHO_PAGINA = 16838;
    static final int ALTO_PAGINA = 11906;
    static final int MARGEN = 680;
    static final int ANCHO_UTIL = ANCHO_PAGINA - 2 * MARGEN;
    static final int ALTO_UTIL = ALTO_PAGINA - 2 * MARGEN;

    /** Banda superior de 7 cm: foto de 10,5 cm a la izquierda, datos a la derecha. */
    static final int ALTO_BANDA = 3969;
    static final int ANCHO_FOTO = 5953;
    /** Aire entre la banda y la primera piel. */
    static final int SEPARACION = 340;
    /** Aire entre dos pieles, para que los recuadros no se toquen. */
    static final int ESPACIO_ENTRE_PIELES = 227;
    /** Una piel sola no necesita media página: 6 cm de recuadro sobran. */
    static final int ALTO_MAXIMO_PIEL = 3402;
    /** Los separadores de 1 pt y un margen para que Word no se pase de página. */
    static final int RESERVA = 170;
    /** La columna de rótulo y nombre, alineada con la foto. */
    static final int ANCHO_NOMBRE = ANCHO_FOTO;
    static final int MARGEN_CELDA = 85;

    private static final String GRIS = "595959";
    private static final String NEGRO = "000000";
    private static final int GROSOR_RECUADRO = 8;

    public byte[] generar(List<OrdenCorte> ordenes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            WordCorte.configurarPagina(doc, ANCHO_PAGINA, ALTO_PAGINA, true,
                    MARGEN, MARGEN, MARGEN, MARGEN / 2);
            for (int i = 0; i < ordenes.size(); i++) {
                escribirBanda(doc, ordenes.get(i));
                WordCorte.parrafoDeAlto(doc, SEPARACION);
                escribirPieles(doc, ordenes.get(i).pieles());
                WordCorte.separador(doc, i < ordenes.size() - 1);
            }
            return WordCorte.escribir(doc);
        }
    }

    /** Alto de cada renglón de piel: lo que queda debajo de la banda, a partes iguales. */
    static int altoPiel(int pieles) {
        int disponible = ALTO_UTIL - ALTO_BANDA - SEPARACION - RESERVA
                - (pieles - 1) * ESPACIO_ENTRE_PIELES;
        return Math.min(ALTO_MAXIMO_PIEL, disponible / pieles);
    }

    private void escribirBanda(XWPFDocument doc, OrdenCorte orden) throws IOException {
        XWPFTable banda = WordCorte.tabla(doc, 1, ANCHO_FOTO, ANCHO_UTIL - ANCHO_FOTO);
        banda.setCellMargins(MARGEN_CELDA, MARGEN_CELDA, MARGEN_CELDA, MARGEN_CELDA);
        XWPFTableRow fila = banda.getRow(0);
        WordCorte.altoExacto(fila, ALTO_BANDA);

        XWPFTableCell celdaFoto = fila.getCell(0);
        celdaFoto.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph parrafoFoto = WordCorte.primerParrafo(celdaFoto);
        parrafoFoto.setAlignment(ParagraphAlignment.CENTER);
        if (orden.fotoPrincipal() != null) {
            WordCorte.imagen(parrafoFoto, orden.fotoPrincipal(),
                    ANCHO_FOTO - 2 * MARGEN_CELDA, ALTO_BANDA - 2 * MARGEN_CELDA - 60);
        } else {
            WordCorte.texto(parrafoFoto, "Sin foto del modelo", 11, false, GRIS);
        }

        XWPFTableCell celdaDatos = fila.getCell(1);
        celdaDatos.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);
        XWPFParagraph subtitulo = WordCorte.primerParrafo(celdaDatos);
        subtitulo.setAlignment(ParagraphAlignment.RIGHT);
        WordCorte.texto(subtitulo, (orden.cliente() + " · " + orden.temporada())
                .toUpperCase(Locale.ROOT), 12, false, GRIS);

        XWPFParagraph referencia = WordCorte.parrafo(celdaDatos, ParagraphAlignment.RIGHT);
        WordCorte.texto(referencia, orden.referencia(), 40, true, NEGRO);

        XWPFParagraph color = WordCorte.parrafo(celdaDatos, ParagraphAlignment.RIGHT);
        WordCorte.texto(color, "Color  ", 12, false, GRIS);
        WordCorte.texto(color, orden.color(), 28, true, NEGRO);

        XWPFParagraph bolsos = WordCorte.parrafo(celdaDatos, ParagraphAlignment.RIGHT);
        WordCorte.texto(bolsos, "Bolsos a cortar  ", 12, false, GRIS);
        WordCorte.texto(bolsos, String.valueOf(orden.bolsos()), 40, true, NEGRO);
    }

    private void escribirPieles(XWPFDocument doc, PielesArticulo pieles) {
        List<String[]> renglones = new ArrayList<>();
        renglones.add(new String[] {"Piel", pieles.nombrePiel()});
        for (int i = 0; i < pieles.combinaciones().size(); i++) {
            renglones.add(new String[] {"Combinación " + (i + 1), pieles.combinaciones().get(i)});
        }
        if (pieles.tieneForro()) {
            renglones.add(new String[] {"Forro", pieles.forro()});
        }

        int alto = altoPiel(renglones.size());
        XWPFTable tabla = WordCorte.tabla(doc, 2 * renglones.size() - 1,
                ANCHO_NOMBRE, ANCHO_UTIL - ANCHO_NOMBRE);
        tabla.setCellMargins(MARGEN_CELDA, MARGEN_CELDA, MARGEN_CELDA, MARGEN_CELDA);
        for (int i = 0; i < renglones.size(); i++) {
            XWPFTableRow fila = tabla.getRow(2 * i);
            WordCorte.altoExacto(fila, alto);
            XWPFTableCell nombre = fila.getCell(0);
            nombre.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            WordCorte.texto(WordCorte.primerParrafo(nombre),
                    renglones.get(i)[0].toUpperCase(Locale.ROOT), 10, false, GRIS);
            WordCorte.texto(WordCorte.parrafo(nombre, ParagraphAlignment.LEFT),
                    renglones.get(i)[1], 20, true, NEGRO);
            WordCorte.bordeCaja(fila.getCell(1), GROSOR_RECUADRO, NEGRO);
            if (i < renglones.size() - 1) {
                WordCorte.altoExacto(tabla.getRow(2 * i + 1), ESPACIO_ENTRE_PIELES);
            }
        }
    }
}
```

- [ ] **Step 4: comprobar que pasa**

Run: `mvn -q test -Dtest=OrdenCorteDocBuilderTest`
Expected: PASS y `target/Ordenes de corte ejemplo.docx` creado.

- [ ] **Step 5: commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/corte src/test/java/com/puntotres/packinglist/service/corte/OrdenCorteDocBuilderTest.java src/test/java/com/puntotres/packinglist/testutil/WordDePrueba.java
git commit -m "orden de corte apaisada con la foto, los datos en grande y un recuadro por piel"
```

---

### Tarea 6: el Word de fotos del artículo

**Files:**
- Create: `service/corte/FotosCorte.java`, `FotosCorteDocBuilder.java`
- Test: `src/test/java/com/puntotres/packinglist/service/corte/FotosCorteDocBuilderTest.java`

**Interfaces:**
- Consumes: `WordCorte`, `Imagen`, `PielesArticulo` (tarea 5); `WordDePrueba`, `FotosDePrueba`.
- Produces: `record FotosCorte(String temporada, String referencia, PielesArticulo pieles, List<Imagen> fotos)`; `new FotosCorteDocBuilder().generar(FotosCorte) → byte[]`; `FotosCorteDocBuilder.describirPieles(PielesArticulo) → String`.

- [ ] **Step 1: test que falla**

<!-- fichero: src/test/java/com/puntotres/packinglist/service/corte/FotosCorteDocBuilderTest.java -->
```java
package com.puntotres.packinglist.service.corte;

import static com.puntotres.packinglist.testutil.WordDePrueba.altoEmu;
import static com.puntotres.packinglist.testutil.WordDePrueba.anchoEmu;
import static com.puntotres.packinglist.testutil.WordDePrueba.fotosDe;
import static com.puntotres.packinglist.testutil.WordDePrueba.saltosDePagina;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class FotosCorteDocBuilderTest {

    private static final PielesArticulo PIELES =
            new PielesArticulo("Box calf", "Tela", List.of("Ante"));

    @Test
    void seisFotosPorA4EnDosColumnasYTresFilas() throws IOException {
        XWPFDocument doc = generar(fotos(7));

        CTPageSz pagina = doc.getDocument().getBody().getSectPr().getPgSz();
        assertEquals(BigInteger.valueOf(11906), pagina.getW());
        assertEquals(BigInteger.valueOf(16838), pagina.getH());
        assertNull(pagina.getOrient(), "vertical");
        assertEquals(2, doc.getTables().size(), "7 fotos son dos hojas");
        assertEquals(3, doc.getTables().get(0).getRows().size());
        assertEquals(2, doc.getTables().get(0).getRow(0).getTableCells().size());
        assertEquals(1, doc.getTables().get(1).getRows().size());
        assertEquals(6, fotosDe(doc.getTables().get(0)).size());
        assertEquals(1, fotosDe(doc.getTables().get(1)).size());
        assertEquals(1, saltosDePagina(doc));
    }

    @Test
    void laRejillaLlenaLaPaginaYCadaFotoCabeEnSuCelda() throws IOException {
        XWPFDocument doc = generar(fotos(6));

        XWPFTable rejilla = doc.getTables().get(0);
        int alto = rejilla.getRows().stream().mapToInt(fila -> fila.getHeight()).sum();
        assertTrue(alto <= FotosCorteDocBuilder.ALTO_UTIL);
        assertTrue(alto >= FotosCorteDocBuilder.ALTO_UTIL - 200, "llena la hoja, sin franja vacía abajo");
        for (XWPFPicture foto : fotosDe(rejilla)) {
            assertTrue(anchoEmu(foto) <= (long) FotosCorteDocBuilder.ANCHO_CELDA * WordCorte.EMU_POR_TWIP);
            assertTrue(altoEmu(foto) <= (long) FotosCorteDocBuilder.ALTO_CELDA * WordCorte.EMU_POR_TWIP);
        }
    }

    @Test
    void laCabeceraDeCadaHojaLlevaTemporadaReferenciaYPieles() throws IOException {
        XWPFDocument doc = generar(fotos(1));

        String cabecera = doc.getHeaderList().get(0).getText();
        for (String esperado : List.of("H26", "ULL729.AL0103", "Piel: Box calf",
                "Combinación: Ante", "Forro: Tela")) {
            assertTrue(cabecera.contains(esperado), "falta '" + esperado + "' en: " + cabecera);
        }
    }

    @Test
    void lasPartesVaciasNoSalenEnLaCabecera() {
        assertEquals("Piel: Box  ·  Combinación 1: A  ·  Combinación 2: B",
                FotosCorteDocBuilder.describirPieles(new PielesArticulo("Box", "", List.of("A", "B"))));
        assertEquals("", FotosCorteDocBuilder.describirPieles(new PielesArticulo("", "", List.of())));
    }

    @Test
    void dejaUnEjemploEnTargetParaRevisarAOjo() throws IOException {
        byte[] word = new FotosCorteDocBuilder().generar(new FotosCorte("H26", "ULL729.AL0103",
                new PielesArticulo("Vachette grainée", "Cabretilla", List.of("Ante")), fotos(8)));

        Path destino = Path.of("target", "Fotos ejemplo.docx");
        Files.createDirectories(destino.getParent());
        Files.write(destino, word);
        assertTrue(Files.size(destino) > 0);
    }

    private static XWPFDocument generar(List<Imagen> fotos) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(new FotosCorteDocBuilder()
                .generar(new FotosCorte("H26", "ULL729.AL0103", PIELES, fotos))));
    }

    /** Alterna apaisadas y verticales, para ver cómo encaja cada una. */
    private static List<Imagen> fotos(int cuantas) {
        List<Imagen> fotos = new ArrayList<>();
        for (int i = 1; i <= cuantas; i++) {
            boolean apaisada = i % 2 == 1;
            int ancho = apaisada ? 1600 : 1200;
            int alto = apaisada ? 1200 : 1600;
            fotos.add(new Imagen(FotosDePrueba.relleno(ancho, alto, "FOTO " + i,
                    new Color(70 + 15 * i, 90, 110)), ancho, alto));
        }
        return fotos;
    }
}
```

- [ ] **Step 2: comprobar que falla**

Run: `mvn -q test -Dtest=FotosCorteDocBuilderTest`
Expected: FAIL de compilación.

- [ ] **Step 3: implementación**

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/FotosCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.util.List;

/** Un Word de fotos: las del modelo, con los datos de una de sus referencias (modelo + piel). */
public record FotosCorte(String temporada, String referencia, PielesArticulo pieles,
                         List<Imagen> fotos) {

    public FotosCorte {
        fotos = List.copyOf(fotos);
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/FotosCorteDocBuilder.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

/**
 * Escribe el Word de fotos de una referencia: todas las fotos de su modelo,
 * seis por A4 en una cuadrícula de 2 columnas por 3 filas que llena la hoja
 * con márgenes estrechos, sin pie de foto (el nombre del fichero no dice
 * nada). Tantas hojas como hagan falta.
 *
 * Temporada, referencia y pieles van en la CABECERA de página y no en el
 * cuerpo: así se repiten en cada hoja, que se separan al imprimir, y la
 * cuadrícula tiene el cuerpo entero para ella. Sin plantilla .docx: la
 * maquetación son las constantes de esta clase.
 */
public class FotosCorteDocBuilder {

    /** A4 vertical: 0,8 cm de margen y 2 cm arriba, que es donde vive la cabecera. */
    static final int ANCHO_PAGINA = 11906;
    static final int ALTO_PAGINA = 16838;
    static final int MARGEN = 454;
    static final int MARGEN_SUPERIOR = 1134;
    static final int DISTANCIA_CABECERA = 340;
    static final int COLUMNAS = 2;
    static final int FILAS = 3;
    static final int FOTOS_POR_PAGINA = COLUMNAS * FILAS;
    static final int ANCHO_UTIL = ANCHO_PAGINA - 2 * MARGEN;
    static final int ALTO_UTIL = ALTO_PAGINA - MARGEN_SUPERIOR - MARGEN;
    /** Los separadores de 1 pt y un margen para que Word no se pase de página. */
    static final int RESERVA = 170;
    static final int ANCHO_CELDA = ANCHO_UTIL / COLUMNAS;
    static final int ALTO_CELDA = (ALTO_UTIL - RESERVA) / FILAS;
    /** Aire alrededor de cada foto: 0,1 cm. */
    static final int HUECO = 57;

    public byte[] generar(FotosCorte fotos) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            WordCorte.configurarPagina(doc, ANCHO_PAGINA, ALTO_PAGINA, false,
                    MARGEN_SUPERIOR, MARGEN, MARGEN, DISTANCIA_CABECERA);
            escribirCabecera(doc, fotos);
            List<Imagen> imagenes = fotos.fotos();
            int paginas = (imagenes.size() + FOTOS_POR_PAGINA - 1) / FOTOS_POR_PAGINA;
            for (int pagina = 0; pagina < paginas; pagina++) {
                escribirRejilla(doc, imagenes.subList(pagina * FOTOS_POR_PAGINA,
                        Math.min(imagenes.size(), (pagina + 1) * FOTOS_POR_PAGINA)));
                WordCorte.separador(doc, pagina < paginas - 1);
            }
            return WordCorte.escribir(doc);
        }
    }

    /** "Piel: X · Combinación: Y · Forro: Z", sin las partes vacías. */
    static String describirPieles(PielesArticulo pieles) {
        List<String> partes = new ArrayList<>();
        if (!pieles.nombrePiel().isEmpty()) {
            partes.add("Piel: " + pieles.nombrePiel());
        }
        List<String> combinaciones = pieles.combinaciones();
        for (int i = 0; i < combinaciones.size(); i++) {
            partes.add((combinaciones.size() == 1 ? "Combinación: " : "Combinación " + (i + 1) + ": ")
                    + combinaciones.get(i));
        }
        if (pieles.tieneForro()) {
            partes.add("Forro: " + pieles.forro());
        }
        return String.join("  ·  ", partes);
    }

    private void escribirCabecera(XWPFDocument doc, FotosCorte fotos) {
        XWPFHeader cabecera = doc.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph titulo = cabecera.createParagraph();
        WordCorte.sinEspacio(titulo);
        WordCorte.texto(titulo, fotos.temporada() + " · " + fotos.referencia(), 12, true, "000000");
        String pieles = describirPieles(fotos.pieles());
        if (!pieles.isEmpty()) {
            XWPFParagraph detalle = cabecera.createParagraph();
            WordCorte.sinEspacio(detalle);
            WordCorte.texto(detalle, pieles, 9, false, "404040");
        }
    }

    private void escribirRejilla(XWPFDocument doc, List<Imagen> imagenes) throws IOException {
        int filas = (imagenes.size() + COLUMNAS - 1) / COLUMNAS;
        int[] anchos = new int[COLUMNAS];
        Arrays.fill(anchos, ANCHO_CELDA);
        XWPFTable rejilla = WordCorte.tabla(doc, filas, anchos);
        rejilla.setCellMargins(HUECO, HUECO, HUECO, HUECO);
        for (int fila = 0; fila < filas; fila++) {
            WordCorte.altoExacto(rejilla.getRow(fila), ALTO_CELDA);
        }
        for (int i = 0; i < imagenes.size(); i++) {
            XWPFTableCell celda = rejilla.getRow(i / COLUMNAS).getCell(i % COLUMNAS);
            celda.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            XWPFParagraph parrafo = WordCorte.primerParrafo(celda);
            parrafo.setAlignment(ParagraphAlignment.CENTER);
            WordCorte.imagen(parrafo, imagenes.get(i),
                    ANCHO_CELDA - 2 * HUECO, ALTO_CELDA - 2 * HUECO - 60);
        }
    }
}
```

- [ ] **Step 4: comprobar que pasa**

Run: `mvn -q test -Dtest=FotosCorteDocBuilderTest`
Expected: PASS y `target/Fotos ejemplo.docx` creado.

- [ ] **Step 5: commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/corte src/test/java/com/puntotres/packinglist/service/corte/FotosCorteDocBuilderTest.java
git commit -m "word de fotos con seis por hoja y los datos del articulo en la cabecera"
```

---

### Tarea 7: de las filas de la pantalla a los documentos

**Files:**
- Create: `service/corte/FilaCorte.java`, `DocumentoCorte.java`, `ResultadoCorte.java`
- Modify: `service/corte/DocumentosCorteService.java` (método `generar`)
- Test: `src/test/java/com/puntotres/packinglist/service/corte/DocumentosCorteGeneracionTest.java`

**Interfaces:**
- Consumes: `ArticuloCorte`, `ColorCorte`, `FotosTemporada`, `FotoModelo`, `OrdenCorteDocBuilder`, `FotosCorteDocBuilder`, `Imagen`, `PielesArticulo`.
- Produces: `FilaCorte(ArticuloCorte)` con `referencia()`, `colores()` (bolsos actuales), `setBolsos(int indiceColor, int bolsos)`, `get/setNombrePiel`, `get/setForro`, `get/setCombinaciones`, `get/setFotoPrincipal`, `tieneBolsos()`, `pieles()`; `record DocumentoCorte(String descripcion, String nombreFichero, Path fichero)`; `record ResultadoCorte(List<DocumentoCorte> documentos, List<String> avisos)`; `DocumentosCorteService.generar(String cliente, String temporada, List<FilaCorte> filas, FotosTemporada fotos, Function<FotoModelo, Optional<Path>> reducida, Path destino) → ResultadoCorte`.

- [ ] **Step 1: test que falla**

<!-- fichero: src/test/java/com/puntotres/packinglist/service/corte/DocumentosCorteGeneracionTest.java -->
```java
package com.puntotres.packinglist.service.corte;

import static com.puntotres.packinglist.testutil.WordDePrueba.fotosDe;
import static com.puntotres.packinglist.testutil.WordDePrueba.texto;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.puntotres.packinglist.testutil.FotosDePrueba;

class DocumentosCorteGeneracionTest {

    @TempDir
    Path dir;

    private final DocumentosCorteService servicio = new DocumentosCorteService(List.of());
    private FotoModelo fotoA;
    private FotoModelo fotoB;
    private FotosTemporada fotos;
    private final Map<Path, Path> reducidas = new HashMap<>();

    @BeforeEach
    void fotosDeUll027() throws IOException {
        fotoA = new FotoModelo("ULL027", "a.jpg", dir.resolve("00001.jpg"));
        fotoB = new FotoModelo("ULL027", "b.jpg", dir.resolve("00002.jpg"));
        fotos = new FotosTemporada(Map.of("ULL027", List.of(fotoA, fotoB)), List.of());
        reducidas.put(fotoA.original(), escribir("a-reducida.jpg", Color.RED));
        reducidas.put(fotoB.original(), escribir("b-reducida.jpg", Color.BLUE));
    }

    @Test
    void unaOrdenPorColorConBolsosYUnWordDeFotosPorReferenciaConFotos() throws IOException {
        ResultadoCorte resultado = generar("H26", List.of(
                fila("ULL027.AL0103", 10, 0),
                fila("ULL712.AL0103", 5),
                fila("ULL999.AL0001", 0)));

        assertEquals(List.of("Ordenes de corte AMI H26.docx", "Fotos ULL027.AL0103.docx"),
                resultado.documentos().stream().map(DocumentoCorte::nombreFichero).toList());
        XWPFDocument ordenes = abrir(resultado.documentos().get(0));
        assertEquals(4, ordenes.getTables().size(), "ULL027 en negro y ULL712: dos órdenes");
        String texto = texto(ordenes);
        assertTrue(texto.contains("ULL027.AL0103") && texto.contains("ULL712.AL0103"));
        assertTrue(!texto.contains("ULL999"), "sin bolsos no hay orden");
        XWPFDocument album = abrir(resultado.documentos().get(1));
        assertEquals(2, fotosDe(album.getTables().get(0)).size());
        assertTrue(resultado.avisos().isEmpty(), resultado.avisos().toString());
    }

    @Test
    void laFotoPrincipalEsLaElegida() throws IOException {
        FilaCorte fila = fila("ULL027.AL0103", 10);
        fila.setFotoPrincipal(1);

        ResultadoCorte resultado = generar("H26", List.of(fila));

        assertArrayEquals(Files.readAllBytes(reducidas.get(fotoB.original())),
                fotoPrincipal(resultado));
    }

    @Test
    void siLaElegidaNoSeHaPodidoConvertirSaleLaSiguienteLegible() throws IOException {
        reducidas.remove(fotoB.original());
        FilaCorte fila = fila("ULL027.AL0103", 10);
        fila.setFotoPrincipal(1);

        ResultadoCorte resultado = generar("H26", List.of(fila));

        assertArrayEquals(Files.readAllBytes(reducidas.get(fotoA.original())),
                fotoPrincipal(resultado));
    }

    @Test
    void laFotoEsDelModeloYValeParaTodasSusPieles() throws IOException {
        ResultadoCorte resultado = generar("H26", List.of(
                fila("ULL027.AL0103", 1), fila("ULL027.AL0216", 1)));

        assertEquals(List.of("Ordenes de corte AMI H26.docx", "Fotos ULL027.AL0103.docx",
                        "Fotos ULL027.AL0216.docx"),
                resultado.documentos().stream().map(DocumentoCorte::nombreFichero).toList());
    }

    @Test
    void sinBolsosEnNingunaFilaNoSaleNadaYSeAvisa() throws IOException {
        ResultadoCorte resultado = generar("H26", List.of(fila("ULL027.AL0103", 0)));

        assertTrue(resultado.documentos().isEmpty());
        assertEquals(1, resultado.avisos().size());
    }

    @Test
    void elNombreDelFicheroSeSanea() throws IOException {
        ResultadoCorte resultado = generar("H26/27", List.of(fila("ULL712.AL0103", 1)));

        assertEquals("Ordenes de corte AMI H26_27.docx", resultado.documentos().get(0).nombreFichero());
        assertTrue(Files.exists(resultado.documentos().get(0).fichero()));
    }

    private ResultadoCorte generar(String temporada, List<FilaCorte> filas) throws IOException {
        return servicio.generar("AMI", temporada, filas, fotos,
                foto -> Optional.ofNullable(reducidas.get(foto.original())), dir.resolve("salida"));
    }

    private static FilaCorte fila(String referencia, int... bolsos) {
        String[] colores = {"001 BLACK", "718 VANILLA CREAM", "A237 MOCHA"};
        List<ColorCorte> lista = new java.util.ArrayList<>();
        for (int i = 0; i < bolsos.length; i++) {
            lista.add(new ColorCorte(colores[i], bolsos[i]));
        }
        FilaCorte fila = new FilaCorte(new ArticuloCorte(ReferenciaCorte.deAmi(referencia), lista));
        fila.setNombrePiel("Box calf");
        return fila;
    }

    private Path escribir(String nombre, Color color) throws IOException {
        Path fichero = dir.resolve(nombre);
        Files.write(fichero, FotosDePrueba.jpeg(160, 120, color));
        return fichero;
    }

    private static XWPFDocument abrir(DocumentoCorte documento) throws IOException {
        try (InputStream entrada = Files.newInputStream(documento.fichero())) {
            return new XWPFDocument(entrada);
        }
    }

    private static byte[] fotoPrincipal(ResultadoCorte resultado) throws IOException {
        return fotosDe(abrir(resultado.documentos().get(0)).getTables().get(0))
                .get(0).getPictureData().getData();
    }
}
```

- [ ] **Step 2: comprobar que falla**

Run: `mvn -q test -Dtest=DocumentosCorteGeneracionTest`
Expected: FAIL de compilación.

- [ ] **Step 3: implementación**

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/FilaCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Una fila de la pantalla de pieles con lo que se ha tecleado: el artículo
 * del pedido, los bolsos de cada color (editables; 0 = esa orden no sale),
 * los nombres de las pieles y qué foto del modelo va de principal.
 *
 * Mutable a propósito: vive en la sesión mientras se rellena la tabla, y cada
 * envío del formulario (añadir una combinación, generar) la actualiza.
 */
public class FilaCorte {

    private final ArticuloCorte articulo;
    private final int[] bolsos;
    private String nombrePiel = "";
    private String forro = "";
    private final List<String> combinaciones = new ArrayList<>();
    private int fotoPrincipal;

    public FilaCorte(ArticuloCorte articulo) {
        this.articulo = articulo;
        this.bolsos = articulo.colores().stream().mapToInt(ColorCorte::bolsos).toArray();
    }

    public ReferenciaCorte referencia() {
        return articulo.referencia();
    }

    /** Los colores con los bolsos que hay ahora, tecleados o del pedido. */
    public List<ColorCorte> colores() {
        return IntStream.range(0, bolsos.length)
                .mapToObj(i -> new ColorCorte(articulo.colores().get(i).color(), bolsos[i]))
                .toList();
    }

    public void setBolsos(int indiceColor, int valor) {
        if (indiceColor >= 0 && indiceColor < bolsos.length && valor >= 0) {
            bolsos[indiceColor] = valor;
        }
    }

    /** ¿Hay algo que cortar? Una fila sin bolsos no saca órdenes ni Word de fotos. */
    public boolean tieneBolsos() {
        return Arrays.stream(bolsos).anyMatch(valor -> valor > 0);
    }

    public String getNombrePiel() {
        return nombrePiel;
    }

    public void setNombrePiel(String nombrePiel) {
        this.nombrePiel = limpio(nombrePiel);
    }

    public String getForro() {
        return forro;
    }

    public void setForro(String forro) {
        this.forro = limpio(forro);
    }

    /** Las casillas de combinación tal como están, vacías incluidas: una por columna. */
    public List<String> getCombinaciones() {
        return List.copyOf(combinaciones);
    }

    public void setCombinaciones(List<String> combinaciones) {
        this.combinaciones.clear();
        if (combinaciones != null) {
            combinaciones.forEach(combinacion -> this.combinaciones.add(limpio(combinacion)));
        }
    }

    /** Índice en las fotos de su modelo, ordenadas por nombre. */
    public int getFotoPrincipal() {
        return fotoPrincipal;
    }

    public void setFotoPrincipal(int fotoPrincipal) {
        this.fotoPrincipal = Math.max(0, fotoPrincipal);
    }

    public PielesArticulo pieles() {
        return new PielesArticulo(nombrePiel, forro, combinaciones);
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/DocumentoCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.nio.file.Path;

/**
 * Un Word generado. Vive en disco, en el directorio de la sesión, y no en
 * memoria: una temporada son decenas de Word con fotos.
 */
public record DocumentoCorte(String descripcion, String nombreFichero, Path fichero) {
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/ResultadoCorte.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.util.List;

/** Lo que sale de generar: los documentos, en el orden en que se enseñan, y los avisos. */
public record ResultadoCorte(List<DocumentoCorte> documentos, List<String> avisos) {

    public ResultadoCorte {
        documentos = List.copyOf(documentos);
        avisos = List.copyOf(avisos);
    }
}
```

Nuevo `DocumentosCorteService` completo (sustituye al de la tarea 1):

<!-- fichero: src/main/java/com/puntotres/packinglist/service/corte/DocumentosCorteService.java -->
```java
package com.puntotres.packinglist.service.corte;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Los documentos del corte: a qué cliente le toca cada pedido y, a partir de
 * lo tecleado en la pantalla de pieles, los Word que salen.
 *
 * Salen un Word de órdenes con una página por referencia y color con bolsos,
 * y un Word de fotos por referencia con bolsos cuyo modelo tenga fotos. La
 * foto es del MODELO: la carpeta ULL027 vale para ULL027.AL0103 y para
 * ULL027.AL0216, así que las dos llevan su Word con las mismas fotos.
 */
@Service
public class DocumentosCorteService {

    /** Lo que Windows no admite en un nombre de fichero, y la barra de la URL de descarga. */
    private static final Pattern CARACTERES_INSEGUROS = Pattern.compile("[\\\\/:*?\"<>|]+");

    private final List<ClienteCorte> clientes;
    private final OrdenCorteDocBuilder ordenes = new OrdenCorteDocBuilder();
    private final FotosCorteDocBuilder albumes = new FotosCorteDocBuilder();

    public DocumentosCorteService(List<ClienteCorte> clientes) {
        this.clientes = List.copyOf(clientes);
    }

    /** El cliente con esa clave, o vacío si todavía no tiene documentos del corte. */
    public Optional<ClienteCorte> clientePara(String clave) {
        String buscada = ReferenciaCorte.normalizar(clave);
        return clientes.stream()
                .filter(cliente -> cliente.clave().equals(buscada))
                .findFirst();
    }

    /**
     * Escribe los Word en {@code destino}. {@code reducida} da el JPEG
     * reducido de cada foto, o vacío si no se pudo convertir (de eso ya avisó
     * la conversión).
     */
    public ResultadoCorte generar(String cliente, String temporada, List<FilaCorte> filas,
                                  FotosTemporada fotos,
                                  Function<FotoModelo, Optional<Path>> reducida,
                                  Path destino) throws IOException {
        Files.createDirectories(destino);
        Map<Path, Optional<Imagen>> leidas = new HashMap<>();
        List<String> avisos = new ArrayList<>();
        List<OrdenCorte> paginas = new ArrayList<>();
        List<DocumentoCorte> documentosFotos = new ArrayList<>();

        for (FilaCorte fila : filas) {
            if (!fila.tieneBolsos()) {
                continue;
            }
            String referencia = fila.referencia().referencia();
            List<FotoModelo> delModelo = fotos.de(fila.referencia().modelo());
            Imagen principal = principal(delModelo, fila.getFotoPrincipal(), reducida, leidas, avisos);
            for (ColorCorte color : fila.colores()) {
                if (color.bolsos() > 0) {
                    paginas.add(new OrdenCorte(cliente, temporada, referencia, color.color(),
                            color.bolsos(), fila.pieles(), principal));
                }
            }

            List<Imagen> imagenes = new ArrayList<>();
            for (FotoModelo foto : delModelo) {
                imagen(foto, reducida, leidas, avisos).ifPresent(imagenes::add);
            }
            if (!imagenes.isEmpty()) {
                String nombre = sanear("Fotos " + referencia + ".docx");
                Path fichero = destino.resolve(nombre);
                Files.write(fichero, albumes.generar(
                        new FotosCorte(temporada, referencia, fila.pieles(), imagenes)));
                documentosFotos.add(new DocumentoCorte("Fotos de " + referencia + " ("
                        + imagenes.size() + (imagenes.size() == 1 ? " foto)" : " fotos)"),
                        nombre, fichero));
            }
        }

        List<DocumentoCorte> documentos = new ArrayList<>();
        if (paginas.isEmpty()) {
            avisos.add("Ninguna referencia tiene bolsos que cortar: no sale el documento de "
                    + "órdenes de corte");
        } else {
            String nombre = sanear("Ordenes de corte " + cliente + " " + temporada + ".docx");
            Path fichero = destino.resolve(nombre);
            Files.write(fichero, ordenes.generar(paginas));
            documentos.add(new DocumentoCorte("Órdenes de corte (" + paginas.size()
                    + (paginas.size() == 1 ? " página)" : " páginas)"), nombre, fichero));
        }
        documentos.addAll(documentosFotos);
        return new ResultadoCorte(documentos, avisos);
    }

    /**
     * La elegida y, si no se pudo convertir, la primera legible del modelo:
     * una orden sin foto teniendo fotos del bolso sería un fallo que no se ve
     * hasta el puesto de corte.
     */
    private static Imagen principal(List<FotoModelo> fotos, int elegida,
                                    Function<FotoModelo, Optional<Path>> reducida,
                                    Map<Path, Optional<Imagen>> leidas, List<String> avisos) {
        List<FotoModelo> candidatas = new ArrayList<>();
        if (elegida >= 0 && elegida < fotos.size()) {
            candidatas.add(fotos.get(elegida));
        }
        candidatas.addAll(fotos);
        for (FotoModelo foto : candidatas) {
            Optional<Imagen> imagen = imagen(foto, reducida, leidas, avisos);
            if (imagen.isPresent()) {
                return imagen.get();
            }
        }
        return null;
    }

    private static Optional<Imagen> imagen(FotoModelo foto,
                                           Function<FotoModelo, Optional<Path>> reducida,
                                           Map<Path, Optional<Imagen>> leidas, List<String> avisos) {
        Optional<Path> fichero = reducida.apply(foto);
        if (fichero.isEmpty()) {
            return Optional.empty();
        }
        return leidas.computeIfAbsent(fichero.get(), ruta -> {
            try {
                return Optional.of(Imagen.de(ruta));
            } catch (IOException e) {
                avisos.add("La foto " + foto.nombreOriginal() + " de " + foto.modelo()
                        + " no se ha podido meter en el Word: no sale");
                return Optional.empty();
            }
        });
    }

    private static String sanear(String nombre) {
        return CARACTERES_INSEGUROS.matcher(nombre).replaceAll("_").replaceAll("\\s+", " ");
    }
}
```

- [ ] **Step 4: comprobar que pasan este y los de la tarea 1**

Run: `mvn -q test -Dtest="DocumentosCorteGeneracionTest,PedidoCorteRealTest"`
Expected: PASS.

- [ ] **Step 5: commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/corte src/test/java/com/puntotres/packinglist/service/corte/DocumentosCorteGeneracionTest.java
git commit -m "de la tabla de pieles salen las ordenes de corte y un word de fotos por referencia"
```

---

### Tarea 8: el flujo web

**Files:**
- Create: `web/CorteEnCurso.java`, `web/PielesForm.java`, `web/FilaPielesVista.java`, `web/DocumentosCorteController.java`
- Create: `templates/documentos-corte.html`, `templates/documentos-corte-pieles.html`, `templates/documentos-corte-resultados.html`
- Modify: `templates/menu.html` (tarjeta nueva), `static/estilo.css` (tabla de pieles), `application.yml` (subida de 1 GB)
- Test: `src/test/java/com/puntotres/packinglist/web/DocumentosCorteControllerTest.java`

**Interfaces:**
- Consumes: todo lo anterior; `ArchivoTemporadas.paraElEnvio(Long, String)`, `ArchivoTemporadas.porCliente()`, `ClientesProperties.getClientes()`, `MemoriaPieles`.
- Produces: rutas `GET /documentos-corte`, `POST /documentos-corte/cargar`, `GET /documentos-corte/pieles`, `POST /documentos-corte/pieles/combinacion`, `GET /documentos-corte/progreso`, `POST /documentos-corte/generar`, `GET /documentos-corte/resultados`, `GET /documentos-corte/descargar/{nombreFichero}`, `GET /documentos-corte/descargar-todo`, `GET /documentos-corte/nuevo`.

- [ ] **Step 1: test que falla**

<!-- fichero: src/test/java/com/puntotres/packinglist/web/DocumentosCorteControllerTest.java -->
```java
package com.puntotres.packinglist.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.testutil.FotosDePrueba;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

/**
 * El flujo web de los documentos del corte con los beans reales. Cada test
 * usa referencias propias: la memoria de pieles es la misma base de datos en
 * memoria para toda la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentosCorteControllerTest {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ArchivoTemporadas archivoTemporadas;

    private final MockHttpSession sesion = new MockHttpSession();

    @AfterEach
    void borrarLoDeLaSesion() throws Exception {
        mvc.perform(get("/documentos-corte/nuevo").session(sesion));
    }

    @Test
    void elMenuEnlazaLosDocumentosDelCorte() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(content().string(containsString("href=\"/documentos-corte\"")));
    }

    @Test
    void laEntradaOfreceAmiYApcYLosGenericosEnDesarrollo() throws Exception {
        mvc.perform(get("/documentos-corte"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ackermann — en desarrollo")))
                .andExpect(content().string(containsString("A.P.C.")))
                .andExpect(content().string(not(containsString("AMI — en desarrollo"))));
    }

    @Test
    void unClienteGenericoNoPasa() throws Exception {
        mvc.perform(cargar("ACKERMANN", "H26", pedidoAmi("ULL100"), zipFotos("ULL100")))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("en desarrollo")));
    }

    @Test
    void sinZipNoPasa() throws Exception {
        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("pedido", "pedido.xlsx", XLSX, pedidoAmi("ULL101")))
                        .param("cliente", "AMI").param("temporada", "H26").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("zip")));
    }

    @Test
    void sinTemporadaNiExcelNoPasa() throws Exception {
        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("fotos", "H26.zip", "application/zip", zipFotos("ULL102")))
                        .param("cliente", "AMI").session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("temporada guardada")));
    }

    @Test
    void cargarLlevaALaTablaDePieles() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL103"), zipFotos("ULL103")))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ULL103")))
                .andExpect(content().string(containsString("AL0103")))
                .andExpect(content().string(containsString("001 BLACK")))
                .andExpect(content().string(containsString("value=\"10\"")))
                .andExpect(content().string(containsString("718 VANILLA CREAM")))
                .andExpect(content().string(containsString("a.jpg")))
                .andExpect(content().string(containsString("sin fotos")))
                .andExpect(content().string(containsString("ULL999")))
                .andExpect(content().string(containsString("ULL745")));
    }

    @Test
    void anadirUnaCombinacionConservaLoTecleado() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL104"), zipFotos("ULL104")));

        mvc.perform(post("/documentos-corte/pieles/combinacion").session(sesion)
                        .param("filas[0].nombrePiel", "Box calf"))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("value=\"Box calf\"")))
                .andExpect(content().string(containsString("name=\"filas[0].combinaciones[0]\"")))
                .andExpect(content().string(containsString("Combinación 1")));
    }

    @Test
    void generarDejaLasDescargas() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL105"), zipFotos("ULL105")));

        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].nombrePiel", "Box calf")
                        .param("filas[0].bolsos[0]", "10")
                        .param("filas[0].bolsos[1]", "0")
                        .param("filas[0].fotoPrincipal", "1")
                        .param("filas[1].bolsos[0]", "5"))
                .andExpect(redirectedUrl("/documentos-corte/resultados"));

        mvc.perform(get("/documentos-corte/resultados").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ordenes de corte AMI H26.docx")))
                .andExpect(content().string(containsString("Fotos ULL105.AL0103.docx")));

        byte[] word = mvc.perform(get("/documentos-corte/descargar/Ordenes de corte AMI H26.docx")
                        .session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals('P', word[0]);
        assertEquals('K', word[1]);

        byte[] zip = mvc.perform(get("/documentos-corte/descargar-todo").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(List.of("Ordenes de corte AMI H26.docx", "Fotos ULL105.AL0103.docx"), entradas(zip));
    }

    @Test
    void unNumeroDeBolsosQueNoEsNumeroVuelveALaTablaSinGenerar() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL106"), zipFotos("ULL106")));

        mvc.perform(post("/documentos-corte/generar").session(sesion)
                        .param("filas[0].bolsos[0]", "diez"))
                .andExpect(redirectedUrl("/documentos-corte/pieles"))
                .andExpect(flash().attribute("errores", org.hamcrest.Matchers.hasItem(containsString("diez"))));
    }

    @Test
    void conTemporadaGuardadaNoHaceFaltaSubirElExcel() throws Exception {
        Long id = archivoTemporadas.guardar(null, "AMI", "H27", "pedido.xlsx", pedidoAmi("ULL107")).getId();

        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("fotos", "H27.zip", "application/zip", zipFotos("ULL107")))
                        .param("cliente", "AMI").param("temporadaGuardadaId", String.valueOf(id))
                        .session(sesion))
                .andExpect(redirectedUrl("/documentos-corte/pieles"));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("H27")))
                .andExpect(content().string(containsString("ULL107")));
    }

    @Test
    void unaTemporadaGuardadaDeOtroClienteNoSeUsa() throws Exception {
        Long id = archivoTemporadas.guardar(null, "APC", "E28", "apc.xlsx", pedidoAmi("ULL108")).getId();

        mvc.perform(multipart("/documentos-corte/cargar")
                        .file(new MockMultipartFile("fotos", "H26.zip", "application/zip", zipFotos("ULL108")))
                        .param("cliente", "AMI").param("temporadaGuardadaId", String.valueOf(id))
                        .session(sesion))
                .andExpect(redirectedUrl("/documentos-corte"))
                .andExpect(flash().attribute("error", containsString("no es de este cliente")));
    }

    @Test
    void losNombresDePielSeRecuerdanParaLaSiguienteVez() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL109", "AL0909"), zipFotos("ULL109")));
        mvc.perform(post("/documentos-corte/generar").session(sesion)
                .param("filas[0].nombrePiel", "Vachette recordada")
                .param("filas[0].forro", "Cabretilla recordada"));
        mvc.perform(get("/documentos-corte/nuevo").session(sesion));

        mvc.perform(cargar("AMI", "H27", pedidoAmi("ULL109", "AL0909"), zipFotos("ULL109")));

        mvc.perform(get("/documentos-corte/pieles").session(sesion))
                .andExpect(content().string(containsString("value=\"Vachette recordada\"")))
                .andExpect(content().string(containsString("value=\"Cabretilla recordada\"")));
    }

    @Test
    void elProgresoDeLasFotosSeConsultaSinRecargar() throws Exception {
        mvc.perform(cargar("AMI", "H26", pedidoAmi("ULL110"), zipFotos("ULL110")));

        mvc.perform(get("/documentos-corte/progreso").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"total\":2")));
    }

    private MockMultipartHttpServletRequestBuilder cargar(String cliente, String temporada,
                                                         byte[] pedido, byte[] fotos) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/documentos-corte/cargar")
                .file(new MockMultipartFile("pedido", "pedido.xlsx", XLSX, pedido))
                .file(new MockMultipartFile("fotos", temporada + ".zip", "application/zip", fotos))
                .param("cliente", cliente)
                .param("temporada", temporada)
                .session(sesion);
    }

    /** Un modelo con dos colores y ULL745, que no trae carpeta de fotos. */
    private static byte[] pedidoAmi(String modelo) {
        return pedidoAmi(modelo, "AL0103");
    }

    private static byte[] pedidoAmi(String modelo, String piel) {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", modelo + "." + piel, "001", "BLACK", "U", "07704 CH", null, null, 4),
                new Fila("SPAIN", modelo + "." + piel, "001", "BLACK", "U", 7665, null, null, 6),
                new Fila("SPAIN", modelo + "." + piel, "718", "VANILLA CREAM", "U", 7665, null, null, 3),
                new Fila("SPAIN", "ULL745." + piel, "001", "BLACK", "U", 7665, null, null, 5));
    }

    /** Dos fotos del modelo y una carpeta que no está en el pedido. */
    private static byte[] zipFotos(String modelo) throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("H26/" + modelo + "/b.jpg", FotosDePrueba.jpeg(300, 200, Color.BLUE));
        entradas.put("H26/" + modelo + "/a.jpg", FotosDePrueba.jpeg(200, 300, Color.RED));
        entradas.put("H26/ULL999/x.jpg", FotosDePrueba.jpeg(10, 10, Color.GRAY));
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(entrada.getKey()));
                zip.write(entrada.getValue());
                zip.closeEntry();
            }
        }
        return salida.toByteArray();
    }

    private static List<String> entradas(byte[] zip) throws IOException {
        List<String> nombres = new ArrayList<>();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry actual;
            while ((actual = entrada.getNextEntry()) != null) {
                nombres.add(actual.getName());
            }
        }
        assertTrue(!nombres.isEmpty());
        return nombres;
    }
}
```

- [ ] **Step 2: comprobar que falla**

Run: `mvn -q test -Dtest=DocumentosCorteControllerTest`
Expected: FAIL (404 en las rutas; no existe el controlador).

- [ ] **Step 3: estado de sesión, formulario y vista**

<!-- fichero: src/main/java/com/puntotres/packinglist/web/CorteEnCurso.java -->
```java
package com.puntotres.packinglist.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.service.corte.ArticuloCorte;
import com.puntotres.packinglist.service.corte.ConversionFotos;
import com.puntotres.packinglist.service.corte.FilaCorte;
import com.puntotres.packinglist.service.corte.FotosTemporada;
import com.puntotres.packinglist.service.corte.PedidoCorte;
import com.puntotres.packinglist.service.corte.ResultadoCorte;

import jakarta.annotation.PreDestroy;

/**
 * Estado de los documentos del corte entre sus tres pantallas.
 *
 * Independiente de los demás flujos, como EtiquetasArticuloEnCurso. Las
 * fotos y los Word viven en un DIRECTORIO TEMPORAL de la sesión y no en
 * memoria —una temporada son cientos de megas de fotos—; se borra al empezar
 * otra carga, con "empezar de nuevo" y al caducar la sesión.
 */
@Component
@SessionScope
public class CorteEnCurso {

    private String claveCliente;
    private String nombreCliente;
    private String temporada;
    private Path directorio;
    private FotosTemporada fotos;
    private ConversionFotos conversion;
    private final List<FilaCorte> filas = new ArrayList<>();
    private int columnasCombinacion;
    private final List<String> avisosCarga = new ArrayList<>();
    private ResultadoCorte resultado;

    public boolean tieneCarga() {
        return conversion != null && !filas.isEmpty();
    }

    public void cargar(String claveCliente, String nombreCliente, String temporada, Path directorio,
                       PedidoCorte pedido, FotosTemporada fotos, ConversionFotos conversion,
                       List<String> avisos) {
        this.claveCliente = claveCliente;
        this.nombreCliente = nombreCliente;
        this.temporada = temporada;
        this.directorio = directorio;
        this.fotos = fotos;
        this.conversion = conversion;
        filas.clear();
        for (ArticuloCorte articulo : pedido.articulos()) {
            filas.add(new FilaCorte(articulo));
        }
        avisosCarga.clear();
        avisosCarga.addAll(avisos);
        columnasCombinacion = 0;
        resultado = null;
    }

    /** Tantas columnas como la fila que más combinaciones recordadas traiga. */
    public void ajustarColumnasALasFilas() {
        int maximo = filas.stream().mapToInt(fila -> fila.getCombinaciones().size()).max().orElse(0);
        columnasCombinacion = Math.min(MemoriaPieles.MAXIMO_COMBINACIONES,
                Math.max(columnasCombinacion, maximo));
    }

    public void anadirColumnaCombinacion() {
        if (columnasCombinacion < MemoriaPieles.MAXIMO_COMBINACIONES) {
            columnasCombinacion++;
        }
    }

    public void reiniciar() {
        if (conversion != null) {
            conversion.cancelar();
        }
        borrar(directorio);
        claveCliente = null;
        nombreCliente = null;
        temporada = null;
        directorio = null;
        fotos = null;
        conversion = null;
        filas.clear();
        columnasCombinacion = 0;
        avisosCarga.clear();
        resultado = null;
    }

    @PreDestroy
    public void alCerrarLaSesion() {
        reiniciar();
    }

    /** Borra un directorio con todo lo que tenga. Lo que no se deje borrar se queda en temporales. */
    static void borrar(Path directorio) {
        if (directorio == null || !Files.exists(directorio)) {
            return;
        }
        try (Stream<Path> todo = Files.walk(directorio)) {
            todo.sorted(Comparator.reverseOrder()).forEach(ruta -> {
                try {
                    Files.deleteIfExists(ruta);
                } catch (IOException e) {
                    // Un fichero abierto por otro proceso: lo limpiará el sistema.
                }
            });
        } catch (IOException e) {
            // Igual que arriba.
        }
    }

    public String getClaveCliente() { return claveCliente; }
    public String getNombreCliente() { return nombreCliente; }
    public String getTemporada() { return temporada; }
    public Path getDirectorio() { return directorio; }
    public FotosTemporada getFotos() { return fotos; }
    public ConversionFotos getConversion() { return conversion; }
    public List<FilaCorte> getFilas() { return filas; }
    public int getColumnasCombinacion() { return columnasCombinacion; }
    public List<String> getAvisosCarga() { return avisosCarga; }
    public ResultadoCorte getResultado() { return resultado; }
    public void setResultado(ResultadoCorte resultado) { this.resultado = resultado; }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/web/PielesForm.java -->
```java
package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que llega de la tabla de pieles: una fila por referencia, en el mismo
 * orden que CorteEnCurso.getFilas(). Los bolsos llegan como texto para poder
 * decir qué se tecleó mal en vez de perderlo en un error de conversión.
 */
public class PielesForm {

    private List<Fila> filas = new ArrayList<>();

    public List<Fila> getFilas() { return filas; }
    public void setFilas(List<Fila> filas) { this.filas = filas; }

    public static class Fila {

        private String nombrePiel;
        private String forro;
        private List<String> combinaciones = new ArrayList<>();
        private List<String> bolsos = new ArrayList<>();
        private Integer fotoPrincipal;

        public String getNombrePiel() { return nombrePiel; }
        public void setNombrePiel(String nombrePiel) { this.nombrePiel = nombrePiel; }
        public String getForro() { return forro; }
        public void setForro(String forro) { this.forro = forro; }
        public List<String> getCombinaciones() { return combinaciones; }
        public void setCombinaciones(List<String> combinaciones) { this.combinaciones = combinaciones; }
        public List<String> getBolsos() { return bolsos; }
        public void setBolsos(List<String> bolsos) { this.bolsos = bolsos; }
        public Integer getFotoPrincipal() { return fotoPrincipal; }
        public void setFotoPrincipal(Integer fotoPrincipal) { this.fotoPrincipal = fotoPrincipal; }
    }
}
```

<!-- fichero: src/main/java/com/puntotres/packinglist/web/FilaPielesVista.java -->
```java
package com.puntotres.packinglist.web;

import java.util.List;

import com.puntotres.packinglist.service.corte.ColorCorte;

/**
 * Una fila de la tabla de pieles lista para pintar: las combinaciones ya
 * vienen con una casilla por columna, y las fotos son los nombres de las de
 * su modelo, en el orden del desplegable.
 */
public record FilaPielesVista(int indice, String referencia, String modelo, String piel,
                              List<ColorCorte> colores, String nombrePiel, String forro,
                              List<String> combinaciones, List<String> fotos, int fotoPrincipal) {
}
```

- [ ] **Step 4: controlador**

<!-- fichero: src/main/java/com/puntotres/packinglist/web/DocumentosCorteController.java -->
```java
package com.puntotres.packinglist.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.persistence.ArchivoTemporadas;
import com.puntotres.packinglist.persistence.MemoriaPieles;
import com.puntotres.packinglist.persistence.ResumenTemporada;
import com.puntotres.packinglist.persistence.TemporadaGuardada;
import com.puntotres.packinglist.service.corte.ClienteCorte;
import com.puntotres.packinglist.service.corte.ConversionFotos;
import com.puntotres.packinglist.service.corte.ConversorFotos;
import com.puntotres.packinglist.service.corte.DocumentoCorte;
import com.puntotres.packinglist.service.corte.DocumentosCorteService;
import com.puntotres.packinglist.service.corte.FilaCorte;
import com.puntotres.packinglist.service.corte.FotoModelo;
import com.puntotres.packinglist.service.corte.FotosTemporada;
import com.puntotres.packinglist.service.corte.LectorZipFotos;
import com.puntotres.packinglist.service.corte.PedidoCorte;
import com.puntotres.packinglist.service.corte.ResultadoCorte;

/**
 * Documentos del Corte, en tres pantallas: entrada (cliente, temporada y zip
 * de fotos) → tabla de pieles → descargas.
 *
 * La conversión de las fotos arranca al cargar y trabaja en segundo plano
 * mientras se rellena la tabla (un HEIC de 24 MP tarda segundos); generar
 * espera a que acabe. Lo único que bloquea es lo que impide empezar: sin
 * pedido legible o sin zip legible no hay nada que hacer. Todo lo demás son
 * avisos.
 */
@Controller
public class DocumentosCorteController {

    private static final MediaType TIPO_DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final String PEDIDO_ILEGIBLE =
            "no tiene el formato esperado: revisa que sea el excel de pedido de ese cliente";

    /** Un cliente del desplegable: los que no tienen documentos del corte salen deshabilitados. */
    public record OpcionCliente(String nombre, boolean disponible) {
    }

    private final DocumentosCorteService servicio;
    private final ConversorFotos conversor;
    private final ClientesProperties clientesProperties;
    private final ArchivoTemporadas archivoTemporadas;
    private final MemoriaPieles memoria;
    private final CorteEnCurso enCurso;

    public DocumentosCorteController(DocumentosCorteService servicio, ConversorFotos conversor,
                                     ClientesProperties clientesProperties,
                                     ArchivoTemporadas archivoTemporadas, MemoriaPieles memoria,
                                     CorteEnCurso enCurso) {
        this.servicio = servicio;
        this.conversor = conversor;
        this.clientesProperties = clientesProperties;
        this.archivoTemporadas = archivoTemporadas;
        this.memoria = memoria;
        this.enCurso = enCurso;
    }

    // --- Paso 1: entrada ---

    @GetMapping("/documentos-corte")
    public String entrada(Model model) {
        model.addAttribute("clientes", opcionesDeCliente());
        model.addAttribute("temporadasJs", temporadasJs());
        return "documentos-corte";
    }

    @PostMapping("/documentos-corte/cargar")
    public String cargar(@RequestParam String cliente,
                         @RequestParam(required = false) Long temporadaGuardadaId,
                         @RequestParam(required = false) String temporada,
                         @RequestParam(required = false) MultipartFile pedido,
                         @RequestParam(required = false) MultipartFile fotos,
                         RedirectAttributes redirect) {
        ClienteCorte clienteCorte = servicio.clientePara(cliente).orElse(null);
        if (clienteCorte == null) {
            return error(redirect, "El cliente '" + cliente
                    + "' todavía no tiene documentos del corte: está en desarrollo");
        }
        boolean conExcel = pedido != null && !pedido.isEmpty();
        // Solo si es de este cliente: el desplegable se rellena en el navegador
        // y cambiar de cliente después de elegir dejaría enviada la de otro.
        Optional<TemporadaGuardada> guardada = conExcel ? Optional.empty()
                : archivoTemporadas.paraElEnvio(temporadaGuardadaId, clienteCorte.clave());
        if (!conExcel && guardada.isEmpty()) {
            return error(redirect, temporadaGuardadaId != null
                    ? "La temporada elegida ya no existe o no es de este cliente: vuelve a elegirla "
                            + "o sube el excel de pedido"
                    : "Elige una temporada guardada o sube el excel de pedido");
        }
        String nombreTemporada = temporada != null && !temporada.isBlank() ? temporada.trim()
                : guardada.map(TemporadaGuardada::getTemporada).orElse("");
        if (nombreTemporada.isBlank()) {
            return error(redirect, "Falta el nombre de la temporada");
        }
        if (fotos == null || fotos.isEmpty()) {
            return error(redirect, "Falta el zip con las fotos de la temporada");
        }

        PedidoCorte pedidoCorte;
        try {
            byte[] excel = conExcel ? pedido.getBytes() : guardada.get().getExcel();
            pedidoCorte = clienteCorte.leerPedido(excel);
        } catch (IllegalArgumentException e) {
            return error(redirect, "No se pudo leer el excel de pedido: "
                    + (e.getMessage() != null ? e.getMessage() : PEDIDO_ILEGIBLE));
        } catch (IOException | RuntimeException e) {
            return error(redirect, "No se pudo leer el excel de pedido: " + PEDIDO_ILEGIBLE);
        }
        if (pedidoCorte.articulos().isEmpty()) {
            return error(redirect, "El excel de pedido no trae ninguna referencia: no hay nada que cortar");
        }

        enCurso.reiniciar();
        Path directorio = null;
        try {
            directorio = Files.createTempDirectory("documentos-corte-");
            Path zip = directorio.resolve("temporada.zip");
            fotos.transferTo(zip);
            FotosTemporada leidas;
            try {
                leidas = new LectorZipFotos().leer(zip, pedidoCorte.modelos(), directorio);
            } finally {
                Files.deleteIfExists(zip);
            }
            ConversionFotos conversion = conversor.convertir(leidas.todas(), directorio);
            enCurso.cargar(clienteCorte.clave(), nombreDe(clienteCorte.clave()), nombreTemporada,
                    directorio, pedidoCorte, leidas, conversion, avisosDeCarga(pedidoCorte, leidas));
        } catch (IllegalArgumentException e) {
            CorteEnCurso.borrar(directorio);
            return error(redirect, "No se pudo usar el zip de fotos: " + e.getMessage());
        } catch (IOException e) {
            CorteEnCurso.borrar(directorio);
            return error(redirect, "No se pudo abrir el zip de fotos: ¿es un fichero .zip?");
        }

        for (FilaCorte fila : enCurso.getFilas()) {
            MemoriaPieles.Recordadas recordadas = memoria.buscar(clienteCorte.clave(),
                    fila.referencia().piel(), fila.referencia().referencia());
            fila.setNombrePiel(recordadas.nombrePiel());
            fila.setForro(recordadas.forro());
            fila.setCombinaciones(recordadas.combinaciones());
        }
        enCurso.ajustarColumnasALasFilas();
        return "redirect:/documentos-corte/pieles";
    }

    // --- Paso 2: pieles ---

    @GetMapping("/documentos-corte/pieles")
    public String pieles(Model model) {
        if (!enCurso.tieneCarga()) {
            return "redirect:/documentos-corte";
        }
        ConversionFotos conversion = enCurso.getConversion();
        model.addAttribute("cliente", enCurso.getNombreCliente());
        model.addAttribute("temporada", enCurso.getTemporada());
        model.addAttribute("filas", vistaDeFilas());
        model.addAttribute("columnas", IntStream.rangeClosed(1, enCurso.getColumnasCombinacion())
                .boxed().toList());
        model.addAttribute("puedeAnadirColumna",
                enCurso.getColumnasCombinacion() < MemoriaPieles.MAXIMO_COMBINACIONES);
        model.addAttribute("fotosTotal", conversion.total());
        model.addAttribute("fotosHechas", conversion.hechas());
        model.addAttribute("fotosTerminadas", conversion.terminada());
        model.addAttribute("avisos", enCurso.getAvisosCarga());
        return "documentos-corte-pieles";
    }

    @PostMapping("/documentos-corte/pieles/combinacion")
    public String anadirCombinacion(@ModelAttribute PielesForm form, RedirectAttributes redirect) {
        if (!enCurso.tieneCarga()) {
            return "redirect:/documentos-corte";
        }
        List<String> errores = aplicar(form);
        if (!errores.isEmpty()) {
            redirect.addFlashAttribute("errores", errores);
        }
        enCurso.anadirColumnaCombinacion();
        return "redirect:/documentos-corte/pieles";
    }

    @GetMapping("/documentos-corte/progreso")
    @ResponseBody
    public Map<String, Object> progreso() {
        ConversionFotos conversion = enCurso.getConversion();
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("hechas", conversion == null ? 0 : conversion.hechas());
        estado.put("total", conversion == null ? 0 : conversion.total());
        estado.put("terminada", conversion == null || conversion.terminada());
        return estado;
    }

    @PostMapping("/documentos-corte/generar")
    public String generar(@ModelAttribute PielesForm form, RedirectAttributes redirect) {
        if (!enCurso.tieneCarga()) {
            return "redirect:/documentos-corte";
        }
        List<String> errores = aplicar(form);
        if (!errores.isEmpty()) {
            // Generar con el número de antes sería dar por bueno algo que se
            // ha tecleado mal: se vuelve a la tabla a corregirlo.
            redirect.addFlashAttribute("errores", errores);
            return "redirect:/documentos-corte/pieles";
        }
        for (FilaCorte fila : enCurso.getFilas()) {
            memoria.recordar(enCurso.getClaveCliente(), fila.referencia().piel(),
                    fila.referencia().referencia(), fila.getNombrePiel(), fila.getForro(),
                    fila.getCombinaciones());
        }
        try {
            enCurso.getConversion().esperar();
            Path salida = enCurso.getDirectorio().resolve("documentos");
            CorteEnCurso.borrar(salida);
            ResultadoCorte resultado = servicio.generar(enCurso.getNombreCliente(),
                    enCurso.getTemporada(), enCurso.getFilas(), enCurso.getFotos(),
                    enCurso.getConversion()::reducida, salida);
            enCurso.setResultado(resultado);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            redirect.addFlashAttribute("error", "Se ha interrumpido la generación: vuelve a darle");
            return "redirect:/documentos-corte/pieles";
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error", "No se pudieron generar los documentos: "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return "redirect:/documentos-corte/pieles";
        }
        return "redirect:/documentos-corte/resultados";
    }

    // --- Paso 3: resultados ---

    @GetMapping("/documentos-corte/resultados")
    public String resultados(Model model) {
        if (enCurso.getResultado() == null) {
            return enCurso.tieneCarga() ? "redirect:/documentos-corte/pieles" : "redirect:/documentos-corte";
        }
        List<String> avisos = new ArrayList<>(enCurso.getAvisosCarga());
        avisos.addAll(enCurso.getConversion().avisos());
        avisos.addAll(enCurso.getResultado().avisos());
        model.addAttribute("cliente", enCurso.getNombreCliente());
        model.addAttribute("temporada", enCurso.getTemporada());
        model.addAttribute("documentos", enCurso.getResultado().documentos());
        model.addAttribute("avisos", avisos);
        return "documentos-corte-resultados";
    }

    @GetMapping("/documentos-corte/descargar/{nombreFichero}")
    public ResponseEntity<byte[]> descargar(@PathVariable String nombreFichero) throws IOException {
        if (enCurso.getResultado() == null) {
            return ResponseEntity.notFound().build();
        }
        Optional<DocumentoCorte> documento = enCurso.getResultado().documentos().stream()
                .filter(candidato -> candidato.nombreFichero().equals(nombreFichero))
                .findFirst();
        if (documento.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(TIPO_DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(documento.get().nombreFichero()).build().toString())
                .body(Files.readAllBytes(documento.get().fichero()));
    }

    @GetMapping("/documentos-corte/descargar-todo")
    public ResponseEntity<byte[]> descargarTodo() throws IOException {
        if (enCurso.getResultado() == null || enCurso.getResultado().documentos().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (DocumentoCorte documento : enCurso.getResultado().documentos()) {
                zip.putNextEntry(new ZipEntry(documento.nombreFichero()));
                zip.write(Files.readAllBytes(documento.fichero()));
                zip.closeEntry();
            }
        }
        String nombreZip = ("Documentos del corte " + enCurso.getNombreCliente() + " "
                + enCurso.getTemporada() + ".zip").replaceAll("[\\\\/:*?\"<>|]+", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(nombreZip).build().toString())
                .body(salida.toByteArray());
    }

    @GetMapping("/documentos-corte/nuevo")
    public String nuevo() {
        enCurso.reiniciar();
        return "redirect:/documentos-corte";
    }

    // --- Internos ---

    /**
     * Pasa lo tecleado a las filas de la sesión y devuelve los bolsos que no
     * se entienden. Un bolso vacío es 0 (esa orden no sale); uno ilegible deja
     * el número que había y se dice.
     */
    private List<String> aplicar(PielesForm form) {
        List<String> errores = new ArrayList<>();
        List<FilaCorte> filas = enCurso.getFilas();
        for (int i = 0; i < Math.min(filas.size(), form.getFilas().size()); i++) {
            PielesForm.Fila datos = form.getFilas().get(i);
            if (datos == null) {
                continue;
            }
            FilaCorte fila = filas.get(i);
            fila.setNombrePiel(datos.getNombrePiel());
            fila.setForro(datos.getForro());
            fila.setCombinaciones(datos.getCombinaciones());
            if (datos.getFotoPrincipal() != null) {
                fila.setFotoPrincipal(datos.getFotoPrincipal());
            }
            List<String> bolsos = datos.getBolsos();
            for (int color = 0; color < Math.min(bolsos.size(), fila.colores().size()); color++) {
                String valor = bolsos.get(color) == null ? "" : bolsos.get(color).trim();
                try {
                    int numero = valor.isEmpty() ? 0 : Integer.parseInt(valor);
                    if (numero < 0) {
                        throw new NumberFormatException();
                    }
                    fila.setBolsos(color, numero);
                } catch (NumberFormatException e) {
                    errores.add("Los bolsos '" + valor + "' de " + fila.referencia().referencia()
                            + " en " + fila.colores().get(color).color()
                            + " no son un número: se deja " + fila.colores().get(color).bolsos());
                }
            }
        }
        return errores;
    }

    private List<FilaPielesVista> vistaDeFilas() {
        List<FilaPielesVista> vista = new ArrayList<>();
        List<FilaCorte> filas = enCurso.getFilas();
        int columnas = enCurso.getColumnasCombinacion();
        for (int i = 0; i < filas.size(); i++) {
            FilaCorte fila = filas.get(i);
            List<String> combinaciones = new ArrayList<>(fila.getCombinaciones());
            while (combinaciones.size() < columnas) {
                combinaciones.add("");
            }
            List<String> fotos = enCurso.getFotos().de(fila.referencia().modelo()).stream()
                    .map(FotoModelo::nombreOriginal).toList();
            vista.add(new FilaPielesVista(i, fila.referencia().referencia(),
                    fila.referencia().modelo(), fila.referencia().piel(), fila.colores(),
                    fila.getNombrePiel(), fila.getForro(),
                    combinaciones.subList(0, Math.min(combinaciones.size(), columnas)), fotos,
                    Math.min(fila.getFotoPrincipal(), Math.max(0, fotos.size() - 1))));
        }
        return vista;
    }

    private static List<String> avisosDeCarga(PedidoCorte pedido, FotosTemporada fotos) {
        List<String> avisos = new ArrayList<>(pedido.avisos());
        avisos.addAll(fotos.avisos());
        List<String> sinFotos = fotos.modelosSinFotos(pedido.modelos());
        if (!sinFotos.isEmpty()) {
            avisos.add("Modelos del pedido sin carpeta de fotos (su orden de corte sale sin foto y "
                    + "no llevan Word de fotos): " + String.join(", ", sinFotos));
        }
        return avisos;
    }

    private Map<String, OpcionCliente> opcionesDeCliente() {
        Map<String, OpcionCliente> opciones = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> opciones.put(clave,
                new OpcionCliente(nombreDe(config, clave), servicio.clientePara(clave).isPresent())));
        return opciones;
    }

    private String nombreDe(String clave) {
        return clientesProperties.clientePara(clave)
                .map(config -> nombreDe(config, clave))
                .orElse(clave);
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank() ? config.getNombre() : clave;
    }

    /** Las temporadas guardadas por cliente, para rehacer el desplegable al cambiar de cliente. */
    private Map<String, List<Map<String, String>>> temporadasJs() {
        Map<String, List<Map<String, String>>> porCliente = new LinkedHashMap<>();
        archivoTemporadas.porCliente().forEach((cliente, temporadas) -> {
            List<Map<String, String>> lista = new ArrayList<>();
            for (ResumenTemporada temporada : temporadas) {
                Map<String, String> datos = new LinkedHashMap<>();
                datos.put("id", String.valueOf(temporada.id()));
                datos.put("temporada", temporada.temporada());
                datos.put("fichero", temporada.nombreFichero());
                lista.add(datos);
            }
            porCliente.put(cliente, lista);
        });
        return porCliente;
    }

    private static String error(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("error", mensaje);
        return "redirect:/documentos-corte";
    }
}
```

Nota sobre el nombre del cliente en los documentos: se usa el `nombre` del catálogo (`AMI`, `A.P.C.`), que es como lo conoce el taller.

- [ ] **Step 5: plantillas**

<!-- fichero: src/main/resources/templates/documentos-corte.html -->
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragmentos :: head('Documentos del Corte')}"></head>
<body>
<header th:replace="~{fragmentos :: cabecera('Documentos del Corte')}"></header>
<main>
    <div class="alerta error" th:if="${error}" th:text="${error}"></div>

    <form class="tarjeta" id="formCorte" th:action="@{/documentos-corte/cargar}" method="post"
          enctype="multipart/form-data">
        <h3 class="titulo-seccion">✂️ Documentos del Corte</h3>
        <p class="ayuda">
            Del pedido de la temporada sale una orden de corte por referencia, piel y color, y de las
            fotos de cada modelo un Word de fotos por referencia. Los nombres de las pieles se ponen en
            la pantalla siguiente.
        </p>

        <label for="cliente">Cliente</label>
        <select id="cliente" name="cliente" required>
            <option th:each="entrada : ${clientes}"
                    th:value="${entrada.key}"
                    th:text="${entrada.value.disponible ? entrada.value.nombre
                              : entrada.value.nombre + ' — en desarrollo'}"
                    th:disabled="${!entrada.value.disponible}"></option>
        </select>

        <label for="temporadaGuardada" class="etiqueta-con-accion">
            <span>Temporada guardada</span>
            <a class="lapiz" th:href="@{/temporadas}"
               title="Añadir, editar o borrar temporadas guardadas"
               aria-label="Editar las temporadas guardadas">&#9998;</a>
        </label>
        <select id="temporadaGuardada" name="temporadaGuardadaId"></select>
        <p class="ayuda" id="ayudaTemporadaGuardada"></p>

        <!-- Con una temporada guardada elegida, su nombre y su excel ya están: estos dos
             campos se esconden para que no haya dos "Temporada" a la vez. -->
        <div id="bloqueSinGuardada">
            <label for="temporada">Temporada</label>
            <input type="text" id="temporada" name="temporada" placeholder="H26">
            <label for="pedido">Excel de pedido del cliente</label>
            <input type="file" id="pedido" name="pedido" accept=".xlsx">
        </div>

        <label for="fotos">Carpeta de fotos de la temporada (zip)</label>
        <input type="file" id="fotos" name="fotos" accept=".zip" required>
        <p class="ayuda">
            Una subcarpeta por modelo, con el nombre de la referencia del bolso (ULL712, F67008), y
            dentro sus fotos en HEIC, JPG o JPEG. La foto vale para todas las pieles del modelo.
        </p>

        <button type="submit" id="botonCargar">Cargar</button>
        <a class="boton secundario" th:href="@{/menu}">Volver</a>
    </form>
    <p id="avisoSubiendo" class="aviso-procesando" style="display: none">
        Subiendo el zip... con muchas fotos tarda un rato; no cierres la página.
    </p>

    <script th:inline="javascript">
        /*<![CDATA[*/
        const TEMPORADAS = /*[[${temporadasJs}]]*/ {};
        /*]]>*/
        (function () {
            const cliente = document.getElementById('cliente');
            const guardada = document.getElementById('temporadaGuardada');
            const ayuda = document.getElementById('ayudaTemporadaGuardada');
            const bloqueSinGuardada = document.getElementById('bloqueSinGuardada');

            function temporadasDelCliente() {
                return TEMPORADAS[cliente.value] || [];
            }

            // Se rehace al cambiar de cliente: una temporada elegida de otro
            // cliente se enviaría igual aunque ya no se vea.
            function rellenar() {
                guardada.innerHTML = '';
                const suelta = document.createElement('option');
                suelta.value = '';
                suelta.textContent = '-- otra temporada: subir el excel --';
                guardada.appendChild(suelta);
                temporadasDelCliente().forEach(function (temporada) {
                    const opcion = document.createElement('option');
                    opcion.value = temporada.id;
                    opcion.textContent = temporada.temporada;
                    guardada.appendChild(opcion);
                });
                aplicar();
            }

            function aplicar() {
                const elegida = temporadasDelCliente().filter(function (temporada) {
                    return String(temporada.id) === guardada.value;
                })[0];
                bloqueSinGuardada.style.display = elegida ? 'none' : '';
                ayuda.textContent = elegida ? 'Excel de pedido guardado: ' + elegida.fichero : '';
                if (elegida) {
                    document.getElementById('temporada').value = '';
                    document.getElementById('pedido').value = '';
                }
            }

            cliente.addEventListener('change', rellenar);
            guardada.addEventListener('change', aplicar);
            document.getElementById('formCorte').addEventListener('submit', function () {
                document.getElementById('avisoSubiendo').style.display = '';
            });
            rellenar();
        })();
    </script>
</main>
</body>
</html>
```

<!-- fichero: src/main/resources/templates/documentos-corte-pieles.html -->
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragmentos :: head('Pieles · Documentos del Corte')}"></head>
<body>
<header th:replace="~{fragmentos :: cabecera('Documentos del Corte')}"></header>
<main class="ancho-completo">
    <p class="resumen-envio">
        Cliente <strong th:text="${cliente}"></strong> ·
        Temporada <strong th:text="${temporada}"></strong> ·
        <span th:text="${#lists.size(filas)}"></span> referencias
    </p>

    <p class="aviso-procesando" id="progresoFotos"
       th:attr="data-terminada=${fotosTerminadas}">
        <span id="textoProgreso"
              th:text="${fotosTerminadas} ? |Fotos preparadas: ${fotosTotal}|
                       : |Preparando fotos: ${fotosHechas} de ${fotosTotal}|"></span>
    </p>

    <div class="alerta error" th:if="${error}" th:text="${error}"></div>
    <div class="alerta error" th:each="mensaje : ${errores}" th:text="${mensaje}"></div>

    <details class="avisos-plegables" th:if="${!#lists.isEmpty(avisos)}">
        <summary th:text="|${#lists.size(avisos)} aviso(s)|"></summary>
        <div class="alerta" th:each="aviso : ${avisos}" th:text="${aviso}"></div>
    </details>

    <form class="tarjeta" id="formPieles" method="post" th:action="@{/documentos-corte/generar}">
        <p class="ayuda">
            Los nombres valen para todos los colores de la referencia. Una combinación o un forro en
            blanco es que no lleva. Un color con 0 bolsos no saca orden de corte.
        </p>
        <div class="tabla-desplazable">
            <table class="tabla-pieles">
                <thead>
                <tr>
                    <th>Modelo</th>
                    <th>Piel</th>
                    <th>Bolsos por color</th>
                    <th>Nombre de la piel</th>
                    <th>Forro de piel</th>
                    <th th:each="numero : ${columnas}" th:text="|Combinación ${numero}|"></th>
                    <th>Foto principal</th>
                </tr>
                </thead>
                <tbody>
                <tr th:each="fila : ${filas}">
                    <td class="c-modelo" th:text="${fila.modelo}"></td>
                    <td class="c-piel" th:text="${fila.piel}"></td>
                    <td class="c-bolsos">
                        <div class="color-bolsos" th:each="color, estado : ${fila.colores}">
                            <label th:for="|bolsos-${fila.indice}-${estado.index}|"
                                   th:text="${color.color}"></label>
                            <input type="number" min="0"
                                   th:id="|bolsos-${fila.indice}-${estado.index}|"
                                   th:name="|filas[${fila.indice}].bolsos[${estado.index}]|"
                                   th:value="${color.bolsos}">
                        </div>
                    </td>
                    <td>
                        <input type="text" class="c-nombre-piel"
                               th:name="|filas[${fila.indice}].nombrePiel|"
                               th:value="${fila.nombrePiel}" th:attr="data-piel=${fila.piel}">
                    </td>
                    <td>
                        <input type="text" th:name="|filas[${fila.indice}].forro|"
                               th:value="${fila.forro}">
                    </td>
                    <td th:each="combinacion, estado : ${fila.combinaciones}">
                        <input type="text"
                               th:name="|filas[${fila.indice}].combinaciones[${estado.index}]|"
                               th:value="${combinacion}">
                    </td>
                    <td>
                        <select th:if="${!#lists.isEmpty(fila.fotos)}"
                                th:name="|filas[${fila.indice}].fotoPrincipal|">
                            <option th:each="foto, estado : ${fila.fotos}"
                                    th:value="${estado.index}" th:text="${foto}"
                                    th:selected="${estado.index == fila.fotoPrincipal}"></option>
                        </select>
                        <span class="sin-fotos" th:if="${#lists.isEmpty(fila.fotos)}">sin fotos</span>
                    </td>
                </tr>
                </tbody>
            </table>
        </div>

        <div class="acciones-corte">
            <button type="submit" class="secundario" th:if="${puedeAnadirColumna}"
                    th:formaction="@{/documentos-corte/pieles/combinacion}">+ combinación</button>
            <button type="submit" id="botonGenerar">Generar documentos</button>
            <a class="boton secundario" th:href="@{/documentos-corte/nuevo}">Empezar de nuevo</a>
        </div>
    </form>
    <p id="avisoGenerando" class="aviso-procesando" style="display: none">
        Generando... si las fotos aún se están preparando, espera a que acaben.
    </p>

    <script th:inline="javascript">
        /*<![CDATA[*/
        const URL_PROGRESO = /*[[@{/documentos-corte/progreso}]]*/ '/documentos-corte/progreso';
        /*]]>*/
        (function () {
            const progreso = document.getElementById('progresoFotos');
            const texto = document.getElementById('textoProgreso');

            function consultar() {
                fetch(URL_PROGRESO).then(function (respuesta) {
                    return respuesta.json();
                }).then(function (estado) {
                    if (estado.terminada) {
                        texto.textContent = 'Fotos preparadas: ' + estado.total;
                    } else {
                        texto.textContent = 'Preparando fotos: ' + estado.hechas + ' de ' + estado.total;
                        setTimeout(consultar, 2000);
                    }
                }).catch(function () {
                    setTimeout(consultar, 5000);
                });
            }
            if (progreso.dataset.terminada !== 'true') {
                setTimeout(consultar, 2000);
            }

            // Un nombre de piel vale para todas las filas con la misma ref de
            // piel: se copia a las que siguen vacías, nunca encima de otro.
            const nombres = document.querySelectorAll('input.c-nombre-piel');
            nombres.forEach(function (input) {
                input.addEventListener('change', function () {
                    const valor = input.value.trim();
                    if (!valor || !input.dataset.piel) {
                        return;
                    }
                    nombres.forEach(function (otro) {
                        if (otro !== input && otro.dataset.piel === input.dataset.piel
                                && !otro.value.trim()) {
                            otro.value = valor;
                        }
                    });
                });
            });

            document.getElementById('botonGenerar').addEventListener('click', function () {
                document.getElementById('avisoGenerando').style.display = '';
            });
        })();
    </script>
</main>
</body>
</html>
```

<!-- fichero: src/main/resources/templates/documentos-corte-resultados.html -->
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragmentos :: head('Documentos del Corte generados')}"></head>
<body>
<header th:replace="~{fragmentos :: cabecera('Documentos del Corte')}"></header>
<main>
    <p class="resumen-envio">
        Cliente <strong th:text="${cliente}"></strong> ·
        Temporada <strong th:text="${temporada}"></strong> ·
        <span th:text="${#lists.size(documentos)}"></span> documento(s)
    </p>

    <details class="avisos-plegables" th:if="${!#lists.isEmpty(avisos)}">
        <summary th:text="|${#lists.size(avisos)} aviso(s)|"></summary>
        <div class="alerta" th:each="aviso : ${avisos}" th:text="${aviso}"></div>
    </details>

    <section class="tarjeta seccion-salida">
        <h3 class="titulo-seccion">✂️ Documentos</h3>
        <table>
            <thead>
            <tr>
                <th>Contenido</th>
                <th>Fichero</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <tr th:each="documento : ${documentos}">
                <td th:text="${documento.descripcion}"></td>
                <td th:text="${documento.nombreFichero}"></td>
                <td>
                    <a th:href="@{/documentos-corte/descargar/{nombre}(nombre=${documento.nombreFichero})}">
                        Descargar</a>
                </td>
            </tr>
            </tbody>
        </table>

        <a class="boton" th:if="${!#lists.isEmpty(documentos)}"
           th:href="@{/documentos-corte/descargar-todo}">Descargar todo (ZIP)</a>
        <a class="boton secundario" th:href="@{/documentos-corte/pieles}">Volver a las pieles</a>
        <a class="boton secundario" th:href="@{/documentos-corte/nuevo}">Otra temporada</a>
        <a class="boton secundario" th:href="@{/menu}">Volver al menú</a>
    </section>
</main>
</body>
</html>
```

- [ ] **Step 6: menú, estilos y límite de subida**

En `menu.html`, detrás de la tarjeta de escandallos:

```html
        <section class="tarjeta menu-tarjeta">
            <h2 class="menu-titulo">
                <svg class="menu-icono" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round" aria-hidden="true">
                    <circle cx="6" cy="6" r="3"/>
                    <circle cx="6" cy="18" r="3"/>
                    <path d="M8.6 7.6 20 18M8.6 16.4 20 6"/>
                </svg>
                Documentos del Corte
            </h2>
            <p class="menu-entrada">Necesitas el pedido de la temporada y las fotos de cada modelo.</p>
            <ul class="menu-lista">
                <li>Una orden de corte por referencia, piel y color</li>
                <li>Un Word de fotos por referencia</li>
                <li>AMI y APC — genéricos en desarrollo</li>
            </ul>
            <a class="boton" th:href="@{/documentos-corte}">Empezar</a>
        </section>
```

Al final de `estilo.css`:

```css
/* --- Documentos del corte: tabla de pieles ---
   Puede ser ancha (hasta cuatro combinaciones), así que la pantalla se deja
   ensanchar y la tabla se desplaza dentro de su tarjeta en vez de desbordar. */
main.ancho-completo { max-width: 96rem; }
.tabla-desplazable { overflow-x: auto; }
.tabla-pieles td { vertical-align: top; }
.tabla-pieles td.c-modelo, .tabla-pieles td.c-piel { white-space: nowrap; font-weight: 600; }
.tabla-pieles .color-bolsos {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.5rem;
    white-space: nowrap;
}
.tabla-pieles .color-bolsos + .color-bolsos { margin-top: 0.3rem; }
.tabla-pieles .color-bolsos label { display: inline; margin: 0; font-weight: normal; }
.tabla-pieles td input[type="number"] { width: 5rem; }
.tabla-pieles td input[type="text"] { width: 100%; min-width: 9rem; }
.tabla-pieles select { max-width: 12rem; }
.tabla-pieles .sin-fotos { color: var(--gris-fuerte); font-style: italic; }
.acciones-corte { display: flex; flex-wrap: wrap; gap: 0.6rem; margin-top: 1rem; }
```

En `application.yml`, el bloque de subida:

```yaml
spring:
  servlet:
    multipart:
      # Las fotos de packing lists del móvil superan de sobra el 1MB por
      # defecto de Spring, y el zip de fotos de una temporada (documentos del
      # corte, fotos de iPhone de 24 MP) pesa cientos de megas.
      max-file-size: 1GB
      max-request-size: 1GB
```

- [ ] **Step 7: comprobar que pasa**

Run: `mvn -q test -Dtest="DocumentosCorteControllerTest,RutasTest"`
Expected: PASS.

- [ ] **Step 8: commit**

```bash
git add src/main/java/com/puntotres/packinglist/web src/main/resources/templates src/main/resources/static/estilo.css src/main/resources/application.yml src/test/java/com/puntotres/packinglist/web/DocumentosCorteControllerTest.java
git commit -m "seccion documentos del corte en el menu: entrada, tabla de pieles y descargas"
```

---

### Tarea 9: documentación y verificación final

**Files:**
- Modify: `CLAUDE.md` (sección "Documentos del Corte" y comando de los Word de ejemplo), `src/test/resources/ejemplos/README.md` (procedencia del HEIC de prueba), spec (estado y la regla de la fila sin bolsos)

- [ ] **Step 1:** añadir a CLAUDE.md, en "Comandos", que `OrdenCorteDocBuilderTest` y `FotosCorteDocBuilderTest` dejan `target/Ordenes de corte ejemplo.docx` y `target/Fotos ejemplo.docx`; y una sección **Documentos del Corte** con lo que no se deduce del código: partición de referencias por cliente, foto del modelo, alcance (órdenes = todo el pedido; Word de fotos = referencias con fotos y con bolsos), las dos claves de la memoria, los dos caminos de HEIC con sus medidas y el repositorio de Aspose, el directorio temporal de sesión, y que el HEIC de prueba viene del repo de Openize.
- [ ] **Step 2:** en `src/test/resources/ejemplos/README.md`, una línea para `corte/gimp_rgb_420_with_alpha.heic`.
- [ ] **Step 3:** `mvn test` completo. Expected: toda la suite en verde.
- [ ] **Step 4:** comprobar que existen `target/Ordenes de corte ejemplo.docx` y `target/Fotos ejemplo.docx`.
- [ ] **Step 5:** commit `documentacion de los documentos del corte`.
