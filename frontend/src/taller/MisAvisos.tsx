// «Mis avisos»: lo que el taller ha mandado a despacho y en qué punto está
// (enviado, visto, hecho), el último primero. Tocar la tarjeta abre el aviso
// (/avisos/:id), donde se corrige o se borra mientras despacho no lo haya
// empezado a aplicar. Aquí no hay respuestas: las dudas van por teléfono.
//
// Son todos los avisos del taller y no solo los de quien mira: hay una sola
// cuenta de taller (la tablet), así que la API ya los da todos. De la nota de
// voz del aviso, la lista ya dice cuánto dura (duracionAudioSeg).
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import FotoModelo from '../comun/FotoModelo';
import { avisoEditable, estadoAviso, fotoDelModelo, ordenarAvisos, textoVoz, type ModificacionResumen } from '../comun/modificaciones';
import { hace } from '../comun/tiempo';

function Tarjeta({ aviso }: { aviso: ModificacionResumen }) {
  const navegar = useNavigate();
  const estado = estadoAviso(aviso.estado);
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
      </div>
    </div>
  );
}

export default function MisAvisos() {
  const [avisos, setAvisos] = useState<ModificacionResumen[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const peticion = useRef(0);
  const cargar = useCallback(() => {
    const esta = ++peticion.current;
    api<ModificacionResumen[]>('/api/modificaciones?direccion=TALLER_A_DESPACHO')
      .then((lista) => {
        if (esta !== peticion.current) return;
        setAvisos(ordenarAvisos(lista));
        setError(null);
      })
      .catch((e: unknown) => esta === peticion.current && setError(e instanceof ErrorApi ? e.message : 'No se han podido cargar los avisos'));
  }, []);

  useEffect(cargar, [cargar]);

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
            <Tarjeta key={a.id} aviso={a} />
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
