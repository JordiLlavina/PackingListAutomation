# Packing con destinaciones no configuradas: avisar y seguir — diseño

Fecha: 2026-07-26 · Estado: aprobado

## Problema

Con un cliente que tiene catálogo de destinos en `application.yml` (hoy solo
APC), una destinación del JSON que no esté en ese catálogo (ej. "Australia",
"Chine franch", "Retail") hace que `ApcExcelBuilder.generar` lance
`IllegalStateException` (`ApcExcelBuilder.java:91-94`). El catch de
`POST /generar` en `PackingListController` envuelve el bucle entero, así que
una sola destinación desconocida aborta la generación de TODO el envío.

Esto viola la regla del proyecto "nunca bloquear por datos que un humano
puede resolver": las destinaciones reconocidas deben generarse igual.

## Comportamiento nuevo

En el bucle de `POST /generar`, antes de llamar al generador de cada
destinación:

- Si el cliente tiene catálogo de destinos (`!cliente.getDestinos().isEmpty()`)
  y `cliente.destinoPara(nombre)` está vacío → se añade el aviso
  `"Destinación '<nombre>' sin datos configurados para <cliente>: packing no
  generado"` a una lista nueva `avisosGeneracion` de `EnvioEnCurso`, y la
  destinación SE SALTA. Las demás se generan normal.
- Clientes sin catálogo de destinos (AMI, GENERIC) no cambian: su mapa de
  destinos está vacío y el check no aplica.
- El `orElseThrow` de `ApcExcelBuilder` se queda como red de seguridad
  (con el check previo ya no debería dispararse desde la web).

`avisosGeneracion` se limpia en `reiniciar()` y al regenerar (mismo sitio
donde hoy se limpian `excels` y etiquetas).

### Vista de resultados

`resultados.html`, sección "📦 Packing Lists": los `avisosGeneracion` se
muestran como alertas `div.alerta` (mismo patrón que los avisos de
etiquetas), encima de la tabla de excels.

### Casos de borde (decisiones cerradas)

- **Volcado ERP: sin cambios.** Sigue agrupando TODAS las cajas del envío,
  también las de destinaciones no reconocidas (el albarán es del envío
  completo y no depende de la plantilla del packing).
- **Etiquetas: sin cambios.** Ya avisan por su cuenta de destinaciones no
  soportadas.
- **Ninguna destinación reconocida** → 0 excels: se vuelve a `/revision` con
  el flash de error de siempre, incluyendo la lista de destinaciones no
  reconocidas (los avisos acumulados). No se entra en resultados.

## Tests

En `PackingListControllerTest` (MockMvc, patrón existente del fichero):

1. Envío APC con una destinación válida (ej. JAPAN) + una desconocida
   (ej. AUSTRALIA) → generar → resultados con 1 excel y el aviso de la
   desconocida en el modelo/vista.
2. Envío APC solo con destinaciones desconocidas → generar → redirect a
   `/revision` con flash de error que menciona la destinación.

## Fuera de alcance

- Avisar ya en la pantalla de revisión (podría añadirse más adelante).
- Cambiar la interfaz `GeneradorPackingListCliente` para devolver avisos.
