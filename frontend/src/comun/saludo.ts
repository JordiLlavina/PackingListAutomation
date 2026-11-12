// El saludo de la pantalla de inicio del taller. Las frases y sus franjas las
// eligió el usuario (noviembre de 2026), y también la regla, porque la primera
// versión cambiaba demasiado. Cada vez que se abre el inicio:
//
//   1. Si vale ahora una frase de franja (buen provecho, buen fin de semana…)
//      que hoy todavía no ha salido, sale ella en lugar del saludo: una sola
//      vez al día cada una, y nunca dos de franja con menos de 4 horas entre
//      ellas (la que no cabe espera a que pasen las 4 horas, si su franja aún
//      dura).
//   2. Si no, 1 de cada 6 veces una divertida, al azar y sin repetir la última.
//   3. Si no, «Hola, {n}».
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

export const HOLA = 'Hola, {n}';

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

export const PROB_DIVERTIDA = 1 / 6;
/** Lo mínimo entre dos frases de franja. */
export const SEPARACION_FRANJAS_MS = 4 * 60 * 60 * 1000;

/** Lo que ya ha salido en esta tablet. */
export type HistorialSaludo = {
  /** La última divertida, para no repetirla. */
  ultimaDivertida: string | null;
  /** Cuándo salió la última frase de franja (ms), para las 4 horas. */
  ultimaFranja: number | null;
  /** El día (`YYYY-MM-DD` local) en que salió cada frase de franja, para que salga una vez al día. */
  franjas: Record<string, string>;
};

export const HISTORIAL_VACIO: HistorialSaludo = { ultimaDivertida: null, ultimaFranja: null, franjas: {} };

export type Eleccion = { plantilla: string; tipo: 'hola' | 'franja' | 'divertida' };

function valenAhora(frases: Frase[], ahora: Date): Frase[] {
  const dia = ahora.getDay();
  const minutos = ahora.getHours() * 60 + ahora.getMinutes();
  return frases.filter((f) => !f.vale || f.vale(dia, minutos));
}

function unaAlAzar(lista: Frase[], azar: () => number): Frase | undefined {
  if (lista.length === 0) return undefined;
  return lista[Math.min(lista.length - 1, Math.floor(azar() * lista.length))];
}

/** El día local de `ahora` como `YYYY-MM-DD` (no `toISOString`, que es UTC y cambia de día a deshora). */
export function claveDia(ahora: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${ahora.getFullYear()}-${dos(ahora.getMonth() + 1)}-${dos(ahora.getDate())}`;
}

/** Las frases de franja que podrían salir ahora mismo. */
export function franjasPosibles(ahora: Date, historial: HistorialSaludo): Frase[] {
  if (historial.ultimaFranja !== null && ahora.getTime() - historial.ultimaFranja < SEPARACION_FRANJAS_MS) return [];
  const hoy = claveDia(ahora);
  return valenAhora(DE_FRANJA, ahora).filter((f) => historial.franjas[f.texto] !== hoy);
}

/** Elige la plantilla del saludo. `azar` es inyectable para los tests. */
export function elegirSaludo(ahora: Date, historial: HistorialSaludo, azar: () => number = Math.random): Eleccion {
  const franja = unaAlAzar(franjasPosibles(ahora, historial), azar);
  if (franja) return { plantilla: franja.texto, tipo: 'franja' };
  if (azar() < PROB_DIVERTIDA) {
    const posibles = valenAhora(DIVERTIDAS, ahora);
    const sinRepetir = posibles.filter((f) => f.texto !== historial.ultimaDivertida);
    const divertida = unaAlAzar(sinRepetir.length > 0 ? sinRepetir : posibles, azar);
    if (divertida) return { plantilla: divertida.texto, tipo: 'divertida' };
  }
  return { plantilla: HOLA, tipo: 'hola' };
}

/** El historial después de enseñar `eleccion`. */
export function apuntarSaludo(historial: HistorialSaludo, eleccion: Eleccion, ahora: Date): HistorialSaludo {
  switch (eleccion.tipo) {
    case 'franja': {
      // Solo hace falta recordar las de hoy: las de otros días ya no cuentan.
      const hoy = claveDia(ahora);
      const deHoy = Object.fromEntries(Object.entries(historial.franjas).filter(([, dia]) => dia === hoy));
      return { ...historial, ultimaFranja: ahora.getTime(), franjas: { ...deHoy, [eleccion.plantilla]: hoy } };
    }
    case 'divertida':
      return { ...historial, ultimaDivertida: eleccion.plantilla };
    default:
      return historial;
  }
}

export function conNombre(plantilla: string, nombre: string | null | undefined): string {
  const limpio = nombre?.trim();
  return limpio ? plantilla.replace('{n}', limpio) : plantilla.replace(', {n}', '').replace('{n}', '');
}
