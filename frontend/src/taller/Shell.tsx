// El marco de la cara de taller (la tablet), copiado de la maqueta: cabecera
// pegada arriba con el título y, si hace falta, «atrás»; el contenido con
// scroll; la barra inferior con los cuatro destinos, y el botón flotante de
// escanear en las pantallas de consulta. Cada ruta dice en su `handle` cómo
// se pinta el marco (ver Pantalla). El marco también lleva la consulta de la
// bandeja (useBandeja), que comparten la pepita de «Bandeja de Entrada», la losa
// de Inicio y las listas.
import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { Outlet, useMatches, useNavigate, type Params } from 'react-router';
import { Icono, type NombreIcono } from '../comun/iconos';
import { llegaronNuevas } from '../comun/modificaciones';
import { toast } from '../comun/toast';
import { ProveedorBandeja, useBandeja } from '../comun/useBandeja';

export type Seccion = 'inicio' | 'ordenes' | 'avisos' | 'modelos';

/** Lo que cada ruta del taller le dice al marco. */
export type Pantalla = {
  titulo: string;
  /**
   * Destino de «atrás» si no hay historial; sin él no hay botón. Con una
   * función, se calcula con los parámetros de la ruta (la duda de una orden
   * vuelve a esa orden).
   */
  atras?: string | ((params: Params) => string);
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
  { seccion: 'ordenes', ruta: '/ordenes', texto: 'Bandeja de Entrada', icono: 'bandeja' },
  { seccion: 'avisos', ruta: '/avisos', texto: 'Mis avisos', icono: 'lista' },
  { seccion: 'modelos', ruta: '/modelos', texto: 'Modelos', icono: 'rejilla' },
];

const PANTALLA_POR_DEFECTO: Pantalla = { titulo: 'Inicio · Puntotres', seccion: 'inicio' };

/**
 * El título que una pantalla pone en la cabecera cuando el de su ruta no basta
 * (la ficha de un modelo lleva su referencia, como en la maqueta). Mientras
 * llega, o con null, se ve el título fijo de la ruta; al salir de la pantalla
 * vuelve el fijo.
 */
const TituloContexto = createContext<(titulo: string | null) => void>(() => {});

export function useTitulo(titulo: string | null) {
  const poner = useContext(TituloContexto);
  useEffect(() => {
    poner(titulo);
    return () => poner(null);
  }, [poner, titulo]);
}

/**
 * Con la app abierta, una orden nueva vibra y lo dice (la maqueta: «sonará y
 * vibrará»). Con la app cerrada lo hará la notificación push (Task 17).
 */
function useAvisoDeOrdenNueva(nuevas: number | undefined) {
  const anterior = useRef<number | null>(null);
  useEffect(() => {
    if (nuevas === undefined) return;
    if (llegaronNuevas(anterior.current, nuevas)) {
      navigator.vibrate?.(200);
      toast('Tienes una orden nueva');
    }
    anterior.current = nuevas;
  }, [nuevas]);
}

function Marco() {
  const navegar = useNavigate();
  const matches = useMatches();
  const ultima = matches.at(-1);
  const pantalla = (ultima?.handle as Pantalla | undefined) ?? PANTALLA_POR_DEFECTO;
  const [tituloPropio, setTituloPropio] = useState<string | null>(null);
  const { resumen } = useBandeja();
  useAvisoDeOrdenNueva(resumen?.nuevas);
  const pendientes: Partial<Record<Seccion, number>> = { ordenes: resumen?.nuevas ?? 0 };
  const destinoAtras = typeof pantalla.atras === 'function' ? pantalla.atras(ultima?.params ?? {}) : pantalla.atras;

  const atras = () => {
    if (window.history.length > 1) navegar(-1);
    else navegar(destinoAtras ?? '/');
  };

  return (
    <>
      <header className="cabecera">
        {destinoAtras && (
          <button className="atras" type="button" aria-label="Volver" onClick={atras}>
            <Icono nombre="atras" />
          </button>
        )}
        <h1>{tituloPropio ?? pantalla.titulo}</h1>
      </header>
      <main>
        <TituloContexto.Provider value={setTituloPropio}>
          <Outlet />
        </TituloContexto.Provider>
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
              {numero > 0 && (
                <span className="pepita" aria-label={`${numero} ${numero === 1 ? 'nueva' : 'nuevas'}`}>
                  {numero}
                </span>
              )}
            </button>
          );
        })}
      </nav>
    </>
  );
}

export default function Shell() {
  return (
    <ProveedorBandeja>
      <Marco />
    </ProveedorBandeja>
  );
}
