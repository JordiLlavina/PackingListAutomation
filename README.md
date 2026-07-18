# Packing List Automation

Generador de packing lists en Excel para clientes (actualmente AMI bolsos), a partir de datos en JSON, usando Java 17 + Spring Boot + Apache POI. Rellena la plantilla real del cliente respetando estilos, fórmulas y estructura.

## Requisitos

- **Java 17** (probado con Temurin 17)
- **Maven 3.9+**

Comprueba que los tienes:

```
java -version
mvn -version
```

## Cómo probar rápido (el `Main`)

Desde la raíz del proyecto:

```
mvn -q compile exec:java
```

Esto compila y ejecuta [`Main.java`](src/main/java/com/puntotres/packinglist/Main.java), que corre el **flujo completo** con el JSON de prueba real [`client-packinglist/packing_list_ami_test.json`](src/main/resources/client-packinglist/packing_list_ami_test.json):

1. Importa el envío (expande los rangos de cajas y valida `cantidadTotal`).
2. Asigna palets a cada caja según los rangos.
3. Intenta inferir los pesos que faltan (con las taras de `application.yml`).
4. Genera **un excel por destinación + referencia + color** en `target\PKL_*.xlsx`.

Por consola salen los avisos de validación, las cajas sin palet y los excels con pesos pendientes. Para probar con otros datos, edita ese JSON (o apunta `JSON_PRUEBA` en `Main.java` a otro fichero) y relanza el comando. Formato del JSON de envío:

```json
{
  "cliente": "AMI",
  "destinos": [
    {
      "destino": "PARIS",
      "palets": [ { "palet": 1, "cajaInicio": 1, "cajaFin": 12 } ],
      "referencias": [
        {
          "referencia": "USL728.AL217.001", "color": "NOIR",
          "medidaCaja": "60x40x40", "pedido": "07685", "cantidadTotal": 1597,
          "cajas": [
            { "cajaInicio": 1, "cajaFin": 30, "unidadesPorCaja": 50 },
            { "caja": 31, "unidades": 50 }
          ]
        }
      ]
    }
  ]
}
```

- Las cajas admiten dos formas: rango (`cajaInicio`/`cajaFin`/`unidadesPorCaja`) o caja suelta (`caja`/`unidades`).
- `medidaCaja` en formato `LxWxH` en **centímetros**; los pesos no vienen en el JSON (se infieren o quedan pendientes).
- Alternativa desde VS Code: con el *Extension Pack for Java*, pulsa el botón **Run** que aparece sobre el método `main`.

## Ejecutar los tests

```
mvn test
```

Corre toda la suite (importación del JSON de envío, asignación de palets, inferencia de pesos, generación por modelo+color y carga de configuración). El test end-to-end `flujoCompletoGeneraExcelsAbribles` deja además excels reales en `target\` generados con el flujo completo.

Un solo test:

```
mvn test -Dtest=PackingListGenerationServiceTest
```

## Estructura del proyecto

```
src/main/resources/
    application.yml                  Configuración (tabla de taras por tamaño de caja)
    client-packinglist/              Plantillas Excel de cliente (AMI bags/belts, ACKERMANN, APC)
src/main/java/com/puntotres/packinglist/
    Main.java                        Prueba manual: JSON de envío -> excels en target/
    PackingListApplication.java      Arranque Spring Boot (sin web todavía)
    PackingListData.java             Datos de UN packing list (entrada del builder)
    AmiExcelBuilder.java             Rellena la plantilla AMI bags con POI
    model/
        EnvioInput.java              Estructura del JSON de entrada (envío completo)
        DestinoData.java             Todas las cajas de una destinación
        CajaData.java                Una caja (pesos nullable, palet asignable)
        PaletData.java               Un palet con su rango de cajas
        DatosEnvio.java              Cabecera que no sale de las imágenes (factura, fechas...)
    service/
        EnvioImportService.java      JSON de envío -> dominio (expande rangos, valida)
        PaletAssignmentService.java  Cruza cajas con rangos de palet
        WeightInferenceService.java  Infiere pesos que faltan (taras del application.yml)
        PackingListGenerationService.java  Un excel por referencia+color, con AmiExcelBuilder
    config/
        TaraProperties.java          Tabla de taras cargada desde application.yml
```

## Flujo de datos (visión general)

1. **Entrada**: imágenes de distribución de palets + detalle por destinación, convertidas a un JSON de envío (`EnvioInput`) por un modelo de visión.
2. **`EnvioImportService`**: expande los rangos de cajas a una `CajaData` por caja física y valida (suma de unidades vs `cantidadTotal`, cajas duplicadas) devolviendo avisos, sin bloquear.
3. **`PaletAssignmentService`**: asigna a cada caja su palet según el rango de números. Cajas fuera de rango quedan marcadas como *sin palet* (nunca falla en silencio).
4. **`WeightInferenceService`**: para cada referencia, calcula el peso por unidad a partir de las cajas con peso bruto conocido (`(bruto − tara) / cantidad`, promediado) y completa las cajas sin peso. Sin tara o sin cajas conocidas → pesos a `null`, pendientes de revisión.
5. **`PackingListGenerationService`**: agrupa las cajas por **referencia (modelo) + color** y genera un excel por grupo con `AmiExcelBuilder`. Las cajas con pesos pendientes no bloquean: el excel sale con esas celdas vacías y se devuelven listadas para la revisión.

## Configuración: taras por tamaño de caja

En [`application.yml`](src/main/resources/application.yml):

```yaml
packing-list:
  taras:
    "[60x40x40]": 1.6
    "[60x40x30]": 1.2
```

**Para soportar un tamaño de caja nuevo basta con añadir una línea aquí** (clave entre `[]` y comillas, valor = kg del embalaje vacío). No hay que tocar código. Las claves se normalizan: `"60X40X40 "` de una imagen casa con `"60x40x40"`.

## Plantillas de cliente

Las plantillas viven en `src/main/resources/client-packinglist/`. El builder actual usa **`ami-bags-packing-list-template.xlsx`** (hoja `STANDARD PKL H26`): cabecera fija, leyenda de tallas (filas 14–18), encabezados (fila 19), fila modelo con estilos (fila 20), totales (fila 21) y bloque SUM UP (filas 23–28). El builder clona la fila modelo por cada caja, desplaza totales/resumen hacia abajo y reescribe las fórmulas `SUM` sobre el rango real.

> ⚠️ No borres ni edites la fila modelo ni los encabezados de la plantilla: el builder depende de esas posiciones. Si el cliente cambia la plantilla, hay que revisar las coordenadas en `AmiExcelBuilder`.

El resto de plantillas (AMI belts, ACKERMANN, APC) son de clientes futuros, aún sin builder.

## Estado y próximos pasos

- ✅ Generación AMI bags validada contra la plantilla real (fórmulas, estilos, fechas Excel, volumen).
- ✅ Asignación de palets, inferencia de pesos y generación multi-pedido, con tests.
- ⬜ Endpoint REST para recibir los JSON (Spring Web).
- ⬜ Pantalla de revisión (pesos pendientes, cajas sin palet) antes de generar. Debe mostrar como popup/alerta los avisos de `EnvioImportado.avisos` y `ResultadoAsignacion.avisos` (buscar `TODO(web-ui)` en el código).
- ⬜ Builders para el resto de clientes (belts, ACKERMANN, APC) — se generalizará con una interfaz común cuando haya detalle de cada cliente.
