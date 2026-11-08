/// <reference lib="webworker" />
// Service worker de la PWA de modelos (vite-plugin-pwa con injectManifest:
// el plugin mete en self.__WB_MANIFEST la lista de ficheros del build).
//
// - Precarga todo el build (también la letra y los iconos), para que la app
//   se abra sin internet en la tablet del taller.
// - Al publicar una versión nueva, la vieja se borra y la nueva toma el
//   control enseguida (skipWaiting + clientsClaim): registerType 'autoUpdate'.
import { clientsClaim } from 'workbox-core';
import { cleanupOutdatedCaches, precacheAndRoute } from 'workbox-precaching';

declare let self: ServiceWorkerGlobalScope;

precacheAndRoute(self.__WB_MANIFEST);
cleanupOutdatedCaches();

self.skipWaiting();
clientsClaim();

// Notificaciones push: llegan con las órdenes de despacho (Task 17), que
// escucharán aquí el evento 'push' y enseñarán la notificación.
