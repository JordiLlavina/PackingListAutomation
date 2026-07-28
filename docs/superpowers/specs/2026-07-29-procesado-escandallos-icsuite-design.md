# Procesado de Escandallos ICSUITE — diseño

Fecha: 2026-07-29

## Qué resuelve

El ERP (ICSUITE) imprime un escandallo por artículo y color: un `.xlsx` que en
realidad es un PDF convertido, con celdas minúsculas, columnas de 0,16 de ancho
y los valores desalineados respecto a sus propios encabezados. Volcar esos datos
a mano a otro sistema es tedioso y se hace fichero a fichero.

La feature recibe **varios escandallos** y devuelve **un solo excel con una hoja
por escandallo**, limpio, con solo los datos que interesan.

Es un flujo **completamente independiente** del asistente de packing list: no
comparte modelo, ni sesión, ni catálogo de clientes. Comparte únicamente el
menú, los estilos y la pauta de "avisos en vez de excepciones".

## Entrada: el escandallo del ERP

Un `.xlsx` de una sola hoja. Los datos que se extraen:

| Dato | Etiqueta en el excel | Ejemplo |
|---|---|---|
| Modelo | `MODEL` | `ULL770.AL245` |
| Descripción | `DESCRIPCIO` | `SAC CANDY RABAT LARGE UNISEX` |
| Color | `COLOR` | `000    0014 NOIR` |

Y la tabla de materiales, cuyos encabezados son `Article`, `Descripcio` y
`Quantitat`. Se ignora todo lo demás: precios, importes, la tabla de **Fases**
que viene a continuación y los totales.

### El problema de las coordenadas

En el fichero real los encabezados y sus valores **no están en la misma
columna**: `Article` está en B9 pero los códigos en A10:A34; `Quantitat` en X9
pero las cantidades en W10:W34. Y el número de materiales cambia de un artículo
a otro, así que las filas tampoco son fijas.

Anclar a coordenadas fijas (S4, AH4, A10…) funcionaría con los dos ejemplos y
se rompería con el tercero.

**Solución: anclaje por cercanía a los encabezados.**

1. Se busca la fila de encabezado de la tabla: la primera que contiene a la vez
   `Article` y `Quantitat` (normalizando mayúsculas, acentos y espacios).
2. Se anotan **todas** las columnas con encabezado de esa fila —`Article`,
   `Descripcio`, `Quantitat`, `Preu Ult.`, `Imp. Material`, `Imp. Fases`,
   `Import`—, no solo las tres que interesan.
3. En cada fila de datos, cada celda no vacía se asigna al **encabezado cuya
   columna esté más cerca**. Así `W` (23) cae en `Quantitat` (24) y `AB` (28)
   cae en `Preu Ult.` (29) sin ambigüedad, aunque la primera esté a solo 4
   columnas de `Quantitat`. Los encabezados que no interesan actúan de
   pararrayos: existen precisamente para que sus valores no contaminen los
   nuestros.
4. La tabla termina en la primera fila que contenga la palabra `Total`, o al
   acabarse la hoja.
5. El bloque de cabecera (`MODEL`, `DESCRIPCIO`, `COLOR`) se busca solo en las
   filas **anteriores** a la fila de encabezado de la tabla —así el
   `Descripcio` de la tabla no se confunde con el del artículo— y el valor de
   cada etiqueta es la primera celda no vacía a su derecha en la misma fila.

### Cantidades

Las cantidades llegan como texto (`1.2`, `0.505`, `4.0`). Se parsean tolerando
ambos separadores decimales: si el texto trae coma y no trae punto, la coma es
el decimal. Una cantidad ilegible se guarda como `null` y deja la celda en
blanco, con aviso; no tumba el escandallo.

## Salida: el excel único

Un `.xlsx` con una hoja por escandallo, en el orden en que se subieron los
ficheros. Cada hoja calca el ejemplo `escandallos_extraidos_ejemplo.xlsx`:

```
      A                              B                        C
1
2   MODEL          ULL770.AL245
3   DESCRIPCIÓ     SAC CANDY RABAT LARGE UNISEX
4   COLOR          000    0014 NOIR
5
6   Article        Descripció                  Quantitat
7   P-FOUB01       PIEL/FOULARD NEGRO                1,2
8   F-ECAMI        ETIQUETA EXTERIOR AMI               1
…
```

Etiquetas y encabezados en negrita; datos en Calibri 8 como el ejemplo; anchos
de columna A≈11, B≈41, C≈10; cantidades como **número**, no como texto.

### Nombre de hoja

`MODEL + color abreviado` — `ULL770.AL245 NOIR`, `ULL770.AL245 SABLE SAND`.

Necesario porque los escandallos son por modelo **y color**: los dos ejemplos
comparten `MODEL` (`ULL770.AL245`) y Excel no admite dos hojas con el mismo
nombre.

El color abreviado sale de descartar los grupos puramente numéricos del campo
`COLOR` (`000    0014 NOIR` → `NOIR`; `001    255 SABLE SAND` → `SABLE SAND`).
La celda B4 conserva el valor completo.

Reglas de saneado, en este orden:

1. Se sustituyen por espacio los caracteres que Excel prohíbe: `\ / ? * [ ] :`.
2. Se recorta a 31 caracteres (límite de Excel).
3. Si el nombre ya existe se añade ` (2)`, ` (3)`… recortando lo necesario para
   no pasar de 31.
4. Si no hay modelo legible se usa el nombre del fichero de origen.

## Flujo web

Ruta `/escandallos`, una sola pantalla, con descarga directa en el caso bueno:

- `GET /escandallos` — formulario de subida múltiple (`.xlsx`).
- `POST /escandallos/procesar`:
  - **sin avisos** → responde con los bytes del `.xlsx`. No hay pantalla
    intermedia.
  - **con avisos** → vuelve a `/escandallos` mostrando la lista de problemas,
    el resumen de hojas generadas y un botón «Descargar igualmente». El excel
    queda guardado en un bean de sesión.
- `GET /escandallos/descargar` — entrega el excel guardado en sesión.

Nombre del fichero: `Escandallos ICSUITE.xlsx`.

Una tarjeta nueva en `/menu` («📋 Procesado de Escandallos ICSUITE») lleva a la
pantalla.

### Avisos y errores

Siguiendo la regla del proyecto (*nunca fallar en silencio, nunca bloquear por
datos que un humano puede resolver*):

| Situación | Comportamiento |
|---|---|
| Un fichero no es un `.xlsx` legible | Se omite, aviso con su nombre; el resto se procesa |
| No se encuentra la fila `Article`/`Quantitat` | Se omite, aviso; el resto se procesa |
| Falta `MODEL`, `DESCRIPCIO` o `COLOR` | La hoja se genera igual con esa celda vacía, aviso |
| La tabla no tiene ninguna línea | La hoja se genera con solo la cabecera, aviso |
| Una cantidad no es un número | Celda en blanco, aviso |
| Choque de nombre de hoja | Se renombra con ` (2)`, aviso |
| **Ningún** fichero utilizable | Error en la pantalla, no hay descarga |

## Componentes

```
web/EscandallosController        subida múltiple → xlsx o pantalla con avisos
web/EscandallosEnCurso           bean de sesión: excel + avisos (Descargar igualmente)
templates/escandallos.html       la pantalla

service/escandallos/
  Escandallo                     record: modelo, descripcion, color, líneas, origen
  LineaEscandallo                record: article, descripcion, cantidad (Double)
  EscandalloReader               .xlsx del ERP → Escandallo (+ avisos)
  LecturaEscandallo              record de resultado del reader: escandallo + avisos
  NombresHoja                    modelo+color → nombres de hoja únicos y válidos
  EscandallosExcelBuilder        List<Escandallo> → byte[] (un libro, una hoja cada uno)
  EscandallosGenerationService   orquesta ficheros → ResultadoEscandallos
  ResultadoEscandallos           record: excel, nombres de hoja, avisos
```

`cantidad` es `Double` y no `double` por la misma razón que los pesos del
packing list: `null` significa «desconocido» y es lo que deja la celda en
blanco.

## Tests

Se sigue la pauta del proyecto: JUnit 5 puro con `new`, salvo el test de la
capa web (`@SpringBootTest` + MockMvc), y los tests de builder reabren el
`.xlsx` con POI para comprobar celdas reales.

- `EscandalloReaderTest` — contra `ULL770 NOIR.xlsx` real: modelo, descripción,
  color, 25 líneas, primera y última línea, cantidades decimales, y que **no**
  se cuelan ni el `Total` ni las Fases. Más casos sintéticos: columnas
  desplazadas, sin cabecera, cantidad ilegible.
- `NombresHojaTest` — color abreviado, choque, truncado a 31, caracteres
  prohibidos, sin modelo.
- `EscandallosExcelBuilderTest` — reabre el libro: número y nombres de hojas,
  celdas A2:B4, cabecera de la fila 6, filas de datos, cantidad numérica.
- `EscandallosGenerationServiceTest` — un fichero ilegible no impide el resto;
  ningún fichero utilizable → resultado vacío con avisos.
- `EscandallosControllerTest` — `@SpringBootTest`: GET pinta la pantalla, POST
  limpio devuelve el `.xlsx`, POST con un fichero roto devuelve HTML con el
  aviso, `descargar` sin sesión redirige.
- `EscandallosFlujoRealTest` — los dos `ULL770` reales, deja
  `Escandallos ICSUITE.xlsx` en `target/` para abrirlo a mano.

Los dos escandallos de ejemplo se copian a
`src/test/resources/ejemplos/escandallos/`, como el resto de ejemplos de test.

## Fuera de alcance (YAGNI)

- Editar o revisar los datos extraídos antes de generar (no hay nada que
  inferir: o el dato está en el escandallo o no está).
- La tabla de Fases, precios e importes.
- Formatos de escandallo de otros ERPs.
- Persistencia: como el resto de la app, todo vive en la sesión.
