// La ficha de un modelo en el taller, la de la maqueta: foto, referencia,
// nombre, cliente y etapa; el botón para avisar de un cambio en este modelo, y
// el historial de lo que le ha pasado.
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Etapa from '../comun/Etapa';
import FotoModelo from '../comun/FotoModelo';
import Historial from '../comun/Historial';
import { Icono } from '../comun/iconos';
import type { ModeloFicha } from '../comun/modelos';

export default function FichaModelo() {
  const { id } = useParams();
  const navegar = useNavigate();
  const [ficha, setFicha] = useState<ModeloFicha | null>(null);
  const [error, setError] = useState<string | null>(null);

  const cargar = useCallback(() => {
    setError(null);
    setFicha(null);
    api<ModeloFicha>(`/api/modelos/${id}`)
      .then(setFicha)
      .catch((e: unknown) => setError(e instanceof ErrorApi ? e.message : 'No se ha podido cargar el modelo'));
  }, [id]);

  useEffect(cargar, [cargar]);

  if (error) {
    return (
      <div className="vacio">
        <b>{error}</b>
        <button className="boton" type="button" onClick={cargar}>
          Reintentar
        </button>
      </div>
    );
  }
  if (!ficha) return null;
  const m = ficha.modelo;

  return (
    <div className="detalle">
      <div className="ficha grande">
        <FotoModelo modelo={m} />
        <div className="texto">
          <b>{m.referencia}</b>
          <span className="nombre">{m.nombre}</span>
          <span>{m.cliente}</span>
          <Etapa etapa={m.etapa} />
        </div>
      </div>
      <div className="acciones" style={{ margin: 0 }}>
        <button className="boton marca ancho" type="button" onClick={() => navegar(`/cambio?modelo=${m.id}`)}>
          <Icono nombre="mic" /> He cambiado algo en este modelo
        </button>
      </div>
      <div className="fila-titulo">
        <h2>Historial</h2>
      </div>
      <Historial eventos={ficha.lineaTiempo} />
    </div>
  );
}
