// La ficha de un modelo en el taller, la de la maqueta: foto, referencia,
// nombre, cliente y etapa; el botón para avisar de un cambio en este modelo, sus
// documentos (fichas técnicas: se abren en otra pestaña, con el visor de la
// tablet) y el historial de lo que le ha pasado.
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Etapa from '../comun/Etapa';
import FotoModelo from '../comun/FotoModelo';
import Historial from '../comun/Historial';
import { Icono } from '../comun/iconos';
import { documentoUrl, type ModeloFicha } from '../comun/modelos';
import { useTitulo } from './Shell';

export default function FichaModelo() {
  const { id } = useParams();
  const navegar = useNavigate();
  const [ficha, setFicha] = useState<ModeloFicha | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Solo vale la respuesta de la última petición: al saltar de ficha en ficha
  // (el escáner), una respuesta lenta del modelo anterior no debe pisar la buena.
  const peticion = useRef(0);
  const cargar = useCallback(() => {
    const esta = ++peticion.current;
    setError(null);
    setFicha(null);
    api<ModeloFicha>(`/api/modelos/${id}`)
      .then((f) => esta === peticion.current && setFicha(f))
      .catch((e: unknown) => esta === peticion.current && setError(e instanceof ErrorApi ? e.message : 'No se ha podido cargar el modelo'));
  }, [id]);

  useEffect(cargar, [cargar]);
  useTitulo(ficha?.modelo.referencia ?? null);

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
          <Icono nombre="mic" /> Crear Modificación de este modelo
        </button>
      </div>
      {ficha.documentos.length > 0 && (
        <div className="docs">
          {ficha.documentos.map((d) => (
            <button key={d.id} className="doc" type="button" onClick={() => window.open(documentoUrl(d.id), '_blank', 'noopener')}>
              <Icono nombre="pdf" />
              <span>
                {d.nombre}
                <small>Toca para abrir · vuelve con «atrás»</small>
              </span>
            </button>
          ))}
        </div>
      )}
      <div className="fila-titulo">
        <h2>Historial</h2>
      </div>
      <Historial eventos={ficha.lineaTiempo} />
    </div>
  );
}
