import { describe, expect, it } from 'vitest';
import { POR_HOJA, fechaDia, hojasDe, idsDe, recuento, tamanoNombre } from './etiquetas';

describe('hojasDe', () => {
  it('una etiqueta por hoja: 3 etiquetas son 3 hojas', () => {
    expect(POR_HOJA).toBe(1);
    expect(hojasDe([1, 2, 3])).toEqual([[1], [2], [3]]);
  });

  it('ninguna etiqueta, ninguna hoja', () => {
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
    expect(recuento(3)).toBe('3 etiquetas · 3 hojas');
    expect(recuento(1)).toBe('1 etiqueta · 1 hoja');
  });
});

describe('fechaDia', () => {
  it('el día de una fecha sin zona, como dd/mm/aaaa', () => {
    expect(fechaDia('2026-11-03T09:15:00')).toBe('03/11/2026');
  });
});
