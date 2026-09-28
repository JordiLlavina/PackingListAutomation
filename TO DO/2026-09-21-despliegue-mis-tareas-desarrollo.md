# Despliegue en el servidor — mis tareas (desarrollo)

La aplicación se llamará **`app.puntotres.com`** y vivirá en una máquina virtual con Linux, dentro de
la red de la oficina. Las tareas de la máquina, la red, el certificado y el registro en Microsoft son
de informática: están en
[2026-09-21-despliegue-tareas-informatica.md](2026-09-21-despliegue-tareas-informatica.md), que es el
documento que hay que enviarles.

Esto es **lo que me toca a mí**, en el orden en que hay que hacerlo. Cada tarea dice qué es, por qué
importa, qué teclear y cómo sé que ha ido bien.

**Lo primero, para quitarlo de la cabeza: el cambio de nombre no toca ni una línea de código.** La
dirección de vuelta del login se construye sola a partir del nombre por el que entra el navegador
(`{baseUrl}` en `application.yml`), así que `app.puntotres.com` funciona sin recompilar nada. El
nombre solo hay que escribirlo en dos sitios, y los dos son de informática: el registro DNS y el
registro de la aplicación en Entra.

---

## Tarea 1 — Mandar el documento a informática y pedir los tres valores de Entra

**Qué es.** La aplicación ya no tiene usuarios propios: quien entra se identifica con su cuenta de
Microsoft de Puntotres. Para que eso funcione, alguien tiene que dar de alta la aplicación en el
Entra ID de la empresa, y de ese alta salen tres datos que la aplicación necesita para arrancar. Si
faltan, el servicio **no levanta** — no es que no se pueda entrar, es que no arranca.

**Qué hacer.**

- [ ] Enviar a informática el documento de sus tareas.
- [ ] Pedirles expresamente los **tres valores** de su apartado "Qué nos tenéis que devolver":
      `ENTRA_TENANT_ID`, `ENTRA_CLIENT_ID` y `ENTRA_CLIENT_SECRET`.
- [ ] Pedirles también que registren la **segunda** dirección de vuelta, la de desarrollo:
      `http://localhost:8080/login/oauth2/code/azure`. Sin ella no puedo probar el login en mi PC
      antes de desplegar, y entonces los fallos aparecerían en el servidor, mezclados con los del
      proxy y el certificado.

**Cuidado con el secreto.** `ENTRA_CLIENT_SECRET` es una contraseña. Que no venga por correo ni por
Teams si se puede evitar: lo ideal es que quien monta el servidor lo reciba directamente del proveedor
de Microsoft y lo escriba allí, y que a mí me llegue solo para la prueba en local. Si pasa por mis
manos, va a un gestor de contraseñas, nunca a un fichero del proyecto.

**Nunca en el repositorio.** Estos tres valores no se escriben en `application.yml` (ese fichero viaja
dentro del jar) ni en git. En mi PC van como variables de entorno de la sesión; en el servidor, en un
fichero que solo puede leer root.

---

## Tarea 2 — Probar el login en mi PC antes de desplegar

**Qué es.** Comprobar que el registro de Entra está bien hecho **antes** de que haya un servidor de
por medio. Si algo está mal (una URI mal escrita, un permiso que falta), aquí se ve en un minuto.

**Qué hacer.** Abrir PowerShell en la carpeta del proyecto y teclear, con los valores de verdad:

```powershell
$env:ENTRA_TENANT_ID = "..."
$env:ENTRA_CLIENT_ID = "..."
$env:ENTRA_CLIENT_SECRET = "..."
mvn spring-boot:run
```

Luego abrir **`http://localhost:8080`** en el navegador.

**Dos detalles que hacen perder la tarde si no se saben:**

- Tiene que ser `localhost`, **no `127.0.0.1`**. La cookie de sesión está marcada como "solo por
  conexión segura"; los navegadores hacen la excepción con el nombre `localhost` pero no con la IP, así
  que por `127.0.0.1` el login da vueltas sin entrar nunca.
- Las tres variables viven **solo en esa ventana de PowerShell**. Si la cierro, hay que volver a
  ponerlas.

**Cómo sé que ha ido bien.** Sale la pantalla de Microsoft, entro con mi cuenta de Puntotres y caigo en
el menú de la aplicación con mi nombre arriba.

**Si sale `AADSTS50011`** ("redirect URI mismatch"): la URI de desarrollo no está registrada, o está
escrita distinta. Tiene que ser exactamente `http://localhost:8080/login/oauth2/code/azure`.

**Al acabar, matar el proceso a conciencia.** En Windows, cerrar `mvn spring-boot:run` no mata el `java`
hijo y el puerto 8080 se queda ocupado:

```powershell
netstat -ano | findstr :8080
taskkill /F /PID <el pid>
```

---

## Tarea 3 — Confirmar el correo del administrador inicial ANTES del primer login

**Qué es.** La aplicación no reparte permisos sola: quien entra por primera vez ve el menú vacío. El
primer administrador tiene que salir de una lista del `application.yml`, porque no hay nadie que se lo
pueda conceder:

```yaml
seguridad:
  administradores-iniciales:
    - jllavinapamias@puntotres.com
```

**Por qué esta es la tarea más delicada de la lista.** Esa lista se consulta **solo en el momento en que
la persona se da de alta**, es decir, en su primerísima entrada. Si el correo de la lista no coincide
con el que manda Entra, mi usuario se crea **sin** administrador — y a partir de ahí, corregir el
`application.yml` y volver a desplegar **no arregla nada**, porque el usuario ya existe y la lista ya no
se mira. Me quedaría con una aplicación en la que nadie puede dar permisos a nadie.

**Qué hacer.**

- [ ] En la prueba de la tarea 2, mirar qué correo aparece en la cabecera después de entrar. Ese es el
      que manda Entra.
- [ ] Comprobar que es idéntico al de la lista. Mayúsculas y espacios de sobra no importan; el resto sí.
      Normalmente será el correo corporativo, pero puede ser el "nombre de usuario principal" (UPN) si
      en Entra no coinciden.
- [ ] Si no coincide, corregir la lista **antes** de construir el jar que va al servidor.

**La salida de emergencia, si ya ha pasado.** No hay que rehacer nada: con el servicio parado se marca
el administrador a mano en la base de datos (`UPDATE usuarios SET administrador = TRUE WHERE correo =
'...'`), o se borra esa fila para que el siguiente login la vuelva a dar de alta. Pero es mejor no
tener que hacer eso en el servidor el primer día.

---

## Tarea 4 — Ensayar la base de datos del almacén con una COPIA

**Qué es.** En la base de datos del PC del almacén hay trabajo de meses: las taras pesadas cartón a
cartón, las temporadas guardadas con los excels de pedido enteros dentro, y lo que el programa ha
aprendido de cada referencia. El jar nuevo, al arrancar sobre esa base, le aplica las migraciones
(usuarios, permisos, actividad) y además comprueba que el esquema y el código cuadran: si no cuadran
**no arranca**, que es lo que queremos. Lo que no queremos es descubrirlo en el servidor con el almacén
esperando.

**Qué hacer.**

- [ ] En el PC del almacén, **cerrar la aplicación del todo** (que no quede ningún `java` vivo; ver el
      `netstat` de la tarea 2). Copiar una base de datos en caliente puede dar un fichero a medias.
- [ ] Copiar la carpeta `datos\` entera a un sitio de paso. **Es una copia: el original no se toca.**
- [ ] En mi PC, crear una carpeta de ensayo, por ejemplo `C:\ensayo`, y dejar dentro la carpeta `datos\`
      copiada y el jar recién construido (tarea 6).
- [ ] Arrancar desde **dentro** de esa carpeta:

```powershell
cd C:\ensayo
$env:ENTRA_TENANT_ID = "..."
$env:ENTRA_CLIENT_ID = "..."
$env:ENTRA_CLIENT_SECRET = "..."
java -jar packing-list-ami-1.0.0-SNAPSHOT.jar
```

**Por qué "desde dentro".** La aplicación busca la base de datos en `.\datos\`, relativo al directorio
desde el que se arranca. Si se arranca desde otro sitio, **crea una base vacía allí y no da ningún
error**: parece que funciona y se ha perdido todo de vista. En el servidor esto lo garantiza el
`WorkingDirectory` de la unidad de systemd, que es tarea de informática.

**Cómo sé que ha ido bien.**

- [ ] En el log del arranque aparecen las migraciones aplicándose (`Migrating schema ... to version 2`,
      `3`, `4`) y ningún error de validación de esquema.
- [ ] En `http://localhost:8080/taras` **están los pesos que se pesaron a mano**.
- [ ] En `/temporadas` están las temporadas guardadas, con su fichero.
- [ ] Genero un envío cualquiera y la memoria de referencias sigue rellenando cartón y unidades.

**Si el arranque falla** por un choque entre las tablas y el código, ese es justo el hallazgo de esta
tarea: se resuelve aquí, sobre una copia, y no en el servidor.

---

## Tarea 5 — Pasar la suite y fusionar la rama a `main`

**Qué es.** Todo el login, los permisos y el registro de actividad están en la rama `login-microsoft`,
sin fusionar. Lo que se despliega debería ser `main`: dentro de seis meses, cuando haya que reconstruir
el jar, nadie se acordará de que "el bueno" estaba en una rama.

```bash
mvn test                      # tiene que quedar verde entero
git checkout main
git merge login-microsoft
mvn test                      # otra vez, ya fusionado
```

**Cómo sé que ha ido bien.** `BUILD SUCCESS` y cero tests en rojo. Si algo falla, no se despliega: la
suite es lo único que vigila que las plantillas de los clientes se sigan rellenando donde toca.

---

## Tarea 6 — Construir el jar y entregarlo

```bash
mvn clean package
```

Eso deja `target/packing-list-ami-1.0.0-SNAPSHOT.jar`. Es un fichero autocontenido: lleva dentro el
servidor web, las plantillas de los clientes y todo lo demás. En la máquina no hay que instalar nada
más aparte de Java.

- [ ] Entregar el jar a informática. Lo instalarán como `/opt/packing/packing.jar`.
- [ ] Entregar también, si queremos el modo de fotos y PDF, la clave `ANTHROPIC_API_KEY`: es otro
      secreto y va al mismo fichero protegido del servidor. Sin ella la aplicación arranca igual y solo
      deja de funcionar ese modo de entrada.

---

## Tarea 7 — La mudanza de la base de datos y el "apagado" del PC del almacén

**Qué es.** El día del despliegue hay que llevarse la base de datos buena al servidor y, sobre todo,
**dejar de usar la del PC**. Si la aplicación sigue viva en el PC del almacén, cada una aprende cosas
distintas y a la semana hay dos verdades: taras pesadas en una, temporadas guardadas en la otra, y
ninguna forma limpia de juntarlas.

**Qué hacer, en este orden y el mismo día.**

- [ ] Cerrar la aplicación del PC del almacén, esta vez para no volver a abrirla.
- [ ] Copiar `datos\` (ya en frío) y entregarla a informática para que la deje en `/opt/packing/datos/`.
      Es la tarea 7 de su documento.
- [ ] Quitar del PC el acceso directo con el que se arrancaba, o renombrar la carpeta, para que nadie la
      abra por costumbre.
- [ ] Avisar a quien la use de la dirección nueva: **https://app.puntotres.com**.

---

## Tarea 8 — Pruebas de aceptación, ya en el servidor

Informática dará el servicio por bueno cuando responda y el certificado sea válido. Eso no prueba que
la aplicación *haga su trabajo* ahí dentro. Esta lista es la que lo prueba, y la paso yo.

- [ ] **Entrar** en `https://app.puntotres.com` desde un PC de la oficina: candado cerrado, sin avisos,
      login de Microsoft y menú.
- [ ] **Los permisos**: que otra persona entre y compruebe que ve el menú vacío con el texto de a quién
      pedirlos; dárselos desde `/administracion` y comprobar que al siguiente clic ya los tiene.
- [ ] **Un envío entero del taller**: subir el packing list del taller, ajustar, previsualizar, generar.
      Descargar el ZIP y abrir un par de excels.
- [ ] **Las etiquetas** — la prueba importante del servidor. Las etiquetas se dibujan como imágenes
      (códigos de barras y textos) y para eso el sistema operativo necesita tener fuentes instaladas; en
      una instalación mínima de Linux no las hay. Hay que **abrir un libro de etiquetas generado allí**,
      mirar que los textos no se desbordan ni se ven raros, y **pasar un lector físico** por un código
      de barras. Si algo falla, es el paquete de fuentes de la máquina (está en la ficha que les he
      dado), no el código.
- [ ] **La entrada por fotos o PDF**: subir un PDF de hojas manuscritas. Tarda unos cuatro minutos. Esto
      no prueba la extracción, que ya funciona: prueba que los tiempos de espera del proxy están bien
      puestos. Si sale un error de pasarela a los 30 o 60 segundos, es la configuración del proxy.
- [ ] **El Packing Puntotres** (el Word) y el **volcado ERP**: que se descarguen y se abran.
- [ ] **La pantalla de actividad**: que registre lo que acabo de hacer y que el excel del filtro se baje.
- [ ] **Salir** con el botón de salir y comprobar que vuelve a pedir identificarse.

---

## Tarea 9 — Dos cosas que no se olvidan porque duelen luego

- [ ] **El recordatorio del secreto de Entra.** El secreto caduca (24 meses). El día que caduque nadie
      podrá entrar, y no habrá habido ningún aviso previo. Tiene que quedar una entrada en el calendario
      compartido con la fecha, quién lo custodia y qué pasa si nadie lo renueva.
- [ ] **La copia de seguridad la hace informática, pero el dato es nuestro.** Preguntar explícitamente si
      la copia diaria está hecha y **si se ha probado a restaurarla una vez**. Una copia que nunca se ha
      restaurado no es una copia.

---

## Tarea 10 — Dejar la documentación al día

- [ ] El plan
      [2026-09-20-login-microsoft-y-actividad-en-red-local-plan.md](2026-09-20-login-microsoft-y-actividad-en-red-local-plan.md)
      dice `packing.puntotres.com` en todas sus fases, y ahora el nombre es `app.puntotres.com`.
      Cambiarlo, o dejar escrito arriba cuál es el bueno: si no, el día que alguien siga ese plan
      registrará el nombre viejo.
- [ ] Las comprobaciones de aquel plan esperan un `200` en `/menu` porque se escribieron para un jar
      **sin** login. Con el jar de hoy eso devuelve un `302` hacia `login.microsoftonline.com`, y ese es
      el resultado correcto.
