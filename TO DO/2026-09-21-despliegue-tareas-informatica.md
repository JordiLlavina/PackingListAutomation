# app.puntotres.com — despliegue de la aplicación de packing lists

Documento para el equipo de informática. Cubre dos bloques de trabajo independientes:

- **Bloque A — Sistemas y red**: la máquina virtual, el servicio, el nombre, el certificado y las
  copias de seguridad.
- **Bloque B — Microsoft 365 / Entra ID**: el registro de la aplicación para que la gente entre con su
  cuenta corporativa.

Al final está lo que necesitamos que nos devolváis y las comprobaciones de aceptación.

---

## Qué es la aplicación

Una aplicación web interna que genera la documentación de los envíos a clientes (packing lists,
etiquetas de caja y de artículo, hojas de trabajo y volcados para el ERP). Hoy se ejecuta en un PC del
almacén y queremos que pase a ser un servicio en una máquina virtual, accesible desde la red de la
oficina.

- La usan **entre 3 y 8 personas**, no simultáneamente en su mayoría.
- **No se publica en internet**: solo red local. El nombre `app.puntotres.com` resolverá a una IP
  privada.
- Es un único fichero `.jar` de Java (Spring Boot, con servidor web incorporado). No necesita instalar
  base de datos, ni servidor de aplicaciones, ni nada más aparte de Java.
- La entrega el desarrollador; el nombre del fichero es `packing-list-ami-1.0.0-SNAPSHOT.jar` y se
  instala como `/opt/packing/packing.jar`.

Esquema del despliegue:

```
PC de la oficina  --https 443-->  [ proxy inverso con TLS ]  --http-->  127.0.0.1:8080  ( el jar )
                                                                             |
                                                                   /opt/packing/datos  (todo el estado)
```

---

## Ficha técnica: lo que la aplicación necesita

| Qué | Requisito |
|---|---|
| Java | **17 o superior**, JRE headless (`openjdk-17-jre-headless`) |
| Sistema | Debian 12 / Ubuntu Server LTS (cualquier distribución con systemd sirve) |
| Recursos | 2 vCPU, **4 GB de RAM**, 20–40 GB de disco |
| Puerto de la aplicación | 8080, escuchando **solo en `127.0.0.1`** |
| Puerto de acceso | 443 (TLS en el proxy inverso) |
| Estado en disco | **una sola carpeta: `/opt/packing/datos`** |
| Paquetes extra | `fontconfig` y un paquete de fuentes (`fonts-liberation`) |
| Salida a internet | `login.microsoftonline.com:443`, `api.anthropic.com:443`, ACME/Let's Encrypt y la API del proveedor DNS, repositorios del sistema |
| Hora | NTP activo y sincronizado; zona horaria `Europe/Madrid` |

### Cinco cosas que condicionan la configuración

1. **Una sola instancia, y la sesión vive en memoria.** No se puede balancear entre dos copias ni
   replicar. La sesión de cada persona guarda **el envío que está preparando** (los ficheros subidos y
   los documentos generados), así que un reinicio del servicio **le tira el trabajo de la mañana a quien
   esté a medias**. Consecuencia práctica: los reinicios, actualizaciones del jar y reinicios de la VM,
   **fuera del horario del almacén**. La sesión está configurada para durar 8 horas, una jornada.

2. **Hay operaciones que tardan unos 4–5 minutos.** Una de las formas de entrada analiza fotos y PDFs de
   hojas manuscritas y es lenta por naturaleza. Los tiempos de espera del proxy tienen que estar en
   **15 minutos**; con los valores por defecto (30–60 s) el proxy corta la petición y el error parece un
   fallo de la aplicación.

3. **Subidas grandes**: hasta 20 MB por fichero y 60 MB por petición (fotos de móvil, varios PDFs). El
   límite de cuerpo de petición del proxy tiene que admitir **60 MB**.

4. **La aplicación dibuja imágenes (códigos de barras y etiquetas) usando las fuentes del sistema.** En
   una instalación mínima de servidor no hay ninguna fuente ni `fontconfig`, y eso hace que las
   etiquetas salgan mal o que la generación falle. Por eso `fontconfig` y `fonts-liberation` están en la
   lista de paquetes: no son un adorno.

5. **Todo el estado está en `/opt/packing/datos`.** Dentro hay una base de datos embebida en fichero con
   datos que representan **meses de trabajo manual** y que no se pueden reconstruir desde ningún otro
   sistema (no vienen del ERP). Copiar esa carpeta es copiar la aplicación entera; perderla es perderlo
   todo. La aplicación la busca **relativa al directorio de trabajo del proceso**, así que el
   `WorkingDirectory` de la unidad de systemd tiene que ser `/opt/packing`: si el servicio arranca desde
   otro directorio, **crea una base de datos vacía sin dar ningún error**.

---

# Bloque A — Tareas de sistemas y red

### A.1 — La máquina virtual

- [ ] VM con Debian 12 o Ubuntu Server LTS: 2 vCPU, 4 GB de RAM, 20–40 GB de disco.
- [ ] Zona horaria `Europe/Madrid` y NTP sincronizado. **La hora importa**: la validación de los tokens
      de Microsoft falla si el reloj se desvía, y el síntoma es un login que no entra sin decir por qué.
- [ ] Política de snapshots de la VM según lo habitual, **además** de la copia de la carpeta de datos
      (A.6). Un snapshot protege de perder la máquina; la copia de datos protege de un borrado o una
      corrupción dentro de ella.
- [ ] Paquetes:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jre-headless fontconfig fonts-liberation
java -version    # esperado: 17 o superior
```

### A.2 — Salida a internet desde la VM

Si la salida está filtrada, hay que permitir por HTTPS (443):

- [ ] `login.microsoftonline.com` — imprescindible. Se consulta **al arrancar** y en cada login; sin
      ella la aplicación **no arranca**.
- [ ] `api.anthropic.com` — el análisis de fotos y PDFs. Sin ella la aplicación arranca igual y solo deja
      de funcionar esa forma de entrada.
- [ ] Let's Encrypt (ACME) y la API del proveedor de DNS — para el certificado (A.4).
- [ ] Repositorios del sistema, para actualizaciones.

### A.3 — El nombre y el DNS

- [ ] Registro `A` de **`app.puntotres.com`** apuntando a la **IP privada** de la VM. Que un nombre
      público resuelva a una IP privada es intencionado: desde fuera de la oficina esa IP no lleva a
      ningún sitio.
- [ ] Un token de API del proveedor de DNS con permiso de edición sobre la zona `puntotres.com`, para
      que el certificado se pueda emitir por el reto **DNS-01** (no requiere que la VM sea alcanzable
      desde internet; solo poder crear un registro `TXT`).
- [ ] Comprobación: `nslookup app.puntotres.com` desde un PC de la oficina devuelve la IP privada.

**Si el reto DNS-01 no es viable** con nuestro proveedor, la alternativa es un certificado de una CA
interna (por ejemplo `tls internal` de Caddy), con su coste: hay que **distribuir la raíz a todos los
PCs** (por GPO si hay dominio) o cada navegador mostrará un aviso rojo. Decidlo antes de empezar, porque
cambia el trabajo de A.4.

### A.4 — El proxy inverso con TLS

Propuesta con Caddy (nginx sirve igual si lo preferís, manteniendo los valores marcados). Caddy de los
repositorios no trae los módulos de DNS: hay que coger el binario con el módulo del proveedor desde
`caddyserver.com/download` o compilarlo con `xcaddy`.

`/etc/caddy/Caddyfile`:

```
app.puntotres.com {
        tls {
                dns <proveedor> {env.DNS_API_TOKEN}
        }
        encode gzip
        reverse_proxy 127.0.0.1:8080 {
                transport http {
                        response_header_timeout 15m
                        read_timeout 15m
                }
        }
        request_body {
                max_size 60MB
        }
}
```

- [ ] Los **15 minutos de espera** y el **60 MB de cuerpo** no son negociables: ver los puntos 2 y 3 de
      la ficha técnica.
- [ ] El proxy tiene que mandar las cabeceras **`X-Forwarded-Proto` y `X-Forwarded-Host`** (Caddy lo hace
      por defecto; en nginx hay que ponerlas). **Si faltan, el login no funciona**: la aplicación
      construye la dirección de vuelta a Microsoft con lo que ve y mandaría `http://127.0.0.1:8080/...`,
      que Microsoft rechaza con el error `AADSTS50011`.
- [ ] El token de DNS, fuera del Caddyfile, en `/etc/systemd/system/caddy.service.d/dns.conf` con
      permisos `600`:

```
[Service]
Environment=DNS_API_TOKEN=<el token de A.3>
```

- [ ] Comprobación: `curl -sI https://app.puntotres.com/` **sin** `--insecure` responde sin error de
      certificado. La respuesta será un **`302` hacia `login.microsoftonline.com`** — eso es lo correcto:
      la aplicación no tiene ninguna página accesible sin identificarse.

### A.5 — El servicio

- [ ] Usuario de servicio y directorios:

```bash
sudo useradd --system --home /opt/packing --shell /usr/sbin/nologin packing
sudo mkdir -p /opt/packing/datos
sudo chown -R packing:packing /opt/packing
```

- [ ] El jar que entrega el desarrollador, como `/opt/packing/packing.jar`, propietario `packing`.
- [ ] Fichero de secretos `/etc/packing/entorno`, propietario `root:root` y permisos **`600`**:

```
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
SERVER_FORWARD_HEADERS_STRATEGY=framework
ENTRA_TENANT_ID=<del bloque B>
ENTRA_CLIENT_ID=<del bloque B>
ENTRA_CLIENT_SECRET=<del bloque B>
ANTHROPIC_API_KEY=<la entrega el desarrollador>
```

`SERVER_ADDRESS=127.0.0.1` es lo que impide llegar a la aplicación **saltándose el proxy**: sin ella el
8080 queda abierto a toda la red y se puede rodear el login entrando por ahí.
`SERVER_FORWARD_HEADERS_STRATEGY=framework` es la otra mitad del punto de las cabeceras de A.4.

- [ ] Unidad `/etc/systemd/system/packing.service`:

```
[Unit]
Description=Generador de packing lists de Puntotres
After=network.target

[Service]
Type=simple
User=packing
WorkingDirectory=/opt/packing
EnvironmentFile=/etc/packing/entorno
ExecStart=/usr/bin/java -Xmx1g -jar /opt/packing/packing.jar
Restart=on-failure
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

`WorkingDirectory=/opt/packing` es el punto 5 de la ficha técnica: **no se puede omitir ni cambiar**.
`-Xmx1g` es un punto de partida; si con el uso real se queda corto, se sube y se reinicia fuera de
horario.

- [ ] Arrancar y comprobar:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now packing
sudo systemctl status packing
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/    # esperado: 302
```

- [ ] **Comprobar que NO se llega desde fuera**: desde otro PC,
      `curl -m 5 http://<ip-de-la-vm>:8080/` tiene que **fallar la conexión**. Si responde, falta
      `SERVER_ADDRESS` y hay que arreglarlo antes de seguir.
- [ ] Cortafuegos: 443 abierto a la red de la oficina; 8080 no accesible desde la red.

### A.6 — Copia de seguridad de la carpeta de datos

Es el punto 5 de la ficha técnica: ahí está todo y no se puede reconstruir desde otro sistema.

- [ ] Copia **diaria** de `/opt/packing/datos`, con retención de al menos 30 días, y **fuera de la propia
      VM** (una copia dentro de la misma máquina no protege de perder la máquina). Si se integra en el
      sistema de copias de la empresa, mejor que un script local.
- [ ] Si se hace con un temporizador local, algo así:

```bash
#!/bin/bash
set -euo pipefail
destino=/var/backups/packing
mkdir -p "$destino"
tar czf "$destino/datos-$(date +%Y-%m-%d).tar.gz" -C /opt/packing datos
find "$destino" -name 'datos-*.tar.gz' -mtime +30 -delete
```

con un `packing-copia.timer` de systemd a las 03:00 (`OnCalendar=*-*-* 03:00:00`, `Persistent=true`).

- [ ] **Probar una restauración**, aunque sea a `/tmp`. Una copia que no se ha restaurado nunca no es una
      copia.

### A.7 — Restaurar los datos que trae el desarrollador

El día del despliegue, el desarrollador entregará una carpeta `datos` copiada del PC del almacén (con la
aplicación de allí **parada**, para que el fichero no salga a medias).

- [ ] Con el servicio `packing` **parado**:

```bash
sudo systemctl stop packing
sudo cp /tmp/datos/* /opt/packing/datos/
sudo chown -R packing:packing /opt/packing/datos
sudo systemctl start packing
```

- [ ] Revisar el arranque con `journalctl -u packing -n 100 --no-pager`: la aplicación aplica sus
      migraciones de esquema sola. Si no arranca, **no hay que tocar nada**: avisad al desarrollador, que
      ya habrá ensayado esta misma operación con una copia.

---

# Bloque B — Microsoft 365 / Entra ID

Objetivo: que la gente entre en la aplicación con su cuenta de Puntotres. La aplicación usa OpenID
Connect estándar; **no guarda contraseñas** y **no lee el directorio** (no necesita Microsoft Graph más
allá de los tres scopes básicos de identidad).

### B.1 — Registro de la aplicación

- [ ] *App registrations* → *New registration*:
      - Nombre: `Packing List Puntotres`
      - Tipos de cuenta: **Accounts in this organizational directory only (Single tenant)**
      - Redirect URI: plataforma **Web** (no SPA):
        **`https://app.puntotres.com/login/oauth2/code/azure`**
- [ ] Añadir una **segunda** Redirect URI, también plataforma Web, para el entorno de desarrollo del
      programador: **`http://localhost:8080/login/oauth2/code/azure`**. Entra admite `http` solo para
      `localhost`, y sin ella no se puede probar nada antes de desplegar.
- [ ] *API permissions*: dejar **solo** `openid`, `profile` y `email` (Microsoft Graph, delegados). Si
      aparece `User.Read`, quitarlo.
- [ ] No hacen falta app roles, ni certificados, ni exponer ninguna API.

### B.2 — El secreto de cliente

- [ ] *Certificates & secrets* → *New client secret*, **24 meses**. Copiar el **Value** en ese momento
      (el portal no lo vuelve a mostrar).
- [ ] Entregarlo por un canal seguro (gestor de contraseñas, no correo ni Teams).
- [ ] **Anotar la caducidad en el calendario compartido**, con quién lo custodia. Cuando caduque, nadie
      podrá entrar en la aplicación y **no habrá ningún aviso previo**: el síntoma es un lunes en que el
      almacén no puede trabajar.

### B.3 — Quién puede entrar

La aplicación da de alta sola a quien entra por primera vez, **sin ningún permiso**: ve un menú vacío
hasta que un administrador le concede lo que necesita. Aun así, si preferís cerrar la puerta antes:

- [ ] Opcional: en *Enterprise applications* → la aplicación → *Properties*, poner **Assignment
      required = Yes** y asignar un grupo (por ejemplo, almacén + oficina). Así solo ese grupo puede
      autenticarse.
- [ ] Confirmar que **ninguna política de acceso condicional** bloquea esta aplicación desde los PCs del
      almacén (dispositivo conforme, MFA con dispositivo no registrado, etc.). Si hay MFA, decidnos si
      aplica: es compatible, pero afecta a la experiencia de cada entrada.

### B.4 — Un detalle sobre las identidades

La aplicación identifica a cada persona por su **identificador de objeto de Entra** (`oid`), que no
cambia nunca, y muestra su correo solo para que se la reconozca en pantalla. Si un usuario no tiene
atributo de correo, usa su nombre de usuario principal (UPN).

- [ ] Confirmadnos, para **`jllavinapamias@puntotres.com`**, cuál de los dos valores manda Entra en el
      token (correo o UPN, si difieren). Ese usuario es el primer administrador de la aplicación y la
      comparación se hace por esa cadena exacta; si no coincide, se queda sin permisos de administración
      y hay que corregirlo a mano en la base de datos.

---

## Qué necesitamos que nos devolváis

| Dato | De dónde sale |
|---|---|
| `ENTRA_TENANT_ID` | *Overview* del registro → `Directory (tenant) ID` |
| `ENTRA_CLIENT_ID` | *Overview* del registro → `Application (client) ID` |
| `ENTRA_CLIENT_SECRET` | el **Value** del secreto de B.2 (por canal seguro) |
| IP privada de la VM | para el registro DNS y para nuestras pruebas |
| Confirmación de correo/UPN | B.4 |
| Ventana de mantenimiento | cuándo se puede reiniciar el servicio sin molestar al almacén |

---

## Comprobaciones de aceptación (las hacemos juntos)

- [ ] `nslookup app.puntotres.com` desde un PC de la oficina → IP privada de la VM.
- [ ] `https://app.puntotres.com` en un navegador → **login de Microsoft**, candado cerrado y sin avisos
      de certificado.
- [ ] Tras identificarse → el menú de la aplicación.
- [ ] `curl -m 5 http://<ip-de-la-vm>:8080/` desde otro PC → **falla la conexión** (no se puede rodear el
      proxy).
- [ ] `systemctl status packing` → `active (running)`; y tras `systemctl restart packing`, vuelve a
      levantar solo.
- [ ] Reiniciar la VM entera → los dos servicios (aplicación y proxy) arrancan solos.
- [ ] Un `.tar.gz` de la copia diaria existe y se ha restaurado una vez a modo de prueba.
- [ ] El desarrollador pasa su propia lista de pruebas funcionales (generar documentos, etiquetas con
      códigos de barras, y una carga de PDFs que tarda unos 4 minutos y valida los tiempos de espera del
      proxy).

---

## Limitaciones conocidas que conviene que sepáis

- **Una sola instancia y sesiones en memoria**: no hay alta disponibilidad. Si la VM cae, la aplicación
  no está disponible hasta que vuelva; el trabajo a medias de cada usuario se pierde, el trabajo ya
  guardado no.
- **Actualizar la aplicación es sustituir el jar y reiniciar el servicio** (unos segundos). Siempre fuera
  del horario del almacén.
- **No hay exposición a internet** en esta fase. Si algún día hace falta acceso desde fuera de la
  oficina, es un proyecto aparte y cambia el planteamiento de red y de base de datos.
