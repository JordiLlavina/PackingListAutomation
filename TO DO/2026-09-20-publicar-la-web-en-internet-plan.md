# Sacar la aplicación fuera de la oficina

> **Para quien ejecute esto:** usa `superpowers:subagent-driven-development` (recomendado) o
> `superpowers:executing-plans` para ir tarea a tarea. Los pasos llevan casilla (`- [ ]`).

**Objetivo:** que `https://packing.puntotres.com` se pueda abrir desde fuera de la red de la
oficina —desde casa, desde el móvil, desde la fábrica— con la misma seguridad con la que se abre
dentro, y que los datos sobrevivan a que la máquina se pierda.

**Arquitectura:** la aplicación no cambia de forma: sigue siendo un Spring Boot detrás de un proxy
que termina TLS. Lo que cambia es de dónde llega el tráfico (un túnel de salida, sin abrir puertos),
dónde vive la base de datos (PostgreSQL en vez del fichero H2) y dónde están las copias (fuera de la
máquina, con una restauración probada).

**Stack añadido:** PostgreSQL 16, Testcontainers, Cloudflare Tunnel o Entra Application Proxy,
OWASP dependency-check.

**Requisito previo, sin excepción:** este plan **empieza donde acaba**
`2026-09-20-login-microsoft-y-actividad-en-red-local-plan.md`. Sin login, sin permisos y sin la CSP,
publicar esto en internet sería poner el pedido y los precios de los clientes de Puntotres en una URL
pública. Si ese plan no está terminado, este no se empieza.

**Diseño del que sale:** `git show dc07522:"TO DO/2026-09-19-login-microsoft-y-actividad-design.md"`,
secciones 10 y 11.

---

## Restricciones globales

1. Las diez restricciones del plan de red local siguen vigentes, **en particular la 9**: las
   migraciones en SQL que entiendan H2 y PostgreSQL, con `{vendor}` solo para el baseline. Este plan
   es el que cobra esa deuda.
2. **Ningún dato de cliente sale de la máquina sin cifrar.** Eso incluye las copias.
3. **Nada que abra un puerto de entrada** sin haber descartado antes el túnel de salida.
4. Comando de la suite: `mvn test`. Una tarea no está hecha si la suite no está verde.

---

## Fase 1 — PostgreSQL

H2 en fichero no tiene copias en caliente, ni restauración a un punto en el tiempo, ni forma de
mirar la base de datos mientras la aplicación corre. Con usuarios, permisos, histórico de actividad
y los excels de pedido de cada temporada dentro, eso deja de ser aceptable en cuanto la máquina es
alcanzable desde fuera.

### Tarea 1.1: El baseline de PostgreSQL y Testcontainers

**Ficheros:**
- Modificar: `pom.xml`
- Crear: `src/main/resources/db/migration/postgresql/V1__esquema_inicial.sql`
- Crear: `docker-compose.yml`
- Crear: `src/test/java/com/puntotres/packinglist/persistence/MigracionesPostgresTest.java`

**Interfaces:**
- Consume: `db/migration/h2/V1__esquema_inicial.sql` y las migraciones de `common` (plan de red
  local, tareas 2.1, 3.3, 4.2, 5.1).
- Produce: un PostgreSQL de desarrollo en `localhost:5432` y la certeza de que las migraciones
  comunes corren en los dos motores.

- [ ] **Paso 1: Escribir el test que falla**

```java
package com.puntotres.packinglist.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Las mismas migraciones, contra PostgreSQL de verdad.
 *
 * Es el único test que prueba que el SQL de db/migration/common es portable.
 * Sin él, la primera vez que alguien escriba una migración con sintaxis de H2
 * no lo sabrá nadie hasta que falle el arranque en el servidor.
 *
 * Necesita Docker. Si no lo hay, el test se salta solo y la portabilidad deja
 * de estar comprobada: eso hay que saberlo, no descubrirlo.
 */
@SpringBootTest
@Testcontainers
class MigracionesPostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void sinBaseline(DynamicPropertyRegistry registro) {
        // La base del contenedor nace vacía: aquí no hay nada que "adoptar".
        registro.add("spring.flyway.baseline-on-migrate", () -> "false");
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void todasLasMigracionesCorrenEnPostgres() throws Exception {
        List<String> fallidas = new ArrayList<>();
        int aplicadas = 0;
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "select version, success from flyway_schema_history order by installed_rank")) {
            while (rs.next()) {
                aplicadas++;
                if (!rs.getBoolean("success")) {
                    fallidas.add(rs.getString("version"));
                }
            }
        }
        assertEquals(List.of(), fallidas);
        assertTrue(aplicadas >= 4, "Faltan migraciones: esperadas al menos V1..V4");
    }

    @Test
    void lasTablasDeSeguridadYActividadExistenEnPostgres() throws Exception {
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement()) {
            for (String tabla : List.of("usuario", "usuario_permiso", "evento_actividad",
                    "tara_caja", "memoria_caja_referencia", "temporada_guardada")) {
                try (ResultSet rs = st.executeQuery("select count(*) from " + tabla)) {
                    assertTrue(rs.next(), "No se puede consultar " + tabla);
                }
            }
        }
    }
}
```

- [ ] **Paso 2: Comprobar que falla**

`mvn test -Dtest=MigracionesPostgresTest` → FALLA: no compila (faltan Testcontainers) y no existe el
baseline de PostgreSQL.

- [ ] **Paso 3: Las dependencias**

En `pom.xml`, junto a la de H2:

```xml
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
```

y en el bloque de test:

```xml
        <!-- Los tests de migraciones corren contra un PostgreSQL de verdad:
             es la única forma de saber que el SQL de db/migration/common es
             portable antes de descubrirlo en el servidor. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Paso 4: El baseline de PostgreSQL**

`src/main/resources/db/migration/postgresql/V1__esquema_inicial.sql`: **el gemelo exacto** del de
H2, con el único cambio que obligó a partirlos.

```sql
-- Gemelo de db/migration/h2/V1__esquema_inicial.sql.
--
-- La única diferencia es el tipo del excel de pedido guardado: blob en H2,
-- bytea en PostgreSQL. Todo lo demás es idéntico, y de V2 en adelante no hay
-- ningún fichero partido: las migraciones comunes se escriben en SQL que
-- entienden los dos motores.
--
-- Si algún día hay que partir otra migración, el motivo se escribe aquí.

create table tara_caja (
    medida   varchar(32)      not null,
    tara_kg  double precision not null,
    primary key (medida)
);

create table memoria_caja_referencia (
    cliente            varchar(64)      not null,
    referencia         varchar(64)      not null,
    medida_caja        varchar(32),
    unidades_por_caja  integer,
    peso_neto_kg       double precision,
    primary key (cliente, referencia)
);

create table temporada_guardada (
    id              bigint generated by default as identity,
    cliente         varchar(64)  not null,
    temporada       varchar(64)  not null,
    nombre_fichero  varchar(255) not null,
    contenido       bytea        not null,
    fecha_alta      timestamp with time zone,
    primary key (id)
);
```

**Comprobar columna a columna contra el fichero de H2 antes de seguir.** Una columna que difiera
entre los dos baselines es un `ddl-auto: validate` que falla al arrancar en el servidor y en ningún
otro sitio.

- [ ] **Paso 5: El PostgreSQL de desarrollo**

`docker-compose.yml` en la raíz del proyecto:

```yaml
# PostgreSQL para desarrollo y para mirar la base de datos a mano.
# En el servidor, PostgreSQL se instala del sistema (tarea 1.2), no de aquí.
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: packinglist
      POSTGRES_USER: packing
      POSTGRES_PASSWORD: desarrollo
    ports:
      - "5432:5432"
    volumes:
      - postgres-datos:/var/lib/postgresql/data

volumes:
  postgres-datos:
```

- [ ] **Paso 6: Comprobar que pasa**

```bash
docker compose up -d
mvn test -Dtest=MigracionesPostgresTest
```

→ PASA. Luego `mvn test` → verde. Los demás tests siguen sobre H2 en memoria: solo este habla con
PostgreSQL.

- [ ] **Paso 7: Commit**

```bash
git add pom.xml docker-compose.yml src/main/resources/db/migration/postgresql \
        src/test/java/com/puntotres/packinglist/persistence/MigracionesPostgresTest.java
git commit -m "las migraciones corren tambien contra un postgres de verdad"
```

### Tarea 1.2: PostgreSQL en el servidor y el traspaso de los datos

**Ficheros:** en el servidor; y `/etc/packing/entorno`.

**El traspaso es de una sola dirección y sin vuelta fácil.** Hacerlo con la aplicación parada y con
la copia de la tarea 1.4 del plan anterior recién hecha y comprobada.

- [ ] **Paso 1: Instalar PostgreSQL**

```
sudo apt-get update && sudo apt-get install -y postgresql
sudo -u postgres createuser packing --pwprompt
sudo -u postgres createdb packinglist --owner=packing
```

Anotar la contraseña: va al fichero de entorno del paso 3.

- [ ] **Paso 2: Comprobar que solo escucha en local**

```
sudo -u postgres psql -c "show listen_addresses;"
```

Esperado: `localhost`. La aplicación y la base de datos viven en la misma máquina, así que PostgreSQL
no tiene ningún motivo para escuchar en la red — y menos ahora que la máquina va a ser alcanzable
desde fuera. Si no es `localhost`, corregirlo en `/etc/postgresql/16/main/postgresql.conf` y
reiniciar.

- [ ] **Paso 3: Apuntar la aplicación a PostgreSQL**

Añadir a `/etc/packing/entorno`:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/packinglist
SPRING_DATASOURCE_USERNAME=packing
SPRING_DATASOURCE_PASSWORD=<la contraseña del paso 1>
```

El `application.yml` sigue apuntando a H2: así el desarrollo en el PC no necesita nada y el servidor
manda por entorno. La contraseña **nunca** en el yml del jar ni en git.

- [ ] **Paso 4: Arrancar contra la base vacía**

```
sudo systemctl restart packing
sudo journalctl -u packing -n 80 --no-pager
```

Esperado: Flyway aplicando V1 (el de `postgresql`), V2, V3 y V4, y la aplicación arrancando. En este
punto la base está **vacía**: no hay taras, ni temporadas, ni usuarios.

Si falla con `Schema-validation:`, el baseline del paso 4 de la tarea 1.1 no cuadra con las
entidades. Se corrige el SQL.

- [ ] **Paso 5: Traspasar los datos**

Las tres tablas con datos de verdad son `tara_caja`, `memoria_caja_referencia` y
`temporada_guardada`; `usuario`, `usuario_permiso` y `evento_actividad` también, si el plan anterior
lleva tiempo en marcha.

Con la aplicación **parada**, volcar de H2 a CSV y cargar en PostgreSQL:

```
sudo systemctl stop packing

# Volcado desde H2, tabla a tabla
java -cp ~/.m2/repository/com/h2database/h2/*/h2-*.jar org.h2.tools.Shell \
  -url jdbc:h2:file:/opt/packing/datos/packinglist -user sa \
  -sql "call csvwrite('/tmp/tara_caja.csv', 'select * from tara_caja')"
```

y lo mismo para cada tabla. Luego, en PostgreSQL:

```
sudo -u postgres psql packinglist -c "\copy tara_caja from '/tmp/tara_caja.csv' with (format csv, header true)"
```

**`temporada_guardada` no pasa por CSV**: lleva el excel entero en una columna binaria y el CSV lo
estropea. Para esa tabla, la vía buena es un programa de un solo uso que lea de H2 con JDBC y escriba
en PostgreSQL con JDBC, o —más simple— **volver a subir los excels de cada temporada desde la
pantalla `/temporadas`**, que son media docena y ya están en la carpeta de quien los subió. Elegir
antes de empezar y anotar cuál se eligió.

- [ ] **Paso 6: Comprobar el traspaso**

```
sudo systemctl start packing
```

Abrir `https://packing.puntotres.com` y comprobar **una a una**:

- `/taras`: están todas las taras pesadas, con sus pesos
- `/temporadas`: están las temporadas, y **descargar una** para ver que el excel se abre
- `/administracion/usuarios`: están los usuarios con sus permisos
- `/actividad`: el histórico sigue ahí

Si falta algo, **parar y volver a la copia**: `sudo systemctl stop packing`, devolver
`SPRING_DATASOURCE_*` a H2 comentando esas tres líneas, y arrancar. Nada se ha perdido mientras el
fichero H2 siga en `/opt/packing/datos/`.

- [ ] **Paso 7: Guardar el fichero de H2, no borrarlo**

```
sudo mv /opt/packing/datos/packinglist.mv.db /opt/packing/datos/packinglist.mv.db.antes-de-postgres
```

Se queda ahí un par de meses. Borrarlo el primer día es quedarse sin vuelta atrás por ahorrar unos
megas.

### Tarea 1.3: Las copias, ahora con `pg_dump` y fuera de la máquina

**Ficheros:** en el servidor, sustituyen a los de la tarea 1.4 del plan anterior.

**Una copia dentro de la misma máquina no es una copia.** Protege de un borrado, no de que la
máquina se pierda, que es exactamente el riesgo que sube al publicar.

- [ ] **Paso 1: El script**

Sustituir `/opt/packing/copia.sh` por:

```
#!/bin/bash
set -euo pipefail
destino=/var/backups/packing
mkdir -p "$destino"
fecha=$(date +%Y-%m-%d)
fichero="$destino/packinglist-$fecha.sql.gz"

PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" pg_dump -h localhost -U packing packinglist \
  | gzip > "$fichero"

# Cifrada antes de salir de la máquina: lleva dentro los excels de pedido de
# los clientes y el histórico de quién ha hecho qué.
gpg --batch --yes --encrypt --recipient "$GPG_DESTINATARIO" "$fichero"
rm -f "$fichero"

# Fuera de la máquina. El destino se configura en /etc/packing/copias.
rclone copy "$fichero.gpg" "$RCLONE_DESTINO"

find "$destino" -name 'packinglist-*.sql.gz.gpg' -mtime +30 -delete
```

- [ ] **Paso 2: Configurar el destino y la clave**

Crear `/etc/packing/copias` con permisos `600`:

```
GPG_DESTINATARIO=<el correo de la clave pública con la que se cifran>
RCLONE_DESTINO=<remoto:carpeta de rclone>
```

y añadir `EnvironmentFile=/etc/packing/copias` y `EnvironmentFile=/etc/packing/entorno` a
`/etc/systemd/system/packing-copia.service`.

El destino puede ser cualquier cosa que sepa `rclone`: un OneDrive de la empresa, un S3, otro
servidor. Lo que **no** puede ser es la misma máquina.

- [ ] **Paso 3: Probar la copia**

```
sudo systemctl daemon-reload
sudo systemctl start packing-copia.service
sudo journalctl -u packing-copia -n 30 --no-pager
```

Esperado: un `.sql.gz.gpg` en el destino remoto.

- [ ] **Paso 4: Probar la RESTAURACIÓN**

**Este paso es la tarea.** Los tres anteriores solo hacen ficheros.

```
# Traerse la copia del destino remoto y descifrarla
rclone copy "$RCLONE_DESTINO/packinglist-$(date +%Y-%m-%d).sql.gz.gpg" /tmp/
gpg --decrypt /tmp/packinglist-*.sql.gz.gpg | gunzip > /tmp/restaurada.sql

# Restaurar en una base de pruebas, NUNCA sobre la buena
sudo -u postgres createdb prueba_restauracion --owner=packing
sudo -u postgres psql prueba_restauracion < /tmp/restaurada.sql
sudo -u postgres psql prueba_restauracion -c "select count(*) from tara_caja;"
sudo -u postgres psql prueba_restauracion -c "select count(*) from temporada_guardada;"
sudo -u postgres psql prueba_restauracion -c "select octet_length(contenido) from temporada_guardada limit 1;"
```

Esperado: los recuentos cuadran con los de la base real, y el `octet_length` de un excel guardado
es de decenas de miles de bytes, no cero. Si el binario sale vacío, la copia no sirve y hay que
arreglarla **antes** de publicar nada.

Luego `sudo -u postgres dropdb prueba_restauracion` y borrar los ficheros de `/tmp`.

- [ ] **Paso 5: Anotar la fecha de la prueba**

Escribirla aquí debajo, con el resultado. **Repetirla cada seis meses**: una copia que se probó una
vez hace dos años y desde entonces cambió el esquema tampoco es una copia.

---

## Fase 2 — Endurecimiento adicional

Lo que no tenía sentido en la red local y sí lo tiene con tráfico de internet.

### Tarea 2.1: HSTS y el repaso de cabeceras con TLS público

**Ficheros:** `/etc/caddy/Caddyfile` en el servidor.

- [ ] **Paso 1: Poner HSTS en el proxy, no en la aplicación**

Lo pone quien termina el TLS. En el bloque de `packing.puntotres.com` del `Caddyfile`:

```
        header {
                Strict-Transport-Security "max-age=31536000; includeSubDomains"
        }
```

**Ojo con `includeSubDomains`**: afecta a todo `*.puntotres.com` en los navegadores que hayan
visitado esta página. Si hay algún otro servicio de la empresa en un subdominio **sin** TLS, dejará
de ser accesible desde esos navegadores durante un año. Comprobarlo antes; si hay dudas, poner la
directiva sin `includeSubDomains`.

- [ ] **Paso 2: Comprobar la nota final**

`sudo systemctl reload caddy` y luego, desde fuera de la oficina, pasar el dominio por
`https://www.ssllabs.com/ssltest/`. Esperado: A o A+. Cualquier cosa por debajo se lee en el informe
y se corrige.

### Tarea 2.2: Vigilar las dependencias

**Ficheros:** `pom.xml`.

Son 69 MB de jar y muchas dependencias transitivas. Mientras la aplicación vivía en la red local eso
era un riesgo teórico; con tráfico de internet deja de serlo.

- [ ] **Paso 1: Añadir el plugin**

```xml
            <!-- No corre en cada build: se lanza a mano antes de publicar una
                 versión, con mvn verify -Pseguridad. Meterlo en el ciclo
                 normal añadiría minutos a cada "mvn test" y acabaría
                 desactivado. -->
            <plugin>
                <groupId>org.owasp</groupId>
                <artifactId>dependency-check-maven</artifactId>
                <version>10.0.4</version>
                <configuration>
                    <failBuildOnCVSS>7</failBuildOnCVSS>
                </configuration>
            </plugin>
```

dentro de un perfil `seguridad`.

- [ ] **Paso 2: Pasarlo una vez**

```bash
mvn verify -Pseguridad
```

La primera vez tarda (se descarga la base de vulnerabilidades). Leer el informe de
`target/dependency-check-report.html` y anotar aquí debajo lo que salga con CVSS ≥ 7, con qué se
hizo con cada uno.

- [ ] **Paso 3: Ponerlo en el calendario**

Una revisión **antes de cada publicación de una versión nueva** y, como mínimo, una vez al
trimestre. Junto al recordatorio de la caducidad del client secret de Entra.

- [ ] **Paso 4: Commit**

```bash
git add pom.xml
git commit -m "las dependencias se revisan antes de publicar"
```

### Tarea 2.3: Repasar lo que ya está y no tocar lo que funciona

Lista de comprobación, sin cambios de código esperados:

- [ ] **Consola de H2**: comprobar que no está habilitada. `git grep -n "h2.console"` → sin
      resultados. Tras la fase 1 la dependencia de H2 solo hace falta para los tests; si se quiere,
      pasarla a `<scope>test</scope>`, pero **solo después** de que el traspaso a PostgreSQL lleve
      un mes funcionando.
- [ ] **Actuator**: comprobar que no está en el `pom.xml`. Si algún día entra, solo
      `/actuator/health` y el resto cerrado — y con su ruta reclamada por una `Seccion` o declarada
      pública, o `CoberturaDeRutasTest` fallará, que es lo que se quiere.
- [ ] **Subidas**: los límites de 20 MB y 60 MB se quedan como están. Y **no tocar**
      `ZipSecureFile.setMinInflateRatio`: un `.xlsx` es un zip, y bajar ese ratio abre la puerta a
      una bomba de descompresión. `git grep -n "setMinInflateRatio"` → sin resultados.
- [ ] **Tiempos de espera del proxy**: los 15 minutos del `reverse_proxy` siguen haciendo falta (la
      extracción por fotos tarda unos cuatro minutos). No bajarlos "por seguridad".

---

## Fase 3 — La ruta desde fuera

**Esta fase la ejecutan los informáticos de la empresa, no este plan.** Lo que sigue es lo que hay
que pedirles y lo que la aplicación necesita de vuelta.

### Tarea 3.1: Elegir cómo entra el tráfico

Dos caminos, en orden de preferencia:

| | Cómo | A favor | En contra |
|---|---|---|---|
| **A. Túnel de salida** (recomendado) | Cloudflare Tunnel o Entra Application Proxy: un agente en el servidor abre una conexión **hacia fuera** y el tráfico vuelve por ella | **Ningún puerto de entrada.** El cortafuegos de la empresa se queda como está. Con Application Proxy, además, la preautenticación la hace Entra antes de que el tráfico toque la máquina | Depende de un servicio de terceros. Application Proxy necesita **Entra ID P1** (incluida en Microsoft 365 Business Premium): hay que comprobar la licencia |
| **B. NAT al 443** | Publicación clásica en el router | No depende de nadie | La VM tiene que ir en **VLAN aislada sin acceso al resto de la LAN**. El riesgo de un servidor propio expuesto no es que entren en la aplicación: es que desde ella lleguen al ERP y a las carpetas compartidas |

- [ ] **Paso 1: Comprobar la licencia de Microsoft 365**

Si hay Business Premium o superior, Entra Application Proxy es una opción y es la mejor de las tres:
pone el login de Microsoft **delante** de la aplicación, además del que ya tiene.

- [ ] **Paso 2: Decidir y anotarlo aquí**

Escribir debajo cuál se eligió y por qué. La decisión cambia los pasos 3 y 4.

- [ ] **Paso 3 (si es túnel): montar el agente**

El agente corre en el servidor y apunta a `127.0.0.1:443` (Caddy) o directamente a `127.0.0.1:8080`.
**Si apunta al 8080 saltándose Caddy, el TLS lo termina el túnel**, y entonces hay que comprobar que
sigue mandando `X-Forwarded-Proto: https`, o el login volverá a fallar.

- [ ] **Paso 4 (si es NAT): la VLAN**

La VM en una VLAN sin ruta al resto de la LAN, con una regla explícita que permita solo lo que
necesite (DNS y actualizaciones de sistema). **Sin esa VLAN, este camino no se toma.**

- [ ] **Paso 5: DNS que resuelva dentro y fuera**

`packing.puntotres.com` tiene que resolver a la IP privada **dentro** de la oficina y a la pública
(o al túnel) **fuera**. Con port-forward clásico, el router no suele hacer el giro y aparece el
síntoma clásico: *"funciona desde casa y no desde la oficina"*. Se arregla con una entrada en el DNS
interno.

- [ ] **Paso 6: Comprobarlo desde los dos lados**

Desde un PC de la oficina y desde un móvil con datos, **sin VPN**:

```
https://packing.puntotres.com
```

Esperado en los dos: el login de Microsoft, y luego el menú. Si desde fuera pide login y desde
dentro no, o al revés, la sesión está viajando distinto por cada camino y hay que mirar las
cabeceras del proxy antes de dar esto por bueno.

### Tarea 3.2: La URI de redirección de producción en Entra

- [ ] **Paso 1: Comprobar que ya está**

La tarea 0.1 del plan anterior ya registró `https://packing.puntotres.com/login/oauth2/code/azure`.
Si el dominio ha cambiado en el camino, actualizarlo.

- [ ] **Paso 2: Quitar la URI de desarrollo del registro de producción**

`http://localhost:8080/login/oauth2/code/azure` **se queda**, y es a propósito: sin ella no se puede
desarrollar contra el login de verdad, y la alternativa —un perfil local sin seguridad— es justo la
clase de interruptor que acaba puesto en producción. Un `localhost` solo sirve a quien ya está en la
máquina.

Si aun así se prefiere separarlos, lo correcto es **un segundo registro de aplicación** para
desarrollo, con su propio secreto, no borrar la URI.

---

## Fase 4 — Operación

Lo que hay que dejar montado para que la máquina se pueda quedar sola.

### Tarea 4.1: La lista para entregarle a informática

Para dársela tal cual:

1. **Sin puertos de entrada desde internet** si se puede: publicación por túnel de salida
   (Cloudflare Tunnel o Entra Application Proxy). Si tiene que haber NAT al 443, la VM va en **VLAN
   aislada sin acceso al resto de la LAN**.
2. **TLS** con certificado válido para `packing.puntotres.com` y **renovación automática**.
3. Cabeceras `X-Forwarded-*` correctas en el proxy. La aplicación ya lleva
   `SERVER_FORWARD_HEADERS_STRATEGY=framework`; sin las cabeceras, el login falla.
4. **Hipervisor de servicio** (Hyper-V o Proxmox), **no VirtualBox**: VirtualBox es un hipervisor de
   escritorio, corre como proceso de un usuario del host y se para cuando ese usuario cierra sesión.
5. **Cifrado del disco de la VM.**
6. **Copia diaria fuera de la máquina con restauración probada** — ya montada en la tarea 1.3; lo
   que hace falta de ellos es el destino remoto y que esté dentro de su propio plan de copias.
7. Actualizaciones de seguridad del sistema **automáticas**
   (`unattended-upgrades`); acceso de administración solo por **SSH con clave**, no con contraseña.
8. DNS interno para que `packing.puntotres.com` resuelva también **dentro** de la oficina.
9. Registro de la aplicación en Entra: ya hecho. Lo que necesitan saber es **quién custodia el
   client secret y cuándo caduca**.

### Tarea 4.2: Los recordatorios que evitan los fallos silenciosos

- [ ] **Paso 1: En el calendario compartido, tres entradas**

```
1. <fecha del secreto - 1 mes>: renovar el client secret de Entra de
   "Packing List Puntotres". Si caduca, nadie puede entrar.
2. Cada trimestre: mvn verify -Pseguridad y leer el informe.
3. Cada seis meses: probar la restauración de una copia (tarea 1.3, paso 4).
```

- [ ] **Paso 2: Comprobar que el arranque sobrevive a un reinicio**

```
sudo reboot
```

Y desde otro sitio, a los dos minutos: `curl -sI https://packing.puntotres.com/menu | head -1`.
Esperado: responde sin que nadie haya tocado nada. Si no, falta `systemctl enable` en alguno de los
tres servicios (`packing`, `caddy`, el agente del túnel).

---

## Fase 5 — Documentación

### Tarea 5.1: Dejar los documentos al día

- [ ] **Paso 1: El README**

Retirar lo que quede de *"solo dentro de la red de la empresa"*. Ahora la frase es: se entra con la
cuenta de Microsoft de Puntotres desde cualquier sitio, en `https://packing.puntotres.com`.

Añadir cómo se levanta el entorno de desarrollo: `docker compose up -d` y las tres variables
`ENTRA_*`.

- [ ] **Paso 2: El ARCHITECTURE**

Que la base de datos es PostgreSQL, que el esquema lo mandan las migraciones y que
`db/migration/{vendor}` solo alberga el baseline, con el motivo (el tipo binario del excel guardado).

- [ ] **Paso 3: El CLAUDE.md**

Lo que no se deduce del código:

- que el baseline está partido por motor y **por qué solo ese**
- que `MigracionesPostgresTest` es el único sitio donde se comprueba la portabilidad del SQL, y que
  **sin Docker ese test se salta y la portabilidad deja de estar comprobada**
- que PostgreSQL escucha solo en `localhost` a propósito
- que el fichero H2 antiguo se conserva un par de meses tras el traspaso
- que la copia va cifrada antes de salir de la máquina porque lleva dentro los excels de pedido de
  los clientes

- [ ] **Paso 4: Commit**

```bash
git add README.md ARCHITECTURE.md CLAUDE.md
git commit -m "los documentos cuentan que esto vive en postgres y sale a internet"
```

---

## Lo que hay que decidir al ejecutar esto

1. **Túnel o NAT** (tarea 3.1), y con él si hay licencia **Entra ID P1** para Application Proxy.
2. **Cómo se traspasa `temporada_guardada`** (tarea 1.2, paso 5): programa de un solo uso, o volver
   a subir los excels desde `/temporadas`.
3. **Dónde van las copias** (tarea 1.3): qué remoto de `rclone` y con qué clave se cifran.
4. Si `includeSubDomains` en el HSTS es seguro para el resto de `puntotres.com` (tarea 2.1).

## Fuera de alcance

- MFA y políticas de contraseña: son de Entra. Si la empresa exige segundo factor, este login lo
  hereda sin una línea de código.
- Alta disponibilidad, réplicas de la base de datos, balanceo. Es una aplicación de almacén con una
  docena de usuarios: una máquina que se reinicia sola es suficiente, y pretender otra cosa cuesta
  más de lo que vale.
- Monitorización con alertas. Si hace falta, es un plan aparte.
- La limitación de peticiones: sigue sin haber tráfico anónimo, así que sigue sin haber nada que
  limitar.

## Riesgos conocidos

1. **El traspaso de `temporada_guardada`**: es la única tabla con un binario grande dentro y la única
   que no pasa por CSV. Si se hace mal, el síntoma es un excel de pedido que se descarga corrupto, y
   eso no se ve hasta que alguien lo abre.
2. **El túnel es una dependencia de un tercero.** Si Cloudflare o Application Proxy tienen una
   incidencia, la aplicación no se abre desde fuera. Desde dentro de la oficina sigue funcionando,
   que es donde está el almacén.
3. **`includeSubDomains` en el HSTS** puede dejar sin acceso a otro subdominio de la empresa que no
   tenga TLS, durante un año y solo en los navegadores que hayan visitado esta página. Se comprueba
   antes o no se pone.
4. **La superficie sube de verdad.** Hasta hoy, para atacar esto había que estar dentro de la red.
   Lo que lo sostiene son el login de Entra, los permisos por sección, la CSP y —si se elige el
   túnel— que no haya ningún puerto abierto. Las cuatro cosas, no tres.
