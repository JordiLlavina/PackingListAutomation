// «¿Cuáles son del modelo?»: tras subir un PDF, las imágenes que trae (las
// incrustadas grandes y cada página pintada) en una rejilla, cada una con su
// casilla «Es del modelo» y, entre las marcadas, el «Principal». Solo las
// marcadas entran en las fotos del modelo; las demás no se guardan nunca. El
// documento ya está guardado: «Ninguna, solo el documento» cierra sin más.
// Las candidatas caducan a la media hora; si se elige tarde, el servidor lo
// dice y hay que volver a subir el PDF.
import { useEffect, useRef, useState } from 'react';
import { ErrorApi, api } from '../comun/api';
import {
  alternarMarcada,
  candidataUrl,
  clavesMarcadas,
  elegirPrincipal,
  textoFotosAnadidas,
  type EleccionImagenes,
  type ResumenFoto,
  type Subida,
} from '../comun/modelos';
import { toast } from '../comun/toast';

type Props = {
  modeloId: number;
  subida: Subida;
  onCerrar: () => void;
  /** Tras guardar las elegidas, para volver a pedir la galería. */
  onGuardadas: () => void;
};

export default function ElegirImagenesPdf({ modeloId, subida, onCerrar, onGuardadas }: Props) {
  const dialogo = useRef<HTMLDialogElement>(null);
  const [eleccion, setEleccion] = useState<EleccionImagenes>({ marcadas: [], principal: null });
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const d = dialogo.current;
    if (d && !d.open) d.showModal();
  }, []);

  const loteId = subida.loteId ?? '';
  const claves = clavesMarcadas(subida.candidatas, eleccion);

  const guardar = async () => {
    if (claves.length === 0) return;
    setGuardando(true);
    setError(null);
    try {
      await api<ResumenFoto[]>(`/api/modelos/${modeloId}/documentos/${subida.documento.id}/elegir-fotos`, {
        method: 'POST',
        body: JSON.stringify({ lote: loteId, claves, principal: eleccion.principal }),
      });
      toast(textoFotosAnadidas(claves.length));
      onGuardadas();
      dialogo.current?.close();
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se han podido guardar las fotos');
    } finally {
      setGuardando(false);
    }
  };

  return (
    <dialog ref={dialogo} className="dialogo elegir-imagenes" onClose={onCerrar} aria-labelledby="titulo-imagenes">
      <div className="formulario">
        <h2 id="titulo-imagenes">¿Cuáles son del modelo?</h2>
        <p className="ayuda">
          Las que no marques no se guardan. De {subida.documento.nombre}.
        </p>
        <div className="candidatas">
          {subida.candidatas.map((c) => {
            const marcada = eleccion.marcadas.includes(c.clave);
            return (
              <figure key={c.clave} className={marcada ? 'marcada' : undefined}>
                <img src={candidataUrl(modeloId, loteId, c.clave)} alt={c.origen} loading="lazy" />
                <figcaption>
                  <span className="meta">
                    {c.origen} · {c.ancho} × {c.alto}
                  </span>
                  <label>
                    <input type="checkbox" checked={marcada} onChange={() => setEleccion((e) => alternarMarcada(e, c.clave))} />
                    Es del modelo
                  </label>
                  <label className={marcada ? undefined : 'apagada'}>
                    <input
                      type="radio"
                      name="principal"
                      disabled={!marcada}
                      checked={eleccion.principal === c.clave}
                      onChange={() => setEleccion((e) => elegirPrincipal(e, c.clave))}
                    />
                    Principal
                  </label>
                </figcaption>
              </figure>
            );
          })}
        </div>
        {error && <p className="error">{error}</p>}
        <div className="botones">
          <button className="boton ok" type="button" disabled={guardando || claves.length === 0} onClick={() => void guardar()}>
            {guardando ? 'Guardando…' : 'Guardar las elegidas'}
          </button>
          <button className="boton" type="button" disabled={guardando} onClick={() => dialogo.current?.close()}>
            Ninguna, solo el documento
          </button>
        </div>
      </div>
    </dialog>
  );
}
