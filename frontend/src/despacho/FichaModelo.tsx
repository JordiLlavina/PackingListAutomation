// La ficha de un modelo en despacho, en el panel de la derecha: la cabecera
// (foto principal, referencia, nombre editable, cliente, etapa, temporadas y
// los nombres que ha tenido), las acciones (una orden nueva para el taller,
// cambiar de etapa, subir fotos, asignar la referencia a un prototipo o, en
// uno importado, juntarlo con el prototipo que ya teníamos), la galería con
// la principal marcada, los documentos y el historial. Al subir un PDF, se
// eligen sus imágenes que son fotos del modelo (ElegirImagenesPdf); las demás no
// se guardan. Juntar dos modelos deja el de menor id,
// que puede no ser este: entonces se navega a la ficha del superviviente.
// Tras cada cambio se vuelve a pedir la ficha y se avisa a la lista para que
// repinte su fila.
import { useCallback, useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { ErrorApi, api } from '../comun/api';
import Etapa from '../comun/Etapa';
import FotoModelo from '../comun/FotoModelo';
import Historial from '../comun/Historial';
import ElegirImagenesPdf from './ElegirImagenesPdf';
import { Icono } from '../comun/iconos';
import {
  ETAPAS,
  antesEra,
  documentoUrl,
  etapaTexto,
  fotoUrl,
  prototiposCandidatos,
  tamanoTexto,
  textoFusion,
  type DocumentoResumen,
  type Etapa as TipoEtapa,
  type Fusion,
  type ModeloFicha,
  type ModeloResumen,
  type Subida,
} from '../comun/modelos';
import { toast } from '../comun/toast';

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
  // Documentos: el que se está subiendo, las candidatas del PDF recién subido
  // (abre la elección de imágenes) y el que se va a quitar (pide confirmación).
  const [subiendoDocumento, setSubiendoDocumento] = useState(false);
  const [subidaPdf, setSubidaPdf] = useState<Subida | null>(null);
  const dialogoQuitar = useRef<HTMLDialogElement>(null);
  const [aQuitar, setAQuitar] = useState<DocumentoResumen | null>(null);
  const dialogo = useRef<HTMLDialogElement>(null);
  const [etapa, setEtapa] = useState<TipoEtapa>('PROTOTIPO');
  const [motivo, setMotivo] = useState('');
  const [errorEtapa, setErrorEtapa] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const navegar = useNavigate();
  // Asignar referencia: el código tecleado y, si ya lo tiene otro modelo, lo
  // que se juntaría (con el código que se previsualizó, para confirmar ese).
  const dialogoReferencia = useRef<HTMLDialogElement>(null);
  const [codigo, setCodigo] = useState('');
  const [prevista, setPrevista] = useState<{ codigo: string; fusion: Fusion } | null>(null);
  const [errorReferencia, setErrorReferencia] = useState<string | null>(null);
  // Juntar un importado con un prototipo: los prototipos del cliente, el
  // buscador y el elegido con lo que se juntaría.
  const dialogoPrototipo = useRef<HTMLDialogElement>(null);
  const [prototipos, setPrototipos] = useState<ModeloResumen[] | null>(null);
  const [busca, setBusca] = useState('');
  const [elegido, setElegido] = useState<{ prototipo: ModeloResumen; fusion: Fusion } | null>(null);
  const [errorPrototipo, setErrorPrototipo] = useState<string | null>(null);

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

  // Tras asignar o juntar: si sobrevive este, se recarga; si sobrevive el
  // otro (el de menor id), este ya no existe y se va a la ficha de aquel.
  const irAlSuperviviente = async (superviviente: ModeloResumen, aviso: string) => {
    toast(aviso);
    if (superviviente.id === m.id) {
      await trasCambiar();
    } else {
      onCambiado();
      navegar(`/modelos/${superviviente.id}`);
    }
  };

  const previsualizar = (modeloId: number, conCodigo: string) =>
    api<Fusion>(`/api/modelos/${modeloId}/referencia/previsualizar?codigo=${encodeURIComponent(conCodigo)}`);

  const abrirReferencia = () => {
    setCodigo('');
    setPrevista(null);
    setErrorReferencia(null);
    dialogoReferencia.current?.showModal();
  };

  // Primero se pregunta qué pasaría: con la referencia libre se asigna sin
  // más; si ya la tiene otro modelo, se enseña qué se junta y hace falta
  // darle a «Juntar», que la manda confirmada.
  const asignarReferencia = async (e: FormEvent) => {
    e.preventDefault();
    const elCodigo = prevista ? prevista.codigo : codigo.trim().toUpperCase();
    if (!elCodigo) {
      setErrorReferencia('Falta la referencia');
      return;
    }
    setOcupado(true);
    setErrorReferencia(null);
    try {
      const confirmar = prevista !== null;
      if (!confirmar) {
        const fusion = await previsualizar(m.id, elCodigo);
        if (fusion.absorbido !== null) {
          setPrevista({ codigo: elCodigo, fusion });
          return;
        }
      }
      const superviviente = await api<ModeloResumen>(`/api/modelos/${m.id}/referencia`, {
        method: 'POST',
        body: JSON.stringify({ codigo: elCodigo, confirmar }),
      });
      dialogoReferencia.current?.close();
      await irAlSuperviviente(superviviente, confirmar ? 'Modelos juntados' : 'Referencia asignada');
    } catch (err) {
      setErrorReferencia(mensaje(err, 'No se ha podido asignar la referencia'));
      // Otro la ha dado de alta mientras tanto: se enseña qué se juntaría.
      if (err instanceof ErrorApi && err.estado === 409) {
        try {
          setPrevista({ codigo: elCodigo, fusion: await previsualizar(m.id, elCodigo) });
        } catch {
          // Ya se ha dicho el error de arriba.
        }
      }
    } finally {
      setOcupado(false);
    }
  };

  const abrirPrototipos = async () => {
    setBusca('');
    setElegido(null);
    setPrototipos(null);
    setErrorPrototipo(null);
    dialogoPrototipo.current?.showModal();
    try {
      setPrototipos(await api<ModeloResumen[]>(`/api/modelos?cliente=${encodeURIComponent(m.cliente)}`));
    } catch (err) {
      setErrorPrototipo(mensaje(err, 'No se han podido cargar los prototipos'));
    }
  };

  // Lo que se juntaría es lo mismo que daría asignar al prototipo el código de este.
  const elegirPrototipo = async (prototipo: ModeloResumen) => {
    if (m.codigo === null) return;
    setErrorPrototipo(null);
    try {
      setElegido({ prototipo, fusion: await previsualizar(prototipo.id, m.codigo) });
    } catch (err) {
      setErrorPrototipo(mensaje(err, 'No se ha podido ver qué se junta'));
    }
  };

  const juntarConPrototipo = async () => {
    if (!elegido) return;
    setOcupado(true);
    setErrorPrototipo(null);
    try {
      const superviviente = await api<ModeloResumen>(`/api/modelos/${elegido.prototipo.id}/fusionar-con/${m.id}`, {
        method: 'POST',
      });
      dialogoPrototipo.current?.close();
      await irAlSuperviviente(superviviente, 'Modelos juntados');
    } catch (err) {
      setErrorPrototipo(mensaje(err, 'No se han podido juntar los modelos'));
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

  // Un PDF vuelve con candidatas: se abre la elección de imágenes. Cualquier
  // otro documento (o un PDF sin ninguna) solo se guarda.
  const subirDocumento = async (e: ChangeEvent<HTMLInputElement>) => {
    const fichero = e.target.files?.[0];
    e.target.value = '';
    if (!fichero) return;
    const datos = new FormData();
    datos.append('documento', fichero);
    setSubiendoDocumento(true);
    setErrorAccion(null);
    try {
      const subida = await api<Subida>(`/api/modelos/${m.id}/documentos`, { method: 'POST', body: datos });
      await trasCambiar();
      if (subida.loteId !== null && subida.candidatas.length > 0) {
        setSubidaPdf(subida);
      } else {
        toast('Documento añadido');
      }
    } catch (err) {
      setErrorAccion(mensaje(err, 'No se ha podido subir el documento'));
    } finally {
      setSubiendoDocumento(false);
    }
  };

  const pedirQuitar = (documento: DocumentoResumen) => {
    setAQuitar(documento);
    dialogoQuitar.current?.showModal();
  };

  const quitarDocumento = async () => {
    if (!aQuitar) return;
    setOcupado(true);
    setErrorAccion(null);
    try {
      await api(`/api/documentos/${aQuitar.id}`, { method: 'DELETE' });
      dialogoQuitar.current?.close();
      toast('Documento quitado');
      await trasCambiar();
    } catch (err) {
      dialogoQuitar.current?.close();
      setErrorAccion(mensaje(err, 'No se ha podido quitar el documento'));
    } finally {
      setOcupado(false);
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
          {antesEra(ficha.fusionadosDesde) && <span className="meta">{antesEra(ficha.fusionadosDesde)}</span>}
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
        <Link className="boton marca" to={`/bandeja/nueva?pestana=despacho&modelo=${m.id}`}>
          <Icono nombre="mas" /> Nueva orden para este modelo
        </Link>
        <button className="boton" type="button" onClick={abrirEtapa}>
          Cambiar etapa
        </button>
        {m.codigo === null ? (
          <button className="boton" type="button" onClick={abrirReferencia}>
            Asignar referencia
          </button>
        ) : (
          <button className="boton" type="button" onClick={() => void abrirPrototipos()}>
            Es un prototipo que ya tenemos
          </button>
        )}
        <label className="boton subir" aria-disabled={subiendo}>
          <Icono nombre="cam" /> {subiendo ? 'Subiendo fotos…' : 'Subir fotos'}
          <input type="file" multiple accept="image/*" disabled={subiendo} onChange={(e) => void subirFotos(e)} />
        </label>
        <label className="boton subir" aria-disabled={subiendoDocumento}>
          <Icono nombre="pdf" /> {subiendoDocumento ? 'Subiendo documento…' : 'Subir PDF / documento'}
          <input
            type="file"
            accept=".pdf,application/pdf,image/*,.doc,.docx,.xlsx"
            disabled={subiendoDocumento}
            onChange={(e) => void subirDocumento(e)}
          />
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
        <h2>Documentos</h2>
      </div>
      {ficha.documentos.length === 0 ? (
        <p className="ayuda">Este modelo aún no tiene documentos. Súbelos con «Subir PDF / documento».</p>
      ) : (
        <ul className="lista-documentos">
          {ficha.documentos.map((d) => (
            <li key={d.id}>
              <Icono nombre="pdf" />
              <span className="texto">
                <b>{d.nombre}</b>
                <span className="meta">
                  {tamanoTexto(d.tamano)} · {fecha(d.fecha)}
                </span>
              </span>
              <a className="chip" href={documentoUrl(d.id)} target="_blank" rel="noopener">
                Abrir
              </a>
              <button className="chip" type="button" onClick={() => pedirQuitar(d)}>
                Quitar
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="fila-titulo">
        <h2>Historial</h2>
      </div>
      <Historial eventos={ficha.lineaTiempo} />

      {subidaPdf && (
        <ElegirImagenesPdf
          modeloId={m.id}
          subida={subidaPdf}
          onCerrar={() => setSubidaPdf(null)}
          onGuardadas={() => void trasCambiar()}
        />
      )}

      <dialog ref={dialogoQuitar} className="dialogo" aria-labelledby="titulo-quitar">
        <div className="formulario">
          <h2 id="titulo-quitar">¿Quitar {aQuitar?.nombre}?</h2>
          <p>Se borra el documento del modelo. Las fotos que ya se sacaron de él se quedan en la galería.</p>
          <div className="botones">
            <button className="boton peligro" type="button" disabled={ocupado} onClick={() => void quitarDocumento()}>
              Quitar
            </button>
            <button className="boton" type="button" onClick={() => dialogoQuitar.current?.close()}>
              Cancelar
            </button>
          </div>
        </div>
      </dialog>

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

      <dialog ref={dialogoReferencia} className="dialogo" aria-labelledby="titulo-referencia">
        <form onSubmit={(e) => void asignarReferencia(e)}>
          <h2 id="titulo-referencia">Asignar referencia a {m.referencia}</h2>
          <label className="campo">
            Referencia del cliente
            <input
              value={codigo}
              maxLength={80}
              autoFocus
              onChange={(e) => {
                setCodigo(e.target.value);
                setPrevista(null);
              }}
            />
          </label>
          {prevista && <p className="aviso-fusion">{textoFusion(prevista.codigo, prevista.fusion)}</p>}
          {errorReferencia && <p className="error">{errorReferencia}</p>}
          <div className="botones">
            <button className="boton ok" type="submit" disabled={ocupado || !codigo.trim()}>
              {prevista ? 'Juntar' : 'Asignar'}
            </button>
            <button className="boton" type="button" onClick={() => dialogoReferencia.current?.close()}>
              Cancelar
            </button>
          </div>
        </form>
      </dialog>

      <dialog ref={dialogoPrototipo} className="dialogo" aria-labelledby="titulo-prototipo">
        <div className="formulario">
          <h2 id="titulo-prototipo">¿Qué prototipo era {m.referencia}?</h2>
          {elegido ? (
            <>
              <div className="modelo-elegido">
                <FotoModelo modelo={elegido.prototipo} />
                <span className="texto">
                  <b>{elegido.prototipo.referencia}</b>
                  <span>{elegido.prototipo.nombre}</span>
                </span>
                <button className="chip" type="button" onClick={() => setElegido(null)}>
                  Cambiar
                </button>
              </div>
              <p className="aviso-fusion">{textoFusion(m.referencia, elegido.fusion)}</p>
            </>
          ) : (
            <div className="buscador-modelos">
              <input type="search" aria-label="Buscar el prototipo" value={busca} placeholder="Referencia de prototipo o nombre" onChange={(e) => setBusca(e.target.value)} />
              {prototipos !== null &&
                (prototiposCandidatos(prototipos, m.id, busca).length === 0 ? (
                  <p className="ayuda">No hay ningún prototipo de {m.cliente}{busca.trim() ? ` con «${busca.trim()}»` : ''}.</p>
                ) : (
                  <ul className="lista-modelos">
                    {prototiposCandidatos(prototipos, m.id, busca).map((p) => (
                      <li key={p.id} className="fila-modelo">
                        <button className="abrir" type="button" onClick={() => void elegirPrototipo(p)}>
                          <FotoModelo modelo={p} />
                          <span className="texto">
                            <b>{p.referencia}</b>
                            <span>{p.nombre || 'Sin nombre'}</span>
                          </span>
                          <Etapa etapa={p.etapa} />
                        </button>
                      </li>
                    ))}
                  </ul>
                ))}
            </div>
          )}
          {errorPrototipo && <p className="error">{errorPrototipo}</p>}
          <div className="botones">
            {elegido && (
              <button className="boton ok" type="button" disabled={ocupado} onClick={() => void juntarConPrototipo()}>
                Juntar
              </button>
            )}
            <button className="boton" type="button" onClick={() => dialogoPrototipo.current?.close()}>
              Cancelar
            </button>
          </div>
        </div>
      </dialog>
    </div>
  );
}
