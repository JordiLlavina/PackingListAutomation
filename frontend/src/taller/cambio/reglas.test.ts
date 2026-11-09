import { describe, expect, it } from 'vitest';
import type { ModeloResumen } from '../../comun/modelos';
import { motivoNoEnviar, pasoActual, puedeEnviar, type EstadoBorrador } from './reglas';

const modelo: ModeloResumen = {
  id: 1,
  cliente: 'AMI',
  codigo: 'ULL770',
  nombrePrototipo: null,
  referencia: 'ULL770',
  nombre: 'CANDY POUCH BAG',
  tipo: 'BOLSO',
  etapa: 'PRODUCCION',
  fotoPrincipalId: null,
  etiquetaImpresaEn: null,
  fechaEtapa: '2026-11-01T10:00:00',
};
const foto = { blob: new Blob(['x']), url: 'blob:foto' };
const audio = { blob: new Blob(['y']), duracionSeg: 12, url: 'blob:audio' };

function borrador(cambios: Partial<EstadoBorrador> = {}): EstadoBorrador {
  return { modelo, tipo: 'COSTURA', fotos: [], audio: null, ...cambios };
}

describe('motivoNoEnviar', () => {
  it('sin modelo pide el modelo, antes que nada', () => {
    expect(motivoNoEnviar(borrador({ modelo: null, tipo: null }))).toBe('Elige el modelo');
  });

  it('sin tipo pide qué se ha cambiado', () => {
    expect(motivoNoEnviar(borrador({ tipo: null }))).toBe('Di qué has cambiado');
  });

  it('sin foto ni voz pide una de las dos', () => {
    expect(motivoNoEnviar(borrador())).toBe('Hace falta una nota de voz o una foto');
  });

  it('con todo no hay motivo', () => {
    expect(motivoNoEnviar(borrador({ fotos: [foto] }))).toBeNull();
  });
});

describe('puedeEnviar', () => {
  it('con solo la nota de voz', () => {
    expect(puedeEnviar(borrador({ audio }))).toBe(true);
  });

  it('con solo una foto', () => {
    expect(puedeEnviar(borrador({ fotos: [foto] }))).toBe(true);
  });

  it('con las dos', () => {
    expect(puedeEnviar(borrador({ fotos: [foto], audio }))).toBe(true);
  });

  it('sin ninguna', () => {
    expect(puedeEnviar(borrador())).toBe(false);
  });
});

describe('pasoActual', () => {
  it('los cuatro pasos de la barra', () => {
    expect(pasoActual('/cambio')).toBe(1);
    expect(pasoActual('/cambio/tipo')).toBe(2);
    expect(pasoActual('/cambio/fotos')).toBe(3);
    expect(pasoActual('/cambio/voz')).toBe(4);
  });

  it('revisar y enviado no llevan barra', () => {
    expect(pasoActual('/cambio/revisar')).toBeNull();
    expect(pasoActual('/cambio/enviado')).toBeNull();
  });

  it('con la barra del final da igual', () => {
    expect(pasoActual('/cambio/')).toBe(1);
    expect(pasoActual('/cambio/fotos/')).toBe(3);
  });
});
