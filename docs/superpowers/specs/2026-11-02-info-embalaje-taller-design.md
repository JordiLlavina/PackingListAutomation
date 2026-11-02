# Etiqueta Info de Embalaje para Taller — diseño

Fecha: 2026-11-02. Estado: aprobado por el usuario en conversación («Impleméntalo»); las decisiones marcadas **(decisión de Claude)** son calls del asistente que el usuario no ha rechazado y puede cambiar.

## Qué es y para qué

Cada vez que Puntotres manda al taller (Tánger, Fez) las etiquetas de un modelo, van dentro de un **sobre grande** y encima del sobre va pegada una **hoja A4** que le dice al taller cómo empaquetar ese modelo: qué caja, cuántos bolsos por caja, qué bolsa de tela, si lleva etiqueta de cartón interior, a qué destinaciones va y con qué número de pedido, y cualquier comentario. Hoy esa hoja se escribe a mano en Word, con la información desordenada y repetida (fotos en `docs/Info Embalaje Taller/`).

La feature genera esa hoja en **Word A4** desde la aplicación, con los datos que ya tenemos (excel de pedido del cliente, catálogo de modelos) y los que teclea el usuario, la **guarda** para poder volver a ella y lleva, si hay vídeo, un **QR** con la guía de embalaje.

Un sobre puede llevar etiquetas de **varias destinaciones**: el taller las separa allí. Por eso la hoja lista todas las destinaciones del artículo.

## Goles, no-goles

- Gol: una pantalla donde en un minuto se saca la hoja de un artículo, con lo que ya se sabe prerrellenado.
- Gol: historial de hojas con «importar antiguos» para repetir o corregir una.
- Gol: comentarios tipo guardados, elegibles de un desplegable.
- No-gol (de momento): **calcular el reparto en cajas**. Lo escribe la persona a mano en un campo de texto libre. Se automatizará después.
- No-gol: alojar el vídeo. La app solo guarda el enlace. Recomendación al usuario: YouTube «no listado» o Google Drive con «cualquier persona con el enlace».
- Anti-gol: inventar datos. Lo que no está en el pedido ni en el catálogo se deja en blanco y se teclea.

## Flujo de pantallas

Sección nueva del menú: tarjeta **«Etiqueta Info de Embalaje para Taller»** → `/info-embalaje`.

1. **Entrada** (`GET /info-embalaje`): cliente + temporada guardada (desplegable con lápiz a `/temporadas`) o excel de pedido subido a mano, exactamente como `/etiquetas-articulo-produccion` (misma plantilla de JS, `ArchivoTemporadas.paraElDesplegable()` y `conPedidoParaElEnvio`). Solo AMI y APC tienen lector de pedido; los demás clientes salen «en desarrollo» y deshabilitados. Botón **«Importar antiguos»** → `/info-embalaje/antiguos`. `POST /info-embalaje/cargar` lee el pedido, resuelve los artículos y los deja en sesión (`EmbalajeEnCurso`); si el excel no se deja leer, vuelve con error.
2. **Formulario** (`GET /info-embalaje/formulario`): una sola pantalla con todo. Un desplegable **Artículo** (referencia + color + nombre) rellena por JavaScript el resto con lo que viene de sesión en JSON; todos los campos quedan editables. Al generar (`POST /info-embalaje/generar`), se **guarda la hoja** y se devuelve el `.docx` como descarga sin recargar (patrón del Packing Puntotres: `fetch` + blob); la pantalla enseña «Guardada como envío N».
3. **Antiguos** (`GET /info-embalaje/antiguos?id=`): lista a la izquierda (fecha, temporada, nº envío, referencia + color) y panel centro-derecha con el resumen de la elegida (todos los campos) y dos botones: **Descargar Word** (`GET /info-embalaje/antiguos/{id}/docx`, se regenera del snapshot) e **Importar** (`POST /info-embalaje/importar?id=`), que carga el snapshot en sesión como valores de partida —y el pedido de su temporada si sigue guardada— y lleva al formulario. Sin temporada, el desplegable de artículo solo tiene el de la hoja importada.
4. **Comentarios de embalaje** (`GET /comentarios-embalaje`): pantalla gemela de `/temporadas` para mantener comentarios tipo (nombre corto + texto). Lápiz desde el formulario y panel en el menú.

## Campos de la hoja

Grupo | Campo | De dónde sale | Editable
---|---|---|---
Cabecera | Cliente, temporada | entrada | no (vienen de la carga)
Cabecera | Taller | texto; `<datalist>` con los talleres usados antes | sí
Envío | Nº envío | **max(número de envío guardado de ese taller) + 1**, 1 si el taller no tiene ninguna; **un contador por taller** (Tánger, Fez, Ubrique…), comparando el taller recortado y sin mayúsculas. **Decisión del usuario** (noviembre de 2026), que sustituye al contador único que se había propuesto. Lo propone la pantalla al teclear el taller | sí
Envío | Nota ERP | texto, lo teclea el usuario | sí
Envío | Fecha | hoy | no
Artículo | Referencia, código y descripción de color | pedido (AMI: `COLORIS` + `Libellé coloris`; APC: color del `sku`/`Couleurs` + descripción de `color_cliente`) | elección en el desplegable
Artículo | Nombre del bolso | APC `Désignation`; si no, `modelo.nombre` del catálogo | sí
Artículo | Foto | foto **principal** del modelo en el catálogo (`foto_modelo`), si hay | no (se gestiona en `/catalogo`)
Artículo | Total unidades | suma de las destinaciones (se recalcula al editar) | derivado
Embalaje | Caja (medida) | `articulo.medida_caja` | sí
Embalaje | Unidades por caja | `articulo.unidades_por_caja` | sí
Embalaje | Bolsa de tela | última hoja guardada del mismo cliente + modelo | sí
Embalaje | Etiqueta de cartón interior | texto; vacío = «NO LLEVA ETIQUETA DE CARTÓN INTERIOR», con texto = «LLEVA ETIQUETA DE CARTÓN INTERIOR <texto>» (`PARIS`); última hoja del modelo | sí
Embalaje | Instrucción especial | texto (`GRABADO LOGO NIKEL`), destacado en la hoja; última hoja del modelo | sí
Destinaciones | Filas destino · nº pedido · cantidad | pedido (ver abajo); filas añadibles y borrables | sí
Reparto en cajas | Texto libre multilínea | vacío | sí
Comentarios | Texto libre multilínea; desplegable de comentarios guardados que **añade** el texto elegido al final (se pueden combinar varios) | — | sí
QR | Enlace al vídeo | opcional; con enlace sale el QR | sí

### Destinaciones desde el pedido

- **AMI** (`AmiPedidoExcel`): para la referencia + color, `comandasDe(referencia, color, talla)` por cada talla de `catalogo()` de ese artículo; se agrupan por PO sumando tallas (la hoja no lleva EAN, así que aquí sí se suman). Destino = `ReglasTallerProperties.clienteTaller("AMI").destinoDeSufijo(poSufijo)` (como `ObjetivosPedidoAmi`); un sufijo sin traducción deja el destino como el sufijo en mayúsculas y un aviso. Nº pedido = `poNumerico` + espacio + sufijo (`09012 PRESS`) tal como lo escribe el cliente; sin sufijo, el número solo.
- **APC** (`ApcPedidoExcel`): las líneas de `catalogo()` con esa referencia y color dan los `Document d'achat` distintos; `comandaDe(pedido)` da destino (`Notre référence`: `Japan`, `Douanes USA`…) y cantidad. Destino en mayúsculas.
- Orden de las filas: el del pedido.

## Persistencia

Migración **`V2__info_embalaje.sql`** (regla del proyecto: la V1 no se toca).

```sql
create table hoja_embalaje (
    id bigint generated by default as identity,
    cliente varchar(60) not null,
    temporada varchar(60) not null,
    temporada_id bigint,                 -- sin FK: si la temporada se borra, la hoja se queda
    numero_envio integer not null,
    nota varchar(60),
    taller varchar(60),
    referencia varchar(80) not null,
    codigo_color varchar(200) not null,
    codigo_modelo varchar(80),           -- para «última hoja del modelo» y la foto
    fecha timestamp(6) not null,
    datos clob not null,                 -- JSON de DatosHojaEmbalaje: lo que se imprimió
    primary key (id)
);

create table comentario_embalaje (
    id bigint generated by default as identity,
    nombre varchar(100) not null,
    texto varchar(4000) not null,
    fecha_actualizacion timestamp(6) not null,
    primary key (id),
    constraint uk_comentario_embalaje_nombre unique (nombre)
);
```

- `datos` guarda **el snapshot entero** (`DatosHojaEmbalaje`, serializado con Jackson) porque dentro de un año el excel de la temporada o la memoria del artículo pueden haber cambiado y «antiguos» tiene que enseñar lo que de verdad se mandó. Las columnas sueltas son las que se listan y filtran. **La foto no va en el JSON**: se vuelve a buscar en el catálogo por `codigo_modelo` al regenerar.
- `codigo_modelo` se obtiene con la regla del cliente (`DocumentosCorteService.reglasDeProduccion(cliente).partir(referencia)`), como hace el catálogo.
- Bolsa de tela, etiqueta interior e instrucción especial **no entran en el catálogo** **(decisión de Claude)**: se prerrellenan de la última `hoja_embalaje` del mismo cliente + `codigo_modelo`. Así no se toca el esquema del catálogo ni su pantalla; si algún día se quieren editar en `/catalogo`, se mueven.
- La memoria de artículo (`articulo.medida_caja`, `unidades_por_caja`) **solo se lee**. Lo que se teclea aquí no la escribe: esa memoria la escribe el packing del taller al generar, y pisarla desde una hoja informativa cambiaría el packing siguiente sin que nadie lo viera.

## El documento Word

`InfoEmbalajeDocBuilder` (`service/embalaje/`), Apache POI XWPF, sin plantilla: la maquetación son constantes de la clase, como `PackingPuntotresDocBuilder`. A4 vertical, márgenes 1,5 cm, Calibri. Diseño: moderno, aire entre secciones, fácil de leer en un sobre a un metro.

Orden y aspecto (de arriba abajo):

1. **Banda de cabecera**: tabla de dos columnas sin bordes. Izquierda: «INSTRUCCIONES DE EMBALAJE» (22 pt, negrita), debajo en gris `Cliente · Temporada · Taller`. Derecha, **solo si hay enlace**: recuadro con borde fino (1,5 pt, tinta) titulado **«Guide d'emballage du sac»** (9 pt, negrita, mayúsculas pequeñas) y el QR a 3,5 cm. El QR lo genera ZXing (`CodigoQr.png(texto, pixeles)`), se pega 1:1.
2. **Tres fichas de envío** en fila (tabla de 3 celdas con fondo `F2F4F6`, sin bordes): `Nº ENVÍO` / `NOTA ICSUITE` / `FECHA`, rótulo 8 pt gris mayúsculas, valor 20 pt negrita.
3. **Artículo**: tabla de dos columnas. Izquierda, la foto (5 cm de ancho, proporción conservada; sin foto, celda vacía). Derecha: referencia (26 pt, negrita), color `código descripción` (14 pt), nombre del bolso (12 pt, mayúsculas, gris), y **TOTAL N UNIDADES** (16 pt, negrita).
4. **Sección «Embalaje»**: título de sección (11 pt, negrita, mayúsculas, con filete naranja `FF8900` de 1,5 pt debajo —el naranja de la marca—). Lista rótulo/valor en tabla de dos columnas sin bordes: Caja, Unidades por caja, Bolsa de tela, Etiqueta cartón interior (la frase de arriba), Instrucción especial (**valor en naranja y negrita, 13 pt**; la fila no sale si está vacía).
5. **Sección «Destinaciones»**: tabla con cabecera gris (`Destinación · Nº pedido · Cantidad`), filetes horizontales finos `D4DAE1`, cantidad alineada a la derecha, fila final **Total** en negrita.
6. **Sección «Reparto en cajas»**: el texto libre, una línea por párrafo, 11 pt. No sale si está vacío.
7. **Sección «Comentarios»**: recuadro con fondo `FFF7E0` y borde `E6C34A` (los colores de aviso de la app), texto 12 pt negrita. No sale si está vacío.

Todo tiene que caber en **un A4** con 6 destinaciones, 8 líneas de reparto y 4 de comentarios; si no cabe, Word sigue en otra página, no se recorta nada.

Nombre del fichero: `Info embalaje <cliente> <nº envío> <referencia> <color>.docx`, saneado con el patrón de `EtiquetasArticuloController.CARACTERES_INSEGUROS`.

## Código

- `service/embalaje/`: `DatosHojaEmbalaje` (record + `DestinacionHoja(destino, pedido, cantidad)`), `ArticuloEmbalaje` (lo que el formulario ofrece por artículo: referencia, codigoColor, descripcionColor, nombreBolso, codigoModelo, fotoId, medidaCaja, unidadesPorCaja, bolsaTela, etiquetaInterior, instruccionEspecial, destinaciones), `PedidoEmbalajeService` (pedido + catálogo + última hoja → `List<ArticuloEmbalaje>`, con avisos), `InfoEmbalajeDocBuilder`, `CodigoQr`.
- `persistence/`: `HojaEmbalaje` (entidad), `HojaEmbalajeRepository`, `ArchivoHojasEmbalaje` (guardar, `todas()` resumen, `conDatos(id)`, `siguienteNumeroEnvio()`, `ultimaDe(cliente, codigoModelo)`, `talleres()`), `ComentarioEmbalaje`, `ComentarioEmbalajeRepository`, `ArchivoComentariosEmbalaje`.
- `web/`: `InfoEmbalajeController`, `EmbalajeEnCurso` (`@SessionScope`: cliente, temporada, temporadaId, artículos, valores de partida de una importación), `ComentariosEmbalajeController`; plantillas `info-embalaje.html`, `info-embalaje-formulario.html`, `info-embalaje-antiguos.html`, `comentarios-embalaje.html`; tarjeta y panel en `menu.html`.
- `pom.xml`: `com.google.zxing:core` y `javase` 3.5.3.

## Tests

- `CodigoQrTest`: el PNG se decodifica con ZXing y devuelve el mismo texto.
- `ArchivoHojasEmbalajeTest` (`@SpringBootTest`): guardar, siguiente número (1 sin hojas; max+1), última del modelo, snapshot ida y vuelta.
- `PedidoEmbalajeServiceTest` contra los excels reales: APC Spring 27 `PXCBC-F67008` + `LZZ` → cuatro destinaciones con los pedidos `4100132915` (JAPAN), `4100132811` (DOUANES USA), `4100132847` (WHOLESALE), `4100132835` (RETAIL) y nombre `SAC LE NEIGE…`; AMI SS27 `USL745.AL0245` + `709` → PO `09012`, destino PARIS, descripción `VANILLE`. Las cantidades se leen del fichero, no se fijan a mano (las fotos dicen 40/243/110/127 y 73; el test comprueba que son las del excel).
- `InfoEmbalajeDocBuilderTest`: reabre el `.docx` con POI, comprueba textos, que el QR está solo con enlace, que la instrucción especial y los comentarios no salen vacíos, y deja `target/Info embalaje ejemplo.docx` para abrirlo en Word.
- `InfoEmbalajeControllerTest` (MockMvc): cargar con el excel real → formulario con el JSON de artículos → generar devuelve `.docx` y guarda la hoja → antiguos la lista → importar prerrellena → siguiente número de envío sube.
- `ComentariosEmbalajeControllerTest`: alta, edición, borrado, nombre repetido rechazado.
- `RutasTest`: la tarjeta y el panel del menú enlazan a las rutas nuevas.
