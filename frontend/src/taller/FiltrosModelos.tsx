// Los filtros del taller, los mismos en la rejilla de modelos (Inicio y
// Modelos) y en la Bandeja de Entrada: un campo de texto que busca dentro de
// la referencia y del nombre, el cliente en un desplegable y los chips de
// etapa (sin «Inactivos», por decisión del usuario: en el taller no se
// trabaja con ellos). En la rejilla de modelos va además la etiqueta
// «Cinturones: no / sí», que por defecto deja fuera los cinturones; en la
// Bandeja de Entrada no, porque ahí se filtran órdenes y una orden de un
// cinturón no puede quedarse escondida. Van en la dirección
// (?q=770&cliente=AMI&etapa=SMS&cinturones=si) para que al volver de una
// ficha o de una orden sigan puestos.
import { useSearchParams } from 'react-router';
import { ETAPAS, textoCinturones, type Etapa as TipoEtapa } from '../comun/modelos';

export type FiltrosTaller = { texto: string; cliente: string; etapa: TipoEtapa | ''; cinturones: boolean };

export type ClaveFiltro = 'q' | 'cliente' | 'etapa' | 'cinturones';

const CHIPS_ETAPA: [TipoEtapa | '', string][] = [
  ['', 'Todos'],
  ['PROTOTIPO', 'Prototipos'],
  ['SMS', 'Muestras'],
  ['PRODUCCION', 'Producción'],
];

/** Los filtros leídos de la dirección, y cómo cambiar uno dejando los demás (uno vacío no se escribe). */
export function useFiltrosEnDireccion(): { filtros: FiltrosTaller; poner: (clave: ClaveFiltro, valor: string) => void } {
  const [params, setParams] = useSearchParams();
  const etapaParam = params.get('etapa') ?? '';
  const filtros: FiltrosTaller = {
    texto: params.get('q') ?? '',
    cliente: params.get('cliente') ?? '',
    etapa: (ETAPAS as string[]).includes(etapaParam) ? (etapaParam as TipoEtapa) : '',
    cinturones: params.get('cinturones') === 'si',
  };
  const poner = (clave: ClaveFiltro, valor: string) =>
    setParams(
      (antes) => {
        const despues = new URLSearchParams(antes);
        if (valor) despues.set(clave, valor);
        else despues.delete(clave);
        return despues;
      },
      { replace: true },
    );
  return { filtros, poner };
}

export default function FiltrosModelos({
  filtros,
  clientes,
  placeholder = 'Buscar referencia o nombre',
  conCinturones = false,
  onCambiar,
}: {
  filtros: FiltrosTaller;
  /** Las claves de cliente que se ofrecen (las que hay en la lista que se filtra). */
  clientes: string[];
  placeholder?: string;
  /** Si se ofrece la etiqueta «Cinturones: no / sí» (solo donde se filtran modelos). */
  conCinturones?: boolean;
  onCambiar: (clave: ClaveFiltro, valor: string) => void;
}) {
  return (
    <div className="filtros-rejilla">
      <input
        type="search"
        className="buscar"
        value={filtros.texto}
        placeholder={placeholder}
        aria-label={placeholder}
        onChange={(e) => onCambiar('q', e.target.value)}
      />
      <select className="cliente" value={filtros.cliente} aria-label="Cliente" onChange={(e) => onCambiar('cliente', e.target.value)}>
        <option value="">Todos los clientes</option>
        {clientes.map((c) => (
          <option key={c} value={c}>
            {c}
          </option>
        ))}
      </select>
      {CHIPS_ETAPA.map(([clave, txt]) => (
        <button key={clave || 'todos'} className="chip" type="button" aria-pressed={filtros.etapa === clave} onClick={() => onCambiar('etapa', clave)}>
          {txt}
        </button>
      ))}
      {conCinturones && (
        <button
          className="chip"
          type="button"
          aria-pressed={filtros.cinturones}
          title={filtros.cinturones ? 'Se ven también los cinturones: toca para esconderlos' : 'Los cinturones no se ven: toca para verlos'}
          onClick={() => onCambiar('cinturones', filtros.cinturones ? '' : 'si')}
        >
          {textoCinturones(filtros.cinturones)}
        </button>
      )}
    </div>
  );
}
