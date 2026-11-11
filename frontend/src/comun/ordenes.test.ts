import { describe, expect, it } from 'vitest';
import {
  colaFiltrada,
  estadoAviso,
  filtrarCola,
  llegaronNuevas,
  moverEnCola,
  opcionesPosicion,
  ordenarAvisos,
  ordenarOrdenes,
  soltarSobre,
  type EstadoModificacion,
  type ModificacionResumen,
} from './modificaciones';

let siguienteId = 1;
function orden(estado: EstadoModificacion, posicionCola: number | null, creadaEn: string, extra: Partial<ModificacionResumen> = {}): ModificacionResumen {
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
    urgente: false,
    posicionCola,
    estado,
    titulo: `${estado} ${posicionCola} ${creadaEn}`,
    tituloDeIa: false,
    creadaEn,
    creadaPor: 'Carme',
    vistaEn: null,
    hechaEn: null,
    hechaPor: null,
    respuestaPendienteDe: null,
    etapa: 'PRODUCCION',
    destinos: [],
    fotos: 0,
    audios: 0,
    documentos: 0,
    duracionAudioSeg: null,
    ultimoMensajeDe: null,
    ...extra,
  };
}

const titulos = (lista: ModificacionResumen[]) => lista.map((o) => o.titulo);

describe('ordenarOrdenes', () => {
  it('en el orden de la cola, aunque la nueva llegue después', () => {
    const segunda = orden('VISTA', 2, '2026-11-01T10:00:00');
    const primera = orden('NUEVA', 1, '2026-11-08T10:00:00');
    const tercera = orden('EN_CURSO', 3, '2026-11-02T10:00:00');
    expect(titulos(ordenarOrdenes([segunda, tercera, primera]).pendientes)).toEqual(titulos([primera, segunda, tercera]));
  });

  it('sin puesto, al final y la más antigua primero', () => {
    const reciente = orden('NUEVA', null, '2026-11-08T09:00:00');
    const antigua = orden('NUEVA', null, '2026-11-02T18:30:00');
    const conPuesto = orden('VISTA', 1, '2026-11-09T07:15:00.123456');
    expect(ordenarOrdenes([reciente, antigua, conPuesto]).pendientes).toEqual([conPuesto, antigua, reciente]);
  });

  it('las hechas y las descartadas van aparte, la última cerrada primero', () => {
    const hechaAntes = orden('HECHA', null, '2026-11-01T10:00:00', { hechaEn: '2026-11-03T10:00:00' });
    const hechaDespues = orden('HECHA', null, '2026-11-02T10:00:00', { hechaEn: '2026-11-07T10:00:00' });
    const descartada = orden('DESCARTADA', null, '2026-11-05T10:00:00');
    const abierta = orden('VISTA', 1, '2026-11-04T10:00:00');
    const { pendientes, hechas } = ordenarOrdenes([hechaAntes, abierta, descartada, hechaDespues]);
    expect(pendientes).toEqual([abierta]);
    expect(hechas).toEqual([hechaDespues, descartada, hechaAntes]);
  });

  it('no toca la lista que recibe', () => {
    const lista = [orden('VISTA', 2, '2026-11-01T10:00:00'), orden('NUEVA', 1, '2026-11-02T10:00:00')];
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
    const medio = orden('VISTA', null, '2026-11-05T10:00:00');
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

describe('moverEnCola', () => {
  const cola = [10, 20, 30, 40];

  it('sube, baja, a la primera y a la última', () => {
    expect(moverEnCola(cola, 30, 'arriba')).toEqual([10, 30, 20, 40]);
    expect(moverEnCola(cola, 20, 'abajo')).toEqual([10, 30, 20, 40]);
    expect(moverEnCola(cola, 40, 'primera')).toEqual([40, 10, 20, 30]);
    expect(moverEnCola(cola, 10, 'ultima')).toEqual([20, 30, 40, 10]);
  });

  it('en los bordes, o si no está, la misma lista sin tocarla', () => {
    expect(moverEnCola(cola, 10, 'arriba')).toBe(cola);
    expect(moverEnCola(cola, 10, 'primera')).toBe(cola);
    expect(moverEnCola(cola, 40, 'abajo')).toBe(cola);
    expect(moverEnCola(cola, 99, 'abajo')).toBe(cola);
    expect(cola).toEqual([10, 20, 30, 40]);
  });
});

describe('soltarSobre', () => {
  const cola = [10, 20, 30, 40];

  it('desde abajo queda delante de la otra; desde arriba, detrás', () => {
    expect(soltarSobre(cola, 40, 20)).toEqual([10, 40, 20, 30]);
    expect(soltarSobre(cola, 10, 30)).toEqual([20, 30, 10, 40]);
  });

  it('sobre sí misma o con una que no está, nada', () => {
    expect(soltarSobre(cola, 20, 20)).toBe(cola);
    expect(soltarSobre(cola, 99, 20)).toBe(cola);
  });
});

describe('filtrarCola y colaFiltrada', () => {
  const forro = orden('VISTA', 1, '2026-11-01T10:00:00', { titulo: 'Cambiar el forro', modeloReferencia: 'ULL770', cliente: 'AMI', etapa: 'PRODUCCION' });
  const asa = orden('VISTA', 2, '2026-11-01T10:00:00', { titulo: 'Asa más corta', modeloReferencia: 'PROTO A', modeloNombre: 'Le neige', cliente: 'APC', etapa: 'PROTOTIPO' });
  const sin = { texto: '', cliente: '', etapa: '' as const };

  it('busca dentro de la referencia, el nombre y el título, sin acentos ni puntos', () => {
    expect(filtrarCola([forro, asa], { ...sin, texto: '77' })).toEqual([forro]);
    expect(filtrarCola([forro, asa], { ...sin, texto: 'neige' })).toEqual([asa]);
    expect(filtrarCola([forro, asa], { ...sin, texto: 'ásá' })).toEqual([asa]);
  });

  it('por cliente y por etapa', () => {
    expect(filtrarCola([forro, asa], { ...sin, cliente: 'APC' })).toEqual([asa]);
    expect(filtrarCola([forro, asa], { ...sin, etapa: 'PRODUCCION' })).toEqual([forro]);
  });

  it('con cualquier filtro la vista es parcial; sin ninguno, no', () => {
    expect(colaFiltrada(sin)).toBe(false);
    expect(colaFiltrada({ ...sin, texto: '  ' })).toBe(false);
    expect(colaFiltrada({ ...sin, texto: 'x' })).toBe(true);
    expect(colaFiltrada({ ...sin, cliente: 'AMI' })).toBe(true);
    expect(colaFiltrada({ ...sin, etapa: 'SMS' })).toBe(true);
  });
});

describe('opcionesPosicion', () => {
  it('al final por defecto y antes de cada una de la cola', () => {
    const a = orden('VISTA', 1, '2026-11-01T10:00:00', { id: 7, titulo: 'Forro', modeloReferencia: 'ULL770' });
    const b = orden('VISTA', 2, '2026-11-01T10:00:00', { id: 8, titulo: 'Asa', modeloReferencia: 'ULL027' });
    expect(opcionesPosicion([a, b])).toEqual([
      { valor: '', texto: 'Al final (3ª)' },
      { valor: '7', texto: 'La primera, antes de ULL770 · Forro' },
      { valor: '8', texto: 'Antes de la 2ª · ULL027 · Asa' },
    ]);
  });

  it('con la cola vacía, solo una', () => {
    expect(opcionesPosicion([])).toEqual([{ valor: '', texto: 'La primera (la cola está vacía)' }]);
  });
});
