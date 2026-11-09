// Lo que decide el recorrido de «He cambiado algo» sin mirar la pantalla:
// cuándo se puede mandar a despacho, qué falta si no, y en qué paso de la
// barra de progreso se está. Funciones puras, para poder probarlas.

export type FotoBorrador = { blob: Blob; url: string };
export type AudioBorrador = { blob: Blob; duracionSeg: number; url: string };

/** Lo que lleva el aviso mientras se prepara (sin los setters del contexto). */
export type EstadoBorrador = {
  modelo: { id: number } | null;
  tipo: string | null;
  fotos: FotoBorrador[];
  audio: AudioBorrador | null;
};

/** Por qué no se puede mandar todavía, en el orden en que se pide; null si se puede. */
export function motivoNoEnviar(b: EstadoBorrador): string | null {
  if (!b.modelo) return 'Elige el modelo';
  if (!b.tipo) return 'Di qué has cambiado';
  if (b.fotos.length === 0 && !b.audio) return 'Hace falta una nota de voz o una foto';
  return null;
}

/** Hace falta el modelo, el tipo de cambio y al menos una foto o la nota de voz. */
export function puedeEnviar(b: EstadoBorrador): boolean {
  return motivoNoEnviar(b) === null;
}

const PASOS: Record<string, number> = {
  '/cambio': 1,
  '/cambio/tipo': 2,
  '/cambio/fotos': 3,
  '/cambio/voz': 4,
};

/** El tramo de la barra de 4 pasos (1 a 4); revisar y enviado no llevan barra (null). */
export function pasoActual(ruta: string): number | null {
  const limpia = ruta.length > 1 ? ruta.replace(/\/+$/, '') : ruta;
  return PASOS[limpia] ?? null;
}
