// Las modificaciones tal como las da la API (/api/modificaciones): las órdenes
// que despacho manda al taller y los avisos que el taller manda a despacho,
// con sus mensajes y adjuntos. Las fechas llegan en ISO sin zona (hora local
// del servidor), que es como las lee hace(). Y lo poco que se calcula con ellas
// en el navegador, en funciones puras para poder probarlas.

import type { Etapa, Subida } from './modelos';
import { mmss } from './tiempo';

export type EstadoModificacion = 'NUEVA' | 'VISTA' | 'EN_CURSO' | 'HECHA' | 'DESCARTADA';
export type Direccion = 'DESPACHO_A_TALLER' | 'TALLER_A_DESPACHO';
export type Rol = 'TALLER' | 'DESPACHO' | 'ADMIN';
export type TipoAdjunto = 'AUDIO' | 'FOTO' | 'DOCUMENTO';
/** Por dónde va la transcripción de una nota de voz; una foto o un documento son NO_APLICA. */
export type EstadoTranscripcion = 'PENDIENTE' | 'HECHA' | 'FALLIDA' | 'NO_APLICA';

/** Dónde se aplica un aviso y si ya está hecho allí (las órdenes no tienen; el taller no los usa). */
export type DestinoEstado = { destino: string; texto: string; aplica: boolean; hechoEn: string | null; hechoPor: string | null };

/** Una modificación en una lista o en la cabecera de su detalle. */
export type ModificacionResumen = {
  id: number;
  modeloId: number;
  /** El código del modelo o, si no lo tiene, su nombre de prototipo. */
  modeloReferencia: string;
  modeloNombre: string;
  cliente: string;
  fotoPrincipalId: number | null;
  direccion: Direccion;
  tipoCambio: string | null;
  tipoCambioTexto: string | null;
  /** La ha marcado urgente el despacho (solo órdenes para el taller). */
  urgente: boolean;
  /** Su puesto en la cola del taller, desde 1; null si no está (un aviso, o ya cerrada). */
  posicionCola: number | null;
  estado: EstadoModificacion;
  titulo: string | null;
  tituloDeIa: boolean;
  creadaEn: string;
  /** Null si no la creó una persona. */
  creadaPor: string | null;
  vistaEn: string | null;
  hechaEn: string | null;
  hechaPor: string | null;
  /** El lado que tiene que contestar; null si nadie. */
  respuestaPendienteDe: Rol | null;
  /** La etapa del modelo, para filtrar; null si el modelo ya no está. */
  etapa: Etapa | null;
  destinos: DestinoEstado[];
  fotos: number;
  audios: number;
  documentos: number;
  /** Cuánto dura la nota de voz del primer mensaje (el cuerpo); null si no lleva. */
  duracionAudioSeg: number | null;
  /** De qué lado es el último mensaje (TALLER o DESPACHO), también en una cerrada; null si no hay ninguno. */
  ultimoMensajeDe: 'TALLER' | 'DESPACHO' | null;
};

/** Un adjunto de un mensaje, sin sus bytes (van en adjuntoUrl). */
export type AdjuntoVista = {
  id: number;
  tipo: TipoAdjunto;
  nombre: string;
  mime: string;
  tamano: number;
  /** Solo en las notas de voz. */
  duracionSeg: number | null;
  transcripcion: string | null;
  resumenIa: string | null;
  estadoTranscripcion: EstadoTranscripcion;
  error: string | null;
};

export type MensajeVista = {
  id: number;
  autor: string | null;
  /** De qué lado habla; null si no hay autor. */
  rolAutor: Rol | null;
  /** Vacío cuando todo lo que dice va en los adjuntos. */
  texto: string;
  creadoEn: string;
  adjuntos: AdjuntoVista[];
};

/** Lo que contesta crear (POST /api/modificaciones): la creada y, con «guardar también en el modelo», lo que se copió al modelo y lo que no se pudo (avisos). */
export type ModificacionCreada = { modificacion: ModificacionResumen; documentosGuardados: Subida[]; avisos: string[] };

export type ModificacionDetalle = {
  resumen: ModificacionResumen;
  motivoDescarte: string | null;
  /** Del primero (el cuerpo de la orden o del aviso) al último. */
  mensajes: MensajeVista[];
};

/**
 * Los números de la bandeja (GET /api/bandeja/resumen). En el taller: nuevas = órdenes sin abrir, pendientes = órdenes abiertas,
 * conRespuesta = avisos suyos con una respuesta de despacho por leer. En los dos lados, urgentes = órdenes abiertas marcadas urgentes.
 */
export type ResumenBandeja = {
  nuevas: number;
  pendientes: number;
  urgentes: number;
  conRespuesta: number;
};

export function adjuntoUrl(id: number): string {
  return `/api/adjuntos/${id}`;
}

export function estaCerrada(estado: EstadoModificacion): boolean {
  return estado === 'HECHA' || estado === 'DESCARTADA';
}

function instante(fechaIso: string | null): number {
  return fechaIso ? new Date(fechaIso).getTime() : 0;
}

/**
 * «Bandeja de Entrada», en el orden de la cola que pone el despacho (la
 * primera es la siguiente que hay que hacer) y, sin puesto, la más antigua
 * primero. Las cerradas (hechas o descartadas) van aparte, la última que se
 * cerró primero.
 */
export function ordenarOrdenes(lista: ModificacionResumen[]): { pendientes: ModificacionResumen[]; hechas: ModificacionResumen[] } {
  const pendientes = lista
    .filter((o) => !estaCerrada(o.estado))
    .sort(
      (a, b) =>
        (a.posicionCola ?? Number.MAX_SAFE_INTEGER) - (b.posicionCola ?? Number.MAX_SAFE_INTEGER) ||
        instante(a.creadaEn) - instante(b.creadaEn),
    );
  const hechas = lista
    .filter((o) => estaCerrada(o.estado))
    .sort((a, b) => instante(b.hechaEn ?? b.creadaEn) - instante(a.hechaEn ?? a.creadaEn));
  return { pendientes, hechas };
}

/** «Mis avisos»: el último que se ha mandado primero, como en la maqueta. */
export function ordenarAvisos(lista: ModificacionResumen[]): ModificacionResumen[] {
  return [...lista].sort((a, b) => instante(b.creadaEn) - instante(a.creadaEn));
}

/** La pastilla de una orden en «Bandeja de Entrada». */
export function estadoOrden(estado: EstadoModificacion): { texto: string; pill: string } {
  switch (estado) {
    case 'NUEVA':
      return { texto: 'Nueva', pill: 'nueva' };
    case 'HECHA':
      return { texto: 'Hecha', pill: 'hecha' };
    case 'DESCARTADA':
      return { texto: 'Descartada', pill: 'vista' };
    default:
      return { texto: 'Vista', pill: 'vista' };
  }
}

/**
 * Cómo se ve un aviso en «Mis avisos»: el texto y el color de su pastilla, y
 * la clase que pinta su franja (azul enviado, ámbar visto, verde hecho).
 */
export function estadoAviso(estado: EstadoModificacion): { texto: string; pill: string; franja: string } {
  switch (estado) {
    case 'NUEVA':
      return { texto: 'Enviado', pill: 'enviado', franja: 'aviso-enviado' };
    case 'HECHA':
      return { texto: 'Hecho', pill: 'hecha', franja: 'aviso-hecho' };
    case 'DESCARTADA':
      return { texto: 'Descartado', pill: 'vista', franja: 'aviso-descartado' };
    default:
      return { texto: 'Visto', pill: 'vista', franja: 'aviso-visto' };
  }
}

/** Si ha llegado alguna orden nueva entre dos consultas de la bandeja (la primera no cuenta: es lo que ya había). */
export function llegaronNuevas(antes: number | null, ahora: number): boolean {
  return antes !== null && ahora > antes;
}

/**
 * Lo que FotoModelo necesita del modelo de una modificación. El resumen no
 * dice si es bolso o cinturón: sin foto principal sale la silueta de bolso.
 */
export function fotoDelModelo(r: ModificacionResumen): { id: number; referencia: string; tipo: 'BOLSO'; fotoPrincipalId: number | null } {
  return { id: r.modeloId, referencia: r.modeloReferencia, tipo: 'BOLSO', fotoPrincipalId: r.fotoPrincipalId };
}

/** «00:35 de voz» o «sin voz»: lo que dura la nota de voz del cuerpo de una modificación. */
export function textoVoz(duracionAudioSeg: number | null): string {
  return duracionAudioSeg === null ? 'sin voz' : `${mmss(duracionAudioSeg)} de voz`;
}

/**
 * La pepita de «Bandeja» en despacho: lo que le toca mirar ya, los avisos
 * nuevos del taller y las órdenes con una duda del taller por contestar.
 * Cero (y sin pepita) mientras no se sabe.
 */
export function numeroBandejaDespacho(resumen: ResumenBandeja | null): number {
  return resumen ? resumen.nuevas + resumen.conRespuesta : 0;
}

/** La pastilla de estado en la bandeja de despacho (avisos y órdenes). */
export function estadoModificacion(estado: EstadoModificacion): { texto: string; pill: string } {
  switch (estado) {
    case 'NUEVA':
      return { texto: 'Nueva', pill: 'nueva' };
    case 'VISTA':
      return { texto: 'Vista', pill: 'vista' };
    case 'EN_CURSO':
      return { texto: 'En curso', pill: 'en-curso' };
    case 'HECHA':
      return { texto: 'Hecha', pill: 'hecha' };
    default:
      return { texto: 'Descartada', pill: 'descartada' };
  }
}

/** Los chips de estado de la bandeja de despacho. */
export type FiltroEstado = 'ABIERTAS' | 'NUEVAS' | 'HECHAS' | 'DESCARTADAS' | 'TODAS';

/** Los estados que pide cada chip a la API; vacío = todos (la API pone entonces las cerradas al final). */
export function estadosDelFiltro(filtro: FiltroEstado): EstadoModificacion[] {
  switch (filtro) {
    case 'ABIERTAS':
      return ['NUEVA', 'VISTA', 'EN_CURSO'];
    case 'NUEVAS':
      return ['NUEVA'];
    case 'HECHAS':
      return ['HECHA'];
    case 'DESCARTADAS':
      return ['DESCARTADA'];
    default:
      return [];
  }
}

/** La marca pequeña de un destino en la fila de un aviso: ✓ aplicado, – por aplicar o no aplica. */
export function marcaDestino(d: DestinoEstado): { simbolo: string; estado: 'hecho' | 'pendiente' | 'no-aplica'; texto: string } {
  if (!d.aplica) return { simbolo: '–', estado: 'no-aplica', texto: `${d.texto}: no aplica` };
  if (d.hechoEn !== null) return { simbolo: '✓', estado: 'hecho', texto: `${d.texto}: aplicado` };
  return { simbolo: '–', estado: 'pendiente', texto: `${d.texto}: por aplicar` };
}

/** Si alguna nota de voz del detalle se está transcribiendo todavía: mientras tanto el detalle se vuelve a pedir solo. */
export function hayTranscripcionPendiente(detalle: ModificacionDetalle): boolean {
  return detalle.mensajes.some((m) => m.adjuntos.some((a) => a.tipo === 'AUDIO' && a.estadoTranscripcion === 'PENDIENTE'));
}

/**
 * Si el despacho puede corregir el título desde la cabecera: el que ha puesto
 * la IA y, en un aviso que se ha quedado sin él (sin IA configurada), ponerlo.
 */
export function tituloEditable(r: ModificacionResumen): boolean {
  return r.tituloDeIa || (r.direccion === 'TALLER_A_DESPACHO' && !r.titulo);
}

/**
 * Los destinos de un aviso se pueden tocar mientras no esté descartado: en uno
 * hecho, desmarcar un destino lo devuelve a «en curso» (lo hace el servidor).
 */
export function destinosEditables(estado: EstadoModificacion): boolean {
  return estado !== 'DESCARTADA';
}

/**
 * La frase del inicio del taller, debajo del saludo: las órdenes nuevas si
 * las hay; si no, las que siguen abiertas (vistas o empezadas), que también
 * son trabajo por hacer; y solo sin ninguna, «No tienes nada pendiente».
 */
export function fraseInicio(resumen: ResumenBandeja | null): string {
  const nuevas = resumen?.nuevas ?? 0;
  const pendientes = resumen?.pendientes ?? 0;
  if (nuevas > 0) {
    return `Tienes ${nuevas} ${nuevas === 1 ? 'orden nueva' : 'órdenes nuevas'} de despacho.`;
  }
  if (pendientes > 0) {
    return `Tienes ${pendientes} ${pendientes === 1 ? 'orden' : 'órdenes'} por hacer.`;
  }
  return 'No tienes nada pendiente.';
}

/**
 * El título que se enseña de una modificación: el suyo o, sin él (un aviso
 * cuya nota de voz todavía no ha resumido la IA), el tipo de cambio y el
 * modelo, «Piel · ULL027». La misma regla en las dos caras: nunca «Sin título».
 */
export function tituloVisible(m: Pick<ModificacionResumen, 'titulo' | 'tipoCambioTexto' | 'modeloReferencia'>): string {
  return m.titulo ?? `${m.tipoCambioTexto ?? 'Cambio'} · ${m.modeloReferencia}`;
}

/** «1ª», «12ª»: el puesto en la cola como se dice. */
export function puesto(n: number): string {
  return `${n}ª`;
}

/** Cómo se mueve una orden en la cola con los botones. */
export type Movimiento = 'primera' | 'arriba' | 'abajo' | 'ultima';

/**
 * La cola (los ids, de la primera a la última) con esa orden movida; la
 * misma lista si no está o ya no se puede mover hacia allí. No toca la que
 * recibe.
 */
export function moverEnCola(ids: number[], id: number, movimiento: Movimiento): number[] {
  const i = ids.indexOf(id);
  if (i < 0) return ids;
  const destino = { primera: 0, arriba: i - 1, abajo: i + 1, ultima: ids.length - 1 }[movimiento];
  if (destino < 0 || destino >= ids.length || destino === i) return ids;
  const nueva = ids.filter((x) => x !== id);
  nueva.splice(destino, 0, id);
  return nueva;
}

/**
 * Lo que deja arrastrar una orden y soltarla sobre otra: queda delante de
 * ella si venía de más abajo y detrás si venía de más arriba, que es donde
 * se ve el hueco al arrastrar. La misma lista si alguna no está.
 */
export function soltarSobre(ids: number[], arrastrada: number, sobre: number): number[] {
  const desde = ids.indexOf(arrastrada);
  const hasta = ids.indexOf(sobre);
  if (desde < 0 || hasta < 0 || desde === hasta) return ids;
  const nueva = ids.filter((x) => x !== arrastrada);
  nueva.splice(hasta, 0, arrastrada);
  return nueva;
}

/** Los filtros de la cola del despacho, como los de los modelos; vacío = sin ese filtro. */
export type FiltroCola = { texto: string; cliente: string; etapa: Etapa | '' };

/** Con algún filtro puesto la vista es parcial y no se deja cambiar el orden. */
export function colaFiltrada(filtro: FiltroCola): boolean {
  return filtro.texto.trim() !== '' || filtro.cliente !== '' || filtro.etapa !== '';
}

function paraBuscar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
}

/** Las órdenes de la cola que pasan el filtro: el texto, dentro de la referencia, el nombre del modelo o el título. */
export function filtrarCola(cola: ModificacionResumen[], filtro: FiltroCola): ModificacionResumen[] {
  const buscado = paraBuscar(filtro.texto);
  return cola.filter(
    (m) =>
      (filtro.cliente === '' || m.cliente === filtro.cliente) &&
      (filtro.etapa === '' || m.etapa === filtro.etapa) &&
      (buscado === '' || [m.modeloReferencia, m.modeloNombre, m.titulo].some((c) => c !== null && paraBuscar(c).includes(buscado))),
  );
}

/** Dónde puede ir una orden nueva: «Al final» (sin antesDe), «La primera» y «Antes de la 2ª · ULL770 · Cambiar el forro»… */
export function opcionesPosicion(cola: ModificacionResumen[]): { valor: string; texto: string }[] {
  if (cola.length === 0) return [{ valor: '', texto: 'La primera (la cola está vacía)' }];
  return [
    { valor: '', texto: `Al final (${puesto(cola.length + 1)})` },
    ...cola.map((m, i) => ({
      valor: String(m.id),
      texto: i === 0 ? `La primera, antes de ${m.modeloReferencia} · ${tituloVisible(m)}` : `Antes de la ${puesto(i + 1)} · ${m.modeloReferencia} · ${tituloVisible(m)}`,
    })),
  ];
}
