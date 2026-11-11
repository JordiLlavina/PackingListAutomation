# Cola de órdenes del taller (sustituye a las prioridades)

Fecha: 2026-11-11. Rama `modelos-y-modificaciones`. Pedido por el usuario tras
probar la app; acordado en la conversación (todas las suposiciones salvo una,
corregida: **con un filtro puesto no se puede reordenar**).

## Por qué

Las prioridades alta / normal / baja no sirven: cambian mucho y lo que importa
es saber **cuál es la siguiente**. Y nada «vence»: toda modificación se hace en
algún momento, así que la etiqueta «vencida» y los plazos por prioridad sobran.

## Qué se construye

1. **Una cola única** con las **órdenes del despacho al taller** abiertas
   (NUEVA, VISTA, EN_CURSO), cada una con su posición (1ª, 2ª…). Los avisos del
   taller **no** entran: los aplica el despacho en ICSUITE.
2. **Pestaña «Cola» en el despacho** (`/app/cola`): la lista con foto, modelo,
   título, estado y posición; se reordena **arrastrando** o con **↑ ↓,
   «Primera» y «Última»**; un botón la da vuelta (de la primera a la última o
   al revés); filtros como en modelos: buscador (referencia, nombre del modelo
   o título), cliente y Todos / Prototipos / Muestras / Producción /
   Inactivos. **Con cualquier filtro puesto los controles de orden se apagan**
   y se dice por qué: con la vista parcial no se ve la cola entera.
3. **Al crear una orden**, «Posición en la cola»: al final (por defecto), la
   primera, o «antes de…» cualquiera de la cola. Y una casilla «Urgente».
4. **Urgente** sustituye a «vencida»: una marca que pone el despacho a mano.
   Marcarla **sube la orden a la primera posición**; quitarla no la mueve.
   Se ve como la etiqueta roja «Urgente».
5. **Taller**: la Bandeja de Entrada sale en el orden de la cola con el número
   de cada orden; la primera destaca como «La siguiente».
6. **Recordatorios**:
   - fuera los plazos por prioridad (nada caduca);
   - al taller, **un resumen diario** («Tienes 5 órdenes; la siguiente es
     ULL770: cambiar el forro») a partir de la hora configurada, de lunes a
     viernes;
   - cada orden **urgente** sin hacer, además, una vez al día;
   - al despacho: aviso sin aplicar (cada 7 días, ahora contado desde que se
     crea) y preguntas sin contestar, como estaban;
   - fuera el de «aviso sin prioridad»: los avisos ya no llevan prioridad.

## Cómo (decisiones de implementación, mías)

- **V6**: `modificacion.posicion_cola integer` (nulo fuera de la cola) y
  `modificacion.urgente boolean not null default false`. Migración de datos:
  las órdenes abiertas ALTA pasan a urgentes; la cola se siembra con el orden
  que tenían (ALTA, NORMAL, BAJA o sin prioridad, y la más antigua antes). La
  columna `prioridad` **se queda en la base sin usar** (nada de lo guardado se
  pierde); la entidad deja de mapearla. Test de migración como
  `MigracionHistorialSinCascadaTest`.
- **`ColaTaller`** (servicio): lee la cola, coloca una orden nueva, la sube a
  la primera y reordena. Las posiciones se **renumeran 1..N** en cada cambio
  (son decenas). Una orden que se cierra conserva su número, que ya no cuenta:
  la cola son siempre las abiertas ordenadas por posición (y a igualdad, la
  más antigua).
- **API**: `GET /api/modificaciones/cola` (cualquier usuario: el taller
  también la lee), `PUT /api/modificaciones/cola {ids}` (despacho) con **la cola entera**: si los ids no son
  exactamente las órdenes abiertas (alguien ha creado o cerrado una entretanto)
  contesta **409** y la pantalla recarga; `POST /api/modificaciones/{id}/urgente
  {urgente}`; al crear, `antesDe` (id; vacío = al final) y `urgente`. Se quitan
  `POST /{id}/prioridad` y los parámetros `prioridad`.
- **`ModificacionResumen`**: fuera `prioridad` y `vencida`; entran `urgente`,
  `posicionCola` (1-based, nulo fuera de la cola) y `etapa` del modelo.
- **Bandeja de despacho**: la pestaña «Al taller» sale en el orden de la cola;
  «Del taller», las nuevas primero y la más antigua antes. Contadores: fuera
  «vencidas» y «sin prioridad»; entra «urgentes» (órdenes abiertas urgentes).
- **Config** (`modelos.recordatorios.taller`): `resumen-cola-hora: 8` (hora
  desde la que sale el resumen del día; negativa lo apaga) y `urgente-dias: 1`
  (0 lo apaga). El resumen se apunta en `aviso_enviado` con tipo
  `RESUMEN_COLA` y la orden que iba primera; se manda una vez por día natural.
- `AvisoEnviado.Tipo.SIN_PRIORIDAD` se conserva para leer las filas viejas.

## Plan de desarrollo

1. V6 + test de migración.
2. Entidad, `ColaTaller`, cambios en `Modificaciones` (crear, urgente, listar,
   resumen) y DTO; tests de servicio.
3. `Recordatorios` nuevo + tests; yml y `application-test.yml`.
4. Controlador (`/api/cola`, urgente, crear) + tests de controlador.
5. PWA: tipos y funciones puras (con Vitest), pestaña Cola, NuevaOrden,
   detalle, bandeja, taller (Bandeja de Entrada y orden), estilos.
6. Suite completa, `tsc`, Vitest, build; CLAUDE.md.

## Fuera de alcance

Varias colas (por técnico), cola para los avisos del taller, deshacer un
reordenado.
