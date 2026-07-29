# EAN13 y EAN128 en las etiquetas de caja de AMI — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir a las etiquetas de caja de AMI (China, Japan, France) el código de barras EAN-13 de la columna `EAN13` y el Code 128 de la columna `EAN128` del excel de pedido, además del Code 128 del PO que ya llevan.

**Architecture:** Se extrae un record `AnclajeBloque` para la geometría de imágenes relativa al bloque de la etiqueta, y `AmiEtiquetaLayout` pasa a tener tres (`po`, `ean13`, `ean128`) más `JAPAN_DIRECCION`. `AmiPedidoExcel` lee dos columnas más y resuelve los EAN con clave exacta `ARTICLE`+`COLORIS`+`TAILLE`+sufijo de PO, devolviéndolos en `FilaPedido` junto con sus avisos. `AmiEtiquetasExcelBuilder` dibuja las tres imágenes en un bucle.

**Tech Stack:** Java 17, Spring Boot 3.5, Apache POI (XSSF), barcode4j (`EAN13Bean`, `Code128Bean`), JUnit 5.

**Spec:** [docs/superpowers/specs/2026-07-29-etiquetas-caja-ami-ean13-ean128-design.md](../specs/2026-07-29-etiquetas-caja-ami-ean13-ean128-design.md)

## Global Constraints

- Idioma: nombres de clases y métodos en inglés/español como ya hace el proyecto; **javadoc, comentarios y mensajes de aviso en español**.
- **Nunca fallar en silencio, nunca bloquear por datos que un humano puede resolver**: los servicios acumulan avisos en DTOs de resultado, no lanzan excepciones. Una etiqueta sin EAN se genera igual, sin la imagen.
- **Un peso por caja física, en su línea líder** (`model/CajaFisica`): nunca sumar las líneas de una caja. El EAN sale de la talla de esa misma línea líder.
- El EAN128 se pasa a Code 128 **verbatim**, nunca compuesto en código.
- Las plantillas de `src/main/resources/client-labels/` tienen filas modelo y coordenadas de las que dependen los builders: no editarlas sin revisar su builder.
- Los tests instancian los servicios con `new` (JUnit 5 puro). Los tests de builders reabren el `.xlsx` generado con POI y comprueban celdas reales.
- Comando de una clase de test: `mvn test -Dtest=NombreDelTest`. Suite completa: `mvn test`.

## Estructura de ficheros

**Crear:**
- `src/main/java/com/puntotres/packinglist/service/etiquetas/AnclajeBloque.java` — geometría de una imagen relativa al bloque de la etiqueta (fila + dx/dy/cx/cy en EMU).
- `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayoutTest.java` — ancla las coordenadas de los tres códigos y la dirección de Japan.
- `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoRealTest.java` — verificación contra el fichero real `EAN PUNTOTRES H26.xlsx`.
- `src/test/resources/ejemplos/README.md` — deja claro que los `.json` son fixtures sintéticos.

**Modificar:**
- `src/main/resources/client-labels/ami-etiquetas-template.xlsx` — sustituir por el `ETIQUETA CAJA AMI.xlsx` actualizado.
- `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayout.java` — `po`/`ean13`/`ean128`/`JAPAN_DIRECCION` como `AnclajeBloque`.
- `src/main/java/com/puntotres/packinglist/service/etiquetas/HojaEan.java` — `columnaOpcional`.
- `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java` — lee `EAN13`, `EAN128`, `Made in` y `TAILLE`; clave exacta; avisos.
- `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java` — `EtiquetaCaja` con `ean13`/`ean128`; dibujo en bucle.
- `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java` — talla del líder, traslado de códigos, avisos.
- `src/test/java/com/puntotres/packinglist/testutil/PedidoAmiExcel.java` — `ean128` en `Fila` y variante sin columnas EAN.
- Los tres tests existentes de etiquetas AMI + `HojaEanTest`.
- `CLAUDE.md` — nota sobre los fixtures y sobre los tres códigos de barras.

---

### Task 1: Sustituir la plantilla por el fichero actualizado del cliente

La plantilla actual es una copia de una versión anterior de `ETIQUETA CAJA AMI.xlsx`. El fichero nuevo trae las 4 imágenes de ejemplo de los EAN, que el builder limpia igual (`limpiarImagenesDeEjemplo` borra todos los anclajes). Verificado que la estructura de filas, alturas y celdas combinadas es idéntica, así que las coordenadas actuales siguen valiendo y **la suite debe seguir verde sin tocar código**.

**Files:**
- Modify: `src/main/resources/client-labels/ami-etiquetas-template.xlsx`

**Interfaces:**
- Consumes: nada.
- Produces: una plantilla en la que las hojas `AMI CHINA`, `AMI JAPAN` y `AMI FRANCE` mantienen el mismo layout de filas y siguen teniendo **un solo PNG** (la dirección de Japan), que es lo que busca `AmiEtiquetasExcelBuilder.extraerPngDireccion`.

- [ ] **Step 1: Copiar el fichero**

```bash
cp "docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx" \
   "src/main/resources/client-labels/ami-etiquetas-template.xlsx"
```

- [ ] **Step 2: Comprobar que sigue habiendo un solo PNG y tres hojas**

Run:

```bash
mkdir -p target/tmpl && cd target/tmpl && rm -rf * && \
unzip -o -q ../../src/main/resources/client-labels/ami-etiquetas-template.xlsx && \
ls xl/media/ && grep -o '<sheet name="[^"]*"' xl/workbook.xml
```

Expected: exactamente **un** fichero `.png` en `xl/media/` (más varios `.gif`), y las tres hojas `AMI CHINA`, `AMI JAPAN`, `AMI FRANCE`.

- [ ] **Step 3: Ejecutar los tests de etiquetas AMI sin tocar código**

Run: `mvn test -Dtest='AmiEtiquetas*Test'`
Expected: PASS. Si algo falla, la plantilla nueva **no** es equivalente y hay que parar y revisar antes de seguir: el resto del plan asume que las coordenadas actuales valen.

- [ ] **Step 4: Ejecutar la suite completa**

Run: `mvn test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/client-labels/ami-etiquetas-template.xlsx
git commit -m "plantilla de etiquetas AMI actualizada con los anclajes de EAN13 y EAN128"
```

---

### Task 2: Extraer `AnclajeBloque` (refactor sin cambio de comportamiento)

`AmiEtiquetaLayout` tiene 17 componentes, 5 solo para el barcode del PO. Se agrupan en un record y se aprovecha para meter también la dirección de Japan, hoy 5 constantes sueltas. **Ningún valor cambia**: es un refactor puro y los tests existentes deben pasar sin tocarlos.

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/AnclajeBloque.java`
- Create: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayoutTest.java`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayout.java`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java:149-173`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `record AnclajeBloque(int fila, long dx, long dy, long cx, long cy)`
  - `AmiEtiquetaLayout` con los componentes `AnclajeBloque po()`, `AnclajeBloque ean13()`, `AnclajeBloque ean128()` en el lugar de `filaBarcode/dxBarcode/dyBarcode/cxBarcode/cyBarcode`, y la constante `AmiEtiquetaLayout.JAPAN_DIRECCION` de tipo `AnclajeBloque`.
  - En esta tarea `ean13()` y `ean128()` se rellenan **ya con sus valores definitivos** (tabla de coordenadas del spec), pero el builder todavía no los dibuja.

- [ ] **Step 1: Escribir el test que ancla las coordenadas**

Crear `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayoutTest.java`:

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
    void chinaTieneLosTresCodigosEnSuSitio() {
        assertEquals(new AnclajeBloque(8, 2971800, 19050, 1047750, 666750),
                AmiEtiquetaLayout.CHINA.po());
        assertEquals(new AnclajeBloque(10, 2613660, 162388, 1478280, 652951),
                AmiEtiquetaLayout.CHINA.ean13());
        assertEquals(new AnclajeBloque(12, 1394460, 420424, 2727960, 455876),
                AmiEtiquetaLayout.CHINA.ean128());
    }

    @Test
    void japanTieneLosTresCodigosEnSuSitioYLaDireccion() {
        assertEquals(new AnclajeBloque(7, 2857500, 19050, 990600, 628650),
                AmiEtiquetaLayout.JAPAN.po());
        assertEquals(new AnclajeBloque(9, 2430780, 68580, 1478280, 652951),
                AmiEtiquetaLayout.JAPAN.ean13());
        assertEquals(new AnclajeBloque(12, 899160, 15168, 3009900, 502991),
                AmiEtiquetaLayout.JAPAN.ean128());
        assertEquals(new AnclajeBloque(2, 66675, 95250, 2562225, 1143000),
                AmiEtiquetaLayout.JAPAN_DIRECCION);
    }

    @Test
    void franceTieneLosTresCodigosEnSuSitio() {
        assertEquals(new AnclajeBloque(7, 3457575, 9525, 1209675, 762000),
                AmiEtiquetaLayout.FRANCE.po());
        assertEquals(new AnclajeBloque(9, 3116580, 68580, 1569902, 693420),
                AmiEtiquetaLayout.FRANCE.ean13());
        assertEquals(new AnclajeBloque(11, 2095500, 423031, 2575560, 430408),
                AmiEtiquetaLayout.FRANCE.ean128());
    }

    @Test
    void todosLosCodigosCabenDentroDeSuBloque() {
        // Las imágenes se replican a +offsetSegundaEtiqueta, así que ninguna
        // puede arrancar más allá de esa mitad o pisaría la etiqueta de abajo.
        for (AmiEtiquetaLayout layout : new AmiEtiquetaLayout[] {
                AmiEtiquetaLayout.CHINA, AmiEtiquetaLayout.JAPAN, AmiEtiquetaLayout.FRANCE }) {
            for (AnclajeBloque anclaje : new AnclajeBloque[] {
                    layout.po(), layout.ean13(), layout.ean128() }) {
                assertTrue(anclaje.fila() < layout.offsetSegundaEtiqueta(),
                        layout.nombreHoja() + ": el anclaje de la fila " + anclaje.fila()
                                + " se sale de la primera etiqueta del par");
            }
        }
    }
}
```

- [ ] **Step 2: Ejecutar el test para verificar que no compila**

Run: `mvn test -Dtest=AmiEtiquetaLayoutTest`
Expected: FAIL de compilación — no existen `AnclajeBloque` ni `po()`/`ean13()`/`ean128()`.

- [ ] **Step 3: Crear `AnclajeBloque`**

```java
package com.puntotres.packinglist.service.etiquetas;

/**
 * Dónde va una imagen dentro del bloque de una etiqueta: la fila relativa al
 * arranque del bloque más el desplazamiento (dx, dy) y el tamaño (cx, cy) en
 * EMU (360 000 EMU = 1 cm), medidos sobre la plantilla real del cliente.
 *
 * La columna no va aquí: las cuatro imágenes de las etiquetas de AMI viven en
 * la columna C (AmiEtiquetaLayout.COL_BARCODE).
 *
 * Lo usan los tres códigos de barras de la etiqueta (PO, EAN13, EAN128) y la
 * imagen-dirección de Japan.
 */
public record AnclajeBloque(int fila, long dx, long dy, long cx, long cy) {
}
```

- [ ] **Step 4: Reescribir `AmiEtiquetaLayout`**

Sustituir el fichero entero por:

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
 * Los tres códigos de barras son imágenes flotantes ancladas en la columna C
 * con los offsets EMU medidos en la plantilla de ejemplo: el Code 128 del PO
 * (po), el EAN-13 del artículo (ean13) y el Code 128 de la columna EAN128 del
 * excel de pedido (ean128).
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
        AnclajeBloque po, AnclajeBloque ean13, AnclajeBloque ean128) {

    public static final int COL_TEMPORADA = 1;
    public static final int COL_VALOR = 2;
    public static final int COL_BARCODE = 2;

    public static final AmiEtiquetaLayout CHINA = new AmiEtiquetaLayout(
            "AMI CHINA", "CH", 34, 17,
            9, 11, 11, 12, 13, 14, 15, 16,
            new AnclajeBloque(8, 2971800, 19050, 1047750, 666750),
            new AnclajeBloque(10, 2613660, 162388, 1478280, 652951),
            new AnclajeBloque(12, 1394460, 420424, 2727960, 455876));

    public static final AmiEtiquetaLayout JAPAN = new AmiEtiquetaLayout(
            "AMI JAPAN", "JP", 32, 16,
            8, 10, 10, 11, 12, 13, 14, 15,
            new AnclajeBloque(7, 2857500, 19050, 990600, 628650),
            new AnclajeBloque(9, 2430780, 68580, 1478280, 652951),
            new AnclajeBloque(12, 899160, 15168, 3009900, 502991));

    public static final AmiEtiquetaLayout FRANCE = new AmiEtiquetaLayout(
            "AMI FRANCE", null, 32, 16,
            8, 10, 10, 11, 12, 13, 14, 15,
            new AnclajeBloque(7, 3457575, 9525, 1209675, 762000),
            new AnclajeBloque(9, 3116580, 68580, 1569902, 693420),
            new AnclajeBloque(11, 2095500, 423031, 2575560, 430408));

    /** Anclaje de la imagen-dirección de JAPAN (único PNG de la plantilla). */
    public static final AnclajeBloque JAPAN_DIRECCION =
            new AnclajeBloque(2, 66675, 95250, 2562225, 1143000);
}
```

- [ ] **Step 5: Adaptar `insertarImagenes` del builder al nuevo layout**

En `AmiEtiquetasExcelBuilder`, sustituir el cuerpo del bucle de `insertarImagenes` (líneas 156-172) por la versión que usa `AnclajeBloque`, **sin añadir todavía los EAN**:

```java
        for (int offset : offsets) {
            if (barcode != null) {
                int indice = libro.addPicture(barcode, Workbook.PICTURE_TYPE_PNG);
                dibujo.createPicture(anclar(hoja, layout.po(), base + offset), indice);
            }
            if (direccionJapan != null && "AMI JAPAN".equals(layout.nombreHoja())) {
                int indice = libro.addPicture(direccionJapan, Workbook.PICTURE_TYPE_PNG);
                dibujo.createPicture(
                        anclar(hoja, AmiEtiquetaLayout.JAPAN_DIRECCION, base + offset), indice);
            }
        }
```

Y añadir el helper al final de la clase:

```java
    /** Traduce un anclaje relativo al bloque a un anclaje de tamaño fijo de POI. */
    private static XSSFClientAnchor anclar(XSSFSheet hoja, AnclajeBloque anclaje, int base) {
        return AnclajeImagen.fijo(hoja, AmiEtiquetaLayout.COL_BARCODE, anclaje.dx(),
                base + anclaje.fila(), anclaje.dy(), anclaje.cx(), anclaje.cy());
    }
```

Añadir el import `org.apache.poi.xssf.usermodel.XSSFClientAnchor`.

- [ ] **Step 6: Ejecutar los tests**

Run: `mvn test -Dtest='AmiEtiquetaLayoutTest+AmiEtiquetas*Test'`
Expected: PASS. Los tests de builder y generador no se han tocado: si pasan, el refactor no ha cambiado comportamiento.

- [ ] **Step 7: Ejecutar la suite completa**

Run: `mvn test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AnclajeBloque.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayout.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetaLayoutTest.java
git commit -m "AnclajeBloque agrupa la geometria de imagenes de las etiquetas de AMI"
```

---

### Task 3: `HojaEan.columnaOpcional`

Hace falta para que un excel de pedido sin las columnas `EAN13`, `EAN128` o `Made in` avise en vez de reventar el flujo de etiquetas.

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/HojaEan.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/HojaEanTest.java`
- Modify: `src/test/java/com/puntotres/packinglist/testutil/PedidoAmiExcel.java`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `int HojaEan.columnaOpcional(String titulo)` — el índice de la columna, o `-1` si no está.
  - `byte[] PedidoAmiExcel.crearSinColumnasEan(String nombreHojaEan, Fila... filas)` — el mismo libro pero con la cabecera cortada antes de `EAN13`.

- [ ] **Step 1: Escribir los tests que fallan**

Añadir a `HojaEanTest`:

```java
    @Test
    void columnaOpcionalDevuelveElIndiceCuandoLaColumnaEsta() throws Exception {
        try (HojaEan hoja = HojaEan.abrir(pedido())) {
            assertEquals(8, hoja.columnaOpcional("EAN13"));
            assertEquals(9, hoja.columnaOpcional("EAN128"));
        }
    }

    @Test
    void columnaOpcionalDevuelveMenosUnoSinLanzar() throws Exception {
        byte[] sinEan = PedidoAmiExcel.crearSinColumnasEan("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7672));
        try (HojaEan hoja = HojaEan.abrir(sinEan)) {
            assertEquals(-1, hoja.columnaOpcional("EAN13"));
            assertEquals(-1, hoja.columnaOpcional("EAN128"));
            // Las obligatorias siguen ahí y siguen lanzando si faltan.
            assertEquals(1, hoja.columna("ARTICLE"));
        }
    }
```

- [ ] **Step 2: Ejecutar para verificar que no compila**

Run: `mvn test -Dtest=HojaEanTest`
Expected: FAIL de compilación — no existen `columnaOpcional` ni `crearSinColumnasEan`.

- [ ] **Step 3: Implementar `columnaOpcional` en `HojaEan`**

Refactorizar la búsqueda para que `columna` se apoye en ella. Sustituir el método `columna(String, String)` por:

```java
    public int columna(String titulo, String rotuloEnElError) {
        int indice = columnaOpcional(titulo);
        if (indice < 0) {
            throw new IllegalArgumentException("La hoja '" + nombre()
                    + "' del excel de pedido no tiene la columna '" + rotuloEnElError + "'");
        }
        return indice;
    }

    /**
     * Igual que {@link #columna(String)} pero devuelve -1 en vez de lanzar
     * cuando la columna no está. Para las columnas de las que se puede
     * prescindir: sin ellas la salida sale incompleta y con aviso, pero sale.
     */
    public int columnaOpcional(String titulo) {
        String buscado = titulo.toUpperCase(Locale.ROOT);
        for (Cell celda : cabecera) {
            if (texto(celda).trim().toUpperCase(Locale.ROOT).startsWith(buscado)) {
                return celda.getColumnIndex();
            }
        }
        return -1;
    }
```

- [ ] **Step 4: Añadir `crearSinColumnasEan` a `PedidoAmiExcel`**

Extraer el cuerpo de `crear` a un método privado parametrizado por los títulos y añadir la variante:

```java
    private static final String[] TITULOS = {"Made in", "ARTICLE", "COLORIS",
            "Libellé coloris", "", "TAILLE", "PO", "Commandé", "EAN13", "EAN128"};

    public static byte[] crear(String nombreHojaEan, Fila... filas) {
        return crear(nombreHojaEan, TITULOS.length, filas);
    }

    /**
     * El mismo libro pero sin las columnas EAN13/EAN128 en la cabecera, para
     * los tests de excels de pedido de temporadas antiguas.
     */
    public static byte[] crearSinColumnasEan(String nombreHojaEan, Fila... filas) {
        return crear(nombreHojaEan, 8, filas);
    }

    private static byte[] crear(String nombreHojaEan, int numColumnas, Fila... filas) {
        // ... cuerpo actual, escribiendo solo los primeros numColumnas títulos
        // y omitiendo las celdas 8 y 9 cuando numColumnas <= 8.
    }
```

El cuerpo completo:

```java
    private static byte[] crear(String nombreHojaEan, int numColumnas, Fila... filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("BOLSITAS ANTIHUMEDAD"); // señuelo: no es la primera hoja EAN
            Sheet hoja = libro.createSheet(nombreHojaEan);
            Row cabecera = hoja.createRow(0);
            for (int i = 0; i < numColumnas; i++) {
                cabecera.createCell(i).setCellValue(TITULOS[i]);
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
                if (numColumnas > 8 && fila.ean13() != null) {
                    f.createCell(8).setCellValue(fila.ean13());
                }
                if (numColumnas > 9 && fila.ean128() != null) {
                    f.createCell(9).setCellValue(fila.ean128());
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
```

Y `Fila` gana `ean128` manteniendo las firmas cortas que ya usan los tests:

```java
    /**
     * po: String ("07704 CH") o Number (7672 = PO de France sin sufijo).
     * ean13/ean128: null para los tests que no los necesitan.
     */
    public record Fila(String madeIn, String article, String coloris, String libelle,
                       String taille, Object po, String ean13, String ean128) {

        /** Sin EAN: firma que ya usaban los tests de etiquetas de caja. */
        public Fila(String madeIn, String article, String coloris,
                    String libelle, String taille, Object po) {
            this(madeIn, article, coloris, libelle, taille, po, null, null);
        }

        /** Solo con EAN13: firma que ya usaban los tests de etiquetas de artículo. */
        public Fila(String madeIn, String article, String coloris, String libelle,
                    String taille, Object po, String ean13) {
            this(madeIn, article, coloris, libelle, taille, po, ean13, null);
        }
    }
```

- [ ] **Step 5: Ejecutar los tests**

Run: `mvn test -Dtest=HojaEanTest`
Expected: PASS

- [ ] **Step 6: Ejecutar la suite completa**

Run: `mvn test`
Expected: PASS (las firmas cortas de `Fila` siguen existiendo, así que nada más se rompe).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/HojaEan.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/HojaEanTest.java \
        src/test/java/com/puntotres/packinglist/testutil/PedidoAmiExcel.java
git commit -m "HojaEan.columnaOpcional para las columnas de las que se puede prescindir"
```

---

### Task 4: `AmiPedidoExcel` lee y resuelve los EAN

El corazón del cambio. `buscar` gana la talla y devuelve los dos códigos con sus avisos. La resolución del `colorCode` y del `orderNumber` **no cambia**: sigue filtrando por `ARTICLE` + sufijo y prefiriendo el `COLORIS`, con caída a la primera candidata. Los EAN, en cambio, exigen clave exacta `ARTICLE`+`COLORIS`+`TAILLE`+sufijo.

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcelTest.java`

**Interfaces:**
- Consumes: `HojaEan.columnaOpcional(String)` (Task 3), `PedidoAmiExcel.Fila` con `ean128` y `crearSinColumnasEan` (Task 3), `CodigoBarrasEan13.esValido(String)` (ya existe).
- Produces:
  - `record FilaPedido(String orderNumber, String colorCode, String ean13, String ean128, List<String> avisosEan)` — `ean13`/`ean128` a `null` cuando no se pueden dar; `avisosEan` nunca es null (lista vacía si no hay nada que decir), y sus mensajes **no llevan** contexto de caja/destinación: lo pone quien llama.
  - `Optional<FilaPedido> buscar(String referencia, String codigoColor, String talla, String sufijoPo)` — `talla` null o vacía = talla única (`U`).
  - `List<String> avisos()` — avisos de nivel de libro (columnas ausentes).

- [ ] **Step 1: Escribir los tests que fallan**

Sustituir `AmiPedidoExcelTest` entero por:

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.testutil.PedidoAmiExcel;
import com.puntotres.packinglist.testutil.PedidoAmiExcel.Fila;

class AmiPedidoExcelTest {

    /** EAN128 bien formado: EAN13 + 00001 + PO a 8 dígitos + 16 ceros + país. */
    private static String ean128(String ean13, int po, String pais) {
        return ean13 + "00001" + String.format("%08d", po) + "0000000000000000" + pais;
    }

    private static byte[] pedidoTipico() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07703 CH",
                        "3666598354771", ean128("3666598354771", 7703, "ES")),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07697 JP",
                        "3666598354771", ean128("3666598354771", 7697, "ES")),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665,
                        "3666598354771", ean128("3666598354771", 7665, "ES")),
                // Misma referencia, OTRO color: EAN13 distinto y mismo PO France.
                new Fila("SPAIN", "ULL163.AL0052", "001", "DARK COFFEE", "U", 7665,
                        "3666598313495", ean128("3666598313495", 7665, "ES")),
                // Cinturón: una fila por talla, mismo PO France, EAN13 por talla.
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "85", 7672,
                        "3666598890064", ean128("3666598890064", 7672, "MA")),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "95", 7672,
                        "3666598890088", ean128("3666598890088", 7672, "MA")));
    }

    // --- lo que ya funcionaba y no debe cambiar ---

    @Test
    void encuentraElPoDeCadaDestinacionPorSufijo() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());

        assertEquals("07703",
                pedido.buscar("ULL163.AL0052", "221", null, "CH").orElseThrow().orderNumber());
        assertEquals("07697",
                pedido.buscar("ULL163.AL0052", "221", null, "JP").orElseThrow().orderNumber());
        // PO numérico sin sufijo = France, con padding a 5 dígitos.
        assertEquals("07665",
                pedido.buscar("ULL163.AL0052", "221", null, null).orElseThrow().orderNumber());
    }

    @Test
    void elColorCodeEsColorisMasLibelle() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertEquals("001 BLACK",
                pedido.buscar("UBL029.AL0216", "001", "85", null).orElseThrow().colorCode());
    }

    @Test
    void referenciaNoEncontradaDevuelveVacio() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertTrue(pedido.buscar("ULL999.XX9999", "001", null, "CH").isEmpty());
        // Referencia existe pero no para esa destinación.
        assertTrue(pedido.buscar("UBL029.AL0216", "001", "85", "CH").isEmpty());
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
                AmiPedidoExcel.desdeBytes(pedidoTipico()).buscar("ULL163.AL0052", "221", null, "CH");
        assertTrue(fila.isPresent());
    }

    // --- EAN13 y EAN128 ---

    @Test
    void devuelveLosDosCodigosDeUnBolso() throws IOException {
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedidoTipico())
                .buscar("ULL163.AL0052", "221", null, "CH").orElseThrow();

        assertEquals("3666598354771", fila.ean13());
        assertEquals(ean128("3666598354771", 7703, "ES"), fila.ean128());
        assertTrue(fila.avisosEan().isEmpty());
    }

    @Test
    void elEan13EsComunPeroElEan128CambiaPorDestinacion() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        AmiPedidoExcel.FilaPedido china =
                pedido.buscar("ULL163.AL0052", "221", null, "CH").orElseThrow();
        AmiPedidoExcel.FilaPedido japan =
                pedido.buscar("ULL163.AL0052", "221", null, "JP").orElseThrow();

        assertEquals(china.ean13(), japan.ean13());
        assertEquals(ean128("3666598354771", 7703, "ES"), china.ean128());
        assertEquals(ean128("3666598354771", 7697, "ES"), japan.ean128());
    }

    @Test
    void elColorFormaParteDeLaClave() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertEquals("3666598354771",
                pedido.buscar("ULL163.AL0052", "221", null, null).orElseThrow().ean13());
        assertEquals("3666598313495",
                pedido.buscar("ULL163.AL0052", "001", null, null).orElseThrow().ean13());
    }

    @Test
    void cadaTallaDeCinturonTieneSuEan13() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(pedidoTipico());
        assertEquals("3666598890064",
                pedido.buscar("UBL029.AL0216", "001", "85", null).orElseThrow().ean13());
        assertEquals("3666598890088",
                pedido.buscar("UBL029.AL0216", "001", "95", null).orElseThrow().ean13());
    }

    @Test
    void tallaQueNoEstaEnElPedidoAvisaYNoDaCodigos() throws IOException {
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedidoTipico())
                .buscar("UBL029.AL0216", "001", "105", null).orElseThrow();

        // El color code y el PO siguen saliendo: lo que falta son los EAN.
        assertEquals("001 BLACK", fila.colorCode());
        assertEquals("07672", fila.orderNumber());
        assertNull(fila.ean13());
        assertNull(fila.ean128());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("105")));
    }

    @Test
    void colorQueNoEstaEnElPedidoAvisaYNoDaCodigos() throws IOException {
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedidoTipico())
                .buscar("ULL163.AL0052", "999", null, "CH").orElseThrow();

        // colorCode cae a la primera candidata, como hasta ahora.
        assertEquals("221 BLACK", fila.colorCode());
        assertNull(fila.ean13());
        assertNull(fila.ean128());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("999")));
    }

    @Test
    void unEan13InvalidoAvisaYSeDescarta() throws IOException {
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665,
                        "3666598354770", ean128("3666598354770", 7665, "ES")));
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedido)
                .buscar("ULL163.AL0052", "221", null, null).orElseThrow();

        // 3666598354770: dígito de control incorrecto (el bueno es 1).
        assertNull(fila.ean13());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("3666598354770")));
        // El EAN128 no depende del EAN13 y se sigue dando.
        assertEquals(ean128("3666598354770", 7665, "ES"), fila.ean128());
    }

    @Test
    void unEan128FueraDeEstructuraSeDaIgualPeroAvisa() throws IOException {
        // Caso real del fichero del cliente: Made in MOROCCO pero sufijo ES.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("MOROCCO", "USL728.AL0217", "001", "BLACK", "U", 7685,
                        "3666598897124", ean128("3666598897124", 7685, "ES")));
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedido)
                .buscar("USL728.AL0217", "001", null, null).orElseThrow();

        assertEquals(ean128("3666598897124", 7685, "ES"), fila.ean128());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("EAN128")));
    }

    @Test
    void unEan128ConElPoDeOtraDestinacionAvisa() throws IOException {
        // Caso real: dos filas con los EAN128 intercambiados entre destinaciones.
        byte[] pedido = PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL027.AL0103", "718", "BLACK", "U", "07691 JP",
                        "3666598886005", ean128("3666598886005", 7705, "ES")));
        AmiPedidoExcel.FilaPedido fila = AmiPedidoExcel.desdeBytes(pedido)
                .buscar("ULL027.AL0103", "718", null, "JP").orElseThrow();

        assertEquals(ean128("3666598886005", 7705, "ES"), fila.ean128());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("EAN128")));
    }

    @Test
    void sinColumnasEanAvisaAlAbrirYLasEtiquetasVanSinCodigos() throws IOException {
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(
                PedidoAmiExcel.crearSinColumnasEan("EAN H26",
                        new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665)));

        assertTrue(pedido.avisos().stream().anyMatch(a -> a.contains("EAN13")));
        assertTrue(pedido.avisos().stream().anyMatch(a -> a.contains("EAN128")));
        AmiPedidoExcel.FilaPedido fila =
                pedido.buscar("ULL163.AL0052", "221", null, null).orElseThrow();
        assertEquals("221 BLACK", fila.colorCode());
        assertNull(fila.ean13());
        assertNull(fila.ean128());
    }
}
```

- [ ] **Step 2: Ejecutar para verificar que falla**

Run: `mvn test -Dtest=AmiPedidoExcelTest`
Expected: FAIL de compilación — `buscar` no acepta 4 argumentos y `FilaPedido` no tiene `ean13()`.

- [ ] **Step 3: Reescribir `AmiPedidoExcel`**

Sustituir el fichero entero por:

```java
package com.puntotres.packinglist.service.etiquetas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
 *
 * De aquí salen dos cosas con reglas distintas a propósito:
 * <ul>
 * <li><b>order number y color code</b>: filtrando por ARTICLE + sufijo de PO y
 * prefiriendo el COLORIS, con caída a la primera fila candidata. Un color code
 * aproximado es aceptable.</li>
 * <li><b>EAN13 y EAN128</b>: solo con clave EXACTA ARTICLE + COLORIS + TAILLE +
 * sufijo de PO, que en el fichero real identifica una única fila. Imprimir un
 * código de barras equivocado es peor que no imprimirlo.</li>
 * </ul>
 *
 * La lectura de bajo nivel (localizar la hoja, resolver columnas, leer
 * celdas) está en HojaEan, compartida con las etiquetas de artículo. Las
 * filas ocultas se leen como las demás: ocultar es estado de vista, y el
 * fichero real del cliente viene con casi todas ocultas por un filtro.
 */
public class AmiPedidoExcel {

    /** Talla de lo que no es cinturón en la columna TAILLE del pedido. */
    private static final String TALLA_UNICA = "U";

    /**
     * Una coincidencia del pedido. ean13/ean128 son null cuando no se pueden
     * dar (sin fila exacta, sin columna, EAN13 inválido); avisosEan explica
     * por qué, sin contexto de caja ni destinación: lo añade quien llama.
     */
    public record FilaPedido(String orderNumber, String colorCode,
                             String ean13, String ean128, List<String> avisosEan) {

        public FilaPedido {
            avisosEan = List.copyOf(avisosEan);
        }
    }

    private record FilaCruda(String madeIn, String article, String coloris, String libelle,
                             String taille, String poNumerico, String poSufijo,
                             String ean13, String ean128) {
    }

    private final List<FilaCruda> filas;
    private final List<String> avisos;

    private AmiPedidoExcel(List<FilaCruda> filas, List<String> avisos) {
        this.filas = List.copyOf(filas);
        this.avisos = List.copyOf(avisos);
    }

    public static AmiPedidoExcel desdeBytes(byte[] contenido) throws IOException {
        try (HojaEan hoja = HojaEan.abrir(contenido)) {
            int colArticle = hoja.columna("ARTICLE");
            int colColoris = hoja.columna("COLORIS");
            int colLibelle = hoja.columna("LIBELL", "Libellé coloris");
            int colTaille = hoja.columna("TAILLE");
            int colPo = hoja.columna("PO");
            int colMadeIn = hoja.columnaOpcional("MADE IN");
            int colEan13 = hoja.columnaOpcional("EAN13");
            int colEan128 = hoja.columnaOpcional("EAN128");

            List<String> avisos = new ArrayList<>();
            if (colEan13 < 0) {
                avisos.add("El excel de pedido no tiene la columna 'EAN13': "
                        + "las etiquetas van sin ese código de barras");
            }
            if (colEan128 < 0) {
                avisos.add("El excel de pedido no tiene la columna 'EAN128': "
                        + "las etiquetas van sin ese código de barras");
            }
            if (colMadeIn < 0) {
                avisos.add("El excel de pedido no tiene la columna 'Made in': "
                        + "no se comprueba el país del EAN128");
            }

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
                        textoDe(hoja, i, colMadeIn).toUpperCase(Locale.ROOT),
                        article.trim().toUpperCase(Locale.ROOT),
                        hoja.texto(i, colColoris).trim(),
                        hoja.texto(i, colLibelle).trim(),
                        hoja.texto(i, colTaille).trim(),
                        String.format("%05d", Long.parseLong(numerico)),
                        sufijo.isBlank() ? null : sufijo,
                        textoDe(hoja, i, colEan13),
                        textoDe(hoja, i, colEan128)));
            }
            return new AmiPedidoExcel(filas, avisos);
        }
    }

    /** Avisos de nivel de libro: columnas de las que se ha prescindido. */
    public List<String> avisos() {
        return avisos;
    }

    /**
     * Busca la fila del pedido para una referencia, color, talla y
     * destinación. talla null o vacía = talla única ("U"), que es lo que
     * traen en el pedido los bolsos.
     */
    public Optional<FilaPedido> buscar(String referencia, String codigoColor,
                                       String talla, String sufijoPo) {
        String ref = referencia == null ? "" : referencia.trim().toUpperCase(Locale.ROOT);
        String color = codigoColor == null ? "" : codigoColor.trim();
        List<FilaCruda> candidatas = filas.stream()
                .filter(fila -> fila.article().equals(ref))
                .filter(fila -> sufijoPo == null
                        ? fila.poSufijo() == null
                        : sufijoPo.equalsIgnoreCase(fila.poSufijo()))
                .toList();
        if (candidatas.isEmpty()) {
            return Optional.empty();
        }

        // Order number y color code: como siempre, con caída a la primera.
        FilaCruda elegida = candidatas.stream()
                .filter(fila -> fila.coloris().equalsIgnoreCase(color))
                .findFirst()
                .orElse(candidatas.get(0));
        String colorCode = elegida.libelle().isBlank()
                ? elegida.coloris()
                : elegida.coloris() + " " + elegida.libelle();

        // Los EAN, solo con clave exacta.
        String tallaBuscada = talla == null || talla.isBlank() ? TALLA_UNICA : talla.trim();
        List<FilaCruda> exactas = candidatas.stream()
                .filter(fila -> fila.coloris().equalsIgnoreCase(color))
                .filter(fila -> fila.taille().equalsIgnoreCase(tallaBuscada))
                .toList();

        List<String> avisosEan = new ArrayList<>();
        String ean13 = null;
        String ean128 = null;
        if (exactas.size() == 1) {
            FilaCruda exacta = exactas.get(0);
            ean13 = exacta.ean13().isBlank() ? null : exacta.ean13();
            if (ean13 != null && !CodigoBarrasEan13.esValido(ean13)) {
                avisosEan.add("el EAN13 '" + ean13 + "' del pedido no es un EAN-13 válido"
                        + " (13 dígitos con dígito de control): etiqueta sin ese código");
                ean13 = null;
            }
            ean128 = exacta.ean128().isBlank() ? null : exacta.ean128();
            String esperado = estructuraEsperada(exacta);
            if (ean128 != null && esperado != null && !esperado.equals(ean128)) {
                avisosEan.add("el EAN128 del pedido (" + ean128 + ") no cuadra con su EAN13,"
                        + " su PO y su 'Made in' (debería ser " + esperado + "):"
                        + " se imprime tal cual, pero revisar el fichero con el cliente");
            }
        } else if (exactas.isEmpty()) {
            avisosEan.add("el pedido no tiene fila de " + ref + " color '" + color
                    + "' talla '" + tallaBuscada + "' para "
                    + (sufijoPo == null ? "France" : sufijoPo)
                    + ": etiqueta sin EAN13 ni EAN128");
        } else {
            avisosEan.add("el pedido tiene " + exactas.size() + " filas de " + ref + " color '"
                    + color + "' talla '" + tallaBuscada + "' para "
                    + (sufijoPo == null ? "France" : sufijoPo)
                    + ": etiqueta sin EAN13 ni EAN128 para no elegir a ciegas");
        }
        return Optional.of(
                new FilaPedido(elegida.poNumerico(), colorCode, ean13, ean128, avisosEan));
    }

    /**
     * El EAN128 del cliente es EAN13 + "00001" + PO a 8 dígitos + 16 ceros +
     * país ("ES" SPAIN, "MA" MOROCCO). Devuelve null si no se puede componer
     * (sin EAN13, sin 'Made in' o país desconocido): entonces no se comprueba.
     *
     * Sirve solo para AVISAR: el EAN128 que se imprime es siempre el de la
     * columna, verbatim. En el fichero real hay 8 filas que no cuadran y es el
     * código del cliente el que espera su escáner.
     */
    private static String estructuraEsperada(FilaCruda fila) {
        String pais = switch (fila.madeIn()) {
            case "SPAIN" -> "ES";
            case "MOROCCO" -> "MA";
            default -> null;
        };
        if (pais == null || fila.ean13().isBlank()) {
            return null;
        }
        return fila.ean13() + "00001"
                + String.format("%08d", Long.parseLong(fila.poNumerico()))
                + "0000000000000000" + pais;
    }

    /** Texto de una celda cuya columna puede no existir (-1 = "" sin leer). */
    private static String textoDe(HojaEan hoja, int fila, int columna) {
        return columna < 0 ? "" : hoja.texto(fila, columna).trim();
    }
}
```

- [ ] **Step 4: Arreglar la llamada de `AmiEtiquetasGenerador` para que compile**

En `AmiEtiquetasGenerador.etiquetaDe`, línea 181, añadir el argumento de talla (el uso real se afina en la Task 6):

```java
        Optional<AmiPedidoExcel.FilaPedido> fila = pedido.buscar(lider.getReferencia(),
                lider.getCodigoColor(), lider.esCinturon() ? lider.getTalla() : null,
                layout.sufijoPo());
```

- [ ] **Step 5: Ejecutar los tests**

Run: `mvn test -Dtest=AmiPedidoExcelTest`
Expected: PASS (los 15 tests).

- [ ] **Step 6: Ejecutar la suite completa**

Run: `mvn test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcel.java \
        src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoExcelTest.java
git commit -m "AmiPedidoExcel resuelve EAN13 y EAN128 con clave exacta y avisa de las anomalias"
```

---

### Task 5: El builder dibuja los tres códigos

`EtiquetaCaja` gana `ean13` y `ean128`, e `insertarImagenes` pasa a resolver una lista de imágenes y dibujarla en las dos etiquetas del par. Se aprovecha para reutilizar el índice de imagen del libro cuando el mismo código se repite: pasar de 1 a 3 códigos por etiqueta triplicaría el número de partes de imagen del `.xlsx` (dos por etiqueta y por caja) si no se cachea.

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilderTest.java`

**Interfaces:**
- Consumes: `AnclajeBloque` y `AmiEtiquetaLayout.po()/ean13()/ean128()` (Task 2), `CodigoBarrasEan13.png(String)` que devuelve `Optional<byte[]>`, `CodigoBarrasCode128.png(String)` que devuelve `byte[]`.
- Produces: `record EtiquetaCaja(String temporada, String referencia, String colorCode, String talla, String cantidad, String pesoBruto, String parcel, String orderNumber, String ean13, String ean128)` — los dos últimos null = sin ese código de barras.

- [ ] **Step 1: Escribir los tests que fallan**

En `AmiEtiquetasExcelBuilderTest`, actualizar el fixture y los `new EtiquetaCaja(...)` existentes con los dos campos nuevos, y añadir los tests de imágenes. Fixture:

```java
    private static final String EAN13_BOLSO = "3666598354771";
    private static final String EAN128_BOLSO =
            "366659835477100001000077030000000000000000MA";

    private static EtiquetaCaja etiquetaBolso(String parcel) {
        return new EtiquetaCaja("H26", "ULL163.AL0052", "221 BLACK",
                "U", "50", "5,28 KGS", parcel, "07703", EAN13_BOLSO, EAN128_BOLSO);
    }
```

Los otros cuatro `new EtiquetaCaja(...)` del fichero pasan a llevar `EAN13_BOLSO, EAN128_BOLSO` al final, salvo el de `sinOrderNumberNoHayBarcodePeroElExcelSaleIgual`, que los lleva `null, null`.

Sustituir `insertaUnCodigoDeBarrasPorEtiquetaYNingunaImagenDeEjemplo` y `enJapanCadaParLlevaAdemasLaDireccionComoImagen` por:

```java
    @Test
    void insertaLosTresCodigosPorEtiquetaYNingunaImagenDeEjemplo() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                etiquetaBolso("1 / 2"), etiquetaBolso("2 / 2")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            XSSFDrawing dibujo = hoja.getDrawingPatriarch();
            assertNotNull(dibujo);
            // 2 cajas x 2 etiquetas x 3 códigos = 12 imágenes, y nada más
            // (las imágenes de ejemplo de la plantilla se limpian).
            assertEquals(12, dibujo.getShapes().size());
        }
    }

    @Test
    void cadaCodigoSeAnclaEnLaFilaQueDiceElLayout() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA,
                List.of(etiquetaBolso("1 / 1")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFDrawing dibujo = libro.getSheetAt(0).getDrawingPatriarch();
            List<Integer> filas = dibujo.getShapes().stream()
                    .map(forma -> ((XSSFPicture) forma).getClientAnchor().getRow1())
                    .sorted()
                    .toList();
            // Las tres filas del layout de CHINA (8, 10, 12) y las mismas
            // + offsetSegundaEtiqueta (17) para la etiqueta de abajo.
            assertEquals(List.of(8, 10, 12, 8 + 17, 10 + 17, 12 + 17), filas);
        }
    }

    @Test
    void enJapanCadaParLlevaAdemasLaDireccionComoImagen() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.JAPAN, List.of(
                etiquetaBolso("1 / 2"), etiquetaBolso("2 / 2")));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // 2 cajas x 2 etiquetas x (3 códigos + 1 dirección) = 16 imágenes.
            assertEquals(16, hoja.getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void sinEanLosOtrosCodigosSiguenSaliendo() throws IOException {
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                new EtiquetaCaja("H26", "ULL163.AL0052", "221 BLACK", "U", "50",
                        "5,28 KGS", "1 / 1", "07703", null, null)));
        try (XSSFWorkbook libro = abrir(excel)) {
            // Solo el barcode del PO, en las dos etiquetas del par.
            assertEquals(2, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void unEan13InvalidoNoRompeElExcel() throws IOException {
        // El generador ya lo filtra, pero el builder no debe reventar si le
        // llega uno malo: la etiqueta sale sin ese código.
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                new EtiquetaCaja("H26", "ULL163.AL0052", "221 BLACK", "U", "50",
                        "5,28 KGS", "1 / 1", "07703", "123", EAN128_BOLSO)));
        try (XSSFWorkbook libro = abrir(excel)) {
            // PO + EAN128 en las dos etiquetas = 4 (el EAN13 malo no se dibuja).
            assertEquals(4, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void elMismoCodigoNoSeGuardaDosVecesEnElLibro() throws IOException {
        // 3 cajas iguales = 18 imágenes ancladas pero solo 3 partes de imagen
        // en el .xlsx: PO, EAN13 y EAN128 se reutilizan.
        byte[] excel = builder.generar(AmiEtiquetaLayout.CHINA, List.of(
                etiquetaBolso("1 / 3"), etiquetaBolso("2 / 3"), etiquetaBolso("3 / 3")));
        try (XSSFWorkbook libro = abrir(excel)) {
            assertEquals(18, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
            assertEquals(3, libro.getAllPictures().size());
        }
    }
```

Añadir los imports `org.apache.poi.xssf.usermodel.XSSFPicture` y `java.util.List` (ya está).

- [ ] **Step 2: Ejecutar para verificar que falla**

Run: `mvn test -Dtest=AmiEtiquetasExcelBuilderTest`
Expected: FAIL de compilación — `EtiquetaCaja` no acepta 10 argumentos.

- [ ] **Step 3: Ampliar `EtiquetaCaja` y reescribir el dibujo**

En `AmiEtiquetasExcelBuilder`:

```java
    /**
     * Los datos ya formateados de la etiqueta de una caja física. null =
     * celda en blanco, y sin ese código de barras: sin orderNumber no hay
     * Code 128 del PO, sin ean13 no hay EAN-13 y sin ean128 no hay su Code 128.
     */
    public record EtiquetaCaja(String temporada, String referencia, String colorCode,
                               String talla, String cantidad, String pesoBruto,
                               String parcel, String orderNumber,
                               String ean13, String ean128) {
    }

    /** Una imagen ya resuelta: dónde va en el bloque y su índice en el libro. */
    private record ImagenAnclada(AnclajeBloque anclaje, int indice) {
    }
```

`generar` crea la caché y la pasa a `insertarImagenes`:

```java
            BloqueEtiquetaModelo modelo = BloqueEtiquetaModelo.capturar(hoja, layout.alturaBloque());
            for (int i = 1; i < etiquetas.size(); i++) {
                modelo.copiarEn(hoja, i * layout.alturaBloque());
            }
            // Un envío repite mucho la misma referencia y el mismo PO: sin esta
            // caché el .xlsx guardaría el mismo PNG una vez por etiqueta.
            Map<String, Integer> imagenesDelLibro = new HashMap<>();
            for (int i = 0; i < etiquetas.size(); i++) {
                int base = i * layout.alturaBloque();
                escribirEtiqueta(hoja, layout, base, etiquetas.get(i));
                escribirEtiqueta(hoja, layout, base + layout.offsetSegundaEtiqueta(),
                        etiquetas.get(i));
                insertarImagenes(libro, hoja, layout, base, etiquetas.get(i), direccionJapan,
                        imagenesDelLibro);
                if (i < etiquetas.size() - 1) {
                    hoja.setRowBreak(base + layout.alturaBloque() - 1);
                }
            }
```

Y el nuevo `insertarImagenes` más sus helpers:

```java
    private void insertarImagenes(XSSFWorkbook libro, XSSFSheet hoja, AmiEtiquetaLayout layout,
                                  int base, EtiquetaCaja etiqueta, byte[] direccionJapan,
                                  Map<String, Integer> cache) {
        XSSFDrawing dibujo = hoja.createDrawingPatriarch();
        List<ImagenAnclada> imagenes = new ArrayList<>();
        if (tiene(etiqueta.orderNumber())) {
            imagenes.add(new ImagenAnclada(layout.po(), indice(libro, cache,
                    "PO:" + etiqueta.orderNumber(),
                    () -> CodigoBarrasCode128.png(etiqueta.orderNumber()))));
        }
        // El EAN13 puede llegar inválido: entonces no se dibuja y la etiqueta
        // sale igual (el aviso lo dio ya AmiPedidoExcel). Se comprueba con
        // esValido para no pagar el render aquí: el Supplier lo hace luego, y
        // solo la primera vez que aparece ese código.
        if (tiene(etiqueta.ean13()) && CodigoBarrasEan13.esValido(etiqueta.ean13())) {
            imagenes.add(new ImagenAnclada(layout.ean13(), indice(libro, cache,
                    "EAN13:" + etiqueta.ean13(),
                    () -> CodigoBarrasEan13.png(etiqueta.ean13()).orElseThrow())));
        }
        if (tiene(etiqueta.ean128())) {
            imagenes.add(new ImagenAnclada(layout.ean128(), indice(libro, cache,
                    "EAN128:" + etiqueta.ean128(),
                    () -> CodigoBarrasCode128.png(etiqueta.ean128()))));
        }
        if (direccionJapan != null && "AMI JAPAN".equals(layout.nombreHoja())) {
            imagenes.add(new ImagenAnclada(AmiEtiquetaLayout.JAPAN_DIRECCION,
                    indice(libro, cache, "DIRECCION", () -> direccionJapan)));
        }

        for (int offset : new int[] {0, layout.offsetSegundaEtiqueta()}) {
            for (ImagenAnclada imagen : imagenes) {
                dibujo.createPicture(
                        anclar(hoja, imagen.anclaje(), base + offset), imagen.indice());
            }
        }
    }

    private static boolean tiene(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** Índice de la imagen en el libro, añadiéndola solo la primera vez. */
    private static int indice(XSSFWorkbook libro, Map<String, Integer> cache,
                             String clave, Supplier<byte[]> png) {
        return cache.computeIfAbsent(clave,
                k -> libro.addPicture(png.get(), Workbook.PICTURE_TYPE_PNG));
    }

    /** Traduce un anclaje relativo al bloque a un anclaje de tamaño fijo de POI. */
    private static XSSFClientAnchor anclar(XSSFSheet hoja, AnclajeBloque anclaje, int base) {
        return AnclajeImagen.fijo(hoja, AmiEtiquetaLayout.COL_BARCODE, anclaje.dx(),
                base + anclaje.fila(), anclaje.dy(), anclaje.cx(), anclaje.cy());
    }
```

Imports nuevos: `java.util.ArrayList`, `java.util.HashMap`, `java.util.Map`, `java.util.function.Supplier`, `org.apache.poi.xssf.usermodel.XSSFClientAnchor`.

- [ ] **Step 4: Ejecutar los tests del builder**

Run: `mvn test -Dtest=AmiEtiquetasExcelBuilderTest`
Expected: PASS

- [ ] **Step 5: Ejecutar la suite completa**

Run: `mvn test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilderTest.java
git commit -m "las etiquetas de caja de AMI dibujan EAN13 y EAN128 ademas del PO"
```

---

### Task 6: El generador rellena los dos códigos y sus avisos

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java`

**Interfaces:**
- Consumes: `AmiPedidoExcel.buscar(referencia, color, talla, sufijoPo)` y `FilaPedido.ean13()/ean128()/avisosEan()` (Task 4); `EtiquetaCaja` de 10 componentes (Task 5).
- Produces: nada nuevo hacia fuera; `ResultadoEtiquetas.getAvisos()` incluye ahora los de `AmiPedidoExcel.avisos()` y los de cada caja con el prefijo `"Caja N de DESTINO: "`.

- [ ] **Step 1: Escribir los tests que fallan**

En `AmiEtiquetasGeneradorTest`, ampliar el fixture `pedido()` con los EAN y añadir los tests. Fixture:

```java
    private static String ean128(String ean13, int po, String pais) {
        return ean13 + "00001" + String.format("%08d", po) + "0000000000000000" + pais;
    }

    private static byte[] pedido() {
        return PedidoAmiExcel.crear("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665,
                        "3666598354771", ean128("3666598354771", 7665, "ES")),
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", "07703 CH",
                        "3666598354771", ean128("3666598354771", 7703, "ES")),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "85", 7672,
                        "3666598890064", ean128("3666598890064", 7672, "MA")),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "95", 7672,
                        "3666598890088", ean128("3666598890088", 7672, "MA")),
                new Fila("MOROCCO", "UBL029.AL0216", "001", "BLACK", "105", 7672,
                        "3666598890101", ean128("3666598890101", 7672, "MA")));
    }
```

Tests nuevos:

```java
    @Test
    void laEtiquetaLlevaLosTresCodigosDeBarras() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("CHINA", caja(1, "ULL163.AL0052", "221", null, 40, 4.10, "07703")))),
                cabecera(), Map.of("pedido", pedido()));

        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            // PO + EAN13 + EAN128 en las dos etiquetas del par.
            assertEquals(6, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
        assertTrue(resultado.getAvisos().isEmpty(), resultado.getAvisos().toString());
    }

    @Test
    void enCinturonMultiTallaElEanEsElDeLaLineaLider() throws IOException {
        // Las líneas llegan 95, 85, 105: la líder es la 95 (la que lleva el
        // peso), así que el EAN13 es el de la 95, no el de la talla menor.
        ResultadoEtiquetas resultado = generador.generar(List.of(importado(destino("PARIS",
                        caja(2, "UBL029.AL0216", "001", "95", 33, 9.93, "07672"),
                        caja(2, "UBL029.AL0216", "001", "85", 4, null, "07672"),
                        caja(2, "UBL029.AL0216", "001", "105", 3, null, "07672")))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getAvisos().isEmpty(), resultado.getAvisos().toString());
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            // SIZE sí sale ordenado, aunque el EAN sea el de la líder.
            assertEquals("85-95-105", texto(hoja, 12, 2));
            assertEquals(6, hoja.getDrawingPatriarch().getShapes().size());
            // 3 partes de imagen distintas = PO, EAN13 de la 95 y su EAN128.
            assertEquals(3, libro.getAllPictures().size());
        }
    }

    @Test
    void tallaAusenteDelPedidoAvisaConLaCajaYLaEtiquetaVaSinEan() throws IOException {
        // El pedido de PARIS no tiene la talla 75 de este cinturón.
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "UBL029.AL0216", "001", "75", 45, 8.5, "07672")))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("Caja 1") && aviso.contains("PARIS")
                        && aviso.contains("75")));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            // Solo el barcode del PO, en las dos etiquetas.
            assertEquals(2, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
            // El resto de la etiqueta sale igual.
            assertEquals("001 BLACK", texto(libro.getSheetAt(0), 11, 2));
        }
    }

    @Test
    void referenciaAusenteDelPedidoVaSinEan() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "USL999.XX0000", "007", null, 10, 2.0, "07699")))),
                cabecera(), Map.of("pedido", pedido()));

        assertTrue(resultado.getAvisos().stream()
                .anyMatch(aviso -> aviso.contains("USL999.XX0000") && aviso.contains("EAN")));
        try (XSSFWorkbook libro = abrir(resultado.getExcels().get(0))) {
            assertEquals(2, libro.getSheetAt(0).getDrawingPatriarch().getShapes().size());
        }
    }

    @Test
    void sinColumnasEanElAvisoDelLibroLlegaAlResultado() throws IOException {
        byte[] pedidoViejo = PedidoAmiExcel.crearSinColumnasEan("EAN H26",
                new Fila("SPAIN", "ULL163.AL0052", "221", "BLACK", "U", 7665));
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665")))),
                cabecera(), Map.of("pedido", pedidoViejo));

        assertTrue(resultado.getAvisos().stream().anyMatch(aviso -> aviso.contains("EAN13")));
        assertEquals(1, resultado.getExcels().size());
    }
```

Y en `cinturonesMultiTallaVanEnUnaEtiquetaConTallasYCantidades` cambiar el peso de la caja 1 para que el fixture siga cuadrando: no hace falta tocarlo, pero **sí** hay que revisar que `unaCajaConVariasReferenciasPesaLoQueDigaSuLineaLider` sigue pasando — su segunda línea (`USL728.AL0217`) no está en el pedido, así que ahora genera un aviso más de EAN. El test solo comprueba peso y `cajasPendientes`, así que sigue verde.

- [ ] **Step 2: Ejecutar para verificar que falla**

Run: `mvn test -Dtest=AmiEtiquetasGeneradorTest`
Expected: FAIL — `laEtiquetaLlevaLosTresCodigosDeBarras` encuentra 2 imágenes en vez de 6 (el generador todavía no traslada los EAN).

- [ ] **Step 3: Trasladar los códigos y los avisos en el generador**

En `generar`, después de construir el pedido:

```java
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(contenidoPedido);

        ResultadoEtiquetas resultado = new ResultadoEtiquetas();
        // Avisos de nivel de fichero (columnas ausentes) antes que los de caja.
        resultado.getAvisos().addAll(pedido.avisos());
```

En `etiquetaDe`, sustituir el bloque de búsqueda (líneas 180-196) por:

```java
        // La talla solo entra en la clave de los cinturones: los bolsos van
        // como talla única ("U") en el excel de pedido.
        Optional<AmiPedidoExcel.FilaPedido> fila = pedido.buscar(lider.getReferencia(),
                lider.getCodigoColor(), lider.esCinturon() ? lider.getTalla() : null,
                layout.sufijoPo());
        String colorCode;
        String ean13 = null;
        String ean128 = null;
        if (fila.isPresent()) {
            colorCode = fila.get().colorCode();
            ean13 = fila.get().ean13();
            ean128 = fila.get().ean128();
            for (String aviso : fila.get().avisosEan()) {
                avisos.add("Caja " + lider.getNumeroCaja() + " de " + nombreDestino
                        + ": " + aviso);
            }
            if (orderNumber != null
                    && Long.parseLong(fila.get().orderNumber()) != Long.parseLong(orderNumber)) {
                avisos.add("Caja " + lider.getNumeroCaja() + " de " + nombreDestino
                        + ": el pedido del JSON (" + orderNumber
                        + ") no coincide con el PO del excel de pedido ("
                        + fila.get().orderNumber() + "); la etiqueta lleva el del JSON");
            }
        } else {
            avisos.add("Referencia '" + lider.getReferencia() + "' (" + nombreDestino
                    + ") no encontrada en el excel de pedido: el color code sale del JSON"
                    + " y la etiqueta va sin EAN13 ni EAN128");
            colorCode = lider.getCodigoColor();
        }
```

Y el `return`:

```java
        return new EtiquetaCaja(envio.getTemporada(), lider.getReferencia(), colorCode,
                talla, cantidad, pesoTexto, posicion + " / " + total, orderNumber,
                ean13, ean128);
```

Actualizar además el javadoc de la clase para mencionar que del excel de pedido salen también los dos códigos de barras.

- [ ] **Step 4: Ejecutar los tests del generador**

Run: `mvn test -Dtest=AmiEtiquetasGeneradorTest`
Expected: PASS

- [ ] **Step 5: Ejecutar la suite completa**

Run: `mvn test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java \
        src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java
git commit -m "el generador de etiquetas de AMI traslada EAN13 y EAN128 con sus avisos"
```

---

### Task 7: Verificación contra el excel de pedido REAL

Los tests anteriores usan fixtures sintéticos. Este ancla el comportamiento contra `src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx`, que es el fichero real del cliente, con sus casos raros incluidos.

**Files:**
- Create: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoRealTest.java`

**Interfaces:**
- Consumes: `AmiPedidoExcel.desdeBytes(byte[])` y `buscar(...)` (Task 4).
- Produces: nada.

- [ ] **Step 1: Escribir el test**

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Ancla la resolución de EAN13/EAN128 contra el excel de pedido REAL de AMI
 * ("EAN PUNTOTRES H26.xlsx", copia del de docs/Etiquetas cajas/). Los demás
 * tests usan fixtures sintéticos; este es el que garantiza que la regla de
 * clave exacta funciona con los datos que manda el cliente, rarezas incluidas.
 */
class AmiPedidoRealTest {

    private static AmiPedidoExcel pedido;

    @BeforeAll
    static void cargarElPedidoReal() throws IOException {
        try (InputStream entrada = AmiPedidoRealTest.class
                .getResourceAsStream("/ejemplos/EAN PUNTOTRES H26.xlsx")) {
            assertNotNull(entrada, "Falta src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx");
            pedido = AmiPedidoExcel.desdeBytes(entrada.readAllBytes());
        }
    }

    @Test
    void elFicheroRealTraeTodasLasColumnasQueHacenFalta() {
        assertTrue(pedido.avisos().isEmpty(), pedido.avisos().toString());
    }

    @Test
    void bolso() {
        // Fila 81: ULL027.AL0103 / 001 / U / 07705 CH.
        AmiPedidoExcel.FilaPedido fila =
                pedido.buscar("ULL027.AL0103", "001", null, "CH").orElseThrow();
        assertEquals("07705", fila.orderNumber());
        assertEquals("3666598550098", fila.ean13());
        assertEquals("366659855009800001000077050000000000000000ES", fila.ean128());
        assertTrue(fila.avisosEan().isEmpty(), fila.avisosEan().toString());
    }

    @Test
    void cadaTallaDeCinturonTieneSuEan13() {
        // Filas 28-31: UBL029.AL0216 / 001 / 75-85-95-105 / 7672.
        assertEquals("3666598890040",
                pedido.buscar("UBL029.AL0216", "001", "75", null).orElseThrow().ean13());
        assertEquals("3666598890064",
                pedido.buscar("UBL029.AL0216", "001", "85", null).orElseThrow().ean13());
        assertEquals("3666598890088",
                pedido.buscar("UBL029.AL0216", "001", "95", null).orElseThrow().ean13());
        assertEquals("3666598890101",
                pedido.buscar("UBL029.AL0216", "001", "105", null).orElseThrow().ean13());
    }

    @Test
    void mismoProductoConEan13ComunYEan128PorDestinacion() {
        // Filas 119 y 120: ULL163.AL0052 / 001 / U, en Japan y France.
        AmiPedidoExcel.FilaPedido japan =
                pedido.buscar("ULL163.AL0052", "001", null, "JP").orElseThrow();
        AmiPedidoExcel.FilaPedido france =
                pedido.buscar("ULL163.AL0052", "001", null, null).orElseThrow();

        assertEquals("3666598313495", japan.ean13());
        assertEquals("3666598313495", france.ean13());
        assertTrue(japan.ean128().contains("00007688"), japan.ean128());
        assertTrue(france.ean128().contains("00007663"), france.ean128());
    }

    @Test
    void elColorFormaParteDeLaClave() {
        // Fila 123: la misma referencia en 221 DARK COFFEE tiene otro EAN13.
        assertEquals("3666598354771",
                pedido.buscar("ULL163.AL0052", "221", null, null).orElseThrow().ean13());
    }

    @Test
    void tallaQueNoExisteEnEsaDestinacionNoDaCodigos() {
        // UBL029.AL0104 / 0014 con 07690 JP solo llega hasta la talla 95.
        AmiPedidoExcel.FilaPedido fila =
                pedido.buscar("UBL029.AL0104", "0014", "105", "JP").orElseThrow();

        assertNull(fila.ean13());
        assertNull(fila.ean128());
        assertTrue(fila.avisosEan().stream().anyMatch(a -> a.contains("105")));
        // La talla 95 de la misma caja sí existe.
        assertNotNull(pedido.buscar("UBL029.AL0104", "0014", "95", "JP").orElseThrow().ean13());
    }

    @Test
    void lasFilasAnomalasDelClienteSeImprimenPeroAvisan() {
        // Fila 137: USL728.AL0217 / 001 / U / 7685, Made in MOROCCO pero el
        // EAN128 acaba en ES.
        AmiPedidoExcel.FilaPedido paisRaro =
                pedido.buscar("USL728.AL0217", "001", null, null).orElseThrow();
        assertTrue(paisRaro.ean128().endsWith("ES"), paisRaro.ean128());
        assertTrue(paisRaro.avisosEan().stream().anyMatch(a -> a.contains("EAN128")));

        // Fila 83: ULL027.AL0103 / 718 con 07691 JP lleva dentro el PO 07705.
        AmiPedidoExcel.FilaPedido poIntercambiado =
                pedido.buscar("ULL027.AL0103", "718", null, "JP").orElseThrow();
        assertEquals("07691", poIntercambiado.orderNumber());
        assertTrue(poIntercambiado.ean128().contains("00007705"), poIntercambiado.ean128());
        assertTrue(poIntercambiado.avisosEan().stream().anyMatch(a -> a.contains("EAN128")));
    }
}
```

- [ ] **Step 2: Ejecutar el test**

Run: `mvn test -Dtest=AmiPedidoRealTest`
Expected: PASS. Si `elFicheroRealTraeTodasLasColumnasQueHacenFalta` falla, la copia de `src/test/resources/ejemplos/` no es la misma que la de `docs/`: sincronizarlas con
`cp "docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx" "src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx"`.

- [ ] **Step 3: Ejecutar la suite completa**

Run: `mvn test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/puntotres/packinglist/service/etiquetas/AmiPedidoRealTest.java
git commit -m "test de EAN de AMI contra el excel de pedido real del cliente"
```

---

### Task 8: Documentar que los JSON de ejemplo son fixtures

**Files:**
- Create: `src/test/resources/ejemplos/README.md`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: nada.
- Produces: nada de código.

- [ ] **Step 1: Escribir el README de los ejemplos**

Crear `src/test/resources/ejemplos/README.md`:

```markdown
# Ejemplos de entrada

## Los `.json` son fixtures, no datos del cliente

`envio-*.json` los mantiene **el usuario** para pruebas visuales rápidas: son
envíos inventados a mano, con referencias, colores, pesos y números de pedido
puestos a ojo. Sirven para ejercitar el código de punta a punta y como fixture
de los tests, y nada más.

**No son autoridad sobre cómo son los datos reales de un cliente.** No deducir
de ellos qué colores tiene una referencia, qué POs usa una destinación ni cómo
se comporta el fichero de un cliente. Para eso están los ficheros de `docs/`.

## Lo que sí es un fichero real

`EAN PUNTOTRES H26.xlsx` es copia literal de
`docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx`, el excel de pedido real de AMI de
la temporada H26. Ese sí describe los datos del cliente, con sus rarezas
(hay 8 filas cuyo EAN128 no cuadra con sus propias columnas). Si se actualiza el
de `docs/`, actualizar también esta copia:

```bash
cp "docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx" \
   "src/test/resources/ejemplos/EAN PUNTOTRES H26.xlsx"
```

`escandallos/` son también copias de escandallos reales del ERP.
```

- [ ] **Step 2: Añadir la nota a `CLAUDE.md`**

En la sección "Reglas del proyecto", añadir al final:

```markdown
- **Los `.json` de `src/test/resources/ejemplos/` son fixtures sintéticos**, no datos de cliente: los mantiene el usuario para pruebas visuales y sus referencias, colores y pedidos están puestos a ojo. No deducir de ellos cómo son los datos reales de un cliente ni citarlos como evidencia; para eso están los ficheros de `docs/`. Los `.xlsx` de `ejemplos/` sí son copias literales de ficheros reales (ver [src/test/resources/ejemplos/README.md](src/test/resources/ejemplos/README.md)).
```

Y en el párrafo de **Etiquetas de caja**, cambiar la descripción de AMI para reflejar los tres códigos:

```markdown
Implementado: AMI (China/Japan/France; hoja por destinación, par de etiquetas A4 por caja, y **tres códigos de barras** por etiqueta: Code 128 del PO, EAN-13 de la columna `EAN13` del excel de pedido y Code 128 de su columna `EAN128`, que se pasa **verbatim** porque en el fichero real hay filas cuyo EAN128 no cuadra con sus propias columnas). Los EAN se resuelven con clave exacta `ARTICLE`+`COLORIS`+`TAILLE`+sufijo de PO, y la talla es la de la **línea líder** de la caja.
```

- [ ] **Step 3: Comprobar que la suite sigue verde**

Run: `mvn test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/resources/ejemplos/README.md CLAUDE.md
git commit -m "documenta que los json de ejemplos son fixtures y no datos de cliente"
```

---

### Task 9: Revisión visual del resultado

Los tests comprueban celdas y número de imágenes, pero que un código de barras quede **bien colocado y legible** solo se ve abriendo el excel.

**Files:**
- Ninguno (verificación manual).

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: nada.

- [ ] **Step 1: Generar los excels de las tres destinaciones**

Run: `mvn test -Dtest='AmiEtiquetas*Test'`

Los tests dejan `target/Etiquetas_AMI_PARIS_F-123.xlsx` y `target/etiquetas-ami-china-sin-barcode.xlsx`.

- [ ] **Step 2: Abrirlos y comparar con el fichero del cliente**

Abrir `target/Etiquetas_AMI_PARIS_F-123.xlsx` junto a `docs/Etiquetas cajas/ETIQUETA CAJA AMI.xlsx` y comprobar en la hoja `AMI FRANCE`:

- los tres códigos están donde están en el fichero del cliente;
- ninguno pisa el texto de la etiqueta;
- el EAN-13 sale con sus dígitos legibles debajo;
- el Code 128 del EAN128 (que es largo, 44 caracteres) cabe en su hueco sin salirse de la etiqueta;
- las dos etiquetas del par llevan los tres códigos;
- cada par sigue cabiendo en un A4 (vista previa de impresión).

- [ ] **Step 3: Informar al usuario**

Contar qué se ha visto y pedirle que lo mire él, que es quien conoce lo que espera AMI. Si algún código queda mal colocado, el arreglo es ajustar el `AnclajeBloque` de esa destinación en `AmiEtiquetaLayout` y su assert en `AmiEtiquetaLayoutTest`.

---

## Self-Review

**Cobertura del spec:**

| Requisito del spec | Task |
|---|---|
| Sustituir la plantilla | 1 |
| `AnclajeBloque` y layout de 15 componentes | 2 |
| Coordenadas medidas de los 6 anclajes nuevos | 2 (constantes) + 5 (dibujo) |
| `oneCellAnchor` vía `AnclajeImagen.fijo` | 2 (helper `anclar`), 5 |
| Réplica en la 2ª etiqueta y por caja | 5 |
| Columnas ausentes no bloquean | 3, 4 |
| Leer `EAN13`, `EAN128`, `Made in` | 4 |
| EAN128 verbatim | 4 (nunca se sustituye por `estructuraEsperada`) |
| Aviso de EAN128 fuera de estructura | 4, 7 |
| Clave exacta de 4 campos | 4, 7 |
| `colorCode` sin cambios | 4 (test `colorQueNoEstaEnElPedidoAvisaYNoDaCodigos`) |
| EAN de la talla de la línea líder | 6 (test `enCinturonMultiTallaElEanEsElDeLaLineaLider`) |
| Filas ocultas se leen | 7 (el fichero real tiene 147 ocultas y los tests las encuentran) |
| Aviso de EAN13 inválido | 4 |
| Empate de filas → aviso | 4 (rama `else` de `buscar`) |
| Fixtures documentados | 8 |
| Revisión visual | 9 |

**Consistencia de tipos:** `AnclajeBloque(int, long, long, long, long)` se usa igual en las Tasks 2, 5 y en `AmiEtiquetaLayoutTest`. `FilaPedido` se declara en la Task 4 con los cinco componentes que consumen las Tasks 6 y 7. `EtiquetaCaja` se amplía a 10 componentes en la Task 5 y la Task 6 la construye con ese orden. `buscar` tiene la misma firma de 4 argumentos en las Tasks 4, 6 y 7. El helper `anclar` se introduce en la Task 2 y se conserva en la Task 5.

**Dependencias entre tareas:** 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9. La Task 4 incluye el arreglo mínimo de la llamada del generador para que el proyecto compile antes de que la Task 6 la desarrolle.
