// Paso 2: «¿Qué has cambiado?». Un botón grande por tipo de cambio, con su
// icono; al tocar uno se pasa a las fotos.
import { Navigate, useNavigate } from 'react-router';
import { Icono } from '../../comun/iconos';
import { useTitulo } from '../Shell';
import { useBorrador } from './Borrador';
import Pasos from './Pasos';
import { useTiposCambio } from './tipos';

export default function QueHasCambiado() {
  const navegar = useNavigate();
  const { modelo, tipo, elegirTipo } = useBorrador();
  const { tipos, error, reintentar } = useTiposCambio();
  useTitulo(modelo ? `${modelo.referencia} · ${modelo.nombre}` : null);

  if (!modelo) return <Navigate to="/cambio" replace />;

  return (
    <>
      <Pasos />
      <h2 className="pregunta">¿Qué has cambiado?</h2>
      {error ? (
        <div className="vacio">
          <b>{error}</b>
          <button className="boton" type="button" onClick={reintentar}>
            Reintentar
          </button>
        </div>
      ) : (
        <div className="opciones">
          {tipos?.map((t) => (
            <button
              key={t.clave}
              className="opcion"
              type="button"
              aria-pressed={tipo === t.clave}
              onClick={() => {
                elegirTipo(t.clave);
                navegar('/cambio/fotos');
              }}
            >
              <Icono nombre={t.icono} />
              <span>{t.texto}</span>
            </button>
          ))}
        </div>
      )}
    </>
  );
}
