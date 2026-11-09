// Paso 3: «Haz fotos del cambio». El recuadro abre la cámara de atrás de la
// tablet (se pueden hacer varias); cada foto se reduce en el navegador antes
// de guardarla en el borrador, y se puede quitar. Sin fotos se sigue con la voz.
import { useState, type ChangeEvent } from 'react';
import { Navigate, useNavigate } from 'react-router';
import { Icono } from '../../comun/iconos';
import { reducirImagen } from '../../comun/imagen';
import { useBorrador } from './Borrador';
import Pasos from './Pasos';

export default function Fotos() {
  const navegar = useNavigate();
  const { modelo, fotos, anadirFotos, quitarFoto } = useBorrador();
  const [preparando, setPreparando] = useState(0);

  if (!modelo) return <Navigate to="/cambio" replace />;

  const alElegir = async (e: ChangeEvent<HTMLInputElement>) => {
    const ficheros = [...(e.target.files ?? [])];
    // Vaciarlo deja volver a elegir la misma foto si se ha quitado.
    e.target.value = '';
    if (ficheros.length === 0) return;
    setPreparando((n) => n + ficheros.length);
    try {
      anadirFotos(await Promise.all(ficheros.map((f) => reducirImagen(f))));
    } finally {
      setPreparando((n) => n - ficheros.length);
    }
  };

  return (
    <>
      <Pasos />
      <h2 className="pregunta">Haz fotos del cambio</h2>
      <label className="camara">
        <Icono nombre="cam" />
        <span>
          <b>Hacer foto</b>
          <br />
          <span className="ayuda" style={{ margin: 0 }}>
            Puedes hacer varias
          </span>
        </span>
        <input type="file" accept="image/*" capture="environment" multiple onChange={(e) => void alElegir(e)} />
      </label>
      {preparando > 0 && <p className="ayuda">Preparando {preparando === 1 ? 'la foto' : `${preparando} fotos`}…</p>}
      {fotos.length > 0 && (
        <div className="miniaturas" style={{ marginTop: 14 }}>
          {fotos.map((f, i) => (
            <figure key={f.url} className="con-quitar">
              <img src={f.url} alt={`Foto ${i + 1} del cambio`} />
              <button className="quitar-foto" type="button" aria-label={`Quitar la foto ${i + 1}`} onClick={() => quitarFoto(i)}>
                Quitar
              </button>
            </figure>
          ))}
        </div>
      )}
      <div className="acciones">
        <button className="boton" type="button" onClick={() => navegar('/cambio/voz')}>
          Sin foto, solo voz
        </button>
        <button className="boton ok" type="button" disabled={fotos.length === 0 || preparando > 0} onClick={() => navegar('/cambio/voz')}>
          <Icono nombre="check" /> Siguiente ({fotos.length})
        </button>
      </div>
    </>
  );
}
