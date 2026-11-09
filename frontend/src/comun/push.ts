// Los avisos al móvil (Web Push). El navegador se suscribe con la clave
// pública VAPID del servidor (GET /api/push/clave-publica) y le manda la
// suscripción (POST /api/push/suscripcion); a partir de ahí el servidor avisa
// aunque la app esté cerrada, y el service worker (sw.ts) enseña la
// notificación.
//
// Sin service worker ni PushManager (un iPhone con la web sin instalar, un
// navegador viejo) es 'no-soportado' y no se ofrece nada. Con el permiso ya
// dado es 'activo': la tarjeta no sale y la suscripción se vuelve a mandar en
// silencio al entrar (el servidor borra las que el navegador ha tirado, y en
// un PC compartido pasa a ser de quien está dentro).
import { api } from './api';

export type EstadoPush = 'no-soportado' | 'denegado' | 'pendiente' | 'activo';

export type ClavePublica = { clave: string; configurado: boolean };

export function estadoPush(): EstadoPush {
  if (typeof window === 'undefined' || !('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
    return 'no-soportado';
  }
  if (Notification.permission === 'denied') return 'denegado';
  if (Notification.permission === 'granted') return 'activo';
  return 'pendiente';
}

/**
 * La clave VAPID en Base64 URL (sin relleno, con - y _) pasada a bytes, que es
 * como la pide pushManager.subscribe.
 */
export function urlBase64ToUint8Array(base64: string): Uint8Array<ArrayBuffer> {
  const relleno = '='.repeat((4 - (base64.length % 4)) % 4);
  const normal = (base64 + relleno).replace(/-/g, '+').replace(/_/g, '/');
  const crudo = atob(normal);
  return Uint8Array.from(crudo, (letra) => letra.charCodeAt(0));
}

/** El service worker de la app, si hay uno; sin él (npm run dev) no se espera a nada. */
async function registro(): Promise<ServiceWorkerRegistration | null> {
  const hay = await navigator.serviceWorker.getRegistration();
  return hay ? navigator.serviceWorker.ready : null;
}

/**
 * Pide permiso (si aún no se ha dado), suscribe el navegador y se lo dice al
 * servidor. True si queda activo; false si no hay push en el servidor, el
 * permiso se niega o algo falla por el camino.
 */
export async function activarPush(): Promise<boolean> {
  if (estadoPush() === 'no-soportado') return false;
  try {
    const { clave, configurado } = await api<ClavePublica>('/api/push/clave-publica');
    if (!configurado || !clave) return false;
    const permiso = Notification.permission === 'granted' ? 'granted' : await Notification.requestPermission();
    if (permiso !== 'granted') return false;
    const sw = await registro();
    if (!sw) return false;
    const suscripcion =
      (await sw.pushManager.getSubscription()) ??
      (await sw.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToUint8Array(clave) }));
    const { endpoint, keys } = suscripcion.toJSON();
    if (!endpoint || !keys?.p256dh || !keys?.auth) return false;
    await api<void>('/api/push/suscripcion', {
      method: 'POST',
      body: JSON.stringify({ endpoint, p256dh: keys.p256dh, auth: keys.auth }),
    });
    return true;
  } catch {
    return false;
  }
}

/** Da de baja la suscripción de este navegador, en el servidor y en el navegador. Nunca falla. */
export async function desactivarPush(): Promise<void> {
  if (estadoPush() === 'no-soportado') return;
  try {
    const sw = await navigator.serviceWorker.getRegistration();
    const suscripcion = await sw?.pushManager.getSubscription();
    if (!suscripcion) return;
    await api<void>('/api/push/suscripcion', { method: 'DELETE', body: JSON.stringify({ endpoint: suscripcion.endpoint }) }).catch(() => {});
    await suscripcion.unsubscribe();
  } catch {
    // Sin baja: el servidor la borrará cuando el navegador diga que ya no existe.
  }
}
