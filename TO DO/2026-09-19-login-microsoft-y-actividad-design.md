# Login de Microsoft, permisos por sección y dashboard de actividad

Fecha: 2026-09-19. Estado: **diseño, sin implementar**. La aplicación se queda de momento en
localhost y en el `.jar` del PC del almacén; este documento es el plan para cuando se retome.

Cubre todas las piezas de una vez, dashboard incluido, porque así se pidió. Si al retomarlo
conviene partirlo, las fases de la sección 13 ya están ordenadas por dependencia y cada una deja
la aplicación compilando y con la suite en verde.

## 1. Qué se construye

1. **Login con las cuentas Microsoft de Puntotres** (Entra ID). Sin login no se ve nada.
2. **Usuarios en base de datos**, dados de alta solos la primera vez que entran, sin permisos.
3. **Permisos por sección**: cada usuario ve en el menú y puede abrir solo lo que tenga concedido.
4. **Registro de actividad**: quién hizo qué, cuándo, de qué cliente y cuántos documentos salieron.
5. **Dashboard de actividad** para leer ese registro.
6. **Endurecimiento** de la aplicación para que pueda salir de la red local: PostgreSQL con
   migraciones versionadas, secretos fuera del código, cabeceras, sesión y copias.

**Fuera de este diseño**, por decisión explícita: la capa de red (publicación en
`app.puntotres.com`, TLS, cortafuegos, aislamiento de la máquina) la montan los informáticos de la
empresa. La sección 11 recoge lo que hay que pedirles para que el diseño se sostenga; lo demás no
se especifica aquí.

## 2. Lo que hay hoy en el repo y cómo encaja

- Spring Boot 3.5.3, Java 17, Thymeleaf, JPA sobre **H2 en fichero** (`./datos/packinglist`),
  `ddl-auto: update`, **sin Flyway**, **sin Spring Security**, **sin ningún concepto de usuario**.
- Tres tablas hoy: taras, memoria de referencias y temporadas guardadas. Migrar a PostgreSQL nunca
  va a costar menos que ahora.
- Estado del asistente en **sesión HTTP** (`EnvioEnCurso`, `TallerEnCurso`, `EscandallosEnCurso`,
  `EtiquetasArticuloEnCurso`), con los excels generados y los ficheros subidos **dentro**.
- Las descargas (`/descargar/{nombreFichero}`, `/descargar-etiquetas/...`) buscan el fichero **en
  la lista de la sesión**, no en el disco. Eso ya da dos propiedades que hay que conservar: no hay
  recorrido de rutas posible, y **un usuario no puede descargar los ficheros de otro** aunque
  adivine el nombre. La excepción es `/temporadas/descargar/{id}`, que sí lee de la base de datos
  por id y por tanto depende del permiso de sección.
- Distribución actual: un `.jar` en el PC del almacén. El README ya avisa de que *"la aplicación no
  tiene usuarios ni contraseña, así que solo dentro de la red de la empresa"*. Este diseño es lo
  que retira esa frase.
- Convenciones que se respetan: nombres de clase en inglés, campos/métodos/javadoc/UI en español;
  URLs en español; servicios que devuelven resultados con avisos en vez de lanzar; formularios sin
  JavaScript; una sola hoja `estilo.css`; portada en `/menu` con tarjetas.
- **Interacción con el diseño de Envíos DHL** (aprobado y sin implementar): allí el campo
  `responsable` se dejó como texto *"para que cuando llegue el login de Microsoft se rellene solo"*.
  Cuando se haga esto, ese campo pasa a rellenarse con el usuario de la sesión y deja de ser un
  desplegable del yml. La lista `envios.responsables` se retira entonces.

## 3. Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Quién entra | **Solo cuentas del tenant de Puntotres.** Sin invitados, sin externos, sin usuarios locales. No hay registro, ni recuperación de contraseña, ni contraseñas que custodiar. |
| Dónde corre | Servidor propio de la empresa (VM Linux), publicado en `app.puntotres.com`. **La red la montan los informáticos.** |
| Enfoque de seguridad | **La aplicación se ocupa de la suya** (Spring Security + OIDC contra Entra). Se descartó delegar en una cabecera del proxy: no arranca en local sin falsearla, se rompe al cambiar de proxy, y si el servidor queda alcanzable por otra ruta esa cabecera es "soy quien yo diga". Si los informáticos añaden preautenticación en el borde, suma como segunda capa sin que la app se entere. |
| Permisos | **Pantalla propia dentro de la app**, no grupos de Entra: añadir o quitar una sección a alguien no puede depender de que un informático abra el portal de Azure. Las bajas siguen siendo automáticas: cuenta desactivada en Entra = no entra. |
| Qué se registra | **Acciones con significado + accesos.** No navegación por pantallas: `/revision` visitada 40 veces no dice nada; "35 packing lists de APC en septiembre" sí. |
| Base de datos | **PostgreSQL + Flyway ahora.** Con usuarios, permisos, histórico y (con DHL) facturas y guías dentro, `ddl-auto: update` sobre un fichero H2 es una pérdida de datos esperando: no hay forma segura de cambiar una columna ni de volver atrás. |
| Alcance | Todo de una vez, dashboard incluido. |

## 4. Identidad: login con Entra ID

### 4.1 Registro de la aplicación en el directorio (fase 0, fuera del código)

Lo hace quien tenga permiso en el portal de Entra. Hay que dejar apuntado:

- **Tipo de cuenta**: *Accounts in this organizational directory only* (single tenant).
- **URIs de redirección** (tipo Web): `https://app.puntotres.com/login/oauth2/code/azure` y
  `http://localhost:8080/login/oauth2/code/azure`. Entra admite `http` para `localhost`, así que
  **el desarrollo usa el login de verdad** y no hace falta ninguna puerta trasera de "perfil local
  sin seguridad", que es justo la clase de interruptor que acaba puesto en producción.
- **Permisos**: solo `openid`, `profile`, `email`. Nada de Microsoft Graph: la aplicación no
  necesita leer el directorio.
- **Client secret**: caduca (máximo 24 meses). Hay que anotar **la fecha de caducidad y quién lo
  custodia** el mismo día que se crea; el fallo es silencioso hasta que un lunes nadie puede entrar.

Se guardan tres valores: `ENTRA_TENANT_ID`, `ENTRA_CLIENT_ID`, `ENTRA_CLIENT_SECRET`.

### 4.2 Dependencias y configuración

```xml
<dependency>org.springframework.boot:spring-boot-starter-oauth2-client</dependency>
<dependency>org.thymeleaf.extras:thymeleaf-extras-springsecurity6</dependency>
```

Se usa el cliente OAuth2 estándar de Spring, **no el starter de Azure**: el proveedor se describe
entero con una línea de `issuer-uri` y así no entra un árbol de dependencias de SDK para algo que
es un OIDC normal.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          azure:
            client-id: ${ENTRA_CLIENT_ID}
            client-secret: ${ENTRA_CLIENT_SECRET}
            scope: openid, profile, email
        provider:
          azure:
            issuer-uri: https://login.microsoftonline.com/${ENTRA_TENANT_ID}/v2.0
```

El `issuer-uri` con el tenant dentro es lo que impide que entre nadie de otro directorio: la
validación del `iss` del token lo rechaza. No hace falta comprobar el `tid` a mano.

### 4.3 El claim que manda es `oid`, no el correo

De la identidad se guarda el **`oid`** (identificador de objeto en el directorio), no el correo. El
correo cambia: alguien se casa y cambia de apellido, o cambia de nombre de usuario, y el directorio
lo cambia con él. Si la clave fuera el correo, ese día la persona entraría como **usuario nuevo sin
permisos** y su histórico de actividad se quedaría huérfano a nombre de una dirección que ya no
existe. Con el `oid` la fila es la misma persona para siempre y el correo es solo un dato que se
refresca en cada entrada.

### 4.4 Alta automática, permisos ninguno

Un `OidcUserService` propio, en cada login:

1. Busca el `Usuario` por `oid`. Si no está, lo crea: `activo = true`, **sin ningún permiso**.
2. Refresca `correo` y `nombre` con lo que venga del token.
3. Estampa `ultimoAcceso` y registra un evento `ENTRADA`.
4. Devuelve el `OidcUser`.

Entrar en el tenant no da acceso a nada: quien entra por primera vez ve el menú vacío y un texto que
dice a quién pedirle permisos. Es lo contrario de una lista negra, y es lo correcto: el día que
entre en la empresa alguien de administración, no verá los escandallos por defecto.

**El primer administrador no puede salir de la propia aplicación** (no hay nadie que se lo
conceda). Se resuelve con una lista de correos en el yml:

```yaml
seguridad:
  administradores-iniciales: [ jllavinapamias@puntotres.com ]
```

Se aplica **solo en el alta** de ese usuario, no en cada login: si no, quitarle el administrador a
alguien de esa lista sería imposible desde la pantalla, y la lista se quedaría mandando para siempre
desde un fichero que nadie vuelve a mirar.

## 5. Modelo de datos

```
usuario
  id                bigserial PK
  oid_entra         varchar(64)  UNIQUE NOT NULL   -- el identificador inmutable del directorio
  correo            varchar(255) NOT NULL          -- se refresca en cada entrada
  nombre            varchar(255) NOT NULL
  administrador     boolean      NOT NULL DEFAULT false
  activo            boolean      NOT NULL DEFAULT true
  fecha_alta        timestamptz  NOT NULL
  ultimo_acceso     timestamptz

usuario_permiso                                    -- una fila por sección concedida
  usuario_id        bigint  FK usuario(id) ON DELETE CASCADE
  seccion           varchar(32)                    -- nombre del enum Seccion
  PK (usuario_id, seccion)

evento_actividad
  id                bigserial PK
  usuario_id        bigint  FK usuario(id)         -- puede ser null: ver abajo
  correo            varchar(255) NOT NULL          -- copia, a propósito
  momento           timestamptz  NOT NULL
  seccion           varchar(32)  NOT NULL
  tipo              varchar(48)  NOT NULL
  cliente           varchar(64)                    -- AMI, APC... null si no aplica
  cantidad          integer                        -- nº de ficheros, de cajas... null si no aplica
  detalle           varchar(500)                   -- frase corta, legible
  INDEX (momento), INDEX (usuario_id, momento), INDEX (tipo, momento)
```

Dos decisiones que no se deducen de la tabla:

- **El correo se copia en cada evento** aunque el usuario esté enlazado. El informe de hace un año
  tiene que seguir diciendo quién era esa persona **entonces**, y el correo del directorio puede
  haber cambiado desde entonces. El `usuario_id` sirve para agrupar; el correo, para leer.
- **Los usuarios no se borran, se desactivan.** Borrar una fila deja el histórico apuntando a un id
  que ya no existe, y el histórico es justamente lo que se quiere conservar. Un usuario inactivo no
  puede entrar aunque su cuenta de Microsoft siga viva.

Un evento con `usuario_id` nulo es posible en un solo caso: `ACCESO_DENEGADO` de alguien que aún no
tenga fila. No debería pasar, porque el alta es automática, pero la columna lo admite antes que
perder el registro de un intento.

## 6. Secciones y permisos

`Seccion` es un enum, y **es el único sitio donde vive el mapa de rutas a permisos**:

| Sección | Rutas | Qué produce |
|---|---|---|
| `PACKING_LIST` | `/packing-list**`, `/importar`, `/revision`, `/recalcular`, `/alternar-fila`, `/packing-puntotres`, `/generar`, `/resultados`, `/descargar**`, `/nuevo` | Packing lists, etiquetas de caja, volcado ERP, Packing Puntotres |
| `ETIQUETAS_ARTICULO` | `/etiquetas-articulo`, `/etiquetas-articulo-produccion**` | Etiquetas de pieza |
| `ESCANDALLOS` | `/clickup**` | Excel de escandallos para ClickUp |
| `TARAS` | `/taras` | Pesos de cartón vacío |
| `TEMPORADAS` | `/temporadas**` | Excels de pedido guardados por temporada |
| `ACTIVIDAD` | `/actividad**` | Dashboard de uso |
| `ADMINISTRACION` | `/administracion**` | Usuarios y permisos |

`/`, `/menu`, `/login**`, `/error` y los estáticos son accesibles a cualquiera **autenticado**.
Nada es accesible sin autenticar.

`ADMINISTRACION` no es una sección concedible como las demás: la abre solo `administrador = true`.
`ACTIVIDAD` sí es concedible, porque tiene sentido que un jefe de producción vea el uso sin poder
tocar permisos.

Las rutas del packing list cuelgan hoy de la raíz (`/importar`, `/generar`, `/revision`...) en vez
de agruparse bajo `/packing-list/`. Reordenarlas sería más limpio, pero rompe marcadores y toca
todas las plantillas: **no se hace**. Se declaran una a una en el enum, y el test de la sección 6.2
es lo que impide que esa lista se quede coja.

### 6.1 Cómo se aplica

Un `InterceptorPermisos` (`HandlerInterceptor` registrado para `/**`) que resuelve la sección de la
URL con `Seccion.deRuta(uri)` y pregunta a un `UsuarioActual` de ámbito petición.

**Por qué un interceptor y no `requestMatchers` en el `SecurityFilterChain`**, que sería lo
canónico, por dos razones concretas:

1. La lista de rutas quedaría **duplicada** — una vez en la cadena de seguridad y otra en el menú,
   que también necesita saber qué tarjetas pintar. Con el enum, añadir una sección es **una
   constante**, y las dos cosas salen de ahí.
2. Las autoridades de Spring Security se calculan **en el login** y viven en la sesión. Si quitas un
   permiso a alguien que está dentro, no pasaría nada hasta que volviera a entrar. El interceptor
   lee de la base de datos en cada petición (una consulta trivial, cacheada por petición en
   `UsuarioActual`), así que **quitar un permiso surte efecto en el siguiente clic**.

El menú filtra las tarjetas con el mismo enum. **Esconder la tarjeta no es seguridad** — la
seguridad es el interceptor; esconderla es no enseñar puertas que no se pueden abrir.

Un acceso denegado no es un 403 en blanco: es una pantalla que dice qué sección es y a quién
pedirla, y deja un evento `ACCESO_DENEGADO`.

### 6.2 El test que impide que nazca una ruta sin dueño

Un test recorre por reflexión **todas** las anotaciones `@GetMapping`/`@PostMapping` de
`com.puntotres.packinglist.web` y exige que cada ruta esté reclamada por exactamente una `Seccion` o
declarada explícitamente como pública. Sin él, un endpoint nuevo nace **sin protección y sin que
nada avise**, que es la forma más normal de perder una aplicación entera por una feature pequeña.

## 7. Registro de actividad

Un servicio `RegistroActividad` con llamadas **explícitas** desde los controladores. Se descarta una
anotación con aspecto: sería la primera magia del proyecto, y sobre todo el valor del evento está en
el detalle (*qué cliente, cuántos ficheros*), que un aspecto genérico no sabe calcular.

`TipoEvento` (enum cerrado y enumerable, que es lo que permite agrupar en el dashboard):

| Tipo | Se emite en | Detalle que lleva |
|---|---|---|
| `ENTRADA` | login | — |
| `ENVIO_IMPORTADO` | `POST /importar` | cliente, modo (JSON / fotos / taller), nº de cajas |
| `EXTRACCION_FOTOS` | `POST /importar` en modo CLAUDE | nº de páginas, duración, nº de referencias |
| `PACKING_LIST_GENERADO` | `POST /generar` | cliente, nº de ficheros, nº de destinaciones |
| `ETIQUETAS_CAJA_GENERADAS` | `POST /generar` | cliente, nº de ficheros |
| `PACKING_PUNTOTRES_IMPRESO` | `POST /packing-puntotres` | cliente |
| `VOLCADO_ERP_DESCARGADO` | `GET /descargar-volcado-erp` | cliente |
| `ETIQUETAS_ARTICULO_GENERADAS` | `POST /etiquetas-articulo-produccion/generar` | cliente, nº de ficheros |
| `ESCANDALLOS_PROCESADOS` | `POST /clickup/procesar` | nº de escandallos |
| `TARA_EDITADA` | `POST /taras` | tamaño y peso nuevo |
| `TEMPORADA_GUARDADA` / `TEMPORADA_BORRADA` | `POST /temporadas`, `/temporadas/borrar` | cliente, temporada |
| `PERMISOS_CAMBIADOS` | pantalla de administración | a quién y qué |
| `USUARIO_DESACTIVADO` / `USUARIO_REACTIVADO` | ídem | a quién |
| `ACCESO_DENEGADO` | interceptor | sección intentada |

**Qué NO se guarda, a propósito**: ni el JSON del envío, ni el contenido de los excels, ni los
ficheros subidos, ni los nombres de fichero completos con datos de cliente. El registro es de
**metadatos de uso**, no un archivo paralelo de los datos de los clientes; guardarlo todo
convertiría esta tabla en el objetivo más goloso de toda la aplicación.

**Un fallo escribiendo el evento no puede tumbar un envío.** La regla del proyecto es *nunca fallar
en silencio, nunca bloquear por algo que un humano puede resolver*, y aquí eso significa: se captura
la excepción, se deja un `WARN` en el log —que no es silencio— y el packing list se genera igual.
Que no se pueda apuntar quién generó un documento no es razón para no generarlo.

**No se purga.** A este volumen (unos pocos miles de eventos al año) la tabla no es un problema, y
borrar el histórico es exactamente lo contrario de lo que se quiere de él.

## 8. Pantalla de administración (`/administracion/usuarios`)

Una tabla con todos los usuarios: nombre, correo, alta, último acceso, si es administrador, si está
activo, y una casilla por sección. Un solo botón de guardar, como el resto de la aplicación
(formulario Thymeleaf, sin JavaScript, submit que recarga).

Reglas:

- Un administrador **no puede quitarse a sí mismo el administrador** ni desactivarse. Quedarse sin
  ningún administrador deja la aplicación sin forma de conceder permisos salvo tocando la base de
  datos a mano.
- Cada cambio deja su evento.
- No hay botón de borrar: se desactiva (sección 5).

## 9. Dashboard de actividad (`/actividad`)

Filtros arriba: rango de fechas (por defecto los últimos 30 días), usuario y sección. Debajo:

1. **Cuatro cifras** del periodo: personas que han entrado, accesos, documentos generados, envíos
   procesados.
2. **Por usuario**: último acceso, nº de accesos, nº de documentos generados, secciones que usa.
3. **Por sección**: nº de acciones, con una barra proporcional.
4. **Por cliente**: cuántos packing lists y etiquetas han salido de AMI, APC y el resto.
5. **Cronología**: los últimos 100 eventos del filtro, en una tabla legible.
6. **Exportar a Excel** el resultado del filtro, con POI, que ya es una dependencia del proyecto.

Sin librería de gráficos y sin JavaScript, coherente con el resto: las barras son un `div` con un
ancho en porcentaje. Ojo con un detalle: ese ancho suele escribirse como `style` en línea, lo que
obligaría a abrir la CSP con `unsafe-inline` (sección 10). Se evita con clases de tramo fijo
(`barra-5`, `barra-10`, ... `barra-100`) declaradas una vez en `estilo.css`, que además es más
coherente con cómo está escrita la hoja hoy.

Las agregaciones son JPQL con `group by` sobre `evento_actividad`, apoyadas en los tres índices de
la sección 5. Nada de cargar los eventos en memoria y contarlos en Java: a los dos años esa página
tardaría segundos sin ninguna necesidad.

Quién lo ve: quien tenga la sección `ACTIVIDAD`. Por defecto, solo los administradores.

## 10. Endurecimiento de la aplicación

Esto es la respuesta a *"¿qué seguridad necesito para que no se acceda a los datos?"* en la parte
que depende del código.

| Qué | Cómo | Por qué importa aquí |
|---|---|---|
| **CSRF** | Activo (por defecto con Spring Security). Los formularios Thymeleaf con `th:action` inyectan el token solos. | Hay que **probar uno a uno los POST**, incluidos los multipart y los que hacen submit del mismo formulario a otra acción (`/alternar-fila`, `/packing-puntotres`, `/previsualizar`). Un POST que se olvide del token falla en tiempo de ejecución, no de compilación. |
| **Cabeceras** | `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy` y una **CSP estricta** `default-src 'self'` | La aplicación no carga nada de ningún CDN ni tiene JavaScript externo, así que aquí cabe una CSP que en otras aplicaciones sería impensable. Desaprovecharlo sería absurdo. |
| **HSTS** | Lo pone quien termine el TLS (el proxy). Si lo pone la app, solo con TLS delante. | — |
| **Cookie de sesión** | `secure`, `http-only`, `same-site=lax` | — |
| **Timeout de sesión** | **8 horas**, no 30 minutos | La sesión **contiene el envío en curso**. Un timeout corto le borra a un operario el trabajo de la mañana en mitad de una revisión de 200 cajas. |
| **Memoria** | `-Xmx` explícito en el arranque del servicio | Con 8 h de sesión, varios usuarios y los excels generados **dentro** de la sesión, la memoria retenida crece de verdad. Hay que medirla antes de publicar y decidir si el estado pasa a fichero temporal. Es el riesgo técnico más concreto de pasar de un usuario a varios. |
| **Secretos** | `ENTRA_CLIENT_SECRET`, `ANTHROPIC_API_KEY` y la contraseña de PostgreSQL como **variables de entorno del servicio systemd**, en un fichero con permisos `600` | Nunca en el `application.yml` de dentro del jar (viaja con él) ni en git. Y anotada la caducidad del secreto de Entra. |
| **Consola H2** | Nunca habilitar | — |
| **Actuator** | No está. Si entra, solo `/actuator/health` y el resto cerrado | — |
| **Subidas** | Ya limitadas (20 MB / 60 MB). Mantener los límites de descompresión por defecto de POI | Un `.xlsx` es un zip: bajar `ZipSecureFile.setMinInflateRatio` abre la puerta a una bomba de descompresión. Hoy no se toca; que siga así. |
| **Logs** | Revisar que la extracción por fotos no vuelque el JSON del envío ni fragmentos del token | Los datos de cliente no pueden acabar en un fichero de log que se copia sin pensar. |
| **Dependencias** | `mvn versions:display-dependency-updates` y OWASP dependency-check antes de cada publicación | 69 MB de jar son muchas dependencias transitivas. |
| **Copias** | `pg_dump` diario **fuera de la máquina**, con una restauración probada | Una copia que no se ha restaurado nunca no es una copia. |

## 11. Lo que hay que pedirle a los informáticos

Lista corta, para entregársela tal cual:

1. **Sin puertos de entrada desde internet** si se puede: publicación por túnel de salida
   (Cloudflare Tunnel o Entra Application Proxy). Si tiene que haber NAT al 443, la VM va en **VLAN
   aislada sin acceso al resto de la LAN**: el riesgo de un servidor propio expuesto no es que
   entren en la aplicación, es que desde ella lleguen al ERP y a las carpetas compartidas.
2. **TLS** con certificado válido para `app.puntotres.com` y **renovación automática**.
3. Cabeceras `X-Forwarded-*` correctas en el proxy, y `server.forward-headers-strategy: framework`
   en la aplicación, o las URLs de redirección del login saldrán en `http` y el login fallará.
4. **Hipervisor de servicio** (Hyper-V o Proxmox), no VirtualBox: VirtualBox es un hipervisor de
   escritorio, corre como proceso de un usuario del host y se para cuando ese usuario cierra sesión.
5. **Copia diaria fuera de la máquina**, con restauración probada, y cifrado del disco de la VM.
6. Actualizaciones de seguridad del sistema **automáticas**; acceso de administración solo por SSH
   con clave, no con contraseña.
7. DNS interno para que `app.puntotres.com` resuelva también **dentro** de la oficina: con
   port-forward clásico el router no suele hacer el giro, y la aplicación "funciona desde casa y no
   desde la oficina".
8. Registro de la aplicación en Entra: quién lo hace, quién custodia el client secret y **cuándo
   caduca**.

## 12. Tests

- `@SpringBootTest` con MockMvc y `oidcLogin()` para simular la identidad; nada de perfiles que
  desactiven la seguridad.
- **Cobertura de rutas** (sección 6.2): toda ruta declarada tiene sección o es pública.
- **Permisos**: usuario sin la sección → 403 y evento `ACCESO_DENEGADO`; el menú no pinta la tarjeta.
- **Alta automática**: el primer login crea el usuario sin permisos; el segundo no duplica; un
  cambio de correo en el token actualiza la fila y **no** crea otra (es la prueba del `oid`).
- **Permiso revocado en caliente**: se quita el permiso a un usuario con sesión abierta y su
  siguiente petición ya es 403.
- **Registro**: generar un packing list deja un evento con cliente y nº de ficheros; un fallo del
  repositorio de eventos **no** impide que la respuesta traiga los excels.
- **Dashboard**: agregaciones correctas sobre eventos sembrados, incluidos los bordes del rango.
- **Base de datos**: los tests de repositorio contra **PostgreSQL con Testcontainers** (requiere
  Docker); si no hay Docker, H2 en `MODE=PostgreSQL` como alternativa peor pero viable. El perfil
  `test` sigue fijado en surefire y no con `@ActiveProfiles`, por el motivo que ya documenta el pom.
- Los tests unitarios existentes (JUnit puro, sin contexto) **no cambian**, y `Main.java` tampoco:
  no levanta Spring, así que sigue funcionando sin login.

## 13. Fases de implementación

Cada fase compila y deja la suite en verde.

| # | Fase | Tamaño | Depende de |
|---|---|---|---|
| 0 | Registro de la app en Entra y custodia del secreto (fuera del código) | S | — |
| 1 | **Flyway con baseline** sobre el esquema que hoy crea Hibernate; `ddl-auto: validate` | M | — |
| 2 | **PostgreSQL**: dependencia, `docker-compose` para desarrollo, traspaso de los datos de H2, Testcontainers | M | 1 |
| 3 | **Login Entra**: dependencias, configuración, `OidcUserService`, entidad `Usuario`, alta automática, administradores iniciales, cabecera con el nombre y botón de salir | L | 2 |
| 4 | **Secciones y permisos**: enum `Seccion`, tabla de permisos, `InterceptorPermisos`, menú filtrado, pantalla de administración, test de cobertura de rutas | L | 3 |
| 5 | **Registro de actividad**: entidad, servicio, enum, llamadas en cada controlador | M | 4 |
| 6 | **Dashboard** `/actividad`: consultas agregadas, pantalla, filtros, exportación a Excel | L | 5 |
| 7 | **Endurecimiento**: CSP y cabeceras, cookie y timeout, secretos fuera, revisión de logs, medición de la memoria de sesión | M | 3 |
| 8 | **Documentación**: README (retirar el aviso de "sin usuarios ni contraseña"), ARCHITECTURE, CLAUDE.md | S | todas |

La fase 1 es **la misma tarea** que la Task 1 del plan de Envíos DHL
(`docs/superpowers/plans/2026-09-18-envios-dhl-express-fase1.md`). Si se acomete DHL antes, esta
fase ya estará hecha y no se repite.

Las fases 1-2 son útiles por sí solas aunque nunca se publique nada: son deuda que ya existe hoy.
Las fases 3-4 son las que permiten sacar la aplicación de la red local. Las 5-6 son las que
responden a la pregunta original: quién usa esto y para qué.

## 14. Fuera de alcance explícito

- **MFA y políticas de contraseña**: son de Entra, no de la aplicación. Si la empresa exige segundo
  factor, este login lo hereda sin una línea de código.
- Usuarios externos, invitados B2B y cualquier identidad que no sea del tenant.
- API REST pública, tokens de servicio, integraciones máquina a máquina.
- **Auditoría de cambios de datos** (el *antes y después* de una tara). Se registra que alguien la
  cambió y a qué valor, no un diff completo.
- Limitación de peticiones y protección contra abuso: sin tráfico anónimo no hay nada que limitar.
- Toda la capa de red (sección 11).

## 15. Lo que hay que decidir al retomarlo

1. **Quién administra Entra** y puede crear el registro de la aplicación.
2. **Qué licencia de Microsoft 365** tiene la empresa: decide si Entra Application Proxy (requiere
   Entra ID P1, incluida en Business Premium) es una opción para los informáticos.
3. **Quién controla el DNS de `puntotres.com`** y puede crear `app.puntotres.com`.
4. Si el dashboard lo ve alguien más que los administradores.
5. Si al llegar aquí ya está hecho DHL, **retirar `envios.responsables`** y rellenar el responsable
   con el usuario de la sesión (sección 2).
