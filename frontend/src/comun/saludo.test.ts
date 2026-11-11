import { describe, expect, it } from 'vitest';
import {
  apuntarDivertida,
  conNombre,
  DE_FRANJA,
  DIVERTIDAS,
  elegirSaludo,
  MAX_DIVERTIDAS,
  TIPICAS,
  VENTANA_MS,
  type HistorialDivertidas,
} from './saludo';

// 2026-11-09 es lunes, el 11 miércoles y el 13 viernes (hora local).
const fecha = (dia: number, horas: number, minutos = 0) => new Date(2026, 10, dia, horas, minutos);
const vacio: HistorialDivertidas = { veces: [], ultima: null };
/** Devuelve los valores en orden y repite el último. */
const secuencia = (...valores: number[]) => {
  let i = 0;
  return () => valores[Math.min(i++, valores.length - 1)] ?? 0;
};
const textos = (frases: { texto: string }[]) => frases.map((f) => f.texto);

describe('elegirSaludo', () => {
  it('con una franja que vale, sale de franja si el azar cae en la mitad baja', () => {
    // Miércoles 9:00: solo «Buen comienzo de día».
    expect(elegirSaludo(fecha(11, 9), vacio, secuencia(0.1, 0))).toEqual({
      plantilla: 'Buen comienzo de día, {n}',
      divertida: false,
    });
  });

  it('con una franja que vale y el azar en la mitad alta, sale típica o divertida', () => {
    const e = elegirSaludo(fecha(11, 9), vacio, secuencia(0.9, 0.9, 0));
    expect(textos(TIPICAS)).toContain(e.plantilla);
  });

  it('sin franja, 1 de cada 4 sale una divertida', () => {
    const e = elegirSaludo(fecha(11, 11), vacio, secuencia(0.1, 0));
    expect(e.divertida).toBe(true);
    expect(textos(DIVERTIDAS)).toContain(e.plantilla);
  });

  it('sin franja y sin divertida, una típica de la hora', () => {
    const manana = elegirSaludo(fecha(11, 11), vacio, secuencia(0.9, 0));
    expect(manana.plantilla).toBe('Buenos días, {n}');
    const tarde = elegirSaludo(fecha(11, 16), vacio, secuencia(0.9, 0));
    expect(tarde.plantilla).toBe('Buenas tardes, {n}');
  });

  it('no más de 6 divertidas en las últimas 24 horas, aunque haya cambiado el día', () => {
    // Seis de ayer por la tarde: siguen dentro de la ventana de 24 h.
    const ayer = Array.from({ length: MAX_DIVERTIDAS }, (_, i) => fecha(10, 17, i).getTime());
    const lleno: HistorialDivertidas = { veces: ayer, ultima: null };
    const e = elegirSaludo(fecha(11, 11), lleno, secuencia(0.1, 0));
    expect(e.divertida).toBe(false);
    // Pasadas 24 h desde la más antigua, vuelve a haber hueco (miércoles 17:00, sin franja).
    const despues = new Date(fecha(10, 17).getTime() + VENTANA_MS + 1);
    expect(elegirSaludo(despues, lleno, secuencia(0.1, 0)).divertida).toBe(true);
  });

  it('no repite la última divertida', () => {
    const ultima = '¡Ya llegó el jefe de los bolsos!';
    expect(textos(DIVERTIDAS)).toContain(ultima);
    for (let r = 0; r < 1; r += 0.05) {
      const e = elegirSaludo(fecha(11, 11), { veces: [], ultima }, secuencia(0.1, r));
      expect(e.plantilla).not.toBe(ultima);
    }
  });

  it('las divertidas con hora solo salen a partir de su hora', () => {
    const todas = (cuando: Date) => {
      const vistas = new Set<string>();
      for (let r = 0; r < 1; r += 0.01) vistas.add(elegirSaludo(cuando, vacio, secuencia(0.1, r)).plantilla);
      return vistas;
    };
    expect(todas(fecha(11, 11))).not.toContain('¡Mediodía, tarde y gol!');
    expect(todas(fecha(11, 11))).not.toContain('Segunda parte del partido, ¡y vamos ganando!');
    expect(todas(fecha(11, 17, 30))).toContain('¡Mediodía, tarde y gol!');
    expect(todas(fecha(11, 15, 45))).toContain('Segunda parte del partido, ¡y vamos ganando!');
  });
});

describe('franjas', () => {
  const valen = (cuando: Date) =>
    textos(DE_FRANJA.filter((f) => !f.vale || f.vale(cuando.getDay(), cuando.getHours() * 60 + cuando.getMinutes())));

  it('cada una en su franja', () => {
    expect(valen(fecha(11, 10, 29))).toEqual(['Buen comienzo de día, {n}']);
    expect(valen(fecha(11, 10, 30))).toEqual([]);
    expect(valen(fecha(11, 13, 30))).toEqual(['¡Buen provecho, {n}!']);
    expect(valen(fecha(11, 15, 30))).toEqual([]);
    expect(valen(fecha(13, 11))).toEqual(['Feliz viernes, {n}']);
    expect(valen(fecha(13, 12, 45))).toEqual(['Feliz viernes, {n}', 'Buen fin de semana, {n}']);
    expect(valen(fecha(9, 12, 29))).toEqual(['Los bolsos ya preguntaban por ti']);
    expect(valen(fecha(9, 12, 30))).toEqual([]);
  });
});

describe('apuntarDivertida', () => {
  it('añade la de ahora y olvida las de hace más de 24 horas', () => {
    const ahora = fecha(11, 9);
    const vieja = ahora.getTime() - VENTANA_MS - 1;
    const reciente = ahora.getTime() - 1000;
    expect(apuntarDivertida({ veces: [vieja, reciente], ultima: null }, 'Modo artista: activado', ahora)).toEqual({
      veces: [reciente, ahora.getTime()],
      ultima: 'Modo artista: activado',
    });
  });
});

describe('conNombre', () => {
  it('pone el nombre o quita el hueco con su coma', () => {
    expect(conNombre('¡Buen provecho, {n}!', 'Bachir')).toBe('¡Buen provecho, Bachir!');
    expect(conNombre('¡Buen provecho, {n}!', undefined)).toBe('¡Buen provecho!');
    expect(conNombre('¿Qué tal, {n}?', ' ')).toBe('¿Qué tal?');
    expect(conNombre('Modo artista: activado', 'Bachir')).toBe('Modo artista: activado');
  });
});
