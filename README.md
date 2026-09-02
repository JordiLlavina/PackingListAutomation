# Packing List Automation

Generador de packing lists en Excel para los clientes de Punto Tres (proveedor de marroquinería). A partir de un **JSON de envío** — pegado a mano o **extraído de fotos/PDFs de las hojas manuscritas del almacén** con la API de Anthropic — asigna palets, infiere los pesos que faltan y genera un `.xlsx` por grupo rellenando la **plantilla real de cada cliente** con Apache POI.

Alrededor de ese flujo central hay tres salidas más:

- **Volcado de albarán para el ERP** (un excel con las líneas del envío).
- **Etiquetas de caja y de palet** (AMI y APC), con códigos de barras EAN-13 y Code 128.
- Dos flujos independientes del envío: **etiquetas de artículo** (desde el excel de pedido del cliente) y **procesado de escandallos ICSUITE** (excels del ERP → un libro con una hoja por escandallo).

Todo se maneja desde un asistente web (Spring Boot + Thymeleaf). Código y documentación en **español** (nombres de clases en inglés; javadoc, comentarios y UI en español).

## Requisitos

- **Java 17** (probado con Temurin 17)
- **Maven 3.9+**
- *(Opcional)* Variable de entorno `ANTHROPIC_API_KEY` — solo para el modo CLAUDE de la pantalla de entrada (extracción desde fotos/PDFs). Sin ella la aplicación arranca igual y el resto de modos funciona.

Dependencias clave (las versiones las gestiona el parent de Spring Boot salvo donde se indica, ver [pom.xml](pom.xml)):

| Dependencia | Para qué |
|---|---|
| Spring Boot 3.5 (web, thymeleaf, validation) | Asistente web y formularios |
| Apache POI 5.4 (`poi-ooxml`) | Leer/escribir los `.xlsx` |
| Jackson | Parseo del JSON de envío |
| SDK Java de Anthropic | Extracción desde fotos/PDFs |
| Barcode4J | Códigos de barras de las etiquetas |

## Puesta en marcha en local desde cero

```bash
# 1. Clonar
git clone git@github.com:JordiLlavina/PackingListAutomation.git
cd PackingListAutomation

# 2. Comprobar el entorno
java -version    # debe ser 17
mvn -version     # 3.9+

# 3. Compilar y pasar la suite de tests
mvn test

# 4. Levantar el asistente web
mvn spring-boot:run
```

Con el servidor levantado, abrir **http://localhost:8080** (redirige al menú `/menu`). Desde ahí:

- `/packing-list` — asistente de packing lists en 3 pantallas: **entrada** (pegar JSON o subir fotos/PDFs) → **revisión** (avisos y tabla editable) → **resultados** (descarga individual, ZIP, volcado ERP y etiquetas de caja).
- `/etiquetas-articulo` — etiquetas de artículo a partir del excel de pedido (sin envío).
- `/escandallos` — procesado de escandallos ICSUITE (sin envío ni cliente).

Para usar el **modo CLAUDE** (fotos/PDFs → JSON) hay que exportar la clave antes de arrancar. En PowerShell:

```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."   # solo esta sesión de terminal
mvn spring-boot:run
```

(`setx ANTHROPIC_API_KEY "sk-ant-..."` la deja persistente para terminales nuevos.)

> ⚠️ **Windows**: matar `mvn spring-boot:run` con Ctrl+C no siempre mata el proceso `java` hijo y el puerto 8080 queda ocupado. Liberarlo: `netstat -ano | findstr :8080` y `taskkill /F /PID <pid>`.

### Prueba manual sin web

```bash
mvn -q compile exec:java
```

Ejecuta [Main.java](src/main/java/com/puntotres/packinglist/Main.java) con el JSON de prueba [packing_list_ami_test.json](src/main/resources/client-packinglist/packing_list_ami_test.json) y deja los excels en `target/`. Se mantiene a propósito como prueba manual rápida del pipeline.

### Tests

```bash
mvn test                                          # toda la suite
mvn test -Dtest=PackingListGenerationServiceTest  # una clase
```

El test end-to-end `flujoCompletoGeneraExcelsAbribles` deja excels reales en `target/` para inspección manual, y `EscandallosFlujoRealTest` deja `target/Escandallos ICSUITE.xlsx`.

## Estructura del proyecto

```
src/main/java/com/puntotres/packinglist/
    Main.java                        Prueba manual: JSON de envío → excels en target/
    PackingListApplication.java      Arranque Spring Boot
    PackingListData.java             Vocabulario de la plantilla Excel de un cliente
    AmiExcelBuilder.java, AmiLayout.java   Builder de la plantilla AMI (formato propio del cliente)
    model/                           Dominio: EnvioInput (calco del JSON), CajaData/CajaFisica,
                                     DestinoData, PaletData, DatosEnvio, VolcadoErpData...
    service/                         Pipeline del envío: importación, destinos padre de APC,
                                     asignación de palets, completado de pedido APC, inferencia
                                     de pesos, generación de excels (AMI/APC/genérico), Livraison
                                     code, volcado ERP, extracción con Claude y su validación
    service/etiquetas/               Etiquetas de caja y de palet (AMI y APC) + códigos de barras
                                     y piezas compartidas con las etiquetas de artículo
    service/etiquetasarticulo/       Etiquetas de artículo (flujo independiente; solo AMI)
    service/escandallos/             Escandallos ICSUITE (flujo totalmente independiente)
    web/                             Asistente de 3 pantallas; estado del envío en sesión HTTP
                                     (EnvioEnCurso), tabla de revisión compactada
    config/                          Taras, catálogo de clientes (ClientesProperties),
                                     TipoPlantilla (AMI, APC, GENERIC)

src/main/resources/
    application.yml                  Taras por tamaño de caja + catálogo de clientes + límites
                                     de subida + hash de estáticos
    client-packinglist/              Plantillas Excel de packing list (AMI bags/belts, APC, genérica)
    client-labels/                   Plantillas de etiquetas de caja (AMI y las 5 de APC)
    templates/, static/              Vistas Thymeleaf y CSS
```

**Multi-cliente**: `GeneradorPackingListCliente` es la interfaz; `AmiExcelBuilder`, `ApcExcelBuilder` y `GenericoExcelBuilder` la implementan y `PackingListGenerationService` despacha según la plantilla del cliente. **Añadir un cliente de plantilla GENERIC es solo configuración** en `application.yml` (nombre-legal + direccion-entrega), sin tocar Java.

## Formato del JSON de envío

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
- `medidaCaja` en formato `LxWxH` en **centímetros**. Puede faltar (no bloquea: la caja no suma volumen y el desglose la rotula `?`).
- Los pesos normalmente **no vienen en el JSON** y se infieren con las taras de `application.yml`. Una caja puede traer `pesoBruto` (kg) opcional cuando la hoja lo indica; el neto nunca viene.
- El JSON extraído por Claude añade dos campos que el pegado a mano no suele traer: `avisos` (dudas de lectura) y `resumenPalets` (recuentos declarados en la hoja, que se contrastan con lo extraído).

El detalle de qué campos usa cada cliente está en [docs/Packing Lists/campos-json-por-cliente.md](docs/Packing%20Lists/campos-json-por-cliente.md).

## Configuración (`application.yml`)

- **Taras por tamaño de caja** (`packing-list.taras`): kg del embalaje vacío por clave `LxWxH`. **Un tamaño de caja nuevo es una línea aquí**, sin tocar código. Las claves se normalizan (`"60X40X40 "` casa con `"60x40x40"`). Es un dato del almacén, no una constante: los tamaños aún sin pesar están puestos a `0.01`.
- **Catálogo de clientes** (`packing-list.clientes`): nombre, plantilla (AMI/APC/GENERIC), y por cliente lo suyo — si sube excel de pedido (`pedido-cliente`), las destinaciones de APC con sus direcciones, abreviaturas del Livraison code y destinos hijo.
- Límites de subida multipart (las fotos del móvil superan el 1MB por defecto de Spring) y hash del contenido en los nombres de los estáticos (sin él, el navegador reutiliza el CSS viejo tras cada cambio).

La única variable de entorno es `ANTHROPIC_API_KEY` (opcional, ver arriba).

## Los JSON de ejemplo son inventados

Los `envio-*.json` de [src/test/resources/ejemplos/](src/test/resources/ejemplos/README.md), el `packing_list_ami_test.json` del `Main` y los JSON recortados en los documentos de `docs/` son **fixtures generados por IA**: envíos escritos a ojo, sin conocer la realidad del almacén, para ejercitar el flujo y hacer pruebas visuales. **No son datos de ningún cliente** y no hay que deducir de ellos cómo son los datos reales.

Los únicos **datos reales** de cliente son los dos excels de pedido: `docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx` (AMI) y `docs/Etiquetas cajas/APC_PEDIDO_FALL26.xlsx` (APC), ambos copiados a `src/test/resources/ejemplos/`. De los demás `.xlsx` de [docs/](docs/) lo real es la **maquetación**, no el contenido.

## Plantillas de cliente

Las plantillas de packing list viven en `src/main/resources/client-packinglist/` y las de etiquetas en `src/main/resources/client-labels/`. Todas tienen filas modelo y coordenadas de las que dependen sus builders:

> ⚠️ **No editar la fila modelo ni los encabezados de una plantilla sin revisar su builder** (`AmiExcelBuilder`/`AmiLayout`, `ApcExcelBuilder`, `GenericoExcelBuilder`; para etiquetas `AmiEtiquetaLayout` y `ApcEtiquetaLayout`, con tests que anclan las coordenadas).

## Documentación relacionada

- [CLAUDE.md](CLAUDE.md) — **la referencia más completa y al día** del proyecto: arquitectura, decisiones y trampas conocidas.
- [ARCHITECTURE.md](ARCHITECTURE.md) — recorrido interno del pipeline de dominio. ⚠️ Parcialmente desactualizado (no cuenta la web, la extracción ni los builders nuevos; lo que cuenta del pipeline sigue siendo válido).
- [ESTADO_ANTES_VACACIONES.md](ESTADO_ANTES_VACACIONES.md) — foto del estado del proyecto a 2026-08-08.
- [TODO](TODO) — pendientes (lo mantiene Jordi).
- [docs/](docs/) — ejemplos de plantillas por cliente, análisis de las hojas manuscritas y prompts de extracción.
