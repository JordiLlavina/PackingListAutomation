# Peso único por caja física, unificado para todos los clientes — diseño

Fecha: 2026-07-26 · Estado: aprobado

## Objetivo

Unificar cómo se determina el **peso bruto de una caja física** en todos los
clientes (packing lists y etiquetas), aplicando la regla que hoy solo usa el
packing de AMI: **el peso de la caja es el de su línea líder** (la primera
entrada del JSON con ese número de caja); las demás líneas de una caja mixta
no aportan peso; la caja está **pendiente** si su línea líder no trae peso.

## Contexto y motivación

Una caja física puede aparecer en varias líneas del JSON (misma caja, varios
colores/tallas/canales). El peso se toma **una sola vez** porque la caja se
pesa entera una vez. Hoy esa regla está implementada de tres formas distintas,
copiada en cuatro métodos privados, y ha divergido:

| Superficie | Comportamiento actual | ¿Correcto? |
|---|---|---|
| AMI packing (`AmiGenerador.mapearCajasCinturon`) | peso = línea líder; pendiente si la líder no lo trae | ✅ referencia |
| APC packing (`ApcExcelBuilder.pesoBrutoTotal`) | suma de todas las líneas; `null` si falta alguna | ❌ |
| APC etiquetas (`ApcEtiquetasGenerador.pesoDeLaCaja` y `etiquetasDePalet`) | suma; palet sin peso si *cualquier línea* es null | ❌ |
| AMI etiquetas (`AmiEtiquetasGenerador`) | suma; el comentario dice "línea líder" pero el código suma | ❌ |
| GENERIC packing (`GenericoExcelBuilder`) | una fila por `CajaData`; total suma por fila | ⚠️ frágil si hay cajas mixtas |

**Consecuencia medida (2026-07-26):** con la convención documentada ("peso una
sola vez, en la primera línea"), una caja mixta de APC sale **sin peso** tanto
en el packing como en la etiqueta, porque la suma con exigencia de no-null
devuelve `null`. Verificado empíricamente: peso en la 1ª línea → packing `null`
y etiqueta pendiente; peso en todas las líneas → 7,60 en ambos. Las etiquetas
de AMI tienen el mismo defecto latente (pierden el peso de las cajas mixtas de
cinturón) sin que ningún test lo detecte.

## Decisiones cerradas con el usuario

1. **Semántica: exactamente como AMI.** Peso de la caja = línea líder (la 1ª).
   Si la líder no trae peso, la caja está pendiente **aunque otra línea sí lo
   tenga**. Las líneas no líder se ignoran para el peso. (No se suma; no se
   busca "la primera con peso".)
2. **Alcance: todos los clientes** y ambas salidas (packing list y etiquetas):
   APC packing, APC etiquetas, AMI etiquetas y GENERIC packing pasan a la regla
   de la línea líder. AMI packing ya la cumple (se refactoriza para compartir
   la misma pieza, sin cambiar comportamiento).
3. **Convención de entrada del JSON** pasa a ser la de AMI: el `pesoBruto` va
   **una sola vez, en la primera línea** de cada caja física. La documentación
   que hoy se contradice con el código se actualiza para reflejarlo.

## Arquitectura

### Componente nuevo: `CajaFisica` (paquete `model`)

Value object que agrupa las `CajaData` de una misma caja física y encapsula la
regla en un único sitio.

```
CajaFisica
  numeroCaja()            int
  lineas()                List<CajaData>   // orden de llegada; lineas[0] = líder
  lider()                 CajaData         // = lineas[0]
  pesoBrutoKg()           Double           // = líder.pesoBrutoKg (null = sin peso)
  pesoNetoKg()            Double           // = líder.pesoNetoKg
  tienePesosCompletos()   boolean          // delega en la líder (bruto && neto)
  numeroPalet()           Integer          // = líder.numeroPalet
  static agrupar(Collection<CajaData>) → List<CajaFisica>
```

- `agrupar(...)` agrupa por `numeroCaja` con un `LinkedHashMap` (preserva el
  orden de llegada, igual que hoy). La primera `CajaData` de cada grupo es la
  líder.
- **Pendiente** no es un método propio: cada superficie decide su criterio
  sobre la líder — las etiquetas usan `pesoBrutoKg() == null`; el packing usa
  `!tienePesosCompletos()` (bruto y neto), igual que AMI hoy.

Qué hace: representa la caja física y su peso único. Cómo se usa: `CajaFisica
.agrupar(cajas)` y luego `pesoBrutoKg()` / `tienePesosCompletos()`. De qué
depende: solo de `CajaData`.

### Cambios por superficie

Todas consumen `CajaFisica`; cada builder conserva su forma de pintar filas.

- **AMI packing** (`AmiGenerador`): usar `CajaFisica.agrupar` para la
  agrupación por caja y el cálculo de pendientes. Comportamiento idéntico
  (es la referencia). Los tests AMI existentes deben seguir en verde.
- **APC packing** (`ApcExcelBuilder`): eliminar el `record CajaFisica` privado
  y su `pesoBrutoTotal()` (suma); usar el `CajaFisica` compartido y su
  `pesoBrutoKg()`. El peso se escribe solo en la fila líder (ya lo hace). Los
  subtotales de palet y el TOTAL suman el peso **por caja física (una vez)**,
  no por línea.
- **APC etiquetas** (`ApcEtiquetasGenerador`): `pesoDeLaCaja(lineas)` pasa a
  devolver `new CajaFisica(...).pesoBrutoKg()`. `etiquetasDePalet` se reescribe
  para iterar **cajas físicas** del palet: peso del palet = suma de los pesos
  de sus cajas + tara; en blanco solo si alguna caja física está pendiente
  (hoy lo marca en blanco si *cualquier línea* es null → roto con la nueva
  convención).
- **AMI etiquetas** (`AmiEtiquetasGenerador`): sustituir el bucle de suma por el
  peso de la línea líder vía `CajaFisica`.
- **GENERIC packing** (`GenericoExcelBuilder`): agrupar por caja física; el
  `pesoBruto` se escribe solo en la fila líder de cada caja; el total y la lista
  de pendientes se calculan por caja física. Si un cliente genérico no tiene
  cajas mixtas (una línea por caja), la salida no cambia.

### Manejo de errores / pendientes

- Nunca se bloquea (regla del proyecto): caja sin peso → celdas en blanco + la
  caja va a `cajasPendientes` (packing) o genera aviso + peso en blanco
  (etiquetas).
- Peso de palet en blanco si alguna caja física del palet está pendiente.
- El número de cajas del palet sigue siendo `cajaFin − cajaInicio + 1`.

## Documentación a actualizar

- `docs/Packing Lists/campos-json-por-cliente.md`: el `pesoBruto` de una caja
  mixta va una sola vez, en la primera línea, y ese es el peso de la caja (no
  se suman las líneas).
- Javadoc de `EnvioInput.CajaRangoInput` (líneas ~123-125): mismo matiz.
- Comentarios de `AmiEtiquetasGenerador` / `ApcEtiquetasGenerador` que hoy
  dicen "suma de líneas".

## Tests

Patrón del proyecto (JUnit 5 puro, `new`; los tests de builder reabren el
`.xlsx` con POI y comprueban celdas reales):

- `CajaFisicaTest`: líder, `pesoBrutoKg`/`pesoNetoKg`, `tienePesosCompletos`,
  `agrupar` (orden y agrupación por número), caja mixta con peso solo en la
  líder, caja sin peso.
- APC packing y APC etiquetas: caja mixta con `pesoBruto` **solo en la líder**
  → el peso aparece (hoy saldría en blanco); palet con esa caja → peso de palet
  = suma + tara.
- AMI etiquetas: caja mixta de cinturón con peso en la líder → la etiqueta
  lleva el peso (hoy en blanco).
- AMI packing y GENERIC: los tests existentes siguen en verde (comportamiento
  sin cambios para datos no mixtos).
- Regenerar el fixture `src/test/resources/ejemplos/envio-apc-etiquetas.json`:
  el `pesoBruto` pasa a ir **solo en la primera línea** de cada caja física
  (hoy está por línea). Debe seguir generando 4 excels (JAPAN, KOREA, D. USA,
  C-LOG) con las mismas cajas pendientes deliberadas.

## Fuera de alcance

- Cambiar el modelo de datos de `CajaData` o el flujo de import/inferencia.
- Añadir agrupación de rangos idénticos en GENERIC (sigue una fila por caja).
- Etiquetas de palet de AMI (no existen).
