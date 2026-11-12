// El marco de la cara de despacho (PC): una barra arriba con la marca, las
// pestañas, quién ha entrado y «Salir»; debajo, la pantalla. La pestaña
// «Usuarios» (/admin) solo la ve el rol ADMIN. El marco lleva también la consulta de la bandeja
// (useBandeja, la misma del taller): la pepita de «Bandeja de Modificaciones» y los contadores
// de la bandeja leen de ella, y las acciones la vuelven a pedir. Encima de la
// pantalla, mientras no se hayan activado, la barra para activar los avisos.
import { Link, Outlet, useLocation } from 'react-router';
import ActivarAvisos from '../comun/ActivarAvisos';
import { Icono } from '../comun/iconos';
import { LogoPuntotres } from '../comun/LogoPuntotres';
import { numeroBandejaDespacho } from '../comun/modificaciones';
import { useSesion } from '../comun/sesion';
import { ProveedorBandeja, useBandeja } from '../comun/useBandeja';

type Pestana = { ruta: string; texto: string; activa: (camino: string) => boolean; soloAdmin?: boolean; pepita?: boolean };

const PESTANAS: Pestana[] = [
  // La bandeja es el inicio de despacho: también queda marcada en «/».
  { ruta: '/bandeja', texto: 'Bandeja de Modificaciones', activa: (c) => c === '/' || c.startsWith('/bandeja'), pepita: true },
  // Una modificación de solo lectura se abre desde la ficha de un modelo: sigue marcada «Modelos».
  { ruta: '/modelos', texto: 'Modelos', activa: (c) => c.startsWith('/modelos') || c.startsWith('/modificaciones') },
  // La cola del taller: el orden en que hará las órdenes.
  { ruta: '/cola', texto: 'Lista de Prioridades', activa: (c) => c.startsWith('/cola') },
  // Cuánto lleva cada cosa sin hacerse: información, no alertas.
  { ruta: '/reportes', texto: 'Reportes', activa: (c) => c.startsWith('/reportes') },
  // Usuarios y tablets: solo la ve el rol ADMIN (y el servidor lo vuelve a exigir).
  { ruta: '/admin', texto: 'Usuarios', activa: (c) => c.startsWith('/admin'), soloAdmin: true },
];

function Marco() {
  const { sesion, salir } = useSesion();
  const { pathname } = useLocation();
  const { resumen } = useBandeja();
  const esAdmin = sesion?.rol === 'ADMIN';
  // Lo que hay que mirar ya: avisos nuevos del taller y órdenes con una duda por contestar.
  const pendientes = numeroBandejaDespacho(resumen);

  return (
    <div className="despacho">
      <header className="despacho-barra">
        <span className="marca">
          <LogoPuntotres className="logo" />
          Modelos · Puntotres
        </span>
        <nav className="pestanas" aria-label="Secciones">
          {PESTANAS.filter((p) => !p.soloAdmin || esAdmin).map((p) => (
            <Link key={p.ruta} to={p.ruta} aria-current={p.activa(pathname) ? 'page' : undefined}>
              {p.texto}
              {p.pepita && pendientes > 0 && (
                <span className="pepita" aria-label={`${pendientes} por mirar`}>
                  {pendientes}
                </span>
              )}
            </Link>
          ))}
        </nav>
        <div className="usuario">
          <span>{sesion?.nombre}</span>
          <button type="button" onClick={() => void salir()}>
            <Icono nombre="salir" /> Salir
          </button>
        </div>
      </header>
      <main>
        <ActivarAvisos compacta motivo="Para enterarte cuando el taller avise de un cambio" />
        <Outlet />
      </main>
    </div>
  );
}

export default function Shell() {
  return (
    <ProveedorBandeja>
      <Marco />
    </ProveedorBandeja>
  );
}
