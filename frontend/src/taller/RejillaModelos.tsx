// La rejilla de modelos del taller: arriba los filtros, como en despacho (un
// campo de texto que busca dentro de la referencia y del nombre, el cliente
// en un desplegable y los chips de etapa), y una tarjeta por modelo con su
// foto. Carga ella misma la lista, así Inicio y Modelos solo tienen que
// ponerla, y filtra en el navegador (son pocos y ya están todos). Los filtros
// van en la dirección (?q=770&cliente=AMI&etapa=SMS) para que al volver de
// una ficha sigan puestos.
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Etapa from '../comun/Etapa';
import FotoModelo from '../comun/FotoModelo';
import { ETAPAS, filtrarRejilla, type Etapa as TipoEtapa, type ModeloResumen } from '../comun/modelos';

// Los de despacho, con «Todos» delante.
const CHIPS_ETAPA: [TipoEtapa | '', string][] = [
  ['', 'Todos'],
  ['PROTOTIPO', 'Prototipos'],
  ['SMS', 'Muestras'],
  ['PRODUCCION', 'Producción'],
  ['INACTIVO', 'Inactivos'],
];

export default function RejillaModelos({ onElegir }: { onElegir: (id: number, modelo: ModeloResumen) => void }) {
  const [modelos, setModelos] = useState<ModeloResumen[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [params, setParams] = useSearchParams();
  const texto = params.get('q') ?? '';
  const cliente = params.get('cliente') ?? '';
  const etapaParam = params.get('etapa') ?? '';
  const etapa: TipoEtapa | '' = (ETAPAS as string[]).includes(etapaParam) ? (etapaParam as TipoEtapa) : '';

  const cargar = useCallback(() => {
    setError(null);
    api<ModeloResumen[]>('/api/modelos')
      .then(setModelos)
      .catch((e: unknown) => setError(e instanceof ErrorApi ? e.message : 'No se han podido cargar los modelos'));
  }, []);

  useEffect(cargar, [cargar]);

  if (error) {
    return (
      <div className="vacio">
        <b>{error}</b>
        <span>Comprueba la conexión y vuelve a intentarlo.</span>
        <button className="boton" type="button" onClick={cargar}>
          Reintentar
        </button>
      </div>
    );
  }
  if (modelos === null) return null;
  if (modelos.length === 0) {
    return (
      <div className="vacio">
        <b>Todavía no hay modelos. Pide a despacho que los dé de alta.</b>
      </div>
    );
  }

  const clientes = [...new Set(modelos.map((m) => m.cliente))].sort();
  // Cambia un filtro y deja los demás; uno vacío no se escribe en la dirección.
  const poner = (clave: 'q' | 'cliente' | 'etapa', valor: string) =>
    setParams(
      (antes) => {
        const despues = new URLSearchParams(antes);
        if (valor) despues.set(clave, valor);
        else despues.delete(clave);
        return despues;
      },
      { replace: true },
    );
  const lista = filtrarRejilla(modelos, { texto, cliente, etapa });

  return (
    <>
      <div className="filtros-rejilla">
        <input
          type="search"
          className="buscar"
          value={texto}
          placeholder="Buscar referencia o nombre"
          aria-label="Buscar por referencia o nombre"
          onChange={(e) => poner('q', e.target.value)}
        />
        <select className="cliente" value={cliente} aria-label="Cliente" onChange={(e) => poner('cliente', e.target.value)}>
          <option value="">Todos los clientes</option>
          {clientes.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        {CHIPS_ETAPA.map(([clave, txt]) => (
          <button key={clave || 'todos'} className="chip" type="button" aria-pressed={etapa === clave} onClick={() => poner('etapa', clave)}>
            {txt}
          </button>
        ))}
      </div>
      {lista.length === 0 ? (
        <p className="ayuda">Ningún modelo con estos filtros.</p>
      ) : (
        <div className="rejilla">
          {lista.map((m) => (
            <button key={m.id} className="tarjeta-modelo" type="button" onClick={() => onElegir(m.id, m)}>
              <FotoModelo modelo={m} />
              <div className="pie">
                <span className="ref">{m.referencia}</span>
                <span className="nom">{m.nombre}</span>
                <Etapa etapa={m.etapa} cliente={m.cliente} />
              </div>
            </button>
          ))}
        </div>
      )}
    </>
  );
}
