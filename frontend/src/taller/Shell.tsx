// El marco de la cara de taller (la tablet), copiado de la maqueta: cabecera
// pegada arriba con el título y, si hace falta, «atrás»; el contenido con
// scroll; la barra inferior con los cuatro destinos, y el botón flotante de
// escanear en las pantallas de consulta. Cada ruta dice en su `handle` cómo
// se pinta el marco (ver Pantalla).
import { Outlet, useMatches, useNavigate } from 'react-router';
import { Icono, type NombreIcono } from '../comun/iconos';

export type Seccion = 'inicio' | 'ordenes' | 'avisos' | 'modelos';

/** Lo que cada ruta del taller le dice al marco. */
export type Pantalla = {
  titulo: string;
  /** Destino de «atrás» si no hay historial; sin él no hay botón. */
  atras?: string;
  /** El destino de la barra inferior que queda marcado (el escáner no marca ninguno). */
  seccion?: Seccion;
  /**
   * El escáner flotante, solo en las pantallas de consulta: dentro de un aviso
   * o de una orden taparía el botón de abajo y no hace falta cambiar de modelo.
   */
  fab?: boolean;
};

const DESTINOS: { seccion: Seccion; ruta: string; texto: string; icono: NombreIcono }[] = [
  { seccion: 'inicio', ruta: '/', texto: 'Inicio', icono: 'casa' },
  { seccion: 'ordenes', ruta: '/ordenes', texto: 'Tengo que hacer', icono: 'bandeja' },
  { seccion: 'avisos', ruta: '/avisos', texto: 'Mis avisos', icono: 'lista' },
  { seccion: 'modelos', ruta: '/modelos', texto: 'Modelos', icono: 'rejilla' },
];

const PANTALLA_POR_DEFECTO: Pantalla = { titulo: 'Modelos · Puntotres', seccion: 'inicio' };

export default function Shell() {
  const navegar = useNavigate();
  const matches = useMatches();
  const pantalla = (matches.at(-1)?.handle as Pantalla | undefined) ?? PANTALLA_POR_DEFECTO;
  // Las órdenes nuevas de despacho llegan en Task 14; hasta entonces la pepita no sale.
  const pendientes: Partial<Record<Seccion, number>> = {};

  const atras = () => {
    if (window.history.length > 1) navegar(-1);
    else navegar(pantalla.atras ?? '/');
  };

  return (
    <>
      <header className="cabecera">
        {pantalla.atras && (
          <button className="atras" type="button" aria-label="Volver" onClick={atras}>
            <Icono nombre="atras" />
          </button>
        )}
        <h1>{pantalla.titulo}</h1>
      </header>
      <main>
        <Outlet />
      </main>
      {pantalla.fab && (
        <button className="fab" type="button" aria-label="Escanear etiqueta" title="Escanear etiqueta" onClick={() => navegar('/escanear')}>
          <Icono nombre="qr" />
        </button>
      )}
      <nav className="nav" aria-label="Secciones">
        {DESTINOS.map((d) => {
          const numero = pendientes[d.seccion] ?? 0;
          return (
            <button
              key={d.seccion}
              type="button"
              aria-current={pantalla.seccion === d.seccion ? 'page' : undefined}
              onClick={() => navegar(d.ruta)}
            >
              <Icono nombre={d.icono} />
              <span>{d.texto}</span>
              {numero > 0 && <span className="pepita">{numero}</span>}
            </button>
          );
        })}
      </nav>
    </>
  );
}
