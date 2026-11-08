import { describe, expect, it } from 'vitest';
import { hace, mmss } from './tiempo';

// Fechas sin zona: se leen en la hora local, así que el día no cambia según
// dónde corran los tests.
const ahora = new Date('2026-11-09T12:00:00');
const antes = (segundos: number) => new Date(ahora.getTime() - segundos * 1000).toISOString();

describe('hace', () => {
  it('por debajo de un minuto es «ahora mismo»', () => {
    expect(hace(antes(0), ahora)).toBe('ahora mismo');
    expect(hace(antes(59), ahora)).toBe('ahora mismo');
  });

  it('en minutos', () => {
    expect(hace(antes(5 * 60), ahora)).toBe('hace 5 min');
  });

  it('en horas', () => {
    expect(hace(antes(2 * 3600), ahora)).toBe('hace 2 horas');
    expect(hace(antes(3600), ahora)).toBe('hace 1 hora');
  });

  it('en días, en singular y en plural', () => {
    expect(hace(antes(24 * 3600), ahora)).toBe('hace 1 día');
    expect(hace(antes(3 * 24 * 3600), ahora)).toBe('hace 3 días');
  });

  it('en semanas hasta cuatro', () => {
    expect(hace(antes(14 * 24 * 3600), ahora)).toBe('hace 2 semanas');
  });

  it('más allá de cuatro semanas, la fecha', () => {
    expect(hace('2026-03-12T10:30:00', ahora)).toBe('el 12/03/2026');
  });
});

describe('mmss', () => {
  it('minutos y segundos con dos cifras', () => {
    expect(mmss(125)).toBe('02:05');
  });

  it('cero', () => {
    expect(mmss(0)).toBe('00:00');
  });
});
