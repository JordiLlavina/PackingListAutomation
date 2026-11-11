// Una orden nueva para el taller, en el panel de la derecha de la bandeja:
// el modelo (buscador con foto; viene elegido desde «Nueva orden para este
// modelo»), título, texto, su sitio en la cola del taller (al final, la
// primera o delante de cualquiera), si es urgente y la fecha objetivo
// (opcional), tipo de cambio, nota de voz, fotos y documentos, con la casilla
// «Guardar también en el modelo». Si el modelo ya tiene órdenes abiertas en la
// cola se dice, con un enlace a cada una: así no se manda dos veces lo mismo
// ni se pisa lo que el taller está haciendo.
// Lo que el servidor exige (modelo, título) se dice antes de mandar, con sus
// mismas palabras; lo demás lo contesta él.
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Audio from '../comun/Audio';
import Etapa from '../comun/Etapa';
import FotoModelo from '../comun/FotoModelo';
import { useGrabadora } from '../comun/Grabadora';
import { extensionAudio, type Grabacion } from '../comun/grabador';
import { Icono } from '../comun/iconos';
import { reducirImagen } from '../comun/imagen';
import type { ModeloFicha, ModeloResumen, Subida } from '../comun/modelos';
import { opcionesPosicion, ordenesAbiertasDe, puesto, tituloVisible, type ModificacionCreada, type ModificacionResumen } from '../comun/modificaciones';
import { mmss } from '../comun/tiempo';
import { toast } from '../comun/toast';
import { useTiposCambio } from '../taller/cambio/tipos';
import ElegirImagenesPdf from './ElegirImagenesPdf';

/** Cuántos modelos se enseñan al buscar: con más, mejor afinar la búsqueda. */
const MAXIMO_RESULTADOS = 12;

type Nota = { blob: Blob; duracionSeg: number; url: string };

/** Tras crear, los PDFs guardados en el modelo con imágenes que elegir, de uno en uno, antes de abrir la orden. */
type Eleccion = { orden: ModificacionResumen; modeloId: number; actual: Subida; siguientes: Subida[] };

/** La orden ya creada con algún documento que no se pudo copiar al modelo: se queda a la vista hasta «Continuar». */
type ConAvisos = { orden: ModificacionResumen; modeloId: number; avisos: string[]; porElegir: Subida[] };

type Props = {
  modeloInicial: number | null;
  onCreada: (orden: ModificacionResumen) => void;
  onCancelar: () => void;
};

export default function NuevaOrden({ modeloInicial, onCreada, onCancelar }: Props) {
  const [modelo, setModelo] = useState<ModeloResumen | null>(null);
  const [busca, setBusca] = useState('');
  const [busqueda, setBusqueda] = useState('');
  const [resultados, setResultados] = useState<ModeloResumen[] | null>(null);
  const [titulo, setTitulo] = useState('');
  const [texto, setTexto] = useState('');
  const [cola, setCola] = useState<ModificacionResumen[] | null>(null);
  /** El id de la orden delante de la que va; vacío, al final. */
  const [antesDe, setAntesDe] = useState('');
  const [urgente, setUrgente] = useState(false);
  /** La fecha objetivo (`2026-11-20`); vacía, sin fecha. */
  const [fechaObjetivo, setFechaObjetivo] = useState('');
  /** Las razones del cambio marcadas (claves del yml); puede haber varias. */
  const [razones, setRazones] = useState<string[]>([]);
  const [nota, setNota] = useState<Nota | null>(null);
  const [fotos, setFotos] = useState<File[]>([]);
  const [documentos, setDocumentos] = useState<File[]>([]);
  const [guardarEnModelo, setGuardarEnModelo] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [eleccion, setEleccion] = useState<Eleccion | null>(null);
  const [conAvisos, setConAvisos] = useState<ConAvisos | null>(null);
  const { tipos } = useTiposCambio();

  // El modelo de partida (desde su ficha).
  useEffect(() => {
    if (modeloInicial === null) return;
    let vigente = true;
    api<ModeloFicha>(`/api/modelos/${modeloInicial}`)
      .then((f) => vigente && setModelo(f.modelo))
      .catch(() => vigente && setError('No se ha podido cargar el modelo: búscalo abajo'));
    return () => {
      vigente = false;
    };
  }, [modeloInicial]);

  // La cola, para elegir dónde va la orden nueva.
  useEffect(() => {
    let vigente = true;
    api<ModificacionResumen[]>('/api/modificaciones/cola')
      .then((c) => vigente && setCola(c))
      .catch(() => vigente && setCola([]));
    return () => {
      vigente = false;
    };
  }, []);

  /** Urgente la pone la primera (se puede cambiar después en el desplegable). */
  const marcarUrgente = (marcada: boolean) => {
    setUrgente(marcada);
    const primera = cola?.[0];
    if (marcada && primera) setAntesDe(String(primera.id));
  };

  // Se busca cuando se deja de teclear, no con cada letra.
  useEffect(() => {
    const espera = setTimeout(() => setBusqueda(busca.trim()), 300);
    return () => clearTimeout(espera);
  }, [busca]);

  useEffect(() => {
    if (!busqueda) {
      setResultados(null);
      return;
    }
    let vigente = true;
    api<ModeloResumen[]>(`/api/modelos?q=${encodeURIComponent(busqueda)}`)
      .then((l) => vigente && setResultados(l))
      .catch(() => vigente && setResultados([]));
    return () => {
      vigente = false;
    };
  }, [busqueda]);

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

  const elegir = (m: ModeloResumen) => {
    setModelo(m);
    setBusca('');
    setError(null);
  };

  const crear = async (e: FormEvent) => {
    e.preventDefault();
    if (enviando || grabadora.grabando) return;
    if (!modelo) return setError('Elige el modelo');
    if (!titulo.trim()) return setError('Falta el título');
    setEnviando(true);
    setError(null);
    const datos = new FormData();
    datos.append('modeloId', String(modelo.id));
    datos.append('titulo', titulo.trim());
    datos.append('texto', texto.trim());
    if (antesDe) datos.append('antesDe', antesDe);
    if (urgente) datos.append('urgente', 'true');
    if (fechaObjetivo) datos.append('fechaObjetivo', fechaObjetivo);
    if (razones.length > 0) datos.append('tipoCambio', razones.join(','));
    if (nota) {
      datos.append('audio', nota.blob, `nota-de-voz.${extensionAudio(nota.blob.type)}`);
      datos.append('audioDuracionSeg', String(nota.duracionSeg));
    }
    for (const f of fotos) datos.append('fotos', await reducirImagen(f), f.name);
    for (const d of documentos) datos.append('documentos', d, d.name);
    if (documentos.length > 0) datos.append('guardarEnModelo', String(guardarEnModelo));
    try {
      const creada = await api<ModificacionCreada>('/api/modificaciones', { method: 'POST', body: datos });
      toast('Orden enviada al taller');
      const porElegir = creada.documentosGuardados.filter((s) => s.loteId !== null && s.candidatas.length > 0);
      // La orden ya está creada; lo que no se pudo copiar al modelo se dice en el propio formulario, una línea
      // por documento, y se queda a la vista hasta que el despacho lo ha leído (un toast se va solo).
      if (creada.avisos.length > 0) {
        setConAvisos({ orden: creada.modificacion, modeloId: modelo.id, avisos: creada.avisos, porElegir });
      } else continuar(creada.modificacion, modelo.id, porElegir);
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se ha podido crear la orden');
      setEnviando(false);
    }
  };

  // Con la orden creada: elegir las imágenes de los PDFs guardados en el modelo, si hay, y después abrirla.
  const continuar = (orden: ModificacionResumen, modeloId: number, porElegir: Subida[]) => {
    setConAvisos(null);
    const [primero, ...siguientes] = porElegir;
    if (primero === undefined) onCreada(orden);
    else setEleccion({ orden, modeloId, actual: primero, siguientes });
  };

  // Un PDF resuelto (con fotos elegidas o sin ellas): el siguiente, y al acabar, la orden.
  const siguienteDocumento = () => {
    if (!eleccion) return;
    const [siguiente, ...resto] = eleccion.siguientes;
    if (siguiente === undefined) {
      setEleccion(null);
      onCreada(eleccion.orden);
    } else setEleccion({ ...eleccion, actual: siguiente, siguientes: resto });
  };

  const nombres = (ficheros: File[]) => ficheros.map((f) => f.name).join(', ');
  const yaAbiertas = modelo && cola ? ordenesAbiertasDe(cola, modelo.id) : [];

  return (
    <>
    <form className="formulario nueva-orden" onSubmit={(e) => void crear(e)}>
      <h2>Nueva orden para el taller</h2>

      <div className="campo">
        Modelo
        {modelo ? (
          <div className="modelo-elegido">
            <FotoModelo modelo={modelo} />
            <span className="texto">
              <b>{modelo.referencia}</b>
              <span>
                {modelo.nombre} · {modelo.cliente}
              </span>
            </span>
            <button className="chip" type="button" onClick={() => setModelo(null)}>
              Cambiar
            </button>
          </div>
        ) : (
          <div className="buscador-modelos">
            <input type="search" aria-label="Buscar el modelo" value={busca} placeholder="Referencia o nombre del modelo" onChange={(e) => setBusca(e.target.value)} />
            {resultados !== null &&
              (resultados.length === 0 ? (
                <p className="ayuda">Ningún modelo con «{busqueda}».</p>
              ) : (
                <ul className="lista-modelos">
                  {resultados.slice(0, MAXIMO_RESULTADOS).map((m) => (
                    <li key={m.id} className="fila-modelo">
                      <button className="abrir" type="button" onClick={() => elegir(m)}>
                        <FotoModelo modelo={m} />
                        <span className="texto">
                          <b>{m.referencia}</b>
                          <span>
                            {m.nombre} · {m.cliente}
                          </span>
                        </span>
                        <Etapa etapa={m.etapa} />
                      </button>
                    </li>
                  ))}
                </ul>
              ))}
            {resultados !== null && resultados.length > MAXIMO_RESULTADOS && (
              <p className="ayuda">Hay {resultados.length} modelos: escribe algo más para afinar.</p>
            )}
          </div>
        )}
      </div>

      {yaAbiertas.length > 0 && (
        <p className="aviso-orden-existente" role="status">
          <b>{yaAbiertas.length === 1 ? 'Este modelo ya tiene una orden abierta' : `Este modelo ya tiene ${yaAbiertas.length} órdenes abiertas`}</b> en la cola del taller:
          {yaAbiertas.map((o) => (
            <Link key={o.id} to={`/bandeja/${o.id}?pestana=despacho`}>
              {o.posicionCola !== null && `${puesto(o.posicionCola)} · `}
              {tituloVisible(o)}
            </Link>
          ))}
        </p>
      )}

      <label className="campo">
        Título
        <input value={titulo} maxLength={200} placeholder="Qué hay que cambiar, en pocas palabras" onChange={(e) => setTitulo(e.target.value)} />
      </label>
      <label className="campo">
        Texto
        <textarea rows={4} value={texto} onChange={(e) => setTexto(e.target.value)} />
      </label>

      <fieldset className="lugar-cola">
        <legend>Lugar en la cola del taller</legend>
        <select aria-label="Lugar en la cola" value={antesDe} disabled={cola === null} onChange={(e) => setAntesDe(e.target.value)}>
          {(cola === null ? [{ valor: '', texto: 'Cargando la cola…' }] : opcionesPosicion(cola)).map((o) => (
            <option key={o.valor} value={o.valor}>
              {o.texto}
            </option>
          ))}
        </select>
        <label className="casilla-linea">
          <input type="checkbox" checked={urgente} onChange={(e) => marcarUrgente(e.target.checked)} />
          Urgente <span className="ayuda">(la pone la primera; puedes cambiarlo arriba)</span>
        </label>
        <label className="campo fecha-objetivo-campo">
          Fecha objetivo (opcional)
          <input type="date" value={fechaObjetivo} onChange={(e) => setFechaObjetivo(e.target.value)} />
          <span className="ayuda">El taller la ve como una etiqueta que se va poniendo roja al acercarse el día.</span>
        </label>
      </fieldset>

      <div className="campo">
        Razón del cambio (opcional; puedes marcar varias)
        <div className="chips razones-chips">
          {(tipos ?? []).map((t) => (
            <button
              key={t.clave}
              className="chip"
              type="button"
              aria-pressed={razones.includes(t.clave)}
              onClick={() => setRazones(razones.includes(t.clave) ? razones.filter((r) => r !== t.clave) : [...razones, t.clave])}
            >
              {t.texto}
            </button>
          ))}
        </div>
      </div>

      <div className="campo">
        Nota de voz (opcional)
        <div className="voz">
          <button className={grabadora.grabando ? 'boton grabando' : 'boton'} type="button" disabled={grabadora.iniciando || enviando} onClick={() => void grabadora.alternar()}>
            <Icono nombre={grabadora.grabando ? 'pausa' : 'mic'} />
            {grabadora.grabando ? `Parar · ${mmss(grabadora.seg)}` : nota ? 'Grabar otra nota' : 'Grabar nota de voz'}
          </button>
          {nota && !grabadora.grabando && (
            <>
              <Audio key={nota.url} compacto src={nota.url} duracionSeg={nota.duracionSeg} />
              <button className="chip" type="button" onClick={() => ponerNota(null)}>
                Quitar la nota
              </button>
            </>
          )}
        </div>
        {grabadora.sinMicro && <p className="ayuda">No puedo usar el micrófono: la orden puede ir sin nota.</p>}
      </div>

      <div className="campo">
        Fotos (opcional)
        <div className="ficheros">
          <label className="boton subir">
            <Icono nombre="cam" /> Elegir fotos
            <input type="file" multiple accept="image/*" onChange={(e) => setFotos([...(e.target.files ?? [])])} />
          </label>
          {fotos.length > 0 && <span title={nombres(fotos)}>{fotos.length === 1 ? '1 foto elegida' : `${fotos.length} fotos elegidas`}</span>}
        </div>
      </div>

      <div className="campo">
        Documentos (opcional)
        <div className="ficheros">
          <label className="boton subir">
            <Icono nombre="pdf" /> Elegir documentos
            <input type="file" multiple accept=".pdf,application/pdf,.doc,.docx,.xls,.xlsx,.odt,.ods,.txt" onChange={(e) => setDocumentos([...(e.target.files ?? [])])} />
          </label>
          {documentos.length > 0 && <span>{nombres(documentos)}</span>}
        </div>
        <label className="casilla-linea">
          <input type="checkbox" checked={guardarEnModelo} disabled={documentos.length === 0} onChange={(e) => setGuardarEnModelo(e.target.checked)} />
          Guardar también en el modelo
        </label>
      </div>

      {conAvisos ? (
        <div className="resultado-orden" role="status">
          <p>
            <b>Orden enviada al taller.</b> Lo que no se ha podido guardar en el modelo (súbelo desde su ficha):
          </p>
          <ul className="avisos-orden">
            {conAvisos.avisos.map((aviso) => (
              <li key={aviso}>{aviso}</li>
            ))}
          </ul>
          <div className="botones">
            <button className="boton ok" type="button" onClick={() => continuar(conAvisos.orden, conAvisos.modeloId, conAvisos.porElegir)}>
              Continuar
            </button>
          </div>
        </div>
      ) : (
        <div className="botones">
          <button className="boton ok" type="submit" disabled={enviando || grabadora.grabando}>
            <Icono nombre="check" /> {enviando ? 'Enviando…' : 'Crear orden'}
          </button>
          <button className="boton" type="button" disabled={enviando} onClick={onCancelar}>
            Cancelar
          </button>
        </div>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </form>
    {eleccion && (
      <ElegirImagenesPdf key={eleccion.actual.documento.id} modeloId={eleccion.modeloId} subida={eleccion.actual} onGuardadas={() => undefined} onCerrar={siguienteDocumento} />
    )}
    </>
  );
}
