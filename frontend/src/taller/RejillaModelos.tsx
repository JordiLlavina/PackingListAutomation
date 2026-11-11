// La rejilla de modelos del taller: arriba los filtros (FiltrosModelos, los
// mismos que en la Bandeja de Entrada) y una tarjeta por modelo con su foto.
// Carga ella misma la lista, así Inicio y Modelos solo tienen que ponerla, y
// filtra en el navegador (son pocos y ya están todos).
import { useCallback, useEffect, useState } from 'react';
import { ErrorApi, api } from '../comun/api';
import Etapa from '../comun/Etapa';
import FotoModelo from '../comun/FotoModelo';
import { filtrarRejilla, type ModeloResumen } from '../comun/modelos';
import FiltrosModelos, { useFiltrosEnDireccion } from './FiltrosModelos';

export default function RejillaModelos({ onElegir }: { onElegir: (id: number, modelo: ModeloResumen) => void }) {
  const [modelos, setModelos] = useState<ModeloResumen[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { filtros, poner } = useFiltrosEnDireccion();

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
  const lista = filtrarRejilla(modelos, filtros);

  return (
    <>
      <FiltrosModelos filtros={filtros} clientes={clientes} onCambiar={poner} />
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
