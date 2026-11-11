// La app según quién haya entrado: nadie → la entrada; el taller → su marco
// de tablet; despacho y admin → el suyo de PC. Cada cara tiene su router
// (basename /app, donde la sirve Spring Boot), y una ruta que no existe
// vuelve al inicio.
import { useMemo } from 'react';
import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { useSesion, type Sesion } from './comun/sesion';
import AdminDespacho from './despacho/Admin';
import ShellDespacho from './despacho/Shell';
import BandejaDespacho from './despacho/Bandeja';
import EtiquetasPatrones from './despacho/EtiquetasPatrones';
import ModelosDespacho from './despacho/Modelos';
import ColaDespacho from './despacho/Cola';
import ReportesDespacho from './despacho/Reportes';
import VerModificacion from './despacho/VerModificacion';
import Entrada from './entrada/Entrada';
import Duda from './taller/Duda';
import Aviso from './taller/Aviso';
import MisAvisos from './taller/MisAvisos';
import Orden from './taller/Orden';
import Ordenes from './taller/Ordenes';
import ShellTaller, { type Pantalla } from './taller/Shell';
import { BorradorProvider } from './taller/cambio/Borrador';
import ElegirModelo from './taller/cambio/ElegirModelo';
import Enviado from './taller/cambio/Enviado';
import Fotos from './taller/cambio/Fotos';
import Grabar from './taller/cambio/Grabar';
import QueHasCambiado from './taller/cambio/QueHasCambiado';
import Escanear from './taller/Escanear';
import Revisar from './taller/cambio/Revisar';
import FichaModeloTaller from './taller/FichaModelo';
import InicioTaller from './taller/Inicio';
import ModelosTaller from './taller/Modelos';

function pantalla(datos: Pantalla): { handle: Pantalla } {
  return { handle: datos };
}

/**
 * «Crear Modificación», paso a paso: cada paso es su ruta (atrás, recargar y el
 * título de la cabecera funcionan solos) y el borrador va en un contexto por
 * encima de todas, así que salir por la barra de abajo no lo pierde. Sin
 * escáner flotante: taparía los botones de abajo y aquí no hace falta.
 */
const rutasCambio: RouteObject[] = [
  { index: true, element: <ElegirModelo />, ...pantalla({ titulo: '¿Qué modelo?', atras: '/', seccion: 'inicio' }) },
  // El título lo pone el paso con la referencia y el nombre del modelo.
  { path: 'tipo', element: <QueHasCambiado />, ...pantalla({ titulo: 'Crear Modificación', atras: '/cambio', seccion: 'inicio' }) },
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
      { index: true, element: <InicioTaller />, ...pantalla({ titulo: 'Inicio · Puntotres', seccion: 'inicio', fab: true }) },
      { path: 'ordenes', element: <Ordenes />, ...pantalla({ titulo: 'Bandeja de Entrada', seccion: 'ordenes', fab: true }) },
      // El título lo pone la orden con el suyo. Sin escáner: taparía «Hecho».
      { path: 'ordenes/:id', element: <Orden />, ...pantalla({ titulo: 'Orden', atras: '/ordenes', seccion: 'ordenes' }) },
      { path: 'ordenes/:id/duda', element: <Duda modo="duda" />, ...pantalla({ titulo: 'Tengo una duda', atras: (p) => `/ordenes/${p.id}`, seccion: 'ordenes' }) },
      { path: 'avisos', element: <MisAvisos />, ...pantalla({ titulo: 'Mis avisos', seccion: 'avisos', fab: true }) },
      // El título lo pone el aviso con su modelo. Sin escáner: taparía «Guardar».
      { path: 'avisos/:id', element: <Aviso />, ...pantalla({ titulo: 'Mi aviso', atras: '/avisos', seccion: 'avisos' }) },
      { path: 'avisos/:id/contestar', element: <Duda modo="contestar" />, ...pantalla({ titulo: 'Contestar a despacho', atras: (p) => `/avisos/${p.id}`, seccion: 'avisos' }) },
      { path: 'modelos', element: <ModelosTaller />, ...pantalla({ titulo: 'Modelos', seccion: 'modelos', fab: true }) },
      { path: 'modelos/:id', element: <FichaModeloTaller />, ...pantalla({ titulo: 'Modelo', atras: '/modelos', seccion: 'modelos', fab: true }) },
      { path: 'cambio', children: rutasCambio },
      { path: 'escanear', element: <Escanear />, ...pantalla({ titulo: 'Escanear etiqueta', atras: '/' }) },
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
        // La bandeja es el inicio de despacho.
        { index: true, element: <Navigate to="/bandeja" replace /> },
        // Con el id se abre su detalle a la derecha sin volver a montar la lista; «nueva» abre el alta de una orden.
        { path: 'bandeja/:id?', element: <BandejaDespacho /> },
        // La cola del taller: en qué orden hará las órdenes, y cambiarlo.
        { path: 'cola', element: <ColaDespacho /> },
        // Cuántos días lleva sin hacerse la primera de la cola, cada urgente y las de fecha pasada.
        { path: 'reportes', element: <ReportesDespacho /> },
        // Opcional: con el id se abre su ficha a la derecha sin volver a montar la lista.
        { path: 'modelos/:id?', element: <ModelosDespacho /> },
        // Una modificación de solo lectura, desde el historial de un modelo.
        { path: 'modificaciones/:id', element: <VerModificacion /> },
        // Solo el admin: a cualquier otro rol la dirección le devuelve a la bandeja.
        { path: 'admin', element: esAdmin ? <AdminDespacho /> : <Navigate to="/bandeja" replace /> },
        { path: '*', element: <Navigate to="/" replace /> },
      ],
    },
    // La hoja de etiquetas de patrones va fuera del marco: es para imprimir y en el papel no sale nada más.
    { path: '/etiquetas-patrones', element: <EtiquetasPatrones /> },
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
