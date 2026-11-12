import { describe, expect, it } from 'vitest';
import { cambioDeUsuario, nombreDelAparato } from './sesion';

describe('cambioDeUsuario', () => {
  it('el mismo usuario con el mismo rol no ha cambiado', () => {
    expect(cambioDeUsuario({ id: 2, rol: 'TALLER' }, { id: 2, rol: 'TALLER' })).toBe(false);
  });

  it('otro usuario en otra pestaña del mismo navegador sí (la tablet atendida como el admin)', () => {
    expect(cambioDeUsuario({ id: 2, rol: 'TALLER' }, { id: 1, rol: 'ADMIN' })).toBe(true);
  });

  it('el mismo usuario con otro rol también, porque cambia de cara', () => {
    expect(cambioDeUsuario({ id: 3, rol: 'DESPACHO' }, { id: 3, rol: 'TALLER' })).toBe(true);
  });
});

describe('nombreDelAparato', () => {
  it('dice el sistema y el navegador', () => {
    expect(
      nombreDelAparato('Mozilla/5.0 (Linux; Android 14; SM-X200) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36'),
    ).toBe('Tablet Android · Chrome');
    expect(
      nombreDelAparato(
        'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36',
      ),
    ).toBe('Móvil Android · Chrome');
    expect(
      nombreDelAparato(
        'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1',
      ),
    ).toBe('iPhone · Safari');
    expect(
      nombreDelAparato(
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0',
      ),
    ).toBe('Windows · Edge');
  });

  it('si no lo reconoce, el principio del texto', () => {
    expect(nombreDelAparato('RaroBot 1.0')).toBe('RaroBot 1.0');
    expect(nombreDelAparato('')).toBe('Sin nombre');
  });
});
