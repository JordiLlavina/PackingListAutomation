# Campos del JSON de envío por cliente

El JSON de entrada tiene la misma estructura para todos los clientes; lo que
cambia es qué campos opcionales necesita la plantilla de cada uno. Un envío
es siempre de UN cliente (campo `cliente`, que debe coincidir con el
seleccionado en el desplegable de la web; si no coincide se avisa sin
bloquear).

## Estructura común

```json
{
  "cliente": "<clave del catálogo de application.yml>",
  "destinos": [
    {
      "destino": "<nombre del destino>",
      "palets": [
        { "palet": 1, "cajaInicio": 1, "cajaFin": 12,
          "medidas": "80x120x130",   // opcional, cm
          "tara": 8.04 }             // opcional, kg (10 si falta)
      ],
      "referencias": [
        {
          "referencia": "...",       // obligatorio
          "color": "...",            // obligatorio
          "medidaCaja": "60x40x40",  // obligatorio, "LxWxH" en cm
          "pedido": "...",           // obligatorio
          "cantidadTotal": 100,      // opcional, para validar la suma
          "modelo": "...",           // opcional (APC / genérica)
          "livraisonCode": "...",    // opcional (APC)
          "canal": "...",            // opcional (APC)
          "talla": "85",             // opcional (cinturones AMI y APC)
          "cajas": [
            { "caja": 31, "unidades": 50, "pesoBruto": 16.2 },  // pesoBruto opcional, kg
            { "cajaInicio": 1, "cajaFin": 30, "unidadesPorCaja": 50, "pesoBruto": 18.5 }
          ]
        }
      ]
    }
  ]
}
```

Los pesos son opcionales. `pesoBruto` (kg) es el peso de la caja física y
solo se pone cuando la imagen del packing list lo indica; si falta, el peso
queda pendiente y lo completa la aplicación (tabla de taras de
`application.yml` + pesos tecleados en la pantalla de revisión). El peso neto
NUNCA viene en el JSON: se deriva del bruto menos la tara. En un rango el
`pesoBruto` aplica a cada una de sus cajas; en una caja mixta (mismo nº de
caja en varias entradas de talla/color/referencia) se pone una sola vez, en
la primera entrada, y es el peso del bulto ENTERO, porque la caja se pesa
entera una vez.

Esta regla vale para **todos los clientes** y para ambas salidas (packing
list y etiquetas): las líneas de una caja nunca se suman entre sí y las que
no son la primera no aportan peso. Si la primera línea no trae peso, la caja
queda pendiente aunque otra línea sí lo traiga.

Una misma caja física puede aparecer en VARIAS entradas de `referencias`
(mismo número de caja): caja mixta de dos colores, cinturones con varias
tallas, canales distintos de APC. Solo se avisa como posible error si se
repite la combinación completa referencia+color+talla+canal.

## AMI (plantilla AMI, un excel por referencia+color)

El tipo de artículo se reconoce por el prefijo de la referencia:
`UBL` = cinturón, `ULL` = bolso, `USL` = cartera.

| Campo | Bolsos/carteras (ULL/USL) | Cinturones (UBL) |
|---|---|---|
| `referencia`, `color`, `medidaCaja`, `pedido`, `cajas` | obligatorios | obligatorios |
| `talla` | no se usa | **obligatorio** (70, 75, ... 110); una entrada por talla; una caja con varias tallas = varias entradas con el mismo número de caja |
| `modelo`, `livraisonCode`, `canal` | no se usan | no se usan |

Además, en la pantalla de entrada AMI permite editar CIUDAD y PAÍS del
proveedor (por defecto Badalona / Spain).

## APC (plantilla APC, un excel por destino)

Bolsos y cinturones comparten plantilla. Destinos configurados en
`application.yml` (D. USA, IVRY, JAPAN, KOREA, C-LOG): un destino que no
esté en el catálogo hace fallar la generación con un error claro.

| Campo | Uso |
|---|---|
| `modelo` | columna MODÈLE (p. ej. "LE NEIGE") |
| `livraisonCode` | columna Livraison code (p. ej. "PUN20260428WH1") |
| `pedido` | columna COMMANDE |
| `referencia` | columna RÉFÉRENCE |
| `canal` | columna DESTINATION de la línea (WHOLESALE, RETAIL, AUSTRALIA...) |
| `color` | columna COLORIS |
| `talla` | columna SIZE (solo cinturones) |
| `palets[].tara` | tara del palet en el subtotal "PALET n" (10 kg si falta) |
| `palets[].medidas` | volumen de palet del TOTAL GROSS VOLUME (0.168 m3 si falta) |

Una caja con varias líneas (tallas/canales/colores) ocupa varias filas: el
Nº COLIS y el peso de la caja entera van solo en la primera.

## Clientes genéricos (plantilla GENERIC, un excel por destino)

ACKERMANN, DROLE DU MONSIEUR, KAMI, LGN, PALOMA WOOL, PAUL & JOE,
SONIA RYKIEL... Dar de alta uno nuevo = añadir su bloque en
`application.yml` (nombre-legal + direccion-entrega), sin tocar código.

| Campo | Uso |
|---|---|
| `referencia` | columna REFERENCE |
| `modelo` | columna MODEL (opcional) |
| `color` | columna COLOUR |
| `medidaCaja` | columna SIZE in CM |
| `palets[].medidas` / `palets[].tara` | fila "PALET n" (tara 10 kg si falta) |
| `talla`, `livraisonCode`, `canal` | no se usan |

La temporada del formulario va a la columna SEASON.

## Ejemplos completos (pasan los tests)

Los tres JSON de `src/test/resources/ejemplos/` son ejemplos completos y
válidos que los tests importan y generan de verdad:

- `envio-ami-bags-y-belts.json`: AMI con las tres destinaciones
  (China/Japan/France), bolsos y carteras (ULL/USL) y cinturones (UBL),
  incluida una caja con tres tallas (85-95-105 en France) y `pesoBruto` por
  caja (en las cajas mixtas de cinturón, en la primera talla). Referencias,
  colores, POs y tallas reales del pedido `AMI EAN H26.xlsx` (el sufijo del
  PO marca la destinación: CH China, JP Japan, sin sufijo France).
- `envio-apc.json`: APC destino IVRY con bolsos y cinturones, caja de tres
  líneas y taras de palet mixtas (8.04 del JSON + 10 por defecto).
- `envio-generico.json`: ACKERMANN con un palet de 16 cajas.
