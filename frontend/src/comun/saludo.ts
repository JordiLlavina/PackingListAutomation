// El saludo de la pantalla de inicio del taller, que cambia según la hora y el
// día para que la app sea más amigable. Las frases y sus franjas las eligió el
// usuario (noviembre de 2026). Cada vez que se abre el inicio se elige una:
//
//   1. Si hay frases de franja que valen ahora (buen provecho, buen fin de
//      semana…), sale una de ellas 1 de cada 2 veces.
//   2. Si no, 1 de cada 4 veces sale una divertida, al azar y sin repetir la
//      última, pero no más de 6 en 24 horas. Las 24 horas son una ventana que
//      se desliza: el contador no vuelve a cero a medianoche.
//   3. Si no, una típica de las que valen a esa hora.
//
// `{n}` es el nombre de quien ha entrado; sin nombre se quita con su coma.

export type Frase = {
  texto: string;
  /** Cuándo vale: sin él, siempre. `minutos` son los del día (13:30 = 810). */
  vale?: (dia: number, minutos: number) => boolean;
};

const LUNES = 1;
const VIERNES = 5;
const h = (horas: number, minutos = 0) => horas * 60 + minutos;

export const TIPICAS: Frase[] = [
  { texto: 'Buenos días, {n}', vale: (_d, m) => m < h(14) },
  { texto: 'Buen día, {n}', vale: (_d, m) => m < h(14) },
  { texto: 'Buenas tardes, {n}', vale: (_d, m) => m >= h(14) },
  { texto: 'Hola, {n}' },
  { texto: 'Hola de nuevo, {n}' },
  { texto: '¿Qué tal, {n}?' },
  { texto: 'Bienvenido, {n}' },
];

export const DE_FRANJA: Frase[] = [
  { texto: 'Buen comienzo de día, {n}', vale: (_d, m) => m < h(10, 30) },
  { texto: '¡Buen provecho, {n}!', vale: (_d, m) => m >= h(13, 30) && m < h(15, 30) },
  { texto: 'Feliz viernes, {n}', vale: (d) => d === VIERNES },
  // El viernes se acaba a las 14 h.
  { texto: 'Buen fin de semana, {n}', vale: (d, m) => d === VIERNES && m >= h(12, 45) },
  { texto: 'Los bolsos ya preguntaban por ti', vale: (d, m) => d === LUNES && m < h(12, 30) },
];

export const DIVERTIDAS: Frase[] = [
  { texto: '¡Ya llegó el jefe de los bolsos!' },
  { texto: '¿Café primero o bolsos primero?' },
  { texto: 'Cuidado, que hoy el cuero está de buen humor' },
  { texto: 'No hay costuras torcidas, solo costuras con personalidad' },
  { texto: 'Con ese talento, hoy sale todo redondo' },
  { texto: '¡Mediodía, tarde y gol!', vale: (_d, m) => m >= h(17) },
  { texto: 'Modo artista: activado' },
  { texto: 'Si el cuero hablara, te daría las gracias' },
  { texto: 'Segunda parte del partido, ¡y vamos ganando!', vale: (_d, m) => m >= h(15) },
  { texto: '¿En qué andamos hoy?' },
  { texto: '¡Hoy llevas el guapo subido!' },
];

export const PROB_FRANJA = 1 / 2;
export const PROB_DIVERTIDA = 1 / 4;
export const MAX_DIVERTIDAS = 6;
export const VENTANA_MS = 24 * 60 * 60 * 1000;

/** Las divertidas que ya han salido: cuándo, y la última, para no repetirla. */
export type HistorialDivertidas = { veces: number[]; ultima: string | null };

export type Eleccion = { plantilla: string; divertida: boolean };

function valenAhora(frases: Frase[], ahora: Date): Frase[] {
  const dia = ahora.getDay();
  const minutos = ahora.getHours() * 60 + ahora.getMinutes();
  return frases.filter((f) => !f.vale || f.vale(dia, minutos));
}

function unaAlAzar(lista: Frase[], azar: () => number): Frase {
  // Las típicas sin hora («Hola, {n}») valen siempre, así que nunca está vacía.
  return lista[Math.min(lista.length - 1, Math.floor(azar() * lista.length))] ?? { texto: 'Hola, {n}' };
}

/** Las divertidas de las últimas 24 horas que cuentan para el tope. */
export function recientes(historial: HistorialDivertidas, ahora: Date): number[] {
  const desde = ahora.getTime() - VENTANA_MS;
  return historial.veces.filter((t) => t > desde && t <= ahora.getTime());
}

/** Elige la plantilla del saludo. `azar` es inyectable para los tests. */
export function elegirSaludo(ahora: Date, historial: HistorialDivertidas, azar: () => number = Math.random): Eleccion {
  const franja = valenAhora(DE_FRANJA, ahora);
  if (franja.length > 0 && azar() < PROB_FRANJA) {
    return { plantilla: unaAlAzar(franja, azar).texto, divertida: false };
  }
  if (azar() < PROB_DIVERTIDA && recientes(historial, ahora).length < MAX_DIVERTIDAS) {
    const posibles = valenAhora(DIVERTIDAS, ahora);
    const sinRepetir = posibles.filter((f) => f.texto !== historial.ultima);
    const candidatas = sinRepetir.length > 0 ? sinRepetir : posibles;
    if (candidatas.length > 0) {
      return { plantilla: unaAlAzar(candidatas, azar).texto, divertida: true };
    }
  }
  return { plantilla: unaAlAzar(valenAhora(TIPICAS, ahora), azar).texto, divertida: false };
}

/** El historial después de enseñar una divertida, sin lo que ya no cuenta. */
export function apuntarDivertida(historial: HistorialDivertidas, plantilla: string, ahora: Date): HistorialDivertidas {
  return { veces: [...recientes(historial, ahora), ahora.getTime()], ultima: plantilla };
}

export function conNombre(plantilla: string, nombre: string | null | undefined): string {
  const limpio = nombre?.trim();
  return limpio ? plantilla.replace('{n}', limpio) : plantilla.replace(', {n}', '').replace('{n}', '');
}
