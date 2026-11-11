// El chip de etapa de la maqueta: punto de color y texto en versalitas. Con
// cliente, «Producción · AMI», como en las tarjetas de la rejilla.
import { etapaTexto, type Etapa as TipoEtapa } from './modelos';

export default function Etapa({ etapa, cliente }: { etapa: TipoEtapa; cliente?: string }) {
  return (
    <span className="etapa" data-e={etapa}>
      {etapaTexto(etapa)}
      {cliente ? ` · ${cliente}` : ''}
    </span>
  );
}
