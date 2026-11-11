// «Enviado a despacho». El borrador ya no hace falta: se vacía al llegar
// aquí, así que salir por la barra de abajo tampoco deja nada a medias.
import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { Icono } from '../../comun/iconos';
import { useBorrador } from './Borrador';

export default function Enviado() {
  const navegar = useNavigate();
  const { reiniciar } = useBorrador();

  useEffect(reiniciar, [reiniciar]);

  return (
    <div className="enviado">
      <div className="tick">
        <Icono nombre="check" />
      </div>
      <h2 className="pregunta" style={{ margin: 0 }}>
        Enviado a despacho
      </h2>
      <p className="sub">Lo verás en «Mis avisos» cuando lo lean y cuando esté hecho.</p>
      <div className="acciones" style={{ width: '100%', maxWidth: 520 }}>
        <button
          className="boton marca ancho"
          type="button"
          onClick={() => {
            reiniciar();
            navegar('/');
          }}
        >
          Volver al inicio
        </button>
      </div>
    </div>
  );
}
