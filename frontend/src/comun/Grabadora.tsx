// La grabadora de la maqueta, la misma en «Crear Modificación» y en «Tengo una
// duda»: el botón redondo de 160 px que graba y para, el contador y la onda
// que dicen que está escuchando. useGrabadora lleva el micrófono (pedir
// permiso, parar al tope o si se va solo, soltarlo al salir de la pantalla) y
// Grabadora lo pinta; qué se hace con la nota grabada lo decide la pantalla.
import { useEffect, useRef, useState } from 'react';
import { Grabador, type Grabacion } from './grabador';
import { Icono } from './iconos';
import { mmss } from './tiempo';

export type EstadoGrabadora = {
  grabando: boolean;
  /** Segundos que lleva la grabación en curso. */
  seg: number;
  /** El navegador no ha dejado usar el micrófono (sin permiso o sin micrófono). */
  sinMicro: boolean;
  /** Mientras el navegador pregunta por el permiso: un segundo toque abriría otro micrófono. */
  iniciando: boolean;
  /** Empieza a grabar o, si está grabando, para. */
  alternar: () => Promise<void>;
};

/** El micrófono de una pantalla. `alGrabar` recibe cada nota acabada: parada a mano, al tope o porque el micrófono se ha ido. */
export function useGrabadora(alGrabar: (g: Grabacion) => void): EstadoGrabadora {
  const grabador = useRef<Grabador | null>(null);
  const [grabando, setGrabando] = useState(false);
  const [seg, setSeg] = useState(0);
  const [sinMicro, setSinMicro] = useState(false);
  const [iniciando, setIniciando] = useState(false);
  const montado = useRef(true);
  // La última versión del callback, sin volver a crear la grabadora a cada pintado.
  const alGrabarActual = useRef(alGrabar);
  useEffect(() => {
    alGrabarActual.current = alGrabar;
  }, [alGrabar]);

  // Salir de la pantalla grabando tira lo que se estaba grabando y suelta el micrófono.
  useEffect(() => {
    montado.current = true;
    return () => {
      montado.current = false;
      grabador.current?.cancelar();
    };
  }, []);

  const guardar = (g: Grabacion) => {
    grabador.current = null;
    setGrabando(false);
    alGrabarActual.current(g);
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

  return { grabando, seg, sinMicro, iniciando, alternar };
}

type Props = {
  estado: EstadoGrabadora;
  /** Duración de la nota ya grabada; null si todavía no hay ninguna. */
  grabadaSeg: number | null;
};

/** El botón, el contador y la onda. Con una nota ya grabada, tocar el botón graba otra (como en la maqueta). */
export default function Grabadora({ estado, grabadaSeg }: Props) {
  const { grabando, seg, iniciando, alternar } = estado;
  const listo = !grabando && grabadaSeg !== null;
  const segundos = grabando ? seg : (grabadaSeg ?? 0);
  const clases = ['mic', grabando ? 'grabando' : '', listo ? 'listo' : ''].filter(Boolean).join(' ');
  return (
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
  );
}
