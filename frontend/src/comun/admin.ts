// La administración de la app (solo ADMIN): usuarios y los móviles y tablets
// del taller, que se dan de alta solos al entrar con su contraseña. Los tipos
// son los del AdminController; las fechas llegan como LocalDateTime sin zona
// y se leen en la hora local, que es la del servidor. La función pura de
// abajo decide cómo se dice el estado de un dispositivo.
import { api } from './api';
import type { Sesion } from './sesion';

export type Rol = Sesion['rol'];

export const ROLES: Rol[] = ['TALLER', 'DESPACHO', 'ADMIN'];

/** `conPassword` falso: un usuario del taller de cuando se entraba con código; no puede entrar hasta que se le ponga contraseña. */
export type Usuario = {
  id: number;
  nombre: string;
  login: string;
  rol: Rol;
  activo: boolean;
  ultimoAcceso: string | null;
  conPassword: boolean;
};

export type Dispositivo = {
  id: number;
  usuarioId: number;
  usuario: string | null;
  nombre: string | null;
  alta: string;
  ultimoUso: string | null;
  revocado: boolean;
};

export type NuevoUsuario = { nombre: string; login: string; password: string; rol: Rol };

/** Lo que se cambia de un usuario: lo que no va, el servidor no lo toca. */
export type CambiosUsuario = Partial<{ nombre: string; rol: Rol; activo: boolean; password: string }>;

export const usuarios = () => api<Usuario[]>('/api/usuarios');

export const crearUsuario = (nuevo: NuevoUsuario) =>
  api<Usuario>('/api/usuarios', { method: 'POST', body: JSON.stringify(nuevo) });

export const cambiarUsuario = (id: number, cambios: CambiosUsuario) =>
  api<Usuario>(`/api/usuarios/${id}`, { method: 'PATCH', body: JSON.stringify(cambios) });

export const dispositivos = () => api<Dispositivo[]>('/api/dispositivos');

export const revocarDispositivo = (id: number) => api<void>(`/api/dispositivos/${id}/revocar`, { method: 'POST' });

/** «revocado» manda; si no, un dispositivo que nunca se ha usado está «sin usar», y si no, «activo». */
export function estadoDispositivo(d: Dispositivo): 'activo' | 'revocado' | 'sin usar' {
  if (d.revocado) return 'revocado';
  if (!d.ultimoUso) return 'sin usar';
  return 'activo';
}
