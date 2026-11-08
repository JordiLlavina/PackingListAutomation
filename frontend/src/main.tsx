// Arranque de la PWA de modelos. La letra va dentro del bundle (solo los dos
// pesos que se usan, 400 y 700): la tablet puede no tener internet.
import '@fontsource/atkinson-hyperlegible/400.css';
import '@fontsource/atkinson-hyperlegible/700.css';
import './comun/estilos.css';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { ProveedorSesion } from './comun/sesion';

const raiz = document.getElementById('raiz');
if (!raiz) throw new Error('Falta <div id="raiz"> en index.html');

createRoot(raiz).render(
  <StrictMode>
    <ProveedorSesion>
      <App />
    </ProveedorSesion>
  </StrictMode>,
);
