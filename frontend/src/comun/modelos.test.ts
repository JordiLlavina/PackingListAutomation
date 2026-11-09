import { describe, expect, it } from 'vitest';
import {
  antesEra,
  etapaTexto,
  filtrarRejilla,
  fotoUrl,
  prototiposCandidatos,
  textoFusion,
  type ModeloResumen,
} from './modelos';

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

describe('asignar referencia y juntar', () => {
  it('el aviso dice qué se junta, en singular o plural, y que no se deshace', () => {
    const fusion = { superviviente: 1, absorbido: 2, articulos: 3, fotos: 1, modificaciones: 0, documentos: 0 };
    expect(textoFusion('ULL770', fusion)).toBe(
      'Se va a juntar con ULL770: 3 artículos, 1 foto, 0 modificaciones. No se puede deshacer.',
    );
  });

  it('antes era: nada sin fusiones, uno o varios con su #', () => {
    expect(antesEra([])).toBeNull();
    expect(antesEra([12])).toBe('Antes era el #12');
    expect(antesEra([15, 12])).toBe('Antes era el #15 y el #12');
    expect(antesEra([20, 15, 12])).toBe('Antes era el #20, el #15 y el #12');
  });

  it('los prototipos candidatos son los sin código, sin el propio y con el texto', () => {
    const proto = (id: number, nombre: string): ModeloResumen => ({
      ...modelo(id, 'AMI', 'PROTOTIPO'),
      codigo: null,
      nombrePrototipo: `PROTO ${id}`,
      referencia: `PROTO ${id}`,
      nombre,
    });
    const lista = [proto(1, 'Candy'), modelo(2, 'AMI', 'PRODUCCION'), proto(3, 'Bolsa grande'), proto(4, '')];
    expect(ids(prototiposCandidatos(lista, 4, ''))).toEqual([1, 3]);
    expect(ids(prototiposCandidatos(lista, 4, 'candy'))).toEqual([1]);
    expect(ids(prototiposCandidatos(lista, 4, 'proto 3'))).toEqual([3]);
  });
});
