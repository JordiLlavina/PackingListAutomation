// La hoja de etiquetas de patrones (/app/etiquetas-patrones?ids=1,2,3): una
// vista de impresión de la propia PWA, fuera del marco de despacho para que
// en el papel no salga nada más. Una etiqueta por A4, a página entera y sin
// líneas de corte: arriba a la izquierda la foto principal en grande (entera,
// nunca recortada), a la derecha el QR del modelo (lo pinta el servidor,
// /m/{id}) y debajo, uno bajo otro, el nombre lo más grande, la referencia, el
// cliente y el recuadro de «Mano de obra», que el técnico rellena a boli. En
// pantalla, encima, una barra para volver, imprimir y marcarlas como
// impresas, que en el papel no sale (etiquetas.css).
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import FotoModelo from '../comun/FotoModelo';
import { Icono } from '../comun/iconos';
import { fechaDia, hojasDe, idsDe, recuento, tamanoNombre } from '../comun/etiquetas';
import { fotoUrl, type ModeloFicha, type ModeloResumen } from '../comun/modelos';
import { toast } from '../comun/toast';
import './etiquetas.css';

type Carga = { modelos: ModeloResumen[]; perdidos: number[] };

/**
 * Espera a que estén cargadas y decodificadas todas las imágenes de las
 * etiquetas (QR y fotos). Una que no se puede cargar no bloquea: se imprime
 * con su hueco, igual que la vería quien imprime.
 */
function imagenesListas(): Promise<void> {
  const imagenes = [...document.querySelectorAll<HTMLImageElement>('.etiqueta-patron img')];
  return Promise.all(imagenes.map((i) => i.decode().catch(() => undefined))).then(() => undefined);
}

function Etiqueta({ modelo }: { modelo: ModeloResumen }) {
  return (
    <article className="etiqueta-patron">
      <div className="arriba">
        <div className="marco-foto">
          {modelo.fotoPrincipalId !== null ? (
            <img className="foto-etiqueta" src={fotoUrl(modelo.fotoPrincipalId)} alt="" />
          ) : (
            <FotoModelo modelo={modelo} className="foto-etiqueta" />
          )}
        </div>
        <img className="qr" src={`/api/modelos/${modelo.id}/qr.png`} alt={`QR de ${modelo.referencia}`} />
      </div>
      <p className={`nombre ${tamanoNombre(modelo.nombre)}`}>{modelo.nombre}</p>
      <p className="referencia">{modelo.referencia}</p>
      <p className="cliente">{modelo.cliente}</p>
      <div className="mano-de-obra">
        <span className="rotulo">Mano de obra</span>
        <div className="recuadro" />
      </div>
      {modelo.etiquetaImpresaEn && <span className="ya-impresa">Ya impresa el {fechaDia(modelo.etiquetaImpresaEn)}</span>}
    </article>
  );
}

export default function EtiquetasPatrones() {
  const [params] = useSearchParams();
  const ids = params.get('ids');
  const [carga, setCarga] = useState<Carga | null>(null);
  const [marcando, setMarcando] = useState(false);
  const [marcadas, setMarcadas] = useState(false);
  // Las imágenes (QR y fotos) se piden después de pintar las fichas: imprimir antes dejaría etiquetas sin QR.
  const [imagenesCargadas, setImagenesCargadas] = useState(false);
  const [preparando, setPreparando] = useState(false);

  // Las reglas de impresión de etiquetas.css van colgadas de esta clase, para no tocar cómo se imprime otra pantalla.
  useEffect(() => {
    document.body.classList.add('imprimiendo-etiquetas');
    return () => document.body.classList.remove('imprimiendo-etiquetas');
  }, []);

  useEffect(() => {
    let vigente = true;
    const pedidos = idsDe(ids);
    setImagenesCargadas(false);
    setCarga(null);
    setMarcadas(false);
    // Un modelo que no llega (borrado, sin permiso) no tumba la hoja: se dice cuál falta y se imprimen los demás.
    const pedir = (id: number) =>
      api<ModeloFicha>(`/api/modelos/${id}`).then(
        (ficha) => ({ id, modelo: ficha.modelo }),
        () => ({ id, modelo: null }),
      );
    void Promise.all(pedidos.map(pedir)).then((respuestas) => {
      if (!vigente) return;
      const modelos: ModeloResumen[] = [];
      const perdidos: number[] = [];
      for (const { id, modelo } of respuestas) {
        if (modelo === null) perdidos.push(id);
        // Dos ids que acaban en el mismo modelo (uno se juntó con el otro) son una etiqueta.
        else if (!modelos.some((m) => m.id === modelo.id)) modelos.push(modelo);
      }
      setCarga({ modelos, perdidos });
    });
    return () => {
      vigente = false;
    };
  }, [ids]);

  const marcarComoImpresas = async () => {
    if (!carga) return;
    setMarcando(true);
    try {
      await Promise.all(carga.modelos.map((m) => api<void>(`/api/modelos/${m.id}/etiqueta-impresa`, { method: 'POST' })));
      const ahora = new Date().toISOString();
      setCarga({ ...carga, modelos: carga.modelos.map((m) => ({ ...m, etiquetaImpresaEn: ahora })) });
      setMarcadas(true);
      const n = carga.modelos.length;
      toast(n === 1 ? '1 etiqueta marcada como impresa' : `${n} etiquetas marcadas como impresas`);
    } catch (e) {
      toast(e instanceof ErrorApi ? e.message : 'No se han podido marcar como impresas');
    } finally {
      setMarcando(false);
    }
  };

  const modelos = carga?.modelos ?? [];
  const hayEtiquetas = modelos.length > 0;
  // Qué imágenes hay en la hoja: marcar como impresas cambia la carga pero no las imágenes, y no hay que volver a esperarlas.
  const claveImagenes = carga === null ? null : modelos.map((m) => `${m.id}:${m.fotoPrincipalId ?? ''}`).join(',');

  useEffect(() => {
    if (claveImagenes === null) return;
    let vigente = true;
    void imagenesListas().then(() => vigente && setImagenesCargadas(true));
    return () => {
      vigente = false;
    };
  }, [claveImagenes]);

  const imprimir = async () => {
    setPreparando(true);
    try {
      await imagenesListas();
    } finally {
      setPreparando(false);
    }
    window.print();
  };

  const listoParaImprimir = hayEtiquetas && imagenesCargadas && !preparando;

  return (
    <div className="despacho etiquetas-patrones">
      <header className="barra-etiquetas">
        <Link className="boton" to="/modelos">
          <Icono nombre="atras" /> Volver a modelos
        </Link>
        <div className="texto">
          <b>{carga === null ? 'Cargando las etiquetas…' : recuento(modelos.length)}</b>
          <span>Papel A4 normal, una etiqueta por hoja.</span>
        </div>
        <div className="acciones">
          <button className="boton" type="button" disabled={!hayEtiquetas || marcando || marcadas} onClick={() => void marcarComoImpresas()}>
            {marcadas ? (
              <>
                <Icono nombre="check" /> Marcadas como impresas
              </>
            ) : (
              'Marcar como impresas'
            )}
          </button>
          <button className="boton marca" type="button" disabled={!listoParaImprimir} onClick={() => void imprimir()}>
            <Icono nombre="imprimir" /> {hayEtiquetas && !listoParaImprimir ? 'Preparando…' : 'Imprimir'}
          </button>
        </div>
        {carga !== null && carga.perdidos.length > 0 && (
          <p className="aviso-perdidos">
            {carga.perdidos.length === 1
              ? `No se ha encontrado el modelo #${carga.perdidos[0]}: no lleva etiqueta.`
              : `No se han encontrado los modelos ${carga.perdidos.map((id) => `#${id}`).join(', ')}: no llevan etiqueta.`}
          </p>
        )}
      </header>

      {carga !== null && !hayEtiquetas ? (
        <div className="vacio">
          <b>No hay ningún modelo que imprimir. Elígelos con su casilla en la lista de modelos.</b>
        </div>
      ) : (
        <div className="hojas">
          {hojasDe(modelos).map((hoja, i) => (
            <section key={i} className="hoja-etiquetas" aria-label={`Hoja ${i + 1}`}>
              {hoja.map((m) => (
                <Etiqueta key={m.id} modelo={m} />
              ))}
            </section>
          ))}
        </div>
      )}
    </div>
  );
}
