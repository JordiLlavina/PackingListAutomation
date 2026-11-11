// Quién ha entrado en la app. Al arrancar se pregunta al servidor
// (GET /api/sesion): un 401 es «nadie» y la app enseña la entrada. El taller
// entra una vez con el código de alta y se queda dentro con la cookie del
// dispositivo; despacho entra con usuario y contraseña.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, SESION_CADUCADA } from './api';
import { desactivarPush } from './push';
import { toast } from './toast';

export type Sesion = { id: number; nombre: string; rol: 'TALLER' | 'DESPACHO' | 'ADMIN'; dispositivo: boolean };

type Entrado = Omit<Sesion, 'dispositivo'>;

type ContextoSesion = {
  sesion: Sesion | null;
  cargando: boolean;
  entrar(login: string, password: string): Promise<void>;
  entrarConCodigo(codigo: string, nombre: string): Promise<void>;
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
    const entrado = await api<Entrado>('/api/sesion', {
      method: 'POST',
      body: JSON.stringify({ login, password }),
    });
    setSesion({ ...entrado, dispositivo: false });
  }, []);

  const entrarConCodigo = useCallback(async (codigo: string, nombre: string) => {
    const entrado = await api<Entrado>('/api/sesion/dispositivo', {
      method: 'POST',
      body: JSON.stringify({ codigo, nombre }),
    });
    setSesion({ ...entrado, dispositivo: true });
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
    () => ({ sesion, cargando, entrar, entrarConCodigo, salir }),
    [sesion, cargando, entrar, entrarConCodigo, salir],
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
