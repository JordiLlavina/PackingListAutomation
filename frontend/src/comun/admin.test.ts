import { describe, expect, it } from 'vitest';
import { caducaEn, estadoDispositivo, type Dispositivo } from './admin';

// Fechas sin zona, como las manda el servidor (LocalDateTime): se leen en la
// hora local, así que los tests no dependen de dónde corran.
const ahora = new Date('2026-11-09T12:00:00');

function dispositivo(cambios: Partial<Dispositivo>): Dispositivo {
  return {
    id: 1,
    usuarioId: 2,
    usuario: 'Bachir',
    nombre: 'Chrome en Android',
    alta: '2026-11-01T09:00:00',
    ultimoUso: '2026-11-09T11:00:00',
    revocado: false,
    ...cambios,
  };
}

describe('estadoDispositivo', () => {
  it('revocado manda sobre todo lo demás', () => {
    expect(estadoDispositivo(dispositivo({ revocado: true }))).toBe('revocado');
    expect(estadoDispositivo(dispositivo({ revocado: true, ultimoUso: null }))).toBe('revocado');
  });

  it('sin último uso es «sin usar»', () => {
    expect(estadoDispositivo(dispositivo({ ultimoUso: null }))).toBe('sin usar');
  });

  it('usado y sin revocar es «activo»', () => {
    expect(estadoDispositivo(dispositivo({}))).toBe('activo');
  });
});

describe('caducaEn', () => {
  it('en minutos, redondeando hacia arriba', () => {
    expect(caducaEn('2026-11-09T12:09:00', ahora)).toBe('caduca en 9 min');
    expect(caducaEn('2026-11-09T12:08:30', ahora)).toBe('caduca en 9 min');
    expect(caducaEn('2026-11-09T12:10:00', ahora)).toBe('caduca en 10 min');
  });

  it('el último minuto en segundos', () => {
    expect(caducaEn('2026-11-09T12:00:45', ahora)).toBe('caduca en 45 s');
  });

  it('con la hora ya pasada es «caducado»', () => {
    expect(caducaEn('2026-11-09T12:00:00', ahora)).toBe('caducado');
    expect(caducaEn('2026-11-09T11:50:00', ahora)).toBe('caducado');
  });

  it('acepta las fracciones de segundo del servidor', () => {
    expect(caducaEn('2026-11-09T12:08:59.500000', ahora)).toBe('caduca en 9 min');
  });
});
