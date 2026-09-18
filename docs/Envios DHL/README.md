# Envíos por transportista — DHL Express (fase 1)

## Qué hace

Crea envíos internacionales con DHL Express desde `/envios`: libreta de
contactos, cuentas DHL, catálogo de artículos aduaneros, borrador → tarifa →
confirmación → creación con guía y PDFs (etiqueta, waybill, factura
comercial). API REST equivalente en `/api/shipments`.

Es una sección **independiente** del asistente de packing lists: no comparte
envío ni pantallas con él, solo el menú (`/menu`) y el estilo.

## Variables de entorno

| Variable | Qué es | Sin ella |
|---|---|---|
| `DHL_API_KEY` | API key de MyDHL API | La app arranca; tarifar/crear avisan "Faltan las credenciales de DHL" |
| `DHL_API_SECRET` | Secreto de la API key | Igual |
| `DHL_SHIPPER_ACCOUNT` | Cuenta DHL propia (9 dígitos) bajo la que se crean los envíos (`accounts[shipper]`) | Igual; además no se da de alta la cuenta propia en `/envios/cuentas` |
| `DHL_BASE_URL` | Opcional. Por defecto `https://express.api.dhl.com/mydhlapi/test` | Se usa el entorno de test |

Producción: `DHL_BASE_URL=https://express.api.dhl.com/mydhlapi` (sin `/test`).

## Probar contra el entorno de test de DHL

1. Pedir credenciales de test en https://developer.dhl.com (app sobre "DHL Express - MyDHL API").
2. Exportar las cuatro variables y arrancar `mvn spring-boot:run`.
3. `/envios/contactos/importar`: subir el export CSV de la libreta de MyDHL+ (44 columnas; el
   fichero de referencia está en esta carpeta, `address-book-*.csv`, y **no** se copia al
   repo por llevar datos personales de terceros — está en `.gitignore`).
4. `/envios/cuentas`: comprobar que la cuenta propia (la de `DHL_SHIPPER_ACCOUNT`) aparece
   marcada como "propia"; añadir las de los clientes que pagan transporte o aranceles.
5. `/envios` → "Nuevo envío DHL": remitente, destinatario, motivo, bultos, líneas de aduana,
   recogida (check "Pedir recogida al mensajero", marcado por defecto, hora de cierre y
   lugar), cuenta de pago → Tarifar → elegir producto → Confirmar y crear.
6. Descargar la etiqueta, el waybill y la factura comercial desde la ficha del envío ya
   `CREATED`. En el entorno de test la guía y los PDF son ficticios.
7. Si falla la creación en DHL, el envío queda en `ERROR` con el mensaje de DHL a la vista y
   un botón "Reintentar la creación en DHL" que reutiliza el mismo `requestId`.

## Dónde está cada cosa

- Núcleo: `shipping/` (modelo, `ShipmentService` con la máquina de estados, puerto
  `CarrierClient`, `DirectionResolver`/`CustomsChecker`, importadores CSV, web, api).
- Adaptador: `carrier/dhl/` (DTOs del OpenAPI 3.3.2 de esta carpeta, `DhlRequestMapper`/
  `DhlResponseMapper`, `DhlErrorTranslator`, `DhlExpressClient`). `shipping` no importa nada
  de `carrier.dhl`.
- Esquema: `db/migration/V1__esquema_inicial.sql` (baseline del esquema que ya existía:
  taras, temporadas guardadas, memoria de referencias) y `V2__envios.sql` (todas las tablas
  de envíos y la semilla de motivos). Flyway gestiona el esquema; Hibernate solo valida
  (`ddl-auto: validate`).
- Log de DHL: logger `dhl.api` — a `INFO` método, ruta, código de respuesta y duración; a
  `DEBUG` los cuerpos JSON con los PDF sustituidos por `<pdf N bytes>`. La cabecera
  `Authorization` nunca se escribe.

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
- Recogidas y cancelación de guías por API, coste facturado (`Shipment.costeFacturado`, hoy
  siempre `null`).
- Reports de costes por responsable, motivo y cliente (los campos ya están en `Shipment`).
- Login de Microsoft: sustituir el desplegable de `envios.responsables` por el usuario
  autenticado.
- Otros transportistas: un paquete `carrier.<nombre>` que implemente `CarrierClient` y un
  valor nuevo en `CarrierCode`.
