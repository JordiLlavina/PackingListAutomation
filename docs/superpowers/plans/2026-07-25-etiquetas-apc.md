# Etiquetas de caja y palet de APC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generar el excel de etiquetas de caja (par por caja) y de palet (una por palet) de APC, un fichero por destinación soportada (JAPAN, KOREA, USA, WH CROSSLOG), a partir de las plantillas reales del cliente.

**Architecture:** Espejo del patrón AMI en `service/etiquetas/`: un `ApcEtiquetaLayout` (datos por destinación), un `ApcEtiquetasExcelBuilder` (POI sobre la plantilla) y un `ApcEtiquetasGenerador implements GeneradorEtiquetasCliente`. Se extrae el motor de replicación de bloques (`BloqueModelo`) de AMI a una clase compartida, y `GeneradorEtiquetasCliente.generar(...)` pasa a recibir destino+palets (`EnvioImportado.DestinoImportado`).

**Tech Stack:** Java 17, Spring Boot 3.5, Apache POI (XSSF), JUnit 5 puro (servicios con `new`).

**Spec:** `docs/superpowers/specs/2026-07-25-etiquetas-apc-design.md`

## Global Constraints

- Idioma: javadoc/comentarios/strings de UI en **español**; seguir el naming del paquete `service/etiquetas/` existente.
- **Nunca fallar en silencio, nunca bloquear**: datos resolubles por humanos → avisos en `ResultadoEtiquetas`/`ExcelGenerado.cajasPendientes`, no excepciones. Excepción solo para errores de programación/plantilla (hoja ausente).
- Pesos `Double`, `null` = desconocido → celda en blanco.
- **Order N° y Livraison code (y ASN N° de CROSSLOG) = literal `"NOT FOUND"`** en esta iteración (decisión del usuario).
- Formato de pesos en etiquetas: `String.format(Locale es-ES, "%.2f Kg", peso)` → `"7,60 Kg"`.
- Plantillas en `src/main/resources/client-labels/`: NO editarlas; los layouts dependen de sus coordenadas.
- Tests de builders: reabrir el `.xlsx` generado con POI y comprobar celdas reales.
- Comandos: `mvn test`, `mvn test -Dtest=NombreTest`. Commits pequeños por tarea.

## Hallazgos verificados de las 4 plantillas (fuente de las coordenadas)

Extraídos del XML real de `docs/Etiquetas cajas/ETIQUETA CAJA APC *.xlsx`. **Los nombres de hoja difieren por destinación** (el spec asumía los de JAPAN para todas) y **WH CROSSLOG no tiene "Livrasion code" sino "ASN N°"** (recibe el mismo `NOT FOUND`). USA intercala una fila PROVENANCE que desplaza Reference.

Hoja de cajas — filas **0-based** del valor (columna C = índice 2); la plantilla trae el par ya apilado (etiqueta 2 = misma fila + offset):

| Campo | JAPAN | KOREA | USA | CROSSLOG |
|---|---|---|---|---|
| Order N° | 9 | 11 | 9 | 12 |
| Livraison / ASN | 10 | 12 | 10 | 11 (ASN N°) |
| Reference | 11 | 13 | 12 | 13 |
| Colour | 12 | 14 | 13 | 14 |
| Size | 13 | 15 | 14 | 15 |
| Pieces by size | 14 | 16 | 15 | 16 |
| Colisage | 17 | 19 | 18 | 18 |
| Poids brut du colis | 18 | 20 | 19 | 19 |
| offset 2ª etiqueta | 21 | 22 | 23 | 20 |
| altura bloque (2×offset) | 42 | 44 | 46 | 40 |

Nombres de hoja (¡JAPAN cajas lleva **espacio final**!):

| | hoja cajas | hoja palet |
|---|---|---|
| JAPAN | `Etiquette colis Bolloré ` | `Etiquette Palette Bolloré` |
| KOREA | `Etiquette colis FC Logistique` | `Etiquette Palette FC logistique` |
| USA | `ETIQUETTE COLIS` | `PALET` |
| CROSSLOG | `Etiquette colis Crosslog` | `Etiquette Palette Crosslog` |

Hoja de palet — **uniforme en las 4**: una etiqueta modelo en filas 1–14 (1-based), valores en C13 (nº de cajas, numérico) y C14 (peso). Altura de bloque 14. La fila 1 trae una celda suelta con un contador manual (E1/C1/C1/D1 según plantilla) que hay que limpiar. Cada plantilla lleva un logo como imagen: 2 anclajes en la hoja de cajas (uno por etiqueta del par) y 1 en la de palet; al replicar bloques hay que clonar esos anclajes desplazados (leyéndolos de la propia plantilla, sin hardcodear EMUs).

Convención de pesos APC (igual que `ApcExcelBuilder`): cada línea (CajaData) lleva su peso; peso de la caja física = suma de sus líneas, `null` si falta alguna. Los cinturones APC **no** usan el prefijo `UBL` (referencias `PXBHZ-...`): cinturón ⇔ `talla != null`.

---

### Task 1: Extraer `BloqueEtiquetaModelo` (refactor sin cambio de comportamiento)

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/BloqueEtiquetaModelo.java`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasExcelBuilder.java` (borrar el record interno `BloqueModelo`, usar la clase nueva)

**Interfaces:**
- Produces: `BloqueEtiquetaModelo.capturar(XSSFSheet hoja, int altura)` y `void copiarEn(XSSFSheet hoja, int filaDestino)` — los usan los Tasks 4 y 5.

- [ ] **Step 1: Crear la clase compartida**

Copiar el record `BloqueModelo` (líneas 219–281 de `AmiEtiquetasExcelBuilder`) a un record top-level paquete-privado, renombrado:

```java
package com.puntotres.packinglist.service.etiquetas;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;

/**
 * El bloque de etiquetas modelo de una plantilla: valores, estilos, altos
 * de fila y celdas combinadas de las primeras {@code altura} filas,
 * capturados antes de escribir nada para poder replicarlos por caja o por
 * palet. Compartido por los builders de etiquetas (AMI, APC).
 */
record BloqueEtiquetaModelo(List<FilaModelo> filas, List<CellRangeAddress> merges,
                            int altura) {

    record CeldaModelo(int col, CellStyle estilo, CellType tipo, String texto) {
    }

    record FilaModelo(int fila, float altoPuntos, boolean altoPersonalizado,
                      List<CeldaModelo> celdas) {
    }

    static BloqueEtiquetaModelo capturar(XSSFSheet hoja, int altura) {
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
                    ((XSSFRow) fila).getCTRow().getCustomHeight(), celdas));
        }
        List<CellRangeAddress> merges = new ArrayList<>();
        for (CellRangeAddress merge : hoja.getMergedRegions()) {
            if (merge.getLastRow() < altura) {
                merges.add(merge);
            }
        }
        return new BloqueEtiquetaModelo(filas, merges, altura);
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
```

- [ ] **Step 2: Usarla en el builder AMI**

En `AmiEtiquetasExcelBuilder`: borrar el record interno `BloqueModelo` completo (con sus records anidados) y cambiar la línea

```java
BloqueModelo modelo = BloqueModelo.capturar(hoja, layout.alturaBloque());
```
por
```java
BloqueEtiquetaModelo modelo = BloqueEtiquetaModelo.capturar(hoja, layout.alturaBloque());
```
Quitar los imports que queden sin uso (`CellStyle`, `CellType`, `Row`, `CellRangeAddress` si ya no se usan en el fichero).

- [ ] **Step 3: Verificar que los tests AMI siguen en verde**

Run: `mvn test -Dtest=AmiEtiquetasExcelBuilderTest,AmiEtiquetasGeneradorTest`
Expected: PASS (mismo comportamiento, solo se movió código).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/etiquetas/
git commit -m "extrae BloqueEtiquetaModelo del builder de etiquetas AMI"
```

---

### Task 2: `generar(...)` recibe los palets (`DestinoImportado`)

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/GeneradorEtiquetasCliente.java`
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGenerador.java:70-91`
- Modify: `src/main/java/com/puntotres/packinglist/web/PackingListController.java:409-410`
- Modify: `src/test/java/com/puntotres/packinglist/service/etiquetas/AmiEtiquetasGeneradorTest.java`
- Modify: `src/test/java/com/puntotres/packinglist/service/etiquetas/EtiquetasGenerationServiceTest.java`

**Interfaces:**
- Produces: `ResultadoEtiquetas generar(List<EnvioImportado.DestinoImportado> destinos, DatosEnvio envio, Map<String, byte[]> archivos)` — la implementa el Task 6. `camposRequeridos(List<DestinoData>)` NO cambia.

- [ ] **Step 1: Cambiar la firma en la interfaz**

En `GeneradorEtiquetasCliente` (añadiendo `import com.puntotres.packinglist.service.EnvioImportado;`):

```java
    /**
     * Genera un excel de etiquetas por destinación soportada. Cada destino
     * llega con sus palets (los necesitan las etiquetas de palet; los
     * generadores sin ellas los ignoran). archivos: contenido de cada
     * CampoEtiquetas subido, indexado por su nombre.
     */
    ResultadoEtiquetas generar(List<EnvioImportado.DestinoImportado> destinos, DatosEnvio envio,
                               Map<String, byte[]> archivos) throws IOException;
```

- [ ] **Step 2: Adaptar `AmiEtiquetasGenerador`**

En `generar(...)`: misma lógica, desempaquetando el destino:

```java
    @Override
    public ResultadoEtiquetas generar(List<EnvioImportado.DestinoImportado> destinos,
                                      DatosEnvio envio, Map<String, byte[]> archivos)
            throws IOException {
        byte[] contenidoPedido = archivos.get(CAMPO_PEDIDO.nombre());
        if (contenidoPedido == null) {
            throw new IllegalArgumentException("Falta el excel del pedido de AMI");
        }
        AmiPedidoExcel pedido = AmiPedidoExcel.desdeBytes(contenidoPedido);

        ResultadoEtiquetas resultado = new ResultadoEtiquetas();
        for (EnvioImportado.DestinoImportado importado : destinos) {
            DestinoData destino = importado.getDestino();
            AmiEtiquetaLayout layout =
                    LAYOUT_POR_DESTINO.get(normalizar(destino.getNombreDestino()));
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
```

- [ ] **Step 3: Adaptar el controller**

En `PackingListController.generarEtiquetas(...)` cambiar la llamada:

```java
            ResultadoEtiquetas resultado = generador.generar(
                    envioEnCurso.getImportado().getDestinos(), envioEnCurso.getCabecera(), archivos);
```
(`destinosDelEnvio()` se mantiene: lo siguen usando `camposRequeridos` y la vista.)

- [ ] **Step 4: Adaptar los tests**

En `AmiEtiquetasGeneradorTest` añadir el helper (con `import com.puntotres.packinglist.service.EnvioImportado;`):

```java
    private static EnvioImportado.DestinoImportado importado(DestinoData destino) {
        return new EnvioImportado.DestinoImportado(destino, List.of());
    }
```
y envolver cada `destino(...)` de las ~9 llamadas a `generador.generar(List.of(...))`, p. ej.:

```java
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        importado(destino("PARIS", caja(1, "ULL163.AL0052", "221", null, 50, 5.28, "07665"))),
                        importado(destino("CHINA", caja(1, "ULL163.AL0052", "221", null, 40, 4.10, "07703")))),
                envio(), Map.of("pedido", pedidoEjemplo()));
```
(los argumentos `envio()`/archivos de cada llamada quedan como estén en el test actual; solo cambia el primer parámetro).

En `EtiquetasGenerationServiceTest` actualizar la firma del `GeneradorEtiquetasCliente` anónimo:

```java
            @Override public ResultadoEtiquetas generar(List<EnvioImportado.DestinoImportado> destinos,
                    DatosEnvio envio, Map<String, byte[]> archivos) {
```

- [ ] **Step 5: Suite completa en verde**

Run: `mvn test`
Expected: PASS (cambio mecánico de firma).

- [ ] **Step 6: Commit**

```bash
git add src/main/java src/test/java
git commit -m "GeneradorEtiquetasCliente.generar recibe los destinos con sus palets"
```

---

### Task 3: Plantillas APC en resources + `ApcEtiquetaLayout`

**Files:**
- Create: `src/main/resources/client-labels/apc-etiquetas-japan.xlsx` (y korea, usa, wh-crosslog — copias exactas de `docs/Etiquetas cajas/`)
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetaLayout.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetaLayoutTest.java`

**Interfaces:**
- Produces: `ApcEtiquetaLayout` record con `rutaPlantilla(), hojaCajas(), hojaPalet(), alturaBloque(), offsetSegundaEtiqueta(), filaOrder(), filaLivraison(), filaReferencia(), filaColor(), filaTalla(), filaPiezas(), filaColisage(), filaPeso()`; constantes `COL_VALOR`, `ALTURA_BLOQUE_PALET`, `FILA_PALET_NUM_CAJAS`, `FILA_PALET_PESO`; y `static Optional<ApcEtiquetaLayout> paraDestino(String)`. Lo consumen los Tasks 4, 5 y 6.

- [ ] **Step 1: Copiar las plantillas**

```bash
cd "c:/Users/jordi/Documents/Workspace/Packing List Automation"
cp "docs/Etiquetas cajas/ETIQUETA CAJA APC JAPAN.xlsx"       src/main/resources/client-labels/apc-etiquetas-japan.xlsx
cp "docs/Etiquetas cajas/ETIQUETA CAJA APC KOREA.xlsx"       src/main/resources/client-labels/apc-etiquetas-korea.xlsx
cp "docs/Etiquetas cajas/ETIQUETA CAJA APC USA.xlsx"         src/main/resources/client-labels/apc-etiquetas-usa.xlsx
cp "docs/Etiquetas cajas/ETIQUETA CAJA APC WH CROSSLOG.xlsx" src/main/resources/client-labels/apc-etiquetas-wh-crosslog.xlsx
```

- [ ] **Step 2: Escribir el test (fallará por clase inexistente)**

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ApcEtiquetaLayoutTest {

    @Test
    void resuelveLosNombresDeDestinoConocidosYRechazaElResto() {
        // Acepta tanto la clave de la config de packing ("D. USA", "C-LOG")
        // como el nombre del fichero de plantilla ("USA", "WH CROSSLOG").
        assertSame(ApcEtiquetaLayout.JAPAN, ApcEtiquetaLayout.paraDestino(" japan ").orElseThrow());
        assertSame(ApcEtiquetaLayout.KOREA, ApcEtiquetaLayout.paraDestino("KOREA").orElseThrow());
        assertSame(ApcEtiquetaLayout.USA, ApcEtiquetaLayout.paraDestino("D. USA").orElseThrow());
        assertSame(ApcEtiquetaLayout.USA, ApcEtiquetaLayout.paraDestino("USA").orElseThrow());
        assertSame(ApcEtiquetaLayout.WH_CROSSLOG, ApcEtiquetaLayout.paraDestino("C-LOG").orElseThrow());
        assertSame(ApcEtiquetaLayout.WH_CROSSLOG, ApcEtiquetaLayout.paraDestino("WH CROSSLOG").orElseThrow());
        assertTrue(ApcEtiquetaLayout.paraDestino("IVRY").isEmpty());
        assertTrue(ApcEtiquetaLayout.paraDestino(null).isEmpty());
    }

    @Test
    void cadaPlantillaExisteEnElClasspathYTieneSusDosHojas() throws IOException {
        for (ApcEtiquetaLayout layout : List.of(ApcEtiquetaLayout.JAPAN, ApcEtiquetaLayout.KOREA,
                ApcEtiquetaLayout.USA, ApcEtiquetaLayout.WH_CROSSLOG)) {
            try (InputStream plantilla = getClass().getResourceAsStream(layout.rutaPlantilla())) {
                assertNotNull(plantilla, "Falta la plantilla " + layout.rutaPlantilla());
                try (XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {
                    assertNotNull(libro.getSheet(layout.hojaCajas()),
                            layout.rutaPlantilla() + " sin hoja '" + layout.hojaCajas() + "'");
                    assertNotNull(libro.getSheet(layout.hojaPalet()),
                            layout.rutaPlantilla() + " sin hoja '" + layout.hojaPalet() + "'");
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verificar que falla**

Run: `mvn test -Dtest=ApcEtiquetaLayoutTest`
Expected: FAIL de compilación ("cannot find symbol: ApcEtiquetaLayout").

- [ ] **Step 4: Implementar el layout**

```java
package com.puntotres.packinglist.service.etiquetas;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Coordenadas (0-based de POI) de las dos hojas de la plantilla de una
 * destinación de APC (client-labels/apc-etiquetas-*.xlsx). La hoja de cajas
 * trae UN par de etiquetas modelo apilado en vertical (= una hoja A4): la
 * caja i-ésima se escribe desplazada i*alturaBloque y la segunda etiqueta
 * del par a +offsetSegundaEtiqueta. Los valores van en la columna C.
 *
 * La hoja de palet es uniforme en las 4 plantillas: etiqueta modelo en las
 * filas 0..13, nº de cajas en C13 y peso en C14 (1-based).
 *
 * filaLivraison: en WH CROSSLOG es la fila "ASN N°" (esa plantilla no
 * tiene Livraison); recibe el mismo valor.
 *
 * NO cambiar estas coordenadas sin revisar la plantilla, y viceversa.
 */
record ApcEtiquetaLayout(
        String rutaPlantilla, String hojaCajas, String hojaPalet,
        int alturaBloque, int offsetSegundaEtiqueta,
        int filaOrder, int filaLivraison, int filaReferencia, int filaColor,
        int filaTalla, int filaPiezas, int filaColisage, int filaPeso) {

    public static final int COL_VALOR = 2;          // columna C
    public static final int ALTURA_BLOQUE_PALET = 14;
    public static final int FILA_PALET_NUM_CAJAS = 12; // C13
    public static final int FILA_PALET_PESO = 13;      // C14

    public static final ApcEtiquetaLayout JAPAN = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-japan.xlsx",
            "Etiquette colis Bolloré ", "Etiquette Palette Bolloré",
            42, 21, 9, 10, 11, 12, 13, 14, 17, 18);

    public static final ApcEtiquetaLayout KOREA = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-korea.xlsx",
            "Etiquette colis FC Logistique", "Etiquette Palette FC logistique",
            44, 22, 11, 12, 13, 14, 15, 16, 19, 20);

    public static final ApcEtiquetaLayout USA = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-usa.xlsx",
            "ETIQUETTE COLIS", "PALET",
            46, 23, 9, 10, 12, 13, 14, 15, 18, 19);

    public static final ApcEtiquetaLayout WH_CROSSLOG = new ApcEtiquetaLayout(
            "/client-labels/apc-etiquetas-wh-crosslog.xlsx",
            "Etiquette colis Crosslog", "Etiquette Palette Crosslog",
            40, 20, 12, 11, 13, 14, 15, 16, 18, 19);

    /**
     * Se aceptan la clave del catálogo de packing (D. USA, C-LOG) y el
     * nombre de la plantilla del cliente (USA, WH CROSSLOG).
     */
    private static final Map<String, ApcEtiquetaLayout> POR_DESTINO = Map.of(
            "JAPAN", JAPAN,
            "KOREA", KOREA,
            "D. USA", USA, "USA", USA,
            "C-LOG", WH_CROSSLOG, "WH CROSSLOG", WH_CROSSLOG);

    public static Optional<ApcEtiquetaLayout> paraDestino(String nombreDestino) {
        if (nombreDestino == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                POR_DESTINO.get(nombreDestino.trim().toUpperCase(Locale.ROOT)));
    }
}
```

- [ ] **Step 5: Verificar que pasa**

Run: `mvn test -Dtest=ApcEtiquetaLayoutTest`
Expected: PASS. (Si `getSheet("Etiquette colis Bolloré ")` fallara, revisar el espacio final del nombre.)

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/client-labels src/main/java src/test/java
git commit -m "plantillas de etiquetas APC y layout por destinación"
```

---

### Task 4: `ApcEtiquetasExcelBuilder` — hoja de cajas

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasExcelBuilder.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasExcelBuilderTest.java`

**Interfaces:**
- Consumes: `ApcEtiquetaLayout` (Task 3), `BloqueEtiquetaModelo` (Task 1).
- Produces: `@Service ApcEtiquetasExcelBuilder` con
  `public record EtiquetaCajaApc(String orderNumber, String livraisonCode, String referencia, String colour, String size, String piecesBySize, String colisage, String poidsBrut)`,
  `public record EtiquetaPaletApc(int numeroCajas, String poidsBrut)` y
  `public byte[] generar(ApcEtiquetaLayout layout, List<EtiquetaCajaApc> cajas, List<EtiquetaPaletApc> palets) throws IOException`.
  En esta tarea los `palets` se aceptan pero aún no se escriben (Task 5); la hoja de palet se conserva tal cual.

- [ ] **Step 1: Escribir el test de la hoja de cajas**

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaCajaApc;
import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaPaletApc;

class ApcEtiquetasExcelBuilderTest {

    private final ApcEtiquetasExcelBuilder builder = new ApcEtiquetasExcelBuilder();

    private static EtiquetaCajaApc etiqueta(String colisage, String poids) {
        return new EtiquetaCajaApc("NOT FOUND", "NOT FOUND", "PXCBC-F67008",
                "LZZ-NOIR", "U", "11", colisage, poids);
    }

    @Test
    void escribeElParDeEtiquetasYConservaLasDosHojas() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN,
                List.of(etiqueta("1 / 1", "7,60 Kg")), List.of());
        try (XSSFWorkbook libro = abrir(excel)) {
            assertEquals(2, libro.getNumberOfSheets());
            XSSFSheet cajas = libro.getSheet("Etiquette colis Bolloré ");
            assertNotNull(cajas);
            assertNotNull(libro.getSheet("Etiquette Palette Bolloré"));
            // Etiqueta 1 (los códigos pisan los valores de ejemplo).
            assertEquals("NOT FOUND", texto(cajas, 9, 2));
            assertEquals("NOT FOUND", texto(cajas, 10, 2));
            assertEquals("PXCBC-F67008", texto(cajas, 11, 2));
            assertEquals("LZZ-NOIR", texto(cajas, 12, 2));
            assertEquals("U", texto(cajas, 13, 2));
            assertEquals("11", texto(cajas, 14, 2));
            assertEquals("1 / 1", texto(cajas, 17, 2));
            assertEquals("7,60 Kg", texto(cajas, 18, 2));
            // Etiqueta 2 = mismas celdas + offset 21.
            assertEquals("NOT FOUND", texto(cajas, 9 + 21, 2));
            assertEquals("PXCBC-F67008", texto(cajas, 11 + 21, 2));
            assertEquals("7,60 Kg", texto(cajas, 18 + 21, 2));
            // Los estáticos de la plantilla no se tocan (DESTINATION = TOKYO).
            assertEquals("TOKYO", texto(cajas, 8, 2));
        }
    }

    @Test
    void replicaElBloquePorCajaConSaltoDePaginaYPesoEnBlancoSiFalta() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN, List.of(
                etiqueta("1 / 2", "7,60 Kg"), etiqueta("2 / 2", null)), List.of());
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet cajas = libro.getSheet("Etiquette colis Bolloré ");
            // Caja 2 = bloque desplazado 42 filas, con sus estáticos copiados.
            assertEquals("NOT FOUND", texto(cajas, 9 + 42, 2));
            assertEquals("2 / 2", texto(cajas, 17 + 42, 2));
            assertEquals("", texto(cajas, 18 + 42, 2));
            assertEquals("TOKYO", texto(cajas, 8 + 42, 2));
            // Cada par en su A4.
            assertTrue(cajas.getRowBreaks().length >= 1);
            assertEquals(41, cajas.getRowBreaks()[0]);
            // El bloque copiado conserva los altos de fila de la plantilla.
            assertEquals(cajas.getRow(6).getHeightInPoints(),
                    cajas.getRow(6 + 42).getHeightInPoints(), 0.01);
        }
    }

    @Test
    void replicaElLogoDeLaPlantillaEnCadaBloque() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN, List.of(
                etiqueta("1 / 2", "7,60 Kg"), etiqueta("2 / 2", "8,20 Kg")), List.of());
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet cajas = libro.getSheet("Etiquette colis Bolloré ");
            // La plantilla trae 2 logos (uno por etiqueta del par): con 2
            // cajas debe haber 4.
            assertEquals(4, cajas.getDrawingPatriarch().getShapes().size());
        }
    }

    static XSSFWorkbook abrir(byte[] contenido) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(contenido));
    }

    static String texto(XSSFSheet hoja, int fila, int col) {
        if (hoja.getRow(fila) == null || hoja.getRow(fila).getCell(col) == null) {
            return "";
        }
        return hoja.getRow(fila).getCell(col).toString().trim();
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `mvn test -Dtest=ApcEtiquetasExcelBuilderTest`
Expected: FAIL de compilación ("cannot find symbol: ApcEtiquetasExcelBuilder").

- [ ] **Step 3: Implementar el builder (hoja de cajas)**

```java
package com.puntotres.packinglist.service.etiquetas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Escribe el excel de etiquetas de UNA destinación de APC a partir de su
 * plantilla real (client-labels/apc-etiquetas-*.xlsx). Se conservan las DOS
 * hojas del libro: en la de cajas se replica el par de etiquetas modelo por
 * caja física y en la de palet la etiqueta modelo por palet, cada bloque
 * con su salto de página (un A4 por par de caja y por palet). El logo de la
 * plantilla se clona en cada bloque copiado leyendo sus anclajes reales.
 */
@Service
public class ApcEtiquetasExcelBuilder {

    /** Datos ya formateados de la etiqueta de una caja. null = en blanco. */
    public record EtiquetaCajaApc(String orderNumber, String livraisonCode, String referencia,
                                  String colour, String size, String piecesBySize,
                                  String colisage, String poidsBrut) {
    }

    /** Datos de la etiqueta de un palet. poidsBrut null = en blanco. */
    public record EtiquetaPaletApc(int numeroCajas, String poidsBrut) {
    }

    public byte[] generar(ApcEtiquetaLayout layout, List<EtiquetaCajaApc> cajas,
                          List<EtiquetaPaletApc> palets) throws IOException {
        try (InputStream plantilla = getClass().getResourceAsStream(layout.rutaPlantilla());
             XSSFWorkbook libro = new XSSFWorkbook(plantilla)) {
            escribirHojaCajas(hoja(libro, layout.hojaCajas(), layout), layout, cajas);
            // Hoja de palet: Task 5.
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static XSSFSheet hoja(XSSFWorkbook libro, String nombre, ApcEtiquetaLayout layout) {
        XSSFSheet hoja = libro.getSheet(nombre);
        if (hoja == null) {
            throw new IllegalStateException("La plantilla " + layout.rutaPlantilla()
                    + " no tiene la hoja '" + nombre + "': revisar client-labels/");
        }
        return hoja;
    }

    private static void escribirHojaCajas(XSSFSheet hoja, ApcEtiquetaLayout layout,
                                          List<EtiquetaCajaApc> cajas) {
        if (cajas.isEmpty()) {
            return;
        }
        BloqueEtiquetaModelo modelo = BloqueEtiquetaModelo.capturar(hoja, layout.alturaBloque());
        for (int i = 1; i < cajas.size(); i++) {
            modelo.copiarEn(hoja, i * layout.alturaBloque());
        }
        replicarImagenes(hoja, layout.alturaBloque(), cajas.size());
        for (int i = 0; i < cajas.size(); i++) {
            int base = i * layout.alturaBloque();
            escribirEtiquetaCaja(hoja, layout, base, cajas.get(i));
            escribirEtiquetaCaja(hoja, layout, base + layout.offsetSegundaEtiqueta(), cajas.get(i));
            if (i < cajas.size() - 1) {
                hoja.setRowBreak(base + layout.alturaBloque() - 1);
            }
        }
    }

    private static void escribirEtiquetaCaja(XSSFSheet hoja, ApcEtiquetaLayout layout,
                                             int base, EtiquetaCajaApc etiqueta) {
        escribir(hoja, base + layout.filaOrder(), etiqueta.orderNumber());
        escribir(hoja, base + layout.filaLivraison(), etiqueta.livraisonCode());
        escribir(hoja, base + layout.filaReferencia(), etiqueta.referencia());
        escribir(hoja, base + layout.filaColor(), etiqueta.colour());
        escribir(hoja, base + layout.filaTalla(), etiqueta.size());
        escribir(hoja, base + layout.filaPiezas(), etiqueta.piecesBySize());
        escribir(hoja, base + layout.filaColisage(), etiqueta.colisage());
        escribir(hoja, base + layout.filaPeso(), etiqueta.poidsBrut());
    }

    private static void escribir(XSSFSheet hoja, int fila, String valor) {
        XSSFRow f = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell celda = f.getCell(ApcEtiquetaLayout.COL_VALOR) != null
                ? f.getCell(ApcEtiquetaLayout.COL_VALOR)
                : f.createCell(ApcEtiquetaLayout.COL_VALOR);
        if (valor == null || valor.isBlank()) {
            celda.setBlank();
        } else {
            celda.setCellValue(valor);
        }
    }

    /**
     * Clona las imágenes de la plantilla (el logo del cliente) en cada
     * bloque copiado, desplazando sus anclajes reales i*alturaBloque filas:
     * así no hay que hardcodear EMUs por plantilla como en AMI.
     */
    private static void replicarImagenes(XSSFSheet hoja, int alturaBloque, int numBloques) {
        XSSFDrawing dibujo = hoja.getDrawingPatriarch();
        if (dibujo == null || numBloques <= 1) {
            return;
        }
        List<XSSFPicture> originales = new ArrayList<>();
        for (XSSFShape forma : dibujo.getShapes()) {
            if (forma instanceof XSSFPicture imagen) {
                originales.add(imagen);
            }
        }
        for (XSSFPicture imagen : originales) {
            XSSFClientAnchor origen = imagen.getClientAnchor();
            int indice = hoja.getWorkbook().addPicture(imagen.getPictureData().getData(),
                    imagen.getPictureData().getPictureType());
            for (int i = 1; i < numBloques; i++) {
                XSSFClientAnchor ancla = new XSSFClientAnchor(
                        origen.getDx1(), origen.getDy1(), origen.getDx2(), origen.getDy2(),
                        origen.getCol1(), origen.getRow1() + i * alturaBloque,
                        origen.getCol2(), origen.getRow2() + i * alturaBloque);
                ancla.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
                dibujo.createPicture(ancla, indice);
            }
        }
    }
}
```

- [ ] **Step 4: Verificar que pasa**

Run: `mvn test -Dtest=ApcEtiquetasExcelBuilderTest`
Expected: PASS. Si el test del logo fallara en el recuento, volcar `dibujo.getShapes()` para ver qué formas trae la plantilla y ajustar el recuento esperado (JAPAN trae 2 anclajes en la hoja de cajas).

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/test/java
git commit -m "builder de etiquetas de caja de APC"
```

---

### Task 5: `ApcEtiquetasExcelBuilder` — hoja de palet

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasExcelBuilder.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasExcelBuilderTest.java` (añadir tests)

**Interfaces:**
- Consumes/Produces: la firma `generar(layout, cajas, palets)` del Task 4 ya acepta los palets; esta tarea los escribe.

- [ ] **Step 1: Añadir los tests de la hoja de palet**

```java
    @Test
    void generaUnaEtiquetaDePaletPorPaletConSaltoDePagina() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN,
                List.of(etiqueta("1 / 1", "7,60 Kg")),
                List.of(new EtiquetaPaletApc(9, "64,58 Kg"), new EtiquetaPaletApc(3, null)));
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet palet = libro.getSheet("Etiquette Palette Bolloré");
            // Palet 1: nº de cajas numérico en C13 y peso en C14.
            assertEquals(9, palet.getRow(12).getCell(2).getNumericCellValue(), 0.001);
            assertEquals("64,58 Kg", texto(palet, 13, 2));
            // Palet 2 = bloque desplazado 14 filas; peso null en blanco.
            assertEquals(3, palet.getRow(12 + 14).getCell(2).getNumericCellValue(), 0.001);
            assertEquals("", texto(palet, 13 + 14, 2));
            // Estáticos copiados y salto de página entre palets.
            assertEquals("TOKYO", texto(palet, 9 + 14, 2));
            assertTrue(palet.getRowBreaks().length >= 1);
            assertEquals(13, palet.getRowBreaks()[0]);
            // La celda suelta del contador manual de la fila 1 se limpia
            // (en la plantilla de JAPAN es E1).
            assertEquals("", texto(palet, 0, 4));
        }
    }

    @Test
    void sinPaletsLaHojaDePaletQuedaConLosValoresEnBlanco() throws IOException {
        byte[] excel = builder.generar(ApcEtiquetaLayout.JAPAN,
                List.of(etiqueta("1 / 1", "7,60 Kg")), List.of());
        try (XSSFWorkbook libro = abrir(excel)) {
            XSSFSheet palet = libro.getSheet("Etiquette Palette Bolloré");
            assertEquals("", texto(palet, 12, 2));
            assertEquals("", texto(palet, 13, 2));
        }
        // Copia para inspección manual, como hace el e2e de packing lists.
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
        java.nio.file.Files.write(
                java.nio.file.Path.of("target", "etiquetas-apc-japan.xlsx"), excel);
    }
```

Nota sobre `texto(palet, 9 + 14, 2)`: en la hoja de palet de JAPAN la celda C10 (0-based fila 9) es el DESTINATION "TOKYO" — ver el volcado del plan (§ Hallazgos).

- [ ] **Step 2: Verificar que fallan**

Run: `mvn test -Dtest=ApcEtiquetasExcelBuilderTest`
Expected: FAIL — los dos tests nuevos (la hoja de palet aún conserva los valores de ejemplo `9` / `64,58 Kg` sin desplazar, y la celda del contador sin limpiar).

- [ ] **Step 3: Implementar la escritura de la hoja de palet**

En `generar(...)` sustituir el comentario `// Hoja de palet: Task 5.` por:

```java
            escribirHojaPalets(hoja(libro, layout.hojaPalet(), layout), palets);
```

y añadir al final de la clase:

```java
    private static void escribirHojaPalets(XSSFSheet hoja, List<EtiquetaPaletApc> palets) {
        limpiarContadorManual(hoja);
        if (palets.isEmpty()) {
            // Sin palets no se sabe qué imprimir: la etiqueta modelo queda
            // con los valores en blanco (el generador avisa).
            escribir(hoja, ApcEtiquetaLayout.FILA_PALET_NUM_CAJAS, null);
            escribir(hoja, ApcEtiquetaLayout.FILA_PALET_PESO, null);
            return;
        }
        BloqueEtiquetaModelo modelo =
                BloqueEtiquetaModelo.capturar(hoja, ApcEtiquetaLayout.ALTURA_BLOQUE_PALET);
        for (int i = 1; i < palets.size(); i++) {
            modelo.copiarEn(hoja, i * ApcEtiquetaLayout.ALTURA_BLOQUE_PALET);
        }
        replicarImagenes(hoja, ApcEtiquetaLayout.ALTURA_BLOQUE_PALET, palets.size());
        for (int i = 0; i < palets.size(); i++) {
            int base = i * ApcEtiquetaLayout.ALTURA_BLOQUE_PALET;
            escribirNumero(hoja, base + ApcEtiquetaLayout.FILA_PALET_NUM_CAJAS,
                    palets.get(i).numeroCajas());
            escribir(hoja, base + ApcEtiquetaLayout.FILA_PALET_PESO,
                    palets.get(i).poidsBrut());
            if (i < palets.size() - 1) {
                hoja.setRowBreak(base + ApcEtiquetaLayout.ALTURA_BLOQUE_PALET - 1);
            }
        }
    }

    /**
     * La fila 1 de la hoja de palet trae una celda suelta con un contador
     * apuntado a mano (E1/C1/D1 según plantilla) que no debe replicarse.
     */
    private static void limpiarContadorManual(XSSFSheet hoja) {
        if (hoja.getRow(0) != null) {
            hoja.getRow(0).forEach(Cell::setBlank);
        }
    }

    private static void escribirNumero(XSSFSheet hoja, int fila, int valor) {
        XSSFRow f = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell celda = f.getCell(ApcEtiquetaLayout.COL_VALOR) != null
                ? f.getCell(ApcEtiquetaLayout.COL_VALOR)
                : f.createCell(ApcEtiquetaLayout.COL_VALOR);
        celda.setCellValue(valor);
    }
```

- [ ] **Step 4: Verificar que pasa toda la clase de test**

Run: `mvn test -Dtest=ApcEtiquetasExcelBuilderTest`
Expected: PASS. Abrir `target/etiquetas-apc-japan.xlsx` a mano si se quiere inspeccionar.

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/test/java
git commit -m "etiquetas de palet de APC en el builder"
```

---

### Task 6: `ApcEtiquetasGenerador`

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasGenerador.java`
- Test: `src/test/java/com/puntotres/packinglist/service/etiquetas/ApcEtiquetasGeneradorTest.java`

**Interfaces:**
- Consumes: `GeneradorEtiquetasCliente` con la firma del Task 2, `ApcEtiquetasExcelBuilder` (Tasks 4–5), `ApcEtiquetaLayout.paraDestino` (Task 3).
- Produces: bean Spring `ApcEtiquetasGenerador` con `claveCliente() == "APC"` — lo recoge automáticamente `EtiquetasGenerationService` (inyección por lista), no hay que registrarlo en ningún sitio.

- [ ] **Step 1: Escribir el test**

```java
package com.puntotres.packinglist.service.etiquetas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puntotres.packinglist.model.CajaData;
import com.puntotres.packinglist.model.DatosEnvio;
import com.puntotres.packinglist.model.DestinoData;
import com.puntotres.packinglist.model.EnvioInput;
import com.puntotres.packinglist.model.PaletData;
import com.puntotres.packinglist.service.EnvioImportService;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.PaletAssignmentService;

class ApcEtiquetasGeneradorTest {

    private final ApcEtiquetasGenerador generador =
            new ApcEtiquetasGenerador(new ApcEtiquetasExcelBuilder());

    // --- helpers ---

    private static DatosEnvio envio() {
        DatosEnvio envio = new DatosEnvio();
        envio.setTemporada("E25");
        envio.setNumeroFactura("26071");
        envio.setClaveCliente("APC");
        return envio;
    }

    private static CajaData caja(int numero, String referencia, String color, String talla,
                                 int cantidad, Double pesoBruto, Integer palet) {
        CajaData caja = new CajaData(referencia, color, talla, cantidad, null, pesoBruto);
        caja.setNumeroCaja(numero);
        caja.setNumeroPalet(palet);
        return caja;
    }

    private static PaletData palet(int numero, int inicio, int fin, Double tara) {
        PaletData palet = new PaletData();
        palet.setNumeroPalet(numero);
        palet.setCajaInicio(inicio);
        palet.setCajaFin(fin);
        palet.setTara(tara);
        return palet;
    }

    private static EnvioImportado.DestinoImportado destino(String nombre,
            List<PaletData> palets, CajaData... cajas) {
        DestinoData destino = new DestinoData();
        destino.setNombreDestino(nombre);
        destino.setCajas(List.of(cajas));
        return new EnvioImportado.DestinoImportado(destino, palets);
    }

    private static XSSFSheet hojaCajas(byte[] excel) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(excel))
                .getSheet(ApcEtiquetaLayout.JAPAN.hojaCajas());
    }

    private static String texto(XSSFSheet hoja, int fila, int col) {
        if (hoja.getRow(fila) == null || hoja.getRow(fila).getCell(col) == null) {
            return "";
        }
        return hoja.getRow(fila).getCell(col).toString().trim();
    }

    // --- tests ---

    @Test
    void generaUnExcelPorDestinacionSoportadaYAvisaDeLasNoSoportadas() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1)),
                        destino("IVRY", List.of(),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, null))),
                envio(), Map.of());
        assertEquals(1, resultado.getExcels().size());
        assertEquals("Etiquetas_APC_JAPAN_26071.xlsx",
                resultado.getExcels().get(0).getNombreFichero());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("IVRY")));
    }

    @Test
    void laEtiquetaDeCajaLlevaLosDatosDelJsonYNotFoundEnLosCodigos() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("NOT FOUND", texto(hoja, 9, 2));   // Order N°
        assertEquals("NOT FOUND", texto(hoja, 10, 2));  // Livraison
        assertEquals("PXCBC-F67008", texto(hoja, 11, 2));
        assertEquals("LZZ-NOIR", texto(hoja, 12, 2));
        assertEquals("U", texto(hoja, 13, 2));          // bolso: sin talla
        assertEquals("11", texto(hoja, 14, 2));
        assertEquals("1 / 1", texto(hoja, 17, 2));
        assertEquals("7,60 Kg", texto(hoja, 18, 2));
    }

    @Test
    void losCinturonesAgrupanUnidadesPorTalla() throws IOException {
        // Caso real de envio-apc.json: caja 3 con tallas 85 (7u), 90 (8u) y
        // 85 (5u de otro pedido/canal) de la misma referencia y color.
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 3, 3, null)),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "85", 7, 2.0, 1),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "90", 8, 2.5, 1),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "85", 5, 1.5, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("85-90", texto(hoja, 13, 2));
        assertEquals("12-85,8-90", texto(hoja, 14, 2));
        // El peso es de la caja física entera: 2.0 + 2.5 + 1.5.
        assertEquals("6,00 Kg", texto(hoja, 18, 2));
        assertEquals("1 / 1", texto(hoja, 17, 2));
    }

    @Test
    void unaSolaTallaEscribeLaCantidadASecas() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXBHZ-H65077", "LZZ-NOIR", "85", 7, 2.0, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("85", texto(hoja, 13, 2));
        assertEquals("7", texto(hoja, 14, 2));
    }

    @Test
    void cajaSinPesoSaleEnBlancoYQuedaPendiente() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, null, 1))),
                envio(), Map.of());
        XSSFSheet hoja = hojaCajas(resultado.getExcels().get(0).getContenido());
        assertEquals("", texto(hoja, 18, 2));
        assertEquals(1, resultado.getExcels().get(0).getCajasPendientes().size());
    }

    @Test
    void laEtiquetaDePaletSumaLasCajasYSuTara() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN",
                                List.of(palet(1, 1, 2, 8.04), palet(2, 3, 3, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, 1),
                                caja(2, "PXCBC-F67008", "LZZ-NOIR", null, 11, 8.2, 1),
                                caja(3, "PXBHZ-H65077", "LZZ-NOIR", "85", 7, 2.0, 2))),
                envio(), Map.of());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            XSSFSheet palet = libro.getSheet(ApcEtiquetaLayout.JAPAN.hojaPalet());
            // Palet 1: 2 cajas, 7.6 + 8.2 + 8.04 de tara = 23.84.
            assertEquals(2, palet.getRow(12).getCell(2).getNumericCellValue(), 0.001);
            assertEquals("23,84 Kg", texto(palet, 13, 2));
            // Palet 2: 1 caja, 2.0 + 10 de tara por defecto = 12.00.
            assertEquals(1, palet.getRow(12 + 14).getCell(2).getNumericCellValue(), 0.001);
            assertEquals("12,00 Kg", texto(palet, 13 + 14, 2));
        }
    }

    @Test
    void paletConCajasSinPesoAvisaYVaEnBlanco() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(palet(1, 1, 1, null)),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, null, 1))),
                envio(), Map.of());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                resultado.getExcels().get(0).getContenido()))) {
            XSSFSheet palet = libro.getSheet(ApcEtiquetaLayout.JAPAN.hojaPalet());
            assertEquals("", texto(palet, 13, 2));
        }
        assertTrue(resultado.getAvisos().stream()
                .anyMatch(a -> a.contains("Palet 1") && a.contains("sin peso")));
    }

    @Test
    void avisaDeCajasSinPaletYDeDestinacionSinPalets() throws IOException {
        ResultadoEtiquetas resultado = generador.generar(List.of(
                        destino("JAPAN", List.of(),
                                caja(1, "PXCBC-F67008", "LZZ-NOIR", null, 11, 7.6, null))),
                envio(), Map.of());
        assertEquals(1, resultado.getExcels().size());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("sin palet")));
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("sin palets")));
    }

    @Test
    void elEnvioDeEjemploCompletoSoloAvisaDeIvry() throws IOException {
        // envio-apc.json solo trae IVRY (sin plantilla de etiquetas): no se
        // genera ningún excel pero tampoco se lanza nada.
        EnvioInput envioInput;
        try (InputStream json = getClass().getResourceAsStream("/ejemplos/envio-apc.json")) {
            envioInput = new ObjectMapper().readValue(json, EnvioInput.class);
        }
        EnvioImportado importado = new EnvioImportService().importar(envioInput);
        PaletAssignmentService asignador = new PaletAssignmentService();
        for (EnvioImportado.DestinoImportado destino : importado.getDestinos()) {
            asignador.asignar(destino.getDestino(), destino.getPalets());
        }
        ResultadoEtiquetas resultado =
                generador.generar(importado.getDestinos(), envio(), Map.of());
        assertEquals(0, resultado.getExcels().size());
        assertTrue(resultado.getAvisos().stream().anyMatch(a -> a.contains("IVRY")));
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `mvn test -Dtest=ApcEtiquetasGeneradorTest`
Expected: FAIL de compilación ("cannot find symbol: ApcEtiquetasGenerador").

- [ ] **Step 3: Implementar el generador**

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
import com.puntotres.packinglist.model.PaletData;
import com.puntotres.packinglist.service.EnvioImportado;
import com.puntotres.packinglist.service.ExcelGenerado;
import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaCajaApc;
import com.puntotres.packinglist.service.etiquetas.ApcEtiquetasExcelBuilder.EtiquetaPaletApc;

/**
 * Etiquetas de caja y palet de APC: cuatro destinaciones con plantilla
 * propia (JAPAN, KOREA, USA, WH CROSSLOG); ver ApcEtiquetaLayout. No pide
 * archivos al usuario: los datos estáticos van horneados en cada plantilla
 * y el Order N° y el Livraison code salen como "NOT FOUND" hasta que se
 * implemente su búsqueda (iteración futura).
 *
 * Una caja física = un numeroCaja; los cinturones (línea con talla, APC no
 * usa el prefijo UBL) agrupan unidades por talla en SIZE/PIECES. El peso de
 * palet reutiliza la convención del packing list de APC: suma de los pesos
 * de sus cajas más la tara del palet (10 kg si el JSON no la trae).
 */
@Service
public class ApcEtiquetasGenerador implements GeneradorEtiquetasCliente {

    private static final String NO_DISPONIBLE = "NOT FOUND";
    private static final double TARA_PALET_KG_DEFECTO = 10.0;
    private static final Locale ESPANOL = Locale.forLanguageTag("es-ES");

    private final ApcEtiquetasExcelBuilder builder;

    public ApcEtiquetasGenerador(ApcEtiquetasExcelBuilder builder) {
        this.builder = builder;
    }

    @Override
    public String claveCliente() {
        return "APC";
    }

    @Override
    public boolean soportaDestino(String nombreDestino) {
        return ApcEtiquetaLayout.paraDestino(nombreDestino).isPresent();
    }

    @Override
    public List<CampoEtiquetas> camposRequeridos(List<DestinoData> destinos) {
        return List.of();
    }

    @Override
    public ResultadoEtiquetas generar(List<EnvioImportado.DestinoImportado> destinos,
                                      DatosEnvio envio, Map<String, byte[]> archivos)
            throws IOException {
        ResultadoEtiquetas resultado = new ResultadoEtiquetas();
        for (EnvioImportado.DestinoImportado importado : destinos) {
            DestinoData destino = importado.getDestino();
            Optional<ApcEtiquetaLayout> layout =
                    ApcEtiquetaLayout.paraDestino(destino.getNombreDestino());
            if (layout.isEmpty()) {
                resultado.getAvisos().add("Destinación '" + destino.getNombreDestino()
                        + "' sin etiquetas de APC implementadas: se omite");
                continue;
            }
            resultado.getExcels().add(generarDestino(destino, importado.getPalets(),
                    layout.get(), envio, resultado.getAvisos()));
        }
        return resultado;
    }

    private ExcelGenerado generarDestino(DestinoData destino, List<PaletData> palets,
                                         ApcEtiquetaLayout layout, DatosEnvio envio,
                                         List<String> avisos) throws IOException {
        // Una caja física por numeroCaja, en orden ascendente.
        Map<Integer, List<CajaData>> porNumero = new LinkedHashMap<>();
        destino.getCajas().stream()
                .sorted(Comparator.comparingInt(CajaData::getNumeroCaja))
                .forEach(caja -> porNumero
                        .computeIfAbsent(caja.getNumeroCaja(), n -> new ArrayList<>())
                        .add(caja));

        List<EtiquetaCajaApc> etiquetas = new ArrayList<>();
        List<CajaData> cajasPendientes = new ArrayList<>();
        int posicion = 0;
        int total = porNumero.size();
        for (List<CajaData> lineas : porNumero.values()) {
            posicion++;
            etiquetas.add(etiquetaDe(lineas, posicion, total,
                    destino.getNombreDestino(), avisos, cajasPendientes));
        }

        List<EtiquetaPaletApc> etiquetasPalet =
                etiquetasDePalet(destino, palets, avisos);

        if (destino.getCajas().stream().anyMatch(caja -> caja.getNumeroPalet() == null)) {
            avisos.add("Destinación " + destino.getNombreDestino()
                    + ": hay cajas sin palet asignado, no salen en ninguna etiqueta de palet");
        }

        String nombreFichero = ("Etiquetas_APC_" + destino.getNombreDestino() + "_"
                + envio.getNumeroFactura() + ".xlsx").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        byte[] contenido = builder.generar(layout, etiquetas, etiquetasPalet);
        return new ExcelGenerado(destino.getNombreDestino(), nombreFichero,
                contenido, cajasPendientes);
    }

    private EtiquetaCajaApc etiquetaDe(List<CajaData> lineas, int posicion, int total,
                                       String nombreDestino, List<String> avisos,
                                       List<CajaData> cajasPendientes) {
        CajaData lider = lineas.get(0);

        // Caja mixta de verdad (varias referencias o colores): se etiqueta
        // con la primera y se avisa, igual que en AMI.
        Set<String> refsColores = new LinkedHashSet<>();
        for (CajaData linea : lineas) {
            refsColores.add(claveRefColor(linea));
        }
        if (refsColores.size() > 1) {
            avisos.add("La caja " + lider.getNumeroCaja() + " de " + nombreDestino
                    + " mezcla varias referencias/colores: la etiqueta lleva "
                    + lider.getReferencia() + " " + lider.getCodigoColor());
        }
        List<CajaData> propias = lineas.stream()
                .filter(linea -> claveRefColor(lider).equals(claveRefColor(linea)))
                .toList();

        // Cinturón = línea con talla (APC no usa el prefijo UBL). Las
        // unidades se agrupan por talla, tallas en orden numérico.
        Map<String, Integer> unidadesPorTalla = new LinkedHashMap<>();
        propias.stream()
                .filter(linea -> linea.getTalla() != null)
                .sorted(Comparator.comparingInt(ApcEtiquetasGenerador::tallaNumerica))
                .forEach(linea -> unidadesPorTalla.merge(
                        linea.getTalla(), linea.getCantidad(), Integer::sum));

        String size;
        String piezas;
        if (unidadesPorTalla.isEmpty()) {
            size = "U";
            piezas = String.valueOf(propias.stream().mapToInt(CajaData::getCantidad).sum());
        } else if (unidadesPorTalla.size() == 1) {
            var unica = unidadesPorTalla.entrySet().iterator().next();
            size = unica.getKey();
            piezas = String.valueOf(unica.getValue());
        } else {
            size = String.join("-", unidadesPorTalla.keySet());
            piezas = String.join(",", unidadesPorTalla.entrySet().stream()
                    .map(e -> e.getValue() + "-" + e.getKey()).toList());
        }

        // El peso es de la caja física ENTERA: suma de todas sus líneas
        // (convención APC: cada línea lleva su peso, ver ApcExcelBuilder).
        Double peso = pesoDeLaCaja(lineas);
        if (peso == null) {
            cajasPendientes.add(lider);
        }
        return new EtiquetaCajaApc(NO_DISPONIBLE, NO_DISPONIBLE, lider.getReferencia(),
                lider.getCodigoColor(), size, piezas, posicion + " / " + total, kg(peso));
    }

    private List<EtiquetaPaletApc> etiquetasDePalet(DestinoData destino,
                                                    List<PaletData> palets,
                                                    List<String> avisos) {
        if (palets.isEmpty()) {
            avisos.add("Destinación " + destino.getNombreDestino()
                    + " sin palets: la hoja de etiquetas de palet sale en blanco");
            return List.of();
        }
        List<EtiquetaPaletApc> etiquetas = new ArrayList<>();
        for (PaletData palet : palets.stream()
                .sorted(Comparator.comparingInt(PaletData::getNumeroPalet)).toList()) {
            int numeroCajas = palet.getCajaFin() - palet.getCajaInicio() + 1;
            Double peso = null;
            boolean completo = true;
            for (CajaData caja : destino.getCajas()) {
                if (!Integer.valueOf(palet.getNumeroPalet()).equals(caja.getNumeroPalet())) {
                    continue;
                }
                if (caja.getPesoBrutoKg() == null) {
                    completo = false;
                } else {
                    peso = (peso == null ? 0 : peso) + caja.getPesoBrutoKg();
                }
            }
            if (!completo || peso == null) {
                avisos.add("Palet " + palet.getNumeroPalet() + " de "
                        + destino.getNombreDestino()
                        + " con cajas sin peso: etiqueta de palet sin peso");
                peso = null;
            } else {
                peso += palet.getTara() != null ? palet.getTara() : TARA_PALET_KG_DEFECTO;
            }
            etiquetas.add(new EtiquetaPaletApc(numeroCajas, kg(peso)));
        }
        return etiquetas;
    }

    private static Double pesoDeLaCaja(List<CajaData> lineas) {
        double total = 0;
        for (CajaData linea : lineas) {
            if (linea.getPesoBrutoKg() == null) {
                return null;
            }
            total += linea.getPesoBrutoKg();
        }
        return total;
    }

    private static String kg(Double peso) {
        return peso == null ? null : String.format(ESPANOL, "%.2f Kg", peso);
    }

    private static String claveRefColor(CajaData caja) {
        return caja.getReferencia() + "|" + caja.getCodigoColor();
    }

    private static int tallaNumerica(CajaData caja) {
        try {
            return Integer.parseInt(caja.getTalla().trim());
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE; // tallas raras al final, sin romper
        }
    }
}
```

- [ ] **Step 4: Verificar que pasa, y la suite entera también**

Run: `mvn test -Dtest=ApcEtiquetasGeneradorTest` y después `mvn test`
Expected: PASS ambas (el bean nuevo entra en `EtiquetasGenerationService` por la lista de la inyección; `PackingListApplicationTest` valida que el contexto arranca).

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/test/java
git commit -m "generador de etiquetas de caja y palet de APC"
```

---

### Task 7: Web Paso 2 — generar sin campos que pedir

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/web/PackingListController.java:364-385` (GET `/etiquetas`)
- Modify: `src/main/resources/templates/etiquetas.html`

**Interfaces:**
- Consumes: `soportaDestino(...)` del generador (sin cambios).
- Produces: atributo de modelo `haySoportadas` (boolean) para la vista.

Hoy el botón "Generar etiquetas" se deshabilita si `campos` está vacío — correcto para AMI (siempre pide el excel del pedido) pero bloquearía APC, que no pide nada. El criterio pasa a ser "¿hay alguna destinación soportada?".

- [ ] **Step 1: Añadir `haySoportadas` al modelo**

En el GET `/etiquetas`, tras montar `destinos`:

```java
        boolean haySoportadas = destinos.stream()
                .anyMatch(destino -> generador.soportaDestino(destino.getNombreDestino()));
        model.addAttribute("haySoportadas", haySoportadas);
```

- [ ] **Step 2: Cambiar la condición en la vista**

En `etiquetas.html`, sustituir:

```html
            <p th:if="${campos.isEmpty()}">
                Ninguna destinación de este envío tiene etiquetas implementadas.
            </p>
            <button type="submit" th:disabled="${campos.isEmpty()}">Generar etiquetas</button>
```
por:
```html
            <p th:unless="${haySoportadas}">
                Ninguna destinación de este envío tiene etiquetas implementadas.
            </p>
            <button type="submit" th:disabled="${!haySoportadas}">Generar etiquetas</button>
```

- [ ] **Step 3: Suite en verde y prueba manual**

Run: `mvn test`
Expected: PASS.

Prueba manual (opcional pero recomendada): `mvn spring-boot:run`, pegar un JSON APC con destino JAPAN (adaptar `src/test/resources/ejemplos/envio-apc.json` cambiando `"IVRY"` por `"JAPAN"`), cliente APC, generar packing → ir a Etiquetas → sin campos de archivo, botón habilitado → generar → descargar y abrir el excel: hoja de cajas con pares y hoja de palet. Recordar en Windows: matar el `java` huérfano del 8080 (`netstat -ano | findstr :8080` + `taskkill /F /PID <pid>`).

- [ ] **Step 4: Commit**

```bash
git add src/main/java src/main/resources/templates
git commit -m "el paso de etiquetas permite generar sin campos que pedir (APC)"
```

---

## Self-review del plan (hecho)

- **Cobertura del spec:** layouts/plantillas (T3), builder cajas (T4), builder palet (T5), generador+avisos+pendientes (T6), refactor BloqueEtiquetaModelo (T1), cambio de firma (T2), fix web (T7). Los "hallazgos" corrigen dos supuestos del spec con datos verificados de las plantillas (nombres de hoja por destinación; ASN N° en CROSSLOG).
- **Sin placeholders:** todas las coordenadas están medidas del XML real; todo el código está completo.
- **Consistencia de tipos:** `EtiquetaCajaApc`/`EtiquetaPaletApc`/`generar(layout, cajas, palets)` idénticos en T4, T5 y T6; `BloqueEtiquetaModelo.capturar/copiarEn` idéntico en T1, T4 y T5; firma de `generar(...)` de T2 usada en T6.
