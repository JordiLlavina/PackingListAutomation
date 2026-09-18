# Envíos por transportista — DHL Express (fase 1)

## Qué hace

Crea envíos internacionales con DHL Express desde `/envios`: libreta de
contactos, cuentas DHL, catálogo de artículos aduaneros, borrador → tarifa →
confirmación → creación con guía y PDFs (etiqueta, waybill, factura
comercial). API REST equivalente en `/api/shipments`.

Es una sección **independiente** del asistente de packing lists: no comparte
envío ni pantallas con él, solo el menú (`/menu`) y el estilo.

La libreta de contactos guarda datos personales de terceros (nombre, dirección,
teléfono, email de personas de contacto reales) y la aplicación no tiene
autenticación, así que esta sección debe seguir funcionando **solo dentro de la
red interna** de la empresa, nunca expuesta a Internet.

## Variables de entorno

| Variable | Qué es | Sin ella |
|---|---|---|
| `DHL_API_KEY` | API key de MyDHL API | La app arranca; tarifar/crear avisan "Faltan las credenciales de DHL" |
| `DHL_API_SECRET` | Secreto de la API key | Igual |
| `DHL_SHIPPER_ACCOUNT` | Cuenta DHL propia (9 dígitos) bajo la que se crean los envíos (`accounts[shipper]`) | Igual; además no se da de alta la cuenta propia en `/envios/cuentas` |
| `DHL_BASE_URL` | Opcional. Por defecto `https://express.api.dhl.com/mydhlapi/test` | Se usa el entorno de test |

Producción: `DHL_BASE_URL=https://express.api.dhl.com/mydhlapi` (sin `/test`).

## Probar contra el entorno de test de DHL

1. **Antes del primer arranque con esta rama, copia `./datos/` a un lugar seguro.**
   Flyway marca el esquema actual como baseline la primera vez que arranca contra una
   base existente, y a partir de ahí Hibernate ya no crea ni actualiza nada: solo
   `validate`. Si una entidad no encaja con lo que hay en la base (una columna que
   falta, un tipo distinto), el arranque se detiene con el nombre de la columna en el
   mensaje. La solución es siempre una migración nueva `V3__algo.sql`, **nunca** volver
   a `ddl-auto: update`; tener la copia de `./datos/` a mano evita perder la base real
   mientras se decide esa migración.
2. Pedir credenciales de test en https://developer.dhl.com (app sobre "DHL Express - MyDHL API").
3. Exportar las cuatro variables y arrancar `mvn spring-boot:run`.
4. `/envios/contactos/importar`: subir el export CSV de la libreta de MyDHL+ (44 columnas; el
   fichero de referencia está en esta carpeta, `address-book-*.csv`, y **no** se copia al
   repo por llevar datos personales de terceros — está en `.gitignore`).
5. `/envios/cuentas`: comprobar que la cuenta propia (la de `DHL_SHIPPER_ACCOUNT`) aparece
   marcada como "propia"; añadir las de los clientes que pagan transporte o aranceles.
6. `/envios` → "Nuevo envío DHL": remitente, destinatario, motivo, bultos, líneas de aduana,
   recogida (check "Pedir recogida al mensajero", marcado por defecto, hora de cierre y
   lugar), cuenta de pago → Tarifar → elegir producto → Confirmar y crear.
7. Descargar la etiqueta, el waybill y la factura comercial desde la ficha del envío ya
   `CREATED`. En el entorno de test la guía y los PDF son ficticios.
8. Si falla la creación en DHL, el envío queda en `ERROR` con el mensaje de DHL a la vista y
   un botón "Reintentar la creación en DHL" que reutiliza el mismo `requestId`.

## API

`PUT /api/shipments/{id}` es un **reemplazo completo** del envío, con las mismas
reglas de "campo ausente" que la pantalla:

- `horaCierreRecogida` y `lugarRecogida`: `null` (el campo no viaja en el JSON)
  significa "no tocar" y conserva lo que hubiera; una cadena vacía `""` sí lo borra.
- `pedirRecogida`: `null` equivale a `true` (se pide recogida salvo que se mande
  explícitamente `false`).

El resto de campos (remitente, destinatario, cuentas, motivo, bultos, líneas de
aduana...) sí se reemplazan enteros con lo que traiga el `PUT`, incluido `null`.

## Dónde está cada cosa

- Núcleo: `shipping/` (modelo, `ShipmentService` con la máquina de estados, puerto
  `CarrierClient`, `DirectionResolver`/`CustomsChecker`, importadores CSV, web, api).
- Adaptador: `carrier/dhl/` (DTOs del OpenAPI 3.3.2 de esta carpeta, `DhlRequestMapper`/
  `DhlResponseMapper`, `DhlErrorTranslator`, `DhlExpressClient`). `shipping` no importa nada
  de `carrier.dhl`, y `carrier.dhl` solo importa `shipping.carrier` y `shipping.model` —
  y, como única excepción, `CuentaPropiaInicial` usa `BillingAccountService` para dar de
  alta la cuenta propia al arrancar, porque es quien conoce `shipper-account`.
- Esquema: `db/migration/V1__esquema_inicial.sql` (baseline del esquema que ya existía:
  taras, temporadas guardadas, memoria de referencias) y `V2__envios.sql` (todas las tablas
  de envíos y la semilla de motivos). Flyway gestiona el esquema; Hibernate solo valida
  (`ddl-auto: validate`).
- Log de DHL: logger `dhl.api` — a `INFO` método, ruta, código de respuesta y duración; a
  `DEBUG` los cuerpos JSON con los documentos sustituidos por `<documento base64, N
  caracteres>`. La cabecera `Authorization` nunca se escribe.

## Dudas abiertas con el contrato de DHL (buscar `TODO DHL` en el código)

- Formato exacto de `plannedShippingDateAndTime` (con o sin espacio antes de `GMT`) —
  `DhlRequestMapper`.
- `typeCode` del identificador ICE marroquí en `registrationNumbers` (`FTN`, `CIC` o `VAT`
  con emisor `MA`) — `DhlRequestMapper`.
- Número de factura comercial: hoy `PT-<año>-<id del envío>`; si el ERP exige el suyo propio,
  hace falta un campo nuevo en `Shipment` — `DhlRequestMapper`.

Los tres son deliberados y quedan pendientes de comprobar contra el entorno de test de DHL,
no bugs.

## Pendiente para las fases siguientes

- Seguimiento: `CarrierClient.track` (firma ya definida, lanza `UnsupportedOperationException`),
  tabla de eventos, notificaciones por `Notifier` (correo Microsoft 365, Telegram). El único
  `Notifier` de esta fase es `NoOpNotifier`; `Shipment.urlSeguimiento` ya se guarda al crear.
- Gestionar por separado una recogida ya pedida (modificarla o cancelarla, `/pickups`
  de MyDHL API) y cancelar una guía ya creada en DHL — pedir la recogida al crear el
  envío (`pickup.isRequested`) ya está hecho, esto es todo lo que queda de recogidas.
  Coste facturado (`Shipment.costeFacturado`, hoy siempre `null`).
- Reports de costes por responsable, motivo y cliente (los campos ya están en `Shipment`).
- Login de Microsoft: sustituir el desplegable de `envios.responsables` por el usuario
  autenticado.
- Otros transportistas: un paquete `carrier.<nombre>` que implemente `CarrierClient` y un
  valor nuevo en `CarrierCode`.
