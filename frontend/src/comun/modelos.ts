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

/** Una imagen sacada de un PDF que podría ser foto del modelo; `origen` es «imagen» o «página N». */
export type CandidataImagen = { clave: string; ancho: number; alto: number; origen: string };

/**
 * Lo que contesta subir un documento: el documento guardado y, si era un PDF,
 * el lote de sus candidatas (media hora de vida) para elegir cuáles son fotos
 * del modelo; si no, `loteId` nulo y ninguna candidata.
 */
export type Subida = { documento: DocumentoResumen; loteId: string | null; candidatas: CandidataImagen[] };

/** Una entrada de la línea de tiempo: un cambio de etapa, un nombre nuevo o una modificación. */
export type Evento = {
  fecha: string;
  tipo: 'MODIFICACION' | 'ETAPA' | 'CODIGO';
  texto: string;
  quien: string;
  modificacionId: number | null;
  audioAdjuntoId: number | null;
  /** Cómo está hoy la modificación del evento; null si el evento no es una modificación. */
  estado: 'NUEVA' | 'VISTA' | 'EN_CURSO' | 'HECHA' | 'DESCARTADA' | null;
};

/**
 * El punto de un evento en el historial: el color dice si una modificación ya
 * está hecha, sigue abierta o se descartó; los cambios de etapa y de nombre
 * tienen el suyo. El texto es lo que se lee al pasar por encima (y el lector de pantalla).
 */
export function puntoEvento(e: Pick<Evento, 'tipo' | 'estado'>): { clase: string; texto: string | null } {
  if (e.tipo === 'ETAPA') return { clase: 'etapa-p', texto: 'Cambio de etapa' };
  if (e.tipo !== 'MODIFICACION' || e.estado === null) return { clase: '', texto: null };
  if (e.estado === 'HECHA') return { clase: 'hecha-p', texto: 'Hecha' };
  if (e.estado === 'DESCARTADA') return { clase: 'descartada-p', texto: 'Descartada' };
  return { clase: 'abierta-p', texto: 'Sin hacer' };
}

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

/** Los filtros de la rejilla del taller: texto libre, cliente y etapa; vacío = sin ese filtro. */
export type FiltroRejilla = { texto: string; cliente: string; etapa: Etapa | '' };

/**
 * Para buscar: en mayúsculas, sin acentos y solo letras y cifras, así
 * «ull027al0103» encuentra «ULL027.AL0103» y «bolso» encuentra «BOLSO».
 */
function paraBuscar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
}

/**
 * El filtro de la rejilla del taller, como el de despacho: el texto se busca
 * DENTRO de la referencia (el código o el nombre de prototipo, también el
 * prototipo de un modelo que ya tiene código) y del nombre, así «77» o «770»
 * encuentran «ULL770»; y además, si se eligen, el cliente y la etapa.
 */
export function filtrarRejilla(modelos: ModeloResumen[], filtro: FiltroRejilla): ModeloResumen[] {
  const buscado = paraBuscar(filtro.texto);
  return modelos.filter(
    (m) =>
      (filtro.cliente === '' || m.cliente === filtro.cliente) &&
      (filtro.etapa === '' || m.etapa === filtro.etapa) &&
      (buscado === '' ||
        [m.referencia, m.codigo, m.nombrePrototipo, m.nombre].some((campo) => campo !== null && paraBuscar(campo).includes(buscado))),
  );
}

function cuenta(n: number, uno: string, varios: string): string {
  return `${n} ${n === 1 ? uno : varios}`;
}

/** El aviso antes de juntar: qué se junta y que no tiene vuelta atrás. */
export function textoFusion(codigo: string, fusion: Fusion): string {
  return (
    `Se va a juntar con ${codigo}: ${cuenta(fusion.articulos, 'artículo', 'artículos')}, ` +
    `${cuenta(fusion.fotos, 'foto', 'fotos')}, ` +
    `${cuenta(fusion.modificaciones, 'modificación', 'modificaciones')}` +
    (fusion.documentos > 0 ? `, ${cuenta(fusion.documentos, 'documento', 'documentos')}` : '') +
    '. No se puede deshacer.'
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

export function documentoUrl(id: number): string {
  return `/api/documentos/${id}`;
}

export function candidataUrl(modeloId: number, loteId: string, clave: string): string {
  return `/api/modelos/${modeloId}/documentos/candidatas/${encodeURIComponent(loteId)}/${encodeURIComponent(clave)}`;
}

/** «820 KB», «2,4 MB»: el peso de un documento, redondeado como lo dice una persona. */
export function tamanoTexto(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}

/**
 * Qué candidatas de un PDF ha marcado el despacho como fotos del modelo y
 * cuál de ellas es la principal (o ninguna: entonces la principal del modelo
 * no cambia).
 */
export type EleccionImagenes = { marcadas: string[]; principal: string | null };

/** Marca o desmarca una candidata; si se desmarca la principal, deja de haber principal. */
export function alternarMarcada(eleccion: EleccionImagenes, clave: string): EleccionImagenes {
  if (eleccion.marcadas.includes(clave)) {
    return {
      marcadas: eleccion.marcadas.filter((c) => c !== clave),
      principal: eleccion.principal === clave ? null : eleccion.principal,
    };
  }
  return { marcadas: [...eleccion.marcadas, clave], principal: eleccion.principal };
}

/** La principal solo puede ser una de las marcadas: con otra, no cambia nada. */
export function elegirPrincipal(eleccion: EleccionImagenes, clave: string): EleccionImagenes {
  return eleccion.marcadas.includes(clave) ? { ...eleccion, principal: clave } : eleccion;
}

/** Las claves marcadas en el orden de las candidatas, que es como se guardan en la galería. */
export function clavesMarcadas(candidatas: CandidataImagen[], eleccion: EleccionImagenes): string[] {
  return candidatas.map((c) => c.clave).filter((clave) => eleccion.marcadas.includes(clave));
}

/** «1 foto añadida al modelo», «3 fotos añadidas al modelo». */
export function textoFotosAnadidas(n: number): string {
  return n === 1 ? '1 foto añadida al modelo' : `${n} fotos añadidas al modelo`;
}
