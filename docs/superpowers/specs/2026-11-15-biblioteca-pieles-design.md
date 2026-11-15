# Biblioteca de pieles: el conocimiento de pieles y proveedores — diseño

Fecha: 2026-11-15. Rama `claude/leather-database-feature-ac64mp`. Acordado en la
conversación con el usuario. Relacionado con
[2026-11-01-catalogo-modelos-design.md](2026-11-01-catalogo-modelos-design.md)
(la tabla `piel` que ya existe) y
[2026-11-08-modelos-y-modificaciones-design.md](2026-11-08-modelos-y-modificaciones-design.md)
(los prototipos).

Estado: **implementado** (2026-11-15, misma rama), sin el agente de fichas
técnicas, que sigue siendo la fase siguiente. Desvíos respecto de lo escrito
abajo, todos hacia menos trabajo al teclear:

- El grueso y la superficie se teclean en **una casilla** (`1,2` o `1,1-1,3`) y no
  en dos; en la base siguen siendo mínimo y máximo.
- El **cliente de un evento es texto** (la pantalla sugiere los del yml): una piel
  se puede buscar para alguien que aún no es cliente.
- En la tabla solo se editan en la casilla los datos principales (animal, tipo,
  grueso, superficie, stock service, plazo y calidad); los precios, las fotos y lo
  demás, en la ficha.
- La pantalla de proveedores no lista las pieles de cada uno: enseña cuántas tiene
  y enlaza a la tabla filtrada por ese proveedor.

## Qué pidió el usuario

Una base de datos en la app de Automatizaciones donde guardar lo que se sabe de
las pieles y de sus proveedores: proveedor, tipo, precios según MOQ, MOQ, stock
service, superficie, grueso, valoración del proveedor (responsabilidad, entrega a
tiempo), calidad, fotos, y **para qué cliente se buscó cada piel**. Tres
condiciones:

- **Entrar los datos tiene que ser lo menos tedioso posible.** Más adelante, un
  agente digerirá fichas técnicas y el usuario solo comprobará y guardará. El
  agente no entra ahora, pero el diseño tiene que dejarle sitio.
- **Juntas con las pieles de los modelos.** Las automatizaciones solo necesitan
  la referencia y el nombre, y muchas pieles entran desde producción sin nada más.
  Las incompletas y las completas se guardan y se ven juntas.
- **El recorrido de una piel entre clientes.** La misma piel (mismo nombre y
  mismo proveedor) se usa para varios clientes a lo largo del tiempo, cada uno con
  su referencia. Se quiere saber, por ejemplo, que la usó AMI primero, que se le
  enseñó a APC en tal fecha y que luego se hizo con ella un prototipo de APC.

## Decisiones tomadas con el usuario

| Pregunta | Decisión |
|---|---|
| ¿Una piel de proveedor puede ser de varios clientes? | **Sí**, a lo largo del tiempo, cada uno con su referencia (`AL0103` en AMI, `PXCBC` en APC). El nombre de la piel y el proveedor son los mismos. |
| ¿Responsabilidad y entrega a tiempo, por piel o por proveedor? | **Por proveedor.** |
| ¿Histórico de valoraciones o una nota? | **Una sola nota que se va corrigiendo.** |
| ¿Unidad del precio? | **€/m²**, siempre. |
| ¿Fotos? | **Sí**, fotos de la piel. |
| ¿Animal? | **Lista cerrada**: `Vaca` (por defecto) y `Cabra`. |
| ¿Tipo? | **Lista cerrada**, en orden alfabético: `Animal print`, `Cabra`, `Cocodrilo`, `Grabado`, `Napa`, `Nubuck`, `Release`, `Serpiente`, `Serraje`, `Serraje de forro`. |
| ¿Acabado y grabado como campos aparte? | **No.** Se eliminan los dos (los había propuesto el asistente). |
| ¿Texto libre? | Un campo **«Comentarios»** en la ficha. |
| ¿Cuántos tipos lleva una piel? | **Uno solo.** |
| ¿Más animales (cocodrilo, serpiente)? | **No.** Solo hay dos animales en la lista. |
| ¿Login para estas pantallas? | **No.** Quedan abiertas en la red local, como el resto de la app vieja. |
| ¿Enlazar solo por nombre una piel de cliente con su ficha? | **Se propone y lo acepta el usuario**; nunca se enlaza solo. |
| ¿Filtro por precio? | **Descartado.** |

## Supuestos (míos, a confirmar)

- La **calidad es de la piel** y no del proveedor: un mismo proveedor tiene pieles
  buenas y malas. Es también una sola nota que se va corrigiendo, como la del
  proveedor.
- Los **precios por MOQ** son los vigentes, en tramos (`desde 50 m² → 42 €/m²`,
  `desde 200 m² → 38 €/m²`), con la **fecha de la oferta** a nivel de piel. Al
  llegar una oferta nueva se corrigen los tramos. Si se quiere histórico de
  precios, sería una tabla más sin tocar nada de lo de aquí.
- El **MOQ** se expresa en m², con una casilla «por color» (el MOQ de un color o
  el del pedido entero, que no es lo mismo).
- Las dos **listas cerradas** (animal y tipo) viven en `application.yml`, como las
  razones de cambio de la app de modelos: añadir un valor es una línea y un
  reinicio. Un valor guardado que luego se quite del yml se sigue enseñando tal
  cual, no se pierde.
- El **MOQ es el tramo de precio más bajo**: no hay un campo MOQ aparte de los
  tramos, porque dos sitios para el mismo dato acaban diciendo cosas distintas.
  Un MOQ del que aún no se sabe el precio es un tramo con el precio vacío.
- La **pantalla vive en la app vieja** (Thymeleaf, como `/catalogo` y `/colores`),
  no en la PWA de modelos, porque la pidió para «la app de Automatizaciones».

## Modelo de datos

Dos piezas nuevas, el **proveedor** y la **ficha de piel**, y un enlace opcional
desde la tabla `piel` que ya existe.

```
proveedor ──< ficha_piel ──< precio_piel
                   │    ──< foto_piel
                   │    ──< documento_piel
                   │    ──< evento_piel >── modelo (opcional)
                   │
   piel (cliente, ref_piel) ── ficha_piel_id (opcional)
       └──< articulo ── modelo ──< modelo_temporada
```

### Por qué un enlace y no ampliar `piel`

`piel` dice **cómo llama un cliente a una piel**. La ficha dice **qué piel es**.
Si los datos de la ficha fueran columnas de `piel`, la misma piel con dos clientes
tendría dos precios, dos calidades y dos juegos de fotos que acabarían diciendo
cosas distintas. Con el enlace:

- Lo que entra desde producción (cliente, referencia, nombre) sigue entrando igual,
  sin enlace: es la piel **incompleta**.
- Cuando alguien sabe qué piel de proveedor es, la enlaza y pasa a ser
  **completa**. Varias filas de `piel` (de clientes distintos) pueden apuntar a la
  misma ficha.
- Ninguna automatización cambia: el corte, el catálogo y `/colores` siguen leyendo
  `piel` como hasta ahora.

### Tablas

**`proveedor`**: `id`, `nombre` (única), `contacto`, `email`, `telefono`, `pais`,
`condiciones` (pago, Incoterm, portes; texto libre), `responsabilidad` (1-5),
`entrega_a_tiempo` (1-5), `nota_valoracion`, `fecha_valoracion`,
`fecha_actualizacion`.

**`ficha_piel`**: `id`, `proveedor_id`, `nombre` (único dentro del proveedor),
`ref_proveedor`, `animal`, `tipo`, `grueso_min_mm` y `grueso_max_mm`,
`superficie_min_m2` y `superficie_max_m2` (un valor fijo es mínimo = máximo),
`stock_service` (sí, no o nulo si no se sabe), `plazo_entrega_dias`,
`moq_por_color`, `fecha_precios`, `calidad` (1-5), `nota_calidad`,
`comentarios`, `origen` (`MANUAL`, `FICHA`,
`AGENTE`), `fecha_alta`, `fecha_actualizacion`.

**`precio_piel`**: `id`, `ficha_piel_id`, `moq_m2`, `precio_eur_m2` (puede ir
vacío). Los tramos de una ficha, ordenados por `moq_m2`; el más bajo es el MOQ de
la piel.

**`foto_piel`**: `id`, `ficha_piel_id`, `nombre_fichero`, `principal`, `orden`,
`bytes`. Reducidas a 1.600 px y con nombre único dentro de la ficha, como las
fotos del catálogo (`FotoCatalogo.nombreLibre`; HEIC por el conversor del corte).

**`documento_piel`**: `id`, `ficha_piel_id`, `nombre_fichero`, `tipo`, `bytes`,
`fecha`. Las fichas técnicas y ofertas en PDF. Se guardan aunque el agente aún no
exista: si un dato está mal, se puede ir al original.

**`evento_piel`**: `id`, `ficha_piel_id`, `fecha`, `cliente`, `tipo`, `modelo`
(texto libre, opcional: `PROTO B`, `ULL027`), `nota`. El recorrido de la piel.
Los tipos son `BUSCADA` (se buscó para ese cliente), `MOSTRADA` (se le enseñó o
propuso), `MUESTRA` (se mandó una muestra), `PROTOTIPO`, `SMS`, `PRODUCCION` y
`DESCARTADA` (con el motivo en la nota). El modelo es texto y no un enlace a la
app de modelos: al fusionar allí un prototipo con su referencia, un enlace se
perdería en silencio.

**`piel`** gana `ficha_piel_id bigint` nulo, con FK a `ficha_piel(id)` y `on delete
set null`. Al ser de una sola columna, el `set null` no tiene el problema de la FK
compuesta de `articulo`.

## El recorrido de una piel entre clientes

La ficha enseña una sola **línea de tiempo**, de más antiguo a más reciente, que
junta dos fuentes:

1. **Los eventos apuntados** (`evento_piel`): «2026-03-12 · APC · Mostrada»,
   «2026-05-02 · APC · Prototipo `PROTO B`».
2. **Lo que ya sabe el catálogo**, sin teclear nada: las filas de `piel` enlazadas
   a la ficha → sus artículos → su modelo (con su etapa) y sus temporadas. Salen
   como «AMI · `ULL027.AL0103` · Producción · H26».

Eso responde a la pregunta del usuario. De la segunda fuente sale «la usó AMI en
H26». De la primera sale «se le enseñó a APC el 12 de marzo y se hizo un prototipo
en mayo». Cuando ese prototipo entre por el pedido de APC con su referencia y se
enlace su `piel`, aparecerá también como uso de APC en la temporada que toque.

Del catálogo solo hay **temporada**, no fecha: `temporada_guardada` no guarda
cuándo empezó, solo cuándo se guardó por última vez. Por eso esos usos salen con
el nombre de la temporada y no con un día, y se ordenan por esa fecha de guardado,
que es aproximada.

Los eventos **se apuntan por un camino único** (un botón «+ evento» en la ficha,
con la fecha de hoy por defecto) y no se generan solos desde la app de modelos en
esta versión: un prototipo no dice con qué piel se hizo.

## La pantalla

`/biblioteca-pieles`, con su tarjeta «Biblioteca de pieles» en el menú.

- **Una tabla con todas**: cada ficha es una fila, con los chips de las
  referencias de cliente enlazadas (`AMI AL0103`, `APC PXCBC`). Debajo van las
  filas de `piel` sin ficha, marcadas «sin ficha», con dos acciones: **enlazar a
  una ficha existente** (buscador por nombre y proveedor) o **crear la ficha**
  (rellena el nombre). Si una piel sin ficha **se llama igual** que una ficha,
  la fila lo propone («¿Es ALPACA TOUCH de Curtidos X?») y se enlaza solo cuando
  el usuario lo acepta. Al enlazar una piel de cliente sin nombre, se le pone el
  de la ficha (rellenar un hueco, nunca pisar).
- **Filtros**: buscador (nombre, referencia de proveedor o de cliente), proveedor,
  cliente, animal, tipo, rango de grueso, stock service, y «solo sin ficha».
- **Edición celda a celda por `fetch`**, como `/catalogo` y `/colores`, para
  cambiar un dato sin abrir nada.
- **Ficha de una piel** (`/biblioteca-pieles/{id}`): todos los campos, los tramos
  de precio, la galería de fotos, los documentos, las referencias enlazadas y la
  línea de tiempo.
- **Proveedores** (`/biblioteca-pieles/proveedores`): la tabla de proveedores con
  su valoración, y la lista de sus pieles.

## Para que entrar datos sea rápido (ya en esta versión)

- **Lo único obligatorio es proveedor y nombre.** Todo lo demás es opcional, y una
  ficha a medias se guarda: la tabla marca lo que falta. Es la regla del proyecto
  de no bloquear por lo que se arregla después.
- **«Duplicar»** una ficha del mismo proveedor: las pieles de una curtiduría
  comparten casi todo (país, curtición, plazos, MOQ, certificaciones), y solo se
  cambia el nombre, el grueso y el precio.
- **«Buscada para»** en el alta: un desplegable de cliente que apunta el primer
  evento (`BUSCADA`) con la fecha de hoy, sin un segundo paso.
- **Proveedor nuevo sin salir de la ficha**: si el nombre tecleado no existe, se
  crea al guardar.
- **Soltar fotos y PDFs** encima de la ficha, como el campo de fotos del corte.

## Lo que deja preparado para el agente (siguiente fase)

El patrón será el de la conversión del excel del taller, que ya funciona: **Claude
lee, Java decide, la persona confirma.** Se suelta una ficha técnica. Claude
devuelve los campos con salida estructurada, eligiendo animal y tipo de las dos
listas cerradas. Java comprueba lo comprobable (unidades, rangos
mínimo ≤ máximo, proveedor existente). La pantalla enseña el formulario relleno
con las dudas en amarillo, y la persona guarda. Para eso el diseño ya trae:

- `origen` en la ficha, para saber qué entró por el agente;
- `documento_piel`, donde queda el PDF del que salieron los datos;
- todos los campos opcionales, para que una ficha técnica incompleta no falle;
- las dos listas cerradas, que irán en el prompt y en el esquema de la salida.

Otra entrada posible para más adelante: **dictar** la valoración de un proveedor
con la transcripción de notas de voz que ya existe en la app de modelos.

## Migración

`V10__biblioteca_pieles.sql`: crea las seis tablas y añade la columna nula
`piel.ficha_piel_id`. No mueve datos, así que no se pierde nada de lo guardado.
SQL del subconjunto común a H2 y PostgreSQL, como desde la V4, y binarios como
`bytea` sin `@Lob`.

## Fuera de esta versión

- El agente de fichas técnicas (siguiente fase, ver arriba).
- Histórico de precios y de valoraciones (el usuario pidió una nota que se
  corrige).
- Proponer el enlace solo: el excel de SMS de APC trae en `ARTICLE` el nombre de
  la piel del proveedor (`ALPACA TOUCH`) junto a la referencia del cliente, y
  podría sugerir el enlace al leerlo.
- Que los eventos salgan solos de la app de modelos.

## Límites conocidos de esta versión

De la revisión crítica del 2026-11-15. Lo que el usuario decidió está en la tabla
de decisiones. Tres propuestas del asistente no tuvieron respuesta y se aplican
**salvo que el usuario diga otra cosa**: quitar los campos que no pidió
(curtición, país, selección, colores, precio de muestra, certificaciones,
ubicación de la muestra: caben en «Comentarios») dejando solo un plazo de
entrega; el modelo de un evento como texto y no como enlace; y «Buscada para» en
el alta.

- **Los forros y las combinaciones no salen en el recorrido automático.** En el
  catálogo son texto libre, sin referencia de piel, así que un «serraje de
  forro» de la biblioteca no se puede enlazar a los modelos que lo llevan. Su
  recorrido son solo los eventos apuntados a mano.
- **El recorrido automático depende de que alguien acepte los enlaces** de cada
  piel de cliente con su ficha. Sin enlaces, solo se ven los eventos a mano.
- **Nombres escritos de dos formas** (`ALPACA TOUCH`, `ALPACA-TOUCH`) dan dos
  fichas. Se guardan en mayúsculas y sin espacios de sobra, y el alta sugiere las
  que ya existen del proveedor, pero no hay fusión de fichas.
- **Sin login**: precios y valoraciones de proveedores se ven desde cualquier
  equipo de la red local, incluida la tablet del taller.
- **`Cabra` está en animal y en tipo**, tal como dio las listas el usuario.
