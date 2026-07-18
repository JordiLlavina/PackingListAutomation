# Arquitectura

Este documento explica **cómo funciona el código por dentro**: el recorrido completo de los datos desde el JSON de entrada hasta el Excel final, por qué está dividido en estas piezas concretas, y las decisiones de diseño detrás de cada una. Para instrucciones de uso (cómo ejecutar, cómo probar), ver [README.md](README.md).

## El problema en una frase

Convertir un envío (varias destinaciones, cada una con varias referencias/colores y cajas numeradas) en **un Excel AMI por cada combinación destinación + referencia + color**, con los pesos que falten inferidos automáticamente cuando sea posible.

## Mapa del flujo de datos

```
JSON de entrada (EnvioInput)
        │
        ▼  EnvioImportService.importar()
EnvioImportado (DestinoData + List<PaletData>, por destinación)
        │
        ▼  PaletAssignmentService.asignar()           (por destinación)
CajaData.numeroPalet relleno + ResultadoAsignacion (avisos)
        │
        ▼  WeightInferenceService.inferirPesosPorReferencia()
CajaData.pesoNetoKg / pesoBrutoKg rellenos donde se pudo inferir
        │
        ▼  PackingListGenerationService.generarPorModeloYColor()
List<ExcelGenerado>  (uno por referencia+color, con AmiExcelBuilder)
```

Cada flecha es un `@Service` de Spring, sin estado, que se puede llamar independientemente. [`Main.java`](src/main/java/com/puntotres/packinglist/Main.java) es quien los encadena a mano hoy (no hay endpoint web todavía); mañana ese papel lo hará un controlador REST.

## Por qué hay dos modelos de datos distintos

Esta es la decisión de diseño más importante del proyecto, así que merece explicación aparte.

**`EnvioInput`** ([model/EnvioInput.java](src/main/java/com/puntotres/packinglist/model/EnvioInput.java)) es un calco literal del JSON tal como lo produce el modelo de visión que lee las imágenes: mismos nombres de campo, misma jerarquía anidada, incluida la ambigüedad de que una caja puede venir como rango (`cajaInicio/cajaFin/unidadesPorCaja`) o como caja suelta (`caja/unidades`). Este modelo es **frágil a propósito**: si el prompt del modelo de visión cambia de formato, solo hay que tocar esta clase.

**`DestinoData` / `CajaData` / `PaletData`** ([model/](src/main/java/com/puntotres/packinglist/model/)) son el modelo de dominio: una caja física por fila, ya expandida, ya numerada, lista para razonar sobre ella (asignar palet, inferir peso, agrupar). Todo el resto del código (los tres servicios) trabaja exclusivamente contra este modelo y no sabe nada de cómo era el JSON original.

`EnvioImportService` es la única pieza que conoce ambos mundos: traduce de uno a otro y es donde vive toda la lógica de "cómo interpretar lo que dijo la imagen" (expandir un rango, decidir cantidad, etc.). Esta separación significa que si mañana cambia el formato del JSON de entrada (o hay que soportar un segundo formato), **solo se toca este servicio** — `PaletAssignmentService`, `WeightInferenceService` y `PackingListGenerationService` no se enteran.

Hay un tercer modelo, **`PackingListData`** ([PackingListData.java](src/main/java/com/puntotres/packinglist/PackingListData.java)), que es la entrada específica de `AmiExcelBuilder`: literalmente los campos que van a parar a celdas de la plantilla AMI. `PackingListGenerationService.mapear()` es quien traduce de `CajaData` (dominio) a `PackingListData.Caja` (formato de un cliente concreto). Ver más abajo por qué esta tercera capa importa.

## Los servicios, uno a uno

### `EnvioImportService` — JSON → dominio

[service/EnvioImportService.java](src/main/java/com/puntotres/packinglist/service/EnvioImportService.java)

Por cada destinación del envío, recorre sus referencias y expande las entradas de caja:

- Un rango `{cajaInicio: 1, cajaFin: 30, unidadesPorCaja: 50}` se expande a 30 `CajaData`, cada una con `cantidad = 50`.
- Una caja suelta `{caja: 31, unidades: 50}` se expande a 1 `CajaData`.

Los pesos **nunca** vienen del JSON (las imágenes no los traen); toda `CajaData` nace con `pesoNetoKg`/`pesoBrutoKg` a `null`.

**Validación sin bloquear**: si la suma de unidades de las cajas de una referencia no coincide con el `cantidadTotal` declarado, o si un número de caja se repite dentro de una destinación, se añade un texto a `EnvioImportado.avisos` — pero la importación sigue. La regla de todo el proyecto es *"nunca falles en silencio, pero tampoco bloquees por una inconsistencia de datos que un humano puede resolver"*. Estos avisos hoy se imprimen por consola desde `Main`; están marcados con `TODO(web-ui)` porque cuando exista la pantalla de revisión deben aparecer como alerta.

### `PaletAssignmentService` — cruza cajas con palets

[service/PaletAssignmentService.java](src/main/java/com/puntotres/packinglist/service/PaletAssignmentService.java)

Para cada caja de una destinación, busca entre los palets de esa misma destinación cuál tiene un rango `[cajaInicio, cajaFin]` que contenga su `numeroCaja`, y le asigna ese `numeroPalet`. Dos matices:

- **Rangos solapados**: si una caja encaja en más de un palet, se queda con el primero y se registra un aviso — nunca se elige "el mejor" en silencio.
- **Sin ningún rango que case**: `numeroPalet` se deja en `null` y la caja se añade a `ResultadoAsignacion.cajasSinPalet`, para que la revisión la pueda señalar.

La asignación se escribe directamente sobre los objetos `CajaData` de entrada (mutación intencionada, no se devuelve una copia) — es el mismo patrón que usa `WeightInferenceService` a continuación.

### `WeightInferenceService` — completa los pesos que faltan

[service/WeightInferenceService.java](src/main/java/com/puntotres/packinglist/service/WeightInferenceService.java)

La idea: dentro de una misma referencia, todas las cajas contienen el mismo producto, así que el **peso neto por unidad** debería ser el mismo en todas. Si algunas cajas ya tienen su peso bruto (a mano, o porque sí venía en la imagen), se puede usar ese dato para inferir el peso del resto:

```
peso_unitario = promedio( (pesoBruto - tara) / cantidad )   sobre las cajas con bruto conocido
```

y luego, para cada caja sin peso:

```
pesoNetoKg   = cantidad * peso_unitario
pesoBrutoKg  = pesoNetoKg + tara
```

La **tara** (peso del cartón vacío) depende del tamaño de caja y viene de `TaraProperties`, no está en el código Java — ver la sección de configuración más abajo.

Casos que se quedan en `null` a propósito (nunca se inventa un número):
- El tamaño de caja no tiene tara conocida en `application.yml`.
- Ninguna caja de la referencia tiene peso bruto conocido (no hay de dónde partir).

`inferirPesosPorReferencia(List<CajaData>)` es el punto de entrada normal: agrupa internamente por `referencia` y llama a `inferirPesos` grupo a grupo, para no mezclar el peso unitario de un modelo con el de otro.

### `PackingListGenerationService` — genera los excels

[service/PackingListGenerationService.java](src/main/java/com/puntotres/packinglist/service/PackingListGenerationService.java)

Agrupa las `CajaData` de una destinación por la clave `referencia + "|" + color` y, por cada grupo, construye un `PackingListData` (mapeando campo a campo) y llama a `AmiExcelBuilder.generar(...)`.

**Las cajas con pesos pendientes no bloquean la generación.** Esto es deliberado: es mejor tener el Excel con las celdas U/V vacías y una lista clara de qué falta, que no tener Excel en absoluto. `ExcelGenerado.cajasPendientes` lleva esa lista; `tienePesosPendientes()` es el flag rápido para la UI.

El nombre de fichero se deriva de destinación + referencia + color, saneado con una regex que sustituye espacios y caracteres no válidos en nombres de fichero de Windows (`\/:*?"<>|`) por `_`.

### `AmiExcelBuilder` — la capa POI

[AmiExcelBuilder.java](src/main/java/com/puntotres/packinglist/AmiExcelBuilder.java)

Este es el único servicio que sabe leer y escribir `.xlsx` con Apache POI, y el único acoplado a la plantilla concreta de AMI. Recibe un `PackingListData` (ya en el vocabulario del Excel: temporada, cabecera, lista de filas) y no sabe nada de destinaciones, palets ni inferencia de pesos.

Mecánica interna (plantilla: `client-packinglist/ami-bags-packing-list-template.xlsx`, hoja `STANDARD PKL H26`):

1. Escribe la cabecera fija (proveedor desde constantes, factura y fechas desde `PackingListData`).
2. Toma la **fila modelo** (fila 20, la única que trae los estilos correctos en la plantilla) y la clona tantas veces como cajas haya, usando `Sheet.shiftRows` para desplazar hacia abajo la fila de totales y el bloque resumen y dejar hueco.
3. Por cada fila de caja, escribe una fórmula `SUM(G{fila}:R{fila})` en la columna de cantidad total — igual que hace la plantilla original a mano.
4. Reescribe la fila de totales con `SUM` sobre el rango real de filas usadas (no un rango fijo).
5. Reescribe el bloque "SUM UP" (cantidad total, nº de cajas, peso bruto, peso neto, volumen) apuntando a la fila de totales ya desplazada. El volumen se calcula en Java (no es fórmula Excel) a partir de las dimensiones de cada caja.
6. Marca el workbook con `setForceFormulaRecalculation(true)` para que Excel recalcule las fórmulas al abrir (POI no ejecuta fórmulas, solo las escribe).

Si `pesoNetoKg`/`pesoBrutoKg` son `null`, esas celdas simplemente no se escriben (quedan en blanco) — así es como se ve un "peso pendiente" en el Excel final.

Esta clase existe separada de `PackingListGenerationService` a propósito: es la pieza que **cambiará por cliente**. Ver la siguiente sección.

## Por qué `PackingListData` es un modelo aparte (y qué pasará cuando lleguen más clientes)

Ahora mismo solo hay un cliente (AMI bolsos) y un builder (`AmiExcelBuilder`), así que la frontera entre "dominio" (`CajaData`) y "formato de cliente" (`PackingListData`) puede parecer redundante. No lo es: ya hay plantillas de otros clientes en `client-packinglist/` (belts, ACKERMANN, APC) esperando su turno, y cada cliente pedirá datos distintos — por ejemplo APC necesita dirección y nombre de empresa distintos por destinación, algo que AMI no tiene.

El plan (todavía no implementado, ver README → "Próximos pasos") es que `AmiExcelBuilder` se convierta en la primera implementación de una interfaz común (`ClientPackingListBuilder` o similar), y que `PackingListGenerationService` elija el builder según el cliente del envío, sin tener que saber nada de las particularidades de cada plantilla. `EnvioInput` ya lleva un campo `cliente` pensado para esto, aunque de momento no se usa. Los datos específicos de cada cliente (como las direcciones de APC) seguirán el mismo patrón que las taras: configuración en `application.yml`, no código.

## Configuración externa: `TaraProperties`

[config/TaraProperties.java](src/main/java/com/puntotres/packinglist/config/TaraProperties.java) + [application.yml](src/main/resources/application.yml)

Es una clase `@ConfigurationProperties(prefix = "packing-list")` con un único campo `Map<String, Double> taras`. Spring la rellena automáticamente al arrancar leyendo el yml:

```yaml
packing-list:
  taras:
    "[60x40x40]": 1.6
    "[60x40x30]": 1.2
```

Las claves se normalizan (minúsculas, sin espacios) tanto al cargar como al consultar (`taraPara(...)`), para que un tamaño de caja que venga de una imagen como `"60X40X40 "` case con la entrada `"60x40x40"` del yml sin esfuerzo. Esto significa que **soportar un tamaño de caja nuevo es una línea de configuración**, nunca un cambio de código ni un redeploy con recompilación — motivo por el que el usuario pidió explícitamente esta clase en vez de un `Map` hardcodeado.

## Por qué los pesos son `Double` y no `double`

En `CajaData` y en `PackingListData.Caja`, `pesoNetoKg`/`pesoBrutoKg` son el tipo objeto `Double`, no el primitivo `double`. Es deliberado: un primitivo no puede representar "desconocido", solo `0.0`, y `0.0` es un peso real y ambiguo con "no lo sé todavía". `null` sí distingue ambos casos, y es lo que permite que `WeightInferenceService` sepa qué cajas necesitan inferencia y que `AmiExcelBuilder` sepa qué celdas dejar en blanco.

## Spring Boot: qué hace hoy y qué no hace todavía

El proyecto usa `spring-boot-starter` (núcleo) pero **no** `spring-boot-starter-web**: no hay ningún endpoint HTTP. Lo que aporta Spring hoy es:

- **Inyección de dependencias**: los tres servicios y `AmiExcelBuilder` son `@Service`; en producción Spring los instanciaría y conectaría solos. Hoy, en `Main.java`, se instancian a mano con `new` porque no hay contexto de Spring arrancado (es una clase de prueba manual, no un componente gestionado).
- **`@ConfigurationProperties`**: la lectura de `application.yml` descrita arriba.
- **`PackingListApplication`** ([PackingListApplication.java](src/main/java/com/puntotres/packinglist/PackingListApplication.java)) es el punto de arranque `@SpringBootApplication`, hoy sin ningún `@RestController` — solo sirve para que `mvn test` pueda levantar un contexto Spring real en `PackingListApplicationTest` y verificar que `TaraProperties` se cablea bien.

Cuando llegue el endpoint REST (ver README), `Main.java` dejará de ser necesario para probar manualmente porque se podrá probar contra el endpoint real; hoy sigue siendo el atajo más rápido para ejecutar el flujo completo sin levantar un servidor.

## Tests: qué cubre cada uno

Todos los tests son JUnit 5 puro instanciando los servicios con `new` (sin contexto Spring), salvo uno explícito:

- [`EnvioImportServiceTest`](src/test/java/com/puntotres/packinglist/service/EnvioImportServiceTest.java) — corre contra el JSON real de ejemplo (`client-packinglist/packing_list_ami_test.json`), no contra fixtures inventados: expansión de rangos, cajas sueltas, y los dos avisos de `cantidadTotal` que ese JSON contiene a propósito.
- [`PaletAssignmentServiceTest`](src/test/java/com/puntotres/packinglist/service/PaletAssignmentServiceTest.java) — asignación normal, caja fuera de rango, destinación sin palets, rangos solapados.
- [`WeightInferenceServiceTest`](src/test/java/com/puntotres/packinglist/service/WeightInferenceServiceTest.java) — promedio con varias cajas conocidas, tamaño sin tara, referencia sin ninguna caja conocida, normalización de la clave de tara, agrupación por referencia.
- [`PackingListGenerationServiceTest`](src/test/java/com/puntotres/packinglist/service/PackingListGenerationServiceTest.java) — agrupación por modelo+color reabriendo el `.xlsx` generado con POI para comprobar celdas reales; un test de flujo completo (`flujoCompletoGeneraExcelsAbribles`) que encadena los tres servicios y deja los excels en `target/` para inspección manual.
- [`PackingListApplicationTest`](src/test/java/com/puntotres/packinglist/PackingListApplicationTest.java) — el único que levanta contexto Spring (`@SpringBootTest`), para verificar que `application.yml` se enlaza de verdad en `TaraProperties` (no solo que la clase compile).

## Estructura de ficheros

```
src/main/java/com/puntotres/packinglist/
    Main.java                        Orquesta el flujo completo a mano (prueba manual)
    PackingListApplication.java      Arranque Spring Boot (sin web todavía)
    PackingListData.java             Vocabulario de AmiExcelBuilder (formato de cliente)
    AmiExcelBuilder.java             Única clase que toca Apache POI / la plantilla AMI
    model/
        EnvioInput.java              Calco del JSON de entrada (frágil a propósito)
        DestinoData.java             Dominio: cajas de una destinación
        CajaData.java                Dominio: una caja física
        PaletData.java                Dominio: un palet con su rango de cajas
        DatosEnvio.java              Cabecera que no sale de las imágenes
    config/
        TaraProperties.java          Tabla de taras, desde application.yml
    service/
        EnvioImportService.java      JSON → dominio (expande rangos, valida)
        PaletAssignmentService.java  Cruza cajas con rangos de palet
        WeightInferenceService.java  Infiere pesos que faltan
        PackingListGenerationService.java  Dominio → Excel, uno por modelo+color
        EnvioImportado.java          DTO de resultado de EnvioImportService
        ResultadoAsignacion.java     DTO de resultado de PaletAssignmentService
        ExcelGenerado.java           DTO de resultado de PackingListGenerationService
```

Patrón que se repite en los tres servicios: cada uno devuelve (o recibe) un DTO de resultado dedicado (`EnvioImportado`, `ResultadoAsignacion`, `ExcelGenerado`) en vez de una lista suelta o un booleano, precisamente para poder llevar los avisos/pendientes junto al resultado principal sin recurrir a excepciones para casos que no son errores de programación, sino datos del mundo real que no cuadran.
