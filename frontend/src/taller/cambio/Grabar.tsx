// Paso 4: la nota de voz. El botón rojo de 160 px graba y para; el contador
// y la onda dicen que está escuchando (comun/Grabadora). Se para sola a los 3
// minutos. Sin permiso de micrófono no se bloquea nada: se puede mandar solo
// con fotos.
import { Navigate, useNavigate } from 'react-router';
import Audio from '../../comun/Audio';
import Grabadora, { useGrabadora } from '../../comun/Grabadora';
import { Icono } from '../../comun/iconos';
import { useBorrador } from './Borrador';
import Pasos from './Pasos';

export default function Grabar() {
  const navegar = useNavigate();
  const { modelo, fotos, audio, ponerAudio } = useBorrador();
  const grabadora = useGrabadora((g) => ponerAudio({ blob: g.blob, duracionSeg: g.duracionSeg }));
  const { grabando, sinMicro } = grabadora;

  if (!modelo) return <Navigate to="/cambio" replace />;

  const listo = !grabando && audio !== null;
  const pregunta = grabando ? 'Te escucho… toca para parar' : listo ? 'Grabado. ¿Lo mandamos?' : 'Toca y explica qué has cambiado';

  return (
    <>
      <Pasos />
      <h2 className="pregunta">{pregunta}</h2>
      <Grabadora estado={grabadora} grabadaSeg={audio?.duracionSeg ?? null} />
      {listo && audio && <Audio key={audio.url} src={audio.url} duracionSeg={audio.duracionSeg} />}
      <div className="acciones">
        <button className="boton" type="button" disabled={!listo} onClick={() => ponerAudio(null)}>
          Repetir
        </button>
        <button className="boton ok" type="button" disabled={grabando || (!listo && fotos.length === 0)} onClick={() => navegar('/cambio/revisar')}>
          <Icono nombre="check" /> Siguiente
        </button>
      </div>
      {sinMicro && <p className="ayuda">No puedo usar el micrófono. Puedes mandar solo fotos</p>}
      <p className="ayuda">
        {fotos.length > 0
          ? `Llevas ${fotos.length} ${fotos.length === 1 ? 'foto' : 'fotos'}. Si no quieres grabar, pulsa Siguiente.`
          : 'Hace falta una nota de voz o una foto.'}
      </p>
    </>
  );
}
