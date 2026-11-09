// La app según quién haya entrado: nadie → la entrada; el taller → su marco
// de tablet; despacho y admin → el suyo de PC. Cada cara tiene su router
// (basename /app, donde la sirve Spring Boot), y una ruta que no existe
// vuelve al inicio.
import { useMemo } from 'react';
import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { useSesion, type Sesion } from './comun/sesion';
import ShellDespacho from './despacho/Shell';
import InicioDespacho from './despacho/Inicio';
import ModelosDespacho from './despacho/Modelos';
import Entrada from './entrada/Entrada';
import ShellTaller, { type Pantalla } from './taller/Shell';
import { BorradorProvider } from './taller/cambio/Borrador';
import ElegirModelo from './taller/cambio/ElegirModelo';
import Enviado from './taller/cambio/Enviado';
import Fotos from './taller/cambio/Fotos';
import Grabar from './taller/cambio/Grabar';
import QueHasCambiado from './taller/cambio/QueHasCambiado';
import Revisar from './taller/cambio/Revisar';
import FichaModeloTaller from './taller/FichaModelo';
import InicioTaller from './taller/Inicio';
import ModelosTaller from './taller/Modelos';

/** Hueco de una pantalla que todavía no está hecha: la ruta ya existe para que los enlaces no reboten al inicio. */
function PorLlegar() {
  return (
    <div className="vacio">
      <b>Esta pantalla llega en una próxima entrega</b>
    </div>
  );
}

function pantalla(datos: Pantalla): { handle: Pantalla } {
  return { handle: datos };
}

/**
 * «He cambiado algo», paso a paso: cada paso es su ruta (atrás, recargar y el
 * título de la cabecera funcionan solos) y el borrador va en un contexto por
 * encima de todas, así que salir por la barra de abajo no lo pierde. Sin
 * escáner flotante: taparía los botones de abajo y aquí no hace falta.
 */
const rutasCambio: RouteObject[] = [
  { index: true, element: <ElegirModelo />, ...pantalla({ titulo: '¿Qué modelo?', atras: '/', seccion: 'inicio' }) },
  // El título lo pone el paso con la referencia y el nombre del modelo.
  { path: 'tipo', element: <QueHasCambiado />, ...pantalla({ titulo: 'He cambiado algo', atras: '/cambio', seccion: 'inicio' }) },
  { path: 'fotos', element: <Fotos />, ...pantalla({ titulo: 'Fotos del cambio', atras: '/cambio/tipo', seccion: 'inicio' }) },
  { path: 'voz', element: <Grabar />, ...pantalla({ titulo: 'Nota de voz', atras: '/cambio/fotos', seccion: 'inicio' }) },
  { path: 'revisar', element: <Revisar />, ...pantalla({ titulo: 'Revisar y enviar', atras: '/cambio/voz', seccion: 'inicio' }) },
  { path: 'enviado', element: <Enviado />, ...pantalla({ titulo: 'Enviado', seccion: 'inicio' }) },
  { path: '*', element: <Navigate to="/cambio" replace /> },
];

const rutasTaller: RouteObject[] = [
  {
    path: '/',
    element: (
      <BorradorProvider>
        <ShellTaller />
      </BorradorProvider>
    ),
    children: [
      { index: true, element: <InicioTaller />, ...pantalla({ titulo: 'Modelos · Puntotres', seccion: 'inicio', fab: true }) },
      { path: 'ordenes', element: <PorLlegar />, ...pantalla({ titulo: 'Tengo que hacer', seccion: 'ordenes', fab: true }) },
      { path: 'avisos', element: <PorLlegar />, ...pantalla({ titulo: 'Mis avisos', seccion: 'avisos', fab: true }) },
      { path: 'modelos', element: <ModelosTaller />, ...pantalla({ titulo: 'Modelos', seccion: 'modelos', fab: true }) },
      { path: 'modelos/:id', element: <FichaModeloTaller />, ...pantalla({ titulo: 'Modelo', atras: '/modelos', seccion: 'modelos', fab: true }) },
      { path: 'cambio', children: rutasCambio },
      { path: 'escanear', element: <PorLlegar />, ...pantalla({ titulo: 'Escanear etiqueta', atras: '/' }) },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
];

function rutasDespacho(esAdmin: boolean): RouteObject[] {
  return [
    {
      path: '/',
      element: <ShellDespacho />,
      children: [
        { index: true, element: <InicioDespacho /> },
        { path: 'bandeja/*', element: <PorLlegar /> },
        // Opcional: con el id se abre su ficha a la derecha sin volver a montar la lista.
        { path: 'modelos/:id?', element: <ModelosDespacho /> },
        ...(esAdmin ? [{ path: 'admin', element: <PorLlegar /> }] : []),
        { path: '*', element: <Navigate to="/" replace /> },
      ],
    },
  ];
}

function Router({ rol }: { rol: Sesion['rol'] }) {
  const router = useMemo(
    () => createBrowserRouter(rol === 'TALLER' ? rutasTaller : rutasDespacho(rol === 'ADMIN'), { basename: '/app' }),
    [rol],
  );
  return <RouterProvider router={router} />;
}

export default function App() {
  const { sesion, cargando } = useSesion();
  // Mientras se pregunta quién ha entrado no se pinta nada: enseñar la entrada
  // un instante a quien ya está dentro sería un parpadeo.
  if (cargando) return null;
  if (!sesion) return <Entrada />;
  return <Router rol={sesion.rol} />;
}
