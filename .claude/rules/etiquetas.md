---
paths:
  - "src/**/packinglist/service/etiquetas/**"
  - "src/**/packinglist/service/etiquetasarticulo/**"
  - "src/**/packinglist/web/Etiquetas*"
  - "src/**/packinglist/web/AgrupadorAvisosEtiquetas*"
  - "src/main/resources/client-labels/**"
  - "src/main/resources/templates/etiquetas-*"
  - "src/main/resources/templates/resultados.html"
---

# Etiquetas de caja, de palet y de artículo (producción y SMS)

> Movido tal cual desde CLAUDE.md el 2026-11-24. Se carga solo al leer o editar un fichero de los `paths` de arriba. Se cruza con `packing-list.md` (palet 0, destinos padre, Livraison code) y `corte.md` (el excel de SMS).

**Etiquetas de caja** (`service/etiquetas/`): estrategia propia `GeneradorEtiquetasCliente` despachada por **clave de cliente** (no por TipoPlantilla); cada implementación declara qué destinaciones soporta y qué archivos necesita (`CampoEtiquetas`). **No hay paso intermedio**: se generan dentro del mismo `POST /generar` que los packing lists, y `CampoEtiquetas` ya no pinta inputs — le dice al controlador qué sacar de la sesión. Lo que no se pueda resolver sale como aviso en `/resultados` y deja el envío sin etiquetas, nunca sin packing lists. Implementado: AMI (China/Japan/France; hoja por destinación, par de etiquetas A4 por caja, más su hoja de palets) y APC (JAPAN, KOREA, D. USA, WHOLESALE y RETAIL, **una plantilla por destinación** y dos hojas por libro, cajas y palets; sin archivos extra, porque el pedido y el Livraison code ya vienen rellenos del packing list). `IVRY` sigue sin etiqueta: genera packing list y avisa. Plantillas en `src/main/resources/client-labels/` — misma regla que las de packing list: **no editarlas sin revisar su builder** (`AmiEtiquetasExcelBuilder`/`AmiEtiquetaLayout`, cuyas coordenadas ancla `AmiEtiquetaLayoutTest`; `ApcEtiquetasExcelBuilder`/`ApcEtiquetaLayout`, las de APC).

**Los ajustes del área de impresión viven en el código, no en la plantilla**: anchos de columna y altos de fila que el cliente pide después de ver el papel impreso (columna D a 19, columna E a 8 y las seis celdas de valor del pie alineadas a la izquierda en el packing list de APC; un alto de fila por destinación en las etiquetas de AMI, `AmiEtiquetaLayout.alturasFila`). **Las etiquetas de caja de APC son la excepción y van al revés**: ver más abajo. Editarlos a mano dentro del `.xlsx` funciona, pero es un cambio binario que no se ve en ningún diff y que la siguiente copia de la plantilla del cliente se lleva por delante en silencio. En AMI hay además un motivo de funcionamiento: los altos se aplican **antes** de capturar el bloque modelo (`corregirAlturasDeFila`), así que viajan en cada etiqueta copiada; hechos después solo valdrían para la primera caja del libro. Y la fila corregida es distinta en cada destinación porque **las tres hojas de AMI no están alineadas entre sí**: en CHINA todo va una fila más abajo que en JAPAN y FRANCE, así que la fila 8 de una no es la fila 8 de la otra. En JAPAN y FRANCE la fila tocada es la primera de la segunda etiqueta del par (el hueco entre las dos del A4); su homóloga de la primera etiqueta es la fila 0, que es el margen superior de la página y se deja como está. En el pie de APC la alineación importa porque la columna no está ni cuadrada consigo misma: `PALLETS` y `CARTONS` son texto y se quedan a la izquierda, mientras que los cuatro pesos y volúmenes son **números** y la alineación General de la plantilla los manda al borde derecho.

**Etiquetas de palet de AMI**: una hoja más (`Etiquetas Palets CHINA/JAPAN/FRANCE`) dentro del mismo libro de etiquetas de caja, con **dos etiquetas por A4** (media página cada una, salto de página cada dos palets). Sus coordenadas son constantes compartidas de `AmiEtiquetaLayout` (`FILA_PRIMER_PALET`, `ALTURA_BLOQUE_PALET`, `FILA_PALET_COLIS`, `FILA_PALET_PESO`) porque las tres hojas son idénticas salvo los textos fijos del destinatario. Tres cosas que no son obvias: el bloque **arranca en la fila 1**, no en la 0 (encima va un contador que el cliente apunta a mano, se blanquea y no se replica — por eso existe `BloqueEtiquetaModelo.capturar(hoja, filaInicio, altura)`); la plantilla trae **dos bloques de ejemplo** y lo que sobre tras el último palet **hay que borrarlo**, o la etiqueta de ejemplo del cliente se imprime y acaba pegada en un bulto; y los rótulos de la columna B son **bilingües y multilínea** (`EXPEDITEUR\n(Shipper / Sender)…`), así que buscarlos por texto exige comparar solo la primera línea.

**Número de palet en su etiqueta** (`NumeroPaletEtiqueta`, compartido por AMI y APC): cada etiqueta de palet lleva `Nº3` arriba a la derecha, a 11 puntos, alineado arriba y a la derecha; sustituye al contador que el cliente apuntaba a mano. Va en la **última columna del área de impresión** y en la **primera fila de cada bloque**: en AMI la D (`AmiEtiquetaLayout.COL_NUMERO_PALET`), en la fila del remitente, y **en cada etiqueta y no una vez por A4**, porque las dos del A4 acaban en palets distintos; en APC la fila 0 del bloque (la del contador, que se blanquea) y la columna `ApcEtiquetaLayout.Palet.colNumeroPalet`, que es la D salvo en WHOLESALE, donde es la **B** (esa hoja no tiene columna A). `elNumeroDePaletVaEnLaUltimaColumnaDelAreaDeImpresion` lo ancla contra las cinco plantillas.

**Quitar hojas del libro exige poner la primera pestaña visible a 0** (`setFirstVisibleTab(0)` en `dejarHojas` y en `quitarHojaPalets`). La plantilla de AMI guarda `firstSheet="1"` en el `workbookView`, y POI no lo corrige al borrar hojas. Con la hoja de palets en el libro el 1 existe, pero en una destinación **sin palets** (palet 0) queda una sola hoja y apunta a una que no existe. Entonces Excel da el libro por corrupto (por COM ni lo abre) y al repararlo dice «Parte quitada: /xl/drawings/drawing1.xml (Forma de dibujo)». **El mensaje engaña**: el dibujo es byte a byte el mismo que el del libro con palets, que abre bien. Se averiguó abriendo los dos con una instancia propia de Excel por COM y contando `Shapes` de la hoja: si Excel repara, salen 0. Ningún validador de esquema lo detecta. Lo ancla `laPrimeraPestanaVisibleExisteAunqueSeQuiteLaHojaDePalets`.

El campo palet es **opcional**, y basta con que **una** caja de la destinación no lo traiga para que ese excel salga **sin hoja de palets** y con aviso: una hoja a medias se imprime y se pega igual que una completa, y quien la mire asumirá que están todas. **APC hace ya lo mismo**: sin ninguna etiqueta que escribir se quita la hoja de palets del libro, en vez de conservarla con la etiqueta modelo de la plantilla en blanco (que se imprime y se pega igual que una buena). Las cajas de un palet de APC se cuentan además de **las cajas** y no del `cajaInicio..cajaFin` del `PaletData`, por el mismo motivo que el rango de AMI. Un peso que falta sí es distinto: solo deja en blanco su celda. El rango `Nº X à Nº Y` se lee de **las cajas**, no del `cajaInicio..cajaFin` del `PaletData`: ese es el del JSON original y se queda viejo en cuanto el usuario corrige un palet en la revisión. El peso es la suma de las cajas físicas más la tara (10 kg si el JSON no la trae), como en APC, pero se rotula `Kg` y no `KGS` porque es lo que pone la plantilla de palet.

**Una etiqueta de APC lleva TODOS los artículos de la caja**: `Order N°`, `Reference` y `Colour` se concatenan con `" / "` en el **mismo orden** (un artículo = referencia + color, en el orden del packing list), para poder leerlas en paralelo columna a columna. El pedido no se puede heredar de la línea líder como se hacía: en APC un `Document d'achat` es una destinación, un artículo **y un color**, así que una caja con cuatro artículos lleva cuatro pedidos distintos y quedarse con el del líder pone en la etiqueta un pedido que no es el de los demás. Un artículo sin pedido deja `NOT FOUND` **en su posición**, no un hueco. Lo que sigue siendo de un solo artículo (el líder) en una caja de cinturones son `SIZE` y `PIECES BY SIZE` —en una etiqueta no cabe más de un desglose por talla—, y de eso avisa `Mezcla de referencias/colores`. El `Livraison code` sí es único: es de la destinación entera.

**Una caja con material de varias destinaciones hijas** (WHOLESALE y AUSTRALIA en el mismo bulto, que solo pasa en entradas por JSON o fotos: la del taller nunca las mezcla): la línea `DESTINATION` las dice todas (`WHOLESALE / AUSTRALIA`, del `canal` de cada línea) y, como el pedido de APC es de la destinación, el **mismo artículo a dos hijas son dos artículos** (`ArticulosDeCaja.de(caja, false, true)`), cada uno con su `Order N°` en su posición. Dos cosas que no se deducen: con **una sola** destinación la línea se queda como la trae la plantilla, y solo se reescribe en **WHOLESALE y RETAIL** (`ApcEtiquetaLayout.filaDestino`, null en las demás), porque en JAPAN, KOREA y USA ese valor no es la destinación sino un aeropuerto o una ciudad (`TOKYO`, `JFK`) y no se toca nunca. **La etiqueta de palet sigue la misma regla** con las líneas de todas sus cajas (`ApcEtiquetaLayout.Palet.filaDestino`, la misma función `destinosSiVarios`), y aquí sí pasa también en la entrada por taller, porque las hijas comparten palet aunque no compartan caja. El palet no lleva `Order N°`: ninguna de las cinco plantillas de palet tiene ese campo.

**La fila de `Reference` de la plantilla de USA no es la de su rótulo**: en `apc-etiquetas-usa.xlsx` la celda de valor está **combinada** (`C12:C13`) y el rótulo cae en la fila de abajo, así que `ApcEtiquetaLayout.USA.filaReferencia()` es **11 y no 12**. Escribir en la fila del rótulo no da error ni celda vacía —Excel ignora lo escrito en una celda tapada por una combinación—: se queda a la vista la referencia de ejemplo de la plantilla, que es de otro artículo, y el fallo solo se ve abriendo el excel. `cadaFilaEsLaDeSuRotuloResolviendoLasCeldasCombinadas` lo ancla para las cinco plantillas, buscando cada rótulo en la columna B y resolviendo la combinación de su celda de valor.

**En las etiquetas de caja de APC la maquetación vive en la PLANTILLA, no en el código**, al revés que en todo lo demás: el reparto del par de etiquetas en el A4 se ajustó abriendo los cinco `.xlsx` y **borrando y redimensionando filas** (y en la hoja de palet de WHOLESALE, borrando la columna A), y eso no es expresable como «corregir un alto» — cambia las coordenadas de todos los campos. El flujo para volver a tocarlo son tres herramientas manuales de `src/test/.../etiquetas/`, en este orden: `ApcEtiquetasMuestrasTool` deja en `target/muestras-etiquetas-apc/` un libro por destinación con varias cajas y palets → se ajusta a mano en Excel **dejando solo el primer par** → `ApcEtiquetasLeerMuestrasTool` vuelca dónde ha quedado cada valor → `PreparadorPlantillasApcTool` convierte las muestras en las plantillas de `client-labels/` (recorta la hoja de palet a un bloque, borra los valores de prueba y los saltos de página). Después hay que **actualizar a mano las coordenadas de `ApcEtiquetaLayout`**; `ApcEtiquetaLayoutTest` las verifica buscando cada rótulo en la plantilla, así que un descuadre sale en rojo y no en el papel.

Consecuencias que no se ven leyendo el código:

- **Las cinco plantillas están maquetadas cada una por su lado y NADA se comparte**: ni `alturaBloque` (36, 36, 41, 37, 39), ni dónde cae cada campo, ni la geometría de la hoja de palet (que por eso es el record anidado `ApcEtiquetaLayout.Palet` y no las cuatro constantes compartidas que había).
- **RETAIL y WHOLESALE ya NO comparten coordenadas.** Siguen yendo al mismo almacén (Crosslog) y el cliente sigue habiendo partido el fichero solo para imprimir `RETAIL` o `WHOLESALE` en la línea `DESTINATION`, pero sus dos maquetaciones divergieron al repartir el A4: copiar las de `WH_CROSSLOG` en `RETAIL`, que antes era lo correcto, hoy escribiría cada valor una o dos filas por encima de su rótulo. Lo ancla `retailYWholesaleYaNoCompartenCoordenadasNiPlantilla`, y la aserción de `DESTINATION` en `ApcEtiquetasGeneradorTest` sigue siendo lo que distingue haber abierto un fichero u otro.
- **En la hoja de palet de WHOLESALE los valores van en la columna B**, no en la C: ahí no existe la columna A. Escribir en la C los dejaría fuera del recuadro **y fuera del área de impresión**, que en esa hoja acaba en la B.
- **`alturaBloque` no es `2 * offsetSegundaEtiqueta`**: la segunda etiqueta del par viene recortada por abajo, porque ahí acaba la página. Es correcto y el bloque se copia entero, recorte incluido.
- **El ajuste de página de la plantilla no se puede copiar tal cual.** Viene con «ajustar todas las filas en una página», que sobre el único bloque de la plantilla es justo lo que se quiere, pero sobre N cajas le pide a Excel meter **todas** las etiquetas en un solo A4 y de paso ignora los saltos de página. `ajustarImpresion` lo traduce a una escala fija calculada para que quepa **un** bloque, y estira el área de impresión hasta la última fila escrita conservando las columnas de la plantilla. La escala mira **alto y ancho**: en las hojas de cajas manda el alto, pero en las de palet la etiqueta sobra de alto y lo que se sale es el ancho, así que mirando solo el alto saldría al 100% y se partiría en dos hojas por el lado derecho. Ninguna de las cinco tiene encabezado ni pie, así que el alto útil es el papel menos los márgenes y no hace falta reservar nada más.

Cada etiqueta lleva **dos imágenes**, no tres: la **imagen compuesta** del artículo (los cuatro textos de la etiqueta de artículo más su EAN-13, generados en un solo PNG por `ImagenEtiquetaArticulo`, que solo habla del **primer** artículo de la caja) y el Code 128 del `EAN128` del excel de pedido. El Code 128 del PO **ya no existe**: el cliente lo quitó de su plantilla. `COLOR CODE` en la etiqueta es **solo el código** (`221`); el color completo (`221 DARK COFFEE`) va dentro de la imagen compuesta y en la hoja extra — `AmiPedidoExcel.FilaPedido` da los dos. `REFERENCE` y `ORDER NUMBER` son celdas combinadas en la plantilla nueva (ver más abajo por qué importa para `shrinkToFit`). La dirección de JAPAN se extrae **por su anclaje** en la plantilla, no cogiendo el primer PNG del libro: la plantilla trae dos PNG (el mock de la imagen compuesta y la propia dirección) y coger "el primero" sacaría el equivocado.

**El código de barras nunca se reescala**: se genera ya con la altura de barras que le toca (`CodigoBarrasEan13.png(ean13, proporcion)`) y se pega 1:1 con `Graphics2D.drawImage`; reescalarlo con interpolación lo deja bonito en pantalla e ilegible para un lector físico. La proporción barras:dígitos (5,5:1) sale medida del mock del cliente y deja las barras a ~42% de la altura nominal de un EAN-13 en la maqueta del cliente — **eso solo lo puede verificar un lector físico, ningún test lo cubre**. `CodigoBarrasEan13.png(String)` sin proporción sigue saliendo **byte a byte igual** que antes de esta feature, porque lo usan caminos ya en producción (hay un test que lo fija). La zona muda (`doQuietZone(true)`) es parte del símbolo, no un margen decorativo: sin ella muchos lectores no leen.

**Los dos EAN de las etiquetas de AMI** salen del excel de pedido con **clave exacta** `ARTICLE`+`COLORIS`+`TAILLE`+sufijo de PO (en el fichero real esa clave identifica una sola fila de 151; el color es obligatorio porque hay 59 claves con varios colores). Si no hay fila exacta: aviso y etiqueta sin esos códigos, nunca la fila de otro PO. La talla es la de la **línea líder** de la caja, porque en una caja de cinturones con varias tallas solo cabe un par de EAN. El `EAN128` se pasa a Code 128 **verbatim**, nunca compuesto: lleva dentro el EAN13 y el PO, y en el fichero real hay 8 filas donde eso no cuadra con sus propias columnas — se avisa y se imprime igual, porque es el código del cliente el que espera su escáner. `AmiPedidoRealTest` ancla todo esto contra el fichero real.

**Varios artículos en una caja** (`ArticulosDeCaja`): una caja física puede
llevar varios artículos y la etiqueta ya no los colapsa a la línea líder. Un
artículo es referencia+color en bolsos y referencia+color+**talla** en
cinturones (cada talla es un SKU con su propio EAN-13). En **bolsos** la
etiqueta concatena `REFERENCE`, `COLOR CODE` y `QUANTITY` con `" / "` en el
orden del packing list —`SIZE` sigue siendo `U`— y `AjusteFuente` encoge la
fuente si el texto no cabe (tamaño calculado con suelo de 8 pt **y**,
solo cuando el estilo original no tiene `wrapText` ni la celda está
combinada, `shrinkToFit` como red de seguridad adicional). **Excel ignora
`shrinkToFit` tanto en celdas con `wrapText` como en celdas combinadas.** En
las cuatro plantillas de **APC** el motivo es `wrapText`. En **AMI**, con la
plantilla nueva, la celda `REFERENCE` está **combinada**, así que ahí lo único
que protege el texto es el tamaño calculado de `AjusteFuente` — igual que en
APC, aunque por un motivo distinto. Los **cinturones no cambian de valor** en
`SIZE`/`QUANTITY`: `SIZE` se escribe sin pasar por `AjusteFuente` y nunca
encoge, pero `QUANTITY` sí pasa por él como cualquier caja, así que su fuente
puede encoger si el texto no cabe (`AjusteFuente` se aplica por celda, no por
tipo de caja).
Como en una etiqueta solo cabe un par de EAN, los artículos 2..N van a la hoja
`CODIGOS BARRAS EXTRA` (`HojaCodigosBarrasExtra`, sin plantilla propia, maquetación
en constantes) del mismo libro de la destinación, con un aviso al usuario de que
hay una hoja más que imprimir. Usa **la misma rejilla que las etiquetas de
artículo** (`RejillaEtiquetas`), 10 artículos por A4, con tres columnas por
bloque: caja, EAN-13 y EAN128. Solo AMI: **APC no imprime códigos de barras**,
solo hereda la concatenación.

`EtiquetaArticulo`, `RejillaEtiquetas` y `BloqueEtiquetaArticulo` viven en
`service/etiquetas/` (no en `service/etiquetasarticulo/`) y los comparten los
dos flujos, el de etiquetas de caja y el de etiquetas de artículo: **tocar uno
afecta a los dos**.

**Etiquetas de artículo** (`service/etiquetasarticulo/`): flujo **independiente
del envío** (`/etiquetas-articulo-produccion`), su única entrada es el excel de
pedido del cliente. `/etiquetas-articulo` es el **menú de la sección** (en
`MenuController`, que es una portada y no un paso del flujo): producción, SMS
(ver más abajo) y, sin implementar todavía, prototipos. `GeneradorEtiquetasArticuloCliente` es la interfaz, despachada por
clave de cliente; implementado AMI. Una hoja por fila del pedido (= por EAN13),
40 etiquetas idénticas por hoja en una rejilla 4×10 que cabe en un A4, y un
fichero por (tipo, Made in): bolsos MOROCCO, bolsos SPAIN, cinturones
(`UBL*`). **No hay plantilla `.xlsx`**: la maquetación son las constantes de
`RejillaEtiquetas` y `BloqueEtiquetaArticulo` (capa común, `service/etiquetas/`);
`EtiquetasArticuloExcelBuilder` es solo el orquestador. Todo eso está anclado
al fichero real del cliente por `EtiquetasArticuloMaquetacionTest` — POI no
copia el `pageSetup` al clonar hojas, así que heredarla de una plantilla no
servía.

**Etiquetas de artículo de SMS** (`/etiquetas-articulo-sms`, `EtiquetasSmsController`,
`EtiquetasSmsService`, `EtiquetasSmsDocBuilder`): de momento **solo APC**, los
demás clientes salen «en desarrollo». Entrada: cliente, temporada escrita a
mano (no se guarda nada) y el **excel de pedido de SMS** (`COMMANDE
SMS_FALL27_PUNTOTRES.xlsx`), leído con el mismo `ApcSmsExcel` que las hojas de
corte de SMS, así que lo que cuenta como muestra es lo mismo en los dos
documentos. Sale **un Word** (no un Excel) con **una etiqueta por muestra** de
«Commande SMS», seguidas en el orden del excel, y siete líneas por etiqueta,
decididas por el usuario: `PUNTOTRES`, la temporada, `SMS`, la referencia
entera, el nombre del modelo (`MODELE`), la piel (`ARTICLE`) y su color
(`COLORIS FOURNISSEURS`), todo en mayúsculas. La plantilla del usuario es
`docs/Etiquetas Artículos/ETIQUETAS PEQUEÑAS Template.doc` (en las de
prototipos la tercera línea será `TEST`). Lo que no se deduce del código:

- **La maquetación está copiada de esa plantilla, sin usarla**: hojas
  adhesivas de 65 etiquetas (5 × 13) por A4, casillas de 2160 × 1202 twips
  sin hueco entre ellas, Calibri 7 pt, líneas de alto fijo. El `.doc` es
  antiguo y mide el sangrado de la tabla **hasta el texto** de la primera
  casilla y no hasta su borde: la tabla empieza 96 twips (el margen de la
  casilla) antes de los 660 que dice la plantilla. Se comprobó exportando los
  dos documentos con Word a PDF y comparando dónde cae el texto.
- Una línea que no cabe **baja de letra** de medio en medio punto, hasta 4 pt
  (un bolso de dos pieles escribe las dos, `BOMBARDIER … + NOBILE`), y nunca
  se corta; si ni así cabe, se avisa. Un dato que falta deja **su línea en
  blanco** (no se corre la de debajo) y también se avisa. Ninguna de las dos
  cosas bloquea.
- `EtiquetasSmsServiceTest` deja `target/Etiquetas SMS APC FALL 27.docx`, el
  del pedido real (93 etiquetas, 2 hojas), y `EtiquetasSmsDocBuilderTest`,
  `target/Etiquetas SMS ejemplo.docx`.

Un aviso de etiquetas es un `AvisoEtiqueta` con las piezas **sueltas** (destinación, ámbito CAJA/PALET/DESTINO/FICHERO, número, hecho, consecuencia), no una frase montada, y lo comparten los dos generadores a propósito: **el formato de un aviso es una sola decisión, no una por cliente**. Van sueltas porque la pantalla necesita cada una por su lado —resalta la consecuencia, agrupa por destinación, compacta números y tiñe los de palet— y **la frase no es partible con seguridad**: unos separan hecho y consecuencia con `.`, otros con `;` y los de `FilaPedido.avisosEan` con `:`. `texto()` reconstruye la frase de siempre (juntando con `". "`) para quien solo quiera leerla; `ResultadoEtiquetas.getAvisos()` es esa vista derivada y de solo lectura, mientras que `getDetalle()` es la lista que se rellena. Sin vocabulario técnico —"la entrada", nunca "el JSON"—: quien los lee no sabe de qué formato salieron los datos.

**Los avisos de etiquetas se agrupan al pintarlos, no al generarlos** (`AgrupadorAvisosEtiquetas`, capa web): el generador emite uno **por caja**, que es la verdad del envío, pero leídos así son la misma frase repetida tantas veces como cajas y eso entierra los que sí hay que atender. En pantalla, cada destinación es un `<details>` con separación propia; a la vista quedan el recuento y la **consecuencia** en negrita ("3 cajas · etiqueta sin EAN13 ni EAN128") y el hecho largo que la causó se pliega. Tres reglas: el **resumen agrupa por consecuencia** y no por hecho (dos referencias que faltan son, en resumen, dos cajas sin EAN; el detalle sí las separa), cuenta **números distintos** —una caja con dos problemas suma en las dos líneas, pero el mismo problema repetido en ella no la cuenta dos veces, que pasa porque `resolver()` corre una vez por talla— y los **rangos no rellenan huecos** (`Cajas 1-2, 5`, nunca `1-5`), igual que en la tabla de revisión: una línea compactada nunca dice algo que no sea cierto de todas sus cajas. Es `<details>` nativo y no JavaScript porque en `/resultados` no hay formulario que se pueda pisar. El tinte propio de los avisos de palet y el margen entre destinaciones viven solo en el CSS, así que los ancla `RutasTest` leyendo el fichero.
