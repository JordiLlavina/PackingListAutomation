// Una orden de despacho abierta, la de la maqueta: la ficha del modelo, la
// nota de voz, el texto en grande, las fotos (se abren a pantalla completa) y
// los documentos (en otra pestaña, con el visor de la tablet). Las dudas se
// resuelven por teléfono: aquí no se contesta. Abrirla la da por vista. «Hecho» pide dos toques para
// que no se marque sin querer: el primero lo pone en verde y, si no llega el
// segundo en 6 s, se queda como estaba. Aun así se puede tocar sin querer, así
// que una hecha tiene «No está hecha»: vuelve a la Bandeja de Entrada la
// primera, detrás de las urgentes.
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Audio from '../comun/Audio';
import FechaObjetivo from '../comun/FechaObjetivo';
import FotoModelo from '../comun/FotoModelo';
import { Icono } from '../comun/iconos';
import {
  adjuntoUrl,
  estaCerrada,
  fotoDelModelo,
  puesto,
  type AdjuntoVista,
  type MensajeVista,
  type ModificacionDetalle,
  type ModificacionResumen,
} from '../comun/modificaciones';
import { hace } from '../comun/tiempo';
import { toast } from '../comun/toast';
import { useBandeja } from '../comun/useBandeja';
import { useTitulo } from './Shell';

const CONFIRMAR_MS = 6000;


/** Una foto a pantalla completa, con un «Cerrar» grande (Esc también la cierra). */
function FotoCompleta({ adjuntoId, onCerrar }: { adjuntoId: number; onCerrar: () => void }) {
  const dialogo = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = dialogo.current;
    if (d && !d.open) d.showModal();
  }, []);
  return (
    <dialog ref={dialogo} className="foto-completa" onClose={onCerrar} aria-label="Foto">
      <img src={adjuntoUrl(adjuntoId)} alt="Foto de la orden" />
      <button className="boton marca" type="button" onClick={() => dialogo.current?.close()}>
        Cerrar
      </button>
    </dialog>
  );
}

function Miniaturas({ fotos, onAbrir }: { fotos: AdjuntoVista[]; onAbrir: (id: number) => void }) {
  if (fotos.length === 0) return null;
  return (
    <div className="miniaturas">
      {fotos.map((f, i) => (
        <button key={f.id} className="ampliar" type="button" aria-label={`Ver la foto ${i + 1} en grande`} onClick={() => onAbrir(f.id)}>
          <img src={adjuntoUrl(f.id)} alt="" loading="lazy" />
        </button>
      ))}
    </div>
  );
}

function deTipo(m: MensajeVista | undefined, tipo: AdjuntoVista['tipo']): AdjuntoVista[] {
  return m?.adjuntos.filter((a) => a.tipo === tipo) ?? [];
}

export default function Orden() {
  const { id } = useParams();
  const navegar = useNavigate();
  const { recargar } = useBandeja();
  const [detalle, setDetalle] = useState<ModificacionDetalle | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fotoAbierta, setFotoAbierta] = useState<number | null>(null);
  const [confirmando, setConfirmando] = useState(false);
  const [marcando, setMarcando] = useState(false);
  const [errorHecho, setErrorHecho] = useState<string | null>(null);
  const [reabriendo, setReabriendo] = useState(false);
  const reloj = useRef<ReturnType<typeof setTimeout> | null>(null);

  const peticion = useRef(0);
  const cargar = useCallback(() => {
    const esta = ++peticion.current;
    setError(null);
    setDetalle(null);
    api<ModificacionDetalle>(`/api/modificaciones/${id}`)
      .then((d) => {
        if (esta !== peticion.current) return;
        setDetalle(d);
        // Abrirla la da por vista; la pepita y la losa bajan en cuanto contesta.
        if (d.resumen.estado === 'NUEVA') {
          api<ModificacionResumen>(`/api/modificaciones/${id}/vista`, { method: 'POST' })
            .then((r) => {
              if (esta === peticion.current) setDetalle((antes) => (antes ? { ...antes, resumen: r } : antes));
              recargar();
            })
            .catch(() => {
              // Sin red se queda como nueva: se volverá a marcar la próxima vez que se abra.
            });
        }
      })
      .catch((e: unknown) => esta === peticion.current && setError(e instanceof ErrorApi ? e.message : 'No se ha podido cargar la orden'));
  }, [id, recargar]);

  useEffect(cargar, [cargar]);
  // Al salir de la pantalla con «Hecho» a medio confirmar, el reloj ya no tiene nada que deshacer.
  useEffect(
    () => () => {
      if (reloj.current) clearTimeout(reloj.current);
    },
    [],
  );
  const r = detalle?.resumen;
  useTitulo(r ? (r.titulo ?? r.tipoCambioTexto) : null);

  if (error) {
    return (
      <div className="vacio">
        <b>{error}</b>
        <button className="boton" type="button" onClick={cargar}>
          Reintentar
        </button>
      </div>
    );
  }
  if (!detalle || !r) return null;

  const [primero] = detalle.mensajes;
  const audio = deTipo(primero, 'AUDIO')[0];
  const documentos = deTipo(primero, 'DOCUMENTO');

  const hecho = async () => {
    if (marcando) return;
    if (!confirmando) {
      setConfirmando(true);
      setErrorHecho(null);
      reloj.current = setTimeout(() => setConfirmando(false), CONFIRMAR_MS);
      return;
    }
    if (reloj.current) clearTimeout(reloj.current);
    reloj.current = null;
    setMarcando(true);
    try {
      await api<ModificacionResumen>(`/api/modificaciones/${r.id}/hecha`, { method: 'POST' });
      toast('Marcada como hecha');
      recargar();
      navegar('/ordenes');
    } catch (e: unknown) {
      setErrorHecho(e instanceof ErrorApi ? e.message : 'No se ha podido marcar como hecha');
      setMarcando(false);
      setConfirmando(false);
    }
  };

  const noHecha = async () => {
    if (reabriendo) return;
    setReabriendo(true);
    setErrorHecho(null);
    try {
      const resumen = await api<ModificacionResumen>(`/api/modificaciones/${r.id}/no-hecha`, { method: 'POST' });
      setDetalle((antes) => (antes ? { ...antes, resumen } : antes));
      setConfirmando(false);
      setMarcando(false);
      toast(resumen.posicionCola !== null ? `Vuelve a la Bandeja de Entrada, ${puesto(resumen.posicionCola)}` : 'Vuelve a la Bandeja de Entrada');
      recargar();
    } catch (e: unknown) {
      setErrorHecho(e instanceof ErrorApi ? e.message : 'No se ha podido cambiar');
    } finally {
      setReabriendo(false);
    }
  };

  // En otra pestaña, con el visor de la tablet; sin acceso a esta (noopener).
  const abrirDocumento = (doc: AdjuntoVista) => {
    window.open(adjuntoUrl(doc.id), '_blank', 'noopener');
  };

  return (
    <div className="detalle">
      <div className="ficha">
        <FotoModelo modelo={fotoDelModelo(r)} />
        <div className="texto">
          <b>{r.modeloReferencia}</b>
          <span>
            {r.modeloNombre} · {r.cliente}
          </span>
          <span className="meta">
            {hace(r.creadaEn)}
            {r.creadaPor && ` · de ${r.creadaPor}`}
            {!estaCerrada(r.estado) && r.posicionCola !== null && ` · ${puesto(r.posicionCola)} de la cola`}
            {r.urgente && !estaCerrada(r.estado) && (
              <>
                {' '}
                <span className="pill urgente">Urgente</span>
              </>
            )}
            {!estaCerrada(r.estado) && r.fechaObjetivo && (
              <>
                {' '}
                <FechaObjetivo fecha={r.fechaObjetivo} />
              </>
            )}
          </span>
        </div>
      </div>
      {audio && <Audio adjuntoId={audio.id} duracionSeg={audio.duracionSeg ?? 0} />}
      {primero?.texto && <div className="texto-grande">{primero.texto}</div>}
      <Miniaturas fotos={deTipo(primero, 'FOTO')} onAbrir={setFotoAbierta} />
      {documentos.length > 0 && (
        <div className="docs">
          {documentos.map((d) => (
            <button key={d.id} className="doc" type="button" onClick={() => abrirDocumento(d)}>
              <Icono nombre="pdf" />
              <span>
                {d.nombre}
                <small>Toca para abrir · vuelve con «atrás»</small>
              </span>
            </button>
          ))}
        </div>
      )}
      {r.estado === 'HECHA' ? (
        <div>
          <div className="vacio cerrada">
            <Icono nombre="ok" />
            <b>Hecha</b>
          </div>
          <div className="acciones">
            <button className="boton" type="button" disabled={reabriendo} onClick={() => void noHecha()}>
              {reabriendo ? 'Cambiando…' : 'No está hecha'}
            </button>
          </div>
          {errorHecho && (
            <p className="error-envio" role="alert">
              {errorHecho}
            </p>
          )}
          <p className="ayuda">Si la marcaste sin querer: vuelve a la Bandeja de Entrada, la primera detrás de las urgentes.</p>
        </div>
      ) : r.estado === 'DESCARTADA' ? (
        <div className="vacio cerrada">
          <b>Descartada</b>
          {detalle.motivoDescarte && <span>{detalle.motivoDescarte}</span>}
        </div>
      ) : (
        <div>
          <div className="acciones">
            <button className={confirmando ? 'boton ok' : 'boton'} type="button" disabled={marcando} onClick={() => void hecho()}>
              <Icono nombre="check" /> {marcando ? 'Marcando…' : confirmando ? 'Confirmar: está hecho' : 'Hecho'}
            </button>
          </div>
          {errorHecho && (
            <p className="error-envio" role="alert">
              {errorHecho}
            </p>
          )}
          <p className="ayuda">
            {confirmando ? 'Toca otra vez para confirmar. Si no, espera y se queda como estaba.' : '«Hecho» pide dos toques para que no se marque sin querer.'}
          </p>
        </div>
      )}
      {fotoAbierta !== null && <FotoCompleta adjuntoId={fotoAbierta} onCerrar={() => setFotoAbierta(null)} />}
    </div>
  );
}
