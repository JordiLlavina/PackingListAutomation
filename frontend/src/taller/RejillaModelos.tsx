// La rejilla de modelos del taller, la de la maqueta: chips de filtro (todos,
// cada cliente, y las etapas que interesan en el taller) y una tarjeta por
// modelo con su foto. Carga ella misma la lista, así Inicio y Modelos solo
// tienen que ponerla. El filtro va en la dirección (?filtro=AMI) para que al
// volver de una ficha siga elegido.
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Etapa from '../comun/Etapa';
import FotoModelo from '../comun/FotoModelo';
import { filtrarRejilla, type ModeloResumen } from '../comun/modelos';

const CHIPS_ETAPA: [string, string][] = [
  ['PROTOTIPO', 'Prototipos'],
  ['SMS', 'Muestras'],
  ['PRODUCCION', 'Producción'],
];

export default function RejillaModelos({ onElegir }: { onElegir: (id: number) => void }) {
  const [modelos, setModelos] = useState<ModeloResumen[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [params, setParams] = useSearchParams();
  const filtro = params.get('filtro') ?? 'TODOS';

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
  const chips: [string, string][] = [['TODOS', 'Todos'], ...clientes.map((c): [string, string] => [c, c]), ...CHIPS_ETAPA];
  const elegir = (clave: string) => setParams(clave === 'TODOS' ? {} : { filtro: clave }, { replace: true });
  const lista = filtrarRejilla(modelos, filtro);

  return (
    <>
      <div className="chips">
        {chips.map(([clave, texto]) => (
          <button key={clave} className="chip" type="button" aria-pressed={filtro === clave} onClick={() => elegir(clave)}>
            {texto}
          </button>
        ))}
      </div>
      {lista.length === 0 ? (
        <p className="ayuda">Ningún modelo con este filtro.</p>
      ) : (
        <div className="rejilla">
          {lista.map((m) => (
            <button key={m.id} className="tarjeta-modelo" type="button" onClick={() => onElegir(m.id)}>
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
