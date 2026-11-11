import { describe, expect, it } from 'vitest';
import { cambioDeUsuario } from './sesion';

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
