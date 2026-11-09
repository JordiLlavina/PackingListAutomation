// Paso 1: «¿Qué modelo has cambiado?». Escanear la etiqueta del patrón (lo
// más rápido) o tocarlo en la rejilla. Desde la ficha de un modelo se llega
// con ?modelo=ID y este paso se salta: se carga el modelo y se va al tipo.
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ErrorApi, api } from '../../comun/api';
import { Icono } from '../../comun/iconos';
import type { ModeloFicha } from '../../comun/modelos';
import RejillaModelos from '../RejillaModelos';
import { useBorrador } from './Borrador';
import Pasos from './Pasos';

export default function ElegirModelo() {
  const navegar = useNavigate();
  const [params] = useSearchParams();
  const borrador = useBorrador();
  const { elegirModelo } = borrador;
  const pedido = params.get('modelo');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!pedido) return;
    // Ya es el del borrador (volver a entrar desde su ficha): se sigue con lo que había.
    if (borrador.modelo && String(borrador.modelo.id) === pedido) {
      navegar('/cambio/tipo', { replace: true });
      return;
    }
    let vigente = true;
    setError(null);
    api<ModeloFicha>(`/api/modelos/${encodeURIComponent(pedido)}`)
      .then((ficha) => {
        if (!vigente) return;
        elegirModelo(ficha.modelo);
        navegar('/cambio/tipo', { replace: true });
      })
      .catch((e: unknown) => vigente && setError(e instanceof ErrorApi ? e.message : 'No se ha podido cargar el modelo'));
    return () => {
      vigente = false;
    };
    // Solo al llegar con otro ?modelo: el borrador cambia justo por esto mismo.
  }, [pedido, navegar, elegirModelo]);

  // Cargando el modelo de la ficha: un instante, sin pintar la rejilla debajo.
  if (pedido && !error) return null;

  return (
    <>
      <Pasos />
      <h2 className="pregunta">¿Qué modelo has cambiado?</h2>
      {error && <p className="error-envio" role="alert">{error}. Elígelo en la lista.</p>}
      <div className="acciones" style={{ margin: '0 0 18px' }}>
        <button className="boton marca ancho" type="button" onClick={() => navegar('/escanear?para=cambio')}>
          <Icono nombre="qr" /> Escanear la etiqueta del patrón
        </button>
      </div>
      <RejillaModelos
        onElegir={(_, modelo) => {
          elegirModelo(modelo);
          navegar('/cambio/tipo');
        }}
      />
    </>
  );
}
