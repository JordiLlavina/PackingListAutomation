// La cola del taller en despacho (/app/cola): las órdenes para el taller que
// siguen abiertas, una detrás de otra; la primera es la siguiente que hará el
// taller. Se reordena arrastrando una fila sobre otra o con los botones
// (primera, adelantar, atrasar, última), y una orden se marca urgente, que la
// sube la primera. Se puede ver de la primera a la última o al revés.
//
// Filtros como los de los modelos (buscador, cliente y etapa). Con cualquiera
// puesto la vista es parcial y el orden NO se deja cambiar (decisión del
// usuario): mover una orden entre las que se ven la colocaría respecto a otras
// que no se ven. Cada cambio manda la cola entera al servidor; si mientras
// tanto alguien ha creado o cerrado una orden contesta 409 y se vuelve a cargar.
//
// Dos estilos, con el interruptor de al lado de «Nueva orden»: la lista de
// siempre (una tarjeta por orden) y una tabla, más densa, con una columna por
// dato. Los dos ordenan igual (arrastrar y los mismos botones); la elección se
// recuerda en el navegador (localStorage): es solo una comodidad de quien mira.
import { useCallback, useEffect, useState, type DragEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Etapa from '../comun/Etapa';
import FechaObjetivo from '../comun/FechaObjetivo';
import FotoModelo from '../comun/FotoModelo';
import { Icono } from '../comun/iconos';
import type { Cliente, Etapa as TipoEtapa } from '../comun/modelos';
import {
  colaFiltrada,
  estadoModificacion,
  filtrarCola,
  fotoDelModelo,
  moverEnCola,
  puesto,
  soltarSobre,
  tituloVisible,
  type FiltroCola,
  type ModificacionResumen,
  type Movimiento,
} from '../comun/modificaciones';
import { hace } from '../comun/tiempo';
import { toast } from '../comun/toast';
import { useBandeja } from '../comun/useBandeja';

const CHIPS_ETAPA: [TipoEtapa | '', string][] = [
  ['', 'Todas'],
  ['PROTOTIPO', 'Prototipos'],
  ['SMS', 'Muestras'],
  ['PRODUCCION', 'Producción'],
  ['INACTIVO', 'Inactivos'],
];

const SIN_FILTROS: FiltroCola = { texto: '', cliente: '', etapa: '' };

type Estilo = 'lista' | 'tabla';
const CLAVE_ESTILO = 'modelos.cola.estilo';

function estiloGuardado(): Estilo {
  try {
    return localStorage.getItem(CLAVE_ESTILO) === 'tabla' ? 'tabla' : 'lista';
  } catch {
    return 'lista';
  }
}

function guardarEstilo(estilo: Estilo) {
  try {
    localStorage.setItem(CLAVE_ESTILO, estilo);
  } catch {
    // Sin almacenamiento (ventana privada, datos bloqueados) solo dura mientras se está en la pantalla.
  }
}

function mensaje(e: unknown, porDefecto: string): string {
  return e instanceof ErrorApi ? e.message : porDefecto;
}

export default function Cola() {
  const navegar = useNavigate();
  const { resumen, recargar: recargarBandeja } = useBandeja();
  const [cola, setCola] = useState<ModificacionResumen[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [filtro, setFiltro] = useState<FiltroCola>(SIN_FILTROS);
  const [alReves, setAlReves] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [arrastrada, setArrastrada] = useState<number | null>(null);
  const [encima, setEncima] = useState<number | null>(null);
  const [estilo, setEstilo] = useState<Estilo>(estiloGuardado);

  const cambiarEstilo = () => {
    const nuevo: Estilo = estilo === 'tabla' ? 'lista' : 'tabla';
    setEstilo(nuevo);
    guardarEstilo(nuevo);
  };

  const cargar = useCallback(() => {
    api<ModificacionResumen[]>('/api/modificaciones/cola')
      .then((c) => {
        setCola(c);
        setError(null);
      })
      .catch((e: unknown) => setError(mensaje(e, 'No se ha podido cargar la cola')));
  }, []);

  // Al entrar y cuando cambian los números de la bandeja (una orden nueva, una hecha, una urgente).
  const cambio = resumen ? `${resumen.pendientes}|${resumen.urgentes}|${resumen.nuevas}` : '';
  useEffect(cargar, [cargar, cambio]);

  useEffect(() => {
    api<Cliente[]>('/api/clientes')
      .then(setClientes)
      .catch(() => setClientes([]));
  }, []);

  const filtrada = colaFiltrada(filtro);
  const sePuedeOrdenar = !filtrada && !guardando;

  /** Manda la cola entera en su orden nuevo; lo enseña ya y, si el servidor no lo acepta, vuelve a cargar. */
  const guardarOrden = async (ids: number[]) => {
    if (!cola) return;
    const porId = new Map(cola.map((m) => [m.id, m]));
    setCola(ids.map((id, i) => ({ ...porId.get(id)!, posicionCola: i + 1 })));
    setGuardando(true);
    try {
      const nueva = await api<ModificacionResumen[]>('/api/modificaciones/cola', { method: 'PUT', body: JSON.stringify({ ids }) });
      setCola(nueva);
      setError(null);
    } catch (e) {
      toast(mensaje(e, 'No se ha podido guardar el orden'));
      cargar();
    } finally {
      setGuardando(false);
    }
  };

  const mover = (id: number, movimiento: Movimiento) => {
    if (!cola || !sePuedeOrdenar) return;
    const ids = cola.map((m) => m.id);
    const nueva = moverEnCola(ids, id, movimiento);
    if (nueva !== ids) void guardarOrden(nueva);
  };

  const alternarUrgente = async (m: ModificacionResumen) => {
    setGuardando(true);
    try {
      await api(`/api/modificaciones/${m.id}/urgente`, { method: 'POST', body: JSON.stringify({ urgente: !m.urgente }) });
      toast(m.urgente ? 'Ya no es urgente' : 'Urgente: ahora es la primera de la cola');
      cargar();
      recargarBandeja();
    } catch (e) {
      toast(mensaje(e, 'No se ha podido cambiar la marca de urgente'));
    } finally {
      setGuardando(false);
    }
  };

  // Arrastrar y soltar: se suelta encima de otra fila y la arrastrada se queda en su sitio.
  const empezar = (e: DragEvent, id: number) => {
    if (!sePuedeOrdenar) return e.preventDefault();
    setArrastrada(id);
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', String(id));
  };
  const sobre = (e: DragEvent, id: number) => {
    if (arrastrada === null) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    if (encima !== id) setEncima(id);
  };
  const soltar = (e: DragEvent, id: number) => {
    e.preventDefault();
    const origen = arrastrada;
    setArrastrada(null);
    setEncima(null);
    if (origen === null || !cola || !sePuedeOrdenar) return;
    const ids = cola.map((m) => m.id);
    const nueva = soltarSobre(ids, origen, id);
    if (nueva !== ids) void guardarOrden(nueva);
  };
  const acabar = () => {
    setArrastrada(null);
    setEncima(null);
  };

  const visibles = cola ? filtrarCola(cola, filtro) : null;
  const enPantalla = visibles && alReves ? [...visibles].reverse() : visibles;
  // Las flechas siguen a la pantalla: con la vista al revés, adelantar es bajar.
  const flechaAdelantar = alReves ? '▼' : '▲';
  const flechaAtrasar = alReves ? '▲' : '▼';
  const total = cola?.length ?? 0;

  /** Lo que hace arrastrable una fila: igual en la lista (`<li>`) y en la tabla (`<tr>`). */
  const propsFila = (m: ModificacionResumen) => ({
    className: `fila-cola${arrastrada === m.id ? ' arrastrando' : ''}${encima === m.id && arrastrada !== m.id ? ' encima' : ''}`,
    'data-urgente': m.urgente ? 'true' : undefined,
    draggable: sePuedeOrdenar,
    onDragStart: (e: DragEvent) => empezar(e, m.id),
    onDragOver: (e: DragEvent) => sobre(e, m.id),
    onDrop: (e: DragEvent) => soltar(e, m.id),
    onDragEnd: acabar,
  });

  /** El puesto en la cola y, si se puede reordenar, el asa de arrastrar. */
  const asa = sePuedeOrdenar && (
    <span className="asa" aria-hidden="true" title="Arrastra para cambiar el orden">
      ⋮⋮
    </span>
  );
  const numero = (n: number) => (
    <span className="puesto-cola" aria-label={`${puesto(n)} de la cola`}>
      {puesto(n)}
    </span>
  );

  /** Los botones de mover y de urgente de una orden, los mismos en los dos estilos. */
  const mandos = (m: ModificacionResumen) => {
    const n = m.posicionCola ?? 0;
    return (
      <span className="mandos-cola">
        <button type="button" className="chip" disabled={!sePuedeOrdenar || n === 1} title="Ponerla la primera" onClick={() => mover(m.id, 'primera')}>
          Primera
        </button>
        <button type="button" className="chip flecha" disabled={!sePuedeOrdenar || n === 1} title="Adelantarla un puesto" aria-label="Adelantarla un puesto" onClick={() => mover(m.id, 'arriba')}>
          {flechaAdelantar}
        </button>
        <button type="button" className="chip flecha" disabled={!sePuedeOrdenar || n === total} title="Atrasarla un puesto" aria-label="Atrasarla un puesto" onClick={() => mover(m.id, 'abajo')}>
          {flechaAtrasar}
        </button>
        <button type="button" className="chip" disabled={!sePuedeOrdenar || n === total} title="Ponerla la última" onClick={() => mover(m.id, 'ultima')}>
          Última
        </button>
        <button type="button" className="chip urgente-chip" aria-pressed={m.urgente} disabled={guardando} onClick={() => void alternarUrgente(m)}>
          {m.urgente ? 'Urgente · quitar' : 'Marcar urgente'}
        </button>
      </span>
    );
  };

  const titulo = (m: ModificacionResumen) => (
    <Link className="tit" to={`/bandeja/${m.id}?pestana=despacho`} draggable={false}>
      {tituloVisible(m)}
    </Link>
  );
  const pastillasEstado = (m: ModificacionResumen) => {
    const estado = estadoModificacion(m.estado);
    return (
      <>
        <span className={`pill ${estado.pill}`}>{estado.texto}</span>
        {m.urgente && <span className="pill urgente">Urgente</span>}
      </>
    );
  };

  return (
    <section className="pantalla-cola" aria-label="Lista de prioridades">
      <div className="cabecera-cola">
        <div>
          <h2>Lista de prioridades</h2>
        </div>
        <div className="acciones-cabecera-cola">
          <button
            className="interruptor-estilo"
            type="button"
            role="switch"
            aria-checked={estilo === 'tabla'}
            title={estilo === 'tabla' ? 'Ver la cola como lista' : 'Ver la cola como tabla'}
            onClick={cambiarEstilo}
          >
            <span className="interruptor-etiqueta">{estilo === 'tabla' ? 'Estilo Tabla' : 'Estilo Lista'}</span>
            <span className="interruptor-pista" aria-hidden="true" />
          </button>
          <button className="boton marca" type="button" onClick={() => navegar('/bandeja/nueva?pestana=despacho')}>
            <Icono nombre="mas" /> Nueva orden
          </button>
        </div>
      </div>

      <div className="filtros">
        <label className="campo">
          Cliente
          <select value={filtro.cliente} onChange={(e) => setFiltro({ ...filtro, cliente: e.target.value })}>
            <option value="">Todos los clientes</option>
            {clientes.map((c) => (
              <option key={c.clave} value={c.clave}>
                {c.nombre}
              </option>
            ))}
          </select>
        </label>
        <label className="campo">
          Buscar
          <input type="search" value={filtro.texto} placeholder="Referencia, nombre o título" onChange={(e) => setFiltro({ ...filtro, texto: e.target.value })} />
        </label>
      </div>
      <div className="chips">
        {CHIPS_ETAPA.map(([clave, txt]) => (
          <button key={clave || 'todas'} className="chip" type="button" aria-pressed={filtro.etapa === clave} onClick={() => setFiltro({ ...filtro, etapa: clave })}>
            {txt}
          </button>
        ))}
        <button className="chip sentido-cola" type="button" onClick={() => setAlReves(!alReves)} title="Cambia el sentido de la lista; no cambia el orden de la cola">
          {alReves ? '▲ De la última a la primera' : '▼ De la primera a la última'}
        </button>
      </div>

      {filtrada && (
        <p className="aviso-cola" role="status">
          Con filtros solo se ve una parte de la cola, así que no se puede cambiar el orden.
          <button className="enlace" type="button" onClick={() => setFiltro(SIN_FILTROS)}>
            Quitar los filtros
          </button>
        </p>
      )}

      {error && !cola ? (
        <div className="vacio">
          <b>{error}</b>
          <button className="boton" type="button" onClick={cargar}>
            Reintentar
          </button>
        </div>
      ) : enPantalla === null ? null : cola?.length === 0 ? (
        <div className="vacio">
          <Icono nombre="ok" />
          <b>El taller no tiene ninguna orden pendiente</b>
        </div>
      ) : enPantalla.length === 0 ? (
        <p className="ayuda">Ninguna orden con estos filtros.</p>
      ) : estilo === 'tabla' ? (
        <div className="marco-tabla">
          <table className="tabla-cola" aria-busy={guardando}>
            <thead>
              <tr>
                <th className="col-puesto">Nº</th>
                <th className="col-foto">
                  <span className="solo-lector">Foto</span>
                </th>
                <th>Orden</th>
                <th>Referencia</th>
                <th>Modelo</th>
                <th>Cliente</th>
                <th>Etapa</th>
                <th>Estado</th>
                <th>Fecha</th>
                <th>Creada</th>
                <th className="col-mandos">
                  <span className="solo-lector">Mover en la cola</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {enPantalla.map((m) => (
                <tr key={m.id} {...propsFila(m)}>
                  <td className="col-puesto">
                    {asa}
                    {numero(m.posicionCola ?? 0)}
                  </td>
                  <td className="col-foto">
                    <FotoModelo modelo={fotoDelModelo(m)} />
                  </td>
                  <td className="col-titulo">{titulo(m)}</td>
                  <td className="col-ref">
                    <b>{m.modeloReferencia}</b>
                  </td>
                  <td>{m.modeloNombre}</td>
                  <td>{m.cliente}</td>
                  <td>{m.etapa && <Etapa etapa={m.etapa} />}</td>
                  <td>
                    <span className="pastillas-cola">{pastillasEstado(m)}</span>
                  </td>
                  <td>
                    <FechaObjetivo fecha={m.fechaObjetivo} />
                  </td>
                  <td className="col-creada">{hace(m.creadaEn)}</td>
                  <td className="col-mandos">{mandos(m)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <ol className="lista-cola" aria-busy={guardando}>
          {enPantalla.map((m) => (
            <li key={m.id} {...propsFila(m)}>
              {numero(m.posicionCola ?? 0)}
              {asa}
              <FotoModelo modelo={fotoDelModelo(m)} />
              <span className="cuerpo">
                {titulo(m)}
                <span className="meta">
                  <b>{m.modeloReferencia}</b> · {m.modeloNombre} · {m.cliente} · {hace(m.creadaEn)}
                </span>
                <span className="etiquetas">
                  {m.etapa && <Etapa etapa={m.etapa} />}
                  {pastillasEstado(m)}
                  <FechaObjetivo fecha={m.fechaObjetivo} />
                </span>
              </span>
              {mandos(m)}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
