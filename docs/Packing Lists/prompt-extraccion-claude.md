# Prompt de extracción por imágenes — análisis y propuesta

Dónde está hoy: [`ClaudeEnvioExtractionService.PROMPT_SISTEMA`](../../src/main/java/com/puntotres/packinglist/service/ClaudeEnvioExtractionService.java)
(constante `PROMPT_SISTEMA`, y el mensaje de usuario al final de `extraer(...)`).

Este documento es **una propuesta para validar**, no está implementado.

**Decisiones ya tomadas** (2026-08-06):

- Estructura **núcleo + bloque por cliente** (apartado 4): aprobada.
- La entrada real serán **PDFs**, no fotos sueltas. Hoy `mediaTypeDe(...)`
  rechaza `application/pdf`; el SDK 2.49.0 ya trae `DocumentBlockParam` +
  `Base64PdfSource`, así que aceptar PDF es un cambio pequeño y **obligatorio**
  para este flujo.
- Los **palets de AMI ya importan de verdad**: desde las etiquetas de palet de
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

## 2. Los 12 fallos del prompt actual contra estas hojas

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

### 🟡 12. No hay dónde poner las dudas

El operario marca lo dudoso: `60x40x20 ̶ o 60x40x40 !?`, `1 NEGRO. ??`. Hoy el
modelo solo puede elegir en silencio. Contradice la regla del proyecto de *nunca
fallar en silencio*.

---

## 3. Cambios de código que hacen falta

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

## 4. Prompt propuesto

### 4.1 Núcleo (común a los tres clientes)

```
Transcribes packing lists escritos A MANO por el operario de almacén de un
fabricante de marroquinería (Punto Tres). La entrada son escaneos de hojas
manuscritas: son notas en taquigrafía, NO tablas. Devuelves EXCLUSIVAMENTE un
JSON válido, sin markdown, sin comentarios y sin texto antes o después.

## Cómo se leen estas hojas

Cada hoja lista, para una destinación, qué referencias se han empaquetado y en
qué caja física ha ido cada una. Anatomía típica de un bloque:

  MOD <referencia> <color>             cabecera de artículo
  <destinación> (<pedido>): <total>    destinación, nº de pedido y total pedido
  <n>/<talla>, <n>/<talla>, ...        unidades pedidas por talla (SUMA DE CONTROL)
  Nº 1, 2 x 61: 122/75    13'52kg      cajas 1 y 2, 61 uds cada una, peso por caja
  Nº 15 - 83 x 5 sacs  345 (69 CAJAS)  RANGO de cajas 15..83, 5 uds cada una
  60x40x40                             medida de caja del grupo de arriba

## Reglas de lectura

1. "Nº" introduce SIEMPRE números de caja, nunca cantidades.
   - "Nº 1, 2 x 61" = cajas 1 y 2, 61 unidades CADA UNA (no 61 repartidas).
   - "Nº 15 - 83 x 5" = rango de las cajas 15 a 83, 5 unidades cada una.
   - "x" significa "cada una contiene". "sacs" = unidades. "cajas" = cajas.
   - "(69 CAJAS)" es un recuento de control: 83-15+1 = 69.

2. La línea "<n>/<talla>, <n>/<talla>, ..." que va justo debajo de la cabecera
   del artículo son las UNIDADES PEDIDAS POR TALLA. NO es una caja y no genera
   nunca entradas de "cajas": va a "cantidadTotal", una entrada de "referencias"
   por talla. Una "t" o un "+" delante de la talla son la T de talla:
   "45/t75" son 45 unidades de la talla 75, la talla es "75", nunca "t75".

3. Copia "cantidadTotal" de esa línea tal como está escrita. NO la recalcules
   sumando las cajas y NO cuadres las dos si difieren: un descuadre es una
   discrepancia real que el operario tiene que ver.

4. Lo TACHADO no existe: si un bloque está cruzado por una raya, omítelo
   entero. Si una cifra está escrita encima de otra, o rodeada con un círculo,
   vale la de encima / la rodeada: son la corrección final del operario.

5. Los decimales se escriben con apóstrofo o coma: "13'52kg" son 13,52 y
   "12'820kg" son 12,820. En el JSON emite SIEMPRE un número JSON con punto
   decimal: 13.52. Nunca 13'52, nunca una cadena, nunca la unidad.

6. "TODO Nº <n>" (o "todo caja <n>") significa que TODAS las referencias
   listadas encima van en esa única caja: repite ese número de caja en todas
   ellas. Es una caja mixta y es correcto.

7. Las medidas de caja ("60x40x40") se escriben una vez para un grupo de cajas,
   a veces debajo y a veces de lado en el margen. Aplícalas a las cajas de su
   grupo, en "medidaCaja", como LxWxH en cm.

8. Los números de caja son los del operario y van escritos físicamente en el
   bulto. Transcríbelos EXACTAMENTE: no renumeres, no cierres huecos y no
   fusiones repetidos. Un mismo número de caja bajo varias referencias es una
   caja mixta y es correcto.

9. "pesoBruto" (kg) es el peso de la CAJA FÍSICA ENTERA y es OPCIONAL.
   - En un rango es el peso de CADA UNA de sus cajas.
   - En una caja mixta (mismo nº de caja en varias entradas) va UNA SOLA VEZ,
     en la primera entrada. Nunca lo repartas ni lo repitas.
   - Si la hoja no da peso, omite el campo. No lo estimes nunca.

10. Lo normal es que las hojas digan QUÉ CAJAS van en cada palet con una tabla
    tipo "1  1-12 / 2  13-24": eso es lo que esperas encontrar y lo que va a
    "palets". La tabla puede venir en una hoja aparte (incluso la primera del
    documento) o al final de la última hoja de la destinación, y vale para la
    DESTINACIÓN ENTERA, todas sus referencias, no solo para la hoja donde está
    escrita. Si NO hay reparto escrito, deja "palets" vacío — se completa en
    la pantalla de revisión. Un simple RECUENTO ("5 palets") NUNCA basta para
    inventar rangos: un palet inventado acaba impreso en una etiqueta pegada a
    un bulto real. El recuento va a "resumenPalets" (ver estructura), que la
    aplicación usa para validar la extracción.

11. Las hojas van numeradas (a menudo con el número rodeado arriba a la
    derecha). Léelas en orden: un bloque puede continuar en la hoja siguiente,
    y entonces es UN solo bloque, no dos. Si un bloque quedó CORTADO al final
    de una hoja y la siguiente lo repite entero, cuenta solo la versión
    completa: no dupliques la referencia.

11b. Un número rodeado en el MARGEN IZQUIERDO de uno o varios bloques es el
    número de caja de todos ellos (equivale a "TODO Nº <n>"). Las anotaciones
    en otro bolígrafo o rotulador también son datos: suelen ser correcciones o
    aclaraciones posteriores.

12. Todo lo que no puedas leer con seguridad, y todo lo que el operario haya
    marcado con "?" o "!?", va igualmente en el campo con tu mejor lectura Y
    además como una línea de "avisos" diciendo qué es dudoso y dónde. Nunca
    dejes un campo mal en silencio.

## Estructura exacta del JSON

{
  "cliente": "<clave del cliente si aparece; si no, omítelo>",
  "avisos": ["<dudas y descuadres, en español>"],
  "resumenPalets": [
    { "destino": "<destinación>", "palets": 5 }
  ],
  "destinos": [
    {
      "destino": "<nombre de la destinación>",
      "palets": [ { "palet": 1, "cajaInicio": 1, "cajaFin": 12 } ],
      "referencias": [
        {
          "referencia": "...",
          "color": "...",
          "medidaCaja": "60x40x40",
          "pedido": "...",
          "talla": "<solo si el artículo tiene talla>",
          "modelo": "<solo si el cliente lo usa>",
          "cantidadTotal": 150,
          "cajas": [
            { "cajaInicio": 1, "cajaFin": 3, "unidadesPorCaja": 50, "pesoBruto": 18.5 },
            { "caja": 4, "unidades": 45, "pesoBruto": 16.2 }
          ]
        }
      ]
    }
  ]
}

Cada entrada de "cajas" tiene UNA de las dos formas: caja suelta
{"caja": N, "unidades": U} o rango {"cajaInicio": A, "cajaFin": B,
"unidadesPorCaja": U}. Un rango implica que TODAS esas cajas llevan las mismas
unidades; si no, usa cajas sueltas.

Una referencia con varias tallas o colores son varias entradas de
"referencias", una por combinación, cada una con su "cantidadTotal".

Copia referencias, colores y pedidos EXACTAMENTE como aparecen, respetando
puntos, ceros a la izquierda y mayúsculas.

"resumenPalets" recoge los RECUENTOS de palets por destinación cuando alguna
hoja los declara ("Japan -> 1 palet", "wh. 5 palet"): una entrada por
destinación mencionada, aunque no tengas su reparto de cajas. Si ninguna hoja
da recuentos, omite el campo. La aplicación lo usa para comprobar que no
falta ninguna hoja.
```

### 4.2 Bloque AMI

```
## Este envío es de AMI

- Cabecera de artículo: "MOD ULL163.AL0052.221 DARK COFFEE". Sepárala así:
    "referencia" = los DOS primeros grupos de puntos -> "ULL163.AL0052"
    "color"      = el TERCER grupo, solo el código numérico -> "221"
  Las palabras finales ("DARK COFFEE") son el nombre del color: NO van a
  ningún campo. Prefijos: ULL = bolso, USL = cartera, UBL = cinturón.

- Los cinturones (UBL) llevan "talla" (75, 85, 95, 105): una entrada de
  "referencias" por talla, cada una con su "cantidadTotal". Los bolsos y
  carteras no llevan talla: omite el campo.

- La destinación y el pedido van juntos: "PARIS 07672:" es destino PARIS y
  pedido "07672". Respeta los ceros a la izquierda.

- Los números de caja son CONTINUOS entre las hojas y las referencias de una
  misma destinación (si la hoja 1 acaba en la caja 11, la hoja 2 empieza en la
  12 aunque cambie de artículo). No reinicies en 1 por hoja ni por referencia.

- La tabla caja→palet ("1  1-12 / 2  13-24 ...") suele venir al final de la
  última hoja de la destinación, y cubre TODAS sus cajas, de todas las
  referencias. Transcríbela entera en "palets". Puede llevar anotaciones
  encima (tachones, letras en rotulador): apúntalas en "avisos".

- NO rellenes "modelo", "canal" ni "livraisonCode": AMI no los usa.
```

### 4.3 Bloque APC

```
## Este envío es de APC

- Cabecera de artículo: "MOD 67043 SJ sac Le Neige CLOU CAMEL". Sepárala así:
    "referencia" = el código tal cual está escrito -> "67043", "F63023", "63024"
                   Está INCOMPLETO a propósito: NO lo amplíes ni le añadas
                   prefijos, lo completa la aplicación. Las siglas sueltas
                   detrás del código ("SJ", "SS") NO son parte de la
                   referencia: déjalas fuera y, si dudas de qué son, una
                   línea en "avisos".
    "modelo"     = el nombre descriptivo -> "sac Le Neige", "Pochette Neige",
                   "Ceinture Rosette Antik"
    "color"      = el color como está, con su código si lo lleva ->
                   "CLOU CAMEL", "LZZ Negro", "KBE Olive"

- Debajo va "<destinación> (<pedido>): <total> <color>", p. ej.
  "Retail (705): 2 camel". El número entre paréntesis es el NÚMERO DE PEDIDO y
  normalmente son solo sus TRES ÚLTIMOS DÍGITOS (705, 682, 860). Transcribe
  esos tres dígitos EXACTAMENTE: no los rellenes, no inventes el resto; la
  aplicación completa el número desde el excel de pedido del cliente. Si el
  operario lo escribe entero (4100128715), cópialo entero.

- Destinación: hay UNA HOJA POR DESTINACIÓN, titulada arriba ("APC Japan",
  "APC Korea", "APC Retail", "APC Douanes USA"). Usa el nombre del catálogo:
    Japan -> JAPAN
    Korea -> KOREA
    Douanes USA / D. USA -> D. USA
    Ivry -> IVRY
    Retail -> RETAIL
    Wholesale concess -> WHOLESALE CONCESS
    Wh. / Wholesale -> WHOLESALE
    Australia -> AUSTRALIA
    Chine franch -> CHINE FRANCH
  Desarrolla las abreviaturas ("wh." es WHOLESALE). Si la hoja dice una
  destinación que no está en esta lista, cópiala tal cual Y añade una línea a
  "avisos".

- "canal": solo si una línea concreta marca un canal distinto del de la hoja.
  Si no, omítelo: lo rellena la aplicación.

- Los cinturones llevan "talla" de 5 en 5 (75, 80, 85, 90, 95, 100): una
  entrada de "referencias" por talla.

- Los números de caja REINICIAN EN 1 en cada destinación.

- El número de pedido puede repetirse entre paréntesis en las líneas de caja
  ("Nº 4 x 25 Liquen (682) Japan"): ahí es el MISMO pedido, no una cantidad.

- El reparto de palets puede venir en una hoja resumen APARTE, incluso la
  primera del documento, con una línea por destinación ("Japan -> 1 palet
  1-6", "wh. 5 palet"). Aplica la regla 10 del núcleo destinación a
  destinación: rango de cajas escrito -> a "palets"; solo un recuento ->
  "palets" vacío y el recuento a "resumenPalets".

- NO rellenes nunca "livraisonCode": la aplicación lo genera y se ignora el que
  venga en la entrada.
```

### 4.4 Bloque genérico

```
## Este envío es de un cliente de plantilla genérica

- Lo que importa es "referencia", "color" y "medidaCaja"; añade "modelo" si en
  la hoja aparece un nombre descriptivo del artículo.
- Añade "pedido" si aparece en la hoja.
- NO rellenes "talla", "canal" ni "livraisonCode": estas plantillas no los usan.
```

### 4.5 Mensaje de usuario (tras los documentos)

Sustituye al actual («Transcribe el packing list de estas imágenes…»):

```
Transcribe al JSON descrito el packing list de estas <N> hojas. Son hojas del
MISMO envío y van en orden: un bloque puede continuar en la hoja siguiente.
Antes de responder, comprueba caja por caja que no has inventado ninguna, que
no has transcrito nada tachado y que todos los pesos son números con punto
decimal.
```

---

## 5. Qué queda por decidir

1. ~~¿Prompt por cliente?~~ **Decidido: núcleo + bloque por cliente.**
2. **¿Campo `avisos`?** Da salida a las dudas del operario (`!?`, `??`). Cuesta
   un campo en `EnvioInput` y tres líneas en el importador. Recomendado.
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

### Los tres cambios de escritura del operario que más rinden

1. **Tabla caja→palet siempre**, por destinación (como AMI hoja 3). Sin ella,
   AMI se queda sin etiquetas de palet y APC obliga a teclear el reparto a mano.
2. **Un solo peso por caja, con coma decimal** (`13,52`), y si apunta dos,
   etiquetados (`B:`/`N:`).
3. **Referencia APC con su código completo** del pedido impreso (o mínimo con
   la letra: `F67043`), y las correcciones tachando y reescribiendo al lado,
   con el valor final rodeado.
