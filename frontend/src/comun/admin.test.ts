import { describe, expect, it } from 'vitest';
import { estadoDispositivo, type Dispositivo } from './admin';

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
