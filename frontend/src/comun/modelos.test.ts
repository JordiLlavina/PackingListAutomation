import { describe, expect, it } from 'vitest';
import { etapaTexto, filtrarRejilla, fotoUrl, type ModeloResumen } from './modelos';

function modelo(id: number, cliente: string, etapa: ModeloResumen['etapa']): ModeloResumen {
  return {
    id,
    cliente,
    codigo: `REF${id}`,
    nombrePrototipo: null,
    referencia: `REF${id}`,
    nombre: `MODELO ${id}`,
    tipo: 'BOLSO',
    etapa,
    fotoPrincipalId: null,
    etiquetaImpresaEn: null,
    fechaEtapa: '2026-11-01T10:00:00',
  };
}

const modelos = [
  modelo(1, 'AMI', 'PRODUCCION'),
  modelo(2, 'AMI', 'PROTOTIPO'),
  modelo(3, 'APC', 'PRODUCCION'),
  modelo(4, 'APC', 'SMS'),
  modelo(5, 'AMI', 'INACTIVO'),
];

const ids = (lista: ModeloResumen[]) => lista.map((m) => m.id);

describe('filtrarRejilla', () => {
  it('TODOS devuelve todos los modelos', () => {
    expect(ids(filtrarRejilla(modelos, 'TODOS'))).toEqual([1, 2, 3, 4, 5]);
  });

  it('una clave de cliente devuelve solo los de ese cliente', () => {
    expect(ids(filtrarRejilla(modelos, 'APC'))).toEqual([3, 4]);
  });

  it('una etapa devuelve solo los de esa etapa', () => {
    expect(ids(filtrarRejilla(modelos, 'PRODUCCION'))).toEqual([1, 3]);
    expect(ids(filtrarRejilla(modelos, 'SMS'))).toEqual([4]);
  });

  it('un filtro que no es ni cliente ni etapa no devuelve ninguno', () => {
    expect(filtrarRejilla(modelos, 'XYZ')).toEqual([]);
  });
});

describe('etapaTexto y fotoUrl', () => {
  it('las etapas con el texto de la maqueta', () => {
    expect(etapaTexto('PROTOTIPO')).toBe('Prototipo');
    expect(etapaTexto('SMS')).toBe('Muestras');
    expect(etapaTexto('PRODUCCION')).toBe('Producción');
    expect(etapaTexto('INACTIVO')).toBe('Inactivo');
  });

  it('la foto se pide a la API por su id', () => {
    expect(fotoUrl(7)).toBe('/api/fotos/7');
  });
});
