import { describe, expect, it } from 'vitest';
import { dimensionesReducidas } from './imagen';

describe('dimensionesReducidas', () => {
  it('una foto apaisada grande se queda con el lado largo en 1600', () => {
    expect(dimensionesReducidas(4000, 3000, 1600)).toEqual({ ancho: 1600, alto: 1200 });
  });

  it('una foto vertical grande también', () => {
    expect(dimensionesReducidas(3000, 4000, 1600)).toEqual({ ancho: 1200, alto: 1600 });
  });

  it('una foto pequeña se queda como está', () => {
    expect(dimensionesReducidas(800, 600, 1600)).toEqual({ ancho: 800, alto: 600 });
  });

  it('una foto que ya mide justo el máximo no se toca', () => {
    expect(dimensionesReducidas(1600, 1600, 1600)).toEqual({ ancho: 1600, alto: 1600 });
  });
});
