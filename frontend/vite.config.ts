import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { defineConfig } from 'vitest/config';

// La PWA de modelos. Se sirve desde Spring Boot en /app (SpaController), así
// que todo cuelga de esa base; Maven copia dist/ a static/app. En desarrollo
// (npm run dev) la API y las páginas /m van al Spring Boot de siempre.
export default defineConfig({
  base: '/app/',
  build: {
    outDir: 'dist',
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      // Con la barra: '/m' a secas se llevaría cualquier ruta que empiece por m.
      '/m/': 'http://localhost:8080',
    },
  },
  plugins: [
    react(),
    VitePWA({
      // Service worker propio (src/sw.ts): las notificaciones push llegan en
      // una entrega posterior y con generateSW no habría dónde ponerlas.
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      registerType: 'autoUpdate',
      injectRegister: 'auto',
      injectManifest: {
        // También la letra: sin internet la app tiene que pintarse igual. Los
        // iconos y el manifiesto los añade el plugin por su cuenta.
        globPatterns: ['**/*.{js,css,html,woff2}'],
      },
      manifest: {
        name: 'Modelos Puntotres',
        short_name: 'Modelos',
        start_url: '/app/',
        scope: '/app/',
        display: 'standalone',
        background_color: '#f1f3f6',
        theme_color: '#16213a',
        lang: 'es',
        icons: [
          { src: 'icono-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icono-512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
      devOptions: {
        enabled: false,
      },
    }),
  ],
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
