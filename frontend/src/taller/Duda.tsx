// «Tengo una duda» sobre una orden y «Contestar a despacho» en un aviso: la
// misma pantalla con otra cabecera. Se graba una nota de voz con la grabadora
// de «He cambiado algo» y se manda como un mensaje más de la modificación
// (POST /mensajes), que deja la pelota en el otro lado. Si el envío falla, la
// nota se queda y sale un botón grande de volver a intentar, como en Revisar.
import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Audio from '../comun/Audio';
import Grabadora, { useGrabadora } from '../comun/Grabadora';
import { extensionAudio, type Grabacion } from '../comun/grabador';
import { Icono } from '../comun/iconos';
import type { ModificacionDetalle } from '../comun/modificaciones';
import { toast } from '../comun/toast';
import { useBandeja } from '../comun/useBandeja';

type Modo = 'duda' | 'contestar';

type Nota = { blob: Blob; duracionSeg: number; url: string };

const TEXTOS: Record<Modo, { listo: string; enviar: string }> = {
  duda: { listo: '¿Mandamos la duda a despacho?', enviar: 'Enviar la duda' },
  contestar: { listo: '¿Mandamos la respuesta a despacho?', enviar: 'Enviar la respuesta' },
};

export default function Duda({ modo }: { modo: Modo }) {
  const { id } = useParams();
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const { recargar } = useBandeja();
  const [detalle, setDetalle] = useState<ModificacionDetalle | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [nota, setNota] = useState<Nota | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // La URL blob: de la nota se suelta al sustituirla y al salir de la pantalla.
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
  const { grabando, sinMicro } = grabadora;

  const cargar = useCallback(() => {
    setErrorCarga(null);
    api<ModificacionDetalle>(`/api/modificaciones/${id}`)
      .then(setDetalle)
      .catch((e: unknown) => setErrorCarga(e instanceof ErrorApi ? e.message : 'No se ha podido cargar'));
  }, [id]);
  useEffect(cargar, [cargar]);

  if (errorCarga) {
    return (
      <div className="vacio">
        <b>{errorCarga}</b>
        <button className="boton" type="button" onClick={cargar}>
          Reintentar
        </button>
      </div>
    );
  }
  if (!detalle) return null;

  const r = detalle.resumen;
  const sobre = r.titulo ?? `${r.tipoCambioTexto ?? 'Cambio'} · ${r.modeloReferencia}`;
  const listo = !grabando && nota !== null;
  const textos = TEXTOS[modo];
  const pregunta = grabando
    ? 'Te escucho… toca para parar'
    : listo
      ? textos.listo
      : modo === 'duda'
        ? `Graba tu duda sobre «${sobre}»`
        : `Graba tu respuesta sobre «${sobre}»`;
  const destino = modo === 'duda' ? `/ordenes/${r.id}` : '/avisos';

  const enviar = async () => {
    if (!nota || enviando) return;
    setEnviando(true);
    setError(null);
    const datos = new FormData();
    datos.append('audio', nota.blob, `nota-de-voz.${extensionAudio(nota.blob.type)}`);
    datos.append('audioDuracionSeg', String(nota.duracionSeg));
    try {
      await api<unknown>(`/api/modificaciones/${r.id}/mensajes`, { method: 'POST', body: datos });
      toast(modo === 'duda' ? `Duda enviada a ${r.creadaPor ?? 'despacho'}` : 'Respuesta enviada');
      recargar();
      // De vuelta a donde se venía; si se entró aquí directamente, a la orden o a los avisos.
      if (ubicacion.key !== 'default') navegar(-1);
      else navegar(destino, { replace: true });
    } catch (e: unknown) {
      setError(e instanceof ErrorApi ? e.message : 'No se ha podido enviar');
      setEnviando(false);
    }
  };

  // Otra nota desde cero: el error del envío anterior ya no es de esta.
  const repetir = () => {
    ponerNota(null);
    setError(null);
  };

  const textoEnviar = enviando ? 'Enviando…' : null;

  return (
    <>
      <h2 className="pregunta">{pregunta}</h2>
      <Grabadora estado={grabadora} grabadaSeg={nota?.duracionSeg ?? null} />
      {listo && nota && <Audio key={nota.url} src={nota.url} duracionSeg={nota.duracionSeg} />}
      <div className="acciones">
        {error && (
          <>
            <button className="boton marca ancho" type="button" disabled={enviando} onClick={() => void enviar()}>
              {textoEnviar ?? 'Volver a intentar'}
            </button>
            <p className="error-envio ancho" role="alert">
              {error}
            </p>
          </>
        )}
        <button className="boton" type="button" disabled={!listo || enviando} onClick={repetir}>
          Repetir
        </button>
        {!error && (
          <button className="boton ok" type="button" disabled={!listo || enviando} onClick={() => void enviar()}>
            <Icono nombre="check" /> {textoEnviar ?? textos.enviar}
          </button>
        )}
      </div>
      {sinMicro && <p className="ayuda">No puedo usar el micrófono: sin él no se puede mandar la nota.</p>}
    </>
  );
}
