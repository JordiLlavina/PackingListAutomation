# Etiquetas de caja y palet de APC — diseño

Fecha: 2026-07-25 · Estado: aprobado (enfoque A)

## Objetivo

Añadir a la funcionalidad de etiquetas existente (hoy solo AMI, etiquetas de
caja) las etiquetas de **caja** y de **palet** del cliente **APC**, con un
excel por destinación generado a partir de la plantilla real de cada una.

## Contexto y hallazgos de las plantillas

Las 4 plantillas viven en `docs/Etiquetas cajas/` con prefijo
`ETIQUETA CAJA APC ...`: **JAPAN, KOREA, USA, WH CROSSLOG**. Inspección real
(JAPAN, verificar el resto en implementación):

- Cada plantilla es **un fichero por destinación** con los datos estáticos
  (EXPEDITEUR, DESTINATAIRE, DESTINATION, SERVICE Bolloré Logistics ROISSY)
  **ya horneados en las celdas**: no hay que configurarlos ni escribirlos.
- Dos hojas por libro, identificadas **por nombre** (no por posición):
  - `"Etiquette colis Bolloré "` — ¡con **espacio final** en el nombre! —
    etiquetas de caja. A4 **vertical**, con **dos etiquetas modelo ya
    apiladas verticalmente** (filas 6–19 y 27–40): el par de una caja ocupa
    un A4 completo. Coincide con la regla por defecto (apilado vertical);
    no hace falta disposición horizontal.
  - `"Etiquette Palette Bolloré"` — etiqueta de palet. A4 **apaisado**,
    **una** etiqueta por palet (aquí no aplica la regla del par).
- Los valores van en la columna C junto a su rótulo en B (según plantilla).

## Decisiones cerradas con el usuario

1. **"Nombre total de colis sur la palette"** = **número de cajas del palet**
   (`cajaFin − cajaInicio + 1`), coherente con la glosa inglesa del campo
   ("Total number of packages on the palette"). NO es el número de palet.
2. **Order N°** y **Livraison code** = literal **`"NOT FOUND"`** en esta
   iteración. La búsqueda del order number en el excel de pedido de APC y la
   petición del livraison code al usuario quedan para otra iteración.
   Consecuencia: **APC no pide ningún input al usuario en el Paso 2**.
3. Enfoque **A**: builder APC paralelo + **extraer el motor de replicación
   de bloques** (`BloqueModelo`) de `AmiEtiquetasExcelBuilder` a una clase
   compartida, porque APC lo necesita en dos hojas (cajas y palet).

## Campos de las etiquetas

### Etiqueta de caja (par apilado por caja física)

| Campo | Origen |
|---|---|
| Order N° : | `"NOT FOUND"` (fijo por ahora) |
| Livrasion code | `"NOT FOUND"` (fijo por ahora) |
| Reference : | JSON `referencia` |
| Colour : | JSON `codigoColor` |
| Size : | `"U"` si bolso/cartera; talla numérica si cinturón (`CajaData.esCinturon()`) |
| Pieces by size : | unidades de la caja (suma de líneas de la caja física) |
| Colisage | `posición / total` incremental por destinación (ej. `3 / 11`) |
| Poids brut du colis | peso bruto de la caja física (suma de líneas); si falta alguno → celda en blanco + caja a `cajasPendientes` |

Formato del peso: `"7,60 Kg"` (coma decimal, sufijo `Kg`, como el ejemplo de
la plantilla).

Caja física = entradas del JSON con el mismo `numeroCaja` (mismo criterio que
`AmiEtiquetasGenerador`). Cajas mixtas de varias referencias/colores: se
etiqueta con la línea líder y se avisa (mismo comportamiento que AMI).
Cinturones multi-talla: Size con tallas unidas por `-` y Pieces by size con
pares `cantidad-talla`, reutilizando la lógica AMI como referencia.

### Etiqueta de palet (una por palet de la destinación)

| Campo | Origen |
|---|---|
| Nombre total de colis sur la palette | nº de cajas del palet (`cajaFin − cajaInicio + 1`) |
| Poids brut | suma de pesos brutos de las cajas del palet **+ tara** (`PaletData.tara`, por defecto **10 kg** — misma convención que `ApcExcelBuilder.TARA_PALET_KG_DEFECTO`); si falta el peso de alguna caja → en blanco + aviso |

## Arquitectura

### Componentes nuevos (`service/etiquetas/`)

- **`ApcEtiquetaLayout`**: record con, por destinación, la ruta de la
  plantilla en el classpath y las coordenadas 0-based de los campos de la
  hoja de cajas (altura de bloque del par, offset de la segunda etiqueta,
  filas de cada campo) y de la hoja de palet (altura de bloque, filas de sus
  dos campos). Coordenadas exactas a fijar en implementación volcando cada
  plantilla (pueden diferir entre destinaciones, como pasa en AMI).
  Mapa destino→layout con claves normalizadas: `JAPAN`, `KOREA`,
  `D. USA`/`USA`, `C-LOG`/`WH CROSSLOG` (aceptar tanto la clave de la config
  de packing como el nombre del fichero de plantilla).
- **`ApcEtiquetasExcelBuilder`**: abre la plantilla de la destinación,
  **conserva las dos hojas**, y en cada una captura el bloque modelo y lo
  replica (par por caja / etiqueta por palet), escribe los valores y añade
  salto de página por bloque (un A4 por par de caja y por palet). En la hoja
  de cajas la plantilla trae DOS etiquetas modelo: el bloque modelo a
  capturar es el par completo.
- **`ApcEtiquetasGenerador implements GeneradorEtiquetasCliente`**
  (`claveCliente = "APC"`): agrupa cajas físicas por destinación, resuelve
  el layout, genera un excel por destinación soportada y acumula avisos.
  Destinación sin plantilla → aviso "sin etiquetas implementadas: se omite"
  y se sigue con el resto (regla del proyecto: nunca bloquear).
  `camposRequeridos(...)` devuelve lista vacía.

### Refactor compartido

- Extraer `BloqueModelo` (capturar filas/estilos/altos/merges + copiar en
  offset) de `AmiEtiquetasExcelBuilder` a una clase paquete-privada
  compartida (p. ej. `BloqueEtiquetaModelo`) en `service/etiquetas/`.
  AMI pasa a usarla; sus tests existentes deben seguir en verde.

### Cambio de interfaz

`GeneradorEtiquetasCliente.generar(...)` necesita los **palets**, que hoy no
recibe. Se cambia la firma para recibir `List<EnvioImportado.DestinoImportado>`
(destino + palets, lo que el controller ya tiene) en lugar de
`List<DestinoData>`. `camposRequeridos(List<DestinoData>)` no cambia. AMI
ignora los palets. `PackingListController.destinosDelEnvio()` se ajusta (o se
pasa directamente `importado.getDestinos()`).

### Web (Paso 2, `/etiquetas`)

- **Fix necesario**: hoy el botón "Generar etiquetas" se deshabilita si
  `campos` está vacío. Con APC (0 campos) debe habilitarse cuando el envío
  tenga **≥1 destinación soportada**. El texto "Ninguna destinación..."
  solo debe salir cuando de verdad no haya ninguna soportada.
- Sin campos que pedir, la vista APC muestra la lista de destinaciones con
  sus badges (disponible / se omitirá) y el botón Generar directamente.
- Los clientes sin generador siguen mostrando "en desarrollo" como hasta
  ahora (sin cambios).

### Recursos

Copiar las 4 plantillas de `docs/Etiquetas cajas/` a
`src/main/resources/client-labels/`:
`apc-etiquetas-japan.xlsx`, `apc-etiquetas-korea.xlsx`,
`apc-etiquetas-usa.xlsx`, `apc-etiquetas-wh-crosslog.xlsx`.
Misma regla que el resto de plantillas: no editarlas sin revisar su builder.

## Manejo de errores

- Destinación no soportada → aviso, se omite, se generan las demás.
- Peso de caja incompleto → etiqueta con peso en blanco + `cajasPendientes`.
- Peso de palet incompleto → en blanco + aviso.
- Cajas sin palet asignado → no aparecen en ninguna etiqueta de palet; aviso.
- Hoja esperada ausente en la plantilla → `IllegalStateException` con nombre
  de hoja y fichero (error de programación/plantilla, no de datos).

## Tests

Patrón del proyecto (JUnit 5 puro, `new`, reabrir el `.xlsx` con POI):

- `ApcEtiquetasGeneradorTest` / `ApcEtiquetasExcelBuilderTest`:
  - genera desde `envio-apc.json` (destino IVRY → comprueba el aviso de
    destinación sin plantilla) y desde un envío sintético con destinaciones
    soportadas;
  - comprueba celdas reales: Reference, Colour, Size (bolso `U` y cinturón),
    Pieces by size, Colisage `n / total`, `NOT FOUND` en Order N° y
    Livraison, peso con formato `x,xx Kg`;
  - hoja de palet: nº de cajas del palet y peso = suma + tara (y 10 kg por
    defecto sin tara);
  - ambas hojas presentes en el libro generado, saltos de página por bloque.
- Tests AMI existentes en verde tras extraer `BloqueEtiquetaModelo`.

## Fuera de alcance (iteraciones futuras)

- Order N° real desde el excel de pedido de APC.
- Livraison code pedido al usuario (probablemente uno por destinación).
- "Etiqueta de etiqueta" (funcionalidad paralela, ignorada aquí).
- Etiquetas de palet de AMI.
