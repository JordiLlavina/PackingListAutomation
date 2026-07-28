# Etiquetas de artículo (EAN13) + menú de entrada — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A partir del excel de pedido de un cliente, generar excels de etiquetas de artículo (40 etiquetas por A4, una hoja por EAN13) para bolsos y cinturones, y añadir una vista `/menu` como portada de las dos familias de salidas.

**Architecture:** Paquete nuevo `service/etiquetasarticulo/`, en paralelo a `service/etiquetas/` (etiquetas de caja), que se queda intacto. Tres primitivas compartidas (`HojaEan`, `AnclajeImagen`, `CodigoBarrasEan13`) viven en `service/etiquetas/`. El flujo web es independiente del envío: su única entrada es el excel de pedido. Las hojas se maquetan con POI desde cero (no hay plantilla `.xlsx`: POI no copia el `pageSetup` al clonar hojas).

**Tech Stack:** Java 17 · Spring Boot 3.5.3 (web + thymeleaf) · Apache POI 5.4.1 (`poi-ooxml`) · barcode4j 2.1 (`org.krysalis.barcode4j.impl.upcean.EAN13Bean`) · JUnit 5.

**Spec:** [docs/superpowers/specs/2026-07-27-etiquetas-articulo-design.md](../specs/2026-07-27-etiquetas-articulo-design.md)

## Global Constraints

- **Idioma**: código en inglés para nombres de clase; **javadoc, comentarios, avisos y UI en español**. Los avisos son texto que lee Jordi.
- **Nunca fallar en silencio, nunca bloquear por datos que un humano puede resolver**: los servicios devuelven DTOs de resultado con `avisos`, no excepciones. Solo se lanza cuando no hay nada útil que generar (no hay hoja `EAN…`, falta una columna obligatoria).
- **Tests**: JUnit 5 puro, servicios instanciados con `new`. Los tests de builder **reabren el `.xlsx` generado con POI** y comprueban celdas reales. Solo `PackingListApplicationTest` levanta contexto Spring.
- **Fixtures del pedido**: se sintetizan con `testutil/PedidoAmiExcel.crear(...)`, no se commitean binarios. Única excepción autorizada: `src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx` (23 KB) para el test end-to-end de la Task 9.
- **Maquetación** (medida del fichero real y verificada con POI en un spike; ninguno de estos valores se cambia sin actualizar `EtiquetasArticuloMaquetacionTest`):
  - anchos de columna POI: `3766, 4425, 621, 3766, 4534, 512, 3766, 4534, 621, 3766, 4534`
  - alto fila 0 = `6.0pt` · fila separadora = `9.95pt` · alto por defecto = `15.0pt`
  - `pageSetup`: `paperSize = 9` (A4), `scale = 74`, `landscape = false`
  - márgenes: izquierdo `0.0`, resto `0.03937007874015748`
  - bloques: fila base 0-based `{1, 9, 17, 25, 33, 41, 49, 57, 65, 73}` (paso 8)
  - columnas izquierdas de etiqueta: `{0, 3, 6, 9}` · 4 × 10 = **40 etiquetas/hoja**
  - anclaje del barcode: `dx = 342901`, `dy = 9525`, `cx = 1463802`, `cy = 647700` EMU, en `filaBase + 2`
- **POI**: `XSSFWorkbook.cloneSheet` **no** copia el `pageSetup` ("Cloning sheets with page setup is not yet supported") — no usarlo. Los estilos se crean **una vez por libro** y se cachean: un fichero llega a 79 hojas.
- **Nombres de fichero**: `AMI CODE BARRE {temporada} {país}.xlsx` (bolsos) · `AMI CODE BARRE ITEMS {temporada} {país} CINTURONES.xlsx` (cinturones, `CINTURONES` siempre al final).
- **Rama de trabajo**: `etiquetas-articulo` (ya creada, con el spec commiteado).

---

### Task 1: `HojaEan` — extraer la lectura del excel de pedido

Refactor con red de seguridad. `AmiPedidoExcel` (etiquetas de caja) ya localiza la hoja `EAN…` y resuelve columnas por cabecera; el flujo nuevo necesita lo mismo. Se extrae antes de duplicarlo.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/HojaEan.java`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java` (borrar los privados `hojaEan`, `columna`, `texto`, `textoPo`; reescribir `desdeBytes`)
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/HojaEanTest.java`
- Red de seguridad (no se toca): `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcelTest.java`

**Interfaces:**
- Consumes: `com.puntotres.packinglist.testutil.PedidoAmiExcel.crear(String nombreHojaEan, Fila... filas)` y `PedidoAmiExcel.Fila(String madeIn, String article, String coloris, String libelle, String taille, Object po)`.
- Produces: `HojaEan implements AutoCloseable` con `static HojaEan abrir(byte[]) throws IOException`, `String nombre()`, `int columna(String titulo)`, `int primeraFilaDatos()`, `int ultimaFila()`, `String texto(int fila, int columna)`.

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/com/puntotres/packinglist/service/etiquetas/HojaEanTest.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class HojaEanTest {

    /** PO numérico (7672) para comprobar que no sale como "7672.0". */
    private static byte[] pedido() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672));
    }

    @Test
    void localizaLaHojaEanAunqueNoSeaLaPrimera() throws Exception {
        // PedidoAmiExcel pone delante una hoja señuelo "BOLSITAS ANTIHUMEDAD".
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertEquals("EAN H26", hoja.nombre());
        }
    }

    @Test
    void resuelveLasColumnasPorElTextoDeLaCabecera() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertEquals(0, hoja.columna("MADE IN"));
            assertEquals(1, hoja.columna("ARTICLE"));
            assertEquals(2, hoja.columna("COLORIS"));
            // "Libellé coloris": se busca por "LIBELL" para no depender del acento.
            assertEquals(3, hoja.columna("LIBELL"));
            assertEquals(5, hoja.columna("TAILLE"));
            assertEquals(6, hoja.columna("PO"));
            assertEquals(8, hoja.columna("EAN13"));
        }
    }

    @Test
    void ean13NoSeConfundeConEan128() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertNotEquals(hoja.columna("EAN13"), hoja.columna("EAN128"));
        }
    }

    @Test
    void losNumerosSeLeenComoEnterosNoComoDecimales() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertEquals("7672", hoja.texto(hoja.primeraFilaDatos(), hoja.columna("PO")));
        }
    }

    @Test
    void unaCeldaQueNoExisteDevuelveCadenaVacia() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertEquals("", hoja.texto(hoja.primeraFilaDatos(), 4));
            assertEquals("", hoja.texto(hoja.ultimaFila() + 5, 1));
        }
    }

    @Test
    void sinHojaEanAvisaConClaridad() {
        byte[] libroSinEan = PedidoAmiExcel.crear("OTRA COSA",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672));
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> HojaEan.abrir(libroSinEan));
        assertTrue(e.getMessage().contains("EAN"));
    }

    @Test
    void columnaQueNoExisteAvisaConElNombreDeLaHoja() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, () -> hoja.columna("PRECIO"));
            assertTrue(e.getMessage().contains("PRECIO"));
            assertTrue(e.getMessage().contains("EAN H26"));
        }
    }
}
```

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
mvn test -Dtest=HojaEanTest
```

Esperado: FAIL de compilación, `HojaEan cannot be resolved to a type`.

- [ ] **Step 3: Escribir `HojaEan`**

`src/main/java/com/puntotres/packinglist/service/etiquetas/HojaEan.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Acceso de bajo nivel a la hoja "EAN ..." del excel de pedido de un
 * cliente, compartido por todo lo que la lee (etiquetas de caja y etiquetas
 * de artículo).
 *
 * La hoja buena es la primera cuyo nombre empieza por "EAN" (la temporada
 * cambia: EAN H26, EAN E27...); el libro trae más hojas (bolsitas, copias
 * por artículo) que se ignoran. Las columnas se localizan por el texto de la
 * cabecera de la primera fila, no por posición, porque el cliente reordena
 * columnas entre temporadas.
 *
 * Es AutoCloseable porque mantiene abierto el Workbook de POI: usar siempre
 * dentro de un try-with-resources y no dejar escapar nada que dependa de él.
 */
public final class HojaEan implements AutoCloseable {

    private final Workbook libro;
    private final Sheet hoja;
    private final Row cabecera;

    private HojaEan(Workbook libro, Sheet hoja) {
        this.libro = libro;
        this.hoja = hoja;
        this.cabecera = hoja.getRow(hoja.getFirstRowNum());
        if (cabecera == null) {
            throw new IllegalArgumentException(
                    "La hoja '" + hoja.getSheetName() + "' del excel de pedido está vacía");
        }
    }

    public static HojaEan abrir(byte[] contenido) throws IOException {
        Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido));
        try {
            return new HojaEan(libro, primeraHojaEan(libro));
        } catch (RuntimeException e) {
            libro.close();
            throw e;
        }
    }

    /** Nombre de la hoja, ej. "EAN H26": de ahí sale la temporada. */
    public String nombre() {
        return hoja.getSheetName().trim();
    }

    /**
     * Índice de la primera columna cuya cabecera empieza por el título dado
     * (indiferente a mayúsculas). Lanza si no está: sin ella no se puede
     * hacer nada útil con el fichero, así que aquí sí se bloquea.
     */
    public int columna(String titulo) {
        String buscado = titulo.toUpperCase(Locale.ROOT);
        for (Cell celda : cabecera) {
            if (texto(celda).trim().toUpperCase(Locale.ROOT).startsWith(buscado)) {
                return celda.getColumnIndex();
            }
        }
        throw new IllegalArgumentException("La hoja '" + nombre()
                + "' del excel de pedido no tiene la columna '" + titulo + "'");
    }

    public int primeraFilaDatos() {
        return hoja.getFirstRowNum() + 1;
    }

    public int ultimaFila() {
        return hoja.getLastRowNum();
    }

    /**
     * Texto de una celda, o "" si la fila o la celda no existen. Los
     * numéricos se leen como enteros: el PO (7672) y la talla (75) están
     * guardados como número y no deben salir como "7672.0".
     */
    public String texto(int fila, int columna) {
        Row f = hoja.getRow(fila);
        return f == null ? "" : texto(f.getCell(columna));
    }

    @Override
    public void close() throws IOException {
        libro.close();
    }

    private static Sheet primeraHojaEan(Workbook libro) {
        for (int i = 0; i < libro.getNumberOfSheets(); i++) {
            if (libro.getSheetName(i).trim().toUpperCase(Locale.ROOT).startsWith("EAN")) {
                return libro.getSheetAt(i);
            }
        }
        throw new IllegalArgumentException(
                "El excel de pedido no tiene ninguna hoja 'EAN ...': ¿es el archivo correcto?");
    }

    private static String texto(Cell celda) {
        if (celda == null) {
            return "";
        }
        return switch (celda.getCellType()) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> String.valueOf((long) celda.getNumericCellValue());
            case FORMULA -> celda.getCachedFormulaResultType() == CellType.STRING
                    ? celda.getStringCellValue() : "";
            default -> "";
        };
    }
}
```

- [ ] **Step 4: Ejecutar el test y comprobar que pasa**

```bash
mvn test -Dtest=HojaEanTest
```

Esperado: PASS, 7 tests.

- [ ] **Step 5: Reescribir `AmiPedidoExcel.desdeBytes` sobre `HojaEan`**

En `AmiPedidoExcel.java`, sustituir el método `desdeBytes` por:

```java
    public static AmiPedidoExcel desdeBytes(byte[] contenido) throws IOException {
        try (HojaEan hoja = HojaEan.abrir(contenido)) {
            int colArticle = hoja.columna("ARTICLE");
            int colColoris = hoja.columna("COLORIS");
            int colLibelle = hoja.columna("LIBELL");
            int colPo = hoja.columna("PO");

            List<FilaCruda> filas = new ArrayList<>();
            for (int i = hoja.primeraFilaDatos(); i <= hoja.ultimaFila(); i++) {
                String article = hoja.texto(i, colArticle);
                String po = hoja.texto(i, colPo);
                if (article.isBlank() || po.isBlank()) {
                    continue;
                }
                String numerico = po.replaceAll("[^0-9]", "");
                if (numerico.isBlank()) {
                    continue;
                }
                String sufijo = po.replaceAll("[0-9\\s]", "").toUpperCase(Locale.ROOT);
                filas.add(new FilaCruda(
                        article.trim().toUpperCase(Locale.ROOT),
                        hoja.texto(i, colColoris).trim(),
                        hoja.texto(i, colLibelle).trim(),
                        String.format("%05d", Long.parseLong(numerico)),
                        sufijo.isBlank() ? null : sufijo));
            }
            return new AmiPedidoExcel(filas);
        }
    }
```

Borrar de esa clase los métodos privados `hojaEan(Workbook)`, `columna(Row, String)`, `texto(Cell)` y `textoPo(Cell)`, y los imports que quedan sin usar: `java.io.ByteArrayInputStream`, `org.apache.poi.ss.usermodel.Cell`, `CellType`, `Row`, `Sheet`, `Workbook`, `org.apache.poi.xssf.usermodel.XSSFWorkbook`. Se conservan `java.io.IOException`, `java.util.ArrayList`, `List`, `Locale`, `Optional`.

Actualizar el javadoc de la clase añadiendo al final:

```java
 * La lectura de bajo nivel (localizar la hoja, resolver columnas, leer
 * celdas) está en HojaEan, compartida con las etiquetas de artículo.
```

Nota: `texto` y `textoPo` tenían **el mismo comportamiento** (los dos leen NUMERIC como `long`), así que quedarse solo con uno no cambia nada. `AmiPedidoExcelTest` lo demuestra.

- [ ] **Step 6: Ejecutar la suite completa**

```bash
mvn test
```

Esperado: PASS. `AmiPedidoExcelTest` en verde es la prueba de que el refactor no cambió comportamiento.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/HojaEan.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/HojaEanTest.java
git commit -m "HojaEan: extraida la lectura de la hoja EAN del pedido

AmiPedidoExcel pasa a usarla sin cambiar su API. texto() y textoPo()
tenian el mismo comportamiento: se queda uno.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: `CodigoBarrasEan13`

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasEan13.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasEan13Test.java`

**Interfaces:**
- Consumes: barcode4j `org.krysalis.barcode4j.impl.upcean.EAN13Bean` y `org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider` (ya en el `pom.xml`, versión 2.1).
- Produces: `static boolean CodigoBarrasEan13.esValido(String ean13)` y `static Optional<byte[]> CodigoBarrasEan13.png(String ean13)`.

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasEan13Test.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class CodigoBarrasEan13Test {

    /** EAN13 real del pedido de H26; su dígito de control (2) es correcto. */
    private static final String VALIDO = "3666598543892";

    @Test
    void generaUnPngApaisadoParaUnEan13Valido() throws Exception {
        byte[] png = CodigoBarrasEan13.png(VALIDO).orElseThrow();

        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                Arrays.copyOfRange(png, 0, 4));
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(imagen);
        // Las barras más los dígitos debajo: siempre más ancho que alto.
        assertTrue(imagen.getWidth() > imagen.getHeight());
    }

    @Test
    void aceptaEspaciosAlrededor() {
        assertTrue(CodigoBarrasEan13.esValido("  " + VALIDO + " "));
        assertTrue(CodigoBarrasEan13.png("  " + VALIDO + " ").isPresent());
    }

    @Test
    void rechazaUnDigitoDeControlIncorrecto() {
        // Mismo código con el último dígito cambiado: 3 en vez de 2.
        assertFalse(CodigoBarrasEan13.esValido("3666598543893"));
        assertTrue(CodigoBarrasEan13.png("3666598543893").isEmpty());
    }

    @Test
    void rechazaLoQueNoEsUnEan13Completo() {
        // 12 dígitos no se completan con un control inventado: se rechaza.
        for (String malo : new String[] {null, "", "   ", "366659854389",
                "36665985438921", "366659854389X"}) {
            assertFalse(CodigoBarrasEan13.esValido(malo), "debería rechazar: " + malo);
            assertTrue(CodigoBarrasEan13.png(malo).isEmpty(), "debería rechazar: " + malo);
        }
    }
}
```

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
mvn test -Dtest=CodigoBarrasEan13Test
```

Esperado: FAIL de compilación, `CodigoBarrasEan13 cannot be resolved`.

- [ ] **Step 3: Escribir `CodigoBarrasEan13`**

`src/main/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasEan13.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.krysalis.barcode4j.impl.upcean.EAN13Bean;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;

/**
 * Genera la imagen PNG de un código de barras EAN-13 con los dígitos
 * legibles debajo, igual que los .gif de los ficheros de etiquetas de
 * artículo del cliente. Hermano de CodigoBarrasCode128, que hace lo mismo
 * para el PO de las etiquetas de caja.
 *
 * Devuelve Optional.empty() en vez de lanzar cuando el código no es un
 * EAN-13 válido: regla del proyecto, la etiqueta se imprime igual sin su
 * código de barras y quien llama acumula un aviso.
 */
public final class CodigoBarrasEan13 {

    private CodigoBarrasEan13() {
    }

    /**
     * ¿Son 13 dígitos con el dígito de control correcto? Se comprueba aquí
     * y no generando la imagen para poder avisar sin pagar el render (el
     * generador valida todas las filas, el builder solo dibuja las buenas).
     */
    public static boolean esValido(String ean13) {
        if (ean13 == null) {
            return false;
        }
        String digitos = ean13.trim();
        if (!digitos.matches("\\d{13}")) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 12; i++) {
            int digito = digitos.charAt(i) - '0';
            suma += (i % 2 == 0) ? digito : digito * 3;
        }
        int control = (10 - suma % 10) % 10;
        return control == digitos.charAt(12) - '0';
    }

    public static Optional<byte[]> png(String ean13) {
        if (!esValido(ean13)) {
            return Optional.empty();
        }
        EAN13Bean codigo = new EAN13Bean();
        codigo.doQuietZone(true);
        BitmapCanvasProvider lienzo =
                new BitmapCanvasProvider(300, BufferedImage.TYPE_BYTE_BINARY, false, 0);
        try {
            codigo.generateBarcode(lienzo, ean13.trim());
            lienzo.finish();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(lienzo.getBufferedImage(), "png", salida);
            return Optional.of(salida.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "No se pudo generar el código de barras de '" + ean13 + "'", e);
        }
    }
}
```

- [ ] **Step 4: Ejecutar el test y comprobar que pasa**

```bash
mvn test -Dtest=CodigoBarrasEan13Test
```

Esperado: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasEan13.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasEan13Test.java
git commit -m "CodigoBarrasEan13: EAN-13 en PNG, con validacion del digito de control

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: `AnclajeImagen` — extraer el anclaje de tamaño fijo

Refactor con red de seguridad. El cálculo del anclaje "oneCellAnchor equivalente" existe como método privado en `AmiEtiquetasExcelBuilder` y lo necesita también el builder nuevo. `ApcEtiquetasExcelBuilder` **no** lo duplica (copia anclajes existentes de su plantilla): no se toca.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/AnclajeImagen.java`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java:152-212`
- Red de seguridad (no se toca): `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilderTest.java`

**Interfaces:**
- Produces: `static XSSFClientAnchor AnclajeImagen.fijo(Sheet hoja, int columna, long dx, int fila, long dy, long cx, long cy)`.

- [ ] **Step 1: Crear `AnclajeImagen` moviendo el código tal cual**

`src/main/java/com/puntotres/packinglist/service/etiquetas/AnclajeImagen.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;

/**
 * Anclaje de imagen de tamaño fijo, equivalente al oneCellAnchor que
 * escribe Excel: la imagen arranca en (columna, fila) más un desplazamiento
 * en EMU y ocupa (cx, cy) EMU, sin estirarse si cambian filas o columnas.
 *
 * POI no expone oneCellAnchor desde createPicture, así que se construye un
 * anclaje de dos celdas cuyo extremo se calcula recorriendo los anchos y
 * altos reales de la hoja, con AnchorType.MOVE_DONT_RESIZE.
 *
 * Lo usan los dos builders de etiquetas de AMI (caja y artículo).
 */
public final class AnclajeImagen {

    private AnclajeImagen() {
    }

    public static XSSFClientAnchor fijo(Sheet hoja, int columna, long dx,
                                        int fila, long dy, long cx, long cy) {
        int col2 = columna;
        long xRestante = dx + cx;
        while (xRestante > anchoColumnaEmu(hoja, col2)) {
            xRestante -= anchoColumnaEmu(hoja, col2);
            col2++;
        }
        int fila2 = fila;
        long yRestante = dy + cy;
        while (yRestante > altoFilaEmu(hoja, fila2)) {
            yRestante -= altoFilaEmu(hoja, fila2);
            fila2++;
        }
        XSSFClientAnchor ancla = new XSSFClientAnchor((int) dx, (int) dy,
                (int) xRestante, (int) yRestante, columna, fila, col2, fila2);
        ancla.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
        return ancla;
    }

    private static long anchoColumnaEmu(Sheet hoja, int columna) {
        return Units.columnWidthToEMU(hoja.getColumnWidth(columna));
    }

    private static long altoFilaEmu(Sheet hoja, int fila) {
        float puntos = hoja.getRow(fila) != null
                ? hoja.getRow(fila).getHeightInPoints()
                : hoja.getDefaultRowHeightInPoints();
        return Units.toEMU(puntos);
    }
}
```

Es una extracción **literal**: mismo cuerpo, mismo comportamiento, solo cambia `XSSFSheet` por `Sheet` en la firma (suficiente para lo que usa). No se le añaden guardas nuevas para que el diff sea trivial de revisar.

- [ ] **Step 2: Hacer que `AmiEtiquetasExcelBuilder` la use**

En `AmiEtiquetasExcelBuilder.java`:

1. Borrar los tres métodos privados del final: `anclaje(XSSFSheet, int, long, int, long, long, long)`, `anchoColumnaEmu`, `altoFilaEmu`, junto con el javadoc de `anclaje`.
2. En `insertarImagenes`, cambiar las dos llamadas `anclaje(hoja, ...)` por `AnclajeImagen.fijo(hoja, ...)`:

```java
                dibujo.createPicture(AnclajeImagen.fijo(hoja, AmiEtiquetaLayout.COL_BARCODE,
                        layout.dxBarcode(), base + offset + layout.filaBarcode(),
                        layout.dyBarcode(), layout.cxBarcode(), layout.cyBarcode()), indice);
```

```java
                dibujo.createPicture(AnclajeImagen.fijo(hoja, AmiEtiquetaLayout.COL_BARCODE,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_DX,
                        base + offset + AmiEtiquetaLayout.JAPAN_DIRECCION_FILA,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_DY,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_CX,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_CY), indice);
```

3. Borrar los imports que quedan sin usar: `org.apache.poi.ss.usermodel.ClientAnchor`, `org.apache.poi.util.Units`, `org.apache.poi.xssf.usermodel.XSSFClientAnchor`.

- [ ] **Step 3: Ejecutar la suite completa**

```bash
mvn test
```

Esperado: PASS. `AmiEtiquetasExcelBuilderTest` en verde demuestra que los anclajes salen idénticos.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AnclajeImagen.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java
git commit -m "AnclajeImagen: extraido el anclaje de tamano fijo del builder de cajas

Extraccion literal, para reusarlo en las etiquetas de articulo.
AmiEtiquetasExcelBuilderTest es la red de seguridad.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: `FilaEan` + `AmiCatalogoEan` — leer todo el pedido

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/FilaEan.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiCatalogoEan.java`
- Modify: `src/test/java/com/puntotres/packinglist/testutil/PedidoAmiExcel.java` (añadir `ean13` a `Fila`)
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiCatalogoEanTest.java`

**Interfaces:**
- Consumes: `HojaEan` (Task 1).
- Produces:
  - `record FilaEan(String madeIn, String article, String coloris, String libelle, String taille, String poNumerico, String poSufijo, String ean13)` con `String poCompacto()` y `String colorCompleto()`.
  - `AmiCatalogoEan` con `static AmiCatalogoEan desdeBytes(byte[]) throws IOException`, `List<FilaEan> filas()`, `List<String> avisos()`, `Optional<String> temporada()`.
  - `PedidoAmiExcel.Fila` gana un 7º componente `String ean13`, **manteniendo** el constructor de 6 argumentos.

- [ ] **Step 1: Añadir `ean13` al fixture sin romper sus usos**

En `src/test/java/com/puntotres/packinglist/testutil/PedidoAmiExcel.java`, sustituir el record `Fila` por:

```java
    /**
     * po: String ("07704 CH") o Number (7672 = PO de France sin sufijo).
     * ean13: null para los tests que no lo necesitan (etiquetas de caja).
     */
    public record Fila(String madeIn, String article, String coloris,
                       String libelle, String taille, Object po, String ean13) {

        /** Sin EAN13: firma que ya usaban los tests de etiquetas de caja. */
        public Fila(String madeIn, String article, String coloris,
                    String libelle, String taille, Object po) {
            this(madeIn, article, coloris, libelle, taille, po, null);
        }
    }
```

y en `crear(...)`, justo después del bloque que escribe la celda 6 (el PO), añadir:

```java
                if (fila.ean13() != null) {
                    f.createCell(8).setCellValue(fila.ean13());
                }
```

- [ ] **Step 2: Comprobar que los tests existentes siguen compilando y en verde**

```bash
mvn test
```

Esperado: PASS. El constructor de 6 argumentos mantiene `AmiPedidoExcelTest`, `AmiEtiquetasGeneradorTest` y `HojaEanTest` sin tocar.

- [ ] **Step 3: Escribir el test que falla**

`src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiCatalogoEanTest.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class AmiCatalogoEanTest {

    @Test
    void leeTodasLasColumnasDeUnaFila() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR/ARGENT VIBRE",
                        "75", "07704 CH", "3666598543892")));

        FilaEan fila = catalogo.filas().get(0);
        assertEquals("SPAIN", fila.madeIn());
        assertEquals("UBL029.AL0104", fila.article());
        assertEquals("0014", fila.coloris());
        assertEquals("NOIR/ARGENT VIBRE", fila.libelle());
        assertEquals("75", fila.taille());
        assertEquals("07704", fila.poNumerico());
        assertEquals("CH", fila.poSufijo());
        assertEquals("3666598543892", fila.ean13());
    }

    @Test
    void elPoNumericoLlevaPaddingYNoTieneSufijo() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672, "3666598543892")));

        FilaEan fila = catalogo.filas().get(0);
        assertEquals("07672", fila.poNumerico());
        assertNull(fila.poSufijo());
        assertEquals("07672", fila.poCompacto());
    }

    @Test
    void elPoCompactoPegaElSufijoSinEspacio() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07703 CH",
                        "3666598543892")));

        assertEquals("07703CH", catalogo.filas().get(0).poCompacto());
    }

    @Test
    void elColorCompletoEsColorisMasLibelle() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "A236", "TRUFFLE", "U", 7672,
                        "3666598543892")));

        assertEquals("A236 TRUFFLE", catalogo.filas().get(0).colorCompleto());
    }

    @Test
    void unaFilaSinArticuloSeOmiteConAviso() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "", "221", "BLACK", "U", 7672, "3666598543892"),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672,
                        "3666598543892")));

        assertEquals(1, catalogo.filas().size());
        assertEquals(1, catalogo.avisos().size());
        assertTrue(catalogo.avisos().get(0).contains("ARTICLE"));
    }

    @Test
    void unaFilaSinPoSeOmiteConAviso() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "", "3666598543892")));

        assertTrue(catalogo.filas().isEmpty());
        assertEquals(1, catalogo.avisos().size());
        assertTrue(catalogo.avisos().get(0).contains("PO"));
    }

    @Test
    void laTemporadaSaleDelNombreDeLaHoja() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672, "3666598543892"));

        assertEquals("H26", AmiCatalogoEan.desdeBytes(pedido).temporada().orElseThrow());
    }

    @Test
    void unaHojaLlamadaSoloEanNoDaTemporada() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672, "3666598543892"));

        assertTrue(AmiCatalogoEan.desdeBytes(pedido).temporada().isEmpty());
    }

    @Test
    void elEan13VacioLlegaComoCadenaVaciaNoComoNull() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672)));

        assertEquals("", catalogo.filas().get(0).ean13());
    }

    @Test
    void conservaElOrdenDeAparicionDelExcel() throws Exception {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR", "75", 7704, "3666598543892"),
                new Fila("MOROCCO", "ULL163.AL0052", "221", "BLACK", "U", 7672,
                        "3666598543892")));

        List<FilaEan> filas = catalogo.filas();
        assertEquals("UBL029.AL0104", filas.get(0).article());
        assertEquals("ULL163.AL0052", filas.get(1).article());
    }
}
```

- [ ] **Step 4: Ejecutar el test y comprobar que falla**

```bash
mvn test -Dtest=AmiCatalogoEanTest
```

Esperado: FAIL de compilación, `AmiCatalogoEan cannot be resolved`.

- [ ] **Step 5: Escribir `FilaEan`**

`src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/FilaEan.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

/**
 * Una fila del excel de pedido del cliente, tal cual: calco literal, sin
 * interpretar. Quién es bolso y quién cinturón, cómo se agrupan y cómo se
 * nombran las hojas es cosa del generador del cliente.
 *
 * poNumerico: la parte numérica del PO con padding a 5 dígitos ("07704").
 * poSufijo: el sufijo de destinación ("CH", "JP") o null (France).
 * ean13: tal como viene, sin validar; "" si la celda estaba vacía.
 */
public record FilaEan(String madeIn, String article, String coloris, String libelle,
                      String taille, String poNumerico, String poSufijo, String ean13) {

    /** "07704CH" / "07714": el PO tal como aparece en el nombre de hoja. */
    public String poCompacto() {
        return poSufijo == null ? poNumerico : poNumerico + poSufijo;
    }

    /** "A236 TRUFFLE", o solo el código si la fila no trae libellé. */
    public String colorCompleto() {
        return libelle.isBlank() ? coloris : coloris + " " + libelle;
    }
}
```

- [ ] **Step 6: Escribir `AmiCatalogoEan`**

`src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiCatalogoEan.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.puntotres.packinglist.service.etiquetas.HojaEan;

/**
 * Lee el excel de pedido de AMI y devuelve TODAS sus filas, sin
 * interpretarlas.
 *
 * Comparte con AmiPedidoExcel (etiquetas de caja) la lectura de bajo nivel
 * vía HojaEan, pero no su API: allí interesa buscar el PO de una referencia
 * concreta, aquí interesan todas las filas y el EAN13.
 *
 * Una fila del todo vacía se ignora en silencio (los excels del cliente
 * traen filas sueltas al final); una fila con datos pero sin ARTICLE o sin
 * PO se omite CON aviso, porque probablemente sea un error del fichero.
 */
public final class AmiCatalogoEan {

    private final String nombreHoja;
    private final List<FilaEan> filas;
    private final List<String> avisos;

    private AmiCatalogoEan(String nombreHoja, List<FilaEan> filas, List<String> avisos) {
        this.nombreHoja = nombreHoja;
        this.filas = List.copyOf(filas);
        this.avisos = List.copyOf(avisos);
    }

    public static AmiCatalogoEan desdeBytes(byte[] contenido) throws IOException {
        try (HojaEan hoja = HojaEan.abrir(contenido)) {
            int colMadeIn = hoja.columna("MADE IN");
            int colArticle = hoja.columna("ARTICLE");
            int colColoris = hoja.columna("COLORIS");
            int colLibelle = hoja.columna("LIBELL");
            int colTaille = hoja.columna("TAILLE");
            int colPo = hoja.columna("PO");
            int colEan13 = hoja.columna("EAN13");

            List<FilaEan> filas = new ArrayList<>();
            List<String> avisos = new ArrayList<>();
            for (int i = hoja.primeraFilaDatos(); i <= hoja.ultimaFila(); i++) {
                String article = hoja.texto(i, colArticle).trim().toUpperCase(Locale.ROOT);
                String po = hoja.texto(i, colPo).trim();
                if (article.isBlank() && po.isBlank()) {
                    continue;
                }
                String numerico = po.replaceAll("[^0-9]", "");
                if (article.isBlank() || numerico.isBlank()) {
                    // La fila del excel se cuenta desde 1, no desde 0.
                    avisos.add("Fila " + (i + 1) + " del pedido sin "
                            + (article.isBlank() ? "ARTICLE" : "PO") + ": se omite");
                    continue;
                }
                String sufijo = po.replaceAll("[0-9\\s]", "").toUpperCase(Locale.ROOT);
                filas.add(new FilaEan(
                        hoja.texto(i, colMadeIn).trim().toUpperCase(Locale.ROOT),
                        article,
                        hoja.texto(i, colColoris).trim(),
                        hoja.texto(i, colLibelle).trim(),
                        hoja.texto(i, colTaille).trim(),
                        String.format("%05d", Long.parseLong(numerico)),
                        sufijo.isBlank() ? null : sufijo,
                        hoja.texto(i, colEan13).trim()));
            }
            return new AmiCatalogoEan(hoja.nombre(), filas, avisos);
        }
    }

    public List<FilaEan> filas() {
        return filas;
    }

    public List<String> avisos() {
        return avisos;
    }

    /**
     * "EAN H26" -> "H26", para el nombre de los ficheros generados. Vacío si
     * la hoja se llama solo "EAN": entonces la temporada la pone el usuario
     * desde la pantalla.
     */
    public Optional<String> temporada() {
        String resto = nombreHoja.substring("EAN".length()).trim();
        return resto.isBlank() ? Optional.empty() : Optional.of(resto.toUpperCase(Locale.ROOT));
    }
}
```

`substring("EAN".length())` es seguro porque `HojaEan` garantiza que el nombre empieza por "EAN".

- [ ] **Step 7: Ejecutar el test y comprobar que pasa**

```bash
mvn test -Dtest=AmiCatalogoEanTest
```

Esperado: PASS, 10 tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/FilaEan.java \
        src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiCatalogoEan.java \
        src/test/java/com/puntotres/packinglist/testutil/PedidoAmiExcel.java \
        src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiCatalogoEanTest.java
git commit -m "AmiCatalogoEan: todas las filas del pedido, con EAN13 y Made in

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: DTOs del flujo y la interfaz de cliente

Sin lógica: define el vocabulario que usan las tareas 6 a 10. Un solo commit.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetaArticulo.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/HojaEtiquetas.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/ExcelEtiquetasArticulo.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/ResultadoEtiquetasArticulo.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/GeneradorEtiquetasArticuloCliente.java`

**Interfaces:**
- Produces:
  - `record EtiquetaArticulo(String referencia, String talla, String color, String pedido, String ean13)`
  - `record HojaEtiquetas(String nombreHoja, EtiquetaArticulo etiqueta)`
  - `record ExcelEtiquetasArticulo(String descripcion, String nombreFichero, byte[] contenido)`
  - `ResultadoEtiquetasArticulo` con `List<ExcelEtiquetasArticulo> getExcels()` y `List<String> getAvisos()`
  - `interface GeneradorEtiquetasArticuloCliente` con `String claveCliente()`, `String tituloCampoPedido()`, `ResultadoEtiquetasArticulo generar(byte[] excelPedido, String temporadaPorDefecto) throws IOException`

- [ ] **Step 1: Escribir los cinco tipos**

`EtiquetaArticulo.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

/**
 * Las cinco partes de una etiqueta de artículo, YA formateadas tal como van
 * a la celda o a la imagen ("Size: U", "Cde: 07714", "A236 TRUFFLE").
 *
 * Formatear aquí, en el generador del cliente, y no en el builder, es lo que
 * permite que el builder no sepa nada del cliente ni del excel de pedido.
 *
 * ean13 == null: la fila no traía un EAN13 válido. La etiqueta se imprime
 * igual, sin código de barras, y el generador ya ha dejado su aviso.
 */
public record EtiquetaArticulo(String referencia, String talla, String color,
                               String pedido, String ean13) {
}
```

`HojaEtiquetas.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

/**
 * Una hoja del excel de etiquetas: su nombre y la etiqueta que se repite en
 * ella.
 *
 * UNA etiqueta, no una lista: las 40 de la hoja son idénticas (una hoja =
 * una fila del pedido = un EAN13) y el builder es quien las repite en la
 * rejilla. El nombre llega ya saneado y recortado a 31 caracteres.
 */
public record HojaEtiquetas(String nombreHoja, EtiquetaArticulo etiqueta) {
}
```

`ExcelEtiquetasArticulo.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

/**
 * Un excel de etiquetas de artículo generado.
 *
 * No se reutiliza ExcelGenerado: su vocabulario es de packing list (destino,
 * referencia, color, cajasPendientes) y aquí no aplica ninguno de sus
 * campos. descripcion es el texto que ve el usuario en la pantalla de
 * resultados, ej. "Bolsos · MOROCCO · 46 hojas".
 */
public record ExcelEtiquetasArticulo(String descripcion, String nombreFichero,
                                     byte[] contenido) {
}
```

`ResultadoEtiquetasArticulo.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import java.util.ArrayList;
import java.util.List;

/**
 * Salida de la generación de etiquetas de artículo: un excel por grupo
 * (tipo × país) con filas, más los avisos acumulados (filas del pedido sin
 * ARTICLE, EAN13 inválidos, tallas vacías...).
 *
 * Nunca se lanza excepción por datos resolubles por un humano: se avisa y se
 * genera lo que se pueda, igual que ResultadoEtiquetas.
 */
public class ResultadoEtiquetasArticulo {

    private final List<ExcelEtiquetasArticulo> excels = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();

    public List<ExcelEtiquetasArticulo> getExcels() {
        return excels;
    }

    public List<String> getAvisos() {
        return avisos;
    }
}
```

`GeneradorEtiquetasArticuloCliente.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import java.io.IOException;

/**
 * Estrategia de generación de etiquetas de ARTÍCULO de un cliente concreto
 * (las que se enganchan al bolso o al cinturón), análoga a
 * GeneradorEtiquetasCliente para las etiquetas de caja.
 *
 * Cada implementación sabe leer el excel de pedido de su cliente, clasificar
 * sus referencias (bolso / cinturón), agruparlas en ficheros y nombrar hojas
 * y ficheros. La maquetación de la etiqueta la pone
 * EtiquetasArticuloExcelBuilder, que es común: un cliente nuevo con la misma
 * rejilla 4 × 10 lo reutiliza y solo aporta esta clase.
 *
 * Un cliente sin implementación simplemente no tiene esta funcionalidad
 * todavía: la pantalla lo marca "en desarrollo".
 */
public interface GeneradorEtiquetasArticuloCliente {

    /** Clave del cliente en el catálogo de application.yml (ej. "AMI"). */
    String claveCliente();

    /** Rótulo del input de archivo, ej. "Introducir excel del pedido de AMI". */
    String tituloCampoPedido();

    /**
     * Genera un excel por grupo con filas.
     *
     * temporadaPorDefecto: la que ha escrito el usuario en la pantalla. Solo
     * se usa si no se puede deducir del propio excel (el nombre de la hoja
     * "EAN H26"), que es la fuente preferente.
     */
    ResultadoEtiquetasArticulo generar(byte[] excelPedido, String temporadaPorDefecto)
            throws IOException;
}
```

- [ ] **Step 2: Comprobar que compila**

```bash
mvn -q test-compile
```

Esperado: sin errores. No hay test propio: son tipos sin comportamiento, los ejercitan las tareas 6 y 7.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/
git commit -m "DTOs e interfaz del flujo de etiquetas de articulo

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: `EtiquetasArticuloExcelBuilder` — maquetar la rejilla 4 × 10

El corazón. No sabe nada de AMI ni del excel de pedido: recibe hojas con la etiqueta ya formateada y produce los bytes del `.xlsx`.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloExcelBuilder.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloExcelBuilderTest.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloMaquetacionTest.java`

**Interfaces:**
- Consumes: `HojaEtiquetas`, `EtiquetaArticulo` (Task 5); `AnclajeImagen.fijo(...)` (Task 3); `CodigoBarrasEan13.png(...)` (Task 2).
- Produces: `EtiquetasArticuloExcelBuilder` (`@Service`) con `byte[] generar(List<HojaEtiquetas> hojas) throws IOException`; constantes visibles al paquete `ANCHOS_COLUMNA`, `COLUMNAS_IZQUIERDA`, `BLOQUES`, `FILAS_POR_BLOQUE`, `PRIMERA_FILA_BLOQUE`, `ETIQUETAS_POR_HOJA`, `ALTO_MARGEN_SUPERIOR`, `ALTO_SEPARADORA`, `ALTO_DEFECTO`, `ESCALA`, `MARGEN_IZQUIERDO`, `MARGEN`; `static int filaBase(int bloque)`, `static int cuerpoPara(String)`, `static String nombreUnico(Set<String>, String)`.

- [ ] **Step 1: Escribir el test de contenido que falla**

`src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloExcelBuilderTest.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class EtiquetasArticuloExcelBuilderTest {

    private final EtiquetasArticuloExcelBuilder builder = new EtiquetasArticuloExcelBuilder();

    private static final EtiquetaArticulo BOLSO = new EtiquetaArticulo(
            "USL738.AL0137", "Size: U", "A236 TRUFFLE", "Cde: 07714", "3666598543892");

    private static XSSFWorkbook reabrir(byte[] xlsx) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    @Test
    void unaHojaPorEtiquetaConSuNombre() throws Exception {
        byte[] xlsx = builder.generar(List.of(
                new HojaEtiquetas("USL738.AL0137 TRUFFLE 07714CH", BOLSO),
                new HojaEtiquetas("ULL754.AL0218 A328 07683", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            assertEquals(2, libro.getNumberOfSheets());
            assertEquals("USL738.AL0137 TRUFFLE 07714CH", libro.getSheetName(0));
            assertEquals("ULL754.AL0218 A328 07683", libro.getSheetName(1));
        }
    }

    @Test
    void escribeLasCuatroCeldasDelPrimerBloque() throws Exception {
        byte[] xlsx = builder.generar(List.of(new HojaEtiquetas("HOJA", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            int base = EtiquetasArticuloExcelBuilder.filaBase(0);   // fila 0-based 1 = "2" en Excel
            assertEquals("USL738.AL0137", hoja.getRow(base).getCell(0).getStringCellValue());
            assertEquals("Size: U", hoja.getRow(base).getCell(1).getStringCellValue());
            assertEquals("A236 TRUFFLE", hoja.getRow(base + 1).getCell(0).getStringCellValue());
            assertEquals("Cde: 07714", hoja.getRow(base + 1).getCell(1).getStringCellValue());
        }
    }

    @Test
    void repiteLaEtiquetaEnLasCuatroColumnasYEnElUltimoBloque() throws Exception {
        byte[] xlsx = builder.generar(List.of(new HojaEtiquetas("HOJA", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // Último bloque: fila base 0-based 73.
            int base = EtiquetasArticuloExcelBuilder.filaBase(
                    EtiquetasArticuloExcelBuilder.BLOQUES - 1);
            assertEquals(73, base);
            for (int izquierda : EtiquetasArticuloExcelBuilder.COLUMNAS_IZQUIERDA) {
                assertEquals("USL738.AL0137",
                        hoja.getRow(base).getCell(izquierda).getStringCellValue());
                assertEquals("Size: U",
                        hoja.getRow(base).getCell(izquierda + 1).getStringCellValue());
                assertEquals("A236 TRUFFLE",
                        hoja.getRow(base + 1).getCell(izquierda).getStringCellValue());
                assertEquals("Cde: 07714",
                        hoja.getRow(base + 1).getCell(izquierda + 1).getStringCellValue());
            }
        }
    }

    @Test
    void cuarentaAnclajesPeroUnaSolaImagenPorHoja() throws Exception {
        byte[] xlsx = builder.generar(List.of(new HojaEtiquetas("HOJA", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            assertEquals(40, EtiquetasArticuloExcelBuilder.ETIQUETAS_POR_HOJA);
            // Los bytes del código de barras se guardan UNA vez, como en los
            // ficheros del cliente: una imagen y 40 anclajes que la reusan.
            assertEquals(1, libro.getAllPictures().size());
            XSSFDrawing dibujo = libro.getSheetAt(0).getDrawingPatriarch();
            assertEquals(40, dibujo.getShapes().size());
        }
    }

    @Test
    void unaEtiquetaSinEan13SeGeneraSinCodigoDeBarras() throws Exception {
        EtiquetaArticulo sinCodigo = new EtiquetaArticulo(
                "USL738.AL0137", "Size: U", "A236 TRUFFLE", "Cde: 07714", null);

        byte[] xlsx = builder.generar(List.of(new HojaEtiquetas("HOJA", sinCodigo)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            assertTrue(libro.getAllPictures().isEmpty());
            // Los textos sí están: la hoja es útil aunque falte el código.
            int base = EtiquetasArticuloExcelBuilder.filaBase(0);
            assertEquals("USL738.AL0137",
                    libro.getSheetAt(0).getRow(base).getCell(0).getStringCellValue());
        }
    }

    @Test
    void elCuerpoDeLaCeldaDeColorSeReduceSegunLaLongitud() throws Exception {
        // 10,5pt hasta 14 caracteres; 9pt de 15 a 17; 8pt a partir de 18.
        assertEquals(210, EtiquetasArticuloExcelBuilder.cuerpoPara("001 BLACK"));
        assertEquals(210, EtiquetasArticuloExcelBuilder.cuerpoPara("2221 CHOCOLATE"));
        assertEquals(180, EtiquetasArticuloExcelBuilder.cuerpoPara("221 DARK COFFEE"));
        assertEquals(180, EtiquetasArticuloExcelBuilder.cuerpoPara("A184 MASTIC BEIGE"));
        assertEquals(160, EtiquetasArticuloExcelBuilder.cuerpoPara("A328 SAND-CHOCOLATE"));

        EtiquetaArticulo corta = new EtiquetaArticulo("REF", "Size: U", "001 BLACK", "Cde: 1", null);
        EtiquetaArticulo larga = new EtiquetaArticulo("REF", "Size: U",
                "A328 SAND-CHOCOLATE", "Cde: 1", null);
        byte[] xlsx = builder.generar(List.of(
                new HojaEtiquetas("CORTA", corta), new HojaEtiquetas("LARGA", larga)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            int base = EtiquetasArticuloExcelBuilder.filaBase(0);
            short cuerpoCorta = libro.getSheetAt(0).getRow(base + 1).getCell(0)
                    .getCellStyle().getFont().getFontHeight();
            short cuerpoLarga = libro.getSheetAt(1).getRow(base + 1).getCell(0)
                    .getCellStyle().getFont().getFontHeight();
            assertEquals(210, cuerpoCorta);
            assertEquals(160, cuerpoLarga);
            assertNotEquals(cuerpoCorta, cuerpoLarga);
        }
    }

    @Test
    void laTallaYElPedidoVanAlineadosALaDerecha() throws Exception {
        byte[] xlsx = builder.generar(List.of(new HojaEtiquetas("HOJA", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            int base = EtiquetasArticuloExcelBuilder.filaBase(0);
            assertEquals(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT,
                    hoja.getRow(base).getCell(1).getCellStyle().getAlignment());
            assertEquals(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT,
                    hoja.getRow(base + 1).getCell(1).getCellStyle().getAlignment());
        }
    }

    @Test
    void dosHojasConElMismoNombreNoRompenLaGeneracion() throws Exception {
        byte[] xlsx = builder.generar(List.of(
                new HojaEtiquetas("MISMO NOMBRE", BOLSO),
                new HojaEtiquetas("MISMO NOMBRE", BOLSO)));

        try (XSSFWorkbook libro = reabrir(xlsx)) {
            assertEquals(2, libro.getNumberOfSheets());
            assertEquals("MISMO NOMBRE", libro.getSheetName(0));
            assertEquals("MISMO NOMBRE-2", libro.getSheetName(1));
        }
    }

    @Test
    void elSufijoDeDesempateRecortaCuandoElNombreYaMideTreintaYUno() {
        java.util.Set<String> usados = new java.util.HashSet<>();
        String largo = "A".repeat(31);
        assertEquals(largo, EtiquetasArticuloExcelBuilder.nombreUnico(usados, largo));
        String segundo = EtiquetasArticuloExcelBuilder.nombreUnico(usados, largo);
        assertEquals(31, segundo.length());
        assertTrue(segundo.endsWith("-2"));
    }

    @Test
    void sinHojasNoSeGeneraNada() {
        // Excel no abre un libro sin hojas: el generador nunca debe llamar
        // al builder con un grupo vacío, y si lo hace se entera.
        assertThrows(IllegalArgumentException.class, () -> builder.generar(List.of()));
    }
}
```

- [ ] **Step 2: Escribir el test de maquetación que falla**

Este es el que **ancla las constantes al fichero real del cliente**, ahora que no hay plantilla de la que heredarlas.

`src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloMaquetacionTest.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Compara la maquetación generada contra la del fichero REAL del cliente.
 *
 * Sin plantilla .xlsx, las medidas viven como constantes en el builder: este
 * test es lo que impide que deriven. Si alguien cambia un ancho, un alto o
 * el pageSetup, cae aquí señalando el fichero del cliente como verdad.
 */
class EtiquetasArticuloMaquetacionTest {

    private static final Path FICHERO_REAL =
            Path.of("docs/Etiquetas para etiquetas/AMI CODE BARRE H26 MOROCCO.xlsx");

    @Test
    void laMaquetacionCoincideConElFicheroRealDelCliente() throws Exception {
        assertTrue(Files.exists(FICHERO_REAL),
                "falta el fichero de referencia del cliente: " + FICHERO_REAL);

        byte[] generado = new EtiquetasArticuloExcelBuilder().generar(List.of(
                new HojaEtiquetas("HOJA", new EtiquetaArticulo(
                        "USL738.AL0137", "Size: U", "A236 TRUFFLE", "Cde: 07714",
                        "3666598543892"))));

        try (InputStream real = Files.newInputStream(FICHERO_REAL);
             XSSFWorkbook libroReal = new XSSFWorkbook(real);
             XSSFWorkbook libroGenerado = new XSSFWorkbook(new ByteArrayInputStream(generado))) {

            XSSFSheet esperada = libroReal.getSheetAt(0);
            XSSFSheet obtenida = libroGenerado.getSheetAt(0);

            for (int columna = 0; columna < 11; columna++) {
                assertEquals(esperada.getColumnWidth(columna), obtenida.getColumnWidth(columna),
                        "ancho de la columna " + columna);
            }

            assertEquals(esperada.getRow(0).getHeightInPoints(),
                    obtenida.getRow(0).getHeightInPoints(), 0.001f, "alto de la fila 0");
            assertEquals(esperada.getDefaultRowHeightInPoints(),
                    obtenida.getDefaultRowHeightInPoints(), 0.001f, "alto por defecto");

            // Filas separadoras: la del último bloque (índice 80) no existe en
            // el fichero real, que no tiene celdas más allá de la fila 74.
            for (int bloque = 0; bloque < EtiquetasArticuloExcelBuilder.BLOQUES - 1; bloque++) {
                int separadora = EtiquetasArticuloExcelBuilder.filaBase(bloque)
                        + EtiquetasArticuloExcelBuilder.FILAS_POR_BLOQUE - 1;
                assertEquals(esperada.getRow(separadora).getHeightInPoints(),
                        obtenida.getRow(separadora).getHeightInPoints(), 0.001f,
                        "alto de la fila separadora " + separadora);
            }

            assertEquals(esperada.getPrintSetup().getScale(),
                    obtenida.getPrintSetup().getScale(), "escala de impresión");
            assertEquals(esperada.getPrintSetup().getPaperSize(),
                    obtenida.getPrintSetup().getPaperSize(), "tamaño de papel");
            assertEquals(esperada.getPrintSetup().getLandscape(),
                    obtenida.getPrintSetup().getLandscape(), "orientación");

            for (PageMargin margen : new PageMargin[] {
                    PageMargin.LEFT, PageMargin.RIGHT, PageMargin.TOP, PageMargin.BOTTOM}) {
                assertEquals(esperada.getMargin(margen), obtenida.getMargin(margen), 1e-9,
                        "margen " + margen);
            }
        }
    }

    @Test
    void losValoresEsperadosSonLosDelSpike() throws Exception {
        // Duplicado deliberado: si el fichero de docs/ desaparece o cambia,
        // estos valores siguen documentando qué maquetación se espera.
        byte[] generado = new EtiquetasArticuloExcelBuilder().generar(List.of(
                new HojaEtiquetas("HOJA", new EtiquetaArticulo(
                        "REF", "Size: U", "001 BLACK", "Cde: 1", null))));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(generado))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            int[] anchos = {3766, 4425, 621, 3766, 4534, 512, 3766, 4534, 621, 3766, 4534};
            for (int columna = 0; columna < anchos.length; columna++) {
                assertEquals(anchos[columna], hoja.getColumnWidth(columna),
                        "ancho de la columna " + columna);
            }
            assertEquals(6.0f, hoja.getRow(0).getHeightInPoints(), 0.001f);
            assertEquals(9.95f, hoja.getRow(8).getHeightInPoints(), 0.001f);
            assertEquals(15.0f, hoja.getDefaultRowHeightInPoints(), 0.001f);
            assertEquals(74, hoja.getPrintSetup().getScale());
            assertEquals(9, hoja.getPrintSetup().getPaperSize());
            assertEquals(false, hoja.getPrintSetup().getLandscape());
            assertEquals(0.0, hoja.getMargin(PageMargin.LEFT), 1e-9);
            assertEquals(0.03937007874015748, hoja.getMargin(PageMargin.RIGHT), 1e-9);
            assertEquals(0.03937007874015748, hoja.getMargin(PageMargin.TOP), 1e-9);
            assertEquals(0.03937007874015748, hoja.getMargin(PageMargin.BOTTOM), 1e-9);
        }
    }
}
```

- [ ] **Step 3: Ejecutar los dos tests y comprobar que fallan**

```bash
mvn test -Dtest='EtiquetasArticulo*Test'
```

Esperado: FAIL de compilación, `EtiquetasArticuloExcelBuilder cannot be resolved`.

- [ ] **Step 4: Escribir `EtiquetasArticuloExcelBuilder`**

`src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloExcelBuilder.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPrintSetup;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.puntotres.packinglist.service.etiquetas.AnclajeImagen;
import com.puntotres.packinglist.service.etiquetas.CodigoBarrasEan13;

/**
 * Escribe un excel de etiquetas de artículo: una hoja por HojaEtiquetas y,
 * dentro de cada hoja, la MISMA etiqueta repetida en una rejilla de 4 × 10
 * que cabe justa en un A4 para imprimir, recortar y enganchar.
 *
 * La maquetación (anchos, altos, pageSetup, márgenes y anclaje de la imagen)
 * está medida del fichero real del cliente "AMI CODE BARRE H26 MOROCCO.xlsx"
 * y la fija EtiquetasArticuloMaquetacionTest, que compara lo generado contra
 * ese fichero. NO hay plantilla .xlsx: POI no copia el pageSetup al clonar
 * hojas ("Cloning sheets with page setup is not yet supported"), que es justo
 * lo único que interesaba heredar, así que heredarla no servía de nada.
 *
 * No es específico de AMI: cualquier cliente con esta misma rejilla lo
 * reutiliza pasándole sus HojaEtiquetas ya formateadas.
 */
@Service
public class EtiquetasArticuloExcelBuilder {

    /** Anchos de columna en unidades POI (caracteres × 256). */
    static final int[] ANCHOS_COLUMNA =
            {3766, 4425, 621, 3766, 4534, 512, 3766, 4534, 621, 3766, 4534};

    /**
     * Columna izquierda de cada par de columnas de etiqueta. La derecha es
     * la siguiente; las columnas 2, 5 y 8 son separadores estrechos.
     */
    static final int[] COLUMNAS_IZQUIERDA = {0, 3, 6, 9};

    static final int BLOQUES = 10;
    static final int FILAS_POR_BLOQUE = 8;
    /** Fila 0-based del primer bloque: la 0 es el margen superior. */
    static final int PRIMERA_FILA_BLOQUE = 1;
    static final int ETIQUETAS_POR_HOJA = COLUMNAS_IZQUIERDA.length * BLOQUES;

    static final float ALTO_MARGEN_SUPERIOR = 6f;
    static final float ALTO_SEPARADORA = 9.95f;
    static final float ALTO_DEFECTO = 15f;

    static final short ESCALA = 74;
    static final double MARGEN_IZQUIERDO = 0.0;
    /** 1 mm en pulgadas, que es lo que guarda el fichero del cliente. */
    static final double MARGEN = 0.03937007874015748;

    /** Longitud máxima de un nombre de hoja en Excel. */
    private static final int MAX_NOMBRE_HOJA = 31;

    /** Desplazamiento del código de barras respecto a la fila base del bloque. */
    static final int BARCODE_OFFSET_FILA = 2;
    /** Desplazamiento y tamaño del código de barras, en EMU. dx lo centra. */
    static final long BARCODE_DX = 342901;
    static final long BARCODE_DY = 9525;
    static final long BARCODE_CX = 1463802;
    static final long BARCODE_CY = 647700;

    /**
     * Cuerpo de la celda de color en veinteavos de punto, según la longitud
     * del texto: {longitud máxima, cuerpo}. Los ficheros del cliente lo
     * hacen a mano y de forma desigual (se ven 10,5 / 10 / 9 / 6pt para
     * longitudes solapadas); aquí es una regla determinista.
     */
    private static final int[][] CUERPO_COLOR_POR_LONGITUD = {
            {14, 210},                // hasta 14 caracteres: 10,5 pt
            {17, 180},                // 15 a 17:              9 pt
            {Integer.MAX_VALUE, 160}  // 18 o más:             8 pt
    };

    public byte[] generar(List<HojaEtiquetas> hojas) throws IOException {
        if (hojas.isEmpty()) {
            throw new IllegalArgumentException(
                    "No hay ninguna hoja que generar: Excel no abre un libro sin hojas");
        }
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Estilos estilos = new Estilos(libro);
            Set<String> nombresUsados = new HashSet<>();
            for (HojaEtiquetas hoja : hojas) {
                XSSFSheet destino =
                        crearHojaMaquetada(libro, nombreUnico(nombresUsados, hoja.nombreHoja()));
                rellenar(libro, destino, hoja.etiqueta(), estilos);
            }
            libro.setActiveSheet(0);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    /** Fila 0-based donde arranca el bloque: 1, 9, 17 ... 73. */
    static int filaBase(int bloque) {
        return PRIMERA_FILA_BLOQUE + bloque * FILAS_POR_BLOQUE;
    }

    /** Cuerpo en veinteavos de punto para un nombre de color. */
    static int cuerpoPara(String texto) {
        int longitud = texto == null ? 0 : texto.length();
        for (int[] tramo : CUERPO_COLOR_POR_LONGITUD) {
            if (longitud <= tramo[0]) {
                return tramo[1];
            }
        }
        return CUERPO_COLOR_POR_LONGITUD[CUERPO_COLOR_POR_LONGITUD.length - 1][1];
    }

    /**
     * Excel no admite dos hojas con el mismo nombre. El nombre llega ya
     * saneado y recortado desde el generador del cliente; esto es la última
     * red: dos filas idénticas en el pedido no deben romper la generación.
     */
    static String nombreUnico(Set<String> usados, String nombre) {
        if (usados.add(nombre)) {
            return nombre;
        }
        for (int n = 2; ; n++) {
            String sufijo = "-" + n;
            String candidato = nombre.length() + sufijo.length() <= MAX_NOMBRE_HOJA
                    ? nombre + sufijo
                    : nombre.substring(0, MAX_NOMBRE_HOJA - sufijo.length()) + sufijo;
            if (usados.add(candidato)) {
                return candidato;
            }
        }
    }

    // --- pasos ---

    private static XSSFSheet crearHojaMaquetada(XSSFWorkbook libro, String nombre) {
        XSSFSheet hoja = libro.createSheet(nombre);
        for (int columna = 0; columna < ANCHOS_COLUMNA.length; columna++) {
            hoja.setColumnWidth(columna, ANCHOS_COLUMNA[columna]);
        }
        hoja.setDefaultRowHeightInPoints(ALTO_DEFECTO);
        hoja.createRow(0).setHeightInPoints(ALTO_MARGEN_SUPERIOR);
        for (int bloque = 0; bloque < BLOQUES; bloque++) {
            int base = filaBase(bloque);
            for (int desplazamiento = 0; desplazamiento < FILAS_POR_BLOQUE; desplazamiento++) {
                Row fila = hoja.createRow(base + desplazamiento);
                if (desplazamiento == FILAS_POR_BLOQUE - 1) {
                    fila.setHeightInPoints(ALTO_SEPARADORA);
                }
            }
        }
        XSSFPrintSetup impresion = hoja.getPrintSetup();
        impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
        impresion.setScale(ESCALA);
        impresion.setLandscape(false);
        hoja.setMargin(PageMargin.LEFT, MARGEN_IZQUIERDO);
        hoja.setMargin(PageMargin.RIGHT, MARGEN);
        hoja.setMargin(PageMargin.TOP, MARGEN);
        hoja.setMargin(PageMargin.BOTTOM, MARGEN);
        return hoja;
    }

    private static void rellenar(XSSFWorkbook libro, XSSFSheet hoja,
                                 EtiquetaArticulo etiqueta, Estilos estilos) {
        int imagen = indiceImagen(libro, etiqueta.ean13());
        XSSFDrawing dibujo = imagen >= 0 ? hoja.createDrawingPatriarch() : null;
        CellStyle derecha = estilos.derecha();
        CellStyle color = estilos.color(etiqueta.color());
        for (int bloque = 0; bloque < BLOQUES; bloque++) {
            int base = filaBase(bloque);
            for (int izquierda : COLUMNAS_IZQUIERDA) {
                escribir(hoja, base, izquierda, etiqueta.referencia(), null);
                escribir(hoja, base, izquierda + 1, etiqueta.talla(), derecha);
                escribir(hoja, base + 1, izquierda, etiqueta.color(), color);
                escribir(hoja, base + 1, izquierda + 1, etiqueta.pedido(), derecha);
                if (dibujo != null) {
                    dibujo.createPicture(AnclajeImagen.fijo(hoja, izquierda, BARCODE_DX,
                            base + BARCODE_OFFSET_FILA, BARCODE_DY,
                            BARCODE_CX, BARCODE_CY), imagen);
                }
            }
        }
    }

    /**
     * Añade el PNG del código de barras al libro UNA vez y devuelve su
     * índice para que los 40 anclajes de la hoja lo reutilicen: así lo
     * guarda Excel en los ficheros del cliente, una imagen y 40 anclajes.
     * -1 si la etiqueta no trae un EAN13 válido.
     */
    private static int indiceImagen(XSSFWorkbook libro, String ean13) {
        Optional<byte[]> png = CodigoBarrasEan13.png(ean13);
        return png.map(bytes -> libro.addPicture(bytes, Workbook.PICTURE_TYPE_PNG)).orElse(-1);
    }

    private static void escribir(XSSFSheet hoja, int fila, int columna, String valor,
                                 CellStyle estilo) {
        Row f = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell celda = f.getCell(columna) != null ? f.getCell(columna) : f.createCell(columna);
        if (valor == null || valor.isBlank()) {
            celda.setBlank();
        } else {
            celda.setCellValue(valor);
        }
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    /**
     * Estilos creados UNA sola vez por libro y cacheados: POI los acumula
     * por libro, no por hoja, y un fichero de cinturones llega a 79 hojas.
     */
    private static final class Estilos {

        private final XSSFWorkbook libro;
        private final Map<Integer, CellStyle> porCuerpo = new HashMap<>();
        private CellStyle derecha;

        Estilos(XSSFWorkbook libro) {
            this.libro = libro;
        }

        /** Talla y pedido, en la columna derecha de la etiqueta. */
        CellStyle derecha() {
            if (derecha == null) {
                derecha = libro.createCellStyle();
                derecha.setAlignment(HorizontalAlignment.RIGHT);
            }
            return derecha;
        }

        /** Color, con el cuerpo reducido si el nombre es largo. */
        CellStyle color(String texto) {
            return porCuerpo.computeIfAbsent(cuerpoPara(texto), cuerpo -> {
                Font fuente = libro.createFont();
                // setFontHeight va en veinteavos de punto:
                // setFontHeightInPoints solo acepta puntos enteros y hacen
                // falta los 10,5pt del fichero del cliente.
                fuente.setFontHeight((short) cuerpo.intValue());
                CellStyle estilo = libro.createCellStyle();
                estilo.setFont(fuente);
                return estilo;
            });
        }
    }
}
```

- [ ] **Step 5: Ejecutar los dos tests y comprobar que pasan**

```bash
mvn test -Dtest='EtiquetasArticuloExcelBuilderTest+EtiquetasArticuloMaquetacionTest'
```

Esperado: PASS, 10 + 2 tests. Si `EtiquetasArticuloMaquetacionTest` falla en un ancho o un alto, el fichero del cliente es la verdad: corregir la constante del builder, no el test.

- [ ] **Step 6: Ejecutar la suite completa**

```bash
mvn test
```

Esperado: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloExcelBuilder.java \
        src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloExcelBuilderTest.java \
        src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloMaquetacionTest.java
git commit -m "EtiquetasArticuloExcelBuilder: rejilla 4x10 de etiquetas en un A4

Maquetacion con POI, sin plantilla. Una imagen y 40 anclajes por hoja,
como los ficheros del cliente. EtiquetasArticuloMaquetacionTest compara
lo generado contra el fichero real de docs/.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: `AmiEtiquetasArticuloGenerador` — clasificar, agrupar, ordenar y nombrar

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiEtiquetasArticuloGenerador.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiEtiquetasArticuloGeneradorTest.java`

**Interfaces:**
- Consumes: `AmiCatalogoEan`, `FilaEan` (Task 4); `EtiquetaArticulo`, `HojaEtiquetas`, `ExcelEtiquetasArticulo`, `ResultadoEtiquetasArticulo`, `GeneradorEtiquetasArticuloCliente` (Task 5); `EtiquetasArticuloExcelBuilder.generar(List<HojaEtiquetas>)` (Task 6); `CodigoBarrasEan13.esValido(String)` (Task 2).
- Produces: `AmiEtiquetasArticuloGenerador` (`@Service`), constructor `AmiEtiquetasArticuloGenerador(EtiquetasArticuloExcelBuilder builder)`, implementa `GeneradorEtiquetasArticuloCliente` con `claveCliente() == "AMI"`.

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiEtiquetasArticuloGeneradorTest.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class AmiEtiquetasArticuloGeneradorTest {

    private final AmiEtiquetasArticuloGenerador generador =
            new AmiEtiquetasArticuloGenerador(new EtiquetasArticuloExcelBuilder());

    /** EAN13 válidos y distintos, para no depender del dígito de control al variar filas. */
    private static final String EAN_A = "3666598543892";
    private static final String EAN_B = "3666598543915";

    private static List<String> nombresDeHoja(byte[] xlsx) throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            return java.util.stream.IntStream.range(0, libro.getNumberOfSheets())
                    .mapToObj(libro::getSheetName)
                    .toList();
        }
    }

    private static ExcelEtiquetasArticulo porNombre(ResultadoEtiquetasArticulo resultado,
                                                   String nombreFichero) {
        return resultado.getExcels().stream()
                .filter(excel -> excel.nombreFichero().equals(nombreFichero))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no se generó " + nombreFichero
                        + "; sí: " + resultado.getExcels().stream()
                                .map(ExcelEtiquetasArticulo::nombreFichero).toList()));
    }

    @Test
    void parteEnTresFicherosPorTipoYPais() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A),
                new Fila("SPAIN", "ULL754.AL0218", "A328", "SAND/CHOCOLATE", "U", 7683, EAN_A),
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR/ARGENT VIBRE", "75",
                        "07704 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(3, resultado.getExcels().size());
        assertEquals(List.of(
                        "AMI CODE BARRE H26 MOROCCO.xlsx",
                        "AMI CODE BARRE H26 SPAIN.xlsx",
                        "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx"),
                resultado.getExcels().stream()
                        .map(ExcelEtiquetasArticulo::nombreFichero).sorted().toList());
    }

    @Test
    void sinFilasDeUnGrupoNoSeGeneraEseFicheroNiSeAvisa() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL754.AL0218", "A328", "SAND", "U", 7683, EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(1, resultado.getExcels().size());
        assertEquals("AMI CODE BARRE H26 SPAIN.xlsx",
                resultado.getExcels().get(0).nombreFichero());
        assertTrue(resultado.getAvisos().isEmpty());
    }

    @Test
    void unaHojaPorFilaDelPedido() throws Exception {
        // Mismo artículo y color, dos PO: dos hojas.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A),
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", 7687, EAN_B));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(2, nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx").contenido()).size());
    }

    @Test
    void elNombreDeHojaUsaElLibelleCuandoCabe() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("USL738.AL0137 TRUFFLE 07714CH"), nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx").contenido()));
    }

    @Test
    void elNombreDeHojaCaeAlColorisCuandoElLibelleNoCabe() throws Exception {
        // "USL738.AL0137 SAND-CHOCOLATE 07683" son 34 caracteres: no cabe.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A328", "SAND/CHOCOLATE", "U", 7683, EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("USL738.AL0137 A328 07683"), nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx").contenido()));
    }

    @Test
    void enCinturonesElNombreDeHojaLlevaLaTalla() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL214.AL0223", "001", "BLACK", "85", "07694 JP", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("UBL214.AL0223 BLACK 07694JP 85"), nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx")
                        .contenido()));
    }

    @Test
    void enCinturonesConLibelleLargoCaeAlColoris() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR/ARGENT VIBRE", "75",
                        "07704 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("UBL029.AL0104 0014 07704CH 75"), nombresDeHoja(
                porNombre(resultado, "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx")
                        .contenido()));
    }

    @Test
    void dosColorisConElMismoLibelleCaenLosDosAlColoris() throws Exception {
        // Si se usara el libellé, las dos hojas se llamarían igual.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "BLACK", "U", 7687, EAN_A),
                new Fila("MOROCCO", "USL738.AL0137", "A237", "BLACK", "U", 7687, EAN_B));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("USL738.AL0137 A237 07687", "USL738.AL0137 A236 07687"),
                nombresDeHoja(porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx")
                        .contenido()));
    }

    @Test
    void ordenaTodoDescendenteYLaTallaNumericamente() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR", "75", 7704, EAN_A),
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR", "105", 7704, EAN_A),
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR", "95", 7704, EAN_A),
                new Fila("SPAIN", "UBL029.AL0104", "0014", "NOIR", "85", 7704, EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        // 105 antes que 95: si se ordenara como texto saldría 95, 85, 75, 105.
        assertEquals(List.of(
                        "UBL029.AL0104 NOIR 07704 105",
                        "UBL029.AL0104 NOIR 07704 95",
                        "UBL029.AL0104 NOIR 07704 85",
                        "UBL029.AL0104 NOIR 07704 75"),
                nombresDeHoja(porNombre(resultado,
                        "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx").contenido()));
    }

    @Test
    void ordenaLosArticulosDescendente() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "ULL163.AL0052", "221", "BLACK", "U", 7663, EAN_A),
                new Fila("MOROCCO", "USL738.AL0137", "A236", "BLACK", "U", 7687, EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(List.of("USL738.AL0137 BLACK 07687", "ULL163.AL0052 BLACK 07663"),
                nombresDeHoja(porNombre(resultado, "AMI CODE BARRE H26 MOROCCO.xlsx")
                        .contenido()));
    }

    @Test
    void unEan13InvalidoAvisaPeroGeneraLaHojaSinCodigoDeBarras() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH",
                        "3666598543893"));   // dígito de control incorrecto

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(1, resultado.getExcels().size());
        assertEquals(1, resultado.getAvisos().size());
        String aviso = resultado.getAvisos().get(0);
        assertTrue(aviso.contains("USL738.AL0137"), aviso);
        assertTrue(aviso.contains("3666598543893"), aviso);
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).contenido()))) {
            assertEquals(1, libro.getNumberOfSheets());
            assertTrue(libro.getAllPictures().isEmpty());
        }
    }

    @Test
    void sinEan13AvisaIndicandoQueNoLoTrae() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH"));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals(1, resultado.getAvisos().size());
        assertTrue(resultado.getAvisos().get(0).contains("no trae EAN13"),
                resultado.getAvisos().get(0));
    }

    @Test
    void unaTallaVaciaAvisa() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "", "07714 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("Sin talla")),
                resultado.getAvisos().toString());
    }

    @Test
    void arrastraLosAvisosDeLecturaDelCatalogo() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "", "A236", "TRUFFLE", "U", "07714 CH", EAN_A),
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("ARTICLE")),
                resultado.getAvisos().toString());
    }

    @Test
    void laTemporadaDelExcelManda() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN E27",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A));

        // Aunque el usuario escriba H26, la hoja dice E27.
        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals("AMI CODE BARRE E27 MOROCCO.xlsx",
                resultado.getExcels().get(0).nombreFichero());
    }

    @Test
    void sinTemporadaEnLaHojaSeUsaLaDelFormulario() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals("AMI CODE BARRE H26 MOROCCO.xlsx",
                resultado.getExcels().get(0).nombreFichero());
    }

    @Test
    void sinTemporadaEnNingunSitioSeOmiteYSeAvisa() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN",
                new Fila("MOROCCO", "USL738.AL0137", "A236", "TRUFFLE", "U", "07714 CH", EAN_A));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "  ");

        assertEquals("AMI CODE BARRE MOROCCO.xlsx",
                resultado.getExcels().get(0).nombreFichero());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("temporada")),
                resultado.getAvisos().toString());
    }

    @Test
    void laDescripcionDiceTipoPaisYNumeroDeHojas() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "UBL214.AL0223", "001", "BLACK", "85", "07694 JP", EAN_A),
                new Fila("SPAIN", "UBL214.AL0223", "001", "BLACK", "75", "07694 JP", EAN_B));

        ResultadoEtiquetasArticulo resultado = generador.generar(pedido, "H26");

        assertEquals("Cinturones · SPAIN · 2 hojas",
                resultado.getExcels().get(0).descripcion());
    }

    @Test
    void identificaAlClienteYSuCampoDeArchivo() {
        assertEquals("AMI", generador.claveCliente());
        assertTrue(generador.tituloCampoPedido().contains("AMI"));
    }
}
```

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
mvn test -Dtest=AmiEtiquetasArticuloGeneradorTest
```

Esperado: FAIL de compilación, `AmiEtiquetasArticuloGenerador cannot be resolved`.

- [ ] **Step 3: Escribir `AmiEtiquetasArticuloGenerador`**

`src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiEtiquetasArticuloGenerador.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.service.etiquetas.CodigoBarrasEan13;

/**
 * Etiquetas de artículo de AMI: las que se enganchan al bolso o al cinturón,
 * con el código de barras EAN13 del pedido.
 *
 * A partir del excel de pedido de la temporada (ej. "EAN PUNTOTRES H26.xlsx")
 * genera un excel por grupo (tipo × país de fabricación), con una hoja por
 * fila del pedido —es decir por EAN13— y 40 etiquetas idénticas en cada hoja.
 *
 * Los cinturones son las referencias que empiezan por UBL; el resto son
 * bolsos. El país sale de la columna "Made in", que es la que hace que los
 * bolsos vayan en dos ficheros (MOROCCO y SPAIN).
 */
@Service
public class AmiEtiquetasArticuloGenerador implements GeneradorEtiquetasArticuloCliente {

    /** Longitud máxima de un nombre de hoja en Excel. */
    private static final int MAX_NOMBRE_HOJA = 31;

    /** Caracteres que Excel no admite en un nombre de hoja. */
    private static final Pattern PROHIBIDOS_EN_HOJA = Pattern.compile("[/\\\\?*:\\[\\]]");

    /**
     * Todo descendente, como los ficheros del cliente: artículo, color, PO y
     * talla. La talla se compara NUMÉRICAMENTE (105 antes que 95, no al
     * revés como saldría en texto); las no numéricas ("U") van al final.
     */
    private static final Comparator<FilaEan> ORDEN_HOJAS =
            Comparator.comparing(FilaEan::article, Comparator.reverseOrder())
                    .thenComparing(FilaEan::coloris, Comparator.reverseOrder())
                    .thenComparing(FilaEan::poCompacto, Comparator.reverseOrder())
                    .thenComparing(AmiEtiquetasArticuloGenerador::tallaNumerica,
                            Comparator.reverseOrder());

    private final EtiquetasArticuloExcelBuilder builder;

    public AmiEtiquetasArticuloGenerador(EtiquetasArticuloExcelBuilder builder) {
        this.builder = builder;
    }

    @Override
    public String claveCliente() {
        return "AMI";
    }

    @Override
    public String tituloCampoPedido() {
        return "Introducir excel del pedido de AMI";
    }

    @Override
    public ResultadoEtiquetasArticulo generar(byte[] excelPedido, String temporadaPorDefecto)
            throws IOException {
        AmiCatalogoEan catalogo = AmiCatalogoEan.desdeBytes(excelPedido);
        ResultadoEtiquetasArticulo resultado = new ResultadoEtiquetasArticulo();
        resultado.getAvisos().addAll(catalogo.avisos());

        // El nombre de la hoja del excel manda sobre lo que haya escrito el
        // usuario: es el dato del propio pedido.
        String temporada = catalogo.temporada()
                .orElseGet(() -> temporadaPorDefecto == null ? "" : temporadaPorDefecto.trim());
        if (temporada.isBlank()) {
            resultado.getAvisos().add("No se ha podido deducir la temporada del excel ni se ha "
                    + "indicado ninguna: los nombres de fichero la omiten");
        }

        for (Map.Entry<Grupo, List<FilaEan>> entrada : agrupar(catalogo.filas()).entrySet()) {
            Grupo grupo = entrada.getKey();
            List<FilaEan> filas = new ArrayList<>(entrada.getValue());
            filas.sort(ORDEN_HOJAS);

            List<String> nombres = nombresDeHoja(filas, grupo.cinturon());
            List<HojaEtiquetas> hojas = new ArrayList<>();
            for (int i = 0; i < filas.size(); i++) {
                hojas.add(new HojaEtiquetas(nombres.get(i),
                        etiquetaDe(filas.get(i), resultado.getAvisos())));
            }
            resultado.getExcels().add(new ExcelEtiquetasArticulo(
                    descripcion(grupo, hojas.size()),
                    nombreFichero(grupo, temporada),
                    builder.generar(hojas)));
        }
        return resultado;
    }

    // --- pasos ---

    /** Los cinturones de AMI son las referencias que empiezan por UBL. */
    private static boolean esCinturon(FilaEan fila) {
        return fila.article().startsWith("UBL");
    }

    /** Un grupo = un fichero. Se conserva el orden de aparición en el excel. */
    private static Map<Grupo, List<FilaEan>> agrupar(List<FilaEan> filas) {
        Map<Grupo, List<FilaEan>> porGrupo = new LinkedHashMap<>();
        for (FilaEan fila : filas) {
            porGrupo.computeIfAbsent(new Grupo(esCinturon(fila), fila.madeIn()),
                    grupo -> new ArrayList<>()).add(fila);
        }
        return porGrupo;
    }

    /**
     * Nombre de hoja de cada fila del grupo: "{ARTICLE} {color} {PO}{sufijo}"
     * y, en cinturones, " {TALLA}".
     *
     * Se intenta con el libellé, que es lo legible al buscar la hoja para
     * imprimir ("BLACK", "TRUFFLE"), y se cae al código COLORIS cuando el
     * nombre no cabría en los 31 caracteres de Excel o cuando dos filas
     * distintas darían el mismo nombre (dos COLORIS con el mismo libellé).
     * Nunca se trunca a media palabra.
     */
    private static List<String> nombresDeHoja(List<FilaEan> filas, boolean cinturon) {
        List<String> conLibelle = filas.stream()
                .map(fila -> nombre(fila, cinturon, sanear(fila.libelle())))
                .toList();
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            String candidato = conLibelle.get(i);
            boolean cabe = candidato.length() <= MAX_NOMBRE_HOJA;
            // O(n²) sobre 79 filas como máximo: irrelevante y más claro que
            // montar un mapa de frecuencias.
            boolean unico = Collections.frequency(conLibelle, candidato) == 1;
            nombres.add(recortar(cabe && unico
                    ? candidato
                    : nombre(filas.get(i), cinturon, sanear(filas.get(i).coloris()))));
        }
        return nombres;
    }

    private static String nombre(FilaEan fila, boolean cinturon, String color) {
        String base = fila.article() + " " + color + " " + fila.poCompacto();
        return cinturon ? base + " " + fila.taille() : base;
    }

    private static String sanear(String texto) {
        return PROHIBIDOS_EN_HOJA.matcher(texto.trim()).replaceAll("-");
    }

    /**
     * Última red por si un artículo fuera más largo de lo habitual: con
     * COLORIS el nombre mide 30 como máximo, pero recortar es más barato que
     * dejar que POI lance al crear la hoja.
     */
    private static String recortar(String nombre) {
        return nombre.length() <= MAX_NOMBRE_HOJA
                ? nombre
                : nombre.substring(0, MAX_NOMBRE_HOJA);
    }

    /** La talla como número para ordenar; -1 si no es numérica ("U"). */
    private static int tallaNumerica(FilaEan fila) {
        return fila.taille().matches("\\d+") ? Integer.parseInt(fila.taille()) : -1;
    }

    /**
     * Formatea la etiqueta. Un EAN13 que no sean 13 dígitos con dígito de
     * control correcto se deja en null: la hoja se genera igual sin código de
     * barras y se avisa, en vez de bloquear el fichero entero.
     */
    private static EtiquetaArticulo etiquetaDe(FilaEan fila, List<String> avisos) {
        String ean13 = CodigoBarrasEan13.esValido(fila.ean13()) ? fila.ean13().trim() : null;
        if (ean13 == null) {
            avisos.add("Sin código de barras: " + identifica(fila)
                    + (fila.ean13().isBlank()
                            ? " (no trae EAN13)"
                            : " (EAN13 inválido: '" + fila.ean13() + "')"));
        }
        if (fila.taille().isBlank()) {
            avisos.add("Sin talla: " + identifica(fila));
        }
        return new EtiquetaArticulo(fila.article(), "Size: " + fila.taille(),
                fila.colorCompleto(), "Cde: " + fila.poNumerico(), ean13);
    }

    /** Cómo se nombra una fila en los avisos, para que Jordi la localice. */
    private static String identifica(FilaEan fila) {
        return fila.article() + " " + fila.colorCompleto()
                + " talla " + (fila.taille().isBlank() ? "?" : fila.taille())
                + " PO " + fila.poNumerico();
    }

    /**
     * "AMI CODE BARRE H26 MOROCCO.xlsx" para bolsos y
     * "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx" para cinturones:
     * ITEMS delante e CINTURONES al final, como los ficheros del cliente.
     */
    private static String nombreFichero(Grupo grupo, String temporada) {
        List<String> partes = new ArrayList<>(List.of("AMI", "CODE", "BARRE"));
        if (grupo.cinturon()) {
            partes.add("ITEMS");
        }
        if (!temporada.isBlank()) {
            partes.add(temporada);
        }
        if (!grupo.madeIn().isBlank()) {
            partes.add(grupo.madeIn());
        }
        if (grupo.cinturon()) {
            partes.add("CINTURONES");
        }
        return String.join(" ", partes) + ".xlsx";
    }

    /** Lo que se lee en la pantalla de resultados. */
    private static String descripcion(Grupo grupo, int hojas) {
        return (grupo.cinturon() ? "Cinturones" : "Bolsos")
                + " · " + (grupo.madeIn().isBlank() ? "sin país" : grupo.madeIn())
                + " · " + hojas + (hojas == 1 ? " hoja" : " hojas");
    }

    /** Un fichero: tipo de artículo por país de fabricación. */
    private record Grupo(boolean cinturon, String madeIn) {
    }
}
```

- [ ] **Step 4: Ejecutar el test y comprobar que pasa**

```bash
mvn test -Dtest=AmiEtiquetasArticuloGeneradorTest
```

Esperado: PASS, 19 tests.

- [ ] **Step 5: Ejecutar la suite completa**

```bash
mvn test
```

Esperado: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiEtiquetasArticuloGenerador.java \
        src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/AmiEtiquetasArticuloGeneradorTest.java
git commit -m "AmiEtiquetasArticuloGenerador: agrupa por tipo y pais, nombra hojas

Cinturones = referencias UBL. El nombre de hoja usa el libelle cuando
cabe en 31 caracteres y es unico, y cae al codigo COLORIS si no.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: `EtiquetasArticuloGenerationService` + end-to-end con el pedido real

Cierra la capa de servicio y la valida contra el fichero real del cliente. Es la primera vez que se ejercita el flujo completo con 151 filas.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloGenerationService.java`
- Create: `src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx` (copia de `docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx`, 23 KB)
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloGenerationServiceTest.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloFlujoRealTest.java`

**Interfaces:**
- Consumes: `GeneradorEtiquetasArticuloCliente` (Task 5), `AmiEtiquetasArticuloGenerador` (Task 7), `EtiquetasArticuloExcelBuilder` (Task 6).
- Produces: `EtiquetasArticuloGenerationService` (`@Service`), constructor `EtiquetasArticuloGenerationService(List<GeneradorEtiquetasArticuloCliente> generadores)`, método `Optional<GeneradorEtiquetasArticuloCliente> generadorPara(String claveCliente)`.

- [ ] **Step 1: Copiar el pedido real a los recursos de test**

```bash
cp "docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx" "src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx"
ls -la "src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx"
```

Esperado: ~23 KB. Es el único binario commiteado en los recursos de test, y está autorizado en las restricciones globales: a cambio, valida que el formato real del cliente se lee bien.

- [ ] **Step 2: Escribir los dos tests que fallan**

`src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloGenerationServiceTest.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EtiquetasArticuloGenerationServiceTest {

    private final EtiquetasArticuloGenerationService servicio =
            new EtiquetasArticuloGenerationService(List.of(
                    new AmiEtiquetasArticuloGenerador(new EtiquetasArticuloExcelBuilder())));

    @Test
    void encuentraElGeneradorDeSuCliente() {
        assertEquals("AMI", servicio.generadorPara("AMI").orElseThrow().claveCliente());
    }

    @Test
    void laClaveEsIndiferenteAMayusculasYEspacios() {
        assertTrue(servicio.generadorPara("  ami ").isPresent());
    }

    @Test
    void unClienteSinGeneradorNoTieneEstaFuncionalidad() {
        assertTrue(servicio.generadorPara("APC").isEmpty());
        assertTrue(servicio.generadorPara(null).isEmpty());
    }
}
```

`src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloFlujoRealTest.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Flujo completo con el excel de pedido REAL de la temporada H26.
 *
 * Además de comprobar el reparto (46 bolsos MOROCCO, 26 bolsos SPAIN, 79
 * cinturones), deja los tres .xlsx en target/ para abrirlos e imprimirlos a
 * mano, igual que hace flujoCompletoGeneraExcelsAbribles con los packing
 * lists.
 */
class EtiquetasArticuloFlujoRealTest {

    private static final String RECURSO = "/ejemplos/EAN PUNTOTRES H26.xlsx";

    private static byte[] pedidoReal() throws Exception {
        try (InputStream entrada =
                     EtiquetasArticuloFlujoRealTest.class.getResourceAsStream(RECURSO)) {
            assertNotNull(entrada, "falta el recurso de test " + RECURSO);
            return entrada.readAllBytes();
        }
    }

    @Test
    void generaLosTresFicherosConSuNumeroDeHojasYLosDejaEnTarget() throws Exception {
        AmiEtiquetasArticuloGenerador generador =
                new AmiEtiquetasArticuloGenerador(new EtiquetasArticuloExcelBuilder());

        ResultadoEtiquetasArticulo resultado = generador.generar(pedidoReal(), "H26");

        Map<String, Integer> hojasPorFichero = new LinkedHashMap<>();
        Path destino = Path.of("target");
        for (ExcelEtiquetasArticulo excel : resultado.getExcels()) {
            try (XSSFWorkbook libro =
                         new XSSFWorkbook(new ByteArrayInputStream(excel.contenido()))) {
                hojasPorFichero.put(excel.nombreFichero(), libro.getNumberOfSheets());
            }
            // Para inspección manual: abrir y comprobar que cabe en un A4.
            Files.write(destino.resolve(excel.nombreFichero()), excel.contenido());
        }

        assertEquals(Map.of(
                        "AMI CODE BARRE H26 MOROCCO.xlsx", 46,
                        "AMI CODE BARRE H26 SPAIN.xlsx", 26,
                        "AMI CODE BARRE ITEMS H26 SPAIN CINTURONES.xlsx", 79),
                hojasPorFichero);
    }

    @Test
    void elPedidoRealNoProduceNingunAviso() throws Exception {
        AmiEtiquetasArticuloGenerador generador =
                new AmiEtiquetasArticuloGenerador(new EtiquetasArticuloExcelBuilder());

        ResultadoEtiquetasArticulo resultado = generador.generar(pedidoReal(), "H26");

        // Todas las filas de H26 traen ARTICLE, PO, talla y un EAN13 válido:
        // si algún día aparece un aviso aquí, el fichero del cliente ha
        // cambiado de forma y hay que mirarlo.
        assertTrue(resultado.getAvisos().isEmpty(), resultado.getAvisos().toString());
    }

    @Test
    void cadaHojaDelFicheroRealLleva40EtiquetasYSuCodigoDeBarras() throws Exception {
        AmiEtiquetasArticuloGenerador generador =
                new AmiEtiquetasArticuloGenerador(new EtiquetasArticuloExcelBuilder());

        ResultadoEtiquetasArticulo resultado = generador.generar(pedidoReal(), "H26");
        ExcelEtiquetasArticulo cinturones = resultado.getExcels().stream()
                .filter(excel -> excel.nombreFichero().contains("CINTURONES"))
                .findFirst().orElseThrow();

        try (XSSFWorkbook libro =
                     new XSSFWorkbook(new ByteArrayInputStream(cinturones.contenido()))) {
            // Una imagen por hoja, no una por etiqueta.
            assertEquals(libro.getNumberOfSheets(), libro.getAllPictures().size());
            for (int i = 0; i < libro.getNumberOfSheets(); i++) {
                assertEquals(40, libro.getSheetAt(i).getDrawingPatriarch().getShapes().size(),
                        "hoja " + libro.getSheetName(i));
            }
        }
    }
}
```

- [ ] **Step 3: Ejecutar los tests y comprobar que fallan**

```bash
mvn test -Dtest='EtiquetasArticuloGenerationServiceTest+EtiquetasArticuloFlujoRealTest'
```

Esperado: FAIL de compilación, `EtiquetasArticuloGenerationService cannot be resolved`.

- [ ] **Step 4: Escribir `EtiquetasArticuloGenerationService`**

`src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloGenerationService.java`:

```java
package com.puntotres.packinglist.service.etiquetasarticulo;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Punto de entrada de las etiquetas de artículo: localiza el generador del
 * cliente por su clave de catálogo. Un cliente sin generador simplemente no
 * tiene esta funcionalidad todavía y la pantalla lo marca "en desarrollo".
 *
 * Calco de EtiquetasGenerationService, que hace lo mismo para las etiquetas
 * de caja.
 */
@Service
public class EtiquetasArticuloGenerationService {

    private final Map<String, GeneradorEtiquetasArticuloCliente> porCliente = new HashMap<>();

    public EtiquetasArticuloGenerationService(
            List<GeneradorEtiquetasArticuloCliente> generadores) {
        for (GeneradorEtiquetasArticuloCliente generador : generadores) {
            porCliente.put(normalizar(generador.claveCliente()), generador);
        }
    }

    public Optional<GeneradorEtiquetasArticuloCliente> generadorPara(String claveCliente) {
        if (claveCliente == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(porCliente.get(normalizar(claveCliente)));
    }

    /** ¿Hay algún cliente con etiquetas de artículo implementadas? */
    public boolean tieneAlguno() {
        return !porCliente.isEmpty();
    }

    private static String normalizar(String clave) {
        return clave.trim().toUpperCase(Locale.ROOT);
    }
}
```

- [ ] **Step 5: Ejecutar los tests y comprobar que pasan**

```bash
mvn test -Dtest='EtiquetasArticuloGenerationServiceTest+EtiquetasArticuloFlujoRealTest'
```

Esperado: PASS, 3 + 3 tests.

Si `generaLosTresFicherosConSuNumeroDeHojas...` falla con otros números, comprobar el reparto real del pedido antes de tocar el código:

```bash
# Cuenta de control: 46 MOROCCO bolsos, 26 SPAIN bolsos, 79 UBL
mvn test -Dtest=EtiquetasArticuloFlujoRealTest 2>&1 | grep -A5 "expected"
```

- [ ] **Step 6: Abrir a mano los tres excels de `target/`**

```bash
ls -la target/*.xlsx
```

Abrir `target/AMI CODE BARRE H26 MOROCCO.xlsx` en Excel y comprobar a ojo: 46 hojas, 40 etiquetas por hoja, los códigos de barras centrados, y en **Vista previa de impresión** que cada hoja cabe en **una sola** página A4. Esto es lo único que ningún test puede comprobar.

- [ ] **Step 7: Ejecutar la suite completa**

```bash
mvn test
```

Esperado: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloGenerationService.java \
        "src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx" \
        src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloGenerationServiceTest.java \
        src/test/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloFlujoRealTest.java
git commit -m "servicio de despacho + flujo completo con el pedido real de H26

46 bolsos MOROCCO, 26 bolsos SPAIN, 79 cinturones; sin avisos. Deja los
tres xlsx en target/ para abrirlos e imprimirlos a mano.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 9: `/menu` — la portada con las dos familias de salidas

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/web/MenuController.java`
- Create: `src/main/resources/templates/menu.html`
- Modify: `src/main/java/com/puntotres/packinglist/web/PackingListController.java:112` (`@GetMapping("/")` → `@GetMapping("/packing-list")`), `:463` y `:500` (`redirect:/` → `redirect:/packing-list`)
- Modify: `src/main/resources/templates/fragmentos.html` (enlace al menú en la cabecera)
- Modify: `src/main/resources/static/estilo.css` (estilos de las tarjetas del menú)
- Test: `src/test/java/com/puntotres/packinglist/web/RutasTest.java`

**Interfaces:**
- Produces: rutas `GET /` (redirige a `/menu`), `GET /menu`, `GET /packing-list`. El resto de rutas del asistente (`/importar`, `/revision`, `/recalcular`, `/generar`, `/resultados`, `/etiquetas`, `/descargar/**`, `/nuevo`) no cambian.

- [ ] **Step 1: Escribir el test que falla**

Es el segundo test que levanta contexto Spring (el otro es `PackingListApplicationTest`): las rutas no se pueden comprobar de otra forma.

`src/test/java/com/puntotres/packinglist/web/RutasTest.java`:

```java
package com.puntotres.packinglist.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RutasTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void laRaizLlevaAlMenu() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void elMenuMuestraLasDosSecciones() throws Exception {
        mvc.perform(get("/menu"))
                .andExpect(status().isOk())
                .andExpect(view().name("menu"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/packing-list")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("/etiquetas-articulo")));
    }

    @Test
    void elAsistenteDePackingListViveEnSuPropiaRuta() throws Exception {
        mvc.perform(get("/packing-list"))
                .andExpect(status().isOk())
                .andExpect(view().name("entrada"));
    }

    @Test
    void sinEnvioEnCursoLaRevisionVuelveAlPasoUno() throws Exception {
        mvc.perform(get("/revision"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/packing-list"));
    }
}
```

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
mvn test -Dtest=RutasTest
```

Esperado: FAIL. `laRaizLlevaAlMenu` obtiene 200 con la vista `entrada` en vez de una redirección.

- [ ] **Step 3: Escribir `MenuController`**

`src/main/java/com/puntotres/packinglist/web/MenuController.java`:

```java
package com.puntotres.packinglist.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Portada de la aplicación: elige entre las dos familias de salidas
 * (packing list + etiquetas de caja + volcado ERP, o etiquetas de artículo).
 *
 * La raíz redirige al menú en vez de servirlo para que la portada tenga una
 * URL propia y no se rompa ningún enlace o marcador a "/".
 */
@Controller
public class MenuController {

    @GetMapping("/")
    public String raiz() {
        return "redirect:/menu";
    }

    @GetMapping("/menu")
    public String menu() {
        return "menu";
    }
}
```

- [ ] **Step 4: Mover el asistente a `/packing-list`**

En `PackingListController.java`, tres cambios:

1. Línea 112, la anotación del método `entrada`:

```java
    @GetMapping("/packing-list")
    public String entrada(Model model) {
```

2. Línea 463, el método `nuevo`:

```java
    @GetMapping("/nuevo")
    public String nuevo() {
        envioEnCurso.reiniciar();
        return "redirect:/packing-list";
    }
```

3. Línea 500, el método `sinEnvio`:

```java
    private String sinEnvio(RedirectAttributes redirect) {
        redirect.addFlashAttribute("mensaje", "No hay ningún envío en curso: empieza pegando el JSON.");
        return "redirect:/packing-list";
    }
```

Y actualizar el javadoc de la clase, que dice "tres pantallas":

```java
/**
 * Asistente web de generación de packing lists en tres pantallas:
 * entrada (pegar JSON + cabecera) → revisión (avisos y pesos editables)
 * → resultados (descarga de excels). Orquesta los mismos servicios que
 * {@code Main.java}, con el estado del envío en la sesión HTTP.
 *
 * Su primera pantalla vive en /packing-list: la raíz es el menú
 * (MenuController), desde el que se llega también a las etiquetas de
 * artículo, que son un flujo aparte.
 */
```

- [ ] **Step 5: Escribir `menu.html`**

`src/main/resources/templates/menu.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragmentos :: head('Inicio')}"></head>
<body>
<header th:replace="~{fragmentos :: cabecera('')}"></header>
<main>
    <p class="menu-intro">
        Dos cosas distintas que salen de dos documentos distintos. Elige por dónde empezar.
    </p>

    <div class="menu-tarjetas">
        <section class="tarjeta menu-tarjeta">
            <h2 class="menu-titulo"><span class="menu-icono">📦</span> Packing List y albarán</h2>
            <p class="menu-descripcion">
                Del JSON del envío —pegado a mano, rellenado en el formulario o extraído de
                fotos— a los excels que espera el cliente. Los pesos que falten se infieren
                y se pueden revisar y corregir antes de generar.
            </p>
            <p class="menu-entrada">Necesitas: el packing list del envío.</p>
            <ul class="menu-lista">
                <li>Un packing list por destinación, en la plantilla real del cliente</li>
                <li>Etiquetas de caja y de palet</li>
                <li>Volcado del albarán para el ERP</li>
            </ul>
            <a class="boton" th:href="@{/packing-list}">Empezar →</a>
        </section>

        <section class="tarjeta menu-tarjeta">
            <h2 class="menu-titulo"><span class="menu-icono">🏷️</span> Etiquetas de artículo</h2>
            <p class="menu-descripcion">
                Del excel de pedido de la temporada a los códigos de barras EAN13 que se
                enganchan a cada bolso y a cada cinturón. Una hoja por referencia, color,
                pedido y talla.
            </p>
            <p class="menu-entrada">Necesitas: el excel de pedido del cliente.</p>
            <ul class="menu-lista">
                <li>Un excel de bolsos por país de fabricación</li>
                <li>Un excel de cinturones</li>
                <li>40 etiquetas por hoja, ajustadas a un A4</li>
            </ul>
            <a class="boton" th:href="@{/etiquetas-articulo}">Empezar →</a>
        </section>
    </div>
</main>
</body>
</html>
```

- [ ] **Step 6: Añadir el enlace al menú en la cabecera común**

En `src/main/resources/templates/fragmentos.html`, sustituir el fragmento `cabecera` por:

```html
<header class="cabecera" th:fragment="cabecera(paso)">
    <div class="titulo">
        <a th:href="@{/menu}" class="enlace-menu">Generador de Packing Lists</a>
        <span class="paso" th:text="${paso}">paso</span>
    </div>
</header>
```

El título pasa a ser el enlace de vuelta al menú: no añade ruido y está en todas las pantallas.

- [ ] **Step 7: Añadir los estilos**

Al final de `src/main/resources/static/estilo.css`:

```css
/* --- Menú de inicio: una tarjeta por familia de salidas --- */

header.cabecera .enlace-menu {
    color: inherit;
    text-decoration: none;
}

header.cabecera .enlace-menu:hover {
    text-decoration: underline;
}

.menu-intro {
    margin: 1.2rem 0 0;
    color: #5a6673;
}

.menu-tarjetas {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr));
    gap: 1rem;
    align-items: start;
}

/* Botón pegado al fondo para que las dos tarjetas queden a la misma altura. */
.menu-tarjeta {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 1.5rem;
}

.menu-tarjeta .boton { margin-top: auto; align-self: flex-start; }

.menu-titulo {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    margin: 0 0 0.6rem;
    font-size: 1.3rem;
}

.menu-icono { font-size: 1.8rem; line-height: 1; }

.menu-descripcion { margin: 0 0 0.8rem; line-height: 1.5; }

.menu-entrada {
    margin: 0 0 0.8rem;
    padding: 0.45rem 0.7rem;
    border-left: 3px solid var(--acento);
    background: var(--fondo-suave);
    font-size: 0.9rem;
}

.menu-lista {
    margin: 0 0 1.2rem;
    padding-left: 1.2rem;
    font-size: 0.9rem;
    line-height: 1.7;
}
```

- [ ] **Step 8: Ejecutar el test y comprobar que pasa**

`elMenuMuestraLasDosSecciones` comprueba que el HTML enlaza `/etiquetas-articulo`, ruta que aún no existe (Task 10). El enlace se renderiza igual: Thymeleaf no valida que el destino tenga controlador.

```bash
mvn test -Dtest=RutasTest
```

Esperado: PASS, 4 tests.

- [ ] **Step 9: Ejecutar la suite completa**

```bash
mvn test
```

Esperado: PASS. Si algún test de la web esperaba `redirect:/`, actualizarlo a `/packing-list`.

- [ ] **Step 10: Mirar el menú en el navegador**

```bash
mvn spring-boot:run
```

Abrir `http://localhost:8080/` y comprobar que redirige al menú, que las dos tarjetas quedan a la misma altura con los botones alineados, y que el botón de packing list lleva al paso 1. Recordatorio de Windows: matar `mvn` no mata el `java` hijo, así que para liberar el 8080 hace falta `netstat -ano | findstr :8080` y `taskkill /F /PID <pid>`.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/web/MenuController.java \
        src/main/java/com/puntotres/packinglist/web/PackingListController.java \
        src/main/resources/templates/menu.html \
        src/main/resources/templates/fragmentos.html \
        src/main/resources/static/estilo.css \
        src/test/java/com/puntotres/packinglist/web/RutasTest.java
git commit -m "/menu como portada; el asistente pasa a /packing-list

La raiz redirige al menu para no romper enlaces. El titulo de la
cabecera comun pasa a ser el enlace de vuelta.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 10: `/etiquetas-articulo` — la pantalla del flujo nuevo

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/web/EtiquetasArticuloEnCurso.java`
- Create: `src/main/java/com/puntotres/packinglist/web/EtiquetasArticuloController.java`
- Create: `src/main/resources/templates/etiquetas-articulo.html`
- Create: `src/main/resources/templates/etiquetas-articulo-resultados.html`
- Modify: `src/test/java/com/puntotres/packinglist/web/RutasTest.java` (añadir dos tests)

**Interfaces:**
- Consumes: `EtiquetasArticuloGenerationService.generadorPara(String)` (Task 8); `GeneradorEtiquetasArticuloCliente.tituloCampoPedido()` y `.generar(byte[], String)` (Task 5); `ResultadoEtiquetasArticulo`, `ExcelEtiquetasArticulo` (Task 5); `ClientesProperties.getClientes()` → `Map<String, ClienteConfig>` y `ClienteConfig.getNombre()` / `.getPlaceholderTemporada()`.
- Produces: rutas `GET /etiquetas-articulo`, `POST /etiquetas-articulo/generar`, `GET /etiquetas-articulo/resultados`, `GET /etiquetas-articulo/descargar/{nombreFichero}`, `GET /etiquetas-articulo/descargar-todo`, `GET /etiquetas-articulo/nuevo`.

- [ ] **Step 1: Añadir los tests de ruta que fallan**

Añadir a `src/test/java/com/puntotres/packinglist/web/RutasTest.java`:

```java
    @Test
    void laPantallaDeEtiquetasDeArticuloOfreceElFormulario() throws Exception {
        mvc.perform(get("/etiquetas-articulo"))
                .andExpect(status().isOk())
                .andExpect(view().name("etiquetas-articulo"))
                // El desplegable y el input del excel de pedido.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cliente")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pedido")));
    }

    @Test
    void sinNadaGeneradoLosResultadosVuelvenAlFormulario() throws Exception {
        mvc.perform(get("/etiquetas-articulo/resultados"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/etiquetas-articulo"));
    }
```

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
mvn test -Dtest=RutasTest
```

Esperado: FAIL, 404 en `/etiquetas-articulo`.

- [ ] **Step 3: Escribir `EtiquetasArticuloEnCurso`**

`src/main/java/com/puntotres/packinglist/web/EtiquetasArticuloEnCurso.java`:

```java
package com.puntotres.packinglist.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.puntotres.packinglist.service.etiquetasarticulo.ExcelEtiquetasArticulo;

/**
 * Estado de la generación de etiquetas de artículo entre la pantalla de
 * entrada y la de resultados.
 *
 * Independiente de EnvioEnCurso a propósito: son dos flujos que no comparten
 * nada y que pueden estar a medias a la vez en la misma sesión sin pisarse.
 */
@Component
@SessionScope
public class EtiquetasArticuloEnCurso {

    private String claveCliente;
    private final List<ExcelEtiquetasArticulo> excels = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();

    public boolean estaVacio() {
        return excels.isEmpty();
    }

    public void reiniciar() {
        claveCliente = null;
        excels.clear();
        avisos.clear();
    }

    public String getClaveCliente() { return claveCliente; }
    public void setClaveCliente(String claveCliente) { this.claveCliente = claveCliente; }

    public List<ExcelEtiquetasArticulo> getExcels() { return excels; }
    public List<String> getAvisos() { return avisos; }
}
```

- [ ] **Step 4: Escribir `EtiquetasArticuloController`**

`src/main/java/com/puntotres/packinglist/web/EtiquetasArticuloController.java`:

```java
package com.puntotres.packinglist.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.puntotres.packinglist.config.ClienteConfig;
import com.puntotres.packinglist.config.ClientesProperties;
import com.puntotres.packinglist.service.etiquetasarticulo.EtiquetasArticuloGenerationService;
import com.puntotres.packinglist.service.etiquetasarticulo.ExcelEtiquetasArticulo;
import com.puntotres.packinglist.service.etiquetasarticulo.GeneradorEtiquetasArticuloCliente;
import com.puntotres.packinglist.service.etiquetasarticulo.ResultadoEtiquetasArticulo;

/**
 * Flujo de etiquetas de artículo, en dos pantallas: elegir cliente y subir su
 * excel de pedido → descargar los excels generados.
 *
 * No tiene nada que ver con el asistente de packing lists: no necesita JSON,
 * ni envío en curso, ni pesos. Su única entrada es el excel de pedido.
 */
@Controller
public class EtiquetasArticuloController {

    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EtiquetasArticuloGenerationService etiquetasArticuloService;
    private final ClientesProperties clientesProperties;
    private final EtiquetasArticuloEnCurso enCurso;

    public EtiquetasArticuloController(
            EtiquetasArticuloGenerationService etiquetasArticuloService,
            ClientesProperties clientesProperties,
            EtiquetasArticuloEnCurso enCurso) {
        this.etiquetasArticuloService = etiquetasArticuloService;
        this.clientesProperties = clientesProperties;
        this.enCurso = enCurso;
    }

    // --- Paso 1: elegir cliente y subir el pedido ---

    @GetMapping("/etiquetas-articulo")
    public String entrada(Model model) {
        model.addAttribute("clientes", vistaDeClientes());
        return "etiquetas-articulo";
    }

    @PostMapping("/etiquetas-articulo/generar")
    public String generar(@RequestParam String cliente,
                          @RequestParam(required = false) String temporada,
                          @RequestParam MultipartFile pedido,
                          RedirectAttributes redirect) {
        GeneradorEtiquetasArticuloCliente generador =
                etiquetasArticuloService.generadorPara(cliente).orElse(null);
        if (generador == null) {
            redirect.addFlashAttribute("error",
                    "El cliente '" + cliente + "' no tiene etiquetas de artículo implementadas");
            return "redirect:/etiquetas-articulo";
        }
        if (pedido == null || pedido.isEmpty()) {
            redirect.addFlashAttribute("error", "Falta el " + generador.tituloCampoPedido());
            return "redirect:/etiquetas-articulo";
        }

        ResultadoEtiquetasArticulo resultado;
        try {
            resultado = generador.generar(pedido.getBytes(), temporada);
        } catch (IOException | RuntimeException e) {
            // Un excel que no es el que toca (sin hoja EAN, sin columna
            // EAN13) llega aquí: es lo único que bloquea, porque no hay nada
            // útil que generar.
            redirect.addFlashAttribute("error",
                    "No se pudieron generar las etiquetas: " + e.getMessage());
            return "redirect:/etiquetas-articulo";
        }
        if (resultado.getExcels().isEmpty()) {
            redirect.addFlashAttribute("error", "El excel de pedido no tiene ninguna fila "
                    + "utilizable: no se ha generado nada");
            return "redirect:/etiquetas-articulo";
        }

        enCurso.reiniciar();
        enCurso.setClaveCliente(cliente);
        enCurso.getExcels().addAll(resultado.getExcels());
        enCurso.getAvisos().addAll(resultado.getAvisos());
        return "redirect:/etiquetas-articulo/resultados";
    }

    // --- Paso 2: resultados y descargas ---

    @GetMapping("/etiquetas-articulo/resultados")
    public String resultados(Model model) {
        if (enCurso.estaVacio()) {
            return "redirect:/etiquetas-articulo";
        }
        model.addAttribute("excels", enCurso.getExcels());
        model.addAttribute("avisos", enCurso.getAvisos());
        model.addAttribute("cliente", enCurso.getClaveCliente());
        return "etiquetas-articulo-resultados";
    }

    @GetMapping("/etiquetas-articulo/descargar/{nombreFichero}")
    public ResponseEntity<byte[]> descargar(@PathVariable String nombreFichero) {
        return enCurso.getExcels().stream()
                .filter(excel -> excel.nombreFichero().equals(nombreFichero))
                .findFirst()
                .map(excel -> ResponseEntity.ok()
                        .contentType(TIPO_XLSX)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                                .attachment().filename(excel.nombreFichero()).build().toString())
                        .body(excel.contenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/etiquetas-articulo/descargar-todo")
    public ResponseEntity<byte[]> descargarTodo() throws IOException {
        if (enCurso.estaVacio()) {
            return ResponseEntity.notFound().build();
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (ExcelEtiquetasArticulo excel : enCurso.getExcels()) {
                zip.putNextEntry(new ZipEntry(excel.nombreFichero()));
                zip.write(excel.contenido());
                zip.closeEntry();
            }
        }
        String nombreZip = ("CODE_BARRE_" + enCurso.getClaveCliente() + ".zip")
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                        .attachment().filename(nombreZip).build().toString())
                .body(salida.toByteArray());
    }

    @GetMapping("/etiquetas-articulo/nuevo")
    public String nuevo() {
        enCurso.reiniciar();
        return "redirect:/etiquetas-articulo";
    }

    // --- Internos ---

    /**
     * Catálogo para el desplegable: todos los clientes, marcando los que aún
     * no tienen etiquetas de artículo implementadas para que la pantalla los
     * deshabilite en vez de esconderlos.
     */
    private Map<String, Map<String, String>> vistaDeClientes() {
        Map<String, Map<String, String>> vista = new LinkedHashMap<>();
        clientesProperties.getClientes().forEach((clave, config) -> {
            GeneradorEtiquetasArticuloCliente generador =
                    etiquetasArticuloService.generadorPara(clave).orElse(null);
            Map<String, String> datos = new LinkedHashMap<>();
            datos.put("nombre", nombreDe(config, clave));
            datos.put("disponible", String.valueOf(generador != null));
            datos.put("tituloCampoPedido",
                    generador != null ? generador.tituloCampoPedido() : "");
            datos.put("placeholderTemporada", config.getPlaceholderTemporada() != null
                    ? config.getPlaceholderTemporada() : "");
            vista.put(clave, datos);
        });
        return vista;
    }

    private static String nombreDe(ClienteConfig config, String clave) {
        return config.getNombre() != null && !config.getNombre().isBlank()
                ? config.getNombre() : clave;
    }
}
```

- [ ] **Step 5: Escribir `etiquetas-articulo.html`**

El desplegable y el rótulo del input cambian según el cliente, con el mismo patrón de JS inline que ya usa `entrada.html`.

`src/main/resources/templates/etiquetas-articulo.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragmentos :: head('Etiquetas de artículo')}"></head>
<body>
<header th:replace="~{fragmentos :: cabecera('· Etiquetas de artículo')}"></header>
<main>
    <div class="alerta error" th:if="${error}" th:text="${error}"></div>

    <form class="tarjeta" th:action="@{/etiquetas-articulo/generar}" method="post"
          enctype="multipart/form-data">
        <h3 class="titulo-seccion">🏷️ Etiquetas de artículo</h3>
        <p class="ayuda">
            Del excel de pedido de la temporada salen los códigos de barras EAN13 de cada
            referencia, color, pedido y talla: un excel de bolsos por país de fabricación y
            otro de cinturones, con 40 etiquetas por hoja ajustadas a un A4.
        </p>

        <label for="cliente">Cliente</label>
        <select id="cliente" name="cliente" required>
            <option th:each="entrada : ${clientes}"
                    th:value="${entrada.key}"
                    th:text="${entrada.value['disponible'] == 'true'
                              ? entrada.value['nombre']
                              : entrada.value['nombre'] + ' — en desarrollo'}"
                    th:disabled="${entrada.value['disponible'] != 'true'}"
                    th:attr="data-titulo=${entrada.value['tituloCampoPedido']},
                             data-temporada=${entrada.value['placeholderTemporada']}"></option>
        </select>

        <label for="temporada">Temporada</label>
        <input type="text" id="temporada" name="temporada">
        <p class="ayuda">
            Solo se usa si no se puede deducir del propio excel: si su hoja se llama
            «EAN H26», manda «H26».
        </p>

        <label for="pedido" id="etiquetaPedido">Excel de pedido del cliente</label>
        <input type="file" id="pedido" name="pedido" accept=".xlsx" required>

        <button type="submit">Generar etiquetas</button>
        <a class="boton secundario" th:href="@{/menu}">Volver al menú</a>
    </form>
</main>
<script>
    // El rótulo del input y la temporada por defecto dependen del cliente,
    // igual que en la pantalla de entrada del asistente.
    (function () {
        const cliente = document.getElementById('cliente');
        const etiqueta = document.getElementById('etiquetaPedido');
        const temporada = document.getElementById('temporada');

        function ajustar() {
            const opcion = cliente.options[cliente.selectedIndex];
            if (!opcion) {
                return;
            }
            const titulo = opcion.dataset.titulo;
            etiqueta.textContent = titulo ? titulo : 'Excel de pedido del cliente';
            temporada.placeholder = opcion.dataset.temporada || '';
        }

        cliente.addEventListener('change', ajustar);
        ajustar();
    })();
</script>
</body>
</html>
```

- [ ] **Step 6: Escribir `etiquetas-articulo-resultados.html`**

`src/main/resources/templates/etiquetas-articulo-resultados.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragmentos :: head('Etiquetas de artículo generadas')}"></head>
<body>
<header th:replace="~{fragmentos :: cabecera('· Etiquetas de artículo')}"></header>
<main>
    <p class="resumen-envio">
        Cliente <strong th:text="${cliente}"></strong> ·
        <span th:text="${#lists.size(excels)}"></span> excel(s) generado(s)
    </p>

    <div class="alerta" th:each="aviso : ${avisos}" th:text="${aviso}"></div>

    <section class="tarjeta seccion-salida">
        <h3 class="titulo-seccion">🏷️ Excels de etiquetas</h3>
        <table>
            <thead>
            <tr>
                <th>Contenido</th>
                <th>Fichero</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <tr th:each="excel : ${excels}">
                <td th:text="${excel.descripcion}"></td>
                <td th:text="${excel.nombreFichero}"></td>
                <td>
                    <a class="boton mini"
                       th:href="@{/etiquetas-articulo/descargar/{nombre}(nombre=${excel.nombreFichero})}">
                        Descargar</a>
                </td>
            </tr>
            </tbody>
        </table>

        <a class="boton" th:href="@{/etiquetas-articulo/descargar-todo}">Descargar todo (ZIP)</a>
        <a class="boton secundario" th:href="@{/etiquetas-articulo/nuevo}">Otro pedido</a>
        <a class="boton secundario" th:href="@{/menu}">Volver al menú</a>
    </section>
</main>
</body>
</html>
```

- [ ] **Step 7: Ejecutar el test y comprobar que pasa**

```bash
mvn test -Dtest=RutasTest
```

Esperado: PASS, 6 tests.

- [ ] **Step 8: Ejecutar la suite completa**

```bash
mvn test
```

Esperado: PASS.

- [ ] **Step 9: Probar el flujo completo en el navegador**

```bash
mvn spring-boot:run
```

1. `http://localhost:8080/` → menú.
2. «Etiquetas de artículo» → el desplegable muestra AMI disponible y el resto «en desarrollo»; el rótulo del input dice «Introducir excel del pedido de AMI».
3. Subir `docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx` → tres filas de resultados: `Bolsos · MOROCCO · 46 hojas`, `Bolsos · SPAIN · 26 hojas`, `Cinturones · SPAIN · 79 hojas`, sin avisos.
4. Descargar el ZIP, abrir uno de los excels y comprobar en Vista previa de impresión que una hoja = una página A4.

Liberar el 8080 al terminar: `netstat -ano | findstr :8080` + `taskkill /F /PID <pid>`.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/web/EtiquetasArticuloEnCurso.java \
        src/main/java/com/puntotres/packinglist/web/EtiquetasArticuloController.java \
        src/main/resources/templates/etiquetas-articulo.html \
        src/main/resources/templates/etiquetas-articulo-resultados.html \
        src/test/java/com/puntotres/packinglist/web/RutasTest.java
git commit -m "/etiquetas-articulo: subir el pedido y descargar los excels

Flujo aparte del asistente, con su propio estado de sesion. Los
clientes sin generador salen deshabilitados como 'en desarrollo'.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Cierre

- [ ] **Actualizar la documentación del repo**

Añadir a `CLAUDE.md`, en la sección «Arquitectura (lo esencial)», después del bloque de etiquetas de caja:

```markdown
**Etiquetas de artículo** (`service/etiquetasarticulo/`): flujo **independiente
del envío** (`/etiquetas-articulo`), su única entrada es el excel de pedido del
cliente. `GeneradorEtiquetasArticuloCliente` es la interfaz, despachada por
clave de cliente; implementado AMI. Una hoja por fila del pedido (= por EAN13),
40 etiquetas idénticas por hoja en una rejilla 4×10 que cabe en un A4, y un
fichero por (tipo, Made in): bolsos MOROCCO, bolsos SPAIN, cinturones
(`UBL*`). **No hay plantilla `.xlsx`**: la maquetación son constantes en
`EtiquetasArticuloExcelBuilder`, ancladas al fichero real del cliente por
`EtiquetasArticuloMaquetacionTest` — POI no copia el `pageSetup` al clonar
hojas, así que heredarla de una plantilla no servía.
```

Y en la sección «Comandos», junto al aviso del 8080:

```markdown
- La portada es `/menu`; el asistente de packing list vive en `/packing-list` y `/` redirige al menú.
```

Marcar en `TODO` nada: ese archivo lo mantiene Jordi.

- [ ] **Verificación final y commit de la documentación**

```bash
mvn test
git add CLAUDE.md
git commit -m "CLAUDE.md: etiquetas de articulo y rutas nuevas

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

Esperado: suite completa en verde. Comprobar antes de dar por cerrado:

| Comprobación | Cómo |
|---|---|
| Suite completa en verde | `mvn test` |
| El pedido real da 46 / 26 / 79 hojas sin avisos | `EtiquetasArticuloFlujoRealTest` |
| La maquetación coincide con el fichero del cliente | `EtiquetasArticuloMaquetacionTest` |
| Una hoja cabe en una página A4 | Vista previa de impresión en Excel, sobre `target/AMI CODE BARRE H26 MOROCCO.xlsx` |
| El flujo web funciona de punta a punta | Paso 9 de la Task 10 |
| Las etiquetas de caja siguen funcionando | `AmiEtiquetasExcelBuilderTest`, `AmiPedidoExcelTest`, `ApcEtiquetasExcelBuilderTest` |

## Notas de la auto-revisión

Tres cosas que se detectaron revisando el plan contra el spec y que quedan resueltas arriba:

1. **`HojaEtiquetas` lleva UNA etiqueta, no una lista.** El spec decía `List<EtiquetaArticulo>`, pero las 40 etiquetas de una hoja son idénticas por definición (una hoja = una fila del pedido). El spec ya está corregido.
2. **`Font.setFontHeightInPoints` no sirve para 10,5pt**: solo acepta puntos enteros. Se usa `setFontHeight(short)`, que va en veinteavos de punto — de ahí que `cuerpoPara` devuelva 210 / 180 / 160 y no 10,5 / 9 / 8.
3. **La fila separadora del último bloque (índice 80) no existe en el fichero real**, que no tiene celdas más allá de la 74. `EtiquetasArticuloMaquetacionTest` compara solo las 9 primeras separadoras; el segundo test del mismo fichero cubre los valores absolutos.
