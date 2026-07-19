# Diseño: Framework de Múltiples Outputs (PackingList, Volcado ERP, Etiquetas)

**Fecha:** 2026-07-19  
**Autor:** Brainstorming Superpowers  
**Estado:** Diseño aprobado, pendiente implementación

---

## 1. Problema

Actualmente la vista de resultados solo muestra **PackingLists** (excels por referencia+color). El sistema necesita generar y presentar **3 tipos de outputs distintos**:

1. **PackingLists** (excels individuales, uno por referencia+color) — ya existe
2. **Volcado ERP** (un único excel con tabla de artículos para importar a ERP)
3. **Etiquetas de Caja** (un excel con códigos/etiquetas — en desarrollo, desactivado por ahora)

La vista de resultados debe mostrar las 3 opciones de forma clara, permitiendo al usuario descargar selectivamente los que necesite en cada momento.

---

## 2. Solución: Layout Stack Vertical

La vista de resultados se estructura en **3 secciones ampliadas** apiladas verticalmente (una por tipo de output):

### 2.1 Sección 1: PackingLists

**Estructura:**
- Encabezado: `📦 PACKING LISTS`
- Tabla con columnas:
  - **Fichero**: nombre del archivo `.xlsx`
  - **Contenido**: `DESTINACION · REFERENCIA` con COLOR tabulado en la línea siguiente para alineación visual
  - **Pesos**: badge "completos" o "N cajas sin peso"
  - **Descarga**: enlace para descargar el archivo

- Botones bajo la tabla:
  - `[Descargar todo (ZIP)]` — agrupa todos los PackingLists en un ZIP
  - `[Volver a revisión]` — vuelve a la pantalla anterior
  - `[Nuevo envío]` — reinicia el flujo

**Cambio respecto a ahora:**
- La columna "Contenido" cambia de formato para incluir información adicional de forma tabulada:
  ```
  PARIS · USL728.AL217
         NOIR
  ```

### 2.2 Sección 2: Volcado ERP

**Estructura:**
- Encabezado: `📊 VOLCADO ERP`
- Card simplificado (sin tabla):
  - Nombre archivo: `Volcado_ERP_<numero_factura>.xlsx`
  - Texto informativo: `N líneas de artículos listos para importar`
  
- Botones:
  - `[Descargar]` — descarga el Excel de volcado
  - `[Vista previa]` — abre modal con tabla de columnas

**Modal de Vista Previa:**
- Título: "Vista previa: Volcado ERP"
- Tabla HTML mostrando primeras filas (o todas si no son muchas)
- Columnas: ARTICLE, TALLA, COLORCODI, COLOR, SISTALL, SISGRUP, QUANTITAT
- Botón X para cerrar (CSS puro, no librería)

**Datos de entrada:**
- Nombre factura: obtenido de la sesión actual (`cabecera.numeroFactura`)
- Número de líneas: contador de artículos generados (referencia+talla+color única por línea)
- Tabla: datos generados por `VoltadoErpGenerationService` (a implementar en backend)

### 2.3 Sección 3: Etiquetas de Caja

**Estructura:**
- Encabezado: `🏷️ ETIQUETAS DE CAJA`
- Card con aspecto desactivado (opacidad reducida, texto gris)
  - Nombre archivo: `Etiquetas_<numero_factura>.xlsx`
  - Texto: `🔒 En desarrollo - Próximamente`

- Botón:
  - `[Descargar]` — desactivado (disabled attribute)

---

## 3. Arquitectura de cambios

### Backend (Java/Spring)

**Nuevos servicios/métodos:**

1. **`VoltadoErpGenerationService`** (nuevo)
   - Recibe: `List<CajaData>`, `cabecera`
   - Genera: `VoltadoErpData` (DTO con tabla + metadatos)
   - Lógica:
     - Agrupa por `referencia + talla + color` (una línea por grupo único)
     - Para cada grupo: extrae ARTICLE (referencia), TALLA, COLOR, QUANTITAT (suma)
     - Genera códigos COLORCODI secuenciales (001, 002, 003...) por orden de aparición de color
     - Genera archivo Excel con `VoltadoErpExcelBuilder`

2. **`VoltadoErpExcelBuilder`** (nuevo)
   - Similar a `AmiExcelBuilder` pero para volcado ERP
   - Escribe tabla simple (sin estilos complejos, sin fórmulas)
   - Columnas: ARTICLE, TALLA, COLORCODI, COLOR, SISTALL (=1), SISGRUP (=1), QUANTITAT

3. **`GenaricarGenerationService`** (renombrar `PackingListGenerationService` o crear wrapper)
   - Orquesta los 3 tipos de outputs
   - Llama a: `PackingListGenerationService`, `VoltadoErpGenerationService`, `EtiquetasGenerationService` (stub por ahora)
   - Devuelve: DTO con resultados de los 3 tipos

**DTO nuevo:**
```java
public class OutputsGenerados {
    List<ExcelGenerado> packingLists;      // Lo que devuelve PackingListGenerationService
    VoltadoErpData volcadoErp;             // Lo que devuelve VoltadoErpGenerationService
    EtiquetasData etiquetas;               // Null por ahora
}
```

**Endpoint existente:**
- `/generar` — ya existe, ahora devuelve `OutputsGenerados` en lugar de solo PackingLists

### Frontend (Thymeleaf HTML)

**Archivo a modificar: `resultados.html`**

1. Cambiar modelo de datos que recibe:
   - En lugar de `${excels}` (List<ExcelGenerado>)
   - Recibe `${outputs}` (OutputsGenerados)
   - Variables disponibles:
     - `${outputs.packingLists}` → lista de excels
     - `${outputs.volcadoErp}` → DTO con datos
     - `${outputs.etiquetas}` → null (desactivado)

2. Restructurar HTML en 3 secciones:
   - Sección 1: tabla PackingLists (similar a actual, con formato de contenido tabulado)
   - Sección 2: card Volcado ERP con modal
   - Sección 3: card Etiquetas (desactivada)

3. CSS nuevo:
   - Estilos para cards amplias
   - Modal con overlay + close button
   - Estilos disabled para sección 3

**Nuevos endpoints de descarga:**
- `/descargar-volcado-erp` → descarga el Excel de volcado
- `/descargar-etiquetas` → (stub, retorna 404 por ahora)

### Tests

**Nuevos tests:**
- `VoltadoErpGenerationServiceTest` — agrupación por referencia+talla+color, generación de COLORCODI, cálculo de QUANTITAT
- `VoltadoErpExcelBuilderTest` — escritura de columnas, formato Excel
- End-to-end test en `PackingListApplicationTest` que verifica los 3 outputs juntos

---

## 4. Generación de COLORCODI

**Regla:** Un código numérico secuencial por cada color único, en orden de aparición.

**Ejemplo:**
```
Si los colores aparecen en orden: NOIR, BLEU, NOIR, BLANC
COLORCODI asignado:
  - NOIR → 001 (primera aparición)
  - BLEU → 002 (primera aparición)
  - NOIR → 001 (ya visto)
  - BLANC → 003 (primera aparición)
```

**Implementación:**
- Usar `LinkedHashMap` para preservar orden de inserción
- Recorrer artículos en orden, asignar código a cada color nuevo

---

## 5. Datos del Modal de Volcado ERP

**¿Qué muestra?**
- Tabla HTML con todas las líneas del volcado (o primeras N si hay muchas)
- Columnas: ARTICLE, TALLA, COLORCODI, COLOR, SISTALL, SISGRUP, QUANTITAT

**¿Cómo se pasa al frontend?**
- Backend genera `VoltadoErpData` con:
  - `List<VoltadoErpLinea>` (filas de la tabla)
  - `int totalLineas` (para mostrar "N líneas")
- Thymeleaf renderiza la tabla en JavaScript en el modal (o como atributo data oculto)

---

## 6. Flujo de usuario

```
1. Entrada → 2. Revisión → 3. Generar → Backend genera outputs
                                        ↓
                                   4. Resultados (nueva)
                                   ├─ 📦 PackingLists
                                   │  └─ [Descargar todo] [Volver] [Nuevo]
                                   ├─ 📊 Volcado ERP
                                   │  └─ [Descargar] [Vista previa]
                                   │     └─ Modal: tabla ERP (X cerrar)
                                   └─ 🏷️ Etiquetas
                                      └─ [Descargar] (disabled)
```

---

## 7. Casos de borde / Consideraciones

**¿Qué pasa si no hay PackingLists?**
- No debería pasar en flujo normal, pero si ocurre, mostrar sección vacía o mensaje

**¿Qué pasa si no hay artículos para volcado?**
- Sección Volcado ERP muestra "0 líneas", botones desactivados

**¿Qué pasa con Etiquetas?**
- Por ahora: siempre desactivada, texto "En desarrollo"
- Cuando se implemente: se habilitará con lógica similar

**Modal responsive:**
- En mobile, el modal debe ajustarse (ancho máximo 90vw, altura máxima 80vh)
- Tabla scroll horizontal si es muy ancha

---

## 8. Checklist de implementación

Backend:
- [ ] Crear `VoltadoErpData` (DTO)
- [ ] Crear `VoltadoErpGenerationService`
- [ ] Crear `VoltadoErpExcelBuilder`
- [ ] Crear `OutputsGenerados` (DTO orquestador)
- [ ] Crear `GenerationOrchestrationService` (orquesta los 3)
- [ ] Modificar endpoint `/generar` para retornar `OutputsGenerados`
- [ ] Crear endpoints `/descargar-volcado-erp`, `/descargar-etiquetas`
- [ ] Crear `VoltadoErpGenerationServiceTest`
- [ ] Crear `VoltadoErpExcelBuilderTest`

Frontend:
- [ ] Modificar `resultados.html` (3 secciones)
- [ ] CSS para cards y modal
- [ ] JavaScript para abrir/cerrar modal

---

## 9. Notas de diseño

- **Separación de responsabilidades:** Cada tipo de output es independiente (service + builder)
- **Reutilización:** `VoltadoErpExcelBuilder` sigue patrón de `AmiExcelBuilder`, facilitando futuros clientes
- **Desactivación graceful:** Etiquetas desactivada sin código complicado, fácil de activar luego
- **Datos completos en tabla:** El volcado ERP en modal muestra todos los datos, permitiendo verificación pre-descarga

---

## 10. Testing

**Flujo end-to-end a validar:**
1. Upload JSON con 3 destinaciones × 2 referencias × 2 colores (12 artículos totales)
2. Generar outputs
3. Verificar:
   - PackingLists: 4 excels (2 refs × 2 colores)
   - Volcado ERP: 1 excel, 12 líneas (una por referencia+talla+color única)
   - COLORCODI: secuencial, mismo color = mismo código
   - Modal: muestra tabla correcta
