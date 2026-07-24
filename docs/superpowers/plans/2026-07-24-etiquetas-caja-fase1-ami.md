# Etiquetas de caja — Fase 1 (plumbing común + AMI) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generar los excels de etiquetas de caja (un `.xlsx` por cliente+destinación, dos etiquetas A4-media-hoja por caja) a partir del envío en sesión, con arquitectura multi-cliente y la primera implementación: AMI (China, Japan, France), incluyendo lookup en el excel de pedido EAN y código de barras Code 128.

**Architecture:** Nueva familia de servicios en `service/etiquetas/`: interfaz `GeneradorEtiquetasCliente` (análoga a `GeneradorPackingListCliente`) despachada por `EtiquetasGenerationService` según la clave de cliente del catálogo. El flujo web gana un paso opcional post-resultados: `GET /etiquetas` (Paso 2: inputs dinámicos declarados por el generador del cliente) → `POST /etiquetas/generar` → descargas en `/resultados`. La plantilla real `docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx` se copia tal cual a resources; en runtime se limpian sus imágenes de ejemplo y se insertan las generadas.

**Tech Stack:** Java 17, Spring Boot 3.5, Apache POI 5.4.1, Barcode4J (`net.sf.barcode4j:barcode4j-light:2.1`, nueva dependencia), Thymeleaf, JUnit 5 puro (servicios con `new`).

## Global Constraints

- Idioma: javadoc/comentarios/UI en **español**; nombres de clase siguiendo el patrón existente (`GeneradorPackingListCliente` → `GeneradorEtiquetasCliente`).
- **Nunca fallar en silencio, nunca bloquear**: los servicios devuelven DTOs con `avisos` (`List<String>`), no lanzan excepciones por datos incompletos. Referencia no encontrada en el pedido, destinación no soportada o peso null ⇒ aviso + celda en blanco, el excel se genera igual.
- Pesos `Double` con null = desconocido. GROSS WEIGHT en blanco si null y la caja va a `cajasPendientes`.
- No editar las plantillas de `src/main/resources/` a mano; la de etiquetas se copia **byte a byte** del ejemplo de `docs/`.
- Tests de builders: instanciar con `new`, reabrir el `.xlsx` generado con `new XSSFWorkbook(new ByteArrayInputStream(...))` y afirmar celdas reales (patrón `GenericoExcelBuilderTest`).
- Los clientes sin generador de etiquetas siguen mostrando la tarjeta "🔒 En desarrollo — Próximamente" actual de `resultados.html`.
- Etiquetas de palet y "etiqueta de etiqueta": **fuera de alcance**. El punto de extensión queda en que `GeneradorEtiquetasCliente` es solo de etiquetas de caja; tipos nuevos serán métodos/interfaces nuevos en fases posteriores (no crear nada ahora).
- Commits frecuentes (uno por tarea), mensajes en español, terminados en `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

## Decisiones ya tomadas con el usuario

1. **Simbología del código de barras: Code 128** con el número legible debajo (el spec decía "EAN-13", pero los gifs de ejemplo codifican el PO de 5 dígitos — imposible como EAN-13 real; confirmado con el usuario el 2026-07-24).
2. **Lookup del PO por destinación (confirmado)**: en la hoja `EAN *` del excel de pedido, la columna `PO` vale `"NNNNN CH"` (China), `"NNNNN JP"` (Japan) o un número sin sufijo (France). ORDER NUMBER = parte numérica con padding a 5 dígitos. Si difiere del campo `pedido` del JSON (`CajaData.numeroPedido`) ⇒ aviso sin bloquear.

## Datos de referencia (análisis ya hecho de los excels de ejemplo)

### Plantilla `docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx`

3 hojas, una por destinación: **`AMI CHINA`**, **`AMI JAPAN`**, **`AMI FRANCE`**. Cada hoja contiene UN par de etiquetas (2 etiquetas idénticas apiladas verticalmente = 1 hoja A4, pageSetup ya en A4 vertical con escala 80–89%). La etiqueta es más ancha que alta ⇒ apilado vertical correcto (regla del spec verificada).

Coordenadas (filas/columnas **0-based** de POI; col B=1, C=2). "Bloque" = el par completo de etiquetas; la caja i-ésima se escribe con desplazamiento `i * alturaBloque`; la 2ª etiqueta del par con `+ offsetSegundaEtiqueta`:

| | AMI CHINA | AMI JAPAN | AMI FRANCE |
|---|---|---|---|
| alturaBloque (filas) | 34 | 32 | 32 |
| offsetSegundaEtiqueta | 17 | 16 | 16 |
| temporada (col B) | fila 11 | fila 10 | fila 10 |
| referencia (col C) | 11 | 10 | 10 |
| color code (col C) | 12 | 11 | 11 |
| size (col C) | 13 | 12 | 12 |
| quantity (col C) | 14 | 13 | 13 |
| gross weight (col C) | 15 | 14 | 14 |
| parcel n/total (col C) | 16 | 15 | 15 |
| barcode: fila / dx1 / dy1 (EMU) | 8 / 2971800 / 19050 | 7 / 2857500 / 19050 | 7 / 3457575 / 9525 |
| barcode: cx / cy (EMU) | 1047750 / 666750 | 990600 / 628650 | 1209675 / 762000 |
| sufijo PO | `CH` | `JP` | (numérico sin sufijo) |

Todas las imágenes van ancladas en la **columna C (índice 2)** con `oneCellAnchor`. La hoja JAPAN además lleva la **dirección de entrega como imagen PNG** (texto "AMI PARIS JAPAN / MARUNI BUSINESS LOGISTICS CORPORATION…"), anclada en fila 2, dx1=66675, dy1=95250, cx=2562225, cy=1143000 — es el único PNG del libro (los códigos de barras de ejemplo son GIF): se extrae en runtime con `workbook.getAllPictures()` y se reinserta en cada par JAPAN.

Celdas de ejemplo que confirman los formatos (hoja AMI FRANCE): SIZE `85-95-105`, QUANTITY `4-85, 33-95, 4-105` (nosotros generaremos sin espacio: `4-85,33-95,4-105`, formato del spec), GROSS WEIGHT `9,93 KG` (generaremos `%.2f KGS` con coma decimal, Locale es-ES), PARCEL `14 / 84` (formato `"i / total"`).

### Excel de pedido `docs/Etiquetas cajas/AMI EAN H26.xlsx`

Hojas: `BOLSITAS ANTIHUMEDAD`, **`EAN H26`** (la buena — identificarla por nombre que empieza por `EAN`, la temporada cambia), `UBL209.AL0222`. Cabecera en fila 1: A=`Made in`, B=`ARTICLE`, C=`COLORIS`, D=`Libellé coloris`, E=(fórmula concat), F=`TAILLE`, G=`PO`, H=`Commandé`, I=`EAN13`, J=`EAN128`. Localizar columnas **por texto de cabecera**, no por índice fijo. Muchas filas están ocultas (autofiltro): leerlas igual.

- `COLOR CODE` de la etiqueta = `COLORIS + " " + Libellé coloris` (ej. `001 BLACK`; el libellé a veces está en francés).
- Cinturones: una fila por talla (mismo ARTICLE, TAILLE 75/85/95/105…), mismo PO por artículo+destinación.
- La celda PO puede ser texto (`07704 CH`) o numérica (7672.0 ⇒ formatear `%05d`).

## Estructura de ficheros (mapa completo)

```
src/main/java/com/puntotres/packinglist/service/etiquetas/
    GeneradorEtiquetasCliente.java      (interfaz, Tarea 3)
    CampoEtiquetas.java                 (record input dinámico Paso 2, Tarea 3)
    ResultadoEtiquetas.java             (DTO excels+avisos, Tarea 3)
    EtiquetasGenerationService.java     (despacho por cliente, Tarea 3)
    CodigoBarrasCode128.java            (PNG Code128, Tarea 1)
    AmiPedidoExcel.java                 (lector/índice del excel de pedido, Tarea 2)
    AmiEtiquetaLayout.java              (coordenadas por destinación, Tarea 4)
    AmiEtiquetasExcelBuilder.java       (POI: rellena la plantilla, Tarea 4)
    AmiEtiquetasGenerador.java          (implementación AMI, Tarea 5)
src/main/resources/client-labels/
    ami-etiquetas-template.xlsx         (copia byte a byte del ejemplo, Tarea 4)
src/main/resources/templates/
    etiquetas.html                      (vista Paso 2, Tarea 6)
pom.xml                                 (dependencia barcode4j-light, Tarea 1)
web: EnvioEnCurso.java, PackingListController.java, resultados.html (Tarea 6)
tests espejo en src/test/java/... + testutil/PedidoAmiExcel.java (Tarea 2)
```

---

### Tarea 1: Dependencia Barcode4J + generador de PNG Code 128

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasCode128.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasCode128Test.java`

**Interfaces:**
- Produces: `public final class CodigoBarrasCode128 { public static byte[] png(String texto) }` — PNG con el texto legible debajo de las barras. Lo consume la Tarea 4.

- [ ] **Step 1: Añadir la dependencia al pom**

En `pom.xml`, tras el bloque de POI (línea ~51):

```xml
        <!-- Barcode4J: generación de códigos de barras Code 128 para las
             etiquetas de caja (imagen PNG con el número legible debajo). -->
        <dependency>
            <groupId>net.sf.barcode4j</groupId>
            <artifactId>barcode4j-light</artifactId>
            <version>2.1</version>
        </dependency>
```

Nota: si al compilar faltara `BitmapCanvasProvider` en el artefacto *light*, cambiar a `<artifactId>barcode4j</artifactId>` (misma versión; arrastra avalon-framework, aceptable).

- [ ] **Step 2: Escribir el test que falla**

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class CodigoBarrasCode128Test {

    @Test
    void generaUnPngLegibleConProporcionApaisada() throws Exception {
        byte[] png = CodigoBarrasCode128.png("07672");

        // Firma PNG
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                Arrays.copyOfRange(png, 0, 4));

        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(imagen);
        // Apaisado (más ancho que alto): las barras + el número debajo.
        assertTrue(imagen.getWidth() > imagen.getHeight());
    }
}
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `mvn test -Dtest=CodigoBarrasCode128Test`
Expected: error de compilación "cannot find symbol: CodigoBarrasCode128".

- [ ] **Step 4: Implementación mínima**

```java
package com.puntotres.packinglist.service.etiquetas;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import org.krysalis.barcode4j.impl.code128.Code128Bean;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;

/**
 * Genera la imagen PNG de un código de barras Code 128 con el texto
 * legible debajo, tal como aparecen en las etiquetas de caja de ejemplo.
 * (El spec original hablaba de EAN-13, pero los ejemplos reales codifican
 * el PO de 5 dígitos, imposible como EAN-13; decisión: Code 128.)
 */
public final class CodigoBarrasCode128 {

    private CodigoBarrasCode128() {
    }

    public static byte[] png(String texto) {
        Code128Bean codigo = new Code128Bean();
        codigo.doQuietZone(true);
        BitmapCanvasProvider lienzo =
                new BitmapCanvasProvider(300, BufferedImage.TYPE_BYTE_BINARY, false, 0);
        codigo.generateBarcode(lienzo, texto);
        try {
            lienzo.finish();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(lienzo.getBufferedImage(), "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el código de barras de '" + texto + "'", e);
        }
    }
}
```

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=CodigoBarrasCode128Test`
Expected: `Tests run: 1, Failures: 0`.

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasCode128.java src/test/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasCode128Test.java
git commit -m "etiquetas: generador de PNG Code 128 (Barcode4J)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Tarea 2: Lector del excel de pedido de AMI (`AmiPedidoExcel`)

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java`
- Create: `src/test/java/com/puntotres/packinglist/testutil/PedidoAmiExcel.java` (fábrica de excels de pedido para tests; la reutilizan las Tareas 5 y 6)
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcelTest.java`

**Interfaces:**
- Produces:
  ```java
  public class AmiPedidoExcel {
      /** Falla con IllegalArgumentException("...ninguna hoja 'EAN...'") si no hay hoja EAN. */
      public static AmiPedidoExcel desdeBytes(byte[] contenido) throws IOException;
      /** sufijoPo: "CH", "JP" o null (PO numérico sin sufijo = France). */
      public Optional<FilaPedido> buscar(String referencia, String codigoColor, String sufijoPo);
      public record FilaPedido(String orderNumber, String colorCode) {}
  }
  ```
  `orderNumber` siempre 5 dígitos con ceros a la izquierda; `colorCode` = `COLORIS + " " + Libellé` (o solo COLORIS si el libellé está vacío).
- Produces (testutil): `public final class PedidoAmiExcel { public record Fila(String madeIn, String article, String coloris, String libelle, String taille, Object po) {} public static byte[] crear(String nombreHojaEan, Fila... filas) }` — `po` es `String` ("07704 CH") o `Number` (7672).

- [ ] **Step 1: Escribir la fábrica de tests**

```java
package com.puntotres.packinglist.testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Construye en memoria un excel de pedido de AMI con la misma forma que el
 * real ("AMI EAN H26.xlsx"): una hoja señuelo delante y la hoja EAN con la
 * cabecera Made in/ARTICLE/COLORIS/Libellé coloris/(vacía)/TAILLE/PO/... .
 */
public final class PedidoAmiExcel {

    /** po: String ("07704 CH") o Number (7672 = PO de France sin sufijo). */
    public record Fila(String madeIn, String article, String coloris,
                       String libelle, String taille, Object po) {
    }

    private PedidoAmiExcel() {
    }

    public static byte[] crear(String nombreHojaEan, Fila... filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("BOLSITAS ANTIHUMEDAD"); // señuelo: no es la primera hoja EAN
            Sheet hoja = libro.createSheet(nombreHojaEan);
            Row cabecera = hoja.createRow(0);
            String[] titulos = {"Made in", "ARTICLE", "COLORIS", "Libellé coloris",
                    "", "TAILLE", "PO", "Commandé", "EAN13", "EAN128"};
            for (int i = 0; i < titulos.length; i++) {
                cabecera.createCell(i).setCellValue(titulos[i]);
            }
            int numFila = 1;
            for (Fila fila : filas) {
                Row f = hoja.createRow(numFila++);
                f.createCell(0).setCellValue(fila.madeIn());
                f.createCell(1).setCellValue(fila.article());
                f.createCell(2).setCellValue(fila.coloris());
                f.createCell(3).setCellValue(fila.libelle());
                f.createCell(5).setCellValue(fila.taille());
                if (fila.po() instanceof Number n) {
                    f.createCell(6).setCellValue(n.doubleValue());
                } else {
                    f.createCell(6).setCellValue((String) fila.po());
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 2: Escribir el test que falla**

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class AmiPedidoExcelTest {

    private static byte[] pedidoTipico() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07703 CH"),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07697 JP"),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665),
                // Cinturón: una fila por talla, mismo PO France.
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "85", 7672),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "95", 7672));
    }

    @Test
    void encuentraElPoDeCadaDestinacionPorSufijo() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());

        assertEquals("07703", pedido.buscar("ULL163.AL0052", "221", "CH").orElseThrow().orderNumber());
        assertEquals("07697", pedido.buscar("ULL163.AL0052", "221", "JP").orElseThrow().orderNumber());
        // PO numérico sin sufijo = France, con padding a 5 dígitos.
        assertEquals("07665", pedido.buscar("ULL163.AL0052", "221", null).orElseThrow().orderNumber());
    }

    @Test
    void elColorCodeEsColorisMasLibelle() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertEquals("001 BLACK", pedido.buscar("UBL029.AL0216", "001", null).orElseThrow().colorCode());
    }

    @Test
    void referenciaNoEncontradaDevuelveVacio() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertTrue(pedido.buscar("ULL999.XX9999", "001", "CH").isEmpty());
        // Referencia existe pero no para esa destinación.
        assertTrue(pedido.buscar("UBL029.AL0216", "001", "CH").isEmpty());
    }

    @Test
    void sinHojaEanFallaConMensajeClaro() {
        byte[] sinEan = PedidoAmiExcel.crear("OTRA COSA");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AmiPedidoExcel.desdeBytes(sinEan));
        assertTrue(error.getMessage().contains("EAN"));
    }

    @Test
    void laHojaEanSeLocalizaPorNombreAunqueNoSeaLaPrimera() throws IOException {
        // pedidoTipico ya mete "BOLSITAS ANTIHUMEDAD" delante; si esto
        // resuelve, es que no se ha asumido "primera hoja del libro".
        Optional<AmiPedidoExcel.FilaPedido> fila =
                AmiPedidoExcel.desdeBytes(pedidoTipico()).buscar("ULL163.AL0052", "221", "CH");
        assertTrue(fila.isPresent());
    }
}
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `mvn test -Dtest=AmiPedidoExcelTest`
Expected: error de compilación "cannot find symbol: AmiPedidoExcel".

- [ ] **Step 4: Implementación**

```java
package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Índice en memoria del excel de pedido de la temporada de AMI (el que sube
 * el usuario en el Paso 2, ej. "AMI EAN H26.xlsx").
 *
 * La hoja buena es la primera cuyo nombre empieza por "EAN" (la temporada
 * cambia: EAN H26, EAN E27...); el libro trae más hojas (bolsitas, copias
 * por artículo) que se ignoran. Las columnas se localizan por el texto de
 * la cabecera de la fila 1, no por posición.
 *
 * La columna PO codifica la destinación: "NNNNN CH" (China), "NNNNN JP"
 * (Japan) o un número sin sufijo (France). El order number de la etiqueta
 * es siempre la parte numérica con padding a 5 dígitos.
 */
public class AmiPedidoExcel {

    /** Una coincidencia del pedido: order number (5 dígitos) y color code ("001 BLACK"). */
    public record FilaPedido(String orderNumber, String colorCode) {
    }

    private record FilaCruda(String article, String coloris, String libelle,
                             String poNumerico, String poSufijo) {
    }

    private final List<FilaCruda> filas;

    private AmiPedidoExcel(List<FilaCruda> filas) {
        this.filas = filas;
    }

    public static AmiPedidoExcel desdeBytes(byte[] contenido) throws IOException {
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet hoja = hojaEan(libro);
            Row cabecera = hoja.getRow(hoja.getFirstRowNum());
            int colArticle = columna(cabecera, "ARTICLE");
            int colColoris = columna(cabecera, "COLORIS");
            int colLibelle = columna(cabecera, "LIBELL");
            int colPo = columna(cabecera, "PO");

            List<FilaCruda> filas = new ArrayList<>();
            for (int i = hoja.getFirstRowNum() + 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) {
                    continue;
                }
                String article = texto(fila.getCell(colArticle));
                String po = textoPo(fila.getCell(colPo));
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
                        texto(fila.getCell(colColoris)).trim(),
                        texto(fila.getCell(colLibelle)).trim(),
                        String.format("%05d", Long.parseLong(numerico)),
                        sufijo.isBlank() ? null : sufijo));
            }
            return new AmiPedidoExcel(filas);
        }
    }

    /**
     * Busca la fila del pedido para una referencia y destinación. Si hay
     * varias (cinturones: una por talla) da igual cuál: comparten PO y
     * color; se prefiere la que coincida en COLORIS con el color del JSON.
     */
    public Optional<FilaPedido> buscar(String referencia, String codigoColor, String sufijoPo) {
        String ref = referencia == null ? "" : referencia.trim().toUpperCase(Locale.ROOT);
        List<FilaCruda> candidatas = filas.stream()
                .filter(fila -> fila.article().equals(ref))
                .filter(fila -> sufijoPo == null
                        ? fila.poSufijo() == null
                        : sufijoPo.equalsIgnoreCase(fila.poSufijo()))
                .toList();
        if (candidatas.isEmpty()) {
            return Optional.empty();
        }
        FilaCruda elegida = candidatas.stream()
                .filter(fila -> fila.coloris().equalsIgnoreCase(codigoColor == null ? "" : codigoColor.trim()))
                .findFirst()
                .orElse(candidatas.get(0));
        String colorCode = elegida.libelle().isBlank()
                ? elegida.coloris()
                : elegida.coloris() + " " + elegida.libelle();
        return Optional.of(new FilaPedido(elegida.poNumerico(), colorCode));
    }

    private static Sheet hojaEan(Workbook libro) {
        for (int i = 0; i < libro.getNumberOfSheets(); i++) {
            if (libro.getSheetName(i).trim().toUpperCase(Locale.ROOT).startsWith("EAN")) {
                return libro.getSheetAt(i);
            }
        }
        throw new IllegalArgumentException(
                "El excel de pedido no tiene ninguna hoja 'EAN ...': ¿es el archivo correcto?");
    }

    private static int columna(Row cabecera, String titulo) {
        for (Cell celda : cabecera) {
            if (texto(celda).trim().toUpperCase(Locale.ROOT).startsWith(titulo)) {
                return celda.getColumnIndex();
            }
        }
        throw new IllegalArgumentException(
                "La hoja EAN del pedido no tiene la columna '" + titulo + "'");
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

    /** El PO puede ser texto ("07704 CH") o numérico (7672.0). */
    private static String textoPo(Cell celda) {
        if (celda == null) {
            return "";
        }
        if (celda.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) celda.getNumericCellValue());
        }
        return texto(celda);
    }
}
```

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=AmiPedidoExcelTest`
Expected: `Tests run: 5, Failures: 0`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java src/test/java/com/puntotres/packinglist/testutil/PedidoAmiExcel.java src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcelTest.java
git commit -m "etiquetas: lector del excel de pedido EAN de AMI

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Tarea 3: Contrato común de etiquetas + servicio de despacho

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/GeneradorEtiquetasCliente.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/CampoEtiquetas.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/ResultadoEtiquetas.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/EtiquetasGenerationService.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/EtiquetasGenerationServiceTest.java`

**Interfaces:**
- Produces (las consume todo lo demás):
  ```java
  public interface GeneradorEtiquetasCliente {
      String claveCliente();                       // clave del catálogo, ej. "AMI"
      boolean soportaDestino(String nombreDestino);
      List<CampoEtiquetas> camposRequeridos(List<DestinoData> destinos);
      ResultadoEtiquetas generar(List<DestinoData> destinos, DatosEnvio envio,
                                 Map<String, byte[]> archivos) throws IOException;
  }
  public record CampoEtiquetas(String nombre, String titulo) {}   // input file del Paso 2
  public class ResultadoEtiquetas {
      public List<ExcelGenerado> getExcels();
      public List<String> getAvisos();
  }
  public class EtiquetasGenerationService {
      public Optional<GeneradorEtiquetasCliente> generadorPara(String claveCliente); // case-insensitive
  }
  ```

- [ ] **Step 1: Escribir el test que falla**

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;

class EtiquetasGenerationServiceTest {

    /** Doble mínimo: solo interesa el despacho por clave. */
    private static GeneradorEtiquetasCliente generadorDe(String clave) {
        return new GeneradorEtiquetasCliente() {
            @Override public String claveCliente() { return clave; }
            @Override public boolean soportaDestino(String nombreDestino) { return true; }
            @Override public List<CampoEtiquetas> camposRequeridos(List<DestinoData> destinos) {
                return List.of();
            }
            @Override public ResultadoEtiquetas generar(List<DestinoData> destinos,
                    DatosEnvio envio, Map<String, byte[]> archivos) {
                return new ResultadoEtiquetas();
            }
        };
    }

    @Test
    void despachaPorClaveDeClienteIgnorandoMayusculas() {
        GeneradorEtiquetasCliente ami = generadorDe("AMI");
        EtiquetasGenerationService servicio = new EtiquetasGenerationService(List.of(ami));

        assertSame(ami, servicio.generadorPara("ami").orElseThrow());
        assertSame(ami, servicio.generadorPara(" AMI ").orElseThrow());
    }

    @Test
    void clienteSinGeneradorDevuelveVacio() {
        EtiquetasGenerationService servicio =
                new EtiquetasGenerationService(List.of(generadorDe("AMI")));
        assertTrue(servicio.generadorPara("ACKERMANN").isEmpty());
        assertTrue(servicio.generadorPara(null).isEmpty());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=EtiquetasGenerationServiceTest`
Expected: error de compilación (no existen los tipos).

- [ ] **Step 3: Implementación**

`CampoEtiquetas.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

/**
 * Un input del Paso 2 de etiquetas que el generador de un cliente pide al
 * usuario ANTES de generar (siempre un archivo .xlsx en esta fase; si un
 * cliente futuro necesita un texto/código, se ampliará con un tipo).
 *
 * nombre: name del input HTML y clave del mapa de archivos.
 * titulo: etiqueta visible, ej. "Introducir excel del pedido de AMI".
 */
public record CampoEtiquetas(String nombre, String titulo) {
}
```

`ResultadoEtiquetas.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import java.util.ArrayList;
import java.util.List;

import com.puntotres.packinglist.service.ExcelGenerado;

/**
 * Salida de la generación de etiquetas: un excel por destinación soportada
 * y los avisos acumulados (destinaciones sin implementar, referencias que
 * no están en el excel de pedido...). Nunca se lanza excepción por datos
 * resolubles por un humano: se avisa y se genera lo que se pueda.
 */
public class ResultadoEtiquetas {

    private final List<ExcelGenerado> excels = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();

    public List<ExcelGenerado> getExcels() { return excels; }
    public List<String> getAvisos() { return avisos; }
}
```

`GeneradorEtiquetasCliente.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;

/**
 * Estrategia de generación de etiquetas de caja de un cliente concreto
 * (análoga a GeneradorPackingListCliente para los packing lists).
 *
 * Cada implementación sabe qué destinaciones soporta, qué datos estáticos
 * usar por destinación y qué archivos extra pedir al usuario en el Paso 2
 * del asistente. Las etiquetas de palet y la "etiqueta de etiqueta" son
 * funcionalidades futuras y NO forman parte de este contrato.
 */
public interface GeneradorEtiquetasCliente {

    /** Clave del cliente en el catálogo de application.yml (ej. "AMI"). */
    String claveCliente();

    /** ¿Este generador reconoce esta destinación del JSON? */
    boolean soportaDestino(String nombreDestino);

    /**
     * Archivos que hay que pedir al usuario para estas destinaciones
     * (Paso 2). Puede depender de qué destinaciones contenga el envío.
     */
    List<CampoEtiquetas> camposRequeridos(List<DestinoData> destinos);

    /**
     * Genera un excel de etiquetas por destinación soportada. archivos:
     * contenido de cada CampoEtiquetas subido, indexado por su nombre.
     */
    ResultadoEtiquetas generar(List<DestinoData> destinos, DatosEnvio envio,
                               Map<String, byte[]> archivos) throws IOException;
}
```

`EtiquetasGenerationService.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Punto de entrada de las etiquetas de caja: localiza el generador del
 * cliente por su clave de catálogo. Un cliente sin generador simplemente
 * no tiene etiquetas implementadas todavía (la web muestra "en
 * desarrollo", igual que el resto de funcionalidades pendientes).
 */
@Service
public class EtiquetasGenerationService {

    private final Map<String, GeneradorEtiquetasCliente> porCliente = new HashMap<>();

    public EtiquetasGenerationService(List<GeneradorEtiquetasCliente> generadores) {
        for (GeneradorEtiquetasCliente generador : generadores) {
            porCliente.put(normalizar(generador.claveCliente()), generador);
        }
    }

    public Optional<GeneradorEtiquetasCliente> generadorPara(String claveCliente) {
        if (claveCliente == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(porCliente.get(normalizar(claveCliente)));
    }

    private static String normalizar(String clave) {
        return clave.trim().toUpperCase(Locale.ROOT);
    }
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=EtiquetasGenerationServiceTest`
Expected: `Tests run: 2, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/ src/test/java/com/puntotres/packinglist/service/etiquetas/EtiquetasGenerationServiceTest.java
git commit -m "etiquetas: contrato multi-cliente y servicio de despacho

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Tarea 4: Plantilla en resources + builder POI de AMI

**Files:**
- Create: `src/main/resources/client-labels/ami-etiquetas-template.xlsx` (copia exacta de `docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx`)
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayout.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilderTest.java`

**Interfaces:**
- Consumes: `CodigoBarrasCode128.png(String)` (Tarea 1).
- Produces (lo consume la Tarea 5):
  ```java
  public record AmiEtiquetaLayout(String nombreHoja, String sufijoPo, int alturaBloque,
          int offsetSegundaEtiqueta, int filaTemporada, int filaReferencia, int filaColor,
          int filaTalla, int filaCantidad, int filaPeso, int filaParcel,
          int filaBarcode, long dxBarcode, long dyBarcode, long cxBarcode, long cyBarcode) {
      public static final AmiEtiquetaLayout CHINA;
      public static final AmiEtiquetaLayout JAPAN;
      public static final AmiEtiquetaLayout FRANCE;
  }
  public class AmiEtiquetasExcelBuilder {
      /** Una entrada por caja física; los String ya vienen formateados; null = celda en blanco / sin barcode. */
      public record EtiquetaCaja(String temporada, String referencia, String colorCode,
                                 String talla, String cantidad, String pesoBruto,
                                 String parcel, String orderNumber) {}
      public byte[] generar(AmiEtiquetaLayout layout, List<EtiquetaCaja> etiquetas) throws IOException;
  }
  ```

- [ ] **Step 1: Copiar la plantilla a resources (byte a byte, sin editar)**

```bash
mkdir -p "src/main/resources/client-labels"
cp "docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx" "src/main/resources/client-labels/ami-etiquetas-template.xlsx"
```

- [ ] **Step 2: Escribir el layout (datos puros, sin lógica)**

```java
package com.puntotres.packinglist.service.etiquetas;

/**
 * Coordenadas (0-based de POI) de la hoja de una destinación en la
 * plantilla client-labels/ami-etiquetas-template.xlsx. Cada hoja trae UN
 * par de etiquetas modelo (2 etiquetas idénticas apiladas en vertical =
 * una hoja A4); la caja i-ésima se escribe desplazada i*alturaBloque y la
 * segunda etiqueta del par a +offsetSegundaEtiqueta. Los valores van en la
 * columna C (índice 2) salvo la temporada, en B (índice 1). El código de
 * barras es una imagen flotante anclada en la columna C con los offsets
 * EMU medidos en la plantilla de ejemplo.
 *
 * sufijoPo: sufijo de la columna PO del excel de pedido para esta
 * destinación ("CH", "JP"); null = PO numérico sin sufijo (France).
 *
 * NO cambiar estas coordenadas sin revisar la plantilla, y viceversa.
 */
public record AmiEtiquetaLayout(
        String nombreHoja, String sufijoPo, int alturaBloque, int offsetSegundaEtiqueta,
        int filaTemporada, int filaReferencia, int filaColor, int filaTalla,
        int filaCantidad, int filaPeso, int filaParcel,
        int filaBarcode, long dxBarcode, long dyBarcode, long cxBarcode, long cyBarcode) {

    public static final int COL_TEMPORADA = 1;
    public static final int COL_VALOR = 2;
    public static final int COL_BARCODE = 2;

    public static final AmiEtiquetaLayout CHINA = new AmiEtiquetaLayout(
            "AMI CHINA", "CH", 34, 17,
            11, 11, 12, 13, 14, 15, 16,
            8, 2971800, 19050, 1047750, 666750);

    public static final AmiEtiquetaLayout JAPAN = new AmiEtiquetaLayout(
            "AMI JAPAN", "JP", 32, 16,
            10, 10, 11, 12, 13, 14, 15,
            7, 2857500, 19050, 990600, 628650);

    public static final AmiEtiquetaLayout FRANCE = new AmiEtiquetaLayout(
            "AMI FRANCE", null, 32, 16,
            10, 10, 11, 12, 13, 14, 15,
            7, 3457575, 9525, 1209675, 762000);

    /** Anclaje de la imagen-dirección de JAPAN (único PNG de la plantilla). */
    public static final int JAPAN_DIRECCION_FILA = 2;
    public static final long JAPAN_DIRECCION_DX = 66675;
    public static final long JAPAN_DIRECCION_DY = 95250;
    public static final long JAPAN_DIRECCION_CX = 2562225;
    public static final long JAPAN_DIRECCION_CY = 1143000;
}
```

- [ ] **Step 3: Escribir el test que falla**

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.etiquetas.AmiEtiquetasExcelBuilder.EtiquetaCaja;

class AmiEtiquetasExcelBuilderTest {

    private final AmiEtiquetasExcelBuilder builder = new AmiEtiquetasExcelBuilder();

    private static EtiquetaCaja etiquetaBolso(String parcel) {
        return new EtiquetaCaja("H26", "ULL163.AL0052", "221 BLACK",
                "U", "50", "5,28 KGS", parcel, "07703");
    }

    @Test
    void generaUnaHojaSoloConLaDestinacionPedida() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(etiquetaBolso("1 / 1")));
        try (XSSFWorkbook libro = abrir(excel)) {
            assertEquals(1, libro.getNumberOfSheets());
            assertEquals("AMI CHINA", libro.getSheetName(0));
        }
    }

    @Test
    void escribeLosValoresEnLasDosEtiquetasDelPar() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(etiquetaBolso("1 / 1")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // Etiqueta 1 (bloque 0)
            assertEquals("H26", texto(hoja, 11, 1));
            assertEquals("ULL163.AL0052", texto(hoja, 11, 2));
            assertEquals("221 BLACK", texto(hoja, 12, 2));
            assertEquals("U", texto(hoja, 13, 2));
            assertEquals("50", texto(hoja, 14, 2));
            assertEquals("5,28 KGS", texto(hoja, 15, 2));
            assertEquals("1 / 1", texto(hoja, 16, 2));
            // Etiqueta 2 = mismas celdas + offset 17
            assertEquals("ULL163.AL0052", texto(hoja, 11 + 17, 2));
            assertEquals("1 / 1", texto(hoja, 16 + 17, 2));
        }
    }

    @Test
    void replicaElBloqueParaCadaCajaYSeparaLasPaginas() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.FRANCE, List.of(
                new EtiquetaCaja("H26", "UBL029.AL0216", "001 BLACK", "85-95",
                        "4-85,33-95", "9,93 KGS", "1 / 2", "07672"),
                new EtiquetaCaja("H26", "UBL029.AL0216", "001 BLACK", "105",
                        "3-105", null, "2 / 2", "07672")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // Caja 1, etiqueta 1 (FRANCE: bloque de 32 filas, valores desde fila 10).
            assertEquals("85-95", texto(hoja, 12, 2));
            assertEquals("4-85,33-95", texto(hoja, 13, 2));
            // Caja 2 = bloque desplazado 32 filas; peso null = celda en blanco.
            assertEquals("UBL029.AL0216", texto(hoja, 10 + 32, 2));
            assertEquals("3-105", texto(hoja, 13 + 32, 2));
            assertEquals("", texto(hoja, 14 + 32, 2));
            assertEquals("2 / 2", texto(hoja, 15 + 32, 2));
            // Salto de página entre las dos cajas (cada par en su A4).
            assertTrue(hoja.getRowBreaks().length >= 1);
            assertEquals(31, hoja.getRowBreaks()[0]);
            // Los estilos/altos del bloque copiado se conservan (fila de
            // cabecera de la 2ª caja con el alto de la plantilla).
            assertEquals(hoja.getRow(2).getHeightInPoints(),
                    hoja.getRow(2 + 32).getHeightInPoints(), 0.01);
        }
    }

    @Test
    void insertaUnCodigoDeBarrasPorEtiquetaYNingunaImagenDeEjemplo() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetasLayoutParaTest(), List.of(
                etiquetaBolso("1 / 2"), etiquetaBolso("2 / 2")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            XSSFDrawing dibujo = hoja.getDrawingPatriarch();
            assertNotNull(dibujo);
            // 2 cajas x 2 etiquetas = 4 códigos de barras, y nada más
            // (las imágenes de ejemplo de la plantilla se limpian).
            assertEquals(4, dibujo.getShapes().size());
        }
    }

    private static AmiEtiquetaLayout AmiEtiquetasLayoutParaTest() {
        return AmiEtiquetaLayout.CHINA;
    }

    @Test
    void enJapanCadaParLlevaAdemasLaDireccionComoImagen() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.JAPAN, List.of(
                etiquetaBolso("1 / 2"), etiquetaBolso("2 / 2")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // 2 cajas x (2 barcodes + 2 direcciones) = 8 imágenes.
            assertEquals(8, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void sinOrderNumberNoHayBarcodePeroElExcelSaleIgual() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                new EtiquetaCaja("H26", "ULL163.AL0052", null, "U", "50",
                        null, "1 / 1", null)));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            assertEquals("ULL163.AL0052", texto(hoja, 11, 2));
            XSSFDrawing dibujo = hoja.getDrawingPatriarch();
            assertTrue(dibujo == null || dibujo.getShapes().isEmpty());
        }
        // Copia para inspección manual, como hace el e2e de packing lists.
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "etiquetas-ami-china-sin-barcode.xlsx"), excel);
    }

    private static XSSFWorkbook abrir(byte[] contenido) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(contenido));
    }

    private static String texto(XSSFSheet hoja, int fila, int col) {
        if (hoja.getRow(fila) == null || hoja.getRow(fila).getCell(col) == null) {
            return "";
        }
        return hoja.getRow(fila).getCell(col).toString().trim();
    }
}
```

- [ ] **Step 4: Ejecutar y ver que falla**

Run: `mvn test -Dtest=AmiEtiquetasExcelBuilderTest`
Expected: error de compilación "cannot find symbol: AmiEtiquetasExcelBuilder".

- [ ] **Step 5: Implementar el builder**

Algoritmo: abrir plantilla → guardar bytes del PNG de dirección (si JAPAN) → borrar las otras 2 hojas → limpiar TODOS los anclajes de dibujo de la hoja (fuera imágenes de ejemplo) → capturar el bloque modelo (valores+estilos+altos+merges) ANTES de escribir → para cada caja i>0 copiar el bloque a `i*alturaBloque` → escribir los valores de cada caja en sus 2 etiquetas → insertar imágenes → salto de página por caja.

```java
package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Escribe el excel de etiquetas de caja de UNA destinación de AMI a partir
 * de la plantilla real (client-labels/ami-etiquetas-template.xlsx, copia
 * del "ETIQUETA CAJA AMI.xlsx" del cliente).
 *
 * La plantilla trae una hoja por destinación con un par de etiquetas
 * modelo y sus imágenes de EJEMPLO; aquí se conserva solo la hoja pedida,
 * se limpian esas imágenes, se replica el bloque modelo para cada caja
 * (estilos, altos de fila y celdas combinadas incluidos) y se insertan el
 * código de barras generado y, en JAPAN, la imagen-dirección extraída de
 * la propia plantilla. Cada par lleva su salto de página: un A4 por caja.
 */
@Service
public class AmiEtiquetasExcelBuilder {

    private static final String RUTA_PLANTILLA = "/client-labels/ami-etiquetas-template.xlsx";

    /**
     * Los datos ya formateados de la etiqueta de una caja física. null =
     * celda en blanco (y sin código de barras si falta orderNumber).
     */
    public record EtiquetaCaja(String temporada, String referencia, String colorCode,
                               String talla, String cantidad, String pesoBruto,
                               String parcel, String orderNumber) {
    }

    public byte[] generar(AmiEtiquetaLayout layout, List<EtiquetaCaja> etiquetas)
            throws IOException {
        try (InputStream plantilla = getClass().getResourceAsStream(RUTA_PLANTILLA);
             XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {

            byte[] direccionJapan = extraerPngDireccion(libro);
            dejarSoloLaHoja(libro, layout.nombreHoja());
            XSSFSheet hoja = libro.getSheetAt(0);
            limpiarImagenesDeEjemplo(hoja);

            BloqueModelo modelo = BloqueModelo.capturar(hoja, layout.alturaBloque());
            for (int i = 1; i < etiquetas.size(); i++) {
                modelo.copiarEn(hoja, i * layout.alturaBloque());
            }
            for (int i = 0; i < etiquetas.size(); i++) {
                int base = i * layout.alturaBloque();
                escribirEtiqueta(hoja, layout, base, etiquetas.get(i));
                escribirEtiqueta(hoja, layout, base + layout.offsetSegundaEtiqueta(),
                        etiquetas.get(i));
                insertarImagenes(libro, hoja, layout, base, etiquetas.get(i), direccionJapan);
                if (i < etiquetas.size() - 1) {
                    hoja.setRowBreak(base + layout.alturaBloque() - 1);
                }
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    // --- pasos ---

    /** La dirección de entrega de JAPAN va como imagen: el único PNG del libro. */
    private static byte[] extraerPngDireccion(XSSFWorkbook libro) {
        for (XSSFPictureData imagen : libro.getAllPictures()) {
            if (imagen.getPictureType() == Workbook.PICTURE_TYPE_PNG) {
                return imagen.getData();
            }
        }
        return null;
    }

    private static void dejarSoloLaHoja(XSSFWorkbook libro, String nombreHoja) {
        for (int i = libro.getNumberOfSheets() - 1; i >= 0; i--) {
            if (!libro.getSheetName(i).equals(nombreHoja)) {
                libro.removeSheetAt(i);
            }
        }
        if (libro.getNumberOfSheets() != 1) {
            throw new IllegalStateException("La plantilla de etiquetas AMI no tiene la hoja '"
                    + nombreHoja + "': revisar client-labels/ami-etiquetas-template.xlsx");
        }
        libro.setActiveSheet(0);
    }

    /** Quita los anclajes de las imágenes de ejemplo (los gifs de barcode y el png). */
    private static void limpiarImagenesDeEjemplo(XSSFSheet hoja) {
        XSSFDrawing dibujo = hoja.getDrawingPatriarch();
        if (dibujo == null) {
            return;
        }
        var ct = dibujo.getCTDrawing();
        while (ct.sizeOfOneCellAnchorArray() > 0) {
            ct.removeOneCellAnchor(0);
        }
        while (ct.sizeOfTwoCellAnchorArray() > 0) {
            ct.removeTwoCellAnchor(0);
        }
    }

    private static void escribirEtiqueta(XSSFSheet hoja, AmiEtiquetaLayout layout,
                                         int base, EtiquetaCaja etiqueta) {
        escribir(hoja, base + layout.filaTemporada(), AmiEtiquetaLayout.COL_TEMPORADA,
                etiqueta.temporada());
        escribir(hoja, base + layout.filaReferencia(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.referencia());
        escribir(hoja, base + layout.filaColor(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.colorCode());
        escribir(hoja, base + layout.filaTalla(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.talla());
        escribir(hoja, base + layout.filaCantidad(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.cantidad());
        escribir(hoja, base + layout.filaPeso(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.pesoBruto());
        escribir(hoja, base + layout.filaParcel(), AmiEtiquetaLayout.COL_VALOR,
                etiqueta.parcel());
    }

    private static void escribir(XSSFSheet hoja, int fila, int col, String valor) {
        XSSFRow f = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell celda = f.getCell(col) != null ? f.getCell(col) : f.createCell(col);
        if (valor == null || valor.isBlank()) {
            celda.setBlank();
        } else {
            celda.setCellValue(valor);
        }
    }

    private void insertarImagenes(XSSFWorkbook libro, XSSFSheet hoja, AmiEtiquetaLayout layout,
                                  int base, EtiquetaCaja etiqueta, byte[] direccionJapan) {
        XSSFDrawing dibujo = hoja.createDrawingPatriarch();
        int[] offsets = {0, layout.offsetSegundaEtiqueta()};
        byte[] barcode = etiqueta.orderNumber() == null || etiqueta.orderNumber().isBlank()
                ? null
                : CodigoBarrasCode128.png(etiqueta.orderNumber());
        for (int offset : offsets) {
            if (barcode != null) {
                int indice = libro.addPicture(barcode, Workbook.PICTURE_TYPE_PNG);
                dibujo.createPicture(anclaje(hoja, AmiEtiquetaLayout.COL_BARCODE,
                        layout.dxBarcode(), base + offset + layout.filaBarcode(),
                        layout.dyBarcode(), layout.cxBarcode(), layout.cyBarcode()), indice);
            }
            if (direccionJapan != null && "AMI JAPAN".equals(layout.nombreHoja())) {
                int indice = libro.addPicture(direccionJapan, Workbook.PICTURE_TYPE_PNG);
                dibujo.createPicture(anclaje(hoja, AmiEtiquetaLayout.COL_BARCODE,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_DX,
                        base + offset + AmiEtiquetaLayout.JAPAN_DIRECCION_FILA,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_DY,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_CX,
                        AmiEtiquetaLayout.JAPAN_DIRECCION_CY), indice);
            }
        }
    }

    /**
     * Anclaje de dos celdas equivalente al oneCellAnchor de la plantilla:
     * desde (col,fila)+offset EMU, con tamaño fijo (cx,cy) EMU repartido
     * sobre las columnas/filas siguientes según sus anchos reales.
     */
    private static XSSFClientAnchor anclaje(XSSFSheet hoja, int col, long dx,
                                            int fila, long dy, long cx, long cy) {
        int col2 = col;
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
                (int) xRestante, (int) yRestante, (short) col, fila, (short) col2, fila2);
        ancla.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
        return ancla;
    }

    private static long anchoColumnaEmu(XSSFSheet hoja, int col) {
        return Units.columnWidthToEMU(hoja.getColumnWidth(col));
    }

    private static long altoFilaEmu(XSSFSheet hoja, int fila) {
        float puntos = hoja.getRow(fila) != null
                ? hoja.getRow(fila).getHeightInPoints()
                : hoja.getDefaultRowHeightInPoints();
        return Units.toEMU(puntos);
    }

    /**
     * El par de etiquetas modelo de la plantilla: valores, estilos, altos
     * de fila y celdas combinadas de las primeras alturaBloque filas,
     * capturados antes de escribir nada para poder replicarlos por caja.
     */
    private record BloqueModelo(List<FilaModelo> filas, List<CellRangeAddress> merges,
                                int altura) {

        private record CeldaModelo(int col, org.apache.poi.ss.usermodel.CellStyle estilo,
                                   CellType tipo, String texto) {
        }

        private record FilaModelo(int fila, float altoPuntos, boolean altoPersonalizado,
                                  List<CeldaModelo> celdas) {
        }

        static BloqueModelo capturar(XSSFSheet hoja, int altura) {
            List<FilaModelo> filas = new ArrayList<>();
            for (int i = 0; i < altura; i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) {
                    continue;
                }
                List<CeldaModelo> celdas = new ArrayList<>();
                for (Cell celda : fila) {
                    celdas.add(new CeldaModelo(celda.getColumnIndex(), celda.getCellStyle(),
                            celda.getCellType(),
                            celda.getCellType() == CellType.STRING
                                    ? celda.getStringCellValue() : null));
                }
                filas.add(new FilaModelo(i, fila.getHeightInPoints(),
                        fila.getRow() != null && ((XSSFRow) fila).getCTRow().getCustomHeight(),
                        celdas));
            }
            List<CellRangeAddress> merges = new ArrayList<>();
            for (CellRangeAddress merge : hoja.getMergedRegions()) {
                if (merge.getLastRow() < altura) {
                    merges.add(merge);
                }
            }
            return new BloqueModelo(filas, merges, altura);
        }

        void copiarEn(XSSFSheet hoja, int filaDestino) {
            for (FilaModelo modelo : filas) {
                XSSFRow fila = hoja.createRow(filaDestino + modelo.fila());
                if (modelo.altoPersonalizado()) {
                    fila.setHeightInPoints(modelo.altoPuntos());
                }
                for (CeldaModelo celdaModelo : modelo.celdas()) {
                    Cell celda = fila.createCell(celdaModelo.col());
                    // Mismo libro: la referencia de estilo se comparte, sin clonar.
                    celda.setCellStyle(celdaModelo.estilo());
                    if (celdaModelo.texto() != null) {
                        celda.setCellValue(celdaModelo.texto());
                    }
                }
            }
            for (CellRangeAddress merge : merges) {
                hoja.addMergedRegion(new CellRangeAddress(
                        merge.getFirstRow() + filaDestino, merge.getLastRow() + filaDestino,
                        merge.getFirstColumn(), merge.getLastColumn()));
            }
        }
    }
}
```

Notas para el implementador:
- `fila.getRow() != null` en `capturar` es un descuido deliberado del plan si no compila: basta `((XSSFRow) fila).getCTRow().getCustomHeight()` con el cast directo (la fila ya es XSSF). Ajustar al compilar.
- Las celdas con texto enriquecido (SUPPLIER CODE con negrita parcial) se copian con `getStringCellValue()`: pierden el formato parcial de runs pero conservan el estilo de celda. Aceptable: la plantilla de la caja 1 (bloque original) queda intacta y las copias son legibles. Si en la revisión visual se quiere fidelidad total, cambiar `CeldaModelo.texto` por `celda.getRichStringCellValue()` (XSSFRichTextString) y copiarlo tal cual — probar que POI no comparta mal los fonts entre libros no aplica aquí (mismo libro, es seguro).
- `Units.columnWidthToEMU(int)` existe en POI 5.x (`org.apache.poi.util.Units`). Si el nombre difiere, la conversión manual es `anchoEn256avos / 256.0 * 7 * Units.EMU_PER_PIXEL` — pero comprobar primero el método de Units.

- [ ] **Step 6: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=AmiEtiquetasExcelBuilderTest`
Expected: `Tests run: 6, Failures: 0`.

- [ ] **Step 7: Inspección visual (obligatoria en esta tarea)**

Abrir `target/etiquetas-ami-china-sin-barcode.xlsx` (lo deja el último test) con Excel/LibreOffice y comprobar: bordes/estilos de la 2ª caja iguales a la 1ª, saltos de página uno por par (Vista → Vista previa de salto de página), y en un run con barcode (añadir un `Files.write` temporal si hace falta) que la imagen cae a la derecha de ORDER NUMBER como en `docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx`.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/client-labels/ src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayout.java src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilderTest.java
git commit -m "etiquetas: builder POI de AMI sobre la plantilla real

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Tarea 5: `AmiEtiquetasGenerador` (orquestación AMI)

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java`

**Interfaces:**
- Consumes: `AmiPedidoExcel` (T2), `AmiEtiquetaLayout`/`AmiEtiquetasExcelBuilder` (T4), contrato (T3), `CajaData.esCinturon()`, `ExcelGenerado(destino, nombreFichero, contenido, cajasPendientes)`.
- Produces: bean `@Service AmiEtiquetasGenerador implements GeneradorEtiquetasCliente` con `claveCliente()=="AMI"` y `CAMPO_PEDIDO = new CampoEtiquetas("pedido", "Introducir excel del pedido de AMI")`. Lo consume la Tarea 6 vía `EtiquetasGenerationService`.

**Reglas de negocio que implementa (del spec):**
- Destinaciones AMI y su mapeo (normalizando trim+uppercase): `CHINA`→layout CHINA, `JAPAN`→JAPAN, `FRANCE` y `PARIS`→FRANCE. Cualquier otra ⇒ aviso `"Destinación 'X' sin etiquetas de AMI implementadas: se omite"` y no genera ese excel.
- Una caja física = un `numeroCaja` dentro de la destinación (los cinturones multi-talla llegan como varias `CajaData` con el mismo número). Orden por `numeroCaja` ascendente; `PARCEL = posición " / " total` empezando en 1.
- Bolsos/carteras: SIZE=`U`, QUANTITY=cantidad. Cinturones: SIZE=tallas ascendentes unidas por `-` (`85-90-95`), QUANTITY=pares `cantidad-talla` unidos por `,` (`2-85,3-90,1-95`), ordenados por talla.
- GROSS WEIGHT: suma de `pesoBrutoKg` de las líneas líder (primera aparición de referencia+color en la caja; las demás tallas de la misma ref+color llevan null). Si falta alguno ⇒ null ⇒ celda en blanco + caja a `cajasPendientes`. Formato `String.format(Locale.of("es","ES"), "%.2f KGS", peso)`.
- ORDER NUMBER/COLOR CODE: `pedido.buscar(referencia, color, layout.sufijoPo())`. Si no está ⇒ aviso `"Referencia 'X' (DESTINO) no encontrada en el excel de pedido"` + fallback: orderNumber = parte numérica de `CajaData.numeroPedido` (null si no hay), colorCode = `codigoColor` del JSON. Si está pero difiere de `numeroPedido` del JSON ⇒ aviso de discrepancia (sin bloquear).
- Caja con varias referencias o colores (caja mixta real): aviso + se etiqueta con la primera referencia+color (el spec no lo contempla; no bloquear).
- Nombre de fichero: `("Etiquetas_AMI_" + destino + "_" + envio.getNumeroFactura() + ".xlsx").replaceAll("[\\\\/:*?\"<>|\\s]+", "_")`.
- `camposRequeridos`: `List.of(CAMPO_PEDIDO)` si alguna destinación es soportada; si ninguna, lista vacía. `generar` sin el archivo "pedido" en el mapa ⇒ `IllegalArgumentException` (el controlador ya lo valida antes; esto es cinturón y tirantes).

- [ ] **Step 1: Escribir el test que falla**

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class AmiEtiquetasGeneradorTest {

    private final AmiEtiquetasGenerador generador =
            new AmiEtiquetasGenerador(new AmiEtiquetasExcelBuilder());

    // --- fixtures ---

    private static DatosEnvio cabecera() {
        DatosEnvio envio = new DatosEnvio();
        envio.setTemporada("H26");
        envio.setNumeroFactura("F-123");
        return envio;
    }

    private static CajaData caja(int numero, String referencia, String color, String talla,
                                 int cantidad, Double pesoBruto, String pedido) {
        CajaData caja = new CajaData();
        caja.setNumeroCaja(numero);
        caja.setReferencia(referencia);
        caja.setCodigoColor(color);
        caja.setTalla(talla);
        caja.setCantidad(cantidad);
        caja.setPesoBrutoKg(pesoBruto);
        caja.setNumeroPedido(pedido);
        return caja;
    }

    private static DestinoData destino(String nombre, CajaData... cajas) {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino(nombre);
        destino.setCajas(List.of(cajas));
        return destino;
    }

    private static byte[] pedido() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07703 CH"),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "85", 7672),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "95", 7672),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "105", 7672));
    }

    // --- tests ---

    @Test
    void generaUnExcelPorDestinacionSoportada() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665")),
                        destino("CHINA", caja(1, "ULL163.AL0052", "221", null, 40, 4.10, "07703"))),
                cabecera(), Map.of("pedido", pedido()));

        assertEquals(2, resultado.getExcels().size());
        ExcelGenerado paris = resultado.getExcels().get(0);
        assertEquals("PARIS", paris.getDestino());
        assertEquals("Etiquetas_AMI_PARIS_F-123.xlsx", paris.getNombreFichero());
        try (XSSFWorkbook libro = abrir(paris)) {
            assertEquals("AMI FRANCE", libro.getSheetName(0));
        }
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(1))) {
            assertEquals("AMI CHINA", libro.getSheetName(0));
        }
        // Para inspección manual, como el e2e de packing lists.
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", paris.getNombreFichero()), paris.getContenido());
    }

    @Test
    void cinturonesMultiTallaVanEnUnaEtiquetaConTallasYCantidades() throws IOException {
        // Caja 2 con tres tallas (mismo numeroCaja); solo la líder lleva peso.
        ResultadoEtiquetas resultado = generador.generar(List.of(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665"),
                        caja(2, "UBL029.AL0216", "001", "95", 33, 9.93, "07672"),
                        caja(2, "UBL029.AL0216", "001", "85", 4, null, "07672"),
                        caja(2, "UBL029.AL0216", "001", "105", 3, null, "07672"))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // Caja 2 = segundo bloque (FRANCE: 32 filas por bloque).
            assertEquals("UBL029.AL0216", texto(hoja, 10 + 32, 2));
            assertEquals("85-95-105", texto(hoja, 12 + 32, 2));
            assertEquals("4-85,33-95,3-105", texto(hoja, 13 + 32, 2));
            assertEquals("9,93 KGS", texto(hoja, 14 + 32, 2));
            assertEquals("2 / 2", texto(hoja, 15 + 32, 2));
            // La caja 1 (bolso) es talla única.
            assertEquals("U", texto(hoja, 12, 2));
            assertEquals("50", texto(hoja, 13, 2));
            assertEquals("1 / 2", texto(hoja, 15, 2));
            // COLOR CODE sale del excel de pedido (coloris + libellé).
            assertEquals("001 BLACK", texto(hoja, 11 + 32, 2));
        }
    }

    @Test
    void destinacionNoReconocidaSeOmiteConAviso() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("HONG KONG", caja(1, "ULL163.AL0052", "221", null, 10, 1.0, null))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getExcels().isEmpty());
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("HONG KONG")));
    }

    @Test
    void referenciaAusenteDelPedidoAvisaYGeneraConFallback() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("PARIS", caja(1, "USL999.XX0000", "007", null, 10, 2.0, "07699"))),
                cabecera(), Map.of("pedido", pedido()));

        assertEquals(1, resultado.getExcels().size());
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("USL999.XX0000")));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            // Fallback: color del JSON tal cual.
            assertEquals("007", texto(libro.getSheetAt(0), 11, 2));
        }
    }

    @Test
    void poDelExcelDiscrepanteDelJsonAvisa() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07777"))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("07777") && aviso.contains("07665")));
    }

    @Test
    void pesoPendienteDejaLaCajaEnCajasPendientes() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, null, "07665"))),
                cabecera(), Map.of("pedido", pedido()));

        assertEquals(1, resultado.getExcels().get(0).getCajasPendientes().size());
    }

    @Test
    void declaraElCampoDelExcelDePedidoSoloSiHayDestinosSoportados() {
        assertEquals("pedido", generador.camposRequeridos(
                List.of(destino("CHINA"))).get(0).nombre());
        assertTrue(generador.camposRequeridos(List.of(destino("HONG KONG"))).isEmpty());
        assertTrue(generador.soportaDestino("paris"));
        assertTrue(!generador.soportaDestino("HONG KONG"));
    }

    private static XSSFWorkbook abrir(ExcelGenerado excel) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(excel.getContenido()));
    }

    private static String texto(XSSFSheet hoja, int fila, int col) {
        if (hoja.getRow(fila) == null || hoja.getRow(fila).getCell(col) == null) {
            return "";
        }
        return hoja.getRow(fila).getCell(col).toString().trim();
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=AmiEtiquetasGeneradorTest`
Expected: error de compilación "cannot find symbol: AmiEtiquetasGenerador".

- [ ] **Step 3: Implementación**

```java
package com.puntotres.packinglist.service.etiquetas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.etiquetas.AmiEtiquetasExcelBuilder.EtiquetaCaja;

/**
 * Etiquetas de caja de AMI: tres destinaciones (China, Japan, France; el
 * JSON suele llamar PARIS a la de France). Necesita del usuario el excel
 * del pedido completo de la temporada (ej. "AMI EAN H26.xlsx") para el
 * order number (columna PO, que distingue destinación por sufijo) y el
 * color code. Una caja física = un numeroCaja: los cinturones multi-talla
 * llegan como varias CajaData del mismo número y comparten par de
 * etiquetas (SIZE "85-90-95", QUANTITY "4-85,33-95,...").
 */
@Service
public class AmiEtiquetasGenerador implements GeneradorEtiquetasCliente {

    static final CampoEtiquetas CAMPO_PEDIDO =
            new CampoEtiquetas("pedido", "Introducir excel del pedido de AMI");

    private static final Map<String, AmiEtiquetaLayout> LAYOUT_POR_DESTINO = Map.of(
            "CHINA", AmiEtiquetaLayout.CHINA,
            "JAPAN", AmiEtiquetaLayout.JAPAN,
            "FRANCE", AmiEtiquetaLayout.FRANCE,
            "PARIS", AmiEtiquetaLayout.FRANCE);

    private final AmiEtiquetasExcelBuilder builder;

    public AmiEtiquetasGenerador(AmiEtiquetasExcelBuilder builder) {
        this.builder = builder;
    }

    @Override
    public String claveCliente() {
        return "AMI";
    }

    @Override
    public boolean soportaDestino(String nombreDestino) {
        return nombreDestino != null
                && LAYOUT_POR_DESTINO.containsKey(normalizar(nombreDestino));
    }

    @Override
    public List<CampoEtiquetas> camposRequeridos(List<DestinoData> destinos) {
        boolean alguno = destinos.stream()
                .anyMatch(destino -> soportaDestino(destino.getNombreDestino()));
        return alguno ? List.of(CAMPO_PEDIDO) : List.of();
    }

    @Override
    public ResultadoEtiquetas generar(List<DestinoData> destinos, DatosEnvio envio,
                                      Map<String, byte[]> archivos) throws IOException {
        byte[] contenidoPedido = archivos.get(CAMPO_PEDIDO.nombre());
        if (contenidoPedido == null) {
            throw new IllegalArgumentException("Falta el excel del pedido de AMI");
        }
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(contenidoPedido);

        ResultadoEtiquetas resultado = new ResultadoEtiquetas();
        for (DestinoData destino : destinos) {
            AmiEtiquetaLayout layout = LAYOUT_POR_DESTINO.get(normalizar(destino.getNombreDestino()));
            if (layout == null) {
                resultado.getAvisos().add("Destinación '" + destino.getNombreDestino()
                        + "' sin etiquetas de AMI implementadas: se omite");
                continue;
            }
            resultado.getExcels().add(
                    generarDestino(destino, layout, pedido, envio, resultado.getAvisos()));
        }
        return resultado;
    }

    private ExcelGenerado generarDestino(DestinoData destino, AmiEtiquetaLayout layout,
                                         AmiPedidoExcel pedido, DatosEnvio envio,
                                         List<String> avisos) throws IOException {
        // Una caja física por numeroCaja, en orden ascendente.
        Map<Integer, List<CajaData>> porNumero = new LinkedHashMap<>();
        destino.getCajas().stream()
                .sorted(Comparator.comparingInt(CajaData::getNumeroCaja))
                .forEach(caja -> porNumero
                        .computeIfAbsent(caja.getNumeroCaja(), n -> new ArrayList<>())
                        .add(caja));

        List<EtiquetaCaja> etiquetas = new ArrayList<>();
        List<CajaData> cajasPendientes = new ArrayList<>();
        int posicion = 0;
        int total = porNumero.size();
        for (Map.Entry<Integer, List<CajaData>> entrada : porNumero.entrySet()) {
            posicion++;
            etiquetas.add(etiquetaDe(entrada.getValue(), posicion, total, layout,
                    pedido, envio, destino.getNombreDestino(), avisos, cajasPendientes));
        }

        String nombreFichero = ("Etiquetas_AMI_" + destino.getNombreDestino() + "_"
                + envio.getNumeroFactura() + ".xlsx").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        byte[] contenido = builder.generar(layout, etiquetas);
        return new ExcelGenerado(destino.getNombreDestino(), nombreFichero,
                contenido, cajasPendientes);
    }

    private EtiquetaCaja etiquetaDe(List<CajaData> lineas, int posicion, int total,
                                    AmiEtiquetaLayout layout, AmiPedidoExcel pedido,
                                    DatosEnvio envio, String nombreDestino,
                                    List<String> avisos, List<CajaData> cajasPendientes) {
        CajaData lider = lineas.get(0);

        // Caja mixta de verdad (varias referencias o colores): el spec no la
        // contempla para etiquetas; se etiqueta con la primera y se avisa.
        Set<String> refsColores = new LinkedHashSet<>();
        for (CajaData linea : lineas) {
            refsColores.add(linea.getReferencia() + "|" + linea.getCodigoColor());
        }
        if (refsColores.size() > 1) {
            avisos.add("La caja " + lider.getNumeroCaja() + " de " + nombreDestino
                    + " mezcla varias referencias/colores: la etiqueta lleva "
                    + lider.getReferencia() + " " + lider.getCodigoColor());
        }

        String talla;
        String cantidad;
        if (lider.esCinturon()) {
            List<CajaData> ordenadas = lineas.stream()
                    .filter(linea -> refsColores.size() == 1
                            || (lider.getReferencia() + "|" + lider.getCodigoColor())
                                    .equals(linea.getReferencia() + "|" + linea.getCodigoColor()))
                    .sorted(Comparator.comparingInt(AmiEtiquetasGenerador::tallaNumerica))
                    .toList();
            talla = String.join("-", ordenadas.stream()
                    .map(CajaData::getTalla).toList());
            cantidad = String.join(",", ordenadas.stream()
                    .map(linea -> linea.getCantidad() + "-" + linea.getTalla()).toList());
        } else {
            talla = "U";
            cantidad = String.valueOf(lineas.stream().mapToInt(CajaData::getCantidad).sum());
        }

        // El peso es de la caja física y lo lleva la línea líder de cada
        // referencia+color (las demás tallas van a null).
        Double peso = null;
        boolean pesoCompleto = true;
        Set<String> vistos = new LinkedHashSet<>();
        for (CajaData linea : lineas) {
            if (!vistos.add(linea.getReferencia() + "|" + linea.getCodigoColor())) {
                continue;
            }
            if (linea.getPesoBrutoKg() == null) {
                pesoCompleto = false;
            } else {
                peso = (peso == null ? 0 : peso) + linea.getPesoBrutoKg();
            }
        }
        if (!pesoCompleto) {
            peso = null;
            cajasPendientes.add(lider);
        }

        Optional<AmiPedidoExcel.FilaPedido> fila =
                pedido.buscar(lider.getReferencia(), lider.getCodigoColor(), layout.sufijoPo());
        String orderNumber;
        String colorCode;
        if (fila.isPresent()) {
            orderNumber = fila.get().orderNumber();
            colorCode = fila.get().colorCode();
            String pedidoJson = soloDigitos(lider.getNumeroPedido());
            if (!pedidoJson.isBlank()
                    && Long.parseLong(pedidoJson) != Long.parseLong(orderNumber)) {
                avisos.add("Caja " + lider.getNumeroCaja() + " de " + nombreDestino
                        + ": el pedido del JSON (" + lider.getNumeroPedido()
                        + ") no coincide con el PO del excel de pedido (" + orderNumber
                        + "); la etiqueta lleva el del excel");
            }
        } else {
            avisos.add("Referencia '" + lider.getReferencia() + "' (" + nombreDestino
                    + ") no encontrada en el excel de pedido: order number y color "
                    + "salen del JSON");
            String pedidoJson = soloDigitos(lider.getNumeroPedido());
            orderNumber = pedidoJson.isBlank()
                    ? null : String.format("%05d", Long.parseLong(pedidoJson));
            colorCode = lider.getCodigoColor();
        }

        String pesoTexto = peso == null
                ? null : String.format(Locale.of("es", "ES"), "%.2f KGS", peso);
        return new EtiquetaCaja(envio.getTemporada(), lider.getReferencia(), colorCode,
                talla, cantidad, pesoTexto, posicion + " / " + total, orderNumber);
    }

    private static int tallaNumerica(CajaData caja) {
        try {
            return Integer.parseInt(caja.getTalla().trim());
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE; // tallas raras al final, sin romper
        }
    }

    private static String soloDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("[^0-9]", "");
    }

    private static String normalizar(String nombre) {
        return nombre.trim().toUpperCase(Locale.ROOT);
    }
}
```

Nota: `Locale.of("es", "ES")` es Java 19+; en Java 17 usar `new Locale("es", "ES")` (deprecated pero funcional) o `Locale.forLanguageTag("es-ES")` — usar `Locale.forLanguageTag("es-ES")`, tanto aquí como en el test si hiciera falta.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=AmiEtiquetasGeneradorTest`
Expected: `Tests run: 7, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java
git commit -m "etiquetas: generador AMI (China/Japan/France) con pedido EAN

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Tarea 6: Flujo web (Paso 2 dinámico + descargas + tarjeta de resultados)

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/web/EnvioEnCurso.java`
- Modify: `src/main/java/com/puntotres/packinglist/web/PackingListController.java` (quitar el stub de `/descargar-etiquetas` de las líneas 343-347; añadir endpoints)
- Create: `src/main/resources/templates/etiquetas.html`
- Modify: `src/main/resources/templates/resultados.html` (Sección 3, líneas 72-77)
- Test: `src/test/java/com/puntotres/packinglist/web/PackingListControllerTest.java` (sustituir `descargarEtiquetasDevuelve404PorqueAunNoExiste`, líneas 428-432)

**Interfaces:**
- Consumes: `EtiquetasGenerationService.generadorPara(clave)`, `GeneradorEtiquetasCliente` (T3/T5), `PedidoAmiExcel` (testutil, T2).
- Produces (rutas):
  - `GET /etiquetas` → vista `etiquetas` (Paso 2). Redirige a `/` si no hay envío, a `/revision` si aún no se ha generado nada, a `/resultados` si el cliente no tiene generador.
  - `POST /etiquetas/generar` (multipart) → genera, guarda en sesión, `redirect:/resultados`. Archivo que falta ⇒ flash `error` y `redirect:/etiquetas`.
  - `GET /descargar-etiquetas/{nombreFichero}` → descarga individual (reemplaza al stub 404 sin parámetro).

- [ ] **Step 1: Ampliar `EnvioEnCurso`**

Añadir junto a `volcadoErp`:

```java
    private final List<ExcelGenerado> etiquetas = new ArrayList<>();
    private final List<String> avisosEtiquetas = new ArrayList<>();
```

- getters `getEtiquetas()` / `getAvisosEtiquetas()` con el estilo de los existentes.
- En `reiniciar()`: `etiquetas.clear(); avisosEtiquetas.clear();`.

- [ ] **Step 2: Escribir los tests de controlador que fallan**

En `PackingListControllerTest`, **eliminar** `descargarEtiquetasDevuelve404PorqueAunNoExiste` y añadir (usar los helpers existentes del test para llegar a resultados con el cliente AMI y el JSON de ejemplo `envio-ami-bags-y-belts.json`; seguir el patrón de `generarProduceElVolcadoErpDescargableDelEnvioCompleto`, líneas 388-418):

```java
    @Test
    void elPasoDeEtiquetasPideElExcelDePedidoDeAmi() throws Exception {
        MockHttpSession sesion = sesionConEnvioGenerado(); // helper existente o equivalente
        mockMvc.perform(get("/etiquetas").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Introducir excel del pedido de AMI")))
                .andExpect(content().string(containsString("PARIS")));
    }

    @Test
    void generarEtiquetasDejaLosExcelsDescargables() throws Exception {
        MockHttpSession sesion = sesionConEnvioGenerado();
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new PedidoAmiExcel.Fila("SPAIN", "USL728.AL217", "NOIR", "BLACK", "U", 7685),
                new PedidoAmiExcel.Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "85", 7672),
                new PedidoAmiExcel.Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "95", 7672),
                new PedidoAmiExcel.Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "105", 7672));

        mockMvc.perform(multipart("/etiquetas/generar")
                        .file(new MockMultipartFile("pedido", "AMI EAN H26.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                pedido))
                        .session(sesion))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/resultados"));

        MvcResult descarga = mockMvc.perform(
                        get("/descargar-etiquetas/Etiquetas_AMI_PARIS_{factura}.xlsx",
                                "F-2026-001").session(sesion)) // factura del helper
                .andExpect(status().isOk())
                .andReturn();
        try (XSSFWorkbook libro = new XSSFWorkbook(
                new ByteArrayInputStream(descarga.getResponse().getContentAsByteArray()))) {
            assertEquals("AMI FRANCE", libro.getSheetName(0));
        }
    }

    @Test
    void generarEtiquetasSinArchivoVuelveAlPasoConError() throws Exception {
        MockHttpSession sesion = sesionConEnvioGenerado();
        mockMvc.perform(multipart("/etiquetas/generar").session(sesion))
                .andExpect(redirectedUrl("/etiquetas"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void clienteSinGeneradorDeEtiquetasSigueEnDesarrollo() throws Exception {
        MockHttpSession sesion = sesionConEnvioGeneradoDeClienteGenerico(); // helper existente o crear
        mockMvc.perform(get("/resultados").session(sesion))
                .andExpect(content().string(containsString("En desarrollo")));
        mockMvc.perform(get("/etiquetas").session(sesion))
                .andExpect(redirectedUrl("/resultados"));
    }
```

Ajustar nombres de helper/factura a los reales del fichero de test (leerlo primero); si no existe un helper reutilizable, crearlo siguiendo el estilo del propio fichero. Import de `PedidoAmiExcel` desde testutil.

- [ ] **Step 3: Ejecutar y ver que fallan**

Run: `mvn test -Dtest=PackingListControllerTest`
Expected: fallos de compilación (endpoints inexistentes) o 404.

- [ ] **Step 4: Implementar controlador + vistas**

Controlador — inyectar `EtiquetasGenerationService etiquetasService` (campo + parámetro de constructor, estilo existente) e importar `org.springframework.web.multipart.support.StandardMultipartHttpServletRequest` no hace falta: usar `@RequestParam Map` tampoco — método con `MultipartHttpServletRequest`:

```java
    // --- Paso extra: etiquetas de caja ---

    /** Paso 2 de etiquetas: inputs que pida el generador del cliente. */
    @GetMapping("/etiquetas")
    public String etiquetas(Model model, RedirectAttributes redirect) {
        if (envioEnCurso.estaVacio()) {
            return sinEnvio(redirect);
        }
        if (envioEnCurso.getExcels().isEmpty()) {
            return "redirect:/revision";
        }
        GeneradorEtiquetasCliente generador = generadorEtiquetasDelEnvio().orElse(null);
        if (generador == null) {
            return "redirect:/resultados";
        }
        List<DestinoData> destinos = destinosDelEnvio();
        model.addAttribute("campos", generador.camposRequeridos(destinos));
        model.addAttribute("destinos", destinos.stream()
                .map(destino -> Map.of(
                        "nombre", destino.getNombreDestino(),
                        "soportado", generador.soportaDestino(destino.getNombreDestino())))
                .toList());
        model.addAttribute("cabecera", envioEnCurso.getCabecera());
        return "etiquetas";
    }

    @PostMapping("/etiquetas/generar")
    public String generarEtiquetas(MultipartHttpServletRequest peticion,
                                   RedirectAttributes redirect) {
        if (envioEnCurso.estaVacio()) {
            return sinEnvio(redirect);
        }
        GeneradorEtiquetasCliente generador = generadorEtiquetasDelEnvio().orElse(null);
        if (generador == null) {
            return "redirect:/resultados";
        }
        List<DestinoData> destinos = destinosDelEnvio();
        Map<String, byte[]> archivos = new LinkedHashMap<>();
        try {
            for (CampoEtiquetas campo : generador.camposRequeridos(destinos)) {
                MultipartFile archivo = peticion.getFile(campo.nombre());
                if (archivo == null || archivo.isEmpty()) {
                    redirect.addFlashAttribute("error",
                            "Falta el archivo: " + campo.titulo());
                    return "redirect:/etiquetas";
                }
                archivos.put(campo.nombre(), archivo.getBytes());
            }
            ResultadoEtiquetas resultado =
                    generador.generar(destinos, envioEnCurso.getCabecera(), archivos);
            envioEnCurso.getEtiquetas().clear();
            envioEnCurso.getEtiquetas().addAll(resultado.getExcels());
            envioEnCurso.getAvisosEtiquetas().clear();
            envioEnCurso.getAvisosEtiquetas().addAll(resultado.getAvisos());
        } catch (IOException | RuntimeException e) {
            redirect.addFlashAttribute("error",
                    "No se pudieron generar las etiquetas: " + e.getMessage());
            return "redirect:/etiquetas";
        }
        return "redirect:/resultados";
    }

    @GetMapping("/descargar-etiquetas/{nombreFichero}")
    public ResponseEntity<byte[]> descargarEtiquetas(@PathVariable String nombreFichero) {
        return envioEnCurso.getEtiquetas().stream()
                .filter(excel -> excel.getNombreFichero().equals(nombreFichero))
                .findFirst()
                .map(excel -> ResponseEntity.ok()
                        .contentType(TIPO_XLSX)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                                .attachment().filename(excel.getNombreFichero()).build().toString())
                        .body(excel.getContenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Optional<GeneradorEtiquetasCliente> generadorEtiquetasDelEnvio() {
        return etiquetasService.generadorPara(envioEnCurso.getCabecera().getClaveCliente());
    }

    private List<DestinoData> destinosDelEnvio() {
        return envioEnCurso.getImportado().getDestinos().stream()
                .map(EnvioImportado.DestinoImportado::getDestino)
                .toList();
    }
```

(Borrar el stub antiguo `descargarEtiquetas()` sin parámetro. Imports nuevos: `MultipartHttpServletRequest` de `org.springframework.web.multipart`, `Optional`, `DestinoData`, y los tipos de `service.etiquetas`.)

En `generar()` (POST /generar), tras `envioEnCurso.setVolcadoErp(volcado);` añadir — los datos han podido cambiar:

```java
        // Regenerar invalida las etiquetas ya hechas (pesos/cajas cambiados).
        envioEnCurso.getEtiquetas().clear();
        envioEnCurso.getAvisosEtiquetas().clear();
```

En `resultados()` añadir al modelo:

```java
        model.addAttribute("etiquetas", envioEnCurso.getEtiquetas());
        model.addAttribute("avisosEtiquetas", envioEnCurso.getAvisosEtiquetas());
        model.addAttribute("hayGeneradorEtiquetas", generadorEtiquetasDelEnvio().isPresent());
```

Vista `etiquetas.html` (nueva; mirar `revision.html`/`entrada.html` para reutilizar clases CSS y el fragmento de cabecera):

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragmentos :: head('Etiquetas de caja')}"></head>
<body>
<header th:replace="~{fragmentos :: cabecera('· Etiquetas de caja')}"></header>
<main>
    <p class="resumen-envio">
        Factura <strong th:text="${cabecera.numeroFactura}"></strong> ·
        temporada <strong th:text="${cabecera.temporada}"></strong>
    </p>

    <div class="avisos error" th:if="${error != null}">
        <p th:text="${error}"></p>
    </div>

    <section class="tarjeta">
        <h3 class="titulo-seccion">🏷️ Etiquetas de caja — datos adicionales</h3>
        <p>Destinaciones del envío:</p>
        <ul>
            <li th:each="destino : ${destinos}">
                <span th:text="${destino['nombre']}"></span>
                <span th:if="${destino['soportado']}" class="badge ok">etiquetas disponibles</span>
                <span th:unless="${destino['soportado']}" class="badge pendiente">
                    sin etiquetas implementadas: se omitirá</span>
            </li>
        </ul>

        <form th:action="@{/etiquetas/generar}" method="post" enctype="multipart/form-data">
            <div class="campo" th:each="campo : ${campos}">
                <label th:for="${campo.nombre}" th:text="${campo.titulo}"></label>
                <input type="file" th:id="${campo.nombre}" th:name="${campo.nombre}"
                       accept=".xlsx" required>
            </div>
            <p th:if="${campos.isEmpty()}">
                Ninguna destinación de este envío tiene etiquetas implementadas.
            </p>
            <button type="submit" th:disabled="${campos.isEmpty()}">Generar etiquetas</button>
            <a class="boton secundario" th:href="@{/resultados}">Volver a resultados</a>
        </form>
    </section>
</main>
</body>
</html>
```

(Comprobar en `estilo.css` los nombres reales de clases de avisos — buscar cómo pinta `revision.html` sus avisos y calcar; `${error}` llega por flash attribute.)

`resultados.html` — sustituir la Sección 3 (líneas 72-77) por:

```html
    <!-- Sección 3: etiquetas de caja -->
    <section class="tarjeta seccion-salida" th:if="${hayGeneradorEtiquetas}">
        <h3 class="titulo-seccion">🏷️ Etiquetas de caja</h3>

        <div th:if="${!avisosEtiquetas.isEmpty()}" class="avisos">
            <p th:each="aviso : ${avisosEtiquetas}" th:text="${aviso}"></p>
        </div>

        <p th:if="${etiquetas.isEmpty()}">
            Requieren datos adicionales según las destinaciones del envío.
        </p>
        <table th:if="${!etiquetas.isEmpty()}">
            <thead>
            <tr><th>Fichero</th><th>Destinación</th><th>Pesos</th><th></th></tr>
            </thead>
            <tbody>
            <tr th:each="excel : ${etiquetas}">
                <td th:text="${excel.nombreFichero}"></td>
                <td th:text="${excel.destino}"></td>
                <td>
                    <span th:if="${excel.tienePesosPendientes()}" class="badge pendiente"
                          th:text="|${excel.cajasPendientes.size()} cajas sin peso|"></span>
                    <span th:if="${!excel.tienePesosPendientes()}" class="badge ok">completos</span>
                </td>
                <td>
                    <a th:href="@{/descargar-etiquetas/{fichero}(fichero=${excel.nombreFichero})}">Descargar</a>
                </td>
            </tr>
            </tbody>
        </table>

        <a class="boton" th:href="@{/etiquetas}"
           th:text="${etiquetas.isEmpty()} ? 'Preparar etiquetas' : 'Regenerar etiquetas'"></a>
    </section>
    <section class="tarjeta seccion-salida deshabilitada" th:unless="${hayGeneradorEtiquetas}">
        <h3 class="titulo-seccion">🏷️ Etiquetas de caja</h3>
        <p>🔒 En desarrollo — Próximamente</p>
        <button type="button" disabled>Descargar</button>
    </section>
```

- [ ] **Step 5: Ejecutar los tests del controlador**

Run: `mvn test -Dtest=PackingListControllerTest`
Expected: todos verdes (los nuevos y los preexistentes, que no deben romperse).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/web/ src/main/resources/templates/ src/test/java/com/puntotres/packinglist/web/PackingListControllerTest.java
git commit -m "etiquetas: paso web de datos adicionales, generación y descargas

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Tarea 7: Verificación end-to-end y documentación

**Files:**
- Modify: `CLAUDE.md` (sección Arquitectura: una mención al pipeline de etiquetas)
- Modify: `docs/Packing Lists/campos-json-por-cliente.md` solo si algo de lo implementado contradice lo documentado (no debería)

- [ ] **Step 1: Suite completa**

Run: `mvn test`
Expected: todo verde, incluidos los tests previos al plan.

- [ ] **Step 2: Prueba manual con los archivos reales**

1. `mvn spring-boot:run` → http://localhost:8080 (recordar en Windows: si el 8080 queda ocupado, `netstat -ano | findstr :8080` + `taskkill /F /PID <pid>`).
2. Cliente AMI + JSON de `src/test/resources/ejemplos/envio-ami-bags-y-belts.json` → revisar → generar.
3. En resultados: "Preparar etiquetas" → subir `docs/Etiquetas cajas/AMI EAN H26.xlsx` → generar.
4. Descargar `Etiquetas_AMI_PARIS_*.xlsx` y compararlo A OJO con `docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx` (hoja AMI FRANCE): estilos, barcode con "07672" debajo, dos etiquetas por A4, saltos de página. Comprobar los avisos en pantalla (la referencia `USL728.AL217` del JSON de ejemplo NO está en el excel de pedido real → debe salir el aviso y el fallback).
5. Probar también un envío con destinación CHINA o JAPAN si se tiene JSON a mano (opcional).

- [ ] **Step 3: Actualizar CLAUDE.md**

En la sección "Arquitectura (lo esencial)", tras la línea del volcado ERP del diagrama, añadir `  → EtiquetasGenerationService → GeneradorEtiquetasCliente (AmiEtiquetasGenerador) → excels de etiquetas (opcional, Paso 2 web)` y, en el párrafo Multi-cliente, una frase: las etiquetas de caja tienen su propia estrategia `GeneradorEtiquetasCliente` en `service/etiquetas/` despachada por clave de cliente; plantillas en `src/main/resources/client-labels/` (misma regla: no editar sin revisar su builder/layout).

- [ ] **Step 4: Commit final**

```bash
git add CLAUDE.md
git commit -m "etiquetas: fase 1 completa (plumbing común + AMI); docs

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Self-review (hecho al escribir el plan)

- **Cobertura del spec**: arquitectura estrategia por cliente ✔ (T3); flujo 2 pasos con inputs dinámicos post-detección de destinaciones ✔ (T6); alerta destinación no reconocida ✔ (T5, test `destinacionNoReconocidaSeOmiteConAviso`); par de etiquetas por caja, media hoja A4, apiladas verticalmente ✔ (plantilla real ya viene así, T4); numeración 1..N por destinación ✔ (T5); hoja correcta por nombre en ambos libros ✔ (T2 `hojaEan`, T4 `dejarSoloLaHoja`); "en desarrollo" para clientes sin implementar ✔ (T6, test `clienteSinGeneradorDeEtiquetasSigueEnDesarrollo`); campos ORDER NUMBER (numérico + barcode como imagen flotante), REFERENCE, COLOR CODE, SIZE, QUANTITY, GROSS WEIGHT, PARCEL ✔ (T4/T5); caso cinturones multi-talla ✔ (T5); aviso referencia ausente del pedido ✔ (T5); etiquetas de palet fuera de alcance, sin implementar nada ✔; etiqueta-de-etiqueta ignorada ✔.
- **Placeholders**: ninguno — todo el código está en los pasos; los dos puntos de riesgo de API de POI (Units.columnWidthToEMU, customHeight) llevan alternativa concreta escrita.
- **Consistencia de tipos**: `EtiquetaCaja` (T4) la consume T5 con los mismos 8 campos; `FilaPedido(orderNumber, colorCode)` idéntico en T2/T5; `CampoEtiquetas(nombre, titulo)` en T3/T5/T6; `camposRequeridos(List<DestinoData>)` uniforme; nombre de fichero `Etiquetas_AMI_<destino>_<factura>.xlsx` igual en T5 y en el test de T6.
