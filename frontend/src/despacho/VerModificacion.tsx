// Una modificación vista desde el historial de un modelo (/app/modificaciones/12):
// el mismo detalle que en la bandeja, pero de solo lectura. Para contestar o
// cambiarla, el enlace la abre en la bandeja.
import { Link, useNavigate, useParams } from 'react-router';
import Modificacion from './Modificacion';

const NADA = () => {};

export default function VerModificacion() {
  const { id } = useParams();
  const navegar = useNavigate();
  const numero = id && /^\d+$/.test(id) ? Number(id) : null;

  const volver = () => {
    if (window.history.length > 1) navegar(-1);
    else navegar('/modelos');
  };

  if (numero === null) {
    return (
      <div className="vacio">
        <b>Esa modificación no existe</b>
        <Link className="boton" to="/modelos">
          Ir a los modelos
        </Link>
      </div>
    );
  }
  return (
    <div className="ver-modificacion-sola">
      <div className="botones">
        <button className="enlace" type="button" onClick={volver}>
          Volver
        </button>
        <span className="ayuda">Solo lectura.</span>
        <Link to={`/bandeja/${numero}`}>Abrirla en la bandeja para contestar o cambiarla</Link>
      </div>
      <Modificacion key={numero} id={numero} onCambiado={NADA} soloLectura />
    </div>
  );
}
