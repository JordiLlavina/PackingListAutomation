import { describe, expect, it } from 'vitest';
import {
  apuntarSaludo,
  claveDia,
  conNombre,
  DE_FRANJA,
  DIVERTIDAS,
  elegirSaludo,
  HISTORIAL_VACIO,
  HOLA,
  SEPARACION_FRANJAS_MS,
  type HistorialSaludo,
} from './saludo';

// 2026-11-09 es lunes, el 11 miércoles y el 13 viernes (hora local).
const fecha = (dia: number, horas: number, minutos = 0) => new Date(2026, 10, dia, horas, minutos);
/** Devuelve los valores en orden y repite el último. */
const secuencia = (...valores: number[]) => {
  let i = 0;
  return () => valores[Math.min(i++, valores.length - 1)] ?? 0;
};
const textos = (frases: { texto: string }[]) => frases.map((f) => f.texto);

describe('elegirSaludo', () => {
  it('una frase de franja que vale y no ha salido hoy sale siempre, en lugar del saludo', () => {
    // Miércoles 9:00: solo «Buen comienzo de día». Da igual el azar.
    for (const r of [0, 0.5, 0.99]) {
      expect(elegirSaludo(fecha(11, 9), HISTORIAL_VACIO, secuencia(r))).toEqual({
        plantilla: 'Buen comienzo de día, {n}',
        tipo: 'franja',
      });
    }
  });

  it('cada frase de franja sale una sola vez al día', () => {
    const tras = apuntarSaludo(HISTORIAL_VACIO, { plantilla: 'Buen comienzo de día, {n}', tipo: 'franja' }, fecha(11, 8));
    // Pasadas las 4 horas ya no está en su franja; dentro de ella, tampoco repite.
    const sinCuatroHoras: HistorialSaludo = { ...tras, ultimaFranja: null };
    expect(elegirSaludo(fecha(11, 10), sinCuatroHoras, secuencia(0.9)).tipo).toBe('hola');
    // Al día siguiente vuelve.
    expect(elegirSaludo(fecha(12, 9), tras, secuencia(0.9)).tipo).toBe('franja');
  });

  it('nunca dos frases de franja con menos de 4 horas entre ellas', () => {
    // Viernes: «Feliz viernes» a las 10:00; a las 13:00 vale «Buen fin de semana» pero espera.
    const tras = apuntarSaludo(HISTORIAL_VACIO, { plantilla: 'Feliz viernes, {n}', tipo: 'franja' }, fecha(13, 10));
    expect(elegirSaludo(fecha(13, 13), tras, secuencia(0.9)).tipo).toBe('hola');
    const cuatroHorasDespues = new Date(fecha(13, 10).getTime() + SEPARACION_FRANJAS_MS);
    expect(elegirSaludo(cuatroHorasDespues, tras, secuencia(0.9))).toEqual({
      plantilla: 'Buen fin de semana, {n}',
      tipo: 'franja',
    });
  });

  it('sin franja, 1 de cada 6 una divertida y si no «Hola»', () => {
    const divertida = elegirSaludo(fecha(11, 11), HISTORIAL_VACIO, secuencia(0.1, 0));
    expect(divertida.tipo).toBe('divertida');
    expect(textos(DIVERTIDAS)).toContain(divertida.plantilla);
    expect(elegirSaludo(fecha(11, 11), HISTORIAL_VACIO, secuencia(1 / 6 + 0.01))).toEqual({ plantilla: HOLA, tipo: 'hola' });
    expect(elegirSaludo(fecha(11, 16), HISTORIAL_VACIO, secuencia(0.99))).toEqual({ plantilla: HOLA, tipo: 'hola' });
  });

  it('no repite la última divertida', () => {
    const ultima = '¡Ya llegó el jefe de los bolsos!';
    expect(textos(DIVERTIDAS)).toContain(ultima);
    const historial: HistorialSaludo = { ...HISTORIAL_VACIO, ultimaDivertida: ultima };
    for (let r = 0; r < 1; r += 0.05) {
      expect(elegirSaludo(fecha(11, 11), historial, secuencia(0.1, r)).plantilla).not.toBe(ultima);
    }
  });

  it('las divertidas con hora solo salen a partir de su hora', () => {
    const todas = (cuando: Date) => {
      const vistas = new Set<string>();
      for (let r = 0; r < 1; r += 0.01) vistas.add(elegirSaludo(cuando, HISTORIAL_VACIO, secuencia(0.1, r)).plantilla);
      return vistas;
    };
    expect(todas(fecha(11, 11))).not.toContain('¡Mediodía, tarde y gol!');
    expect(todas(fecha(11, 11))).not.toContain('Segunda parte del partido, ¡y vamos ganando!');
    expect(todas(fecha(11, 17, 30))).toContain('¡Mediodía, tarde y gol!');
    expect(todas(fecha(11, 16))).toContain('Segunda parte del partido, ¡y vamos ganando!');
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

describe('apuntarSaludo', () => {
  it('una de franja apunta el día y la hora, y olvida las de otros días', () => {
    const ayer: HistorialSaludo = { ...HISTORIAL_VACIO, franjas: { '¡Buen provecho, {n}!': '2026-11-10' } };
    const ahora = fecha(11, 9);
    expect(apuntarSaludo(ayer, { plantilla: 'Buen comienzo de día, {n}', tipo: 'franja' }, ahora)).toEqual({
      ultimaDivertida: null,
      ultimaFranja: ahora.getTime(),
      franjas: { 'Buen comienzo de día, {n}': '2026-11-11' },
    });
  });

  it('una divertida apunta cuál fue y «Hola» no apunta nada', () => {
    expect(apuntarSaludo(HISTORIAL_VACIO, { plantilla: 'Modo artista: activado', tipo: 'divertida' }, fecha(11, 11))).toEqual({
      ...HISTORIAL_VACIO,
      ultimaDivertida: 'Modo artista: activado',
    });
    expect(apuntarSaludo(HISTORIAL_VACIO, { plantilla: HOLA, tipo: 'hola' }, fecha(11, 11))).toBe(HISTORIAL_VACIO);
  });

  it('el día es el local', () => {
    expect(claveDia(fecha(11, 0, 5))).toBe('2026-11-11');
    expect(claveDia(fecha(11, 23, 55))).toBe('2026-11-11');
  });
});

describe('conNombre', () => {
  it('pone el nombre o quita el hueco con su coma', () => {
    expect(conNombre('¡Buen provecho, {n}!', 'Bachir')).toBe('¡Buen provecho, Bachir!');
    expect(conNombre('¡Buen provecho, {n}!', undefined)).toBe('¡Buen provecho!');
    expect(conNombre(HOLA, ' ')).toBe('Hola');
    expect(conNombre('Modo artista: activado', 'Bachir')).toBe('Modo artista: activado');
  });
});
