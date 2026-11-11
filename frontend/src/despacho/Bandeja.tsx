// La bandeja de despacho, en dos paneles: a la izquierda las dos pestañas
// («Del taller», los avisos que manda el taller; «Al taller», las órdenes que
// se le mandan), los contadores, los filtros y la lista; a la derecha el
// detalle de lo elegido o el alta de una orden nueva. Lo elegido va en la
// dirección (/app/bandeja/12, /app/bandeja/nueva?modelo=7) y la pestaña en
// ?pestana=despacho, así que un enlace abre lo mismo. La lista se vuelve a
// pedir tras cada acción y cuando los números de la bandeja cambian (llega un
// aviso nuevo, el taller contesta). Por debajo de 900 px los paneles se
// apilan y, con algo elegido, solo se ve el detalle.
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import FechaObjetivo from '../comun/FechaObjetivo';
import FotoModelo from '../comun/FotoModelo';
import { Icono } from '../comun/iconos';
import type { Cliente } from '../comun/modelos';
import {
  estaCerrada,
  estadoModificacion,
  estadosDelFiltro,
  fotoDelModelo,
  marcaDestino,
  puesto,
  tituloVisible,
  type FiltroEstado,
  type ModificacionResumen,
} from '../comun/modificaciones';
import { hace } from '../comun/tiempo';
import { useBandeja } from '../comun/useBandeja';
import Modificacion from './Modificacion';
import NuevaOrden from './NuevaOrden';

type Pestana = 'taller' | 'despacho';

const CHIPS_ESTADO: [FiltroEstado, string][] = [
  ['ABIERTAS', 'Abiertas'],
  ['NUEVAS', 'Nuevas'],
  ['HECHAS', 'Hechas'],
  ['DESCARTADAS', 'Descartadas'],
  ['TODAS', 'Todas'],
];

/** El nombre corto de cada destino en la fila (el largo va en el title). */
const DESTINO_CORTO: Record<string, string> = { EXCEL_ESCANDALLOS: 'Escandallos' };

/** Lo que se busca cuando se deja de teclear, no con cada letra. */
function useRetrasado(valor: string, ms = 300): string {
  const [retrasado, setRetrasado] = useState(valor);
  useEffect(() => {
    const espera = setTimeout(() => setRetrasado(valor.trim()), ms);
    return () => clearTimeout(espera);
  }, [valor, ms]);
  return retrasado;
}

function Fila({ m, elegida, onAbrir }: { m: ModificacionResumen; elegida: boolean; onAbrir: () => void }) {
  const estado = estadoModificacion(m.estado);
  const esAviso = m.direccion === 'TALLER_A_DESPACHO';
  return (
    <li>
      <button
        className="fila-bandeja"
        type="button"
        data-urgente={m.urgente && !estaCerrada(m.estado) ? 'true' : undefined}
        data-nueva={esAviso && m.estado === 'NUEVA' ? 'true' : undefined}
        aria-current={elegida ? 'true' : undefined}
        onClick={onAbrir}
      >
        <FotoModelo modelo={fotoDelModelo(m)} />
        <span className="cuerpo">
          <span className="tit">
            {tituloVisible(m)}
            {m.tituloDeIa && <span className="chip-ia">IA</span>}
          </span>
          <span className="meta">
            {!esAviso && m.posicionCola !== null && !estaCerrada(m.estado) && <b>{puesto(m.posicionCola)} · </b>}
            {m.modeloReferencia} · {hace(m.creadaEn)}
            {m.creadaPor && ` · de ${m.creadaPor}`}
          </span>
          <span className="etiquetas">
            {m.tipoCambioTexto && <span className="etiqueta">{m.tipoCambioTexto}</span>}
            {esAviso && m.destinos.length > 0 && (
              <span className="marcas-destino">
                {m.destinos.map((d) => {
                  const marca = marcaDestino(d);
                  return (
                    <span key={d.destino} className="marca-destino" data-d={marca.estado} title={marca.texto} aria-label={marca.texto}>
                      {marca.simbolo} {DESTINO_CORTO[d.destino] ?? d.texto}
                    </span>
                  );
                })}
              </span>
            )}
            {m.urgente && !estaCerrada(m.estado) && <span className="pill urgente">Urgente</span>}
            {!estaCerrada(m.estado) && <FechaObjetivo fecha={m.fechaObjetivo} />}
            {m.respuestaPendienteDe === 'DESPACHO' && <span className="etiqueta espera">Te toca responder</span>}
          </span>
        </span>
        <span className={`pill ${estado.pill}`}>{estado.texto}</span>
      </button>
    </li>
  );
}

export default function Bandeja() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const navegar = useNavigate();
  const { resumen, recargar: recargarBandeja } = useBandeja();

  const nueva = id === 'nueva';
  const elegido = id && /^\d+$/.test(id) ? Number(id) : null;
  const modeloInicial = Number(params.get('modelo')) || null;
  // El alta de una orden va con «Al taller» aunque el enlace no lo diga.
  const pestanaPedida = params.get('pestana');
  const pestana: Pestana = pestanaPedida === 'despacho' || (nueva && pestanaPedida === null) ? 'despacho' : 'taller';
  const sufijo = pestana === 'despacho' ? '?pestana=despacho' : '';

  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [filtroEstado, setFiltroEstado] = useState<FiltroEstado>('ABIERTAS');
  const [cliente, setCliente] = useState('');
  const [textoModelo, setTextoModelo] = useState('');
  const [texto, setTexto] = useState('');
  const modelo = useRetrasado(textoModelo);
  const busqueda = useRetrasado(texto);
  const [lista, setLista] = useState<ModificacionResumen[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    api<Cliente[]>('/api/clientes')
      .then(setClientes)
      .catch(() => setClientes([]));
  }, []);

  // Al cambiar de pestaña, la lista de la otra no se queda pintada mientras llega la suya.
  useEffect(() => setLista(null), [pestana]);

  // Cuando cambian los números de la bandeja hay algo nuevo que enseñar.
  const cambio = resumen ? `${resumen.nuevas}|${resumen.pendientes}|${resumen.urgentes}|${resumen.conRespuesta}` : '';
  useEffect(() => {
    const p = new URLSearchParams();
    p.set('direccion', pestana === 'taller' ? 'TALLER_A_DESPACHO' : 'DESPACHO_A_TALLER');
    for (const e of estadosDelFiltro(filtroEstado)) p.append('estado', e);
    if (cliente) p.set('cliente', cliente);
    if (busqueda) p.set('q', busqueda);
    let vigente = true;
    api<ModificacionResumen[]>(`/api/modificaciones?${p}`)
      .then((l) => {
        if (!vigente) return;
        setLista(l);
        setError(null);
      })
      .catch((e: unknown) => vigente && setError(e instanceof ErrorApi ? e.message : 'No se ha podido cargar la bandeja'));
    return () => {
      vigente = false;
    };
  }, [pestana, filtroEstado, cliente, busqueda, version, cambio]);

  /** Tras una acción en el detalle: la lista y los números de la barra. */
  const recargar = useCallback(() => {
    setVersion((v) => v + 1);
    recargarBandeja();
  }, [recargarBandeja]);

  // El modelo se filtra aquí: la API filtra por id, y aquí se teclea la referencia o el nombre.
  const filtroModelo = modelo.toLowerCase();
  const visibles =
    lista?.filter((m) => !filtroModelo || `${m.modeloReferencia} ${m.modeloNombre ?? ''}`.toLowerCase().includes(filtroModelo)) ?? null;

  const cambiarPestana = (p: Pestana) => navegar(p === 'despacho' ? '/bandeja?pestana=despacho' : '/bandeja');
  const abrir = (modId: number) => navegar(`/bandeja/${modId}${sufijo}`);
  const volverALista = () => navegar(`/bandeja${sufijo}`);
  const creada = (r: ModificacionResumen) => {
    recargar();
    navegar(`/bandeja/${r.id}?pestana=despacho`);
  };

  const sinFiltros = filtroEstado === 'ABIERTAS' && !cliente && !modelo && !busqueda;
  const contadores: { clase: string; numero: number; texto: string; title: string }[] = resumen
    ? [
        { clase: 'nuevo', numero: resumen.nuevas, texto: 'nuevas', title: 'Avisos del taller que nadie ha abierto todavía' },
        { clase: '', numero: resumen.pendientes, texto: 'pendientes', title: 'Avisos del taller sin acabar (nuevos, vistos o en curso)' },
        { clase: 'urgente', numero: resumen.urgentes, texto: 'urgentes', title: 'Órdenes para el taller marcadas urgentes y sin hacer' },
        { clase: 'con-respuesta', numero: resumen.conRespuesta, texto: 'con respuesta', title: 'Órdenes con una duda del taller por contestar' },
      ]
    : [];

  return (
    <div className={elegido !== null || nueva ? 'paneles con-detalle' : 'paneles'}>
      <section className="panel-lista" aria-label="Bandeja">
        <div className="botones">
          <button className="boton marca" type="button" onClick={() => navegar('/bandeja/nueva?pestana=despacho')}>
            <Icono nombre="mas" /> Nueva orden
          </button>
        </div>

        <div className="pestanas-bandeja">
          <button type="button" aria-pressed={pestana === 'taller'} onClick={() => cambiarPestana('taller')}>
            Del taller
          </button>
          <button type="button" aria-pressed={pestana === 'despacho'} onClick={() => cambiarPestana('despacho')}>
            Al taller
          </button>
        </div>

        {contadores.length > 0 && (
          <div className="contadores" aria-label="Resumen de la bandeja">
            {contadores.map((c) => (
              <span key={c.texto} className={`dato ${c.clase} ${c.numero === 0 ? 'cero' : ''}`} title={c.title}>
                <b>{c.numero}</b> {c.texto}
              </span>
            ))}
          </div>
        )}

        <div className="chips">
          {CHIPS_ESTADO.map(([clave, txt]) => (
            <button key={clave} className="chip" type="button" aria-pressed={filtroEstado === clave} onClick={() => setFiltroEstado(clave)}>
              {txt}
            </button>
          ))}
        </div>
        <div className="filtros">
          <label className="campo">
            Cliente
            <select value={cliente} onChange={(e) => setCliente(e.target.value)}>
              <option value="">Todos los clientes</option>
              {clientes.map((c) => (
                <option key={c.clave} value={c.clave}>
                  {c.nombre}
                </option>
              ))}
            </select>
          </label>
          <label className="campo">
            Modelo
            <input type="search" value={textoModelo} placeholder="Referencia o nombre" onChange={(e) => setTextoModelo(e.target.value)} />
          </label>
          <label className="campo ancho">
            Buscar
            <input type="search" value={texto} placeholder="Título, texto o lo que se dijo en la nota" onChange={(e) => setTexto(e.target.value)} />
          </label>
        </div>

        {error ? (
          <div className="vacio">
            <b>{error}</b>
            <button className="boton" type="button" onClick={recargar}>
              Reintentar
            </button>
          </div>
        ) : visibles === null ? null : visibles.length === 0 ? (
          sinFiltros ? (
            <div className="vacio">
              <Icono nombre="ok" />
              <b>{pestana === 'taller' ? 'Nada nuevo del taller' : 'No hay órdenes pendientes'}</b>
            </div>
          ) : (
            <p className="ayuda">Nada con estos filtros.</p>
          )
        ) : (
          <ul className="lista-bandeja">
            {visibles.map((m) => (
              <Fila key={m.id} m={m} elegida={m.id === elegido} onAbrir={() => abrir(m.id)} />
            ))}
          </ul>
        )}
      </section>

      <section className="panel-detalle" aria-label="Detalle">
        {(elegido !== null || nueva) && (
          <button className="enlace volver-lista" type="button" onClick={volverALista}>
            Volver a la lista
          </button>
        )}
        {nueva ? (
          // Con key, otro modelo de partida (otro enlace «Nueva orden para este modelo») empieza un alta limpia.
          <NuevaOrden key={modeloInicial ?? 'sin-modelo'} modeloInicial={modeloInicial} onCreada={creada} onCancelar={volverALista} />
        ) : elegido !== null ? (
          // Con key, cada modificación monta su propio detalle: nada de la anterior (respuesta a medias, diálogo) pasa a la nueva.
          <Modificacion key={elegido} id={elegido} onCambiado={recargar} />
        ) : (
          <div className="vacio">
            <b>{pestana === 'taller' ? 'Elige un aviso de la lista' : 'Elige una orden de la lista'}</b>
          </div>
        )}
      </section>
    </div>
  );
}
