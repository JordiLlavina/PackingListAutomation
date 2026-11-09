import { describe, expect, it } from 'vitest';
import {
  estadoAviso,
  llegaronNuevas,
  ordenarAvisos,
  ordenarOrdenes,
  type EstadoModificacion,
  type ModificacionResumen,
  type Prioridad,
} from './modificaciones';

let siguienteId = 1;
function orden(estado: EstadoModificacion, prioridad: Prioridad | null, creadaEn: string, extra: Partial<ModificacionResumen> = {}): ModificacionResumen {
  return {
    id: siguienteId++,
    modeloId: 1,
    modeloReferencia: 'ULL770',
    modeloNombre: 'CANDY POUCH BAG',
    cliente: 'AMI',
    fotoPrincipalId: null,
    direccion: 'DESPACHO_A_TALLER',
    tipoCambio: 'FORRO',
    tipoCambioTexto: 'Forro',
    prioridad,
    estado,
    titulo: `${estado} ${prioridad} ${creadaEn}`,
    tituloDeIa: false,
    creadaEn,
    creadaPor: 'Carme',
    vistaEn: null,
    hechaEn: null,
    hechaPor: null,
    respuestaPendienteDe: null,
    vencida: false,
    destinos: [],
    fotos: 0,
    audios: 0,
    documentos: 0,
    ...extra,
  };
}

const titulos = (lista: ModificacionResumen[]) => lista.map((o) => o.titulo);

describe('ordenarOrdenes', () => {
  it('las nuevas van primero aunque tengan menos prioridad', () => {
    const vistaAlta = orden('VISTA', 'ALTA', '2026-11-01T10:00:00');
    const nuevaBaja = orden('NUEVA', 'BAJA', '2026-11-08T10:00:00');
    expect(ordenarOrdenes([vistaAlta, nuevaBaja]).pendientes).toEqual([nuevaBaja, vistaAlta]);
  });

  it('a igual novedad, ALTA, NORMAL, BAJA y sin prioridad al final', () => {
    const sin = orden('VISTA', null, '2026-11-01T10:00:00');
    const baja = orden('VISTA', 'BAJA', '2026-11-01T10:00:00');
    const alta = orden('VISTA', 'ALTA', '2026-11-01T10:00:00');
    const normal = orden('EN_CURSO', 'NORMAL', '2026-11-01T10:00:00');
    expect(titulos(ordenarOrdenes([sin, baja, alta, normal]).pendientes)).toEqual(titulos([alta, normal, baja, sin]));
  });

  it('a igual novedad y prioridad, la más antigua primero', () => {
    const reciente = orden('NUEVA', 'NORMAL', '2026-11-08T09:00:00');
    const antigua = orden('NUEVA', 'NORMAL', '2026-11-02T18:30:00');
    const media = orden('NUEVA', 'NORMAL', '2026-11-05T07:15:00.123456');
    expect(ordenarOrdenes([reciente, antigua, media]).pendientes).toEqual([antigua, media, reciente]);
  });

  it('las hechas y las descartadas van aparte, la última cerrada primero', () => {
    const hechaAntes = orden('HECHA', 'ALTA', '2026-11-01T10:00:00', { hechaEn: '2026-11-03T10:00:00' });
    const hechaDespues = orden('HECHA', 'BAJA', '2026-11-02T10:00:00', { hechaEn: '2026-11-07T10:00:00' });
    const descartada = orden('DESCARTADA', 'NORMAL', '2026-11-05T10:00:00');
    const abierta = orden('VISTA', 'NORMAL', '2026-11-04T10:00:00');
    const { pendientes, hechas } = ordenarOrdenes([hechaAntes, abierta, descartada, hechaDespues]);
    expect(pendientes).toEqual([abierta]);
    expect(hechas).toEqual([hechaDespues, descartada, hechaAntes]);
  });

  it('no toca la lista que recibe', () => {
    const lista = [orden('VISTA', 'BAJA', '2026-11-01T10:00:00'), orden('NUEVA', 'ALTA', '2026-11-02T10:00:00')];
    const copia = [...lista];
    ordenarOrdenes(lista);
    expect(lista).toEqual(copia);
  });

  it('sin órdenes, las dos listas vacías', () => {
    expect(ordenarOrdenes([])).toEqual({ pendientes: [], hechas: [] });
  });
});

describe('ordenarAvisos', () => {
  it('el más reciente primero, como en la maqueta', () => {
    const viejo = orden('HECHA', null, '2026-11-01T10:00:00');
    const nuevo = orden('NUEVA', null, '2026-11-08T10:00:00');
    const medio = orden('VISTA', 'ALTA', '2026-11-05T10:00:00');
    expect(ordenarAvisos([viejo, nuevo, medio])).toEqual([nuevo, medio, viejo]);
  });
});

describe('estadoAviso', () => {
  it('cada estado con su texto y su color', () => {
    expect(estadoAviso('NUEVA')).toEqual({ texto: 'Enviado', pill: 'enviado', franja: 'aviso-enviado' });
    expect(estadoAviso('VISTA')).toEqual({ texto: 'Visto', pill: 'vista', franja: 'aviso-visto' });
    expect(estadoAviso('EN_CURSO')).toEqual({ texto: 'Visto', pill: 'vista', franja: 'aviso-visto' });
    expect(estadoAviso('HECHA')).toEqual({ texto: 'Hecho', pill: 'hecha', franja: 'aviso-hecho' });
    expect(estadoAviso('DESCARTADA')).toEqual({ texto: 'Descartado', pill: 'vista', franja: 'aviso-descartado' });
  });
});

describe('llegaronNuevas', () => {
  it('solo cuando sube el número, y no en la primera consulta', () => {
    expect(llegaronNuevas(null, 3)).toBe(false);
    expect(llegaronNuevas(1, 2)).toBe(true);
    expect(llegaronNuevas(2, 2)).toBe(false);
    expect(llegaronNuevas(2, 1)).toBe(false);
  });
});
