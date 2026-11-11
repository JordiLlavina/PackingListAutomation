// «Bandeja de Entrada»: las órdenes que despacho manda al taller, en el orden de
// la cola que pone el despacho (la primera es la siguiente que toca, y se
// destaca) y las hechas debajo, con los mismos filtros que la rejilla de
// modelos (texto, cliente y etapa; petición del usuario). La lista se vuelve a
// pedir cuando cambian los números de la bandeja, así que una orden que llega
// con la pantalla abierta aparece sola.
//
// Cuando el despacho mueve una orden entre los tres primeros puestos, la
// tarjeta lo dice con una etiqueta y un marco suaves («Prioridad cambiada al
// 2º puesto»), sin sonido ni vibración, y los dos se van en el siguiente
// refresco (decisión del usuario). Para saber qué ha cambiado se guardan en
// este navegador los puestos tal como se vieron la última vez (cambiosDePuesto).
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import FotoModelo from '../comun/FotoModelo';
import { Icono } from '../comun/iconos';
import FechaObjetivo from '../comun/FechaObjetivo';
import {
  cambiosDePuesto,
  colaFiltrada,
  estaCerrada,
  estadoOrden,
  filtrarCola,
  fotoDelModelo,
  ordenarOrdenes,
  puesto,
  puestosDe,
  textoCambioPuesto,
  type ModificacionResumen,
} from '../comun/modificaciones';
import { hace } from '../comun/tiempo';
import { useBandeja } from '../comun/useBandeja';
import FiltrosModelos, { useFiltrosEnDireccion } from './FiltrosModelos';

/** Los puestos de la cola tal como se vieron la última vez, en este navegador. */
const PUESTOS_VISTOS = 'modelos.cola.puestos-vistos';

function leerPuestosVistos(): Record<string, number> {
  try {
    const guardado = localStorage.getItem(PUESTOS_VISTOS);
    return guardado ? (JSON.parse(guardado) as Record<string, number>) : {};
  } catch {
    return {};
  }
}

function guardarPuestosVistos(puestos: Record<string, number>) {
  try {
    localStorage.setItem(PUESTOS_VISTOS, JSON.stringify(puestos));
  } catch {
    // Sin almacenamiento (ventana privada) no se puede comparar: no sale ninguna etiqueta.
  }
}

function Tarjeta({ orden, siguiente = false, cambioDePuesto = null }: { orden: ModificacionResumen; siguiente?: boolean; cambioDePuesto?: number | null }) {
  const navegar = useNavigate();
  const estado = estadoOrden(orden.estado);
  const cerrada = estaCerrada(orden.estado);
  // La clase de la maqueta (nueva, vista, hecha): las cerradas se ven más apagadas.
  const clases = `orden ${cerrada ? 'hecha' : estado.pill}${siguiente ? ' siguiente' : ''}`;
  return (
    <button
      className={clases}
      type="button"
      data-urgente={orden.urgente && !cerrada ? 'true' : undefined}
      data-cambio={cambioDePuesto !== null ? 'true' : undefined}
      onClick={() => navegar(`/ordenes/${orden.id}`)}
    >
      <span className="franja" />
      {!cerrada && orden.posicionCola !== null && (
        <span className="puesto" aria-label={`${puesto(orden.posicionCola)} de la cola`}>
          {puesto(orden.posicionCola)}
        </span>
      )}
      <FotoModelo modelo={fotoDelModelo(orden)} />
      <span className="cuerpo">
        {siguiente && <span className="la-siguiente">La siguiente</span>}
        <span className="tit">{orden.titulo ?? orden.tipoCambioTexto}</span>
        <span className="meta">
          <span>
            <b>{orden.modeloReferencia}</b> · {orden.modeloNombre}
          </span>
        </span>
        <span className="meta">
          <span className={`pill ${estado.pill}`}>{estado.texto}</span>
          {orden.urgente && !cerrada && <span className="pill urgente">Urgente</span>}
          {cambioDePuesto !== null && <span className="pill cambio">{textoCambioPuesto(cambioDePuesto)}</span>}
          {!cerrada && <FechaObjetivo fecha={orden.fechaObjetivo} />}
          <span className="cuando">
            {hace(orden.creadaEn)}
            {orden.creadaPor && ` · de ${orden.creadaPor}`}
          </span>
        </span>
      </span>
    </button>
  );
}

export default function Ordenes() {
  const { resumen } = useBandeja();
  const [ordenes, setOrdenes] = useState<ModificacionResumen[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Las órdenes que han cambiado de puesto desde la última vez que se vio la lista, con su puesto nuevo.
  const [cambios, setCambios] = useState<Map<number, number>>(new Map());
  const { filtros, poner } = useFiltrosEnDireccion();

  const peticion = useRef(0);
  const cargar = useCallback(() => {
    const esta = ++peticion.current;
    api<ModificacionResumen[]>('/api/modificaciones?direccion=DESPACHO_A_TALLER')
      .then((lista) => {
        if (esta !== peticion.current) return;
        setOrdenes(lista);
        setError(null);
        // Lo que se ve ahora pasa a ser «lo visto»: en la siguiente carga estas etiquetas ya no salen.
        setCambios(cambiosDePuesto(leerPuestosVistos(), lista));
        guardarPuestosVistos(puestosDe(lista));
      })
      .catch((e: unknown) => esta === peticion.current && setError(e instanceof ErrorApi ? e.message : 'No se han podido cargar las órdenes'));
  }, []);

  // Al entrar y cada vez que la bandeja dice que algo ha cambiado (también cuando una orden pasa a urgente).
  const cambio = resumen ? `${resumen.nuevas}·${resumen.pendientes}·${resumen.urgentes}` : '';
  useEffect(cargar, [cargar, cambio]);

  if (error && !ordenes) {
    return (
      <div className="vacio">
        <b>{error}</b>
        <button className="boton" type="button" onClick={cargar}>
          Reintentar
        </button>
      </div>
    );
  }
  if (!ordenes) return null;
  const clientes = [...new Set(ordenes.map((o) => o.cliente))].sort();
  const filtrando = colaFiltrada(filtros);
  // El filtro busca en la referencia, el nombre del modelo y el título de la orden (el mismo que la cola de despacho).
  const { pendientes, hechas } = ordenarOrdenes(filtrarCola(ordenes, filtros));
  // «La siguiente» es la primera de la cola entera: con un filtro puesto, la primera que se ve puede no serlo.
  const siguienteId = ordenarOrdenes(ordenes).pendientes[0]?.id;

  return (
    <>
      <FiltrosModelos filtros={filtros} clientes={clientes} placeholder="Buscar referencia, nombre u orden" onCambiar={poner} />
      {pendientes.length > 0 ? (
        <div className="lista">
          {pendientes.map((o) => (
            <Tarjeta key={o.id} orden={o} siguiente={o.id === siguienteId} cambioDePuesto={cambios.get(o.id) ?? null} />
          ))}
        </div>
      ) : filtrando ? (
        <p className="ayuda">Ninguna orden pendiente con estos filtros.</p>
      ) : (
        <div className="vacio">
          <Icono nombre="ok" />
          <b>No tienes nada pendiente</b>
          <span>Cuando despacho mande una orden, sonará y vibrará.</span>
        </div>
      )}
      {hechas.length > 0 && (
        <>
          <div className="fila-titulo">
            <h2>Hechas</h2>
          </div>
          <div className="lista">
            {hechas.map((o) => (
              <Tarjeta key={o.id} orden={o} />
            ))}
          </div>
        </>
      )}
    </>
  );
}
