// «¿Cuáles son del modelo?»: tras subir un PDF, las imágenes que trae (las
// incrustadas grandes y cada página pintada) en una rejilla, cada una con su
// casilla «Es del modelo»; con «Añadir fotos del PC» se suman otras desde el
// ordenador. Debajo, todas las fotos que tendrá el modelo —las que ya tenía,
// las marcadas del PDF y las del PC— en una tira que se ordena arrastrando:
// ese es el orden de la orden de corte y del Word de fotos, y la primera es
// la principal. Solo las marcadas y las del PC entran en las fotos del
// modelo; las demás no se guardan nunca. El documento ya está guardado:
// «Ninguna, solo el documento» cierra sin más. Las candidatas caducan a la
// media hora; si se elige tarde, el servidor lo dice y hay que volver a subir
// el PDF.
//
// Guardar son hasta tres pasos (las del PDF, las del PC, el orden). Si uno
// falla, lo ya guardado pasa a la tira como «Ya guardada», así reintentar no
// guarda dos veces la misma foto.
import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { ErrorApi, api } from '../comun/api';
import { reducirImagen } from '../comun/imagen';
import {
  alternarEnTira,
  candidataUrl,
  enTira,
  fotoUrl,
  idsEnOrden,
  idsNuevos,
  mismoOrden,
  textoFotosAnadidas,
  type ResumenFoto,
  type Subida,
} from '../comun/modelos';
import { toast } from '../comun/toast';
import FotosOrdenables, { type FotoOrdenable } from './FotosOrdenables';

type Props = {
  modeloId: number;
  subida: Subida;
  onCerrar: () => void;
  /** Tras guardar las elegidas, para volver a pedir la galería. */
  onGuardadas: () => void;
};

/** Una foto elegida en el PC, con su vista previa, hasta que se guarda. */
type DelPc = { clave: string; fichero: File; url: string };

/** El fichero reducido en el navegador (y derecho), con nombre .jpg si se ha convertido. */
async function preparada(fichero: File): Promise<{ blob: Blob; nombre: string }> {
  const blob = await reducirImagen(fichero);
  const nombre = blob === fichero ? fichero.name : `${fichero.name.replace(/\.[^.]*$/, '')}.jpg`;
  return { blob, nombre };
}

export default function ElegirImagenesPdf({ modeloId, subida, onCerrar, onGuardadas }: Props) {
  const dialogo = useRef<HTMLDialogElement>(null);
  // Las que el modelo ya tiene, en su orden; null mientras se cargan.
  const [guardadas, setGuardadas] = useState<ResumenFoto[] | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [tira, setTira] = useState<string[]>([]);
  const [delPc, setDelPc] = useState<DelPc[]>([]);
  const siguientePc = useRef(0);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const d = dialogo.current;
    if (d && !d.open) d.showModal();
  }, []);

  // Las fotos que ya tiene el modelo van delante, en su orden; lo que se haya
  // marcado mientras cargaban se queda detrás.
  useEffect(() => {
    let vigente = true;
    api<ResumenFoto[]>(`/api/modelos/${modeloId}/fotos`)
      .then((fotos) => {
        if (!vigente) return;
        setGuardadas(fotos);
        setTira((antes) => [...fotos.map((f) => enTira.guardada(f.id)), ...antes.filter((c) => !c.startsWith('g:'))]);
      })
      .catch((e: unknown) => {
        if (!vigente) return;
        setGuardadas([]);
        setErrorCarga(e instanceof ErrorApi ? e.message : 'No se han podido cargar las fotos que ya tiene el modelo');
      });
    return () => {
      vigente = false;
    };
  }, [modeloId]);

  // Las vistas previas del PC ocupan memoria hasta que se sueltan.
  const vistas = useRef<string[]>([]);
  vistas.current = delPc.map((f) => f.url);
  useEffect(() => () => vistas.current.forEach((url) => URL.revokeObjectURL(url)), []);

  const loteId = subida.loteId ?? '';
  const candidata = new Map(subida.candidatas.map((c) => [enTira.delPdf(c.clave), c]));
  const guardada = new Map((guardadas ?? []).map((f) => [enTira.guardada(f.id), f]));
  const pc = new Map(delPc.map((f) => [f.clave, f]));

  // Hay algo que guardar si hay fotos nuevas o si las que ya tenía han cambiado de orden.
  const nuevas = tira.filter((c) => !c.startsWith('g:')).length;
  const ordenCambiado = !mismoOrden(
    tira.flatMap((c) => guardada.get(c)?.id ?? []),
    (guardadas ?? []).map((f) => f.id),
  );
  const hayQueGuardar = nuevas > 0 || ordenCambiado;

  const quitarDelPc = (clave: string) => {
    const foto = pc.get(clave);
    if (foto) URL.revokeObjectURL(foto.url);
    setDelPc((antes) => antes.filter((f) => f.clave !== clave));
    setTira((antes) => antes.filter((c) => c !== clave));
  };

  const anadirDelPc = (e: ChangeEvent<HTMLInputElement>) => {
    const ficheros = [...(e.target.files ?? [])];
    e.target.value = '';
    if (ficheros.length === 0) return;
    const nuevasPc = ficheros.map((fichero) => ({ clave: enTira.delPc(siguientePc.current++), fichero, url: URL.createObjectURL(fichero) }));
    setDelPc((antes) => [...antes, ...nuevasPc]);
    setTira((antes) => [...antes, ...nuevasPc.map((f) => f.clave)]);
  };

  const fotos: FotoOrdenable[] = tira.flatMap((clave): FotoOrdenable[] => {
    const g = guardada.get(clave);
    if (g) return [{ clave, src: fotoUrl(g.id), alt: g.nombreFichero, nota: 'Ya guardada' }];
    const p = candidata.get(clave);
    if (p) return [{ clave, src: candidataUrl(modeloId, loteId, p.clave), alt: p.origen, nota: `Del PDF · ${p.origen}`, onQuitar: () => setTira((t) => alternarEnTira(t, clave)) }];
    const f = pc.get(clave);
    if (f) return [{ clave, src: f.url, alt: f.fichero.name, nota: f.fichero.name, onQuitar: () => quitarDelPc(clave) }];
    return [];
  });

  const guardar = async () => {
    if (!hayQueGuardar) return;
    setGuardando(true);
    setError(null);
    // Qué id tiene ya cada clave de la tira; las del modelo, el suyo.
    const idDe = new Map<string, number>((guardadas ?? []).map((f) => [enTira.guardada(f.id), f.id]));
    let actuales = guardadas ?? [];
    let tiraActual = tira;
    let anadidas = 0;
    // Lo ya guardado pasa a ser «Ya guardada» en la tira, por si el paso siguiente falla.
    const asentar = (despues: ResumenFoto[], claves: string[]) => {
      const ids = idsNuevos(
        actuales.map((f) => f.id),
        despues,
      );
      const guardadaComo = new Map<string, string>();
      claves.forEach((clave, i) => {
        const id = ids[i];
        if (id === undefined) return;
        idDe.set(enTira.guardada(id), id);
        guardadaComo.set(clave, enTira.guardada(id));
      });
      anadidas += ids.length;
      actuales = despues;
      tiraActual = tiraActual.map((c) => guardadaComo.get(c) ?? c);
      setGuardadas(despues);
      setTira(tiraActual);
    };
    try {
      const delPdf = tiraActual.filter((c) => c.startsWith('p:'));
      if (delPdf.length > 0) {
        const despues = await api<ResumenFoto[]>(`/api/modelos/${modeloId}/documentos/${subida.documento.id}/elegir-fotos`, {
          method: 'POST',
          body: JSON.stringify({ lote: loteId, claves: delPdf.flatMap((c) => candidata.get(c)?.clave ?? []), principal: null }),
        });
        asentar(despues, delPdf);
      }
      const clavesPc = tiraActual.filter((c) => c.startsWith('f:'));
      if (clavesPc.length > 0) {
        const datos = new FormData();
        for (const clave of clavesPc) {
          const foto = pc.get(clave);
          if (!foto) continue;
          const { blob, nombre } = await preparada(foto.fichero);
          datos.append('fotos', blob, nombre);
        }
        const despues = await api<ResumenFoto[]>(`/api/modelos/${modeloId}/fotos`, { method: 'POST', body: datos });
        asentar(despues, clavesPc);
        clavesPc.forEach((clave) => {
          const foto = pc.get(clave);
          if (foto) URL.revokeObjectURL(foto.url);
        });
        setDelPc((antes) => antes.filter((f) => !clavesPc.includes(f.clave)));
      }
      // Sin saber qué fotos tenía, no se toca el orden: las nuevas ya han quedado detrás, en el de la tira.
      const ids = idsEnOrden(tiraActual, idDe, actuales.map((f) => f.id));
      if (errorCarga === null && !mismoOrden(ids, actuales.map((f) => f.id))) {
        await api<ResumenFoto[]>(`/api/modelos/${modeloId}/fotos/orden`, { method: 'PUT', body: JSON.stringify({ ids }) });
      }
      toast(anadidas > 0 ? textoFotosAnadidas(anadidas) : 'Orden de las fotos guardado');
      onGuardadas();
      dialogo.current?.close();
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se han podido guardar las fotos');
      // Lo que sí se guardó ya está en la galería: que la ficha lo enseñe.
      if (anadidas > 0) onGuardadas();
    } finally {
      setGuardando(false);
    }
  };

  return (
    <dialog ref={dialogo} className="dialogo elegir-imagenes" onClose={onCerrar} aria-labelledby="titulo-imagenes">
      <div className="formulario">
        <h2 id="titulo-imagenes">¿Cuáles son del modelo?</h2>
        <p className="ayuda">Las que no marques no se guardan. De {subida.documento.nombre}.</p>
        <div className="candidatas">
          {subida.candidatas.map((c) => {
            const clave = enTira.delPdf(c.clave);
            const marcada = tira.includes(clave);
            return (
              <figure key={c.clave} className={marcada ? 'marcada' : undefined}>
                <img src={candidataUrl(modeloId, loteId, c.clave)} alt={c.origen} loading="lazy" />
                <figcaption>
                  <span className="meta">
                    {c.origen} · {c.ancho} × {c.alto}
                  </span>
                  <label>
                    <input type="checkbox" checked={marcada} disabled={guardando} onChange={() => setTira((t) => alternarEnTira(t, clave))} />
                    Es del modelo
                  </label>
                </figcaption>
              </figure>
            );
          })}
        </div>

        <div className="botones">
          <label className="boton subir" aria-disabled={guardando}>
            Añadir fotos del PC
            <input type="file" multiple accept="image/*" disabled={guardando} onChange={anadirDelPc} />
          </label>
        </div>

        <h3>Fotos del modelo, en orden</h3>
        {errorCarga && <p className="error">{errorCarga}. Las nuevas irán detrás de las que ya tenga.</p>}
        {fotos.length === 0 ? (
          <p className="ayuda">Marca las imágenes del PDF que son del modelo o añade fotos del PC.</p>
        ) : (
          <>
            <p className="ayuda">Arrástralas para cambiar el orden: es el de la orden de corte y el Word de fotos. La primera es la principal.</p>
            <FotosOrdenables fotos={fotos} ocupada={guardando} onOrdenar={setTira} />
          </>
        )}

        {error && <p className="error">{error}</p>}
        <div className="botones">
          <button className="boton ok" type="button" disabled={guardando || guardadas === null || !hayQueGuardar} onClick={() => void guardar()}>
            {guardando ? 'Guardando…' : 'Guardar las fotos'}
          </button>
          <button className="boton" type="button" disabled={guardando} onClick={() => dialogo.current?.close()}>
            Ninguna, solo el documento
          </button>
        </div>
      </div>
    </dialog>
  );
}
