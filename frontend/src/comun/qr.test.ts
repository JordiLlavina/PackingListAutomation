import { describe, expect, it } from 'vitest';
import { idDeModelo } from './qr';

describe('idDeModelo', () => {
  it('lee la dirección completa de la etiqueta, con cualquier dominio', () => {
    expect(idDeModelo('https://modelos.puntotres.com/m/123')).toBe(123);
    expect(idDeModelo('http://192.168.1.20:8080/m/7')).toBe(7);
  });

  it('admite barra final y parámetros', () => {
    expect(idDeModelo('https://x.es/m/5/')).toBe(5);
    expect(idDeModelo('https://x.es/m/5?origen=etiqueta')).toBe(5);
    expect(idDeModelo('https://x.es/m/5#arriba')).toBe(5);
  });

  it('lee la ruta sola y el número a secas, recortando espacios', () => {
    expect(idDeModelo('/m/123')).toBe(123);
    expect(idDeModelo('123')).toBe(123);
    expect(idDeModelo('  42\n')).toBe(42);
  });

  it('rechaza lo que no es de una etiqueta de patrón', () => {
    expect(idDeModelo('abc')).toBeNull();
    expect(idDeModelo('/x/123')).toBeNull();
    expect(idDeModelo('https://x.es/otra/123')).toBeNull();
    expect(idDeModelo('0')).toBeNull();
    expect(idDeModelo('-5')).toBeNull();
    expect(idDeModelo('m/12a')).toBeNull();
    expect(idDeModelo('')).toBeNull();
  });
});
