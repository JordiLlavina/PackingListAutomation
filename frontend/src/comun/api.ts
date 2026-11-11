// Llamadas a la API de modelos. Lo que hace cada una por su cuenta, para que
// ninguna pantalla tenga que acordarse:
// - manda la cookie de sesión (y la del dispositivo) del mismo origen;
// - en lo que no es GET, devuelve el token CSRF de la cookie XSRF-TOKEN en la
//   cabecera X-XSRF-TOKEN, que es lo que pide Spring Security;
// - un error del servidor llega como ErrorApi con su "mensaje", que está
//   escrito para enseñarlo tal cual;
// - un 401 avisa con el evento "sesion-caducada" para que la app vuelva a la
//   entrada (la sesión de despacho caduca a las 8 horas).

export class ErrorApi extends Error {
  estado: number;

  constructor(estado: number, mensaje: string) {
    super(mensaje);
    this.name = 'ErrorApi';
    this.estado = estado;
  }
}

export const SESION_CADUCADA = 'sesion-caducada';

function cookie(nombre: string): string | null {
  for (const trozo of document.cookie.split(';')) {
    const [clave, ...valor] = trozo.trim().split('=');
    if (clave === nombre) return decodeURIComponent(valor.join('='));
  }
  return null;
}

async function mensajeDe(respuesta: Response): Promise<string> {
  try {
    const cuerpo: unknown = await respuesta.json();
    if (cuerpo && typeof cuerpo === 'object' && 'mensaje' in cuerpo && typeof cuerpo.mensaje === 'string') {
      return cuerpo.mensaje;
    }
  } catch {
    // Sin JSON (un proxy, un 502): se dice lo que se sabe.
  }
  return `Error ${respuesta.status} del servidor`;
}

export async function api<T>(ruta: string, init: RequestInit = {}): Promise<T> {
  const cabeceras = new Headers(init.headers);
  const metodo = (init.method ?? 'GET').toUpperCase();
  if (metodo !== 'GET' && metodo !== 'HEAD') {
    const token = cookie('XSRF-TOKEN');
    if (token) cabeceras.set('X-XSRF-TOKEN', token);
  }
  if (typeof init.body === 'string' && !cabeceras.has('Content-Type')) {
    cabeceras.set('Content-Type', 'application/json');
  }
  cabeceras.set('Accept', 'application/json');

  let respuesta: Response;
  try {
    respuesta = await fetch(ruta, { ...init, headers: cabeceras, credentials: 'same-origin' });
  } catch {
    throw new ErrorApi(0, 'No hay conexión con el servidor');
  }

  if (!respuesta.ok) {
    const mensaje = await mensajeDe(respuesta);
    if (respuesta.status === 401) window.dispatchEvent(new Event(SESION_CADUCADA));
    throw new ErrorApi(respuesta.status, mensaje);
  }
  if (respuesta.status === 204) return undefined as T;
  const tipo = respuesta.headers.get('Content-Type') ?? '';
  return (tipo.includes('json') ? await respuesta.json() : await respuesta.text()) as T;
}
