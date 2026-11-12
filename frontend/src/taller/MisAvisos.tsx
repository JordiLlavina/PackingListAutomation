// «Mis avisos»: lo que el taller ha mandado a despacho y en qué punto está
// (enviado, visto, hecho), el último primero. Si lo último que se ha dicho en
// un aviso es de despacho, sale dentro de la tarjeta con su nota de voz y un
// botón para contestar; también en un aviso ya cerrado, que es donde despacho
// explica por qué lo ha descartado. Tocar la tarjeta abre el aviso
// (/avisos/:id), donde se corrige o se borra mientras despacho no lo haya
// empezado a aplicar.
//
// Son todos los avisos del taller y no solo los de quien mira: hay una sola
// cuenta de taller (la tablet), así que la API ya los da todos.
//
// La lista no trae los mensajes. Para no pedir el detalle de cada tarjeta,
// solo se pide el de los avisos cuyo último mensaje es de despacho
// (ultimoMensajeDe = DESPACHO), que son pocos. De la nota de voz del aviso, la
// lista ya dice cuánto dura (duracionAudioSeg).
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Audio from '../comun/Audio';
import FotoModelo from '../comun/FotoModelo';
import {
  avisoEditable,
  estadoAviso,
  fotoDelModelo,
  ordenarAvisos,
  textoVoz,
  type MensajeVista,
  type ModificacionDetalle,
  type ModificacionResumen,
} from '../comun/modificaciones';
import { hace } from '../comun/tiempo';
import { useBandeja } from '../comun/useBandeja';

/** El último mensaje de cada aviso cuyo último mensaje es de despacho (también en los cerrados). */
type Respuestas = Record<number, MensajeVista>;

async function respuestasDe(avisos: ModificacionResumen[]): Promise<Respuestas> {
  const esperando = avisos.filter((a) => a.ultimoMensajeDe === 'DESPACHO');
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
  const abrir = () => navegar(`/avisos/${aviso.id}`);
  return (
    <div
      className={`orden aviso abrible ${estado.franja}`}
      role="link"
      tabIndex={0}
      aria-label={`Abrir el aviso ${aviso.tipoCambioTexto ?? 'Cambio'} · ${aviso.modeloReferencia}`}
      onClick={abrir}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          abrir();
        }
      }}
    >
      <span className="franja" />
      <FotoModelo modelo={fotoDelModelo(aviso)} />
      <div className="cuerpo">
        <span className="tit">
          {aviso.tipoCambioTexto ?? 'Cambio'} · {aviso.modeloReferencia}
        </span>
        <span className="meta">
          {aviso.modeloNombre} · {hace(aviso.creadaEn)} · {textoVoz(aviso.duracionAudioSeg)} · {aviso.fotos}{' '}
          {aviso.fotos === 1 ? 'foto' : 'fotos'}
        </span>
        <span className="meta">
          <span className={`pill ${estado.pill}`}>{estado.texto}</span>
          {avisoEditable(aviso.estado) && <span className="cuando">Toca para corregirlo o borrarlo</span>}
        </span>
        {respuesta && (
          <div className="respuesta">
            <span>
              <b>{respuesta.autor ?? 'Despacho'}:</b> {respuesta.texto}
            </span>
            {audioRespuesta && <Audio compacto adjuntoId={audioRespuesta.id} duracionSeg={audioRespuesta.duracionSeg ?? 0} />}
            <button
              className="boton marca"
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                navegar(`/avisos/${aviso.id}/contestar`);
              }}
            >
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
      {avisos.length > 0 ? (
        <div className="lista">
          {avisos.map((a) => (
            <Tarjeta key={a.id} aviso={a} respuesta={respuestas[a.id]} />
          ))}
        </div>
      ) : (
        <div className="vacio">
          <b>Todavía no has mandado nada</b>
          <span>Cuando avises de un cambio con «Crear Modificación», lo verás aquí.</span>
        </div>
      )}
    </>
  );
}
