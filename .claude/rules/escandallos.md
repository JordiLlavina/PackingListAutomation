---
paths:
  - "src/**/packinglist/service/escandallos/**"
  - "src/**/packinglist/web/Escandallos*"
  - "src/main/resources/templates/escandallos.html"
  - "src/test/resources/ejemplos/escandallos/**"
---

# Procesado de escandallos ICSUITE

> Movido tal cual desde CLAUDE.md el 2026-11-24. Se carga solo al leer o editar un fichero de los `paths` de arriba.

**Procesado de escandallos ICSUITE** (`service/escandallos/`): flujo
**totalmente independiente** del resto (`/clickup`, pantalla «Escandallos para
ClickUp») — no hay envío, ni
cliente, ni catálogo. Entran N excels de escandallo del ERP y sale **un solo
`.xlsx` con una hoja por escandallo** (`Escandallos ICSUITE.xlsx`). Los
escandallos son PDFs convertidos a excel: **los valores no caen en la columna de
su encabezado** (`Article` en B9 pero los códigos en A10, `Quantitat` en X9 pero
las cantidades en W10) y el número de materiales varía, así que `EscandalloReader`
**no usa coordenadas**: localiza la fila `Article…Quantitat`, anota *todas* sus
columnas con encabezado —también `Preu Ult.` e `Imp. Material`, que son las que
impiden que el precio se lea como cantidad— y asigna cada celda al encabezado más
cercano. Ojo: esos ficheros traen celdas `inlineStr` vacías y POI devuelve `null`
en `getStringCellValue()`. Nombre de hoja = modelo + color (`NombresHoja`), porque
hay un escandallo por color y todos comparten `MODEL`. Sin plantilla `.xlsx`: la
maquetación son constantes de `EscandallosExcelBuilder`. Por decisión del usuario,
la espuma, el papel y la tinta (`R-FOAM`, `R-PP00`, `R-TT00`) **no salen** en el
excel: los quita `EscandallosGenerationService.ARTICULOS_EXCLUIDOS`, sin aviso, y
no el lector, que sigue devolviendo todos los materiales del ERP tal cual. Del
`COLOR` (`000    0014 NOIR`) solo sale la **descripción** (`NOIR`), en la celda y en
el nombre de la hoja (`Escandallo.descripcionColor`): se quitan los grupos del
principio que llevan algún dígito, también un código con letras como `A237`.
