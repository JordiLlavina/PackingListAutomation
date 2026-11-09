// La ficha de un modelo en despacho, en el panel de la derecha: la cabecera
// (foto principal, referencia, nombre editable, cliente, etapa, temporadas y
// los nombres que ha tenido), las acciones (cambiar de etapa, subir fotos),
// la galería con la principal marcada y el historial. Tras cada cambio se
// vuelve a pedir la ficha y se avisa a la lista para que repinte su fila.
import { useCallback, useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { ErrorApi, api } from '../comun/api';
import Etapa from '../comun/Etapa';
import FotoModelo from '../comun/FotoModelo';
import Historial from '../comun/Historial';
import { Icono } from '../comun/iconos';
import { ETAPAS, etapaTexto, fotoUrl, type Etapa as TipoEtapa, type ModeloFicha } from '../comun/modelos';

function mensaje(e: unknown, porDefecto: string): string {
  return e instanceof ErrorApi ? e.message : porDefecto;
}

function fecha(iso: string): string {
  return new Date(iso).toLocaleDateString('es-ES');
}

export default function FichaModelo({ id, onCambiado }: { id: number; onCambiado: () => void }) {
  const [ficha, setFicha] = useState<ModeloFicha | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [nombre, setNombre] = useState<string | null>(null);
  const [subiendo, setSubiendo] = useState(false);
  const dialogo = useRef<HTMLDialogElement>(null);
  const [etapa, setEtapa] = useState<TipoEtapa>('PROTOTIPO');
  const [motivo, setMotivo] = useState('');
  const [errorEtapa, setErrorEtapa] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);

  // Solo vale la respuesta de la última petición: si llega tarde la de otro
  // modelo (o una recarga vieja), se tira, para que la ficha y las acciones
  // sean siempre del modelo que se ve.
  const peticion = useRef(0);
  const cargar = useCallback(() => {
    const esta = ++peticion.current;
    setError(null);
    return api<ModeloFicha>(`/api/modelos/${id}`)
      .then((f) => {
        if (esta === peticion.current) setFicha(f);
      })
      .catch((e: unknown) => {
        if (esta === peticion.current) setError(mensaje(e, 'No se ha podido cargar el modelo'));
      });
  }, [id]);

  useEffect(() => {
    void cargar();
    // Al desmontar, cualquier respuesta pendiente deja de valer.
    return () => {
      peticion.current++;
    };
  }, [cargar]);

  const trasCambiar = async () => {
    await cargar();
    onCambiado();
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
  if (!ficha) return null;
  const m = ficha.modelo;
  const anteriores = ficha.codigos.filter((c) => c.hasta !== null);

  const guardarNombre = async (e: FormEvent) => {
    e.preventDefault();
    if (nombre === null) return;
    setErrorAccion(null);
    try {
      await api(`/api/modelos/${m.id}`, { method: 'PATCH', body: JSON.stringify({ nombre: nombre.trim() }) });
      setNombre(null);
      await trasCambiar();
    } catch (err) {
      setErrorAccion(mensaje(err, 'No se ha podido guardar el nombre'));
    }
  };

  const abrirEtapa = () => {
    setEtapa(m.etapa);
    setMotivo('');
    setErrorEtapa(null);
    dialogo.current?.showModal();
  };

  const cambiarEtapa = async (e: FormEvent) => {
    e.preventDefault();
    setOcupado(true);
    setErrorEtapa(null);
    try {
      await api(`/api/modelos/${m.id}/etapa`, {
        method: 'POST',
        body: JSON.stringify({ etapa, motivo: motivo.trim() || null }),
      });
      dialogo.current?.close();
      await trasCambiar();
    } catch (err) {
      setErrorEtapa(mensaje(err, 'No se ha podido cambiar la etapa'));
    } finally {
      setOcupado(false);
    }
  };

  const subirFotos = async (e: ChangeEvent<HTMLInputElement>) => {
    const ficheros = [...(e.target.files ?? [])];
    e.target.value = '';
    if (ficheros.length === 0) return;
    const datos = new FormData();
    for (const f of ficheros) datos.append('fotos', f);
    setSubiendo(true);
    setErrorAccion(null);
    try {
      await api(`/api/modelos/${m.id}/fotos`, { method: 'POST', body: datos });
      await trasCambiar();
    } catch (err) {
      setErrorAccion(mensaje(err, 'No se han podido subir las fotos'));
    } finally {
      setSubiendo(false);
    }
  };

  const hacerPrincipal = async (fotoId: number) => {
    setErrorAccion(null);
    try {
      await api(`/api/modelos/${m.id}/fotos/${fotoId}/principal`, { method: 'POST' });
      await trasCambiar();
    } catch (err) {
      setErrorAccion(mensaje(err, 'No se ha podido cambiar la foto principal'));
    }
  };

  return (
    <div className="detalle">
      <div className="ficha grande">
        <FotoModelo modelo={m} />
        <div className="texto">
          <b>{m.referencia}</b>
          {nombre === null ? (
            <button className="editable nombre" type="button" title="Cambiar el nombre" onClick={() => setNombre(m.nombre)}>
              {m.nombre || 'Sin nombre'}
            </button>
          ) : (
            <form className="editar-nombre" onSubmit={(e) => void guardarNombre(e)}>
              <input aria-label="Nombre del modelo" value={nombre} autoFocus onChange={(e) => setNombre(e.target.value)} />
              <button className="boton ok" type="submit">
                Guardar
              </button>
              <button className="boton" type="button" onClick={() => setNombre(null)}>
                Cancelar
              </button>
            </form>
          )}
          <span>{m.cliente}</span>
          <Etapa etapa={m.etapa} />
          <span className="meta">
            {ficha.temporadas.length > 0 ? `Temporadas: ${ficha.temporadas.join(', ')}` : 'Sin temporada'}
          </span>
          {anteriores.length > 0 && (
            <span className="meta">
              Antes:{' '}
              {anteriores
                .map((c) => `${c.codigo} (hasta el ${fecha(c.hasta ?? c.desde)}${c.motivo ? `, ${c.motivo}` : ''})`)
                .join(' · ')}
            </span>
          )}
        </div>
      </div>

      <div className="botones">
        <button className="boton" type="button" onClick={abrirEtapa}>
          Cambiar etapa
        </button>
        <label className="boton subir" aria-disabled={subiendo}>
          <Icono nombre="cam" /> {subiendo ? 'Subiendo fotos…' : 'Subir fotos'}
          <input type="file" multiple accept="image/*" disabled={subiendo} onChange={(e) => void subirFotos(e)} />
        </label>
      </div>
      {errorAccion && <p className="error">{errorAccion}</p>}

      <div className="fila-titulo">
        <h2>Fotos</h2>
      </div>
      {ficha.fotos.length === 0 ? (
        <p className="ayuda">Este modelo aún no tiene fotos. Súbelas con «Subir fotos».</p>
      ) : (
        <div className="miniaturas galeria">
          {ficha.fotos.map((f) => (
            <figure key={f.id}>
              <img src={fotoUrl(f.id)} alt={f.nombreFichero} loading="lazy" />
              <figcaption>
                {f.principal ? (
                  <span className="principal">Principal</span>
                ) : (
                  <button className="chip" type="button" onClick={() => void hacerPrincipal(f.id)}>
                    Hacer principal
                  </button>
                )}
              </figcaption>
            </figure>
          ))}
        </div>
      )}

      <div className="fila-titulo">
        <h2>Historial</h2>
      </div>
      <Historial eventos={ficha.lineaTiempo} />

      <dialog ref={dialogo} className="dialogo" aria-labelledby="titulo-etapa">
        <form onSubmit={(e) => void cambiarEtapa(e)}>
          <h2 id="titulo-etapa">Cambiar etapa de {m.referencia}</h2>
          <label className="campo">
            Etapa
            <select value={etapa} onChange={(e) => setEtapa(e.target.value as TipoEtapa)}>
              {ETAPAS.map((x) => (
                <option key={x} value={x}>
                  {etapaTexto(x)}
                </option>
              ))}
            </select>
          </label>
          <label className="campo">
            Motivo (opcional)
            <textarea rows={3} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
          </label>
          {errorEtapa && <p className="error">{errorEtapa}</p>}
          <div className="botones">
            <button className="boton ok" type="submit" disabled={ocupado || etapa === m.etapa}>
              Cambiar etapa
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
