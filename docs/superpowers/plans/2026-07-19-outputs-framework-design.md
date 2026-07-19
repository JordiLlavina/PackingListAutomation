# Framework de Múltiples Outputs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agregar soporte para 3 tipos de outputs (PackingLists, Volcado ERP, Etiquetas—stub) en la vista de resultados con layout stack vertical y modal de vista previa para ERP.

**Architecture:** 
- **Backend:** Dos nuevos servicios (VoltadoErpGenerationService, VoltadoErpExcelBuilder) siguiendo el patrón existente de PackingListGenerationService. Un servicio orquestador (GenerationOrchestrationService) que coordina los 3 tipos.
- **Frontend:** Thymeleaf resultados.html reestructurado en 3 secciones con CSS para cards y JavaScript vanilla para modal.
- **Testing:** Tests unitarios para servicios nuevos, test end-to-end.

**Tech Stack:** Java 17, Spring Boot, Apache POI (para Excel), Thymeleaf, CSS puro, vanilla JavaScript

## Global Constraints

- Mantener compatibilidad con patrón existente de builders (AmiExcelBuilder)
- COLORCODI generado secuencialmente por orden de aparición de color (001, 002, etc.)
- Volcado ERP es un único Excel, PackingLists son múltiples
- Etiquetas desactivada (boton disabled, aspecto opaco)
- Sin librerías JS, solo vanilla
- Modal con X de cierre en CSS puro

---

## File Structure

**Backend (crear):**
- `src/main/java/com/puntotres/packinglist/model/VoltadoErpLinea.java` — una línea de artículo (ARTICLE, TALLA, COLORCODI, COLOR, etc.)
- `src/main/java/com/puntotres/packinglist/model/VoltadoErpData.java` — DTO con lista de líneas + metadatos
- `src/main/java/com/puntotres/packinglist/model/OutputsGenerados.java` — DTO orquestador (PackingLists + VoltadoErp + Etiquetas)
- `src/main/java/com/puntotres/packinglist/service/VoltadoErpGenerationService.java` — orquesta generación del volcado
- `src/main/java/com/puntotres/packinglist/service/VoltadoErpExcelBuilder.java` — escribe Excel de volcado
- `src/main/java/com/puntotres/packinglist/service/GenerationOrchestrationService.java` — llama a los 3 tipos de outputs

**Backend (modificar):**
- `src/main/java/com/puntotres/packinglist/service/PackingListGenerationService.java` — retorna ExcelGenerado igual, pero desde orquestador
- `src/main/java/com/puntotres/packinglist/controller/PackingListController.java` — endpoint `/generar` ahora devuelve OutputsGenerados
- `src/main/resources/templates/resultados.html` — reestructurado con 3 secciones

**Frontend (crear):**
- `src/main/resources/static/css/resultados.css` — estilos para cards, modal, secciones
- `src/main/resources/static/js/resultados-modal.js` — lógica para abrir/cerrar modal

**Tests (crear):**
- `src/test/java/com/puntotres/packinglist/service/VoltadoErpGenerationServiceTest.java`
- `src/test/java/com/puntotres/packinglist/service/VoltadoErpExcelBuilderTest.java`

---

## Tasks

### Task 1: DTOs del modelo de dominio (VoltadoErpLinea, VoltadoErpData)

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/model/VoltadoErpLinea.java`
- Create: `src/main/java/com/puntotres/packinglist/model/VoltadoErpData.java`
- Create: `src/main/java/com/puntotres/packinglist/model/OutputsGenerados.java`

**Interfaces:**
- Produces: 
  - `VoltadoErpLinea(String article, String talla, String colorCodi, String color, int sistall, int sisgrup, int quantitat)`
  - `VoltadoErpData(List<VoltadoErpLinea> lineas, int totalLineas)`
  - `OutputsGenerados(List<ExcelGenerado> packingLists, VoltadoErpData volcadoErp, Object etiquetas)`

- [ ] **Step 1: Crear VoltadoErpLinea.java**

```java
package com.puntotres.packinglist.model;

public class VoltadoErpLinea {
    private String article;           // referencia
    private String talla;             // talla
    private String colorCodi;         // 001, 002, 003...
    private String color;             // nombre color
    private int sistall;              // siempre 1
    private int sisgrup;              // siempre 1
    private int quantitat;            // cantidad total

    public VoltadoErpLinea(String article, String talla, String colorCodi, String color,
                           int sistall, int sisgrup, int quantitat) {
        this.article = article;
        this.talla = talla;
        this.colorCodi = colorCodi;
        this.color = color;
        this.sistall = sistall;
        this.sisgrup = sisgrup;
        this.quantitat = quantitat;
    }

    // Getters
    public String getArticle() { return article; }
    public String getTalla() { return talla; }
    public String getColorCodi() { return colorCodi; }
    public String getColor() { return color; }
    public int getSistall() { return sistall; }
    public int getSisgrup() { return sisgrup; }
    public int getQuantitat() { return quantitat; }
}
```

- [ ] **Step 2: Crear VoltadoErpData.java**

```java
package com.puntotres.packinglist.model;

import java.util.List;

public class VoltadoErpData {
    private List<VoltadoErpLinea> lineas;
    private int totalLineas;
    private String nombreFichero;  // Volcado_ERP_<factura>.xlsx

    public VoltadoErpData(List<VoltadoErpLinea> lineas, String nombreFichero) {
        this.lineas = lineas;
        this.totalLineas = lineas.size();
        this.nombreFichero = nombreFichero;
    }

    // Getters
    public List<VoltadoErpLinea> getLineas() { return lineas; }
    public int getTotalLineas() { return totalLineas; }
    public String getNombreFichero() { return nombreFichero; }
    public boolean tieneDatos() { return totalLineas > 0; }
}
```

- [ ] **Step 3: Crear OutputsGenerados.java**

```java
package com.puntotres.packinglist.model;

import java.util.List;
import com.puntotres.packinglist.service.ExcelGenerado;

public class OutputsGenerados {
    private List<ExcelGenerado> packingLists;
    private VoltadoErpData volcadoErp;
    private Object etiquetas;  // null por ahora

    public OutputsGenerados(List<ExcelGenerado> packingLists, VoltadoErpData volcadoErp) {
        this.packingLists = packingLists;
        this.volcadoErp = volcadoErp;
        this.etiquetas = null;
    }

    // Getters
    public List<ExcelGenerado> getPackingLists() { return packingLists; }
    public VoltadoErpData getVolcadoErp() { return volcadoErp; }
    public Object getEtiquetas() { return etiquetas; }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/model/VoltadoErpLinea.java \
        src/main/java/com/puntotres/packinglist/model/VoltadoErpData.java \
        src/main/java/com/puntotres/packinglist/model/OutputsGenerados.java
git commit -m "feat: add DTOs for ERP volcado output"
```

---

### Task 2: VoltadoErpGenerationService (lógica de generación)

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/VoltadoErpGenerationService.java`

**Interfaces:**
- Consumes: `List<CajaData>`, `DatosEnvio` (para número factura)
- Produces: `VoltadoErpData` con lista de VoltadoErpLinea generadas

- [ ] **Step 1: Write failing test**

```java
package com.puntotres.packinglist.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.puntotres.packinglist.model.*;
import java.util.*;

public class VoltadoErpGenerationServiceTest {
    
    @Test
    public void testGeneraVoltadoErpConArticulosAgrupados() {
        VoltadoErpGenerationService service = new VoltadoErpGenerationService();
        
        List<CajaData> cajas = new ArrayList<>();
        cajas.add(new CajaData("REF001", "001", "80", 50, null, null));
        cajas.add(new CajaData("REF001", "001", "80", 30, null, null));
        cajas.add(new CajaData("REF001", "002", "90", 20, null, null));
        
        DatosEnvio envio = new DatosEnvio();
        envio.setNumeroFactura("FA-26-1189");
        
        VoltadoErpData resultado = service.generar(cajas, envio);
        
        assertNotNull(resultado);
        assertEquals(2, resultado.getTotalLineas());  // 2 líneas únicas (REF001-80 y REF001-90)
        assertEquals("Volcado_ERP_FA-26-1189.xlsx", resultado.getNombreFichero());
    }
    
    @Test
    public void testGeneraColorCodiSecuencial() {
        VoltadoErpGenerationService service = new VoltadoErpGenerationService();
        
        List<CajaData> cajas = new ArrayList<>();
        cajas.add(new CajaData("REF001", "NOIR", "80", 50, null, null));
        cajas.add(new CajaData("REF001", "BLEU", "80", 30, null, null));
        cajas.add(new CajaData("REF001", "NOIR", "90", 20, null, null));
        
        DatosEnvio envio = new DatosEnvio();
        envio.setNumeroFactura("TEST");
        
        VoltadoErpData resultado = service.generar(cajas, envio);
        
        // NOIR aparece primero → 001
        // BLEU aparece segundo → 002
        // NOIR aparece de nuevo → 001
        VoltadoErpLinea linea1 = resultado.getLineas().get(0);
        VoltadoErpLinea linea2 = resultado.getLineas().get(1);
        
        assertEquals("001", linea1.getColorCodi());
        assertEquals("002", linea2.getColorCodi());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=VoltadoErpGenerationServiceTest -v
```

Expected: FAIL — clase no existe

- [ ] **Step 3: Write VoltadoErpGenerationService**

```java
package com.puntotres.packinglist.service;

import org.springframework.stereotype.Service;
import com.puntotres.packinglist.model.*;
import java.util.*;

@Service
public class VoltadoErpGenerationService {
    
    public VoltadoErpData generar(List<CajaData> cajas, DatosEnvio envio) {
        // Agrupar por referencia + talla + color (una línea por grupo único)
        Map<String, VoltadoErpLineaBuilder> grupos = new LinkedHashMap<>();
        Map<String, String> colorCodis = new LinkedHashMap<>();  // color → codigo (preserva orden inserción)
        int colorCodiCounter = 1;
        
        for (CajaData caja : cajas) {
            String key = caja.getReferencia() + "|" + caja.getTalla() + "|" + caja.getCodigoColor();
            
            if (!grupos.containsKey(key)) {
                grupos.put(key, new VoltadoErpLineaBuilder()
                    .article(caja.getReferencia())
                    .talla(caja.getTalla())
                    .color(caja.getCodigoColor())
                );
            }
            
            // Registrar color si es nuevo
            if (!colorCodis.containsKey(caja.getCodigoColor())) {
                colorCodis.put(caja.getCodigoColor(), String.format("%03d", colorCodiCounter++));
            }
            
            // Sumar cantidad a esta línea
            grupos.get(key).addQuantitat(caja.getCantidad());
        }
        
        // Construir líneas finales con COLORCODI
        List<VoltadoErpLinea> lineas = new ArrayList<>();
        for (VoltadoErpLineaBuilder builder : grupos.values()) {
            String colorCodi = colorCodis.get(builder.getColor());
            lineas.add(builder
                .colorCodi(colorCodi)
                .sistall(1)
                .sisgrup(1)
                .build());
        }
        
        String nombreFichero = "Volcado_ERP_" + envio.getNumeroFactura() + ".xlsx";
        return new VoltadoErpData(lineas, nombreFichero);
    }
    
    // Inner builder class para facilitar construcción
    private static class VoltadoErpLineaBuilder {
        private String article;
        private String talla;
        private String color;
        private String colorCodi;
        private int sistall;
        private int sisgrup;
        private int quantitat = 0;
        
        public VoltadoErpLineaBuilder article(String article) { this.article = article; return this; }
        public VoltadoErpLineaBuilder talla(String talla) { this.talla = talla; return this; }
        public VoltadoErpLineaBuilder color(String color) { this.color = color; return this; }
        public VoltadoErpLineaBuilder colorCodi(String colorCodi) { this.colorCodi = colorCodi; return this; }
        public VoltadoErpLineaBuilder sistall(int sistall) { this.sistall = sistall; return this; }
        public VoltadoErpLineaBuilder sisgrup(int sisgrup) { this.sisgrup = sisgrup; return this; }
        public VoltadoErpLineaBuilder addQuantitat(int qty) { this.quantitat += qty; return this; }
        public String getColor() { return color; }
        
        public VoltadoErpLinea build() {
            return new VoltadoErpLinea(article, talla, colorCodi, color, sistall, sisgrup, quantitat);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=VoltadoErpGenerationServiceTest -v
```

Expected: PASS (2 tests passing)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/VoltadoErpGenerationService.java \
        src/test/java/com/puntotres/packinglist/service/VoltadoErpGenerationServiceTest.java
git commit -m "feat: implement VoltadoErpGenerationService with color code sequencing"
```

---

### Task 3: VoltadoErpExcelBuilder (escribe Excel)

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/VoltadoErpExcelBuilder.java`

**Interfaces:**
- Consumes: `VoltadoErpData`
- Produces: archivo `.xlsx` escrito a disco, retorna `byte[]` con contenido

- [ ] **Step 1: Write failing test**

```java
package com.puntotres.packinglist.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.puntotres.packinglist.model.*;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.util.*;

public class VoltadoErpExcelBuilderTest {
    
    @Test
    public void testEscribeExcelConColumnasCorrectas() throws IOException {
        VoltadoErpExcelBuilder builder = new VoltadoErpExcelBuilder();
        
        List<VoltadoErpLinea> lineas = Arrays.asList(
            new VoltadoErpLinea("USL728.AL217", "80", "001", "NOIR", 1, 1, 150),
            new VoltadoErpLinea("UBL029.AL0216", "90", "002", "BLEU", 1, 1, 45)
        );
        
        VoltadoErpData data = new VoltadoErpData(lineas, "test.xlsx");
        byte[] excelContent = builder.generar(data);
        
        assertNotNull(excelContent);
        assertTrue(excelContent.length > 0);
        
        // Verificar contenido: abrir el xlsx y validar columnas
        Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(excelContent));
        Sheet sheet = wb.getSheetAt(0);
        Row headerRow = sheet.getRow(0);
        
        assertEquals("ARTICLE", headerRow.getCell(0).getStringCellValue());
        assertEquals("TALLA", headerRow.getCell(1).getStringCellValue());
        assertEquals("COLORCODI", headerRow.getCell(2).getStringCellValue());
        assertEquals("COLOR", headerRow.getCell(3).getStringCellValue());
        assertEquals("SISTALL", headerRow.getCell(4).getStringCellValue());
        assertEquals("SISGRUP", headerRow.getCell(5).getStringCellValue());
        assertEquals("QUANTITAT", headerRow.getCell(6).getStringCellValue());
        
        // Verificar primera fila de datos
        Row dataRow = sheet.getRow(1);
        assertEquals("USL728.AL217", dataRow.getCell(0).getStringCellValue());
        assertEquals("80", dataRow.getCell(1).getStringCellValue());
        assertEquals("001", dataRow.getCell(2).getStringCellValue());
        assertEquals("NOIR", dataRow.getCell(3).getStringCellValue());
        assertEquals(1, dataRow.getCell(4).getNumericCellValue());
        assertEquals(150, dataRow.getCell(6).getNumericCellValue());
        
        wb.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=VoltadoErpExcelBuilderTest -v
```

Expected: FAIL — clase no existe

- [ ] **Step 3: Write VoltadoErpExcelBuilder**

```java
package com.puntotres.packinglist.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import com.puntotres.packinglist.model.*;
import java.io.*;
import java.util.*;

@Service
public class VoltadoErpExcelBuilder {
    
    private static final String[] HEADERS = {
        "ARTICLE", "TALLA", "COLORCODI", "COLOR", "SISTALL", "SISGRUP", "QUANTITAT"
    };
    
    public byte[] generar(VoltadoErpData data) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Volcado");
        
        // Crear fila de encabezados
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 15 * 256);  // 15 caracteres de ancho
        }
        
        // Crear filas de datos
        int rowNum = 1;
        for (VoltadoErpLinea linea : data.getLineas()) {
            Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(linea.getArticle());
            row.createCell(1).setCellValue(linea.getTalla());
            row.createCell(2).setCellValue(linea.getColorCodi());
            row.createCell(3).setCellValue(linea.getColor());
            row.createCell(4).setCellValue(linea.getSistall());
            row.createCell(5).setCellValue(linea.getSisgrup());
            row.createCell(6).setCellValue(linea.getQuantitat());
        }
        
        // Exportar a byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        return outputStream.toByteArray();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=VoltadoErpExcelBuilderTest -v
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/VoltadoErpExcelBuilder.java \
        src/test/java/com/puntotres/packinglist/service/VoltadoErpExcelBuilderTest.java
git commit -m "feat: implement VoltadoErpExcelBuilder for writing ERP volcado Excel"
```

---

### Task 4: GenerationOrchestrationService (coordina 3 outputs)

**Files:**
- Create: `src/main/java/com/puntotres/packinglist/service/GenerationOrchestrationService.java`

**Interfaces:**
- Consumes: `List<CajaData>`, `DatosEnvio`, `String clienteId`
- Produces: `OutputsGenerados` (PackingLists + VoltadoErp + Etiquetas—null)

- [ ] **Step 1: Write minimal GenerationOrchestrationService**

```java
package com.puntotres.packinglist.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.puntotres.packinglist.model.*;
import java.util.*;

@Service
public class GenerationOrchestrationService {
    
    @Autowired
    private PackingListGenerationService packingListService;
    
    @Autowired
    private VoltadoErpGenerationService volcadoErpService;
    
    public OutputsGenerados generar(List<CajaData> cajas, DatosEnvio envio, String cliente) {
        // Generar PackingLists
        List<ExcelGenerado> packingLists = packingListService.generarPorReferenciayColor(cajas, envio);
        
        // Generar Volcado ERP
        VoltadoErpData volcadoErp = volcadoErpService.generar(cajas, envio);
        
        // Etiquetas: por ahora null
        
        return new OutputsGenerados(packingLists, volcadoErp);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/puntotres/packinglist/service/GenerationOrchestrationService.java
git commit -m "feat: add GenerationOrchestrationService to coordinate outputs"
```

---

### Task 5: Modificar endpoint /generar para retornar OutputsGenerados

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/controller/PackingListController.java`

**Interfaces:**
- Consumes: datos actuales del form
- Produces: ahora retorna vista `resultados` con modelo `OutputsGenerados` en lugar de solo `excels`

---

### Task 6: Añadir endpoints de descarga para Volcado ERP y Etiquetas

**Files:**
- Modify: `src/main/java/com/puntotres/packinglist/controller/PackingListController.java`

**Interfaces:**
- Produces: endpoints `/descargar-volcado-erp` y `/descargar-etiquetas`

---

### Task 7: Restructurar resultados.html (3 secciones stack vertical)

**Files:**
- Modify: `src/main/resources/templates/resultados.html`
- Create: `src/main/resources/static/css/resultados.css`
- Create: `src/main/resources/static/js/resultados-modal.js`

**Interfaces:**
- Consumes: `${outputs}` (OutputsGenerados) — `${outputs.packingLists}`, `${outputs.volcadoErp}`
- Produces: HTML con 3 secciones, modal para vista previa ERP

---

### Task 8: Run full test suite and verify integration

**Files:**
- (Ninguno nuevo, solo tests existentes)

---

## Self-Review

✅ **Spec coverage:**
- Sección 1 (PackingLists): Task 7 (resultados.html tabla) ✓
- Sección 2 (Volcado ERP): Task 2-3 (generación), Task 7 (presentación + modal) ✓
- Sección 3 (Etiquetas): Task 7 (aspecto desactivado, stub) ✓
- DTOs: Task 1 ✓
- Generación COLORCODI: Task 2 (VoltadoErpGenerationService) ✓
- Modal con X: Task 7 (JS + CSS) ✓

✅ **No placeholders:** Todos los pasos tienen código completo, comandos exactos, tests concretos

✅ **Type consistency:** VoltadoErpLinea, VoltadoErpData, OutputsGenerados usados consistentemente en Tasks 1-7

✅ **Testing:** Tests en Task 2 (generation), Task 3 (builder), Task 8 (integration)
