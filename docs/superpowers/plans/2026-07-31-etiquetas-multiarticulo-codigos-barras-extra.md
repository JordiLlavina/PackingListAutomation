# Etiquetas con varios artículos por caja + hoja "CODIGOS BARRAS EXTRA" — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que la etiqueta de caja muestre los N artículos de una caja física en vez de solo el primero, y que los códigos de barras que no caben en la etiqueta se impriman en una hoja `CODIGOS BARRAS EXTRA` del mismo libro.

**Architecture:** Tres piezas nuevas en la capa común `service/etiquetas/` (`ArticulosDeCaja` agrupa las líneas de una `CajaFisica` en artículos, `AjusteFuente` encoge la fuente de las celdas concatenadas, `HojaCodigosBarrasExtra` maqueta la hoja nueva) y los generadores de AMI y APC pasando a trabajar contra esa lista de artículos en vez de contra `CajaFisica.lider()`.

**Tech Stack:** Java 17, Spring Boot 3.5, Apache POI (XSSF), JUnit 5.

**Spec:** [docs/superpowers/specs/2026-07-31-etiquetas-multiarticulo-codigos-barras-extra-design.md](../specs/2026-07-31-etiquetas-multiarticulo-codigos-barras-extra-design.md)

## Global Constraints

- Idioma: **español** en javadoc, comentarios, avisos y nombres de método; nombres de clase en el estilo mixto que ya usa el paquete (`CajaFisica`, `HojaEan`, `AmiEtiquetasGenerador`).
- **Nunca fallar en silencio, nunca bloquear por datos que un humano puede resolver**: todo lo que falte produce un aviso en `ResultadoEtiquetas` y el excel se genera igual, con la celda o la imagen en blanco. Nada de excepciones nuevas.
- **No editar** `src/main/resources/client-labels/ami-etiquetas-template.xlsx` ni ninguna otra plantilla.
- **Un peso por caja física, en su línea líder**: no tocar nada de pesos en este trabajo.
- Los tests se instancian con `new` (JUnit 5 puro). Los de builder **reabren el `.xlsx` generado con POI y comprueban celdas reales**.
- Comando de la suite: `mvn test`. Una sola clase: `mvn test -Dtest=NombreTest`.
- Ejecutar `mvn test` completo antes de cada commit de tarea; ningún test existente puede romperse.
- Los cinturones **no cambian de aspecto**: cualquier cambio en `SIZE`/`QUANTITY` de una caja de cinturones es un fallo.
- Separador de concatenación: exactamente `" / "` (espacio, barra, espacio).
- Nombre de la hoja nueva: exactamente `CODIGOS BARRAS EXTRA`.

---

## Estructura de ficheros

**Crear:**

| Fichero | Responsabilidad |
|---|---|
| `src/main/java/com/puntotres/packinglist/service/etiquetas/ArticuloEtiqueta.java` | Record: un artículo de una caja (referencia, color, talla, cantidad). |
| `.../service/etiquetas/ArticulosDeCaja.java` | Agrupa las líneas de una `CajaFisica` en artículos y concatena campos con `" / "`. Sin POI. |
| `.../service/etiquetas/AjusteFuente.java` | Calcula el tamaño de fuente que hace caber un texto en su columna y aplica el estilo clonado. |
| `.../service/etiquetas/FilaCodigoBarrasExtra.java` | Record: una fila de la hoja extra. |
| `.../service/etiquetas/HojaCodigosBarrasExtra.java` | Maqueta y escribe la hoja `CODIGOS BARRAS EXTRA` en un libro dado. |
| `src/test/java/com/puntotres/packinglist/service/etiquetas/ArticulosDeCajaTest.java` | Tests puros de agrupación. |
| `.../service/etiquetas/AjusteFuenteTest.java` | Tests puros del cálculo + un test de estilo con POI. |
| `.../service/etiquetas/HojaCodigosBarrasExtraTest.java` | Reabre el `.xlsx` y comprueba celdas e imágenes. |

**Modificar:**

| Fichero | Cambio |
|---|---|
| `.../service/etiquetas/AmiEtiquetasGenerador.java` | Búsqueda en el pedido por artículo, concatenación en bolsos, filas extra y avisos nuevos. |
| `.../service/etiquetas/AmiEtiquetasExcelBuilder.java` | Sobrecarga de `generar` con las filas extra, `AjusteFuente` en las celdas concatenadas. |
| `.../service/etiquetas/ApcEtiquetasGenerador.java` | Concatenación en bolsos. |
| `.../service/etiquetas/ApcEtiquetasExcelBuilder.java` | `AjusteFuente` en las celdas concatenadas. |
| `src/test/java/.../etiquetas/AmiEtiquetasGeneradorTest.java` | Tests nuevos + fila nueva en el fixture del pedido. |
| `src/test/java/.../etiquetas/AmiEtiquetasExcelBuilderTest.java` | Tests de la hoja extra. |
| `src/test/java/.../etiquetas/ApcEtiquetasGeneradorTest.java` | Test de concatenación. |
| `CLAUDE.md` | Documentar artículos por caja y la hoja extra. |

---

### Task 1: `ArticulosDeCaja` — de líneas a artículos

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/ArticuloEtiqueta.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/ArticulosDeCaja.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/ArticulosDeCajaTest.java`

**Interfaces:**
- Consumes: `com.puntotres.packinglist.model.CajaFisica` (`lineas()`, `lider()`), `CajaData` (`getReferencia()`, `getCodigoColor()`, `getTalla()`, `getCantidad()`).
- Produces:
  - `public record ArticuloEtiqueta(String referencia, String codigoColor, String talla, int cantidad)` — `talla` es `null` en bolsos.
  - `public static List<ArticuloEtiqueta> ArticulosDeCaja.de(CajaFisica caja, boolean agruparPorTalla)`
  - `public static String ArticulosDeCaja.unir(List<ArticuloEtiqueta> articulos, Function<ArticuloEtiqueta, String> campo)`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/puntotres/packinglist/service/etiquetas/ArticulosDeCajaTest.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.CajaFisica;

class ArticulosDeCajaTest {

    private static CajaData linea(String referencia, String color, String talla, int cantidad) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(1);
        caja.setReferencia(referencia);
        caja.setCodigoColor(color);
        caja.setTalla(talla);
        caja.setCantidad(cantidad);
        return caja;
    }

    private static CajaFisica caja(CajaData... lineas) {
        return CajaFisica.de(List.of(lineas));
    }

    @Test
    void unaCajaDeUnSoloArticuloDaUnArticulo() {
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 50)), false);

        assertEquals(1, articulos.size());
        assertEquals("ULL163.AL0052", articulos.get(0).referencia());
        assertEquals("221", articulos.get(0).codigoColor());
        assertEquals(null, articulos.get(0).talla());
        assertEquals(50, articulos.get(0).cantidad());
    }

    @Test
    void enBolsosLasLineasDelMismoArticuloSeFundenSumandoCantidades() {
        // Sin agrupar por talla, dos líneas de la misma referencia y color son
        // un solo artículo: es el comportamiento de hoy (QUANTITY sumada).
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 30),
                     linea("ULL163.AL0052", "221", null, 20)), false);

        assertEquals(1, articulos.size());
        assertEquals(50, articulos.get(0).cantidad());
    }

    @Test
    void enBolsosCadaReferenciaOColorEsUnArticuloYSeConservaElOrdenDeLlegada() {
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 3),
                     linea("ULL753.AL0168", "001", null, 5),
                     linea("ULL163.AL0052", "999", null, 2)), false);

        assertEquals(List.of("ULL163.AL0052", "ULL753.AL0168", "ULL163.AL0052"),
                articulos.stream().map(ArticuloEtiqueta::referencia).toList());
        assertEquals(List.of("221", "001", "999"),
                articulos.stream().map(ArticuloEtiqueta::codigoColor).toList());
    }

    @Test
    void enBolsosLaTallaNoParteElArticulo() {
        // Un bolso no tiene talla; si alguna línea la trajera, se ignora.
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", "U", 3),
                     linea("ULL163.AL0052", "221", null, 5)), false);

        assertEquals(1, articulos.size());
        assertEquals(8, articulos.get(0).cantidad());
    }

    @Test
    void enCinturonesCadaTallaEsUnArticuloEnOrdenDeLlegada() {
        // Cada talla tiene su propio EAN-13 en el pedido de AMI: son
        // artículos distintos aunque compartan referencia y color.
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("UBL029.AL0216", "001", "95", 33),
                     linea("UBL029.AL0216", "001", "85", 4),
                     linea("UBL029.AL0216", "001", "105", 3)), true);

        assertEquals(List.of("95", "85", "105"),
                articulos.stream().map(ArticuloEtiqueta::talla).toList());
        assertEquals(List.of(33, 4, 3),
                articulos.stream().map(ArticuloEtiqueta::cantidad).toList());
    }

    @Test
    void enCinturonesDosLineasDeLaMismaTallaSeFunden() {
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("UBL029.AL0216", "001", "85", 4),
                     linea("UBL029.AL0216", "001", "85", 6)), true);

        assertEquals(1, articulos.size());
        assertEquals(10, articulos.get(0).cantidad());
    }

    @Test
    void unirConcatenaConBarrasEnOrden() {
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 3),
                     linea("ULL753.AL0168", "001", null, 5)), false);

        assertEquals("ULL163.AL0052 / ULL753.AL0168",
                ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::referencia));
        assertEquals("3 / 5",
                ArticulosDeCaja.unir(articulos, a -> String.valueOf(a.cantidad())));
    }

    @Test
    void unirRepiteLosValoresIgualesEnVezDeDeduplicar() {
        // Dos artículos distintos con el mismo color code lo repiten: la
        // etiqueta se lee en paralelo, columna a columna.
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 3),
                     linea("ULL753.AL0168", "221", null, 5)), false);

        assertEquals("221 / 221",
                ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::codigoColor));
    }

    @Test
    void unirDeUnSoloArticuloNoAnadeSeparador() {
        List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(
                caja(linea("ULL163.AL0052", "221", null, 50)), false);

        assertEquals("ULL163.AL0052",
                ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::referencia));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ArticulosDeCajaTest`
Expected: fallo de compilación — `ArticulosDeCaja` y `ArticuloEtiqueta` no existen.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/puntotres/packinglist/service/etiquetas/ArticuloEtiqueta.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

/**
 * Un artículo dentro de una caja física: lo que la etiqueta tiene que saber
 * decir de él. Una caja puede llevar varios.
 *
 * talla es null en los bolsos (van como talla única "U" en el excel de
 * pedido) y la talla real en los cinturones, donde cada talla es un SKU
 * distinto con su propio EAN-13.
 */
public record ArticuloEtiqueta(String referencia, String codigoColor, String talla,
                               int cantidad) {
}
```

`src/main/java/com/puntotres/packinglist/service/etiquetas/ArticulosDeCaja.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.CajaFisica;

/**
 * Traduce las líneas del packing list de una caja física a la lista de
 * ARTÍCULOS que hay dentro, que es la unidad con la que trabajan las
 * etiquetas: un artículo = una referencia identificable con sus propios
 * códigos de barras.
 *
 * Hasta ahora los generadores colapsaban la caja a su línea líder y el resto
 * de artículos desaparecía de la etiqueta. Esto lo pone en un solo sitio para
 * que lo compartan todos los clientes.
 *
 * El criterio de qué parte un artículo lo decide quien llama, porque no es
 * igual en todos los clientes:
 * <ul>
 * <li><b>bolsos</b> (agruparPorTalla = false): referencia + color.</li>
 * <li><b>cinturones</b> (agruparPorTalla = true): referencia + color + talla,
 * porque cada talla tiene su propio EAN-13 en el excel de pedido.</li>
 * </ul>
 *
 * Con una sola línea, o con varias líneas del mismo artículo, sale un único
 * artículo: el comportamiento anterior es el caso particular de N=1.
 */
public final class ArticulosDeCaja {

    private static final String SEPARADOR = " / ";

    private ArticulosDeCaja() {
    }

    /** Los artículos de la caja, en orden de primera aparición en el packing list. */
    public static List<ArticuloEtiqueta> de(CajaFisica caja, boolean agruparPorTalla) {
        Map<String, ArticuloEtiqueta> porClave = new LinkedHashMap<>();
        for (CajaData linea : caja.lineas()) {
            String talla = agruparPorTalla ? linea.getTalla() : null;
            String clave = linea.getReferencia() + "|" + linea.getCodigoColor() + "|" + talla;
            porClave.merge(clave,
                    new ArticuloEtiqueta(linea.getReferencia(), linea.getCodigoColor(),
                            talla, linea.getCantidad()),
                    (previo, nuevo) -> new ArticuloEtiqueta(previo.referencia(),
                            previo.codigoColor(), previo.talla(),
                            previo.cantidad() + nuevo.cantidad()));
        }
        return List.copyOf(porClave.values());
    }

    /**
     * Un campo de todos los artículos concatenado con " / ", en el mismo
     * orden. Los valores repetidos NO se deduplican: la etiqueta se lee en
     * paralelo, un artículo por posición en cada campo.
     */
    public static String unir(List<ArticuloEtiqueta> articulos,
                              Function<ArticuloEtiqueta, String> campo) {
        return articulos.stream().map(campo).collect(Collectors.joining(SEPARADOR));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ArticulosDeCajaTest`
Expected: PASS (9 tests).

- [ ] **Step 5: Run the whole suite**

Run: `mvn test`
Expected: PASS. Nada usa todavía las clases nuevas, así que no puede romperse nada.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/ArticuloEtiqueta.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/ArticulosDeCaja.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/ArticulosDeCajaTest.java
git commit -m "las lineas de una caja se agrupan en articulos de etiqueta"
```

---

### Task 2: `AjusteFuente` — encoger el texto que no cabe

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/AjusteFuente.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AjusteFuenteTest.java`

**Interfaces:**
- Consumes: nada de tareas anteriores.
- Produces:
  - `public static final int AjusteFuente.TAMANO_MINIMO_PT = 8`
  - `public static short AjusteFuente.tamano(String texto, double anchoEnChars, short tamanoOriginalPt)` — función pura.
  - `public AjusteFuente(XSSFWorkbook libro)` y `public void ajustar(Cell celda)` — aplica el estilo; no hace nada si el texto cabe.

**Contexto que el implementador necesita:** `hoja.getColumnWidth(col)` de POI devuelve el ancho en 1/256 de carácter de la fuente **por defecto** de Excel (11 pt). De ahí que la capacidad a otro tamaño sea `anchoEnChars * 11 / tamanoPt`. Es una estimación: sirve para decidir cuánto encoger, no para prometer un ajuste exacto — por eso además se marca `shrinkToFit`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/puntotres/packinglist/service/etiquetas/AjusteFuenteTest.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class AjusteFuenteTest {

    // --- el cálculo, sin POI ---

    @Test
    void unTextoQueCabeConservaSuTamano() {
        // 10 chars de ancho a 11 pt = capacidad 10; el texto son 10.
        assertEquals((short) 11, AjusteFuente.tamano("1234567890", 10.0, (short) 11));
    }

    @Test
    void unTextoQueNoCabeEncogeProporcionalmente() {
        // Ancho 20 chars, fuente 22 pt -> capacidad 10 chars; texto de 15
        // -> 22 * 10 / 15 = 14,66 -> 14.
        assertEquals((short) 14, AjusteFuente.tamano("123456789012345", 20.0, (short) 22));
    }

    @Test
    void nuncaBajaDelMinimoLegible() {
        // Capacidad 10, texto de 40 -> 2,75 pt, que no lo lee nadie.
        assertEquals((short) AjusteFuente.TAMANO_MINIMO_PT,
                AjusteFuente.tamano("1".repeat(40), 10.0, (short) 11));
    }

    @Test
    void elTextoVacioConservaSuTamano() {
        assertEquals((short) 18, AjusteFuente.tamano("", 10.0, (short) 18));
        assertEquals((short) 18, AjusteFuente.tamano(null, 10.0, (short) 18));
    }

    // --- la aplicación sobre la celda ---

    // POI devuelve un XSSFCellStyle nuevo en cada getCellStyle(): "el mismo
    // estilo" solo se puede comprobar por getIndex(), nunca con assertSame.
    @Test
    void unaCeldaQueCabeNoCambiaDeEstilo() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Cell celda = celdaCon(libro, "CORTO", (short) 11, 30);
            short indiceAntes = celda.getCellStyle().getIndex();
            int estilosAntes = libro.getNumCellStyles();

            new AjusteFuente(libro).ajustar(celda);

            // Ni estilo nuevo, ni shrinkToFit: una caja de un solo artículo
            // tiene que producir el mismo fichero que antes de esta clase.
            assertEquals(indiceAntes, celda.getCellStyle().getIndex());
            assertEquals(estilosAntes, libro.getNumCellStyles());
            assertFalse(((XSSFCellStyle) celda.getCellStyle()).getShrinkToFit());
            assertEquals((short) 11,
                    ((XSSFCellStyle) celda.getCellStyle()).getFont().getFontHeightInPoints());
        }
    }

    @Test
    void unaCeldaQueNoCabeRecibeUnEstiloConLaFuenteMasPequenaYShrinkToFit() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Cell celda = celdaCon(libro, "X".repeat(60), (short) 22, 20);

            new AjusteFuente(libro).ajustar(celda);

            XSSFCellStyle estilo = (XSSFCellStyle) celda.getCellStyle();
            assertTrue(estilo.getFont().getFontHeightInPoints() < 22);
            assertTrue(estilo.getShrinkToFit());
        }
    }

    @Test
    void elEstiloClonadoConservaNegritaYNombreDeFuente() throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Cell celda = celdaCon(libro, "X".repeat(60), (short) 22, 20);
            XSSFFont original = ((XSSFCellStyle) celda.getCellStyle()).getFont();
            original.setBold(true);
            original.setFontName("Arial");

            new AjusteFuente(libro).ajustar(celda);

            XSSFFont fuente = ((XSSFCellStyle) celda.getCellStyle()).getFont();
            assertTrue(fuente.getBold());
            assertEquals("Arial", fuente.getFontName());
        }
    }

    @Test
    void dosCeldasIgualesComparteEstilo() throws IOException {
        // POI tiene un tope de ~64.000 estilos por libro y estas celdas se
        // escriben dos veces por caja: sin caché un envío grande lo revienta.
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            AjusteFuente ajuste = new AjusteFuente(libro);
            Cell una = celdaCon(libro, "X".repeat(60), (short) 22, 20);
            Cell otra = una.getRow().getSheet().createRow(1).createCell(0);
            otra.setCellStyle(una.getSheet().getRow(0).getCell(0).getCellStyle());
            otra.setCellValue("X".repeat(60));

            int estilosAntes = libro.getNumCellStyles();
            ajuste.ajustar(una);
            ajuste.ajustar(otra);

            assertEquals(estilosAntes + 1, libro.getNumCellStyles());
            assertEquals(una.getCellStyle().getIndex(), otra.getCellStyle().getIndex());
        }
    }

    /** Una celda con texto, tamaño de fuente y ancho de columna dados. */
    private static Cell celdaCon(XSSFWorkbook libro, String texto, short tamanoPt,
                                 int anchoEnChars) {
        XSSFSheet hoja = libro.createSheet("h" + libro.getNumberOfSheets());
        hoja.setColumnWidth(0, anchoEnChars * 256);
        XSSFFont fuente = libro.createFont();
        fuente.setFontHeightInPoints(tamanoPt);
        XSSFCellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        Cell celda = hoja.createRow(0).createCell(0);
        celda.setCellValue(texto);
        celda.setCellStyle(estilo);
        return celda;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AjusteFuenteTest`
Expected: fallo de compilación — `AjusteFuente` no existe.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/puntotres/packinglist/service/etiquetas/AjusteFuente.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Encoge la fuente de una celda cuando su texto no cabe en el ancho de su
 * columna, sin tocar el tamaño de la celda ni el de la etiqueta.
 *
 * Hace falta porque una etiqueta de caja con varios artículos concatena sus
 * campos con " / " y el texto crece: en la plantilla de AMI la columna C da
 * para unos 28 caracteres a la fuente de REFERENCE, y dos artículos ya llegan
 * justos.
 *
 * Se hacen las DOS cosas a la vez:
 * <ul>
 * <li>se <b>calcula</b> el tamaño y se escribe en el estilo, porque es
 * determinista y un test puede afirmarlo reabriendo el .xlsx;</li>
 * <li>se marca además <b>shrinkToFit</b>, que es el "Reducir hasta ajustar"
 * de Excel, como red de seguridad para cuando el texto se pasa incluso al
 * tamaño mínimo. Funciona aquí porque las celdas de valor de la plantilla no
 * están combinadas ni tienen wrapText — Excel lo ignora en esos dos casos.</li>
 * </ul>
 *
 * Si el texto cabe, no se toca nada: una caja de un solo artículo produce el
 * mismo fichero que antes de existir esta clase.
 */
public final class AjusteFuente {

    /** Por debajo de esto no se lee en una etiqueta impresa. */
    public static final int TAMANO_MINIMO_PT = 8;

    /**
     * Excel mide el ancho de columna en caracteres de su fuente por defecto,
     * que es de 11 pt: la capacidad a otro tamaño se escala con esto.
     */
    private static final double TAMANO_DE_REFERENCIA_PT = 11.0;

    private final XSSFWorkbook libro;
    private final Map<String, XSSFCellStyle> estilos = new HashMap<>();

    public AjusteFuente(XSSFWorkbook libro) {
        this.libro = libro;
    }

    /**
     * Tamaño de fuente con el que el texto cabe en una columna de ese ancho.
     * anchoEnChars es getColumnWidth()/256: caracteres de la fuente por
     * defecto. Devuelve el tamaño original si ya cabe.
     */
    public static short tamano(String texto, double anchoEnChars, short tamanoOriginalPt) {
        if (texto == null || texto.isBlank() || tamanoOriginalPt <= TAMANO_MINIMO_PT) {
            return tamanoOriginalPt;
        }
        double capacidad = anchoEnChars * TAMANO_DE_REFERENCIA_PT / tamanoOriginalPt;
        if (texto.length() <= capacidad) {
            return tamanoOriginalPt;
        }
        double escalado = tamanoOriginalPt * capacidad / texto.length();
        return (short) Math.max(TAMANO_MINIMO_PT, Math.floor(escalado));
    }

    /** Ajusta la fuente de la celda a su contenido; no hace nada si cabe. */
    public void ajustar(Cell celda) {
        if (celda.getCellType() != CellType.STRING) {
            return;
        }
        XSSFCellStyle original = (XSSFCellStyle) celda.getCellStyle();
        short tamanoOriginal = original.getFont().getFontHeightInPoints();
        double anchoEnChars = celda.getSheet().getColumnWidth(celda.getColumnIndex()) / 256.0;
        short nuevo = tamano(celda.getStringCellValue(), anchoEnChars, tamanoOriginal);
        if (nuevo == tamanoOriginal) {
            return;
        }
        celda.setCellStyle(estiloCon(original, nuevo));
    }

    /** El estilo original con otro tamaño de fuente, creado una sola vez. */
    private XSSFCellStyle estiloCon(XSSFCellStyle original, short tamano) {
        return estilos.computeIfAbsent(original.getIndex() + ":" + tamano, clave -> {
            XSSFFont fuenteOriginal = original.getFont();
            XSSFFont fuente = libro.createFont();
            fuente.setFontName(fuenteOriginal.getFontName());
            fuente.setBold(fuenteOriginal.getBold());
            fuente.setItalic(fuenteOriginal.getItalic());
            if (fuenteOriginal.getXSSFColor() != null) {
                fuente.setColor(fuenteOriginal.getXSSFColor());
            }
            fuente.setFontHeightInPoints(tamano);
            XSSFCellStyle estilo = libro.createCellStyle();
            estilo.cloneStyleFrom(original);
            estilo.setFont(fuente);
            estilo.setShrinkToFit(true);
            return estilo;
        });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AjusteFuenteTest`
Expected: PASS (8 tests).

- [ ] **Step 5: Run the whole suite**

Run: `mvn test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AjusteFuente.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/AjusteFuenteTest.java
git commit -m "el texto que no cabe en una celda de etiqueta encoge la fuente"
```

---

### Task 3: AMI — la etiqueta de bolsos muestra todos sus artículos

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java:128-221`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java:130-152`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java`

**Interfaces:**
- Consumes: `ArticulosDeCaja.de(CajaFisica, boolean)`, `ArticulosDeCaja.unir(List, Function)`, `ArticuloEtiqueta`, `new AjusteFuente(XSSFWorkbook)` + `ajustar(Cell)`.
- Produces: `AmiEtiquetasGenerador` sigue construyendo el mismo record `EtiquetaCaja`; no cambia ninguna firma pública. El campo `colorCode` de `EtiquetaCaja` pasa a poder llevar varios valores concatenados.

**Contexto:** en esta tarea **no** se toca nada de la hoja extra (Task 6) ni el aviso (Task 6). Solo la concatenación en bolsos.

- [ ] **Step 1: Write the failing test**

En `AmiEtiquetasGeneradorTest`, añadir la referencia de bolso que falta al fixture y los tests nuevos. Sustituir el método `pedido()` por:

```java
    private static byte[] pedido() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665,
                        "3666598354771", ean128("3666598354771", 7665, "ES")),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07703 CH",
                        "3666598354771", ean128("3666598354771", 7703, "ES")),
                new Fila("SPAIN", "ULL753.AL0168", "001", "IVORY", "U", 7665,
                        "3666598313495", ean128("3666598313495", 7665, "ES")),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "85", 7672,
                        "3666598890064", ean128("3666598890064", 7672, "MA")),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "95", 7672,
                        "3666598890088", ean128("3666598890088", 7672, "MA")),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "105", 7672,
                        "3666598890101", ean128("3666598890101", 7672, "MA")));
    }
```

Y añadir estos tests:

```java
    @Test
    void unaCajaDeBolsosConDosArticulosLosConcatenaConBarras() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertEquals("ULL163.AL0052 / ULL753.AL0168", texto(hoja, 10, 2));
            assertEquals("221 BLACK / 001 IVORY", texto(hoja, 11, 2));
            // SIZE sigue siendo único: "U / U" no aporta nada.
            assertEquals("U", texto(hoja, 12, 2));
            assertEquals("3 / 5", texto(hoja, 13, 2));
            // Peso y parcel son de la caja, no del artículo.
            assertEquals("5,28 KGS", texto(hoja, 14, 2));
            assertEquals("1 / 1", texto(hoja, 15, 2));
        }
    }

    @Test
    void unaCajaDeBolsosConTresArticulosLosConcatenaTodos() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665"),
                        caja(1, "USL999.XX0000", "007", null, 2, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertEquals("ULL163.AL0052 / ULL753.AL0168 / USL999.XX0000", texto(hoja, 10, 2));
            assertEquals("3 / 5 / 2", texto(hoja, 13, 2));
            // El tercero no está en el pedido: color del JSON tal cual.
            assertEquals("221 BLACK / 001 IVORY / 007", texto(hoja, 11, 2));
        }
    }

    @Test
    void laEtiquetaDeVariosArticulosLlevaLosCodigosDeBarrasDelPrimero() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            // PO + EAN13 + EAN128 del primer artículo, en las dos etiquetas
            // del par: tres imágenes por etiqueta, no seis.
            assertEquals(6, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void unaCajaDeUnSoloBolsoSaleExactamenteIgualQueAntes() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertEquals("ULL163.AL0052", texto(hoja, 10, 2));
            assertEquals("221 BLACK", texto(hoja, 11, 2));
            assertEquals("U", texto(hoja, 12, 2));
            assertEquals("50", texto(hoja, 13, 2));
        }
        assertTrue(resultado.getAvisos().isEmpty(), resultado.getAvisos().toString());
    }

    @Test
    void dosLineasDelMismoBolsoEnUnaCajaSumanLaCantidadComoAntes() throws IOException {
        // Mismo artículo repartido en dos líneas: un solo artículo, cantidad
        // sumada. Nada de "30 / 20".
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 30, 5.28, "07665"),
                        caja(1, "ULL163.AL0052", "221", null, 20, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals("ULL163.AL0052", texto(libro.getSheetAt(0), 10, 2));
            assertEquals("50", texto(libro.getSheetAt(0), 13, 2));
        }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AmiEtiquetasGeneradorTest`
Expected: FAIL en `unaCajaDeBolsosConDosArticulosLosConcatenaConBarras` — se espera `ULL163.AL0052 / ULL753.AL0168` y sale `ULL163.AL0052`.

- [ ] **Step 3: Write minimal implementation**

En `AmiEtiquetasGenerador`, añadir un record privado y sustituir el cuerpo de `etiquetaDe` desde el bloque de "Caja mixta" hasta el `return`:

```java
    /** Un artículo de la caja con lo que aporta el excel de pedido. */
    private record ArticuloResuelto(ArticuloEtiqueta articulo, String colorCode,
                                    String ean13, String ean128) {
    }
```

```java
    private EtiquetaCaja etiquetaDe(CajaFisica caja, int posicion, int total,
                                    AmiEtiquetaLayout layout, AmiPedidoExcel pedido,
                                    DatosEnvio envio, String nombreDestino,
                                    List<String> avisos, List<CajaData> cajasPendientes) {
        List<CajaData> lineas = caja.lineas();
        CajaData lider = caja.lider();
        boolean cinturones = lider.esCinturon();

        // Caja mixta de verdad (varias referencias o colores): el spec no la
        // contempla para etiquetas; se etiqueta con la primera y se avisa.
        Set<String> refsColores = new LinkedHashSet<>();
        for (CajaData linea : lineas) {
            refsColores.add(claveRefColor(linea));
        }
        if (refsColores.size() > 1 && cinturones) {
            avisos.add("La caja " + lider.getNumeroCaja() + " de " + nombreDestino
                    + " mezcla varias referencias/colores: la etiqueta lleva "
                    + lider.getReferencia() + " " + lider.getCodigoColor());
        }

        // El ORDER NUMBER (celda y código de barras) sale SIEMPRE del campo
        // 'pedido' del JSON: es el dato por referencia del packing list. El
        // excel de pedido aporta el color code y sirve de contraste del PO.
        String pedidoJson = soloDigitos(lider.getNumeroPedido());
        String orderNumber = pedidoJson.isBlank()
                ? null : String.format("%05d", Long.parseLong(pedidoJson));
        if (orderNumber == null) {
            avisos.add("Caja " + lider.getNumeroCaja() + " de " + nombreDestino
                    + " sin campo 'pedido' en el JSON: etiqueta sin order number "
                    + "ni código de barras");
        }

        // Un artículo por referencia+color (bolsos) o +talla (cinturones), y
        // cada uno con su fila del pedido: su color code y sus dos EAN.
        List<ArticuloResuelto> resueltos = ArticulosDeCaja.de(caja, cinturones).stream()
                .map(articulo -> resolver(articulo, layout, pedido, orderNumber,
                        lider.getNumeroCaja(), nombreDestino, avisos))
                .toList();
        ArticuloResuelto primero = resueltos.get(0);

        String referencia;
        String colorCode;
        String talla;
        String cantidad;
        if (cinturones) {
            // Los cinturones no cambian: SIZE con las tallas ordenadas y
            // QUANTITY con los pares cantidad-talla de la referencia líder.
            List<CajaData> ordenadas = lineas.stream()
                    .filter(linea -> claveRefColor(lider).equals(claveRefColor(linea)))
                    .sorted(Comparator.comparingInt(AmiEtiquetasGenerador::tallaNumerica))
                    .toList();
            referencia = lider.getReferencia();
            colorCode = primero.colorCode();
            talla = String.join("-", ordenadas.stream().map(CajaData::getTalla).toList());
            // Los pares cantidad-talla solo tienen sentido con varias tallas;
            // con una sola, QUANTITY es la cantidad a secas (regla general).
            cantidad = ordenadas.size() == 1
                    ? String.valueOf(ordenadas.get(0).getCantidad())
                    : String.join(",", ordenadas.stream()
                            .map(linea -> linea.getCantidad() + "-" + linea.getTalla()).toList());
        } else {
            // Bolsos: un valor por artículo en cada campo, en el orden del
            // packing list. La talla no se concatena: "U / U" no dice nada.
            List<ArticuloEtiqueta> articulos = resueltos.stream()
                    .map(ArticuloResuelto::articulo).toList();
            referencia = ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::referencia);
            // El color code no sale del artículo sino de su fila del pedido,
            // así que este no puede pasar por ArticulosDeCaja.unir.
            colorCode = resueltos.stream().map(ArticuloResuelto::colorCode)
                    .collect(Collectors.joining(" / "));
            talla = TALLA_UNICA;
            cantidad = ArticulosDeCaja.unir(articulos, a -> String.valueOf(a.cantidad()));
        }

        // El peso es de la caja física ENTERA y viene una sola vez, en su
        // línea líder; las demás líneas no aportan peso.
        Double peso = caja.pesoBrutoKg();
        if (peso == null) {
            cajasPendientes.add(lider);
        }
        String pesoTexto = peso == null
                ? null : String.format(ESPANOL, "%.2f KGS", peso);
        return new EtiquetaCaja(envio.getTemporada(), referencia, colorCode,
                talla, cantidad, pesoTexto, posicion + " / " + total, orderNumber,
                primero.ean13(), primero.ean128());
    }

    /** La fila del pedido de un artículo, o el color del JSON con aviso. */
    private ArticuloResuelto resolver(ArticuloEtiqueta articulo, AmiEtiquetaLayout layout,
                                      AmiPedidoExcel pedido, String orderNumber,
                                      int numeroCaja, String nombreDestino,
                                      List<String> avisos) {
        // La talla solo entra en la clave de los cinturones: los bolsos van
        // como talla única ("U") en el excel de pedido.
        Optional<AmiPedidoExcel.FilaPedido> fila = pedido.buscar(articulo.referencia(),
                articulo.codigoColor(), articulo.talla(), layout.sufijoPo());
        if (fila.isEmpty()) {
            avisos.add("Referencia '" + articulo.referencia() + "' (" + nombreDestino
                    + ") no encontrada en el excel de pedido: el color code sale del JSON"
                    + " y la etiqueta va sin EAN13 ni EAN128");
            return new ArticuloResuelto(articulo, articulo.codigoColor(), null, null);
        }
        for (String aviso : fila.get().avisosEan()) {
            avisos.add("Caja " + numeroCaja + " de " + nombreDestino + ": " + aviso);
        }
        if (orderNumber != null
                && Long.parseLong(fila.get().orderNumber()) != Long.parseLong(orderNumber)) {
            avisos.add("Caja " + numeroCaja + " de " + nombreDestino
                    + ": el pedido del JSON (" + orderNumber
                    + ") no coincide con el PO del excel de pedido ("
                    + fila.get().orderNumber() + "); la etiqueta lleva el del JSON");
        }
        return new ArticuloResuelto(articulo, fila.get().colorCode(),
                fila.get().ean13(), fila.get().ean128());
    }
```

Añadir la constante que falta junto a las demás de la clase:

```java
    /** Los bolsos van como talla única en la etiqueta y en el pedido. */
    private static final String TALLA_UNICA = "U";
```

Imports: `ArticuloEtiqueta` y `ArticulosDeCaja` son del mismo paquete, no hace falta importarlos; sí añadir `java.util.stream.Collectors` y revisar que sigan usándose `LinkedHashSet`, `Set`, `Optional` y `Comparator`.

En `AmiEtiquetasExcelBuilder`, aplicar el ajuste de fuente a las tres celdas que pueden ir concatenadas. Cambiar `escribirEtiqueta` a no estática y con el ajuste:

```java
    private void escribirEtiqueta(XSSFSheet hoja, AmiEtiquetaLayout layout, int base,
                                  EtiquetaCaja etiqueta, AjusteFuente ajuste) {
        // El order number va también como texto bajo su encabezado (la
        // plantilla trae un valor de ejemplo que hay que pisar siempre).
        escribir(hoja, base + layout.filaOrderNumber(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.orderNumber());
        // La temporada conserva el estilo de la plantilla (rojo en AMI): es
        // el aspecto que quiere el cliente, no un placeholder a corregir.
        escribir(hoja, base + layout.filaTemporada(),
                AmiEtiquetaLayout.COL_TEMPORADA, etiqueta.temporada());
        // Estas tres pueden llevar varios artículos concatenados y crecer.
        ajuste.ajustar(escribir(hoja, base + layout.filaReferencia(),
                AmiEtiquetaLayout.COL_VALOR, etiqueta.referencia()));
        ajuste.ajustar(escribir(hoja, base + layout.filaColor(),
                AmiEtiquetaLayout.COL_VALOR, etiqueta.colorCode()));
        escribir(hoja, base + layout.filaTalla(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.talla());
        ajuste.ajustar(escribir(hoja, base + layout.filaCantidad(),
                AmiEtiquetaLayout.COL_VALOR, etiqueta.cantidad()));
        escribir(hoja, base + layout.filaPeso(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.pesoBruto());
        escribir(hoja, base + layout.filaParcel(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.parcel());
    }
```

Y en `generar`, crear el ajuste una vez y pasarlo (una sola instancia por libro, que es donde vive la caché de estilos):

```java
            AjusteFuente ajuste = new AjusteFuente(libro);
            for (int i = 0; i < etiquetas.size(); i++) {
                int base = i * layout.alturaBloque();
                escribirEtiqueta(hoja, layout, base, etiquetas.get(i), ajuste);
                escribirEtiqueta(hoja, layout, base + layout.offsetSegundaEtiqueta(),
                        etiquetas.get(i), ajuste);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AmiEtiquetasGeneradorTest`
Expected: PASS.

- [ ] **Step 5: Run the whole suite**

Run: `mvn test`
Expected: PASS. Ojo especialmente a `AmiEtiquetasExcelBuilderTest`, `AmiPedidoRealTest` y `EtiquetasGenerationServiceTest`. Si `unaCajaConVariasReferenciasPesaLoQueDigaSuLineaLider` falla, **no relajar la aserción de peso**: el peso sigue siendo el de la línea líder.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java
git commit -m "la etiqueta de bolsos de AMI muestra todos los articulos de la caja"
```

---

### Task 4: APC — la etiqueta de bolsos muestra todos sus artículos

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasGenerador.java:119-172`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasExcelBuilder.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasGeneradorTest.java`

**Interfaces:**
- Consumes: `ArticulosDeCaja.de(CajaFisica, false)`, `ArticulosDeCaja.unir(...)`, `AjusteFuente`.
- Produces: `EtiquetaCajaApc` sin cambios de firma.

**Contexto:** APC decide "es cinturón" por `talla != null` en la línea, **no** por el prefijo `UBL`. APC no imprime códigos de barras, así que aquí no hay hoja extra: solo concatenación.

- [ ] **Step 1: Write the failing test**

Añadir a `ApcEtiquetasGeneradorTest`, con sus helpers ya existentes (`envio()`, `caja(numero, referencia, color, talla, cantidad, pesoBruto, palet)`, `destino(nombre, palets, cajas...)` — que ya devuelve un `DestinoImportado` —, `palet(...)`, `hojaCajas(byte[])` y `texto(...)`). En la hoja JAPAN las filas son: REFERENCE 11, COLOUR 12, SIZE 13, PIECES 14, COLISAGE 17, POIDS 18, y la columna de valor es la 2:

```java
    @Test
    void unaCajaDeBolsosConDosArticulosConcatenaReferenciaColorYPiezas() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 3, 7.6, 1),
                                caja(1, "PXCBC-F67009", "LZZ-BLANC", null, 5, null, 1))),
                envio(), Map.of());

        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("PXCBC-F67008 / PXCBC-F67009", texto(hoja, 11, 2));
        assertEquals("LZZ-NOIR / LZZ-BLANC", texto(hoja, 12, 2));
        // SIZE no se concatena: "U / U" no dice nada.
        assertEquals("U", texto(hoja, 13, 2));
        assertEquals("3 / 5", texto(hoja, 14, 2));
        // Peso y colisage son de la caja, no del artículo.
        assertEquals("7,60 Kg", texto(hoja, 18, 2));
        assertEquals("1 / 1", texto(hoja, 17, 2));
    }

    @Test
    void unaCajaDeUnSoloBolsoSaleExactamenteIgualQueAntes() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1))),
                envio(), Map.of());

        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("PXCBC-F67008", texto(hoja, 11, 2));
        assertEquals("LZZ-NOIR", texto(hoja, 12, 2));
        assertEquals("11", texto(hoja, 14, 2));
    }
```

El test existente `losCinturonesAgrupanUnidadesPorTalla` es ya el de "cinturones sin cambios": no tocarlo y comprobar que sigue en verde.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ApcEtiquetasGeneradorTest`
Expected: FAIL — sale `ULL163.AL0052` en vez de la concatenación.

- [ ] **Step 3: Write minimal implementation**

En `ApcEtiquetasGenerador.etiquetaDe`, sustituir el bloque que calcula `size`/`piezas` y el `return`:

```java
        String size;
        String piezas;
        String referencia;
        String colour;
        if (unidadesPorTalla.isEmpty()) {
            // Bolsos: un valor por artículo en cada campo, en el orden del
            // packing list. SIZE no se concatena: "U / U" no dice nada.
            List<ArticuloEtiqueta> articulos = ArticulosDeCaja.de(caja, false);
            referencia = ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::referencia);
            colour = ArticulosDeCaja.unir(articulos, ArticuloEtiqueta::codigoColor);
            size = "U";
            piezas = ArticulosDeCaja.unir(articulos, a -> String.valueOf(a.cantidad()));
        } else {
            // Cinturones: sin cambios, la referencia y el color son los de la
            // línea líder y las unidades van agrupadas por talla.
            referencia = lider.getReferencia();
            colour = lider.getCodigoColor();
            if (unidadesPorTalla.size() == 1) {
                var unica = unidadesPorTalla.entrySet().iterator().next();
                size = unica.getKey();
                piezas = String.valueOf(unica.getValue());
            } else {
                size = String.join("-", unidadesPorTalla.keySet());
                piezas = String.join(",", unidadesPorTalla.entrySet().stream()
                        .map(e -> e.getValue() + "-" + e.getKey()).toList());
            }
        }
        ...
        return new EtiquetaCajaApc(NO_DISPONIBLE, NO_DISPONIBLE, referencia,
                colour, size, piezas, posicion + " / " + total, kg(peso));
```

Ojo: la variable `propias` deja de usarse en la rama de bolsos (la sustituye `ArticulosDeCaja`) pero **sigue usándose** para construir `unidadesPorTalla`; no borrarla.

En `ApcEtiquetasExcelBuilder` hay que hacer tres cambios encadenados, porque hoy todo ese camino es estático:

1. `escribir(...)` pasa de `private static void` a `private static Cell` y termina con `return celda;`.
2. `escribirEtiquetaCaja(...)` recibe el ajuste y lo aplica a las tres celdas concatenables:

```java
    private static void escribirEtiquetaCaja(XSSFSheet hoja, ApcEtiquetaLayout layout,
                                             int base, EtiquetaCajaApc etiqueta,
                                             AjusteFuente ajuste) {
        escribir(hoja, base + layout.filaOrder(), etiqueta.orderNumber());
        escribir(hoja, base + layout.filaLivraison(), etiqueta.livraisonCode());
        // Estas tres pueden llevar varios artículos concatenados y crecer.
        ajuste.ajustar(escribir(hoja, base + layout.filaReferencia(), etiqueta.referencia()));
        ajuste.ajustar(escribir(hoja, base + layout.filaColor(), etiqueta.colour()));
        escribir(hoja, base + layout.filaTalla(), etiqueta.size());
        ajuste.ajustar(escribir(hoja, base + layout.filaPiezas(), etiqueta.piecesBySize()));
        escribir(hoja, base + layout.filaColisage(), etiqueta.colisage());
        escribir(hoja, base + layout.filaPeso(), etiqueta.poidsBrut());
    }
```

3. `escribirHojaCajas(...)` recibe también el ajuste y se lo pasa a las dos llamadas del par de etiquetas; quien lo crea es `generar(...)`, **una sola instancia por libro** (ahí vive la caché de estilos), justo después de abrir el `XSSFWorkbook` de la plantilla.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ApcEtiquetasGeneradorTest`
Expected: PASS.

- [ ] **Step 5: Run the whole suite**

Run: `mvn test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasGenerador.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasExcelBuilder.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasGeneradorTest.java
git commit -m "la etiqueta de bolsos de APC muestra todos los articulos de la caja"
```

---

### Task 5: `HojaCodigosBarrasExtra` — maquetar la hoja

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/FilaCodigoBarrasExtra.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/HojaCodigosBarrasExtra.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/HojaCodigosBarrasExtraTest.java`

**Interfaces:**
- Consumes: `CodigoBarrasEan13.esValido(String)` / `png(String) -> Optional<byte[]>`, `CodigoBarrasCode128.png(String, double)`, `AnclajeImagen.fijo(Sheet, int columna, long dx, int fila, long dy, long cx, long cy)`.
- Produces:
  - `public record FilaCodigoBarrasExtra(int numeroCaja, String referencia, String colorCode, String talla, String cantidad, String ean13, String ean128)`
  - `public static final String HojaCodigosBarrasExtra.NOMBRE_HOJA = "CODIGOS BARRAS EXTRA"`
  - `public static void HojaCodigosBarrasExtra.escribir(XSSFWorkbook libro, List<FilaCodigoBarrasExtra> filas)` — no hace nada si la lista está vacía.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/puntotres/packinglist/service/etiquetas/HojaCodigosBarrasExtraTest.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class HojaCodigosBarrasExtraTest {

    private static final String EAN13 = "3666598890064";
    private static final String EAN128 =
            "366659889006400001000076720000000000000000MA";

    private static FilaCodigoBarrasExtra fila(int caja, String referencia, String talla) {
        return new FilaCodigoBarrasExtra(caja, referencia, "001 BLACK", talla, "4",
                EAN13, EAN128);
    }

    /** Escribe la hoja en un libro nuevo y lo reabre desde bytes. */
    private static XSSFWorkbook generarYReabrir(List<FilaCodigoBarrasExtra> filas)
            throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("ETIQUETAS");
            HojaCodigosBarrasExtra.escribir(libro, filas);
            libro.write(salida);
        }
        return new XSSFWorkbook(new ByteArrayInputStream(salida.toByteArray()));
    }

    @Test
    void sinFilasNoCreaLaHoja() throws IOException {
        try (XSSFWorkbook libro = generarYReabrir(List.of())) {
            assertEquals(1, libro.getNumberOfSheets());
            assertNull(libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA));
        }
    }

    @Test
    void escribeUnaFilaPorArticuloConSusDatos() throws IOException {
        try (XSSFWorkbook libro = generarYReabrir(List.of(
                fila(7, "ULL753.AL0168", "U"),
                fila(9, "UBL029.AL0216", "95")))) {

            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertNotNull(hoja);
            assertEquals("CAJA", texto(hoja, 0, 0));
            assertEquals("REFERENCE", texto(hoja, 0, 1));
            assertEquals("COLOR CODE", texto(hoja, 0, 2));
            assertEquals("SIZE", texto(hoja, 0, 3));
            assertEquals("QUANTITY", texto(hoja, 0, 4));
            assertEquals("EAN-13", texto(hoja, 0, 5));
            assertEquals("EAN128", texto(hoja, 0, 6));

            assertEquals("7", texto(hoja, 1, 0));
            assertEquals("ULL753.AL0168", texto(hoja, 1, 1));
            assertEquals("001 BLACK", texto(hoja, 1, 2));
            assertEquals("U", texto(hoja, 1, 3));
            assertEquals("4", texto(hoja, 1, 4));
            // El valor del código va como texto además de como imagen.
            assertEquals(EAN13, texto(hoja, 1, 5));
            assertEquals(EAN128, texto(hoja, 1, 6));

            assertEquals("9", texto(hoja, 2, 0));
            assertEquals("95", texto(hoja, 2, 3));
        }
    }

    @Test
    void cadaFilaLlevaSusDosCodigosDeBarrasComoImagen() throws IOException {
        try (XSSFWorkbook libro = generarYReabrir(List.of(
                fila(7, "ULL753.AL0168", "U"),
                fila(9, "UBL029.AL0216", "95")))) {

            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals(4, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void unArticuloSinCodigosSaleIgualPeroSinImagenes() throws IOException {
        try (XSSFWorkbook libro = generarYReabrir(List.of(
                new FilaCodigoBarrasExtra(7, "USL999.XX0000", "007", "U", "2",
                        null, null)))) {

            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals("USL999.XX0000", texto(hoja, 1, 1));
            assertEquals("", texto(hoja, 1, 5));
            assertEquals(0, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void unEan13InvalidoNoSeDibujaPeroSuTextoSiSeVe() throws IOException {
        // Mismo criterio que la etiqueta: no se imprime un código que el
        // escáner no va a leer, pero el dato no se oculta al operario.
        try (XSSFWorkbook libro = generarYReabrir(List.of(
                new FilaCodigoBarrasExtra(7, "ULL753.AL0168", "001", "U", "5",
                        "1234567890123", EAN128)))) {

            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals("1234567890123", texto(hoja, 1, 5));
            // Solo la imagen del EAN128.
            assertEquals(1, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void elMismoCodigoEnVariasFilasSeGuardaUnaSolaVez() throws IOException {
        // Un envío repite mucho el mismo artículo: sin caché el .xlsx
        // guardaría el mismo PNG una vez por fila.
        try (XSSFWorkbook libro = generarYReabrir(List.of(
                fila(7, "UBL029.AL0216", "95"),
                fila(8, "UBL029.AL0216", "95")))) {

            assertEquals(2, libro.getAllPictures().size());
        }
    }

    private static String texto(XSSFSheet hoja, int fila, int col) {
        if (hoja.getRow(fila) == null || hoja.getRow(fila).getCell(col) == null) {
            return "";
        }
        return hoja.getRow(fila).getCell(col).toString().trim();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=HojaCodigosBarrasExtraTest`
Expected: fallo de compilación — las clases no existen.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/puntotres/packinglist/service/etiquetas/FilaCodigoBarrasExtra.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

/**
 * Una fila de la hoja "CODIGOS BARRAS EXTRA": el artículo de una caja que no
 * cabe en su etiqueta y los códigos de barras que le faltan por imprimir.
 *
 * ean13/ean128 null = ese código no se puede dar (artículo no encontrado en
 * el excel de pedido, o columna ausente): la fila se escribe igual, sin esa
 * imagen. El código de barras del PO no está aquí: es de la caja entera y ya
 * va en la etiqueta.
 */
public record FilaCodigoBarrasExtra(int numeroCaja, String referencia, String colorCode,
                                    String talla, String cantidad,
                                    String ean13, String ean128) {
}
```

`src/main/java/com/puntotres/packinglist/service/etiquetas/HojaCodigosBarrasExtra.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * La hoja "CODIGOS BARRAS EXTRA": una fila por artículo que no cabe en la
 * etiqueta de su caja, con sus datos y sus códigos de barras escaneables.
 *
 * En una etiqueta solo cabe un par de EAN, así que cuando una caja lleva
 * varios artículos —o un cinturón con varias tallas, que en el pedido son
 * artículos distintos con EAN distinto— los demás códigos no se imprimían en
 * ningún sitio. Aquí van todos, en una sola hoja del mismo libro de la
 * destinación, para imprimirla junto a las etiquetas.
 *
 * <b>No hay plantilla .xlsx</b>: la maquetación son las constantes de esta
 * clase, como en EscandallosExcelBuilder y EtiquetasArticuloExcelBuilder.
 *
 * El código de barras del PO no se repite: es de la caja y ya está en la
 * etiqueta.
 */
public final class HojaCodigosBarrasExtra {

    public static final String NOMBRE_HOJA = "CODIGOS BARRAS EXTRA";

    private static final String[] ENCABEZADOS =
            {"CAJA", "REFERENCE", "COLOR CODE", "SIZE", "QUANTITY", "EAN-13", "EAN128"};
    /** Ancho de cada columna en caracteres; las dos últimas alojan la imagen. */
    private static final int[] ANCHOS_CHARS = {8, 24, 20, 10, 12, 30, 46};

    private static final int COL_EAN13 = 5;
    private static final int COL_EAN128 = 6;

    private static final float ALTO_FILA_PT = 62;
    /** El texto del código va debajo: la imagen ocupa la parte de arriba. */
    private static final long ALTO_IMAGEN_EMU = 520_700;   // ~41 pt
    private static final long ANCHO_EAN13_EMU = 1_478_280;
    private static final long ANCHO_EAN128_EMU = 2_727_960;
    private static final long MARGEN_EMU = 38_100;         // ~3 pt

    private HojaCodigosBarrasExtra() {
    }

    /** Añade la hoja al libro. Sin filas no se crea nada. */
    public static void escribir(XSSFWorkbook libro, List<FilaCodigoBarrasExtra> filas) {
        if (filas.isEmpty()) {
            return;
        }
        XSSFSheet hoja = libro.createSheet(NOMBRE_HOJA);
        for (int col = 0; col < ANCHOS_CHARS.length; col++) {
            hoja.setColumnWidth(col, ANCHOS_CHARS[col] * 256);
        }
        escribirEncabezados(libro, hoja);

        XSSFCellStyle estiloDato = estiloDato(libro);
        XSSFDrawing dibujo = hoja.createDrawingPatriarch();
        // Un envío repite mucho el mismo artículo: sin esta caché el .xlsx
        // guardaría el mismo PNG una vez por fila.
        Map<String, Integer> imagenes = new HashMap<>();
        for (int i = 0; i < filas.size(); i++) {
            escribirFila(libro, hoja, dibujo, estiloDato, imagenes, i + 1, filas.get(i));
        }
        hoja.setRepeatingRows(org.apache.poi.ss.util.CellRangeAddress.valueOf("1:1"));
        hoja.getPrintSetup().setLandscape(true);
        hoja.setFitToPage(true);
        hoja.getPrintSetup().setFitWidth((short) 1);
        hoja.getPrintSetup().setFitHeight((short) 0);
    }

    private static void escribirEncabezados(XSSFWorkbook libro, XSSFSheet hoja) {
        XSSFFont negrita = libro.createFont();
        negrita.setBold(true);
        XSSFCellStyle estilo = libro.createCellStyle();
        estilo.setFont(negrita);
        estilo.setAlignment(HorizontalAlignment.CENTER);
        XSSFRow cabecera = hoja.createRow(0);
        for (int col = 0; col < ENCABEZADOS.length; col++) {
            Cell celda = cabecera.createCell(col);
            celda.setCellValue(ENCABEZADOS[col]);
            celda.setCellStyle(estilo);
        }
    }

    private static XSSFCellStyle estiloDato(XSSFWorkbook libro) {
        XSSFCellStyle estilo = libro.createCellStyle();
        // El texto del código va abajo, bajo su imagen.
        estilo.setVerticalAlignment(VerticalAlignment.BOTTOM);
        return estilo;
    }

    private static void escribirFila(XSSFWorkbook libro, XSSFSheet hoja, XSSFDrawing dibujo,
                                     XSSFCellStyle estilo, Map<String, Integer> imagenes,
                                     int numeroFila, FilaCodigoBarrasExtra fila) {
        XSSFRow row = hoja.createRow(numeroFila);
        row.setHeightInPoints(ALTO_FILA_PT);
        escribir(row, estilo, 0, String.valueOf(fila.numeroCaja()));
        escribir(row, estilo, 1, fila.referencia());
        escribir(row, estilo, 2, fila.colorCode());
        escribir(row, estilo, 3, fila.talla());
        escribir(row, estilo, 4, fila.cantidad());
        escribir(row, estilo, COL_EAN13, fila.ean13());
        escribir(row, estilo, COL_EAN128, fila.ean128());

        // El EAN13 puede llegar inválido: entonces no se dibuja y la fila sale
        // igual (el aviso lo dio ya AmiPedidoExcel), con su texto visible.
        if (tiene(fila.ean13()) && CodigoBarrasEan13.esValido(fila.ean13())) {
            dibujar(libro, hoja, dibujo, imagenes, "EAN13:" + fila.ean13(),
                    () -> CodigoBarrasEan13.png(fila.ean13()).orElseThrow(),
                    COL_EAN13, numeroFila, ANCHO_EAN13_EMU);
        }
        // El EAN128 se genera ya con la proporción de su hueco para que no se
        // estire al encajarlo, como en la etiqueta.
        if (tiene(fila.ean128())) {
            dibujar(libro, hoja, dibujo, imagenes, "EAN128:" + fila.ean128(),
                    () -> CodigoBarrasCode128.png(fila.ean128(),
                            (double) ANCHO_EAN128_EMU / ALTO_IMAGEN_EMU),
                    COL_EAN128, numeroFila, ANCHO_EAN128_EMU);
        }
    }

    private static void escribir(XSSFRow fila, XSSFCellStyle estilo, int col, String valor) {
        Cell celda = fila.createCell(col);
        if (tiene(valor)) {
            celda.setCellValue(valor);
        } else {
            celda.setBlank();
        }
        celda.setCellStyle(estilo);
    }

    private static void dibujar(XSSFWorkbook libro, XSSFSheet hoja, XSSFDrawing dibujo,
                                Map<String, Integer> imagenes, String clave,
                                Supplier<byte[]> png, int columna, int fila, long ancho) {
        int indice = imagenes.computeIfAbsent(clave,
                k -> libro.addPicture(png.get(), Workbook.PICTURE_TYPE_PNG));
        dibujo.createPicture(AnclajeImagen.fijo(hoja, columna, MARGEN_EMU, fila, MARGEN_EMU,
                ancho, ALTO_IMAGEN_EMU), indice);
    }

    private static boolean tiene(String valor) {
        return valor != null && !valor.isBlank();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=HojaCodigosBarrasExtraTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Run the whole suite**

Run: `mvn test`
Expected: PASS. Nadie llama todavía a la hoja nueva.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/FilaCodigoBarrasExtra.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/HojaCodigosBarrasExtra.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/HojaCodigosBarrasExtraTest.java
git commit -m "hoja CODIGOS BARRAS EXTRA con una fila por articulo sobrante"
```

---

### Task 6: AMI — llenar la hoja extra y avisar de que hay que imprimirla

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java:55-88`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilderTest.java`

**Interfaces:**
- Consumes: `HojaCodigosBarrasExtra.escribir(XSSFWorkbook, List<FilaCodigoBarrasExtra>)`, `FilaCodigoBarrasExtra`, el `ArticuloResuelto` privado de Task 3.
- Produces:
  - `AmiEtiquetasExcelBuilder.generar(AmiEtiquetaLayout, List<EtiquetaCaja>, List<FilaCodigoBarrasExtra>)` — sobrecarga nueva.
  - `AmiEtiquetasExcelBuilder.generar(AmiEtiquetaLayout, List<EtiquetaCaja>)` — se **conserva**, delegando con `List.of()`, para no tocar los 15 tests que ya la usan.

- [ ] **Step 1: Write the failing test**

En `AmiEtiquetasExcelBuilderTest`, añadir:

```java
    @Test
    void sinFilasExtraElLibroSoloTieneLaHojaDeEtiquetas() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA,
                List.of(etiquetaBolso("1 / 1")), List.of());

        try (XSSFWorkbook libro = abrir(excel)) {
            assertEquals(1, libro.getNumberOfSheets());
        }
    }

    @Test
    void conFilasExtraElLibroAnadeLaHojaDeCodigosDeBarras() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA,
                List.of(etiquetaBolso("1 / 1")),
                List.of(new FilaCodigoBarrasExtra(1, "ULL753.AL0168", "001 IVORY", "U", "5",
                        "3666598313495", null)));

        try (XSSFWorkbook libro = abrir(excel)) {
            assertEquals(2, libro.getNumberOfSheets());
            assertEquals("AMI CHINA", libro.getSheetName(0));
            assertEquals(HojaCodigosBarrasExtra.NOMBRE_HOJA, libro.getSheetName(1));
        }
    }
```

**Nota:** `etiquetaBolso(...)` y `abrir(...)` son helpers que ya existen en esa clase de test; usarlos tal cual.

En `AmiEtiquetasGeneradorTest`, añadir:

```java
    @Test
    void unaCajaDeBolsosConDosArticulosGeneraLaHojaExtraConElSegundo() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertNotNull(extra);
            // Solo el segundo artículo: el primero va entero en la etiqueta.
            assertEquals(1, extra.getLastRowNum());
            assertEquals("1", texto(extra, 1, 0));
            assertEquals("ULL753.AL0168", texto(extra, 1, 1));
            assertEquals("001 IVORY", texto(extra, 1, 2));
            assertEquals("U", texto(extra, 1, 3));
            assertEquals("5", texto(extra, 1, 4));
            assertEquals("3666598313495", texto(extra, 1, 5));
        }
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.equals("La caja 1 de PARIS mezcla varias "
                        + "referencias/colores: se han generado códigos de barra aparte "
                        + "para imprimir")), resultado.getAvisos().toString());
    }

    @Test
    void unCinturonMultiTallaGeneraUnaFilaExtraPorTallaNoLider() throws IOException {
        // La líder es la 95 (la que lleva el peso): la etiqueta imprime su
        // EAN y las tallas 85 y 105 van a la hoja extra.
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(2, "UBL029.AL0216", "001", "95", 33, 9.93, "07672"),
                        caja(2, "UBL029.AL0216", "001", "85", 4, null, "07672"),
                        caja(2, "UBL029.AL0216", "001", "105", 3, null, "07672")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // La etiqueta no cambia.
            assertEquals("UBL029.AL0216", texto(hoja, 10, 2));
            assertEquals("85-95-105", texto(hoja, 12, 2));
            assertEquals("4-85,33-95,3-105", texto(hoja, 13, 2));

            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals(2, extra.getLastRowNum());
            assertEquals("85", texto(extra, 1, 3));
            assertEquals("4", texto(extra, 1, 4));
            assertEquals("3666598890064", texto(extra, 1, 5));
            assertEquals("105", texto(extra, 2, 3));
            assertEquals("3666598890101", texto(extra, 2, 5));
        }
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.equals("La caja 2 de PARIS lleva varias tallas: "
                        + "se han generado códigos de barra aparte para imprimir")),
                resultado.getAvisos().toString());
    }

    @Test
    void unaCajaDeUnSoloArticuloNoGeneraHojaExtraNiAviso() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals(1, libro.getNumberOfSheets());
        }
        assertTrue(resultado.getAvisos().isEmpty(), resultado.getAvisos().toString());
    }

    @Test
    void lasFilasExtraDeVariasCajasVanEnLaMismaHoja() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665"),
                        caja(2, "ULL163.AL0052", "221", null, 4, 6.10, "07665"),
                        caja(2, "ULL753.AL0168", "001", null, 6, null, "07665")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals(2, extra.getLastRowNum());
            assertEquals("1", texto(extra, 1, 0));
            assertEquals("2", texto(extra, 2, 0));
        }
    }
```

Añadir el import `static org.junit.jupiter.api.Assertions.assertNotNull;` si no está.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AmiEtiquetasExcelBuilderTest+AmiEtiquetasGeneradorTest`
Expected: fallo de compilación en el builder test (no existe la sobrecarga de 3 argumentos).

- [ ] **Step 3: Write minimal implementation**

En `AmiEtiquetasExcelBuilder`:

```java
    /** Sin filas extra: la firma que usan los tests y los flujos sin sobrantes. */
    public byte[] generar(AmiEtiquetaLayout layout, List<EtiquetaCaja> etiquetas)
            throws IOException {
        return generar(layout, etiquetas, List.of());
    }

    public byte[] generar(AmiEtiquetaLayout layout, List<EtiquetaCaja> etiquetas,
                          List<FilaCodigoBarrasExtra> filasExtra) throws IOException {
        // ...cuerpo actual sin cambios hasta el final del bucle de etiquetas...

            // La hoja extra va DESPUÉS de dejarSoloLaHoja, que se lleva por
            // delante cualquier otra hoja del libro.
            HojaCodigosBarrasExtra.escribir(libro, filasExtra);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }
```

En `AmiEtiquetasGenerador`:

1. `generarDestino` acumula las filas y se las pasa al builder:

```java
        List<EtiquetaCaja> etiquetas = new ArrayList<>();
        List<CajaData> cajasPendientes = new ArrayList<>();
        List<FilaCodigoBarrasExtra> filasExtra = new ArrayList<>();
        int posicion = 0;
        int total = cajasFisicas.size();
        for (CajaFisica caja : cajasFisicas) {
            posicion++;
            etiquetas.add(etiquetaDe(caja, posicion, total, layout, pedido, envio,
                    destino.getNombreDestino(), avisos, cajasPendientes, filasExtra));
        }

        String nombreFichero = ("Etiquetas_AMI_" + destino.getNombreDestino() + "_"
                + envio.getNumeroFactura() + ".xlsx").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        byte[] contenido = builder.generar(layout, etiquetas, filasExtra);
```

2. En `etiquetaDe` (parámetro nuevo `List<FilaCodigoBarrasExtra> filasExtra`), sustituir el bloque del aviso de caja mixta que quedó en Task 3 por el volcado de sobrantes, **después** de calcular `resueltos`:

```java
        // Solo el primer artículo conserva sus códigos de barras en la
        // etiqueta; los demás se imprimen en la hoja "CODIGOS BARRAS EXTRA".
        for (ArticuloResuelto sobrante : resueltos.subList(1, resueltos.size())) {
            filasExtra.add(new FilaCodigoBarrasExtra(lider.getNumeroCaja(),
                    sobrante.articulo().referencia(), sobrante.colorCode(),
                    sobrante.articulo().talla() == null
                            ? TALLA_UNICA : sobrante.articulo().talla(),
                    String.valueOf(sobrante.articulo().cantidad()),
                    sobrante.ean13(), sobrante.ean128()));
        }
        if (resueltos.size() > 1) {
            // El usuario tiene que saber que ese excel trae una hoja más.
            avisos.add("La caja " + lider.getNumeroCaja() + " de " + nombreDestino
                    + (refsColores.size() > 1
                            ? " mezcla varias referencias/colores"
                            : " lleva varias tallas")
                    + ": se han generado códigos de barra aparte para imprimir");
        }
```

Y borrar el `if (refsColores.size() > 1 && cinturones) { ... }` de Task 3: este aviso lo sustituye. `refsColores` se sigue calculando, solo que ahora únicamente para elegir el texto.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AmiEtiquetasExcelBuilderTest+AmiEtiquetasGeneradorTest`
Expected: PASS.

- [ ] **Step 5: Run the whole suite**

Run: `mvn test`

**Va a fallar un test existente, y es correcto que falle:** `AmiEtiquetasGeneradorTest.enCinturonMultiTallaElEanEsElDeLaLineaLider` afirma `resultado.getAvisos().isEmpty()`, y esa caja de tres tallas ahora emite el aviso nuevo. Cambiar esa aserción por:

```java
        assertEquals(List.of("La caja 2 de PARIS lleva varias tallas: se han generado "
                + "códigos de barra aparte para imprimir"), resultado.getAvisos());
```

No relajarla a "ignorar avisos": el test sigue teniendo que demostrar que no aparece **ningún otro** aviso. Si falla algún otro test por el texto del aviso, actualizar la aserción al texto nuevo — el aviso cambió a propósito. Lo que **no** puede cambiar es ninguna aserción sobre celdas de cinturones.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilderTest.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java
git commit -m "las etiquetas de AMI llevan hoja de codigos de barra de los articulos sobrantes"
```

---

### Task 7: Comprobación visual y documentación

**Files:**
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: `target/Etiquetas_AMI_PARIS_F-MULTI.xlsx` para inspección manual.

- [ ] **Step 1: Añadir el test que deja el excel para revisar a ojo**

Sigue el patrón de `generaUnExcelPorDestinacionSoportada`, que ya escribe en `target/`:

```java
    @Test
    void dejaUnExcelDeVariosArticulosParaInspeccionManual() throws IOException {
        // Tres artículos: es donde el texto concatenado se pasa del ancho de
        // la columna y hay que ver en Excel que la fuente encoge de verdad.
        DatosEnvio envio = cabecera();
        envio.setNumeroFactura("F-MULTI");
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 3, 5.28, "07665"),
                        caja(1, "ULL753.AL0168", "001", null, 5, null, "07665"),
                        caja(1, "USL999.XX0000", "007", null, 2, null, "07665"),
                        caja(2, "UBL029.AL0216", "001", "95", 33, 9.93, "07672"),
                        caja(2, "UBL029.AL0216", "001", "85", 4, null, "07672"),
                        caja(2, "UBL029.AL0216", "001", "105", 3, null, "07672")))),
                envio, Map.of("pedido", pedido()));

        ExcelGenerado excel = resultado.getExcels().get(0);
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", excel.getNombreFichero()), excel.getContenido());
        assertEquals("Etiquetas_AMI_PARIS_F-MULTI.xlsx", excel.getNombreFichero());
    }
```

- [ ] **Step 2: Generar y revisar en Excel**

Run: `mvn test -Dtest=AmiEtiquetasGeneradorTest`
Abrir `target/Etiquetas_AMI_PARIS_F-MULTI.xlsx` y comprobar a ojo:
- La etiqueta de la caja 1 muestra las tres referencias concatenadas y **no se salen** de su celda.
- La etiqueta de la caja 2 (cinturones) está **igual que siempre**.
- La hoja `CODIGOS BARRAS EXTRA` tiene 4 filas (2 sobrantes de la caja 1 + 2 tallas de la caja 2) y los códigos se ven completos, no cortados ni estirados.
- Vista previa de impresión: la hoja extra cabe a lo ancho.

Si algún código sale deformado o cortado, ajustar `ANCHOS_CHARS`, `ALTO_FILA_PT`, `ALTO_IMAGEN_EMU` o los anchos EMU de `HojaCodigosBarrasExtra` — son constantes de maquetación y para eso están.

- [ ] **Step 3: Actualizar CLAUDE.md**

En la sección **Etiquetas de caja**, después del párrafo que empieza por "**Los dos EAN de las etiquetas de AMI**", añadir:

```markdown
**Varios artículos en una caja** (`ArticulosDeCaja`): una caja física puede
llevar varios artículos y la etiqueta ya no los colapsa a la línea líder. Un
artículo es referencia+color en bolsos y referencia+color+**talla** en
cinturones (cada talla es un SKU con su propio EAN-13). En **bolsos** la
etiqueta concatena `REFERENCE`, `COLOR CODE` y `QUANTITY` con `" / "` en el
orden del packing list —`SIZE` sigue siendo `U`— y `AjusteFuente` encoge la
fuente si el texto no cabe (tamaño calculado con suelo de 8 pt **y**
`shrinkToFit`; funciona porque las celdas de valor de la plantilla no están
combinadas ni tienen `wrapText`). Los **cinturones no cambian de aspecto**.
Como en una etiqueta solo cabe un par de EAN, los artículos 2..N van a la hoja
`CODIGOS BARRAS EXTRA` (`HojaCodigosBarrasExtra`, sin plantilla, maquetación en
constantes) del mismo libro de la destinación, con un aviso al usuario de que
hay una hoja más que imprimir. Solo AMI: **APC no imprime códigos de barras**,
solo hereda la concatenación.
```

- [ ] **Step 4: Run the whole suite**

Run: `mvn test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java CLAUDE.md
git commit -m "documentacion de articulos por caja y excel de muestra para revisar a ojo"
```

---

## Cobertura del spec

| Requisito del spec | Tarea |
|---|---|
| `ArticulosDeCaja` + criterio de artículo por tipo | 1 |
| Fusión de líneas del mismo artículo, orden de llegada, sin deduplicar valores | 1 |
| Concatenación `" / "` en bolsos de AMI (ref, color, cantidad; SIZE `U`) | 3 |
| Búsqueda en el pedido por artículo | 3 |
| Códigos de barras de la etiqueta = los del primer artículo | 3 |
| Caja de 1 artículo idéntica a hoy | 3, 6 |
| Cinturones sin cambios de aspecto | 3, 6 |
| Concatenación en APC | 4 |
| Ajuste de fuente calculado + `shrinkToFit`, suelo 8 pt, caché de estilos | 2, 3, 4 |
| Hoja `CODIGOS BARRAS EXTRA`: nombre, columnas, imágenes, solo si hay filas | 5, 6 |
| Todas las cajas en la misma hoja | 6 |
| Sin fila del pedido → fila sin imágenes y aviso | 5, 6 |
| Aviso "se han generado códigos de barra aparte para imprimir" | 6 |
| Resto de clientes intactos ("en desarrollo") | ninguna: no se toca `EtiquetasGenerationService` |
| Comprobación manual en Excel | 7 |
| Documentación | 7 |
