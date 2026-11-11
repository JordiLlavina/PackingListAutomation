// Dar de alta un modelo a mano, en el panel de la derecha: cliente, temporada
// (una de las suyas o una nueva, que se crea antes del modelo), la referencia
// del cliente o un nombre de prototipo (una de las dos), nombre, tipo y etapa.
// Lo que el servidor rechace se dice debajo del botón, con su mensaje.
import { useEffect, useState, type FormEvent } from 'react';
import { ErrorApi, api } from '../comun/api';
import {
  ETAPAS,
  etapaTexto,
  type Cliente,
  type Etapa,
  type ModeloResumen,
  type Temporada,
  type TipoArticulo,
} from '../comun/modelos';

const NUEVA = 'nueva';

type Props = {
  clientes: Cliente[];
  clienteInicial: string;
  onCreado: (modelo: ModeloResumen) => void;
  onCancelar: () => void;
};

export default function AltaModelo({ clientes, clienteInicial, onCreado, onCancelar }: Props) {
  const [cliente, setCliente] = useState(clienteInicial || clientes[0]?.clave || '');
  const [temporadas, setTemporadas] = useState<Temporada[]>([]);
  const [temporada, setTemporada] = useState('');
  const [nuevaTemporada, setNuevaTemporada] = useState('');
  const [deCliente, setDeCliente] = useState(false);
  const [referencia, setReferencia] = useState('');
  const [nombre, setNombre] = useState('');
  const [tipo, setTipo] = useState<TipoArticulo>('BOLSO');
  const [etapa, setEtapa] = useState<Etapa>('PROTOTIPO');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  // Si la lista de clientes llega después de abrir el formulario.
  useEffect(() => {
    if (!cliente && clientes[0]) setCliente(clientes[0].clave);
  }, [cliente, clientes]);

  useEffect(() => {
    setTemporada('');
    setTemporadas([]);
    if (!cliente) return;
    let vigente = true;
    api<Temporada[]>(`/api/temporadas?cliente=${encodeURIComponent(cliente)}`)
      .then((t) => vigente && setTemporadas(t))
      .catch(() => vigente && setTemporadas([]));
    return () => {
      vigente = false;
    };
  }, [cliente]);

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      let temporadaId: number | null = temporada && temporada !== NUEVA ? Number(temporada) : null;
      if (temporada === NUEVA) {
        // La temporada se crea primero; si después falla el modelo, queda
        // elegida en el desplegable y no se vuelve a crear al reintentar.
        const creada = await api<Temporada>('/api/temporadas', {
          method: 'POST',
          body: JSON.stringify({ cliente, temporada: nuevaTemporada.trim() }),
        });
        setTemporadas((t) => [...t, creada]);
        setTemporada(String(creada.id));
        setNuevaTemporada('');
        temporadaId = creada.id;
      }
      const ref = referencia.trim() || null;
      const modelo = await api<ModeloResumen>('/api/modelos', {
        method: 'POST',
        body: JSON.stringify({
          cliente,
          temporadaId,
          codigo: deCliente ? ref : null,
          nombrePrototipo: deCliente ? null : ref,
          nombre: nombre.trim(),
          tipo,
          etapa,
        }),
      });
      onCreado(modelo);
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se ha podido dar de alta el modelo');
    } finally {
      setEnviando(false);
    }
  };

  return (
    <form className="formulario" onSubmit={(e) => void enviar(e)}>
      <h2>Dar de alta un modelo</h2>
      <label className="campo">
        Cliente
        <select value={cliente} onChange={(e) => setCliente(e.target.value)}>
          {clientes.map((c) => (
            <option key={c.clave} value={c.clave}>
              {c.nombre}
            </option>
          ))}
        </select>
      </label>
      <label className="campo">
        Temporada
        <select value={temporada} onChange={(e) => setTemporada(e.target.value)}>
          <option value="">Sin temporada</option>
          {temporadas.map((t) => (
            <option key={t.id} value={String(t.id)}>
              {t.temporada}
            </option>
          ))}
          <option value={NUEVA}>Nueva temporada…</option>
        </select>
      </label>
      {temporada === NUEVA && (
        <label className="campo">
          Nombre de la temporada nueva
          <input value={nuevaTemporada} placeholder="SS27" onChange={(e) => setNuevaTemporada(e.target.value)} />
        </label>
      )}
      <fieldset className="opciones-radio">
        <legend>Cómo se llama</legend>
        <label>
          <input type="radio" name="referencia" checked={deCliente} onChange={() => setDeCliente(true)} /> Referencia del cliente
        </label>
        <label>
          <input type="radio" name="referencia" checked={!deCliente} onChange={() => setDeCliente(false)} /> Referencia de prototipo
        </label>
      </fieldset>
      <label className="campo">
        {deCliente ? 'Referencia del cliente' : 'Referencia de prototipo'}
        <input value={referencia} placeholder={deCliente ? 'ULL770' : 'PROTO A'} onChange={(e) => setReferencia(e.target.value)} />
      </label>
      <label className="campo">
        Nombre
        <input value={nombre} placeholder="CANDY POUCH BAG" onChange={(e) => setNombre(e.target.value)} />
      </label>
      <fieldset className="opciones-radio">
        <legend>Tipo</legend>
        <label>
          <input type="radio" name="tipo" checked={tipo === 'BOLSO'} onChange={() => setTipo('BOLSO')} /> Bolso
        </label>
        <label>
          <input type="radio" name="tipo" checked={tipo === 'CINTURON'} onChange={() => setTipo('CINTURON')} /> Cinturón
        </label>
      </fieldset>
      <label className="campo">
        Etapa
        <select value={etapa} onChange={(e) => setEtapa(e.target.value as Etapa)}>
          {ETAPAS.map((x) => (
            <option key={x} value={x}>
              {etapaTexto(x)}
            </option>
          ))}
        </select>
      </label>
      <div className="botones">
        <button className="boton ok" type="submit" disabled={enviando || !cliente}>
          {enviando ? 'Dando de alta…' : 'Dar de alta'}
        </button>
        <button className="boton" type="button" onClick={onCancelar}>
          Cancelar
        </button>
      </div>
      {error && <p className="error">{error}</p>}
    </form>
  );
}
