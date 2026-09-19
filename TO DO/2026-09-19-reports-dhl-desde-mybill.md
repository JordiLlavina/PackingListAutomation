# Reports de envíos DHL a partir de las facturas de MyBill

Fecha: 2026-09-19. Estado: **pendiente, sin empezar**. Sustituye como dirección a la
creación de envíos desde la app (rama `envios-dhl`, que queda aparcada sin fusionar).

## 1. Decisión de la que parte este plan

Los envíos se **crean en MyDHL+**, la web de DHL, no en nuestra app. Lo que la app
aporta es lo que DHL no da: el coste real de cada envío agrupado por **temporada,
motivo y responsable**, acumulado a lo largo de los meses, y una lista corregible de
lo que no se etiquetó bien.

Por qué así y no creando envíos por API:

- MyDHL+ ya tiene libreta, plantillas, catálogo de artículos, recogida y etiqueta.
  No vamos a ganarle en pantalla.
- El coste **real** (combustible, corrección de peso, zona remota, aranceles) no
  existe en la API de envíos, que solo da la tarifa estimada. Vive en **MyBill**, el
  portal de facturación. Cualquier report económico necesita MyBill igual.
- La API de MyDHL **no tiene endpoint de facturación** (comprobado en la especificación
  3.3.2, `docs/Envios DHL/mydhl-openapi-3.3.2.yaml` de la rama). Los endpoints
  `/invoices/...` sirven para subir la factura comercial al despacho sin papel.

## 2. Prerrequisitos fuera del código

Sin esto el software no tiene nada que leer. Van antes que cualquier tarea.

| Qué | Quién | Cómo se comprueba |
|---|---|---|
| **Convención de referencia** acordada y escrita por todos al crear el envío en MyDHL+ (sección 3) | Equipo (Pol, Susana, Carme, Jordi) | Una semana de envíos con la referencia bien puesta |
| **Un login de MyDHL+ por persona**, nunca compartido | Jordi con el gestor de DHL | El administrador ve en "Mis envíos" quién creó cada guía |
| **Formato del export de MyBill**: columnas exactas del CSV, si trae la referencia del envío y el desglose de cargos | Jordi, bajando una factura real | Guardar una factura real en `docs/Envios DHL/` (fuera de git: lleva nombres de destinatarios) |
| **Factura electrónica por correo** con el CSV adjunto activable en vuestro perfil de MyBill | Jordi en MyBill o con el gestor | Llega un correo con PDF + CSV |
| Cuántos **campos de referencia** admite vuestro perfil de MyDHL+ y cuántos llegan a la factura | Gestor de DHL | Si son dos, temporada y motivo pueden ir separados del responsable |

## 3. Convención de referencia (propuesta)

Un solo campo, sin espacios, en mayúsculas, separado por guiones:

```
<TEMPORADA>-<MOTIVO>-<RESPONSABLE>
H26-MUESTRAS-POL
FALL26-PRODUCCION-SUSANA
```

- **Temporada**: la del cliente tal como se escribe en el asistente de packing list
  (`H26`, `FALL26`). Para envíos que no son de una temporada, un valor fijo tipo `GEN`.
- **Motivo**: los cinco que ya se definieron para la fase 1, sin acentos:
  `MUESTRAS`, `PRODUCCION`, `REPOSICION`, `DEVOLUCION`, `REPARACION`. Se puede añadir
  alguno, pero cada motivo nuevo es una línea de configuración, no una decisión al
  teclear.
- **Responsable**: el nombre de pila en mayúsculas. Cuando llegue el login de
  Microsoft se cruzará con el usuario.

Reglas del lector: tolera minúsculas y espacios de más; **no adivina**: si falta un
tramo o el motivo no está en la lista, la guía queda "sin clasificar" y se corrige en
pantalla. Si el cliente del envío también interesa para los reports, se añade un
cuarto tramo opcional (`-AMI`, `-APC`); decidirlo antes de empezar a escribir
referencias, porque cambiar la convención a mitad obliga a reclasificar.

## 4. Etapas

Cada etapa es útil por sí sola. Se puede parar después de cualquiera.

### Etapa 0 — Excel, sin código

Bajar los CSV de MyBill de dos o tres meses, pegarlos en un libro y hacer un pivot por
los tramos de la referencia. Sirve para dos cosas: comprobar que el CSV trae lo que
creemos y ver qué preguntas de verdad se hacen sobre los datos. Si con esto basta, el
resto del plan no hace falta.

### Etapa 1 — Importador manual + reports

Subir el CSV de MyBill desde una pantalla, guardar una fila por guía y consultar
reports. Es el núcleo del plan.

**Importador** (`/envios-dhl/facturas`):
- Subida de uno o varios CSV. Previsualización antes de guardar: número de guías,
  total facturado, guías sin clasificar, guías ya importadas (misma factura + misma
  guía = duplicado, se salta con aviso).
- Una guía que aparece en dos facturas (transporte en una, aranceles en otra) **suma**
  en la misma guía pero **conserva cada línea de factura** para poder auditar.
- Fichero CSV entero guardado en base de datos junto a la factura, como se hace con
  los excels de temporada: así se puede reimportar si cambia el lector.
- Misma forma que `ImportadorLibretaMyDhl` de la rama (lector CSV, previsualización,
  duplicados, informe de errores). Reutilizar `LectorCsv` y `commons-csv`.

**Sin clasificar** (`/envios-dhl/sin-clasificar`): lista de guías cuya referencia no
cumple la convención, con la referencia cruda visible y tres desplegables para fijar
temporada, motivo y responsable a mano. La corrección se guarda y no se pierde al
reimportar.

**Reports** (`/envios-dhl/reports`): filtros por rango de fechas, temporada, motivo,
responsable y destino. Tabla con total facturado, transporte, recargos y aranceles,
número de guías y peso facturado, agrupada por la dimensión que se elija. Una fila de
totales. Botón de exportar a `.xlsx` para quien quiera seguir en Excel.

### Etapa 2 — Importación automática desde el buzón

Configurar en MyBill la factura electrónica por correo con el CSV adjunto hacia un
buzón de Microsoft 365. La app lee ese buzón con Microsoft Graph, detecta correos
nuevos de DHL, importa el adjunto con el mismo importador de la etapa 1 y marca el
correo como procesado. Lo que falle queda en el log y en una lista de "correos no
procesados", nunca en silencio.

Depende del alta de la app en Azure AD, que es el mismo trámite que hará falta para el
login de Microsoft: conviene hacer los dos a la vez.

### Etapa 3 — Tablero de estado (opcional)

Solo si "Mis envíos" de MyDHL+ no basta. Lista de guías de los últimos N días con su
último evento de tracking, consultado por la API (`GET /tracking`, solo lectura). El
cliente HTTP, el interceptor de log sin credenciales y el traductor de errores ya
existen en la rama `envios-dhl` (`carrier/dhl`). Las guías saldrían de las facturas
importadas o del export de "Mis envíos" de MyDHL+.

## 5. Modelo de datos (propuesta, a ajustar con el CSV real)

```
factura_dhl          numero_factura (único), fecha_factura, fichero (blob), nombre_fichero,
                     importado_en, guias, total

linea_factura_dhl    factura_id, guia, fecha_envio, origen, destino, producto, bultos,
                     peso_facturado, referencia_cruda, cargo_transporte, cargo_combustible,
                     otros_recargos, aranceles_impuestos, total, moneda

envio_dhl            guia (único), fecha_envio, origen, destino, producto, bultos,
                     peso_facturado, referencia_cruda, temporada, motivo, responsable,
                     clasificacion (AUTOMATICA | MANUAL | PENDIENTE),
                     total_facturado (suma de sus líneas)
```

`envio_dhl` es la fila que ven los reports; `linea_factura_dhl` es la verdad contable.
Una corrección manual pone `clasificacion = MANUAL` y a partir de ahí el importador
**no la pisa**.

Esquema: decidir al empezar si se hace con Flyway (como la rama `envios-dhl`, que
trae el baseline hecho y probado) o con `ddl-auto: update` como el resto de la app.
Si se fusiona algo de la rama, Flyway; si no, `update` y punto.

## 6. Qué se rescata de la rama `envios-dhl`

- `LectorCsv` y el patrón previsualización → confirmar → informe del importador.
- `ShipmentReason` y su semilla de cinco motivos.
- `EnviosProperties` (responsables, moneda).
- `carrier/dhl`: cliente, log e interceptor, solo para la etapa 3.
- El estilo y la tarjeta del menú.

No se rescata: creación de envíos, tarifas, libreta de contactos, catálogo de
artículos, API REST. Se quedan en la rama por si algún día se quiere crear un envío
DHL a partir de un packing list generado en la app, que es lo único que MyDHL+ no
puede hacer.

## 7. Reglas del proyecto que aplican

- Nunca fallar en silencio, nunca bloquear por lo que un humano puede resolver: una
  referencia mal escrita es un aviso y una fila en "sin clasificar", no un error.
- El CSV real de MyBill lleva nombres y direcciones de destinatarios: va en
  `docs/Envios DHL/` y en `.gitignore`, igual que la libreta. El fixture de tests es un
  recorte **anonimizado** en `src/test/resources/ejemplos/`.
- Tests: lector y clasificador con JUnit puro; importador y reports con
  `@SpringBootTest` y H2 en memoria; el `.xlsx` exportado se reabre con POI.
- Ningún test fija un importe del CSV real; los importes de prueba van en el fixture.

## 8. Preguntas que hay que responder antes de la etapa 1

1. ¿El CSV de MyBill trae la referencia del envío? ¿Con qué nombre de columna?
2. ¿El desglose de cargos viene en columnas o en varias líneas por guía?
3. ¿Los aranceles llegan en una factura aparte con la misma guía?
4. ¿Moneda siempre EUR?
5. ¿Interesa el cliente como dimensión de report? Si sí, cuarto tramo en la referencia.
6. ¿Qué periodo de histórico hay que cargar al principio? MyBill guarda facturas de
   varios meses atrás; conviene bajarlas todas de golpe el primer día.

## 9. Orden sugerido

1. Convención de referencia acordada y en uso (esta semana).
2. Bajar una factura real y responder la sección 8.
3. Etapa 0 con dos o tres meses de CSV.
4. Si el pivot cansa o las preguntas crecen: etapa 1.
5. Con el alta en Azure para el login de Microsoft: etapa 2.
6. Etapa 3 solo si se echa en falta.
