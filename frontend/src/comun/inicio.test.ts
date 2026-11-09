import { describe, expect, it } from 'vitest';
import { fraseInicio, tituloVisible, type ResumenBandeja } from './modificaciones';

const resumen = (nuevas: number, pendientes: number): ResumenBandeja => ({
  nuevas,
  pendientes,
  vencidas: 0,
  conRespuesta: 0,
  sinPrioridad: 0,
});

describe('fraseInicio', () => {
  it('cuenta las órdenes nuevas cuando las hay', () => {
    expect(fraseInicio(resumen(1, 3))).toBe('Tienes 1 orden nueva de despacho.');
    expect(fraseInicio(resumen(2, 2))).toBe('Tienes 2 órdenes nuevas de despacho.');
  });

  it('sin nuevas pero con órdenes abiertas, dice cuántas quedan por hacer', () => {
    expect(fraseInicio(resumen(0, 1))).toBe('Tienes 1 orden por hacer.');
    expect(fraseInicio(resumen(0, 4))).toBe('Tienes 4 órdenes por hacer.');
  });

  it('solo sin nada abierto (o sin saberlo todavía) no hay nada pendiente', () => {
    expect(fraseInicio(resumen(0, 0))).toBe('No tienes nada pendiente.');
    expect(fraseInicio(null)).toBe('No tienes nada pendiente.');
  });
});

describe('tituloVisible', () => {
  it('el título si lo hay; si no, el tipo de cambio y el modelo', () => {
    expect(tituloVisible({ titulo: 'Cambiar asa', tipoCambioTexto: 'Herrajes', modeloReferencia: 'ULL027' })).toBe('Cambiar asa');
    expect(tituloVisible({ titulo: null, tipoCambioTexto: 'Piel', modeloReferencia: 'ULL027' })).toBe('Piel · ULL027');
    expect(tituloVisible({ titulo: null, tipoCambioTexto: null, modeloReferencia: 'PROTO A' })).toBe('Cambio · PROTO A');
  });
});
