// Los números de la bandeja (GET /api/bandeja/resumen): una sola consulta
// compartida por la pepita de la barra, la losa de Inicio y las listas (en el
// taller) o la pepita de «Bandeja» y sus contadores (en despacho), que
// se vuelven a pedir cuando cambian. Se pregunta cada 20 s mientras la app
// está a la vista, y en cuanto vuelve a estarlo (la tablet se desbloquea, se
// vuelve de otra aplicación); escondida no pregunta. El aviso de verdad de
// una orden nueva, con la app cerrada, es la notificación push (Task 17).
import { createContext, createElement, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api } from './api';
import type { ResumenBandeja } from './modificaciones';

const CADA_MS = 20_000;

type ContextoBandeja = {
  /** Null hasta la primera respuesta. */
  resumen: ResumenBandeja | null;
  /** Vuelve a preguntar ya (tras abrir una orden, marcarla hecha, contestar…). */
  recargar: () => void;
};

const Contexto = createContext<ContextoBandeja>({ resumen: null, recargar: () => {} });

export function ProveedorBandeja({ children }: { children: ReactNode }) {
  const [resumen, setResumen] = useState<ResumenBandeja | null>(null);
  // Solo vale la respuesta de la última pregunta: una lenta no pisa a una más nueva.
  const peticion = useRef(0);

  const recargar = useCallback(() => {
    const esta = ++peticion.current;
    api<ResumenBandeja>('/api/bandeja/resumen')
      .then((r) => {
        if (esta !== peticion.current) return;
        // El mismo resumen no cambia el estado: las listas no se vuelven a pedir por nada.
        setResumen((antes) => (antes && mismoResumen(antes, r) ? antes : r));
      })
      .catch(() => {
        // Sin red se queda lo último que se sabía; la próxima vuelta lo reintenta.
      });
  }, []);

  useEffect(() => {
    let reloj: ReturnType<typeof setInterval> | null = null;
    const parar = () => {
      if (reloj) clearInterval(reloj);
      reloj = null;
    };
    const seguir = () => {
      parar();
      if (document.visibilityState !== 'visible') return;
      recargar();
      reloj = setInterval(recargar, CADA_MS);
    };
    const alCambiarVisibilidad = () => (document.visibilityState === 'visible' ? seguir() : parar());
    const alEnfocar = () => {
      if (document.visibilityState === 'visible') recargar();
    };
    seguir();
    document.addEventListener('visibilitychange', alCambiarVisibilidad);
    window.addEventListener('focus', alEnfocar);
    return () => {
      parar();
      document.removeEventListener('visibilitychange', alCambiarVisibilidad);
      window.removeEventListener('focus', alEnfocar);
    };
  }, [recargar]);

  const valor = useMemo(() => ({ resumen, recargar }), [resumen, recargar]);
  return createElement(Contexto.Provider, { value: valor }, children);
}

function mismoResumen(a: ResumenBandeja, b: ResumenBandeja): boolean {
  return (
    a.nuevas === b.nuevas &&
    a.pendientes === b.pendientes &&
    a.urgentes === b.urgentes &&
    a.conRespuesta === b.conRespuesta
  );
}

export function useBandeja(): ContextoBandeja {
  return useContext(Contexto);
}
