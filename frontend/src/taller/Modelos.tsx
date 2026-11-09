// Los modelos en el taller: la rejilla entera, con sus filtros.
import { useNavigate } from 'react-router';
import RejillaModelos from './RejillaModelos';

export default function Modelos() {
  const navegar = useNavigate();
  return (
    <>
      <h2 className="saludo">Modelos</h2>
      <p className="sub">Toca un modelo para ver su ficha, o escanea la etiqueta del patrón.</p>
      <RejillaModelos onElegir={(id) => navegar(`/modelos/${id}`)} />
    </>
  );
}
