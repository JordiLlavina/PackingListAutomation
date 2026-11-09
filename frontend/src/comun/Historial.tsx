// La línea de tiempo de un modelo, la de la maqueta: un evento por fila, de lo
// más nuevo a lo más viejo (así llega de la API), con el punto azul en los
// cambios de etapa y el reproductor cuando el evento trae nota de voz. La
// usan las dos caras.
import Audio from './Audio';
import type { Evento } from './modelos';
import { hace } from './tiempo';

export default function Historial({ eventos }: { eventos: Evento[] }) {
  if (eventos.length === 0) return <p className="ayuda">Todavía no ha pasado nada en este modelo.</p>;
  return (
    <div className="linea">
      {eventos.map((e, i) => (
        <div className="evento" key={`${e.fecha}-${i}`}>
          <span className={e.tipo === 'ETAPA' ? 'punto etapa-p' : 'punto'} />
          <div>
            <div className="cuando">{hace(e.fecha)}</div>
            <div className="que">{e.texto}</div>
            <div className="quien">{e.quien}</div>
            {e.audioAdjuntoId !== null && <Audio adjuntoId={e.audioAdjuntoId} compacto />}
          </div>
        </div>
      ))}
    </div>
  );
}
