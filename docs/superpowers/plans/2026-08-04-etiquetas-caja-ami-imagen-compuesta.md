# Etiqueta de caja de AMI con imagen compuesta — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adaptar las etiquetas de caja de AMI a la plantilla nueva del cliente: el EAN-13 pasa a ser una imagen compuesta con los cuatro textos de la etiqueta de artículo, desaparece el Code 128 del PO, el EAN128 se reubica, y la hoja `CODIGOS BARRAS EXTRA` se rehace como rejilla imprimible de 10 artículos por A4.

**Architecture:** Se baja `EtiquetaArticulo` a la capa común `service/etiquetas/` y se construyen sobre él dos renderizadores compartidos: `ImagenEtiquetaArticulo` (PNG compuesto, para la etiqueta de caja) y el par `RejillaEtiquetas` + `BloqueEtiquetaArticulo` (celdas + anclaje, extraídos de `EtiquetasArticuloExcelBuilder`, para las etiquetas de artículo y para la hoja extra). Los generadores de cliente siguen decidiendo *qué* poner; la capa común decide *cómo* se dibuja.

**Tech Stack:** Java 17, Spring Boot 3.5, Apache POI 5.4.1 (XSSF), barcode4j (`EAN13Bean`, `Code128Bean`), `java.awt.Graphics2D` + `javax.imageio.ImageIO`, JUnit 5.

**Spec:** [docs/superpowers/specs/2026-08-04-etiquetas-caja-ami-imagen-compuesta-design.md](../specs/2026-08-04-etiquetas-caja-ami-imagen-compuesta-design.md)

## Global Constraints

- Idioma: **código y nombres de clase en inglés/español como ya están; javadoc, comentarios, avisos y UI en español**. Los nombres de clase nuevos van en español, como el resto de `service/etiquetas/`.
- **Nunca fallar en silencio, nunca bloquear por datos que un humano puede resolver.** Ningún camino nuevo lanza excepciones: se acumulan avisos en `ResultadoEtiquetas` y el excel se genera igual con las celdas o imágenes que falten en blanco.
- **El código de barras nunca se reescala.** Se genera con la geometría final y se pega 1:1 con `Graphics2D.drawImage(imagen, x, y, null)`. Prohibido `RenderingHints.KEY_INTERPOLATION`, `getScaledInstance`, y el `drawImage` de 6 argumentos con ancho/alto.
- **Un peso por caja física, en su línea líder.** No se suman líneas de una caja.
- Los pesos son `Double`; `null` significa "desconocido".
- Los tests instancian los servicios con `new` (JUnit 5 puro). Los tests de builder **reabren el `.xlsx` generado con POI y comprueban celdas e imágenes reales**.
- Las plantillas de `src/main/resources/client-labels/` tienen coordenadas de las que dependen los builders: no editarlas sin revisar su builder.
- Comando de la suite: `mvn test`. Una clase suelta: `mvn test -Dtest=NombreDeLaClase`.
- **Alcance: solo AMI.** APC y el resto de clientes no cambian de comportamiento. Las etiquetas de artículo (`/etiquetas-articulo`) tampoco: los refactors que las tocan son a comportamiento constante.

## Datos medidos de la plantilla nueva

Todos los valores de este plan salen de descomprimir `docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx` (versión del 2026-08-04). No hay que volver a medirlos.

**Filas de valor** (0-based, relativas a la base del bloque):

| Campo | CHINA | JAPAN | FRANCE |
|---|---|---|---|
| `filaOrderNumber` | 8 | 7 | 7 |
| `filaReferencia` | 10 | 9 | 9 |
| `filaTemporada` (col B) | 11 | 10 | 10 |
| `filaColor` | 12 | 11 | 11 |
| `filaTalla` | 13 | 12 | 12 |
| `filaCantidad` | 14 | 13 | 13 |
| `filaPeso` | 15 | 14 | 14 |
| `filaParcel` | 16 | 15 | 15 |

`alturaBloque`/`offsetSegundaEtiqueta` no cambian: 34/17 en CHINA, 32/16 en JAPAN y FRANCE.

**Anclajes de imagen** `AnclajeBloque(fila, dx, dy, cx, cy)`, todos en la columna C:

| | CHINA | JAPAN | FRANCE |
|---|---|---|---|
| `imagenArticulo` | `(10, 2481943, 54429, 1674091, 762000)` | `(9, 2241177, 26896, 1674091, 762000)` | `(9, 2937163, 69273, 1674091, 762000)` |
| `ean128` | `(8, 1352897, 143333, 2727960, 452413)` | `(7, 918884, 62682, 3009900, 502991)` | `(7, 2057400, 156331, 2575560, 430408)` |

`JAPAN_DIRECCION` no cambia: `(2, 66675, 95250, 2562225, 1143000)`.

El hueco de la imagen compuesta mide **1.674.091 × 762.000 EMU en las tres destinaciones**; su proporción es **2,1969**.

---

## Estructura de ficheros

**Crear:**
- `src/main/java/com/puntotres/packinglist/service/etiquetas/ImagenEtiquetaArticulo.java` — compone el PNG de 4 textos + EAN-13.
- `src/main/java/com/puntotres/packinglist/service/etiquetas/RejillaEtiquetas.java` — la rejilla A4 (anchos, altos, `pageSetup`, bloques) compartida por las etiquetas de artículo y la hoja extra.
- `src/main/java/com/puntotres/packinglist/service/etiquetas/BloqueEtiquetaArticulo.java` — escribe las 4 celdas de un artículo y da el anclaje de su código de barras.
- `src/test/java/com/puntotres/packinglist/service/etiquetas/ImagenEtiquetaArticuloTest.java`
- `src/test/java/com/puntotres/packinglist/service/etiquetas/BloqueEtiquetaArticuloTest.java`
- `src/test/java/com/puntotres/packinglist/service/etiquetas/HojaCodigosBarrasExtraMaquetacionTest.java`

**Mover:**
- `service/etiquetasarticulo/EtiquetaArticulo.java` → `service/etiquetas/EtiquetaArticulo.java`.

**Modificar:**
- `service/etiquetas/CodigoBarrasEan13.java` — overload con proporción.
- `service/etiquetas/AjusteFuente.java` — no marcar `shrinkToFit` en celdas combinadas.
- `service/etiquetas/AmiEtiquetaLayout.java` — sin `po()`, `ean13()` → `imagenArticulo()`, coordenadas nuevas.
- `service/etiquetas/AmiPedidoExcel.java` — `colorCode` numérico + `colorCompleto`.
- `service/etiquetas/AmiEtiquetasExcelBuilder.java` — sin Code 128 del PO, imagen compuesta, extracción de la dirección de JAPAN por anclaje.
- `service/etiquetas/AmiEtiquetasGenerador.java` — construye la `EtiquetaArticulo` del primer artículo y las filas extra.
- `service/etiquetas/FilaCodigoBarrasExtra.java` — nueva forma.
- `service/etiquetas/HojaCodigosBarrasExtra.java` — reescrita como rejilla.
- `service/etiquetasarticulo/EtiquetasArticuloExcelBuilder.java` — delega en `RejillaEtiquetas` y `BloqueEtiquetaArticulo`.
- `src/main/resources/client-labels/ami-etiquetas-template.xlsx` — sustituida por la del cliente.
- `CLAUDE.md`.
- Tests: `CodigoBarrasEan13Test`, `AjusteFuenteTest`, `AmiEtiquetaLayoutTest`, `AmiPedidoExcelTest`, `AmiPedidoRealTest`, `AmiEtiquetasExcelBuilderTest`, `AmiEtiquetasGeneradorTest`, `HojaCodigosBarrasExtraTest`, `EtiquetasArticuloExcelBuilderTest`, `AmiEtiquetasArticuloGeneradorTest`, `EtiquetasArticuloMaquetacionTest`.

---

## Task 1: EAN-13 con proporción pedida

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasEan13.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasEan13Test.java`

**Interfaces:**
- Consumes: nada de tareas anteriores.
- Produces: `public static Optional<byte[]> CodigoBarrasEan13.png(String ean13, double proporcion)`. Con `proporcion <= 0` devuelve exactamente lo mismo que `png(String)`.

**Por qué:** la imagen compuesta necesita el código con una altura de barras concreta. Generarlo así y pegarlo 1:1 es la única forma de no reescalarlo. `CodigoBarrasCode128` ya resuelve lo mismo con este patrón; esto es su gemelo.

- [ ] **Step 1: Escribir los tests que fallan**

Añadir a `CodigoBarrasEan13Test.java` (los imports que falten: `java.awt.image.BufferedImage`, `java.io.ByteArrayInputStream`, `javax.imageio.ImageIO`):

```java
    /** El EAN13 del mock de la plantilla del cliente. */
    private static final String EAN_VALIDO = "3666598354771";

    @Test
    void conProporcionLaImagenSaleConEsaProporcion() throws Exception {
        byte[] png = CodigoBarrasEan13.png(EAN_VALIDO, 3.3).orElseThrow();
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(3.3, (double) imagen.getWidth() / imagen.getHeight(), 0.15);
    }

    @Test
    void conProporcionCeroSaleExactamenteLoMismoQueSinProporcion() {
        assertArrayEquals(CodigoBarrasEan13.png(EAN_VALIDO).orElseThrow(),
                CodigoBarrasEan13.png(EAN_VALIDO, 0).orElseThrow());
    }

    @Test
    void laProporcionNoCambiaElAnchoDelCodigo() throws Exception {
        // El ancho lo fija el ancho de módulo, no la proporción: lo que se
        // ajusta es el alto de las barras. Si esto cambiara, la imagen
        // compuesta dejaría de poder dimensionarse a partir del código.
        BufferedImage suelto = ImageIO.read(new ByteArrayInputStream(
                CodigoBarrasEan13.png(EAN_VALIDO).orElseThrow()));
        BufferedImage ajustado = ImageIO.read(new ByteArrayInputStream(
                CodigoBarrasEan13.png(EAN_VALIDO, 3.3).orElseThrow()));
        assertEquals(suelto.getWidth(), ajustado.getWidth());
    }

    @Test
    void unEan13InvalidoConProporcionSigueDevolviendoVacio() {
        assertTrue(CodigoBarrasEan13.png("1234567890123", 3.3).isEmpty());
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=CodigoBarrasEan13Test`
Expected: FAIL de compilación — `png(String, double)` no existe.

- [ ] **Step 3: Implementar**

En `CodigoBarrasEan13.java`, dejar `png(String)` delegando y añadir el overload:

```java
    public static Optional<byte[]> png(String ean13) {
        return png(ean13, 0);
    }

    /**
     * Igual que {@link #png(String)} pero ajustando el alto de las barras para
     * que la imagen salga con la proporción ancho/alto pedida (0 = la que
     * salga). Lo usa ImagenEtiquetaArticulo: el código se pega en la imagen
     * compuesta a resolución nativa, sin reescalar, así que tiene que salir ya
     * con la forma del hueco que le toca.
     *
     * El ancho NO cambia: lo fija el ancho de módulo. Lo que se ajusta es el
     * alto de las barras.
     */
    public static Optional<byte[]> png(String ean13, double proporcion) {
        if (!esValido(ean13)) {
            return Optional.empty();
        }
        String digitos = ean13.trim();
        EAN13Bean codigo = new EAN13Bean();
        codigo.doQuietZone(true);
        ajustarElAltoALaProporcion(codigo, digitos, proporcion);
        BitmapCanvasProvider lienzo =
                new BitmapCanvasProvider(300, BufferedImage.TYPE_BYTE_BINARY, false, 0);
        try {
            codigo.generateBarcode(lienzo, digitos);
            lienzo.finish();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(lienzo.getBufferedImage(), "png", salida);
            return Optional.of(salida.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "No se pudo generar el código de barras de '" + ean13 + "'", e);
        }
    }

    /**
     * Deja el alto total (barras + dígitos legibles) en ancho/proporción,
     * tocando solo el alto de las barras. Si la proporción pedida no deja
     * sitio ni para las barras se ignora: mejor una imagen algo achatada que
     * una sin barras.
     */
    private static void ajustarElAltoALaProporcion(EAN13Bean codigo, String texto,
                                                   double proporcion) {
        if (proporcion <= 0) {
            return;
        }
        var dimensiones = codigo.calcDimensions(texto);
        double altoDeLosDigitos = dimensiones.getHeight() - codigo.getBarHeight();
        double altoDeLasBarras = dimensiones.getWidth() / proporcion - altoDeLosDigitos;
        if (altoDeLasBarras > 0) {
            codigo.setBarHeight(altoDeLasBarras);
        }
    }
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=CodigoBarrasEan13Test`
Expected: PASS, todos los tests de la clase (los antiguos incluidos).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasEan13.java src/test/java/com/puntotres/packinglist/service/etiquetas/CodigoBarrasEan13Test.java
git commit -m "el EAN13 se puede generar con la proporcion de su hueco"
```

---

## Task 2: Bajar `EtiquetaArticulo` a la capa común

**Files:**
- Move: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetaArticulo.java` → `src/main/java/com/puntotres/packinglist/service/etiquetas/EtiquetaArticulo.java`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloExcelBuilder.java`, `AmiEtiquetasArticuloGenerador.java`, `HojaEtiquetas.java`
- Modify (tests): cualquier test de `etiquetasarticulo` que lo importe.

**Interfaces:**
- Produces: `com.puntotres.packinglist.service.etiquetas.EtiquetaArticulo`, record público de 5 componentes `String`: `referencia`, `talla`, `color`, `pedido`, `ean13`. Sin cambios de contenido: `talla` viene ya formateada (`"Size: U"`), `pedido` también (`"Cde: 07703"`), `color` es el color completo (`"221 DARK COFFEE"`), `ean13` es `null` si la fila no traía un EAN-13 válido.

**Por qué:** `etiquetasarticulo` depende de `etiquetas`, no al revés. La etiqueta de caja y la hoja extra necesitan este record, así que baja a la capa común. Es un refactor **a comportamiento constante**: no se toca ni un valor.

- [ ] **Step 1: Mover el fichero y cambiarle el paquete**

```bash
git mv src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetaArticulo.java src/main/java/com/puntotres/packinglist/service/etiquetas/EtiquetaArticulo.java
```

En el fichero movido, cambiar la primera línea a:

```java
package com.puntotres.packinglist.service.etiquetas;
```

- [ ] **Step 2: Arreglar los usos**

Buscar todos los usos y añadir el import donde haga falta:

```bash
grep -rln "EtiquetaArticulo" src/main src/test
```

En cada fichero de `service/etiquetasarticulo/` (y sus tests) que lo use, añadir:

```java
import com.puntotres.packinglist.service.etiquetas.EtiquetaArticulo;
```

Ojo: `EtiquetasArticuloGenerationService`, `ResultadoEtiquetasArticulo` y `ExcelEtiquetasArticulo` no lo usan; `HojaEtiquetas`, `EtiquetasArticuloExcelBuilder` y `AmiEtiquetasArticuloGenerador` sí. No confundir con `EtiquetasArticulo*`, que son otras clases.

- [ ] **Step 3: Ejecutar la suite entera**

Run: `mvn test`
Expected: PASS. Es un refactor puro: **si falla un test, algo se ha movido de más**.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "EtiquetaArticulo baja a la capa comun de etiquetas"
```

---

## Task 3: La imagen compuesta

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/ImagenEtiquetaArticulo.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/ImagenEtiquetaArticuloTest.java`

**Interfaces:**
- Consumes: `CodigoBarrasEan13.png(String, double)` (Task 1), `EtiquetaArticulo` en `service.etiquetas` (Task 2).
- Produces: `public static byte[] ImagenEtiquetaArticulo.png(EtiquetaArticulo etiqueta, double proporcion)`. Nunca devuelve `null` ni lanza por datos: sin EAN-13 válido devuelve la imagen con los cuatro textos y el hueco del código vacío.

**Geometría.** El lienzo se dimensiona **a partir del código de barras**, no al revés: así el código cabe entero a resolución nativa y no hay nada que reescalar. Con `MARGEN = 0,02` del lado y `ALTO_LINEA = 0,16` del alto por cada una de las dos líneas de texto:

```
fraccionAncho = 1 - 2*MARGEN                = 0,96
fraccionAlto  = 1 - 2*ALTO_LINEA - 2*MARGEN = 0,64
proporcionDelCodigo = proporcion * fraccionAncho / fraccionAlto
ancho = round(anchoDelCodigo / fraccionAncho)
alto  = round(ancho / proporcion)
```

Con la proporción del hueco de AMI (2,1969) y el ancho nativo del EAN-13 a 300 dpi (~440 px), sale un lienzo de ~458 × 208 px con el código ocupando ~440 × 134 px abajo. Las barras quedan a ~9 mm impresos: **es la maqueta que pide el cliente y hay que validarla con un lector físico** (Task 11).

- [ ] **Step 1: Escribir los tests que fallan**

Crear `src/test/java/com/puntotres/packinglist/service/etiquetas/ImagenEtiquetaArticuloTest.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * La imagen compuesta que va en el hueco del EAN13 de la etiqueta de caja de
 * AMI: los cuatro textos de la etiqueta de artículo con el código de barras
 * debajo, todo en un solo PNG.
 */
class ImagenEtiquetaArticuloTest {

    /** El EAN13 y los cuatro textos del mock de la plantilla del cliente. */
    private static final EtiquetaArticulo ETIQUETA = new EtiquetaArticulo(
            "ULL163.AL0052", "Size: U", "221 DARK COFFEE", "Cde: 07703", "3666598354771");

    /** La del hueco de la plantilla: 1674091 / 762000. */
    private static final double PROPORCION = 2.1969;

    private static BufferedImage leer(byte[] png) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    void laImagenSaleConLaProporcionDelHueco() throws Exception {
        BufferedImage imagen = leer(ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        assertEquals(PROPORCION, (double) imagen.getWidth() / imagen.getHeight(), 0.02);
    }

    @Test
    void elLienzoSeDimensionaAPartirDelCodigo() throws Exception {
        // Es lo que garantiza que el código quepa a resolución nativa: si el
        // lienzo se fijara aparte, habría que escalar el código para meterlo.
        BufferedImage codigo = leer(CodigoBarrasEan13.png(
                ETIQUETA.ean13(), PROPORCION * 0.96 / 0.64).orElseThrow());
        BufferedImage imagen = leer(ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        assertEquals(Math.round(codigo.getWidth() / 0.96), imagen.getWidth());
    }

    @Test
    void elCodigoDeBarrasNoSeInterpola() throws Exception {
        // Un reescalado con interpolación mete grises entre barra y hueco. La
        // banda central del código, pegada 1:1, es blanco y negro puros.
        BufferedImage imagen = leer(ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        int y = imagen.getHeight() * 3 / 5;   // dentro de las barras
        int grises = 0;
        for (int x = 0; x < imagen.getWidth(); x++) {
            int rgb = imagen.getRGB(x, y) & 0xFFFFFF;
            if (rgb != 0x000000 && rgb != 0xFFFFFF) {
                grises++;
            }
        }
        assertEquals(0, grises, "hay píxeles grises en la banda del código de barras");
    }

    @Test
    void unaFilaDeBarrasTieneBarrasDeVerdad() throws Exception {
        // Que la banda sea blanco y negro puros no basta: podría ser toda
        // blanca. Tiene que haber negro.
        BufferedImage imagen = leer(ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        int y = imagen.getHeight() * 3 / 5;
        int negros = 0;
        for (int x = 0; x < imagen.getWidth(); x++) {
            if ((imagen.getRGB(x, y) & 0xFFFFFF) == 0x000000) {
                negros++;
            }
        }
        assertTrue(negros > 20, "la banda del código no tiene barras: " + negros);
    }

    @Test
    void sinEan13SaleLaImagenIgualConSusTextos() throws Exception {
        EtiquetaArticulo sinCodigo = new EtiquetaArticulo(
                "ULL163.AL0052", "Size: U", "221 DARK COFFEE", "Cde: 07703", null);
        byte[] png = ImagenEtiquetaArticulo.png(sinCodigo, PROPORCION);
        assertNotNull(png);
        BufferedImage imagen = leer(png);
        assertEquals(PROPORCION, (double) imagen.getWidth() / imagen.getHeight(), 0.02);
    }

    @Test
    void losTextosNullNoRompenLaImagen() throws Exception {
        EtiquetaArticulo vacia = new EtiquetaArticulo(null, null, null, null, null);
        assertNotNull(leer(ImagenEtiquetaArticulo.png(vacia, PROPORCION)));
    }

    @Test
    void unTextoLargoNoSeSaleDelLienzo() throws Exception {
        // No se puede afirmar el cuerpo elegido desde fuera, pero sí que la
        // imagen sigue teniendo el mismo tamaño: el texto encoge, el lienzo no.
        EtiquetaArticulo larga = new EtiquetaArticulo(
                "ULL163.AL0052 / ULL745.AL0103 / UBL029.AL0216", "Size: U",
                "221 DARK COFFEE / 001 BLACK", "Cde: 07703", "3666598354771");
        BufferedImage normal = leer(ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        BufferedImage grande = leer(ImagenEtiquetaArticulo.png(larga, PROPORCION));
        assertEquals(normal.getWidth(), grande.getWidth());
        assertEquals(normal.getHeight(), grande.getHeight());
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=ImagenEtiquetaArticuloTest`
Expected: FAIL de compilación — `ImagenEtiquetaArticulo` no existe.

- [ ] **Step 3: Implementar**

Crear `src/main/java/com/puntotres/packinglist/service/etiquetas/ImagenEtiquetaArticulo.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * La etiqueta de artículo entera en un solo PNG: los cuatro textos arriba
 * (referencia y color a la izquierda, talla y pedido a la derecha) y el
 * EAN-13 centrado debajo.
 *
 * Va en el hueco que la plantilla de etiquetas de caja de AMI reserva para
 * ella. Como imagen flotante única se puede colocar donde quiera el cliente
 * dentro de una maqueta de celdas fija.
 *
 * <b>El código de barras no se reescala nunca.</b> Se pide a
 * CodigoBarrasEan13 con la proporción que le toca —el ancho de un EAN-13 lo
 * fija el ancho de módulo, lo que se ajusta es el alto de las barras— y se
 * pega con drawImage a resolución nativa. Reescalar un código de barras con
 * interpolación lo deja bonito en pantalla e ilegible para un lector físico,
 * así que el lienzo se dimensiona A PARTIR del código y no al revés.
 *
 * Las proporciones (márgenes y alto de las líneas de texto) están medidas del
 * mock que el cliente dejó en su plantilla (290 × 132 px).
 */
public final class ImagenEtiquetaArticulo {

    /** Margen a cada lado, como fracción del lado correspondiente. */
    private static final double MARGEN = 0.02;
    /** Alto de cada una de las dos líneas de texto, como fracción del alto. */
    private static final double ALTO_LINEA = 0.16;
    /** Ancho del lienzo cuando no hay código que pegar, en px. */
    private static final int ANCHO_SIN_CODIGO_PX = 440;
    /** Cuerpo de la fuente como fracción del alto de su línea. */
    private static final double CUERPO = 0.75;
    private static final int CUERPO_MINIMO_PX = 8;
    private static final String FUENTE = "Arial";

    private ImagenEtiquetaArticulo() {
    }

    /**
     * proporcion = ancho/alto del hueco donde va la imagen. Nunca lanza por
     * datos: sin EAN-13 válido sale la imagen con los textos y sin código, y
     * un texto null se dibuja como vacío.
     */
    public static byte[] png(EtiquetaArticulo etiqueta, double proporcion) {
        BufferedImage codigo = codigoDeBarras(etiqueta.ean13(), proporcion);
        int ancho = codigo == null
                ? ANCHO_SIN_CODIGO_PX
                : (int) Math.round(codigo.getWidth() / fraccionAncho());
        int alto = (int) Math.round(ancho / proporcion);
        int margenX = (int) Math.round(ancho * MARGEN);
        int margenY = (int) Math.round(alto * MARGEN);
        int altoLinea = (int) Math.round(alto * ALTO_LINEA);

        BufferedImage lienzo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D pincel = lienzo.createGraphics();
        try {
            pincel.setColor(Color.WHITE);
            pincel.fillRect(0, 0, ancho, alto);
            pincel.setColor(Color.BLACK);
            pincel.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int anchoUtil = ancho - 2 * margenX;
            linea(pincel, etiqueta.referencia(), etiqueta.talla(),
                    margenX, margenY, anchoUtil, altoLinea);
            linea(pincel, etiqueta.color(), etiqueta.pedido(),
                    margenX, margenY + altoLinea, anchoUtil, altoLinea);
            if (codigo != null) {
                // 1:1, sin escalar y sin RenderingHints de interpolación.
                pincel.drawImage(codigo, (ancho - codigo.getWidth()) / 2,
                        alto - margenY - codigo.getHeight(), null);
            }
            pincel.setColor(Color.BLACK);
            pincel.drawRect(0, 0, ancho - 1, alto - 1);
        } finally {
            pincel.dispose();
        }
        return aPng(lienzo);
    }

    // --- pasos ---

    private static double fraccionAncho() {
        return 1 - 2 * MARGEN;
    }

    private static double fraccionAlto() {
        return 1 - 2 * ALTO_LINEA - 2 * MARGEN;
    }

    /** null si no hay EAN-13 válido: la imagen sale igual, sin código. */
    private static BufferedImage codigoDeBarras(String ean13, double proporcion) {
        return CodigoBarrasEan13.png(ean13, proporcion * fraccionAncho() / fraccionAlto())
                .map(ImagenEtiquetaArticulo::leer)
                .orElse(null);
    }

    /**
     * Una línea de la etiqueta: un texto pegado a la izquierda y otro a la
     * derecha. El cuerpo baja hasta que los dos caben sin solaparse; por
     * debajo del mínimo se deja de encoger y se acepta el solape antes que
     * dejar la etiqueta ilegible.
     */
    private static void linea(Graphics2D pincel, String izquierda, String derecha,
                              int x, int y, int ancho, int alto) {
        String izq = izquierda == null ? "" : izquierda;
        String der = derecha == null ? "" : derecha;
        int cuerpo = Math.max(CUERPO_MINIMO_PX, (int) Math.round(alto * CUERPO));
        Font fuente = new Font(FUENTE, Font.PLAIN, cuerpo);
        while (cuerpo > CUERPO_MINIMO_PX && anchoDe(pincel, fuente, izq)
                + anchoDe(pincel, fuente, der) > ancho) {
            cuerpo--;
            fuente = new Font(FUENTE, Font.PLAIN, cuerpo);
        }
        pincel.setFont(fuente);
        int base = y + alto - pincel.getFontMetrics().getDescent();
        pincel.drawString(izq, x, base);
        pincel.drawString(der, x + ancho - pincel.getFontMetrics().stringWidth(der), base);
    }

    private static int anchoDe(Graphics2D pincel, Font fuente, String texto) {
        return pincel.getFontMetrics(fuente).stringWidth(texto);
    }

    private static BufferedImage leer(byte[] png) {
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el código de barras generado", e);
        }
    }

    private static byte[] aPng(BufferedImage imagen) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir la imagen de la etiqueta", e);
        }
    }
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=ImagenEtiquetaArticuloTest`
Expected: PASS.

Si `elCodigoDeBarrasNoSeInterpola` falla, **no relajar el test**: significa que el código se está escalando en algún punto. Comprobar que el `drawImage` es el de 4 argumentos y que `BufferedImage.TYPE_INT_RGB` no está aplicando ningún filtro.

- [ ] **Step 5: Dejar una muestra para mirarla**

Añadir este test al final de la clase, que deja la imagen en `target/` para inspección visual:

```java
    @Test
    void dejaUnaMuestraEnTargetParaMirarla() throws Exception {
        // Existe para abrirla y compararla con el mock del cliente: lo que
        // afirma es lo único afirmable de un artefacto cuyo juez es el ojo.
        Path muestra = Path.of("target/imagen-etiqueta-articulo.png");
        Files.write(muestra, ImagenEtiquetaArticulo.png(ETIQUETA, PROPORCION));
        assertNotNull(ImageIO.read(muestra.toFile()), "la muestra no es un PNG legible");
    }
```

Imports nuevos para este test: `java.nio.file.Files`, `java.nio.file.Path`.

Run: `mvn test -Dtest=ImagenEtiquetaArticuloTest`
Expected: PASS y `target/imagen-etiqueta-articulo.png` existe. Abrirla y comprobar que se parece al mock del cliente (`docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx`, imagen `image2.png`).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/ImagenEtiquetaArticulo.java src/test/java/com/puntotres/packinglist/service/etiquetas/ImagenEtiquetaArticuloTest.java
git commit -m "imagen compuesta de etiqueta de articulo con el EAN13 sin reescalar"
```

---

## Task 4: `AjusteFuente` no miente sobre celdas combinadas

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AjusteFuente.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AjusteFuenteTest.java`

**Interfaces:**
- Produces: `AjusteFuente.ajustar(Cell)` sin cambios de firma. Cambia el comportamiento: si la celda está dentro de una región combinada, **no** se marca `shrinkToFit`; el tamaño calculado se aplica igual.

**Por qué:** en la plantilla nueva `REFERENCE` pasa a ser celda combinada (`C10:C11`) y **Excel ignora `shrinkToFit` en celdas combinadas**, igual que lo ignora con `wrapText`. Escribir el atributo ahí sería un atributo inerte que hace creer que hay una red de seguridad que no existe. Es la misma decisión que ya se tomó para APC.

**Precondición:** el conjunto de celdas combinadas se calcula la primera vez que se ajusta una celda de esa hoja y se cachea. Los dos builders que usan esta clase copian todos sus bloques (y con ellos todos sus merges) **antes** de escribir ningún valor, así que el caché nunca se queda corto. Queda documentado en el javadoc.

- [ ] **Step 1: Escribir el test que falla**

Añadir a `AjusteFuenteTest.java` (imports que falten: `org.apache.poi.ss.util.CellRangeAddress`):

```java
    @Test
    void enUnaCeldaCombinadaNoSeMarcaShrinkToFitPeroSiSeEncogeLaFuente() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet();
            hoja.setColumnWidth(0, 10 * 256);
            hoja.addMergedRegion(new CellRangeAddress(0, 1, 0, 0));
            XSSFCellStyle estilo = libro.createCellStyle();
            XSSFFont fuente = libro.createFont();
            fuente.setFontHeightInPoints((short) 22);
            estilo.setFont(fuente);
            Cell celda = hoja.createRow(0).createCell(0);
            celda.setCellStyle(estilo);
            celda.setCellValue("UN TEXTO LARGUISIMO QUE NO CABE NI DE LEJOS");

            new AjusteFuente(libro).ajustar(celda);

            XSSFCellStyle resultante = (XSSFCellStyle) celda.getCellStyle();
            assertTrue(resultante.getFont().getFontHeightInPoints() < 22,
                    "la fuente tenía que encoger igual");
            assertFalse(resultante.getShrinkToFit(),
                    "Excel ignora shrinkToFit en celdas combinadas: no hay que marcarlo");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void enUnaCeldaSueltaSeSigueMarcandoShrinkToFit() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = libro.createSheet();
            hoja.setColumnWidth(0, 10 * 256);
            XSSFCellStyle estilo = libro.createCellStyle();
            XSSFFont fuente = libro.createFont();
            fuente.setFontHeightInPoints((short) 22);
            estilo.setFont(fuente);
            Cell celda = hoja.createRow(0).createCell(0);
            celda.setCellStyle(estilo);
            celda.setCellValue("UN TEXTO LARGUISIMO QUE NO CABE NI DE LEJOS");

            new AjusteFuente(libro).ajustar(celda);

            assertTrue(((XSSFCellStyle) celda.getCellStyle()).getShrinkToFit());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=AjusteFuenteTest`
Expected: FAIL en `enUnaCeldaCombinadaNoSeMarcaShrinkToFitPeroSiSeEncogeLaFuente` — hoy se marca `shrinkToFit` igual.

- [ ] **Step 3: Implementar**

En `AjusteFuente.java`:

1. Añadir los imports `java.util.HashSet`, `java.util.Set`, `org.apache.poi.ss.usermodel.Sheet`, `org.apache.poi.ss.util.CellRangeAddress`.
2. Añadir el campo de caché junto a `estilos`:

```java
    /**
     * Celdas cubiertas por una región combinada, por hoja, como "fila:columna".
     * Se calcula la primera vez que se ajusta una celda de esa hoja: los dos
     * builders que usan esta clase replican todos sus bloques —y con ellos
     * todos sus merges— antes de escribir ningún valor, así que para entonces
     * la hoja ya tiene todas sus combinaciones.
     */
    private final Map<Sheet, Set<String>> combinadasPorHoja = new HashMap<>();
```

3. Cambiar `ajustar` para pasar el dato al estilo:

```java
        short nuevo = tamano(texto, anchoEnChars, tamanoOriginal);
        celda.setCellStyle(estiloCon(original, nuevo, !estaCombinada(celda)));
```

4. Añadir:

```java
    /**
     * Si la celda cae dentro de una región combinada. Excel ignora
     * shrinkToFit en esas celdas, igual que lo ignora con wrapText.
     */
    private boolean estaCombinada(Cell celda) {
        Set<String> combinadas = combinadasPorHoja.computeIfAbsent(
                celda.getSheet(), AjusteFuente::mapearCombinadas);
        return combinadas.contains(celda.getRowIndex() + ":" + celda.getColumnIndex());
    }

    private static Set<String> mapearCombinadas(Sheet hoja) {
        Set<String> combinadas = new HashSet<>();
        for (CellRangeAddress region : hoja.getMergedRegions()) {
            for (int fila = region.getFirstRow(); fila <= region.getLastRow(); fila++) {
                for (int col = region.getFirstColumn(); col <= region.getLastColumn(); col++) {
                    combinadas.add(fila + ":" + col);
                }
            }
        }
        return combinadas;
    }
```

5. Cambiar `estiloCon` para recibir y respetar el flag, **incluyéndolo en la clave del caché** (el mismo estilo original puede tocar a una celda combinada y a una suelta):

```java
    private XSSFCellStyle estiloCon(XSSFCellStyle original, short tamano, boolean puedeEncoger) {
        String clave = original.getIndex() + ":" + tamano + ":" + puedeEncoger;
        return estilos.computeIfAbsent(clave, k -> {
            ... // igual que ahora hasta el final
            if (puedeEncoger && !original.getWrapText()) {
                estilo.setShrinkToFit(true);
            }
            return estilo;
        });
    }
```

6. Actualizar el javadoc de la clase: donde dice que en AMI `shrinkToFit` sí es una red de seguridad real, añadir que **desde la plantilla nueva la celda de `REFERENCE` está combinada y por tanto tampoco lo es**; lo único que protege ese texto es el tamaño calculado.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=AjusteFuenteTest`
Expected: PASS, incluidos los tests que ya había.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AjusteFuente.java src/test/java/com/puntotres/packinglist/service/etiquetas/AjusteFuenteTest.java
git commit -m "no marcar shrinkToFit en celdas combinadas, que Excel lo ignora"
```

---

## Task 5: Plantilla nueva y coordenadas

**Files:**
- Modify (reemplazar el binario): `src/main/resources/client-labels/ami-etiquetas-template.xlsx`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayout.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayoutTest.java`

**Interfaces:**
- Produces: `AmiEtiquetaLayout` con el componente `po` **eliminado**, el componente `ean13` **renombrado a `imagenArticulo`** y las coordenadas y filas de la tabla "Datos medidos". El orden de los componentes del record pasa a ser:
  `(nombreHoja, sufijoPo, alturaBloque, offsetSegundaEtiqueta, filaOrderNumber, filaTemporada, filaReferencia, filaColor, filaTalla, filaCantidad, filaPeso, filaParcel, imagenArticulo, ean128)`.

**Nota:** después de esta tarea `AmiEtiquetasExcelBuilder` no compila (usa `layout.po()` y `layout.ean13()`). Se arregla en la Task 7; hasta entonces el proyecto no compila. Esta tarea y la 7 se pueden hacer seguidas, pero se separan porque la 5 es "medir bien" y la 7 es "dibujar bien", y un revisor puede rechazar una y aprobar la otra. **Si el flujo exige que cada commit compile, hacer la Task 5 y la Task 7 en el mismo commit.**

- [ ] **Step 1: Copiar la plantilla del cliente a resources**

```bash
cp "docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx" src/main/resources/client-labels/ami-etiquetas-template.xlsx
```

- [ ] **Step 2: Actualizar el test de coordenadas**

Reemplazar el contenido de `AmiEtiquetaLayoutTest.java` por:

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Ancla las coordenadas medidas en client-labels/ami-etiquetas-template.xlsx.
 * Si un test de aquí falla es que alguien ha tocado la plantilla o el layout
 * sin el otro: comparar contra docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx.
 */
class AmiEtiquetaLayoutTest {

    @Test
    void chinaTieneLaImagenYElEan128EnSuSitio() {
        assertEquals(new AnclajeBloque(10, 2481943, 54429, 1674091, 762000),
                AmiEtiquetaLayout.CHINA.imagenArticulo());
        assertEquals(new AnclajeBloque(8, 1352897, 143333, 2727960, 452413),
                AmiEtiquetaLayout.CHINA.ean128());
    }

    @Test
    void japanTieneLaImagenElEan128YLaDireccion() {
        assertEquals(new AnclajeBloque(9, 2241177, 26896, 1674091, 762000),
                AmiEtiquetaLayout.JAPAN.imagenArticulo());
        assertEquals(new AnclajeBloque(7, 918884, 62682, 3009900, 502991),
                AmiEtiquetaLayout.JAPAN.ean128());
        assertEquals(new AnclajeBloque(2, 66675, 95250, 2562225, 1143000),
                AmiEtiquetaLayout.JAPAN_DIRECCION);
    }

    @Test
    void franceTieneLaImagenYElEan128EnSuSitio() {
        assertEquals(new AnclajeBloque(9, 2937163, 69273, 1674091, 762000),
                AmiEtiquetaLayout.FRANCE.imagenArticulo());
        assertEquals(new AnclajeBloque(7, 2057400, 156331, 2575560, 430408),
                AmiEtiquetaLayout.FRANCE.ean128());
    }

    @Test
    void elHuecoDeLaImagenEsElMismoEnLasTresDestinaciones() {
        // La imagen compuesta se genera con la proporción del hueco: si una
        // destinación tuviera otra, saldría deformada en esa.
        for (AmiEtiquetaLayout layout : todos()) {
            assertEquals(1674091, layout.imagenArticulo().cx(), layout.nombreHoja());
            assertEquals(762000, layout.imagenArticulo().cy(), layout.nombreHoja());
        }
    }

    @Test
    void lasFilasDeValorSonLasDeLaPlantillaNueva() {
        assertEquals(8, AmiEtiquetaLayout.CHINA.filaOrderNumber());
        assertEquals(10, AmiEtiquetaLayout.CHINA.filaReferencia());
        assertEquals(7, AmiEtiquetaLayout.JAPAN.filaOrderNumber());
        assertEquals(9, AmiEtiquetaLayout.JAPAN.filaReferencia());
        assertEquals(7, AmiEtiquetaLayout.FRANCE.filaOrderNumber());
        assertEquals(9, AmiEtiquetaLayout.FRANCE.filaReferencia());
    }

    @Test
    void todasLasImagenesCabenDentroDeSuBloque() {
        // Las imágenes se replican a +offsetSegundaEtiqueta, así que ninguna
        // puede arrancar más allá de esa mitad o pisaría la etiqueta de abajo.
        for (AmiEtiquetaLayout layout : todos()) {
            for (AnclajeBloque anclaje : new AnclajeBloque[] {
                    layout.imagenArticulo(), layout.ean128() }) {
                assertTrue(anclaje.fila() < layout.offsetSegundaEtiqueta(),
                        layout.nombreHoja() + ": el anclaje de la fila " + anclaje.fila()
                                + " se sale de la primera etiqueta del par");
            }
        }
    }

    private static AmiEtiquetaLayout[] todos() {
        return new AmiEtiquetaLayout[] {
                AmiEtiquetaLayout.CHINA, AmiEtiquetaLayout.JAPAN, AmiEtiquetaLayout.FRANCE };
    }
}
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `mvn test -Dtest=AmiEtiquetaLayoutTest`
Expected: FAIL de compilación — `imagenArticulo()` no existe.

- [ ] **Step 4: Implementar el layout nuevo**

Reemplazar el cuerpo de `AmiEtiquetaLayout.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

/**
 * Coordenadas (0-based de POI) de la hoja de una destinación en la
 * plantilla client-labels/ami-etiquetas-template.xlsx. Cada hoja trae UN
 * par de etiquetas modelo (2 etiquetas idénticas apiladas en vertical =
 * una hoja A4); la caja i-ésima se escribe desplazada i*alturaBloque y la
 * segunda etiqueta del par a +offsetSegundaEtiqueta. Los valores van en la
 * columna C (índice 2) salvo la temporada, en B (índice 1).
 *
 * La plantilla del cliente lleva DOS imágenes flotantes por etiqueta, ancladas
 * en la columna C con los offsets EMU medidos sobre ella:
 * <ul>
 * <li><b>imagenArticulo</b>: la etiqueta de artículo entera (cuatro textos más
 * el EAN-13) compuesta en un solo PNG por ImagenEtiquetaArticulo. Su hueco
 * mide lo mismo en las tres destinaciones, 1674091 × 762000 EMU;</li>
 * <li><b>ean128</b>: el Code 128 de la columna EAN128 del excel de pedido, en
 * el sitio donde antes iba el Code 128 del PO.</li>
 * </ul>
 * El Code 128 del Product Order ya no existe: el cliente lo quitó de su
 * plantilla.
 *
 * filaOrderNumber y filaReferencia apuntan a la fila SUPERIOR de sus celdas
 * combinadas (C8:C9 y C10:C11 en JAPAN y FRANCE, una más abajo en CHINA), que
 * es donde vive el valor de una celda combinada.
 *
 * sufijoPo: sufijo de la columna PO del excel de pedido para esta
 * destinación ("CH", "JP"); null = PO numérico sin sufijo (France).
 *
 * NO cambiar estas coordenadas sin revisar la plantilla, y viceversa:
 * AmiEtiquetaLayoutTest las ancla.
 */
public record AmiEtiquetaLayout(
        String nombreHoja, String sufijoPo, int alturaBloque, int offsetSegundaEtiqueta,
        int filaOrderNumber, int filaTemporada, int filaReferencia, int filaColor,
        int filaTalla, int filaCantidad, int filaPeso, int filaParcel,
        AnclajeBloque imagenArticulo, AnclajeBloque ean128) {

    public static final int COL_TEMPORADA = 1;
    public static final int COL_VALOR = 2;
    public static final int COL_BARCODE = 2;

    public static final AmiEtiquetaLayout CHINA = new AmiEtiquetaLayout(
            "AMI CHINA", "CH", 34, 17,
            8, 11, 10, 12, 13, 14, 15, 16,
            new AnclajeBloque(10, 2481943, 54429, 1674091, 762000),
            new AnclajeBloque(8, 1352897, 143333, 2727960, 452413));

    public static final AmiEtiquetaLayout JAPAN = new AmiEtiquetaLayout(
            "AMI JAPAN", "JP", 32, 16,
            7, 10, 9, 11, 12, 13, 14, 15,
            new AnclajeBloque(9, 2241177, 26896, 1674091, 762000),
            new AnclajeBloque(7, 918884, 62682, 3009900, 502991));

    public static final AmiEtiquetaLayout FRANCE = new AmiEtiquetaLayout(
            "AMI FRANCE", null, 32, 16,
            7, 10, 9, 11, 12, 13, 14, 15,
            new AnclajeBloque(9, 2937163, 69273, 1674091, 762000),
            new AnclajeBloque(7, 2057400, 156331, 2575560, 430408));

    /** Anclaje de la imagen-dirección de JAPAN. */
    public static final AnclajeBloque JAPAN_DIRECCION =
            new AnclajeBloque(2, 66675, 95250, 2562225, 1143000);
}
```

**Ojo con el orden de los argumentos**: en el constructor las filas van
`filaOrderNumber, filaTemporada, filaReferencia, filaColor, ...`. En CHINA eso
es `8, 11, 10, 12, ...` — temporada (11) va **antes** que referencia (10)
aunque su número sea mayor.

- [ ] **Step 5: Ejecutar el test de coordenadas**

Run: `mvn test -Dtest=AmiEtiquetaLayoutTest`
Expected: los tests de `AmiEtiquetaLayoutTest` compilan y pasan. La suite completa **no** compila todavía (`AmiEtiquetasExcelBuilder` usa `po()`): es lo esperado y lo arregla la Task 7.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/client-labels/ami-etiquetas-template.xlsx src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayout.java src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayoutTest.java
git commit -m "plantilla nueva de etiqueta de caja de AMI y sus coordenadas"
```

---

## Task 6: `colorCode` numérico y `colorCompleto`

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcelTest.java`, `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoRealTest.java`

**Interfaces:**
- Produces: `AmiPedidoExcel.FilaPedido` pasa a tener 6 componentes:
  `(String orderNumber, String colorCode, String colorCompleto, String ean13, String ean128, List<String> avisosEan)`.
  - `colorCode` = **solo el COLORIS** (`"221"`), que es lo que va a la celda `COLOR CODE` de la etiqueta.
  - `colorCompleto` = `COLORIS + " " + LIBELLÉ` (`"221 DARK COFFEE"`), lo de antes, que va a la imagen compuesta y a la hoja extra. Si el libellé está en blanco, `colorCompleto` es igual a `colorCode`.

- [ ] **Step 1: Escribir el test que falla**

Añadir a `AmiPedidoExcelTest.java`. El fixture se construye con el helper de test `com.puntotres.packinglist.testutil.PedidoAmiExcel`, cuyo record `Fila` es
`(madeIn, article, coloris, libelle, taille, po, ean13, ean128)` — es decir, el COLORIS y el libellé van **separados**, y `colorCompleto` es la unión de los dos:

```java
    @Test
    void elColorCodeEsSoloElCodigoYElCompletoLlevaTambienElNombre() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665,
                        "3666598354771", "3666598354771000010000766500000000000000 00ES"));
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedido)
                .buscar("ULL163.AL0052", "221", "U", null).orElseThrow();
        assertEquals("221", fila.colorCode());
        assertEquals("221 BLACK", fila.colorCompleto());
    }

    @Test
    void sinLibelleElColorCompletoEsElCodigoASecas() throws Exception {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "", "U", 7665,
                        "3666598354771", ""));
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedido)
                .buscar("ULL163.AL0052", "221", "U", null).orElseThrow();
        assertEquals("221", fila.colorCode());
        assertEquals("221", fila.colorCompleto());
    }
```

El EAN128 del primer test lleva un espacio de más a propósito para no romper la línea del plan: al escribirlo, componerlo con el helper `ean128(...)` que ya usa `AmiEtiquetasGeneradorTest` o con la fórmula EAN13 + `"00001"` + PO a 8 dígitos + 16 ceros + `"ES"`. Su valor no afecta a estos dos tests.

Y en `AmiPedidoRealTest.java`, añadir la misma comprobación contra el fichero real del cliente (`src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx`), usando la referencia y el color que ya use esa clase: `colorCode()` tiene que ser el COLORIS a secas y `colorCompleto()` el COLORIS más el libellé de esa fila.

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=AmiPedidoExcelTest`
Expected: FAIL de compilación — `colorCompleto()` no existe.

- [ ] **Step 3: Implementar**

En `AmiPedidoExcel.java`:

1. Cambiar el record y su javadoc:

```java
    /**
     * Una coincidencia del pedido. colorCode es SOLO el COLORIS ("221"), que
     * es lo que la plantilla nueva quiere en la celda COLOR CODE de la
     * etiqueta; colorCompleto añade el libellé ("221 DARK COFFEE") y es lo que
     * va a la imagen compuesta y a la hoja de códigos extra, igual que en la
     * etiqueta de artículo. ean13/ean128 son null cuando no se pueden dar (sin
     * fila exacta, sin columna, EAN13 inválido); avisosEan explica por qué, sin
     * contexto de caja ni destinación: lo añade quien llama.
     */
    public record FilaPedido(String orderNumber, String colorCode, String colorCompleto,
                             String ean13, String ean128, List<String> avisosEan) {

        public FilaPedido {
            avisosEan = List.copyOf(avisosEan);
        }
    }
```

2. En `buscar(...)`, sustituir el cálculo del color:

```java
        String colorCode = elegida.coloris();
        String colorCompleto = elegida.libelle().isBlank()
                ? colorCode
                : colorCode + " " + elegida.libelle();
```

3. Y la construcción del resultado:

```java
        return Optional.of(new FilaPedido(elegida.poNumerico(), colorCode, colorCompleto,
                ean13, ean128, avisosEan));
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=AmiPedidoExcelTest+AmiPedidoRealTest`
Expected: PASS. Otras clases pueden seguir sin compilar por las Tasks 5 y 7.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcelTest.java src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoRealTest.java
git commit -m "el pedido de AMI da el color code y el color completo por separado"
```

---

## Task 7: La etiqueta de caja dibuja la imagen compuesta

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilderTest.java`

**Interfaces:**
- Consumes: `AmiEtiquetaLayout.imagenArticulo()` / `.ean128()` (Task 5), `ImagenEtiquetaArticulo.png(EtiquetaArticulo, double)` (Task 3), `EtiquetaArticulo` (Task 2).
- Produces: `AmiEtiquetasExcelBuilder.EtiquetaCaja` con un componente más al final:
  `(String temporada, String referencia, String colorCode, String talla, String cantidad, String pesoBruto, String parcel, String orderNumber, String ean128, EtiquetaArticulo articulo)`.
  **El componente `ean13` desaparece**: el EAN-13 viaja dentro de `articulo`.

**Tres cambios de comportamiento:**

1. **Se acabó el Code 128 del PO.** La celda `ORDER NUMBER` sigue escribiéndose; lo que desaparece es su imagen.
2. **La imagen compuesta** se ancla en `layout.imagenArticulo()` y se genera con `layout.imagenArticulo().proporcion()`.
3. **La dirección de JAPAN se extrae por anclaje, no por "el primer PNG del libro".** La plantilla nueva tiene DOS PNG (el mock de la imagen compuesta y la dirección), así que `getAllPictures()` ya no es determinista. Se busca en la hoja de JAPAN la imagen anclada en la fila `JAPAN_DIRECCION.fila()`.

- [ ] **Step 1: Escribir los tests que fallan**

Añadir a `AmiEtiquetasExcelBuilderTest.java`. Adaptar la construcción de `EtiquetaCaja` al helper que ya tenga la clase:

```java
    @Test
    void yaNoHayCodigoDeBarrasDelPo() throws Exception {
        // Antes había 3 imágenes por etiqueta (PO, EAN13, EAN128) y ahora 2
        // (imagen compuesta y EAN128), duplicadas por el par de etiquetas.
        XSSFWorkbook libro = generarUnaEtiquetaFrance();
        assertEquals(4, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
    }

    @Test
    void laImagenCompuestaSeAnclaEnElHuecoDeLaPlantilla() throws Exception {
        XSSFWorkbook libro = generarUnaEtiquetaFrance();
        AnclajeBloque hueco = AmiEtiquetaLayout.FRANCE.imagenArticulo();
        boolean encontrada = libro.getSheetAt(0).getDrawingPatriarch().getShapes().stream()
                .filter(XSSFPicture.class::isInstance)
                .map(forma -> ((XSSFPicture) forma).getPreferredSize().getFrom())
                .anyMatch(desde -> desde.getRow() == hueco.fila()
                        && desde.getDx() == hueco.dx());
        assertTrue(encontrada, "no hay ninguna imagen anclada en el hueco de la plantilla");
    }

    @Test
    void japanConservaSuImagenDeDireccion() throws Exception {
        // La plantilla nueva tiene dos PNG: el mock de la imagen compuesta y
        // la dirección. Si se coge "el primer PNG del libro" sale el mock.
        XSSFWorkbook libro = generarUnaEtiquetaJapan();
        AnclajeBloque direccion = AmiEtiquetaLayout.JAPAN_DIRECCION;
        boolean encontrada = libro.getSheetAt(0).getDrawingPatriarch().getShapes().stream()
                .filter(XSSFPicture.class::isInstance)
                .map(XSSFPicture.class::cast)
                .anyMatch(imagen -> imagen.getPreferredSize().getFrom().getRow()
                                == direccion.fila()
                        && imagen.getPictureData().getData().length > 10_000);
        assertTrue(encontrada, "la dirección de JAPAN no está o es la imagen equivocada");
    }

    @Test
    void elOrderNumberYLaReferenciaCaenEnLasFilasNuevas() throws Exception {
        XSSFWorkbook libro = generarUnaEtiquetaFrance();
        XSSFSheet hoja = libro.getSheetAt(0);
        assertEquals("07703",
                hoja.getRow(AmiEtiquetaLayout.FRANCE.filaOrderNumber()).getCell(2)
                        .getStringCellValue());
        assertEquals("ULL163.AL0052",
                hoja.getRow(AmiEtiquetaLayout.FRANCE.filaReferencia()).getCell(2)
                        .getStringCellValue());
    }
```

Escribir los helpers `generarUnaEtiquetaFrance()` y `generarUnaEtiquetaJapan()` en la clase de test, siguiendo el patrón que ya use para generar y reabrir un `.xlsx`:

```java
    private static final EtiquetaArticulo ARTICULO = new EtiquetaArticulo(
            "ULL163.AL0052", "Size: U", "221 DARK COFFEE", "Cde: 07703", "3666598354771");

    private static XSSFWorkbook generar(AmiEtiquetaLayout layout) throws IOException {
        AmiEtiquetasExcelBuilder builder = new AmiEtiquetasExcelBuilder();
        byte[] contenido = builder.generar(layout, List.of(new AmiEtiquetasExcelBuilder
                .EtiquetaCaja("H26", "ULL163.AL0052", "221", "U", "5", "5,28 KGS",
                        "1 / 1", "07703", "366659835477100001000077030000000000000000ES",
                        ARTICULO)));
        return new XSSFWorkbook(new ByteArrayInputStream(contenido));
    }

    private static XSSFWorkbook generarUnaEtiquetaFrance() throws IOException {
        return generar(AmiEtiquetaLayout.FRANCE);
    }

    private static XSSFWorkbook generarUnaEtiquetaJapan() throws IOException {
        return generar(AmiEtiquetaLayout.JAPAN);
    }
```

Y **actualizar todos los tests que ya había** en la clase para el nuevo orden de `EtiquetaCaja`.

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=AmiEtiquetasExcelBuilderTest`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar**

En `AmiEtiquetasExcelBuilder.java`:

1. Cambiar el record y su javadoc:

```java
    /**
     * Los datos ya formateados de la etiqueta de una caja física. null =
     * celda en blanco, y sin ese código de barras: sin ean128 no hay su
     * Code 128. articulo son los cuatro textos y el EAN-13 del PRIMER
     * artículo de la caja, los que van dentro de la imagen compuesta; con
     * varios artículos, referencia/colorCode/cantidad vienen concatenados
     * pero la imagen habla solo del primero, que es de quien es su EAN-13.
     */
    public record EtiquetaCaja(String temporada, String referencia, String colorCode,
                               String talla, String cantidad, String pesoBruto,
                               String parcel, String orderNumber, String ean128,
                               EtiquetaArticulo articulo) {
    }
```

2. Cambiar el arranque de `generar(...)` para extraer la dirección de la hoja de JAPAN **antes** de quedarse con una sola hoja:

```java
            byte[] direccionJapan = extraerPngDireccion(libro);
            dejarSoloLaHoja(libro, layout.nombreHoja());
```

y reescribir el método:

```java
    /**
     * La dirección de entrega de JAPAN va como imagen. No vale coger "el
     * primer PNG del libro": la plantilla nueva trae también el mock de la
     * imagen compuesta, y el orden de getAllPictures() no lo distingue. Se
     * busca en la hoja de JAPAN la imagen anclada en la fila de
     * JAPAN_DIRECCION. null si no está: JAPAN sale sin dirección y el resto
     * de la etiqueta se genera igual.
     */
    private static byte[] extraerPngDireccion(XSSFWorkbook libro) {
        XSSFSheet japan = libro.getSheet(AmiEtiquetaLayout.JAPAN.nombreHoja());
        if (japan == null || japan.getDrawingPatriarch() == null) {
            return null;
        }
        for (XSSFShape forma : japan.getDrawingPatriarch().getShapes()) {
            if (forma instanceof XSSFPicture imagen
                    && imagen.getPreferredSize().getFrom().getRow()
                            == AmiEtiquetaLayout.JAPAN_DIRECCION.fila()) {
                return imagen.getPictureData().getData();
            }
        }
        return null;
    }
```

Imports nuevos: `org.apache.poi.xssf.usermodel.XSSFPicture`, `org.apache.poi.xssf.usermodel.XSSFShape`. Se puede quitar `XSSFPictureData` si deja de usarse.

3. Reescribir `insertarImagenes`: fuera el bloque del PO, y el del EAN-13 pasa a ser el de la imagen compuesta:

```java
        // La imagen compuesta lleva los cuatro textos del artículo y su
        // EAN-13; se genera con la proporción del hueco para que no se
        // deforme. Se cachea por su contenido entero, no solo por el EAN-13:
        // un artículo sin fila en el pedido no tiene EAN-13 y aun así su
        // imagen es distinta de la de otro artículo sin fila.
        if (etiqueta.articulo() != null) {
            imagenes.add(new ImagenAnclada(layout.imagenArticulo(), indice(libro, cache,
                    "ARTICULO:" + etiqueta.articulo(),
                    () -> ImagenEtiquetaArticulo.png(etiqueta.articulo(),
                            layout.imagenArticulo().proporcion()))));
        }
        if (tiene(etiqueta.ean128())) {
            imagenes.add(new ImagenAnclada(layout.ean128(), indice(libro, cache,
                    "EAN128:" + etiqueta.ean128(),
                    () -> CodigoBarrasCode128.png(etiqueta.ean128(),
                            layout.ean128().proporcion()))));
        }
```

4. Actualizar el javadoc de la clase: donde dice "los tres códigos de barras generados (PO, EAN13 y EAN128)", poner "la imagen compuesta del artículo y el Code 128 del EAN128".

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=AmiEtiquetasExcelBuilderTest`
Expected: PASS. `AmiEtiquetasGenerador` sigue sin compilar (Task 8).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilderTest.java
git commit -m "la etiqueta de caja lleva la imagen compuesta y pierde el codigo del PO"
```

---

## Task 8: El generador arma la etiqueta de artículo de cada caja

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/FilaCodigoBarrasExtra.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java`

**Interfaces:**
- Consumes: `FilaPedido.colorCode()/colorCompleto()` (Task 6), `EtiquetaCaja` con `articulo` (Task 7), `EtiquetaArticulo` (Task 2).
- Produces: `FilaCodigoBarrasExtra` con forma nueva:
  `(String parcel, String destino, EtiquetaArticulo articulo, String ean128)`.
  El EAN-13 va dentro de `articulo`; `parcel` es el mismo string que la etiqueta de esa caja (`"1 / 15"`).

**Reglas:**
- `EtiquetaArticulo` de un artículo resuelto:
  - `referencia` = `articulo.referencia()`
  - `talla` = `"Size: " + (articulo.talla() == null ? "U" : articulo.talla())`
  - `color` = el `colorCompleto` de su fila del pedido, o el color del JSON si no hay fila
  - `pedido` = `"Cde: " + orderNumber del EXCEL DE PEDIDO`, o `null` si no hay fila
  - `ean13` = el de su fila del pedido
- La celda `COLOR CODE` de la etiqueta usa `colorCode` (numérico), **no** `colorCompleto`.
- La imagen compuesta es la del **primer** artículo.
- Cinturones y bolsos no cambian en `SIZE`/`QUANTITY`/`REFERENCE`.

- [ ] **Step 1: Escribir los tests que fallan**

`AmiEtiquetasGeneradorTest` **no accede a `EtiquetaCaja` ni a `FilaCodigoBarrasExtra`**: genera el `.xlsx` y lo reabre con POI, con los helpers `abrir(ExcelGenerado)` y `texto(hoja, fila, columna)` que ya tiene. Los tests nuevos siguen ese patrón.

Un detalle que decide qué se puede afirmar: los cuatro textos del **primer** artículo viven dentro de un PNG y no se pueden leer desde el `.xlsx`. Los de los artículos **2..N** sí, porque la hoja extra los escribe como celdas. Como los dos salen del mismo `ArticuloResuelto.etiquetaArticulo()`, comprobarlos en la hoja extra cubre la construcción; de la etiqueta se afirma lo que sí es observable (la celda `COLOR CODE` y que hay imagen en el hueco).

El fixture `pedido()` que ya tiene la clase da, para `ULL163.AL0052` color `221`, libellé `BLACK` y PO `07703` en CHINA / `07665` en France; es decir `colorCompleto` = `"221 BLACK"`.

```java
    @Test
    void laCeldaColorCodeLlevaSoloElCodigoNumerico() throws IOException {
        // Antes ponía "001 BLACK": ahora el nombre del color vive en la
        // imagen compuesta y en la hoja extra, no en la celda.
        ResultadoEtiquetas resultado = generador.generar(
                List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", "U", 5, 5.28, "7665")))),
                cabecera(), Map.of("pedido", pedido()));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals("221", texto(libro.getSheetAt(0),
                    AmiEtiquetaLayout.FRANCE.filaColor(), 2));
        }
    }

    @Test
    void cadaEtiquetaLlevaDosImagenes() throws IOException {
        // La compuesta y el EAN128, duplicadas por el par de etiquetas. El
        // Code 128 del PO ya no existe: antes eran 6.
        ResultadoEtiquetas resultado = generador.generar(
                List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", "U", 5, 5.28, "7665")))),
                cabecera(), Map.of("pedido", pedido()));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals(4, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void laHojaExtraLlevaLosCuatroTextosDelArticuloSobrante() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(
                List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", "U", 5, 5.28, "7665"),
                        caja(1, "ULL753.AL0168", "001", "U", 3, null, "7665")))),
                cabecera(), Map.of("pedido", pedido()));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertNotNull(extra, "no se ha generado la hoja de códigos extra");
            int base = RejillaEtiquetas.filaBase(0);
            assertEquals("1 / 1", texto(extra, base, 1));
            assertEquals("PARIS", texto(extra, base + 2, 1));
            assertEquals("ULL753.AL0168", texto(extra, base, 3));
            assertEquals("Size: U", texto(extra, base, 4));
            assertEquals("001 IVORY", texto(extra, base + 1, 3));
            assertEquals("Cde: 07665", texto(extra, base + 1, 4));
        }
    }

    @Test
    void enCinturonesLaHojaExtraLlevaLaTallaDeCadaSobrante() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(
                List.of(importado(destino("PARIS",
                        caja(1, "UBL029.AL0216", "001", "85", 4, 9.93, "7672"),
                        caja(1, "UBL029.AL0216", "001", "95", 33, null, "7672"),
                        caja(1, "UBL029.AL0216", "001", "105", 4, null, "7672")))),
                cabecera(), Map.of("pedido", pedido()));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals("Size: 95", texto(extra, RejillaEtiquetas.filaBase(0), 4));
            assertEquals("Size: 105", texto(extra, RejillaEtiquetas.filaBase(1), 4));
        }
    }

    @Test
    void unArticuloQueNoEstaEnElPedidoSaleSinCdeYConSuColorDelJson()
            throws IOException {
        ResultadoEtiquetas resultado = generador.generar(
                List.of(importado(destino("PARIS",
                        caja(1, "ULL163.AL0052", "221", "U", 5, 5.28, "7665"),
                        caja(1, "ULL999.AL9999", "777", "U", 1, null, "7665")))),
                cabecera(), Map.of("pedido", pedido()));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet extra = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            int base = RejillaEtiquetas.filaBase(0);
            assertEquals("777", texto(extra, base + 1, 3));
            assertEquals("", texto(extra, base + 1, 4));
        }
    }
```

**Migración de los tests que ya hay en la clase.** Todos usan índices de fila literales del layout viejo. Sustituirlos por los accesores del layout (`AmiEtiquetaLayout.FRANCE.filaOrderNumber()`, etc.) en vez de por el número nuevo, para que no vuelvan a quedarse obsoletos:

| Lo que afirma el test | Índice viejo (FRANCE) | Nuevo |
|---|---|---|
| order number | 8 | 7 |
| referencia | 10 | 9 |
| color | 11 | 11 (no cambia) |
| talla | 12 | 12 (no cambia) |
| cantidad | 13 | 13 (no cambia) |
| peso | 14 | 14 (no cambia) |
| parcel | 15 | 15 (no cambia) |

Y los recuentos de imágenes: donde hoy se esperan **6** por caja (3 códigos × 2 etiquetas) ahora son **4**; donde se esperan 2 hay que recontar según qué códigos tenga esa caja. El valor `"001 BLACK"` que hoy se espera en la celda de color pasa a ser `"001"`.

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=AmiEtiquetasGeneradorTest`
Expected: FAIL de compilación.

- [ ] **Step 3: Cambiar `FilaCodigoBarrasExtra`**

```java
package com.puntotres.packinglist.service.etiquetas;

/**
 * Un artículo que no cabe en la etiqueta de su caja y va a la hoja
 * "CODIGOS BARRAS EXTRA": los cuatro textos de su etiqueta de artículo, su
 * EAN-13 (dentro de articulo) y su Code 128 largo.
 *
 * parcel es el mismo string que la etiqueta de esa caja ("1 / 15"), para que
 * el operario case la hoja con la caja; destino es el nombre de la
 * destinación del JSON.
 *
 * articulo.ean13() o ean128 null = ese código no se puede dar (artículo no
 * encontrado en el excel de pedido, o columna ausente): el bloque se escribe
 * igual, sin esa imagen. El código de barras del PO no está aquí: el cliente
 * lo quitó de su maqueta.
 */
public record FilaCodigoBarrasExtra(String parcel, String destino,
                                    EtiquetaArticulo articulo, String ean128) {
}
```

- [ ] **Step 4: Cambiar `AmiEtiquetasGenerador`**

1. `ArticuloResuelto` gana el color completo y el PO del pedido:

```java
    /** Un artículo de la caja con lo que aporta el excel de pedido. */
    private record ArticuloResuelto(ArticuloEtiqueta articulo, String colorCode,
                                    String colorCompleto, String poDelPedido,
                                    String ean13, String ean128) {

        /** Los cuatro textos y el EAN-13 tal como van a la imagen y a la hoja extra. */
        EtiquetaArticulo etiquetaArticulo() {
            return new EtiquetaArticulo(articulo.referencia(),
                    "Size: " + (articulo.talla() == null ? TALLA_UNICA : articulo.talla()),
                    colorCompleto,
                    poDelPedido == null ? null : "Cde: " + poDelPedido,
                    ean13);
        }
    }
```

2. En `resolver(...)`, los dos `return`:

```java
        // sin fila en el pedido:
        return new ArticuloResuelto(articulo, articulo.codigoColor(),
                articulo.codigoColor(), null, null, null);
        ...
        // con fila:
        return new ArticuloResuelto(articulo, fila.get().colorCode(),
                fila.get().colorCompleto(), fila.get().orderNumber(),
                fila.get().ean13(), fila.get().ean128());
```

3. Las filas extra pasan a llevar `parcel` y destino. `etiquetaDe(...)` ya recibe `posicion` y `total`, así que el parcel se calcula una vez arriba:

```java
        String parcel = posicion + " / " + total;
        ...
        for (ArticuloResuelto sobrante : resueltos.subList(1, resueltos.size())) {
            filasExtra.add(new FilaCodigoBarrasExtra(parcel, nombreDestino,
                    sobrante.etiquetaArticulo(), sobrante.ean128()));
        }
```

4. El `return` final:

```java
        return new EtiquetaCaja(envio.getTemporada(), referencia, colorCode,
                talla, cantidad, pesoTexto, parcel, orderNumber,
                primero.ean128(), primero.etiquetaArticulo());
```

5. Comprobar que `colorCode` en la rama de bolsos sigue uniendo `ArticuloResuelto::colorCode` (ahora numérico) con `ArticulosDeCaja.unirValores`, y que la rama de cinturones sigue usando `primero.colorCode()`. **No** cambiar a `colorCompleto` en ninguna de las dos: la celda quiere el código.

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=AmiEtiquetasGeneradorTest`
Expected: PASS. `HojaCodigosBarrasExtra` sigue sin compilar (Task 10) — si el flujo lo exige, dejar esta tarea y la 10 en un mismo commit.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java src/main/java/com/puntotres/packinglist/service/etiquetas/FilaCodigoBarrasExtra.java src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java
git commit -m "el generador de AMI arma la etiqueta de articulo de cada caja"
```

---

## Task 9: Extraer la rejilla y el bloque de etiqueta de artículo

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/RejillaEtiquetas.java`
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/BloqueEtiquetaArticulo.java`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetasarticulo/EtiquetasArticuloExcelBuilder.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/BloqueEtiquetaArticuloTest.java` (nuevo), `EtiquetasArticuloExcelBuilderTest`, `EtiquetasArticuloMaquetacionTest` (deben seguir pasando **sin tocarlos**)

**Interfaces:**
- Produces:

```java
public final class RejillaEtiquetas {
    public static final int BLOQUES_POR_PAGINA = 10;
    public static final int FILAS_POR_BLOQUE = 8;
    public static final int[] COLUMNAS_IZQUIERDA = {0, 3, 6, 9};
    public static final int MAX_NOMBRE_HOJA = 31;

    public static int filaBase(int bloque);
    public static XSSFSheet crearHojaMaquetada(XSSFWorkbook libro, String nombre, int bloques);
    public static String nombreUnico(Set<String> usados, String nombre);
}

public final class BloqueEtiquetaArticulo {
    /** Filas por debajo de la base donde arranca el código de barras. */
    public static final int BARCODE_OFFSET_FILA = 2;

    public BloqueEtiquetaArticulo(XSSFWorkbook libro);
    public void escribirTextos(XSSFSheet hoja, int filaBase, int columnaIzquierda,
                               EtiquetaArticulo etiqueta);
    public static XSSFClientAnchor anclajeCodigo(XSSFSheet hoja, int filaBase,
                                                 int columnaIzquierda);
    public static int cuerpoPara(String texto);
}
```

**Refactor a comportamiento constante.** `EtiquetasArticuloExcelBuilder` pasa a ser el orquestador (agrupa hojas, cachea la imagen, escribe el libro) y delega la maqueta. `EtiquetasArticuloMaquetacionTest` compara lo generado contra el fichero real del cliente: **es el juez de que este refactor no ha cambiado nada.**

- [ ] **Step 1: Crear `RejillaEtiquetas` moviendo el código tal cual**

Mover desde `EtiquetasArticuloExcelBuilder`: `ANCHOS_COLUMNA`, `COLUMNAS_IZQUIERDA`, `BLOQUES` (renombrado a `BLOQUES_POR_PAGINA`), `FILAS_POR_BLOQUE`, `PRIMERA_FILA_BLOQUE`, `ETIQUETAS_POR_HOJA`, los altos, `ESCALA`, los márgenes, `MAX_NOMBRE_HOJA`, `filaBase`, `nombreUnico` y `crearHojaMaquetada`. Mantener los javadoc.

`crearHojaMaquetada` gana el parámetro `bloques` y generaliza la regla de la separadora y de los saltos de página:

```java
    /**
     * Una hoja con la rejilla maquetada para {@code bloques} etiquetas.
     *
     * La separadora se omite en el último bloque de cada página y en el
     * último de la hoja: crearla haría la página 9,95 pt más alta y sacaría
     * una página de más al imprimir (el fichero real del cliente termina en
     * la fila 74). Con bloques = BLOQUES_POR_PAGINA sale exactamente la hoja
     * de las etiquetas de artículo de siempre.
     */
    public static XSSFSheet crearHojaMaquetada(XSSFWorkbook libro, String nombre, int bloques) {
        XSSFSheet hoja = libro.createSheet(nombre);
        for (int columna = 0; columna < ANCHOS_COLUMNA.length; columna++) {
            hoja.setColumnWidth(columna, ANCHOS_COLUMNA[columna]);
        }
        hoja.setDefaultRowHeightInPoints(ALTO_DEFECTO);
        hoja.createRow(0).setHeightInPoints(ALTO_MARGEN_SUPERIOR);
        for (int bloque = 0; bloque < bloques; bloque++) {
            int base = filaBase(bloque);
            hoja.createRow(base);
            hoja.createRow(base + 1);
            boolean ultimoDePagina = (bloque + 1) % BLOQUES_POR_PAGINA == 0;
            boolean ultimoDeLaHoja = bloque == bloques - 1;
            if (!ultimoDePagina && !ultimoDeLaHoja) {
                hoja.createRow(base + FILAS_POR_BLOQUE - 1)
                        .setHeightInPoints(ALTO_SEPARADORA);
            }
            if (ultimoDePagina && !ultimoDeLaHoja) {
                hoja.setRowBreak(base + FILAS_POR_BLOQUE - 1);
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
        hoja.setMargin(PageMargin.HEADER, MARGEN_CABECERA_PIE);
        hoja.setMargin(PageMargin.FOOTER, MARGEN_CABECERA_PIE);
        return hoja;
    }
```

- [ ] **Step 2: Crear `BloqueEtiquetaArticulo` moviendo el código tal cual**

Mover `BARCODE_OFFSET_FILA`, `BARCODE_DX`, `BARCODE_DY`, `BARCODE_CX`, `BARCODE_CY`, `CUERPO_COLOR_POR_LONGITUD`, `cuerpoPara`, `escribir` y la clase interna `Estilos`. La clase pasa a tener estado (el caché de estilos por libro), como `AjusteFuente`:

```java
package com.puntotres.packinglist.service.etiquetas;

/**
 * Escribe una etiqueta de artículo dentro de la rejilla de RejillaEtiquetas:
 * las cuatro celdas (referencia y talla arriba, color y pedido abajo) y el
 * anclaje de su código de barras.
 *
 * Los estilos se crean UNA vez por libro y se cachean: POI los acumula por
 * libro, no por hoja, y un fichero de cinturones llega a 79 hojas. Por eso
 * hay que crear UNA instancia por libro y reutilizarla.
 *
 * Lo usan las etiquetas de artículo (una hoja por fila del pedido, 40
 * etiquetas idénticas por hoja) y la hoja "CODIGOS BARRAS EXTRA" de las
 * etiquetas de caja de AMI (un artículo por bloque, dos bloques por artículo:
 * uno con el EAN-13 y otro con el EAN128).
 */
public final class BloqueEtiquetaArticulo {

    // Movidos tal cual desde EtiquetasArticuloExcelBuilder, con su javadoc:
    //   BARCODE_OFFSET_FILA (ahora public), BARCODE_DX, BARCODE_DY,
    //   BARCODE_CX, BARCODE_CY, CUERPO_COLOR_POR_LONGITUD, cuerpoPara(String),
    //   escribir(XSSFSheet, int, int, String, CellStyle) y la clase interna
    //   Estilos. El campo de instancia es:
    private final Estilos estilos;

    public BloqueEtiquetaArticulo(XSSFWorkbook libro) {
        this.estilos = new Estilos(libro);
    }

    /**
     * Escribe las cuatro celdas en las filas filaBase y filaBase+1, que
     * RejillaEtiquetas.crearHojaMaquetada ya ha creado.
     */
    public void escribirTextos(XSSFSheet hoja, int filaBase, int columnaIzquierda,
                               EtiquetaArticulo etiqueta) {
        escribir(hoja, filaBase, columnaIzquierda, etiqueta.referencia(), null);
        escribir(hoja, filaBase, columnaIzquierda + 1, etiqueta.talla(), estilos.derecha());
        escribir(hoja, filaBase + 1, columnaIzquierda, etiqueta.color(),
                estilos.color(etiqueta.color()));
        escribir(hoja, filaBase + 1, columnaIzquierda + 1, etiqueta.pedido(),
                estilos.derecha());
    }

    /** Dónde va el código de barras de ese bloque. */
    public static XSSFClientAnchor anclajeCodigo(XSSFSheet hoja, int filaBase,
                                                 int columnaIzquierda) {
        return AnclajeImagen.fijo(hoja, columnaIzquierda, BARCODE_DX,
                filaBase + BARCODE_OFFSET_FILA, BARCODE_DY, BARCODE_CX, BARCODE_CY);
    }
}
```

- [ ] **Step 3: Dejar `EtiquetasArticuloExcelBuilder` delegando**

`generar(...)` pasa a:

```java
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            BloqueEtiquetaArticulo bloques = new BloqueEtiquetaArticulo(libro);
            Set<String> nombresUsados = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (HojaEtiquetas hoja : hojas) {
                XSSFSheet destino = RejillaEtiquetas.crearHojaMaquetada(libro,
                        RejillaEtiquetas.nombreUnico(nombresUsados, hoja.nombreHoja()),
                        RejillaEtiquetas.BLOQUES_POR_PAGINA);
                rellenar(libro, destino, hoja.etiqueta(), bloques);
            }
            libro.setActiveSheet(0);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
```

y `rellenar`:

```java
    private static void rellenar(XSSFWorkbook libro, XSSFSheet hoja,
                                 EtiquetaArticulo etiqueta, BloqueEtiquetaArticulo bloques) {
        int imagen = indiceImagen(libro, etiqueta.ean13());
        XSSFDrawing dibujo = imagen >= 0 ? hoja.createDrawingPatriarch() : null;
        for (int bloque = 0; bloque < RejillaEtiquetas.BLOQUES_POR_PAGINA; bloque++) {
            int base = RejillaEtiquetas.filaBase(bloque);
            for (int izquierda : RejillaEtiquetas.COLUMNAS_IZQUIERDA) {
                bloques.escribirTextos(hoja, base, izquierda, etiqueta);
                if (dibujo != null) {
                    dibujo.createPicture(
                            BloqueEtiquetaArticulo.anclajeCodigo(hoja, base, izquierda), imagen);
                }
            }
        }
    }
```

Los tests que referencien `EtiquetasArticuloExcelBuilder.COLUMNAS_IZQUIERDA`, `.BLOQUES`, `.FILAS_POR_BLOQUE`, `.MAX_NOMBRE_HOJA`, `.filaBase`, `.cuerpoPara` o `.nombreUnico` pasan a referenciar `RejillaEtiquetas` o `BloqueEtiquetaArticulo`. Igual `AmiEtiquetasArticuloGenerador`, que usa `MAX_NOMBRE_HOJA`.

- [ ] **Step 4: Test del bloque**

Crear `src/test/java/com/puntotres/packinglist/service/etiquetas/BloqueEtiquetaArticuloTest.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class BloqueEtiquetaArticuloTest {

    private static final EtiquetaArticulo ETIQUETA = new EtiquetaArticulo(
            "ULL163.AL0052", "Size: U", "221 DARK COFFEE", "Cde: 07703", "3666598354771");

    @Test
    void escribeLosCuatroTextosEnSusCeldas() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = RejillaEtiquetas.crearHojaMaquetada(libro, "H", 1);
            new BloqueEtiquetaArticulo(libro).escribirTextos(hoja, 1, 3, ETIQUETA);
            assertEquals("ULL163.AL0052", hoja.getRow(1).getCell(3).getStringCellValue());
            assertEquals("Size: U", hoja.getRow(1).getCell(4).getStringCellValue());
            assertEquals("221 DARK COFFEE", hoja.getRow(2).getCell(3).getStringCellValue());
            assertEquals("Cde: 07703", hoja.getRow(2).getCell(4).getStringCellValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void reutilizaElEstiloEntreBloquesDelMismoLibro() {
        // POI acumula los estilos por libro y un fichero de cinturones tiene
        // 79 hojas × 40 etiquetas: sin caché se dispara el tope de ~64.000.
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = RejillaEtiquetas.crearHojaMaquetada(libro, "H", 2);
            BloqueEtiquetaArticulo bloques = new BloqueEtiquetaArticulo(libro);
            bloques.escribirTextos(hoja, 1, 0, ETIQUETA);
            bloques.escribirTextos(hoja, 9, 0, ETIQUETA);
            assertEquals(hoja.getRow(2).getCell(0).getCellStyle().getIndex(),
                    hoja.getRow(10).getCell(0).getCellStyle().getIndex());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void laRejillaDeUnaPaginaSaleIgualQueLaDeSiempre() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = RejillaEtiquetas.crearHojaMaquetada(
                    libro, "H", RejillaEtiquetas.BLOQUES_POR_PAGINA);
            // La última separadora no se crea: si se creara, la hoja sería
            // 9,95 pt más alta y saldría una segunda página al imprimir.
            assertNull(hoja.getRow(RejillaEtiquetas.filaBase(9)
                    + RejillaEtiquetas.FILAS_POR_BLOQUE - 1));
            assertEquals(0, hoja.getRowBreaks().length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void conMasDeUnaPaginaHaySaltoAlFinalDeCadaUna() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet hoja = RejillaEtiquetas.crearHojaMaquetada(libro, "H", 12);
            assertEquals(1, hoja.getRowBreaks().length);
            assertEquals(RejillaEtiquetas.filaBase(9)
                    + RejillaEtiquetas.FILAS_POR_BLOQUE - 1, hoja.getRowBreaks()[0]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 5: Ejecutar la suite entera**

Run: `mvn test`
Expected: PASS. **`EtiquetasArticuloMaquetacionTest` y `EtiquetasArticuloExcelBuilderTest` tienen que pasar sin haberlos tocado** (salvo el renombrado de constantes): son la prueba de que el refactor no ha cambiado el fichero generado. Si alguno falla, el refactor ha movido algo de más.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "extraer la rejilla y el bloque de etiqueta de articulo a la capa comun"
```

---

## Task 10: La hoja de códigos extra, como rejilla imprimible

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/HojaCodigosBarrasExtra.java` (reescritura)
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/HojaCodigosBarrasExtraTest.java` (reescritura), `HojaCodigosBarrasExtraMaquetacionTest.java` (nuevo)

**Interfaces:**
- Consumes: `RejillaEtiquetas`, `BloqueEtiquetaArticulo` (Task 9), `FilaCodigoBarrasExtra` con forma nueva (Task 8).
- Produces: `public static void HojaCodigosBarrasExtra.escribir(XSSFWorkbook libro, List<FilaCodigoBarrasExtra> filas)` — firma sin cambios. La hoja sigue llamándose `CODIGOS BARRAS EXTRA` y sigue sin crearse cuando la lista está vacía.

**Maqueta**, medida de `docs/Etiquetas cajas/AMI ETIQUETAS CAJA - CODE BARRAS EXTRA TEMPLATE.xlsx`:

- Un artículo por bloque, en 3 de las 4 columnas de la rejilla:
  - `COLUMNAS_IZQUIERDA[0]` (A/B): la caja.
  - `COLUMNAS_IZQUIERDA[1]` (D/E): los 4 textos + el EAN-13.
  - `COLUMNAS_IZQUIERDA[2]` (G/H): los mismos 4 textos + el EAN128.
  - `COLUMNAS_IZQUIERDA[3]` (J/K): vacía.
- Bloque de caja: `A(base):A(base+1)` combinada con `CAJA` y `B(base):B(base+1)` combinada con el parcel; `A(base+2):A(base+3)` con `Destinación` y `B(base+2):B(base+3)` con el nombre de la destinación. Calibri negrita, **18 pt** el rótulo `CAJA`, **20 pt** el parcel, **14 pt** los dos de la destinación, todos centrados horizontal y verticalmente.
- EAN128: anclado en la columna `COLUMNAS_IZQUIERDA[2]` con `dx 60959, dy 129540, cx 2118833, cy 352239`, generado con la proporción `2118833.0 / 352239` para que no se estire.

- [ ] **Step 1: Escribir los tests que fallan**

Reescribir `HojaCodigosBarrasExtraTest.java`:

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class HojaCodigosBarrasExtraTest {

    private static FilaCodigoBarrasExtra fila(String referencia, String ean13) {
        return new FilaCodigoBarrasExtra("3 / 15", "CHINA",
                new EtiquetaArticulo(referencia, "Size: U", "221 DARK COFFEE",
                        "Cde: 07703", ean13),
                "366659835477100001000077030000000000000000ES");
    }

    @Test
    void sinFilasNoSeCreaLaHoja() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("AMI CHINA");
            HojaCodigosBarrasExtra.escribir(libro, List.of());
            assertEquals(1, libro.getNumberOfSheets());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void cadaArticuloOcupaUnBloqueConSusTresColumnas() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaCodigosBarrasExtra.escribir(libro,
                    List.of(fila("ULL163.AL0052", "3666598354771")));
            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            int base = RejillaEtiquetas.filaBase(0);
            assertEquals("CAJA", hoja.getRow(base).getCell(0).getStringCellValue());
            assertEquals("3 / 15", hoja.getRow(base).getCell(1).getStringCellValue());
            assertEquals("Destinación",
                    hoja.getRow(base + 2).getCell(0).getStringCellValue());
            assertEquals("CHINA", hoja.getRow(base + 2).getCell(1).getStringCellValue());
            // Los mismos cuatro textos en el bloque del EAN13 y en el del EAN128.
            for (int columna : new int[] {3, 6}) {
                assertEquals("ULL163.AL0052",
                        hoja.getRow(base).getCell(columna).getStringCellValue());
                assertEquals("Size: U",
                        hoja.getRow(base).getCell(columna + 1).getStringCellValue());
                assertEquals("221 DARK COFFEE",
                        hoja.getRow(base + 1).getCell(columna).getStringCellValue());
                assertEquals("Cde: 07703",
                        hoja.getRow(base + 1).getCell(columna + 1).getStringCellValue());
            }
            assertNull(hoja.getRow(base).getCell(9), "la cuarta columna va vacía");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void cadaArticuloLlevaSusDosImagenes() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaCodigosBarrasExtra.escribir(libro, List.of(
                    fila("ULL163.AL0052", "3666598354771"),
                    fila("ULL745.AL0103", "3666598354771")));
            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals(4, hoja.getDrawingPatriarch().getShapes().size());
            // El mismo código en dos artículos se guarda una sola vez.
            assertEquals(2, libro.getAllPictures().size());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void unArticuloSinEan13SaleIgualPeroSinEsaImagen() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaCodigosBarrasExtra.escribir(libro, List.of(fila("ULL163.AL0052", null)));
            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals("ULL163.AL0052",
                    hoja.getRow(RejillaEtiquetas.filaBase(0)).getCell(3).getStringCellValue());
            assertEquals(1, hoja.getDrawingPatriarch().getShapes().size());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void onceArticulosOcupanDosPaginas() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaCodigosBarrasExtra.escribir(libro, java.util.stream.IntStream.range(0, 11)
                    .mapToObj(i -> fila("REF" + i, "3666598354771")).toList());
            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertEquals(1, hoja.getRowBreaks().length);
            assertEquals("REF10",
                    hoja.getRow(RejillaEtiquetas.filaBase(10)).getCell(3).getStringCellValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void laHojaSaleEnVerticalYAEscalaDeImpresion() {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            HojaCodigosBarrasExtra.escribir(libro,
                    List.of(fila("ULL163.AL0052", "3666598354771")));
            XSSFSheet hoja = libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
            assertTrue(!hoja.getPrintSetup().getLandscape());
            assertEquals(74, hoja.getPrintSetup().getScale());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn test -Dtest=HojaCodigosBarrasExtraTest`
Expected: FAIL — la hoja de hoy es una tabla.

- [ ] **Step 3: Reescribir la clase**

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
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * La hoja "CODIGOS BARRAS EXTRA": los artículos que no caben en la etiqueta de
 * su caja, maquetados para imprimir y recortar.
 *
 * En una etiqueta de caja solo cabe un par de códigos, así que cuando una caja
 * lleva varios artículos —o un cinturón con varias tallas, que en el pedido son
 * artículos distintos con EAN distinto— los demás no se imprimían en ningún
 * sitio. Aquí van todos, en una sola hoja del mismo libro de la destinación.
 *
 * Usa la MISMA rejilla que las etiquetas de artículo (RejillaEtiquetas): 10
 * artículos por A4. Cada artículo ocupa un bloque con tres columnas: la caja,
 * la etiqueta con su EAN-13 y la misma etiqueta con su EAN128.
 *
 * <b>No hay plantilla .xlsx</b>: la maquetación son las constantes de esta
 * clase y las de RejillaEtiquetas, medidas de
 * "docs/Etiquetas cajas/AMI ETIQUETAS CAJA - CODE BARRAS EXTRA TEMPLATE.xlsx".
 */
public final class HojaCodigosBarrasExtra {

    public static final String NOMBRE_HOJA = "CODIGOS BARRAS EXTRA";

    private static final String ROTULO_CAJA = "CAJA";
    private static final String ROTULO_DESTINO = "Destinación";

    private static final int COL_CAJA = RejillaEtiquetas.COLUMNAS_IZQUIERDA[0];
    private static final int COL_EAN13 = RejillaEtiquetas.COLUMNAS_IZQUIERDA[1];
    private static final int COL_EAN128 = RejillaEtiquetas.COLUMNAS_IZQUIERDA[2];

    private static final short CUERPO_ROTULO_CAJA = 18;
    private static final short CUERPO_PARCEL = 20;
    private static final short CUERPO_DESTINO = 14;

    /** Anclaje del EAN128 dentro de su bloque, en EMU. */
    private static final long EAN128_DX = 60959;
    private static final long EAN128_DY = 129540;
    private static final long EAN128_CX = 2118833;
    private static final long EAN128_CY = 352239;

    private HojaCodigosBarrasExtra() {
    }

    /** Añade la hoja al libro. Sin filas no se crea nada. */
    public static void escribir(XSSFWorkbook libro, List<FilaCodigoBarrasExtra> filas) {
        if (filas.isEmpty()) {
            return;
        }
        XSSFSheet hoja = RejillaEtiquetas.crearHojaMaquetada(libro, NOMBRE_HOJA, filas.size());
        BloqueEtiquetaArticulo bloques = new BloqueEtiquetaArticulo(libro);
        Estilos estilos = new Estilos(libro);
        XSSFDrawing dibujo = hoja.createDrawingPatriarch();
        // Un envío repite mucho el mismo artículo: sin esta caché el .xlsx
        // guardaría el mismo PNG una vez por bloque.
        Map<String, Integer> imagenes = new HashMap<>();
        for (int i = 0; i < filas.size(); i++) {
            escribirBloque(libro, hoja, bloques, estilos, dibujo, imagenes,
                    RejillaEtiquetas.filaBase(i), filas.get(i));
        }
    }

    // --- pasos ---

    private static void escribirBloque(XSSFWorkbook libro, XSSFSheet hoja,
                                       BloqueEtiquetaArticulo bloques, Estilos estilos,
                                       XSSFDrawing dibujo, Map<String, Integer> imagenes,
                                       int base, FilaCodigoBarrasExtra fila) {
        caja(hoja, estilos, base, fila);
        bloques.escribirTextos(hoja, base, COL_EAN13, fila.articulo());
        bloques.escribirTextos(hoja, base, COL_EAN128, fila.articulo());

        String ean13 = fila.articulo().ean13();
        if (CodigoBarrasEan13.esValido(ean13)) {
            dibujo.createPicture(
                    BloqueEtiquetaArticulo.anclajeCodigo(hoja, base, COL_EAN13),
                    indice(libro, imagenes, "EAN13:" + ean13,
                            () -> CodigoBarrasEan13.png(ean13).orElseThrow()));
        }
        if (fila.ean128() != null && !fila.ean128().isBlank()) {
            dibujo.createPicture(
                    AnclajeImagen.fijo(hoja, COL_EAN128, EAN128_DX,
                            base + BloqueEtiquetaArticulo.BARCODE_OFFSET_FILA, EAN128_DY,
                            EAN128_CX, EAN128_CY),
                    indice(libro, imagenes, "EAN128:" + fila.ean128(),
                            () -> CodigoBarrasCode128.png(fila.ean128(),
                                    (double) EAN128_CX / EAN128_CY)));
        }
    }

    /** La columna de la izquierda: de qué caja y de qué destinación es. */
    private static void caja(XSSFSheet hoja, Estilos estilos, int base,
                             FilaCodigoBarrasExtra fila) {
        parDeCeldas(hoja, estilos.negrita(CUERPO_ROTULO_CAJA), base, COL_CAJA, ROTULO_CAJA);
        parDeCeldas(hoja, estilos.negrita(CUERPO_PARCEL), base, COL_CAJA + 1, fila.parcel());
        parDeCeldas(hoja, estilos.negrita(CUERPO_DESTINO), base + 2, COL_CAJA,
                ROTULO_DESTINO);
        parDeCeldas(hoja, estilos.negrita(CUERPO_DESTINO), base + 2, COL_CAJA + 1,
                fila.destino());
    }

    /** Dos filas combinadas en vertical con un texto centrado. */
    private static void parDeCeldas(XSSFSheet hoja, XSSFCellStyle estilo, int fila,
                                    int columna, String texto) {
        for (int i = 0; i < 2; i++) {
            if (hoja.getRow(fila + i) == null) {
                hoja.createRow(fila + i);
            }
            Cell celda = hoja.getRow(fila + i).createCell(columna);
            celda.setCellStyle(estilo);
        }
        Cell celda = hoja.getRow(fila).getCell(columna);
        if (texto == null || texto.isBlank()) {
            celda.setBlank();
        } else {
            celda.setCellValue(texto);
        }
        hoja.addMergedRegion(new CellRangeAddress(fila, fila + 1, columna, columna));
    }

    /** Índice de la imagen en el libro, añadiéndola solo la primera vez. */
    private static int indice(XSSFWorkbook libro, Map<String, Integer> cache,
                              String clave, Supplier<byte[]> png) {
        return cache.computeIfAbsent(clave,
                k -> libro.addPicture(png.get(), Workbook.PICTURE_TYPE_PNG));
    }

    /** Estilos creados UNA vez por libro: POI los acumula por libro. */
    private static final class Estilos {

        private final XSSFWorkbook libro;
        private final Map<Short, XSSFCellStyle> porCuerpo = new HashMap<>();

        Estilos(XSSFWorkbook libro) {
            this.libro = libro;
        }

        XSSFCellStyle negrita(short cuerpo) {
            return porCuerpo.computeIfAbsent(cuerpo, c -> {
                XSSFFont fuente = libro.createFont();
                fuente.setBold(true);
                fuente.setFontHeightInPoints(c);
                XSSFCellStyle estilo = libro.createCellStyle();
                estilo.setFont(fuente);
                estilo.setAlignment(HorizontalAlignment.CENTER);
                estilo.setVerticalAlignment(VerticalAlignment.CENTER);
                return estilo;
            });
        }
    }
}
```

`BARCODE_OFFSET_FILA` tiene que ser `public` en `BloqueEtiquetaArticulo` para esto.

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn test -Dtest=HojaCodigosBarrasExtraTest`
Expected: PASS.

- [ ] **Step 5: Test de maquetación contra la plantilla de referencia**

Crear `src/test/java/com/puntotres/packinglist/service/etiquetas/HojaCodigosBarrasExtraMaquetacionTest.java`, al estilo de `EtiquetasArticuloMaquetacionTest`. Compara la hoja generada contra la plantilla de referencia del cliente. Los anchos de columna de la plantilla y los de la rejilla difieren en menos de 100 unidades POI (0,4 caracteres) porque el cliente maquetó a mano y nosotros reutilizamos los de las etiquetas de artículo; la tolerancia lo deja escrito en vez de esconderlo.

En la plantilla, los rótulos son `"CAJA "` y `"Destinación "` **con un espacio al final**. Nosotros escribimos `"CAJA"` y `"Destinación"` sin él: en una etiqueta impresa un espacio final no se ve, y las constantes quedan limpias. El test lo comprueba con `trim()` para que quede constancia de que la diferencia es deliberada y no una errata.

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Ancla la maquetación de la hoja "CODIGOS BARRAS EXTRA" contra la plantilla
 * de referencia del cliente. No hay .xlsx de plantilla en resources: la
 * maquetación son constantes, y esto es lo que impide que se desvíen.
 */
class HojaCodigosBarrasExtraMaquetacionTest {

    private static final Path REFERENCIA = Path.of(
            "docs/Etiquetas cajas/AMI ETIQUETAS CAJA - CODE BARRAS EXTRA TEMPLATE.xlsx");

    /** Diferencia máxima admitida en el ancho de columna, en unidades POI. */
    private static final int TOLERANCIA_ANCHO = 100;

    private static XSSFSheet referencia(XSSFWorkbook libro) {
        return libro.getSheetAt(0);
    }

    private static XSSFSheet generada(XSSFWorkbook libro) {
        HojaCodigosBarrasExtra.escribir(libro, List.of(
                new FilaCodigoBarrasExtra("1 / 15", "CHINA",
                        new EtiquetaArticulo("ULL163.AL0052", "Size: U",
                                "221 DARK COFFEE", "Cde: 07703", "3666598354771"),
                        "366659835477100001000077030000000000000000ES")));
        return libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA);
    }

    @Test
    void losAnchosDeColumnaCoincidenConLaPlantillaDeReferencia() {
        try (XSSFWorkbook plantilla = new XSSFWorkbook(Files.newInputStream(REFERENCIA));
             XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet esperada = referencia(plantilla);
            XSSFSheet obtenida = generada(libro);
            for (int columna = 0; columna < 11; columna++) {
                assertEquals(esperada.getColumnWidth(columna),
                        obtenida.getColumnWidth(columna), TOLERANCIA_ANCHO,
                        "columna " + columna);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void elPrimerBloqueArrancaEnLaMismaFilaQueLaPlantillaDeReferencia() {
        // En la plantilla el rótulo "CAJA " está en A2 (1-based) = fila 1.
        try (XSSFWorkbook plantilla = new XSSFWorkbook(Files.newInputStream(REFERENCIA))) {
            assertEquals(RejillaEtiquetas.filaBase(0),
                    referencia(plantilla).getRow(1).getRowNum());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void losRotulosSonLosDeLaPlantillaSalvoElEspacioFinal() {
        try (XSSFWorkbook plantilla = new XSSFWorkbook(Files.newInputStream(REFERENCIA));
             XSSFWorkbook libro = new XSSFWorkbook()) {
            XSSFSheet esperada = referencia(plantilla);
            XSSFSheet obtenida = generada(libro);
            int base = RejillaEtiquetas.filaBase(0);
            assertEquals(esperada.getRow(base).getCell(0).getStringCellValue().trim(),
                    obtenida.getRow(base).getCell(0).getStringCellValue());
            assertEquals(esperada.getRow(base + 2).getCell(0).getStringCellValue().trim(),
                    obtenida.getRow(base + 2).getCell(0).getStringCellValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void laEscalaYLaOrientacionSonLasDeLaPlantilla() {
        try (XSSFWorkbook plantilla = new XSSFWorkbook(Files.newInputStream(REFERENCIA));
             XSSFWorkbook libro = new XSSFWorkbook()) {
            assertEquals(referencia(plantilla).getPrintSetup().getScale(),
                    generada(libro).getPrintSetup().getScale());
            assertEquals(referencia(plantilla).getPrintSetup().getLandscape(),
                    generada(libro).getPrintSetup().getLandscape());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

Si alguna comparación falla por más de la tolerancia, **no subirla sin más**: mirar cuál de las dos maquetas es la buena. La regla del spec es que la rejilla tiene una sola fuente de verdad (`RejillaEtiquetas`) y la plantilla de referencia es una maqueta hecha a mano.

- [ ] **Step 6: Ejecutar la suite entera**

Run: `mvn test`
Expected: PASS. Todo compila otra vez.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "la hoja de codigos extra pasa a ser una rejilla imprimible"
```

---

## Task 11: Documentación y muestra para revisión visual

**Files:**
- Modify: `CLAUDE.md`
- Modify: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilderTest.java`

- [ ] **Step 1: Dejar un excel real en `target/` para mirarlo**

En `AmiEtiquetasExcelBuilderTest`, actualizar (o añadir, si no existe ya) el test de inspección manual para que genere una caja de **tres bolsos** en FRANCE, con lo que la hoja extra sale con dos bloques:

```java
    /** EAN128 bien formado: EAN13 + 00001 + PO a 8 dígitos + 16 ceros + país. */
    private static String ean128(String ean13, int po) {
        return ean13 + "00001" + String.format("%08d", po) + "0000000000000000" + "ES";
    }

    private static EtiquetaArticulo articulo(String referencia, String color, String ean13) {
        return new EtiquetaArticulo(referencia, "Size: U", color, "Cde: 07665", ean13);
    }

    @Test
    void dejaUnExcelEnTargetParaInspeccionManual() throws Exception {
        // No afirma casi nada: existe para abrirlo en Excel, imprimirlo y
        // pasarle un lector de códigos de barras. Es lo único que ningún test
        // puede comprobar.
        var etiqueta = new AmiEtiquetasExcelBuilder.EtiquetaCaja(
                "H26",
                "ULL163.AL0052 / ULL753.AL0168 / UBL029.AL0216",
                "221 / 001 / 001", "U", "5 / 3 / 4", "5,28 KGS", "1 / 1", "07665",
                ean128("3666598354771", 7665),
                articulo("ULL163.AL0052", "221 BLACK", "3666598354771"));
        var extras = List.of(
                new FilaCodigoBarrasExtra("1 / 1", "PARIS",
                        articulo("ULL753.AL0168", "001 IVORY", "3666598313495"),
                        ean128("3666598313495", 7665)),
                new FilaCodigoBarrasExtra("1 / 1", "PARIS",
                        articulo("UBL029.AL0216", "001 BLACK", "3666598890064"),
                        ean128("3666598890064", 7665)));
        byte[] contenido = new AmiEtiquetasExcelBuilder()
                .generar(AmiEtiquetaLayout.FRANCE, List.of(etiqueta), extras);
        Path muestra = Path.of("target/Etiquetas_AMI_PARIS_IMAGEN-COMPUESTA.xlsx");
        Files.write(muestra, contenido);
        // Lo único afirmable de un artefacto cuyo juez es el ojo y el lector
        // de códigos: que el libro se ha escrito y se puede volver a abrir con
        // sus dos hojas.
        try (XSSFWorkbook libro = new XSSFWorkbook(Files.newInputStream(muestra))) {
            assertEquals(2, libro.getNumberOfSheets());
            assertNotNull(libro.getSheet(HojaCodigosBarrasExtra.NOMBRE_HOJA));
        }
    }
```

La referencia va concatenada a propósito: es el caso que ejercita a la vez el
merge, el encogido de fuente y la imagen compuesta hablando solo del primer
artículo.

Run: `mvn test -Dtest=AmiEtiquetasExcelBuilderTest`
Expected: PASS y el fichero existe.

- [ ] **Step 2: Actualizar `CLAUDE.md`**

En la sección **Etiquetas de caja**, sustituir la frase de los "tres códigos de barras" por la maqueta nueva y añadir lo que no se deduce del código:

- La etiqueta de AMI lleva **dos imágenes**: la **imagen compuesta** del artículo (los cuatro textos de la etiqueta de artículo más su EAN-13, en un solo PNG generado por `ImagenEtiquetaArticulo`) y el **Code 128 del EAN128**. El Code 128 del PO ya no existe.
- **El código de barras nunca se reescala**: se genera con la altura de barras que le toca (`CodigoBarrasEan13.png(ean13, proporcion)`) y se pega 1:1. Reescalarlo con interpolación lo deja ilegible para un lector físico. Con la maqueta del cliente las barras quedan al ~42% de la altura nominal del EAN-13: **verificado solo con lector físico, ningún test lo cubre**.
- `COLOR CODE` en la etiqueta es **solo el código** (`221`); el color completo (`221 DARK COFFEE`) va dentro de la imagen y en la hoja extra. `AmiPedidoExcel.FilaPedido` da los dos.
- `REFERENCE` y `ORDER NUMBER` son **celdas combinadas**, y **Excel ignora `shrinkToFit` en celdas combinadas**: en AMI lo único que protege el texto de `REFERENCE` es el tamaño calculado de `AjusteFuente`, igual que en APC (allí por `wrapText`). Actualizar el párrafo que hoy dice que en AMI `shrinkToFit` sí actúa.
- La hoja `CODIGOS BARRAS EXTRA` usa **la misma rejilla que las etiquetas de artículo** (`RejillaEtiquetas`), 10 artículos por A4, con tres columnas por bloque: caja, EAN-13 y EAN128.
- `EtiquetaArticulo`, `RejillaEtiquetas` y `BloqueEtiquetaArticulo` viven en `service/etiquetas/` y los comparten los dos flujos: **tocar uno afecta a las etiquetas de artículo y a las de caja**.
- La dirección de JAPAN se extrae **por su anclaje**, no cogiendo el primer PNG del libro: la plantilla nueva tiene dos PNG.

- [ ] **Step 3: Ejecutar la suite entera**

Run: `mvn test`
Expected: PASS, todo verde.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "documentar la maqueta nueva de etiquetas de caja de AMI"
```

- [ ] **Step 5: Revisión visual del usuario (no la hace el implementador)**

Dejar escrito en el informe final que **falta la comprobación humana**, que ningún test sustituye:

1. Abrir `target/Etiquetas_AMI_PARIS_IMAGEN-COMPUESTA.xlsx`.
2. Hoja `AMI FRANCE`: la imagen compuesta cae en su hueco sin pisar el EAN128 ni la fila de `COLOR CODE`; `COLOR CODE` dice solo el número; `REFERENCE` concatenada se lee.
3. Hoja `CODIGOS BARRAS EXTRA`: dos bloques, cada uno con su caja, su EAN-13 y su EAN128.
4. **Imprimir las dos hojas y pasar un lector físico** por los tres códigos. Es el único punto que puede tumbar la maqueta: si el EAN-13 de la imagen compuesta no se lee, hay que crecer el hueco.

---

## Autorrevisión del plan

**Cobertura del spec:**

| Sección del spec | Tarea |
|---|---|
| Bajar `EtiquetaArticulo` a la capa común | 2 |
| `ImagenEtiquetaArticulo` | 3 |
| `BloqueEtiquetaArticulo` extraído de `EtiquetasArticuloExcelBuilder` | 9 |
| EAN-13 sin reescalar (overload de proporción) | 1, 3 |
| `AmiEtiquetaLayout` sin `po`, `ean13`→`imagenArticulo`, filas nuevas | 5 |
| Plantilla nueva en `resources` | 5 |
| `COLOR CODE` numérico + `colorCompleto` | 6 |
| `EtiquetaCaja` con `EtiquetaArticulo` | 7 |
| `Cde:` del excel de pedido | 8 |
| Talla del primer artículo en la imagen | 8 |
| `shrinkToFit` inerte en celdas combinadas | 4 |
| Hoja extra como rejilla, 10 por A4, 3 columnas | 10 |
| Parcel y destinación en el bloque de caja | 8, 10 |
| Nombre de hoja en plural | 10 |
| Avisos sin excepciones nuevas | 3, 8, 10 |
| Verificación: tests + comprobación manual con lector | 3, 9, 10, 11 |

**Riesgo conocido y aceptado:** las Tasks 5→7 y 8→10 dejan el árbol sin compilar entre medias. Está avisado en cada tarea y la alternativa (un commit gigante) es peor para la revisión. Si el flujo de ejecución exige que cada commit compile, fusionar 5 con 7 y 8 con 10.
