# Las hojas manuscritas del operario — análisis y catálogo de fallos

Qué son las hojas manuscritas del operario y dónde puede equivocarse la LLM al
leerlas. **La propuesta de prompt que traía este documento ya está
implementada**: el apartado 4 dice dónde vive ahora y qué se le añadió después.

El prompt vivo es el del código
([`ClaudeEnvioExtractionService`](../../src/main/java/com/puntotres/packinglist/service/ClaudeEnvioExtractionService.java):
`promptPara(plantilla)` = `PROMPT_NUCLEO` + `BLOQUE_<CLIENTE>`). Para
**copiarlo y pegarlo en claude.ai**, la copia buena es
[prompts-para-copiar.md](prompts-para-copiar.md), que un test mantiene idéntica
al código.

**Decisiones tomadas el 2026-08-06**, las tres ya implementadas:

- Estructura **núcleo + bloque por cliente** (apartado 4).
- La entrada real son **PDFs**, no fotos sueltas: `bloquesDe(...)` los manda
  como `DocumentBlockParam` + `Base64PdfSource`, y sigue aceptando imágenes.
- Los **palets de AMI importan de verdad**: desde las etiquetas de palet de
  AMI (spec 2026-08-05), una destinación con una sola caja sin palet sale SIN
  hoja de etiquetas de palet. La tabla caja→palet normalmente estará, en hoja
  aparte o al final de una hoja de packing, y puede faltar (se completa en la
  revisión).

---

## 1. Qué son de verdad las hojas de entrada

Analizadas las 9 páginas de `AMI PACKING IMAGENES.pdf` (3) y
`APC PACKING IMAGENES.pdf` (6).

**No son packing lists tabulados: son notas a mano del operario**, con una
taquigrafía propia y consistente. El prompt actual describe el JSON de salida
pero **no describe en ningún punto cómo se leen estas notas**, así que el
modelo tiene que inferir la gramática en cada extracción.

Anatomía típica de un bloque:

```
MOD  UBL029.AL0216.2221 chocolat        ← cabecera de artículo
   PARIS 07672:                          ← destinación + nº de pedido
   45/t75,  64/t85,  31/t95,  3/t105     ← unidades pedidas POR TALLA (suma de control)
Nº 1, 2 x 61:  122/75        13'52kg     ← cajas 1 y 2, 61 uds CADA UNA, peso por caja
Nº 15 - 83 x 5 sacs   345  (69 CAJAS)    ← RANGO de cajas 15..83, 5 uds cada una
   60x40x40                              ← medida de caja, vale para el grupo de arriba
```

### El vocabulario del operario

| Escribe | Significa |
|---|---|
| `Nº 1, 2 x 61` | cajas 1 y 2, **61 unidades cada una** (no 61 repartidas) |
| `Nº 15 - 83 x 5 sacs` | **rango** de cajas 15 a 83, 5 unidades cada una |
| `(69 CAJAS)` | recuento de control: 83−15+1 = 69 |
| `sacs` | unidades (bolsos/piezas) |
| `cajas` | cajas físicas |
| `157/75` | 157 unidades de la talla 75 |
| `45/t75`, `45/+75` | idéntico: la `t` es la **T de talla**, no parte del número |
| `13'52kg` | **13,52 kg** — el apóstrofo es la coma decimal |
| `TODO Nº 1` | todo lo listado encima va en **la caja 1** (caja mixta) |
| `(702)`, `(682)` | nº de pedido de APC, **solo sus tres últimos dígitos** |
| tachado | **no existe**: línea anulada |
| número encima de otro, o rodeado | corrección; manda el de encima / el rodeado |

### Diferencias reales entre clientes

|  | AMI | APC |
|---|---|---|
| Cabecera de artículo | `MOD ULL163.AL0052.221 DARK COFFEE` | `MOD 67043 SJ sac Le Neige CLOU CAMEL` |
| Referencia | los dos primeros grupos: `ULL163.AL0052` | el código tal cual: `67043`, `F63023` (**incompleto a propósito**) |
| Color | tercer grupo, **solo el código**: `221` | nombre + código: `CLOU CAMEL`, `LZZ Negro`, `KBE Olive` |
| Modelo | no se usa | sí: `sac Le Neige`, `Pochette Neige`, `Ceinture Rosette Antik` |
| Pedido | tras la destinación: `PARIS 07672:` | entre paréntesis, 3 dígitos: `Retail (705)`; a veces entero: `D. USA (4100128715)` |
| Tallas (cinturón) | 75, 85, 95, 105 (de 10 en 10) | 75, 80, 85, 90, 95, 100 (de 5 en 5) |
| Numeración de cajas | **continua** entre hojas de la misma destinación (hoja 1 acaba en 11, hoja 2 empieza en 12) | **reinicia en 1** en cada destinación |
| Pesos | sí, por caja | **ninguno en las 6 hojas** |
| Palets | tabla explícita `1 → 1-12`, `2 → 13-24`… | solo **recuento** (`wh. 5 palet`), sin rangos salvo Japan (`1-6`) |
| Una hoja por | referencia/color | destinación |

---

## 2. Los 13 fallos del prompt original contra estas hojas

Ordenados por gravedad. Los tres primeros rompen la importación o falsean cajas.

### 🔴 1. El apóstrofo decimal rompe el JSON

El operario escribe `13'52kg`, `12'820kg`, `9'93 Kg`. El prompt dice
*«Transcribe el número tal cual, sin la unidad»*. Obedeciéndolo el modelo emite
`"pesoBruto": 13'52` (JSON inválido) o `"13'52"` (Jackson no lo convierte a
`Double`). Las dos vías acaban en **«El JSON devuelto por Claude no es válido»**,
sin pista de la causa. Hay que exigir número JSON con punto: `13.52`.

### 🔴 2. Lo tachado se transcribe

En `APC Retail` hoja 1 hay un bloque entero tachado:
`MOD 67043 SJ sac Le Neige CLOU GRIS / Retail (703): 1 GRIS`. Nada en el prompt
dice que se ignore. Transcribirlo mete **una referencia y una caja fantasma** en
el packing list: un bulto que no existe.

### 🔴 3. `TODO Nº 1` no se entiende

En `APC Retail` hoja 1, cuatro referencias seguidas y debajo `TODO Nº 1 -
60x40x40`. Sin explicarlo, esas cuatro referencias **se quedan sin número de
caja** (se pierden) o el modelo se inventa cajas 1, 2, 3, 4. Lo correcto es
repetir la caja 1 en las cuatro: es una caja mixta.

### 🟠 4. La línea de totales puede convertirse en cajas

`157/75, 245/85, 164/95, 53/105` bajo la cabecera es la **cantidad pedida por
talla**, una suma de control. El prompt no la menciona. Es el origen natural de
`cantidadTotal`, pero nada impide que acabe en `cajas`.

### 🟠 5. La `t` de talla se cuela en la talla

`45/t75` → `"talla": "t75"`. Con eso la búsqueda en el excel de pedido de AMI
(clave `ARTICLE`+`COLORIS`+`TAILLE`+PO) falla y las etiquetas salen sin EAN. En
las hojas aparece indistintamente `/75`, `/t75` y `/+75`.

### 🟠 5b. La cabecera de artículo no tiene separadores (APC)

`MOD 67043 SJ sac Le Neige CLOU CAMEL` son tres campos escritos seguidos:
referencia (`67043`), modelo (`sac Le Neige`) y color (`CLOU CAMEL`). **Dónde
acaba el nombre del modelo y empieza el color solo se sabe conociendo el
catálogo de colores del cliente**, así que es la separación más difícil de toda
la hoja y ninguna regla la resuelve del todo. En AMI el problema es menor: los
puntos de la referencia marcan el corte.

**Arreglo por el lado del operario** (decidido 2026-08-07): una **barra `/`
entre los campos** de esa línea — `MOD 67043 SJ / sac Le Neige / CLOU CAMEL`.
Es un trazo por artículo y elimina la ambigüedad entera. Reglas para que no
cree fallos nuevos:

- **Solo en la línea de cabecera.** La barra ya significa otra cosa en el resto
  de la hoja: `122/75` son 122 unidades de la talla 75 y la tabla de palets usa
  `1 1-12 / 2 13-24`. El prompt lo acota explícitamente.
- **Orden fijo** referencia → modelo → color, y con dos trozos el segundo es el
  color: si no, `A / B` sería ambiguo.
- **La barra no es obligatoria.** Las hojas ya escritas no la llevan, así que el
  prompt la trata como autoritativa *cuando está* y cae a las reglas de cliente
  cuando no. Exigirla habría roto la extracción de todo lo anterior.
- En **AMI** el patrón de puntos sigue mandando sobre la barra: con
  `UBL029.AL0216.2221 / chocolat`, el color es `2221` (el código), no
  `chocolat`.

### 🟠 6. AMI: referencia y color van pegados

`UBL029.AL0216.2221 chocolat`. Sin instrucción, lo más probable es
`referencia: "UBL029.AL0216.2221"` + `color: "chocolat"`. Lo que espera el
sistema es `referencia: "UBL029.AL0216"` + `color: "2221"`. Rompe la búsqueda de
EAN igual que el punto anterior.

### 🟠 7. APC: el modelo intenta "arreglar" el pedido de 3 dígitos

`Retail (705)`. Un número de pedido de tres dígitos parece un error, y el modelo
puede rellenarlo o descartarlo. Hay que decir explícitamente que **son los tres
últimos dígitos a propósito** y que los complete `PedidoCompletionService` desde
el excel del cliente. Ojo: en `D. USA` el operario los escribe **enteros**
(`4100128715`); las dos formas valen porque la búsqueda usa los tres últimos.

### 🟠 8. Palets inventados

`wh. 5 palet` es un **recuento**, no un reparto. Si el modelo inventa rangos,
`PaletAssignmentService` asigna cajas a palets equivocados — y según
`CLAUDE.md`, la revisión **no vuelve a ejecutar la asignación**, así que el error
se queda pegado. Sin rangos explícitos, `palets` debe ir vacío.

### 🟠 9. Numeración de cajas: alcance distinto por cliente

APC reinicia en 1 por destinación; AMI es continua entre hojas. Un modelo que
"normalice" la numeración rompe la correspondencia con el número escrito
físicamente en el bulto.

### 🟡 10. `livraisonCode` ya no debe extraerse

El prompt lo sigue listando como campo opcional. Desde el cambio de destinos
padre, APC lo **genera internamente y el de entrada se ignora**. Pedirlo es
trabajo perdido y sugiere que el dato de la imagen importa.

### 🟡 11. La medida de caja flota

`60x40x40` aparece suelta debajo de un grupo de cajas, y en la hoja de Japan
incluso **en diagonal en el margen**. Hay que decir que aplica al grupo con el
que está agrupada.

Y hay grupos donde **no está escrita**. El prompt prohíbe entonces copiar la
del grupo de al lado o poner la habitual: se omite `medidaCaja` y se avisa. La
aplicación lo aguanta —la caja sale en el packing list sin sumar volumen, y la
revisión avisa de qué cajas hay que medir— porque una medida inventada acabaría
en el volumen declarado de un envío real. Antes de eso, la medida ausente
reventaba la generación con un `NullPointerException`.

### 🟡 12. No hay dónde poner las dudas

El operario marca lo dudoso: `60x40x20 ̶ o 60x40x40 !?`, `1 NEGRO. ??`. Hoy el
modelo solo puede elegir en silencio. Contradice la regla del proyecto de *nunca
fallar en silencio*.

---

## 3. Cambios de código que hicieron falta (todos implementados)

| # | Cambio | Por qué |
|---|---|---|
| A | **Prompt por cliente**: `extraer(imágenes, claveCliente)` y núcleo + bloque de cliente | **Decidido.** Lo que lee cada uno es muy distinto; un prompt único obliga al modelo a decidir qué reglas aplican y arrastra las de un cliente al otro |
| B | **Aceptar PDF** | **Obligatorio.** `mediaTypeDe(...)` hoy **lanza excepción** con `application/pdf`. El SDK ya trae `DocumentBlockParam` + `Base64PdfSource` (verificado en el jar 2.49.0). Sin esto, el flujo real no arranca |
| C | *(opcional)* campo `avisos` en `EnvioInput`, volcado a `EnvioImportado.getAvisos()` | Da salida a las dudas del punto 12 |
| D | **Casar la referencia de APC por SUFIJO y completarla** en `ApcPedidoExcel`/`PedidoCompletionService` | **Crítico para el flujo por imágenes.** El operario escribe `67043`, `F63023 SJ` o `63024`; el excel de pedido dice `PXCBS-F67043`. Hoy la clave es el Article **exacto**, así que la transcripción fiel garantiza «pedido no encontrado» en TODAS las líneas de APC. Con sufijo + los 3 dígitos del PO se encuentra la fila, se completa el pedido **y de paso la referencia entera** para el packing list; una clave ambigua (el mismo modelo en varios tipos: `PXBHZ-F65101` y `PXCBT-F65101`) se deja como llegó, con aviso, como siempre |

El cliente ya está validado antes de llamar al extractor
([`PackingListController:168`](../../src/main/java/com/puntotres/packinglist/web/PackingListController.java#L168)),
así que (A) es solo pasar el parámetro.

---

## 4. El prompt: dónde está y qué forma tiene

**Implementado.** El texto vivo es el del código, no el de este documento:
`promptPara(plantilla)` concatena el núcleo con el bloque del cliente.

| Pieza | Constante en `ClaudeEnvioExtractionService` | Qué aporta |
|---|---|---|
| Núcleo | `PROMPT_NUCLEO` | La taquigrafía del operario: las 14 reglas de lectura y la estructura exacta del JSON. Idéntico para los tres clientes |
| Bloque AMI | `BLOQUE_AMI` | Referencia partida por puntos, tallas de cinturón, numeración de cajas continua entre hojas |
| Bloque APC | `BLOQUE_APC` | Referencia y pedido incompletos a propósito, catálogo de destinaciones, numeración que reinicia |
| Bloque genérico | `BLOQUE_GENERICO` | Lo mínimo: referencia, color, medida y `modelo` si la hoja lo trae |
| Mensaje de usuario | `MENSAJE_USUARIO` | Va detrás de los documentos adjuntos |

**El texto literal no se copia en este documento, a propósito.** Su copia
oficial —la que se pega en claude.ai cuando la API falla— es
[prompts-para-copiar.md](prompts-para-copiar.md), **generada desde el código y
anclada por `PromptsParaCopiarTest`**: si las dos divergen, el test falla. Aquí
no hay ancla que valga, así que una tercera copia se quedaría vieja en
silencio — que es justo lo que le pasó a este apartado entre agosto y
septiembre de 2026, cuando llegó a describir un prompt de 12 reglas que el
código ya no mandaba.

### 4.1 Qué cambió respecto a la propuesta original

La propuesta de agosto se implementó casi entera. Lo que se le añadió después,
todo salido del uso real:

- **Regla 2, la barra `/` de la cabecera**: el operario puede separar
  `referencia / modelo / color` con barras, y donde estén, mandan. No existía
  en la propuesta; ver el fallo 🟠 5b.
- **Regla 8, medida de caja ausente**: la propuesta decía dónde buscarla, pero
  no qué hacer cuando no está en ninguna parte. Ahora el prompt manda **omitir**
  `medidaCaja` y avisar, en vez de copiar la del grupo vecino; ver el fallo
  🟡 11.
- **Regla 10, dos pesos en la misma línea**: gana el que lleva `kg`; si los dos
  o ninguno lo llevan, el mayor, y a `avisos`.
- **Regla 5, tachados**: de «un bloque» a «un bloque **o una línea**», y con la
  consecuencia explícita de que no genere ni referencia ni caja.
- **Bloque AMI**: el patrón `3 letras + 3 dígitos . 2 letras + 4 dígitos` se usa
  para autocorregir caligrafía (`O`/`0`, `L`/`C`, `I`/`1`), y se dice
  explícitamente que AMI no usa `modelo`.
- **Mensaje de usuario**: habla de «documentos», no de «\<N\> hojas», porque la
  entrada real acabó siendo PDFs.

---

## 5. Qué queda por decidir

1. ~~¿Prompt por cliente?~~ **Decidido: núcleo + bloque por cliente.**
2. ~~¿Campo `avisos`?~~ **Decidido: sí.** Da salida a las dudas del operario
   (`!?`, `??`) y llega a la revisión con el prefijo «Lectura de las hojas:».
3. **`cantidadTotal` cuando el operario corrige.** En la hoja 2 de AMI la
   cabecera dice `45/t75` pero la caja 12 acaba en 44 (corregido y rodeado). La
   propuesta transcribe el 45 de la cabecera, lo que **dispara un aviso de
   descuadre** en la revisión. Es lo que creo correcto —que lo vea un humano—,
   pero si prefieres que no avise, la alternativa es omitir `cantidadTotal`
   cuando hay corrección visible.
4. ~~PDF~~ **Decidido: obligatorio** (cambio B).
5. ~~¿Completar la referencia de APC por sufijo?~~ **Decidido: sí** (cambio D).
6. ~~Palet único inferido~~ **Decidido: NO.** Sin reparto escrito, `palets`
   vacío siempre; se completa en la revisión. El recuento declarado va a
   `resumenPalets` y sirve solo para validar.
7. **Validación del resumen (nueva, decidida):** si una hoja declara
   destinaciones o recuentos de palets, la aplicación comprueba que cada
   destinación declarada tiene hojas de packing y que el nº de palets
   extraídos coincide con el declarado. Si no cuadra: **error en la pantalla
   de entrada y NO se pasa a revisión** (el JSON extraído se vuelca al
   textarea para no perder el trabajo). Caso real que lo motiva: el resumen
   decía `wh. 5 palet` y el documento no traía las hojas de WHOLESALE.

---

## 6. Catálogo de puntos donde la LLM puede errar

Todos observados en las 9 hojas reales. Tres columnas de mitigación: qué hace
el **prompt** propuesto, qué puede hacer el **software** además, y qué podría
cambiar el **operario** al escribir (barato, sin cambiar su forma de trabajar).

### A. Caligrafía y correcciones

| # | Riesgo | Visto en | Prompt | Software | Operario |
|---|---|---|---|---|---|
| A1 | `O`/`0`, `L`/`C`, `I`/`1` en referencias: `AL0216` se lee `ACO216`, `ULL163` parece `OCC163` | AMI 1 y 3 | Declarar el patrón AMI (`3 letras + 3 dígitos . 2 letras + 4 dígitos`) para que el modelo se autocorrija | Validar la referencia extraída contra el excel de pedido al importar (si está subido); aviso si no existe | Referencias en mayúscula de imprenta, o copiadas del pedido impreso |
| A2 | Cifra corregida escribiendo encima (`13'72` sobre `12'72`) o rodeada (`44` sobre `45`) | AMI 1 y 2 | Regla 4: manda lo rodeado / lo de encima | Nada fiable; la revisión editable es la red | **Tachar entero y reescribir al lado**, nunca encima; rodear siempre el valor final |
| A3 | Símbolos sueltos y dudas del propio operario (`$`, `!?`, `??`, `F 13 y 14` en rotulador) | AMI 1 y 3, APC 4 y 6 | Regla 12: mejor lectura + línea en `avisos` | Campo `avisos` en el JSON (cambio C) que aflora en la revisión | Marcar dudas siempre con `?` y al margen, no sobre el dato |
| A4 | Anotaciones de un segundo bolígrafo/rotulador ignoradas o malinterpretadas | APC 1 (`1-6` en negro), AMI 3 | Regla 11b: también son datos (suelen ser la corrección final) | — | Mismo bolígrafo, o rotulador solo para correcciones definitivas |

### B. Gramática de las notas

| # | Riesgo | Visto en | Prompt | Software | Operario |
|---|---|---|---|---|---|
| B1 | `Nº 1, 2 x 61` leído como "61 unidades en total" en vez de "61 CADA UNA" | AMI 1 | Regla 1; el total tras los dos puntos (`122/75`) es control interno (2×61) | `cantidadTotal` vs suma de cajas ya se valida al importar → aviso de descuadre | Mantener la notación con el total detrás: es la redundancia que salva |
| B2 | `Nº 15 - 83` leído como "cajas 15 y 83" en vez de rango | AMI 3 | Regla 1; el `(69 CAJAS)` es el control (83−15+1) | El descuadre con `cantidadTotal` delata la pérdida de 67 cajas | Seguir apuntando el recuento entre paréntesis |
| B3 | `TODO Nº 1` no entendido: referencias sin caja o cajas inventadas 1..4 | APC 2 | Regla 6: caja mixta, repetir la caja 1 en todas | La revisión muestra la caja mixta; sin caja, la línea aflora como error | Escribir `Nº 1` delante de cada bloque también vale |
| B4 | El `⑥` rodeado al margen no se asocia a los DOS bloques que marca | APC 4 | Regla 11b | — | `TODO Nº 6` debajo, como hace en Retail |
| B5 | Bloque cortado a final de hoja y repetido entero en la siguiente → referencia duplicada | APC 2→3 (63024) | Regla 11: cuenta solo la versión completa | Deduplicar referencia+color+pedido con mismas cajas al importar → aviso | Tachar el fragmento cortado al reescribirlo |
| B6 | Línea de totales por talla (`157/75, 245/85…`) convertida en cajas | AMI 1-2, APC 2 y 6 | Regla 2: va a `cantidadTotal`, jamás a `cajas` | Una "caja" sin `Nº` delante no existe; el descuadre avisa | — |
| B7 | `t`/`+` pegado a la talla (`t75`, `+105`, `122/+/95`) acaba en `"talla": "t75"` | AMI 1-2, APC 6 | Regla 2: la talla es el número | Normalizar talla a dígitos al importar (quitar `t`/`+`) — barato y sin riesgo | Escribir siempre `/75` a secas |
| B8 | Numeración continua (AMI) aplicada a APC o viceversa | ambos | Bloques de cliente | — | — |

### C. Pesos

| # | Riesgo | Visto en | Prompt | Software | Operario |
|---|---|---|---|---|---|
| C1 | Apóstrofo decimal (`13'52kg`) → JSON inválido o string | AMI 1-2 | Regla 5: número JSON con punto | Red extra: normalizar `(\d)'(\d)` → `$1.$2` en `parsear()` antes de Jackson | Coma o punto en vez de apóstrofo — el cambio más barato de todos |
| C2 | Dos números de peso en la línea (`62/85 12'820 13'94kg`): ¿cuál es el bruto? | AMI 1 caja 6 | Añadir a regla 9: el bruto es el que lleva `kg`; si los dos o ninguno, el mayor + `avisos` | La inferencia deriva neto = bruto − tara; un neto negativo o absurdo → aviso | **Un solo peso por caja (el bruto)**; si apunta los dos, etiquetarlos (`B:`/`N:`) |
| C3 | Tres decimales (`12'820`) leído como 12820 | AMI 1 y 3 | Regla 5 con el ejemplo exacto (12'820 = 12.820) | Peso > ~50 kg por caja es imposible: validación de rango al importar → aviso | Dos decimales |
| C4 | Peso inventado donde no hay (APC no apunta ninguno) | APC 1-6 | Regla 9: omitir, nunca estimar | La inferencia ya rellena con taras; celdas en blanco si no puede | — |

### D. Identificación del artículo

| # | Riesgo | Visto en | Prompt | Software | Operario |
|---|---|---|---|---|---|
| D1 | AMI: referencia y color pegados (`UBL029.AL0216.2221 chocolat`) mal partidos | AMI 1-3 | Bloque AMI: 2 grupos = referencia, 3º = color, el nombre no va a ningún campo | Validar contra el pedido (A1) | — |
| D2 | APC: referencia parcial (`67043`, `F63023`, `63024`) que el modelo "arregla" o que no casa con el excel | APC 2-6 | Bloque APC: transcribir tal cual, no ampliar | **Cambio D: casar por sufijo + completar referencia y pedido.** Sin esto, todo APC por imágenes sale con «pedido no encontrado» | Copiar el código completo del pedido impreso (`PXCBS-F67043`), o al menos siempre con la letra |
| D3 | Siglas `SJ`/`SS` tras el código tomadas como parte de la referencia | APC 2, 4, 5 | Bloque APC: fuera de la referencia; duda → `avisos` | El sufijo-match (D2) tolera el residuo si se cuela | Omitirlas, o escribirlas tras el nombre del modelo |
| D4 | Pedido de 3 dígitos (`(705)`) rellenado/inventado por parecer incompleto | APC 2-5 | Bloque APC: son los 3 últimos a propósito | `PedidoCompletionService` ya lo completa; `sufijo()` tolera el entero (`4100128715`) | — |
| D5 | El `(682)` repetido en líneas de caja leído como cantidad | APC 4 | Bloque APC: es el mismo pedido | — | — |
| D6 | Color ES/FR inconsistente (`Negro`/`Noir`, `CLOU CAMEL` vs código) | APC 2-6 | Transcribir tal cual | Al casar la fila del pedido (D2), el color oficial sale de la fila: contrastar → aviso si difiere | — |

### E. Palets

| # | Riesgo | Visto en | Prompt | Software | Operario |
|---|---|---|---|---|---|
| E1 | Recuento (`wh. 5 palet`) convertido en rangos inventados | APC 1 | Regla 10: recuento ≠ reparto; vacío antes que inventar | La revisión permite teclear palets caja a caja; con AMI, sin palets no sale hoja de etiquetas (a propósito) | **La mejora más rentable: escribir SIEMPRE la tabla caja→palet** como la de AMI hoja 3, también en APC |
| E2 | Tabla de palets al final de la última hoja asociada solo a esa referencia | AMI 3 | Regla 10: vale para la destinación entera | — | — |
| E3 | Hoja resumen de palets al PRINCIPIO del documento, antes de las destinaciones | APC 1 | Regla 10 + bloque APC | — | — |
| E4 | Tachones/anotaciones dentro de la tabla (`6: 61-7̶2̶ 72`, `F 13 y 14`) | AMI 3 | Reglas 4 y 12 | — | A2/A3 |

### F. Destinaciones

| # | Riesgo | Visto en | Prompt | Software | Operario |
|---|---|---|---|---|---|
| F1 | Abreviaturas (`wh.`, `D. USA`) no mapeadas al catálogo | APC 1 y 6 | Bloque APC: tabla de mapeo explícita | Destino fuera de catálogo ya avisa y no bloquea; hijas → `ResolutorDestinosPadre` | Nombre completo en la cabecera de cada hoja (ya casi siempre) |
| F2 | `PARIS` en AMI tomado por destino desconocido | AMI 1-3 | Transcribir tal cual | **Ya resuelto**: `AmiNombreFichero` y `AmiEtiquetasGenerador` mapean PARIS → FR/FRANCE | — |
| F3 | Cliente deducido de las hojas distinto del seleccionado | ambos | `cliente` opcional | El controlador ya avisa sin bloquear si difieren | — |

### Los cambios de escritura del operario que más rinden

Son las reglas de oro de
[guia-operario-packing-manuscrito.md](guia-operario-packing-manuscrito.md), que
es el documento que se le da a quien escribe las hojas (hay una versión `.docx`
al lado, para imprimir).

1. **La barra `/` en la cabecera del artículo**
   (`MOD 67043 SJ / sac Le Neige / CLOU CAMEL`). Es el único hábito nuevo que
   se pide y quita de golpe la separación más difícil de la hoja; ver el
   fallo 🟠 5b.
2. **Tabla caja→palet siempre**, por destinación (como AMI hoja 3). Sin ella,
   AMI se queda sin etiquetas de palet y APC obliga a teclear el reparto a mano.
3. **Un solo peso por caja, con coma decimal** (`13,52`), y si apunta dos,
   etiquetados (`B:`/`N:`).
4. **Referencia APC con su código completo** del pedido impreso (o mínimo con
   la letra: `F67043`), y las correcciones tachando y reescribiendo al lado,
   con el valor final rodeado.
5. **La medida de caja en todos los grupos**: sin ella la caja no suma volumen
   y hay que elegir el tamaño a mano en la revisión; ver el fallo 🟡 11.
