// Cómo se dice el tiempo en la app: «hace 3 horas» en las listas y «02:05»
// en las notas de voz. Funciones puras, sin depender del reloj si se les pasa
// `ahora`, para poder probarlas.

const MINUTO = 60;
const HORA = 60 * MINUTO;
const DIA = 24 * HORA;
const SEMANA = 7 * DIA;

function plural(n: number, uno: string, varios: string): string {
  return `${n} ${n === 1 ? uno : varios}`;
}

function dosCifras(n: number): string {
  return String(n).padStart(2, '0');
}

/**
 * Cuánto hace de `fechaIso`, como lo diría una persona. Hasta cuatro semanas
 * en palabras; más allá, la fecha (`el 12/03/2026`), porque «hace 7 semanas»
 * obliga a hacer la cuenta. Una fecha en el futuro (relojes desajustados) es
 * «ahora mismo».
 */
export function hace(fechaIso: string, ahora: Date = new Date()): string {
  const fecha = new Date(fechaIso);
  const segundos = Math.floor((ahora.getTime() - fecha.getTime()) / 1000);
  if (segundos < MINUTO) return 'ahora mismo';
  if (segundos < HORA) return `hace ${Math.floor(segundos / MINUTO)} min`;
  if (segundos < DIA) return `hace ${plural(Math.floor(segundos / HORA), 'hora', 'horas')}`;
  if (segundos < SEMANA) return `hace ${plural(Math.floor(segundos / DIA), 'día', 'días')}`;
  if (segundos < 4 * SEMANA) return `hace ${plural(Math.floor(segundos / SEMANA), 'semana', 'semanas')}`;
  return `el ${dosCifras(fecha.getDate())}/${dosCifras(fecha.getMonth() + 1)}/${fecha.getFullYear()}`;
}

/** Segundos como `mm:ss` (125 → `02:05`). */
export function mmss(segundos: number): string {
  const total = Math.max(0, Math.floor(segundos));
  return `${dosCifras(Math.floor(total / 60))}:${dosCifras(total % 60)}`;
}
