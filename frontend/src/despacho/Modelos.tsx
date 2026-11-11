// Los modelos en despacho, en dos paneles: a la izquierda los filtros
// (cliente, etapa, temporada y texto) y la lista, con una casilla por modelo
// para elegir varios (los elegidos van a la hoja de etiquetas de patrones,
// /app/etiquetas-patrones); a la derecha la ficha del elegido o el alta de uno
// nuevo. El elegido va en la dirección (/app/modelos/12), así que un enlace o
// el QR de un patrón (/m/12) abren su ficha. Por debajo de 900 px los paneles
// se apilan y, con un modelo elegido, solo se ve su ficha.
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Etapa from '../comun/Etapa';
import FotoModelo from '../comun/FotoModelo';
import { Icono } from '../comun/iconos';
import type { Cliente, Etapa as TipoEtapa, ModeloResumen, Temporada } from '../comun/modelos';
import AltaModelo from './AltaModelo';
import FichaModelo from './FichaModelo';

const CHIPS_ETAPA: [TipoEtapa | '', string][] = [
  ['', 'Todas'],
  ['PROTOTIPO', 'Prototipos'],
  ['SMS', 'Muestras'],
  ['PRODUCCION', 'Producción'],
  ['INACTIVO', 'Inactivos'],
];

type Filtros = { cliente: string; etapa: TipoEtapa | ''; temporada: string; texto: string; sinEtiqueta: boolean };

const CLAVE_FILTROS = 'despacho-modelos-filtros';
const SIN_FILTROS: Filtros = { cliente: '', etapa: '', temporada: '', texto: '', sinEtiqueta: false };

/**
 * Los filtros se recuerdan en la pestaña (sessionStorage): la hoja de
 * etiquetas va fuera del marco y, al volver de imprimir, la lista se vuelve a
 * montar con los mismos filtros, «Sin etiqueta impresa» incluido.
 */
function filtrosGuardados(): Filtros {
  try {
    const guardado: unknown = JSON.parse(sessionStorage.getItem(CLAVE_FILTROS) ?? 'null');
    if (!guardado || typeof guardado !== 'object') return SIN_FILTROS;
    const f = guardado as Partial<Record<keyof Filtros, unknown>>;
    const texto = (v: unknown) => (typeof v === 'string' ? v : '');
    const etapa = CHIPS_ETAPA.some(([clave]) => clave === f.etapa) ? (f.etapa as TipoEtapa | '') : '';
    return { cliente: texto(f.cliente), etapa, temporada: texto(f.temporada), texto: texto(f.texto), sinEtiqueta: f.sinEtiqueta === true };
  } catch {
    return SIN_FILTROS;
  }
}

export default function Modelos() {
  const { id } = useParams();
  const elegido = id && /^\d+$/.test(id) ? Number(id) : null;
  const navegar = useNavigate();

  const [inicial] = useState(filtrosGuardados);
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [cliente, setCliente] = useState(inicial.cliente);
  const [etapa, setEtapa] = useState<TipoEtapa | ''>(inicial.etapa);
  const [temporadas, setTemporadas] = useState<Temporada[]>([]);
  const [temporada, setTemporada] = useState(inicial.temporada);
  const [texto, setTexto] = useState(inicial.texto);
  const [busqueda, setBusqueda] = useState(inicial.texto.trim());
  const [sinEtiqueta, setSinEtiqueta] = useState(inicial.sinEtiqueta);
  const [modelos, setModelos] = useState<ModeloResumen[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [marcados, setMarcados] = useState<Set<number>>(new Set());
  const [alta, setAlta] = useState(false);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    api<Cliente[]>('/api/clientes')
      .then(setClientes)
      .catch(() => setClientes([]));
  }, []);

  // La temporada elegida se borra al cambiar de cliente (cambiarCliente), no aquí: al volver a la
  // lista con los filtros recordados, la temporada guardada tiene que sobrevivir a esta carga.
  useEffect(() => {
    setTemporadas([]);
    if (!cliente) return;
    let vigente = true;
    api<Temporada[]>(`/api/temporadas?cliente=${encodeURIComponent(cliente)}`)
      .then((t) => vigente && setTemporadas(t))
      .catch(() => vigente && setTemporadas([]));
    return () => {
      vigente = false;
    };
  }, [cliente]);

  // Se busca cuando se deja de teclear, no con cada letra.
  useEffect(() => {
    const espera = setTimeout(() => setBusqueda(texto.trim()), 300);
    return () => clearTimeout(espera);
  }, [texto]);

  useEffect(() => {
    const params = new URLSearchParams();
    if (cliente) params.set('cliente', cliente);
    if (etapa) params.set('etapa', etapa);
    if (temporada) params.set('temporada', temporada);
    if (busqueda) params.set('q', busqueda);
    if (sinEtiqueta) params.set('sinEtiqueta', 'true');
    let vigente = true;
    setError(null);
    api<ModeloResumen[]>(`/api/modelos?${params}`)
      .then((lista) => {
        if (!vigente) return;
        setModelos(lista);
        setMarcados((antes) => new Set([...antes].filter((m) => lista.some((x) => x.id === m))));
      })
      .catch((e: unknown) => vigente && setError(e instanceof ErrorApi ? e.message : 'No se han podido cargar los modelos'));
    return () => {
      vigente = false;
    };
  }, [cliente, etapa, temporada, busqueda, sinEtiqueta, version]);

  useEffect(() => {
    try {
      sessionStorage.setItem(CLAVE_FILTROS, JSON.stringify({ cliente, etapa, temporada, texto, sinEtiqueta } satisfies Filtros));
    } catch {
      // Sin almacenamiento (ventana privada, bloqueado) los filtros simplemente no se recuerdan.
    }
  }, [cliente, etapa, temporada, texto, sinEtiqueta]);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  const cambiarCliente = (clave: string) => {
    setCliente(clave);
    setTemporada('');
  };

  const elegidos = (modelos ?? []).filter((m) => marcados.has(m.id));

  const imprimirEtiquetas = () => navegar(`/etiquetas-patrones?ids=${elegidos.map((m) => m.id).join(',')}`);

  const marcar = (modeloId: number) =>
    setMarcados((antes) => {
      const despues = new Set(antes);
      if (despues.has(modeloId)) despues.delete(modeloId);
      else despues.add(modeloId);
      return despues;
    });

  const abrir = (modeloId: number) => {
    setAlta(false);
    navegar(`/modelos/${modeloId}`);
  };

  const darDeAlta = () => setAlta(true);

  const creado = (m: ModeloResumen) => {
    setAlta(false);
    if (cliente && cliente !== m.cliente) cambiarCliente(m.cliente);
    recargar();
    navegar(`/modelos/${m.id}`);
  };

  const volverALista = () => {
    setAlta(false);
    navegar('/modelos');
  };

  const nombreCliente = clientes.find((c) => c.clave === cliente)?.nombre ?? cliente;
  const otrosFiltros = Boolean(etapa || temporada || busqueda || sinEtiqueta);

  const vacio = () => {
    if (otrosFiltros) return <p className="ayuda">Ningún modelo con estos filtros.</p>;
    return (
      <div className="vacio">
        <b>
          {cliente ? `No hay modelos de ${nombreCliente}. Guarda su temporada en` : 'Todavía no hay modelos. Guarda una temporada en'}{' '}
          <a href="/temporadas">/temporadas</a> o da de alta uno.
        </b>
        <button className="boton marca" type="button" onClick={darDeAlta}>
          <Icono nombre="mas" /> Dar de alta un modelo
        </button>
      </div>
    );
  };

  return (
    <div className={elegido !== null || alta ? 'paneles con-detalle' : 'paneles'}>
      <section className="panel-lista" aria-label="Lista de modelos">
        <div className="botones">
          <button className="boton marca" type="button" onClick={darDeAlta}>
            <Icono nombre="mas" /> Dar de alta un modelo
          </button>
          <button
            className="boton"
            type="button"
            disabled={elegidos.length === 0}
            title={elegidos.length === 0 ? 'Elige uno o varios modelos con su casilla' : undefined}
            onClick={imprimirEtiquetas}
          >
            <Icono nombre="imprimir" />
            {elegidos.length === 0 ? 'Imprimir etiquetas de patrones' : `Imprimir etiquetas de patrones (${elegidos.length})`}
          </button>
        </div>

        <div className="filtros">
          <label className="campo">
            Cliente
            <select value={cliente} onChange={(e) => cambiarCliente(e.target.value)}>
              <option value="">Todos los clientes</option>
              {clientes.map((c) => (
                <option key={c.clave} value={c.clave}>
                  {c.nombre}
                </option>
              ))}
            </select>
          </label>
          <label className="campo">
            Temporada
            <select value={temporada} disabled={!cliente} onChange={(e) => setTemporada(e.target.value)}>
              <option value="">{cliente ? 'Todas las temporadas' : 'Elige antes un cliente'}</option>
              {temporadas.map((t) => (
                <option key={t.id} value={String(t.id)}>
                  {t.temporada}
                </option>
              ))}
            </select>
          </label>
          <label className="campo ancho">
            Buscar
            <input type="search" value={texto} placeholder="Referencia o nombre" onChange={(e) => setTexto(e.target.value)} />
          </label>
        </div>
        <div className="chips">
          {CHIPS_ETAPA.map(([clave, txt]) => (
            <button key={clave || 'todas'} className="chip" type="button" aria-pressed={etapa === clave} onClick={() => setEtapa(clave)}>
              {txt}
            </button>
          ))}
          <button className="chip" type="button" aria-pressed={sinEtiqueta} onClick={() => setSinEtiqueta((s) => !s)}>
            Sin etiqueta impresa
          </button>
        </div>

        {marcados.size > 0 && (
          <p className="ayuda">
            {marcados.size === 1 ? '1 modelo elegido' : `${marcados.size} modelos elegidos`}
          </p>
        )}

        {error ? (
          <div className="vacio">
            <b>{error}</b>
            <button className="boton" type="button" onClick={recargar}>
              Reintentar
            </button>
          </div>
        ) : modelos === null ? null : modelos.length === 0 ? (
          vacio()
        ) : (
          <ul className="lista-modelos">
            {modelos.map((m) => (
              <li key={m.id} className="fila-modelo" aria-current={m.id === elegido ? 'true' : undefined}>
                <input type="checkbox" aria-label={`Elegir ${m.referencia}`} checked={marcados.has(m.id)} onChange={() => marcar(m.id)} />
                <button className="abrir" type="button" onClick={() => abrir(m.id)}>
                  <FotoModelo modelo={m} />
                  <span className="texto">
                    <b>{m.referencia}</b>
                    <span>{m.nombre}</span>
                  </span>
                  <Etapa etapa={m.etapa} />
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="panel-detalle" aria-label="Detalle">
        {(elegido !== null || alta) && (
          <button className="enlace volver-lista" type="button" onClick={volverALista}>
            Volver a la lista
          </button>
        )}
        {alta ? (
          <AltaModelo clientes={clientes} clienteInicial={cliente} onCreado={creado} onCancelar={() => setAlta(false)} />
        ) : elegido !== null ? (
          // Con key, cada modelo monta su propia ficha: nada del anterior (estado, diálogo, nombre a medio editar) pasa al nuevo.
          <FichaModelo key={elegido} id={elegido} onCambiado={recargar} />
        ) : (
          <div className="vacio">
            <b>Elige un modelo de la lista</b>
          </div>
        )}
      </section>
    </div>
  );
}
