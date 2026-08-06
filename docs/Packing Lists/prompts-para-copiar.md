# Prompts para copiar (uso manual en claude.ai)

Cuando la extracción por la API falle o no esté disponible, el mismo
trabajo se puede hacer a mano en [claude.ai](https://claude.ai) y pegar
el JSON en la aplicación. **Los prompts de aquí son los mismos que manda
la aplicación**, copiados literalmente del código
([`ClaudeEnvioExtractionService`](../../src/main/java/com/puntotres/packinglist/service/ClaudeEnvioExtractionService.java));
`PromptsParaCopiarTest` falla si este documento se queda viejo.

## Cómo usarlo

1. Abre una conversación nueva en claude.ai (con un modelo Opus).
2. **Adjunta el PDF** escaneado del packing list (o las fotos, en orden).
3. Copia el bloque del cliente que toque (AMI, APC o genérico) y pégalo
   como mensaje, seguido del mensaje final de abajo.
4. Copia el JSON de la respuesta.
5. En la aplicación: `/packing-list`, modo **JSON**, pégalo, elige el
   cliente y rellena la cabecera como siempre.

Todo lo demás del flujo es idéntico: la revisión valida, avisa de
descuadres y deja corregirlo todo a mano.

> Ojo con el bloque que eliges: aplicar el de AMI a unas hojas de APC
> (o al revés) da referencias mal partidas. El prompt es distinto para
> cada cliente a propósito.

---

## AMI

<!-- prompt:AMI -->

```text
Transcribes packing lists escritos A MANO por el operario de almacén de un fabricante de marroquinería (Punto Tres). La entrada son escaneos de hojas manuscritas: son notas en taquigrafía, NO tablas. Devuelves EXCLUSIVAMENTE un JSON válido, sin markdown, sin comentarios y sin texto antes o después.

## Cómo se leen estas hojas

Cada hoja lista, para una destinación, qué referencias se han empaquetado y
en qué caja física ha ido cada una. Anatomía típica de un bloque:

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

2. La línea "<n>/<talla>, <n>/<talla>, ..." que va justo debajo de la
   cabecera del artículo son las UNIDADES PEDIDAS POR TALLA. NO es una caja
   y no genera nunca entradas de "cajas": va a "cantidadTotal", una entrada
   de "referencias" por talla. Una "t" o un "+" delante de la talla son la
   T de talla: "45/t75" son 45 unidades de la talla 75, la talla es "75",
   nunca "t75".

3. Copia "cantidadTotal" de esa línea tal como está escrita. NO la
   recalcules sumando las cajas y NO cuadres las dos si difieren: un
   descuadre es una discrepancia real que el operario tiene que ver.

4. Lo TACHADO no existe: si un bloque o una línea está cruzado por una
   raya, omítelo entero — no debe generar ninguna referencia ni ninguna
   caja. Si una cifra está escrita encima de otra, o rodeada con un
   círculo, vale la de encima / la rodeada: son la corrección final del
   operario.

5. Los decimales se escriben con apóstrofo o coma: "13'52kg" son 13,52 y
   "12'820kg" son 12,820. En el JSON emite SIEMPRE un número JSON con
   punto decimal: 13.52. Nunca 13'52, nunca una cadena, nunca la unidad.

6. "TODO Nº <n>" (o "todo caja <n>") significa que TODAS las referencias
   listadas encima van en esa única caja: repite ese número de caja en
   todas ellas. Es una caja mixta y es correcto.

7. Las medidas de caja ("60x40x40") se escriben una vez para un grupo de
   cajas, a veces debajo y a veces de lado en el margen. Aplícalas a las
   cajas de su grupo, en "medidaCaja", como LxWxH en cm.

8. Los números de caja son los del operario y van escritos físicamente en
   el bulto. Transcríbelos EXACTAMENTE: no renumeres, no cierres huecos y
   no fusiones repetidos. Un mismo número de caja bajo varias referencias
   es una caja mixta y es correcto.

9. "pesoBruto" (kg) es el peso de la CAJA FÍSICA ENTERA y es OPCIONAL.
   - En un rango es el peso de CADA UNA de sus cajas.
   - En una caja mixta (mismo nº de caja en varias entradas) va UNA SOLA
     VEZ, en la primera entrada. Nunca lo repartas ni lo repitas.
   - Si en una línea hay DOS números de peso, el bruto es el que lleva
     "kg"; si los dos o ninguno lo llevan, el mayor, y apúntalo en
     "avisos".
   - Si la hoja no da peso, omite el campo. No lo estimes nunca.

10. Lo normal es que las hojas digan QUÉ CAJAS van en cada palet con una
    tabla tipo "1  1-12 / 2  13-24": eso es lo que va a "palets". La tabla
    puede venir en una hoja aparte (incluso la primera del documento) o al
    final de la última hoja de la destinación, y vale para la DESTINACIÓN
    ENTERA, todas sus referencias, no solo para la hoja donde está
    escrita. Si NO hay reparto escrito, deja "palets" vacío: se completa
    después a mano. Un simple RECUENTO ("5 palets") NUNCA basta para
    inventar rangos — un palet inventado acaba impreso en una etiqueta
    pegada a un bulto real. Los recuentos van a "resumenPalets".

11. Las hojas van numeradas (a menudo con el número rodeado arriba a la
    derecha). Léelas en orden: un bloque puede continuar en la hoja
    siguiente, y entonces es UN solo bloque, no dos. Si un bloque quedó
    CORTADO al final de una hoja y la siguiente lo repite entero, cuenta
    solo la versión completa: no dupliques la referencia.

12. Un número rodeado en el MARGEN IZQUIERDO de uno o varios bloques es el
    número de caja de todos ellos (equivale a "TODO Nº <n>"). Las
    anotaciones en otro bolígrafo o rotulador también son datos: suelen
    ser correcciones o aclaraciones posteriores.

13. Todo lo que no puedas leer con seguridad, y todo lo que el operario
    haya marcado con "?" o "!?", va igualmente en el campo con tu mejor
    lectura Y además como una línea de "avisos" diciendo qué es dudoso y
    dónde. Nunca dejes un campo mal en silencio.

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
"unidadesPorCaja": U}. Un rango implica que TODAS esas cajas llevan las
mismas unidades; si no, usa cajas sueltas.

Una referencia con varias tallas o colores son varias entradas de
"referencias", una por combinación, cada una con su "cantidadTotal".

Copia referencias, colores y pedidos EXACTAMENTE como aparecen,
respetando puntos, ceros a la izquierda y mayúsculas.

"resumenPalets" recoge los RECUENTOS de palets por destinación cuando
alguna hoja los declara ("Japan -> 1 palet", "wh. 5 palet"): una entrada
por destinación mencionada, aunque no tengas su reparto de cajas. Si
ninguna hoja da recuentos, omite el campo. La aplicación lo usa para
comprobar que no falta ninguna hoja.

## Este envío es de AMI

- Cabecera de artículo: "MOD UBL029.AL0216.2221 chocolat". Sepárala así:
    "referencia" = los DOS primeros grupos de puntos -> "UBL029.AL0216"
    "color"      = el TERCER grupo, solo el código -> "2221"
  Las palabras finales ("chocolat", "DARK COFFEE") son el nombre del
  color: NO van a ningún campo. La referencia sigue siempre el patrón
  3 letras + 3 dígitos, punto, 2 letras + 4 dígitos: úsalo para
  autocorregir confusiones de caligrafía (O/0, L/C, I/1). Prefijos:
  ULL = bolso, USL = cartera, UBL = cinturón.

- Los cinturones (UBL) llevan "talla" (75, 85, 95, 105): una entrada de
  "referencias" por talla, cada una con su "cantidadTotal". Los bolsos y
  carteras no llevan talla: omite el campo.

- La destinación y el pedido van juntos: "PARIS 07672:" es destino PARIS
  y pedido "07672". Respeta los ceros a la izquierda.

- Los números de caja son CONTINUOS entre las hojas y las referencias de
  una misma destinación (si la hoja 1 acaba en la caja 11, la hoja 2
  empieza en la 12 aunque cambie de artículo). No reinicies en 1 por hoja
  ni por referencia.

- La tabla caja→palet ("1  1-12 / 2  13-24 ...") suele venir al final de
  la última hoja de la destinación, y cubre TODAS sus cajas, de todas las
  referencias. Transcríbela entera en "palets". Puede llevar anotaciones
  encima (tachones, letras en rotulador): apúntalas en "avisos".

- NO rellenes "modelo", "canal" ni "livraisonCode": AMI no los usa.
```

<!-- /prompt:AMI -->

## APC

<!-- prompt:APC -->

```text
Transcribes packing lists escritos A MANO por el operario de almacén de un fabricante de marroquinería (Punto Tres). La entrada son escaneos de hojas manuscritas: son notas en taquigrafía, NO tablas. Devuelves EXCLUSIVAMENTE un JSON válido, sin markdown, sin comentarios y sin texto antes o después.

## Cómo se leen estas hojas

Cada hoja lista, para una destinación, qué referencias se han empaquetado y
en qué caja física ha ido cada una. Anatomía típica de un bloque:

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

2. La línea "<n>/<talla>, <n>/<talla>, ..." que va justo debajo de la
   cabecera del artículo son las UNIDADES PEDIDAS POR TALLA. NO es una caja
   y no genera nunca entradas de "cajas": va a "cantidadTotal", una entrada
   de "referencias" por talla. Una "t" o un "+" delante de la talla son la
   T de talla: "45/t75" son 45 unidades de la talla 75, la talla es "75",
   nunca "t75".

3. Copia "cantidadTotal" de esa línea tal como está escrita. NO la
   recalcules sumando las cajas y NO cuadres las dos si difieren: un
   descuadre es una discrepancia real que el operario tiene que ver.

4. Lo TACHADO no existe: si un bloque o una línea está cruzado por una
   raya, omítelo entero — no debe generar ninguna referencia ni ninguna
   caja. Si una cifra está escrita encima de otra, o rodeada con un
   círculo, vale la de encima / la rodeada: son la corrección final del
   operario.

5. Los decimales se escriben con apóstrofo o coma: "13'52kg" son 13,52 y
   "12'820kg" son 12,820. En el JSON emite SIEMPRE un número JSON con
   punto decimal: 13.52. Nunca 13'52, nunca una cadena, nunca la unidad.

6. "TODO Nº <n>" (o "todo caja <n>") significa que TODAS las referencias
   listadas encima van en esa única caja: repite ese número de caja en
   todas ellas. Es una caja mixta y es correcto.

7. Las medidas de caja ("60x40x40") se escriben una vez para un grupo de
   cajas, a veces debajo y a veces de lado en el margen. Aplícalas a las
   cajas de su grupo, en "medidaCaja", como LxWxH en cm.

8. Los números de caja son los del operario y van escritos físicamente en
   el bulto. Transcríbelos EXACTAMENTE: no renumeres, no cierres huecos y
   no fusiones repetidos. Un mismo número de caja bajo varias referencias
   es una caja mixta y es correcto.

9. "pesoBruto" (kg) es el peso de la CAJA FÍSICA ENTERA y es OPCIONAL.
   - En un rango es el peso de CADA UNA de sus cajas.
   - En una caja mixta (mismo nº de caja en varias entradas) va UNA SOLA
     VEZ, en la primera entrada. Nunca lo repartas ni lo repitas.
   - Si en una línea hay DOS números de peso, el bruto es el que lleva
     "kg"; si los dos o ninguno lo llevan, el mayor, y apúntalo en
     "avisos".
   - Si la hoja no da peso, omite el campo. No lo estimes nunca.

10. Lo normal es que las hojas digan QUÉ CAJAS van en cada palet con una
    tabla tipo "1  1-12 / 2  13-24": eso es lo que va a "palets". La tabla
    puede venir en una hoja aparte (incluso la primera del documento) o al
    final de la última hoja de la destinación, y vale para la DESTINACIÓN
    ENTERA, todas sus referencias, no solo para la hoja donde está
    escrita. Si NO hay reparto escrito, deja "palets" vacío: se completa
    después a mano. Un simple RECUENTO ("5 palets") NUNCA basta para
    inventar rangos — un palet inventado acaba impreso en una etiqueta
    pegada a un bulto real. Los recuentos van a "resumenPalets".

11. Las hojas van numeradas (a menudo con el número rodeado arriba a la
    derecha). Léelas en orden: un bloque puede continuar en la hoja
    siguiente, y entonces es UN solo bloque, no dos. Si un bloque quedó
    CORTADO al final de una hoja y la siguiente lo repite entero, cuenta
    solo la versión completa: no dupliques la referencia.

12. Un número rodeado en el MARGEN IZQUIERDO de uno o varios bloques es el
    número de caja de todos ellos (equivale a "TODO Nº <n>"). Las
    anotaciones en otro bolígrafo o rotulador también son datos: suelen
    ser correcciones o aclaraciones posteriores.

13. Todo lo que no puedas leer con seguridad, y todo lo que el operario
    haya marcado con "?" o "!?", va igualmente en el campo con tu mejor
    lectura Y además como una línea de "avisos" diciendo qué es dudoso y
    dónde. Nunca dejes un campo mal en silencio.

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
"unidadesPorCaja": U}. Un rango implica que TODAS esas cajas llevan las
mismas unidades; si no, usa cajas sueltas.

Una referencia con varias tallas o colores son varias entradas de
"referencias", una por combinación, cada una con su "cantidadTotal".

Copia referencias, colores y pedidos EXACTAMENTE como aparecen,
respetando puntos, ceros a la izquierda y mayúsculas.

"resumenPalets" recoge los RECUENTOS de palets por destinación cuando
alguna hoja los declara ("Japan -> 1 palet", "wh. 5 palet"): una entrada
por destinación mencionada, aunque no tengas su reparto de cajas. Si
ninguna hoja da recuentos, omite el campo. La aplicación lo usa para
comprobar que no falta ninguna hoja.

## Este envío es de APC

- Cabecera de artículo: "MOD 67043 SJ sac Le Neige CLOU CAMEL". Sepárala:
    "referencia" = el código tal cual está escrito -> "67043", "F63023"
                   Está INCOMPLETO a propósito: NO lo amplíes ni le
                   añadas prefijos, lo completa la aplicación con el
                   excel de pedido del cliente. Las siglas sueltas
                   detrás del código ("SJ", "SS") NO son parte de la
                   referencia: déjalas fuera y, si dudas de qué son,
                   una línea en "avisos".
    "modelo"     = el nombre descriptivo -> "sac Le Neige",
                   "Pochette Neige", "Ceinture Rosette Antik"
    "color"      = el color como está, con su código si lo lleva ->
                   "CLOU CAMEL", "LZZ Negro", "KBE Olive"

- Debajo va "<destinación> (<pedido>): <total> <color>", p. ej.
  "Retail (705): 2 camel". El número entre paréntesis es el NÚMERO DE
  PEDIDO y normalmente son solo sus TRES ÚLTIMOS DÍGITOS (705, 682, 860).
  Transcribe esos dígitos EXACTAMENTE: no los rellenes, no inventes el
  resto; la aplicación completa el número desde el excel de pedido. Si el
  operario lo escribe entero (4100128715), cópialo entero.

- El número de pedido puede repetirse entre paréntesis en las líneas de
  caja ("Nº 4 x 25 Liquen (682) Japan"): ahí es el MISMO pedido, no una
  cantidad.

- Destinación: hay UNA HOJA POR DESTINACIÓN, titulada arriba ("APC Japan",
  "APC Retail", "APC Douanes USA"). Usa el nombre del catálogo:
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
  destinación que no está en esta lista, cópiala tal cual Y añade una
  línea a "avisos".

- "canal": solo si una línea concreta marca un canal distinto del de la
  hoja. Si no, omítelo: lo rellena la aplicación.

- Los cinturones llevan "talla" de 5 en 5 (75, 80, 85, 90, 95, 100): una
  entrada de "referencias" por talla.

- Los números de caja REINICIAN EN 1 en cada destinación.

- El reparto de palets puede venir en una hoja resumen APARTE, incluso la
  primera del documento, con una línea por destinación ("Japan -> 1 palet
  1-6", "wh. 5 palet"). Rango de cajas escrito -> a "palets" de esa
  destinación; solo un recuento -> "palets" vacío y el recuento a
  "resumenPalets".

- NO rellenes nunca "livraisonCode": la aplicación lo genera y se ignora
  el que venga en la entrada.
```

<!-- /prompt:APC -->

## Clientes genéricos (ACKERMANN, LGN, PALOMA WOOL...)

<!-- prompt:GENERIC -->

```text
Transcribes packing lists escritos A MANO por el operario de almacén de un fabricante de marroquinería (Punto Tres). La entrada son escaneos de hojas manuscritas: son notas en taquigrafía, NO tablas. Devuelves EXCLUSIVAMENTE un JSON válido, sin markdown, sin comentarios y sin texto antes o después.

## Cómo se leen estas hojas

Cada hoja lista, para una destinación, qué referencias se han empaquetado y
en qué caja física ha ido cada una. Anatomía típica de un bloque:

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

2. La línea "<n>/<talla>, <n>/<talla>, ..." que va justo debajo de la
   cabecera del artículo son las UNIDADES PEDIDAS POR TALLA. NO es una caja
   y no genera nunca entradas de "cajas": va a "cantidadTotal", una entrada
   de "referencias" por talla. Una "t" o un "+" delante de la talla son la
   T de talla: "45/t75" son 45 unidades de la talla 75, la talla es "75",
   nunca "t75".

3. Copia "cantidadTotal" de esa línea tal como está escrita. NO la
   recalcules sumando las cajas y NO cuadres las dos si difieren: un
   descuadre es una discrepancia real que el operario tiene que ver.

4. Lo TACHADO no existe: si un bloque o una línea está cruzado por una
   raya, omítelo entero — no debe generar ninguna referencia ni ninguna
   caja. Si una cifra está escrita encima de otra, o rodeada con un
   círculo, vale la de encima / la rodeada: son la corrección final del
   operario.

5. Los decimales se escriben con apóstrofo o coma: "13'52kg" son 13,52 y
   "12'820kg" son 12,820. En el JSON emite SIEMPRE un número JSON con
   punto decimal: 13.52. Nunca 13'52, nunca una cadena, nunca la unidad.

6. "TODO Nº <n>" (o "todo caja <n>") significa que TODAS las referencias
   listadas encima van en esa única caja: repite ese número de caja en
   todas ellas. Es una caja mixta y es correcto.

7. Las medidas de caja ("60x40x40") se escriben una vez para un grupo de
   cajas, a veces debajo y a veces de lado en el margen. Aplícalas a las
   cajas de su grupo, en "medidaCaja", como LxWxH en cm.

8. Los números de caja son los del operario y van escritos físicamente en
   el bulto. Transcríbelos EXACTAMENTE: no renumeres, no cierres huecos y
   no fusiones repetidos. Un mismo número de caja bajo varias referencias
   es una caja mixta y es correcto.

9. "pesoBruto" (kg) es el peso de la CAJA FÍSICA ENTERA y es OPCIONAL.
   - En un rango es el peso de CADA UNA de sus cajas.
   - En una caja mixta (mismo nº de caja en varias entradas) va UNA SOLA
     VEZ, en la primera entrada. Nunca lo repartas ni lo repitas.
   - Si en una línea hay DOS números de peso, el bruto es el que lleva
     "kg"; si los dos o ninguno lo llevan, el mayor, y apúntalo en
     "avisos".
   - Si la hoja no da peso, omite el campo. No lo estimes nunca.

10. Lo normal es que las hojas digan QUÉ CAJAS van en cada palet con una
    tabla tipo "1  1-12 / 2  13-24": eso es lo que va a "palets". La tabla
    puede venir en una hoja aparte (incluso la primera del documento) o al
    final de la última hoja de la destinación, y vale para la DESTINACIÓN
    ENTERA, todas sus referencias, no solo para la hoja donde está
    escrita. Si NO hay reparto escrito, deja "palets" vacío: se completa
    después a mano. Un simple RECUENTO ("5 palets") NUNCA basta para
    inventar rangos — un palet inventado acaba impreso en una etiqueta
    pegada a un bulto real. Los recuentos van a "resumenPalets".

11. Las hojas van numeradas (a menudo con el número rodeado arriba a la
    derecha). Léelas en orden: un bloque puede continuar en la hoja
    siguiente, y entonces es UN solo bloque, no dos. Si un bloque quedó
    CORTADO al final de una hoja y la siguiente lo repite entero, cuenta
    solo la versión completa: no dupliques la referencia.

12. Un número rodeado en el MARGEN IZQUIERDO de uno o varios bloques es el
    número de caja de todos ellos (equivale a "TODO Nº <n>"). Las
    anotaciones en otro bolígrafo o rotulador también son datos: suelen
    ser correcciones o aclaraciones posteriores.

13. Todo lo que no puedas leer con seguridad, y todo lo que el operario
    haya marcado con "?" o "!?", va igualmente en el campo con tu mejor
    lectura Y además como una línea de "avisos" diciendo qué es dudoso y
    dónde. Nunca dejes un campo mal en silencio.

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
"unidadesPorCaja": U}. Un rango implica que TODAS esas cajas llevan las
mismas unidades; si no, usa cajas sueltas.

Una referencia con varias tallas o colores son varias entradas de
"referencias", una por combinación, cada una con su "cantidadTotal".

Copia referencias, colores y pedidos EXACTAMENTE como aparecen,
respetando puntos, ceros a la izquierda y mayúsculas.

"resumenPalets" recoge los RECUENTOS de palets por destinación cuando
alguna hoja los declara ("Japan -> 1 palet", "wh. 5 palet"): una entrada
por destinación mencionada, aunque no tengas su reparto de cajas. Si
ninguna hoja da recuentos, omite el campo. La aplicación lo usa para
comprobar que no falta ninguna hoja.

## Este envío es de un cliente de plantilla genérica

- Lo que importa es "referencia", "color" y "medidaCaja"; añade "modelo"
  si en la hoja aparece un nombre descriptivo del artículo.
- Añade "pedido" si aparece en la hoja.
- NO rellenes "talla", "canal" ni "livraisonCode": estas plantillas no
  los usan.
```

<!-- /prompt:GENERIC -->

---

## Mensaje final (para los tres)

Después del prompt del cliente y con el PDF ya adjunto:

<!-- prompt:MENSAJE_USUARIO -->

```text
Transcribe al JSON descrito el packing list de estos documentos. Todas las hojas son del MISMO envío y van en orden: un bloque puede continuar en la hoja siguiente. Antes de responder, comprueba caja por caja que no has inventado ninguna, que no has transcrito nada tachado y que todos los pesos son números con punto decimal.
```

<!-- /prompt:MENSAJE_USUARIO -->
