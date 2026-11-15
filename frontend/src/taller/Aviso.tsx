// Un aviso del taller, abierto desde «Mis avisos»: lo que se mandó (modelo,
// razones, fotos, nota de voz). No hay respuestas: las dudas van por
// teléfono. Mientras
// despacho no haya empezado a aplicarlo (enviado o visto) se puede corregir
// —marcar otras razones, quitar o añadir fotos, grabar otra nota de voz— y
// borrar (petición del usuario, noviembre de 2026). Después solo se mira, y la
// pantalla dice por qué. Nada se guarda hasta «Guardar los cambios»; borrar
// pide dos toques, como «Hecho» en una orden.
import { useCallback, useEffect, useRef, useState, type ChangeEvent } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Audio from '../comun/Audio';
import FotoModelo from '../comun/FotoModelo';
import Grabadora, { useGrabadora } from '../comun/Grabadora';
import { extensionAudio, type Grabacion } from '../comun/grabador';
import { Icono } from '../comun/iconos';
import { reducirImagen } from '../comun/imagen';
import {
  adjuntoUrl,
  avisoEditable,
  estadoAviso,
  fotoDelModelo,
  motivoNoEditable,
  type AdjuntoVista,
  type MensajeVista,
  type ModificacionDetalle,
} from '../comun/modificaciones';
import { hace } from '../comun/tiempo';
import { toast } from '../comun/toast';
import { useBandeja } from '../comun/useBandeja';
import { useTitulo } from './Shell';
import { useTiposCambio } from './cambio/tipos';

const CONFIRMAR_MS = 6000;

type FotoNueva = { blob: Blob; url: string };
type Nota = { blob: Blob; duracionSeg: number; url: string };

function deTipo(m: MensajeVista | undefined, tipo: AdjuntoVista['tipo']): AdjuntoVista[] {
  return m?.adjuntos.filter((a) => a.tipo === tipo) ?? [];
}

export default function Aviso() {
  const { id } = useParams();
  const navegar = useNavigate();
  const { recargar } = useBandeja();
  const { tipos } = useTiposCambio();
  const [detalle, setDetalle] = useState<ModificacionDetalle | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Lo que se está cambiando; null en las razones = no se ha tocado.
  const [razones, setRazones] = useState<string[] | null>(null);
  const [quitar, setQuitar] = useState<number[]>([]);
  const [fotosNuevas, setFotosNuevas] = useState<FotoNueva[]>([]);
  const [nota, setNota] = useState<Nota | null>(null);
  const [quitarNota, setQuitarNota] = useState(false);
  const [preparando, setPreparando] = useState(0);
  const [guardando, setGuardando] = useState(false);
  const [confirmandoBorrar, setConfirmandoBorrar] = useState(false);
  const [borrando, setBorrando] = useState(false);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const reloj = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Las URLs blob de las fotos nuevas y de la nota se sueltan al quitarlas y al salir.
  const notaActual = useRef<Nota | null>(null);
  const ponerNota = useCallback((nueva: Nota | null) => {
    if (notaActual.current) URL.revokeObjectURL(notaActual.current.url);
    notaActual.current = nueva;
    setNota(nueva);
  }, []);
  const fotosActuales = useRef<FotoNueva[]>([]);
  useEffect(() => {
    fotosActuales.current = fotosNuevas;
  }, [fotosNuevas]);
  useEffect(
    () => () => {
      if (notaActual.current) URL.revokeObjectURL(notaActual.current.url);
      fotosActuales.current.forEach((f) => URL.revokeObjectURL(f.url));
      if (reloj.current) clearTimeout(reloj.current);
    },
    [],
  );
  const grabadora = useGrabadora((g: Grabacion) => {
    ponerNota({ blob: g.blob, duracionSeg: g.duracionSeg, url: URL.createObjectURL(g.blob) });
    setQuitarNota(false);
  });

  const peticion = useRef(0);
  const cargar = useCallback(() => {
    const esta = ++peticion.current;
    setError(null);
    api<ModificacionDetalle>(`/api/modificaciones/${id}`)
      .then((d) => esta === peticion.current && setDetalle(d))
      .catch((e: unknown) => esta === peticion.current && setError(e instanceof ErrorApi ? e.message : 'No se ha podido cargar el aviso'));
  }, [id]);
  useEffect(cargar, [cargar]);

  const r = detalle?.resumen;
  useTitulo(r ? `${r.modeloReferencia} · ${r.modeloNombre}` : null);

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

  const [cuerpo] = detalle.mensajes;
  const audioActual = deTipo(cuerpo, 'AUDIO')[0];
  const fotosActualesDelAviso = deTipo(cuerpo, 'FOTO').filter((f) => !quitar.includes(f.id));
  const editable = avisoEditable(r.estado);
  const estado = estadoAviso(r.estado);
  const marcadas = razones ?? (r.tipoCambio ? r.tipoCambio.split(',') : []);
  const notaQueQueda = nota !== null || (audioActual !== undefined && !quitarNota);
  const fotosQueQuedan = fotosActualesDelAviso.length + fotosNuevas.length;
  const hayCambios = razones !== null || quitar.length > 0 || fotosNuevas.length > 0 || nota !== null || quitarNota;
  const sePuedeGuardar = hayCambios && marcadas.length > 0 && (notaQueQueda || fotosQueQuedan > 0) && !grabadora.grabando && preparando === 0;

  const alternarRazon = (clave: string) => {
    setRazones(marcadas.includes(clave) ? marcadas.filter((c) => c !== clave) : [...marcadas, clave]);
  };

  const alElegirFotos = async (e: ChangeEvent<HTMLInputElement>) => {
    const ficheros = [...(e.target.files ?? [])];
    e.target.value = '';
    if (ficheros.length === 0) return;
    setPreparando((n) => n + ficheros.length);
    try {
      const reducidas = await Promise.all(ficheros.map((f) => reducirImagen(f)));
      setFotosNuevas((antes) => [...antes, ...reducidas.map((blob) => ({ blob, url: URL.createObjectURL(blob) }))]);
    } finally {
      setPreparando((n) => n - ficheros.length);
    }
  };

  const quitarFotoNueva = (indice: number) => {
    setFotosNuevas((antes) => {
      const foto = antes[indice];
      if (foto) URL.revokeObjectURL(foto.url);
      return antes.filter((_, i) => i !== indice);
    });
  };

  const guardar = async () => {
    if (!sePuedeGuardar || guardando) return;
    setGuardando(true);
    setErrorAccion(null);
    const datos = new FormData();
    if (razones !== null) datos.append('tipoCambio', marcadas.join(','));
    for (const q of quitar) datos.append('quitar', String(q));
    if (quitarNota && audioActual && nota === null) datos.append('quitar', String(audioActual.id));
    if (nota) {
      datos.append('audio', nota.blob, `nota-de-voz.${extensionAudio(nota.blob.type)}`);
      datos.append('audioDuracionSeg', String(nota.duracionSeg));
    }
    fotosNuevas.forEach((f, i) => datos.append('fotos', f.blob, `foto-${i + 1}.jpg`));
    try {
      await api(`/api/modificaciones/${r.id}/aviso`, { method: 'POST', body: datos });
      toast('Aviso corregido: despacho lo verá como nuevo');
      recargar();
      navegar('/avisos');
    } catch (e: unknown) {
      setErrorAccion(e instanceof ErrorApi ? e.message : 'No se ha podido guardar');
      setGuardando(false);
    }
  };

  const borrar = async () => {
    if (borrando) return;
    if (!confirmandoBorrar) {
      setConfirmandoBorrar(true);
      setErrorAccion(null);
      reloj.current = setTimeout(() => setConfirmandoBorrar(false), CONFIRMAR_MS);
      return;
    }
    if (reloj.current) clearTimeout(reloj.current);
    reloj.current = null;
    setBorrando(true);
    try {
      await api(`/api/modificaciones/${r.id}`, { method: 'DELETE' });
      toast('Aviso borrado');
      recargar();
      navegar('/avisos', { replace: true });
    } catch (e: unknown) {
      setErrorAccion(e instanceof ErrorApi ? e.message : 'No se ha podido borrar');
      setBorrando(false);
      setConfirmandoBorrar(false);
    }
  };

  const ocupado = guardando || borrando;

  return (
    <div className="detalle editar-aviso">
      <div className="ficha">
        <FotoModelo modelo={fotoDelModelo(r)} />
        <div className="texto">
          <b>{r.modeloReferencia}</b>
          <span>
            {r.modeloNombre} · {r.cliente}
          </span>
          <span className="meta">
            <span className={`pill ${estado.pill}`}>{estado.texto}</span> {hace(r.creadaEn)}
          </span>
        </div>
      </div>

      {!editable && <p className="aviso-fijo">{motivoNoEditable(r.estado)}</p>}

      <section className="bloque-aviso">
        <h3>{editable ? '¿Qué cambiaste? Puedes marcar varias.' : 'Qué cambió'}</h3>
        <div className="chips razones-chips">
          {(tipos ?? []).map((t) => (
            <button key={t.clave} className="chip" type="button" aria-pressed={marcadas.includes(t.clave)} disabled={!editable || ocupado} onClick={() => alternarRazon(t.clave)}>
              <Icono nombre={t.icono} /> {t.texto}
            </button>
          ))}
        </div>
      </section>

      <section className="bloque-aviso">
        <h3>Fotos</h3>
        {fotosActualesDelAviso.length + fotosNuevas.length === 0 && <p className="ayuda">Sin fotos.</p>}
        <div className="miniaturas">
          {fotosActualesDelAviso.map((f, i) => (
            <figure key={f.id} className="con-quitar">
              <img src={adjuntoUrl(f.id)} alt={`Foto ${i + 1} del aviso`} />
              {editable && (
                <button className="quitar-foto" type="button" aria-label={`Quitar la foto ${i + 1}`} disabled={ocupado} onClick={() => setQuitar([...quitar, f.id])}>
                  Quitar
                </button>
              )}
            </figure>
          ))}
          {fotosNuevas.map((f, i) => (
            <figure key={f.url} className="con-quitar nueva">
              <img src={f.url} alt={`Foto nueva ${i + 1}`} />
              <button className="quitar-foto" type="button" aria-label={`Quitar la foto nueva ${i + 1}`} disabled={ocupado} onClick={() => quitarFotoNueva(i)}>
                Quitar
              </button>
            </figure>
          ))}
        </div>
        {editable && (
          <label className="camara pequena">
            <Icono nombre="cam" />
            <span>
              <b>Hacer otra foto</b>
            </span>
            <input type="file" accept="image/*" capture="environment" multiple disabled={ocupado} onChange={(e) => void alElegirFotos(e)} />
          </label>
        )}
        {preparando > 0 && <p className="ayuda">Preparando {preparando === 1 ? 'la foto' : `${preparando} fotos`}…</p>}
        {quitar.length > 0 && (
          <button className="enlace" type="button" onClick={() => setQuitar([])}>
            Recuperar las {quitar.length === 1 ? 'foto quitada' : `${quitar.length} fotos quitadas`}
          </button>
        )}
      </section>

      <section className="bloque-aviso">
        <h3>Nota de voz</h3>
        {/* A la derecha de la nota, el botón que la tira, la quita o la recupera, con un icono por cada caso. */}
        <div className="nota-con-boton">
          {nota ? (
            <Audio key={nota.url} src={nota.url} duracionSeg={nota.duracionSeg} />
          ) : audioActual && !quitarNota ? (
            <Audio adjuntoId={audioActual.id} duracionSeg={audioActual.duracionSeg ?? 0} />
          ) : (
            <p className="ayuda">{quitarNota ? 'La nota se quitará al guardar.' : 'Sin nota de voz.'}</p>
          )}
          {editable && nota && (
            <button className="boton" type="button" disabled={ocupado || grabadora.grabando} onClick={() => ponerNota(null)}>
              <Icono nombre="papelera" /> Tirar la nota nueva
            </button>
          )}
          {editable && !nota && audioActual && (
            <button className="boton" type="button" disabled={ocupado || grabadora.grabando} onClick={() => setQuitarNota(!quitarNota)}>
              <Icono nombre={quitarNota ? 'deshacer' : 'sinMic'} /> {quitarNota ? 'Dejar la nota que había' : 'Quitar la nota'}
            </button>
          )}
        </div>
        {nota && <p className="ayuda">Nota nueva: sustituirá a la anterior al guardar.</p>}
        {editable && (
          <>
            <Grabadora estado={grabadora} grabadaSeg={null} pequena />
            {grabadora.sinMicro && <p className="ayuda">No puedo usar el micrófono: puedes dejar la nota que había o solo fotos.</p>}
          </>
        )}
      </section>

      {editable && (
        <div>
          <div className="acciones">
            <button className={confirmandoBorrar ? 'boton peligro confirmar' : 'boton peligro'} type="button" disabled={ocupado} onClick={() => void borrar()}>
              {borrando ? 'Borrando…' : confirmandoBorrar ? 'Confirmar: borrar el aviso' : 'Borrar el aviso'}
            </button>
            <button className="boton ok" type="button" disabled={!sePuedeGuardar || ocupado} onClick={() => void guardar()}>
              <Icono nombre="check" /> {guardando ? 'Guardando…' : 'Guardar los cambios'}
            </button>
          </div>
          {errorAccion && (
            <p className="error-envio" role="alert">
              {errorAccion}
            </p>
          )}
          {confirmandoBorrar ? (
            <p className="ayuda">Toca otra vez para borrarlo del todo. Si no, espera y se queda.</p>
          ) : (
            hayCambios &&
            !sePuedeGuardar && <p className="ayuda">{marcadas.length === 0 ? 'Marca al menos una razón.' : 'Hace falta una foto o una nota de voz.'}</p>
          )}
        </div>
      )}
    </div>
  );
}
