# Carga de modelos en batería — diseño

Fecha: 23 de noviembre de 2026. Estado: aprobado por el usuario.

## Para qué

Hoy los modelos entran al catálogo de tres maneras, y ninguna deja revisar
lo que entra: solos al guardar una temporada en `/temporadas` (sin revisión,
todos en Producción), de uno en uno en la app de modelos, o celda a celda en
`/catalogo`. El usuario quiere **una sola puerta de entrada revisada**: elegir
de dónde salen los datos, ver cada modelo con lo que se ha podido cargar,
completar lo que falta, elegir sus fotos, y aceptar o cancelar uno a uno
antes de que nada llegue al sistema.

## Objetivos

1. Un panel pequeño nuevo en el menú, **debajo de «Guarda Temporadas y
   Ficheros»**: «Carga de modelos». Las tarjetas grandes del menú se hacen
   más altas para que la columna de paneles admita cinco.
2. Pantalla 1, **origen de la información** (`/carga-modelos`).
3. Pantalla 2, **Carga de Modelos en Batería** (`/carga-modelos/bateria`).
4. `/temporadas` pasa a guardar **solo los ficheros** de la temporada.

## Lo que NO entra

- No se toca la app de React (`/app`): su alta de modelos, su ficha y su
  selector de imágenes de PDF siguen como están.
- No se lee el excel de SMS de AMI (no hay lector): AMI sale «en desarrollo»
  en ese origen, como en las hojas de corte de SMS.
- No hay IA: nada lee una ficha técnica para proponer campos.
- No se borra ni se cambia nada de lo que ya hay en el catálogo. La carga
  **solo añade y rellena huecos**.
- No hay login: es una pantalla de la aplicación vieja, abierta en la red
  local como las demás.

## Lo que haría de esto un fracaso

- Que un modelo aceptado pise algo que alguien tecleó (un nombre de piel, un
  forro, la etapa de un modelo que ya existía).
- Que recargar la página o tardar una hora pierda lo tecleado o las fotos.
- Que entre al catálogo una imagen de una ficha técnica que nadie eligió
  (logos, muestras de color).
- Que «Cargar modelos al Sistema» deje el lote a medias sin decirlo.

## Decisiones del usuario

- **`/temporadas` solo guarda ficheros**: se quita la alta automática de
  modelos al guardar (`incorporarPedido` / `incorporarProduccion` desde el
  controlador), se quita la subida de fotos de esa pantalla y su emergente de
  resultado. Los excels de la temporada siguen guardándose igual, con las
  mismas reglas de obligatorio por cliente.
- **Cinco orígenes**, como botones en la parte de arriba; al pulsar uno
  aparece debajo su zona de selección:
  1. *Excel de temporada* (AMI y APC): se elige una temporada **ya guardada**
     del cliente; se lee su excel de pedido.
  2. *Excel de SMS* (AMI y APC; de momento solo APC tiene lector): se sube el
     excel de pedido de SMS.
  3. *Excel de Producción de Puntotres* (todos los clientes): una temporada
     guardada que lo tenga, o uno subido a mano (el subido manda).
  4. *Añadir en grupo*: se sube una carpeta de carpetas (o su zip).
  5. *Añadir modelo*: no pide nada; va directo a la batería con un modelo en
     blanco.
- **El cliente elegido se conserva** al cambiar de origen, salvo que sea un
  cliente estándar (genérico) y se pase a un origen solo de AMI y APC: ahí
  pasa al primero que lo admite.
- En los tres orígenes con excel, **después del documento se puede subir la
  carpeta de carpetas de modelos**, con el mismo campo que hoy tiene
  `/temporadas` (`campoFotos`: carpeta o zip, pinchar o soltar).
- **Botón de esqueleto** (icono de esquema tipo árbol genealógico) en los
  tres orígenes con excel: descarga un zip con una carpeta raíz llamada como
  la temporada y, dentro, una carpeta vacía por cada modelo del excel. En
  «Añadir en grupo» **no hay esqueleto**: la carpeta la hace el usuario.
- **En «Añadir en grupo» los modelos salen de la propia carpeta**: cada
  subcarpeta es un modelo, y **el nombre de la carpeta raíz es el nombre de
  la temporada**.
- Dentro de la carpeta de un modelo, **un PDF es una ficha técnica** del
  modelo y **una imagen es una foto** del modelo.
- **Fotos**: las subidas como foto salen **seleccionadas**; las que salen de
  una ficha técnica salen **sin seleccionar** y se eligen en una **ventana
  emergente**, como en la app. Todas se pueden seleccionar y deseleccionar,
  y las seleccionadas se **ordenan arrastrando**; la primera por la izquierda
  es la principal.
- Tras cargar el origen, el botón **«Ir a Cargar Modelos»** lleva a la
  batería.
- **Batería**: lista de modelos a la izquierda, panel del modelo a la
  derecha, con una sección **Obligatorio** arriba y **Opcional** debajo.
  No es un formulario de arriba abajo: los campos se agrupan y reparten en
  rejilla. **Todos los campos son editables**, vengan del origen o no.
- En la lista: **verde** el modelo aceptado; **rojo** el cancelado; **gris y
  al final** el que ya existe; un **iconito de foto tachada** en el que no
  tiene ninguna foto seleccionada.
- **«Aceptar Modelo»** solo se enciende con los obligatorios completos; lo
  marca en verde y abre el siguiente por completar. **«Cancelar Modelo»** lo
  marca en rojo y no se cargará.
- **«Cargar modelos al Sistema»**, siempre encima de los dos paneles: carga
  los que están en verde. Si quedan modelos sin aceptar ni cancelar, sale un
  aviso informando.
- **«Añadir modelo»** entra a la batería con la lista vacía y un panel por
  rellenar. **«Añadir otro»** deja el anterior en la lista (accesible) y abre
  un panel en blanco con el mismo cliente.

## Decisiones del asistente (corregibles)

- **Una línea de la lista es un modelo**, no una referencia. Sus pieles
  (artículos: `ULL027.AL0103`, `ULL027.AL0216`) van dentro del panel.
- **Obligatorios**: cliente; referencia del modelo, o referencia de prototipo
  + temporada (la regla de `Modelos.darDeAlta`); nombre del modelo; tipo
  (bolso / cinturón); etapa. Tipo sale puesto por el prefijo (`UBL`) y etapa
  por el origen: temporada y producción → Producción; SMS → SMS; grupo y
  manual → Prototipo.
- **Opcionales**: temporada (salvo en un prototipo sin referencia); por cada
  piel, nombre de la piel, forro, hasta cuatro combinaciones y, por color del
  bolso, color de piel, de forro y de combinaciones; cartón, unidades por
  caja y peso neto; fotos; fichas técnicas.
- **«Ya existe»**: gris y al final solo el modelo que existe y **no trae
  nada nuevo** (ni pieles, ni colores, ni fotos, ni fichas). El que existe y
  trae algo nuevo va con los demás, rotulado («ya existe · 2 pieles
  nuevas»). Uno gris se puede abrir, editar y aceptar igualmente. En un
  modelo que existe, el panel enseña lo guardado y lo nuevo, y al aceptar
  **solo se rellenan huecos y se añade lo nuevo**; la etapa guardada no
  cambia.
- **En «Añadir en grupo»**, si el nombre de la carpeta raíz coincide (sin
  mayúsculas ni espacios de sobra) con una temporada guardada del cliente,
  los modelos se vinculan a ella; si no, se avisa y quedan sin temporada (se
  puede elegir una en el panel). No se crea ninguna temporada desde aquí:
  una temporada necesita su excel. El nombre de cada subcarpeta se lee como
  en el corte: la primera palabra, partida con la regla del cliente
  (`ULL027.AL103 candy` → modelo `ULL027`).
- **Carpeta con un origen de excel**: las subcarpetas se casan con los
  modelos del excel igual; una subcarpeta que no casa con ninguno se avisa y
  no crea modelo (para eso está «Añadir en grupo»).
- **Aplicación vieja, no React**: Thymeleaf + JavaScript propio, como
  `/catalogo`. El lote vive en la **sesión** (`CargaModelosEnCurso`), con
  fotos, PDFs y candidatas en un directorio temporal propio (prefijo
  `carga-modelos-`, limpiado como los del corte); cada cambio del panel se
  guarda por `fetch` con JSON, así que recargar no pierde nada. La página
  manda un latido para que la sesión no caduque mientras se trabaja.
- **Las candidatas de un PDF tienen que durar lo que dure el lote**: las de
  `ImagenesDePdf` caducan a los 30 minutos, así que aquí se guardan en el
  directorio del lote (o se vuelven a extraer del PDF guardado).
- **Cargar es todo o nada**: una sola transacción con todos los verdes. Si
  uno falla, no entra ninguno, se dice cuál y por qué, y el lote sigue en
  pantalla. Al acabar bien: resumen (modelos nuevos, completados, pieles,
  colores, fotos, fichas) y el lote se queda solo con lo no cargado
  (pendientes y cancelados), para poder seguir.
- **El aviso de pendientes no bloquea**: se cargan los verdes y los demás
  siguen en la lista.
- **Conversión de fotos** (HEIC, reducción a 1.600 px): en segundo plano al
  subir, como en el corte; «Cargar modelos al Sistema» espera apagado hasta
  que acaban.
- **Una ficha técnica aceptada se guarda como documento del modelo**
  (`documento_modelo`), que es donde las ve la app.
- El **nombre de piel** de una referencia de piel que ya tiene nombre se
  enseña y, si se cambia aquí, **sí se guarda** (lo ha tecleado una persona
  en esta pantalla); lo que venía del excel solo rellena huecos.

### Añadidas tras la revisión del diseño

- **Qué se escribe en un modelo que ya existe**: lo que viene del origen y
  nadie ha tocado **solo rellena huecos**; lo que una persona **cambia en el
  panel** (nombre, tipo, forro, cartón, un color, el nombre de piel…) **se
  guarda y pisa** lo que había, porque «todos los campos son editables». El
  panel recuerda qué campos se han tocado. La etapa de un modelo que existe
  solo cambia si se cambia en el panel, y entonces pasa por
  `Modelos.cambiarEtapa` (deja su apunte). Lo guardado desde aquí **no se
  propaga** a las pieles hermanas: cada piel está a la vista en el panel.
- **Temporada con un excel subido a mano** (SMS, o producción sin temporada
  guardada): un desplegable con las temporadas guardadas del cliente, y
  «sin temporada». Con una elegida, los modelos se vinculan a ella y el
  esqueleto lleva su nombre; sin ella no se vinculan y la raíz del esqueleto
  se llama como el excel.
- **Un prototipo sin referencia del cliente no tiene pieles ni embalaje**:
  esas secciones del panel aparecen cuando hay referencia (los artículos se
  guardan por referencia). Sí tiene fotos y fichas técnicas.
- **Fotos de un modelo que ya tiene fotos**: las guardadas salen en la tira,
  delante, fijas como seleccionadas (aquí no se borra nada) y **se pueden
  reordenar** con las nuevas: el orden es lo que el usuario hace a mano, y
  al cargar se manda el orden completo (`FotosCatalogo.ordenar`). Sin tocar
  el orden, las nuevas van detrás.
- **Vincular a la temporada cuenta como «algo nuevo»**: un modelo que existe
  pero no está vinculado a la temporada del origen no sale gris («ya existe ·
  se vincula a H26»).
- **«Aceptar todos los completos»**, junto a «Cargar modelos al Sistema»:
  marca en verde de una vez los pendientes con los obligatorios completos,
  para no aceptar uno a uno cincuenta modelos que el excel ya trae enteros.
- **Orden al cargar un modelo nuevo**: primero `Modelos.darDeAlta` (deja el
  histórico de nombre y de etapa) y después sus artículos;
  `asegurarArticulo` solo, lo crearía en Producción y sin histórico.
- Comprobado en el código: `darDeAlta` y `Documentos.guardar` admiten autor
  nulo; todo lo que se llama se une a una transacción exterior (usar
  `Documentos.guardar`, no `subir`); `incorporarPedido`,
  `incorporarProduccion` e `ImportacionFotosTemporada` solo los llama
  `TemporadasController`, así que la importación de fotos de temporada queda
  sin uso y se borra; `LectorFotos` hay que ampliarlo (hoy trata los PDF como
  «no son fotos», pide los modelos de antemano y no da el nombre de la raíz).

### Decidido por el usuario tras la revisión

- **El nombre del modelo es obligatorio siempre**, también en AMI, cuyo
  pedido no lo trae: ningún modelo entra sin nombre, aunque haya que
  teclearlos la primera vez. (Un modelo que ya existe y tiene nombre lo
  trae puesto.)

## Qué da cada origen

| Origen | Modelos y pieles | Nombre del bolso | Nombre de piel | Colores | Forro | Imagen |
|---|---|---|---|---|---|---|
| Excel de temporada (pedido) AMI | sí | no | no | código + descripción | no | no |
| Excel de temporada (pedido) APC | sí | sí (`Désignation`) | no | código | no | no |
| Excel de SMS APC | sí | sí (`MODELE`) | sí (`ARTICLE`) | código + color de piel | sí | croquis, como foto seleccionada |
| Excel de producción | sí | sí | sí (columna PIEL) | sí, y color de cada piel | no | no |
| Añadir en grupo | carpeta = modelo | no | no | no | no | fotos y PDFs |
| Añadir modelo | a mano | a mano | a mano | a mano | a mano | subidas en el panel |

Los lectores son los que ya existen: `ClienteCorte.pedidoEntero`,
`ApcSmsExcel`, `ProduccionPuntotresExcel`, `LectorFotos`.

## Enfoques considerados

1. **Pantallas de la aplicación vieja con el lote en sesión** (elegido).
   Encaja con el menú de automatizaciones, sin login, y reutiliza lectores,
   campo de fotos y conversor. Hay que rehacer en JavaScript el selector de
   imágenes de PDF y el arrastre de fotos.
2. **Pantallas nuevas en la app de React.** Reutilizaría `ElegirImagenesPdf`
   y `FotosOrdenables`, pero pide entrar con usuario, queda fuera del menú y
   mete en esa app lectores de excel que no son suyos.
3. **Borrador en la base de datos** (tablas de lote). Sobrevive a un
   reinicio, pero es una migración y un ciclo de vida más para algo que se
   hace en una sentada; la sesión con directorio temporal ya es el patrón
   del corte.

## Lo que se deja a quien lo construya

- Nombres de clases y rutas exactas; el reparto en rejilla del panel
  (aplicando la skill de diseño de interfaz); textos de avisos.
- Cómo reutilizar `Modelos.darDeAlta`, `CatalogoModelos`, `FotosCatalogo` y
  `Documentos` sin usuario de sesión (hay que comprobar qué columnas de
  autor admiten nulo y, si alguna no, resolverlo sin inventar un usuario).
- El formato del esqueleto y de los nombres de carpeta, siguiendo lo que ya
  reconoce `LectorFotos`.
- Tests: servicio del lote con JUnit puro donde se pueda, MockMvc para las
  rutas, y los lectores contra los excels reales de `ejemplos/`. Los tests
  de `/temporadas` que anclan la alta automática y las fotos se reescriben.
- Actualizar `CLAUDE.md` (secciones de temporadas y catálogo, y una nueva).
