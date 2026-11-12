// Quién ha entrado en la app. Al arrancar se pregunta al servidor
// (GET /api/sesion): un 401 es «nadie» y la app enseña la entrada. Todos
// entran con usuario y contraseña; el del taller se queda dentro de ese
// aparato con la cookie del dispositivo (un año), el despacho con una sesión
// de 8 horas.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, SESION_CADUCADA } from './api';
import { desactivarPush } from './push';
import { toast } from './toast';

export type Sesion = { id: number; nombre: string; rol: 'TALLER' | 'DESPACHO' | 'ADMIN'; dispositivo: boolean };

type ContextoSesion = {
  sesion: Sesion | null;
  cargando: boolean;
  entrar(login: string, password: string): Promise<void>;
  salir(): Promise<void>;
};

const Contexto = createContext<ContextoSesion | null>(null);

export function ProveedorSesion({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion | null>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    api<Sesion>('/api/sesion')
      .then(setSesion)
      .catch(() => setSesion(null))
      .finally(() => setCargando(false));
  }, []);

  // Las pestañas de un mismo navegador comparten las cookies (también todas las
  // ventanas de incógnito entre sí): si en otra se entra con otro usuario, el
  // servidor atiende a esta como a ese otro, mientras ella sigue pintando la
  // cara del primero. Pasó probando taller y despacho a la vez: la tablet decía
  // «No tienes nada pendiente» (le llegaban los números del despacho) y su
  // «Hecho» quedaba a nombre del admin. Al volver a la pestaña se pregunta
  // quién es y, si ha cambiado, se pinta la cara de quien es de verdad.
  const actual = useRef<Sesion | null>(null);
  useEffect(() => {
    actual.current = sesion;
  }, [sesion]);
  useEffect(() => {
    const comprobar = () => {
      if (document.visibilityState !== 'visible' || !actual.current) return;
      api<Sesion>('/api/sesion')
        .then((ahora) => {
          const antes = actual.current;
          if (antes && cambioDeUsuario(antes, ahora)) {
            setSesion(ahora);
            toast(`Se ha entrado como ${ahora.nombre} en otra pestaña de este navegador`);
          }
        })
        .catch(() => {
          // Un 401 ya devuelve a la entrada (SESION_CADUCADA); sin red, se queda como estaba.
        });
    };
    window.addEventListener('focus', comprobar);
    document.addEventListener('visibilitychange', comprobar);
    return () => {
      window.removeEventListener('focus', comprobar);
      document.removeEventListener('visibilitychange', comprobar);
    };
  }, []);

  // Cualquier 401 de la API (sesión caducada, dispositivo revocado) devuelve a la entrada.
  useEffect(() => {
    const caducada = () => setSesion(null);
    window.addEventListener(SESION_CADUCADA, caducada);
    return () => window.removeEventListener(SESION_CADUCADA, caducada);
  }, []);

  const entrar = useCallback(async (login: string, password: string) => {
    // El nombre solo se usa si es del taller: así se reconoce el aparato en la lista de dispositivos.
    const entrado = await api<Sesion>('/api/sesion', {
      method: 'POST',
      body: JSON.stringify({ login, password, nombre: nombreDelAparato(navigator.userAgent) }),
    });
    setSesion(entrado);
  }, []);

  const salir = useCallback(async () => {
    // Antes de cerrar la sesión (la baja la necesita): en un PC compartido, los avisos no siguen llegando a quien sale.
    await desactivarPush();
    try {
      await api<void>('/api/sesion', { method: 'DELETE' });
    } finally {
      setSesion(null);
    }
  }, []);

  const valor = useMemo(
    () => ({ sesion, cargando, entrar, salir }),
    [sesion, cargando, entrar, salir],
  );
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useSesion(): ContextoSesion {
  const contexto = useContext(Contexto);
  if (!contexto) throw new Error('useSesion fuera de <ProveedorSesion>');
  return contexto;
}

/** Si quien atiende el servidor ya no es quien pinta la app: otro usuario, o el mismo con otro rol (cambia de cara). */
export function cambioDeUsuario(antes: Pick<Sesion, 'id' | 'rol'>, ahora: Pick<Sesion, 'id' | 'rol'>): boolean {
  return antes.id !== ahora.id || antes.rol !== ahora.rol;
}

/**
 * Cómo llamar al aparato en la lista de dispositivos, sacado del navegador:
 * «Móvil Android · Chrome», «Tablet Android · Chrome», «iPhone · Safari»,
 * «Windows · Edge». Si no se reconoce, el principio del texto del navegador.
 */
export function nombreDelAparato(agente: string): string {
  const sistema = /iPhone/.test(agente)
    ? 'iPhone'
    : /iPad/.test(agente)
      ? 'iPad'
      : /Android/.test(agente)
        ? /Mobile/.test(agente)
          ? 'Móvil Android'
          : 'Tablet Android'
        : /Windows/.test(agente)
          ? 'Windows'
          : /Mac OS X/.test(agente)
            ? 'Mac'
            : /Linux/.test(agente)
              ? 'Linux'
              : null;
  const navegador = /Edg\//.test(agente)
    ? 'Edge'
    : /SamsungBrowser\//.test(agente)
      ? 'Samsung Internet'
      : /Firefox\/|FxiOS\//.test(agente)
        ? 'Firefox'
        : /Chrome\/|CriOS\//.test(agente)
          ? 'Chrome'
          : /Safari\//.test(agente)
            ? 'Safari'
            : null;
  if (!sistema && !navegador) return agente.slice(0, 80) || 'Sin nombre';
  return [sistema, navegador].filter(Boolean).join(' · ');
}
