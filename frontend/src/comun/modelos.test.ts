import { describe, expect, it } from 'vitest';
import {
  alternarEnTira,
  antesEra,
  candidataUrl,
  conOSinCinturones,
  documentoUrl,
  enTira,
  etapaTexto,
  filtrarRejilla,
  fotoUrl,
  idsEnOrden,
  idsNuevos,
  mismoOrden,
  prototiposCandidatos,
  puntoEvento,
  tamanoTexto,
  textoCinturones,
  textoFotosAnadidas,
  textoFusion,
  type ModeloResumen,
  type ResumenFoto,
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
  const sin = { texto: '', cliente: '', etapa: '', cinturones: false } as const;

  it('sin filtros devuelve todos los modelos', () => {
    expect(ids(filtrarRejilla(modelos, sin))).toEqual([1, 2, 3, 4, 5]);
  });

  it('el cliente devuelve solo los de ese cliente', () => {
    expect(ids(filtrarRejilla(modelos, { ...sin, cliente: 'APC' }))).toEqual([3, 4]);
  });

  it('la etapa devuelve solo los de esa etapa, también los inactivos', () => {
    expect(ids(filtrarRejilla(modelos, { ...sin, etapa: 'PRODUCCION' }))).toEqual([1, 3]);
    expect(ids(filtrarRejilla(modelos, { ...sin, etapa: 'SMS' }))).toEqual([4]);
    expect(ids(filtrarRejilla(modelos, { ...sin, etapa: 'INACTIVO' }))).toEqual([5]);
  });

  it('cliente y etapa se suman', () => {
    expect(ids(filtrarRejilla(modelos, { ...sin, cliente: 'AMI', etapa: 'PRODUCCION' }))).toEqual([1]);
  });

  it('el texto se busca dentro de la referencia: «77» y «770» encuentran ULL770', () => {
    const lista = [{ ...modelo(7, 'AMI', 'PRODUCCION'), codigo: 'ULL770', referencia: 'ULL770' }, ...modelos];
    expect(ids(filtrarRejilla(lista, { ...sin, texto: '770' }))).toEqual([7]);
    expect(ids(filtrarRejilla(lista, { ...sin, texto: '77' }))).toEqual([7]);
  });

  it('el texto se busca también en el nombre, sin mirar mayúsculas ni acentos', () => {
    const lista = [{ ...modelo(8, 'APC', 'SMS'), nombre: 'PORTE-MONNAIE NEIGE' }, ...modelos];
    expect(ids(filtrarRejilla(lista, { ...sin, texto: 'néige' }))).toEqual([8]);
  });

  it('el texto no mira puntos ni espacios: «ull027al0103» encuentra ULL027.AL0103', () => {
    const lista = [{ ...modelo(9, 'AMI', 'PRODUCCION'), codigo: 'ULL027.AL0103', referencia: 'ULL027.AL0103' }];
    expect(ids(filtrarRejilla(lista, { ...sin, texto: 'ull027al0103' }))).toEqual([9]);
  });

  it('un modelo que ya tiene código se encuentra también por su nombre de prototipo', () => {
    const lista = [{ ...modelo(10, 'AMI', 'PRODUCCION'), nombrePrototipo: 'PROTO A' }];
    expect(ids(filtrarRejilla(lista, { ...sin, texto: 'proto a' }))).toEqual([10]);
  });

  it('un texto que no está en ningún modelo no devuelve ninguno', () => {
    expect(filtrarRejilla(modelos, { ...sin, texto: 'XYZ' })).toEqual([]);
  });

  it('los cinturones solo salen si se piden, también buscándolos', () => {
    const lista = [...modelos, { ...modelo(6, 'AMI', 'PRODUCCION'), tipo: 'CINTURON' as const, codigo: 'UBL029', referencia: 'UBL029' }];
    expect(ids(filtrarRejilla(lista, sin))).toEqual([1, 2, 3, 4, 5]);
    expect(ids(filtrarRejilla(lista, { ...sin, texto: 'UBL029' }))).toEqual([]);
    expect(ids(filtrarRejilla(lista, { ...sin, cinturones: true }))).toEqual([1, 2, 3, 4, 5, 6]);
    expect(ids(filtrarRejilla(lista, { ...sin, cinturones: true, texto: 'UBL' }))).toEqual([6]);
  });
});

describe('cinturones', () => {
  const cinturon = { ...modelo(6, 'AMI', 'PRODUCCION'), tipo: 'CINTURON' as const };

  it('sin cinturones se quitan solo los cinturones', () => {
    expect(ids(conOSinCinturones([...modelos, cinturon], false))).toEqual([1, 2, 3, 4, 5]);
    expect(ids(conOSinCinturones([...modelos, cinturon], true))).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it('la etiqueta dice cómo está el filtro', () => {
    expect(textoCinturones(false)).toBe('Cinturones: no');
    expect(textoCinturones(true)).toBe('Cinturones: sí');
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
    expect(textoFusion('ULL770', { ...fusion, documentos: 2 })).toBe(
      'Se va a juntar con ULL770: 3 artículos, 1 foto, 0 modificaciones, 2 documentos. No se puede deshacer.',
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

describe('documentos e imágenes de un PDF', () => {
  it('las direcciones del documento y de cada candidata', () => {
    expect(documentoUrl(5)).toBe('/api/documentos/5');
    expect(candidataUrl(3, 'a1-b2', 'pagina-1')).toBe('/api/modelos/3/documentos/candidatas/a1-b2/pagina-1');
  });

  it('el peso se dice en KB o en MB con coma', () => {
    expect(tamanoTexto(100)).toBe('1 KB');
    expect(tamanoTexto(820 * 1024)).toBe('820 KB');
    expect(tamanoTexto(2.4 * 1024 * 1024)).toBe('2,4 MB');
  });

  it('marcar una imagen la pone al final de la tira y desmarcarla la quita', () => {
    const tira = [enTira.guardada(7)];
    const conDos = alternarEnTira(alternarEnTira(tira, enTira.delPdf('pagina-1')), enTira.delPc(0));
    expect(conDos).toEqual(['g:7', 'p:pagina-1', 'f:0']);
    expect(alternarEnTira(conDos, 'p:pagina-1')).toEqual(['g:7', 'f:0']);
  });

  it('las fotos nuevas son las que no estaban, en el orden en que se guardaron', () => {
    const foto = (id: number, orden: number, principal = false): ResumenFoto => ({ id, modeloId: 1, nombreFichero: `${id}.jpg`, principal, orden });
    // La principal viene delante aunque su orden sea mayor: el orden de guardado es el que manda.
    expect(idsNuevos([5], [foto(9, 3, true), foto(5, 0), foto(8, 2), foto(7, 1)])).toEqual([7, 8, 9]);
    expect(idsNuevos([5], [foto(5, 0)])).toEqual([]);
  });

  it('el orden a guardar es el de la tira, con las que falten al final', () => {
    const idDe = new Map([
      ['g:5', 5],
      ['p:pagina-1', 8],
      ['f:0', 9],
    ]);
    expect(idsEnOrden(['f:0', 'g:5', 'p:pagina-1'], idDe, [5, 8, 9])).toEqual([9, 5, 8]);
    // Una que alguien ha subido mientras tanto (11) no se pierde; una clave sin id (no guardada) se salta.
    expect(idsEnOrden(['f:0', 'f:1', 'g:5'], idDe, [5, 9, 11])).toEqual([9, 5, 11]);
  });

  it('mismo orden solo si son los mismos ids en el mismo sitio', () => {
    expect(mismoOrden([1, 2, 3], [1, 2, 3])).toBe(true);
    expect(mismoOrden([1, 2, 3], [2, 1, 3])).toBe(false);
    expect(mismoOrden([1, 2], [1, 2, 3])).toBe(false);
  });

  it('el aviso de las fotos añadidas va en singular o plural', () => {
    expect(textoFotosAnadidas(1)).toBe('1 foto añadida al modelo');
    expect(textoFotosAnadidas(3)).toBe('3 fotos añadidas al modelo');
  });
});

describe('puntoEvento', () => {
  it('el color del punto dice cómo está la modificación', () => {
    expect(puntoEvento({ tipo: 'MODIFICACION', estado: 'HECHA' })).toEqual({ clase: 'hecha-p', texto: 'Hecha' });
    expect(puntoEvento({ tipo: 'MODIFICACION', estado: 'DESCARTADA' })).toEqual({ clase: 'descartada-p', texto: 'Descartada' });
    for (const estado of ['NUEVA', 'VISTA', 'EN_CURSO'] as const) {
      expect(puntoEvento({ tipo: 'MODIFICACION', estado })).toEqual({ clase: 'abierta-p', texto: 'Sin hacer' });
    }
  });

  it('los cambios de etapa van en azul y los de nombre con el punto de siempre', () => {
    expect(puntoEvento({ tipo: 'ETAPA', estado: null }).clase).toBe('etapa-p');
    expect(puntoEvento({ tipo: 'CODIGO', estado: null })).toEqual({ clase: '', texto: null });
  });
});
