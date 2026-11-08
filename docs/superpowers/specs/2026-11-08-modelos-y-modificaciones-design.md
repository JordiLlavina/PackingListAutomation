# Modelos y modificaciones: la app del taller — diseño

Fecha: 2026-11-08. Estado: **diseño acordado con el usuario, pendiente de plan**
(el plan se escribe con la skill de planes en
`docs/superpowers/plans/2026-11-xx-modelos-y-modificaciones.md`). Se apoya en
[2026-11-01-catalogo-modelos-design.md](2026-11-01-catalogo-modelos-design.md)
(el catálogo es la base) y adelanta decisiones del diseño aparcado
`TO DO/2026-09-19-login-microsoft-y-actividad-design.md` (usuarios), sin
implementarlo.

Material de apoyo que acompaña a este spec:

- `docs/Modelos/maqueta-tablet.html`: **maqueta clicable** de la cara de taller,
  sin servidor, para probarla con Bachir antes de programar nada. Lo que la
  maqueta decide (recorrido, tamaños, orden de los pasos) manda sobre lo que
  diga este texto si no coinciden, salvo que se corrija la maqueta.
- `docs/Modelos/modelos.puntotres.com - guia para el informatico.md`: lo que hay
  que pedir a informática (DNS interno, certificado, nginx, tablet). **Sin eso no
  se puede probar nada en la tablet**: el navegador no da cámara, micrófono,
  instalación ni notificaciones en `http://`.

## 1. Qué pidió el usuario

Una app nueva, de aspecto de app móvil y sin parecido con el programa actual,
con dos caras:

- **Taller**: Bachir, técnico responsable de bolsos, que lee y escribe despacio.
  Desde una tablet Android (y puede que un móvil) avisa de que ha **modificado un
  modelo** (construcción, materiales, lo que sea) eligiendo el modelo, pulsando
  botones, haciendo **fotos** y mandando una **nota de voz**; nunca teclea. Y
  recibe **órdenes de modificación** de despacho, con documentos e imágenes,
  las escucha y las lee, y las marca **hechas**.
- **Despacho**: dos o tres personas en PC. Crean órdenes para el taller, reciben
  los avisos de Bachir, los trasladan al ERP (ICSUITE) y al excel de escandallos,
  y los marcan hechos. Cambian la **etapa** de un modelo (prototipo → SMS →
  producción, o inactivo, y de vuelta), y dan de alta modelos.

El objetivo de fondo: que **ningún cambio en un bolso, billetero o cinturón se
quede sin avisar ni sin apuntar**, y que quede el histórico de todos. Los
billeteros se tratan como bolsos.

## 2. Decisiones tomadas con el usuario

| Pregunta | Decisión |
|---|---|
| ¿Notificaciones e histórico son dos cosas? | **No, una.** Una **modificación** con dirección (taller→despacho o despacho→taller), autor, modelo, evidencia y estado. El histórico de un modelo es la lista de sus modificaciones más sus cambios de etapa. Las dos bandejas son filtros sobre la misma tabla. |
| ¿Es una ficha técnica completa? | **No.** El registro de materiales sigue en ICSUITE y en los excels. La app guarda identidad del modelo, etapa, fotos, documentos y **los cambios con su evidencia y si ya se trasladaron**. |
| Identidad del prototipo | Un prototipo **siempre tiene cliente** y una **referencia de prototipo** que da el cliente (`PROTO A`), que **se repite** entre temporadas y modelos pero **no dentro de una misma temporada del mismo cliente**. Al pasar a SMS o producción recibe la referencia definitiva (`ULL770`). Además tiene su nombre propio, como todos los modelos. |
| Esquema para eso | `modelo` gana un **id interno** (clave primaria nueva); `codigo` pasa a **nulo** hasta que el cliente da referencia; columna `nombre_prototipo` (la referencia de prototipo) y la temporada de alta. Migración **V4 con datos** aunque la base actual no importe: es el patrón de aquí en adelante. |
| Fusionar y mover | Hace falta **vincular** un prototipo con la referencia que entra después por el pedido (fusión), y **mover** un modelo entre prototipo, SMS, producción e inactivo libremente. Un inactivo puede volver. La etapa es **del modelo**, no del artículo: la piel cambia al pasar a muestras y producción. |
| ¿Dónde está Bachir? | **Red local de Puntotres**, WiFi estable, tablet Android (y quizá móvil Android). Más adelante la app tendrá que verse desde internet. |
| Orden de la seguridad | **Tabla de usuarios mínima ahora, Microsoft después.** La tabla nace con las columnas del diseño de Entra para que ese paso sea añadir una forma de entrar, no rehacer. |
| Base de datos | **La misma (H2 en `./datos`) ahora**; PostgreSQL llegará antes que internet. Desde la **V4 el SQL se escribe en el subconjunto que PostgreSQL acepta**. |
| Lenguaje y arquitectura | **Monolito con dos caras**: backend en el Spring Boot actual (paquete nuevo, API JSON) y **PWA en TypeScript** servida desde el mismo jar. Ver §5. |
| Transcripción | Claude **no acepta audio**: un servicio de voz a texto delante (**Azure AI Speech** por defecto, intercambiable) y Claude detrás para resumir y estructurar. Castellano. |
| Despacho→taller | **Sin voz sintética.** Despacho escribe y, si quiere, graba su propia nota de voz; las dos cosas se guardan. |
| Documentos PDF | **No se convierten a imágenes.** El PDF se abre en el visor de la tablet. Lo que sí: **extraer las imágenes del modelo** que lleve dentro, con selección manual de cuáles son del modelo. Un día podrá hacerlo una llamada a un LLM; hoy no. |
| Fotos | **Varias** por aviso, con la cámara, junto al audio. |
| Anti-olvido | **En los dos sentidos**: recordatorios por push a taller y a despacho, con **prioridad que pone despacho**, y una **lista de dónde aplicar** cada cambio en despacho (**ICSUITE** y **excel de escandallos**). Resumen diario y semanal por correo: **lista de pendientes**, no v1. |
| Destinatario del taller | Las órdenes van **al rol de taller**, no a una persona; pero la app **saluda por su nombre** al abrirse («Hola, Bachir»). Solo Bachir a corto plazo. |
| Sonidos | **Nada** en lo que hace el propio usuario. **Sonido y vibración** cuando entra una modificación nueva. |
| Elegir el modelo sin leer | **Etiqueta de patrones**: impresión en **A4 normal** desde despacho, con QR, foto principal, nombre del modelo, cliente y referencia, para pegar en los patrones del bolso. La app **escanea** ese QR. Bachir no tiene acceso a los EAN. |
| Dominio | **`modelos.puntotres.com`**. Los QR llevan esa URL desde el primer día. nginx y certificado los monta el informático. |
| ClickUp | **No para Bachir.** Despacho lo usará; se integrará cuando esté en marcha. No afecta a la v1. |
| v1 sin | Email, Telegram, excels de SMS, resúmenes por correo, Microsoft, PostgreSQL. Esqueleto principal con buena estructura y buena UX. |

## 3. Supuestos (míos, no corregidos por el usuario)

- **Dos disposiciones, una app**: la PWA detecta el rol (taller / despacho) y
  pinta una u otra. La de taller se diseña para tablet y funciona en móvil; la
  de despacho para PC y funciona en tablet. Ningún recorrido depende del ancho.
- **El audio es obligatorio o lo son las fotos** en un aviso de Bachir: al menos
  una de las dos cosas. Un aviso con solo el tipo de cambio no dice nada.
- **Todo audio se transcribe**, también el de despacho: la transcripción es lo
  que hace buscable el histórico.
- **Los documentos de un modelo** (fichas técnicas, PDF del cliente) se guardan
  con el modelo, no solo como adjuntos de una orden: la app es el sitio donde
  está «todo lo que se sabe del modelo».
- **Los prototipos pueden nacer sin temporada con excel**: `temporada_guardada`
  admite las dos columnas de excel a nulo desde la V3; la obligatoriedad es de
  la pantalla `/temporadas`. La app de modelos crea temporadas **solo con nombre**
  si hace falta.
- **Un modelo que entra por el pedido** (`CatalogoModelos.incorporar`) nace en
  etapa **PRODUCCION**; uno que se da de alta a mano en la app nace en la etapa
  que se elija (por defecto PROTOTIPO). Los que ya existen en la base pasan a
  PRODUCCION en la V4.
- **La tablet es de Bachir**: entra una vez con un código de alta y no vuelve a
  identificarse. Sin PIN en la v1.
- **La app actual no cambia de aspecto ni de rutas.** Solo gana una tarjeta en
  `/menu` que lleva a `/app/`. Spring Security protege **solo** las rutas nuevas;
  lo de siempre sigue abierto en la red local como hoy. Extenderlo a todo es la
  fase de Entra del TODO.
- **Sin cola fuera de línea** en la v1: con WiFi estable, un envío que falla se
  queda en la pantalla con su borrador y un botón de reintentar; no se pierde,
  pero tampoco se guarda en el dispositivo.
- **El `tipo` BOLSO/CINTURON sigue informativo**, como en el catálogo.

## 4. Lo que NO entra en la v1 (lista de pendientes, en orden de valor)

1. Resumen diario y semanal por correo (necesita buzón SMTP o Graph del admin).
2. Entrada de modelos por email a un agente; soltar un documento y que Claude
   proponga el modelo (misma cola de borradores que el email). La V4 **no**
   reserva tabla para borradores: se añade cuando toque.
3. Telegram/WhatsApp para notas de voz.
4. Excels de SMS (van a la app actual, `/temporadas`).
5. Login de Microsoft, PostgreSQL, salida a internet (TODO existente).
6. Integración con ClickUp para despacho.
7. Elegir las imágenes de un PDF con un LLM en vez de a mano.
8. Interfaz en árabe; cola fuera de línea; PIN en la tablet; varios técnicos.

## 5. Arquitectura

### 5.1 Opción elegida y por qué

**Monolito con dos caras.** Un solo proceso Spring Boot, un solo jar, una sola
base de datos y un solo Flyway.

- Backend: paquete **`com.puntotres.packinglist.modelos`**, con subpaquetes
  `persistence`, `service`, `api`, `seguridad`. Spring Boot lo escanea solo
  (cuelga del paquete de la aplicación). **Disciplina de paquete**: nada de
  `modelos` importa de `web/` ni de los servicios del packing; habla con el
  catálogo **solo** por `CatalogoModelos`, `FotosCatalogo` y `ArchivoTemporadas`.
  Así, si algún día duele, se extrae a un módulo Maven (`core`, `packing-web`,
  `modelos-api`) sin reescribir.
- Frontend: **PWA en TypeScript** (React + Vite + Tailwind, `vite-plugin-pwa`),
  en `frontend/` en la raíz del repo, compilada por Maven
  (`frontend-maven-plugin`) a `target/classes/static/app/` y servida en
  **`/app/`** por el mismo jar, con un controlador que devuelve `index.html`
  para cualquier ruta bajo `/app/**` (enrutado por historial). En desarrollo,
  `vite dev` con proxy de `/api` y `/m` a `:8080`. Svelte 5 valdría igual; React
  por ecosistema. **Sin librería de componentes móvil**: diseño propio.
- API: **JSON bajo `/api/...`**, misma sesión de cookie que la web (mismo
  origen), CSRF por cookie `XSRF-TOKEN` + cabecera `X-XSRF-TOKEN`.
- Rutas estables fuera de la SPA: **`/m/{id}`** (lo que lleva el QR) redirige a
  `/app/modelos/{id}`; así una etiqueta impresa no depende del enrutador.
- Acceso: `https://modelos.puntotres.com/` → nginx manda `/` a `/app/` y pasa
  todo a `127.0.0.1:8080`. La app vieja queda también accesible por ese nombre
  (`/menu`), que es lo que quiere decir «se accede desde la app actual».

Descartadas, y por qué (resumen; el detalle se discutió con el usuario):
**dos servicios sobre PostgreSQL** (obliga a migrar ya, dos ORMs sobre un
esquema sin dueño, dos lenguajes para una persona); **monolito modular Maven
ahora** (reestructurar 251 clases sin ganar función; se deja para cuando duela);
**app nativa Android** (segundo lenguaje y APK a mano, y en Android la PWA da
cámara, micrófono, instalación y push con sonido y vibración).

### 5.2 Dependencias nuevas

| Qué | Para | Nota |
|---|---|---|
| `spring-boot-starter-security` | Usuarios, roles, dispositivos | Cadena acotada a `/app/**`, `/api/**`, `/m/**`, `/login`; `permitAll` al resto; CSRF ignorado en las rutas viejas. |
| `nl.martijndwars:web-push` (+ BouncyCastle) | Web Push con VAPID | Claves en variables de entorno. |
| `org.apache.pdfbox:pdfbox` | Imágenes de los PDF | Hermano de POI. |
| `frontend-maven-plugin` | Compilar la PWA en `mvn package` | Descarga Node en `target/`; sin Node en el PATH del servidor. |
| Azure AI Speech | Voz a texto | **REST** con `RestClient`, sin SDK. `AZURE_SPEECH_KEY`, `AZURE_SPEECH_REGION`. |
| SDK de Anthropic (ya está) | Resumir y estructurar transcripciones | `claude-opus-5-5`, esfuerzo bajo. |

En el frontend: React, `react-router`, `vite-plugin-pwa` (Workbox), `@zxing/browser`
(respaldo del escáner), nada más que haga falta.

### 5.3 Variables de entorno y configuración

`/etc/packinglist.env` (ver la guía del informático):

```
MODELOS_URL_PUBLICA=https://modelos.puntotres.com     # lo que llevan los QR
MODELOS_ADMIN_PASSWORD=...                            # solo para el primer arranque sin usuarios
MODELOS_VAPID_PUBLIC=... / MODELOS_VAPID_PRIVATE=...  # Web Push
AZURE_SPEECH_KEY=... / AZURE_SPEECH_REGION=westeurope # sin ellas, no hay transcripción y se dice
```

`application.yml`, bloque `modelos`: tipos de cambio (con icono), destinos de
aplicación de despacho (`ICSUITE`, `EXCEL_ESCANDALLOS`), plazos de recordatorio
por prioridad, `server.forward-headers-strategy: framework` y
`server.address: 127.0.0.1` cuando nginx esté delante (el informático lo
confirma; hasta entonces el 8080 sigue abierto a la red como hoy).

## 6. Esquema: migración V4

**Dialecto**: la V4 y las siguientes se escriben en el **subconjunto común a H2 y
PostgreSQL**, que es el que la suite comprueba (Flyway corre en los tests). Reglas:

- `bigint generated by default as identity` para ids; `varchar(n)`; `text` para
  textos largos; **`bytea`** para binarios; `timestamp`; `boolean`.
- **Binarios sin `@Lob`**: `byte[]` a secas en la entidad. En H2 `bytea` es
  `VARBINARY` y Hibernate valida `byte[]` contra eso; en PostgreSQL `@Lob` sobre
  `byte[]` crea un `oid` (large object), que es la trampa clásica. Las tablas de
  la V1–V3 (`blob`, `@Lob`) se reescriben el día de PostgreSQL en una base
  consolidada por proveedor (`db/migration/{vendor}`); **no se tocan ahora**.
- Renombrar: `alter table t rename column a to b`; nulabilidad: `alter table t
  alter column c drop not null`. Los dos valen en los dos.
- Nada de `jsonb`, arrays, `serial`, `on update cascade` ni índices parciales.

### 6.1 `modelo` pasa a tener id

Patrón **tabla nueva + volcado + recolocar hijos**, que funciona igual en los dos
motores y no depende de cómo rellena cada uno una columna identidad añadida:

```sql
create table modelo_v4 (
    id                  bigint generated by default as identity primary key,
    cliente             varchar(60)  not null,
    codigo              varchar(80),                 -- null hasta que el cliente da referencia
    nombre_prototipo    varchar(80),                 -- la referencia de prototipo ("PROTO A")
    nombre              varchar(200),
    tipo                varchar(20)  not null,
    etapa               varchar(20)  not null,       -- PROTOTIPO | SMS | PRODUCCION | INACTIVO
    fecha_etapa         timestamp    not null,
    temporada_alta_id   bigint references temporada_guardada (id),
    etiqueta_impresa_en timestamp,
    fecha_actualizacion timestamp    not null,
    constraint uk_modelo_codigo unique (cliente, codigo),
    constraint uk_modelo_prototipo unique (cliente, nombre_prototipo, temporada_alta_id),
    constraint ck_modelo_identidad check (codigo is not null or nombre_prototipo is not null)
);
insert into modelo_v4 (cliente, codigo, nombre, tipo, etapa, fecha_etapa, fecha_actualizacion)
    select cliente, codigo, nombre, tipo, 'PRODUCCION', fecha_actualizacion, fecha_actualizacion from modelo;
```

Después: `articulo` y `foto_modelo` ganan `modelo_id` (se rellena con un `update
… from`/subconsulta por `(cliente, codigo_modelo)`), pierden `codigo_modelo` y
su FK antigua, y enlazan a `modelo_v4(id) on delete cascade`; `modelo_temporada`
se recrea con clave `(modelo_id, temporada_id)`; se borra `modelo` y se renombra
`modelo_v4` a `modelo`. Dos nulos no chocan en una `unique`, así que los
prototipos sin código conviven; la `unique` de prototipo es la regla del usuario
(«no dos `PROTO A` en la misma temporada del mismo cliente»), y los modelos con
código tienen `nombre_prototipo` a nulo o el que tuvieron.

`hoja_embalaje.codigo_modelo` se queda como texto: busca la foto por
`(cliente, codigo)` y solo tiene sentido en modelos con código.

### 6.2 Tablas nuevas

```
modelo_codigo_historico   id, modelo_id FK, codigo, desde, hasta, motivo
                          (cada referencia o nombre de prototipo que ha tenido)
cambio_etapa              id, modelo_id FK, de, a, fecha, usuario_id, motivo
documento_modelo          id, modelo_id FK, nombre, mime, tamano, bytes (bytea), subido_por, fecha

modificacion              id, modelo_id FK, direccion (TALLER_A_DESPACHO | DESPACHO_A_TALLER),
                          tipo_cambio (clave del yml), prioridad (ALTA | NORMAL | BAJA, nula en las
                          de taller hasta que despacho la pone), estado (NUEVA | VISTA | EN_CURSO |
                          HECHA | DESCARTADA), titulo (despacho; en las de taller lo pone la IA),
                          necesita_respuesta boolean, creada_por, creada_en, vista_en,
                          hecha_por, hecha_en, motivo_descarte
modificacion_destino      id, modificacion_id FK, destino (ICSUITE | EXCEL_ESCANDALLOS), aplica
                          boolean, hecho_en, hecho_por   (solo en las de taller→despacho)
mensaje                   id, modificacion_id FK, autor_id, texto, creado_en
                          (el cuerpo inicial es el mensaje 1; las respuestas, los siguientes)
adjunto                   id, mensaje_id FK, tipo (AUDIO | FOTO | DOCUMENTO), nombre, mime, tamano,
                          duracion_seg, bytes (bytea), transcripcion text, resumen_ia text,
                          estado_transcripcion (PENDIENTE | HECHA | FALLIDA | NO_APLICA), error, fecha

usuario                   id, nombre, login (único, nulo en quien solo entra por dispositivo),
                          password_hash, correo, oid_entra (único, nulo), rol (TALLER | DESPACHO |
                          ADMIN), activo, fecha_alta, ultimo_acceso
dispositivo               id, usuario_id FK, nombre, token_hash (único), alta, ultimo_uso, revocado_en
codigo_alta               id, usuario_id FK, codigo (6 cifras), caduca, usado_en
suscripcion_push          id, usuario_id FK, dispositivo_id FK (nula en PC), endpoint, p256dh, auth, fecha
aviso_enviado             id, modificacion_id FK, usuario_id FK, tipo (NUEVA | RECORDATORIO |
                          RESPUESTA | SIN_PRIORIDAD), enviado_en
```

Convenciones del paquete: columnas planas sin `@ManyToOne`, FKs en el SQL,
`on delete cascade` de modelo a todo lo suyo; `usuario` **no se borra**, se
desactiva (regla del diseño de Entra: el histórico apunta a él).

### 6.3 Qué cambia en el catálogo

`Modelo` pasa a `@Id Long id` y pierde `ModeloId`; `Articulo` y `FotoCatalogo`
llevan `modeloId`; `ModeloTemporada` también. `CatalogoModelos` conserva los
métodos por `(cliente, codigo)` que usan las importaciones (buscan por la
`unique`) y gana los de `id`. `Modelo.getCodigo()` puede ser nulo:
`nombreVisible()` devuelve el código o, si no, el nombre de prototipo. La pantalla
`/catalogo` enseña también los prototipos con una marca de etapa (sin más
cambios). `incorporar` **no toca la etapa** de un modelo que ya existe.

## 7. Comportamiento

### 7.1 Etapas

`PROTOTIPO`, `SMS`, `PRODUCCION`, `INACTIVO`. Cualquier transición es legal (un
inactivo vuelve, un prototipo puede ir directo a producción). La cambia despacho
desde la ficha, con motivo opcional; se apunta en `cambio_etapa` y sale en la
línea de tiempo del modelo. Bachir ve la etapa (chip) y no la cambia.

### 7.2 Asignar referencia y fusionar

- **Asignar referencia** a un prototipo: se escribe `codigo`; `nombre_prototipo`
  se conserva; se apunta en `modelo_codigo_historico`. Si **ya existe** un modelo
  del cliente con ese código (ha entrado por el pedido de la temporada), la
  acción pasa a ser **fusionar**: sobrevive el **prototipo** (el de menor id, que
  tiene fotos e histórico), recibe el código y, si no tenía nombre, el nombre
  del otro; `articulo`, `foto_modelo`, `modelo_temporada`, `documento_modelo`,
  `modificacion` y `cambio_etapa` del otro se recolocan; el otro se borra. Antes
  de hacerlo, la pantalla dice qué se va a juntar (n artículos, n fotos, n
  modificaciones). No es reversible y se dice.
- **Desde el modelo importado**: «Este modelo es un prototipo que ya tenemos»,
  eligiendo el prototipo. Misma fusión.
- La etapa tras fusionar es la del modelo con código (lo que dice el pedido),
  apuntando el cambio.

### 7.3 Ciclo de una modificación

**Taller → despacho** (Bachir):

1. Elige el modelo (escáner o rejilla), el **tipo de cambio** (botones del yml:
   piel/material, forro, herrajes, costura/construcción, medidas, patrón, otro),
   **fotos** (las que quiera; se reducen a 1.600 px en el navegador antes de
   subir) y **nota de voz** (máximo 3 min). Al menos foto o audio.
2. Envía. Se crea la `modificacion` en `NUEVA`, sin prioridad, con su `mensaje`
   inicial y adjuntos; los destinos de aplicación nacen con `aplica = true`.
3. Despacho recibe **push** y la ve en su bandeja; al abrirla pasa a `VISTA`;
   pone **prioridad** (hasta entonces el recordatorio «sin prioridad» insiste);
   marca cada destino aplicado; cuando todos los que aplican están hechos la
   modificación pasa a **`HECHA`** sola. O la **descarta** con motivo. Puede
   **responder** (texto o voz); la respuesta enciende `necesita_respuesta` para
   el taller y llega por push.
4. Bachir ve en «Mis avisos» el estado de lo suyo (enviado, visto, hecho) y las
   respuestas; puede contestar por voz.

**Despacho → taller**:

1. Despacho crea la orden: modelo, título, texto, **prioridad** (obligatoria),
   nota de voz opcional, fotos y documentos. `NUEVA`.
2. Bachir recibe push con **sonido y vibración**; la orden aparece en «Tengo que
   hacer» con el número en la losa. Al abrirla pasa a `VISTA`. Escucha el audio
   si lo hay, lee el texto (letra grande), abre los documentos en el visor de la
   tablet y vuelve.
3. **«Hecho»** en dos toques (el segundo confirma; sin sonido) → `HECHA`, con
   `hecho_por` él. O **«Tengo una duda»**: graba, se añade como mensaje y
   `necesita_respuesta` para despacho.
4. Despacho ve en su bandeja lo que el taller ha hecho y las dudas.

Una modificación **no se borra nunca**. `DESCARTADA` conserva todo.

### 7.4 Transcripción y resumen

Al guardar un adjunto `AUDIO` queda en `PENDIENTE` y se encola en un ejecutor
propio de un hilo (`TranscripcionService`). Pasos:

1. `Transcriptor` (interfaz): `AzureSpeechTranscriptor` por REST (webm/opus tal
   como lo graba Chrome, sin convertir) o `TranscriptorDesactivado` si faltan las
   variables → el adjunto pasa a `NO_APLICA` y la bandeja dice «sin transcripción
   (servicio no configurado)». Idioma `es-ES`.
2. `ResumidorModificacion` con Claude (`claude-opus-5-5`, salida estructurada):
   `titulo` (una línea, lo que ve despacho en la lista), `resumen`, `tipo_cambio
   sugerido`, `materiales mencionados`, `dudas de lectura`. Se guarda en
   `adjunto.resumen_ia` y, si la modificación no tiene título, se le pone **marcado
   como de la IA**; despacho lo puede corregir.
3. Fallo en cualquiera: `FALLIDA` con el error, aviso en la ficha, botón
   «reintentar». **La modificación llega igual** y el push sale igual: la regla
   del proyecto (no bloquear por lo que una persona puede resolver escuchando).

Las transcripciones y resúmenes son texto buscable en la lista de despacho.

### 7.5 Recordatorios (anti-olvido)

Tarea programada cada hora (`@Scheduled`), reglas en `application.yml`:

```yaml
modelos:
  recordatorios:
    taller:                   # órdenes despacho→taller no hechas
      dias-por-prioridad: { ALTA: 1, NORMAL: 3, BAJA: 7 }   # cada N días, push de nuevo
    despacho:
      sin-prioridad-horas: 24      # aviso de taller sin prioridad puesta
      sin-aplicar-dias: 7          # aviso de taller sin aplicar del todo, cada 7 días
      respuesta-pendiente-horas: 24
```

Cada envío se apunta en `aviso_enviado` para no repetir dentro del plazo. Los
recordatorios van por push y se ven en la app como «vencidas» (badge con color
de aviso). Los resúmenes diario y semanal por correo quedan para cuando haya
buzón; la consulta que los alimenta (lo nuevo, lo pendiente, lo vencido) se
hace ya para el panel de despacho.

### 7.6 Push y «lo nuevo»

- **Web Push** con VAPID. Suscripción por dispositivo/usuario al aceptar el
  permiso (la app lo pide en la primera apertura, explicando para qué). Payload:
  título, modelo, URL. Sonido y vibración los pone Android; en la app abierta,
  `navigator.vibrate` y un aviso corto.
- La app abierta **consulta cada 20 s** `/api/bandeja/resumen` (contadores) y
  refresca badges; sin WebSockets ni SSE en la v1.
- Un push que falla (suscripción caducada) borra la suscripción y no rompe nada.

### 7.7 Etiqueta de patrones

- Despacho elige modelos (filtros: cliente, etapa, temporada, «sin etiqueta
  impresa») y abre **`/app/etiquetas-patrones?ids=…`**, una vista de impresión
  de la propia PWA: `@page A4`, márgenes de 10 mm, **8 etiquetas por hoja (2 × 4)**
  en papel normal, para recortar. Cada etiqueta: **QR** (45 mm), **foto
  principal**, **nombre del modelo** (lo más grande), **cliente**, **referencia**
  (o la de prototipo). Sin más texto.
- El QR lo pinta el servidor con el `CodigoQr` que ya existe
  (`GET /api/modelos/{id}/qr.png`), con el contenido **`${MODELOS_URL_PUBLICA}/m/{id}`**.
  El id es el interno y no cambia nunca, así que renombrar o fusionar no
  invalida una etiqueta pegada (al fusionar, el id que desaparece queda
  apuntado en `modelo_codigo_historico` y `/m/{id}` lo resuelve al superviviente).
- Al imprimir, un botón «Marcar como impresas» estampa `etiqueta_impresa_en`.

### 7.8 Escáner

Botón siempre a la vista en la cara de taller. `BarcodeDetector` de Chrome
Android; `@zxing/browser` de respaldo. Acepta la URL entera o solo el id;
**lee el id de la ruta `/m/{id}` sin mirar el dominio**, por si un día cambia.
Abre la ficha del modelo, desde la que se puede avisar de un cambio con el
modelo ya elegido.

### 7.9 Imágenes de los PDF y documentos del modelo

- Un PDF subido a un modelo (o como adjunto de una orden, con la casilla
  «guardar también en el modelo») se guarda en `documento_modelo` y, en la misma
  respuesta, el servidor devuelve **candidatas**: las imágenes incrustadas de al
  menos 300 × 300 px (PDFBox) y cada página renderizada a 100 ppp (máximo 20
  páginas), porque muchas fichas técnicas son dibujo vectorial sin imagen
  extraíble. Las candidatas viven en un directorio temporal 30 minutos.
- Despacho **toca cuáles son del modelo** y cuál es la principal; solo esas
  entran en `foto_modelo` por `FotosCatalogo` (reducidas a 1.600 px, como las
  demás). **Nunca se guarda ninguna sin elegir**: el catálogo se llenaría de
  logos y muestras de color.
- Las fotos de una modificación pueden **pasar a la galería del modelo** con un
  botón (son del modelo, pero no todas sirven de foto principal).

### 7.10 Usuarios, dispositivos y sesión

- `ADMIN` gestiona usuarios y dispositivos; `DESPACHO` todo lo de despacho;
  `TALLER` solo la cara de taller.
- **Primer arranque sin usuarios**: si está `MODELOS_ADMIN_PASSWORD`, se crea
  `admin` (ADMIN) con ella; si no, un `WARN` y la app arranca sin poder entrar
  en `/app`. La contraseña se cambia desde la pantalla de administración.
- **Alta de la tablet**: el admin genera un **código de 6 cifras** (10 min) para el
  usuario de taller; en la tablet, la pantalla de entrada tiene «Soy del taller»
  con un teclado numérico grande; el servidor crea el `dispositivo` y deja una
  cookie de un año (`token_hash`). Revocable desde administración. El PC de
  despacho entra con usuario y contraseña (BCrypt), sesión de 8 horas.
- Al abrir la app, «Hola, Bachir» con el nombre del usuario del dispositivo.
- Spring Security: `SecurityFilterChain` para `/app/**`, `/api/**`, `/m/**`;
  `permitAll` al resto (la app vieja sigue como hoy); CSRF con cookie para la
  SPA e **ignorado** para las rutas viejas; un filtro propio lee la cookie de
  dispositivo. **Test de cobertura de rutas**: toda ruta de `modelos.api` exige
  autenticación salvo las declaradas públicas (`/m/**` redirige al login si no
  hay sesión, pero no filtra nada).

### 7.11 Avisos y errores

Regla del proyecto: **nunca fallar en silencio, nunca bloquear por lo que una
persona puede resolver.** Transcripción, push y resumen de la IA son
**accesorios**: su fallo se ve (chip, texto corto, botón de reintentar) y no
impide que una modificación se cree, se vea o se marque hecha. Un envío de
Bachir que falla por red se queda en su pantalla con todo lo grabado y un botón
grande de reintentar.

## 8. API (JSON, `/api`)

| Grupo | Rutas (resumen) |
|---|---|
| Sesión | `POST /api/sesion` (login), `DELETE /api/sesion`, `GET /api/sesion` (quién soy, rol, nombre), `POST /api/sesion/dispositivo` (código de alta → cookie) |
| Modelos | `GET /api/modelos?cliente&etapa&temporada&q`, `GET /api/modelos/{id}` (ficha + línea de tiempo), `POST /api/modelos` (alta a mano), `PATCH /api/modelos/{id}` (nombre, tipo), `POST /api/modelos/{id}/etapa`, `POST /api/modelos/{id}/referencia` (asignar o fusionar; `?confirmar=true`), `POST /api/modelos/{id}/fusionar-con/{otroId}`, `GET /api/modelos/{id}/qr.png`, `GET /api/modelos/{id}/fotos`, `POST /api/modelos/{id}/fotos`, `POST /api/modelos/{id}/documentos` (devuelve candidatas), `POST /api/modelos/{id}/documentos/{docId}/elegir-fotos`, `GET /api/modelos/{id}/documentos/{docId}` |
| Modificaciones | `GET /api/modificaciones?direccion&estado&prioridad&modelo&q`, `POST /api/modificaciones` (multipart: campos + adjuntos), `GET /api/modificaciones/{id}`, `POST …/{id}/vista`, `POST …/{id}/prioridad`, `POST …/{id}/destinos/{destino}` (aplicado / no aplica), `POST …/{id}/hecha`, `POST …/{id}/descartar`, `POST …/{id}/mensajes` (multipart), `GET /api/adjuntos/{id}` (bytes), `POST /api/adjuntos/{id}/retranscribir`, `POST /api/adjuntos/{id}/a-galeria` |
| Bandeja | `GET /api/bandeja/resumen` (contadores por rol: nuevas, pendientes, vencidas, con respuesta) |
| Push | `GET /api/push/clave-publica`, `POST /api/push/suscripcion`, `DELETE /api/push/suscripcion` |
| Admin | `GET/POST /api/usuarios`, `PATCH /api/usuarios/{id}`, `POST /api/usuarios/{id}/codigo-alta`, `GET /api/dispositivos`, `POST /api/dispositivos/{id}/revocar` |
| Catálogo | `GET /api/clientes`, `GET /api/temporadas?cliente`, `POST /api/temporadas` (solo nombre), `GET /api/tipos-cambio`, `GET /api/destinos-aplicacion` |
| Fuera de `/api` | `GET /m/{id}` → `302 /app/modelos/{id}`; `GET /app/**` → `index.html` |

Fechas en ISO-8601, errores con `{ "mensaje": "…" }` legible (la PWA lo enseña
tal cual), paginación por `?pagina&tamano` donde haga falta.

## 9. Pantallas

### 9.1 Principios de la cara de taller

Una tarea por pantalla; **tres toques** para avisar de un cambio; objetivos
táctiles de **64 px**; foto antes que texto; icono + una palabra; colores con
significado fijo (nuevo, hecho, prioridad alta); todo texto largo tiene botón de
**escuchar** si hay audio; **nunca un teclado**; confirmación visual sin sonido;
«deshacer» en vez de diálogos salvo en «Hecho», que es en dos toques; letra
grande (base 18–20 px), tipografía de alta legibilidad (la maqueta usa Atkinson
Hyperlegible); castellano. En móvil, lo mismo a una columna.

### 9.2 Cara de taller (maqueta: `docs/Modelos/maqueta-tablet.html`)

- **Inicio**: «Hola, Bachir». Dos losas grandes: **Tengo que hacer** (con el
  número de órdenes nuevas) y **He cambiado algo**. Botón **Escanear** siempre
  visible. Debajo, rejilla de modelos por foto (filtros por cliente y etapa).
  Barra inferior: Inicio · Órdenes (badge) · Mis avisos · Modelos.
- **He cambiado algo**: elegir modelo (rejilla o escáner) → **¿Qué has
  cambiado?** (botones con icono) → **Fotos** (cámara; miniaturas; «sin foto») →
  **Grabar** (botón grande; tiempo; onda; repetir; escuchar) → **Revisar**
  (modelo, tipo, fotos, audio) → **Enviar**. Pantalla de «Enviado» con vuelta al
  inicio. Si no hay ni foto ni audio, el botón Enviar explica por qué no.
- **Tengo que hacer**: tarjetas con foto del modelo, franja de prioridad, título,
  antigüedad («hace 2 días»), chip «nueva». Orden: nuevas, luego por prioridad y
  antigüedad. Detalle: audio con un botón grande de escuchar, texto en letra
  grande, miniaturas de fotos (se abren a pantalla completa), documentos como
  fichas grandes que abren el visor. Abajo, **Hecho** (dos toques) y **Tengo una
  duda** (graba y envía).
- **Mis avisos**: lo que Bachir ha mandado, con su estado (enviado / visto /
  hecho) y las respuestas de despacho, con botón de escuchar y de contestar.
- **Ficha del modelo**: foto grande, nombre, referencia, cliente, chip de etapa,
  botón «He cambiado algo en este modelo», línea de tiempo (cambios y etapas)
  con botones de escuchar.
- **Escanear**: cámara a pantalla completa con el recuadro; al leer, abre la
  ficha. Si no hay permiso de cámara, lo dice y ofrece la rejilla.

### 9.3 Cara de despacho (PC)

- **Bandeja**: dos pestañas, **Del taller** (avisos de Bachir: nuevos sin
  prioridad arriba, luego por prioridad y antigüedad; filtros por estado, cliente,
  modelo, texto) y **Al taller** (órdenes: nuevas, vistas, hechas, con dudas).
  Columnas: modelo (foto pequeña), título, tipo, prioridad, estado, antigüedad,
  destinos aplicados (dos casillas). Contadores arriba: nuevas, pendientes,
  vencidas, con respuesta pendiente.
- **Detalle de modificación**: hilo de mensajes con audio (reproductor),
  transcripción y resumen de la IA marcado como tal, fotos, documentos;
  prioridad, destinos, descartar, responder (texto o voz), «pasar foto a la
  galería del modelo».
- **Nueva orden**: modelo (buscador con foto), título, texto, prioridad, voz,
  fotos, documentos (con «guardar también en el modelo»).
- **Modelos**: lista/cuadrícula por cliente, etapa y temporada; alta a mano
  (cliente, temporada, referencia o referencia de prototipo, nombre, tipo,
  etapa); selección múltiple → **Imprimir etiquetas de patrones**.
- **Ficha de modelo**: cabecera (foto principal, referencia o prototipo, nombre,
  cliente, etapa, temporadas, códigos históricos), acciones (**cambiar etapa**,
  **asignar referencia / fusionar**, **imprimir etiqueta**, subir fotos, subir
  PDF → elegir imágenes), galería, documentos, **línea de tiempo** completa.
- **Administración**: usuarios (alta, rol, activo, contraseña), dispositivos
  (nombre, último uso, revocar), **código de alta** para la tablet.

### 9.4 Estados vacíos

Bachir sin órdenes: «No tienes nada pendiente» con un dibujo, no una tabla
vacía. Despacho sin modelos del cliente: enlace a `/temporadas` y a «dar de
alta». Un modelo sin foto principal se pinta con la silueta y el nombre; la
etiqueta de patrones también.

## 10. Prerrequisitos fuera del código

Todo en `docs/Modelos/modelos.puntotres.com - guia para el informatico.md`: DNS
interno de `modelos.puntotres.com` a la IP privada del servidor, certificado
válido por reto DNS con renovación automática, nginx delante del 8080 con los
límites de subida y tiempos del proyecto, firewall, y la tablet (Chrome,
instalar la app, permisos de cámara, micrófono y notificaciones, ahorro de
batería). Y las claves: Azure Speech (recurso en el tenant), VAPID (se generan
una vez), contraseña inicial del admin.

## 11. Tests que anclan el diseño

- **V4**: sobre una base llevada hasta la V3 con modelos, artículos, fotos y
  vínculos de temporada, la V4 conserva todo, cada hijo apunta al id de su
  modelo, los modelos existentes quedan en `PRODUCCION`; dos prototipos `PROTO A`
  del mismo cliente en temporadas distintas conviven y en la misma no (patrón de
  `MigracionTemporadaDosExcelsTest`).
- **Catálogo**: `incorporarPedido` con el pedido real de AMI sigue dando de alta
  lo mismo que antes (los tests actuales en verde) y no toca la etapa de un
  modelo que ya existía; `/catalogo` enseña un prototipo sin código.
- **Fusión**: asignar `ULL770` a un prototipo cuando ya existe `ULL770` importado
  deja un solo modelo con las fotos y modificaciones de los dos, el histórico de
  códigos, y `/m/{idViejo}` resuelve al superviviente.
- **Ciclo**: un aviso de taller sin foto ni audio se rechaza con mensaje; con
  audio nace `NUEVA` sin prioridad; marcar los dos destinos lo pasa a `HECHA`
  solo; una orden a taller en `NUEVA` pasa a `VISTA` al abrirla y a `HECHA` con
  `hecho_por` el usuario del dispositivo; una respuesta enciende
  `necesita_respuesta` del otro lado.
- **Transcripción**: con `TranscriptorDesactivado` el adjunto queda `NO_APLICA`
  y la modificación se crea igual; con un transcriptor falso que lanza, queda
  `FALLIDA` con error y el push sale igual; el resumen de la IA marca el título
  como de la IA. Claude se prueba con un `Resumidor` falso; el real no corre en
  la suite.
- **Recordatorios**: con las reglas de prueba, una orden `ALTA` no hecha recibe
  recordatorio a las 24 h y no dos; un aviso de taller sin prioridad avisa a
  despacho a las 24 h; `aviso_enviado` evita repetir.
- **Seguridad**: cobertura de rutas de `modelos.api` (toda ruta autenticada o
  declarada pública); `TALLER` no puede crear órdenes ni cambiar etapas;
  `/menu` y las rutas viejas siguen respondiendo sin sesión; el código de alta
  caduca y se usa una vez; un dispositivo revocado recibe 401.
- **QR**: `GET /api/modelos/{id}/qr.png` codifica `${MODELOS_URL_PUBLICA}/m/{id}`
  (decodificado con ZXing, como `CodigoQrTest`); el analizador del escáner saca
  el id de `https://otro.dominio/m/123`, de `/m/123` y de `123`.
- **PDF**: de un PDF de prueba con dos imágenes grandes, un logo pequeño y una
  página vectorial salen como candidatas las dos imágenes y las páginas, no el
  logo; nada entra en `foto_modelo` sin elegir.
- **Frontend**: funciones puras (reducción de imagen, análisis del QR, reglas
  de la pantalla de revisar) con Vitest; un recorrido de humo con Playwright
  (Chromium) sobre la PWA servida por el jar: entrar con código de alta, avisar
  de un cambio con una foto de fichero, verlo en la bandeja de despacho.

## 12. Piezas

- `db/migration/V4__modelos_y_modificaciones.sql` (subconjunto común).
- `modelos/persistence/`: entidades y repositorios de §6.2; `Modelo`, `Articulo`,
  `FotoCatalogo`, `ModeloTemporada` adaptados al id (en `persistence/` de
  siempre).
- `modelos/service/`: `Modelos` (alta, etapa, referencia, fusión, línea de
  tiempo), `Modificaciones` (ciclo, destinos, mensajes), `Adjuntos`,
  `TranscripcionService` + `Transcriptor` + `AzureSpeechTranscriptor` +
  `ResumidorModificacion`, `Recordatorios` (`@Scheduled`), `Push`,
  `EtiquetasPatrones` (URL del QR), `ImagenesDePdf`, `Usuarios`, `Dispositivos`.
- `modelos/api/`: controladores de §8; `RutaQrController` (`/m/{id}`);
  `SpaController` (`/app/**`).
- `modelos/seguridad/`: `SecurityConfig`, `FiltroDispositivo`, `UsuarioActual`.
- `frontend/`: Vite + React + TS; `src/taller/` y `src/despacho/`; `src/comun/`
  (API, grabador, cámara, escáner, reducción de imagen); `public/manifest`;
  service worker de `vite-plugin-pwa` con el receptor de push.
- `pom.xml`: dependencias de §5.2 y `frontend-maven-plugin`.
- `application.yml`: bloque `modelos`, `forward-headers-strategy`.
- `menu.html`: tarjeta «Modelos y taller» → `/app/`.
- `CLAUDE.md`: sección nueva (arquitectura de dos caras, V4, dialecto SQL,
  trampa del `@Lob`, seguridad acotada, qué es accesorio y qué no); `README.md`
  y la guía de instalación: variables de entorno nuevas y nginx.

## 13. Fases (para el plan)

Cada fase compila y deja la suite en verde.

| # | Fase | Depende de |
|---|---|---|
| 0 | Maqueta probada con Bachir; correcciones al recorrido | — |
| 1 | Prerrequisitos de informática en marcha (pueden ir en paralelo con 2–4) | — |
| 2 | **V4** y catálogo adaptado al id; prototipos en `/catalogo` | — |
| 3 | Usuarios, dispositivos, código de alta, Spring Security acotado; esqueleto de la PWA con login y «Hola, Bachir» | 2 |
| 4 | Modelos en la API y en las dos caras: lista, ficha, etapa, alta a mano, fotos, línea de tiempo | 3 |
| 5 | **Modificaciones** en los dos sentidos: API, grabador, cámara, bandejas, destinos, hilo | 4 |
| 6 | Transcripción y resumen; push; contadores en vivo | 5 |
| 7 | Recordatorios; asignar referencia y fusionar; etiqueta de patrones y escáner; PDF → imágenes; documentos del modelo | 5 |
| 8 | Documentación (`CLAUDE.md`, README, guía de instalación) y lista de pendientes de §4 | todas |

## 14. Lo que queda al constructor

- Nombres exactos de iconos y textos de los botones de tipo de cambio (el yml
  los trae; la maqueta propone unos).
- Formato exacto de la etiqueta de patrones (2 × 4 por A4 es la propuesta; se
  ajusta en papel con el usuario, como se hizo con las de caja).
- Si la reducción de la foto se hace solo en el navegador o también en el
  servidor (recomendado: las dos, la del servidor como red).
- El modelo de Claude y el esfuerzo del resumen (`claude-opus-5-5`, esfuerzo
  bajo, según la regla del proyecto); medir coste con `count_tokens` como se
  hizo con la extracción de fotos.
- Qué pasa con `/catalogo` a medio plazo: hoy sigue como está (con el chip de
  etapa); si la ficha de despacho de la app nueva cubre lo mismo, se decide
  entonces si una de las dos sobra.
