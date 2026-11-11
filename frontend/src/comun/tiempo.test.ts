import { describe, expect, it } from 'vitest';
import { diaYMes, diasDesde, diasHasta, hace, mmss } from './tiempo';

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

describe('diasHasta', () => {
  it('cuenta días de calendario, no horas', () => {
    expect(diasHasta('2026-11-09', ahora)).toBe(0);
    expect(diasHasta('2026-11-10', new Date('2026-11-09T23:59:00'))).toBe(1);
    expect(diasHasta('2026-11-14', ahora)).toBe(5);
    expect(diasHasta('2026-11-02', ahora)).toBe(-7);
  });

  it('admite una fecha con hora y no pasa por UTC', () => {
    expect(diasHasta('2026-11-10T00:30:00', ahora)).toBe(1);
  });
});

describe('diasDesde', () => {
  it('días enteros, y nunca negativos', () => {
    expect(diasDesde('2026-11-06T13:00:00', ahora)).toBe(2);
    expect(diasDesde('2026-11-09T10:00:00', ahora)).toBe(0);
    expect(diasDesde('2026-11-10T10:00:00', ahora)).toBe(0);
  });
});

describe('diaYMes', () => {
  it('día y mes en palabras, y el año solo si no es el de hoy', () => {
    expect(diaYMes('2026-11-11', ahora)).toBe('11 noviembre');
    expect(diaYMes('2027-01-03', ahora)).toBe('3 enero 2027');
  });
});
