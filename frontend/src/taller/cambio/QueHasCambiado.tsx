// Paso 2: «¿Qué has cambiado?». Un botón grande por razón de cambio, con su
// icono; se pueden marcar varias (petición del usuario), y «Siguiente» pasa a
// las fotos cuando hay al menos una.
import { Navigate, useNavigate } from 'react-router';
import { Icono } from '../../comun/iconos';
import { useTitulo } from '../Shell';
import { useBorrador } from './Borrador';
import Pasos from './Pasos';
import { useTiposCambio } from './tipos';

export default function QueHasCambiado() {
  const navegar = useNavigate();
  const { modelo, tipos: marcados, alternarTipo } = useBorrador();
  const { tipos, error, reintentar } = useTiposCambio();
  useTitulo(modelo ? `${modelo.referencia} · ${modelo.nombre}` : null);

  if (!modelo) return <Navigate to="/cambio" replace />;

  return (
    <>
      <Pasos />
      <h2 className="pregunta">¿Qué has cambiado?</h2>
      <p className="sub">Puedes marcar varias.</p>
      {error ? (
        <div className="vacio">
          <b>{error}</b>
          <button className="boton" type="button" onClick={reintentar}>
            Reintentar
          </button>
        </div>
      ) : (
        <>
          <div className="opciones">
            {tipos?.map((t) => (
              <button key={t.clave} className="opcion" type="button" aria-pressed={marcados.includes(t.clave)} onClick={() => alternarTipo(t.clave)}>
                <Icono nombre={t.icono} />
                <span>{t.texto}</span>
              </button>
            ))}
          </div>
          <div className="acciones">
            <button className="boton ok ancho" type="button" disabled={marcados.length === 0} onClick={() => navegar('/cambio/fotos')}>
              {marcados.length > 1 ? `Siguiente (${marcados.length})` : 'Siguiente'}
            </button>
          </div>
        </>
      )}
    </>
  );
}
