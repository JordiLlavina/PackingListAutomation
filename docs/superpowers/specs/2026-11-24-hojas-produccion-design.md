# Hojas de Producción — diseño

**Fecha:** 2026-11-24 · **Tamaño:** proyecto (pantalla nueva, excel nuevo, tabla nueva)

## Qué y por qué

Un excel de producción para los talleres externos (Sweet Bag, C&C Leather, Antonio Muñiz, Adrian) que dice qué bolso hacer y con qué: modelo, unidades, piel base y su color, piel de combinación y su color, hilo, marcado interior y exterior, fornitura, forro y tinta. Hasta ahora se rellenaba a mano sobre un excel heredado (`docs/Hojas de Producción/Ejemplo Hoja de Producción.xlsx`), con los encabezados desordenados y celdas que no cabían. La feature lo sustituye por un formulario que genera el excel con la maquetación rehecha y guarda un histórico.

## Lo que decidió el usuario

- Entra en **Automatizaciones** en el sitio de la tarjeta «Escandallos para ClickUp»; la de ClickUp baja a la fila de abajo del menú.
- **Formulario** con todos los campos del excel, hecho con el skill de diseño de frontend.
- **Histórico** de hojas generadas.
- **Encabezados y tabla principal rehechos**: más ordenados, claros y modernos.
- **Numeración por taller**: cada taller lleva su contador y cada hoja nueva es la siguiente.
- **Marcado interior**: `MADE IN MOROCCO`, `MADE IN SPAIN`, `-`.
- **Marcado exterior** compuesto de **tres partes**: nombre del marcado (el cliente: AMI, APC… o `-`), color (`ORO`, `NIQUEL`, `ORO VIEJO`, `-`) y tamaño. Para AMI, tamaños 4, 4.5 y 5 cm; para APC, por defecto sin tamaño y aparte 2.5, 3, 3.5 y 4 cm. **Tres áreas de texto seguidas con tres desplegables debajo, del mismo ancho**: elegir en el desplegable escribe en el texto, y el texto siempre se puede corregir a mano.
- **Fornitura**: `ORO`, `NIQUEL`, `ORO VIEJO`, `-`. **Forro** y **tinta**: texto.
- **Hilo**: Gütermann o Serafil; ya no se escribe «G 181» ni «Sera 120».
- **Importar la información de los modelos existentes del cliente** (como la info de embalaje) e **importar hojas antiguas** al formulario.
- **Las celdas se ajustan al texto**: un texto largo cabe siempre, sin salirse de la celda.

## Enfoques considerados

1. **Rellenar la plantilla heredada con POI**, como los packing lists. Descartado: el usuario pide rehacer los encabezados y la tabla, así que la plantilla vieja no aporta nada; y las alturas de fila hay que calcularlas igual.
2. **Excel sin plantilla, dibujado por código** (elegido), como `OrdenCorteExcelBuilder` y `EscandallosExcelBuilder`. La maquetación son constantes y los tests reabren el `.xlsx` y comprueban celdas.
3. **Word** como la info de embalaje. Descartado: el taller trabaja con el excel y así lo pidió el usuario.

Para la pantalla:

1. **Dos pantallas con sesión**, como la info de embalaje (cargar cliente → formulario). Descartado: aquí no hay excel de pedido que leer, solo el catálogo, y la sesión complica importar antiguas.
2. **Una sola pantalla sin sesión** (elegido): el cliente es un desplegable del propio formulario y los artículos del catálogo se piden por `fetch` al cambiarlo. Importar una hoja antigua es abrir el formulario con `?importar=<id>`.

## Decisiones del asistente

- **Una hoja = un bolso con una o varias líneas.** El ejemplo real tiene una sola fila, pero la tabla admite varias (dos colores del mismo modelo, por ejemplo). El formulario arranca con una línea y tiene «+ Añadir línea» y «Duplicar».
- **Cabecera de la hoja**: cliente, temporada, nombre del bolso, taller, nº de hoja y fecha de salida. La fecha de salida se teclea (por defecto, hoy).
- **Talleres**: campo de texto con sugerencias: los cuatro que nombró el usuario más los ya usados en hojas guardadas. El contador compara el taller recortado y sin mayúsculas, como el de embalaje.
- **Hoja importada conserva su número** (mismo criterio que la info de embalaje: repetir o corregir una hoja). La pantalla dice cuál es el siguiente libre de su taller y un botón lo pone.
- **Tamaños del marcado exterior para los demás clientes**: la unión (2.5, 3, 3.5, 4, 4.5 y 5 cm), sin valor por defecto. El nombre del marcado arranca con el cliente de la hoja. Lo que va al excel es la unión de las tres partes separadas por espacio, saltando las vacías y los `-`; si no queda nada, `-`.
- **Hilo**: desplegable de marca (Gütermann, Serafil, otra) y número. En el excel: la marca en una línea y el número debajo, en negrita.
- **Importar del catálogo** por línea: un desplegable con los artículos del cliente (referencia · color — nombre del bolso) que rellena modelo, piel base (nombre de la piel, si lo tiene, o su referencia), color base, primera combinación y su color, forro y, si la cabecera no lo tiene, el nombre del bolso. Solo se lee el catálogo; **nunca se escribe en él**.
- **Se guarda el snapshot entero en JSON** (`DatosHojaProduccion`) y las columnas sueltas solo listan y filtran, igual que `hoja_embalaje`.
- **Alturas de fila calculadas**: texto con ajuste y la altura de la fila estimada con el ancho de la columna y la letra; no hay encogido de letra.
- **Excel**: apaisado, A4, ajustado al ancho de una página. Arial. Sin colores de marca: tinta, grises y rellenos claros, que es lo que se imprime bien.

Detalle para quien toque el código: `.claude/rules/hojas-produccion.md`.

## Segunda revisión (25 de noviembre de 2026)

Lo que pidió el usuario tras ver la primera versión:

- **Excel.** Los títulos de la cabecera (modelo, número y nombre del bolso) pasan del negro azulado a un gris un par de tonos más oscuro que el de los rótulos. (Primero se aclaró por error la cabecera de la tabla; el usuario pidió volver a su estilo: tinta con letra blanca.) Todas las filas de la tabla miden lo mismo, 110 pt. Sale la **foto del modelo, en grande**, en la cabecera. Desaparece la columna MODELO: **una hoja es un modelo** y el modelo va en el título, junto a la foto. «COMBINACIÓN» pasa a «PIEL COMBINACIÓN».
- **Formulario en una página**: cliente, temporada y artículo como en la info de embalaje, sin subir el excel de pedido. El desplegable de artículos es el mismo que el de embalaje y todos los desplegables de la pantalla toman su estilo.
- Fuera el texto «Lo propone el taller».
- Piel base y combinación con un desplegable de las **pieles enlazadas al bolso** y, tras elegir la piel, el desplegable de sus colores.
- **Marcado interior según el taller**: Adrian y Antonio Muñiz, MADE IN SPAIN; C&C Leather y Sweet Bag, MADE IN MOROCCO. El cliente pone el nombre del marcado. El primer color elegido de marcado o fornitura rellena el otro si está vacío.
- Los **cm del hilo**.
- Todo desplegable escribe en su texto, y toda carga automática deja el valor en el texto y en el desplegable (salvo las que solo llenan listas de pieles o colores).
- Lo que se carga solo **late con un borde coral**.
- «+ Añadir color», separado de las tarjetas. «Modelo y unidades» pasa a «Unidades»; el modelo va arriba con el nombre del bolso.
- **Apartado de foto** en el formulario: la del modelo o, si no tiene, una subida.

Decisiones del asistente en esta revisión:

- Los artículos salen del **catálogo** filtrado por los vínculos modelo–temporada (`modelo_temporada`); sin temporada, todo el catálogo del cliente. El modelo que se rellena es el **código de modelo** (F67104), no la referencia con la piel.
- Elegir el artículo rellena **el primer color**; los demás conservan lo suyo y reciben las pieles del modelo en sus desplegables.
- La **foto subida se guarda con la hoja** (V14, columna `foto`), no en el catálogo: esta pantalla solo lee el catálogo. Una hoja importada la hereda.
- Las filas miden 110 pt **o más** si un texto no cabe, todas a la vez: así sigue valiendo la regla de que nada se sale de la celda.
- Los cm del hilo van en la misma celda que la marca y el número, como tercera línea («3 CM»).
- El marcado del taller solo pisa un marcado interior vacío o el que puso el taller anterior.
