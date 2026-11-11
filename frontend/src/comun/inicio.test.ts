import { describe, expect, it } from 'vitest';
import { tituloVisible } from './modificaciones';

describe('tituloVisible', () => {
  it('el título si lo hay; si no, el tipo de cambio y el modelo', () => {
    expect(tituloVisible({ titulo: 'Cambiar asa', tipoCambioTexto: 'Fornituras', modeloReferencia: 'ULL027' })).toBe('Cambiar asa');
    expect(tituloVisible({ titulo: null, tipoCambioTexto: 'Consumo piel · Fornituras', modeloReferencia: 'ULL027' })).toBe('Consumo piel · Fornituras · ULL027');
    expect(tituloVisible({ titulo: null, tipoCambioTexto: null, modeloReferencia: 'PROTO A' })).toBe('Cambio · PROTO A');
  });
});
