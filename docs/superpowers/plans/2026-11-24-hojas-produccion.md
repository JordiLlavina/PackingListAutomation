# Hojas de Producción — plan de implementación

Diseño: `docs/superpowers/specs/2026-11-24-hojas-produccion-design.md`.

## Tareas

1. **Esquema**: `V13__hojas_produccion.sql` con la tabla `hoja_produccion` (columnas sueltas + `datos` clob).
2. **Modelo**: `model/DatosHojaProduccion` (record con `LineaProduccion`), normaliza nulos a vacío.
3. **Persistencia**: `HojaProduccion`, `HojaProduccionRepository`, `ResumenHojaProduccion`, `ArchivoHojasProduccion` (guardar, todas, datosDe, siguienteNumero por taller, siguientesPorTaller, talleres). Test `ArchivoHojasProduccionTest`.
4. **Opciones**: `service/produccion/OpcionesHojaProduccion` (marcado interior, colores, tamaños por cliente, fornitura, marcas de hilo, talleres conocidos). Test.
5. **Excel**: `service/produccion/HojaProduccionExcelBuilder` + `AlturaFila` (estimación de líneas). Test que reabre el xlsx, comprueba celdas, apaisado, y deja `target/Hoja de produccion ejemplo.xlsx`.
6. **Catálogo**: `service/produccion/ArticulosProduccion` → lista de `ArticuloProduccion` por cliente desde `CatalogoModelos` + `PielRepository`/`MemoriaPieles`. Test.
7. **Web**: `HojaProduccionForm`, `HojasProduccionController` (`GET /hojas-produccion`, `GET /hojas-produccion/catalogo`, `POST /hojas-produccion/generar`, `GET /hojas-produccion/antiguas`, `GET /hojas-produccion/antiguas/{id}/xlsx`). Test MockMvc.
8. **Pantallas**: `hojas-produccion.html`, `hojas-produccion-antiguas.html`, `static/hoja-produccion.js`, CSS.
9. **Menú**: tarjeta nueva en el sitio de ClickUp; ClickUp al pie. Ajustar `ClientesControllerTest` y `RutasTest`.
10. **Documentación**: `.claude/rules/hojas-produccion.md`, fila en la tabla de `CLAUDE.md`.
11. `mvn test -Dskip.frontend=true` en verde; revisión visual del excel.
