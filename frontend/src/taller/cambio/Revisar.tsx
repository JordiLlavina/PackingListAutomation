// «¿Está bien?»: el modelo, el tipo de cambio, la nota de voz y las fotos,
// antes de mandarlo a despacho. Si el envío falla (la WiFi del taller), todo
// se queda como estaba, con lo que ha dicho el servidor y un botón grande de
// volver a intentar: no se pierde nada de lo grabado.
import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router';
import { ErrorApi, api } from '../../comun/api';
import Audio from '../../comun/Audio';
import Etapa from '../../comun/Etapa';
import FotoModelo from '../../comun/FotoModelo';
import { extensionAudio } from '../../comun/grabador';
import { Icono } from '../../comun/iconos';
import { useBorrador } from './Borrador';
import { motivoNoEnviar } from './reglas';
import { useTiposCambio } from './tipos';

/** El nombre con el que viaja una foto: el suyo si no se ha podido reducir (sigue siendo el fichero original). */
function nombreFoto(blob: Blob, indice: number): string {
  return blob instanceof File ? blob.name : `foto-${indice + 1}.jpg`;
}

export default function Revisar() {
  const navegar = useNavigate();
  const borrador = useBorrador();
  const { modelo, tipo, fotos, audio } = borrador;
  const { tipos } = useTiposCambio();
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!modelo) return <Navigate to="/cambio" replace />;

  const motivo = motivoNoEnviar(borrador);
  const tipoCambio = tipos?.find((t) => t.clave === tipo);

  const enviar = async () => {
    if (motivo || enviando) return;
    setEnviando(true);
    setError(null);
    const datos = new FormData();
    datos.append('modeloId', String(modelo.id));
    datos.append('tipoCambio', tipo ?? '');
    if (audio) {
      datos.append('audio', audio.blob, `nota-de-voz.${extensionAudio(audio.blob.type)}`);
      datos.append('audioDuracionSeg', String(audio.duracionSeg));
    }
    fotos.forEach((f, i) => datos.append('fotos', f.blob, nombreFoto(f.blob, i)));
    try {
      await api<unknown>('/api/modificaciones', { method: 'POST', body: datos });
      // Sin vuelta atrás a esta pantalla: el aviso ya está en despacho.
      navegar('/cambio/enviado', { replace: true });
    } catch (e: unknown) {
      setError(e instanceof ErrorApi ? e.message : 'No se ha podido enviar');
      setEnviando(false);
    }
  };

  const textoEnviar = enviando ? 'Enviando…' : null;

  return (
    <>
      <h2 className="pregunta">¿Está bien?</h2>
      <div className="resumen">
        <div className="ficha">
          <FotoModelo modelo={modelo} />
          <div className="texto">
            <b>{modelo.referencia}</b>
            <span>
              {modelo.nombre} · {modelo.cliente}
            </span>
            <Etapa etapa={modelo.etapa} />
          </div>
        </div>
        {tipo && (
          <div className="ficha">
            <Icono nombre={tipoCambio?.icono ?? 'otro'} className="foto" />
            <div className="texto">
              <b>{tipoCambio?.texto ?? tipo}</b>
              <span>Tipo de cambio</span>
            </div>
          </div>
        )}
        {audio && <Audio key={audio.url} src={audio.url} duracionSeg={audio.duracionSeg} />}
        {fotos.length > 0 && (
          <div className="miniaturas">
            {fotos.map((f, i) => (
              <img key={f.url} src={f.url} alt={`Foto ${i + 1} del cambio`} />
            ))}
          </div>
        )}
      </div>
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
        <button className="boton" type="button" disabled={enviando} onClick={() => navegar('/cambio/tipo')}>
          Cambiar algo
        </button>
        {!error && (
          <button className="boton ok" type="button" disabled={motivo !== null || enviando} onClick={() => void enviar()}>
            <Icono nombre="check" /> {textoEnviar ?? 'Enviar a despacho'}
          </button>
        )}
      </div>
      {motivo && <p className="ayuda">{motivo}</p>}
    </>
  );
}
