import { describe, expect, it } from 'vitest';
import {
  alternarMarcada,
  antesEra,
  candidataUrl,
  clavesMarcadas,
  documentoUrl,
  elegirPrincipal,
  etapaTexto,
  filtrarRejilla,
  fotoUrl,
  prototiposCandidatos,
  puntoEvento,
  tamanoTexto,
  textoFotosAnadidas,
  textoFusion,
  type CandidataImagen,
  type EleccionImagenes,
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
  const sin = { texto: '', cliente: '', etapa: '' } as const;

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
  const candidatas: CandidataImagen[] = [
    { clave: 'imagen-1', ancho: 400, alto: 400, origen: 'imagen' },
    { clave: 'imagen-2', ancho: 400, alto: 400, origen: 'imagen' },
    { clave: 'pagina-1', ancho: 826, alto: 1169, origen: 'página 1' },
  ];
  const nada: EleccionImagenes = { marcadas: [], principal: null };

  it('las direcciones del documento y de cada candidata', () => {
    expect(documentoUrl(5)).toBe('/api/documentos/5');
    expect(candidataUrl(3, 'a1-b2', 'pagina-1')).toBe('/api/modelos/3/documentos/candidatas/a1-b2/pagina-1');
  });

  it('el peso se dice en KB o en MB con coma', () => {
    expect(tamanoTexto(100)).toBe('1 KB');
    expect(tamanoTexto(820 * 1024)).toBe('820 KB');
    expect(tamanoTexto(2.4 * 1024 * 1024)).toBe('2,4 MB');
  });

  it('la principal solo puede ser una marcada y se pierde al desmarcarla', () => {
    expect(elegirPrincipal(nada, 'imagen-1')).toEqual(nada);
    const dos = alternarMarcada(alternarMarcada(nada, 'pagina-1'), 'imagen-1');
    const conPrincipal = elegirPrincipal(dos, 'imagen-1');
    expect(conPrincipal.principal).toBe('imagen-1');
    expect(alternarMarcada(conPrincipal, 'pagina-1')).toEqual({ marcadas: ['imagen-1'], principal: 'imagen-1' });
    expect(alternarMarcada(conPrincipal, 'imagen-1')).toEqual({ marcadas: ['pagina-1'], principal: null });
  });

  it('las marcadas se mandan en el orden de las candidatas', () => {
    const marcadas = alternarMarcada(alternarMarcada(nada, 'pagina-1'), 'imagen-1');
    expect(clavesMarcadas(candidatas, marcadas)).toEqual(['imagen-1', 'pagina-1']);
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
