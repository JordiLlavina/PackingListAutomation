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
`application.yml`: **WHOLESALE**, **RETAIL**, D. USA, IVRY, JAPAN y KOREA.

WHOLESALE y RETAIL son **destinos padre**: agrupan destinaciones hijas que se
tratan como si fueran ellos (mismo fichero, misma hoja, misma dirección), y la
hija solo sobrevive en la columna DESTINATION de sus líneas.

| Padre | Hijas |
|---|---|
| WHOLESALE | AUSTRALIA, WHOLESALE, CHINE FRANCH |
| RETAIL | RETAIL, WHOLESALE CONCESS |

Dos hijas del mismo padre en un envío salen en UN solo excel, con sus cajas
concatenadas. Si sus números de caja o de palet se repiten se avisa y **no se
renumera nada**: el número está pegado físicamente en el bulto. Un destino que
no sea ni clave ni hija sigue sin generar, con aviso.

`C-LOG` era el nombre viejo de WHOLESALE y ya no existe.

| Campo | Uso |
|---|---|
| `modelo` | columna MODÈLE (p. ej. "LE NEIGE") |
| `livraisonCode` | **se ignora**: lo genera la aplicación (`PUN` + fecha de envío `yyyyMMdd` + abreviatura del padre + contador, p. ej. `PUN20260428WH1`) y se edita en la cabecera de cada destinación de la pantalla de revisión |
| `pedido` | los **tres últimos dígitos** del pedido; el número entero se busca por referencia en el excel de pedido del cliente que se sube en la entrada. Sin excel o sin fila, se queda como llegó, con aviso. Columna COMMANDE |
| `referencia` | columna RÉFÉRENCE |
| `canal` | columna DESTINATION de la línea (WHOLESALE, RETAIL, AUSTRALIA...). Si falta, se rellena con el nombre de la destinación hija |
| `color` | columna COLORIS |
| `talla` | columna SIZE (solo cinturones) |
| `palets[].tara` | tara del palet en el subtotal "PALET n" (10 kg si falta) |
| `palets[].medidas` | volumen de palet del TOTAL GROSS VOLUME (0.168 m3 si falta) |

Una caja con varias líneas (tallas/canales/colores) ocupa varias filas: el
Nº COLIS y el peso de la caja entera van solo en la primera.

En la pantalla de entrada, APC y AMI muestran un campo opcional para subir el
**excel de pedido del cliente**. En APC es de donde sale el número de pedido
completo; en AMI solo se guarda para no tener que volver a subirlo en el Paso 2
de etiquetas de caja.

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

> ⚠️ **Son envíos inventados, no envíos que hayan existido.** Los generó una IA
> a partir de la forma del JSON, sin conocer la realidad del almacén: los
> números de caja, unidades, pesos, medidas y el reparto en palets están
> puestos a ojo. Sirven para ejercitar el flujo de punta a punta y como
> fixture, y **no son autoridad sobre nada**. Lo mismo vale para los JSON
> recortados que aparecen como ejemplo más arriba en este documento.
>
> Los únicos ficheros de `docs/` con **datos reales** de cliente son los dos
> excels de pedido: `docs/Etiquetas cajas/EAN PUNTOTRES H26.xlsx` (AMI) y
> `docs/Etiquetas cajas/APC_PEDIDO_FALL26.xlsx` (APC). De los demás `.xlsx`
> de `docs/` lo real es la **maquetación** (son las plantillas y salidas que
> usa el cliente); su contenido es también de relleno.

De los cuatro JSON de `src/test/resources/ejemplos/`, `EjemplosJsonTest`
importa y genera de verdad los tres primeros contra el catálogo real de
`application.yml`; el de etiquetas de APC no lo carga ningún test:

- `envio-ami-bags-y-belts.json`: AMI con las tres destinaciones
  (China/Japan/France), bolsos y carteras (ULL/USL) y cinturones (UBL),
  incluida una caja con tres tallas (85-95-105 en France) y `pesoBruto` por
  caja (en las cajas mixtas de cinturón, en la primera talla). De este el
  único que tiene las **claves** copiadas del pedido real: referencias,
  colores, POs y tallas existen en `EAN PUNTOTRES H26.xlsx` (el sufijo del PO
  marca la destinación: CH China, JP Japan, sin sufijo France), así que las
  etiquetas salen con EAN de verdad. Las cantidades y los pesos siguen siendo
  inventados, y nada impide que una edición futura rompa esa correspondencia:
  si un PO deja de existir, las etiquetas salen sin EAN y con aviso.
- `envio-apc.json`: APC destino IVRY con bolsos y cinturones, caja de tres
  líneas y taras de palet mixtas (8.04 del JSON + 10 por defecto).
- `envio-apc-etiquetas.json`: APC con sus siete destinos (Australia, Chine
  franch, D. USA, Japan, Korea, Retail, Wholesale), para probar a mano el flujo
  de etiquetas de caja. Ningún test lo usa. Ojo: Australia, Chine franch y
  Wholesale son las tres hijas de WHOLESALE, así que al importarlo se fusionan
  en una sola destinación y avisa de sus números de caja repetidos.
- `envio-generico.json`: ACKERMANN con un palet de 16 cajas.
