import { describe, expect, it } from 'vitest';
import { POR_HOJA, fechaDia, hojasDe, idsDe, recuento, tamanoNombre } from './etiquetas';

describe('hojasDe', () => {
  it('reparte de 8 en 8: 9 etiquetas son una hoja de 8 y otra de 1', () => {
    const hojas = hojasDe([1, 2, 3, 4, 5, 6, 7, 8, 9]);
    expect(POR_HOJA).toBe(8);
    expect(hojas).toEqual([[1, 2, 3, 4, 5, 6, 7, 8], [9]]);
  });

  it('8 justas son una hoja y ninguna, ninguna hoja', () => {
    expect(hojasDe([1, 2, 3, 4, 5, 6, 7, 8])).toHaveLength(1);
    expect(hojasDe([])).toEqual([]);
  });
});

describe('idsDe', () => {
  it('lee los ids de la dirección en su orden, sin repetidos ni basura', () => {
    expect(idsDe('3,1,3, 7,abc,,0,-2,5.5')).toEqual([3, 1, 7]);
  });

  it('sin parámetro no hay ninguno', () => {
    expect(idsDe(null)).toEqual([]);
    expect(idsDe('')).toEqual([]);
  });
});

describe('tamanoNombre', () => {
  it('un nombre corto va a lo más grande', () => {
    expect(tamanoNombre('LE NEIGE')).toBe('nombre-grande');
    expect(tamanoNombre('MINI SATCHEL')).toBe('nombre-grande');
  });

  it('cuanto más largo, más pequeño', () => {
    expect(tamanoNombre('AMI CANDY POUCH')).toBe('nombre-medio');
    expect(tamanoNombre('PORTE-MONNAIE NEIGE ROND')).toBe('nombre-pequeno');
    expect(tamanoNombre('SAC LE NEIGE CLOU CAMEL AVEC BANDOULIÈRE AMOVIBLE')).toBe('nombre-minimo');
  });

  it('mide el nombre sin los espacios de sobra', () => {
    expect(tamanoNombre('   LE    NEIGE   ')).toBe('nombre-grande');
  });
});

describe('recuento', () => {
  it('dice las etiquetas y las hojas', () => {
    expect(recuento(9)).toBe('9 etiquetas · 2 hojas');
    expect(recuento(1)).toBe('1 etiqueta · 1 hoja');
    expect(recuento(8)).toBe('8 etiquetas · 1 hoja');
  });
});

describe('fechaDia', () => {
  it('el día de una fecha sin zona, como dd/mm/aaaa', () => {
    expect(fechaDia('2026-11-03T09:15:00')).toBe('03/11/2026');
  });
});
