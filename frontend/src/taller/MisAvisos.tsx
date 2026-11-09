// «Mis avisos»: lo que el taller ha mandado a despacho y en qué punto está
// (enviado, visto, hecho), el último primero. Si despacho ha contestado y le
// toca al taller, la respuesta sale dentro de la tarjeta con su nota de voz y
// un botón para contestar.
//
// Son todos los avisos del taller y no solo los de quien mira: hay una sola
// cuenta de taller (la tablet), así que la API ya los da todos.
//
// La lista no trae los mensajes. Para no pedir el detalle de cada tarjeta,
// solo se pide el de los avisos con una respuesta de despacho pendiente
// (respuestaPendienteDe = TALLER), que son pocos: en esos el último mensaje
// es de despacho, y es el que se enseña. Por lo mismo, de la nota de voz del
// aviso solo se sabe si la hay, no cuánto dura.
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Audio from '../comun/Audio';
import FotoModelo from '../comun/FotoModelo';
import {
  estadoAviso,
  fotoDelModelo,
  ordenarAvisos,
  type MensajeVista,
  type ModificacionDetalle,
  type ModificacionResumen,
} from '../comun/modificaciones';
import { hace } from '../comun/tiempo';
import { useBandeja } from '../comun/useBandeja';

/** El último mensaje de cada aviso que espera respuesta del taller, si es de despacho. */
type Respuestas = Record<number, MensajeVista>;

async function respuestasDe(avisos: ModificacionResumen[]): Promise<Respuestas> {
  const esperando = avisos.filter((a) => a.respuestaPendienteDe === 'TALLER');
  const detalles = await Promise.allSettled(esperando.map((a) => api<ModificacionDetalle>(`/api/modificaciones/${a.id}`)));
  const respuestas: Respuestas = {};
  for (const d of detalles) {
    if (d.status !== 'fulfilled') continue; // Sin respuesta en la tarjeta; la próxima vuelta lo reintenta.
    const ultimo = d.value.mensajes.at(-1);
    if (ultimo && ultimo.rolAutor !== 'TALLER') respuestas[d.value.resumen.id] = ultimo;
  }
  return respuestas;
}

function Tarjeta({ aviso, respuesta }: { aviso: ModificacionResumen; respuesta: MensajeVista | undefined }) {
  const navegar = useNavigate();
  const estado = estadoAviso(aviso.estado);
  const audioRespuesta = respuesta?.adjuntos.find((a) => a.tipo === 'AUDIO');
  return (
    <div className={`orden aviso ${estado.franja}`}>
      <span className="franja" />
      <FotoModelo modelo={fotoDelModelo(aviso)} />
      <div className="cuerpo">
        <span className="tit">
          {aviso.tipoCambioTexto ?? 'Cambio'} · {aviso.modeloReferencia}
        </span>
        <span className="meta">
          {aviso.modeloNombre} · {hace(aviso.creadaEn)} · {aviso.audios > 0 ? 'con voz' : 'sin voz'} · {aviso.fotos}{' '}
          {aviso.fotos === 1 ? 'foto' : 'fotos'}
        </span>
        <span className="meta">
          <span className={`pill ${estado.pill}`}>{estado.texto}</span>
        </span>
        {respuesta && (
          <div className="respuesta">
            <span>
              <b>{respuesta.autor ?? 'Despacho'}:</b> {respuesta.texto}
            </span>
            {audioRespuesta && <Audio compacto adjuntoId={audioRespuesta.id} duracionSeg={audioRespuesta.duracionSeg ?? 0} />}
            <button className="boton marca" type="button" onClick={() => navegar(`/avisos/${aviso.id}/contestar`)}>
              Contestar
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default function MisAvisos() {
  const { resumen } = useBandeja();
  const [avisos, setAvisos] = useState<ModificacionResumen[] | null>(null);
  const [respuestas, setRespuestas] = useState<Respuestas>({});
  const [error, setError] = useState<string | null>(null);

  const peticion = useRef(0);
  const cargar = useCallback(() => {
    const esta = ++peticion.current;
    api<ModificacionResumen[]>('/api/modificaciones?direccion=TALLER_A_DESPACHO')
      .then(async (lista) => {
        const suyas = await respuestasDe(lista);
        if (esta !== peticion.current) return;
        setAvisos(ordenarAvisos(lista));
        setRespuestas(suyas);
        setError(null);
      })
      .catch((e: unknown) => esta === peticion.current && setError(e instanceof ErrorApi ? e.message : 'No se han podido cargar los avisos'));
  }, []);

  // Al entrar y cada vez que la bandeja dice que despacho ha contestado algo.
  const cambio = resumen ? `${resumen.conRespuesta}` : '';
  useEffect(cargar, [cargar, cambio]);

  if (error && !avisos) {
    return (
      <div className="vacio">
        <b>{error}</b>
        <button className="boton" type="button" onClick={cargar}>
          Reintentar
        </button>
      </div>
    );
  }
  if (!avisos) return null;

  return (
    <>
      <h2 className="saludo">Mis avisos</h2>
      <p className="sub">Lo que has mandado a despacho y en qué punto está.</p>
      {avisos.length > 0 ? (
        <div className="lista">
          {avisos.map((a) => (
            <Tarjeta key={a.id} aviso={a} respuesta={respuestas[a.id]} />
          ))}
        </div>
      ) : (
        <div className="vacio">
          <b>Todavía no has mandado nada</b>
          <span>Cuando avises de un cambio con «He cambiado algo», lo verás aquí.</span>
        </div>
      )}
    </>
  );
}
