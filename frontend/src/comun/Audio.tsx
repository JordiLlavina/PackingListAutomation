// Reproductor de una nota de voz, el de la maqueta: botón grande de escuchar,
// barra de avance y duración. Por debajo, un <audio> de verdad que pide el
// adjunto a la API. `compacto` es la versión de dentro de la línea de tiempo
// (sin sombra, con filete y el botón más pequeño).
import { useEffect, useRef, useState } from 'react';
import { Icono } from './iconos';
import { mmss } from './tiempo';

export default function Audio({ adjuntoId, compacto = false }: { adjuntoId: number; compacto?: boolean }) {
  const audio = useRef<HTMLAudioElement>(null);
  const [sonando, setSonando] = useState(false);
  const [actual, setActual] = useState(0);
  const [duracion, setDuracion] = useState(0);

  useEffect(() => {
    const a = audio.current;
    if (!a) return;
    const alAvanzar = () => setActual(a.currentTime);
    // Las notas grabadas en Chrome (WebM) no siempre dicen su duración al
    // cargar: Infinity hasta que se oyen enteras. Entonces se enseña lo oído.
    const alSaberDuracion = () => setDuracion(Number.isFinite(a.duration) ? a.duration : 0);
    const alParar = () => setSonando(false);
    const alAcabar = () => {
      setSonando(false);
      setActual(0);
      alSaberDuracion();
    };
    a.addEventListener('timeupdate', alAvanzar);
    a.addEventListener('loadedmetadata', alSaberDuracion);
    a.addEventListener('durationchange', alSaberDuracion);
    a.addEventListener('pause', alParar);
    a.addEventListener('ended', alAcabar);
    return () => {
      a.removeEventListener('timeupdate', alAvanzar);
      a.removeEventListener('loadedmetadata', alSaberDuracion);
      a.removeEventListener('durationchange', alSaberDuracion);
      a.removeEventListener('pause', alParar);
      a.removeEventListener('ended', alAcabar);
    };
  }, []);

  const alternar = () => {
    const a = audio.current;
    if (!a) return;
    if (sonando) {
      a.pause();
    } else {
      setSonando(true);
      a.play().catch(() => setSonando(false));
    }
  };

  const avance = duracion > 0 ? Math.min(100, (actual / duracion) * 100) : 0;
  return (
    <div className={compacto ? 'audio compacto' : 'audio'}>
      <audio ref={audio} src={`/api/adjuntos/${adjuntoId}`} preload="metadata" />
      <button className="play" type="button" aria-label={sonando ? 'Parar' : 'Escuchar'} onClick={alternar}>
        <Icono nombre={sonando ? 'pausa' : 'play'} />
      </button>
      <div className="barra">
        <i style={{ width: `${avance}%` }} />
      </div>
      <span className="dur">{mmss(sonando || duracion === 0 ? actual : duracion)}</span>
    </div>
  );
}
