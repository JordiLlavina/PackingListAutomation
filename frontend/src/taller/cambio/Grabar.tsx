// Paso 4: la nota de voz. El botón rojo de 160 px graba y para; el contador
// y la onda dicen que está escuchando. Se para sola a los 3 minutos. Sin
// permiso de micrófono no se bloquea nada: se puede mandar solo con fotos.
import { useEffect, useRef, useState } from 'react';
import { Navigate, useNavigate } from 'react-router';
import Audio from '../../comun/Audio';
import { Grabador, type Grabacion } from '../../comun/grabador';
import { Icono } from '../../comun/iconos';
import { mmss } from '../../comun/tiempo';
import { useBorrador } from './Borrador';
import Pasos from './Pasos';

export default function Grabar() {
  const navegar = useNavigate();
  const { modelo, fotos, audio, ponerAudio } = useBorrador();
  const grabador = useRef<Grabador | null>(null);
  const [grabando, setGrabando] = useState(false);
  const [seg, setSeg] = useState(0);
  const [sinMicro, setSinMicro] = useState(false);
  // Mientras el navegador pregunta por el permiso: un segundo toque abriría otro micrófono.
  const [iniciando, setIniciando] = useState(false);
  const montado = useRef(true);

  // Salir de la pantalla grabando tira lo que se estaba grabando y suelta el micrófono.
  useEffect(() => {
    montado.current = true;
    return () => {
      montado.current = false;
      grabador.current?.cancelar();
    };
  }, []);

  if (!modelo) return <Navigate to="/cambio" replace />;

  const listo = !grabando && audio !== null;

  const guardar = (g: Grabacion) => {
    grabador.current = null;
    setGrabando(false);
    ponerAudio({ blob: g.blob, duracionSeg: g.duracionSeg });
  };

  const alternar = async () => {
    if (grabando) {
      const g = await grabador.current?.parar();
      if (g) guardar(g);
      return;
    }
    const nuevo = new Grabador();
    nuevo.onTick = setSeg;
    nuevo.onTope = guardar;
    setIniciando(true);
    try {
      await nuevo.empezar();
    } catch {
      if (montado.current) {
        setSinMicro(true);
        setIniciando(false);
      }
      return;
    }
    // Se ha salido de la pantalla mientras se daba el permiso.
    if (!montado.current) {
      nuevo.cancelar();
      return;
    }
    setIniciando(false);
    grabador.current = nuevo;
    setSinMicro(false);
    setSeg(0);
    setGrabando(true);
  };

  const repetir = () => {
    ponerAudio(null);
    setSeg(0);
  };

  const pregunta = grabando ? 'Te escucho… toca para parar' : listo ? 'Grabado. ¿Lo mandamos?' : 'Toca y explica qué has cambiado';
  const segundos = grabando ? seg : (audio?.duracionSeg ?? 0);
  const clases = ['mic', grabando ? 'grabando' : '', listo ? 'listo' : ''].filter(Boolean).join(' ');

  return (
    <>
      <Pasos />
      <h2 className="pregunta">{pregunta}</h2>
      <div className="grabador">
        <button className={clases} type="button" aria-label={grabando ? 'Parar' : 'Grabar'} disabled={iniciando} onClick={() => void alternar()}>
          <Icono nombre={grabando ? 'pausa' : listo ? 'check' : 'mic'} />
        </button>
        <div className="tiempo" aria-live="off">
          {mmss(segundos)}
        </div>
        <div className={grabando ? 'onda viva' : 'onda'} aria-hidden="true">
          {Array.from({ length: 18 }, (_, i) => (
            <i key={i} />
          ))}
        </div>
      </div>
      {listo && audio && <Audio key={audio.url} src={audio.url} duracionSeg={audio.duracionSeg} />}
      <div className="acciones">
        <button className="boton" type="button" disabled={!listo} onClick={repetir}>
          Repetir
        </button>
        <button className="boton ok" type="button" disabled={grabando || (!listo && fotos.length === 0)} onClick={() => navegar('/cambio/revisar')}>
          <Icono nombre="check" /> Siguiente
        </button>
      </div>
      {sinMicro && <p className="ayuda">No puedo usar el micrófono. Puedes mandar solo fotos</p>}
      <p className="ayuda">
        {fotos.length > 0
          ? `Llevas ${fotos.length} ${fotos.length === 1 ? 'foto' : 'fotos'}. Si no quieres grabar, pulsa Siguiente.`
          : 'Hace falta una nota de voz o una foto.'}
      </p>
    </>
  );
}
