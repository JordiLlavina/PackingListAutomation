# modelos.puntotres.com — guía para el informático

Fecha: 2026-11-08 (revisada el 2026-11-09, con la aplicación ya programada).
Para quien administra la red y el servidor de Puntotres.
Lo que se pide aquí es **solo para la red local**; la salida a internet es otro
proyecto y se describe al final para que lo que se haga hoy no estorbe mañana.

## 1. Contexto en dos párrafos

El generador de packing lists ya corre en el servidor del almacén como un único
`.jar` con su servidor web dentro, en el puerto **8080**, arrancado como servicio
(`/etc/systemd/system/packinglist.service`, carpeta `/opt/packinglist`, base de
datos en `/opt/packinglist/datos`; ver «Instalación en el servidor»). Hasta hoy
se usa desde PCs del almacén por `http://<servidor>:8080`.

Dentro de ese mismo programa va a entrar una **segunda aplicación**: el técnico
de bolsos (Bachir) avisará de los cambios que hace en los modelos desde una
**tablet Android** con notas de voz y fotos, y recibirá órdenes de despacho.
El navegador de la tablet **no permite usar la cámara ni el micrófono, ni
instalar la aplicación en la pantalla de inicio, ni recibir notificaciones, si
la página no se sirve por HTTPS** con un certificado válido (solo `localhost` se
libra). Por eso hace falta un nombre, un certificado y un nginx delante del
8080, aunque todo quede dentro de la red local.

## 2. Lo que se pide (resumen)

| # | Qué | Quién | Cómo se comprueba |
|---|---|---|---|
| 1 | El nombre **`modelos.puntotres.com`** resuelve a la IP privada del servidor **desde toda la red local**, incluida la WiFi de la tablet | Informática | `nslookup modelos.puntotres.com` desde un PC y desde la tablet da la IP privada |
| 2 | **Certificado TLS válido** (no autofirmado) para ese nombre, con renovación automática | Informática | El navegador muestra el candado sin avisos |
| 3 | **nginx** en el servidor: HTTPS en el 443 → `http://127.0.0.1:8080`, con los límites de subida y tiempos del apartado 5 | Informática | `https://modelos.puntotres.com/menu` abre la aplicación actual |
| 4 | Cortafuegos: **443** (y 80 para la redirección) abiertos a la red local; **8080 cerrado** a la red una vez funcione nginx | Informática | Desde un PC, `:8080` no responde y `https://` sí |
| 5 | La **tablet**: Chrome al día, en la WiFi, con la app instalada y los permisos concedidos | Informática + Jordi | Apartado 7 |
| 6 | Variables de entorno nuevas del servicio (apartado 8) | Jordi da los valores, informática los pone | El servicio arranca sin `WARN` de configuración |

## 3. DNS interno

`puntotres.com` tiene su DNS público fuera. Hay dos formas válidas y la elección
es vuestra:

- **Registro público** `modelos.puntotres.com → A <IP privada>` (por ejemplo
  `192.168.1.20`). Es legal y seguro: desde fuera no lleva a ningún sitio, y
  desde dentro resuelve bien. Es lo más sencillo si el DNS interno de la oficina
  reenvía al público.
- **Zona interna** (split-horizon) en el DNS de la oficina con ese registro, y
  el día de salir a internet se crea el público con la IP pública.

Lo que importa: que la **tablet por WiFi** lo resuelva. Si la WiFi de la nave
usa otro DNS (el del router), hay que comprobarlo ahí también.

## 4. Certificado

Recomendado: **Let's Encrypt con reto DNS-01**. No hace falta abrir ningún
puerto a internet: la validación se hace creando un registro TXT en el DNS de
`puntotres.com`. Opciones, de más a menos automática:

1. **Caddy** en vez de nginx: hace certificado, renovación y proxy en un fichero
   de 10 líneas, con el plugin DNS del proveedor donde esté `puntotres.com`
   (Cloudflare, OVH, IONOS, Route 53…). Si os vale, el apartado 5 se traduce
   casi línea a línea.
2. **certbot** con el plugin DNS del proveedor (`certbot-dns-cloudflare`, etc.),
   renovación por el timer de systemd que instala certbot.
3. **acme.sh** con el API del DNS, si el proveedor no tiene plugin de certbot.
4. Si el proveedor de DNS no tiene API: reto DNS-01 **manual** cada 90 días
   (`certbot certonly --manual --preferred-challenges dns`). Funciona, pero
   alguien tiene que acordarse: apuntadlo en el calendario.

Alternativa **no recomendada**: certificado de una CA interna instalada en la
tablet como CA de confianza. Chrome en Android lo acepta, pero hay que
instalarlo en cada dispositivo (y en los PCs de despacho), y no sirve el día de
salir a internet.

## 5. nginx

Servidor Debian/Ubuntu, `apt install nginx`. Fichero
`/etc/nginx/sites-available/modelos.puntotres.com` (enlazado en
`sites-enabled`):

```nginx
server {
    listen 80;
    server_name modelos.puntotres.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name modelos.puntotres.com;

    ssl_certificate     /etc/letsencrypt/live/modelos.puntotres.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/modelos.puntotres.com/privkey.pem;
    # Dejad los ssl_protocols / ssl_ciphers que recomiende certbot (include options-ssl-nginx.conf).

    # La aplicación de modelos vive en /app/. La portada de siempre sigue en /menu.
    location = / { return 302 /app/; }

    # Subidas grandes: la carpeta de fotos de una temporada (documentos del corte)
    # puede pesar cientos de MB; la aplicación admite 1 GB.
    client_max_body_size 1g;

    # La lectura de hojas manuscritas por IA tarda ~4 min; no cortar antes.
    proxy_read_timeout  900s;
    proxy_send_timeout  900s;

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        # $remote_addr y no $proxy_add_x_forwarded_for: la aplicación limita los
        # intentos de alta de una tablet por IP, y con el otro un cliente podría
        # mandar su propia cabecera X-Forwarded-For y hacerse pasar por otra IP.
        proxy_set_header   X-Forwarded-For   $remote_addr;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_set_header   X-Forwarded-Host  $host;
        proxy_request_buffering off;   # las subidas grandes van pasando, no se guardan enteras en disco antes
    }
}
```

`nginx -t && systemctl reload nginx`. HSTS (`Strict-Transport-Security`) se puede
añadir cuando lleve unas semanas funcionando bien; al principio mejor sin él,
por si hay que volver atrás.

**Por nuestra parte** (lo hace Jordi en la aplicación, no hace falta que lo
hagáis vosotros): la aplicación leerá las cabeceras `X-Forwarded-*` para componer
bien sus URLs (`server.forward-headers-strategy: framework`) y, cuando nginx esté
en marcha, escuchará **solo en `127.0.0.1:8080`** (`server.address`), de modo que
el 8080 deje de estar expuesto a la red aunque el cortafuegos lo dejara pasar.
Las dos líneas ya están escritas, comentadas, en el bloque `server` del
`application.yml` del programa (`#forward-headers-strategy: framework` y
`#address: 127.0.0.1`); el día de activarlas basta con ponerlas en el
`application.yml` que hay junto al jar en `/opt/packinglist`, sin recompilar.
Avisadnos cuando el nginx esté para activar las dos cosas.

## 6. Cortafuegos

- Entrada desde la red local: **443/tcp** y **80/tcp** permitidos.
- **8080/tcp**: cerrado desde la red en cuanto nginx funcione (hasta entonces,
  como hoy).
- Salida del servidor a internet: la aplicación llama a `api.anthropic.com`
  (lectura de hojas manuscritas, ya hoy) y, con esta nueva parte, a
  `*.cognitiveservices.azure.com` / `*.api.cognitive.microsoft.com` (voz a
  texto de Azure) y a los servidores de notificaciones push de Google y Mozilla
  (`fcm.googleapis.com`, `*.push.services.mozilla.com`, `*.notify.windows.com`).
  Si la salida está filtrada, hay que permitir esos destinos.

## 7. La tablet (y los móviles)

Android con **Chrome actualizado** (la app usa cámara, micrófono y notificaciones
del navegador). Pasos, una vez por dispositivo:

1. Conectarla a la WiFi de la nave; comprobar que `https://modelos.puntotres.com/app/`
   abre con candado.
2. En Chrome, menú ⋮ → **«Instalar aplicación»** (o «Añadir a pantalla de inicio»).
   Queda como una app más, a pantalla completa.
3. Abrir la app desde el icono. Pedirá **cámara**, **micrófono** y
   **notificaciones**: aceptar las tres. Si se negó alguna por error: Ajustes de
   Android → Aplicaciones → Chrome → Permisos; o en Chrome, candado → Permisos.
4. **Ahorro de batería**: Ajustes → Aplicaciones → Chrome → Batería →
   «Sin restricciones», para que las notificaciones lleguen con la pantalla
   apagada.
5. Volumen de notificaciones activo (la orden nueva suena y vibra).
6. La primera vez, la app pide un **código de alta de 6 cifras** (vale 10
   minutos) que genera el administrador de la aplicación (Jordi); después ya no
   vuelve a pedir nada en esa tablet durante un año. Si se pierde o se cambia
   la tablet, se le revoca el alta y se da de alta la nueva.
7. Recomendable: un soporte y el cargador fijos en el puesto de Bachir, y
   bloqueo de pantalla sencillo (la app no guarda contraseñas, pero la tablet
   abre la app sin preguntar).

Los PCs de despacho no necesitan nada: entran por `https://modelos.puntotres.com/app/`
con usuario y contraseña.

## 8. Variables de entorno nuevas

En `/etc/packinglist.env` (permisos `600`, como la clave de Claude que ya está):

```
MODELOS_URL_PUBLICA=https://modelos.puntotres.com
MODELOS_ADMIN_PASSWORD=<la da Jordi; solo se usa en el primer arranque sin usuarios>
MODELOS_VAPID_PUBLIC=<se generan una vez; las da Jordi>
MODELOS_VAPID_PRIVATE=<…>
AZURE_SPEECH_KEY=<clave del recurso "Speech" creado en el tenant de Puntotres>
AZURE_SPEECH_REGION=westeurope
```

Opcional: `MODELOS_VAPID_ASUNTO` (por defecto `mailto:modelos@puntotres.com`),
el contacto que los servidores de push ven en cada aviso. `MODELOS_URL_PUBLICA`
sí es imprescindible: sin ella los QR de las etiquetas se imprimirían con
`http://localhost:8080` y no llevarían a ningún sitio. Las claves VAPID se
generan **una sola vez** (cambiarlas deja sin avisos a todas las tablets y PCs
hasta que vuelvan a activarlos); Jordi las saca con la herramienta del
proyecto y os las pasa (ver el README).

Lo de Azure: un recurso **«Speech service»** (Azure AI Speech) en la suscripción
de la empresa, región Oeste de Europa, nivel estándar (pago por minuto, muy
poco a este volumen). Lo puede crear el administrador de Microsoft; nos hace
falta la **clave** y la **región**. Sin ellas la aplicación funciona igual, pero
las notas de voz llegan sin texto.

Tras cambiar el fichero: `systemctl restart packinglist`.

## 9. Copias de seguridad

Igual que hoy, lo único que hay que salvar es `/opt/packinglist/datos`. Con la
nueva parte **crece**: audios (~200 KB por nota) y fotos (~300 KB cada una) van
dentro de la base de datos. Cientos de MB al año, no más; la copia diaria debe
seguir incluyéndola, y que alguna vez se haya **probado a restaurar**.

## 10. Comprobación final

1. Desde un PC y desde la tablet: `https://modelos.puntotres.com/app/` con candado.
2. `https://modelos.puntotres.com/menu` enseña la portada de siempre.
3. `http://modelos.puntotres.com/` redirige a `https`.
4. Desde la tablet, la app pide cámara, micrófono y notificaciones (si no los
   pide, es que no está en HTTPS o el certificado no es válido).
5. Con la tablet con la pantalla apagada, una orden de prueba desde despacho
   suena y vibra.

## 11. Para más adelante: salida a internet

**No hacerlo ahora.** Cuando toque, el plan ya está escrito en
`TO DO/2026-09-19-login-microsoft-y-actividad-design.md` y pide, en resumen:
publicación por túnel de salida (Cloudflare Tunnel o Entra Application Proxy)
o, si hay que abrir el 443, la VM en una VLAN aislada sin acceso al resto de la
LAN; login con las cuentas de Microsoft de la empresa (registro de la aplicación
en Entra ID, redirecciones a `https://modelos.puntotres.com/login/oauth2/code/azure`);
PostgreSQL en vez de la base embebida; copias fuera de la máquina; DNS público
del nombre. Lo que se monta hoy (nombre, certificado, nginx) **se reutiliza tal
cual**.

## 12. Para desarrollar sin HTTPS (lo hace Jordi, no informática)

Mientras no estén el nombre, el certificado y el nginx, la aplicación se puede
probar desde la tablet contra el PC de desarrollo en la misma WiFi, sin HTTPS:

1. En el PC: `mvn spring-boot:run` (con `MODELOS_ADMIN_PASSWORD` la primera vez)
   y el cortafuegos de Windows dejando entrar el 8080 desde la red local.
2. En el Chrome **de la tablet**: `chrome://flags/#unsafely-treat-insecure-origin-as-secure`,
   activarlo, escribir `http://<ip-del-pc>:8080` y reiniciar Chrome. Con eso
   Chrome trata esa dirección como segura y da cámara, micrófono, notificaciones
   e instalación.
3. Abrir `http://<ip-del-pc>:8080/app/`.

Es solo para probar: vale para ese navegador y esa dirección, y hay que quitarlo
cuando la tablet pase a `https://modelos.puntotres.com`. En el propio PC no hace
falta nada, porque `localhost` ya cuenta como seguro.
