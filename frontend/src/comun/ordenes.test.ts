import { describe, expect, it } from 'vitest';
import {
  avisoEditable,
  cambiosDePuesto,
  colaFiltrada,
  estiloFechaObjetivo,
  informeCola,
  motivoNoEditable,
  ordenesAbiertasDe,
  puestosDe,
  textoCambioPuesto,
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
    fechaObjetivo: null,
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

describe('cambiosDePuesto', () => {
  it('solo las que se mueven entre los tres primeros, y nunca una que no se había visto', () => {
    const a = orden('VISTA', 1, '2026-11-01T10:00:00');
    const b = orden('VISTA', 2, '2026-11-01T10:00:00');
    const c = orden('VISTA', 3, '2026-11-01T10:00:00');
    const d = orden('VISTA', 4, '2026-11-01T10:00:00');
    const e = orden('NUEVA', 5, '2026-11-01T10:00:00');
    const antes = puestosDe([a, b, c, d, e]);
    expect(antes).toEqual({ [a.id]: 1, [b.id]: 2, [c.id]: 3, [d.id]: 4, [e.id]: 5 });

    // d pasa la 2ª (b y c bajan), e se mueve a la 8ª (fuera de los tres), y llega f nueva la 1ª.
    const f = orden('NUEVA', 1, '2026-11-02T10:00:00');
    const despues = [f, { ...a, posicionCola: 2 }, { ...d, posicionCola: 3 }, { ...b, posicionCola: 4 }, { ...c, posicionCola: 5 }, { ...e, posicionCola: 8 }];
    expect([...cambiosDePuesto(antes, despues)]).toEqual([
      [a.id, 2],
      [b.id, 4],
      [c.id, 5],
      [d.id, 3],
    ]);
  });

  it('con la misma cola no hay cambios, y una cerrada no cuenta', () => {
    const a = orden('VISTA', 1, '2026-11-01T10:00:00');
    const hecha = orden('HECHA', 1, '2026-11-01T10:00:00');
    expect(puestosDe([a, hecha])).toEqual({ [a.id]: 1 });
    expect(cambiosDePuesto(puestosDe([a]), [a]).size).toBe(0);
  });
});

describe('textoCambioPuesto', () => {
  it('primer puesto en palabras y los demás con ordinal', () => {
    expect(textoCambioPuesto(1)).toBe('Prioridad cambiada al primer puesto');
    expect(textoCambioPuesto(2)).toBe('Prioridad cambiada al 2º puesto');
  });
});

describe('ordenesAbiertasDe', () => {
  it('las abiertas del modelo, no las hechas ni las de otro', () => {
    const suya = orden('VISTA', 1, '2026-11-01T10:00:00', { modeloId: 7 });
    const hecha = orden('HECHA', null, '2026-11-01T10:00:00', { modeloId: 7 });
    const otra = orden('VISTA', 2, '2026-11-01T10:00:00', { modeloId: 8 });
    expect(ordenesAbiertasDe([suya, hecha, otra], 7)).toEqual([suya]);
  });
});

describe('estiloFechaObjetivo', () => {
  const hoy = new Date('2026-11-09T12:00:00');

  it('gris como «Vista» mientras falten más de 5 días', () => {
    const e = estiloFechaObjetivo('2026-11-20', hoy);
    expect(e.texto).toBe('Fecha: 20 noviembre');
    expect(e.fondo).toBe('var(--line)');
    expect(e.tinta).toBe('var(--fg)');
    expect(e.detalle).toBe('faltan 11 días');
  });

  it('cada vez más roja desde 5 días, y el rojo de urgente el mismo día', () => {
    expect(estiloFechaObjetivo('2026-11-14', hoy).fondo).toBe('color-mix(in oklab, var(--alta) 17%, var(--line))');
    expect(estiloFechaObjetivo('2026-11-14', hoy).tinta).toBe('var(--fg)');
    const manana = estiloFechaObjetivo('2026-11-10', hoy);
    expect(manana.fondo).toBe('color-mix(in oklab, var(--alta) 83%, var(--line))');
    expect(manana.tinta).toBe('#fff');
    expect(manana.detalle).toBe('es mañana');
    const hoyMismo = estiloFechaObjetivo('2026-11-09', hoy);
    expect(hoyMismo.fondo).toBe('var(--alta)');
    expect(hoyMismo.detalle).toBe('es hoy');
  });

  it('pasado el día se oscurece un paso al día hasta el negro a los 7, y ahí se queda', () => {
    const ayer = estiloFechaObjetivo('2026-11-08', hoy);
    expect(ayer.fondo).toBe('color-mix(in oklab, #000 14%, var(--alta))');
    expect(ayer.detalle).toBe('pasó ayer');
    expect(estiloFechaObjetivo('2026-11-02', hoy).fondo).toBe('color-mix(in oklab, #000 100%, var(--alta))');
    const masAun = estiloFechaObjetivo('2026-10-01', hoy);
    expect(masAun.fondo).toBe('color-mix(in oklab, #000 100%, var(--alta))');
    expect(masAun.detalle).toBe('pasó hace 39 días');
  });
});

describe('informeCola', () => {
  const ahora = new Date('2026-11-09T12:00:00');

  it('la primera con sus días, las urgentes y las de fecha pasada; las hechas no cuentan', () => {
    const primera = orden('VISTA', 1, '2026-11-01T10:00:00');
    const urgente = orden('VISTA', 2, '2026-11-05T10:00:00', { urgente: true, fechaObjetivo: '2026-11-07' });
    const conFecha = orden('VISTA', 3, '2026-11-08T10:00:00', { fechaObjetivo: '2026-11-01' });
    const futura = orden('VISTA', 4, '2026-11-08T10:00:00', { fechaObjetivo: '2026-11-30' });
    const hecha = orden('HECHA', null, '2026-10-01T10:00:00', { urgente: true, fechaObjetivo: '2026-10-02' });
    const informe = informeCola([hecha, futura, conFecha, urgente, primera], ahora);
    expect(informe.primera).toEqual({ orden: primera, dias: 8 });
    expect(informe.urgentes).toEqual([{ orden: urgente, dias: 4 }]);
    expect(informe.fechaPasada.map((f) => [f.orden.id, f.diasPasados])).toEqual([
      [conFecha.id, 8],
      [urgente.id, 2],
    ]);
    expect(informe.abiertas).toBe(4);
  });

  it('con la cola vacía no hay primera', () => {
    expect(informeCola([], ahora)).toEqual({ primera: null, urgentes: [], fechaPasada: [], abiertas: 0 });
  });
});

describe('avisoEditable', () => {
  it('solo mientras despacho no ha empezado: enviado o visto', () => {
    expect(avisoEditable('NUEVA')).toBe(true);
    expect(avisoEditable('VISTA')).toBe(true);
    expect(avisoEditable('EN_CURSO')).toBe(false);
    expect(avisoEditable('HECHA')).toBe(false);
    expect(avisoEditable('DESCARTADA')).toBe(false);
  });

  it('y si no, dice por qué', () => {
    expect(motivoNoEditable('NUEVA')).toBeNull();
    expect(motivoNoEditable('EN_CURSO')).toContain('ya está aplicando');
    expect(motivoNoEditable('HECHA')).toContain('ya está hecho');
    expect(motivoNoEditable('DESCARTADA')).toContain('descartó');
  });
});
