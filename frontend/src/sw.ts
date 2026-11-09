/// <reference lib="webworker" />
// Service worker de la PWA de modelos (vite-plugin-pwa con injectManifest:
// el plugin mete en self.__WB_MANIFEST la lista de ficheros del build).
//
// - Precarga todo el build (también la letra y los iconos), para que la app
//   se abra sin internet en la tablet del taller.
// - Una ruta de dentro de la app (/app/ordenes/12) recargada sin internet se
//   sirve con el index.html precargado: las rutas las resuelve React.
// - Al publicar una versión nueva, la vieja se borra y la nueva toma el
//   control enseguida (skipWaiting + clientsClaim): registerType 'autoUpdate'.
// - Enseña los avisos al móvil (Web Push) que manda el servidor (Push.java):
//   una orden nueva al taller, un aviso o una respuesta al despacho. Tocar el
//   aviso abre la app en su pantalla.
import { clientsClaim } from 'workbox-core';
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching';
import { NavigationRoute, registerRoute } from 'workbox-routing';

declare let self: ServiceWorkerGlobalScope;

precacheAndRoute(self.__WB_MANIFEST);
cleanupOutdatedCaches();
registerRoute(new NavigationRoute(createHandlerBoundToURL('/app/index.html'), { allowlist: [/^\/app\//] }));

self.skipWaiting();
clientsClaim();

/** Lo que manda el servidor en cada push (Push.Aviso). */
type AvisoPush = { titulo?: string; cuerpo?: string; url?: string; modificacionId?: number; tag?: string };

// vibrate y renotify existen en los navegadores pero no en los tipos de TypeScript.
type OpcionesAviso = NotificationOptions & { vibrate?: number[]; renotify?: boolean };

self.addEventListener('push', (evento) => {
  let aviso: AvisoPush = {};
  try {
    aviso = (evento.data?.json() as AvisoPush | undefined) ?? {};
  } catch {
    // Un push sin JSON: se enseña igual, con el título de la app.
  }
  const opciones: OpcionesAviso = {
    body: aviso.cuerpo,
    icon: '/app/icono-192.png',
    badge: '/app/icono-192.png',
    vibrate: [200, 100, 200],
    tag: aviso.tag,
    // Con la misma etiqueta (la misma modificación), el nuevo sustituye al viejo y vuelve a sonar.
    renotify: Boolean(aviso.tag),
    data: { url: aviso.url },
  };
  evento.waitUntil(self.registration.showNotification(aviso.titulo ?? 'Modelos', opciones));
});

self.addEventListener('notificationclick', (evento) => {
  evento.notification.close();
  const datos = evento.notification.data as { url?: string } | undefined;
  const url = new URL(datos?.url ?? '/app/', self.location.origin).href;
  evento.waitUntil(
    (async () => {
      const ventanas = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
      const yaAbierta = ventanas.find((v) => v.url === url);
      if (yaAbierta) {
        await yaAbierta.focus();
        return;
      }
      // La app ya abierta en otra pantalla: se lleva allí en vez de abrir otra ventana.
      const deLaApp = ventanas.find((v) => new URL(v.url).pathname.startsWith('/app/'));
      if (deLaApp) {
        const enfocada = await deLaApp.focus();
        await enfocada.navigate(url);
        return;
      }
      await self.clients.openWindow(url);
    })(),
  );
});
