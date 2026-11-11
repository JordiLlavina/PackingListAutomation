// La línea de tiempo de un modelo, la de la maqueta: un evento por fila, de lo
// más nuevo a lo más viejo (así llega de la API), con el reproductor cuando el
// evento trae nota de voz. El color del punto dice si una modificación ya está
// hecha (verde), sigue sin hacer (ámbar) o se descartó (gris); los cambios de
// etapa van en azul. La usan las dos caras; con `enlace` (despacho), cada
// modificación es clicable y lleva a su vista de solo lectura.
import type { MouseEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import Audio from './Audio';
import { puntoEvento, type Evento } from './modelos';
import { hace } from './tiempo';

export default function Historial({ eventos, enlace }: { eventos: Evento[]; enlace?: (modificacionId: number) => string }) {
  const navegar = useNavigate();
  if (eventos.length === 0) return <p className="ayuda">Todavía no ha pasado nada en este modelo.</p>;
  const hayModificaciones = eventos.some((e) => e.tipo === 'MODIFICACION' && e.estado !== null);

  return (
    <>
      {hayModificaciones && (
        <p className="leyenda-historial" aria-hidden="true">
          <span className="punto hecha-p" /> Hecha <span className="punto abierta-p" /> Sin hacer <span className="punto descartada-p" /> Descartada
        </p>
      )}
      <div className="linea">
        {eventos.map((e, i) => {
          const punto = puntoEvento(e);
          const destino = enlace && e.modificacionId !== null ? enlace(e.modificacionId) : null;
          // Toda la fila lleva a la modificación, menos lo que ya es interactivo dentro (el reproductor, el enlace).
          const alPulsar = destino
            ? (ev: MouseEvent<HTMLDivElement>) => {
                if (!(ev.target as HTMLElement).closest('a, button, audio, input')) navegar(destino);
              }
            : undefined;
          return (
            <div className={destino ? 'evento clicable' : 'evento'} key={`${e.fecha}-${i}`} onClick={alPulsar}>
              <span className={`punto ${punto.clase}`.trim()} title={punto.texto ?? undefined} aria-label={punto.texto ?? undefined} role={punto.texto ? 'img' : undefined} />
              <div>
                <div className="cuando">{hace(e.fecha)}</div>
                <div className="que">{e.texto}</div>
                <div className="quien">{e.quien}</div>
                {e.audioAdjuntoId !== null && <Audio adjuntoId={e.audioAdjuntoId} compacto />}
                {destino && (
                  <Link className="ver-modificacion" to={destino}>
                    Ver la modificación
                  </Link>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
}
