# Instalación en el servidor (Linux)

Cómo dejar la aplicación corriendo como servicio en la máquina virtual Linux
del servidor de Puntotres, para que se use desde toda la red local.

- Servidor: **`192.168.0.15`** (Debian/Ubuntu). La IP tiene que ser **fija**
  (reserva en el DHCP o IP estática): va dentro de los QR de las etiquetas de
  patrones y es la dirección que usan los PCs y la tablet.
- Carpeta de la aplicación: **`/opt/packinglist`** (jar + base de datos en `datos/`).
- Variables de entorno: **`/etc/packinglist.env`**.
- Servicio: **`packinglist`** (`/etc/systemd/system/packinglist.service`).
- La instalación arranca con la **base de datos vacía** (no se copia la carpeta
  `datos` del PC de desarrollo).

Lo de HTTPS (nombre `modelos.puntotres.com`, certificado y nginx) es de
informática y está en
[la guía para el informático](docs/Modelos/modelos.puntotres.com%20-%20guia%20para%20el%20informatico.md);
se monta encima de esto sin cambiar nada de lo de aquí.

---

## 1. Construir el jar (en el PC de desarrollo)

```bash
mvn clean package
```

Sale `target/packing-list-ami-1.0.0-SNAPSHOT.jar`. Lleva todo dentro: la web,
la PWA de `/app/`, las plantillas, el `application.yml` y las migraciones de la
base de datos.

> ⚠️ **No usar `-Dskip.frontend=true` para el jar que va al servidor**: se
> saltaría el build de la PWA y el jar iría sin la app de modelos (`/app/`).
> `-DskipTests` sí se puede usar si la suite ya se ha pasado.

## 2. Preparar la máquina virtual (una sola vez)

```bash
sudo apt update && sudo apt install -y openjdk-17-jre-headless
sudo timedatectl set-timezone Europe/Madrid      # los recordatorios usan la hora local del servidor
sudo useradd --system --home /opt/packinglist --shell /usr/sbin/nologin packinglist
sudo mkdir -p /opt/packinglist
sudo chown -R packinglist:packinglist /opt/packinglist
```

- Basta el JRE: en el servidor no hace falta ni Maven ni el código fuente.
- La máquina debería tener **al menos 4 GB de RAM**: en Linux las fotos HEIC
  del corte se convierten con el conversor Java, que necesita `-Xmx3g` (el
  conversor rápido de PowerShell solo existe en Windows).
- La carpeta tiene que ser del usuario `packinglist`: la aplicación crea ahí
  `datos/` en el primer arranque y, sin permiso, el servicio falla al abrir la base.

## 3. Copiar el jar al servidor

Desde el PC con Windows, en PowerShell (`scp` viene de serie en Windows 10/11).
Hace falta un usuario con acceso SSH al servidor (lo da informática; aquí, `usuario`):

```powershell
cd C:\ruta\donde\esta\el\jar
scp .\packing-list-ami-1.0.0-SNAPSHOT.jar usuario@192.168.0.15:/tmp/
ssh usuario@192.168.0.15
```

Ya en el servidor:

```bash
sudo mv /tmp/packing-list-ami-1.0.0-SNAPSHOT.jar /opt/packinglist/packinglist.jar
sudo chown -R packinglist:packinglist /opt/packinglist
```

Se pasa por `/tmp` porque el usuario de SSH no puede escribir en `/opt/packinglist`.
El jar se renombra a `packinglist.jar` para que el servicio no cambie aunque cambie
la versión del `pom.xml`. Alternativa con ventanas: **WinSCP** por SFTP, soltando
el jar en `/tmp`.

**No hace falta copiar el `application.yml`**: va dentro del jar (ver el
apartado 9 para cuándo se pone uno al lado).

## 4. Variables de entorno

Fichero **`/etc/packinglist.env`**:

```
# Claude: lectura de hojas manuscritas, conversión de excels de taller y títulos de las notas de voz
ANTHROPIC_API_KEY=sk-ant-api03-...

# Dirección con la que se llega a la app (va dentro de los QR)
MODELOS_URL_PUBLICA=http://192.168.0.15:8080

# Contraseña del usuario admin (solo se usa en el primer arranque, con la base vacía)
MODELOS_ADMIN_PASSWORD=...

# Avisos push a la tablet y a los PCs (apartado 5)
MODELOS_VAPID_PUBLIC=...
MODELOS_VAPID_PRIVATE=...

# Notas de voz a texto (Azure)
AZURE_SPEECH_KEY=...
AZURE_SPEECH_REGION=westeurope
```

Formato: `NOMBRE=valor`, sin espacios alrededor del `=` y sin comillas. Protegerlo
para que solo lo lea root (systemd lo lee como root al arrancar y le pasa las
variables a la aplicación):

```bash
sudo chown root:root /etc/packinglist.env
sudo chmod 600 /etc/packinglist.env
```

Para empezar bastan las tres primeras (`ANTHROPIC_API_KEY`, `MODELOS_URL_PUBLICA`
y `MODELOS_ADMIN_PASSWORD`); las demás se añaden cuando se tengan, con un reinicio
del servicio (`sudo systemctl restart packinglist`), sin tocar el jar.

### Qué hace cada una

| Variable | Para qué | Si falta |
|---|---|---|
| `ANTHROPIC_API_KEY` | Clave de la API de Claude (console.anthropic.com, empieza por `sk-ant-api03-`). La usan la lectura de fotos/PDF de las hojas manuscritas, el botón «Generar Formato con IA» de los excels de taller y el título automático de las notas de voz. | La aplicación arranca y funciona; solo se apagan esas tres cosas. |
| `MODELOS_URL_PUBLICA` | La dirección que se graba en el **QR de las etiquetas de patrones**. **No** decide si la app se ve en la red (eso es el cortafuegos, apartado 7). Ahora `http://192.168.0.15:8080` (con `http://` y con el puerto, sin barra final); con nginx, `https://modelos.puntotres.com`. | Vale `http://localhost:8080` y los QR impresos no llevan a ningún sitio. **Obligatoria en el servidor.** |
| `MODELOS_ADMIN_PASSWORD` | Contraseña del usuario `admin` de `/app/`. Solo se usa si la base **no tiene ningún usuario**, es decir, en el primer arranque. Después se puede quitar del fichero: la contraseña se cambia en la pestaña «Usuarios» de `/app/`. | Con la base vacía no se crea ningún usuario y nadie puede entrar en `/app/` (el resto del programa sí funciona). Se arregla poniéndola y reiniciando. |
| `MODELOS_VAPID_PUBLIC` / `MODELOS_VAPID_PRIVATE` | El par de claves de las **notificaciones push**: la tablet suena cuando el despacho manda una orden, y el despacho se entera de los avisos del taller. Ver el apartado 5. | No hay push: hay que mirar la bandeja a mano. |
| `MODELOS_VAPID_ASUNTO` *(opcional)* | Correo de contacto que acompaña a cada push (lo ven los servidores de Google y Mozilla). | Vale `mailto:modelos@puntotres.com`. |
| `AZURE_SPEECH_KEY` | Clave del «Speech service» de Azure (lo crea el administrador de Microsoft de la empresa). Pasa a texto las **notas de voz** del taller. | Las notas se oyen pero salen «sin transcripción» (y sin título automático). |
| `AZURE_SPEECH_REGION` *(opcional)* | Región donde se creó ese servicio. | Vale `westeurope`. |

### Sobre las claves y su seguridad

- La clave **no está "hardcoded"** por estar en este fichero: hardcoded es escrita
  en el código o en un fichero del repositorio. Aquí vive fuera del programa y el
  jar la lee del entorno al arrancar, que es lo correcto. (`setx` es el equivalente
  en Windows y no es más seguro: guarda la clave en texto plano en el registro.)
- **Nunca** en `application.yml`, en el repositorio ni en el `ExecStart` del
  servicio (se vería con `ps` y en los logs).
- No pegarlas enteras en chats, correos ni capturas. Si se escapa una, se revoca
  y se crea otra (en console.anthropic.com → API Keys, para la de Claude).
- Recomendado: una **clave de Anthropic propia para el servidor** («servidor-puntotres»),
  distinta de la del PC de desarrollo, para poder revocar una sin romper la otra y
  ver el gasto de cada una.

## 5. Claves VAPID (push)

Se generan **una sola vez por instalación**, en cualquier PC con Maven y el
proyecto (no en el servidor: la herramienta está en los tests y no va dentro del
jar). En la carpeta del proyecto:

PowerShell (las comillas hacen falta):

```powershell
mvn -q test-compile exec:java "-Dskip.frontend=true" "-Dexec.classpathScope=test" "-Dexec.mainClass=com.puntotres.packinglist.modelos.GenerarClavesVapid"
```

Git Bash o cmd:

```bash
mvn -q test-compile exec:java -Dskip.frontend=true -Dexec.classpathScope=test -Dexec.mainClass=com.puntotres.packinglist.modelos.GenerarClavesVapid
```

Escribe dos líneas (`MODELOS_VAPID_PUBLIC=…` y `MODELOS_VAPID_PRIVATE=…`) que se
pegan tal cual en `/etc/packinglist.env`, y se reinicia el servicio.

- Las dos tienen que ser **las que salieron juntas** del mismo comando.
- **Guardarlas también en un gestor de contraseñas.** Si se pierden o se
  regeneran, todos los aparatos dejan de recibir avisos hasta que los vuelvan a
  activar uno a uno. No volver a ejecutar el comando "para probar".
- La privada es secreta; la pública no (el navegador la recibe igualmente).
- Al pegarlas, que no se cuele ningún espacio ni salto de línea: con una clave
  cortada la aplicación arranca igual, deja el push apagado y lo dice con un
  `WARN` en el log.
- **El push solo llega por HTTPS**: hasta que esté `modelos.puntotres.com` no
  funcionará en la tablet, pero se pueden poner ya y funcionarán sin tocar nada más.

## 6. El servicio

Fichero **`/etc/systemd/system/packinglist.service`**:

```ini
[Unit]
Description=Packing List Puntotres
After=network-online.target
Wants=network-online.target

[Service]
User=packinglist
Group=packinglist
WorkingDirectory=/opt/packinglist
EnvironmentFile=/etc/packinglist.env
ExecStart=/usr/bin/java -Xmx3g -jar /opt/packinglist/packinglist.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

> ⚠️ **`WorkingDirectory` es imprescindible.** La base de datos está configurada
> como `./datos/packinglist`, relativa a la carpeta desde la que se arranca. Sin
> esa línea la aplicación crearía una base nueva y vacía en otro sitio, sin avisar.

`SuccessExitStatus=143` es para que una parada normal (`systemctl stop`) no salga
como fallo.

Activarlo y arrancarlo:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now packinglist
sudo journalctl -u packinglist -f        # el log en directo; Ctrl+C para salir
```

Cuando el log diga `Started PackingListApplication`, la base ya está creada:

```bash
ls -l /opt/packinglist/datos             # debe aparecer packinglist.mv.db
```

Órdenes útiles:

```bash
sudo systemctl status packinglist        # ¿está corriendo?
sudo systemctl restart packinglist       # tras cambiar /etc/packinglist.env
sudo systemctl stop packinglist
sudo journalctl -u packinglist -n 200    # las últimas 200 líneas del log
```

### Qué trae la base vacía

- **Ya relleno**: la tabla de **taras** (los cartones del `application.yml`, con
  los pesos del yml, no los que se hubieran corregido en `/taras` en otro PC) y
  los **nueve códigos de color de APC** de la tabla de colores. El usuario
  **`admin`**, con la contraseña de `MODELOS_ADMIN_PASSWORD`.
- **Vacío**: temporadas y sus excels, catálogo de modelos y fotos, nombres de
  pieles, descripciones de color, memoria de cartones y unidades por caja, hojas
  de embalaje y los demás usuarios.

Si en el PC de desarrollo se habían pesado cartones en `/taras`, hay que volver a
teclear esos pesos en el servidor.

## 7. Que se vea en la red local

La aplicación ya escucha en todas las interfaces de la máquina (la línea
`address: 127.0.0.1` del yml está comentada y no se activa hasta que haya nginx).
Lo que falta es que el cortafuegos deje pasar el **8080**:

```bash
sudo ufw status                 # si dice "inactive", no hay nada que abrir
sudo ufw allow 8080/tcp         # si está activo
```

Si hay además un cortafuegos de red entre los PCs y el servidor, lo abre informática.

Comprobación, en este orden:

1. En el propio servidor: `curl -I http://localhost:8080/menu` → `200`.
2. Desde un PC: `http://192.168.0.15:8080/menu`.
3. Desde la tablet: `http://192.168.0.15:8080/app/`, y entrar con `admin`.

Si el 1 funciona y el 2 no, es el cortafuegos.

### Mientras no haya HTTPS

Por `http://192.168.0.15:8080` los PCs pueden usarlo todo, pero la tablet **no
tiene cámara, micrófono ni notificaciones** (el navegador solo los da en HTTPS o
en `localhost`). Para probar mientras tanto, en el Chrome **de la tablet**:
`chrome://flags/#unsafely-treat-insecure-origin-as-secure`, activarlo, escribir
`http://192.168.0.15:8080` y reiniciar Chrome. Hay que quitarlo cuando la tablet
pase a `https://modelos.puntotres.com`.

> ⚠️ **No imprimir etiquetas de patrones con la dirección provisional.** El QR
> lleva dentro `MODELOS_URL_PUBLICA` y una etiqueta pegada no se puede
> actualizar. Cuando `modelos.puntotres.com` funcione: cambiar la variable,
> reiniciar el servicio, y entonces imprimir.

## 8. Actualizar a una versión nueva

Basta con cambiar el jar, pero **con la aplicación parada** y con **copia de
`datos` antes**:

```bash
# (antes: scp del jar nuevo a /tmp, como en el apartado 3)
sudo systemctl stop packinglist
sudo cp -r /opt/packinglist/datos /opt/packinglist/datos-copia-$(date +%F)
sudo cp /tmp/packing-list-ami-1.0.0-SNAPSHOT.jar /opt/packinglist/packinglist.jar
sudo chown packinglist:packinglist /opt/packinglist/packinglist.jar
sudo systemctl start packinglist
sudo journalctl -u packinglist -f        # comprobar que arranca sin errores
```

- **Por qué la copia**: si la versión nueva trae un cambio de esquema (una
  migración nueva de Flyway), se aplica sola al arrancar, y si fallara a medias
  H2 no la deshace. Para volver atrás: parar, poner el jar viejo y la carpeta
  copiada, arrancar.
- **No se toca** al actualizar: `/etc/packinglist.env`, el `application.yml` de
  al lado del jar (si lo hay), el fichero del servicio ni `datos`.
- Actualizar cuando no haya nadie trabajando: durante el reinicio la aplicación
  no contesta, y un envío o un corte a medias en una pestaña se pierde (vive en
  la sesión, no en la base).
- La tablet no necesita nada: la PWA coge la versión nueva sola al abrirse
  (como mucho, tras recargar una vez).

## 9. Cambiar ajustes sin recompilar

Spring lee un `application.yml` que esté **al lado del jar**
(`/opt/packinglist/application.yml`) y lo fusiona propiedad a propiedad con el de
dentro, ganando el de fuera. Solo lleva **las líneas que cambian**, nunca una
copia del yml entero (en la siguiente actualización pisaría en silencio lo que
trajera el jar nuevo). Detalle y excepción de las listas en el
[README](README.md#4-cambiar-taras-o-clientes-sin-recompilar).

El caso previsto es nginx: cuando informática lo tenga delante, se crea con

```yaml
server:
  forward-headers-strategy: framework
  address: 127.0.0.1
```

y, a la vez, `MODELOS_URL_PUBLICA=https://modelos.puntotres.com` (sin puerto) en
`/etc/packinglist.env`. Después, `sudo systemctl restart packinglist`. Con
`address: 127.0.0.1` el 8080 deja de verse desde la red: a partir de ahí se entra
solo por `https://modelos.puntotres.com`.

## 10. Copias de seguridad

Lo único que hay que salvar es **`/opt/packinglist/datos`**: las temporadas, el
catálogo, los usuarios y, con la app de modelos, los audios y las fotos (cientos
de MB al año). Copiar los ficheros de H2 con la aplicación en marcha puede dar
una copia incoherente, así que o bien se para el servicio un minuto de madrugada
desde un cron (`stop`, copiar, `start`), o se usa la copia en caliente de H2
(`BACKUP TO`). Y alguna vez, **probar a restaurar**.

## 11. Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| El servicio no arranca y el log habla de permisos al abrir la base | `/opt/packinglist` no es del usuario `packinglist`: `sudo chown -R packinglist:packinglist /opt/packinglist`. |
| `Validate failed: Migrations have failed validation` | La carpeta `datos` es de una versión anterior incompatible. Apartarla (`mv datos datos-vieja`) y arrancar con base vacía. |
| Funciona desde el servidor (`curl`) pero no desde los PCs | Cortafuegos: apartado 7. |
| `/app/` está en blanco o da 404 | El jar se construyó con `-Dskip.frontend=true`. Volver a construirlo sin esa opción. |
| Nadie puede entrar en `/app/` | Base vacía sin `MODELOS_ADMIN_PASSWORD`: ponerla y reiniciar. |
| La tablet no pide cámara ni micrófono | Falta HTTPS: apartado 7 («Mientras no haya HTTPS»). |
| `WARN` sobre las claves VAPID en el log | Una clave pegada cortada o con espacios: volver a pegarlas. |
| El QR de una etiqueta abre `localhost` | Falta `MODELOS_URL_PUBLICA` (o tenía otro valor al imprimir). |
