// El recorrido de humo de la PWA de modelos (e2e/), con Chromium sin ventana
// contra el jar arrancado de verdad. Lo lanza Maven con `mvn verify -Pe2e`,
// que arranca la aplicación en el puerto 8089 con la base en memoria y pasa
// la dirección en E2E_BASE_URL; a mano, `npm run e2e` contra una aplicación
// ya arrancada en ese puerto.
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: 'e2e',
  outputDir: 'test-results',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  retries: 0,
  workers: 1,
  // La lista para leerla en la consola; el JSON, para que Maven decida en
  // verify si el recorrido ha ido bien (e2e/comprobar-resultado.mjs).
  reporter: [['list'], ['json', { outputFile: 'playwright-report/resultado.json' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8089',
    headless: true,
    trace: 'retain-on-failure',
    // Sin service worker: la caché de la PWA no tiene nada que ver con lo que
    // se prueba y solo podría servir una versión vieja de la app.
    serviceWorkers: 'block',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
