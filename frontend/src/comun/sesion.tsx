// Quién ha entrado en la app. Al arrancar se pregunta al servidor
// (GET /api/sesion): un 401 es «nadie» y la app enseña la entrada. El taller
// entra una vez con el código de alta y se queda dentro con la cookie del
// dispositivo; despacho entra con usuario y contraseña.
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, SESION_CADUCADA } from './api';

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
