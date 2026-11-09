// La administración de la app (solo ADMIN): usuarios, dispositivos y el
// código de alta con el que una tablet entra por «Soy del taller». Los tipos
// son los del AdminController; las fechas llegan como LocalDateTime sin zona
// y se leen en la hora local, que es la del servidor. Las dos funciones puras
// de abajo deciden cómo se dice el estado de un dispositivo y cuánto le queda
// a un código.
import { api } from './api';
import type { Sesion } from './sesion';

export type Rol = Sesion['rol'];

export const ROLES: Rol[] = ['TALLER', 'DESPACHO', 'ADMIN'];

export type Usuario = { id: number; nombre: string; login: string; rol: Rol; activo: boolean; ultimoAcceso: string | null };

export type Dispositivo = {
  id: number;
  usuarioId: number;
  usuario: string | null;
  nombre: string | null;
  alta: string;
  ultimoUso: string | null;
  revocado: boolean;
};

export type CodigoAlta = { codigo: string; caduca: string };

export type NuevoUsuario = { nombre: string; login: string; password: string; rol: Rol };

/** Lo que se cambia de un usuario: lo que no va, el servidor no lo toca. */
export type CambiosUsuario = Partial<{ nombre: string; rol: Rol; activo: boolean; password: string }>;

export const usuarios = () => api<Usuario[]>('/api/usuarios');

export const crearUsuario = (nuevo: NuevoUsuario) =>
  api<Usuario>('/api/usuarios', { method: 'POST', body: JSON.stringify(nuevo) });

export const cambiarUsuario = (id: number, cambios: CambiosUsuario) =>
  api<Usuario>(`/api/usuarios/${id}`, { method: 'PATCH', body: JSON.stringify(cambios) });

export const generarCodigoAlta = (id: number) => api<CodigoAlta>(`/api/usuarios/${id}/codigo-alta`, { method: 'POST' });

export const dispositivos = () => api<Dispositivo[]>('/api/dispositivos');

export const revocarDispositivo = (id: number) => api<void>(`/api/dispositivos/${id}/revocar`, { method: 'POST' });

/** «revocado» manda; si no, un dispositivo que nunca se ha usado está «sin usar», y si no, «activo». */
export function estadoDispositivo(d: Dispositivo): 'activo' | 'revocado' | 'sin usar' {
  if (d.revocado) return 'revocado';
  if (!d.ultimoUso) return 'sin usar';
  return 'activo';
}

/**
 * Cuánto le queda a un código de alta: «caduca en 9 min», redondeando hacia
 * arriba (recién generado dice 10, como el texto que lo acompaña); el último
 * minuto, en segundos; con la hora ya pasada, «caducado».
 */
export function caducaEn(caducaIso: string, ahora: Date = new Date()): string {
  const segundos = Math.ceil((new Date(caducaIso).getTime() - ahora.getTime()) / 1000);
  if (!(segundos > 0)) return 'caducado';
  if (segundos < 60) return `caduca en ${segundos} s`;
  return `caduca en ${Math.ceil(segundos / 60)} min`;
}
