// El marco de la cara de despacho (PC): una barra arriba con la marca, las
// pestañas, quién ha entrado y «Salir»; debajo, la pantalla. La pestaña Admin
// solo la ve el rol ADMIN.
import { Link, Outlet, useLocation } from 'react-router';
import { Icono } from '../comun/iconos';
import { useSesion } from '../comun/sesion';

type Pestana = { ruta: string; texto: string; activa: (camino: string) => boolean; soloAdmin?: boolean };

const PESTANAS: Pestana[] = [
  // La bandeja es el inicio de despacho: también queda marcada en «/».
  { ruta: '/bandeja', texto: 'Bandeja', activa: (c) => c === '/' || c.startsWith('/bandeja') },
  { ruta: '/modelos', texto: 'Modelos', activa: (c) => c.startsWith('/modelos') },
  { ruta: '/admin', texto: 'Admin', activa: (c) => c.startsWith('/admin'), soloAdmin: true },
];

export default function Shell() {
  const { sesion, salir } = useSesion();
  const { pathname } = useLocation();
  const esAdmin = sesion?.rol === 'ADMIN';

  return (
    <div className="despacho">
      <header className="despacho-barra">
        <span className="marca">Modelos · Puntotres</span>
        <nav className="pestanas" aria-label="Secciones">
          {PESTANAS.filter((p) => !p.soloAdmin || esAdmin).map((p) => (
            <Link key={p.ruta} to={p.ruta} aria-current={p.activa(pathname) ? 'page' : undefined}>
              {p.texto}
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
        <Outlet />
      </main>
    </div>
  );
}
