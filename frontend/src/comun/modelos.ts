// Los modelos tal como los da la API (/api/modelos) y lo poco que se calcula
// con ellos en el navegador. Las fechas llegan en ISO sin zona (hora local
// del servidor), que es como las lee hace().

export type Etapa = 'PROTOTIPO' | 'SMS' | 'PRODUCCION' | 'INACTIVO';
export type TipoArticulo = 'BOLSO' | 'CINTURON';

/** Un modelo en una lista o en la cabecera de su ficha. */
export type ModeloResumen = {
  id: number;
  cliente: string;
  codigo: string | null;
  nombrePrototipo: string | null;
  /** Lo que se enseña: el código o, si aún no lo tiene, el nombre de prototipo. */
  referencia: string;
  nombre: string;
  tipo: TipoArticulo;
  etapa: Etapa;
  fotoPrincipalId: number | null;
  etiquetaImpresaEn: string | null;
  fechaEtapa: string;
};

/** Un nombre que ha tenido el modelo; `hasta` nulo es el de hoy. */
export type CodigoHistorico = { codigo: string; desde: string; hasta: string | null; motivo: string | null };

export type ResumenFoto = { id: number; modeloId: number; nombreFichero: string; principal: boolean; orden: number };

export type DocumentoResumen = { id: number; nombre: string; mime: string; tamano: number; fecha: string };

/** Una entrada de la línea de tiempo: un cambio de etapa, un nombre nuevo o una modificación. */
export type Evento = {
  fecha: string;
  tipo: 'MODIFICACION' | 'ETAPA' | 'CODIGO';
  texto: string;
  quien: string;
  modificacionId: number | null;
  audioAdjuntoId: number | null;
};

export type ModeloFicha = {
  modelo: ModeloResumen;
  temporadas: string[];
  codigos: CodigoHistorico[];
  fotos: ResumenFoto[];
  documentos: DocumentoResumen[];
  lineaTiempo: Evento[];
  /** Los ids de los modelos que se juntaron con este y ya no existen, del más reciente al más antiguo. */
  fusionadosDesde: number[];
};

/**
 * Lo que pasaría al juntar dos modelos (/api/modelos/{id}/referencia/previsualizar
 * y el cuerpo del 409): sobrevive el de menor id y los números son lo que se
 * lleva el absorbido. `absorbido` nulo: la referencia está libre, no se junta nada.
 */
export type Fusion = {
  superviviente: number;
  absorbido: number | null;
  articulos: number;
  fotos: number;
  modificaciones: number;
  documentos: number;
};

export type Cliente = { clave: string; nombre: string };
export type Temporada = { id: number; temporada: string };

export const ETAPAS: Etapa[] = ['PROTOTIPO', 'SMS', 'PRODUCCION', 'INACTIVO'];

const TEXTO_ETAPA: Record<Etapa, string> = {
  PROTOTIPO: 'Prototipo',
  SMS: 'Muestras',
  PRODUCCION: 'Producción',
  INACTIVO: 'Inactivo',
};

export function etapaTexto(etapa: Etapa): string {
  return TEXTO_ETAPA[etapa];
}

export function fotoUrl(id: number): string {
  return `/api/fotos/${id}`;
}

/**
 * El filtro de la rejilla del taller, el de la maqueta: `TODOS` deja pasar
 * todos; cualquier otro valor es la clave de un cliente o una etapa, y pasan
 * los modelos de ese cliente o en esa etapa.
 */
export function filtrarRejilla(modelos: ModeloResumen[], filtro: string): ModeloResumen[] {
  if (filtro === 'TODOS') return modelos;
  return modelos.filter((m) => m.cliente === filtro || m.etapa === filtro);
}

function cuenta(n: number, uno: string, varios: string): string {
  return `${n} ${n === 1 ? uno : varios}`;
}

/** El aviso antes de juntar: qué se junta y que no tiene vuelta atrás. */
export function textoFusion(codigo: string, fusion: Fusion): string {
  return (
    `Se va a juntar con ${codigo}: ${cuenta(fusion.articulos, 'artículo', 'artículos')}, ` +
    `${cuenta(fusion.fotos, 'foto', 'fotos')}, ` +
    `${cuenta(fusion.modificaciones, 'modificación', 'modificaciones')}. No se puede deshacer.`
  );
}

/** «Antes era el #12» (o «el #12 y el #15») para la cabecera de un modelo que ha absorbido otros. */
export function antesEra(fusionadosDesde: number[]): string | null {
  if (fusionadosDesde.length === 0) return null;
  const ids = fusionadosDesde.map((id) => `el #${id}`);
  const ultimo = ids.pop();
  return `Antes era ${ids.length > 0 ? `${ids.join(', ')} y ${ultimo}` : ultimo}`;
}

/**
 * Los prototipos con los que se puede juntar un modelo importado: los del
 * cliente sin código (salvo él mismo), filtrados por el texto en la
 * referencia de prototipo o el nombre, sin mirar mayúsculas.
 */
export function prototiposCandidatos(modelos: ModeloResumen[], esteId: number, texto: string): ModeloResumen[] {
  const buscado = texto.trim().toUpperCase();
  return modelos.filter(
    (m) =>
      m.codigo === null &&
      m.id !== esteId &&
      (buscado === '' || m.referencia.toUpperCase().includes(buscado) || m.nombre.toUpperCase().includes(buscado)),
  );
}
