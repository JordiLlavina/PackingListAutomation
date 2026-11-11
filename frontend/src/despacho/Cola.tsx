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
import { useCallback, useEffect, useState, type DragEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Etapa from '../comun/Etapa';
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

  return (
    <section className="pantalla-cola" aria-label="Cola del taller">
      <div className="cabecera-cola">
        <div>
          <h2>Cola del taller</h2>
          <p className="ayuda">La primera es la siguiente que hará el taller. Arrastra una orden sobre otra, o usa los botones, para cambiar el orden.</p>
        </div>
        <button className="boton marca" type="button" onClick={() => navegar('/bandeja/nueva?pestana=despacho')}>
          <Icono nombre="mas" /> Nueva orden
        </button>
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
      ) : (
        <ol className="lista-cola" aria-busy={guardando}>
          {enPantalla.map((m) => {
            const estado = estadoModificacion(m.estado);
            const n = m.posicionCola ?? 0;
            const total = cola?.length ?? 0;
            return (
              <li
                key={m.id}
                className={`fila-cola${arrastrada === m.id ? ' arrastrando' : ''}${encima === m.id && arrastrada !== m.id ? ' encima' : ''}`}
                data-urgente={m.urgente ? 'true' : undefined}
                draggable={sePuedeOrdenar}
                onDragStart={(e) => empezar(e, m.id)}
                onDragOver={(e) => sobre(e, m.id)}
                onDrop={(e) => soltar(e, m.id)}
                onDragEnd={acabar}
              >
                <span className="puesto-cola" aria-label={`${puesto(n)} de la cola`}>
                  {puesto(n)}
                </span>
                {sePuedeOrdenar && (
                  <span className="asa" aria-hidden="true" title="Arrastra para cambiar el orden">
                    ⋮⋮
                  </span>
                )}
                <FotoModelo modelo={fotoDelModelo(m)} />
                <span className="cuerpo">
                  <Link className="tit" to={`/bandeja/${m.id}?pestana=despacho`} draggable={false}>
                    {tituloVisible(m)}
                  </Link>
                  <span className="meta">
                    <b>{m.modeloReferencia}</b> · {m.modeloNombre} · {m.cliente} · {hace(m.creadaEn)}
                  </span>
                  <span className="etiquetas">
                    {m.etapa && <Etapa etapa={m.etapa} />}
                    <span className={`pill ${estado.pill}`}>{estado.texto}</span>
                    {m.urgente && <span className="pill urgente">Urgente</span>}
                    {m.respuestaPendienteDe === 'DESPACHO' && <span className="etiqueta espera">Te toca responder</span>}
                  </span>
                </span>
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
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
