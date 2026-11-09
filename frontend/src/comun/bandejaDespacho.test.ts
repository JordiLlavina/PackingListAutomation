import { describe, expect, it } from 'vitest';
import { estadoModificacion, estadosDelFiltro, marcaDestino, numeroBandejaDespacho, textoVoz, type DestinoEstado } from './modificaciones';

describe('textoVoz', () => {
  it('dice cuánto dura la nota o que no hay', () => {
    expect(textoVoz(35)).toBe('00:35 de voz');
    expect(textoVoz(125)).toBe('02:05 de voz');
    expect(textoVoz(0)).toBe('00:00 de voz');
    expect(textoVoz(null)).toBe('sin voz');
  });
});

describe('numeroBandejaDespacho', () => {
  it('suma los avisos nuevos y las órdenes con respuesta, no las pendientes ni las vencidas', () => {
    expect(numeroBandejaDespacho({ nuevas: 2, pendientes: 7, vencidas: 1, conRespuesta: 3, sinPrioridad: 2 })).toBe(5);
    expect(numeroBandejaDespacho({ nuevas: 0, pendientes: 4, vencidas: 0, conRespuesta: 0, sinPrioridad: 0 })).toBe(0);
  });

  it('es cero mientras no se sabe', () => {
    expect(numeroBandejaDespacho(null)).toBe(0);
  });
});

describe('estadosDelFiltro', () => {
  it('«Abiertas» son las nuevas, vistas y en curso; «Todas» no filtra', () => {
    expect(estadosDelFiltro('ABIERTAS')).toEqual(['NUEVA', 'VISTA', 'EN_CURSO']);
    expect(estadosDelFiltro('NUEVAS')).toEqual(['NUEVA']);
    expect(estadosDelFiltro('HECHAS')).toEqual(['HECHA']);
    expect(estadosDelFiltro('DESCARTADAS')).toEqual(['DESCARTADA']);
    expect(estadosDelFiltro('TODAS')).toEqual([]);
  });
});

describe('estadoModificacion', () => {
  it('da un texto y una pastilla por estado', () => {
    expect(estadoModificacion('NUEVA')).toEqual({ texto: 'Nueva', pill: 'nueva' });
    expect(estadoModificacion('EN_CURSO')).toEqual({ texto: 'En curso', pill: 'en-curso' });
    expect(estadoModificacion('DESCARTADA')).toEqual({ texto: 'Descartada', pill: 'descartada' });
  });
});

describe('marcaDestino', () => {
  const destino = (extra: Partial<DestinoEstado>): DestinoEstado => ({
    destino: 'ICSUITE',
    texto: 'ICSUITE',
    aplica: true,
    hechoEn: null,
    hechoPor: null,
    ...extra,
  });

  it('✓ si ya está aplicado; – si falta o no aplica, con el porqué en el texto', () => {
    expect(marcaDestino(destino({ hechoEn: '2026-11-09T10:00:00' }))).toEqual({ simbolo: '✓', estado: 'hecho', texto: 'ICSUITE: aplicado' });
    expect(marcaDestino(destino({}))).toEqual({ simbolo: '–', estado: 'pendiente', texto: 'ICSUITE: por aplicar' });
    expect(marcaDestino(destino({ aplica: false }))).toEqual({ simbolo: '–', estado: 'no-aplica', texto: 'ICSUITE: no aplica' });
  });
});
