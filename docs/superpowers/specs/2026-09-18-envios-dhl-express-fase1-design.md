# Envíos por transportista — Fase 1: DHL Express (MyDHL API)

Fecha: 2026-09-18. Estado: diseño aprobado (los dos puntos de la sección 18 quedaron cerrados el 2026-09-18); plan en
`docs/superpowers/plans/2026-09-18-envios-dhl-express-fase1.md`. Se ejecuta con un subagente
nuevo por tarea y revisión entre tareas. El CSV real de la libreta está en `.gitignore`.

## 1. Qué se construye

Una sección nueva de la aplicación, **independiente del flujo de packing
lists**, para crear envíos internacionales con DHL Express desde la propia
app: elegir remitente y destinatario de una libreta, describir bultos y
mercancía para la aduana, tarifar, y crear el envío en DHL guardando la guía
y los PDF (etiqueta, factura comercial, waybill).

Puntotres envía entre Marruecos y España/Francia en los dos sentidos, y a
veces con la cuenta DHL de un tercero (el cliente paga el transporte o los
aranceles). Casi todo es aduanero, pero puede haber envíos nacionales.

**Fuera de esta fase**, pero con hueco preparado: otros transportistas (SEUR,
palets), seguimiento con notificaciones (Microsoft 365, Telegram), reports de
costes, login de Microsoft y recogidas programadas desde la API.

## 2. Lo que hay hoy en el repo y cómo encaja

- Spring Boot 3.5.3, Java 17, Thymeleaf, JPA sobre H2 en fichero
  (`./datos/packinglist`), `ddl-auto: update`, **sin Flyway ni Liquibase**,
  **sin Spring Security ni usuarios**, **sin ningún `@RestController`**.
- Convenciones: nombres de clase en inglés, campos, métodos, javadoc y UI en
  español; URLs en español; servicios que devuelven resultados con avisos en
  vez de lanzar; formularios sin JavaScript, con submits que recargan la
  pantalla; una sola hoja `estilo.css`; portada en `/menu` con tarjetas.
- La sección nueva sigue esas convenciones salvo donde el propio prompt fija
  nombres en inglés (`Shipment`, `DRAFT`, `/api/shipments`).

## 3. Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Usuarios | No se implementa autenticación ahora. Cada envío lleva un **responsable** obligatorio, elegido de una lista configurada en el yml (`envios.responsables`: Pol, Susana y Carme para las primeras pruebas). Se guarda como texto (nombre o correo corporativo), para que cuando llegue el login de Microsoft el campo se rellene solo y los reports por responsable sigan valiendo. |
| Sentido | Cuatro valores, confirmados. Respecto a **España**, sede central. Origen ES → `EXPORT`; destino ES → `IMPORT`; los dos ES → `DOMESTIC`; ninguno ES (Marruecos → Francia) → `CROSS_TRADE`. Se deduce **siempre** de los países de remitente y destinatario, en cada guardado, y no se edita: `DOMESTIC` y `CROSS_TRADE` salen con aviso, no bloquean. |
| Aduana | `isCustomsDeclarable` es independiente del sentido: hay aduana cuando origen y destino no están los dos en la lista `envios.paises-union-aduanera` del yml (la UE). ES → FR no declara; ES → MA sí. |
| Contrato DHL | `docs/Envios DHL/mydhl-openapi-3.3.2.yaml` (versión 3.3.2, 2026-09-06). Los mappers se escriben contra él. Lo que no esté ahí o dependa del entorno de test lleva `// TODO DHL:` con la duda concreta. |
| Libreta | `docs/Envios DHL/address-book-18-09-2026-09-23-12.csv`: export real de MyDHL+, 213 contactos, 44 columnas. El mapeo del importador se fija con él (sección 9). |
| PDFs | **En base de datos**, como el excel de temporada: la base viaja entera de un ordenador a otro y una ruta se rompe al mover la carpeta. |
| Migraciones | **Flyway con baseline** sobre el esquema que hoy crea Hibernate (sección 8). |
| Moneda | Todo en **EUR**: valores aduaneros, tarifa y coste. `envios.moneda: EUR` por si cambia. |
| Recogida | **Se pide al mensajero en la misma llamada** (`pickup.isRequested`), con un check "Pedir recogida" que nace **marcado**, una hora de cierre (HH:MM, `pickup.closeTime`) y un lugar (`pickup.location`, "Recepción" por defecto); sin `pickupDetails`, DHL recoge en la dirección del remitente. El `dispatchConfirmationNumber` de la respuesta se guarda en el envío. La fecha del envío es además la `plannedShippingDateAndTime` que exige la API. |

## 4. Arquitectura

```
com.puntotres.packinglist
  shipping/                          núcleo, no sabe nada de DHL
    config/      EnviosProperties (bloque envios.* del yml)
    model/       entidades JPA y enums (sección 5)
    carrier/     CarrierClient (puerto) y sus tipos: ProductOffer, QuoteResult,
                 CreatedShipment, CarrierDocument, AddressCheck, TrackingEvent,
                 CarrierException; CarrierClientRegistry (por CarrierCode)
    service/     ShipmentService (máquina de estados), DirectionResolver,
                 CustomsChecker, ContactService, BillingAccountService,
                 ArticleService, ShipmentReasonService, Notifier + NoOpNotifier
    importacion/ ImportadorLibretaMyDhl, ImportadorArticulosCsv, ResultadoImportacion
    web/         EnviosController, ContactosController, CuentasController,
                 MotivosController, ArticulosController (Thymeleaf)
    api/         ShipmentsApiController (/api/shipments), DTOs JSON
  carrier/dhl/                       adaptador MyDHL API
    DhlExpressClient (implements CarrierClient), DhlProperties, DhlRestClientFactory,
    dto/ (request/response tal cual el OpenAPI), DhlRequestMapper, DhlResponseMapper,
    DhlErrorTranslator, DhlLogInterceptor
```

Reglas de dependencia: `shipping` no importa nada de `carrier.dhl`; `carrier.dhl`
solo importa `shipping.carrier` y `shipping.model`. Los DTOs de DHL no salen de
su paquete. Añadir SEUR es un paquete `carrier.seur` y un valor en `CarrierCode`.

## 5. Modelo de datos

Campos en español, clases en inglés. Todas las entidades llevan `id` autonumérico
salvo que se diga otra cosa. Importes `BigDecimal`, pesos `Double` (con `null` =
desconocido, como en el resto del proyecto).

**Shipment**
- `transportista` (`CarrierCode`, solo `DHL_EXPRESS`), `sentido` (`Direction`),
  `aduanero` (boolean, deducido en cada guardado, no editable), `remitente` y `destinatario`
  (FK a `Contact`), `fechaRecogida` (`LocalDate`), `productoCodigo` y
  `productoNombre` (el elegido al tarifar; `P` = Express Worldwide no documentos).
- Recogida: `pedirRecogida` (boolean, por defecto `true`), `horaCierreRecogida` (texto `HH:MM`, por defecto `envios.hora-cierre-recogida`), `lugarRecogida` (por defecto `envios.lugar-recogida`), `numeroConfirmacionRecogida` (lo devuelve DHL como `dispatchConfirmationNumber`).
- `incoterm` (`Incoterm`, enum con los 16 valores del OpenAPI; por defecto `DAP`),
  `cuentaTransporte` y `cuentaAranceles` (FK a `BillingAccount`, la segunda opcional).
- `motivo` (FK a `ShipmentReason`, obligatorio), `cliente`, `temporada`,
  `referencia`, `notas`, `responsable` (obligatorio).
- `costeEstimado` + `moneda` (de la tarifa), `costeFacturado` (null en esta fase).
- `numeroGuia`, `urlSeguimiento`, `estado` (`ShipmentStatus`), `ultimoError`
  (texto legible), `requestId` (UUID, único, asignado al confirmar),
  `fechaCreacion`, `fechaActualizacion`, `version` (`@Version`, bloqueo optimista).
- Relaciones: `bultos` (`ShipmentPackage`, cascade), `lineasAduana`
  (`CustomsLineItem`, cascade), `documentos` (`ShipmentDocument`, cascade),
  `historial` (`ShipmentStatusHistory`).
- Tarifa vigente: `ofertas` (`RateOffer`, cascade, se borran al volver a DRAFT):
  código y nombre de producto, precio, moneda, fecha estimada de entrega.

**ShipmentPackage**: `peso` (kg), `largo`, `ancho`, `alto` (cm), `descripcion` opcional.

**CustomsLineItem**: `descripcion`, `codigoHs`, `cantidad`, `unidad` (por defecto
`PCS`), `valorUnitario`, `moneda`, `pesoNeto`, `pesoBruto`, `paisOrigen` (ISO-2),
`articulo` (FK opcional a `Article`, de dónde se cargó).

**ShipmentDocument**: `tipo` (`label`, `invoice`, `waybillDoc`, `shipmentReceipt`,
tal cual los `typeCode` de DHL), `formato` (`PDF`), `contenido` (`@Lob byte[]`).
DHL devuelve N documentos; guardar todos y no solo "etiqueta" y "factura" evita
perder el waybill que a veces hace falta imprimir.

**ShipmentStatusHistory**: `envio` (FK), `estadoAnterior`, `estadoNuevo`, `fecha`,
`responsable`, `detalle` (p. ej. el error de DHL o el producto elegido).

**Contact**
- `empresa`, `persona`, `direccion1`, `direccion2`, `direccion3`, `codigoPostal`,
  `ciudad`, `provincia`, `provinciaCodigo`, `pais` (ISO-2), `telefono` (con prefijo
  internacional), `email`, `tipo` (`BUSINESS` / `PRIVATE`, de la columna
  "Parte adicional Rol" PR/BU del CSV), `residencial` (boolean).
- Identificadores: `nifVat`, `eori`, `identificadorMarroqui` (ICE; no viene en el
  CSV, se teclea).
- `alias` (`@ElementCollection` de texto), `notas`.
- Por defecto para el formulario: `cuentaTransportePorDefecto`,
  `cuentaArancelesPorDefecto` (FK opcionales a `BillingAccount`), `incotermPorDefecto`.
  Salen del CSV (columnas "Por Defecto ...") y ahorran elegir la cuenta de AMI cada
  vez que se le envía algo.
- `activo` (baja lógica: un contacto usado en envíos no se borra).

**BillingAccount**: `numero` (único), `titular`, `propia` (boolean), `roles`
(`@ElementCollection` de `BillingRole`: `SHIPPER`, `PAYER`, `DUTIES`), `activa`.
La cuenta de `DHL_SHIPPER_ACCOUNT` se da de alta al arrancar si no existe, como
propia y con los tres roles: DHL exige siempre `accounts[typeCode=shipper]` con la
cuenta bajo la que se crean los envíos, y esa es la de las credenciales.

**ShipmentReason**: `nombre` (único), `motivoDhl` (`ExportReasonType`: `permanent`,
`temporary`, `return`, `sample`, `gift`, `commercial_purpose_or_sale`,
`intercompany_use`, `warranty_replacement`, `return_to_origin`, ... los 11 del
OpenAPI), `activo`. Semilla inicial en `V2`: Muestras → `sample`, Producción →
`commercial_purpose_or_sale`, Reposición → `commercial_purpose_or_sale`,
Devolución → `return`, Reparación → `temporary`. Editable en pantalla.

**Article**: `referencia` (única), `descripcionAduanera`, `codigoHs`, `paisOrigen`,
`pesoNetoUnitario`, `pesoBrutoUnitario`, `valorUnitario`, `moneda`, `activo`.

## 6. Máquina de estados

```
DRAFT ──tarifar──▶ QUOTED ──confirmar(producto)──▶ CONFIRMED ──crear: DHL 201──▶ CREATED
  ▲                  │                                 │
  └────── editar ────┘                                 └── DHL error ──▶ ERROR ──reintentar──▶ CONFIRMED
DRAFT | QUOTED | CONFIRMED | ERROR ──cancelar──▶ CANCELLED
```

- **Editar** un envío en `QUOTED` lo devuelve a `DRAFT` y borra las ofertas: la
  tarifa era de otros datos. En `CONFIRMED` o posteriores no se edita.
- **Confirmar** exige un producto de las ofertas vigentes, fija `productoCodigo`,
  `costeEstimado` y genera el `requestId`. Se **persiste antes** de llamar a DHL.
- **Crear** solo desde `CONFIRMED` o `ERROR`. Manda el `requestId` como
  `Message-Reference` (36 caracteres máximo; un UUID son 36). Si DHL responde 201:
  `CREATED`, con guía, URL de seguimiento y documentos. Si falla: `ERROR` con
  `ultimoError` legible y el envío intacto; reintentar reutiliza **el mismo**
  `requestId`, nunca uno nuevo.
- **Idempotencia**: la transición a `CREATED` se hace en una transacción con
  `@Version`; dos clics simultáneos en "Confirmar y crear" dan uno que gana y otro
  que ve `TransicionIlegalException` y recarga la pantalla ya en `CREATED`. El
  índice único de `requestId` es la segunda red.
- **Cancelar** en esta fase no llama a DHL (`CREATED` es terminal): cancelar una
  guía ya emitida es de la fase de seguimiento.
- Toda transición escribe una fila de `ShipmentStatusHistory` y avisa al
  `Notifier` (`envioCreado`, `envioFallido`), que en esta fase no hace nada.
- Una transición ilegal **sí lanza** (`TransicionIlegalException`): no es un dato
  que un humano pueda corregir, es un fallo de programa o una carrera. La web la
  traduce a un mensaje y recarga; la API a un 409.

## 7. Cliente DHL

- `RestClient` de spring-web (sin WebFlux), autenticación básica con
  `DHL_API_KEY:DHL_API_SECRET`. Bean **perezoso** como el de Anthropic: sin variables
  la app arranca y la sección avisa "faltan las credenciales de DHL".
- Configuración `envios.dhl` en `application.yml`: `base-url` (por defecto
  `https://express.api.dhl.com/mydhlapi/test`), `api-key`, `api-secret`,
  `shipper-account`, `plantilla-etiqueta` (`ECOM26_84_A4_001`, hoja A4; la de
  impresora térmica es `ECOM26_84_001`), `hora-recogida` (`12:00`),
  `zonas-horarias` (`ES: Europe/Madrid`, `FR: Europe/Paris`, `MA: Africa/Casablanca`),
  `timeout` (30 s).
- Cabeceras en todas las llamadas: `Message-Reference` (UUID), `Message-Reference-Date`
  (RFC 1123), `Plugin-Name`/`Plugin-Version` con el nombre de la app.

| Operación | Endpoint | Notas |
|---|---|---|
| `quote` | `POST /rates` | Un piece por bulto, `accounts[shipper]`, `customerDetails` solo postal, `isCustomsDeclarable`, `monetaryAmount[declaredValue]`, `plannedShippingDateAndTime`, `unitOfMeasurement: metric`, `productTypeCode: all`. Se leen `products[]` con `totalPrice[currencyType=BILLC]` y `deliveryCapabilities.estimatedDeliveryDateAndTime`. |
| `getProducts` | mismo `POST /rates` | Devuelve las ofertas sin precio. `GET /products` solo admite un bulto, así que no se usa. |
| `createShipment` | `POST /shipments` | Sección 7.1. Respuesta: `shipmentTrackingNumber`, `trackingUrl`, `packages[].trackingNumber`, `documents[]` (`content` base64, `typeCode`, `imageFormat`). |
| `validateAddress` | `GET /address-validate` | `type=pickup|delivery`, `countryCode`, `postalCode`, `cityName`, `strictValidation=false`. Se ofrece como botón "Comprobar dirección" en la ficha del contacto; no bloquea. |
| `track` | `GET /tracking` | **Solo la firma**; lanza `UnsupportedOperationException` con un mensaje que dice que es de la fase 2. |

### 7.1 Mapeo del envío (lo que fija el OpenAPI)

Obligatorios de `CreateShipmentRequest`: `plannedShippingDateAndTime`, `pickup`,
`productCode`, `accounts`, `customerDetails`, `content`.

- `plannedShippingDateAndTime`: `fechaRecogida` + `hora-recogida` en la zona del país
  de origen, formato `yyyy-MM-dd'T'HH:mm:ss 'GMT'xxx` (`2026-09-21T12:00:00 GMT+02:00`).
  `// TODO DHL:` el OpenAPI trae ejemplos con y sin espacio antes de `GMT`; se
  comprueba contra el entorno de test.
- `pickup`: `isRequested` = `pedirRecogida`; si se pide, `closeTime` = `horaCierreRecogida` y `location` = `lugarRecogida`; sin `pickupDetails` (recoge en el remitente). Si se pide recogida, la hora de cierre es obligatoria para tarifar.
- `accounts`: `shipper` = cuenta propia; `payer` = `cuentaTransporte` si es distinta;
  `duties-taxes` = `cuentaAranceles` si la hay.
- `customerDetails.shipperDetails` / `receiverDetails`: `postalAddress` (código
  postal, ciudad, país, tres líneas de dirección, provincia en `provinceName`/
  `provinceCode`), `contactInformation` (`email`, `phone`, `companyName`, `fullName`),
  `typeCode` (`business` / `direct_consumer`), `registrationNumbers`: `VAT` con
  `nifVat`, `EOR` con `eori`, ambos con `issuerCountryCode` = país del contacto.
  `// TODO DHL:` con qué `typeCode` viaja el ICE marroquí (`FTN`, `CIC` o `VAT`
  con emisor `MA`); hasta saberlo, se manda como `VAT` solo si el contacto no tiene
  `nifVat`.
- `content`: `packages[]` (`weight`, `dimensions`, `customerReferences[CU]` =
  referencia), `isCustomsDeclarable`, `declaredValue` = Σ cantidad × valor,
  `declaredValueCurrency: EUR`, `incoterm`, `unitOfMeasurement: metric`,
  `description` = nombre del motivo.
- `content.exportDeclaration` solo si `aduanero`: `lineItems[]` (`number`,
  `description`, `price`, `quantity {value, unitOfMeasurement: PCS}`,
  `commodityCodes [{typeCode: outbound, value: codigoHs}]`, `exportReasonType` del
  motivo, `manufacturerCountry`, `weight {netValue, grossValue}`), `invoice {number,
  date}` con número `PT-<año>-<id del envío>` y la fecha de recogida, `exportReasonType`
  del motivo, `placeOfIncoterm` = ciudad del destinatario.
  `// TODO DHL:` si la factura comercial exige un número propio del ERP, se añade
  campo.
- `outputImageProperties`: `encodingFormat: pdf`, `printerDPI: 300`, `imageOptions`:
  `label` con la plantilla configurada, `waybillDoc` (`ARCH_8x4`, una copia),
  `invoice` (`COMMERCIAL_INVOICE_P_10`, `commercial`, `eng`) solo si `aduanero`;
  `splitTransportAndWaybillDocLabels: true`, `allDocumentsInOneImage: false`.
- `customerReferences[CU]` = `referencia` del envío, para que salga en la etiqueta.

### 7.2 Errores y registro

- `4xx`/`5xx` llegan como `ErrorResponse {instance, detail, title, message,
  additionalDetails[], status}`. `DhlErrorTranslator` monta un texto para el usuario:
  `title` + `detail` + una línea por `additionalDetails`, sin URLs internas ni
  trazas. Es lo que se guarda en `ultimoError` y se pinta en rojo.
- Timeout o red: `CarrierException` con "DHL no ha respondido" y el envío en
  `ERROR`, reintentable.
- `DhlLogInterceptor` registra a `INFO` método, URL, código de respuesta y duración,
  y a `DEBUG` los cuerpos JSON con los `content` base64 sustituidos por
  `<pdf N bytes>`. La cabecera `Authorization` **nunca** se escribe; el interceptor
  solo copia las cabeceras de una lista blanca.

## 8. Migraciones: Flyway con baseline

- Dependencia `flyway-core` (Boot la gestiona; H2 no necesita módulo aparte).
- `V1__esquema_inicial.sql`: las tres tablas que hoy genera Hibernate
  (`temporada_guardada`, `tara_caja`, `memoria_caja_referencia`) con el DDL exacto
  que tiene la base real, leído de `INFORMATION_SCHEMA` de `./datos/packinglist`
  durante la implementación.
- `V2__envios.sql`: todas las tablas de la sección 5 y la semilla de motivos.
- `spring.flyway.baseline-on-migrate: true` y `baseline-version: 1`: la base del
  almacén, que ya tiene las tres tablas, queda marcada en V1 sin tocarla y recibe
  V2. Una base nueva ejecuta V1 y V2.
- `spring.jpa.hibernate.ddl-auto` pasa a **`validate`** en producción y en el perfil
  `test` (que sigue en memoria): así la suite comprueba que el SQL y las entidades
  cuadran. Es el cambio con más riesgo del proyecto y va **solo, en el primer paso**,
  verificado arrancando contra una copia de `./datos`.

## 9. Importadores CSV

**Libreta de MyDHL+** (`ImportadorLibretaMyDhl`). Formato medido en el fichero real:
UTF-8 con **BOM doble** (`EF BB BF EF BB BF`), coma, comillas dobles, 44 columnas,
cabeceras en español con acentos. Se lee con `commons-csv` (Apache, gestionada por Boot): hay campos entrecomillados
con comas dentro y un lector propio acabaría reproduciéndola a medias.

| Columna CSV | Destino |
|---|---|
| Nombre y apellidos | `persona` |
| Empresa | `empresa` (si falta, se usa la persona) |
| Alias, Alias 2 | `alias` (se descartan los vacíos y los que quedan en `at` tras quitar el nombre) |
| Email 1 | `email`; Email 2..5, si vienen, a `notas` |
| Número de teléfono Código de país + Número de teléfono | `telefono` = `+CC número` (sin ceros iniciales del número); Extensión a `notas` |
| NIF/CIF | `nifVat` |
| Número EORI | `eori` |
| Código de país | `pais` |
| Dirección 1..3, CP Código Postal, Ciudad, Estado / Provincia, Estado / Provincia Código | sus campos |
| Por Defecto Remitente Cuenta | alta de `BillingAccount` con rol `SHIPPER` si no existe |
| Por Defecto Enviar Cargos (Fracturar a) | alta con rol `PAYER`; `cuentaTransportePorDefecto` |
| Por Defecto Arancel/Impuestos (Fracturar a), Arancel, Impuesto | alta con rol `DUTIES`; `cuentaArancelesPorDefecto` |
| Términos aduaneros | `incotermPorDefecto` si es uno de los 16 |
| Notas | `notas` |
| Parte adicional Rol (PR/BU) | `tipo` PRIVATE/BUSINESS |
| Entregas residenciales (Y/N) | `residencial` |
| Fax, CNPJ/CPF, IE/RG, Suburbio, Referencia 1..4, Tipo de impuesto, Número de identificación fiscal | se ignoran (vacías en el fichero real) |

Las cuentas dadas de alta desde el CSV llevan `titular` = empresa del contacto y
`propia` = `numero == shipper-account` (en el fichero, `300112049` aparece como
remitente en varias filas de terceros: es la de Puntotres).

Flujo: subir → **vista previa** con tres bloques: filas listas, filas
**duplicadas** (misma empresa + persona ya en la libreta o repetida en el CSV; el
fichero real trae cuatro grupos repetidos, uno con cuatro copias) y filas con
**error** (sin país, ciudad, dirección 1 o empresa/persona). Un check por fila
duplicada para importarla igual. Confirmar → alta en una transacción → informe con
recuentos y el detalle de lo que no entró. La vista previa vive en sesión
(`ImportacionEnCurso`), como el resto de flujos.

**Artículos** (`ImportadorArticulosCsv`): CSV con cabecera `referencia, descripcion,
codigo_hs, pais_origen, peso_neto, peso_bruto, valor_unitario` (las tres primeras
obligatorias); misma vista previa; una referencia que ya existe **se actualiza**,
porque el catálogo se corrige reimportando.

## 10. Pantallas (Thymeleaf, sin JavaScript)

Tarjeta nueva en `/menu`: "Envíos por transportista" → `/envios`.

- **`/envios`**: listado con filtros por fecha (desde/hasta), estado, motivo, cliente
  y sentido; columnas fecha, guía, remitente → destinatario, motivo, cliente,
  responsable, coste, estado. Botón "Nuevo envío DHL".
- **`/envios/nuevo`** → crea un `DRAFT` vacío y redirige a `/envios/{id}`. Así el
  borrador existe desde el primer segundo y saltar a dar de alta un contacto no
  pierde lo tecleado.
- **`/envios/{id}`**: un solo formulario, en bloques:
  1. Remitente y destinatario: `<select>` de contactos activos ordenados por empresa
     (`EMPRESA · Persona · Ciudad (PAÍS)`; en un `<select>` se busca tecleando, y la
     búsqueda de verdad vive en la libreta), botones
     "Intercambiar" y "Alta rápida" (va a `/envios/contactos/nuevo?volver=/envios/{id}`).
     Debajo, el sentido deducido y si es aduanero, de solo lectura, con su aviso.
  2. Motivo (desplegable), cliente y temporada (texto libre con `datalist` del
     catálogo `packing-list.clientes` y de las temporadas guardadas), referencia,
     responsable (desplegable de `envios.responsables`), notas, fecha de envío, y la recogida: check
     "Pedir recogida al mensajero" (marcado por defecto), hora de cierre y lugar.
  3. Bultos: filas peso/largo/ancho/alto; botones "Añadir bulto" y "Quitar" son
     submits del formulario, como en la revisión del packing list.
  4. Aduana (solo si `aduanero`): filas de `CustomsLineItem`; "Añadir del catálogo"
     con un `<select>` de artículos que añade la fila rellena; "Añadir línea vacía".
     Comprobaciones (avisos, no bloqueos salvo la primera): toda línea con
     descripción, HS, cantidad y valor; neto ≤ bruto en cada línea; Σ bruto de
     líneas ≤ Σ peso de bultos; el valor declarado se muestra calculado.
  5. Pago: cuenta de transporte (solo cuentas con rol `PAYER`), cuenta de aranceles
     (solo `DUTIES`, opcional), incoterm. Se preseleccionan las del destinatario.
  6. **Guardar**, **Tarifar** (guarda y pasa a `QUOTED`; pinta la tabla de ofertas
     con radio para elegir), **Confirmar y crear** (con la oferta elegida: `CONFIRMED`
     → llamada a DHL → `CREATED` o `ERROR`), **Cancelar**.
  En `CREATED` el formulario es de solo lectura y aparece el bloque de descargas
  (`/envios/{id}/documentos/{tipo}.pdf`), la guía y el enlace de seguimiento. En
  `ERROR`, el error de DHL en rojo y el botón "Reintentar".
- **`/envios/contactos`** (listado con buscador por texto, alta, edición, baja
  lógica, "Comprobar dirección" con DHL) y **`/envios/contactos/importar`** (vista
  previa e informe).
- **`/envios/cuentas`**, **`/envios/motivos`**: tablas editables en línea.
- **`/envios/articulos`** con **`/envios/articulos/importar`**.

Todo lo tecleado se guarda en el `Shipment` en cada submit; no hay estado de envío
en sesión (a diferencia del asistente de packing lists), porque el borrador es un
dato que tiene que sobrevivir a cerrar el navegador.

## 11. API REST (`/api/shipments`)

JSON con Jackson, DTOs `record` propios en `shipping.api` (nunca las entidades),
sin autenticación en esta fase. Misma `ShipmentService` que la web.

```
POST   /api/shipments                 crea un DRAFT con el cuerpo completo → 201 + envío
GET    /api/shipments                 listado con los mismos filtros que la pantalla
GET    /api/shipments/{id}
PUT    /api/shipments/{id}            edita (vuelve a DRAFT si estaba QUOTED)
POST   /api/shipments/{id}/quote      → ofertas
POST   /api/shipments/{id}/confirm    { "productCode": "P" }
POST   /api/shipments/{id}/create     → CREATED o ERROR (200 con el estado)
POST   /api/shipments/{id}/cancel
GET    /api/shipments/{id}/documents/{tipo}.pdf
GET    /api/contacts?q=               búsqueda para el agente
GET    /api/billing-accounts, /api/reasons, /api/articles?q=
```

Errores: 404 si no existe, 409 en transición ilegal, 422 en validación (lista de
campo + mensaje), 502 cuando DHL falla en `quote` (en `create` no: el envío queda en
`ERROR` y se devuelve 200 con ese estado, que es la verdad del dato).

## 12. Validación

Bean Validation (`@Size`, `@Positive`, `@Valid`) en los DTOs de entrada de la API, con los
máximos del OpenAPI (35 en referencia, 70 en descripción de bulto, 2 en país); en los
formularios web, `maxlength` en los inputs y las mismas comprobaciones en el servicio. Además, `ShipmentService.validarParaTarifar` devuelve una lista de
problemas (contacto sin teléfono, sin bultos, aduanero sin líneas, cuenta sin el
rol necesario) que la pantalla pinta y que impide tarifar; el resto son avisos.

## 13. Notifier

```java
public interface Notifier {
    void envioCreado(Shipment envio);
    void envioFallido(Shipment envio, String error);
}
```
`NoOpNotifier` es el único bean. Las fases siguientes añaden `EmailNotifier`
(Microsoft 365) y `TelegramNotifier` y un compuesto que llame a todos.

## 14. Tests

- **Unitarios (JUnit 5 con `new`)**: `ShipmentService` con repositorios en memoria
  falsos y un `CarrierClient` de prueba: cada transición legal, cada ilegal, que
  confirmar fija `requestId` y reintentar lo conserva, que editar en `QUOTED` borra
  ofertas, que `ERROR` guarda `ultimoError`. `DirectionResolver` y `CustomsChecker`
  con los casos ES/MA/FR. `DhlRequestMapper` (envío completo → JSON: cuentas,
  registros, líneas, fecha con zona) y `DhlResponseMapper` (documentos base64 →
  bytes). `DhlErrorTranslator`. `ImportadorLibretaMyDhl` contra un **recorte anonimizado** del CSV real
  (misma cabecera, BOM doble y una docena de filas inventadas que reproducen los
  casos: duplicados, cuentas, alias vacíos, extensión, Email 2) en
  `src/test/resources/ejemplos/envios/`. El fichero real tiene nombres, correos y
  teléfonos de terceros y **no se copia al repo**; un test opcional lo lee de `docs/`
  si existe y comprueba solo recuentos (213 filas, 0 errores). `ImportadorArticulosCsv`.
- **Integración con WireMock** (`org.wiremock:wiremock-standalone` 3.x, test):
  `DhlExpressClient` contra stubs sacados de los ejemplos del OpenAPI
  (`nonDocInternationalShipmentRates`, `...ShipmentRequest/Response`): tarifa con
  precios, creación con tres documentos, 400 con `additionalDetails` traducido,
  cabeceras `Authorization` y `Message-Reference` presentes en la petición, y que
  el log no contiene la credencial.
- **`@SpringBootTest`** (perfil `test`, H2 en memoria, Flyway desde cero): rutas de
  las pantallas nuevas y de la API, y que `ddl-auto: validate` pasa.

## 15. Documentación

- `docs/Envios DHL/README.md`: variables de entorno, cómo pedir acceso al entorno
  de test de DHL y probar un envío, qué hace cada pantalla, qué queda para las
  fases de seguimiento, reports, agente, login y otros transportistas.
- Sección nueva en `CLAUDE.md` con lo que no se deduce del código (sentido respecto
  a España, aduana respecto a la UE, `requestId` antes de llamar, PDFs en base de
  datos, Flyway con baseline, `ddl-auto: validate`).
- `README.md`: una línea en el menú de secciones.

## 16. Pasos de implementación (cada uno compila y pasa la suite)

1. Flyway con `V1` baseline y `ddl-auto: validate`; verificado contra una copia de `./datos`.
2. Entidades, enums y repositorios del núcleo + `V2` con la semilla de motivos.
3. `CarrierClient`, tipos del puerto, `CarrierClientRegistry`, `Notifier` + `NoOpNotifier`.
4. `ShipmentService` con la máquina de estados, `DirectionResolver`, `CustomsChecker` (TDD).
5. `carrier.dhl`: propiedades, `RestClient`, DTOs, mappers, traductor de errores, interceptor de log, WireMock.
6. Mantenimientos: contactos (con "Comprobar dirección"), cuentas, motivos, artículos, y los dos importadores con su vista previa.
7. Pantalla del envío (`/envios/{id}`), listado, tarjeta del menú, CSS.
8. API REST.
9. Documentación (README de la feature, CLAUDE.md, README).

Rama `envios-dhl`, un commit por paso.

## 17. Fuera de alcance explícito

Recogidas por API, cancelación en DHL, seguimiento, notificaciones reales, reports,
login de Microsoft, otros transportistas, envíos de documentos (`productCode: D`),
servicios de valor añadido (seguro, entrega en sábado), y facturas comerciales
propias (se usa la que genera DHL).

## 18. Puntos que estuvieron abiertos (cerrados el 2026-09-18)

Decisión del usuario: **sí se pide la recogida** por API, con el check marcado por defecto;
el **sentido se queda con cuatro valores**. Se conserva la explicación por si se vuelve a dudar.

1. **Recogida del mensajero.** MyDHL API separa dos cosas: *crear el envío* (guía,
   etiqueta y documentos) y *pedir que pase el mensajero*. La primera exige siempre
   `plannedShippingDateAndTime` (cuándo se entregará el bulto a DHL; no puede ser
   pasado ni a más de 10 días). La segunda es `pickup.isRequested: true` con hora
   de cierre y lugar, y DHL devuelve un `dispatchConfirmationNumber`. La propuesta
   inicial era no pedirla por API y seguir avisando al mensajero como hasta ahora.
   Si hoy la recogida se pide envío a envío en MyDHL+, conviene pedirla desde la
   app en esta fase: un check "Pedir recogida", una hora de cierre y el lugar
   (ver `pickup` en el OpenAPI), y guardar el número de confirmación en el envío.
2. **Valores del sentido.** El sentido es solo una etiqueta informativa (columna y
   filtro del listado, aviso en la ficha): no interviene en la llamada a DHL ni en
   la aduana, que se decide aparte respecto a la UE. Con España como referencia,
   cualquier destino (Marruecos, Francia, EE. UU., Alemania, Italia) es
   `EXPORT` y cualquier origen extranjero hacia España es `IMPORT`; añadir países no
   añade nada. Los otros dos valores solo cubren los casos que no encajan en esos
   dos: España → España (`DOMESTIC`) y un envío que ni sale ni llega a España, como
   Marruecos → Francia (`CROSS_TRADE`). Alternativa más simple: tres valores
   `EXPORT`, `IMPORT` y `OTRO`.
