import { describe, expect, it } from 'vitest';
import {
  destinosEditables,
  estadoModificacion,
  estadosDelFiltro,
  hayTranscripcionPendiente,
  marcaDestino,
  numeroBandejaDespacho,
  textoVoz,
  tituloEditable,
  type AdjuntoVista,
  type DestinoEstado,
  type ModificacionDetalle,
  type ModificacionResumen,
} from './modificaciones';

describe('textoVoz', () => {
  it('dice cuánto dura la nota o que no hay', () => {
    expect(textoVoz(35)).toBe('00:35 de voz');
    expect(textoVoz(125)).toBe('02:05 de voz');
    expect(textoVoz(0)).toBe('00:00 de voz');
    expect(textoVoz(null)).toBe('sin voz');
  });
});

describe('numeroBandejaDespacho', () => {
  it('suma los avisos nuevos y las órdenes con respuesta, no las pendientes ni las urgentes', () => {
    expect(numeroBandejaDespacho({ nuevas: 2, pendientes: 7, urgentes: 1, conRespuesta: 3 })).toBe(5);
    expect(numeroBandejaDespacho({ nuevas: 0, pendientes: 4, urgentes: 0, conRespuesta: 0 })).toBe(0);
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

function resumen(extra: Partial<ModificacionResumen>): ModificacionResumen {
  return {
    id: 1,
    modeloId: 1,
    modeloReferencia: 'ULL770',
    modeloNombre: 'CANDY POUCH BAG',
    cliente: 'AMI',
    fotoPrincipalId: null,
    direccion: 'TALLER_A_DESPACHO',
    tipoCambio: 'COSTURA',
    tipoCambioTexto: 'Costura / construcción',
    urgente: false,
    posicionCola: null,
    estado: 'VISTA',
    titulo: null,
    tituloDeIa: false,
    creadaEn: '2026-11-09T10:00:00',
    creadaPor: 'Taller',
    vistaEn: null,
    hechaEn: null,
    hechaPor: null,
    respuestaPendienteDe: null,
    etapa: 'PRODUCCION',
    destinos: [],
    fotos: 0,
    audios: 1,
    documentos: 0,
    duracionAudioSeg: 12,
    ultimoMensajeDe: 'TALLER',
    ...extra,
  };
}

function nota(id: number, estadoTranscripcion: AdjuntoVista['estadoTranscripcion'], tipo: AdjuntoVista['tipo'] = 'AUDIO'): AdjuntoVista {
  return { id, tipo, nombre: 'nota.webm', mime: 'audio/webm', tamano: 4, duracionSeg: 12, transcripcion: null, resumenIa: null, estadoTranscripcion, error: null };
}

function detalle(...adjuntos: AdjuntoVista[]): ModificacionDetalle {
  return {
    resumen: resumen({}),
    motivoDescarte: null,
    mensajes: [{ id: 1, autor: 'Taller', rolAutor: 'TALLER', texto: '', creadoEn: '2026-11-09T10:00:00', adjuntos }],
  };
}

describe('hayTranscripcionPendiente', () => {
  it('solo cuenta las notas de voz que se están transcribiendo', () => {
    expect(hayTranscripcionPendiente(detalle(nota(1, 'HECHA'), nota(2, 'PENDIENTE')))).toBe(true);
    expect(hayTranscripcionPendiente(detalle(nota(1, 'FALLIDA'), nota(2, 'NO_APLICA')))).toBe(false);
    expect(hayTranscripcionPendiente(detalle(nota(1, 'PENDIENTE', 'FOTO')))).toBe(false);
    expect(hayTranscripcionPendiente(detalle())).toBe(false);
  });
});

describe('tituloEditable', () => {
  it('el título de la IA se corrige, y un aviso sin título se titula', () => {
    expect(tituloEditable(resumen({ titulo: 'Asa reforzada', tituloDeIa: true }))).toBe(true);
    expect(tituloEditable(resumen({ titulo: null }))).toBe(true);
    expect(tituloEditable(resumen({ titulo: 'Asa de 60 cm', tituloDeIa: false }))).toBe(false);
    expect(tituloEditable(resumen({ direccion: 'DESPACHO_A_TALLER', titulo: 'Forro de ante' }))).toBe(false);
  });
});

describe('destinosEditables', () => {
  it('en un aviso hecho se pueden desmarcar; en uno descartado no', () => {
    expect(destinosEditables('VISTA')).toBe(true);
    expect(destinosEditables('HECHA')).toBe(true);
    expect(destinosEditables('DESCARTADA')).toBe(false);
  });
});
