// La barra de progreso de 4 tramos de la maqueta (modelo, tipo, fotos, voz):
// marcados hasta el paso en el que se está.
import { useLocation } from 'react-router';
import { pasoActual } from './reglas';

export default function Pasos() {
  const paso = pasoActual(useLocation().pathname);
  if (paso === null) return null;
  return (
    <div className="pasos" role="progressbar" aria-label="Pasos" aria-valuemin={1} aria-valuemax={4} aria-valuenow={paso}>
      {[1, 2, 3, 4].map((n) => (
        <span key={n} className={n <= paso ? 'hecho' : undefined} />
      ))}
    </div>
  );
}
