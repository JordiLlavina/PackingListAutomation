// El detalle de una modificación en despacho, en el panel de la derecha de la
// bandeja: la cabecera (foto, modelo, tipo, estado, quién y cuándo), el hilo
// de mensajes con sus notas de voz (y lo que se transcribió), fotos y
// documentos, y al lado el panel de acciones: en un aviso del taller la
// prioridad, los destinos donde hay que aplicarlo, «Marcar hecha» y
// «Descartar»; en una orden, su prioridad y en qué punto la tiene el taller.
// Debajo del hilo, «Responder». Abrir un aviso NUEVO lo da por visto. Tras
// cada acción se vuelve a pedir el detalle y se avisa a la bandeja (lista y
// números). Mientras alguna nota de voz se está transcribiendo, el detalle se
// vuelve a pedir solo cada pocos segundos; una que falló se puede reintentar,
// y el título que puso la IA se corrige con el lápiz.
import { useCallback, useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Audio from '../comun/Audio';
import Etapa from '../comun/Etapa';
import FotoModelo from '../comun/FotoModelo';
import { useGrabadora } from '../comun/Grabadora';
import { extensionAudio, type Grabacion } from '../comun/grabador';
import { Icono } from '../comun/iconos';
import { reducirImagen } from '../comun/imagen';
import type { ModeloFicha, ModeloResumen, ResumenFoto } from '../comun/modelos';
import {
  adjuntoUrl,
  destinosEditables,
  estaCerrada,
  estadoModificacion,
  fotoDelModelo,
  hayTranscripcionPendiente,
  tituloEditable,
  type AdjuntoVista,
  type DestinoEstado,
  type MensajeVista,
  type ModificacionDetalle,
  type Prioridad,
  type Rol,
} from '../comun/modificaciones';
import { hace, mmss } from '../comun/tiempo';
import { toast } from '../comun/toast';

const PRIORIDADES: [Prioridad, string][] = [
  ['ALTA', 'Alta'],
  ['NORMAL', 'Normal'],
  ['BAJA', 'Baja'],
];

const ROL_TEXTO: Record<Rol, string> = { TALLER: 'taller', DESPACHO: 'despacho', ADMIN: 'admin' };

function mensaje(e: unknown, porDefecto: string): string {
  return e instanceof ErrorApi ? e.message : porDefecto;
}

function tamano(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}

type Reintento = { reintentando: number | null; onReintentar: (adjuntoId: number) => void };

/**
 * Lo que se sabe de la transcripción de una nota de voz, debajo de su
 * reproductor. Si falló el resumen de la IA, la transcripción sigue HECHA y el
 * motivo sale aparte.
 */
function Transcripcion({ audio, reintentando, onReintentar }: { audio: AdjuntoVista } & Reintento) {
  let estado = null;
  switch (audio.estadoTranscripcion) {
    case 'HECHA':
      estado = (
        <>
          {audio.transcripcion ? <p className="transcripcion">{audio.transcripcion}</p> : <span className="etiqueta">no se ha oído nada</span>}
          {audio.error && <span className="etiqueta">{audio.error}</span>}
        </>
      );
      break;
    case 'NO_APLICA':
      estado = <span className="etiqueta">sin transcripción (servicio no configurado)</span>;
      break;
    case 'PENDIENTE':
      estado = <span className="etiqueta">transcribiendo…</span>;
      break;
    case 'FALLIDA':
      estado = (
        <span className="estado-transcripcion">
          <span className="etiqueta fallo">falló: {audio.error ?? 'sin motivo'}</span>
          <button className="chip" type="button" disabled={reintentando !== null} onClick={() => onReintentar(audio.id)}>
            {reintentando === audio.id ? 'Reintentando…' : 'Reintentar'}
          </button>
        </span>
      );
      break;
  }
  return (
    <>
      {estado}
      {audio.resumenIa && (
        <div className="resumen-ia">
          <span className="rotulo">
            <span className="chip-ia">IA</span>Resumen de la IA
          </span>
          <span className="texto-resumen">{audio.resumenIa}</span>
        </div>
      )}
    </>
  );
}

function Mensaje({
  m,
  pasando,
  onAGaleria,
  reintentando,
  onReintentar,
}: { m: MensajeVista; pasando: number | null; onAGaleria: (id: number) => void } & Reintento) {
  const audios = m.adjuntos.filter((a) => a.tipo === 'AUDIO');
  const fotos = m.adjuntos.filter((a) => a.tipo === 'FOTO');
  const documentos = m.adjuntos.filter((a) => a.tipo === 'DOCUMENTO');
  return (
    <article className={m.rolAutor === 'TALLER' ? 'mensaje de-taller' : 'mensaje'}>
      <span className="quien">
        {m.autor ?? 'Sin autor'}
        {m.rolAutor && ` · ${ROL_TEXTO[m.rolAutor]}`} · {hace(m.creadoEn)}
      </span>
      {m.texto && <p>{m.texto}</p>}
      {audios.map((a) => (
        <div key={a.id} className="nota-voz">
          <Audio compacto adjuntoId={a.id} duracionSeg={a.duracionSeg ?? 0} />
          <Transcripcion audio={a} reintentando={reintentando} onReintentar={onReintentar} />
        </div>
      ))}
      {fotos.length > 0 && (
        <div className="miniaturas">
          {fotos.map((f, i) => (
            <figure key={f.id}>
              <a href={adjuntoUrl(f.id)} target="_blank" rel="noopener" aria-label={`Abrir la foto ${i + 1} en otra pestaña`}>
                <img src={adjuntoUrl(f.id)} alt="" loading="lazy" />
              </a>
              <button className="chip" type="button" disabled={pasando !== null} onClick={() => onAGaleria(f.id)}>
                {pasando === f.id ? 'Guardando…' : 'Pasar a la galería del modelo'}
              </button>
            </figure>
          ))}
        </div>
      )}
      {documentos.length > 0 && (
        <div className="docs">
          {documentos.map((d) => (
            <a key={d.id} className="doc" href={adjuntoUrl(d.id)} target="_blank" rel="noopener">
              <Icono nombre="pdf" />
              <span>
                {d.nombre}
                <small>{tamano(d.tamano)} · se abre en otra pestaña</small>
              </span>
            </a>
          ))}
        </div>
      )}
    </article>
  );
}

type Nota = { blob: Blob; duracionSeg: number; url: string };

/** Responder: texto y, si se quiere, una nota de voz y fotos. Hace falta texto o nota (o una foto, como el servidor). */
function Responder({ id, onEnviado }: { id: number; onEnviado: () => Promise<void> }) {
  const [texto, setTexto] = useState('');
  const [nota, setNota] = useState<Nota | null>(null);
  const [fotos, setFotos] = useState<File[]>([]);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Cambiar la key vacía el selector de fotos tras enviar.
  const [vueltaFotos, setVueltaFotos] = useState(0);

  // La URL blob: de la nota se suelta al sustituirla y al salir.
  const notaActual = useRef<Nota | null>(null);
  const ponerNota = useCallback((nueva: Nota | null) => {
    if (notaActual.current) URL.revokeObjectURL(notaActual.current.url);
    notaActual.current = nueva;
    setNota(nueva);
  }, []);
  useEffect(
    () => () => {
      if (notaActual.current) URL.revokeObjectURL(notaActual.current.url);
    },
    [],
  );
  const grabadora = useGrabadora((g: Grabacion) => ponerNota({ blob: g.blob, duracionSeg: g.duracionSeg, url: URL.createObjectURL(g.blob) }));

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    if (enviando || grabadora.grabando) return;
    const limpio = texto.trim();
    if (!limpio && !nota && fotos.length === 0) {
      setError('Escribe algo o graba una nota');
      return;
    }
    setEnviando(true);
    setError(null);
    const datos = new FormData();
    if (limpio) datos.append('texto', limpio);
    if (nota) {
      datos.append('audio', nota.blob, `nota-de-voz.${extensionAudio(nota.blob.type)}`);
      datos.append('audioDuracionSeg', String(nota.duracionSeg));
    }
    for (const f of fotos) datos.append('fotos', await reducirImagen(f), f.name);
    try {
      await api<unknown>(`/api/modificaciones/${id}/mensajes`, { method: 'POST', body: datos });
      setTexto('');
      ponerNota(null);
      setFotos([]);
      setVueltaFotos((v) => v + 1);
      toast('Respuesta enviada');
      await onEnviado();
    } catch (err) {
      setError(mensaje(err, 'No se ha podido enviar la respuesta'));
    } finally {
      setEnviando(false);
    }
  };

  const elegirFotos = (e: ChangeEvent<HTMLInputElement>) => setFotos([...(e.target.files ?? [])]);

  return (
    <form className="responder" onSubmit={(e) => void enviar(e)}>
      <h3>Responder</h3>
      <label className="campo">
        Texto
        <textarea rows={3} value={texto} onChange={(e) => setTexto(e.target.value)} />
      </label>
      <div className="adjuntar">
        <button className={grabadora.grabando ? 'boton grabando' : 'boton'} type="button" disabled={grabadora.iniciando || enviando} onClick={() => void grabadora.alternar()}>
          <Icono nombre={grabadora.grabando ? 'pausa' : 'mic'} />
          {grabadora.grabando ? `Parar · ${mmss(grabadora.seg)}` : nota ? 'Grabar otra nota' : 'Grabar nota de voz'}
        </button>
        <label className="boton subir">
          <Icono nombre="cam" /> Fotos
          <input key={vueltaFotos} type="file" multiple accept="image/*" onChange={elegirFotos} />
        </label>
        {fotos.length > 0 && <span className="ficheros">{fotos.length === 1 ? '1 foto elegida' : `${fotos.length} fotos elegidas`}</span>}
      </div>
      {nota && !grabadora.grabando && (
        <div className="voz">
          <Audio key={nota.url} compacto src={nota.url} duracionSeg={nota.duracionSeg} />
          <button className="chip" type="button" onClick={() => ponerNota(null)}>
            Quitar la nota
          </button>
        </div>
      )}
      {grabadora.sinMicro && <p className="ayuda">No puedo usar el micrófono: responde por escrito.</p>}
      <div className="botones">
        <button className="boton ok" type="submit" disabled={enviando || grabadora.grabando}>
          {enviando ? 'Enviando…' : 'Enviar respuesta'}
        </button>
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}

export default function Modificacion({ id, onCambiado }: { id: number; onCambiado: () => void }) {
  const [detalle, setDetalle] = useState<ModificacionDetalle | null>(null);
  const [modelo, setModelo] = useState<ModeloResumen | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [pasando, setPasando] = useState<number | null>(null);
  const dialogo = useRef<HTMLDialogElement>(null);
  const [motivo, setMotivo] = useState('');
  const [errorDescarte, setErrorDescarte] = useState<string | null>(null);
  const [reintentando, setReintentando] = useState<number | null>(null);
  // El título mientras se corrige; null = no se está corrigiendo.
  const [titulo, setTitulo] = useState<string | null>(null);
  const [errorTitulo, setErrorTitulo] = useState<string | null>(null);

  // La última versión del aviso a la bandeja, sin que cada pintado del padre vuelva a pedir el detalle.
  const avisar = useRef(onCambiado);
  useEffect(() => {
    avisar.current = onCambiado;
  }, [onCambiado]);
  // Abrir un aviso nuevo lo da por visto una sola vez, aunque se recargue.
  const vistaPedida = useRef(false);

  // Solo vale la respuesta de la última petición: si llega tarde la de otra
  // modificación (o una recarga vieja), se tira.
  const peticion = useRef(0);
  const cargar = useCallback(() => {
    const esta = ++peticion.current;
    setError(null);
    return api<ModificacionDetalle>(`/api/modificaciones/${id}`)
      .then((d) => {
        if (esta !== peticion.current) return;
        setDetalle(d);
        const r = d.resumen;
        if (r.estado === 'NUEVA' && r.direccion === 'TALLER_A_DESPACHO' && !vistaPedida.current) {
          vistaPedida.current = true;
          api(`/api/modificaciones/${id}/vista`, { method: 'POST' })
            .then(() => {
              avisar.current();
              return cargar();
            })
            .catch(() => {
              // Sin red se queda como nueva: se volverá a marcar la próxima vez que se abra.
              vistaPedida.current = false;
            });
        }
      })
      .catch((e: unknown) => {
        if (esta === peticion.current) setError(mensaje(e, 'No se ha podido cargar'));
      });
  }, [id]);

  useEffect(() => {
    void cargar();
    return () => {
      peticion.current++;
    };
  }, [cargar]);

  // La etapa y el tipo (para la silueta) son del modelo, que el resumen no trae.
  const modeloId = detalle?.resumen.modeloId;
  useEffect(() => {
    if (modeloId === undefined) return;
    let vigente = true;
    api<ModeloFicha>(`/api/modelos/${modeloId}`)
      .then((f) => vigente && setModelo(f.modelo))
      .catch(() => {
        // Sin el modelo se pinta igual, sin etapa.
      });
    return () => {
      vigente = false;
    };
  }, [modeloId]);

  // Mientras alguna nota de voz se está transcribiendo, el detalle se vuelve a
  // pedir cada pocos segundos; cada respuesta nueva arma la siguiente espera.
  const transcribiendo = detalle !== null && hayTranscripcionPendiente(detalle);
  useEffect(() => {
    if (!transcribiendo) return;
    const espera = window.setTimeout(() => void cargar(), 4000);
    return () => window.clearTimeout(espera);
  }, [transcribiendo, detalle, cargar]);

  const trasCambiar = async () => {
    await cargar();
    avisar.current();
  };

  const accion = async (ruta: string, cuerpo: unknown, porDefecto: string, aviso?: string): Promise<boolean> => {
    setOcupado(true);
    setErrorAccion(null);
    try {
      await api(`/api/modificaciones/${id}${ruta}`, { method: 'POST', body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) });
      if (aviso) toast(aviso);
      await trasCambiar();
      return true;
    } catch (e) {
      setErrorAccion(mensaje(e, porDefecto));
      return false;
    } finally {
      setOcupado(false);
    }
  };

  if (error) {
    return (
      <div className="vacio">
        <b>{error}</b>
        <button className="boton" type="button" onClick={() => void cargar()}>
          Reintentar
        </button>
      </div>
    );
  }
  if (!detalle) return null;
  const r = detalle.resumen;
  const esAviso = r.direccion === 'TALLER_A_DESPACHO';
  const cerrada = estaCerrada(r.estado);
  const estado = estadoModificacion(r.estado);

  const aGaleria = async (adjuntoId: number) => {
    setPasando(adjuntoId);
    setErrorAccion(null);
    try {
      await api<ResumenFoto[]>(`/api/adjuntos/${adjuntoId}/a-galeria`, { method: 'POST' });
      toast('Foto guardada en el modelo');
      // Si el modelo no tenía foto, esta pasa a ser la principal: la cabecera y la fila cambian.
      setModelo(null);
      await trasCambiar();
    } catch (e) {
      setErrorAccion(mensaje(e, 'No se ha podido pasar la foto al modelo'));
    } finally {
      setPasando(null);
    }
  };

  const reintentar = async (adjuntoId: number) => {
    setReintentando(adjuntoId);
    setErrorAccion(null);
    try {
      await api(`/api/adjuntos/${adjuntoId}/retranscribir`, { method: 'POST' });
      // Vuelve como «transcribiendo…», y la recarga sola sigue hasta que acabe.
      await cargar();
    } catch (e) {
      setErrorAccion(mensaje(e, 'No se ha podido volver a transcribir la nota'));
    } finally {
      setReintentando(null);
    }
  };

  const guardarTitulo = async (e: FormEvent) => {
    e.preventDefault();
    const limpio = (titulo ?? '').trim();
    if (!limpio) {
      setErrorTitulo('Falta el título');
      return;
    }
    setOcupado(true);
    setErrorTitulo(null);
    try {
      await api(`/api/modificaciones/${id}/titulo`, { method: 'PATCH', body: JSON.stringify({ titulo: limpio }) });
      setTitulo(null);
      toast('Título guardado');
      await trasCambiar();
    } catch (err) {
      setErrorTitulo(mensaje(err, 'No se ha podido guardar el título'));
    } finally {
      setOcupado(false);
    }
  };

  const marcar = (d: DestinoEstado, aplica: boolean, hecho: boolean) =>
    void accion(`/destinos/${d.destino}`, { aplica, hecho }, 'No se ha podido marcar el destino');

  const abrirDescarte = () => {
    setMotivo('');
    setErrorDescarte(null);
    dialogo.current?.showModal();
  };

  const descartar = async (e: FormEvent) => {
    e.preventDefault();
    if (!motivo.trim()) {
      setErrorDescarte('Hace falta el motivo');
      return;
    }
    setOcupado(true);
    setErrorDescarte(null);
    try {
      await api(`/api/modificaciones/${id}/descartar`, { method: 'POST', body: JSON.stringify({ motivo: motivo.trim() }) });
      dialogo.current?.close();
      toast('Descartada');
      await trasCambiar();
    } catch (err) {
      setErrorDescarte(mensaje(err, 'No se ha podido descartar'));
    } finally {
      setOcupado(false);
    }
  };

  const fotoModelo = modelo ?? fotoDelModelo(r);

  return (
    <div className="detalle">
      <div className="ficha cabecera-modificacion">
        <FotoModelo modelo={fotoModelo} />
        <div className="texto">
          {titulo === null ? (
            <h2>
              {r.titulo ?? 'Sin título'}
              {r.tituloDeIa && (
                <span className="chip-ia" title="Título escrito por la IA">
                  IA
                </span>
              )}
              {tituloEditable(r) && (
                <button
                  className="editar-titulo"
                  type="button"
                  aria-label={r.titulo ? 'Corregir el título' : 'Poner un título'}
                  title={r.titulo ? 'Corregir el título' : 'Poner un título'}
                  onClick={() => {
                    setErrorTitulo(null);
                    setTitulo(r.titulo ?? '');
                  }}
                >
                  <Icono nombre="lapiz" />
                </button>
              )}
            </h2>
          ) : (
            <form className="editar-nombre" onSubmit={(e) => void guardarTitulo(e)}>
              <input aria-label="Título" value={titulo} maxLength={200} autoFocus onChange={(e) => setTitulo(e.target.value)} />
              <button className="boton ok" type="submit" disabled={ocupado}>
                Guardar
              </button>
              <button className="boton" type="button" onClick={() => setTitulo(null)}>
                Cancelar
              </button>
            </form>
          )}
          {errorTitulo && (
            <p className="error" role="alert">
              {errorTitulo}
            </p>
          )}
          <span>
            <b>{r.modeloReferencia}</b> · {r.modeloNombre} · {r.cliente}
          </span>
          <span className="meta">
            {modelo && <Etapa etapa={modelo.etapa} />}
            {r.tipoCambioTexto && <span className="etiqueta">{r.tipoCambioTexto}</span>}
            <span className={`pill ${estado.pill}`}>{estado.texto}</span>
            <span>
              {hace(r.creadaEn)}
              {r.creadaPor && ` · de ${r.creadaPor}`}
            </span>
            <Link to={`/modelos/${r.modeloId}`}>Ver modelo</Link>
          </span>
        </div>
      </div>

      <div className="cuerpo-modificacion">
        <div className="hilo">
          {detalle.mensajes.map((m) => (
            <Mensaje
              key={m.id}
              m={m}
              pasando={pasando}
              onAGaleria={(adj) => void aGaleria(adj)}
              reintentando={reintentando}
              onReintentar={(adj) => void reintentar(adj)}
            />
          ))}
          <Responder id={r.id} onEnviado={trasCambiar} />
        </div>

        <aside className="acciones-detalle" aria-label="Acciones">
          {r.respuestaPendienteDe === 'DESPACHO' && <p className="espera-respuesta">El taller espera tu respuesta</p>}
          {r.respuestaPendienteDe === 'TALLER' && <p className="ayuda">Esperando la respuesta del taller.</p>}

          {r.estado === 'HECHA' && (
            <div className="estado-cerrada">
              <b>
                <Icono nombre="ok" /> Hecha
              </b>
              {r.hechaEn && (
                <span className="meta">
                  {r.hechaPor ? `por ${r.hechaPor} · ` : ''}
                  {hace(r.hechaEn)}
                </span>
              )}
            </div>
          )}
          {r.estado === 'DESCARTADA' && (
            <div className="estado-cerrada">
              <b>Descartada</b>
              {detalle.motivoDescarte && <span>{detalle.motivoDescarte}</span>}
            </div>
          )}

          {/* La prioridad se cambia en avisos y órdenes mientras no estén cerradas; en una orden cerrada solo se dice. */}
          {(esAviso || !cerrada) && (
            <fieldset>
              <legend>Prioridad</legend>
              <div className="prioridades">
                {PRIORIDADES.map(([p, texto]) => (
                  <button
                    key={p}
                    className="prioridad"
                    type="button"
                    data-p={p}
                    aria-pressed={r.prioridad === p}
                    disabled={cerrada || ocupado}
                    onClick={() => void accion('/prioridad', { prioridad: p }, 'No se ha podido poner la prioridad')}
                  >
                    {texto}
                  </button>
                ))}
              </div>
              {r.prioridad === null && !cerrada && <p className="ayuda">Todavía sin prioridad.</p>}
            </fieldset>
          )}

          {esAviso ? (
            <>
              <fieldset>
                <legend>Aplicar en</legend>
                {r.destinos.map((d) => (
                  <div key={d.destino} className="destino">
                    <span className="nombre">{d.texto}</span>
                    <label className="casilla">
                      <input
                        type="checkbox"
                        checked={d.hechoEn !== null}
                        disabled={!d.aplica || !destinosEditables(r.estado) || ocupado}
                        onChange={(e) => marcar(d, true, e.target.checked)}
                      />
                      Aplicado
                    </label>
                    <button
                      className="chip"
                      type="button"
                      aria-pressed={!d.aplica}
                      disabled={!destinosEditables(r.estado) || ocupado}
                      onClick={() => marcar(d, !d.aplica, false)}
                    >
                      No aplica
                    </button>
                    {d.hechoEn && (
                      <span className="meta">
                        {d.hechoPor ? `por ${d.hechoPor} · ` : ''}
                        {hace(d.hechoEn)}
                      </span>
                    )}
                  </div>
                ))}
              </fieldset>
            </>
          ) : (
            <fieldset>
              <legend>En el taller</legend>
              {cerrada && r.prioridad && <span>Prioridad {PRIORIDADES.find(([p]) => p === r.prioridad)?.[1].toLowerCase()}</span>}
              <span className="meta">{r.vistaEn ? `La vio ${hace(r.vistaEn)}` : 'Todavía no la ha abierto'}</span>
            </fieldset>
          )}

          {!cerrada && (
            <div className="botones">
              <button className="boton ok" type="button" disabled={ocupado} onClick={() => void accion('/hecha', undefined, 'No se ha podido marcar como hecha', 'Marcada como hecha')}>
                <Icono nombre="check" /> Marcar hecha
              </button>
              <button className="boton peligro" type="button" disabled={ocupado} onClick={abrirDescarte}>
                Descartar
              </button>
            </div>
          )}
          {errorAccion && (
            <p className="error" role="alert">
              {errorAccion}
            </p>
          )}
        </aside>
      </div>

      <dialog ref={dialogo} className="dialogo" aria-labelledby={`titulo-descartar-${id}`}>
        <form onSubmit={(e) => void descartar(e)}>
          <h2 id={`titulo-descartar-${id}`}>Descartar «{r.titulo ?? r.tipoCambioTexto ?? r.modeloReferencia}»</h2>
          <label className="campo">
            Motivo
            <textarea rows={3} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
          </label>
          {errorDescarte && <p className="error">{errorDescarte}</p>}
          <div className="botones">
            <button className="boton peligro" type="submit" disabled={ocupado}>
              Descartar
            </button>
            <button className="boton" type="button" onClick={() => dialogo.current?.close()}>
              Cancelar
            </button>
          </div>
        </form>
      </dialog>
    </div>
  );
}
