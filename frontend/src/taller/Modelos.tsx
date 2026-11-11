// Los modelos en el taller: la rejilla entera, con sus filtros.
import { useNavigate } from 'react-router';
import RejillaModelos from './RejillaModelos';

export default function Modelos() {
  const navegar = useNavigate();
  return (
    <>
      <RejillaModelos onElegir={(id) => navegar(`/modelos/${id}`)} />
    </>
  );
}
